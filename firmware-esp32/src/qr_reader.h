#pragma once
#include <Arduino.h>

// Non-blocking line reader for a GM65 (or similar) barcode module on UART2.
namespace QrReader {

void begin();

// Returns true once per complete scan (terminated by CR/LF, or idle gap).
// Oversized scans are returned as "!" so the verifier rejects them.
bool poll(char* out, size_t outSize);

}  // namespace QrReader
