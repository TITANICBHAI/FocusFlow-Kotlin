#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
cd "$ROOT_DIR"

echo "== FocusFlow supplied-file import test and isolated UI-test compile =="
export FOCUSFLOW_SKIP_APK_BUILD=1
exec bash "$ROOT_DIR/scripts/build-apk-with-java.sh" \
  -PrunFocusFlowFileFixture=true \
  :app:testProductionDebugUnitTest \
  --tests=com.tbtechs.focusflow.data.backupfixture.FocusFlowFileImportFixtureTest \
  :app:compileProductionDebugAndroidTestKotlin \
  "$@"
