package org.skia.tests

/**
 * C++ original:
 * ```cpp
 * static struct lineConic {
 *     ConicPts conic;
 *     SkDLine line;
 *     int result;
 *     SkDPoint expected[2];
 * } lineConicTests[] = {
 *     {
 *      {{{{30.6499996,25.6499996}, {30.6499996,20.6499996}, {25.6499996,20.6499996}}}, 0.707107008f},
 *       {{{25.6499996,20.6499996}, {45.6500015,20.6499996}}},
 *           1,
 *        {{25.6499996,20.6499996}, {0,0}}
 *     },
 * }
 * ```
 */
public open class LineConicTests
