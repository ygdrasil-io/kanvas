package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import kotlin.IntArray
import org.skia.core.SkEnumBitMask
import org.skia.foundation.SkColorInfo
import org.skia.foundation.SkSpan

/**
 * C++ original:
 * ```cpp
 * class PrecompileYUVImageShader : public PrecompileShader {
 * public:
 *     PrecompileYUVImageShader(SkEnumBitMask<YUVImageShaderFlags> shaderFlags,
 *                              SkSpan<const SkColorInfo> colorInfos)
 *             : fColorInfos(!colorInfos.empty()
 *                             ? std::vector<SkColorInfo>(colorInfos.begin(), colorInfos.end())
 *                             : PrecompileImageShader::NonAlphaOnlyDefaultColorInfos())
 *             , fUseDstColorSpace(!colorInfos.empty()) {
 *         this->setupTilingModes(shaderFlags);
 *     }
 *
 * private:
 *     // There are 4 possible tiling modes:
 *     //    non-cubic shader tiling
 *     //    HW tiling w/o swizzle
 *     //    HW tiling w/ swizzle
 *     //    cubic shader tiling       -- can be omitted
 *     inline static constexpr int kMaxTilingModes     = 4;
 *
 *     inline static constexpr int kShaderTiled        = 0;
 *     inline static constexpr int kHWTiledNoSwizzle   = 1;
 *     inline static constexpr int kHWTiledWithSwizzle = 2;
 *     inline static constexpr int kCubicShaderTiled   = 3;
 *
 *     void setupTilingModes(SkEnumBitMask<YUVImageShaderFlags> flags) {
 *         fNumTilingModes = 0;
 *
 *         if (flags & YUVImageShaderFlags::kHardwareSamplingNoSwizzle) {
 *             fTilingModes[fNumTilingModes++] = kHWTiledNoSwizzle;
 *         }
 *         if (flags & YUVImageShaderFlags::kHardwareSampling) {
 *             fTilingModes[fNumTilingModes++] = kHWTiledWithSwizzle;
 *         }
 *         if (flags & YUVImageShaderFlags::kShaderBasedSampling) {
 *             fTilingModes[fNumTilingModes++] = kShaderTiled;
 *         }
 *         if (flags & YUVImageShaderFlags::kCubicSampling) {
 *             fTilingModes[fNumTilingModes++] = kCubicShaderTiled;
 *         }
 *
 *         SkASSERT(fNumTilingModes == SkPopCount(flags.value()));
 *         SkASSERT(fNumTilingModes <= kMaxTilingModes);
 *     }
 *
 *     int numIntrinsicCombinations() const override {
 *         return fNumTilingModes * fColorInfos.size();
 *     }
 *
 *     void addToKey(const KeyContext& keyContext, int desiredCombination) const override {
 *         SkASSERT(desiredCombination < this->numIntrinsicCombinations());
 *
 *         int desiredTiling = desiredCombination % fNumTilingModes;
 *         desiredCombination /= fNumTilingModes;
 *
 *         int desiredColorInfo = desiredCombination;
 *         SkASSERT(desiredColorInfo < static_cast<int>(fColorInfos.size()));
 *
 *         static constexpr SkSamplingOptions kDefaultCubicSampling(SkCubicResampler::Mitchell());
 *         static constexpr SkSamplingOptions kDefaultSampling;
 *
 *         YUVImageShaderBlock::ImageData imgData(
 *                 fTilingModes[desiredTiling] == kCubicShaderTiled
 *                                      ? kDefaultCubicSampling
 *                                      : kDefaultSampling,
 *                 SkTileMode::kClamp,
 *                 fTilingModes[desiredTiling] == kShaderTiled ? SkTileMode::kRepeat
 *                                                             : SkTileMode::kClamp,
 *                 /* imgSize= */ { 1, 1 },
 *                 /* subset= */ fTilingModes[desiredTiling] == kShaderTiled
 *                                      ? SkRect::MakeEmpty()
 *                                      : SkRect::MakeWH(1, 1));
 *
 *         static constexpr SkV4 kRedChannel{ 1.f, 0.f, 0.f, 0.f };
 *         imgData.fChannelSelect[0] = kRedChannel;
 *         imgData.fChannelSelect[1] = kRedChannel;
 *         if (fTilingModes[desiredTiling] == kHWTiledNoSwizzle) {
 *             imgData.fChannelSelect[2] = kRedChannel;
 *         } else {
 *             // Having a non-red channel selector forces a swizzle
 *             imgData.fChannelSelect[2] = { 0.f, 1.f, 0.f, 0.f};
 *         }
 *         imgData.fChannelSelect[3] = kRedChannel;
 *
 *         imgData.fYUVtoRGBMatrix.setAll(1, 0, 0, 0, 1, 0, 0, 0, 0);
 *         imgData.fYUVtoRGBTranslate = { 0, 0, 0 };
 *
 *         const SkColorInfo& colorInfo = fColorInfos[desiredColorInfo];
 *
 *         const Caps* caps = keyContext.caps();
 *         Swizzle readSwizzle = caps->getReadSwizzle(
 *                 colorInfo.colorType(),
 *                 caps->getDefaultSampledTextureInfo(
 *                         colorInfo.colorType(), Mipmapped::kNo, Protected::kNo, Renderable::kNo));
 *         ColorSpaceTransformBlock::ColorSpaceTransformData colorXformData(
 *                 SwizzleClassToReadEnum(readSwizzle));
 *
 *         const SkColorSpace* dstColorSpace = fUseDstColorSpace
 *                                             ? keyContext.dstColorInfo().colorSpace()
 *                                             : sk_srgb_singleton();
 *         colorXformData.fSteps = SkColorSpaceXformSteps(
 *                 colorInfo.colorSpace(), colorInfo.alphaType(),
 *                 dstColorSpace, colorInfo.alphaType());
 *
 *         Compose(keyContext,
 *                 /* addInnerToKey= */ [&]() -> void {
 *                     YUVImageShaderBlock::AddBlock(keyContext, imgData);
 *                 },
 *                 /* addOuterToKey= */ [&]() -> void {
 *                     ColorSpaceTransformBlock::AddBlock(keyContext, colorXformData);
 *                 });
 *     }
 *
 *     const std::vector<SkColorInfo> fColorInfos;
 *
 *     // If true, use the destination color space from the KeyContext provided to addToKey.
 *     // This is true if and only if the client has provided a list of color infos. Otherwise, we
 *     // always use an sRGB destination per the default SkColorInfo lists defined in
 *     // PrecompileImageShader::DefaultColorInfos.
 *     const bool fUseDstColorSpace;
 *     int fNumTilingModes;
 *     int fTilingModes[kMaxTilingModes];
 * }
 * ```
 */
public open class PrecompileYUVImageShader public constructor(
  shaderFlags: SkEnumBitMask<YUVImageShaderFlags>,
  colorInfos: SkSpan<SkColorInfo>,
) : PrecompileShader() {
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kMaxTilingModes     = 4
   * ```
   */
  private val fColorInfos: Int = TODO("Initialize fColorInfos")

  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kShaderTiled        = 0
   * ```
   */
  private val fUseDstColorSpace: Boolean = TODO("Initialize fUseDstColorSpace")

  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kHWTiledNoSwizzle   = 1
   * ```
   */
  private var fNumTilingModes: Int = TODO("Initialize fNumTilingModes")

  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kHWTiledWithSwizzle = 2
   * ```
   */
  private var fTilingModes: IntArray = TODO("Initialize fTilingModes")

  /**
   * C++ original:
   * ```cpp
   * void setupTilingModes(SkEnumBitMask<YUVImageShaderFlags> flags) {
   *         fNumTilingModes = 0;
   *
   *         if (flags & YUVImageShaderFlags::kHardwareSamplingNoSwizzle) {
   *             fTilingModes[fNumTilingModes++] = kHWTiledNoSwizzle;
   *         }
   *         if (flags & YUVImageShaderFlags::kHardwareSampling) {
   *             fTilingModes[fNumTilingModes++] = kHWTiledWithSwizzle;
   *         }
   *         if (flags & YUVImageShaderFlags::kShaderBasedSampling) {
   *             fTilingModes[fNumTilingModes++] = kShaderTiled;
   *         }
   *         if (flags & YUVImageShaderFlags::kCubicSampling) {
   *             fTilingModes[fNumTilingModes++] = kCubicShaderTiled;
   *         }
   *
   *         SkASSERT(fNumTilingModes == SkPopCount(flags.value()));
   *         SkASSERT(fNumTilingModes <= kMaxTilingModes);
   *     }
   * ```
   */
  private fun setupTilingModes(flags: SkEnumBitMask<YUVImageShaderFlags>) {
    TODO("Implement setupTilingModes")
  }

  /**
   * C++ original:
   * ```cpp
   * int numIntrinsicCombinations() const override {
   *         return fNumTilingModes * fColorInfos.size();
   *     }
   * ```
   */
  public override fun numIntrinsicCombinations(): Int {
    TODO("Implement numIntrinsicCombinations")
  }

  /**
   * C++ original:
   * ```cpp
   * void addToKey(const KeyContext& keyContext, int desiredCombination) const override {
   *         SkASSERT(desiredCombination < this->numIntrinsicCombinations());
   *
   *         int desiredTiling = desiredCombination % fNumTilingModes;
   *         desiredCombination /= fNumTilingModes;
   *
   *         int desiredColorInfo = desiredCombination;
   *         SkASSERT(desiredColorInfo < static_cast<int>(fColorInfos.size()));
   *
   *         static constexpr SkSamplingOptions kDefaultCubicSampling(SkCubicResampler::Mitchell());
   *         static constexpr SkSamplingOptions kDefaultSampling;
   *
   *         YUVImageShaderBlock::ImageData imgData(
   *                 fTilingModes[desiredTiling] == kCubicShaderTiled
   *                                      ? kDefaultCubicSampling
   *                                      : kDefaultSampling,
   *                 SkTileMode::kClamp,
   *                 fTilingModes[desiredTiling] == kShaderTiled ? SkTileMode::kRepeat
   *                                                             : SkTileMode::kClamp,
   *                 /* imgSize= */ { 1, 1 },
   *                 /* subset= */ fTilingModes[desiredTiling] == kShaderTiled
   *                                      ? SkRect::MakeEmpty()
   *                                      : SkRect::MakeWH(1, 1));
   *
   *         static constexpr SkV4 kRedChannel{ 1.f, 0.f, 0.f, 0.f };
   *         imgData.fChannelSelect[0] = kRedChannel;
   *         imgData.fChannelSelect[1] = kRedChannel;
   *         if (fTilingModes[desiredTiling] == kHWTiledNoSwizzle) {
   *             imgData.fChannelSelect[2] = kRedChannel;
   *         } else {
   *             // Having a non-red channel selector forces a swizzle
   *             imgData.fChannelSelect[2] = { 0.f, 1.f, 0.f, 0.f};
   *         }
   *         imgData.fChannelSelect[3] = kRedChannel;
   *
   *         imgData.fYUVtoRGBMatrix.setAll(1, 0, 0, 0, 1, 0, 0, 0, 0);
   *         imgData.fYUVtoRGBTranslate = { 0, 0, 0 };
   *
   *         const SkColorInfo& colorInfo = fColorInfos[desiredColorInfo];
   *
   *         const Caps* caps = keyContext.caps();
   *         Swizzle readSwizzle = caps->getReadSwizzle(
   *                 colorInfo.colorType(),
   *                 caps->getDefaultSampledTextureInfo(
   *                         colorInfo.colorType(), Mipmapped::kNo, Protected::kNo, Renderable::kNo));
   *         ColorSpaceTransformBlock::ColorSpaceTransformData colorXformData(
   *                 SwizzleClassToReadEnum(readSwizzle));
   *
   *         const SkColorSpace* dstColorSpace = fUseDstColorSpace
   *                                             ? keyContext.dstColorInfo().colorSpace()
   *                                             : sk_srgb_singleton();
   *         colorXformData.fSteps = SkColorSpaceXformSteps(
   *                 colorInfo.colorSpace(), colorInfo.alphaType(),
   *                 dstColorSpace, colorInfo.alphaType());
   *
   *         Compose(keyContext,
   *                 /* addInnerToKey= */ [&]() -> void {
   *                     YUVImageShaderBlock::AddBlock(keyContext, imgData);
   *                 },
   *                 /* addOuterToKey= */ [&]() -> void {
   *                     ColorSpaceTransformBlock::AddBlock(keyContext, colorXformData);
   *                 });
   *     }
   * ```
   */
  public override fun addToKey(keyContext: KeyContext, desiredCombination: Int) {
    TODO("Implement addToKey")
  }

  public companion object {
    private val kMaxTilingModes: Int = TODO("Initialize kMaxTilingModes")

    private val kShaderTiled: Int = TODO("Initialize kShaderTiled")

    private val kHWTiledNoSwizzle: Int = TODO("Initialize kHWTiledNoSwizzle")

    private val kHWTiledWithSwizzle: Int = TODO("Initialize kHWTiledWithSwizzle")

    private val kCubicShaderTiled: Int = TODO("Initialize kCubicShaderTiled")
  }
}
