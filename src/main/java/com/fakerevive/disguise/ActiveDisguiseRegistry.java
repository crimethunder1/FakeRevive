package com.fakerevive.disguise;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ActiveDisguiseRegistry {
    private final Map<UUID, FakeIdentity> activeIdentities = new ConcurrentHashMap<>();

    public void assign(UUID playerId, FakeIdentity identity) {
        activeIdentities.put(playerId, identity);
    }

    public Optional<FakeIdentity> getIdentity(UUID playerId) {
        return Optional.ofNullable(activeIdentities.get(playerId));
    }

    public Optional<String> getFakeName(UUID playerId) {
        return getIdentity(playerId).map(FakeIdentity::name);
    }

    public Optional<UUID> findByFakeName(String fakeName) {
        return activeIdentities.entrySet().stream()
                .filter(entry -> entry.getValue().name().equalsIgnoreCase(fakeName))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    public void clear(UUID playerId) {
        activeIdentities.remove(playerId);
    }
}
