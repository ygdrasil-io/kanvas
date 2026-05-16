package org.skia.core

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkPixmap
import org.skia.math.SkIPoint
import org.skia.math.SkIRect

/**
 * C++ original:
 * ```cpp
 * class SkDrawTiler {
 *     // 8K is 1 too big, since 8K << supersample == 32768 which is too big for SkFixed
 *     static constexpr int kMaxDim = 8192 - 1;
 *
 *     SkBitmapDevice* fDevice;
 *     SkPixmap        fRootPixmap;
 *     SkIRect         fSrcBounds;
 *
 *     // Used for tiling and non-tiling
 *     skcpu::Draw fDraw;
 *
 *     // fTileMatrix... are only used if fNeedTiling
 *     std::optional<SkMatrix> fTileMatrix;
 *     SkRasterClip            fTileRC;
 *     SkIPoint                fOrigin;
 *
 *     bool            fDone, fNeedsTiling;
 *
 * public:
 *     static bool NeedsTiling(SkBitmapDevice* dev) {
 *         return dev->width() > kMaxDim || dev->height() > kMaxDim;
 *     }
 *
 *     SkDrawTiler(SkBitmapDevice* dev, const SkRect* bounds) : fDevice(dev) {
 *         fDone = false;
 *
 *         // we need fDst to be set, and if we're actually drawing, to dirty the genID
 *         if (!fDevice->accessPixels(&fRootPixmap)) {
 *             // NoDrawDevice uses us (why?) so we have to catch this case w/ no pixels
 *             fRootPixmap.reset(fDevice->imageInfo(), nullptr, 0);
 *         }
 *
 *         // do a quick check, so we don't even have to process "bounds" if there is no need
 *         const SkIRect clipR = fDevice->fRCStack.rc().getBounds();
 *         fNeedsTiling = clipR.right() > kMaxDim || clipR.bottom() > kMaxDim;
 *         if (fNeedsTiling) {
 *             if (bounds) {
 *                 // Make sure we round first, and then intersect. We can't rely on promoting the
 *                 // clipR to floats (and then intersecting with devBounds) since promoting
 *                 // int --> float can make the float larger than the int.
 *                 // rounding(out) first runs the risk of clamping if the float is larger an intmax
 *                 // but our roundOut() is saturating, which is fine for this use case
 *                 //
 *                 // e.g. the older version of this code did this:
 *                 //    devBounds = mapRect(bounds);
 *                 //    if (devBounds.intersect(SkRect::Make(clipR))) {
 *                 //        fSrcBounds = devBounds.roundOut();
 *                 // The problem being that the promotion of clipR to SkRect was unreliable
 *                 //
 *                 fSrcBounds = fDevice->localToDevice().mapRect(*bounds).roundOut();
 *                 if (fSrcBounds.intersect(clipR)) {
 *                     // Check again, now that we have computed srcbounds.
 *                     fNeedsTiling = fSrcBounds.right() > kMaxDim || fSrcBounds.bottom() > kMaxDim;
 *                 } else {
 *                     fNeedsTiling = false;
 *                     fDone = true;
 *                 }
 *             } else {
 *                 fSrcBounds = clipR;
 *             }
 *         }
 *
 *         if (fNeedsTiling) {
 *             // fDraw.fDst and fCTM are reset each time in setupTileDraw()
 *             fDraw.fRC = &fTileRC;
 *             // we'll step/increase it before using it
 *             fOrigin.set(fSrcBounds.fLeft - kMaxDim, fSrcBounds.fTop);
 *         } else {
 *             // don't reference fSrcBounds, as it may not have been set
 *             fDraw.fDst = fRootPixmap;
 *             fDraw.fCTM = &fDevice->localToDevice();
 *             fDraw.fRC = &fDevice->fRCStack.rc();
 *             fOrigin.set(0, 0);
 *         }
 *
 *         fDraw.fProps = &fDevice->surfaceProps();
 *         if (fDevice->fRecorder) {
 *             fDraw.fCtx = fDevice->fRecorder->ctx();
 *         }
 *     }
 *
 *     bool needsTiling() const { return fNeedsTiling; }
 *
 *     const skcpu::Draw* next() {
 *         if (fDone) {
 *             return nullptr;
 *         }
 *         if (fNeedsTiling) {
 *             do {
 *                 this->stepAndSetupTileDraw();  // might set the clip to empty and fDone to true
 *             } while (!fDone && fTileRC.isEmpty());
 *             // if we exit the loop and we're still empty, we're (past) done
 *             if (fTileRC.isEmpty()) {
 *                 SkASSERT(fDone);
 *                 return nullptr;
 *             }
 *             SkASSERT(!fTileRC.isEmpty());
 *         } else {
 *             fDone = true;   // only draw untiled once
 *         }
 *         return &fDraw;
 *     }
 *
 * private:
 *     void stepAndSetupTileDraw() {
 *         SkASSERT(!fDone);
 *         SkASSERT(fNeedsTiling);
 *
 *         // We do fRootPixmap.width() - kMaxDim instead of fOrigin.fX + kMaxDim to avoid overflow.
 *         if (fOrigin.fX >= fSrcBounds.fRight - kMaxDim) {    // too far
 *             fOrigin.fX = fSrcBounds.fLeft;
 *             fOrigin.fY += kMaxDim;
 *         } else {
 *             fOrigin.fX += kMaxDim;
 *         }
 *         // fDone = next origin will be invalid.
 *         fDone = fOrigin.fX >= fSrcBounds.fRight - kMaxDim &&
 *                 fOrigin.fY >= fSrcBounds.fBottom - kMaxDim;
 *
 *         SkIRect bounds = SkIRect::MakeXYWH(fOrigin.x(), fOrigin.y(), kMaxDim, kMaxDim);
 *         SkASSERT(!bounds.isEmpty());
 *         bool success = fRootPixmap.extractSubset(&fDraw.fDst, bounds);
 *         SkASSERT_RELEASE(success);
 *         // now don't use bounds, since fDst has the clipped dimensions.
 *
 *         fTileMatrix = fDevice->localToDevice();
 *         fTileMatrix->postTranslate(-fOrigin.x(), -fOrigin.y());
 *         fDraw.fCTM = &fTileMatrix.value();
 *         fDevice->fRCStack.rc().translate(-fOrigin.x(), -fOrigin.y(), &fTileRC);
 *         fTileRC.op(SkIRect::MakeSize(fDraw.fDst.dimensions()), SkClipOp::kIntersect);
 *     }
 * }
 * ```
 */
