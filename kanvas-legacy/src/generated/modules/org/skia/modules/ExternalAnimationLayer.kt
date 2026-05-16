package org.skia.modules

import kotlin.Double
import org.skia.core.SkCanvas
import org.skia.foundation.SkSp
import org.skia.math.SkSize

/**
 * C++ original:
 * ```cpp
 * class ExternalAnimationLayer final : public skottie::ExternalLayer {
 * public:
 *     ExternalAnimationLayer(sk_sp<skottie::Animation> anim, const SkSize& size)
 *         : fAnimation(std::move(anim))
 *         , fSize(size) {}
 *
 * private:
 *     void render(SkCanvas* canvas, double t) override {
 *         fAnimation->seekFrameTime(t);
 *
 *         // The main animation will layer-isolate if needed - we don't want the nested animation
 *         // to override that decision.
 *         const auto flags = skottie::Animation::RenderFlag::kSkipTopLevelIsolation;
 *         const auto dst_rect = SkRect::MakeSize(fSize);
 *         fAnimation->render(canvas, &dst_rect, flags);
 *     }
 *
 *     const sk_sp<skottie::Animation> fAnimation;
 *     const SkSize                    fSize;
 * }
 * ```
 */
public class ExternalAnimationLayer public constructor(
  anim: SkSp<Animation>,
  size: SkSize,
) : ExternalLayer() {
  /**
   * C++ original:
   * ```cpp
   * const sk_sp<skottie::Animation> fAnimation
   * ```
   */
  private val fAnimation: SkSp<Animation> = TODO("Initialize fAnimation")

  /**
   * C++ original:
   * ```cpp
   * const SkSize                    fSize
   * ```
   */
  private val fSize: SkSize = TODO("Initialize fSize")

  /**
   * C++ original:
   * ```cpp
   * void render(SkCanvas* canvas, double t) override {
   *         fAnimation->seekFrameTime(t);
   *
   *         // The main animation will layer-isolate if needed - we don't want the nested animation
   *         // to override that decision.
   *         const auto flags = skottie::Animation::RenderFlag::kSkipTopLevelIsolation;
   *         const auto dst_rect = SkRect::MakeSize(fSize);
   *         fAnimation->render(canvas, &dst_rect, flags);
   *     }
   * ```
   */
  public override fun render(canvas: SkCanvas?, t: Double) {
    TODO("Implement render")
  }
}
