#!/usr/bin/env bash

canonical_new_durable_path() {
    local workspace_root=$1
    local requested=$2
    local label=${3:-path}
    local ancestor suffix part repository temporary_root

    [[ "$requested" == /* ]] || { echo "$label must be an absolute path" >&2; return 2; }
    [[ "$requested" != *'/./'* && "$requested" != *'/../'* && "$requested" != */. && "$requested" != */.. ]] || {
        echo "$label must not contain dot segments" >&2
        return 2
    }
    [[ ! -e "$requested" && ! -L "$requested" ]] || { echo "$label must not exist: $requested" >&2; return 2; }
    suffix=$(basename "$requested")
    [[ "$suffix" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$ ]] || { echo "$label basename is unsafe" >&2; return 2; }
    ancestor=$(dirname "$requested")
    while [[ ! -e "$ancestor" && ! -L "$ancestor" ]]; do
        part=$(basename "$ancestor")
        [[ "$part" != . && "$part" != .. ]] || { echo "$label ancestor is unsafe" >&2; return 2; }
        suffix="$part/$suffix"
        ancestor=$(dirname "$ancestor")
    done
    [[ -d "$ancestor" ]] || { echo "nearest $label ancestor is not a directory" >&2; return 2; }
    ancestor=$(cd "$ancestor" && pwd -P)
    requested="$ancestor/$suffix"

    for repository in voboost voboost-app voboost-agents voboost-inject voboost-installer voboost-license voboost-logo; do
        [[ -d "$workspace_root/$repository" ]] || continue
        repository=$(cd "$workspace_root/$repository" && pwd -P)
        [[ "$requested/" != "$repository/"* ]] || { echo "$label must remain outside source repository: $repository" >&2; return 2; }
    done
    for temporary_root in /tmp /var/tmp "${TMPDIR:-/tmp}"; do
        temporary_root=$(cd "$temporary_root" && pwd -P)
        [[ "$requested/" != "$temporary_root/"* ]] || { echo "$label must remain outside temporary trees: $temporary_root" >&2; return 2; }
    done
    [[ "$requested/" != */build/* && "$requested/" != */target/* && "$requested/" != */.gradle/* && "$requested/" != */node_modules/* && "$requested/" != */.cache/* && "$requested/" != */Caches/* ]] || {
        echo "$label must remain outside build/cache trees" >&2
        return 2
    }
    printf '%s\n' "$requested"
}

validate_local_test_authority() {
    local workspace_root=$1
    local authority=$2
    local canonical mode name suffix

    [[ "$authority" == /* ]] || { echo "authority must be an absolute path" >&2; return 2; }
    [[ -d "$authority" && ! -L "$authority" ]] || { echo "authority must be a regular directory" >&2; return 2; }
    canonical=$(cd "$authority" && pwd -P)
    [[ "$canonical" == "$authority" ]] || { echo "authority must use its canonical path" >&2; return 2; }
    mode=$(stat -f %Lp "$authority")
    [[ "$mode" == 700 ]] || { echo "authority directory must have mode 0700" >&2; return 2; }
    canonical_new_durable_path "$workspace_root" "$authority.validation-probe" authority >/dev/null
    for name in agent-catalog product native-executable restore; do
        for suffix in key pub pem; do
            [[ -f "$authority/$name.$suffix" && ! -L "$authority/$name.$suffix" ]] || { echo "missing local test authority input: $name.$suffix" >&2; return 2; }
        done
        [[ "$(stat -f %Lp "$authority/$name.key")" == 600 ]] || { echo "private authority file must have mode 0600: $name.key" >&2; return 2; }
    done
    [[ -f "$authority/app.jks" && ! -L "$authority/app.jks" && "$(stat -f %Lp "$authority/app.jks")" == 600 ]] || {
        echo "local test Android keystore must be a regular mode-0600 file" >&2
        return 2
    }
    [[ -f "$authority/authority-identity.txt" && ! -L "$authority/authority-identity.txt" ]] || {
        echo "local test authority is incomplete" >&2
        return 2
    }
    [[ "$(<"$authority/authority-identity.txt")" == schema=1 ]] || { echo "local test authority identity is invalid" >&2; return 2; }
    printf '%s\n' "$canonical"
}
