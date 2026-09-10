# Mesh MANET architecture

The app is a **BitChat-style dual-role mesh messenger**. Nearby Android phones form an ad-hoc network over Bluetooth Low Energy without pairing. Classic RFCOMM remains as a compatibility path for the Windows node and older radios.

BitChat ([permissionlesstech/bitchat](https://github.com/permissionlesstech/bitchat)) is the reference design. This project maps those layers onto the existing MANET packet format rather than speaking BitChat's binary protocol on the wire.

## Layers

```text
Compose UI  (ComposeMeshActivity is the live messenger; MainActivity is the legacy XML console)
        ↓
MeshService foreground process
        ↓
Message / file protocol  (ManetMessage, FilePacket, TTL, ACK, HELLO)
        ↓
Message router
   ├── BLE GATT dual-role transport   (primary, BitChat model)
   └── RFCOMM transport               (Windows node / manual classic peers)
        ↓
Routing manager  (direct route when known, otherwise controlled flood)
```

`MeshPacket` / `InMemoryRoutingManager` exist in Kotlin for tests and a possible future router. The **live** path is Java `BluetoothMeshManager`: signed `ManetMessage` flood with TTL 7, in-memory seen-set, Room `pending_messages` until destination ACK or 24h expiry. BLE and RFCOMM both dump complete application payloads into `BluetoothMeshManager.ingestPayload` / `handleIncoming`.

## BitChat features included here

| BitChat idea | MANET implementation |
| --- | --- |
| Every phone is GATT **central and peripheral** at once | `BleGattTransport` opens a GATT server, advertises connectably, and scans/connects as a client |
| No pairing or accounts | BLE links use the MANET service UUID; RFCOMM pairing is only for classic/Windows |
| Automatic discovery | Connectable advertisements + manufacturer node ID; role split by node ID so two phones do not collide |
| Compact framed payloads | Length-prefixed GATT stream, then the existing `TYPE\|ID\|SRC\|DEST\|TTL\|DATA` / `FILE\|...` application packets |
| Fragmentation | GATT writes are split to `MTU - 3`; files are also chunked at 800 bytes |
| TTL + dedup flood | Default TTL 7; seen-set for messages; HELLO presence is rate-limited and relayed |
| Split horizon | Incoming BLE/RFCOMM address is excluded when forwarding |
| Store-and-forward | `pending_messages` retries for 24 hours when a destination is offline |
| Neighbor announcements | Periodic HELLO over all live links; neighbors persist in Room |
| Source routing with flood fallback | `InMemoryRoutingManager` uses a scored route when fresh, otherwise floods |
| Foreground mesh | `MeshService` keeps advertising/scanning within Android background limits |
| Connection cap | At most 6 BLE links |
| Files over the mesh | Chunked `FILE` packets, reassembled, saved under app Downloads |

Not copied from BitChat (future work): Noise XX sessions, Nostr/internet transport, courier envelopes, geohash channels.

## Security boundary

See [THREAT_MODEL.md](THREAT_MODEL.md) and [SIGNING.md](SIGNING.md).

Identities are ECDSA P-256 over SHA-256 (`PacketSigner`). New installs prefer Android Keystore; existing PKCS8 files are kept so a reinstall is not implied. Relays must not alter signed fields (TTL is excluded from the signature). **Payloads are not end-to-end encrypted.** A destination that already pinned the sender’s key drops tampered chat and files. HELLO keys are pinned TOFU; a mismatch is a visible identity warning, not a silent overwrite.

## Persistence

Room stores messages, pending packets, mesh neighbors, routes, and packet history. Migrations preserve the chat database from v1 through v3. `MessageStatus.QUEUED` was appended to the enum; Room stores status by name, so existing rows are unchanged. The live dedup cache is in-memory (`seenMessages`); the `packet_history` table is not what the Java mesh path uses today.

## How a packet moves

See [PACKET_LIFECYCLE.md](PACKET_LIFECYCLE.md). BLE and RFCOMM both dump complete application payloads into `BluetoothMeshManager.ingestPayload`.
