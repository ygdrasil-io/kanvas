package org.skia.core

import kotlin.Int
import kotlin.UInt

/**
 * C++ original:
 * ```cpp
 * struct YUVPlanesKey : public SkResourceCache::Key {
 *     YUVPlanesKey(uint32_t genID)
 *         : fGenID(genID)
 *     {
 *         this->init(&gYUVPlanesKeyNamespaceLabel, SkMakeResourceCacheSharedIDForBitmap(genID),
 *                    sizeof(genID));
 *     }
 *
 *     uint32_t fGenID;
 * }
 * ```
 */
public open class YUVPlanesKey public constructor(
  /**
   * C++ original:
   * ```cpp
   * uint32_t fGenID
   * ```
   */
  public var fGenID: Int,
) : SkResourceCache.Key(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * YUVPlanesKey(uint32_t genID)
   *         : fGenID(genID)
   *     {
   *         this->init(&gYUVPlanesKeyNamespaceLabel, SkMakeResourceCacheSharedIDForBitmap(genID),
   *                    sizeof(genID));
   *     }
   * ```
   */
  public constructor(genID: UInt) : this() {
    TODO("Implement constructor")
  }
}
