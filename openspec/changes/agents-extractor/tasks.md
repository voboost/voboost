# agents-extractor

## 1. ScriptExtractor core

- [x] 1.1 `ScriptExtractor(paths, context)` reads `assets/agents/agents-manifest.json`
      bytes; `assetsSha = sha256(bytes)` (MessageDigest)
- [x] 1.2 Read `appZone/agents-extracted-version` → `storedSha` (null if absent)
- [x] 1.3 Idempotency gate: if `storedSha == assetsSha` AND every manifest
      `agents[].file` (as basename) exists in `staging/agents/` → return (no copy,
      no marker recreation)
- [x] 1.4 On change: `staging/agents/` mkdirs; for each `agents[].file` copy
      `assets/<file>` → `staging/agents/<basename>` (atomic write)
- [x] 1.5 Copy `assets/agents/agents-manifest.json` → `staging/agents-manifest.json`
      (atomic)
- [x] 1.6 If `assets/agents/agents-manifest.json.sig` exists → copy →
      `staging/agents-manifest.json.sig` (atomic)
- [x] 1.7 Create `staging/agents-update-ready` marker LAST (atomic)
- [x] 1.8 Write `assetsSha` to `appZone/agents-extracted-version` (atomic)

## 2. Wiring

- [x] 2.1 Callsite 1: `VoboostService.onStartCommand` after
      `config = configResult.getOrThrow()`, before `when (startupMode)`
- [x] 2.2 Callsite 2: `VoboostService.checkOtaUpdates` after `val staged = client.checkAndUpdate()`:
      `if (staged) scriptExtractor.extractIfChanged()`
- [x] 2.3 Construct `ScriptExtractor` where `paths` + `applicationContext` are
      available

## 3. Tests

- [x] 3.1 `ScriptExtractorTest` (Robolectric): extract copies all `.js` + manifest,
      creates `agents-update-ready`
- [x] 3.2 Idempotent: second call (storedSha matches + `.js` present) is a no-op
      (marker NOT recreated, files NOT copied)
- [x] 3.3 Self-healing: delete one `.js` → next extract restores it
- [x] 3.4 `.sig` absent → manifest copied, `.sig` not present, marker created
- [x] 3.5 `.sig` present → copied
- [x] 3.6 Tests are silent on success

## 4. Validation

- [x] 4.1 `./gradlew testUnit` passes
- [x] 4.2 `npx @fission-ai/openspec validate agents-extractor --strict` passes
- [x] 4.3 `npx @fission-ai/openspec validate --all --strict` passes
