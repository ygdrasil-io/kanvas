package org.skia.gpu

import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class UniqueKeyInvalidatedMessage {
 * public:
 *     UniqueKeyInvalidatedMessage() = default;
 *     UniqueKeyInvalidatedMessage(const UniqueKey& key,
 *                                 uint32_t contextUniqueID,
 *                                 bool inThreadSafeCache = false)
 *             : fKey(key), fContextID(contextUniqueID), fInThreadSafeCache(inThreadSafeCache) {
 *         SkASSERT(SK_InvalidUniqueID != contextUniqueID);
 *     }
 *
 *     UniqueKeyInvalidatedMessage(const UniqueKeyInvalidatedMessage&) = default;
 *
 *     UniqueKeyInvalidatedMessage& operator=(const UniqueKeyInvalidatedMessage&) = default;
 *
 *     const UniqueKey& key() const { return fKey; }
 *     uint32_t contextID() const { return fContextID; }
 *     bool inThreadSafeCache() const { return fInThreadSafeCache; }
 *
 * private:
 *     UniqueKey fKey;
 *     uint32_t fContextID = SK_InvalidUniqueID;
 *     bool fInThreadSafeCache = false;
 * }
 * ```
 */
public data class UniqueKeyInvalidatedMessage public constructor(
  /**
   * C++ original:
   * ```cpp
   * UniqueKey fKey
   * ```
   */
  private var fKey: UniqueKey,
  /**
   * C++ original:
   * ```cpp
   * uint32_t fContextID
   * ```
   */
  private var fContextID: Int,
  /**
   * C++ original:
   * ```cpp
   * bool fInThreadSafeCache = false
   * ```
   */
  private var fInThreadSafeCache: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * UniqueKeyInvalidatedMessage& operator=(const UniqueKeyInvalidatedMessage&) = default
   * ```
   */
  public fun assign(param0: UniqueKeyInvalidatedMessage) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * const UniqueKey& key() const { return fKey; }
   * ```
   */
  public fun key(): UniqueKey {
    TODO("Implement key")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t contextID() const { return fContextID; }
   * ```
   */
  public fun contextID(): Int {
    TODO("Implement contextID")
  }

  /**
   * C++ original:
   * ```cpp
   * bool inThreadSafeCache() const { return fInThreadSafeCache; }
   * ```
   */
  public fun inThreadSafeCache(): Boolean {
    TODO("Implement inThreadSafeCache")
  }
}
