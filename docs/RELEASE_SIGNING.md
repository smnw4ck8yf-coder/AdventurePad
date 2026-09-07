# Release Signing

AdventurePad and ScummVM-AdventurePad communicate through signature-protected Android permissions. Every matching APK pair must therefore be signed by the **same durable, controlled release identity**.

No signing key, password, machine-specific keystore path, or release-signing secret is stored in either source repository. The release identity must be created, stored, and backed up separately from the repositories and used consistently for all future AdventurePad preview and stable releases.

Losing or changing this identity prevents normal in-place updates and can break the private bridge between a mismatched AdventurePad/ScummVM pair.

## AdventurePad release identity

Beginning with `v0.2.0-preview`, the controlled AdventurePad release certificate has this SHA-256 fingerprint:

`3A:07:5D:9A:DA:42:82:FC:8F:A6:19:AA:E2:FA:44:FE:39:68:A5:DB:AF:DF:A5:AC:AC:34:B9:CE:3C:3A:AA:13`

The certificate fingerprint is public information. The private signing key and its credentials must never be committed or distributed.

AdventurePad reads release-signing configuration from the developer's ignored `local.properties`. Local signing configuration must provide the keystore location, key alias, keystore password, and key password expected by the Gradle release signing configuration.

## ScummVM-AdventurePad pairing

The current AdventurePad runtime explicitly targets the custom ScummVM package:

`org.scummvm.scummvm.debug`

For `v0.2.0-preview`, the matching ScummVM-AdventurePad APK therefore retains that package ID and is signed with the same controlled AdventurePad release certificate.

This is intentional: the standard ScummVM release package `org.scummvm.scummvm` is not a drop-in replacement for the current AdventurePad bridge.

Before distribution, verify both APKs independently with Android `apksigner` and confirm that their SHA-256 signer digests are identical.

## Migration from the first preview

The published `v0.1.0-preview` APKs used the former Android debug signer. Moving to the controlled release identity can therefore require uninstalling both old apps before installing the newly signed pair.

Before migration, users should back up relevant AdventurePad data, imported skins, ScummVM configuration, and save data. Uninstalling can remove app-private data and persisted folder permissions.

Never publish a keystore or its credentials, and never use the Android debug certificate as the release identity for future AdventurePad releases.

## v0.2.0-preview source pairing

The intended source pair is:

- AdventurePad `main` at the revision recorded by the `v0.2.0-preview` release/tag.
- ScummVM-AdventurePad branch `adventurepad` at the revision recorded by the `v0.2.0-preview` release/tag.

The recorded ScummVM revision identifies the corresponding custom source and must include the package-preserving, non-debuggable `adventurepadRelease` variant. Local Android packaging/signing configuration may contain ignored machine-specific release settings and must not contain signing secrets in Git.
