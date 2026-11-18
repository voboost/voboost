# Change: Authoritative user desired state

## Why

A user choice can currently wait behind product operation ownership, lose its reply, or be overwritten by a prepared product snapshot. Desired persistence needs independent owner admission and durable per-field ordering before runtime integration.

## What Changes

- Make schema-2 `/data/voboost/agents.json` the sole desired authority, with atomic semantic `SaveDesired`, owner revisions and per-field input identities.
- Separate saved receipts from applied results; retain unsupported desired values and prevent product publication, rollback or restore from replacing stamped choices.
- Give OFF immediate runtime revocation and fence stale jobs, callbacks and retries without claiming cancellation of an already executing OEM operation.
- Require honest storage-failure and three-layer evidence boundaries. No schema-1 history migration or second desired journal is introduced; clean reinstall is allowed.

## Impact

Product verification requirements apply before component runtime integration. Owning implementations are inject owner/protocol, agents Settings input, installer observation and product integration/emulator scenarios. This change remains active until implementation and declared evidence agree. No build, machine run, publication or archive is authorized by this artifact.
