package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import org.skia.core.SkEnumBitMask
import org.skia.foundation.SkColorInfo
import org.skia.foundation.SkSpan
import org.skia.foundation.SkTileMode

/**
 * C++ original:
 * ```cpp
 * class PrecompileImageShader final : public PrecompileShader {
 * public:
 *     PrecompileImageShader(SkEnumBitMask<PrecompileShaders::ImageShaderFlags> flags,
 *                           SkSpan<const SkColorInfo> colorInfos,
 *                           SkSpan<const SkTileMode> tileModes,
 *                           bool raw);
 *
 *     void setImmutableSamplerInfo(const ImmutableSamplerInfo& samplerInfo);
 *
 * private:
 *     friend class PrecompileYUVImageShader; // for NonAlphaOnlyDefaultColorInfos
 *
 *     // In addition to the tile mode options provided by the client, we can precompile two additional
 *     // sampling/tiling variants: hardware-tiled and cubic sampling (which always uses the most
 *     // generic tiling shader).
 *     inline static constexpr int kExtraNumSamplingTilingCombos = 2;
 *     inline static constexpr int kCubicSampled = 1;
 *     inline static constexpr int kHWTiled      = 0;
 *
 *     // These color info objects are defined assuming an sRGB destination.
 *     // Most specialized color space transform shader, no actual color space handling.
 *     static SkColorInfo DefaultColorInfoPremul() {
 *         return { kRGBA_8888_SkColorType, kPremul_SkAlphaType, SkColorSpace::MakeSRGB() };
 *     }
 *     // sRGB-to-sRGB specialized color space transform shader.
 *     static SkColorInfo DefaultColorInfoSRGB() {
 *         return { kRGBA_8888_SkColorType, kPremul_SkAlphaType,
 *                  sk_srgb_singleton()->makeColorSpin() };
 *     }
 *     // Most general color space transform shader.
 *     static SkColorInfo DefaultColorInfoGeneral() {
 *         return { kRGBA_8888_SkColorType, kPremul_SkAlphaType, SkColorSpace::MakeSRGBLinear() };
 *     }
 *     // Alpha-only, most general color space transform shader.
 *     static SkColorInfo DefaultColorInfoAlphaOnly() {
 *         return { kAlpha_8_SkColorType, kPremul_SkAlphaType, SkColorSpace::MakeSRGBLinear() };
 *     }
 *
 *     // A fixed list of SkColorInfos that will trigger each possible combination of alpha-only
 *     // handling and color space transform variants, when drawn to an sRGB destination.
 *     static std::vector<SkColorInfo> DefaultColorInfos() {
 *         return { DefaultColorInfoPremul(), DefaultColorInfoSRGB(), DefaultColorInfoGeneral(),
 *                  DefaultColorInfoAlphaOnly() };
 *     }
 *     // A fixed list of SkColorInfos that will trigger each color space transform shader variant when
 *     // drawn to an sRGB destination.
 *     static std::vector<SkColorInfo> NonAlphaOnlyDefaultColorInfos() {
 *         return { DefaultColorInfoPremul(), DefaultColorInfoSRGB(), DefaultColorInfoGeneral() };
 *     }
 *     // A fixed list of SkColorInfos that will trigger each color space transform shader variant
 *     // possible from a raw image draw. The general shader is still required if the image is
 *     // alpha-only, because the read swizzle is implemented as a gamut transformation.
 *     static std::vector<SkColorInfo> RawImageDefaultColorInfos() {
 *         return { DefaultColorInfoPremul(), DefaultColorInfoAlphaOnly() };
 *     }
 *
 *     const int fNumExtraSamplingTilingCombos;
 *
 *     const std::vector<SkColorInfo> fColorInfos;
 *     const std::vector<SkTileMode> fTileModes;
 *
 *     // If true, use the destination ColorInfo from the KeyContext provided to addToKey.
 *     // This is true if and only if the client has provided a list of color infos. Otherwise, we
 *     // always use an sRGB destination (as per the default SkColorInfo lists defined above) and
 *     // the source's alphaType.
 *     const bool fUseDstColorInfo;
 *
 *     // Whether this precompiles raw image shaders.
 *     const bool fRaw;
 *     ImmutableSamplerInfo fImmutableSamplerInfo;
 *
 *     int numIntrinsicCombinations() const override;
 *
 *     void addToKey(const KeyContext& keyContext, int desiredCombination) const override;
 * }
 * ```
 */
