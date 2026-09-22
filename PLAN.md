# Focus session bug-fix plan

## Confirmed gap from the previous agent

The previous agent diagnosed the issue but did not complete the implementation. The current checkout still has all four reported gaps:

1. `ACTION_TASK_ENDED` is broadcast by the foreground service, but `FocusSessionViewModel` does not receive it or close the active Room session.
2. `HomeScreen` selects the task matching the focus session without excluding `completed` or `skipped` tasks.
3. The Focus screen's Done and Skip handlers only stop focus when `keepFocusActiveUntilTaskEnd` is false, including after the task is already overdue.
4. `FocusTaskPanel` renders the overdue label from time alone and does not suppress it for completed or skipped tasks.

The result is a task that can be marked complete while the Room focus session remains active, leaving the Focus and Schedule UI in a stale state.

## Implementation order

### 1. Close sessions when the task-end broadcast arrives

- Register a package-scoped receiver in `FocusSessionViewModel` for `ForegroundTaskService.ACTION_TASK_ENDED`.
- On receipt, look up the active session and end it through `FocusSessionRepository`.
- Clear `_focusSession` after the repository update.
- Unregister the receiver in `onCleared`.
- Make the handler safe when the session is already gone or the broadcast belongs to another task.

### 2. Prevent completed tasks from becoming the active banner

- Update `HomeScreen` active-task selection to ignore `completed` and `skipped` tasks before matching the focus session.
- Keep the existing fallback for genuinely running tasks.
- Ensure the banner disappears when a task is completed even if stale session state briefly exists.

### 3. Make terminal actions stop an overdue focus session

- In the Focus screen completion handler, stop the matching focus session when the task is overdue regardless of `keepFocusActiveUntilTaskEnd`.
- Apply the same rule to skip if the current product behavior treats Skip as a terminal action for the active session.
- Apply equivalent logic to the Home screen banner completion path.
- Avoid stopping an unrelated focus session by checking the session task ID.

### 4. Fix the overdue display state

- Derive a display-level overdue flag that is false for `completed` and `skipped` tasks.
- Use that flag for the timer color, numeric label state, and “overdue” caption.
- Preserve the normal remaining-time display for active tasks.

### 5. Verify the complete lifecycle

- Build the Android app with the project’s Gradle task.
- Exercise the early-completion path with the setting enabled: task completes and focus remains active only when intended.
- Exercise the overdue completion path with the setting enabled: task completes and focus ends.
- Exercise natural task expiry: the service broadcast closes the Room session and the UI clears.
- Confirm Schedule no longer shows a completed task as the active or overdue banner.
- Confirm Stop Focus still works with and without a session PIN.

## Acceptance criteria

- No active Room focus session remains after natural task expiry.
- Completing an overdue task cannot leave the focus UI stuck in overdue mode.
- Completed and skipped tasks never appear as active focus banners.
- Early completion continues to respect `keepFocusActiveUntilTaskEnd`.
- Broadcast registration and cleanup do not leak an Android context or receiver.
- The app builds successfully and the changed lifecycle paths are covered by tests or a documented manual verification run.

## Open caveat

The handoff could not confirm whether a focus-session PIN is configured. PIN-protected stop behavior must therefore be included in verification rather than assumed to follow the no-PIN path.