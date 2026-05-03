package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import org.skia.core.SkYUVAInfo
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkImage
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan
import undefined.YUVAProxies

/**
 * C++ original:
 * ```cpp
 * class Image_YUVA final : public Image_Base {
 * public:
 *     ~Image_YUVA() override;
 *
 *     // Create an Image_YUVA by interpreting the multiple 'planes' using 'yuvaInfo'. If the info
 *     // or provided plane proxies do not produce a valid mulitplane image, null is returned.
 *     static sk_sp<Image_YUVA> Make(const Caps* caps,
 *                                   const SkYUVAInfo& yuvaInfo,
 *                                   SkSpan<TextureProxyView> planes,
 *                                   sk_sp<SkColorSpace> imageColorSpace);
 *
 *     // Wraps the Graphite-backed Image planes into a YUV[A] image. The returned image shares
 *     // textures as well as any links to Devices that might modify those textures.
 *     static sk_sp<Image_YUVA> WrapImages(const Caps* caps,
 *                                         const SkYUVAInfo& yuvaInfo,
 *                                         SkSpan<const sk_sp<SkImage>> images,
 *                                         sk_sp<SkColorSpace> imageColorSpace);
 *
 *     SkImage_Base::Type type() const override { return SkImage_Base::Type::kGraphiteYUVA; }
 *
 *     size_t textureSize() const override;
 *
 *     bool onHasMipmaps() const override { return fMipmapped == Mipmapped::kYes; }
 *
 *     bool onIsProtected() const override { return fProtected == Protected::kYes; }
 *
 *     sk_sp<SkImage> onReinterpretColorSpace(sk_sp<SkColorSpace>) const override;
 *
 *     // Returns the proxy view that provides value for the YUVA channel specified by 'channelIndex'.
 *     // The view of the returned proxy applies a swizzle to map the relevant data channel into all
 *     // slots of the sample value. The alpha proxy may be null.
 *     const TextureProxyView& proxyView(int channelIndex) const {
 *         SkASSERT(channelIndex >= 0 && channelIndex < SkYUVAInfo::kYUVAChannelCount);
 *         return fProxies[channelIndex];
 *     }
 *
 *     std::tuple<int, int> uvSubsampleFactors() const { return fUVSubsampleFactors; }
 *
 *     const SkYUVAInfo& yuvaInfo() const { return fYUVAInfo; }
 *
 * private:
 *     // The proxy views are ordered Y,U,V,A and if the channels are held in the same plane, the
 *     // respective proxy views will share the underlying TextureProxy but have the appropriate
 *     // swizzle to access the appropriate channel and return it in the R slot.
 *     using YUVAProxies = std::array<TextureProxyView, SkYUVAInfo::kYUVAChannelCount>;
 *
 *     Image_YUVA(const YUVAProxies&,
 *                const SkYUVAInfo&,
 *                sk_sp<SkColorSpace>);
 *
 *     YUVAProxies fProxies;
 *     SkYUVAInfo fYUVAInfo;
 *     std::tuple<int, int> fUVSubsampleFactors;
 *
 *     // Aggregate mipmap/protected status from the proxies
 *     Mipmapped fMipmapped = Mipmapped::kYes;
 *     Protected fProtected = Protected::kNo;
 * }
 * ```
 */
