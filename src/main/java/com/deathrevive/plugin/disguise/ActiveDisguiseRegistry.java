package com.deathrevive.plugin.disguise;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class ActiveDisguiseRegistry {

    private final Map<UUID, FakeIdentity> activeIdentities = new HashMap<>();

    public void assign(UUID playerId, FakeIdentity identity) {
        activeIdentities.put(playerId, identity);
    }

    public Optional<FakeIdentity> getIdentity(UUID playerId) {
        return Optional.ofNullable(activeIdentities.get(playerId));
    }

    public Optional<String> getFakeName(UUID playerId) {
        return getIdentity(playerId).map(FakeIdentity::name);
    }

    public void clear(UUID playerId) {
        activeIdentities.remove(playerId);
    }
}
