package ru.voboost.tests.installer.cli;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import ru.voboost.tests.RunFiles;

/** Offline timings from retained CLI streams and their durable lifecycle operation. */
public final class LifecycleTiming {
    private record Event(Instant at, long sequence, String message, Path file, int line) {}
    private record Stage(String name, boolean milestone) {}
    private static final Set<String> COMMANDS = Set.of("install", "uninstall", "update");
    private static final List<String> STAGES = List.of("connection", "preflight", "transfer", "android-apply",
            "android-removal", "android-configuration", "android-readiness", "qnx-prepare", "qnx-activation",
            "qnx-removal", "reboot-reconnect", "final-verification");
    private LifecycleTiming() {}

    public static JsonObject read(String command, String phase, Path out, Path err, String operationState)
            throws IOException {
        if (!COMMANDS.contains(command)) throw new IOException("install, uninstall or update timing required");
        JsonObject result = new JsonObject();
        result.addProperty("command", command); result.addProperty("phase", phase);
        result.addProperty("operationState", operationState);
        result.addProperty("stdout", out.toAbsolutePath().normalize().toString());
        if (err != null) result.addProperty("stderr", err.toAbsolutePath().normalize().toString());
        List<Event> events = new ArrayList<>();
        List<String> output = new ArrayList<>();
        JsonArray warnings = new JsonArray();
        readLines(out, events, output, warnings);
        if (err != null && !out.equals(err)) readLines(err, events, output, warnings);
        events.sort(Comparator.comparingLong(Event::sequence));
        boolean terminal = output.stream().anyMatch(line -> terminal(command, line));
        result.addProperty("terminalOutputObserved", terminal);
        Path processFile = out.resolveSibling(out.getFileName() + ".timing.json");
        JsonObject process;
        if (Files.isRegularFile(processFile)) {
            process = RunFiles.read(processFile);
            if (!process.has("schemaVersion") || process.get("schemaVersion").getAsInt() != 1
                    || !process.has("monotonicElapsedNanos") || process.get("monotonicElapsedNanos").getAsLong() < 0)
                throw new IOException("invalid CLI timing: " + processFile);
            process.addProperty("evidence", processFile.toAbsolutePath().toString());
        } else {
            process = new JsonObject(); process.addProperty("status", "unavailable");
            process.addProperty("reason", "No monotonic process clock or exit result was recorded");
        }
        result.add("process", process);
        boolean failed = process.has("exit") && !process.get("exit").isJsonNull() && process.get("exit").getAsInt() != 0;
        result.addProperty("status", failed ? "process-failed" : terminal ? "terminal-output-observed" : "unfinished");
        if (process.has("status") && !Set.of("exited", "unavailable").contains(process.get("status").getAsString()))
            result.addProperty("status", "process-interrupted");
        if (terminal && !Set.of("verified", "unavailable").contains(operationState))
            warnings.add("Terminal output and durable operation state differ; acceptance is unverified");
        JsonArray stages = new JsonArray();
        Event start = null, previous = null;
        Stage current = null;
        boolean clockRegressed = false;
        int sequenceGaps = 0;
        Set<String> seen = new java.util.HashSet<>();
        for (Event event : events) {
            if (previous != null) {
                if (event.at().isBefore(previous.at())) clockRegressed = true;
                if (event.sequence() > previous.sequence() + 1) sequenceGaps++;
                if (event.sequence() <= previous.sequence()) warnings.add("Duplicate or regressing canonical sequence: " + event.sequence());
            }
            previous = event;
            Stage next = stage(command, event.message());
            if (next == null || next.equals(current)) continue;
            if (current != null) stages.add(interval(current, start, event, clockRegressed));
            start = event; current = next; seen.add(next.name());
        }
        if (current != null) {
            JsonObject open = interval(current, start, null, clockRegressed);
            if (!current.milestone() && previous != null && !clockRegressed)
                open.addProperty("observedAtLeastWallMs", Duration.between(start.at(), previous.at()).toMillis());
            stages.add(open);
        }
        result.add("stages", stages);
        result.addProperty("canonicalEventCount", events.size());
        result.addProperty("sequenceGapCount", sequenceGaps);
        if (!events.isEmpty()) {
            result.addProperty("firstEventAt", events.get(0).at().toString());
            result.addProperty("lastEventAt", events.get(events.size() - 1).at().toString());
            if (!clockRegressed) result.addProperty("observedEventWallMs",
                    Duration.between(events.get(0).at(), events.get(events.size() - 1).at()).toMillis());
        }
        if (clockRegressed) warnings.add("Canonical UTC clock regressed; stage durations are unverified");
        if (sequenceGaps > 0) warnings.add("Canonical sequences have gaps; filtered or absent records cannot provide additional stage boundaries");
        JsonArray unobserved = new JsonArray();
        for (String name : STAGES) if (!seen.contains(name)) unobserved.add(name);
        result.add("unobservedStages", unobserved); result.add("warnings", warnings);
        return result;
    }

