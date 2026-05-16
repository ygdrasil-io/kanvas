package org.skia.tests

import kotlin.Boolean
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class LcdTextGM : public skiagm::GM {
 *     static constexpr SkScalar kTextHeight = 36;
 *     SkScalar fY = kTextHeight;
 *
 *     SkString getName() const override { return SkString("lcdtext"); }
 *
 *     SkISize getISize() override { return {640, 480}; }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         fY = kTextHeight;
 *         drawText(canvas, SkString("TEXT: SubpixelTrue LCDRenderTrue"),
 *                  true,  true);
 *         drawText(canvas, SkString("TEXT: SubpixelTrue LCDRenderFalse"),
 *                  true,  false);
 *         drawText(canvas, SkString("TEXT: SubpixelFalse LCDRenderTrue"),
 *                  false, true);
 *         drawText(canvas, SkString("TEXT: SubpixelFalse LCDRenderFalse"),
 *                  false, false);
 *     }
 *
 *     void drawText(SkCanvas* canvas, const SkString& string,
 *                   bool subpixelTextEnabled, bool lcdRenderTextEnabled) {
 *         SkPaint paint;
 *         paint.setColor(SK_ColorBLACK);
 *         paint.setDither(true);
 *         SkFont font(ToolUtils::DefaultPortableTypeface(), kTextHeight);
 *         if (subpixelTextEnabled) {
 *             font.setSubpixel(true);
 *         }
 *         if (lcdRenderTextEnabled) {
 *             font.setEdging(SkFont::Edging::kSubpixelAntiAlias);
 *         }
 *         canvas->drawString(string, 0, fY, font, paint);
 *         fY += kTextHeight;
 *     }
 * }
 * ```
 */
public open class LcdTextGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * static constexpr SkScalar kTextHeight = 36
   * ```
   */
  private var fY: SkScalar = TODO("Initialize fY")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("lcdtext"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {640, 480}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         fY = kTextHeight;
   *         drawText(canvas, SkString("TEXT: SubpixelTrue LCDRenderTrue"),
   *                  true,  true);
   *         drawText(canvas, SkString("TEXT: SubpixelTrue LCDRenderFalse"),
   *                  true,  false);
   *         drawText(canvas, SkString("TEXT: SubpixelFalse LCDRenderTrue"),
   *                  false, true);
   *         drawText(canvas, SkString("TEXT: SubpixelFalse LCDRenderFalse"),
   *                  false, false);
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawText(SkCanvas* canvas, const SkString& string,
   *                   bool subpixelTextEnabled, bool lcdRenderTextEnabled) {
   *         SkPaint paint;
   *         paint.setColor(SK_ColorBLACK);
   *         paint.setDither(true);
   *         SkFont font(ToolUtils::DefaultPortableTypeface(), kTextHeight);
   *         if (subpixelTextEnabled) {
   *             font.setSubpixel(true);
   *         }
   *         if (lcdRenderTextEnabled) {
   *             font.setEdging(SkFont::Edging::kSubpixelAntiAlias);
   *         }
   *         canvas->drawString(string, 0, fY, font, paint);
   *         fY += kTextHeight;
   *     }
   * ```
   */
  private fun drawText(
    canvas: SkCanvas?,
    string: String,
    subpixelTextEnabled: Boolean,
    lcdRenderTextEnabled: Boolean,
  ) {
    TODO("Implement drawText")
  }

  public companion object {
    private val kTextHeight: SkScalar = TODO("Initialize kTextHeight")
  }
}
