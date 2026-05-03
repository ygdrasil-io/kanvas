package org.skia.tests

import kotlin.Boolean
import org.skia.core.SkRasterHandleAllocator
import org.skia.foundation.SkImageInfo
import org.skia.math.SkIRect
import org.skia.math.SkMatrix
import undefined.Handle
import undefined.Rec

/**
 * C++ original:
 * ```cpp
 * class SkiaAllocator : public SkRasterHandleAllocator {
 * public:
 *     SkiaAllocator() {}
 *
 *     bool allocHandle(const SkImageInfo& info, Rec* rec) override {
 *         sk_sp<SkSurface> surface = SkSurfaces::Raster(info);
 *         if (!surface) {
 *             return false;
 *         }
 *         SkCanvas* canvas = surface->getCanvas();
 *         SkPixmap pixmap;
 *         canvas->peekPixels(&pixmap);
 *
 *         rec->fReleaseProc = [](void* pixels, void* ctx){ SkSafeUnref((SkSurface*)ctx); };
 *         rec->fReleaseCtx = surface.release();
 *         rec->fPixels = pixmap.writable_addr();
 *         rec->fRowBytes = pixmap.rowBytes();
 *         rec->fHandle = canvas;
 *         canvas->save();    // balanced each time updateHandle is called
 *         return true;
 *     }
 *
 *     void updateHandle(Handle hndl, const SkMatrix& ctm, const SkIRect& clip) override {
 *         SkCanvas* canvas = (SkCanvas*)hndl;
 *         canvas->restore();
 *         canvas->save();
 *         canvas->clipRect(SkRect::Make(clip));
 *         canvas->concat(ctm);
 *     }
 * }
 * ```
 */
public open class SkiaAllocator public constructor() : SkRasterHandleAllocator() {
  /**
   * C++ original:
   * ```cpp
   * bool allocHandle(const SkImageInfo& info, Rec* rec) override {
   *         sk_sp<SkSurface> surface = SkSurfaces::Raster(info);
   *         if (!surface) {
   *             return false;
   *         }
   *         SkCanvas* canvas = surface->getCanvas();
   *         SkPixmap pixmap;
   *         canvas->peekPixels(&pixmap);
   *
   *         rec->fReleaseProc = [](void* pixels, void* ctx){ SkSafeUnref((SkSurface*)ctx); };
   *         rec->fReleaseCtx = surface.release();
   *         rec->fPixels = pixmap.writable_addr();
   *         rec->fRowBytes = pixmap.rowBytes();
   *         rec->fHandle = canvas;
   *         canvas->save();    // balanced each time updateHandle is called
   *         return true;
   *     }
   * ```
   */
  public override fun allocHandle(info: SkImageInfo, rec: Rec?): Boolean {
    TODO("Implement allocHandle")
  }

  /**
   * C++ original:
   * ```cpp
   * void updateHandle(Handle hndl, const SkMatrix& ctm, const SkIRect& clip) override {
   *         SkCanvas* canvas = (SkCanvas*)hndl;
   *         canvas->restore();
   *         canvas->save();
   *         canvas->clipRect(SkRect::Make(clip));
   *         canvas->concat(ctm);
   *     }
   * ```
   */
  public override fun updateHandle(
    hndl: Handle,
    ctm: SkMatrix,
    clip: SkIRect,
  ) {
    TODO("Implement updateHandle")
  }
}
