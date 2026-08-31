# Mesh MANET architecture

The app is a **BitChat-style dual-role mesh messenger**. Nearby Android phones form an ad-hoc network over Bluetooth Low Energy without pairing. Classic RFCOMM remains as a compatibility path for the Windows node and older radios.

BitChat ([permissionlesstech/bitchat](https://github.com/permissionlesstech/bitchat)) is the reference design. This project maps those layers onto the existing MANET packet format rather than speaking BitChat's binary protocol on the wire.

## Layers

```text
Compose / XML UI  (MainActivity is the live messenger)
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

`MeshPacket` stays transport independent. Relays forward opaque payloads and only change TTL / hop metadata. `RoutingManager` prefers a scored next hop from neighbor quality; if no confirmed route exists it floods connected neighbors (BitChat's gossip fallback).

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

The Android Keystore / local EC keypair owns this installation's identity. `PacketSigner` uses ECDSA P-256 over SHA-256. Relays forward packets without modifying the signed fields (TTL is excluded from the signature). Destinations drop messages or files whose signature or SHA-256 hash does not match. Unsigned application payloads are treated as tampered.

## Persistence

Room stores messages, pending packets, mesh neighbors, routes, and packet history. Migrations preserve the chat database from v1 through v3.

## How a packet moves

See [PACKET_LIFECYCLE.md](PACKET_LIFECYCLE.md). BLE and RFCOMM both dump complete application payloads into `BluetoothMeshManager.ingestPayload`.
