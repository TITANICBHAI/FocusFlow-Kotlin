# Tasks bugs

Tracker for the frozen focus-session state reported in the handoff.

## Status legend

- `[ ]` Not started
- `[~]` In progress
- `[x]` Complete
- `[!]` Blocked or needs a decision

## Bugs

### BUG-1 — Task-end broadcast does not close the Room session

- Status: `[ ]`
- Priority: P0 — root cause of stale active focus state
- Evidence: `ForegroundTaskService` sends `ACTION_TASK_ENDED`; no receiver is registered in `FocusSessionViewModel` or another UI component.
- Fix: receive the broadcast, end the active Room session through the repository, clear `_focusSession`, and unregister the receiver in `onCleared`.
- Done when: natural task expiry clears SharedPrefs, service state, Room state, and UI state.

### BUG-2 — Completed task remains eligible for the Schedule active banner

- Status: `[ ]`
- Priority: P1
- Evidence: `HomeScreen.activeTask` matches `focusSession.taskId` without checking task status.
- Fix: exclude `completed` and `skipped` tasks before matching the focus session.
- Done when: a completed task cannot render as the active or overdue Schedule banner.

### BUG-3 — Done/Skip shutdown is incorrectly gated after expiry

- Status: `[ ]`
- Priority: P1
- Evidence: Focus and Home completion paths only call `stopFocusMode()` when `keepFocusActiveUntilTaskEnd` is false.
- Fix: always stop the matching focus session for terminal actions after the task is overdue; preserve the setting for early completion.
- Done when: completing an overdue task ends focus even if the setting is true, while early completion still follows the setting.

### BUG-4 — Completed tasks render an overdue timer

- Status: `[ ]`
- Priority: P1
- Evidence: the Focus hero card derives its caption and color from `overdue` without checking task status.
- Fix: suppress overdue presentation for completed and skipped tasks.
- Done when: completed and skipped tasks do not show an overdue caption or overdue color.

## Verification tasks

### VERIFY-1 — Build and static checks

- Status: `[ ]`
- Run the Android Gradle build after implementation.
- Record any environment limitation separately from source failures.

### VERIFY-2 — Natural expiry lifecycle

- Status: `[ ]`
- Start a short focus task, let it expire, and confirm the service broadcast closes the Room session and clears the UI.

### VERIFY-3 — Early completion behavior

- Status: `[ ]`
- With `keepFocusActiveUntilTaskEnd = true`, complete before the end time and confirm the intended focus behavior remains unchanged.
- Repeat with the setting disabled and confirm focus stops.

### VERIFY-4 — Overdue completion behavior

- Status: `[ ]`
- Complete an overdue task with the setting enabled and confirm the focus session ends and no stale banner remains.

### VERIFY-5 — PIN-protected stop behavior

- Status: `[ ]`
- Verify the stop path with a configured session PIN, since the handoff did not confirm this state.

## Notes

- The previous agent supplied a diagnosis but did not implement or verify these fixes.
- The attached diagnosis is retained in `attached_assets/` as the source of the initial findings.