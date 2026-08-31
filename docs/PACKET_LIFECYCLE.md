# Packet lifecycle

```text
create → (optional encrypt) → fragment → send on BLE and/or RFCOMM
                         ↓
receive → reassemble GATT frames → parse line → deduplicate
                                             ↓
                           destination? ─ yes → deliver + ACK (or save file)
                                ↓ no
                      choose route if known, else flood
                      decrement TTL → forward or queue
```

Packets expire when TTL reaches zero (origin TTL is 7, matching BitChat's mesh depth) or their timestamp exceeds the configured retention window. `packet_history` persists replay/deduplication entries. `pending_messages` provides store-and-forward delivery for unavailable peers and is cleaned after 24 hours.

BLE uses a 4-byte little-endian length prefix plus payload, then splits that stream into `MTU - 3` GATT writes (180-byte application frames after MTU negotiation). The legacy GATT frame codec remains available for tests and future authenticated headers.
