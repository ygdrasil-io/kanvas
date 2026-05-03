package org.skia.core

import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkData
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkMipmap
import org.skia.foundation.SkPixmap
import org.skia.foundation.SkSp
import org.skia.foundation.SkYUVColorSpace
import org.skia.gpu.GrImageContext
import org.skia.gpu.ReadPixelsCallback
import org.skia.gpu.ReadPixelsContext
import org.skia.gpu.RescaleGamma
import org.skia.gpu.RescaleMode
import org.skia.gpu.ganesh.GrDirectContext
import org.skia.math.SkIRect
import org.skia.math.SkISize
import undefined.CachingHint
import undefined.RequiredProperties

/**
 * C++ original:
 * ```cpp
 * class SkImage_Base : public SkImage {
 * public:
 *     ~SkImage_Base() override;
 *
 *     // From SkImage.h
 *     sk_sp<SkImage> makeColorSpace(SkRecorder*,
 *                                   sk_sp<SkColorSpace>,
 *                                   RequiredProperties) const override;
 *
 *     sk_sp<SkImage> makeSubset(SkRecorder*, const SkIRect&, RequiredProperties) const override;
 *     size_t textureSize() const override { return 0; }
 *
 *     // Methods that we want to use elsewhere in Skia, but not be a part of the public API.
 *     virtual bool onPeekPixels(SkPixmap*) const { return false; }
 *
 *     virtual const SkBitmap* onPeekBitmap() const { return nullptr; }
 *
 *     virtual bool onReadPixels(GrDirectContext*,
 *                               const SkImageInfo& dstInfo,
 *                               void* dstPixels,
 *                               size_t dstRowBytes,
 *                               int srcX,
 *                               int srcY,
 *                               CachingHint) const = 0;
 *
 *     virtual bool readPixelsGraphite(SkRecorder*, const SkPixmap& dst, int srcX, int srcY) const {
 *         return false;
 *     }
 *
 *     virtual bool onHasMipmaps() const = 0;
 *     virtual bool onIsProtected() const = 0;
 *
 *     virtual SkMipmap* onPeekMips() const { return nullptr; }
 *
 *     sk_sp<SkMipmap> refMips() const {
 *         return sk_ref_sp(this->onPeekMips());
 *     }
 *
 *     /**
 *      * Default implementation does a rescale/read and then calls the callback.
 *      */
 *     virtual void onAsyncRescaleAndReadPixels(const SkImageInfo&,
 *                                              SkIRect srcRect,
 *                                              RescaleGamma,
 *                                              RescaleMode,
 *                                              ReadPixelsCallback,
 *                                              ReadPixelsContext) const;
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
 *                                                    ReadPixelsContext) const;
 *
 *     virtual GrImageContext* context() const { return nullptr; }
 *
 *     /** this->context() try-casted to GrDirectContext. Useful for migrations – avoid otherwise! */
 *     virtual GrDirectContext* directContext() const { return nullptr; }
 *
 *     // If this image is the current cached image snapshot of a surface then this is called when the
 *     // surface is destroyed to indicate no further writes may happen to surface backing store.
 *     virtual void generatingSurfaceIsDeleted() {}
 *
 *     // return a read-only copy of the pixels. We promise to not modify them,
 *     // but only inspect them (or encode them).
 *     virtual bool getROPixels(GrDirectContext*, SkBitmap*,
 *                              CachingHint = kAllow_CachingHint) const = 0;
 *
 *     virtual sk_sp<SkImage> onMakeSubset(SkRecorder*, const SkIRect&, RequiredProperties) const = 0;
 *
 *     virtual sk_sp<const SkData> onRefEncoded() const { return nullptr; }
 *
 *     virtual bool onAsLegacyBitmap(GrDirectContext*, SkBitmap*) const;
 *
 *     // Create the surface used by makeScaled. If this is a GPU backed image, the surface
 *     // should be Ganesh or Graphite backed (as appropriate), otherwise this can raster backed.
 *     virtual sk_sp<SkSurface> onMakeSurface(SkRecorder*, const SkImageInfo&) const = 0;
 *
 *     enum class Type {
 *         kRaster,
 *         kRasterPinnable,
 *         kLazy,
 *         kLazyPicture,
 *         kLazyTexture,
 *         kGanesh,
 *         kGaneshYUVA,
 *         kGraphite,
 *         kGraphiteYUVA,
 *     };
 *
 *     virtual Type type() const = 0;
 *
 *     // True for picture-backed and codec-backed
 *     bool isLazyGenerated() const override {
 *         return this->type() == Type::kLazy || this->type() == Type::kLazyPicture ||
 *                this->type() == Type::kLazyTexture;
 *     }
 *
 *     bool isRasterBacked() const {
 *         return this->type() == Type::kRaster || this->type() == Type::kRasterPinnable;
 *     }
 *
 *     // True for images instantiated by Ganesh in GPU memory
 *     bool isGaneshBacked() const {
 *         return this->type() == Type::kGanesh || this->type() == Type::kGaneshYUVA;
 *     }
 *
 *     // True for images instantiated by Graphite in GPU memory
 *     bool isGraphiteBacked() const {
 *         return this->type() == Type::kGraphite || this->type() == Type::kGraphiteYUVA;
 *     }
 *
 *     bool isYUVA() const {
 *         return this->type() == Type::kGaneshYUVA || this->type() == Type::kGraphiteYUVA;
 *     }
 *
 *     bool isTextureBacked() const override {
 *         return this->isGaneshBacked() || this->isGraphiteBacked();
 *     }
 *
 *     // Call when this image is part of the key to a resourcecache entry. This allows the cache
 *     // to know automatically those entries can be purged when this SkImage deleted.
 *     virtual void notifyAddedToRasterCache() const {
 *         fAddedToRasterCache.store(true);
 *     }
 *
 *     virtual sk_sp<SkImage> onReinterpretColorSpace(sk_sp<SkColorSpace>) const = 0;
 *
 *     // on failure, returns nullptr
 *     // NOLINTNEXTLINE(performance-unnecessary-value-param)
 *     virtual sk_sp<SkImage> onMakeWithMipmaps(sk_sp<SkMipmap>) const {
 *         return nullptr;
 *     }
 *
 * protected:
 *     SkImage_Base(const SkImageInfo& info, uint32_t uniqueID);
 *
 * private:
 *     // Set true by caches when they cache content that's derived from the current pixels.
 *
 *     mutable std::atomic<bool> fAddedToRasterCache;
 * }
 * ```
 */
public abstract class SkImageBase public constructor(
  info: SkImageInfo,
  uniqueID: UInt,
) : SkImage(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * mutable std::atomic<bool> fAddedToRasterCache
   * ```
   */
  private var fAddedToRasterCache: Int = TODO("Initialize fAddedToRasterCache")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> SkImage_Base::makeColorSpace(SkRecorder* recorder,
   *                                             sk_sp<SkColorSpace> target,
   *                                             RequiredProperties props) const {
   *     return this->makeColorTypeAndColorSpace(recorder, this->colorType(), std::move(target), props);
   * }
   * ```
   */
  public override fun makeColorSpace(
    recorder: SkRecorder?,
    target: SkSp<SkColorSpace>,
    props: RequiredProperties,
  ): SkSp<SkImage> {
    TODO("Implement makeColorSpace")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> SkImage_Base::makeSubset(SkRecorder* recorder,
   *                                         const SkIRect& subset,
   *                                         RequiredProperties requiredProps) const {
   *     if (subset.isEmpty()) {
   *         return nullptr;
   *     }
   *
   *     const SkIRect bounds = SkIRect::MakeWH(this->width(), this->height());
   *     if (!bounds.contains(subset)) {
   *         return nullptr;
   *     }
   *
   *     return this->onMakeSubset(recorder, subset, requiredProps);
   * }
   * ```
   */
  public override fun makeSubset(
    recorder: SkRecorder?,
    subset: SkIRect,
    requiredProps: RequiredProperties,
  ): SkSp<SkImage> {
    TODO("Implement makeSubset")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t textureSize() const override { return 0; }
   * ```
   */
  public override fun textureSize(): ULong {
    TODO("Implement textureSize")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool onPeekPixels(SkPixmap*) const { return false; }
   * ```
   */
  public open fun onPeekPixels(param0: SkPixmap?): Boolean {
    TODO("Implement onPeekPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual const SkBitmap* onPeekBitmap() const { return nullptr; }
   * ```
   */
  public open fun onPeekBitmap(): SkBitmap {
    TODO("Implement onPeekBitmap")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool onReadPixels(GrDirectContext*,
   *                               const SkImageInfo& dstInfo,
   *                               void* dstPixels,
   *                               size_t dstRowBytes,
   *                               int srcX,
   *                               int srcY,
   *                               CachingHint) const = 0
   * ```
   */
  public abstract fun onReadPixels(
    param0: GrDirectContext?,
    dstInfo: SkImageInfo,
    dstPixels: Unit?,
    dstRowBytes: ULong,
    srcX: Int,
    srcY: Int,
    param6: CachingHint,
  ): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual bool readPixelsGraphite(SkRecorder*, const SkPixmap& dst, int srcX, int srcY) const {
   *         return false;
   *     }
   * ```
   */
  public open fun readPixelsGraphite(
    param0: SkRecorder?,
    dst: SkPixmap,
    srcX: Int,
    srcY: Int,
  ): Boolean {
    TODO("Implement readPixelsGraphite")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool onHasMipmaps() const = 0
   * ```
   */
  public abstract fun onHasMipmaps(): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual bool onIsProtected() const = 0
   * ```
   */
  public abstract fun onIsProtected(): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual SkMipmap* onPeekMips() const { return nullptr; }
   * ```
   */
  public open fun onPeekMips(): SkMipmap {
    TODO("Implement onPeekMips")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkMipmap> refMips() const {
   *         return sk_ref_sp(this->onPeekMips());
   *     }
   * ```
   */
  public fun refMips(): SkSp<SkMipmap> {
    TODO("Implement refMips")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkImage_Base::onAsyncRescaleAndReadPixels(const SkImageInfo& info,
   *                                                SkIRect origSrcRect,
   *                                                RescaleGamma rescaleGamma,
   *                                                RescaleMode rescaleMode,
   *                                                ReadPixelsCallback callback,
   *                                                ReadPixelsContext context) const {
   *     SkBitmap src;
   *     SkPixmap peek;
   *     SkIRect srcRect;
   *     if (this->peekPixels(&peek)) {
   *         src.installPixels(peek);
   *         srcRect = origSrcRect;
   *     } else {
   *         // Context TODO: Elevate GrDirectContext requirement to public API.
   *         auto dContext = as_IB(this)->directContext();
   *         src.setInfo(this->imageInfo().makeDimensions(origSrcRect.size()));
   *         src.allocPixels();
   *         if (!this->readPixels(dContext, src.pixmap(), origSrcRect.x(), origSrcRect.y())) {
   *             callback(context, nullptr);
   *             return;
   *         }
   *         srcRect = SkIRect::MakeSize(src.dimensions());
   *     }
   *     return SkRescaleAndReadPixels(src, info, srcRect, rescaleGamma, rescaleMode, callback, context);
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
   * void SkImage_Base::onAsyncRescaleAndReadPixelsYUV420(SkYUVColorSpace,
   *                                                      bool readAlpha,
   *                                                      sk_sp<SkColorSpace> dstColorSpace,
   *                                                      SkIRect srcRect,
   *                                                      SkISize dstSize,
   *                                                      RescaleGamma,
   *                                                      RescaleMode,
   *                                                      ReadPixelsCallback callback,
   *                                                      ReadPixelsContext context) const {
   *     // TODO: Call non-YUV asyncRescaleAndReadPixels and then make our callback convert to YUV and
   *     // call client's callback.
   *     callback(context, nullptr);
   * }
   * ```
   */
  public open fun onAsyncRescaleAndReadPixelsYUV420(
    param0: SkYUVColorSpace,
    readAlpha: Boolean,
    dstColorSpace: SkSp<SkColorSpace>,
    srcRect: SkIRect,
    dstSize: SkISize,
    param5: RescaleGamma,
    param6: RescaleMode,
    callback: ReadPixelsCallback,
    context: ReadPixelsContext,
  ) {
    TODO("Implement onAsyncRescaleAndReadPixelsYUV420")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual GrImageContext* context() const { return nullptr; }
   * ```
   */
  public open fun context(): GrImageContext {
    TODO("Implement context")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual GrDirectContext* directContext() const { return nullptr; }
   * ```
   */
  public open fun directContext(): GrDirectContext {
    TODO("Implement directContext")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void generatingSurfaceIsDeleted() {}
   * ```
   */
  public open fun generatingSurfaceIsDeleted() {
    TODO("Implement generatingSurfaceIsDeleted")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool getROPixels(GrDirectContext*, SkBitmap*,
   *                              CachingHint = kAllow_CachingHint) const = 0
   * ```
   */
  public abstract fun getROPixels(
    param0: GrDirectContext?,
    param1: SkBitmap?,
    param2: CachingHint = TODO(),
  ): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<SkImage> onMakeSubset(SkRecorder*, const SkIRect&, RequiredProperties) const = 0
   * ```
   */
  public abstract fun onMakeSubset(
    param0: SkRecorder?,
    param1: SkIRect,
    param2: RequiredProperties,
  ): SkSp<SkImage>

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<const SkData> onRefEncoded() const { return nullptr; }
   * ```
   */
  public open fun onRefEncoded(): SkSp<SkData> {
    TODO("Implement onRefEncoded")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkImage_Base::onAsLegacyBitmap(GrDirectContext* dContext, SkBitmap* bitmap) const {
   *     // As the base-class, all we can do is make a copy (regardless of mode).
   *     // Subclasses that want to be more optimal should override.
   *     SkImageInfo info = fInfo.makeColorType(kN32_SkColorType).makeColorSpace(nullptr);
   *     if (!bitmap->tryAllocPixels(info)) {
   *         return false;
   *     }
   *
   *     if (!this->readPixels(
   *                 dContext, bitmap->info(), bitmap->getPixels(), bitmap->rowBytes(), 0, 0)) {
   *         bitmap->reset();
   *         return false;
   *     }
   *
   *     bitmap->setImmutable();
   *     return true;
   * }
   * ```
   */
  public open fun onAsLegacyBitmap(dContext: GrDirectContext?, bitmap: SkBitmap?): Boolean {
    TODO("Implement onAsLegacyBitmap")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<SkSurface> onMakeSurface(SkRecorder*, const SkImageInfo&) const = 0
   * ```
   */
  public abstract fun onMakeSurface(param0: SkRecorder?, param1: SkImageInfo): SkSp<SkSurface>

  /**
   * C++ original:
   * ```cpp
   * virtual Type type() const = 0
   * ```
   */
  public abstract fun type(): Type

  /**
   * C++ original:
   * ```cpp
   * bool isLazyGenerated() const override {
   *         return this->type() == Type::kLazy || this->type() == Type::kLazyPicture ||
   *                this->type() == Type::kLazyTexture;
   *     }
   * ```
   */
  public override fun isLazyGenerated(): Boolean {
    TODO("Implement isLazyGenerated")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isRasterBacked() const {
   *         return this->type() == Type::kRaster || this->type() == Type::kRasterPinnable;
   *     }
   * ```
   */
  public fun isRasterBacked(): Boolean {
    TODO("Implement isRasterBacked")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isGaneshBacked() const {
   *         return this->type() == Type::kGanesh || this->type() == Type::kGaneshYUVA;
   *     }
   * ```
   */
  public fun isGaneshBacked(): Boolean {
    TODO("Implement isGaneshBacked")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isGraphiteBacked() const {
   *         return this->type() == Type::kGraphite || this->type() == Type::kGraphiteYUVA;
   *     }
   * ```
   */
  public fun isGraphiteBacked(): Boolean {
    TODO("Implement isGraphiteBacked")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isYUVA() const {
   *         return this->type() == Type::kGaneshYUVA || this->type() == Type::kGraphiteYUVA;
   *     }
   * ```
   */
  public fun isYUVA(): Boolean {
    TODO("Implement isYUVA")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isTextureBacked() const override {
   *         return this->isGaneshBacked() || this->isGraphiteBacked();
   *     }
   * ```
   */
  public override fun isTextureBacked(): Boolean {
    TODO("Implement isTextureBacked")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void notifyAddedToRasterCache() const {
   *         fAddedToRasterCache.store(true);
   *     }
   * ```
   */
  public open fun notifyAddedToRasterCache() {
    TODO("Implement notifyAddedToRasterCache")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<SkImage> onReinterpretColorSpace(sk_sp<SkColorSpace>) const = 0
   * ```
   */
  public abstract fun onReinterpretColorSpace(param0: SkSp<SkColorSpace>): SkSp<SkImage>

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<SkImage> onMakeWithMipmaps(sk_sp<SkMipmap>) const {
   *         return nullptr;
   *     }
   * ```
   */
  public open fun onMakeWithMipmaps(param0: SkSp<SkMipmap>): SkSp<SkImage> {
    TODO("Implement onMakeWithMipmaps")
  }

  public enum class Type {
    kRaster,
    kRasterPinnable,
    kLazy,
    kLazyPicture,
    kLazyTexture,
    kGanesh,
    kGaneshYUVA,
    kGraphite,
    kGraphiteYUVA,
  }
}
