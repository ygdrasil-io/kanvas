package org.skia.tests

import SkColor4f
import kotlin.Boolean
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class AlphaGradientsGM : public skiagm::GM {
 * public:
 *     AlphaGradientsGM() {}
 *
 * protected:
 *     SkString getName() const override { return SkString("alphagradients"); }
 *
 *     SkISize getISize() override { return SkISize::Make(640, 480); }
 *
 *     static void draw_grad(SkCanvas* canvas, const SkRect& r,
 *                           SkColor4f c0, SkColor4f c1, bool doPreMul) {
 *         SkColor4f colors[] = { c0, c1 };
 *         SkPoint pts[] = { { r.fLeft, r.fTop }, { r.fRight, r.fBottom } };
 *         SkPaint paint;
 *         auto pm = doPreMul ? SkGradient::Interpolation::InPremul::kYes
 *                            : SkGradient::Interpolation::InPremul::kNo;
 *         paint.setShader(SkShaders::LinearGradient(pts, {{colors, {}, SkTileMode::kClamp}, {pm}}));
 *         canvas->drawRect(r, paint);
 *
 *         paint.setShader(nullptr);
 *         paint.setStyle(SkPaint::kStroke_Style);
 *         canvas->drawRect(r, paint);
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         constexpr struct {
 *             SkColor4f fColor0;
 *             SkColor4f fColor1;
 *         } gRec[] = {
 *             { SkColors::kWhite, {0, 0, 0, 0} },
 *             { SkColors::kWhite, {1, 0, 0, 0} },
 *             { SkColors::kWhite, {1, 1, 0, 0} },
 *             { SkColors::kWhite, {1, 1, 1, 0} },
 *
 *             { SkColors::kRed, {0, 0, 0, 0} },
 *             { SkColors::kRed, {1, 0, 0, 0} },
 *             { SkColors::kRed, {1, 1, 0, 0} },
 *             { SkColors::kRed, {1, 1, 1, 0} },
 *
 *             { SkColors::kBlue, {0, 0, 0, 0} },
 *             { SkColors::kBlue, {1, 0, 0, 0} },
 *             { SkColors::kBlue, {1, 1, 0, 0} },
 *             { SkColors::kBlue, {1, 1, 1, 0} },
 *         };
 *
 *         SkRect r = SkRect::MakeWH(300, 30);
 *
 *         canvas->translate(10, 10);
 *
 *         for (int doPreMul = 0; doPreMul <= 1; ++doPreMul) {
 *             canvas->save();
 *             for (size_t i = 0; i < std::size(gRec); ++i) {
 *                 draw_grad(canvas, r, gRec[i].fColor0, gRec[i].fColor1, SkToBool(doPreMul));
 *                 canvas->translate(0, r.height() + 8);
 *             }
 *             canvas->restore();
 *             canvas->translate(r.width() + 10, 0);
 *         }
 *     }
 *
 * private:
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class AlphaGradientsGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("alphagradients"); }
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
   *         constexpr struct {
   *             SkColor4f fColor0;
   *             SkColor4f fColor1;
   *         } gRec[] = {
   *             { SkColors::kWhite, {0, 0, 0, 0} },
   *             { SkColors::kWhite, {1, 0, 0, 0} },
   *             { SkColors::kWhite, {1, 1, 0, 0} },
   *             { SkColors::kWhite, {1, 1, 1, 0} },
   *
   *             { SkColors::kRed, {0, 0, 0, 0} },
   *             { SkColors::kRed, {1, 0, 0, 0} },
   *             { SkColors::kRed, {1, 1, 0, 0} },
   *             { SkColors::kRed, {1, 1, 1, 0} },
   *
   *             { SkColors::kBlue, {0, 0, 0, 0} },
   *             { SkColors::kBlue, {1, 0, 0, 0} },
   *             { SkColors::kBlue, {1, 1, 0, 0} },
   *             { SkColors::kBlue, {1, 1, 1, 0} },
   *         };
   *
   *         SkRect r = SkRect::MakeWH(300, 30);
   *
   *         canvas->translate(10, 10);
   *
   *         for (int doPreMul = 0; doPreMul <= 1; ++doPreMul) {
   *             canvas->save();
   *             for (size_t i = 0; i < std::size(gRec); ++i) {
   *                 draw_grad(canvas, r, gRec[i].fColor0, gRec[i].fColor1, SkToBool(doPreMul));
   *                 canvas->translate(0, r.height() + 8);
   *             }
   *             canvas->restore();
   *             canvas->translate(r.width() + 10, 0);
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
     * static void draw_grad(SkCanvas* canvas, const SkRect& r,
     *                           SkColor4f c0, SkColor4f c1, bool doPreMul) {
     *         SkColor4f colors[] = { c0, c1 };
     *         SkPoint pts[] = { { r.fLeft, r.fTop }, { r.fRight, r.fBottom } };
     *         SkPaint paint;
     *         auto pm = doPreMul ? SkGradient::Interpolation::InPremul::kYes
     *                            : SkGradient::Interpolation::InPremul::kNo;
     *         paint.setShader(SkShaders::LinearGradient(pts, {{colors, {}, SkTileMode::kClamp}, {pm}}));
     *         canvas->drawRect(r, paint);
     *
     *         paint.setShader(nullptr);
     *         paint.setStyle(SkPaint::kStroke_Style);
     *         canvas->drawRect(r, paint);
     *     }
     * ```
     */
    protected fun drawGrad(
      canvas: SkCanvas?,
      r: SkRect,
      c0: SkColor4f,
      c1: SkColor4f,
      doPreMul: Boolean,
    ) {
      TODO("Implement drawGrad")
    }
  }
}
