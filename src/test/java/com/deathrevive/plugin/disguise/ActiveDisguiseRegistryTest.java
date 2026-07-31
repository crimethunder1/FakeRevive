package com.deathrevive.plugin.disguise;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActiveDisguiseRegistryTest {

    @Test
    void returnsTheAssignedFakeNameForAPlayer() {
        ActiveDisguiseRegistry registry = new ActiveDisguiseRegistry();
        UUID playerId = UUID.randomUUID();

        registry.assign(playerId, new FakeIdentity("Crimson_Wolf", "skin-value", "skin-signature"));

        assertEquals("Crimson_Wolf", registry.getFakeName(playerId).orElseThrow());
    }

    @Test
    void clearRemovesTheActiveAssignment() {
        ActiveDisguiseRegistry registry = new ActiveDisguiseRegistry();
        UUID playerId = UUID.randomUUID();
        registry.assign(playerId, new FakeIdentity("Crimson_Wolf", "skin-value", "skin-signature"));

        registry.clear(playerId);

        assertTrue(registry.getFakeName(playerId).isEmpty());
    }

    @Test
    void unknownPlayerHasNoFakeName() {
        ActiveDisguiseRegistry registry = new ActiveDisguiseRegistry();

        assertTrue(registry.getFakeName(UUID.randomUUID()).isEmpty());
    }
}
