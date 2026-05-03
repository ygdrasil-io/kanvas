package org.skia.core

import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkSp
import org.skia.math.SkIRect
import org.skia.math.SkScalar
import undefined.ContentChangeMode

/**
 * C++ original:
 * ```cpp
 * class SkSurface_Raster : public SkSurface_Base {
 * public:
 *     SkSurface_Raster(const SkImageInfo&, void*, size_t rb,
 *                      void (*releaseProc)(void* pixels, void* context), void* context,
 *                      const SkSurfaceProps*);
 *     SkSurface_Raster(const SkImageInfo& info, sk_sp<SkPixelRef>, const SkSurfaceProps*);
 *
 *     SkSurface_Raster(skcpu::RecorderImpl* recorder,
 *                      const SkImageInfo&,
 *                      void* pixels,
 *                      size_t rowBytes,
 *                      SkSurfaces::PixelsReleaseProc releaseProc,
 *                      void* context,
 *                      const SkSurfaceProps*);
 *     SkSurface_Raster(skcpu::RecorderImpl* recorder,
 *                      const SkImageInfo&,
 *                      sk_sp<SkPixelRef>,
 *                      const SkSurfaceProps*);
 *
 *     // From SkSurface.h
 *     SkImageInfo imageInfo() const override { return fBitmap.info(); }
 *
 *     // From SkSurface_Base.h
 *     SkSurface_Base::Type type() const override { return SkSurface_Base::Type::kRaster; }
 *
 *     SkCanvas* onNewCanvas() override;
 *     sk_sp<SkSurface> onNewSurface(const SkImageInfo&) override;
 *     sk_sp<SkImage> onNewImageSnapshot(const SkIRect* subset) override;
 *     void onWritePixels(const SkPixmap&, int x, int y) override;
 *     void onDraw(SkCanvas*, SkScalar, SkScalar, const SkSamplingOptions&, const SkPaint*) override;
 *     bool onCopyOnWrite(ContentChangeMode) override;
 *     void onRestoreBackingMutability() override;
 *     sk_sp<const SkCapabilities> onCapabilities() override;
 *     SkRecorder* onGetBaseRecorder() const override;
 *
 * private:
 *     skcpu::RecorderImpl* fRecorder;
 *     SkBitmap fBitmap;
 *     bool fWeOwnThePixels;
 * }
 * ```
 */
