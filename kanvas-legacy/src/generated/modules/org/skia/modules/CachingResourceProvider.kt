package org.skia.modules

import kotlin.CharArray
import kotlin.Int
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class SK_API CachingResourceProvider final : public ResourceProviderProxyBase {
 * public:
 *     static sk_sp<CachingResourceProvider> Make(sk_sp<ResourceProvider> rp) {
 *         return rp ? sk_sp<CachingResourceProvider>(new CachingResourceProvider(std::move(rp)))
 *                   : nullptr;
 *     }
 *
 * private:
 *     explicit CachingResourceProvider(sk_sp<ResourceProvider>);
 *
 *     sk_sp<ImageAsset> loadImageAsset(const char[], const char[], const char[]) const override;
 *
 *     mutable SkMutex                                             fMutex;
 *     mutable skia_private::THashMap<SkString, sk_sp<ImageAsset>> fImageCache;
 *
 *     using INHERITED = ResourceProviderProxyBase;
 * }
 * ```
 */
public class CachingResourceProvider public constructor(
  rp: SkSp<ResourceProvider>,
) : ResourceProviderProxyBase(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * mutable SkMutex                                             fMutex
   * ```
   */
  private var fMutex: Int = TODO("Initialize fMutex")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<ImageAsset> CachingResourceProvider::loadImageAsset(const char resource_path[],
   *                                                           const char resource_name[],
   *                                                           const char resource_id[]) const {
   *     SkAutoMutexExclusive amx(fMutex);
   *
   *     const SkString key(resource_id);
   *     if (const auto* asset = fImageCache.find(key)) {
   *         return *asset;
   *     }
   *
   *     auto asset = this->INHERITED::loadImageAsset(resource_path, resource_name, resource_id);
   *     fImageCache.set(key, asset);
   *
   *     return asset;
   * }
   * ```
   */
  public override fun loadImageAsset(
    resourcePath: CharArray,
    resourceName: CharArray,
    resourceId: CharArray,
  ): Int {
    TODO("Implement loadImageAsset")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<CachingResourceProvider> Make(sk_sp<ResourceProvider> rp) {
     *         return rp ? sk_sp<CachingResourceProvider>(new CachingResourceProvider(std::move(rp)))
     *                   : nullptr;
     *     }
     * ```
     */
    public fun make(rp: SkSp<ResourceProvider>): Int {
      TODO("Implement make")
    }
  }
}
