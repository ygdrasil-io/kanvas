package org.skia.tests

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct AddressedMessage {
 *     ID fInboxID = 0;
 * }
 * ```
 */
public data class AddressedMessage public constructor(
  /**
   * C++ original:
   * ```cpp
   * ID fInboxID
   * ```
   */
  public var fInboxID: Int,
)
