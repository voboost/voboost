# Intermediate beta0 state

**English** | [Русский](beta0-state.ru.md)

`voboost-1.0.0-beta0` identifies an intermediate source snapshot. The product release identity remains `1.0.0-beta1`. This snapshot does not establish QNX readiness or completed beta1 acceptance.

## QNX

[The QNX inject implementation](../../voboost-inject/cluster) contains the cluster daemon, transport, signed-module verification and startup implant loading. The current path uses `LD_PRELOAD` for `/usr/bin/gauge`; injection into an arbitrary already-running process has not been demonstrated.

[The runtime and Cyrillic agent](cluster-font-runtime.md) intercept FreeType font loading. Host checks and cross-builds do not establish cluster operation. Static compatibility with the original Free 6.0.3 fonts was checked separately. Successful application must be demonstrated by actual FreeType face creation, not merely desired-state delivery.

Enabling after original faces have been created and disabling after application require a new cluster process. Automatic gauge restart is not implemented. The complete QNX enable, apply, disable, original-font restoration and reboot cycle has not passed runtime acceptance.

[The Android courier](../../voboost-app/src/main/java/ru/voboost/cluster) and cluster-state delivery to Settings are implemented. End-to-end Android → QNX → Settings operation, key provisioning, installation and final removal on a running QNX system have not been accepted. Generated QNX build evidence explicitly records `qnxExecuted: false` and open startup, TLS/UI and final-removal checks. Bundled QNX files do not establish installation or execution.

## Android and installer

The catalog contains seven logical agents: Cyrillic font, two keyboards, launcher, Settings, Forced EV and Pedestrian Warning, with shared runtime/UI dependencies. Launcher extensions and the Cyrillic QNX target are retained as the current implementation; the latest combined scope has not completed runtime acceptance. Deferred agents do not become shipping features through this snapshot.

FEV/PW persistence and restoration were previously confirmed on the vehicle. Android Video was checked through warm and cold starts after fixes. These results do not establish acceptance for every process or firmware.

The build284 GUI run confirmed installation, startup-mode management, Restore and removal, with independent absence checks. Not all 11 GUI scenarios have passed. Forced reinstallation with `--force` is deferred and is not claimed to work successfully.

The latest packaged DMG286 does not contain the subsequent source fix for `agentCatalogSchema` validation. A new build from the agreed source composition is required before tester delivery. This state has not been built by remote CI or published.

## Release preparation limits

The build workflow accepts two macOS DMGs, but publication and aggregation workflows still expect the previous single-DMG set. These workflows must be aligned before release. Native ADB overlay provenance and per-file modification notices have not completed review.

Component revisions in release/version.toml still point to earlier commits. The source definition must be aligned separately after intermediate commits; the current dirty-tree snapshot cannot be reproduced from those earlier pins.

The current QNX uninstaller restores startup integration, requests daemon shutdown and returns `ReloadRequired` while retaining resident files. Final cleanup establishing complete product absence is not implemented.

The build workflow also lacks the mandatory `qnxLibraries` inputs already required by the compositor. CI cannot build this composition until its configuration and verified QNX inputs are prepared.
