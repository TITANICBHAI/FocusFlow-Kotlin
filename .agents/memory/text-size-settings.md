---
name: Text size settings
description: Durable product constraints for FocusFlow's text scaling feature.
---

The text-size feature must preserve Home/Schedule at exactly 100% while
allowing a General scale and nullable per-tab replacement scales for Focus,
Stats, Settings, and Defense. A null override inherits General; a non-null
override replaces it rather than stacking with it.

**Why:** Home/Schedule is being tuned independently, and scaling shared or root
content into it would create unintended visual changes.

**How to apply:** Keep Home pinned to 1f, wrap only tab content rather than the
bottom navigation scaffold, and treat the mechanical `.sp` rollout as a
strictly scoped job. Plain Android views such as the block overlay need direct
preference reads instead of Compose locals.

The feature has two distinct implementation shapes: interconnected model,
persistence, theme, navigation, settings UI, and overlay wiring; plus a
separate mechanical conversion of eligible Compose text-size literals. Existing
defaults must remain visually unchanged, and non-composable `.sp` usages require
manual review rather than blind replacement.

**Why:** Mixing the two jobs makes scope errors likely, especially around Home,
shared components, and top-level typography.

**How to apply:** Use the saved work handoff documents as the agent pre-read and
report all skipped files or source mismatches explicitly.