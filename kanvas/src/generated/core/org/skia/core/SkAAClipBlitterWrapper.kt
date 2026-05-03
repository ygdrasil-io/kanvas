package org.skia.core

import org.skia.math.SkIRect

/**
 * C++ original:
 * ```cpp
 * class SkAAClipBlitterWrapper {
 * public:
 *     SkAAClipBlitterWrapper();
 *     SkAAClipBlitterWrapper(const SkRasterClip&, SkBlitter*);
 *     SkAAClipBlitterWrapper(const SkAAClip*, SkBlitter*);
 *
 *     void init(const SkRasterClip&, SkBlitter*);
 *
 *     const SkIRect& getBounds() const {
 *         SkASSERT(fClipRgn);
 *         return fClipRgn->getBounds();
 *     }
 *     const SkRegion& getRgn() const {
 *         SkASSERT(fClipRgn);
 *         return *fClipRgn;
 *     }
 *     SkBlitter* getBlitter() {
 *         SkASSERT(fBlitter);
 *         return fBlitter;
 *     }
 *
 * private:
 *     SkRegion        fBWRgn;
 *     SkAAClipBlitter fAABlitter;
 *     // what we return
 *     const SkRegion* fClipRgn;
 *     SkBlitter* fBlitter;
 * }
 * ```
 */
public data class SkAAClipBlitterWrapper public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkRegion        fBWRgn
   * ```
   */
  private var fBWRgn: SkRegion,
  /**
   * C++ original:
   * ```cpp
   * SkAAClipBlitter fAABlitter
   * ```
   */
  private var fAABlitter: SkAAClipBlitter,
  /**
   * C++ original:
   * ```cpp
   * const SkRegion* fClipRgn
   * ```
   */
  private val fClipRgn: SkRegion?,
  /**
   * C++ original:
   * ```cpp
   * SkBlitter* fBlitter
   * ```
   */
  private var fBlitter: SkBlitter?,
) {
  /**
   * C++ original:
   * ```cpp
   * void SkAAClipBlitterWrapper::init(const SkRasterClip& clip, SkBlitter* blitter) {
   *     SkASSERT(blitter);
   *     if (clip.isBW()) {
   *         fClipRgn = &clip.bwRgn();
   *         fBlitter = blitter;
   *     } else {
   *         const SkAAClip& aaclip = clip.aaRgn();
   *         fBWRgn.setRect(aaclip.getBounds());
   *         fAABlitter.init(blitter, &aaclip);
   *         // now our return values
   *         fClipRgn = &fBWRgn;
   *         fBlitter = &fAABlitter;
   *     }
   * }
   * ```
   */
  public fun `init`(clip: SkRasterClip, blitter: SkBlitter?) {
    TODO("Implement init")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkIRect& getBounds() const {
   *         SkASSERT(fClipRgn);
   *         return fClipRgn->getBounds();
   *     }
   * ```
   */
  public fun getBounds(): SkIRect {
    TODO("Implement getBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkRegion& getRgn() const {
   *         SkASSERT(fClipRgn);
   *         return *fClipRgn;
   *     }
   * ```
   */
  public fun getRgn(): SkRegion {
    TODO("Implement getRgn")
  }

  /**
   * C++ original:
   * ```cpp
   * SkBlitter* getBlitter() {
   *         SkASSERT(fBlitter);
   *         return fBlitter;
   *     }
   * ```
   */
  public fun getBlitter(): SkBlitter {
    TODO("Implement getBlitter")
  }
}
