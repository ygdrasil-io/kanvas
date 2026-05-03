package org.skia.tests

import kotlin.Any
import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class ListElement {
 * public:
 *     ListElement(int id) : fID(id) {
 *     }
 *     bool operator== (const ListElement& other) { return fID == other.fID; }
 *
 *     int fID;
 *
 * private:
 *
 *     SK_DECLARE_INTERNAL_LLIST_INTERFACE(ListElement);
 * }
 * ```
 */
public data class ListElement public constructor(
  /**
   * C++ original:
   * ```cpp
   * int fID
   * ```
   */
  public var fID: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * bool operator== (const ListElement& other) { return fID == other.fID; }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }
}
