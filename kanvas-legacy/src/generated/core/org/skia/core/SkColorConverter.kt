package org.skia.core

import kotlin.Int
import org.skia.foundation.SkSpan
import undefined.SkColor4f

/**
 * C++ original:
 * ```cpp
 * struct SkColorConverter {
 * //    SkColorConverter() {}
 *     SkColorConverter(SkSpan<const SkColor>);
 *
 *     SkSpan<SkColor4f> colors4f() { return fColors4f; }
 *
 * private:
 *     skia_private::STArray<2, SkColor4f> fColors4f;
 * }
 * ```
 */
public data class SkColorConverter public constructor(
  /**
   * C++ original:
   * ```cpp
   * skia_private::STArray<2, SkColor4f> fColors4f
   * ```
   */
  private var fColors4f: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * SkSpan<SkColor4f> colors4f() { return fColors4f; }
   * ```
   */
  public fun colors4f(): SkSpan<SkColor4f> {
    TODO("Implement colors4f")
  }
}
