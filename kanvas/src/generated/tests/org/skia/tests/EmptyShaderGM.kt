package org.skia.tests

import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class EmptyShaderGM : public GM {
 * public:
 *     EmptyShaderGM() {
 *         this->setBGColor(0xFFCCCCCC);
 *     }
 *
 * protected:
 *     SkString getName() const override { return SkString("emptyshader"); }
 *
 *     SkISize getISize() override { return SkISize::Make(128, 88); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPaint stroke;
 *         stroke.setStyle(SkPaint::kStroke_Style);
 *
 *         int left = kPad, top = kPad;
 *         for (auto f : { empty, degen_sweep, degen_linear, degen_radial, degen_conical }) {
 *             SkRect r = SkRect::MakeXYWH(left, top, kSize, kSize);
 *
 *             SkPaint p;
 *             p.setColor(SK_ColorBLUE);
 *             p.setShader(f(r));
 *
 *             canvas->drawRect(r, p);
 *             canvas->drawRect(r, stroke);
 *
 *             left += kSize + kPad;
 *             if (left >= this->getISize().width()) {
 *                 left = kPad;
 *                 top += kSize + kPad;
 *             }
 *         }
 *     }
 *
 * private:
 *     static constexpr int kPad = 8;
 *     static constexpr int kSize = 32;
 * }
 * ```
 */
public open class EmptyShaderGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("emptyshader"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(128, 88); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkPaint stroke;
   *         stroke.setStyle(SkPaint::kStroke_Style);
   *
   *         int left = kPad, top = kPad;
   *         for (auto f : { empty, degen_sweep, degen_linear, degen_radial, degen_conical }) {
   *             SkRect r = SkRect::MakeXYWH(left, top, kSize, kSize);
   *
   *             SkPaint p;
   *             p.setColor(SK_ColorBLUE);
   *             p.setShader(f(r));
   *
   *             canvas->drawRect(r, p);
   *             canvas->drawRect(r, stroke);
   *
   *             left += kSize + kPad;
   *             if (left >= this->getISize().width()) {
   *                 left = kPad;
   *                 top += kSize + kPad;
   *             }
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public companion object {
    private val kPad: Int = TODO("Initialize kPad")

    private val kSize: Int = TODO("Initialize kSize")
  }
}
