package com.deathrevive.plugin.disguise;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches a single fresh {@link FakeIdentity} on demand by generating a random candidate name,
 * verifying it against the real Mojang API, and pairing it with a skin borrowed from a different,
 * already-taken account. Used to top up the fake-name pool live during gameplay, one identity at a
 * time, as opposed to {@code NameListGenerator}'s bulk offline generation. Runs blocking HTTP calls
 * - callers must invoke this off the main server thread.
 */
public class MojangIdentityFetcher {

    private static final String[] ADJECTIVES = {
            "Silent", "Shadow", "Crimson", "Frozen", "Golden", "Swift", "Dark", "Wild", "Blazing", "Mystic",
            "Savage", "Lucky", "Bold", "Sneaky", "Feral", "Cosmic", "Rogue", "Brave", "Noble", "Stormy",
            "Rapid", "Fierce", "Icy", "Ghostly", "Deadly", "Happy", "Crazy", "Epic", "Turbo", "Cool"
    };

    private static final String[] NOUNS = {
            "Wolf", "Falcon", "Ninja", "Knight", "Ghost", "Raven", "Tiger", "Dragon", "Pirate", "Ranger",
            "Hunter", "Viper", "Panther", "Phoenix", "Golem", "Wizard", "Archer", "Bandit", "Reaper", "Goblin",
            "Gamer", "Player", "King", "Legend", "Boss", "Star", "Fox", "Bear", "Shark", "Eagle"
    };

    private static final int MIN_NAME_LENGTH = 3;
    private static final int MAX_NAME_LENGTH = 16;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration RETRY_DELAY = Duration.ofSeconds(2);
    private static final String NAME_LOOKUP_URI_PREFIX = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String SESSION_SERVER_URI_PREFIX = "https://sessionserver.mojang.com/session/minecraft/profile/";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Random random = new Random();

    private record ProfileLookup(boolean free, String uuid) {
    }

    /**
     * @return a verified-free name paired with a skin borrowed from an unrelated taken account, or
     *         empty if no such pair could be found within {@code maxAttempts} (e.g. Mojang is
     *         unreachable or rate-limiting).
     */
    public Optional<FakeIdentity> fetchNewIdentity(Set<String> excludedNames, int maxAttempts) {
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            String candidateName = randomCandidateName();
            if (excludedNames.contains(candidateName)) {
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

    private Optional<FakeIdentity> findSkinDonor(String candidateName, int maxAttempts) {
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            String donorCandidate = randomCandidateName();
            Optional<ProfileLookup> donorLookup = lookupProfile(donorCandidate);
            if (donorLookup.isEmpty() || donorLookup.get().free()) {
                continue;
            }

            Optional<String[]> texture = fetchSkinTexture(donorLookup.get().uuid());
            if (texture.isPresent()) {
                return Optional.of(new FakeIdentity(candidateName, texture.get()[0], texture.get()[1]));
            }
        }
        return Optional.empty();
    }

    private String randomCandidateName() {
        String adjective = ADJECTIVES[random.nextInt(ADJECTIVES.length)];
        String noun = NOUNS[random.nextInt(NOUNS.length)];
        String name = switch (random.nextInt(3)) {
            case 0 -> adjective + noun;
            case 1 -> adjective + "_" + noun;
            default -> adjective + noun + (1 + random.nextInt(9999));
        };
        return name.length() <= MAX_NAME_LENGTH ? name : name.substring(0, MAX_NAME_LENGTH);
    }

    private Optional<ProfileLookup> lookupProfile(String name) {
        if (name.length() < MIN_NAME_LENGTH) {
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

    private Optional<HttpResponse<String>> getWithRetry(String uri) {
        for (int attempt = 0; attempt < 2; attempt++) {
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
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    return Optional.empty();
                }
            }
            sleepQuietly(RETRY_DELAY);
        }
        return Optional.empty();
    }

    private void sleepQuietly(Duration duration) {
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
