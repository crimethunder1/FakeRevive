package com.fakerevive.disguise;

import com.fakerevive.FakeRevivePlugin;
import com.fakerevive.support.PluginLoader;
import com.fakerevive.message.MessageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakeNamePoolReplenisherTest {
    private static final String EMPTY_NAMES_JSON = "[]";
    private static final String THREE_NAMES_JSON = "["
            + "{\"name\": \"Alpha_Wolf\", \"skinValue\": \"v1\", \"skinSignature\": \"s1\"}, "
            + "{\"name\": \"Beta_Falcon\", \"skinValue\": \"v2\", \"skinSignature\": \"s2\"}, "
            + "{\"name\": \"Gamma_Tiger\", \"skinValue\": \"v3\", \"skinSignature\": \"s3\"}"
            + "]";

    private ServerMock server;
    private FakeRevivePlugin plugin;
    private MessageService messageService;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = PluginLoader.load();
        messageService = new MessageService(plugin, "en");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void anExhaustedPoolStillAsksForARefill(@TempDir Path tempDir) throws InterruptedException {
        FakeNamePool pool = pool(EMPTY_NAMES_JSON, tempDir);
        CountingFetcher fetcher = new CountingFetcher(2);
        FakeNamePoolReplenisher replenisher = replenisher(pool, fetcher, settings(3, 1, 2));

        replenisher.requestTopUp();

        assertTrue(awaitLatch(fetcher.finished), "No refill was started for an empty pool");
        awaitPoolSize(pool, 2);
        assertEquals(2, pool.availableCount());
    }

    @Test
    void aWellStockedPoolIsLeftAlone(@TempDir Path tempDir) {
        FakeNamePool pool = pool(THREE_NAMES_JSON, tempDir);
        CountingFetcher fetcher = new CountingFetcher(5);
        FakeNamePoolReplenisher replenisher = replenisher(pool, fetcher, settings(3, 1, 2));

        replenisher.requestTopUp();
        server.getScheduler().performTicks(5L);

        assertEquals(0, fetcher.calls.get());
        assertEquals(3, pool.availableCount());
    }

    @Test
    void aSingleBatchNeverFetchesMoreThanTheConfiguredBatchSize(@TempDir Path tempDir) throws InterruptedException {
        FakeNamePool pool = pool(EMPTY_NAMES_JSON, tempDir);
        CountingFetcher fetcher = new CountingFetcher(50);
        FakeNamePoolReplenisher replenisher = replenisher(pool, fetcher, settings(40, 10, 4));

        replenisher.requestTopUp();

        assertTrue(awaitLatch(fetcher.finished));
        awaitPoolSize(pool, 4);
        assertEquals(4, pool.availableCount());
        assertEquals(4, fetcher.calls.get());
    }

    @Test
    void refillIsSkippedEntirelyWhenAutoRefillIsOff(@TempDir Path tempDir) {
        FakeNamePool pool = pool(EMPTY_NAMES_JSON, tempDir);
        CountingFetcher fetcher = new CountingFetcher(5);
        ReplenishSettings disabled = new ReplenishSettings(false, 3, 1, 2, 20L * 60L, Duration.ZERO, 3, false);
        FakeNamePoolReplenisher replenisher = replenisher(pool, fetcher, disabled);

        replenisher.requestTopUp();
        server.getScheduler().performTicks(5L);

        assertEquals(0, fetcher.calls.get());
    }

    @Test
    void manualRefillStartsEvenWhenAutoRefillIsOffAndThePoolIsAboveTheLowWaterMark(@TempDir Path tempDir)
            throws InterruptedException {
        FakeNamePool pool = pool(THREE_NAMES_JSON, tempDir);
        ScriptedFetcher fetcher = new ScriptedFetcher(2);
        ReplenishSettings disabled = new ReplenishSettings(false, 40, 1, 2, 20L * 60L, Duration.ZERO, 3, false);
        FakeNamePoolReplenisher replenisher = replenisher(pool, fetcher, disabled);

        ManualRefillResult result = replenisher.requestManualRefill();

        assertEquals(ManualRefillResult.STARTED, result);
        assertTrue(awaitLatch(fetcher.finished));
        awaitPoolSize(pool, 5);
        assertEquals(5, pool.availableCount());
        assertEquals(2, fetcher.lastRequestedCount.get());
    }

    @Test
    void manualRefillIgnoresARunningBackoff(@TempDir Path tempDir) throws InterruptedException {
        FakeNamePool pool = pool(EMPTY_NAMES_JSON, tempDir);
        ScriptedFetcher fetcher = new ScriptedFetcher(0);
        FakeNamePoolReplenisher replenisher = replenisher(pool, fetcher, settings(3, 1, 2));

        replenisher.requestTopUp();
        awaitIdle(replenisher);
        assertEquals(1, fetcher.batches.get());

        replenisher.requestTopUp();
        server.getScheduler().performTicks(5L);
        assertEquals(1, fetcher.batches.get());

        fetcher.identitiesToReturn = 2;
        ManualRefillResult result = replenisher.requestManualRefill();

        assertEquals(ManualRefillResult.STARTED, result);
        awaitPoolSize(pool, 2);
        assertEquals(2, pool.availableCount());
        assertEquals(2, fetcher.batches.get());
    }

    @Test
    void manualRefillReportsARunningRefill(@TempDir Path tempDir) throws InterruptedException {
        FakeNamePool pool = pool(EMPTY_NAMES_JSON, tempDir);
        ScriptedFetcher fetcher = new ScriptedFetcher(2);
        fetcher.hold = new CountDownLatch(1);
        FakeNamePoolReplenisher replenisher = replenisher(pool, fetcher, settings(3, 1, 2));

        ManualRefillResult first = replenisher.requestManualRefill();
        assertTrue(awaitLatch(fetcher.entered));
        ManualRefillResult second = replenisher.requestManualRefill();
        fetcher.hold.countDown();

        assertEquals(ManualRefillResult.STARTED, first);
        assertEquals(ManualRefillResult.ALREADY_RUNNING, second);
        awaitPoolSize(pool, 2);
        assertEquals(1, fetcher.batches.get());
    }

    @Test
    void manualRefillReportsAFullPool(@TempDir Path tempDir) {
        FakeNamePool pool = pool(THREE_NAMES_JSON, tempDir);
        ScriptedFetcher fetcher = new ScriptedFetcher(2);
        FakeNamePoolReplenisher replenisher = replenisher(pool, fetcher, settings(3, 1, 2));

        ManualRefillResult result = replenisher.requestManualRefill();
        server.getScheduler().performTicks(5L);

        assertEquals(ManualRefillResult.POOL_FULL, result);
        assertEquals(0, fetcher.batches.get());
    }

    @Test
    void requestTopUpStaysInactiveWhenAutoRefillIsOffEvenForAnEmptyPool(@TempDir Path tempDir) {
        FakeNamePool pool = pool(EMPTY_NAMES_JSON, tempDir);
        ScriptedFetcher fetcher = new ScriptedFetcher(2);
        ReplenishSettings disabled = new ReplenishSettings(false, 3, 1, 2, 20L * 60L, Duration.ZERO, 3, false);
        FakeNamePoolReplenisher replenisher = replenisher(pool, fetcher, disabled);

        replenisher.requestTopUp();
        server.getScheduler().performTicks(5L);

        assertEquals(0, fetcher.batches.get());
        assertEquals(0, pool.availableCount());
    }

    private boolean awaitLatch(CountDownLatch latch) throws InterruptedException {
        for (int attempt = 0; attempt < 250 && latch.getCount() > 0; attempt++) {
            server.getScheduler().performTicks(2L);
            latch.await(20L, TimeUnit.MILLISECONDS);
        }
        return latch.getCount() == 0;
    }

    private void awaitIdle(FakeNamePoolReplenisher replenisher) throws InterruptedException {
        for (int attempt = 0; attempt < 250 && (replenisher.isRefilling() || attempt == 0); attempt++) {
            server.getScheduler().performTicks(2L);
            Thread.sleep(20L);
        }
    }

    private void awaitPoolSize(FakeNamePool pool, int expected) throws InterruptedException {
        for (int attempt = 0; attempt < 100 && pool.availableCount() < expected; attempt++) {
            server.getScheduler().performTicks(2L);
            if (pool.availableCount() < expected) {
                Thread.sleep(20L);
            }
        }
    }

    private FakeNamePoolReplenisher replenisher(FakeNamePool pool, MojangIdentityFetcher fetcher,
                                                 ReplenishSettings settings) {
        return new FakeNamePoolReplenisher(plugin, pool, fetcher, plugin.getLogger(), messageService, settings);
    }

    private static ReplenishSettings settings(int targetSize, int lowWaterMark, int batchSize) {
        return new ReplenishSettings(true, targetSize, lowWaterMark, batchSize, 20L * 60L, Duration.ZERO, 3, false);
    }

    private static FakeNamePool pool(String namesJson, Path tempDir) {
        InputStream stream = new ByteArrayInputStream(namesJson.getBytes(StandardCharsets.UTF_8));
        return new FakeNamePool(stream, tempDir.resolve("used.yml"));
    }

    private static final class ScriptedFetcher extends MojangIdentityFetcher {
        private final AtomicInteger batches = new AtomicInteger();
        private final AtomicInteger lastRequestedCount = new AtomicInteger();
        private final CountDownLatch finished = new CountDownLatch(1);
        private final CountDownLatch entered = new CountDownLatch(1);
        private volatile int identitiesToReturn;
        private volatile CountDownLatch hold;

        private ScriptedFetcher(int identitiesToReturn) {
            this.identitiesToReturn = identitiesToReturn;
        }

        @Override
        public List<FakeIdentity> fetchNewIdentities(Set<String> excludedNames, int count, int maxAttemptsEach,
                                                     Duration spacing, BooleanSupplier abort) {
            int batchNumber = batches.incrementAndGet();
            lastRequestedCount.set(count);
            entered.countDown();
            CountDownLatch holdLatch = hold;
            if (holdLatch != null) {
                try {
                    holdLatch.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
            List<FakeIdentity> identities = new ArrayList<>();
            for (int index = 0; index < Math.min(count, identitiesToReturn); index++) {
                String suffix = batchNumber + "_" + index;
                identities.add(new FakeIdentity("Scripted_" + suffix, "v" + suffix, "s" + suffix));
            }
            finished.countDown();
            return identities;
        }
    }

    private static final class CountingFetcher extends MojangIdentityFetcher {
        private final int available;
        private final AtomicInteger calls = new AtomicInteger();
        private final CountDownLatch finished = new CountDownLatch(1);

        private CountingFetcher(int available) {
            this.available = available;
        }

        @Override
        public List<FakeIdentity> fetchNewIdentities(Set<String> excludedNames, int count, int maxAttemptsEach,
                                                     Duration spacing, BooleanSupplier abort) {
            try {
                return super.fetchNewIdentities(excludedNames, count, maxAttemptsEach, spacing, abort);
            } finally {
                finished.countDown();
            }
        }

        @Override
        public Optional<FakeIdentity> fetchNewIdentity(Set<String> excludedNames, int maxAttempts) {
            int index = calls.getAndIncrement();
            if (index >= available) {
                return Optional.empty();
            }
            return Optional.of(new FakeIdentity("Fetched_" + index, "v" + index, "s" + index));
        }
    }
}
