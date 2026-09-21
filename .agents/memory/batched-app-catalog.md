---
name: Batched installed-app catalog
description: The shared installed-app loader publishes metadata in batches while feature screens retain independent selection and configuration state.
---

The installed-app catalog is shared discovery data only. VPN, Always-On, Daily Allowance, and Allowed During Focus must continue owning their own selected packages, drafts, presets, and saved configuration.

**Why:** Batching reduces repeated list copying and Compose recompositions without coupling unrelated feature behavior or persistence.

**How to apply:** Change the catalog loader or batch size independently from feature selection logic; keep feature-specific filtering and mutations at the screen boundary.