#!/usr/bin/env bash
set -euo pipefail

[[ $# -eq 5 ]] || { echo "usage: prepare-package-inputs.sh <product-root> <final-closure> <role-fixtures> <new-roots-directory> <new-package-closure>" >&2; exit 2; }
product=$1
closure=$2
fixtures=$3
roots=$4
package_closure=$5
trust="$product/release/1.0.0-beta1-trust.json"
definition="$product/release/1.0.0-beta1.toml"
cargo fetch --manifest-path "$product/release/Cargo.toml" --locked
cli=(cargo run --manifest-path "$product/release/Cargo.toml" --locked --offline --)

[[ ! -e "$roots" && ! -L "$roots" && ! -e "$package_closure" && ! -L "$package_closure" ]] || { echo "package input output already exists" >&2; exit 2; }
"${cli[@]}" check-trust-roots "$trust"
test "$(jq -r .state "$trust")" = provisioned
mkdir "$roots"
for row in agentCatalogPublicKeyHex:agent productPublicKeyHex:product nativeExecutablePublicKeyHex:native restorePublicKeyHex:restore; do
    field=${row%%:*}; name=${row#*:}
    printf '%s' "$(jq -r ".roots.$field" "$trust")" | xxd -r -p > "$roots/$name.pub"
    "${cli[@]}" write-public-key-pem "$roots/$name.pub" "$roots/$name.pem"
done

"${cli[@]}" verify-file "$roots/product.pub" "$closure/voboost.json" "$closure/voboost.json.sig"
"${cli[@]}" verify-file "$roots/product.pub" "$closure/release-identity.json" "$closure/release-identity.json.sig"
"${cli[@]}" verify-file "$roots/agent.pub" "$closure/agents.json" "$closure/agents.json.sig"
"${cli[@]}" verify-restore "$definition" "$closure/voboost.json" "$closure" "$roots/restore.pub"
"${cli[@]}" verify-apk-product-root "$closure/voboost-app.apk" "$roots/product.pub"
for pair in daemon:voboost-daemon install:voboost-install restore:voboost-restore uninstall:voboost-uninstall selinux:voboost-selinux; do
    role=${pair%%:*}; file=${pair#*:}
    "${cli[@]}" verify-native-executable "$role" "$roots/native.pub" "$closure/$file"
    "${cli[@]}" verify-embedded-root "$roots/native.pub" "$closure/$file"
done
for file in voboost-daemon voboost-install; do
    "${cli[@]}" verify-embedded-root "$roots/agent.pub" "$closure/$file"
    "${cli[@]}" verify-embedded-root "$roots/product.pub" "$closure/$file"
done
for file in voboost-daemon voboost-install voboost-restore voboost-uninstall; do
    "${cli[@]}" verify-embedded-root "$roots/restore.pub" "$closure/$file"
done
mkdir "$package_closure"
cp "$closure/voboost.json" "$closure/voboost.json.sig" "$package_closure/"
jq -r '.artifacts[].file' "$closure/voboost.json" | while IFS= read -r file; do cp "$closure/$file" "$package_closure/"; done
"${cli[@]}" verify-product-closure "$package_closure" "$roots/product.pub"

temporary=$(mktemp -d "${TMPDIR:-/tmp}/voboost-package-fixtures.XXXXXX")
cleanup() { [[ ! -d "$temporary" ]] || rm -r "$temporary"; }
trap cleanup EXIT HUP INT TERM
printf '%s\n' 'voboost-role-trust-fixture-v1:release-catalog' > "$temporary/product.fixture"
printf '%s\n' 'voboost-role-trust-fixture-v1:restore' > "$temporary/restore.fixture"
printf '%s\n' 'voboost-role-trust-fixture-v1:native-executable' > "$temporary/native-executable.fixture"
cmp "$temporary/product.fixture" "$fixtures/product/product.fixture"
cmp "$temporary/restore.fixture" "$fixtures/product/restore.fixture"
cmp "$temporary/native-executable.fixture" "$fixtures/native/native-executable.fixture"
"${cli[@]}" verify-file "$roots/product.pub" "$fixtures/product/product.fixture" "$fixtures/product/product.fixture.sig"
"${cli[@]}" verify-file "$roots/restore.pub" "$fixtures/product/restore.fixture" "$fixtures/product/restore.fixture.sig"
"${cli[@]}" verify-file "$roots/native.pub" "$fixtures/native/native-executable.fixture" "$fixtures/native/native-executable.fixture.sig"
