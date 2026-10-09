# Task-alarm reconciliation failure

## Status

The report and implementation decision were recorded before implementation changes. The targeted correction is implemented and the Android JVM unit-test workflow passed. A force-stop/relaunch has not yet been exercised on a physical device.

## User-visible problem

The user reports that, instead of behaving normally, the app reports an error and opens or surfaces its diagnostic log. In the supplied log, task-alarm reconciliation fails twice, at approximately `21:14:03` and `21:15:05` on October 9, 2026. The app process continues running; this is not a fatal process crash.

The user also reports that this began after force-stopping the app, after which task alarms stopped firing entirely. A force-stop removes the app's OS-scheduled alarms while the app's local preferences (including its alarm registry) remain. When the user next opens the app, reconciliation is expected to rebuild alarms from the task database.

## What the failure is

The alarm registry is a local index of task-end alarms. During reconciliation, the app compares registered alarm IDs with the tasks that still need alarms. For an ID no longer desired by the task database, it tries to cancel any matching Android `PendingIntent`, remove the registry entry and clear the associated notification.

The cancellation path looks up the alarm with `PendingIntent.FLAG_NO_CREATE`. That flag asks Android to return an existing `PendingIntent` without creating one. If no matching alarm exists, Android returns `null`; this is a normal “already absent” result.

The current `buildAlarmPendingIntent` method declares a non-null `PendingIntent` return type even though it is also used for that no-create lookup. Kotlin therefore throws:

```text
NullPointerException: getBroadcast(...) must not be null
```

The supplied stack trace reaches this failure while cancelling an alarm no longer desired by the database. The cancellation method catches the exception and returns `false`. Reconciliation then deliberately throws an `IllegalStateException` because cancellation reported failure. Since the exception occurs before registry cleanup, the stale ID remains and can cause the same diagnostic again on a later reconciliation.

The show/full-screen Activity `PendingIntent` lookup uses the same non-null builder pattern and the same no-create flags. It has the same latent issue, although the supplied trace fails on the broadcast lookup first.

## What is known and what is not

**Confirmed by the supplied log and current source:**

- Android returned no existing broadcast `PendingIntent` for an ID the app attempted to cancel.
- The Kotlin non-null return contract converted that result into the reported `NullPointerException`.
- Cancellation returned failure, causing reconciliation to raise a diagnostic error.
- Registry cleanup was not reached after the exception, allowing the stale ID to be retried.
- The process remained alive; the log contains no fatal Android runtime crash for this event.
- `MainActivity` requests task-alarm reconciliation on both activity start and resume. Reconciliation cleans registered IDs that are no longer desired before it schedules the still-desired future alarms.
- The failure shown in the supplied trace was reported through the task-change reconciliation path. The trace does not prove that it came from the first post-force-stop startup attempt, but that startup path calls the same reconciler and can fail on the same stale ID.

**Not established by this evidence:**

- Whether any additional issue also contributed to alarms not firing after force-stop. The reported force-stop explains why OS alarms can be absent while registry data remains, and this reconciliation failure explains how that mismatch can block their restoration.
- Whether unrelated platform messages in the capture, such as vendor EGL warnings, contributed. They do not appear in this alarm-reconciliation stack trace.

## Alternatives considered

1. **Make the existing builders nullable everywhere.** Rejected as the primary design. Those builders are also used to create or update alarms and notification activities, where a missing result is not the expected outcome. Making creation and lookup share one nullable contract would spread null handling to scheduling and notification call sites and blur two different operations.
2. **Catch and ignore the `NullPointerException`.** Rejected. This would treat an implementation-contract bug as a special exception, could hide unrelated null failures, and would not clearly distinguish “already absent” from a real cancellation failure.
3. **Clear the registry entry unconditionally.** Rejected. If an existing alarm was found but Android cancellation failed, erasing its bookkeeping could leave an active orphan alarm.
4. **Separate “find existing” from “create/update,” sharing the same canonical Intent identity.** Chosen. The find operations will explicitly return nullable results for `FLAG_NO_CREATE`; creation/update operations will retain their non-null behavior. A missing alarm or show `PendingIntent` will mean there is nothing of that kind left to cancel. Any other cancellation error will still fail and retain the registry entry for a later retry.

## Chosen correction and safety boundaries

The implemented cancellation path:

1. Uses nullable lookup methods for both the broadcast alarm and show Activity, constructed with the same component, action, package, data URI, request code, and immutable flag as their creation counterparts.
2. Cancels each `PendingIntent` that actually exists. If the broadcast exists, it is cancelled through `AlarmManager` and directly; the show Activity `PendingIntent` is cancelled if present.
3. Treats a missing `PendingIntent` as already absent, not as an error.
4. Removes stale alarm registry/deferred/trigger/tier metadata and cancels its notification only after required cancellation calls succeed.
5. Preserves failure behavior if `AlarmManager` is unavailable or a real cancellation/registry operation throws. Such failures are not silently converted into success.

This cleanup affects alarm bookkeeping and the task-end notification only. It does not delete or modify the task row and does not address the separate Focus-screen app-closing behavior.

## Regression verification

The project has JVM unit tests but no current Robolectric or mocking dependency for Android framework `PendingIntent`/`AlarmManager` behavior. Verification will therefore include the project's complete Android unit-test workflow and code-level checks for the nullable lookup contract and shared PendingIntent identity. Any limitation in directly exercising Android's framework return value in a local JVM test will be recorded explicitly rather than represented as an end-to-end device test.

## Resolution and verification

Implemented in `AlarmRepository`:

- Extracted shared canonical alarm and show Activity `Intent` builders, so create/update and find-existing operations use the same component, action, package, data URI, request code, and extras.
- Added nullable find-existing operations using `FLAG_NO_CREATE | FLAG_IMMUTABLE`. The non-null create/update builders remain unchanged in behavior and are still used for actual scheduling and notification creation.
- Updated reconciliation cancellation to cancel whichever `PendingIntent`s exist. If the alarm or show Activity `PendingIntent` is absent, that is treated as already absent. The stale registry metadata and notification are then cleared.
- Preserved failure reporting and registry retention if `AlarmManager` is unavailable or an actual cancellation/registry operation fails.

The new source-contract tests verify that no-create lookups are nullable, cancellation uses those lookup operations and reaches registry cleanup after absent results, and both lookup methods share the canonical Intent builders.

Verification completed:

- `bash scripts/test-unit.sh` passed for both `productionDebug` and `tbtechsdevDebug` JVM unit-test variants (`BUILD SUCCESSFUL`).
- The two new contract tests were present and passed in both variants (four executions total).
- `git diff --check` passed.

The project has no local Robolectric/mock setup for Android framework alarm services, so the tests verify the Kotlin lookup and cancellation contract rather than invoking Android's `PendingIntent`/`AlarmManager` implementation. A device test is still needed to confirm the full recovery sequence: force-stop the updated app, reopen it, and verify future task alarms are re-armed without the repeated diagnostic.
