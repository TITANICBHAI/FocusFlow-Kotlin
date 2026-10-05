---
name: Always-on status notification workflow
description: Working context and progress-recording rules for the FocusFlow Android notification implementation.
---

For work on the always-on status notification, use `work/ALWAYS_ON_STATUS_NOTIFICATION_IMPLEMENTATION_PLAN.md` as the scope and behavior contract, `work/BATCH_TRACKER.md` as the authoritative progress record, and `work/AGENT_PRE_PROMPT.md` as the required onboarding instructions for a new agent. The plan's code observations must be re-verified against the current repository in Batch 0 before implementation. Owner recommendations are not approvals; unresolved decision gates must remain blocked.

The owner decided to keep the service running whenever existing background consent is granted, without idle/active checks or an in-app off switch in this iteration; an idle-only opt-out can be revisited later. Keep the scheduled-task card separate. The health state is useful but nonessential, and the allowance refactor is deferred until later.

**Why:** The user asked future agents to receive scoped context and strictly record progress, and chose always-on behavior to avoid bugs from app-side idle/active decisions.

**How to apply:** Before continuing this feature, read the three work-folder documents and this memory note. Follow the owner's recorded decisions over older plan suggestions. Keep progress and evidence in the tracker; use project memory only for durable constraints or lessons.
