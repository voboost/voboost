package ru.voboost.tests.logging;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import ru.voboost.tests.HostProcess;
import ru.voboost.tests.RunFiles;

/** Product-owned compressed logs with strict identities and bounded streaming record validation. */
public final class InstallerLogs {
    private static final long LIMIT = 400L * 1024 * 1024;
    // Up to 48 MiB of system text plus JSON escaping and bounded owner metadata.
    private static final int CONTEXT_LIMIT = 320 * 1024 * 1024;
    public record Diagnostic(BigInteger sequence, String agent, String event, String outcome, JsonObject fields, JsonObject sourceFields) {}
    public record DaemonEvent(BigInteger sequence, String component, String message, JsonObject fields) {}
    public record RootEffect(BigInteger sequence, JsonObject fields) {
        public RootEffect { fields = fields.deepCopy(); }
        @Override public JsonObject fields() { return fields.deepCopy(); }
    }
    public record Snapshot(String boot, String writerId, BigInteger watermark, BigInteger sequence, Set<BigInteger> exportedSequences, Instant collectedAt, List<Diagnostic> diagnostics, List<RootEffect> effects, Path archive, List<DaemonEvent> daemonEvents) {
        public Snapshot { exportedSequences = Set.copyOf(exportedSequences); diagnostics = List.copyOf(diagnostics); effects = List.copyOf(effects); daemonEvents = List.copyOf(daemonEvents); }
        public Snapshot(String boot, String writerId, BigInteger watermark, BigInteger sequence, Set<BigInteger> exportedSequences, Instant collectedAt, List<Diagnostic> diagnostics, List<RootEffect> effects, Path archive) {
            this(boot, writerId, watermark, sequence, exportedSequences, collectedAt, diagnostics, effects, archive, List.of());
        }

        private void requireSameWriter(Snapshot prior) throws IOException {
            if (!boot.equals(prior.boot) || !writerId.equals(prior.writerId) || watermark.compareTo(prior.watermark) < 0 || sequence.compareTo(prior.sequence) < 0 || collectedAt.isBefore(prior.collectedAt))
                throw new IOException("log observation crossed writer/boot or regressed");
        }
        /** Tailing cursor includes delayed exports but does not establish causal effect freshness. */
        public List<Diagnostic> newlyExportedAfter(Snapshot prior) throws IOException {
            requireSameWriter(prior);
            return diagnostics.stream().filter(row -> row.sequence.compareTo(prior.sequence) > 0).toList();
        }
        private void requireCausalCoverage(Snapshot prior) throws IOException {
            requireSameWriter(prior);
            BigInteger span = sequence.subtract(prior.watermark);
            if (span.signum() > 0 && !span.equals(BigInteger.valueOf(exportedSequences.stream().filter(id -> id.compareTo(prior.watermark) > 0 && id.compareTo(sequence) <= 0).count())))
                throw new IOException("current writer export has missing rows in the causal observation interval");
        }
        public List<Diagnostic> after(Snapshot prior) throws IOException {
            requireCausalCoverage(prior);
            return diagnostics.stream().filter(row -> row.sequence.compareTo(prior.watermark) > 0).toList();
        }
        /** Negative assertions need the complete tail through the authenticated live writer watermark. */
        public void requireCompleteExportAfter(Snapshot prior) throws IOException {
            requireCausalCoverage(prior);
            if (!sequence.equals(watermark)) throw new IOException("negative observation lacks complete export through live writer watermark");
        }
        public List<RootEffect> effectsAfter(Snapshot prior) throws IOException {
            requireCausalCoverage(prior);
            return effects.stream().filter(row -> row.sequence.compareTo(prior.watermark) > 0).toList();
        }
    }
    /** Authenticated current owner identity; the post-export watermark is not a records cursor. */
    public record CurrentSession(String boot,String writerId,BigInteger watermark) {
        public CurrentSession {
            if(boot==null||!boot.matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}")||writerId==null||!writerId.matches("[0-9a-f]{32}")||watermark==null||watermark.signum()<0||watermark.bitLength()>64)throw new IllegalArgumentException("exact current log session scope required");
        }
    }
    public static CurrentSession currentSession(JsonObject context,String boot)throws IOException {
        try {
            if (context.has("observedLogSession"))
                throw new IOException("ADB log observation lacks an authenticated owner watermark; causal and negative checks are inconclusive");
            if(!context.has("currentLogSession")||!context.get("currentLogSession").isJsonObject())throw new IOException("authenticated current owner log session metadata missing");
            JsonObject session=context.getAsJsonObject("currentLogSession");String availability=text(session,"availability");
            if("unavailable".equals(availability)) {
                if(!session.keySet().equals(Set.of("availability","reason"))||!"owner-unavailable-or-changed".equals(text(session,"reason")))throw new IOException("malformed unavailable current owner log session");
                throw new IOException("current owner log session is unavailable or changed; historical writers cannot substitute");
            }
            if(!session.keySet().equals(Set.of("availability","bootId","writerId","sequence"))||!"available".equals(availability)||!string(session,"bootId")||!string(session,"writerId")||!boot.equals(text(session,"bootId")))throw new IOException("current owner log session scope differs");
            return new CurrentSession(boot,text(session,"writerId"),unsigned(session,"sequence",true));
        }catch(RuntimeException malformed){throw new IOException("malformed current owner log session",malformed);}
    }
    private static BigInteger unsigned(JsonObject object,String key,boolean zero)throws IOException {
        String value=text(object,key);if(!numeric(object,key)||!value.matches("0|[1-9][0-9]{0,19}"))throw new IOException("exact canonical u64 log field required: "+key);
        BigInteger number=new BigInteger(value);if(number.bitLength()>64||!zero&&number.signum()==0)throw new IOException("u64 log field out of range: "+key);return number;
    }
    private InstallerLogs() {}
    public static final class LocalePending extends IOException {
        public LocalePending(String reason) { super(reason); }
    }

