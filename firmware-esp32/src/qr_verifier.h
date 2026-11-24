#pragma once
#include <Arduino.h>
#include "storage.h"
#include "revoked_list.h"

// Offline verification of the compact QR (ARCHITECTURE.md section 5):
//   PASS_ID|CONDO_ID|UNIT_ID|EXP_UNIX_TIMESTAMP|HMAC_SHA256_TRUNCATED
enum class QrVerdict : uint8_t {
  GRANTED,
  DENIED_WRONG_CONDO,
  DENIED_EXPIRED,
  DENIED_REVOKED,
  DENIED_INVALID_HMAC,  // also used for malformed payloads
  ERROR_NO_TIME,        // clock not trustworthy: fail closed
  ERROR_CRYPTO
};

struct QrCheck {
  QrVerdict verdict;
  bool parsed;            // true if ids below are filled (structurally valid payload)
  char passIdB64[23];
  char unitIdB64[23];
  int64_t expUnix;
};

// access_logs.result string (matches backend AccessResult enum).
const char* qrVerdictToResult(QrVerdict v);

QrCheck verifyCompactQr(const char* payload, int64_t nowUnix, bool timeValid,
                        const Credentials& creds, const RevokedList& revoked);
