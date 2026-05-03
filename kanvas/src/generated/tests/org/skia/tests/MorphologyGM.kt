package org.skia.tests

import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSp
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class MorphologyGM : public GM {
 * public:
 *     MorphologyGM() {
 *         this->setBGColor(0xFF000000);
 *     }
 *
 * protected:
 *     SkString getName() const override { return SkString("morphology"); }
 *
 *     void onOnceBeforeDraw() override {
 *         auto surf = SkSurfaces::Raster(SkImageInfo::MakeN32Premul(135, 135));
 *
 *         SkFont  font(ToolUtils::DefaultPortableTypeface(), 64.0f);
 *         SkPaint paint;
 *         paint.setColor(0xFFFFFFFF);
 *         surf->getCanvas()->drawString("ABC", 10, 55,  font, paint);
 *         surf->getCanvas()->drawString("XYZ", 10, 110, font, paint);
 *
 *         fImage = surf->makeImageSnapshot();
 *     }
 *
 *     SkISize getISize() override { return SkISize::Make(WIDTH, HEIGHT); }
 *
 *     void drawClippedBitmap(SkCanvas* canvas, const SkPaint& paint, int x, int y) {
 *         canvas->save();
 *         canvas->translate(SkIntToScalar(x), SkIntToScalar(y));
 *         canvas->clipIRect(fImage->bounds());
 *         canvas->drawImage(fImage, 0, 0, SkSamplingOptions(), &paint);
 *         canvas->restore();
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         struct {
 *             int fWidth, fHeight;
 *             int fRadiusX, fRadiusY;
 *         } samples[] = {
 *             { 140, 140,   0,   0 },
 *             { 140, 140,   0,   2 },
 *             { 140, 140,   2,   0 },
 *             { 140, 140,   2,   2 },
 *             {  24,  24,  25,  25 },
 *         };
 *         SkPaint paint;
 *         SkIRect cropRect = SkIRect::MakeXYWH(25, 20, 100, 80);
 *
 *         for (unsigned j = 0; j < 4; ++j) {
 *             for (unsigned i = 0; i < std::size(samples); ++i) {
 *                 const SkIRect* cr = j & 0x02 ? &cropRect : nullptr;
 *                 if (j & 0x01) {
 *                     paint.setImageFilter(SkImageFilters::Erode(
 *                             samples[i].fRadiusX, samples[i].fRadiusY, nullptr, cr));
 *                 } else {
 *                     paint.setImageFilter(SkImageFilters::Dilate(
 *                             samples[i].fRadiusX, samples[i].fRadiusY, nullptr, cr));
 *                 }
 *                 this->drawClippedBitmap(canvas, paint, i * 140, j * 140);
 *             }
 *         }
 *     }
 *
 * private:
 *     sk_sp<SkImage> fImage;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class MorphologyGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> fImage
   * ```
   */
  private var fImage: SkSp<SkImage> = TODO("Initialize fImage")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("morphology"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         auto surf = SkSurfaces::Raster(SkImageInfo::MakeN32Premul(135, 135));
   *
   *         SkFont  font(ToolUtils::DefaultPortableTypeface(), 64.0f);
   *         SkPaint paint;
   *         paint.setColor(0xFFFFFFFF);
   *         surf->getCanvas()->drawString("ABC", 10, 55,  font, paint);
   *         surf->getCanvas()->drawString("XYZ", 10, 110, font, paint);
   *
   *         fImage = surf->makeImageSnapshot();
   *     }
   * ```
   */
  protected override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
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
   * void drawClippedBitmap(SkCanvas* canvas, const SkPaint& paint, int x, int y) {
   *         canvas->save();
   *         canvas->translate(SkIntToScalar(x), SkIntToScalar(y));
   *         canvas->clipIRect(fImage->bounds());
   *         canvas->drawImage(fImage, 0, 0, SkSamplingOptions(), &paint);
   *         canvas->restore();
   *     }
   * ```
   */
  protected fun drawClippedBitmap(
    canvas: SkCanvas?,
    paint: SkPaint,
    x: Int,
    y: Int,
  ) {
    TODO("Implement drawClippedBitmap")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         struct {
   *             int fWidth, fHeight;
   *             int fRadiusX, fRadiusY;
   *         } samples[] = {
   *             { 140, 140,   0,   0 },
   *             { 140, 140,   0,   2 },
   *             { 140, 140,   2,   0 },
   *             { 140, 140,   2,   2 },
   *             {  24,  24,  25,  25 },
   *         };
   *         SkPaint paint;
   *         SkIRect cropRect = SkIRect::MakeXYWH(25, 20, 100, 80);
   *
   *         for (unsigned j = 0; j < 4; ++j) {
   *             for (unsigned i = 0; i < std::size(samples); ++i) {
   *                 const SkIRect* cr = j & 0x02 ? &cropRect : nullptr;
   *                 if (j & 0x01) {
   *                     paint.setImageFilter(SkImageFilters::Erode(
   *                             samples[i].fRadiusX, samples[i].fRadiusY, nullptr, cr));
   *                 } else {
   *                     paint.setImageFilter(SkImageFilters::Dilate(
   *                             samples[i].fRadiusX, samples[i].fRadiusY, nullptr, cr));
   *                 }
   *                 this->drawClippedBitmap(canvas, paint, i * 140, j * 140);
   *             }
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
