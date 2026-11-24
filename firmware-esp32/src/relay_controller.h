#pragma once
#include <Arduino.h>

// Non-blocking relay pulse (gate motor trigger or door strike).
namespace Relay {

void begin();
void loop();
// Starts (or extends) a pulse; never shortens a running one.
void pulse(uint32_t durationMs);
bool active();

}  // namespace Relay
