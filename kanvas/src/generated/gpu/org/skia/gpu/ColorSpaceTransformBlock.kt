package org.skia.gpu

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct ColorSpaceTransformBlock {
 *     struct ColorSpaceTransformData {
 *         ColorSpaceTransformData(const SkColorSpace* src,
 *                                 SkAlphaType srcAT,
 *                                 const SkColorSpace* dst,
 *                                 SkAlphaType dstAT);
 *         ColorSpaceTransformData(const SkColorSpaceXformSteps& steps) { fSteps = steps; }
 *         ColorSpaceTransformData(ReadSwizzle swizzle) : fReadSwizzle(swizzle) {
 *             SkASSERT(fSteps.fFlags.mask() == 0);  // By default, the colorspace should have no effect
 *         }
 *         SkColorSpaceXformSteps fSteps;
 *         ReadSwizzle            fReadSwizzle = ReadSwizzle::kRGBA;
 *     };
 *
 *     static void AddBlock(const KeyContext&, const ColorSpaceTransformData&);
 * }
 * ```
 */
public open class ColorSpaceTransformBlock {
  public data class ColorSpaceTransformData public constructor(
    public var fSteps: Int,
    public var fReadSwizzle: ReadSwizzle,
  )

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * void ColorSpaceTransformBlock::AddBlock(const KeyContext& keyContext,
     *                                         const ColorSpaceTransformData& data) {
     *     const bool xformNeedsGamutOrXferFn = data.fSteps.fFlags.linearize ||
     *                                          data.fSteps.fFlags.encode    ||
     *                                          data.fSteps.fFlags.src_ootf  ||
     *                                          data.fSteps.fFlags.dst_ootf  ||
     *                                          data.fSteps.fFlags.gamut_transform;
     *     const bool swizzleNeedsGamutTransform = !(data.fReadSwizzle == ReadSwizzle::kRGBA ||
     *                                               data.fReadSwizzle == ReadSwizzle::kRGB1);
     *
     *     // Use a specialized shader if we don't need transfer function or gamut transforms.
     *     if (!(xformNeedsGamutOrXferFn || swizzleNeedsGamutTransform)) {
     *         // When enabled, the most specialized is to do nothing at all. To simplify calling code,
     *         // this adds a passthrough block vs. having callers know how to reconfigure their blocks.
     *         if (SkToBool(keyContext.flags() & KeyGenFlags::kEnableIdentityColorSpaceXform) &&
     *             data.fReadSwizzle == ReadSwizzle::kRGBA &&
     *             !data.fSteps.fFlags.premul && !data.fSteps.fFlags.unpremul) {
     *             keyContext.paintParamsKeyBuilder()->addBlock(BuiltInCodeSnippetID::kPriorOutput);
     *             return;
     *         }
     *
     *         add_color_space_uniforms(keyContext, BuiltInCodeSnippetID::kColorSpaceXformPremul,
     *                                  data.fSteps, data.fReadSwizzle);
     *         keyContext.paintParamsKeyBuilder()->addBlock(BuiltInCodeSnippetID::kColorSpaceXformPremul);
     *         return;
     *     }
     *
     *     // Use a specialized shader if we're transferring to and from sRGB-ish color spaces.
     *     // We take this path even if linearize/encode are false since we can set coefficients
     *     // in the sRGB transfer functions to represent identity, and that is better than using the
     *     // most general colorspace option.
     *     if ((!data.fSteps.fFlags.linearize || skcms_TransferFunction_isSRGBish(&data.fSteps.fSrcTF)) &&
     *         (!data.fSteps.fFlags.encode || skcms_TransferFunction_isSRGBish(&data.fSteps.fDstTFInv))) {
     *         add_color_space_uniforms(keyContext, BuiltInCodeSnippetID::kColorSpaceXformSRGB,
     *                                  data.fSteps, data.fReadSwizzle);
     *         keyContext.paintParamsKeyBuilder()->addBlock(BuiltInCodeSnippetID::kColorSpaceXformSRGB);
     *         return;
     *     }
     *
     *     // Use the most general color space transform shader if no specializations can be used.
     *     add_color_space_uniforms(keyContext, BuiltInCodeSnippetID::kColorSpaceXformColorFilter,
     *                              data.fSteps, data.fReadSwizzle);
     *     keyContext.paintParamsKeyBuilder()->addBlock(BuiltInCodeSnippetID::kColorSpaceXformColorFilter);
     * }
     * ```
     */
    public fun addBlock(keyContext: KeyContext, `data`: ColorSpaceTransformData) {
      TODO("Implement addBlock")
    }
  }
}
