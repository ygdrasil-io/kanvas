package org.skia.tests

import kotlin.Boolean
import kotlin.Int
import kotlin.UByte
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkStream

/**
 * C++ original:
 * ```cpp
 * class DumbStream : public SkStream {
 * public:
 *     DumbStream(const uint8_t* data, size_t n)
 *         : fData(data), fCount(n), fIdx(0) {}
 *     size_t read(void* buffer, size_t size) override {
 *         size_t copyCount = std::min(fCount - fIdx, size);
 *         if (copyCount) {
 *             memcpy(buffer, &fData[fIdx], copyCount);
 *             fIdx += copyCount;
 *         }
 *         return copyCount;
 *     }
 *     bool isAtEnd() const override {
 *         return fCount == fIdx;
 *     }
 *  private:
 *     const uint8_t* fData;
 *     size_t fCount, fIdx;
 * }
 * ```
 */
public open class DumbStream public constructor(
  `data`: UByte?,
  n: ULong,
) : SkStream() {
  /**
   * C++ original:
   * ```cpp
   * const uint8_t* fData
   * ```
   */
  private val fData: Int? = TODO("Initialize fData")

  /**
   * C++ original:
   * ```cpp
   * size_t fCount
   * ```
   */
  private var fCount: Int = TODO("Initialize fCount")

  /**
   * C++ original:
   * ```cpp
   * size_t fCount, fIdx
   * ```
   */
  private var fIdx: Int = TODO("Initialize fIdx")

  /**
   * C++ original:
   * ```cpp
   * size_t read(void* buffer, size_t size) override {
   *         size_t copyCount = std::min(fCount - fIdx, size);
   *         if (copyCount) {
   *             memcpy(buffer, &fData[fIdx], copyCount);
   *             fIdx += copyCount;
   *         }
   *         return copyCount;
   *     }
   * ```
   */
  public override fun read(buffer: Unit?, size: ULong): Int {
    TODO("Implement read")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isAtEnd() const override {
   *         return fCount == fIdx;
   *     }
   * ```
   */
  public override fun isAtEnd(): Boolean {
    TODO("Implement isAtEnd")
  }
}
