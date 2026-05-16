package org.skia.tests

import kotlin.Boolean
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkColor
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.math.SkISize
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class LcdBlendGM : public skiagm::GM {
 * public:
 *     LcdBlendGM() {
 *         const int kPointSize = 25;
 *         fTextHeight = SkIntToScalar(kPointSize);
 *     }
 *
 * protected:
 *     SkString getName() const override { return SkString("lcdblendmodes"); }
 *
 *     void onOnceBeforeDraw() override {
 *         fCheckerboard = ToolUtils::create_checkerboard_shader(SK_ColorBLACK, SK_ColorWHITE, 4);
 *     }
 *
 *     SkISize getISize() override { return SkISize::Make(kWidth, kHeight); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPaint p;
 *         p.setAntiAlias(false);
 *         p.setStyle(SkPaint::kFill_Style);
 *         p.setShader(fCheckerboard);
 *         SkRect r = SkRect::MakeWH(SkIntToScalar(kWidth), SkIntToScalar(kHeight));
 *         canvas->drawRect(r, p);
 *
 *         SkImageInfo info = SkImageInfo::MakeN32Premul(kWidth, kHeight);
 *         SkSurfaceProps props = SkSurfaceProps(0, kRGB_H_SkPixelGeometry);
 *         auto surface(ToolUtils::makeSurface(canvas, info, &props));
 *
 *         SkCanvas* surfCanvas = surface->getCanvas();
 *         this->drawColumn(surfCanvas, SK_ColorBLACK, SK_ColorWHITE, false);
 *         surfCanvas->translate(SkIntToScalar(kColWidth), 0);
 *         this->drawColumn(surfCanvas, SK_ColorWHITE, SK_ColorBLACK, false);
 *         surfCanvas->translate(SkIntToScalar(kColWidth), 0);
 *         this->drawColumn(surfCanvas, SK_ColorGREEN, SK_ColorMAGENTA, false);
 *         surfCanvas->translate(SkIntToScalar(kColWidth), 0);
 *         this->drawColumn(surfCanvas, SK_ColorCYAN, SK_ColorMAGENTA, true);
 *
 *         SkPaint surfPaint;
 *         surfPaint.setBlendMode(SkBlendMode::kSrcOver);
 *         surface->draw(canvas, 0, 0, SkSamplingOptions(), &surfPaint);
 *     }
 *
 *     void drawColumn(SkCanvas* canvas, SkColor backgroundColor, SkColor textColor, bool useGrad) {
 *         const SkBlendMode gModes[] = {
 *             SkBlendMode::kClear,
 *             SkBlendMode::kSrc,
 *             SkBlendMode::kDst,
 *             SkBlendMode::kSrcOver,
 *             SkBlendMode::kDstOver,
 *             SkBlendMode::kSrcIn,
 *             SkBlendMode::kDstIn,
 *             SkBlendMode::kSrcOut,
 *             SkBlendMode::kDstOut,
 *             SkBlendMode::kSrcATop,
 *             SkBlendMode::kDstATop,
 *             SkBlendMode::kXor,
 *             SkBlendMode::kPlus,
 *             SkBlendMode::kModulate,
 *             SkBlendMode::kScreen,
 *             SkBlendMode::kOverlay,
 *             SkBlendMode::kDarken,
 *             SkBlendMode::kLighten,
 *             SkBlendMode::kColorDodge,
 *             SkBlendMode::kColorBurn,
 *             SkBlendMode::kHardLight,
 *             SkBlendMode::kSoftLight,
 *             SkBlendMode::kDifference,
 *             SkBlendMode::kExclusion,
 *             SkBlendMode::kMultiply,
 *             SkBlendMode::kHue,
 *             SkBlendMode::kSaturation,
 *             SkBlendMode::kColor,
 *             SkBlendMode::kLuminosity,
 *         };
 *         // Draw background rect
 *         SkPaint backgroundPaint;
 *         backgroundPaint.setColor(backgroundColor);
 *         canvas->drawRect(SkRect::MakeIWH(kColWidth, kHeight), backgroundPaint);
 *         SkScalar y = fTextHeight;
 *         for (size_t m = 0; m < std::size(gModes); m++) {
 *             SkPaint paint;
 *             paint.setColor(textColor);
 *             paint.setBlendMode(gModes[m]);
 *             SkFont font(ToolUtils::DefaultPortableTypeface(), fTextHeight);
 *             font.setSubpixel(true);
 *             font.setEdging(SkFont::Edging::kSubpixelAntiAlias);
 *             if (useGrad) {
 *                 SkRect r;
 *                 r.setXYWH(0, y - fTextHeight, SkIntToScalar(kColWidth), fTextHeight);
 *                 paint.setShader(make_shader(r));
 *             }
 *             SkString string(SkBlendMode_Name(gModes[m]));
 *             canvas->drawString(string, 0, y, font, paint);
 *             y+=fTextHeight;
 *         }
 *     }
 *
 * private:
 *     SkScalar fTextHeight;
 *     sk_sp<SkShader> fCheckerboard;
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class LcdBlendGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkScalar fTextHeight
   * ```
   */
  private var fTextHeight: SkScalar = TODO("Initialize fTextHeight")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> fCheckerboard
   * ```
   */
  private var fCheckerboard: SkSp<SkShader> = TODO("Initialize fCheckerboard")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("lcdblendmodes"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         fCheckerboard = ToolUtils::create_checkerboard_shader(SK_ColorBLACK, SK_ColorWHITE, 4);
   *     }
   * ```
   */
  protected override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(kWidth, kHeight); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkPaint p;
   *         p.setAntiAlias(false);
   *         p.setStyle(SkPaint::kFill_Style);
   *         p.setShader(fCheckerboard);
   *         SkRect r = SkRect::MakeWH(SkIntToScalar(kWidth), SkIntToScalar(kHeight));
   *         canvas->drawRect(r, p);
   *
   *         SkImageInfo info = SkImageInfo::MakeN32Premul(kWidth, kHeight);
   *         SkSurfaceProps props = SkSurfaceProps(0, kRGB_H_SkPixelGeometry);
   *         auto surface(ToolUtils::makeSurface(canvas, info, &props));
   *
   *         SkCanvas* surfCanvas = surface->getCanvas();
   *         this->drawColumn(surfCanvas, SK_ColorBLACK, SK_ColorWHITE, false);
   *         surfCanvas->translate(SkIntToScalar(kColWidth), 0);
   *         this->drawColumn(surfCanvas, SK_ColorWHITE, SK_ColorBLACK, false);
   *         surfCanvas->translate(SkIntToScalar(kColWidth), 0);
   *         this->drawColumn(surfCanvas, SK_ColorGREEN, SK_ColorMAGENTA, false);
   *         surfCanvas->translate(SkIntToScalar(kColWidth), 0);
   *         this->drawColumn(surfCanvas, SK_ColorCYAN, SK_ColorMAGENTA, true);
   *
   *         SkPaint surfPaint;
   *         surfPaint.setBlendMode(SkBlendMode::kSrcOver);
   *         surface->draw(canvas, 0, 0, SkSamplingOptions(), &surfPaint);
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawColumn(SkCanvas* canvas, SkColor backgroundColor, SkColor textColor, bool useGrad) {
   *         const SkBlendMode gModes[] = {
   *             SkBlendMode::kClear,
   *             SkBlendMode::kSrc,
   *             SkBlendMode::kDst,
   *             SkBlendMode::kSrcOver,
   *             SkBlendMode::kDstOver,
   *             SkBlendMode::kSrcIn,
   *             SkBlendMode::kDstIn,
   *             SkBlendMode::kSrcOut,
   *             SkBlendMode::kDstOut,
   *             SkBlendMode::kSrcATop,
   *             SkBlendMode::kDstATop,
   *             SkBlendMode::kXor,
   *             SkBlendMode::kPlus,
   *             SkBlendMode::kModulate,
   *             SkBlendMode::kScreen,
   *             SkBlendMode::kOverlay,
   *             SkBlendMode::kDarken,
   *             SkBlendMode::kLighten,
   *             SkBlendMode::kColorDodge,
   *             SkBlendMode::kColorBurn,
   *             SkBlendMode::kHardLight,
   *             SkBlendMode::kSoftLight,
   *             SkBlendMode::kDifference,
   *             SkBlendMode::kExclusion,
   *             SkBlendMode::kMultiply,
   *             SkBlendMode::kHue,
   *             SkBlendMode::kSaturation,
   *             SkBlendMode::kColor,
   *             SkBlendMode::kLuminosity,
   *         };
   *         // Draw background rect
   *         SkPaint backgroundPaint;
   *         backgroundPaint.setColor(backgroundColor);
   *         canvas->drawRect(SkRect::MakeIWH(kColWidth, kHeight), backgroundPaint);
   *         SkScalar y = fTextHeight;
   *         for (size_t m = 0; m < std::size(gModes); m++) {
   *             SkPaint paint;
   *             paint.setColor(textColor);
   *             paint.setBlendMode(gModes[m]);
   *             SkFont font(ToolUtils::DefaultPortableTypeface(), fTextHeight);
   *             font.setSubpixel(true);
   *             font.setEdging(SkFont::Edging::kSubpixelAntiAlias);
   *             if (useGrad) {
   *                 SkRect r;
   *                 r.setXYWH(0, y - fTextHeight, SkIntToScalar(kColWidth), fTextHeight);
   *                 paint.setShader(make_shader(r));
   *             }
   *             SkString string(SkBlendMode_Name(gModes[m]));
   *             canvas->drawString(string, 0, y, font, paint);
   *             y+=fTextHeight;
   *         }
   *     }
   * ```
   */
  protected fun drawColumn(
    canvas: SkCanvas?,
    backgroundColor: SkColor,
    textColor: SkColor,
    useGrad: Boolean,
  ) {
    TODO("Implement drawColumn")
  }
}
