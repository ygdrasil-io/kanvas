package org.skia.modules

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class StyleMetrics {
 * public:
 *     explicit StyleMetrics(const TextStyle* style) : text_style(style) {}
 *
 *     StyleMetrics(const TextStyle* style, SkFontMetrics& metrics)
 *             : text_style(style), font_metrics(metrics) {}
 *
 *     const TextStyle* text_style;
 *
 *     // SkFontMetrics contains the following metrics:
 *     //
 *     // * Top                 distance to reserve above baseline
 *     // * Ascent              distance to reserve below baseline
 *     // * Descent             extent below baseline
 *     // * Bottom              extent below baseline
 *     // * Leading             distance to add between lines
 *     // * AvgCharWidth        average character width
 *     // * MaxCharWidth        maximum character width
 *     // * XMin                minimum x
 *     // * XMax                maximum x
 *     // * XHeight             height of lower-case 'x'
 *     // * CapHeight           height of an upper-case letter
 *     // * UnderlineThickness  underline thickness
 *     // * UnderlinePosition   underline position relative to baseline
 *     // * StrikeoutThickness  strikeout thickness
 *     // * StrikeoutPosition   strikeout position relative to baseline
 *     SkFontMetrics font_metrics;
 * }
 * ```
 */
public data class StyleMetrics public constructor(
  /**
   * C++ original:
   * ```cpp
   * const TextStyle* text_style
   * ```
   */
  public val textStyle: Int?,
  /**
   * C++ original:
   * ```cpp
   * SkFontMetrics font_metrics
   * ```
   */
  public var fontMetrics: Int,
)
