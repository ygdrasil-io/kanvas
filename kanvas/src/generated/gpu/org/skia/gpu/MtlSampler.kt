package org.skia.gpu

import kotlin.Int
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode

/**
 * C++ original:
 * ```cpp
 * class MtlSampler : public Sampler {
 * public:
 *     static sk_sp<MtlSampler> Make(const MtlSharedContext*,
 *                                   const SkSamplingOptions& samplingOptions,
 *                                   SkTileMode xTileMode,
 *                                   SkTileMode yTileMode);
 *
 *     ~MtlSampler() override {}
 *
 *     id<MTLSamplerState> mtlSamplerState() const { return fSamplerState.get(); }
 *
 * private:
 *     MtlSampler(const MtlSharedContext* sharedContext,
 *                sk_cfp<id<MTLSamplerState>>);
 *
 *     void freeGpuData() override;
 *
 *     sk_cfp<id<MTLSamplerState>> fSamplerState;
 * }
 * ```
 */
public open class MtlSampler public constructor(
  sharedContext: MtlSharedContext?,
) : Sampler(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * sk_cfp<id<MTLSamplerState>> fSamplerState
   * ```
   */
  private var fSamplerState: Int = TODO("Initialize fSamplerState")

  /**
   * C++ original:
   * ```cpp
   * id<MTLSamplerState> mtlSamplerState() const { return fSamplerState.get(); }
   * ```
   */
  public fun mtlSamplerState(): Int {
    TODO("Implement mtlSamplerState")
  }

  /**
   * C++ original:
   * ```cpp
   * void MtlSampler::freeGpuData() {
   *     fSamplerState.reset();
   * }
   * ```
   */
  public override fun freeGpuData() {
    TODO("Implement freeGpuData")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<MtlSampler> MtlSampler::Make(const MtlSharedContext* sharedContext,
     *                                    const SkSamplingOptions& samplingOptions,
     *                                    SkTileMode xTileMode,
     *                                    SkTileMode yTileMode) {
     *     sk_cfp<MTLSamplerDescriptor*> desc([[MTLSamplerDescriptor alloc] init]);
     *
     *     MTLSamplerMinMagFilter minMagFilter = [&] {
     *         switch (samplingOptions.filter) {
     *             case SkFilterMode::kNearest: return MTLSamplerMinMagFilterNearest;
     *             case SkFilterMode::kLinear:  return MTLSamplerMinMagFilterLinear;
     *         }
     *         SkUNREACHABLE;
     *     }();
     *
     *     MTLSamplerMipFilter mipFilter = [&] {
     *       switch (samplingOptions.mipmap) {
     *           case SkMipmapMode::kNone:    return MTLSamplerMipFilterNotMipmapped;
     *           case SkMipmapMode::kNearest: return MTLSamplerMipFilterNearest;
     *           case SkMipmapMode::kLinear:  return MTLSamplerMipFilterLinear;
     *       }
     *       SkUNREACHABLE;
     *     }();
     *
     *     (*desc).rAddressMode = MTLSamplerAddressModeClampToEdge;
     *     (*desc).sAddressMode = tile_mode_to_mtl_sampler_address(xTileMode, sharedContext->mtlCaps());
     *     (*desc).tAddressMode = tile_mode_to_mtl_sampler_address(yTileMode, sharedContext->mtlCaps());
     *     (*desc).magFilter = minMagFilter;
     *     (*desc).minFilter = minMagFilter;
     *     (*desc).mipFilter = mipFilter;
     *     (*desc).lodMinClamp = 0.0f;
     *     (*desc).lodMaxClamp = FLT_MAX;  // default value according to docs.
     *     (*desc).maxAnisotropy = 1;      // TODO: if we start using aniso, need to add to key
     *     (*desc).normalizedCoordinates = true;
     *     if (@available(macOS 10.11, iOS 9.0, tvOS 9.0, *)) {
     *         (*desc).compareFunction = MTLCompareFunctionNever;
     *     }
     * #ifdef SK_ENABLE_MTL_DEBUG_INFO
     *     NSString* tileModeLabels[] = {
     *         @"Clamp",
     *         @"Repeat",
     *         @"Mirror",
     *         @"Decal"
     *     };
     *     NSString* minMagFilterLabels[] = {
     *         @"Nearest",
     *         @"Linear"
     *     };
     *     NSString* mipFilterLabels[] = {
     *         @"MipNone",
     *         @"MipNearest",
     *         @"MipLinear"
     *     };
     *
     *     (*desc).label = [NSString stringWithFormat:@"X%@Y%@%@%@",
     *                                                tileModeLabels[(int)xTileMode],
     *                                                tileModeLabels[(int)yTileMode],
     *                                                minMagFilterLabels[(int)samplingOptions.filter],
     *                                                mipFilterLabels[(int)samplingOptions.mipmap]];
     * #endif
     *
     *     sk_cfp<id<MTLSamplerState>> sampler(
     *             [sharedContext->device() newSamplerStateWithDescriptor:desc.get()]);
     *     if (!sampler) {
     *         return nullptr;
     *     }
     *     return sk_sp<MtlSampler>(new MtlSampler(sharedContext, std::move(sampler)));
     * }
     * ```
     */
    public fun make(
      sharedContext: MtlSharedContext?,
      samplingOptions: SkSamplingOptions,
      xTileMode: SkTileMode,
      yTileMode: SkTileMode,
    ): Int {
      TODO("Implement make")
    }
  }
}
