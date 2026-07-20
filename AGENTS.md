# Voboost Code Style (CRITICAL)

## Global
- This project follows ALL common rules from ../voboost-codestyle/AGENTS.md

## Commands
- `./gradlew build`: Build APK (release-only; NEVER use installDebug/installRelease)
- `./gradlew emulatorTest`: Run emulator E2E harness (tools/emulator/) against AVD 'free'
- `./gradlew openspecValidate`: Validate openspec changes (strict)

## Project Structure
- Application code: `src/main/java/ru/voboost/`
- Features: `src/main/java/ru/voboost/feature/`
- UI components: `src/main/java/ru/voboost/ui/`
- Resources: `src/main/res/`

## UI
- Button style: NEVER use `ButtonStyle.PRIMARY` unless explicitly instructed.
  In the car all buttons MUST be `ButtonStyle.SECONDARY`. PRIMARY is reserved
  for rare, explicitly-approved call-to-action cases only.

## OpenSpec
- Spec-driven; truth is openspec, no code without an applied change, invariants live in specs
- propose -> apply -> archive
- `npx @fission-ai/openspec validate <change> --strict`
- Layout mirrors `../voboost-inject/openspec`: `changes/<change>/{.openspec.yaml,proposal.md,design.md,tasks.md,specs/<capability>/spec.md}`; specs use `## ADDED/MODIFIED/REMOVED Requirements` with SHALL + WHEN/THEN scenarios
