# Design

Preparation establishes the required persistent guest state and closes any preparation session. Startup launches a separate owned test session and publishes its identity. Test execution consumes the started session and selects cases by group or class/method pattern. Cleanup retains any session whose operation has an unknown outcome.

The combined run resolves both roles before guest mutations and owns the candidate mount for both. It runs the installer lane before the agent lane when the canonical machine paths coincide. Distinct machine paths use distinct owned sessions. Standalone stages share a private run directory and require the previous stage's receipt.

The installer lane starts on stock and can use a separately supplied previous DMG for the update case. The agent lane verifies the requested installed candidate and installs it only when needed. Each agent case controls its own stimulus and observations.
