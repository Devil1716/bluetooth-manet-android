# Signing and release

This document describes how MESH APKs are signed. It does not contain passwords, keystore bytes, or private keys.

## Current state (verified in source)

Through `v1.4.0`, GitHub Releases attached `app-debug.apk`, and `app/build.gradle` signed both debug and release with a keystore that was committed to the repository together with plaintext Gradle passwords.

**Treat that upload key as compromised.** Anyone with repository history can sign APKs that Android will accept as updates for existing `com.devil1716.bluetoothmanet` installs that already trust this certificate. Deleting the file from the current tree does **not** remove it from git history. This project does **not** rewrite history automatically.

## What changed in the build

- Debug builds use the Android debug keystore. They are for local development only.
- Release builds are not debuggable.
- Release signing loads from untracked `keystore.properties` (see `keystore.properties.example`) or environment variables:
  - `MESH_STORE_FILE`
  - `MESH_STORE_PASSWORD`
  - `MESH_KEY_ALIAS`
  - `MESH_KEY_PASSWORD`
- CI unit-tests every push/PR (`android-ci.yml`).
- Tag releases (`android-release.yml`) run tests and attach `app-release.apk` only when the secrets above are present. They no longer publish a debug APK as the product artifact.

## Local release build

1. Copy `keystore.properties.example` to `keystore.properties` (gitignored).
2. Point `storeFile` at a keystore that is **not** committed.
3. Fill passwords only on that machine, or export the `MESH_*` environment variables.
4. Run `./gradlew assembleRelease`.
5. Confirm the APK is under `app/build/outputs/apk/release/` and that `apksigner verify --print-certs` shows the intended certificate.

Do not paste passwords into issues, chat, or commit messages.

## GitHub Actions secrets

Configure repository secrets named `MESH_KEYSTORE_BASE64` (base64 of the `.jks`), `MESH_STORE_PASSWORD`, `MESH_KEY_ALIAS`, and `MESH_KEY_PASSWORD`. The workflow writes the keystore to a runner-local path that is not committed.

## Migration for installed users

Existing `v1.3.6`–`v1.4.0` installs already trust the exposed certificate. Options:

1. **Keep signing updates with the same key** (from a private secret store, not the repo) so in-app updates continue. Understand that a holder of the leaked key can also ship a malicious update. This is a damage-containment choice, not a security fix.
2. **Rotate to a new upload key** only as a deliberate product decision. Android will reject the APK as a package conflict. Users must uninstall and reinstall, which deletes local chat history unless they export it first. Communicate that clearly before shipping.
3. **Do not rewrite git history** unless owners separately authorize a `filter-repo` / force-push. Even then, forks, clones, and the old GitHub Release APKs still have the key.

`v1.3.5` and Android Studio debug installs already required uninstall to take a GitHub APK; that remains true for any certificate change.
