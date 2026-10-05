#!/usr/bin/env bash
set -euo pipefail
task_root="$(cd "$(dirname "$0")/.." && pwd)"
simulator_sdk="${CIQ_SIMULATOR_SDK_HOME:-$task_root/.tooling/garmin-sdk-8.1.0}"
if [[ ! -x "$simulator_sdk/bin/monkeydo" ]]; then
    printf 'Set CIQ_SIMULATOR_SDK_HOME to the verified Connect IQ 8.1.0 SDK.\n' >&2
    exit 1
fi
cd "$task_root"
tools/build-watch.sh "${1:-}"
if [[ "${1:-}" == "--tests" ]]; then
    "$simulator_sdk/bin/monkeydo" watch/build/ShutterClick-tests.prg fr955 -t
else
    "$simulator_sdk/bin/monkeydo" watch/build/ShutterClick.prg fr955
fi
