# Complete product acceptance

## Why

The root test entry points do not yet aggregate component verification or execute a complete candidate-specific emulator acceptance run. Independent session owners and unconditional cleanup can terminate a guest while a product operation has an unknown outcome.

## What changes

Use one machine lease and product operation journal, preserve the existing functional requirements, include every stable candidate agent and required target, and derive the final verdict from complete evidence. Complete the component integration and runtime scenarios before enabling a full PASS.

## Scope

Root orchestration owns execution and reporting. Components retain production behavior, colocated unit tests and native harnesses. No emulator execution in CI or physical-vehicle claim is introduced.
