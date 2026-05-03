package org.skia.core

import kotlin.Int
import org.skia.foundation.SkFlattenableFactory
import org.skia.foundation.SkReadBuffer

/**
 * C++ original:
 * ```cpp
 * class SkFactoryPlayback {
 * public:
 *     SkFactoryPlayback(int count) : fCount(count) { fArray = new SkFlattenable::Factory[count]; }
 *
 *     ~SkFactoryPlayback() { delete[] fArray; }
 *
 *     SkFlattenable::Factory* base() const { return fArray; }
 *
 *     void setupBuffer(SkReadBuffer& buffer) const {
 *         buffer.setFactoryPlayback(fArray, fCount);
 *     }
 *
 * private:
 *     int fCount;
 *     SkFlattenable::Factory* fArray;
 * }
 * ```
 */
public data class SkFactoryPlayback public constructor(
  /**
   * C++ original:
   * ```cpp
   * int fCount
   * ```
   */
  private var fCount: Int,
  /**
   * C++ original:
   * ```cpp
   * SkFlattenable::Factory* fArray
   * ```
   */
  private var fArray: SkFlattenableFactory?,
) {
  /**
   * C++ original:
   * ```cpp
   * SkFlattenable::Factory* base() const { return fArray; }
   * ```
   */
  public fun base(): SkFlattenableFactory {
    TODO("Implement base")
  }

  /**
   * C++ original:
   * ```cpp
   * void setupBuffer(SkReadBuffer& buffer) const {
   *         buffer.setFactoryPlayback(fArray, fCount);
   *     }
   * ```
   */
  public fun setupBuffer(buffer: SkReadBuffer) {
    TODO("Implement setupBuffer")
  }
}
