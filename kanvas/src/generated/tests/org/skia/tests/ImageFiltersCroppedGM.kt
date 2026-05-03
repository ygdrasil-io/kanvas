package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkImage
import org.skia.foundation.SkSp
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class ImageFiltersCroppedGM : public skiagm::GM {
 * public:
 *     ImageFiltersCroppedGM () {}
 *
 * protected:
 *     SkString getName() const override { return SkString("imagefilterscropped"); }
 *
 *     SkISize getISize() override { return SkISize::Make(400, 960); }
 *
 *     void make_checkerboard() {
 *         auto surf = SkSurfaces::Raster(SkImageInfo::MakeN32Premul(80, 80));
 *         auto canvas = surf->getCanvas();
 *         SkPaint darkPaint;
 *         darkPaint.setColor(0xFF404040);
 *         SkPaint lightPaint;
 *         lightPaint.setColor(0xFFA0A0A0);
 *         for (int y = 0; y < 80; y += 16) {
 *             for (int x = 0; x < 80; x += 16) {
 *                 canvas->save();
 *                 canvas->translate(SkIntToScalar(x), SkIntToScalar(y));
 *                 canvas->drawRect(SkRect::MakeXYWH(0, 0, 8, 8), darkPaint);
 *                 canvas->drawRect(SkRect::MakeXYWH(8, 0, 8, 8), lightPaint);
 *                 canvas->drawRect(SkRect::MakeXYWH(0, 8, 8, 8), lightPaint);
 *                 canvas->drawRect(SkRect::MakeXYWH(8, 8, 8, 8), darkPaint);
 *                 canvas->restore();
 *             }
 *         }
 *         fCheckerboard = surf->makeImageSnapshot();
 *     }
 *
 *     void draw_frame(SkCanvas* canvas, const SkRect& r) {
 *         SkPaint paint;
 *         paint.setStyle(SkPaint::kStroke_Style);
 *         paint.setColor(SK_ColorRED);
 *         canvas->drawRect(r, paint);
 *     }
 *
 *     void onOnceBeforeDraw() override {
 *         make_checkerboard();
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         void (*drawProc[])(SkCanvas*, const SkRect&, sk_sp<SkImageFilter>) = {
 *             draw_bitmap, draw_path, draw_paint, draw_text
 *         };
 *
 *         sk_sp<SkColorFilter> cf(SkColorFilters::Blend(SK_ColorBLUE,
 *                                                               SkBlendMode::kSrcIn));
 *         SkIRect cropRect = SkIRect::MakeXYWH(10, 10, 44, 44);
 *         SkIRect bogusRect = SkIRect::MakeXYWH(-100, -100, 10, 10);
 *
 *         sk_sp<SkImageFilter> offset(SkImageFilters::Offset(-10, -10, nullptr));
 *
 *         sk_sp<SkImageFilter> cfOffset(SkImageFilters::ColorFilter(cf, std::move(offset)));
 *
 *         // These are composed with an outer erode along the other axis, so don't add a cropRect to
 *         // them or it will interfere with the second filter evaluation.
 *         sk_sp<SkImageFilter> erodeX(SkImageFilters::Erode(8, 0, nullptr));
 *         sk_sp<SkImageFilter> erodeY(SkImageFilters::Erode(0, 8, nullptr));
 *
 *         sk_sp<SkImageFilter> filters[] = {
 *             nullptr,
 *             SkImageFilters::ColorFilter(cf, nullptr, &cropRect),
 *             SkImageFilters::Blur(0.0f, 0.0f, nullptr, &cropRect),
 *             SkImageFilters::Blur(1.0f, 1.0f, nullptr, &cropRect),
 *             SkImageFilters::Blur(8.0f, 0.0f, nullptr, &cropRect),
 *             SkImageFilters::Blur(0.0f, 8.0f, nullptr, &cropRect),
 *             SkImageFilters::Blur(8.0f, 8.0f, nullptr, &cropRect),
 *             SkImageFilters::Erode(1, 1, nullptr, &cropRect),
 *             SkImageFilters::Erode(8, 0, std::move(erodeY), &cropRect),
 *             SkImageFilters::Erode(0, 8, std::move(erodeX), &cropRect),
 *             SkImageFilters::Erode(8, 8, nullptr, &cropRect),
 *             SkImageFilters::Merge(nullptr, std::move(cfOffset), &cropRect),
 *             SkImageFilters::Blur(8.0f, 8.0f, nullptr, &bogusRect),
 *             SkImageFilters::ColorFilter(cf, nullptr, &bogusRect),
 *         };
 *
 *         SkRect r = SkRect::MakeWH(SkIntToScalar(64), SkIntToScalar(64));
 *         SkScalar MARGIN = SkIntToScalar(16);
 *         SkScalar DX = r.width() + MARGIN;
 *         SkScalar DY = r.height() + MARGIN;
 *
 *         canvas->translate(MARGIN, MARGIN);
 *         for (size_t j = 0; j < std::size(drawProc); ++j) {
 *             canvas->save();
 *             for (size_t i = 0; i < std::size(filters); ++i) {
 *                 SkPaint paint;
 *                 canvas->drawImage(fCheckerboard, 0, 0);
 *                 drawProc[j](canvas, r, filters[i]);
 *                 canvas->translate(0, DY);
 *             }
 *             canvas->restore();
 *             canvas->translate(DX, 0);
 *         }
 *     }
 *
 * private:
 *     sk_sp<SkImage> fCheckerboard;
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class ImageFiltersCroppedGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> fCheckerboard
   * ```
   */
  private var fCheckerboard: SkSp<SkImage> = TODO("Initialize fCheckerboard")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("imagefilterscropped"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(400, 960); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void make_checkerboard() {
   *         auto surf = SkSurfaces::Raster(SkImageInfo::MakeN32Premul(80, 80));
   *         auto canvas = surf->getCanvas();
   *         SkPaint darkPaint;
   *         darkPaint.setColor(0xFF404040);
   *         SkPaint lightPaint;
   *         lightPaint.setColor(0xFFA0A0A0);
   *         for (int y = 0; y < 80; y += 16) {
   *             for (int x = 0; x < 80; x += 16) {
   *                 canvas->save();
   *                 canvas->translate(SkIntToScalar(x), SkIntToScalar(y));
   *                 canvas->drawRect(SkRect::MakeXYWH(0, 0, 8, 8), darkPaint);
   *                 canvas->drawRect(SkRect::MakeXYWH(8, 0, 8, 8), lightPaint);
   *                 canvas->drawRect(SkRect::MakeXYWH(0, 8, 8, 8), lightPaint);
   *                 canvas->drawRect(SkRect::MakeXYWH(8, 8, 8, 8), darkPaint);
   *                 canvas->restore();
   *             }
   *         }
   *         fCheckerboard = surf->makeImageSnapshot();
   *     }
   * ```
   */
  protected fun makeCheckerboard() {
    TODO("Implement makeCheckerboard")
  }

  /**
   * C++ original:
   * ```cpp
   * void draw_frame(SkCanvas* canvas, const SkRect& r) {
   *         SkPaint paint;
   *         paint.setStyle(SkPaint::kStroke_Style);
   *         paint.setColor(SK_ColorRED);
   *         canvas->drawRect(r, paint);
   *     }
   * ```
   */
  protected fun drawFrame(canvas: SkCanvas?, r: SkRect) {
    TODO("Implement drawFrame")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         make_checkerboard();
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
   *         void (*drawProc[])(SkCanvas*, const SkRect&, sk_sp<SkImageFilter>) = {
   *             draw_bitmap, draw_path, draw_paint, draw_text
   *         };
   *
   *         sk_sp<SkColorFilter> cf(SkColorFilters::Blend(SK_ColorBLUE,
   *                                                               SkBlendMode::kSrcIn));
   *         SkIRect cropRect = SkIRect::MakeXYWH(10, 10, 44, 44);
   *         SkIRect bogusRect = SkIRect::MakeXYWH(-100, -100, 10, 10);
   *
   *         sk_sp<SkImageFilter> offset(SkImageFilters::Offset(-10, -10, nullptr));
   *
   *         sk_sp<SkImageFilter> cfOffset(SkImageFilters::ColorFilter(cf, std::move(offset)));
   *
   *         // These are composed with an outer erode along the other axis, so don't add a cropRect to
   *         // them or it will interfere with the second filter evaluation.
   *         sk_sp<SkImageFilter> erodeX(SkImageFilters::Erode(8, 0, nullptr));
   *         sk_sp<SkImageFilter> erodeY(SkImageFilters::Erode(0, 8, nullptr));
   *
   *         sk_sp<SkImageFilter> filters[] = {
   *             nullptr,
   *             SkImageFilters::ColorFilter(cf, nullptr, &cropRect),
   *             SkImageFilters::Blur(0.0f, 0.0f, nullptr, &cropRect),
   *             SkImageFilters::Blur(1.0f, 1.0f, nullptr, &cropRect),
   *             SkImageFilters::Blur(8.0f, 0.0f, nullptr, &cropRect),
   *             SkImageFilters::Blur(0.0f, 8.0f, nullptr, &cropRect),
   *             SkImageFilters::Blur(8.0f, 8.0f, nullptr, &cropRect),
   *             SkImageFilters::Erode(1, 1, nullptr, &cropRect),
   *             SkImageFilters::Erode(8, 0, std::move(erodeY), &cropRect),
   *             SkImageFilters::Erode(0, 8, std::move(erodeX), &cropRect),
   *             SkImageFilters::Erode(8, 8, nullptr, &cropRect),
   *             SkImageFilters::Merge(nullptr, std::move(cfOffset), &cropRect),
   *             SkImageFilters::Blur(8.0f, 8.0f, nullptr, &bogusRect),
   *             SkImageFilters::ColorFilter(cf, nullptr, &bogusRect),
   *         };
   *
   *         SkRect r = SkRect::MakeWH(SkIntToScalar(64), SkIntToScalar(64));
   *         SkScalar MARGIN = SkIntToScalar(16);
   *         SkScalar DX = r.width() + MARGIN;
   *         SkScalar DY = r.height() + MARGIN;
   *
   *         canvas->translate(MARGIN, MARGIN);
   *         for (size_t j = 0; j < std::size(drawProc); ++j) {
   *             canvas->save();
   *             for (size_t i = 0; i < std::size(filters); ++i) {
   *                 SkPaint paint;
   *                 canvas->drawImage(fCheckerboard, 0, 0);
   *                 drawProc[j](canvas, r, filters[i]);
   *                 canvas->translate(0, DY);
   *             }
   *             canvas->restore();
   *             canvas->translate(DX, 0);
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
