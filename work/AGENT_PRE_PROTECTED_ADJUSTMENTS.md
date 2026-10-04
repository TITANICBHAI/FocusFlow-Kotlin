# Agent Context Pre-Prompt — Guarded Adjustments

## Purpose

Gather current project context for the main agent. This is a read-only context
pass, not an implementation assignment. Stop after reporting the findings in
chat; do not make code or documentation changes.

## Boundaries

- Work as the main agent only. Do not spawn, call, or delegate to subagents.
- Do not edit files, install packages, change workflows or environment settings,
  or otherwise modify the workspace.
- Inspect the Kotlin Android app under `app/`; do not inspect the archived React
  app.
- Treat the plan and tracker as prior notes. Confirm current behavior from the
  checked-out source before reporting it.

## Read for context

1. `work/PROTECTED_ADJUSTMENTS_PLAN.md`
2. `work/PROTECTED_ADJUSTMENTS_TRACKER.md`
3. `app/src/main/java/com/tbtechs/focusflow/ui/settings/SettingsScreen.kt`
4. `app/src/main/java/com/tbtechs/focusflow/ui/settings/ProtectedAdjustmentsScreen.kt`
5. `app/src/main/java/com/tbtechs/focusflow/ui/navigation/Routes.kt`
6. `app/src/main/java/com/tbtechs/focusflow/ui/navigation/FocusFlowNavGraph.kt`
7. The guard-owner source files listed in the tracker’s source-audit record.
8. `work/TEXT_SIZE_PLAN.md` §12 only if Settings route-scale context is needed.

## Report in chat

- The current Settings entry, destination, back path, and guide link destinations.
- The live guard behavior by action, including the exact PIN/active-block
  condition and any allowed additions or exceptions.
- Which owner screens are responsible for each guard.
- Any mismatch between the written audit and current source, any excluded
  surfaces, and any unresolved uncertainty.
- Existing verification notes and current toolchain limitations. Do not start a
  build or workflow during this context-only pass.