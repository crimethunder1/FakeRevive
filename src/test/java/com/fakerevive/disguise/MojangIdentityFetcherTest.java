package com.fakerevive.disguise;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.net.ssl.SSLSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MojangIdentityFetcherTest {
    private static final Pattern LEGAL_MINECRAFT_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final int DRAWS = 10_000;
    private static final String NOTCH_UUID = "069a79f444e94726a5befca90e38aaf5";
    private static final String LOOKUP_PREFIX = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String SESSION_PREFIX = "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static final String SKIN_QUERY = "?unsigned=false";

    @Test
    void freeCandidateNamesAreAlwaysLegalMinecraftNames() {
        MojangIdentityFetcher fetcher = new MojangIdentityFetcher();

        for (int index = 0; index < DRAWS; index++) {
            String name = fetcher.randomFreeCandidateName();
            assertTrue(LEGAL_MINECRAFT_NAME.matcher(name).matches(), "Illegal candidate name: " + name);
        }
    }

    @Test
    void freeCandidateNamesAreOverwhelminglyDistinct() {
        MojangIdentityFetcher fetcher = new MojangIdentityFetcher();

        Set<String> distinct = new HashSet<>();
        for (int index = 0; index < DRAWS; index++) {
            distinct.add(fetcher.randomFreeCandidateName());
        }

        assertTrue(distinct.size() > 9_000,
                "Expected well over 9000 distinct names out of " + DRAWS + " draws, got " + distinct.size());
    }

    @Test
    void donorCandidateNamesCarryNoDigits() {
        MojangIdentityFetcher fetcher = new MojangIdentityFetcher();

        for (int index = 0; index < DRAWS; index++) {
            String name = fetcher.randomTakenCandidateName();
            assertTrue(LEGAL_MINECRAFT_NAME.matcher(name).matches(), "Illegal donor name: " + name);
            assertFalse(name.matches(".*\\d.*"), "Donor candidate should look like a claimed name: " + name);
        }
    }

    @Test
    void batchStopsAtTheFirstFailureInsteadOfBurningTheWholeBudget() {
        RecordingFetcher fetcher = new RecordingFetcher(List.of());

        List<FakeIdentity> fetched = fetcher.fetchNewIdentities(Set.of(), 5, 3, Duration.ZERO, () -> false);

        assertTrue(fetched.isEmpty());
        assertEquals(1, fetcher.exclusionSetsSeen.size());
    }

    @Test
    void batchExcludesNamesItAlreadyFoundEarlierInTheSameBatch() {
        RecordingFetcher fetcher = new RecordingFetcher(List.of(
                new FakeIdentity("First_Name", "v1", "s1"),
                new FakeIdentity("Second_Name", "v2", "s2")));

        List<FakeIdentity> fetched = fetcher.fetchNewIdentities(Set.of("Preexisting"), 2, 3, Duration.ZERO, () -> false);

        assertEquals(2, fetched.size());
        assertTrue(fetcher.exclusionSetsSeen.get(0).contains("Preexisting"));
        assertFalse(fetcher.exclusionSetsSeen.get(0).contains("First_Name"));
        assertTrue(fetcher.exclusionSetsSeen.get(1).contains("First_Name"),
                "The second lookup must know about the name the first one just claimed");
    }

    @Test
    void batchHonoursTheAbortSignal() {
        RecordingFetcher fetcher = new RecordingFetcher(List.of(
                new FakeIdentity("First_Name", "v1", "s1"),
                new FakeIdentity("Second_Name", "v2", "s2")));

        List<FakeIdentity> fetched = fetcher.fetchNewIdentities(Set.of(), 2, 3, Duration.ZERO, () -> true);

        assertTrue(fetched.isEmpty());
        assertTrue(fetcher.exclusionSetsSeen.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ab", "this_name_is_far_too_long", "bad name", "bad/name", "../secret", "name?x=1", "naïve"})
    void invalidNamesAreRejectedWithoutAnyRequest(String invalidName) {
        StubbedHttpFetcher fetcher = new StubbedHttpFetcher(Map.of());

        Optional<FakeIdentity> identity = assertDoesNotThrow(() -> fetcher.fetchIdentityByName(invalidName));

        assertTrue(identity.isEmpty());
        assertTrue(fetcher.requestedUris.isEmpty());
    }

    @Test
    void nullNameIsRejectedWithoutAnyRequest() {
        StubbedHttpFetcher fetcher = new StubbedHttpFetcher(Map.of());

        assertTrue(fetcher.fetchIdentityByName(null).isEmpty());
        assertTrue(fetcher.requestedUris.isEmpty());
    }

    @Test
    void usesTheCanonicalNameFromTheMojangResponse() {
        StubbedHttpFetcher fetcher = new StubbedHttpFetcher(Map.of(
                LOOKUP_PREFIX + "nOtCh", new StubbedResponse(200, profileBody("Notch")),
                SESSION_PREFIX + NOTCH_UUID + SKIN_QUERY, new StubbedResponse(200, skinBody("skinValue", "skinSignature"))));

        Optional<FakeIdentity> identity = fetcher.fetchIdentityByName("nOtCh");

        assertEquals(Optional.of(new FakeIdentity("Notch", "skinValue", "skinSignature")), identity);
    }

    @Test
    void readsTheTexturesPropertyEvenWhenOtherPropertiesComeFirst() {
        String skinResponse = "{\"id\":\"" + NOTCH_UUID + "\",\"name\":\"Notch\",\"properties\":["
                + "{\"name\":\"other\",\"value\":\"wrongValue\",\"signature\":\"wrongSignature\"},"
                + "{\"name\":\"textures\",\"value\":\"rightValue\",\"signature\":\"rightSignature\"}]}";
        StubbedHttpFetcher fetcher = new StubbedHttpFetcher(Map.of(
                LOOKUP_PREFIX + "Notch", new StubbedResponse(200, profileBody("Notch")),
                SESSION_PREFIX + NOTCH_UUID + SKIN_QUERY, new StubbedResponse(200, skinResponse)));

        assertEquals(Optional.of(new FakeIdentity("Notch", "rightValue", "rightSignature")),
                fetcher.fetchIdentityByName("Notch"));
    }

    @Test
    void unknownAccountYieldsEmpty() {
        StubbedHttpFetcher fetcher = new StubbedHttpFetcher(Map.of(
                LOOKUP_PREFIX + "Nobody", new StubbedResponse(404, "")));

        assertTrue(fetcher.fetchIdentityByName("Nobody").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not json at all",
            "[]",
            "{}",
            "{\"id\":42,\"name\":\"Notch\"}",
            "{\"id\":null,\"name\":\"Notch\"}",
            "{\"id\":\"../../x\",\"name\":\"Notch\"}",
            "{\"id\":\"069a79f444e94726a5befca90e38aaf5\"}",
            "{\"id\":\"069a79f444e94726a5befca90e38aaf5\",\"name\":\"bad name\"}"})
    void malformedProfileResponsesYieldEmptyInsteadOfThrowing(String responseBody) {
        StubbedHttpFetcher fetcher = new StubbedHttpFetcher(Map.of(
                LOOKUP_PREFIX + "Notch", new StubbedResponse(200, responseBody)));

        Optional<FakeIdentity> identity = assertDoesNotThrow(() -> fetcher.fetchIdentityByName("Notch"));

        assertTrue(identity.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not json",
            "{}",
            "{\"properties\":\"nope\"}",
            "{\"properties\":[1,null]}",
            "{\"properties\":[{\"name\":\"textures\"}]}",
            "{\"properties\":[{\"name\":\"textures\",\"value\":\"only\"}]}"})
    void malformedSkinResponsesYieldEmptyInsteadOfThrowing(String responseBody) {
        StubbedHttpFetcher fetcher = new StubbedHttpFetcher(Map.of(
                LOOKUP_PREFIX + "Notch", new StubbedResponse(200, profileBody("Notch")),
                SESSION_PREFIX + NOTCH_UUID + SKIN_QUERY, new StubbedResponse(200, responseBody)));

        Optional<FakeIdentity> identity = assertDoesNotThrow(() -> fetcher.fetchIdentityByName("Notch"));

        assertTrue(identity.isEmpty());
    }

    private static String profileBody(String name) {
        return "{\"id\":\"" + NOTCH_UUID + "\",\"name\":\"" + name + "\"}";
    }

    private static String skinBody(String value, String signature) {
        return "{\"id\":\"" + NOTCH_UUID + "\",\"name\":\"Notch\",\"properties\":[{\"name\":\"textures\",\"value\":\""
                + value + "\",\"signature\":\"" + signature + "\"}]}";
    }

    private static final class RecordingFetcher extends MojangIdentityFetcher {
        private final List<FakeIdentity> canned;
        private final List<Set<String>> exclusionSetsSeen = new ArrayList<>();
        private int calls;

        private RecordingFetcher(List<FakeIdentity> canned) {
            this.canned = canned;
        }

        @Override
        public Optional<FakeIdentity> fetchNewIdentity(Set<String> excludedNames, int maxAttempts) {
            exclusionSetsSeen.add(new HashSet<>(excludedNames));
            return calls < canned.size() ? Optional.of(canned.get(calls++)) : Optional.empty();
        }
    }

    private record StubbedResponse(int statusCode, String body) implements HttpResponse<String> {
        @Override
        public HttpRequest request() {
            return null;
        }

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(), (headerName, headerValue) -> true);
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return URI.create("https://localhost");
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_2;
        }
    }

    private static final class StubbedHttpFetcher extends MojangIdentityFetcher {
        private final Map<String, StubbedResponse> responsesByUri;
        private final List<String> requestedUris = new ArrayList<>();

        private StubbedHttpFetcher(Map<String, StubbedResponse> responsesByUri) {
            this.responsesByUri = responsesByUri;
        }

        @Override
        Optional<HttpResponse<String>> getWithRetry(String uri) {
            requestedUris.add(uri);
            return Optional.ofNullable(responsesByUri.get(uri));
        }
    }
}
