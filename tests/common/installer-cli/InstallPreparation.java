package ru.voboost.tests.installer.cli;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Set;

/** Existing typed preparation receipts from canonical monitored stderr; never dispatches preparation. */
public final class InstallPreparation {

    public record Proof(boolean readiness, boolean enforcing, boolean acceptedJournal,
            int receipts) {}

    private static final Set<String> FIELDS = Set.of("schemaVersion", "deviceSerial", "sequence",
            "context", "step", "attempt", "transactionId", "manifestSha256", "journal", "bootId",
            "phase", "rebootCount", "statusSequence", "journalSha256", "result", "exitCode",
            "error", "errorTruncated", "stdoutSha256", "stdoutBytes", "stderrSha256",
            "stderrBytes");

    private static final List<String> PHASES = List.of("prepare", "policy-backup", "runtime-apply",
            "app", "policy-apply", "reboot-first", "agents", "reboot-second", "runtime-verify",
            "inventory-commit", "cleanup", "complete");

    private static final Set<String> CONTEXTS = Set.of("preflight", "staging", "admission", "retry",
            "observation", "post-reboot", "completion", "failure-recovery");

    private static final Set<String> STEPS = Set.of("wait-device", "root", "remount",
            "relax-first-phase", "verify-write-access", "enforce-selinux");

    private static void require(boolean value, String why) throws IOException {
        if (!value)
            throw new IOException("install preparation receipt: " + why);
    }

    private static boolean nil(JsonObject value, String key) {
        return value.has(key) && value.get(key).isJsonNull();
    }

    private static String text(JsonObject value, String key) throws IOException {
        require(value.has(key) && value.get(key).isJsonPrimitive()
                && value.getAsJsonPrimitive(key).isString(), key);
        String result = value.get(key).getAsString();
        require(result.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 1024
                && !result.contains("\0"), "bounded " + key);
        return result;
    }

    private static BigInteger number(JsonObject value, String key,
            boolean positive) throws IOException {
        require(value.has(key) && value.get(key).isJsonPrimitive()
                && value.getAsJsonPrimitive(key).isNumber(), key);
        String raw = value.get(key).getAsString();
        require(raw.matches(positive ? "[1-9][0-9]{0,19}" : "0|[1-9][0-9]{0,19}"), key);
        BigInteger result = new BigInteger(raw);
        require(result.bitLength() <= 64, key);
        return result;
    }

    private static boolean yes(JsonObject value, String key) throws IOException {
        require(value.has(key) && value.get(key).isJsonPrimitive()
                && value.getAsJsonPrimitive(key).isBoolean(), key);
        return value.get(key).getAsBoolean();
    }

