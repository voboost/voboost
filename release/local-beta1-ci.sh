#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
    echo "usage: local-beta1-ci.sh <workspace-root> <output-plan.tsv>" >&2
    exit 2
fi

workspace_root=$1
output=$2
definition="$(cd "$(dirname "$0")" && pwd)/1.0.0-beta1.toml"
temporary="${output}.tmp.$$"
active_scratch=
cleanup() {
    if [[ -n "$active_scratch" && -d "$active_scratch" ]]; then
        chmod -R u+w "$active_scratch" 2>/dev/null || true
        rm -rf -- "$active_scratch"
    fi
    rm -f -- "$temporary"
}
trap cleanup EXIT HUP INT TERM

commit() {
    sed -nE "s/^$1 = \"([0-9a-f]{40})\"$/\1/p" "$definition"
}

record_repository() {
    local name=$1
    local directory=$2
    local expected=$3
    local actual snapshot_tree status_digest git_directory scratch
    actual=$(git -C "$directory" rev-parse HEAD)
    [[ "$actual" == "$expected" ]] || {
        echo "$name HEAD $actual does not match definition $expected" >&2
        exit 1
    }
    status_digest=$(git -C "$directory" status --porcelain=v1 -z --untracked-files=all --ignore-submodules=none | shasum -a 256 | cut -d ' ' -f1)
    git_directory=$(git -C "$directory" rev-parse --absolute-git-dir)
    scratch=$(mktemp -d)
    active_scratch=$scratch
    mkdir "$scratch/objects"
    GIT_INDEX_FILE="$scratch/index" GIT_OBJECT_DIRECTORY="$scratch/objects" GIT_ALTERNATE_OBJECT_DIRECTORIES="$git_directory/objects" git -C "$directory" read-tree HEAD
    if ! GIT_INDEX_FILE="$scratch/index" GIT_OBJECT_DIRECTORY="$scratch/objects" GIT_ALTERNATE_OBJECT_DIRECTORIES="$git_directory/objects" git -c advice.addEmbeddedRepo=false -C "$directory" add -A -- . 2>"$scratch/add.stderr"; then
        sed -n '1,120p' "$scratch/add.stderr" >&2
        exit 1
    fi
    snapshot_tree=$(GIT_INDEX_FILE="$scratch/index" GIT_OBJECT_DIRECTORY="$scratch/objects" GIT_ALTERNATE_OBJECT_DIRECTORIES="$git_directory/objects" git -C "$directory" write-tree)
    chmod -R u+w "$scratch" 2>/dev/null || true
    rm -rf -- "$scratch"
    active_scratch=
    printf 'repository\t%s\t%s\t%s\t%s\t%s\n' "$name" "$directory" "$actual" "$snapshot_tree" "$status_digest" >>"$temporary"
}

record_artifact() {
    local component=$1
    local path=$2
    if [[ -f "$path" ]]; then
        local size digest
        size=$(stat -f %z "$path" 2>/dev/null || stat -c %s "$path")
        digest=$(shasum -a 256 "$path" | cut -d ' ' -f1)
        printf 'artifact\t%s\t%s\t%s\t%s\n' "$component" "$path" "$size" "$digest" >>"$temporary"
    else
        printf 'artifact-missing\t%s\t%s\t-\t-\n' "$component" "$path" >>"$temporary"
    fi
}

