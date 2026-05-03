package org.skia.modules

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGFeTurbulenceBaseFrequency {
 * public:
 *     SkSVGFeTurbulenceBaseFrequency() : fFreqX(0), fFreqY(0) {}
 *     SkSVGFeTurbulenceBaseFrequency(SkSVGNumberType freqX, SkSVGNumberType freqY)
 *             : fFreqX(freqX), fFreqY(freqY) {}
 *
 *     SkSVGNumberType freqX() const { return fFreqX; }
 *     SkSVGNumberType freqY() const { return fFreqY; }
 *
 * private:
 *     SkSVGNumberType fFreqX;
 *     SkSVGNumberType fFreqY;
 * }
 * ```
 */
public data class SkSVGFeTurbulenceBaseFrequency public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkSVGNumberType fFreqX
   * ```
   */
  private var fFreqX: Int,
  /**
   * C++ original:
   * ```cpp
   * SkSVGNumberType fFreqY
   * ```
   */
  private var fFreqY: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * SkSVGNumberType freqX() const { return fFreqX; }
   * ```
   */
  public fun freqX(): Int {
    TODO("Implement freqX")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSVGNumberType freqY() const { return fFreqY; }
   * ```
   */
  public fun freqY(): Int {
    TODO("Implement freqY")
  }
}
