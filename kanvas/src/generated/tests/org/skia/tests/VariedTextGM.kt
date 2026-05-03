package org.skia.tests

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.IntArray
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkColor
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSp
import org.skia.foundation.SkTypeface
import org.skia.math.SkISize
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class VariedTextGM : public skiagm::GM {
 * public:
 *     VariedTextGM(bool effectiveClip, bool lcd)
 *         : fEffectiveClip(effectiveClip)
 *         , fLCD(lcd) {
 *     }
 *
 * protected:
 *     SkString getName() const override {
 *         SkString name("varied_text");
 *         if (fEffectiveClip) {
 *             name.append("_clipped");
 *         } else {
 *             name.append("_ignorable_clip");
 *         }
 *         if (fLCD) {
 *             name.append("_lcd");
 *         } else {
 *             name.append("_no_lcd");
 *         }
 *         return name;
 *     }
 *
 *     SkISize getISize() override { return SkISize::Make(640, 480); }
 *
 *     void onOnceBeforeDraw() override {
 *         fPaint.setAntiAlias(true);
 *         fFont.setEdging(fLCD ? SkFont::Edging::kSubpixelAntiAlias : SkFont::Edging::kAntiAlias);
 *
 *         SkISize size = this->getISize();
 *         SkScalar w = SkIntToScalar(size.fWidth);
 *         SkScalar h = SkIntToScalar(size.fHeight);
 *
 *         SkASSERTF(4 == std::size(fTypefaces), "typeface_cnt");
 *         fTypefaces[0] = ToolUtils::CreatePortableTypeface("sans-serif", SkFontStyle());
 *         fTypefaces[1] = ToolUtils::CreatePortableTypeface("sans-serif", SkFontStyle::Bold());
 *         fTypefaces[2] = ToolUtils::CreatePortableTypeface("serif", SkFontStyle());
 *         fTypefaces[3] = ToolUtils::CreatePortableTypeface("serif", SkFontStyle::Bold());
 *
 *         SkRandom random;
 *         for (int i = 0; i < kCnt; ++i) {
 *             int length = random.nextRangeU(kMinLength, kMaxLength);
 *             char text[kMaxLength];
 *             for (int j = 0; j < length; ++j) {
 *                 text[j] = (char)random.nextRangeU('!', 'z');
 *             }
 *             fStrings[i].set(text, length);
 *
 *             fColors[i] = random.nextU();
 *             fColors[i] |= 0xFF000000;
 *             fColors[i] = ToolUtils::color_to_565(fColors[i]);
 *
 *             constexpr SkScalar kMinPtSize = 8.f;
 *             constexpr SkScalar kMaxPtSize = 32.f;
 *
 *             fPtSizes[i] = random.nextRangeScalar(kMinPtSize, kMaxPtSize);
 *
 *             fTypefaceIndices[i] = random.nextULessThan(std::size(fTypefaces));
 *
 *             SkRect r;
 *             fPaint.setColor(fColors[i]);
 *             fFont.setTypeface(fTypefaces[fTypefaceIndices[i]]);
 *             fFont.setSize(fPtSizes[i]);
 *
 *             fFont.measureText(fStrings[i].c_str(), fStrings[i].size(), SkTextEncoding::kUTF8, &r);
 *             // The set of x,y offsets which place the bounding box inside the GM's border.
 *             SkRect safeRect = SkRect::MakeLTRB(-r.fLeft, -r.fTop, w - r.fRight, h - r.fBottom);
 *             if (safeRect.isEmpty()) {
 *                 // If the bounds don't fit then allow any offset in the GM's border.
 *                 safeRect = SkRect::MakeWH(w, h);
 *             }
 *             fOffsets[i].fX = random.nextRangeScalar(safeRect.fLeft, safeRect.fRight);
 *             fOffsets[i].fY = random.nextRangeScalar(safeRect.fTop, safeRect.fBottom);
 *
 *             fClipRects[i] = r;
 *             fClipRects[i].offset(fOffsets[i].fX, fOffsets[i].fY);
 *             fClipRects[i].outset(2.f, 2.f);
 *
 *             if (fEffectiveClip) {
 *                 fClipRects[i].fRight -= 0.25f * fClipRects[i].width();
 *             }
 *         }
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         for (int i = 0; i < kCnt; ++i) {
 *             fPaint.setColor(fColors[i]);
 *             fFont.setSize(fPtSizes[i]);
 *             fFont.setTypeface(fTypefaces[fTypefaceIndices[i]]);
 *
 *             canvas->save();
 *                 canvas->clipRect(fClipRects[i]);
 *                 canvas->translate(fOffsets[i].fX, fOffsets[i].fY);
 *                 canvas->drawSimpleText(fStrings[i].c_str(), fStrings[i].size(), SkTextEncoding::kUTF8,
 *                                        0, 0, fFont, fPaint);
 *             canvas->restore();
 *         }
 *
 *         // Visualize the clips, but not in bench mode.
 *         if (kBench_Mode != this->getMode()) {
 *             SkPaint wirePaint;
 *             wirePaint.setAntiAlias(true);
 *             wirePaint.setStrokeWidth(0);
 *             wirePaint.setStyle(SkPaint::kStroke_Style);
 *             for (int i = 0; i < kCnt; ++i) {
 *                 canvas->drawRect(fClipRects[i], wirePaint);
 *             }
 *         }
 *     }
 *
 *     bool runAsBench() const override { return true; }
 *
 * private:
 *     inline static constexpr int kCnt = 30;
 *     inline static constexpr int kMinLength = 15;
 *     inline static constexpr int kMaxLength = 40;
 *
 *     bool        fEffectiveClip;
 *     bool        fLCD;
 *     sk_sp<SkTypeface> fTypefaces[4];
 *     SkPaint     fPaint;
 *     SkFont      fFont;
 *
 *     // precomputed for each text draw
 *     SkString        fStrings[kCnt];
 *     SkColor         fColors[kCnt];
 *     SkScalar        fPtSizes[kCnt];
 *     int             fTypefaceIndices[kCnt];
 *     SkPoint         fOffsets[kCnt];
 *     SkRect          fClipRects[kCnt];
 *
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class VariedTextGM public constructor(
  effectiveClip: Boolean,
  lcd: Boolean,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kCnt = 30
   * ```
   */
  private var fEffectiveClip: Boolean = TODO("Initialize fEffectiveClip")

  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kMinLength = 15
   * ```
   */
  private var fLCD: Boolean = TODO("Initialize fLCD")

  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kMaxLength = 40
   * ```
   */
  private var fTypefaces: Array<SkSp<SkTypeface>> = TODO("Initialize fTypefaces")

  /**
   * C++ original:
   * ```cpp
   * bool        fEffectiveClip
   * ```
   */
  private var fPaint: SkPaint = TODO("Initialize fPaint")

  /**
   * C++ original:
   * ```cpp
   * bool        fLCD
   * ```
   */
  private var fFont: SkFont = TODO("Initialize fFont")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> fTypefaces[4]
   * ```
   */
  private var fStrings: Array<String> = TODO("Initialize fStrings")

  /**
   * C++ original:
   * ```cpp
   * SkPaint     fPaint
   * ```
   */
  private var fColors: Array<SkColor> = TODO("Initialize fColors")

  /**
   * C++ original:
   * ```cpp
   * SkFont      fFont
   * ```
   */
  private var fPtSizes: Array<SkScalar> = TODO("Initialize fPtSizes")

  /**
   * C++ original:
   * ```cpp
   * SkString        fStrings[kCnt]
   * ```
   */
  private var fTypefaceIndices: IntArray = TODO("Initialize fTypefaceIndices")

  /**
   * C++ original:
   * ```cpp
   * SkColor         fColors[kCnt]
   * ```
   */
  private var fOffsets: Array<SkPoint> = TODO("Initialize fOffsets")

  /**
   * C++ original:
   * ```cpp
   * SkScalar        fPtSizes[kCnt]
   * ```
   */
  private var fClipRects: Array<SkRect> = TODO("Initialize fClipRects")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override {
   *         SkString name("varied_text");
   *         if (fEffectiveClip) {
   *             name.append("_clipped");
   *         } else {
   *             name.append("_ignorable_clip");
   *         }
   *         if (fLCD) {
   *             name.append("_lcd");
   *         } else {
   *             name.append("_no_lcd");
   *         }
   *         return name;
   *     }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(640, 480); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         fPaint.setAntiAlias(true);
   *         fFont.setEdging(fLCD ? SkFont::Edging::kSubpixelAntiAlias : SkFont::Edging::kAntiAlias);
   *
   *         SkISize size = this->getISize();
   *         SkScalar w = SkIntToScalar(size.fWidth);
   *         SkScalar h = SkIntToScalar(size.fHeight);
   *
   *         SkASSERTF(4 == std::size(fTypefaces), "typeface_cnt");
   *         fTypefaces[0] = ToolUtils::CreatePortableTypeface("sans-serif", SkFontStyle());
   *         fTypefaces[1] = ToolUtils::CreatePortableTypeface("sans-serif", SkFontStyle::Bold());
   *         fTypefaces[2] = ToolUtils::CreatePortableTypeface("serif", SkFontStyle());
   *         fTypefaces[3] = ToolUtils::CreatePortableTypeface("serif", SkFontStyle::Bold());
   *
   *         SkRandom random;
   *         for (int i = 0; i < kCnt; ++i) {
   *             int length = random.nextRangeU(kMinLength, kMaxLength);
   *             char text[kMaxLength];
   *             for (int j = 0; j < length; ++j) {
   *                 text[j] = (char)random.nextRangeU('!', 'z');
   *             }
   *             fStrings[i].set(text, length);
   *
   *             fColors[i] = random.nextU();
   *             fColors[i] |= 0xFF000000;
   *             fColors[i] = ToolUtils::color_to_565(fColors[i]);
   *
   *             constexpr SkScalar kMinPtSize = 8.f;
   *             constexpr SkScalar kMaxPtSize = 32.f;
   *
   *             fPtSizes[i] = random.nextRangeScalar(kMinPtSize, kMaxPtSize);
   *
   *             fTypefaceIndices[i] = random.nextULessThan(std::size(fTypefaces));
   *
   *             SkRect r;
   *             fPaint.setColor(fColors[i]);
   *             fFont.setTypeface(fTypefaces[fTypefaceIndices[i]]);
   *             fFont.setSize(fPtSizes[i]);
   *
   *             fFont.measureText(fStrings[i].c_str(), fStrings[i].size(), SkTextEncoding::kUTF8, &r);
   *             // The set of x,y offsets which place the bounding box inside the GM's border.
   *             SkRect safeRect = SkRect::MakeLTRB(-r.fLeft, -r.fTop, w - r.fRight, h - r.fBottom);
   *             if (safeRect.isEmpty()) {
   *                 // If the bounds don't fit then allow any offset in the GM's border.
   *                 safeRect = SkRect::MakeWH(w, h);
   *             }
   *             fOffsets[i].fX = random.nextRangeScalar(safeRect.fLeft, safeRect.fRight);
   *             fOffsets[i].fY = random.nextRangeScalar(safeRect.fTop, safeRect.fBottom);
   *
   *             fClipRects[i] = r;
   *             fClipRects[i].offset(fOffsets[i].fX, fOffsets[i].fY);
   *             fClipRects[i].outset(2.f, 2.f);
   *
   *             if (fEffectiveClip) {
   *                 fClipRects[i].fRight -= 0.25f * fClipRects[i].width();
   *             }
   *         }
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
   *         for (int i = 0; i < kCnt; ++i) {
   *             fPaint.setColor(fColors[i]);
   *             fFont.setSize(fPtSizes[i]);
   *             fFont.setTypeface(fTypefaces[fTypefaceIndices[i]]);
   *
   *             canvas->save();
   *                 canvas->clipRect(fClipRects[i]);
   *                 canvas->translate(fOffsets[i].fX, fOffsets[i].fY);
   *                 canvas->drawSimpleText(fStrings[i].c_str(), fStrings[i].size(), SkTextEncoding::kUTF8,
   *                                        0, 0, fFont, fPaint);
   *             canvas->restore();
   *         }
   *
   *         // Visualize the clips, but not in bench mode.
   *         if (kBench_Mode != this->getMode()) {
   *             SkPaint wirePaint;
   *             wirePaint.setAntiAlias(true);
   *             wirePaint.setStrokeWidth(0);
   *             wirePaint.setStyle(SkPaint::kStroke_Style);
   *             for (int i = 0; i < kCnt; ++i) {
   *                 canvas->drawRect(fClipRects[i], wirePaint);
   *             }
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * bool runAsBench() const override { return true; }
   * ```
   */
  protected override fun runAsBench(): Boolean {
    TODO("Implement runAsBench")
  }

  public companion object {
    private val kCnt: Int = TODO("Initialize kCnt")

    private val kMinLength: Int = TODO("Initialize kMinLength")

    private val kMaxLength: Int = TODO("Initialize kMaxLength")
  }
}
