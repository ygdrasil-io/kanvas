package org.skia.modules

import kotlin.Int
import org.skia.foundation.SkTypefaceID
import undefined.HBFont
import undefined.SkMutex

/**
 * C++ original:
 * ```cpp
 * class HBLockedFaceCache {
 * public:
 *     HBLockedFaceCache(SkLRUCache<SkTypefaceID, HBFont>& lruCache, SkMutex& mutex)
 *         : fLRUCache(lruCache), fMutex(mutex)
 *     {
 *         fMutex.acquire();
 *     }
 *     HBLockedFaceCache(const HBLockedFaceCache&) = delete;
 *     HBLockedFaceCache& operator=(const HBLockedFaceCache&) = delete;
 *     HBLockedFaceCache& operator=(HBLockedFaceCache&&) = delete;
 *
 *     ~HBLockedFaceCache() {
 *         fMutex.release();
 *     }
 *
 *     HBFont* find(SkTypefaceID fontId) {
 *         return fLRUCache.find(fontId);
 *     }
 *     HBFont* insert(SkTypefaceID fontId, HBFont hbFont) {
 *         return fLRUCache.insert(fontId, std::move(hbFont));
 *     }
 *     void reset() {
 *         fLRUCache.reset();
 *     }
 * private:
 *     SkLRUCache<SkTypefaceID, HBFont>& fLRUCache;
 *     SkMutex& fMutex;
 * }
 * ```
 */
public data class HBLockedFaceCache public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkLRUCache<SkTypefaceID, HBFont>& fLRUCache
   * ```
   */
  private var fLRUCache: Int,
  /**
   * C++ original:
   * ```cpp
   * SkMutex& fMutex
   * ```
   */
  private var fMutex: SkMutex,
) {
  /**
   * C++ original:
   * ```cpp
   * HBLockedFaceCache& operator=(const HBLockedFaceCache&) = delete
   * ```
   */
  public fun assign(param0: HBLockedFaceCache) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * HBLockedFaceCache& operator=(HBLockedFaceCache&&) = delete
   * ```
   */
  public fun find(fontId: SkTypefaceID): Int {
    TODO("Implement find")
  }

  /**
   * C++ original:
   * ```cpp
   * HBFont* find(SkTypefaceID fontId) {
   *         return fLRUCache.find(fontId);
   *     }
   * ```
   */
  public fun insert(fontId: SkTypefaceID, hbFont: HBFont): Int {
    TODO("Implement insert")
  }

  /**
   * C++ original:
   * ```cpp
   * HBFont* insert(SkTypefaceID fontId, HBFont hbFont) {
   *         return fLRUCache.insert(fontId, std::move(hbFont));
   *     }
   * ```
   */
  public fun reset() {
    TODO("Implement reset")
  }
}
