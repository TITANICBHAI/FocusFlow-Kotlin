---
name: Android build environment
description: Environment limitation affecting Android Gradle verification in this workspace
---

The workspace can expose a usable JDK through `/nix/store` while still lacking an Android SDK platform installation or `sdk.dir` configuration. In that state, Gradle starts normally but fails during Android plugin dependency setup before Kotlin source compilation.

**Why:** A successful Gradle launch is not evidence that Android source compilation ran; distinguish JVM setup failures from SDK provisioning failures.

**How to apply:** Check for `ANDROID_HOME`, `ANDROID_SDK_ROOT`, or `local.properties` SDK configuration before spending time on compiler diagnostics. Do not treat the absence of an SDK as a source regression.