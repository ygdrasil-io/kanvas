package org.skia.gpu

import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan

/**
 * C++ original:
 * ```cpp
 * class PrecompileMorphologyImageFilter : public PrecompileImageFilter {
 * public:
 *     PrecompileMorphologyImageFilter(SkSpan<sk_sp<PrecompileImageFilter>> inputs)
 *             : PrecompileImageFilter(std::move(inputs)) {
 *     }
 *
 * private:
 *     void onCreatePipelines(
 *             const KeyContext& keyContext,
 *             const RenderPassDesc& renderPassDesc,
 *             const PaintOptionsPriv::ProcessCombination& processCombination) const override {
 *
 *         // For morphology imagefilters we know we don't have alpha-only textures and don't need
 *         // cubic filtering.
 *         sk_sp<PrecompileShader> imageShader = PrecompileShaders::Image(
 *                 ImageShaderFlags::kNoAlphaNoCubic);
 *
 *         {
 *             PaintOptions sparse;
 *
 *             static const SkBlendMode kBlendModes[] = { SkBlendMode::kSrc };
 *             sparse.setShaders({{ PrecompileShadersPriv::SparseMorphology(imageShader) }});
 *             sparse.setBlendModes(kBlendModes);
 *
 *             sparse.priv().buildCombinations(keyContext,
 *                                             DrawTypeFlags::kSimpleShape,
 *                                             /* withPrimitiveBlender= */ false,
 *                                             Coverage::kSingleChannel,
 *                                             renderPassDesc,
 *                                             processCombination);
 *         }
 *
 *         {
 *             PaintOptions linear;
 *
 *             static const SkBlendMode kBlendModes[] = { SkBlendMode::kSrcOver };
 *             linear.setShaders({{ PrecompileShadersPriv::LinearMorphology(std::move(imageShader))}});
 *             linear.setBlendModes(kBlendModes);
 *
 *             linear.priv().buildCombinations(keyContext,
 *                                             DrawTypeFlags::kSimpleShape,
 *                                             /* withPrimitiveBlender= */ false,
 *                                             Coverage::kSingleChannel,
 *                                             renderPassDesc,
 *                                             processCombination);
 *         }
 *     }
 * }
 * ```
 */
public open class PrecompileMorphologyImageFilter public constructor(
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
   *         // For morphology imagefilters we know we don't have alpha-only textures and don't need
   *         // cubic filtering.
   *         sk_sp<PrecompileShader> imageShader = PrecompileShaders::Image(
   *                 ImageShaderFlags::kNoAlphaNoCubic);
   *
   *         {
   *             PaintOptions sparse;
   *
   *             static const SkBlendMode kBlendModes[] = { SkBlendMode::kSrc };
   *             sparse.setShaders({{ PrecompileShadersPriv::SparseMorphology(imageShader) }});
   *             sparse.setBlendModes(kBlendModes);
   *
   *             sparse.priv().buildCombinations(keyContext,
   *                                             DrawTypeFlags::kSimpleShape,
   *                                             /* withPrimitiveBlender= */ false,
   *                                             Coverage::kSingleChannel,
   *                                             renderPassDesc,
   *                                             processCombination);
   *         }
   *
   *         {
   *             PaintOptions linear;
   *
   *             static const SkBlendMode kBlendModes[] = { SkBlendMode::kSrcOver };
   *             linear.setShaders({{ PrecompileShadersPriv::LinearMorphology(std::move(imageShader))}});
   *             linear.setBlendModes(kBlendModes);
   *
   *             linear.priv().buildCombinations(keyContext,
   *                                             DrawTypeFlags::kSimpleShape,
   *                                             /* withPrimitiveBlender= */ false,
   *                                             Coverage::kSingleChannel,
   *                                             renderPassDesc,
   *                                             processCombination);
   *         }
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
