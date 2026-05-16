package org.skia.modules

import kotlin.CharArray
import kotlin.Int
import org.skia.foundation.SkFontMgr
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class SK_API DataURIResourceProviderProxy final : public ResourceProviderProxyBase {
 * public:
 *     // If font data is supplied via base64 encoding, this needs a provided SkFontMgr to process
 *     // that font data into an SkTypeface. To decode images, clients must call SkCodecs::Register()
 *     // before calling Make.
 *     static sk_sp<DataURIResourceProviderProxy> Make(
 *             sk_sp<ResourceProvider> rp,
 *             ImageDecodeStrategy = ImageDecodeStrategy::kLazyDecode,
 *             sk_sp<const SkFontMgr> fontMgr = nullptr);
 *
 * private:
 *     DataURIResourceProviderProxy(sk_sp<ResourceProvider>,
 *                                  ImageDecodeStrategy,
 *                                  sk_sp<const SkFontMgr> fontMgr);
 *
 *     sk_sp<ImageAsset> loadImageAsset(const char[], const char[], const char[]) const override;
 *     sk_sp<SkTypeface> loadTypeface(const char[], const char[]) const override;
 *
 *     const ImageDecodeStrategy fStrategy;
 *     sk_sp<const SkFontMgr> fFontMgr;
 *
 *     using INHERITED = ResourceProviderProxyBase;
 * }
 * ```
 */
public class DataURIResourceProviderProxy public constructor(
  rp: SkSp<ResourceProvider>,
  strat: ImageDecodeStrategy,
  mgr: SkSp<SkFontMgr>,
) : ResourceProviderProxyBase(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * const ImageDecodeStrategy fStrategy
   * ```
   */
  private val fStrategy: ImageDecodeStrategy = TODO("Initialize fStrategy")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<const SkFontMgr> fFontMgr
   * ```
   */
  private var fFontMgr: Int = TODO("Initialize fFontMgr")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<ImageAsset> DataURIResourceProviderProxy::loadImageAsset(const char rpath[],
   *                                                                const char rname[],
   *                                                                const char rid[]) const {
   *     // First try to decode the data as base64 using codecs registered with SkCodecs::Register()
   *     if (auto data = decode_datauri("data:image/", rname)) {
   *         return MultiFrameImageAsset::Make(std::move(data), fStrategy);
   *     }
   *     // Fallback to the asking the ProviderProxy to load this image for us.
   *     return this->INHERITED::loadImageAsset(rpath, rname, rid);
   * }
   * ```
   */
  public override fun loadImageAsset(
    rpath: CharArray,
    rname: CharArray,
    rid: CharArray,
  ): Int {
    TODO("Implement loadImageAsset")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> DataURIResourceProviderProxy::loadTypeface(const char name[],
   *                                                              const char url[]) const {
   *     if (fFontMgr) {
   *         if (auto data = decode_datauri("data:font/", url)) {
   *             return fFontMgr->makeFromData(std::move(data));
   *         }
   *     }
   *
   *     return this->INHERITED::loadTypeface(name, url);
   * }
   * ```
   */
  public override fun loadTypeface(name: CharArray, url: CharArray): Int {
    TODO("Implement loadTypeface")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<DataURIResourceProviderProxy> DataURIResourceProviderProxy::Make(sk_sp<ResourceProvider> rp,
     *                                                                        ImageDecodeStrategy strat,
     *                                                                        sk_sp<const SkFontMgr> mgr) {
     *     return sk_sp<DataURIResourceProviderProxy>(
     *             new DataURIResourceProviderProxy(std::move(rp), strat, std::move(mgr)));
     * }
     * ```
     */
    public fun make(
      rp: SkSp<ResourceProvider>,
      strat: ImageDecodeStrategy = TODO(),
      fontMgr: SkSp<SkFontMgr> = TODO(),
    ): Int {
      TODO("Implement make")
    }
  }
}
