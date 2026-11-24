#pragma once
#include <Arduino.h>

// Unix time with NTP as primary source and an internal backup:
//   backup = last persisted time (NVS) / server_unix_time (SYNC_CONFIG) + millis().
namespace TimeService {

void begin();
void loop();

// Unix seconds, or 0 if no trustworthy source exists yet.
int64_t now();
bool valid();
const char* source();  // "ntp" | "backup" | "none"

// Used by SYNC_CONFIG when NTP has not synced yet.
void syncFromServer(int64_t serverUnix);

}  // namespace TimeService
