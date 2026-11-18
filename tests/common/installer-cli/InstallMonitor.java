package ru.voboost.tests.installer.cli;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayDeque;
import java.util.Set;

/** Observes ordinary installer milestones with independent, cancellation-fenced boot verification. */
public final class InstallMonitor implements CliProcess.Listener {
    public record Boot(String id, int rebootCount) {
        public Boot {
            if (id == null || !id.matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}") || rebootCount < 0 || rebootCount > 2)
                throw new IllegalArgumentException("invalid observed boot");
        }
    }
    public interface Observer {
        Boot verifyBoot(int rebootCount, String previousBoot, CliProcess.Control control) throws Exception;
        Boot currentBoot(CliProcess.Control control) throws Exception;
        String check(JsonObject request, CliProcess.Control control) throws Exception;
    }
    private static final Set<String> LOG_FIELDS = Set.of("schemaVersion", "sequence", "timestamp", "source", "level", "component", "message", "fields");
    private static final BigInteger MAX_SEQUENCE = new BigInteger("9007199254740991");
    private static final DateTimeFormatter TIMESTAMP = new DateTimeFormatterBuilder().appendInstant(3).toFormatter();
    private static final String CLEAN_PREFLIGHT = "Clean install preflight passed; Android absence verified before staging";
    private static final String CLEAN_VERIFIED = "clean-install-verified ";
    private final String manifest;
    private final String initialBoot;
    private final java.util.List<JsonObject> preparationRows = new java.util.ArrayList<>();
    private final Set<String> verifiedBoots = new java.util.HashSet<>();
    private final Observer observer;
    private final ArrayDeque<Integer> pending = new ArrayDeque<>();
    private String previousBoot;
    private String source;
    private long sequence;
    private int reboots;
    private boolean completed;
    private boolean cleanPreflight;
    private boolean staging;
    private boolean infoStaging;
    private JsonObject cleanReceipt;
    private String terminalFailure;

    public InstallMonitor(String manifest, String beforeBoot, Observer observer) {
        this.manifest = manifest; this.initialBoot = beforeBoot; verifiedBoots.add(beforeBoot);
        this.previousBoot = beforeBoot;
        this.observer = observer;
    }

    @Override public String line(CliProcess.Stream stream, String line, CliProcess.Control control) throws Exception {
        control.check();
        if (stream == CliProcess.Stream.OUT) {
            if (line.contains("Install preparation receipt: ") || line.startsWith(CLEAN_VERIFIED)
                    || line.equals(CLEAN_PREFLIGHT))
                throw new IOException("installer diagnostic appeared on stdout");
            if (line.startsWith("Install completed:")) {
                if (completed || !line.equals("Install completed: transaction " + manifest)) {
                    throw new IOException("installer terminal transaction receipt differs");
                }
                completed = true;
            }
        } else {
            observeStderr(line);
        }
        while (!pending.isEmpty()) verify(pending.remove(), control);
        return null;
    }

    static String retainedTerminalFailure(String manifest, String priorBoot, String stderr) throws IOException {
        InstallMonitor monitor = new InstallMonitor(manifest, priorBoot, null);
        for (String line : stderr.lines().toList()) monitor.observeStderr(line);
        return monitor.terminalFailure;
    }

    private void observeStderr(String line) throws IOException {
        if (line.startsWith("Install failed: ")) terminalFailure = line;
        else observeLog(line);
    }

