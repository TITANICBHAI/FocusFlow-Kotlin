# FocusFlow Fixes Phase 5 — Main Work Tracker (v8)

**Primary tracker for the plan in `fixes/PLAN.md`.** This file is the authoritative progress record for this fixes effort. `allowance/TRACKER.md` belongs to a separate earlier workstream: it may be read as a reference for tracker format and discipline, but its statuses, evidence, decisions, and progress do not apply here and must not be updated for this work.

The v8 plan supersedes `allowance/PLAN.md` (v7). The plan is the implementation contract; it is not authorization by itself. See `fixes/AGENT_PRE_PROMPT.md` before starting work.

**Initial state:** All implementation batches below are `NOT STARTED`; no progress or evidence has been copied from the allowance tracker. Product decisions below are transcribed from section 3 of the v8 plan, not independently re-confirmed in this tracker.

---

## Tracker rules

### Status vocabulary

- `NOT STARTED`
- `IN PROGRESS`
- `BLOCKED — <reason>`
- `DONE — evidence recorded`
- `OWNER-RUN PENDING`

Every work item records its own status, `Blocked by`, evidence, and whether it is owner-run. Batch headers also record status, blocker, start/completion dates, and owner-run state.

### Before and during a batch

- At batch start, change its status to `IN PROGRESS`, record `Started`, and read the matching plan section in full.
- Update the relevant notes after each meaningful change; do not wait until the batch is complete.
- Follow the order and parallelism allowed by `fixes/PLAN.md`. Do not silently widen scope.
- Preserve unrelated user changes and data. No destructive task/data replacement without the user's authorization.

### Checkboxes and evidence

- Tick an item only when it is complete and verified by the evidence named for it. Never tick partial, attempted, unverified, or owner-run work.
- For each completed item, record changed files, the exact command/test/search/device report used, and the observed result.
- If a check fails, keep its item unchecked, append the observed failure, and later append the fix and successful rerun. Never erase failure history.
- If evidence is unavailable, the check remains open. Do not infer a pass from source presence or from an older tracker.

### Decisions and completion

- Record decisions made, alternatives rejected, defaults, blockers, and deferred work. An applied default that still needs the owner's answer must say `default applied, awaiting owner`.
- Mark a batch `DONE — evidence recorded` only when its “done when” condition, required tests, and gates pass. Reconcile every checkbox against current code and observed outputs first.
- Leave incomplete work unchecked and explain why. Owner-run checks remain `OWNER-RUN PENDING` until the owner supplies results; the agent never ticks them.
- At project close-out, reconcile this tracker with the code, test results, and owner reports. Preserve archived plans and tracker history; do not delete them.

---

## Order and dependencies

1. **A5 first.** Reconcile this tracker with v8 and the current code before app-code changes.
2. **A1–A4 next.** Do not start them until A5 is done.
3. **B and C after A.** Batches B and C may run in parallel where their plan dependencies allow.
4. **D after B and C.**
5. **E after D.** Owner device checks happen once, at the end.

---

## Decision log

Decision entries below are transcribed from `fixes/PLAN.md`, section 3.1–3.2. The plan is the recorded source; do not represent these as independently re-confirmed during implementation.

