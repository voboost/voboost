package ru.voboost.voyemu;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import ru.voboost.tests.RunFiles;
import ru.voboost.voyemu.EmulatorPreparation.Candidate;
import ru.voboost.voyemu.EmulatorPreparation.Lane;

/** Owns the lifecycle around the selected JUnit cases. */
public final class EmulatorRun {
    private static final Map<String, List<String>> SUITES = Map.of(
            "generic", List.of("ru.voboost.tests.installer.cli.SessionLifecycleTest"),
            "installer-cli", List.of("ru.voboost.tests.installer.cli.InstallerCliLifecycleTest"),
            "installer-gui", List.of("ru.voboost.tests.installer.gui.InstallerGuiLifecycleTest"),
            "agent-pedestrian-warning", List.of("ru.voboost.tests.agents.AgentPedestrianWarning"),
            "agent-vehicle-outing", List.of("ru.voboost.tests.agents.AgentVehicleOuting"),
            "agent-forced-ev", List.of("ru.voboost.tests.agents.AgentForcedEv"),
            "agent-settings-engineering-menu", List.of("ru.voboost.tests.agents.AgentSettingsEngineeringMenu"),
            "agent-keyboard", List.of("ru.voboost.tests.agents.AgentKeyboard"));
    private static final List<String> ORDER = List.of("generic", "installer-cli", "installer-gui",
            "agent-pedestrian-warning", "agent-vehicle-outing", "agent-forced-ev", "agent-settings-engineering-menu", "agent-keyboard");
    private static final String UPDATE = "updatesFromPreviousRelease";
    private static final String REINSTALL = "reinstallsOverPreviousResourceRelease";

    private record Case(String suite, Class<?> type, java.lang.reflect.Method method) {
        String name() { return type.getName() + "." + method.getName(); }
        String role() { return suite.startsWith("agent-") ? "agents" : "installer"; }
    }

    private record Plan(List<Case> cases) {
        List<Case> forRole(String role) {
            return cases.stream().filter(test -> role.equals(test.role())).toList();
        }

        boolean needsCandidate() {
            return cases.stream().anyMatch(test -> !"generic".equals(test.suite()));
        }

        boolean includesUpdate() {
            return cases.stream().anyMatch(test -> Set.of(UPDATE, "updatesAcrossDisconnectedInstaller", "reappliesAcrossDisconnectedInstaller").contains(test.method().getName()));
        }

        boolean includesReinstall() {
            return cases.stream().anyMatch(test -> REINSTALL.equals(test.method().getName()));
        }
    }

    private record Packages(Candidate current, Candidate previous) implements AutoCloseable {
        @Override public void close() throws Exception {
            try (current; previous) {
                // Each Candidate detaches its own read-only mount.
            }
        }
    }

    private EmulatorRun() {}

    public static void main(String[] args) throws Exception {
        Plan plan = select(System.getProperty("emulatorSuite"), System.getProperty("emulatorTests"));
        if (Boolean.getBoolean("emulatorDryRun")) {
            plan.cases().forEach(test -> System.out.println(test.name()));
            return;
        }
        if (plan.includesReinstall()) candidatePath("previousDmg");
        Path base = Path.of(System.getProperty("testWorkDir")).toAbsolutePath();
        Files.createDirectories(base);
        Path root = RunFiles.directory(base.resolve("lifecycle"));
        Path locks = RunFiles.directory(base.resolve("machine-locks"));
        if ("check".equals(args[0])) {
            check(root, locks, plan);
            return;
        }
        String role = plan.cases().get(0).role();
        if (plan.forRole(role).size() != plan.cases().size()) {
            throw new IOException("Select one role with -Psuite for standalone emulator stages");
        }
        Path job = RunFiles.directory(root.resolve("manual"));
        Plan preparation = plan.includesReinstall() ? plan
                : new Plan(select(System.getProperty("emulatorSuite"), null).forRole(role));
        Lane lane = lane(role, preparation);
        Path work = RunFiles.directory(job.resolve(role));
        try (var lease = MachineLease.acquire(root.resolve("manual-locks"), job)) {
            switch (args[0]) {
                case "prepare" -> prepareStandalone(job, work, locks, preparation, lane);
                case "start" -> startStandalone(job, work, locks, preparation, lane);
                case "test" -> testStandalone(job, work, locks, preparation, plan, lane);
                case "reconcile" -> reconcileStandalone(job, work, locks, preparation, lane);
                case "stop" -> stopStandalone(job, work, locks, preparation, lane);
                default -> throw new IOException("Unknown emulator stage " + args[0]);
            }
        }
    }

