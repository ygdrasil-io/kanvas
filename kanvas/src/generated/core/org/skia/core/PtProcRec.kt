package org.skia.core

import kotlin.Boolean
import kotlin.Int
import org.skia.math.SkMatrix
import org.skia.math.SkRect
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * struct PtProcRec {
 *     SkCanvas::PointMode fMode;
 *     const SkPaint*  fPaint;
 *     const SkRegion* fClip;
 *     const SkRasterClip* fRC;
 *
 *     // computed values
 *     SkRect   fClipBounds;
 *     SkScalar fRadius;
 *
 *     typedef void (*Proc)(const PtProcRec&, SkSpan<const SkPoint> devPts, SkBlitter*);
 *
 *     bool init(SkCanvas::PointMode, const SkPaint&, const SkMatrix* matrix,
 *               const SkRasterClip*);
 *     Proc chooseProc(SkBlitter** blitter);
 *
 * private:
 *     SkAAClipBlitterWrapper fWrapper;
 * }
 * ```
 */
public data class PtProcRec public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkCanvas::PointMode fMode
   * ```
   */
  public var fMode: SkCanvas.PointMode,
  /**
   * C++ original:
   * ```cpp
   * const SkPaint*  fPaint
   * ```
   */
  public val fPaint: SkPaint?,
  /**
   * C++ original:
   * ```cpp
   * const SkRegion* fClip
   * ```
   */
  public val fClip: SkRegion?,
  /**
   * C++ original:
   * ```cpp
   * const SkRasterClip* fRC
   * ```
   */
  public val fRC: SkRasterClip?,
  /**
   * C++ original:
   * ```cpp
   * SkRect   fClipBounds
   * ```
   */
  public var fClipBounds: SkRect,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fRadius
   * ```
   */
  public var fRadius: SkScalar,
  /**
   * C++ original:
   * ```cpp
   * SkAAClipBlitterWrapper fWrapper
   * ```
   */
  private var fWrapper: SkAAClipBlitterWrapper,
) {
  /**
   * C++ original:
   * ```cpp
   * bool PtProcRec::init(SkCanvas::PointMode mode, const SkPaint& paint,
   *                      const SkMatrix* matrix, const SkRasterClip* rc) {
   *     if ((unsigned)mode > (unsigned)SkCanvas::kPolygon_PointMode) {
   *         return false;
   *     }
   *     if (paint.getPathEffect() || paint.getMaskFilter()) {
   *         return false;
   *     }
   *     SkScalar width = paint.getStrokeWidth();
   *     SkScalar radius = -1;   // sentinel value, a "valid" value must be > 0
   *
   *     if (0 == width) {
   *         radius = 0.5f;
   *     } else if (paint.getStrokeCap() != SkPaint::kRound_Cap &&
   *                matrix->isScaleTranslate() && SkCanvas::kPoints_PointMode == mode) {
   *         SkScalar sx = matrix->get(SkMatrix::kMScaleX);
   *         SkScalar sy = matrix->get(SkMatrix::kMScaleY);
   *         if (SkScalarNearlyZero(sx - sy)) {
   *             radius = SkScalarHalf(width * SkScalarAbs(sx));
   *         }
   *     }
   *     if (radius > 0) {
   *         SkRect clipBounds = SkRect::Make(rc->getBounds());
   *         // if we return true, the caller may assume that the constructed shapes can be represented
   *         // using SkFixed (after clipping), so we preflight that here.
   *         if (!SkRectPriv::FitsInFixed(clipBounds)) {
   *             return false;
   *         }
   *         fMode = mode;
   *         fPaint = &paint;
   *         fClip = nullptr;
   *         fRC = rc;
   *         fClipBounds = clipBounds;
   *         fRadius = radius;
   *         return true;
   *     }
   *     return false;
   * }
   * ```
   */
  public fun `init`(
    mode: SkCanvas.PointMode,
    paint: SkPaint,
    matrix: SkMatrix?,
    rc: SkRasterClip?,
  ): Boolean {
    TODO("Implement init")
  }

  /**
   * C++ original:
   * ```cpp
   * PtProcRec::Proc PtProcRec::chooseProc(SkBlitter** blitterPtr) {
   *     Proc proc = nullptr;
   *
   *     SkBlitter* blitter = *blitterPtr;
   *     if (fRC->isBW()) {
   *         fClip = &fRC->bwRgn();
   *     } else {
   *         fWrapper.init(*fRC, blitter);
   *         fClip = &fWrapper.getRgn();
   *         blitter = fWrapper.getBlitter();
   *         *blitterPtr = blitter;
   *     }
   *
   *     // for our arrays
   *     SkASSERT(0 == SkCanvas::kPoints_PointMode);
   *     SkASSERT(1 == SkCanvas::kLines_PointMode);
   *     SkASSERT(2 == SkCanvas::kPolygon_PointMode);
   *     SkASSERT((unsigned)fMode <= (unsigned)SkCanvas::kPolygon_PointMode);
   *
   *     if (fPaint->isAntiAlias()) {
   *         if (0 == fPaint->getStrokeWidth()) {
   *             static const Proc gAAProcs[] = {
   *                 aa_square_proc, aa_line_hair_proc, aa_poly_hair_proc
   *             };
   *             proc = gAAProcs[fMode];
   *         } else if (fPaint->getStrokeCap() != SkPaint::kRound_Cap) {
   *             SkASSERT(SkCanvas::kPoints_PointMode == fMode);
   *             proc = aa_square_proc;
   *         }
   *     } else {    // BW
   *         if (fRadius <= 0.5f) {    // small radii and hairline
   *             static const Proc gBWProcs[] = {
   *                 bw_pt_hair_proc, bw_line_hair_proc, bw_poly_hair_proc
   *             };
   *             proc = gBWProcs[fMode];
   *         } else {
   *             proc = bw_square_proc;
   *         }
   *     }
   *     return proc;
   * }
   * ```
   */
  public fun chooseProc(blitter: Int?): PtProcRecProc {
    TODO("Implement chooseProc")
  }
}
