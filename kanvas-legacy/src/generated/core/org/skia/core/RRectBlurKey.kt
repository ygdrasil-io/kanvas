package org.skia.core

import kotlin.Int
import org.skia.foundation.SkRRect
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * struct RRectBlurKey : public SkResourceCache::Key {
 * public:
 *     RRectBlurKey(SkScalar sigma, const SkRRect& rrect, SkBlurStyle style)
 *         : fSigma(sigma)
 *         , fStyle(style)
 *         , fRRect(rrect)
 *     {
 *         this->init(&gRRectBlurKeyNamespaceLabel, 0,
 *                    sizeof(fSigma) + sizeof(fStyle) + sizeof(fRRect));
 *     }
 *
 *     SkScalar   fSigma;
 *     int32_t    fStyle;
 *     SkRRect    fRRect;
 * }
 * ```
 */
public open class RRectBlurKey public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkScalar   fSigma
   * ```
   */
  public var fSigma: SkScalar,
  /**
   * C++ original:
   * ```cpp
   * int32_t    fStyle
   * ```
   */
  public var fStyle: Int,
  /**
   * C++ original:
   * ```cpp
   * SkRRect    fRRect
   * ```
   */
  public var fRRect: SkRRect,
) : SkResourceCache.Key(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * RRectBlurKey(SkScalar sigma, const SkRRect& rrect, SkBlurStyle style)
   *         : fSigma(sigma)
   *         , fStyle(style)
   *         , fRRect(rrect)
   *     {
   *         this->init(&gRRectBlurKeyNamespaceLabel, 0,
   *                    sizeof(fSigma) + sizeof(fStyle) + sizeof(fRRect));
   *     }
   * ```
   */
  public constructor(
    sigma: SkScalar,
    rrect: SkRRect,
    style: SkBlurStyle,
  ) : this() {
    TODO("Implement constructor")
  }
}
