package org.skia.core

import kotlin.Boolean
import kotlin.String

/**
 * C++ original:
 * ```cpp
 * struct EntryComparator {
 *     bool operator()(const Entry& a, const Entry& b) const {
 *         return strcmp(a.fName, b.fName) < 0;
 *     }
 *     bool operator()(const Entry& a, const char* b) const {
 *         return strcmp(a.fName, b) < 0;
 *     }
 *     bool operator()(const char* a, const Entry& b) const {
 *         return strcmp(a, b.fName) < 0;
 *     }
 * }
 * ```
 */
public open class EntryComparator {
  /**
   * C++ original:
   * ```cpp
   * bool operator()(const Entry& a, const Entry& b) const {
   *         return strcmp(a.fName, b.fName) < 0;
   *     }
   * ```
   */
  public operator fun invoke(a: Entry, b: Entry): Boolean {
    TODO("Implement invoke")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator()(const Entry& a, const char* b) const {
   *         return strcmp(a.fName, b) < 0;
   *     }
   * ```
   */
  public operator fun invoke(a: Entry, b: String?): Boolean {
    TODO("Implement invoke")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator()(const char* a, const Entry& b) const {
   *         return strcmp(a, b.fName) < 0;
   *     }
   * ```
   */
  public operator fun invoke(a: String?, b: Entry): Boolean {
    TODO("Implement invoke")
  }
}
