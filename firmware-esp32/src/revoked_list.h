#pragma once
#include <Arduino.h>

// Revoked QR pass IDs (Base64URL, 22 chars), persisted in NVS, filled by REVOKE_PASS commands.
class RevokedList {
 public:
  static const size_t CAPACITY = 64;

  void load();
  bool contains(const char* passIdB64) const;
  void add(const char* passIdB64, int64_t expiresAt, int64_t now);

 private:
  struct Entry {
    char id[23];       // "" = empty slot
    uint32_t expires;  // unix seconds; used to recycle slots of already-expired passes
  };
  Entry entries_[CAPACITY];
  uint8_t next_ = 0;

  void persist();
};
