package org.skia.core

import kotlin.UInt

/**
 * C++ original:
 * ```cpp
 * class SkYUVPlanesCache {
 * public:
 *     /**
 *      * On success, return a ref to the SkCachedData that holds the pixel data. The SkYUVAPixmaps
 *      * contains a description of the YUVA data and has a SkPixmap for each plane that points
 *      * into the SkCachedData.
 *      *
 *      * On failure, return nullptr.
 *      */
 *     static SkCachedData* FindAndRef(uint32_t genID,
 *                                     SkYUVAPixmaps* pixmaps,
 *                                     SkResourceCache* localCache = nullptr);
 *
 *     /**
 *      * Add a pixelRef ID and its YUV planes data to the cache. The SkYUVAPixmaps should contain
 *      * SkPixmaps that store their pixel data in the SkCachedData.
 *      */
 *     static void Add(uint32_t genID, SkCachedData* data, const SkYUVAPixmaps& pixmaps,
 *                     SkResourceCache* localCache = nullptr);
 * }
 * ```
 */
public open class SkYUVPlanesCache {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * SkCachedData* SkYUVPlanesCache::FindAndRef(uint32_t genID,
     *                                            SkYUVAPixmaps* pixmaps,
     *                                            SkResourceCache* localCache) {
     *     YUVValue result;
     *     YUVPlanesKey key(genID);
     *     if (!CHECK_LOCAL(localCache, find, Find, key, YUVPlanesRec::Visitor, &result)) {
     *         return nullptr;
     *     }
     *
     *     *pixmaps = result.fPixmaps;
     *     return result.fData;
     * }
     * ```
     */
    public fun findAndRef(
      genID: UInt,
      pixmaps: SkYUVAPixmaps?,
      localCache: SkResourceCache? = TODO(),
    ): SkCachedData {
      TODO("Implement findAndRef")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkYUVPlanesCache::Add(uint32_t genID, SkCachedData* data, const SkYUVAPixmaps& pixmaps,
     *                            SkResourceCache* localCache) {
     *     YUVPlanesKey key(genID);
     *     return CHECK_LOCAL(localCache, add, Add, new YUVPlanesRec(key, data, pixmaps));
     * }
     * ```
     */
    public fun add(
      genID: UInt,
      `data`: SkCachedData?,
      pixmaps: SkYUVAPixmaps,
      localCache: SkResourceCache? = TODO(),
    ) {
      TODO("Implement add")
    }
  }
}
