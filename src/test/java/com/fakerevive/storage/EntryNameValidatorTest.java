package com.fakerevive.storage;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntryNameValidatorTest {
    @Test
    void acceptsLettersDigitsUnderscoreAndHyphen() {
        for (String name : List.of("a", "Kit1", "red_team", "red-team", "Rot_Team-2", "Ärzte")) {
            assertTrue(EntryNameValidator.isValid(name), name);
        }
    }

    @Test
    void rejectsEmptyAndSpecialCharacters() {
        for (String name : List.of("", "a.b", "kits.name", "has space", "slash/name", "colon:name", "quote\"name", "tab\tname")) {
            assertFalse(EntryNameValidator.isValid(name), name);
        }
    }

    @Test
    void enforcesMaximumLengthOfThirtyTwoCharacters() {
        assertTrue(EntryNameValidator.isValid("a".repeat(32)));
        assertFalse(EntryNameValidator.isValid("a".repeat(33)));
    }

    @Test
    void rejectsNull() {
        assertFalse(EntryNameValidator.isValid(null));
    }
}