    private static void readLines(Path file, List<Event> events, List<String> output, JsonArray warnings)
            throws IOException {
        if (!Files.isRegularFile(file)) { warnings.add("Stream unavailable: " + file); return; }
        if (Files.size(file) > 32L * 1024 * 1024) throw new IOException("CLI stream exceeds timing input bound: " + file);
        int number = 0;
        for (String line : Files.readAllLines(file)) {
            number++; output.add(line);
            if (!line.startsWith("{")) continue;
            try {
                JsonObject row = RunFiles.parse(line);
                if (!row.has("source") || !row.get("source").isJsonObject()
                        || !"installer".equals(text(row.getAsJsonObject("source"), "kind"))
                        || !"installer".equals(text(row, "component"))) continue;
                if (!row.has("schemaVersion") || row.get("schemaVersion").getAsInt() != 1
                        || !row.has("sequence") || row.get("sequence").getAsLong() < 1)
                    throw new IOException("invalid canonical installer event");
                events.add(new Event(Instant.parse(text(row, "timestamp")), row.get("sequence").getAsLong(),
                        text(row, "message"), file.toAbsolutePath().normalize(), number));
            } catch (IOException | RuntimeException error) {
                warnings.add("Unparsed JSON row: " + file + ":" + number);
            }
        }
    }

    private static boolean terminal(String command, String line) {
        if (command.equals("install") && line.matches("Install completed: transaction [0-9a-f]{64}")) return true;
        if (command.equals("update") && line.matches("Update completed: transaction [0-9a-f]{64}")) return true;
        if (command.equals("uninstall") && line.startsWith("{")) {
            try {
                JsonObject row = RunFiles.parse(line);
                if (row.has("uninstall") && row.get("uninstall").isJsonObject()) row = row.getAsJsonObject("uninstall");
                else if (row.has("absence") && row.get("absence").isJsonObject()) row = row.getAsJsonObject("absence");
                return text(row, "transactionId").matches("[0-9a-f]{64}")
                        && !text(row, "bootId").isEmpty() && row.has("checks") && row.get("checks").isJsonArray();
            } catch (IOException | RuntimeException ignored) { return false; }
        }
        return false;
    }

    private static Stage stage(String command, String message) {
        String name;
        if (message.startsWith("Connecting to the head unit")) name = "connection";
        else if (message.startsWith("Connected to the head unit")) name = "unattributed-gap";
        else if (message.equals("Verifying the installation package")) name = "preflight";
        else if (message.equals("Only policy remains unproved; capturing stock policy inputs")) name = "preflight";
        else if (message.equals("Stock policy inputs verified; restarting to prove the original policy")) name = "reboot-reconnect";
        else if (message.equals("Removing Voboost and restoring the head unit"))
            name = command.equals("install") ? "preflight" : "android-removal";
        else if (message.startsWith("Clean install preflight passed") || message.startsWith("installed product is ready:"))
            name = "unattributed-gap";
        else if (message.equals("Sending the verified installation files to the head unit")
                || message.equals("Transferring the installation package to the head unit")
                || message.equals("Copying the verified Voboost update")) name = "transfer";
        else if (message.equals("All installation files were transferred and verified")) name = "unattributed-gap";
        else if (message.startsWith("Restarting the head unit") || message.startsWith("Waiting for Android to reconnect")
                || message.startsWith("Waiting for the head unit to reconnect") || message.startsWith("Restarting to ")) name = "reboot-reconnect";
        else if (message.equals("Preparing the application and connecting Voboost to vehicle settings")) name = "android-configuration";
        else if (message.equals("Waiting for the application to start and become ready")) name = "android-readiness";
        else if (message.startsWith("Cluster recovery: restarting QNX")) name = "qnx-activation";
        else if (message.equals("Cluster recovery: signed new-boot absence verified and accepted by root")) return new Stage("qnx-absence-verified", true);
        else if (message.startsWith("Cluster recovery:") || message.startsWith("Continuing existing Cluster removal"))
            name = command.equals("uninstall") ? "qnx-removal" : "qnx-prepare";
        else if (message.equals("Cluster service is running on the verified installation")) return new Stage("qnx-ready", true);
        else if (message.startsWith("Device installation finished;") || message.startsWith("Checking Voboost services after restart")
                || message.startsWith("The application and all installed files were verified")
                || message.startsWith("Checking that Voboost has been completely removed")
                || message.startsWith("Fresh stock boot verified") || message.equals("Verifying the complete installed update")
                || message.equals("Checking updated services after restart") || message.equals("Checking system permissions after restart")) name = "final-verification";
        else if (message.startsWith("Starting installation on the head unit") || message.startsWith("Preparing installation on the head unit")
                || message.startsWith("Installing ") || message.equals("Applying Voboost SELinux permissions")
                || message.startsWith("Preparing the update") || message.startsWith("Preparing the complete updated product")
                || message.startsWith("Preparing updated ") || message.startsWith("Activating updated ")
                || message.equals("Applying the verified system update")) name = "android-apply";
        else if (message.startsWith("Preparing to remove Voboost") || message.startsWith("Stopping Voboost in vehicle applications")
                || message.startsWith("Preparing to restore the original SELinux policy")
                || message.startsWith("Removing the Voboost application") || message.startsWith("Removing Voboost services")
                || message.startsWith("Removing remaining Voboost files") || message.startsWith("Uninstall request published")) name = "android-removal";
        else return null;
        return new Stage(name, false);
    }

