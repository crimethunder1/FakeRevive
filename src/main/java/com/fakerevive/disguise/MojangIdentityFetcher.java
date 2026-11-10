package com.fakerevive.disguise;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

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
import java.util.regex.Pattern;

public class MojangIdentityFetcher {
    private static final int MIN_NAME_LENGTH = 3;
    private static final int MAX_NAME_LENGTH = 16;
    private static final int MIN_APPENDED_DIGITS = 2;
    private static final int MAX_APPENDED_DIGITS = 5;
    private static final int MAX_NAME_BUILD_REDRAWS = 10;
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Pattern VALID_UUID = Pattern.compile("[0-9a-fA-F]{32}");

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration INITIAL_BACKOFF = Duration.ofSeconds(2);
    private static final int MAX_RETRIES_ON_RATE_LIMIT = 4;
    private static final String NAME_LOOKUP_URI_PREFIX = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String SESSION_SERVER_URI_PREFIX = "https://sessionserver.mojang.com/session/minecraft/profile/";

    private static final int MAX_REMEMBERED_PROBES = 5_000;
    private static final int MAX_CACHED_DONOR_SKINS = 32;
    private static final int MIN_DONOR_CACHE_BEFORE_REUSE = 8;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Random random = new Random();
    private final Set<String> recentlyProbed = Collections.synchronizedSet(new LinkedHashSet<>());
    private final List<String[]> donorSkins = Collections.synchronizedList(new ArrayList<>());

    private volatile boolean reuseDonorSkins = true;

    private record ProfileLookup(boolean free, String uniqueId, String canonicalName) {
    }

    public void setReuseDonorSkins(boolean reuseDonorSkins) {
        this.reuseDonorSkins = reuseDonorSkins;
    }

    public List<FakeIdentity> fetchNewIdentities(Set<String> excludedNames, int count, int maxAttemptsEach,
                                                 Duration spacing, BooleanSupplier abort) {
        Set<String> exclusions = new HashSet<>(excludedNames);
        List<FakeIdentity> fetched = new ArrayList<>(Math.max(count, 0));

        for (int index = 0; index < count; index++) {
            if (abort.getAsBoolean() || Thread.currentThread().isInterrupted()) {
                break;
            }

            Optional<FakeIdentity> identity = fetchNewIdentity(exclusions, maxAttemptsEach);
            if (identity.isEmpty()) {
                break;
            }

            exclusions.add(identity.get().name());
            fetched.add(identity.get());

            if (index + 1 < count) {
                sleepQuietly(spacing);
            }
        }
        return fetched;
    }

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

