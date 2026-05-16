package org.skia.modules

import kotlin.Boolean
import kotlin.Double
import kotlin.ULong
import kotlin.collections.Map

/**
 * C++ original:
 * ```cpp
 * class LineMetrics {
 * public:
 *     LineMetrics() { }
 *
 *     LineMetrics(size_t start,
 *                 size_t end,
 *                 size_t end_excluding_whitespace,
 *                 size_t end_including_newline,
 *                 bool hard_break)
 *             : fStartIndex(start)
 *             , fEndIndex(end)
 *             , fEndExcludingWhitespaces(end_excluding_whitespace)
 *             , fEndIncludingNewline(end_including_newline)
 *             , fHardBreak(hard_break) {}
 *     // The following fields are used in the layout process itself.
 *
 *     // The indexes in the text buffer the line begins and ends.
 *     size_t fStartIndex = 0;
 *     size_t fEndIndex = 0;
 *     size_t fEndExcludingWhitespaces = 0;
 *     size_t fEndIncludingNewline = 0;
 *     bool fHardBreak = false;
 *
 *     // The following fields are tracked after or during layout to provide to
 *     // the user as well as for computing bounding boxes.
 *
 *     // The final computed ascent and descent for the line. This can be impacted by
 *     // the strut, height, scaling, as well as outlying runs that are very tall.
 *     //
 *     // The top edge is `baseline - ascent` and the bottom edge is `baseline +
 *     // descent`. Ascent and descent are provided as positive numbers. Raw numbers
 *     // for specific runs of text can be obtained in run_metrics_map. These values
 *     // are the cumulative metrics for the entire line.
 *     double fAscent = SK_ScalarMax;
 *     double fDescent = SK_ScalarMin;
 *     double fUnscaledAscent = SK_ScalarMax;
 *     // Total height of the paragraph including the current line.
 *     //
 *     // The height of the current line is `round(ascent + descent)`.
 *     double fHeight = 0.0;
 *     // Width of the line.
 *     double fWidth = 0.0;
 *     // The left edge of the line. The right edge can be obtained with `left +
 *     // width`
 *     double fLeft = 0.0;
 *     // The y position of the baseline for this line from the top of the paragraph.
 *     double fBaseline = 0.0;
 *     // Zero indexed line number
 *     size_t fLineNumber = 0;
 *
 *     // Mapping between text index ranges and the FontMetrics associated with
 *     // them. The first run will be keyed under start_index. The metrics here
 *     // are before layout and are the base values we calculate from.
 *     std::map<size_t, StyleMetrics> fLineMetrics;
 * }
 * ```
 */
public data class LineMetrics public constructor(
  /**
   * C++ original:
   * ```cpp
   * size_t fStartIndex = 0
   * ```
   */
  public var fStartIndex: ULong,
  /**
   * C++ original:
   * ```cpp
   * size_t fEndIndex = 0
   * ```
   */
  public var fEndIndex: ULong,
  /**
   * C++ original:
   * ```cpp
   * size_t fEndExcludingWhitespaces = 0
   * ```
   */
  public var fEndExcludingWhitespaces: ULong,
  /**
   * C++ original:
   * ```cpp
   * size_t fEndIncludingNewline = 0
   * ```
   */
  public var fEndIncludingNewline: ULong,
  /**
   * C++ original:
   * ```cpp
   * bool fHardBreak = false
   * ```
   */
  public var fHardBreak: Boolean,
  /**
   * C++ original:
   * ```cpp
   * double fAscent = SK_ScalarMax
   * ```
   */
  public var fAscent: Double,
  /**
   * C++ original:
   * ```cpp
   * double fDescent = SK_ScalarMin
   * ```
   */
  public var fDescent: Double,
  /**
   * C++ original:
   * ```cpp
   * double fUnscaledAscent = SK_ScalarMax
   * ```
   */
  public var fUnscaledAscent: Double,
  /**
   * C++ original:
   * ```cpp
   * double fHeight = 0.0
   * ```
   */
  public var fHeight: Double,
  /**
   * C++ original:
   * ```cpp
   * double fWidth = 0.0
   * ```
   */
  public var fWidth: Double,
  /**
   * C++ original:
   * ```cpp
   * double fLeft = 0.0
   * ```
   */
  public var fLeft: Double,
  /**
   * C++ original:
   * ```cpp
   * double fBaseline = 0.0
   * ```
   */
  public var fBaseline: Double,
  /**
   * C++ original:
   * ```cpp
   * size_t fLineNumber = 0
   * ```
   */
  public var fLineNumber: ULong,
  /**
   * C++ original:
   * ```cpp
   * std::map<size_t, StyleMetrics> fLineMetrics
   * ```
   */
  public var fLineMetrics: Map<ULong, StyleMetrics>,
)
