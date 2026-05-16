package org.skia.tests

import kotlin.IntArray
import kotlin.String
import kotlin.ULong
import org.skia.core.SkCanvas
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSp
import org.skia.foundation.SkTypeface
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class Poly2PolyGM : public skiagm::GM {
 * public:
 *     Poly2PolyGM() {}
 *
 * protected:
 *     SkString getName() const override { return SkString("poly2poly"); }
 *
 *     SkISize getISize() override { return SkISize::Make(835, 840); }
 *
 *     static void doDraw(SkCanvas* canvas, const SkFont& font, SkPaint* paint, const int isrc[],
 *                        const int idst[], size_t count) {
 *         SkPoint src[4], dst[4];
 *
 *         for (size_t i = 0; i < count; i++) {
 *             src[i].set(SkIntToScalar(isrc[2*i+0]), SkIntToScalar(isrc[2*i+1]));
 *             dst[i].set(SkIntToScalar(idst[2*i+0]), SkIntToScalar(idst[2*i+1]));
 *         }
 *
 *         canvas->save();
 *         if (auto mx = SkMatrix::PolyToPoly({src, count}, {dst, count})) {
 *             canvas->concat(*mx);
 *         }
 *
 *         paint->setColor(SK_ColorGRAY);
 *         paint->setStyle(SkPaint::kStroke_Style);
 *         const SkScalar D = 64;
 *         canvas->drawRect(SkRect::MakeWH(D, D), *paint);
 *         canvas->drawLine(0, 0, D, D, *paint);
 *         canvas->drawLine(0, D, D, 0, *paint);
 *
 *         SkFontMetrics fm;
 *         font.getMetrics(&fm);
 *         paint->setColor(SK_ColorRED);
 *         paint->setStyle(SkPaint::kFill_Style);
 *         SkScalar x = D/2;
 *         SkScalar y = D/2 - (fm.fAscent + fm.fDescent)/2;
 *         uint16_t glyphID = 3; // X
 *         SkTextUtils::Draw(canvas, &glyphID, sizeof(glyphID), SkTextEncoding::kGlyphID, x, y,
 *                           font, *paint, SkTextUtils::kCenter_Align);
 *         canvas->restore();
 *     }
 *
 *     void onOnceBeforeDraw() override {
 *         fEmFace = ToolUtils::CreateTypefaceFromResource("fonts/Em.ttf");
 *         if (!fEmFace) {
 *             fEmFace = ToolUtils::DefaultPortableTypeface();
 *         }
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPaint paint;
 *         paint.setAntiAlias(true);
 *         paint.setStrokeWidth(SkIntToScalar(4));
 *         SkFont font(fEmFace, 40);
 *
 *         canvas->save();
 *         canvas->translate(SkIntToScalar(10), SkIntToScalar(10));
 *         // translate (1 point)
 *         const int src1[] = { 0, 0 };
 *         const int dst1[] = { 5, 5 };
 *         doDraw(canvas, font, &paint, src1, dst1, 1);
 *         canvas->restore();
 *
 *         canvas->save();
 *         canvas->translate(SkIntToScalar(160), SkIntToScalar(10));
 *         // rotate/uniform-scale (2 points)
 *         const int src2[] = { 32, 32, 64, 32 };
 *         const int dst2[] = { 32, 32, 64, 48 };
 *         doDraw(canvas, font, &paint, src2, dst2, 2);
 *         canvas->restore();
 *
 *         canvas->save();
 *         canvas->translate(SkIntToScalar(10), SkIntToScalar(110));
 *         // rotate/skew (3 points)
 *         const int src3[] = { 0, 0, 64, 0, 0, 64 };
 *         const int dst3[] = { 0, 0, 96, 0, 24, 64 };
 *         doDraw(canvas, font, &paint, src3, dst3, 3);
 *         canvas->restore();
 *
 *         canvas->save();
 *         canvas->translate(SkIntToScalar(160), SkIntToScalar(110));
 *         // perspective (4 points)
 *         const int src4[] = { 0, 0, 64, 0, 64, 64, 0, 64 };
 *         const int dst4[] = { 0, 0, 96, 0, 64, 96, 0, 64 };
 *         doDraw(canvas, font, &paint, src4, dst4, 4);
 *         canvas->restore();
 *     }
 *
 * private:
 *     using INHERITED = skiagm::GM;
 *     sk_sp<SkTypeface> fEmFace;
 * }
 * ```
 */
public open class Poly2PolyGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> fEmFace
   * ```
   */
  private var fEmFace: SkSp<SkTypeface> = TODO("Initialize fEmFace")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("poly2poly"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(835, 840); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         fEmFace = ToolUtils::CreateTypefaceFromResource("fonts/Em.ttf");
   *         if (!fEmFace) {
   *             fEmFace = ToolUtils::DefaultPortableTypeface();
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
   *         SkPaint paint;
   *         paint.setAntiAlias(true);
   *         paint.setStrokeWidth(SkIntToScalar(4));
   *         SkFont font(fEmFace, 40);
   *
   *         canvas->save();
   *         canvas->translate(SkIntToScalar(10), SkIntToScalar(10));
   *         // translate (1 point)
   *         const int src1[] = { 0, 0 };
   *         const int dst1[] = { 5, 5 };
   *         doDraw(canvas, font, &paint, src1, dst1, 1);
   *         canvas->restore();
   *
   *         canvas->save();
   *         canvas->translate(SkIntToScalar(160), SkIntToScalar(10));
   *         // rotate/uniform-scale (2 points)
   *         const int src2[] = { 32, 32, 64, 32 };
   *         const int dst2[] = { 32, 32, 64, 48 };
   *         doDraw(canvas, font, &paint, src2, dst2, 2);
   *         canvas->restore();
   *
   *         canvas->save();
   *         canvas->translate(SkIntToScalar(10), SkIntToScalar(110));
   *         // rotate/skew (3 points)
   *         const int src3[] = { 0, 0, 64, 0, 0, 64 };
   *         const int dst3[] = { 0, 0, 96, 0, 24, 64 };
   *         doDraw(canvas, font, &paint, src3, dst3, 3);
   *         canvas->restore();
   *
   *         canvas->save();
   *         canvas->translate(SkIntToScalar(160), SkIntToScalar(110));
   *         // perspective (4 points)
   *         const int src4[] = { 0, 0, 64, 0, 64, 64, 0, 64 };
   *         const int dst4[] = { 0, 0, 96, 0, 64, 96, 0, 64 };
   *         doDraw(canvas, font, &paint, src4, dst4, 4);
   *         canvas->restore();
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
     * static void doDraw(SkCanvas* canvas, const SkFont& font, SkPaint* paint, const int isrc[],
     *                        const int idst[], size_t count) {
     *         SkPoint src[4], dst[4];
     *
     *         for (size_t i = 0; i < count; i++) {
     *             src[i].set(SkIntToScalar(isrc[2*i+0]), SkIntToScalar(isrc[2*i+1]));
     *             dst[i].set(SkIntToScalar(idst[2*i+0]), SkIntToScalar(idst[2*i+1]));
     *         }
     *
     *         canvas->save();
     *         if (auto mx = SkMatrix::PolyToPoly({src, count}, {dst, count})) {
     *             canvas->concat(*mx);
     *         }
     *
     *         paint->setColor(SK_ColorGRAY);
     *         paint->setStyle(SkPaint::kStroke_Style);
     *         const SkScalar D = 64;
     *         canvas->drawRect(SkRect::MakeWH(D, D), *paint);
     *         canvas->drawLine(0, 0, D, D, *paint);
     *         canvas->drawLine(0, D, D, 0, *paint);
     *
     *         SkFontMetrics fm;
     *         font.getMetrics(&fm);
     *         paint->setColor(SK_ColorRED);
     *         paint->setStyle(SkPaint::kFill_Style);
     *         SkScalar x = D/2;
     *         SkScalar y = D/2 - (fm.fAscent + fm.fDescent)/2;
     *         uint16_t glyphID = 3; // X
     *         SkTextUtils::Draw(canvas, &glyphID, sizeof(glyphID), SkTextEncoding::kGlyphID, x, y,
     *                           font, *paint, SkTextUtils::kCenter_Align);
     *         canvas->restore();
     *     }
     * ```
     */
    protected fun doDraw(
      canvas: SkCanvas?,
      font: SkFont,
      paint: SkPaint?,
      isrc: IntArray,
      idst: IntArray,
      count: ULong,
    ) {
      TODO("Implement doDraw")
    }
  }
}
