---
name: Navigation icon states
description: The Focus and Defense tab icon visual rules for the Kotlin app.
---

Use the same custom timer geometry as the Ready-to-Focus artwork. In bottom navigation and the side drawer, show a single outer-rim outline in the muted nav tint while unselected; when selected, fill the timer with the same BrandPrimary used by the other selected nav icons and cut the hand out so the nav background shows through. Keep the larger Ready-to-Focus artwork's existing gradient and filled hand. For Defense, preserve the existing outline while unselected; use the supplied filled shield-checkmark vector at its normal 22dp size only when the Defense tab is selected.

**Why:** The user clarified that Focus navigation must match the Ready-to-Focus timer silhouette, must not look double-rimmed, must use the same selected brand color as other nav icons, and must keep the clock hand visible against the filled active state. The user supplied a replacement shield vector because the previous selected Defense shield rendering looked poor.

**How to apply:** Pass selected state, inactive tint, BrandPrimary selected color, and the hand-cutout treatment into the existing timer composable for bottom navigation and the drawer. Draw only the outer rim when unselected. Leave the large Focus-screen gradient and filled hand unchanged. Use the supplied filled Defense shield only for the selected Defense state in the bottom bar and drawer; leave the other tabs and generic Material timer/shield icons unchanged.