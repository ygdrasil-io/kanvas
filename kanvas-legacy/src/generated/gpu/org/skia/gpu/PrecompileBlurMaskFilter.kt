package org.skia.gpu

/**
 * C++ original:
 * ```cpp
 * class PrecompileBlurMaskFilter : public PrecompileMaskFilter {
 * public:
 *     PrecompileBlurMaskFilter() {}
 *
 * private:
 *     void createPipelines(
 *             const KeyContext& keyContext,
 *             const PaintOptions& paintOptions,
 *             const RenderPassDesc& renderPassDescIn,
 *             const PaintOptionsPriv::ProcessCombination& processCombination) const override {
 *         const Caps* caps = keyContext.caps();
 *         // TODO: pull Protected-ness from 'renderPassDescIn'
 *         TextureInfo info = caps->getDefaultSampledTextureInfo(kAlpha_8_SkColorType,
 *                                                               Mipmapped::kNo,
 *                                                               Protected::kNo,
 *                                                               Renderable::kYes);
 *
 *         RenderPassDesc coverageRenderPassDesc = RenderPassDesc::Make(
 *                 caps,
 *                 info,
 *                 LoadOp::kClear,
 *                 StoreOp::kStore,
 *                 DepthStencilFlags::kDepth,
 *                 { 0.0f, 0.0f, 0.0f, 0.0f },
 *                 /* requiresMSAA= */ false,
 *                 skgpu::Swizzle("a000"),
 *                 caps->getDstReadStrategy());
 *
 *         PrecompileImageFiltersPriv::CreateBlurImageFilterPipelines(keyContext,
 *                                                                    coverageRenderPassDesc,
 *                                                                    processCombination);
 *
 *         // c.f. AutoLayerForImageFilter::addMaskFilterLayer. The following PaintOptions handle
 *         // the case where an explicit layer must be created.
 *         {
 *             // The restore draw takes over all the shading effects. The mask filter blur will have
 *             // been converted to an image filter applied to the coverage layer. That coverage
 *             // will then be used as the coverage mask for the restoreOptions.
 *             PaintOptions restoreOptions = paintOptions;
 *             restoreOptions.setMaskFilters({});
 *             restoreOptions.priv().buildCombinations(
 *                     keyContext,
 *                     static_cast<DrawTypeFlags>(InternalDrawTypeFlags::kCoverageMask),
 *                     /* withPrimitiveBlender= */ false,
 *                     Coverage::kSingleChannel,
 *                     renderPassDescIn,
 *                     processCombination);
 *         }
 *
 *         {
 *             // The initial draw into the coverage layer is just a solid white kSrcOver SkPaint
 *             // These options cover the case where the coverage draw can be done with the
 *             // AnalyticRRect RenderStep.
 *             // TODO: gate the inclusion of this option on the drawType being kSimple
 *             PaintOptions coverageOptions;
 *             coverageOptions.setShaders({{ PrecompileShaders::Color() }});
 *             coverageOptions.setBlendModes(SKSPAN_INIT_ONE(SkBlendMode::kSrcOver));
 *
 *             coverageOptions.priv().buildCombinations(
 *                     keyContext,
 *                     DrawTypeFlags::kAnalyticRRect,
 *                     /* withPrimitiveBlender= */ false,
 *                     Coverage::kSingleChannel,
 *                     coverageRenderPassDesc,
 *                     processCombination);
 *         }
 *     }
 * }
 * ```
 */
public open class PrecompileBlurMaskFilter public constructor() : PrecompileMaskFilter() {
  /**
   * C++ original:
   * ```cpp
   * void createPipelines(
   *             const KeyContext& keyContext,
   *             const PaintOptions& paintOptions,
   *             const RenderPassDesc& renderPassDescIn,
   *             const PaintOptionsPriv::ProcessCombination& processCombination) const override {
   *         const Caps* caps = keyContext.caps();
   *         // TODO: pull Protected-ness from 'renderPassDescIn'
   *         TextureInfo info = caps->getDefaultSampledTextureInfo(kAlpha_8_SkColorType,
   *                                                               Mipmapped::kNo,
   *                                                               Protected::kNo,
   *                                                               Renderable::kYes);
   *
   *         RenderPassDesc coverageRenderPassDesc = RenderPassDesc::Make(
   *                 caps,
   *                 info,
   *                 LoadOp::kClear,
   *                 StoreOp::kStore,
   *                 DepthStencilFlags::kDepth,
   *                 { 0.0f, 0.0f, 0.0f, 0.0f },
   *                 /* requiresMSAA= */ false,
   *                 skgpu::Swizzle("a000"),
   *                 caps->getDstReadStrategy());
   *
   *         PrecompileImageFiltersPriv::CreateBlurImageFilterPipelines(keyContext,
   *                                                                    coverageRenderPassDesc,
   *                                                                    processCombination);
   *
   *         // c.f. AutoLayerForImageFilter::addMaskFilterLayer. The following PaintOptions handle
   *         // the case where an explicit layer must be created.
   *         {
   *             // The restore draw takes over all the shading effects. The mask filter blur will have
   *             // been converted to an image filter applied to the coverage layer. That coverage
   *             // will then be used as the coverage mask for the restoreOptions.
   *             PaintOptions restoreOptions = paintOptions;
   *             restoreOptions.setMaskFilters({});
   *             restoreOptions.priv().buildCombinations(
   *                     keyContext,
   *                     static_cast<DrawTypeFlags>(InternalDrawTypeFlags::kCoverageMask),
   *                     /* withPrimitiveBlender= */ false,
   *                     Coverage::kSingleChannel,
   *                     renderPassDescIn,
   *                     processCombination);
   *         }
   *
   *         {
   *             // The initial draw into the coverage layer is just a solid white kSrcOver SkPaint
   *             // These options cover the case where the coverage draw can be done with the
   *             // AnalyticRRect RenderStep.
   *             // TODO: gate the inclusion of this option on the drawType being kSimple
   *             PaintOptions coverageOptions;
   *             coverageOptions.setShaders({{ PrecompileShaders::Color() }});
   *             coverageOptions.setBlendModes(SKSPAN_INIT_ONE(SkBlendMode::kSrcOver));
   *
   *             coverageOptions.priv().buildCombinations(
   *                     keyContext,
   *                     DrawTypeFlags::kAnalyticRRect,
   *                     /* withPrimitiveBlender= */ false,
   *                     Coverage::kSingleChannel,
   *                     coverageRenderPassDesc,
   *                     processCombination);
   *         }
   *     }
   * ```
   */
  public override fun createPipelines(
    keyContext: KeyContext,
    paintOptions: PaintOptions,
    renderPassDescIn: RenderPassDesc,
    processCombination: PaintOptionsPriv.ProcessCombination,
  ) {
    TODO("Implement createPipelines")
  }
}
