package org.skia.core

import org.skia.foundation.SkMipmap

/**
 * C++ original:
 * ```cpp
 * class SkMipmapCache {
 * public:
 *     static const SkMipmap* FindAndRef(const SkBitmapCacheDesc&,
 *                                       SkResourceCache* localCache = nullptr);
 *     static const SkMipmap* AddAndRef(const SkImage_Base*,
 *                                      SkResourceCache* localCache = nullptr);
 * }
 * ```
 */
public open class SkMipmapCache {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * const SkMipmap* SkMipmapCache::FindAndRef(const SkBitmapCacheDesc& desc,
     *                                           SkResourceCache* localCache) {
     *     MipMapKey key(desc);
     *     const SkMipmap* result;
     *
     *     if (!CHECK_LOCAL(localCache, find, Find, key, MipMapRec::Finder, &result)) {
     *         result = nullptr;
     *     }
     *     return result;
     * }
     * ```
     */
    public fun findAndRef(desc: SkBitmapCacheDesc, localCache: SkResourceCache? = TODO()): SkMipmap {
      TODO("Implement findAndRef")
    }

    /**
     * C++ original:
     * ```cpp
     * const SkMipmap* SkMipmapCache::AddAndRef(const SkImage_Base* image, SkResourceCache* localCache) {
     *     SkBitmap src;
     *     if (!image->getROPixels(nullptr, &src)) {
     *         return nullptr;
     *     }
     *
     *     SkMipmap* mipmap = SkMipmap::Build(src, get_fact(localCache));
     *     if (mipmap) {
     *         MipMapRec* rec = new MipMapRec(SkBitmapCacheDesc::Make(image), mipmap);
     *         CHECK_LOCAL(localCache, add, Add, rec);
     *         image->notifyAddedToRasterCache();
     *     }
     *     return mipmap;
     * }
     * ```
     */
    public fun addAndRef(image: SkImageBase?, localCache: SkResourceCache? = TODO()): SkMipmap {
      TODO("Implement addAndRef")
    }
  }
}
