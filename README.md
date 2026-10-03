# FocusFlow

FocusFlow is a native Android focus and digital-wellbeing app. Plan focus sessions, manage tasks and reminders, configure app and network blocking, and review usage and progress in Stats.

The Android app is implemented in Kotlin with Jetpack Compose. Its application ID is `com.tbtechs.focusflow`.

## What’s in the app

- Focus sessions, task scheduling, and notifications
- Usage and progress reports, achievements, and daily ratings
- App-blocking controls, including Accessibility and VPN-based enforcement
- Profiles, settings, backup and restore, and a home-screen widget

Some blocking and background features require the corresponding Android permissions or system settings to be enabled by the user.

## Build the debug APK

**Requirements:** JDK 17 and Android SDK Platform 35 with Build Tools 35.0.0. Android Studio can install these through SDK Manager.

From the repository root:

```bash
./gradlew :app:assembleDebug
```

The APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The app supports Android 8.0 (API 26) and later. Install the debug APK on a connected device with:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Tests

Run local JVM tests with:

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