public data class SkDrawTiler public constructor(
  /**
   * C++ original:
   * ```cpp
   * static constexpr int kMaxDim = 8192 - 1
   * ```
   */
  private var fDevice: SkBitmapDevice?,
  /**
   * C++ original:
   * ```cpp
   * SkBitmapDevice* fDevice
   * ```
   */
  private var fRootPixmap: SkPixmap,
  /**
   * C++ original:
   * ```cpp
   * SkPixmap        fRootPixmap
   * ```
   */
  private var fSrcBounds: SkIRect,
  /**
   * C++ original:
   * ```cpp
   * SkIRect         fSrcBounds
   * ```
   */
  private var fDraw: Draw,
  /**
   * C++ original:
   * ```cpp
   * skcpu::Draw fDraw
   * ```
   */
  private var fTileMatrix: Int,
  /**
   * C++ original:
   * ```cpp
   * std::optional<SkMatrix> fTileMatrix
   * ```
   */
  private var fTileRC: SkRasterClip,
  /**
   * C++ original:
   * ```cpp
   * SkRasterClip            fTileRC
   * ```
   */
  private var fOrigin: SkIPoint,
  /**
   * C++ original:
   * ```cpp
   * SkIPoint                fOrigin
   * ```
   */
  private var fDone: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool            fDone
   * ```
   */
  private var fNeedsTiling: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * bool needsTiling() const { return fNeedsTiling; }
   * ```
   */
  public fun needsTiling(): Boolean {
    TODO("Implement needsTiling")
  }

  /**
   * C++ original:
   * ```cpp
   * const skcpu::Draw* next() {
   *         if (fDone) {
   *             return nullptr;
   *         }
   *         if (fNeedsTiling) {
   *             do {
   *                 this->stepAndSetupTileDraw();  // might set the clip to empty and fDone to true
   *             } while (!fDone && fTileRC.isEmpty());
   *             // if we exit the loop and we're still empty, we're (past) done
   *             if (fTileRC.isEmpty()) {
   *                 SkASSERT(fDone);
   *                 return nullptr;
   *             }
   *             SkASSERT(!fTileRC.isEmpty());
   *         } else {
   *             fDone = true;   // only draw untiled once
   *         }
   *         return &fDraw;
   *     }
   * ```
   */
  public fun next(): Draw {
    TODO("Implement next")
  }

  /**
   * C++ original:
   * ```cpp
   * void stepAndSetupTileDraw() {
   *         SkASSERT(!fDone);
   *         SkASSERT(fNeedsTiling);
   *
   *         // We do fRootPixmap.width() - kMaxDim instead of fOrigin.fX + kMaxDim to avoid overflow.
   *         if (fOrigin.fX >= fSrcBounds.fRight - kMaxDim) {    // too far
   *             fOrigin.fX = fSrcBounds.fLeft;
   *             fOrigin.fY += kMaxDim;
   *         } else {
   *             fOrigin.fX += kMaxDim;
   *         }
   *         // fDone = next origin will be invalid.
   *         fDone = fOrigin.fX >= fSrcBounds.fRight - kMaxDim &&
   *                 fOrigin.fY >= fSrcBounds.fBottom - kMaxDim;
   *
   *         SkIRect bounds = SkIRect::MakeXYWH(fOrigin.x(), fOrigin.y(), kMaxDim, kMaxDim);
   *         SkASSERT(!bounds.isEmpty());
   *         bool success = fRootPixmap.extractSubset(&fDraw.fDst, bounds);
   *         SkASSERT_RELEASE(success);
   *         // now don't use bounds, since fDst has the clipped dimensions.
   *
   *         fTileMatrix = fDevice->localToDevice();
   *         fTileMatrix->postTranslate(-fOrigin.x(), -fOrigin.y());
   *         fDraw.fCTM = &fTileMatrix.value();
   *         fDevice->fRCStack.rc().translate(-fOrigin.x(), -fOrigin.y(), &fTileRC);
   *         fTileRC.op(SkIRect::MakeSize(fDraw.fDst.dimensions()), SkClipOp::kIntersect);
   *     }
   * ```
   */
  private fun stepAndSetupTileDraw() {
    TODO("Implement stepAndSetupTileDraw")
  }

  public companion object {
    private val kMaxDim: Int = TODO("Initialize kMaxDim")

    /**
     * C++ original:
     * ```cpp
     * static bool NeedsTiling(SkBitmapDevice* dev) {
     *         return dev->width() > kMaxDim || dev->height() > kMaxDim;
     *     }
     * ```
     */
    public fun needsTiling(dev: SkBitmapDevice?): Boolean {
      TODO("Implement needsTiling")
    }
  }
}
