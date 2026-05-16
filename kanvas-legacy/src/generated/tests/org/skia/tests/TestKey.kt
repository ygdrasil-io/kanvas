package org.skia.tests

import kotlin.Int
import org.skia.core.SkResourceCache

/**
 * C++ original:
 * ```cpp
 * struct TestKey : SkResourceCache::Key {
 *     int32_t fData;
 *
 *     TestKey(int sharedID, int32_t data) : fData(data) {
 *         this->init(&gTestNamespace, sharedID, sizeof(fData));
 *     }
 * }
 * ```
 */
public open class TestKey public constructor(
  /**
   * C++ original:
   * ```cpp
   * int32_t fData
   * ```
   */
  public var fData: Int,
) : SkResourceCache.Key(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * TestKey(int sharedID, int32_t data) : fData(data) {
   *         this->init(&gTestNamespace, sharedID, sizeof(fData));
   *     }
   * ```
   */
  public constructor(sharedID: Int, `data`: Int) : this() {
    TODO("Implement constructor")
  }
}
