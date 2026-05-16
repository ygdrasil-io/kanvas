package org.skia.foundation

import kotlin.Char
import kotlin.Int
import kotlin.UInt
import kotlin.ULong
import kotlin.Unit
import org.skia.core.SkCanvas
import org.skia.gpu.ganesh.GrBackendApi
import org.skia.gpu.ganesh.GrBackendDrawableInfo
import org.skia.math.SkIRect
import org.skia.math.SkMatrix
import org.skia.math.SkRect
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SK_API SkDrawable : public SkFlattenable {
 * public:
 *     /**
 *      *  Draws into the specified content. The drawing sequence will be balanced upon return
 *      *  (i.e. the saveLevel() on the canvas will match what it was when draw() was called,
 *      *  and the current matrix and clip settings will not be changed.
 *      */
 *     void draw(SkCanvas*, const SkMatrix* = nullptr);
 *     void draw(SkCanvas*, SkScalar x, SkScalar y);
 *
 *     /**
 *      *  When using the GPU backend it is possible for a drawable to execute using the underlying 3D
 *      *  API rather than the SkCanvas API. It does so by creating a GpuDrawHandler. The GPU backend
 *      *  is deferred so the handler will be given access to the 3D API at the correct point in the
 *      *  drawing stream as the GPU backend flushes. Since the drawable may mutate, each time it is
 *      *  drawn to a GPU-backed canvas a new handler is snapped, representing the drawable's state at
 *      *  the time of the snap.
 *      *
 *      *  When the GPU backend flushes to the 3D API it will call the draw method on the
 *      *  GpuDrawHandler. At this time the drawable may add commands to the stream of GPU commands for
 *      *  the unerlying 3D API. The draw function takes a GrBackendDrawableInfo which contains
 *      *  information about the current state of 3D API which the caller must respect. See
 *      *  GrBackendDrawableInfo for more specific details on what information is sent and the
 *      *  requirements for different 3D APIs.
 *      *
 *      *  Additionaly there may be a slight delay from when the drawable adds its commands to when
 *      *  those commands are actually submitted to the GPU. Thus the drawable or GpuDrawHandler is
 *      *  required to keep any resources that are used by its added commands alive and valid until
 *      *  those commands are submitted to the GPU. The GpuDrawHandler will be kept alive and then
 *      *  deleted once the commands are submitted to the GPU. The dtor of the GpuDrawHandler is the
 *      *  signal to the drawable that the commands have all been submitted. Different 3D APIs may have
 *      *  additional requirements for certain resources which require waiting for the GPU to finish
 *      *  all work on those resources before reusing or deleting them. In this case, the drawable can
 *      *  use the dtor call of the GpuDrawHandler to add a fence to the GPU to track when the GPU work
 *      *  has completed.
 *      *
 *      *  Currently this is only supported for the GPU Vulkan backend.
 *      */
 *
 *     class GpuDrawHandler {
 *     public:
 *         virtual ~GpuDrawHandler() {}
 *
 *         virtual void draw(const GrBackendDrawableInfo&) {}
 *     };
 *
 *     /**
 *      * Snaps off a GpuDrawHandler to represent the state of the SkDrawable at the time the snap is
 *      * called. This is used for executing GPU backend specific draws intermixed with normal Skia GPU
 *      * draws. The GPU API, which will be used for the draw, as well as the full matrix, device clip
 *      * bounds and imageInfo of the target buffer are passed in as inputs.
 *      */
 *     std::unique_ptr<GpuDrawHandler> snapGpuDrawHandler(GrBackendApi backendApi,
 *                                                        const SkMatrix& matrix,
 *                                                        const SkIRect& clipBounds,
 *                                                        const SkImageInfo& bufferInfo) {
 *         return this->onSnapGpuDrawHandler(backendApi, matrix, clipBounds, bufferInfo);
 *     }
 *
 *     /**
 *      * Returns an SkPicture with the contents of this SkDrawable.
 *      */
 *     sk_sp<SkPicture> makePictureSnapshot();
 *
 *     /**
 *      *  Return a unique value for this instance. If two calls to this return the same value,
 *      *  it is presumed that calling the draw() method will render the same thing as well.
 *      *
 *      *  Subclasses that change their state should call notifyDrawingChanged() to ensure that
 *      *  a new value will be returned the next time it is called.
 *      */
 *     uint32_t getGenerationID();
 *
 *     /**
 *      *  Return the (conservative) bounds of what the drawable will draw. If the drawable can
 *      *  change what it draws (e.g. animation or in response to some external change), then this
 *      *  must return a bounds that is always valid for all possible states.
 *      */
 *     SkRect getBounds();
 *
 *     /**
 *      *  Return approximately how many bytes would be freed if this drawable is destroyed.
 *      *  The base implementation returns 0 to indicate that this is unknown.
 *      */
 *     size_t approximateBytesUsed();
 *
 *     /**
 *      *  Calling this invalidates the previous generation ID, and causes a new one to be computed
 *      *  the next time getGenerationID() is called. Typically this is called by the object itself,
 *      *  in response to its internal state changing.
 *      */
 *     void notifyDrawingChanged();
 *
 *     static SkFlattenable::Type GetFlattenableType() {
 *         return kSkDrawable_Type;
 *     }
 *
 *     SkFlattenable::Type getFlattenableType() const override {
 *         return kSkDrawable_Type;
 *     }
 *
 *     static sk_sp<SkDrawable> Deserialize(const void* data, size_t size,
 *                                           const SkDeserialProcs* procs = nullptr) {
 *         return sk_sp<SkDrawable>(static_cast<SkDrawable*>(
 *                                   SkFlattenable::Deserialize(
 *                                   kSkDrawable_Type, data, size, procs).release()));
 *     }
 *
 *     Factory getFactory() const override { return nullptr; }
 *     const char* getTypeName() const override { return nullptr; }
 *
 * protected:
 *     SkDrawable();
 *
 *     virtual SkRect onGetBounds() = 0;
 *     virtual size_t onApproximateBytesUsed();
 *     virtual void onDraw(SkCanvas*) = 0;
 *
 *     virtual std::unique_ptr<GpuDrawHandler> onSnapGpuDrawHandler(GrBackendApi, const SkMatrix&,
 *                                                                  const SkIRect& /*clipBounds*/,
 *                                                                  const SkImageInfo&) {
 *         return nullptr;
 *     }
 *
 *     // TODO: Delete this once Android gets updated to take the clipBounds version above.
 *     virtual std::unique_ptr<GpuDrawHandler> onSnapGpuDrawHandler(GrBackendApi, const SkMatrix&) {
 *         return nullptr;
 *     }
 *
 *     /**
 *      *  Default implementation calls onDraw() with a canvas that records into a picture. Subclasses
 *      *  may override if they have a more efficient way to return a picture for the current state
 *      *  of their drawable. Note: this picture must draw the same as what would be drawn from
 *      *  onDraw().
 *      */
 *     virtual sk_sp<SkPicture> onMakePictureSnapshot();
 *
 * private:
 *     int32_t fGenerationID;
 * }
 * ```
 */
public abstract class SkDrawable public constructor() : SkFlattenable() {
  /**
   * C++ original:
   * ```cpp
   * int32_t fGenerationID
   * ```
   */
  private var fGenerationID: Int = TODO("Initialize fGenerationID")

  /**
   * C++ original:
   * ```cpp
   * void SkDrawable::draw(SkCanvas* canvas, const SkMatrix* matrix) {
   *     SkAutoCanvasRestore acr(canvas, true);
   *     if (matrix) {
   *         canvas->concat(*matrix);
   *     }
   *     this->onDraw(canvas);
   *
   *     if ((false)) {
   *         draw_bbox(canvas, this->getBounds());
   *     }
   * }
   * ```
   */
  public fun draw(canvas: SkCanvas?, matrix: SkMatrix? = TODO()) {
    TODO("Implement draw")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDrawable::draw(SkCanvas* canvas, SkScalar x, SkScalar y) {
   *     SkMatrix matrix = SkMatrix::Translate(x, y);
   *     this->draw(canvas, &matrix);
   * }
   * ```
   */
  public fun draw(
    canvas: SkCanvas?,
    x: SkScalar,
    y: SkScalar,
  ) {
    TODO("Implement draw")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<GpuDrawHandler> snapGpuDrawHandler(GrBackendApi backendApi,
   *                                                        const SkMatrix& matrix,
   *                                                        const SkIRect& clipBounds,
   *                                                        const SkImageInfo& bufferInfo) {
   *         return this->onSnapGpuDrawHandler(backendApi, matrix, clipBounds, bufferInfo);
   *     }
   * ```
   */
  public fun snapGpuDrawHandler(
    backendApi: GrBackendApi,
    matrix: SkMatrix,
    clipBounds: SkIRect,
    bufferInfo: SkImageInfo,
  ): GpuDrawHandler? {
    TODO("Implement snapGpuDrawHandler")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkPicture> SkDrawable::makePictureSnapshot() {
   *     return this->onMakePictureSnapshot();
   * }
   * ```
   */
  public fun makePictureSnapshot(): Int {
    TODO("Implement makePictureSnapshot")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t SkDrawable::getGenerationID() {
   *     if (0 == fGenerationID) {
   *         fGenerationID = next_generation_id();
   *     }
   *     return fGenerationID;
   * }
   * ```
   */
  public fun getGenerationID(): UInt {
    TODO("Implement getGenerationID")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect SkDrawable::getBounds() {
   *     return this->onGetBounds();
   * }
   * ```
   */
  public fun getBounds(): SkRect {
    TODO("Implement getBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkDrawable::approximateBytesUsed() {
   *     return this->onApproximateBytesUsed();
   * }
   * ```
   */
  public fun approximateBytesUsed(): ULong {
    TODO("Implement approximateBytesUsed")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDrawable::notifyDrawingChanged() {
   *     fGenerationID = 0;
   * }
   * ```
   */
  public fun notifyDrawingChanged() {
    TODO("Implement notifyDrawingChanged")
  }

  /**
   * C++ original:
   * ```cpp
   * SkFlattenable::Type getFlattenableType() const override {
   *         return kSkDrawable_Type;
   *     }
   * ```
   */
  public override fun getFlattenableType(): Int {
    TODO("Implement getFlattenableType")
  }

  /**
   * C++ original:
   * ```cpp
   * Factory getFactory() const override { return nullptr; }
   * ```
   */
  public override fun getFactory(): Int {
    TODO("Implement getFactory")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* getTypeName() const override { return nullptr; }
   * ```
   */
  public override fun getTypeName(): Char {
    TODO("Implement getTypeName")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual SkRect onGetBounds() = 0
   * ```
   */
  protected abstract fun onGetBounds(): SkRect

  /**
   * C++ original:
   * ```cpp
   * size_t SkDrawable::onApproximateBytesUsed() {
   *     return 0;
   * }
   * ```
   */
  protected open fun onApproximateBytesUsed(): ULong {
    TODO("Implement onApproximateBytesUsed")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void onDraw(SkCanvas*) = 0
   * ```
   */
  protected abstract fun onDraw(param0: SkCanvas?)

  /**
   * C++ original:
   * ```cpp
   * virtual std::unique_ptr<GpuDrawHandler> onSnapGpuDrawHandler(GrBackendApi, const SkMatrix&,
   *                                                                  const SkIRect& /*clipBounds*/,
   *                                                                  const SkImageInfo&) {
   *         return nullptr;
   *     }
   * ```
   */
  protected open fun onSnapGpuDrawHandler(
    param0: GrBackendApi,
    param1: SkMatrix,
    param2: Int,
    param3: SkImageInfo,
  ): GpuDrawHandler? {
    TODO("Implement onSnapGpuDrawHandler")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual std::unique_ptr<GpuDrawHandler> onSnapGpuDrawHandler(GrBackendApi, const SkMatrix&) {
   *         return nullptr;
   *     }
   * ```
   */
  protected open fun onSnapGpuDrawHandler(param0: GrBackendApi, param1: SkMatrix): GpuDrawHandler? {
    TODO("Implement onSnapGpuDrawHandler")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkPicture> SkDrawable::onMakePictureSnapshot() {
   *     SkPictureRecorder recorder;
   *
   *     const SkRect bounds = this->getBounds();
   *     SkCanvas* canvas = recorder.beginRecording(bounds);
   *     this->draw(canvas);
   *     if ((false)) {
   *         draw_bbox(canvas, bounds);
   *     }
   *     return recorder.finishRecordingAsPicture();
   * }
   * ```
   */
  protected open fun onMakePictureSnapshot(): Int {
    TODO("Implement onMakePictureSnapshot")
  }

  public open class GpuDrawHandler {
    public open fun draw(param0: GrBackendDrawableInfo) {
      TODO("Implement draw")
    }
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static SkFlattenable::Type GetFlattenableType() {
     *         return kSkDrawable_Type;
     *     }
     * ```
     */
    public fun getFlattenableType(): Int {
      TODO("Implement getFlattenableType")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkDrawable> Deserialize(const void* data, size_t size,
     *                                           const SkDeserialProcs* procs = nullptr) {
     *         return sk_sp<SkDrawable>(static_cast<SkDrawable*>(
     *                                   SkFlattenable::Deserialize(
     *                                   kSkDrawable_Type, data, size, procs).release()));
     *     }
     * ```
     */
    public fun deserialize(
      `data`: Unit?,
      size: ULong,
      procs: SkDeserialProcs? = TODO(),
    ): Int {
      TODO("Implement deserialize")
    }
  }
}
