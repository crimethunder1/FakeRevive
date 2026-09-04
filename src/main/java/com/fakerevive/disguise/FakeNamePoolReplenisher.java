package com.fakerevive.disguise;

import com.fakerevive.message.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Keeps the fake-name pool stocked with fresh, never-before-used identities pulled from the
 * Mojang API. Runs a repeating main-thread check that starts an async batch whenever the pool
 * has fallen to the configured low-water mark, and can be nudged directly from a command path
 * via {@link #requestTopUp()}.
 *
 * <p>Replaces the old per-revive top-up in {@code FakeReviveCommand}, which fetched at most one
 * name per assignment and - fatally - was only reachable while the pool still had names left, so
 * an empty pool could never refill itself.
 *
 * <p>Threading: {@link FakeNamePool} is main-thread-only. This class touches it only from
 * {@link #cycle()} and from the main-thread hop that applies a finished batch; the blocking HTTP
 * work in between never sees the pool.
 */
public class FakeNamePoolReplenisher {

    /** Give the server a moment to finish starting before the first check - never fetch in onEnable. */
    private static final long INITIAL_DELAY_TICKS = 100L;
    private static final long MAX_BACKOFF_MILLIS = TimeUnit.MINUTES.toMillis(30);

    private final JavaPlugin plugin;
    private final FakeNamePool pool;
    private final MojangIdentityFetcher fetcher;
    private final Logger logger;
    private final MessageService messageService;

    private final AtomicBoolean inFlight = new AtomicBoolean(false);
    private volatile ReplenishSettings settings;
    private volatile boolean shuttingDown;
    private volatile long backoffUntilMillis;
    private volatile int consecutiveFailures;
    private BukkitTask timerTask;

    public FakeNamePoolReplenisher(JavaPlugin plugin, FakeNamePool pool, MojangIdentityFetcher fetcher,
                                    Logger logger, MessageService messageService, ReplenishSettings settings) {
        this.plugin = plugin;
        this.pool = pool;
        this.fetcher = fetcher;
        this.logger = logger;
        this.messageService = messageService;
        this.settings = settings;
        fetcher.setReuseDonorSkins(settings.reuseDonorSkins());
    }

    /** Schedules the periodic pool check. Main thread; schedules only, never fetches inline. */
    public void start() {
        shuttingDown = false;
        scheduleTimer();
    }

    /** Main thread. Stops future checks and asks any running batch to bail out early. */
    public void stop() {
        shuttingDown = true;
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
    }

    /**
     * Applies reloaded config. Main thread. Deliberately leaves {@code inFlight} alone - a batch
     * that is still running owns that flag and will clear it in its own finally block.
     */
    public void applySettings(ReplenishSettings newSettings) {
        this.settings = newSettings;
        fetcher.setReuseDonorSkins(newSettings.reuseDonorSkins());
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
        if (!shuttingDown) {
            scheduleTimer();
        }
    }

    /**
     * Nudges the replenisher from a command path, so an empty pool starts refilling immediately
     * instead of waiting for the next scheduled check. Safe to call on every single revive: the
     * guards in {@link #cycle()} short-circuit before any real work.
     */
    public void requestTopUp() {
        cycle();
    }

    /** @return whether a batch is currently being fetched from Mojang. */
    public boolean isRefilling() {
        return inFlight.get();
    }

    /** @return the pool size this replenisher is refilling towards. */
    public int getTargetSize() {
        return settings.targetSize();
    }

    private void scheduleTimer() {
        timerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::cycle, INITIAL_DELAY_TICKS, settings.intervalTicks());
    }

    /**
     * Main thread. Decides whether a refill is due and, if so, snapshots the known names and
     * hands the network work to an async task.
     */
    private void cycle() {
        ReplenishSettings current = settings;
        if (!current.enabled() || shuttingDown) {
            return;
        }
        if (System.currentTimeMillis() < backoffUntilMillis) {
            return;
        }

        int available = pool.availableCount();
        if (available > current.lowWaterMark()) {
            return;
        }

        int wanted = Math.min(current.targetSize() - available, current.batchSize());
        if (wanted <= 0) {
            return;
        }

        // CAS before the snapshot: twenty simultaneous revives should cost one snapshot, not twenty.
        if (!inFlight.compareAndSet(false, true)) {
            return;
        }

        if (available == 0) {
            logger.warning(messageService.get("events.pool-low",
                    "available", String.valueOf(available), "target", String.valueOf(current.targetSize())));
        }

        Set<String> knownNames = pool.getKnownNames();
        try {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> runBatch(knownNames, wanted, current));
        } catch (IllegalPluginAccessException e) {
            inFlight.set(false);
        }
    }

    /** Async thread. Never touches {@link FakeNamePool}. */
    private void runBatch(Set<String> knownNames, int wanted, ReplenishSettings current) {
        try {
            List<FakeIdentity> fetched = runBatchNow(knownNames, wanted, current);

            if (fetched.isEmpty()) {
                registerFailure(current);
                return;
            }

            consecutiveFailures = 0;
            backoffUntilMillis = 0L;

            if (!plugin.isEnabled()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> applyFetched(fetched));
        } catch (IllegalPluginAccessException e) {
            // Server shut down between the isEnabled check and the hop back - nothing to salvage.
        } finally {
            inFlight.set(false);
        }
    }

    /** Async thread. Package-private so tests can drive one batch without the scheduler. */
    List<FakeIdentity> runBatchNow(Set<String> knownNames, int wanted, ReplenishSettings current) {
        return fetcher.fetchNewIdentities(knownNames, wanted, current.maxAttemptsPerIdentity(),
                current.requestSpacing(), () -> shuttingDown);
    }

    /** Main thread. */
    private void applyFetched(List<FakeIdentity> fetched) {
        int accepted = 0;
        for (FakeIdentity identity : fetched) {
            if (pool.offer(identity)) {
                accepted++;
            }
        }
        logger.info(messageService.get("events.pool-refilled",
                "count", String.valueOf(accepted), "available", String.valueOf(pool.availableCount())));
    }

    /**
     * Backs off exponentially when Mojang gives us nothing, so an outage or a rate limit does not
     * turn into hours of hammering. Reset by the next successful batch.
     */
    private void registerFailure(ReplenishSettings current) {
        consecutiveFailures++;
        long intervalMillis = current.intervalTicks() * 50L;
        long delay = Math.min(intervalMillis * (1L << Math.min(consecutiveFailures, 16)), MAX_BACKOFF_MILLIS);
        backoffUntilMillis = System.currentTimeMillis() + delay;
        logger.warning(messageService.get("events.pool-refill-failed",
                "minutes", String.valueOf(Math.max(1L, TimeUnit.MILLISECONDS.toMinutes(delay)))));
    }
}
