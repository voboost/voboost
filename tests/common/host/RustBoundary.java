package ru.voboost.tests;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Runs a root-owned host boundary suite against the actual owning component sources. */
public final class RustBoundary {
    private RustBoundary() {}
    public static Path verify(String suite, List<String> requiredCases) throws IOException, InterruptedException {
        if (!suite.matches("[a-z][a-z0-9_]{0,63}") || requiredCases.isEmpty())
            throw new IllegalArgumentException("bounded named Rust suite and cases required");
        Path cargo = executable("cargo"), rustc = executable("rustc");
        Path root = Path.of(System.getProperty("testWorkDir")); Files.createDirectories(root);
        Path work = RunFiles.directory(root.resolve(suite + "-" + UUID.randomUUID()));
        Path log = work.resolve("cargo.log");
        HostProcess.Result result = HostProcess.run(log, List.of(cargo.toString(), "test", "--offline", "--locked",
            "--manifest-path", Path.of("release/Cargo.toml").toAbsolutePath().toString(), "--test", suite), 300000,
            Map.of("RUSTC", rustc.toString(), "RUSTC_WRAPPER", "", "RUSTC_WORKSPACE_WRAPPER", "", "TMPDIR", work.toString(),
                "CARGO_TARGET_DIR", Path.of("../.build-cache/inject-host").toAbsolutePath().normalize().toString(), "CARGO_TERM_COLOR", "never"));
        if (result.exit() != 0) throw new IOException("Rust interoperability failed; retained log: " + log);
        for (String name : requiredCases) {
            if (!name.matches("[a-z][a-z0-9_]{0,159}") || !result.output().lines().anyMatch(line -> line.equals("test " + name + " ... ok")))
                throw new IOException("Required interoperability case did not pass: " + name + "; " + log);
        }
        return log;
    }
    public static Path executable(String name) throws IOException {
        String value = System.getProperty(name);
        if (value == null || !Path.of(value).isAbsolute() || !Files.isExecutable(Path.of(value)))
            throw new IOException("BLOCKED: absolute trusted " + name + " required");
        return Path.of(value);
    }
}
