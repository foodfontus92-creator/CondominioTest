// AccesoLibre ESP32 controller: gate/door relay, GM65 QR reader (offline HMAC),
// reed switch alarm and MQTT link to the local Mosquitto broker.
#include <Arduino.h>
#include "commands.h"
#include "config.h"
#include "gate_sensor.h"
#include "net.h"
#include "qr_reader.h"
#include "qr_verifier.h"
#include "relay_controller.h"
#include "revoked_list.h"
#include "ring_queue.h"
#include "storage.h"
#include "telemetry.h"
#include "time_service.h"

static Credentials creds;
static RevokedList revoked;
static RingQueue<AccessEventRecord, 32> accessQueue;
static RingQueue<GateEventRecord, 8> gateQueue;

static char lastQr[QR_LINE_BUF];
static uint32_t lastQrMs = 0;
static uint32_t lastHeartbeatMs = 0;

static void handleQrScan() {
  char line[QR_LINE_BUF];
  if (!QrReader::poll(line, sizeof(line))) return;

  const uint32_t t0 = millis();
  // GM65 in continuous mode may repeat the same code while it stays in view.
  if (lastQr[0] != '\0' && strcmp(lastQr, line) == 0 && (uint32_t)(t0 - lastQrMs) < QR_REPEAT_IGNORE_MS) {
    lastQrMs = t0;
    return;
  }
  strncpy(lastQr, line, sizeof(lastQr) - 1);
  lastQr[sizeof(lastQr) - 1] = '\0';
  lastQrMs = t0;

  const int64_t now = TimeService::now();
  const QrCheck check = verifyCompactQr(line, now, TimeService::valid(), creds, revoked);

  if (check.verdict == QrVerdict::GRANTED) Relay::pulse(RELAY_PULSE_MS);  // open first, report later

  AccessEventRecord rec;
  memset(&rec, 0, sizeof(rec));
  rec.occurredAt = now;
  rec.result = qrVerdictToResult(check.verdict);
  rec.hasIds = check.parsed;
  if (check.parsed) {
    memcpy(rec.passIdB64, check.passIdB64, sizeof(rec.passIdB64));
    memcpy(rec.unitIdB64, check.unitIdB64, sizeof(rec.unitIdB64));
  }
  rec.latencyMs = (uint16_t)min<uint32_t>(millis() - t0, 65535);
  accessQueue.push(rec);

  Serial.printf("[qr] %s (pass=%s, %u ms, clock=%s)\n", rec.result, check.parsed ? check.passIdB64 : "-",
                (unsigned)rec.latencyMs, TimeService::source());
}

static void collectGateEvents() {
  GateSensor::Event e;
  while (GateSensor::poll(e)) gateQueue.push({e, TimeService::now()});
}

static void flushTelemetry() {
  if (!Net::connected()) return;
  if (Net::consumeJustConnected()) {
    // Overwrites the retained LWT ("device_offline") left by a previous session.
    Telemetry::publishHeartbeat(true, creds.keyVersion);
    lastHeartbeatMs = millis();
  }
  for (int i = 0; i < 4 && !accessQueue.empty(); i++) {
    if (!Telemetry::publishAccessEvent(accessQueue.front())) break;
    accessQueue.pop();
  }
  for (int i = 0; i < 2 && !gateQueue.empty(); i++) {
    if (!Telemetry::publishGateEvent(gateQueue.front())) break;
    gateQueue.pop();
  }
  if ((uint32_t)(millis() - lastHeartbeatMs) >= HEARTBEAT_INTERVAL_MS) {
    lastHeartbeatMs = millis();
    Telemetry::publishHeartbeat(false, creds.keyVersion);
  }
}

void setup() {
  Relay::begin();  // relay must be inactive first
  Serial.begin(115200);
  Serial.printf("\n[boot] AccesoLibre ESP32 fw %s\n", FW_VERSION);

  Storage::begin();
  Storage::loadCredentials(creds);
  revoked.load();

  GateSensor::begin();
  QrReader::begin();
  TimeService::begin();
  Commands::begin(&creds, &revoked);
  Net::begin(Commands::handle);
}

void loop() {
  handleQrScan();  // highest priority: works with no Wi-Fi / MQTT
  Relay::loop();
  GateSensor::loop();
  TimeService::loop();
  Net::loop();
  collectGateEvents();
  flushTelemetry();
}
