package ru.voboost.tests.logging;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Comparator;
import java.util.List;

/** Boot-clock feedback observations, never ECU application or a latency acceptance threshold. */
public final class NativeStartupTiming {
    private NativeStartupTiming() {}
    private static BigInteger millis(JsonObject fields, String key) throws IOException {
        var value = fields.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || !value.getAsString().matches("0|[1-9][0-9]{0,19}")) throw new IOException("missing canonical boot milliseconds: " + key);
        BigInteger number = new BigInteger(value.getAsString());
        if (number.bitLength() > 64) throw new IOException("boot milliseconds exceed u64");
        return number;
    }
    private static String text(JsonObject fields, String key) {
        var value = fields.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : "";
    }
    private static InstallerLogs.DaemonEvent first(List<InstallerLogs.DaemonEvent> rows, String message, String agent, String signal) {
        return rows.stream().filter(row -> message.equals(row.message())
            && (agent == null || agent.equals(text(row.fields(), "agent")))
            && (signal == null || signal.equals(text(row.fields(), "signal"))))
            .min(Comparator.comparing(InstallerLogs.DaemonEvent::sequence)).orElse(null);
    }
    public static JsonObject measure(InstallerLogs.Snapshot logs) {
        JsonObject result = new JsonObject(); result.addProperty("bootId", logs.boot()); result.addProperty("writerId", logs.writerId());
        result.addProperty("status", "UNVERIFIED"); result.addProperty("functionalAcceptance", false);
        result.addProperty("observationType", "startup passive feedback readiness");
        result.addProperty("earlyRestoreApplicationStatus", "UNVERIFIED: requires prior explicit OEM choice, matching expected feedback and causal command evidence");
        result.addProperty("meaning", "First exported passive native feedback observation; no ECU apply acknowledgement, pre-OEM ordering or 3-5 second threshold acceptance");
        try {
            var startupRows = logs.daemonEvents().stream().filter(row -> "storage endpoint available".equals(row.message())).toList();
            if (startupRows.size() != 1) throw new IOException("unique current-writer bootstrap timing row is missing");
            var startup = startupRows.get(0); BigInteger started = millis(startup.fields(), "processStartedBootMs"), ready = millis(startup.fields(), "endpointReadyBootMs");
            if (ready.compareTo(started) < 0) throw new IOException("startup boot clock regressed");
            result.addProperty("processStartedBootMs", started.toString()); result.addProperty("endpointReadyBootMs", ready.toString());
            result.addProperty("endpointDelayMs", ready.subtract(started).toString());
            for (String agent : List.of("vehicle-drive-forced-ev", "vehicle-drive-pedestrian-warning")) {
                String signal = agent.endsWith("forced-ev") ? "IVI_SOC_MODESET" : "HUM_VSP_FUNCTION_SW";
                var sample = first(logs.daemonEvents(), "native CAN sample received", agent, signal);
                if (sample == null) throw new IOException("current native feedback is missing: " + agent + "/" + signal);
                JsonObject observed = timing(logs, startup, sample, started, ready);
                String value = text(sample.fields(), "value");
                if (!value.matches("-?(0|[1-9][0-9]{0,9})")) throw new IOException("native feedback value is not canonical i32");
                try { Integer.parseInt(value); } catch (NumberFormatException invalid) { throw new IOException("native feedback value exceeds i32", invalid); }
                observed.addProperty("signal", signal); observed.addProperty("feedbackValue", value);
                var progress = first(logs.daemonEvents(), "native controller state changed", agent, null);
                if (progress != null) { observed.add("firstControllerProgress", timing(logs, startup, progress, started, ready)); observed.addProperty("controllerResult", text(progress.fields(), "result")); }
                result.add(agent, observed);
            }
            var receiver = first(logs.daemonEvents(), "native CAN receiver available", null, null);
            if (receiver != null) result.add("firstReceiverAvailable", timing(logs, startup, receiver, started, ready));
            else result.addProperty("receiverAvailability", "UNVERIFIED: receiver availability timing row missing");
            result.addProperty("status", "MEASURED_ACCEPTANCE_UNVERIFIED");
        } catch (IOException missing) { result.addProperty("reason", missing.getMessage()); }
        return result;
    }
    private static JsonObject timing(InstallerLogs.Snapshot logs, InstallerLogs.DaemonEvent startup, InstallerLogs.DaemonEvent event,
            BigInteger started, BigInteger ready) throws IOException {
        BigInteger observed = millis(event.fields(), "observedBootMs");
        if (event.sequence().compareTo(startup.sequence()) <= 0 || observed.compareTo(ready) < 0) throw new IOException("native observation precedes current bootstrap");
        BigInteger span = event.sequence().subtract(startup.sequence());
        long exported = logs.exportedSequences().stream().filter(seq -> seq.compareTo(startup.sequence()) > 0 && seq.compareTo(event.sequence()) <= 0).count();
        if (!span.equals(BigInteger.valueOf(exported))) throw new IOException("current export has gaps after bootstrap; first feedback is unverified");
        JsonObject result = new JsonObject(); result.addProperty("sequence", event.sequence().toString()); result.addProperty("observedBootMs", observed.toString());
        result.addProperty("afterProcessStartMs", observed.subtract(started).toString()); result.addProperty("afterEndpointReadyMs", observed.subtract(ready).toString()); return result;
    }
}
