#include "storage.h"
#include <Preferences.h>
#include "config.h"
#include "util.h"

namespace {
Preferences prefs;
const char* NS = "accesolibre";
}

namespace Storage {

void begin() { prefs.begin(NS, false); }

void loadCredentials(Credentials& out) {
  memset(&out, 0, sizeof(out));
  if (!Util::uuidToBase64Url(CONDO_ID, out.condoIdB64)) {
    Serial.println(F("[cfg] CONDO_ID in secrets.h is not a valid UUID"));
  }
  if (prefs.getBytesLength("ckey") == sizeof(out.condoKey)) {
    prefs.getBytes("ckey", out.condoKey, sizeof(out.condoKey));
    out.keyVersion = prefs.getUInt("ckver", 1);
    Serial.printf("[cfg] HMAC key loaded from NVS (version %u)\n", (unsigned)out.keyVersion);
  } else {
    if (!Util::hexToBytes(CONDO_HMAC_SECRET_HEX, strlen(CONDO_HMAC_SECRET_HEX), out.condoKey, sizeof(out.condoKey))) {
      Serial.println(F("[cfg] CONDO_HMAC_SECRET_HEX must be 64 hex chars"));
    }
    out.keyVersion = 1;
    Serial.println(F("[cfg] HMAC key loaded from secrets.h defaults"));
  }
}

void saveCredentials(const Credentials& creds) {
  prefs.putBytes("ckey", creds.condoKey, sizeof(creds.condoKey));
  prefs.putUInt("ckver", creds.keyVersion);
}

int64_t loadLastUnixTime() { return (int64_t)prefs.getLong64("last_t", 0); }
void saveLastUnixTime(int64_t t) { prefs.putLong64("last_t", (int64_t)t); }

size_t loadBlob(const char* key, void* buf, size_t len) {
  if (prefs.getBytesLength(key) != len) return 0;
  return prefs.getBytes(key, buf, len);
}

bool saveBlob(const char* key, const void* buf, size_t len) {
  return prefs.putBytes(key, buf, len) == len;
}

}  // namespace Storage
