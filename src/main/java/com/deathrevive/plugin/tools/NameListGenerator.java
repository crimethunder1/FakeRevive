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

    private static final int TARGET_NAME_COUNT = 500;
    private static final int MAX_NAME_LENGTH = 16;
    private static final int MIN_NAME_LENGTH = 3;
    private static final Duration REQUEST_DELAY = Duration.ofMillis(200);

    private NameListGenerator() {
    }

    public static void main(String[] args) throws Exception {
        Path outputFile = Path.of(args.length > 0 ? args[0] : "src/main/resources/names.json");

        List<String> candidates = buildCandidates();
        HttpClient httpClient = HttpClient.newHttpClient();
        List<String> freeNames = new ArrayList<>();

        for (String candidate : candidates) {
            if (freeNames.size() >= TARGET_NAME_COUNT) {
                break;
            }
            if (isFreeOnMojang(httpClient, candidate)) {
                freeNames.add(candidate);
                System.out.println("Free: " + candidate + " (" + freeNames.size() + "/" + TARGET_NAME_COUNT + ")");
            }
            Thread.sleep(REQUEST_DELAY.toMillis());
        }

        writeJson(outputFile, freeNames);
        System.out.println("Wrote " + freeNames.size() + " names to " + outputFile.toAbsolutePath());
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
            for (int i = 0; i < 3 && candidates.size() < TARGET_NAME_COUNT * 4; i++) {
                int suffix = 1 + random.nextInt(999);
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

    private static boolean isFreeOnMojang(HttpClient httpClient, String name) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + name))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode() == 404;
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
