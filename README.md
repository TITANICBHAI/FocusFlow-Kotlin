# FocusFlow

FocusFlow is a native Android focus and digital-wellbeing app. Plan focus sessions, manage tasks and reminders, configure app and network blocking, and review usage and progress in Stats.

The Android app is implemented in Kotlin with Jetpack Compose. Its application ID is `com.tbtechs.focusflow`.

## What’s in the app

- Focus sessions, task scheduling, and notifications
- Usage and progress reports, achievements, and daily ratings
- App-blocking controls, including Accessibility and VPN-based enforcement
- Profiles, settings, backup and restore, and a home-screen widget

Some blocking and background features require the corresponding Android permissions or system settings to be enabled by the user.

## `.focusflow` backup files

FocusFlow backups are UTF-8 JSON files using the V1 envelope. The exported file
is named with the `.focusflow` extension and starts with the format marker
`"kind": "FocusFlowBackupV1"`. The `settings` object contains portable settings;
the `tasks` array contains task records and their reminders. Exported files
also include metadata such as an export timestamp and a task-count summary.
The importer requires `settings` to be an object and `tasks` to be an array.
Include `"version": 1` in hand-written files; the current importer also treats
an omitted version as V1.

Here is a small example with one scheduled task:

```json
{
  "kind": "FocusFlowBackupV1",
  "version": 1,
  "exportedAt": "2026-10-04T10:00:00.000Z",
  "settings": {},
  "tasks": [
    {
      "id": "weekly-review",
      "title": "Weekly review",
      "description": "",
      "startTime": "2026-10-05T09:00:00.000Z",
      "endTime": "2026-10-05T09:50:00.000Z",
      "durationMinutes": 50,
      "status": "scheduled",
      "priority": "medium",
      "tags": ["planning"],
      "reminders": [],
      "color": "#6366f1",
      "focusMode": false,
      "focusAllowedPackages": [],
      "createdAt": "2026-10-04T10:00:00.000Z",
      "updatedAt": "2026-10-04T10:00:00.000Z"
    }
  ]
}
```

Task timestamps use ISO-8601 date-times with an explicit offset (`Z` for UTC is
a good choice). Each task needs a unique `id`, a title, start/end and
created/updated timestamps, a `durationMinutes` integer, a supported `status`
(`scheduled`, `active`, `completed`, `skipped`, or `overdue`), and a supported
`priority` (`low`, `medium`, `high`, or `critical`). Tags, reminders, color,
`focusMode`, and `focusAllowedPackages` are also represented in task records.

Import shows a review before applying changes. Matching portable settings are
updated while settings omitted from the file stay local. Tasks can be merged
with the existing list or replaced; **Replace removes existing task rows
first**. Keep a copy of your current backup and review the import warnings
before confirming.

For a fuller field guide and an offline JSON editor/checker, see
[`docs/focusflow-backup-builder.html`](docs/focusflow-backup-builder.html).
The app's import preview and validation remain authoritative, especially for
settings and device-specific protection.

## Build the debug APK

### Build with automatic JDK and Android SDK setup

On Linux, run this from the repository root:

```bash
bash scripts/build-apk-with-java.sh
```

The script uses an existing JDK 17 when available; otherwise, it downloads and caches JDK 17 from Adoptium. It also downloads Google's Android SDK command-line tools when needed, accepts the SDK package licenses, installs Platform 35, Build Tools 35.0.0, and Platform Tools with Android CLI metrics disabled, then runs the checked-in Gradle wrapper. Gradle downloads the version configured for this project automatically.

The SDK is cached outside the Git repository at `$HOME/.cache/focusflow/android-sdk` by default. You can select another external location with `FOCUSFLOW_ANDROID_SDK_ROOT`, `ANDROID_SDK_ROOT`, or `ANDROID_HOME`; the script refuses SDK paths inside the repository. The root `.gitignore` also excludes common accidental in-repository SDK locations and `local.properties`. SDK files are not added to GitHub by the build or GitHub sync scripts.

To run the Android compilation, JVM tests, instrumentation-test APK assembly, and connected instrumentation tests in one invocation:

```bash
bash scripts/build-apk-with-java.sh \
  :app:compileDebugKotlin \
  :app:testDebugUnitTest \
  :app:assembleDebugAndroidTest \
  :app:connectedDebugAndroidTest
```

Connected instrumentation tests still require a running emulator or connected Android device.

### Build directly with Gradle

```bash
./gradlew :app:assembleDebug
```

The APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The app supports Android 10 (API 29) and later. Install the debug APK on a connected device with:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Tests

Run local JVM tests with the repository's toolchain bootstrap:

```bash
bash scripts/test-unit.sh
```

This runs `:app:testDebugUnitTest` without assembling an APK. If JDK 17 and the
Android SDK are already configured, the Gradle task can also be run directly:

```bash
./gradlew :app:testDebugUnitTest
```

Instrumented tests require a running emulator or connected Android device:

```bash
./gradlew :app:connectedDebugAndroidTest
```

## Build an APK with GitHub Actions

The **Build Native Kotlin APK** workflow is in `.github/workflows/build-native-kotlin-apk.yml`. It can be started manually from the repository’s **Actions** tab using **Run workflow**. The workflow installs Java 17 and Android SDK 35, assembles the debug APK, and uploads the `focusflow-native-debug-apk` artifact for 14 days.

This workflow creates a **debug** APK; it is not a signed release or publishing pipeline. No Gemini API key or Node.js setup is needed to build the Android app.

## Project layout

- `app/src/main/java/com/tbtechs/focusflow/ui/` — Compose screens and navigation
- `app/src/main/java/com/tbtechs/focusflow/data/` — persistence, repositories, backup, and restore
- `app/src/main/java/com/tbtechs/focusflow/analytics/` — usage insights and progress summaries
- `app/src/main/java/com/tbtechs/focusflow/enforcement/` — focus enforcement, services, and receivers
- `app/src/main/java/com/tbtechs/focusflow/notifications/` — reminders and notification scheduling
