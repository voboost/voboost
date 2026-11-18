package ru.voboost.tests;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import ru.voboost.voyemu.MachineLease;
import ru.voboost.voyemu.OwnedSession;
import ru.voboost.voyemu.VoyemuClient;

/** Durable single-writer scope for one product run and its guest mutations. */
public final class ProductRun implements AutoCloseable {
    public enum Kind { INSTALL, UPDATE, UNINSTALL, RESTORE, REBOOT, CONFIGURE }
    private final Path work, owner;
    private final MachineLease lease;
    private final String id;
    private final JsonObject record;
    private OwnedSession session;
    private Operation active;
    private boolean closed, startNotExecuted, guiPending, retirementOnly;
    private boolean failedInstallHistory, failedInstallCleanup;
    private int sequence;
    private Path failedInstallEvidence;

    private ProductRun(Path work, MachineLease lease, String id, JsonObject record) {
        this.work = work; this.lease = lease; this.id = id; this.record = record;
        owner = lease.directory().resolve("owner.json");
    }

    public static ProductRun open(Path leaseRoot, Path machine, Path work) throws IOException {
        if (!work.isAbsolute()) throw new IOException("absolute run directory required");
        Path canonicalWork = RunFiles.directory(work).toRealPath();
        if (Files.exists(canonicalWork.resolve("run.json"), LinkOption.NOFOLLOW_LINKS)
                || Files.exists(canonicalWork.resolve("operations"), LinkOption.NOFOLLOW_LINKS))
            throw new IOException("existing run directory requires explicit resume");
        MachineLease lease = MachineLease.acquire(leaseRoot, machine);
        try {
            Path owner = lease.directory().resolve("owner.json");
            if (Files.exists(owner, LinkOption.NOFOLLOW_LINKS)) {
                JsonObject previous = RunFiles.read(owner);
                if (!previous.keySet().equals(Set.of("schemaVersion", "runId", "machine", "work", "state"))
                        || !previous.get("schemaVersion").isJsonPrimitive()
                        || !previous.getAsJsonPrimitive("schemaVersion").isNumber()
                        || !"1".equals(previous.get("schemaVersion").getAsString())
                        || !previous.get("runId").isJsonPrimitive() || !previous.getAsJsonPrimitive("runId").isString()
                        || !previous.get("runId").getAsString().matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}")
                        || !previous.get("work").isJsonPrimitive() || !previous.getAsJsonPrimitive("work").isString()
                        || !Path.of(previous.get("work").getAsString()).isAbsolute()
                        || !previous.get("machine").isJsonPrimitive()
                        || !previous.getAsJsonPrimitive("machine").isString()
                        || !machine.toRealPath().toString().equals(previous.get("machine").getAsString())
                        || !previous.get("state").isJsonPrimitive()
                        || !previous.getAsJsonPrimitive("state").isString()
                        || !"complete".equals(previous.get("state").getAsString()))
                    throw new IOException("BLOCKED: previous machine run requires reconciliation; ownership retained at " + owner);
            }
            String id = UUID.randomUUID().toString();
            JsonObject record = new JsonObject(); record.addProperty("schemaVersion", 1);
            record.addProperty("runId", id); record.addProperty("machine", machine.toRealPath().toString());
            record.addProperty("work", canonicalWork.toString()); record.addProperty("state", "starting");
            ProductRun run = new ProductRun(canonicalWork, lease, id, record);
            RunFiles.directory(canonicalWork.resolve("operations"));
            run.persist();
            return run;
        } catch (IOException | RuntimeException failure) {
            try { lease.close(); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    /** Reopen the same run for observation and reconciliation, never to replay its dispatched input. */
    public static ProductRun resume(Path leaseRoot, Path machine, Path work) throws IOException {
        return resume(leaseRoot, machine, work, false);
    }

    /** Observation/import only; cannot dispatch product commands or start/stop a session. */
    public static ProductRun resumeForRetirement(Path leaseRoot, Path machine, Path work)
            throws IOException {
        return resume(leaseRoot, machine, work, true);
    }

    private static ProductRun resume(Path leaseRoot, Path machine, Path work,
            boolean retirementOnly) throws IOException {
        Path canonicalWork = work.toRealPath();
        RunFiles.directory(canonicalWork);
        MachineLease lease = MachineLease.acquire(leaseRoot, machine);
        try {
            if (!retirementOnly && Files.exists(canonicalWork.resolve("retirement.json"),
                    LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("retired failed fixture requires a verified fresh baseline");
            }
            JsonObject record = RunFiles.read(lease.directory().resolve("owner.json"));
            JsonObject local = RunFiles.read(canonicalWork.resolve("run.json"));
            Path guiOwner = canonicalWork.resolve("gui-owner.json");
            if (Files.exists(guiOwner, LinkOption.NOFOLLOW_LINKS)) {
                JsonObject gui = RunFiles.read(guiOwner);
                if (!gui.keySet().equals(Set.of("schemaVersion", "runId", "sessionId", "scope", "state", "evidence"))
                        || !gui.get("schemaVersion").isJsonPrimitive() || !gui.getAsJsonPrimitive("schemaVersion").isNumber()
                        || !"1".equals(gui.get("schemaVersion").getAsString()) || !record.get("runId").equals(gui.get("runId"))
                        || !"complete".equals(gui.get("state").getAsString()))
                    throw new IOException("BLOCKED: retained GUI owner requires native app/device read-only reconciliation; generic resume cannot replay it");
                for (String key : java.util.List.of("scope", "evidence")) {
                    Path relative = Path.of(gui.get(key).getAsString());
                    if (relative.isAbsolute() || !canonicalWork.resolve(relative).normalize().startsWith(canonicalWork)
                            || !Files.exists(canonicalWork.resolve(relative), LinkOption.NOFOLLOW_LINKS)) throw new IOException("retained GUI evidence differs");
                }
            }

            if (!local.keySet().equals(Set.of("schemaVersion", "runId", "machine", "work", "state")))
                throw new IOException("invalid retained local run shape");
            if (!record.keySet().equals(Set.of("schemaVersion", "runId", "machine", "work", "state"))
                    || !record.get("runId").isJsonPrimitive() || !record.getAsJsonPrimitive("runId").isString()
                    || !record.get("runId").getAsString().matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}"))
                throw new IOException("invalid retained run identity");
            for (String key : java.util.List.of("schemaVersion", "runId", "machine", "work")) {
                if (!record.get(key).equals(local.get(key))) throw new IOException("retained run identities differ");
            }
            if (!record.get("machine").isJsonPrimitive() || !record.getAsJsonPrimitive("machine").isString()
                    || !machine.toRealPath().toString().equals(record.get("machine").getAsString())
                    || !record.get("work").isJsonPrimitive() || !record.getAsJsonPrimitive("work").isString()
                    || !canonicalWork.toString().equals(record.get("work").getAsString())
                    || !record.get("schemaVersion").isJsonPrimitive() || !record.getAsJsonPrimitive("schemaVersion").isNumber()
                    || !"1".equals(record.get("schemaVersion").getAsString())
                    || !record.get("state").isJsonPrimitive() || !record.getAsJsonPrimitive("state").isString()
                    || !(retirementOnly ? Set.of("starting", "ready", "operation", "retired-failed")
                            : Set.of("starting", "ready", "operation"))
                            .contains(record.get("state").getAsString()))
                throw new IOException("retained run scope differs or is already complete");
            ProductRun run = new ProductRun(canonicalWork, lease, record.get("runId").getAsString(), record);
            run.retirementOnly = retirementOnly;
            java.util.List<Path> operations;
            try (var entries = Files.list(canonicalWork.resolve("operations"))) { operations = entries.sorted().toList(); }
            for (Path directory : operations) {
                if (!directory.getFileName().toString().matches("[0-9]{6}") || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))
                    throw new IOException("invalid retained operation directory");
                if (Integer.parseInt(directory.getFileName().toString()) != run.sequence + 1)
                    throw new IOException("retained operation history is incomplete");
                if (run.active != null) throw new IOException("nonterminal operation has later descendants");
                JsonObject operation = RunFiles.read(directory.resolve("operation.json"));
                Set<String> base = Set.of("schemaVersion", "runId", "sessionId", "kind", "input", "state");
                Set<String> verified = Set.of("schemaVersion", "runId", "sessionId", "kind", "input", "state", "evidence");
                if (!operation.keySet().equals(base) && !operation.keySet().equals(verified))
                    throw new IOException("retained operation shape differs");
                if (!operation.get("schemaVersion").isJsonPrimitive() || !operation.getAsJsonPrimitive("schemaVersion").isNumber()
                        || !"1".equals(operation.get("schemaVersion").getAsString()))
                    throw new IOException("retained operation schema differs");
                if (!run.id.equals(operation.get("runId").getAsString()) || !operation.get("sessionId").isJsonPrimitive()
                        || !operation.getAsJsonPrimitive("sessionId").isString()
                        || !operation.get("sessionId").getAsString().matches("[0-9a-f]{32}")
                        || !operation.has("input") || !operation.get("input").isJsonObject())
                    throw new IOException("retained product operation identity differs");
                Kind.valueOf(operation.get("kind").getAsString());
                String state = operation.get("state").getAsString();
                if (!Set.of("reserved", "dispatched", "unknown", "verified", "failed-reconciled", "not-dispatched", "rejected").contains(state))
                    throw new IOException("retained product operation state differs");
                if (state.equals("verified") || state.equals("failed-reconciled") || state.equals("rejected")) {
                    if (!operation.has("evidence") || !operation.get("evidence").isJsonArray()
                            || operation.getAsJsonArray("evidence").isEmpty()) throw new IOException("terminal evidence missing");
                    for (var entry : operation.getAsJsonArray("evidence")) {
                        if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) throw new IOException("invalid terminal evidence");
                        Path relative = Path.of(entry.getAsString());
                        Path file = canonicalWork.resolve(relative).normalize();
                        if (relative.isAbsolute() || !file.startsWith(canonicalWork) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                                || !file.toRealPath().startsWith(canonicalWork)) throw new IOException("terminal evidence scope differs");
                    }
                }
                Kind kind = Kind.valueOf(operation.get("kind").getAsString());
                if (run.failedInstallCleanup && kind != Kind.UNINSTALL) {
                    throw new IOException("failed install has a non-cleanup descendant");
                }
                if (state.equals("rejected")) {
                    if (kind != Kind.UNINSTALL) throw new IOException("only uninstall supports pre-admission rejection");
                    ru.voboost.tests.installer.cli.InstallerRemovalRejection.require(
                            RunFiles.read(run.evidence(operation.getAsJsonArray("evidence").get(0).getAsString())),
                            operation.getAsJsonObject("input"));
                }
                if (state.equals("failed-reconciled")) {
                    if (kind != Kind.INSTALL && kind != Kind.UPDATE) {
                        throw new IOException("only installation may have failed reconciliation");
                    }
                    Path proof = run.evidence(operation.getAsJsonArray("evidence").get(0).getAsString());
                    ru.voboost.tests.installer.cli.FailedInstallReconciliation.require(
                            RunFiles.read(proof), operation.getAsJsonObject("input"));
                    run.failedInstallEvidence = proof;
                    run.failedInstallHistory = true;
                    run.failedInstallCleanup = true;
                } else if (kind == Kind.UNINSTALL && state.equals("verified")) {
                    if (run.failedInstallCleanup) {
                        ru.voboost.tests.installer.cli.FailedInstallReconciliation.requireNormalCleanup(
                                run.evidence(operation.getAsJsonArray("evidence").get(0).getAsString()),
                                operation.getAsJsonObject("input"));
                    }
                    run.failedInstallCleanup = false;
                }
                if (Set.of("reserved", "dispatched", "unknown").contains(state)) {

                    Operation pending = run.new Operation(directory.resolve("operation.json"), operation);
                    pending.recovered = true;
                    pending.dispatched = !state.equals("reserved");
                    run.active = pending;
                }
                run.sequence = Math.max(run.sequence, Integer.parseInt(directory.getFileName().toString()));
            }
            return run;
        } catch (IOException | RuntimeException failure) {
            try { lease.close(); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            if (failure instanceof RuntimeException) throw new IOException("malformed retained run", failure);
            throw failure;
        }
    }

    /** Registers an already completed operator stop; never resolves product operations as PASS. */
    public synchronized Path importExistingStop(OwnedSession stopped) throws IOException {
        if (closed || !retirementOnly || !failedInstallHistory || active == null
                || active.kind() != Kind.UNINSTALL || !active.recovered
                || !"unknown".equals(active.value.get("state").getAsString())
                || !active.input().has("phase")
                || !active.input().get("phase").isJsonPrimitive()
                || !active.input().getAsJsonPrimitive("phase").isString()
                || !"failed-install-cleanup".equals(active.input().get("phase").getAsString())
                || Files.exists(work.resolve("gui-owner.json"), LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("retirement requires the retained failed install/unknown cleanup");
        }
        JsonObject stop = stopped.retainedStopProof(Path.of(record.get("machine").getAsString()),
                work.resolve("session"));
        if (!active.value.get("sessionId").getAsString().equals(stop.get("session_id").getAsString())) {
            throw new IOException("retained stop belongs to another product session");
        }
        JsonArray diagnostics = new JsonArray();
        diagnostics.add(work.relativize(failedInstallEvidence.toRealPath()).toString());
        diagnostics.add(work.relativize(active.path.toRealPath()).toString());
        JsonObject input = active.input();
        if (input.has("process")) {
            JsonObject process = input.getAsJsonObject("process");
            for (String key : java.util.List.of("stdout", "stderr")) {
                diagnostics.add(work.relativize(evidence(process.get(key).getAsString())).toString());
            }
        }
        JsonObject disposition = new JsonObject();
        disposition.addProperty("schemaVersion", 1);
        disposition.addProperty("runId", id);
        disposition.addProperty("machine", record.get("machine").getAsString());
        disposition.addProperty("disposition", "retired-failed");
        disposition.addProperty("acceptance", "FAILED");
        disposition.addProperty("operationResolution", "retained");
        disposition.addProperty("baselineRequired", true);
        disposition.add("stop", stop);
        disposition.add("diagnostics", diagnostics);
        Path file = work.resolve("retirement.json");
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            if (!RunFiles.read(file).equals(disposition)) {
                throw new IOException("existing retirement disposition differs");
            }
        } else {
            RunFiles.write(file, disposition);
        }
        record.addProperty("state", "retired-failed");
        persist();
        closed = true;
        lease.close();
        return file;
    }

    /** A native app can outlive its launcher. Pending GUI ownership forbids guest shutdown and generic resume. */
    public synchronized void beginGui(Path scope) throws IOException {
        requireIdle();
        Path canonical = scope.toRealPath();
        if (session == null || session.cleanupVerified() || !canonical.startsWith(work)
                || Files.exists(work.resolve("gui-owner.json"), LinkOption.NOFOLLOW_LINKS))
            throw new IOException("fresh same-run GUI owner required; existing GUI intent cannot replay");
        guiPending = true;
        JsonObject value = new JsonObject(); value.addProperty("schemaVersion", 1); value.addProperty("runId", id);
        value.addProperty("sessionId", session.session().id()); value.addProperty("scope", work.relativize(canonical).toString()); value.addProperty("state", "pending");
        RunFiles.write(work.resolve("gui-owner.json"), value);
    }
    public synchronized void completeGui(Path proof) throws IOException {
        requireIdle();
        Path canonical = proof.toRealPath();
        if (!guiPending || !canonical.startsWith(work) || !Files.isRegularFile(proof, LinkOption.NOFOLLOW_LINKS)) throw new IOException("same-run GUI completion evidence required");
        JsonObject value = RunFiles.read(work.resolve("gui-owner.json"));
        if (!value.keySet().equals(Set.of("schemaVersion", "runId", "sessionId", "scope", "state"))
                || !"pending".equals(value.get("state").getAsString()) || !id.equals(value.get("runId").getAsString())
                || session == null || !session.session().id().equals(value.get("sessionId").getAsString())
                || !canonical.startsWith(work.resolve(value.get("scope").getAsString()).toRealPath())) throw new IOException("GUI owner differs");
        value.addProperty("state", "complete"); value.addProperty("evidence", work.relativize(canonical).toString());
        RunFiles.write(work.resolve("gui-owner.json"), value); guiPending = false;
    }

    public synchronized Operation pending() { return active; }

    public synchronized boolean hasFailedInstallHistory() { return failedInstallHistory; }

    public synchronized Path failedInstallEvidence() throws IOException {
        if (failedInstallEvidence == null) throw new IOException("failed install evidence missing");
        return failedInstallEvidence;
    }


    public Path evidence(String relative) throws IOException {
        if (relative == null || relative.isBlank()) throw new IOException("retained evidence path missing");
        Path value = Path.of(relative);
        Path file = work.resolve(value).normalize();
        if (value.isAbsolute() || !file.startsWith(work) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                || !file.toRealPath().startsWith(work)) throw new IOException("retained evidence is outside this run");
        return file;
    }

    public synchronized OwnedSession start(VoyemuClient client, String profile, boolean fresh, boolean withCluster)
            throws IOException, InterruptedException {
        if (closed || retirementOnly || session != null || active != null) throw new IOException("run cannot start another session");
        Path sessionWork = RunFiles.directory(work.resolve("session"));
        try {
            OwnedSession owned = OwnedSession.start(client, profile, Path.of(record.get("machine").getAsString()),
                sessionWork, fresh, withCluster);
            attach(owned);
            return owned;
        } catch (IOException | InterruptedException | RuntimeException failure) {
            startNotExecuted = failure instanceof OwnedSession.NotStarted
                || !Files.exists(sessionWork.resolve("start/dispatch"), LinkOption.NOFOLLOW_LINKS);
            throw failure;
        }
    }

    public synchronized void attach(OwnedSession owned) throws IOException {
        if (closed || retirementOnly || session != null) throw new IOException("run cannot attach a session");
        if (active != null && (!active.recovered || !owned.session().id().equals(active.value.get("sessionId").getAsString())))
            throw new IOException("retained operation belongs to another session");
        if (owned.cleanupVerified()) requireStop();
        else owned.beforeStop(this::requireStop);
        session = owned;
        record.addProperty("state", active == null ? "ready" : "operation");
        persist();
    }

    /** Reconcile the retained start without issuing another start request. */
    public synchronized OwnedSession resumeSession(VoyemuClient client, String profile)
            throws IOException, InterruptedException {
        if (closed || retirementOnly || session != null) throw new IOException("run already has a session");
        try {
            OwnedSession owned = OwnedSession.resume(client, profile,
                Path.of(record.get("machine").getAsString()), work.resolve("session"));
            attach(owned);
            return owned;
        } catch (OwnedSession.NotStarted rejected) {
            startNotExecuted = true;
            throw rejected;
        }
    }

    /** Leave a ready owned session for the next Gradle stage. */
    public synchronized void handoff() throws IOException {
        requireStop();
        if (session == null || session.cleanupVerified()) throw new IOException("No live session to hand off");
        releaseLease();
    }

    /** Release this process's lock while retaining all durable ownership and evidence. */
    public synchronized void releaseLease() throws IOException {
        if (closed) return;
        closed = true;
        lease.close();
    }

    public synchronized void requireTestReady() throws IOException {
        requireStop();
        if (session == null || session.cleanupVerified()) throw new IOException("No live test session");
    }

    /** Read-only journal identity for cross-layer evidence links. */
    public synchronized JsonObject identity() { return record.deepCopy(); }

    public synchronized boolean owns(OwnedSession owned) { return !closed && session == owned; }

    public synchronized Operation reserve(Kind kind, JsonObject input) throws IOException {
        requireIdle();
        if (failedInstallCleanup && (kind != Kind.UNINSTALL || input == null
                || !input.has("phase") || !input.get("phase").isJsonPrimitive()
                || !input.getAsJsonPrimitive("phase").isString()
                || !"failed-install-cleanup".equals(input.get("phase").getAsString()))) {
            throw new IOException("failed install requires normal uninstall before another mutation");
        }
        if (session == null || session.cleanupVerified()) throw new IOException("live verified session required before product mutation");
        if (sequence >= 999999) throw new IOException("run operation count exceeds bound");
        if (kind == null || input == null || input.isEmpty()) throw new IOException("operation kind and input required");
        Path directory = RunFiles.directory(work.resolve("operations").resolve(String.format(java.util.Locale.ROOT, "%06d", ++sequence)));
        JsonObject value = new JsonObject(); value.addProperty("schemaVersion", 1); value.addProperty("runId", id);
        value.addProperty("sessionId", session.session().id()); value.addProperty("kind", kind.name());
        value.add("input", input.deepCopy()); value.addProperty("state", "reserved");
        Operation operation = new Operation(directory.resolve("operation.json"), value);
        active = operation;
        RunFiles.write(operation.path, value);
        record.addProperty("state", "operation"); persist();
        return operation;
    }

    private synchronized void requireIdle() throws IOException {
        if (closed || retirementOnly || active != null) throw new IOException("BLOCKED: product operation is nonterminal; session shutdown and further mutations are forbidden");
    }

    private synchronized void requireStop() throws IOException {
        requireIdle();
        if (failedInstallCleanup) throw new IOException("failed install cleanup is not verified");
        if (guiPending) throw new IOException("BLOCKED: native GUI outcome remains unknown; guest owner retained");
    }

    private void persist() throws IOException {
        RunFiles.write(work.resolve("run.json"), record);
        RunFiles.write(owner, record);
    }

    public final class Operation implements AutoCloseable {
        private final Path path;
        private final JsonObject value;
        private boolean dispatched, terminal, verificationRecorded, recovered;
        private Operation(Path path, JsonObject value) { this.path = path; this.value = value; }

        public Path receiptPath() { return path; }

        public Kind kind() { return Kind.valueOf(value.get("kind").getAsString()); }
        public JsonObject input() { return value.getAsJsonObject("input").deepCopy(); }

        public void observePriorBoot(String bootId) throws IOException {
            synchronized (ProductRun.this) {
                if (closed || retirementOnly || active != this || dispatched || recovered || bootId == null
                        || !bootId.matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}"))
                    throw new IOException("prior boot must be observed before dispatch");
                value.getAsJsonObject("input").addProperty("priorBoot", bootId);
                RunFiles.write(path, value);
            }
        }

        public void process(java.util.List<String> command, Path out, Path err) throws IOException {
            synchronized (ProductRun.this) {
                if (closed || retirementOnly || active != this || terminal || !dispatched || recovered || command.isEmpty()
                        || value.getAsJsonObject("input").has("process")) throw new IOException("operation process already bound or not dispatched");
                JsonObject process = new JsonObject(); JsonArray arguments = new JsonArray();
                command.forEach(arguments::add); process.add("command", arguments);
                for (Path file : java.util.List.of(out, err)) {
                    if (!file.toAbsolutePath().normalize().startsWith(work)) throw new IOException("process evidence outside run");
                }
                process.addProperty("stdout", work.relativize(out.toAbsolutePath().normalize()).toString());
                process.addProperty("stderr", work.relativize(err.toAbsolutePath().normalize()).toString());
                value.getAsJsonObject("input").add("process", process);
                RunFiles.write(path, value);
            }
        }

        public void dispatch() throws IOException {
            synchronized (ProductRun.this) {
                if (closed || retirementOnly || active != this || terminal || dispatched || recovered) throw new IOException("operation cannot dispatch again");
                // An uncertain durable write is never an authorization to send again.
                dispatched = true;
                value.addProperty("state", "dispatched"); RunFiles.write(path, value);
            }
        }

        /** Call only after independent verification; these are actual retained evidence paths. */
        public void complete(Path... evidence) throws IOException {
            finish("verified", evidence);
        }

        /** Resolves terminal mutation only; failed acceptance is never a verified install. */
        public void failInstall(Path proof) throws IOException {
            synchronized (ProductRun.this) {
                if (kind() != Kind.INSTALL && kind() != Kind.UPDATE) {
                    throw new IOException("only an install may be reconciled as failed");
                }
                ru.voboost.tests.installer.cli.FailedInstallReconciliation.require(
                        RunFiles.read(proof), input());
                finish("failed-reconciled", proof);
            }
        }

        public void rejectUninstall(Path proof) throws IOException {
            synchronized (ProductRun.this) {
                if (kind() != Kind.UNINSTALL) throw new IOException("only uninstall may be rejected");
                ru.voboost.tests.installer.cli.InstallerRemovalRejection.require(RunFiles.read(proof), input());
                finish("rejected", proof);
            }
        }

        private void finish(String state, Path... evidence) throws IOException {
            synchronized (ProductRun.this) {
                if (closed || retirementOnly || active != this || terminal || !dispatched || evidence.length == 0)
                    throw new IOException("dispatched operation and terminal evidence required");
                JsonArray files = new JsonArray();
                for (Path file : evidence) {
                    Path canonical = file.toRealPath();
                    if (!canonical.startsWith(work) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
                        throw new IOException("terminal evidence must belong to this run");
                    files.add(work.relativize(canonical).toString());
                }
                if (failedInstallCleanup && kind() == Kind.UNINSTALL && state.equals("verified")) {
                    ru.voboost.tests.installer.cli.FailedInstallReconciliation.requireNormalCleanup(
                            evidence[0], input());
                }
                value.add("evidence", files); value.addProperty("state", state);
                RunFiles.write(path, value);
                verificationRecorded = true;
                if (state.equals("failed-reconciled")) {
                    failedInstallEvidence = evidence[0].toRealPath();
                    failedInstallHistory = true;
                    failedInstallCleanup = true;
                } else if (kind() == Kind.UNINSTALL && state.equals("verified")) {
                    failedInstallCleanup = false;
                }
                record.addProperty("state", "ready"); persist();
                terminal = true; active = null;
            }
        }

        @Override public void close() throws IOException {
            synchronized (ProductRun.this) {
                if (retirementOnly || terminal || verificationRecorded || active != this) return;
                value.addProperty("state", dispatched ? "unknown" : "not-dispatched");
                RunFiles.write(path, value);
                if (!dispatched) {
                    record.addProperty("state", "ready"); persist(); active = null; terminal = true;
                }
            }
        }
    }

    @Override public synchronized void close() throws IOException, InterruptedException {
        if (closed) return;
        Throwable failure = null;
        try {
            requireStop();
            if (guiPending) throw new IOException("BLOCKED: native GUI outcome remains unknown; guest owner retained");
            if (session == null && !startNotExecuted)
                throw new IOException("BLOCKED: session start outcome is not reconciled; machine owner retained");
            if (session != null) session.close();
            record.addProperty("state", "complete"); persist();
        } catch (IOException | InterruptedException | RuntimeException error) {
            failure = error;
            throw error;
        } finally {
            closed = true;
            try { lease.close(); }
            catch (IOException cleanup) { if (failure != null) failure.addSuppressed(cleanup); else throw cleanup; }
        }
    }
}
