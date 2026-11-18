## ADDED Requirements

### Requirement: Installer consumers select devices with one canonical runtime flag

Product acceptance consumers SHALL pass the currently selected serial to Installer observation, log collection and acceptance requests through the sole --device runtime flag. The product CLI contract SHALL NOT retain --device-serial as an alias or add a separate ADB server port option. Existing configuration, owned-session identity and Installer response schemas SHALL remain authoritative for the selected serial; host consumers SHALL NOT replace a selected serial with a fallback.

Exact argument proof guards SHALL admit the canonical invocation and reject a legacy flag, changed serial or additional target arguments. Native USB consumers SHALL use the same canonical device flag without an ADB server port argument.

#### Scenario: Acceptance consumers forward the current device

- **WHEN** an acceptance consumer selects either of two emulator serials or a native USB serial
- **THEN** the host constructs the exact --device invocation and preserves that serial across the child call and result identity check

#### Scenario: Retained argument proof differs

- **WHEN** retained evidence or a fault-window invocation contains --device-serial, another serial or extra target arguments
- **THEN** the exact proof guard rejects that input instead of admitting it as current canonical evidence

# Gate: node --test --test-name-pattern="runtime drivers forward the selected device with only the canonical flag" tools/emulator/installer-device.test.ts

Gate contract: Maps: Requirement `Installer consumers select devices with one canonical runtime flag`; Scenario `Acceptance consumers forward the current device`. Preconditions: Four real product runtime driver entrypoints run against a host process adapter and owned-session fixtures. Observable result: Every driver passes each selected emulator serial through exact --observe --device argv without a server-port option. Evidence boundary: Host invocation construction only; actual Installer transport and emulator device I/O remain separate.

# Gate: node --test --test-name-pattern="Installer UI preserves canonical device identity for emulator and native USB" tools/emulator/installer-device.test.ts

Gate contract: Maps: Requirement `Installer consumers select devices with one canonical runtime flag`; Scenario `Acceptance consumers forward the current device`. Preconditions: InstallerUi and a host child adapter are available. Observable result: Exact acceptance argv uses --device for two emulator serials and a USB serial, and a foreign result serial fails. Evidence boundary: Host invocation and receipt admission only; physical USB and diagnostic transport remain separate.

# Gate: node --test --test-name-pattern="provider forwards only the canonical runtime device" tools/emulator/provider-voboost.test.ts

Gate contract: Maps: Requirement `Installer consumers select devices with one canonical runtime flag`; Scenario `Acceptance consumers forward the current device`. Preconditions: Schema2 requests and a host child adapter are available. Observable result: Two explicit emulator serials and one USB serial each reach exact --observe --device argv without protocol extensions. Evidence boundary: Product consumer host contract only; no external producer or guest is run.

# Gate: node --test --test-name-pattern="i18n log proof rejects legacy and foreign device arguments" tools/tests/i18n-system-picker.test.ts

Gate contract: Maps: Requirement `Installer consumers select devices with one canonical runtime flag`; Scenario `Retained argument proof differs`. Preconditions: Complete raw i18n host fixtures contain canonical log invocation transcripts. Observable result: Canonical evidence admits while legacy flags, changed serials and extra port arguments fail. Evidence boundary: Host evidence admission only; Android and QNX acceptance remain separate.

# Gate: node --test --test-name-pattern="fault window rejects legacy and extra device arguments before dispatch" tools/emulator/fault-window.test.ts

Gate contract: Maps: Requirement `Installer consumers select devices with one canonical runtime flag`; Scenario `Retained argument proof differs`. Preconditions: Valid typed fault authority and hostile invocation fixtures are available. Observable result: The legacy flag, mismatched serial and extra target arguments fail before any child dispatch. Evidence boundary: Host argument admission only; guest fault behavior remains separate.
