package org.skia.gpu

import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan

/**
 * C++ original:
 * ```cpp
 * class PrecompileLightingImageFilter : public PrecompileImageFilter {
 * public:
 *     PrecompileLightingImageFilter(SkSpan<sk_sp<PrecompileImageFilter>> inputs)
 *             : PrecompileImageFilter(std::move(inputs)) {
 *     }
 *
 * private:
 *     void onCreatePipelines(
 *             const KeyContext& keyContext,
 *             const RenderPassDesc& renderPassDesc,
 *             const PaintOptionsPriv::ProcessCombination& processCombination) const override {
 *
 *         sk_sp<PrecompileShader> imageShader = PrecompileShaders::Image(
 *                 ImageShaderFlags::kNoAlphaNoCubic);
 *
 *         PaintOptions lighting;
 *         lighting.setShaders({{ PrecompileShadersPriv::Lighting(std::move(imageShader)) }});
 *
 *         lighting.priv().buildCombinations(keyContext,
 *                                           DrawTypeFlags::kSimpleShape,
 *                                           /* withPrimitiveBlender= */ false,
 *                                           Coverage::kSingleChannel,
 *                                           renderPassDesc,
 *                                           processCombination);
 *     }
 * }
 * ```
 */
public open class PrecompileLightingImageFilter public constructor(
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
   *         sk_sp<PrecompileShader> imageShader = PrecompileShaders::Image(
   *                 ImageShaderFlags::kNoAlphaNoCubic);
   *
   *         PaintOptions lighting;
   *         lighting.setShaders({{ PrecompileShadersPriv::Lighting(std::move(imageShader)) }});
   *
   *         lighting.priv().buildCombinations(keyContext,
   *                                           DrawTypeFlags::kSimpleShape,
   *                                           /* withPrimitiveBlender= */ false,
   *                                           Coverage::kSingleChannel,
   *                                           renderPassDesc,
   *                                           processCombination);
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
