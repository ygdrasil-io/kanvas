package org.skia.modules

import kotlin.Int
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class ModeColorFilter final : public ColorFilter {
 * public:
 *     ~ModeColorFilter() override;
 *
 *     static sk_sp<ModeColorFilter> Make(sk_sp<RenderNode> child,
 *                                        sk_sp<Color> color,
 *                                        SkBlendMode mode);
 *
 * protected:
 *     sk_sp<SkColorFilter> onRevalidateFilter() override;
 *
 * private:
 *     ModeColorFilter(sk_sp<RenderNode>, sk_sp<Color>, SkBlendMode);
 *
 *     const sk_sp<Color> fColor;
 *     const SkBlendMode  fMode;
 *
 *     using INHERITED = ColorFilter;
 * }
 * ```
 */
public class ModeColorFilter public constructor(
  child: SkSp<RenderNode>,
  color: SkSp<Color>,
  mode: SkBlendMode,
) : ColorFilter(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * const sk_sp<Color> fColor
   * ```
   */
  private val fColor: Int = TODO("Initialize fColor")

  /**
   * C++ original:
   * ```cpp
   * const SkBlendMode  fMode
   * ```
   */
  private val fMode: SkBlendMode = TODO("Initialize fMode")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorFilter> ModeColorFilter::onRevalidateFilter() {
   *     fColor->revalidate(nullptr, SkMatrix::I());
   *     return SkColorFilters::Blend(fColor->getColor(), fMode);
   * }
   * ```
   */
  protected override fun onRevalidateFilter(): Int {
    TODO("Implement onRevalidateFilter")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<ModeColorFilter> ModeColorFilter::Make(sk_sp<RenderNode> child, sk_sp<Color> color,
     *                                              SkBlendMode mode) {
     *     return (child && color) ? sk_sp<ModeColorFilter>(new ModeColorFilter(std::move(child),
     *                                                                          std::move(color), mode))
     *                             : nullptr;
     * }
     * ```
     */
    public fun make(
      child: SkSp<RenderNode>,
      color: SkSp<Color>,
      mode: SkBlendMode,
    ): Int {
      TODO("Implement make")
    }
  }
}
