#include "time_service.h"
#include <time.h>
#include <esp_sntp.h>
#include "config.h"
#include "storage.h"

namespace {
bool ntpSynced = false;
bool backupValid = false;
int64_t backupBaseUnix = 0;
uint32_t backupBaseMillis = 0;
uint32_t lastPersistMs = 0;
uint32_t lastNtpCheckMs = 0;
const uint32_t PERSIST_INTERVAL_MS = 15UL * 60UL * 1000UL;

void setBackup(int64_t unixTime) {
  backupBaseUnix = unixTime;
  backupBaseMillis = millis();
  backupValid = unixTime >= MIN_VALID_UNIX;
}
}  // namespace

namespace TimeService {

void begin() {
  // UTC, no DST: QR timestamps are absolute Unix seconds.
  configTime(0, 0, NTP_SERVER_PRIMARY, NTP_SERVER_SECONDARY);

  int64_t persisted = Storage::loadLastUnixTime();
  if (persisted >= MIN_VALID_UNIX) {
    setBackup(persisted);
    Serial.printf("[time] backup clock restored from NVS: %lld\n", (long long)persisted);
  } else {
    Serial.println(F("[time] no backup time; QR validation blocked until NTP/SYNC_CONFIG"));
  }
}

void loop() {
  const uint32_t ms = millis();
  if (!ntpSynced && (uint32_t)(ms - lastNtpCheckMs) >= 1000UL) {
    lastNtpCheckMs = ms;
    if (sntp_get_sync_status() == SNTP_SYNC_STATUS_COMPLETED && time(nullptr) >= MIN_VALID_UNIX) {
      ntpSynced = true;
      Serial.printf("[time] NTP synced: %lld\n", (long long)time(nullptr));
      Storage::saveLastUnixTime((int64_t)time(nullptr));
      lastPersistMs = ms;
    }
  }
  if (valid() && (uint32_t)(ms - lastPersistMs) >= PERSIST_INTERVAL_MS) {
    lastPersistMs = ms;
    Storage::saveLastUnixTime(now());
  }
}

int64_t now() {
  if (ntpSynced) return (int64_t)time(nullptr);
  if (backupValid) return backupBaseUnix + (int64_t)((uint32_t)(millis() - backupBaseMillis) / 1000UL);
  return 0;
}

bool valid() { return ntpSynced || backupValid; }

const char* source() { return ntpSynced ? "ntp" : (backupValid ? "backup" : "none"); }

void syncFromServer(int64_t serverUnix) {
  if (ntpSynced || serverUnix < MIN_VALID_UNIX) return;
  setBackup(serverUnix);
  Storage::saveLastUnixTime(serverUnix);
  lastPersistMs = millis();
  Serial.printf("[time] backup clock set from server: %lld\n", (long long)serverUnix);
}

}  // namespace TimeService
