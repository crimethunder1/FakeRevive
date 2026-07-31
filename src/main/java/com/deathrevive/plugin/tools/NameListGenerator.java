package com.deathrevive.plugin.tools;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Standalone dev tool (not wired into the plugin runtime) that generates realistic-looking
 * Minecraft username candidates, checks each against the real Mojang API to confirm it doesn't
 * belong to an existing account, and pairs each verified-free name with a skin borrowed from a
 * real account encountered as TAKEN (fetched once via the Mojang session-server and embedded as
 * a signed texture, so /revive needs no network access at runtime). Writes the paired identities
 * to names.json.
 *
 * Run manually, e.g.:
 *   javac -d out src/main/java/com/deathrevive/plugin/tools/NameListGenerator.java
 *   java -cp out com.deathrevive.plugin.tools.NameListGenerator src/main/resources/names.json
 */
public final class NameListGenerator {

    private static final String[] ADJECTIVES = {
            "Silent", "Shadow", "Crimson", "Frozen", "Golden", "Swift", "Dark", "Ancient", "Wild", "Blazing",
            "Rusty", "Mystic", "Savage", "Lucky", "Grim", "Bold", "Quiet", "Sneaky", "Feral", "Toxic",
            "Neon", "Cosmic", "Rogue", "Vivid", "Brave", "Cursed", "Wicked", "Noble", "Iron", "Stormy",
            "Rapid", "Bitter", "Lone", "Sly", "Fierce", "Icy", "Molten", "Ghostly", "Deadly", "Windy",
            "Happy", "Lazy", "Crazy", "Epic", "Super", "Turbo", "Retro", "Cool", "Chill", "Angry",
            "Jolly", "Wacky", "Funky", "Groovy", "Spicy", "Salty", "Sweet", "Sour", "Bright", "Dim",
            "Sharp", "Dull", "Heavy", "Quick", "Slow", "Loud", "Soft", "Hard", "Smooth", "Rough",
            "Tiny", "Giant", "Mini", "Wide", "Thin", "Slim", "Round", "Square", "Curly", "Spiky",
            "Fuzzy", "Shiny", "Glowing", "Sparkly", "Misty", "Foggy", "Sunny", "Rainy", "Snowy", "Cloudy",
            "Electric", "Magnetic", "Atomic", "Solar", "Lunar", "Astral", "Royal", "Regal", "Divine", "Sacred",
            "Rebel", "Hidden", "Secret", "Masked", "Phantom", "Spectral", "Eternal", "Infinite", "Endless", "Timeless",
            "Chaotic", "Wandering", "Roaming", "Drifting", "Hunting", "Prowling", "Stalking", "Lurking", "Vanishing", "Fading",
            "Burning", "Freezing", "Melting", "Shining", "Gleaming", "Radiant", "Dazzling", "Brilliant", "Vibrant", "Steel",
            "Copper", "Silver", "Bronze", "Platinum", "Diamond", "Ruby", "Emerald", "Sapphire", "Obsidian", "Amber"
    };

