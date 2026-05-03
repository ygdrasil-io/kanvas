package org.skia.core

import org.skia.math.SkIRect

/**
 * C++ original:
 * ```cpp
 * class SkScanClipper {
 * public:
 *     SkScanClipper(SkBlitter* blitter, const SkRegion* clip, const SkIRect& bounds,
 *                   bool skipRejectTest = false, bool boundsPreClipped = false);
 *
 *     SkBlitter*      getBlitter() const { return fBlitter; }
 *     const SkIRect*  getClipRect() const { return fClipRect; }
 *
 * private:
 *     SkRectClipBlitter   fRectBlitter;
 *     SkRgnClipBlitter    fRgnBlitter;
 * #ifdef SK_DEBUG
 *     SkRectClipCheckBlitter fRectClipCheckBlitter;
 * #endif
 *     SkBlitter*          fBlitter;
 *     const SkIRect*      fClipRect;
 * }
 * ```
 */
public data class SkScanClipper public constructor(
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
  /**
   * C++ original:
   * ```cpp
   * SkRectClipCheckBlitter fRectClipCheckBlitter
   * ```
   */
  private var fRectClipCheckBlitter: SkRectClipCheckBlitter,
  /**
   * C++ original:
   * ```cpp
   * SkBlitter*          fBlitter
   * ```
   */
  private var fBlitter: SkBlitter?,
  /**
   * C++ original:
   * ```cpp
   * const SkIRect*      fClipRect
   * ```
   */
  private val fClipRect: SkIRect?,
) {
  /**
   * C++ original:
   * ```cpp
   * SkBlitter*      getBlitter() const { return fBlitter; }
   * ```
   */
  public fun getBlitter(): SkBlitter {
    TODO("Implement getBlitter")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkIRect*  getClipRect() const { return fClipRect; }
   * ```
   */
  public fun getClipRect(): SkIRect {
    TODO("Implement getClipRect")
  }
}
