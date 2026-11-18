# Logs

**English** | [Русский](logs.ru.md)

Voboost uses one persistent diagnostic stream. Only voboost-daemon writes shared logs in /data/voboost/logs. Inject authenticates application and agent producers and forwards their records over the private owner protocol; it does not write the log files.

## Levels

Exactly three levels are allowed:

- `debug` — checks, decision branches, retries, and skipped actions
- `info` — successful state changes and completed stages
- `error` — an action was not performed, failed, or requires recovery

There is no `warning` level. The record producer must select one of the three levels before sending it.

## Sources

The daemon derives `source` from the authenticated transport instead of accepting it as a trusted producer field:

| Source | Transport | How it is determined |
| --- | --- | --- |
| `daemon` | internal process queue | the component is known inside the daemon |
| `agent` | pipe for a specific process runtime | the connection is bound to PID/starttime, generation, and agent ID |
| `app` | dedicated `ru.voboost` socket | peer credentials and package process bind the connection to the application |
| `qnx` | paired Android–QNX channel | the authenticated peer and its source session bind the records to the instrument cluster |

An agent cannot write a message on behalf of another agent, the application, or the daemon. `ru.voboost` cannot supply an arbitrary `source`. App connections are served serially with bounded two-second reads and responses; a stalled producer cannot block later batches indefinitely.

## Record format

After accepting a message, the daemon creates a canonical record:

```json
{
    "schemaVersion": 2,
    "bootId": "11111111-1111-4111-8111-111111111111",
    "writerId": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
    "sequence": 1042,
    "timestamp": "2026-09-01T08:15:30.123Z",
    "source": {
        "kind": "agent",
        "id": "vehicle-drive-forced-ev"
    },
    "level": "debug",
    "component": "contract",
    "message": "provider read matched",
    "fields": {
        "predicate": "forced-ev-state",
        "result": "MATCH"
    }
}
```

`sequence` increases within one writer incarnation. The record identity is `(writerId, sequence)`; `bootId` binds it to an Android boot. A daemon restart creates a new writer ID and starts its sequence again. `timestamp` is acceptance time and does not establish event order. `source.id` is required for an authenticated agent.

Installer and fallback stderr records use producer-local sequences. QNX batches carry a source session and sequence before the Android daemon assigns its canonical record identity. Their numbers cannot establish an order across processes or writer sessions. Installer CLI diagnostics use JSONL on stderr, leaving stdout for command results and protocols. `VOBOOST_LOG=debug` enables transport detail; the default threshold is `info`.

Owners using change-based diagnostics emit repeated wait observations at `debug` when their diagnostic context changes. Duplicate suppression affects diagnostic output only; checks, retries, and state publication continue. A changed identity, reason, or lifecycle opens a new episode. Independent failures retain separate `error` records.

One record must not exceed 16 KiB after canonicalization. Field count and key and string lengths have closed bounds. Unknown fields, control characters, invalid UTF-8, and a level outside the closed list are rejected.

## Application logs before daemon startup

`ru.voboost` may start before the daemon, so it stores only an unacknowledged queue in:

```text
/data/user/0/ru.voboost/logs
```

This is not a second persistent log or a separate read endpoint. The application briefly groups records in memory, then atomically creates a `batch-<128-bit-id>.json` file with `fsync`.

A batch closes when the first condition is met:

- an `error` record is added
- 250 ms have elapsed since the first record
- 64 records have accumulated
- canonical size has reached 256 KiB

The application sends the batch to the daemon and waits for up to three seconds for an ACK containing the same 128-bit ID. The daemon first persists the records and durably records the accepted ID, then returns the ACK. After the ACK, the application deletes the file. A timeout or broken connection keeps the same batch for retry, while a retry of an already accepted ID returns the same ACK and creates no duplicates. Enumeration, reading, truncation, and removal are relative to a retained directory descriptor and accept only application-owned, single-link, mode-`0600` regular files. A symbolic link, hard link, or inode replacement is rejected without reading or deleting its target.

The application directory is limited to 2 MiB. On overflow, the oldest `debug` records are deleted first, followed by `info`. If only `error` records remain, the oldest record is deleted and one `error` record reports the lost messages. The new overflow record must not recursively create more records.

## Daemon storage

The shared log consists of three independent streams, each limited to 128 MiB: the canonical all-levels stream (`all/debug`), the combined `info`+`error` stream, and the `error`-only stream. Each stream holds sixteen completed or active JSONL segments of up to 8 MiB each, so the worst case for the three streams together is 384 MiB. The daemon writes only to the active segment of each stream, performs `fsync` according to a bounded flush policy, and closes a segment before rotation.

When the limit is reached, the oldest completed segment is deleted. An active segment or a file with an invalid owner, mode, type, or link count is not deleted as ordinary rotation and puts logging into a constrained error state without affecting agent operation.

After an append or synchronization failure, the daemon closes that writer's files. A later record retries recovery with a delay starting at 250 ms and capped at five seconds. Recovery creates a new writer ID and starts its sequence at one; it never appends to the potentially torn old tail. The daemon, application batches and QNX batches can all trigger this recovery. Existing immutable batch receipts retain their original record identities when retried.

Logs are not part of `state/`, `operation.json`, or the system-operation journal. Log overflow or corruption does not block agent startup, install, update, or restore.

## Log queries

The storage owner does not expose live query/follow operations. Use the bounded export endpoint. Consumers must scope their cursor to an authenticated writer session rather than treating a sequence as global.

