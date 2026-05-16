package org.skia.gpu

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct ImageShaderBlock {
 *     struct ImageData {
 *         ImageData(const SkSamplingOptions& sampling,
 *                   SkTileMode tileModeX,
 *                   SkTileMode tileModeY,
 *                   SkISize imgSize,
 *                   SkRect subset,
 *                   ImmutableSamplerInfo immutableSamplerInfo = {});
 *         SkSamplingOptions fSampling;
 *         std::pair<SkTileMode, SkTileMode> fTileModes;
 *         SkISize fImgSize;
 *         SkRect fSubset;
 *
 *         // When we're generating the key from an actual SkImageShader fTextureProxy will be
 *         // non-null. Otherwise, fImmutableSamplerInfo will be filled in.
 *         sk_sp<TextureProxy> fTextureProxy;
 *         ImmutableSamplerInfo fImmutableSamplerInfo;
 *     };
 *
 *     static void AddBlock(const KeyContext&, const ImageData&);
 * }
 * ```
 */
public open class ImageShaderBlock {
  public data class ImageData public constructor(
    public var fSampling: Int,
    public var fTileModes: Int,
    public var fImgSize: Int,
    public var fSubset: Int,
    public var fTextureProxy: Int,
    public var fImmutableSamplerInfo: Int,
  )

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * void ImageShaderBlock::AddBlock(const KeyContext& keyContext, const ImageData& imgData) {
     *     if (keyContext.recorder() && !imgData.fTextureProxy) {
     *         keyContext.paintParamsKeyBuilder()->addErrorBlock();
     *         return;
     *     }
     *
     *     const Caps* caps = keyContext.caps();
     *     const bool doTilingInHw = !imgData.fSampling.useCubic && can_do_tiling_in_hw(imgData, caps);
     *
     *     if (doTilingInHw) {
     *         CoordNormalizeShaderBlock::CoordNormalizeData data(SkSize::Make(imgData.fImgSize));
     *         CoordNormalizeShaderBlock::BeginBlock(keyContext, data);
     *         keyContext.paintParamsKeyBuilder()->beginBlock(BuiltInCodeSnippetID::kHWImageShader);
     *     } else if (imgData.fSampling.useCubic) {
     *         add_cubic_image_uniform_data(keyContext, imgData);
     *         keyContext.paintParamsKeyBuilder()->beginBlock(BuiltInCodeSnippetID::kCubicImageShader);
     *     } else if (imgData.fTileModes.first == SkTileMode::kClamp &&
     *                imgData.fTileModes.second == SkTileMode::kClamp) {
     *         add_clamp_image_uniform_data(keyContext, imgData);
     *         keyContext.paintParamsKeyBuilder()->beginBlock(BuiltInCodeSnippetID::kImageShaderClamp);
     *     } else {
     *         add_image_uniform_data(keyContext, imgData);
     *         keyContext.paintParamsKeyBuilder()->beginBlock(BuiltInCodeSnippetID::kImageShader);
     *     }
     *
     *     // Image shaders must append immutable sampler data (or '0' in the more common case where
     *     // regular samplers are used).
     *     // TODO(b/392623124): In precompile mode (fTextureProxy == null), we still have a need for
     *     // immutable samplers, which must be passed in somehow.
     *     ImmutableSamplerInfo info = imgData.fTextureProxy
     *             ? caps->getImmutableSamplerInfo(imgData.fTextureProxy->textureInfo())
     *             : imgData.fImmutableSamplerInfo;
     *     auto tileModeWithSubstitution = doTilingInHw ? imgData.fTileModes :
     *                                     std::make_pair(SkTileMode::kClamp, SkTileMode::kClamp);
     *     SamplerDesc samplerDesc{imgData.fSampling, tileModeWithSubstitution, info};
     *     keyContext.pipelineDataGatherer()->add(imgData.fTextureProxy, samplerDesc);
     *     add_sampler_data_to_key(keyContext, samplerDesc);
     *
     *     keyContext.paintParamsKeyBuilder()->endBlock();
     *
     *     if (doTilingInHw) {
     *         // Additional block for coord normalization.
     *         keyContext.paintParamsKeyBuilder()->endBlock();
     *     }
     * }
     * ```
     */
    public fun addBlock(keyContext: KeyContext, imgData: ImageData) {
      TODO("Implement addBlock")
    }
  }
}
