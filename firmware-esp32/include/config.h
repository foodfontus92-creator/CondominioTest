#pragma once
#include <Arduino.h>

#if __has_include("secrets.h")
#include "secrets.h"
#else
#error "Missing include/secrets.h: copy include/secrets.example.h and fill it in"
#endif

#define FW_VERSION "1.0.0"
#define SCHEMA_VERSION 1

// ---- Pins ---------------------------------------------------------------
#define PIN_RELAY         26      // relay module IN (gate motor pulse / door strike)
#define RELAY_ACTIVE_LEVEL HIGH   // set LOW for active-low relay boards
#define PIN_REED          27      // reed switch to GND, INPUT_PULLUP
#define REED_CLOSED_LEVEL LOW     // magnet near contact = gate closed
#define PIN_QR_RX         16      // UART2 RX  <- GM65 TX
#define PIN_QR_TX         17      // UART2 TX  -> GM65 RX
#define QR_BAUD           9600

// ---- Behaviour ----------------------------------------------------------
#define RELAY_PULSE_MS        1000UL
#ifndef HONOR_COMMAND_PULSE_MS
#define HONOR_COMMAND_PULSE_MS 0  // 1 = use pulse_ms from UNLOCK command (clamped 200..30000)
#endif
#define GATE_OPEN_ALERT_SEC   45UL
#define REED_DEBOUNCE_MS      50UL
#define HEARTBEAT_INTERVAL_MS 30000UL
#define QR_REPEAT_IGNORE_MS   2000UL
#define COMMAND_MAX_AGE_SEC   60

// ---- QR protocol (ARCHITECTURE.md section 5) -----------------------------
#define QR_MAX_PAYLOAD_LEN    120   // payload length must be < 120
#define QR_LINE_BUF           160
#define HMAC_TRUNC_BYTES      12
#define MIN_VALID_UNIX        1704067200LL  // 2024-01-01, anything below = clock not set

// ---- Network ------------------------------------------------------------
#define WIFI_RETRY_MS         10000UL
#define MQTT_RETRY_MIN_MS     1000UL
#define MQTT_RETRY_MAX_MS     30000UL
#define MQTT_BUFFER_BYTES     1024