| PD | Decision recorded in v8 | Tracker status / application |
|---|---|---|
| PD-1 | Same seven-day gate for fresh and upgraded installs; no upgrade-day cutover. | RECORDED FROM PLAN; implement and verify in A1. |
| PD-2 | Allowance limits require Accessibility; when it is off, allowance apps are blocked during any active block and the UI explains why. No fallback meter. | RECORDED FROM PLAN; implement and verify in B1. |
| PD-3 | An empty Focus allow-list means no app is allowed; an allowance never widens Focus. | RECORDED FROM PLAN. The detail has a safe default of “No” and remains awaiting owner confirmation as specified in section 3.2. |
| PD-4 | Loosening allowance edits are locked during any active block; tightening remains allowed. | RECORDED FROM PLAN; implement and verify in B6/D2. |
| PD-5 | Usage Access is required; enabling an allowance prompts for it. | RECORDED FROM PLAN; implement and verify in D2. |
| PD-6 | Usage history is not exported; `.focusflow` format remains unchanged and backup/restore explains history is rebuilt. | RECORDED FROM PLAN; implement and verify in D2. |
| PD-7 | Remove the Today Stats fallback after the Stats fix; a failed read is unavailable, never zero. | RECORDED FROM PLAN; implement in A3/D3. |
| PD-8 | Do not fix the legacy tracker; retain its known limitations and stop its writes 14 days after cutover. Never use legacy numbers as validation reference. | RECORDED FROM PLAN; preserve in C6. |
| PD-9 | Keep refusing usage windows that cross cutover; the data-health notice explains the blackout. No relaxation without device evidence. | RECORDED FROM PLAN; preserve and verify in C6. |
| PD-10 | Merge same-package sessions across a 3-second gap when no other package intervenes. | RECORDED FROM PLAN; implement and verify in C1. |
| PD-11 | Compare Stats with device screen-time totals within about 20% or 15 minutes, whichever is larger; if unavailable, validate internal totals and top apps. | RECORDED FROM PLAN; owner validation in E1. |
| PD-12 | Google Play target API requirement remains an open product question; treat it as a release gate, with no Phase 5 implementation. | OPEN; keep release gate in E2 and do not change target API in this scope. |
| PD-13 | When usage data is unavailable, enforce using last known value plus projection, mark values approximate, and show revoked permission in permission health. | RECORDED FROM PLAN; implement and verify in B4/D1. |
| PD-14 | Usage is counted all day; allowances restrict only while a block is active and never open an app a block keeps closed. | RECORDED FROM PLAN; implement and verify in B6/D2. |
| Modes | Keep count, time-budget, and interval modes; time budget and interval are the main focus, count follows. | RECORDED FROM PLAN; test in B5. |
| Devices | Owner tests once, at the end, on Realme C3 (Android 10) and Samsung M31 (Android 12). | RECORDED FROM PLAN; owner-run in E1. |

---

## Batch A — Release blockers

### A5 — Tracker truth

**Status:** `NOT STARTED`  
**Blocked by:** None — do this first  
**Started:** —  
**Completed:** —  
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Verify current Room database version and `MIGRATION_7_8`; record the v8 A1 change accurately. | NOT STARTED | — | Pending | No |
| [ ] | Check this primary tracker for stale v6/v7 plan references; correct them to v8 or document why a historical reference remains. | NOT STARTED | — | Pending | No |
| [ ] | Audit all checkboxes and statuses; leave unsupported or unverified work incomplete. Confirm no allowance-tracker progress was imported. | NOT STARTED | — | Pending | No |
| [ ] | Verify the Batch 0 usage-table writer/reader map against code; identify `AppUsageAndSessionTracker` as legacy-table writer if confirmed. | NOT STARTED | — | Pending | No |
| [ ] | Verify the `SettingsRepository.getAllowanceSnapshot` purpose and whether `resetDailyAllowanceUsage` has callers. | NOT STARTED | — | Pending | No |
| [ ] | Confirm every batch/item has status, blocker, evidence, and owner-run fields; reconcile this decision log with PD-1–PD-14. | NOT STARTED | — | Pending | No |
| [ ] | Final audit: every tracker statement matches current code or is explicitly marked pending/unverified. | NOT STARTED | Items above | Pending | No |

**Done when:** every statement in `fixes/TRACKER.md` matches the current code and v8 plan.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

### A1 — Database and cutover

**Status:** `NOT STARTED`  
**Blocked by:** A5  
**Started:** —  
**Completed:** —  
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Seed `usage_pipeline_state` on fresh database creation with local `shadow_started_on` and null `cutover_date`. | NOT STARTED | A5 | Pending | No |
| [ ] | Add an idempotent read-model repair for a missing state row. | NOT STARTED | A5 | Pending | No |
| [ ] | Make `MIGRATION_7_8` data-free; add version 9 `MIGRATION_8_9` to clear stale cutover dates, reset shadow start where needed, and create missing state. | NOT STARTED | A5 | Pending | No |
| [ ] | Simplify `cutoverDate()` as specified; document fresh-install and upgrade behavior. | NOT STARTED | A5 | Pending | No |
| [ ] | JVM/fake-store cases: missing row repair; fewer than seven complete days; seven complete current-version days; old pipeline version excluded; repeated calls idempotent. | NOT STARTED | A5 | Pending | No |
| [ ] | Migration coverage: 6→9, 7→9, 8→9, fresh install, and legacy `user_version = 0`; record whether tests ran or only compiled. | NOT STARTED | A5 | Pending | No |
| [ ] | Owner scenario S2 on both phones; do not tick until owner reports. | OWNER-RUN PENDING | A5; E1 device session | Pending owner report | Yes |

