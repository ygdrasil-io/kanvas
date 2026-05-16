package org.skia.core

import org.skia.foundation.SkBitmap
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class BitmapDevicePainter {
 * public:
 *     BitmapDevicePainter() = default;
 *     BitmapDevicePainter(const BitmapDevicePainter&) = default;
 *     virtual ~BitmapDevicePainter() = default;
 *
 *     virtual void paintMasks(SkZip<const SkGlyph*, SkPoint> accepted,
 *                             const SkPaint& paint) const = 0;
 *     virtual void drawBitmap(const SkBitmap&,
 *                             const SkMatrix&,
 *                             const SkRect* dstOrNull,
 *                             const SkSamplingOptions&,
 *                             const SkPaint&) const = 0;
 * }
 * ```
 */
public abstract class BitmapDevicePainter public constructor() {
  /**
   * C++ original:
   * ```cpp
   * BitmapDevicePainter() = default
   * ```
   */
  public constructor(param0: BitmapDevicePainter) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void paintMasks(SkZip<const SkGlyph*, SkPoint> accepted,
   *                             const SkPaint& paint) const = 0
   * ```
   */
  public abstract fun paintMasks(accepted: SkZip<SkGlyph?, SkPoint>, paint: SkPaint)

  /**
   * C++ original:
   * ```cpp
   * virtual void drawBitmap(const SkBitmap&,
   *                             const SkMatrix&,
   *                             const SkRect* dstOrNull,
   *                             const SkSamplingOptions&,
   *                             const SkPaint&) const = 0
   * ```
   */
  public abstract fun drawBitmap(
    param0: SkBitmap,
    param1: SkMatrix,
    dstOrNull: SkRect?,
    param3: SkSamplingOptions,
    param4: SkPaint,
  )
}
