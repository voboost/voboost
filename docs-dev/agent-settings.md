# `settings` agent and OEM surface

**English** | [Русский](agent-settings.ru.md)

The `settings` agent is the single host for the Voboost surface inside `com.qinggan.app.setting`. Source lives in [`voboost-agents`](../../voboost-agents/src/main/java/ru/voboost/agents/settings); there is no separate `ui` module or `ui-res` resource-APK pipeline.

## Ownership

Only the host agent discovers the OEM fragment factory, parent container, constructors, theme, and symbolic resources. Feature agents register semantic contributions through `AgentSettingsTabRegistry`; they never install their own menu hook or construct a parallel Settings hierarchy.

The host publishes contributions transactionally. A route becomes visible only after the complete surface has been created and bound. Missing, partial, or ambiguous capability triggers cleanup and returns an exact unsupported reason without a fallback View.

## OEM adapter

The production adapter discovers one complete target-specific factory contract from live OEM objects and symbolic resources. It creates the Voboost and Logs routes only after the constructor, container, theme, resources, and contribution registry bind successfully.

Positive, partial, ambiguous, recreation, cleanup, theme, resource, and mutation-acknowledgement fixtures verify the host boundary. Physical geometry remains a named vehicle gate.

## Contributions and state

One contribution contains a stable semantic tag and order. Labels, options, desired state, availability, and target statuses arrive through typed daemon transport. A mutation remains pending until its correlated owner Saved receipt confirms persistence. A failed checkbox retains the latest user choice for retry; runtime application is acknowledged separately. Saving an enabled catalog alternative releases only older conflicting input and requests owner readback for its neighbor. Request tokens protect newer edits, and the accepted `userRevision` fences older replies.

The surface never reads `/data/voboost`, agent DEX, or manifests directly. `UninstallActivity` in `voboost-app` remains the separate Android confirmation boundary for removal requests.

## System language

After the version and startup controls, Localization is the first feature section, above Vehicle. It contains cataloged `i18n-ru` and `i18n-en` entries while preserving their `interface` category and checkbox contract. Both agents can be enabled together; disabling one preserves the other. These checkboxes enable agents; language selection belongs to the stock Display page; canonical intent and the Settings projection have no language override. Android configuration and locale broadcasts refresh Voboost copy from the actual system locale. The baseline supports Simplified Chinese and English fallback; Russian copy remains unavailable as a system selection until its complete translation runtime is admitted.

The exact Free A9 Settings adapter repairs unknown locale requests through the original English setter and signal branches. The original presenter then owns its English radio, QGSettings and SP writes together. Manual EN/ZH requests remain unchanged. This adapter does not claim A11 native Russian, resource APK, or QNX translation admission.

## Verification

The Settings gate verifies complete, partial, and ambiguous discovery; publication and cleanup; recreation; theme and resource lookup; mutation acknowledgement; and physical geometry on the named firmware. A failed structural check returns the exact unsupported reason without publishing a partial surface.

## Related pages

- [Agent runtime](agents-runtime.md)
- [Application](application.md)
- [Contracts](contracts.md)
- [Testing](testing.md)
