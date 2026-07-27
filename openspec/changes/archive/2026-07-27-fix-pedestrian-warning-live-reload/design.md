## Context

The inject plan (`inject.json`) is the app→daemon contract: the app produces
it in its app zone, the daemon reads it, reconciles per-process, and
injects/removes agents. The plan is produced by `FeatureManager.applyConfig`,
which collects enabled features and writes the plan via `PlanProducer`.

Today `applyConfig` runs once, at `VoboostService.onStartCommand`, against the
config loaded once at service start. There is no path from a mid-run config
change to `applyConfig`. `ConfigState` (UI) watches the config file but only to
refresh UI StateFlows; the service owns the `FeatureManager` and never watches.

`FeatureManager.applyConfig` is idempotent: it re-evaluates every feature's
`shouldEnable`, rebuilds the agent entry list, and rewrites `inject.json`
atomically. Calling it again on a config change is safe and is exactly what
the dead `Main.producePlan()` was written to do.

## Goals

- Reproduce `inject.json` when `config.yaml` changes while the service runs.
- No service restart, no re-injection of already-running agents beyond the
  daemon's normal per-`enabled` toggle start/stop.
- Keep the UI watcher (`ConfigState`) unchanged — it owns UI reactivity on its
  own `ConfigManager`.

## Non-Goals

- Changing the plan format or the daemon contract.
- Replacing the separate `ConfigManager` instances (service vs UI) with a
  shared one — out of scope; two watchers on one file are independent and fine.
- General "FeatureFrida" rename / Frida-to-ptrace text cleanup — separate pass.

## Decisions

### Decision: watcher in the service, not the UI

The `FeatureManager` and `PlanProducer` live in the service. Wiring the UI's
`ConfigState` watcher to plan production would require the UI to reach into
service-owned state (or a shared singleton) — a larger coupling change with no
benefit. The service already owns everything `applyConfig` needs. A second
watcher on the service's own `configManager` is the minimal, localized fix.

Rejected: drive plan production from the UI watcher. Couples UI→service,
requires a shared `ConfigManager` or IPC, and the UI process is not always
alive when the service is (the service runs on boot; the UI may be closed).

### Decision: re-run `applyConfig` on every change, not diff

`applyConfig` is already a full re-evaluation (it rebuilds the whole agent
list and rewrites the plan atomically). A diff-based partial update would be
more complex and error-prone for no gain at the current agent count. The plan
is small (<1 MiB cap) and the daemon's reconcile is already idempotent.

Rejected: only re-apply the changed feature. Requires diffing and partial plan
patches; the full rewrite is cheap and already correct.

### Decision: delete the dead `Main` class

`Main` (`Main.kt`) is never instantiated anywhere (grep for `Main(` across the
repo returns no construction sites). Its `producePlan()` is the exact logic
this change moves into the service watcher; its `start()`/`shutdown()`/
`readStatus()` are unused (the service inlines equivalent logic). Deleting it
removes the confusion of a second, unused orchestration path.

Rejected: keep `Main` and call it from the watcher. `Main` would then own
`FeatureManager`, but the service already constructs and owns its own
`FeatureManager` — two instances would race on the same plan file. The service
is the single owner.

## Risks

- **Two watchers on one config file**: the UI `ConfigState` watcher and the new
  service watcher both watch `config.yaml` on separate `ConfigManager`
  instances. `inotify` delivers to both independently; no shared mutable state.
  Verified the `ConfigManager` watcher is per-instance (`currentListener` is an
  instance field, not static).
- **Watch firing during shutdown**: `onDestroy` stops the watcher before
  `stopFeatures`, so no `onConfigChanged` can arrive mid-teardown.
- **`off` startup mode**: the service stops itself before `startFeatures`, so
  the watcher is never started in that mode.
