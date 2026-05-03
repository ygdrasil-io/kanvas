package org.skia.tests

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class GradientsManyColorsGM : public GM {
 *     enum {
 *         W = 800,
 *     };
 *     sk_sp<SkShader> fShader;
 *
 *     typedef void (*Proc)(ColorPos*);
 * public:
 *     GradientsManyColorsGM(bool dither) : fDither(dither) {}
 *
 * protected:
 *     SkString getName() const override {
 *         return SkString(fDither ? "gradients_many" : "gradients_many_nodither");
 *     }
 *
 *     SkISize getISize() override { return SkISize::Make(880, 400); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         const Proc procs[] = {
 *             make0, make1, make2, make3,
 *         };
 *         const SkPoint pts[] = {
 *             { 0, 0 },
 *             { SkIntToScalar(W), 0 },
 *         };
 *         const SkRect r = SkRect::MakeWH(SkIntToScalar(W), 30);
 *
 *         SkPaint paint;
 *         paint.setDither(fDither);
 *
 *         canvas->translate(40, 20);
 *
 *         for (int i = 0; i <= 8; ++i) {
 *             SkScalar x = r.width() * i / 8;
 *             canvas->drawLine(x, 0, x, 10000, paint);
 *         }
 *
 *         // expand the drawing rect so we exercise clampping in the gradients
 *         const SkRect drawR = r.makeOutset(20, 0);
 *         for (size_t i = 0; i < std::size(procs); ++i) {
 *             ColorPos rec;
 *             procs[i](&rec);
 *             paint.setShader(SkShaders::LinearGradient(pts, rec(SkTileMode::kClamp)));
 *             canvas->drawRect(drawR, paint);
 *
 *             canvas->save();
 *             canvas->translate(r.centerX(), r.height() + 4);
 *             canvas->scale(-1, 1);
 *             canvas->translate(-r.centerX(), 0);
 *             canvas->drawRect(drawR, paint);
 *             canvas->restore();
 *
 *             canvas->translate(0, r.height() + 2*r.height() + 8);
 *         }
 *     }
 *
 * private:
 *     bool fDither;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class GradientsManyColorsGM public constructor(
  dither: Boolean,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> fShader
   * ```
   */
  private var fShader: SkSp<SkShader> = TODO("Initialize fShader")

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
   *         return SkString(fDither ? "gradients_many" : "gradients_many_nodither");
   *     }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(880, 400); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         const Proc procs[] = {
   *             make0, make1, make2, make3,
   *         };
   *         const SkPoint pts[] = {
   *             { 0, 0 },
   *             { SkIntToScalar(W), 0 },
   *         };
   *         const SkRect r = SkRect::MakeWH(SkIntToScalar(W), 30);
   *
   *         SkPaint paint;
   *         paint.setDither(fDither);
   *
   *         canvas->translate(40, 20);
   *
   *         for (int i = 0; i <= 8; ++i) {
   *             SkScalar x = r.width() * i / 8;
   *             canvas->drawLine(x, 0, x, 10000, paint);
   *         }
   *
   *         // expand the drawing rect so we exercise clampping in the gradients
   *         const SkRect drawR = r.makeOutset(20, 0);
   *         for (size_t i = 0; i < std::size(procs); ++i) {
   *             ColorPos rec;
   *             procs[i](&rec);
   *             paint.setShader(SkShaders::LinearGradient(pts, rec(SkTileMode::kClamp)));
   *             canvas->drawRect(drawR, paint);
   *
   *             canvas->save();
   *             canvas->translate(r.centerX(), r.height() + 4);
   *             canvas->scale(-1, 1);
   *             canvas->translate(-r.centerX(), 0);
   *             canvas->drawRect(drawR, paint);
   *             canvas->restore();
   *
   *             canvas->translate(0, r.height() + 2*r.height() + 8);
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public companion object {
    public val w: Int = TODO("Initialize w")
  }
}
