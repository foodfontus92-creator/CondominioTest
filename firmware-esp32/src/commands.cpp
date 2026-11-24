#include "commands.h"
#include <ArduinoJson.h>
#include "config.h"
#include "relay_controller.h"
#include "telemetry.h"
#include "time_service.h"
#include "util.h"

namespace {
Credentials* g_creds = nullptr;
RevokedList* g_revoked = nullptr;

char recentIds[8][40];
uint8_t recentNext = 0;

bool seenBefore(const char* id) {
  for (auto& r : recentIds) {
    if (r[0] != '\0' && strcmp(r, id) == 0) return true;
  }
  return false;
}

void remember(const char* id) {
  strncpy(recentIds[recentNext], id, sizeof(recentIds[0]) - 1);
  recentIds[recentNext][sizeof(recentIds[0]) - 1] = '\0';
  recentNext = (recentNext + 1) % 8;
}

void cmdUnlock(const char* commandId, int64_t issuedAt, JsonObject p) {
  if (TimeService::valid() && issuedAt > 0 && llabs(TimeService::now() - issuedAt) > COMMAND_MAX_AGE_SEC) {
    Serial.println(F("[cmd] UNLOCK rejected: stale"));
    Telemetry::publishCommandAck(commandId, "REJECTED", "stale_command");
    return;
  }
  uint32_t pulse = RELAY_PULSE_MS;
#if HONOR_COMMAND_PULSE_MS
  pulse = constrain((uint32_t)(p["pulse_ms"] | (int)RELAY_PULSE_MS), 200UL, 30000UL);
#endif
  Serial.printf("[cmd] UNLOCK reason=%s user=%s\n", p["reason"] | "?", p["triggered_by_user_id"] | "-");
  Relay::pulse(pulse);
  Telemetry::publishCommandAck(commandId, "EXECUTED", nullptr);
}

void cmdSyncConfig(const char* commandId, JsonObject p) {
  const char* secretHex = p["hmac_secret_hex"] | "";
  const char* condoB64 = p["condo_id_b64"] | "";
  Credentials next = *g_creds;

  if (strlen(condoB64) != 22 || strncmp(condoB64, g_creds->condoIdB64, 22) != 0) {
    Telemetry::publishCommandAck(commandId, "REJECTED", "condo_id_mismatch");
    return;
  }
  if (!Util::hexToBytes(secretHex, strlen(secretHex), next.condoKey, sizeof(next.condoKey))) {
    Telemetry::publishCommandAck(commandId, "REJECTED", "invalid_hmac_secret_hex");
    return;
  }
  next.keyVersion = p["hmac_key_version"] | 1;
  *g_creds = next;
  Storage::saveCredentials(*g_creds);
  TimeService::syncFromServer(p["server_unix_time"] | 0LL);
  Serial.printf("[cmd] SYNC_CONFIG applied, key version %u\n", (unsigned)g_creds->keyVersion);
  Telemetry::publishCommandAck(commandId, "EXECUTED", nullptr);
}

void cmdRevokePass(const char* commandId, JsonObject p) {
  const char* passB64 = p["pass_id_b64"] | "";
  if (strlen(passB64) != 22) {
    Telemetry::publishCommandAck(commandId, "REJECTED", "invalid_pass_id");
    return;
  }
  g_revoked->add(passB64, p["expires_at"] | 0LL, TimeService::now());
  Serial.printf("[cmd] REVOKE_PASS %s\n", passB64);
  Telemetry::publishCommandAck(commandId, "EXECUTED", nullptr);
}
}  // namespace

namespace Commands {

void begin(Credentials* creds, RevokedList* revoked) {
  g_creds = creds;
  g_revoked = revoked;
  memset(recentIds, 0, sizeof(recentIds));
}

void handle(const char* payload, size_t len) {
  JsonDocument doc;
  if (deserializeJson(doc, payload, len) != DeserializationError::Ok) {
    Serial.println(F("[cmd] invalid JSON ignored"));
    return;
  }
  const char* commandId = doc["command_id"] | "";
  const char* type = doc["type"] | "";
  const int64_t issuedAt = doc["issued_at"] | 0LL;
  JsonObject p = doc["payload"].as<JsonObject>();

  if (commandId[0] == '\0' || type[0] == '\0') {
    Serial.println(F("[cmd] missing command_id/type, ignored"));
    return;
  }
  if (seenBefore(commandId)) {
    Serial.printf("[cmd] duplicate %s ignored\n", commandId);
    Telemetry::publishCommandAck(commandId, "EXECUTED", "duplicate_ignored");
    return;
  }
  remember(commandId);

  if (strcmp(type, "UNLOCK") == 0) cmdUnlock(commandId, issuedAt, p);
  else if (strcmp(type, "SYNC_CONFIG") == 0) cmdSyncConfig(commandId, p);
  else if (strcmp(type, "REVOKE_PASS") == 0) cmdRevokePass(commandId, p);
  else if (strcmp(type, "INTERCOM_ACTION") == 0) {
    Serial.printf("[cmd] INTERCOM_ACTION %s (no audio hardware)\n", p["action"] | "?");
    Telemetry::publishCommandAck(commandId, "EXECUTED", nullptr);
  } else {
    Serial.printf("[cmd] unsupported type %s\n", type);
    Telemetry::publishCommandAck(commandId, "REJECTED", "unsupported_type");
  }
}

}  // namespace Commands
