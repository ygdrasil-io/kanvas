package org.skia.core

import org.skia.foundation.SkMask
import org.skia.foundation.SkRRect
import org.skia.foundation.SkSpan
import org.skia.math.SkRect
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * template <typename T> class SkTLazy;
 *
 * class SkMaskCache {
 * public:
 *     /**
 *      * On success, return a ref to the SkCachedData that holds the pixels, and have mask
 *      * already point to that memory.
 *      *
 *      * On failure, return nullptr.
 *      */
 *     static SkCachedData* FindAndRef(SkScalar sigma, SkBlurStyle style,
 *                                     const SkRRect& rrect, SkTLazy<SkMask>* mask,
 *                                     SkResourceCache* localCache = nullptr);
 *     static SkCachedData* FindAndRef(SkScalar sigma,
 *                                     SkBlurStyle style,
 *                                     SkSpan<const SkRect> rects,
 *                                     SkTLazy<SkMask>* mask,
 *                                     SkResourceCache* localCache = nullptr);
 *
 *     /**
 *      * Add a mask and its pixel-data to the cache.
 *      */
 *     static void Add(SkScalar sigma, SkBlurStyle style,
 *                     const SkRRect& rrect, const SkMask& mask, SkCachedData* data,
 *                     SkResourceCache* localCache = nullptr);
 *     static void Add(SkScalar sigma,
 *                     SkBlurStyle style,
 *                     SkSpan<const SkRect> rects,
 *                     const SkMask& mask,
 *                     SkCachedData* data,
 *                     SkResourceCache* localCache = nullptr);
 * }
 * ```
 */
public open class SkMaskCache {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * SkCachedData* SkMaskCache::FindAndRef(SkScalar sigma, SkBlurStyle style,
     *                                       const SkRRect& rrect, SkTLazy<SkMask>* mask,
     *                                       SkResourceCache* localCache) {
     *     SkTLazy<MaskValue> result;
     *     RRectBlurKey key(sigma, rrect, style);
     *     if (!CHECK_LOCAL(localCache, find, Find, key, RRectBlurRec::Visitor, &result)) {
     *         return nullptr;
     *     }
     *
     *     mask->init(static_cast<const uint8_t*>(result->fData->data()),
     *                result->fMask.fBounds, result->fMask.fRowBytes, result->fMask.fFormat);
     *     return result->fData;
     * }
     * ```
     */
    public fun findAndRef(
      sigma: SkScalar,
      style: SkBlurStyle,
      rrect: SkRRect,
      mask: SkTLazy<SkMask>?,
      localCache: SkResourceCache? = TODO(),
    ): SkCachedData {
      TODO("Implement findAndRef")
    }

    /**
     * C++ original:
     * ```cpp
     * SkCachedData* SkMaskCache::FindAndRef(SkScalar sigma,
     *                                       SkBlurStyle style,
     *                                       SkSpan<const SkRect> rects,
     *                                       SkTLazy<SkMask>* mask,
     *                                       SkResourceCache* localCache) {
     *     SkTLazy<MaskValue> result;
     *     RectsBlurKey key(sigma, style, rects);
     *     if (!CHECK_LOCAL(localCache, find, Find, key, RectsBlurRec::Visitor, &result)) {
     *         return nullptr;
     *     }
     *
     *     mask->init(static_cast<const uint8_t*>(result->fData->data()),
     *                result->fMask.fBounds, result->fMask.fRowBytes, result->fMask.fFormat);
     *     return result->fData;
     * }
     * ```
     */
    public fun findAndRef(
      sigma: SkScalar,
      style: SkBlurStyle,
      rects: SkSpan<SkRect>,
      mask: SkTLazy<SkMask>?,
      localCache: SkResourceCache? = TODO(),
    ): SkCachedData {
      TODO("Implement findAndRef")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkMaskCache::Add(SkScalar sigma, SkBlurStyle style,
     *                       const SkRRect& rrect, const SkMask& mask, SkCachedData* data,
     *                       SkResourceCache* localCache) {
     *     RRectBlurKey key(sigma, rrect, style);
     *     return CHECK_LOCAL(localCache, add, Add, new RRectBlurRec(key, mask, data));
     * }
     * ```
     */
    public fun add(
      sigma: SkScalar,
      style: SkBlurStyle,
      rrect: SkRRect,
      mask: SkMask,
      `data`: SkCachedData?,
      localCache: SkResourceCache? = TODO(),
    ) {
      TODO("Implement add")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkMaskCache::Add(SkScalar sigma,
     *                       SkBlurStyle style,
     *                       SkSpan<const SkRect> rects,
     *                       const SkMask& mask,
     *                       SkCachedData* data,
     *                       SkResourceCache* localCache) {
     *     RectsBlurKey key(sigma, style, rects);
     *     return CHECK_LOCAL(localCache, add, Add, new RectsBlurRec(key, mask, data));
     * }
     * ```
     */
    public fun add(
      sigma: SkScalar,
      style: SkBlurStyle,
      rects: SkSpan<SkRect>,
      mask: SkMask,
      `data`: SkCachedData?,
      localCache: SkResourceCache? = TODO(),
    ) {
      TODO("Implement add")
    }
  }
}
