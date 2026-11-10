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

public class FakeNamePoolReplenisher {
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

    public void start() {
        shuttingDown = false;
        scheduleTimer();
    }

    public void stop() {
        shuttingDown = true;
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
    }

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

    public void requestTopUp() {
        cycle();
    }

    public ManualRefillResult requestManualRefill() {
        ReplenishSettings current = settings;
        int available = pool.availableCount();
        int wanted = Math.min(current.targetSize() - available, current.batchSize());
        if (wanted <= 0) {
            return ManualRefillResult.POOL_FULL;
        }
        return launchBatch(current, available, wanted)
                ? ManualRefillResult.STARTED
                : ManualRefillResult.ALREADY_RUNNING;
    }

    public boolean isRefilling() {
        return inFlight.get();
    }

    public int getTargetSize() {
        return settings.targetSize();
    }

    private void scheduleTimer() {
        timerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::cycle, INITIAL_DELAY_TICKS, settings.intervalTicks());
    }

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

        launchBatch(current, available, wanted);
    }

    private boolean launchBatch(ReplenishSettings current, int available, int wanted) {
        if (!inFlight.compareAndSet(false, true)) {
            return false;
        }

        if (available == 0) {
            logger.warning(messageService.get("events.pool-low",
                    "available", String.valueOf(available), "target", String.valueOf(current.targetSize())));
        }

        Set<String> knownNames = pool.getKnownNames();
        try {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> runBatch(knownNames, wanted, current));
        } catch (IllegalPluginAccessException exception) {
            inFlight.set(false);
            return false;
        }
        return true;
    }

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
        } catch (IllegalPluginAccessException exception) {
        } finally {
            inFlight.set(false);
        }
    }

    List<FakeIdentity> runBatchNow(Set<String> knownNames, int wanted, ReplenishSettings current) {
        return fetcher.fetchNewIdentities(knownNames, wanted, current.maxAttemptsPerIdentity(),
                current.requestSpacing(), () -> shuttingDown);
    }

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

    private void registerFailure(ReplenishSettings current) {
        consecutiveFailures++;
        long intervalMillis = current.intervalTicks() * 50L;
        long delay = Math.min(intervalMillis * (1L << Math.min(consecutiveFailures, 16)), MAX_BACKOFF_MILLIS);
        backoffUntilMillis = System.currentTimeMillis() + delay;
        logger.warning(messageService.get("events.pool-refill-failed",
                "minutes", String.valueOf(Math.max(1L, TimeUnit.MILLISECONDS.toMinutes(delay)))));
    }
}
