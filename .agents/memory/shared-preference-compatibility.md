---
name: Shared preference compatibility
description: Compatibility rule for Android SharedPreferences values that outlive app code changes.
---

Read metadata preferences by checking the stored value type instead of assuming every historical value matches the current accessor.

**Why:** Android SharedPreferences throws `ClassCastException` when a key written as a Boolean is later read with `getString`; existing users can carry those values across APK updates.

**How to apply:** For non-enforcement metadata, treat an unexpected stored type as absent or migrate it explicitly before using a typed accessor. Avoid changing enforcement preference semantics without a deliberate migration.