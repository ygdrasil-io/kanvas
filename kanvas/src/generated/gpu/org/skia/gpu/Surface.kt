package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.core.SkSurfaceBase
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPixmap
import org.skia.foundation.SkSp
import org.skia.foundation.SkSurfaceProps
import org.skia.foundation.SkYUVColorSpace
import org.skia.math.SkIRect
import org.skia.math.SkISize
import undefined.ContentChangeMode

/**
 * C++ original:
 * ```cpp
 * class Surface final : public SkSurface_Base {
 * public:
 *     // Convenience factory to create a Device, instantiate its target proxy and return as a Surface.
 *     static sk_sp<Surface> Make(Recorder* recorder,
 *                                const SkImageInfo& info,
 *                                std::string_view label,
 *                                Budgeted budgeted,
 *                                Mipmapped mipmapped = Mipmapped::kNo,
 *                                SkBackingFit backingFit = SkBackingFit::kExact,
 *                                const SkSurfaceProps* props = nullptr) {
 *         return Make(recorder, info, std::move(label), budgeted, mipmapped, backingFit, props,
 *                     LoadOp::kClear, /*registerWithRecorder=*/true);
 *     }
 *     // Make a surface that is not registered with the provided recorder. This surface should be
 *     // short-lived and it must be flushed manually for its draw commands to be recorded. Most
 *     // scratch surfaces will be budgeted, but if the underlying texture is being returned as a
 *     // client-owned image, that may not be the case.
 *     static sk_sp<Surface> MakeScratch(Recorder* recorder,
 *                                       const SkImageInfo& info,
 *                                       std::string_view label,
 *                                       Budgeted budgeted = Budgeted::kYes,
 *                                       Mipmapped mipmapped = Mipmapped::kNo,
 *                                       SkBackingFit backingFit = SkBackingFit::kApprox) {
 *         return Make(recorder, info, std::move(label), budgeted, mipmapped, backingFit,
 *                     /*props=*/nullptr, LoadOp::kDiscard, /*registerWithRecorder=*/false);
 *     }
 *
 *     Surface(sk_sp<Device>);
 *     ~Surface() override;
 *
 *     // From SkSurface.h
 *     SkImageInfo imageInfo() const override;
 *
 *     // From SkSurface_Base.h
 *     SkSurface_Base::Type type() const override { return SkSurface_Base::Type::kGraphite; }
 *
 *     Recorder* onGetRecorder() const override;
 *     SkRecorder* onGetBaseRecorder() const override;
 *     SkCanvas* onNewCanvas() override;
 *     sk_sp<SkSurface> onNewSurface(const SkImageInfo&) override;
 *     sk_sp<SkImage> onNewImageSnapshot(const SkIRect* subset) override;
 *     sk_sp<SkImage> onMakeTemporaryImage() override;
 *     void onWritePixels(const SkPixmap&, int x, int y) override;
 *     void onAsyncRescaleAndReadPixels(const SkImageInfo& info,
 *                                      SkIRect srcRect,
 *                                      RescaleGamma rescaleGamma,
 *                                      RescaleMode rescaleMode,
 *                                      ReadPixelsCallback callback,
 *                                      ReadPixelsContext context) override;
 *     void onAsyncRescaleAndReadPixelsYUV420(SkYUVColorSpace yuvColorSpace,
 *                                            bool readAlpha,
 *                                            sk_sp<SkColorSpace> dstColorSpace,
 *                                            SkIRect srcRect,
 *                                            SkISize dstSize,
 *                                            RescaleGamma rescaleGamma,
 *                                            RescaleMode,
 *                                            ReadPixelsCallback callback,
 *                                            ReadPixelsContext context) override;
 *     bool onCopyOnWrite(ContentChangeMode) override;
 *     sk_sp<const SkCapabilities> onCapabilities() override;
 *
 *     TextureProxyView readSurfaceView() const;
 *     sk_sp<Image> asImage() const;
 *     sk_sp<Image> makeImageCopy(const SkIRect* subset, Mipmapped) const;
 *     TextureProxy* backingTextureProxy() const;
 *
 *     void flushToDrawContext(DrawContext*);
 *
 * private:
 *     // Regular and scratch surfaces differ by initial clear and if they are registered or not,
 *     // otherwise are constructed the same.
 *     static sk_sp<Surface> Make(Recorder* recorder,
 *                                const SkImageInfo&,
 *                                std::string_view label,
 *                                Budgeted,
 *                                Mipmapped,
 *                                SkBackingFit,
 *                                const SkSurfaceProps* props,
 *                                LoadOp initialLoadOp,
 *                                bool registerWithRecorder);
 *
 *     sk_sp<Device> fDevice;
 *     sk_sp<Image>  fImageView; // the image object returned by asImage()
 *
 *     friend void Flush(SkSurface*);
 * }
 * ```
 */
public class Surface public constructor(
  device: SkSp<Device>,
) : SkSurfaceBase(TODO(), TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * Surface(sk_sp<Device>)
   * ```
   */
  public var skSp: Surface = TODO("Initialize skSp")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<Device> fDevice
   * ```
   */
  private var fDevice: Int = TODO("Initialize fDevice")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<Image>  fImageView
   * ```
   */
  private var fImageView: Int = TODO("Initialize fImageView")

  /**
   * C++ original:
   * ```cpp
   * SkImageInfo Surface::imageInfo() const {
   *     return fDevice->imageInfo();
   * }
   * ```
   */
  public override fun imageInfo(): Int {
    TODO("Implement imageInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSurface_Base::Type type() const override { return SkSurface_Base::Type::kGraphite; }
   * ```
   */
  public override fun type(): Int {
    TODO("Implement type")
  }

  /**
   * C++ original:
   * ```cpp
   * Recorder* Surface::onGetRecorder() const { return fDevice->recorder(); }
   * ```
   */
  public override fun onGetRecorder(): Recorder {
    TODO("Implement onGetRecorder")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRecorder* Surface::onGetBaseRecorder() const { return fDevice->recorder(); }
   * ```
   */
  public override fun onGetBaseRecorder(): Int {
    TODO("Implement onGetBaseRecorder")
  }

  /**
   * C++ original:
   * ```cpp
   * SkCanvas* Surface::onNewCanvas() { return new SkCanvas(fDevice); }
   * ```
   */
  public override fun onNewCanvas(): Int {
    TODO("Implement onNewCanvas")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkSurface> Surface::onNewSurface(const SkImageInfo& ii) {
   *     return fDevice->makeSurface(ii, this->props());
   * }
   * ```
   */
  public override fun onNewSurface(ii: SkImageInfo): Int {
    TODO("Implement onNewSurface")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> Surface::onNewImageSnapshot(const SkIRect* subset) {
   *     return this->makeImageCopy(subset, fDevice->target()->mipmapped());
   * }
   * ```
   */
  public override fun onNewImageSnapshot(subset: SkIRect?): Int {
    TODO("Implement onNewImageSnapshot")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> Surface::onMakeTemporaryImage() {
   *     if (this->hasCachedImage()) {
   *         SKGPU_LOG_W("Intermingling makeImageSnapshot and makeTemporaryImage calls may produce "
   *                     "unexpected results. Please use either the old _or_ new API.");
   *     }
   *     return this->asImage();
   * }
   * ```
   */
  public override fun onMakeTemporaryImage(): Int {
    TODO("Implement onMakeTemporaryImage")
  }

  /**
   * C++ original:
   * ```cpp
   * void Surface::onWritePixels(const SkPixmap& pixmap, int x, int y) {
   *     fDevice->writePixels(pixmap, x, y);
   * }
   * ```
   */
  public override fun onWritePixels(
    pixmap: SkPixmap,
    x: Int,
    y: Int,
  ) {
    TODO("Implement onWritePixels")
  }

  /**
   * C++ original:
   * ```cpp
   * void Surface::onAsyncRescaleAndReadPixels(const SkImageInfo& info,
   *                                           SkIRect srcRect,
   *                                           RescaleGamma rescaleGamma,
   *                                           RescaleMode rescaleMode,
   *                                           ReadPixelsCallback callback,
   *                                           ReadPixelsContext context) {
   *     // Not supported for Graphite. Use Context::asyncRescaleAndReadPixels instead.
   *     callback(context, nullptr);
   * }
   * ```
   */
  public override fun onAsyncRescaleAndReadPixels(
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
   * void Surface::onAsyncRescaleAndReadPixelsYUV420(SkYUVColorSpace yuvColorSpace,
   *                                                 bool readAlpha,
   *                                                 sk_sp<SkColorSpace> dstColorSpace,
   *                                                 SkIRect srcRect,
   *                                                 SkISize dstSize,
   *                                                 RescaleGamma rescaleGamma,
   *                                                 RescaleMode rescaleMode,
   *                                                 ReadPixelsCallback callback,
   *                                                 ReadPixelsContext context) {
   *     // Not supported for Graphite. Use Context::asyncRescaleAndReadPixelsYUV420 instead.
   *     callback(context, nullptr);
   * }
   * ```
   */
  public override fun onAsyncRescaleAndReadPixelsYUV420(
    yuvColorSpace: SkYUVColorSpace,
    readAlpha: Boolean,
    dstColorSpace: SkSp<SkColorSpace>,
    srcRect: SkIRect,
    dstSize: SkISize,
    rescaleGamma: RescaleGamma,
    rescaleMode: RescaleMode,
    callback: ReadPixelsCallback,
    context: ReadPixelsContext,
  ) {
    TODO("Implement onAsyncRescaleAndReadPixelsYUV420")
  }

  /**
   * C++ original:
   * ```cpp
   * bool Surface::onCopyOnWrite(ContentChangeMode) { return true; }
   * ```
   */
  public override fun onCopyOnWrite(param0: ContentChangeMode): Boolean {
    TODO("Implement onCopyOnWrite")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<const SkCapabilities> Surface::onCapabilities() {
   *     return fDevice->recorder()->priv().caps()->capabilities();
   * }
   * ```
   */
  public override fun onCapabilities(): Int {
    TODO("Implement onCapabilities")
  }

  /**
   * C++ original:
   * ```cpp
   * TextureProxyView Surface::readSurfaceView() const {
   *     return fDevice->readSurfaceView();
   * }
   * ```
   */
  public fun readSurfaceView(): Int {
    TODO("Implement readSurfaceView")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<Image> Surface::asImage() const {
   *     if (this->hasCachedImage()) {
   *         SKGPU_LOG_W("Intermingling makeImageSnapshot and asImage calls may produce "
   *                     "unexpected results. Please use either the old _or_ new API.");
   *     }
   *     return fImageView;
   * }
   * ```
   */
  public fun asImage(): Int {
    TODO("Implement asImage")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<Image> Surface::makeImageCopy(const SkIRect* subset, Mipmapped mipmapped) const {
   *     if (this->hasCachedImage()) {
   *         SKGPU_LOG_W("Intermingling makeImageSnapshot and asImage calls may produce "
   *                     "unexpected results. Please use either the old _or_ new API.");
   *     }
   *
   *     SkIRect srcRect = subset ? *subset : SkIRect::MakeSize(this->imageInfo().dimensions());
   *     // NOTE: Must copy through fDevice and not fImageView if the surface's texture is not sampleable
   *     return fDevice->makeImageCopy(srcRect, Budgeted::kNo, mipmapped, SkBackingFit::kExact);
   * }
   * ```
   */
  public fun makeImageCopy(subset: SkIRect?, mipmapped: Mipmapped): Int {
    TODO("Implement makeImageCopy")
  }

  /**
   * C++ original:
   * ```cpp
   * TextureProxy* Surface::backingTextureProxy() const { return fDevice->target(); }
   * ```
   */
  public fun backingTextureProxy(): TextureProxy {
    TODO("Implement backingTextureProxy")
  }

  /**
   * C++ original:
   * ```cpp
   * void Surface::flushToDrawContext(DrawContext* drawContext) {
   *     this->fDevice->flushPendingWork(drawContext);
   * }
   * ```
   */
  public fun flushToDrawContext(drawContext: DrawContext?) {
    TODO("Implement flushToDrawContext")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<Surface> Make(Recorder* recorder,
     *                                const SkImageInfo& info,
     *                                std::string_view label,
     *                                Budgeted budgeted,
     *                                Mipmapped mipmapped = Mipmapped::kNo,
     *                                SkBackingFit backingFit = SkBackingFit::kExact,
     *                                const SkSurfaceProps* props = nullptr) {
     *         return Make(recorder, info, std::move(label), budgeted, mipmapped, backingFit, props,
     *                     LoadOp::kClear, /*registerWithRecorder=*/true);
     *     }
     * ```
     */
    public fun make(
      recorder: Recorder?,
      info: SkImageInfo,
      label: String,
      budgeted: Budgeted,
      mipmapped: Mipmapped = TODO(),
      backingFit: SkBackingFit = TODO(),
      props: SkSurfaceProps? = TODO(),
    ): Int {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<Surface> MakeScratch(Recorder* recorder,
     *                                       const SkImageInfo& info,
     *                                       std::string_view label,
     *                                       Budgeted budgeted = Budgeted::kYes,
     *                                       Mipmapped mipmapped = Mipmapped::kNo,
     *                                       SkBackingFit backingFit = SkBackingFit::kApprox) {
     *         return Make(recorder, info, std::move(label), budgeted, mipmapped, backingFit,
     *                     /*props=*/nullptr, LoadOp::kDiscard, /*registerWithRecorder=*/false);
     *     }
     * ```
     */
    public fun makeScratch(
      recorder: Recorder?,
      info: SkImageInfo,
      label: String,
      budgeted: Budgeted = TODO(),
      mipmapped: Mipmapped = TODO(),
      backingFit: SkBackingFit = TODO(),
    ): Int {
      TODO("Implement makeScratch")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<Surface> Surface::Make(Recorder* recorder,
     *                              const SkImageInfo& info,
     *                              std::string_view label,
     *                              Budgeted budgeted,
     *                              Mipmapped mipmapped,
     *                              SkBackingFit backingFit,
     *                              const SkSurfaceProps* props,
     *                              LoadOp initialLoadOp,
     *                              bool registerWithRecorder) {
     *     sk_sp<Device> device = Device::Make(recorder,
     *                                         info,
     *                                         budgeted,
     *                                         mipmapped,
     *                                         backingFit,
     *                                         SkSurfacePropsCopyOrDefault(props),
     *                                         initialLoadOp,
     *                                         std::move(label),
     *                                         registerWithRecorder);
     *     if (!device) {
     *         return nullptr;
     *     }
     *     // A non-budgeted surface should be fully instantiated before we return it
     *     // to the client.
     *     SkASSERT(budgeted == Budgeted::kYes || device->target()->isInstantiated());
     *     return sk_make_sp<Surface>(std::move(device));
     * }
     * ```
     */
    public fun make(
      recorder: Recorder?,
      info: SkImageInfo,
      label: String,
      budgeted: Budgeted,
      mipmapped: Mipmapped,
      backingFit: SkBackingFit,
      props: SkSurfaceProps?,
      initialLoadOp: LoadOp,
      registerWithRecorder: Boolean,
    ): Int {
      TODO("Implement make")
    }
  }
}
