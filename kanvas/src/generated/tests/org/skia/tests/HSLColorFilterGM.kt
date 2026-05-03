package org.skia.tests

import kotlin.Float
import kotlin.Int
import kotlin.String
import kotlin.ULong
import org.skia.core.SkCanvas
import org.skia.foundation.SkColorFilter
import org.skia.foundation.SkSp
import org.skia.math.SkISize
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class HSLColorFilterGM : public skiagm::GM {
 * protected:
 *     SkString getName() const override { return SkString("hslcolorfilter"); }
 *
 *     SkISize getISize() override { return {840, 1100}; }
 *
 *     void onOnceBeforeDraw() override {
 *         sk_sp<SkImage> mandrill = ToolUtils::GetResourceAsImage("images/mandrill_256.png");
 *         const auto lm = SkMatrix::RectToRectOrIdentity(SkRect::MakeWH(mandrill->width(),
 *                                                                       mandrill->height()),
 *                                                        SkRect::MakeWH(kWheelSize, kWheelSize));
 *         fShaders.push_back(mandrill->makeShader(SkSamplingOptions(), &lm));
 *
 *         static constexpr SkColor gGrads[][4] = {
 *             { 0xffff0000, 0xff00ff00, 0xff0000ff, 0xffff0000 },
 *             { 0xdfc08040, 0xdf8040c0, 0xdf40c080, 0xdfc08040 },
 *         };
 *
 *         for (const auto& cols : gGrads) {
 *             SkColorConverter conv({cols, 4});
 *             fShaders.push_back(SkShaders::SweepGradient({kWheelSize / 2, kWheelSize / 2}, -90, 270,
 *                                                 {{conv.colors4f(), {}, SkTileMode::kRepeat}, {}}));
 *         }
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         using std::make_tuple;
 *
 *         static constexpr struct {
 *             std::tuple<float, float> h, s, l;
 *         } gTests[] = {
 *             { make_tuple(-0.5f, 0.5f), make_tuple( 0.0f, 0.0f), make_tuple( 0.0f, 0.0f) },
 *             { make_tuple( 0.0f, 0.0f), make_tuple(-1.0f, 1.0f), make_tuple( 0.0f, 0.0f) },
 *             { make_tuple( 0.0f, 0.0f), make_tuple( 0.0f, 0.0f), make_tuple(-1.0f, 1.0f) },
 *         };
 *
 *         const auto rect = SkRect::MakeWH(kWheelSize, kWheelSize);
 *
 *         canvas->drawColor(0xffcccccc);
 *         SkPaint paint;
 *
 *         for (const auto& shader : fShaders) {
 *             paint.setShader(shader);
 *
 *             for (const auto& tst: gTests) {
 *                 canvas->translate(0, kWheelSize * 0.1f);
 *
 *                 const auto dh = (std::get<1>(tst.h) - std::get<0>(tst.h)) / (kSteps - 1),
 *                            ds = (std::get<1>(tst.s) - std::get<0>(tst.s)) / (kSteps - 1),
 *                            dl = (std::get<1>(tst.l) - std::get<0>(tst.l)) / (kSteps - 1);
 *                 auto h = std::get<0>(tst.h),
 *                      s = std::get<0>(tst.s),
 *                      l = std::get<0>(tst.l);
 *                 {
 *                     SkAutoCanvasRestore acr(canvas, true);
 *                     for (size_t i = 0; i < kSteps; ++i) {
 *                         paint.setColorFilter(make_filter(h, s, l));
 *                         canvas->translate(kWheelSize * 0.1f, 0);
 *                         canvas->drawRect(rect, paint);
 *                         canvas->translate(kWheelSize * 1.1f, 0);
 *                         h += dh;
 *                         s += ds;
 *                         l += dl;
 *                     }
 *                 }
 *                 canvas->translate(0, kWheelSize * 1.1f);
 *             }
 *             canvas->translate(0, kWheelSize * 0.1f);
 *         }
 *     }
 *
 * private:
 *     inline static constexpr SkScalar kWheelSize  = 100;
 *     inline static constexpr size_t   kSteps = 7;
 *
 *     static sk_sp<SkColorFilter> make_filter(float h, float s, float l) {
 *         // These are roughly AE semantics.
 *         const auto h_bias  = h,
 *                    h_scale = 1.0f,
 *                    s_bias  = std::max(s, 0.0f),
 *                    s_scale = 1 - std::abs(s),
 *                    l_bias  = std::max(l, 0.0f),
 *                    l_scale = 1 - std::abs(l);
 *
 *         const float cm[20] = {
 *             h_scale,       0,       0, 0, h_bias,
 *                   0, s_scale,       0, 0, s_bias,
 *                   0,       0, l_scale, 0, l_bias,
 *                   0,       0,       0, 1,      0,
 *         };
 *
 *         return SkColorFilters::HSLAMatrix(cm);
 *     }
 *
 *     std::vector<sk_sp<SkShader>> fShaders;
 * }
 * ```
 */
public open class HSLColorFilterGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr SkScalar kWheelSize  = 100
   * ```
   */
  private var fShaders: Int = TODO("Initialize fShaders")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("hslcolorfilter"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {840, 1100}; }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         sk_sp<SkImage> mandrill = ToolUtils::GetResourceAsImage("images/mandrill_256.png");
   *         const auto lm = SkMatrix::RectToRectOrIdentity(SkRect::MakeWH(mandrill->width(),
   *                                                                       mandrill->height()),
   *                                                        SkRect::MakeWH(kWheelSize, kWheelSize));
   *         fShaders.push_back(mandrill->makeShader(SkSamplingOptions(), &lm));
   *
   *         static constexpr SkColor gGrads[][4] = {
   *             { 0xffff0000, 0xff00ff00, 0xff0000ff, 0xffff0000 },
   *             { 0xdfc08040, 0xdf8040c0, 0xdf40c080, 0xdfc08040 },
   *         };
   *
   *         for (const auto& cols : gGrads) {
   *             SkColorConverter conv({cols, 4});
   *             fShaders.push_back(SkShaders::SweepGradient({kWheelSize / 2, kWheelSize / 2}, -90, 270,
   *                                                 {{conv.colors4f(), {}, SkTileMode::kRepeat}, {}}));
   *         }
   *     }
   * ```
   */
  protected override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         using std::make_tuple;
   *
   *         static constexpr struct {
   *             std::tuple<float, float> h, s, l;
   *         } gTests[] = {
   *             { make_tuple(-0.5f, 0.5f), make_tuple( 0.0f, 0.0f), make_tuple( 0.0f, 0.0f) },
   *             { make_tuple( 0.0f, 0.0f), make_tuple(-1.0f, 1.0f), make_tuple( 0.0f, 0.0f) },
   *             { make_tuple( 0.0f, 0.0f), make_tuple( 0.0f, 0.0f), make_tuple(-1.0f, 1.0f) },
   *         };
   *
   *         const auto rect = SkRect::MakeWH(kWheelSize, kWheelSize);
   *
   *         canvas->drawColor(0xffcccccc);
   *         SkPaint paint;
   *
   *         for (const auto& shader : fShaders) {
   *             paint.setShader(shader);
   *
   *             for (const auto& tst: gTests) {
   *                 canvas->translate(0, kWheelSize * 0.1f);
   *
   *                 const auto dh = (std::get<1>(tst.h) - std::get<0>(tst.h)) / (kSteps - 1),
   *                            ds = (std::get<1>(tst.s) - std::get<0>(tst.s)) / (kSteps - 1),
   *                            dl = (std::get<1>(tst.l) - std::get<0>(tst.l)) / (kSteps - 1);
   *                 auto h = std::get<0>(tst.h),
   *                      s = std::get<0>(tst.s),
   *                      l = std::get<0>(tst.l);
   *                 {
   *                     SkAutoCanvasRestore acr(canvas, true);
   *                     for (size_t i = 0; i < kSteps; ++i) {
   *                         paint.setColorFilter(make_filter(h, s, l));
   *                         canvas->translate(kWheelSize * 0.1f, 0);
   *                         canvas->drawRect(rect, paint);
   *                         canvas->translate(kWheelSize * 1.1f, 0);
   *                         h += dh;
   *                         s += ds;
   *                         l += dl;
   *                     }
   *                 }
   *                 canvas->translate(0, kWheelSize * 1.1f);
   *             }
   *             canvas->translate(0, kWheelSize * 0.1f);
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public companion object {
    private val kWheelSize: SkScalar = TODO("Initialize kWheelSize")

    private val kSteps: ULong = TODO("Initialize kSteps")

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkColorFilter> make_filter(float h, float s, float l) {
     *         // These are roughly AE semantics.
     *         const auto h_bias  = h,
     *                    h_scale = 1.0f,
     *                    s_bias  = std::max(s, 0.0f),
     *                    s_scale = 1 - std::abs(s),
     *                    l_bias  = std::max(l, 0.0f),
     *                    l_scale = 1 - std::abs(l);
     *
     *         const float cm[20] = {
     *             h_scale,       0,       0, 0, h_bias,
     *                   0, s_scale,       0, 0, s_bias,
     *                   0,       0, l_scale, 0, l_bias,
     *                   0,       0,       0, 1,      0,
     *         };
     *
     *         return SkColorFilters::HSLAMatrix(cm);
     *     }
     * ```
     */
    private fun makeFilter(
      h: Float,
      s: Float,
      l: Float,
    ): SkSp<SkColorFilter> {
      TODO("Implement makeFilter")
    }
  }
}