    public static Snapshot read(Path archive, Path parent, String serial, String version, String manifest, String boot,
            Instant started, Instant completed) throws Exception {
        if (!Files.isRegularFile(archive, LinkOption.NOFOLLOW_LINKS) || Files.size(archive) < 1 || Files.size(archive) > LIMIT + 128 * 1024
                || completed.isBefore(started)) throw new IOException("bounded current log archive required");
        Path work = RunFiles.directory(parent.resolve("log-read-" + UUID.randomUUID()));
        HostProcess.Result names = HostProcess.run(work.resolve("members.log"), List.of("/usr/bin/tar", "-tf", archive.toString()), 180000);
        if (names.exit() != 0 || !names.output().equals("manifest.json\nrecords.jsonl\ncontext.json\n"))
            throw new IOException("exact product log archive members required; " + work);
        Path metadata = member(archive, work, "manifest.json", 65536), context = member(archive, work, "context.json", CONTEXT_LIMIT);
        JsonObject value = RunFiles.read(metadata), device = RunFiles.read(context, CONTEXT_LIMIT);
        JsonObject envelope = value.getAsJsonObject("hostEnvelope"), header = value.getAsJsonObject("header");
        if (!"1".equals(text(value, "schemaVersion")) || !"VBOOSTLOG1".equals(text(value, "format"))
                || !value.getAsJsonArray("files").toString().equals("[\"records.jsonl\",\"context.json\"]")
                || !"1".equals(text(header, "schemaVersion")) || !serial.equals(text(envelope, "deviceSerial"))
                || !version.equals(text(envelope, "installerVersion")) || !completeProductLogs(envelope, device)
                || !"1".equals(text(device, "schemaVersion")) || !manifest.equals(text(device, "manifestSha256"))
                || !version.equals(text(device, "releaseIdentity")) || !"root-owned-segments".equals(text(device, "logSource"))
                || !Long.toString(Files.size(context)).equals(text(header, "contextLength")))
            throw new IOException("log product identity or payload partition differs; " + work);
        Instant collected = Instant.parse(text(envelope, "collectedAtUtc"));
        if (collected.isBefore(started) || collected.isAfter(completed)) throw new IOException("log archive was not collected during this command");
        CurrentSession session=currentSession(device,boot);
        Path records = member(archive, work, "records.jsonl", LIMIT);
        try {
            if (!Long.toString(Files.size(records)).equals(text(header, "recordsLength"))) throw new IOException("log record partition differs");
            return records(records, session, collected, archive);
        } finally { Files.deleteIfExists(records); }
    }
    private static boolean completeProductLogs(JsonObject envelope, JsonObject device) {
        if (!string(envelope, "completeness")) return false;
        JsonObject bundle = device.has("diagnosticBundle") && device.get("diagnosticBundle").isJsonObject()
                ? device.getAsJsonObject("diagnosticBundle") : new JsonObject();
        String bundleStatus = text(bundle, "completeness");
        if ("partial".equals(bundleStatus) || "failed".equals(bundleStatus)) return false;
        String status = text(envelope, "completeness");
        return "verified-stream".equals(status)
                || ("complete".equals(status) && string(bundle, "completeness") && "complete".equals(bundleStatus));
    }

