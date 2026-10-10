# "Rate your day" notification: crash on tap, and the wrong destination

Traced on `new.zip` (latest code). Not part of the Phase 5 plan, so **no plan or tracker change is involved**. Nothing was run on a device; everything below comes from reading the code. Line numbers refer to `new.zip`.

---

## 0. Summary

- **Crash (certain from the code).** Tapping the notification starts `LauncherActivity` with the action `ACTION_OPEN_DAY_RATING`. Its `onCreate` forwards to `MainActivity`, calls `finish()` and returns **before** `prefs` is assigned. `prefs` is `lateinit`. Android then runs `onDestroy()`, which starts with `prefs.unregisterOnSharedPreferenceChangeListener(...)`, so the app dies with `UninitializedPropertyAccessException`. That is the "crashes almost instantly after opening" you see.
- **Wrong destination (a second bug, hidden behind the crash).** Even without the crash, the tap would not land on Stats, Extra, Today. `StatsScreen` never opens Extra for this flag, so the user would land on the normal Stats report and the day-rating bar would not be shown.
- **A third crash waiting behind the second.** Once Extra is opened for this flag, `DayRatingBar` calls `focusRequester.requestFocus()` on a node that is not always composed, which throws `IllegalStateException` when today is already rated.

---

## 1. How the tap travels

1. `DayRatingReminderReceiver.kt:51-60` builds the notification tap intent for **`LauncherActivity`** with action `ACTION_OPEN_DAY_RATING` and flags `NEW_TASK | CLEAR_TOP`.
2. `LauncherActivity.kt:394-398` (`onCreate`) and `:412-414` (`onNewIntent`) forward it with `openDayRatingInMainActivity()` (`:417-423`), which starts `MainActivity` with the same action and `CLEAR_TOP | SINGLE_TOP`.
3. `MainActivity.kt:127-129` maps the action to `Routes.STATS` and sets `focusDayRating = true` (`:84`, `:104`).
4. `FocusFlowNavGraph.kt:265` passes the flag to `StatsScreen`.
5. `StatsScreen.kt:23-50` shows the report by default and shows Extra (`StatsInsightsExperience`, `screenTitle = "Extra"`) only when `showExtra` is true. The flag is passed only to Extra (`:32`).
6. `StatsInsightsExperience.kt:127-139` draws `DayRatingBar(requestFocus = focusDayRating)` only when the window is Today or Yesterday.
7. `DayRatingBar.kt:176-178` runs `if (requestFocus) focusRequester.requestFocus()`.

## 2. The crash

**Code.** `LauncherActivity.kt:393-402`:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (intent?.action == ACTION_OPEN_DAY_RATING) {
        openDayRatingInMainActivity()
        finish()
        return                       // <- prefs (line 143, lateinit) is assigned at line 402
    }
    ...
    prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
```

A `finish()` inside `onCreate` makes Android call `onDestroy()` straight away, and `onDestroy` (`:437-440`) begins with `prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)`. `onPause` (`:432-435`) has the same call but is not reached on this path.

**When it happens.** When `LauncherActivity` is **not already alive**, for example after the process was killed or when FocusFlow is not the running home screen. If `LauncherActivity` is already running, the tap arrives through `onNewIntent`, which does not crash. That is why it feels like "crashes right after opening the app".

**How to confirm in one run.** Kill the app, post or wait for the notification, tap it, and read logcat. Expected line:
`kotlin.UninitializedPropertyAccessException: lateinit property prefs has not been initialized` with `LauncherActivity.onDestroy` in the stack.

**Fix (any one of these removes the crash; the first two together are the safest).**
1. Make the lifecycle callbacks safe: in `onPause` and `onDestroy` use `if (::prefs.isInitialized) prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)`. This also protects every other early-exit path that may be added later.
2. Initialize `prefs` before the early return (move the `prefs = getSharedPreferences(...)` line above the `if`).
3. Stop routing through the home activity: build the notification tap intent for `MainActivity` directly (same action, flags `NEW_TASK | CLEAR_TOP | SINGLE_TOP`). `LauncherActivity` is the home-screen activity and should not be a trampoline. Keep fixes 1 or 2 anyway, because notifications already posted on devices still carry the old target.

## 3. The wrong destination

**Code.** `StatsScreen` keeps `showExtra` in `rememberSaveable` with default `false` and nothing sets it from `focusDayRating`. `ArchivedStatsScreen` has no day-rating bar, so after the crash fix the user lands on the normal report and the flag does nothing. The window also stays whatever it was last (`statsViewModel.activeWindow`), which may not be Today.

**Fix (illustrative).** In `StatsScreen`, when `focusDayRating` becomes true: call `statsViewModel.setWindow(ANALYTICS_TODAY)`, select today's date for rating, and set `showExtra = true`.

**Important detail.** `MainActivity` never resets `focusDayRating` (`:75`, `:84`, `:104`); it stays true until another intent arrives. If Stats opens Extra whenever the flag is true, every later visit to Stats would jump to Extra. Add a "handled" callback so `StatsScreen` consumes the flag once, and make sure a process recreation does not replay it (`showExtra` is saved with `rememberSaveable`).

## 4. The latent crash in `DayRatingBar`

**Code.** `DayRatingBar.kt:176-178` calls `focusRequester.requestFocus()` in a `LaunchedEffect`. The `focusRequester` modifier is attached only to the 1 to 10 rating row (`:258-262`), which sits in the `else` branch of `if (!isEditing && currentRating != null)` (`:239`). If today is already rated, that row is not composed and `requestFocus()` throws `IllegalStateException: FocusRequester is not initialized`.

**When it happens.** The reminder only posts if today is unrated, but the notification stays on screen until tapped, so the user can rate the day another way first and then tap it. The same call can also run before the node is attached on a very fast first composition.

**Fix (illustrative).** Request focus only when the target row is composed (key the effect on `requestFocus` and `isEditing`), and wrap the call so that a missing node is ignored; or, when the flag is set and today is already rated, show the saved state instead of requesting focus. Also check `reflectionPromptsEnabled`: when it is off, no bar is drawn, so the flow should open the normal report.

## 5. Tests and checks

- **Cold start.** Kill the app. Run `adb shell am start -n com.tbtechs.focusflow/.enforcement.LauncherActivity -a com.tbtechs.focusflow.action.OPEN_DAY_RATING --activity-clear-top` and expect Extra, Today, with the rating row visible and no crash. (`LauncherActivity` is the home activity, so it is exported.)
- **Warm start.** Repeat with `MainActivity` open on another tab, and with `LauncherActivity` already running as the home screen.
- **Already rated.** Rate today, then tap the stale notification: no crash, and the saved rating is shown.
- **Different window.** Leave Stats on Week, tap the notification: the window switches to Today.
- **Prompts off.** Turn off reflection prompts and tap the notification: the app opens Stats without a crash.
- **Regression test.** An instrumented or Robolectric test that launches `LauncherActivity` with the action and asserts no exception and that `MainActivity` starts; a Compose test that composes `DayRatingBar(requestFocus = true)` with an existing rating.

## 6. Limits

- The manifest is not in the zip, so the launch modes of `LauncherActivity` and `MainActivity` are unverified. The crash does not depend on them.
- I could not run the app, so the crash is confirmed from the code path, not from a log. The logcat line in section 2 will settle it.
