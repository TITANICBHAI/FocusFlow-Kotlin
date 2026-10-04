# Tab Screen Customization and Data-Wiring Plan

**Status:** Batch 06 source work is implemented; Android verification is blocked
by the missing Java/Android SDK toolchain. The existing expandable text-size
hierarchy and Schedule override are already implemented and tracked in Batch 07;
do not rebuild them. Batch 08 records the installed-app catalog work in
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

## Checked-out source audit — 2026-10-05

### Tab surfaces and state ownership

| Tab | Current routes and important nested surfaces | State/data owner | Commit and back/dismiss behavior |
|---|---|---|---|
| Schedule | `HomeScreen`; Quick Add, task details, edit-task, and Allowed During Focus picker | `TaskViewModel` owns tasks; settings preferences use `SettingsViewModel` / `SettingsRepository`. | Task drafts commit through task actions; Allowed During Focus commits through its Settings owner. Dialog close/back callbacks dismiss nested surfaces. |
| Focus | `FocusScreen`; active session, Extend and debrief surfaces, Standalone Block setup/modal, permission route | `FocusSessionViewModel` owns the live session; `SettingsViewModel` / `SettingsRepository` own Focus and Standalone Block preferences. | Session actions go through the session view model. Standalone Block selections remain a local draft until save; close/back dismisses the modal. |
| Stats | `StatsScreen`; Reports and Report detail routes; archived stats and rating surfaces | `StatsViewModel` and local analytics/usage repositories own displayed data and rating updates. | Report/detail routes return through navigation back; rating or feedback actions use their owning callbacks. |
| Settings | `SettingsScreen`; Text Size, How to Use, Profile, Changelog, legal pages, Import confirmation, permissions, and overlay-appearance surfaces | `SettingsViewModel` / `SettingsRepository` own preferences; `TaskViewModel` owns task clearing; backup screens delegate to the backup coordinator. | Preference controls use their existing view-model/repository updates; profile and import flows use their explicit save/confirm actions; child destinations use navigation back. |
| Defense | `DefenseScreen`; Always-On, scheduled-block editor, daily allowances, Standalone Block, keyword blocker, VPN block list, launcher setup, Nuclear Mode, Active Blocks, and permissions | `SettingsViewModel` / `SettingsRepository` own persisted protection settings; `VpnRepository` owns VPN state. Feature selections remain local to their owning settings/model. | Schedules and app lists commit through their feature save actions; VPN uses its repository flow; permission and launcher destinations return through navigation; Nuclear Mode uses Android's uninstall flow. |

### Existing text-size implementation — reuse, do not duplicate

- `TextSizeSettingsScreen` already hosts the General slider and expandable
  Schedule, Focus, Stats, Settings, and Defense groups. Each group starts
  collapsed; its tab slider appears after expanding the row. The Schedule tab
  slider is present, and its nullable `homeTextScale` inherits General when
  unset.
- The same screen already exposes child-screen text-size destinations and
  focused editors. Schedule currently has Quick Add, Task details, and Edit
  task; the task-list route uses the Schedule tab override. `screenTextScales`
  is persisted and keyed by owner/source route so shared destinations inherit
  the caller's tab context.
- No additional per-screen display option was found in this audit with both a
  persisted setting and a real rendering effect. Do not add placeholder
  appearance controls or another slider hierarchy.
- Focus's dismissible Defense notice and the Ready to Focus layout, plus removal
  of the Defense information cards, are already recorded as Batch 07 work.

### Existing app catalog and adoption gaps

- `InstalledAppsRepository` is the shared metadata source for package name,
  display label, icon, and IME status. Its process cache is time-limited; its
  batched API emits 24-app batches by default. `rememberInstalledApps` is the
  existing Compose adapter. Reuse these rather than adding a second catalog.
- The batched adapter is already used by the Allowed During Focus picker,
  Always-On, VPN Block List, and Daily Allowance surfaces. These screens keep
  selections and saved configuration in their feature-owned state.
- Before Batch 08, Standalone Block, scheduled-block overview/editor, Nuclear
  Mode, Active Blocks, and Launcher Setup called the shared repository directly.
  They now use `rememberInstalledApps`; Launcher Setup and Nuclear Mode
  invalidate and reload the shared cache after an Android lifecycle pause/resume.
- The Allowed During Focus picker, Always-On, and VPN Block List already showed
  explicit missing/unavailable app entries. Active Blocks previously used a
  package suffix, and scheduled summaries/editor could use suffixes or raw package
  IDs. Batch 08 replaces those fallbacks with app labels/icons or explicit
  missing/unavailable states without changing saved package selections.
- The navigation graph also resolves one-off package labels directly through
  `PackageManager`; this is a single-package lookup, not an independent catalog
  enumeration.

### Batch 08 implementation record — 2026-10-05

- `ActiveScreen`, `StandaloneBlockModal`, `GreyoutScheduleModal`, Nuclear Mode,
  and Launcher Setup now consume the shared progressive loader. Launcher Setup
  and Nuclear Mode retain refresh-on-return behavior; the cache refresh is keyed
  by lifecycle state rather than by duplicating a catalog.
- Active Blocks and scheduled-block cards/editor resolve saved package IDs to
  catalog names and icons. Missing packages show “App not installed”; catalog
  failures show “App details unavailable”; both retain the full package ID as a
  secondary identifier. Loading is represented explicitly.
- `InstalledAppsLoaderTest` covers installed, loading, missing, and catalog
  failure resolution. Source review confirms that selections and persistence
  remain feature-owned.
- `git diff --check` and Kotlin LSP diagnostics passed for the changed sources
  and test. Android unit tests could not run: `./gradlew :app:testDebugUnitTest`
  stops before Gradle starts because no Java executable or `JAVA_HOME` exists;
  Android SDK variables and `local.properties` are also absent.

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