**Done when:** fresh and upgraded installs reach the same state and behavior; automated migration/cutover evidence is recorded.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

### A2 — Maintenance job

**Status:** `NOT STARTED`  
**Blocked by:** A5  
**Started:** —  
**Completed:** —  
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Implement a maintenance worker that runs recent rollups, completed-day detection, then prune in that order. | NOT STARTED | A5 | Pending | No |
| [ ] | Schedule unique periodic work with keep policy from app startup; record observed schedule evidence. | NOT STARTED | A5 | Pending | No |
| [ ] | Preserve unrelated old background-fetch duties; remove old detection/prune calls only after the replacement is live. | NOT STARTED | A5 | Pending | No |
| [ ] | Detection marker advances only when required days are COMPLETE or permission is denied; unknown results retry. | NOT STARTED | A5 | Pending | No |
| [ ] | Run detection after successful rollup completion; inject clock/zone for pruning and detection while task dates retain UTC behavior. | NOT STARTED | A5 | Pending | No |
| [ ] | Worker tests: order; unknown-result retry; one success per local day; prune horizon uses injected clock. | NOT STARTED | A5 | Pending | No |
| [ ] | Prune delegates to the rollup retention work in C5. | NOT STARTED | A5; C5 | Pending | No |

**Done when:** scheduling is evidenced at app start and required worker tests pass.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

### A3 — Stats zero reading

**Status:** `NOT STARTED`  
**Blocked by:** A5  
**Started:** —  
**Completed:** —  
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Preserve every app row per date in `DeviceUsageSource`; cache app name/category per package per read. | NOT STARTED | A5 | Pending | No |
| [ ] | Remove `mergeTodayUsageFallback` and its `getUsageSummary` dependency; represent read failure as unavailable, never zero. | NOT STARTED | A5 | Pending | No |
| [ ] | Log exception type/reason and return typed failure from `readSource`; add one debug line with the plan-required read counters. | NOT STARTED | A5 | Pending | No |
| [ ] | Test multi-app day values (Instagram 40m, WhatsApp 15m, Maps 3m, Settings 0.4s): summary, daily, hourly, and app rows agree. | NOT STARTED | A5 | Pending | No |
| [ ] | Test four stored days plus live today total (120+90+150+60 plus today = 478 as specified). | NOT STARTED | A5 | Pending | No |
| [ ] | Test same multi-app day from live and rollup sources gives identical per-app minutes. | NOT STARTED | A5 | Pending | No |
| [ ] | Test unavailable events produce unavailable state, not zero. | NOT STARTED | A5 | Pending | No |
| [ ] | Owner checks S1 and confirms Today/Week totals against daily bars and device screen-time page. | OWNER-RUN PENDING | A5; E1 device session | Pending owner report | Yes |

**Done when:** required source tests pass; owner check remains open until reported.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

### A4 — Test baseline

**Status:** `NOT STARTED`  
**Blocked by:** A5  
**Started:** —  
**Completed:** —  
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Find and remove tests that read `.kt` source text; replace with behavior tests for ledger, pipeline, and meter using fake clocks/sources. | NOT STARTED | A5 | Pending | No |
| [ ] | Add fresh-database test and ensure migration tests compile and run where the project supports them. | NOT STARTED | A5; A1 | Pending | No |
| [ ] | Search test sources and verify no Kotlin-source `readText` checks remain; verify section 6 requirements are assigned to batch tests. | NOT STARTED | A5 | Pending | No |

**Done when:** source-text tests are absent and section 6 test coverage is present.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

---

## Batch B — One meter everywhere

**Batch gate:** A1–A4 must be DONE before starting B. B batches may proceed in parallel only where current code ownership and plan dependencies permit.

### B1 — Accessibility lifecycle and fallback behavior