public class ImageYUVA public constructor(
  proxies: YUVAProxies,
  yuvaInfo: SkYUVAInfo,
  imageColorSpace: SkSp<SkColorSpace>,
) : ImageBase(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * YUVAProxies fProxies
   * ```
   */
  private var fProxies: Int = TODO("Initialize fProxies")

  /**
   * C++ original:
   * ```cpp
   * SkYUVAInfo fYUVAInfo
   * ```
   */
  private var fYUVAInfo: Int = TODO("Initialize fYUVAInfo")

  /**
   * C++ original:
   * ```cpp
   * std::tuple<int, int> fUVSubsampleFactors
   * ```
   */
  private var fUVSubsampleFactors: Int = TODO("Initialize fUVSubsampleFactors")

  /**
   * C++ original:
   * ```cpp
   * Mipmapped fMipmapped
   * ```
   */
  private var fMipmapped: Int = TODO("Initialize fMipmapped")

  /**
   * C++ original:
   * ```cpp
   * Protected fProtected
   * ```
   */
  private var fProtected: Int = TODO("Initialize fProtected")

  /**
   * C++ original:
   * ```cpp
   * SkImage_Base::Type type() const override { return SkImage_Base::Type::kGraphiteYUVA; }
   * ```
   */
  public override fun type(): Int {
    TODO("Implement type")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t Image_YUVA::textureSize() const {
   *     // We could look at the plane config and plane count to determine how many different textures
   *     // to expect, but it's theoretically possible for an Image_YUVA to be constructed where the
   *     // same TextureProxy is aliased to both the U and the V planes (and similarly for the Y and A)
   *     // even when the plane config specifies that those channels are not packed into the same texture
   *     //
   *     // Given that it's simpler to just sum the total gpu memory of non-duplicate textures.
   *     size_t size = 0;
   *     for (int i = 0; i < SkYUVAInfo::kYUVAChannelCount; ++i) {
   *         if (!fProxies[i]) {
   *             continue; // Null channels (A) have no size.
   *         }
   *         bool repeat = false;
   *         for (int j = i - 1; j >= 0; --j) {
   *             if (fProxies[i].proxy() == fProxies[j].proxy()) {
   *                 repeat = true;
   *                 break;
   *             }
   *         }
   *         if (!repeat) {
   *             if (fProxies[i].proxy()->isInstantiated()) {
   *                 size += fProxies[i].proxy()->texture()->gpuMemorySize();
   *             } else {
   *                 size += fProxies[i].proxy()->uninstantiatedGpuMemorySize();
   *             }
   *         }
   *     }
   *
   *     return size;
   * }
   * ```
   */
  public override fun textureSize(): Int {
    TODO("Implement textureSize")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onHasMipmaps() const override { return fMipmapped == Mipmapped::kYes; }
   * ```
   */
  public override fun onHasMipmaps(): Boolean {
    TODO("Implement onHasMipmaps")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onIsProtected() const override { return fProtected == Protected::kYes; }
   * ```
   */
  public override fun onIsProtected(): Boolean {
    TODO("Implement onIsProtected")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> Image_YUVA::onReinterpretColorSpace(sk_sp<SkColorSpace> newCS) const {
   *     sk_sp<Image_YUVA> view{new Image_YUVA(fProxies,
   *                                           fYUVAInfo,
   *                                           std::move(newCS))};
   *     // The new Image object shares the same texture planes, so it should also share linked Devices
   *     view->linkDevices(this);
   *     return view;
   * }
   * ```
   */
  public override fun onReinterpretColorSpace(newCS: SkSp<SkColorSpace>): Int {
    TODO("Implement onReinterpretColorSpace")
  }

  /**
   * C++ original:
   * ```cpp
   * const TextureProxyView& proxyView(int channelIndex) const {
   *         SkASSERT(channelIndex >= 0 && channelIndex < SkYUVAInfo::kYUVAChannelCount);
   *         return fProxies[channelIndex];
   *     }
   * ```
   */
  public fun proxyView(channelIndex: Int): Int {
    TODO("Implement proxyView")
  }

  /**
   * C++ original:
   * ```cpp
   * std::tuple<int, int> uvSubsampleFactors() const { return fUVSubsampleFactors; }
   * ```
   */
  public fun uvSubsampleFactors(): Int {
    TODO("Implement uvSubsampleFactors")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkYUVAInfo& yuvaInfo() const { return fYUVAInfo; }
   * ```
   */
  public fun yuvaInfo(): Int {
    TODO("Implement yuvaInfo")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<Image_YUVA> Image_YUVA::Make(const Caps* caps,
     *                                    const SkYUVAInfo& yuvaInfo,
     *                                    SkSpan<TextureProxyView> planes,
     *                                    sk_sp<SkColorSpace> imageColorSpace) {
     *     if (!yuvaInfo.isValid()) {
     *         return nullptr;
     *     }
     *     SkImageInfo info = SkImageInfo::Make(
     *             yuvaInfo.dimensions(), kAssumedColorType, yuva_alpha_type(yuvaInfo), imageColorSpace);
     *     if (!SkImageInfoIsValid(info)) {
     *         return nullptr;
     *     }
     *
     *     // Invoke the PlaneProxyFactoryFn for each plane and validate it against the plane config
     *     const int numPlanes = yuvaInfo.numPlanes();
     *     SkISize planeDimensions[SkYUVAInfo::kMaxPlanes];
     *     if (numPlanes != yuvaInfo.planeDimensions(planeDimensions)) {
     *         return nullptr;
     *     }
     *     uint32_t pixmapChannelmasks[SkYUVAInfo::kMaxPlanes];
     *     for (int i = 0; i < numPlanes; ++i) {
     *         if (!planes[i] || !caps->isTexturable(planes[i].proxy()->textureInfo())) {
     *             return nullptr;
     *         }
     *         if (planes[i].dimensions() != planeDimensions[i]) {
     *             return nullptr;
     *         }
     *         pixmapChannelmasks[i] = TextureInfoPriv::ChannelMask(planes[i].proxy()->textureInfo());
     *     }
     *
     *     // Re-arrange the proxies from planes to channels
     *     SkYUVAInfo::YUVALocations locations = yuvaInfo.toYUVALocations(pixmapChannelmasks);
     *     int expectedPlanes;
     *     if (!SkYUVAInfo::YUVALocation::AreValidLocations(locations, &expectedPlanes) ||
     *         expectedPlanes != numPlanes) {
     *         return nullptr;
     *     }
     *     // Y channel should match the YUVAInfo dimensions
     *     if (planes[locations[kY].fPlane].dimensions() != yuvaInfo.dimensions()) {
     *         return nullptr;
     *     }
     *     // UV channels should have planes with the same dimensions and subsampling factor.
     *     if (planes[locations[kU].fPlane].dimensions() != planes[locations[kV].fPlane].dimensions()) {
     *         return nullptr;
     *     }
     *     // If A channel is present, it should match the Y channel
     *     if (locations[kA].fPlane >= 0 &&
     *         planes[locations[kA].fPlane].dimensions() != yuvaInfo.dimensions()) {
     *         return nullptr;
     *     }
     *
     *     if (yuvaInfo.planeSubsamplingFactors(locations[kU].fPlane) !=
     *         yuvaInfo.planeSubsamplingFactors(locations[kV].fPlane)) {
     *         return nullptr;
     *     }
     *
     *     // Re-arrange into YUVA channel order and apply the location to the swizzle
     *     YUVAProxies channelProxies;
     *     for (int i = 0; i < SkYUVAInfo::kYUVAChannelCount; ++i) {
     *         auto [plane, channel] = locations[i];
     *         if (plane >= 0) {
     *             // Compose the YUVA location with the data swizzle. replaceSwizzle() is used since
     *             // selectChannelInR() effectively does the composition (vs. Swizzle::Concat).
     *             Swizzle channelSwizzle = planes[plane].swizzle().selectChannelInR((int) channel);
     *             channelProxies[i] = planes[plane].replaceSwizzle(channelSwizzle);
     *         } else if (i == kA) {
     *             // The alpha channel is allowed to be not provided, set it to an empty view
     *             channelProxies[i] = {};
     *         } else {
     *             SKGPU_LOG_W("YUVA channel %d does not have a valid location", i);
     *             return nullptr;
     *         }
     *     }
     *
     *     return sk_sp<Image_YUVA>(new Image_YUVA(std::move(channelProxies),
     *                                             yuvaInfo,
     *                                             std::move(imageColorSpace)));
     * }
     * ```
     */
    public fun make(
      caps: Caps?,
      yuvaInfo: SkYUVAInfo,
      planes: SkSpan<TextureProxyView>,
      imageColorSpace: SkSp<SkColorSpace>,
    ): Int {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<Image_YUVA> Image_YUVA::WrapImages(const Caps* caps,
     *                                          const SkYUVAInfo& yuvaInfo,
     *                                          SkSpan<const sk_sp<SkImage>> images,
     *                                          sk_sp<SkColorSpace> imageColorSpace) {
     *     if (SkTo<int>(images.size()) < yuvaInfo.numPlanes()) {
     *         return nullptr;
     *     }
     *
     *     TextureProxyView planes[SkYUVAInfo::kMaxPlanes];
     *     for (int i = 0; i < yuvaInfo.numPlanes(); ++i) {
     *         planes[i] = AsView(images[i]);
     *         if (!planes[i]) {
     *             // A null image, or not graphite-backed, or not backed by a single texture.
     *             return nullptr;
     *         }
     *         // The YUVA shader expects to sample from the red channel for single-channel textures, so
     *         // reset the swizzle for alpha-only textures to compensate for that
     *         if (images[i]->isAlphaOnly()) {
     *             planes[i] = planes[i].makeSwizzle(Swizzle("aaaa"));
     *         }
     *     }
     *
     *     sk_sp<Image_YUVA> image = Make(caps, yuvaInfo, SkSpan(planes), std::move(imageColorSpace));
     *     if (image) {
     *         // Unlike the other factories, this YUVA image shares the texture proxies with each plane
     *         // Image, so if those are linked to Devices, it must inherit those same links.
     *         for (int plane = 0; plane < yuvaInfo.numPlanes(); ++plane) {
     *             SkASSERT(as_IB(images[plane])->isGraphiteBacked());
     *             image->linkDevices(static_cast<Image_Base*>(images[plane].get()));
     *         }
     *     }
     *     return image;
     * }
     * ```
     */
    public fun wrapImages(
      caps: Caps?,
      yuvaInfo: SkYUVAInfo,
      images: SkSpan<SkSp<SkImage>>,
      imageColorSpace: SkSp<SkColorSpace>,
    ): Int {
      TODO("Implement wrapImages")
    }
  }
}
