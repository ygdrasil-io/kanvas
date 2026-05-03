package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class PerlinNoiseLocalMatrixGM : public skiagm::GM {
 *     static constexpr SkISize kSize = {80, 80};
 *
 *     SkString getName() const override { return SkString("perlinnoise_localmatrix"); }
 *
 *     SkISize getISize() override { return {640, 480}; }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         canvas->translate(10, 10);
 *
 *         SkPaint paint;
 *         paint.setShader(noise_shader(Type::kFractalNoise, 0.1f, 0.1f, 2, 0, false, kSize));
 *
 *         const SkScalar w = SkIntToScalar(kSize.width());
 *         const SkScalar h = SkIntToScalar(kSize.height());
 *
 *         SkRect r = SkRect::MakeWH(w, h);
 *         canvas->drawRect(r, paint);
 *
 *         canvas->save();
 *         canvas->translate(w * 5/4, 0);
 *         canvas->drawRect(r, paint);
 *         canvas->restore();
 *
 *         canvas->save();
 *         canvas->translate(0, h + 10);
 *         canvas->scale(2, 2);
 *         canvas->drawRect(r, paint);
 *         canvas->restore();
 *
 *         canvas->save();
 *         canvas->translate(w + 100, h + 10);
 *         canvas->scale(2, 2);
 *         canvas->drawRect(r, paint);
 *         canvas->restore();
 *
 *         // The next row should draw the same as the previous, even though we are using a local
 *         // matrix instead of the canvas.
 *
 *         canvas->translate(0, h * 2 + 10);
 *
 *         SkMatrix lm;
 *         lm.setScale(2, 2);
 *         paint.setShader(paint.getShader()->makeWithLocalMatrix(lm));
 *         r.fRight += r.width();
 *         r.fBottom += r.height();
 *
 *         canvas->save();
 *         canvas->translate(0, h + 10);
 *         canvas->drawRect(r, paint);
 *         canvas->restore();
 *
 *         canvas->save();
 *         canvas->translate(w + 100, h + 10);
 *         canvas->drawRect(r, paint);
 *         canvas->restore();
 *     }
 * }
 * ```
 */
public open class PerlinNoiseLocalMatrixGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("perlinnoise_localmatrix"); }
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
   *         canvas->translate(10, 10);
   *
   *         SkPaint paint;
   *         paint.setShader(noise_shader(Type::kFractalNoise, 0.1f, 0.1f, 2, 0, false, kSize));
   *
   *         const SkScalar w = SkIntToScalar(kSize.width());
   *         const SkScalar h = SkIntToScalar(kSize.height());
   *
   *         SkRect r = SkRect::MakeWH(w, h);
   *         canvas->drawRect(r, paint);
   *
   *         canvas->save();
   *         canvas->translate(w * 5/4, 0);
   *         canvas->drawRect(r, paint);
   *         canvas->restore();
   *
   *         canvas->save();
   *         canvas->translate(0, h + 10);
   *         canvas->scale(2, 2);
   *         canvas->drawRect(r, paint);
   *         canvas->restore();
   *
   *         canvas->save();
   *         canvas->translate(w + 100, h + 10);
   *         canvas->scale(2, 2);
   *         canvas->drawRect(r, paint);
   *         canvas->restore();
   *
   *         // The next row should draw the same as the previous, even though we are using a local
   *         // matrix instead of the canvas.
   *
   *         canvas->translate(0, h * 2 + 10);
   *
   *         SkMatrix lm;
   *         lm.setScale(2, 2);
   *         paint.setShader(paint.getShader()->makeWithLocalMatrix(lm));
   *         r.fRight += r.width();
   *         r.fBottom += r.height();
   *
   *         canvas->save();
   *         canvas->translate(0, h + 10);
   *         canvas->drawRect(r, paint);
   *         canvas->restore();
   *
   *         canvas->save();
   *         canvas->translate(w + 100, h + 10);
   *         canvas->drawRect(r, paint);
   *         canvas->restore();
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public companion object {
    private val kSize: SkISize = TODO("Initialize kSize")
  }
}
