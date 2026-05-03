package org.skia.tests

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkSp
import org.skia.foundation.SkTypeface
import org.skia.math.SkISize
import org.skia.math.SkScalar
import org.skia.tools.SkMetaData
import org.skia.tools.VariationSliders

/**
 * C++ original:
 * ```cpp
 * class FontScalerDistortableGM : public GM {
 * public:
 *     FontScalerDistortableGM() {
 *         this->setBGColor(0xFFFFFFFF);
 *     }
 *
 * private:
 *     SkString getName() const override { return SkString("fontscalerdistortable"); }
 *
 *     SkISize getISize() override { return SkISize::Make(550, 700); }
 *
 *     bool fDirty = true;
 *     bool fOverride = false;
 *
 *     ToolUtils::VariationSliders fVariationSliders;
 *
 *     bool onGetControls(SkMetaData* controls) override {
 *         controls->setBool("Override", fOverride);
 *         return fVariationSliders.writeControls(controls);
 *     }
 *
 *     void onSetControls(const SkMetaData& controls) override {
 *         bool oldOverride = fOverride;
 *         controls.findBool("Override", &fOverride);
 *         if (fOverride != oldOverride) {
 *             fDirty = true;
 *         }
 *
 *         return fVariationSliders.readControls(controls, &fDirty);
 *     }
 *
 *     struct Info {
 *         sk_sp<SkTypeface> distortable;
 *         SkFourByteTag axisTag;
 *         SkScalar axisMin;
 *         SkScalar axisMax;
 *     } fInfo;
 *
 *     void onOnceBeforeDraw() override {
 *         constexpr SkFourByteTag wght = SkSetFourByteTag('w','g','h','t');
 *         //constexpr SkFourByteTag wdth = SkSetFourByteTag('w','d','t','h');
 *         fInfo = {
 *             ToolUtils::CreateTypefaceFromResource("fonts/Distortable.ttf"), wght, 0.5f, 2.0f
 *             //SkTypeface::MakeFromFile("/Library/Fonts/Skia.ttf"), wght, 0.48f, 3.2f
 *             //ToolUtils::CreateTestTypeface("Skia", SkFontStyle()), wdth, 0.62f, 1.3f
 *             //SkTypeface::MakeFromFile("/System/Library/Fonts/SFNS.ttf"), wght, 100.0f, 900.0f
 *             //ToolUtils::CreateTestTypeface(".SF NS", SkFontStyle()), wght, 100.0f, 900.0f
 *         };
 *
 *         if (!fInfo.distortable) {
 *             fInfo.distortable = ToolUtils::DefaultPortableTypeface();
 *         }
 *         SkASSERT(fInfo.distortable);
 *         fVariationSliders = ToolUtils::VariationSliders(fInfo.distortable.get());
 *     }
 *
 *     inline static constexpr int rows = 2;
 *     inline static constexpr int cols = 5;
 *     sk_sp<SkTypeface> typeface[rows][cols];
 *
 *     void updateTypefaces() {
 *         sk_sp<SkFontMgr> fontMgr = ToolUtils::TestFontMgr();
 *
 *         std::unique_ptr<SkStreamAsset> distortableStream( fInfo.distortable
 *                                                         ? fInfo.distortable->openStream(nullptr)
 *                                                         : nullptr);
 *         for (int row = 0; row < rows; ++row) {
 *             for (int col = 0; col < cols; ++col) {
 *                 using Coordinate = SkFontArguments::VariationPosition::Coordinate;
 *                 SkFontArguments::VariationPosition position;
 *                 Coordinate coordinates[2];
 *
 *                 if (fOverride) {
 *                     SkSpan<const Coordinate> user_coordinates = fVariationSliders.getCoordinates();
 *                     position = {user_coordinates.data(), static_cast<int>(user_coordinates.size())};
 *
 *                 } else {
 *                     const int coordinateCount = 2;
 *                     SkScalar styleValue = SkScalarInterp(fInfo.axisMin, fInfo.axisMax,
 *                                                          SkScalar(row*cols + col) / (rows*cols));
 *                     coordinates[0] = {fInfo.axisTag, styleValue};
 *                     coordinates[1] = {fInfo.axisTag, styleValue};
 *                     position = {coordinates, static_cast<int>(coordinateCount)};
 *                 }
 *
 *                 typeface[row][col] = [&]() -> sk_sp<SkTypeface> {
 *                     if (row == 0 && fInfo.distortable) {
 *                         return fInfo.distortable->makeClone(
 *                                 SkFontArguments().setVariationDesignPosition(position));
 *                     }
 *                     if (distortableStream) {
 *                         return fontMgr->makeFromStream(distortableStream->duplicate(),
 *                                 SkFontArguments().setVariationDesignPosition(position));
 *                     }
 *                     return nullptr;
 *                 }();
 *             }
 *         }
 *         fDirty = false;
 *     }
 *
 *     DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
 *         if (fDirty) {
 *             this->updateTypefaces();
 *         }
 *
 *         SkPaint paint;
 *         paint.setAntiAlias(true);
 *         SkFont font;
 *         font.setEdging(SkFont::Edging::kSubpixelAntiAlias);
 *
 *         const char* text = "abc";
 *         const size_t textLen = strlen(text);
 *
 *         for (int row = 0; row < rows; ++row) {
 *             for (int col = 0; col < cols; ++col) {
 *                 SkScalar x = SkIntToScalar(10);
 *                 SkScalar y = SkIntToScalar(20);
 *
 *                 font.setTypeface(typeface[row][col] ? typeface[row][col] :
 *                                                       ToolUtils::DefaultPortableTypeface());
 *
 *                 SkAutoCanvasRestore acr(canvas, true);
 *                 canvas->translate(SkIntToScalar(30 + col * 100), SkIntToScalar(20));
 *                 canvas->rotate(SkIntToScalar(col * 5), x, y * 10);
 *
 *                 {
 *                     SkPaint p;
 *                     p.setAntiAlias(true);
 *                     SkRect r;
 *                     r.setLTRB(x - 3, 15, x - 1, 280);
 *                     canvas->drawRect(r, p);
 *                 }
 *
 *                 for (int ps = 6; ps <= 22; ps++) {
 *                     font.setSize(SkIntToScalar(ps));
 *                     canvas->drawSimpleText(text, textLen, SkTextEncoding::kUTF8, x, y, font, paint);
 *                     y += font.getMetrics(nullptr);
 *                 }
 *             }
 *             canvas->translate(0, SkIntToScalar(360));
 *             font.setSubpixel(true);
 *             font.setLinearMetrics(true);
 *             font.setBaselineSnap(false);
 *         }
 *         return DrawResult::kOk;
 *     }
 * }
 * ```
 */
