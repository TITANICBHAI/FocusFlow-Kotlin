---
name: Navigation icon states
description: The Focus and Defense tab icon visual rules for the Kotlin app.
---

Use the same approved custom timer geometry for both Focus states: outline-only with no glow while unselected, gradient-filled while selected. Scale its artwork down inside the 22dp bottom-navigation slot; use the same state behavior in the side drawer. The selected timer hand is a true transparent cutout, not a painted background color. For Defense, use the supplied shield outline while unselected and filled shield while selected, with the checkmark as a transparent cutout. Keep generic timer/shield icons elsewhere and preserve the larger Focus-screen artwork.

**Why:** The user specified that selection changes the approved timer's fill, not its icon family or glow, and that its optical size match the other navigation icons. A painted cutout color can be visibly wrong when the surface changes with the theme.

**How to apply:** Pass the selected state into the existing timer composable and apply optical scaling only in navigation; keep the larger Focus-screen rendering unchanged. Clear the selected-state hand on an isolated layer so the actual surface shows through. Do not change the other bottom-navigation icons or remove generic Material timer/shield icons still used elsewhere.