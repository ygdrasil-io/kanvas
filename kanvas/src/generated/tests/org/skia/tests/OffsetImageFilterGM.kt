package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSp
import org.skia.math.SkIRect
import org.skia.math.SkISize
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class OffsetImageFilterGM : public skiagm::GM {
 * public:
 *     OffsetImageFilterGM() {
 *         this->setBGColor(0xFF000000);
 *     }
 *
 * protected:
 *     SkString getName() const override { return SkString("offsetimagefilter"); }
 *
 *     SkISize getISize() override { return SkISize::Make(WIDTH, HEIGHT); }
 *
 *     void onOnceBeforeDraw() override {
 *         fBitmap = ToolUtils::CreateStringImage(80, 80, 0xD000D000, 15, 65, 96, "e");
 *
 *         fCheckerboard = ToolUtils::create_checkerboard_image(80, 80, 0xFFA0A0A0, 0xFF404040, 8);
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         canvas->clear(SK_ColorBLACK);
 *         SkPaint paint;
 *
 *         for (int i = 0; i < 4; i++) {
 *             sk_sp<SkImage> image = (i & 0x01) ? fCheckerboard : fBitmap;
 *             SkIRect cropRect = SkIRect::MakeXYWH(i * 12,
 *                                                  i * 8,
 *                                                  image->width() - i * 8,
 *                                                  image->height() - i * 12);
 *             sk_sp<SkImageFilter> tileInput(SkImageFilters::Image(image, SkFilterMode::kNearest));
 *             SkScalar dx = SkIntToScalar(i*5);
 *             SkScalar dy = SkIntToScalar(i*10);
 *             paint.setImageFilter(SkImageFilters::Offset(dx, dy, std::move(tileInput), &cropRect));
 *             DrawClippedImage(canvas, image.get(), paint, 1, cropRect);
 *             canvas->translate(SkIntToScalar(image->width() + MARGIN), 0);
 *         }
 *
 *         SkIRect cropRect = SkIRect::MakeXYWH(0, 0, 100, 100);
 *         paint.setImageFilter(SkImageFilters::Offset(-5, -10, nullptr, &cropRect));
 *         DrawClippedImage(canvas, fBitmap.get(), paint, 2, cropRect);
 *     }
 * private:
 *     static void DrawClippedImage(SkCanvas* canvas, const SkImage* image, const SkPaint& paint,
 *                           SkScalar scale, const SkIRect& cropRect) {
 *         SkRect clipRect = SkRect::MakeIWH(image->width(), image->height());
 *
 *         canvas->save();
 *         canvas->clipRect(clipRect);
 *         canvas->scale(scale, scale);
 *         canvas->drawImage(image, 0, 0, SkSamplingOptions(), &paint);
 *         canvas->restore();
 *
 *         // Draw a boundary rect around the intersection of the clip rect and crop rect.
 *         SkRect cropRectFloat;
 *         SkMatrix::Scale(scale, scale).mapRect(&cropRectFloat, SkRect::Make(cropRect));
 *         if (clipRect.intersect(cropRectFloat)) {
 *             SkPaint strokePaint;
 *             strokePaint.setStyle(SkPaint::kStroke_Style);
 *             strokePaint.setStrokeWidth(2);
 *             strokePaint.setColor(SK_ColorRED);
 *             canvas->drawRect(clipRect, strokePaint);
 *         }
 *     }
 *
 *     sk_sp<SkImage> fBitmap, fCheckerboard;
 *
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class OffsetImageFilterGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> fBitmap
   * ```
   */
  private var fBitmap: SkSp<SkImage> = TODO("Initialize fBitmap")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> fBitmap, fCheckerboard
   * ```
   */
  private var fCheckerboard: SkSp<SkImage> = TODO("Initialize fCheckerboard")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("offsetimagefilter"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(WIDTH, HEIGHT); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         fBitmap = ToolUtils::CreateStringImage(80, 80, 0xD000D000, 15, 65, 96, "e");
   *
   *         fCheckerboard = ToolUtils::create_checkerboard_image(80, 80, 0xFFA0A0A0, 0xFF404040, 8);
   *     }
   * ```
   */
  protected override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         canvas->clear(SK_ColorBLACK);
   *         SkPaint paint;
   *
   *         for (int i = 0; i < 4; i++) {
   *             sk_sp<SkImage> image = (i & 0x01) ? fCheckerboard : fBitmap;
   *             SkIRect cropRect = SkIRect::MakeXYWH(i * 12,
   *                                                  i * 8,
   *                                                  image->width() - i * 8,
   *                                                  image->height() - i * 12);
   *             sk_sp<SkImageFilter> tileInput(SkImageFilters::Image(image, SkFilterMode::kNearest));
   *             SkScalar dx = SkIntToScalar(i*5);
   *             SkScalar dy = SkIntToScalar(i*10);
   *             paint.setImageFilter(SkImageFilters::Offset(dx, dy, std::move(tileInput), &cropRect));
   *             DrawClippedImage(canvas, image.get(), paint, 1, cropRect);
   *             canvas->translate(SkIntToScalar(image->width() + MARGIN), 0);
   *         }
   *
   *         SkIRect cropRect = SkIRect::MakeXYWH(0, 0, 100, 100);
   *         paint.setImageFilter(SkImageFilters::Offset(-5, -10, nullptr, &cropRect));
   *         DrawClippedImage(canvas, fBitmap.get(), paint, 2, cropRect);
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
     * static void DrawClippedImage(SkCanvas* canvas, const SkImage* image, const SkPaint& paint,
     *                           SkScalar scale, const SkIRect& cropRect) {
     *         SkRect clipRect = SkRect::MakeIWH(image->width(), image->height());
     *
     *         canvas->save();
     *         canvas->clipRect(clipRect);
     *         canvas->scale(scale, scale);
     *         canvas->drawImage(image, 0, 0, SkSamplingOptions(), &paint);
     *         canvas->restore();
     *
     *         // Draw a boundary rect around the intersection of the clip rect and crop rect.
     *         SkRect cropRectFloat;
     *         SkMatrix::Scale(scale, scale).mapRect(&cropRectFloat, SkRect::Make(cropRect));
     *         if (clipRect.intersect(cropRectFloat)) {
     *             SkPaint strokePaint;
     *             strokePaint.setStyle(SkPaint::kStroke_Style);
     *             strokePaint.setStrokeWidth(2);
     *             strokePaint.setColor(SK_ColorRED);
     *             canvas->drawRect(clipRect, strokePaint);
     *         }
     *     }
     * ```
     */
    private fun drawClippedImage(
      canvas: SkCanvas?,
      image: SkImage?,
      paint: SkPaint,
      scale: SkScalar,
      cropRect: SkIRect,
    ) {
      TODO("Implement drawClippedImage")
    }
  }
}
