# Release Signing

The combined AdventurePad APK must always use the original durable AdventurePad signing identity. Android requires the same application ID and signing certificate for an in-place update, so a build signed with a temporary or replacement key cannot update existing installations.

No signing key, password, machine-specific keystore path, or release-signing secret is stored in either source repository. The release identity must be created, stored, and backed up separately from the repositories and used consistently for all future AdventurePad preview and stable releases.

Losing or changing this identity prevents normal in-place updates and can break the private bridge between a mismatched AdventurePad/ScummVM pair.

## AdventurePad release identity

Beginning with `v0.2.0-preview`, the controlled AdventurePad release certificate has this SHA-256 fingerprint:

`3A:07:5D:9A:DA:42:82:FC:8F:A6:19:AA:E2:FA:44:FE:39:68:A5:DB:AF:DF:A5:AC:AC:34:B9:CE:3C:3A:AA:13`

The certificate fingerprint is public information. The private signing key and its credentials must never be committed or distributed.

AdventurePad reads local release-signing configuration from ignored `local.properties` or environment variables. Local configuration must provide the keystore location, key alias, keystore password, and key password expected by the Gradle release signing configuration.

GitHub Actions uses four repository secrets:

- `ADVENTUREPAD_RELEASE_KEYSTORE_BASE64`
- `ADVENTUREPAD_RELEASE_STORE_PASSWORD`
- `ADVENTUREPAD_RELEASE_KEY_ALIAS`
- `ADVENTUREPAD_RELEASE_KEY_PASSWORD`

Encode the existing keystore as a single-line Base64 value before adding the first secret. The keystore and passwords remain outside Git. The combined-release workflow fails if any secret is missing, writes the decoded keystore only to the runner's temporary directory with restrictive permissions, deletes it when the signing step ends, and verifies the finished APK against the public fingerprint above.

Never generate a fallback key in a release workflow. A fallback can produce a valid APK that cannot update the installed AdventurePad app.

## Combined release verification

The combined distribution has one installed package, `com.jamesmoran.adventurepad`, with ScummVM packaged inside it. Before distribution, run `tools/verify-release-apk.sh` against the exact APK to be uploaded. It rejects the wrong package or version, an unexpected signer, a debuggable build, non-AdventurePad application labels, or a missing arm64 ScummVM engine.

Never publish a keystore or its credentials, and never use an Android debug or temporary certificate for an AdventurePad release.

## v0.2.0-preview source pairing

The intended source pair is:

- AdventurePad `main` at the revision recorded by the `v0.2.0-preview` release/tag.
- ScummVM-AdventurePad at the revision recorded in `scummvm-engine.properties`.

Local Android packaging/signing configuration may contain ignored machine-specific release settings and must not contain signing secrets in Git.
