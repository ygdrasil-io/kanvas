package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class ImageFiltersStrokedGM : public GM {
 * public:
 *     ImageFiltersStrokedGM() {
 *         this->setBGColor(0x00000000);
 *     }
 *
 * protected:
 *     SkString getName() const override { return SkString("imagefiltersstroked"); }
 *
 *     SkISize getISize() override { return SkISize::Make(860, 500); }
 *
 *     static void draw_circle(SkCanvas* canvas, const SkRect& r, const SkPaint& paint) {
 *         canvas->drawCircle(r.centerX(), r.centerY(),
 *                            r.width() * 2 / 5, paint);
 *     }
 *
 *     static void draw_line(SkCanvas* canvas, const SkRect& r, const SkPaint& paint) {
 *         canvas->drawLine(r.fLeft, r.fBottom, r.fRight, r.fTop, paint);
 *     }
 *
 *     static void draw_rect(SkCanvas* canvas, const SkRect& r, const SkPaint& paint) {
 *         canvas->drawRect(r, paint);
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         void (*drawProc[])(SkCanvas*, const SkRect&, const SkPaint&) = {
 *             draw_line, draw_rect, draw_circle,
 *         };
 *
 *         canvas->clear(SK_ColorBLACK);
 *
 *         SkMatrix resizeMatrix;
 *         resizeMatrix.setScale(RESIZE_FACTOR_X, RESIZE_FACTOR_Y);
 *
 *         sk_sp<SkImageFilter> filters[] = {
 *             SkImageFilters::Blur(5, 5, nullptr),
 *             SkImageFilters::DropShadow(10, 10, 3, 3, SK_ColorGREEN, nullptr),
 *             SkImageFilters::Offset(-16, 32, nullptr),
 *             SkImageFilters::MatrixTransform(resizeMatrix, SkSamplingOptions(), nullptr),
 *         };
 *
 *         SkRect r = SkRect::MakeWH(64, 64);
 *         SkScalar margin = 32;
 *         SkPaint paint;
 *         paint.setColor(SK_ColorWHITE);
 *         paint.setAntiAlias(true);
 *         paint.setStrokeWidth(10);
 *         paint.setStyle(SkPaint::kStroke_Style);
 *
 *         for (size_t i = 0; i < std::size(drawProc); ++i) {
 *             canvas->translate(0, margin);
 *             canvas->save();
 *             for (size_t j = 0; j < std::size(filters); ++j) {
 *                 canvas->translate(margin, 0);
 *                 canvas->save();
 *                 if (2 == j) {
 *                     canvas->translate(16, -32);
 *                 } else if (3 == j) {
 *                     canvas->scale(SkScalarInvert(RESIZE_FACTOR_X),
 *                                   SkScalarInvert(RESIZE_FACTOR_Y));
 *                 }
 *                 paint.setImageFilter(filters[j]);
 *                 drawProc[i](canvas, r, paint);
 *                 canvas->restore();
 *                 canvas->translate(r.width() + margin, 0);
 *             }
 *             canvas->restore();
 *             canvas->translate(0, r.height());
 *         }
 *     }
 *
 * private:
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class ImageFiltersStrokedGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("imagefiltersstroked"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(860, 500); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         void (*drawProc[])(SkCanvas*, const SkRect&, const SkPaint&) = {
   *             draw_line, draw_rect, draw_circle,
   *         };
   *
   *         canvas->clear(SK_ColorBLACK);
   *
   *         SkMatrix resizeMatrix;
   *         resizeMatrix.setScale(RESIZE_FACTOR_X, RESIZE_FACTOR_Y);
   *
   *         sk_sp<SkImageFilter> filters[] = {
   *             SkImageFilters::Blur(5, 5, nullptr),
   *             SkImageFilters::DropShadow(10, 10, 3, 3, SK_ColorGREEN, nullptr),
   *             SkImageFilters::Offset(-16, 32, nullptr),
   *             SkImageFilters::MatrixTransform(resizeMatrix, SkSamplingOptions(), nullptr),
   *         };
   *
   *         SkRect r = SkRect::MakeWH(64, 64);
   *         SkScalar margin = 32;
   *         SkPaint paint;
   *         paint.setColor(SK_ColorWHITE);
   *         paint.setAntiAlias(true);
   *         paint.setStrokeWidth(10);
   *         paint.setStyle(SkPaint::kStroke_Style);
   *
   *         for (size_t i = 0; i < std::size(drawProc); ++i) {
   *             canvas->translate(0, margin);
   *             canvas->save();
   *             for (size_t j = 0; j < std::size(filters); ++j) {
   *                 canvas->translate(margin, 0);
   *                 canvas->save();
   *                 if (2 == j) {
   *                     canvas->translate(16, -32);
   *                 } else if (3 == j) {
   *                     canvas->scale(SkScalarInvert(RESIZE_FACTOR_X),
   *                                   SkScalarInvert(RESIZE_FACTOR_Y));
   *                 }
   *                 paint.setImageFilter(filters[j]);
   *                 drawProc[i](canvas, r, paint);
   *                 canvas->restore();
   *                 canvas->translate(r.width() + margin, 0);
   *             }
   *             canvas->restore();
   *             canvas->translate(0, r.height());
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static void draw_circle(SkCanvas* canvas, const SkRect& r, const SkPaint& paint) {
     *         canvas->drawCircle(r.centerX(), r.centerY(),
     *                            r.width() * 2 / 5, paint);
     *     }
     * ```
     */
    protected fun drawCircle(
      canvas: SkCanvas?,
      r: SkRect,
      paint: SkPaint,
    ) {
      TODO("Implement drawCircle")
    }

    /**
     * C++ original:
     * ```cpp
     * static void draw_line(SkCanvas* canvas, const SkRect& r, const SkPaint& paint) {
     *         canvas->drawLine(r.fLeft, r.fBottom, r.fRight, r.fTop, paint);
     *     }
     * ```
     */
    protected fun drawLine(
      canvas: SkCanvas?,
      r: SkRect,
      paint: SkPaint,
    ) {
      TODO("Implement drawLine")
    }

    /**
     * C++ original:
     * ```cpp
     * static void draw_rect(SkCanvas* canvas, const SkRect& r, const SkPaint& paint) {
     *         canvas->drawRect(r, paint);
     *     }
     * ```
     */
    protected fun drawRect(
      canvas: SkCanvas?,
      r: SkRect,
      paint: SkPaint,
    ) {
      TODO("Implement drawRect")
    }
  }
}
