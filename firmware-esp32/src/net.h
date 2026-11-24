#pragma once
#include <Arduino.h>

// Wi-Fi + MQTT link (PubSubClient) with automatic, non-blocking-ish reconnection.
// Offline operation (QR + relay) never depends on this module.
namespace Net {

using MessageHandler = void (*)(const char* payload, size_t len);

void begin(MessageHandler onCommand);
void loop();

bool connected();
// True exactly once after each (re)connection to the broker.
bool consumeJustConnected();

// Publishes on condo/{condoId}/device/{deviceId}/telemetry
bool publishTelemetry(const char* json, bool retain = false);

const char* commandTopic();
const char* telemetryTopic();

}  // namespace Net
