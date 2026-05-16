package org.skia.modules

import kotlin.Float
import kotlin.Int
import org.skia.foundation.SkSp
import undefined.AnimatorScope
import undefined.StateChanged

/**
 * C++ original:
 * ```cpp
 * class CompTimeMapper final : public Animator {
 * public:
 *     CompTimeMapper(AnimatorScope&& layer_animators,
 *                    sk_sp<TimeRemapper> remapper,
 *                    float time_bias, float time_scale)
 *         : fAnimators(std::move(layer_animators))
 *         , fRemapper(std::move(remapper))
 *         , fTimeBias(time_bias)
 *         , fTimeScale(time_scale) {}
 *
 *     StateChanged onSeek(float t) override {
 *         if (fRemapper) {
 *             // When time remapping is active, |t| is fully driven externally.
 *             fRemapper->seek(t);
 *             t = fRemapper->t();
 *         } else {
 *             t = (t + fTimeBias) * fTimeScale;
 *         }
 *
 *         bool changed = false;
 *
 *         for (const auto& anim : fAnimators) {
 *             changed |= anim->seek(t);
 *         }
 *
 *         return changed;
 *     }
 *
 * private:
 *     const AnimatorScope       fAnimators;
 *     const sk_sp<TimeRemapper> fRemapper;
 *     const float               fTimeBias,
 *                               fTimeScale;
 * }
 * ```
 */
public class CompTimeMapper public constructor(
  layerAnimators: AnimatorScope,
  remapper: SkSp<TimeRemapper>,
  timeBias: Float,
  timeScale: Float,
) : Animator() {
  /**
   * C++ original:
   * ```cpp
   * const AnimatorScope       fAnimators
   * ```
   */
  private val fAnimators: Int = TODO("Initialize fAnimators")

  /**
   * C++ original:
   * ```cpp
   * const sk_sp<TimeRemapper> fRemapper
   * ```
   */
  private val fRemapper: SkSp<TimeRemapper> = TODO("Initialize fRemapper")

  /**
   * C++ original:
   * ```cpp
   * const float               fTimeBias
   * ```
   */
  private val fTimeBias: Float = TODO("Initialize fTimeBias")

  /**
   * C++ original:
   * ```cpp
   * const float               fTimeBias,
   *                               fTimeScale
   * ```
   */
  private val fTimeScale: Float = TODO("Initialize fTimeScale")

  /**
   * C++ original:
   * ```cpp
   * StateChanged onSeek(float t) override {
   *         if (fRemapper) {
   *             // When time remapping is active, |t| is fully driven externally.
   *             fRemapper->seek(t);
   *             t = fRemapper->t();
   *         } else {
   *             t = (t + fTimeBias) * fTimeScale;
   *         }
   *
   *         bool changed = false;
   *
   *         for (const auto& anim : fAnimators) {
   *             changed |= anim->seek(t);
   *         }
   *
   *         return changed;
   *     }
   * ```
   */
  public override fun onSeek(t: Float): StateChanged {
    TODO("Implement onSeek")
  }
}