    public static Proof read(List<JsonObject> rows, String serial, String manifest,
            Set<String> independentlyObservedBoots) throws IOException {
        require(rows.size() <= 512, "record budget");
        BigInteger previous = null;
        boolean enforcing = false, journal = false, failedAuthority = false;
        for (var row : rows) {
            require(row.keySet().equals(FIELDS) && number(row, "schemaVersion",
                    true).equals(BigInteger.ONE) && serial.equals(text(row, "deviceSerial")),
                    "closed schema/serial");
            BigInteger sequence = number(row, "sequence", true);
            require(previous == null || sequence.equals(previous.add(BigInteger.ONE)),
                    "sequence continuity");
            previous = sequence;
            String context = text(row, "context"), step = text(row, "step"), result = text(row,
                    "result");
            require(CONTEXTS.contains(context) && STEPS.contains(step) && Set.of("success",
                    "failed", "transport-failed").contains(result), "operation vocabulary");
            require(number(row, "attempt", true).compareTo(BigInteger.valueOf(30)) <= 0,
                    "attempt bound");
            require(nil(row, "transactionId") || manifest.equals(text(row, "transactionId")),
                    "transaction");
            require(nil(row, "manifestSha256") || manifest.equals(text(row, "manifestSha256")),
                    "candidate manifest");
            if ("preflight".equals(context))
                require(nil(row, "transactionId") && nil(row, "manifestSha256"),
                        "preflight identity");
            if ("staging".equals(context))
                require(!nil(row, "transactionId") && !nil(row, "manifestSha256"),
                        "staging identity");
            if (!Set.of("staging", "failure-recovery").contains(context))
                require(nil(row, "manifestSha256"), "manifest context");
            int populated = 0;
            for (String field : List.of("journal", "bootId", "phase", "rebootCount",
                    "statusSequence", "journalSha256"))
                if (!nil(row, field))
                    populated++;
            if (populated != 0) {
                require(populated == 6 && manifest.equals(text(row, "transactionId"))
                        && ("recovery/" + manifest + "/install-journal.json").equals(text(row,
                        "journal")), "accepted journal identity");
                require(independentlyObservedBoots.contains(text(row, "bootId")),
                        "journal boot lacks independent observation");
                String phase = text(row, "phase");
                int phaseIndex = PHASES.indexOf(phase.startsWith("install-") ? phase.substring(8) : "");
                require(phaseIndex >= 0, "journal phase");
                require(number(row, "rebootCount", false).compareTo(BigInteger.valueOf(2)) <= 0,
                        "journal reboot count");
                BigInteger status = number(row, "statusSequence", true),
                        high = BigInteger.valueOf((phaseIndex + 1) * 3L);
                require(status.compareTo(high.subtract(BigInteger.TWO)) >= 0
                        && status.compareTo(high) <= 0, "journal status sequence");
                // Existing digest fields are retained as opaque producer data, never firmware gates or authority.
                text(row, "journalSha256");
                require(!Set.of("preflight", "staging", "admission").contains(context),
                        "unaccepted initial journal");
                if ("success".equals(result))
                    journal = true;
            }
            boolean truncated = yes(row, "errorTruncated");
            if ("transport-failed".equals(result)) {
                require(nil(row, "exitCode"), "transport exit");
                for (String field : List.of("stdoutSha256", "stdoutBytes", "stderrSha256",
                        "stderrBytes"))
                    require(nil(row, field), "transport output");
            } else {
                text(row, "stdoutSha256");
                text(row, "stderrSha256");
                number(row, "stdoutBytes", false);
                number(row, "stderrBytes", false);
                require(row.has("exitCode") && row.get("exitCode").isJsonPrimitive()
                        && row.getAsJsonPrimitive("exitCode").isNumber()
                        && row.get("exitCode").getAsString().matches("-?(?:0|[1-9][0-9]{0,9})"),
                        "exit code");
                try {
                    Integer.parseInt(row.get("exitCode").getAsString());
                } catch (NumberFormatException invalid) {
                    throw new IOException("install preparation exit outside i32", invalid);
                }
            }
            if ("success".equals(result))
                require("0".equals(row.get("exitCode").getAsString()) && nil(row, "error")
                        && !truncated, "successful exit/error");
            else {
                text(row, "error");
                require("transport-failed".equals(result)
                        || !"0".equals(row.get("exitCode").getAsString()), "failed zero exit");
            }
            if (Set.of("remount", "relax-first-phase", "verify-write-access").contains(step)
                    && !"success".equals(result))
                failedAuthority = true;
            if ("enforce-selinux".equals(step) && "success".equals(result) && Set.of("observation",
                    "post-reboot", "completion", "retry").contains(context))
                enforcing = true;
        }
        List<String> wanted = List.of("wait-device", "root", "wait-device", "remount",
                "relax-first-phase", "verify-write-access");
        boolean readiness = false;
        for (int index = 0; index + wanted.size() <= rows.size(); index++) {
            var first = rows.get(index);
            boolean matched = Set.of("staging", "admission").contains(text(first, "context"));
            for (int offset = 0; offset < wanted.size() && matched; offset++) {
                var row = rows.get(index + offset);
                matched = text(row, "context").equals(text(first, "context"))
                        && row.get("transactionId").equals(first.get("transactionId"))
                        && row.get("manifestSha256").equals(first.get("manifestSha256"))
                        && wanted.get(offset).equals(text(row, "step"))
                        && "success".equals(text(row, "result")) && number(row, "attempt",
                        true).equals(offset < 3 ? number(first, "attempt", true) : BigInteger.ONE);
            }
            readiness |= matched;
        }
        return new Proof(readiness && !failedAuthority, enforcing, journal, rows.size());
    }

    private InstallPreparation() {}
}