    private static final String[] NOUNS = {
            "Wolf", "Falcon", "Ninja", "Knight", "Ghost", "Raven", "Tiger", "Dragon", "Pirate", "Ranger",
            "Hunter", "Viper", "Panther", "Phoenix", "Golem", "Wizard", "Archer", "Bandit", "Reaper", "Goblin",
            "Yeti", "Sniper", "Cobra", "Badger", "Otter", "Falconer", "Rider", "Sailor", "Miner", "Smith",
            "Nomad", "Warden", "Scout", "Hawk", "Lynx", "Puma", "Drifter", "Marauder", "Sentinel", "Gamer",
            "Player", "King", "Queen", "Legend", "Boss", "Star", "Panda", "Fox", "Bear", "Shark",
            "Eagle", "Owl", "Bat", "Snake", "Spider", "Scorpion", "Mantis", "Beetle", "Wasp", "Hornet",
            "Lion", "Leopard", "Cheetah", "Jaguar", "Bison", "Buffalo", "Elk", "Moose", "Deer", "Rabbit",
            "Squirrel", "Raccoon", "Skunk", "Weasel", "Ferret", "Mole", "Hedgehog", "Porcupine", "Beaver", "Chipmunk",
            "Crow", "Sparrow", "Robin", "Cardinal", "Woodpecker", "Pelican", "Heron", "Stork", "Swan", "Duck",
            "Goose", "Turkey", "Rooster", "Peacock", "Parrot", "Toucan", "Flamingo", "Penguin", "Puffin", "Albatross",
            "Whale", "Dolphin", "Orca", "Narwhal", "Squid", "Octopus", "Jellyfish", "Crab", "Lobster", "Turtle",
            "Frog", "Toad", "Newt", "Salamander", "Chameleon", "Iguana", "Gecko", "Lizard", "Crocodile", "Alligator",
            "Zombie", "Skeleton", "Vampire", "Werewolf", "Demon", "Angel", "Titan", "Colossus", "Behemoth", "Kraken",
            "Hydra", "Griffin", "Chimera", "Basilisk", "Wyvern", "Serpent", "Mummy", "Specter", "Wraith", "Banshee",
            "Samurai", "Shogun", "Warrior", "Gladiator", "Berserker", "Paladin", "Templar", "Crusader", "Monk", "Sage",
            "Oracle", "Prophet", "Seer", "Alchemist", "Sorcerer", "Enchanter", "Druid", "Shaman", "Priest", "Captain",
            "General", "Commander", "Admiral", "Chief", "Marshal", "Colonel", "Major", "Sergeant", "Private", "Rocket",
            "Comet", "Meteor", "Nova", "Nebula", "Galaxy", "Cosmos", "Void", "Storm", "Blaze", "Frost",
            "Ember", "Flame", "Spark", "Bolt", "Thunder", "Quake", "Tremor", "Avalanche", "Cyclone", "Tornado",
            "Hurricane", "Blizzard", "Monsoon", "Tsunami", "Vortex", "Eclipse", "Zenith", "Apex", "Summit", "Peak"
    };

    private static final String[] SINGLE_WORDS = {
            "Steve", "Alex", "Notch", "Pixel", "Nova", "Zero", "Echo", "Comet", "Nebula", "Cosmo",
            "Orbit", "Lunar", "Solar", "Astro", "Turbo", "Nitro", "Cyber", "Byte", "Chip", "Glitch",
            "Rex", "Max", "Leo", "Jax", "Kai", "Finn", "Zane", "Cole", "Ryder", "Axel",
            "Milo", "Otto", "Hugo", "Theo", "Remy", "Beau", "Jett", "Ace", "Duke", "Earl",
            "Baron", "Knox", "Flynn", "Blitz", "Dash", "Zippy", "Nimbus", "Cirrus", "Cumulus", "Static",
            "Voltage", "Circuit", "Vector", "Matrix", "Cipher", "Cortex", "Nexus", "Pulse", "Signal", "Beacon",
            "Horizon", "Summit", "Ridge", "Canyon", "Mesa", "Delta", "Sigma", "Omega", "Alpha", "Gamma",
            "Kappa", "Onyx", "Jade", "Topaz", "Pearl", "Ivory", "Ebony", "Slate", "Marble", "Granite",
            "Cobalt", "Indigo", "Violet", "Azure", "Coral", "Willow", "Cedar", "Maple", "Birch", "Aspen",
            "Rowan", "Fern", "Clover", "Meadow", "Brook", "River", "Lake", "Ocean", "Reef", "Dune"
    };

    private static final String[] PREFIXES = {
            "The", "Mr", "Mrs", "Its", "Real", "Not", "Lord", "Sir", "Captain", "King", "Queen", "Xx", "Yo", "Big"
    };

    private static final String[] SUFFIXES = {
            "YT", "TV", "Gaming", "Gamer", "Plays", "Pro", "HD", "OP", "X", "Z", "Xx", "Boy", "Girl", "Kid"
    };

    private static final long GENERATION_SEED = 42L;
    private static final int TARGET_NAME_COUNT = 5000;
    private static final int TARGET_DONOR_COUNT = TARGET_NAME_COUNT;
    private static final int MAX_CANDIDATE_POOL = 60_000;
    private static final int MAX_NAME_LENGTH = 16;
    private static final int MIN_NAME_LENGTH = 3;
    private static final Duration REQUEST_DELAY = Duration.ofMillis(700);
    private static final Duration INITIAL_BACKOFF = Duration.ofSeconds(2);
    private static final int MAX_RETRIES_ON_RATE_LIMIT = 5;
    private static final Path NAME_PROGRESS_FILE = Path.of("tools-output", "name-generator-progress.txt");
    private static final Path DONOR_PROGRESS_FILE = Path.of("tools-output", "skin-donor-progress.txt");
    private static final String NAME_LOOKUP_URI_PREFIX = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String SESSION_SERVER_URI_PREFIX = "https://sessionserver.mojang.com/session/minecraft/profile/";

