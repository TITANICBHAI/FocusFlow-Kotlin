---
name: Android build environment
description: Environment limitation affecting Android Gradle verification in this workspace
---

The workspace may lack a usable Java command or `JAVA_HOME`; it can also expose a JDK while lacking an Android SDK platform installation or `sdk.dir` configuration. Gradle may therefore stop before Kotlin source compilation for either JVM or SDK setup.

**Why:** A failed Gradle invocation can be an environment problem before any source is compiled, so distinguish JVM setup failures from SDK provisioning failures.

**How to apply:** Check `java`, `JAVA_HOME`, `ANDROID_HOME`, `ANDROID_SDK_ROOT`, and `local.properties` before spending time on compiler diagnostics. Do not treat missing Java or an SDK as a source regression.