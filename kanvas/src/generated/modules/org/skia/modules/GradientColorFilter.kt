package org.skia.modules

import kotlin.Float
import kotlin.Int
import kotlin.collections.List
import org.skia.foundation.SkColorFilter
import org.skia.foundation.SkSp
import undefined.Weight

/**
 * C++ original:
 * ```cpp
 * class GradientColorFilter final : public ColorFilter {
 * public:
 *     ~GradientColorFilter() override;
 *
 *     static sk_sp<GradientColorFilter> Make(sk_sp<RenderNode> child,
 *                                            sk_sp<Color> c0, sk_sp<Color> c1);
 *     static sk_sp<GradientColorFilter> Make(sk_sp<RenderNode> child,
 *                                            std::vector<sk_sp<Color>>);
 *
 *     SG_ATTRIBUTE(Weight, float, fWeight)
 *
 * protected:
 *     sk_sp<SkColorFilter> onRevalidateFilter() override;
 *
 * private:
 *     GradientColorFilter(sk_sp<RenderNode>, std::vector<sk_sp<Color>>);
 *
 *     const std::vector<sk_sp<Color>> fColors;
 *
 *     float                           fWeight = 0;
 *
 *     using INHERITED = ColorFilter;
 * }
 * ```
 */
public class GradientColorFilter public constructor(
  child: SkSp<RenderNode>,
  colors: List<SkSp<Color>>,
) : ColorFilter(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * float                           fWeight = 0
   * ```
   */
  private var fWeight: Float = TODO("Initialize fWeight")

  /**
   * C++ original:
   * ```cpp
   * SG_ATTRIBUTE(Weight, float, fWeight)
   * ```
   */
  public fun sgATTRIBUTE(param0: Weight, param1: Float): Int {
    TODO("Implement sgATTRIBUTE")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorFilter> GradientColorFilter::onRevalidateFilter() {
   *     for (const auto& color : fColors) {
   *         color->revalidate(nullptr, SkMatrix::I());
   *     }
   *
   *     if (fWeight <= 0) {
   *         return nullptr;
   *     }
   *
   *     SkASSERT(fColors.size() > 1);
   *     auto gradientCF = (fColors.size() > 2) ? MakeNColorGradient(fColors)
   *                                            : Make2ColorGradient(fColors[0], fColors[1]);
   *
   *     return SkColorFilters::Lerp(fWeight, nullptr, std::move(gradientCF));
   * }
   * ```
   */
  public override fun onRevalidateFilter(): SkSp<SkColorFilter> {
    TODO("Implement onRevalidateFilter")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<GradientColorFilter> GradientColorFilter::Make(sk_sp<RenderNode> child,
     *                                                      sk_sp<Color> c0, sk_sp<Color> c1) {
     *     return Make(std::move(child), { std::move(c0), std::move(c1) });
     * }
     * ```
     */
    public fun make(
      child: SkSp<RenderNode>,
      c0: SkSp<Color>,
      c1: SkSp<Color>,
    ): Int {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<GradientColorFilter> GradientColorFilter::Make(sk_sp<RenderNode> child,
     *                                                      std::vector<sk_sp<Color>> colors) {
     *     return (child && colors.size() > 1)
     *         ? sk_sp<GradientColorFilter>(new GradientColorFilter(std::move(child), std::move(colors)))
     *         : nullptr;
     * }
     * ```
     */
    public fun make(child: SkSp<RenderNode>, colors: List<SkSp<Color>>): Int {
      TODO("Implement make")
    }
  }
}