**Status:** `NOT STARTED`  
**Blocked by:** A1–A4  
**Started:** —  
**Completed:** —  
**Owner-run:** S4 is owner-run

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Interrupt does not tear down meter; unbind/destroy do; rebind recreates and starts it. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Remove foreground-service allowance mirror/ledger reads; while Accessibility is off, allowance apps are blocked during active blocks with the required reason. | NOT STARTED | A1–A4; B6 resolver | Pending | No |
| [ ] | Add the required Accessibility-off explanation to allowance screens and permission health card. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Recover checkpoint once at meter start, clear marker after recovery, prevent double credit; remove recovery from foreground service. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | JVM tests cover lifecycle, capped one-time recovery, fallback reason, and no ledger access in fallback. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Owner scenario S4: Accessibility off during a block; explanation and restoration behavior reported. | OWNER-RUN PENDING | E1 device session | Pending owner report | Yes |

**Done when:** lifecycle/recovery/fallback tests pass and S4 is recorded by the owner.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

### B2 — Foreground signal filter

**Status:** `NOT STARTED`  
**Blocked by:** A1–A4  
**Started:** —  
**Completed:** —  
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Put one `ForegroundSignalFilter` before the meter; Accessibility remains the only meter signal source. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Ignore resolved input methods, system UI, app overlays, blocked screens, and accessibility overlays; only launchable apps/home cause a change. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Bridge permission-controller/intent-resolver dialogs for up to three seconds; keep the bridge list evidence-based. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Remove duplicate `currentTimed*` foreground state; count opens only on a filtered switch from another real app/home. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Tests: keyboard, shade, permission dialog do not add opens; A→B→A adds one; no spontaneous count increase/decrease. | NOT STARTED | A1–A4 | Pending | No |

**Done when:** filtered signal behavior and all listed tests pass.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

### B3 — Read policy

**Status:** `NOT STARTED`  
**Blocked by:** A1–A4  
**Started:** —  
**Completed:** —  
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Implement the v8 cadence; no read for non-allowance windows except one read when an allowance app leaves front; enforce 10-second minimum between non-lifecycle reads. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Keep scan cost independent of prior event count or demonstrate compliance with the plan's read budgets; reduce off main thread. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Replace timeout around blocking platform read with cancellation-aware behavior; slow reads cannot pile up. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Persist ledger only for meaningful changes; make bridge-return lookup linear; add hourly read/write and last-reason/outcome counters. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Tests: 200 non-target signals obey no-read/debounce policy; 10-minute foreground has expected ticks; read/reduction never run on main; writes only for changed targets. | NOT STARTED | A1–A4 | Pending | No |

**Done when:** cadence, dispatcher, cost, and persistence tests pass; counters are observable.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

### B4 — Closed time versus open tail

**Status:** `NOT STARTED`  
**Blocked by:** A1–A4  
**Started:** —  
**Completed:** —  
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Separate closed usage from open-tail projection; only closed time is a persistent floor; recompute tail on each read; cap tail at four hours. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Decode legacy JSON by treating old `confirmedUsedMs` as closed time. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Tests cover late end lowering projected value, tail growth/cap, midnight, interval, restart, and unavailable-data projection. | NOT STARTED | A1–A4 | Pending | No |

**Done when:** closed-versus-tail model, compatibility, and required tests pass.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

### B5 — Per-mode rules as tests

**Status:** `NOT STARTED`  
**Blocked by:** A1–A4  
**Started:** —  
**Completed:** —  
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Convert every rule in plan section 4.5 into behavior tests, implement time-budget and interval behavior first, then count. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Test count N−1, Nth open remains usable, and next open blocks. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Test exact time-budget expiry within one tick and reseeding after read. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Test interval start on first open, midnight crossing without reset, expiry, and restart. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Test unavailable/revoked data using PD-13 and config edits during a period. | NOT STARTED | A1–A4 | Pending | No |

**Done when:** all plan section 4.5 rows are tested and deviations are fixed.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

### B6 — Config, block decision, and editor lock

