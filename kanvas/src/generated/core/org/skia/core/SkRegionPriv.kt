package org.skia.core

import kotlin.Int
import kotlin.Unit
import org.skia.math.SkIRect

/**
 * C++ original:
 * ```cpp
 * class SkRegionPriv {
 * public:
 *     inline static constexpr int kRunTypeSentinel = 0x7FFFFFFF;
 *     typedef SkRegion::RunType RunType;
 *     typedef SkRegion::RunHead RunHead;
 *
 *     // Call the function with each span, in Y -> X ascending order.
 *     // We pass a rect, but we will still ensure the span Y->X ordering, so often the height
 *     // of the rect may be 1. It should never be empty.
 *     static void VisitSpans(const SkRegion& rgn, const std::function<void(const SkIRect&)>&);
 *
 * #ifdef SK_DEBUG
 *     static void Validate(const SkRegion& rgn);
 * #endif
 * }
 * ```
 */
public open class SkRegionPriv {
  public companion object {
    public val kRunTypeSentinel: Int = TODO("Initialize kRunTypeSentinel")

    /**
     * C++ original:
     * ```cpp
     * void SkRegionPriv::VisitSpans(const SkRegion& rgn,
     *                               const std::function<void(const SkIRect&)>& visitor) {
     *     if (rgn.isEmpty()) {
     *         return;
     *     }
     *     if (rgn.isRect()) {
     *         visitor(rgn.getBounds());
     *     } else {
     *         const int32_t* p = rgn.fRunHead->readonly_runs();
     *         int32_t top = *p++;
     *         int32_t bot = *p++;
     *         do {
     *             int pairCount = *p++;
     *             if (pairCount == 1) {
     *                 visitor({ p[0], top, p[1], bot });
     *                 p += 2;
     *             } else if (pairCount > 1) {
     *                 // we have to loop repeated in Y, sending each interval in Y -> X order
     *                 for (int y = top; y < bot; ++y) {
     *                     visit_pairs(pairCount, y, p, visitor);
     *                 }
     *                 p += pairCount * 2;
     *             }
     *             assert_sentinel(*p, true);
     *             p += 1; // skip sentinel
     *
     *             // read next bottom or sentinel
     *             top = bot;
     *             bot = *p++;
     *         } while (!SkRegionValueIsSentinel(bot));
     *     }
     * }
     * ```
     */
    public fun visitSpans(rgn: SkRegion, visitor: (SkIRect) -> Unit) {
      TODO("Implement visitSpans")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkRegionPriv::Validate(const SkRegion& rgn) { SkASSERT(rgn.isValid()); }
     * ```
     */
    public fun validate(rgn: SkRegion) {
      TODO("Implement validate")
    }
  }
}
