package org.skia.gpu

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct NonMSAAClipBlock {
 *     struct NonMSAAClipData {
 *         NonMSAAClipData(SkRect rect,
 *                         SkPoint radiusPlusHalf,
 *                         SkRect edgeSelect,
 *                         SkPoint texCoordOffset,
 *                         SkRect maskBounds,
 *                         sk_sp<TextureProxy> atlasTexture)
 *                 : fRect(rect)
 *                 , fRadiusPlusHalf(radiusPlusHalf)
 *                 , fEdgeSelect(edgeSelect)
 *                 , fTexCoordOffset(texCoordOffset)
 *                 , fMaskBounds(maskBounds)
 *                 , fAtlasTexture(std::move(atlasTexture)){}
 *         // analytic clip
 *         SkRect  fRect;            // bounds, outset by 0.5
 *         SkPoint fRadiusPlusHalf;  // abs() of .x is radius+0.5, if < 0 indicates inverse fill
 *                                   // .y is 1/(radius+0.5)
 *         SkRect  fEdgeSelect;      // 1 indicates a rounded corner on that side (LTRB), 0 otherwise
 *
 *         // atlas clip
 *         SkPoint fTexCoordOffset;  // translation from local coords to unnormalized texel coords
 *         SkRect  fMaskBounds;      // bounds of mask area, in unnormalized texel coords
 *
 *         sk_sp<TextureProxy> fAtlasTexture;
 *     };
 *
 *     static void AddBlock(const KeyContext&, const NonMSAAClipData&);
 * }
 * ```
 */
public open class NonMSAAClipBlock {
  public data class NonMSAAClipData public constructor(
    public var fRect: Int,
    public var fRadiusPlusHalf: Int,
    public var fEdgeSelect: Int,
    public var fTexCoordOffset: Int,
    public var fMaskBounds: Int,
    public var fAtlasTexture: Int,
  )

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * void NonMSAAClipBlock::AddBlock(const KeyContext& keyContext, const NonMSAAClipData& data) {
     *     if (data.fAtlasTexture) {
     *         add_analytic_and_atlas_clip_data(keyContext, data);
     *         keyContext.paintParamsKeyBuilder()->beginBlock(BuiltInCodeSnippetID::kAnalyticAndAtlasClip);
     *
     *         const Caps* caps = keyContext.caps();
     *         ImmutableSamplerInfo info =
     *                 caps->getImmutableSamplerInfo(data.fAtlasTexture->textureInfo());
     *         SamplerDesc samplerDesc {SkSamplingOptions(SkFilterMode::kNearest, SkMipmapMode::kNone),
     *                                  {SkTileMode::kClamp, SkTileMode::kClamp},
     *                                  info};
     *         keyContext.pipelineDataGatherer()->add(data.fAtlasTexture, samplerDesc);
     *
     *         keyContext.paintParamsKeyBuilder()->endBlock();
     *     } else {
     *         add_analytic_clip_data(keyContext, data);
     *         keyContext.paintParamsKeyBuilder()->addBlock(BuiltInCodeSnippetID::kAnalyticClip);
     *     }
     * }
     * ```
     */
    public fun addBlock(keyContext: KeyContext, `data`: NonMSAAClipData) {
      TODO("Implement addBlock")
    }
  }
}
