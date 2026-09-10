# Threat model (MESH Bluetooth MANET)

This is an honest map of what the live Java mesh path (`BluetoothMeshManager` + BLE GATT + RFCOMM) can and cannot defend. Algorithm names are not a security claim.

## Actors

| Actor | Access |
| --- | --- |
| Passive radio observer | Sees BLE/RFCOMM frames in range |
| Malicious peer | Runs a modified MESH node, can send HELLO/MSG/ACK/FILE |
| Untrusted relay | Forwards packets; can drop, delay, duplicate, or flood |
| Compromised device | Reads local files, Keystore (if unlocked), Room DB |

## What is protected today

- **Message authentication (not encryption).** Chat lines and file chunks carry ECDSA P-256 signatures (`PacketSigner`). Relays that alter `TYPE|ID|SRC|DEST|DATA` fail verification at a node that already knows the sender’s public key.
- **Identity pin.** The first verified HELLO public key for a node ID is stored in `identity/known-peers.txt`. A later HELLO with a different key is rejected and the UI shows a fingerprint warning. This is TOFU (trust on first use), not in-person verification.
- **Device-held keys on new installs.** If no legacy PKCS8 files exist, the signing keypair is created in Android Keystore (API 23+). Existing installs that already wrote `mesh-ecdsa-private.key` keep that file so the node identity does not silently change.

## What is not protected

- **No end-to-end encryption.** `AesGcmCipher` is unused on the send path. Relays and radio observers can read chat bodies and file bytes. Signatures prove origin of plaintext; they do not hide it.
- **Routing metadata is visible.** Source, destination, TTL, message ID, and packet type are required for flood routing and are not encrypted.
- **TOFU can be poisoned.** The first HELLO that verifies wins. An attacker who speaks first can bind their key to someone else’s node ID until the user notices a later conflict.
- **No QR / fingerprint verification yet.** “Unverified” means “we stored the first key we saw.” “Changed identity” means a different key appeared for that ID. There is no “verified in person” state in this revision.
- **Legacy private keys are files.** Pre-existing PKCS8 files under `filesDir/identity` are exportable if the app sandbox is readable (backup, root, debug backup). New Keystore keys are non-exportable but still lost on app uninstall unless a backup exists — MESH does not ship a recovery phrase.
- **Upload signing key was public.** See [SIGNING.md](SIGNING.md). A leaked Play/GitHub signing key lets an attacker ship a fake update, which is a full device-side compromise of the app.

## Relays

Relays **can** check:

- Framing (length prefix, parseable `ManetMessage`)
- TTL remaining and seen-set (after authentication for signed traffic)
- HELLO self-signature against the advertised key
- For traffic whose source key is already pinned: signature over canonical fields

Relays **cannot** authenticate a payload encrypted to a recipient they do not know, and they **must not** be required to. Abuse is bounded by TTL 7, a ~1000-entry seen cache (10 minutes), 24-hour pending storage, and per-link BLE queues. These are DoS mitigations, not proofs.

## Replay and identity substitution

- Authentication runs **before** the seen-set. Tampered unique IDs are marked seen and dropped so they cannot occupy the cache for a later genuine packet with the same id. Unknown-source packets destined to this node are held without marking seen, so a later HELLO can still allow delivery.
- Duplicate chat messages destined to this node resend ACK without inserting a second conversation row (lost-ACK recovery).
- Identity substitution of a **known** contact is rejected. Substitution of an **unknown** ID is indistinguishable from a new neighbor.

## Recovery and key loss

| Event | Behavior |
| --- | --- |
| Reinstall / clear data | New signing key. Peers treat this as a new identity or an identity change if the node ID is reused. |
| Android Keystore unavailable | Falls back to PKCS8 files in the app sandbox. |
| User copies node ID to a new phone | ID is a display label; cryptographic identity does not move unless keys are moved. |
| Backup / ADB pull of legacy PKCS8 | Private signing key can leave the device. |

## End-to-end encryption (deferred)

E2E would change the wire payload (`DATA`) and must not be silently enabled against legacy peers. A later stage should use a maintained protocol for asynchronous offline messaging (for example Noise or a reviewed Signal-style session library), keep an outer signed envelope relays can check, and refuse to downgrade an encrypted conversation to plaintext. That work is intentionally not in this revision.
