# Minimal beta1 GUI acceptance

**English** | [Русский](installer-gui-test-cases.ru.md)

A case represents distinct behavior, not a screenshot. Record one PASS/FAIL/SKIP per ID, with reasons and evidence; record parameter outcomes separately. Environment preparation is a prerequisite, not a user case. Earlier evidence does not pass a new candidate.

Record candidate and diagnostic executable hashes, serial, theme and scale; verify session ownership and initial product state. One owner mutates the device through GUI. Independent status reads are allowed. Follow the [diagnostic launch restrictions](installer-gui-acceptance.md).

| ID | Actions | Acceptance |
| --- | --- | --- |
| G01 | Welcome → license → connection → preparation | Consent gates continuation; Back preserves correct state; model, versions and device admission are correct. |
| G02 | Clean install, finish, reopen installer | Accumulated milestones remain visible; duplicate mutations are blocked; installation survives required reboots; reopening reaches management. |
| G03 | Select hidden, none, interface and read configuration back | Each selection is persisted; selection does not immediately stop the running product. Next-boot behavior belongs to runtime acceptance. |
| G04 | Export logs, then cancel another save | Archive is readable and contains logs; cancellation is not an error and does not mutate the device. |
| G05 | Cancel Restore, then confirm it | Cancellation changes nothing; confirmation restores the product and reports the observed outcome. |
| G06 | Cancel Uninstall, then confirm it; reopen installer | Cancellation preserves installation; progress is visible and duplicate operations blocked; independent absence verification and clean-install route succeed. |
| G07 | Reinstall a compatible degraded installation of the same version | Explicit reinstall goes through license consent and repairs the product; unknown identity and incompatibility remain blocked. Another version is a host-test parameter, not another full GUI cycle. |
| G08 | No device → connect; also unauthorized and unsupported targets | Each parameter has a clear message and correct disabled actions; connection recovers routing. Record each cause separately. |
| G09 | Disconnect transport before mutation, reconnect | Stale discovery cannot start installation; discovery restores the valid route. |
| G10 | Disconnect during mutation, reconnect and perform permitted recovery/retry | No false success, concurrent mutation or uncontrolled retry; state is reread and recovery completes. Transport loss and VM power loss are distinct faults. |
| G11 | Shared controls once: RU/EN, both themes, default/minimum window size, Log off/on, copy, license menu | No clipping, wrapped buttons, inaccessible controls or notification-induced content shift; logs open/copy and licenses are readable. Also check Log availability and mutation locks during G02/G06. |

Run G01–G06 as one sequence. Do not reinstall for language, screenshots or Log variants. G07 requires a separate degraded baseline and cannot pass on a healthy product. Component tests parameterize backend errors for restore/uninstall rather than repeating the native route for every identical error; G10 remains a genuine integration check. Label fixtures explicitly. Emulator transport loss does not prove physical USB behavior.

For every ID record actions, expected/observed result, candidate, environment and evidence. Capture distinct main-route screens, dialogs and different errors; link the first capture when revisiting an unchanged screen. JSON records values and enabled states. Diagnostic intermediate frames are attachments, not new cases. Language, theme and Log are G11 parameters, not multipliers for every operation; check stable screens without reinstalling.

Illustration/icon-only changes do not require installation or vehicle FEV/PW reruns. Backend changes require the affected operation and recovery case; the final candidate runs the main route. Component tests cover consent, compatibility/version/health matrices, dialog cancellation, command errors, localization and shared controls. Local native GUI covers the cases requiring real backend operations; missing fault fixtures mean SKIP. Packaging independently excludes WebDriver and diagnostic transport from production. Screenshots support visual review, not device-state verification. This plan does not require an emulator in CI.

The root Java `GuiReport` in `tests/common` defines the host-side case, prerequisite, fixture, G08 parameter and G11 shared-control contract. A PASS requires a verified fixture and evidence; prerequisite, case or parameter failure disables subsequent mutations. A complete G08 PASS requires all three availability parameters. A complete G11 PASS requires G02 and G06 to pass, all shared-control parameters to pass, and each Log or mutation-lock parameter to be recorded with verified evidence inside an explicitly opened G02 or G06 operation window. The host contract records only this structural begin-and-complete sequence; it does not prove that the UI or backend actually ran, which remains dependent on the integration runner. Legacy skips require an explicit cause mapped to BLOCKED or NOT_RUN. This contract does not run GUI operations; full integration and emulator acceptance remain BLOCKED.
