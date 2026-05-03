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
import org.skia.gpu.ganesh.GrDirectContext
import org.skia.math.SkIRect
import undefined.CachingHint
import undefined.RequiredProperties

/**
 * C++ original:
 * ```cpp
 * class SkImage_Raster : public SkImage_Base {
 * public:
 *     SkImage_Raster(const SkImageInfo&, sk_sp<SkData>, size_t rb,
 *                    uint32_t id = kNeedNewImageUniqueID);
 *     SkImage_Raster(const SkBitmap& bm, bool bitmapMayBeMutable = false);
 *     ~SkImage_Raster() override;
 *
 *     // From SkImage.h
 *     bool isValid(SkRecorder* recorder) const override {
 *         if (!recorder) {
 *             return false;
 *         }
 *         if (!recorder->cpuRecorder()) {
 *             return false;
 *         }
 *         return true;
 *     }
 *     sk_sp<SkImage> makeColorTypeAndColorSpace(SkRecorder*,
 *                                               SkColorType targetColorType,
 *                                               sk_sp<SkColorSpace> targetColorSpace,
 *                                               RequiredProperties) const override;
 *
 *     // From SkImage_Base.h
 *     bool onReadPixels(GrDirectContext*, const SkImageInfo&, void*, size_t, int srcX, int srcY,
 *                       CachingHint) const override;
 *     bool onPeekPixels(SkPixmap*) const override;
 *     const SkBitmap* onPeekBitmap() const override { return &fBitmap; }
 *
 *     bool getROPixels(GrDirectContext*, SkBitmap*, CachingHint) const override;
 *
 *     sk_sp<SkImage> onMakeSubset(SkRecorder*, const SkIRect&, RequiredProperties) const override;
 *
 *     sk_sp<SkSurface> onMakeSurface(SkRecorder*, const SkImageInfo&) const final;
 *
 *     SkPixelRef* getPixelRef() const { return fBitmap.pixelRef(); }
 *
 *     bool onAsLegacyBitmap(GrDirectContext*, SkBitmap*) const override;
 *
 *     sk_sp<SkImage> onReinterpretColorSpace(sk_sp<SkColorSpace>) const override;
 *
 *     void notifyAddedToRasterCache() const override {
 *         // We explicitly DON'T want to call INHERITED::notifyAddedToRasterCache. That ties the
 *         // lifetime of derived/cached resources to the image. In this case, we only want cached
 *         // data (eg mips) tied to the lifetime of the underlying pixelRef.
 *         SkASSERT(fBitmap.pixelRef());
 *         fBitmap.pixelRef()->notifyAddedToCache();
 *     }
 *
 *     bool onHasMipmaps() const override { return SkToBool(fBitmap.fMips); }
 *     bool onIsProtected() const override { return false; }
 *
 *     SkMipmap* onPeekMips() const override { return fBitmap.fMips.get(); }
 *
 *     sk_sp<SkImage> onMakeWithMipmaps(sk_sp<SkMipmap> mips) const override {
 *         // It's dangerous to have two SkBitmaps that share a SkPixelRef but have different SkMipmaps
 *         // since various caches key on SkPixelRef's generation ID. Also, SkPixelRefs that back
 *         // SkSurfaces are marked "temporarily immutable" and making an image that uses the same
 *         // SkPixelRef can interact badly with SkSurface/SkImage copy-on-write. So we just always
 *         // make a copy with a new ID.
 *         static auto constexpr kCopyMode = SkCopyPixelsMode::kAlways_SkCopyPixelsMode;
 *         sk_sp<SkImage> img = SkMakeImageFromRasterBitmap(fBitmap, kCopyMode);
 *         auto imgRaster = static_cast<SkImage_Raster*>(img.get());
 *         if (mips) {
 *             imgRaster->fBitmap.fMips = std::move(mips);
 *         } else {
 *             imgRaster->fBitmap.fMips.reset(SkMipmap::Build(fBitmap.pixmap(), nullptr));
 *         }
 *         return img;
 *     }
 *
 *     SkImage_Base::Type type() const override { return SkImage_Base::Type::kRaster; }
 *
 *     SkBitmap bitmap() const { return fBitmap; }
 * private:
 *     SkBitmap fBitmap;
 * }
 * ```
 */
