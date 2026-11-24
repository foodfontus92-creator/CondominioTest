#include "util.h"

namespace Util {

static int hexVal(char c) {
  if (c >= '0' && c <= '9') return c - '0';
  if (c >= 'a' && c <= 'f') return c - 'a' + 10;
  if (c >= 'A' && c <= 'F') return c - 'A' + 10;
  return -1;
}

bool isHexChar(char c) { return hexVal(c) >= 0; }

char toLowerAscii(char c) { return (c >= 'A' && c <= 'Z') ? (char)(c + 32) : c; }

bool isBase64UrlChar(char c) {
  return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_';
}

bool hexToBytes(const char* hex, size_t hexLen, uint8_t* out, size_t outLen) {
  if (!hex || hexLen != outLen * 2) return false;
  for (size_t i = 0; i < outLen; i++) {
    int hi = hexVal(hex[2 * i]);
    int lo = hexVal(hex[2 * i + 1]);
    if (hi < 0 || lo < 0) return false;
    out[i] = (uint8_t)((hi << 4) | lo);
  }
  return true;
}

void bytesToHexLower(const uint8_t* in, size_t len, char* out) {
  static const char digits[] = "0123456789abcdef";
  for (size_t i = 0; i < len; i++) {
    out[2 * i] = digits[in[i] >> 4];
    out[2 * i + 1] = digits[in[i] & 0x0F];
  }
  out[2 * len] = '\0';
}

bool uuidToBytes(const char* uuid, uint8_t out[16]) {
  if (!uuid || strlen(uuid) != 36) return false;
  size_t nibbles = 0;
  for (size_t i = 0; i < 36; i++) {
    char c = uuid[i];
    if (i == 8 || i == 13 || i == 18 || i == 23) {
      if (c != '-') return false;
      continue;
    }
    int v = hexVal(c);
    if (v < 0) return false;
    if (nibbles % 2 == 0) out[nibbles / 2] = (uint8_t)(v << 4);
    else out[nibbles / 2] |= (uint8_t)v;
    nibbles++;
  }
  return nibbles == 32;
}

void bytes16ToBase64Url(const uint8_t in[16], char out[23]) {
  static const char T[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
  size_t o = 0;
  for (int i = 0; i < 15; i += 3) {
    uint32_t v = ((uint32_t)in[i] << 16) | ((uint32_t)in[i + 1] << 8) | in[i + 2];
    out[o++] = T[(v >> 18) & 63];
    out[o++] = T[(v >> 12) & 63];
    out[o++] = T[(v >> 6) & 63];
    out[o++] = T[v & 63];
  }
  uint32_t last = (uint32_t)in[15] << 16;
  out[o++] = T[(last >> 18) & 63];
  out[o++] = T[(last >> 12) & 63];
  out[o] = '\0';
}

bool uuidToBase64Url(const char* uuid, char out[23]) {
  uint8_t raw[16];
  if (!uuidToBytes(uuid, raw)) return false;
  bytes16ToBase64Url(raw, out);
  return true;
}

bool constantTimeEquals(const char* a, const char* b, size_t len) {
  uint8_t diff = 0;
  for (size_t i = 0; i < len; i++) diff |= (uint8_t)(a[i] ^ b[i]);
  return diff == 0;
}

}  // namespace Util
