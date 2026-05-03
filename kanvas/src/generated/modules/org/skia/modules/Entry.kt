package org.skia.modules

import kotlin.Int
import org.skia.core.Entry

/**
 * C++ original:
 * ```cpp
 * struct ParagraphCache::Entry {
 *
 *     Entry(ParagraphCacheValue* value) : fValue(value) {}
 *     std::unique_ptr<ParagraphCacheValue> fValue;
 * }
 * ```
 */
public open class Entry public constructor(
  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<ParagraphCacheValue> fValue
   * ```
   */
  public var fValue: Int,
) : Entry(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * Entry(ParagraphCacheValue* value) : fValue(value) {}
   * ```
   */
  public constructor(`value`: ParagraphCacheValue?) : this() {
    TODO("Implement constructor")
  }
}
