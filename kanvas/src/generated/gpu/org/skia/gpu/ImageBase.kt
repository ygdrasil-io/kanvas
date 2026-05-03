package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.ULong
import kotlin.Unit
import org.skia.core.SkImageBase
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkRecorder
import org.skia.foundation.SkSp
import org.skia.foundation.SkYUVColorSpace
import org.skia.gpu.ganesh.GrDirectContext
import org.skia.math.SkIRect
import org.skia.math.SkISize
import undefined.CachingHint
import undefined.RequiredProperties

/**
 * C++ original:
 * ```cpp
 * class Image_Base : public SkImage_Base {
 * public:
 *     ~Image_Base() override;
 *
 *     // Must be called at the time of recording an action that reads from the image, be it a draw
 *     // or a copy operation. `drawContext` can be null if the "use" is scoped by a draw.
 *     void notifyInUse(Recorder*, DrawContext* drawContext) const;
 *
 *     // Returns true if this image is linked to a device that may render their shared texture(s).
 *     bool isDynamic() const;
 *
 *     // Always copy this image, even if 'subset' and mipmapping match this image exactly.
 *     // The base implementation performs all copies as draws.
 *     virtual sk_sp<Image> copyImage(Recorder*,
 *                                    const SkIRect& subset,
 *                                    Budgeted,
 *                                    Mipmapped,
 *                                    SkBackingFit,
 *                                    std::string_view label) const;
 *
 *     // From SkImage.h
 *     bool isValid(SkRecorder* recorder) const final {
 *         return recorder && recorder->type() == SkRecorder::Type::kGraphite;
 *     }
 *
 *     // From SkImage_Base.h
 *     sk_sp<SkImage> onMakeSubset(SkRecorder*, const SkIRect&, RequiredProperties) const final;
 *     sk_sp<SkImage> makeColorTypeAndColorSpace(SkRecorder*,
 *                                               SkColorType targetCT,
 *                                               sk_sp<SkColorSpace> targetCS,
 *                                               RequiredProperties) const final;
 *
 *     // No-ops for Ganesh APIs
 *     bool onReadPixels(GrDirectContext*,
 *                       const SkImageInfo& dstInfo,
 *                       void* dstPixels,
 *                       size_t dstRowBytes,
 *                       int srcX,
 *                       int srcY,
 *                       CachingHint) const final {
 *         return false;
 *     }
 *
 *     bool getROPixels(GrDirectContext*, SkBitmap*, CachingHint = kAllow_CachingHint) const final {
 *         return false;
 *     }
 *
 *     sk_sp<SkSurface> onMakeSurface(SkRecorder*, const SkImageInfo&) const final;
 *
 *     void onAsyncRescaleAndReadPixels(const SkImageInfo&,
 *                                      SkIRect srcRect,
 *                                      RescaleGamma,
 *                                      RescaleMode,
 *                                      ReadPixelsCallback,
 *                                      ReadPixelsContext) const override;
 *
 *     void onAsyncRescaleAndReadPixelsYUV420(SkYUVColorSpace,
 *                                            bool readAlpha,
 *                                            sk_sp<SkColorSpace>,
 *                                            SkIRect srcRect,
 *                                            SkISize dstSize,
 *                                            RescaleGamma,
 *                                            RescaleMode,
 *                                            ReadPixelsCallback,
 *                                            ReadPixelsContext) const override;
 *
 * protected:
 *     Image_Base(const SkImageInfo& info, uint32_t uniqueID);
 *
 *     // If the passed-in image is linked with Devices that modify its texture, copy the links to
 *     // this Image. This is used when a new Image is created that shares the same texture proxy as
 *     // a dynamic image. This can only be called before the Image has been returned from a factory.
 *     void linkDevices(const Image_Base*);
 *     // Link this image to the Device that can write to their shared texture proxy, so that when the
 *     // image is sampled in a draw, any pending work from the Device is automatically flushed. This
 *     // can only be called before the Image has been returned from a factory function.
 *     void linkDevice(sk_sp<Device>);
 *
 * private:
 *     // Devices are flushed in notifyImageInUse(). If a linked device is uniquely held by the image
 *     // or if it's marked as immutable, it will be unlinked (allowing it to be destroyed eventually).
 *     // If all linked devices are removed, this array will become empty. Other than initialization,
 *     // this array cannot transition from an empty state to having linked devices, so while it's
 *     // empty no locking is required.
 *     mutable skia_private::STArray<1, sk_sp<Device>> fLinkedDevices SK_GUARDED_BY(fDeviceLinkLock);
 *     mutable SkSpinlock fDeviceLinkLock;
 * }
 * ```
 */
