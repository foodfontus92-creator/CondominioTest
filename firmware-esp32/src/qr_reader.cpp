#include "qr_reader.h"
#include "config.h"

namespace {
char buf[QR_LINE_BUF];
size_t len = 0;
bool overflow = false;
uint32_t lastByteMs = 0;
const uint32_t IDLE_FLUSH_MS = 150;

bool emit(char* out, size_t outSize) {
  bool produced = false;
  if (overflow) {
    if (outSize >= 2) { out[0] = '!'; out[1] = '\0'; produced = true; }
  } else if (len > 0 && outSize > len) {
    memcpy(out, buf, len);
    out[len] = '\0';
    produced = true;
  }
  len = 0;
  overflow = false;
  return produced;
}
}  // namespace

namespace QrReader {

void begin() {
  Serial2.setRxBufferSize(512);
  Serial2.begin(QR_BAUD, SERIAL_8N1, PIN_QR_RX, PIN_QR_TX);
}

bool poll(char* out, size_t outSize) {
  const uint32_t now = millis();
  while (Serial2.available() > 0) {
    const int c = Serial2.read();
    lastByteMs = now;
    if (c == '\r' || c == '\n') {
      if (emit(out, outSize)) return true;
      continue;
    }
    if (len < sizeof(buf) - 1) buf[len++] = (char)c;
    else overflow = true;
  }
  // Readers configured without terminator: flush after an idle gap.
  if ((len > 0 || overflow) && (uint32_t)(now - lastByteMs) >= IDLE_FLUSH_MS) {
    return emit(out, outSize);
  }
  return false;
}

}  // namespace QrReader
