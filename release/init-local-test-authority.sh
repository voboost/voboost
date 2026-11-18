#!/usr/bin/env bash
set -euo pipefail
umask 077

if [[ $# -ne 1 ]]; then
    echo "usage: init-local-test-authority.sh <new-authority-directory>" >&2
    exit 2
fi

script_dir=$(cd "$(dirname "$0")" && pwd -P)
product=$(cd "$script_dir/.." && pwd -P)
workspace_root=$(cd "$product/.." && pwd -P)
source "$script_dir/local-build-paths.sh"
authority=$(canonical_new_durable_path "$workspace_root" "$1" authority)
release_cli=(cargo run --manifest-path "$product/release/Cargo.toml" --locked --offline --)

for command in cargo openssl keytool; do command -v "$command" >/dev/null || { echo "missing host command: $command" >&2; exit 2; }; done

mkdir -m 700 "$authority"
mkdir "$authority/fixtures"
for role in agent-catalog product native-executable restore; do
    openssl rand -out "$authority/$role.key" 32
    chmod 600 "$authority/$role.key"
    "${release_cli[@]}" derive-public-key "$authority/$role.key" "$authority/$role.pub"
    "${release_cli[@]}" write-public-key-pem "$authority/$role.pub" "$authority/$role.pem"
done

for role in release-catalog restore native-executable; do
    printf '%s\n' "voboost-role-trust-fixture-v1:$role" > "$authority/fixtures/$role"
done
"${release_cli[@]}" sign-file "$authority/product.key" "$authority/fixtures/release-catalog" "$authority/fixtures/release-catalog.sig"
"${release_cli[@]}" sign-file "$authority/restore.key" "$authority/fixtures/restore" "$authority/fixtures/restore.sig"
"${release_cli[@]}" sign-file "$authority/native-executable.key" "$authority/fixtures/native-executable" "$authority/fixtures/native-executable.sig"

keytool -genkeypair -keystore "$authority/app.jks" -storepass changeit -keypass changeit -alias voboost -keyalg RSA -keysize 2048 -validity 365 -dname 'CN=Voboost local test,O=Voboost,C=RU' -noprompt >/dev/null
chmod 600 "$authority/app.jks"
"${release_cli[@]}" verify-file "$authority/product.pub" "$authority/fixtures/release-catalog" "$authority/fixtures/release-catalog.sig"
"${release_cli[@]}" verify-file "$authority/restore.pub" "$authority/fixtures/restore" "$authority/fixtures/restore.sig"
"${release_cli[@]}" verify-file "$authority/native-executable.pub" "$authority/fixtures/native-executable" "$authority/fixtures/native-executable.sig"
printf '%s\n' 'schema=1' > "$authority/authority-identity.txt"
validate_local_test_authority "$workspace_root" "$authority" >/dev/null
echo "local test signing authority initialized at $authority; private material is retained until explicitly removed"
