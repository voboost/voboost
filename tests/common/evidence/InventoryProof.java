package ru.voboost.tests;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Re-evaluates a retained run-scoped inventory exclusion; no caller-supplied decision is trusted. */
public final class InventoryProof {
    private static final Set<String> FIELDS = Set.of("schemaVersion", "checkRunId", "productRunId", "sessionId", "machine", "serial", "bootId", "androidPid", "candidate", "operation", "link", "beforeStatus", "beforeSystem", "afterStatus", "afterSystem", "rawBeforeStatus", "rawBeforeSystem", "rawAfterStatus", "rawAfterSystem", "beforeStatusRequest", "beforeSystemRequest", "afterStatusRequest", "afterSystemRequest", "inventoryRequest", "inventoryOutput", "inventoryError", "inventoryBody", "inventoryExit", "inventoryExecution", "listRequest", "listOutput", "listError", "listBody", "listExit", "listExecution", "orderedEvents");
    private static final List<String> SOURCES = List.of("link", "beforeStatus", "beforeSystem", "afterStatus", "afterSystem", "rawBeforeStatus", "rawBeforeSystem", "rawAfterStatus", "rawAfterSystem", "beforeStatusRequest", "beforeSystemRequest", "afterStatusRequest", "afterSystemRequest", "inventoryRequest", "inventoryOutput", "inventoryError", "inventoryBody", "inventoryExecution", "listRequest", "listOutput", "listError", "listBody", "listExecution");
    public static final class OwnerRequired extends IOException {
        private OwnerRequired() { super("source-owned target owner or process is present"); }
    }
    public static final class Verified {
        private final AcceptancePlan.Key key;
        private final List<Path> evidence;
        private final String owner, checkRun;
        private final Path receipt, root, candidate;
        private Verified(AcceptancePlan.Key key, List<Path> evidence, String owner, Path receipt, Path root, String checkRun, Path candidate) {
            this.key = key; this.evidence = List.copyOf(evidence); this.owner = owner;
            this.receipt = receipt; this.root = root; this.checkRun = checkRun; this.candidate = candidate;
        }
        public void revalidate() throws IOException { InventoryProof.read(receipt, root, checkRun, candidate, key); }
        public void requireScope(String runId, Path candidatePath) throws IOException {
            if (!checkRun.equals(runId) || !candidate.toRealPath().equals(candidatePath.toRealPath()))
                throw new IOException("verified exclusion belongs to a different run or candidate");
        }
        public AcceptancePlan.Key key() { return key; }
        public List<Path> evidence() { return evidence; }
        public String owner() { return owner; }
    }
    private InventoryProof() {}