    private record ProfileLookup(boolean free, String uuid) {
    }

    private record SkinDonor(String uuid, String name, String value, String signature) {
    }

    private NameListGenerator() {
    }

    public static void main(String[] args) throws Exception {
        Path outputFile = Path.of(args.length > 0 ? args[0] : "src/main/resources/names.json");

        // Resuming from a prior run: skip candidates already checked (no repeat API calls) and
        // seed freeNames/donors from what a prior run already found, so an interrupted
        // multi-hour run can just be re-invoked to keep making progress.
        Map<String, String> nameProgress = loadProgress(NAME_PROGRESS_FILE);
        List<String> freeNames = new ArrayList<>();
        for (Map.Entry<String, String> entry : nameProgress.entrySet()) {
            if ("FREE".equals(entry.getValue())) {
                freeNames.add(entry.getKey());
            }
        }
        System.out.println("Loaded " + nameProgress.size() + " previously checked candidates ("
                + freeNames.size() + " free) from " + NAME_PROGRESS_FILE);

        Map<String, SkinDonor> donors = loadDonors(DONOR_PROGRESS_FILE);
        System.out.println("Loaded " + donors.size() + " previously fetched skin donors from " + DONOR_PROGRESS_FILE);

        HttpClient httpClient = HttpClient.newHttpClient();

        try {
            // A name already known to be TAKEN belongs to a real account we can borrow a skin
            // from - harvest those before spending any calls checking new candidates.
            for (Map.Entry<String, String> entry : nameProgress.entrySet()) {
                if (donors.size() >= TARGET_DONOR_COUNT) {
                    break;
                }
                String candidate = entry.getKey();
                if (!"TAKEN".equals(entry.getValue()) || donors.containsKey(candidate)) {
                    continue;
                }
                Optional<ProfileLookup> profile = lookupProfile(httpClient, candidate);
                if (profile.isPresent() && !profile.get().free()) {
                    fetchAndStoreDonor(httpClient, candidate, profile.get().uuid(), donors);
                    writeIdentitiesJson(outputFile, freeNames, donors);
                }
            }

            List<String> candidates = buildCandidates();
            System.out.println("Built " + candidates.size() + " candidates, checking against Mojang API...");

            for (String candidate : candidates) {
                boolean needMoreNames = freeNames.size() < TARGET_NAME_COUNT;
                boolean needMoreDonors = donors.size() < TARGET_DONOR_COUNT;
                if (!needMoreNames && !needMoreDonors) {
                    break;
                }
                if (nameProgress.containsKey(candidate)) {
                    continue;
                }

                Optional<ProfileLookup> profile = lookupProfile(httpClient, candidate);
                if (profile.isEmpty()) {
                    appendProgress(NAME_PROGRESS_FILE, "SKIPPED", candidate);
                    nameProgress.put(candidate, "SKIPPED");
                    continue;
                }

                if (profile.get().free()) {
                    appendProgress(NAME_PROGRESS_FILE, "FREE", candidate);
                    nameProgress.put(candidate, "FREE");
                    if (needMoreNames) {
                        freeNames.add(candidate);
                        System.out.println("Free name: " + candidate + " (" + freeNames.size() + "/" + TARGET_NAME_COUNT + ")");
                    }
                } else {
                    appendProgress(NAME_PROGRESS_FILE, "TAKEN", candidate);
                    nameProgress.put(candidate, "TAKEN");
                    if (needMoreDonors) {
                        fetchAndStoreDonor(httpClient, candidate, profile.get().uuid(), donors);
                    }
                }

                writeIdentitiesJson(outputFile, freeNames, donors);
            }
        } finally {
            writeIdentitiesJson(outputFile, freeNames, donors);
            System.out.println("Wrote " + Math.min(freeNames.size(), donors.size())
                    + " paired identities to " + outputFile.toAbsolutePath());
        }
    }

