#include "telemetry.h"
#include <ArduinoJson.h>
#include <WiFi.h>
#include "config.h"
#include "net.h"
#include "time_service.h"

namespace {
bool send(const JsonDocument& doc, bool retain) {
  char buf[512];
  const size_t n = serializeJson(doc, buf, sizeof(buf));
  if (n == 0 || n >= sizeof(buf)) return false;
  return Net::publishTelemetry(buf, retain);
}

void header(JsonDocument& doc, const char* event, int64_t occurredAt) {
  doc["event"] = event;
  doc["schema_version"] = SCHEMA_VERSION;
  if (occurredAt > 0) doc["occurred_at"] = (int64_t)occurredAt;
}
}  // namespace

namespace Telemetry {

bool publishHeartbeat(bool retain, uint32_t hmacKeyVersion) {
  JsonDocument doc;
  header(doc, "HEARTBEAT", TimeService::now());
  JsonObject p = doc["payload"].to<JsonObject>();
  p["status"] = "ONLINE";
  p["firmware_version"] = FW_VERSION;
  p["ip_address"] = WiFi.localIP().toString();
  p["wifi_rssi_dbm"] = WiFi.RSSI();
  p["uptime_seconds"] = (uint32_t)(millis() / 1000UL);
  p["free_heap_bytes"] = (uint32_t)ESP.getFreeHeap();
  p["hmac_key_version_synced"] = hmacKeyVersion;
  p["door_sensor_open"] = (digitalRead(PIN_REED) != REED_CLOSED_LEVEL);
  return send(doc, retain);
}

bool publishAccessEvent(const AccessEventRecord& rec) {
  JsonDocument doc;
  header(doc, "ACCESS_EVENT", rec.occurredAt);
  JsonObject p = doc["payload"].to<JsonObject>();
  p["access_method"] = "QR_OFFLINE";
  p["result"] = rec.result;
  p["offline_validated"] = true;
  if (rec.hasIds) {
    p["pass_id_b64"] = rec.passIdB64;
    p["unit_id_b64"] = rec.unitIdB64;
  }
  p["latency_ms"] = rec.latencyMs;
  p["command_id"] = nullptr;
  return send(doc, false);
}

bool publishCommandAck(const char* commandId, const char* status, const char* errorMessage) {
  JsonDocument doc;
  header(doc, "COMMAND_ACK", TimeService::now());
  JsonObject p = doc["payload"].to<JsonObject>();
  p["command_id"] = commandId;
  p["status"] = status;
  if (errorMessage != nullptr) p["error_message"] = errorMessage;
  else p["error_message"] = nullptr;
  return send(doc, false);
}

bool publishGateEvent(const GateEventRecord& rec) {
  JsonDocument doc;
  header(doc, rec.event.type == GateSensor::EventType::OPEN_TIMEOUT ? "GATE_OPEN_TIMEOUT" : "GATE_CLOSED",
         rec.occurredAt);
  JsonObject p = doc["payload"].to<JsonObject>();
  p["open_seconds"] = rec.event.openSeconds;
  p["threshold_seconds"] = (uint32_t)GATE_OPEN_ALERT_SEC;
  return send(doc, false);
}

}  // namespace Telemetry
