#include "revoked_list.h"
#include "storage.h"
#include "config.h"

void RevokedList::load() {
  memset(entries_, 0, sizeof(entries_));
  next_ = 0;
  if (Storage::loadBlob("revoked", entries_, sizeof(entries_)) != sizeof(entries_)) {
    memset(entries_, 0, sizeof(entries_));
  }
  uint8_t savedNext = 0;
  if (Storage::loadBlob("rev_next", &savedNext, sizeof(savedNext)) == sizeof(savedNext) && savedNext < CAPACITY) {
    next_ = savedNext;
  }
}

bool RevokedList::contains(const char* passIdB64) const {
  for (size_t i = 0; i < CAPACITY; i++) {
    if (entries_[i].id[0] != '\0' && strncmp(entries_[i].id, passIdB64, 22) == 0) return true;
  }
  return false;
}

void RevokedList::add(const char* passIdB64, int64_t expiresAt, int64_t now) {
  const uint32_t exp = expiresAt > 0 ? (uint32_t)expiresAt : 0;
  // Already known: refresh expiry only.
  for (size_t i = 0; i < CAPACITY; i++) {
    if (entries_[i].id[0] != '\0' && strncmp(entries_[i].id, passIdB64, 22) == 0) {
      entries_[i].expires = exp;
      persist();
      return;
    }
  }
  // Prefer an empty slot, then an expired one (revocation is moot once QR is expired).
  int slot = -1;
  for (size_t i = 0; i < CAPACITY && slot < 0; i++) {
    if (entries_[i].id[0] == '\0') slot = (int)i;
  }
  if (slot < 0 && now >= MIN_VALID_UNIX) {
    for (size_t i = 0; i < CAPACITY && slot < 0; i++) {
      if ((int64_t)entries_[i].expires < now) slot = (int)i;
    }
  }
  if (slot < 0) {  // full of live entries: overwrite oldest (circular)
    slot = next_;
    next_ = (uint8_t)((next_ + 1) % CAPACITY);
  }
  memset(&entries_[slot], 0, sizeof(Entry));
  memcpy(entries_[slot].id, passIdB64, 22);
  entries_[slot].id[22] = '\0';
  entries_[slot].expires = exp;
  persist();
}

void RevokedList::persist() {
  Storage::saveBlob("revoked", entries_, sizeof(entries_));
  Storage::saveBlob("rev_next", &next_, sizeof(next_));
}
