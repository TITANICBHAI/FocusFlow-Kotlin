# Stats "Observed Device Time" reads zero (Today and Week): trace and fix

Traced on `new.zip` (latest code), separately from the plan. **Nothing in the plan, the tracker or the earlier review files was changed.** Section 9 lists plan changes I would propose; they are not applied and wait for your guidance.

Nothing was run on a device. Everything below comes from reading the code, plus a small simulation of the Kotlin logic (section 3). Line numbers refer to `new.zip`.

---

## 0. Summary

- **Root cause (certain, from the code).** `DeviceUsageSource.kt:74` turns the list of per-app rows into one row per date with `.associateBy { it.date }`. The aggregator sorts each date's apps from most used to least used, and `associateBy` keeps the last element for a key, so the row that survives is **the least-used app of the day**. Today always goes through this path, and so does any past day that has no stored rollup.
- **Effect on Today.** The total, the top-apps list and the hourly chart are all computed from one tiny app, so the card reads 0 min.
- **The Today fallback added in `new.zip` cannot help.** It only runs when there is no row for today (`AnalyticsProcessor.kt:96`), and the bug creates a row for today with 0 minutes.
- **Week.** Past days that have stored rollups use a different, correct path (grouped by date). So this bug alone makes Week wrong only for the days that did not come from rollups. A Week that is zero as well means no earlier day contributed; section 4 lists why and how to tell.
- **Fix.** Group the rows instead of collapsing them (a three-line change in `DeviceUsageSource.kt`), add the tests in section 7, and log failures (section 8).

---

## 1. What the screen shows and what that tells us

- The "Observed Device Time" card is only drawn when `phoneUsage` exists (`ArchivedStatsScreen.kt:176-181`). `phoneUsage` is null only when the summary, the hourly data and the daily list are all missing (`AnalyticsProcessor.kt:460-467`). So **a visible card reading 0 means the read succeeded and returned near-empty rows**. It does not mean "events unavailable"; in the code before the new Today fallback, that case hides the card.
- If the summary total is 0, the card falls back to the sum of the hourly buckets (`ArchivedStatsScreen.kt:596-600`). Both come from the same selected rows, so both are about 0. This is why the filter and "launchable" checks are not the likely cause of a zero total.

## 2. The trace (where the data is lost)

1. `UsageStatsRepository.readForegroundEvents` reads the platform events. Fine.
2. `ForegroundSpanTracker.sessions` turns them into sessions. Fine.
3. `UsageCalendarAggregator.aggregate` returns **one `AppUsageDay` per (date, package)**, ordered by date and then by foreground time, most used first (`UsageCalendarAggregator.kt:68-69`). Fine.
4. **`DeviceUsageSource.kt:74`: `.associateBy { it.date }`.** The map has one entry per date, holding the last row, which is the least-used app.
5. `DeviceUsageSource.kt:114-150`: for dates served by `LIVE_PIPELINE` (always today) or `ON_DEMAND_PIPELINE` (past days with no rollup), `selectedRows += listOfNotNull(pipelineDays[dateText]?.toHistoryAppDay())` adds that single row.
6. `:166-172` drops rows under 500 ms or not launchable; `:188` builds the hourly buckets and the daily totals from the same rows. With one tiny app everything is about 0.
7. The UI shows 0m, an empty top-apps list and a near-empty hourly chart.

The stored-rollup path is correct: `UsageHistoryRepository.deviceStatsRollups` uses `groupBy { it.date }`. The rollup writer and the detector on-demand reader also keep one row per app (they call `aggregate` and use the full list), so **the behaviour detectors do not share this bug**.

## 3. Reproduction (simulation of the Kotlin logic, not a device run)

One day, four apps: Instagram 40 min, WhatsApp 15 min, Maps 3 min, Settings 0.4 s.

| | Summary total | Top apps | Daily row for today |
|---|---|---|---|
| Current code (`associateBy`) | **0 min** | none | 0 min |
| With `groupBy` | **58 min** | Instagram 40, WhatsApp 15, Maps 3 | 58 min |

Week with four earlier days from stored rollups (120, 90, 150, 60 min): current code gives 420 min (earlier days correct, today lost); fixed gives 478 min.

## 4. Why Week can still be zero

With the bug alone, Week = earlier days (from rollups, correct) + today (collapsed). Week reads zero only if one of these holds:

1. **No earlier day in the range has events**, for example a new device or emulator, or the selected week starts today. Then Week equals Today.
2. **The earlier days did not come from rollups**, so they also took the collapsed path. Rollup write and read failures are caught and only logged (`DeviceUsageSource.kt`: "Could not persist on-demand usage rollups" / "Could not read persisted usage rollups"). Check logcat tag `DeviceUsageSource`.
3. Less likely: the 500 ms and launchable filter removing all apps. It cannot make the card total zero by itself because of the hourly fallback in section 1, but it would empty the top-apps list.

