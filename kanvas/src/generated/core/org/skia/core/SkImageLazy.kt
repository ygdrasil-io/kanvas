package org.skia.core

import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkData
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPixmap
import org.skia.foundation.SkSp
import org.skia.gpu.ganesh.GrDirectContext
import org.skia.math.SkIRect
import undefined.CachingHint
import undefined.RequiredProperties
import undefined.SkMutex

/**
 * C++ original:
 * ```cpp
 * class SkImage_Lazy : public SkImage_Base {
 * public:
 *     struct Validator {
 *         Validator(sk_sp<SharedGenerator>, const SkColorType*, sk_sp<SkColorSpace>);
 *
 *         explicit operator bool() const { return fSharedGenerator.get(); }
 *
 *         sk_sp<SharedGenerator> fSharedGenerator;
 *         SkImageInfo            fInfo;
 *         sk_sp<SkColorSpace>    fColorSpace;
 *         uint32_t               fUniqueID;
 *     };
 *
 *     explicit SkImage_Lazy(Validator* validator);
 *
 *     // From SkImage.h
 *     bool isValid(SkRecorder*) const override;
 *     sk_sp<SkImage> makeColorTypeAndColorSpace(SkRecorder*,
 *                                               SkColorType targetColorType,
 *                                               sk_sp<SkColorSpace> targetColorSpace,
 *                                               RequiredProperties) const override;
 *
 *     // From SkImage_Base.h
 *     bool onHasMipmaps() const override {
 *         // TODO: Should we defer to the generator? The generator interface currently doesn't have
 *         // a way to provide content for levels other than via SkImageGenerator::generateTexture().
 *         return false;
 *     }
 *     bool onIsProtected() const override;
 *
 *     bool onReadPixels(GrDirectContext*, const SkImageInfo&, void*, size_t, int srcX, int srcY,
 *                       CachingHint) const override;
 *     sk_sp<const SkData> onRefEncoded() const override;
 *
 *     sk_sp<SkImage> onMakeSubset(SkRecorder*, const SkIRect&, RequiredProperties) const override;
 *
 *     sk_sp<SkSurface> onMakeSurface(SkRecorder*, const SkImageInfo&) const override;
 *
 *     bool getROPixels(GrDirectContext*, SkBitmap*, CachingHint) const override;
 *     SkImage_Base::Type type() const override { return SkImage_Base::Type::kLazy; }
 *
 *     sk_sp<SkImage> onReinterpretColorSpace(sk_sp<SkColorSpace>) const final;
 *
 *     void addUniqueIDListener(sk_sp<SkIDChangeListener>) const;
 *     sk_sp<SkCachedData> getPlanes(const SkYUVAPixmapInfo::SupportedDataTypes& supportedDataTypes,
 *                                   SkYUVAPixmaps* pixmaps) const;
 *
 *     // Be careful with this. You need to acquire the mutex, as the generator might be shared
 *     // among several images.
 *     sk_sp<SharedGenerator> generator() const;
 *
 * protected:
 *     virtual bool readPixelsProxy(GrDirectContext*, const SkPixmap&) const { return false; }
 *
 * private:
 *
 *     class ScopedGenerator;
 *
 *     // Note that this->imageInfo() is not necessarily the info from the generator. It may be
 *     // cropped by onMakeSubset and its color type/space may be changed by
 *     // onMakeColorTypeAndColorSpace.
 *     sk_sp<SharedGenerator> fSharedGenerator;
 *
 *     // Repeated calls to onMakeColorTypeAndColorSpace will result in a proliferation of unique IDs
 *     // and SkImage_Lazy instances. Cache the result of the last successful call.
 *     mutable SkMutex        fOnMakeColorTypeAndSpaceMutex;
 *     mutable sk_sp<SkImage> fOnMakeColorTypeAndSpaceResult;
 *     // When the SkImage_Lazy goes away, we will iterate over all the listeners to inform them
 *     // of the unique ID's demise. This is used to remove cached textures from GrContext.
 *     mutable SkIDChangeListener::List fUniqueIDListeners;
 * }
 * ```
 */
