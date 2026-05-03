package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class ComposeShaderGM : public skiagm::GM {
 * protected:
 *     void onOnceBeforeDraw() override {
 *         fShader = make_shader(SkBlendMode::kDstIn);
 *     }
 *
 *     SkString getName() const override { return SkString("composeshader"); }
 *
 *     SkISize getISize() override { return SkISize::Make(120, 120); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPaint paint;
 *         paint.setColor(SK_ColorGREEN);
 *         canvas->drawRect(SkRect::MakeWH(100, 100), paint);
 *         paint.setShader(fShader);
 *         canvas->drawRect(SkRect::MakeWH(100, 100), paint);
 *     }
 *
 * protected:
 *     sk_sp<SkShader> fShader;
 *
 * private:
 *     typedef GM INHERITED ;
 * }
 * ```
 */
public open class ComposeShaderGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> fShader
   * ```
   */
  protected var fShader: SkSp<SkShader> = TODO("Initialize fShader")

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         fShader = make_shader(SkBlendMode::kDstIn);
   *     }
   * ```
   */
  protected override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("composeshader"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(120, 120); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkPaint paint;
   *         paint.setColor(SK_ColorGREEN);
   *         canvas->drawRect(SkRect::MakeWH(100, 100), paint);
   *         paint.setShader(fShader);
   *         canvas->drawRect(SkRect::MakeWH(100, 100), paint);
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
