package org.skia.core

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct HashTraits {
 *         static uint32_t Hash(const SkResourceCache::Key& key) { return key.hash(); }
 *         static const SkResourceCache::Key& GetKey(const SkResourceCache::Rec* rec) {
 *             return rec->getKey();
 *         }
 *     }
 * ```
 */
public open class HashTraits {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static uint32_t Hash(const SkResourceCache::Key& key) { return key.hash(); }
     * ```
     */
    public fun hash(key: SkResourceCache.Key): Int {
      TODO("Implement hash")
    }

    /**
     * C++ original:
     * ```cpp
     * static const SkResourceCache::Key& GetKey(const SkResourceCache::Rec* rec) {
     *             return rec->getKey();
     *         }
     * ```
     */
    public fun getKey(rec: SkResourceCache.Rec?): SkResourceCache.Key {
      TODO("Implement getKey")
    }
  }
}
