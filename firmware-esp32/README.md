# AccesoLibre ESP32 firmware (PlatformIO / Arduino)

1. `copy include\secrets.example.h include\secrets.h` and fill Wi-Fi, broker, `CONDO_ID`, `DEVICE_ID`, `CONDO_HMAC_SECRET_HEX`.
2. `pio run -t upload` and `pio device monitor`.

| Signal | ESP32 pin | Notes |
| --- | --- | --- |
| Relay IN | GPIO26 | active HIGH (`RELAY_ACTIVE_LEVEL` in `config.h`); 1 s pulse |
| Reed switch | GPIO27 → GND | `INPUT_PULLUP`; contact closed = gate closed; alert after 45 s open |
| GM65 TX → ESP32 RX2 | GPIO16 | UART2, 9600 8N1, GM65 in UART output mode with CR suffix |
| GM65 RX ← ESP32 TX2 | GPIO17 | |

Modules (`src/`): `net` (Wi-Fi + MQTT reconnect), `commands` (UNLOCK / SYNC_CONFIG / REVOKE_PASS), `qr_reader` (UART2 line reader), `qr_verifier` (mbedtls HMAC-SHA256), `time_service` (NTP + NVS backup clock), `relay_controller`, `gate_sensor`, `telemetry`, `revoked_list`, `storage`.

Contracts: see `ARCHITECTURE.md` sections 5 and 6.