After the fix, if Week is still zero, the log lines in section 8 will say which of these it is.

## 5. The Today fallback added in `new.zip`

- `mergeTodayUsageFallback` (`AnalyticsProcessor.kt:88`) returns early when `daily.any { it.date == todayKey }` (`:96`). The bug always produces that row, so the fallback never runs in this case.
- When it does run it uses the old `getUsageSummary` path (different rules: 30 s launch debounce, no session logic) and adds to the summary and the daily list but not to the hourly chart, so the card could show numbers that disagree with each other.
- After the fix it would cover only "events unavailable". Whether to keep it is a plan decision (section 9).

---

## 6. The fix

In `DeviceUsageSource.read` keep every app of the day:

```kotlin
// line 74 — was: ).associateBy { it.date }
).groupBy { it.date }

// line 147-150 — was: listOfNotNull(pipelineDays[dateText]?.toHistoryAppDay())
UsageHistorySource.LIVE_PIPELINE,
UsageHistorySource.ON_DEMAND_PIPELINE -> selectedRows +=
    pipelineDays[dateText].orEmpty().map { it.toHistoryAppDay() }
```

Notes for the implementer (illustrative, not prescriptive):
- `toHistoryAppDay()` resolves the app name and category through `PackageManager` per row. Cache that per package inside one `read` call, since every app now produces a row.
- The aggregator and the rollup writer are correct; do not change them for this bug.
- Add a review gate: no `associateBy` or `toMap` over a list that has one row per (date, package).

## 7. Tests that would have caught it

The tracker describes synthetic fixtures; the tests are not in the zip, so I cannot tell what they contained. A fixture with one app per day cannot catch this. Add tests that go through `DeviceUsageSource.read` with a fake event source and a fake history store:

- **Today, several apps.** Fixture from section 3. Expect summary 58, daily total 58, hourly sum about 58, three apps; Settings (0.4 s) is dropped by the 500 ms floor.
- **Week, mixed sources.** Four stored rollup days plus a live today. Expect the total to equal the sum of the daily totals.
- **Live equals rollup.** The same multi-app day served through the live path and through the rollup path must give the same per-app minutes (this is the plan's own live-equals-rollup invariant, applied at the `DeviceUsageSource` level).
- **Unavailable events.** The result is an explicit "unavailable" state, never a zero total.

## 8. Related defects on the same path (not the cause, worth fixing together)

- **Failures are invisible.** `readSource` catches every exception and returns null with a FAILED state, with no log (`AnalyticsProcessor.kt:535-544`). Log the exception and reason, and consider showing "unavailable" rather than a card that reads 0.
- **Different filters inside one screen.** The summary total uses the 500 ms and launchable rules, but the hourly bars and daily totals do not (`DeviceUsageSource.kt:166-195`; item R-15 in the earlier review). Apply one rule at row level.
- **`isLaunchable` runs per package per read** through `PackageManager`; cache it.
- **Literal pipeline version.** `UsageRollupWriter.kt:122` writes `pipelineVersion = 1` while the other path uses the constant (also 1 today). Use the constant before the value changes.
- **Diagnostics to add while fixing** (debug level, one line per read): event count, session count, rows per date before grouping, each date's source and status, `selectedRows` and `displayRows` sizes, and the exception reason when the read fails.

## 9. Plan impact (proposal only, not applied)

Tell me which of these you want, and in what form, before I touch the plan or the tracker.

1. **Tests (plan section 9).** Require the Stats read to be tested end to end with a multi-app fixture, a mixed rollup plus live Week, and an "events unavailable" case, and require the live-equals-rollup test at the `DeviceUsageSource` level.
2. **Device acceptance (Batch 5).** Add an explicit item: Stats Today and Week show non-zero totals that match the per-day bars on a real device. Batch 5 cannot be marked complete without it.
3. **Today fallback.** The plan wants one pass (D2, D3). Decide: remove the fallback after the fix, or keep it only as a labelled "events unavailable" fallback, and update the plan text to match.
4. **Failure handling.** Extend plan rule 10 ("unknown, not zero") from the pipeline to the Stats view model and the UI: no catch-all that turns a failed source into an empty or zero result without a log and an explicit state.
5. **Review gate.** Add "no key-collapsing (`associateBy`, `toMap`) over per-(date, package) rows" to the review gates.
6. **Tracker.** Record this defect, its cause and its fix, and mark the Batch 5 acceptance items that depended on the single-pass Stats as not yet verified on a device.

## 10. Limits

- The Today cause is deterministic in the code. The Week outcome depends on whether earlier days had events and rollups on your device, which I cannot see; section 4 gives the checks.
- The manifest is not in the zip, so package visibility (`QUERY_ALL_PACKAGES`) is unverified. It matters only for the top-apps list and the 500 ms and launchable filter.
- The tests are not in the zip.
