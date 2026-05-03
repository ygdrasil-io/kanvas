package org.skia.gpu

import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class PrecompileColorFilterImageFilter : public PrecompileImageFilter {
 * public:
 *     PrecompileColorFilterImageFilter(sk_sp<PrecompileColorFilter> colorFilter,
 *                                      sk_sp<PrecompileImageFilter> input)
 *             : PrecompileImageFilter(SkSpan(&input, 1))
 *             , fColorFilter(std::move(colorFilter)) {
 *     }
 *
 * private:
 *     sk_sp<PrecompileColorFilter> isColorFilterNode() const override {
 *         return fColorFilter;
 *     }
 *
 *     void onCreatePipelines(
 *             const KeyContext& keyContext,
 *             const RenderPassDesc& renderPassDesc,
 *             const PaintOptionsPriv::ProcessCombination& processCombination) const override {
 *         PaintOptions paintOptions;
 *
 *         sk_sp<PrecompileShader> imageShader = PrecompileShaders::Image(
 *                 ImageShaderFlags::kNoAlphaNoCubic);
 *
 *         static const SkBlendMode kBlendModes[] = { SkBlendMode::kDstOut };
 *         paintOptions.setShaders({{ std::move(imageShader) }});
 *         paintOptions.setColorFilters({{ fColorFilter }});
 *         paintOptions.setBlendModes(kBlendModes);
 *
 *         paintOptions.priv().buildCombinations(keyContext,
 *                                               DrawTypeFlags::kSimpleShape,
 *                                               /* withPrimitiveBlender= */ false,
 *                                               Coverage::kSingleChannel,
 *                                               renderPassDesc,
 *                                               processCombination);
 *     }
 *
 *     sk_sp<PrecompileColorFilter> fColorFilter;
 * }
 * ```
 */
public open class PrecompileColorFilterImageFilter public constructor(
  colorFilter: SkSp<PrecompileColorFilter>,
  input: SkSp<PrecompileImageFilter>,
) : PrecompileImageFilter(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<PrecompileColorFilter> fColorFilter
   * ```
   */
  private var fColorFilter: SkSp<PrecompileColorFilter> = TODO("Initialize fColorFilter")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<PrecompileColorFilter> isColorFilterNode() const override {
   *         return fColorFilter;
   *     }
   * ```
   */
  public override fun isColorFilterNode(): SkSp<PrecompileColorFilter> {
    TODO("Implement isColorFilterNode")
  }

  /**
   * C++ original:
   * ```cpp
   * void onCreatePipelines(
   *             const KeyContext& keyContext,
   *             const RenderPassDesc& renderPassDesc,
   *             const PaintOptionsPriv::ProcessCombination& processCombination) const override {
   *         PaintOptions paintOptions;
   *
   *         sk_sp<PrecompileShader> imageShader = PrecompileShaders::Image(
   *                 ImageShaderFlags::kNoAlphaNoCubic);
   *
   *         static const SkBlendMode kBlendModes[] = { SkBlendMode::kDstOut };
   *         paintOptions.setShaders({{ std::move(imageShader) }});
   *         paintOptions.setColorFilters({{ fColorFilter }});
   *         paintOptions.setBlendModes(kBlendModes);
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
