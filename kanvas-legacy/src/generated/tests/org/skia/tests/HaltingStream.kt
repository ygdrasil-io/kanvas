package org.skia.tests

import kotlin.Int
import org.skia.foundation.SkStream

/**
 * C++ original:
 * ```cpp
 * class HaltingStream : public SkStream {
 * public:
 *     HaltingStream(sk_sp<SkData> data, size_t initialLimit)
 *         : fTotalSize(data->size())
 *         , fLimit(initialLimit)
 *         , fStream(std::move(data))
 *     {}
 *
 *     void addNewData(size_t extra) {
 *         fLimit = std::min(fTotalSize, fLimit + extra);
 *     }
 *
 *     size_t read(void* buffer, size_t size) override {
 *         if (fStream.getPosition() + size > fLimit) {
 *             size = fLimit - fStream.getPosition();
 *         }
 *
 *         return fStream.read(buffer, size);
 *     }
 *
 *     bool isAtEnd() const override {
 *         return fStream.isAtEnd();
 *     }
 *
 *     bool hasLength() const override { return true; }
 *     size_t getLength() const override { return fLimit; }
 *
 *     bool hasPosition() const override { return true; }
 *     size_t getPosition() const override { return fStream.getPosition(); }
 *     bool rewind() override { return fStream.rewind(); }
 *     bool move(long offset) override { return fStream.move(offset); }
 *     bool seek(size_t position) override { return fStream.seek(position); }
 *
 *     bool isAllDataReceived() const { return fLimit == fTotalSize; }
 *
 * private:
 *     const size_t    fTotalSize;
 *     size_t          fLimit;
 *     SkMemoryStream  fStream;
 * }
 * ```
 */
public open class HaltingStream : SkStream() {
  /**
   * C++ original:
   * ```cpp
   * HaltingStream(sk_sp<SkData> data, size_t initialLimit)
   *         : fTotalSize(data->size())
   *         , fLimit(initialLimit)
   * ```
   */
  public fun fLimit(param0: Int): HaltingStream {
    TODO("Implement fLimit")
  }

  /**
   * C++ original:
   * ```cpp
   * HaltingStream(sk_sp<SkData> data, size_t initialLimit)
   *         : fTotalSize(data->size())
   *         , fLimit(initialLimit)
   *         , fStream(std::move(data))
   * ```
   */
  public fun fStream(param0: Int): HaltingStream {
    TODO("Implement fStream")
  }
}
