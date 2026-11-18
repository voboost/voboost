package ru.voboost.tests.installer.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import com.google.gson.JsonObject;
import ru.voboost.tests.RunFiles;

/** Owns one CLI process and captured helpers, preserving separate bounded raw protocol output. */
public final class CliProcess {
    public enum Stream { OUT, ERR }
    @FunctionalInterface
    public interface Listener {
        /** Check control before external actions; optional reply is sent to stdin with one newline. */
        String line(Stream stream, String line, Control control) throws Exception;
    }
    /** Revoked before cleanup. A callback ignoring cancellation is reported as unconfirmed. */
    public static final class Control {
        private final long deadline;
        private final Control parent;
        private volatile boolean open = true;
        private java.io.OutputStream input;
        private volatile boolean inputClosed;
        private volatile String callbackStdout;
        private volatile FaultWindow diagnosticWindow;
        private volatile boolean genericReplySent;
        private final Object nativeGate = new Object();
        private int nativeControls;
        Control(long deadline, Control parent) { this.deadline = deadline; this.parent = parent; }
        public void check() throws IOException, InterruptedException {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException("CLI protocol interrupted");
            if (!open || System.nanoTime() - deadline >= 0) throw new IOException("CLI protocol scope expired");
            if (parent != null) parent.check();
        }
        public Duration remaining() throws IOException, InterruptedException {
            check();
            long left = deadline - System.nanoTime();
            if (parent != null) left = Math.min(left, parent.remaining().toNanos());
            if (left <= 0) throw new IOException("CLI protocol scope expired");
            return Duration.ofNanos(left);
        }
        /** Release a scope-owned streaming observer using EOF; never sends command bytes. */
        public void endInput() throws IOException, InterruptedException {
            synchronized (nativeGate) {
                check();
                if (input == null || inputClosed || diagnosticWindow != null) throw new IOException("CLI stdin is unavailable or native diagnostic requires bound release");
                inputClosed = true;
                input.close();
            }
        }
        /** Bind only the actual current stdout arm line to this process control. */
        public void bindNativeDiagnostic(FaultWindow window) throws IOException, InterruptedException {
            synchronized (nativeGate) {
                check();
                if (window == null || diagnosticWindow != null || callbackStdout == null || input == null || inputClosed || genericReplySent)
                    throw new IOException("native diagnostic arm is unavailable");
                window.observe(callbackStdout);
                window.requireNativeWindow();
                diagnosticWindow = window;
            }
        }
        /** Atomically revoke the bound native controls and close their input. */
        public void endNativeDiagnostic(FaultWindow window) throws IOException, InterruptedException {
            synchronized (nativeGate) {
                check();
                if (window == null || window != diagnosticWindow || input == null || inputClosed)
                    throw new IOException("native diagnostic release is not bound to this process");
                window.release();
                inputClosed = true;
                input.close();
            }
        }
        /** Capture only the current stdout receipt from this bound process. */
        public com.google.gson.JsonObject captureNativeDiagnostic(FaultWindow window) throws IOException, InterruptedException {
            synchronized (nativeGate) {
                check();
                if (window == null || window != diagnosticWindow || callbackStdout == null) throw new IOException("native receipt is not from this process");
                return window.observeNative(callbackStdout);
            }
        }
        /** Send one of the two fixed native diagnostic controls; release is never replayed. */
        public void sendNativeDiagnostic(FaultWindow window, FaultWindow.NativeControl command) throws IOException, InterruptedException {
            synchronized (nativeGate) {
                check();
                if (window == null || window != diagnosticWindow || input == null || inputClosed || nativeControls >= 13)
                    throw new IOException("native diagnostic control is not bound to this process");
                window.reserveNativeControl(command);
                nativeControls++;
                input.write((command == FaultWindow.NativeControl.SNAPSHOT ? "S\n" : "R\n").getBytes(StandardCharsets.US_ASCII));
                input.flush();
            }
        }
        private synchronized void callbackLine(Stream stream, String line) { callbackStdout = stream == Stream.OUT ? line : null; }
        private synchronized void clearCallbackLine() { callbackStdout = null; }
        private synchronized void reply(String reply) throws IOException, InterruptedException {
            check();
            if (input == null || inputClosed || diagnosticWindow != null) throw new IOException("CLI protocol reply after EOF or native binding");
            genericReplySent = true;
            input.write((reply + "\n").getBytes(StandardCharsets.UTF_8));
            input.flush();
        }
        void close() { synchronized (nativeGate) { open = false; } }
    }
    public record Result(int exit, Path out, Path err) {}
    record Limits(int outputBytes, int lineBytes) {
        Limits {
            if (outputBytes < 1 || lineBytes < 1 || lineBytes > outputBytes)
                throw new IllegalArgumentException("invalid CLI output limits");
        }
    }
    private record Child(ProcessHandle handle, Instant started) {}
    private static final Limits LIMITS = new Limits(32 * 1024 * 1024, 8 * 1024 * 1024);
    private CliProcess() {}

