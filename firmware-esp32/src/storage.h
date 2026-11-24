#pragma once
#include <Arduino.h>

// Active HMAC material of the condominium (survives reboot via NVS).
struct Credentials {
  uint8_t condoKey[32];     // condominiums.hmac_secret (hex_decode)
  char condoIdB64[23];      // Base64URL(UUID bytes) of CONDO_ID, 22 chars + NUL
  uint32_t keyVersion;      // hmac_key_version
};

namespace Storage {

void begin();

// NVS value if present, otherwise compile-time defaults from secrets.h.
void loadCredentials(Credentials& out);
void saveCredentials(const Credentials& creds);

int64_t loadLastUnixTime();
void saveLastUnixTime(int64_t t);

size_t loadBlob(const char* key, void* buf, size_t len);
bool saveBlob(const char* key, const void* buf, size_t len);

}  // namespace Storage
