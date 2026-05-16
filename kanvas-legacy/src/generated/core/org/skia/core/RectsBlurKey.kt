package org.skia.core

import kotlin.Array
import kotlin.Int
import org.skia.foundation.SkSpan
import org.skia.math.SkRect
import org.skia.math.SkScalar
import org.skia.math.SkSize

/**
 * C++ original:
 * ```cpp
 * struct RectsBlurKey : public SkResourceCache::Key {
 * public:
 *     RectsBlurKey(SkScalar sigma, SkBlurStyle style, SkSpan<const SkRect> rects)
 *             : fSigma(sigma), fStyle(style) {
 *         SkASSERT(rects.size() == 1 || rects.size() == 2);
 *         SkIRect ir;
 *         rects[0].roundOut(&ir);
 *         fSizes[0] = SkSize{rects[0].width(), rects[0].height()};
 *         if (rects.size() == 2) {
 *             fSizes[1] = SkSize{rects[1].width(), rects[1].height()};
 *             fSizes[2] = SkSize{rects[0].x() - rects[1].x(), rects[0].y() - rects[1].y()};
 *         } else {
 *             fSizes[1] = SkSize{0, 0};
 *             fSizes[2] = SkSize{0, 0};
 *         }
 *         fSizes[3] = SkSize{rects[0].x() - ir.x(), rects[0].y() - ir.y()};
 *
 *         this->init(&gRectsBlurKeyNamespaceLabel, 0,
 *                    sizeof(fSigma) + sizeof(fStyle) + sizeof(fSizes));
 *     }
 *
 *     SkScalar    fSigma;
 *     int32_t     fStyle;
 *     SkSize      fSizes[4];
 * }
 * ```
 */
public open class RectsBlurKey public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkScalar    fSigma
   * ```
   */
  public var fSigma: SkScalar,
  /**
   * C++ original:
   * ```cpp
   * int32_t     fStyle
   * ```
   */
  public var fStyle: Int,
  /**
   * C++ original:
   * ```cpp
   * SkSize      fSizes[4]
   * ```
   */
  public var fSizes: Array<SkSize>,
) : SkResourceCache.Key(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * RectsBlurKey(SkScalar sigma, SkBlurStyle style, SkSpan<const SkRect> rects)
   *             : fSigma(sigma), fStyle(style) {
   *         SkASSERT(rects.size() == 1 || rects.size() == 2);
   *         SkIRect ir;
   *         rects[0].roundOut(&ir);
   *         fSizes[0] = SkSize{rects[0].width(), rects[0].height()};
   *         if (rects.size() == 2) {
   *             fSizes[1] = SkSize{rects[1].width(), rects[1].height()};
   *             fSizes[2] = SkSize{rects[0].x() - rects[1].x(), rects[0].y() - rects[1].y()};
   *         } else {
   *             fSizes[1] = SkSize{0, 0};
   *             fSizes[2] = SkSize{0, 0};
   *         }
   *         fSizes[3] = SkSize{rects[0].x() - ir.x(), rects[0].y() - ir.y()};
   *
   *         this->init(&gRectsBlurKeyNamespaceLabel, 0,
   *                    sizeof(fSigma) + sizeof(fStyle) + sizeof(fSizes));
   *     }
   * ```
   */
  public constructor(
    sigma: SkScalar,
    style: SkBlurStyle,
    rects: SkSpan<SkRect>,
  ) : this() {
    TODO("Implement constructor")
  }
}
