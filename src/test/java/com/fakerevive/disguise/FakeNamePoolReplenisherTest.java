package com.fakerevive.disguise;

import com.fakerevive.FakeRevivePlugin;
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
        FakeRevivePlugin.setIdentityFetcherFactoryForTesting(() -> new MojangIdentityFetcher() {
            @Override
            public Optional<FakeIdentity> fetchNewIdentity(Set<String> excludedNames, int maxAttempts) {
                return Optional.empty();
            }
        });
        server = MockBukkit.mock();
        plugin = MockBukkit.load(FakeRevivePlugin.class);
        messageService = new MessageService(plugin, "en");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
        FakeRevivePlugin.setIdentityFetcherFactoryForTesting(MojangIdentityFetcher::new);
    }

    /**
     * The bug this whole feature exists for: an exhausted pool used to be unable to ask for a
     * refill, so it stayed empty forever and revives silently stopped disguising anyone.
     */
    @Test
    void anExhaustedPoolStillAsksForARefill(@TempDir Path tempDir) throws InterruptedException {
        FakeNamePool pool = pool(EMPTY_NAMES_JSON, tempDir);
        CountingFetcher fetcher = new CountingFetcher(2);
        FakeNamePoolReplenisher replenisher = replenisher(pool, fetcher, settings(3, 1, 2));

        replenisher.requestTopUp();

        assertTrue(fetcher.finished.await(5, TimeUnit.SECONDS), "No refill was started for an empty pool");
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

        assertTrue(fetcher.finished.await(5, TimeUnit.SECONDS));
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

    /**
     * MockBukkit runs async tasks on a real thread but only runs scheduled sync tasks on a tick,
     * so the hop that hands a finished batch back to the pool needs ticking until it lands.
     */
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

    /** Hands back as many synthetic identities as it was told to, counting the calls. */
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
