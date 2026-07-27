## Why

Changing `vehicle-pedestrian-warning` (or any feature field) in `config.yaml`
while the app is running has no effect on the inject plan until the service
restarts. The pedestrian-warning feature is therefore not live-reloadable: the
user toggles the sound off in the UI, the config file updates, but
`inject.json` is not reproduced, so the daemon never sees the new plan and the
agent keeps the previous state until the next boot.

Root cause (verified statically in this session):

- `VoboostService.onStartCommand` loads the config once
  (`configManager.loadConfig()` → cached in `config: Config?`) and calls
  `startFeatures()` → `featureManager.applyConfig(...)` exactly once per
  service start. `VoboostService` never calls `configManager.startWatching(...)`.
- `ru.voboost.ui.ConfigState` (UI singleton) DOES call
  `configManager.startWatching(listener)` — but on its own separate
  `ConfigManager` instance, and the listener only updates UI-facing StateFlows
  (theme/language/selectedTab/panel values). It does NOT call
  `FeatureManager` / `applyConfig`.
- `Main.producePlan()` (`Main.kt:125`) reads the config and calls
  `featureManager.applyConfig(...)` — written exactly for this live-reload
  scenario — but has zero callers (the `Main` class is never instantiated by
  the service or the UI). Dead code.

So the config-changed signal exists (UI watcher) but is never wired to plan
production. The service is the only component that owns the `FeatureManager`
and the plan producer, so the live-reload must run there.

## What Changes

- `VoboostService`: after `startFeatures()` in `onStartCommand`, start a config
  watcher on the service's own `configManager`. The watcher's
  `onConfigChanged` updates the cached `config` and calls `startFeatures()`
  (which re-runs `featureManager.applyConfig` — idempotent: it re-reads every
  feature and reproduces `inject.json`). The watcher is stopped in `onDestroy`.
  This applies only for the `hidden` / `interface` startup modes (the `off`
  mode stops the service before any features start).
- `Main.kt`: delete the dead `producePlan()` (and the dead `Main` class if it
  has no remaining callers after the above change — verified: `Main` is never
  instantiated anywhere).

## Impact

- A running service now reproduces `inject.json` when the user changes any
  feature field (pedestrian-warning, settings-startup, etc.), without a service
  restart. The daemon picks up the new plan via its existing file-watch/reconcile
  loop and applies the per-agent `enabled` toggle live (start/stop the agent in
  the injected process).
- No change to the inject plan format, the daemon contract, or the UI watcher.
  The UI's own `ConfigState` watcher continues to drive UI reactivity
  independently; the service's new watcher drives plan production. They run on
  separate `ConfigManager` instances (no shared mutable state).
