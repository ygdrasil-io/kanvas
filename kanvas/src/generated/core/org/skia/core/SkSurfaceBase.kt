package org.skia.core

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPixmap
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkSp
import org.skia.foundation.SkYUVColorSpace
import org.skia.gpu.ReadPixelsCallback
import org.skia.gpu.ReadPixelsContext
import org.skia.gpu.Recorder
import org.skia.gpu.RescaleGamma
import org.skia.gpu.RescaleMode
import org.skia.gpu.ganesh.GrBackendSemaphore
import org.skia.gpu.ganesh.GrBackendTexture
import org.skia.gpu.ganesh.GrRecordingContext
import org.skia.gpu.ganesh.GrSurfaceOrigin
import org.skia.gpu.ganesh.TextureReleaseProc
import org.skia.math.SkIRect
import org.skia.math.SkISize
import org.skia.math.SkScalar
import org.skia.ports.ReleaseContext
import org.skia.utils.GrSurfaceCharacterization
import undefined.ContentChangeMode

/**
 * C++ original:
 * ```cpp
 * class SkSurface_Base : public SkSurface {
 * public:
 *     SkSurface_Base(int width, int height, const SkSurfaceProps*);
 *     SkSurface_Base(const SkImageInfo&, const SkSurfaceProps*);
 *     ~SkSurface_Base() override;
 *
 *     // From SkSurface.h
 *     bool replaceBackendTexture(const GrBackendTexture&,
 *                                GrSurfaceOrigin,
 *                                ContentChangeMode,
 *                                TextureReleaseProc,
 *                                ReleaseContext) override {
 *         return false;
 *     }
 *
 *     enum class Type {
 *         kNull,     // intentionally associating 0 with a null canvas
 *         kGanesh,
 *         kGraphite,
 *         kRaster,
 *     };
 *
 *     // TODO(kjlubick) Android directly subclasses SkSurface_Base for tests, so we
 *     // cannot make this a pure virtual. They seem to want a surface that is spy-able
 *     // or mockable, so maybe we should provide something like that.
 *     virtual Type type() const { return Type::kNull; }
 *
 *     // True for surfaces instantiated by pixels in CPU memory
 *     bool isRasterBacked() const { return this->type() == Type::kRaster; }
 *     // True for surfaces instantiated by Ganesh in GPU memory
 *     bool isGaneshBacked() const { return this->type() == Type::kGanesh; }
 *     // True for surfaces instantiated by Graphite in GPU memory
 *     bool isGraphiteBacked() const { return this->type() == Type::kGraphite; }
 *
 *     virtual GrRecordingContext* onGetRecordingContext() const;
 *     virtual skgpu::graphite::Recorder* onGetRecorder() const;
 *     virtual SkRecorder* onGetBaseRecorder() const;
 *
 *     /**
 *      *  Allocate a canvas that will draw into this surface. We will cache this
 *      *  canvas, to return the same object to the caller multiple times. We
 *      *  take ownership, and will call unref() on the canvas when we go out of
 *      *  scope.
 *      */
 *     virtual SkCanvas* onNewCanvas() = 0;
 *
 *     virtual sk_sp<SkSurface> onNewSurface(const SkImageInfo&) = 0;
 *
 *     /**
 *      *  Allocate an SkImage that represents the current contents of the surface.
 *      *  This needs to be able to outlive the surface itself (if need be), and
 *      *  must faithfully represent the current contents, even if the surface
 *      *  is changed after this called (e.g. it is drawn to via its canvas).
 *      *
 *      *  If a subset is specified, the the impl must make a copy, rather than try to wait
 *      *  on copy-on-write.
 *      */
 *     virtual sk_sp<SkImage> onNewImageSnapshot(const SkIRect* subset = nullptr) { return nullptr; }
 *
 *     virtual sk_sp<SkImage> onMakeTemporaryImage() { return this->makeImageSnapshot(); }
 *
 *     virtual void onWritePixels(const SkPixmap&, int x, int y) = 0;
 *
 *     /**
 *      * Default implementation does a rescale/read and then calls the callback.
 *      */
 *     virtual void onAsyncRescaleAndReadPixels(const SkImageInfo&,
 *                                              const SkIRect srcRect,
 *                                              RescaleGamma,
 *                                              RescaleMode,
 *                                              ReadPixelsCallback,
 *                                              ReadPixelsContext);
 *     /**
 *      * Default implementation does a rescale/read/yuv conversion and then calls the callback.
 *      */
 *     virtual void onAsyncRescaleAndReadPixelsYUV420(SkYUVColorSpace,
 *                                                    bool readAlpha,
 *                                                    sk_sp<SkColorSpace> dstColorSpace,
 *                                                    SkIRect srcRect,
 *                                                    SkISize dstSize,
 *                                                    RescaleGamma,
 *                                                    RescaleMode,
 *                                                    ReadPixelsCallback,
 *                                                    ReadPixelsContext);
 *
 *     /**
 *      *  Default implementation:
 *      *
 *      *  image = this->newImageSnapshot();
 *      *  if (image) {
 *      *      image->draw(canvas, ...);
 *      *      image->unref();
 *      *  }
 *      */
 *     virtual void onDraw(SkCanvas*, SkScalar x, SkScalar y, const SkSamplingOptions&,const SkPaint*);
 *
 *     /**
 *      * Called as a performance hint when the Surface is allowed to make it's contents
 *      * undefined.
 *      */
 *     virtual void onDiscard() {}
 *
 *     /**
 *      *  If the surface is about to change, we call this so that our subclass
 *      *  can optionally fork their backend (copy-on-write) in case it was
 *      *  being shared with the cachedImage.
 *      *
 *      *  Returns false if the backing cannot be un-shared.
 *      */
 *     [[nodiscard]] virtual bool onCopyOnWrite(ContentChangeMode) = 0;
 *
 *     /**
 *      *  Signal the surface to remind its backing store that it's mutable again.
 *      *  Called only when we _didn't_ copy-on-write; we assume the copies start mutable.
 *      */
 *     virtual void onRestoreBackingMutability() {}
 *
 *     /**
 *      * Caused the current backend 3D API to wait on the passed in semaphores before executing new
 *      * commands on the gpu. Any previously submitting commands will not be blocked by these
 *      * semaphores.
 *      */
 *     virtual bool onWait(int numSemaphores, const GrBackendSemaphore* waitSemaphores,
 *                         bool deleteSemaphoresAfterWait) {
 *         return false;
 *     }
 *
 *     virtual bool onCharacterize(GrSurfaceCharacterization*) const { return false; }
 *     virtual bool onIsCompatible(const GrSurfaceCharacterization&) const { return false; }
 *
 *     // TODO: Remove this (make it pure virtual) after updating Android (which has a class derived
 *     // from SkSurface_Base).
 *     virtual sk_sp<const SkCapabilities> onCapabilities();
 *
 *     /**
 *      * If capturing, signals to the capture manager and capture canvas to break off the recording
 *      * SkPicture into a new SkPicture.
 *      */
 *     void createCaptureBreakpoint();
 *
 *     inline SkCanvas* getCachedCanvas();
 *     inline sk_sp<SkImage> refCachedImage();
 *
 *     bool hasCachedImage() const { return fCachedImage != nullptr; }
 *
 *     // called by SkSurface to compute a new genID
 *     uint32_t newGenerationID();
 *
 * private:
 *     // fCachedCanvas is the raw pointer to the canvas that is returned to the client.
 *     // It can point to either the base canvas or a capture canvas wrapper.
 *     SkCanvas* fCachedCanvas = nullptr;
 *     // SkSurface_Base must always own the base canvas. During capture, SkCaptureManager owns any
 *     // wrapping capture canvas that fCachedCanvas may point to.
 *     std::unique_ptr<SkCanvas> fOwnedBaseCanvas = nullptr;
 *     sk_sp<SkImage>            fCachedImage  = nullptr;
 *
 *     // Returns false if drawing should not take place (allocation failure).
 *     [[nodiscard]] bool aboutToDraw(ContentChangeMode mode);
 *
 *     // Returns true if there is an outstanding image-snapshot, indicating that a call to aboutToDraw
 *     // would trigger a copy-on-write.
 *     bool outstandingImageSnapshot() const;
 *
 *     friend class SkCanvas;
 *     friend class SkSurface;
 * }
 * ```
 */
public abstract class SkSurfaceBase public constructor(
  width: Int,
  height: Int,
  props: SkSurfaceProps?,
) : SkSurface(TODO(), TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SkCanvas* fCachedCanvas = nullptr
   * ```
   */
  private var fCachedCanvas: SkCanvas? = TODO("Initialize fCachedCanvas")

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkCanvas> fOwnedBaseCanvas
   * ```
   */
  private var fOwnedBaseCanvas: Int = TODO("Initialize fOwnedBaseCanvas")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage>            fCachedImage
   * ```
   */
  private var fCachedImage: SkSp<SkImage> = TODO("Initialize fCachedImage")

  /**
   * C++ original:
   * ```cpp
   * SkSurface_Base::SkSurface_Base(int width, int height, const SkSurfaceProps* props)
   *         : SkSurface(width, height, props) {}
   * ```
   */
  public constructor(info: SkImageInfo, props: SkSurfaceProps?) : this(TODO(), TODO()) {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * bool replaceBackendTexture(const GrBackendTexture&,
   *                                GrSurfaceOrigin,
   *                                ContentChangeMode,
   *                                TextureReleaseProc,
   *                                ReleaseContext) override {
   *         return false;
   *     }
   * ```
   */
  public override fun replaceBackendTexture(
    param0: GrBackendTexture,
    param1: GrSurfaceOrigin,
    param2: ContentChangeMode,
    param3: TextureReleaseProc,
    param4: ReleaseContext,
  ): Boolean {
    TODO("Implement replaceBackendTexture")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual Type type() const { return Type::kNull; }
   * ```
   */
  public open fun type(): Type {
    TODO("Implement type")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isRasterBacked() const { return this->type() == Type::kRaster; }
   * ```
   */
  public fun isRasterBacked(): Boolean {
    TODO("Implement isRasterBacked")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isGaneshBacked() const { return this->type() == Type::kGanesh; }
   * ```
   */
  public fun isGaneshBacked(): Boolean {
    TODO("Implement isGaneshBacked")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isGraphiteBacked() const { return this->type() == Type::kGraphite; }
   * ```
   */
  public fun isGraphiteBacked(): Boolean {
    TODO("Implement isGraphiteBacked")
  }

  /**
   * C++ original:
   * ```cpp
   * GrRecordingContext* SkSurface_Base::onGetRecordingContext() const { return nullptr; }
   * ```
   */
  public open fun onGetRecordingContext(): GrRecordingContext {
    TODO("Implement onGetRecordingContext")
  }

  /**
   * C++ original:
   * ```cpp
   * skgpu::graphite::Recorder* SkSurface_Base::onGetRecorder() const { return nullptr; }
   * ```
   */
  public open fun onGetRecorder(): Recorder {
    TODO("Implement onGetRecorder")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRecorder* SkSurface_Base::onGetBaseRecorder() const { return nullptr; }
   * ```
   */
  public open fun onGetBaseRecorder(): SkRecorder {
    TODO("Implement onGetBaseRecorder")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual SkCanvas* onNewCanvas() = 0
   * ```
   */
  public abstract fun onNewCanvas(): SkCanvas

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<SkSurface> onNewSurface(const SkImageInfo&) = 0
   * ```
   */
  public abstract fun onNewSurface(param0: SkImageInfo): SkSp<SkSurface>

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<SkImage> onNewImageSnapshot(const SkIRect* subset = nullptr) { return nullptr; }
   * ```
   */
  public open fun onNewImageSnapshot(subset: SkIRect? = null): SkSp<SkImage> {
    TODO("Implement onNewImageSnapshot")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<SkImage> onMakeTemporaryImage() { return this->makeImageSnapshot(); }
   * ```
   */
  public open fun onMakeTemporaryImage(): SkSp<SkImage> {
    TODO("Implement onMakeTemporaryImage")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void onWritePixels(const SkPixmap&, int x, int y) = 0
   * ```
   */
  public abstract fun onWritePixels(
    param0: SkPixmap,
    x: Int,
    y: Int,
  )

  /**
   * C++ original:
   * ```cpp
   * void SkSurface_Base::onAsyncRescaleAndReadPixels(const SkImageInfo& info,
   *                                                  SkIRect origSrcRect,
   *                                                  SkSurface::RescaleGamma rescaleGamma,
   *                                                  RescaleMode rescaleMode,
   *                                                  SkSurface::ReadPixelsCallback callback,
   *                                                  SkSurface::ReadPixelsContext context) {
   *     SkBitmap src;
   *     SkPixmap peek;
   *     SkIRect srcRect;
   *     if (this->peekPixels(&peek)) {
   *         src.installPixels(peek);
   *         srcRect = origSrcRect;
   *     } else {
   *         src.setInfo(this->imageInfo().makeDimensions(origSrcRect.size()));
   *         src.allocPixels();
   *         if (!this->readPixels(src, origSrcRect.x(), origSrcRect.y())) {
   *             callback(context, nullptr);
   *             return;
   *         }
   *         srcRect = SkIRect::MakeSize(src.dimensions());
   *     }
   *     return SkRescaleAndReadPixels(src, info, srcRect, rescaleGamma, rescaleMode, callback,
   *                                   context);
   * }
   * ```
   */
  public open fun onAsyncRescaleAndReadPixels(
    info: SkImageInfo,
    srcRect: SkIRect,
    rescaleGamma: RescaleGamma,
    rescaleMode: RescaleMode,
    callback: ReadPixelsCallback,
    context: ReadPixelsContext,
  ) {
    TODO("Implement onAsyncRescaleAndReadPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSurface_Base::onAsyncRescaleAndReadPixelsYUV420(
   *         SkYUVColorSpace yuvColorSpace, bool readAlpha, sk_sp<SkColorSpace> dstColorSpace,
   *         SkIRect srcRect, SkISize dstSize, RescaleGamma rescaleGamma, RescaleMode,
   *         ReadPixelsCallback callback, ReadPixelsContext context) {
   *     // TODO: Call non-YUV asyncRescaleAndReadPixels and then make our callback convert to YUV and
   *     // call client's callback.
   *     callback(context, nullptr);
   * }
   * ```
   */
  public open fun onAsyncRescaleAndReadPixelsYUV420(
    yuvColorSpace: SkYUVColorSpace,
    readAlpha: Boolean,
    dstColorSpace: SkSp<SkColorSpace>,
    srcRect: SkIRect,
    dstSize: SkISize,
    rescaleGamma: RescaleGamma,
    param6: RescaleMode,
    callback: ReadPixelsCallback,
    context: ReadPixelsContext,
  ) {
    TODO("Implement onAsyncRescaleAndReadPixelsYUV420")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSurface_Base::onDraw(SkCanvas* canvas, SkScalar x, SkScalar y,
   *                             const SkSamplingOptions& sampling, const SkPaint* paint) {
   *     auto image = this->makeTemporaryImage();
   *     if (image) {
   *         canvas->drawImage(image.get(), x, y, sampling, paint);
   *     }
   * }
   * ```
   */
  public open fun onDraw(
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
   * virtual void onDiscard() {}
   * ```
   */
  public open fun onDiscard() {
    TODO("Implement onDiscard")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool onCopyOnWrite(ContentChangeMode) = 0
   * ```
   */
  public abstract fun onCopyOnWrite(param0: ContentChangeMode): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual void onRestoreBackingMutability() {}
   * ```
   */
  public open fun onRestoreBackingMutability() {
    TODO("Implement onRestoreBackingMutability")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool onWait(int numSemaphores, const GrBackendSemaphore* waitSemaphores,
   *                         bool deleteSemaphoresAfterWait) {
   *         return false;
   *     }
   * ```
   */
  public open fun onWait(
    numSemaphores: Int,
    waitSemaphores: GrBackendSemaphore?,
    deleteSemaphoresAfterWait: Boolean,
  ): Boolean {
    TODO("Implement onWait")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool onCharacterize(GrSurfaceCharacterization*) const { return false; }
   * ```
   */
  public open fun onCharacterize(param0: GrSurfaceCharacterization?): Boolean {
    TODO("Implement onCharacterize")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool onIsCompatible(const GrSurfaceCharacterization&) const { return false; }
   * ```
   */
  public open fun onIsCompatible(param0: GrSurfaceCharacterization): Boolean {
    TODO("Implement onIsCompatible")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<const SkCapabilities> SkSurface_Base::onCapabilities() {
   *     return SkCapabilities::RasterBackend();
   * }
   * ```
   */
  public open fun onCapabilities(): SkSp<SkCapabilities> {
    TODO("Implement onCapabilities")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSurface_Base::createCaptureBreakpoint() {
   *     if (this->baseRecorder()) {
   *         this->baseRecorder()->createCaptureBreakpoint(this);
   *     }
   * }
   * ```
   */
  public fun createCaptureBreakpoint() {
    TODO("Implement createCaptureBreakpoint")
  }

  /**
   * C++ original:
   * ```cpp
   * SkCanvas* SkSurface_Base::getCachedCanvas() {
   *     if (nullptr == fCachedCanvas) {
   *         fOwnedBaseCanvas = std::unique_ptr<SkCanvas>(this->onNewCanvas());
   *
   *         if (this->baseRecorder()) {
   *             fCachedCanvas = this->baseRecorder()->makeCaptureCanvas(fOwnedBaseCanvas.get());
   *         }
   *         if (!fCachedCanvas) {
   *             fCachedCanvas = fOwnedBaseCanvas.get();
   *         }
   *         if (fCachedCanvas) {
   *             fCachedCanvas->setSurfaceBase(this);
   *         }
   *     }
   *     return fCachedCanvas;
   * }
   * ```
   */
  public fun getCachedCanvas(): SkCanvas {
    TODO("Implement getCachedCanvas")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> SkSurface_Base::refCachedImage() {
   *     if (fCachedImage) {
   *         return fCachedImage;
   *     }
   *     this->createCaptureBreakpoint();
   *
   *     fCachedImage = this->onNewImageSnapshot();
   *
   *     SkASSERT(!fCachedCanvas || fCachedCanvas->getSurfaceBase() == this);
   *     return fCachedImage;
   * }
   * ```
   */
  public fun refCachedImage(): SkSp<SkImage> {
    TODO("Implement refCachedImage")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hasCachedImage() const { return fCachedImage != nullptr; }
   * ```
   */
  public fun hasCachedImage(): Boolean {
    TODO("Implement hasCachedImage")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t SkSurface_Base::newGenerationID() {
   *     SkASSERT(!fCachedCanvas || fCachedCanvas->getSurfaceBase() == this);
   *     static std::atomic<uint32_t> nextID{1};
   *     return nextID.fetch_add(1, std::memory_order_relaxed);
   * }
   * ```
   */
  public fun newGenerationID(): Int {
    TODO("Implement newGenerationID")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSurface_Base::aboutToDraw(ContentChangeMode mode) {
   *     this->dirtyGenerationID();
   *
   *     SkASSERT(!fCachedCanvas || fCachedCanvas->getSurfaceBase() == this);
   *
   *     if (fCachedImage) {
   *         // the surface may need to fork its backend, if its sharing it with
   *         // the cached image. Note: we only call if there is an outstanding owner
   *         // on the image (besides us).
   *         bool unique = fCachedImage->unique();
   *         if (!unique) {
   *             if (!this->onCopyOnWrite(mode)) {
   *                 return false;
   *             }
   *         }
   *
   *         // regardless of copy-on-write, we must drop our cached image now, so
   *         // that the next request will get our new contents.
   *         fCachedImage.reset();
   *
   *         if (unique) {
   *             // Our content isn't held by any image now, so we can consider that content mutable.
   *             // Raster surfaces need to be told it's safe to consider its pixels mutable again.
   *             // We make this call after the ->unref() so the subclass can assert there are no images.
   *             this->onRestoreBackingMutability();
   *         }
   *     } else if (kDiscard_ContentChangeMode == mode) {
   *         this->onDiscard();
   *     }
   *     return true;
   * }
   * ```
   */
  private fun aboutToDraw(mode: ContentChangeMode): Boolean {
    TODO("Implement aboutToDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSurface_Base::outstandingImageSnapshot() const {
   *     return fCachedImage && !fCachedImage->unique();
   * }
   * ```
   */
  private fun outstandingImageSnapshot(): Boolean {
    TODO("Implement outstandingImageSnapshot")
  }

  public enum class Type {
    kNull,
    kGanesh,
    kGraphite,
    kRaster,
  }
}
