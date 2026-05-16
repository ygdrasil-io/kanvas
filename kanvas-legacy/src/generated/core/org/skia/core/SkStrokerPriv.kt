package org.skia.core

import org.skia.foundation.SkPaint

/**
 * C++ original:
 * ```cpp
 * class SkStrokerPriv {
 * public:
 *     typedef void (*CapProc)(SkPathBuilder* path,
 *                             const SkPoint& pivot,
 *                             const SkVector& normal,
 *                             const SkPoint& stop,
 *                             bool extendLastPt);
 *
 *     typedef void (*JoinProc)(SkPathBuilder* outer, SkPathBuilder* inner,
 *                              const SkVector& beforeUnitNormal,
 *                              const SkPoint& pivot,
 *                              const SkVector& afterUnitNormal,
 *                              SkScalar radius, SkScalar invMiterLimit,
 *                              bool prevIsLine, bool currIsLine);
 *
 *     static CapProc  CapFactory(SkPaint::Cap);
 *     static JoinProc JoinFactory(SkPaint::Join);
 * }
 * ```
 */
public open class SkStrokerPriv {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * SkStrokerPriv::CapProc SkStrokerPriv::CapFactory(SkPaint::Cap cap) {
     *     const SkStrokerPriv::CapProc gCappers[] = {
     *         ButtCapper, RoundCapper, SquareCapper
     *     };
     *
     *     SkASSERT((unsigned)cap < SkPaint::kCapCount);
     *     return gCappers[cap];
     * }
     * ```
     */
    public fun capFactory(cap: SkPaint.Cap): SkStrokerPrivCapProc {
      TODO("Implement capFactory")
    }

    /**
     * C++ original:
     * ```cpp
     * SkStrokerPriv::JoinProc SkStrokerPriv::JoinFactory(SkPaint::Join join) {
     *     const SkStrokerPriv::JoinProc gJoiners[] = {
     *         MiterJoiner, RoundJoiner, BluntJoiner
     *     };
     *
     *     SkASSERT((unsigned)join < SkPaint::kJoinCount);
     *     return gJoiners[join];
     * }
     * ```
     */
    public fun joinFactory(join: SkPaint.Join): SkStrokerPrivJoinProc {
      TODO("Implement joinFactory")
    }
  }
}
