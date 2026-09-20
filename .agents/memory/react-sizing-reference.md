---
name: React sizing reference
description: The dimensional baseline for keeping the Kotlin Compose UI compact and consistent with the React product.
---

Use the React implementation's design tokens and component measurements as the source of truth for Kotlin Compose sizing. Prefer compact values around 11/13/15/18/22sp typography, 6/10/16dp radii, and 4/8/12/16/24/32dp spacing unless interaction requirements justify more.

**Why:** Screenshot-based estimates led to oversized Kotlin headers, FABs, task cards, switches, navigation items, and task-editor controls. The React source provides the actual product scale.

**How to apply:** Before changing a shared or reference-driven UI component, compare its dimensions with the React baseline. Preserve business behavior and content while shrinking presentation geometry toward the reference values.