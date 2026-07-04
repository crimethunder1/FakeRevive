package com.deathrevive.plugin.disguise;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class ActiveDisguiseRegistry {

    private final Map<UUID, String> activeFakeNames = new HashMap<>();

    public void assign(UUID playerId, String fakeName) {
        activeFakeNames.put(playerId, fakeName);
    }

    public Optional<String> getFakeName(UUID playerId) {
        return Optional.ofNullable(activeFakeNames.get(playerId));
    }

    public void clear(UUID playerId) {
        activeFakeNames.remove(playerId);
    }
}
