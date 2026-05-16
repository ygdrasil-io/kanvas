package org.skia.core

import kotlin.CharArray
import kotlin.UInt
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * struct SkPictInfo {
 *     SkPictInfo() : fVersion(~0U) {}
 *
 *     uint32_t getVersion() const {
 *         SkASSERT(fVersion != ~0U);
 *         return fVersion;
 *     }
 *
 *     void setVersion(uint32_t version) {
 *         SkASSERT(version != ~0U);
 *         fVersion = version;
 *     }
 *
 * public:
 *     char        fMagic[8];
 * private:
 *     uint32_t    fVersion;
 * public:
 *     SkRect      fCullRect;
 * }
 * ```
 */
public data class SkPictInfo public constructor(
  /**
   * C++ original:
   * ```cpp
   * char        fMagic[8]
   * ```
   */
  public var fMagic: CharArray,
  /**
   * C++ original:
   * ```cpp
   * uint32_t    fVersion
   * ```
   */
  private var fVersion: UInt,
  /**
   * C++ original:
   * ```cpp
   * SkRect      fCullRect
   * ```
   */
  public var fCullRect: SkRect,
) {
  /**
   * C++ original:
   * ```cpp
   * uint32_t getVersion() const {
   *         SkASSERT(fVersion != ~0U);
   *         return fVersion;
   *     }
   * ```
   */
  public fun getVersion(): UInt {
    TODO("Implement getVersion")
  }

  /**
   * C++ original:
   * ```cpp
   * void setVersion(uint32_t version) {
   *         SkASSERT(version != ~0U);
   *         fVersion = version;
   *     }
   * ```
   */
  public fun setVersion(version: UInt) {
    TODO("Implement setVersion")
  }
}
