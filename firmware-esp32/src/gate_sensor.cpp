#include "gate_sensor.h"
#include "config.h"

namespace {
bool stableOpen = false;
bool rawOpen = false;
uint32_t rawChangedMs = 0;
uint32_t openedAtMs = 0;
bool alerted = false;

GateSensor::Event pending[4];
uint8_t pendingCount = 0;

void push(GateSensor::EventType t, uint32_t openSeconds) {
  if (pendingCount < 4) pending[pendingCount++] = {t, openSeconds};
}

bool readOpen() { return digitalRead(PIN_REED) != REED_CLOSED_LEVEL; }
}  // namespace

namespace GateSensor {

void begin() {
  pinMode(PIN_REED, INPUT_PULLUP);
  stableOpen = rawOpen = readOpen();
  rawChangedMs = millis();
  openedAtMs = stableOpen ? millis() : 0;
  alerted = false;
}

void loop() {
  const uint32_t now = millis();
  const bool sample = readOpen();
  if (sample != rawOpen) {
    rawOpen = sample;
    rawChangedMs = now;
  }
  if (rawOpen != stableOpen && (uint32_t)(now - rawChangedMs) >= REED_DEBOUNCE_MS) {
    stableOpen = rawOpen;
    if (stableOpen) {
      openedAtMs = now;
      alerted = false;
      Serial.println(F("[gate] OPEN"));
    } else {
      const uint32_t openSec = (uint32_t)(now - openedAtMs) / 1000UL;
      Serial.printf("[gate] CLOSED after %lu s\n", (unsigned long)openSec);
      if (alerted) push(EventType::CLOSED_AFTER_ALERT, openSec);
      alerted = false;
    }
  }
  if (stableOpen && !alerted && (uint32_t)(now - openedAtMs) >= GATE_OPEN_ALERT_SEC * 1000UL) {
    alerted = true;
    const uint32_t openSec = (uint32_t)(now - openedAtMs) / 1000UL;
    Serial.printf("[gate] ALERT: open for %lu s\n", (unsigned long)openSec);
    push(EventType::OPEN_TIMEOUT, openSec);
  }
}

bool isOpen() { return stableOpen; }

bool poll(Event& out) {
  if (pendingCount == 0) return false;
  out = pending[0];
  for (uint8_t i = 1; i < pendingCount; i++) pending[i - 1] = pending[i];
  pendingCount--;
  return true;
}

}  // namespace GateSensor
