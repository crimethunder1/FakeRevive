package com.fakerevive.disguise;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches fresh {@link FakeIdentity} objects on demand by generating random candidate names,
 * verifying them against the real Mojang API, and pairing each with a skin borrowed from a
 * different, already-taken account. Used to top up the fake-name pool live during gameplay, as
 * opposed to {@code NameListGenerator}'s bulk offline generation. Runs blocking HTTP calls -
 * callers must invoke this off the main server thread.
 */
public class MojangIdentityFetcher {

    private static final int MIN_NAME_LENGTH = 3;
    private static final int MAX_NAME_LENGTH = 16;
    private static final int MIN_APPENDED_DIGITS = 2;
    private static final int MAX_APPENDED_DIGITS = 5;
    private static final int MAX_NAME_BUILD_REDRAWS = 10;
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration INITIAL_BACKOFF = Duration.ofSeconds(2);
    private static final int MAX_RETRIES_ON_RATE_LIMIT = 4;
    private static final String NAME_LOOKUP_URI_PREFIX = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String SESSION_SERVER_URI_PREFIX = "https://sessionserver.mojang.com/session/minecraft/profile/";

    /** Cap on the "already asked Mojang about this one" memo, so a long uptime cannot leak memory. */
    private static final int MAX_REMEMBERED_PROBES = 5_000;
    private static final int MAX_CACHED_DONOR_SKINS = 32;
    /** Only start recycling cached donor skins once the cache is varied enough to look natural. */
    private static final int MIN_DONOR_CACHE_BEFORE_REUSE = 8;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Random random = new Random();
    private final Set<String> recentlyProbed = Collections.synchronizedSet(new LinkedHashSet<>());
    private final List<String[]> donorSkins = Collections.synchronizedList(new ArrayList<>());

    private volatile boolean reuseDonorSkins = true;

    private record ProfileLookup(boolean free, String uuid) {
    }

    /**
     * Reusing donor skins cuts the API calls per identity from roughly four-to-eight down to
     * two, which is the single biggest lever against Mojang rate limiting. Nothing in the plugin
     * requires a disguise skin to be unique.
     */
    public void setReuseDonorSkins(boolean reuseDonorSkins) {
        this.reuseDonorSkins = reuseDonorSkins;
    }

    /**
     * Fetches up to {@code count} identities in one pass. Every accepted name is added to a
     * thread-confined copy of {@code excludedNames}, so a single batch can never hand back the
     * same name twice. Stops early once an identity cannot be found (Mojang unreachable or
     * rate-limiting) or once {@code abort} reports true, rather than burning the whole budget.
     * Blocking - async thread only.
     *
     * @param spacing pause between identities, to stay well under the Mojang rate limit.
     */
    public List<FakeIdentity> fetchNewIdentities(Set<String> excludedNames, int count, int maxAttemptsEach,
                                                 Duration spacing, BooleanSupplier abort) {
        Set<String> exclusions = new HashSet<>(excludedNames);
        List<FakeIdentity> fetched = new ArrayList<>(Math.max(count, 0));

        for (int i = 0; i < count; i++) {
            if (abort.getAsBoolean() || Thread.currentThread().isInterrupted()) {
                break;
            }

            Optional<FakeIdentity> identity = fetchNewIdentity(exclusions, maxAttemptsEach);
            if (identity.isEmpty()) {
                break;
            }

            exclusions.add(identity.get().name());
            fetched.add(identity.get());

            if (i + 1 < count) {
                sleepQuietly(spacing);
            }
        }
        return fetched;
    }

