# Physical-device test checklist

Unit tests and the JVM mesh helpers cannot prove radio behavior, OEM battery policy, or Bluetooth stacks. Use this checklist on at least two phones before claiming reliability.

Record: device model, Android version, whether battery saver / OEM “sleeping apps” is on, topology, duration, and counts (sent / delivered / duplicate / failed).

## Matrix (fill in results; do not invent them)

| Phone | Android | Role | Notes |
| --- | --- | --- | --- |
| | | A sender | |
| | | B relay / peer | |
| | | C destination (optional) | |

## Messaging

- [ ] Two phones, direct chat: status moves Queued → Sending → Sent · waiting for confirmation → Delivered. Checkmark only on Delivered.
- [ ] Send while no peers: message stays **Waiting for a nearby phone**, not failed. After a link appears, it leaves the phone and later Delivered if the ACK returns.
- [ ] Three phones, A–B–C: A→C is delivered; B does not show the chat as its own thread unless it is the destination.
- [ ] Disconnect before send, during send, and after send but before ACK. Confirm no duplicate bubbles for the same message ID. Lost ACK should still reach Delivered after retry (destination resends ACK).
- [ ] Airplane / Bluetooth off then on: queued messages retry; UI matches Bluetooth-off vs permission vs searching.

## Transport

- [ ] BLE only (no classic pairing) between two Android phones.
- [ ] RFCOMM / Windows node still exchanges a signed chat line if you still support that path.
- [ ] Large file: progress updates; cancel/retry if offered; incomplete transfer does not show as fully received.

## Process and OS

- [ ] Screen off, Mesh notification visible, chat still delivers for several minutes.
- [ ] App backgrounded (not force-stopped).
- [ ] Swipe away from recents (process death): queued messages still in the thread after relaunch; mesh may need the notification/service restart.
- [ ] Force-stop: mesh **does not** run. The app must not claim it does.
- [ ] Battery saver / OEM autostart restrictions: document whether discovery stalls.
- [ ] Revoke Bluetooth permission while running: mesh stops or shows Permission needed.
- [ ] Notification **Stop nearby chat** stops the service; Nearby tab shows nearby chat off.

## Identity

- [ ] First meeting stores a fingerprint. A second device advertising the same node ID with a different key shows the identity warning and does not replace the pin.

## What simulation cannot prove

Deterministic unit tests cover ACK split-horizon, queue vs failed, pin-on-first-key, and backoff numbers. They cannot prove GATT write callbacks on a given OEM, MTU negotiation, BLE duty cycle, or that a flood reaches a third phone.
