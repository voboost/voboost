// Process runner for release orchestration. argv is always an array — no
// shell string concatenation anywhere. Captured output is bounded and honest:
// exceeding the cap kills the process group and fails with an explicit
// overflow error — a truncated-but-parseable capture is never reported as a
// successful result. Timeouts kill the whole process group, and pending kill
// timers are cleared once the child is reaped. Secret values are redacted
// from every error and log line.
import { spawn, type ChildProcess } from "node:child_process";
import { basename } from "node:path";

export type RunOptions = {
  cwd?: string;
  env?: Record<string, string>;
  timeoutMs?: number;
  /** Values redacted from all rendered output (secret file contents, paths). */
  secrets?: readonly string[];
  /** Maximum captured bytes per stream (default 8 MiB); overflow fails. */
  maxBufferBytes?: number;
  onSpawn?: (child: ChildProcess) => void;
};

export type RunResult = {
  status: number | null;
  stdout: Buffer;
  stderr: Buffer;
};

const DEFAULT_MAX_BUFFER = 8 * 1024 * 1024;

// Default deadlines. Component builds get a separate, generous deadline from
// short metadata calls; callers may override either.
export const METADATA_TIMEOUT_MS = 120_000;
export const COMPONENT_BUILD_TIMEOUT_MS = 3_600_000;

export function redact(text: string, secrets: readonly string[]): string {
  let result = text;
  for (const secret of secrets) {
    if (secret.length > 0) result = result.split(secret).join("[redacted]");
  }
  return result;
}

function renderCommand(executable: string, args: readonly string[], secrets: readonly string[]): string {
  return redact(
    [basename(executable), ...args].map((part) => (part.includes(" ") ? JSON.stringify(part) : part)).join(" "),
    secrets,
  );
}

export class ProcessError extends Error {
  readonly command: string;
  readonly stdout: string;
  readonly stderr: string;
  readonly timedOut: boolean;
  readonly overflowed: boolean;
  constructor(options: {
    command: string;
    stdout: string;
    stderr: string;
    timedOut: boolean;
    overflowed: boolean;
    message: string;
  }) {
    super(options.message);
    this.name = "ProcessError";
    this.command = options.command;
    this.stdout = options.stdout;
    this.stderr = options.stderr;
    this.timedOut = options.timedOut;
    this.overflowed = options.overflowed;
  }
}

export async function runProcess(
  executable: string,
  args: readonly string[],
  options: RunOptions = {},
): Promise<RunResult> {
  const secrets = options.secrets ?? [];
  const maxBuffer = options.maxBufferBytes ?? DEFAULT_MAX_BUFFER;
  const command = renderCommand(executable, args, secrets);

  return new Promise<RunResult>((resolve, reject) => {
    let child: ChildProcess;
    try {
      child = spawn(executable, [...args], {
        cwd: options.cwd,
        env: { ...process.env, ...options.env },
        stdio: ["ignore", "pipe", "pipe"],
        detached: true,
      });
    } catch (error) {
      reject(
        new ProcessError({
          command,
          stdout: "",
          stderr: String(error),
          timedOut: false,
          overflowed: false,
          message: `failed to start ${command}: ${redact(String(error), secrets)}`,
        }),
      );
      return;
    }
    options.onSpawn?.(child);

    const chunks: [Buffer[], Buffer[]] = [[], []];
    let total = [0, 0];
    let overflowed = false;
    const attach = (stream: NodeJS.ReadableStream | null, index: 0 | 1) => {
      if (stream === null) return;
      stream.on("data", (chunk: Buffer) => {
        if (total[index] + chunk.length > maxBuffer) {
          overflowed = true;
          // Overflow kills the group immediately — never keep a partial
          // capture that could be parsed as a complete result.
          killGroup("SIGKILL");
          return;
        }
        chunks[index].push(chunk);
        total[index] += chunk.length;
      });
    };
    attach(child.stdout, 0);
    attach(child.stderr, 1);

    let settled = false;
    const timers: NodeJS.Timeout[] = [];
    const clearTimers = () => {
      while (timers.length !== 0) clearTimeout(timers.pop());
    };
    const killGroup = (signal: NodeJS.Signals) => {
      try {
        if (child.pid !== undefined) process.kill(-child.pid, signal);
      } catch {
        child.kill(signal);
      }
    };
    let timedOut = false;
    if (options.timeoutMs !== undefined) {
      timers.push(
        setTimeout(() => {
          timedOut = true;
          killGroup("SIGTERM");
          timers.push(
            setTimeout(() => {
              killGroup("SIGKILL");
            }, 5000),
          );
        }, options.timeoutMs),
      );
    }

    const finish = (error: ProcessError | null, status: number | null) => {
      if (settled) return;
      settled = true;
      clearTimers();
      if (error !== null) {
        reject(error);
        return;
      }
      // Resolve with the RAW captured bytes: callers hash binary payloads
      // (certificates) — redaction applies to error/diagnostic text only.
      resolve({ status, stdout: Buffer.concat(chunks[0]), stderr: Buffer.concat(chunks[1]) });
    };

    const fail = (message: string) =>
      new ProcessError({
        command,
        stdout: stdoutTail(),
        stderr: stderrTail(),
        timedOut,
        overflowed,
        message,
      });
    const tail = (index: 0 | 1): string => {
      const text = redact(Buffer.concat(chunks[index]).toString("utf8"), secrets);
      return text.length > 8192 ? `…${text.slice(-8192)}` : text;
    };
    const stdoutTail = () => tail(0);
    const stderrTail = () => tail(1);

    child.on("error", (error) => {
      finish(
        new ProcessError({
          command,
          stdout: "",
          stderr: redact(String(error), secrets),
          timedOut,
          overflowed,
          message: `child error on ${command}: ${redact(String(error), secrets)}`,
        }),
        null,
      );
    });

    child.on("close", (status) => {
      if (overflowed) {
        finish(
          new ProcessError({
            command,
            stdout: stdoutTail(),
            stderr: stderrTail(),
            timedOut,
            overflowed: true,
            message: `output exceeded the ${maxBuffer}-byte capture limit on ${command}; the result is incomplete and was discarded`,
          }),
          status,
        );
        return;
      }
      if (timedOut) {
        finish(
          new ProcessError({
            command,
            stdout: stdoutTail(),
            stderr: stderrTail(),
            timedOut: true,
            overflowed: false,
            message: `timed out after ${options.timeoutMs}ms: ${command}`,
          }),
          status,
        );
        return;
      }
      if (status !== 0) {
        const message = `non-zero exit ${status} from ${command}\n${stderrTail()}`;
        finish(new ProcessError({ command, stdout: stdoutTail(), stderr: stderrTail(), timedOut, overflowed: false, message }), status);
        return;
      }
      finish(null, status);
    });
  });
}

// Convenience wrapper: run and return captured stdout as UTF-8 text.
export async function captureProcess(
  executable: string,
  args: readonly string[],
  options: RunOptions = {},
): Promise<string> {
  const result = await runProcess(executable, args, options);
  return result.stdout.toString("utf8");
}
