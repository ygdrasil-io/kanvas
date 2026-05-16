package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkImage
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkSp
import org.skia.math.SkISize
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class ShowMipLevels3 : public skiagm::GM {
 *     sk_sp<SkImage> fImg;
 *
 *     SkString getName() const override { return SkString("showmiplevels_explicit"); }
 *
 *     SkISize getISize() override { return {1130, 970}; }
 *
 *     void onOnceBeforeDraw() override {
 *         fImg = ToolUtils::GetResourceAsImage("images/ship.png");
 *         fImg = fImg->makeRasterImage(nullptr); // makeWithMips only works on raster for now
 *
 *         const SkColor colors[] = { SK_ColorRED, SK_ColorGREEN, SK_ColorBLUE };
 *
 *         SkMipmapBuilder builder(fImg->imageInfo());
 *         for (int i = 0; i < builder.countLevels(); ++i) {
 *             auto surf = SkSurfaces::WrapPixels(builder.level(i));
 *             surf->getCanvas()->drawColor(colors[i % std::size(colors)]);
 *         }
 *         fImg = builder.attachTo(fImg);
 *     }
 *
 *     DrawResult onDraw(SkCanvas* canvas, SkString*) override {
 *         canvas->drawColor(0xFFDDDDDD);
 *
 *         canvas->translate(10, 10);
 *         for (auto mm : {SkMipmapMode::kNone, SkMipmapMode::kNearest, SkMipmapMode::kLinear}) {
 *             for (auto fm : {SkFilterMode::kNearest, SkFilterMode::kLinear}) {
 *                 canvas->translate(0, draw_downscaling(canvas, {fm, mm}));
 *             }
 *         }
 *         return DrawResult::kOk;
 *     }
 *
 * private:
 *     SkScalar draw_downscaling(SkCanvas* canvas, SkSamplingOptions sampling) {
 *         SkAutoCanvasRestore acr(canvas, true);
 *
 *         SkPaint paint;
 *         SkRect r = {0, 0, 150, 150};
 *         for (float scale = 1; scale >= 0.1f; scale *= 0.7f) {
 *             SkMatrix matrix = SkMatrix::Scale(scale, scale);
 *             paint.setShader(fImg->makeShader(SkTileMode::kRepeat, SkTileMode::kRepeat,
 *                                              sampling, &matrix));
 *             canvas->drawRect(r, paint);
 *             canvas->translate(r.width() + 10, 0);
 *         }
 *         return r.height() + 10;
 *     }
 *
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class ShowMipLevels3 : GM() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> fImg
   * ```
   */
  private var fImg: SkSp<SkImage> = TODO("Initialize fImg")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("showmiplevels_explicit"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {1130, 970}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         fImg = ToolUtils::GetResourceAsImage("images/ship.png");
   *         fImg = fImg->makeRasterImage(nullptr); // makeWithMips only works on raster for now
   *
   *         const SkColor colors[] = { SK_ColorRED, SK_ColorGREEN, SK_ColorBLUE };
   *
   *         SkMipmapBuilder builder(fImg->imageInfo());
   *         for (int i = 0; i < builder.countLevels(); ++i) {
   *             auto surf = SkSurfaces::WrapPixels(builder.level(i));
   *             surf->getCanvas()->drawColor(colors[i % std::size(colors)]);
   *         }
   *         fImg = builder.attachTo(fImg);
   *     }
   * ```
   */
  public override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawResult onDraw(SkCanvas* canvas, SkString*) override {
   *         canvas->drawColor(0xFFDDDDDD);
   *
   *         canvas->translate(10, 10);
   *         for (auto mm : {SkMipmapMode::kNone, SkMipmapMode::kNearest, SkMipmapMode::kLinear}) {
   *             for (auto fm : {SkFilterMode::kNearest, SkFilterMode::kLinear}) {
   *                 canvas->translate(0, draw_downscaling(canvas, {fm, mm}));
   *             }
   *         }
   *         return DrawResult::kOk;
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?, param1: String?): DrawResult {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar draw_downscaling(SkCanvas* canvas, SkSamplingOptions sampling) {
   *         SkAutoCanvasRestore acr(canvas, true);
   *
   *         SkPaint paint;
   *         SkRect r = {0, 0, 150, 150};
   *         for (float scale = 1; scale >= 0.1f; scale *= 0.7f) {
   *             SkMatrix matrix = SkMatrix::Scale(scale, scale);
   *             paint.setShader(fImg->makeShader(SkTileMode::kRepeat, SkTileMode::kRepeat,
   *                                              sampling, &matrix));
   *             canvas->drawRect(r, paint);
   *             canvas->translate(r.width() + 10, 0);
   *         }
   *         return r.height() + 10;
   *     }
   * ```
   */
  private fun drawDownscaling(canvas: SkCanvas?, sampling: SkSamplingOptions): SkScalar {
    TODO("Implement drawDownscaling")
  }
}
