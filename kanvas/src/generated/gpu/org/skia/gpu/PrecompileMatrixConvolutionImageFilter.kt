package org.skia.gpu

import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan

/**
 * C++ original:
 * ```cpp
 * class PrecompileMatrixConvolutionImageFilter : public PrecompileImageFilter {
 * public:
 *     PrecompileMatrixConvolutionImageFilter(SkSpan<sk_sp<PrecompileImageFilter>> inputs)
 *             : PrecompileImageFilter(std::move(inputs)) {
 *     }
 *
 * private:
 *     void onCreatePipelines(
 *             const KeyContext& keyContext,
 *             const RenderPassDesc& renderPassDesc,
 *             const PaintOptionsPriv::ProcessCombination& processCombination) const override {
 *
 *         PaintOptions matrixConv;
 *
 *         // For matrix convolution imagefilters we know we don't have alpha-only textures and don't
 *         // need cubic filtering.
 *         sk_sp<PrecompileShader> imageShader = PrecompileShaders::Image(
 *                 ImageShaderFlags::kNoAlphaNoCubic);
 *
 *         matrixConv.setShaders({{ PrecompileShadersPriv::MatrixConvolution(imageShader) }});
 *
 *         matrixConv.priv().buildCombinations(keyContext,
 *                                             DrawTypeFlags::kSimpleShape,
 *                                             /* withPrimitiveBlender= */ false,
 *                                             Coverage::kSingleChannel,
 *                                             renderPassDesc,
 *                                             processCombination);
 *     }
 * }
 * ```
 */
public open class PrecompileMatrixConvolutionImageFilter public constructor(
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
   *         PaintOptions matrixConv;
   *
   *         // For matrix convolution imagefilters we know we don't have alpha-only textures and don't
   *         // need cubic filtering.
   *         sk_sp<PrecompileShader> imageShader = PrecompileShaders::Image(
   *                 ImageShaderFlags::kNoAlphaNoCubic);
   *
   *         matrixConv.setShaders({{ PrecompileShadersPriv::MatrixConvolution(imageShader) }});
   *
   *         matrixConv.priv().buildCombinations(keyContext,
   *                                             DrawTypeFlags::kSimpleShape,
   *                                             /* withPrimitiveBlender= */ false,
   *                                             Coverage::kSingleChannel,
   *                                             renderPassDesc,
   *                                             processCombination);
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