    private static List<String> buildCandidates() {
        Set<String> candidates = new LinkedHashSet<>();
        Random random = new Random(GENERATION_SEED);
        List<String> combinedBases = new ArrayList<>();

        // Adjective+Noun pairs in rotating styles (CamelCase / underscore / lowercase) so no
        // single style dominates if the verification pass is interrupted partway through.
        int pairIndex = 0;
        adjectiveLoop:
        for (String adjective : ADJECTIVES) {
            for (String noun : NOUNS) {
                if (candidates.size() >= MAX_CANDIDATE_POOL) {
                    break adjectiveLoop;
                }
                String camel = adjective + noun;
                combinedBases.add(camel);

                int style = pairIndex % 3;
                if (style == 0) {
                    addIfValid(candidates, camel);
                } else if (style == 1) {
                    addIfValid(candidates, adjective + "_" + noun);
                } else {
                    addIfValid(candidates, camel.toLowerCase(Locale.ROOT));
                }
                pairIndex++;
            }
        }

        for (String word : SINGLE_WORDS) {
            addNumberedVariants(candidates, word, 6, 1, 999, random);
            addNumberedVariants(candidates, word, 2, 1970, 2025, random);
        }

        for (String noun : NOUNS) {
            addNumberedVariants(candidates, noun, 3, 1, 999, random);
        }

        for (int i = 0; i < combinedBases.size(); i += 20) {
            addNumberedVariants(candidates, combinedBases.get(i), 2, 1, 9999, random);
        }

        List<String> allWords = new ArrayList<>(NOUNS.length + SINGLE_WORDS.length);
        Collections.addAll(allWords, NOUNS);
        Collections.addAll(allWords, SINGLE_WORDS);

        int prefixIndex = 0;
        for (String prefix : PREFIXES) {
            for (String word : allWords) {
                addIfValid(candidates, prefix + word);
                if (prefixIndex % 4 == 0) {
                    addNumberedVariants(candidates, prefix + word, 1, 1, 99, random);
                }
                prefixIndex++;
            }
        }

        for (String suffix : SUFFIXES) {
            for (String word : allWords) {
                addIfValid(candidates, word + suffix);
            }
        }

        List<String> result = new ArrayList<>(candidates);
        // Fixed-seed shuffle: a partial/interrupted verification pass still yields a
        // representative mix across all pattern families instead of exhausting one style first.
        Collections.shuffle(result, random);
        return result;
    }

    private static void addNumberedVariants(
            Set<String> candidates, String base, int count, int minValue, int maxValue, Random random) {
        for (int i = 0; i < count && candidates.size() < MAX_CANDIDATE_POOL; i++) {
            int value = minValue + random.nextInt(maxValue - minValue + 1);
            addIfValid(candidates, base + value);
        }
    }

    private static void addIfValid(Set<String> candidates, String name) {
        if (name.length() >= MIN_NAME_LENGTH
                && name.length() <= MAX_NAME_LENGTH
                && name.matches("[A-Za-z0-9_]+")) {
            candidates.add(name);
        }
    }

