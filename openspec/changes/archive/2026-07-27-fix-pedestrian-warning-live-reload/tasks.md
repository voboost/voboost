## 1. Implement the live-reload in VoboostService

- [x] 1.1 In `VoboostService.onStartCommand`, after the `startFeatures()` call
  in the `hidden` / `interface` branches, call
  `configManager.startWatching(serviceConfigListener)` where the listener's
  `onConfigChanged(newConfig, diff)` updates `this.config = newConfig` and calls
  `startFeatures()` (re-runs `applyConfig`). `onConfigError` logs.
- [x] 1.2 In `VoboostService.onDestroy`, call `configManager.stopWatching()`
  before `stopFeatures()` so no config-changed callback can arrive mid-teardown.
- [x] 1.3 Ensure the watcher is only started in `hidden` / `interface` modes
  (the `off` branch stops the service before `startFeatures`, so it never
  reaches the watcher start).

## 2. Remove the dead Main class

- [x] 2.1 Confirm `Main` (`Main.kt`) has no construction sites across the repo
  (`grep -rn "Main(" --include=*.kt` excluding the class definition).
- [x] 2.2 Delete `Main.kt` and remove any now-unused imports it was the sole
  user of.

## 3. Verify

- [x] 3.1 `./gradlew build` passes (compile + unit tests).
- [x] 3.2 Static check: the service watcher calls `applyConfig` on
  `onConfigChanged`; `off` mode never starts the watcher.
- [ ] 3.3 (runtime, if reachable) On the emulator: start the service, toggle
  `vehicle-pedestrian-warning` in the config, confirm `inject.json` is
  reproduced and the daemon reconciles without a service restart. Marked
  `## Unverified` in the spec (device pass not done this session).

## 4. OpenSpec

- [x] 4.1 `npx @fission-ai/openspec validate fix-pedestrian-warning-live-reload
  --strict`.
- [ ] 4.2 Archive the change (`openspec archive`) and commit.