verify_wire_fixture_mirror() {
    local component=$1
    local mirror=$2
    local producer="$workspace_root/voboost/release/fixtures/wire"
    local expected_count=0 actual_count name digest
    local -a expected=(
        installed-healthy.json
        installed-unknown-no-version.json
        operation-install-complete.json
        SHA256SUMS
        startup-mode-hidden.json
        status-interface.json
        status-none.json
        uninstall-absence.json
    )

    [[ -d "$mirror" ]] || {
        echo "$component wire fixture mirror is missing: $mirror" >&2
        exit 1
    }
    actual_count=$(find "$mirror" -mindepth 1 -maxdepth 1 | wc -l | tr -d ' ')
    expected_count=${#expected[@]}
    [[ "$actual_count" == "$expected_count" ]] || {
        echo "$component wire fixture mirror has $actual_count entries; expected $expected_count" >&2
        exit 1
    }
    for name in "${expected[@]}"; do
        [[ -f "$producer/$name" && -f "$mirror/$name" ]] || {
            echo "$component wire fixture is missing: $name" >&2
            exit 1
        }
        cmp "$producer/$name" "$mirror/$name" || {
            echo "$component wire fixture differs from product authority: $name" >&2
            exit 1
        }
    done
    digest=$(shasum -a 256 "$producer/SHA256SUMS" | cut -d ' ' -f1)
    printf 'wire-corpus\t%s\t%s\t%s\n' "$component" "$mirror" "$digest" >>"$temporary"
}

printf 'schema\t1\nrelease\t1.0.0-beta1\nmode\tlocal-plan-no-publication\n' >"$temporary"
printf 'builder\tlocal-ephemeral\trelease/build-local-beta1.sh\n' >>"$temporary"
verify_wire_fixture_mirror inject "$workspace_root/voboost-inject/test/fixtures/wire"
verify_wire_fixture_mirror installer "$workspace_root/voboost-installer/src-tauri/tests/fixtures/wire"
record_repository product "$workspace_root/voboost" "$(git -C "$workspace_root/voboost" rev-parse HEAD)"
record_repository app "$workspace_root/voboost-app" "$(commit app)"
record_repository agents "$workspace_root/voboost-agents" "$(commit agents)"
record_repository inject "$workspace_root/voboost-inject" "$(commit inject)"
record_repository installer "$workspace_root/voboost-installer" "$(commit installer)"
record_repository logo "$workspace_root/voboost-logo" "$(commit logo)"
record_repository codestyle "$workspace_root/voboost-codestyle" "$(git -C "$workspace_root/voboost-codestyle" rev-parse HEAD)"

record_artifact app "$workspace_root/voboost-app/build/outputs/apk/release/voboost-app.apk"
record_artifact app "$workspace_root/voboost-app/build/outputs/apk/release/voboost-app-evidence.json"
record_artifact agents "$workspace_root/voboost-agents/build/agents-bundle/agents.json"
record_artifact agents "$workspace_root/voboost-agents/build/agents-bundle/agents.json.sig"
record_artifact agents "$workspace_root/voboost-agents/build/agents-bundle/agent-common.dex"
if [[ -d "$workspace_root/voboost-agents/build/agents-bundle" ]]; then
    while IFS= read -r -d '' module; do record_artifact agents "$module"; done < <(find "$workspace_root/voboost-agents/build/agents-bundle" -maxdepth 1 -type f -name 'agent-*.dex' ! -name 'agent-common.dex' -print0 | sort -z)
fi
record_artifact inject "$workspace_root/voboost-inject/target/aarch64-linux-android/release/voboost-daemon"
record_artifact inject "$workspace_root/voboost-inject/target/aarch64-linux-android/release/voboost-install"
record_artifact inject "$workspace_root/voboost-inject/target/aarch64-linux-android/release/voboost-restore"
record_artifact inject "$workspace_root/voboost-inject/target/aarch64-linux-android/release/voboost-uninstall"
record_artifact inject "$workspace_root/voboost-inject/dist/selinux/arm64-v8a/voboost-selinux"
record_artifact inject "$workspace_root/voboost-inject/config/voboost.rc"
record_artifact inject "$workspace_root/voboost-inject/dist/payload/arm64-v8a/libimplant.so"
record_artifact inject "$workspace_root/voboost-inject/dist/payload/armeabi-v7a/libimplant.so"
record_artifact installer "$workspace_root/voboost-installer/src-tauri/target/release/voboost-installer"
record_artifact product "$workspace_root/voboost/release-output/voboost.json"
record_artifact product "$workspace_root/voboost/release-output/voboost.json.sig"

printf '%s\n' \
    $'stage\t1\tlocal ephemeral beta1 build' \
    $'stage\t2\tlocal vehicle gate over exact ephemeral bytes' \
    $'stage\t3\tverifiable uninstall and clean-state receipt' \
    $'stage\t4\tplan repository commits against passed local bytes' \
    $'stage\t5\tcommit exact snapshot trees and push source only after local vehicle pass' \
    $'stage\t6\tbeta1-ci from exact pushed SHAs and local delivery evidence' \
    $'stage\t7\tdownload exact CI artifact IDs and verify hashes' \
    $'stage\t8\tclean CI vehicle gate plus uninstall and clean-state receipt' \
    $'stage\t9\tseparate publication decision without rebuild or resign' \
    $'blocked\tsource-push\trequires passed local build vehicle uninstall clean-state evidence and commits with the recorded trees' \
    $'blocked\tlocal-build\trequires successful build-local-beta1.sh evidence' \
    $'ready\truntime-readiness\tintegrated Android 9 gateway passed; local vehicle gate remains required' \
    $'blocked\ttag\tmanual protected publication gate' \
    $'blocked\tgithub-release\tmanual protected publication gate' \
    $'blocked\tchannel-update\tmanual protected publication gate' >>"$temporary"

mv "$temporary" "$output"
trap - EXIT HUP INT TERM