**Status:** `NOT STARTED`  
**Blocked by:** A1–A4  
**Started:** —  
**Completed:** —  
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Implement one typed `AllowanceConfig` parser; replace all listed independent parse sites; expose config flow and invalidate cache on writes. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Implement one block-decision resolver for all rows of plan section 4.6 and use it in both paths. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Apply empty Focus allow-list = no app allowed; allowances never widen Focus; preserve explicit defaults and decision log. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Put loosening-edit lock predicate in repository/view model and apply to focus, standalone, and always-on blocks. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Update Focus and allowance help wording. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Tests: parser missing/wrong/unknown values; each section 4.6 row in both paths; lock predicate for each block type. | NOT STARTED | A1–A4 | Pending | No |

**Done when:** config, resolver, UI wording, and lock tests meet plan requirements.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

### B7 — Ledger hygiene

**Status:** `NOT STARTED`  
**Blocked by:** A1–A4  
**Started:** —  
**Completed:** —  
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Search for callers and remove dead legacy ledger API only when no callers remain. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Split ledger implementation below 300 lines; define active-session constants once; cache snapshots. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | UI observes ledger changes instead of one-second polling; document asynchronous-write crash window. | NOT STARTED | A1–A4 | Pending | No |

**Done when:** dead API is verified unused before removal; file-size and behavior requirements are met.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

---

## Batch C — One definition of usage

**Batch gate:** A1–A4 must be DONE. B and C may proceed in parallel where dependencies permit.

### C1 — Session definition

**Status:** `NOT STARTED`  
**Blocked by:** A1–A4  
**Started:** —  
**Completed:** —  
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Merge same-package sessions across the PD-10 three-second gap; document session definition and bump `CURRENT_PIPELINE_VERSION`. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Tests: A→A merges; A→B→A does not; screen-off separates; exact three-second boundary; launch counts unchanged. | NOT STARTED | A1–A4 | Pending | No |

**Done when:** session definition and pipeline version behavior are verified by tests.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

### C2 — One inclusion policy

**Status:** `NOT STARTED`  
**Blocked by:** A1–A4  
**Started:** —  
**Completed:** —  
**Owner-run:** Manifest visibility confirmation requires owner

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Apply one session-level inclusion policy to Stats, rollups, and detectors; dynamically resolve/cache home apps and keep static list only as fallback. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Put own package, system UI, home, launchable, and minimum-duration rules in this shared policy; do not apply it to allowance measurement. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Verify package-visibility manifest requirements against available project files and record the result/limitation. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Owner confirms manifest package visibility where manifest is unavailable; do not tick without owner evidence. | OWNER-RUN PENDING | E1 or owner confirmation | Pending owner report | Yes |
| [ ] | Tests: hourly sum = daily sum = summary; dynamically identified unknown launcher excluded. | NOT STARTED | A1–A4 | Pending | No |

**Done when:** shared inclusion policy and tests pass; manifest limitation/confirmation is recorded.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

### C3 — Detector windows

**Status:** `NOT STARTED`  
**Blocked by:** A1–A4  
**Started:** —  
**Completed:** —  
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Add a usage-specific end-date helper ending usage windows yesterday; restore task detectors' own date end unless owner decides otherwise. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Tests prove usage detector windows exclude today and task detector windows retain intended current behavior. | NOT STARTED | A1–A4 | Pending | No |

**Done when:** usage and task detector date behavior are independently verified.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

### C4 — Side effects, clocks, and dead code

**Status:** `NOT STARTED`  
**Blocked by:** A1–A4  
**Started:** —  
**Completed:** —  
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Move cutover writes out of reads into maintenance path; make `getActiveFindings` read-only and perform repair once. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Inject clock into `RoomUsageHistoryStore`; route `StatsViewModel` through repository instead of `AppModule.database`. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Search callers before removing `getUsageSummary`, `PhoneUsageSummary.kt`, and unused DAO method(s). | NOT STARTED | A1–A4 | Pending | No |

**Done when:** reads have no repair side effects, clock/repository boundaries are in place, and deletions are proven safe by search/tests.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

### C5 — Retention

**Status:** `NOT STARTED`  
**Blocked by:** A1–A4  
**Started:** —  
**Completed:** —  
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Add pruning for pipeline state/rollup day, app-day, and session tables with the legacy 90-day retention horizon. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Add tests proving every usage table is pruned at the boundary. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Verify A2 maintenance worker invokes rollup pruning. | NOT STARTED | A2 | Pending | No |

