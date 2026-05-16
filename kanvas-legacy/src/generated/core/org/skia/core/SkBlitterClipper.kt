package org.skia.core

import org.skia.foundation.SkRegion
import org.skia.math.SkIRect

/**
 * C++ original:
 * ```cpp
 * class SkBlitterClipper {
 * public:
 *     SkBlitter*  apply(SkBlitter* blitter, const SkRegion* clip,
 *                       const SkIRect* bounds = nullptr);
 *
 * private:
 *     SkNullBlitter       fNullBlitter;
 *     SkRectClipBlitter   fRectBlitter;
 *     SkRgnClipBlitter    fRgnBlitter;
 * }
 * ```
 */
public data class SkBlitterClipper public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkNullBlitter       fNullBlitter
   * ```
   */
  private var fNullBlitter: SkNullBlitter,
  /**
   * C++ original:
   * ```cpp
   * SkRectClipBlitter   fRectBlitter
   * ```
   */
  private var fRectBlitter: SkRectClipBlitter,
  /**
   * C++ original:
   * ```cpp
   * SkRgnClipBlitter    fRgnBlitter
   * ```
   */
  private var fRgnBlitter: SkRgnClipBlitter,
) {
  /**
   * C++ original:
   * ```cpp
   * SkBlitter* SkBlitterClipper::apply(SkBlitter* blitter, const SkRegion* clip,
   *                                    const SkIRect* ir) {
   *     if (clip) {
   *         const SkIRect& clipR = clip->getBounds();
   *
   *         if (clip->isEmpty() || (ir && !SkIRect::Intersects(clipR, *ir))) {
   *             blitter = &fNullBlitter;
   *         } else if (clip->isRect()) {
   *             if (ir == nullptr || !clipR.contains(*ir)) {
   *                 fRectBlitter.init(blitter, clipR);
   *                 blitter = &fRectBlitter;
   *             }
   *         } else {
   *             fRgnBlitter.init(blitter, clip);
   *             blitter = &fRgnBlitter;
   *         }
   *     }
   *     return blitter;
   * }
   * ```
   */
  public fun apply(
    blitter: SkBlitter?,
    clip: SkRegion?,
    bounds: SkIRect? = TODO(),
  ): SkBlitter {
    TODO("Implement apply")
  }
}