public open class ImageBase public constructor(
  info: SkImageInfo,
  uniqueID: UInt,
) : SkImageBase() {
  /**
   * C++ original:
   * ```cpp
   * mutable skia_private::STArray<1, sk_sp<Device>> fLinkedDevices
   * ```
   */
  private var fLinkedDevices: Int = TODO("Initialize fLinkedDevices")

  /**
   * C++ original:
   * ```cpp
   * mutable SkSpinlock fDeviceLinkLock
   * ```
   */
  private var fDeviceLinkLock: Int = TODO("Initialize fDeviceLinkLock")

  /**
   * C++ original:
   * ```cpp
   * void Image_Base::notifyInUse(Recorder* recorder, DrawContext* drawContext) const {
   *     SkASSERT(recorder);
   *
   *     // The ref counts stored on each linked device are thread safe, but the Image's sk_sp's that
   *     // track the refs its responsible for are *not* thread safe. Use a spin lock since the majority
   *     // of device-linked images will be used only on the Recorder's thread. Since it should be
   *     // uncontended, the empty check is also done inside the lock vs. a double-checked locking
   *     // pattern that is non-trivial to ensure correctness in C++.
   *     SkAutoSpinlock lock{fDeviceLinkLock};
   *
   *     if (!fLinkedDevices.empty()) {
   *         int emptyCount = 0;
   *         for (sk_sp<Device>& device : fLinkedDevices) {
   *             if (!device || device->notifyInUse(recorder, drawContext)) {
   *                 // Already unlinked or notifyInUse() signals the device doesn't need to be linked
   *                 // anymore. reset() is a no-op if device is already holding null.
   *                 device.reset();
   *                 emptyCount++;
   *             }
   *         }
   *
   *         if (emptyCount == fLinkedDevices.size()) {
   *             fLinkedDevices.clear();
   *         }
   *     }
   * }
   * ```
   */
  public fun notifyInUse(recorder: Recorder?, drawContext: DrawContext?) {
    TODO("Implement notifyInUse")
  }

  /**
   * C++ original:
   * ```cpp
   * bool Image_Base::isDynamic() const {
   *     SkAutoSpinlock lock{fDeviceLinkLock};
   *     int emptyCount = 0;
   *     if (!fLinkedDevices.empty()) {
   *         for (sk_sp<Device>& device : fLinkedDevices) {
   *             if (!device || !device->recorder() || device->unique()) {
   *                 device.reset();
   *                 emptyCount++;
   *             }
   *         }
   *         if (emptyCount == fLinkedDevices.size()) {
   *             fLinkedDevices.clear();
   *             emptyCount = 0;
   *         }
   *     }
   *
   *     return emptyCount > 0;
   * }
   * ```
   */
  public fun isDynamic(): Boolean {
    TODO("Implement isDynamic")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<Image> Image_Base::copyImage(Recorder* recorder,
   *                                    const SkIRect& subset,
   *                                    Budgeted budgeted,
   *                                    Mipmapped mipmapped,
   *                                    SkBackingFit backingFit,
   *                                    std::string_view label) const {
   *     return CopyAsDraw(recorder,
   *                       /*drawContext=*/nullptr, this, subset, this->imageInfo().colorInfo(),
   *                       budgeted, mipmapped, backingFit, std::move(label));
   * }
   * ```
   */
  public open fun copyImage(
    recorder: Recorder?,
    subset: SkIRect,
    budgeted: Budgeted,
    mipmapped: Mipmapped,
    backingFit: SkBackingFit,
    label: String,
  ): Int {
    TODO("Implement copyImage")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isValid(SkRecorder* recorder) const final {
   *         return recorder && recorder->type() == SkRecorder::Type::kGraphite;
   *     }
   * ```
   */
  public fun isValid(recorder: SkRecorder?): Boolean {
    TODO("Implement isValid")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> Image_Base::onMakeSubset(SkRecorder* recorder,
   *                                         const SkIRect& subset,
   *                                         RequiredProperties requiredProps) const {
   *     auto gRecorder = AsGraphiteRecorder(recorder);
   *     if (!gRecorder) {
   *         return nullptr;
   *     }
   *     // optimization : return self if the subset == our bounds and requirements met and the image's
   *     // texture is immutable
   *     if (this->bounds() == subset &&
   *         (!requiredProps.fMipmapped || this->hasMipmaps()) &&
   *         !this->isDynamic()) {
   *         return sk_ref_sp(this);
   *     }
   *
   *     TextureProxy* proxy = get_base_proxy_for_label(this);
   *     SkASSERT(proxy);
   *     std::string label = proxy->label();
   *     if (label.empty()) {
   *         label = "ImageSubsetTexture";
   *     } else {
   *         label += "_Subset";
   *     }
   *
   *     // The copied image is not considered budgeted because this is a client-invoked API and they
   *     // will own the image.
   *     return this->copyImage(gRecorder,
   *                            subset,
   *                            Budgeted::kNo,
   *                            requiredProps.fMipmapped ? Mipmapped::kYes : Mipmapped::kNo,
   *                            SkBackingFit::kExact,
   *                            label);
   * }
   * ```
   */
  public fun onMakeSubset(
    recorder: SkRecorder?,
    subset: SkIRect,
    requiredProps: RequiredProperties,
  ): Int {
    TODO("Implement onMakeSubset")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> Image_Base::makeColorTypeAndColorSpace(SkRecorder* recorder,
   *                                                       SkColorType targetCT,
   *                                                       sk_sp<SkColorSpace> targetCS,
   *                                                       RequiredProperties requiredProps) const {
   *     auto gRecorder = AsGraphiteRecorder(recorder);
   *     if (!gRecorder) {
   *         return nullptr;
   *     }
   *
   *     SkColorInfo dstColorInfo{targetCT, this->alphaType(), std::move(targetCS)};
   *     // optimization : return self if there's no color type/space change and the image's texture
   *     // is immutable
   *     if (this->imageInfo().colorInfo() == dstColorInfo && !this->isDynamic()) {
   *         return sk_ref_sp(this);
   *     }
   *
   *     TextureProxy* proxy = get_base_proxy_for_label(this);
   *     SkASSERT(proxy);
   *     std::string label = proxy->label();
   *     if (label.empty()) {
   *         label = "ImageMakeCTandCSTexture";
   *     } else {
   *         label += "_CTandCSConversion";
   *     }
   *
   *     // Use CopyAsDraw directly to perform the color space changes. The copied image is not
   *     // considered budgeted because this is a client-invoked API and they will own the image.
   *     return CopyAsDraw(gRecorder,
   *                       /*drawContext=*/nullptr,
   *                       this,
   *                       this->bounds(),
   *                       dstColorInfo,
   *                       Budgeted::kNo,
   *                       requiredProps.fMipmapped ? Mipmapped::kYes : Mipmapped::kNo,
   *                       SkBackingFit::kExact,
   *                       label);
   * }
   * ```
   */
  public fun makeColorTypeAndColorSpace(
    recorder: SkRecorder?,
    targetCT: SkColorType,
    targetCS: SkSp<SkColorSpace>,
    requiredProps: RequiredProperties,
  ): Int {
    TODO("Implement makeColorTypeAndColorSpace")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onReadPixels(GrDirectContext*,
   *                       const SkImageInfo& dstInfo,
   *                       void* dstPixels,
   *                       size_t dstRowBytes,
   *                       int srcX,
   *                       int srcY,
   *                       CachingHint) const final {
   *         return false;
   *     }
   * ```
   */
  public fun onReadPixels(
    param0: GrDirectContext?,
    dstInfo: SkImageInfo,
    dstPixels: Unit?,
    dstRowBytes: ULong,
    srcX: Int,
    srcY: Int,
    param6: CachingHint,
  ): Boolean {
    TODO("Implement onReadPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * bool getROPixels(GrDirectContext*, SkBitmap*, CachingHint = kAllow_CachingHint) const final {
   *         return false;
   *     }
   * ```
   */
  public fun getROPixels(
    param0: GrDirectContext?,
    param1: SkBitmap?,
    param2: CachingHint = TODO(),
  ): Boolean {
    TODO("Implement getROPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkSurface> Image_Base::onMakeSurface(SkRecorder* recorder, const SkImageInfo& info) const {
   *     auto gRecorder = AsGraphiteRecorder(recorder);
   *     if (!gRecorder) {
   *         return nullptr;
   *     }
   *     return SkSurfaces::RenderTarget(gRecorder, info);
   * }
   * ```
   */
  public fun onMakeSurface(recorder: SkRecorder?, info: SkImageInfo): Int {
    TODO("Implement onMakeSurface")
  }

  /**
   * C++ original:
   * ```cpp
   * void Image_Base::onAsyncRescaleAndReadPixels(const SkImageInfo& info,
   *                                              SkIRect srcRect,
   *                                              RescaleGamma rescaleGamma,
   *                                              RescaleMode rescaleMode,
   *                                              ReadPixelsCallback callback,
   *                                              ReadPixelsContext context) const {
   *     SKGPU_LOG_W("Cannot use Ganesh async API with Graphite-backed image, use API on Context");
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
   * void Image_Base::onAsyncRescaleAndReadPixelsYUV420(SkYUVColorSpace yuvColorSpace,
   *                                                    bool readAlpha,
   *                                                    sk_sp<SkColorSpace> dstColorSpace,
   *                                                    const SkIRect srcRect,
   *                                                    const SkISize dstSize,
   *                                                    RescaleGamma rescaleGamma,
   *                                                    RescaleMode rescaleMode,
   *                                                    ReadPixelsCallback callback,
   *                                                    ReadPixelsContext context) const {
   *     SKGPU_LOG_W("Cannot use Ganesh async API with Graphite-backed image, use API on Context");
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
   * void Image_Base::linkDevices(const Image_Base* other) {
   *     SkASSERT(other);
   *
   *     SkAutoSpinlock lock{other->fDeviceLinkLock};
   *     for (const auto& device : other->fLinkedDevices) {
   *         this->linkDevice(device);
   *     }
   * }
   * ```
   */
  protected fun linkDevices(other: ImageBase?) {
    TODO("Implement linkDevices")
  }

  /**
   * C++ original:
   * ```cpp
   * void Image_Base::linkDevice(sk_sp<Device> device) {
   *     // Technically this lock isn't needed since this is only called before the Image is returned to
   *     // user code that could expose it to multiple threads. But this quiets threading warnings and
   *     // should be uncontested.
   *     SkAutoSpinlock lock{fDeviceLinkLock};
   *     fLinkedDevices.push_back(std::move(device));
   * }
   * ```
   */
  protected fun linkDevice(device: SkSp<Device>) {
    TODO("Implement linkDevice")
  }
}
