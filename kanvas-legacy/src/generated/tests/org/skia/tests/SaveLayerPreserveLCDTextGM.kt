package org.skia.tests

import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.core.SkCanvasSaveLayerFlags
import org.skia.math.SkISize
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SaveLayerPreserveLCDTextGM : public skiagm::GM {
 *     static constexpr SkScalar kTextHeight = 36;
 *
 *     SkString getName() const override { return SkString("savelayerpreservelcdtext"); }
 *
 *     SkISize getISize() override { return {620, 300}; }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         drawText(canvas, SkString("SaveLayer PreserveLCDText"), 50,
 *                  SkCanvas::kPreserveLCDText_SaveLayerFlag);
 *         drawText(canvas, SkString("SaveLayer Default (LCDText not preserved)"), 150, 0);
 *     }
 *
 *     void drawText(SkCanvas* canvas,
 *                   const SkString& string,
 *                   int y,
 *                   SkCanvas::SaveLayerFlags saveLayerFlags) {
 *         SkCanvas::SaveLayerRec rec(nullptr, nullptr, saveLayerFlags);
 *         canvas->saveLayer(rec);
 *         SkPaint paint;
 *         paint.setColor(SK_ColorWHITE);
 *         canvas->drawRect(SkRect::MakeXYWH(0, y - 10, 640, kTextHeight + 20), paint);
 *         paint.setColor(SK_ColorBLACK);
 *         SkFont font(ToolUtils::DefaultPortableTypeface(), kTextHeight);
 *         font.setEdging(SkFont::Edging::kSubpixelAntiAlias);
 *         canvas->drawString(string, 10, y, font, paint);
 *         canvas->restore();
 *     }
 * }
 * ```
 */
public open class SaveLayerPreserveLCDTextGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("savelayerpreservelcdtext"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {620, 300}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         drawText(canvas, SkString("SaveLayer PreserveLCDText"), 50,
   *                  SkCanvas::kPreserveLCDText_SaveLayerFlag);
   *         drawText(canvas, SkString("SaveLayer Default (LCDText not preserved)"), 150, 0);
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawText(SkCanvas* canvas,
   *                   const SkString& string,
   *                   int y,
   *                   SkCanvas::SaveLayerFlags saveLayerFlags) {
   *         SkCanvas::SaveLayerRec rec(nullptr, nullptr, saveLayerFlags);
   *         canvas->saveLayer(rec);
   *         SkPaint paint;
   *         paint.setColor(SK_ColorWHITE);
   *         canvas->drawRect(SkRect::MakeXYWH(0, y - 10, 640, kTextHeight + 20), paint);
   *         paint.setColor(SK_ColorBLACK);
   *         SkFont font(ToolUtils::DefaultPortableTypeface(), kTextHeight);
   *         font.setEdging(SkFont::Edging::kSubpixelAntiAlias);
   *         canvas->drawString(string, 10, y, font, paint);
   *         canvas->restore();
   *     }
   * ```
   */
  private fun drawText(
    canvas: SkCanvas?,
    string: String,
    y: Int,
    saveLayerFlags: SkCanvasSaveLayerFlags,
  ) {
    TODO("Implement drawText")
  }

  public companion object {
    private val kTextHeight: SkScalar = TODO("Initialize kTextHeight")
  }
}
