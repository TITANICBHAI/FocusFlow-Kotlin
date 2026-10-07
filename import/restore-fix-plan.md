# FocusFlow — Restore Loop Fix Plan

## Summary

Importing a backup via **Import backup → Replace tasks** causes two dialogs
("Restore could not be completed" and "Discard this restore?") to cycle
forever, surviving app restarts. The root cause is a single missing JSON
serialisation option that makes the restore engine unable to read back the
journal it just wrote. Discard also has a secondary bug that can re-lock the
app instead of always reopening it.

---

## Bug 1 — Journal is written without its version tag (primary cause)

### Why it happens

`RestoreJournal` has a field:

```kotlin
val journalVersion: Int = JOURNAL_VERSION  // default = 1
```

kotlinx.serialization's default behaviour is to **omit fields that equal
their default value**. So when `AtomicRestoreJournalStore.write()` encodes the
journal, `journalVersion` is never written to disk.

When the engine reads it back, the version check looks for
`root["journalVersion"]`. It finds `null`, treats that as an unknown version,
and calls `blockUnreadableJournal()`. The journal is moved to a quarantine
file. Every Retry moves it back, reads it, and quarantines it again — an
infinite loop that also survives restarts because `AppModule.init()` starts
in `RECOVERY_BLOCKED` whenever the quarantine file exists.

### Fix — `data/restore/RestoreStores.kt`

1. Add `encodeDefaults = true` to a shared `RestoreStoreJson` codec object.
   Both stores (`AtomicPendingImportStore`, `AtomicRestoreJournalStore`) must
   use this codec everywhere they encode or decode.

2. Extract a pure, Android-free `parseJournalText(text: String)` function
   that handles the reading logic:
   - Missing `journalVersion` → **accept as v1** (tolerates files written
     before the fix; schema is identical to v1)
   - `journalVersion` present but ≠ 1 → `RestoreJournalRead.UnknownVersion`
   - Any parse error → `RestoreJournalRead.Corrupt`
   - Success → `RestoreJournalRead.Value`

3. Replace the inline read logic in `AtomicRestoreJournalStore.read()` with a
   call to `parseJournalText(...)`.

**Effect on the stuck phone:** the quarantined journal lacks the version field.
The tolerant reader accepts it. Retry finishes the Replace normally (deleting
existing tasks and inserting the backup's tasks). If you want to keep existing
tasks instead, choose Discard.

---

## Bug 2 — Discard can re-lock the app instead of always reopening it

### Why it happens

`discard()` sets a boolean flag `discardReconciliationPending = true`, then
calls `actions.reconcileCurrentState()`. If that call throws — for example, if
the alarm manager rejects a schedule — the `catch` block calls
`gate.markRecoveryBlocked()`. The app ends up locked again even though the
journal has been deleted and the user's restore decision is already durable.
The "retry" path in `retryInternal()` also checks this flag and routes to a
different code path, which is unnecessary complexity.

### Fix — `data/restore/RestoreRecoveryEngine.kt`

1. Remove `discardReconciliationPending` entirely.

2. Rewrite `discard()` with a clear two-phase approach:

   **Phase 1 — delete artifacts (must always succeed to proceed):**
   ```
   journalStore.deleteJournal()
   journalStore.deleteQuarantine()
   ```
   If this throws (e.g. storage error), stay blocked and return
   `Result.failure`. The user can try again.

   **Phase 2 — repair derived state (best-effort, always reopens after):**
   ```
   repeat up to maxAttempts (default 3) with retryDelayMillis between tries:
       actions.reconcileCurrentState()
   ```
   Whether repair succeeds or not, always call:
   ```
   _state.value = RestoreUiState.Idle
   gate.reopen()
   ```
   Return `Result.failure(lastException)` if all repair attempts fail, so the
   caller can show an informative toast, but the app is **always** reopened.

3. Remove the `discardReconciliationPending` branch from `retryInternal()`.
   After the fix, a Retry after a Discard cannot happen because the gate has
   been reopened and `gate.beginRetry()` returns false unless the gate is
   `RECOVERY_BLOCKED`.

**Note:** Alarms are also reconciled on every Activity start and resume
(`requestTaskAlarmReconciliation("activity_start")`), so even a failed repair
self-corrects the next time the user opens the app.

---

## Bug 3 — Duplicate error message in the "Restore could not be completed" dialog

### Why it happens

`RestoreUiState.Blocked.message` already contains the string
"The restore record could not be read. Retry may not succeed.". The dialog
in `MainActivity` also shows a second `Text()` with the same sentence when
`blocked.unreadableJournal` is true. The user sees the sentence twice.

### Fix — `MainActivity.kt`

Change the second `Text()` to a helpful hint instead:

```
"If retrying does not help, choose Discard to keep your current data."
```

Also improve the Discard failure toast to accurately reflect whether the gate
actually reopened:

```kotlin
if (reopened) {
    "The app reopened, but some derived state could not be refreshed."
} else {
    "The restore could not be discarded. Please try again."
}
```

---

## Files to change

| File | What changes |
|------|-------------|
| `data/restore/RestoreStores.kt` | Add `RestoreStoreJson` with `encodeDefaults = true`; add `parseJournalText()`; use both in both stores |
| `data/restore/RestoreRecoveryEngine.kt` | Remove `discardReconciliationPending`; rewrite `discard()` to always reopen; remove discard branch from `retryInternal()` |
| `MainActivity.kt` | Fix duplicate error sentence; fix failure toast |

No schema change, no database migration, no new dependencies.

---

## Why the TS hybrid worked

The TS hybrid kept the backup JSON in a memory map and applied it directly via
`restoreFromJson()`. There was no journal file and no read-back step, so there
was nothing to fail. The Kotlin journal approach is architecturally better
(crash-safe, idempotent, multi-phase) — it just had this one codec gap.

---

## Tests to add

Add a new JVM (non-Android) test file:

**`app/src/test/…/data/restore/RestoreJournalCodecTest.kt`**

| Test | Verifies |
|------|----------|
| `writtenJournalCarriesExplicitVersionAndRoundTrips` | `encodeDefaults = true` writes the version field; decoding produces the same object |
| `journalWrittenWithoutDefaultsStillReads` | Legacy files (missing version) are accepted as v1 |
| `differentVersionIsRejected` | Version ≠ 1 returns `UnknownVersion` |
| `garbageIsCorrupt` | Non-JSON and JSON arrays return `Corrupt` |

These tests are pure JVM — no Android emulator or Robolectric needed.

---

## Roll-out notes

- **Existing stuck phones:** install the fix → Retry once → restore completes
  normally (Replace mode, existing tasks deleted). Tell users about Discard if
  they want to cancel and keep current data.
- **If Retry still fails after the fix:** send the Diagnostics log. The next
  likely failure point is `applyRestoreTaskPlan` (Room write error) or
  `reconcileDuringRestore` (alarm manager rejection).
- **No data loss risk from the fix itself.** The journal and quarantine files
  are read-only until the user explicitly presses Retry or Discard.
