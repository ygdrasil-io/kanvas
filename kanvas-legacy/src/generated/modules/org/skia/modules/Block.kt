package org.skia.modules

import kotlin.ULong

/**
 * C++ original:
 * ```cpp
 * struct Block {
 *     Block() = default;
 *     Block(size_t start, size_t end, const TextStyle& style) : fRange(start, end), fStyle(style) {}
 *     Block(TextRange textRange, const TextStyle& style) : fRange(textRange), fStyle(style) {}
 *
 *     void add(TextRange tail) {
 *         SkASSERT(fRange.end == tail.start);
 *         fRange = TextRange(fRange.start, fRange.start + fRange.width() + tail.width());
 *     }
 *
 *     TextRange fRange = EMPTY_RANGE;
 *     TextStyle fStyle;
 * }
 * ```
 */
public open class Block public constructor(
  /**
   * C++ original:
   * ```cpp
   * TextRange fRange
   * ```
   */
  public var fRange: TextRange,
  /**
   * C++ original:
   * ```cpp
   * TextStyle fStyle
   * ```
   */
  public var fStyle: TextStyle,
) {
  /**
   * C++ original:
   * ```cpp
   * Block() = default
   * ```
   */
  public constructor() : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * Block(size_t start, size_t end, const TextStyle& style) : fRange(start, end), fStyle(style) {}
   * ```
   */
  public constructor(
    start: ULong,
    end: ULong,
    style: TextStyle,
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * Block(TextRange textRange, const TextStyle& style) : fRange(textRange), fStyle(style) {}
   * ```
   */
  public constructor(textRange: TextRange, style: TextStyle) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * void add(TextRange tail) {
   *         SkASSERT(fRange.end == tail.start);
   *         fRange = TextRange(fRange.start, fRange.start + fRange.width() + tail.width());
   *     }
   * ```
   */
  public fun add(tail: TextRange) {
    TODO("Implement add")
  }
}