    private void observeLog(String line) throws IOException {
        try {
            JsonObject row = JsonParser.parseString(line).getAsJsonObject();
            if (!row.toString().equals(line) || !row.keySet().equals(LOG_FIELDS)
                    || !number(row, "schemaVersion", "1") || !row.get("source").isJsonObject() || !row.get("fields").isJsonObject())
                throw new IOException("noncanonical installer log fields");
            JsonObject origin = row.getAsJsonObject("source");
            String id = text(origin, "id"), timestamp = text(row, "timestamp"), level = text(row, "level"), message = text(row, "message");
            String rawSequence = row.get("sequence").getAsString();
            if (!row.get("sequence").isJsonPrimitive() || !row.getAsJsonPrimitive("sequence").isNumber()
                    || !rawSequence.matches("[1-9][0-9]{0,15}") || new BigInteger(rawSequence).compareTo(MAX_SEQUENCE) > 0
                    || !origin.keySet().equals(Set.of("kind", "id")) || !"installer".equals(text(origin, "kind"))
                    || !Set.of("desktop", "cli").contains(id == null ? "" : id) || !"installer".equals(text(row, "component"))
                    || timestamp == null || !timestamp.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{3}Z")
                    || !TIMESTAMP.format(Instant.parse(timestamp)).equals(timestamp) || message == null
                    || !Set.of("debug", "info", "error").contains(level == null ? "" : level)
                    || row.getAsJsonObject("fields").entrySet().stream().anyMatch(entry -> !entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isString()))
                throw new IOException("installer log typed identity differs");
            long current = Long.parseLong(rawSequence);
            if (current <= sequence || (source != null && !source.equals(id))) throw new IOException("installer log sequence or source differs");
            sequence = current; source = id;
            if (message.startsWith("Install failed: ")) {
                terminalFailure = message;
            }
            if (message.equals(CLEAN_PREFLIGHT)) {
                if (cleanPreflight || staging || reboots != 0 || !pending.isEmpty()
                        || !"info".equals(level) || !row.getAsJsonObject("fields").isEmpty())
                    throw new IOException("clean install preflight was repeated, malformed or followed staging");
                cleanPreflight = true;
            } else if (message.startsWith(CLEAN_VERIFIED)) {
                if (!cleanPreflight || cleanReceipt != null || staging || reboots != 0 || !pending.isEmpty()
                        || !"info".equals(level) || !row.getAsJsonObject("fields").isEmpty())
                    throw new IOException("clean install receipt appeared outside preflight");
                cleanReceipt = cleanReceipt(message.substring(CLEAN_VERIFIED.length()));
                previousBoot = text(cleanReceipt, "bootId");
            } else if (message.equals("Sending the verified installation files to the head unit")
                    || message.equals("Starting Voboost installation; progress will continue through restarts")) {
                if (!"info".equals(level) || !row.getAsJsonObject("fields").isEmpty()
                        || (cleanPreflight && cleanReceipt == null))
                    throw new IOException("installation transfer started before the clean install receipt");
                staging = true;
                infoStaging = true;
            }
            if (message.startsWith("Install preparation receipt: ")) {
                if (!"debug".equals(level) || !row.getAsJsonObject("fields").isEmpty() || preparationRows.size() >= 512)
                    throw new IOException("preparation receipt is not bounded canonical DEBUG");
                JsonObject receipt = ru.voboost.tests.RunFiles.parse(message.substring("Install preparation receipt: ".length()));
                if ("staging".equals(text(receipt, "context"))) {
                    if (cleanPreflight && cleanReceipt == null)
                        throw new IOException("installation staged before the clean install receipt");
                    staging = true;
                }
                preparationRows.add(receipt);
            }
            for (int count = 1; count <= 2; ++count) {
                if (message.equals("Restarting the head unit (reboot " + count + "). Waiting for reconnection; install will continue automatically.")) {
                    if (!"info".equals(level) || !row.getAsJsonObject("fields").isEmpty() || count != reboots + pending.size() + 1)
                        throw new IOException("installer reboot progression differs");
                    pending.add(count);
                }
            }
        } catch (RuntimeException error) { throw new IOException("malformed installer log", error); }
    }

    private void verify(int count, CliProcess.Control control) throws Exception {
        control.check();
        Boot boot = observer.verifyBoot(count, previousBoot, control);
        control.check();
        if (boot.rebootCount() != count || boot.id().equals(previousBoot) || verifiedBoots.contains(boot.id()))
            throw new IOException("verified installer boot identity differs");
        verifiedBoots.add(boot.id());
        previousBoot = boot.id();
        reboots = count;
    }

    public InstallPreparation.Proof preparationProof(String serial) throws IOException {
        return InstallPreparation.read(preparationRows, serial, manifest, Set.copyOf(verifiedBoots));
    }

    String terminalFailure() {
        return terminalFailure;
    }

    private static JsonObject cleanReceipt(String raw) throws IOException {
        if (raw.length() > 256) throw new IOException("clean install receipt exceeds bound");
        JsonObject receipt = ru.voboost.tests.RunFiles.parse(raw);
        String boot = text(receipt, "bootId"), qnx = text(receipt, "qnx");
        if (!raw.equals(receipt.toString()) || !receipt.keySet().equals(Set.of("schemaVersion", "bootId", "android", "qnx"))
                || !number(receipt, "schemaVersion", "1") || !"absent".equals(text(receipt, "android"))
                || boot == null || !boot.matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}")
                || qnx == null || !Set.of("verified", "stock-structure", "not-applicable").contains(qnx))
            throw new IOException("clean install receipt identity differs");
        return receipt;
    }

    public JsonObject requireCleanPreflight() throws IOException {
        if (cleanReceipt == null || !infoStaging || initialBoot.equals(text(cleanReceipt, "bootId")))
            throw new IOException("single reinstall invocation lacks a fresh clean boot before installation transfer");
        return cleanReceipt.deepCopy();
    }

    public void requireComplete(int exit) throws IOException {
        if (terminalFailure != null) {
            throw new IOException("installer reported terminal failure: " + terminalFailure);
        }
        if (exit != 0 || !completed || !pending.isEmpty() || reboots != 2)
            throw new IOException("installer process lacks verified two-boot terminal completion");
    }
    private static String text(JsonObject value, String name) {
        return value.has(name) && value.get(name).isJsonPrimitive() && value.getAsJsonPrimitive(name).isString() ? value.get(name).getAsString() : null;
    }
    private static boolean number(JsonObject value, String name, String expected) {
        return value.has(name) && value.get(name).isJsonPrimitive() && value.getAsJsonPrimitive(name).isNumber() && expected.equals(value.get(name).getAsString());
    }
}
