# AccesoLibre IoT Simulator

Emulates the ESP32 controller from the terminal (same topics, JSON and QR algorithm as the firmware).

```powershell
pip install -r requirements.txt
python simulator.py --condo-id <condominiums.id> --device-id <iot_devices.id> --condo-secret <hmac_secret 64 hex> `
       --host localhost --port 1883 --user accesolibre_device --password mqtt_device_local_dev
```

(Also configurable via `SIM_MQTT_HOST`, `SIM_MQTT_PORT`, `SIM_MQTT_USER`, `SIM_MQTT_PASSWORD`, `SIM_CONDO_ID`, `SIM_DEVICE_ID`, `SIM_CONDO_SECRET`.)

At the `qr>` prompt paste a QR string to simulate a scan. Commands: `/gen <unit_uuid> [ttl]`, `/open`, `/close`, `/disconnect`, `/connect`, `/status`, `/help`, `/quit`.

When the backend publishes `UNLOCK` (e.g. `POST /api/v1/access/open`) the console prints `>>> OPEN COMMAND RECEIVED FROM BACKEND`.

Tests: `python -m unittest discover -v` (known-answer vector shared with the Java backend test).
