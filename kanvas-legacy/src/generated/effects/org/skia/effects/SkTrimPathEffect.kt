package org.skia.effects

import kotlin.Int
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SK_API SkTrimPathEffect {
 * public:
 *     enum class Mode {
 *         kNormal,   // return the subset path [start,stop]
 *         kInverted, // return the complement/subset paths [0,start] + [stop,1]
 *     };
 *
 *     /**
 *      *  Take start and stop "t" values (values between 0...1), and return a path that is that
 *      *  subset of the original path.
 *      *
 *      *  e.g.
 *      *      Make(0.5, 1.0) --> return the 2nd half of the path
 *      *      Make(0.33333, 0.66667) --> return the middle third of the path
 *      *
 *      *  The trim values apply to the entire path, so if it contains several contours, all of them
 *      *  are including in the calculation.
 *      *
 *      *  startT and stopT must be 0..1 inclusive. If they are outside of that interval, they will
 *      *  be pinned to the nearest legal value. If either is NaN, null will be returned.
 *      *
 *      *  Note: for Mode::kNormal, this will return one (logical) segment (even if it is spread
 *      *        across multiple contours). For Mode::kInverted, this will return 2 logical
 *      *        segments: stopT..1 and 0...startT, in this order.
 *      */
 *     static sk_sp<SkPathEffect> Make(SkScalar startT, SkScalar stopT, Mode = Mode::kNormal);
 * }
 * ```
 */
public open class SkTrimPathEffect {
  public enum class Mode {
    kNormal,
    kInverted,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkPathEffect> SkTrimPathEffect::Make(SkScalar startT, SkScalar stopT, Mode mode) {
     *     if (!SkIsFinite(startT, stopT)) {
     *         return nullptr;
     *     }
     *
     *     if (startT <= 0 && stopT >= 1 && mode == Mode::kNormal) {
     *         return nullptr;
     *     }
     *
     *     startT = SkTPin(startT, 0.f, 1.f);
     *     stopT  = SkTPin(stopT,  0.f, 1.f);
     *
     *     if (startT >= stopT && mode == Mode::kInverted) {
     *         return nullptr;
     *     }
     *
     *     return sk_sp<SkPathEffect>(new SkTrimPE(startT, stopT, mode));
     * }
     * ```
     */
    public fun make(
      startT: SkScalar,
      stopT: SkScalar,
      mode: Mode = TODO(),
    ): Int {
      TODO("Implement make")
    }
  }
}
