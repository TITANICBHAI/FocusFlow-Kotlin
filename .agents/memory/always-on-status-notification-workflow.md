---
name: Always-on status notification workflow
description: Working context and progress-recording rules for the FocusFlow Android notification implementation.
---

Keep the service running whenever existing background consent is granted; do not add app-side idle/active gating or an in-app off switch in this iteration. An idle-only opt-out requires a later decision. Keep the scheduled-task card separate. The health state is useful but nonessential, and the allowance refactor is deferred.

**Why:** The user chose always-on behavior to avoid bugs caused by app-side idle/active decisions.

**How to apply:** Preserve these scope decisions when changing the foreground service or its notifications. Re-check current source and tests rather than relying on old implementation-plan or tracker references.
