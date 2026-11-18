# Common Voboost glossary

This is a semantic reference, not a mandatory replacement list. It defines distinctions for product documentation, OpenSpec, handoffs, and engineering notes. Keep exact API, file, command, format, field, and value names unchanged.

<!-- term: delivery-steps -->

- **`commit`, `push`, publication, and release.** A `commit` records a change in local Git history; `push` transfers Git references to a remote; publication makes a specific version or artifact available through an explicit mechanism; a release is an approved, versioned delivery set. No step proves that any of the others happened.

<!-- term: source-of-truth -->

- **Source of truth.** The current authoritative input for a specific claim: for example, applied OpenSpec for a contract, source or a generator for an implementation, or a signed artifact for delivered bytes. Name it explicitly.

<!-- term: evidence -->

- **Evidence.** A dated, reproducible result that identifies the revision, artifact, environment, and gate. It supports only the scope actually checked.

<!-- term: plan -->

- **Plan.** An intention and sequence of future actions. Even an accepted plan is not evidence of implementation or readiness.

<!-- term: runtime-roles -->

- **Daemon, injector, implant, and agent.** A daemon is a long-running orchestrator process; an injector inserts a payload into a target process; an implant, already inside that process, loads the runtime; an agent implements one product behavior in that runtime. These are roles, not interchangeable names.

<!-- term: update -->

- **Update.** One Voboost product transaction identified by a new Product SemVer and complete signed `voboost.json`. The app downloads changed artifacts; `voboost-daemon` verifies and activates the complete candidate by role. This is not an independently versioned app, agent, or native release.

<!-- term: manifest-trust -->

- **Product, agent, and restore authorities.** `voboost.json` binds the complete release and artifact identities; `agents.json` binds agent semantics, target placement, and DEX identities; `restore.json` grants narrow installed recovery authority. Each document has its own trust role and is not a duplicate component manifest.

<!-- term: gate -->

- **Gate.** A check with explicit preconditions, an action, and an expected result. A green gate permits only the conclusion named in advance.

<!-- term: environment-gates -->

- **Host, emulator, device, and vehicle gates.** A host gate runs on the workstation, an emulator gate on virtual Android, a device gate on a named physical device, and a vehicle gate in the vehicle's OEM environment. A result at one level does not substitute for another.

<!-- term: evidence-baselines -->

- **Reference, baseline, and hardware evidence.** A reference is comparison material; a baseline is a dated starting snapshot; hardware evidence is a log or measurement from explicitly identified physical hardware and firmware. A reference is not automatically authoritative, and a baseline is not a permanent norm.

<!-- term: language-locale -->

- **Language and locale.** Language identifies the text's language. Locale is a set of regional conventions that may include language, country, and formatting rules. They are not equivalent.

<!-- term: infotainment -->

- **Infotainment.** English product documentation uses “infotainment system.” Russian product documentation uses «мультимедиа», not «мультимедийная система».

<!-- term: build-ready -->

- **Build-ready.** The named revision reproducibly builds the exact artifacts and passes the declared host build and integrity gates. This does not prove runtime behavior on a device.

<!-- term: device-ready -->

- **Device-ready.** The exact artifacts are installed and pass the declared gates on the named physical Android device. This does not prove vehicle integration.

<!-- term: release-ready -->

- **Release-ready.** The exact delivery set passes every mandatory gate for the declared delivery environment, including signatures and physical integration when the contract requires them. This is readiness to publish or deliver, not proof of publication or deployment.

<!-- term: device-operation -->

- **Device operation.** An install, restore, or uninstall operation whose state is owned by the device. The installer starts a fixed signed program without arguments, reconnects after reboots, and observes the same operation through verified completion.

<!-- term: fixed-staging -->

- **Fixed staging.** A closed set of verified files for one installation under `/data/voboost/install/<transactionId>`. It contains no UI-supplied commands or host-controlled phases.

<!-- term: absence-check -->

- **Absence check.** The final uninstall check after reconnection. Success requires the absence of `/data/voboost`, the `ru.voboost` package, startup programs, SELinux rules, permissions, and Voboost settings.

<!-- term: license-summary -->

- **License summary.** A short explanation shown before the full MIT license in the GUI and CLI. It does not replace or add conditions to the license.
