package org.skia.tests

import kotlin.Boolean
import kotlin.Double
import kotlin.Float
import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.effects.SkRuntimeEffect
import org.skia.foundation.SkImage
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class ArithmodeBlenderGM : public skiagm::GM {
 *     float                  fK1, fK2, fK3, fK4;
 *     sk_sp<SkImage>         fSrc, fDst, fChecker;
 *     sk_sp<SkShader>        fSrcShader, fDstShader;
 *     sk_sp<SkRuntimeEffect> fRuntimeEffect;
 *
 *     SkString getName() const override { return SkString("arithmode_blender"); }
 *
 *     static constexpr int W = 200;
 *     static constexpr int H = 200;
 *
 *     SkISize getISize() override { return {(W + 30) * 2, (H + 30) * 4}; }
 *
 *     void onOnceBeforeDraw() override {
 *         // Prepare a runtime effect for this blend.
 *         static constexpr char kShader[] = R"(
 *             uniform shader srcImage;
 *             uniform shader dstImage;
 *             uniform blender arithBlend;
 *             half4 main(float2 xy) {
 *                 return arithBlend.eval(srcImage.eval(xy), dstImage.eval(xy));
 *             }
 *         )";
 *         auto [effect, error] = SkRuntimeEffect::MakeForShader(SkString(kShader));
 *         SkASSERT(effect);
 *         fRuntimeEffect = effect;
 *
 *         // Start with interesting K-values, in case we're drawn without calling onAnimate().
 *         fK1 = -0.25f;
 *         fK2 =  0.25f;
 *         fK3 =  0.25f;
 *         fK4 =  0;
 *
 *         fSrc = make_src(W, H);
 *         fDst = make_dst(W, H);
 *         fSrcShader = fSrc->makeShader(SkTileMode::kClamp, SkTileMode::kClamp, SkSamplingOptions());
 *         fDstShader = fDst->makeShader(SkTileMode::kClamp, SkTileMode::kClamp, SkSamplingOptions());
 *
 *         fChecker = ToolUtils::create_checkerboard_image(W, H, 0xFFBBBBBB, 0xFFEEEEEE, 8);
 *     }
 *
 *     bool onAnimate(double nanos) override {
 *         double theta = nanos * 1e-6 * 0.001;
 *         fK1 = sin(theta + 0) * 0.25;
 *         fK2 = cos(theta + 1) * 0.25;
 *         fK3 = sin(theta + 2) * 0.25;
 *         fK4 = 0.5;
 *         return true;
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         const SkRect rect = SkRect::MakeWH(W, H);
 *
 *         canvas->drawImage(fSrc, 10, 10);
 *         canvas->drawImage(fDst, 10, 10 + H + 10);
 *
 *         SkSamplingOptions sampling;
 *         sk_sp<SkBlender> blender = SkBlenders::Arithmetic(fK1, fK2, fK3, fK4,
 *                                                           /*enforcePremul=*/true);
 *         canvas->translate(10 + W + 10, 10);
 *
 *         // All three images drawn below should appear identical.
 *         // Draw via blend step
 *         SkPaint blenderPaint;
 *         canvas->drawImage(fChecker, 0, 0);
 *         canvas->saveLayer(&rect, nullptr);
 *         canvas->drawImage(fDst, 0, 0);
 *         blenderPaint.setBlender(blender);
 *         canvas->drawImage(fSrc, 0, 0, sampling, &blenderPaint);
 *         canvas->restore();
 *
 *         canvas->translate(0, 10 + H);
 *
 *         // Draw via SkImageFilters::Blend (should appear the same as above)
 *         SkPaint imageFilterPaint;
 *         canvas->drawImage(fChecker, 0, 0);
 *         imageFilterPaint.setImageFilter(
 *                 SkImageFilters::Blend(blender,
 *                                       /*background=*/nullptr,
 *                                       /*foreground=*/SkImageFilters::Image(fSrc, sampling)));
 *         canvas->drawImage(fDst, 0, 0, sampling, &imageFilterPaint);
 *
 *         canvas->translate(0, 10 + H);
 *
 *         // Draw via SkShaders::Blend (should still appear the same as above)
 *         SkPaint shaderBlendPaint;
 *         canvas->drawImage(fChecker, 0, 0);
 *         shaderBlendPaint.setShader(SkShaders::Blend(blender, fDstShader, fSrcShader));
 *         canvas->drawRect(rect, shaderBlendPaint);
 *
 *         canvas->translate(0, 10 + H);
 *
 *         // Draw via runtime effect (should still appear the same as above)
 *         SkPaint runtimePaint;
 *         canvas->drawImage(fChecker, 0, 0);
 *         SkRuntimeEffect::ChildPtr children[] = {fSrcShader, fDstShader, blender};
 *         runtimePaint.setShader(fRuntimeEffect->makeShader(/*uniforms=*/{}, children));
 *         canvas->drawRect(rect, runtimePaint);
 *     }
 *
 * private:
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class ArithmodeBlenderGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * float                  fK1
   * ```
   */
  private var fK1: Float = TODO("Initialize fK1")

  /**
   * C++ original:
   * ```cpp
   * float                  fK1, fK2
   * ```
   */
  private var fK2: Float = TODO("Initialize fK2")

  /**
   * C++ original:
   * ```cpp
   * float                  fK1, fK2, fK3
   * ```
   */
  private var fK3: Float = TODO("Initialize fK3")

  /**
   * C++ original:
   * ```cpp
   * float                  fK1, fK2, fK3, fK4
   * ```
   */
  private var fK4: Float = TODO("Initialize fK4")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage>         fSrc
   * ```
   */
  private var fSrc: SkSp<SkImage> = TODO("Initialize fSrc")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage>         fSrc, fDst
   * ```
   */
  private var fDst: SkSp<SkImage> = TODO("Initialize fDst")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage>         fSrc, fDst, fChecker
   * ```
   */
  private var fChecker: SkSp<SkImage> = TODO("Initialize fChecker")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader>        fSrcShader
   * ```
   */
  private var fSrcShader: SkSp<SkShader> = TODO("Initialize fSrcShader")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader>        fSrcShader, fDstShader
   * ```
   */
  private var fDstShader: SkSp<SkShader> = TODO("Initialize fDstShader")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkRuntimeEffect> fRuntimeEffect
   * ```
   */
  private var fRuntimeEffect: SkSp<SkRuntimeEffect> = TODO("Initialize fRuntimeEffect")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("arithmode_blender"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {(W + 30) * 2, (H + 30) * 4}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         // Prepare a runtime effect for this blend.
   *         static constexpr char kShader[] = R"(
   *             uniform shader srcImage;
   *             uniform shader dstImage;
   *             uniform blender arithBlend;
   *             half4 main(float2 xy) {
   *                 return arithBlend.eval(srcImage.eval(xy), dstImage.eval(xy));
   *             }
   *         )";
   *         auto [effect, error] = SkRuntimeEffect::MakeForShader(SkString(kShader));
   *         SkASSERT(effect);
   *         fRuntimeEffect = effect;
   *
   *         // Start with interesting K-values, in case we're drawn without calling onAnimate().
   *         fK1 = -0.25f;
   *         fK2 =  0.25f;
   *         fK3 =  0.25f;
   *         fK4 =  0;
   *
   *         fSrc = make_src(W, H);
   *         fDst = make_dst(W, H);
   *         fSrcShader = fSrc->makeShader(SkTileMode::kClamp, SkTileMode::kClamp, SkSamplingOptions());
   *         fDstShader = fDst->makeShader(SkTileMode::kClamp, SkTileMode::kClamp, SkSamplingOptions());
   *
   *         fChecker = ToolUtils::create_checkerboard_image(W, H, 0xFFBBBBBB, 0xFFEEEEEE, 8);
   *     }
   * ```
   */
  public override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onAnimate(double nanos) override {
   *         double theta = nanos * 1e-6 * 0.001;
   *         fK1 = sin(theta + 0) * 0.25;
   *         fK2 = cos(theta + 1) * 0.25;
   *         fK3 = sin(theta + 2) * 0.25;
   *         fK4 = 0.5;
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
   *         const SkRect rect = SkRect::MakeWH(W, H);
   *
   *         canvas->drawImage(fSrc, 10, 10);
   *         canvas->drawImage(fDst, 10, 10 + H + 10);
   *
   *         SkSamplingOptions sampling;
   *         sk_sp<SkBlender> blender = SkBlenders::Arithmetic(fK1, fK2, fK3, fK4,
   *                                                           /*enforcePremul=*/true);
   *         canvas->translate(10 + W + 10, 10);
   *
   *         // All three images drawn below should appear identical.
   *         // Draw via blend step
   *         SkPaint blenderPaint;
   *         canvas->drawImage(fChecker, 0, 0);
   *         canvas->saveLayer(&rect, nullptr);
   *         canvas->drawImage(fDst, 0, 0);
   *         blenderPaint.setBlender(blender);
   *         canvas->drawImage(fSrc, 0, 0, sampling, &blenderPaint);
   *         canvas->restore();
   *
   *         canvas->translate(0, 10 + H);
   *
   *         // Draw via SkImageFilters::Blend (should appear the same as above)
   *         SkPaint imageFilterPaint;
   *         canvas->drawImage(fChecker, 0, 0);
   *         imageFilterPaint.setImageFilter(
   *                 SkImageFilters::Blend(blender,
   *                                       /*background=*/nullptr,
   *                                       /*foreground=*/SkImageFilters::Image(fSrc, sampling)));
   *         canvas->drawImage(fDst, 0, 0, sampling, &imageFilterPaint);
   *
   *         canvas->translate(0, 10 + H);
   *
   *         // Draw via SkShaders::Blend (should still appear the same as above)
   *         SkPaint shaderBlendPaint;
   *         canvas->drawImage(fChecker, 0, 0);
   *         shaderBlendPaint.setShader(SkShaders::Blend(blender, fDstShader, fSrcShader));
   *         canvas->drawRect(rect, shaderBlendPaint);
   *
   *         canvas->translate(0, 10 + H);
   *
   *         // Draw via runtime effect (should still appear the same as above)
   *         SkPaint runtimePaint;
   *         canvas->drawImage(fChecker, 0, 0);
   *         SkRuntimeEffect::ChildPtr children[] = {fSrcShader, fDstShader, blender};
   *         runtimePaint.setShader(fRuntimeEffect->makeShader(/*uniforms=*/{}, children));
   *         canvas->drawRect(rect, runtimePaint);
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public companion object {
    private val w: Int = TODO("Initialize w")

    private val h: Int = TODO("Initialize h")
  }
}
