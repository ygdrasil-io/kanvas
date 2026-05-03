package org.skia.tests

import kotlin.Boolean
import kotlin.CharArray
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class ShallowGradientGM : public skiagm::GM {
 * public:
 *     ShallowGradientGM(MakeShaderProc proc, const char name[], bool dither)
 *         : fProc(proc), fName(name), fDither(dither) {}
 *
 * private:
 *     MakeShaderProc fProc;
 *     const char* fName;
 *     bool fDither;
 *
 *     SkString getName() const override {
 *         return SkStringPrintf("shallow_gradient_%s%s", fName, fDither ? "" : "_nodither");
 *     }
 *
 *     SkISize getISize() override { return {800, 800}; }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         const SkColor4f colors[] = {
 *             SkColor4f::FromColor(0xFF555555), SkColor4f::FromColor(0xFF444444)
 *         };
 *         const SkGradient grad = {{colors, {}, SkTileMode::kClamp}, {}};
 *
 *         SkRect r = { 0, 0, this->width(), this->height() };
 *         SkSize size = SkSize::Make(r.width(), r.height());
 *
 *         SkPaint paint;
 *         paint.setShader(fProc(grad, size));
 *         paint.setDither(fDither);
 *         canvas->drawRect(r, paint);
 *     }
 * }
 * ```
 */
public open class ShallowGradientGM public constructor(
  proc: MakeShaderProc,
  name: CharArray,
  dither: Boolean,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * MakeShaderProc fProc
   * ```
   */
  private var fProc: MakeShaderProc = TODO("Initialize fProc")

  /**
   * C++ original:
   * ```cpp
   * const char* fName
   * ```
   */
  private val fName: String? = TODO("Initialize fName")

  /**
   * C++ original:
   * ```cpp
   * bool fDither
   * ```
   */
  private var fDither: Boolean = TODO("Initialize fDither")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override {
   *         return SkStringPrintf("shallow_gradient_%s%s", fName, fDither ? "" : "_nodither");
   *     }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {800, 800}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         const SkColor4f colors[] = {
   *             SkColor4f::FromColor(0xFF555555), SkColor4f::FromColor(0xFF444444)
   *         };
   *         const SkGradient grad = {{colors, {}, SkTileMode::kClamp}, {}};
   *
   *         SkRect r = { 0, 0, this->width(), this->height() };
   *         SkSize size = SkSize::Make(r.width(), r.height());
   *
   *         SkPaint paint;
   *         paint.setShader(fProc(grad, size));
   *         paint.setDither(fDither);
   *         canvas->drawRect(r, paint);
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
