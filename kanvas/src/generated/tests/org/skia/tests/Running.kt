package org.skia.tests

import kotlin.String
import org.skia.core.SkThreadID

/**
 * C++ original:
 * ```cpp
 * struct Running {
 *     SkString   id;
 *     SkThreadID thread;
 *
 *     void dump() const {
 *         info("\t%s\n", id.c_str());
 *     }
 * }
 * ```
 */
public data class Running public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkString   id
   * ```
   */
  public var id: String,
  /**
   * C++ original:
   * ```cpp
   * SkThreadID thread
   * ```
   */
  public var thread: SkThreadID,
) {
  /**
   * C++ original:
   * ```cpp
   * void dump() const {
   *         info("\t%s\n", id.c_str());
   *     }
   * ```
   */
  public fun dump() {
    TODO("Implement dump")
  }
}
