package org.skia.gpu

import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan

/**
 * C++ original:
 * ```cpp
 * class PrecompileBlendFilterImageFilter : public PrecompileImageFilter {
 * public:
 *     PrecompileBlendFilterImageFilter(sk_sp<PrecompileBlender> blender,
 *                                      SkSpan<sk_sp<PrecompileImageFilter>> inputs)
 *             : PrecompileImageFilter(std::move(inputs))
 *             , fBlender(std::move(blender)) {
 *     }
 *
 * private:
 *     void onCreatePipelines(
 *             const KeyContext& keyContext,
 *             const RenderPassDesc& renderPassDesc,
 *             const PaintOptionsPriv::ProcessCombination& processCombination) const override {
 *
 *         PaintOptions paintOptions;
 *
 *         sk_sp<PrecompileShader> imageShader = PrecompileShaders::Image(
 *                 ImageShaderFlags::kNoAlphaNoCubic);
 *
 *         sk_sp<PrecompileShader> blendShader = PrecompileShaders::Blend(
 *                 SkSpan<const sk_sp<PrecompileBlender>>(&fBlender, 1),
 *                 {{ imageShader }},
 *                 {{ imageShader }});
 *
 *         paintOptions.setShaders({{ std::move(blendShader) }});
 *
 *         paintOptions.priv().buildCombinations(keyContext,
 *                                               DrawTypeFlags::kSimpleShape,
 *                                               /* withPrimitiveBlender= */ false,
 *                                               Coverage::kSingleChannel,
 *                                               renderPassDesc,
 *                                               processCombination);
 *     }
 *
 *     sk_sp<PrecompileBlender> fBlender;
 * }
 * ```
 */
public open class PrecompileBlendFilterImageFilter public constructor(
  blender: SkSp<PrecompileBlender>,
  inputs: SkSpan<SkSp<PrecompileImageFilter>>,
) : PrecompileImageFilter(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<PrecompileBlender> fBlender
   * ```
   */
  private var fBlender: SkSp<PrecompileBlender> = TODO("Initialize fBlender")

  /**
   * C++ original:
   * ```cpp
   * void onCreatePipelines(
   *             const KeyContext& keyContext,
   *             const RenderPassDesc& renderPassDesc,
   *             const PaintOptionsPriv::ProcessCombination& processCombination) const override {
   *
   *         PaintOptions paintOptions;
   *
   *         sk_sp<PrecompileShader> imageShader = PrecompileShaders::Image(
   *                 ImageShaderFlags::kNoAlphaNoCubic);
   *
   *         sk_sp<PrecompileShader> blendShader = PrecompileShaders::Blend(
   *                 SkSpan<const sk_sp<PrecompileBlender>>(&fBlender, 1),
   *                 {{ imageShader }},
   *                 {{ imageShader }});
   *
   *         paintOptions.setShaders({{ std::move(blendShader) }});
   *
   *         paintOptions.priv().buildCombinations(keyContext,
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