    private static void check(Path root, Path locks, Plan plan) throws Exception {
        List<Lane> lanes = new ArrayList<>();
        for (String role : List.of("installer", "agents")) {
            var selected = plan.forRole(role);
            if (!selected.isEmpty()) lanes.add(lane(role, new Plan(selected)));
        }
        Path job = Files.createTempDirectory(root, "check-");
        System.out.println("Emulator evidence: " + job);
        try (Packages packages = openPackages(job, plan)) {
            var actions = new ArrayList<Callable<Void>>();
            for (Lane lane : lanes) {
                Path work = RunFiles.directory(job.resolve(lane.role()));
                var selected = new Plan(plan.forRole(lane.role()));
                actions.add(() -> {
                    cycle(work, locks, selected, lane, packages);
                    return null;
                });
            }
            boolean parallel = lanes.size() == 2
                    && !lanes.get(0).configuration().machine().equals(lanes.get(1).configuration().machine());
            System.out.println("Emulator lanes: " + (parallel ? "parallel" : "sequential"));
            runLanes(actions, parallel);
        }
    }

    private static void runLanes(List<Callable<Void>> actions, boolean parallel) throws Exception {
        if (!parallel) {
            Exception failure = null;
            for (var action : actions) {
                try { action.call(); }
                catch (Exception error) {
                    if (failure == null) failure = error;
                    else failure.addSuppressed(error);
                }
            }
            if (failure != null) throw failure;
            return;
        }
        var executor = Executors.newFixedThreadPool(actions.size());
        try {
            var results = executor.invokeAll(actions);
            Exception failure = null;
            for (var result : results) {
                try { result.get(); }
                catch (java.util.concurrent.ExecutionException error) {
                    if (failure == null) failure = new IOException("Parallel emulator lane failed", error.getCause());
                    else failure.addSuppressed(error.getCause());
                }
            }
            if (failure != null) throw failure;
        } finally {
            executor.shutdownNow();
            if (!executor.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new IOException("Emulator lanes did not terminate");
            }
        }
    }

    private static void cycle(Path work, Path locks, Plan plan, Lane lane, Packages packages) throws Exception {
        Candidate candidate = plan.needsCandidate() ? packages.current() : null;
        Path receipt = work.resolve("result.json");
        JsonObject result = lane.identity();
        String stage = "prepare";
        long started = System.nanoTime();
        try {
            prepare(work, locks, lane, candidate);
            stage = "start";
            try (var session = EmulatorPreparation.start(lane, sessionWork(work), locks, candidate)) {
                result.addProperty("session", session.session.session().id());
                stage = "test";
                execute(plan, session, packages.previous());
                stage = "stop";
            }
            result.addProperty("status", "passed");
        } catch (Exception | Error failure) {
            result.addProperty("status", "failed");
            result.addProperty("stage", stage);
            result.addProperty("diagnostic", failure.toString());
            throw failure;
        } finally {
            result.addProperty("elapsedMillis", (System.nanoTime() - started) / 1_000_000);
            RunFiles.write(receipt, result);
        }
    }

