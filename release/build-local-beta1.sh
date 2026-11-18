#!/usr/bin/env bash
set -euo pipefail
umask 077

if [[ $# -ne 3 ]]; then
    echo "usage: build-local-beta1.sh <workspace-root> <retained-authority-directory> <new-output-directory>" >&2
    exit 2
fi

workspace_root=$1
authority=$2
output=$3
product="$workspace_root/voboost"
app="$workspace_root/voboost-app"
agents="$workspace_root/voboost-agents"
inject="$workspace_root/voboost-inject"
installer="$workspace_root/voboost-installer"
logo="$workspace_root/voboost-logo"
definition="$product/release/version.toml"
release_cli=(cargo run --manifest-path "$product/release/Cargo.toml" --locked --offline --)
source "$product/release/local-build-paths.sh"
output=$(canonical_new_durable_path "$workspace_root" "$output" output)
work=$(canonical_new_durable_path "$workspace_root" "$output.work" work)
keys=$(validate_local_test_authority "$workspace_root" "$authority")

[[ -d "$product" && -d "$app" && -d "$agents" && -d "$inject" && -d "$installer" && -d "$logo" ]] || {
    echo "workspace is missing a beta1 repository" >&2
    exit 2
}
for command in cargo openssl keytool jq shasum; do command -v "$command" >/dev/null || { echo "missing host command: $command" >&2; exit 2; }; done
[[ -n "${ANDROID_SDK_ROOT:-}" ]] || { echo "ANDROID_SDK_ROOT is required" >&2; exit 2; }
ANDROID_SDK_ROOT=$(cd "$ANDROID_SDK_ROOT" && pwd -P)
export ANDROID_SDK_ROOT
export ANDROID_HOME="$ANDROID_SDK_ROOT"
[[ -n "${ANDROID_NDK_HOME:-}" ]] || { echo "ANDROID_NDK_HOME is required" >&2; exit 2; }
android_tools="$ANDROID_SDK_ROOT/build-tools/34.0.0"
[[ -x "$android_tools/apksigner" && -x "$android_tools/aapt2" ]] || { echo "Android build-tools 34.0.0 are required" >&2; exit 2; }

mkdir -m 700 "$work"
mkdir "$work/closure" "$work/package"

# Freeze the product-owned definition before any component build.
node "$product/tools/release/installer-version.ts" "$definition" "$work/inputs" > "$work/installer-version.json"
product_version=$(jq -er .productVersion "$work/installer-version.json")
definition="$work/inputs/$product_version.toml"

"$product/release/local-beta1-ci.sh" "$workspace_root" "$work/snapshot-before.tsv"
node "$product/tools/release/local-source-admission.ts" capture "$workspace_root" "$work/sources-before.json"
"${release_cli[@]}" check-definition "$definition"
ANDROID_SDK_ROOT="$ANDROID_SDK_ROOT" cargo test --manifest-path "$product/release/Cargo.toml" --locked --offline --features android-integration verify::tests::pinned_android_tools_extract_identity_from_exact_signed_apk -- --exact

android_certificate=$(keytool -exportcert -keystore "$keys/app.jks" -storepass changeit -alias voboost | shasum -a 256 | cut -d ' ' -f1)

missing=0
for adapter in "$app/scripts/prepare-beta1-unsigned.sh" "$inject/tools/prepare-beta1-unsigned.sh" "$installer/scripts/build-macos-universal-app.sh"; do
    if [[ ! -x "$adapter" ]]; then
        echo "local beta1 blocked: missing reviewed adapter $adapter" >&2
        missing=1
    fi
done
[[ "$missing" == 0 ]] || exit 1

VOBOOST_EMBEDDED_PRODUCT_PUBLIC_KEY_PEM="$keys/product.pem" \
    "$app/scripts/prepare-beta1-unsigned.sh" "$work/voboost-app-unsigned.apk"
[[ -f "$work/voboost-app-unsigned.apk" ]] || { echo "app adapter did not produce its exact output" >&2; exit 1; }
"$android_tools/apksigner" sign --ks "$keys/app.jks" --ks-pass pass:changeit --key-pass pass:changeit --v4-signing-enabled false --out "$work/closure/voboost-app.apk" "$work/voboost-app-unsigned.apk"

(cd "$agents" && ./gradlew buildRelease --no-daemon)
cp "$agents/build/agents-bundle/agents.json" "$agents/build/agents-bundle/agent-common.dex" "$agents"/build/agents-bundle/agent-*.dex "$work/closure/"
"${release_cli[@]}" sign-file "$keys/agent-catalog.key" "$work/closure/agents.json" "$work/closure/agents.json.sig"

VOBOOST_AGENT_CATALOG_PUBLIC_KEY_PEM="$keys/agent-catalog.pem" \
VOBOOST_RELEASE_CATALOG_PUBLIC_KEY_PEM="$keys/product.pem" \
VOBOOST_NATIVE_EXECUTABLE_PUBLIC_KEY_PEM="$keys/native-executable.pem" \
VOBOOST_RESTORE_PUBLIC_KEY_PEM="$keys/restore.pem" \
VOBOOST_ANDROID_CERT_SHA256="$android_certificate" \
    make -C "$inject" build-android \
        AGENT_CATALOG_PUBLIC_KEY_PEM="$keys/agent-catalog.pem" \
        RELEASE_CATALOG_PUBLIC_KEY_PEM="$keys/product.pem" \
        NATIVE_EXECUTABLE_PUBLIC_KEY_PEM="$keys/native-executable.pem" \
        RESTORE_PUBLIC_KEY_PEM="$keys/restore.pem" \
        ANDROID_CERT_SHA256="$android_certificate" \
        NDK_HOME="${ANDROID_NDK_HOME:-$ANDROID_SDK_ROOT/ndk/29.0.14206865}"
VOBOOST_AGENT_CATALOG_PUBLIC_KEY_PEM="$keys/agent-catalog.pem" \
VOBOOST_RELEASE_CATALOG_PUBLIC_KEY_PEM="$keys/product.pem" \
VOBOOST_NATIVE_EXECUTABLE_PUBLIC_KEY_PEM="$keys/native-executable.pem" \
VOBOOST_RESTORE_PUBLIC_KEY_PEM="$keys/restore.pem" \
VOBOOST_ANDROID_CERT_SHA256="$android_certificate" \
    "$inject/tools/prepare-beta1-unsigned.sh" "$work/native-unsigned"

for file in voboost.rc libimplant-arm64-v8a.so libimplant-armeabi-v7a.so; do
    cp "$work/native-unsigned/$file" "$work/closure/$file"
done
for pair in daemon:voboost-daemon install:voboost-install restore:voboost-restore uninstall:voboost-uninstall selinux:voboost-selinux; do
    role=${pair%%:*}; file=${pair#*:}
    "${release_cli[@]}" sign-native-executable "$role" "$keys/native-executable.key" "$work/native-unsigned/$file" "$work/closure/$file"
done

"${release_cli[@]}" inspect-apk "$definition" "$work/closure" "$ANDROID_SDK_ROOT" "$work/apk-evidence.json"
test "$(jq -r .certificateSha256 "$work/apk-evidence.json")" = "$android_certificate"
"${release_cli[@]}" verify-apk-product-root "$work/closure/voboost-app.apk" "$keys/product.pub"
"${release_cli[@]}" produce-restore "$definition" "$work/closure" "$ANDROID_SDK_ROOT" "$keys/restore.key"
"${release_cli[@]}" compose "$definition" "$work/closure" "$ANDROID_SDK_ROOT" "$work/apk-evidence.json" "$keys/restore.pub" "$work/voboost.json"
"${release_cli[@]}" sign-file "$keys/product.key" "$work/voboost.json" "$work/voboost.json.sig"
cp "$work/voboost.json" "$work/voboost.json.sig" "$work/closure/"
"${release_cli[@]}" verify-file "$keys/product.pub" "$work/closure/voboost.json" "$work/closure/voboost.json.sig"
"${release_cli[@]}" verify-file "$keys/agent-catalog.pub" "$work/closure/agents.json" "$work/closure/agents.json.sig"
"${release_cli[@]}" verify-restore "$definition" "$work/closure/voboost.json" "$work/closure" "$keys/restore.pub"
for pair in daemon:voboost-daemon install:voboost-install restore:voboost-restore uninstall:voboost-uninstall selinux:voboost-selinux; do
    role=${pair%%:*}; file=${pair#*:}
    "${release_cli[@]}" verify-native-executable "$role" "$keys/native-executable.pub" "$work/closure/$file"
    "${release_cli[@]}" verify-embedded-root "$keys/native-executable.pub" "$work/closure/$file"
done
for file in voboost-daemon voboost-install; do
    "${release_cli[@]}" verify-embedded-root "$keys/agent-catalog.pub" "$work/closure/$file"
    "${release_cli[@]}" verify-embedded-root "$keys/product.pub" "$work/closure/$file"
done
for file in voboost-daemon voboost-install voboost-restore voboost-uninstall; do
    "${release_cli[@]}" verify-embedded-root "$keys/restore.pub" "$work/closure/$file"
done
"${release_cli[@]}" verify-product-closure "$work/closure" "$keys/product.pub"
mkdir "$work/reopened-closure"
cp -R "$work/closure/." "$work/reopened-closure/"
mkdir "$work/no-packages"
node "$product/tools/beta1-evidence.ts" inventory "$work/closure" "$work/no-packages" "$work/closure-inventory.json"
node "$product/tools/beta1-evidence.ts" inventory "$work/reopened-closure" "$work/no-packages" "$work/reopened-closure-inventory.json"
cmp "$work/closure-inventory.json" "$work/reopened-closure-inventory.json"
"${release_cli[@]}" verify-product-closure "$work/reopened-closure" "$keys/product.pub"
"${release_cli[@]}" verify-apk-product-root "$work/reopened-closure/voboost-app.apk" "$keys/product.pub"

(
    cd "$installer"
    VOBOOST_PRODUCT_VERSION="$product_version" \
    VOBOOST_PRODUCT_MANIFEST_PUBLIC_KEY_PEM="$keys/product.pem" \
    VOBOOST_RESTORE_PUBLIC_KEY_PEM="$keys/restore.pem" \
    VOBOOST_NATIVE_EXECUTABLE_PUBLIC_KEY_PEM="$keys/native-executable.pem" \
    VOBOOST_PRODUCT_MANIFEST_ROLE_FIXTURE_SIGNATURE="$keys/fixtures/release-catalog.sig" \
    VOBOOST_RESTORE_ROLE_FIXTURE_SIGNATURE="$keys/fixtures/restore.sig" \
    VOBOOST_NATIVE_EXECUTABLE_ROLE_FIXTURE_SIGNATURE="$keys/fixtures/native-executable.sig" \
        scripts/build-macos-universal-app.sh "$work/universal-app/Voboost Installer.app"
)

node "$installer/src/build/offline-package.ts" build-mac-dmg \
    "$work/universal-app/Voboost Installer.app" "$work/closure" \
    "$keys/product.pem" "$keys/fixtures/release-catalog.sig" \
    "$keys/fixtures/restore.sig" "$keys/fixtures/native-executable.sig" \
    "$work/package/voboost-installer-1.0.0-beta1.dmg"
hdiutil verify "$work/package/voboost-installer-1.0.0-beta1.dmg" >/dev/null

"$product/release/local-beta1-ci.sh" "$workspace_root" "$work/snapshot-after.tsv"
node "$product/tools/release/local-source-admission.ts" capture "$workspace_root" "$work/sources-after.json"
cmp "$work/sources-before.json" "$work/sources-after.json"
diff <(sed -n '/^repository\t/p' "$work/snapshot-before.tsv") <(sed -n '/^repository\t/p' "$work/snapshot-after.tsv")
node "$product/tools/beta1-evidence.ts" inventory "$work/closure" "$work/package" "$work/artifact-set.json"
printf '{}\n' > "$work/public-identities.json"
for role in agent-catalog product native-executable restore; do
    raw=$(xxd -p -c 64 "$keys/$role.pub")
    digest=$(shasum -a 256 "$keys/$role.pub" | cut -d ' ' -f1)
    jq -c --arg role "$role" --arg raw "$raw" --arg digest "$digest" '. + {($role):{rawPublicKeyHex:$raw,rawPublicKeySha256:$digest}}' "$work/public-identities.json" > "$work/public-identities.next"
    mv "$work/public-identities.next" "$work/public-identities.json"
done
snapshot_sha=$(shasum -a 256 "$work/snapshot-before.tsv" | cut -d ' ' -f1)
artifact_set_sha=$(shasum -a 256 "$work/artifact-set.json" | cut -d ' ' -f1)
closure_inventory_sha=$(shasum -a 256 "$work/closure-inventory.json" | cut -d ' ' -f1)
dmg="$work/package/voboost-installer-1.0.0-beta1.dmg"
jq -cn --arg releaseIdentity 1.0.0-beta1 --arg androidCertificateSha256 "$android_certificate" --arg snapshotSha256 "$snapshot_sha" --arg sourceInventorySha256 "$(shasum -a 256 "$work/sources-before.json" | cut -d ' ' -f1)" --arg artifactSetSha256 "$artifact_set_sha" --arg closureInventorySha256 "$closure_inventory_sha" --arg manifestSha256 "$(shasum -a 256 "$work/closure/voboost.json" | cut -d ' ' -f1)" --arg manifestSignatureSha256 "$(shasum -a 256 "$work/closure/voboost.json.sig" | cut -d ' ' -f1)" --arg packageSha256 "$(shasum -a 256 "$dmg" | cut -d ' ' -f1)" --argjson packageSize "$(stat -f %z "$dmg")" --slurpfile publicIdentities "$work/public-identities.json" --slurpfile artifactSet "$work/artifact-set.json" --rawfile snapshot "$work/snapshot-before.tsv" '{schemaVersion:1,releaseIdentity:$releaseIdentity,authority:"persistent-local-test-only",androidCertificateSha256:$androidCertificateSha256,publicIdentities:$publicIdentities[0],evidenceAuthority:"not-created-before-vehicle-gate",sourceSnapshot:{inventorySha256:$sourceInventorySha256,sha256:$snapshotSha256,finalVerified:true,repositories:($snapshot|split("\n")|map(select(startswith("repository\t"))|split("\t")|{name:.[1],head:.[3],tree:.[4],statusSha256:.[5]}))},artifactSet:$artifactSet[0],artifactSetSha256:$artifactSetSha256,closure:{inventorySha256:$closureInventorySha256,manifestSha256:$manifestSha256,manifestSignatureSha256:$manifestSignatureSha256},package:{file:"voboost-installer-1.0.0-beta1.dmg",size:$packageSize,sha256:$packageSha256,reopened:true},privateKeysPersisted:true,privateKeysLocation:"explicit-retained-authority",runtimeReadiness:"ready-for-local-vehicle-gate",vehicle:"not-run",publication:"forbidden"}' > "$work/local-build-evidence.json"
mkdir "$output"
cp -R "$work/closure" "$output/closure"
cp "$dmg" "$output/"
cp "$work/snapshot-before.tsv" "$output/source-snapshot.tsv"
cp "$work/sources-before.json" "$output/source-inventory.json"
cp "$work/local-build-evidence.json" "$output/"
mkdir "$output/public-roots"
cp "$keys"/*.pub "$keys"/*.pem "$output/public-roots/"
"$product/release/local-beta1-ci.sh" "$workspace_root" "$work/snapshot-final.tsv"
node "$product/tools/release/local-source-admission.ts" capture "$workspace_root" "$work/sources-final.json"
cmp "$work/sources-before.json" "$work/sources-final.json"
diff <(sed -n '/^repository\t/p' "$work/snapshot-before.tsv") <(sed -n '/^repository\t/p' "$work/snapshot-final.tsv")
printf '%s\n' 'schema=1' "evidenceSha256=$(shasum -a 256 "$output/local-build-evidence.json" | cut -d ' ' -f1)" > "$output/output-identity.txt"
echo "local beta1 bytes built at $output; retained work is at $work and the supplied test authority remains at $keys"