public open class SkSurfaceRaster public constructor(
  param0: SkImageInfo,
  param1: Unit,
  rb: ULong,
  releaseProc: Unit,
  context: Unit?,
  param5: SkSurfaceProps,
) : SkSurfaceBase() {
  /**
   * C++ original:
   * ```cpp
   * skcpu::RecorderImpl* fRecorder
   * ```
   */
  private var fRecorder: RecorderImpl? = TODO("Initialize fRecorder")

  /**
   * C++ original:
   * ```cpp
   * SkBitmap fBitmap
   * ```
   */
  private var fBitmap: SkBitmap = TODO("Initialize fBitmap")

  /**
   * C++ original:
   * ```cpp
   * bool fWeOwnThePixels
   * ```
   */
  private var fWeOwnThePixels: Boolean = TODO("Initialize fWeOwnThePixels")

  /**
   * C++ original:
   * ```cpp
   * SkSurface_Raster(const SkImageInfo&, void*, size_t rb,
   *                      void (*releaseProc)(void* pixels, void* context), void* context,
   *                      const SkSurfaceProps*)
   * ```
   */
  public constructor(
    info: SkImageInfo,
    pr: SkSp<SkPixelRef>,
    props: SkSurfaceProps?,
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSurface_Raster::SkSurface_Raster(const SkImageInfo& info,
   *                                    sk_sp<SkPixelRef> pr,
   *                                    const SkSurfaceProps* props)
   *         : SkSurface_Raster(asRRI(skcpu::Recorder::TODO()), info, pr, props) {}
   * ```
   */
  public constructor(
    recorder: RecorderImpl?,
    param1: SkImageInfo,
    pixels: Unit?,
    rowBytes: ULong,
    releaseProc: PixelsReleaseProc,
    context: Unit?,
    param6: SkSurfaceProps,
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSurface_Raster(skcpu::RecorderImpl* recorder,
   *                      const SkImageInfo&,
   *                      void* pixels,
   *                      size_t rowBytes,
   *                      SkSurfaces::PixelsReleaseProc releaseProc,
   *                      void* context,
   *                      const SkSurfaceProps*)
   * ```
   */
  public constructor(
    recorder: RecorderImpl?,
    info: SkImageInfo,
    pr: SkSp<SkPixelRef>,
    props: SkSurfaceProps?,
  ) : super(TODO(), TODO(), TODO()) {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSurface_Raster::SkSurface_Raster(skcpu::RecorderImpl* recorder,
   *                                    const SkImageInfo& info,
   *                                    sk_sp<SkPixelRef> pr,
   *                                    const SkSurfaceProps* props)
   *         : SkSurface_Base(pr->width(), pr->height(), props), fRecorder(recorder) {
   *     fBitmap.setInfo(info, pr->rowBytes());
   *     fBitmap.setPixelRef(std::move(pr), 0, 0);
   *     fWeOwnThePixels = true;
   * }
   * ```
   */
  public constructor(
    info: SkImageInfo,
    pixels: Unit?,
    rb: ULong,
    releaseProc: Unit,
    context: Unit?,
    props: SkSurfaceProps?,
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSurface_Raster::SkSurface_Raster(const SkImageInfo& info,
   *                                    void* pixels,
   *                                    size_t rb,
   *                                    void (*releaseProc)(void* pixels, void* context),
   *                                    void* context,
   *                                    const SkSurfaceProps* props)
   *         : SkSurface_Raster(
   *                   asRRI(skcpu::Recorder::TODO()), info, pixels, rb, releaseProc, context, props) {}
   * ```
   */
  public constructor(
    recorder: RecorderImpl?,
    info: SkImageInfo,
    pixels: Unit?,
    rowBytes: ULong,
    releaseProc: Unit,
    context: Unit?,
    props: SkSurfaceProps?,
  ) : super(TODO(), TODO()) {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkImageInfo imageInfo() const override { return fBitmap.info(); }
   * ```
   */
  public override fun imageInfo(): SkImageInfo {
    TODO("Implement imageInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSurface_Base::Type type() const override { return SkSurface_Base::Type::kRaster; }
   * ```
   */
  public override fun type(): SkSurface_Base.Type {
    TODO("Implement type")
  }

  /**
   * C++ original:
   * ```cpp
   * SkCanvas* SkSurface_Raster::onNewCanvas() {
   *     SkASSERT(fRecorder);
   *     return new SkCanvas(sk_make_sp<SkBitmapDevice>(fRecorder, fBitmap, this->props()));
   * }
   * ```
   */
  public override fun onNewCanvas(): SkCanvas {
    TODO("Implement onNewCanvas")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkSurface> SkSurface_Raster::onNewSurface(const SkImageInfo& info) {
   *     SkASSERT(fRecorder);
   *     return fRecorder->makeBitmapSurface(info, 0, &this->props());
   * }
   * ```
   */
  public override fun onNewSurface(info: SkImageInfo): SkSp<SkSurface> {
    TODO("Implement onNewSurface")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> SkSurface_Raster::onNewImageSnapshot(const SkIRect* subset) {
   *     if (subset) {
   *         SkASSERT(SkIRect::MakeWH(fBitmap.width(), fBitmap.height()).contains(*subset));
   *         SkBitmap dst;
   *         dst.allocPixels(fBitmap.info().makeDimensions(subset->size()));
   *         SkAssertResult(fBitmap.readPixels(dst.pixmap(), subset->left(), subset->top()));
   *         dst.setImmutable(); // key, so MakeFromBitmap doesn't make a copy of the buffer
   *         return dst.asImage();
   *     }
   *
   *     SkCopyPixelsMode cpm = kIfMutable_SkCopyPixelsMode;
   *     if (fWeOwnThePixels) {
   *         // SkImage_raster requires these pixels are immutable for its full lifetime.
   *         // We'll undo this via onRestoreBackingMutability() if we can avoid the COW.
   *         if (SkPixelRef* pr = fBitmap.pixelRef()) {
   *             pr->setTemporarilyImmutable();
   *         }
   *     } else {
   *         cpm = kAlways_SkCopyPixelsMode;
   *     }
   *
   *     // Our pixels are in memory, so read access on the snapshot SkImage could be cheap.
   *     // Lock the shared pixel ref to ensure peekPixels() is usable.
   *     return SkMakeImageFromRasterBitmap(fBitmap, cpm);
   * }
   * ```
   */
  public override fun onNewImageSnapshot(subset: SkIRect?): SkSp<SkImage> {
    TODO("Implement onNewImageSnapshot")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSurface_Raster::onWritePixels(const SkPixmap& src, int x, int y) {
   *     fBitmap.writePixels(src, x, y);
   * }
   * ```
   */
  public override fun onWritePixels(
    src: SkPixmap,
    x: Int,
    y: Int,
  ) {
    TODO("Implement onWritePixels")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSurface_Raster::onDraw(SkCanvas* canvas, SkScalar x, SkScalar y,
   *                               const SkSamplingOptions& sampling, const SkPaint* paint) {
   *     canvas->drawImage(fBitmap.asImage().get(), x, y, sampling, paint);
   * }
   * ```
   */
  public override fun onDraw(
    canvas: SkCanvas?,
    x: SkScalar,
    y: SkScalar,
    sampling: SkSamplingOptions,
    paint: SkPaint?,
  ) {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSurface_Raster::onCopyOnWrite(ContentChangeMode mode) {
   *     // are we sharing pixelrefs with the image?
   *     sk_sp<SkImage> cached(this->refCachedImage());
   *     SkASSERT(cached);
   *     if (SkBitmapImageGetPixelRef(cached.get()) == fBitmap.pixelRef()) {
   *         SkASSERT(fWeOwnThePixels);
   *         if (kDiscard_ContentChangeMode == mode) {
   *             if (!fBitmap.tryAllocPixels()) {
   *                 return false;
   *             }
   *         } else {
   *             SkBitmap prev(fBitmap);
   *             if (!fBitmap.tryAllocPixels()) {
   *                 return false;
   *             }
   *             SkASSERT(prev.info() == fBitmap.info());
   *             SkASSERT(prev.rowBytes() == fBitmap.rowBytes());
   *             memcpy(fBitmap.getPixels(), prev.getPixels(), fBitmap.computeByteSize());
   *         }
   *
   *         // Now fBitmap is a deep copy of itself (and therefore different from
   *         // what is being used by the image. Next we update the canvas to use
   *         // this as its backend, so we can't modify the image's pixels anymore.
   *         SkASSERT(this->getCachedCanvas());
   *         SkBitmapDevice* bmDev = static_cast<SkBitmapDevice*>(this->getCachedCanvas()->rootDevice());
   *         bmDev->replaceBitmapBackendForRasterSurface(fBitmap);
   *     }
   *     return true;
   * }
   * ```
   */
  public override fun onCopyOnWrite(mode: ContentChangeMode): Boolean {
    TODO("Implement onCopyOnWrite")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSurface_Raster::onRestoreBackingMutability() {
   *     SkASSERT(!this->hasCachedImage());  // Shouldn't be any snapshots out there.
   *     if (SkPixelRef* pr = fBitmap.pixelRef()) {
   *         pr->restoreMutability();
   *     }
   * }
   * ```
   */
  public override fun onRestoreBackingMutability() {
    TODO("Implement onRestoreBackingMutability")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<const SkCapabilities> SkSurface_Raster::onCapabilities() {
   *     return SkCapabilities::RasterBackend();
   * }
   * ```
   */
  public override fun onCapabilities(): SkSp<SkCapabilities> {
    TODO("Implement onCapabilities")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRecorder* SkSurface_Raster::onGetBaseRecorder() const {
   *     return fRecorder;
   * }
   * ```
   */
  public override fun onGetBaseRecorder(): SkRecorder {
    TODO("Implement onGetBaseRecorder")
  }
}
