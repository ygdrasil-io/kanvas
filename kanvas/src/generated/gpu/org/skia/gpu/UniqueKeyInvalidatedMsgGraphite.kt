package org.skia.gpu

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class UniqueKeyInvalidatedMsg_Graphite {
 * public:
 *     UniqueKeyInvalidatedMsg_Graphite() = default;
 *     UniqueKeyInvalidatedMsg_Graphite(const UniqueKey& key, uint32_t recorderID)
 *             : fKey(key), fRecorderID(recorderID) {
 *         SkASSERT(SK_InvalidUniqueID != fRecorderID);
 *     }
 *
 *     UniqueKeyInvalidatedMsg_Graphite(const UniqueKeyInvalidatedMsg_Graphite&) = default;
 *
 *     UniqueKeyInvalidatedMsg_Graphite& operator=(const UniqueKeyInvalidatedMsg_Graphite&) = default;
 *
 *     const UniqueKey& key() const { return fKey; }
 *     uint32_t recorderID() const { return fRecorderID; }
 *
 * private:
 *     UniqueKey fKey;
 *     uint32_t fRecorderID = SK_InvalidUniqueID;
 * }
 * ```
 */
public data class UniqueKeyInvalidatedMsgGraphite public constructor(
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
   * uint32_t fRecorderID
   * ```
   */
  private var fRecorderID: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * UniqueKeyInvalidatedMsg_Graphite& operator=(const UniqueKeyInvalidatedMsg_Graphite&) = default
   * ```
   */
  public fun assign(param0: UniqueKeyInvalidatedMsgGraphite) {
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
   * uint32_t recorderID() const { return fRecorderID; }
   * ```
   */
  public fun recorderID(): Int {
    TODO("Implement recorderID")
  }
}
