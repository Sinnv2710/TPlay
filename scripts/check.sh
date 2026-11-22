#!/usr/bin/env bash
set -o pipefail
for step in assembleDebug testDebugUnitTest lintDebug; do
  if ./gradlew "$step"; then echo "PASS: $step"; else echo "FAIL: $step"; exit 1; fi
done
