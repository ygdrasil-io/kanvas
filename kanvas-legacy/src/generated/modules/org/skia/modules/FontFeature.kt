package org.skia.modules

import kotlin.Any
import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct FontFeature {
 *     FontFeature(SkString name, int value) : fName(std::move(name)), fValue(value) {}
 *     bool operator==(const FontFeature& that) const {
 *         return fName == that.fName && fValue == that.fValue;
 *     }
 *     SkString fName;
 *     int fValue;
 * }
 * ```
 */
public data class FontFeature public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkString fName
   * ```
   */
  public var fName: Int,
  /**
   * C++ original:
   * ```cpp
   * int fValue
   * ```
   */
  public var fValue: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * bool operator==(const FontFeature& that) const {
   *         return fName == that.fName && fValue == that.fValue;
   *     }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }
}
