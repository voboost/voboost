# Native GUI acceptance

**English** | [Русский](installer-gui-acceptance.ru.md)

The production installer has no automation server. The optional `gui-test` Cargo feature adds `tauri-plugin-wdio-webdriver =1.5.0` and a runtime-only capability. On macOS its HTTP listener binds to `127.0.0.1`. Do not distribute this build or enable the feature in release packaging.

Build the same frontend and packaged resources as the candidate, then build the native executable with `cargo build --release --locked --features gui-test --target aarch64-apple-darwin --bin voboost-installer`. Use the candidate's public role roots, product version, observation directory and staged ADB resources; no test mocks replace backend operations. The binary is a diagnostic test artifact, not the production DMG.

Start the diagnostic app with both `VOBOOST_GUI_TEST_SERIAL=emulator-PORT` and `TAURI_WEBDRIVER_PORT=PORT`. The feature refuses another serial in the shared backend parser and filters enumeration to that exact emulator. Missing/invalid configuration fails closed. Do not use this build against a vehicle.

Run `node tools/gui/client.ts PROJECT_REQUEST.json` from the repository. Paths are workspace-relative or absolute inside the workspace; existing parent directories are required. The client creates one W3C session, performs the ordered actions, then closes it. The app stays open. The client accepts only a loopback port and stores tree/PNG outputs inside the workspace.

```json
{
  "port": 4445,
  "actions": [
    { "op": "wait", "selector": "button", "timeoutMs": 120000 },
    { "op": "tree", "path": "plans/DATE/gui/startup.json" },
    { "op": "screenshot", "path": "plans/DATE/gui/startup.png" }
  ]
}
```

Supported actions are `tree`, `screenshot`, `click`, `value`, and `wait`. `click`/`value` use a CSS selector or an exact `target: {role, name}` accessible target; `value` supplies `text`. `wait` supports `visible`, `absent`, `enabled`, and `text` predicates, with a bounded timeout (120 seconds by default; conditions are polled every 100 ms). Screenshot optionally uses `selector` for the element endpoint. In upstream macOS plugin1.5.0 this endpoint still returns the full window rather than a crop; do not treat cropping as verified. Trees contain rendered controls and visible text, accessible labels, states, CSS bounds, text line counts and overflow flags. `root` points into `nodes`; each node has `index`, nearest included `parent`, and `children` references. The snapshot is bounded to 5,000 candidate nodes and reports truncation. They never read Zustand internals or acceptance tokens. Line counts traverse DOM text nodes and measure their ranges, excluding SVG icons. Native geometry still needs runtime validation. A per-port project lock prevents concurrent client ownership; each request closes only its transport session and leaves the UI open.

## Case set

The canonical minimal set is [G01–G11](installer-gui-test-cases.md). Repeated captures and shared preflight are not independent cases. The runner emits exactly G01–G11 and stores shared setup separately in prerequisites. Missing or incomplete automation is SKIP; availability parameter evidence is stored in parameters.G08. Stage counts do not establish complete acceptance.

## Automatic cycle

`node tools/gui/scenario.ts PROJECT_CONFIG.json` runs one local route: welcome/license, connection, install/reopen, three startup-mode values, restore cancellation/confirmation and uninstall cancellation/confirmation. Language is sampled on Welcome; Log is exercised on management and during install/uninstall. Do not repeat entire routes for languages or captures. Device mutations use real GUI controls; CLI only reads independent status. An external owner starts the emulator and diagnostic app.

```json
{
  "port": 4445,
  "serial": "emulator-5582",
  "candidateCli": "/Users/vitaly/voboost/PROJECT-CANDIDATE-CLI",
  "candidateCliSha256": "REPLACE_WITH_64_HEX_SHA256",
  "appExecutable": "/Users/vitaly/voboost/DIAGNOSTIC.app/Contents/MacOS/voboost-installer",
  "appSha256": "REPLACE_WITH_64_HEX_SHA256",
  "output": "plans/DATE/UNIQUE-GUI-RUN",
  "operationTimeoutMs": 600000
}
```

