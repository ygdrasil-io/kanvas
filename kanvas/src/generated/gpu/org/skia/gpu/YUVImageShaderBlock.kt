package org.skia.gpu

import kotlin.Float
import kotlin.Int
import kotlin.IntArray

/**
 * C++ original:
 * ```cpp
 * struct YUVImageShaderBlock {
 *     struct ImageData {
 *         ImageData(const SkSamplingOptions& sampling,
 *                   SkTileMode tileModeX,
 *                   SkTileMode tileModeY,
 *                   SkISize imgSize,
 *                   SkRect subset);
 *
 *         SkSamplingOptions fSampling;
 *         SkSamplingOptions fSamplingUV;
 *         std::pair<SkTileMode, SkTileMode> fTileModes;
 *         SkISize fImgSize;
 *         SkISize fImgSizeUV;  // Size of UV planes relative to Y's texel space
 *         SkRect fSubset;
 *         SkPoint fLinearFilterUVInset = { 0.50001f, 0.50001f };
 *         SkV4 fChannelSelect[4];
 *         float fAlphaParam = 0;
 *         SkMatrix fYUVtoRGBMatrix;
 *         SkPoint3 fYUVtoRGBTranslate;
 *
 *         // TODO: Currently these are only filled in when we're generating the key from an actual
 *         // SkImageShader. In the pre-compile case we will need to create Graphite promise
 *         // images which hold the appropriate data.
 *         sk_sp<TextureProxy> fTextureProxies[4];
 *     };
 *
 *     static void AddBlock(const KeyContext&, const ImageData&);
 * }
 * ```
 */
public open class YUVImageShaderBlock {
  public data class ImageData public constructor(
    public var fSampling: Int,
    public var fSamplingUV: Int,
    public var fTileModes: Int,
    public var fImgSize: Int,
    public var fImgSizeUV: Int,
    public var fSubset: Int,
    public var fLinearFilterUVInset: Int,
    public var fChannelSelect: IntArray,
    public var fAlphaParam: Float,
    public var fYUVtoRGBMatrix: Int,
    public var fYUVtoRGBTranslate: Int,
    public var fTextureProxies: IntArray,
  )

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * void YUVImageShaderBlock::AddBlock(const KeyContext& keyContext, const ImageData& imgData) {
     *     if (keyContext.recorder() &&
     *         (!imgData.fTextureProxies[0] || !imgData.fTextureProxies[1] ||
     *          !imgData.fTextureProxies[2] || !imgData.fTextureProxies[3])) {
     *         keyContext.paintParamsKeyBuilder()->addErrorBlock();
     *         return;
     *     }
     *
     *     const Caps* caps = keyContext.caps();
     *     const bool doTilingInHw = !imgData.fSampling.useCubic && can_do_yuv_tiling_in_hw(imgData, caps);
     *     const bool noYUVSwizzle = no_yuv_swizzle(imgData);
     *
     *     // uvs are never SkTileMode::kDecal
     *     auto uvTileModes = std::make_pair(imgData.fTileModes.first == SkTileMode::kDecal
     *                                             ? SkTileMode::kClamp : imgData.fTileModes.first,
     *                                       imgData.fTileModes.second == SkTileMode::kDecal
     *                                             ? SkTileMode::kClamp : imgData.fTileModes.second);
     *     auto yAlphaTileModes = doTilingInHw ? imgData.fTileModes :
     *                            std::make_pair(SkTileMode::kClamp, SkTileMode::kClamp);
     *     keyContext.pipelineDataGatherer()->add(imgData.fTextureProxies[0],
     *                                            {imgData.fSampling, yAlphaTileModes});
     *     keyContext.pipelineDataGatherer()->add(imgData.fTextureProxies[1],
     *                                            {imgData.fSamplingUV, uvTileModes});
     *     keyContext.pipelineDataGatherer()->add(imgData.fTextureProxies[2],
     *                                            {imgData.fSamplingUV, uvTileModes});
     *     keyContext.pipelineDataGatherer()->add(imgData.fTextureProxies[3],
     *                                            {imgData.fSampling, yAlphaTileModes});
     *
     *     if (doTilingInHw && noYUVSwizzle) {
     *         add_hw_yuv_no_swizzle_image_uniform_data(keyContext, imgData);
     *         keyContext.paintParamsKeyBuilder()->addBlock(
     *                 BuiltInCodeSnippetID::kHWYUVNoSwizzleImageShader);
     *     } else if (doTilingInHw) {
     *         add_hw_yuv_image_uniform_data(keyContext, imgData);
     *         keyContext.paintParamsKeyBuilder()->addBlock(
     *                 BuiltInCodeSnippetID::kHWYUVImageShader);
     *     } else if (imgData.fSampling.useCubic) {
     *         add_cubic_yuv_image_uniform_data(keyContext, imgData);
     *         keyContext.paintParamsKeyBuilder()->addBlock(
     *                 BuiltInCodeSnippetID::kCubicYUVImageShader);
     *     } else {
     *         add_yuv_image_uniform_data(keyContext, imgData);
     *         keyContext.paintParamsKeyBuilder()->addBlock(
     *                 BuiltInCodeSnippetID::kYUVImageShader);
     *     }
     * }
     * ```
     */
    public fun addBlock(keyContext: KeyContext, imgData: ImageData) {
      TODO("Implement addBlock")
    }
  }
}
