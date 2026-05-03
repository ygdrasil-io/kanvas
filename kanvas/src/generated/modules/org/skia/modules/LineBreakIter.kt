package org.skia.modules

import kotlin.Double
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class LineBreakIter {
 *     CTTypesetterRef fTypesetter;
 *     double          fWidth;
 *     CFIndex         fStart;
 *
 * public:
 *     LineBreakIter(CTTypesetterRef ts, SkScalar width) : fTypesetter(ts), fWidth(width) {
 *         fStart = 0;
 *     }
 *
 *     SkUniqueCFRef<CTLineRef> nextLine() {
 *         CFRange stringRange {fStart, CTTypesetterSuggestLineBreak(fTypesetter, fStart, fWidth)};
 *         if (stringRange.length == 0) {
 *             return nullptr;
 *         }
 *         fStart += stringRange.length;
 *         return SkUniqueCFRef<CTLineRef>(CTTypesetterCreateLine(fTypesetter, stringRange));
 *     }
 * }
 * ```
 */
public data class LineBreakIter public constructor(
  /**
   * C++ original:
   * ```cpp
   * CTTypesetterRef fTypesetter
   * ```
   */
  private var fTypesetter: Int,
  /**
   * C++ original:
   * ```cpp
   * double          fWidth
   * ```
   */
  private var fWidth: Double,
  /**
   * C++ original:
   * ```cpp
   * CFIndex         fStart
   * ```
   */
  private var fStart: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * SkUniqueCFRef<CTLineRef> nextLine() {
   *         CFRange stringRange {fStart, CTTypesetterSuggestLineBreak(fTypesetter, fStart, fWidth)};
   *         if (stringRange.length == 0) {
   *             return nullptr;
   *         }
   *         fStart += stringRange.length;
   *         return SkUniqueCFRef<CTLineRef>(CTTypesetterCreateLine(fTypesetter, stringRange));
   *     }
   * ```
   */
  public fun nextLine(): Int {
    TODO("Implement nextLine")
  }
}