    private static Path member(Path archive, Path work, String name, long limit) throws Exception {
        Path output = work.resolve(name), error = work.resolve(name + ".stderr");
        if (HostProcess.produceFile(work, output, error, List.of("/usr/bin/tar", "-xOf", archive.toString(), name), 180000, limit) != 0)
            throw new IOException("product log member could not be decoded; " + error);
        return output;
    }
    /** Historical rows are read-only history. Only schema2 rows in the authenticated current scope provide effects. */
    public static Snapshot records(Path file, CurrentSession session, Instant collected, Path archive) throws IOException {
        BigInteger last=BigInteger.ZERO;List<Diagnostic> diagnostics=new ArrayList<>();List<RootEffect> effects=new ArrayList<>();
        List<DaemonEvent> daemonEvents=new ArrayList<>();
        java.util.Map<String,java.util.Set<BigInteger>> identifiers=new java.util.HashMap<>();int count=0;String boot=session.boot();
        try(BufferedReader reader=Files.newBufferedReader(file,StandardCharsets.UTF_8)) {
            String line;
            while((line=boundedLine(reader))!=null) {
                if(line.isEmpty()||++count>1_000_000)throw new IOException("empty or excessive canonical log records");
                JsonObject row=RunFiles.parse(line);String schema=text(row,"schemaVersion");boolean legacy=schema.equals("1");
                Set<String> keys=legacy?Set.of("schemaVersion","sequence","timestamp","source","level","component","message","fields"):Set.of("schemaVersion","bootId","writerId","sequence","timestamp","source","level","component","message","fields");
                if(!row.keySet().equals(keys)||!numeric(row,"schemaVersion")||!Set.of("1","2").contains(schema)||!Set.of("debug","info","error").contains(text(row,"level"))||!string(row,"level")||!string(row,"component")||!string(row,"message")||!string(row,"timestamp")||text(row,"component").isBlank()||!row.get("fields").isJsonObject()||!row.get("source").isJsonObject()||text(row.getAsJsonObject("source"),"kind").isBlank())throw new IOException("canonical log record shape differs");
                Instant.parse(text(row,"timestamp"));BigInteger sequence=unsigned(row,"sequence",legacy);
                if(legacy)continue; // Preserved schema1 history has no authenticated current writer identity.
                if(!string(row,"bootId")||!text(row,"bootId").matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}")||!string(row,"writerId")||!text(row,"writerId").matches("[0-9a-f]{32}"))throw new IOException("schema2 log writer/boot identity differs");
                String scope=text(row,"bootId")+":"+text(row,"writerId");if(!identifiers.containsKey(scope)&&identifiers.size()>=4096)throw new IOException("log scope bound exceeded");
                if(!identifiers.computeIfAbsent(scope,key->new java.util.HashSet<>()).add(sequence))throw new IOException("duplicate canonical log identity tuple");
                if(!boot.equals(text(row,"bootId"))||!session.writerId().equals(text(row,"writerId")))continue;
                if(sequence.compareTo(session.watermark())>0)throw new IOException("current exported row exceeds authenticated live writer watermark");
                last=last.max(sequence);
                String message = text(row, "message");
                if (message.equals("fault-effect")) {
                    JsonObject source = row.getAsJsonObject("source"), fields = row.getAsJsonObject("fields");
                    if (!source.keySet().equals(Set.of("kind")) || !"daemon".equals(text(source, "kind"))
                            || !"info".equals(text(row, "level")) || !"emulator-diagnostic".equals(text(row, "component"))
                            || !fields.keySet().equals(Set.of("token", "kind", "effect", "source", "requestId", "bootId", "manifestSha256", "generationId", "catalogSha256", "targetPid", "targetStarttime", "process"))
                            || !boot.equals(text(fields, "bootId"))) throw new IOException("canonical root fault effect authority differs");
                    for (String field : fields.keySet())
                        if (!string(fields, field) || text(fields, field).isEmpty() || text(fields, field).length() > 1024)
                            throw new IOException("canonical root fault effect fields differ");
                    if (effects.size() >= 100000) throw new IOException("root diagnostic effect bound exceeded");
                    effects.add(new RootEffect(sequence, fields));
                    continue;
                }
                if (message.equals("vehicle-state-observed")) {
                    JsonObject source=row.getAsJsonObject("source"),fields=row.getAsJsonObject("fields");
                    if(!source.keySet().equals(Set.of("kind"))||!"daemon".equals(text(source,"kind"))||!"info".equals(text(row,"level"))||!"agent-state".equals(text(row,"component")))throw new IOException("canonical root state operation origin differs");
                    ru.voboost.tests.agents.RootVehicleState.read(fields);
                    if(effects.size()>=100000)throw new IOException("root operation record bound exceeded");
                    effects.add(new RootEffect(sequence,fields));continue;
                }
                if(message.equals("desired-snapshot-queued")){
                    JsonObject source=row.getAsJsonObject("source"),fields=row.getAsJsonObject("fields");
                    if(!source.keySet().equals(Set.of("kind"))||!"daemon".equals(text(source,"kind"))||!"info".equals(text(row,"level"))||!"runtime".equals(text(row,"component")))throw new IOException("queued layout snapshot origin differs");
                    Set<String> required=Set.of("process","generationId","catalogSha256","documentGeneration","snapshotSha256","frames");
                    Set<String> allowed=new java.util.HashSet<>(required);allowed.addAll(Set.of("sourceLanguage","targetPid","targetStarttime","endpointEpoch","profileLanguage","effectiveLanguage","requestId"));
                    if(!fields.keySet().containsAll(required)||!allowed.containsAll(fields.keySet()))throw new IOException("queued layout snapshot fields differ");
                    for(String key:fields.keySet())if(!string(fields,key)||text(fields,key).isBlank())throw new IOException("queued layout snapshot text differs");
                    for(String key:Set.of("generationId","catalogSha256","snapshotSha256"))if(!text(fields,key).matches("[0-9a-f]{64}"))throw new IOException("queued layout snapshot identity differs");
                    if(!text(fields,"process").matches("[A-Za-z][A-Za-z0-9_.:-]{0,254}")||!text(fields,"documentGeneration").matches("0|[1-9][0-9]{0,19}")||new BigInteger(text(fields,"documentGeneration")).bitLength()>64||!Set.of("D","D+A").contains(text(fields,"frames")))throw new IOException("queued layout snapshot typed identity differs");
                    int targetFields=0;for(String key:Set.of("targetPid","targetStarttime","endpointEpoch"))if(fields.has(key)){targetFields++;if(!text(fields,key).matches("[1-9][0-9]{0,19}")||new BigInteger(text(fields,key)).bitLength()>64)throw new IOException("queued layout snapshot target differs");}
                    if(targetFields!=0&&targetFields!=3||fields.has("profileLanguage")!=fields.has("effectiveLanguage"))throw new IOException("partial queued layout target or language");
                    for(String key:Set.of("sourceLanguage","profileLanguage","effectiveLanguage"))if(fields.has(key)&&!Set.of("en","ru","zh").contains(text(fields,key)))throw new IOException("queued layout language differs");
                    if("D+A".equals(text(fields,"frames"))!=fields.has("requestId")||fields.has("requestId")&&(!text(fields,"requestId").matches("0|[1-9][0-9]{0,19}")||new BigInteger(text(fields,"requestId")).bitLength()>64))throw new IOException("queued layout action identity differs");
                    if(daemonEvents.size()>=100000)throw new IOException("queued layout snapshot bound exceeded");
                    daemonEvents.add(new DaemonEvent(sequence,"runtime",message,fields.deepCopy()));
                }
                if(message.equals("initial-state-capture-sealed")) {
                    JsonObject source=row.getAsJsonObject("source"),fields=row.getAsJsonObject("fields");
                    if(!source.keySet().equals(Set.of("kind"))||!"daemon".equals(text(source,"kind"))||!"initial-seeding".equals(text(row,"component"))||!"info".equals(text(row,"level"))||!fields.keySet().equals(Set.of("transactionId","candidate","lastSequence")))throw new IOException("initial seeding seal origin differs");
                    for(String key:fields.keySet())if(!string(fields,key))throw new IOException("initial seeding seal typed fields differ");
                    if(!text(fields,"transactionId").matches("[0-9a-f]{64}")||!text(fields,"candidate").matches("[0-9a-f]{64}")||!text(fields,"lastSequence").matches("[1-9][0-9]{0,19}"))throw new IOException("initial seeding seal identity differs");
                    BigInteger floor=new BigInteger(text(fields,"lastSequence"));if(floor.bitLength()>64||floor.compareTo(sequence)>=0)throw new IOException("initial seeding seal precedes no completed state operation");
                    if(daemonEvents.size()>=100000)throw new IOException("initial seeding seal event bound exceeded");daemonEvents.add(new DaemonEvent(sequence,"initial-seeding",message,fields.deepCopy()));
                }
                if(message.equals("native command phase changed")) {
                    JsonObject source=row.getAsJsonObject("source"),fields=row.getAsJsonObject("fields");Set<String> required=Set.of("agent","commandId","state","value","phase");Set<String> allowed=new java.util.HashSet<>(required);allowed.add("observedBootMs");
                    if(!source.keySet().equals(Set.of("kind"))||!"daemon".equals(text(source,"kind"))||!"native".equals(text(row,"component"))||!"debug".equals(text(row,"level"))||!fields.keySet().containsAll(required)||!allowed.containsAll(fields.keySet()))throw new IOException("native command phase authority differs");
                    for(String key:fields.keySet())if(!string(fields,key))throw new IOException("native command phase typed field differs");
                    String agent=text(fields,"agent"),state=text(fields,"state"),value=text(fields,"value");
                    boolean command=agent.equals("vehicle-drive-forced-ev")&&state.equals("IVI_SOC_MODESET")&&Set.of("2","5").contains(value)||agent.equals("vehicle-drive-pedestrian-warning")&&state.equals("HUM_VSP_FUNCTION_SW")&&Set.of("1","2").contains(value);
                    if(!command||!text(fields,"commandId").matches("[1-9][0-9]{0,18}")||new BigInteger(text(fields,"commandId")).bitLength()>63||!Set.of("offered","execute","accepted","cancelled","failed","unknown").contains(text(fields,"phase")))throw new IOException("native command phase closed identity differs");
                    if(fields.has("observedBootMs")&&(!text(fields,"observedBootMs").matches("0|[1-9][0-9]{0,19}")||new BigInteger(text(fields,"observedBootMs")).bitLength()>64))throw new IOException("native command phase clock differs");
                    if(daemonEvents.size()>=100000)throw new IOException("native command phase bound exceeded");daemonEvents.add(new DaemonEvent(sequence,"native",message,fields.deepCopy()));
                }
                if (Set.of("storage endpoint available", "native CAN receiver available", "native CAN sample received", "native controller state changed").contains(message)) {
                    JsonObject source = row.getAsJsonObject("source");
                    if (!source.keySet().equals(Set.of("kind")) || !"daemon".equals(text(source,"kind"))
                            || !(message.equals("storage endpoint available") ? "owner" : "native").equals(text(row,"component")))
                        throw new IOException("startup/native timing origin differs");
                    if (daemonEvents.size() >= 100000) throw new IOException("startup/native timing record bound exceeded");
                    daemonEvents.add(new DaemonEvent(sequence, text(row,"component"), message, row.getAsJsonObject("fields").deepCopy()));
                }
                if (!message.startsWith("VOBDBG1 ")) continue;

                JsonObject unownedSource=row.getAsJsonObject("source"),unownedFields=row.getAsJsonObject("fields");
                if(unownedSource.keySet().equals(Set.of("kind"))&&"daemon".equals(text(unownedSource,"kind"))&&"implant".equals(text(row,"component"))
                        &&unownedFields.keySet().equals(Set.of("declaredSource","producerTimestampMs","producerClock"))
                        &&string(unownedFields,"declaredSource")&&!text(unownedFields,"declaredSource").isBlank()&&text(unownedFields,"declaredSource").length()<=256
                        &&string(unownedFields,"producerClock")&&"unix".equals(text(unownedFields,"producerClock"))
                        &&string(unownedFields,"producerTimestampMs")&&text(unownedFields,"producerTimestampMs").matches("0|[1-9][0-9]{0,19}")
                        &&new BigInteger(text(unownedFields,"producerTimestampMs")).bitLength()<=64) {
                    // Retained in the raw canonical archive; declaredSource never becomes agent authority.
                    continue;
                }
                JsonObject body = RunFiles.parse(message.substring(8)), source = row.getAsJsonObject("source");
                if (body.has("fields") && !body.get("fields").isJsonObject())
                    throw new IOException("agent diagnostic fields must be an object when present");
                JsonObject bodyFields = body.has("fields") ? body.getAsJsonObject("fields") : new JsonObject();
                if (!"agent".equals(text(source, "kind")) || !text(source, "id").equals(text(body, "agent"))
                        || text(body, "event").isBlank() || text(body, "outcome").isBlank())
                    throw new IOException("agent diagnostic identity differs");
                if (diagnostics.size() >= 100000) throw new IOException("diagnostic record bound exceeded");
                diagnostics.add(new Diagnostic(sequence, text(body, "agent"), text(body, "event"), text(body, "outcome"), bodyFields, row.getAsJsonObject("fields")));
            }
        }catch(RuntimeException malformed){throw new IOException("malformed canonical product logs",malformed);}
        diagnostics.sort(java.util.Comparator.comparing(Diagnostic::sequence));effects.sort(java.util.Comparator.comparing(RootEffect::sequence));
        return new Snapshot(boot,session.writerId(),session.watermark(),last,identifiers.getOrDefault(boot+":"+session.writerId(),Set.of()),collected,diagnostics,effects,archive,daemonEvents);
    }
    public static Diagnostic renderLocale(Snapshot prior, Snapshot current, String locale) throws IOException {
        Diagnostic selected = null;
        for (Diagnostic row : current.after(prior))
            if (row.agent.equals("ui-qinggan") && Set.of("ui.compose", "ui.render").contains(row.event)
                    && "com.qinggan.app.setting".equals(text(row.sourceFields, "process"))
                    && current.boot.equals(text(row.sourceFields, "bootId"))) selected = row;
        if (selected == null) throw new LocalePending("fresh Setting locale render not received");
        if (!selected.outcome.equals("complete")
                || !text(selected.sourceFields, "targetPid").matches("[1-9][0-9]{0,9}")
                || !text(selected.sourceFields, "targetStarttime").matches("[1-9][0-9]{0,19}")
                || !text(selected.sourceFields, "endpointEpoch").matches("[1-9][0-9]{0,19}")
                || !selected.fields.keySet().equals(Set.of("defaultLocale", "effectiveLanguage", "resourceLocale", "elapsed_ms"))
                || !numeric(selected.fields, "elapsed_ms") || !text(selected.fields, "elapsed_ms").matches("[0-9]{1,18}"))
            throw new IOException("Setting locale render fields or endpoint proof malformed");
        if (!locale.equals(text(selected.fields, "effectiveLanguage"))
                || !text(selected.fields, "defaultLocale").matches(locale + "(?:[-_].*)?")
                || !text(selected.fields, "resourceLocale").matches(locale + "(?:[-_].*)?"))
            throw new LocalePending("Setting process/resource render has not reached selected locale");
        return selected;
    }
    private static String boundedLine(BufferedReader reader) throws IOException {
        StringBuilder line = new StringBuilder(); int value;
        while ((value = reader.read()) != -1) {
            if (value == '\n') {
                String result = line.toString();
                if (result.getBytes(StandardCharsets.UTF_8).length > 256 * 1024) throw new IOException("oversized canonical JSONL bytes");
                return result;
            }
            if (value == '\r' || line.length() >= 256 * 1024) throw new IOException("oversized or noncanonical JSONL line");
            line.append((char) value);
        }
        if (line.length() != 0) throw new IOException("unterminated canonical JSONL record");
        return null;
    }
    private static boolean numeric(JsonObject object, String key) {
        JsonElement value = object.get(key); return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber();
    }
    private static boolean string(JsonObject object, String key) {
        JsonElement value = object.get(key); return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
    }
    private static String text(JsonObject object, String key) {
        JsonElement value = object.get(key); return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }
}