    private static JsonObject interval(Stage stage, Event start, Event end, boolean regressed) {
        JsonObject result = new JsonObject(); result.addProperty("stage", stage.name());
        result.addProperty("clock", "canonical-utc-event"); result.add("start", evidence(start));
        if (stage.milestone()) result.addProperty("status", "milestone-only");
        else if (end == null) result.addProperty("status", "end-not-recorded");
        else {
            result.add("end", evidence(end));
            result.addProperty("status", regressed ? "clock-unverified" : "bounded-by-next-stage-event");
            if (!regressed && !end.at().isBefore(start.at())) result.addProperty("wallMs", Duration.between(start.at(), end.at()).toMillis());
        }
        return result;
    }

    private static JsonObject evidence(Event event) {
        JsonObject result = new JsonObject(); result.addProperty("at", event.at().toString());
        result.addProperty("sequence", event.sequence()); result.addProperty("message", event.message());
        result.addProperty("file", event.file().toString()); result.addProperty("line", event.line()); return result;
    }

    public static List<JsonObject> discover(Path directory) throws IOException {
        List<JsonObject> result = new ArrayList<>();
        try (var files = Files.walk(directory)) {
            for (Path file : files.filter(path -> path.getFileName().toString().equals("operation.json")
                    && path.getParent() != null && path.getParent().getParent() != null
                    && path.getParent().getParent().getFileName().toString().equals("operations")).sorted().toList()) {
                JsonObject operation = RunFiles.read(file);
                String command = text(operation, "kind").toLowerCase(java.util.Locale.ROOT);
                if (!COMMANDS.contains(command) || !operation.has("input")) continue;
                JsonObject input = operation.getAsJsonObject("input");
                if (!input.has("process")) continue;
                JsonObject process = input.getAsJsonObject("process");
                Path session = file.getParent().getParent().getParent();
                JsonObject timing = read(command, text(input, "phase"), session.resolve(text(process, "stdout")),
                        session.resolve(text(process, "stderr")), text(operation, "state"));
                timing.addProperty("route", input.has("route") ? text(input, "route") : "cli");
                if (process.has("command") && process.get("command").isJsonArray()
                        && process.getAsJsonArray("command").size() > 1)
                    timing.addProperty("entryPoint", process.getAsJsonArray("command").get(1).getAsString());
                timing.addProperty("operationEvidence", file.toAbsolutePath().toString()); result.add(timing);
            }
        }
        return result;
    }

