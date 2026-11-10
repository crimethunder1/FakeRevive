package com.fakerevive.command;

import java.util.List;
import java.util.stream.Collectors;

public final class TabCompletions {
    private TabCompletions() {
    }

    public static List<String> filterByPrefix(List<String> options, String prefix) {
        String lowerPrefix = prefix.toLowerCase();
        return options.stream()
                .filter(option -> option.toLowerCase().startsWith(lowerPrefix))
                .collect(Collectors.toList());
    }
}
