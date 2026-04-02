# Windows MANET Node

This folder contains a Windows-native interactive CLI that can participate in the same Bluetooth RFCOMM MANET used by the Android app.

## What it can do

- Advertise the same custom RFCOMM service UUID as the Android app.
- Accept incoming RFCOMM connections.
- Connect to paired Bluetooth devices that advertise the MANET service.
- Send messages in the same wire format:

```text
ID|SRC|DEST|TTL|DATA
```

- Relay unseen messages while decrementing TTL.

## Requirements

- Windows with a working Bluetooth adapter
- Python 3.12+
- The packages in `requirements.txt`
- Android phones paired in Windows Bluetooth settings

## Install

```powershell
cd windows-node
python -m pip install -r requirements.txt
```

## Before running

1. Pair the Android phone in Windows Bluetooth settings.
2. Open the Android app on the phone.
3. Enable Bluetooth on the phone.
4. Tap `Make Discoverable`.
5. Tap `Start Listening`.
6. In Windows Bluetooth settings, allow the PC to be discoverable if you want the phone to connect back to the laptop.

## Run

```powershell
cd windows-node
python manet_node.py --node-id LAPTOP
```

## Interface

When you launch the script it now shows:

- a startup banner
- a guided `menu`
- command-based control if you prefer typing
- cached paired-device list
- inbox and recent logs views

## Commands

- `devices`
  Lists paired Bluetooth devices that Windows exposes to the script.

- `menu`
  Opens the guided action menu.

- `status`
  Shows node status, peer count, and cache counts.

- `connect <index>`
  Connects to the numbered device from the last `devices` refresh.

- `peers`
  Shows active RFCOMM peers.

- `send <DEST> <MESSAGE>`
  Sends a MANET payload into the mesh.

- `inbox`
  Shows recently delivered messages.

- `logs`
  Shows recent event log lines.

- `clear`
  Clears the terminal and redraws the banner.

- `quit`
  Stops the node.

## Example

```text
manet> devices
1. My Android Phone
   \\?\BTHENUM#Dev_...
manet> connect 1
manet> send C hello from laptop
manet> inbox
```

## Notes

- Windows Bluetooth device discovery is more reliable with already paired devices.
- Incoming connections from Android to Windows may require Windows discoverability to be enabled in Bluetooth settings.
- This tool is intended for testing and demos, not production mesh routing.
