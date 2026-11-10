package com.fakerevive.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakedOutPlayerStoreTest {
    @TempDir
    Path temporaryDirectory;

    private FakedOutPlayerStore createStore() {
        FakedOutPlayerStore store = new FakedOutPlayerStore(
                temporaryDirectory.resolve("faked-out.yml"), Logger.getAnonymousLogger());
        store.load();
        return store;
    }

    @Test
    void stateSurvivesLoadingIntoANewInstance() {
        UUID playerId = UUID.randomUUID();
        FakedOutPlayerStore store = createStore();

        assertTrue(store.add(playerId));
        assertFalse(store.add(playerId));

        FakedOutPlayerStore reloadedStore = createStore();
        assertTrue(reloadedStore.contains(playerId));
        assertEquals(Set.of(playerId), reloadedStore.getFakedOutPlayers());
    }

    @Test
    void removalIsPersistedPermanently() {
        UUID removedPlayerId = UUID.randomUUID();
        UUID keptPlayerId = UUID.randomUUID();
        FakedOutPlayerStore store = createStore();
        store.add(removedPlayerId);
        store.add(keptPlayerId);

        assertTrue(store.remove(removedPlayerId));
        assertFalse(store.remove(removedPlayerId));

        FakedOutPlayerStore reloadedStore = createStore();
        assertFalse(reloadedStore.contains(removedPlayerId));
        assertTrue(reloadedStore.contains(keptPlayerId));
    }

    @Test
    void loadDiscardsInMemoryStateAndReadsTheFileAgain() {
        UUID playerId = UUID.randomUUID();
        FakedOutPlayerStore writer = createStore();
        FakedOutPlayerStore reader = createStore();

        writer.add(playerId);
        assertFalse(reader.contains(playerId));

        reader.load();
        assertTrue(reader.contains(playerId));
    }

    @Test
    void invalidUuidEntriesAreSkipped() throws IOException {
        UUID validPlayerId = UUID.randomUUID();
        Files.writeString(temporaryDirectory.resolve("faked-out.yml"),
                "players:\n  - not-a-uuid\n  - " + validPlayerId + "\n");

        FakedOutPlayerStore store = createStore();

        assertEquals(Set.of(validPlayerId), store.getFakedOutPlayers());
    }

    @Test
    void brokenFileIsBackedUpAndStoreStartsEmpty() throws IOException {
        Path file = temporaryDirectory.resolve("faked-out.yml");
        Files.writeString(file, "players: [unclosed\n  - : :\n");

        FakedOutPlayerStore store = createStore();

        assertTrue(store.getFakedOutPlayers().isEmpty());
        assertTrue(Files.exists(temporaryDirectory.resolve("faked-out.yml.broken")));
        assertEquals("players: [unclosed\n  - : :\n",
                Files.readString(temporaryDirectory.resolve("faked-out.yml.broken")));
    }
}
