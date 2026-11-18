package ru.voboost.tests.agents;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Set;
import ru.voboost.tests.installer.cli.FaultWindow;
import ru.voboost.tests.installer.cli.StartupObservation;
import ru.voboost.tests.logging.InstallerLogs;

/** Rechecks authenticated owner receipts. The caller must independently bind the input endpoint and actual root readback. */
public final class NativeStateOutageAdmission {
    public record Scope(String kind, String token, String boot, String generation, String catalog,
            String ownerWriter, int ownerPid, long ownerStart, int targetPid, long targetStart,
            long endpointEpoch, long deadline) {}
    public record Observations(JsonObject initialTarget, JsonObject terminalTarget,
            InstallerLogs.CurrentSession initialWriter, InstallerLogs.CurrentSession terminalWriter,
            StartupObservation.ProcessIdentity initialOwner, StartupObservation.ProcessIdentity terminalOwner,
            StartupObservation.ProcessIdentity initialInput, StartupObservation.ProcessIdentity terminalInput) {
        public Observations {
            initialTarget = initialTarget == null ? null : initialTarget.deepCopy();
            terminalTarget = terminalTarget == null ? null : terminalTarget.deepCopy();
        }
        @Override public JsonObject initialTarget() { return initialTarget == null ? null : initialTarget.deepCopy(); }
        @Override public JsonObject terminalTarget() { return terminalTarget == null ? null : terminalTarget.deepCopy(); }
    }
    public record Result(boolean recoveredValue) {}
    private record Row(BigInteger time, BigInteger reads, BigInteger drops, BigInteger writes,
            BigInteger failed, BigInteger waits, BigInteger recovered, Boolean value,
            BigInteger lease, boolean stopped, boolean released, boolean closed) {}
    private static final Set<String> FIELDS = Set.of("kind", "token", "bootId", "generationId", "catalogSha256", "ownerWriterId",
            "ownerPid", "ownerStarttime", "targetPid", "targetStarttime", "deadlineMonotonicMs", "observedMonotonicMs",
            "reads", "droppedReads", "writes", "failedWrites", "waitStateSteps", "recoveredValues", "recoveredValue",
            "leaseRevision", "stopped", "released", "closed");
    private static void require(boolean ok, String why) throws IOException { if (!ok) throw new IOException("native state outage: " + why); }
    private static String text(JsonObject v, String key) throws IOException {
        JsonElement e = v.get(key); require(e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString(), key); return e.getAsString();
    }
    private static BigInteger number(JsonObject v, String key, boolean positive) throws IOException {
        JsonElement e = v.get(key); require(e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber(), key);
        String s = e.getAsString(); require(s.matches("0|[1-9][0-9]{0,19}"), key); BigInteger n = new BigInteger(s);
        require(n.bitLength() <= 64 && (!positive || n.signum() > 0), key); return n;
    }
    private static boolean bool(JsonObject v, String key) throws IOException {
        JsonElement e = v.get(key); require(e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean(), key); return e.getAsBoolean();
    }
    private static Row row(Scope s, JsonObject v) throws IOException {
        require(v != null && v.keySet().equals(FIELDS), "closed receipt fields");
        for (var e : java.util.Map.of("kind", s.kind(), "token", s.token(), "bootId", s.boot(), "generationId", s.generation(),
                "catalogSha256", s.catalog(), "ownerWriterId", s.ownerWriter()).entrySet()) require(e.getValue().equals(text(v, e.getKey())), "foreign " + e.getKey());
        require(number(v,"ownerPid",true).equals(BigInteger.valueOf(s.ownerPid())) && number(v,"ownerStarttime",true).equals(BigInteger.valueOf(s.ownerStart()))
                && number(v,"targetPid",true).equals(BigInteger.valueOf(s.targetPid())) && number(v,"targetStarttime",true).equals(BigInteger.valueOf(s.targetStart()))
                && number(v,"deadlineMonotonicMs",true).equals(BigInteger.valueOf(s.deadline())), "owner, target or deadline differs");
        BigInteger time = number(v,"observedMonotonicMs",true); require(time.compareTo(BigInteger.valueOf(s.deadline())) < 0, "expired receipt");
        JsonElement value = v.get("recoveredValue"); require(value != null && (value.isJsonNull() || value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()), "recovered value");
        JsonElement lease = v.get("leaseRevision"); require(lease != null, "lease missing");
        return new Row(time, number(v,"reads",false), number(v,"droppedReads",false), number(v,"writes",false), number(v,"failedWrites",false),
                number(v,"waitStateSteps",false), number(v,"recoveredValues",false), value.isJsonNull() ? null : value.getAsBoolean(),
                lease.isJsonNull() ? null : number(v,"leaseRevision",true), bool(v,"stopped"), bool(v,"released"), bool(v,"closed"));
    }
    private static void endpoint(Scope s, JsonObject row) throws IOException {
        require(row != null && "com.qinggan.canbus.service".equals(text(row, "process"))
                && Set.of("vehicle-drive-pedestrian-warning", "vehicle-drive-forced-ev").contains(text(row, "id"))
                && Set.of("active", "waiting").contains(text(row, "state"))
                && row.has("currentEndpoint") && row.get("currentEndpoint").isJsonObject(), "current canbus target unavailable");
        JsonObject endpoint = row.getAsJsonObject("currentEndpoint");
        for (var item : java.util.Map.of("targetPid", Integer.toString(s.targetPid()), "targetStarttime", Long.toString(s.targetStart()),
                "endpointEpoch", Long.toString(s.endpointEpoch()), "generationId", s.generation(), "catalogSha256", s.catalog()).entrySet())
            require(item.getValue().equals(text(endpoint, item.getKey())), "input endpoint " + item.getKey());
    }
    /** Rows come from the live diagnostic; target rows must come from current RuntimeObservation at both fences. */
    public static Result admit(Scope s, FaultWindow window, boolean cancel, JsonObject originalRoot, JsonObject terminalRoot,
            Observations observations) throws IOException {
        require(s != null && s.kind() != null && s.token() != null && s.boot() != null && s.generation() != null
                && s.catalog() != null && s.ownerWriter() != null
                && Set.of("native-state-read-unavailable", "native-state-write-unavailable").contains(s.kind())
                && s.token().matches("[0-9a-f]{64}") && s.generation().matches("[0-9a-f]{64}") && s.catalog().matches("[0-9a-f]{64}")
                && s.ownerWriter().matches("[0-9a-f]{32}") && s.boot().matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}")
                && s.ownerPid() > 1 && s.ownerStart() > 0 && s.targetPid() > 1 && s.targetStart() > 0 && s.endpointEpoch() > 0 && s.deadline() > 0, "scope");
        require(observations != null && observations.initialWriter() != null && observations.terminalWriter() != null
                && observations.initialOwner() != null && observations.terminalOwner() != null
                && observations.initialInput() != null && observations.terminalInput() != null, "independent owner and input observations missing");
        endpoint(s, observations.initialTarget()); endpoint(s, observations.terminalTarget());
        require(s.boot().equals(observations.initialWriter().boot()) && s.boot().equals(observations.terminalWriter().boot())
                && s.ownerWriter().equals(observations.initialWriter().writerId()) && s.ownerWriter().equals(observations.terminalWriter().writerId())
                && observations.terminalWriter().watermark().compareTo(observations.initialWriter().watermark()) >= 0,
                "current root writer changed");
        for (StartupObservation.ProcessIdentity process : List.of(observations.initialOwner(), observations.terminalOwner()))
            require(process.pid() == s.ownerPid() && Long.toString(s.ownerStart()).equals(process.starttime())
                    && "/data/voboost/voboost-daemon".equals(process.argv0()), "root writer process changed");
        for (StartupObservation.ProcessIdentity process : List.of(observations.initialInput(), observations.terminalInput()))
            require(process.pid() == s.targetPid() && Long.toString(s.targetStart()).equals(process.starttime())
                    && "com.qinggan.canbus.service".equals(process.argv0()), "input process changed");
        require(originalRoot != null && terminalRoot != null && window != null, "terminal cleanup or independent root observation unavailable");
        boolean originalRootValue = ru.voboost.voyemu.VehicleArmed.readPedestrian(originalRoot, s.boot());
        boolean terminalRootValue = ru.voboost.voyemu.VehicleArmed.readPedestrian(terminalRoot, s.boot());
        require(originalRootValue == terminalRootValue, "independently observed root value was not restored");
        JsonObject cleanup = window.closed(), request = window.request(), arm = window.armed();
        require("closed".equals(text(cleanup,"phase")) && bool(cleanup,"cleanup") && "eof".equals(text(cleanup,"reason"))
                && s.token().equals(text(cleanup,"token")) && s.ownerWriter().equals(text(cleanup,"ownerWriterId"))
                && s.token().equals(text(arm,"token")) && s.ownerWriter().equals(text(arm,"ownerWriterId"))
                && s.kind().equals(text(request,"kind")) && s.boot().equals(text(request,"bootId"))
                && s.generation().equals(text(request,"generationId")) && s.catalog().equals(text(request,"catalogSha256"))
                && number(request,"targetPid",true).equals(BigInteger.valueOf(s.targetPid()))
                && Long.toString(s.targetStart()).equals(text(request,"targetStarttime"))
                && Long.toString(s.deadline()).equals(text(arm,"expiresMonotonicMs")), "terminal cleanup or diagnostic scope differs");
        List<JsonObject> receipts = window.nativeReceipts();
        require(receipts.size() >= (cancel ? 7 : 5) && receipts.size() <= 14, "closed bounded receipt sequence");
        List<Row> rows = new java.util.ArrayList<>(); for (JsonObject v : receipts) rows.add(row(s,v));
        String started = text(arm,"startedMonotonicMs"); require(started.matches("[1-9][0-9]{0,19}"), "diagnostic start");
        BigInteger start = new BigInteger(started);
        require(start.bitLength() <= 64 && rows.get(0).time().compareTo(start) >= 0, "receipt precedes diagnostic window");
        for (int i = 1; i < rows.size(); i++) {
            Row a=rows.get(i-1), b=rows.get(i);
            require(b.time().compareTo(a.time()) >= 0 && b.reads().compareTo(a.reads()) >= 0 && b.drops().compareTo(a.drops()) >= 0
                    && b.writes().compareTo(a.writes()) >= 0 && b.failed().compareTo(a.failed()) >= 0 && b.waits().compareTo(a.waits()) >= 0
                    && b.recovered().compareTo(a.recovered()) >= 0 && b.drops().compareTo(b.reads()) <= 0 && b.failed().compareTo(b.writes()) <= 0, "regressed counters or time");
        }
        Row base=rows.get(0), terminal=rows.get(rows.size()-1);
        int releaseIndex=-1;
        for (int i=1; i<rows.size()-1; i++) if (rows.get(i).released()) { releaseIndex=i; break; }
        require(releaseIndex >= (cancel ? 4 : 2), "release phase missing");
        Row release=rows.get(releaseIndex);
        int stoppedIndex=-1;
        if (cancel) for (int i=1; i<releaseIndex; i++) if (rows.get(i).stopped()) { stoppedIndex=i; break; }
        require(!cancel || stoppedIndex>=2 && stoppedIndex<releaseIndex-1, "separate fault and stopped snapshots missing");
        Row fault=rows.get(cancel ? stoppedIndex-1 : releaseIndex-1);
        int recoveryIndex=-1;
        for (int i=releaseIndex+1; i<rows.size()-1; i++) if (rows.get(i).recovered().signum()>0) { recoveryIndex=i; break; }
        require(recoveryIndex>releaseIndex, "fresh recovery snapshot missing");
        Row recovered=rows.get(recoveryIndex);
        for (int i=0; i<rows.size(); i++) {
            Row current=rows.get(i);
            require(current.closed()==(i==rows.size()-1) && current.released()==(i>=releaseIndex), "receipt phase order");
            require((current.recovered().signum()==0)==(current.value()==null)
                    && (s.kind().equals("native-state-read-unavailable") ? current.failed().signum()==0 : current.drops().signum()==0), "fault kind or recovery value differs");
            if (i<releaseIndex) require(current.recovered().signum()==0 && current.value()==null, "premature recovery");
            if (!cancel && i<releaseIndex) require(!current.stopped(), "unexpected stopped controller");
        }
        require(base.reads().signum()==0 && base.drops().signum()==0 && base.writes().signum()==0 && base.failed().signum()==0
                && base.waits().signum()==0 && base.recovered().signum()==0 && base.value()==null && !base.released() && !base.closed(), "baseline");
        require(!fault.released() && !fault.closed() && fault.lease()!=null && fault.recovered().signum()==0 && fault.value()==null, "fault lease");
        if (s.kind().equals("native-state-read-unavailable")) require(fault.drops().compareTo(BigInteger.TWO)>=0 && fault.waits().compareTo(BigInteger.TWO)>=0, "actual read loss and waits missing");
        else require(fault.failed().compareTo(BigInteger.TWO)>=0, "two actual write failures missing");
        if (cancel) {
            Row before=rows.get(stoppedIndex), after=rows.get(releaseIndex-1);
            for (int i=stoppedIndex; i<releaseIndex; i++) require(rows.get(i).stopped(), "controller resumed before release");
            require(before.stopped() && after.stopped() && !before.released() && !after.released()
                    && before.lease()!=null && before.lease().equals(fault.lease()) && before.lease().equals(after.lease())
                    && after.time().subtract(before.time()).compareTo(BigInteger.valueOf(1000)) >= 0
                    && before.reads().equals(after.reads()) && before.writes().equals(after.writes()) && before.waits().equals(after.waits())
                    && after.reads().equals(release.reads()) && after.writes().equals(release.writes()) && after.waits().equals(release.waits())
                    && after.lease().equals(release.lease()), "cancelled worker replay or unbounded quiet observation");
        }
        require((cancel || fault.lease().equals(release.lease())) && release.recovered().signum()==0 && release.value()==null
                && release.released() && !release.closed() && recovered.released() && !recovered.closed() && terminal.released() && terminal.closed()
                && recovered.reads().compareTo(release.reads()) > 0 && recovered.recovered().signum()>0 && recovered.value()!=null && recovered.value().booleanValue() == terminalRootValue
                && terminal.recovered().compareTo(recovered.recovered())>=0 && terminal.value()!=null && terminal.value().booleanValue() == terminalRootValue, "recovery or terminal missing");
        require(recovered.lease()!=null && terminal.lease()!=null && recovered.lease().equals(terminal.lease()), "recovery lease changed");
        require(cancel ? recovered.lease().compareTo(fault.lease()) > 0 : recovered.lease().equals(fault.lease()), "wrong recovery lease");
        return new Result(terminalRootValue);
    }
    /** The same admission applied again to the actual retained CLI stream and independent archives. */
    public static JsonObject readProof(java.nio.file.Path path)throws IOException {
        try {
            JsonObject proof=ru.voboost.tests.RunFiles.read(path),request=proof.getAsJsonObject("request"),arm=proof.getAsJsonObject("armed");
            String boot=text(request,"bootId"),serial=text(proof,"serial"),release=text(proof,"releaseIdentity"),manifest=text(request,"manifestSha256");
            require("vehicle-drive-pedestrian-warning".equals(text(proof,"agent"))&&number(proof,"exit",false).signum()==0,"actual PW terminal process");
            FaultWindow window=FaultWindow.readNative(java.nio.file.Path.of(text(proof,"stdout")),request,serial,release);
            require(window.armed().equals(arm)&&window.closed().equals(proof.getAsJsonObject("closed")),"retained native envelopes changed");
            var before=recordedLogs(proof.getAsJsonObject("initialLogCapture"),serial,release,manifest,boot);
            var after=recordedLogs(proof.getAsJsonObject("terminalLogCapture"),serial,release,manifest,boot);
            var owner=StartupObservation.identity(text(proof,"ownerProcessesBefore"),boot,"voboost-daemon");
            var finalOwner=StartupObservation.identity(text(proof,"ownerProcessesAfter"),boot,"voboost-daemon");
            var input=StartupObservation.identity(text(proof,"ownerProcessesBefore"),boot,"com.qinggan.canbus.service");
            var finalInput=StartupObservation.identity(text(proof,"ownerProcessesAfter"),boot,"com.qinggan.canbus.service");
            JsonObject first=proof.getAsJsonObject("initialTarget"),last=proof.getAsJsonObject("terminalTarget"),endpoint=first.getAsJsonObject("currentEndpoint");
            Scope scope=new Scope(text(request,"kind"),text(arm,"token"),boot,text(request,"generationId"),text(request,"catalogSha256"),before.writerId(),owner.pid(),Long.parseLong(owner.starttime()),input.pid(),Long.parseLong(input.starttime()),Long.parseLong(text(endpoint,"endpointEpoch")),Long.parseLong(text(arm,"expiresMonotonicMs")));
            admit(scope,window,bool(proof,"cancel"),proof.getAsJsonObject("originalRoot"),proof.getAsJsonObject("terminalRoot"),new Observations(first,last,new InstallerLogs.CurrentSession(before.boot(),before.writerId(),before.watermark()),new InstallerLogs.CurrentSession(after.boot(),after.writerId(),after.watermark()),owner,finalOwner,input,finalInput));
            return proof;
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException("native proof re-evaluation interrupted",interrupted);}
        catch(IOException failure){throw failure;}catch(Exception malformed){throw new IOException("actual retained native proof unavailable",malformed);}
    }
    private static InstallerLogs.Snapshot recordedLogs(JsonObject capture,String serial,String release,String manifest,String boot)throws Exception {
        java.nio.file.Path archive=java.nio.file.Path.of(text(capture,"archive"));
        require(archive.isAbsolute(),"absolute actual log archive required");
        return InstallerLogs.read(archive,archive.getParent(),serial,release,manifest,boot,java.time.Instant.parse(text(capture,"startedAt")),java.time.Instant.parse(text(capture,"endedAt")));
    }
    private NativeStateOutageAdmission() {}
}
