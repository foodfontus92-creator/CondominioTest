#pragma once
#include <Arduino.h>

namespace Util {

// hexLen must be exactly 2 * outLen. Accepts upper/lower case.
bool hexToBytes(const char* hex, size_t hexLen, uint8_t* out, size_t outLen);

// out must hold 2*len+1 bytes. Lowercase.
void bytesToHexLower(const uint8_t* in, size_t len, char* out);

// Canonical 36-char UUID -> 16 bytes (big-endian, same order as Java ByteBuffer MSB|LSB).
bool uuidToBytes(const char* uuid, uint8_t out[16]);

// RFC 4648 section 5, no padding. 16 bytes -> 22 chars (+ NUL).
void bytes16ToBase64Url(const uint8_t in[16], char out[23]);
bool uuidToBase64Url(const char* uuid, char out[23]);

bool isBase64UrlChar(char c);
bool isHexChar(char c);
char toLowerAscii(char c);

// Constant-time comparison of `len` bytes.
bool constantTimeEquals(const char* a, const char* b, size_t len);

}  // namespace Util
