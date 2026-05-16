package org.skia.tests

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.initializer_list
import org.skia.core.SkCanvas
import org.skia.foundation.SkFontArguments
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan
import org.skia.foundation.SkTypeface
import org.skia.math.SkISize
import org.skia.math.SkScalar
import org.skia.tools.SkMetaData
import org.skia.tools.VariationSliders

/**
 * C++ original:
 * ```cpp
 * class ColrV1GM : public GM {
 * public:
 *     ColrV1GM(const char* testName,
 *              SkSpan<const uint32_t> codepoints,
 *              SkScalar skewX,
 *              SkScalar rotateDeg,
 *              std::initializer_list<SkFontArguments::VariationPosition::Coordinate>
 *                      specifiedVariations)
 *             : fTestName(testName), fCodepoints(codepoints), fSkewX(skewX), fRotateDeg(rotateDeg) {
 *         fVariationPosition.coordinateCount = specifiedVariations.size();
 *         fCoordinates = std::make_unique<SkFontArguments::VariationPosition::Coordinate[]>(
 *                 specifiedVariations.size());
 *         for (size_t i = 0; i < specifiedVariations.size(); ++i) {
 *             fCoordinates[i] = std::data(specifiedVariations)[i];
 *         }
 *
 *         fVariationPosition.coordinates = fCoordinates.get();
 *     }
 *
 * protected:
 *     void onOnceBeforeDraw() override {
 *         if (fVariationPosition.coordinateCount) {
 *             fTypeface = ToolUtils::CreateTypefaceFromResource(kTestFontNameVariable, 0);
 *         } else {
 *             fTypeface = ToolUtils::CreateTypefaceFromResource(kTestFontName, 0);
 *         }
 *         fVariationSliders = ToolUtils::VariationSliders(fTypeface.get(), fVariationPosition);
 *     }
 *
 *     SkString getName() const override {
 *         SkASSERT(!fTestName.isEmpty());
 *         SkString gm_name = SkStringPrintf("colrv1_%s", fTestName.c_str());
 *
 *         if (fSkewX) {
 *             gm_name.append(SkStringPrintf("_skew_%.2f", fSkewX));
 *         }
 *
 *         if (fRotateDeg) {
 *             gm_name.append(SkStringPrintf("_rotate_%.2f", fRotateDeg));
 *         }
 *
 *         for (int i = 0; i < fVariationPosition.coordinateCount; ++i) {
 *             SkString tagName = ToolUtils::VariationSliders::tagToString(
 *                     fVariationPosition.coordinates[i].axis);
 *             gm_name.append(SkStringPrintf(
 *                     "_%s_%.2f", tagName.c_str(), fVariationPosition.coordinates[i].value));
 *         }
 *
 *         return gm_name;
 *     }
 *
 *     bool onGetControls(SkMetaData* controls) override {
 *         return fVariationSliders.writeControls(controls);
 *     }
 *
 *     void onSetControls(const SkMetaData& controls) override {
 *         return fVariationSliders.readControls(controls);
 *     }
 *
 *     SkISize getISize() override {
 *         // Sweep tests get a slightly wider canvas so that glyphs from one group fit in one row.
 *         if (fTestName.equals("sweep_varsweep")) {
 *             return SkISize::Make(xWidth + 500, xWidth);
 *         }
 *         return SkISize::Make(xWidth, xWidth);
 *     }
 *
 *     sk_sp<SkTypeface> makeVariedTypeface() {
 *         if (!fTypeface) {
 *             return nullptr;
 *         }
 *         SkSpan<const SkFontArguments::VariationPosition::Coordinate> coords =
 *                 fVariationSliders.getCoordinates();
 *         SkFontArguments::VariationPosition varPos = {coords.data(),
 *                                                      static_cast<int>(coords.size())};
 *         SkFontArguments args;
 *         args.setVariationDesignPosition(varPos);
 *         return fTypeface->makeClone(args);
 *     }
 *
 *     DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
 *         canvas->drawColor(SK_ColorWHITE);
 *         SkPaint paint;
 *
 *         canvas->translate(xTranslate, 20);
 *
 *         if (!fTypeface) {
 *             *errorMsg = "Did not recognize COLR v1 font format.";
 *             return DrawResult::kSkip;
 *         }
 *
 *         canvas->rotate(fRotateDeg);
 *         canvas->skew(fSkewX, 0);
 *
 *         SkFont font(makeVariedTypeface());
 *
 *         SkFontMetrics metrics;
 *         SkScalar y = 0;
 *         std::vector<SkColor> paint_colors = {
 *                 SK_ColorBLACK, SK_ColorGREEN, SK_ColorRED, SK_ColorBLUE};
 *         auto paint_color_iterator = paint_colors.begin();
 *         for (SkScalar textSize : kTextSizes) {
 *             font.setSize(textSize);
 *             font.getMetrics(&metrics);
 *             font.setHinting(SkFontHinting::kNone);
 *             SkScalar y_shift = -(metrics.fAscent + metrics.fDescent + metrics.fLeading) * 1.2;
 *             y += y_shift;
 *             paint.setColor(*paint_color_iterator);
 *             int x = 0;
 *             // Perform simple line breaking to fit more glyphs into the GM canvas.
 *             for (size_t i = 0; i < fCodepoints.size(); ++i) {
 *                 SkScalar glyphAdvance = font.measureText(
 *                         &fCodepoints[i], sizeof(uint32_t), SkTextEncoding::kUTF32, nullptr);
 *                 if (0 < x && getISize().width() - xTranslate < x + glyphAdvance) {
 *                     y += y_shift;
 *                     x = 0;
 *                 }
 *                 canvas->drawSimpleText(&fCodepoints[i],
 *                                        sizeof(uint32_t),
 *                                        SkTextEncoding::kUTF32,
 *                                        x,
 *                                        y,
 *                                        font,
 *                                        paint);
 *                 x += glyphAdvance + glyphAdvance * 0.05f;
 *             }
 *             paint_color_iterator++;
 *         }
 *         return DrawResult::kOk;
 *     }
 *
 * private:
 *     using INHERITED = GM;
 *
 *     SkString fTestName;
 *     sk_sp<SkTypeface> fTypeface;
 *     SkSpan<const uint32_t> fCodepoints;
 *     SkScalar fSkewX;
 *     SkScalar fRotateDeg;
 *     std::unique_ptr<SkFontArguments::VariationPosition::Coordinate[]> fCoordinates;
 *     SkFontArguments::VariationPosition fVariationPosition;
 *     ToolUtils::VariationSliders fVariationSliders;
 * }
 * ```
 */
