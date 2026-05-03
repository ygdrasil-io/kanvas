package org.skia.tests

import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkFont
import org.skia.foundation.SkGlyphID
import org.skia.foundation.SkSp
import org.skia.foundation.SkTypeface
import org.skia.math.SkISize
import org.skia.math.SkPoint
import org.skia.math.SkRSXform
import org.skia.math.SkScalar
import org.skia.memory.SkTDArray

/**
 * C++ original:
 * ```cpp
 * class DrawGlyphsGM : public skiagm::GM {
 * public:
 *     void onOnceBeforeDraw() override {
 *         fTypeface = ToolUtils::CreatePortableTypeface("serif", SkFontStyle());
 *         fFont = SkFont(fTypeface);
 *         fFont.setSubpixel(true);
 *         fFont.setSize(18);
 *         const size_t txtLen = strlen(gText);
 *         fGlyphCount = fFont.countText(gText, txtLen, SkTextEncoding::kUTF8);
 *
 *         fGlyphs.append(fGlyphCount);
 *         fFont.textToGlyphs(gText, txtLen, SkTextEncoding::kUTF8, fGlyphs);
 *
 *         fPositions.append(fGlyphCount);
 *         fFont.getPos(fGlyphs, fPositions);
 *         auto positions = SkSpan(fPositions.begin(), fGlyphCount);
 *
 *         fLength = positions.back().x() - positions.front().x();
 *         fRadius = fLength / SK_FloatPI;
 *         fXforms.append(fGlyphCount);
 *
 *         for (auto [xform, pos] : SkMakeZip(fXforms.begin(), positions)) {
 *             const SkScalar lengthToGlyph = pos.x() - positions.front().x();
 *             const SkScalar angle = SK_FloatPI * (fLength - lengthToGlyph) / fLength;
 *             const SkScalar cos = std::cos(angle);
 *             const SkScalar sin = std::sin(angle);
 *             xform = SkRSXform::Make(sin, cos, fRadius*cos, -fRadius*sin);
 *         }
 *     }
 *
 *     SkString getName() const override { return SkString("drawglyphs"); }
 *
 *     SkISize getISize() override { return SkISize::Make(640, 480); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkSpan<const SkGlyphID> glyphs = {fGlyphs.data(), (size_t)fGlyphCount};
 *         SkSpan<SkPoint> pos = {fPositions.data(), (size_t)fGlyphCount};
 *         canvas->drawGlyphs(glyphs, pos, {50, 100}, fFont, SkPaint{});
 *
 *         canvas->drawGlyphs(glyphs, pos, {50, 120}, fFont, SkPaint{});
 *
 *         // Check bounding box calculation.
 *         for (auto& p : fPositions) {
 *             p += {0, -500};
 *         }
 *         canvas->drawGlyphs(glyphs, pos, {50, 640}, fFont, SkPaint{});
 *
 *         canvas->drawGlyphsRSXform(fGlyphs, fXforms,
 *                            {50 + fLength / 2, 160 + fRadius}, fFont, SkPaint{});
 *
 *         // TODO: add tests for cluster versions of drawGlyphs.
 *     }
 *
 * private:
 *     sk_sp<SkTypeface>   fTypeface;
 *     SkFont fFont;
 *     SkTDArray<SkGlyphID> fGlyphs;
 *     SkTDArray<SkPoint>   fPositions;
 *     SkTDArray<SkRSXform> fXforms;
 *     int fGlyphCount;
 *     SkScalar fRadius;
 *     SkScalar fLength;
 * }
 * ```
 */
public open class DrawGlyphsGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface>   fTypeface
   * ```
   */
  private var fTypeface: SkSp<SkTypeface> = TODO("Initialize fTypeface")

  /**
   * C++ original:
   * ```cpp
   * SkFont fFont
   * ```
   */
  private var fFont: SkFont = TODO("Initialize fFont")

  /**
   * C++ original:
   * ```cpp
   * SkTDArray<SkGlyphID> fGlyphs
   * ```
   */
  private var fGlyphs: SkTDArray<SkGlyphID> = TODO("Initialize fGlyphs")

  /**
   * C++ original:
   * ```cpp
   * SkTDArray<SkPoint>   fPositions
   * ```
   */
  private var fPositions: SkTDArray<SkPoint> = TODO("Initialize fPositions")

  /**
   * C++ original:
   * ```cpp
   * SkTDArray<SkRSXform> fXforms
   * ```
   */
  private var fXforms: SkTDArray<SkRSXform> = TODO("Initialize fXforms")

  /**
   * C++ original:
   * ```cpp
   * int fGlyphCount
   * ```
   */
  private var fGlyphCount: Int = TODO("Initialize fGlyphCount")

  /**
   * C++ original:
   * ```cpp
   * SkScalar fRadius
   * ```
   */
  private var fRadius: SkScalar = TODO("Initialize fRadius")

  /**
   * C++ original:
   * ```cpp
   * SkScalar fLength
   * ```
   */
  private var fLength: SkScalar = TODO("Initialize fLength")

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         fTypeface = ToolUtils::CreatePortableTypeface("serif", SkFontStyle());
   *         fFont = SkFont(fTypeface);
   *         fFont.setSubpixel(true);
   *         fFont.setSize(18);
   *         const size_t txtLen = strlen(gText);
   *         fGlyphCount = fFont.countText(gText, txtLen, SkTextEncoding::kUTF8);
   *
   *         fGlyphs.append(fGlyphCount);
   *         fFont.textToGlyphs(gText, txtLen, SkTextEncoding::kUTF8, fGlyphs);
   *
   *         fPositions.append(fGlyphCount);
   *         fFont.getPos(fGlyphs, fPositions);
   *         auto positions = SkSpan(fPositions.begin(), fGlyphCount);
   *
   *         fLength = positions.back().x() - positions.front().x();
   *         fRadius = fLength / SK_FloatPI;
   *         fXforms.append(fGlyphCount);
   *
   *         for (auto [xform, pos] : SkMakeZip(fXforms.begin(), positions)) {
   *             const SkScalar lengthToGlyph = pos.x() - positions.front().x();
   *             const SkScalar angle = SK_FloatPI * (fLength - lengthToGlyph) / fLength;
   *             const SkScalar cos = std::cos(angle);
   *             const SkScalar sin = std::sin(angle);
   *             xform = SkRSXform::Make(sin, cos, fRadius*cos, -fRadius*sin);
   *         }
   *     }
   * ```
   */
  public override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("drawglyphs"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(640, 480); }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkSpan<const SkGlyphID> glyphs = {fGlyphs.data(), (size_t)fGlyphCount};
   *         SkSpan<SkPoint> pos = {fPositions.data(), (size_t)fGlyphCount};
   *         canvas->drawGlyphs(glyphs, pos, {50, 100}, fFont, SkPaint{});
   *
   *         canvas->drawGlyphs(glyphs, pos, {50, 120}, fFont, SkPaint{});
   *
   *         // Check bounding box calculation.
   *         for (auto& p : fPositions) {
   *             p += {0, -500};
   *         }
   *         canvas->drawGlyphs(glyphs, pos, {50, 640}, fFont, SkPaint{});
   *
   *         canvas->drawGlyphsRSXform(fGlyphs, fXforms,
   *                            {50 + fLength / 2, 160 + fRadius}, fFont, SkPaint{});
   *
   *         // TODO: add tests for cluster versions of drawGlyphs.
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