    public static JsonObject report(List<JsonObject> commands) {
        JsonObject report = new JsonObject(); report.addProperty("schemaVersion", 1);
        report.addProperty("functionalAcceptance", false);
        report.addProperty("interpretation", "Process duration uses the independent monotonic clock when recorded. Stage spans use observed canonical UTC events, including polling/replay delay; they are not internal device timings. Untimestamped terminal output cannot close a span. Unobserved stages may be inapplicable or unlogged. Environment setup is excluded from command timing.");
        JsonArray rows = new JsonArray(); commands.forEach(rows::add); report.add("commands", rows);
        JsonArray missing = new JsonArray();
        for (String name : List.of("install", "uninstall", "update"))
            if (commands.stream().noneMatch(row -> name.equals(text(row, "command")))) missing.add(name);
        report.add("unobservedCommands", missing); return report;
    }

    public static String markdown(JsonObject report) {
        StringBuilder result = new StringBuilder("# Installer lifecycle timing\n\n");
        result.append(report.get("interpretation").getAsString()).append("\n\n");
        result.append("Unobserved commands: `").append(report.get("unobservedCommands")).append("`. Functional acceptance is not established by this report.\n\n");
        for (JsonElement value : report.getAsJsonArray("commands")) {
            JsonObject command = value.getAsJsonObject(), process = command.getAsJsonObject("process");
            result.append("## ").append(text(command, "command")).append(" / ").append(text(command, "phase")).append("\n\n");
            result.append("Status: `").append(text(command, "status")).append("`; operation: `").append(text(command, "operationState")).append("`.\n\n");
            if (command.has("route")) result.append("Route: `").append(text(command, "route")).append("`; CLI entry: `")
                    .append(text(command, "entryPoint")).append("`.\n\n");
            result.append("Monotonic process duration: ").append(process.has("monotonicElapsedNanos")
                    ? process.get("monotonicElapsedNanos").getAsLong() / 1_000_000.0 + " ms (" + text(process, "status") + ")"
                    : "unavailable").append(".\n\n");
            result.append("Observed canonical event span: ").append(command.has("observedEventWallMs")
                    ? command.get("observedEventWallMs").getAsLong() + " ms" : "unavailable").append(". This is not total command time.\n\n");
            result.append("| Stage | UTC start | UTC end | Wall ms | Status |\n| --- | --- | --- | ---: | --- |\n");
            for (JsonElement row : command.getAsJsonArray("stages")) {
                JsonObject stage = row.getAsJsonObject();
                result.append("| ").append(text(stage, "stage")).append(" | ").append(text(stage.getAsJsonObject("start"), "at"))
                        .append(" | ").append(stage.has("end") ? text(stage.getAsJsonObject("end"), "at") : "unavailable")
                        .append(" | ").append(stage.has("wallMs") ? stage.get("wallMs").getAsString()
                                : stage.has("observedAtLeastWallMs") ? ">= " + stage.get("observedAtLeastWallMs").getAsString() + " observed; end unknown" : "unavailable")
                        .append(" | ").append(text(stage, "status")).append(" |\n");
            }
            result.append("\nUnobserved stages: `").append(command.get("unobservedStages")).append("`.\n\n");
            for (JsonElement warning : command.getAsJsonArray("warnings")) result.append("- ").append(warning.getAsString()).append('\n');
            result.append("\nEvidence: `").append(text(command, "stdout")).append("`");
            if (command.has("stderr")) result.append(", `").append(text(command, "stderr")).append("`");
            result.append(".\n\n");
        }
        return result.toString();
    }

    private static String text(JsonObject object, String field) {
        return object.has(field) && object.get(field).isJsonPrimitive() ? object.get(field).getAsString() : "";
    }

    /** --output DIR [--run DIR] [--log COMMAND PHASE FILE], repeat inputs as needed. */
    public static void main(String[] arguments) throws Exception {
        Path output = null;
        List<JsonObject> commands = new ArrayList<>();
        for (int index = 0; index < arguments.length;) {
            switch (arguments[index++]) {
                case "--output" -> output = Path.of(arguments[index++]);
                case "--run" -> commands.addAll(discover(Path.of(arguments[index++])));
                case "--log" -> {
                    String command = arguments[index++], phase = arguments[index++];
                    commands.add(read(command, phase, Path.of(arguments[index++]), null, "unavailable"));
                }
                default -> throw new IOException("Usage: --output DIR [--run DIR] [--log COMMAND PHASE FILE]");
            }
        }
        if (output == null || commands.isEmpty()) throw new IOException("output directory and at least one command log required");
        Files.createDirectories(output);
        JsonObject report = report(commands);
        Files.writeString(output.resolve("timing.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
        Files.writeString(output.resolve("timing.md"), markdown(report));
        System.out.println(output.toAbsolutePath().resolve("timing.md"));
    }
}
