# Mesh

Nearby phone chat over Bluetooth. No internet, no pairing, no account.

Android phones talk over a BitChat-style BLE mesh. A Windows laptop can still join over classic Bluetooth.

## What it does

- Finds phones around you automatically (no pairing for phone-to-phone chat).
- Sends messages and files while you stay in the app.
- Relays through nearby phones when a direct link is not enough.
- Signs chat and files so relays cannot tamper with payloads.

## Project notes

- Package: `com.devil1716.bluetoothmanet`
- Language: Java + Kotlin
- Launcher: `ComposeMeshActivity` (legacy XML console is `MainActivity`, hidden behind a long-press on the version in Settings → About)
- Min SDK: 21
- Target / Compile SDK: 34
- Current release: `v1.4.0`
- Android Gradle Plugin: `8.5.2`

## How to run

1. Open the project in Android Studio.
2. Build and run on at least 2 Android phones.
3. On each phone:
   - Allow Bluetooth when asked. Turn Location on if the banner says so.
   - Nearby chat starts on its own after permission. If not, tap **Start nearby chat**.
   - Share your ID, tap someone under **Online**, or use **New chat**.
4. Send a message. Laptop / already-paired devices live under **Settings → Advanced**.

## Multi-hop test

- Phone A links to Phone B, Phone B links to Phone C.
- Send from A to `C`.
- B forwards automatically.

## Important limitations

- Keep the app in the foreground (or the Mesh is on notification) so Android does not freeze Bluetooth.
- Location must be on for nearby scanning on many devices.
- Phones usually need to be paired first only for a Windows laptop.
- Received files land in the app-specific Downloads directory, not the system Downloads list.

## Updating Mesh

From 1.3.6 on, GitHub APKs are signed with a stable key and the app updates in place (WhatsApp-style banner: download, then Install).

If you still have 1.3.5 or an Android Studio debug build, Android will refuse the new APK as a **package conflict**. Copy your ID, uninstall Mesh, then install [the latest release](https://github.com/Devil1716/bluetooth-manet-android/releases/latest). After that, in-app updates replace the existing app.

## Windows laptop node

If you want the Windows laptop to join, use the Python CLI in [windows-node](./windows-node). That path still uses classic Bluetooth.

## Architecture

BLE mesh, flood routing, presence hellos, and store-and-forward are documented in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Publishing a GitHub release

If the app version is bumped but the GitHub "Releases" page still shows the old version, run the **Android Release** workflow manually:

1. Open **Actions → Android Release → Run workflow**.
2. Set `tag` to the version tag (example: `v1.3.7`).
3. Run the workflow. It builds the APK and publishes/updates the GitHub release for that tag.
