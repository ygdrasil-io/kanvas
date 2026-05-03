package org.skia.gpu

import kotlin.Char
import kotlin.String

/**
 * C++ original:
 * ```cpp
 * class TextureAndSampler {
 * public:
 *     constexpr TextureAndSampler(const char* name) : fName(name) {}
 *
 *     const char* name() const { return fName; }
 *
 * private:
 *     const char* fName;
 * }
 * ```
 */
public data class TextureAndSampler public constructor(
  /**
   * C++ original:
   * ```cpp
   * const char* fName
   * ```
   */
  private val fName: String?,
) {
  /**
   * C++ original:
   * ```cpp
   * const char* name() const { return fName; }
   * ```
   */
  public fun name(): Char {
    TODO("Implement name")
  }
}