public open class FontScalerDistortableGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * bool fDirty = true
   * ```
   */
  private var fDirty: Boolean = TODO("Initialize fDirty")

  /**
   * C++ original:
   * ```cpp
   * bool fOverride = false
   * ```
   */
  private var fOverride: Boolean = TODO("Initialize fOverride")

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
   * struct Info {
   *         sk_sp<SkTypeface> distortable;
   *         SkFourByteTag axisTag;
   *         SkScalar axisMin;
   *         SkScalar axisMax;
   *     } fInfo
   * ```
   */
  private var fInfo: Info = TODO("Initialize fInfo")

  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int rows = 2
   * ```
   */
  private var typeface: Array<SkSp<SkTypeface>> = TODO("Initialize typeface")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("fontscalerdistortable"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(550, 700); }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onGetControls(SkMetaData* controls) override {
   *         controls->setBool("Override", fOverride);
   *         return fVariationSliders.writeControls(controls);
   *     }
   * ```
   */
  public override fun onGetControls(controls: SkMetaData?): Boolean {
    TODO("Implement onGetControls")
  }

  /**
   * C++ original:
   * ```cpp
   * void onSetControls(const SkMetaData& controls) override {
   *         bool oldOverride = fOverride;
   *         controls.findBool("Override", &fOverride);
   *         if (fOverride != oldOverride) {
   *             fDirty = true;
   *         }
   *
   *         return fVariationSliders.readControls(controls, &fDirty);
   *     }
   * ```
   */
  public override fun onSetControls(controls: SkMetaData) {
    TODO("Implement onSetControls")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         constexpr SkFourByteTag wght = SkSetFourByteTag('w','g','h','t');
   *         //constexpr SkFourByteTag wdth = SkSetFourByteTag('w','d','t','h');
   *         fInfo = {
   *             ToolUtils::CreateTypefaceFromResource("fonts/Distortable.ttf"), wght, 0.5f, 2.0f
   *             //SkTypeface::MakeFromFile("/Library/Fonts/Skia.ttf"), wght, 0.48f, 3.2f
   *             //ToolUtils::CreateTestTypeface("Skia", SkFontStyle()), wdth, 0.62f, 1.3f
   *             //SkTypeface::MakeFromFile("/System/Library/Fonts/SFNS.ttf"), wght, 100.0f, 900.0f
   *             //ToolUtils::CreateTestTypeface(".SF NS", SkFontStyle()), wght, 100.0f, 900.0f
   *         };
   *
   *         if (!fInfo.distortable) {
   *             fInfo.distortable = ToolUtils::DefaultPortableTypeface();
   *         }
   *         SkASSERT(fInfo.distortable);
   *         fVariationSliders = ToolUtils::VariationSliders(fInfo.distortable.get());
   *     }
   * ```
   */
  public override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void updateTypefaces() {
   *         sk_sp<SkFontMgr> fontMgr = ToolUtils::TestFontMgr();
   *
   *         std::unique_ptr<SkStreamAsset> distortableStream( fInfo.distortable
   *                                                         ? fInfo.distortable->openStream(nullptr)
   *                                                         : nullptr);
   *         for (int row = 0; row < rows; ++row) {
   *             for (int col = 0; col < cols; ++col) {
   *                 using Coordinate = SkFontArguments::VariationPosition::Coordinate;
   *                 SkFontArguments::VariationPosition position;
   *                 Coordinate coordinates[2];
   *
   *                 if (fOverride) {
   *                     SkSpan<const Coordinate> user_coordinates = fVariationSliders.getCoordinates();
   *                     position = {user_coordinates.data(), static_cast<int>(user_coordinates.size())};
   *
   *                 } else {
   *                     const int coordinateCount = 2;
   *                     SkScalar styleValue = SkScalarInterp(fInfo.axisMin, fInfo.axisMax,
   *                                                          SkScalar(row*cols + col) / (rows*cols));
   *                     coordinates[0] = {fInfo.axisTag, styleValue};
   *                     coordinates[1] = {fInfo.axisTag, styleValue};
   *                     position = {coordinates, static_cast<int>(coordinateCount)};
   *                 }
   *
   *                 typeface[row][col] = [&]() -> sk_sp<SkTypeface> {
   *                     if (row == 0 && fInfo.distortable) {
   *                         return fInfo.distortable->makeClone(
   *                                 SkFontArguments().setVariationDesignPosition(position));
   *                     }
   *                     if (distortableStream) {
   *                         return fontMgr->makeFromStream(distortableStream->duplicate(),
   *                                 SkFontArguments().setVariationDesignPosition(position));
   *                     }
   *                     return nullptr;
   *                 }();
   *             }
   *         }
   *         fDirty = false;
   *     }
   * ```
   */
  private fun updateTypefaces() {
    TODO("Implement updateTypefaces")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
   *         if (fDirty) {
   *             this->updateTypefaces();
   *         }
   *
   *         SkPaint paint;
   *         paint.setAntiAlias(true);
   *         SkFont font;
   *         font.setEdging(SkFont::Edging::kSubpixelAntiAlias);
   *
   *         const char* text = "abc";
   *         const size_t textLen = strlen(text);
   *
   *         for (int row = 0; row < rows; ++row) {
   *             for (int col = 0; col < cols; ++col) {
   *                 SkScalar x = SkIntToScalar(10);
   *                 SkScalar y = SkIntToScalar(20);
   *
   *                 font.setTypeface(typeface[row][col] ? typeface[row][col] :
   *                                                       ToolUtils::DefaultPortableTypeface());
   *
   *                 SkAutoCanvasRestore acr(canvas, true);
   *                 canvas->translate(SkIntToScalar(30 + col * 100), SkIntToScalar(20));
   *                 canvas->rotate(SkIntToScalar(col * 5), x, y * 10);
   *
   *                 {
   *                     SkPaint p;
   *                     p.setAntiAlias(true);
   *                     SkRect r;
   *                     r.setLTRB(x - 3, 15, x - 1, 280);
   *                     canvas->drawRect(r, p);
   *                 }
   *
   *                 for (int ps = 6; ps <= 22; ps++) {
   *                     font.setSize(SkIntToScalar(ps));
   *                     canvas->drawSimpleText(text, textLen, SkTextEncoding::kUTF8, x, y, font, paint);
   *                     y += font.getMetrics(nullptr);
   *                 }
   *             }
   *             canvas->translate(0, SkIntToScalar(360));
   *             font.setSubpixel(true);
   *             font.setLinearMetrics(true);
   *             font.setBaselineSnap(false);
   *         }
   *         return DrawResult::kOk;
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?, errorMsg: String?): DrawResult {
    TODO("Implement onDraw")
  }

  public data class Info public constructor(
    public var distortable: SkSp<SkTypeface>,
    public var axisTag: Int,
    public var axisMin: SkScalar,
    public var axisMax: SkScalar,
  )

  public companion object {
    private val rows: Int = TODO("Initialize rows")

    private val cols: Int = TODO("Initialize cols")
  }
}
