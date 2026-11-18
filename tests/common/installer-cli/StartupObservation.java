package ru.voboost.tests.installer.cli;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.Set;

/** Persisted startup input and a fresh fixed observer's process-absence result. */
public record StartupObservation(String mode, String revision) {
    public static StartupObservation read(JsonObject observation, String boot) throws IOException {
        try {
            JsonObject data = observation.getAsJsonObject("data"), config = data.getAsJsonObject("files").getAsJsonObject("config");
            if (!observation.get("schema").isJsonPrimitive() || !observation.getAsJsonPrimitive("schema").isNumber()
                    || !"2".equals(observation.get("schema").getAsString()) || !"voboost".equals(text(observation, "source"))
                    || !"ready".equals(text(observation, "status")) || !boot.equals(text(observation, "boot_id"))
                    || !"present".equals(text(data, "root")) || !"fixed-path-owner-mode-boot-fenced".equals(text(data, "root_proof"))
                    || !"present".equals(text(config, "status")) || !config.has("current") || !config.get("current").isJsonPrimitive() || !config.getAsJsonPrimitive("current").isBoolean() || !config.get("current").getAsBoolean()
                    || !"same-boot-retained-file".equals(text(config, "proof")) || text(config, "revision").isBlank())
                throw new IOException("current same-boot persisted startup input unavailable");
            String mode = text(config.getAsJsonObject("data"), "startup");
            if (!Set.of("interface", "hidden", "none").contains(mode)) throw new IOException("persisted startup mode unavailable");
            return new StartupObservation(mode, text(config, "revision"));
        } catch (RuntimeException malformed) { throw new IOException("malformed persisted startup observation", malformed); }
    }
    public static void requireNoProcesses(String body, String boot) throws IOException {
        for (String name : runningProcesses(body, boot)) {
            String basename = name.substring(name.lastIndexOf('/') + 1);
            if (basename.startsWith("voboost") || basename.startsWith("ru.voboost") || name.startsWith("/data/voboost/"))
                throw new IOException("none mode has a resident Voboost native or APK process");
        }
    }
    /** Only the exact installed resident owner is permitted; all functional Voboost processes remain absent. */
    public static String requireNoneProcesses(String body,String boot)throws IOException {
        runningProcesses(body,boot); // Full inventory validation is mandatory even when the owner is identified.
        var current=processes(body,boot,false);String owner=current.get("voboost-daemon");
        if(owner==null||!owner.endsWith(":/data/voboost/voboost-daemon")||current.containsKey("voboost-inject"))throw new IOException("none lacks exact installed owner or still has inject");
        String[] fields=owner.split(":",3);
        var proof=java.util.regex.Pattern.compile("(?m)^owner-executable pid="+fields[0]+" starttime="+fields[1]+" path=/data/voboost/voboost-daemon backing=([0-9]{1,20}:[1-9][0-9]{0,19}:0:0:700:1)$").matcher(body.substring(body.indexOf("current-process-identities:\n"),body.indexOf("current-process-identities-end=complete\n")));
        if(!proof.find())throw new IOException("none owner exact executable backing unavailable");String identity=owner+":"+proof.group(1);if(proof.find())throw new IOException("ambiguous owner executable backing");
        int start=body.indexOf("\noem-target-processes:\n"),end=body.indexOf("inventoryEnd=complete\n");
        var rows=java.util.regex.Pattern.compile("(?m)^\\s*([1-9][0-9]{0,9})\\s+([!-~]{1,4096})\\s*$").matcher(body.substring(start,end));boolean found=false;
        while(rows.find()) {
            String name=rows.group(2),base=name.substring(name.lastIndexOf('/')+1);
            if(base.startsWith("voboost")||base.startsWith("ru.voboost")||name.startsWith("/data/voboost/")) {
                if(!rows.group(1).equals(fields[0])||!Set.of("voboost-daemon","/data/voboost/voboost-daemon").contains(name)||found)throw new IOException("none has an unbound Voboost process");found=true;
            }
        }
        if(!found)throw new IOException("none owner missing from full process inventory");return identity;
    }
    public static Set<String> runningProcesses(String body, String boot) throws IOException {
        String frame = "bootCompleted=1\nbootId=" + boot + "\nenforcing=Enforcing\n";
        String inventoryStart = "\noem-target-processes:\n", inventoryEnd = "inventoryEnd=complete\n";
        int identity = body.indexOf(frame), start = body.indexOf(inventoryStart), end = body.indexOf(inventoryEnd);
        if (body.length() > 4 * 1024 * 1024 || body.indexOf('\0') >= 0 || body.indexOf('\r') >= 0
                || identity < 0 || body.lastIndexOf(frame) != identity || start < identity + frame.length() - 1 || end <= start
                || body.lastIndexOf(inventoryStart) != start || body.lastIndexOf(inventoryEnd) != end)
            throw new IOException("fresh complete process observation framing unavailable");
        String inventory = body.substring(start + inventoryStart.length(), end);
        if (inventory.length() > 131072 + 64 || !inventory.startsWith("inventoryStatus=complete\n"))
            throw new IOException("official full process inventory is unavailable or oversized");
        String[] processes = inventory.substring("inventoryStatus=complete\n".length()).strip().split("\n", -1);
        if (processes.length < 2 || !processes[0].strip().matches("PID\\s+NAME"))
            throw new IOException("official process inventory is missing its header or all processes");
        Set<Long> pids = new java.util.HashSet<>(); Set<String> names = new java.util.HashSet<>();
        var row = java.util.regex.Pattern.compile("\\s*([1-9][0-9]{0,9})\\s+([!-~]{1,4096})\\s*");
        for (int index = 1; index < processes.length; ++index) {
            var match = row.matcher(processes[index]);
            if (!match.matches()) throw new IOException("official process inventory row is malformed");
            long pid = Long.parseLong(match.group(1));
            if (pid > Integer.MAX_VALUE || !pids.add(pid)) throw new IOException("official process PID is invalid or duplicated");
            names.add(match.group(2));
        }
        return Set.copyOf(names);
    }
    public static void requireSameProcesses(String before, String after, String boot) throws IOException {
        var prior = processes(before, boot); var current = processes(after, boot);
        for (var entry : prior.entrySet()) if (!entry.getValue().equals(current.get(entry.getKey())))
            throw new IOException("startup selection restarted or stopped an observed process: " + entry.getKey());
    }
    public record ProcessIdentity(int pid, String starttime, String argv0) {}
    public static ProcessIdentity identity(String body, String boot, String process) throws IOException {
        String value = processes(body,boot,false).get(process);
        if (value == null) throw new IOException("current observed process identity unavailable: " + process);
        String[] fields = value.split(":", 3);
        if (fields.length != 3) throw new IOException("current observed process identity malformed");
        return new ProcessIdentity(Integer.parseInt(fields[0]), fields[1], fields[2]);
    }
    private static java.util.Map<String, String> processes(String body, String boot) throws IOException {
        return processes(body,boot,true);
    }
    private static java.util.Map<String,String> processes(String body,String boot,boolean requireLive) throws IOException {
        String start = "current-process-identities:\n", end = "current-process-identities-end=complete\n";
        int first = body.indexOf(start), last = body.indexOf(end);
        if (first < 0 || last <= first || body.lastIndexOf(start) != first || body.lastIndexOf(end) != last)
            throw new IOException("complete current process identity section unavailable");
        String section = body.substring(first + start.length(), last);
        if (!section.startsWith("schemaVersion=1\nbootId=" + boot + "\nenforcing=Enforcing\n")
                || !section.endsWith("bootIdAfter=" + boot + "\nenforcingAfter=Enforcing\nbootCompletedAfter=1\n"))
            throw new IOException("current process identity boot fence differs");
        java.util.Map<String, String> result = new java.util.HashMap<>();
        var row = java.util.regex.Pattern.compile("process=([A-Za-z0-9_.:-]+) pid=([1-9][0-9]{0,9}) starttime=([1-9][0-9]{0,19}) argv0=([A-Za-z0-9_./:-]+)");
        for (String line : section.split("\n")) {
            if (!line.startsWith("process=")) continue;
            var match = row.matcher(line);
            if (!match.matches()) {
                if (line.matches("process=[A-Za-z0-9_.:-]+ unavailable=missing-or-invalid")) continue;
                throw new IOException("current process identity row malformed");
            }
            if (Long.parseLong(match.group(2)) > Integer.MAX_VALUE || new java.math.BigInteger(match.group(3)).bitLength() > 64)
                throw new IOException("current process PID/starttime exceeds native authority bounds");
            if (result.putIfAbsent(match.group(1), match.group(2) + ":" + match.group(3) + ":" + match.group(4)) != null)
                throw new IOException("duplicate current process identity");
        }
        if (requireLive && !result.keySet().containsAll(Set.of("voboost-inject", "com.qinggan.app.setting")))
            throw new IOException("live injection producer and Settings process identities unavailable");
        return result;
    }
    private static String text(JsonObject value, String key) {
        var field = value.get(key);
        return field != null && field.isJsonPrimitive() && field.getAsJsonPrimitive().isString() ? field.getAsString() : "";
    }
}
