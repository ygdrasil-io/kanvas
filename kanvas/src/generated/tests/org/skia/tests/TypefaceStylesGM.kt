package org.skia.tests

import kotlin.Array
import kotlin.Boolean
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkSp
import org.skia.foundation.SkTypeface
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class TypefaceStylesGM : public skiagm::GM {
 *     sk_sp<SkTypeface> fFaces[gStylesCount];
 *     bool fApplyKerning;
 *
 * public:
 *     TypefaceStylesGM(bool applyKerning) : fApplyKerning(applyKerning) {}
 *
 * protected:
 *     void onOnceBeforeDraw() override {
 *         for (int i = 0; i < gStylesCount; i++) {
 *             fFaces[i] = ToolUtils::CreateTestTypeface(nullptr, gStyles[i]);
 *         }
 *     }
 *
 *     SkString getName() const override {
 *         SkString name("typefacestyles");
 *         if (fApplyKerning) {
 *             name.append("_kerning");
 *         }
 *         return name;
 *     }
 *
 *     SkISize getISize() override { return SkISize::Make(640, 480); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         // Need to use a font to get dy below.
 *         SkFont font = ToolUtils::DefaultFont();
 *         font.setSize(30);
 *
 *         const char* text = fApplyKerning ? "Type AWAY" : "Hamburgefons";
 *         const size_t textLen = strlen(text);
 *
 *         SkScalar x = SkIntToScalar(10);
 *         SkScalar dy = font.getMetrics(nullptr);
 *         SkASSERT(dy > 0);
 *         SkScalar y = dy;
 *
 *         if (fApplyKerning) {
 *             font.setSubpixel(true);
 *         } else {
 *             font.setLinearMetrics(true);
 *         }
 *
 *         SkPaint paint;
 *         for (int i = 0; i < gStylesCount; i++) {
 *             SkASSERT(fFaces[i]);
 *             font.setTypeface(fFaces[i]);
 *             canvas->drawSimpleText(text, textLen, SkTextEncoding::kUTF8, x, y, font, paint);
 *             if (fApplyKerning) {
 *                 drawKernText(canvas, text, textLen, x + 240, y, font, paint);
 *             }
 *             y += dy;
 *         }
 *     }
 *
 * private:
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class TypefaceStylesGM public constructor(
  applyKerning: Boolean,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> fFaces[gStylesCount]
   * ```
   */
  private var fFaces: Array<SkSp<SkTypeface>> = TODO("Initialize fFaces")

  /**
   * C++ original:
   * ```cpp
   * bool fApplyKerning
   * ```
   */
  private var fApplyKerning: Boolean = TODO("Initialize fApplyKerning")

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         for (int i = 0; i < gStylesCount; i++) {
   *             fFaces[i] = ToolUtils::CreateTestTypeface(nullptr, gStyles[i]);
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
   * SkString getName() const override {
   *         SkString name("typefacestyles");
   *         if (fApplyKerning) {
   *             name.append("_kerning");
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
   * void onDraw(SkCanvas* canvas) override {
   *         // Need to use a font to get dy below.
   *         SkFont font = ToolUtils::DefaultFont();
   *         font.setSize(30);
   *
   *         const char* text = fApplyKerning ? "Type AWAY" : "Hamburgefons";
   *         const size_t textLen = strlen(text);
   *
   *         SkScalar x = SkIntToScalar(10);
   *         SkScalar dy = font.getMetrics(nullptr);
   *         SkASSERT(dy > 0);
   *         SkScalar y = dy;
   *
   *         if (fApplyKerning) {
   *             font.setSubpixel(true);
   *         } else {
   *             font.setLinearMetrics(true);
   *         }
   *
   *         SkPaint paint;
   *         for (int i = 0; i < gStylesCount; i++) {
   *             SkASSERT(fFaces[i]);
   *             font.setTypeface(fFaces[i]);
   *             canvas->drawSimpleText(text, textLen, SkTextEncoding::kUTF8, x, y, font, paint);
   *             if (fApplyKerning) {
   *                 drawKernText(canvas, text, textLen, x + 240, y, font, paint);
   *             }
   *             y += dy;
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