Use a fresh output directory for each run; its parent must exist. The cycle requires an absent product baseline; remove an older installed version through its installer GUI first. Before installation, the runner verifies the read-only CLI SHA256 and immutable test-only native app metadata: exact serial, port and schema. Production builds do not expose this metadata. Do not replace this check with manual DOM injection. On supported macOS the test-only WebView disables background throttling without activating the window or changing OS settings.

`report.json` is atomically updated after each stage with PASS/FAIL/SKIP, duration, target identity and JSON/PNG references associated with case IDs. After FAIL, remaining stages are SKIP and perform no mutations. An untested transport fault is never reported as passed. For the dedicated unavailable-device case, the external owner stops the assigned emulator, starts the UI from its beginning and sets `availabilityOnly: true`: the runner visits the licenses, verifies searching/disabled Next and Log off/on without installing. Physical USB loss remains a separate check.

The client supports `guard` with `serial`, and `settle`. `settle` attempts two animation frames and returns an explicit fallback after 350 ms when frames do not arrive in the background; its HTTP call is bounded to 5 seconds. Visibility and active finite animations are recorded. Capture compares DOM before/after PNG and allows up to three uniquely named attempts; unstable results are not accepted. Every PNG has visualStatus REVIEW_REQUIRED: neither fallback nor stable DOM proves pixel correctness, so humans inspect it. Ordinary screenshot timeout remains 120 seconds. Run stage failure and missing-fixture regressions with `node --test tools/gui/client.test.ts tools/gui/scenario.test.ts`.

After a successful installation, the runner clicks Next, captures completion with Log off/on, clicks Finish, waits for app exit and reopens the same SHA256-verified diagnostic `.app` through `open -g -n` with the same serial/port. This background app relaunch does not alter VM lifecycle. Automatic app relaunch currently supports macOS only. Optional `beforeUninstallGate` names a project JSON file: the runner waits for `{"continue":true}` before uninstall so the external owner can collect additional observations. The wait is bounded by `operationTimeoutMs`.

The runner rejects a Full diagnostic details checkbox, checks compact logs and adjacent Copy controls. Mutations remain disabled during install/uninstall while Log stays available. An incomplete theme, resize and license-menu matrix is not reported as a G11 pass.

## Native visual capture without focus

Set `VOBOOST_GUI_TEST_VISUAL=1` only when launching a `gui-test` build for genuine visual acceptance. The window is ordinary, visible and unfocused; it never stays above other applications. Launch it with `open -g` and leave it behind the user’s working windows. This is not an invisible background capture. Leave the variable unset for the default test-window behavior; other values are rejected. Production builds do not read this variable or change their window ordering.

WebKit can report `document.hidden=true` and leave finite Fluent animations pending at time zero when a test window is fully occluded, even with inactive scheduling disabled. A DOM node or a two-frame timeout is not evidence of rendered pixels. In visual mode verify document visibility, completed finite animations and the real PNG. `tools/gui/dialog-diagnostics.ts PORT EXISTING_SESSION PROJECT_REPORT.json` records dialog and ancestor styles, overlap, animation clocks and a bounded finite-animation wait without changing focus or animation time.

## Visible operation progress and layout

The main window starts at 1060×740, can be resized, and has a 900×640 minimum. Short laptop viewports use compact spacing; management facts share two columns when width permits. Keep overflow as a fallback for smaller viewports, long diagnostics and license/log content rather than clipping controls. Ordinary screens must be checked at the default size and at 1280×800 desktop resolution.

During install, restore and uninstall the visible spinner caption follows closed backend milestones: preparation, file transfer, applying changes, restart/reconnection, verification and cleanup. Detailed transport diagnostics remain in the log. These captions describe the current observed phase; they do not invent percentages or claim skipped phases completed. Every gui-test window starts unfocused, including without visual mode; production user launches retain normal behavior. Background captures with advanced hidden finite animations do not prove real animation duration.
