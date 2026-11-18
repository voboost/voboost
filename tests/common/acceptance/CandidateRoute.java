package ru.voboost.tests;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

/** Sequential product verification; a failed prerequisite never starts the guest. */
public final class CandidateRoute {
    private static final List<String> COMPONENTS = List.of("unitComponents", "unitAgents", "unitApp", "unitInject", "unitInjectNative",
        "unitInstaller", "unitInstallerNode", "unitInstallerRust", "unitRelease", "unitProductTools");
    private static final List<String> PROPERTIES = List.of("candidateDmg", "productPublicRoot", "trustScope", "cargo", "rustc", "voyemu", "machine",
        "machineName", "scenario", "withCluster", "prepareExisting", "resumeRun", "reviewedKeyboardMaps");
    private CandidateRoute() {}
    private record Stage(GateStatus status, JsonObject json) {}

    public static void main(String[] args) {
        try { System.exit(run(args)); }
        catch (Exception failure) {
            JsonObject error = new JsonObject(); error.addProperty("verdict", "BLOCKED"); error.addProperty("fullAcceptance", false);
            error.addProperty("reason", failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage());
            System.out.println(error); System.exit(2);
        }
    }
    static int run(String[] args) throws Exception {
        Map<String, String> options = new LinkedHashMap<>(); boolean offline = false;
        for (String arg : args) {
            if (arg.equals("--offline")) { offline = true; continue; }
            if (!arg.startsWith("-P") || !arg.contains("=")) throw new IOException("Only -Pkey=value and --offline are accepted");
            int split = arg.indexOf('='); String key = arg.substring(2, split), value = arg.substring(split + 1);
            if (!PROPERTIES.contains(key) || value.isBlank() || options.putIfAbsent(key, value) != null) throw new IOException("Unknown, empty or repeated input: " + key);
        }
        for (String key : List.of("candidateDmg", "productPublicRoot", "cargo", "rustc")) {
            String value = options.get(key);
            if (value == null || !Path.of(value).isAbsolute() || !Files.isRegularFile(Path.of(value), LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Absolute existing " + key + " required");
        }
        if (!"local-input".equals(options.get("trustScope"))) throw new IOException("Explicit trustScope=local-input required; source provenance is a separate gate");
        Path candidate = Path.of(options.get("candidateDmg")).toRealPath();
        BasicFileAttributes before = Files.readAttributes(candidate, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        Path root = Path.of("").toRealPath();
        String runId = UUID.randomUUID().toString();
        Path reports = root.resolve("build/candidate-check"); Files.createDirectories(reports);
        Path work = RunFiles.directory(reports.resolve(runId));
        JsonObject report = new JsonObject(); report.addProperty("schemaVersion", 1); report.addProperty("runId", runId);
        report.addProperty("startedAt", Instant.now().toString()); report.addProperty("candidate", candidate.toString());
        report.addProperty("candidateSize", before.size()); report.addProperty("fullAcceptance", false);
        report.addProperty("verdict", "BLOCKED");
        JsonObject stages = new JsonObject(); report.add("layers", stages);
        Path result = work.resolve("report.json"); RunFiles.write(result, report);
        try {
            Stage unit = stage(root, work, runId, "unitTest", options, offline); stages.add("unit", unit.json()); RunFiles.write(result, report);
            Stage integration = unit.status() == GateStatus.PASS ? stage(root, work, runId, "integrationTest", options, offline)
                : skipped("unit prerequisite did not pass");
            stages.add("integration", integration.json()); RunFiles.write(result, report);
            Stage emulator = integration.status() == GateStatus.PASS ? stage(root, work, runId, "emulatorAcceptance", options, offline)
                : skipped("integration prerequisite did not pass");
            stages.add("emulator", emulator.json());
            Path catalog = work.resolve("integrationTest/candidate-agents.json");
            if (Files.isRegularFile(catalog, LinkOption.NOFOLLOW_LINKS)) {
                AcceptancePlan plan = AcceptancePlan.read(root.resolve("release/beta1-functional-plan.json"), catalog);
                AcceptanceReport acceptance = new AcceptanceReport(plan);
                acceptance.identify(runId, candidate, candidate.toString() + "@" + before.size() + ":" + before.lastModifiedTime(),
                    options.getOrDefault("machineName", options.getOrDefault("machine", "configured Voyemu machine")));
                acceptance.prerequisite(AcceptanceReport.Prerequisite.UNIT, outcome(unit, work.resolve("unitTest/result.json")));
                acceptance.prerequisite(AcceptanceReport.Prerequisite.CANDIDATE, outcome(integration, work.resolve("integrationTest/result.json")));
                acceptance.prerequisite(AcceptanceReport.Prerequisite.ENVIRONMENT, outcome(emulator, work.resolve("emulatorAcceptance/result.json")));
                for (String layer : List.of("integrationTest", "emulatorAcceptance")) {
                    for (JsonObject record : records(work.resolve(layer), runId)) {
                        List<Path> files = new ArrayList<>();
                        for (var file : record.getAsJsonArray("evidence")) files.add(Path.of(file.getAsString()));
                        AcceptancePlan.Key key = new AcceptancePlan.Key(record.get("id").getAsString(), record.get("target").getAsString());
                        GateStatus status = GateStatus.valueOf(record.get("status").getAsString());
                        if (status == GateStatus.NOT_APPLICABLE && layer.equals("emulatorAcceptance") && files.size() == 1) {
                            try {
                                acceptance.recordVerifiedAbsence(InventoryProof.read(files.get(0), work.resolve(layer), runId, candidate, key));
                            } catch (IOException invalidProof) {
                                acceptance.record(key, new AcceptanceReport.Result(GateStatus.BLOCKED, "Unverified applicability exclusion: " + invalidProof.getMessage(), List.of()));
                            }
                        } else {
                            acceptance.record(key, importedResult(status, record.get("reason").getAsString(), files));
                        }
                    }
                }
                acceptance.cleanup(emulator.status() == GateStatus.PASS ? outcome(emulator, work.resolve("emulatorAcceptance/result.json"))
                    : new AcceptanceReport.Result(GateStatus.BLOCKED, "Complete runtime cleanup acceptance unavailable", List.of()));
                JsonObject accepted = acceptance.json();
                report.add("acceptance", accepted); report.addProperty("verdict", acceptance.verdict().name());
                report.addProperty("fullAcceptance", accepted.get("fullAcceptance").getAsBoolean());
            } else {
                boolean failed = List.of(unit, integration, emulator).stream().anyMatch(value -> value.status() == GateStatus.FAIL);
                report.addProperty("verdict", failed ? "FAIL" : "BLOCKED");
                report.addProperty("reason", unit.status() != GateStatus.PASS ? "Unit prerequisite failed; integration and emulator were not dispatched"
                    : "Verified candidate catalog unavailable; no functional coverage can be admitted");
            }
            BasicFileAttributes after = Files.readAttributes(candidate, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!java.util.Objects.equals(before.fileKey(), after.fileKey()) || before.size() != after.size() || !before.lastModifiedTime().equals(after.lastModifiedTime())) {
                report.addProperty("verdict", "FAIL"); report.addProperty("fullAcceptance", false); report.addProperty("reason", "Candidate changed during verification");
            }
        } catch (Exception failure) {
            report.addProperty("verdict", "FAIL"); report.addProperty("fullAcceptance", false); report.addProperty("reason", failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage());
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
        } finally {
            report.addProperty("completedAt", Instant.now().toString()); RunFiles.write(result, report);
        }
        JsonObject terminal = new JsonObject(); terminal.addProperty("verdict", report.get("verdict").getAsString());
        terminal.addProperty("fullAcceptance", report.get("fullAcceptance").getAsBoolean()); terminal.addProperty("report", result.toString()); System.out.println(terminal);
        return report.get("fullAcceptance").getAsBoolean() ? 0 : "FAIL".equals(report.get("verdict").getAsString()) ? 1 : 2;
    }

    private static Stage stage(Path root, Path work, String runId, String task, Map<String, String> options, boolean offline) throws Exception {
        Path directory = RunFiles.directory(work.resolve(task));
        List<String> command = new ArrayList<>(List.of(root.resolve("gradlew").toString(), task, "--rerun-tasks", "--console=plain"));
        if (offline) command.add("--offline");
        options.forEach((key, value) -> command.add("-P" + key + "=" + value));
        command.add("-PcheckRunId=" + runId); command.add("-PcheckWorkDir=" + directory);
        Path log = directory.resolve("gradle.log"); long started = System.currentTimeMillis();
        Map<String, String> environment = new java.util.HashMap<>(); environment.put("TMPDIR", directory.toString());
        String sdk = System.getenv("ANDROID_SDK_ROOT") == null ? System.getenv("ANDROID_HOME") : System.getenv("ANDROID_SDK_ROOT");
        if (sdk != null) { environment.put("ANDROID_SDK_ROOT", sdk); environment.put("ANDROID_HOME", sdk); }
        HostProcess.Result process = HostProcess.runAt(root, log, command, 3600000, environment);
        int tests = 0, failures = 0, errors = 0, skipped = 0;
        Path xml = root.resolve("build/test-results/" + task);
        if (Files.isDirectory(xml)) {
            try (var entries = Files.list(xml)) {
                for (Path file : entries.filter(path -> path.getFileName().toString().matches("TEST-.*\\.xml")).toList()) {
                    if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.getLastModifiedTime(file).toMillis() < started) continue;
                    var factory = DocumentBuilderFactory.newInstance();
                    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
                    var suite = factory.newDocumentBuilder().parse(file.toFile()).getDocumentElement();
                    tests += Integer.parseInt(suite.getAttribute("tests")); failures += Integer.parseInt(suite.getAttribute("failures"));
                    errors += Integer.parseInt(suite.getAttribute("errors")); skipped += Integer.parseInt(suite.getAttribute("skipped"));
                }
            }
        }
        boolean components = true; JsonArray componentProblems = new JsonArray();
        if (task.equals("unitTest")) {
            for (String name : COMPONENTS) {
                Path file = root.resolve("build/reports/unit-components/" + name + ".json");
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) { components = false; continue; }
                JsonObject current = RunFiles.read(file);
                Path retained = RunFiles.directory(directory.resolve("components"));
                RunFiles.write(retained.resolve(name + ".json"), current);
                Path raw = root.resolve("build/reports/unit-components/" + name + ".log");
                if (Files.isRegularFile(raw, LinkOption.NOFOLLOW_LINKS)) Files.copy(raw, retained.resolve(name + ".log"));
                boolean passed = current.has("runId") && runId.equals(current.get("runId").getAsString()) && "PASS".equals(current.get("status").getAsString());
                components &= passed;
                if (!passed) { JsonObject problem = new JsonObject(); problem.addProperty("task", name);
                    problem.addProperty("status", current.get("status").getAsString()); problem.addProperty("log", retained.resolve(name + ".log").toString()); componentProblems.add(problem); }
            }
        }
        GateStatus status = process.exit() == 0 && tests > 0 && failures == 0 && errors == 0 && skipped == 0 && components ? GateStatus.PASS : GateStatus.FAIL;
        JsonArray missing = new JsonArray();
        if (task.equals("integrationTest") && status == GateStatus.PASS) {
            List<JsonObject> observed = records(directory, runId);
            for (var requirement : RunFiles.read(root.resolve("release/beta1-functional-plan.json")).getAsJsonArray("integrationCases")) {
                JsonObject item = requirement.getAsJsonObject(); if (!item.get("required").getAsBoolean()) continue;
                long matches = observed.stream().filter(row -> item.get("id").equals(row.get("id"))
                    && "product".equals(row.get("target").getAsString()) && "PASS".equals(row.get("status").getAsString())).count();
                if (matches != 1) missing.add(item.get("id"));
            }
            if (!missing.isEmpty()) status = GateStatus.BLOCKED;
        }
        JsonObject result = new JsonObject(); result.add("missingRequiredCases", missing); result.addProperty("status", status.name()); result.addProperty("task", task);
        result.addProperty("exitCode", process.exit()); result.addProperty("tests", tests); result.addProperty("failures", failures);
        result.addProperty("errors", errors); result.addProperty("skipped", skipped); result.addProperty("componentSuitesPassed", components); result.add("componentProblems", componentProblems); result.addProperty("log", log.toString());
        RunFiles.write(directory.resolve("result.json"), result);
        return new Stage(status, result);
    }
    static AcceptanceReport.Result importedResult(GateStatus status, String reason, List<Path> files) {
        if (status == GateStatus.NOT_APPLICABLE)
            return new AcceptanceReport.Result(GateStatus.BLOCKED, "Unverified applicability exclusion: " + reason, files);
        return new AcceptanceReport.Result(status, reason, files);
    }
    private static AcceptanceReport.Result outcome(Stage stage, Path file) {
        return new AcceptanceReport.Result(stage.status(), "Current-run " + stage.status().name(),
            Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) ? List.of(file) : List.of());
    }
    static List<JsonObject> records(Path work, String runId) throws IOException {
        Path directory = work.resolve("cases");
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return List.of();
        RunFiles.directory(directory);
        List<JsonObject> records = new ArrayList<>();
        try (var paths = Files.list(directory)) {
            for (Path file : paths.toList()) {
                if (records.size() >= 16384 || !file.getFileName().toString().matches("[0-9a-f-]{36}\\.json")) throw new IOException("invalid case record inventory");
                JsonObject value = RunFiles.read(file);
                if (!value.keySet().equals(java.util.Set.of("schemaVersion", "runId", "id", "target", "status", "reason", "evidence"))
                        || !"1".equals(value.get("schemaVersion").getAsString()) || !runId.equals(value.get("runId").getAsString()))
                    throw new IOException("case evidence belongs to another run or schema");
                records.add(value);
            }
        }
        return records;
    }
    private static Stage skipped(String reason) {
        JsonObject value = new JsonObject(); value.addProperty("status", "NOT_RUN"); value.addProperty("reason", reason);
        return new Stage(GateStatus.NOT_RUN, value);
    }
}
