#pragma once
// Copy to include/secrets.h (git-ignored) and fill in with the values of THIS device.

#define WIFI_SSID      "my-lan-ssid"
#define WIFI_PASSWORD  "my-lan-password"

// Local Mosquitto broker (docker-compose, port 1883, password auth)
#define MQTT_HOST      "192.168.1.10"
#define MQTT_PORT      1883
#define MQTT_USER      "accesolibre_device"
#define MQTT_PASSWORD  "mqtt_device_local_dev"

// Canonical 36-char UUIDs: condominiums.id and iot_devices.id
#define CONDO_ID       "00000000-0000-0000-0000-000000000000"
#define DEVICE_ID      "00000000-0000-0000-0000-000000000000"

// condominiums.hmac_secret (64 hex chars). Can later be replaced by SYNC_CONFIG over MQTT.
#define CONDO_HMAC_SECRET_HEX "0000000000000000000000000000000000000000000000000000000000000000"

// Prefer a LAN NTP server (router / local server) so it works without Internet
#define NTP_SERVER_PRIMARY   "192.168.1.1"
#define NTP_SERVER_SECONDARY "pool.ntp.org"
