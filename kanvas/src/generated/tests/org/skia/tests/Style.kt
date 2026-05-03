package org.skia.tests

import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathEffect
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * struct Style {
 *     Style(SkPaint::Style paintStyle, sk_sp<SkPathEffect> pe = sk_sp<SkPathEffect>())
 *         : fPaintStyle(paintStyle)
 *         , fPathEffect(std::move(pe)) {}
 *     SkPaint::Style      fPaintStyle;
 *     sk_sp<SkPathEffect> fPathEffect;
 * }
 * ```
 */
public data class Style public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkPaint::Style      fPaintStyle
   * ```
   */
  public var fPaintStyle: SkPaint.Style,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkPathEffect> fPathEffect
   * ```
   */
  public var fPathEffect: SkSp<SkPathEffect>,
)
