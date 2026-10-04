# Tab Screen Customization and Data-Wiring Plan

**Status:** Batch 06 implementation is in progress. The separately requested
Schedule/Focus/Defense UI fixes are tracked as Batch 07 in
`PROJECT_WORK_TRACKER.md`.

## Goal

Make each tab's screens feel intentionally customizable and ensure the app data
shown by pickers and summaries is connected to the real installed-app catalog.
Keep shared discovery and presentation infrastructure separate from each
feature's own saved selection and behavior.

## Text-size and screen-customization hierarchy

Do not show every tab slider and every screen option as one long, flat list.
The Text Size entry should open a hierarchical screen:

1. **General** — one default-size control at the top.
2. **Tab groups** — Schedule, Focus, Stats, Settings, and Defense appear as
   collapsed rows with a down chevron. Tapping the row expands it; the chevron
   changes to an up chevron. Use clear labels and an accessible hit target.
3. **Tab screens** — an expanded tab lists the screens and relevant secondary
   screens owned by that tab. For example, Schedule can list the task list,
   quick-add, task details, and edit-task surfaces.
4. **Screen-specific controls** — selecting a listed screen opens its focused
   customization view. Put that screen's text-size override and any supported
   display options there, rather than exposing all screen controls at once.

Keep the General fallback and every tab/screen override independent. Resetting a
tab or screen override must restore inheritance without changing another
screen's saved choices. The current user request supersedes the earlier
Home/Schedule 100% pin: Schedule must have a working override like the other
tabs, initially inheriting General so the default remains visually unchanged.

Before adding any additional controls such as icon style, card layout, or
display density, inventory what each screen already supports and only expose
options that have a real, persisted effect. Do not add cosmetic controls that
are disconnected from rendering.

## Installed-app names, icons, and picker data

- Audit every app picker and app summary reachable from the five tabs,
  including Standalone Block, group schedules, Always-On/VPN, Focus allowed
  apps, and launcher flows.
- Resolve package IDs through one shared installed-app catalog that supplies
  the real application label and icon. Keep discovery batched/cached so each
  screen does not independently enumerate and copy the whole installed-app
  list.
- Keep each feature's selected packages, drafts, presets, and saved
  configuration local to that feature. Sharing labels/icons must not couple
  Standalone Block selection to schedule, VPN, launcher, or Focus selections.
- Make missing/uninstalled-package behavior explicit and consistent; do not
  silently display package IDs as if they were user-facing app names.

## Screen audit sequence

1. Inventory each root tab and its nested routes, dialogs, sheets, and shared
   components. Record the entry point, source of displayed data, state owner,
   save action, and dismissal/back behavior.
2. Trace each app picker from package selection through rendering and
   persistence. Check that labels/icons remain correct in cards, summaries,
   and active-block views.
3. Identify which screen-specific display settings are useful and can be
   persisted independently; group them under the owning tab and screen.
4. Implement shared metadata loading once, then update the relevant pickers and
   summaries without merging feature-specific state.
5. Review each tab at default, configured, empty, loading, error, permission
   granted/denied, and dismissed-banner states. Check that content remains
   aligned when banners appear or disappear.
6. Add source-level and UI tests for selection isolation, displayed app
   metadata, persistence, hierarchy expansion/navigation, and responsive
   states.

## Acceptance criteria

- No unexplained flat list of every tab's sliders or screen options.
- Each tab can be expanded and its nested screen controls reached with clear
  back navigation.
- A screen's customization changes only that screen unless it explicitly
  inherits General or its tab setting.
- Installed apps display their actual names and icons wherever they are chosen
  or summarized.
- App selection and configuration remain independent across features.
- Tab layouts remain balanced after permission notices or dismissible guidance
  disappear.
- The Settings and screen-specific controls are tested for persistence and
  reset behavior; Android build limitations are recorded rather than presented
  as passing verification.

## Scope boundary

This document records the broader audit and implementation plan; it does not
authorize a blanket redesign or replacing existing app-selection logic.
Implement the audit in staged batches after the targeted Batch 07 fixes, and
update this plan with findings before expanding customization options.