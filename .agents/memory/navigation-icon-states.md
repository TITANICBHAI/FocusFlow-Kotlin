---
name: Navigation icon states
description: The Focus and Defense tab icon visual rules for the Kotlin app.
---

Use the same approved custom timer geometry for both Focus states: outline-only in the standard muted navigation tint while unselected, gradient-filled with a visible hand while selected. Scale its artwork down inside the 22dp bottom-navigation slot and use the same state behavior in the side drawer. The larger Ready-to-Focus artwork remains purple and unchanged. For Defense, preserve the approved outline while unselected; optically enlarge the filled selected shield within the same 22dp slot, with its checkmark as a transparent cutout.

**Why:** The user compared the rendered navigation against screenshots: selected Focus should match the filled timer treatment, inactive Focus must stay muted rather than purple, and the selected Defense shield needs more visual weight without changing the approved inactive outline.

**How to apply:** Pass the selected state and inactive tint into the existing timer composable; use the filled hand for selected nav/drawer states and keep the large Focus-screen rendering unchanged. Scale only the selected Defense artwork inside its existing slot. Do not change the other bottom-navigation icons or remove generic Material timer/shield icons still used elsewhere.