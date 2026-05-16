package org.skia.core

import kotlin.Int
import kotlin.ULong
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSp
import org.skia.foundation.SkTypeface

/**
 * C++ original:
 * ```cpp
 * class SkTypefacePlayback {
 * public:
 *     SkTypefacePlayback() : fCount(0), fArray(nullptr) {}
 *     ~SkTypefacePlayback() = default;
 *
 *     void setCount(size_t count);
 *
 *     size_t count() const { return fCount; }
 *
 *     sk_sp<SkTypeface>& operator[](size_t index) {
 *         SkASSERT(index < fCount);
 *         return fArray[index];
 *     }
 *
 *     void setupBuffer(SkReadBuffer& buffer) const {
 *         buffer.setTypefaceArray(fArray.get(), fCount);
 *     }
 *
 * protected:
 *     size_t fCount;
 *     std::unique_ptr<sk_sp<SkTypeface>[]> fArray;
 * }
 * ```
 */
public data class SkTypefacePlayback public constructor(
  /**
   * C++ original:
   * ```cpp
   * size_t fCount
   * ```
   */
  protected var fCount: ULong,
  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<sk_sp<SkTypeface>[]> fArray
   * ```
   */
  protected var fArray: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * void SkTypefacePlayback::setCount(size_t count) {
   *     fCount = count;
   *     fArray = std::make_unique<sk_sp<SkTypeface>[]>(count);
   * }
   * ```
   */
  public fun setCount(count: ULong) {
    TODO("Implement setCount")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t count() const { return fCount; }
   * ```
   */
  public fun count(): ULong {
    TODO("Implement count")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface>& operator[](size_t index) {
   *         SkASSERT(index < fCount);
   *         return fArray[index];
   *     }
   * ```
   */
  public operator fun `get`(index: ULong): SkSp<SkTypeface> {
    TODO("Implement get")
  }

  /**
   * C++ original:
   * ```cpp
   * void setupBuffer(SkReadBuffer& buffer) const {
   *         buffer.setTypefaceArray(fArray.get(), fCount);
   *     }
   * ```
   */
  public fun setupBuffer(buffer: SkReadBuffer) {
    TODO("Implement setupBuffer")
  }
}
