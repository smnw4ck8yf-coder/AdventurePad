# Release Signing Preparation

AdventurePad and ScummVM-AdventurePad communicate through signature-protected Android permissions. Every matching APK pair must therefore be signed by the **same durable, controlled release identity**.

No signing key, password, machine-specific keystore path, or release-signing secret is stored in either source repository. Creating and securely backing up that identity is a separate manual release step. Once established, use it consistently for all future AdventurePad preview and stable releases; losing or changing it prevents normal in-place updates and can break the private bridge between a mismatched pair.

## Migration from the first preview

The published `v0.1.0-preview` APKs used the former Android debug signer. Moving to the controlled release identity can require uninstalling both old apps before installing the newly signed pair. Before migration, users should back up relevant AdventurePad data, imported skins, ScummVM configuration, and save data. Uninstalling can remove app-private data and persisted folder permissions.

After signing is configured locally, verify the signing certificate on both APKs and confirm the certificate digests match before distribution. Never publish a keystore or its credentials, and never use the Android debug certificate as the release identity.

For `v0.2.0-preview`, the intended source pair is the final AdventurePad `main` revision recorded at release time and ScummVM-AdventurePad branch `adventurepad` at `fe5bea93fe4180489d83c4586f4aad1d086a388c`. This mapping is release documentation only; it does not change either package ID or the runtime bridge protocol.
