package ru.voboost.tests.agents;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import ru.voboost.tests.installer.cli.FaultWindow;
import ru.voboost.tests.logging.InstallerLogs;

/** Current Java PW retry/cancel evidence; native metadata does not establish or revoke controller ownership. */
public final class StateOutageEvidence {

    public static final String AGENT = "vehicle-drive-pedestrian-warning";

    private static void require(boolean value, String reason) throws IOException {
        if (!value)
            throw new IOException("state outage: " + reason);
    }

    private static String text(JsonObject value, String key) throws IOException {
        require(value.has(key) && value.get(key).isJsonPrimitive()
                && value.getAsJsonPrimitive(key).isString(), "source " + key);
        return value.get(key).getAsString();
    }

    private static void source(InstallerLogs.Diagnostic row,
            RootVehicleState.Authority authority) throws IOException {
        for (var pair : java.util.Map.of("bootId", authority.boot(), "process", authority.process(),
                "targetPid", authority.pid(), "targetStarttime", authority.starttime(),
                "endpointEpoch", authority.epoch()).entrySet())
            require(pair.getValue().equals(text(row.sourceFields(), pair.getKey())),
                    "current Java endpoint differs");
    }

    public static Set<Integer> retries(InstallerLogs.Snapshot prior, InstallerLogs.Snapshot current,
            RootVehicleState.Authority authority) throws IOException {
        Set<Integer> attempts = new HashSet<>();
        for (var row : current.after(prior))
            if (AGENT.equals(row.agent()) && "agent.start.state-read".equals(row.event())
                    && "unavailable-retry".equals(row.outcome())) {
                if (row.sourceFields().has("desiredDocumentGeneration")
                        && !authority.document().equals(text(row.sourceFields(),
                        "desiredDocumentGeneration")))
                    continue;
                source(row, authority);
                require(authority.document().equals(text(row.sourceFields(),
                        "desiredDocumentGeneration")), "current desired context missing");
                JsonObject fields = row.fields();
                require(fields.keySet().equals(Set.of("attempt"))
                        && fields.get("attempt").isJsonPrimitive()
                        && fields.getAsJsonPrimitive("attempt").isNumber(),
                        "actual bounded attempt missing");
                String raw = fields.get("attempt").getAsString();
                require(raw.matches("[1-9]|1[0-9]|20"), "readiness attempt outside source budget");
                attempts.add(Integer.parseInt(raw));
            }
        require(attempts.size() >= 2, "two distinct actual UNAVAILABLE retries missing");
        return Set.copyOf(attempts);
    }

    public static void quiet(InstallerLogs.Snapshot drained, InstallerLogs.Snapshot current,
            RootVehicleState.Authority authority, FaultWindow window) throws IOException {
        current.requireCompleteExportAfter(drained);
        require(window.stateReplyLosses(AGENT, drained, current).isEmpty(),
                "cancelled worker issued new dropped state reads");
        for (var row : current.after(drained))
            if (AGENT.equals(row.agent()) && "agent.start.state-read".equals(row.event())
                    && "unavailable-retry".equals(row.outcome())) {
                if (row.sourceFields().has("desiredDocumentGeneration")
                        && !authority.document().equals(text(row.sourceFields(),
                        "desiredDocumentGeneration")))
                    continue;
                source(row, authority);
                throw new IOException("state outage: cancelled worker retried after drain");
            }
        for (var row : current.effectsAfter(drained))
            if (row.fields().has("agentId")
                    && AGENT.equals(row.fields().get("agentId").getAsString())) {
                var operation = RootVehicleState.read(row.fields());
                if (operation.fields().has("documentGeneration")
                        && !authority.document().equals(operation.fields().get("documentGeneration").getAsString()))
                    continue;
                RootVehicleState.requireAuthority(operation, authority);
                require(!"read".equals(operation.operation()),
                        "cancelled worker continued root state reads after drain");
            }
    }

