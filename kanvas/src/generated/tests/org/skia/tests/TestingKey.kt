package org.skia.tests

import kotlin.Int
import kotlin.Long
import kotlin.ULong
import org.skia.core.SkResourceCache

/**
 * C++ original:
 * ```cpp
 * struct TestingKey : public SkResourceCache::Key {
 *     intptr_t    fValue;
 *
 *     TestingKey(intptr_t value, uint64_t sharedID = 0) : fValue(value) {
 *         this->init(&gGlobalAddress, sharedID, sizeof(fValue));
 *     }
 * }
 * ```
 */
public open class TestingKey public constructor(
  /**
   * C++ original:
   * ```cpp
   * intptr_t    fValue
   * ```
   */
  public var fValue: Int,
) : SkResourceCache.Key(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * TestingKey(intptr_t value, uint64_t sharedID = 0) : fValue(value) {
   *         this->init(&gGlobalAddress, sharedID, sizeof(fValue));
   *     }
   * ```
   */
  public constructor(`value`: Long, sharedID: ULong = TODO()) : this() {
    TODO("Implement constructor")
  }
}
