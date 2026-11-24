#include "relay_controller.h"
#include "config.h"

namespace {
bool on = false;
uint32_t startedMs = 0;
uint32_t durationMs = 0;

void drive(bool enable) {
  digitalWrite(PIN_RELAY, enable ? RELAY_ACTIVE_LEVEL : (RELAY_ACTIVE_LEVEL == HIGH ? LOW : HIGH));
}
}  // namespace

namespace Relay {

void begin() {
  pinMode(PIN_RELAY, OUTPUT);
  drive(false);  // inactive before anything else
}

void pulse(uint32_t ms) {
  const uint32_t nowMs = millis();
  if (on) {
    const uint32_t remaining = durationMs - (uint32_t)(nowMs - startedMs);
    if (ms <= remaining) return;
  }
  startedMs = nowMs;
  durationMs = ms;
  on = true;
  drive(true);
  Serial.printf("[relay] ON for %lu ms\n", (unsigned long)ms);
}

void loop() {
  if (on && (uint32_t)(millis() - startedMs) >= durationMs) {
    on = false;
    drive(false);
    Serial.println(F("[relay] OFF"));
  }
}

bool active() { return on; }

}  // namespace Relay
