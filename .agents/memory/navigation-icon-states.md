---
name: Navigation icon states
description: The Focus and Defense tab icon visual rules for the Kotlin app.
---

Use the standard outlined timer for Focus while unselected; show the supplied branded filled timer only while selected, scaled to match the other navigation icons. In the side drawer, use the same selected/unselected behavior. For Defense, use the supplied shield outline while unselected and the filled shield while selected; the checkmark is a transparent negative-space cutout. Keep generic timer/shield icons elsewhere and the larger Focus-screen artwork unchanged.

**Why:** The user specified that the Focus timer should not appear filled or emphasized before selection, then fill when selected, and that its visible size should match other navigation icons. The supplied Defense vectors define outlined and filled selected states.

**How to apply:** Preserve these states in the bottom navigation and side drawer. Reuse the existing timer artwork rather than duplicating it, and do not remove generic Material timer/shield icons that are still used in other screens.