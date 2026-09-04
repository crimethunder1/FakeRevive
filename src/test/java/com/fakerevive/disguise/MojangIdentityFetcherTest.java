package com.fakerevive.disguise;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Network-free tests. The candidate generators are package-private precisely so their output can
 * be checked without touching the Mojang API, and the batch tests drive a subclass whose
 * {@code fetchNewIdentity} never leaves the JVM.
 */
class MojangIdentityFetcherTest {

    private static final Pattern LEGAL_MINECRAFT_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final int DRAWS = 10_000;

    @Test
    void freeCandidateNamesAreAlwaysLegalMinecraftNames() {
        MojangIdentityFetcher fetcher = new MojangIdentityFetcher();

        for (int i = 0; i < DRAWS; i++) {
            String name = fetcher.randomFreeCandidateName();
            assertTrue(LEGAL_MINECRAFT_NAME.matcher(name).matches(), "Illegal candidate name: " + name);
        }
    }

    /**
     * Regression guard for the repeating-names bug: the old generator drew from 30x30 word pairs
     * and truncated anything over 16 characters, so distinct draws collapsed onto the same string.
     */
    @Test
    void freeCandidateNamesAreOverwhelminglyDistinct() {
        MojangIdentityFetcher fetcher = new MojangIdentityFetcher();

        Set<String> distinct = new HashSet<>();
        for (int i = 0; i < DRAWS; i++) {
            distinct.add(fetcher.randomFreeCandidateName());
        }

        assertTrue(distinct.size() > 9_000,
                "Expected well over 9000 distinct names out of " + DRAWS + " draws, got " + distinct.size());
    }

    @Test
    void donorCandidateNamesCarryNoDigits() {
        MojangIdentityFetcher fetcher = new MojangIdentityFetcher();

        for (int i = 0; i < DRAWS; i++) {
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

    /** Hands back canned identities and records the exclusion set it was given each time. */
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
}