    public static Verified read(Path receipt, Path emulatorRoot, String checkRun, Path candidate, AcceptancePlan.Key key) throws IOException {
        try {
            Path root = emulatorRoot.toRealPath(); Path proof = receipt.toRealPath();
            if (!proof.startsWith(root) || !Files.isRegularFile(receipt, LinkOption.NOFOLLOW_LINKS)) throw new IOException("receipt outside current run");
            JsonObject value = RunFiles.read(proof);
            if (!value.keySet().equals(FIELDS) || integer(value, "schemaVersion") != 1 || !text(value, "checkRunId").equals(checkRun)
                    || !text(value, "candidate").equals(candidate.toRealPath().toString()) || integer(value, "inventoryExit") != 0 || integer(value, "listExit") != 0)
                throw new IOException("inventory receipt scope differs");
            String product = text(value, "productRunId"), session = text(value, "sessionId"), machine = text(value, "machine"), serial = text(value, "serial"), boot = text(value, "bootId");
            if (!product.matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}") || !session.matches("[0-9a-f]{32}")
                    || !serial.matches("emulator-[0-9]{4,5}") || (Integer.parseInt(serial.substring(9)) & 1) != 0
                    || !Path.of(machine).isAbsolute()) throw new IOException("invalid owned session scope");
            long pid = integer(value, "androidPid"); if (pid < 2) throw new IOException("invalid Android PID");
            Path operation = source(value, "operation", root); Path work = operation.getParent().getParent().getParent().toRealPath();
            if (!work.startsWith(root) || !operation.getParent().getFileName().toString().matches("[0-9]{6}")
                    || !operation.getFileName().toString().equals("operation.json") || !operation.getParent().getParent().getFileName().toString().equals("operations"))
                throw new IOException("invalid ProductRun operation location");
            JsonObject run = RunFiles.read(work.resolve("run.json")), op = RunFiles.read(operation);
            if (!run.keySet().equals(Set.of("schemaVersion", "runId", "machine", "work", "state")) || integer(run, "schemaVersion") != 1
                    || !Set.of("ready", "operation", "complete").contains(text(run, "state"))
                    || !op.keySet().equals(Set.of("schemaVersion", "runId", "sessionId", "kind", "input", "state", "evidence")) || integer(op, "schemaVersion") != 1
                    || !product.equals(text(run, "runId")) || !machine.equals(text(run, "machine")) || !work.toString().equals(text(run, "work"))
                    || !product.equals(text(op, "runId")) || !session.equals(text(op, "sessionId")) || !"CONFIGURE".equals(text(op, "kind")) || !"verified".equals(text(op, "state")))
                throw new IOException("ProductRun journal differs");
            history(work, operation, product, session);
            JsonObject link = RunFiles.read(source(value, "link", work));
            if (!link.keySet().equals(Set.of("schemaVersion", "checkRunId", "productRunId", "candidate", "machine", "run"))
                    || integer(link, "schemaVersion") != 1 || !checkRun.equals(text(link, "checkRunId"))
                    || !product.equals(text(link, "productRunId")) || !machine.equals(text(link, "machine"))
                    || !candidate.toRealPath().toString().equals(text(link, "candidate"))
                    || !source(link, "run", work).equals(work.resolve("run.json").toRealPath()))
                throw new IOException("check and ProductRun link differs");
            JsonObject input = object(op, "input");
            if (!"target-inventory".equals(text(input, "phase")) || !checkRun.equals(text(input, "checkRunId"))
                    || !candidate.toRealPath().toString().equals(text(input, "candidate"))) throw new IOException("inventory operation link differs");
            JsonArray retained = op.getAsJsonArray("evidence"); if (retained == null) throw new IOException("operation evidence missing");
            Set<String> paths = new java.util.HashSet<>(); for (var element : retained) paths.add(element.getAsString());
            List<Path> evidence = new ArrayList<>(); evidence.add(proof); evidence.add(operation); evidence.add(work.resolve("run.json"));
            if (!paths.contains(work.relativize(proof).toString())) throw new IOException("receipt not linked to operation");
            for (String field : SOURCES) {
                Path file = source(value, field, work); if (!paths.contains(work.relativize(file).toString())) throw new IOException("source not linked to operation"); evidence.add(file);
            }
            timeline(value, work);
            JsonObject beforeStatus = observed(value, work, "beforeStatus", "rawBeforeStatus", "session.status", session);
            JsonObject afterStatus = observed(value, work, "afterStatus", "rawAfterStatus", "session.status", session);
            JsonObject beforeSystem = observed(value, work, "beforeSystem", "rawBeforeSystem", "multimedia.system", session);
            JsonObject afterSystem = observed(value, work, "afterSystem", "rawAfterSystem", "multimedia.system", session);
            status(beforeStatus, session, machine, serial, pid); status(afterStatus, session, machine, serial, pid);
            system(beforeSystem, boot, pid); system(afterSystem, boot, pid);
            Path inventoryBinary = checkCommand(value, work, "inventory", "package-inventory", serial, candidate);
            Path listBinary = checkCommand(value, work, "list", "package-list", serial, candidate);
            if (!inventoryBinary.equals(listBinary)) throw new IOException("inventory binaries differ");
            var capture = TargetInventory.capture(checkRun, session, serial, boot, source(value, "inventoryBody", work));
            var checked = TargetInventory.crossCheck(capture, source(value, "listBody", work));
            TargetOwnerContract.Decision decision = TargetOwnerContract.evaluate(key, checked, checkRun, session, serial, boot);
            if (decision == TargetOwnerContract.Decision.REQUIRED) throw new OwnerRequired();
            if (decision != TargetOwnerContract.Decision.OWNER_ABSENT) throw new IOException("exact owner absence is not established");
            return new Verified(key, evidence, TargetOwnerContract.owner(key).packageName(), proof, root, checkRun, candidate);
        } catch (IllegalStateException | IllegalArgumentException | NullPointerException error) { throw new IOException("invalid inventory proof", error); }
    }
    private static void history(Path work, Path target, String product, String session) throws IOException {
        int end = Integer.parseInt(target.getParent().getFileName().toString());
        if (end < 1 || end > 16384) throw new IOException("invalid or oversized operation sequence");
        try (var entries = Files.list(work.resolve("operations"))) {
            List<Path> directories = entries.toList();
            if (directories.size() != end) throw new IOException("inventory is not the last product operation");
            for (Path directory : directories) {
                String name = directory.getFileName().toString();
                if (!name.matches("[0-9]{6}") || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                        || Integer.parseInt(name) < 1 || Integer.parseInt(name) > end)
                    throw new IOException("invalid or later product operation");
            }
        }
        for (int index = 1; index < end; index++) {
            Path path = work.resolve("operations").resolve(String.format(java.util.Locale.ROOT, "%06d", index)).resolve("operation.json");
            JsonObject prior = RunFiles.read(path);
            Set<String> fields = Set.of("schemaVersion", "runId", "sessionId", "kind", "input", "state");
            Set<String> withEvidence = Set.of("schemaVersion", "runId", "sessionId", "kind", "input", "state", "evidence");
            if ((!prior.keySet().equals(fields) && !prior.keySet().equals(withEvidence)) || integer(prior, "schemaVersion") != 1
                    || !product.equals(text(prior, "runId")) || !session.equals(text(prior, "sessionId"))
                    || !Set.of("INSTALL", "UPDATE", "UNINSTALL", "RESTORE", "REBOOT", "CONFIGURE").contains(text(prior, "kind"))
                    || !Set.of("verified", "failed-reconciled", "not-dispatched").contains(text(prior, "state"))
                    || !prior.has("input") || !prior.get("input").isJsonObject()) throw new IOException("incomplete ProductRun history");
            if (!"not-dispatched".equals(text(prior, "state"))) {
                if (!prior.has("evidence") || !prior.get("evidence").isJsonArray() || prior.getAsJsonArray("evidence").isEmpty())
                    throw new IOException("prior operation evidence missing");
                for (var entry : prior.getAsJsonArray("evidence")) {
                    if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) throw new IOException("invalid prior operation evidence");
                    Path relative = Path.of(entry.getAsString()); Path retained = work.resolve(relative).normalize();
                    if (relative.isAbsolute() || !retained.startsWith(work) || !Files.isRegularFile(retained, LinkOption.NOFOLLOW_LINKS)
                            || !retained.toRealPath().startsWith(work)) throw new IOException("prior operation evidence unavailable");
                }
            } else if (prior.has("evidence")) throw new IOException("not-dispatched operation has evidence");
        }
    }
    private static void timeline(JsonObject value, Path work) throws IOException {
        if (!value.has("orderedEvents") || !value.get("orderedEvents").isJsonArray()) throw new IOException("inventory timeline missing");
        JsonArray events = value.getAsJsonArray("orderedEvents");
        String[][] expected = {
            {"before-status", "beforeStatusRequest", "rawBeforeStatus"},
            {"before-system", "beforeSystemRequest", "rawBeforeSystem"},
            {"inventory", "inventoryRequest", "inventoryOutput", "inventoryError"},
            {"list", "listRequest", "listOutput", "listError"},
            {"after-system", "afterSystemRequest", "rawAfterSystem"},
            {"after-status", "afterStatusRequest", "rawAfterStatus"}
        };
        if (events.size() != expected.length) throw new IOException("inventory timeline incomplete");
        Set<Path> unique = new java.util.HashSet<>(); Set<String> requests = new java.util.HashSet<>();
        long previous = 0;
        for (int i = 0; i < expected.length; i++) {
            if (!events.get(i).isJsonObject()) throw new IOException("invalid inventory timeline event");
            JsonObject event = events.get(i).getAsJsonObject(); String[] row = expected[i];
            if (!event.keySet().equals(row.length == 4 ? Set.of("step", "request", "output", "error", "exit", "execution") : Set.of("step", "request", "output"))
                    || !row[0].equals(text(event, "step"))
                    || !source(event, "request", work).equals(source(value, row[1], work))
                    || !source(event, "output", work).equals(source(value, row[2], work)))
                throw new IOException("inventory timeline event differs");
            String field = switch (row[0]) {
                case "before-status" -> "beforeStatus"; case "before-system" -> "beforeSystem";
                case "after-system" -> "afterSystem"; case "after-status" -> "afterStatus";
                default -> row[0];
            };
            Path request = source(event, "request", work), output = source(event, "output", work);
            long requestOrder = sequence(request, "inventory-" + field + "-request-", ".json");
            long outputOrder = row.length == 3 ? sequence(output, "inventory-" + field + "-raw-", ".json")
                : sequence(output, "cli-", ".out");
            if (requestOrder <= previous || outputOrder <= requestOrder) throw new IOException("inventory observation sequence differs");
            if (row.length == 4 && sequence(source(event, "error", work), "cli-", ".err") != outputOrder)
                throw new IOException("inventory command output pairing differs");
            previous = outputOrder;
            if (!unique.add(source(event, "request", work)) || !unique.add(source(event, "output", work)))
                throw new IOException("inventory timeline reuses a request or response");
            if (row.length == 3 && !requests.add(text(RunFiles.read(source(event, "request", work)), "request_id")))
                throw new IOException("inventory timeline reuses native request identity");
            if (row.length == 4 && (integer(event, "exit") != 0 || !source(event, "error", work).equals(source(value, row[3], work))
                    || !source(event, "execution", work).equals(source(value, row[0] + "Execution", work))
                    || !unique.add(source(event, "execution", work)) || !unique.add(source(event, "error", work))))
                throw new IOException("inventory command failed or timeline differs");
        }
    }
    private static long sequence(Path path, String prefix, String suffix) throws IOException {
        String name = path.getFileName().toString();
        if (!name.startsWith(prefix) || !name.endsWith(suffix)) throw new IOException("inventory source name differs");
        String number = name.substring(prefix.length(), name.length() - suffix.length());
        if (!number.matches("[1-9][0-9]{0,8}")) throw new IOException("inventory source sequence invalid");
        return Long.parseLong(number);
    }
    private static Path checkCommand(JsonObject value, Path work, String prefix, String action, String serial, Path candidate) throws IOException {
        JsonObject request = RunFiles.read(source(value, prefix + "Request", work));
        if (!request.keySet().equals(Set.of("action")) || !action.equals(text(request, "action"))) throw new IOException("inventory action differs");
        JsonObject output = RunFiles.read(source(value, prefix + "Output", work));
        if (!output.keySet().equals(Set.of("schemaVersion", "serial", "body")) || integer(output, "schemaVersion") != 1
                || !serial.equals(text(output, "serial")) || !text(output, "body").equals(Files.readString(source(value, prefix + "Body", work))))
            throw new IOException("inventory command output differs");
        if (Files.size(source(value, prefix + "Error", work)) > 16384) throw new IOException("inventory stderr exceeds bound");
        JsonObject execution = RunFiles.read(source(value, prefix + "Execution", work));
        if (!execution.keySet().equals(Set.of("schemaVersion", "candidate", "binary", "driver", "argv", "request", "stdout", "stderr", "exit"))
                || integer(execution, "schemaVersion") != 1 || integer(execution, "exit") != 0
                || !candidate.toRealPath().toString().equals(text(execution, "candidate"))
                || !source(execution, "request", work).equals(source(value, prefix + "Request", work))
                || !source(execution, "stdout", work).equals(source(value, prefix + "Output", work))
                || !source(execution, "stderr", work).equals(source(value, prefix + "Error", work))) throw new IOException("inventory execution record differs");
        Path binary = Path.of(text(execution, "binary")).normalize();
        if (!binary.isAbsolute() || !binary.startsWith(work) || !binary.endsWith(Path.of("Voboost Installer.app/Contents/MacOS/voboost-installer")))
            throw new IOException("inventory binary outside mounted candidate");
        if (!execution.has("argv") || !execution.get("argv").isJsonArray()) throw new IOException("inventory argv missing");
        JsonArray argv = execution.getAsJsonArray("argv");
        List<String> expected = ru.voboost.tests.installer.cli.EmulatorCommand.of(Path.of(text(execution, "driver")), binary, Path.of(text(execution, "request")), serial);
        if (argv.size() != expected.size()) throw new IOException("inventory argv differs");
        for (int i = 0; i < expected.size(); i++) if (!argv.get(i).isJsonPrimitive() || !argv.get(i).getAsJsonPrimitive().isString()
                || !expected.get(i).equals(argv.get(i).getAsString())) throw new IOException("inventory argv differs");
        return binary;
    }
    private static JsonObject observed(JsonObject proof, Path work, String clean, String raw, String operation, String session) throws IOException {
        JsonObject expected = RunFiles.read(source(proof, clean, work));
        JsonObject envelope = RunFiles.read(source(proof, raw, work));
        JsonObject request = RunFiles.read(source(proof, clean + "Request", work));
        JsonObject response = object(envelope, "response");
        JsonObject identity = object(request, "session");
        if (!identity.keySet().equals(Set.of("id"))) throw new IOException("unexpected retained session credential or field");
        if (integer(request, "schema") != 2 || !operation.equals(text(request, "op"))
                || !session.equals(text(identity, "id")) || !text(request, "request_id").equals(text(response, "request_id"))
                || integer(response, "schema") != 2 || !"RESPONSE".equals(text(envelope, "state"))
                || !bool(response, "ok") || !expected.equals(object(response, "result")))
            throw new IOException("native observation source differs");
        return expected;
    }
    private static void status(JsonObject status, String session, String machine, String serial, long pid) throws IOException {
        JsonObject android = object(object(status, "deliveries"), "android");
        if (!bool(status, "ok") || bool(status, "stopped") || !session.equals(text(status, "session_id"))
                || !machine.equals(text(status, "machine_seed")) || pid != integer(status, "android_pid")
                || !serial.equals(text(android, "serial")) || pid != integer(android, "pid")) throw new IOException("owned session changed");
    }
    private static void system(JsonObject system, String boot, long pid) throws IOException {
        JsonObject observed = object(system, "value");
        if (!"ready".equals(text(system, "status")) || !boot.equals(text(system, "boot_id")) || !boot.equals(text(observed, "boot_id"))
                || pid != integer(observed, "owned_android_pid")) throw new IOException("Android boot identity changed");
    }
    private static Path source(JsonObject value, String key, Path root) throws IOException {
        Path path = Path.of(text(value, key)); if (!path.isAbsolute() || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("retained source missing");
        Path real = path.toRealPath(); if (!real.startsWith(root.toRealPath())) throw new IOException("source outside run"); return real;
    }
    private static JsonObject object(JsonObject value, String key) throws IOException {
        if (!value.has(key) || !value.get(key).isJsonObject()) throw new IOException("missing object " + key); return value.getAsJsonObject(key);
    }
    private static String text(JsonObject value, String key) throws IOException {
        if (!value.has(key) || !value.get(key).isJsonPrimitive() || !value.getAsJsonPrimitive(key).isString() || value.get(key).getAsString().isBlank()) throw new IOException("missing text " + key); return value.get(key).getAsString();
    }
    private static long integer(JsonObject value, String key) throws IOException {
        if (!value.has(key) || !value.get(key).isJsonPrimitive() || !value.getAsJsonPrimitive(key).isNumber()
                || !value.get(key).getAsString().matches("0|[1-9][0-9]{0,18}")) throw new IOException("invalid integer " + key);
        try { return Long.parseLong(value.get(key).getAsString()); } catch (NumberFormatException error) { throw new IOException("integer overflow", error); }
    }
    private static boolean bool(JsonObject value, String key) throws IOException {
        if (!value.has(key) || !value.get(key).isJsonPrimitive() || !value.getAsJsonPrimitive(key).isBoolean()) throw new IOException("invalid boolean " + key); return value.get(key).getAsBoolean();
    }
}
