package org.skia.tests

/**
 * C++ original:
 * ```cpp
 * static struct lineQuad {
 *     QuadPts quad;
 *     SkDLine line;
 *     int result;
 *     SkDPoint expected[2];
 * } lineQuadTests[] = {
 *     //        quad                    line                  results
 *     {{{{1, 1}, {2, 1}, {0, 2}}}, {{{0, 0}, {1, 1}}},  1,  {{1, 1}, {0, 0}} },
 *     {{{{0, 0}, {1, 1}, {3, 1}}}, {{{0, 0}, {3, 1}}},  2,  {{0, 0}, {3, 1}} },
 *     {{{{2, 0}, {1, 1}, {2, 2}}}, {{{0, 0}, {0, 2}}},  0,  {{0, 0}, {0, 0}} },
 *     {{{{4, 0}, {0, 1}, {4, 2}}}, {{{3, 1}, {4, 1}}},  0,  {{0, 0}, {0, 0}} },
 *     {{{{0, 0}, {0, 1}, {1, 1}}}, {{{0, 1}, {1, 0}}},  1,  {{.25, .75}, {0, 0}} },
 * }
 * ```
 */
public open class LineQuadTests
