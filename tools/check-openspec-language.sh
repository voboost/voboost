#!/usr/bin/env bash

set -euo pipefail

repo_dir="$(cd "$(dirname "$0")/.." && pwd)"
cd "$repo_dir"

violations="$(rg -n '[А-Яа-яЁё]' openspec || true)"

if [ -n "$violations" ]; then
    printf '%s\n' "OpenSpec must be English-only:" >&2
    printf '%s\n' "$violations" >&2
    exit 1
fi
