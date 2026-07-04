package com.deathrevive.plugin.tools;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

public final class NameListGenerator {

    private static final String[] ADJECTIVES = {
            "Silent", "Shadow", "Crimson", "Frozen", "Golden", "Swift", "Dark", "Ancient", "Wild", "Blazing",
            "Rusty", "Mystic", "Savage", "Lucky", "Grim", "Bold", "Quiet", "Sneaky", "Feral", "Toxic",
            "Neon", "Cosmic", "Rogue", "Vivid", "Brave", "Cursed", "Wicked", "Noble", "Iron", "Stormy",
            "Rapid", "Bitter", "Lone", "Sly", "Fierce", "Icy", "Molten", "Ghostly", "Deadly", "Windy"
    };

    private static final String[] NOUNS = {
            "Wolf", "Falcon", "Ninja", "Knight", "Ghost", "Raven", "Tiger", "Dragon", "Pirate", "Ranger",
            "Hunter", "Viper", "Panther", "Phoenix", "Golem", "Wizard", "Archer", "Bandit", "Reaper", "Goblin",
            "Yeti", "Sniper", "Cobra", "Badger", "Otter", "Falconer", "Rider", "Sailor", "Miner", "Smith",
            "Nomad", "Rogue", "Warden", "Scout", "Hawk", "Lynx", "Puma", "Drifter", "Marauder", "Sentinel"
    };

    private static final int TARGET_NAME_COUNT = 5000;
    private static final int MAX_CANDIDATE_POOL = TARGET_NAME_COUNT * 8;
    private static final int MAX_SUFFIXES_PER_BASE_NAME = 20;
    private static final int MAX_SUFFIX_VALUE = 9999;
    private static final int MAX_NAME_LENGTH = 16;
    private static final int MIN_NAME_LENGTH = 3;
    private static final Duration REQUEST_DELAY = Duration.ofMillis(700);
    private static final Duration INITIAL_BACKOFF = Duration.ofSeconds(2);
    private static final int MAX_RETRIES_ON_RATE_LIMIT = 5;

    private NameListGenerator() {
    }

    public static void main(String[] args) throws Exception {
        Path outputFile = Path.of(args.length > 0 ? args[0] : "src/main/resources/names.json");

        List<String> candidates = buildCandidates();
        System.out.println("Built " + candidates.size() + " candidates, checking against Mojang API...");

        HttpClient httpClient = HttpClient.newHttpClient();
        List<String> freeNames = new ArrayList<>();

        try {
            for (String candidate : candidates) {
                if (freeNames.size() >= TARGET_NAME_COUNT) {
                    break;
                }

                Optional<Boolean> free = isFreeOnMojang(httpClient, candidate);
                if (free.isEmpty()) {
                    continue;
                }
                if (free.get()) {
                    freeNames.add(candidate);
                    System.out.println(
                            "Free: " + candidate + " (" + freeNames.size() + "/" + TARGET_NAME_COUNT + ")");
                }

                Thread.sleep(REQUEST_DELAY.toMillis());
            }
        } finally {
            writeJson(outputFile, freeNames);
            System.out.println("Wrote " + freeNames.size() + " names to " + outputFile.toAbsolutePath());
        }
    }

    private static List<String> buildCandidates() {
        Set<String> candidates = new LinkedHashSet<>();
        Random random = new Random(42);

        for (String adjective : ADJECTIVES) {
            for (String noun : NOUNS) {
                addIfValid(candidates, adjective + "_" + noun);
            }
        }

        List<String> base = new ArrayList<>(candidates);
        for (String name : base) {
            for (int i = 0; i < MAX_SUFFIXES_PER_BASE_NAME && candidates.size() < MAX_CANDIDATE_POOL; i++) {
                int suffix = 1 + random.nextInt(MAX_SUFFIX_VALUE);
                addIfValid(candidates, name + suffix);
            }
        }

        return new ArrayList<>(candidates);
    }

    private static void addIfValid(Set<String> candidates, String name) {
        if (name.length() >= MIN_NAME_LENGTH
                && name.length() <= MAX_NAME_LENGTH
                && name.matches("[A-Za-z0-9_]+")) {
            candidates.add(name);
        }
    }

    /**
     * @return empty if the name could not be checked (rate-limited or network-flaky past the retry
     *         budget), otherwise whether the name is free to use.
     */
    private static Optional<Boolean> isFreeOnMojang(HttpClient httpClient, String name) throws InterruptedException {
        Duration backoff = INITIAL_BACKOFF;

        for (int attempt = 0; attempt <= MAX_RETRIES_ON_RATE_LIMIT; attempt++) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + name))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            String retryReason;
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 429) {
                    return Optional.of(response.statusCode() == 404);
                }
                retryReason = "429 rate limit";
            } catch (IOException e) {
                retryReason = e.getClass().getSimpleName() + ": " + e.getMessage();
            }

            if (attempt == MAX_RETRIES_ON_RATE_LIMIT) {
                System.out.println("Skipping " + name + " after repeated failures (" + retryReason + ")");
                return Optional.empty();
            }

            System.out.println("Retrying " + name + " after " + retryReason + ", backing off "
                    + backoff.getSeconds() + "s");
            Thread.sleep(backoff.toMillis());
            backoff = backoff.multipliedBy(2);
        }

        return Optional.empty();
    }

    private static void writeJson(Path outputFile, List<String> names) throws IOException {
        StringBuilder json = new StringBuilder("[\n");
        for (int i = 0; i < names.size(); i++) {
            json.append("  \"").append(names.get(i)).append("\"");
            json.append(i < names.size() - 1 ? ",\n" : "\n");
        }
        json.append("]\n");

        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, json.toString(), StandardCharsets.UTF_8);
    }
}
