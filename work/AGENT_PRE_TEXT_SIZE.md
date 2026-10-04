# Agent Pre-Read — Text Size Settings

## Mission

Implement the FocusFlow text-size feature described in:

- `work/TEXT_SIZE_PLAN.md` — architecture, scope, and verified decisions
- `work/TEXT_SIZE_PROMPTS.md` — executable prompts for the two implementation jobs
- `work/PROJECT_WORK_TRACKER.md` — batch owner, checkboxes, evidence, and blockers

The feature is one General percentage scale plus optional overrides for Focus,
Stats, Settings, and Defense. Home/Schedule is intentionally excluded.

## Required reading before editing

Read both work documents in full, then inspect the current source files named by
the prompts. The documents were written against an earlier source snapshot, so
verify names, signatures, call sites, and line locations before changing code.
If an assumption is stale, follow the current source and report the mismatch.

## Work split

Keep these as separate jobs even if the same agent performs both:

1. **Core wiring (Prompt A):** settings model and persistence, theme
   CompositionLocal, root and per-tab wiring, Settings UI, and the non-Compose
   block overlay. Track core wiring as Batch 02 and source-tab context as Batch
   03 in `PROJECT_WORK_TRACKER.md`.
2. **Mechanical rollout (Prompt B):** only the explicitly listed `.sp` files and
   directories, changing numeric text-sizing literals to `.scaledSp`. Track it
   as Batch 04.

Do not let the mechanical rollout expand into unrelated UI cleanup.

## Required progress updates

Before work, claim the relevant batch in `work/PROJECT_WORK_TRACKER.md` with
your actual agent name/handle and date. As each checklist item is completed,
tick it and add inspectable evidence on the same line. Record blockers without
marking blocked work complete. At handoff, update the batch status and include
changed files, checks and results, exceptions, and remaining blockers. Keep the
feature-specific `TEXT_SIZE_TRACKER.md` synchronized with detailed evidence.
Append a dated row to the master tracker's update log at each meaningful
milestone and handoff.

## Non-negotiable constraints

- Home/Schedule must remain at exactly `1f`, unaffected by General or tab scales.
- Do not wrap `MainScaffold`; only wrap the tab content inside it.
- A null per-tab override means inherit General. A non-null override replaces
  General; do not multiply the two values.
- Keep the existing default at `1f` / `100%`, so untouched installations look
  unchanged.
- Do not add bespoke ViewModel or Repository setters if the existing settings
  catch-all still persists the new fields.
- Do not convert files outside Prompt B's exact scope. In particular, leave
  `ui/home`, `ui/common`, `ui/onboarding`, and the explicitly excluded launcher
  and support files alone.
- Do not blindly convert `.sp` values outside `@Composable` scope; list them as
  exceptions if they cannot use the composable extension safely.
- Stats gets General scaling through root typography, but a Stats-specific
  override does not automatically affect existing `MaterialTheme.typography`
  call sites. Treat that as the documented v1 limitation unless separately
  approved.
- The block overlay is a plain Android Activity and needs its own preference read
  and `textSize * textScale` changes; Compose locals cannot reach it.

## Verification and handoff

After editing:

1. Search the diff for accidental changes outside scope.
2. Confirm persistence round-trips General and nullable per-tab values,
   including resetting an override to null.
3. Confirm Home is pinned to `1f` and the bottom navigation is not wrapped.
4. Check all `.scaledSp` imports and composable-scope constraints.
5. Run the available Android build/check commands and report environment
   limitations separately from source failures.

Handoff must list every changed file, replacements per scope, skipped
exceptions, source mismatches, and checks run. Do not claim a full rollout if
any file was skipped.