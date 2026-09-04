package com.fakerevive.disguise;

import org.bukkit.configuration.file.FileConfiguration;

import java.time.Duration;

/**
 * Tuning for the background name refill, read from the {@code fake-names} block of config.yml.
 * Every value is clamped on the way in: a mistyped config must not be able to point a burst of
 * requests at Mojang and get the server IP rate limited.
 */
public record ReplenishSettings(boolean enabled, int targetSize, int lowWaterMark, int batchSize,
                                long intervalTicks, Duration requestSpacing, int maxAttemptsPerIdentity,
                                boolean reuseDonorSkins) {

    private static final int MIN_TARGET_SIZE = 1;
    private static final int MAX_TARGET_SIZE = 500;
    private static final int MIN_BATCH_SIZE = 1;
    /** Bounds the worst-case runtime of a single batch, which matters at shutdown. */
    private static final int MAX_BATCH_SIZE = 50;
    private static final long MIN_INTERVAL_SECONDS = 30L;
    private static final long MIN_REQUEST_SPACING_MILLIS = 200L;
    private static final int MIN_ATTEMPTS_PER_IDENTITY = 1;
    private static final int MAX_ATTEMPTS_PER_IDENTITY = 100;
    private static final long TICKS_PER_SECOND = 20L;

    public static ReplenishSettings fromConfig(FileConfiguration config) {
        boolean enabled = config.getBoolean("fake-names.auto-refill", true);
        int targetSize = clamp(config.getInt("fake-names.target-pool-size", 60), MIN_TARGET_SIZE, MAX_TARGET_SIZE);
        int lowWaterMark = Math.min(clamp(config.getInt("fake-names.low-water-mark", 25), 0, MAX_TARGET_SIZE), targetSize);
        int batchSize = clamp(config.getInt("fake-names.batch-size", 10), MIN_BATCH_SIZE, MAX_BATCH_SIZE);
        long intervalSeconds = Math.max(config.getLong("fake-names.refill-interval-seconds", 300L), MIN_INTERVAL_SECONDS);
        long spacingMillis = Math.max(config.getLong("fake-names.request-spacing-millis", 1200L), MIN_REQUEST_SPACING_MILLIS);
        int maxAttempts = clamp(config.getInt("fake-names.max-attempts-per-identity", 25),
                MIN_ATTEMPTS_PER_IDENTITY, MAX_ATTEMPTS_PER_IDENTITY);
        boolean reuseDonorSkins = config.getBoolean("fake-names.reuse-donor-skins", true);

        return new ReplenishSettings(enabled, targetSize, lowWaterMark, batchSize,
                intervalSeconds * TICKS_PER_SECOND, Duration.ofMillis(spacingMillis), maxAttempts, reuseDonorSkins);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