**Done when:** pruning covers every usage table and tests prove the retention horizon.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

### C6 — What the cutover gate proves

**Status:** `NOT STARTED`  
**Blocked by:** A1–A4; golden logs depend on E1 owner session  
**Started:** —  
**Completed:** —  
**Owner-run:** Golden event logs are owner-provided

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Document and test that the gate proves completeness only; legacy values are not a correctness reference. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Add golden-log tests through tracker, aggregator, and `measure` using logs from two devices, including one non-Pixel. | NOT STARTED | A1–A4; E1 logs | Pending | No |
| [ ] | Owner supplies exported event logs from both phones; agent records provenance without exposing private information. | OWNER-RUN PENDING | E1/S11 | Pending owner report | Yes |
| [ ] | Test rollback: null cutover serves legacy history and resumes legacy writer; keep legacy tracker unchanged. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Preserve the refused cross-cutover detector seam and document the data-health explanation. | NOT STARTED | A1–A4 | Pending | No |

**Done when:** completeness-only contract, golden-log behavior, and rollback are evidenced; owner logs remain pending until supplied.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

---

## Batch D — Screens and wording

**Batch gate:** Required B and C work is DONE.

### D1 — Freshness everywhere

**Status:** `NOT STARTED`  
**Blocked by:** B and C  
**Started:** —  
**Completed:** —  
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Implement the shared freshness definition from plan section 4.3 and carry it in allowance UI models. | NOT STARTED | B and C | Pending | No |
| [ ] | Launcher, `ActiveScreen`, and `DailyAllowanceModal` all use shared freshness; modal displays or drops `usageByPackage` intentionally. | NOT STARTED | B and C | Pending | No |
| [ ] | Test reducer cases including idle-after-leaving exact, foreground tick freshness, stale read, and revoked permission unavailable. | NOT STARTED | B and C | Pending | No |

**Done when:** all allowance surfaces use one freshness state and reducer tests pass.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

### D2 — Wording, permissions, and backup note

**Status:** `NOT STARTED`  
**Blocked by:** B and C  
**Started:** —  
**Completed:** —  
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Update Usage Access definition and broken-without copy to cover foreground detection, allowance measurement, and history. | NOT STARTED | B and C | Pending | No |
| [ ] | Update Stats, data-health, Focus/Standalone/Defense, How to Use, Changelog, and Privacy wording from the plan. | NOT STARTED | B and C | Pending | No |
| [ ] | Add backup/restore notice that usage history is device-local and rebuilt; do not change `.focusflow` format. | NOT STARTED | B and C | Pending | No |
| [ ] | Centralize `UsageAccess.isGranted`, including `MODE_DEFAULT` and failure behavior; errors are not treated as granted. | NOT STARTED | B and C | Pending | No |
| [ ] | Verify permission-health and allowance editor lock text agrees with PD-2/PD-4/PD-5/PD-6/PD-14 behavior. | NOT STARTED | B and C | Pending | No |

**Done when:** required screens, help, and permission paths use consistent wording and one permission definition.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

### D3 — Unavailable state

**Status:** `NOT STARTED`  
**Blocked by:** A3; D2  
**Started:** —  
**Completed:** —  
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Stats card displays “unavailable, grant Usage Access or try again” for failed/denied reads, never `0m`. | NOT STARTED | A3; D2 | Pending | No |
| [ ] | Test unavailable UI state and verify no failure path silently converts unknown to zero. | NOT STARTED | A3; D2 | Pending | No |

**Done when:** failed/denied reads render unavailable state and tests pass.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

---

## Batch E — Verification and release

**Batch gate:** D is DONE. Owner device session is performed once at the end by the owner.

### E1 — Owner device session