## Export through voboost-installer

For an installed product, **Download logs** is available on Management; an unrecognized installation offers it on its diagnostic screen. The Rust backend owns the native folder picker. The CLI uses:

```text
voboost-installer --logs <directory>
```

The CLI and GUI use the same reader for trusted files at fixed paths. It reads existing product files, including retained segments under `/data/voboost/logs` and available installation progress, directly over ADB. Collection works even when the daemon executable is missing. The reader bounds and validates data on the host, including complete JSONL records and their identities. It requires a rooted shell for private sources and checks file metadata. It leaves unacknowledged batches in the application's private directory.

The collector reads available passive Android diagnostics before requesting `adb root` if needed. It binds the root request and reconnect to the selected device, verifies the resulting shell UID and retries unfinished reads with bounded waits. It keeps observations from different Android boots distinguishable by boot ID. A missing file, an empty file and a failed read have distinct outcomes.

The installer writes one archive on the computer.

```text
voboost-logs-<UTC-date>-<UTC-time>-<32-hex-random-token>.tar.xz
├── manifest.json
├── records.jsonl
└── context.json
```

The UTC timestamp uses `YYYY-MM-DD-HHMMSS`. The manifest keeps `schemaVersion: 1` and `format: "VBOOSTLOG1"`. It records the sizes of the two payload files and binds the collection to the selected device when known. `context.diagnosticBundle.sources` records per-source outcomes, reasons, time, boot ID and volume. The installer includes its current session in the archive. It creates no archive or temporary directory on the vehicle and does not acquire `operation.lock`.

If some vehicle data was captured and a source failed, the collector saves what it read and reports `partial`. If it captured no usable vehicle data, it reports `failed` and includes the local installer log in the archive. The installer also tries to save a separate local log in that case. Both outcomes return a nonzero CLI exit code. The GUI reports the collection result and archive path. Confirmed absence of product files on a clean system is an expected source result.

## Instrument cluster events

The Android `voboost-daemon` is the only persistent writer of Voboost events, including records with source `qnx`. QNX buffers events in a bounded RAM queue and forwards them over the paired channel. The daemon acknowledges a batch after persisting it. A retry after a lost acknowledgement uses the same source session and sequence to avoid duplicates. Undelivered events can be lost if the queue fills or the cluster loses power.

Within one QNX daemon process, retries return identical batch bytes until an exact acknowledgement arrives. Drained slots remain reserved in shared RAM until that acknowledgement. After a daemon process restart, those unconfirmed slots become loss markers and contribute to `dropped` in the next source session; the markers and overflow watermark remain in RAM until acknowledged. This count can include a batch already persisted on Android when QNX had not finished processing its acknowledgement. It is explicit accounting of discarded unconfirmed events, not exactly-once delivery across process restarts. A QNX reboot or power loss clears this RAM state. QNX creates no filesystem log.

The collector reads these events from Android and records the available delivery state. It distinguishes a connected cluster with no delivered records from unavailable delivery or a profile without a supported cluster.

`qnx-delivery.json` retains the durable source position, a static `failureCategory` (`configuration`, `transport`, `timeout`, `protocol` or `storage`) while delivery is unavailable, and `lastSuccessAt` when a successful pull has been observed. Categories contain no peer-provided text or credentials. Repeated failures with the same category do not rewrite the health file or add repeated wait messages. A successful pull clears the failure category; its time is kept in memory while connected and persisted on a status change or batch publication. After an abrupt Android daemon exit, the persisted time can predate later successful idle polls.

## Privacy

The log does not contain:

- private or one-time keys
- authorization tokens
- complete HTTP headers
- contents of user files
- VIN or account identifiers
- arbitrary memory dumps

Before persistence, the daemon redacts known token forms and bounds every field. The archive belongs to the user and is never sent anywhere automatically.

## Removal

Uninstall removes the application queue, shared log, completed subscriptions, and all temporary export files. An archive previously saved on the computer does not belong to the vehicle and is not deleted automatically.

## Implementation sources

- `voboost-app/src` — application queue, batch schema, and ACK client
- `voboost-agents/src` — stable agent diagnostics
- `voboost-inject/daemon` — canonical records, storage, queries, and export
- `voboost-installer/src-tauri/src/diagnostic_collection.rs` and `diagnostic-read.sh` — shared ADB collection and archive results
- OpenSpec capabilities `app-logging`, `runtime-observability`, and `operation-adb-execution`

## Required checks

- source spoofing and peer mismatch
- repeated batch ID without duplicates
- limits of 64 records, 256 KiB, and 250 ms
- application queue overflow and nonrecursive loss record
- segment rotation and malformed filesystem objects
- query bounds, filters, cursors, stale request, and stop
- absence of the `warning` level
- bounded ADB reads, fixed paths and file metadata validation
- passive evidence retained across root failure or interrupted USB reads
- partial and failed CLI outcomes and the shared GUI result
- QNX batch retry, deduplication and loss of pending events
- daemon-unavailable context
- absence of application-private batches from export
- absence of export residue on the vehicle

Host tests do not prove the vehicle Settings UI, real SELinux behavior, or diagnostics on a physical device. Emulator and device gates perform those checks.

## Next

- [settings agent](agent-settings.md)
- [Contracts](contracts.md)
- [Application](application.md)
- [Native runtime](native.md)
- [Installer](installer.md)
- [Testing](testing.md)
