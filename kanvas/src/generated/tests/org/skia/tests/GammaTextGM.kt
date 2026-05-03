package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class GammaTextGM : public skiagm::GM {
 * protected:
 *     SkString getName() const override { return SkString("gammatext"); }
 *
 *     SkISize getISize() override { return SkISize::Make(1024, HEIGHT); }
 *
 *     static void drawGrad(SkCanvas* canvas) {
 *         const SkPoint pts[] = { { 0, 0 }, { 0, SkIntToScalar(HEIGHT) } };
 *
 *         canvas->clear(SK_ColorRED);
 *         SkPaint paint;
 *         paint.setShader(make_heatGradient(pts));
 *         SkRect r = { 0, 0, SkIntToScalar(1024), SkIntToScalar(HEIGHT) };
 *         canvas->drawRect(r, paint);
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         drawGrad(canvas);
 *
 *         const SkColor fg[] = {
 *             0xFFFFFFFF,
 *             0xFFFFFF00, 0xFFFF00FF, 0xFF00FFFF,
 *             0xFFFF0000, 0xFF00FF00, 0xFF0000FF,
 *             0xFF000000,
 *         };
 *
 *         const char* text = "Hamburgefons";
 *
 *         SkPaint paint;
 *         SkFont font = ToolUtils::DefaultPortableFont();
 *         font.setSize(16);
 *         font.setEdging(SkFont::Edging::kSubpixelAntiAlias);
 *
 *         SkScalar x = SkIntToScalar(10);
 *         for (size_t i = 0; i < std::size(fg); ++i) {
 *             paint.setColor(fg[i]);
 *
 *             SkScalar y = SkIntToScalar(40);
 *             SkScalar stopy = SkIntToScalar(HEIGHT);
 *             while (y < stopy) {
 *                 canvas->drawString(text, x, y, font, paint);
 *                 y += font.getSize() * 2;
 *             }
 *             x += SkIntToScalar(1024) / std::size(fg);
 *         }
 *     }
 *
 * private:
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class GammaTextGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("gammatext"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(1024, HEIGHT); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         drawGrad(canvas);
   *
   *         const SkColor fg[] = {
   *             0xFFFFFFFF,
   *             0xFFFFFF00, 0xFFFF00FF, 0xFF00FFFF,
   *             0xFFFF0000, 0xFF00FF00, 0xFF0000FF,
   *             0xFF000000,
   *         };
   *
   *         const char* text = "Hamburgefons";
   *
   *         SkPaint paint;
   *         SkFont font = ToolUtils::DefaultPortableFont();
   *         font.setSize(16);
   *         font.setEdging(SkFont::Edging::kSubpixelAntiAlias);
   *
   *         SkScalar x = SkIntToScalar(10);
   *         for (size_t i = 0; i < std::size(fg); ++i) {
   *             paint.setColor(fg[i]);
   *
   *             SkScalar y = SkIntToScalar(40);
   *             SkScalar stopy = SkIntToScalar(HEIGHT);
   *             while (y < stopy) {
   *                 canvas->drawString(text, x, y, font, paint);
   *                 y += font.getSize() * 2;
   *             }
   *             x += SkIntToScalar(1024) / std::size(fg);
   *         }
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
     * static void drawGrad(SkCanvas* canvas) {
     *         const SkPoint pts[] = { { 0, 0 }, { 0, SkIntToScalar(HEIGHT) } };
     *
     *         canvas->clear(SK_ColorRED);
     *         SkPaint paint;
     *         paint.setShader(make_heatGradient(pts));
     *         SkRect r = { 0, 0, SkIntToScalar(1024), SkIntToScalar(HEIGHT) };
     *         canvas->drawRect(r, paint);
     *     }
     * ```
     */
    protected fun drawGrad(canvas: SkCanvas?) {
      TODO("Implement drawGrad")
    }
  }
}
