#!/usr/bin/env bash
set -euo pipefail
task_root="$(cd "$(dirname "$0")/.." && pwd)"
ciq_sdk="${CIQ_SDK_HOME:-$task_root/.tooling/garmin-sdk}"
ciq_key="${CIQ_DEVELOPER_KEY:-$task_root/.tooling/signing/developer.der}"
if [[ ! -x "$ciq_sdk/bin/monkeyc" ]]; then
    printf 'Set CIQ_SDK_HOME to your Connect IQ SDK directory.\n' >&2
    exit 1
fi
if [[ ! -f "$ciq_key" ]]; then
    printf 'Set CIQ_DEVELOPER_KEY to your private DER signing key; see docs/DEVELOPMENT.md.\n' >&2
    exit 1
fi
cd "$task_root"
mkdir -p watch/build
if [[ "${1:-}" == "--store" ]]; then
    # Connect IQ Store package: release build for every product in the manifest.
    "$ciq_sdk/bin/monkeyc" -f watch/monkey.jungle -o watch/build/ShutterClick.iq -y "$ciq_key" -l 1 -w -e -r
elif [[ "${1:-}" == "--tests" ]]; then
    "$ciq_sdk/bin/monkeyc" -f watch/tests.jungle -d fr955 -o watch/build/ShutterClick-tests.prg -y "$ciq_key" -l 1 -w -t
else
    "$ciq_sdk/bin/monkeyc" -f watch/monkey.jungle -d fr955 -o watch/build/ShutterClick.prg -y "$ciq_key" -l 1 -w
fi
