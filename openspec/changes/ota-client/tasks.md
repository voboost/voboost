# ota-client (APK-level)

## 1. Release-manifest = APK list (verify)

- [x] 1.1 `ReleaseFileEntry` трактуется как APK-запись: `path` = имя APK,
      `channel ∈ {app, core}`, `sha256`, `size`, `version` (semver)
- [x] 1.2 `OtaVerifier` (ed25519 detached, BouncyCastle, API 28) — без изменений:
      verify release-manifest.json+.sig против `release-public.pem`; struct-
      validation (missing field / bad channel); bounds (≤ 1 MiB, ≤ 4096 entries)
- [x] 1.3 Никогда не персистить упавший манифест как текущий (D6)
- [x] 1.4 Unit: valid/invalid signature, struct-reject, oversize

## 2. Version compare (version-gated download)

- [x] 2.1 Текущая версия **app** — `BuildConfig.VERSION_NAME`
- [x] 2.2 Текущая версия **daemon** — из `inject-status.json` (`daemon` field)
- [x] 2.3 Semver compare: APK в манифесте newer установленной → кандидат на
      download. Нет version в entry → skip channel
- [x] 2.4 Unit: newer/older/equal для app и core; missing daemon version

## 3. Whole-APK download + verify (download)

- [x] 3.1 `OtaDownloader` — reuse: download APK целиком; size pre-check (reject
      при несовпадении `size` ДО хеширования); verify `sha256`
- [x] 3.2 Unit: size-mismatch reject, sha-mismatch reject (MockWebServer)

## 4. Apply: `app` channel — install

- [x] 4.1 Stage verified APK в app-zone `staging/`
- [x] 4.2 Invoke installer: `Intent(ACTION_INSTALL_PACKAGE)` /
      `PackageInstaller`, или `voboost-install` — replace running app
- [x] 4.3 Никакого marker для app — apply заканчивается install-intent
- [x] 4.4 Unit: install-intent build; staging path

## 5. Apply: `core` channel — stage + signal daemon

- [x] 5.1 Stage verified daemon APK в app-zone `staging/`
- [x] 5.2 Single-use marker (e.g. `core-update-ready`) создаётся последним —
      сигнал voboost-inject self-update (D7). Демон потребляет (удаляет) marker
- [x] 5.3 App НЕ устанавливает daemon — только stage+signal
- [x] 5.4 Unit: marker-created-last; single-use

## 6. Remove file-level OTA (cleanup)

- [x] 6.1 Удалить `OtaIncremental` (per-file diff — не нужно)
- [x] 6.2 Удалить `DaemonManifestProducer` (manifest build-time в daemon APK)
- [x] 6.3 Удалить content-addressed agents staging из `OtaStagingWriter` →
      переименовать/упростить в `ApkStager` (stage whole APK)
- [x] 6.4 Удалить `agentProcessMap`/`buildDaemonAgents` wiring (больше не
      строим daemon manifest в клиенте)

## 7. Client core + tests

- [x] 7.1 `OtaClient.checkAndUpdate()`: fetch+verify manifest → version compare
      → download newer APK(s) → apply per channel; retry/backoff для transient
- [x] 7.2 `OtaConfig`: `baseUrl`, `publicKeyFile`, `currentAppVersion`,
      `currentDaemonVersion` (или reader)
- [x] 7.3 Robolectric/JUnit покрывают verify/version-compare/download/apply
- [x] 7.4 `npx @fission-ai/openspec validate ota-client --strict` проходит
