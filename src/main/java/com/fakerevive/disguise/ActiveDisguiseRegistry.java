package com.fakerevive.disguise;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which players are currently disguised and the {@link FakeIdentity} each one holds.
 * Thread-safe — the packet interceptor in {@link PlayerDisguiseService} reads from this
 * registry on the Netty IO thread while the main thread writes to it.
 */
public class ActiveDisguiseRegistry {

    private final Map<UUID, FakeIdentity> activeIdentities = new ConcurrentHashMap<>();

    /** Registers a fake identity for the given player, replacing any previously held one. */
    public void assign(UUID playerId, FakeIdentity identity) {
        activeIdentities.put(playerId, identity);
    }

    /** @return the full fake identity for the given player, or empty if they are not disguised. */
    public Optional<FakeIdentity> getIdentity(UUID playerId) {
        return Optional.ofNullable(activeIdentities.get(playerId));
    }

    /** @return only the fake name for the given player, or empty if they are not disguised. */
    public Optional<String> getFakeName(UUID playerId) {
        return getIdentity(playerId).map(FakeIdentity::name);
    }

    /**
     * Finds the UUID of the player whose current fake name matches the given string.
     * Comparison is case-insensitive.
     *
     * @return the UUID of the disguised player, or empty if no player has this fake name.
     */
    public Optional<UUID> findByFakeName(String fakeName) {
        return activeIdentities.entrySet().stream()
                .filter(e -> e.getValue().name().equalsIgnoreCase(fakeName))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    /**
     * Removes the disguise registration for the given player. Must be called before
     * {@link PlayerDisguiseService#remove(Player)} so the packet interceptor no longer
     * rewrites outgoing PlayerInfo packets for this player.
     */
    public void clear(UUID playerId) {
        activeIdentities.remove(playerId);
    }
}
