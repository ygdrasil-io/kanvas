package org.skia.modules

/**
 * C++ original:
 * ```cpp
 * struct Placeholder {
 *     Placeholder() = default;
 *     Placeholder(size_t start, size_t end, const PlaceholderStyle& style, const TextStyle& textStyle,
 *                 BlockRange blocksBefore, TextRange textBefore)
 *             : fRange(start, end)
 *             , fStyle(style)
 *             , fTextStyle(textStyle)
 *             , fBlocksBefore(blocksBefore)
 *             , fTextBefore(textBefore) {}
 *
 *     TextRange fRange = EMPTY_RANGE;
 *     PlaceholderStyle fStyle;
 *     TextStyle fTextStyle;
 *     BlockRange fBlocksBefore;
 *     TextRange fTextBefore;
 * }
 * ```
 */
public data class Placeholder public constructor(
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
   * PlaceholderStyle fStyle
   * ```
   */
  public var fStyle: PlaceholderStyle,
  /**
   * C++ original:
   * ```cpp
   * TextStyle fTextStyle
   * ```
   */
  public var fTextStyle: TextStyle,
  /**
   * C++ original:
   * ```cpp
   * BlockRange fBlocksBefore
   * ```
   */
  public var fBlocksBefore: BlockRange,
  /**
   * C++ original:
   * ```cpp
   * TextRange fTextBefore
   * ```
   */
  public var fTextBefore: TextRange,
)
