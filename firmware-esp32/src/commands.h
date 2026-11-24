#pragma once
#include <Arduino.h>
#include "storage.h"
#include "revoked_list.h"

// Handles JSON envelopes received on condo/{condoId}/device/{deviceId}/command:
//   {"command_id","type","issued_at","payload":{...}}  (ARCHITECTURE.md 6.2)
namespace Commands {

void begin(Credentials* creds, RevokedList* revoked);
void handle(const char* payload, size_t len);

}  // namespace Commands
