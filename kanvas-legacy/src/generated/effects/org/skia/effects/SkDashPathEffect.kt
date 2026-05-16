package org.skia.effects

import kotlin.Int
import org.skia.foundation.SkSpan
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SK_API SkDashPathEffect {
 * public:
 *     /** intervals: array containing an even number of entries (>=2), with
 *          the even indices specifying the length of "on" intervals, and the odd
 *          indices specifying the length of "off" intervals. This array will be
 *          copied in Make, and can be disposed of freely after.
 *         count: number of elements in the intervals array
 *         phase: offset into the intervals array (mod the sum of all of the
 *          intervals).
 *
 *         For example: if intervals[] = {10, 20}, count = 2, and phase = 25,
 *          this will set up a dashed path like so:
 *          5 pixels off
 *          10 pixels on
 *          20 pixels off
 *          10 pixels on
 *          20 pixels off
 *          ...
 *         A phase of -5, 25, 55, 85, etc. would all result in the same path,
 *          because the sum of all the intervals is 30.
 *
 *         Note: only affects stroked paths.
 *     */
 *     static sk_sp<SkPathEffect> Make(SkSpan<const SkScalar> intervals, SkScalar phase);
 *
 * #ifdef SK_SUPPORT_UNSPANNED_APIS
 *     static sk_sp<SkPathEffect> Make(const SkScalar intervals[], int count, SkScalar phase) {
 *         return intervals ? Make({intervals, count}, phase) : nullptr;
 *     }
 * #endif
 * }
 * ```
 */
public open class SkDashPathEffect {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkPathEffect> SkDashPathEffect::Make(SkSpan<const SkScalar> intervals, SkScalar phase) {
     *     if (!SkDashPath::ValidDashPath(phase, intervals)) {
     *         return nullptr;
     *     }
     *     return sk_sp<SkPathEffect>(new SkDashImpl(intervals, phase));
     * }
     * ```
     */
    public fun make(intervals: SkSpan<SkScalar>, phase: SkScalar): Int {
      TODO("Implement make")
    }
  }
}
