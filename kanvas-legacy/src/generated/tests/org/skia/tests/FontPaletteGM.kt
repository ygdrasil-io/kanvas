package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkFontArguments
import org.skia.foundation.SkSp
import org.skia.foundation.SkTypeface
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class FontPaletteGM : public GM {
 * public:
 *     FontPaletteGM(const char* test_name, const SkFontArguments::Palette& paletteOverride)
 *             : fName(test_name), fPalette(paletteOverride) {}
 *
 * protected:
 *     sk_sp<SkTypeface> fTypefaceDefault;
 *     sk_sp<SkTypeface> fTypefaceFromStream;
 *     sk_sp<SkTypeface> fTypefaceCloned;
 *
 *     void onOnceBeforeDraw() override {
 *         SkFontArguments paletteArguments;
 *         paletteArguments.setPalette(fPalette);
 *
 *         fTypefaceDefault = MakeTypefaceFromResource(kColrCpalTestFontPath, SkFontArguments());
 *         fTypefaceCloned =
 *                 fTypefaceDefault ? fTypefaceDefault->makeClone(paletteArguments) : nullptr;
 *
 *         fTypefaceFromStream = MakeTypefaceFromResource(kColrCpalTestFontPath, paletteArguments);
 *     }
 *
 *     SkString getName() const override {
 *         SkString gm_name = SkStringPrintf("font_palette_%s", fName.c_str());
 *         return gm_name;
 *     }
 *
 *     SkISize getISize() override { return SkISize::Make(1000, 400); }
 *
 *     DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
 *         canvas->drawColor(SK_ColorWHITE);
 *         SkPaint paint;
 *
 *         canvas->translate(10, 20);
 *
 *         if (!fTypefaceCloned || !fTypefaceFromStream) {
 *             *errorMsg = "Did not recognize COLR v1 test font format.";
 *             return DrawResult::kSkip;
 *         }
 *
 *         SkFontMetrics metrics;
 *         SkScalar y = 0;
 *         SkScalar textSize = 200;
 *         for (auto& typeface : {fTypefaceFromStream, fTypefaceCloned}) {
 *             SkFont defaultFont(fTypefaceDefault);
 *             SkFont paletteFont(typeface);
 *             defaultFont.setSize(textSize);
 *             paletteFont.setSize(textSize);
 *
 *             defaultFont.getMetrics(&metrics);
 *             y += -metrics.fAscent;
 *             // Set a recognizable foreground color which is not to be overriden.
 *             paint.setColor(SK_ColorGRAY);
 *             // Draw the default palette on the left, for COLRv0 and COLRv1.
 *             canvas->drawSimpleText(
 *                     ColrV1TestDefinitions::color_circles_palette,
 *                     std::size(ColrV1TestDefinitions::color_circles_palette) * sizeof(uint32_t),
 *                     SkTextEncoding::kUTF32,
 *                     0,
 *                     y,
 *                     defaultFont,
 *                     paint);
 *             // Draw the overriden palette on the right.
 *             canvas->drawSimpleText(
 *                     ColrV1TestDefinitions::color_circles_palette,
 *                     std::size(ColrV1TestDefinitions::color_circles_palette) * sizeof(uint32_t),
 *                     SkTextEncoding::kUTF32,
 *                     440,
 *                     y,
 *                     paletteFont,
 *                     paint);
 *             y += metrics.fDescent + metrics.fLeading;
 *         }
 *         return DrawResult::kOk;
 *     }
 *
 * private:
 *     using INHERITED = GM;
 *     SkString fName;
 *     SkFontArguments::Palette fPalette;
 * }
 * ```
 */
public open class FontPaletteGM public constructor(
  testName: String?,
  paletteOverride: SkFontArguments.Palette,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> fTypefaceDefault
   * ```
   */
  protected var fTypefaceDefault: SkSp<SkTypeface> = TODO("Initialize fTypefaceDefault")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> fTypefaceFromStream
   * ```
   */
  protected var fTypefaceFromStream: SkSp<SkTypeface> = TODO("Initialize fTypefaceFromStream")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> fTypefaceCloned
   * ```
   */
  protected var fTypefaceCloned: SkSp<SkTypeface> = TODO("Initialize fTypefaceCloned")

  /**
   * C++ original:
   * ```cpp
   * SkString fName
   * ```
   */
  private var fName: String = TODO("Initialize fName")

  /**
   * C++ original:
   * ```cpp
   * SkFontArguments::Palette fPalette
   * ```
   */
  private var fPalette: SkFontArguments.Palette = TODO("Initialize fPalette")

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         SkFontArguments paletteArguments;
   *         paletteArguments.setPalette(fPalette);
   *
   *         fTypefaceDefault = MakeTypefaceFromResource(kColrCpalTestFontPath, SkFontArguments());
   *         fTypefaceCloned =
   *                 fTypefaceDefault ? fTypefaceDefault->makeClone(paletteArguments) : nullptr;
   *
   *         fTypefaceFromStream = MakeTypefaceFromResource(kColrCpalTestFontPath, paletteArguments);
   *     }
   * ```
   */
  protected override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override {
   *         SkString gm_name = SkStringPrintf("font_palette_%s", fName.c_str());
   *         return gm_name;
   *     }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(1000, 400); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
   *         canvas->drawColor(SK_ColorWHITE);
   *         SkPaint paint;
   *
   *         canvas->translate(10, 20);
   *
   *         if (!fTypefaceCloned || !fTypefaceFromStream) {
   *             *errorMsg = "Did not recognize COLR v1 test font format.";
   *             return DrawResult::kSkip;
   *         }
   *
   *         SkFontMetrics metrics;
   *         SkScalar y = 0;
   *         SkScalar textSize = 200;
   *         for (auto& typeface : {fTypefaceFromStream, fTypefaceCloned}) {
   *             SkFont defaultFont(fTypefaceDefault);
   *             SkFont paletteFont(typeface);
   *             defaultFont.setSize(textSize);
   *             paletteFont.setSize(textSize);
   *
   *             defaultFont.getMetrics(&metrics);
   *             y += -metrics.fAscent;
   *             // Set a recognizable foreground color which is not to be overriden.
   *             paint.setColor(SK_ColorGRAY);
   *             // Draw the default palette on the left, for COLRv0 and COLRv1.
   *             canvas->drawSimpleText(
   *                     ColrV1TestDefinitions::color_circles_palette,
   *                     std::size(ColrV1TestDefinitions::color_circles_palette) * sizeof(uint32_t),
   *                     SkTextEncoding::kUTF32,
   *                     0,
   *                     y,
   *                     defaultFont,
   *                     paint);
   *             // Draw the overriden palette on the right.
   *             canvas->drawSimpleText(
   *                     ColrV1TestDefinitions::color_circles_palette,
   *                     std::size(ColrV1TestDefinitions::color_circles_palette) * sizeof(uint32_t),
   *                     SkTextEncoding::kUTF32,
   *                     440,
   *                     y,
   *                     paletteFont,
   *                     paint);
   *             y += metrics.fDescent + metrics.fLeading;
   *         }
   *         return DrawResult::kOk;
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?, errorMsg: String?): DrawResult {
    TODO("Implement onDraw")
  }
}