    /**
     * @return a verified-free name paired with a skin borrowed from an unrelated taken account, or
     *         empty if no such pair could be found within {@code maxAttempts} (e.g. Mojang is
     *         unreachable or rate-limiting).
     */
    public Optional<FakeIdentity> fetchNewIdentity(Set<String> excludedNames, int maxAttempts) {
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            String candidateName = randomFreeCandidateName();
            if (excludedNames.contains(candidateName) || !rememberProbe(candidateName)) {
                continue;
            }

            Optional<ProfileLookup> nameLookup = lookupProfile(candidateName);
            if (nameLookup.isEmpty() || !nameLookup.get().free()) {
                continue;
            }

            Optional<FakeIdentity> identity = findSkinDonor(candidateName, maxAttempts);
            if (identity.isPresent()) {
                return identity;
            }
        }
        return Optional.empty();
    }

    /**
     * Fetches the current name and skin for an existing Minecraft account by username, so a
     * player can be given a specific real identity instead of a randomly generated one.
     * @return the account identity, or empty if no account exists under that name or its skin
     *         could not be retrieved.
     */
    public Optional<FakeIdentity> fetchIdentityByName(String name) {
        Optional<HttpResponse<String>> profileResponse = getWithRetry(NAME_LOOKUP_URI_PREFIX + name);
        if (profileResponse.isEmpty() || profileResponse.get().statusCode() != 200) {
            return Optional.empty();
        }

        Optional<String> uuid = extractJsonField(profileResponse.get().body(), "id");
        if (uuid.isEmpty()) {
            return Optional.empty();
        }

        return fetchSkinTexture(uuid.get()).map(texture -> new FakeIdentity(name, texture[0], texture[1]));
    }

    private Optional<FakeIdentity> findSkinDonor(String candidateName, int maxAttempts) {
        Optional<String[]> cached = borrowCachedDonorSkin();
        if (cached.isPresent()) {
            return Optional.of(new FakeIdentity(candidateName, cached.get()[0], cached.get()[1]));
        }

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            String donorCandidate = randomTakenCandidateName();
            Optional<ProfileLookup> donorLookup = lookupProfile(donorCandidate);
            if (donorLookup.isEmpty() || donorLookup.get().free()) {
                continue;
            }

            Optional<String[]> texture = fetchSkinTexture(donorLookup.get().uuid());
            if (texture.isPresent()) {
                cacheDonorSkin(texture.get());
                return Optional.of(new FakeIdentity(candidateName, texture.get()[0], texture.get()[1]));
            }
        }
        return Optional.empty();
    }

    private Optional<String[]> borrowCachedDonorSkin() {
        if (!reuseDonorSkins) {
            return Optional.empty();
        }
        synchronized (donorSkins) {
            if (donorSkins.size() < MIN_DONOR_CACHE_BEFORE_REUSE) {
                return Optional.empty();
            }
            return Optional.of(donorSkins.get(random.nextInt(donorSkins.size())));
        }
    }

    private void cacheDonorSkin(String[] texture) {
        synchronized (donorSkins) {
            if (donorSkins.size() >= MAX_CACHED_DONOR_SKINS) {
                donorSkins.remove(0);
            }
            donorSkins.add(texture);
        }
    }

    /**
     * Remembers a candidate so the same name is never sent to Mojang twice in one server session.
     * @return {@code true} if this is the first time we have seen the name.
     */
    private boolean rememberProbe(String candidateName) {
        synchronized (recentlyProbed) {
            if (!recentlyProbed.add(candidateName)) {
                return false;
            }
            while (recentlyProbed.size() > MAX_REMEMBERED_PROBES) {
                Iterator<String> oldest = recentlyProbed.iterator();
                oldest.next();
                oldest.remove();
            }
            return true;
        }
    }

    /**
     * Builds a candidate biased towards names that are still <em>unclaimed</em> on Mojang: every
     * shape carries a digit tail. Plain adjective+noun pairs are essentially all taken, so
     * spending lookups on them is wasted budget.
     */
    String randomFreeCandidateName() {
        for (int redraw = 0; redraw < MAX_NAME_BUILD_REDRAWS; redraw++) {
            String base = switch (random.nextInt(5)) {
                case 0 -> pick(NameVocabulary.ADJECTIVES) + pick(NameVocabulary.NOUNS);
                case 1 -> pick(NameVocabulary.ADJECTIVES) + "_" + pick(NameVocabulary.NOUNS);
                case 2 -> pick(NameVocabulary.SINGLE_WORDS) + pick(NameVocabulary.NOUNS);
                case 3 -> pick(NameVocabulary.PREFIXES) + pick(NameVocabulary.ADJECTIVES) + pick(NameVocabulary.NOUNS);
                default -> pick(NameVocabulary.ADJECTIVES) + pick(NameVocabulary.NOUNS) + pick(NameVocabulary.SUFFIXES);
            };

            // Never truncate: cutting a long base down to 16 chars collapses distinct draws onto
            // the same string, which is exactly how the pool used to end up repeating itself.
            int budget = MAX_NAME_LENGTH - base.length();
            if (budget < MIN_APPENDED_DIGITS) {
                continue;
            }

            int maxDigits = Math.min(budget, MAX_APPENDED_DIGITS);
            int digits = MIN_APPENDED_DIGITS + random.nextInt(maxDigits - MIN_APPENDED_DIGITS + 1);
            StringBuilder name = new StringBuilder(base);
            for (int i = 0; i < digits; i++) {
                name.append(random.nextInt(10));
            }
            return name.toString();
        }

        // Every shape overflowed the 16-char budget - fall back to the shortest form we have.
        return pick(NameVocabulary.SINGLE_WORDS) + (10 + random.nextInt(90));
    }

    /**
     * Builds a candidate biased towards names that are <em>already claimed</em>, which is what a
     * skin donor lookup wants. Short, popular, digit-free forms are almost always taken, so this
     * finds a donor in far fewer requests than reusing the free-name generator did.
     */
    String randomTakenCandidateName() {
        for (int redraw = 0; redraw < MAX_NAME_BUILD_REDRAWS; redraw++) {
            String candidate = switch (random.nextInt(6)) {
                case 0 -> pick(NameVocabulary.ADJECTIVES) + pick(NameVocabulary.NOUNS);
                case 1 -> pick(NameVocabulary.ADJECTIVES) + "_" + pick(NameVocabulary.NOUNS);
                case 2 -> (pick(NameVocabulary.ADJECTIVES) + pick(NameVocabulary.NOUNS)).toLowerCase(Locale.ROOT);
                case 3 -> pick(NameVocabulary.SINGLE_WORDS);
                case 4 -> pick(NameVocabulary.PREFIXES) + pick(NameVocabulary.NOUNS);
                default -> pick(NameVocabulary.NOUNS) + pick(NameVocabulary.SUFFIXES);
            };

            if (candidate.length() >= MIN_NAME_LENGTH && candidate.length() <= MAX_NAME_LENGTH) {
                return candidate;
            }
        }
        return pick(NameVocabulary.SINGLE_WORDS);
    }

    private String pick(String[] words) {
        return words[random.nextInt(words.length)];
    }

    private Optional<ProfileLookup> lookupProfile(String name) {
        // Mojang answers 404 for malformed names too, which would read as "free" even though no
        // client would ever render the name. Reject those before spending a request on them.
        if (!VALID_NAME.matcher(name).matches()) {
            return Optional.empty();
        }
        Optional<HttpResponse<String>> response = getWithRetry(NAME_LOOKUP_URI_PREFIX + name);
        if (response.isEmpty()) {
            return Optional.empty();
        }
        HttpResponse<String> httpResponse = response.get();
        if (httpResponse.statusCode() == 404) {
            return Optional.of(new ProfileLookup(true, null));
        }
        if (httpResponse.statusCode() == 200) {
            Optional<String> uuid = extractJsonField(httpResponse.body(), "id");
            return uuid.map(id -> new ProfileLookup(false, id));
        }
        return Optional.empty();
    }

    /**
     * @return {value, signature} of the donor's current skin texture, or empty if it couldn't be
     *         fetched.
     */
    private Optional<String[]> fetchSkinTexture(String uuid) {
        Optional<HttpResponse<String>> response = getWithRetry(SESSION_SERVER_URI_PREFIX + uuid + "?unsigned=false");
        if (response.isEmpty() || response.get().statusCode() != 200) {
            return Optional.empty();
        }
        String body = response.get().body();
        Optional<String> value = extractJsonField(body, "value");
        Optional<String> signature = extractJsonField(body, "signature");
        if (value.isEmpty() || signature.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new String[] {value.get(), signature.get()});
    }

    /**
     * Issues the request, backing off exponentially on HTTP 429 and on transport errors. A flat
     * two-attempt retry was not enough once Mojang starts rate limiting - it burned the budget and
     * returned empty, which upstream reads as "no names available".
     */
    private Optional<HttpResponse<String>> getWithRetry(String uri) {
        Duration backoff = INITIAL_BACKOFF;

        for (int attempt = 0; attempt <= MAX_RETRIES_ON_RATE_LIMIT; attempt++) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uri))
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 429) {
                    return Optional.of(response);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            } catch (IOException e) {
                // falls through to the backoff sleep and the next attempt
            }

            if (attempt < MAX_RETRIES_ON_RATE_LIMIT) {
                sleepQuietly(backoff);
                backoff = backoff.multipliedBy(2);
            }
        }
        return Optional.empty();
    }

    private void sleepQuietly(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            return;
        }
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Optional<String> extractJsonField(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }
}
