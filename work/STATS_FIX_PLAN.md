# Stats Screen — Fix Plan

**For:** whichever agent is implementing these  
**Not done by:** the audit that produced this doc — investigation only, no
edits applied for anything below. Five earlier items *were* already fixed
directly (listed in Part 0 for context) — everything in Part 1 onward is
still open.

**How to use this doc:** each item has the evidence that confirms it's real,
the exact root cause, and a fix *direction* — not a diff to paste. Read the
cited function before changing it; in a couple of cases (marked) the fix
needs to reach into a second file to do it right rather than patch the
symptom in place.

---

## Part 0 — Already fixed, for context only

These were applied directly in a prior pass. Don't redo them; do check they
survived if this repo has diverged since:

1. `StatsViewModel.kt` — `_selectedRatingDate` no longer hardcodes
   `todayLocalDate()`. It now calls `resolveDefaultRatingDate()`, which reads
   `sleep_time_hour`/`sleep_time_minute` from `focusday_prefs` and defaults
   to yesterday until 30 minutes before that time, matching
   `DayRatingNotificationScheduler`'s own threshold.
2. `StatsViewModel.kt` — `reload()`'s `_loadState` computation no longer
   applies `Unavailable` to every window. It's scoped to
   `ANALYTICS_THREE_MONTHS` only now (matching how `PermissionNeeded` was
   already correctly scoped); every other window always resolves `Ready`.
3. `StatsInsightsExperience.kt` — the `Ready` branch's `item {}` block now
   computes one `isEmpty` flag and gates `FocusTimeHero`, the insight cards,
   the whole `when(window)` block, and `TemptationStats` behind it. When
   true, only `EmptyStatsState` renders instead of all of them separately.
4. `EmptyStatsState.kt` — added distinct copy for `ANALYTICS_TODAY` and
   `ANALYTICS_ALL_TIME` instead of falling through to the Week tab's wording.
5. `PermissionGate.kt` / `UnavailableGate.kt` — both had zero layout
   modifiers (no padding, no centering). Added `fillMaxSize().padding(32.dp)`
   with centered content to both.

---

## Part 1 — High priority

### 1.0 — Re-verification note (read this first)

Everything in Part 0 was re-checked directly against the current codebase
and confirmed still intact — no regressions. One correction: the UI_REFRESH
work referenced informally in earlier discussion (FindingCardView accent
system, "pattern" fallback label, clean-state copy) was never actually
applied to this codebase. It's folded into 1.7 below rather than treated as
done. Items 1.4–1.6 below were found by reading `FindingRepository.kt` and
the finding lifecycle end to end — they're more consequential than anything
in the original pass, so they're placed first.

### 1.1 — 3-Month hourly usage chart is wrong by construction, not just stale

**File:** `data/repository/UsageStatsRepository.kt`, `getHourlyUsageSummary()`
(~line 260)

**Root cause, confirmed by reading the function directly:**

```kotlin
val stats = usageManager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, start, end)
for (stat in stats) {
    val calendar = Calendar.getInstance().apply { timeInMillis = stat.firstTimeStamp }
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    hourlyMilliseconds[hour] += stat.totalTimeInForeground
}
```

`queryUsageStats` has no hour-of-day dimension — it returns aggregate
`totalTimeInForeground` per app for whatever interval Android picks
internally. For a 3-month range, `INTERVAL_BEST` resolves to weekly or
monthly buckets. This code takes each bucket's *entire* total and dumps it
into whichever hour corresponds to `stat.firstTimeStamp` — which for a
weekly/monthly bucket is almost always midnight. The result isn't "only
shows the last week," it's structurally capable of putting a whole quarter's
usage into the 12am bar with the other 23 sitting at zero, regardless of
when the person actually used their phone. Called from
`AnalyticsProcessor.kt` line ~503, passed the full 3-month `range` unmodified.

**Why the fix isn't "patch the math":** there is no correct way to recover
genuine hour-of-day data from `queryUsageStats` — it fundamentally doesn't
carry that information at any interval. The bucket-start-timestamp trick is
the entire bug; there's no smaller version of it that's still correct.

