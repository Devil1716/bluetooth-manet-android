# Bluetooth MANET Demo

Android mesh messenger: nearby phones talk over a **BitChat-style BLE mesh** (no pairing) and can still use classic Bluetooth RFCOMM for the Windows node.

## What it does

- Advertises and scans at the same time (GATT peripheral + central).
- Links nearby MANET phones automatically over Bluetooth Low Energy.
- Relays messages and files with TTL 7, deduplication, and store-and-forward.
- ECDSA-signs chat and files so relays cannot tamper with payloads.
- Saves received files under the app's Downloads folder after SHA-256 verification.

## Project notes

- Package: `com.devil1716.bluetoothmanet`
- Language: Java + Kotlin
- Min SDK: 21
- Target / Compile SDK: 34
- Current release: `v1.3.3`
- Android Gradle Plugin: `8.5.2`

## How to run

1. Open the project in Android Studio.
2. Build and run on at least 2 Android phones.
3. On each phone:
   - Allow Bluetooth, notifications, and Location (many OEMs will not BLE-scan with Location off).
   - Tap `Enable Bluetooth` if needed.
   - Give each phone a different node ID (`A`, `B`, `C`).
   - Tap `Start Mesh`.
   - Wait for the event log to show a BLE link. Pairing is not required for phone-to-phone BLE.
4. Send a message or file using the other phone's node ID.

Manual `Connect Selected Peer` is only needed for classic RFCOMM / Windows. BLE peers appear on their own.

## Multi-hop test

- Phone A links to Phone B over BLE, Phone B links to Phone C.
- Send from A to `C`.
- B forwards automatically while TTL is greater than 1.

## Important limitations

- Keep the app in the foreground (or the mesh notification) so Android does not freeze BLE.
- Location must be on for BLE scanning on many devices.
- Phones usually need to be paired first only for classic RFCOMM / Windows.
- This is a demo mesh, not BitChat wire-compatible and not a production protocol.
- Received files land in the app-specific Downloads directory, not the system Downloads list.

## Windows laptop node

If you want the Windows laptop to join the same MANET, use the Python CLI in [windows-node](./windows-node). That path still uses RFCOMM.

## Architecture

BitChat's dual-role BLE mesh, flood-with-TTL routing, presence hellos, and store-and-forward are documented in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Publishing a GitHub release

If the app version is bumped but the GitHub "Releases" page still shows the old version, run the **Android Release** workflow manually:

1. Open **Actions → Android Release → Run workflow**.
2. Set `tag` to the version tag (example: `v1.3.0`).
3. Run the workflow. It builds the APK and publishes/updates the GitHub release for that tag.
