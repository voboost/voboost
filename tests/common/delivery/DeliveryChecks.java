package ru.voboost.tests;

import java.util.Set;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** Narrow checks shared by the focused delivery case and its unit tests. */
public final class DeliveryChecks {
    private DeliveryChecks() {}

    public static void requireInventoryRejection(int exit, String output) {
        if (exit != 1 || !output.startsWith("error: ") || !output.contains("closure inventory differs from voboost.json")) {
            throw new IllegalStateException("Expected verifier inventory rejection, got exit " + exit + ": " + output);
        }
    }

    public static void requireExactNames(Set<String> actual, Set<String> expected) {
        if (!actual.equals(expected)) throw new IllegalStateException("Package inventory differs: " + actual + " expected " + expected);
    }

    public static void requireDirectory(Path path) {
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) throw new IllegalStateException("Expected directory without symlink: " + path);
    }
    
    public static void requireRegular(Path path) {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IllegalStateException("Expected regular file without symlink: " + path);
    }
    
    public static boolean mayRemoveWork(boolean attached, boolean attachOutcomeUnknown) {
        return !attached && !attachOutcomeUnknown;
    }
}
