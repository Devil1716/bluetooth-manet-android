# Mesh MANET architecture

The application is migrating from the original Java RFCOMM proof of concept to a Kotlin-first, layered mesh messenger.

## Layers

```text
Compose UI / ViewModels
        ↓
Use cases / repositories
        ↓
Routing manager + packet cache + queue
        ↓
Mesh transport contract
        ↓
BLE GATT (target) and legacy RFCOMM (compatibility)
```

`MeshPacket` is transport independent. Relays use its immutable encrypted payload and only change forwarding metadata. `RoutingManager` selects a next hop from neighbor quality, latency, battery, and hop count.

## Persistence

Room stores messages, pending packets, mesh neighbors, routes, and packet history. Migrations preserve the existing chat database from v1 through v3; destructive migration is not used.

## Security boundary

The Android Keystore owns the installation AES key. `AesGcmCipher` provides authenticated encryption for packet payloads. The planned GATT handshake will derive per-peer keys before `MeshPacket` is serialized; routers must never decrypt application payloads.

## Transport status

The legacy RFCOMM transport remains the active compatibility transport. The new GATT framing contract is in place and validated by unit tests; GATT client/server implementation is the next migration step.
