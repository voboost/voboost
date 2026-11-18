package ru.voboost.tests;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Bounded observations only. Absence is not an acceptance exclusion. */
public final class TargetInventory {
    private static final int MAX_PACKAGES = 8192, MAX_PROCESSES = 65536;
    private TargetInventory() {}

    public record Package(String name, List<String> processes) {
        public Package { processes = List.copyOf(processes); }
    }
    public record Snapshot(String bootId, List<Package> packages) {
        public Snapshot { packages = List.copyOf(packages); }
    }
    /** Caller-bound retained observation. It cannot authorize an exclusion. */
    public record Capture(String runId, String sessionId, String serial, java.nio.file.Path source, Snapshot snapshot) {}

    public static Capture capture(String runId, String sessionId, String serial, String expectedBoot,
            java.nio.file.Path source) throws IOException {
        if (runId == null || !runId.matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}")
                || sessionId == null || !sessionId.matches("[A-Za-z0-9_-]{1,128}")
                || serial == null || !serial.matches("emulator-[0-9]{4,5}") || source == null
                || !source.isAbsolute() || !java.nio.file.Files.isRegularFile(source, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                || java.nio.file.Files.size(source) > 4 * 1024 * 1024 + 128)
            throw new IOException("invalid inventory capture identity or source");
        String body = java.nio.file.Files.readString(source, java.nio.charset.StandardCharsets.UTF_8);
        return new Capture(runId, sessionId, serial, source, parse(body, expectedBoot));
    }

    /** Implementations must use a fixed, read-only package-manager action. */
    public interface Port {
        String bootId() throws IOException;
        List<Package> installedPackages() throws IOException;
    }

    public static Snapshot collect(Port port) throws IOException {
        String boot = port.bootId();
        if (boot == null || !boot.matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}"))
            throw new IOException("invalid boot identity");
        List<Package> first = validate(port.installedPackages());
        List<Package> second = validate(port.installedPackages());
        if (!boot.equals(port.bootId()) || !first.equals(second))
            throw new IOException("package inventory or boot changed during collection");
        return new Snapshot(boot, first);
    }

    public enum Applicability { REQUIRED, UNKNOWN }

    /** A declared process remains required; a missing name alone never proves absence. */
    public static Applicability applicability(CrossCheck checked, AcceptancePlan.Key key) {
        if (checked == null || key == null || !key.id().startsWith("agent/")
                || !key.target().startsWith("android:") || key.target().equals("android:system_server"))
            return Applicability.UNKNOWN;
        String process = key.target().substring("android:".length());
        for (Package item : checked.inventory().snapshot().packages())
            if (item.processes().contains(process)) return Applicability.REQUIRED;
        return Applicability.UNKNOWN;
    }

    /** Matches independently captured system-server names. Not an exclusion receipt. */
    public record CrossCheck(Capture inventory, java.nio.file.Path source) {}

    public static CrossCheck crossCheck(Capture capture, java.nio.file.Path source) throws IOException {
        if (capture == null || source == null || !source.isAbsolute()
                || !java.nio.file.Files.isRegularFile(source, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                || java.nio.file.Files.size(source) > 524416) throw new IOException("invalid package list source");
        String body = java.nio.file.Files.readString(source, java.nio.charset.StandardCharsets.UTF_8);
        if (body.indexOf('\r') >= 0 || !body.endsWith("\n")) throw new IOException("invalid package list frame");
        String[] lines = body.split("\n", -1);
        if (lines.length < 5 || lines.length > MAX_PACKAGES + 4
                || !lines[0].equals("bootId=" + capture.snapshot().bootId())
                || !lines[1].equals("package-list-v1") || !lines[lines.length - 2].equals("end"))
            throw new IOException("package list identity differs");
        Set<String> names = new HashSet<>();
        for (int i = 2; i < lines.length - 2; i++) {
            if (!lines[i].startsWith("package:")) throw new IOException("invalid package list row");
            String name = lines[i].substring(8);
            if (!(name.equals("android") || name.matches("[A-Za-z0-9_]+(?:[.][A-Za-z0-9_]+)+"))
                    || !names.add(name)) throw new IOException("invalid or duplicate package name");
        }
        Set<String> actual = new HashSet<>();
        for (Package item : capture.snapshot().packages()) actual.add(item.name());
        if (!names.equals(actual)) throw new IOException("package manager inventory differs from system list");
        return new CrossCheck(capture, source);
    }

    /** Parses only the closed producer frame; this is not an absence receipt. */
    public static Snapshot parse(String body, String expectedBoot) throws IOException {
        if (body == null || body.length() > 4 * 1024 * 1024 + 128 || body.indexOf('\r') >= 0
                || !body.endsWith("\n")) throw new IOException("invalid inventory frame");
        String[] lines = body.split("\n", -1);
        if (lines.length < 5 || !lines[0].equals("bootId=" + expectedBoot)
                || !lines[1].equals("package-inventory-v1") || !lines[lines.length - 2].equals("end"))
            throw new IOException("inventory identity or framing differs");
        java.util.LinkedHashMap<String, List<String>> records = new java.util.LinkedHashMap<>();
        String previous = "";
        for (int i = 2; i < lines.length - 2; i++) {
            String line = lines[i]; int separator = line.indexOf('\t');
            if (separator < 1 || separator != line.lastIndexOf('\t') || line.compareTo(previous) <= 0)
                throw new IOException("inventory rows are malformed or duplicated");
            records.computeIfAbsent(line.substring(0, separator), ignored -> new java.util.ArrayList<>())
                .add(line.substring(separator + 1));
            previous = line;
        }
        List<Package> packages = new java.util.ArrayList<>();
        records.forEach((name, processes) -> packages.add(new Package(name, processes)));
        if (expectedBoot == null || !expectedBoot.matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}"))
            throw new IOException("invalid boot identity");
        return new Snapshot(expectedBoot, validate(packages));
    }

    private static List<Package> validate(List<Package> packages) throws IOException {
        if (packages == null || packages.isEmpty() || packages.size() > MAX_PACKAGES)
            throw new IOException("invalid package inventory size");
        Set<String> names = new HashSet<>(); int count = 0;
        for (Package item : packages) {
            if (item == null || item.name() == null || !(item.name().equals("android") || item.name().matches("[A-Za-z0-9_]+(?:[.][A-Za-z0-9_]+)+"))
                    || !names.add(item.name()) || item.processes().isEmpty())
                throw new IOException("invalid or duplicate package");
            Set<String> processes = new HashSet<>();
            for (String process : item.processes()) {
                if (process == null || !process.matches("[A-Za-z0-9_.:-]{1,200}") || !processes.add(process)
                        || ++count > MAX_PROCESSES) throw new IOException("invalid process inventory");
            }
        }
        return List.copyOf(packages);
    }
}
