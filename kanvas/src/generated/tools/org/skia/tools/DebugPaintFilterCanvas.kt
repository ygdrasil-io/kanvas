package org.skia.tools

import kotlin.Boolean
import org.skia.core.SkCanvas
import org.skia.core.SkPicture
import org.skia.foundation.SkPaint
import org.skia.math.SkMatrix
import org.skia.utils.SkPaintFilterCanvas

/**
 * C++ original:
 * ```cpp
 * class DebugPaintFilterCanvas : public SkPaintFilterCanvas {
 * public:
 *     DebugPaintFilterCanvas(SkCanvas* canvas) : INHERITED(canvas) {}
 *
 * protected:
 *     bool onFilter(SkPaint& paint) const override {
 *         paint.setColor(SK_ColorRED);
 *         paint.setAlpha(0x08);
 *         paint.setBlendMode(SkBlendMode::kSrcOver);
 *         return true;
 *     }
 *
 *     void onDrawPicture(const SkPicture* picture,
 *                        const SkMatrix*  matrix,
 *                        const SkPaint*   paint) override {
 *         // We need to replay the picture onto this canvas in order to filter its internal paints.
 *         this->SkCanvas::onDrawPicture(picture, matrix, paint);
 *     }
 *
 * private:
 *
 *     using INHERITED = SkPaintFilterCanvas;
 * }
 * ```
 */
public open class DebugPaintFilterCanvas public constructor(
  canvas: SkCanvas?,
) : SkPaintFilterCanvas(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * bool onFilter(SkPaint& paint) const override {
   *         paint.setColor(SK_ColorRED);
   *         paint.setAlpha(0x08);
   *         paint.setBlendMode(SkBlendMode::kSrcOver);
   *         return true;
   *     }
   * ```
   */
  protected override fun onFilter(paint: SkPaint): Boolean {
    TODO("Implement onFilter")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDrawPicture(const SkPicture* picture,
   *                        const SkMatrix*  matrix,
   *                        const SkPaint*   paint) override {
   *         // We need to replay the picture onto this canvas in order to filter its internal paints.
   *         this->SkCanvas::onDrawPicture(picture, matrix, paint);
   *     }
   * ```
   */
  protected override fun onDrawPicture(
    picture: SkPicture?,
    matrix: SkMatrix?,
    paint: SkPaint?,
  ) {
    TODO("Implement onDrawPicture")
  }
}
