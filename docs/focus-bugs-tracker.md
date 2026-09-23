# Focus Bugs

This tracker follows the audit in [`focus-bugs.md`](focus-bugs.md). The audit
is the source of the findings; the current Kotlin source is authoritative when
implementation details change.

## Status legend

- `[ ]` Open
- `[~]` Implementation present; runtime verification pending
- `[x]` Fixed and verified
- `[-]` Won't fix or not a bug
- `[?]` Verify only / informational

## Fix batches

### Batch 1 — Focus session lifecycle and stuck-state prevention

F1, TF2, TF3, TF4, F3, F4, F5.

This batch makes automatic task completion, manual completion, and rapid focus
activation converge on one valid session state. F1, TF2, TF3, and TF4 were
already present in the current source and still need runtime verification.
F3, F4, and F5 were implemented as the first active code batch.

### Batch 2 — Task timing and alarm consistency

TF1, T1, T2, T3, F7.

This batch keeps Room task times, AlarmManager alarms, foreground-service
timers, and app-blocking state synchronized during creation, editing, and
extension. The implementation is present; runtime verification remains
pending. An optional “keep the idle service running” setting is intentionally
deferred because it would change battery and notification behavior beyond the
bug fix.

### Batch 3 — Task/session integrity, statistics, and concurrency

F6, TF5, TF6, T4, T5.

This batch repairs stale sessions, closes task/focus races, protects the
preserved task during bulk deletion, avoids full-table lookups, and rejects
invalid time ranges. The implementation is present; runtime verification
remains pending.

### Batch 4 — Enforcement and notification edge cases

AB1, AB2, AB3, AB4, AB6.

This batch makes retry blocking, reminders, notification actions, expiry
feedback, and session preference cleanup use one consistent end-time model.
The implementation is present; runtime verification remains pending.

### Batch 5 — Cleanup and verification-only findings

F2, T6, AB5, AB7.

This batch removes stale coordination comments, resolves or documents the
unreachable scheduler type, records the reboot limitation, and preserves the
allow-list behavior that is confirmed to be by design. The cleanup is complete;
AB5 remains informational rather than a code fix.

## Recommended fix order

F1 → TF2 → TF1 → F3 → TF3 → F4 → F5 → TF4 → AB4 → T1 → T3 → F6 → TF5 →
AB1 → T2 → T4 → TF6 → F7 → AB2 → AB6 → T5 → F2 → AB3 → T6 → AB5

## Tracker

| Status | ID | Severity | Area | Batch | Work item | Verification |
|---|---|---|---|---|---|---|
| [~] | F1 | Critical | Focus | 1 | Receive `ACTION_TASK_ENDED` and end the active Room session | Timer completion clears the Room session and Focus UI |
| [~] | TF2 | Critical | Both | 1 | Ensure Done ends overdue Focus Mode even with keep-alive enabled | Completed task cannot leave an active Focus session |
| [~] | TF1 | Critical | Both | 2 | Update `ForegroundTaskService` when task end time is extended | Blocking and notification use the new end time; one end event only |
| [~] | F3 | Critical | Focus | 1 | Catch `SessionPinRequiredException` and surface a user-facing error | Done/schedule stop paths show feedback instead of failing silently |
| [~] | TF3 | High | Both | 1 | Exclude completed/skipped tasks from `HomeScreen` active-task selection | Completed session task cannot own the active banner |
| [~] | F4 | High | Focus | 1 | Reject completed/skipped tasks in `startFocusMode` | Activation on either status does nothing |
| [~] | F5 | High | Focus | 1 | Guard against an existing active session | Rapid activation creates at most one active Room row |
| [~] | TF4 | High | Both | 1 | Cancel AlarmManager alarms in `completeTask` and `skipTask` | Completed/skipped tasks do not post end notifications |
| [~] | AB4 | High | Both | 4 | Surface expired notification actions to the user | An action older than five minutes produces visible feedback |
| [~] | T1 | High | Tasks | 2 | Reject completed/skipped tasks in `extendTaskTime` | Extending a finished task changes neither alarm nor end time |
| [~] | T3 | Medium | Tasks | 2 | Keep Room and alarm operations inside the task mutex | Rapid edits leave the newest alarm; no insert-to-alarm gap |
| [~] | F6 | High | Focus | 3 | Repair stale orphaned sessions on boot and session-list load | Old active rows are ended and excluded from current stats |
| [~] | TF5 | Medium | Both | 3 | Close the task-completion/start-focus race | Focus cannot start from a stale pre-completion task snapshot |
| [~] | AB1 | Medium | Focus | 4 | Share end-time-aware `focusActive` logic with retry checks | Retry does not re-block after `task_end_ms` |
| [~] | T2 | Medium | Tasks | 2 | Avoid scheduling alarms for completed/skipped tasks in `updateTask` | Editing a finished task creates no alarm |
| [~] | T4 | Medium | Tasks | 3 | Add `getTaskById` and replace full-table status lookups | Status mutations query one task |
| [~] | TF6 | Medium | Both | 3 | Preserve the active session for the excluded task | `clearAllTasksExcept` keeps the preserved task's session |
| [~] | F7 | Low | Focus | 2 | Start the foreground service before enabling blocking prefs | Notification is available before app blocking begins |
| [~] | AB2 | Low | Focus | 4 | Use the shared end-time-aware check for home reminders | No reminder appears after task end |
| [~] | AB6 | Low | Focus | 4 | Optionally clear the full session preference set | Stale session keys are removed after cleanup |
| [~] | T5 | Low | Tasks | 3 | Validate `endTime` is after `startTime` before insert | Invalid time ranges are rejected with UI feedback |
| [~] | F2 | Low | Focus | 5 | Rewrite or remove the stale FLAG-1 comment | Comment matches the reactive Room implementation |
| [~] | AB3 | Low | Both | 4 | Remove or formalize the unused notification bridge broadcast | Notification action path has one documented, tested route |
| [~] | T6 | Low | Tasks | 5 | Map `SchedulerEngine.Task` to the domain task or remove dead code | Engine accepts real tasks; auto-skip behavior is reviewed |
| [?] | AB5 | Info | Focus | 5 | Confirm reboot is not treated as the orphan-session fix | Boot only recovers preferences; AppBoot/session-flow repair handles Room rows |
| [-] | AB7 | N/A | Focus | 5 | Keep empty allow-list behavior as documented by design | Empty list allows all apps when no allowance rule exists |

## Cross-cutting verification

- [ ] Timer completion ends the Room focus session without a manual stop.
- [ ] Completed and skipped tasks cannot create, extend, or retain alarms.
- [ ] Focus activation cannot create duplicate active sessions.
- [ ] Focus UI and Home UI clear their active state after automatic completion.
- [ ] Extending an active task updates the service timer and produces one end
  notification at the new end time.
- [ ] PIN-protected stop paths show a PIN prompt rather than silently ending a
  coroutine.
- [ ] Notification actions either execute or provide feedback when expired.
- [ ] Orphan repair prevents stale sessions from inflating focus statistics.
- [ ] Empty per-task allow-lists retain the documented least-strict behavior.

## Notes

- Emergency Override is a safe current-state workaround for the reported stuck
  Focus screen.
- The standalone **Block apps while I work** toggle is independent from Focus
  Mode and should not be coupled to the fixes above.
- The uploaded audit remains available at
  `attached_assets/Pasted-FocusFlow-Bug-Audit-Tasks-Focus-Mode-All-findings-verif_1790143932885.txt`.