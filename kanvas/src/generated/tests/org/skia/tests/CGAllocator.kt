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
 * class CGAllocator : public SkRasterHandleAllocator {
 * public:
 *     CGAllocator() {}
 *
 *     bool allocHandle(const SkImageInfo& info, Rec* rec) override {
 *         // let CG allocate the pixels
 *         CGContextRef cg = SkCreateCGContext(SkPixmap(info, nullptr, 0));
 *         if (!cg) {
 *             return false;
 *         }
 *         rec->fReleaseProc = [](void* pixels, void* ctx){ CGContextRelease((CGContextRef)ctx); };
 *         rec->fReleaseCtx = cg;
 *         rec->fPixels = CGBitmapContextGetData(cg);
 *         rec->fRowBytes = CGBitmapContextGetBytesPerRow(cg);
 *         rec->fHandle = cg;
 *         CGContextSaveGState(cg);    // balanced each time updateHandle is called
 *         return true;
 *     }
 *
 *     void updateHandle(Handle hndl, const SkMatrix& ctm, const SkIRect& clip) override {
 *         CGContextRef cg = (CGContextRef)hndl;
 *
 *         CGContextRestoreGState(cg);
 *         CGContextSaveGState(cg);
 *         CGContextClipToRect(cg, CGRectMake(clip.x(), clip.y(), clip.width(), clip.height()));
 *         CGContextConcatCTM(cg, matrix_to_transform(cg, ctm));
 *     }
 * }
 * ```
 */
public open class CGAllocator public constructor() : SkRasterHandleAllocator() {
  /**
   * C++ original:
   * ```cpp
   * bool allocHandle(const SkImageInfo& info, Rec* rec) override {
   *         // let CG allocate the pixels
   *         CGContextRef cg = SkCreateCGContext(SkPixmap(info, nullptr, 0));
   *         if (!cg) {
   *             return false;
   *         }
   *         rec->fReleaseProc = [](void* pixels, void* ctx){ CGContextRelease((CGContextRef)ctx); };
   *         rec->fReleaseCtx = cg;
   *         rec->fPixels = CGBitmapContextGetData(cg);
   *         rec->fRowBytes = CGBitmapContextGetBytesPerRow(cg);
   *         rec->fHandle = cg;
   *         CGContextSaveGState(cg);    // balanced each time updateHandle is called
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
   *         CGContextRef cg = (CGContextRef)hndl;
   *
   *         CGContextRestoreGState(cg);
   *         CGContextSaveGState(cg);
   *         CGContextClipToRect(cg, CGRectMake(clip.x(), clip.y(), clip.width(), clip.height()));
   *         CGContextConcatCTM(cg, matrix_to_transform(cg, ctm));
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

public typealias MyAllocator = CGAllocator
