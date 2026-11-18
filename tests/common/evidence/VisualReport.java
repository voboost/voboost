package ru.voboost.tests.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import ru.voboost.tests.RunFiles;

/** Retains the complete current visual run before evaluating its verdict. Never records baselines. */
public final class VisualReport {
    private static final Pattern PACKAGE = Pattern.compile("(?m)^package\\s+([a-zA-Z_][a-zA-Z0-9_.]*)\\s*;");
    private static final long MAX_FILE = 16 * 1024 * 1024, MAX_TOTAL = 512 * 1024 * 1024;
    public record Result(Path report, List<Path> evidence, List<String> problems, int tests) {
        public Result { evidence = List.copyOf(evidence); problems = List.copyOf(problems); }
        public boolean passed() { return tests > 0 && problems.isEmpty(); }
    }
    private VisualReport() {}

    public static Set<String> sourceSuites(Path source) throws IOException {
        Set<String> result = new HashSet<>();
        try (var files = Files.walk(source)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith("TestVisual.java")).toList()) {
                regular(file);
                var matcher = PACKAGE.matcher(Files.readString(file));
                if (!matcher.find()) throw new IOException("visual source package unavailable: " + file);
                String name = file.getFileName().toString().replaceFirst("\\.java$", "");
                if (!result.add(matcher.group(1) + "." + name)) throw new IOException("duplicate visual source suite");
            }
        }
        if (result.isEmpty()) throw new IOException("visual source suite inventory is empty");
        return Set.copyOf(result);
    }

    public static Result retain(Path xml, Path images, Path work, Set<String> expected, long started, int processExit) throws IOException {
        if (expected.isEmpty()) throw new IOException("required visual suites are empty");
        Path results = RunFiles.directory(work.resolve("results")), retainedImages = RunFiles.directory(work.resolve("images"));
        List<Path> evidence = new ArrayList<>(); List<String> problems = new ArrayList<>();
        if (processExit != 0) problems.add("visual process failed: exit=" + processExit);
        Set<String> seen = new HashSet<>(); JsonArray suites = new JsonArray(), screenshots = new JsonArray();
        long total = 0; int tests = 0;
        if (Files.isDirectory(xml, LinkOption.NOFOLLOW_LINKS)) {
            try (var files = Files.list(xml)) {
                for (Path file : files.filter(path -> path.getFileName().toString().matches("TEST-.*\\.xml")).sorted().toList()) {
                    total += regular(file); bound(total);
                    if (Files.getLastModifiedTime(file).toMillis() < started) { problems.add("stale result: " + file.getFileName()); continue; }
                    Path saved = results.resolve(file.getFileName()); Files.copy(file, saved); evidence.add(saved);
                    JsonObject row = new JsonObject(); row.addProperty("file", work.relativize(saved).toString()); suites.add(row);
                    try {
                        var factory = DocumentBuilderFactory.newInstance();
                        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
                        var suite = factory.newDocumentBuilder().parse(saved.toFile()).getDocumentElement();
                        String name = suite.getAttribute("name"); row.addProperty("name", name);
                        if (!"testsuite".equals(suite.getTagName()) || !expected.contains(name) || !seen.add(name))
                            throw new IOException("unexpected or duplicate visual suite: " + name);
                        int count = number(suite.getAttribute("tests")), failures = number(suite.getAttribute("failures"));
                        int errors = number(suite.getAttribute("errors")), skipped = number(suite.getAttribute("skipped"));
                        int actualCount = 0, actualFailures = 0, actualErrors = 0, actualSkipped = 0;
                        Set<String> identities = new HashSet<>(); JsonArray cases = new JsonArray(); row.add("cases", cases);
                        for (var child = suite.getFirstChild(); child != null; child = child.getNextSibling()) {
                            if (!(child instanceof org.w3c.dom.Element item) || !"testcase".equals(item.getTagName())) continue;
                            ++actualCount;
                            String caseName = item.getAttribute("name");
                            if (caseName.isBlank() || !identities.add(item.getAttribute("classname") + "\u0000" + caseName))
                                throw new IOException("missing or duplicate visual testcase identity: " + name);
                            JsonObject recordedCase = new JsonObject(); recordedCase.addProperty("name", caseName);
                            recordedCase.addProperty("classname", item.getAttribute("classname")); recordedCase.addProperty("status", "PASS"); cases.add(recordedCase);
                            int outcomes = 0;
                            for (var outcome = item.getFirstChild(); outcome != null; outcome = outcome.getNextSibling()) {
                                if (!(outcome instanceof org.w3c.dom.Element state)) continue;
                                switch (state.getTagName()) {
                                    case "failure" -> { ++actualFailures; ++outcomes; }
                                    case "error" -> { ++actualErrors; ++outcomes; }
                                    case "skipped" -> { ++actualSkipped; ++outcomes; }
                                    default -> { }
                                }
                                if (Set.of("failure", "error", "skipped").contains(state.getTagName())) {
                                    recordedCase.addProperty("status", state.getTagName().toUpperCase(java.util.Locale.ROOT));
                                    String message = state.getAttribute("message");
                                    recordedCase.addProperty("message", message.substring(0, Math.min(8192, message.length())));
                                }
                            }
                            if (outcomes > 1) throw new IOException("ambiguous visual testcase outcome: " + name);
                        }
                        if (count != actualCount || failures != actualFailures || errors != actualErrors || skipped != actualSkipped)
                            throw new IOException("visual testcase outcomes differ from suite counters: " + name);
                        tests = Math.addExact(tests, count);
                        row.addProperty("tests", count); row.addProperty("failures", failures); row.addProperty("errors", errors); row.addProperty("skipped", skipped);
                        if (count == 0 || failures != 0 || errors != 0 || skipped != 0) problems.add("incomplete visual suite: " + name);
                    } catch (Exception invalid) {
                        row.addProperty("diagnostic", invalid.toString()); problems.add("invalid visual result: " + file.getFileName());
                    }
                }
            }
        }
        for (String missing : expected.stream().sorted().toList()) if (!seen.contains(missing)) problems.add("missing visual suite: " + missing);
        if (Files.isDirectory(images, LinkOption.NOFOLLOW_LINKS)) {
            try (var files = Files.list(images)) {
                for (Path file : files.filter(path -> path.getFileName().toString().matches("[A-Za-z0-9_.-]+_(actual|compare)\\.png")).sorted().toList()) {
                    total += regular(file); bound(total);
                    if (Files.getLastModifiedTime(file).toMillis() < started) continue;
                    Path saved = retainedImages.resolve(file.getFileName()); Files.copy(file, saved); evidence.add(saved);
                    screenshots.add(work.relativize(saved).toString());
                }
            }
        }
        JsonObject report = new JsonObject(); report.addProperty("schemaVersion", 1); report.addProperty("startedMillis", started); report.addProperty("processExit", processExit);
        report.addProperty("tests", tests); report.addProperty("passed", tests > 0 && problems.isEmpty());
        JsonArray required = new JsonArray(); expected.stream().sorted().forEach(required::add); report.add("requiredSuites", required);
        JsonArray issues = new JsonArray(); problems.forEach(issues::add); report.add("problems", issues);
        report.add("suites", suites); report.add("images", screenshots);
        Path file = work.resolve("report.json"); RunFiles.write(file, report); evidence.add(file);
        StringBuilder html = new StringBuilder("<!doctype html><meta charset=\"utf-8\"><title>Visual verification evidence</title><style>body{font:16px system-ui;margin:24px;background:#eee}img{max-width:100%;background:#aaa}figure{margin:24px 0}figcaption{overflow-wrap:anywhere}</style><h1>Visual verification evidence</h1><p>Current run only. References are unchanged. Compare images show reference, difference and actual.</p><p><a href=\"report.json\">Complete suite report</a></p>");
        for (var screenshot : screenshots) {
            String path = screenshot.getAsString();
            if (path.endsWith("_compare.png")) html.append("<figure><figcaption>").append(path).append("</figcaption><a href=\"").append(path).append("\"><img loading=\"lazy\" src=\"").append(path).append("\"></a></figure>");
        }
        Path gallery = work.resolve("index.html"); Files.writeString(gallery, html.toString() + "\n"); evidence.add(gallery);
        return new Result(file, evidence, problems, tests);
    }
    private static int number(String value) throws IOException {
        if (!value.matches("[0-9]{1,7}")) throw new IOException("bounded visual count required");
        return Integer.parseInt(value);
    }
    private static long regular(Path file) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > MAX_FILE) throw new IOException("bounded regular visual evidence required: " + file);
        return Files.size(file);
    }
    private static void bound(long total) throws IOException {
        if (total > MAX_TOTAL) throw new IOException("visual evidence exceeds retained-run bound");
    }
}
