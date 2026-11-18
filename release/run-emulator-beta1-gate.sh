#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 || "$1" != /* ]]; then
    echo "usage: run-emulator-beta1-gate.sh /absolute/path/to/config.json" >&2
    exit 2
fi

script_dir=$(cd "$(dirname "$0")" && pwd -P)
exec node "$script_dir/../tools/emulator/run-report.ts" "$1"
