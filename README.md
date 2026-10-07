# FocusFlow

FocusFlow is a native Android focus and digital-wellbeing app. Plan focus sessions, manage tasks and reminders, configure app and network blocking, and review usage and progress in Stats.

The Android app is implemented in Kotlin with Jetpack Compose. It has two Gradle product flavors:

- `production` — application ID `com.tbtechs.focusflow`
- `tbtechsdev` — application ID `com.tbtechsdev.focusflow`

## What’s in the app

- Focus sessions, task scheduling, and notifications
- Usage and progress reports, achievements, and daily ratings
- App-blocking controls, including Accessibility and VPN-based enforcement
- Profiles, settings, and a home-screen widget

Some blocking and background features require the corresponding Android permissions or system settings to be enabled by the user.

## Build flavor debug APKs

### Build with automatic JDK and Android SDK setup

On Linux, run this from the repository root:

```bash
bash scripts/build-apk-with-java.sh
```

By default, this builds the `production` flavor. To also build `tbtechsdev` in the same invocation, pass its Gradle task:

```bash
bash scripts/build-apk-with-java.sh :app:assembleTbtechsdevDebug
```

The script uses an existing JDK 17 when available; otherwise, it downloads and caches JDK 17 from Adoptium. It also downloads Google's Android SDK command-line tools when needed, accepts the SDK package licenses, installs Platform 35, Build Tools 35.0.0, and Platform Tools with Android CLI metrics disabled, then runs the checked-in Gradle wrapper. Gradle downloads the version configured for this project automatically.

The SDK is cached outside the Git repository at `$HOME/.cache/focusflow/android-sdk` by default. You can select another external location with `FOCUSFLOW_ANDROID_SDK_ROOT`, `ANDROID_SDK_ROOT`, or `ANDROID_HOME`; the script refuses SDK paths inside the repository. The root `.gitignore` also excludes common accidental in-repository SDK locations and `local.properties`. SDK files are not added to GitHub by the build or GitHub sync scripts.

To run the Android compilation, JVM tests, instrumentation-test APK assembly, and connected instrumentation tests in one invocation:

```bash
bash scripts/build-apk-with-java.sh \
  :app:compileProductionDebugKotlin \
  :app:testProductionDebugUnitTest \
  :app:assembleProductionDebugAndroidTest \
  :app:connectedProductionDebugAndroidTest
```

Connected instrumentation tests still require a running emulator or connected Android device.

### Build directly with Gradle

```bash
./gradlew :app:assembleProductionDebug
```

The production APK is written to:

```text
app/build/outputs/apk/production/debug/app-production-debug.apk
```

Build the `tbtechsdev` APK directly with:

```bash
./gradlew :app:assembleTbtechsdevDebug
```

It is written to `app/build/outputs/apk/tbtechsdev/debug/app-tbtechsdev-debug.apk`. The app supports Android 10 (API 29) and later. Install the production debug APK on a connected device with:

```bash
adb install -r app/build/outputs/apk/production/debug/app-production-debug.apk
```

## Tests

Run local JVM tests with the repository's toolchain bootstrap:

```bash
bash scripts/test-unit.sh
```

This runs both flavor unit-test tasks without assembling an APK. If JDK 17 and the
Android SDK are already configured, the Gradle task can also be run directly:

```bash
./gradlew :app:testProductionDebugUnitTest :app:testTbtechsdevDebugUnitTest
```

Instrumented tests require a running emulator or connected Android device:

```bash
./gradlew :app:connectedProductionDebugAndroidTest
```

## Build an APK with GitHub Actions

The **Build Native Kotlin APK** workflow is in `.github/workflows/build-native-kotlin-apk.yml`. It remains manual: start it from the repository’s **Actions** tab using **Run workflow**. It tests and builds the `production` flavor (`com.tbtechs.focusflow`) and uploads the `focusflow-native-debug-apk` artifact for 14 days.

The separate **Build tbtechsdev APK** workflow is in `.github/workflows/build-tbtechsdev-apk.yml`. It runs on each push, tests and builds the `tbtechsdev` flavor (`com.tbtechsdev.focusflow`), and uploads the `focusflow-tbtechsdev-debug-apk` artifact for 14 days.

This workflow creates a **debug** APK; it is not a signed release or publishing pipeline. No Gemini API key or Node.js setup is needed to build the Android app.

## Project layout

- `app/src/main/java/com/tbtechs/focusflow/ui/` — Compose screens and navigation
- `app/src/main/java/com/tbtechs/focusflow/data/` — persistence and repositories
- `app/src/main/java/com/tbtechs/focusflow/analytics/` — usage insights and progress summaries
- `app/src/main/java/com/tbtechs/focusflow/enforcement/` — focus enforcement, services, and receivers
- `app/src/main/java/com/tbtechs/focusflow/notifications/` — reminders and notification scheduling