public open class ColrV1GM public constructor(
  testName: String?,
  codepoints: SkSpan<UInt>,
  skewX: SkScalar,
  rotateDeg: SkScalar,
  specifiedVariations: initializer_list<SkFontArguments.VariationPosition.Coordinate>,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString fTestName
   * ```
   */
  private var fTestName: String = TODO("Initialize fTestName")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> fTypeface
   * ```
   */
  private var fTypeface: SkSp<SkTypeface> = TODO("Initialize fTypeface")

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const uint32_t> fCodepoints
   * ```
   */
  private var fCodepoints: Int = TODO("Initialize fCodepoints")

  /**
   * C++ original:
   * ```cpp
   * SkScalar fSkewX
   * ```
   */
  private var fSkewX: SkScalar = TODO("Initialize fSkewX")

  /**
   * C++ original:
   * ```cpp
   * SkScalar fRotateDeg
   * ```
   */
  private var fRotateDeg: SkScalar = TODO("Initialize fRotateDeg")

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkFontArguments::VariationPosition::Coordinate[]> fCoordinates
   * ```
   */
  private var fCoordinates: Int = TODO("Initialize fCoordinates")

  /**
   * C++ original:
   * ```cpp
   * SkFontArguments::VariationPosition fVariationPosition
   * ```
   */
  private var fVariationPosition: SkFontArguments.VariationPosition =
      TODO("Initialize fVariationPosition")

  /**
   * C++ original:
   * ```cpp
   * ToolUtils::VariationSliders fVariationSliders
   * ```
   */
  private var fVariationSliders: VariationSliders = TODO("Initialize fVariationSliders")

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         if (fVariationPosition.coordinateCount) {
   *             fTypeface = ToolUtils::CreateTypefaceFromResource(kTestFontNameVariable, 0);
   *         } else {
   *             fTypeface = ToolUtils::CreateTypefaceFromResource(kTestFontName, 0);
   *         }
   *         fVariationSliders = ToolUtils::VariationSliders(fTypeface.get(), fVariationPosition);
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
   *         SkASSERT(!fTestName.isEmpty());
   *         SkString gm_name = SkStringPrintf("colrv1_%s", fTestName.c_str());
   *
   *         if (fSkewX) {
   *             gm_name.append(SkStringPrintf("_skew_%.2f", fSkewX));
   *         }
   *
   *         if (fRotateDeg) {
   *             gm_name.append(SkStringPrintf("_rotate_%.2f", fRotateDeg));
   *         }
   *
   *         for (int i = 0; i < fVariationPosition.coordinateCount; ++i) {
   *             SkString tagName = ToolUtils::VariationSliders::tagToString(
   *                     fVariationPosition.coordinates[i].axis);
   *             gm_name.append(SkStringPrintf(
   *                     "_%s_%.2f", tagName.c_str(), fVariationPosition.coordinates[i].value));
   *         }
   *
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
   * bool onGetControls(SkMetaData* controls) override {
   *         return fVariationSliders.writeControls(controls);
   *     }
   * ```
   */
  protected override fun onGetControls(controls: SkMetaData?): Boolean {
    TODO("Implement onGetControls")
  }

  /**
   * C++ original:
   * ```cpp
   * void onSetControls(const SkMetaData& controls) override {
   *         return fVariationSliders.readControls(controls);
   *     }
   * ```
   */
  protected override fun onSetControls(controls: SkMetaData) {
    TODO("Implement onSetControls")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override {
   *         // Sweep tests get a slightly wider canvas so that glyphs from one group fit in one row.
   *         if (fTestName.equals("sweep_varsweep")) {
   *             return SkISize::Make(xWidth + 500, xWidth);
   *         }
   *         return SkISize::Make(xWidth, xWidth);
   *     }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> makeVariedTypeface() {
   *         if (!fTypeface) {
   *             return nullptr;
   *         }
   *         SkSpan<const SkFontArguments::VariationPosition::Coordinate> coords =
   *                 fVariationSliders.getCoordinates();
   *         SkFontArguments::VariationPosition varPos = {coords.data(),
   *                                                      static_cast<int>(coords.size())};
   *         SkFontArguments args;
   *         args.setVariationDesignPosition(varPos);
   *         return fTypeface->makeClone(args);
   *     }
   * ```
   */
  protected fun makeVariedTypeface(): SkSp<SkTypeface> {
    TODO("Implement makeVariedTypeface")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
   *         canvas->drawColor(SK_ColorWHITE);
   *         SkPaint paint;
   *
   *         canvas->translate(xTranslate, 20);
   *
   *         if (!fTypeface) {
   *             *errorMsg = "Did not recognize COLR v1 font format.";
   *             return DrawResult::kSkip;
   *         }
   *
   *         canvas->rotate(fRotateDeg);
   *         canvas->skew(fSkewX, 0);
   *
   *         SkFont font(makeVariedTypeface());
   *
   *         SkFontMetrics metrics;
   *         SkScalar y = 0;
   *         std::vector<SkColor> paint_colors = {
   *                 SK_ColorBLACK, SK_ColorGREEN, SK_ColorRED, SK_ColorBLUE};
   *         auto paint_color_iterator = paint_colors.begin();
   *         for (SkScalar textSize : kTextSizes) {
   *             font.setSize(textSize);
   *             font.getMetrics(&metrics);
   *             font.setHinting(SkFontHinting::kNone);
   *             SkScalar y_shift = -(metrics.fAscent + metrics.fDescent + metrics.fLeading) * 1.2;
   *             y += y_shift;
   *             paint.setColor(*paint_color_iterator);
   *             int x = 0;
   *             // Perform simple line breaking to fit more glyphs into the GM canvas.
   *             for (size_t i = 0; i < fCodepoints.size(); ++i) {
   *                 SkScalar glyphAdvance = font.measureText(
   *                         &fCodepoints[i], sizeof(uint32_t), SkTextEncoding::kUTF32, nullptr);
   *                 if (0 < x && getISize().width() - xTranslate < x + glyphAdvance) {
   *                     y += y_shift;
   *                     x = 0;
   *                 }
   *                 canvas->drawSimpleText(&fCodepoints[i],
   *                                        sizeof(uint32_t),
   *                                        SkTextEncoding::kUTF32,
   *                                        x,
   *                                        y,
   *                                        font,
   *                                        paint);
   *                 x += glyphAdvance + glyphAdvance * 0.05f;
   *             }
   *             paint_color_iterator++;
   *         }
   *         return DrawResult::kOk;
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?, errorMsg: String?): DrawResult {
    TODO("Implement onDraw")
  }
}