    public Optional<FakeIdentity> fetchIdentityByName(String name) {
        if (name == null || !VALID_NAME.matcher(name).matches()) {
            return Optional.empty();
        }
        try {
            Optional<ProfileLookup> lookup = lookupProfile(name);
            if (lookup.isEmpty() || lookup.get().free()) {
                return Optional.empty();
            }
            ProfileLookup profile = lookup.get();
            if (profile.canonicalName() == null) {
                return Optional.empty();
            }
            return fetchSkinTexture(profile.uniqueId())
                    .map(texture -> new FakeIdentity(profile.canonicalName(), texture[0], texture[1]));
        } catch (IllegalArgumentException | JsonParseException | IllegalStateException | UnsupportedOperationException exception) {
            return Optional.empty();
        }
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

            Optional<String[]> texture = fetchSkinTexture(donorLookup.get().uniqueId());
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

    String randomFreeCandidateName() {
        for (int redraw = 0; redraw < MAX_NAME_BUILD_REDRAWS; redraw++) {
            String base = switch (random.nextInt(5)) {
                case 0 -> pick(NameVocabulary.ADJECTIVES) + pick(NameVocabulary.NOUNS);
                case 1 -> pick(NameVocabulary.ADJECTIVES) + "_" + pick(NameVocabulary.NOUNS);
                case 2 -> pick(NameVocabulary.SINGLE_WORDS) + pick(NameVocabulary.NOUNS);
                case 3 -> pick(NameVocabulary.PREFIXES) + pick(NameVocabulary.ADJECTIVES) + pick(NameVocabulary.NOUNS);
                default -> pick(NameVocabulary.ADJECTIVES) + pick(NameVocabulary.NOUNS) + pick(NameVocabulary.SUFFIXES);
            };

            int budget = MAX_NAME_LENGTH - base.length();
            if (budget < MIN_APPENDED_DIGITS) {
                continue;
            }

            int maxDigits = Math.min(budget, MAX_APPENDED_DIGITS);
            int digits = MIN_APPENDED_DIGITS + random.nextInt(maxDigits - MIN_APPENDED_DIGITS + 1);
            StringBuilder name = new StringBuilder(base);
            for (int index = 0; index < digits; index++) {
                name.append(random.nextInt(10));
            }
            return name.toString();
        }

        return pick(NameVocabulary.SINGLE_WORDS) + (10 + random.nextInt(90));
    }

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
        if (!VALID_NAME.matcher(name).matches()) {
            return Optional.empty();
        }
        Optional<HttpResponse<String>> response = getWithRetry(NAME_LOOKUP_URI_PREFIX + name);
        if (response.isEmpty()) {
            return Optional.empty();
        }
        HttpResponse<String> httpResponse = response.get();
        if (httpResponse.statusCode() == 404) {
            return Optional.of(new ProfileLookup(true, null, null));
        }
        if (httpResponse.statusCode() != 200) {
            return Optional.empty();
        }
        Optional<JsonObject> body = parseJsonObject(httpResponse.body());
        if (body.isEmpty()) {
            return Optional.empty();
        }
        Optional<String> uniqueId = readString(body.get(), "id").filter(id -> VALID_UUID.matcher(id).matches());
        Optional<String> canonicalName = readString(body.get(), "name").filter(canonical -> VALID_NAME.matcher(canonical).matches());
        if (uniqueId.isEmpty() || canonicalName.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ProfileLookup(false, uniqueId.get(), canonicalName.get()));
    }

    private Optional<String[]> fetchSkinTexture(String uniqueId) {
        if (uniqueId == null || !VALID_UUID.matcher(uniqueId).matches()) {
            return Optional.empty();
        }
        Optional<HttpResponse<String>> response = getWithRetry(SESSION_SERVER_URI_PREFIX + uniqueId + "?unsigned=false");
        if (response.isEmpty() || response.get().statusCode() != 200) {
            return Optional.empty();
        }
        Optional<JsonObject> body = parseJsonObject(response.get().body());
        if (body.isEmpty() || !body.get().has("properties") || !body.get().get("properties").isJsonArray()) {
            return Optional.empty();
        }
        JsonArray properties = body.get().getAsJsonArray("properties");
        for (JsonElement propertyElement : properties) {
            if (!propertyElement.isJsonObject()) {
                continue;
            }
            JsonObject property = propertyElement.getAsJsonObject();
            if (!"textures".equals(readString(property, "name").orElse(null))) {
                continue;
            }
            Optional<String> value = readString(property, "value");
            Optional<String> signature = readString(property, "signature");
            if (value.isPresent() && signature.isPresent()) {
                return Optional.of(new String[] {value.get(), signature.get()});
            }
        }
        return Optional.empty();
    }

    private static Optional<JsonObject> parseJsonObject(String body) {
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonElement parsed = JsonParser.parseString(body);
            return parsed.isJsonObject() ? Optional.of(parsed.getAsJsonObject()) : Optional.empty();
        } catch (JsonParseException exception) {
            return Optional.empty();
        }
    }

    private static Optional<String> readString(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            return Optional.empty();
        }
        return Optional.of(element.getAsString());
    }

    Optional<HttpResponse<String>> getWithRetry(String uri) {
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
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            } catch (IOException exception) {
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
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
