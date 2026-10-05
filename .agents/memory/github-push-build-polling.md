---
name: GitHub push and build polling
description: The user's rule for pushing project commits and monitoring GitHub Actions.
---

Do not push project changes or start, poll, or monitor GitHub Actions unless the user explicitly asks for that work. A code-change request alone is not permission to continue the push/build/watch loop.

**Why:** the user asked to defer GitHub pushing and build watching until explicitly requested.

**How to apply:** Complete code changes without automatically pushing or polling. If the user explicitly requests a push or build check, do only the requested action and stop when it is complete.