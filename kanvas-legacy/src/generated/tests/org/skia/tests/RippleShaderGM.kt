package org.skia.tests

import kotlin.Boolean
import kotlin.Double
import kotlin.Float
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.effects.SkRuntimeEffect
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class RippleShaderGM : public skiagm::GM {
 * public:
 *     static constexpr SkISize kSize = {512, 512};
 *
 *     void onOnceBeforeDraw() override {
 *         // Load the mandrill into a shader.
 *         sk_sp<SkImage> img = ToolUtils::GetResourceAsImage("images/mandrill_512.png");
 *         if (!img) {
 *             SkDebugf("Unable to load mandrill_512 from resources directory");
 *             return;
 *         }
 *         fMandrill = img->makeShader(SkSamplingOptions());
 *
 *         // Load RippleShader.rts into a SkRuntimeEffect.
 *         sk_sp<SkData> shaderData = GetResourceAsData("sksl/realistic/RippleShader.rts");
 *         if (!shaderData) {
 *             SkDebugf("Unable to load ripple shader from resources directory");
 *             return;
 *         }
 *         auto [effect, error] = SkRuntimeEffect::MakeForShader(
 *                 SkString(static_cast<const char*>(shaderData->data()), shaderData->size()));
 *         if (!effect) {
 *             SkDebugf("Ripple shader failed to compile\n\n%s\n", error.c_str());
 *         }
 *         fEffect = std::move(effect);
 *     }
 *
 *     SkString getName() const override { return SkString("rippleshader"); }
 *     SkISize getISize() override { return kSize; }
 *     bool onAnimate(double nanos) override {
 *         fMillis = nanos / (1000. * 1000.);
 *         return true;
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPaint base;
 *         base.setShader(fMandrill);
 *         canvas->drawRect(SkRect::MakeWH(kSize.width(), kSize.height()), base);
 *
 *         // Uniform setting logic was imperfectly adapted from:
 *         //     frameworks/base/graphics/java/android/graphics/drawable/RippleShader.java
 *         //     frameworks/base/graphics/java/android/graphics/drawable/RippleAnimationSession.java
 *
 *         SkRuntimeShaderBuilder builder(fEffect);
 *         constexpr float ANIM_DURATION = 1500.0f;
 *         constexpr float NOISE_ANIMATION_DURATION = 7000.0f;
 *         constexpr float MAX_NOISE_PHASE = NOISE_ANIMATION_DURATION / 214.0f;
 *         constexpr float PI_ROTATE_RIGHT = SK_ScalarPI * 0.0078125f;
 *         constexpr float PI_ROTATE_LEFT = SK_ScalarPI * -0.0078125f;
 *
 *         builder.uniform("in_origin")          = SkV2{kSize.width() / 2, kSize.height() / 2};
 *         builder.uniform("in_touch")           = SkV2{kSize.width() / 2, kSize.height() / 2};
 *         // Note that `in_progress` should actually be interpolated via FAST_OUT_SLOW_IN.
 *         builder.uniform("in_progress")        = this->sawtoothLerp(0.0f, 1.0f, ANIM_DURATION);
 *         builder.uniform("in_maxRadius")       = 400.0f;
 *         builder.uniform("in_resolutionScale") = SkV2{1.0f / kSize.width(), 1.0f / kSize.height()};
 *         builder.uniform("in_noiseScale")      = SkV2{2.1f / kSize.width(), 2.1f / kSize.height()};
 *         builder.uniform("in_hasMask")         = 1.0f;
 *
 *         float phase = this->sawtoothLerp(0, MAX_NOISE_PHASE, NOISE_ANIMATION_DURATION);
 *         builder.uniform("in_noisePhase")      = phase;
 *         builder.uniform("in_turbulencePhase") = phase * 1000.0f;
 *
 *         const float scale = 1.5f;
 *         builder.uniform("in_tCircle1") = SkV2{scale * .5f + (phase * 0.01f * cosf(scale * .55f)),
 *                                               scale * .5f + (phase * 0.01f * sinf(scale * .55f))};
 *         builder.uniform("in_tCircle2") = SkV2{scale * .2f + (phase * -.0066f * cosf(scale * .45f)),
 *                                               scale * .2f + (phase * -.0066f * sinf(scale * .45f))};
 *         builder.uniform("in_tCircle3") = SkV2{scale + (phase * -.0066f * cosf(scale * .35f)),
 *                                               scale + (phase * -.0066f * sinf(scale * .35f))};
 *
 *         float rotation1 = phase * PI_ROTATE_RIGHT + 1.7f * SK_ScalarPI;
 *         builder.uniform("in_tRotation1") = SkV2{cosf(rotation1), sinf(rotation1)};
 *
 *         float rotation2 = phase * PI_ROTATE_LEFT + 2.0f * SK_ScalarPI;
 *         builder.uniform("in_tRotation2") = SkV2{cosf(rotation2), sinf(rotation2)};
 *
 *         float rotation3 = phase * PI_ROTATE_RIGHT + 2.75f * SK_ScalarPI;
 *         builder.uniform("in_tRotation3") = SkV2{cosf(rotation3), sinf(rotation3)};
 *
 *         builder.uniform("in_color") = SkV4{0.0f, 0.6f, 0.0f, 1.0f};         // green
 *         builder.uniform("in_sparkleColor") = SkV4{1.0f, 1.0f, 1.0f, 1.0f};  // white
 *         builder.child("in_shader") = fMandrill;
 *
 *         SkPaint sparkle;
 *         sparkle.setShader(builder.makeShader());
 *         canvas->drawRect(SkRect::MakeWH(kSize.width(), kSize.height()), sparkle);
 *     }
 *
 *     float sawtoothLerp(float a, float b, float windowMs) {
 *         float t = std::fmod(fMillis, windowMs) / windowMs;
 *         return a * (1. - t) + b * t;
 *     }
 *
 * protected:
 *     sk_sp<SkRuntimeEffect> fEffect;
 *     sk_sp<SkShader> fMandrill;
 *     float fMillis = 500.0f;  // this allows a non-animated single-frame capture to show the effect
 *
 * }
 * ```
 */
public open class RippleShaderGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * static constexpr SkISize kSize = {512, 512}
   * ```
   */
  protected var fEffect: SkSp<SkRuntimeEffect> = TODO("Initialize fEffect")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkRuntimeEffect> fEffect
   * ```
   */
  protected var fMandrill: SkSp<SkShader> = TODO("Initialize fMandrill")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> fMandrill
   * ```
   */
  protected var fMillis: Float = TODO("Initialize fMillis")

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         // Load the mandrill into a shader.
   *         sk_sp<SkImage> img = ToolUtils::GetResourceAsImage("images/mandrill_512.png");
   *         if (!img) {
   *             SkDebugf("Unable to load mandrill_512 from resources directory");
   *             return;
   *         }
   *         fMandrill = img->makeShader(SkSamplingOptions());
   *
   *         // Load RippleShader.rts into a SkRuntimeEffect.
   *         sk_sp<SkData> shaderData = GetResourceAsData("sksl/realistic/RippleShader.rts");
   *         if (!shaderData) {
   *             SkDebugf("Unable to load ripple shader from resources directory");
   *             return;
   *         }
   *         auto [effect, error] = SkRuntimeEffect::MakeForShader(
   *                 SkString(static_cast<const char*>(shaderData->data()), shaderData->size()));
   *         if (!effect) {
   *             SkDebugf("Ripple shader failed to compile\n\n%s\n", error.c_str());
   *         }
   *         fEffect = std::move(effect);
   *     }
   * ```
   */
  public override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("rippleshader"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return kSize; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onAnimate(double nanos) override {
   *         fMillis = nanos / (1000. * 1000.);
   *         return true;
   *     }
   * ```
   */
  public override fun onAnimate(nanos: Double): Boolean {
    TODO("Implement onAnimate")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkPaint base;
   *         base.setShader(fMandrill);
   *         canvas->drawRect(SkRect::MakeWH(kSize.width(), kSize.height()), base);
   *
   *         // Uniform setting logic was imperfectly adapted from:
   *         //     frameworks/base/graphics/java/android/graphics/drawable/RippleShader.java
   *         //     frameworks/base/graphics/java/android/graphics/drawable/RippleAnimationSession.java
   *
   *         SkRuntimeShaderBuilder builder(fEffect);
   *         constexpr float ANIM_DURATION = 1500.0f;
   *         constexpr float NOISE_ANIMATION_DURATION = 7000.0f;
   *         constexpr float MAX_NOISE_PHASE = NOISE_ANIMATION_DURATION / 214.0f;
   *         constexpr float PI_ROTATE_RIGHT = SK_ScalarPI * 0.0078125f;
   *         constexpr float PI_ROTATE_LEFT = SK_ScalarPI * -0.0078125f;
   *
   *         builder.uniform("in_origin")          = SkV2{kSize.width() / 2, kSize.height() / 2};
   *         builder.uniform("in_touch")           = SkV2{kSize.width() / 2, kSize.height() / 2};
   *         // Note that `in_progress` should actually be interpolated via FAST_OUT_SLOW_IN.
   *         builder.uniform("in_progress")        = this->sawtoothLerp(0.0f, 1.0f, ANIM_DURATION);
   *         builder.uniform("in_maxRadius")       = 400.0f;
   *         builder.uniform("in_resolutionScale") = SkV2{1.0f / kSize.width(), 1.0f / kSize.height()};
   *         builder.uniform("in_noiseScale")      = SkV2{2.1f / kSize.width(), 2.1f / kSize.height()};
   *         builder.uniform("in_hasMask")         = 1.0f;
   *
   *         float phase = this->sawtoothLerp(0, MAX_NOISE_PHASE, NOISE_ANIMATION_DURATION);
   *         builder.uniform("in_noisePhase")      = phase;
   *         builder.uniform("in_turbulencePhase") = phase * 1000.0f;
   *
   *         const float scale = 1.5f;
   *         builder.uniform("in_tCircle1") = SkV2{scale * .5f + (phase * 0.01f * cosf(scale * .55f)),
   *                                               scale * .5f + (phase * 0.01f * sinf(scale * .55f))};
   *         builder.uniform("in_tCircle2") = SkV2{scale * .2f + (phase * -.0066f * cosf(scale * .45f)),
   *                                               scale * .2f + (phase * -.0066f * sinf(scale * .45f))};
   *         builder.uniform("in_tCircle3") = SkV2{scale + (phase * -.0066f * cosf(scale * .35f)),
   *                                               scale + (phase * -.0066f * sinf(scale * .35f))};
   *
   *         float rotation1 = phase * PI_ROTATE_RIGHT + 1.7f * SK_ScalarPI;
   *         builder.uniform("in_tRotation1") = SkV2{cosf(rotation1), sinf(rotation1)};
   *
   *         float rotation2 = phase * PI_ROTATE_LEFT + 2.0f * SK_ScalarPI;
   *         builder.uniform("in_tRotation2") = SkV2{cosf(rotation2), sinf(rotation2)};
   *
   *         float rotation3 = phase * PI_ROTATE_RIGHT + 2.75f * SK_ScalarPI;
   *         builder.uniform("in_tRotation3") = SkV2{cosf(rotation3), sinf(rotation3)};
   *
   *         builder.uniform("in_color") = SkV4{0.0f, 0.6f, 0.0f, 1.0f};         // green
   *         builder.uniform("in_sparkleColor") = SkV4{1.0f, 1.0f, 1.0f, 1.0f};  // white
   *         builder.child("in_shader") = fMandrill;
   *
   *         SkPaint sparkle;
   *         sparkle.setShader(builder.makeShader());
   *         canvas->drawRect(SkRect::MakeWH(kSize.width(), kSize.height()), sparkle);
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * float sawtoothLerp(float a, float b, float windowMs) {
   *         float t = std::fmod(fMillis, windowMs) / windowMs;
   *         return a * (1. - t) + b * t;
   *     }
   * ```
   */
  public fun sawtoothLerp(
    a: Float,
    b: Float,
    windowMs: Float,
  ): Float {
    TODO("Implement sawtoothLerp")
  }

  public companion object {
    public val kSize: SkISize = TODO("Initialize kSize")
  }
}
