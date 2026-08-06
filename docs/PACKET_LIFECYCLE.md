# Packet lifecycle

```text
create → encrypt → fragment → send
                         ↓
receive → validate checksum → reassemble → deduplicate
                                             ↓
                           destination? ─ yes → decrypt + acknowledge
                                ↓ no
                      choose route → decrement TTL → forward or queue
```

Packets expire when TTL reaches zero or their timestamp exceeds the configured retention window. `packet_history` persists replay/deduplication entries. `pending_messages` provides store-and-forward delivery for unavailable peers and is cleaned after 24 hours.

The GATT frame codec limits payload frames to 180 bytes by default, leaving room for BLE ATT overhead and future authenticated frame headers.
