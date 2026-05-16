package org.skia.modules

/**
 * C++ original:
 * ```cpp
 * template <typename TStyle>
 * struct StyleBlock {
 *     StyleBlock() : fRange(EMPTY_RANGE), fStyle() { }
 *     StyleBlock(size_t start, size_t end, const TStyle& style) : fRange(start, end), fStyle(style) {}
 *     StyleBlock(TextRange textRange, const TStyle& style) : fRange(textRange), fStyle(style) {}
 *     void add(TextRange tail) {
 *         SkASSERT(fRange.end == tail.start);
 *         fRange = TextRange(fRange.start, fRange.start + fRange.width() + tail.width());
 *     }
 *     TextRange fRange;
 *     TStyle fStyle;
 * }
 * ```
 */
public data class StyleBlock<TStyle> public constructor(
  /**
   * C++ original:
   * ```cpp
   * TextRange fRange
   * ```
   */
  private var fRange: TextLine,
  /**
   * C++ original:
   * ```cpp
   * TStyle fStyle
   * ```
   */
  private var fStyle: TStyle,
) {
  /**
   * C++ original:
   * ```cpp
   * void add(TextRange tail) {
   *         SkASSERT(fRange.end == tail.start);
   *         fRange = TextRange(fRange.start, fRange.start + fRange.width() + tail.width());
   *     }
   * ```
   */
  private fun add(tail: TextRange) {
    TODO("Implement add")
  }
}