    private static void prepareStandalone(Path job, Path work, Path locks, Plan plan, Lane lane) throws Exception {
        Path receipt = work.resolve("prepared.json");
        if (Files.exists(receipt)) throw new IOException("Preparation already exists at " + receipt);
        Packages packages = openPackages(job, plan);
        try {
            prepare(work, locks, lane, packages.current());
            var state = lane.identity();
            if (packages.current() != null) state.add("candidate", packages.current().receipt());
            if (packages.previous() != null) state.add("previous", packages.previous().receipt());
            RunFiles.write(receipt, state);
            System.out.println("Prepared emulator: " + receipt);
        } catch (Exception | Error failure) {
            try { packages.close(); }
            catch (Exception cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private static void startStandalone(Path job, Path work, Path locks, Plan plan, Lane lane) throws Exception {
        Path receipt = work.resolve("session.json");
        if (Files.exists(receipt)) throw new IOException("Test session already exists at " + receipt);
        var packages = claimPackages(job, work, plan, lane);
        var session = EmulatorPreparation.start(lane, sessionWork(work), locks, packages.current());
        try {
            RunFiles.write(receipt, session.receipt());
            session.run.handoff();
            System.out.println("Started emulator session: " + receipt);
        } catch (Exception | Error failure) {
            try { session.close(); }
            catch (Exception cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private static void testStandalone(Path job, Path work, Path locks, Plan preparation, Plan plan, Lane lane)
            throws Exception {
        var packages = claimPackages(job, work, preparation, lane);
        var session = claimSession(work, locks, lane, packages, false);
        try { execute(plan, session, packages.previous()); }
        finally { session.run.releaseLease(); }
    }

    private static void stopStandalone(Path job, Path work, Path locks, Plan plan, Lane lane) throws Exception {
        Path receipt = work.resolve("session.json");
        if (!Files.isRegularFile(receipt)) throw new IOException("No started session. Run emulatorStart first.");
        JsonObject session = RunFiles.read(receipt);
        if (!EmulatorPreparation.stopped(lane, work, session)) {
            var packages = claimPackages(job, work, plan, lane);
            EmulatorPreparation.claimForStop(lane, work, locks, packages.current(), session).close();
        }
        claimPackages(job, work, plan, lane, true).close();
        Files.delete(work.resolve("session.json"));
        Files.delete(work.resolve("prepared.json"));
    }

    private static void reconcileStandalone(Path job, Path work, Path locks, Plan plan, Lane lane) throws Exception {
        var packages = claimPackages(job, work, plan, lane);
        var session = claimSession(work, locks, lane, packages, true);
        try { session.installer.reconcilePending(); }
        finally { session.run.releaseLease(); }
        System.out.println("Reconciled emulator operation: " + work);
    }

    private static EmulatorPreparation claimSession(Path work, Path locks, Lane lane, Packages packages,
            boolean reconcile)
            throws Exception {
        Path receipt = work.resolve("session.json");
        if (!Files.isRegularFile(receipt)) throw new IOException("No started session. Run emulatorStart first.");
        return EmulatorPreparation.claim(lane, work, locks, packages.current(), RunFiles.read(receipt), reconcile);
    }

    private static void prepare(Path work, Path locks, Lane lane, Candidate candidate) throws Exception {
        Path preparation = Files.createTempDirectory(work, "prepare-");
        EmulatorPreparation.prepare(lane, preparation, locks, candidate,
                System.getProperty("prepareExisting", "require-absent"));
    }

    private static Path sessionWork(Path work) throws IOException {
        return Files.createTempDirectory(work, "session-");
    }

    private static Packages openPackages(Path job, Plan plan) throws Exception {
        if (!plan.needsCandidate()) return new Packages(null, null);
        Path current = candidatePath("candidateDmg");
        boolean needsPrevious = plan.includesReinstall()
                || (plan.includesUpdate() && System.getProperty("previousDmg") != null);
        Path previous = needsPrevious ? candidatePath("previousDmg") : null;
        if (current.equals(previous)) throw new IOException("Previous and current DMG must differ");
        Candidate candidate = Candidate.open(RunFiles.directory(job.resolve("candidate")), current);
        Candidate prior = null;
        try {
            if (previous != null) prior = Candidate.open(RunFiles.directory(job.resolve("previous")), previous);
            if (prior != null && Files.mismatch(prior.app().resolve("Contents/Resources/voboost.json"),
                    candidate.app().resolve("Contents/Resources/voboost.json")) == -1) {
                throw new IOException("Previous and current package contents must differ");
            }
            return new Packages(candidate, prior);
        } catch (Exception | Error failure) {
            try { new Packages(candidate, prior).close(); }
            catch (Exception cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private static Packages claimPackages(Path job, Path work, Plan plan, Lane lane) throws Exception {
        return claimPackages(job, work, plan, lane, false);
    }

    private static Packages claimPackages(Path job, Path work, Plan plan, Lane lane, boolean cleanup) throws Exception {
        Path receipt = work.resolve("prepared.json");
        if (!Files.isRegularFile(receipt)) throw new IOException("No prepared machine. Run emulatorPrepare first.");
        var state = RunFiles.read(receipt);
        for (var entry : lane.identity().entrySet()) {
            if (!entry.getValue().equals(state.get(entry.getKey()))) {
                throw new IOException("Prepared emulator differs: " + entry.getKey());
            }
        }
        if (plan.includesReinstall() && !state.has("previous")) {
            throw new IOException("Reinstall requires a previous DMG mount. Run emulatorPrepare with the same --tests selection and -PpreviousDmg");
        }
        Candidate candidate = plan.needsCandidate() ? (cleanup
                ? Candidate.claimForCleanup(job.resolve("candidate"), candidatePath("candidateDmg"), state.getAsJsonObject("candidate"))
                : Candidate.claim(job.resolve("candidate"), candidatePath("candidateDmg"), state.getAsJsonObject("candidate"))) : null;
        Candidate previous = state.has("previous") ? (cleanup
                ? Candidate.claimForCleanup(job.resolve("previous"), candidatePath("previousDmg"), state.getAsJsonObject("previous"))
                : Candidate.claim(job.resolve("previous"), candidatePath("previousDmg"), state.getAsJsonObject("previous"))) : null;
        return new Packages(candidate, previous);
    }

    private static Path candidatePath(String key) throws IOException {
        String value = System.getProperty(key);
        if (value == null || !Path.of(value).isAbsolute() || !Files.isRegularFile(Path.of(value))) {
            throw new IOException("Provide an absolute DMG as -P" + key);
        }
        return Path.of(value).toRealPath();
    }

    private static Lane lane(String role, Plan plan) throws IOException {
        var selected = TestConfiguration.forRole(role);
        boolean cluster = plan.needsCandidate();
        boolean pedestrianWarning = plan.cases().stream()
                .anyMatch(test -> "agent-pedestrian-warning".equals(test.suite()));
        return new Lane(role, selected.machineName(), selected.configuration(), "agents".equals(role),
                pedestrianWarning, cluster);
    }

    private static void execute(Plan plan, EmulatorPreparation session, Candidate previous) throws IOException {
        var request = LauncherDiscoveryRequestBuilder.request()
                .selectors(plan.cases().stream().map(test ->
                        DiscoverySelectors.selectMethod(test.type(), test.method())).toList())
                .configurationParameter("junit.jupiter.execution.parallel.enabled", "false")
                .configurationParameter("junit.jupiter.testclass.order.default", "org.junit.jupiter.api.ClassOrderer$OrderAnnotation")
                .build();
        var listener = new SummaryGeneratingListener();
        EmulatorFixture.bind(session, previous);
        try { LauncherFactory.create().execute(request, listener); }
        finally { EmulatorFixture.unbind(); }
        var summary = listener.getSummary();
        Path report = Files.createTempFile(session.work, "junit-", ".log");
        try (var out = new PrintWriter(Files.newBufferedWriter(report))) {
            summary.printTo(out);
            summary.printFailuresTo(out);
            for (var failure : summary.getFailures()) failure.getException().printStackTrace(out);
        }
        JsonObject result = new JsonObject();
        JsonArray selected = new JsonArray();
        plan.cases().forEach(test -> selected.add(test.name()));
        result.add("selected", selected);
        result.addProperty("session", session.session.session().id());
        result.addProperty("passed", summary.getTestsSucceededCount());
        result.addProperty("failed", summary.getTestsFailedCount());
        result.addProperty("skipped", summary.getTestsSkippedCount() + summary.getTestsAbortedCount());
        result.addProperty("report", report.toString());
        RunFiles.write(report.resolveSibling(report.getFileName() + ".json"), result);
        if (summary.getTestsSucceededCount() != plan.cases().size() || summary.getTotalFailureCount() != 0) {
            throw new IOException("Emulator cases did not all pass. See " + report);
        }
        System.out.println("Emulator cases passed: " + report);
    }

    private static Plan select(String suite, String pattern) throws Exception {
        List<String> suites = switch (suite == null ? "all" : suite) {
            case "all" -> ORDER;
            case "installer" -> ORDER.subList(0, 3);
            case "agents" -> ORDER.subList(3, ORDER.size());
            default -> {
                if (!SUITES.containsKey(suite)) throw new IOException("Unknown emulator suite " + suite);
                yield List.of(suite);
            }
        };
        Pattern matcher = pattern == null ? null : Pattern.compile(
                java.util.Arrays.stream(pattern.split("\\*", -1)).map(Pattern::quote).collect(java.util.stream.Collectors.joining(".*")));
        List<Case> selected = new ArrayList<>();
        for (String group : suites) {
            for (String name : SUITES.get(group)) {
                Class<?> type = Class.forName(name);
                for (var method : java.util.Arrays.stream(type.getDeclaredMethods())
                        .filter(candidate -> candidate.isAnnotationPresent(Test.class))
                        .sorted(Comparator.comparing(java.lang.reflect.Method::getName)).toList()) {
                    var test = new Case(group, type, method);
                    if (REINSTALL.equals(method.getName()) && !test.name().equals(pattern)
                            && !(type.getSimpleName() + "." + method.getName()).equals(pattern)) continue;
                    if (matcher == null || List.of(test.name(), type.getName(), type.getSimpleName(),
                            type.getSimpleName() + "." + method.getName()).stream().anyMatch(value -> matcher.matcher(value).matches())) {
                        selected.add(test);
                    }
                }
            }
        }
        if (selected.isEmpty()) throw new IOException("No emulator tests match the selection");
        return new Plan(List.copyOf(selected));
    }
}
