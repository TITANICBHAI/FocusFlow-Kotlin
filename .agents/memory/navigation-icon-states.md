---
name: Navigation icon states
description: The Focus and Defense tab icon visual rules for the Kotlin app.
---

Use the same custom timer geometry as the Ready-to-Focus artwork. In bottom navigation and the side drawer, show a single outer-rim outline in the muted nav tint while unselected; when selected, fill the timer and hand with the same BrandPrimary used by the other selected nav icons. Keep the larger Ready-to-Focus artwork's existing gradient. For Defense, preserve the approved outline while unselected; optically enlarge the filled selected shield within the same 22dp slot, with its checkmark as a transparent cutout.

**Why:** The user clarified that Focus navigation must match the Ready-to-Focus timer silhouette, must not look double-rimmed, and must use the same selected brand color as other nav icons rather than a different purple gradient.

**How to apply:** Pass selected state, inactive tint, and BrandPrimary selected color into the existing timer composable for bottom navigation and the drawer. Draw only the outer rim when unselected; keep both the filled timer and hand when selected. Leave the large Focus-screen gradient unchanged. Scale only the selected Defense artwork inside its existing slot. Do not change the other bottom-navigation icons or remove generic Material timer/shield icons still used elsewhere.