**Status:** `NOT STARTED`  
**Blocked by:** D; owner availability and both devices  
**Started:** —  
**Completed:** —  
**Owner-run:** Yes — device results are never agent-ticked

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Create `OWNER_DEVICE_TEST_GUIDE.md` with setup, exact steps, pass criteria, expected numbers, and report instructions for S1–S12. | NOT STARTED | D | Pending | No |
| [ ] | Add copyable result template for scenario, phone, pass/partial/fail, observed behavior, and requested numbers. | NOT STARTED | D | Pending | No |
| [ ] | Add debug-only usage diagnostics and one-tap Copy report with only package names and the fields in plan section 7. | NOT STARTED | D | Pending | No |
| [ ] | Use one log tag and include the exact logcat commands from plan section 7 in the guide. | NOT STARTED | D | Pending | No |
| [ ] | Include default-battery then unrestricted-battery procedure; record triage rule and at most two retest rounds. | NOT STARTED | D | Pending | No |
| [ ] | Confirm exported Room schema for the new DB version is committed. | NOT STARTED | A1 | Pending | No |
| [ ] | Owner runs S1–S12 once on Realme C3 (Android 10) and Samsung M31 (Android 12); attach reports and mark each `OWNER-RUN PENDING` until received. | OWNER-RUN PENDING | Guide/tooling complete; owner session | Pending owner reports | Yes |
| [ ] | Owner records PD-11 Stats comparison and provides golden event logs for C6 where available. | OWNER-RUN PENDING | Owner session | Pending owner reports | Yes |

**Done when:** agent guide/tooling/schema work is complete and owner reports are attached. Do not mark device outcomes complete before the owner reports them.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

### E2 — Release gates

**Status:** `NOT STARTED`  
**Blocked by:** E1; owner release decision  
**Started:** —  
**Completed:** —  
**Owner-run:** Release monitoring is owner-run

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Record internal-testing, closed-testing, and staged-rollout gates and halt-rollout rollback procedure. | NOT STARTED | E1 | Pending | No |
| [ ] | Resolve whether Google Play distribution applies; if it does, schedule target-API work before any update. Do not change target API as Phase 5 work. | NOT STARTED | Owner answer | Pending | No |
| [ ] | Owner performs rollout/crash/ANR monitoring if a release is authorized; record owner evidence without claiming an unrun release. | OWNER-RUN PENDING | Release authorization | Pending owner report | Yes |

**Done when:** release gates are documented and applicable target-API work is scheduled; any rollout monitoring remains owner-run.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

### E3 — Close-out

**Status:** `NOT STARTED`  
**Blocked by:** A5–E2  
**Started:** —  
**Completed:** —  
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Reconcile every batch/item status with current source, exact test output, and owner reports; leave incomplete items open with reasons. | NOT STARTED | A5–E2 | Pending | No |
| [ ] | Verify all P0/P1 findings have evidence and all P2 findings are done or explicitly deferred with reasons. | NOT STARTED | A5–E2 | Pending | No |
| [ ] | Verify review gates G1–G9 and required release gates; record each pass/failure/unverified result. | NOT STARTED | A5–E2 | Pending | No |
| [ ] | Archive the v7 plan as historical reference without deleting it; make this v8 plan and tracker the active fixes documents. | NOT STARTED | A5–E2 | Pending | No |

**Done when:** plan section 10 is met or remaining items are explicitly documented as blockers/deferred work; no unsupported completion claims remain.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

---

## Summary

| Batch | Status | Blocked by | Owner-run status |
|---|---|---|---|
| A5 — Tracker truth | NOT STARTED | — | Not owner-run |
| A1 — Database and cutover | NOT STARTED | A5 | S2 pending later |
| A2 — Maintenance job | NOT STARTED | A5 | Not owner-run |
| A3 — Stats zero reading | NOT STARTED | A5 | S1 pending later |
| A4 — Test baseline | NOT STARTED | A5 | Not owner-run |
| B1–B7 — One meter everywhere | NOT STARTED | A1–A4 | S4 pending later |
| C1–C6 — One definition of usage | NOT STARTED | A1–A4; E1 logs for C6 | Manifest confirmation/logs may be owner-provided |
| D1–D3 — Screens and wording | NOT STARTED | B and C; A3 for D3 | Not owner-run |
| E1 — Owner device session | NOT STARTED | D; owner/device availability | OWNER-RUN PENDING |
| E2 — Release gates | NOT STARTED | E1; owner release decision | Release monitoring OWNER-RUN PENDING |
| E3 — Close-out | NOT STARTED | A5–E2 | Not owner-run |

**Final status:**  
**Open blockers:**  
**Deferred work:**  
**Final evidence summary:**  