public open class SkImageRaster public constructor(
  info: SkImageInfo,
  `data`: SkSp<SkData>,
  rb: ULong,
  id: UInt = TODO(),
) : SkImageBase(TODO(), TODO()) {
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
   * SkImage_Raster::SkImage_Raster(const SkImageInfo& info, sk_sp<SkData> data, size_t rowBytes,
   *                                uint32_t id)
   *         : SkImage_Base(info, id) {
   *     void* addr = const_cast<void*>(data->data());
   *
   *     fBitmap.installPixels(info, addr, rowBytes, release_data, data.release());
   *     fBitmap.setImmutable();
   * }
   * ```
   */
  public constructor(bm: SkBitmap, bitmapMayBeMutable: Boolean = false) : this(TODO(), TODO()) {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isValid(SkRecorder* recorder) const override {
   *         if (!recorder) {
   *             return false;
   *         }
   *         if (!recorder->cpuRecorder()) {
   *             return false;
   *         }
   *         return true;
   *     }
   * ```
   */
  public override fun isValid(recorder: SkRecorder?): Boolean {
    TODO("Implement isValid")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> SkImage_Raster::makeColorTypeAndColorSpace(SkRecorder*,
   *                                                           SkColorType targetColorType,
   *                                                           sk_sp<SkColorSpace> targetColorSpace,
   *                                                           RequiredProperties) const {
   *     SkPixmap src;
   *     SkAssertResult(fBitmap.peekPixels(&src));
   *
   *     SkBitmap dst;
   *     if (!dst.tryAllocPixels(
   *                 fBitmap.info().makeColorType(targetColorType).makeColorSpace(targetColorSpace))) {
   *         return nullptr;
   *     }
   *
   *     SkAssertResult(dst.writePixels(src));
   *     dst.setImmutable();
   *     return SkImages::RasterFromBitmap(dst);
   * }
   * ```
   */
  public override fun makeColorTypeAndColorSpace(
    param0: SkRecorder?,
    targetColorType: SkColorType,
    targetColorSpace: SkSp<SkColorSpace>,
    param3: RequiredProperties,
  ): SkSp<SkImage> {
    TODO("Implement makeColorTypeAndColorSpace")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkImage_Raster::onReadPixels(GrDirectContext*,
   *                                   const SkImageInfo& dstInfo,
   *                                   void* dstPixels,
   *                                   size_t dstRowBytes,
   *                                   int srcX,
   *                                   int srcY,
   *                                   CachingHint) const {
   *     SkBitmap shallowCopy(fBitmap);
   *     return shallowCopy.readPixels(dstInfo, dstPixels, dstRowBytes, srcX, srcY);
   * }
   * ```
   */
  public override fun onReadPixels(
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
   * bool SkImage_Raster::onPeekPixels(SkPixmap* pm) const {
   *     return fBitmap.peekPixels(pm);
   * }
   * ```
   */
  public override fun onPeekPixels(pm: SkPixmap?): Boolean {
    TODO("Implement onPeekPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkBitmap* onPeekBitmap() const override { return &fBitmap; }
   * ```
   */
  public override fun onPeekBitmap(): SkBitmap {
    TODO("Implement onPeekBitmap")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkImage_Raster::getROPixels(GrDirectContext*, SkBitmap* dst, CachingHint) const {
   *     *dst = fBitmap;
   *     return true;
   * }
   * ```
   */
  public override fun getROPixels(
    param0: GrDirectContext?,
    dst: SkBitmap?,
    param2: CachingHint,
  ): Boolean {
    TODO("Implement getROPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> SkImage_Raster::onMakeSubset(SkRecorder*,
   *                                             const SkIRect& subset,
   *                                             RequiredProperties requiredProperties) const {
   *     sk_sp<SkImage> img;
   *
   *     if (requiredProperties.fMipmapped) {
   *         bool fullCopy = subset == SkIRect::MakeSize(fBitmap.dimensions());
   *
   *         sk_sp<SkMipmap> mips = fullCopy ? copy_mipmaps(fBitmap, fBitmap.fMips.get()) : nullptr;
   *
   *         // SkImage::withMipmaps will always make a copy for us so we can temporarily share
   *         // the pixel ref with fBitmap
   *         SkBitmap tmpSubset;
   *         if (!fBitmap.extractSubset(&tmpSubset, subset)) {
   *             return nullptr;
   *         }
   *
   *         sk_sp<SkImage> tmp(new SkImage_Raster(tmpSubset, /* bitmapMayBeMutable= */ true));
   *
   *         // withMipmaps will auto generate the mipmaps if a nullptr is passed in
   *         SkASSERT(!mips || mips->validForRootLevel(tmp->imageInfo()));
   *         img = tmp->withMipmaps(std::move(mips));
   *     } else {
   *         SkBitmap copy = copy_bitmap_subset(fBitmap, subset);
   *         if (!copy.isNull()) {
   *             img = SkImages::RasterFromBitmap(copy);
   *         }
   *     }
   *
   *     return img;
   * }
   * ```
   */
  public override fun onMakeSubset(
    param0: SkRecorder?,
    subset: SkIRect,
    requiredProperties: RequiredProperties,
  ): SkSp<SkImage> {
    TODO("Implement onMakeSubset")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkSurface> SkImage_Raster::onMakeSurface(SkRecorder* recorder,
   *                                                const SkImageInfo& info) const {
   *     if (!recorder) {
   *         // TODO(kjlubick) remove this after old SkImage::makeScaled(image info, sampling) API gone
   *         recorder = skcpu::Recorder::TODO();
   *     }
   *     const SkSurfaceProps* props = nullptr;
   *     constexpr size_t rowBytes = 0;
   *     return recorder->cpuRecorder()->makeBitmapSurface(info, rowBytes, props);
   * }
   * ```
   */
  public override fun onMakeSurface(recorder: SkRecorder?, info: SkImageInfo): SkSp<SkSurface> {
    TODO("Implement onMakeSurface")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPixelRef* getPixelRef() const { return fBitmap.pixelRef(); }
   * ```
   */
  public fun getPixelRef(): SkPixelRef {
    TODO("Implement getPixelRef")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkImage_Raster::onAsLegacyBitmap(GrDirectContext*, SkBitmap* bitmap) const {
   *     // When we're a snapshot from a surface, our bitmap may not be marked immutable
   *     // even though logically always we are, but in that case we can't physically share our
   *     // pixelref since the caller might call setImmutable() themselves
   *     // (thus changing our state).
   *     if (fBitmap.isImmutable()) {
   *         SkIPoint origin = fBitmap.pixelRefOrigin();
   *         bitmap->setInfo(fBitmap.info(), fBitmap.rowBytes());
   *         bitmap->setPixelRef(sk_ref_sp(fBitmap.pixelRef()), origin.x(), origin.y());
   *         return true;
   *     }
   *     return this->SkImage_Base::onAsLegacyBitmap(nullptr, bitmap);
   * }
   * ```
   */
  public override fun onAsLegacyBitmap(param0: GrDirectContext?, bitmap: SkBitmap?): Boolean {
    TODO("Implement onAsLegacyBitmap")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> SkImage_Raster::onReinterpretColorSpace(sk_sp<SkColorSpace> newCS) const {
   *     // TODO: If our bitmap is immutable, then we could theoretically create another image sharing
   *     // our pixelRef. That doesn't work (without more invasive logic), because the image gets its
   *     // gen ID from the bitmap, which gets it from the pixelRef.
   *     SkPixmap pixmap = fBitmap.pixmap();
   *     pixmap.setColorSpace(std::move(newCS));
   *     return SkImages::RasterFromPixmapCopy(pixmap);
   * }
   * ```
   */
  public override fun onReinterpretColorSpace(newCS: SkSp<SkColorSpace>): SkSp<SkImage> {
    TODO("Implement onReinterpretColorSpace")
  }

  /**
   * C++ original:
   * ```cpp
   * void notifyAddedToRasterCache() const override {
   *         // We explicitly DON'T want to call INHERITED::notifyAddedToRasterCache. That ties the
   *         // lifetime of derived/cached resources to the image. In this case, we only want cached
   *         // data (eg mips) tied to the lifetime of the underlying pixelRef.
   *         SkASSERT(fBitmap.pixelRef());
   *         fBitmap.pixelRef()->notifyAddedToCache();
   *     }
   * ```
   */
  public override fun notifyAddedToRasterCache() {
    TODO("Implement notifyAddedToRasterCache")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onHasMipmaps() const override { return SkToBool(fBitmap.fMips); }
   * ```
   */
  public override fun onHasMipmaps(): Boolean {
    TODO("Implement onHasMipmaps")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onIsProtected() const override { return false; }
   * ```
   */
  public override fun onIsProtected(): Boolean {
    TODO("Implement onIsProtected")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMipmap* onPeekMips() const override { return fBitmap.fMips.get(); }
   * ```
   */
  public override fun onPeekMips(): SkMipmap {
    TODO("Implement onPeekMips")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> onMakeWithMipmaps(sk_sp<SkMipmap> mips) const override {
   *         // It's dangerous to have two SkBitmaps that share a SkPixelRef but have different SkMipmaps
   *         // since various caches key on SkPixelRef's generation ID. Also, SkPixelRefs that back
   *         // SkSurfaces are marked "temporarily immutable" and making an image that uses the same
   *         // SkPixelRef can interact badly with SkSurface/SkImage copy-on-write. So we just always
   *         // make a copy with a new ID.
   *         static auto constexpr kCopyMode = SkCopyPixelsMode::kAlways_SkCopyPixelsMode;
   *         sk_sp<SkImage> img = SkMakeImageFromRasterBitmap(fBitmap, kCopyMode);
   *         auto imgRaster = static_cast<SkImage_Raster*>(img.get());
   *         if (mips) {
   *             imgRaster->fBitmap.fMips = std::move(mips);
   *         } else {
   *             imgRaster->fBitmap.fMips.reset(SkMipmap::Build(fBitmap.pixmap(), nullptr));
   *         }
   *         return img;
   *     }
   * ```
   */
  public override fun onMakeWithMipmaps(mips: SkSp<SkMipmap>): SkSp<SkImage> {
    TODO("Implement onMakeWithMipmaps")
  }

  /**
   * C++ original:
   * ```cpp
   * SkImage_Base::Type type() const override { return SkImage_Base::Type::kRaster; }
   * ```
   */
  public override fun type(): SkImage_Base.Type {
    TODO("Implement type")
  }

  /**
   * C++ original:
   * ```cpp
   * SkBitmap bitmap() const { return fBitmap; }
   * ```
   */
  public fun bitmap(): SkBitmap {
    TODO("Implement bitmap")
  }
}
