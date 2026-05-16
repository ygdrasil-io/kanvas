package org.skia.modules

import kotlin.Boolean
import kotlin.CharArray
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class SKUNICODE_API SkBreakIterator {
 * public:
 *     typedef int32_t Position;
 *     typedef int32_t Status;
 *     SkBreakIterator() = default;
 *     SkBreakIterator(const SkBreakIterator&) = default;
 *     SkBreakIterator& operator=(const SkBreakIterator&) = default;
 *     virtual ~SkBreakIterator() = default;
 *     virtual Position first() = 0;
 *     virtual Position current() = 0;
 *     virtual Position next() = 0;
 *     virtual Status status() = 0;
 *     virtual bool isDone() = 0;
 *     virtual bool setText(const char utftext8[], int utf8Units) = 0;
 *     virtual bool setText(const char16_t utftext16[], int utf16Units) = 0;
 * }
 * ```
 */
public abstract class SkBreakIterator public constructor() {
  /**
   * C++ original:
   * ```cpp
   * SkBreakIterator() = default
   * ```
   */
  public constructor(param0: SkBreakIterator) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkBreakIterator& operator=(const SkBreakIterator&) = default
   * ```
   */
  public fun assign(param0: SkBreakIterator) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual Position first() = 0
   * ```
   */
  public abstract fun first(): SkBreakIteratorPosition

  /**
   * C++ original:
   * ```cpp
   * virtual Position current() = 0
   * ```
   */
  public abstract fun current(): SkBreakIteratorPosition

  /**
   * C++ original:
   * ```cpp
   * virtual Position next() = 0
   * ```
   */
  public abstract fun next(): SkBreakIteratorPosition

  /**
   * C++ original:
   * ```cpp
   * virtual Status status() = 0
   * ```
   */
  public abstract fun status(): SkBreakIteratorStatus

  /**
   * C++ original:
   * ```cpp
   * virtual bool isDone() = 0
   * ```
   */
  public abstract fun isDone(): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual bool setText(const char utftext8[], int utf8Units) = 0
   * ```
   */
  public abstract fun setText(utftext8: CharArray, utf8Units: Int): Boolean
}
