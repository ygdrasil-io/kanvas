package org.skia.gpu

import kotlin.Int
import org.skia.math.SkIRect
import org.skia.math.SkIVector

/**
 * C++ original:
 * ```cpp
 * class Scissor {
 * public:
 *     explicit Scissor(const SkIRect& rect) : fRect(rect) {}
 *
 *     SkIRect getRect(const SkIVector& replayTranslation, const SkIRect& replayClip) const {
 *         SkIRect rect = fRect.makeOffset(replayTranslation);
 *         if (!rect.intersect(replayClip)) {
 *             rect.setEmpty();
 *         }
 *         return rect;
 *     }
 *
 * private:
 *     const SkIRect fRect;
 * }
 * ```
 */
public data class Scissor public constructor(
  /**
   * C++ original:
   * ```cpp
   * const SkIRect fRect
   * ```
   */
  private val fRect: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * SkIRect getRect(const SkIVector& replayTranslation, const SkIRect& replayClip) const {
   *         SkIRect rect = fRect.makeOffset(replayTranslation);
   *         if (!rect.intersect(replayClip)) {
   *             rect.setEmpty();
   *         }
   *         return rect;
   *     }
   * ```
   */
  public fun getRect(replayTranslation: SkIVector, replayClip: SkIRect): Int {
    TODO("Implement getRect")
  }
}
