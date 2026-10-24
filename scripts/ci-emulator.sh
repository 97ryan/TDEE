#!/usr/bin/env bash
# Runs inside a booted emulator (see .github/workflows/build.yml):
#   1. the instrumented UI tests against the debug build
#   2. a smoke test of the exact release APK that would be published
# Results and screenshots are left in ci-out/ for the workflow to upload.
set -u
mkdir -p ci-out/shots
status=0

echo "::group::Instrumented UI tests (debug build)"
./gradlew --no-daemon installDebug installDebugAndroidTest || { echo "build for the emulator failed"; exit 1; }
adb logcat -c
adb shell am instrument -w com.ryan.tdee.debug.test/androidx.test.runner.AndroidJUnitRunner | tee ci-out/instrument.txt
adb pull /sdcard/Android/data/com.ryan.tdee.debug/files/shots/. ci-out/shots/ > /dev/null 2>&1 || true
adb logcat -d -b crash > ci-out/crash-instrumented.txt
echo "::endgroup::"
if ! grep -q "^OK (" ci-out/instrument.txt; then
  echo "INSTRUMENTED TESTS FAILED"
  status=1
fi

echo "::group::Release APK smoke test"
APK=$(ls dist/*.apk | head -n 1)
echo "installing $APK"
adb install -r "$APK" || exit 1
python3 scripts/ui_smoke.py com.ryan.tdee ci-out/shots | tee ci-out/smoke.txt
if ! grep -q "SMOKE PASSED" ci-out/smoke.txt; then
  echo "RELEASE SMOKE TEST FAILED"
  status=1
fi
echo "::endgroup::"

exit $status
