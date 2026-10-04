---
name: Android build environment
description: Environment limitation affecting Android Gradle verification in this workspace
---

The workspace may lack a usable Java command or `JAVA_HOME`; the Replit `java-graalvm22.3` module exposes GraalVM Java 19 on `PATH` without setting `JAVA_HOME`, not the project's required JDK 17. The checked-in Gradle wrapper downloads Gradle automatically. This workspace also lacks an Android SDK platform installation or `sdk.dir` configuration, so a Gradle APK build can stop before Kotlin source compilation even after Java is provisioned.

**Why:** Build attempts here have stopped first for missing Java and then for the missing Android SDK; Java and Gradle can be bootstrapped independently, but the Android platform remains a separate prerequisite.

**How to apply:** Check `java`, `JAVA_HOME`, `ANDROID_HOME`, `ANDROID_SDK_ROOT`, and `local.properties` before spending time on compiler diagnostics. Use a JDK 17 for this project; do not treat missing Java or an SDK as a source regression.