---
name: GitHub push and build polling
description: The user's project rule for GitHub Actions, APK builds, and local Android tests.
---

Do not push project changes or start, poll, or monitor GitHub Actions unless the user explicitly asks. For this Android plan, do not run GitHub Actions or build an APK unless the user explicitly asks for APK building; local Android tests are allowed.

**Why:** the user asked to defer GitHub Actions and APK builds until explicitly requested, while allowing local Android tests.

**How to apply:** Complete code changes and local Android tests without automatically pushing, starting/polling Actions, or assembling APKs. If the user explicitly requests APK building, do only the requested action and stop when it is complete.