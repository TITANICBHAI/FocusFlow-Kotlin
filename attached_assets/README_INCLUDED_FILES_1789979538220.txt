FocusFlow — Stats / Observed Device Time files

Included files are the files identified as directly involved in the Observed Device Time / Stats calculation path, plus the FocusFlow focus-time source and the separate allowance/blocking usage system.

Direct observed-device-time / stats path:
- android-native/app/src/main/java/com/tbtechs/focusflow/modules/UsageStatsModule.kt
- src/native-modules/UsageStatsModule.ts
- src/components/UsageInsights.tsx
- app/(tabs)/stats.tsx
- app/reports.tsx

Focus-time source used by Stats:
- src/data/database.ts
- src/services/focusService.ts

Separate allowance/blocking usage system:
- android-native/app/src/main/java/com/tbtechs/focusflow/services/AppBlockerAccessibilityService.kt
