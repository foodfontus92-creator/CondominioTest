#pragma once
#include <Arduino.h>
#include "gate_sensor.h"

// ACCESS_EVENT produced by the offline QR validation (queued while MQTT is down).
struct AccessEventRecord {
  int64_t occurredAt;      // 0 = clock unknown (backend will stamp its own time)
  const char* result;      // static string, backend AccessResult name
  char passIdB64[23];
  char unitIdB64[23];
  bool hasIds;
  uint16_t latencyMs;
};

struct GateEventRecord {
  GateSensor::Event event;
  int64_t occurredAt;
};

// Builders for the telemetry contracts in ARCHITECTURE.md section 6.3.
namespace Telemetry {

bool publishHeartbeat(bool retain, uint32_t hmacKeyVersion);
bool publishAccessEvent(const AccessEventRecord& rec);
bool publishCommandAck(const char* commandId, const char* status, const char* errorMessage);
bool publishGateEvent(const GateEventRecord& rec);

}  // namespace Telemetry
