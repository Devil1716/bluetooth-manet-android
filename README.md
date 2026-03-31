# Bluetooth MANET Demo

Android Studio project for a simple Bluetooth-based MANET proof of concept in Java.

## What it does

- Accepts incoming classic Bluetooth RFCOMM connections.
- Discovers nearby devices and lets you tap to connect.
- Sends messages in the format `ID|SRC|DEST|TTL|DATA`.
- Delivers messages locally when `DEST` matches this phone's node ID.
- Relays unseen messages to connected peers while decrementing TTL.

## Project notes

- Package: `com.devil1716.bluetoothmanet`
- Language: Java
- Min SDK: 21
- Target / Compile SDK: 34
- Current release: `v1.0.0`
- Android Gradle Plugin: `8.5.2`

## How to run

1. Open the project in Android Studio.
2. Let Android Studio install the missing Android SDK / JDK if prompted.
3. Build and run on at least 2 Android phones.
4. On each phone:
   - Tap `Enable Bluetooth`
   - Tap `Make Discoverable`
   - Give the phone a simple node ID like `A`, `B`, or `C`
   - Tap `Start Listening`
   - Tap `Discover Nearby Peers`
   - Tap a device in the list to connect
5. Send a message from one node to another using the destination node ID.

## Multi-hop test

- Phone A connects to Phone B
- Phone B connects to Phone C
- Send from A to `C`
- Phone B should forward automatically if TTL is still greater than 1

## Important limitations

- Android Bluetooth discovery and classic RFCOMM behavior vary by device vendor.
- Phones usually need to be paired first for reliable RFCOMM sockets.
- This is a demo routing layer, not a production mesh protocol.
- The current environment could not build an APK because Java / Gradle / Android SDK were not installed locally.
