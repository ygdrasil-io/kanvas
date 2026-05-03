package org.skia.gpu

import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan

/**
 * C++ original:
 * ```cpp
 * class PrecompileDisplacementMapImageFilter : public PrecompileImageFilter {
 * public:
 *     PrecompileDisplacementMapImageFilter(SkSpan<sk_sp<PrecompileImageFilter>> inputs)
 *             : PrecompileImageFilter(std::move(inputs)) {
 *     }
 *
 * private:
 *     void onCreatePipelines(
 *             const KeyContext& keyContext,
 *             const RenderPassDesc& renderPassDesc,
 *             const PaintOptionsPriv::ProcessCombination& processCombination) const override {
 *
 *         PaintOptions displacement;
 *
 *         // For displacement imagefilters we know we don't have alpha-only textures and don't need
 *         // cubic filtering.
 *         sk_sp<PrecompileShader> imageShader = PrecompileShaders::Image(
 *                 ImageShaderFlags::kNoAlphaNoCubic);
 *
 *         displacement.setShaders({{ PrecompileShadersPriv::Displacement(imageShader, imageShader)}});
 *
 *         displacement.priv().buildCombinations(keyContext,
 *                                               DrawTypeFlags::kSimpleShape,
 *                                               /* withPrimitiveBlender= */ false,
 *                                               Coverage::kSingleChannel,
 *                                               renderPassDesc,
 *                                               processCombination);
 *     }
 * }
 * ```
 */
public open class PrecompileDisplacementMapImageFilter public constructor(
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
   *         PaintOptions displacement;
   *
   *         // For displacement imagefilters we know we don't have alpha-only textures and don't need
   *         // cubic filtering.
   *         sk_sp<PrecompileShader> imageShader = PrecompileShaders::Image(
   *                 ImageShaderFlags::kNoAlphaNoCubic);
   *
   *         displacement.setShaders({{ PrecompileShadersPriv::Displacement(imageShader, imageShader)}});
   *
   *         displacement.priv().buildCombinations(keyContext,
   *                                               DrawTypeFlags::kSimpleShape,
   *                                               /* withPrimitiveBlender= */ false,
   *                                               Coverage::kSingleChannel,
   *                                               renderPassDesc,
   *                                               processCombination);
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
