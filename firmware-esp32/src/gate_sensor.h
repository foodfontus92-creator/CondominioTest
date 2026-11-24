#pragma once
#include <Arduino.h>

// Reed switch monitor with debounce and "left open" detection.
namespace GateSensor {

enum class EventType : uint8_t { OPEN_TIMEOUT, CLOSED_AFTER_ALERT };

struct Event {
  EventType type;
  uint32_t openSeconds;
};

void begin();
void loop();
bool isOpen();
bool poll(Event& out);  // pops at most one pending event

}  // namespace GateSensor
