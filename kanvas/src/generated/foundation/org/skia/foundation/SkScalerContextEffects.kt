package org.skia.foundation

/**
 * C++ original:
 * ```cpp
 * struct SkScalerContextEffects {
 *     SkScalerContextEffects() : fPathEffect(nullptr), fMaskFilter(nullptr) {}
 *     SkScalerContextEffects(SkPathEffect* pe, SkMaskFilter* mf)
 *             : fPathEffect(pe), fMaskFilter(mf) {}
 *     explicit SkScalerContextEffects(const SkPaint& paint)
 *             : fPathEffect(paint.getPathEffect())
 *             , fMaskFilter(paint.getMaskFilter()) {}
 *
 *     SkPathEffect*   fPathEffect;
 *     SkMaskFilter*   fMaskFilter;
 * }
 * ```
 */
public data class SkScalerContextEffects public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkPathEffect*   fPathEffect
   * ```
   */
  public var fPathEffect: SkPathEffect?,
  /**
   * C++ original:
   * ```cpp
   * SkMaskFilter*   fMaskFilter
   * ```
   */
  public var fMaskFilter: SkMaskFilter?,
)
