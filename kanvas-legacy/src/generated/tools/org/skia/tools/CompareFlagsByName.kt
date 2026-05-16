package org.skia.tools

import kotlin.Boolean

/**
 * C++ original:
 * ```cpp
 * struct CompareFlagsByName {
 *     bool operator()(SkFlagInfo* a, SkFlagInfo* b) const {
 *         return strcmp(a->name().c_str(), b->name().c_str()) < 0;
 *     }
 * }
 * ```
 */
public open class CompareFlagsByName {
  /**
   * C++ original:
   * ```cpp
   * bool operator()(SkFlagInfo* a, SkFlagInfo* b) const {
   *         return strcmp(a->name().c_str(), b->name().c_str()) < 0;
   *     }
   * ```
   */
  public operator fun invoke(a: SkFlagInfo?, b: SkFlagInfo?): Boolean {
    TODO("Implement invoke")
  }
}
