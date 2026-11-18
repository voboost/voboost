#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo "usage: sync-wire-fixtures.sh <workspace-root>" >&2
    exit 2
fi

workspace_root=$1
product=$(cd "$(dirname "$0")/.." && pwd -P)
authority="$product/release/fixtures/wire"
names=(
    installed-healthy.json
    installed-unknown-no-version.json
    operation-install-complete.json
    SHA256SUMS
    startup-mode-hidden.json
    status-interface.json
    status-none.json
    uninstall-absence.json
)
mirrors=(
    "$workspace_root/voboost-inject/test/fixtures/wire"
    "$workspace_root/voboost-installer/src-tauri/tests/fixtures/wire"
)

[[ "$workspace_root" == /* && -d "$authority" ]] || {
    echo "workspace root must be absolute and the product wire corpus must exist" >&2
    exit 2
}

for mirror in "${mirrors[@]}"; do
    if [[ -e "$mirror" || -L "$mirror" ]]; then
        [[ -d "$mirror" && ! -L "$mirror" ]] || {
            echo "wire mirror is not a regular directory: $mirror" >&2
            exit 2
        }
    else
        mkdir -p "$mirror"
    fi
    for name in "${names[@]}"; do
        cp "$authority/$name" "$mirror/$name"
    done
    count=$(find "$mirror" -mindepth 1 -maxdepth 1 | wc -l | tr -d ' ')
    [[ "$count" == "${#names[@]}" ]] || {
        echo "wire mirror contains an unexpected entry: $mirror" >&2
        exit 1
    }
    for name in "${names[@]}"; do
        cmp "$authority/$name" "$mirror/$name"
    done
done

echo "wire fixtures synchronized to inject and installer mirrors"
