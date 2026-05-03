package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class BitmapCopyGM : public skiagm::GM {
 *     SkBitmap    fDst[NUM_CONFIGS];
 *
 *     void onOnceBeforeDraw() override { this->setBGColor(0xFFDDDDDD); }
 *
 *     SkString getName() const override { return SkString("bitmapcopy"); }
 *
 *     SkISize getISize() override { return {540, 330}; }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPaint paint;
 *         SkScalar horizMargin = 10;
 *         SkScalar vertMargin = 10;
 *
 *         SkBitmap src;
 *         src.allocN32Pixels(40, 40, kOpaque_SkAlphaType);
 *         SkCanvas canvasTmp(src);
 *
 *         draw_checks(&canvasTmp, 40, 40);
 *
 *         for (unsigned i = 0; i < NUM_CONFIGS; ++i) {
 *             ToolUtils::copy_to(&fDst[i], gColorTypes[i], src);
 *         }
 *
 *         canvas->clear(0xFFDDDDDD);
 *         paint.setAntiAlias(true);
 *
 *         SkFont font = ToolUtils::DefaultPortableFont();
 *
 *         SkScalar width = SkIntToScalar(40);
 *         SkScalar height = SkIntToScalar(40);
 *         if (font.getSpacing() > height) {
 *             height = font.getSpacing();
 *         }
 *         for (unsigned i = 0; i < NUM_CONFIGS; i++) {
 *             const char* name = color_type_name(src.colorType());
 *             SkScalar textWidth = font.measureText(name, strlen(name), SkTextEncoding::kUTF8);
 *             if (textWidth > width) {
 *                 width = textWidth;
 *             }
 *         }
 *         SkScalar horizOffset = width + horizMargin;
 *         SkScalar vertOffset = height + vertMargin;
 *         canvas->translate(SkIntToScalar(20), SkIntToScalar(20));
 *
 *         for (unsigned i = 0; i < NUM_CONFIGS; i++) {
 *             canvas->save();
 *             // Draw destination config name
 *             const char* name = color_type_name(fDst[i].colorType());
 *             SkScalar textWidth = font.measureText(name, strlen(name), SkTextEncoding::kUTF8);
 *             SkScalar x = (width - textWidth) / SkScalar(2);
 *             SkScalar y = font.getSpacing() / SkScalar(2);
 *             canvas->drawSimpleText(name, strlen(name), SkTextEncoding::kUTF8, x, y, font, paint);
 *
 *             // Draw destination bitmap
 *             canvas->translate(0, vertOffset);
 *             x = (width - 40) / SkScalar(2);
 *             canvas->drawImage(fDst[i].asImage(), x, 0, SkSamplingOptions(), &paint);
 *             canvas->restore();
 *
 *             canvas->translate(horizOffset, 0);
 *         }
 *     }
 * }
 * ```
 */
public open class BitmapCopyGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkBitmap    fDst
   * ```
   */
  private var fDst: SkBitmap = TODO("Initialize fDst")

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override { this->setBGColor(0xFFDDDDDD); }
   * ```
   */
  public override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("bitmapcopy"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {540, 330}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkPaint paint;
   *         SkScalar horizMargin = 10;
   *         SkScalar vertMargin = 10;
   *
   *         SkBitmap src;
   *         src.allocN32Pixels(40, 40, kOpaque_SkAlphaType);
   *         SkCanvas canvasTmp(src);
   *
   *         draw_checks(&canvasTmp, 40, 40);
   *
   *         for (unsigned i = 0; i < NUM_CONFIGS; ++i) {
   *             ToolUtils::copy_to(&fDst[i], gColorTypes[i], src);
   *         }
   *
   *         canvas->clear(0xFFDDDDDD);
   *         paint.setAntiAlias(true);
   *
   *         SkFont font = ToolUtils::DefaultPortableFont();
   *
   *         SkScalar width = SkIntToScalar(40);
   *         SkScalar height = SkIntToScalar(40);
   *         if (font.getSpacing() > height) {
   *             height = font.getSpacing();
   *         }
   *         for (unsigned i = 0; i < NUM_CONFIGS; i++) {
   *             const char* name = color_type_name(src.colorType());
   *             SkScalar textWidth = font.measureText(name, strlen(name), SkTextEncoding::kUTF8);
   *             if (textWidth > width) {
   *                 width = textWidth;
   *             }
   *         }
   *         SkScalar horizOffset = width + horizMargin;
   *         SkScalar vertOffset = height + vertMargin;
   *         canvas->translate(SkIntToScalar(20), SkIntToScalar(20));
   *
   *         for (unsigned i = 0; i < NUM_CONFIGS; i++) {
   *             canvas->save();
   *             // Draw destination config name
   *             const char* name = color_type_name(fDst[i].colorType());
   *             SkScalar textWidth = font.measureText(name, strlen(name), SkTextEncoding::kUTF8);
   *             SkScalar x = (width - textWidth) / SkScalar(2);
   *             SkScalar y = font.getSpacing() / SkScalar(2);
   *             canvas->drawSimpleText(name, strlen(name), SkTextEncoding::kUTF8, x, y, font, paint);
   *
   *             // Draw destination bitmap
   *             canvas->translate(0, vertOffset);
   *             x = (width - 40) / SkScalar(2);
   *             canvas->drawImage(fDst[i].asImage(), x, 0, SkSamplingOptions(), &paint);
   *             canvas->restore();
   *
   *             canvas->translate(horizOffset, 0);
   *         }
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
