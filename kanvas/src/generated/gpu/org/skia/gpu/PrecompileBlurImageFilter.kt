package org.skia.gpu

import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan

/**
 * C++ original:
 * ```cpp
 * class PrecompileBlurImageFilter : public PrecompileImageFilter {
 * public:
 *     PrecompileBlurImageFilter(SkSpan<sk_sp<PrecompileImageFilter>> inputs)
 *             : PrecompileImageFilter(std::move(inputs)) {
 *     }
 *
 * private:
 *     void onCreatePipelines(
 *             const KeyContext& keyContext,
 *             const RenderPassDesc& renderPassDesc,
 *             const PaintOptionsPriv::ProcessCombination& processCombination) const override {
 *
 *         PrecompileImageFiltersPriv::CreateBlurImageFilterPipelines(keyContext,
 *                                                                    renderPassDesc,
 *                                                                    processCombination);
 *     }
 * }
 * ```
 */
public open class PrecompileBlurImageFilter public constructor(
  inputs: SkSpan<SkSp<PrecompileImageFilter>>,
) : PrecompileImageFilter(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * void onCreatePipelines(
   *             const KeyContext& keyContext,
   *             const RenderPassDesc& renderPassDesc,
   *             const PaintOptionsPriv::ProcessCombination& processCombination) const override {
   *
   *         PrecompileImageFiltersPriv::CreateBlurImageFilterPipelines(keyContext,
   *                                                                    renderPassDesc,
   *                                                                    processCombination);
   *     }
   * ```
   */
  public override fun onCreatePipelines(
    keyContext: KeyContext,
    renderPassDesc: RenderPassDesc,
    processCombination: PaintOptionsPriv.ProcessCombination,
  ) {
    TODO("Implement onCreatePipelines")
  }
}