public open class SkImageLazy public constructor(
  validator: Validator?,
) : SkImageBase(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SharedGenerator> fSharedGenerator
   * ```
   */
  public var fSharedGenerator: SkSp<SharedGenerator> = TODO("Initialize fSharedGenerator")

  /**
   * C++ original:
   * ```cpp
   * mutable SkMutex        fOnMakeColorTypeAndSpaceMutex
   * ```
   */
  private var fOnMakeColorTypeAndSpaceMutex: SkMutex =
      TODO("Initialize fOnMakeColorTypeAndSpaceMutex")

  /**
   * C++ original:
   * ```cpp
   * mutable sk_sp<SkImage> fOnMakeColorTypeAndSpaceResult
   * ```
   */
  private var fOnMakeColorTypeAndSpaceResult: SkSp<SkImage> =
      TODO("Initialize fOnMakeColorTypeAndSpaceResult")

  /**
   * C++ original:
   * ```cpp
   * mutable SkIDChangeListener::List fUniqueIDListeners
   * ```
   */
  private var fUniqueIDListeners: SkIDChangeListener.List = TODO("Initialize fUniqueIDListeners")

  /**
   * C++ original:
   * ```cpp
   * bool SkImage_Lazy::isValid(SkRecorder* recorder) const {
   *     ScopedGenerator generator(fSharedGenerator);
   *     return generator->isValid(recorder);
   * }
   * ```
   */
  public override fun isValid(recorder: SkRecorder?): Boolean {
    TODO("Implement isValid")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> SkImage_Lazy::makeColorTypeAndColorSpace(SkRecorder*,
   *                                                         SkColorType targetColorType,
   *                                                         sk_sp<SkColorSpace> targetColorSpace,
   *                                                         RequiredProperties) const {
   *     SkAutoMutexExclusive autoAquire(fOnMakeColorTypeAndSpaceMutex);
   *     if (fOnMakeColorTypeAndSpaceResult &&
   *         targetColorType == fOnMakeColorTypeAndSpaceResult->colorType() &&
   *         SkColorSpace::Equals(targetColorSpace.get(),
   *                              fOnMakeColorTypeAndSpaceResult->colorSpace())) {
   *         return fOnMakeColorTypeAndSpaceResult;
   *     }
   *     Validator validator(fSharedGenerator, &targetColorType, targetColorSpace);
   *     sk_sp<SkImage> result = validator ? sk_sp<SkImage>(new SkImage_Lazy(&validator)) : nullptr;
   *     if (result) {
   *         fOnMakeColorTypeAndSpaceResult = result;
   *     }
   *     return result;
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
   * bool onHasMipmaps() const override {
   *         // TODO: Should we defer to the generator? The generator interface currently doesn't have
   *         // a way to provide content for levels other than via SkImageGenerator::generateTexture().
   *         return false;
   *     }
   * ```
   */
  public override fun onHasMipmaps(): Boolean {
    TODO("Implement onHasMipmaps")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkImage_Lazy::onIsProtected() const {
   *     ScopedGenerator generator(fSharedGenerator);
   *     return generator->isProtected();
   * }
   * ```
   */
  public override fun onIsProtected(): Boolean {
    TODO("Implement onIsProtected")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkImage_Lazy::onReadPixels(GrDirectContext* dContext,
   *                                 const SkImageInfo& dstInfo,
   *                                 void* dstPixels,
   *                                 size_t dstRB,
   *                                 int srcX,
   *                                 int srcY,
   *                                 CachingHint chint) const {
   *     SkBitmap bm;
   *     if (this->getROPixels(dContext, &bm, chint)) {
   *         return bm.readPixels(dstInfo, dstPixels, dstRB, srcX, srcY);
   *     }
   *     return false;
   * }
   * ```
   */
  public override fun onReadPixels(
    dContext: GrDirectContext?,
    dstInfo: SkImageInfo,
    dstPixels: Unit?,
    dstRB: ULong,
    srcX: Int,
    srcY: Int,
    chint: CachingHint,
  ): Boolean {
    TODO("Implement onReadPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<const SkData> SkImage_Lazy::onRefEncoded() const {
   *     // check that we aren't a subset or colortype/etc modification of the original
   *     if (fSharedGenerator->fGenerator->uniqueID() == this->uniqueID()) {
   *         ScopedGenerator generator(fSharedGenerator);
   *         return generator->refEncodedData();
   *     }
   *     return nullptr;
   * }
   * ```
   */
  public override fun onRefEncoded(): SkSp<SkData> {
    TODO("Implement onRefEncoded")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> SkImage_Lazy::onMakeSubset(SkRecorder*,
   *                                           const SkIRect& subset,
   *                                           RequiredProperties props) const {
   *     // TODO: can we do this more efficiently, by telling the generator we want to
   *     //       "realize" a subset?
   *     sk_sp<SkImage> nonLazyImg = this->makeRasterImage(nullptr);
   *     if (!nonLazyImg) {
   *         return nullptr;
   *     }
   *     return nonLazyImg->makeSubset(nullptr, subset, props);
   * }
   * ```
   */
  public override fun onMakeSubset(
    param0: SkRecorder?,
    subset: SkIRect,
    props: RequiredProperties,
  ): SkSp<SkImage> {
    TODO("Implement onMakeSubset")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkSurface> SkImage_Lazy::onMakeSurface(SkRecorder* recorder, const SkImageInfo& info) const {
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
   * bool SkImage_Lazy::getROPixels(GrDirectContext* ctx, SkBitmap* bitmap,
   *                                SkImage::CachingHint chint) const {
   *     auto check_output_bitmap = [bitmap]() {
   *         SkASSERT(bitmap->isImmutable());
   *         SkASSERT(bitmap->getPixels());
   *         (void)bitmap;
   *     };
   *
   *     auto desc = SkBitmapCacheDesc::Make(this);
   *     if (SkBitmapCache::Find(desc, bitmap)) {
   *         check_output_bitmap();
   *         return true;
   *     }
   *
   *     if (SkImage::kAllow_CachingHint == chint) {
   *         SkPixmap pmap;
   *         SkBitmapCache::RecPtr cacheRec = SkBitmapCache::Alloc(desc, this->imageInfo(), &pmap);
   *         if (!cacheRec) {
   *             return false;
   *         }
   *         bool success = false;
   *         {   // make sure ScopedGenerator goes out of scope before we try readPixelsProxy
   *             success = ScopedGenerator(fSharedGenerator)->getPixels(pmap);
   *         }
   *         if (!success && !this->readPixelsProxy(ctx, pmap)) {
   *             return false;
   *         }
   *         SkBitmapCache::Add(std::move(cacheRec), bitmap);
   *         this->notifyAddedToRasterCache();
   *     } else {
   *         if (!bitmap->tryAllocPixels(this->imageInfo())) {
   *             return false;
   *         }
   *         bool success = false;
   *         {   // make sure ScopedGenerator goes out of scope before we try readPixelsProxy
   *             success = ScopedGenerator(fSharedGenerator)->getPixels(bitmap->pixmap());
   *         }
   *         if (!success && !this->readPixelsProxy(ctx, bitmap->pixmap())) {
   *             return false;
   *         }
   *         bitmap->setImmutable();
   *     }
   *     check_output_bitmap();
   *     return true;
   * }
   * ```
   */
  public override fun getROPixels(
    ctx: GrDirectContext?,
    bitmap: SkBitmap?,
    chint: CachingHint,
  ): Boolean {
    TODO("Implement getROPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * SkImage_Base::Type type() const override { return SkImage_Base::Type::kLazy; }
   * ```
   */
  public override fun type(): SkImage_Base.Type {
    TODO("Implement type")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> SkImage_Lazy::onReinterpretColorSpace(sk_sp<SkColorSpace> newCS) const {
   *     // TODO: The correct thing is to clone the generator, and modify its color space. That's hard,
   *     // because we don't have a clone method, and generator is public (and derived-from by clients).
   *     // So do the simple/inefficient thing here, and fallback to raster when this is called.
   *
   *     // We allocate the bitmap with the new color space, then generate the image using the original.
   *     SkBitmap bitmap;
   *     if (bitmap.tryAllocPixels(this->imageInfo().makeColorSpace(std::move(newCS)))) {
   *         SkPixmap pixmap = bitmap.pixmap();
   *         pixmap.setColorSpace(this->refColorSpace());
   *         if (ScopedGenerator(fSharedGenerator)->getPixels(pixmap)) {
   *             bitmap.setImmutable();
   *             return SkImages::RasterFromBitmap(bitmap);
   *         }
   *     }
   *     return nullptr;
   * }
   * ```
   */
  public override fun onReinterpretColorSpace(newCS: SkSp<SkColorSpace>): SkSp<SkImage> {
    TODO("Implement onReinterpretColorSpace")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkImage_Lazy::addUniqueIDListener(sk_sp<SkIDChangeListener> listener) const {
   *     fUniqueIDListeners.add(std::move(listener));
   * }
   * ```
   */
  public fun addUniqueIDListener(listener: SkSp<SkIDChangeListener>) {
    TODO("Implement addUniqueIDListener")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkCachedData> SkImage_Lazy::getPlanes(
   *         const SkYUVAPixmapInfo::SupportedDataTypes& supportedDataTypes,
   *         SkYUVAPixmaps* yuvaPixmaps) const {
   *     ScopedGenerator generator(fSharedGenerator);
   *
   *     sk_sp<SkCachedData> data(SkYUVPlanesCache::FindAndRef(generator->uniqueID(), yuvaPixmaps));
   *
   *     if (data) {
   *         SkASSERT(yuvaPixmaps->isValid());
   *         SkASSERT(yuvaPixmaps->yuvaInfo().dimensions() == this->dimensions());
   *         return data;
   *     }
   *     SkYUVAPixmapInfo yuvaPixmapInfo;
   *     if (!generator->queryYUVAInfo(supportedDataTypes, &yuvaPixmapInfo) ||
   *         yuvaPixmapInfo.yuvaInfo().dimensions() != this->dimensions()) {
   *         return nullptr;
   *     }
   *     data.reset(SkResourceCache::NewCachedData(yuvaPixmapInfo.computeTotalBytes()));
   *     SkYUVAPixmaps tempPixmaps = SkYUVAPixmaps::FromExternalMemory(yuvaPixmapInfo,
   *                                                                   data->writable_data());
   *     SkASSERT(tempPixmaps.isValid());
   *     if (!generator->getYUVAPlanes(tempPixmaps)) {
   *         return nullptr;
   *     }
   *     // Decoding is done, cache the resulting YUV planes
   *     *yuvaPixmaps = tempPixmaps;
   *     SkYUVPlanesCache::Add(this->uniqueID(), data.get(), *yuvaPixmaps);
   *     return data;
   * }
   * ```
   */
  public fun getPlanes(supportedDataTypes: SkYUVAPixmapInfo.SupportedDataTypes, pixmaps: SkYUVAPixmaps?): SkSp<SkCachedData> {
    TODO("Implement getPlanes")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SharedGenerator> SkImage_Lazy::generator() const {
   *     return fSharedGenerator;
   * }
   * ```
   */
  public fun generator(): SkSp<SharedGenerator> {
    TODO("Implement generator")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool readPixelsProxy(GrDirectContext*, const SkPixmap&) const { return false; }
   * ```
   */
  protected open fun readPixelsProxy(param0: GrDirectContext?, param1: SkPixmap): Boolean {
    TODO("Implement readPixelsProxy")
  }

  public data class Validator public constructor(
    public var fSharedGenerator: SkSp<SharedGenerator>,
    public var fInfo: SkImageInfo,
    public var fColorSpace: SkSp<SkColorSpace>,
    public var fUniqueID: UInt,
  )
}
