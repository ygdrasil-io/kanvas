package org.skia.gpu

import kotlin.Any
import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class UniquePaintParamsID {
 * public:
 *     explicit constexpr UniquePaintParamsID(uint32_t id) : fID(id) {}
 *
 *     constexpr UniquePaintParamsID() : fID(SK_InvalidUniqueID) {}
 *
 *     static constexpr UniquePaintParamsID Invalid() { return UniquePaintParamsID(); }
 *
 *     bool operator==(const UniquePaintParamsID &that) const { return fID == that.fID; }
 *     bool operator!=(const UniquePaintParamsID &that) const { return !(*this == that); }
 *
 *     bool isValid() const { return fID != SK_InvalidUniqueID; }
 *     uint32_t asUInt() const { return fID; }
 *
 * private:
 *     uint32_t fID;
 * }
 * ```
 */
public data class UniquePaintParamsID public constructor(
  /**
   * C++ original:
   * ```cpp
   * uint32_t fID
   * ```
   */
  private var fID: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * bool operator==(const UniquePaintParamsID &that) const { return fID == that.fID; }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator!=(const UniquePaintParamsID &that) const { return !(*this == that); }
   * ```
   */
  public fun isValid(): Boolean {
    TODO("Implement isValid")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isValid() const { return fID != SK_InvalidUniqueID; }
   * ```
   */
  public fun asUInt(): Int {
    TODO("Implement asUInt")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static constexpr UniquePaintParamsID Invalid() { return UniquePaintParamsID(); }
     * ```
     */
    public fun invalid(): UniquePaintParamsID {
      TODO("Implement invalid")
    }
  }
}
