---
name: GitHub Actions APK build environment
description: Where this Android project can reliably build and how the local Replit shell differs.
---

GitHub Actions is the reliable APK build environment for this project because its workflow provisions Java 17 and the Android SDK; the local Replit shell may not have Java installed.

**Why:** The GitHub build passed after provisioning Java 17, while the local shell could not start Gradle because `JAVA_HOME` and `java` were unavailable.

**How to apply:** Use the GitHub Actions APK workflow for build verification, and use the console GitHub sync workflow to push fixes before rerunning it.