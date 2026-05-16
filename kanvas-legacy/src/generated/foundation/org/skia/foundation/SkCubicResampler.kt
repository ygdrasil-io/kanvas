package org.skia.foundation

import kotlin.Float

/**
 * C++ original:
 * ```cpp
 * struct SkCubicResampler {
 *     float B, C;
 *
 *     // Historic default for kHigh_SkFilterQuality
 *     static constexpr SkCubicResampler Mitchell() { return {1/3.0f, 1/3.0f}; }
 *     static constexpr SkCubicResampler CatmullRom() { return {0.0f, 1/2.0f}; }
 * }
 * ```
 */
public data class SkCubicResampler public constructor(
  /**
   * C++ original:
   * ```cpp
   * float B
   * ```
   */
  public var b: Float,
  /**
   * C++ original:
   * ```cpp
   * float B, C
   * ```
   */
  public var c: Float,
) {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static constexpr SkCubicResampler Mitchell() { return {1/3.0f, 1/3.0f}; }
     * ```
     */
    public fun mitchell(): SkCubicResampler {
      TODO("Implement mitchell")
    }

    /**
     * C++ original:
     * ```cpp
     * static constexpr SkCubicResampler CatmullRom() { return {0.0f, 1/2.0f}; }
     * ```
     */
    public fun catmullRom(): SkCubicResampler {
      TODO("Implement catmullRom")
    }
  }
}
