package com.fakerevive.command;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public final class NearbyPlayerResolver {
    public static final String NEAREST_SELECTOR = "@p";
    public static final String NEAR_SELECTOR = "@near";

    private static final double NEARBY_RADIUS_SQUARED = 16 * 16;

    private NearbyPlayerResolver() {
    }

    public static boolean isNearestSelector(String argument) {
        return NEAREST_SELECTOR.equalsIgnoreCase(argument);
    }

    public static boolean isNearSelector(String argument) {
        return NEAR_SELECTOR.equalsIgnoreCase(argument);
    }

    public static Optional<Player> resolveNearest(Player sender, Predicate<Player> filter) {
        Location origin = sender.getLocation();
        return sender.getWorld().getPlayers().stream()
                .filter(candidate -> !candidate.getUniqueId().equals(sender.getUniqueId()))
                .filter(filter)
                .min(Comparator.comparingDouble(candidate -> candidate.getLocation().distanceSquared(origin)));
    }

    public static List<Player> resolveNear(Player sender, Predicate<Player> filter) {
        Location origin = sender.getLocation();
        return sender.getWorld().getPlayers().stream()
                .filter(candidate -> candidate.getLocation().distanceSquared(origin) <= NEARBY_RADIUS_SQUARED)
                .filter(filter)
                .toList();
    }
}