    public static Result run(List<String> command, Path out, Path err, Duration timeout, Listener listener)
            throws Exception {
        return run(command, out, err, timeout, listener, LIMITS, null);
    }
    public static Result run(List<String> command, Path out, Path err, Duration timeout, Listener listener, Control parent)
            throws Exception {
        return run(command, out, err, timeout, listener, LIMITS, Objects.requireNonNull(parent));
    }
    static Result run(List<String> command, Path out, Path err, Duration timeout, Listener listener, Limits limits)
            throws Exception {
        return run(command, out, err, timeout, listener, limits, null);
    }
    private static Result run(List<String> command, Path out, Path err, Duration timeout, Listener listener, Limits limits, Control parent)
            throws Exception {
        Objects.requireNonNull(listener); Objects.requireNonNull(limits);
        if (command.isEmpty() || timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofHours(1)) > 0)
            throw new IllegalArgumentException("invalid CLI process invocation");
        if (out.toAbsolutePath().normalize().equals(err.toAbsolutePath().normalize()))
            throw new IllegalArgumentException("CLI streams require distinct files");
        if (parent != null) {
            Duration remaining = parent.remaining();
            if (timeout.compareTo(remaining) > 0) timeout = remaining;
        }
        Control control = new Control(System.nanoTime() + timeout.toNanos(), parent);
        Files.createFile(out); Files.createFile(err);
        control.check();
        Instant startedAt = Instant.now();
        long startedNanos = System.nanoTime();
        Process process = new ProcessBuilder(command).redirectOutput(out.toFile()).redirectError(err.toFile()).start();
        control.input = process.getOutputStream();
        ExecutorService callbacks = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "installer-cli-protocol");
            thread.setDaemon(true);
            return thread;
        });
        Map<Long, Child> children = new LinkedHashMap<>();
        Throwable failure = null;
        Long elapsedNanos = null;
        Instant exitedAt = null;
        Integer exit = null;
        try (Cursor stdout = new Cursor(out, Stream.OUT, limits);
                Cursor stderr = new Cursor(err, Stream.ERR, limits)) {
            while (true) {
                control.check(); capture(process, children);
                stdout.poll(process, listener, callbacks, control);
                stderr.poll(process, listener, callbacks, control);
                control.check(); capture(process, children);
                if (!process.isAlive()) {
                    elapsedNanos = System.nanoTime() - startedNanos;
                    exitedAt = Instant.now();
                    exit = process.exitValue();
                    stdout.poll(process, listener, callbacks, control);
                    stderr.poll(process, listener, callbacks, control);
                    stdout.finish(); stderr.finish();
                    return new Result(process.exitValue(), out, err);
                }
                process.waitFor(20, TimeUnit.MILLISECONDS);
            }
        } catch (Exception | Error error) {
            failure = error;
            throw error;
        } finally {
            control.close(); callbacks.shutdownNow();
            Exception cleanup = null;
            try { terminate(process, children); }
            catch (IOException | InterruptedException error) { cleanup = error; }
            if (elapsedNanos == null) {
                elapsedNanos = System.nanoTime() - startedNanos;
                exitedAt = Instant.now();
                if (!process.isAlive()) exit = process.exitValue();
            }
            try {
                if (!callbacks.awaitTermination(2, TimeUnit.SECONDS))
                    throw new IOException("CLI callback termination unconfirmed; raw evidence retained");
            } catch (IOException | InterruptedException error) {
                if (cleanup == null) cleanup = error; else cleanup.addSuppressed(error);
            }
            try {
                JsonObject timing = new JsonObject();
                timing.addProperty("schemaVersion", 1);
                timing.addProperty("startedAt", startedAt.toString());
                timing.addProperty("exitedAt", exitedAt.toString());
                timing.addProperty("monotonicElapsedNanos", elapsedNanos);
                timing.addProperty("scope", failure == null ? "launch-through-exit-observation" : "launch-through-termination-observation");
                timing.addProperty("status", cleanup != null ? "cleanup-unconfirmed" : failure == null ? "exited" : "interrupted");
                timing.addProperty("exit", exit);
                RunFiles.write(out.resolveSibling(out.getFileName() + ".timing.json"), timing);
            } catch (IOException error) {
                if (cleanup == null) cleanup = error; else cleanup.addSuppressed(error);
            }
            if (cleanup != null) {
                if (failure != null) cleanup.addSuppressed(failure);
                throw cleanup;
            }
        }
    }

    private static void capture(Process process, Map<Long, Child> children) {
        try (var descendants = process.descendants()) {
            descendants.forEach(handle -> children.putIfAbsent(handle.pid(), new Child(handle, handle.info().startInstant().orElse(null))));
        }
    }
    private static void terminate(Process process, Map<Long, Child> children) throws IOException, InterruptedException {
        capture(process, children);
        boolean retainedHelper = false;
        for (Child child : children.values()) {
            if (!child.handle().isAlive()) continue;
            if (child.started() == null || !child.handle().info().startInstant().filter(child.started()::equals).isPresent()) {
                retainedHelper = true;
                continue;
            }
            child.handle().destroyForcibly();
        }
        // Let a waiting parent reap killed children before killing the parent itself.
        if (process.isAlive() && !children.isEmpty()) process.waitFor(100, TimeUnit.MILLISECONDS);
        if (process.isAlive()) process.toHandle().destroyForcibly();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (process.isAlive() || children.values().stream().anyMatch(child -> child.handle().isAlive())) {
            if (System.nanoTime() - deadline >= 0) throw new IOException("CLI process/helper termination unconfirmed; raw evidence retained");
            Thread.sleep(10);
        }
        if (retainedHelper) throw new IOException("CLI helper identity changed; raw evidence retained");
    }

    private static final class Cursor implements AutoCloseable {
        private final FileChannel file;
        private final Stream stream;
        private final Limits limits;
        private final ByteArrayOutputStream line = new ByteArrayOutputStream();
        Cursor(Path path, Stream stream, Limits limits) throws IOException {
            this.file = FileChannel.open(path, StandardOpenOption.READ); this.stream = stream; this.limits = limits;
        }
        void poll(Process process, Listener listener, ExecutorService callbacks, Control control) throws Exception {
            if (file.size() > limits.outputBytes()) throw new IOException("CLI output exceeds bound: " + stream);
            ByteBuffer buffer = ByteBuffer.allocate(4096);
            while (file.read(buffer) > 0) {
                control.check(); buffer.flip();
                while (buffer.hasRemaining()) {
                    byte value = buffer.get();
                    if (value == '\n') {
                        byte[] bytes = line.toByteArray();
                        int length = bytes.length;
                        if (length > 0 && bytes[length - 1] == '\r') --length;
                        String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes, 0, length)).toString();
                        line.reset();
                        Future<?> delivery = callbacks.submit(() -> {
                            control.check();
                            control.callbackLine(stream, text);
                            String reply;
                            try { reply = listener.line(stream, text, control); }
                            finally { control.clearCallbackLine(); }
                            control.check();
                            if (reply != null) {
                                if (reply.length() > 4096 || reply.indexOf('\n') >= 0 || reply.indexOf('\r') >= 0 || reply.indexOf('\0') >= 0)
                                    throw new IOException("invalid CLI protocol reply");
                                control.reply(reply);
                            }
                            return null;
                        });
                        try { delivery.get(control.remaining().toNanos(), TimeUnit.NANOSECONDS); }
                        catch (TimeoutException expired) { delivery.cancel(true); throw new IOException("CLI process deadline expired", expired); }
                        catch (InterruptedException interrupted) { delivery.cancel(true); throw interrupted; }
                        catch (ExecutionException callback) {
                            if (callback.getCause() instanceof Exception error) throw error;
                            throw (Error) callback.getCause();
                        }
                    } else {
                        if (line.size() >= limits.lineBytes()) throw new IOException("CLI line exceeds bound: " + stream);
                        line.write(value);
                    }
                }
                buffer.clear();
                if (file.size() > limits.outputBytes()) throw new IOException("CLI output exceeds bound: " + stream);
            }
        }
        void finish() throws IOException {
            if (line.size() != 0) throw new IOException("CLI output ended within a protocol line: " + stream);
        }
        @Override public void close() throws IOException { file.close(); }
    }
}