    /**
     * @return empty if the name is currently unclaimed (404), or free/taken info otherwise; the
     *         request could not be completed (rate-limited or network-flaky past the retry
     *         budget) is signalled by an empty Optional at the call site.
     */
    private static Optional<ProfileLookup> lookupProfile(HttpClient httpClient, String name) throws InterruptedException {
        Optional<HttpResponse<String>> response = getWithRetry(httpClient, NAME_LOOKUP_URI_PREFIX + name);
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

    private static void fetchAndStoreDonor(
            HttpClient httpClient, String name, String uuid, Map<String, SkinDonor> donors) throws InterruptedException {
        Optional<HttpResponse<String>> response = getWithRetry(httpClient, SESSION_SERVER_URI_PREFIX + uuid);
        if (response.isEmpty() || response.get().statusCode() != 200) {
            return;
        }
        String body = response.get().body();
        Optional<String> value = extractJsonField(body, "value");
        Optional<String> signature = extractJsonField(body, "signature");
        if (value.isEmpty() || signature.isEmpty()) {
            return;
        }

        SkinDonor donor = new SkinDonor(uuid, name, value.get(), signature.get());
        donors.put(name, donor);
        appendDonorProgress(DONOR_PROGRESS_FILE, donor);
        System.out.println("Skin donor: " + name + " (" + donors.size() + "/" + TARGET_DONOR_COUNT + ")");
    }

    private static Optional<String> extractJsonField(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    /**
     * @return empty if the request could not be completed (rate-limited or network-flaky past
     *         the retry budget), otherwise the response (whatever its status code).
     */
    private static Optional<HttpResponse<String>> getWithRetry(HttpClient httpClient, String uri) throws InterruptedException {
        Duration backoff = INITIAL_BACKOFF;

        for (int attempt = 0; attempt <= MAX_RETRIES_ON_RATE_LIMIT; attempt++) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uri))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            String retryReason;
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 429) {
                    Thread.sleep(REQUEST_DELAY.toMillis());
                    return Optional.of(response);
                }
                retryReason = "429 rate limit";
            } catch (IOException e) {
                retryReason = e.getClass().getSimpleName() + ": " + e.getMessage();
            }

            if (attempt == MAX_RETRIES_ON_RATE_LIMIT) {
                System.out.println("Giving up on " + uri + " after repeated failures (" + retryReason + ")");
                return Optional.empty();
            }

            System.out.println("Retrying " + uri + " after " + retryReason + ", backing off "
                    + backoff.getSeconds() + "s");
            Thread.sleep(backoff.toMillis());
            backoff = backoff.multipliedBy(2);
        }

        return Optional.empty();
    }

    private static Map<String, String> loadProgress(Path progressFile) {
        if (!Files.exists(progressFile)) {
            return new LinkedHashMap<>();
        }
        Map<String, String> statuses = new LinkedHashMap<>();
        try {
            for (String line : Files.readAllLines(progressFile, StandardCharsets.UTF_8)) {
                int space = line.indexOf(' ');
                if (space < 0) {
                    continue;
                }
                statuses.put(line.substring(space + 1), line.substring(0, space));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return statuses;
    }

    private static void appendProgress(Path progressFile, String status, String name) {
        try {
            if (progressFile.getParent() != null) {
                Files.createDirectories(progressFile.getParent());
            }
            try (BufferedWriter writer = Files.newBufferedWriter(progressFile, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                writer.write(status + " " + name);
                writer.newLine();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Map<String, SkinDonor> loadDonors(Path donorFile) {
        if (!Files.exists(donorFile)) {
            return new LinkedHashMap<>();
        }
        Map<String, SkinDonor> donors = new LinkedHashMap<>();
        try {
            for (String line : Files.readAllLines(donorFile, StandardCharsets.UTF_8)) {
                String[] parts = line.split(" ", 4);
                if (parts.length != 4) {
                    continue;
                }
                donors.put(parts[1], new SkinDonor(parts[0], parts[1], parts[2], parts[3]));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return donors;
    }

    private static void appendDonorProgress(Path donorFile, SkinDonor donor) {
        try {
            if (donorFile.getParent() != null) {
                Files.createDirectories(donorFile.getParent());
            }
            try (BufferedWriter writer = Files.newBufferedWriter(donorFile, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                writer.write(donor.uuid() + " " + donor.name() + " " + donor.value() + " " + donor.signature());
                writer.newLine();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writeIdentitiesJson(Path outputFile, List<String> freeNames, Map<String, SkinDonor> donors)
            throws IOException {
        List<SkinDonor> donorList = new ArrayList<>(donors.values());
        int pairCount = Math.min(freeNames.size(), donorList.size());

        StringBuilder json = new StringBuilder("[\n");
        for (int i = 0; i < pairCount; i++) {
            SkinDonor donor = donorList.get(i);
            json.append("  {\"name\": \"").append(freeNames.get(i)).append("\", ")
                    .append("\"skinValue\": \"").append(donor.value()).append("\", ")
                    .append("\"skinSignature\": \"").append(donor.signature()).append("\"}");
            json.append(i < pairCount - 1 ? ",\n" : "\n");
        }
        json.append("]\n");

        if (outputFile.getParent() != null) {
            Files.createDirectories(outputFile.getParent());
        }
        Files.writeString(outputFile, json.toString(), StandardCharsets.UTF_8);
    }
}
