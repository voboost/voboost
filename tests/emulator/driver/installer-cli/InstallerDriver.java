package ru.voboost.tests.installer.cli;

import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.StringReader;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ru.voboost.voyemu.KeyboardCapture;
import ru.voboost.voyemu.KeyboardInput;
import ru.voboost.voyemu.NavigationInput;
import ru.voboost.voyemu.OwnedSession;
import ru.voboost.voyemu.ReviewedKeyboard;
import ru.voboost.voyemu.UiEvidence;
import ru.voboost.voyemu.UiTarget;
import ru.voboost.tests.ProductRun;
import ru.voboost.tests.RunFiles;
import ru.voboost.tests.agents.InitialSeedingProgram;
import ru.voboost.voyemu.VoyemuClient;

/** Runs the packaged CLI against one positively identified owned emulator; never falls back to USB. */
public final class InstallerDriver implements InstallMonitor.Observer {
    private static final Pattern BOOT = Pattern.compile("bootCompleted=([01]?)\\nbootId=([0-9a-f-]{36})\\nenforcing=(Enforcing|Permissive)\\noperation:\\n([\\s\\S]*)");
    private static final List<String> ABSENCE = List.of("root", "app-data", "init-file", "package", "app-op", "mounts", "processes", "init-services", "localization-overlays", "policy");
    private static final String LAUNCHER = "com.qinggan.app.launcher";
    /** One observation needs about 0.8 s on this device (0.46 s device read plus process start), so an
     * iteration that cannot be given this much of its loop budget is not attempted at all. */
    private static final long MINIMUM_OBSERVATION_NANOS = Duration.ofMillis(1500).toNanos();
    private final Path binary, work;
    private Path emulatorDriver;

    public void configureEmulatorDriver(Path executable) throws IOException {
        if (emulatorDriver != null || executable == null || !executable.isAbsolute()
                || !Files.isRegularFile(executable) || !Files.isExecutable(executable))
            throw new IOException("one built host emulator driver is required");
        emulatorDriver = executable.toRealPath();
    }

    private List<String> command(List<String> arguments) throws IOException {
        if (arguments.size() == 2 && "--emulator-check".equals(arguments.get(0)))
            return EmulatorCommand.of(emulatorDriver, binary, Path.of(arguments.get(1)), device);
        List<String> command = new ArrayList<>(); command.add(binary.toString()); command.addAll(arguments);
        command.add("--device"); command.add(device); return command;
    }
    private final Path voyemuRepository, originalPwSystem;
    private final VoyemuClient client;
    private final OwnedSession owned;
    private final String profile, manifest, version, catalog;
    private final String device;
    private final ProductRun productRun;
    private ru.voboost.tests.ui.SettingsCopySource.Proof settingsCopy;
    private Path candidateCatalogEvidence,candidateManifestEvidence,lastRuntimeOfficial,lastInstallOperation;
    private final java.util.Map<String,Path> seedingInstallOperations=new java.util.HashMap<>(),seedingUninstallOperations=new java.util.HashMap<>();
    private final java.util.Map<String,InitialSeedingAdmission.Capture> seedingCaptures=new java.util.HashMap<>();
    private final java.util.Map<String,JsonObject> seedingCaptureEvidence=new java.util.HashMap<>();
    /** The lifecycle keeps this normally verified package mounted for the entire run. */
    public void configureCandidatePackage(Path candidateCatalog)throws IOException {
        if(candidateCatalogEvidence!=null)throw new IOException("selected package already retained");
        Path resources=binary.getParent().getParent().resolve("Resources");
        if(!candidateCatalog.toRealPath().equals(resources.resolve("agents.json").toRealPath()))throw new IOException("catalog is not the selected mounted package");
        candidateCatalogEvidence=work.resolve("candidate-agents.json");Files.copy(candidateCatalog,candidateCatalogEvidence);
        candidateManifestEvidence=work.resolve("candidate-manifest.json");Files.copy(resources.resolve("voboost.json"),candidateManifestEvidence);
    }
    public void configureSettingsCopy(ru.voboost.tests.ui.SettingsCopySource.Proof expected)throws IOException {
        if(settingsCopy!=null||expected==null)throw new IOException("Settings expected data is missing or already set");expected.requireCatalogIdentity(catalog);settingsCopy=expected;
    }
    private long androidPid;
    private int counter;
    private boolean preparationRemovedProduct, observeFirstInstallBootEarly;
    private final Set<String> observedUiIds = new java.util.HashSet<>();
    record BootData(String id, boolean complete, boolean enforcing, JsonObject operation,Path evidence) {}
    @FunctionalInterface interface Read<T> { T get() throws Exception; }
    @FunctionalInterface interface BootRead<T> { T get(BootData boot) throws Exception; }
    static <T> T inReadyBoot(String expected, Read<BootData> boundary, Read<T> body) throws Exception {
        return inReadyBoot(boundary, before -> {
            if (!expected.equals(before.id())) throw new IOException("observation requires the expected boot");
            return body.get();
        });
    }
    static <T> T inReadyBoot(Read<BootData> boundary, BootRead<T> body) throws Exception {
        BootData before = boundary.get();
        if (before == null || before.id() == null || before.id().isBlank() || !before.complete() || !before.enforcing())
            throw new IOException("observation requires a ready Enforcing boot");
        T result = body.get(before);
        BootData after = boundary.get();
        if (after == null || !before.id().equals(after.id()) || !after.complete() || !after.enforcing())
            throw new IOException("observation crossed boot or SELinux boundary");
        return result;
    }
    private final java.util.Map<String,Path> observedReadyBoots=new java.util.LinkedHashMap<>(),verifiedBootCaptures=new java.util.LinkedHashMap<>();
    private Path lastNativeEvidence;
    private final java.util.Map<String,Path> nativeUiEvidence=new java.util.HashMap<>();

    public InstallerDriver(Path binary, Path work, VoyemuClient client, OwnedSession owned, String profile,
            String manifest, String version, String catalog) throws Exception {
        this(binary, work, client, owned, profile, manifest, version, catalog, null);
    }

    public InstallerDriver(Path binary, Path work, VoyemuClient client, OwnedSession owned, String profile,
            String manifest, String version, String catalog, ProductRun productRun) throws Exception {
        this(binary,work,client,owned,profile,manifest,version,catalog,productRun,null);
    }
    public InstallerDriver(Path binary,Path work,VoyemuClient client,OwnedSession owned,String profile,
            String manifest,String version,String catalog,ProductRun productRun,Path voyemuExecutable)throws Exception {
        this(binary, work, client, owned, profile, manifest, version, catalog, productRun, voyemuExecutable, true);
    }

    public InstallerDriver(Path binary, Path work, VoyemuClient client, OwnedSession owned, String profile,
            String manifest, String version, String catalog, ProductRun productRun, Path voyemuExecutable,
            boolean requireOriginalPwArtwork) throws Exception {
        try (var entries = Files.list(work)) {
            if (entries.findAny().isPresent()) throw new IOException("driver evidence directory must be fresh; retained evidence is never overwritten");
        }
        if (productRun != null && !productRun.owns(owned)) throw new IOException("product run owns another session");
        this.productRun = productRun;
        this.voyemuRepository = voyemuExecutable == null ? null
                : requireOriginalPwArtwork ? PwOriginalInputs.repository(voyemuExecutable, profile)
                : voyemuArtifactRepository(voyemuExecutable);
        this.originalPwSystem = voyemuRepository == null || !requireOriginalPwArtwork ? null
                : PwOriginalInputs.system(voyemuRepository, profile);
        this.binary = binary; this.work = work; this.client = client; this.owned = owned; this.profile = profile;
        this.manifest = manifest; this.version = version; this.catalog = catalog;
        JsonObject status = nativeRead(client.status(id(), profile, owned.session()), "identity");
        JsonObject android = object(object(status, "deliveries"), "android");
        device = text(android, "serial");
        androidPid = integer(status, "android_pid");
        if (!owned.session().id().equals(text(status, "session_id")) || !truth(status, "ok") || !falseValue(status, "stopped")
                || androidPid < 1 || integer(android, "pid") != androidPid || !emulator(device))
            throw new IOException("owned emulator endpoint identity differs; USB fallback forbidden");
        JsonObject qnx = object(object(status, "deliveries"), "qnx");
        long qnxPid = integer(status, "qnx_pid");
        if (qnxPid < 1 || integer(qnx, "pid") != qnxPid)
            throw new IOException("BLOCKED: this package lifecycle requires the owned QNX guest");
    }

    private static Path voyemuArtifactRepository(Path executable) throws IOException {
        for (Path parent = executable.toRealPath().getParent(); parent != null; parent = parent.getParent()) {
            if (Files.isDirectory(parent.resolve(".local/build")) && Files.isDirectory(parent.resolve("machines"))) {
                return parent;
            }
        }
        throw new IOException("Voyemu artifact root is unavailable for " + executable);
    }

    private JsonObject inventoryNative(VoyemuClient.Attempt attempt,JsonObject receipt,String field,JsonArray ordered,String step)throws Exception {
        JsonObject request=RunFiles.parse(attempt.json());if(request.has("session"))request.getAsJsonObject("session").remove("ownership_id");
        Path requestFile=work.resolve("inventory-"+field+"-request-"+(++counter)+".json");RunFiles.write(requestFile,request);
        VoyemuClient.Reply reply=client.execute(attempt,Duration.ofSeconds(15));
        JsonObject raw=new JsonObject();raw.addProperty("state",reply.state().name());raw.add("response",reply.response());raw.addProperty("diagnostic",reply.diagnostic());
        Path rawFile=work.resolve("inventory-"+field+"-raw-"+(++counter)+".json");RunFiles.write(rawFile,raw);
        if(reply.state()!=VoyemuClient.State.RESPONSE||!truth(reply.response(),"ok"))throw new IOException("inventory owned observation unavailable: "+field);
        JsonObject result=object(reply.response(),"result");Path clean=work.resolve("inventory-"+field+"-"+(++counter)+".json");RunFiles.write(clean,result);
        receipt.addProperty(field,clean.toString());receipt.addProperty("raw"+Character.toUpperCase(field.charAt(0))+field.substring(1),rawFile.toString());receipt.addProperty(field+"Request",requestFile.toString());
        JsonObject event=new JsonObject();event.addProperty("step",step);event.addProperty("request",requestFile.toString());event.addProperty("output",rawFile.toString());ordered.add(event);return result;
    }
    private void inventoryCommand(JsonObject receipt,String prefix,String actionName,JsonArray ordered)throws Exception {
        Path request=work.resolve("inventory-"+prefix+"-request-"+(++counter)+".json");RunFiles.write(request,action(actionName));
        CliProcess.Result result=run(List.of("--emulator-check",request.toString()),Duration.ofSeconds(30),null,null);
        JsonObject envelope=json(output(result));if(!envelope.keySet().equals(Set.of("schemaVersion","serial","body"))||integer(envelope,"schemaVersion")!=1||!device.equals(text(envelope,"serial")))throw new IOException("inventory command envelope differs");
        if(Files.size(result.err())!=0)throw new IOException("inventory command emitted stderr");
        Path body=work.resolve("inventory-"+prefix+"-body-"+(++counter)+".txt");Files.writeString(body,text(envelope,"body"));
        Path execution=work.resolve("inventory-"+prefix+"-execution-"+(++counter)+".json");JsonObject executed=new JsonObject();executed.addProperty("schemaVersion",1);executed.addProperty("candidate",text(receipt,"candidate"));JsonArray argv=new JsonArray();for(String argument:command(List.of("--emulator-check",request.toString())))argv.add(argument);executed.add("argv",argv);executed.addProperty("binary",binary.toString());executed.addProperty("driver",emulatorDriver.toString());executed.addProperty("request",request.toString());executed.addProperty("stdout",result.out().toString());executed.addProperty("stderr",result.err().toString());executed.addProperty("exit",result.exit());RunFiles.write(execution,executed);receipt.addProperty(prefix+"Execution",execution.toString());
        receipt.addProperty(prefix+"Request",request.toString());receipt.addProperty(prefix+"Output",result.out().toString());receipt.addProperty(prefix+"Error",result.err().toString());receipt.addProperty(prefix+"Body",body.toString());receipt.addProperty(prefix+"Exit",result.exit());
        JsonObject event=new JsonObject();event.addProperty("step",prefix);event.addProperty("execution",execution.toString());event.addProperty("request",request.toString());event.addProperty("output",result.out().toString());event.addProperty("error",result.err().toString());event.addProperty("exit",result.exit());ordered.add(event);
    }
    /** One read-only package census with the same journal, machine and boot around both producers. */
    public Path collectTargetInventory(String checkRun,Path candidate)throws Exception {
        UUID.fromString(checkRun);if(productRun==null||!productRun.owns(owned)||!candidate.isAbsolute())throw new IOException("owned inventory scope required");
        JsonObject run=productRun.identity();Path runFile=Path.of(text(run,"work")).resolve("run.json");String machine=text(run,"machine");
        Path linkFile=work.resolve("check-link-"+(++counter)+".json");JsonObject link=new JsonObject();link.addProperty("schemaVersion",1);link.addProperty("checkRunId",checkRun);link.addProperty("productRunId",text(run,"runId"));link.addProperty("candidate",candidate.toRealPath().toString());link.addProperty("machine",machine);link.addProperty("run",runFile.toString());RunFiles.write(linkFile,link);
        JsonObject input=new JsonObject();input.addProperty("phase","target-inventory");input.addProperty("checkRunId",checkRun);input.addProperty("productRunId",text(run,"runId"));input.addProperty("candidate",candidate.toRealPath().toString());
        try(ProductRun.Operation operation=productRun.reserve(ProductRun.Kind.CONFIGURE,input)) {
            BootData before=boot(null);operation.observePriorBoot(before.id());operation.dispatch();
            JsonObject receipt=new JsonObject();receipt.addProperty("schemaVersion",1);receipt.addProperty("checkRunId",checkRun);receipt.addProperty("productRunId",text(run,"runId"));receipt.addProperty("candidate",candidate.toRealPath().toString());receipt.addProperty("sessionId",owned.session().id());receipt.addProperty("machine",machine);receipt.addProperty("serial",device);receipt.addProperty("bootId",before.id());receipt.addProperty("androidPid",androidPid);receipt.addProperty("link",linkFile.toString());receipt.addProperty("operation",operation.receiptPath().toString());
            JsonArray ordered=new JsonArray();var attached=VoyemuClient.Session.attached(owned.session().id());
            JsonObject firstStatus=inventoryNative(client.status(id(),profile,attached),receipt,"beforeStatus",ordered,"before-status");requireOwnedStatus(firstStatus);
            JsonObject firstSystem=inventoryNative(client.systemRead(id(),profile,attached,5000),receipt,"beforeSystem",ordered,"before-system");
            inventoryCommand(receipt,"inventory","package-inventory",ordered);inventoryCommand(receipt,"list","package-list",ordered);
            JsonObject lastSystem=inventoryNative(client.systemRead(id(),profile,attached,5000),receipt,"afterSystem",ordered,"after-system");
            JsonObject lastStatus=inventoryNative(client.status(id(),profile,attached),receipt,"afterStatus",ordered,"after-status");requireOwnedStatus(lastStatus);
            for(JsonObject system:List.of(firstSystem,lastSystem)){JsonObject value=object(system,"value");if(!"ready".equals(text(system,"status"))||!before.id().equals(text(system,"boot_id"))||!before.id().equals(text(value,"boot_id"))||androidPid!=integer(value,"owned_android_pid"))throw new IOException("inventory boot fence changed");}
            if(!machine.equals(text(firstStatus,"machine_seed"))||!machine.equals(text(lastStatus,"machine_seed")))throw new IOException("inventory machine changed");
            var capture=ru.voboost.tests.TargetInventory.capture(checkRun,owned.session().id(),device,before.id(),Path.of(text(receipt,"inventoryBody")));ru.voboost.tests.TargetInventory.crossCheck(capture,Path.of(text(receipt,"listBody")));
            receipt.add("orderedEvents",ordered);Path file=work.resolve("target-inventory-"+(++counter)+".json");RunFiles.write(file,receipt);List<Path> evidence=new ArrayList<>();evidence.add(file);evidence.add(linkFile);
            for(String key:receipt.keySet())if(key.endsWith("Request")||key.endsWith("Output")||key.endsWith("Error")||key.endsWith("Body")||key.endsWith("Execution")||key.startsWith("raw")||Set.of("beforeStatus","beforeSystem","afterStatus","afterSystem").contains(key))evidence.add(Path.of(text(receipt,key)));
            operation.complete(evidence.toArray(Path[]::new));return file;
        }
    }
    public JsonObject status() throws Exception { return status(null); }
    private JsonObject status(CliProcess.Control control) throws Exception {
        JsonObject result = json(output(runRead(List.of("--status"), Duration.ofSeconds(30), control)));
        if (!device.equals(text(result, "deviceSerial"))) throw new IOException("installer status targets another device");
        return result;
    }

    /** Records the missing early install capability before any clean-cycle mutation is admitted. */
    public Path initialSeedingAdmission() throws Exception {
        if (productRun == null || !productRun.owns(owned) || productRun.pending() != null) {
            throw new IOException("initial seeding requires the idle retained session owner");
        }
        JsonObject identity = nativeRead(client.status(id(), profile, owned.session()),
                "initial-seeding-identity");
        JsonObject android = object(object(identity, "deliveries"), "android");
        String machine = text(identity, "machine_seed");
        if (!owned.session().id().equals(text(identity, "session_id"))
                || !truth(identity, "ok") || !falseValue(identity, "stopped")
                || integer(identity, "android_pid") != androidPid
                || integer(android, "pid") != androidPid
                || !device.equals(text(android, "serial")) || !Path.of(machine).isAbsolute()) {
            throw new IOException("initial seeding admission crossed its owned machine endpoint");
        }
        JsonObject proof = InitialSeedingAdmission.blocked(owned.session().id(), machine,
                device, manifest);        if(candidateCatalogEvidence!=null) {
            try {
                var scope=new InitialSeedingProgram.Scope(owned.session().id(),machine,device,manifest);captureInitialSeeding(scope,boot(null).id());
                proof.addProperty("semanticFunctionalStatus","ADMITTED");proof.addProperty("reason","Selected verified package and actual fixed T2 receipt/current-writer seal are observable; every future clean cycle still requires its own receipt");
            }catch(IOException unavailable){proof.addProperty("reason","Actual early capture unavailable: "+unavailable.getMessage());}
        }

        Path file = work.resolve("initial-seeding-admission-" + (++counter) + ".json");
        RunFiles.write(file, proof);
        return file;
    }

    private InitialSeedingAdmission.Capture captureInitialSeeding(InitialSeedingProgram.Scope scope,String expectedBoot)throws Exception {
        long deadline=System.nanoTime()+Duration.ofSeconds(35).toNanos();IOException last=null;
        while(System.nanoTime()<deadline) {
            BootData before=boot(null);if(!expectedBoot.equals(before.id())||!before.complete()||!before.enforcing())throw new IOException("T2 capture crossed its ready Enforcing boot");
            JsonObject observed=json(output(run(List.of("--observe"),Duration.ofSeconds(30),null,null)));Path observation=work.resolve("initial-seeding-official-"+(++counter)+".json");RunFiles.write(observation,observed);
            JsonObject data=object(observed,"data"),file=object(object(data,"files"),"initial-seeding");
            try {
                if(integer(observed,"schema")!=2||!"voboost".equals(text(observed,"source"))||!"ready".equals(text(observed,"status"))||!expectedBoot.equals(text(observed,"boot_id"))||!"present".equals(text(data,"root"))||!"fixed-path-owner-mode-boot-fenced".equals(text(data,"root_proof"))||!"present".equals(text(file,"status"))||!truth(file,"current")||!"same-boot-retained-file".equals(text(file,"proof"))||!falseValue(file,"current_writer_verified"))throw new IOException("fixed root early capture is unavailable or historical");
                java.time.Instant start=java.time.Instant.now();var logs=collectLogs(expectedBoot);java.time.Instant end=java.time.Instant.now();
                var capture=InitialSeedingAdmission.capture(object(file,"data"),scope,expectedBoot,logs.writerId(),manifest,observation,logs);
                BootData after=boot(null);if(!expectedBoot.equals(after.id())||!after.complete()||!after.enforcing())throw new IOException("T2 capture crossed current writer boot");
                JsonObject evidence=new JsonObject();evidence.addProperty("officialObservationPath",observation.toString());evidence.addProperty("logsArchive",logs.archive().toString());evidence.addProperty("logsDirectory",logs.archive().getParent().toString());evidence.addProperty("logsStartedAt",start.toString());evidence.addProperty("logsEndedAt",end.toString());evidence.addProperty("boot",expectedBoot);evidence.addProperty("writer",logs.writerId());evidence.addProperty("transaction",manifest);
                seedingCaptures.put(expectedBoot,capture);seedingCaptureEvidence.put(expectedBoot,evidence);return capture;
            }catch(IOException pending){last=pending;}
            Thread.sleep(250);
        }
        throw new IOException("actual early current-writer T2 capture did not converge; no install replay",last);
    }
    private static JsonArray paths(List<Path> paths){JsonArray values=new JsonArray();for(Path path:paths)values.add(path.toString());return values;}

    private InitialSeedingProgram.Absence initialSeedingRemove(
            InitialSeedingProgram.Scope scope, int cycle) throws Exception {
        String phase = SeedingPhases.cycle(cycle);
        uninstall(phase);
        BootData before = boot(null);
        Path independent = requireAbsentEvidence();
        BootData after = boot(null);
        if (!before.id().equals(after.id()) || !after.complete() || !after.enforcing()) {
            throw new IOException("initial seeding root absence crossed its normal removal boot");
        }
        return new InitialSeedingProgram.Absence(scope, after.id(),
                true, true, true, List.of(work.resolve(phase + "-absence.raw.json"), independent));
    }

    private void initialSeedingSelectOem(
            InitialSeedingProgram.Baseline baseline) throws Exception {
        fevSelectOem(baseline.forcedEv());
        vehicleSelect(ru.voboost.voyemu.VehicleUi.Feature.PEDESTRIAN_WARNING,
                baseline.pedestrianSound());
    }

    private InitialSeedingProgram.Oem initialSeedingReadOem(
            InitialSeedingProgram.Scope scope) throws Exception {
        BootData before = boot(null);
        var fev = vehicleObserve(ru.voboost.voyemu.VehicleUi.Feature.FORCED_EV);
        var pedestrian = vehicleObserve(ru.voboost.voyemu.VehicleUi.Feature.PEDESTRIAN_WARNING);
        fev.require(true, fev.checked());
        pedestrian.require(false, pedestrian.checked());
        BootData after = boot(null);
        if (!before.id().equals(after.id()) || !after.complete() || !after.enforcing()) {
            throw new IOException("initial OEM readback crossed its pre-install boot");
        }
        return new InitialSeedingProgram.Oem(scope, after.id(),
                new InitialSeedingProgram.Baseline(
                        fev.checked(), pedestrian.checked()),
                List.of(fev.evidence(), pedestrian.evidence()));
    }

    private InitialSeedingProgram.RootRead initialSeedingRoot(
            InitialSeedingProgram.Scope scope, String expectedBoot)
            throws Exception {
        BootData before = boot(null);
        if (!expectedBoot.equals(before.id()) || !before.complete() || !before.enforcing()) {
            throw new IOException("initial seeding root read requires its installed Enforcing boot");
        }
        JsonObject observation = json(output(run(List.of("--observe"), Duration.ofSeconds(30),
                null, null)));
        boolean fev = ru.voboost.voyemu.VehicleArmed.read(observation, expectedBoot);
        boolean pedestrian = ru.voboost.voyemu.VehicleArmed.readPedestrian(observation, expectedBoot);
        BootData after = boot(null);
        if (!expectedBoot.equals(after.id()) || !after.complete() || !after.enforcing()) {
            throw new IOException("initial seeding independent root read crossed boot");
        }
        Path file = work.resolve("initial-seeding-root-" + (++counter) + ".json");
        RunFiles.write(file, observation);
        return new InitialSeedingProgram.RootRead(scope, expectedBoot,
                fev, pedestrian, List.of(file));
    }

    private List<Path> restoreInitialSeedingSuite(JsonObject candidateCatalog,
            JsonObject originalAgents, String startup, String runningStartup) throws Exception {
        InitialSeedingAdmission.requireRestorableConfiguration(candidateCatalog, originalAgents, startup,
                runningStartup);
        uninstall("seed-restore");
        Path restoreAbsence=requireAbsentEvidence();
        install("seed-restore");
        List<Path> evidence = new ArrayList<>();
        evidence.add(work.resolve("seed-restore-absence.raw.json"));
        evidence.add(verifySettingsTab());
        for (boolean enabled : List.of(false, true)) {
            for (String agent : originalAgents.keySet()) {
                if (originalAgents.getAsJsonObject(agent).get("enabled").getAsBoolean() == enabled) {
                    evidence.add(setAgent(candidateCatalog, agent, enabled));
                }
            }
        }
        var current = observeRuntime(text(status(), "activeGeneration"));
        if (!startup.equals(current.startup())) {
            evidence.add(selectStartup(startup));
        }
        current = observeRuntime(text(status(), "activeGeneration"));
        if (!runningStartup.equals(current.runningStartup())) {
            rebootAndVerify();
        }
        requireHealthy();
        var restored = observeRuntime(text(status(), "activeGeneration"));
        if (!startup.equals(restored.startup()) || !runningStartup.equals(restored.runningStartup())
                || !originalAgents.equals(ru.voboost.tests.agents.AgentSettings.snapshot(
                        candidateCatalog, restored))) {
            throw new IOException("normal seeding restoration did not retain complete suite config");
        }
        JsonObject proof = new JsonObject();
        proof.addProperty("startup", startup);
        proof.addProperty("runningStartup", runningStartup);
        proof.add("agents", originalAgents);proof.addProperty("restoreInstallOperation",seedingInstallOperations.get("seed-restore").toString());proof.addProperty("restoreUninstallOperation",seedingUninstallOperations.get("seed-restore").toString());proof.addProperty("restoreRootAbsenceEvidence",restoreAbsence.toString());proof.addProperty("restoreUninstallReceipt",work.resolve("seed-restore-absence.raw.json").toString());
        proof.addProperty("runtimeOfficialEvidence",lastRuntimeOfficial.toString());proof.addProperty("bootId",boot(null).id());
        Path file = work.resolve("initial-seeding-suite-restored-" + (++counter) + ".json");
        RunFiles.write(file, proof);
        evidence.add(file);
        return List.copyOf(evidence);
    }

    /** Concrete normal lifecycle port; missing early capture is rejected before any mutation. */
    public Path verifyInitialSeeding(JsonObject candidateCatalog) throws Exception {
        Path admissionFile = initialSeedingAdmission();
        JsonObject proof = RunFiles.read(admissionFile);
        if ("BLOCKED".equals(text(proof, "semanticFunctionalStatus"))) {
            // Known missing capture does not probe optional case24 prerequisites or mutate.
            InitialSeedingAdmission.requireUnmutatedBlocked(proof);
            return admissionFile;
        }
        var scope = new InitialSeedingProgram.Scope(
                text(proof, "session"), text(proof, "machine"), device, manifest);
        var original = observeRuntime(text(status(), "activeGeneration"));
        JsonObject agents = ru.voboost.tests.agents.AgentSettings.snapshot(candidateCatalog, original);
        Path originalRuntime=lastRuntimeOfficial;
        String reason = "Actual source-bound early capture and normal cleanup/install/OEM/restoration authority";
        boolean restorable=true;
        try {
            InitialSeedingAdmission.requireRestorableConfiguration(candidateCatalog, agents, original.startup(),
                    original.runningStartup());
        } catch (IOException unavailable) {
            reason += "; " + unavailable.getMessage();restorable=false;
        }
        boolean admitted=restorable&&"ADMITTED".equals(text(RunFiles.read(admissionFile),"semanticFunctionalStatus"));
        String admissionReason = reason;
        var result = InitialSeedingProgram.run(
                new InitialSeedingProgram.Port() {
                    public InitialSeedingProgram.Scope scope() {
                        return scope;
                    }
                    public InitialSeedingProgram.Admission admit() {
                        return new InitialSeedingProgram.Admission(
                                admitted, admissionReason, List.of(admissionFile));
                    }
                    public InitialSeedingProgram.Absence
                            removeNormallyAndVerify(int cycle) throws Exception {
                        return initialSeedingRemove(scope, cycle);
                    }
                    public void selectOem(
                            InitialSeedingProgram.Baseline baseline)
                            throws Exception {
                        initialSeedingSelectOem(baseline);
                    }
                    public InitialSeedingProgram.Oem readOem()
                            throws Exception {
                        return initialSeedingReadOem(scope);
                    }
                    public InitialSeedingProgram.Installed
                            installSameCandidate(int cycle) throws Exception {
                        String phase=SeedingPhases.cycle(cycle);install(phase);return captureInitialSeeding(scope,boot(null).id()).installed();
                    }
                    public InitialSeedingProgram.FirstRead firstRead(
                            InitialSeedingProgram.Installed installed)
                            throws Exception {
                        var capture=seedingCaptures.get(installed.boot());if(capture==null)throw new IOException("early capture was not retained for this install boot");return capture.first();
                    }
                    public InitialSeedingProgram.RootRead readRoot(
                            InitialSeedingProgram.Installed installed)
                            throws Exception {
                        return initialSeedingRoot(scope, installed.boot());
                    }
                    public List<Path> restoreSuiteInstallationAndConfig() throws Exception {
                        return restoreInitialSeedingSuite(candidateCatalog, agents,
                                original.startup(), original.runningStartup());
                    }
                });
        proof.addProperty("reason", result.reason());
        proof.addProperty("mutationStarted", result.started());
        proof.addProperty("semanticFunctionalStatus", result.semanticComplete() ? "PASS" : "BLOCKED");        proof.addProperty("functionalAcceptance",result.semanticComplete());proof.addProperty("releaseIdentity",version);proof.addProperty("catalogIdentity",catalog);proof.addProperty("candidateCatalogPath",candidateCatalogEvidence.toString());proof.addProperty("candidateManifestPath",candidateManifestEvidence.toString());proof.addProperty("originalRuntimeEvidence",originalRuntime.toString());
        JsonObject scopeJson=new JsonObject();scopeJson.addProperty("session",scope.session());scopeJson.addProperty("machine",scope.machine());scopeJson.addProperty("serial",scope.serial());scopeJson.addProperty("candidate",scope.candidate());proof.add("scope",scopeJson);JsonArray samples=new JsonArray();int sampleIndex=0;
        for(var sample:result.samples()) {
            JsonObject row=new JsonObject(),baseline=new JsonObject(),absence=new JsonObject(),oem=new JsonObject();baseline.addProperty("forcedEv",sample.baseline().forcedEv());baseline.addProperty("pedestrianSound",sample.baseline().pedestrianSound());row.add("baseline",baseline);
            String samplePhase=SeedingPhases.cycle(sampleIndex++);absence.addProperty("uninstallOperation",seedingUninstallOperations.get(samplePhase).toString());absence.addProperty("boot",sample.absence().boot());absence.addProperty("receipt",sample.absence().evidence().get(0).toString());absence.addProperty("rootObservationPath",sample.absence().evidence().get(1).toString());row.add("absence",absence);
            oem.addProperty("boot",sample.oem().boot());oem.addProperty("forcedEv",sample.oem().value().forcedEv());oem.addProperty("pedestrianSound",sample.oem().value().pedestrianSound());oem.addProperty("forcedEvEvidence",sample.oem().evidence().get(0).toString());oem.addProperty("pedestrianEvidence",sample.oem().evidence().get(1).toString());row.add("oem",oem);
            row.add("capture",seedingCaptureEvidence.get(sample.installed().boot()).deepCopy());row.addProperty("rootObservationPath",sample.root().evidence().get(0).toString());
            row.addProperty("installOperation",seedingInstallOperations.get(samplePhase).toString());samples.add(row);
        }
        proof.add("samples",samples);if(result.semanticComplete())proof.addProperty("restorationEvidence",result.evidence().get(result.evidence().size()-1).toString());

        Path file = work.resolve("initial-seeding-program-" + (++counter) + ".json");
        RunFiles.write(file, proof);
        return file;
    }

    static void requireVehicleStartAt(String stage,boolean vehicleOnly,String preparation)throws IOException {
        if(Set.of("pw-icon","pw-readiness").contains(stage)&&vehicleOnly&&Set.of("require-absent","uninstall","verify-installed","reapply").contains(preparation))return;
        if(!"full".equals(stage)&&!(Set.of("pw-icon","fev","fev-oem","fev-finish","remove").contains(stage)&&vehicleOnly&&"verify-installed".equals(preparation)))
            throw new IOException("vehicle step selection requires the verified existing-kit focused route");
    }
    static void requirePreparationMode(String mode,boolean vehicleOnly)throws IOException {
        if(!Set.of("require-absent","uninstall","verify-installed","reapply").contains(mode))throw new IOException("unknown preparation mode");
        if(Set.of("verify-installed","reapply").contains(mode)&&!vehicleOnly)throw new IOException("existing-kit continuation is restricted to vehicle-only");
    }
    static String reapplyTerminal(String stdout,String priorBoot,String currentBoot,JsonObject operation)throws IOException {
        Matcher terminal=Pattern.compile("Install completed: transaction ([0-9a-f]{64})\\n").matcher(stdout);
        if(!terminal.matches()||!uuid(priorBoot)||!uuid(currentBoot)||priorBoot.equals(currentBoot))throw new IOException("normal reapply lacks its exact terminal transaction and changed boot");
        String transaction=terminal.group(1);
        if(integer(operation,"schemaVersion")!=1||!"update".equals(text(operation,"operation"))||!"complete".equals(text(operation,"outcome"))
                ||!transaction.equals(text(operation,"transactionId"))||!currentBoot.equals(text(operation,"bootId"))
                ||integer(operation,"rebootCount")<1||integer(operation,"rebootCount")>2||operation.has("error")&&!operation.get("error").isJsonNull())
            throw new IOException("normal reapply terminal differs from the current native operation");
        return transaction;
    }
    static String updateTerminal(String stdout, String priorBoot, String boot, JsonObject operation)
            throws IOException {
        Matcher terminal = Pattern.compile("Update completed: transaction ([0-9a-f]{64})\n").matcher(stdout);
        requireInstalledTerminal(operation);
        long reboots = integer(operation, "rebootCount");
        if (!terminal.matches() || !uuid(priorBoot) || !uuid(boot) || reboots < 0 || reboots > 2
                || (priorBoot.equals(boot) != (reboots == 0))
                || !"update".equals(text(operation, "operation"))
                || !boot.equals(text(operation, "bootId"))
                || !terminal.group(1).equals(text(operation, "transactionId"))) {
            throw new IOException("update terminal differs from the current native operation");
        }
        return terminal.group(1);
    }

    private static final List<String> REAPPLY_ARGS = List.of("--install", "--force");
    private static final List<String> UPDATE_ARGS = List.of("--update");
    private static Path reapplyProcessResult(Path stdout) { return stdout.resolveSibling(stdout.getFileName()+".exit.json"); }

    static String reapplyReceipt(JsonObject input,JsonObject completed,String stdout,String boot,JsonObject nativeOperation)throws IOException {
        return applyReceipt(input, completed, stdout, boot, nativeOperation, true);
    }

    private static String applyReceipt(JsonObject input, JsonObject completed, String stdout, String boot,
            JsonObject nativeOperation, boolean reapply) throws IOException {
        String selected = text(input, "binary");
        String device = text(input, "device");
        if (!(reapply ? "vehicle-reapply" : "offline-update").equals(text(input, "phase"))
                || !Path.of(selected).isAbsolute() || !emulator(device)) {
            throw new IOException("retained update selected binary or endpoint differs");
        }
        JsonArray command = new JsonArray();
        command.add(selected);
        for (String argument : reapply ? REAPPLY_ARGS : UPDATE_ARGS) command.add(argument);
        command.add("--device");
        command.add(device);
        JsonObject expected = new JsonObject();
        expected.addProperty("schemaVersion", 1);
        expected.add("command", command);
        expected.addProperty("exit", 0);
        if (!command.equals(object(input, "process").get("command")) || !expected.equals(completed)) {
            throw new IOException("retained update lacks its successful selected CLI command");
        }
        return reapply ? reapplyTerminal(stdout, text(input, "priorBoot"), boot, nativeOperation)
                : updateTerminal(stdout, text(input, "priorBoot"), boot, nativeOperation);
    }
    static void requireInstalledTerminal(JsonObject operation)throws IOException {
        String kind=text(operation,"operation"),phase="install".equals(kind)?"install-complete":"update-product-complete";
        if(integer(operation,"schemaVersion")!=1||!Set.of("install","update").contains(kind)
                ||!"complete".equals(text(operation,"outcome"))||!phase.equals(text(operation,"phase"))
                ||!text(operation,"transactionId").matches("[0-9a-f]{64}")||!uuid(text(operation,"bootId"))
                ||!operation.has("error")||!operation.get("error").isJsonNull())
            throw new IOException("current native installed operation is not terminal; no functional mutation admitted");
    }
    public Path observeCompletedInstalledOperation()throws Exception {
        BootData before=boot(null);requireInstalledTerminal(before.operation());
        BootData after=boot(null);requireInstalledTerminal(after.operation());
        if(!before.complete()||!before.enforcing()||!after.complete()||!after.enforcing()||!before.id().equals(after.id())
                ||!text(before.operation(),"transactionId").equals(text(after.operation(),"transactionId")))
            throw new IOException("current native terminal observation crossed boot, policy or transaction");
        JsonObject proof=new JsonObject();proof.addProperty("kind","existing-native-terminal-observation");
        proof.addProperty("bootId",after.id());proof.addProperty("before",before.evidence().toString());proof.addProperty("after",after.evidence().toString());
        proof.addProperty("functionalAcceptance",false);
        Path file=work.resolve("existing-native-terminal-"+(++counter)+".json");RunFiles.write(file,proof);return file;
    }
    public Path reapplyVehiclePackage() throws Exception {
        return applyVehiclePackage(true);
    }

    public Path updateVehiclePackage() throws Exception {
        return applyVehiclePackage(false);
    }

    private static final class UpdateDisconnected extends IOException {
        UpdateDisconnected() { super("intentional loss of the desktop update session"); }
    }

    public Path updateAcrossClientDisconnect(boolean reapply) throws Exception {
        String phase = reapply ? "vehicle-reapply" : "offline-update";
        BootData before = boot(null);
        if (!before.complete() || !before.enforcing()) throw new IOException("update requires a ready Enforcing boot");
        JsonObject input = new JsonObject();
        input.addProperty("manifest", manifest); input.addProperty("device", device);
        input.addProperty("phase", phase); input.addProperty("binary", binary.toString());
        List<String> command = new ArrayList<>(); command.add(binary.toString());
        command.addAll(reapply ? REAPPLY_ARGS : UPDATE_ARGS); command.add("--device"); command.add(device);
        Path firstOut = work.resolve("update-disconnected-" + (++counter) + ".out");
        Path firstErr = work.resolve("update-disconnected-" + counter + ".err");
        BootData[] boundary = new BootData[1];
        try (ProductRun.Operation operation = productRun.reserve(ProductRun.Kind.UPDATE, input)) {
            operation.observePriorBoot(before.id()); operation.dispatch(); operation.process(command, firstOut, firstErr);
            try {
                CliProcess.run(command, firstOut, firstErr, Duration.ofMinutes(10), (stream, line, control) -> {
                    if (stream == CliProcess.Stream.OUT && line.contains("Preparing updated startup files and SELinux permissions")) {
                        BootData current = boot(control);
                        if (!"update".equals(text(current.operation(), "operation"))
                                || "complete".equals(text(current.operation(), "outcome")))
                            throw new IOException("disconnect boundary is not an unfinished update during system preparation");
                        boundary[0] = current;
                        throw new UpdateDisconnected();
                    }
                    return null;
                });
                throw new IOException("update did not expose the selected pre-reboot disconnect boundary");
            } catch (UpdateDisconnected expected) {
                if (expected.getSuppressed().length != 0) throw expected;
            }
            if (boundary[0] == null) throw new IOException("missing durable update boundary");
            Path out = work.resolve("update-resumed-" + (++counter) + ".out");
            Path err = work.resolve("update-resumed-" + counter + ".err");
            CliProcess.Result resumed = CliProcess.run(command, out, err, Duration.ofMinutes(10), (stream, line, control) -> null);
            if (resumed.exit() != 0) throw new IOException("resumed update failed; raw outputs retained");
            BootData after = boot(null); requireInstalledTerminal(after.operation());
            if (!after.complete() || !after.enforcing() || before.id().equals(after.id())
                    || !text(boundary[0].operation(), "transactionId").equals(text(after.operation(), "transactionId")))
                throw new IOException("resumed update changed transaction or lacks a ready Enforcing terminal");
            if (Files.readString(out).contains("Sending the verified installation files"))
                throw new IOException("resumed update retransferred the installation package");
            requireHealthy();
            JsonObject proof = new JsonObject(); proof.addProperty("kind", "update-client-disconnect");
            proof.addProperty("force", reapply); proof.addProperty("transaction", text(after.operation(), "transactionId"));
            proof.addProperty("beforeBoot", before.id()); proof.addProperty("interruptedBoot", boundary[0].id());
            proof.addProperty("terminalBoot", after.id()); proof.addProperty("exit", resumed.exit());
            Path file = work.resolve("update-client-disconnect-" + (++counter) + ".json"); RunFiles.write(file, proof);
            operation.complete(firstOut, firstErr, boundary[0].evidence(), out, err, after.evidence(), file);
            return file;
        }
    }

    private Path applyVehiclePackage(boolean reapply) throws Exception {
        String phase = reapply ? "vehicle-reapply" : "offline-update";
        if(productRun==null||!productRun.owns(owned))throw new IOException("update requires the current exclusive owner");
        BootData before=boot(null);if(!before.complete()||!before.enforcing())throw new IOException("normal reapply needs a ready Enforcing initial boot");
        JsonObject prior=status();Path priorFile=work.resolve("reapply-prior-status-"+(++counter)+".json");RunFiles.write(priorFile,prior);
        JsonObject input=new JsonObject();input.addProperty("manifest",manifest);input.addProperty("device",device);input.addProperty("phase",phase);input.addProperty("binary",binary.toString());
        try(ProductRun.Operation operation=productRun.reserve(ProductRun.Kind.UPDATE,input)) {
            operation.observePriorBoot(before.id());operation.dispatch();
            CliProcess.Result result=run(reapply ? REAPPLY_ARGS : UPDATE_ARGS, Duration.ofMinutes(10), null, null);
            JsonObject completed=new JsonObject();completed.addProperty("schemaVersion",1);completed.add("command",object(operation.input(),"process").get("command").deepCopy());completed.addProperty("exit",result.exit());
            Path exitFile=reapplyProcessResult(result.out());RunFiles.write(exitFile,completed);
            if(result.exit()!=0)throw new IOException("normal reapply failed; retained transaction is not replayed");
            BootData after=boot(null);if(!after.complete()||!after.enforcing())throw new IOException("normal reapply terminal boot is not ready Enforcing");
            String transaction=applyReceipt(operation.input(),completed,output(result),after.id(),after.operation(),reapply);
            CliProcess.Control control=new CliProcess.Control(System.nanoTime()+Duration.ofMinutes(3).toNanos(),null);
            try{verifyNativeBoot(after.id(),control);}finally{control.close();}
            requireHealthy();BootData fence=boot(null);if(!after.id().equals(fence.id())||!fence.complete()||!fence.enforcing())throw new IOException("normal reapply verification crossed boot or policy");
            applyReceipt(operation.input(),completed,output(result),fence.id(),fence.operation(),reapply);
            JsonObject proof=new JsonObject();proof.addProperty("kind",phase);proof.addProperty("transaction",transaction);
            proof.addProperty("priorBoot",before.id());proof.addProperty("bootId",fence.id());proof.addProperty("manifest",manifest);
            proof.addProperty("priorStatus",priorFile.toString());proof.addProperty("runtime",lastRuntimeOfficial.toString());proof.addProperty("nativeOperation",fence.evidence().toString());
            proof.addProperty("stdout",result.out().toString());proof.addProperty("stderr",result.err().toString());proof.addProperty("processExit",exitFile.toString());
            Path file=work.resolve(phase+"-"+(++counter)+".json");RunFiles.write(file,proof);
            operation.complete(priorFile,result.out(),result.err(),exitFile,fence.evidence(),lastRuntimeOfficial,file);return file;
        }
    }

    public void prepare(String preparation) throws Exception {
        JsonObject initial = status();
        Files.writeString(work.resolve("preparation.json"), initial.toString() + "\n");
        if ("absent".equals(text(initial, "route")) && !"uninstall".equals(preparation)) { requireAbsent(); return; }
        if (!"uninstall".equals(preparation)) throw new IOException("BLOCKED: initial product is not absent; explicit prepareExisting=uninstall required");
        uninstall("preparation");
        preparationRemovedProduct=!"absent".equals(text(initial,"route"));
        requireAbsent();
    }

    static boolean stockColdRequired(boolean removed,String mode){return removed||!Set.of("cold","sdk_cold_fallback").contains(mode);}
    private void stockReferenceColdAfterRemoval()throws Exception {
        JsonObject current=nativeRead(client.status(id(),profile,owned.session()),"stock-reference-boot-mode");requireOwnedStatus(current);String mode=text(object(object(object(current,"deliveries"),"android"),"quickboot"),"mode");if(!stockColdRequired(preparationRemovedProduct,mode))return;BootData before=boot(null);requireAbsentEvidence();
        try(ProductRun.Operation operation=reserve(ProductRun.Kind.REBOOT,"stock-reference-cold-after-restore-or-removal")) {
            operation.observePriorBoot(before.id());operation.dispatch();JsonObject receipt=owned.reboot(true);String after=text(receipt,"boot_id");
            if(!"completed".equals(text(receipt,"status"))||!"guest_boot_changed".equals(text(receipt,"scope"))||!before.id().equals(text(receipt,"boot_id_before"))||!uuid(after)||after.equals(before.id())||integer(receipt,"android_pid_before")!=androidPid||integer(receipt,"android_pid")<1)throw new IOException("stock baseline cold boot receipt differs");androidPid=integer(receipt,"android_pid");
            CliProcess.Control control=new CliProcess.Control(System.nanoTime()+Duration.ofMinutes(3).toNanos(),null);try{verifyNativeBoot(after,control);}finally{control.close();}
            BootData actual=boot(null);if(!after.equals(actual.id())||!actual.complete()||!actual.enforcing())throw new IOException("stock baseline cold boot lacks actual ready Enforcing identity");Path evidence=work.resolve("stock-reference-cold-"+(++counter)+".json");RunFiles.write(evidence,receipt);Path absent=requireAbsentEvidence();operation.complete(evidence,absent);preparationRemovedProduct=false;
        }
    }
    private JsonObject stockSystemActions(String boot)throws Exception {
        owned.launch("com.qinggan.app.setting",boot,"primary");systemMenu(boot);long deadline=System.nanoTime()+Duration.ofSeconds(30).toNanos();int scrolls=0;
        while(System.nanoTime()<deadline) {
            JsonObject measured=settingsUi(boot,System.nanoTime()+Duration.ofSeconds(5).toNanos(),"all_attached"),surface=ru.voboost.voyemu.UiEvidence.surface(measured,"primary");JsonObject reset=null,scroll=null;
            for(JsonElement entry:surface.getAsJsonArray("nodes")){JsonObject node=entry.getAsJsonObject();if(!"com.qinggan.app.setting".equals(text(node,"package")))continue;String resource=text(node,"resource_id");if(resource.equals("com.qinggan.app.setting:id/btn_factory_reset")){if(reset!=null)throw new IOException("ambiguous stock reset caption");reset=node;}if(resource.equals("com.qinggan.app.setting:id/system_scroll")){if(scroll!=null)throw new IOException("ambiguous stock System scroll");scroll=node;}}
            if(reset==null||scroll==null)throw new IOException("original stock System controls/scroll identity unavailable");
            JsonObject parent=reset;boolean inside=false;Set<String> visited=new java.util.HashSet<>();while(parent!=null&&visited.add(text(parent,"id"))&&visited.size()<128){if(text(parent,"id").equals(text(scroll,"id"))){inside=true;break;}String id=text(parent,"parent");parent=null;for(JsonElement entry:surface.getAsJsonArray("nodes"))if(id.equals(text(entry.getAsJsonObject(),"id"))){parent=entry.getAsJsonObject();break;}}
            if(!inside)throw new IOException("stock reset caption is not inside the original System scroll");if(truth(reset,"visible")&&object(reset,"bounds").equals(object(reset,"clipped_bounds"))&&integer(object(reset,"bounds"),"width")>0&&integer(object(reset,"bounds"),"height")>0)return measured;
            if(scrolls>=8)throw new IOException("stock reset caption did not become fully visible within eight observed scrolls");JsonObject input=settingsUi(boot,System.nanoTime()+Duration.ofSeconds(5).toNanos());ru.voboost.voyemu.UiTarget target=null;try{target=ru.voboost.voyemu.UiTarget.selectScrollable(input,profile,owned.session(),"primary",node->"com.qinggan.app.setting".equals(text(node,"package"))&&"com.qinggan.app.setting:id/system_scroll".equals(text(node,"resource_id")));}catch(IOException unresolved){ /* Selection only; no input has been dispatched. */ }
            if(target!=null){owned.scrollSettings(target);scrolls++;}else Thread.sleep(100);
        }
        throw new IOException("stock System caption viewport unavailable; reset/update actions were never pressed");
    }
    private ru.voboost.voyemu.StockSettingsBaseline.Authority stockReferenceAuthority()throws Exception {
        BootData before=boot(null);if(!before.complete()||!before.enforcing())throw new IOException("stock baseline requires actual ready Enforcing boot");JsonObject nativeStatus=nativeRead(client.status(id(),profile,owned.session()),"stock-reference-status");requireOwnedStatus(nativeStatus);
        JsonObject management=status();Path absence=requireAbsentEvidence();JsonObject raw=RunFiles.read(absence);BootData after=boot(null);if(!before.id().equals(after.id())||!after.complete()||!after.enforcing())throw new IOException("stock baseline absence crossed boot/policy");JsonObject proof=new JsonObject();proof.addProperty("bootId",before.id());proof.addProperty("androidPid",androidPid);proof.add("management",management);proof.add("machineStatus",nativeStatus);proof.add("rootAbsence",raw);proof.addProperty("processIdentities",startupProcesses(before.id()));proof.add("localeObservation",localeObservation(before.id()));Path file=work.resolve("stock-reference-authority-"+(++counter)+".json");RunFiles.write(file,proof);
        String mode=text(object(object(object(nativeStatus,"deliveries"),"android"),"quickboot"),"mode");return new ru.voboost.voyemu.StockSettingsBaseline.Authority(profile,owned.session().id(),before.id(),Math.toIntExact(androidPid),mode,management,raw,file);
    }
    /** Candidate acquisition only: no installation and no automatic baseline approval. */
    public Path collectStockSettingsBaselines()throws Exception {
        if(productRun==null||!productRun.owns(owned)||productRun.pending()!=null||voyemuRepository==null)throw new IOException("idle current owned source-bound stock baseline required");requireAbsentEvidence();stockReferenceColdAfterRemoval();BootData initial=boot(null);String original=text(localeObservation(initial.id()),"language");if(!Set.of("en","zh").contains(original))throw new IOException("stock baseline has no restorable original EN/ZH locale");
        Path artifacts=voyemuRepository.resolve(".local/build/tmp/voboost-stock-"+owned.session().id()+"-"+UUID.randomUUID());Files.createDirectories(artifacts.getParent());Files.createDirectory(artifacts,java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rwx------")));JsonArray candidates=new JsonArray();
        for(String language:List.of("en","zh")) {
            String selected=text(localeObservation(initial.id()),"language");if(!language.equals(selected))stockReferenceLocale(initial.id(),language);
        for(var viewport:ru.voboost.voyemu.StockSettingsBaseline.Viewport.values()) {
            var candidate=ru.voboost.voyemu.StockSettingsBaseline.collect(new ru.voboost.voyemu.StockSettingsBaseline.Port(){
                public ru.voboost.voyemu.StockSettingsBaseline.Authority authority()throws Exception{return stockReferenceAuthority();}
                public Path open(ru.voboost.voyemu.StockSettingsBaseline.Viewport view)throws Exception {
                    BootData boot=boot(null);try(ProductRun.Operation operation=reserve(ProductRun.Kind.CONFIGURE,"stock-reference-navigation-"+view.name().toLowerCase(java.util.Locale.ROOT))){operation.observePriorBoot(boot.id());operation.dispatch();JsonObject tree;
                        if(view==ru.voboost.voyemu.StockSettingsBaseline.Viewport.LANGUAGE_RADIO)tree=languagePicker(boot.id());else{tree=stockSystemActions(boot.id());}
                        Path file=work.resolve("stock-reference-navigation-"+(++counter)+".json");RunFiles.write(file,tree);operation.complete(file);return file;}
                }
                public JsonObject ui()throws Exception{return settingsUi(initial.id(),System.nanoTime()+Duration.ofSeconds(10).toNanos(),"all_attached");}
                public Path captureDirectory(ru.voboost.voyemu.StockSettingsBaseline.Viewport view){return artifacts.resolve(language+"-"+view.name().toLowerCase(java.util.Locale.ROOT));}
                public JsonObject capture(Path directory)throws Exception{return nativeRead(client.capturePrimary(id(),profile,owned.session(),directory),"stock-reference-capture");}
                public Path save(String name,JsonObject proof)throws Exception{Path file=work.resolve(name+"-"+(++counter)+".json");RunFiles.write(file,proof);return file;}
            },viewport,language);candidates.add(candidate.evidence().toString());
        }
        }
        if(!original.equals(text(localeObservation(initial.id()),"language")))stockReferenceLocale(initial.id(),original);stockReferenceAuthority();JsonObject proof=new JsonObject();proof.addProperty("kind","emulator-stock-oem-reference-candidates");proof.addProperty("referenceAccepted",false);proof.addProperty("rootReviewRequired",true);proof.addProperty("hardwareFidelityAccepted",false);proof.addProperty("publicationApproval",false);proof.addProperty("originalLocaleRestored",original);proof.add("candidates",candidates);Path file=work.resolve("stock-reference-candidates-"+(++counter)+".json");RunFiles.write(file,proof);return file;
    }
    private void stockReferenceLocale(String boot,String language)throws Exception {
        try(ProductRun.Operation operation=reserve(ProductRun.Kind.CONFIGURE,"stock-reference-locale-"+language)){operation.observePriorBoot(boot);operation.dispatch();selectLanguage(boot,language);JsonObject tree=languagePicker(boot);if(!language.equals(ru.voboost.voyemu.SystemLanguagePicker.read(ru.voboost.voyemu.UiEvidence.surface(tree,"primary")).language()))throw new IOException("stock OEM locale input lacks independent checked readback");requireSystemLocale(boot,language);Path file=work.resolve("stock-reference-locale-"+(++counter)+".json");RunFiles.write(file,tree);operation.complete(file);}
    }
    public static final String VEHICLE_INSTALL_PHASE = "first";

    static void requireInstallPhase(String phase) throws IOException {
        if (!Set.of("first", "second", "previous", "reinstall", "after-settings").contains(phase) && !SeedingPhases.contains(phase)) throw new IOException("unknown install phase");
    }

    public void install(String phase) throws Exception {
        install(phase, false);
    }

    public void installForLifecycle(String phase) throws Exception {
        install(phase, true);
    }

    private void install(String phase, boolean observeFirstBootEarly) throws Exception {
        requireInstallPhase(phase);
        observeFirstInstallBootEarly = observeFirstBootEarly;
        try (ProductRun.Operation operation = reserve(ProductRun.Kind.INSTALL, phase)) {
            install(operation);
        } finally {
            observeFirstInstallBootEarly = false;
        }
    }

    private void install(ProductRun.Operation operation) throws Exception {
        BootData before = boot(null);
        if (!before.complete() || !before.enforcing()) throw new IOException("initial boot is not ready and Enforcing");
        InstallMonitor monitor = new InstallMonitor(manifest, before.id(), this);
        operation.observePriorBoot(before.id());
        operation.dispatch();
        boolean reinstall = "reinstall".equals(text(operation.input(), "phase"));
        CliProcess.Result result = run(List.of("--install"), Duration.ofMinutes(reinstall ? 25 : 10), monitor, null);
        JsonObject completed = new JsonObject();
        completed.addProperty("schemaVersion", 1);
        completed.add("command", object(operation.input(), "process").get("command").deepCopy());
        completed.addProperty("exit", result.exit());
        completed.addProperty("acceptanceVerified", false);
        Path exitFile = reapplyProcessResult(result.out());
        RunFiles.write(exitFile, completed);
        JsonObject cleanReceipt;
        try {
            monitor.requireComplete(result.exit());
            cleanReceipt = reinstall ? monitor.requireCleanPreflight() : null;
            if (reinstall && !"verified".equals(text(cleanReceipt, "qnx")))
                throw new IOException("managed QNX reinstall lacks installer-reported signed QNX removal evidence");
        } catch (IOException failure) {
            completed.addProperty("baseLifecycle", "failed");
            completed.addProperty("diagnostic", failure.toString());
            RunFiles.write(exitFile, completed);
            throw failure;
        }
        requireHealthy();
        var authority = monitor.preparationProof(device);
        JsonObject preparation = new com.google.gson.Gson().toJsonTree(authority).getAsJsonObject();
        if (cleanReceipt != null) preparation.add("installerReportedCleanPreflight", cleanReceipt);
        preparation.addProperty("finalBoot",boot(null).id());preparation.addProperty("serial",device);preparation.addProperty("manifest",manifest);preparation.addProperty("priorBoot",before.id());
        preparation.addProperty("stdout",result.out().toString());preparation.addProperty("stderr",result.err().toString());
        Path authorityFile=work.resolve("install-preparation-"+(++counter)+".json");RunFiles.write(authorityFile,preparation);
        completed.addProperty("acceptanceVerified", true);
        RunFiles.write(exitFile, completed);
        operation.complete(result.out(), result.err(), exitFile, authorityFile);
        if(pwStockBaseline!=null && before.id().equals(text(pwStockBaseline,"androidBootId"))) {
            pwInstallBridge=new JsonObject();pwInstallBridge.add("owner",pwOwner());
            pwInstallBridge.addProperty("qnxGeneration",pwSourceContext(boot(null).id(),null).generation());
            pwInstallBridge.add("operation",RunFiles.read(operation.receiptPath()));pwInstallBridge.add("preparation",preparation);
            pwInstallBridge.addProperty("operationPath",operation.receiptPath().toString());pwInstallBridge.addProperty("preparationPath",authorityFile.toString());
        }
        lastInstallOperation=operation.receiptPath();String phase=text(operation.input(),"phase");if(SeedingPhases.contains(phase))seedingInstallOperations.put(phase,lastInstallOperation);
    }

    static JsonArray installCommand(JsonObject input) throws IOException {
        JsonObject process = object(input, "process");
        var value = process.get("command");
        List<String> expected = List.of("--install", "--device", text(input, "device"));
        if (!emulator(text(input, "device")) || value == null || !value.isJsonArray()
                || value.getAsJsonArray().size() != expected.size() + 1) {
            throw new IOException("retained install process binding differs");
        }
        JsonArray command = value.getAsJsonArray();
        for (int i = 0; i < command.size(); i++) {
            var argument = command.get(i);
            if (!argument.isJsonPrimitive() || !argument.getAsJsonPrimitive().isString()
                    || (i == 0 ? !Path.of(argument.getAsString()).isAbsolute()
                            : !expected.get(i - 1).equals(argument.getAsString()))) {
                throw new IOException("retained install process arguments differ");
            }
        }
        return command.deepCopy();
    }

    static JsonObject installReceipt(JsonObject input, JsonObject completed, String stdout,
            String boot, JsonObject nativeOperation) throws IOException {
        JsonObject expected = new JsonObject();
        expected.addProperty("schemaVersion", 1);
        expected.add("command", installCommand(input));
        expected.addProperty("exit", 0);
        expected.addProperty("acceptanceVerified", true);
        requireInstalledTerminal(nativeOperation);
        String manifest = text(input, "manifest");
        if (!expected.equals(completed) || !manifest.matches("[0-9a-f]{64}")
                || !stdout.equals("Install completed: transaction " + manifest + "\n")
                || !"install".equals(text(nativeOperation, "operation"))
                || !manifest.equals(text(nativeOperation, "transactionId"))
                || !boot.equals(text(nativeOperation, "bootId"))
                || integer(nativeOperation, "rebootCount") != 2
                || !uuid(text(input, "priorBoot")) || boot.equals(text(input, "priorBoot"))) {
            throw new IOException("retained install lacks its accepted successful CLI process");
        }
        return nativeOperation.deepCopy();
    }

    @Override public InstallMonitor.Boot verifyBoot(int rebootCount, String previousBoot, CliProcess.Control control) throws Exception {
        long deadline = System.nanoTime() + Duration.ofMinutes(3).toNanos();
        Exception unavailable = null;
        boolean observeFirstBootEarly = rebootCount == 1
                && (observeFirstInstallBootEarly || Set.of("pw-icon", "pw-readiness").contains(System.getProperty("vehicleStartAt", "full")));
        while (System.nanoTime() - deadline < 0) {
            control.check();
            BootData observed;
            try { observed = boot(control); }
            catch (InterruptedException cancelled) { throw cancelled; }
            catch (IOException error) { unavailable = error; Thread.sleep(250); continue; }
            if (observed.id().equals(previousBoot) || !observed.enforcing()
                    || (!observeFirstBootEarly && !observed.complete())) {
                Thread.sleep(250);
                continue;
            }
            JsonObject report = observed.operation();
            if (observeFirstBootEarly) {
                // The first boot can publish its report before Android is ready.
                if (report.size() == 0 || (previousBoot.equals(text(report, "bootId"))
                        && manifest.equals(text(report, "transactionId")))) {
                    Thread.sleep(250);
                    continue;
                }
                requireFirstInstallBoot(report, manifest, observed.id());
                return new InstallMonitor.Boot(observed.id(), rebootCount);
            }
            if (!"install".equals(text(report, "operation")) || !manifest.equals(text(report, "transactionId"))
                    || integer(report, "rebootCount") != rebootCount || !observed.id().equals(text(report, "bootId")))
                throw new IOException("intermediate boot operation identity differs");
            verifyNativeBoot(observed.id(), control);
            BootData fence = boot(control);
            if (!fence.id().equals(observed.id()) || !fence.complete() || !fence.enforcing()
                    || integer(fence.operation(), "rebootCount") != rebootCount || !manifest.equals(text(fence.operation(), "transactionId")))
                throw new IOException("intermediate boot changed after native UI verification");
            return new InstallMonitor.Boot(observed.id(), rebootCount);
        }
        throw new IOException("installer intermediate boot deadline expired", unavailable);
    }
    static void requireFirstInstallBoot(JsonObject report, String manifest, String bootId) throws IOException {
        String phase = text(report, "phase");
        String outcome = text(report, "outcome");
        long count = integer(report, "rebootCount");
        boolean applyingAgents = count == 1 && "install-agents".equals(phase) && "running".equals(outcome);
        // The journal reserves reboot 2 while the kernel is still in boot 1.
        boolean rebootScheduled = count == 2 && "install-reboot-second".equals(phase) && "reboot-required".equals(outcome);
        if (!"install".equals(text(report, "operation")) || !manifest.equals(text(report, "transactionId"))
                || !bootId.equals(text(report, "bootId")) || !(applyingAgents || rebootScheduled)) {
            throw new IOException("first install boot operation identity differs");
        }
    }

    @Override public InstallMonitor.Boot currentBoot(CliProcess.Control control) throws Exception {
        BootData value = boot(control);
        if (!value.complete() || !value.enforcing()) throw new IOException("current intermediate boot unavailable");
        return new InstallMonitor.Boot(value.id(), Math.toIntExact(integer(value.operation(), "rebootCount")));
    }
    @Override public String check(JsonObject request, CliProcess.Control control) throws Exception {
        // The installer may retry the same acceptance relay request for 120 seconds.
        return checkBody(request, control, Duration.ofSeconds(150));
    }

    public void rebootAndVerify() throws Exception {
        try (ProductRun.Operation operation = reserve(ProductRun.Kind.REBOOT)) {
            rebootAndVerify(operation);
        }
    }

    private void rebootAndVerify(ProductRun.Operation operation) throws Exception {rebootAndVerify(operation,false);}
    private void rebootAndVerify(ProductRun.Operation operation,boolean cold) throws Exception {
        BootData before = boot(null);
        if (!before.complete() || !before.enforcing()) throw new IOException("reboot requires a ready Enforcing prior boot");
        operation.observePriorBoot(before.id());
        operation.dispatch();
        JsonObject result = owned.reboot(cold);
        Path evidence = work.resolve("explicit-reboot-" + (++counter) + ".json");
        Files.writeString(evidence, result.toString() + "\n");
        String after = text(result, "boot_id");
        if (!owned.session().id().equals(text(result, "session_id")) || !"completed".equals(text(result, "status"))
                || !"guest_boot_changed".equals(text(result, "scope")) || !before.id().equals(text(result, "boot_id_before"))
                || !uuid(after) || after.equals(before.id()) || integer(result, "android_pid_before") != androidPid || integer(result, "android_pid") < 1)
            throw new IOException("explicit reboot receipt identity differs");
        androidPid = integer(result, "android_pid");
        CliProcess.Control control = new CliProcess.Control(System.nanoTime() + Duration.ofMinutes(3).toNanos(), null);
        try { verifyNativeBoot(after, control); }
        finally { control.close(); }
        BootData observed = boot(null);
        if (!observed.id().equals(after) || !observed.complete() || !observed.enforcing()) throw new IOException("reboot did not reach the observed ready boot");
        if ("none".equals(text(operation.input(), "expectedStartup"))) {
            Path absent = requireNone(after); operation.complete(evidence, absent);
        } else {
            requireHealthy(); operation.complete(evidence);
        }
    }

    static boolean uninstallPreparation(String phase) throws IOException {
        if ("preparation".equals(phase)) return true;
        if (Set.of("cycle", "final", "updated", "failed-install-cleanup").contains(phase) || SeedingPhases.contains(phase)) return false;
        throw new IOException("unknown uninstall phase");
    }

    public void uninstall(String phase) throws Exception {
        uninstallPreparation(phase);
        if ("failed-install-cleanup".equals(phase)
                && (productRun == null || !productRun.hasFailedInstallHistory())) {
            throw new IOException("failed install cleanup requires reconciled failure authority");
        }
        try (ProductRun.Operation operation = reserve(ProductRun.Kind.UNINSTALL, phase)) {
            uninstall(phase, operation);if(SeedingPhases.contains(phase))seedingUninstallOperations.put(phase,operation.receiptPath());
        }
    }

    public void uninstallNormally(String phase) throws Exception {
        if (uninstallPreparation(phase) || "failed-install-cleanup".equals(phase))
            throw new IOException("expected a normal lifecycle uninstall phase");
        try (ProductRun.Operation operation = reserve(ProductRun.Kind.UNINSTALL, phase)) {
            uninstall(phase, operation, true);
        }
    }

    private void uninstall(String phase, ProductRun.Operation operation) throws Exception {
        uninstall(phase, operation, "failed-install-cleanup".equals(phase));
    }

    private void uninstall(String phase, ProductRun.Operation operation, boolean normalOnly) throws Exception {
        BootData before = boot(null);
        boolean[] acknowledged = {false};
        CliProcess.Listener observer = (stream, line, control) -> {
            if (stream != CliProcess.Stream.ERR || !line.startsWith("VBI_CACHE_RECOVERY_BOOT|")) return null;
            if (acknowledged[0]) throw new IOException("cache recovery boot repeated");
            acknowledged[0] = true;
            JsonObject request = recoveryEvent(line);
            String announced = text(request, "bootId");
            List<String> attempts = new ArrayList<>();
            boolean accept = false;
            if (!before.id().equals(text(request, "priorBootId"))) {
                attempts.add("recovery prior boot differs");
            } else {
                // The product announces the target boot as soon as it has asked the guest to reboot, so at
                // that instant the announced boot is routinely not yet observable (guest still in the
                // previous boot, HOME not yet visible, delivery report still carrying the old boot id).
                // Sampling once turns that normal transient into a terminal rejection, so poll the same
                // identity requirements until they hold or the bounded scope expires; a failure at the
                // deadline still rejects.
                long deadline = System.nanoTime() + Math.min(control.remaining().toNanos(), Duration.ofMinutes(4).toNanos());
                while (true) {
                    try {
                        control.check();
                        verifyNativeBoot(announced, control);
                        BootData fence = boot(control);
                        if (!announced.equals(fence.id()) || !fence.complete() || !fence.enforcing()) throw new IOException("recovery boot changed");
                        accept = true; attempts.add("accepted"); break;
                    } catch (InterruptedException cancelled) { Thread.currentThread().interrupt(); throw cancelled; }
                    catch (Exception failed) {
                        control.check();
                        String reason = failed.getMessage() == null ? failed.toString() : failed.getMessage();
                        if (System.nanoTime() >= deadline) { attempts.add("deadline after: " + reason); break; }
                        attempts.add(reason); Thread.sleep(3000);
                    }
                }
            }
            JsonObject evidence = new JsonObject(); evidence.add("request", request.deepCopy());
            JsonArray seen = new JsonArray(); for (String attempt : attempts) seen.add(attempt); evidence.add("attempts", seen);
            RunFiles.write(work.resolve("cache-recovery-observation-" + (++counter) + ".json"), evidence);
            control.check(); request.remove("state"); request.addProperty("decision", accept ? "accept" : "reject");
            return request.toString();
        };
        boolean preparation = uninstallPreparation(phase);
        List<String> arguments;
        if (normalOnly) {
            Path request = work.resolve("normal-uninstall-request-" + (++counter) + ".json");
            RunFiles.write(request, action("uninstall-normal"));
            arguments = List.of("--emulator-check", request.toString());
        } else {
            arguments = List.of("--uninstall");
        }
        operation.observePriorBoot(before.id());
        operation.dispatch();
        CliProcess.Result result = run(arguments, Duration.ofMinutes(preparation ? 25 : 5),
                observer, null);
        String raw = output(result);
        String rawReceipt = normalOnly ? FailedInstallReconciliation.normalReceipt(raw,
                device, before.id()).toString() + "\n" : raw;
        Files.writeString(work.resolve(phase + "-absence.raw.json"), rawReceipt);
        JsonObject receipt = absenceReceipt(rawReceipt, preparation, before.id());
        String after = text(receipt, "bootId");
        Files.writeString(work.resolve(phase + "-absence.json"), rawReceipt);
        BootData fence = boot(null);
        if (!after.equals(fence.id()) || !fence.complete() || !fence.enforcing()) throw new IOException("uninstall absence boot differs");
        if (normalOnly) {
            var control = new CliProcess.Control(System.nanoTime() + Duration.ofMinutes(2).toNanos(), null);
            try { verifyNativeBoot(after, control); }
            finally { control.close(); }
        }
        requireAbsent();
        if (normalOnly) {
            operation.complete(work.resolve(phase + "-absence.raw.json"), result.out(), result.err());
        } else {
            operation.complete(result.out(), result.err(), work.resolve(phase + "-absence.raw.json"));
        }
    }

    /** Actual Settings action and OEM confirmation; the packaged observer only observes the resulting boot. */
    public Path uninstallFromSettings() throws Exception {
        requireHealthy(); Path settings = verifySettingsTab();
        if (fixturePackage().present()) throw new IOException("BLOCKED: owned Voboost Fixture cleanup must finish before Settings uninstall");
        BootData before = boot(null);
        if (!before.complete() || !before.enforcing() || productRun == null) throw new IOException("Settings uninstall requires owned ready boot");
        JsonObject input = new JsonObject(); input.addProperty("manifest", manifest); input.addProperty("device", device);
        input.addProperty("phase", "final"); input.addProperty("route", "settings");
        Path request = work.resolve("settings-uninstall-request-" + (++counter) + ".json"); RunFiles.write(request, action("observe-settings-uninstall"));
        Path buttonProof = work.resolve("settings-uninstall-button-" + (++counter) + ".json");
        Path confirmProof = work.resolve("settings-uninstall-confirm-" + (++counter) + ".json");
        try (ProductRun.Operation operation = productRun.reserve(ProductRun.Kind.UNINSTALL, input)) {
            operation.observePriorBoot(before.id()); operation.dispatch(); boolean[] armed = {false};
            CliProcess.Listener listener = (stream, line, control) -> {
                if (stream != CliProcess.Stream.ERR || !line.equals("settings-uninstall-observer-armed-v1")) return null;
                if (armed[0]) throw new IOException("Settings uninstall observer arm repeated");
                armed[0] = true; control.check();
                BootData fence = boot(control);
                if (!before.id().equals(fence.id()) || !fence.complete() || !fence.enforcing()) throw new IOException("Settings uninstall arm boot differs");
                long deadline = System.nanoTime() + Math.min(control.remaining().toNanos(), Duration.ofSeconds(45).toNanos());
                boolean tapped = false;
                for (int scroll = 0; scroll <= 8; ++scroll) {
                    control.check(); JsonObject tree = settingsUi(before.id(), deadline);
                    try {
                        String node = ru.voboost.voyemu.SettingsRemoval.button(ru.voboost.voyemu.UiEvidence.surface(tree, "primary"));
                        RunFiles.write(buttonProof, tree); tapNode(tree, node); tapped = true; break;
                    } catch (ru.voboost.voyemu.UiEvidence.Unavailable pending) {
                        if (scroll == 8) break;
                        owned.scrollSettings(ru.voboost.voyemu.UiTarget.selectScrollable(tree, profile, owned.session(), "primary",
                            node -> "com.qinggan.app.setting".equals(text(node, "package")) && truth(node, "scrollable")));
                    }
                }
                if (!tapped) throw new IOException("Settings uninstall action was not observed within bounded scrolling");
                while (System.nanoTime() < deadline) {
                    control.check(); JsonObject tree = settingsUi(before.id(), deadline);
                    try {
                        var confirmation = ru.voboost.voyemu.SettingsRemoval.confirmation(ru.voboost.voyemu.UiEvidence.surface(tree, "primary"));
                        RunFiles.write(confirmProof, tree); tapNode(tree, confirmation.confirm()); return null;
                    } catch (ru.voboost.voyemu.UiEvidence.Unavailable transition) { Thread.sleep(100); }
                }
                throw new IOException("Settings uninstall confirmation was not positively observed");
            };
            CliProcess.Result result = run(List.of("--emulator-check", request.toString()), Duration.ofMinutes(11), listener, null);
            if (!armed[0] || !Files.isRegularFile(confirmProof)) throw new IOException("Settings uninstall did not observe both actual actions");
            JsonObject receipt = settingsAbsence(output(result), device, before.id());
            BootData after = boot(null);
            if (!text(receipt, "bootId").equals(after.id()) || !after.complete() || !after.enforcing()) throw new IOException("Settings uninstall absence boot differs");
            requireAbsent();
            Path proof = work.resolve("settings-uninstall-verified-" + (++counter) + ".json");
            JsonObject evidence = new JsonObject(); evidence.add("absence", receipt); evidence.addProperty("settings", settings.toString());
            evidence.addProperty("button", buttonProof.toString()); evidence.addProperty("confirmation", confirmProof.toString());
            evidence.addProperty("observerStdout", result.out().toString()); RunFiles.write(proof, evidence);
            operation.complete(settings, buttonProof, confirmProof, result.out(), result.err(), proof); return proof;
        }
    }

    static JsonObject settingsAbsence(String raw, String device, String before) throws IOException {
        JsonObject result = json(raw);
        if (raw.length() > 4096 || !raw.strip().equals(result.toString())
                || !result.keySet().equals(Set.of("schemaVersion", "serial", "bootIdBefore", "absence", "enforcing"))
                || integer(result, "schemaVersion") != 1 || !device.equals(text(result, "serial"))
                || !before.equals(text(result, "bootIdBefore")) || !truth(result, "enforcing"))
            throw new IOException("Settings uninstall observer identity differs");
        return absenceReceipt(object(result, "absence").toString(), false, before);
    }

    private ProductRun.Operation reserve(ProductRun.Kind kind) throws IOException { return reserve(kind, null); }

    private ProductRun.Operation reserve(ProductRun.Kind kind, String phase) throws IOException {
        if (productRun == null) throw new IOException("product mutation requires a durable run owner");
        JsonObject input = new JsonObject(); input.addProperty("manifest", manifest); input.addProperty("device", device);
        if (phase != null) input.addProperty("phase", phase);
        return productRun.reserve(kind, input);
    }

    /** Read-only resolution of a terminal native install whose desktop acceptance failed. */
    public Path reconcileFailedInstall() throws Exception {
        if (productRun == null || !productRun.owns(owned)) {
            throw new IOException("retained failed install requires its current session owner");
        }
        ProductRun.Operation pending = productRun.pending();
        if (pending == null) return productRun.failedInstallEvidence();
        if (pending.kind() != ProductRun.Kind.INSTALL && pending.kind() != ProductRun.Kind.UPDATE) {
            throw new IOException("failed install resolver cannot resolve another mutation");
        }
        JsonObject input = pending.input();
        if (!device.equals(text(input, "device"))) {
            throw new IOException("retained failed install targets another endpoint");
        }
        JsonObject process = object(input, "process");
        installCommand(input);
        Path stdout = productRun.evidence(text(process, "stdout"));
        Path stderr = productRun.evidence(text(process, "stderr"));
        if (Files.size(stdout) > 32 * 1024 * 1024 || Files.size(stderr) > 32 * 1024 * 1024) {
            throw new IOException("retained failed install transcript exceeds bound");
        }
        Path processResult = reapplyProcessResult(stdout);
        BootData current = boot(null);
        JsonObject proof;
        Path timing = stdout.resolveSibling(stdout.getFileName() + ".timing.json");
        if (!Files.isRegularFile(processResult) && Files.isRegularFile(timing)) {
            proof = FailedInstallReconciliation.proveInterrupted(input, current.operation(),
                    current.id(), current.complete(), current.enforcing(), RunFiles.read(timing, 4096));
        } else {
            JsonObject runner = Files.isRegularFile(processResult)
                    ? RunFiles.read(processResult, 4096) : RunFiles.read(productRun.evidence("result.json"));
            proof = FailedInstallReconciliation.prove(input, current.operation(),
                    current.id(), current.complete(), current.enforcing(), Files.readString(stdout),
                    Files.readString(stderr), runner);
        }
        CliProcess.Control control = new CliProcess.Control(
                System.nanoTime() + Duration.ofMinutes(3).toNanos(), null);
        try {
            verifyNativeBoot(current.id(), control);
        } finally {
            control.close();
        }
        BootData fence = boot(null);
        if (!fence.id().equals(current.id()) || !fence.complete() || !fence.enforcing()
                || !fence.operation().equals(current.operation())) {
            throw new IOException("failed install terminal observation changed before resolution");
        }
        Path file = work.resolve("failed-install-reconciled-" + (++counter) + ".json");
        RunFiles.write(file, proof);
        pending.failInstall(file);
        return file;
    }

    public void reconcileStockBaseline()throws Exception {
        ProductRun.Operation pending=productRun.pending();if(pending==null)return;
        String phase=text(pending.input(),"phase");
        if(pending.kind()==ProductRun.Kind.CONFIGURE&&Set.of("stock-reference-navigation-system_actions","stock-reference-navigation-language_radio").contains(phase)) {
            JsonObject input=pending.input();BootData before=boot(null);
            if(!manifest.equals(text(input,"manifest"))||!device.equals(text(input,"device"))||!before.id().equals(text(input,"priorBoot"))||!before.complete()||!before.enforcing())throw new IOException("retained stock navigation scope/boot differs");
            // OwnedSession.resume has already looked up every underlying input; this only records its current effect.
            JsonObject tree=settingsUi(before.id(),System.nanoTime()+Duration.ofSeconds(10).toNanos(),"all_attached");JsonObject surface=ru.voboost.voyemu.UiEvidence.surface(tree,"primary");boolean current=false;
            for(JsonElement item:surface.getAsJsonArray("nodes")){JsonObject node=item.getAsJsonObject();if("com.qinggan.app.setting:id/root_layout".equals(text(node,"resource_id"))&&"com.qinggan.app.setting".equals(text(node,"package"))&&truth(node,"visible"))current=true;}
            BootData after=boot(null);if(!current||!before.id().equals(after.id())||!after.complete()||!after.enforcing())throw new IOException("retained navigation has no current same-boot Settings effect");
            Path observed=work.resolve("stock-navigation-current-"+(++counter)+".json");RunFiles.write(observed,tree);JsonObject proof=new JsonObject();proof.addProperty("phase",phase);proof.addProperty("bootId",before.id());proof.addProperty("currentSettings",observed.toString());proof.addProperty("functionalAcceptance",false);proof.addProperty("reconciliation","underlying inputs terminal by owned lookup; partial navigation observed without replay");Path file=work.resolve("stock-navigation-reconciled-"+(++counter)+".json");RunFiles.write(file,proof);pending.complete(file,observed);
            throw new IOException("stock navigation ended before target viewport; partial result reconciled, no replay, normal owned stop required");
        }
        if(pending.kind()!=ProductRun.Kind.REBOOT||!"stock-reference-cold-after-restore-or-removal".equals(phase))throw new IOException("stock resume cannot reconcile another mutation");reconcilePending();
    }
    static void requireStockReboot(JsonObject input,JsonObject receipt,String current,String session,long pid)throws IOException {
        if(!"completed".equals(text(receipt,"status"))||!"guest_boot_changed".equals(text(receipt,"scope"))||!session.equals(text(receipt,"session_id"))||!current.equals(text(receipt,"boot_id"))||!text(input,"priorBoot").equals(text(receipt,"boot_id_before"))||current.equals(text(input,"priorBoot"))||integer(receipt,"android_pid")!=pid)throw new IOException("actual retained stock reboot receipt differs from fresh current boot/owner");
    }
    /** Reconcile only a proven terminal result of the retained operation; never send its command again. */
    public void reconcilePending() throws Exception {
        if (productRun == null) throw new IOException("durable run required");
        ProductRun.Operation pending = productRun.pending();
        if (pending == null) return;
        JsonObject input = pending.input();
        if (!manifest.equals(text(input, "manifest")) || !device.equals(text(input, "device")))
            throw new IOException("retained operation targets another product or endpoint");
        BootData current = boot(null);
        if (!current.complete() || !current.enforcing() || !uuid(text(input, "priorBoot")))
            throw new IOException("retained operation lacks a current ready boot or prior boot evidence");
        if (pending.kind() == ProductRun.Kind.UPDATE
                && Set.of("vehicle-reapply", "offline-update").contains(text(input, "phase"))) {
            boolean reapply = "vehicle-reapply".equals(text(input, "phase"));
            JsonObject process=object(input,"process");Path stdout=productRun.evidence(text(process,"stdout")),stderr=productRun.evidence(text(process,"stderr"));
            if(Files.size(stdout)>256||Files.size(stderr)>32*1024*1024)throw new IOException("retained reapply transcript exceeds bound");
            Path exitFile=reapplyProcessResult(stdout);JsonObject completed=RunFiles.read(exitFile,4096);String raw=Files.readString(stdout);
            String transaction=applyReceipt(input,completed,raw,current.id(),current.operation(),reapply);
            CliProcess.Control control=new CliProcess.Control(System.nanoTime()+Duration.ofMinutes(3).toNanos(),null);
            try{verifyNativeBoot(current.id(),control);}finally{control.close();}
            requireHealthy();BootData fence=boot(null);
            if(!current.id().equals(fence.id())||!fence.complete()||!fence.enforcing())throw new IOException("retained reapply verification crossed boot or policy");
            applyReceipt(input,completed,raw,fence.id(),fence.operation(),reapply);
            JsonObject proof=new JsonObject();proof.addProperty("kind","normal-selected-package-reapply-reconciled");proof.addProperty("transaction",transaction);
            proof.addProperty("priorBoot",text(input,"priorBoot"));proof.addProperty("bootId",fence.id());proof.addProperty("manifest",manifest);
            proof.addProperty("runtime",lastRuntimeOfficial.toString());proof.addProperty("nativeOperation",fence.evidence().toString());proof.addProperty("stdout",stdout.toString());proof.addProperty("stderr",stderr.toString());proof.addProperty("processExit",exitFile.toString());
            Path file=work.resolve("vehicle-reapply-reconciled-"+(++counter)+".json");RunFiles.write(file,proof);
            pending.complete(stdout,stderr,exitFile,current.evidence(),fence.evidence(),lastRuntimeOfficial,file);return;
        }
        JsonObject proof;
        switch (pending.kind()) {
            case INSTALL, UPDATE -> {
                JsonObject process = object(input, "process");
                Path stdout = productRun.evidence(text(process, "stdout"));
                if (Files.size(stdout) > 4096) throw new IOException("retained install output exceeds bound");
                JsonObject completed = RunFiles.read(reapplyProcessResult(stdout), 4096);
                if (completed.has("baseLifecycle")) {
                    reconcileFailedInstall();
                    throw new IOException("retained install reconciled as FAILED; normal cleanup is required");
                }
                proof = installReceipt(input, completed, Files.readString(stdout), current.id(), current.operation());
                requireHealthy();
            }
            case UNINSTALL -> {
                JsonObject retainedProcess = object(input, "process");
                Path retainedOut = productRun.evidence(text(retainedProcess, "stdout"));
                Path retainedErr = productRun.evidence(text(retainedProcess, "stderr"));
                if (Files.size(retainedErr) <= 32 * 1024 * 1024
                        && InstallerRemovalRejection.reported(Files.readString(retainedErr))) {
                    JsonObject rejected = InstallerRemovalRejection.prove(input, current.operation(), current.id(),
                            Files.readString(retainedOut), Files.readString(retainedErr),
                            RunFiles.read(retainedOut.resolveSibling(retainedOut.getFileName() + ".timing.json"), 4096));
                    BootData fence = boot(null);
                    if (!current.id().equals(fence.id()) || !current.operation().equals(fence.operation()))
                        throw new IOException("rejected uninstall observation changed");
                    Path receipt = work.resolve("uninstall-rejected-" + (++counter) + ".json");
                    RunFiles.write(receipt, rejected);
                    pending.rejectUninstall(receipt);
                    return;
                }
                JsonObject process = object(input, "process");
                Path stdout = productRun.evidence(text(process, "stdout"));
                if (Files.size(stdout) > 4096) throw new IOException("retained uninstall output unavailable");
                boolean preparation = uninstallPreparation(text(input, "phase"));
                boolean normalOnly = "failed-install-cleanup".equals(text(input, "phase"));
                if (normalOnly) {
                    var command = process.getAsJsonArray("command");
                    if (command == null || command.size() != 7
                            || !emulatorDriver.toString().equals(command.get(0).getAsString())
                            || !"--candidate".equals(command.get(5).getAsString())
                            || !binary.toString().equals(command.get(6).getAsString())
                            || !"--emulator-check".equals(command.get(1).getAsString())
                            || !"--device".equals(command.get(3).getAsString())
                            || !device.equals(command.get(4).getAsString())) {
                        throw new IOException("retained normal-only process binding differs");
                    }
                    Path request = Path.of(command.get(2).getAsString());
                    if (!request.isAbsolute() || !request.getParent().equals(stdout.getParent())
                            || !Files.isRegularFile(request, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                            || Files.size(request) > 1024) {
                        throw new IOException("retained normal-only request is unavailable");
                    }
                    JsonObject action = RunFiles.read(request);
                    if (!action.keySet().equals(Set.of("action"))
                            || !"uninstall-normal".equals(text(action, "action"))) {
                        throw new IOException("retained request is not the fixed normal-only action");
                    }
                    proof = FailedInstallReconciliation.normalReceipt(Files.readString(stdout),
                            device, text(input, "priorBoot"));
                } else {
                    proof = "settings".equals(text(input, "route"))
                            ? settingsAbsence(Files.readString(stdout), device, text(input, "priorBoot"))
                            : absenceReceipt(Files.readString(stdout), preparation,
                                    text(input, "priorBoot"));
                }
                boolean preparationOnly = preparation && Set.of("already-absent", "emergency-removed").contains(text(proof, "state"));
                if (!current.id().equals(text(proof, "bootId")) || !preparationOnly && !normalOnly && !"settings".equals(text(input, "route")) && !manifest.equals(text(proof, "transactionId")))
                    throw new IOException("retained uninstall terminal identity differs");
                requireAbsent();
            }
            case CONFIGURE -> {
                if (!current.id().equals(text(input, "priorBoot"))) throw new IOException("configuration reconciliation crossed the original boot");
                if ("fev-drawer-observe".equals(text(input,"phase"))) {
                    JsonObject tree=settingsUi(current.id(),System.nanoTime()+Duration.ofSeconds(5).toNanos());
                    JsonObject surface=ru.voboost.voyemu.UiEvidence.surface(tree,"primary");
                    String observed;
                    try {ru.voboost.voyemu.VehicleDrawer.read(surface);observed="open";}
                    catch(ru.voboost.voyemu.UiEvidence.Unavailable closed) {
                        ru.voboost.voyemu.VehicleDrawer.handle(surface);observed="receiver-visible";
                    }
                    proof=new JsonObject();proof.add("ui",tree);proof.addProperty("bootId",current.id());
                    proof.addProperty("reconciliation","drawer-observation-only");proof.addProperty("drawer",observed);
                } else if ("startup-rejection".equals(text(input, "phase"))) {

                    if (!catalog.equals(text(input, "catalogIdentity"))) throw new IOException("retained startup diagnostic catalog differs");
                    Path stdout = productRun.evidence(text(object(input, "process"), "stdout"));
                    if (Files.size(stdout) > 16384) throw new IOException("retained diagnostic stream exceeds bound");
                    JsonObject cleanup = FaultWindow.cleanup(Files.readString(stdout), object(input, "diagnostic"), device, version);
                    var runtime = observeRuntime(text(status(), "activeGeneration"));
                    if (!runtime.publisher().equals(text(input, "priorPublisher")) || !runtime.revision().equals(text(input, "priorRevision"))
                            || !runtime.startup().equals(text(input, "desiredStartup")) || !runtime.runningStartup().equals(text(input, "priorRunningStartup")))
                        throw new IOException("retained rejected startup state differs");
                    JsonObject expected = object(input, "expectedAgents");
                    if (expected.isEmpty()) throw new IOException("retained rejected startup configuration unavailable");
                    for (String agent : expected.keySet())
                        if (!runtime.desiredConfiguration(agent).equals(expected.get(agent))) throw new IOException("retained rejected startup changed agent configuration");
                    proof = new JsonObject(); proof.addProperty("reconciliation", "diagnostic-cleanup-and-unchanged-startup-observed"); proof.add("cleanup", cleanup);
                } else if ("agent-checkbox".equals(text(input, "phase"))) {
                    if (!catalog.equals(text(input, "catalogIdentity"))) throw new IOException("retained checkbox catalog differs");
                    var runtime = observeRuntime(text(status(), "activeGeneration"));
                    if (!runtime.publisher().equals(text(input, "priorPublisher"))) throw new IOException("retained checkbox publisher differs");
                    if (!runtime.startup().equals(text(input, "priorStartup")) || !runtime.runningStartup().equals(text(input, "priorRunningStartup")))
                        throw new IOException("retained checkbox startup configuration differs");
                    JsonObject expected = object(input, "expectedAgents");
                    if (expected.isEmpty() || !expected.has(text(input, "agent"))) throw new IOException("retained checkbox expected configuration unavailable");
                    for (String agent : expected.keySet())
                        if (!runtime.desiredConfiguration(agent).equals(expected.get(agent))) throw new IOException("retained checkbox desired configuration not observed");
                    proof = new JsonObject(); proof.addProperty("reconciliation", "canonical-agent-configuration-observed"); proof.add("agents", expected);
                } else if (Set.of("pw-drawer-select-1", "pw-drawer-select-2").contains(text(input, "phase"))) {
                    int wanted = text(input, "phase").endsWith("-1") ? 1 : 2;
                    String generation = text(status(), "activeGeneration");
                    proof = awaitObservation("Retained pedestrian warning drawer selection", Duration.ofSeconds(30),
                            (scope, deadline) -> {
                        var runtime = observeRuntime(generation, scope, null);
                        if (!runtime.desired("vehicle-drive-pedestrian-warning"))
                            throw new IOException("Retained pedestrian warning agent is disabled");
                        var can = vehicleCan(current.id(), scope);
                        if (can.pw() != wanted) return null;
                        Path durable = vehicleIntent("vehicle-drive-pedestrian-warning", wanted == 1,
                                current.id(), can, runtime, scope);
                        if (durable == null) return null;
                        JsonObject observed = new JsonObject();
                        observed.addProperty("reconciliation", "same-boot-durable-pedestrian-warning-selection");
                        observed.addProperty("bootId", current.id());
                        observed.addProperty("durableIntentEvidence", durable.toString());
                        observed.add("can", can.receipt());
                        return observed;
                    });
                } else if ("startup-none-cli".equals(text(input,"phase"))) {
                    String owner=StartupObservation.requireNoneProcesses(checkBody(action("inspect"),null),current.id());
                    if(!owner.equals(text(input,"priorOwner"))||!"interface".equals(text(input,"desiredStartup"))||!"interface".equals(startupObservation(current.id()).mode()))throw new IOException("retained none startup intent or exact owner changed");
                    var logs=collectLogs(current.id());if(!owner.equals(StartupObservation.requireNoneProcesses(checkBody(action("inspect"),null),current.id())))throw new IOException("retained none startup export crossed owner identity");
                    proof=new JsonObject();proof.addProperty("reconciliation","next-boot-interface-intent-and-unchanged-owner-observed");proof.addProperty("authenticatedLogArchive",logs.archive().toString());
                } else if ("language-cycle".equals(text(input, "phase"))) {
                    String original = text(input, "originalLocale");
                    if (!Set.of("en", "zh").contains(original)) throw new IOException("retained configuration has no language intent");
                    proof = localeObservation(current.id());
                    SystemLocale.read(text(proof, "body"), current.id()).requireOriginal(text(input, "priorBoot"), original);
                    requireHealthy(); proof.addProperty("reconciliation", "original-system-locale-observed");
                } else if (Set.of("startup-radio", "startup-cli", "tbox-recovery").contains(text(input, "phase"))) {
                    String desired = text(input, "desiredStartup");
                    if (!Set.of("interface", "hidden", "none").contains(desired)) throw new IOException("retained startup intent unavailable");
                    JsonObject currentStatus = status();
                    var runtime = observeRuntime(text(currentStatus, "activeGeneration"));
                    if (!desired.equals(startupObservation(current.id()).mode()) || !desired.equals(text(currentStatus, "startupMode")))
                        throw new IOException("retained startup write is not independently confirmed");
                    if (Set.of("startup-radio", "startup-cli").contains(text(input, "phase")) && (!text(input, "priorPublisher").equals(runtime.publisher())
                            || !text(input, "priorRunningStartup").equals(runtime.runningStartup())))
                        throw new IOException("retained next-boot selection stopped or replaced the original live publisher");
                    if ("tbox-recovery".equals(text(input, "phase")) && (!"interface".equals(desired)
                            || !runtime.publisher().equals(text(input, "priorPublisher")) || !"interface".equals(runtime.runningStartup())))
                        throw new IOException("retained TBOX recovery has no original live publisher in interface mode");
                    requireHealthy(); proof = new JsonObject(); proof.addProperty("bootId", current.id());
                    proof.addProperty("startupMode", desired); proof.addProperty("reconciliation", "persisted-startup-observed");
                } else throw new IOException("retained configuration needs its scenario-specific read-only resolver");
                proof.addProperty("functionalAcceptance", false);
            }
            case REBOOT -> {
                proof = owned.rebootReceipt();
                if (!current.id().equals(text(proof, "boot_id")) || !text(input, "priorBoot").equals(text(proof, "boot_id_before"))
                        || !"guest_boot_changed".equals(text(proof, "scope")) || !"completed".equals(text(proof, "status")))
                    throw new IOException("retained reboot terminal identity differs");
                if("stock-reference-cold-after-restore-or-removal".equals(text(input,"phase"))) {
                    requireStockReboot(input,proof,current.id(),owned.session().id(),androidPid);Path absence=requireAbsentEvidence();CliProcess.Control control=new CliProcess.Control(System.nanoTime()+Duration.ofMinutes(3).toNanos(),null);try{verifyNativeBoot(current.id(),control);}finally{control.close();}proof.addProperty("rootAbsenceEvidence",absence.toString());proof.addProperty("bootCapture",verifiedBootCaptures.get(current.id()).toString());
                } else if ("none".equals(text(input, "expectedStartup"))) requireNone(current.id()); else requireHealthy();
            }
            default -> throw new IOException("retained operation needs its scenario-specific read-only resolver");
        }
        Path evidence = work.resolve("reconciled-" + (++counter) + ".json");
        Files.writeString(evidence, proof.toString() + "\n");
        if (pending.kind() == ProductRun.Kind.UNINSTALL
                && "failed-install-cleanup".equals(text(input, "phase"))) {
            pending.complete(evidence, productRun.evidence(
                    text(object(input, "process"), "stdout")));
        } else {
            pending.complete(evidence);
        }
    }

    public String requireOneRegisteredResource(Set<String> packageNames) throws Exception {
        BootData before = boot(null);
        String frame = checkBody(action("package-list"), null);
        BootData after = boot(null);
        Path evidence = work.resolve("resource-package-list-" + (++counter) + ".txt");
        Files.writeString(evidence, frame);
        if (!before.complete() || !before.enforcing() || !after.complete() || !after.enforcing()
                || !before.id().equals(after.id())
                || !frame.startsWith("bootId=" + before.id() + "\npackage-list-v1\n"))
            throw new IOException("resource PackageManager observation crossed the ready Enforcing boot: " + evidence);
        Set<String> registered = Set.copyOf(frame.lines().toList());
        return packageNames.stream().filter(name -> registered.contains("package:" + name)).findFirst()
                .orElseThrow(() -> new IOException("expected one of " + packageNames
                        + " in owned PackageManager observation: " + evidence));
    }

    public void requireAbsent() throws Exception {
        requireAbsentEvidence();
    }

    private Path requireAbsentEvidence() throws Exception {
        JsonObject inspected = status();
        if (!"absent".equals(text(inspected, "route"))) throw new IOException("installed management did not become absent");
        JsonObject observation = json(output(runRead(List.of("--observe"), Duration.ofSeconds(30), null)));
        JsonObject data = object(observation, "data");
        if (!"2".equals(observation.get("schema").getAsString()) || !"voboost".equals(text(observation, "source"))
                || !"ready".equals(text(observation, "status")) || !"absent".equals(text(data, "root")))
            throw new IOException("independent root absence observation unavailable");
        Path file = work.resolve("root-absence-" + (++counter) + ".json");
        RunFiles.write(file, observation);
        return file;
    }

    /** Read only through the packaged fixed operation, with endpoint and independent boot fences. */
    public FixturePackage fixturePackage() throws Exception {
        BootData before = boot(null);
        if (!before.complete() || !before.enforcing()) throw new IOException("fixture observation requires a ready Enforcing boot");
        FixturePackage result = FixturePackage.read(checkBody(action("fixture-state"), null), before.id());
        BootData after = boot(null);
        if (!before.id().equals(after.id()) || !after.complete() || !after.enforcing())
            throw new IOException("fixture observation crossed boot or policy change");
        JsonObject proof = new JsonObject(); proof.addProperty("bootId", result.boot());
        proof.addProperty("package", "ru.voboost.tests.launcherfixture"); proof.addProperty("present", result.present());
        RunFiles.write(work.resolve("fixture-state-" + (++counter) + ".json"), proof);
        return result;
    }

    public FixtureClassification fixtureClassification() throws Exception {
        BootData before = boot(null);
        if (!before.complete() || !before.enforcing()) throw new IOException("fixture classification requires a ready Enforcing boot");
        FixtureClassification result = FixtureClassification.read(checkBody(action("fixture-classification"), null), before.id());
        BootData after = boot(null);
        if (!before.id().equals(after.id()) || !after.complete() || !after.enforcing())
            throw new IOException("fixture classification crossed boot or policy change");
        RunFiles.write(work.resolve("fixture-classification-" + (++counter) + ".json"), new com.google.gson.Gson().toJsonTree(result).getAsJsonObject());
        return result;
    }

    /** Open the OEM Setting tab through observed input and verify its actual version heading. */
    public Path verifySettingsTab() throws Exception {
        BootData before = boot(null);
        if (!before.complete() || !before.enforcing()) throw new IOException("Setting requires a ready Enforcing boot");
        owned.launch("com.qinggan.app.setting", before.id(), "primary");
        // The appended Voboost entry is the last row of the OEM menu, so it starts below the fold of
        // the left menu ScrollView. Walk the menu toward its end with observed gestures until the row
        // reports itself visible, exactly as a person would. This search owns its own budget: the
        // version-heading search below must not inherit a spent deadline.
        long menuDeadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        JsonObject tree = null; String selected = null; int menuScrolls = 0;
        while (System.nanoTime() < menuDeadline) {
            // Every observation owns a fresh read budget: the search deadline must never shrink the
            // read down to the point where the observer times out instead of reporting a tree.
            tree = settingsUi(before.id(), System.nanoTime() + Duration.ofSeconds(5).toNanos());
            JsonObject surface = ru.voboost.voyemu.UiEvidence.surface(tree, "primary");
            if (ru.voboost.voyemu.UiEvidence.visibleText(surface, "com.qinggan.app.setting", "Voboost")) {
                try {
                    selected = ru.voboost.voyemu.UiEvidence.actionForText(surface, "com.qinggan.app.setting", "Voboost");
                    break;
                } catch (ru.voboost.voyemu.UiEvidence.Unavailable transition) {
                    // A visible label can precede an enabled row during composition.
                }
            } else if (menuScrolls < 8 && menuEntryObserved(surface)) {
                // The row exists but is outside the menu viewport, so the menu has to be walked toward
                // its end. Until the row is composed at all there is nothing to walk.
                // The OEM menu recomposes while it is walked, so an observation taken between the mutation and
                // the settled scene can be an incomplete surface that UiEvidence.surface() legitimately admits
                // (status partial) while UiTarget demands a complete one (13q: UiTarget.select refused with
                // "UI input coverage is incomplete or unsupported for this target" at settingsMenuScrollTarget
                // after the Voboost row had been observed but was outside the viewport). That refusal happens
                // while the scroll handle is resolved, before any gesture is dispatched, so the next bounded
                // iteration re-reads and re-resolves; a refusal does not consume a scroll, so menuScrolls still
                // counts only completed gestures. Only a correlated not_started dispatch can be reselected;
                // unknown outcomes still escape without sending another gesture.
                ru.voboost.voyemu.UiTarget menuScroll=null;
                try {menuScroll=settingsMenuScrollTarget(tree);}
                catch(IOException unresolvedMenuScroll) { /* No gesture has been dispatched yet. */ }
                if(menuScroll!=null) {
                    try {owned.scrollSettings(menuScroll); ++menuScrolls;}
                    catch(OwnedSession.NotStarted transition) { /* Proved not executed; re-read and select a fresh target. */ }
                }
            }
            Thread.sleep(100);
        }
        if (selected == null) throw new IOException("Voboost tab not observed in OEM Setting");
        // The row is re-resolved from the newest observation on every attempt: the OEM menu recomposes
        // while it is walked, and the tap API refuses a target whose node is no longer live
        // (TEST_INPUT_BLOCKED/target_missing, surfaced as a proved not-executed NotStarted).
        long tapDeadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        boolean tapped = false;
        while (!tapped && System.nanoTime() < tapDeadline) {
            JsonObject surface = ru.voboost.voyemu.UiEvidence.surface(tree, "primary");
            String row = null;
            try {
                row = ru.voboost.voyemu.UiEvidence.actionForText(surface, "com.qinggan.app.setting", "Voboost");
            } catch (ru.voboost.voyemu.UiEvidence.Unavailable transition) {
                // A visible label can precede an enabled row during composition.
            }
            if (row != null) {
                String nodeId = row;
                try {
                    owned.tap(ru.voboost.voyemu.UiTarget.select(tree, profile, owned.session(), "primary",
                        node -> nodeId.equals(text(node, "id"))));
                    tapped = true;
                    break;
                } catch (OwnedSession.NotStarted proved) {
                    // Only a correlated not_started reply reaches here: the tap did not execute.
                }
            }
            Thread.sleep(100);
            tree = settingsUi(before.id(), System.nanoTime() + Duration.ofSeconds(5).toNanos());
        }
        if (!tapped) throw new IOException("Voboost tab action could not be dispatched");
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        int scrolls = 0;
        while (System.nanoTime() < deadline) {
            tree = settingsUi(before.id(), System.nanoTime() + Duration.ofSeconds(5).toNanos());
            if (ru.voboost.voyemu.UiEvidence.visibleText(ru.voboost.voyemu.UiEvidence.surface(tree, "primary"),
                    "com.qinggan.app.setting", "Voboost " + version)) {
                BootData after = boot(null);
                if (!before.id().equals(after.id()) || !after.complete() || !after.enforcing())
                    throw new IOException("Setting proof crossed a boot or policy change");
                Path evidence = work.resolve("settings-tab-verified-" + (++counter) + ".json"); RunFiles.write(evidence, tree); return evidence;
            }
            if (scrolls < 8) {
                JsonObject surface = ru.voboost.voyemu.UiEvidence.surface(tree, "primary");
                boolean observed = false;
                for (JsonElement item : surface.getAsJsonArray("nodes")) {
                    if (isSettingContentScroll(surface, item.getAsJsonObject())) observed = true;
                }
                if (observed) {
                    var target=settingsScrollTarget(tree);
                    try {owned.scrollSettingsToStart(target); ++scrolls;}
                    catch(OwnedSession.NotStarted transition) { /* Proved not executed; re-read and select a fresh target. */ }
                }
            }
            Thread.sleep(100);
        }
        throw new IOException("OEM Setting did not show the current Voboost version heading");
    }

    /**
     * The observed scroll container that owns the given node, i.e. the nearest scrollable ScrollView
     * on its parent chain. The OEM Setting surface carries several ScrollViews at once (the left menu
     * and the selected tab), so a bare "any setting ScrollView" selector is ambiguous.
     */
    private static JsonObject scrollAncestor(JsonObject surface, JsonObject node) {
        String parent = text(node, "parent");
        while (!parent.isBlank()) {
            JsonObject found = null;
            for (JsonElement item : surface.getAsJsonArray("nodes")) {
                JsonObject candidate = item.getAsJsonObject();
                if (parent.equals(text(candidate, "id"))) { found = candidate; break; }
            }
            if (found == null) return null;
            if ("android.widget.ScrollView".equals(text(found, "class")) && truth(found, "scrollable")) return found;
            parent = text(found, "parent");
        }
        return null;
    }

    /** True when the appended Voboost menu row is composed at all, whether or not it is in the viewport. */
    private static boolean menuEntryObserved(JsonObject surface) {
        for (JsonElement item : surface.getAsJsonArray("nodes")) {
            JsonObject node = item.getAsJsonObject();
            if ("com.qinggan.app.setting".equals(text(node, "package")) && "Voboost".equals(text(node, "text"))) return true;
        }
        return false;
    }

    /** The id of the OEM left menu scroll container, located through the appended Voboost entry; blank when absent. */
    static String menuScrollNodeId(JsonObject surface) throws IOException {
        JsonObject entry = null;
        for (JsonElement item : surface.getAsJsonArray("nodes")) {
            JsonObject node = item.getAsJsonObject();
            // The open tab carries "Voboost <version>"; only the exact menu label selects the menu row.
            if ("com.qinggan.app.setting".equals(text(node, "package")) && "Voboost".equals(text(node, "text"))) {
                if (entry != null) throw new IOException("ambiguous Voboost menu entry");
                entry = node;
            }
        }
        if (entry == null) return "";
        JsonObject scroll = scrollAncestor(surface, entry);
        return scroll == null ? "" : text(scroll, "id");
    }

    /** The OEM left menu container that holds the appended Voboost entry, located through that entry. */
    private ru.voboost.voyemu.UiTarget settingsMenuScrollTarget(JsonObject tree) throws IOException {
        JsonObject surface = ru.voboost.voyemu.UiEvidence.surface(tree, "primary");
        String nodeId = menuScrollNodeId(surface);
        if (nodeId.isBlank()) throw new IOException(menuEntryObserved(surface)
            ? "Voboost menu entry has no observed scroll container" : "Voboost menu entry not observed");
        return ru.voboost.voyemu.UiTarget.selectScrollable(tree, profile, owned.session(), "primary",
            node -> nodeId.equals(text(node, "id")));
    }

    /** The observed OEM content pane: the outermost setting ScrollView that is not the left menu. */
    private ru.voboost.voyemu.UiTarget settingsScrollTarget(JsonObject tree) throws IOException {
        JsonObject surface = ru.voboost.voyemu.UiEvidence.surface(tree, "primary");
        // The OEM layout gives the left menu its own outermost setting ScrollView, so the structural
        // "outermost" test alone leaves two candidates and the gesture is refused as ambiguous. The menu
        // container is identified by observation -- it is the scroll container of the appended Voboost
        // entry -- rather than by a hardcoded OEM id; when that entry is absent nothing is excluded and an
        // ambiguous observation still fails closed.
        String menu = menuScrollNodeId(surface);
        return ru.voboost.voyemu.UiTarget.selectScrollable(tree, profile, owned.session(), "primary",
            node -> isSettingContentScroll(surface, node) && (menu.isBlank() || !menu.equals(text(node, "id"))));
    }

    static boolean isSettingContentScroll(JsonObject surface, JsonObject node) {
        if (!"com.qinggan.app.setting".equals(text(node, "package")) || !"android.widget.ScrollView".equals(text(node, "class"))
                || !truth(node, "scrollable") || !truth(node, "visible") || !truth(node, "enabled")) return false;
        // Only the outermost content container may drive a gesture; a nested ScrollView inside it must not.
        String parent = text(node, "parent");
        while (!parent.isBlank()) {
            JsonObject found = null;
            for (JsonElement item : surface.getAsJsonArray("nodes")) {
                JsonObject candidate = item.getAsJsonObject();
                if (parent.equals(text(candidate, "id"))) { found = candidate; break; }
            }
            if (found == null) return true;
            if ("com.qinggan.app.setting".equals(text(found, "package")) && "android.widget.ScrollView".equals(text(found, "class"))
                    && truth(found, "scrollable")) return false;
            parent = text(found, "parent");
        }
        return true;
    }

    private record CheckboxUi(JsonObject tree, ru.voboost.voyemu.SettingsCheckbox checkbox) {}

    private CheckboxUi checkboxUi(String expectedBoot, String label, boolean scroll) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(scroll ? 90 : 15).toNanos();
        int gestures = 0;
        while (System.nanoTime() < deadline) {
            JsonObject tree = settingsUi(expectedBoot, deadline);
            try { return new CheckboxUi(tree, ru.voboost.voyemu.SettingsCheckbox.read(ru.voboost.voyemu.UiEvidence.surface(tree, "primary"), label)); }
            catch (ru.voboost.voyemu.UiEvidence.Unavailable transition) {
                if (scroll && gestures < 24) {
                    var target = settingsScrollTarget(tree);
                    if (gestures < 8) owned.scrollSettings(target); else owned.scrollSettingsToStart(target);
                    ++gestures;
                } else Thread.sleep(100);
            }
        }
        throw new IOException("candidate checkbox was not observable within bounded Settings input: " + label);
    }

    static void requireOutageCheckboxScope(JsonObject scope,String agent,boolean desired,String boot)throws IOException {
        if(!scope.has("cancel")||!scope.get("cancel").isJsonPrimitive()||!scope.getAsJsonPrimitive("cancel").isBoolean()
                ||!"vehicle-state-outage".equals(text(scope,"phase"))||!"vehicle-drive-pedestrian-warning".equals(agent)
                ||!agent.equals(text(scope,"agent"))||!boot.equals(text(scope,"priorBoot"))||!desired&&!truth(scope,"cancel"))
            throw new IOException("checkbox does not belong to the declared outage program");
    }

    private Path setAgent(JsonObject candidateCatalog, String agent, boolean desired) throws Exception {
        return setAgent(candidateCatalog,agent,desired,null);
    }

    private Path setAgent(JsonObject candidateCatalog, String agent, boolean desired, ProductRun.Operation enclosing) throws Exception {
        return setAgent(candidateCatalog,agent,desired,enclosing,null);
    }

    private Path setAgent(JsonObject candidateCatalog, String agent, boolean desired, ProductRun.Operation enclosing, FaultWindow liveWindow) throws Exception {
        BootData before = boot(null);
        if (!before.complete() || !before.enforcing() || productRun == null) throw new IOException("owned ready boot required for checkbox mutation");
        JsonObject inspected = status(); var runtime = observeRuntime(text(inspected, "activeGeneration"));
        JsonObject prior = ru.voboost.tests.agents.AgentSettings.snapshot(candidateCatalog, runtime);
        JsonObject expected = ru.voboost.tests.agents.AgentSettings.transition(candidateCatalog, prior, agent, desired);
        JsonObject entry = ru.voboost.tests.agents.AgentSettings.checkboxes(candidateCatalog).get(agent);
        String language = text(localeObservation(before.id()), "language");
        String label = ru.voboost.tests.agents.AgentSettings.title(entry, language);
        CheckboxUi ui = checkboxUi(before.id(), label, true);
        if (ui.checkbox().checked() != runtime.desired(agent)) throw new IOException("Settings checkbox disagrees with authoritative desired state");
        Path initial = work.resolve("checkbox-before-" + (++counter) + ".json"); RunFiles.write(initial, ui.tree());
        if (prior.equals(expected)) return initial;
        JsonObject input = new JsonObject(); input.addProperty("manifest", manifest); input.addProperty("device", device);
        input.addProperty("phase", "agent-checkbox"); input.addProperty("catalogIdentity", catalog);
        input.addProperty("agent", agent); input.addProperty("enabled", desired); input.addProperty("priorPublisher", runtime.publisher());
        input.addProperty("priorStartup", runtime.startup()); input.addProperty("priorRunningStartup", runtime.runningStartup());
        input.add("expectedAgents", expected);
        ProductRun.Operation operation=enclosing==null?productRun.reserve(ProductRun.Kind.CONFIGURE,input):enclosing;
        try {
            if(enclosing==null){operation.observePriorBoot(before.id());operation.dispatch();}
            else {
                JsonObject scope=enclosing.input();
                if(productRun.pending()!=enclosing||enclosing.kind()!=ProductRun.Kind.CONFIGURE)throw new IOException("outage operation is not the current product owner");
                requireOutageCheckboxScope(scope,agent,desired,before.id());
                JsonObject intent=input.deepCopy();intent.addProperty("state","dispatching");
                RunFiles.write(work.resolve("outage-checkbox-intent-"+(++counter)+".json"),intent);
            }
            if(enclosing!=null) {
                var fence=observeRuntime(text(status(),"activeGeneration"));
                if(!runtime.publisher().equals(fence.publisher())||!runtime.publisher().equals(text(enclosing.input(),"priorPublisher"))||!runtime.revision().equals(fence.revision())||!prior.equals(ru.voboost.tests.agents.AgentSettings.snapshot(candidateCatalog,fence)))throw new IOException("outage checkbox prior authority changed before dispatch");
                if(liveWindow!=null)liveWindow.requirePublisher(fence.publisher());
            }
            // The Settings hierarchy recomposes while the checkbox is located and walked, so the node read from one
            // observation is not guaranteed live at dispatch (13m: the checkbox tap was refused with
            // TEST_INPUT_BLOCKED/target_missing, outcome not_started, retryable false -- the same class that the
            // menu tap in openVehicle and verifySettingsTab already tolerate). A proved not-executed rejection is a
            // transition, not a verdict: re-read the surface, re-resolve the checkbox through checkboxUi and tap
            // again inside a bounded deadline. Any other outcome still escapes through OwnedSession.
            long tapDeadline=System.nanoTime()+Duration.ofSeconds(15).toNanos();
            for(;;) {
                try {tapNode(ui.tree(), ui.checkbox().target());break;}
                catch(ru.voboost.voyemu.OwnedSession.NotStarted vanished) {
                    if(System.nanoTime()>=tapDeadline)throw vanished;
                    ui=checkboxUi(before.id(),label,true);
                    if(ui.checkbox().checked()!=runtime.desired(agent))throw new IOException("Settings checkbox disagrees with authoritative desired state after a not-executed tap rejection");
                }
            }
            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            while (System.nanoTime() < deadline) {
                var current = observeRuntime(text(status(), "activeGeneration"));
                if (!runtime.publisher().equals(current.publisher())) throw new IOException("checkbox mutation replaced the current publisher");
                if (!runtime.startup().equals(current.startup()) || !runtime.runningStartup().equals(current.runningStartup()))
                    throw new IOException("checkbox mutation changed startup configuration");
                if (!runtime.revision().equals(current.revision()) && expected.equals(ru.voboost.tests.agents.AgentSettings.snapshot(candidateCatalog, current))) {
                    CheckboxUi updated = checkboxUi(before.id(), label, false);
                    if (updated.checkbox().checked() == desired) {
                        BootData fence = boot(null);
                        if (!before.id().equals(fence.id()) || !fence.complete() || !fence.enforcing()) throw new IOException("checkbox mutation crossed boot or policy change");
                        Path after = work.resolve("checkbox-after-" + (++counter) + ".json"); RunFiles.write(after, updated.tree());
                        JsonObject proof = new JsonObject(); proof.addProperty("agent", agent); proof.addProperty("enabled", desired);
                        proof.addProperty("bootId", before.id()); proof.addProperty("publisher", current.publisher());
                        proof.addProperty("priorRevision", runtime.revision()); proof.addProperty("currentRevision", current.revision());
                        proof.add("priorAgents", prior); proof.add("expectedAgents", expected); proof.addProperty("functionalAcceptance", false);
                        Path file = work.resolve("checkbox-verified-" + (++counter) + ".json"); RunFiles.write(file, proof);
                        if(enclosing==null)operation.complete(initial, after, file); return file;
                    }
                }
                Thread.sleep(100);
            }
            throw new IOException("actual Settings checkbox mutation was not independently confirmed; do not replay");
        } finally { if(enclosing==null)operation.close(); }
    }

    /** Configure through typed Settings input; the signed catalog remains an immutable input. */
    public Path configureVehicleOnly(JsonObject candidateCatalog) throws Exception {
        return configureVehicleOnly(candidateCatalog, false);
    }

    public Path configureVehicleOnly(JsonObject candidateCatalog, boolean pwOnly) throws Exception {
        Set<String> enabled = pwOnly ? Set.of("vehicle-drive-pedestrian-warning") : Set.of("vehicle-drive-forced-ev", "vehicle-drive-pedestrian-warning");
        var entries = ru.voboost.tests.agents.AgentSettings.checkboxes(candidateCatalog);
        if (!entries.keySet().containsAll(enabled)) throw new IOException("candidate vehicle checkbox pair unavailable");
        var initial = observeRuntime(text(status(), "activeGeneration"));
        JsonObject expected = ru.voboost.tests.agents.AgentSettings.vehicleOnly(candidateCatalog, ru.voboost.tests.agents.AgentSettings.snapshot(candidateCatalog, initial));
        if (pwOnly) expected = ru.voboost.tests.agents.AgentSettings.transition(candidateCatalog, expected, "vehicle-drive-forced-ev", false);
        Path settings = verifySettingsTab();
        JsonArray steps = new JsonArray();
        // Disable all unrelated entries before enabling either vehicle feature.
        for (String agent : entries.keySet()) if (!enabled.contains(agent) && initial.desired(agent))
            steps.add(setAgent(candidateCatalog, agent, false).toString());
        for (String agent : List.of("vehicle-drive-forced-ev", "vehicle-drive-pedestrian-warning")) if (enabled.contains(agent) && !initial.desired(agent))
            steps.add(setAgent(candidateCatalog, agent, true).toString());
        var current = observeRuntime(text(status(), "activeGeneration"));
        JsonObject agents = ru.voboost.tests.agents.AgentSettings.snapshot(candidateCatalog, current);
        if (!expected.equals(agents)) throw new IOException("vehicle-only canonical options/configuration differ");
        for (String agent : entries.keySet())
            if (current.desired(agent) != enabled.contains(agent)) throw new IOException("vehicle-only desired configuration differs: " + agent);
        JsonObject proof = new JsonObject(); proof.addProperty("settings", settings.toString());
        proof.add("steps", steps); proof.add("agents", agents); proof.addProperty("functionalAcceptance", false);
        Path file = work.resolve("vehicle-only-configuration-" + (++counter) + ".json"); RunFiles.write(file, proof); return file;
    }

    /** Exercise every candidate stable checkbox and both keyboard conflict directions, then restore the original desired configuration. */
    public Path verifyAgentEnablement(JsonObject candidateCatalog) throws Exception {
        Path settings = verifySettingsTab();
        var initial = observeRuntime(text(status(), "activeGeneration"));
        JsonObject original = ru.voboost.tests.agents.AgentSettings.snapshot(candidateCatalog, initial);
        var entries = ru.voboost.tests.agents.AgentSettings.checkboxes(candidateCatalog);
        if (!entries.keySet().containsAll(Set.of("keyboard-en", "keyboard-ru"))) throw new IOException("candidate keyboard conflict pair unavailable");
        JsonArray steps = new JsonArray();
        for (String agent : entries.keySet()) {
            steps.add(setAgent(candidateCatalog, agent, false).toString());
            steps.add(setAgent(candidateCatalog, agent, true).toString());
        }
        for (String selected : List.of("keyboard-en", "keyboard-ru")) {
            String other = selected.equals("keyboard-en") ? "keyboard-ru" : "keyboard-en";
            steps.add(setAgent(candidateCatalog, other, true).toString());
            steps.add(setAgent(candidateCatalog, selected, true).toString());
            var current = observeRuntime(text(status(), "activeGeneration"));
            if (!current.desired(selected) || current.desired(other)) throw new IOException("actual keyboard conflict readback differs");
        }
        for (boolean enabled : List.of(false, true)) for (String agent : original.keySet())
            if (original.getAsJsonObject(agent).get("enabled").getAsBoolean() == enabled)
                steps.add(setAgent(candidateCatalog, agent, enabled).toString());
        var restored = observeRuntime(text(status(), "activeGeneration"));
        if (!original.equals(ru.voboost.tests.agents.AgentSettings.snapshot(candidateCatalog, restored)))
            throw new IOException("checkbox verification did not restore the complete original desired configuration");
        JsonObject proof = new JsonObject(); proof.addProperty("settings", settings.toString()); proof.add("steps", steps);
        proof.add("originalAgents", original); proof.addProperty("functionalAcceptance", false);
        Path file = work.resolve("agent-enablement-conflict-" + (++counter) + ".json"); RunFiles.write(file, proof); return file;
    }

    private StartupObservation startupObservation(String expectedBoot) throws Exception {
        BootData before = boot(null);
        if (!expectedBoot.equals(before.id()) || !before.complete() || !before.enforcing()) throw new IOException("startup read crossed boot");
        var result = StartupObservation.read(json(output(run(List.of("--observe"), Duration.ofSeconds(30), null, null))), expectedBoot);
        BootData after = boot(null);
        if (!expectedBoot.equals(after.id()) || !after.complete() || !after.enforcing()) throw new IOException("startup read crossed boot");
        return result;
    }

    /** Source-bound rejected startup radio mutation; this is only one branch of required case23. */
    public Path verifyStartupRejection(JsonObject candidateCatalog) throws Exception {
        Path settings = verifySettingsTab(); BootData before = boot(null);
        JsonObject inspected = status(); String generation = text(inspected, "activeGeneration");
        var prior = observeRuntime(generation); JsonObject agents = ru.voboost.tests.agents.AgentSettings.snapshot(candidateCatalog, prior);
        String original = prior.startup(), requested = original.equals("hidden") ? "interface" : "hidden";
        var process = StartupObservation.identity(startupProcesses(before.id()), before.id(), "com.qinggan.app.setting");
        if (!process.argv0().equals("com.qinggan.app.setting") || productRun == null) throw new IOException("Settings diagnostic target authority unavailable");
        var baseline = collectLogs(before.id());
        JsonObject request = action("fault-window"); request.addProperty("kind", "startup-mutation-lock"); request.addProperty("bootId", before.id());
        request.addProperty("manifestSha256", manifest); request.addProperty("generationId", generation); request.addProperty("catalogSha256", catalog);
        request.addProperty("targetPid", process.pid()); request.addProperty("targetStarttime", process.starttime());
        FaultWindow window = new FaultWindow(request, device, version);
        Path requestFile = work.resolve("startup-rejection-request-" + (++counter) + ".json"); RunFiles.write(requestFile, request);
        Path attemptedUi = work.resolve("startup-rejection-attempt-" + (++counter) + ".json"), restoredUi = work.resolve("startup-rejection-confirmed-" + (++counter) + ".json");
        JsonObject input = new JsonObject(); input.addProperty("manifest", manifest); input.addProperty("device", device);
        input.addProperty("phase", "startup-rejection"); input.addProperty("catalogIdentity", catalog); input.addProperty("desiredStartup", original);
        input.addProperty("priorPublisher", prior.publisher()); input.addProperty("priorRunningStartup", prior.runningStartup());
        input.addProperty("priorRevision", prior.revision()); input.add("expectedAgents", agents); input.add("diagnostic", request);
        Exception[] actionFailure = {null}; boolean[] attempted = {false};
        try (ProductRun.Operation operation = productRun.reserve(ProductRun.Kind.CONFIGURE, input)) {
            operation.observePriorBoot(before.id()); operation.dispatch();
            CliProcess.Listener listener = (stream, line, control) -> {
                if (stream != CliProcess.Stream.OUT) return null;
                window.observe(line);
                if (attempted[0] || actionFailure[0] != null) return null;
                try {
                    window.requireLive(); control.check();
                    var currentTarget = StartupObservation.identity(startupProcesses(before.id()), before.id(), "com.qinggan.app.setting");
                    if (!currentTarget.equals(process)) throw new IOException("Settings diagnostic target process changed before input");
                    long deadline = System.nanoTime() + Math.min(control.remaining().toNanos(), Duration.ofSeconds(35).toNanos());
                    JsonObject tree = settingsUi(before.id(), deadline);
                    var controls = ru.voboost.voyemu.StartupControls.read(ru.voboost.voyemu.UiEvidence.surface(tree, "primary"));
                    if (!controls.selected().equals(original)) throw new IOException("startup radio disagrees with canonical state before rejection");
                    requireStartupDescription(tree, original, controls.language()); RunFiles.write(attemptedUi, tree);
                    window.requireLive(); control.check(); tapNode(tree, controls.targets().get(requested)); attempted[0] = true;
                    while (System.nanoTime() < deadline) {
                        window.requireLive(); control.check(); tree = settingsUi(before.id(), deadline);
                        controls = ru.voboost.voyemu.StartupControls.read(ru.voboost.voyemu.UiEvidence.surface(tree, "primary"));
                        var current = observeRuntime(generation);
                        if (!original.equals(current.startup()) || !prior.revision().equals(current.revision())
                                || !agents.equals(ru.voboost.tests.agents.AgentSettings.snapshot(candidateCatalog, current)))
                            throw new IOException("rejected startup mutation changed canonical desired state");
                        current.requireDeferredFrom(prior);
                        if (controls.selected().equals(original)) {
                            requireStartupDescription(tree, original, controls.language()); RunFiles.write(restoredUi, tree); break;
                        }
                        Thread.sleep(100);
                    }
                    if (!Files.isRegularFile(restoredUi)) throw new IOException("startup radio rejection did not return to confirmed presentation");
                } catch (Exception failure) { actionFailure[0] = failure; }
                finally { window.release(); control.endInput(); }
                return null;
            };
            CliProcess.Result result = run(List.of("--emulator-check", requestFile.toString()), Duration.ofMinutes(2), listener, null);
            if (result.exit() != 0) throw new IOException("startup diagnostic process did not complete; raw streams retained");
            window.requireClosed();
            if (actionFailure[0] != null) throw actionFailure[0];
            if (!attempted[0] || !Files.isRegularFile(restoredUi)) throw new IOException("startup rejection actual input was not observed");
            var logs = collectLogs(before.id()); var effects = window.startupRejections(baseline, logs);
            if (effects.size() != 1) throw new IOException("one exact current root startup rejection effect is required");
            var current = observeRuntime(generation); current.requireDeferredFrom(prior);
            if (!original.equals(current.startup()) || !prior.revision().equals(current.revision())
                    || !agents.equals(ru.voboost.tests.agents.AgentSettings.snapshot(candidateCatalog, current)))
                throw new IOException("startup desired state changed after diagnostic cleanup");
            JsonObject proof = new JsonObject(); proof.add("request", request); proof.add("armed", window.armed()); proof.add("closed", window.closed());
            proof.addProperty("original", original); proof.addProperty("requested", requested); proof.addProperty("presentation", restoredUi.toString());
            proof.addProperty("baselineLogs", baseline.archive().toString()); proof.addProperty("currentLogs", logs.archive().toString());
            proof.addProperty("effectSequence", effects.get(0).sequence().toString()); proof.add("effect", effects.get(0).fields());
            proof.addProperty("fullCase23Acceptance", false);
            Path file = work.resolve("startup-rejection-verified-" + (++counter) + ".json"); RunFiles.write(file, proof);
            operation.complete(settings, requestFile, attemptedUi, restoredUi, baseline.archive(), logs.archive(), result.out(), result.err(), file); return file;
        }
    }

    private JsonObject javaStateEndpoint(ru.voboost.tests.agents.RuntimeObservation runtime,String agent)throws IOException {
        var rows=runtime.targets(agent).stream().filter(row->"com.qinggan.systemservice".equals(text(row,"process"))&&Set.of("active","waiting").contains(text(row,"state"))).toList();
        if(!runtime.desired(agent)||rows.size()!=1)throw new IOException("current Java state-owning target row unavailable");
        return object(rows.get(0),"currentEndpoint");
    }
    private ru.voboost.tests.agents.RootVehicleState.Authority javaStateAuthority(
            ru.voboost.tests.agents.RuntimeObservation runtime,String agent,String expectedBoot) throws Exception {
        JsonObject endpoint=javaStateEndpoint(runtime,agent);
        var process=StartupObservation.identity(startupProcesses(expectedBoot),expectedBoot,"com.qinggan.systemservice");
        if(!Integer.toString(process.pid()).equals(text(endpoint,"targetPid"))||!process.starttime().equals(text(endpoint,"targetStarttime")))throw new IOException("independent Java target identity differs");
        return new ru.voboost.tests.agents.RootVehicleState.Authority(expectedBoot,text(runtime.runtime(),"generationId"),catalog,"com.qinggan.systemservice",Integer.toString(process.pid()),process.starttime(),text(endpoint,"endpointEpoch"),text(endpoint,"desiredDocumentGeneration"));
    }

    private void requireOutageOwner(ru.voboost.tests.agents.RuntimeObservation runtime,String publisher,JsonObject request)throws Exception {
        if(!publisher.equals(runtime.publisher())||!text(request,"generationId").equals(text(runtime.runtime(),"generationId")))throw new IOException("outage producer/generation changed");
        var process=StartupObservation.identity(startupProcesses(text(request,"bootId")),text(request,"bootId"),"com.qinggan.systemservice");
        if(process.pid()!=integer(request,"targetPid")||!process.starttime().equals(text(request,"targetStarttime")))throw new IOException("outage Java target lifetime changed");
    }

    private JsonObject awaitJavaStop(JsonObject previousBinding,ru.voboost.tests.agents.RootVehicleState.Authority previous,
            String publisher,JsonObject request,FaultWindow window,CliProcess.Control control)throws Exception {
        long deadline=System.nanoTime()+Duration.ofSeconds(20).toNanos();
        while(System.nanoTime()<deadline) {
            if(window!=null)window.requireLive();if(control!=null)control.check();
            var current=observeRuntime(text(status(),"activeGeneration"));requireOutageOwner(current,publisher,request);
            if(current.desired(ru.voboost.tests.agents.StateOutageEvidence.AGENT)||!current.targets(ru.voboost.tests.agents.StateOutageEvidence.AGENT).isEmpty())continue;
            try {
                JsonObject binding=javaStateEndpoint(current,"vehicle-drive-forced-ev");
                var accepted=ru.voboost.tests.agents.JavaStopAcknowledgement.stopped(previousBinding,binding,previous,publisher);
                if(window!=null)window.requireLive();if(control!=null)control.check();
                JsonObject proof=new com.google.gson.Gson().toJsonTree(accepted).getAsJsonObject();proof.add("priorBinding",previousBinding);proof.add("stoppedBinding",binding);
                proof.add("desiredAgent",current.desiredConfiguration(ru.voboost.tests.agents.StateOutageEvidence.AGENT));proof.add("runtime",current.runtime());proof.addProperty("configRevision",current.revision());return proof;
            }catch(IOException pendingAck){ /* Missing or old product ACK remains unavailable; no mutation is retried. */ }
        }
        throw new IOException("current Java S document did not acknowledge complete PW removal/cleanup");
    }

    private Path stateOutage(JsonObject candidateCatalog,boolean cancel) throws Exception {
        String agent=ru.voboost.tests.agents.StateOutageEvidence.AGENT;
        verifySettingsTab();BootData before=boot(null);var initial=observeRuntime(text(status(),"activeGeneration"));
        var initialAuthority=javaStateAuthority(initial,agent,before.id());JsonObject initialBinding=javaStateEndpoint(initial,agent);
        ru.voboost.tests.agents.JavaStopAcknowledgement.prior(initialBinding,initialAuthority,initial.publisher());
        var process=StartupObservation.identity(startupProcesses(before.id()),before.id(),"com.qinggan.systemservice");
        JsonObject request=action("fault-window");request.addProperty("kind","vehicle-state-reply-loss");request.addProperty("bootId",before.id());request.addProperty("manifestSha256",manifest);
        request.addProperty("generationId",text(initial.runtime(),"generationId"));request.addProperty("catalogSha256",catalog);request.addProperty("targetPid",process.pid());request.addProperty("targetStarttime",process.starttime());
        setAgent(candidateCatalog,agent,false);JsonObject initialStop=awaitJavaStop(initialBinding,initialAuthority,initial.publisher(),request,null,null);
        Path initialStopFile=work.resolve("outage-initial-stop-"+(++counter)+".json");RunFiles.write(initialStopFile,initialStop);
        var baseline=collectLogs(before.id());var runtime=observeRuntime(text(status(),"activeGeneration"));requireOutageOwner(runtime,initial.publisher(),request);
        FaultWindow window=new FaultWindow(request,device,version);Path requestFile=work.resolve("state-outage-request-"+(++counter)+".json");RunFiles.write(requestFile,request);
        JsonObject input=new JsonObject();input.addProperty("phase","vehicle-state-outage");input.addProperty("priorPublisher",runtime.publisher());input.addProperty("agent",agent);input.addProperty("cancel",cancel);input.addProperty("manifest",manifest);input.addProperty("device",device);input.add("diagnostic",request);
        List<Path> artifacts=new ArrayList<>();artifacts.add(initialStopFile);artifacts.add(requestFile);artifacts.add(baseline.archive());
        Exception[] failure={null};boolean[] attempted={false};JsonObject[] stopProof={null};
        var authority=new ru.voboost.tests.agents.RootVehicleState.Authority[1];
        try(ProductRun.Operation operation=productRun.reserve(ProductRun.Kind.CONFIGURE,input)) {
            operation.observePriorBoot(before.id());operation.dispatch();
            CliProcess.Listener listener=(stream,line,control)->{
                if(stream!=CliProcess.Stream.OUT)return null;window.observe(line);
                if(attempted[0]||failure[0]!=null)return null;attempted[0]=true;
                try {
                    window.requireLive();control.check();String[] publisher=runtime.publisher().split(":",-1);
                    JsonObject armed=window.armed();
                    if(publisher.length!=3||!publisher[1].equals(armed.get("daemonPid").getAsString())||!publisher[2].equals(text(armed,"daemonStarttime")))throw new IOException("diagnostic producer differs from current publisher");
                    artifacts.add(setAgent(candidateCatalog,agent,true,operation,window));
                    var enabledRuntime=observeRuntime(text(status(),"activeGeneration"));requireOutageOwner(enabledRuntime,runtime.publisher(),request);
                    authority[0]=javaStateAuthority(enabledRuntime,agent,before.id());
                    long deadline=System.nanoTime()+Duration.ofSeconds(35).toNanos();
                    ru.voboost.tests.logging.InstallerLogs.Snapshot retried=null;
                    while(System.nanoTime()<deadline) {
                        window.requireLive();control.check();requireOutageOwner(observeRuntime(text(status(),"activeGeneration")),runtime.publisher(),request);var current=collectLogs(before.id());artifacts.add(current.archive());
                        if(window.stateReplyLosses(agent,baseline,current).size()>=6) {
                            try {ru.voboost.tests.agents.StateOutageEvidence.retries(baseline,current,authority[0]);retried=current;break;}
                            catch(IOException unavailable){if(!unavailable.getMessage().contains("two distinct"))throw unavailable;}
                        }
                    }
                    if(retried==null)throw new IOException("six actual dropped triad reads and two source-bound UNAVAILABLE retries missing");
                    if(cancel) {
                        var pendingRuntime=observeRuntime(text(status(),"activeGeneration"));requireOutageOwner(pendingRuntime,runtime.publisher(),request);
                        JsonObject pendingBinding=javaStateEndpoint(pendingRuntime,agent);
                        ru.voboost.tests.agents.JavaStopAcknowledgement.prior(pendingBinding,authority[0],runtime.publisher());
                        window.requireLive();control.check();artifacts.add(setAgent(candidateCatalog,agent,false,operation,window));
                        stopProof[0]=awaitJavaStop(pendingBinding,authority[0],runtime.publisher(),request,window,control);
                        var drained=collectLogs(before.id());var quiet=collectLogs(before.id());artifacts.add(drained.archive());artifacts.add(quiet.archive());
                        var stoppedAuthority=new ru.voboost.tests.agents.RootVehicleState.Authority(authority[0].boot(),authority[0].generation(),authority[0].catalog(),authority[0].process(),authority[0].pid(),authority[0].starttime(),authority[0].epoch(),text(stopProof[0],"stoppedDocument"));
                        ru.voboost.tests.agents.StateOutageEvidence.quiet(drained,quiet,stoppedAuthority,window);
                        requireOutageOwner(observeRuntime(text(status(),"activeGeneration")),runtime.publisher(),request);
                    }
                }catch(Exception error){failure[0]=error;}
                finally {window.release();control.endInput();}
                return null;
            };
            CliProcess.Result result=run(List.of("--emulator-check",requestFile.toString()),Duration.ofMinutes(2),listener,null);
            if(result.exit()!=0)throw new IOException("state outage diagnostic did not complete; retained process requires reconciliation");
            window.requireClosed();if(failure[0]!=null)throw failure[0];if(!attempted[0]||authority[0]==null)throw new IOException("state outage input was not dispatched and verified");
            requireOutageOwner(observeRuntime(text(status(),"activeGeneration")),runtime.publisher(),request);
            if(cancel)artifacts.add(setAgent(candidateCatalog,agent,true,operation));
            long deadline=System.nanoTime()+Duration.ofSeconds(35).toNanos();
            ru.voboost.tests.agents.RootVehicleState.Operation recovered=null;
            ru.voboost.tests.agents.RootVehicleState.Authority recoveryAuthority=cancel?null:authority[0];
            while(System.nanoTime()<deadline) {
                var currentRuntime=observeRuntime(text(status(),"activeGeneration"));requireOutageOwner(currentRuntime,runtime.publisher(),request);var rows=currentRuntime.targets(agent);
                var current=collectLogs(before.id());artifacts.add(current.archive());
                if(rows.size()==1&&"active".equals(text(rows.get(0),"state"))) {
                    var currentAuthority=javaStateAuthority(currentRuntime,agent,before.id());
                    if(recoveryAuthority==null)recoveryAuthority=currentAuthority;else if(!recoveryAuthority.equals(currentAuthority))throw new IOException("outage recovery endpoint/document context changed unexpectedly");
                    try {recovered=ru.voboost.tests.agents.RootVehicleState.recovered(agent,currentAuthority,baseline,current);break;}
                    catch(IOException unavailable){if(!unavailable.getMessage().contains("fresh present read"))throw unavailable;}
                }
            }
            if(recovered==null)throw new IOException("released outage did not regain active Java readiness and a fresh present root read");
            JsonObject rootObservation=json(output(run(List.of("--observe"),Duration.ofSeconds(30),null,null)));
            boolean rootValue=ru.voboost.voyemu.VehicleArmed.readPedestrian(rootObservation,before.id());
            if(rootValue!=recovered.value()||!before.id().equals(boot(null).id()))throw new IOException("independent root file differs from recovered operation or boot");
            var terminalRuntime=observeRuntime(text(status(),"activeGeneration"));requireOutageOwner(terminalRuntime,runtime.publisher(),request);
            if(!recoveryAuthority.equals(javaStateAuthority(terminalRuntime,agent,before.id())))throw new IOException("recovery context changed during independent root readback");
            Path rootFile=work.resolve("state-outage-root-readback-"+(++counter)+".json");RunFiles.write(rootFile,rootObservation);artifacts.add(rootFile);
            JsonObject proof=new JsonObject();proof.addProperty("cancel",cancel);proof.addProperty("bootId",before.id());proof.add("request",request);proof.add("armed",window.armed());proof.add("closed",window.closed());
            if(stopProof[0]!=null)proof.add("qualifiedStopAcknowledgement",stopProof[0]);
            proof.add("recoveredRootOperation",recovered.fields());JsonArray files=new JsonArray();artifacts.forEach(path->files.add(path.toString()));proof.add("evidence",files);
            Path file=work.resolve("state-outage-verified-"+(++counter)+".json");RunFiles.write(file,proof);artifacts.add(result.out());artifacts.add(result.err());artifacts.add(file);operation.complete(artifacts.toArray(Path[]::new));return file;
        }
    }

    public Path verifyVehicleDesiredOff(JsonObject candidateCatalog, boolean forcedEv) throws Exception {
        if (productRun == null || !productRun.owns(owned)) throw new IOException("current exclusive vehicle owner required");
        String agent = forcedEv ? "vehicle-drive-forced-ev" : "vehicle-drive-pedestrian-warning";
        var feature = forcedEv ? ru.voboost.voyemu.VehicleUi.Feature.FORCED_EV : ru.voboost.voyemu.VehicleUi.Feature.PEDESTRIAN_WARNING;
        boolean[] enabled = {true};
        var result = ru.voboost.voyemu.VehicleDesiredOff.run(new ru.voboost.voyemu.VehicleDesiredOff.Port() {
            public void enable(boolean value) throws Exception { verifySettingsTab(); setAgent(candidateCatalog, agent, value); enabled[0] = value; }
            public void select(boolean selected) throws Exception { if (forcedEv) fevSelectOem(selected); else vehicleSelect(feature, selected); }
            public void warmCycle() throws Exception { fevWarmCycle(); }
            public void coldReboot() throws Exception { fevColdReboot(); }
            public ru.voboost.voyemu.VehicleDesiredOff.State observe() throws Exception {
                String boot = boot(null).id();
                var vehicle = vehicleObserve(feature);
                var runtime = observeRuntime(text(status(), "activeGeneration"));
                long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
                while (runtime.desired(agent) == enabled[0] && (enabled[0] ? runtime.targets(agent).isEmpty() : !runtime.targets(agent).isEmpty())
                        && System.nanoTime() < deadline) {
                    Thread.sleep(100);
                    runtime = observeRuntime(text(status(), "activeGeneration"));
                }
                if (!boot.equals(boot(null).id())) throw new IOException("desired OFF observation crossed boot");
                JsonObject proof = new JsonObject();
                proof.addProperty("agent", agent); proof.addProperty("bootId", boot);
                proof.addProperty("publisher", runtime.publisher()); proof.addProperty("revision", runtime.revision());
                proof.addProperty("enabled", runtime.desired(agent)); proof.addProperty("targets", runtime.targets(agent).size());
                proof.addProperty("vehicle", vehicle.evidence().toString());
                Path file = work.resolve("desired-off-state-" + (++counter) + ".json"); RunFiles.write(file, proof);
                return new ru.voboost.voyemu.VehicleDesiredOff.State(boot, runtime.publisher(), runtime.desired(agent), runtime.targets(agent).size(), vehicle, file);
            }
        }, forcedEv);
        JsonObject proof = new JsonObject(); proof.addProperty("agent", agent);
        proof.addProperty("scope", "sampled-desired-off-warm-cold");
        proof.addProperty("continuousCanOff", false);
        JsonArray states = new JsonArray(); result.states().forEach(state -> states.add(state.evidence().toString())); proof.add("states", states);
        Path file = work.resolve("desired-off-verified-" + (++counter) + ".json"); RunFiles.write(file, proof); return file;
    }

    public Path verifyVehicleDisableCancellation(JsonObject candidateCatalog, boolean forcedEv) throws Exception {
        if (productRun == null || !productRun.owns(owned)) throw new IOException("current exclusive vehicle owner required");
        return vehicleDisableCancellation(candidateCatalog, forcedEv);
    }

    private Path vehicleDisableCancellation(JsonObject candidateCatalog,boolean forcedEv)throws Exception {
        String agent=forcedEv?"vehicle-drive-forced-ev":"vehicle-drive-pedestrian-warning";
        var feature=forcedEv?ru.voboost.voyemu.VehicleUi.Feature.FORCED_EV:ru.voboost.voyemu.VehicleUi.Feature.PEDESTRIAN_WARNING;
        String boot=boot(null).id();String[] stoppedProcesses={null};Path[] warmEvidence={null};boolean[] expected={forcedEv};
        var result=ru.voboost.voyemu.VehicleDisableCancellation.run(new ru.voboost.voyemu.VehicleDisableCancellation.Port(){
            public void enable(boolean enabled)throws Exception {
                verifySettingsTab();setAgent(candidateCatalog,agent,enabled);
                if(!enabled)stoppedProcesses[0]=startupProcesses(boot);
                else expected[0]=true;
            }
            public void select(boolean selected)throws Exception {if(forcedEv)fevSelectOem(selected);else vehicleSelect(feature,selected);expected[0]=selected;}
            public ru.voboost.voyemu.VehiclePersistence.State observe()throws Exception {
                long deadline=System.nanoTime()+Duration.ofSeconds(35).toNanos();ru.voboost.voyemu.VehiclePersistence.State last=null;
                while(System.nanoTime()<deadline){last=vehicleObserve(feature);if(last.checked()==expected[0]&&last.can().selected(forcedEv)==expected[0])return last;}
                if(last==null)throw new IOException("actual vehicle cancellation sample missing");return last;
            }
            public Path requireArmed()throws Exception {
                long deadline=System.nanoTime()+Duration.ofSeconds(15).toNanos();
                while(System.nanoTime()<deadline) {
                    var current=observeRuntime(text(status(),"activeGeneration"));
                    if(!boot.equals(text(current.runtime().getAsJsonObject("publisher"),"bootId")))throw new IOException("disable cancellation root read crossed boot");
                    boolean armed=forcedEv?ru.voboost.voyemu.VehicleArmed.read(current):ru.voboost.voyemu.VehicleArmed.readPedestrian(current);
                    if(armed)return lastRuntimeOfficial;
                }
                throw new IOException("actual armed root boolean missing");
            }
            public Path requireStopped()throws Exception {
                var current=observeRuntime(text(status(),"activeGeneration"));String processes=startupProcesses(boot);
                if(!beforeBoot(boot)||current.desired(agent)||!current.targets(agent).isEmpty()||stoppedProcesses[0]==null)throw new IOException("disabled vehicle target remains desired or published");
                StartupObservation.requireSameProcesses(stoppedProcesses[0],processes,boot);JsonObject proof=new JsonObject();proof.addProperty("agent",agent);proof.addProperty("bootId",boot);proof.addProperty("processes",processes);proof.addProperty("processesBeforeStop",stoppedProcesses[0]);proof.addProperty("runtimeOfficial",lastRuntimeOfficial.toString());proof.addProperty("publisher",current.publisher());
                Path file=work.resolve("disable-cancellation-stopped-"+(++counter)+".json");RunFiles.write(file,proof);return file;
            }
            private boolean beforeBoot(String expected)throws Exception {return expected.equals(boot(null).id());}
            public void warmCycle()throws Exception {warmEvidence[0]=fevWarmCycle();expected[0]=!forcedEv;}
        },forcedEv);
        JsonObject proof=new JsonObject();proof.addProperty("agent",agent);proof.addProperty("bootId",boot);JsonArray states=new JsonArray(),files=new JsonArray();for(var state:result.states())states.add(state.evidence().toString());result.evidence().forEach(path->files.add(path.toString()));proof.add("states",states);proof.add("rootAndStop",files);proof.addProperty("sessionId",owned.session().id());proof.addProperty("warmCycle",warmEvidence[0].toString());
        Path file=work.resolve("disable-cancellation-"+(++counter)+".json");RunFiles.write(file,proof);return file;
    }

    private JsonObject nativeOutageTarget(ru.voboost.tests.agents.RuntimeObservation runtime)throws IOException {
        JsonObject found=null;for(JsonObject row:runtime.targets("vehicle-drive-pedestrian-warning"))if("com.qinggan.canbus.service".equals(text(row,"process"))){if(found!=null)throw new IOException("ambiguous native state input endpoint");found=row;}
        if(found==null||!found.has("currentEndpoint"))throw new IOException("current native state input endpoint unavailable");return found;
    }
    private JsonObject rootPedestrianState(String boot)throws Exception {
        JsonObject raw=json(output(run(List.of("--observe"),Duration.ofSeconds(30),null,null)));ru.voboost.voyemu.VehicleArmed.readPedestrian(raw,boot);Path file=work.resolve("native-outage-root-"+(++counter)+".json");RunFiles.write(file,raw);return raw;
    }
    /** The bounded stream advances only on its actual callback; it never waits for its own stdout. */
    private Path nativeStateOutage(JsonObject candidateCatalog,String kind,boolean cancel)throws Exception {
        if(!Set.of("native-state-read-unavailable","native-state-write-unavailable").contains(kind))throw new IOException("closed native state fault required");
        final String agent="vehicle-drive-pedestrian-warning";BootData before=boot(null);var original=observeRuntime(text(status(),"activeGeneration"));Path originalRuntime=lastRuntimeOfficial;JsonObject originalRoot=rootPedestrianState(before.id());boolean armedValue=ru.voboost.voyemu.VehicleArmed.readPedestrian(originalRoot,before.id());boolean originalEnabled=original.desired(agent);var originalOem=vehicleObserve(ru.voboost.voyemu.VehicleUi.Feature.PEDESTRIAN_WARNING);
        if(!originalEnabled){verifySettingsTab();setAgent(candidateCatalog,agent,true);}var initial=observeRuntime(text(status(),"activeGeneration"));JsonObject initialTarget=nativeOutageTarget(initial);String processes=startupProcesses(before.id());var owner=StartupObservation.identity(processes,before.id(),"voboost-daemon");var inputProcess=StartupObservation.identity(processes,before.id(),"com.qinggan.canbus.service");java.time.Instant initialLogStart=java.time.Instant.now();var initialLogs=collectLogs(before.id());java.time.Instant initialLogEnd=java.time.Instant.now();
        JsonObject endpoint=object(initialTarget,"currentEndpoint");if(!Integer.toString(inputProcess.pid()).equals(text(endpoint,"targetPid"))||!inputProcess.starttime().equals(text(endpoint,"targetStarttime")))throw new IOException("outage input endpoint is not the independently observed canbus process");
        verifySettingsTab();setAgent(candidateCatalog,agent,false);vehicleSelect(ru.voboost.voyemu.VehicleUi.Feature.PEDESTRIAN_WARNING,!armedValue);verifySettingsTab();
        var current=observeRuntime(text(status(),"activeGeneration"));if(!current.publisher().equals(initial.publisher()))throw new IOException("native outage setup changed root writer");JsonObject request=action("fault-window");request.addProperty("kind",kind);request.addProperty("bootId",before.id());request.addProperty("manifestSha256",manifest);request.addProperty("generationId",text(initial.runtime(),"generationId"));request.addProperty("catalogSha256",catalog);request.addProperty("targetPid",inputProcess.pid());request.addProperty("targetStarttime",inputProcess.starttime());
        FaultWindow window=new FaultWindow(request,device,version);JsonObject intent=new JsonObject();intent.addProperty("phase","vehicle-state-outage");intent.addProperty("agent",agent);intent.addProperty("cancel",cancel);intent.addProperty("priorPublisher",initial.publisher());intent.addProperty("manifest",manifest);intent.addProperty("device",device);intent.add("diagnostic",request);
        final int[] stage={0},snapshots={0};final JsonObject[] terminalRoot={null},terminalTarget={null};final java.math.BigInteger[] stoppedAt={null};List<Path> artifacts=new ArrayList<>();
        try(ProductRun.Operation operation=productRun.reserve(ProductRun.Kind.CONFIGURE,intent)) {
            operation.observePriorBoot(before.id());operation.dispatch();Path requestFile=work.resolve("native-outage-request-"+(++counter)+".json");RunFiles.write(requestFile,request);artifacts.add(requestFile);
            CliProcess.Listener callback=(stream,line,control)->{
                if(stream!=CliProcess.Stream.OUT)return null;JsonObject envelope=json(line);String phase=text(envelope,"phase");
                if(phase.equals("armed")){if(stage[0]!=0)throw new IOException("native outage arm repeated");control.bindNativeDiagnostic(window);window.requirePublisher(initial.publisher());control.sendNativeDiagnostic(window,FaultWindow.NativeControl.SNAPSHOT);stage[0]=1;return null;}
                if(phase.equals("closed")){window.observe(line);stage[0]=8;return null;}
                JsonObject row=control.captureNativeDiagnostic(window);
                if(phase.equals("native-closed")){if(stage[0]!=7)throw new IOException("native outage closed before verified recovery");stage[0]=8;return null;}
                if(phase.equals("released")){if(stage[0]!=5)throw new IOException("native release out of order");if(cancel)artifacts.add(setAgent(candidateCatalog,agent,true,operation,window));stage[0]=6;Thread.sleep(250);control.sendNativeDiagnostic(window,FaultWindow.NativeControl.SNAPSHOT);return null;}
                if(!phase.equals("snapshot")||++snapshots[0]>11)throw new IOException("bounded native snapshot progression exceeded");
                switch(stage[0]) {
                    case 1 -> {artifacts.add(setAgent(candidateCatalog,agent,true,operation,window));stage[0]=2;Thread.sleep(350);control.sendNativeDiagnostic(window,FaultWindow.NativeControl.SNAPSHOT);}
                    case 2 -> {
                        boolean fault=kind.equals("native-state-read-unavailable")?row.get("droppedReads").getAsLong()>=2&&row.get("waitStateSteps").getAsLong()>=2:row.get("failedWrites").getAsLong()>=2;
                        if(!fault){Thread.sleep(500);control.sendNativeDiagnostic(window,FaultWindow.NativeControl.SNAPSHOT);}
                        else if(cancel){artifacts.add(setAgent(candidateCatalog,agent,false,operation,window));stage[0]=3;control.sendNativeDiagnostic(window,FaultWindow.NativeControl.SNAPSHOT);}
                        else{stage[0]=5;control.sendNativeDiagnostic(window,FaultWindow.NativeControl.RELEASE);}
                    }
                    case 3 -> {if(truth(row,"stopped")){stoppedAt[0]=new java.math.BigInteger(row.get("observedMonotonicMs").getAsString());stage[0]=4;Thread.sleep(1100);}else Thread.sleep(250);control.sendNativeDiagnostic(window,FaultWindow.NativeControl.SNAPSHOT);}
                    case 4 -> {if(!truth(row,"stopped")||new java.math.BigInteger(row.get("observedMonotonicMs").getAsString()).subtract(stoppedAt[0]).compareTo(java.math.BigInteger.valueOf(1000))<0)throw new IOException("cancelled native worker lacks its separate quiet interval");stage[0]=5;control.sendNativeDiagnostic(window,FaultWindow.NativeControl.RELEASE);}
                    case 6 -> {
                        if(row.get("recoveredValues").getAsLong()<1){Thread.sleep(350);control.sendNativeDiagnostic(window,FaultWindow.NativeControl.SNAPSHOT);}
                        else{terminalRoot[0]=rootPedestrianState(before.id());terminalTarget[0]=nativeOutageTarget(observeRuntime(text(status(),"activeGeneration")));control.endNativeDiagnostic(window);stage[0]=7;}
                    }
                    default -> throw new IOException("native snapshot in an unexpected program phase");
                }
                return null;
            };
            CliProcess.Result result=run(List.of("--emulator-check",requestFile.toString()),Duration.ofSeconds(75),callback,null);if(result.exit()!=0||stage[0]!=8)throw new IOException("native outage stream did not complete its actual bounded lifecycle");window.requireClosed();
            String finalProcesses=startupProcesses(before.id());var finalOwner=StartupObservation.identity(finalProcesses,before.id(),"voboost-daemon");var finalInput=StartupObservation.identity(finalProcesses,before.id(),"com.qinggan.canbus.service");java.time.Instant finalLogStart=java.time.Instant.now();var finalLogs=collectLogs(before.id());java.time.Instant finalLogEnd=java.time.Instant.now();JsonObject arm=window.armed();
            var scope=new ru.voboost.tests.agents.NativeStateOutageAdmission.Scope(kind,text(arm,"token"),before.id(),text(request,"generationId"),catalog,initialLogs.writerId(),owner.pid(),Long.parseLong(owner.starttime()),inputProcess.pid(),Long.parseLong(inputProcess.starttime()),Long.parseLong(text(endpoint,"endpointEpoch")),Long.parseLong(text(arm,"expiresMonotonicMs")));
            var observations=new ru.voboost.tests.agents.NativeStateOutageAdmission.Observations(initialTarget,terminalTarget[0],new ru.voboost.tests.logging.InstallerLogs.CurrentSession(initialLogs.boot(),initialLogs.writerId(),initialLogs.watermark()),new ru.voboost.tests.logging.InstallerLogs.CurrentSession(finalLogs.boot(),finalLogs.writerId(),finalLogs.watermark()),owner,finalOwner,inputProcess,finalInput);
            ru.voboost.tests.agents.NativeStateOutageAdmission.admit(scope,window,cancel,originalRoot,terminalRoot[0],observations);
            JsonObject proof=new JsonObject();proof.addProperty("agent",agent);proof.addProperty("serial",device);proof.addProperty("releaseIdentity",version);proof.addProperty("exit",result.exit());proof.addProperty("stdout",result.out().toString());proof.addProperty("stderr",result.err().toString());proof.addProperty("operation",operation.receiptPath().toString());proof.add("initialLogCapture",logCapture(initialLogs,initialLogStart,initialLogEnd));proof.add("terminalLogCapture",logCapture(finalLogs,finalLogStart,finalLogEnd));proof.addProperty("kind",kind);proof.addProperty("cancel",cancel);proof.add("request",request);proof.add("armed",arm);proof.add("closed",window.closed());JsonArray receipts=new JsonArray();window.nativeReceipts().forEach(receipts::add);proof.add("receipts",receipts);proof.add("originalRoot",originalRoot);proof.add("terminalRoot",terminalRoot[0]);proof.add("initialTarget",initialTarget);proof.add("terminalTarget",terminalTarget[0]);proof.addProperty("ownerProcessesBefore",processes);proof.addProperty("ownerProcessesAfter",finalProcesses);proof.addProperty("initialLogs",initialLogs.archive().toString());proof.addProperty("terminalLogs",finalLogs.archive().toString());
            Path file=work.resolve("native-outage-verified-"+(++counter)+".json");RunFiles.write(file,proof);artifacts.add(file);artifacts.add(result.out());artifacts.add(result.err());artifacts.add(initialLogs.archive());artifacts.add(finalLogs.archive());operation.complete(artifacts.toArray(Path[]::new));
            // Only a known terminal fault and recovered root value authorize normal restoration.
            verifySettingsTab();setAgent(candidateCatalog,agent,originalEnabled);var state=vehicleObserve(ru.voboost.voyemu.VehicleUi.Feature.PEDESTRIAN_WARNING);if(state.checked()!=originalOem.checked())vehicleSelect(ru.voboost.voyemu.VehicleUi.Feature.PEDESTRIAN_WARNING,originalOem.checked());
            var restored=observeRuntime(text(status(),"activeGeneration"));if(!ru.voboost.tests.agents.AgentSettings.snapshot(candidateCatalog,original).equals(ru.voboost.tests.agents.AgentSettings.snapshot(candidateCatalog,restored)))throw new IOException("native outage did not restore original full desired configuration");JsonObject restoration=outageRestoration(agent,before.id(),originalRuntime,originalRoot,originalOem);JsonObject complete=new JsonObject();complete.addProperty("nativeProof",file.toString());complete.add("restoration",restoration);complete.add("scope",currentOutageScope());Path completed=work.resolve("native-outage-complete-"+(++counter)+".json");RunFiles.write(completed,complete);return completed;
        }
    }
    private static JsonObject logCapture(ru.voboost.tests.logging.InstallerLogs.Snapshot logs,java.time.Instant start,java.time.Instant end) {
        JsonObject proof=new JsonObject();proof.addProperty("archive",logs.archive().toString());proof.addProperty("startedAt",start.toString());proof.addProperty("endedAt",end.toString());return proof;
    }
    private JsonObject rootVehicleState(String boot,boolean forcedEv)throws Exception {
        JsonObject raw=json(output(run(List.of("--observe"),Duration.ofSeconds(30),null,null)));if(forcedEv)ru.voboost.voyemu.VehicleArmed.read(raw,boot);else ru.voboost.voyemu.VehicleArmed.readPedestrian(raw,boot);return raw;
    }
    private JsonObject outageRestoration(String agent,String boot,Path originalRuntime,JsonObject originalRoot,ru.voboost.voyemu.VehiclePersistence.State originalOem)throws Exception {
        boolean forcedEv=agent.equals("vehicle-drive-forced-ev");var feature=forcedEv?ru.voboost.voyemu.VehicleUi.Feature.FORCED_EV:ru.voboost.voyemu.VehicleUi.Feature.PEDESTRIAN_WARNING;
        var currentRuntimeObservation=observeRuntime(text(status(),"activeGeneration"));String generation=text(currentRuntimeObservation.runtime(),"generationId");Path currentRuntime=lastRuntimeOfficial;var currentOem=vehicleObserve(feature);JsonObject currentRoot=rootVehicleState(boot,forcedEv);
        boolean original=forcedEv?ru.voboost.voyemu.VehicleArmed.read(originalRoot,boot):ru.voboost.voyemu.VehicleArmed.readPedestrian(originalRoot,boot),current=forcedEv?ru.voboost.voyemu.VehicleArmed.read(currentRoot,boot):ru.voboost.voyemu.VehicleArmed.readPedestrian(currentRoot,boot);
        if(original!=current||currentOem.checked()!=originalOem.checked()||!boot.equals(boot(null).id()))throw new IOException("confirmed outage program did not restore original root/OEM/boot");
        JsonObject proof=new JsonObject();proof.addProperty("agent",agent);proof.addProperty("bootId",boot);proof.addProperty("sessionId",owned.session().id());proof.addProperty("releaseIdentity",version);proof.addProperty("manifest",manifest);proof.addProperty("catalog",catalog);proof.addProperty("generation",generation);proof.addProperty("originalRuntime",originalRuntime.toString());proof.addProperty("currentRuntime",currentRuntime.toString());proof.add("originalRoot",originalRoot);proof.add("currentRoot",currentRoot);proof.addProperty("originalOem",originalOem.evidence().toString());proof.addProperty("currentOem",currentOem.evidence().toString());return proof;
    }
    /** Normal restoration follows only the confirmed program; an unknown input never reaches it. */
    private Path outageDisableCancellation(JsonObject candidateCatalog,boolean forcedEv)throws Exception {
        String agent=forcedEv?"vehicle-drive-forced-ev":"vehicle-drive-pedestrian-warning",boot=boot(null).id();var original=observeRuntime(text(status(),"activeGeneration"));Path originalRuntime=lastRuntimeOfficial;JsonObject originalRoot=rootVehicleState(boot,forcedEv);boolean armed=forcedEv?ru.voboost.voyemu.VehicleArmed.read(originalRoot,boot):ru.voboost.voyemu.VehicleArmed.readPedestrian(originalRoot,boot);boolean enabled=original.desired(agent);var feature=forcedEv?ru.voboost.voyemu.VehicleUi.Feature.FORCED_EV:ru.voboost.voyemu.VehicleUi.Feature.PEDESTRIAN_WARNING;var oem=vehicleObserve(feature);
        if(enabled&&oem.checked()!=(forcedEv?armed:!armed))throw new IOException("original enabled controller root and OEM selection are not settled");
        Path program=vehicleDisableCancellation(candidateCatalog,forcedEv);
        if(forcedEv)fevSelectOem(armed);else vehicleSelect(feature,!armed);
        long deadline=System.nanoTime()+Duration.ofSeconds(15).toNanos();boolean restored=false;while(System.nanoTime()<deadline){JsonObject root=rootVehicleState(boot,forcedEv);if((forcedEv?ru.voboost.voyemu.VehicleArmed.read(root,boot):ru.voboost.voyemu.VehicleArmed.readPedestrian(root,boot))==armed){restored=true;break;}Thread.sleep(100);}if(!restored)throw new IOException("ordinary OEM selection did not restore original durable intent");
        verifySettingsTab();setAgent(candidateCatalog,agent,enabled);if(!enabled&&vehicleObserve(feature).checked()!=oem.checked()){if(forcedEv)fevSelectOem(oem.checked());else vehicleSelect(feature,oem.checked());}
        var current=observeRuntime(text(status(),"activeGeneration"));if(!ru.voboost.tests.agents.AgentSettings.snapshot(candidateCatalog,original).equals(ru.voboost.tests.agents.AgentSettings.snapshot(candidateCatalog,current)))throw new IOException("disable cancellation did not restore full desired configuration");
        JsonObject proof=new JsonObject();proof.addProperty("program",program.toString());proof.add("restoration",outageRestoration(agent,boot,originalRuntime,originalRoot,oem));proof.add("scope",currentOutageScope());Path file=work.resolve("outage-disable-restored-"+(++counter)+".json");RunFiles.write(file,proof);return file;
    }
    /** Current read-only authority from the live owner, independent of any retained subcase. */
    public JsonObject currentOutageScope()throws Exception {
        if(productRun==null||!productRun.owns(owned)||productRun.pending()!=null)throw new IOException("idle current owned case25 scope required");
        BootData boot=boot(null);if(!boot.complete()||!boot.enforcing())throw new IOException("current case25 boot is not ready Enforcing");JsonObject status=nativeRead(client.status(id(),profile,owned.session()),"outage-current-scope");requireOwnedStatus(status);var runtime=observeRuntime(text(status(),"activeGeneration"));String processes=startupProcesses(boot.id());var owner=StartupObservation.identity(processes,boot.id(),"voboost-daemon");var input=StartupObservation.identity(processes,boot.id(),"com.qinggan.canbus.service");var logs=collectLogs(boot.id());JsonObject run=productRun.identity(),scope=new JsonObject();
        for(String key:List.of("runId","work","machine"))scope.addProperty(key,text(run,key));scope.addProperty("sessionId",owned.session().id());scope.addProperty("bootId",boot.id());scope.addProperty("serial",device);scope.addProperty("manifest",manifest);scope.addProperty("catalog",catalog);scope.addProperty("releaseIdentity",version);scope.addProperty("generation",text(runtime.runtime(),"generationId"));scope.addProperty("publisher",runtime.publisher());scope.addProperty("writerId",logs.writerId());scope.addProperty("ownerPid",Integer.toString(owner.pid()));scope.addProperty("ownerStarttime",owner.starttime());scope.addProperty("inputPid",Integer.toString(input.pid()));scope.addProperty("inputStarttime",input.starttime());scope.addProperty("installer",binary.toRealPath().toString());scope.addProperty("driver",emulatorDriver.toString());if(!boot.id().equals(boot(null).id()))throw new IOException("current case25 scope crossed boot");return scope;
    }
    public Path verifyStateOutage(JsonObject candidateCatalog)throws Exception {
        requireHealthy();verifySettingsTab();JsonObject caseScope=currentOutageScope();BootData before=boot(null);var runtime=observeRuntime(text(status(),"activeGeneration"));String processes=startupProcesses(before.id());Set<String> active=new java.util.HashSet<>();JsonArray targets=new JsonArray();
        for(String id:ru.voboost.tests.agents.AgentSettings.checkboxes(candidateCatalog).keySet())for(JsonObject target:runtime.targets(id))if("active".equals(text(target,"state"))&&target.has("currentEndpoint")){String name=text(target,"process");if(!Set.of("com.qinggan.canbus.service","com.qinggan.app.setting","com.qinggan.app.launcher").contains(name))continue;var observed=StartupObservation.identity(processes,before.id(),name);JsonObject endpoint=object(target,"currentEndpoint");if(!Integer.toString(observed.pid()).equals(text(endpoint,"targetPid"))||!observed.starttime().equals(text(endpoint,"targetStarttime")))throw new IOException("multi-target load is not independently observed");active.add(name);targets.add(target);}
        if(active.size()<2)throw new IOException("case25 requires multiple independently observed live target processes");JsonObject subchecks=new JsonObject();for(String kind:List.of("native-state-read-unavailable","native-state-write-unavailable"))for(boolean cancel:List.of(false,true))subchecks.addProperty("native-pw-"+(kind.contains("-read-")?"read":"write")+"-"+(cancel?"cancellation":"recovery"),nativeStateOutage(candidateCatalog,kind,cancel).toString());for(boolean forcedEv:List.of(true,false))subchecks.addProperty("runtime-disable-cancellation-"+(forcedEv?"fev":"pw"),outageDisableCancellation(candidateCatalog,forcedEv).toString());
        JsonObject proof=new JsonObject();proof.addProperty("caseNumber",25);proof.addProperty("semanticFunctionalStatus","PASS");proof.addProperty("functionalAcceptance",true);proof.addProperty("reason","All four native PW read/write recovery/cancellation and both actual FEV/PW disabled same-boot sleep/wake programs, with independent owner/input/root observations and original-state restoration, re-evaluated");proof.add("multiTarget",targets);proof.add("subchecks",subchecks);proof.add("scope",caseScope);ru.voboost.tests.agents.StateOutageEvidence.requireWhole(proof,binary.getParent().getParent().resolve("Resources/agents.json"),currentOutageScope());Path file=work.resolve("state-outage-native-case25-"+(++counter)+".json");RunFiles.write(file,proof);return file;
    }

    private ru.voboost.tests.agents.I18nObservation admittedI18n;
    private final List<Path> currentAndroidI18nEvidence=new ArrayList<>();
    private ru.voboost.voyemu.I18nSuiteRestoration.Observation originalI18nSuite;
    private ru.voboost.voyemu.I18nSuiteRestoration.Observation observeI18nSuite()throws Exception {
        BootData before=boot(null);JsonObject pickerTree=languagePicker(before.id());String language=ru.voboost.voyemu.SystemLanguagePicker.read(ru.voboost.voyemu.UiEvidence.surface(pickerTree,"primary")).language();requireSystemLocale(before.id(),language);
        var runtime=observeRuntime(text(status(),"activeGeneration"));boolean enabled=runtime.desired("i18n-ru");BootData after=boot(null);if(!before.id().equals(after.id())||!after.complete()||!after.enforcing())throw new IOException("i18n original state crossed boot/policy");
        JsonObject proof=new JsonObject();proof.addProperty("bootId",before.id());proof.addProperty("language",language);proof.addProperty("enabled",enabled);proof.addProperty("publisher",runtime.publisher());proof.addProperty("revision",runtime.revision());proof.add("picker",pickerTree);Path evidence=work.resolve("i18n-suite-observed-"+(++counter)+".json");RunFiles.write(evidence,proof);return new ru.voboost.voyemu.I18nSuiteRestoration.Observation(language,enabled,evidence);
    }
    /** Called only after cases22/20/21/23 return normally; unknown operations never trigger restoration. */
    public Path finishI18nSuite(JsonObject candidateCatalog)throws Exception {
        JsonObject proof=new JsonObject();if(originalI18nSuite==null){proof.addProperty("status","not-started");proof.addProperty("reason","No admitted i18n suite changed the original configuration");}
        else {
            var restored=ru.voboost.voyemu.I18nSuiteRestoration.restore(originalI18nSuite,new ru.voboost.voyemu.I18nSuiteRestoration.Port(){
                public ru.voboost.voyemu.I18nSuiteRestoration.Observation observe()throws Exception{return observeI18nSuite();}
                public Path select(String locale)throws Exception{return startupLanguage(locale);}
                public Path disable()throws Exception {
                    var before=readI18n();Path changed=setAgent(candidateCatalog,"i18n-ru",false);var disabled=awaitI18n("zh",before,true);admittedI18n=null;BootData boot=boot(null);JsonObject tree=languagePicker(boot.id());var picker=ru.voboost.voyemu.SystemLanguagePicker.read(ru.voboost.voyemu.UiEvidence.surface(tree,"primary"));if(picker.russian()!=null)throw new IOException("disabled Russian language remains in the genuine OEM picker");requireSystemLocale(boot.id(),originalI18nSuite.locale());JsonObject receipt=new JsonObject();receipt.addProperty("config",changed.toString());receipt.add("nativeDisabledOwners",disabled.evidence());receipt.add("picker",tree);Path file=work.resolve("i18n-suite-disabled-"+(++counter)+".json");RunFiles.write(file,receipt);return file;
                }
            });
            proof.addProperty("status","restored");proof.addProperty("language",restored.current().locale());proof.addProperty("enabled",restored.current().enabled());JsonArray files=new JsonArray();restored.evidence().forEach(path->files.add(path.toString()));proof.add("artifacts",files);originalI18nSuite=null;
        }
        Path file=work.resolve("i18n-suite-restoration-"+(++counter)+".json");RunFiles.write(file,proof);return file;
    }
    private ru.voboost.tests.agents.I18nObservation readI18n()throws Exception {return ru.voboost.tests.agents.I18nObservation.read(observeRuntime(text(status(),"activeGeneration")));}
    private ru.voboost.tests.agents.I18nObservation awaitI18n(String locale,ru.voboost.tests.agents.I18nObservation prior,boolean disabled)throws Exception {
        long deadline=System.nanoTime()+Duration.ofSeconds(30).toNanos();IOException last=null;
        while(System.nanoTime()<deadline){try{var current=readI18n();if(disabled)current.requireDisabledRestart(prior);else current.requireLocale(locale,prior);return current;}catch(ru.voboost.tests.agents.I18nObservation.Pending pending){last=pending;}Thread.sleep(100);}
        throw new IOException("bounded native per-owner locale/restart evidence unavailable",last);
    }
    /** Every invocation fences fresh owner probes after the confirmed input or new cold boot. */
    private Path requireCurrentAndroidLocale(String expectedBoot,String locale)throws Exception {
        if(!Set.of("en","ru","zh").contains(locale))throw new IOException("closed actual owner locale required");
        long deadline=System.nanoTime()+Duration.ofSeconds(45).toNanos();IOException pending=null;JsonArray attempts=new JsonArray();
        requireSystemLocale(expectedBoot,locale);
        var first=observeRuntime(text(status(),"activeGeneration"));
        ru.voboost.tests.agents.I18nResourceReadbacks.PublicationFence fence=null;
        while(System.nanoTime()<deadline) {
            requireSystemLocale(expectedBoot,locale);var runtime=observeRuntime(text(status(),"activeGeneration"));
            JsonObject attempt=new JsonObject();attempt.addProperty("publisher",runtime.publisher());attempt.addProperty("revision",runtime.revision());
            try {
                if(fence==null){fence=ru.voboost.tests.agents.I18nResourceReadbacks.afterPublication(first,runtime);attempt.addProperty("stage","post-input-publication-fence");attempts.add(attempt);Thread.sleep(100);continue;}
                var actual=ru.voboost.tests.agents.I18nResourceReadbacks.readCurrentAfter(runtime,locale,fence);requireSystemLocale(expectedBoot,locale);
                JsonObject proof=new JsonObject();proof.addProperty("bootId",expectedBoot);proof.addProperty("locale",locale);proof.addProperty("kind",actual.kind());proof.addProperty("actualOwnerCount",actual.size());proof.addProperty("publisher",runtime.publisher());proof.addProperty("revision",runtime.revision());proof.add("runtime",runtime.runtime());proof.add("attempts",attempts);
                Path file=work.resolve("i18n-current-android-"+locale+"-"+(++counter)+".json");RunFiles.write(file,proof);currentAndroidI18nEvidence.add(file);return file;
            }catch(IOException unavailable){pending=unavailable;attempt.addProperty("reason",unavailable.getMessage());attempts.add(attempt);}
            Thread.sleep(250);
        }
        JsonObject proof=new JsonObject();proof.addProperty("bootId",expectedBoot);proof.addProperty("locale",locale);proof.addProperty("status","BLOCKED");proof.addProperty("reason","Seven actual owner resource readbacks did not follow this operation's guest publication fence");proof.add("attempts",attempts);RunFiles.write(work.resolve("i18n-current-android-blocked-"+(++counter)+".json"),proof);
        throw new IOException("current seven-owner Android "+locale+" resource proof unavailable within bounded readonly observation; no locale input replay",pending);
    }
    private Path i18nLocale(String locale)throws Exception {
        if(!Set.of("en","zh","ru").contains(locale))throw new IOException("closed system locale required");
        BootData before=boot(null);JsonObject input=new JsonObject();input.addProperty("phase","i18n22-locale");input.addProperty("device",device);input.addProperty("selectedLocale",locale);
        try(ProductRun.Operation operation=productRun.reserve(ProductRun.Kind.CONFIGURE,input)) {
            operation.observePriorBoot(before.id());var baseline=collectLogs(before.id());operation.dispatch();selectLanguage(before.id(),locale);Path ui=verifySettingsTab();var current=waitLocaleRender(baseline,before.id(),locale);requireLanguageCaption(ui,locale);requireSystemLocale(before.id(),locale);
            Path file=languageProof("i18n22-locale-"+(++counter),before.id(),locale,locale,ui,baseline.archive(),current.archive());operation.complete(ui,baseline.archive(),current.archive(),file);return file;
        }
    }
    private JsonObject fullRestartExpected(BootData before,JsonObject current)throws IOException {
        JsonObject android=object(object(current,"deliveries"),"android"),qnx=object(object(current,"deliveries"),"qnx");
        String generation=text(qnx,"generation");
        // Native status may encode the producer generation as an integer; never accept a float or a guessed value.
        if(generation.isBlank()&&qnx.has("generation")&&qnx.get("generation").isJsonPrimitive()&&qnx.getAsJsonPrimitive("generation").isNumber())generation=qnx.get("generation").getAsString();
        if(!before.complete()||!before.enforcing()||!owned.session().id().equals(text(current,"session_id"))||!truth(current,"ok")||!falseValue(current,"stopped")
                ||integer(current,"android_pid")!=androidPid||integer(android,"pid")!=androidPid||!device.equals(text(android,"serial"))
                ||integer(current,"qnx_pid")<1||integer(qnx,"pid")!=integer(current,"qnx_pid")||!"guest_acknowledged".equals(text(object(qnx,"vehicle"),"status"))
                ||!generation.matches("[1-9][0-9]*")||new java.math.BigInteger(generation).bitLength()>64)
            throw new IOException("owned full-machine restart admission lacks current Android/QNX identity");
        JsonObject expected=new JsonObject();expected.addProperty("android",before.id());expected.addProperty("qnx_generation",generation);expected.addProperty("qnx_pid",integer(current,"qnx_pid"));return expected;
    }
    public Path restartBothAndVerify()throws Exception {
        BootData before=boot(null);JsonObject current=nativeRead(client.status(id(),profile,owned.session()),"full-restart-before");JsonObject expected=fullRestartExpected(before,current);
        try(ProductRun.Operation operation=reserve(ProductRun.Kind.REBOOT,"full-machine-cold-restart")) {
            operation.observePriorBoot(before.id());operation.dispatch();JsonObject receipt=owned.restartBoth(expected);Path evidence=work.resolve("full-machine-restart-"+(++counter)+".json");RunFiles.write(evidence,receipt);
            String after=text(object(receipt,"after"),"android_boot_id");if(!uuid(after)||after.equals(before.id()))throw new IOException("full-machine restart lacks fresh Android boot");
            JsonObject live=nativeRead(client.status(id(),profile,owned.session()),"full-restart-after");JsonObject android=object(object(live,"deliveries"),"android");androidPid=integer(live,"android_pid");
            if(androidPid<1||integer(android,"pid")!=androidPid||!device.equals(text(android,"serial")))throw new IOException("full-machine restart changed owned device identity");
            CliProcess.Control control=new CliProcess.Control(System.nanoTime()+Duration.ofMinutes(3).toNanos(),null);
            try{verifyNativeBoot(after,control,receipt);}finally{control.close();}
            BootData actual=boot(null);if(!after.equals(actual.id())||!actual.complete()||!actual.enforcing())throw new IOException("full-machine restart did not reach actual ready Enforcing boot");
            requireHealthy();operation.complete(evidence);return evidence;
        }
    }
    public Path verifyI18nFunctional(JsonObject candidateCatalog)throws Exception {
        if(productRun==null||!productRun.owns(owned))throw new IOException("exclusive current owner required");
        currentAndroidI18nEvidence.clear();
        var result=ru.voboost.tests.agents.I18nProgram.run(new ru.voboost.tests.agents.I18nProgram.Port(){
            public ru.voboost.tests.agents.I18nProgram.Admission admit()throws Exception {
                JsonObject info=nativeRead(client.info(id()),"full-restart-capability");boolean advertised=false;if(info.has("operations")&&info.get("operations").isJsonArray())for(JsonElement op:info.getAsJsonArray("operations"))if(op.isJsonPrimitive()&&op.getAsJsonPrimitive().isString()&&"session.restart".equals(op.getAsString()))advertised=true;
                JsonObject proof=new JsonObject();proof.addProperty("advertised",advertised);boolean admitted=false;String reason="Current Voyemu does not advertise closed owned session.restart";
                if(advertised){BootData before=boot(null);proof.add("expectedBoot",fullRestartExpected(before,nativeRead(client.status(id(),profile,owned.session()),"full-restart-admission")));originalI18nSuite=observeI18nSuite();admitted=true;reason="Current owned Android/QNX identities and advertised full-machine restart/lookup capability observed; actual cold completion remains required";}
                proof.addProperty("reason",reason);Path file=work.resolve("full-restart-admission-"+(++counter)+".json");RunFiles.write(file,proof);return new ru.voboost.tests.agents.I18nProgram.Admission(admitted,reason,List.of(file));
            }
            public Path enabled(boolean value)throws Exception{return setAgent(candidateCatalog,"i18n-ru",value);}
            public Path locale(String value)throws Exception{return i18nLocale(value);}
            public ru.voboost.tests.agents.I18nObservation observe()throws Exception{return readI18n();}
            public ru.voboost.tests.agents.I18nObservation awaitLocale(String locale,ru.voboost.tests.agents.I18nObservation before)throws Exception{var actual=awaitI18n(locale,before,false);requireCurrentAndroidLocale(boot(null).id(),locale);return actual;}
            public ru.voboost.tests.agents.I18nObservation awaitDisabledRestart(ru.voboost.tests.agents.I18nObservation before)throws Exception{return awaitI18n("zh",before,true);}
            public Path verifyRussianAbsent()throws Exception {BootData before=boot(null);JsonObject tree=languagePicker(before.id());var picker=ru.voboost.voyemu.SystemLanguagePicker.read(ru.voboost.voyemu.UiEvidence.surface(tree,"primary"));if(picker.russian()!=null||!"zh".equals(picker.language()))throw new IOException("disabled Russian contribution remains or OEM Chinese selection changed");requireSystemLocale(before.id(),"zh");Path file=work.resolve("i18n22-disabled-picker-"+(++counter)+".json");RunFiles.write(file,tree);return file;}
            public Path restartBoth()throws Exception {return restartBothAndVerify();}
            public Path verifyRestoredRussian()throws Exception {BootData before=boot(null);JsonObject tree=languagePicker(before.id());if(!"ru".equals(ru.voboost.voyemu.SystemLanguagePicker.read(ru.voboost.voyemu.UiEvidence.surface(tree,"primary")).language()))throw new IOException("actual reboot-restored Russian picker unavailable");requireSystemLocale(before.id(),"ru");var baseline=collectLogs(before.id());Path ui=verifySettingsTab();var current=waitLocaleRender(baseline,before.id(),"ru");requireLanguageCaption(ui,"ru");requireCurrentAndroidLocale(before.id(),"ru");return languageProof("i18n22-restored-"+(++counter),before.id(),"ru","ru",ui,baseline.archive(),current.archive());}
            public Path retain(String stage,ru.voboost.tests.agents.I18nObservation observation)throws Exception {Path file=work.resolve("i18n22-"+stage+"-"+(++counter)+".json");RunFiles.write(file,observation.evidence());return file;}
        });
        if(result.accepted())admittedI18n=result.current();JsonObject proof=new JsonObject();proof.addProperty("caseNumber",22);proof.addProperty("semanticFunctionalStatus",result.accepted()?"PASS":"BLOCKED");proof.addProperty("functionalAcceptance",false);proof.addProperty("remainingSubchecks","copy/dialog/retry-error and native draw/visual acceptance are separate from all-owner semantic admission");proof.addProperty("reason",result.reason());JsonArray artifacts=new JsonArray();for(Path file:result.evidence())artifacts.add(file.toString());for(Path file:currentAndroidI18nEvidence)artifacts.add(file.toString());proof.add("artifacts",artifacts);Path file=work.resolve("i18n22-program-"+(++counter)+".json");RunFiles.write(file,proof);return file;
    }
    private Path startupComputerReturn()throws Exception {
        BootData before=boot(null);var prior=observeRuntime(text(status(),"activeGeneration"));String processes=startupProcesses(before.id());
        JsonObject input=new JsonObject();input.addProperty("manifest",manifest);input.addProperty("device",device);input.addProperty("phase","startup-cli");input.addProperty("desiredStartup","interface");input.addProperty("priorPublisher",prior.publisher());input.addProperty("priorRunningStartup",prior.runningStartup());
        try(ProductRun.Operation operation=productRun.reserve(ProductRun.Kind.CONFIGURE,input)) {
            operation.observePriorBoot(before.id());operation.dispatch();CliProcess.Result result=run(List.of("--mode","interface"),Duration.ofMinutes(2),null,null);output(result);
            BootData after=boot(null);if(!before.id().equals(after.id())||!after.complete()||!after.enforcing()||!"interface".equals(startupObservation(after.id()).mode()))throw new IOException("computer-only startup save lacks same-boot persisted interface intent");
            var current=observeRuntime(text(status(),"activeGeneration"));current.requireDeferredFrom(prior);StartupObservation.requireSameProcesses(processes,startupProcesses(before.id()),before.id());
            Path ui=verifySettingsTab();JsonObject proof=new JsonObject();proof.addProperty("bootId",after.id());proof.addProperty("recovery","packaged-installer-next-boot-interface-intent");proof.addProperty("runningStartupUnchanged",prior.runningStartup());proof.addProperty("publisherUnchanged",prior.publisher());proof.addProperty("immediateActivationRequired",false);proof.addProperty("ui",ui.toString());proof.addProperty("stdout",result.out().toString());proof.addProperty("stderr",result.err().toString());Path file=work.resolve("startup-computer-return-"+(++counter)+".json");RunFiles.write(file,proof);operation.complete(result.out(),result.err(),ui,file);return file;
        }
    }
    private Path startupLanguage(String language)throws Exception {
        if(!Set.of("en","zh","ru").contains(language)||language.equals("ru")&&admittedI18n==null)throw new IOException("Terminal current all-owner Russian program admission is required; a picker helper cannot authorize it");
        var nativeBefore=admittedI18n==null?null:readI18n();if(nativeBefore!=null)nativeBefore.requireCurrentWindow(admittedI18n);
        BootData before=boot(null);JsonObject input=new JsonObject();input.addProperty("phase","startup23-language");input.addProperty("device",device);input.addProperty("selectedLocale",language);
        try(ProductRun.Operation operation=productRun.reserve(ProductRun.Kind.CONFIGURE,input)) {
            operation.observePriorBoot(before.id());var baseline=collectLogs(before.id());operation.dispatch();selectLanguage(before.id(),language);Path ui=verifySettingsTab();var current=waitLocaleRender(baseline,before.id(),language);requireLanguageCaption(ui,language);requireSystemLocale(before.id(),language);
            if(nativeBefore!=null){admittedI18n=awaitI18n(language,nativeBefore,false);requireCurrentAndroidLocale(before.id(),language);}
            Path file=languageProof("startup23-locale-"+(++counter),before.id(),language,language,ui,baseline.archive(),current.archive());operation.complete(ui,baseline.archive(),current.archive(),file);return file;
        }
    }
    private ru.voboost.voyemu.StartupDescriptions.Observation startupDescriptionObservation()throws Exception {
        BootData before=boot(null);Path ui=verifySettingsTab();JsonObject tree=RunFiles.read(ui);var controls=ru.voboost.voyemu.StartupControls.read(ru.voboost.voyemu.UiEvidence.surface(tree,"primary"));var state=startupObservation(before.id());var runtime=observeRuntime(text(status(),"activeGeneration"));
        if(!state.mode().equals(controls.selected())||!state.mode().equals(runtime.startup()))throw new IOException("selected startup UI and canonical root readback disagree");requireStartupDescription(tree,state.mode(),controls.language());
        JsonObject proof=new JsonObject();proof.addProperty("bootId",before.id());proof.addProperty("language",controls.language());proof.addProperty("mode",state.mode());proof.addProperty("description",startupDescription(state.mode(),controls.language()));proof.addProperty("ui",ui.toString());proof.addProperty("publisher",runtime.publisher());proof.addProperty("revision",state.revision());proof.addProperty("nativeDrawAcceptance",false);Path file=work.resolve("startup23-observed-"+(++counter)+".json");RunFiles.write(file,proof);return new ru.voboost.voyemu.StartupDescriptions.Observation(controls.language(),state.mode(),file);
    }
    private Path cancelSettingsUninstall(JsonObject candidateCatalog,String language)throws Exception {
        verifySettingsTab();BootData before=boot(null);var prior=observeRuntime(text(status(),"activeGeneration"));JsonObject agents=ru.voboost.tests.agents.AgentSettings.snapshot(candidateCatalog,prior);String processes=startupProcesses(before.id());
        try(ProductRun.Operation operation=launcherOperation("startup23-uninstall-cancel-"+language)) {
            operation.observePriorBoot(before.id());operation.dispatch();boolean tapped=false;long deadline=System.nanoTime()+Duration.ofSeconds(15).toNanos();JsonArray artifacts=new JsonArray();
            for(int scroll=0;scroll<=8&&System.nanoTime()<deadline;scroll++) {
                JsonObject tree=settingsUi(before.id(),deadline);String button=null;
                try{button=ru.voboost.voyemu.SettingsRemoval.button(ru.voboost.voyemu.UiEvidence.surface(tree,"primary"));}catch(ru.voboost.voyemu.UiEvidence.Unavailable absent){ /* Selection only. */ }
                if(button!=null){tapNode(tree,button);tapped=true;break;}if(scroll<8)owned.scrollSettings(settingsScrollTarget(tree));
            }
            if(!tapped)throw new IOException("actual Settings uninstall entry not observed before bounded cancel test");
            ru.voboost.voyemu.SettingsRemoval.Confirmation dialog=null;JsonObject shown=null;
            while(System.nanoTime()<deadline) {
                shown=settingsUi(before.id(),deadline);try{dialog=ru.voboost.voyemu.SettingsRemoval.confirmation(ru.voboost.voyemu.UiEvidence.surface(shown,"primary"));break;}catch(ru.voboost.voyemu.UiEvidence.Unavailable transition){Thread.sleep(50);}
            }
            if(dialog==null||!language.equals(dialog.language()))throw new IOException("actual localized OEM uninstall dialog unavailable");Path shownFile=work.resolve("startup23-cancel-dialog-"+(++counter)+".json");RunFiles.write(shownFile,shown);artifacts.add(shownFile.toString());tapNode(shown,dialog.cancel());boolean gone=false;
            while(System.nanoTime()<deadline) {
                JsonObject tree=settingsUi(before.id(),deadline),surface=ru.voboost.voyemu.UiEvidence.surface(tree,"primary");if(!truth(surface,"complete")||!"ok".equals(text(surface,"status")))continue;boolean visible=false;for(JsonElement item:surface.getAsJsonArray("windows"))if(dialog.window().equals(text(item.getAsJsonObject(),"id")))visible=true;
                if(!visible){Path closed=work.resolve("startup23-cancel-closed-"+(++counter)+".json");RunFiles.write(closed,tree);artifacts.add(closed.toString());gone=true;break;}Thread.sleep(50);
            }
            if(!gone)throw new IOException("one accepted Cancel did not remove its actual OEM dialog; no replay");requireHealthy();var after=observeRuntime(text(status(),"activeGeneration"));after.requireDeferredFrom(prior);
            if(!prior.startup().equals(after.startup())||!prior.revision().equals(after.revision())||!agents.equals(ru.voboost.tests.agents.AgentSettings.snapshot(candidateCatalog,after)))throw new IOException("cancel changed persisted startup/agent configuration");StartupObservation.requireSameProcesses(processes,startupProcesses(before.id()),before.id());Path ui=verifySettingsTab();artifacts.add(ui.toString());JsonObject proof=new JsonObject();proof.addProperty("language",language);proof.addProperty("bootId",before.id());proof.addProperty("uninstallConfirmed",false);proof.addProperty("cancelVerified",true);proof.add("artifacts",artifacts);Path file=work.resolve("startup23-cancel-proof-"+(++counter)+".json");RunFiles.write(file,proof);operation.complete(shownFile,ui,file);return file;
        }
    }
    public Path verifySettingsLayout(JsonObject candidateCatalog,int number)throws Exception {
        if(!Set.of(20,21).contains(number)||productRun==null||!productRun.owns(owned))throw new IOException("closed Settings layout case/current owner required");
        BootData before=boot(null);String original=text(localeObservation(before.id()),"language");var prior=observeRuntime(text(status(),"activeGeneration"));JsonObject agents=ru.voboost.tests.agents.AgentSettings.snapshot(candidateCatalog,prior);JsonArray artifacts=new JsonArray();
        var result=ru.voboost.voyemu.SettingsLocaleProgram.run(number,new ru.voboost.voyemu.SettingsLocaleProgram.Port(){
            private void fence()throws Exception{BootData current=boot(null);if(!before.id().equals(current.id())||!current.complete()||!current.enforcing())throw new IOException("Settings layout crossed original boot/policy");}
            public ru.voboost.voyemu.SettingsLocaleProgram.Admission preflight(){return new ru.voboost.voyemu.SettingsLocaleProgram.Admission(!original.equals("ru")||admittedI18n!=null,"Original Russian may be changed only with current terminal all-owner restoration admission",List.of());}
            public ru.voboost.voyemu.SettingsLocaleProgram.Admission admit(String language)throws Exception{fence();return new ru.voboost.voyemu.SettingsLocaleProgram.Admission(!language.equals("ru")||admittedI18n!=null,"Russian requires terminal current all-owner apply/disable/restart/restoration proof",List.of());}
            public Path language(String value)throws Exception{fence();Path file=startupLanguage(value);artifacts.add(file.toString());fence();return file;}
            public Path measure(String language)throws Exception {
                fence();var baseline=collectLogs(before.id());Path ui=verifySettingsTab();artifacts.add(ui.toString());requireLanguageCaption(ui,language);requireSystemLocale(before.id(),language);
                long deadline=System.nanoTime()+Duration.ofSeconds(60).toNanos();IOException pending=null;
                while(System.nanoTime()<deadline) {
                    fence();JsonObject measured=settingsUi(before.id(),System.nanoTime()+Duration.ofSeconds(10).toNanos(),"all_attached");Path measuredUi=work.resolve("settings-measured-ui-"+(++counter)+".json");RunFiles.write(measuredUi,measured);artifacts.add(measuredUi.toString());
                    var current=collectLogs(before.id());var authority=observeRuntime(text(status(),"activeGeneration"));
                    try {
                        JsonObject metrics=ru.voboost.voyemu.SettingsOemMeasurements.read(baseline,current,authority,measured,profile,owned.session().id(),before.id(),language,number);
                        metrics.addProperty("measuredUi",measuredUi.toString());metrics.addProperty("baselineLogs",baseline.archive().toString());metrics.addProperty("measuredLogs",current.archive().toString());metrics.addProperty("bootId",before.id());metrics.addProperty("language",language);metrics.addProperty("ui",ui.toString());Path file=work.resolve("settings-layout"+number+"-"+language+"-"+(++counter)+".json");RunFiles.write(file,metrics);artifacts.add(file.toString());fence();return file;
                    }catch(ru.voboost.voyemu.UiEvidence.Unavailable incomplete){pending=incomplete;Thread.sleep(100);}
                }
                throw new IOException("current OEM layout capture did not complete within the bounded readonly observation window",pending);
            }
            public Path cancel(String language)throws Exception{fence();Path file=cancelSettingsUninstall(candidateCatalog,language);artifacts.add(file.toString());fence();return file;}
            public Path restore()throws Exception{fence();Path file=startupLanguage(original);artifacts.add(file.toString());requireSystemLocale(before.id(),original);fence();return file;}
            public void retain(ru.voboost.voyemu.SettingsLocaleProgram.Row row)throws Exception{JsonObject proof=new JsonObject();proof.addProperty("caseNumber",number);proof.addProperty("language",row.language());proof.addProperty("status",row.status());proof.addProperty("reason",row.reason());JsonArray files=new JsonArray();for(Path file:row.evidence())files.add(file.toString());proof.add("artifacts",files);Path file=work.resolve("settings-layout"+number+"-row-"+(++counter)+".json");RunFiles.write(file,proof);artifacts.add(file.toString());}
        });
        var current=observeRuntime(text(status(),"activeGeneration"));current.requireDeferredFrom(prior);if(!current.startup().equals(prior.startup())||!agents.equals(ru.voboost.tests.agents.AgentSettings.snapshot(candidateCatalog,current)))throw new IOException("locale measurement/cancel changed desired startup/agent configuration");
        JsonObject proof=new JsonObject();proof.addProperty("caseNumber",number);proof.addProperty("semanticFunctionalStatus",result.semanticComplete()?"PASS":"BLOCKED");proof.addProperty("functionalAcceptance",false);proof.addProperty("visualAcceptance",false);proof.addProperty("reason",result.semanticComplete()?"Actual View metrics and required Cancel readback complete; private canvas ink/padding, font/native draw and full action inventory remain pending":"One or more required locale measurement/cancel profiles are BLOCKED; exact rows retained; private canvas/font/native draw acceptance remains pending");JsonArray rows=new JsonArray();for(var row:result.rows()){JsonObject item=new JsonObject();item.addProperty("language",row.language());item.addProperty("status",row.status());item.addProperty("reason",row.reason());rows.add(item);}proof.add("rows",rows);proof.add("artifacts",artifacts);Path file=work.resolve("settings-layout"+number+"-program-"+(++counter)+".json");RunFiles.write(file,proof);return file;
    }
    public Path verifyStartupDescriptions(JsonObject candidateCatalog)throws Exception {
        if(productRun==null||!productRun.owns(owned))throw new IOException("current exclusive source recipe owner required");BootData before=boot(null);String original=text(localeObservation(before.id()),"language");JsonArray artifacts=new JsonArray();
        var result=ru.voboost.voyemu.StartupDescriptions.run(new ru.voboost.voyemu.StartupDescriptions.Port() {
            private void fence()throws Exception {BootData current=boot(null);if(!before.id().equals(current.id())||!current.complete()||!current.enforcing())throw new IOException("startup23 crossed its original ready boot");}
            public ru.voboost.voyemu.StartupDescriptions.Admission preflight(){return new ru.voboost.voyemu.StartupDescriptions.Admission(!original.equals("ru")||admittedI18n!=null,"Original Russian is not restorable without terminal current all-owner admission",List.of());}
            public ru.voboost.voyemu.StartupDescriptions.Admission admit(String language)throws Exception {fence();
                if(language.equals("ru")&&admittedI18n==null)return new ru.voboost.voyemu.StartupDescriptions.Admission(false,"Russian contribution requires terminal case22 all-owner apply/disable/restart/restoration proof; the current owned full-machine Android/QNX restart admission and terminal all-owner proof are unavailable",List.of());
                return new ru.voboost.voyemu.StartupDescriptions.Admission(true,"Actual OEM EN/ZH picker and process/resource observations are required by the concrete language operation",List.of());
            }
            public void language(String value)throws Exception{fence();artifacts.add(startupLanguage(value).toString());fence();}
            public void select(String mode)throws Exception{fence();artifacts.add(selectStartup(mode).toString());fence();}
            public ru.voboost.voyemu.StartupDescriptions.Observation observe()throws Exception{fence();var result=startupDescriptionObservation();fence();return result;}
            public Path returnFromNoneByComputer()throws Exception{fence();Path result=startupComputerReturn();fence();return result;}
            public Path cancelUninstall(String language)throws Exception{fence();Path result=cancelSettingsUninstall(candidateCatalog,language);fence();return result;}
            public Path rejectStartupMutation(String language)throws Exception{fence();Path result=verifyStartupRejection(candidateCatalog);fence();return result;}
            public void retain(ru.voboost.voyemu.StartupDescriptions.Row row)throws Exception{JsonObject proof=new JsonObject();proof.addProperty("caseNumber",23);proof.addProperty("language",row.language());proof.addProperty("mode",row.mode());proof.addProperty("status",row.status());proof.addProperty("reason",row.reason());JsonArray files=new JsonArray();for(Path file:row.evidence())files.add(file.toString());proof.add("artifacts",files);Path file=work.resolve("startup23-row-"+(++counter)+".json");RunFiles.write(file,proof);artifacts.add(file.toString());}
        });
        BootData finalBoot=boot(null);if(!before.id().equals(finalBoot.id())||!finalBoot.complete()||!finalBoot.enforcing())throw new IOException("startup23 cannot restore locale across a changed boot");if(result.started())artifacts.add(startupLanguage(original).toString());JsonArray rows=new JsonArray();for(var row:result.rows()){JsonObject value=new JsonObject();value.addProperty("language",row.language());value.addProperty("mode",row.mode());value.addProperty("status",row.status());value.addProperty("reason",row.reason());JsonArray files=new JsonArray();for(Path file:row.evidence())files.add(file.toString());value.add("artifacts",files);rows.add(value);}
        JsonObject proof=new JsonObject();proof.addProperty("caseNumber",23);proof.addProperty("sourceComplete",false);proof.addProperty("sourceGap","Terminal all-owner Russian and native draw evidence remain required independently of advertised full-machine restart capability");proof.addProperty("semanticFunctionalStatus",result.semanticComplete()?"PASS":"BLOCKED");proof.addProperty("functionalAcceptance",false);proof.addProperty("nativeDrawAcceptance",false);proof.addProperty("reason",result.semanticComplete()?"All actual locale/startup semantic controls completed; native draw/visual acceptance remains pending":"One or more required locale/startup profiles are BLOCKED; see retained actual rows; full owner restart and native draw proof remain pending");proof.add("rows",rows);proof.add("artifacts",artifacts);Path file=work.resolve("startup23-program-"+(++counter)+".json");RunFiles.write(file,proof);return file;
    }

    private Path selectStartup(String desired) throws Exception {
        if (!Set.of("interface", "hidden", "none").contains(desired) || productRun == null) throw new IOException("owned startup mode required");
        Path opened = verifySettingsTab(); BootData before = boot(null);
        JsonObject initialUi = settingsUi(before.id(), System.nanoTime() + Duration.ofSeconds(10).toNanos());
        var controls = ru.voboost.voyemu.StartupControls.read(ru.voboost.voyemu.UiEvidence.surface(initialUi, "primary"));
        JsonObject currentStatus = status(); var runtime = observeRuntime(text(currentStatus, "activeGeneration"));
        var original = startupObservation(before.id());
        String priorProcesses = startupProcesses(before.id());
        if (!controls.selected().equals(original.mode()) || !original.mode().equals(text(currentStatus, "startupMode"))
                || !runtime.startup().equals(original.mode())) throw new IOException("startup UI and persisted readback differ");
        String priorRunning = runtime.runningStartup();
        initialUi = settingsUi(before.id(), System.nanoTime() + Duration.ofSeconds(10).toNanos());
        controls = ru.voboost.voyemu.StartupControls.read(ru.voboost.voyemu.UiEvidence.surface(initialUi, "primary"));
        if (!controls.selected().equals(original.mode())) throw new IOException("startup group changed before selection");
        Path initial = work.resolve("startup-before-" + (++counter) + ".json"); RunFiles.write(initial, initialUi);
        if (desired.equals(original.mode())) {
            requireStartupDescription(initialUi, desired, controls.language());
            return initial;
        }
        String node = controls.targets().get(desired);
        var target = ru.voboost.voyemu.UiTarget.select(initialUi, profile, owned.session(), "primary", item -> node.equals(text(item, "id")));
        JsonObject input = new JsonObject(); input.addProperty("manifest", manifest); input.addProperty("device", device);
        input.addProperty("phase", "startup-radio"); input.addProperty("originalStartup", original.mode()); input.addProperty("desiredStartup", desired);
        input.addProperty("priorPublisher", runtime.publisher()); input.addProperty("priorRunningStartup", priorRunning);
        try (ProductRun.Operation operation = productRun.reserve(ProductRun.Kind.CONFIGURE, input)) {
            operation.observePriorBoot(before.id()); operation.dispatch(); owned.tap(target);
            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            while (System.nanoTime() < deadline) {
                JsonObject inspected = status(); var current = observeRuntime(text(inspected, "activeGeneration"));
                current.requireDeferredFrom(runtime);
                var saved = startupObservation(before.id());
                JsonObject tree = settingsUi(before.id(), deadline);
                ru.voboost.voyemu.StartupControls selected;
                try { selected = ru.voboost.voyemu.StartupControls.read(ru.voboost.voyemu.UiEvidence.surface(tree, "primary")); }
                catch (ru.voboost.voyemu.UiEvidence.Unavailable transition) { Thread.sleep(100); continue; }
                if (desired.equals(saved.mode()) && !original.revision().equals(saved.revision()) && desired.equals(current.startup())
                        && desired.equals(text(inspected, "startupMode")) && desired.equals(selected.selected())) {
                    if (!ru.voboost.voyemu.UiEvidence.visibleText(ru.voboost.voyemu.UiEvidence.surface(tree, "primary"), "com.qinggan.app.setting", "Voboost " + version))
                        throw new IOException("startup selection removed the current Settings tab");
                    requireStartupDescription(tree, desired, selected.language());
                    String afterProcesses = startupProcesses(before.id());
                    StartupObservation.requireSameProcesses(priorProcesses, afterProcesses, before.id());
                    Path ui = work.resolve("startup-selected-" + (++counter) + ".json"); RunFiles.write(ui, tree);
                    JsonObject proof = new JsonObject(); proof.addProperty("bootId", before.id()); proof.addProperty("original", original.mode());
                    proof.addProperty("selected", desired); proof.addProperty("priorPublisher", runtime.publisher()); proof.addProperty("currentPublisher", current.publisher());
                    proof.addProperty("runningStartupUnchanged", priorRunning); proof.addProperty("configRevision", saved.revision());
                    proof.addProperty("ui", ui.toString()); Path file = work.resolve("startup-deferred-proof-" + (++counter) + ".json"); RunFiles.write(file, proof);
                    operation.complete(initial, opened, ui, file); return file;
                }
                Thread.sleep(250);
            }
            throw new IOException("startup radio/root selection did not confirm within the original boot");
        }
    }

    private void requireStartupDescription(JsonObject tree, String mode, String language) throws IOException {
        if (!ru.voboost.voyemu.UiEvidence.visibleText(ru.voboost.voyemu.UiEvidence.surface(tree, "primary"), "com.qinggan.app.setting", startupDescription(mode, language)))
            throw new IOException("selected startup description differs from the current product contract");
    }

    private String startupDescription(String mode,String language)throws IOException {
        if(settingsCopy==null)throw new IOException("source-built signed Settings copy was not admitted before this operation");
        return settingsCopy.startupDescription(mode,language);
    }

    String startupProcesses(String expectedBoot) throws Exception {
        String body = checkBody(action("inspect"), null); BootData after = boot(null);
        if (!expectedBoot.equals(after.id()) || !after.complete() || !after.enforcing()) throw new IOException("startup processes crossed boot");
        Path file = work.resolve("startup-processes-" + (++counter) + ".txt"); Files.writeString(file, body); return body;
    }

    private Path requireNone(String expectedBoot) throws Exception {
        JsonObject inspected = status();
        if (!"management".equals(text(inspected, "route")) || !version.equals(text(inspected, "installedVersion"))
                || !"same".equals(text(inspected, "contentRelation")) || !"none".equals(text(inspected, "startupMode"))
                || !"stopped".equals(text(inspected, "daemonStatus")) || !"none".equals(startupObservation(expectedBoot).mode()))
            throw new IOException("none mode is not independently persisted with the daemon stopped");
        String body = checkBody(action("inspect"), null);String owner=StartupObservation.requireNoneProcesses(body,expectedBoot);
        var logs=collectLogs(expectedBoot); // Signed daemon CLI + authenticated available resident session, inside the owner identity window.
        String checked=checkBody(action("inspect"),null);if(!owner.equals(StartupObservation.requireNoneProcesses(checked,expectedBoot)))throw new IOException("none signed-session export crossed owner process/file identity");
        BootData after = boot(null);
        if (!expectedBoot.equals(after.id()) || !after.complete() || !after.enforcing()) throw new IOException("process absence crossed boot");
        Path proof = work.resolve("none-processes-" + (++counter) + ".txt"); Files.writeString(proof, body+"\nowner-window-after:\n"+checked+"\nauthenticatedLogArchive="+logs.archive()+"\n"); return proof;
    }

    /** Hidden startup and the actual stock TBOX gesture; target effects remain separate cases. */
    public Path verifyHiddenStartup(JsonObject catalog, Set<String> prerequisiteEnabled) throws Exception {
        JsonObject initialStatus = status(); var initialRuntime = observeRuntime(text(initialStatus, "activeGeneration"));
        JsonObject expectedAgents = ru.voboost.tests.agents.HiddenTargets.snapshot(catalog, initialRuntime,
            prerequisiteEnabled);
        Path expectedFile = work.resolve("hidden-prior-agent-config-" + (++counter) + ".json"); RunFiles.write(expectedFile, expectedAgents);
        Path selected = selectStartup("hidden");
        JsonObject reboot = new JsonObject(); reboot.addProperty("manifest", manifest); reboot.addProperty("device", device);
        reboot.addProperty("phase", "hidden-startup"); reboot.addProperty("expectedStartup", "hidden");
        try (ProductRun.Operation operation = productRun.reserve(ProductRun.Kind.REBOOT, reboot)) { rebootAndVerify(operation); }
        BootData before = boot(null); owned.launch("com.qinggan.app.setting", before.id(), "primary");
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos(); JsonObject hiddenUi = null;
        while (System.nanoTime() < deadline) {
            JsonObject tree = settingsUi(before.id(), deadline);
            try {
                JsonObject surface = ru.voboost.voyemu.UiEvidence.surface(tree, "primary");
                ru.voboost.voyemu.UiEvidence.requireAbsentText(surface, "com.qinggan.app.setting", "Voboost");
                ru.voboost.voyemu.UiEvidence.requireAbsentText(surface, "com.qinggan.app.setting", "Voboost " + version);
                hiddenUi = tree; break;
            } catch (ru.voboost.voyemu.UiEvidence.Unavailable transition) { Thread.sleep(100); }
        }
        if (hiddenUi == null) throw new IOException("complete hidden Settings tab absence was not observed");
        Path hidden = work.resolve("hidden-settings-" + (++counter) + ".json"); RunFiles.write(hidden, hiddenUi);
        deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        JsonArray targets = null; ru.voboost.tests.agents.RuntimeObservation hiddenRuntime = null; String processes = null;
        ru.voboost.tests.agents.ClusterObservation nativeObservation = null;
        boolean nativeRequired = ru.voboost.tests.agents.ClusterObservation.required(catalog, expectedAgents);
        while (System.nanoTime() < deadline) {
            JsonObject inspected = status(); hiddenRuntime = observeRuntime(text(inspected, "activeGeneration"));
            if (!"hidden".equals(hiddenRuntime.runningStartup()) || !"hidden".equals(hiddenRuntime.startup())
                    || !"hidden".equals(startupObservation(before.id()).mode())) throw new IOException("hidden mode is not current in the new boot");
            processes = startupProcesses(before.id());
            try {
                if (nativeRequired) {
                    nativeObservation = nativeObservation == null
                        ? ru.voboost.tests.agents.ClusterObservation.read(hiddenRuntime, catalog, expectedAgents)
                        : nativeObservation.advance(hiddenRuntime, catalog, expectedAgents);
                    if (nativeObservation.advances() < 2) { Thread.sleep(250); continue; }
                }
                targets = ru.voboost.tests.agents.HiddenTargets.read(catalog, hiddenRuntime, StartupObservation.runningProcesses(processes, before.id()), expectedAgents, nativeObservation); break;
            } catch (ru.voboost.tests.agents.HiddenTargets.Pending | ru.voboost.tests.agents.ClusterObservation.Pending transition) { Thread.sleep(250); }
        }
        if (targets == null || hiddenRuntime == null || processes == null) throw new IOException("enabled running targets did not reach active hidden startup");
        Path targetProof = work.resolve("hidden-target-states-" + (++counter) + ".json");
        JsonObject targetEvidence = new JsonObject(); targetEvidence.addProperty("bootId", before.id()); targetEvidence.addProperty("publisher", hiddenRuntime.publisher());
        targetEvidence.add("targets", targets); targetEvidence.addProperty("functionalAcceptance", false); RunFiles.write(targetProof, targetEvidence);
        Path nativeBefore = null;
        if (nativeObservation != null) {
            nativeBefore = work.resolve("hidden-native-before-" + (++counter) + ".json"); RunFiles.write(nativeBefore, nativeObservation.evidence());
        }
        systemMenu(before.id());
        JsonObject input = new JsonObject(); input.addProperty("manifest", manifest); input.addProperty("device", device);
        input.addProperty("phase", "tbox-recovery"); input.addProperty("desiredStartup", "interface"); input.addProperty("priorPublisher", hiddenRuntime.publisher());
        Path restored, nativeAfter = null;
        int priorNativeAdvances = nativeObservation == null ? 0 : nativeObservation.advances();
        try (ProductRun.Operation operation = productRun.reserve(ProductRun.Kind.CONFIGURE, input)) {
            String priorProcesses = startupProcesses(before.id());
            ru.voboost.voyemu.UiTarget target = tboxTarget(before.id());
            operation.observePriorBoot(before.id()); operation.dispatch(); owned.recoverSettings(target);
            long recoveryDeadline = System.nanoTime() + Duration.ofSeconds(30).toNanos(); restored = null;
            while (System.nanoTime() < recoveryDeadline) {
                JsonObject inspected = status(); var current = observeRuntime(text(inspected, "activeGeneration"));
                current.requireRetainedTargetsFrom(hiddenRuntime);
                ru.voboost.tests.agents.HiddenTargets.requireSameConfiguration(expectedAgents, catalog, current);
                if ("interface".equals(current.startup()) && "interface".equals(current.runningStartup())
                        && "interface".equals(startupObservation(before.id()).mode())) {
                    if (nativeRequired) {
                        try { nativeObservation = nativeObservation.advance(current, catalog, expectedAgents); }
                        catch (ru.voboost.tests.agents.ClusterObservation.Pending transition) { Thread.sleep(250); continue; }
                        if (nativeObservation.advances() < priorNativeAdvances + 2) { Thread.sleep(250); continue; }
                    }
                    restored = verifySettingsTab(); StartupObservation.requireSameProcesses(priorProcesses, startupProcesses(before.id()), before.id());
                    if (nativeObservation != null) {
                        nativeAfter = work.resolve("hidden-native-after-" + (++counter) + ".json"); RunFiles.write(nativeAfter, nativeObservation.evidence());
                        operation.complete(expectedFile, hidden, targetProof, nativeBefore, restored, nativeAfter);
                    } else operation.complete(expectedFile, hidden, targetProof, restored);
                    break;
                }
                Thread.sleep(250);
            }
            if (restored == null) throw new IOException("actual TBOX gesture did not prove live interface recovery");
        }
        JsonObject proof = new JsonObject(); proof.addProperty("priorAgentConfiguration", expectedFile.toString()); proof.addProperty("selection", selected.toString()); proof.addProperty("hiddenUi", hidden.toString());
        proof.addProperty("targetStates", targetProof.toString()); proof.addProperty("restoredUi", restored.toString());
        if (nativeBefore != null && nativeAfter != null) {
            proof.addProperty("nativeAvailabilityBefore", nativeBefore.toString()); proof.addProperty("nativeAvailabilityAfter", nativeAfter.toString());
        }
        Path file = work.resolve("hidden-startup-cycle-" + (++counter) + ".json"); RunFiles.write(file, proof); return file;
    }

    private static final String ENGINEERING_AGENT = "settings-engineering-menu";
    private static final String SETTINGS_PACKAGE = "com.qinggan.app.setting";
    private static final String PASSWORD_VIEW = "ll_advance_setting_validation";
    private static final String PASSWORD_EDITOR = "pwdInputView";
    private static final String HARDWARE_MARKER = "open_theme_switcher";
    private static final String CLIENT_MARKER = "btn_save_log_to_udisk";

    /** Exercise both stock version rows through the installed Settings process. */
    public Path verifySettingsEngineeringMenu(JsonObject candidateCatalog) throws Exception {
        requireAgentCandidate();
        BootData initial = boot(null);
        if (!initial.complete() || !initial.enforcing() || !productRun.owns(owned))
            throw new IOException("Settings engineering menu requires the current owned Enforcing boot");
        var original = observeRuntime(text(status(), "activeGeneration"));
        var settings = ru.voboost.tests.agents.AgentSettings.snapshot(candidateCatalog, original);
        boolean originalEnabled = original.desired(ENGINEERING_AGENT);
        JsonObject proof = new JsonObject();
        proof.addProperty("bootId", initial.id());
        proof.addProperty("publisher", original.publisher());
        proof.addProperty("originalEnabled", originalEnabled);
        try {
            for (boolean enabled : List.of(false, true)) {
                verifySettingsTab();
                proof.addProperty(enabled ? "enabled" : "disabled",
                        setAgent(candidateCatalog, ENGINEERING_AGENT, enabled).toString());
                awaitObservation("Engineering menu runtime selection", Duration.ofSeconds(30), (control, deadline) -> {
                    var current = observeRuntime(text(original.runtime(), "generationId"), control, original.publisher());
                    if (current.desired(ENGINEERING_AGENT) != enabled)
                        throw new IOException("engineering menu desired selection changed");
                    boolean active = current.targets(ENGINEERING_AGENT).stream().anyMatch(row ->
                            SETTINGS_PACKAGE.equals(text(row, "process")) && "active".equals(text(row, "state"))
                            && "ready".equals(text(row, "reason")));
                    return active == enabled ? current : null;
                });
                for (boolean hardware : List.of(true, false)) {
                    String row = hardware ? "kv_hw_version" : "kv_sw_version";
                    String activity = enabled ? (hardware ? "EngActivity" : "ClientEngActivity") : "MainActivity";
                    String marker = enabled ? (hardware ? HARDWARE_MARKER : CLIENT_MARKER) : PASSWORD_EDITOR;
                    owned.launch(SETTINGS_PACKAGE, initial.id(), "primary");
                    awaitEngineeringFrame(initial.id(), "layout_menu_setting_left", false);
                    systemMenu(initial.id());
                    var target = engineeringVersionRow(initial.id(), row);
                    JsonObject dispatch = owned.openSettingsEngineeringMenu(
                            ru.voboost.voyemu.UiTarget.select(target.tree(), profile, owned.session(), "primary",
                                    node -> text(node, "id").equals(text(target.node(), "id"))));
                    String step = (enabled ? "on-" : "off-") + (hardware ? "hardware" : "client");
                    Path input = work.resolve("engineering-" + step + "-input.json");
                    RunFiles.write(input, dispatch);
                    var outcome = awaitEngineeringFrame(initial.id(), marker, !enabled);
                    JsonObject resumed = requireEngineeringActivity(initial.id(), activity);
                    var after = awaitEngineeringFrame(initial.id(), marker, !enabled);
                    if (!text(outcome.node(), "window_id").equals(text(after.node(), "window_id"))
                            || !text(outcome.tree(), "source_instance").equals(text(after.tree(), "source_instance")))
                        throw new IOException("engineering menu UI changed while checking the resumed activity");
                    Path observed = work.resolve("engineering-" + step + "-ui.json");
                    RunFiles.write(observed, after.tree());
                    JsonObject result = new JsonObject();
                    result.addProperty("row", row);
                    result.add("resumedActivity", resumed);
                    result.addProperty("input", input.toString());
                    result.addProperty("ui", observed.toString());
                    proof.add(step, result);
                    if (!enabled) dismissEngineeringPassword(initial.id());
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interrupted;
        } catch (Exception failure) {
            try {
                restoreEngineeringSelection(candidateCatalog, initial, original, settings, originalEnabled);
            } catch (Exception cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
        proof.addProperty("restored", restoreEngineeringSelection(
                candidateCatalog, initial, original, settings, originalEnabled).toString());
        Path file = work.resolve("settings-engineering-menu.json");
        RunFiles.write(file, proof);
        return file;
    }

    private Path restoreEngineeringSelection(JsonObject catalog, BootData initial,
            ru.voboost.tests.agents.RuntimeObservation original, JsonObject settings, boolean enabled) throws Exception {
        BootData current = boot(null);
        if (!initial.id().equals(current.id()) || !current.complete() || !current.enforcing()
                || productRun.pending() != null)
            throw new IOException("engineering selection cannot be restored across an unresolved mutation or boot change");
        owned.launch(SETTINGS_PACKAGE, initial.id(), "primary");
        // MainActivity is singleTask. A normal launch clears the engineering Activity above it.
        var frame = awaitObservation("Main Settings after engineering check", Duration.ofSeconds(15), (control, deadline) -> {
            JsonObject tree = settingsUi(initial.id(), deadline);
            JsonObject surface = ru.voboost.voyemu.UiEvidence.surface(tree, "primary");
            JsonObject password = engineeringNode(surface, PASSWORD_EDITOR);
            if (password != null) return true;
            return engineeringNode(surface, "layout_menu_setting_left") != null ? false : null;
        });
        requireEngineeringActivity(initial.id(), "MainActivity");
        if (frame) dismissEngineeringPassword(initial.id());
        verifySettingsTab();
        Path restored = setAgent(catalog, ENGINEERING_AGENT, enabled);
        var after = observeRuntime(text(original.runtime(), "generationId"), null, original.publisher());
        if (!settings.equals(ru.voboost.tests.agents.AgentSettings.snapshot(catalog, after)))
            throw new IOException("engineering menu scenario changed the original checkbox configuration");
        return restored;
    }

    private record EngineeringFrame(JsonObject tree, JsonObject node) {}

    private EngineeringFrame awaitEngineeringFrame(String boot, String marker, boolean password) throws Exception {
        return awaitObservation("Settings engineering menu view " + marker, Duration.ofSeconds(20), (control, deadline) -> {
            JsonObject tree = settingsUi(boot, deadline);
            JsonObject surface = ru.voboost.voyemu.UiEvidence.surface(tree, "primary");
            JsonObject editor = engineeringNode(surface, PASSWORD_EDITOR);
            JsonObject overlay = engineeringNode(surface, PASSWORD_VIEW);
            if (password) {
                if (editor == null || overlay == null) return null;
                if (!"android.widget.EditText".equals(text(editor, "class")) || !engineeringDescendant(surface, editor, overlay))
                    throw new IOException("stock password editor is outside its OEM validation view");
            } else if (editor != null || overlay != null) {
                if (!marker.equals("layout_menu_setting_left"))
                    throw new IOException("stock password appeared when the engineering agent was enabled");
                return null;
            }
            JsonObject node = engineeringNode(surface, marker);
            if (node == null) return null;
            if ((password || marker.equals("layout_menu_setting_left"))
                    && (engineeringNode(surface, HARDWARE_MARKER) != null || engineeringNode(surface, CLIENT_MARKER) != null))
                throw new IOException("unexpected engineering destination");
            return new EngineeringFrame(tree, node);
        });
    }

    private EngineeringFrame engineeringVersionRow(String boot, String row) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(25).toNanos();
        for (int scrolls = 0; System.nanoTime() < deadline;) {
            JsonObject tree = settingsUi(boot, deadline);
            JsonObject surface = ru.voboost.voyemu.UiEvidence.surface(tree, "primary");
            if (engineeringNode(surface, PASSWORD_VIEW) != null)
                throw new IOException("stock password view covers the version rows");
            JsonObject node = engineeringNode(surface, row);
            if (node != null && truth(node, "enabled") && truth(node, "clickable")
                    && object(node, "bounds").equals(object(node, "clipped_bounds")))
                return new EngineeringFrame(tree, node);
            JsonObject scroll = engineeringNode(surface, "system_scroll");
            if (scroll != null && scrolls < 4) {
                owned.scrollSettingsToStart(ru.voboost.voyemu.UiTarget.selectScrollable(tree, profile, owned.session(),
                        "primary", candidate -> text(candidate, "id").equals(text(scroll, "id"))));
                ++scrolls;
            }
            Thread.sleep(100);
        }
        throw new IOException("fully visible OEM engineering version row unavailable: " + row);
    }

    private void dismissEngineeringPassword(String boot) throws Exception {
        var frame = awaitEngineeringFrame(boot, PASSWORD_EDITOR, true);
        JsonObject surface = ru.voboost.voyemu.UiEvidence.surface(frame.tree(), "primary");
        JsonObject overlay = engineeringNode(surface, PASSWORD_VIEW);
        JsonObject close = null;
        for (JsonElement entry : surface.getAsJsonArray("nodes")) {
            JsonObject node = entry.getAsJsonObject();
            if (SETTINGS_PACKAGE.equals(text(node, "package")) && (SETTINGS_PACKAGE + ":id/btn_back").equals(text(node, "resource_id"))
                    && "android.widget.ImageView".equals(text(node, "class")) && truth(node, "visible")
                    && engineeringDescendant(surface, node, overlay)) {
                if (close != null) throw new IOException("ambiguous stock password dismiss control");
                close = node;
            }
        }
        if (close == null) throw new IOException("stock password dismiss control unavailable");
        tapNode(frame.tree(), text(close, "id"));
        awaitEngineeringFrame(boot, "layout_menu_setting_left", false);
    }

    private static JsonObject engineeringNode(JsonObject surface, String resource) throws IOException {
        if (!truth(surface, "complete") || !"ok".equals(text(surface, "status"))) return null;
        JsonObject result = null;
        for (JsonElement windowEntry : surface.getAsJsonArray("windows")) {
            JsonObject window = windowEntry.getAsJsonObject();
            if (!SETTINGS_PACKAGE.equals(text(window, "package")) || integer(window, "type") != 1
                    || !truth(window, "read_complete") || !truth(window, "input_focused")) continue;
            for (JsonElement nodeEntry : surface.getAsJsonArray("nodes")) {
                JsonObject node = nodeEntry.getAsJsonObject();
                if (!SETTINGS_PACKAGE.equals(text(node, "package")) || !text(window, "id").equals(text(node, "window_id"))
                        || !(SETTINGS_PACKAGE + ":id/" + resource).equals(text(node, "resource_id"))
                        || !truth(node, "present") || !truth(node, "visible")
                        || integer(object(node, "clipped_bounds"), "width") <= 0
                        || integer(object(node, "clipped_bounds"), "height") <= 0) continue;
                if (result != null) throw new IOException("ambiguous OEM engineering node: " + resource);
                result = node;
            }
        }
        return result;
    }

    private static boolean engineeringDescendant(JsonObject surface, JsonObject node, JsonObject ancestor) {
        String window = text(ancestor, "window_id");
        Set<String> visited = new java.util.HashSet<>();
        String parent = text(node, "parent");
        while (!parent.isBlank() && visited.add(parent)) {
            if (text(ancestor, "id").equals(parent)) return true;
            String next = "";
            for (JsonElement entry : surface.getAsJsonArray("nodes")) {
                JsonObject candidate = entry.getAsJsonObject();
                if (parent.equals(text(candidate, "id")) && window.equals(text(candidate, "window_id"))) {
                    next = text(candidate, "parent");
                    break;
                }
            }
            parent = next;
        }
        return false;
    }

    private JsonObject requireEngineeringActivity(String boot, String activity) throws Exception {
        String inspection = checkBody(action("inspect"), null);
        if (!inspection.lines().anyMatch(line -> line.equals("bootId=" + boot)))
            throw new IOException("engineering activity observation crossed boot");
        String component = SETTINGS_PACKAGE + "/.activity." + activity;
        String fullComponent = SETTINGS_PACKAGE + "/" + SETTINGS_PACKAGE + ".activity." + activity;
        Pattern resumed = Pattern.compile("^\\s*mResumedActivity:\\s+ActivityRecord\\{[0-9a-f]+ u0 ([^ ]+) t[0-9]+\\}\\s*$");
        List<String> activities = new ArrayList<>();
        boolean activitySection = false;
        for (String line : inspection.lines().toList()) {
            if (line.equals("activity-focus:")) activitySection = true;
            if (line.equals("window-focus:")) break;
            if (!activitySection) continue;
            Matcher match = resumed.matcher(line);
            if (match.matches() && match.group(1).startsWith(SETTINGS_PACKAGE + "/")) activities.add(match.group(1));
        }
        if (!activities.equals(List.of(component)) && !activities.equals(List.of(fullComponent)))
            throw new IOException("expected resumed Settings activity " + component + ", observed " + activities);
        JsonObject proof = new JsonObject();
        proof.addProperty("bootId", boot);
        proof.addProperty("component", activities.get(0));
        return proof;
    }

    private void systemMenu(String boot) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos(); int scrolls=0;
        while (System.nanoTime() < deadline) {
            JsonObject tree = settingsUi(boot, deadline), surface = ru.voboost.voyemu.UiEvidence.surface(tree, "primary"); String action = null;
            for (String label : List.of("System", "系统", "Система")) {
                if (!ru.voboost.voyemu.UiEvidence.visibleText(surface, "com.qinggan.app.setting", label)) continue;
                if (action != null) throw new IOException("ambiguous System menu");
                try { action = ru.voboost.voyemu.UiEvidence.actionForText(surface, "com.qinggan.app.setting", label); }
                catch (ru.voboost.voyemu.UiEvidence.Unavailable transition) { }
            }
            if (action != null) { tapNode(tree, action); return; }
            if(scrolls<8){ru.voboost.voyemu.UiTarget target=null;try{target=systemMenuScroll(tree,profile,owned.session());}catch(ru.voboost.voyemu.UiEvidence.Unavailable notYetObserved){ }
                if(target!=null){owned.scrollSettings(target);scrolls++;}}
            Thread.sleep(100);
        }
        throw new IOException("actual OEM System menu was not observed after bounded source-owned menu scrolls");
    }

    static ru.voboost.voyemu.UiTarget systemMenuScroll(JsonObject tree,String profile,ru.voboost.voyemu.VoyemuClient.Session session)throws IOException {
        return ru.voboost.voyemu.UiTarget.selectScrollable(tree,profile,session,"primary",node->"com.qinggan.app.setting".equals(text(node,"package"))&&"com.qinggan.app.setting:id/layout_menu_setting_left".equals(text(node,"resource_id")));
    }

    private ru.voboost.voyemu.UiTarget tboxTarget(String boot) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            JsonObject tree = settingsUi(boot, deadline), surface = ru.voboost.voyemu.UiEvidence.surface(tree, "primary"); String id = null;
            for (JsonElement value : surface.getAsJsonArray("nodes")) {
                JsonObject node = value.getAsJsonObject();
                if (!"com.qinggan.app.setting".equals(text(node, "package")) || !"com.qinggan.app.setting:id/kv_tbox_mpu_res_version".equals(text(node, "resource_id"))
                        || !"android.widget.LinearLayout".equals(text(node, "class")) || !truth(node, "present") || !truth(node, "visible") || !truth(node, "enabled") || !truth(node, "clickable")) continue;
                if (id != null) throw new IOException("ambiguous actual TBOX hardware-version row"); id = text(node, "id");
            }
            if (id != null) { String selected = id; return ru.voboost.voyemu.UiTarget.select(tree, profile, owned.session(), "primary", node -> selected.equals(text(node, "id"))); }
            Thread.sleep(100);
        }
        throw new IOException("stock TBOX hardware-version row was not observed");
    }

    /** Real Settings none selection, next boot absence, and computer-installer-only recovery. */
    public Path verifyNoneStartup() throws Exception {
        Path selected = selectStartup("none");
        JsonObject reboot = new JsonObject(); reboot.addProperty("manifest", manifest); reboot.addProperty("device", device);
        reboot.addProperty("phase", "none-startup"); reboot.addProperty("expectedStartup", "none");
        try (ProductRun.Operation operation = productRun.reserve(ProductRun.Kind.REBOOT, reboot)) { rebootAndVerify(operation); }
        BootData before = boot(null); Path absent = requireNone(before.id());
        JsonObject input = new JsonObject(); input.addProperty("manifest", manifest); input.addProperty("device", device);
        input.addProperty("phase", "startup-none-cli"); input.addProperty("desiredStartup", "interface"); input.addProperty("priorOwner",StartupObservation.requireNoneProcesses(checkBody(action("inspect"),null),before.id()));
        Path recovered;
        try (ProductRun.Operation operation = productRun.reserve(ProductRun.Kind.CONFIGURE, input)) {
            operation.observePriorBoot(before.id()); operation.dispatch();
            CliProcess.Result result = run(List.of("--mode", "interface"), Duration.ofMinutes(2), null, null); output(result);
            if (!"interface".equals(startupObservation(before.id()).mode())) throw new IOException("packaged computer mode change was not independently confirmed");
            String unchanged=checkBody(action("inspect"),null);if(!text(input,"priorOwner").equals(StartupObservation.requireNoneProcesses(unchanged,before.id())))throw new IOException("startup save crossed exact owner process/backing identity");Path saved=work.resolve("none-interface-saved-"+(++counter)+".txt");Files.writeString(saved,unchanged);operation.complete(result.out(),result.err(),saved);
        }
        rebootAndVerify(); recovered=verifySettingsTab(); if(!"interface".equals(startupObservation(boot(null).id()).mode()))throw new IOException("next boot did not retain saved interface intent");
        JsonObject proof = new JsonObject(); proof.addProperty("selected", selected.toString()); proof.addProperty("absence", absent.toString());
        proof.addProperty("restoredUi", recovered.toString()); proof.addProperty("recovery", "packaged-installer-mode-interface");
        Path file = work.resolve("none-startup-cycle-" + (++counter) + ".json"); RunFiles.write(file, proof); return file;
    }

    public record LanguageProof(Path opposite, Path restored) {}

    public LanguageProof verifySystemLanguageRoundtrip() throws Exception {
        BootData before = boot(null);
        JsonObject initial = languagePicker(before.id());
        var picker = ru.voboost.voyemu.SystemLanguagePicker.read(ru.voboost.voyemu.UiEvidence.surface(initial, "primary"));
        String original = picker.language(), opposite = original.equals("en") ? "zh" : "en";
        if (!original.equals(text(localeObservation(before.id()), "language")))
            throw new IOException("OEM checked language differs from ActivityManager configuration");
        if (productRun == null) throw new IOException("language mutation requires a durable run owner");
        JsonObject input = new JsonObject(); input.addProperty("manifest", manifest); input.addProperty("device", device);
        input.addProperty("phase", "language-cycle"); input.addProperty("originalLocale", original);
        try (ProductRun.Operation operation = productRun.reserve(ProductRun.Kind.CONFIGURE, input)) {
            operation.observePriorBoot(before.id());operation.dispatch();
            var result=ru.voboost.voyemu.LocaleRoundtrip.run(original,selected->{
                var baseline=collectLogs(before.id());selectLanguage(before.id(),selected);Path ui=verifySettingsTab();
                var current=waitLocaleRender(baseline,before.id(),selected);requireLanguageCaption(ui,selected);requireSystemLocale(before.id(),selected);
                return languageProof(selected.equals(original)?"language-restored":"language-opposite",before.id(),original,selected,ui,baseline.archive(),current.archive());
            });
            operation.complete(result.opposite(),result.restored());return new LanguageProof(result.opposite(),result.restored());
        }
    }

    private void requireSystemLocale(String boot, String expected) throws Exception {
        if (!expected.equals(text(localeObservation(boot), "language"))) throw new IOException("ActivityManager primary locale differs");
    }
    private JsonObject localeObservation(String expectedBoot) throws Exception {
        BootData before = boot(null);
        if (!expectedBoot.equals(before.id()) || !before.complete() || !before.enforcing()) throw new IOException("locale read crossed boot");
        String body = checkBody(action("locale-observation"), null);
        SystemLocale facts = SystemLocale.read(body, expectedBoot);
        BootData after = boot(null);
        if (!expectedBoot.equals(after.id()) || !after.complete() || !after.enforcing()) throw new IOException("locale read crossed boot");
        JsonObject proof = new JsonObject(); proof.addProperty("bootId", facts.boot()); proof.addProperty("language", facts.language());
        proof.addProperty("configuration", facts.configuration()); proof.addProperty("body", body);
        RunFiles.write(work.resolve("system-locale-" + (++counter) + ".json"), proof);
        return proof;
    }

    private Path languageProof(String name, String boot, String original, String current, Path ui, Path before, Path after) throws IOException {
        JsonObject proof = new JsonObject(); proof.addProperty("boot", boot); proof.addProperty("original", original);
        proof.addProperty("selected", current); proof.addProperty("ui", ui.toString());
        proof.addProperty("baselineLogs", before.toString()); proof.addProperty("currentLogs", after.toString());
        Path file = work.resolve(name + ".json"); RunFiles.write(file, proof); return file;
    }
    private void requireLanguageCaption(Path ui, String locale) throws IOException {
        if (!ru.voboost.voyemu.UiEvidence.visibleText(ru.voboost.voyemu.UiEvidence.surface(RunFiles.read(ui), "primary"),
                "com.qinggan.app.setting", switch(locale){case "en"->"Startup";case "zh"->"启动方式";case "ru"->"Запуск";default->throw new IOException("unsupported caption locale");}))
            throw new IOException("system-driven Voboost caption differs from selected language");
    }
    private ru.voboost.tests.logging.InstallerLogs.Snapshot waitLocaleRender(
            ru.voboost.tests.logging.InstallerLogs.Snapshot baseline, String boot, String locale) throws Exception {
        IOException pending = null;
        for (int attempt = 0; attempt < 4; ++attempt) {
            var current = collectLogs(boot);
            try {
                ru.voboost.tests.logging.InstallerLogs.renderLocale(baseline, current, locale); return current;
            } catch (ru.voboost.tests.logging.InstallerLogs.LocalePending transition) { pending = transition; }
            if (attempt < 3) Thread.sleep(1000);
        }
        throw new IOException("fresh Setting locale was not confirmed in bounded log observations", pending);
    }

    public Path measureVehicleStartup() throws Exception {
        String boot = boot(null).id();
        var logs = collectLogs(boot);
        JsonObject proof = ru.voboost.tests.logging.NativeStartupTiming.measure(logs);
        proof.addProperty("archive", logs.archive().toString());
        Path file = work.resolve("vehicle-startup-timing-" + (++counter) + ".json"); RunFiles.write(file, proof); return file;
    }

    /** Read-only diagnostic: retain the device log as it stands right now. Never classifies anything and
     * never replaces a verdict; a failure to capture is recorded as a marker file, not raised. */
    public Path captureDiagnosticLogs(String label)throws InterruptedException {
        String name = "diagnostic-logs-" + label + "-" + (++counter);
        try {
            Path destination = RunFiles.directory(work.resolve(name));
            CliProcess.Result result = run(List.of("--logs", destination.toString()), Duration.ofMinutes(3), null, null);
            Path archive = Path.of(output(result).strip());
            if (!archive.isAbsolute() || !archive.getParent().equals(destination) || !archive.toRealPath().startsWith(destination))
                throw new IOException("log receipt escaped its owned destination");
            return archive;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();throw interrupted;
        } catch (Exception unavailable) {
            Path marker = work.resolve(name + ".unavailable.txt");
            try { java.nio.file.Files.writeString(marker, unavailable + "\n"); } catch (Exception unwritable) { unavailable.addSuppressed(unwritable); }
            return marker;
        }
    }

    private ru.voboost.tests.logging.InstallerLogs.Snapshot collectLogs(String expectedBoot) throws Exception {
        BootData before = boot(null);
        if (!expectedBoot.equals(before.id()) || !before.complete() || !before.enforcing()) throw new IOException("log capture crossed boot");
        Path destination = RunFiles.directory(work.resolve("logs-" + (++counter)));
        java.time.Instant start = java.time.Instant.now();
        CliProcess.Result result = run(List.of("--logs", destination.toString()), Duration.ofMinutes(3), null, null);
        java.time.Instant end = java.time.Instant.now();
        String stdout = output(result);
        if (!stdout.endsWith("\n") || stdout.indexOf('\n') != stdout.length() - 1) throw new IOException("exactly one log archive receipt required");
        Path archive = Path.of(stdout.strip());
        if (!archive.isAbsolute() || !archive.getParent().equals(destination) || !archive.toRealPath().startsWith(destination))
            throw new IOException("log receipt escaped its owned destination");
        BootData after = boot(null);
        if (!expectedBoot.equals(after.id()) || !after.complete() || !after.enforcing()) throw new IOException("log capture crossed boot");
        return ru.voboost.tests.logging.InstallerLogs.read(archive, destination, device, version, manifest, expectedBoot, start, end);
    }
    private JsonObject languagePicker(String boot) throws Exception {
        owned.launch("com.qinggan.app.setting", boot, "primary");
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        boolean selectedDisplay = false;
        while (System.nanoTime() < deadline) {
            JsonObject tree = settingsUi(boot, deadline);
            JsonObject surface = ru.voboost.voyemu.UiEvidence.surface(tree, "primary");
            boolean group = false;
            for (JsonElement item : surface.getAsJsonArray("nodes"))
                if (ru.voboost.voyemu.SystemLanguagePicker.GROUP.equals(text(item.getAsJsonObject(), "resource_id")) && truth(item.getAsJsonObject(), "visible")) group = true;
            if (group) {
                try { ru.voboost.voyemu.SystemLanguagePicker.read(surface); return tree; }
                catch (ru.voboost.voyemu.SystemLanguagePicker.Unavailable transition) {
                    // Wait for the same observed group to finish locale reconfiguration.
                }
            } else if (!selectedDisplay) {
                String node = null;
                for (String label : List.of("Display", "显示", "Экран", "Дисплей")) {
                    if (ru.voboost.voyemu.UiEvidence.visibleText(surface, "com.qinggan.app.setting", label)) {
                        if (node != null) throw new IOException("ambiguous Display menu");
                        try { node = ru.voboost.voyemu.UiEvidence.actionForText(surface, "com.qinggan.app.setting", label); }
                        catch (ru.voboost.voyemu.UiEvidence.Unavailable transition) {
                            // No input until the observed row is enabled.
                        }
                    }
                }
                if (node != null) { tapNode(tree, node); selectedDisplay = true; }
            }
            Thread.sleep(100);
        }
        throw new IOException("OEM language picker did not appear");
    }
    private void selectLanguage(String boot, String language) throws Exception {
        JsonObject tree = languagePicker(boot);
        var picker = ru.voboost.voyemu.SystemLanguagePicker.read(ru.voboost.voyemu.UiEvidence.surface(tree, "primary"));
        if (!picker.language().equals(language)) tapNode(tree, picker.target(language));
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            tree = settingsUi(boot, deadline);
            try {
                picker = ru.voboost.voyemu.SystemLanguagePicker.read(ru.voboost.voyemu.UiEvidence.surface(tree, "primary"));
                if (picker.language().equals(language)) return;
            } catch (ru.voboost.voyemu.SystemLanguagePicker.Unavailable transition) {
                // A locale reconfiguration may temporarily remove the checked child.
            }
            Thread.sleep(100);
        }
        throw new IOException("OEM selected language did not change");
    }
    private void tapNode(JsonObject tree, String nodeId) throws Exception {
        owned.tap(ru.voboost.voyemu.UiTarget.select(tree, profile, owned.session(), "primary", node -> nodeId.equals(text(node, "id"))));
    }

    /** Settings input reads the default (Accessibility) source: API 28 hides platform-opaque wallpaper windows there. */
    private JsonObject settingsUi(String expectedBoot, long deadline) throws Exception {
        return settingsUi(expectedBoot, deadline, null);
    }
    private JsonObject settingsUi(String expectedBoot, long deadline, String scope) throws Exception {
        JsonObject tree;
        for(;;) {
            long remaining=Math.max(1,java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(deadline-System.nanoTime()));
            try {
                tree=nativeRead(client.multimediaUi(id(),profile,owned.session(),List.of("primary"),scope,null,null,
                    (int)Math.min(5000,remaining)),"settings-ui",Duration.ofMillis(remaining));
                break;
            }catch(IOException unavailable) {
                if(deadline-System.nanoTime()<MINIMUM_OBSERVATION_NANOS||lastNativeEvidence==null
                        ||!pendingUiObservation(RunFiles.read(lastNativeEvidence), unavailable))throw unavailable;
                Thread.sleep(50); // Read-only resampling; accepted input is never sent again.
            }
        }
        if (!expectedBoot.equals(text(tree, "boot_id")) || !profile.equals(text(tree, "profile"))
                || !owned.session().id().equals(text(tree, "session_id")) || integer(tree,"android_pid")!=androidPid || !"voyemu.ui.v1".equals(text(tree, "format")))
            throw new IOException("Setting UI observation scope differs");
        String observation = text(tree, "observation_id");
        if (observation.isBlank() || text(tree, "source_instance").isBlank() || text(tree, "revision").isBlank()
                || observedUiIds.size() >= 10000 || !observedUiIds.add(observation))
            throw new IOException("Setting UI observation is stale or lacks source identity");
        return tree;
    }

    static boolean pendingUiObservation(JsonObject retained) {
        return pendingUiObservation(retained, null);
    }

    static boolean pendingUiObservation(JsonObject retained, IOException failure) {
        try {
            if (!"RESPONSE".equals(text(retained, "state"))) return false;
            JsonObject response = object(retained, "response");
            if (falseValue(response, "ok")) return pendingUiError(object(response, "error"));
            if (!(failure instanceof ru.voboost.voyemu.SessionUi.Unavailable)) return false;
            JsonObject result = object(response, "result");
            JsonObject primary = result.getAsJsonArray("multimedia").get(0).getAsJsonObject();
            return pendingUiError(object(primary, "error"));
        } catch (RuntimeException malformed) {
            return false;
        }
    }

    private static boolean pendingUiError(JsonObject error) {
        String code = text(error, "code");
        String outcome = text(error, "outcome");
        if (Set.of("TREE_TIMEOUT", "TREE_TRANSPORT", "TEST_BUSY").contains(code))
            return "unknown".equals(outcome) && truth(error, "retryable");
        return "TREE_REJECTED".equals(code) && "not_started".equals(outcome)
                && "TREE_REJECTED: guest could not verify current UI tree; reason=application_window_unverified; error_type=IllegalStateException".equals(text(error, "message"));
    }

    private ru.voboost.voyemu.VehicleCan.Snapshot vehicleCan(String expectedBoot) throws Exception {
        return vehicleCan(expectedBoot, null);
    }

    private ru.voboost.voyemu.VehicleCan.Snapshot vehicleCan(String expectedBoot, CliProcess.Control control)
            throws Exception {
        BootData before = boot(control);
        if (!before.id().equals(expectedBoot) || !before.complete() || !before.enforcing())
            throw new IOException("CAN requires current ready Enforcing boot");
        var result = readVehicleCan(expectedBoot, control);
        BootData after = boot(control);
        if (!after.id().equals(before.id()) || !after.complete() || !after.enforcing())
            throw new IOException("CAN read crossed boot/policy boundary");
        return result;
    }

    private ru.voboost.voyemu.VehicleCan.Snapshot readVehicleCan(String expectedBoot, CliProcess.Control control) throws Exception {
        var read = client.canRead(id(), profile, owned.session(), 5000);
        JsonObject raw = control == null ? nativeRead(read, "vehicle-can")
                : nativeRead(read, "vehicle-can", control.remaining());
        return ru.voboost.voyemu.VehicleCan.read(raw, owned.session().id(), expectedBoot);
    }

    private record VehicleFrame(JsonObject tree,ru.voboost.voyemu.VehicleUi.Control control) {}
    private VehicleFrame openVehicle(ru.voboost.voyemu.VehicleUi.Feature feature,String boot) throws Exception {
        owned.launch(ru.voboost.voyemu.VehicleUi.OWNER,boot,"primary");long deadline=System.nanoTime()+Duration.ofSeconds(25).toNanos();
        boolean menuSent=false;int scrolls=0;ru.voboost.voyemu.UiEvidence.Unavailable last=null;
        while(System.nanoTime()<deadline) {
            JsonObject tree=settingsUi(boot,deadline),surface=ru.voboost.voyemu.UiEvidence.surface(tree,"primary");
            try {
                var control=ru.voboost.voyemu.VehicleUi.feature(surface,feature);
                if(feature==ru.voboost.voyemu.VehicleUi.Feature.FORCED_EV)ru.voboost.voyemu.VehicleUi.electricRadio(surface);
                return new VehicleFrame(tree,control);
            }
            catch(ru.voboost.voyemu.UiEvidence.Unavailable transition){last=transition;}
            if(!menuSent) {
                String target=null;
                try {target=ru.voboost.voyemu.VehicleUi.uniqueResource(surface,feature.menu());}
                catch(ru.voboost.voyemu.UiEvidence.Unavailable notVisible) {last=notVisible;}
                if(target!=null) {
                    // The OEM menu recomposes while it is walked, and the tap API refuses a target whose node is no
                    // longer live (TEST_INPUT_BLOCKED/target_missing, surfaced as a proved not-executed NotStarted).
                    // menuSent stays false, so the next bounded iteration re-reads the tree and re-resolves the entry.
                    try {tapNode(tree,target);menuSent=true;continue;}
                    catch(ru.voboost.voyemu.OwnedSession.NotStarted vanished) {last=new ru.voboost.voyemu.UiEvidence.Unavailable("OEM menu entry vanished before the tap executed: "+vanished.getMessage());}
                }
            }
            String resource=menuSent?feature.scroll():ru.voboost.voyemu.VehicleUi.OWNER+":id/menu_container";
            if(scrolls<16) {
                ru.voboost.voyemu.UiTarget target=null;
                try {target=ru.voboost.voyemu.UiTarget.selectScrollable(tree,profile,owned.session(),"primary",node->resource.equals(text(node,"resource_id")));}
                catch(IOException unsupportedContainer) { /* Selection only: no input has been dispatched. */ }
                if(target!=null) {
                    if(!menuSent&&scrolls<8)owned.scrollSettingsToStart(target);else owned.scrollSettings(target);
                    ++scrolls;continue; // A dispatch failure escapes; it cannot enter the selector catch or replay.
                }
            }
            Thread.sleep(100);
        }
        throw new ru.voboost.voyemu.UiEvidence.Unavailable("OEM feature not observable through bounded actual menu/scroll: "+(last==null?"no complete scene":last.getMessage()));
    }
    private ru.voboost.voyemu.VehiclePersistence.State vehicleObserve(ru.voboost.voyemu.VehicleUi.Feature feature) throws Exception {
        BootData before=boot(null);try(ProductRun.Operation operation=launcherOperation("vehicle-observe-"+feature.name())) {
            operation.observePriorBoot(before.id());operation.dispatch();VehicleFrame ui=openVehicle(feature,before.id());var can=vehicleCan(before.id());
            JsonObject proof=new JsonObject();proof.addProperty("bootId",before.id());proof.add("ui",ui.tree());proof.addProperty("checked",ui.control().checked());proof.add("can",can.receipt());
            Path path=work.resolve("vehicle-state-"+(++counter)+".json");RunFiles.write(path,proof);operation.complete(path);
            return new ru.voboost.voyemu.VehiclePersistence.State(can,ui.control().checked(),path);
        }
    }
    private ru.voboost.tests.agents.RuntimeObservation awaitVehicleInput(
            String agent, String input, String expectedBoot) throws Exception {
        var current = observeRuntime(text(status(), "activeGeneration"));
        return awaitVehicleInput(agent, input, expectedBoot, current);
    }

    private ru.voboost.tests.agents.RuntimeObservation awaitVehicleInput(String agent, String input,
            String expectedBoot, ru.voboost.tests.agents.RuntimeObservation initial) throws Exception {
        String generation = text(initial.runtime(), "generationId");
        return awaitObservation("Current native vehicle input", Duration.ofSeconds(30), (scope, deadline) -> {
            var current = observeRuntime(generation, scope, initial.publisher());
            if (!expectedBoot.equals(text(current.runtime().getAsJsonObject("publisher"), "bootId")))
                throw new IOException("vehicle input wait crossed boot");
            if (!current.desired(agent)) return current;
            String processes = startupProcesses(expectedBoot);
            try {
                for (String process : List.of("com.qinggan.canbus.service", input)) {
                    ru.voboost.tests.agents.VehicleInputReady.require(current, agent, process,
                            StartupObservation.identity(processes, expectedBoot, process));
                }
                return current;
            } catch (IOException pending) {
                throw new ru.voboost.voyemu.UiEvidence.Unavailable(pending.getMessage());
            }
        });
    }
    @FunctionalInterface
    interface Observation<T> {
        T read(CliProcess.Control control, long deadline) throws Exception;
    }

    static <T> T awaitObservation(String stage, Duration timeout, Observation<T> observation) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        var control = new CliProcess.Control(deadline, null);
        IOException pending = null;
        boolean read = false;
        try {
            while (deadline - System.nanoTime() >= MINIMUM_OBSERVATION_NANOS) {
                try {
                    T value = observation.read(control, deadline);
                    control.check();
                    if (value != null) return value;
                    read = true;
                } catch (ru.voboost.tests.agents.RuntimeObservation.Unavailable
                        | ru.voboost.tests.agents.ClusterObservation.Pending
                        | ru.voboost.voyemu.UiEvidence.Unavailable transition) {
                    pending = transition;
                    if (transition instanceof ru.voboost.tests.agents.ClusterObservation.Pending) read = true;
                }
                Thread.sleep(100);
            }
        } finally {
            control.close();
        }
        throw new IOException(stage + (read ? ": required state not confirmed" : ": observation unavailable"), pending);
    }

    private Path vehicleIntent(String agent, boolean armed, String expectedBoot,
            ru.voboost.voyemu.VehicleCan.Snapshot afterInput,
            ru.voboost.tests.agents.RuntimeObservation expected, CliProcess.Control control) throws Exception {
        var current = observeRuntime(text(expected.runtime(), "generationId"), control, expected.publisher());
        if (!expectedBoot.equals(text(current.runtime().getAsJsonObject("publisher"), "bootId"))
                || !expected.publisher().equals(current.publisher()) || !current.desired(agent))
            throw new IOException("vehicle intent read crossed boot, owner, or desired state");
        ru.voboost.voyemu.VehicleArmed.requireAfter(current, afterInput);
        boolean value = agent.equals("vehicle-drive-forced-ev")
                ? ru.voboost.voyemu.VehicleArmed.read(current)
                : ru.voboost.voyemu.VehicleArmed.readPedestrian(current);
        return value == armed ? lastRuntimeOfficial : null;
    }

    private Path vehicleSelect(ru.voboost.voyemu.VehicleUi.Feature feature,boolean wanted) throws Exception {
        BootData before=boot(null);try(ProductRun.Operation operation=launcherOperation("vehicle-select-"+feature.name()+"-"+wanted)) {
            operation.observePriorBoot(before.id());operation.dispatch();VehicleFrame initial=openVehicle(feature,before.id());
            String agent=feature==ru.voboost.voyemu.VehicleUi.Feature.FORCED_EV?"vehicle-drive-forced-ev":"vehicle-drive-pedestrian-warning";
            var ready=awaitVehicleInput(agent,ru.voboost.voyemu.VehicleUi.OWNER,before.id());boolean enabled=ready.desired(agent);Path readyEvidence=lastRuntimeOfficial;
            if(enabled)initial=openVehicle(feature,before.id());var first=vehicleCan(before.id());
            if (initial.control().checked() != wanted) {
                if (!initial.control().enabled()) throw new IOException("OEM vehicle selection is disabled");
                try {
                    tapNode(initial.tree(), initial.control().nodeId());
                } catch (OwnedSession.NotStarted rejected) {
                    if (!rejected.inputCoverageUnavailable()) throw rejected;
                    // Voyemu proved the first tap did not execute. Re-read the OEM target.
                    var fresh = openVehicle(feature, before.id());
                    if (fresh.control().checked() != wanted) {
                        if (!fresh.control().enabled()) throw rejected;
                        tapNode(fresh.tree(), fresh.control().nodeId());
                    }
                }
            }
            boolean forcedEv = feature == ru.voboost.voyemu.VehicleUi.Feature.FORCED_EV;
            Path proof = awaitObservation("OEM selection readback", Duration.ofSeconds(15), (scope, deadline) -> {
                JsonObject tree = settingsUi(before.id(), deadline);
                var control = ru.voboost.voyemu.VehicleUi.feature(
                        ru.voboost.voyemu.UiEvidence.surface(tree, "primary"), feature);
                if (control.checked() != wanted) return null;
                var can = vehicleCan(before.id(), scope);
                can.requireNext(first);
                if (can.selected(forcedEv) != wanted) return null;
                Path durable = enabled
                        ? vehicleIntent(agent, forcedEv ? wanted : !wanted, before.id(), can, ready, scope)
                        : null;
                if (enabled && durable == null) return null;
                JsonObject evidence = new JsonObject();
                evidence.addProperty("wanted", wanted);
                evidence.add("ui", tree);
                evidence.add("can", can.receipt());
                if (enabled) {
                    evidence.addProperty("inputReadyEvidence", readyEvidence.toString());
                    evidence.addProperty("durableIntentEvidence", durable.toString());
                }
                Path path = work.resolve("vehicle-selected-" + (++counter) + ".json");
                RunFiles.write(path, evidence);
                return path;
            });
            operation.complete(proof);
            return proof;
        }
    }
    private String drawerButton(JsonObject tree, String resource) throws IOException {
        String found = null;
        for (JsonElement value : ru.voboost.voyemu.UiEvidence.surface(tree, "primary").getAsJsonArray("nodes")) {
            JsonObject node = value.getAsJsonObject();
            if (!("com.qinggan.app.launcher:id/" + resource).equals(text(node, "resource_id"))
                    || !truth(node, "visible") || !truth(node, "enabled") || !truth(node, "clickable")) continue;
            if (found != null) throw new IOException("ambiguous drawer control: " + resource);
            found = text(node, "id");
        }
        return found;
    }
    private void closePwDrawer(String boot) throws Exception {
        boolean cancelled = false, closed = false;
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (deadline - System.nanoTime() >= MINIMUM_OBSERVATION_NANOS) {
            JsonObject tree = settingsUi(boot, deadline);
            String cancel = drawerButton(tree, "screen_up_cancel_btn");
            String edit = drawerButton(tree, "screen_up_edit_btn");
            if (cancel == null && edit == null) return;
            if (cancel != null && !cancelled) {
                tapNode(tree, cancel);
                cancelled = true;
            } else if (edit != null && !closed) {
                owned.closeVehicleDrawer(ru.voboost.voyemu.UiTarget.selectDrawerClose(tree, profile, owned.session()));
                closed = true;
            }
            Thread.sleep(100);
        }
        throw new IOException("OEM drawer did not close before opening Setting");
    }

    private record PwDrawerEdit(JsonObject tree, boolean changed, JsonObject borrowed) {}

    /** Every accepted edit input is sent once; subsequent iterations only observe its effect. */
    private PwDrawerEdit setPwDrawer(String expectedBoot, boolean present, boolean borrow, JsonObject borrowed)
            throws Exception {
        boolean opened = false, editing = false, moved = false, saved = false;
        boolean spareMoved = false;
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (deadline - System.nanoTime() >= MINIMUM_OBSERVATION_NANOS) {
            JsonObject tree = settingsUi(expectedBoot, deadline);
            JsonObject surface = ru.voboost.voyemu.UiEvidence.surface(tree, "primary");
            String edit = drawerButton(tree, "screen_up_edit_btn");
            if (edit != null) {
                boolean exists;
                try {
                    ru.voboost.voyemu.VehicleDrawer.pedestrianWarningCard(surface);
                    exists = true;
                } catch (ru.voboost.voyemu.UiEvidence.Unavailable absent) {
                    exists = false;
                }
                if (exists == present) return new PwDrawerEdit(tree, moved, borrowed);
                if (!editing) {
                    editing = true;
                    tapNode(tree, edit);
                }
            } else if (editing) {
                if (!moved) {
                    String card;
                    try { card = ru.voboost.voyemu.VehicleDrawer.pedestrianWarningEdit(surface, present); }
                    catch (ru.voboost.voyemu.UiEvidence.Unavailable transition) { Thread.sleep(50); continue; }
                    if (borrow && present && !spareMoved
                            && ru.voboost.voyemu.VehicleDrawer.warningWorkspaceFull(surface)) {
                        borrowed = ru.voboost.voyemu.VehicleDrawer.spareBounds(surface);
                        String spare = ru.voboost.voyemu.VehicleDrawer.spareEdit(surface, false);
                        spareMoved = true;
                        tapNode(tree, spare);
                        continue;
                    }
                    if (present && spareMoved) {
                        try { ru.voboost.voyemu.VehicleDrawer.spareEdit(surface, true); }
                        catch (ru.voboost.voyemu.UiEvidence.Unavailable transition) { Thread.sleep(50); continue; }
                    }
                    moved = true;
                    tapNode(tree, card);
                } else if (!saved) {
                    try { ru.voboost.voyemu.VehicleDrawer.pedestrianWarningEdit(surface, !present); }
                    catch (ru.voboost.voyemu.UiEvidence.Unavailable transition) { Thread.sleep(50); continue; }
                    if (!present && borrowed != null) {
                        if (!spareMoved) {
                            String spare = ru.voboost.voyemu.VehicleDrawer.spareEdit(surface, true);
                            spareMoved = true;
                            tapNode(tree, spare);
                            continue;
                        }
                        try { ru.voboost.voyemu.VehicleDrawer.spareEdit(surface, false); }
                        catch (ru.voboost.voyemu.UiEvidence.Unavailable transition) { Thread.sleep(50); continue; }
                        if (!borrowed.equals(ru.voboost.voyemu.VehicleDrawer.spareBounds(surface)))
                            throw new IOException("PW test could not restore the original drawer slot");
                    }
                    String save = drawerButton(tree, "screen_up_save_btn");
                    if (save != null) {
                        saved = true;
                        tapNode(tree, save);
                    }
                }
            } else if (!opened) {
                ru.voboost.voyemu.UiTarget handle = null;
                try { handle = ru.voboost.voyemu.UiTarget.selectDrawerHandle(tree, profile, owned.session()); }
                catch (ru.voboost.voyemu.UiEvidence.Unavailable transition) { /* Wait for the drawer handle. */ }
                if (handle != null) {
                    opened = true;
                    owned.openVehicleDrawer(handle);
                }
            }
            Thread.sleep(50);
        }
        throw new IOException("PW drawer edit did not complete; accepted inputs were not replayed");
    }

    private record PwDrawerSelection(Path evidence, PwDrawerEdit edit) {}

    private Path pwDrawerSelect(int wanted) throws Exception {
        return pwDrawerSelect(wanted, false, null).evidence();
    }

    private PwDrawerSelection pwDrawerSelect(int wanted, boolean borrow, JsonObject borrowed)
            throws Exception {
        if (wanted != 1 && wanted != 2) throw new IllegalArgumentException("OEM PW value must be 1 or 2");
        final String agent = "vehicle-drive-pedestrian-warning";
        BootData before = boot(null);
        try (ProductRun.Operation operation = launcherOperation("pw-drawer-select-" + wanted)) {
            operation.observePriorBoot(before.id());
            operation.dispatch();
            var ready = awaitVehicleInput(agent, "com.qinggan.app.launcher", before.id());
            if (!ready.desired(agent)) throw new IOException("PW agent must stay enabled during drawer input");
            var initial = vehicleCan(before.id());
            PwDrawerEdit edit = setPwDrawer(before.id(), true, borrow, borrowed);
            JsonObject tree = edit.tree();
            if (initial.pw() != wanted) {
                String card = ru.voboost.voyemu.VehicleDrawer.pedestrianWarningCard(
                        ru.voboost.voyemu.UiEvidence.surface(tree, "primary"));
                tapNode(tree, card);
            }
            Path proof = awaitObservation("PW drawer readback", Duration.ofSeconds(15), (scope, deadline) -> {
                var current = vehicleCan(before.id(), scope);
                current.requireNext(initial);
                if (current.pw() != wanted) return null;
                Path durable = vehicleIntent(agent, wanted == 1, before.id(), current, ready, scope);
                if (durable == null) return null;
                JsonObject evidence = new JsonObject();
                evidence.addProperty("kind", "pw-oem-drawer-selection");
                evidence.addProperty("agentEnabled", true);
                evidence.addProperty("wanted", wanted);
                evidence.add("beforeUi", tree);
                evidence.add("can", current.receipt());
                evidence.addProperty("durableIntentEvidence", durable.toString());
                Path path = work.resolve("pw-drawer-selected-" + (++counter) + ".json");
                RunFiles.write(path, evidence);
                return path;
            });
            operation.complete(proof);
            return new PwDrawerSelection(proof, edit);
        }
    }
    private record DrawerFrame(JsonObject tree,ru.voboost.voyemu.VehicleDrawer.State state) {}
    private DrawerFrame drawerFrame(String expectedBoot,boolean mayOpen) throws Exception {
        long deadline=System.nanoTime()+Duration.ofSeconds(10).toNanos(),stableSince=0;String geometry="",observer="",lastUnavailable="";boolean sent=false;
        while(System.nanoTime()<deadline) {
            JsonObject tree;
            try {tree=settingsUi(expectedBoot,deadline);}
            catch(IOException unavailable) {
                if(sent&&lastNativeEvidence!=null&&pendingUiObservation(RunFiles.read(lastNativeEvidence), unavailable))captureDrawerDiagnostics(expectedBoot);
                throw unavailable;
            }
            String source=text(tree,"source_instance");if(observer.isEmpty())observer=source;else if(!observer.equals(source))throw new IOException("drawer observer lifetime changed");
            try {
                var state=ru.voboost.voyemu.VehicleDrawer.read(ru.voboost.voyemu.UiEvidence.surface(tree,"primary"));
                if(!geometry.equals(state.geometry())){geometry=state.geometry();stableSince=System.nanoTime();}
                else if(System.nanoTime()-stableSince>=Duration.ofMillis(300).toNanos())return new DrawerFrame(tree,state);
            }catch(ru.voboost.voyemu.UiEvidence.Unavailable absent) {
                lastUnavailable=absent.getMessage();geometry="";stableSince=0;
                if(mayOpen&&!sent) {
                    ru.voboost.voyemu.UiTarget target=null;
                    try {target=ru.voboost.voyemu.UiTarget.selectDrawerHandle(tree,profile,owned.session());}catch(ru.voboost.voyemu.UiEvidence.Unavailable unavailableReceiver) { /* Observation only, no input. */ }
                    if(target!=null){sent=true;owned.openVehicleDrawer(target);}
                }
            }
            Thread.sleep(50);
        }
        captureDrawerDiagnostics(expectedBoot);
        throw new IOException("actual drawer card/switch geometry did not settle; openingDispatched="+sent
            +"; lastObservation="+lastUnavailable+"; no input replay");
    }
    private void captureDrawerDiagnostics(String expectedBoot)throws InterruptedException {
        JsonObject proof=new JsonObject();proof.addProperty("kind","drawer-coverage-diagnostic-only");proof.addProperty("bootId",expectedBoot);
        proof.addProperty("failedDefaultUi",lastNativeEvidence.toString());
        try {nativeRead(client.multimediaUi(id(),profile,owned.session(),List.of("primary"),"all_attached",null,null,2000),"drawer-diagnostic-attached",Duration.ofSeconds(3));proof.addProperty("allAttached",lastNativeEvidence.toString());}
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw interrupted;}
        catch(Exception unavailable){proof.addProperty("attachedError",unavailable.getClass().getSimpleName());}
        try {
            Path directory=voyemuRepository.resolve(".local/build/tmp/a9-drawer-gap-"+UUID.randomUUID());
            nativeRead(client.capturePrimary(id(),profile,owned.session(),directory),"drawer-diagnostic-capture",Duration.ofSeconds(3));
            proof.addProperty("capture",lastNativeEvidence.toString());proof.addProperty("artifactDirectory",directory.toString());
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw interrupted;}
        catch(Exception unavailable){proof.addProperty("captureError",unavailable.getClass().getSimpleName());}
        try{RunFiles.write(work.resolve("drawer-coverage-diagnostic-"+(++counter)+".json"),proof);}catch(IOException ignored){ /* Original acceptance failure remains primary. */ }
    }
    private boolean fevArmed(String expectedBoot,JsonArray artifacts) throws Exception {
        JsonObject raw=json(output(run(List.of("--observe"),Duration.ofSeconds(30),null,null)));
        Path file=work.resolve("fev-intent-"+(++counter)+".json");RunFiles.write(file,raw);artifacts.add(file.toString());
        // Live producer authority and retained scalar data are distinct, both must be available.
        var current=ru.voboost.tests.agents.RuntimeObservation.read(raw,new ru.voboost.tests.agents.RuntimeObservation.Identity(expectedBoot,version,manifest,catalog,text(status(),"activeGeneration")));
        boolean value=ru.voboost.voyemu.VehicleArmed.read(current);
        BootData after=boot(null);if(!expectedBoot.equals(after.id())||!after.complete()||!after.enforcing())throw new IOException("FEV intent read crossed boot/policy boundary");return value;
    }
    private ru.voboost.voyemu.ForcedEvProgram.Feedback forcedEvFeedback(String generation) throws Exception {
        return inReadyBoot(() -> boot(null), before -> {
            String boot = before.id();
            var can = readVehicleCan(boot, null);
            JsonArray artifacts = new JsonArray();
            Boolean armed = null;
            String intentStatus = "observed";
            try {
                var current = readRuntime(boot, generation, null, null, false);
                artifacts.add(lastRuntimeOfficial.toString());
                armed = ru.voboost.voyemu.VehicleArmed.read(current);
            } catch (ru.voboost.voyemu.UiEvidence.Unavailable pending) { intentStatus = pending.getMessage(); }
            JsonObject proof = new JsonObject();
            proof.add("can", can.receipt());
            proof.addProperty("armed", armed);
            proof.addProperty("intentStatus", intentStatus);
            proof.add("artifacts", artifacts);
            Path file = work.resolve("agent-forced-ev-feedback-" + (++counter) + ".json");
            RunFiles.write(file, proof);
            return new ru.voboost.voyemu.ForcedEvProgram.Feedback(can, armed, file);
        });
    }
    private ru.voboost.voyemu.ForcedEvProgram.State fevObserve() throws Exception {
        BootData before=boot(null);try(ProductRun.Operation operation=launcherOperation("fev-drawer-observe")) {
            operation.observePriorBoot(before.id());operation.dispatch();DrawerFrame drawer=drawerFrame(before.id(),true);var can=vehicleCan(before.id());JsonArray artifacts=new JsonArray();Boolean armed=null;String intentStatus="observed";try{armed=fevArmed(before.id(),artifacts);}catch(ru.voboost.voyemu.UiEvidence.Unavailable absent){intentStatus=absent.getMessage();}
            JsonObject proof=new JsonObject();proof.add("ui",drawer.tree());proof.add("can",can.receipt());proof.addProperty("sampledDurableArmed",armed);proof.addProperty("intentStatus",intentStatus);proof.addProperty("operationTimeAcknowledgement",false);proof.add("artifacts",artifacts);
            Path file=work.resolve("fev-drawer-state-"+(++counter)+".json");RunFiles.write(file,proof);operation.complete(file);
            return new ru.voboost.voyemu.ForcedEvProgram.State(can,drawer.state().mode(),drawer.state().forceSelected(),armed,file);
        }
    }
    private void awaitDrawerForceOn(String expectedBoot,JsonObject originalTree,String cardId)throws Exception {
        JsonObject originalSurface=ru.voboost.voyemu.UiEvidence.surface(originalTree,"primary");String drawerWindow="";
        for(JsonElement item:originalSurface.getAsJsonArray("nodes")){JsonObject node=item.getAsJsonObject();if(cardId.equals(text(node,"id")))drawerWindow=text(node,"window_id");}
        if(drawerWindow.isBlank())throw new IOException("original actual drawer window identity required");
        boolean closeSent=false,closed=false,consentSent=false;String consentWindow="";long deadline=System.nanoTime()+Duration.ofSeconds(10).toNanos();
        while(System.nanoTime()<deadline) {
            if(deadline-System.nanoTime()<MINIMUM_OBSERVATION_NANOS)break;
            JsonObject tree=settingsUi(expectedBoot,deadline),surface=ru.voboost.voyemu.UiEvidence.surface(tree,"primary");
            if(closeSent){boolean present=false;for(JsonElement item:surface.getAsJsonArray("windows"))if(drawerWindow.equals(text(item.getAsJsonObject(),"id")))present=true;closed=!present;}
            String consent=ru.voboost.voyemu.VehicleUi.forceConsent(surface);Boolean selected=null;
            try{selected=ru.voboost.voyemu.VehicleDrawer.read(surface).forceSelected();}catch(ru.voboost.voyemu.UiEvidence.Unavailable otherScene){ /* Current OEM consent/underlying scene may replace the drawer. */ }
            if(consent!=null&&!consentSent&&(!closeSent||closed)) {
                var ready=ru.voboost.voyemu.VehicleUi.forceConsentTarget(tree,profile,owned.session());
                if(consentWindow.isEmpty())consentWindow=ready.window();else if(!consentWindow.equals(ready.window()))throw new IOException("FEV consent window changed before input");
                if(ready.target()!=null){consentSent=true;owned.tap(ready.target());}
            }
            else if(consent==null&&!consentSent&&!closeSent&&Boolean.FALSE.equals(selected)) {
                var target=ru.voboost.voyemu.UiTarget.selectDrawerClose(tree,profile,owned.session());closeSent=true;owned.closeVehicleDrawer(target);
            }
            var can=vehicleCan(expectedBoot);
            if(can.fev()==5&&(!closeSent||closed)&&(consentSent||Boolean.TRUE.equals(selected)))return;
            Thread.sleep(50);
        }
        throw new IOException("one drawer ON input/one optional background close/one actual OEM consent lacks independent ON effect; none are replayed");
    }
    private void fevDrawerTap(boolean switchControl,boolean wanted) throws Exception {
        BootData before=boot(null);
        var publisher = observeRuntime(text(status(), "activeGeneration"));
        try(ProductRun.Operation operation=launcherOperation(switchControl?"fev-drawer-switch-"+wanted:"fev-drawer-next-mode")) {
            operation.observePriorBoot(before.id());operation.dispatch();DrawerFrame frame=drawerFrame(before.id(),true);
            awaitVehicleInput("vehicle-drive-forced-ev",LAUNCHER,before.id(),publisher);frame=drawerFrame(before.id(),true);
            if(switchControl) {
                if(frame.state().mode()!=ru.voboost.voyemu.ForcedEvProgram.Mode.ELECTRIC||frame.state().forceSelected()==null||frame.state().switchId().isBlank())throw new IOException("actual Electric force switch required");
                if(frame.state().forceSelected()==wanted)throw new IOException("FEV switch already has requested value; no synthetic toggle/replay");
            }
            tapNode(frame.tree(),switchControl?frame.state().switchId():frame.state().cardId());
            if(switchControl&&wanted)awaitDrawerForceOn(before.id(),frame.tree(),frame.state().cardId());
            JsonObject proof=new JsonObject();proof.addProperty("control",switchControl?"force-switch":"power-mode-card");proof.addProperty("dispatch", "one accepted observed tap; effect independently read by program");proof.add("before",frame.tree());
            Path file=work.resolve("fev-drawer-tap-"+(++counter)+".json");RunFiles.write(file,proof);operation.complete(file);
        }
    }
    private void fevSelectOem(boolean wanted)throws Exception {
        fevSelectOem(wanted, false);
    }

    private ru.voboost.voyemu.VehiclePersistence.State fevSelectOem(boolean wanted, boolean requireTransition)
            throws Exception {
        String agent = "vehicle-drive-forced-ev";
        BootData before=boot(null);
        var publisher = observeRuntime(text(status(), "activeGeneration"));
        try(ProductRun.Operation operation=launcherOperation("fev-oem-"+wanted)) {
            operation.observePriorBoot(before.id());operation.dispatch();VehicleFrame initial=openVehicle(ru.voboost.voyemu.VehicleUi.Feature.FORCED_EV,before.id());
            var ready = awaitVehicleInput(agent, ru.voboost.voyemu.VehicleUi.OWNER, before.id(), publisher);
            Path readyEvidence = lastRuntimeOfficial;
            if (ready.desired(agent)) initial=openVehicle(ru.voboost.voyemu.VehicleUi.Feature.FORCED_EV,before.id());
            var radio=ru.voboost.voyemu.VehicleUi.electricRadio(ru.voboost.voyemu.UiEvidence.surface(initial.tree(),"primary"));
            var priorCan = requireTransition ? vehicleCan(before.id()) : null;
            if (requireTransition) {
                if (!ready.desired(agent) || !radio.checked() || initial.control().checked() == wanted
                        || priorCan.fev() != (wanted ? 2 : 5))
                    throw new IOException("Explicit OEM FEV input requires the enabled agent and opposite Electric state");
            }
            if(!radio.checked()) {
                tapNode(initial.tree(),radio.nodeId());long deadline=System.nanoTime()+Duration.ofSeconds(10).toNanos();boolean selected=false;
                while(System.nanoTime()<deadline){JsonObject tree=settingsUi(before.id(),deadline);if(ru.voboost.voyemu.VehicleUi.electricRadio(ru.voboost.voyemu.UiEvidence.surface(tree,"primary")).checked()){selected=true;initial=new VehicleFrame(tree,ru.voboost.voyemu.VehicleUi.feature(ru.voboost.voyemu.UiEvidence.surface(tree,"primary"),ru.voboost.voyemu.VehicleUi.Feature.FORCED_EV));break;}Thread.sleep(50);}
                if(!selected)throw new IOException("one Electric radio input did not select the actual radio; no replay");
            }
            if(initial.control().checked()!=wanted){if(!initial.control().enabled())throw new IOException("actual OEM FEV switch is disabled");tapNode(initial.tree(),initial.control().nodeId());}
            boolean consentSent=false;JsonObject consentTree=null;String consentWindow="";long deadline=System.nanoTime()+Duration.ofSeconds(10).toNanos();
            while(System.nanoTime()<deadline) {
                if(deadline-System.nanoTime()<MINIMUM_OBSERVATION_NANOS)break;
                JsonObject tree=settingsUi(before.id(),deadline),surface=ru.voboost.voyemu.UiEvidence.surface(tree,"primary");String consent=ru.voboost.voyemu.VehicleUi.forceConsent(surface);
                if(consent!=null) {
                    if(!wanted)throw new IOException("unexpected FEV ON consent during OFF");
                    if(!consentSent) {
                        var consentTarget=ru.voboost.voyemu.VehicleUi.forceConsentTarget(tree,profile,owned.session());
                        if(consentWindow.isEmpty())consentWindow=consentTarget.window();else if(!consentWindow.equals(consentTarget.window()))throw new IOException("FEV consent window changed before input");
                        if(consentTarget.target()!=null){consentSent=true;consentTree=tree;owned.tap(consentTarget.target());}
                    }
                    Thread.sleep(50);continue;
                }
                var feature=ru.voboost.voyemu.VehicleUi.feature(surface,ru.voboost.voyemu.VehicleUi.Feature.FORCED_EV);var can=vehicleCan(before.id());
                if(feature.checked()==wanted&&can.selected(true)==wanted) {
                    JsonObject proof=new JsonObject();proof.add("ui",tree);proof.add("can",can.receipt());proof.addProperty("wanted",wanted);proof.addProperty("consentSent",consentSent);
                    if (requireTransition) {
                        if (can.fev() != (wanted ? 5 : 2) || (wanted && !consentSent)) {
                            Thread.sleep(50);
                            continue;
                        }
                        can.requireNext(priorCan);
                        proof.add("priorUi", initial.tree());
                        proof.add("priorCan", priorCan.receipt());
                        if (consentTree != null) proof.add("consentUi", consentTree);
                        proof.addProperty("inputReadyEvidence", readyEvidence.toString());
                        proof.addProperty("durableIntentEvidence", awaitForcedEvOemIntent(wanted, can, ready).toString());
                    }
                    Path file=work.resolve("fev-oem-"+(++counter)+".json");RunFiles.write(file,proof);operation.complete(file);
                    return new ru.voboost.voyemu.VehiclePersistence.State(can, feature.checked(), file);
                }
                Thread.sleep(50);
            }
            throw new IOException("one OEM FEV selection/consent lacks actual checked and CAN readback; no replay");
        }
    }

    private Path awaitForcedEvOemIntent(boolean wanted, ru.voboost.voyemu.VehicleCan.Snapshot can,
            ru.voboost.tests.agents.RuntimeObservation ready) throws Exception {
        return awaitObservation("Durable VehicleSetting FEV intent", Duration.ofSeconds(15),
                (scope, deadline) -> vehicleIntent("vehicle-drive-forced-ev", wanted, can.boot(), can, ready, scope));
    }
    private void fevColdReboot() throws Exception {
        try(ProductRun.Operation operation=reserve(ProductRun.Kind.REBOOT,"fev-cold-reboot")){rebootAndVerify(operation,true);}
    }
    private ru.voboost.voyemu.VehicleCan.PowerSnapshot vehiclePowerCan(String expectedBoot) throws Exception {
        BootData before=boot(null);
        if(!before.id().equals(expectedBoot)||!before.complete()||!before.enforcing())throw new IOException("CAN requires current ready Enforcing boot");
        JsonObject raw=nativeRead(client.canRead(id(),profile,owned.session(),5000),"vehicle-power-can");
        var result=ru.voboost.voyemu.VehicleCan.readPower(raw,owned.session().id(),expectedBoot);
        BootData after=boot(null);
        if(!after.id().equals(expectedBoot)||!after.complete()||!after.enforcing())throw new IOException("CAN read crossed boot/policy boundary");
        return result;
    }
    static void requirePowerKeyPrior(JsonObject system,ru.voboost.voyemu.VehicleCan.PowerSnapshot can,String session,String boot,boolean awake)throws IOException {
        JsonObject value=object(system,"value"),power=object(value,"power");
        if(!session.equals(can.session())||!boot.equals(can.boot())||!boot.equals(text(system,"boot_id"))||!boot.equals(text(value,"boot_id"))
                ||!ru.voboost.voyemu.VehiclePower.matches(power,awake)||can.acc()!=(awake?2:0))
            throw new IOException("SDK Power key requires the exact current opposite power/ACC state; no input dispatched");
    }
    private Path fevWarmCycle() throws Exception {
        BootData before=boot(null);try(ProductRun.Operation operation=launcherOperation("fev-warm-power-cycle")) {
            operation.observePriorBoot(before.id());var initial=vehiclePowerCan(before.id());
            JsonArray reads=new JsonArray(),inputs=new JsonArray();boolean dispatched=false;
            for(String key:List.of("sleep","wake")) {
                var priorCan=vehiclePowerCan(before.id());priorCan.requireNext(initial);
                long priorDeadline=System.nanoTime()+Duration.ofSeconds(10).toNanos();
                JsonObject priorSystem=nativeRead(client.systemRead(id(),profile,owned.session(),5000),"fev-power-before");
                while("unavailable".equals(text(priorSystem,"status")) && priorSystem.has("value")
                        && priorSystem.get("value").isJsonNull()) {
                    if(System.nanoTime()>=priorDeadline)
                        throw new IOException("Current power observation stayed unavailable before SDK Power key");
                    Thread.sleep(100);
                    var next=vehiclePowerCan(before.id());
                    next.requireNext(priorCan);
                    priorCan=next;
                    priorSystem=nativeRead(client.systemRead(id(),profile,owned.session(),5000),"fev-power-before");
                }
                requirePowerKeyPrior(priorSystem,priorCan,owned.session().id(),before.id(),key.equals("sleep"));
                if(!dispatched){operation.dispatch();dispatched=true;}
                JsonObject keyReceipt=owned.powerKey();JsonObject input=new JsonObject();input.addProperty("requestedTransition",key);
                input.add("nativeSdkKey",keyReceipt);input.add("beforeCan",priorCan.receipt());input.add("beforeSystem",priorSystem);
                Path receipt=work.resolve("fev-power-key-"+(++counter)+".json");RunFiles.write(receipt,input);inputs.add(receipt.toString());
                int wanted=key.equals("sleep")?0:2;long deadline=System.nanoTime()+Duration.ofSeconds(10).toNanos();boolean ready=false;
                while(System.nanoTime()<deadline) {
                    var can=vehiclePowerCan(before.id());can.requireNext(priorCan);JsonObject system=nativeRead(client.systemRead(id(),profile,owned.session(),5000),"fev-power-system");reads.add(can.receipt());reads.add(system);
                    JsonObject value=object(system,"value"),power=object(value,"power");
                    if(before.id().equals(text(system,"boot_id"))&&before.id().equals(text(value,"boot_id"))&&ru.voboost.voyemu.VehiclePower.matches(power,key.equals("wake"))&&can.acc()==wanted){ready=true;initial=can;break;}
                    Thread.sleep(100);
                }
                if(!ready)throw new IOException("one accepted SDK Power key lacks the actual "+key+" ACC/display edge; no replay");
            }
            long recoveryDeadline=System.nanoTime()+Duration.ofSeconds(10).toNanos();
            var recovered=vehiclePowerCan(before.id());
            recovered.requireNext(initial);
            for (;;) {
                reads.add(recovered.receipt());
                if(recovered.acc()!=2)throw new IOException("ACC left the awake state during CAN getter recovery");
                if(!recovered.featureGettersPending())break;
                if(System.nanoTime()>=recoveryDeadline)
                    throw new IOException("FEV/PW CAN getters did not recover after the confirmed wake");
                Thread.sleep(100);
                var next=vehiclePowerCan(before.id());
                next.requireNext(recovered);
                recovered=next;
            }
            JsonObject proof=new JsonObject();proof.addProperty("bootId",before.id());proof.addProperty("operation",operation.receiptPath().toString());proof.add("reads",reads);proof.add("sdkPowerInputs",inputs);
            proof.add("recoveredFeatureGetters",recovered.receipt());
            Path file=work.resolve("fev-warm-cycle-"+(++counter)+".json");RunFiles.write(file,proof);operation.complete(file);return file;
        }
    }
    /** A dedicated CLI observer owns its paths and counters; the product action thread never shares its counter. */
    private NativeCanMonitor nativeCanMonitor(String boot,Path directory)throws Exception {
        RunFiles.directory(directory);Path cli=RunFiles.directory(directory.resolve("cli"));var sequence=new java.util.concurrent.atomic.AtomicInteger();
        return new NativeCanMonitor(cursor->{int n=sequence.incrementAndGet();List<String> command=List.of(binary.toString(),"--observe-native-can",cursor,"--device",device);
            var result=CliProcess.run(command,cli.resolve("read-"+n+".out"),cli.resolve("read-"+n+".err"),Duration.ofSeconds(5),(stream,line,control)->null);
            String raw=output(result);if(raw.length()>262144)throw new IOException("native CAN snapshot exceeds its closed bound");return json(raw);
        },boot,RunFiles.directory(directory.resolve("pages")));
    }
    private JsonObject closedEcuSource(String expectedBoot)throws Exception {
        JsonObject source=owned.readCanHistory();NativeCanMonitor.requireClosedEcuSource(source,expectedBoot);
        if(!owned.session().id().equals(text(source,"session_id"))||!expectedBoot.equals(text(source,"guest_boot_id"))||integer(source,"owned_android_pid")!=androidPid||!"closed_ecu_boundary_v1".equals(text(source,"capability")))throw new IOException("closed ECU capability differs from the current owned Android source");return source;
    }
    private long boundaryBootMs(JsonObject source,String field)throws IOException {
        try{JsonElement value=source.get(field);if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString()||!value.getAsString().matches("[1-9][0-9]{0,19}"))throw new IOException("CAN boundary clock is not a positive decimal");var ns=new java.math.BigInteger(value.getAsString());if(ns.bitLength()>64)throw new IOException("CAN boundary clock overflow");return ns.divide(java.math.BigInteger.valueOf(1000000)).longValueExact();}catch(ArithmeticException malformed){throw new IOException("CAN boundary clock range",malformed);}
    }
    private Path nativeTerminal(Path directory,String expectedBoot,long minimumMs)throws Exception {
        Path pages=directory.resolve("pages"),last;try(var files=Files.list(pages)){last=files.filter(file->file.getFileName().toString().matches("native-can-[0-9]{8}\\.json")).max(java.util.Comparator.comparing(file->file.getFileName().toString())).orElseThrow(()->new IOException("native terminal page missing"));}
        JsonObject page=RunFiles.read(last);if(!expectedBoot.equals(text(page,"bootId"))||!"active".equals(text(page,"status"))||!falseValue(page,"gap")||new java.math.BigInteger(text(page,"sampledBootMs")).compareTo(java.math.BigInteger.valueOf(minimumMs))<=0)throw new IOException("native terminal does not follow the closed ECU history anchor");return last;
    }
    private long fevSampleBootMs()throws Exception {return Long.parseLong(vehicleCan(boot(null).id()).sampled())/1_000_000L;}
    private ru.voboost.voyemu.ForcedEvProgram.Proof verifyForcedEvProgram(JsonObject candidateCatalog,ru.voboost.voyemu.VehicleCan.Snapshot baseline) throws Exception {
        String expectedGeneration = text(status(), "activeGeneration");
        final NativeCanMonitor[] monitor={null};final boolean[] window={false};final Path[] currentEvidence={null};final int[] epoch={0};
        Path evidenceRoot=RunFiles.directory(work.resolve("fev-native-"+(++counter)));
        try {
            return ru.voboost.voyemu.ForcedEvProgram.run(new ru.voboost.voyemu.ForcedEvProgram.Port() {
                private void openMonitor()throws Exception {currentEvidence[0]=evidenceRoot.resolve("boot-"+(++epoch[0]));monitor[0]=nativeCanMonitor(boot(null).id(),currentEvidence[0]);}
                public ru.voboost.voyemu.ForcedEvProgram.Admission admit()throws Exception {
                    JsonObject proof=new JsonObject();proof.add("publicCan",baseline.receipt());proof.addProperty("coldBoundaryCapability",false);proof.addProperty("fullEvidenceDirectory",evidenceRoot.toString());boolean continuous=false,durable=false;String reason;
                    try {JsonArray artifacts=new JsonArray();fevArmed(baseline.boot(),artifacts);proof.add("durableIntentSource",artifacts);durable=true;openMonitor();proof.addProperty("nativeEvidence",currentEvidence[0].toString());monitor[0].close();monitor[0]=null;continuous=true;JsonObject capabilities=nativeRead(client.info(id()),"fev-composite-capabilities");Set<String> operations=new java.util.HashSet<>();if(capabilities.has("operations")&&capabilities.get("operations").isJsonArray())for(JsonElement operation:capabilities.getAsJsonArray("operations"))if(operation.isJsonPrimitive()&&operation.getAsJsonPrimitive().isString())operations.add(operation.getAsString());if(!operations.containsAll(Set.of("vehicle.can.history","session.restart")))throw new IOException("owned closed-ECU history and restart capability unavailable");proof.add("closedEcuSource",closedEcuSource(baseline.boot()));proof.addProperty("coldBoundaryCapability",true);reason="Actual native recvmsg receiver and authenticated durable FEV intent are observable; the program still requires every live window and strict closed-ECU cold-boundary proof";}
                    catch(IOException unavailable){reason=unavailable.getMessage();}
                    boolean fullCoverage=continuous&&truth(proof,"coldBoundaryCapability");if(continuous&&!fullCoverage)reason="Actual live native RX is available, but strict closed-ECU cold-boundary admission is not yet available; no FEV program mutations are authorized";proof.addProperty("continuousLiveFeedback",continuous);proof.addProperty("continuousFeedback",fullCoverage);proof.addProperty("durableIntent",durable);proof.addProperty("reason",reason);Path file=work.resolve("fev-capability-"+(++counter)+".json");RunFiles.write(file,proof);
                    return ru.voboost.voyemu.ForcedEvProgram.Admission.observed(continuous,durable,truth(proof,"coldBoundaryCapability"),reason,List.of(file));
                }
                public void enableAgent(boolean enabled)throws Exception {verifySettingsTab();setAgent(candidateCatalog,"vehicle-drive-forced-ev",enabled);}
                public void selectOem(boolean on)throws Exception {fevSelectOem(on);}
                public void openDrawer()throws Exception {try(ProductRun.Operation operation=launcherOperation("fev-open-drawer")){BootData boot=boot(null);operation.observePriorBoot(boot.id());operation.dispatch();awaitVehicleInput("vehicle-drive-forced-ev",LAUNCHER,boot.id());DrawerFrame frame=drawerFrame(boot.id(),true);Path file=work.resolve("fev-open-"+(++counter)+".json");RunFiles.write(file,frame.tree());operation.complete(file);}}
                public void switchForce(boolean on)throws Exception {fevDrawerTap(true,on);}
                public void nextMode()throws Exception {fevDrawerTap(false,false);}
                public void warmCycle() throws Exception {
                    if (!window[0]) {
                        fevWarmCycle();
                        return;
                    }
                    String boot = boot(null).id();
                    JsonObject before = closedEcuSource(boot);
                    long anchor = boundaryBootMs(before, "capture_boottime_ns");
                    monitor[0].endOff(anchor);
                    window[0] = false;
                    monitor[0].close();
                    monitor[0] = null;
                    Path terminal = nativeTerminal(currentEvidence[0], boot, anchor);

                    Path transition = fevWarmCycle();
                    openMonitor();
                    JsonObject after = closedEcuSource(boot);
                    monitor[0].beginOffAfterWarm(before, after);
                    window[0] = true;

                    JsonObject proof = new JsonObject();
                    proof.addProperty("kind", "live-native-rx-and-closed-ecu-warm-off");
                    proof.addProperty("preSleepTerminal", terminal.toString());
                    proof.addProperty("powerTransition", transition.toString());
                    proof.addProperty("postWakeEvidence", currentEvidence[0].toString());
                    proof.add("before", before);
                    proof.add("after", after);
                    RunFiles.write(evidenceRoot.resolve("warm-off.json"), proof);
                }
                public void coldReboot()throws Exception {

                    if(window[0]) {
                        BootData before = boot(null);
                        JsonObject expectedBoot = fullRestartExpected(before,
                                nativeRead(client.status(id(), profile, owned.session()), "fev-cold-before"));
                        Path directory = RunFiles.directory(evidenceRoot.resolve("cold-off"));
                        JsonObject source = closedEcuSource(before.id());
                        long anchorMs = boundaryBootMs(source, "capture_boottime_ns");
                        monitor[0].endOff(anchorMs);
                        window[0] = false;
                        monitor[0].close();
                        monitor[0] = null;
                        Path terminal = nativeTerminal(currentEvidence[0], before.id(), anchorMs);

                        JsonObject expectedCan = new JsonObject();
                        expectedCan.add("anchor", source);
                        expectedCan.addProperty("native_terminal_boottime_ms", text(RunFiles.read(terminal), "sampledBootMs"));
                        JsonObject intent = new JsonObject();
                        intent.add("expectedBoot", expectedBoot);
                        intent.add("expectedCan", expectedCan);
                        intent.addProperty("nativeTerminalPage", terminal.toString());
                        Path intentPath = directory.resolve("intent-before-dispatch.json");
                        RunFiles.write(intentPath, intent);
                        try (ProductRun.Operation operation = reserve(ProductRun.Kind.REBOOT, "fev-closed-ecu-cold-off")) {
                            operation.observePriorBoot(before.id());
                            operation.dispatch();
                            JsonObject restart=owned.restartBoth(expectedBoot,expectedCan);Path restartPath=directory.resolve("restart.json");RunFiles.write(restartPath,restart);String fresh=text(object(restart,"after"),"android_boot_id");androidPid=integer(object(restart,"after"),"android_pid");if(androidPid<1||!uuid(fresh)||fresh.equals(before.id()))throw new IOException("cold OFF has no new actual Android identity");
                            CliProcess.Control control=new CliProcess.Control(System.nanoTime()+Duration.ofMinutes(3).toNanos(),null);try{verifyNativeBoot(fresh,control,restart);}finally{control.close();}BootData actual=boot(null);if(!fresh.equals(actual.id())||!actual.complete()||!actual.enforcing())throw new IOException("cold OFF did not reach new ready Enforcing source");requireHealthy();
                            JsonObject first=owned.coldCanBoundaryReceipt();JsonObject after=closedEcuSource(fresh);for(String field:List.of("boot_uuid","session","kernel_open_seq","first_mode","first_commit_boottime_ns"))if(!first.get(field).equals(after.get(field)))throw new IOException("new kernel first-history identity changed before native RX resumed: "+field);if(new java.math.BigInteger(text(after,"epoch")).compareTo(new java.math.BigInteger(text(first,"epoch")))<0)throw new IOException("new kernel epoch regressed");openMonitor();monitor[0].beginOffAfterCold(after);window[0]=true;
                            Path afterPath=directory.resolve("new-kernel-before-native-resume.json");RunFiles.write(afterPath,after);operation.complete(intentPath,terminal,restartPath,afterPath);return;
                        }
                    }
                    fevColdReboot();
                }
                public ru.voboost.voyemu.ForcedEvProgram.Feedback feedback()throws Exception {return forcedEvFeedback(expectedGeneration);}
                public ru.voboost.voyemu.ForcedEvProgram.State observe()throws Exception {return fevObserve();}
                public void beginContinuousOff()throws Exception {openMonitor();monitor[0].beginOff();window[0]=true;}
                public Path requireContinuousOff()throws Exception {monitor[0].requireOff(fevSampleBootMs());return currentEvidence[0];}
                public Path finishContinuousOff()throws Exception {monitor[0].endOff(fevSampleBootMs());window[0]=false;return currentEvidence[0];}
            });
}catch(Exception|Error failure){if(monitor[0]!=null){try{monitor[0].close();}catch(Exception close){failure.addSuppressed(close);}monitor[0]=null;}throw failure;}
        finally{if(monitor[0]!=null)monitor[0].close();}
    }
    private record PwArtwork(ru.voboost.tests.ui.PedestrianWarningPixels.Artwork artwork,Path rgba,int width,int height) {}
    private List<PwArtwork> pwArtwork(Path directory)throws Exception {
        if(originalPwSystem==null||voyemuRepository==null)throw new IOException("original QNX PW artwork inventory was not bound to this actual Voyemu/profile");
        Path tools=Path.of("../voboost-agents/build/classes/java/i18nTools").toRealPath();if(!Files.isRegularFile(tools.resolve("ru/voboost/qnx/QnxPwArtwork.class")))throw new IOException("production QNX artwork extractor classes unavailable");
        Files.createDirectory(directory);Path log=work.resolve("pw-artwork-extractor-"+(++counter)+".log");var result=ru.voboost.tests.HostProcess.run(log,List.of(Path.of(System.getProperty("java.home"),"bin/java").toString(),"-cp",tools.toString(),"ru.voboost.qnx.QnxPwArtwork",originalPwSystem.toString(),directory.toString()),60000);if(result.exit()!=0)throw new IOException("original QNX PW extraction failed; evidence retained at "+log);
        List<PwArtwork> output=new ArrayList<>();for(String line:Files.readAllLines(log)) {
            int split=line.indexOf(" -> "),last=line.lastIndexOf(" -> ");if(split<1||last<=split)continue;String uri=line.substring(0,split);Path png=Path.of(line.substring(last+4));if(!png.isAbsolute()||!directory.equals(png.getParent())||!Files.isRegularFile(png)||Files.size(png)>262144)throw new IOException("artwork extractor escaped its current owned directory");
            byte[] header;try(var in=Files.newInputStream(png)){header=in.readNBytes(24);}if(header.length!=24)throw new IOException("original artwork PNG dimensions unavailable");var dimensions=java.nio.ByteBuffer.wrap(header,16,8);int width=dimensions.getInt(),height=dimensions.getInt();var image=ru.voboost.voyemu.PixelImage.read(png,width,height);output.add(new PwArtwork(new ru.voboost.tests.ui.PedestrianWarningPixels.Artwork(uri,image),png,width,height));
        }
        if(output.isEmpty()||output.size()>2)throw new IOException("bounded original normal/white PW artwork inventory required");return List.copyOf(output);
    }
    private static boolean positiveUnsigned(JsonObject object,String key){try{JsonElement value=object.get(key);if(value==null||!value.isJsonPrimitive())return false;String number=value.getAsString();return number.matches("[1-9][0-9]{0,19}")&&new java.math.BigInteger(number).bitLength()<=64;}catch(RuntimeException malformed){return false;}}
    private record PwContext(String generation,String theme) {}
    private PwContext pwContext(String expectedBoot,boolean enabled)throws Exception { return pwSourceContext(expectedBoot, enabled); }
    private PwContext pwSourceContext(String expectedBoot, Boolean enabled) throws Exception {
        return pwSourceContext(expectedBoot, enabled, null);
    }
    private PwContext pwSourceContext(String expectedBoot, Boolean enabled, CliProcess.Control control) throws Exception {
        return pwSourceContext(expectedBoot, enabled, control, false);
    }
    private PwContext pwSourceContext(String expectedBoot, Boolean enabled, CliProcess.Control control, boolean outerBootFence) throws Exception {
        JsonObject initial = pwSourceStatus("pw-context-boundary", control);
        requireOwnedStatus(initial);
        String generation = PwSignalWait.generation(object(object(initial,"deliveries"),"qnx").get("generation"));
        PwContext[] accepted = new PwContext[1];
        PwSignalWait.await(new PwSignalWait.Port() {
            public long now() { return System.nanoTime(); }
            public void pause(long nanos)throws InterruptedException { java.util.concurrent.TimeUnit.NANOSECONDS.sleep(nanos); }
            public PwSignalWait.Observation read()throws Exception {
                if (!outerBootFence) {
                    BootData actual = boot(control);
                    if (!expectedBoot.equals(actual.id()) || !actual.complete() || !actual.enforcing())
                        throw new IOException("PW source crossed its Android boot/policy");
                }
                JsonObject current = pwSourceStatus("pw-rendered-context", control);
                requireOwnedStatus(current);
                JsonObject qnx=object(object(current,"deliveries"),"qnx"),theme=object(qnx,"theme");
                String source=PwSignalWait.generation(qnx.get("generation")),themeStatus=text(theme,"status");
                boolean applied="guest_acknowledged".equals(themeStatus)&&truth(theme,"guest_acknowledged")&&"original-setting-request-ACK".equals(text(theme,"receipt_source"))
                    ||"already_selected".equals(themeStatus)&&falseValue(theme,"guest_acknowledged")&&"original-setting-getter".equals(text(theme,"receipt_source"));
                if(!source.matches("[1-9][0-9]*")||!applied||integer(theme,"theme_id")<1||integer(theme,"theme_id")>6
                        ||!positiveUnsigned(theme,"producer_address")||integer(theme,"sequence")<1)
                    throw new IOException("actual applied QNX theme/source required");
                JsonObject state=object(current,"state"),vehicle=object(qnx,"vehicle");
                String delivery=text(vehicle,"status");
                if(!Set.of("guest_acknowledged","queued").contains(delivery))
                    throw new IOException("PW QNX delivery observation is unavailable");
                accepted[0]=new PwContext(source,"theme:"+integer(theme,"theme_id"));
                return new PwSignalWait.Observation(expectedBoot, source,
                    "guest_acknowledged".equals(delivery), integer(state,"sequence"), integer(vehicle,"source_sequence"));
            }
        },expectedBoot,generation);
        if(enabled!=null) {
            var runtime=observeRuntime(text(status(),"activeGeneration"));
            if(runtime.desired("vehicle-drive-pedestrian-warning")!=enabled)
                throw new IOException("PW rendered phase differs from root desired authority");
        }
        return accepted[0];
    }
    private JsonObject pwSourceStatus(String phase, CliProcess.Control control) throws Exception {
        var read = client.status(id(), profile, owned.session());
        return control == null ? nativeRead(read, phase) : nativeRead(read, phase, control.remaining());
    }
    private long clusterSequence(Path capture)throws Exception {
        JsonObject manifest=RunFiles.read(capture.resolve("manifest.json"));long result=0;for(JsonElement entry:manifest.getAsJsonArray("frames")){JsonObject frame=entry.getAsJsonObject();if("cluster".equals(text(frame,"screen"))){if(result!=0)throw new IOException("duplicate cluster source");String sequence=text(frame,"sequence");if(!sequence.matches("[1-9][0-9]{0,18}"))throw new IOException("bounded positive cluster sequence required");result=Long.parseLong(sequence);}}
        if(result<1)throw new IOException("original cluster sequence unavailable");return result;
    }
    private JsonObject pwPhase(Path destination,String boot,PwContext expected,boolean enabled,long afterSequence)throws Exception {
        if(afterSequence<1)throw new IOException("PW accepted phase needs a positive source boundary");PwContext before=pwContext(boot,enabled);if(!before.equals(expected))throw new IOException("PW theme/generation changed before capture");var canBefore=vehicleCan(boot);if(canBefore.pw()!=1)throw new IOException("PW icon counterfactual requires actual native VSP1");JsonObject capture=owned.captureCluster(destination,afterSequence);if(!truth(capture,"accepted"))throw new IOException("PW source capture was not accepted");
        var can=vehicleCan(boot);can.requireNext(canBefore);if(can.pw()!=1||!expected.equals(pwContext(boot,enabled)))throw new IOException("PW capture crossed constant CAN/theme/root authority");ru.voboost.tests.ui.PedestrianWarningPixels.readCapture(destination,owned.session().id(),expected.generation(),expected.theme(),can.pw());JsonObject phase=new JsonObject();phase.addProperty("captureDir",destination.toString());phase.add("can",can.receipt());phase.addProperty("agentEnabled",enabled);phase.addProperty("soundEnable",ru.voboost.tests.ui.PedestrianWarningPixels.ORIGINAL_OFF_SOUND_ENABLE);return phase;
    }
    private JsonObject pwStockBaseline,pwInstallBridge;
    private Integer pwFixtureOriginal;
    private JsonObject pwOwner() throws Exception {
        if(productRun==null||!productRun.owns(owned))throw new IOException("PW proof requires the current exclusive ProductRun");
        JsonObject run=productRun.identity(),source=nativeRead(client.status(id(),profile,owned.session()),"pw-owner");requireOwnedStatus(source);
        JsonObject owner=new JsonObject();owner.addProperty("runId",text(run,"runId"));owner.addProperty("machine",text(run,"machine"));
        owner.addProperty("work",text(run,"work"));owner.addProperty("sessionId",owned.session().id());owner.addProperty("machineSeed",text(source,"machine_seed"));return owner;
    }
    /** Acquires the unmodified OEM OFF/ON/OFF raster before any install request. */
    public Path capturePwStockBaseline() throws Exception {
        if (pwStockBaseline != null) throw new IOException("PW stock baseline already acquired");
        Path absence = requireAbsentEvidence();
        BootData initial = boot(null);
        var context = pwSourceContext(initial.id(), null);
        var region = new ru.voboost.voyemu.VoyemuClient.ClusterRegion(841,102,46,46);
        Path captures = Files.createDirectory(voyemuRepository.resolve(".local/build/tmp/pw-stock-" + UUID.randomUUID()));
        owned.captureCluster(captures.resolve("boundary"),1,region);
        long sequence = clusterSequence(captures.resolve("boundary"));
        List<PwArtwork> artwork = pwArtwork(work.resolve("pw-stock-artwork-" + (++counter)));
        JsonObject phases = new JsonObject();
        for (String name : List.of("visible","absent","restored")) {
            int wanted = name.equals("absent") ? 2 : 1;
            Path input = pwFixtureInput(pwStockSelect(wanted), wanted, initial.id());
            var before = vehicleCan(initial.id());
            if(before.pw()!=wanted || !context.equals(pwSourceContext(initial.id(),null)))
                throw new IOException("stock PW CAN/source/theme differs before capture");
            Path directory = awaitPwStockFrame(captures.resolve(name), sequence, region, artwork,
                    context, initial.id(), wanted);
            var after = vehicleCan(initial.id()); after.requireNext(before);
            if(after.pw()!=wanted || !context.equals(pwSourceContext(initial.id(),null)))
                throw new IOException("stock PW capture crossed CAN/source/theme");
            ru.voboost.tests.ui.PedestrianWarningPixels.readCapture(directory,owned.session().id(),context.generation(),context.theme(),wanted,region);
            sequence = clusterSequence(directory);
            JsonObject phase = new JsonObject(); phase.addProperty("captureDir",directory.toString());
            phase.add("can",after.receipt()); phase.addProperty("inputEvidence",input.toString()); phases.add(name,phase);
        }
        JsonObject proof = new JsonObject(); proof.addProperty("sessionId",owned.session().id());
        proof.addProperty("androidBootId",initial.id()); proof.addProperty("qnxGeneration",context.generation());
        proof.addProperty("theme",context.theme()); proof.addProperty("absenceEvidence",absence.toString()); proof.add("phases",phases);
        IOException mismatch=null;
        for (PwArtwork candidate : artwork) {
            try {
                var visible=ru.voboost.tests.ui.PedestrianWarningPixels.readCapture(Path.of(text(object(phases,"visible"),"captureDir")),owned.session().id(),context.generation(),context.theme(),1,region);
                var hidden=ru.voboost.tests.ui.PedestrianWarningPixels.readCapture(Path.of(text(object(phases,"absent"),"captureDir")),owned.session().id(),context.generation(),context.theme(),2,region);
                var restored=ru.voboost.tests.ui.PedestrianWarningPixels.readCapture(Path.of(text(object(phases,"restored"),"captureDir")),owned.session().id(),context.generation(),context.theme(),1,region);
                ru.voboost.tests.ui.PedestrianWarningPixels.compareRegions(candidate.artwork().image(),visible,hidden,restored,12);
                mismatch=null;break;
            }catch(IOException invalid){mismatch=invalid;}
        }
        if (mismatch != null) {
            owned.captureCluster(captures.resolve("diagnostic-restored"), sequence);
            throw new IOException("stock OFF/ON/OFF glyph baseline failed before install; diagnostic: " + captures, mismatch);
        }
        requireAbsentEvidence();
        proof.add("owner",pwOwner());
        pwStockBaseline = proof;pwInstallBridge=null;
        Path file = work.resolve("pw-stock-baseline-"+(++counter)+".json"); RunFiles.write(file,proof); return file;
    }
    private Path awaitPwStockFrame(Path directory, long afterSequence,
            ru.voboost.voyemu.VoyemuClient.ClusterRegion region, List<PwArtwork> artwork,
            PwContext context, String boot, int wanted) throws Exception {
        Files.createDirectory(directory);
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        for (int attempt = 0; System.nanoTime() < deadline; attempt++) {
            Path frame = directory.resolve("frame-" + attempt);
            JsonObject receipt = owned.captureCluster(frame, afterSequence, region);
            if (!truth(receipt, "accepted")) throw new IOException("stock PW capture rejected");
            var observed = ru.voboost.tests.ui.PedestrianWarningPixels.readCapture(frame,
                    owned.session().id(), context.generation(), context.theme(), wanted, region);
            afterSequence = clusterSequence(frame);
            for (PwArtwork candidate : artwork) {
                if (ru.voboost.tests.ui.PedestrianWarningPixels.matchesRegion(
                        candidate.artwork().image(), observed, wanted == 1, 12)) return frame;
            }
            Thread.sleep(100);
        }
        throw new IOException("stock PW raster did not reach requested state: " + directory);
    }

    private JsonObject setPwFixture(boolean value, String boot) throws Exception {
        JsonObject receipt = owned.setPwSoundFixture(value, boot);
        if (pwFixtureOriginal == null) pwFixtureOriginal = Math.toIntExact(integer(receipt, "original_value"));
        return receipt;
    }

    /** OEM controls exercise Android; the existing MCU fixture supplies the independent QNX input. */
    private Path pwFixtureInput(Path uiInput, int wanted, String boot) throws Exception {
        JsonObject stimulus = new JsonObject();
        stimulus.addProperty("kind", "pw-ui-and-virtual-mcu");
        stimulus.addProperty("uiInput", uiInput.toString());
        stimulus.add("mcuInput", setPwFixture(wanted == 1, boot));
        stimulus.addProperty("physicalCanToMcuVerified", false);
        Path file = work.resolve("pw-stimulus-" + (++counter) + ".json");
        RunFiles.write(file, stimulus);
        return file;
    }
    private Path pwStockSelect(int wanted) throws Exception {
        BootData initial = boot(null);
        try(ProductRun.Operation operation=launcherOperation("pw-stock-select-"+wanted)) {
            operation.observePriorBoot(initial.id()); operation.dispatch();
            VehicleFrame frame=openVehicle(ru.voboost.voyemu.VehicleUi.Feature.PEDESTRIAN_WARNING,initial.id());
            boolean on=wanted==2;
            if(frame.control().checked()!=on) {
                if(!frame.control().enabled()) throw new IOException("stock PW control disabled");
                tapNode(frame.tree(),frame.control().nodeId());
            }
            long deadline=System.nanoTime()+Duration.ofSeconds(15).toNanos();
            while(System.nanoTime()<deadline) {
                VehicleFrame current=openVehicle(ru.voboost.voyemu.VehicleUi.Feature.PEDESTRIAN_WARNING,initial.id());
                var can=vehicleCan(initial.id());
                if(current.control().checked()==on && can.pw()==wanted) {
                    JsonObject proof=new JsonObject();proof.add("ui",current.tree());proof.add("can",can.receipt());
                    Path file=work.resolve("pw-stock-input-"+(++counter)+".json");RunFiles.write(file,proof);operation.complete(file);return file;
                }
                Thread.sleep(100);
            }
            throw new IOException("stock OEM PW selection lacks actual CAN readback");
        }
    }
    public Path awaitPwDeliveryAndStartCluster(JsonObject candidateCatalog) throws Exception {
        Path vehicleSettings = vehicleSelect(ru.voboost.voyemu.VehicleUi.Feature.PEDESTRIAN_WARNING, false);
        return awaitPwDeliveryAndStartCluster(candidateCatalog, vehicleSettings);
    }

    private Path awaitPwDeliveryAndStartCluster(JsonObject candidateCatalog, Path vehicleSettings) throws Exception {
        String generation = text(status(), "activeGeneration");
        var initial = observeRuntime(generation);
        JsonObject expected = ru.voboost.tests.agents.AgentSettings.snapshot(candidateCatalog, initial);
        boolean needsStartup;
        try {
            needsStartup = awaitObservation("PW desired delivery before QNX startup", Duration.ofMinutes(4),
                    (scope, deadline) -> ru.voboost.tests.agents.ClusterObservation.pwNeedsStartup(
                            observeRuntime(generation, scope, initial.publisher()), candidateCatalog, expected));
        } catch (IOException failure) {
            failure.addSuppressed(new IOException("diagnostic log archive retained at "
                    + captureDiagnosticLogs("pw-first-delivery")));
            throw failure;
        }
        JsonObject proof = new JsonObject();
        if (vehicleSettings != null) proof.addProperty("vehicleSettings", vehicleSettings.toString());
        proof.addProperty("deliveredRuntime", lastRuntimeOfficial.toString());
        proof.addProperty("needsStartup", needsStartup);
        Path file = work.resolve("pw-first-delivery-" + (++counter) + ".json");
        RunFiles.write(file, proof);
        if (needsStartup) {
            proof.addProperty("restart", restartBothAndVerify().toString());
            RunFiles.write(file, proof);
        }
        return file;
    }

    private record PwApplied(ru.voboost.tests.agents.ClusterObservation observation, Path evidence) {}

    private PwApplied requirePwClusterApplied(JsonObject candidateCatalog) throws Exception {
        final String agent = "vehicle-drive-pedestrian-warning";
        BootData boot = boot(null);
        String generation = text(status(), "activeGeneration");
        var initial = observeRuntime(generation);
        if (!initial.desired(agent)) throw new IOException("PW agent must remain enabled during its visual test");
        JsonObject expected = ru.voboost.tests.agents.AgentSettings.snapshot(candidateCatalog, initial);
        ru.voboost.tests.agents.ClusterObservation observation = null;
        JsonObject last = initial.runtime();
        IOException pending = null;
        long deadline = System.nanoTime() + Duration.ofMinutes(6).toNanos();
        while (System.nanoTime() < deadline) {
            var current = observeRuntime(generation);
            last = current.runtime();
            try {
                observation = ru.voboost.tests.agents.ClusterObservation.readPwStartup(current, candidateCatalog, expected);
                observation.requireCurrentPw(current);
                JsonObject proof = observation.evidence();
                proof.addProperty("androidBootId", boot.id());
                Path file = work.resolve("pw-cluster-applied-" + (++counter) + ".json");
                RunFiles.write(file, proof);
                return new PwApplied(observation, file);
            } catch (ru.voboost.tests.agents.ClusterObservation.Pending transition) {
                pending = transition;
            }
            Thread.sleep(250);
        }
        JsonObject proof = new JsonObject();
        proof.addProperty("diagnosticLogs", captureDiagnosticLogs("pw-cluster-not-ready").toString());
        proof.addProperty("androidBootId", boot.id());
        proof.add("runtime", last);
        proof.addProperty("reason", pending == null ? "current native applied state unavailable" : pending.getMessage());
        Path file = work.resolve("pw-cluster-not-ready-" + (++counter) + ".json");
        RunFiles.write(file, proof);
        throw new IOException("PW light module is not confirmed applied; screenshot test was not started: " + file, pending);
    }

    private ru.voboost.tests.agents.ClusterObservation pwNativePhase(JsonObject catalog, JsonObject expected,
            ru.voboost.tests.agents.ClusterObservation prior, boolean fresh) throws Exception {
        Duration timeout = fresh ? Duration.ofMinutes(4) : Duration.ofSeconds(15);
        long deadline = System.nanoTime() + timeout.toNanos();
        ru.voboost.tests.agents.ClusterObservation.Pending pending = null;
        while (System.nanoTime() < deadline) {
            var current = observeRuntime(text(status(), "activeGeneration"));
            try {
                var observation = fresh ? prior.advance(current, catalog, expected) : prior.confirmPw(current, catalog, expected);
                observation.requireCurrent(current, "vehicle-drive-pedestrian-warning", "light");
                return observation;
            } catch (ru.voboost.tests.agents.ClusterObservation.Pending waiting) {
                pending = waiting;
                Thread.sleep(500);
            }
        }
        throw new IOException("current applied PW/light observation did not arrive", pending);
    }
    private JsonObject pwEpochBridge()throws Exception {
        JsonObject bridge=pwInstallBridge.deepCopy();JsonArray restarts=new JsonArray();
        Path root=Path.of(text(productRun.identity(),"work")),installed=Path.of(text(bridge,"operationPath"));
        try(var directories=Files.list(root.resolve("operations"))) {
            for(Path directory:directories.sorted().toList()) {
                Path file=directory.resolve("operation.json");if(file.compareTo(installed)<=0)continue;
                JsonObject operation=RunFiles.read(file);
                if(!"REBOOT".equals(text(operation,"kind")))continue;
                if(!"verified".equals(text(operation,"state")))throw new IOException("PW epoch bridge has an unverified reboot");
                JsonObject transition=null;
                for(JsonElement value:operation.getAsJsonArray("evidence")) {
                    Path receiptPath=root.resolve(value.getAsString());if(!receiptPath.toString().endsWith(".json"))continue;
                    JsonObject receipt=RunFiles.read(receiptPath);String scope=text(receipt,"scope");
                    if(!Set.of("guest_boot_changed","android_qnx_cold_boot_changed").contains(scope))continue;
                    if(transition!=null)throw new IOException("ambiguous PW epoch restart evidence");
                    transition=new JsonObject();transition.add("operation",operation);transition.add("receipt",receipt);
                    transition.addProperty("operationPath",file.toString());transition.addProperty("receiptPath",receiptPath.toString());
                }
                if(transition==null)throw new IOException("verified PW reboot lacks its native restart receipt");
                restarts.add(transition);
            }
        }
        bridge.add("restarts",restarts);return bridge;
    }
    private Path verifyPwRendered(JsonObject candidateCatalog) throws Exception {
        final String agent = "vehicle-drive-pedestrian-warning";
        BootData before = boot(null);
        var initialCan = vehicleCan(before.id());
        if (!observeRuntime(text(status(), "activeGeneration")).desired(agent))
            throw new IOException("PW visual test requires the agent enabled throughout");
        Path evidence = RunFiles.directory(work.resolve("pw-rendered-" + (++counter)));
        List<PwArtwork> artwork = pwArtwork(evidence.resolve("artwork"));
        if (artwork.isEmpty()) throw new IOException("original PW artwork is missing");
        if(pwStockBaseline==null||pwInstallBridge==null) throw new IOException("fresh stock baseline and verified installer epoch bridge required");
        var initial = observeRuntime(text(status(), "activeGeneration"));
        JsonObject expected = ru.voboost.tests.agents.AgentSettings.snapshot(candidateCatalog, initial);
        PwApplied applied = requirePwClusterApplied(candidateCatalog);
        var nativeState = applied.observation();
        vehicleSelect(ru.voboost.voyemu.VehicleUi.Feature.PEDESTRIAN_WARNING, false);
        setPwFixture(true, before.id());
        PwContext context = pwContext(before.id(), true);
        var region = new ru.voboost.voyemu.VoyemuClient.ClusterRegion(841, 102, 46, 46);
        Path root = voyemuRepository.resolve(".local/build/tmp");
        Files.createDirectories(root);
        Path captures = Files.createDirectory(root.resolve("voboost-pw-oem-" + owned.session().id() + "-" + UUID.randomUUID()),
                java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rwx------")));
        owned.captureCluster(captures.resolve("boundary"), 1, region);
        long sequence = clusterSequence(captures.resolve("boundary"));
        JsonObject cycles = new JsonObject();
        for (String control : List.of("settings", "drawer")) {
            JsonObject phases = new JsonObject();
            for (String phase : List.of("off", "on", "off-restored")) {
                int wanted = phase.equals("on") ? 2 : 1;
                Path input = control.equals("settings")
                        ? vehicleSelect(ru.voboost.voyemu.VehicleUi.Feature.PEDESTRIAN_WARNING, wanted == 2)
                        : pwDrawerSelect(wanted);
                input = pwFixtureInput(input, wanted, before.id());
                nativeState = pwNativePhase(candidateCatalog, expected, nativeState, false);
                var canBefore = vehicleCan(before.id());
                if (canBefore.pw() != wanted || !context.equals(pwContext(before.id(), true)))
                    throw new IOException("PW UI/CAN/theme changed before region capture");
                JsonObject restoreRootBefore=wanted==2?rootPedestrianState(before.id()):null;
                if(wanted==2&&ru.voboost.voyemu.VehicleArmed.readPedestrian(restoreRootBefore,before.id()))throw new IOException("PW ON root intent is still armed before capture");
                Path directory = captures.resolve(control + "-" + phase);
                JsonObject receipt = owned.captureCluster(directory, sequence, region);
                if (!truth(receipt, "accepted")) throw new IOException("PW region capture rejected");
                var canAfter = vehicleCan(before.id());
                canAfter.requireNext(canBefore);
                if (canAfter.pw() != wanted || !context.equals(pwContext(before.id(), true)))
                    throw new IOException("PW region capture crossed its observed state");
                boolean finalFrame = control.equals("drawer") && phase.equals("off-restored");
                // OEM switches leave the signed intent unchanged. Close the capture series with a new reply.
                nativeState = pwNativePhase(candidateCatalog, expected, nativeState, finalFrame);
                ru.voboost.tests.ui.PedestrianWarningPixels.readCapture(directory, owned.session().id(),
                        context.generation(), context.theme(), wanted, region);
                sequence = clusterSequence(directory);
                var logs = collectLogs(before.id());
                JsonObject state = new JsonObject();
                state.addProperty("captureDir", directory.toString());
                state.addProperty("agentEnabled", true);
                state.addProperty("actualVsp", wanted);
                state.addProperty("inputEvidence", input.toString());
                state.addProperty("logArchive", logs.archive().toString());
                if(wanted==2) {
                    JsonObject restoreRootAfter=rootPedestrianState(before.id());
                    if(ru.voboost.voyemu.VehicleArmed.readPedestrian(restoreRootAfter,before.id()))throw new IOException("PW ON root intent is still armed after capture");
                    state.add("restoreRootBefore",restoreRootBefore);state.add("restoreRootAfter",restoreRootAfter);
                }
                state.add("native", nativeState.evidence());
                state.add("can", canAfter.receipt());
                phases.add(phase, state);
                RunFiles.write(evidence.resolve(control + "-" + phase + ".json"), state);
            }
            cycles.add(control, phases);
        }
        JsonObject proof = new JsonObject();
        proof.addProperty("schemaVersion", 1);
        proof.addProperty("kind", "pw-saved-setting-rendered-regions");
        proof.addProperty("stimulusSource", "virtual-mcu");
        proof.add("stockBaseline",pwStockBaseline.deepCopy());
        proof.add("owner",pwOwner());proof.add("installBridge",pwEpochBridge());
        proof.addProperty("sessionId", owned.session().id());
        proof.addProperty("androidBootId", before.id());
        proof.addProperty("qnxGeneration", context.generation());
        proof.addProperty("theme", context.theme());
        proof.addProperty("initialNativeEvidence", applied.evidence().toString());
        JsonObject rect = new JsonObject();
        rect.addProperty("x", region.x()); rect.addProperty("y", region.y());
        rect.addProperty("width", region.width()); rect.addProperty("height", region.height());
        proof.add("region", rect);
        proof.add("cycles", cycles);
        IOException mismatch = null;
        for (PwArtwork candidate : artwork) {
            JsonObject source = new JsonObject();
            source.addProperty("textureUri", candidate.artwork().textureUri());
            source.addProperty("rgbaPng", candidate.rgba().toString());
            source.addProperty("width", candidate.width()); source.addProperty("height", candidate.height());
            proof.add("artwork", source);
            RunFiles.write(evidence.resolve("attempted-proof.json"), proof);
            try { ru.voboost.tests.ui.PedestrianWarningPixels.readProof(proof); mismatch = null; break; }
            catch (IOException invalid) { mismatch = invalid; }
        }
        if (mismatch != null) throw new IOException("PW stock glyph / enabled-agent absent glyph comparison failed", mismatch);
        if (!observeRuntime(text(status(), "activeGeneration")).desired(agent))
            throw new IOException("PW agent was disabled during its visual test");
        if (initialCan.pw() != 1) vehicleSelect(ru.voboost.voyemu.VehicleUi.Feature.PEDESTRIAN_WARNING, true);
        if (pwFixtureOriginal != null) {
            RunFiles.write(evidence.resolve("fixture-restored.json"),
                    owned.setPwSoundFixture(pwFixtureOriginal == 1, before.id()));
            pwFixtureOriginal = null;
        }
        Path file = evidence.resolve("proof.json");
        RunFiles.write(file, proof);
        return file;
    }
    /** Retain PW while changing only FEV configuration, matching the independent manual failure sequence. */
    private Path verifyPwEnabledRetention(JsonObject candidateCatalog)throws Exception {
        var original=observeRuntime(text(status(),"activeGeneration"));
        if(!original.desired("vehicle-drive-pedestrian-warning")||!original.desired("vehicle-drive-forced-ev"))
            throw new IOException("both vehicle agents must already be enabled for the unchanged-PW configuration experiment");
        JsonObject proof=new JsonObject();proof.addProperty("kind","pw-unchanged-enabled-warm-full-cold");proof.addProperty("complete",false);
        proof.addProperty("originalRuntime",lastRuntimeOfficial.toString());
        Path file=work.resolve("pw-enabled-retention-"+(++counter)+".json");RunFiles.write(file,proof);
        proof.addProperty("oemOn",vehicleSelect(ru.voboost.voyemu.VehicleUi.Feature.PEDESTRIAN_WARNING,true).toString());
        verifySettingsTab();proof.addProperty("fevDisabled",setAgent(candidateCatalog,"vehicle-drive-forced-ev",false).toString());
        verifySettingsTab();proof.addProperty("fevEnabled",setAgent(candidateCatalog,"vehicle-drive-forced-ev",true).toString());
        fevSelectOem(false);fevSelectOem(true);
        proof.addProperty("oemOff",vehicleSelect(ru.voboost.voyemu.VehicleUi.Feature.PEDESTRIAN_WARNING,false).toString());
        var armed=vehicleObserve(ru.voboost.voyemu.VehicleUi.Feature.PEDESTRIAN_WARNING);
        requirePwRetained("beforeWarm",armed,proof,file);
        rebootAndVerify();proof.add("warmReboot",owned.rebootReceipt());
        // Start the genuine OEM scene before requiring its process endpoint after reboot.
        var warm=vehicleObserve(ru.voboost.voyemu.VehicleUi.Feature.PEDESTRIAN_WARNING);
        awaitVehicleInput("vehicle-drive-pedestrian-warning",ru.voboost.voyemu.VehicleUi.OWNER,warm.can().boot());
        var firstWarmCan=warm.can();
        warm=vehicleObserve(ru.voboost.voyemu.VehicleUi.Feature.PEDESTRIAN_WARNING);
        warm.can().requireNext(firstWarmCan);
        warm.can().requireNewBoot(armed.can());requirePwRetained("afterWarm",warm,proof,file);
        proof.addProperty("fullColdReboot",restartBothAndVerify().toString());
        var cold=vehicleObserve(ru.voboost.voyemu.VehicleUi.Feature.PEDESTRIAN_WARNING);
        awaitVehicleInput("vehicle-drive-pedestrian-warning",ru.voboost.voyemu.VehicleUi.OWNER,cold.can().boot());
        var firstColdCan=cold.can();
        cold=vehicleObserve(ru.voboost.voyemu.VehicleUi.Feature.PEDESTRIAN_WARNING);
        cold.can().requireNext(firstColdCan);
        cold.can().requireNewBoot(warm.can());requirePwRetained("afterFullCold",cold,proof,file);
        proof.addProperty("complete",true);RunFiles.write(file,proof);return file;
    }
    private void requirePwRetained(String phase,ru.voboost.voyemu.VehiclePersistence.State observed,JsonObject proof,Path file)throws Exception {
        var current=observeRuntime(text(status(),"activeGeneration"));
        boolean armed=ru.voboost.voyemu.VehicleArmed.readPedestrian(current);
        JsonObject row=new JsonObject();row.addProperty("uiCan",observed.evidence().toString());row.addProperty("runtime",lastRuntimeOfficial.toString());
        row.addProperty("armed",armed);row.addProperty("enabled",current.desired("vehicle-drive-pedestrian-warning"));proof.add(phase,row);RunFiles.write(file,proof);
        if(!observed.can().boot().equals(text(object(current.runtime(),"publisher"),"bootId"))||!armed||!current.desired("vehicle-drive-pedestrian-warning"))
            throw new IOException("PW retained native intent/configuration differs at "+phase);
        observed.require(false,false);
    }

    /** Enabled-agent ON/consent and explicit OFF through the real VehicleSetting switch. */
    public Path verifyForcedEvOem(JsonObject candidateCatalog) throws Exception {
        if (productRun == null || !productRun.owns(owned))
            throw new IOException("Current exclusive vehicle owner required");
        String agent = "vehicle-drive-forced-ev";
        var original = observeRuntime(text(status(), "activeGeneration"));
        boolean originallyEnabled = original.desired(agent);
        JsonObject selection = new JsonObject();
        selection.addProperty(agent, originallyEnabled);
        var feature = ru.voboost.voyemu.VehicleUi.Feature.FORCED_EV;
        var initial = vehicleObserve(feature);
        if (initial.checked() || initial.can().fev() != 2)
            throw new IOException("FEV VehicleSetting fixture requires Electric mode with Force EV off");

        JsonObject proof = new JsonObject();
        proof.addProperty("initialOem", initial.evidence().toString());
        proof.addProperty("originallyEnabled", originallyEnabled);
        try (AutoCloseable restore = () -> {
            if (productRun.pending() != null || Thread.currentThread().isInterrupted()) return;
            var current = vehicleObserve(feature);
            if (current.checked() && current.can().fev() == 5) current = fevSelectOem(false, true);
            if (current.checked() || current.can().fev() != 2)
                throw new IOException("Initial Electric OFF was not restored");
            restoreAgentSelection(candidateCatalog, selection);
            var restored = vehicleObserve(feature);
            if (restored.checked() || restored.can().fev() != 2
                    || observeRuntime(text(status(), "activeGeneration")).desired(agent) != originallyEnabled)
                throw new IOException("Initial FEV selection and agent setting were not restored");
            proof.addProperty("restoredOem", restored.evidence().toString());
            proof.addProperty("restoredRuntime", lastRuntimeOfficial.toString());
        }) {
            if (!originallyEnabled) {
                verifySettingsTab();
                proof.addProperty("enabled", setAgent(candidateCatalog, agent, true).toString());
            }
            var baseline = observeForcedEvOem(false, initial.can(), false);
            proof.addProperty("enabledBaseline", baseline.evidence().toString());
            for (boolean wanted : List.of(true, false)) {
                JsonObject phase = new JsonObject();
                var selected = fevSelectOem(wanted, true);
                phase.addProperty("selection", selected.evidence().toString());
                phase.addProperty("sleepWake", fevWarmCycle().toString());
                var warm = observeForcedEvOem(wanted, selected.can(), false);
                phase.addProperty("afterWake", warm.evidence().toString());
                fevColdReboot();
                phase.add("reboot", owned.rebootReceipt());
                var cold = observeForcedEvOem(wanted, warm.can(), true);
                phase.addProperty("afterReboot", cold.evidence().toString());
                phase.addProperty("firstNativeFrames", captureFirstNativeFrames(cold.can().boot()).toString());
                proof.add(wanted ? "on" : "off", phase);
            }
        }
        Path file = work.resolve("fev-vehicle-setting-" + (++counter) + ".json");
        RunFiles.write(file, proof);
        return file;
    }

    private Path captureFirstNativeFrames(String expectedBoot) throws Exception {
        return inReadyBoot(expectedBoot, () -> boot(null), () -> {
            Path directory = RunFiles.directory(work.resolve("native-startup-" + (++counter)));
            var result = CliProcess.run(List.of(binary.toString(), "--observe-native-can", "0", "--device", device),
                    directory.resolve("read.out"), directory.resolve("read.err"), Duration.ofSeconds(5),
                    (stream, line, control) -> null);
            JsonObject page = json(output(result));
            NativeCanJournal.read(page, java.math.BigInteger.ZERO, expectedBoot, true);
            if (!page.has("firstTargetFrames")) throw new IOException("Candidate lacks first native CAN receive diagnostics");
            Path proof = directory.resolve("first-target-frames.json");
            RunFiles.write(proof, page);
            return proof;
        });
    }

    private ru.voboost.voyemu.VehiclePersistence.State observeForcedEvOem(boolean wanted,
            ru.voboost.voyemu.VehicleCan.Snapshot prior, boolean restarted) throws Exception {
        var feature = ru.voboost.voyemu.VehicleUi.Feature.FORCED_EV;
        var initial = vehicleObserve(feature);
        String boot = initial.can().boot();
        var ready = awaitVehicleInput("vehicle-drive-forced-ev", ru.voboost.voyemu.VehicleUi.OWNER, boot);
        Path inputReady = lastRuntimeOfficial;
        Path file = work.resolve("fev-oem-retained-" + (++counter) + ".json");
        JsonObject observed = new JsonObject();
        var confirmed = awaitObservation("VehicleSetting FEV readback", Duration.ofSeconds(15), (scope, deadline) -> {
            JsonObject tree = settingsUi(boot, deadline);
            var surface = ru.voboost.voyemu.UiEvidence.surface(tree, "primary");
            var control = ru.voboost.voyemu.VehicleUi.feature(surface, feature);
            if (control.checked() != wanted || !ru.voboost.voyemu.VehicleUi.electricRadio(surface).checked())
                return null;
            var can = vehicleCan(boot, scope);
            can.requireNext(initial.can());
            if (restarted) can.requireNewBoot(prior); else can.requireNext(prior);
            if (can.fev() != (wanted ? 5 : 2)) return null;
            observed.add("ui", tree);
            observed.add("can", can.receipt());
            return can;
        });
        observed.addProperty("inputReadyEvidence", inputReady.toString());
        observed.addProperty("durableIntentEvidence", awaitForcedEvOemIntent(wanted, confirmed, ready).toString());
        RunFiles.write(file, observed);
        return new ru.voboost.voyemu.VehiclePersistence.State(confirmed, wanted, file);
    }

    /** OEM-only core coverage remains separate from the unavailable drawer/mode/RX program. */
    public Path verifyForcedEvOem(JsonObject candidateCatalog,boolean postRebootOnly)throws Exception {
        if(productRun==null||!productRun.owns(owned))throw new IOException("current exclusive vehicle owner required");
        var feature=ru.voboost.voyemu.VehicleUi.Feature.FORCED_EV;
        boolean[] enabled={false},expected={true};
        var port=new ru.voboost.voyemu.VehiclePersistence.Port() {
            public void enable(boolean value)throws Exception {verifySettingsTab();setAgent(candidateCatalog,"vehicle-drive-forced-ev",value);enabled[0]=value;}
            public void select(boolean selected)throws Exception {fevSelectOem(selected);expected[0]=selected;}
            public void reboot()throws Exception {rebootAndVerify();expected[0]=enabled[0];}
            public ru.voboost.voyemu.VehiclePersistence.State observe()throws Exception {
                var state=vehicleObserve(feature);
                if(enabled[0]&&!awaitVehicleInput("vehicle-drive-forced-ev",ru.voboost.voyemu.VehicleUi.OWNER,state.can().boot()).desired("vehicle-drive-forced-ev"))
                    throw new IOException("enabled FEV continuation lost current desired state");
                long deadline=System.nanoTime()+Duration.ofSeconds(10).toNanos();
                do {
                    var previous=state.can();state=vehicleObserve(feature);state.can().requireNext(previous);
                    if(state.checked()==expected[0]&&state.can().selected(true)==expected[0])return state;
                }while(System.nanoTime()<deadline);
                throw new IOException("observed OEM FEV retention did not settle; no feature input replay");
            }
        };
        ru.voboost.voyemu.VehiclePersistence.Proof result;
        if(postRebootOnly) {
            var current=observeRuntime(text(status(),"activeGeneration"));
            if(!current.desired("vehicle-drive-forced-ev"))throw new IOException("FEV post-reboot continuation requires current enabled intent");
            enabled[0]=true;expected[0]=true;var baseline=port.observe();baseline.require(true,true);
            result=new ru.voboost.voyemu.VehiclePersistence.Proof(List.of(baseline));
        } else result=ru.voboost.voyemu.VehiclePersistence.run(port,true);
        JsonArray states=new JsonArray();for(var state:result.states())states.add(state.evidence().toString());
        var retained=result.states().get(result.states().size()-1);JsonArray roots=new JsonArray();
        if(!fevArmed(retained.can().boot(),roots))throw new IOException("enabled FEV retained root intent missing after reboot");
        Path warmOperation=null;var warm=retained;
        if(!postRebootOnly) {
            warmOperation=fevWarmCycle();warm=port.observe();warm.can().requireNext(retained.can());warm.require(true,true);states.add(warm.evidence().toString());
            if(!fevArmed(warm.can().boot(),roots))throw new IOException("FEV root intent changed across actual sleep/wake");
        }
        fevColdReboot();var cold=port.observe();cold.can().requireNewBoot(warm.can());cold.require(true,true);states.add(cold.evidence().toString());
        if(!fevArmed(cold.can().boot(),roots))throw new IOException("FEV root intent changed across cold reboot");
        JsonObject proof=new JsonObject();proof.addProperty("caseNumber",13);proof.addProperty("semanticFunctionalStatus","PARTIAL");
        proof.addProperty("oemPersistenceStatus",postRebootOnly?"PARTIAL":"PASS");proof.addProperty("coldRetentionStatus","PASS");proof.addProperty("warmPowerStatus",postRebootOnly?"NOT_RUN: separate power fixture remains unresolved":"PASS");
        proof.addProperty("disabledResetStatus",postRebootOnly?"NOT_RUN: previous receipt retained separately":"PASS");proof.addProperty("functionalAcceptance",false);
        proof.addProperty("drawerStatus","OPEN: independent expanded drawer coverage is incomplete; modes/continuous OFF are not granted");
        proof.addProperty("reason",postRebootOnly?"Actual current enabled FEV cold retention passed; earlier disabled/reset and warm-reboot observations are separate, sleep/wake fixture and drawer remain open":"Actual OEM FEV disabled reset, enabled warm/cold retention and same-boot sleep/wake passed; the separate drawer/mode/continuous-native-RX program remains open");
        proof.add("states",states);proof.add("rootEvidence",roots);if(warmOperation!=null)proof.addProperty("warmCycle",warmOperation.toString());
        Path file=work.resolve("fev-oem-persistence-"+(++counter)+".json");RunFiles.write(file,proof);return file;
    }

    public void prepareAgentPackage(String preparation) throws Exception {
        requirePreparationMode(preparation, false);
        JsonObject current = status();
        String route = text(current, "route");
        String relation = text(current, "contentRelation");
        if ("management".equals(route) && "same".equals(relation)) {
            requireAgentCandidate();
            return;
        }
        if ("management".equals(route) && "different".equals(relation)
                && !"uninstall".equals(preparation)) {
            reapplyVehiclePackage();
            requireAgentCandidate();
            return;
        }
        if ("absent".equals(route)) requireAbsent();
        else prepare(preparation);
        installForLifecycle(VEHICLE_INSTALL_PHASE);
        requireAgentCandidate();
    }

    public PedestrianWarningSession pedestrianWarningSession(JsonObject candidateCatalog) throws Exception {
        return new PedestrianWarningSession(candidateCatalog);
    }

    public void restoreAgentSelection(JsonObject catalog, JsonObject state) throws Exception {
        var current = observeRuntime(text(status(), "activeGeneration"));
        boolean opened = false;
        for (var entry : state.entrySet()) {
            if (current.desired(entry.getKey()) == entry.getValue().getAsBoolean()) continue;
            if (!opened) {
                verifySettingsTab();
                opened = true;
            }
            setAgent(catalog, entry.getKey(), entry.getValue().getAsBoolean());
        }
    }

    /** Prepare the shared QNX lifecycle before the selected UI cases. */
    public final class PedestrianWarningSession implements AutoCloseable {
        private final JsonObject catalog;
        private final java.util.Map<String, Boolean> original = new java.util.LinkedHashMap<>();

        private PedestrianWarningSession(JsonObject catalog) throws Exception {
            this.catalog = catalog;
            var current = observeRuntime(text(status(), "activeGeneration"));
            for (var entry : ru.voboost.tests.agents.AgentSettings.checkboxes(catalog).entrySet()) {
                for (JsonElement target : entry.getValue().getAsJsonArray("targets")) {
                    if ("qnx".equals(text(target.getAsJsonObject(), "platform"))) {
                        original.put(entry.getKey(), current.desired(entry.getKey()));
                        break;
                    }
                }
            }
            if (!original.containsKey(PedestrianWarningView.AGENT))
                throw new IOException("Candidate does not expose the PW Cluster agent");
        }

        public void prepare() throws Exception {
            closePwDrawer(boot(null).id());
            verifySettingsTab();
            for (var entry : original.entrySet()) {
                if (entry.getValue() && !PedestrianWarningView.AGENT.equals(entry.getKey()))
                    setAgent(catalog, entry.getKey(), false);
            }
            setAgent(catalog, PedestrianWarningView.AGENT, true);
            awaitPwDeliveryAndStartCluster(catalog, null);
            requirePwClusterApplied(catalog);
        }

        public JsonObject originalState() {
            JsonObject state = new JsonObject();
            original.forEach(state::addProperty);
            return state;
        }

        @Override public void close() throws Exception {
            if (productRun.pending() == null) restoreAgentSelection(catalog, originalState());
        }
    }

    public PedestrianWarningView pedestrianWarningView(JsonObject candidateCatalog, Path references, String machineName) throws Exception {
        return new PedestrianWarningView(candidateCatalog, references, machineName);
    }

    /** Fixture for an already installed machine. Owns only the PW state it changes. */
    public final class PedestrianWarningView implements AutoCloseable {
        public static final String VISIBLE = "agent-pedestrian-warning-off-visible";
        public static final String HIDDEN = "agent-pedestrian-warning-off-hidden";
        private static final String AGENT = "vehicle-drive-pedestrian-warning";
        private static final String THEME = "theme:2";
        private static final VoyemuClient.ClusterRegion REGION = new VoyemuClient.ClusterRegion(841, 102, 46, 46);
        private final JsonObject catalog;
        private final ru.voboost.tests.ui.ViewAssertion views;
        private String bootId;
        private String generation;
        private final int originalWarning;
        private final boolean originalAgent;
        private JsonObject expected;
        private ru.voboost.tests.agents.ClusterObservation nativeState;
        private Path captures;
        private PwContext context;
        private long sequence = 1;
        private boolean warningChanged, drawerChanged, drawer;
        private JsonObject borrowedDrawerSlot;

        private PedestrianWarningView(JsonObject catalog, Path references, String machineName) throws Exception {
            views = new ru.voboost.tests.ui.ViewAssertion(references, machineName + "-classic", REGION.width(), REGION.height(), VISIBLE, HIDDEN);
            this.catalog = catalog;
            bootId = boot(null).id();
            generation = text(status(), "activeGeneration");
            originalWarning = vehicleCan(bootId).pw();
            originalAgent = awaitRuntimeOwner("Current pedestrian-warning runtime", generation, bootId).desired(AGENT);
        }

        public void prepare() throws Exception {
            setEnabled(true);
            select(true);
            RunFiles.write(work.resolve("pw-stimulus-" + (++counter) + ".json"), setPwFixture(true, bootId));
            initializeCaptureContext();
        }

        public void prepareSound() throws Exception {
            setEnabled(true);
            select(true);
            initializeCaptureContext();
        }

        private void initializeCaptureContext() throws Exception {
            context = pwContext(bootId, true);
            if (!THEME.equals(context.theme())) throw new IOException("PW reference requires " + THEME + "; current " + context.theme());
            Path captureRoot = voyemuRepository.resolve(".local/build/tmp");
            captures = RunFiles.directory(captureRoot.resolve("voboost-pw-view-" + owned.session().id() + "-" + UUID.randomUUID()));
        }

        public void enable() throws Exception {
            setEnabled(true);
            nativeState = requirePwClusterApplied(catalog).observation();
            expected = ru.voboost.tests.agents.AgentSettings.snapshot(catalog,
                    observeRuntime(generation));
        }

        private void setEnabled(boolean enabled) throws Exception {
            if (observeRuntime(generation).desired(AGENT) != enabled) {
                closePwDrawer(bootId);
                verifySettingsTab();
                setAgent(catalog, AGENT, enabled);
            }
        }

        private JsonObject confirmAgent(CliProcess.Control scope) throws Exception {
            if (nativeState == null) throw new IOException("PW agent must be ready before capture");
            var current = observeRuntime(generation, scope, nativeState.publisher());
            var confirmed = nativeState.confirmPw(current, catalog, expected);
            confirmed.requireCurrentPw(current);
            nativeState = confirmed;
            return confirmed.evidence();
        }

        public void useDrawer() {
            drawer = true;
        }

        public void restartMultimedia() throws Exception {
            rebootAndVerify();
            bootId = boot(null).id();
            generation = text(status(), "activeGeneration");
            initializeCaptureContext();
            sequence = 1;
            enable();
        }

        public void restartBoth() throws Exception {
            restartBothAndVerify();
            bootId = boot(null).id();
            generation = text(status(), "activeGeneration");
            initializeCaptureContext();
            sequence = 1;
            enable();
        }

        public record DrawerIcon(Path file, ru.voboost.voyemu.PixelImage image) {}

        private JsonObject drawerIconTree() throws Exception {
            return awaitObservation("Current pedestrian warning drawer icon", Duration.ofSeconds(10),
                    (scope, deadline) -> {
                JsonObject tree = settingsUi(bootId, deadline);
                ru.voboost.voyemu.VehicleDrawer.pedestrianWarningIcon(
                        ru.voboost.voyemu.UiEvidence.surface(tree, "primary"));
                return tree;
            });
        }

        public DrawerIcon captureDrawerIcon(String state, boolean warningOn) throws Exception {
            if (!drawer || !state.matches("[a-z]+(?:-[a-z]+)*"))
                throw new IllegalArgumentException("Named drawer state required");
            drawerChanged |= editDrawer(true);
            JsonObject before = drawerIconTree();
            JsonObject icon = ru.voboost.voyemu.VehicleDrawer.pedestrianWarningIcon(
                    ru.voboost.voyemu.UiEvidence.surface(before, "primary"));
            var beforeAgent = viewContext(warningOn ? 2 : 1);
            var can = beforeAgent.can();
            if (can.pw() != (warningOn ? 2 : 1)) throw new IOException("Drawer sound state differs before capture");
            Path directory = captures.resolve("drawer-" + state + "-" + (++counter));
            JsonObject capture = nativeRead(client.capturePrimary(id(), profile, owned.session(), directory),
                    "pedestrian-warning-drawer-capture");
            JsonObject after = drawerIconTree();
            GuestBootCapture.read(capture, directory, before, after, profile, owned.session().id(),
                    bootId, Math.toIntExact(androidPid));
            JsonObject current = ru.voboost.voyemu.VehicleDrawer.pedestrianWarningIcon(
                    ru.voboost.voyemu.UiEvidence.surface(after, "primary"));
            if (!icon.get("bounds").equals(current.get("bounds")))
                throw new IOException("Drawer icon moved during capture");
            var afterAgent = viewContext(warningOn ? 2 : 1);
            var confirmed = afterAgent.can();
            confirmed.requireNext(can);
            if (confirmed.pw() != can.pw()) throw new IOException("Sound changed during drawer capture");
            JsonObject bounds = icon.getAsJsonObject("bounds");
            int x = bounds.get("x").getAsInt(), y = bounds.get("y").getAsInt();
            int width = bounds.get("width").getAsInt(), height = bounds.get("height").getAsInt();
            var frame = ru.voboost.voyemu.PixelImage.read(directory.resolve("primary.png").toRealPath(), 1920, 720);
            var cropped = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int row = 0; row < height; row++) for (int column = 0; column < width; column++)
                cropped.setRGB(column, row, frame.pixel(x + column, y + row));
            Path image = directory.resolve("agent-pedestrian-warning-drawer-" + state + ".png");
            if (!javax.imageio.ImageIO.write(cropped, "PNG", image.toFile()))
                throw new IOException("Drawer icon PNG writer unavailable");
            JsonObject proof = new JsonObject();
            proof.add("can", confirmed.receipt());
            proof.add("bounds", bounds);
            proof.addProperty("image", image.toString());
            proof.addProperty("bootId", bootId);
            proof.addProperty("generation", generation);
            proof.add("agent", afterAgent.agent());
            RunFiles.write(directory.resolve("agent-pedestrian-warning-drawer.json"), proof);
            return new DrawerIcon(image, ru.voboost.voyemu.PixelImage.read(image.toRealPath(), width, height));
        }

        private boolean editDrawer(boolean present) throws Exception {
            try (ProductRun.Operation operation = launcherOperation("pw-drawer-card-" + present)) {
                operation.observePriorBoot(bootId);
                operation.dispatch();
                PwDrawerEdit edit = setPwDrawer(bootId, present, true, borrowedDrawerSlot);
                borrowedDrawerSlot = edit.borrowed();
                Path evidence = work.resolve("pw-drawer-card-" + (++counter) + ".json");
                RunFiles.write(evidence, edit.tree());
                operation.complete(evidence);
                return edit.changed();
            }
        }

        public void select(boolean on) throws Exception {
            int wanted = on ? 2 : 1;
            if (vehicleCan(bootId).pw() != wanted) {
                if (drawer) {
                    PwDrawerEdit edit = pwDrawerSelect(wanted, true, borrowedDrawerSlot).edit();
                    drawerChanged |= edit.changed();
                    borrowedDrawerSlot = edit.borrowed();
                } else {
                    vehicleSelect(ru.voboost.voyemu.VehicleUi.Feature.PEDESTRIAN_WARNING, on);
                }
                warningChanged = true;
                if (nativeState != null) awaitSelectedIntent(on ? "off" : "on");
            }
        }

        private void awaitSelectedIntent(String intent) throws Exception {
            JsonObject revised = expected.deepCopy();
            var options = revised.getAsJsonObject(AGENT).getAsJsonObject("options");
            boolean changed = !intent.equals(text(options, "intent"));
            options.addProperty("intent", intent);
            nativeState = awaitObservation("PW light intent " + intent, Duration.ofSeconds(30), (scope, deadline) -> {
                var current = observeRuntime(generation, scope, nativeState.publisher());
                var observed = ru.voboost.tests.agents.AgentSettings.snapshot(catalog, current);
                if (!observed.equals(expected) && !observed.equals(revised))
                    throw new IOException("OEM PW input changed another agent setting");
                if (!observed.equals(revised)) return null;
                var next = changed ? nativeState.advancePwSelection(current, catalog, revised)
                        : nativeState.confirmPw(current, catalog, revised);
                next.requireCurrentPw(current);
                return next;
            });
            expected = revised;
        }

        private record ViewContext(ru.voboost.voyemu.VehicleCan.Snapshot can, JsonObject agent,
                ru.voboost.tests.agents.ClusterObservation nativeState) {}

        private ViewContext viewContext(int wanted) throws Exception {
            return awaitObservation("PW capture context", Duration.ofSeconds(15), (scope, deadline) -> {
                ViewContext result = inReadyBoot(bootId, () -> boot(scope), () -> {
                    var can = readVehicleCan(bootId, scope);
                    if (can.pw() != wanted || !context.equals(pwSourceContext(bootId, null, scope, true)))
                        throw new IOException("PW state or QNX theme changed at capture");
                    var current = readRuntime(bootId, generation, scope, nativeState.publisher(), false);
                    var confirmed = nativeState.confirmPw(current, catalog, expected);
                    confirmed.requireCurrentPw(current);
                    return new ViewContext(can, confirmed.evidence(), confirmed);
                });
                nativeState = result.nativeState();
                return result;
            });
        }

        public void assertView(String state, boolean warningOn) throws Exception {
            int wanted = warningOn ? 2 : 1;
            ViewContext before = viewContext(wanted);
            Path directory = captures.resolve(state + "-" + (++counter));
            JsonObject receipt = owned.captureCluster(directory, sequence, REGION);
            if (!truth(receipt, "accepted")) throw new IOException("PW region capture rejected");
            ViewContext after = viewContext(wanted);
            after.can().requireNext(before.can());
            var frame = ru.voboost.tests.ui.PedestrianWarningPixels.readCapture(directory, owned.session().id(),
                    context.generation(), context.theme(), wanted, REGION);
            long next = clusterSequence(directory);
            if (next <= sequence) throw new IOException("PW frame did not advance");
            sequence = next;
            JsonObject evidence = after.agent();
            evidence.addProperty("agentEnabled", true);
            evidence.add("can", after.can().receipt());
            evidence.addProperty("view", state);
            evidence.addProperty("capture", directory.toString());
            Path proof = work.resolve("pw-view-" + (++counter) + ".json");
            try {
                views.assertView(state, directory.resolve("cluster.png"), frame.image());
            } catch (AssertionError mismatch) {
                evidence.addProperty("matched", false);
                RunFiles.write(proof, evidence);
                throw mismatch;
            }
            evidence.addProperty("matched", true);
            RunFiles.write(proof, evidence);
        }

        public Path failureLogs() throws InterruptedException {
            return captureDiagnosticLogs("agent-pedestrian-warning");
        }

        @Override public void close() throws Exception {
            if (productRun.pending() != null) throw new IOException("PW input outcome is uncertain; owner retained");
            if (warningChanged && vehicleCan(bootId).pw() != originalWarning) {
                closePwDrawer(bootId);
                vehicleSelect(ru.voboost.voyemu.VehicleUi.Feature.PEDESTRIAN_WARNING, originalWarning == 2);
            }
            if (drawerChanged) editDrawer(false);
            if (pwFixtureOriginal != null) {
                JsonObject restored = owned.setPwSoundFixture(pwFixtureOriginal == 1, bootId);
                RunFiles.write(work.resolve("pw-mcu-restored-" + (++counter) + ".json"), restored);
                pwFixtureOriginal = null;
            }
            setEnabled(originalAgent);
        }
    }

    /** Native delivery prerequisite only; never grants screenshot or feature acceptance. */
    public Path verifyPwReadinessOnly(JsonObject candidateCatalog) throws Exception {
        Path state = requirePwClusterApplied(candidateCatalog).evidence();
        JsonObject proof = new JsonObject();
        proof.addProperty("caseNumber", 14);
        proof.addProperty("semanticFunctionalStatus", "NOT_RUN");
        proof.addProperty("functionalAcceptance", false);
        proof.addProperty("nativeReadiness", "PASS");
        proof.addProperty("nativeEvidence", state.toString());
        proof.addProperty("reason", "Current pedestrian warning light process and applied state confirmed; OEM switches and icon pixels were not exercised");
        Path file = work.resolve("pw-readiness-" + (++counter) + ".json");
        RunFiles.write(file, proof);
        return file;
    }

    /** Continue only the unrun icon subcheck; prior persistence evidence remains a separate run. */
    public Path verifyPwIconOnly(JsonObject candidateCatalog)throws Exception {
        if(productRun==null||!productRun.owns(owned))throw new IOException("current exclusive vehicle owner required");
        Path rendered=verifyPwRendered(candidateCatalog);JsonObject raster=RunFiles.read(rendered);
        ru.voboost.tests.ui.PedestrianWarningPixels.readProof(raster);
        JsonObject proof=new JsonObject();proof.addProperty("caseNumber",14);proof.addProperty("semanticFunctionalStatus","NOT_RUN");
        proof.addProperty("reason","Icon-only continuation; earlier persistence observations are retained separately and are not rerun or granted by this receipt");
        proof.addProperty("functionalAcceptance",false);proof.addProperty("renderedPwIconAcceptance",true);
        proof.add("renderedPwProof",raster);proof.addProperty("renderedPwProofPath",rendered.toString());
        Path file=work.resolve("vehicle-case-14-icon-"+(++counter)+".json");RunFiles.write(file,proof);return file;
    }

    private static final String OUTING_AGENT = "vehicle-drive-outing";
    private static final String OUTING_OWNER = "com.qinggan.app.vehiclesetting";
    private static final String DRIVE_MODE_RESOURCE = OUTING_OWNER + ":id/fragment_radio_button_drive_mode_";
    private static final Set<String> OUTING_DIALOG = Set.of("Outing", "Режим Outing", "郊游模式");

    private record OutingOptions(String target, String aboveSpeed) {
        static OutingOptions read(ru.voboost.tests.agents.RuntimeObservation runtime) throws IOException {
            JsonObject values = object(runtime.desiredConfiguration(OUTING_AGENT), "options");
            String target = values.has("exit-target") ? text(values, "exit-target") : "comfort";
            String aboveSpeed = values.has("above-speed") ? text(values, "above-speed") : "exit";
            if (!Set.of("comfort", "previous").contains(target) || !Set.of("exit", "keep").contains(aboveSpeed))
                throw new IOException("Canonical Outing options unavailable");
            return new OutingOptions(target, aboveSpeed);
        }
    }

    private final class OutingProbe {
        private final JsonObject catalog;
        private final String boot;
        private final String generation;
        private final String publisher;
        private final Path evidence;
        private ru.voboost.voyemu.VehicleCan.PowerSnapshot prior;

        OutingProbe(JsonObject catalog, String boot, String generation,
                ru.voboost.tests.agents.RuntimeObservation original) throws IOException {
            this.catalog = catalog;
            this.boot = boot;
            this.generation = generation;
            this.publisher = original.publisher();
            evidence = RunFiles.directory(work.resolve("outing-" + (++counter)));
        }

        private ru.voboost.tests.agents.RuntimeObservation runtime() throws Exception {
            return observeRuntime(generation, null, publisher, boot);
        }

        private int mode() throws Exception {
            var raw = nativeRead(client.outingCanRead(id(), profile, owned.session()), "outing-can");
            var sample = ru.voboost.voyemu.VehicleCan.readPower(raw, owned.session().id(), boot);
            if (prior != null) sample.requireNext(prior);
            prior = sample;
            return ru.voboost.voyemu.VehicleCan.driveMode(sample);
        }

        private void speed(int value, String phase) throws Exception {
            if (!boot.equals(boot(null).id())) throw new IOException("Outing speed crossed a boot");
            JsonObject receipt = owned.setOutingSpeed(value);
            if (!boot.equals(boot(null).id())) throw new IOException("Outing speed acknowledgment crossed a boot");
            RunFiles.write(evidence.resolve(phase + "-speed.json"), receipt);
        }

        private void enable(boolean wanted) throws Exception {
            verifySettingsTab();
            setAgent(catalog, OUTING_AGENT, wanted);
            if (wanted) owned.launch(OUTING_OWNER, boot, "primary");
            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            do {
                var snapshot = runtime();
                if (snapshot.desired(OUTING_AGENT) == wanted && active(snapshot, wanted)) return;
                Thread.sleep(100);
            } while (System.nanoTime() < deadline);
            throw new IOException("Outing targets did not settle to the desired state");
        }

        private boolean active(ru.voboost.tests.agents.RuntimeObservation runtime, boolean expected) {
            var processes = runtime.targets(OUTING_AGENT).stream()
                    .filter(row -> "active".equals(text(row, "state")))
                    .map(row -> text(row, "process")).collect(java.util.stream.Collectors.toSet());
            return expected ? processes.equals(Set.of(OUTING_OWNER, "com.qinggan.app.vehicle")) : processes.isEmpty();
        }

        private void openDrive() throws Exception {
            owned.launch(OUTING_OWNER, boot, "primary");
            long deadline = System.nanoTime() + Duration.ofSeconds(25).toNanos();
            int scrolls = 0;
            boolean menuOpened = false;
            while (System.nanoTime() < deadline) {
                JsonObject tree = settingsUi(boot, deadline);
                JsonObject surface = ru.voboost.voyemu.UiEvidence.surface(tree, "primary");
                if (driveRadio(surface, 4) != null) return;
                if (!menuOpened) {
                    String menu = null;
                    try { menu = ru.voboost.voyemu.VehicleUi.uniqueResource(surface,
                            OUTING_OWNER + ":id/rb_menu_drive_preference"); }
                    catch (ru.voboost.voyemu.UiEvidence.Unavailable pending) { /* Re-read below. */ }
                    if (menu != null) {
                        try { tapNode(tree, menu); menuOpened = true; }
                        catch (OwnedSession.NotStarted vanished) { /* Proved no input occurred. */ }
                        continue;
                    }
                }
                if (scrolls < 8) {
                    String resource = OUTING_OWNER + (menuOpened ? ":id/fragment_drive_preference_scroll" : ":id/menu_container");
                    ru.voboost.voyemu.UiTarget target = null;
                    try { target = ru.voboost.voyemu.UiTarget.selectScrollable(tree, profile,
                            owned.session(), "primary", node -> resource.equals(text(node, "resource_id"))); }
                    catch (ru.voboost.voyemu.UiEvidence.Unavailable pending) { /* Selection only. */ }
                    if (target != null) {
                        if (menuOpened) owned.scrollSettings(target);
                        else owned.scrollSettingsToStart(target);
                        scrolls++;
                    }
                }
                Thread.sleep(100);
            }
            throw new IOException("Stock Outing drive-preference surface is unavailable");
        }

        private void choose(int mode, String phase) throws Exception {
            long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
            JsonObject tree = settingsUi(boot, deadline);
            JsonObject node = driveRadio(ru.voboost.voyemu.UiEvidence.surface(tree, "primary"), mode);
            if (node == null) throw new IOException("OEM drive mode radio unavailable: " + mode);
            if (!truth(node, "checked") || mode() != mode) tapNode(tree, text(node, "id"));
            requireMode(mode, phase, false);
        }

        private void requireMode(int expected, String phase, boolean hold) throws Exception {
            long started = System.nanoTime();
            long deadline = started + Duration.ofSeconds(12).toNanos();
            int samples = 0;
            do {
                JsonObject tree = settingsUi(boot, deadline);
                JsonObject node = driveRadio(ru.voboost.voyemu.UiEvidence.surface(tree, "primary"), expected);
                int actual = mode();
                boolean match = node != null && truth(node, "checked") && actual == expected;
                if (hold && !match) throw new IOException(phase + " did not hold drive mode " + expected + ", CAN=" + actual);
                if (match) samples++;
                if (match && (!hold || (samples >= 2 && System.nanoTime() - started >= Duration.ofSeconds(2).toNanos()))) {
                    RunFiles.write(evidence.resolve(phase + "-ui.json"), tree);
                    RunFiles.write(evidence.resolve(phase + "-can.json"), prior.receipt());
                    return;
                }
                Thread.sleep(100);
            } while (System.nanoTime() < deadline);
            throw new IOException(phase + " did not establish drive mode " + expected);
        }

        private void setPolicy(OutingOptions expected, String phase) throws Exception {
            var before = runtime();
            if (expected.equals(OutingOptions.read(before))) return;
            long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            JsonObject tree = settingsUi(boot, deadline);
            JsonObject surface = ru.voboost.voyemu.UiEvidence.surface(tree, "primary");
            var anchors = new java.util.HashSet<String>();
            for (JsonElement entry : surface.getAsJsonArray("nodes")) {
                JsonObject node = entry.getAsJsonObject();
                if ((OUTING_OWNER + ":id/drive_mode_tip_container").equals(text(node, "resource_id")))
                    anchors.add(text(node, "id"));
            }
            JsonObject action = outingNode(surface, node -> anchors.contains(text(node, "parent"))
                    && "android.widget.Button".equals(text(node, "class"))
                    && truth(node, "actionable_candidate")
                    && Set.of("Settings", "Настройки", "设置").contains(text(node, "text")));
            if (action == null) throw new IOException("Outing Settings button unavailable in the OEM anchor");
            tapNode(tree, text(action, "id"));
            selectPolicy(expected.target().equals("previous")
                    ? Set.of("Previous mode", "Предыдущий", "上一个模式")
                    : Set.of("Comfort", "Комфорт", "舒适"), deadline);
            selectPolicy(expected.aboveSpeed().equals("keep")
                    ? Set.of("Keep Outing", "Оставить Outing", "保持郊游模式")
                    : Set.of("Exit Outing", "Выйти из Outing", "退出郊游模式"), deadline);
            tree = settingsUi(boot, deadline);
            JsonObject save = policyControl(tree, Set.of("Save", "Сохранить", "保存"), false);
            if (save == null) throw new IOException("Outing Save unavailable in the policy dialog");
            tapNode(tree, text(save, "id"));
            do {
                var current = runtime();
                tree = settingsUi(boot, deadline);
                if (!current.revision().equals(before.revision()) && expected.equals(OutingOptions.read(current))
                        && policyControl(tree, Set.of("Save", "Сохранить", "保存"), false) == null) {
                    RunFiles.write(evidence.resolve(phase + "-editor-closed.json"), tree);
                    return;
                }
                Thread.sleep(100);
            } while (System.nanoTime() < deadline);
            throw new IOException("Outing policy Save was not confirmed by current persisted settings");
        }

        private void selectPolicy(Set<String> labels, long deadline) throws Exception {
            boolean tapped = false;
            do {
                JsonObject tree = settingsUi(boot, deadline);
                JsonObject choice = policyControl(tree, labels, true);
                if (choice != null && truth(choice, "checked")) return;
                if (choice != null && !tapped) {
                    tapNode(tree, text(choice, "id"));
                    tapped = true;
                }
                Thread.sleep(100);
            } while (System.nanoTime() < deadline);
            throw new IOException("Outing policy radio did not become selected: " + labels);
        }

        private void closeEditorIfOpen() throws Exception {
            long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            JsonObject tree = settingsUi(boot, deadline);
            JsonObject surface = ru.voboost.voyemu.UiEvidence.surface(tree, "primary");
            JsonObject title = outingEditorTitle(surface);
            if (title == null) return;
            JsonObject cancel = outingNode(surface, node ->
                    title.get("window_id").equals(node.get("window_id"))
                    && (OUTING_OWNER + ":id/cancel_settings").equals(text(node, "resource_id")));
            if (cancel == null) throw new IOException("Open Outing editor has no OEM Cancel action");
            tapNode(tree, text(cancel, "id"));
            do {
                tree = settingsUi(boot, deadline);
                if (outingEditorTitle(ru.voboost.voyemu.UiEvidence.surface(tree, "primary")) == null) return;
                Thread.sleep(100);
            } while (System.nanoTime() < deadline);
            throw new IOException("Outing editor did not close during cleanup");
        }

        private void cross(String phase, OutingOptions expected, int result) throws Exception {
            speed(39, phase + "-below");
            requireMode(4, phase + "-below", true);
            requirePolicy(expected);
            speed(40, phase + "-threshold");
            requireMode(result, phase + "-threshold", result == 4);
            requirePolicy(expected);
            if (result == 4) {
                speed(41, phase + "-above");
                requireMode(4, phase + "-above", true);
                requirePolicy(expected);
            }
            speed(0, phase + "-reset");
        }

        private void requirePolicy(OutingOptions expected) throws Exception {
            var state = runtime();
            boolean enabled = expected != null;
            if (state.desired(OUTING_AGENT) != enabled || !active(state, enabled)
                    || (enabled && !expected.equals(OutingOptions.read(state))))
                throw new IOException("Outing policy authority changed across the threshold");
        }
    }

    private static JsonObject outingNode(JsonObject surface, java.util.function.Predicate<JsonObject> selector)
            throws IOException {
        JsonObject found = null;
        for (JsonElement entry : surface.getAsJsonArray("nodes")) {
            JsonObject node = entry.getAsJsonObject();
            if (!OUTING_OWNER.equals(text(node, "package")) || !truth(node, "visible") || !selector.test(node)) continue;
            if (found != null) throw new IOException("Ambiguous Outing UI node");
            found = node;
        }
        return found;
    }

    private static JsonObject driveRadio(JsonObject surface, int mode) throws IOException {
        Set<String> names = switch (mode) {
            case 1 -> Set.of("eco", "eco_5");
            case 2 -> Set.of("comfort", "comfort_5");
            case 3 -> Set.of("energy", "energy_5");
            case 4 -> Set.of("outing");
            case 5 -> Set.of("personal", "personal_5");
            case 6 -> Set.of("snow", "snow_2");
            default -> throw new IOException("Unsupported original drive mode: " + mode);
        };
        return outingNode(surface, node -> truth(node, "checkable")
                && names.stream().anyMatch(name -> (DRIVE_MODE_RESOURCE + name).equals(text(node, "resource_id"))));
    }

    private static JsonObject policyControl(JsonObject tree, Set<String> labels, boolean radio) throws IOException {
        JsonObject surface = ru.voboost.voyemu.UiEvidence.surface(tree, "primary");
        JsonObject title = outingEditorTitle(surface);
        if (title == null) return null;
        return outingNode(surface, node -> title.get("window_id").equals(node.get("window_id"))
                && labels.contains(text(node, "text")) && (radio ? truth(node, "checkable")
                : (OUTING_OWNER + ":id/save_settings").equals(text(node, "resource_id"))));
    }

    private static JsonObject outingEditorTitle(JsonObject surface) throws IOException {
        return outingNode(surface, node ->
                (OUTING_OWNER + ":id/drive_mode_personal_setting_title_tx").equals(text(node, "resource_id"))
                        && OUTING_DIALOG.contains(text(node, "text")));
    }

    public Path verifyOuting(JsonObject candidateCatalog) throws Exception {
        if (productRun == null || !productRun.owns(owned)) throw new IOException("Exclusive current Outing case owner required");
        String boot = boot(null).id(), generation = text(status(), "activeGeneration");
        var original = observeRuntime(generation, null, null, boot);
        var probe = new OutingProbe(candidateCatalog, boot, generation, original);
        int originalSpeed = OwnedSession.outingSpeed(owned.outingVehicle()), originalMode = probe.mode();
        OutingOptions options = OutingOptions.read(original);
        boolean enabled = original.desired(OUTING_AGENT);
        Exception failure = null;
        try {
            probe.speed(0, "start");
            probe.enable(false);
            probe.openDrive();
            probe.choose(4, "stock-entry");
            probe.cross("stock", null, 2);

            // The A9 adapter echoes DRIVING_MODE_SET and does not synthesize the BCM pulse.
            // Activate the agent with a real, distinctive, already selected mode for its initial read.
            probe.choose(3, "previous-seed");
            probe.enable(true);
            probe.openDrive();
            probe.requireMode(3, "previous-ready", false);
            probe.choose(4, "previous-entry");
            OutingOptions previous = new OutingOptions("previous", "exit");
            probe.setPolicy(previous, "previous");
            probe.cross("previous", previous, 3);

            probe.choose(4, "keep-entry");
            OutingOptions keep = new OutingOptions("previous", "keep");
            probe.setPolicy(keep, "keep");
            probe.cross("keep", keep, 4);

            OutingOptions comfort = new OutingOptions("comfort", "exit");
            probe.setPolicy(comfort, "comfort");
            probe.cross("comfort", comfort, 2);
        } catch (Exception error) {
            failure = error;
            throw error;
        } finally {
            try {
                probe.speed(0, "cleanup-reset");
                probe.closeEditorIfOpen();
                if (!options.equals(OutingOptions.read(probe.runtime()))) {
                    probe.openDrive();
                    probe.choose(4, "cleanup-entry");
                    probe.setPolicy(options, "cleanup");
                }
                probe.openDrive();
                probe.choose(originalMode, "cleanup-mode");
                probe.enable(enabled);
                probe.speed(originalSpeed, "cleanup-original");
                probe.openDrive();
                probe.requireMode(originalMode, "cleanup-original", true);
                if (!options.equals(OutingOptions.read(probe.runtime())))
                    throw new IOException("Original Outing options were not restored");
            } catch (Exception cleanup) {
                if (failure == null) throw cleanup;
                failure.addSuppressed(cleanup);
            }
        }
        return probe.evidence;
    }

    public Path verifyForcedEvReturn(JsonObject candidateCatalog) throws Exception {
        String agent = "vehicle-drive-forced-ev";
        var original = observeRuntime(text(status(), "activeGeneration"));
        var initial = fevObserve();
        if (initial.mode() != ru.voboost.voyemu.ForcedEvProgram.Mode.ELECTRIC)
            throw new IOException("Forced EV cycle requires initial Electric mode");
        JsonObject selection = new JsonObject();
        selection.addProperty(agent, original.desired(agent));
        try {
            verifySettingsTab();
            setAgent(candidateCatalog, agent, true);
            fevSelectOem(true);
            fevDrawerTap(false, false);
            if (fevObserve().can().fev() != 3) throw new IOException("Hybrid not selected");
            fevDrawerTap(false, false);
            if (fevObserve().can().fev() != 4) throw new IOException("Save not selected");
            Path directory = RunFiles.directory(work.resolve("fev-return-" + (++counter)));
            try (var monitor = nativeCanMonitor(boot(null).id(), directory)) {
                monitor.beginReturn();
                fevDrawerTap(false, false);
                monitor.endReturn();
            }
            var result = fevObserve();
            if (result.can().fev() != 5 || !Boolean.TRUE.equals(result.switchOn())
                    || !Boolean.TRUE.equals(result.armed()))
                throw new IOException("Forced EV UI or saved intent differs after direct return");
            return result.evidence();
        } finally {
            if (productRun.pending() == null) {
                var current = fevObserve();
                if (current.mode() == ru.voboost.voyemu.ForcedEvProgram.Mode.ELECTRIC
                        && !java.util.Objects.equals(current.switchOn(), initial.switchOn()))
                    fevSelectOem(Boolean.TRUE.equals(initial.switchOn()));
                restoreAgentSelection(candidateCatalog, selection);
            }
        }
    }

    public void verifyForcedEv(JsonObject candidateCatalog) throws Exception {
        String agent = "vehicle-drive-forced-ev";
        var original = observeRuntime(text(status(), "activeGeneration"));
        JsonObject selection = new JsonObject();
        selection.addProperty(agent, original.desired(agent));
        int initialMode=vehicleCan(boot(null).id()).fev();
        if (initialMode!=2 && initialMode!=5)
            throw new IOException("FEV fixture requires Electric mode");
        try (AutoCloseable restore = () -> {
            if (productRun.pending() == null) {
                int currentMode=vehicleCan(boot(null).id()).fev();
                if((currentMode==2 || currentMode==5) && currentMode!=initialMode)
                    fevSelectOem(initialMode==5);
                restoreAgentSelection(candidateCatalog, selection);
            }
        }) {
            Path evidence = verifyVehicleFunctional(candidateCatalog, 13);
            JsonObject proof = RunFiles.read(evidence);
            if (!truth(proof, "functionalAcceptance")) {
                throw new IOException("FEV acceptance: " + text(proof, "reason") + ". Evidence: " + evidence);
            }
        }
    }

    public Path verifyVehicleFunctional(JsonObject candidateCatalog,int number) throws Exception {
        if(!Set.of(13,14).contains(number)||productRun==null||!productRun.owns(owned))throw new IOException("closed vehicle case/current exclusive owner required");
        BootData before=boot(null);var baseline=vehicleCan(before.id());JsonObject proof=new JsonObject();proof.addProperty("caseNumber",number);proof.addProperty("functionalAcceptance",false);proof.add("initialCan",baseline.receipt());
        Path path=work.resolve("vehicle-case-"+number+"-"+(++counter)+".json");
        if(number==13) {
            var result=verifyForcedEvProgram(candidateCatalog,baseline);JsonArray files=new JsonArray();for(Path file:result.evidence())files.add(file.toString());proof.add("artifacts",files);
            proof.addProperty("semanticFunctionalStatus",result.admitted()?"PASS":"BLOCKED");proof.addProperty("reason",result.reason());proof.addProperty("functionalAcceptance",result.admitted());
            proof.addProperty("mutationsDispatched",result.admitted());RunFiles.write(path,proof);return path;
        }
        var feature=ru.voboost.voyemu.VehicleUi.Feature.PEDESTRIAN_WARNING;
        // Probe the genuine public OEM scene before changing agent intent or feature state.
        try {vehicleObserve(feature);}catch(ru.voboost.voyemu.UiEvidence.Unavailable unsupported) {
            throw unsupported; // A dispatched ProductRun remains retained for its explicit read-only resolver.
        }
        var result=ru.voboost.voyemu.VehiclePersistence.run(new ru.voboost.voyemu.VehiclePersistence.Port() {
            public void enable(boolean enabled) throws Exception {verifySettingsTab();setAgent(candidateCatalog,"vehicle-drive-pedestrian-warning",enabled);}
            public void select(boolean selected) throws Exception {vehicleSelect(feature,selected);}
            public void reboot() throws Exception {rebootAndVerify();}
            public ru.voboost.voyemu.VehiclePersistence.State observe() throws Exception {return vehicleObserve(feature);}
        },false);
        JsonArray states=new JsonArray();for(var state:result.states())states.add(state.evidence().toString());proof.add("states",states);
        proof.addProperty("semanticFunctionalStatus","PASS");proof.addProperty("reason","Agent-disabled OEM OFF resets to ON after a new boot; agent-enabled OEM OFF remains OFF after another new boot, each independently confirmed by actual checked UI and fixed CAN getter");
        proof.addProperty("unchangedEnabledWarmFullCold",verifyPwEnabledRetention(candidateCatalog).toString());
        Path rendered=verifyPwRendered(candidateCatalog);JsonObject raster=RunFiles.read(rendered);ru.voboost.tests.ui.PedestrianWarningPixels.readProof(raster);proof.add("renderedPwProof",raster);proof.addProperty("renderedPwProofPath",rendered.toString());proof.addProperty("renderedPwIconAcceptance",true);proof.addProperty("functionalAcceptance",true);RunFiles.write(path,proof);return path;
    }

    private Path keyboardProof(int number, String boot, JsonArray artifacts, String semantic, String reason, String committed) throws IOException {
        JsonObject proof=new JsonObject();proof.addProperty("schemaVersion",1);proof.addProperty("caseNumber",number);
        proof.addProperty("bootId",boot);proof.addProperty("semanticInputStatus",semantic);proof.addProperty("reason",reason);
        proof.addProperty("committed",committed);proof.addProperty("visualAcceptance",false);proof.addProperty("functionalAcceptance",false);
        if(number==9||number==19) proof.addProperty("fontProvenanceStatus","BLOCKED: actual current glyph/font provenance and visual receipt required");
        proof.add("artifacts",artifacts);Path path=work.resolve("keyboard-case-"+number+"-"+(++counter)+".json");RunFiles.write(path,proof);return path;
    }
    private ru.voboost.voyemu.KeyboardInput.Frame navigationFrame(String boot) throws Exception {
        JsonObject tree=settingsUi(boot,System.nanoTime()+Duration.ofSeconds(5).toNanos());
        Path path=work.resolve("navigation-ui-"+(++counter)+".json");RunFiles.write(path,tree);
        return new ru.voboost.voyemu.KeyboardInput.Frame(tree,path);
    }
    private ru.voboost.voyemu.KeyboardInput.Frame openNavigation(String boot,JsonArray artifacts) throws Exception {
        return openNavigation(boot, artifacts, true);
    }
    private KeyboardInput.Frame openNavigation(String boot, JsonArray artifacts, boolean acceptSafety) throws Exception {
        owned.launch(NavigationInput.NAVI, boot, "primary");
        long deadline=System.nanoTime()+Duration.ofSeconds(20).toNanos();Set<String> sent=new java.util.HashSet<>();
        while(System.nanoTime()<deadline) {
            var frame=navigationFrame(boot);artifacts.add(frame.evidence().toString());
            JsonObject surface=frame.surface();JsonObject target=ru.voboost.voyemu.NavigationInput.setup(surface);
            if(target==null) {
                boolean editor=false;for(JsonElement item:surface.getAsJsonArray("nodes")) {
                    JsonObject node=item.getAsJsonObject();if(ru.voboost.voyemu.NavigationInput.SEARCH_EDITOR.equals(text(node,"resource_id"))
                            && ru.voboost.voyemu.NavigationInput.NAVI.equals(text(node,"package"))
                            && "android.widget.EditText".equals(text(node,"class")) && truth(node,"focused")) editor=true;
                }
                if(editor) return frame;
            } else {
                String resource=text(target,"resource_id");
                if (!acceptSafety && resource.equals(NavigationInput.NAVI + ":id/btn_agree"))
                    throw new UiEvidence.Unavailable("Navi safety acknowledgement must already be set in the owned keyboard fixture");
                if(!sent.contains(resource)) {
                    if(sent.size()>=3) throw new IOException("unexpected fourth Navi setup transition");
                    sent.add(resource);tapNode(frame.tree(),text(target,"id"));
                }
            }
            Thread.sleep(100);
        }
        throw new ru.voboost.voyemu.UiEvidence.Unavailable("actual focused Navi editor did not appear; setup actions were not replayed");
    }
    private Path naviLayout(String language,String committed,String probe,String boot,JsonArray artifacts)throws Exception {
        var frame=navigationFrame(boot);artifacts.add(frame.evidence().toString());var editor=ru.voboost.voyemu.NavigationInput.searchEditor(frame.surface());editor.requirePrefix(committed);
        try{ru.voboost.voyemu.NavigationInput.key(frame.surface(),probe);return frame.evidence();}
        catch(ru.voboost.voyemu.NavigationInput.CanvasRequired absent) { /* Only pre-dispatch alphabet absence may select an observed layout control. */ }
        JsonObject target=ru.voboost.voyemu.NavigationInput.switchLayout(frame.surface(),language);tapNode(frame.tree(),text(target,"id"));
        long deadline=System.nanoTime()+Duration.ofSeconds(5).toNanos();
        while(System.nanoTime()<deadline){var current=navigationFrame(boot);artifacts.add(current.evidence().toString());var kept=ru.voboost.voyemu.NavigationInput.searchEditor(current.surface());kept.requireSame(editor);kept.requirePrefix(committed);try{ru.voboost.voyemu.NavigationInput.key(current.surface(),probe);return current.evidence();}catch(ru.voboost.voyemu.NavigationInput.CanvasRequired transition){Thread.sleep(50);}}
        throw new IOException("one observed Navi layout switch lacks its fresh alphabet; no replay");
    }
    public Path verifyNaviSpacing(JsonObject candidateCatalog)throws Exception {
        if(productRun==null||!productRun.owns(owned))throw new IOException("exclusive current Navi case owner required");verifySettingsTab();setAgent(candidateCatalog,"font-cyrillic",true);setAgent(candidateCatalog,"keyboard-ru",true);
        BootData before=boot(null);JsonObject input=new JsonObject();input.addProperty("phase","navi-spacing19");input.addProperty("device",device);JsonArray artifacts=new JsonArray();
        try(ProductRun.Operation operation=productRun.reserve(ProductRun.Kind.CONFIGURE,input)) {
            operation.observePriorBoot(before.id());operation.dispatch();var initial=openNavigation(before.id(),artifacts);
            var result=ru.voboost.voyemu.NaviSpacingProgram.run(new ru.voboost.voyemu.NaviSpacingProgram.Port(){
                public ru.voboost.voyemu.NaviSpacingProgram.Admission admit()throws Exception {
                    try{ru.voboost.voyemu.NavigationInput.searchEditor(initial.surface()).requirePrefix("");naviLayout("ru","","М",before.id(),artifacts);return new ru.voboost.voyemu.NaviSpacingProgram.Admission(true,"Actual empty nonpassword UTF16 editor and live uppercase Russian alphabet",List.of(initial.evidence()));}
                    catch(ru.voboost.voyemu.UiEvidence.Unavailable|ru.voboost.voyemu.NavigationInput.CanvasRequired unsupported){return new ru.voboost.voyemu.NaviSpacingProgram.Admission(false,unsupported.getMessage(),List.of(initial.evidence()));}
                }
                public ru.voboost.voyemu.NaviSpacingProgram.Sample type(String stage,String text,String prefix)throws Exception {
                    var typed=new ru.voboost.voyemu.KeyboardInput(new ru.voboost.voyemu.KeyboardInput.Port(){
                        public ru.voboost.voyemu.KeyboardInput.Frame observe()throws Exception{var frame=navigationFrame(before.id());ru.voboost.voyemu.NavigationInput.searchEditor(frame.surface());return frame;}
                        public void tap(ru.voboost.voyemu.KeyboardInput.Frame frame,JsonObject key)throws Exception{tapNode(frame.tree(),InstallerDriver.text(key,"id"));}
                        public void canvas(ru.voboost.voyemu.KeyboardInput.Frame frame,ru.voboost.voyemu.NavigationInput.Editor editor,String key)throws Exception{throw new IOException("semantic key disappeared; no reviewed canvas fallback or replay");}
                    }).type(text,prefix);
                    for(Path file:typed.evidence())artifacts.add(file.toString());JsonObject sample=new JsonObject();sample.addProperty("stage",stage);sample.addProperty("committed",typed.committed());JsonArray points=new JsonArray();typed.committed().codePoints().forEach(points::add);sample.add("codepoints",points);sample.addProperty("ui",typed.finalFrame().evidence().toString());sample.addProperty("fontProvenanceStatus","BLOCKED: actual glyph/font receipt missing");sample.addProperty("visualAcceptance",false);Path file=work.resolve("navi19-"+stage+"-"+(++counter)+".json");RunFiles.write(file,sample);return new ru.voboost.voyemu.NaviSpacingProgram.Sample(stage,typed.committed(),file);
                }
                public Path resetOwned(String committed)throws Exception {
                    var current=navigationFrame(before.id());artifacts.add(current.evidence().toString());ru.voboost.voyemu.NavigationInput.searchEditor(current.surface()).requirePrefix(committed);JsonObject back=ru.voboost.voyemu.NavigationInput.searchBack(current.surface());tapNode(current.tree(),InstallerDriver.text(back,"id"));
                    var reopened=openNavigation(before.id(),artifacts);ru.voboost.voyemu.NavigationInput.searchEditor(reopened.surface()).requirePrefix("");return reopened.evidence();
                }
                public Path layout(String language,String committed)throws Exception{return naviLayout(language,committed,language.equals("en")?"m":"ё",before.id(),artifacts);}
                public void retain(ru.voboost.voyemu.NaviSpacingProgram.Sample sample){artifacts.add(sample.evidence().toString());}
            });
            BootData after=boot(null);if(!before.id().equals(after.id())||!after.complete()||!after.enforcing())throw new IOException("Navi case crossed boot/policy");
            String committed=result.samples().isEmpty()?"":result.samples().get(result.samples().size()-1).committed();Path proof=keyboardProof(19,before.id(),artifacts,result.semanticComplete()?"PASS":"BLOCKED",result.reason(),committed);operation.complete(proof);return proof;
        }
    }
    private record KeyboardScene(String boot, String source, NavigationInput.Editor editor) {}
    private record KeyboardFrames(KeyboardCapture.Frame first, KeyboardCapture.Frame second, UiTarget current) {}

    private KeyboardCapture.Frame captureKeyboard(String boot, UiTarget canvas, JsonArray artifacts) throws Exception {
        if (voyemuRepository == null) throw new IOException("owned Voyemu artifact root required");
        Path directory = voyemuRepository.resolve(".local/build/tmp/voboost-keyboard-" + UUID.randomUUID());
        long started = System.nanoTime();
        JsonObject receipt = nativeRead(client.captureKeyboard(id(), profile, owned.session(), canvas, directory, 500),
                "keyboard-capture", Duration.ofSeconds(2));
        artifacts.add(lastNativeEvidence.toString());
        var pixels = KeyboardCapture.read(receipt, directory, canvas, owned.session().id(), boot,
                androidPid, started, System.nanoTime());
        artifacts.add(pixels.png().toString());
        artifacts.add(pixels.manifest().toString());
        return pixels;
    }

    private NavigationInput.Editor keyboardEditor(KeyboardScene scene, KeyboardInput.Frame frame) throws Exception {
        if (!scene.source().equals(text(frame.tree(), "source_instance")))
            throw new IOException("Navi observation source changed");
        var editor = NavigationInput.searchEditor(frame.surface());
        editor.requireSame(scene.editor());
        return editor;
    }

    private UiTarget currentKeyboard(KeyboardScene scene, KeyboardInput.Frame frame, String committed,
            JsonArray artifacts) throws Exception {
        artifacts.add(frame.evidence().toString());
        keyboardEditor(scene, frame).requirePrefix(committed);
        return UiTarget.selectKeyboardCanvas(frame.tree(), profile, owned.session());
    }

    private KeyboardFrames keyboardFrames(KeyboardScene scene, KeyboardInput.Frame frame, String committed,
            JsonArray artifacts) throws Exception {
        String boot = scene.boot();
        var first = captureKeyboard(boot, currentKeyboard(scene, frame, committed, artifacts), artifacts);
        var second = captureKeyboard(boot, currentKeyboard(scene, navigationFrame(boot), committed, artifacts), artifacts);
        var current = currentKeyboard(scene, navigationFrame(boot), committed, artifacts);
        return new KeyboardFrames(first, second, current);
    }

    private String keyboardLayout(Path maps, KeyboardScene scene, String committed, JsonArray artifacts) throws Exception {
        var frames = keyboardFrames(scene, navigationFrame(scene.boot()), committed, artifacts);
        return ReviewedKeyboard.layout(maps, frames.first(), frames.second(), frames.current(),
                profile, owned.session(), androidPid);
    }

    private ReviewedKeyboard.Match reviewedKey(Path maps, KeyboardFrames frames, String key) throws IOException {
        return ReviewedKeyboard.match(maps, frames.first(), frames.second(), frames.current(),
                profile, owned.session(), androidPid, key);
    }

    private void pressReviewedKey(KeyboardFrames frames, ReviewedKeyboard.Match match, JsonArray artifacts) throws Exception {
        JsonObject receipt = owned.tapKeyboard(frames.current(), match, androidPid);
        Path evidence = work.resolve("keyboard-input-" + (++counter) + ".json");
        RunFiles.write(evidence, receipt);
        artifacts.add(evidence.toString());
    }

    private String keyboardControl(Path maps, KeyboardScene scene, String committed,
            String label, String from, String to, JsonArray artifacts) throws Exception {
        var frames = keyboardFrames(scene, navigationFrame(scene.boot()), committed, artifacts);
        var match = reviewedKey(maps, frames, label);
        if (!from.equals(match.baseline()) || !to.equals(match.nextBaseline()))
            throw new IOException("current keyboard pixels do not authorize " + from + " to " + to);
        pressReviewedKey(frames, match, artifacts);
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        do {
            Thread.sleep(50);
            String observed = keyboardLayout(maps, scene, committed, artifacts);
            if (to.equals(observed)) return to;
            if (!from.equals(observed)) throw new IOException("unexpected layout after the reviewed control");
        } while (System.nanoTime() < deadline);
        throw new IOException("reviewed keyboard control effect unavailable; do not replay");
    }

    private void keyboardLower(Path maps, KeyboardScene scene, String layout, String family,
            JsonArray artifacts) throws Exception {
        if (layout.equals(family + "-first.png"))
            layout = keyboardControl(maps, scene, "", "shift", layout, family + "-upper-lock.png", artifacts);
        if (layout.equals(family + "-upper-lock.png"))
            layout = keyboardControl(maps, scene, "", "shift", layout, family + "-lower.png", artifacts);
        if (!layout.equals(family + "-lower.png")) throw new IOException("reviewed English layout required");
    }

    /** Numbered keyboard input and the standalone agent tests share the real Navi editor. */
    public Path verifyKeyboardFunctional(JsonObject catalog, int number) throws Exception {
        if (!Set.of(9, 10, 11).contains(number) || productRun == null || !productRun.owns(owned))
            throw new IOException("closed keyboard case and exclusive owner required");
        var before = observeRuntime(text(status(), "activeGeneration"));
        JsonObject snapshot = ru.voboost.tests.agents.AgentSettings.snapshot(catalog, before);
        JsonObject original = new JsonObject();
        for (String id : number == 9 ? List.of("keyboard-en", "keyboard-ru", "font-cyrillic")
                : List.of("keyboard-en", "keyboard-ru")) {
            original.addProperty(id, before.desired(id));
        }
        try (AutoCloseable restore = () -> {
            if (productRun.pending() != null) return;
            restoreAgentSelection(catalog, original);
            var after = observeRuntime(text(status(), "activeGeneration"));
            if (!snapshot.equals(ru.voboost.tests.agents.AgentSettings.snapshot(catalog, after)))
                throw new IOException("original keyboard settings were not independently restored");
        }) {
            verifySettingsTab();
            if (number == 9) setAgent(catalog, "font-cyrillic", true);
            setAgent(catalog, number == 10 ? "keyboard-en" : "keyboard-ru", true);
            return runKeyboard(number);
        }
    }

    private Path runKeyboard(int number) throws Exception {
        BootData before = boot(null);
        if (!before.complete() || !before.enforcing()) throw new IOException("keyboard needs ready Enforcing boot");
        String boot = before.id();
        JsonObject input = new JsonObject();
        input.addProperty("phase", "keyboard-case-" + number);
        input.addProperty("device", device);
        input.addProperty("caseNumber", number);
        JsonArray artifacts = new JsonArray();
        try (ProductRun.Operation operation = productRun.reserve(ProductRun.Kind.CONFIGURE, input)) {
            operation.observePriorBoot(boot);
            operation.dispatch();
            KeyboardInput.Frame initial;
            try {
                initial = openNavigation(boot, artifacts, number == 9);
            } catch (UiEvidence.Unavailable unsupported) {
                Path proof = keyboardProof(number, boot, artifacts, "BLOCKED", unsupported.getMessage(), "");
                operation.complete(proof);
                return proof;
            }
            Path maps;
            KeyboardScene scene;
            String initialLayout;
            try {
                maps = Path.of("tests/common/agents/agent-keyboard/reviewed").toRealPath();
                var editor = NavigationInput.searchEditor(initial.surface());
                editor.requirePrefix("");
                scene = new KeyboardScene(boot, text(initial.tree(), "source_instance"), editor);
                initialLayout = keyboardLayout(maps, scene, "", artifacts);
                boolean englishOnly = initialLayout.startsWith("en-only-");
                if (englishOnly != (number == 10))
                    throw new UiEvidence.Unavailable("initial layout does not match the selected keyboard agent");
            } catch (IOException unsupported) {
                Path proof = keyboardProof(number, boot, artifacts, "BLOCKED", unsupported.getMessage(), "");
                operation.complete(proof);
                return proof;
            }
            boolean initialRussian = initialLayout.startsWith("ru-");
            if (number == 10) {
                keyboardLower(maps, scene, initialLayout, "en-only", artifacts);
            } else if (!initialRussian) {
                keyboardLower(maps, scene, initialLayout, "en", artifacts);
                keyboardControl(maps, scene, "", "switch", "en-lower.png", "ru-lower.png", artifacts);
            }
            if (number != 10 && !initialLayout.equals("ru-first.png")) {
                keyboardControl(maps, scene, "", "shift", "ru-lower.png", "ru-first.png", artifacts);
            }
            var keys = new KeyboardInput(new KeyboardInput.Port() {
                public KeyboardInput.Frame observe() throws Exception {
                    var frame = navigationFrame(boot);
                    keyboardEditor(scene, frame);
                    artifacts.add(frame.evidence().toString());
                    return frame;
                }
                public void tap(KeyboardInput.Frame frame, JsonObject key) throws Exception {
                    tapNode(frame.tree(), text(key, "id"));
                }
                public void canvas(KeyboardInput.Frame frame, NavigationInput.Editor current, String key) throws Exception {
                    var captures = keyboardFrames(scene, frame, current.value(), artifacts);
                    var match = reviewedKey(maps, captures, key);
                    if (match.baseline().startsWith("en-only-") != (number == 10))
                        throw new IOException("reviewed key belongs to a different keyboard agent mode");
                    pressReviewedKey(captures, match, artifacts);
                }
            });
            var typed = keys.type(number == 10 ? "moscow" : "Москва", "");
            if (number == 10) {
                keyboardControl(maps, scene, typed.committed(), "switch",
                        "en-only-lower.png", "en-only-first.png", artifacts);
            } else if (number == 11) {
                keyboardControl(maps, scene, typed.committed(), "switch", "ru-lower.png", "en-lower.png", artifacts);
                typed = keys.type("moscow", typed.committed());
                if (initialRussian)
                    keyboardControl(maps, scene, typed.committed(), "switch", "en-lower.png", "ru-lower.png", artifacts);
            } else if (!initialRussian) {
                keyboardControl(maps, scene, typed.committed(), "switch", "ru-lower.png", "en-lower.png", artifacts);
            }
            var current = navigationFrame(boot);
            artifacts.add(current.evidence().toString());
            keyboardEditor(scene, current).requirePrefix(typed.committed());
            tapNode(current.tree(), text(NavigationInput.searchBack(current.surface()), "id"));
            var reset = openNavigation(boot, artifacts, false);
            if (!scene.source().equals(text(reset.tree(), "source_instance")))
                throw new IOException("Navi observation source changed during search reset");
            NavigationInput.searchEditor(reset.surface()).requirePrefix("");
            BootData after = boot(null);
            if (!boot.equals(after.id()) || !after.complete() || !after.enforcing())
                throw new IOException("keyboard case crossed boot/policy boundary");
            Path proof = keyboardProof(number, boot, artifacts, "PASS",
                    "Reviewed layout and real keys committed exact text with UTF16 readback; owned search text cleared",
                    typed.committed());
            operation.complete(proof);
            return proof;
        }
    }

    static void fixtureSuccess(String body, boolean install) throws IOException {
        if (!("Success\n".equals(body) || install && "Performing Streamed Install\nSuccess\n".equals(body)))
            throw new IOException("fixed fixture mutation did not return its exact successful receipt");
    }
    private record FixtureOwner(Path file) {}
    private FixtureOwner installFixture(Path apk, JsonObject fixture) throws Exception {
        if (productRun == null || !productRun.owns(owned) || !"ru.voboost.tests.launcherfixture".equals(text(fixture,"package"))
                || !apk.isAbsolute() || !Files.isRegularFile(apk, java.nio.file.LinkOption.NOFOLLOW_LINKS)) throw new IOException("approved fixed fixture and current exclusive product owner required");
        FixturePackage absent = fixturePackage();
        if (absent.present()) throw new IOException("Voboost Fixture preexists; this run has no overwrite or removal authority");
        Path file = work.resolve("fixture-owner-" + (++counter) + ".json"); JsonObject owner = new JsonObject();
        owner.addProperty("schemaVersion",1); owner.addProperty("id",UUID.randomUUID().toString()); owner.addProperty("sessionId",owned.session().id());
        owner.addProperty("serial",device); owner.addProperty("manifest",manifest); owner.addProperty("package","ru.voboost.tests.launcherfixture"); owner.addProperty("initialBoot",absent.boot()); owner.addProperty("initialPresent",false); owner.addProperty("state","installing");
        JsonObject input = new JsonObject(); input.addProperty("phase","fixture-install"); input.addProperty("owner",file.toString()); input.addProperty("device",device); input.addProperty("package","ru.voboost.tests.launcherfixture");
        JsonObject request = action("install-fixture"); request.addProperty("apk",apk.toRealPath().toString()); request.addProperty("package",text(fixture,"package"));
        try (ProductRun.Operation operation = productRun.reserve(ProductRun.Kind.CONFIGURE,input)) {
            RunFiles.write(file,owner); operation.observePriorBoot(absent.boot()); operation.dispatch();
            String body = checkBody(request,null,Duration.ofMinutes(3)); fixtureSuccess(body,true);
            FixturePackage present = fixturePackage();
            if (!present.present() || !present.boot().equals(absent.boot())) throw new IOException("fixture installation lacks same-boot independent package presence");
            owner.addProperty("installReceipt",body); owner.addProperty("state","installed"); RunFiles.write(file,owner); operation.complete(file);
        }
        return new FixtureOwner(file);
    }
    static void requireFixtureOwner(JsonObject owner,String session,String serial,String manifest) throws IOException {
        if (!owner.keySet().equals(Set.of("schemaVersion","id","sessionId","serial","manifest","package","initialBoot","initialPresent","state","installReceipt"))
                || integer(owner,"schemaVersion")!=1 || !uuid(text(owner,"id")) || !session.equals(text(owner,"sessionId"))
                || !serial.equals(text(owner,"serial")) || !manifest.equals(text(owner,"manifest")) || !"ru.voboost.tests.launcherfixture".equals(text(owner,"package"))
                || !uuid(text(owner,"initialBoot")) || !falseValue(owner,"initialPresent") || !"installed".equals(text(owner,"state"))) throw new IOException("original fixed fixture ownership is unavailable; do not remove or replay");
        fixtureSuccess(text(owner,"installReceipt"),true);
    }
    private Path removeFixture(FixtureOwner fixture) throws Exception {
        Path file = fixture.file().toRealPath();
        if (productRun == null || !productRun.owns(owned) || !file.startsWith(work.toRealPath())) throw new IOException("fixture cleanup belongs to another product owner");
        JsonObject owner = RunFiles.read(file);
        requireFixtureOwner(owner,owned.session().id(),device,manifest);
        fixtureSuccess(text(owner,"installReceipt"),true); FixturePackage before = fixturePackage();
        if (!before.present()) throw new IOException("owned fixture disappeared before cleanup; preserve evidence for read-only reconciliation");
        JsonObject input = new JsonObject(); input.addProperty("phase","fixture-remove"); input.addProperty("owner",file.toString()); input.addProperty("device",device); input.addProperty("package","ru.voboost.tests.launcherfixture");
        Path proof = work.resolve("fixture-cleanup-" + (++counter) + ".json");
        try (ProductRun.Operation operation = productRun.reserve(ProductRun.Kind.CONFIGURE,input)) {
            owner.addProperty("state","removing"); RunFiles.write(file,owner); operation.observePriorBoot(before.boot()); operation.dispatch();
            JsonObject request = action("remove-fixture"); request.addProperty("package","ru.voboost.tests.launcherfixture");
            String body = checkBody(request,null,Duration.ofMinutes(2)); fixtureSuccess(body,false);
            FixturePackage after = fixturePackage();
            if (after.present() || !before.boot().equals(after.boot())) throw new IOException("fixed fixture removal lacks independent same-boot absence");
            owner.addProperty("state","removed"); owner.addProperty("removeReceipt",body); RunFiles.write(file,owner);
            JsonObject receipt = new JsonObject(); receipt.addProperty("owner",file.toString()); receipt.addProperty("bootId",after.boot()); receipt.addProperty("package","ru.voboost.tests.launcherfixture"); receipt.addProperty("present",false);
            RunFiles.write(proof,receipt); operation.complete(file,proof);
        }
        return proof;
    }
    private ProductRun.Operation launcherOperation(String phase) throws IOException {
        if (productRun == null || !productRun.owns(owned)) throw new IOException("current product run required for Launcher recipe");
        JsonObject input = new JsonObject(); input.addProperty("phase",phase); input.addProperty("device",device); input.addProperty("manifest",manifest);
        return productRun.reserve(ProductRun.Kind.CONFIGURE,input);
    }
    private Path launcherOrderProof(String phase,LauncherOrder order) throws IOException {
        JsonObject proof = new JsonObject(); proof.addProperty("phase",phase); JsonArray labels = new JsonArray(), files = new JsonArray();
        order.labels().forEach(labels::add); order.evidence().forEach(path -> files.add(path.toString())); proof.add("labels",labels); proof.add("pages",files);
        Path file = work.resolve("launcher-order-" + (++counter) + ".json"); RunFiles.write(file,proof); return file;
    }
    static List<String> movedFixtureOrder(List<String> baseline,List<String> installed) throws IOException {
        if (baseline == null || installed == null || baseline.isEmpty() || baseline.size()>4095 || installed.size()!=baseline.size()+1
                || baseline.stream().anyMatch(label -> label==null || label.isBlank() || label.length()>256 || label.equalsIgnoreCase("Voboost Fixture"))
                || !installed.subList(0,baseline.size()).equals(baseline) || installed.get(installed.size()-1)==null
                || !installed.get(installed.size()-1).equalsIgnoreCase("Voboost Fixture")) throw new IOException("approved fixture was not appended at the actual last Launcher slot");
        List<String> result = new ArrayList<>();result.add(installed.get(installed.size()-1));result.addAll(baseline);return List.copyOf(result);
    }
    static void requireFixtureFirst(List<String> expected,List<String> actual) throws IOException {
        if (expected==null || actual==null || expected.isEmpty() || !expected.get(0).equalsIgnoreCase("Voboost Fixture") || !expected.equals(actual))
            throw new IOException("complete Launcher label order or fixture first position differs");
    }
    static void requireLauncherFixture(Path apk,JsonObject fixture) throws IOException {
        if (apk==null || fixture==null || !fixture.keySet().equals(Set.of("package","label","source","apk")) || !"ru.voboost.tests.launcherfixture".equals(text(fixture,"package"))
                || !"Voboost Fixture".equals(text(fixture,"label")) || !"tests/emulator/agents/agent-launcher/app".equals(text(fixture,"source")) || !"build/test-fixtures/launcher/voboost-launcher-fixture.apk".equals(text(fixture,"apk")) || !apk.isAbsolute() || apk.toString().codePoints().anyMatch(Character::isISOControl)
                || !Files.isRegularFile(apk,java.nio.file.LinkOption.NOFOLLOW_LINKS) || Files.size(apk)<1 || Files.size(apk)>11890860)
            throw new IOException("BLOCKED: declared fixed Voboost Fixture input required before any Launcher experiment mutation");
        // The packaged installer independently admits the closed first-party fixture manifest.
    }
    /** Actual case18 uses our exclusively owned fixture; stock icon pixels remain a separate review. */
    public Path verifyCatalogClassification(JsonObject candidateCatalog,Path fixtureApk,JsonObject fixture) throws Exception {
        requireLauncherFixture(fixtureApk,fixture); requireHealthy(); verifySettingsTab();
        var original=observeRuntime(text(status(),"activeGeneration"));
        JsonObject originalAgents=ru.voboost.tests.agents.AgentSettings.snapshot(candidateCatalog,original);
        if(fixturePackage().present()) throw new IOException("BLOCKED: fixture preexists; classification experiment has no replacement authority");
        setAgent(candidateCatalog,"launcher",true); BootData before=boot(null);
        LauncherOrder baseline; Path baselineProof;
        try(ProductRun.Operation operation=launcherOperation("catalog-stock-baseline")) {
            operation.observePriorBoot(before.id());operation.dispatch();baseline=launcherOrder(openLauncher(before.id()),before.id());
            if(baseline.labels().stream().anyMatch(label->label.equalsIgnoreCase("Voboost Fixture"))) throw new IOException("fixture label preexists");
            baselineProof=launcherOrderProof("catalog-before-fixture",baseline);operation.complete(baselineProof);
        }
        var live=ru.voboost.tests.agents.LauncherPage.identity(observeRuntime(text(status(),"activeGeneration")),StartupObservation.identity(startupProcesses(before.id()),before.id(),LAUNCHER));
        var logsBefore=collectLogs(before.id()); FixtureOwner ownedFixture=installFixture(fixtureApk,fixture);
        FixtureClassification platform=fixtureClassification();platform.requireOwnedUserApplication();
        LauncherOrder installed;Path installedProof;
        try(ProductRun.Operation operation=launcherOperation("catalog-installed-order")) {
            operation.observePriorBoot(before.id());operation.dispatch();installed=launcherOrder(openLauncher(before.id()),before.id());
            movedFixtureOrder(baseline.labels(),installed.labels()); // Full baseline prefix + exactly one appended fixture; no drag.
            installedProof=launcherOrderProof("catalog-fixture-appended",installed);operation.complete(ownedFixture.file(),installedProof);
        }
        JsonObject proof=new JsonObject();proof.addProperty("caseNumber",18);proof.addProperty("bootId",before.id());
        proof.addProperty("baseline",baselineProof.toString());proof.addProperty("installedOrder",installedProof.toString());
        proof.add("platformClassification",new com.google.gson.Gson().toJsonTree(platform));
        String reason="Fresh scanner decisions unavailable";boolean classified=false;
        for(int attempt=0;attempt<3&&!classified;attempt++) {
            var logs=collectLogs(before.id());proof.addProperty("catalogArchive",logs.archive().toString());
            try {
                var current=ru.voboost.tests.agents.LauncherPage.identity(observeRuntime(text(status(),"activeGeneration")),StartupObservation.identity(startupProcesses(before.id()),before.id(),LAUNCHER));
                if(!live.equals(current)) throw new IOException("Launcher process/endpoint changed during classification");
                var classification=ru.voboost.tests.agents.CatalogClassification.read(logs.after(logsBefore),live,platform);
                proof.add("catalogClassification",new com.google.gson.Gson().toJsonTree(classification));classified=true;
                reason="Actual fixed-package UID/flags agree with fresh same-owner scan; excluded packages rejected and complete stock label order retained";
            }catch(IOException unavailable){reason=unavailable.getMessage();}
        }
        Path cleanup=removeFixture(ownedFixture);Path restored;
        try(ProductRun.Operation operation=launcherOperation("catalog-cleanup-order")) {
            operation.observePriorBoot(before.id());operation.dispatch();var after=launcherOrder(openLauncher(before.id()),before.id());
            if(!after.labels().equals(baseline.labels()))throw new IOException("catalog cleanup changed stock label order");
            restored=launcherOrderProof("catalog-after-cleanup",after);operation.complete(cleanup,restored);
        }
        if(fixtureClassification().present())throw new IOException("fixture classification remained present after owned cleanup");
        verifySettingsTab();setAgent(candidateCatalog,"launcher",original.desired("launcher"));
        if(!originalAgents.equals(ru.voboost.tests.agents.AgentSettings.snapshot(candidateCatalog,observeRuntime(text(status(),"activeGeneration")))))throw new IOException("catalog experiment did not restore original agent intent");
        proof.addProperty("semanticFunctionalStatus",classified?"PASS":"BLOCKED");proof.addProperty("reason",reason);
        proof.addProperty("cleanup",cleanup.toString());proof.addProperty("restoredOrder",restored.toString());
        proof.addProperty("visualAcceptance",false);proof.addProperty("functionalAcceptance",false);
        Path file=work.resolve("catalog-classification-"+(++counter)+".json");RunFiles.write(file,proof);return file;
    }

    /** Actual case12 semantic lifecycle. Pixel/icon review remains an explicit separate unavailable result. */
    public Path verifyLauncherPersistence(JsonObject candidateCatalog,Path fixtureApk,JsonObject fixture) throws Exception {
        requireLauncherFixture(fixtureApk,fixture);
        requireHealthy(); verifySettingsTab(); var original = observeRuntime(text(status(),"activeGeneration"));
        JsonObject originalAgents = ru.voboost.tests.agents.AgentSettings.snapshot(candidateCatalog,original);
        if (fixturePackage().present()) throw new IOException("BLOCKED: Voboost Fixture preexists and cannot be replaced by this experiment");
        Path intentFile = work.resolve("launcher-case12-intent-" + (++counter) + ".json"); JsonObject intent = new JsonObject();
        intent.addProperty("sessionId",owned.session().id()); intent.addProperty("serial",device); intent.addProperty("publisher",original.publisher());
        intent.add("originalAgents",originalAgents); intent.addProperty("state","running"); RunFiles.write(intentFile,intent);

        setAgent(candidateCatalog,"launcher",true);
        BootData before = boot(null); LauncherOrder baseline;
        Path baselineProof;
        try (ProductRun.Operation operation = launcherOperation("launcher-baseline")) {
            operation.observePriorBoot(before.id()); operation.dispatch(); baseline = launcherOrder(openLauncher(before.id()),before.id());
            if (baseline.labels().stream().anyMatch(label -> label.equalsIgnoreCase("Voboost Fixture"))) throw new IOException("Voboost Fixture label preexists before this run's fixture ownership");
            baselineProof = launcherOrderProof("before-fixture",baseline); operation.complete(baselineProof);
        }
        FixtureOwner owner = installFixture(fixtureApk,fixture); Path movedProof; LauncherOrder moved;
        List<String> expected;
        try (ProductRun.Operation operation = launcherOperation("launcher-fixture-move")) {
            operation.observePriorBoot(before.id()); operation.dispatch(); LauncherOrder installed = launcherOrder(openLauncher(before.id()),before.id());
            expected = movedFixtureOrder(baseline.labels(),installed.labels());
            LauncherFrame last = installed.last(); int page = last.page().first()/12;
            var target = last.grid().items().get(last.grid().items().size()-1);
            if (!target.label().equalsIgnoreCase("Voboost Fixture") || target.position()!=last.page().count()-1) throw new IOException("fresh last-slot fixture authority differs");
            String nodeId = text(target.node(),"id");
            owned.dragLauncher(ru.voboost.voyemu.UiTarget.selectLauncherDrag(last.tree(),profile,owned.session(),node -> nodeId.equals(text(node,"id"))),page,
                ru.voboost.tests.agents.LauncherGrid.rectangle(target.node()),ru.voboost.tests.agents.LauncherGrid.rectangle(last.grid().items().get(0).node()),ru.voboost.tests.agents.LauncherGrid.rectangle(last.grid().grid()));
            LauncherFrame after = launcherFrame(before.id());
            if (!after.page().beginning() || !after.grid().items().get(0).label().equalsIgnoreCase("Voboost Fixture")) throw new IOException("one actual fixture drag did not establish the first slot; do not replay");
            moved = launcherOrder(after,before.id());
            requireFixtureFirst(expected,moved.labels());
            movedProof = launcherOrderProof("moved-first",moved); operation.complete(owner.file(),movedProof);
        }
        rebootAndVerify(); BootData afterBoot = boot(null);
        if (before.id().equals(afterBoot.id())) throw new IOException("Launcher persistence requires an actual different boot");
        Path retainedProof;
        try (ProductRun.Operation operation = launcherOperation("launcher-after-reboot")) {
            operation.observePriorBoot(afterBoot.id()); operation.dispatch(); LauncherOrder retained = launcherOrder(openLauncher(afterBoot.id()),afterBoot.id());
            requireFixtureFirst(expected,retained.labels());
            retainedProof = launcherOrderProof("after-reboot",retained); operation.complete(retainedProof);
        }
        Path cleaned = removeFixture(owner); Path cleanupOrder;
        try (ProductRun.Operation operation = launcherOperation("launcher-after-cleanup")) {
            operation.observePriorBoot(afterBoot.id()); operation.dispatch(); LauncherOrder current = launcherOrder(openLauncher(afterBoot.id()),afterBoot.id());
            if (!current.labels().equals(baseline.labels())) throw new IOException("owned fixture cleanup did not restore original visible label order");
            cleanupOrder = launcherOrderProof("after-cleanup",current); operation.complete(cleaned,cleanupOrder);
        }
        verifySettingsTab(); setAgent(candidateCatalog,"launcher",original.desired("launcher"));
        if (!originalAgents.equals(ru.voboost.tests.agents.AgentSettings.snapshot(candidateCatalog,observeRuntime(text(status(),"activeGeneration")))))
            throw new IOException("successful Launcher experiment did not restore original desired agent configuration");
        JsonObject proof = new JsonObject(); proof.addProperty("caseNumber",12); proof.addProperty("bootBefore",before.id()); proof.addProperty("bootAfter",afterBoot.id());
        proof.addProperty("baseline",baselineProof.toString()); proof.addProperty("moved",movedProof.toString()); proof.addProperty("retained",retainedProof.toString());
        proof.addProperty("fixtureCleanup",cleaned.toString()); proof.addProperty("cleanupOrder",cleanupOrder.toString());
        intent.addProperty("state","semantic-complete"); RunFiles.write(intentFile,intent); proof.addProperty("intent",intentFile.toString());
        proof.addProperty("semanticPersistenceVerified",true); proof.addProperty("visualAcceptance",false); proof.addProperty("functionalAcceptance",false);
        Path file = work.resolve("launcher-persistence-semantic-" + (++counter) + ".json"); RunFiles.write(file,proof); return file;
    }

    private record LauncherFrame(JsonObject tree, ru.voboost.tests.agents.LauncherPage page,
            ru.voboost.tests.agents.LauncherGrid grid, Path evidence) {}

    private ru.voboost.tests.agents.LauncherPage launcherNative(String expectedBoot) throws Exception {
        var beforeProcess = StartupObservation.identity(startupProcesses(expectedBoot), expectedBoot, LAUNCHER);
        var beforeRuntime = observeRuntime(text(status(), "activeGeneration"));
        var identity = ru.voboost.tests.agents.LauncherPage.identity(beforeRuntime, beforeProcess);
        byte[] bytes = new byte[32]; new java.security.SecureRandom().nextBytes(bytes);
        String nonce = java.util.HexFormat.of().formatHex(bytes);
        JsonObject request = action("launcher-page-observe"); request.addProperty("requestId", nonce);
        String body = checkBody(request, null);
        var result = ru.voboost.tests.agents.LauncherPage.read(body, nonce, identity);
        var afterProcess = StartupObservation.identity(startupProcesses(expectedBoot), expectedBoot, LAUNCHER);
        var afterRuntime = observeRuntime(text(status(), "activeGeneration"));
        if (!beforeProcess.equals(afterProcess) || !identity.equals(ru.voboost.tests.agents.LauncherPage.identity(afterRuntime, afterProcess)))
            throw new IOException("launcher native observation crossed process or current authority");
        Path receipt = work.resolve("launcher-native-" + (++counter) + ".json"); RunFiles.write(receipt, result.receipt());
        return result;
    }
    private LauncherFrame launcherFrame(String expectedBoot) throws Exception {
        var before = launcherNative(expectedBoot);
        JsonObject tree = settingsUi(expectedBoot, System.nanoTime() + Duration.ofSeconds(20).toNanos());
        var after = launcherNative(expectedBoot); after.requireSameLifetime(before);
        if (!after.observation().equals(before.observation())) throw new IOException("Launcher page moved across independent hierarchy observation");
        var grid = ru.voboost.tests.agents.LauncherGrid.read(tree, after);
        Path proof = work.resolve("launcher-frame-" + (++counter) + ".json");
        JsonObject evidence = new JsonObject(); evidence.add("nativeBefore", before.receipt()); evidence.add("ui", tree); evidence.add("nativeAfter", after.receipt());
        RunFiles.write(proof, evidence); return new LauncherFrame(tree, after, grid, proof);
    }
    private LauncherFrame openLauncher(String expectedBoot) throws Exception {
        owned.launch(LAUNCHER, expectedBoot, "primary");
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        JsonObject tree = null; String dock = null;
        while (System.nanoTime() < deadline) {
            tree = settingsUi(expectedBoot, deadline); JsonObject surface = ru.voboost.voyemu.UiEvidence.surface(tree, "primary");
            boolean grid = false;
            for (JsonElement row : surface.getAsJsonArray("nodes")) {
                JsonObject node = row.getAsJsonObject();
                if (LAUNCHER.equals(text(node,"package")) && (LAUNCHER+":id/all_app_view").equals(text(node,"resource_id")) && truth(node,"visible") && truth(node,"enabled")) grid = true;
            }
            if (grid) return launcherFrame(expectedBoot);
            List<JsonObject> docks = new ArrayList<>();
            for (JsonElement row : surface.getAsJsonArray("nodes")) {
                JsonObject node = row.getAsJsonObject();
                if (LAUNCHER.equals(text(node,"package")) && (LAUNCHER+":id/screen_up_all_app_icon").equals(text(node,"resource_id"))) docks.add(node);
            }
            if (docks.size() > 1) throw new IOException("ambiguous observed Launcher dock");
            if (docks.size() == 1) {
                JsonObject node = docks.get(0);
                if (!"android.widget.RadioButton".equals(text(node,"class")) || !truth(node,"visible") || !truth(node,"enabled") || !truth(node,"clickable"))
                    throw new IOException("observed Launcher dock is not actionable");
                dock = text(node,"id"); break;
            }
            Thread.sleep(100);
        }
        if (dock == null || tree == null) throw new IOException("primary Launcher entry was not observed; no coordinate fallback");
        tapNode(tree, dock); // One actual observed dock tap; no retry after an uncertain or ignored input.
        return launcherFrame(expectedBoot);
    }
    private LauncherFrame pageLauncher(LauncherFrame before, boolean next, String expectedBoot) throws Exception {
        owned.pageLauncher(ru.voboost.voyemu.UiTarget.selectLauncherPage(before.tree(), profile, owned.session()), next);
        LauncherFrame after = launcherFrame(expectedBoot);
        int expected = before.page().first() + (next ? 12 : -12);
        if (expected < 0 || expected >= before.page().count() || after.page().first() != expected || after.page().count() != before.page().count())
            throw new IOException("one Launcher page gesture had no exact independently observed effect; do not replay");
        after.page().requireSameLifetime(before.page()); return after;
    }
    private LauncherFrame firstLauncherPage(LauncherFrame frame, String expectedBoot) throws Exception {
        int bound = frame.page().pages();
        if (bound > 12) throw new IOException("Launcher page inventory exceeds bounded fixture program");
        for (int step = 0; step < bound && !frame.page().beginning(); step++) frame = pageLauncher(frame,false,expectedBoot);
        if (!frame.page().beginning()) throw new IOException("actual native first-page boundary was not established");
        return frame;
    }
    private record LauncherOrder(List<String> labels, LauncherFrame last, List<Path> evidence) {
        private LauncherOrder { labels = List.copyOf(labels); evidence = List.copyOf(evidence); }
    }
    private LauncherOrder launcherOrder(LauncherFrame initial, String expectedBoot) throws Exception {
        LauncherFrame frame = firstLauncherPage(initial,expectedBoot); List<String> labels = new ArrayList<>(); List<Path> evidence = new ArrayList<>();
        int count = frame.page().count(), pages = frame.page().pages();
        for (int page = 0; page < pages; page++) {
            if (frame.page().first() != labels.size() || frame.page().count() != count) throw new IOException("Launcher inventory page count or position changed");
            for (var item : frame.grid().items()) labels.add(item.label()); evidence.add(frame.evidence());
            if (page + 1 < pages) frame = pageLauncher(frame,true,expectedBoot);
        }
        if (labels.size() != count) throw new IOException("complete actual Launcher order unavailable");
        return new LauncherOrder(labels,frame,evidence);
    }

    /** Verify the selected installed package and its removal owner without requiring agent readiness. */
    public void requireRemovalIdentity() throws Exception {
        JsonObject inspected = status();
        requireHealthyIdentity(inspected, version);
        if (!"running".equals(text(inspected, "daemonStatus")))
            throw new IOException("selected installed candidate has no running removal owner");
    }

    /**
     * The product restarts its resident injections on every boot: until they re-attach, its own status
     * reports health degraded with a diagnostic such as `status.json injection[1] is unhealthy: state=waiting
     * reason=unrecognized`. Run 13i sampled that interval 14 s after its reboot receipt and rejected a device
     * that reported healthy, unchanged and with the same generation minutes later (cli-484.out vs the direct
     * re-reads recorded in the run reconciliation). A warm reboot of the same session measured the interval at
     * about 31 s (build/diag-13i/settle/settle-timeline.json), so the gate re-samples liveness over a bounded
     * four minutes; identity mismatches stay terminal and an unhealthy deadline is still rejected.
     */
    /** Agent admission verifies candidate identity and the resident owner. */
    public Path requireAgentCandidate() throws Exception {
        JsonObject inspected = status();
        requireAgentCandidateStatus(inspected, version);
        Path evidence = work.resolve("agent-candidate-status-" + (++counter) + ".json");
        RunFiles.write(evidence, inspected);
        awaitRuntimeOwner("Current candidate runtime", text(inspected, "activeGeneration"), boot(null).id());
        return evidence;
    }

    private ru.voboost.tests.agents.RuntimeObservation awaitRuntimeOwner(String stage, String generation, String boot)
            throws Exception {
        String[] publisher = {null};
        return awaitObservation(stage, Duration.ofSeconds(30), (scope, deadline) -> {
            try {
                return observeRuntime(generation, scope, publisher[0], boot);
            } catch (ru.voboost.tests.agents.RuntimeObservation.Stale stale) {
                publisher[0] = stale.publisher();
                throw stale;
            }
        });
    }

    static void requireAgentCandidateStatus(JsonObject inspected, String version) throws IOException {
        requireHealthyIdentity(inspected, version);
        if (!"running".equals(text(inspected, "daemonStatus"))
                || !Set.of("healthy", "degraded").contains(text(inspected, "health"))) {
            throw new IOException("Agent candidate owner is unavailable");
        }
    }

    public void requireHealthy() throws Exception {
        JsonArray attempts = new JsonArray();
        long deadline = System.nanoTime() + Duration.ofMinutes(4).toNanos();
        JsonObject inspected = awaitHealthy(this::status, version, System::nanoTime, deadline, Thread::sleep, attempts);
        if (inspected == null) {
            JsonObject failed = new JsonObject(); failed.add("attempts", attempts.deepCopy());
            RunFiles.write(work.resolve("health-observation-" + (++counter) + ".json"), failed);
            throw new IOException("installed candidate is not independently healthy: " + attempts);
        }
        // A sample that had to be retried is itself evidence: a reboot restarts the resident product and its
        // injections report `waiting`/`unrecognized` until they re-attach, an interval observed on run 13i.
        if (attempts.size() > 1) {
            JsonObject evidence = new JsonObject(); evidence.addProperty("bootId", boot(null).id());
            evidence.add("attempts", attempts.deepCopy()); evidence.add("status", inspected);
            RunFiles.write(work.resolve("health-observation-" + (++counter) + ".json"), evidence);
        }
        observeRuntime(text(inspected, "activeGeneration"));
    }

    /** Bounded re-sampling pause, injectable so the settling rule is testable without a device. */
    @FunctionalInterface interface Pause { void forMillis(long millis) throws InterruptedException; }

    /** One health sample, injectable so the settling rule is testable without a device. */
    @FunctionalInterface interface Sample { JsonObject read() throws Exception; }

    /**
     * Identity (`route`, `installedVersion`, `contentRelation`) is terminal: a different product is never
     * accepted by waiting. Liveness (`health`, `daemonStatus`, `diagnostic`) is re-sampled until it holds or
     * the deadline expires; the accepted status is returned, or null at the deadline.
     */
    static JsonObject awaitHealthy(Sample sample, String version,
            java.util.function.LongSupplier now, long deadlineNanos, Pause pause, JsonArray attempts)
            throws Exception {
        while (true) {
            JsonObject inspected = sample.read();
            requireHealthyIdentity(inspected, version);
            String reason = healthReason(inspected);
            if (reason == null) { attempts.add("healthy"); return inspected; }
            attempts.add(reason);
            if (now.getAsLong() >= deadlineNanos) return null;
            pause.forMillis(5000);
        }
    }

    static void requireHealthyStatus(JsonObject inspected, String version) throws IOException {
        requireHealthyIdentity(inspected, version);
        if (healthReason(inspected) != null) throw new IOException("installed candidate is not independently healthy");
    }

    private static void requireHealthyIdentity(JsonObject inspected, String version) throws IOException {
        if (!"management".equals(text(inspected, "route"))
                || !version.equals(text(inspected, "installedVersion"))
                || !"same".equals(text(inspected, "contentRelation"))) {
            throw new IOException("installed candidate identity differs from the verified package");
        }
    }

    private static String healthReason(JsonObject inspected) {
        if (!"healthy".equals(text(inspected, "health"))) return "health=" + text(inspected, "health");
        if (!"running".equals(text(inspected, "daemonStatus"))) return "daemonStatus=" + text(inspected, "daemonStatus");
        if (inspected.has("diagnostic") && !inspected.get("diagnostic").isJsonNull())
            return "diagnostic=" + text(inspected, "diagnostic");
        return null;
    }

    /** Read the packaged official observer between independent boot fences. */
    public ru.voboost.tests.agents.RuntimeObservation observeRuntime(String generation) throws Exception {
        return observeRuntime(generation, null, null);
    }

    private ru.voboost.tests.agents.RuntimeObservation observeRuntime(String generation, CliProcess.Control control,
            String expectedPublisher) throws Exception {
        return observeRuntime(generation, control, expectedPublisher, null);
    }

    private ru.voboost.tests.agents.RuntimeObservation observeRuntime(String generation, CliProcess.Control control,
            String expectedPublisher, String admissionBoot) throws Exception {
        BootData before = boot(control);
        if (!before.complete() || !before.enforcing()) throw new IOException("runtime observation requires a complete Enforcing boot");
        if (admissionBoot != null && !admissionBoot.equals(before.id()))
            throw new IOException("candidate runtime admission crossed a boot");
        var result = readRuntime(before.id(), generation, control, expectedPublisher, admissionBoot != null);
        BootData after = boot(control);
        if (!before.id().equals(after.id()) || !after.complete() || !after.enforcing())
            throw new IOException("runtime observation crossed a boot");
        return result;
    }

    private ru.voboost.tests.agents.RuntimeObservation readRuntime(String bootId, String generation,
            CliProcess.Control control, String expectedPublisher, boolean admission) throws Exception {
        JsonObject observation = json(output(runRead(List.of("--observe"), Duration.ofSeconds(30), control)));
        Path retained = work.resolve("runtime-official-" + (++counter) + ".json");
        RunFiles.write(retained, observation);
        var identity = new ru.voboost.tests.agents.RuntimeObservation.Identity(
                bootId, version, manifest, catalog, generation);
        var result = admission
                ? ru.voboost.tests.agents.RuntimeObservation.readForAdmission(observation, identity, expectedPublisher)
                : ru.voboost.tests.agents.RuntimeObservation.read(observation, identity, expectedPublisher);
        lastRuntimeOfficial = retained;
        return result;
    }

    public Path captureInitialNativeBoot()throws Exception {
        BootData boot=boot(null);JsonObject ready=nativeRead(client.androidReady(id(),profile,owned.session(),60000),"initial-boot-ready");if(!truth(ready,"ready")||!"android.ready".equals(text(ready,"predicate")))throw new IOException("initial native boot not ready");requireOwnedStatus(object(ready,"last_observation"));
        JsonObject ui=nativeRead(client.multimediaUi(id(),profile,owned.session(),List.of("primary"),"all_attached",null,null,30000),"initial-boot-ui");return captureVerifiedNativeBoot(boot.id(),ui,null);
    }
    private Path captureVerifiedNativeBoot(String expectedBoot,JsonObject beforeUi,CliProcess.Control control)throws Exception {
        if(verifiedBootCaptures.containsKey(expectedBoot))return verifiedBootCaptures.get(expectedBoot);
        if(voyemuRepository==null)throw new IOException("native boot evidence requires the actual source-bound Voyemu artifact root");if(control!=null)control.check();BootData before=boot(control);if(!expectedBoot.equals(before.id())||!before.complete()||!before.enforcing())throw new IOException("boot screenshot lacks settled Enforcing source");
        JsonObject ready=nativeRead(client.androidReady(id(),profile,owned.session(),60000),"boot-capture-readiness");Path readinessEvidence=lastNativeEvidence;if(!truth(ready,"ready")||!"android.ready".equals(text(ready,"predicate")))throw new IOException("boot capture readiness predicate unavailable");requireOwnedStatus(object(ready,"last_observation"));
        Path root=voyemuRepository.resolve(".local/build/tmp");Files.createDirectories(root);Path directory=root.resolve("voboost-boot-"+owned.session().id()+"-"+UUID.randomUUID());
        // Only the SDK age mapping is claimed; host timestamps are not a guest frame clock.
        JsonObject capture=nativeRead(client.capturePrimary(id(),profile,owned.session(),directory),"boot-original-capture");Path captureEvidence=lastNativeEvidence;
        JsonObject afterUi=nativeRead(client.multimediaUi(id(),profile,owned.session(),List.of("primary"),"all_attached",null,null,30000),"boot-capture-ui-after");Path afterUiEvidence=lastNativeEvidence;GuestBootCapture.read(capture,directory,beforeUi,afterUi,profile,owned.session().id(),expectedBoot,Math.toIntExact(androidPid));
        BootData after=boot(control);if(!expectedBoot.equals(after.id())||!after.complete()||!after.enforcing())throw new IOException("boot capture crossed Linux boot/policy");JsonObject status=nativeRead(client.status(id(),profile,owned.session()),"boot-capture-status");Path statusEvidence=lastNativeEvidence;requireOwnedStatus(status);
        JsonObject proof=new JsonObject();proof.addProperty("schemaVersion",2);proof.addProperty("backendContract","native_owned_ready_capture_v1");proof.addProperty("bootId",expectedBoot);proof.addProperty("profile",profile);proof.addProperty("sessionId",owned.session().id());proof.addProperty("androidPid",androidPid);proof.addProperty("bootBefore",before.evidence().toString());proof.addProperty("bootAfter",after.evidence().toString());proof.addProperty("captureDirectory",directory.toString());proof.addProperty("readiness",readinessEvidence.toString());Path beforeUiEvidence=nativeUiEvidence.get(expectedBoot+"|"+text(beforeUi,"observation_id"));if(beforeUiEvidence==null)throw new IOException("original before UI wire observation unavailable");proof.addProperty("beforeUi",beforeUiEvidence.toString());proof.addProperty("afterUi",afterUiEvidence.toString());proof.addProperty("capture",captureEvidence.toString());proof.addProperty("statusAfter",statusEvidence.toString());
        Path file=work.resolve("verified-boot-"+(++counter)+".json");RunFiles.write(file,proof);GuestBootLedger.readCapture(file);verifiedBootCaptures.put(expectedBoot,file);return file;
    }
    /** A missed or incomplete boot stays missing; retained screenshots never manufacture coverage. */
    public Path terminalBootEvidence()throws Exception {
        JsonObject proof=new JsonObject();proof.addProperty("caseNumber",26);proof.addProperty("schemaVersion",1);proof.addProperty("backendContract","native_owned_ready_capture_v1");proof.addProperty("sessionId",owned.session().id());proof.addProperty("profile",profile);JsonArray observations=new JsonArray(),captures=new JsonArray();boolean complete=!observedReadyBoots.isEmpty();
        for(var entry:observedReadyBoots.entrySet()){JsonObject row=new JsonObject();row.addProperty("bootId",entry.getKey());row.addProperty("observation",entry.getValue().toString());observations.add(row);Path captured=verifiedBootCaptures.get(entry.getKey());if(captured==null)complete=false;else{GuestBootLedger.readCapture(captured);captures.add(captured.toString());}}
        proof.add("observedBoots",observations);proof.add("captures",captures);proof.addProperty("semanticFunctionalStatus",complete?"PASS":"BLOCKED");proof.addProperty("functionalAcceptance",complete);proof.addProperty("reason",complete?"All independently observed ready boots, including monitored installer boots, have current owned native readiness, Enforcing and an original primary screenshot between current hierarchies":"At least one independently observed boot lacks its complete original capture/readiness proof");Path file=work.resolve("every-guest-boot-"+(++counter)+".json");RunFiles.write(file,proof);return file;
    }

    private void verifyNativeBoot(String boot, CliProcess.Control control) throws Exception {verifyNativeBoot(boot,control,null);}
    private void verifyNativeBoot(String boot,CliProcess.Control control,JsonObject fullRestart)throws Exception {

        JsonObject android = awaitNativeBootDelivery(boot, control, fullRestart);
        JsonObject home = object(android, "home");
        if (!truth(android, "guest_acknowledged") || !"visible_on_primary".equals(text(home, "status"))
                || !"com.qinggan.app.launcher/com.qinggan.app.launcher.activity.MainActivity".equals(text(home, "component"))
                || integer(home, "display_id") != 0 || !"both_guest_displays_ready".equals(text(object(android, "displays"), "status")))
            throw new IOException("native required primary HOME or both-display delivery proof unavailable");
        JsonObject ui = awaitNativeLauncherUi(boot, control);
        control.check();
        requireOwnedStatus(nativeRead(client.status(id(), profile, owned.session()), "native-status-fence"));
        captureVerifiedNativeBoot(boot, ui, control);
    }

    private JsonObject awaitNativeLauncherUi(String boot, CliProcess.Control control) throws Exception {
        for (int attempt = 0; attempt < 3; attempt++) {
            control.check();
            int timeout = (int) Math.max(1, Math.min(30000, control.remaining().toMillis()));
            JsonObject ui = nativeRead(client.multimediaUi(id(), profile, owned.session(), List.of("primary"),
                    "all_attached", null, null, timeout), "native-ui", Duration.ofMillis(timeout));
            if (!owned.session().id().equals(text(ui, "session_id")) || !profile.equals(text(ui, "profile"))
                    || !boot.equals(text(ui, "boot_id")) || !"voyemu.ui.v1".equals(text(ui, "format"))
                    || integer(ui, "android_pid") != androidPid) {
                throw new IOException("fresh primary UI identity differs");
            }
            int primary = 0;
            boolean observed = false;
            boolean incomplete = false;
            for (JsonElement value : ui.getAsJsonArray("screens")) {
                JsonObject screen = value.getAsJsonObject();
                if (!"primary".equals(text(screen, "screen"))) continue;
                ++primary;
                String status = text(screen, "status");
                if (!Set.of("ok", "partial").contains(status)) throw new IOException("primary UI read unavailable");
                var windows = new java.util.HashSet<String>();
                for (JsonElement window : screen.getAsJsonArray("windows")) {
                    JsonObject entry = window.getAsJsonObject();
                    if (LAUNCHER.equals(text(entry, "package"))
                            && "com.qinggan.app.launcher/com.qinggan.app.launcher.activity.MainActivity".equals(text(entry, "title"))) {
                        if (truth(entry, "read_complete")) windows.add(text(entry, "id"));
                        else if ("partial".equals(status)) incomplete = true;
                    }
                }
                for (JsonElement node : screen.getAsJsonArray("nodes")) {
                    JsonObject entry = node.getAsJsonObject();
                    if (LAUNCHER.equals(text(entry, "package")) && truth(entry, "present") && truth(entry, "visible")
                            && windows.contains(text(entry, "window_id"))) observed = true;
                }
            }
            if (primary == 1 && observed) return ui;
            if (primary != 1 || !incomplete) break;
            if (attempt < 2) Thread.sleep(250);
        }
        throw new IOException("required visible Launcher nodes not positively observed");
    }

    private JsonObject awaitNativeBootDelivery(String boot, CliProcess.Control control, JsonObject fullRestart) throws Exception {
        long deadline = System.nanoTime() + Math.min(control.remaining().toNanos(), Duration.ofSeconds(60).toNanos());
        do {
            control.check();
            int timeoutMs = (int) Math.max(1, Math.min(2000, control.remaining().toMillis()));
            JsonObject ready = nativeRead(client.androidReady(id(), profile, owned.session(), timeoutMs), "native-ready");
            if (!"android.ready".equals(text(ready, "predicate"))) throw new IOException("native Android readiness predicate differs");
            JsonObject status = object(ready, "last_observation");
            requireOwnedStatus(status);
            JsonObject android = object(object(status, "deliveries"), "android");
            boolean currentBoot = fullRestart == null ? machineStatesBoot(android, boot) : fullRestartStatesBoot(fullRestart, status, boot);
            if (currentBoot && truth(ready, "ready")) return android;
            Thread.sleep(250);
        } while (System.nanoTime() - deadline < 0);
        throw new IOException("native Android readiness did not attest boot " + boot);
    }

    /**
     * True when the machine's own Android delivery states the expected boot identity.
     *
     * The machine reports a guest boot change in two deliveries, and which one carries the boot depends on
     * who caused the reboot. A reboot the machine did not initiate -- the installer's own device reboot --
     * is published as {@code android.external_reboot}. A reboot the machine itself initiated
     * ({@code android_reboot}, the harness' explicit reboot) is published as {@code multimedia.reboot} with
     * {@code status:completed}, while {@code external_reboot} deliberately keeps pointing at the last reboot
     * observed outside the machine's control: the warm path of the emulator's {@code Session::android_reboot}
     * clears the retained transport boot so the following re-observation is not counted again as an external
     * change, and only a change against a non-empty identity writes {@code external_reboot}. Run 13h reached
     * the host-owned reboot after a successful vehicle-only install and configuration: the machine published
     * the new boot {@code ae543b43} in {@code reboot}, {@code audio_startup}, {@code canbus_startup} and
     * {@code interguest_network} while {@code external_reboot} still held the previous installer-triggered
     * boot {@code cf375dda}. The delivery-level {@code android.generation} is not usable as a freshness
     * anchor either: it is only refreshed on the external-change path and still said 3 while those deliveries
     * said generation 4.
     *
     * Both statements remain machine-authored reboot records for a boot the machine verified under its own
     * ownership fences, so either one proves the live Android session runs the expected boot. Nothing is
     * accepted on absence: an empty expected boot and a missing delivery both fail closed.
     */
    static boolean fullRestartStatesBoot(JsonObject receipt,JsonObject status,String boot) {
        JsonObject after=object(receipt,"after"),expectedQnx=object(after,"qnx"),qnx=object(object(status,"deliveries"),"qnx");
        if(!uuid(boot)||!boot.equals(text(after,"android_boot_id"))||!"completed".equals(text(receipt,"status"))||!truth(receipt,"guest_acknowledged")||!truth(receipt,"authority_retained")||!truth(receipt,"old_processes_stopped")
                ||!"android_qnx_cold_boot_changed".equals(text(receipt,"scope"))||!"cold".equals(text(receipt,"mode"))||text(receipt,"session_id").isBlank()||!text(receipt,"session_id").equals(text(status,"session_id"))
                ||integer(after,"android_pid")<1||integer(after,"android_pid")!=integer(status,"android_pid")||integer(object(object(status,"deliveries"),"android"),"pid")!=integer(status,"android_pid")||!"owned_cold_incarnation".equals(text(expectedQnx,"kind"))||integer(expectedQnx,"qemu_pid")<1||integer(expectedQnx,"qemu_pid")!=integer(status,"qnx_pid")||integer(qnx,"pid")!=integer(status,"qnx_pid"))return false;
        String generation=qnx.has("generation")&&qnx.get("generation").isJsonPrimitive()?qnx.get("generation").getAsString():"";
        return generation.matches("[1-9][0-9]*")&&generation.equals(text(expectedQnx,"generation"));
    }
    private static boolean machineStatesBoot(JsonObject android, String boot) {
        if (boot.isEmpty()) return false;
        JsonObject reboot = object(android, "reboot");
        return boot.equals(text(object(android, "external_reboot"), "boot_id"))
            || ("completed".equals(text(reboot, "status")) && boot.equals(text(reboot, "boot_id")));
    }

    private void requireOwnedStatus(JsonObject status) throws IOException {
        JsonObject android = object(object(status, "deliveries"), "android");
        if (!owned.session().id().equals(text(status, "session_id")) || !truth(status, "ok") || !falseValue(status, "stopped")
                || androidPid != integer(status, "android_pid") || androidPid != integer(android, "pid") || !device.equals(text(android, "serial")))
            throw new IOException("owned native endpoint changed");
    }
    private BootData boot(CliProcess.Control control) throws Exception {
        String body = checkBody(action("boot-observation"), control);
        Matcher match = BOOT.matcher(body);
        if (!match.matches() || !uuid(match.group(2))) throw new IOException("boot observation framing differs");
        String raw = match.group(4);
        if (raw.getBytes(StandardCharsets.UTF_8).length > 16384 || (!raw.isEmpty() && !raw.endsWith("\n")))
            throw new IOException("DeviceReport observation truncated or oversized");
        JsonObject operation = raw.isEmpty() ? new JsonObject() : json(raw);
        if (!raw.isEmpty()) Files.writeString(work.resolve("device-report-" + (++counter) + ".json"), raw);
        Path observed=work.resolve("boot-observation-"+(++counter)+".txt");Files.writeString(observed,body);boolean complete="1".equals(match.group(1)),enforcing="Enforcing".equals(match.group(3));if(complete&&enforcing)observedReadyBoots.putIfAbsent(match.group(2),observed);
        return new BootData(match.group(2),complete,enforcing,operation,observed);
    }
    private String checkBody(JsonObject request, CliProcess.Control control) throws Exception { return checkBody(request,control,Duration.ofSeconds(30)); }
    private String checkBody(JsonObject request, CliProcess.Control control,Duration timeout) throws Exception {
        if (control != null) control.check();
        Path input = work.resolve("request-" + (++counter) + ".json"); Files.writeString(input, request.toString() + "\n");
        List<String> arguments = List.of("--emulator-check", input.toString());
        CliProcess.Result observed = "boot-observation".equals(text(request, "action"))
                ? runRead(arguments, timeout, control) : run(arguments, timeout, null, control);
        JsonObject result = json(output(observed));
        if (!result.keySet().equals(Set.of("schemaVersion", "serial", "body")) || integer(result, "schemaVersion") != 1
                || !device.equals(text(result, "serial"))) throw new IOException("installer observation envelope identity differs");
        return text(result, "body");
    }
    private CliProcess.Result runRead(List<String> arguments, Duration timeout, CliProcess.Control control) throws Exception {
        CliProcess.Result result = run(arguments, timeout, null, control);
        if (result.exit() != 0 && Files.size(result.err()) <= 65536
                && Files.readString(result.err()).contains("failed to write to fdevent interrupt fd: Bad file descriptor")) {
            // The linked ADB process can fail during teardown after a read. Get a fresh response.
            return run(arguments, timeout, null, control);
        }
        return result;
    }

    private CliProcess.Result run(List<String> arguments, Duration timeout, CliProcess.Listener listener, CliProcess.Control control) throws Exception {
        if (control != null) control.check();
        List<String> command = command(arguments);
        int index = ++counter; Path out = work.resolve("cli-" + index + ".out"), err = work.resolve("cli-" + index + ".err");
        if (Set.of("--install", "--update", "--uninstall", "--mode").contains(arguments.get(0))
                || arguments.get(0).equals("--emulator-check") && arguments.size() == 2
                    && Set.of("observe-settings-uninstall", "fault-window", "uninstall-normal").contains(text(RunFiles.read(Path.of(arguments.get(1))), "action"))) {
            if (productRun == null || productRun.pending() == null) throw new IOException("product process lacks a durable operation");
            productRun.pending().process(command, out, err);
        }
        CliProcess.Listener observed = listener == null ? (stream, line, scope) -> null : listener;
        return control == null ? CliProcess.run(command, out, err, timeout, observed) : CliProcess.run(command, out, err, timeout, observed, control);
    }
    private JsonObject nativeRead(VoyemuClient.Attempt attempt, String phase) throws Exception {
        return nativeRead(attempt, phase, Duration.ofSeconds(70));
    }
    private JsonObject nativeRead(VoyemuClient.Attempt attempt, String phase, Duration timeout) throws Exception {
        VoyemuClient.Reply reply = client.execute(attempt, timeout);
        JsonObject evidence = new JsonObject(); evidence.addProperty("state", reply.state().name()); evidence.add("response", reply.response()); evidence.addProperty("diagnostic", reply.diagnostic());
        lastNativeEvidence=work.resolve(phase + "-" + (++counter) + ".json");Files.writeString(lastNativeEvidence,evidence.toString()+"\n");
        if (reply.state() != VoyemuClient.State.RESPONSE || !truth(reply.response(), "ok")) throw nativeObservationFailure(phase, lastNativeEvidence, reply);
        JsonObject result=VoyemuClient.readResult(attempt,object(reply.response(),"result"));if("voyemu.ui.v1".equals(text(result,"format")))nativeUiEvidence.put(text(result,"boot_id")+"|"+text(result,"observation_id"),lastNativeEvidence);return result;
    }
    static IOException nativeObservationFailure(String phase, Path evidence, VoyemuClient.Reply reply) {
        StringBuilder message = new StringBuilder("native observation unavailable: ").append(phase)
            .append("; state=").append(reply.state()).append("; evidence=").append(evidence);
        if (reply.state() == VoyemuClient.State.RESPONSE) {
            JsonObject error = object(reply.response(), "error");
            for (String field : List.of("code", "outcome", "message")) {
                String value = text(error, field).replaceAll("\\p{Cntrl}", " ");
                if (!value.isEmpty()) message.append("; ").append(field).append('=')
                    .append(value, 0, Math.min(value.length(), field.equals("message") ? 512 : 64));
            }
        }
        return new IOException(message.toString());
    }
    private static String output(CliProcess.Result result) throws IOException {
        if (result.exit() != 0) throw new IOException("packaged CLI failed; raw stdout/stderr retained at " + result.out().getParent());
        return Files.readString(result.out());
    }
    static JsonObject absenceReceipt(String raw, boolean preparation, String before) throws IOException {
        if (raw.length() > 4096) throw new IOException("absence receipt exceeds bound");
        JsonObject receipt = new JsonObject();
        try (JsonReader reader = new JsonReader(new StringReader(raw))) {
            reader.setLenient(false);
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                if (receipt.has(key)) throw new IOException("duplicate absence receipt field");
                if ("checks".equals(key)) {
                    JsonArray checks = new JsonArray();
                    reader.beginArray();
                    while (reader.hasNext()) {
                        if (reader.peek() != JsonToken.STRING) throw new IOException("absence check must be a string");
                        checks.add(reader.nextString());
                    }
                    reader.endArray(); receipt.add(key, checks);
                } else {
                    if (reader.peek() != JsonToken.STRING) throw new IOException("absence identity must be a string");
                    receipt.addProperty(key, reader.nextString());
                }
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IOException("trailing absence receipt input");
        } catch (IllegalStateException | IllegalArgumentException error) { throw new IOException("malformed absence receipt", error); }
        if (preparation) requirePreparationAbsence(receipt); else requireAbsence(receipt);
        if (before.equals(text(receipt, "bootId")) && (!preparation || !receipt.has("state") || !"already-absent".equals(text(receipt, "state"))))
            throw new IOException("uninstall receipt did not prove a new boot");
        return receipt;
    }
    static void requirePreparationAbsence(JsonObject receipt) throws IOException { validateAbsence(receipt, true); }
    static void requireAbsence(JsonObject receipt) throws IOException { validateAbsence(receipt, false); }
    private static void validateAbsence(JsonObject receipt, boolean preparation) throws IOException {
        boolean stateReceipt = preparation && receipt.has("state");
        Set<String> expected = stateReceipt ? Set.of("state", "bootId", "checks") : Set.of("transactionId", "bootId", "checks");
        if (!receipt.keySet().equals(expected) || !uuid(text(receipt, "bootId"))
                || (stateReceipt && !Set.of("already-absent", "emergency-removed").contains(text(receipt, "state")))
                || (!stateReceipt && !text(receipt, "transactionId").matches("[0-9a-f]{64}")))
            throw new IOException("absence receipt identity differs");
        if (!receipt.has("checks") || !receipt.get("checks").isJsonArray() || receipt.getAsJsonArray("checks").size() != ABSENCE.size())
            throw new IOException("complete Android absence checks missing");
        for (int index = 0; index < ABSENCE.size(); ++index)
            if (!receipt.getAsJsonArray("checks").get(index).isJsonPrimitive()
                    || !receipt.getAsJsonArray("checks").get(index).getAsJsonPrimitive().isString()
                    || !ABSENCE.get(index).equals(receipt.getAsJsonArray("checks").get(index).getAsString()))
                throw new IOException("absence check order or type differs");
    }
    static JsonObject recoveryEvent(String line) throws IOException {
        if (!line.startsWith("VBI_CACHE_RECOVERY_BOOT|") || line.length() > 1024) throw new IOException("recovery event bound differs");
        String raw = line.substring("VBI_CACHE_RECOVERY_BOOT|".length()); JsonObject value = json(raw);
        if (!raw.equals(value.toString()) || !value.keySet().equals(Set.of("schemaVersion", "operation", "attemptToken", "priorBootId", "bootId", "state"))
                || integer(value, "schemaVersion") != 1 || !"cache-recovery-boot".equals(text(value, "operation"))
                || !text(value, "attemptToken").matches("[0-9a-f]{64}") || !uuid(text(value, "priorBootId")) || !uuid(text(value, "bootId"))
                || text(value, "priorBootId").equals(text(value, "bootId")) || !"waiting".equals(text(value, "state")))
            throw new IOException("recovery event identity differs");
        return value;
    }
    static boolean emulator(String device) {
        if (device == null || !device.matches("emulator-[0-9]{4,5}")) return false;
        int port = Integer.parseInt(device.substring(9));
        return port >= 1024 && port <= 65534 && port % 2 == 0 && device.equals("emulator-" + port);
    }
    private static JsonObject action(String name) { JsonObject value = new JsonObject(); value.addProperty("action", name); return value; }
    private static String id() { return UUID.randomUUID().toString().replace("-", ""); }
    private static boolean uuid(String value) { return value != null && value.matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}"); }
    private static JsonObject json(String value) throws IOException { try { return JsonParser.parseString(value).getAsJsonObject(); } catch (RuntimeException error) { throw new IOException("malformed CLI observation", error); } }
    private static JsonObject object(JsonObject value, String name) { return value != null && value.has(name) && value.get(name).isJsonObject() ? value.getAsJsonObject(name) : new JsonObject(); }
    private static String text(JsonObject value, String name) { return value != null && value.has(name) && value.get(name).isJsonPrimitive() && value.getAsJsonPrimitive(name).isString() ? value.get(name).getAsString() : ""; }
    private static long integer(JsonObject value, String name) { try { return value != null && value.has(name) && value.get(name).isJsonPrimitive() && value.getAsJsonPrimitive(name).isNumber() && value.get(name).getAsString().matches("[0-9]{1,10}") ? value.get(name).getAsLong() : -1; } catch (RuntimeException invalid) { return -1; } }
    private static boolean truth(JsonObject value, String name) { return value != null && value.has(name) && value.get(name).isJsonPrimitive() && value.getAsJsonPrimitive(name).isBoolean() && value.get(name).getAsBoolean(); }
    private static boolean falseValue(JsonObject value, String name) { return value != null && value.has(name) && value.get(name).isJsonPrimitive() && value.getAsJsonPrimitive(name).isBoolean() && !value.get(name).getAsBoolean(); }
}
