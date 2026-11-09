#!/usr/bin/env bash
set -euo pipefail

apk=${1:?usage: verify-release-apk.sh APK}
expected_signer=3a075d9ada4282fc8fa619aae2fa44fe3968a5dbafdfa5acac34b9ce3c3aaa13
expected_package=com.jamesmoran.adventurepad
expected_version_name=0.2.0-preview
expected_version_code=2

for tool in apksigner aapt unzip; do
    command -v "$tool" >/dev/null || {
        echo "Required Android release-verification tool is missing: $tool" >&2
        exit 2
    }
done

test -s "$apk"

signing_report=$(apksigner verify --verbose --print-certs "$apk")
printf '%s\n' "$signing_report"
actual_signer=$(printf '%s\n' "$signing_report" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')
[[ "$actual_signer" == "$expected_signer" ]] || {
    echo "Unexpected signing certificate: ${actual_signer:-missing}" >&2
    exit 1
}

badging=$(aapt dump badging "$apk")
package_line=$(printf '%s\n' "$badging" | sed -n '1p')
[[ "$package_line" == *"name='$expected_package'"* ]]
[[ "$package_line" == *"versionCode='$expected_version_code'"* ]]
[[ "$package_line" == *"versionName='$expected_version_name'"* ]]

if [[ "$badging" == *$'\napplication-debuggable'* ]]; then
    echo "Release APK is debuggable" >&2
    exit 1
fi

label_failures=$(printf '%s\n' "$badging" \
    | sed -n "s/^application-label[^:]*:'\(.*\)'$/\1/p" \
    | grep -Fvx 'AdventurePad' || true)
[[ -z "$label_failures" ]] || {
    echo "Unexpected localized application label(s):" >&2
    printf '%s\n' "$label_failures" >&2
    exit 1
}

[[ "$badging" == *"launchable-activity-label:'AdventurePad'"* ]]
apk_listing=$(unzip -Z1 "$apk")
grep -Fx 'lib/arm64-v8a/libscummvm.so' <<< "$apk_listing" >/dev/null

echo "Verified package, version, signer, release mode, AdventurePad branding, and embedded arm64 ScummVM engine."