    public static final java.util.List<String> PROGRAM_ORDER=java.util.List.of("native-pw-read-recovery","native-pw-read-cancellation","native-pw-write-recovery","native-pw-write-cancellation","runtime-disable-cancellation-fev","runtime-disable-cancellation-pw");
    public static final Set<String> REQUIRED_PROGRAMS=Set.copyOf(PROGRAM_ORDER);
    /** Whole case admission reopens each actual component result; four PW results are only a subset. */
    public static void requireWhole(JsonObject proof,java.nio.file.Path selectedCatalog,JsonObject currentScope)throws IOException {
        requireScope(proof.getAsJsonObject("scope"),currentScope);
        require(selectedCatalog!=null&&selectedCatalog.isAbsolute()&&java.nio.file.Files.isRegularFile(selectedCatalog),"selected normally verified catalog required");
        require(proof.has("subchecks")&&proof.get("subchecks").isJsonObject()&&proof.getAsJsonObject("subchecks").keySet().equals(REQUIRED_PROGRAMS),"all six actual programs required");
        JsonObject subchecks=proof.getAsJsonObject("subchecks"),catalog=ru.voboost.tests.RunFiles.read(selectedCatalog);Set<java.nio.file.Path> unique=new HashSet<>();Set<String> tokens=new HashSet<>();int lastOperation=0;
        for(String key:PROGRAM_ORDER) {
            java.nio.file.Path path=java.nio.file.Path.of(text(subchecks,key));require(path.isAbsolute()&&unique.add(path.toRealPath()),"distinct retained program required");inside(path,currentScope);JsonObject recorded=ru.voboost.tests.RunFiles.read(path),restoration=recorded.getAsJsonObject("restoration");
            requireScope(recorded.getAsJsonObject("scope"),currentScope);requireRestoration(restoration,catalog,currentScope);
            if(key.startsWith("native-pw-")) {
                java.nio.file.Path nativeFile=java.nio.file.Path.of(text(recorded,"nativeProof"));inside(nativeFile,currentScope);JsonObject nativeProof=NativeStateOutageAdmission.readProof(nativeFile);JsonObject request=nativeProof.getAsJsonObject("request"),arm=nativeProof.getAsJsonObject("armed");require(text(currentScope,"serial").equals(text(nativeProof,"serial"))&&text(currentScope,"releaseIdentity").equals(text(nativeProof,"releaseIdentity")),"native release/device outside current scope");
                for(var entry:java.util.Map.of("bootId","bootId","manifestSha256","manifest","catalogSha256","catalog","generationId","generation").entrySet())require(text(currentScope,entry.getValue()).equals(text(request,entry.getKey())),"native request outside current candidate/boot");
                require(text(currentScope,"writerId").equals(text(arm,"ownerWriterId"))&&tokens.add(text(arm,"token")),"foreign writer or replayed fault token");
                String beforeProcesses=text(nativeProof,"ownerProcessesBefore");var owner=ru.voboost.tests.installer.cli.StartupObservation.identity(beforeProcesses,text(currentScope,"bootId"),"voboost-daemon");var input=ru.voboost.tests.installer.cli.StartupObservation.identity(beforeProcesses,text(currentScope,"bootId"),"com.qinggan.canbus.service");require(text(currentScope,"ownerPid").equals(Integer.toString(owner.pid()))&&text(currentScope,"ownerStarttime").equals(owner.starttime())&&text(currentScope,"inputPid").equals(Integer.toString(input.pid()))&&text(currentScope,"inputStarttime").equals(input.starttime()),"foreign current owner/input lifetime");
                lastOperation=operation(java.nio.file.Path.of(text(nativeProof,"operation")),nativeFile,currentScope,lastOperation,nativeProof);
                require((key.contains("-read-")?"native-state-read-unavailable":"native-state-write-unavailable").equals(text(nativeProof,"kind"))&&booleanValue(nativeProof,"cancel")==key.endsWith("-cancellation"),"wrong native program in aggregate");
                require("vehicle-drive-pedestrian-warning".equals(text(restoration,"agent"))&&text(restoration,"bootId").equals(text(nativeProof.getAsJsonObject("request"),"bootId")),"native program/restoration scope differs");
            } else {java.nio.file.Path programFile=java.nio.file.Path.of(text(recorded,"program"));inside(programFile,currentScope);JsonObject program=ru.voboost.tests.RunFiles.read(programFile);requireDisable(program,restoration,key.endsWith("-fev"),currentScope);java.nio.file.Path warmFile=java.nio.file.Path.of(text(program,"warmCycle"));inside(warmFile,currentScope);lastOperation=operation(java.nio.file.Path.of(text(ru.voboost.tests.RunFiles.read(warmFile),"operation")),warmFile,currentScope,lastOperation,null);}
        }
    }
    static void requireScope(JsonObject actual,JsonObject current)throws IOException {
        require(actual!=null&&current!=null&&current.keySet().equals(Set.of("runId","work","machine","sessionId","bootId","serial","manifest","catalog","releaseIdentity","generation","publisher","writerId","ownerPid","ownerStarttime","inputPid","inputStarttime","installer","driver"))&&actual.equals(current),"program belongs to another current run/session/machine/boot/candidate");
    }
    static void requireCurrentProcesses(String body,JsonObject current)throws IOException {
        String boot=text(current,"bootId");var owner=ru.voboost.tests.installer.cli.StartupObservation.identity(body,boot,"voboost-daemon");var input=ru.voboost.tests.installer.cli.StartupObservation.identity(body,boot,"com.qinggan.canbus.service");
        require(text(current,"ownerPid").equals(Integer.toString(owner.pid()))&&text(current,"ownerStarttime").equals(owner.starttime())&&"/data/voboost/voboost-daemon".equals(owner.argv0())&&text(current,"inputPid").equals(Integer.toString(input.pid()))&&text(current,"inputStarttime").equals(input.starttime())&&"com.qinggan.canbus.service".equals(input.argv0()),"current owner/input process replaced");
    }
    static void requireCurrentCan(ru.voboost.voyemu.VehicleCan.Snapshot can,JsonObject current)throws IOException {
        require(can.pid()==Long.parseLong(text(current,"inputPid")),"actual warm ACC edge names a replaced input process");
    }
    private static void inside(java.nio.file.Path path,JsonObject current)throws IOException {
        java.nio.file.Path work=java.nio.file.Path.of(text(current,"work")).toRealPath();require(path.isAbsolute()&&java.nio.file.Files.isRegularFile(path,java.nio.file.LinkOption.NOFOLLOW_LINKS)&&path.toRealPath().startsWith(work),"retained source outside current ProductRun work");
    }
    private static int operation(java.nio.file.Path file,java.nio.file.Path evidence,JsonObject current,int previous,JsonObject nativeProof)throws IOException {
        inside(file,current);JsonObject row=ru.voboost.tests.RunFiles.read(file);java.nio.file.Path work=java.nio.file.Path.of(text(current,"work")).toRealPath();String ordinal=file.getParent().getFileName().toString();require(ordinal.matches("[0-9]{6}")&&file.getParent().getParent().equals(work.resolve("operations")),"actual ProductRun operation required");int index=Integer.parseInt(ordinal);require(index>previous&&text(current,"runId").equals(text(row,"runId"))&&text(current,"sessionId").equals(text(row,"sessionId"))&&"verified".equals(text(row,"state"))&&"CONFIGURE".equals(text(row,"kind")),"foreign, unknown or out-of-order ProductRun operation");JsonObject input=row.getAsJsonObject("input");require(text(current,"manifest").equals(text(input,"manifest"))&&text(current,"serial").equals(text(input,"device")),"operation candidate/device differs");require(row.getAsJsonArray("evidence").asList().stream().anyMatch(e->work.resolve(e.getAsString()).normalize().equals(evidence)),"source missing from verified operation evidence");
        require((nativeProof==null?"fev-warm-power-cycle":"vehicle-state-outage").equals(text(input,"phase")),"foreign operation phase");
        if(nativeProof!=null){require(nativeProof.getAsJsonObject("request").equals(input.getAsJsonObject("diagnostic")),"native operation request differs");JsonObject process=input.getAsJsonObject("process");require(process!=null,"actual native process dispatch missing");var command=process.getAsJsonArray("command");ru.voboost.tests.installer.cli.EmulatorCommand.require(command,java.nio.file.Path.of(text(current,"driver")),java.nio.file.Path.of(text(current,"installer")),java.nio.file.Path.of(command.get(2).getAsString()),text(current,"serial"));java.nio.file.Path request=java.nio.file.Path.of(command.get(2).getAsString()),stdout=java.nio.file.Path.of(text(nativeProof,"stdout")),stderr=java.nio.file.Path.of(text(nativeProof,"stderr"));inside(request,current);inside(stdout,current);inside(stderr,current);require(ru.voboost.tests.RunFiles.read(request).equals(nativeProof.getAsJsonObject("request"))&&work.resolve(text(process,"stdout")).equals(stdout)&&work.resolve(text(process,"stderr")).equals(stderr),"native actual request/output binding differs");for(String key:java.util.List.of("initialLogCapture","terminalLogCapture"))inside(java.nio.file.Path.of(text(nativeProof.getAsJsonObject(key),"archive")),current);}
        return index;
    }
    private static boolean booleanValue(JsonObject value,String key)throws IOException {
        require(value!=null&&value.has(key)&&value.get(key).isJsonPrimitive()&&value.getAsJsonPrimitive(key).isBoolean(),"actual boolean "+key);return value.get(key).getAsBoolean();
    }
    private static RuntimeObservation recordedRuntime(java.nio.file.Path path,JsonObject scope)throws IOException {
        return RuntimeObservation.read(ru.voboost.tests.RunFiles.read(path),new RuntimeObservation.Identity(text(scope,"bootId"),text(scope,"releaseIdentity"),text(scope,"manifest"),text(scope,"catalog"),text(scope,"generation")));
    }
    private static ru.voboost.voyemu.VehicleCan.Snapshot recordedVehicle(java.nio.file.Path path,JsonObject scope,boolean forcedEv)throws IOException {
        JsonObject raw=ru.voboost.tests.RunFiles.read(path);var can=ru.voboost.voyemu.VehicleCan.read(raw.getAsJsonObject("can"),text(scope,"sessionId"),text(scope,"bootId"));
        var control=ru.voboost.voyemu.VehicleUi.feature(ru.voboost.voyemu.UiEvidence.surface(raw.getAsJsonObject("ui"),"primary"),forcedEv?ru.voboost.voyemu.VehicleUi.Feature.FORCED_EV:ru.voboost.voyemu.VehicleUi.Feature.PEDESTRIAN_WARNING);
        require(control.checked()==booleanValue(raw,"checked")&&control.checked()==can.selected(forcedEv),"actual OEM hierarchy and CAN getter disagree");return can;
    }
    private static void requireRestoration(JsonObject scope,JsonObject catalog,JsonObject current)throws IOException {
        require(scope!=null,"restoration source missing");for(String key:java.util.List.of("bootId","sessionId","manifest","catalog","releaseIdentity","generation"))require(text(current,key).equals(text(scope,key)),"restoration outside current "+key);
        require(scope!=null&&Set.of("vehicle-drive-pedestrian-warning","vehicle-drive-forced-ev").contains(text(scope,"agent")),"restoration source missing");
        for(String key:java.util.List.of("originalRuntime","currentRuntime","originalOem","currentOem"))inside(java.nio.file.Path.of(text(scope,key)),current);
        var before=recordedRuntime(java.nio.file.Path.of(text(scope,"originalRuntime")),scope);var after=recordedRuntime(java.nio.file.Path.of(text(scope,"currentRuntime")),scope);
        require(text(current,"publisher").equals(before.publisher())&&before.publisher().equals(after.publisher())&&before.startup().equals(after.startup())&&before.runningStartup().equals(after.runningStartup())&&AgentSettings.snapshot(catalog,before).equals(AgentSettings.snapshot(catalog,after)),"original full configuration/owner not restored");
        boolean forcedEv=text(scope,"agent").equals("vehicle-drive-forced-ev");var first=recordedVehicle(java.nio.file.Path.of(text(scope,"originalOem")),scope,forcedEv);var last=recordedVehicle(java.nio.file.Path.of(text(scope,"currentOem")),scope,forcedEv);last.requireNext(first);require(first.pid()==Long.parseLong(text(current,"inputPid"))&&last.pid()==first.pid()&&first.selected(forcedEv)==last.selected(forcedEv),"original OEM state not restored");
        JsonObject original=scope.getAsJsonObject("originalRoot"),currentRoot=scope.getAsJsonObject("currentRoot");String boot=text(scope,"bootId");require((forcedEv?ru.voboost.voyemu.VehicleArmed.read(original,boot):ru.voboost.voyemu.VehicleArmed.readPedestrian(original,boot))==(forcedEv?ru.voboost.voyemu.VehicleArmed.read(currentRoot,boot):ru.voboost.voyemu.VehicleArmed.readPedestrian(currentRoot,boot)),"original root intent not restored");
    }
    private static void requireDisable(JsonObject proof,JsonObject scope,boolean forcedEv,JsonObject currentScope)throws IOException {
        String agent=forcedEv?"vehicle-drive-forced-ev":"vehicle-drive-pedestrian-warning",boot=text(scope,"bootId"),session=text(scope,"sessionId");require(agent.equals(text(proof,"agent"))&&agent.equals(text(scope,"agent"))&&boot.equals(text(proof,"bootId"))&&session.equals(text(proof,"sessionId")),"wrong disabled warm program");
        boolean[] expected=forcedEv?new boolean[]{false,true,false,true}:new boolean[]{true,false,true,true,false};var states=proof.getAsJsonArray("states");require(states!=null&&states.size()==expected.length,"complete disabled warm state sequence required");ru.voboost.voyemu.VehicleCan.Snapshot prior=null;
        for(int i=0;i<states.size();i++){inside(java.nio.file.Path.of(states.get(i).getAsString()),currentScope);var state=recordedVehicle(java.nio.file.Path.of(states.get(i).getAsString()),scope,forcedEv);require(state.pid()==Long.parseLong(text(currentScope,"inputPid"))&&state.selected(forcedEv)==expected[i],"disabled warm counterfactual state differs");if(prior!=null)state.requireNext(prior);prior=state;}
        var roots=proof.getAsJsonArray("rootAndStop");require(roots!=null&&roots.size()==4,"armed and stopped boundaries missing");
        for(int i:new int[]{0,3}){inside(java.nio.file.Path.of(roots.get(i).getAsString()),currentScope);JsonObject root=ru.voboost.tests.RunFiles.read(java.nio.file.Path.of(roots.get(i).getAsString()));require(forcedEv?ru.voboost.voyemu.VehicleArmed.read(root,boot):ru.voboost.voyemu.VehicleArmed.readPedestrian(root,boot),"actual arm/rearm root missing");}
        for(int i:new int[]{1,2}){inside(java.nio.file.Path.of(roots.get(i).getAsString()),currentScope);JsonObject stop=ru.voboost.tests.RunFiles.read(java.nio.file.Path.of(roots.get(i).getAsString()));require(boot.equals(text(stop,"bootId"))&&agent.equals(text(stop,"agent")),"stopped controller scope differs");ru.voboost.tests.installer.cli.StartupObservation.requireSameProcesses(text(stop,"processesBeforeStop"),text(stop,"processes"),boot);requireCurrentProcesses(text(stop,"processesBeforeStop"),currentScope);requireCurrentProcesses(text(stop,"processes"),currentScope);inside(java.nio.file.Path.of(text(stop,"runtimeOfficial")),currentScope);var runtime=recordedRuntime(java.nio.file.Path.of(text(stop,"runtimeOfficial")),scope);require(text(currentScope,"publisher").equals(runtime.publisher())&&!runtime.desired(agent)&&runtime.targets(agent).isEmpty(),"disabled target still active or publisher replaced");}
        JsonObject warm=ru.voboost.tests.RunFiles.read(java.nio.file.Path.of(text(proof,"warmCycle")));require(boot.equals(text(warm,"bootId")),"warm cycle changed boot");var reads=warm.getAsJsonArray("reads");require(reads!=null&&reads.size()>=4&&reads.size()%2==0,"actual power/CAN edges missing");boolean asleep=false,awake=false;prior=recordedVehicle(java.nio.file.Path.of(states.get(1).getAsString()),scope,forcedEv);
        for(int i=0;i<reads.size();i+=2){var can=ru.voboost.voyemu.VehicleCan.read(reads.get(i).getAsJsonObject(),session,boot);requireCurrentCan(can,currentScope);if(prior!=null)can.requireNext(prior);prior=can;JsonObject system=reads.get(i+1).getAsJsonObject();require(boot.equals(text(system,"boot_id"))&&boot.equals(text(system.getAsJsonObject("value"),"boot_id")),"display edge crossed boot");JsonObject power=system.getAsJsonObject("value").getAsJsonObject("power");int acc=can.receipt().getAsJsonObject("acc").get("raw").getAsInt();if(!asleep&&acc==0&&ru.voboost.voyemu.VehiclePower.matches(power,false))asleep=true;else if(asleep&&acc==2&&ru.voboost.voyemu.VehiclePower.matches(power,true))awake=true;}
        recordedVehicle(java.nio.file.Path.of(states.get(2).getAsString()),scope,forcedEv).requireNext(prior);require(asleep&&awake,"separate actual ACC and display sleep/wake edges required");
    }
    private StateOutageEvidence() {}
}