public class PrecompileImageShader public constructor(
  flags: SkEnumBitMask<ImageShaderFlags>,
  colorInfos: SkSpan<SkColorInfo>,
  tileModes: SkSpan<SkTileMode>,
  raw: Boolean,
) : PrecompileShader() {
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kExtraNumSamplingTilingCombos = 2
   * ```
   */
  private val fNumExtraSamplingTilingCombos: Int = TODO("Initialize fNumExtraSamplingTilingCombos")

  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kCubicSampled = 1
   * ```
   */
  private val fColorInfos: Int = TODO("Initialize fColorInfos")

  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kHWTiled      = 0
   * ```
   */
  private val fTileModes: Int = TODO("Initialize fTileModes")

  /**
   * C++ original:
   * ```cpp
   * const int fNumExtraSamplingTilingCombos
   * ```
   */
  private val fUseDstColorInfo: Boolean = TODO("Initialize fUseDstColorInfo")

  /**
   * C++ original:
   * ```cpp
   * const std::vector<SkColorInfo> fColorInfos
   * ```
   */
  private val fRaw: Boolean = TODO("Initialize fRaw")

  /**
   * C++ original:
   * ```cpp
   * const std::vector<SkTileMode> fTileModes
   * ```
   */
  private var fImmutableSamplerInfo: Int = TODO("Initialize fImmutableSamplerInfo")

  /**
   * C++ original:
   * ```cpp
   * void PrecompileImageShader::setImmutableSamplerInfo(const ImmutableSamplerInfo& samplerInfo) {
   *     fImmutableSamplerInfo = samplerInfo;
   * }
   * ```
   */
  public fun setImmutableSamplerInfo(samplerInfo: ImmutableSamplerInfo) {
    TODO("Implement setImmutableSamplerInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * int PrecompileImageShader::numIntrinsicCombinations() const {
   *     // TODO(b/400682634) If color infos were provided by the client, and we're using the
   *     // destination color space to determine what color space transform shaders to use, we can
   *     // end up generating duplicate shaders, and the actual number of unique shaders generated
   *     // will be less than the number calculated here.
   *     return fColorInfos.size() * (fTileModes.size() + fNumExtraSamplingTilingCombos);
   * }
   * ```
   */
  public override fun numIntrinsicCombinations(): Int {
    TODO("Implement numIntrinsicCombinations")
  }

  /**
   * C++ original:
   * ```cpp
   * void PrecompileImageShader::addToKey(const KeyContext& keyContext, int desiredCombination) const {
   *     SkASSERT(this->numChildCombinations() == 1);
   *     SkASSERT(desiredCombination < this->numIntrinsicCombinations());
   *
   *     const int numSamplingTilingCombos = fTileModes.size() + fNumExtraSamplingTilingCombos;
   *     const int desiredSamplingTilingCombo = desiredCombination % numSamplingTilingCombos;
   *     desiredCombination /= numSamplingTilingCombos;
   *
   *     const int desiredColorInfo = desiredCombination;
   *     SkASSERT(desiredColorInfo < static_cast<int>(fColorInfos.size()));
   *
   *     static constexpr SkSamplingOptions kDefaultCubicSampling(SkCubicResampler::Mitchell());
   *     // This is kLinear to work around b/417429187
   *     static constexpr SkSamplingOptions kDefaultSampling(SkFilterMode::kLinear);
   *
   *     // ImageShaderBlock will use hardware tiling when the subset covers the entire image, so we
   *     // create subset + image size combinations where subset == imgSize (for a shader that uses
   *     // hardware tiling) and subset < imgSize (for a shader that does shader-based tiling).
   *     static constexpr SkRect kSubset = SkRect::MakeWH(1.0f, 1.0f);
   *     static constexpr SkISize kHWTileableSize = SkISize::Make(1, 1);
   *     static constexpr SkISize kShaderTileableSize = SkISize::Make(2, 2);
   *
   *     const int numTileModes = fTileModes.size();
   *     const SkTileMode tileMode = (desiredSamplingTilingCombo < numTileModes)
   *                                         ? fTileModes[desiredSamplingTilingCombo]
   *                                         : SkTileMode::kClamp;
   *     const SkISize imgSize = (desiredSamplingTilingCombo >= numTileModes &&
   *                              desiredSamplingTilingCombo - numTileModes == kHWTiled)
   *                                     ? kHWTileableSize
   *                                     : kShaderTileableSize;
   *     const SkSamplingOptions sampling =
   *             (desiredSamplingTilingCombo >= numTileModes &&
   *              desiredSamplingTilingCombo - numTileModes == kCubicSampled)
   *                     ? kDefaultCubicSampling
   *                     : kDefaultSampling;
   *
   *     const ImageShaderBlock::ImageData imgData(sampling, tileMode, tileMode, imgSize, kSubset,
   *                                               fImmutableSamplerInfo);
   *
   *     const SkColorInfo& colorInfo = fColorInfos[desiredColorInfo];
   *     const bool alphaOnly = SkColorTypeIsAlphaOnly(colorInfo.colorType());
   *
   *     const Caps* caps = keyContext.caps();
   *     Swizzle readSwizzle = caps->getReadSwizzle(
   *             colorInfo.colorType(),
   *             caps->getDefaultSampledTextureInfo(
   *                     colorInfo.colorType(), Mipmapped::kNo, Protected::kNo, Renderable::kNo));
   *     if (alphaOnly) {
   *         readSwizzle = Swizzle::Concat(readSwizzle, Swizzle("000a"));
   *     }
   *
   *     ColorSpaceTransformBlock::ColorSpaceTransformData colorXformData(
   *             SwizzleClassToReadEnum(readSwizzle));
   *
   *     if (!fRaw) {
   *         const SkColorSpace* dstColorSpace = sk_srgb_singleton();
   *         SkAlphaType dstAT = colorInfo.alphaType();
   *         if (fUseDstColorInfo) {
   *             dstColorSpace = keyContext.dstColorInfo().colorSpace();
   *             dstAT = keyContext.dstColorInfo().alphaType();
   *         }
   *         colorXformData.fSteps = SkColorSpaceXformSteps(
   *                 colorInfo.colorSpace(), colorInfo.alphaType(),
   *                 dstColorSpace, dstAT);
   *
   *         if (alphaOnly) {
   *             Blend(keyContext,
   *                   /* addBlendToKey= */ [&] () -> void {
   *                       AddFixedBlendMode(keyContext, SkBlendMode::kDstIn);
   *                   },
   *                   /* addSrcToKey= */ [&] () -> void {
   *                       Compose(keyContext,
   *                               /* addInnerToKey= */ [&]() -> void {
   *                                   ImageShaderBlock::AddBlock(keyContext, imgData);
   *                               },
   *                               /* addOuterToKey= */ [&]() -> void {
   *                                   ColorSpaceTransformBlock::AddBlock(keyContext, colorXformData);
   *                               });
   *                   },
   *                   /* addDstToKey= */ [&]() -> void {
   *                       RGBPaintColorBlock::AddBlock(keyContext);
   *                   });
   *             return;
   *         }
   *     }
   *
   *     Compose(keyContext,
   *             /* addInnerToKey= */ [&]() -> void {
   *                 ImageShaderBlock::AddBlock(keyContext, imgData);
   *             },
   *             /* addOuterToKey= */ [&]() -> void {
   *                 ColorSpaceTransformBlock::AddBlock(keyContext, colorXformData);
   *             });
   * }
   * ```
   */
  public override fun addToKey(keyContext: KeyContext, desiredCombination: Int) {
    TODO("Implement addToKey")
  }

  public companion object {
    private val kExtraNumSamplingTilingCombos: Int =
        TODO("Initialize kExtraNumSamplingTilingCombos")

    private val kCubicSampled: Int = TODO("Initialize kCubicSampled")

    private val kHWTiled: Int = TODO("Initialize kHWTiled")

    /**
     * C++ original:
     * ```cpp
     * static SkColorInfo DefaultColorInfoPremul() {
     *         return { kRGBA_8888_SkColorType, kPremul_SkAlphaType, SkColorSpace::MakeSRGB() };
     *     }
     * ```
     */
    private fun defaultColorInfoPremul(): Int {
      TODO("Implement defaultColorInfoPremul")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkColorInfo DefaultColorInfoSRGB() {
     *         return { kRGBA_8888_SkColorType, kPremul_SkAlphaType,
     *                  sk_srgb_singleton()->makeColorSpin() };
     *     }
     * ```
     */
    private fun defaultColorInfoSRGB(): Int {
      TODO("Implement defaultColorInfoSRGB")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkColorInfo DefaultColorInfoGeneral() {
     *         return { kRGBA_8888_SkColorType, kPremul_SkAlphaType, SkColorSpace::MakeSRGBLinear() };
     *     }
     * ```
     */
    private fun defaultColorInfoGeneral(): Int {
      TODO("Implement defaultColorInfoGeneral")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkColorInfo DefaultColorInfoAlphaOnly() {
     *         return { kAlpha_8_SkColorType, kPremul_SkAlphaType, SkColorSpace::MakeSRGBLinear() };
     *     }
     * ```
     */
    private fun defaultColorInfoAlphaOnly(): Int {
      TODO("Implement defaultColorInfoAlphaOnly")
    }

    /**
     * C++ original:
     * ```cpp
     * static std::vector<SkColorInfo> DefaultColorInfos() {
     *         return { DefaultColorInfoPremul(), DefaultColorInfoSRGB(), DefaultColorInfoGeneral(),
     *                  DefaultColorInfoAlphaOnly() };
     *     }
     * ```
     */
    private fun defaultColorInfos(): Int {
      TODO("Implement defaultColorInfos")
    }

    /**
     * C++ original:
     * ```cpp
     * static std::vector<SkColorInfo> NonAlphaOnlyDefaultColorInfos() {
     *         return { DefaultColorInfoPremul(), DefaultColorInfoSRGB(), DefaultColorInfoGeneral() };
     *     }
     * ```
     */
    private fun nonAlphaOnlyDefaultColorInfos(): Int {
      TODO("Implement nonAlphaOnlyDefaultColorInfos")
    }

    /**
     * C++ original:
     * ```cpp
     * static std::vector<SkColorInfo> RawImageDefaultColorInfos() {
     *         return { DefaultColorInfoPremul(), DefaultColorInfoAlphaOnly() };
     *     }
     * ```
     */
    private fun rawImageDefaultColorInfos(): Int {
      TODO("Implement rawImageDefaultColorInfos")
    }
  }
}
