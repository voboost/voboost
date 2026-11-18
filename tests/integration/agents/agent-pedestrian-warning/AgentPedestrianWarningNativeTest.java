package ru.voboost.tests.agents;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.voboost.tests.HostProcess;

/** Production native saved-setting lifecycle and visual mask composed on the host; no QNX runtime claim. */
class AgentPedestrianWarningNativeTest {
    @TempDir Path work;

    @Test void savedSettingStartsWithoutCanOrLiveLeaseService() throws Exception {
        String compiler = System.getenv("CXX");
        assertNotNull(compiler, "CXX must name the trusted absolute host C++ compiler");
        assertTrue(Path.of(compiler).isAbsolute() && Files.isExecutable(Path.of(compiler)));
        Path root = Path.of("").toRealPath().getParent();
        Path agent = root.resolve("voboost-agents/src/main/cpp/agents/vehicle/drive/pedestrianwarning");
        Path executable = work.resolve("pw-saved-setting");
        var build = HostProcess.run(work.resolve("compile.log"), List.of(compiler,
                "-std=c++17", "-O2", "-Wall", "-Wextra", "-Werror",
                "-I", agent.resolve("src").toString(),
                "-I", root.resolve("voboost-inject/cluster/sdk/include").toString(),
                agent.resolve("tests/agent.cpp").toString(), "-o", executable.toString()), 60000,
                Map.of("TMPDIR", work.toString()));
        assertEquals(0, build.exit(), build.output());
        for (var arguments : List.of(List.of(executable.toString()), List.of(executable.toString(), "stop-failure"))) {
            var run = HostProcess.run(work.resolve("run-" + arguments.size() + ".log"), arguments, 10000);
            assertEquals(0, run.exit(), run.output());
        }
    }

    @Test void savedUserIntentControlsOnlyTheOwnedVisualSource() throws Exception {
        String compiler = System.getenv("CXX");
        assertNotNull(compiler, "CXX must name the trusted absolute host C++ compiler");
        assertTrue(Path.of(compiler).isAbsolute() && Files.isExecutable(Path.of(compiler)));
        Path agent = Path.of("").toRealPath().resolve("../voboost-agents/src/main/cpp/agents/vehicle/drive/pedestrianwarning").normalize();
        Path executable = work.resolve("pw-saved-mask");
        var build = HostProcess.run(work.resolve("compile.log"), List.of(compiler,
                "-std=c++17", "-O2", "-Wall", "-Wextra", "-Werror", "-I", agent.resolve("src").toString(),
                agent.resolve("tests/mask.cpp").toString(),
                agent.resolve("src/mask.cpp").toString(), "-o", executable.toString()), 60000,
                Map.of("TMPDIR",work.toString()));
        assertEquals(0,build.exit(),build.output());
        var run = HostProcess.run(work.resolve("run.log"),List.of(executable.toString()),10000);
        assertEquals(0,run.exit(),run.output());
    }

    @Test void rollsBackEveryGlyphLeafWhenOneCannotBeHidden() throws Exception {
        String compiler = System.getenv("CXX");
        assertNotNull(compiler, "CXX must name the trusted absolute host C++ compiler");
        assertTrue(Path.of(compiler).isAbsolute() && Files.isExecutable(Path.of(compiler)));
        Path root = Path.of("").toRealPath().getParent();
        Path agent = root.resolve("voboost-agents/src/main/cpp/agents/vehicle/drive/pedestrianwarning");
        Path executable = work.resolve("pw-leaf-backend");
        var build = HostProcess.run(work.resolve("compile.log"), List.of(compiler,
                "-std=c++17", "-O2", "-Wall", "-Wextra", "-Werror", "-DVBC_PW_HOST_TEST",
                "-I", agent.resolve("src").toString(),
                "-I", root.resolve("voboost-inject/cluster/sdk/include").toString(),
                agent.resolve("tests/backend.cpp").toString(),
                agent.resolve("src/backend.cpp").toString(),
                agent.resolve("src/mask.cpp").toString(),
                agent.resolve("src/layout.cpp").toString(), "-o", executable.toString()), 60000,
                Map.of("TMPDIR", work.toString()));
        assertEquals(0, build.exit(), build.output());
        var run = HostProcess.run(work.resolve("rollback.log"), List.of(executable.toString(), "b"), 10000);
        assertEquals(0, run.exit(), run.output());
    }
}