**Fix direction:** `daily_app_usage.hourly_ms` (written by
`AppUsageAndSessionTracker` in real time since it shipped) already has
genuine per-hour data with no OS retention limit, because it's accumulated
as usage happens rather than reconstructed after the fact. Point
`PhoneUsageSummary`'s data source at `dailyAppUsageDao.getForDateRange(...)`
instead — sum the 24-value `hourly_ms` CSV column across every row in the
requested range. This retires `getHourlyUsageSummary()` for this call site
entirely rather than fixing it in place. Check whether anything else in the
codebase still calls `getHourlyUsageSummary()` before deleting it outright —
if nothing else does, remove it rather than leave dead code that looks like
a second source of truth.

**Scope check before starting:** confirm `daily_app_usage` actually has 3
months of history to read in whatever environment this gets tested in — it
only started accumulating once the tracker shipped, so a fresh test device
needs time (or seeded fixture data) before this fix is visibly correct
end-to-end.

**Compounding this:** `buildAnalyticsSnapshot`'s `usageReads` block (the
`coroutineScope { async { getUsageSummary(...) }; async { getHourlyUsageSummary(...) } }`
pair) is not gated by `window` at all — it runs on every single tab load
(Today, Yesterday, Week, All Time, not just Three Months) for any user who
has granted Usage Access, per `AppModule`/`StatsViewModel` passing
`usageStatsPermission = null` for non-3-month windows, which
`buildAnalyticsSnapshot` resolves by calling
`usageStatsRepository.hasPermission()` directly rather than skipping the
read. `PhoneUsageSummary` — the only component that ever renders
`snapshot.phoneUsage` — only appears in the `ANALYTICS_THREE_MONTHS` branch.
Four out of five tab switches are triggering real `queryUsageStats` calls
and building a value nobody sees, and for as long as this ticket's core bug
stands, they're doing that wrong computation constantly rather than only
when the 3-Month tab is open. When fixing the data source (pointing at
`daily_app_usage` instead), also gate the read itself to
`window == ANALYTICS_THREE_MONTHS` so the other four tabs stop doing this
work at all.

---

### 1.4 — `markResolved()` is never called — the resolved state is unreachable, not just undisplayed

**Files:** `data/repository/FindingRepository.kt`,
`analytics/detection/*.kt` (all detectors), `analytics/detection/FindingDetectionRunner.kt`

**Confirmed by:** project-wide grep for `markResolved` — the only two hits
are the function's own definition in `FindingRepository` and the matching
`@Query` in `FindingDao`. Nothing calls it.

