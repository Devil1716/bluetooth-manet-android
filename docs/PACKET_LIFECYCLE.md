# Packet lifecycle

```text
create → sign → send on BLE and/or RFCOMM (or persist pending)
                         ↓
receive → reassemble GATT frames → parse line → authenticate
                                             ↓
                           tampered → mark seen, drop
                           unknown source → hold if for me; else forward once
                           ok → deduplicate
                                             ↓
                           destination? ─ yes → deliver + ACK (resend ACK on duplicate)
                                ↓ no
                      decrement TTL → forward (split-horizon) or keep pending
```

Packets expire when TTL reaches zero (origin TTL is 7) or pending rows exceed 24 hours (`FAILED` in the chat UI). A successful write to a neighbor is **Sent · waiting for confirmation**, not Delivered. Delivered requires an authenticated ACK bound to that message ID from a peer whose key is pinned.

BLE uses a 4-byte little-endian length prefix plus payload, then splits that stream into `MTU - 3` GATT writes. Client writes wait for the GATT callback (or a 3s timeout) before dropping a chunk. Server notifications still use a short delay after the stack accepts the notify. Link completion is not end-to-end delivery.
