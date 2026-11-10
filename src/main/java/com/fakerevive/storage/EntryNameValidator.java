package com.fakerevive.storage;

import java.util.regex.Pattern;

public final class EntryNameValidator {
    public static final int MAXIMUM_LENGTH = 32;

    private static final Pattern VALID_NAME = Pattern.compile("[\\p{L}\\p{Nd}_-]{1," + MAXIMUM_LENGTH + "}");

    private EntryNameValidator() {
    }

    public static boolean isValid(String name) {
        return name != null && VALID_NAME.matcher(name).matches();
    }
}
