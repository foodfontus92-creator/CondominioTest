#pragma once
#include <Arduino.h>

// Fixed-size FIFO. When full, the oldest element is dropped.
template <typename T, size_t N>
class RingQueue {
 public:
  bool empty() const { return count_ == 0; }
  size_t size() const { return count_; }
  void push(const T& v) {
    if (count_ == N) { head_ = (head_ + 1) % N; count_--; }
    items_[(head_ + count_) % N] = v;
    count_++;
  }
  const T& front() const { return items_[head_]; }
  void pop() {
    if (count_ == 0) return;
    head_ = (head_ + 1) % N;
    count_--;
  }

 private:
  T items_[N];
  size_t head_ = 0;
  size_t count_ = 0;
};