This is a different, more severe problem than "resolved findings aren't
shown in the UI" (which is a real, separate, already-known gap in
`FindingDao.getActiveFindings()`'s `NOT IN ('intentional', 'resolved')`
filter). This is: **no code anywhere ever re-evaluates an existing finding
to check whether its underlying pattern has faded.** A finding is created
once by a detector returning non-null, and from that point on it can only
move through `seen` → `intentional`/`aware` via explicit user action. There
is no path — automatic or otherwise — that ever marks it `resolved`, even if
the person's actual behavior completely changes. The original design
called for exactly this ("a pattern that fades gets acknowledged as having
improved, not left sitting there indefinitely") — it was never implemented
in any of the detection work.

**Decided: Path 1 only, no hybrid.** A finding resolves after 21
consecutive days where the detector does not fire for that specific
`(detectionType, subjectPackage)` — full stop, no early-resolve shortcut for
a dramatic single-day drop. Reasoning: the metric each detector watches is
already a rolling multi-day average (21 days for Variable Reward Loop, for
example), so a genuinely decisive change already shows up as a steep drop
within that rolling number on its own — a second, separate "resolve early if
it's way below threshold" rule mostly duplicates work the rolling average
already does, while adding its own threshold to pick, defend, and test. The
one real cost of Path 1 alone is a slower "good news" message for the
person who makes an obvious, decisive change (deletes the app outright,
say) — three weeks of latency on a positive message is a fine trade for a
product built end to end around never overclaiming and never flip-flopping.

**Implementation note — this needs new state, not just new logic. Track
calendar time, not job-run count.** There is currently nowhere that records
"this pattern was last confirmed present" — only firing is ever recorded
(via `submit()`). The natural first instinct is a counter incremented once
per daily run the detector stays silent, but that's fragile specifically on
Android: `BackgroundFetchWorker` is not guaranteed to run exactly once every
calendar day — Doze, aggressive OEM battery management (the same class of
problem this app already fights for the blocking feature itself), a
force-stop, or the phone simply being off can all skip a day's execution. A
counter tied to "how many times the job ran and saw nothing" can
under-count real elapsed absence on a device with spotty background
execution, sometimes by a lot.

Use a timestamp instead: a new column on `findings`, e.g.
`last_positive_evidence_at TEXT` (ISO 8601), that:
- gets set to "now" every time `submit()` sees the pattern fire or resurface
  for that `(detectionType, subjectPackage)` — including the moment the
  finding is first created
- on each daily run where the detector does *not* fire for a pair that has
  an existing `detected`/`seen`/`aware` finding, compare
  `now - last_positive_evidence_at` against 21 days; call `markResolved()`
  once that gap is reached

This is calendar-time-based, so it's correct regardless of how many of
those 21 days the background job actually got to run on. Small schema
migration (one nullable text column), not just new runner logic.

**Expected, not a bug:** a pattern hovering right at the edge — absent 18
days, reappears once, `last_positive_evidence_at` bumps back to now — will
never reach 21 days and never resolve. That's correct: the behavior is
genuinely still present, just intermittent, and Path 1 is supposed to
reflect that rather than resolve on a technicality.

**Free byproduct, worth its own small ticket rather than bundling in here:**
once this is timestamp-based, "how many days has this been quiet so far" is
one subtraction away with no new state (`now - last_positive_evidence_at`)
— that supports an optional soft progress signal on the finding card
("quieter for 12 of the last 21 days") without touching the resolution rule
itself or its timing. Doesn't need to ship with this fix; the data will
already be there whenever it's wanted.

**The trap to watch for — multi-candidate detectors return only one winner
per run, and that's not the same thing as "only one app still qualifies."**
Variable Reward Loop, Infinite Session Design, Escalating Capture, and
Streak Lock-in are all designed (deliberately, from earlier in this
project) to submit only the single strongest candidate app per run, so a
qualifying-but-not-strongest app never gets silently dropped by the 7-day
surface cooldown. That design choice creates a real problem for absence
tracking specifically: if Instagram's Variable Reward Loop finding already
exists, and today TikTok happens to edge it out as the highest-CV app, the
detector's return value for today is TikTok — not because Instagram
stopped qualifying, but because it's no longer the *winner* of a
single-winner comparison. A naive absence check ("today's result isn't
Instagram, so count Instagram as absent today") would start counting toward
resolving Instagram's finding for the wrong reason, and could eventually
mark it resolved while the exact same pattern is still fully present in
Instagram's own data — just not the single loudest one that day.

**Fix direction for this specifically — one evaluation function, two thin
callers, not two functions that happen to agree today.** Don't write a
second, separately-maintained function per detector to check one named
package — that creates two logic paths that can silently drift apart the
first time one gets tuned and the other doesn't. Instead, restructure each
multi-candidate detector so there's a single function that scores *every*
candidate (something like `evaluateCandidates(...): List<CandidateResult>`),
and both existing entry points become thin wrappers over it:
`detectVariableRewardLoop()` picks the top-scoring candidate from that list
to submit as new; the absence check calls the same function and looks up
whether one specific package is still present in the qualifying list,
regardless of whether it's the top score that day. One source of truth,
two consumers. Single-package detectors (Morning Hijack already resolves to
one package per run by nature; Substitution and Allowance Suggestion aren't
per-app in the same way) don't hit this problem and can use their existing
return value directly.

**Deployment note:** every existing finding starts this migration with no
`last_positive_evidence_at` history — expect zero resolutions in the first
21 days after this ships, even for patterns that had already been absent
for a while beforehand. That's the correct, unavoidable cold start for a
timestamp the app never tracked before now, not a sign the fix didn't take.

---

### 1.5 — The 60-day "I chose this" suppression is never actually checked

**File:** `data/repository/FindingRepository.kt`, `submit()`

```kotlin
existing.state == "intentional" ->
    if (existing.evidenceFingerprint == finding.evidenceFingerprint) false
    else { resurface(existing.id, finding); true }
```

`acknowledgeIntentional()` correctly writes `suppressedUntil` 60 days out
when the user responds "I chose this." `submit()` never reads
`existing.suppressedUntil` anywhere — the only gate on resurfacing an
intentional finding is whether the fingerprint changed at all. A fingerprint
is deliberately sensitive to real evidence drift (bucketed sample size and
metric, not exact-match), so it can legitimately shift within days of
acknowledgment as more data comes in — nowhere close to 60 days, and not
necessarily because the pattern got worse in any meaningful sense; it can
shift on an *improving* trend too, since the check is fingerprint
inequality, not magnitude or direction of change.

Net effect: a user explicitly tells the system "I know about this, leave it
alone," and it can come back within the week. This isn't a cosmetic gap —
it's the specific thing that makes the difference between a system that
respects a stated choice and one that nags anyway.

**Fix direction:** the `existing.state == "intentional"` branch needs a time
check before anything else: if `Instant.now()` is before
`Instant.parse(existing.suppressedUntil)`, return `false` unconditionally —
still suppressed, full stop, regardless of fingerprint. Only past that date
should fingerprint comparison even run. Separately, re-read the original
intent (evidence would need to worsen by a real margin, not just differ, to
justify an *early* resurface before the 60 days are up) and decide whether
that early-resurface path is worth implementing now or whether "wait out
the 60 days, then treat any change as fair game" is an acceptable v1 — the
time-check alone fixes the actively-broken part; the magnitude-of-change
nuance is a smaller refinement on top.

---

### 1.6 — The one-per-week pacing resets as soon as the current finding is read

**File:** `data/repository/FindingRepository.kt`, `insertIfCooldownElapsed()`

```kotlin
private suspend fun insertIfCooldownElapsed(finding: FindingEntity): Boolean {
    val recent = findingDao.getMostRecentDetected()   // WHERE state = 'detected'
    if (recent != null && daysBetween(recent.firstDetectedAt, now) < 7) return false
    findingDao.insert(finding)
    return true
}
```

`getMostRecentDetected()` filters to `state = 'detected'` — i.e., "is there
one still sitting unread." The moment a user taps their current finding
(moving it to `seen`), that query returns null on the very next detection
run, and the cooldown check passes immediately — not because 7 real days
elapsed, but because nothing is currently unread. An engaged user who checks
Stats daily and reads each finding promptly could receive a new one every
single day. Someone who never opens Stats gets throttled correctly by
accident; someone who actually uses the feature as intended gets the
opposite of the one-per-week pacing that was the whole point.

**Fix direction:** the cooldown needs to be keyed to calendar time since the
last finding was *created*, regardless of its current state — query
`MAX(first_detected_at)` across all findings with no `state` filter, not
just ones still sitting in `detected`. `getMostRecentDetected()` can stay for
whatever else it's used for, but `insertIfCooldownElapsed` needs a
state-agnostic version of that timestamp lookup.

---

### 1.7 — FindingCardView still has the pre-refresh visual treatment

**Files:** `ui/stats/FindingCardView.kt`, `ui/stats/FindingsSection.kt`

Confirmed by direct re-read: neither file reflects the accent-driven,
single-signal design discussed earlier. Concretely, still present:

- A `NEW` / `WATCHING` text badge sitting next to the category label,
  duplicating what the label's own color could carry alone
- `"FINDING"` as the generic fallback label for non-manipulation findings,
  where the existing insight-rule vocabulary (`WeeklyRules.kt`,
  `ThreeMonthRules.kt` already use `category = "pattern"`, `"trend"`,
  `"resistance"`) has a real word available
- The clean-state card in `FindingsSection.kt` still reads *"No unusual
  patterns detected. The system has nothing to report."*

This was written up in detail once already with a specific file-by-file
diff (shared separately) — restating the two changes needed in short form
here so this ticket list is the single complete reference:

1. Collapse the category label + NEW/WATCHING badge into one signal: the
   label's own color/alpha carries read-state (full color = unread, ~60%
   alpha once seen, amber = aware/still-open) — no second badge element.
2. Replace the `"FINDING"` fallback with `"pattern"`, matching the
   vocabulary the rest of Stats already uses for this kind of content.
3. Rewrite the clean-state copy to drop "the system" — plain, user-facing
   language matching every other finding body already written.

---

### 1.2 — TaskResultList's empty message ignores its own title

**File:** `ui/stats/TaskResultList.kt`

```kotlin
fun TaskResultList(snapshot: AnalyticsSnapshot, title: String = "YESTERDAY'S TASKS") = StatsCard {
    Text(title, ...)
    val rows = snapshot.tasks.resultRows.orEmpty()
    if (rows.isEmpty()) Text("No tasks were recorded yesterday.", ...)  // hardcoded
```

Called from the Today branch with `title = "TODAY'S TASKS"`. On a Today with
zero scheduled tasks but some session/blocking activity (so the screen-level
`isEmpty` gate from Part 0 item 3 doesn't suppress it), the card reads:

> TODAY'S TASKS
> No tasks were recorded yesterday.

Two lines, one card, contradicting each other.

**Fix direction:** derive the empty-state line from the same `title` the
caller already passes, or from an explicit second parameter
(`emptyMessage: String = "No tasks were recorded yesterday."` with the Today
call site passing its own). Don't infer the day word from the title string
by parsing it — pass it explicitly so a future third call site
(`"ALL TIME'S TASKS"` or similar) can't reintroduce the same mismatch.

---

### 1.3 — ProductivityHeatmap renders a single populated cell under "Today"

**Files:** `ui/stats/StatsParityCards.kt` (`ProductivityHeatmap`),
`analytics/AnalyticsProcessor.kt` (`buildTaskMetrics`, `getAnalyticsRange`)

**Root cause, confirmed by reading both functions:** `getAnalyticsRange` for
`ANALYTICS_TODAY` returns a range of exactly one calendar day
(`now.toLocalDate().atStartOfDay()` through `+1 day - 1ns`). `buildTaskMetrics`
populates `byDayOfWeek` only from the `tasks` list it's handed — which, for
the Today window, only ever contains today's tasks. Every other weekday
bucket in `byDayOfWeek` stays at its zero default. `ProductivityHeatmap`
titles itself "WEEKLY PRODUCTIVITY" and renders all 7 buckets regardless —
under the Today pill this reliably shows one colored cell and six empty grey
ones for a component whose own label promises a week of pattern.

**Fix direction:** this component's data is only meaningful for windows
where `byDayOfWeek` genuinely spans multiple days — confirm which those are
by checking `getAnalyticsRange`'s branches for `ANALYTICS_WEEK`,
`ANALYTICS_ALL_TIME`, and whatever `ANALYTICS_THREE_MONTHS` resolves to.
Remove the `ProductivityHeatmap` call from the `ANALYTICS_TODAY` branch in
`StatsInsightsExperience.kt`'s `when(window)` block. If a genuinely
Today-relevant version of this idea is wanted, it would need a different
data source (e.g. an hour-of-day view of *today specifically*, not a
day-of-week view) — that's a new component, not a fix to this one.

---

## Part 2 — Medium priority (redundancy, not incorrectness)

### 2.1 — Week tab: PresenceStrip and ProductivityHeatmap show the same thing twice

**Files:** `ui/stats/PresenceStrip.kt`, `ui/stats/StatsParityCards.kt`

Both read `snapshot.tasks.byDayOfWeek` for the same 7 days. `PresenceStrip`
reduces it to a binary checkmark (`total > 0`); `ProductivityHeatmap` shows
the actual count plus a completion-rate color intensity for the identical
data. Everything `PresenceStrip` conveys is a strict subset of what
`ProductivityHeatmap` already shows more precisely, and they render back to
back on the Week tab.

**Separately:** `PresenceStrip` only checks `tasks.byDayOfWeek`, never
`sessions.byDayOfWeek` — a day with two clean focus sessions and zero
scheduled tasks reads as "absent" on the strip.

**Fix direction:** remove `PresenceStrip` from the Week branch in
`StatsInsightsExperience.kt` and keep `ProductivityHeatmap` — it's the
strictly more informative of the two, already present on the same tab. If
`PresenceStrip` is used anywhere else, confirm before deleting the component
outright; if Week is its only call site, delete the file.

---

### 2.2 — All Time tab: two components, two numbers, same questions

**File:** `ui/stats/StatsParityCards.kt` (`FocusTimeHero`, `TaskSummary`,
`AllTimeStats`)

`FocusTimeHero` shows `snapshot.sessions.totalFocusMinutes`. `AllTimeStats`,
rendered later on the same screen, computes its own focus figure as
`lifetime?.totalFocusMinutes ?: snapshot.sessions.totalFocusMinutes` —
preferring a separately-fetched `lifetime: LifetimeStats?` object. Nothing
enforces these two numbers agree; they're two different code paths both
answering "how much have you focused, total." Same pattern for task counts:
`TaskSummary`'s Done/Skipped/Missed sits next to `AllTimeStats`'s own
"`{tasks}` tasks" figure.

Best case, the numbers happen to match and it's just visual redundancy.
Worst case, `lifetime` and the windowed snapshot diverge and the screen
shows two different answers to the same question.

**Fix direction:** `FocusTimeHero` and `TaskSummary` were built for
single-window views (Today/Yesterday/Week) where they're the only summary
on screen. On All Time, `AllTimeStats` already exists specifically to answer
the lifetime version of the same questions. Remove `FocusTimeHero` and
`TaskSummary` from the `ANALYTICS_ALL_TIME` branch; `AllTimeStats` stays.
Before removing, check whether `lifetime` can actually be null in practice
(the fallback in `AllTimeStats` suggests it can) — if so, confirm
`AllTimeStats` degrades sensibly on its own without `FocusTimeHero`'s number
to fall back on.

---

## Part 3 — Low priority

### 3.1 — TrendChart x-axis labels can reference weeks with no plotted data nearby

**File:** `ui/stats/TrendChart.kt`

```kotlin
val labels = listOfNotNull(
    points.firstOrNull()?.weekStart?.let(::weekLabel),
    points.getOrNull(points.lastIndex / 2)?.weekStart?.let(::weekLabel),
    points.lastOrNull()?.weekStart?.let(::weekLabel),
).distinct()
```

Labels are picked from `points` (all 12 weeks), but the line itself is drawn
only from `chartPoints = points.filter { it.hasData }`. If the first or last
week in the raw 12-week range has no data — plausible for a user whose
history doesn't span the full window — the axis shows a date with no line
anywhere near it.

**Fix direction:** pick the three label points from `chartPoints` instead of
`points`, same indexing logic.

---

### 3.2 — PhoneUsageSummary's secondary label

**File:** `ui/stats/PhoneUsageSummary.kt`

`"ON DEVICE"` sits directly under `"ANDROID USAGESTATS"`, ahead of a line
that already explains the data is Android app foreground time. Not a bug,
just a redundant label — optional cleanup, skip if time-constrained.

---

## Suggested order

Revised given what this pass found: **1.5 first**, not 1.1. A chart showing
wrong numbers on one tab is a correctness bug; a suppression the user
explicitly asked for not actually suppressing is a trust bug, and it's a
three-line fix (add the time check) versus 1.1's full data-source swap.
After that: 1.6 (same file, same function neighborhood, small and
mechanical), then 1.4 (needs a real decision on the consecutive-non-firing
question before implementing, so leave room for that). 1.1 next — the
biggest single piece of work here, and the one most worth confirming
end-to-end against real multi-week data once done. 1.2 and 1.3 are quick,
independent, safe to slot in anywhere. 1.7 (the visual refresh) whenever —
it's the only item here with zero correctness or trust implications, purely
cosmetic. Part 2 is cleanup, batch together. Part 3 is optional.
