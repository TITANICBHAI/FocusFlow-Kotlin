---
name: Navigation icon states
description: The Focus and Defense tab icon visual rules for the Kotlin app.
---

Use the same custom timer geometry as the Ready-to-Focus artwork. In bottom navigation and the side drawer, show a single outer-rim outline in the muted nav tint while unselected; when selected, fill the timer with the same BrandPrimary used by the other selected nav icons and cut the hand out so the nav background shows through. Keep the larger Ready-to-Focus artwork's existing gradient and filled hand. For Defense, preserve the outline while unselected and optically enlarge the selected filled shield to about 32dp in both navigation areas, keeping its checkmark as a transparent cutout.

**Why:** The user clarified that Focus navigation must match the Ready-to-Focus timer silhouette, must not look double-rimmed, must use the same selected brand color as other nav icons, and must keep the clock hand visible against the filled active state. The user also found the selected Defense shield too small and hard to read compared with the reference.

**How to apply:** Pass selected state, inactive tint, BrandPrimary selected color, and the hand-cutout treatment into the existing timer composable for bottom navigation and the drawer. Draw only the outer rim when unselected. Leave the large Focus-screen gradient and filled hand unchanged. Scale the selected Defense artwork consistently in the bottom bar and drawer while leaving the outline state at its normal size. Do not change the other bottom-navigation icons or remove generic Material timer/shield icons still used elsewhere.