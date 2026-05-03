package org.skia.core

import kotlin.Boolean
import kotlin.Int
import kotlin.Unit
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkSurfaceProps
import org.skia.math.SkIRect
import org.skia.math.SkMatrix
import undefined.Handle

/**
 * C++ original:
 * ```cpp
 * class SK_API SkRasterHandleAllocator {
 * public:
 *     virtual ~SkRasterHandleAllocator() = default;
 *
 *     // The value that is returned to clients of the canvas that has this allocator installed.
 *     typedef void* Handle;
 *
 *     struct Rec {
 *         // When the allocation goes out of scope, this proc is called to free everything associated
 *         // with it: the pixels, the "handle", etc. This is passed the pixel address and fReleaseCtx.
 *         void    (*fReleaseProc)(void* pixels, void* ctx);
 *         void*   fReleaseCtx;    // context passed to fReleaseProc
 *         void*   fPixels;        // pixels for this allocation
 *         size_t  fRowBytes;      // rowbytes for these pixels
 *         Handle  fHandle;        // public handle returned by SkCanvas::accessTopRasterHandle()
 *     };
 *
 *     /**
 *      *  Given a requested info, allocate the corresponding pixels/rowbytes, and whatever handle
 *      *  is desired to give clients access to those pixels. The rec also contains a proc and context
 *      *  which will be called when this allocation goes out of scope.
 *      *
 *      *  e.g.
 *      *      when canvas->saveLayer() is called, the allocator will be called to allocate the pixels
 *      *      for the layer. When canvas->restore() is called, the fReleaseProc will be called.
 *      */
 *     virtual bool allocHandle(const SkImageInfo&, Rec*) = 0;
 *
 *     /**
 *      *  Clients access the handle for a given layer by calling SkCanvas::accessTopRasterHandle().
 *      *  To allow the handle to reflect the current matrix/clip in the canvs, updateHandle() is
 *      *  is called. The subclass is responsible to update the handle as it sees fit.
 *      */
 *     virtual void updateHandle(Handle, const SkMatrix&, const SkIRect&) = 0;
 *
 *     /**
 *      *  This creates a canvas which will use the allocator to manage pixel allocations, including
 *      *  all calls to saveLayer().
 *      *
 *      *  If rec is non-null, then it will be used as the base-layer of pixels/handle.
 *      *  If rec is null, then the allocator will be called for the base-layer as well.
 *      */
 *     static std::unique_ptr<SkCanvas> MakeCanvas(std::unique_ptr<SkRasterHandleAllocator>,
 *                                                 const SkImageInfo&, const Rec* rec = nullptr,
 *                                                 const SkSurfaceProps* props = nullptr);
 *
 * protected:
 *     SkRasterHandleAllocator() = default;
 *     SkRasterHandleAllocator(const SkRasterHandleAllocator&) = delete;
 *     SkRasterHandleAllocator& operator=(const SkRasterHandleAllocator&) = delete;
 *
 * private:
 *     friend class SkBitmapDevice;
 *
 *     Handle allocBitmap(const SkImageInfo&, SkBitmap*);
 * }
 * ```
 */
public abstract class SkRasterHandleAllocator public constructor() {
  /**
   * C++ original:
   * ```cpp
   * SkRasterHandleAllocator() = default
   * ```
   */
  public constructor(param0: SkRasterHandleAllocator) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool allocHandle(const SkImageInfo&, Rec*) = 0
   * ```
   */
  public abstract fun allocHandle(param0: SkImageInfo, param1: Rec?): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual void updateHandle(Handle, const SkMatrix&, const SkIRect&) = 0
   * ```
   */
  public abstract fun updateHandle(
    param0: SkRasterHandleAllocatorHandle,
    param1: SkMatrix,
    param2: SkIRect,
  )

  /**
   * C++ original:
   * ```cpp
   * SkRasterHandleAllocator& operator=(const SkRasterHandleAllocator&) = delete
   * ```
   */
  protected fun assign(param0: SkRasterHandleAllocator) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRasterHandleAllocator::Handle SkRasterHandleAllocator::allocBitmap(const SkImageInfo& info,
   *                                                                      SkBitmap* bm) {
   *     SkRasterHandleAllocator::Rec rec;
   *     if (!this->allocHandle(info, &rec) || !install(bm, info, rec)) {
   *         return nullptr;
   *     }
   *     return rec.fHandle;
   * }
   * ```
   */
  private fun allocBitmap(info: SkImageInfo, bm: SkBitmap?): SkRasterHandleAllocatorHandle {
    TODO("Implement allocBitmap")
  }

  public open class Rec public constructor(
    public var fReleaseProc: (Unit?, Unit?) -> Unit,
    public var fReleaseCtx: Unit?,
    public var fPixels: Unit?,
    public var fRowBytes: Int,
    public var fHandle: Handle,
  )

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static std::unique_ptr<SkCanvas> MakeCanvas(std::unique_ptr<SkRasterHandleAllocator>,
     *                                                 const SkImageInfo&, const Rec* rec = nullptr,
     *                                                 const SkSurfaceProps* props = nullptr)
     * ```
     */
    public fun makeCanvas(
      param0: SkRasterHandleAllocator?,
      param1: SkImageInfo,
      rec: Rec? = TODO(),
      props: SkSurfaceProps? = TODO(),
    ): Int {
      TODO("Implement makeCanvas")
    }
  }
}
