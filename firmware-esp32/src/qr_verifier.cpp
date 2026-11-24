#include "qr_verifier.h"
#include <mbedtls/md.h>
#include "config.h"
#include "util.h"

namespace {

bool hmacSha256(const uint8_t* key, size_t keyLen, const uint8_t* msg, size_t msgLen, uint8_t out[32]) {
  const mbedtls_md_info_t* info = mbedtls_md_info_from_type(MBEDTLS_MD_SHA256);
  if (info == nullptr) return false;
  return mbedtls_md_hmac(info, key, keyLen, msg, msgLen, out) == 0;
}

}  // namespace

const char* qrVerdictToResult(QrVerdict v) {
  switch (v) {
    case QrVerdict::GRANTED:             return "GRANTED";
    case QrVerdict::DENIED_WRONG_CONDO:  return "DENIED_WRONG_CONDO";
    case QrVerdict::DENIED_EXPIRED:      return "DENIED_EXPIRED";
    case QrVerdict::DENIED_REVOKED:      return "DENIED_REVOKED";
    case QrVerdict::DENIED_INVALID_HMAC: return "DENIED_INVALID_HMAC";
    default:                             return "ERROR";
  }
}

QrCheck verifyCompactQr(const char* payload, int64_t nowUnix, bool timeValid,
                        const Credentials& creds, const RevokedList& revoked) {
  QrCheck r;
  memset(&r, 0, sizeof(r));
  r.verdict = QrVerdict::DENIED_INVALID_HMAC;
  if (payload == nullptr) return r;

  // 1. Length gate: payload must be < 120 chars.
  const size_t len = strnlen(payload, QR_MAX_PAYLOAD_LEN + 1);
  if (len == 0 || len >= QR_MAX_PAYLOAD_LEN) return r;

  // 2. Exactly 5 tokens / 4 pipes.
  size_t sep[4];
  size_t pipes = 0;
  for (size_t i = 0; i < len; i++) {
    if (payload[i] == '|') {
      if (pipes == 4) return r;
      sep[pipes++] = i;
    }
  }
  if (pipes != 4) return r;

  const char* tok[5];
  size_t tokLen[5];
  size_t start = 0;
  for (int k = 0; k < 5; k++) {
    const size_t end = (k < 4) ? sep[k] : len;
    tok[k] = payload + start;
    tokLen[k] = end - start;
    start = end + 1;
  }

  // 3. Structural validation (same rules as backend CompactQrCryptoService.parseQrPayload).
  for (int k = 0; k < 3; k++) {
    if (tokLen[k] != 22) return r;
    for (size_t i = 0; i < 22; i++) {
      if (!Util::isBase64UrlChar(tok[k][i])) return r;
    }
  }
  if (tokLen[3] < 1 || tokLen[3] > 10) return r;
  int64_t expUnix = 0;
  for (size_t i = 0; i < tokLen[3]; i++) {
    if (tok[3][i] < '0' || tok[3][i] > '9') return r;
    expUnix = expUnix * 10 + (tok[3][i] - '0');
  }
  if (expUnix <= 0) return r;
  if (tokLen[4] != (size_t)(HMAC_TRUNC_BYTES * 2)) return r;
  char macProvided[HMAC_TRUNC_BYTES * 2 + 1];
  for (size_t i = 0; i < tokLen[4]; i++) {
    if (!Util::isHexChar(tok[4][i])) return r;
    macProvided[i] = Util::toLowerAscii(tok[4][i]);
  }
  macProvided[tokLen[4]] = '\0';

  memcpy(r.passIdB64, tok[0], 22);
  memcpy(r.unitIdB64, tok[2], 22);
  r.expUnix = expUnix;
  r.parsed = true;

  // 4. Tenant check: token[1] must be this condominium.
  if (memcmp(tok[1], creds.condoIdB64, 22) != 0) {
    r.verdict = QrVerdict::DENIED_WRONG_CONDO;
    return r;
  }

  // 5. Expiry (needs trustworthy clock; fail closed otherwise).
  if (!timeValid) {
    r.verdict = QrVerdict::ERROR_NO_TIME;
    return r;
  }
  if (nowUnix > expUnix) {
    r.verdict = QrVerdict::DENIED_EXPIRED;
    return r;
  }

  // 6. Revocation list (pushed by REVOKE_PASS, stored in NVS).
  if (revoked.contains(r.passIdB64)) {
    r.verdict = QrVerdict::DENIED_REVOKED;
    return r;
  }

  // 7. HMAC:
  //    unit_secret = HMAC-SHA256(condo_key, UTF8(UNIT_ID_B64))
  //    mac         = HMAC-SHA256(unit_secret, "PASS|CONDO|UNIT|EXP")[0..11]
  uint8_t unitSecret[32];
  uint8_t digest[32];
  if (!hmacSha256(creds.condoKey, sizeof(creds.condoKey), (const uint8_t*)tok[2], 22, unitSecret) ||
      !hmacSha256(unitSecret, sizeof(unitSecret), (const uint8_t*)payload, sep[3], digest)) {
    r.verdict = QrVerdict::ERROR_CRYPTO;
    return r;
  }
  char macExpected[HMAC_TRUNC_BYTES * 2 + 1];
  Util::bytesToHexLower(digest, HMAC_TRUNC_BYTES, macExpected);

  const bool ok = Util::constantTimeEquals(macExpected, macProvided, HMAC_TRUNC_BYTES * 2);
  memset(unitSecret, 0, sizeof(unitSecret));
  memset(digest, 0, sizeof(digest));

  r.verdict = ok ? QrVerdict::GRANTED : QrVerdict::DENIED_INVALID_HMAC;
  return r;
}
