package org.skia.core

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkRefCnt
import org.skia.foundation.SkSp
import org.skia.gpu.ganesh.GrDirectContext
import org.skia.math.SkIRect
import org.skia.math.SkISize
import org.skia.math.SkMatrix

/**
 * C++ original:
 * ```cpp
 * class SK_API SkImage : public SkRefCnt {
 * public:
 *     /** Returns a SkImageInfo describing the width, height, color type, alpha type, and color space
 *         of the SkImage.
 *
 *         @return  image info of SkImage.
 *     */
 *     const SkImageInfo& imageInfo() const { return fInfo; }
 *
 *     /** Returns pixel count in each row.
 *
 *         @return  pixel width in SkImage
 *     */
 *     int width() const { return fInfo.width(); }
 *
 *     /** Returns pixel row count.
 *
 *         @return  pixel height in SkImage
 *     */
 *     int height() const { return fInfo.height(); }
 *
 *     /** Returns SkISize { width(), height() }.
 *
 *         @return  integral size of width() and height()
 *     */
 *     SkISize dimensions() const { return SkISize::Make(fInfo.width(), fInfo.height()); }
 *
 *     /** Returns SkIRect { 0, 0, width(), height() }.
 *
 *         @return  integral rectangle from origin to width() and height()
 *     */
 *     SkIRect bounds() const { return SkIRect::MakeWH(fInfo.width(), fInfo.height()); }
 *
 *     /** Returns value unique to image. SkImage contents cannot change after SkImage is
 *         created. Any operation to create a new SkImage will receive generate a new
 *         unique number.
 *
 *         @return  unique identifier
 *     */
 *     uint32_t uniqueID() const { return fUniqueID; }
 *
 *     /** Returns SkAlphaType.
 *
 *         SkAlphaType returned was a parameter to an SkImage constructor,
 *         or was parsed from encoded data.
 *
 *         @return  SkAlphaType in SkImage
 *
 *         example: https://fiddle.skia.org/c/@Image_alphaType
 *     */
 *     SkAlphaType alphaType() const;
 *
 *     /** Returns SkColorType if known; otherwise, returns kUnknown_SkColorType.
 *
 *         @return  SkColorType of SkImage
 *
 *         example: https://fiddle.skia.org/c/@Image_colorType
 *     */
 *     SkColorType colorType() const;
 *
 *     /** Returns SkColorSpace, the range of colors, associated with SkImage.  The
 *         reference count of SkColorSpace is unchanged. The returned SkColorSpace is
 *         immutable.
 *
 *         SkColorSpace returned was passed to an SkImage constructor,
 *         or was parsed from encoded data. SkColorSpace returned may be ignored when SkImage
 *         is drawn, depending on the capabilities of the SkSurface receiving the drawing.
 *
 *         @return  SkColorSpace in SkImage, or nullptr
 *
 *         example: https://fiddle.skia.org/c/@Image_colorSpace
 *     */
 *     SkColorSpace* colorSpace() const;
 *
 *     /** Returns a smart pointer to SkColorSpace, the range of colors, associated with
 *         SkImage.  The smart pointer tracks the number of objects sharing this
 *         SkColorSpace reference so the memory is released when the owners destruct.
 *
 *         The returned SkColorSpace is immutable.
 *
 *         SkColorSpace returned was passed to an SkImage constructor,
 *         or was parsed from encoded data. SkColorSpace returned may be ignored when SkImage
 *         is drawn, depending on the capabilities of the SkSurface receiving the drawing.
 *
 *         @return  SkColorSpace in SkImage, or nullptr, wrapped in a smart pointer
 *
 *         example: https://fiddle.skia.org/c/@Image_refColorSpace
 *     */
 *     sk_sp<SkColorSpace> refColorSpace() const;
 *
 *     /** Returns true if SkImage pixels represent transparency only. If true, each pixel
 *         is packed in 8 bits as defined by kAlpha_8_SkColorType.
 *
 *         @return  true if pixels represent a transparency mask
 *
 *         example: https://fiddle.skia.org/c/@Image_isAlphaOnly
 *     */
 *     bool isAlphaOnly() const;
 *
 *     /** Returns true if pixels ignore their alpha value and are treated as fully opaque.
 *
 *         @return  true if SkAlphaType is kOpaque_SkAlphaType
 *     */
 *     bool isOpaque() const { return SkAlphaTypeIsOpaque(this->alphaType()); }
 *
 *     /**
 *      *  Make a shader with the specified tiling and mipmap sampling.
 *      */
 *     sk_sp<SkShader> makeShader(SkTileMode tmx, SkTileMode tmy, const SkSamplingOptions&,
 *                                const SkMatrix* localMatrix = nullptr) const;
 *     sk_sp<SkShader> makeShader(SkTileMode tmx, SkTileMode tmy, const SkSamplingOptions& sampling,
 *                                const SkMatrix& lm) const;
 *     /** Defaults to clamp in both X and Y. */
 *     sk_sp<SkShader> makeShader(const SkSamplingOptions& sampling, const SkMatrix& lm) const;
 *     sk_sp<SkShader> makeShader(const SkSamplingOptions& sampling,
 *                                const SkMatrix* lm = nullptr) const;
 *
 *     /**
 *      *  makeRawShader functions like makeShader, but for images that contain non-color data.
 *      *  This includes images encoding things like normals, material properties (eg, roughness),
 *      *  heightmaps, or any other purely mathematical data that happens to be stored in an image.
 *      *  These types of images are useful with some programmable shaders (see: SkRuntimeEffect).
 *      *
 *      *  Raw image shaders work like regular image shaders (including filtering and tiling), with
 *      *  a few major differences:
 *      *    - No color space transformation is ever applied (the color space of the image is ignored).
 *      *    - Images with an alpha type of kUnpremul are *not* automatically premultiplied.
 *      *    - Bicubic filtering is not supported. If SkSamplingOptions::useCubic is true, these
 *      *      factories will return nullptr.
 *      */
 *     sk_sp<SkShader> makeRawShader(SkTileMode tmx, SkTileMode tmy, const SkSamplingOptions&,
 *                                   const SkMatrix* localMatrix = nullptr) const;
 *     sk_sp<SkShader> makeRawShader(SkTileMode tmx, SkTileMode tmy, const SkSamplingOptions& sampling,
 *                                   const SkMatrix& lm) const;
 *     /** Defaults to clamp in both X and Y. */
 *     sk_sp<SkShader> makeRawShader(const SkSamplingOptions& sampling, const SkMatrix& lm) const;
 *     sk_sp<SkShader> makeRawShader(const SkSamplingOptions& sampling,
 *                                   const SkMatrix* lm = nullptr) const;
 *
 *     /** Copies SkImage pixel address, row bytes, and SkImageInfo to pixmap, if address
 *         is available, and returns true. If pixel address is not available, return
 *         false and leave pixmap unchanged.
 *
 *         @param pixmap  storage for pixel state if pixels are readable; otherwise, ignored
 *         @return        true if SkImage has direct access to pixels
 *
 *         example: https://fiddle.skia.org/c/@Image_peekPixels
 *     */
 *     bool peekPixels(SkPixmap* pixmap) const;
 *
 *     /** Returns true if the contents of SkImage was created on or uploaded to GPU memory,
 *         and is available as a GPU texture.
 *
 *         @return  true if SkImage is a GPU texture
 *
 *         example: https://fiddle.skia.org/c/@Image_isTextureBacked
 *     */
 *     virtual bool isTextureBacked() const = 0;
 *
 *     /** Returns an approximation of the amount of texture memory used by the image. Returns
 *         zero if the image is not texture backed or if the texture has an external format.
 *      */
 *     virtual size_t textureSize() const = 0;
 *
 *     /** Returns true if SkImage can be drawn on either raster surface or GPU surface.
 *         If recorder is nullptr, tests if SkImage draws on raster surface;
 *         otherwise, tests if SkImage draws on the associated GPU surface.
 *
 *         SkImage backed by GPU texture may become invalid if associated context is
 *         invalid. lazy image may be invalid and may not draw to raster surface or
 *         GPU surface or both.
 *
 *         @param context  GPU context
 *         @return         true if SkImage can be drawn
 *
 *         example: https://fiddle.skia.org/c/@Image_isValid
 *     */
 *     virtual bool isValid(SkRecorder*) const = 0;
 *
 *     /** \enum SkImage::CachingHint
 *         CachingHint selects whether Skia may internally cache SkBitmap generated by
 *         decoding SkImage, or by copying SkImage from GPU to CPU. The default behavior
 *         allows caching SkBitmap.
 *
 *         Choose kDisallow_CachingHint if SkImage pixels are to be used only once, or
 *         if SkImage pixels reside in a cache outside of Skia, or to reduce memory pressure.
 *
 *         Choosing kAllow_CachingHint does not ensure that pixels will be cached.
 *         SkImage pixels may not be cached if memory requirements are too large or
 *         pixels are not accessible.
 *     */
 *     enum CachingHint {
 *         kAllow_CachingHint,    //!< allows internally caching decoded and copied pixels
 *         kDisallow_CachingHint, //!< disallows internally caching decoded and copied pixels
 *     };
 *
 *     /** Copies SkRect of pixels from SkImage to dstPixels. Copy starts at offset (srcX, srcY),
 *         and does not exceed SkImage (width(), height()).
 *
 *         Graphite has deprecated this API in favor of the equivalent asynchronous API on
 *         skgpu::graphite::Context (with an optional explicit synchonization).
 *
 *         dstInfo specifies width, height, SkColorType, SkAlphaType, and SkColorSpace of
 *         destination. dstRowBytes specifies the gap from one destination row to the next.
 *         Returns true if pixels are copied. Returns false if:
 *         - dstInfo.addr() equals nullptr
 *         - dstRowBytes is less than dstInfo.minRowBytes()
 *         - SkPixelRef is nullptr
 *
 *         Pixels are copied only if pixel conversion is possible. If SkImage SkColorType is
 *         kGray_8_SkColorType, or kAlpha_8_SkColorType; dstInfo.colorType() must match.
 *         If SkImage SkColorType is kGray_8_SkColorType, dstInfo.colorSpace() must match.
 *         If SkImage SkAlphaType is kOpaque_SkAlphaType, dstInfo.alphaType() must
 *         match. If SkImage SkColorSpace is nullptr, dstInfo.colorSpace() must match. Returns
 *         false if pixel conversion is not possible.
 *
 *         srcX and srcY may be negative to copy only top or left of source. Returns
 *         false if width() or height() is zero or negative.
 *         Returns false if abs(srcX) >= Image width(), or if abs(srcY) >= Image height().
 *
 *         If cachingHint is kAllow_CachingHint, pixels may be retained locally.
 *         If cachingHint is kDisallow_CachingHint, pixels are not added to the local cache.
 *
 *         @param context      the GrDirectContext in play, if it exists
 *         @param dstInfo      destination width, height, SkColorType, SkAlphaType, SkColorSpace
 *         @param dstPixels    destination pixel storage
 *         @param dstRowBytes  destination row length
 *         @param srcX         column index whose absolute value is less than width()
 *         @param srcY         row index whose absolute value is less than height()
 *         @param cachingHint  whether the pixels should be cached locally
 *         @return             true if pixels are copied to dstPixels
 *     */
 *     bool readPixels(GrDirectContext* context,
 *                     const SkImageInfo& dstInfo,
 *                     void* dstPixels,
 *                     size_t dstRowBytes,
 *                     int srcX, int srcY,
 *                     CachingHint cachingHint = kAllow_CachingHint) const;
 *
 *     /** Copies a SkRect of pixels from SkImage to dst. Copy starts at (srcX, srcY), and
 *         does not exceed SkImage (width(), height()).
 *
 *         Graphite has deprecated this API in favor of the equivalent asynchronous API on
 *         skgpu::graphite::Context (with an optional explicit synchonization).
 *
 *         dst specifies width, height, SkColorType, SkAlphaType, SkColorSpace, pixel storage,
 *         and row bytes of destination. dst.rowBytes() specifics the gap from one destination
 *         row to the next. Returns true if pixels are copied. Returns false if:
 *         - dst pixel storage equals nullptr
 *         - dst.rowBytes is less than SkImageInfo::minRowBytes
 *         - SkPixelRef is nullptr
 *
 *         Pixels are copied only if pixel conversion is possible. If SkImage SkColorType is
 *         kGray_8_SkColorType, or kAlpha_8_SkColorType; dst.colorType() must match.
 *         If SkImage SkColorType is kGray_8_SkColorType, dst.colorSpace() must match.
 *         If SkImage SkAlphaType is kOpaque_SkAlphaType, dst.alphaType() must
 *         match. If SkImage SkColorSpace is nullptr, dst.colorSpace() must match. Returns
 *         false if pixel conversion is not possible.
 *
 *         srcX and srcY may be negative to copy only top or left of source. Returns
 *         false if width() or height() is zero or negative.
 *         Returns false if abs(srcX) >= Image width(), or if abs(srcY) >= Image height().
 *
 *         If cachingHint is kAllow_CachingHint, pixels may be retained locally.
 *         If cachingHint is kDisallow_CachingHint, pixels are not added to the local cache.
 *
 *         @param context      the GrDirectContext in play, if it exists
 *         @param dst          destination SkPixmap: SkImageInfo, pixels, row bytes
 *         @param srcX         column index whose absolute value is less than width()
 *         @param srcY         row index whose absolute value is less than height()
 *         @param cachingHint  whether the pixels should be cached locallyZ
 *         @return             true if pixels are copied to dst
 *     */
 *     bool readPixels(GrDirectContext* context,
 *                     const SkPixmap& dst,
 *                     int srcX,
 *                     int srcY,
 *                     CachingHint cachingHint = kAllow_CachingHint) const;
 *
 * #ifndef SK_IMAGE_READ_PIXELS_DISABLE_LEGACY_API
 *     /** Deprecated. Use the variants that accept a GrDirectContext. */
 *     bool readPixels(const SkImageInfo& dstInfo, void* dstPixels, size_t dstRowBytes,
 *                     int srcX, int srcY, CachingHint cachingHint = kAllow_CachingHint) const;
 *     bool readPixels(const SkPixmap& dst, int srcX, int srcY,
 *                     CachingHint cachingHint = kAllow_CachingHint) const;
 * #endif
 *
 *     /** The result from asyncRescaleAndReadPixels() or asyncRescaleAndReadPixelsYUV420(). */
 *     class AsyncReadResult {
 *     public:
 *         AsyncReadResult(const AsyncReadResult&) = delete;
 *         AsyncReadResult(AsyncReadResult&&) = delete;
 *         AsyncReadResult& operator=(const AsyncReadResult&) = delete;
 *         AsyncReadResult& operator=(AsyncReadResult&&) = delete;
 *
 *         virtual ~AsyncReadResult() = default;
 *         /** Returns how many planes of data are in the result. e.g. 3 for YUV data. */
 *         virtual int count() const = 0;
 *         /** Returns the raw pixel data for a given plane.
 *          *
 *          * It will be organized as per the dst SkImageInfo passed in to the async read call.
 *          *
 *          * Clients may wish to create an SkPixmap with this data using the dst SkImageInfo
 *          * and rowBytes(i).
 *          */
 *         virtual const void* data(int i) const = 0;
 *         /** Returns how many bytes correspond to a single row of image data */
 *         virtual size_t rowBytes(int i) const = 0;
 *
 *     protected:
 *         AsyncReadResult() = default;
 *     };
 *
 *     /** Client-provided context that is passed to client-provided ReadPixelsContext. */
 *     using ReadPixelsContext = void*;
 *
 *     /**  Client-provided callback to asyncRescaleAndReadPixels() or
 *          asyncRescaleAndReadPixelsYUV420() that is called when read result is ready or on failure.
 *      */
 *     using ReadPixelsCallback = void(ReadPixelsContext, std::unique_ptr<const AsyncReadResult>);
 *
 *     enum class RescaleGamma : bool { kSrc, kLinear };
 *
 *     enum class RescaleMode {
 *         kNearest,
 *         kLinear,
 *         kRepeatedLinear,
 *         kRepeatedCubic,
 *     };
 *
 *     /** Makes image pixel data available to caller, possibly asynchronously. It can also rescale
 *         the image pixels.
 *
 *         Currently asynchronous reads are only supported in the Ganesh GPU backend and only when the
 *         underlying 3D API supports transfer buffers and CPU/GPU synchronization primitives. In all
 *         other cases this operates synchronously.
 *
 *         For the Graphite backend this API has been deprecated in favor of the equivalent API
 *         on skgpu::graphite::Context.
 *
 *         Data is read from the source sub-rectangle, is optionally converted to a linear gamma, is
 *         rescaled to the size indicated by 'info', is then converted to the color space, color type,
 *         and alpha type of 'info'. A 'srcRect' that is not contained by the bounds of the image
 *         causes failure.
 *
 *         When the pixel data is ready the caller's ReadPixelsCallback is called with a
 *         AsyncReadResult containing pixel data in the requested color type, alpha type, and color
 *         space. The AsyncReadResult will have count() == 1. Upon failure the callback is called with
 *         nullptr for AsyncReadResult. For a GPU image this flushes work but a submit must occur to
 *         guarantee a finite time before the callback is called.
 *
 *         The data is valid for the lifetime of AsyncReadResult with the exception that if the SkImage
 *         is GPU-backed the data is immediately invalidated if the context is abandoned or
 *         destroyed.
 *
 *         @param info            info of the requested pixels
 *         @param srcRect         subrectangle of image to read
 *         @param rescaleGamma    controls whether rescaling is done in the image's gamma or whether
 *                                the source data is transformed to a linear gamma before rescaling.
 *         @param rescaleMode     controls the technique (and cost) of the rescaling
 *         @param callback        function to call with result of the read
 *         @param context         passed to callback
 *     */
 *     void asyncRescaleAndReadPixels(const SkImageInfo& info,
 *                                    const SkIRect& srcRect,
 *                                    RescaleGamma rescaleGamma,
 *                                    RescaleMode rescaleMode,
 *                                    ReadPixelsCallback callback,
 *                                    ReadPixelsContext context) const;
 *
 *     /**
 *         Similar to asyncRescaleAndReadPixels but performs an additional conversion to YUV. The
 *         RGB->YUV conversion is controlled by 'yuvColorSpace'. The YUV data is returned as three
 *         planes ordered y, u, v. The u and v planes are half the width and height of the resized
 *         rectangle. The y, u, and v values are single bytes. Currently this fails if 'dstSize'
 *         width and height are not even. A 'srcRect' that is not contained by the bounds of the
 *         image causes failure.
 *
 *         When the pixel data is ready the caller's ReadPixelsCallback is called with a
 *         AsyncReadResult containing the planar data. The AsyncReadResult will have count() == 3.
 *         Upon failure the callback is called with nullptr for AsyncReadResult. For a GPU image this
 *         flushes work but a submit must occur to guarantee a finite time before the callback is
 *         called.
 *
 *         The data is valid for the lifetime of AsyncReadResult with the exception that if the SkImage
 *         is GPU-backed the data is immediately invalidated if the context is abandoned or
 *         destroyed.
 *
 *         @param yuvColorSpace  The transformation from RGB to YUV. Applied to the resized image
 *                               after it is converted to dstColorSpace.
 *         @param dstColorSpace  The color space to convert the resized image to, after rescaling.
 *         @param srcRect        The portion of the image to rescale and convert to YUV planes.
 *         @param dstSize        The size to rescale srcRect to
 *         @param rescaleGamma   controls whether rescaling is done in the image's gamma or whether
 *                               the source data is transformed to a linear gamma before rescaling.
 *         @param rescaleMode    controls the technique (and cost) of the rescaling
 *         @param callback       function to call with the planar read result
 *         @param context        passed to callback
 *      */
 *     void asyncRescaleAndReadPixelsYUV420(SkYUVColorSpace yuvColorSpace,
 *                                          sk_sp<SkColorSpace> dstColorSpace,
 *                                          const SkIRect& srcRect,
 *                                          const SkISize& dstSize,
 *                                          RescaleGamma rescaleGamma,
 *                                          RescaleMode rescaleMode,
 *                                          ReadPixelsCallback callback,
 *                                          ReadPixelsContext context) const;
 *
 *     /**
 *      * Identical to asyncRescaleAndReadPixelsYUV420 but a fourth plane is returned in the
 *      * AsyncReadResult passed to 'callback'. The fourth plane contains the alpha chanel at the
 *      * same full resolution as the Y plane.
 *      */
 *     void asyncRescaleAndReadPixelsYUVA420(SkYUVColorSpace yuvColorSpace,
 *                                           sk_sp<SkColorSpace> dstColorSpace,
 *                                           const SkIRect& srcRect,
 *                                           const SkISize& dstSize,
 *                                           RescaleGamma rescaleGamma,
 *                                           RescaleMode rescaleMode,
 *                                           ReadPixelsCallback callback,
 *                                           ReadPixelsContext context) const;
 *
 *     /** Copies SkImage to dst, scaling pixels to fit dst.width() and dst.height(), and
 *         converting pixels to match dst.colorType() and dst.alphaType(). Returns true if
 *         pixels are copied. Returns false if dst.addr() is nullptr, or dst.rowBytes() is
 *         less than dst SkImageInfo::minRowBytes.
 *
 *         Pixels are copied only if pixel conversion is possible. If SkImage SkColorType is
 *         kGray_8_SkColorType, or kAlpha_8_SkColorType; dst.colorType() must match.
 *         If SkImage SkColorType is kGray_8_SkColorType, dst.colorSpace() must match.
 *         If SkImage SkAlphaType is kOpaque_SkAlphaType, dst.alphaType() must
 *         match. If SkImage SkColorSpace is nullptr, dst.colorSpace() must match. Returns
 *         false if pixel conversion is not possible.
 *
 *         If cachingHint is kAllow_CachingHint, pixels may be retained locally.
 *         If cachingHint is kDisallow_CachingHint, pixels are not added to the local cache.
 *
 *         @param dst            destination SkPixmap: SkImageInfo, pixels, row bytes
 *         @return               true if pixels are scaled to fit dst
 *     */
 *     bool scalePixels(const SkPixmap& dst, const SkSamplingOptions&,
 *                      CachingHint cachingHint = kAllow_CachingHint) const;
 *
 *     /**
 *      * Create a new image by copying this image and scaling to fit the ImageInfo's dimensions
 *      * and converting the pixels into the ImageInfo's ColorInfo.
 *      * This is done retaining the domain (backend) of the image (e.g. gpu, raster)
 *      *
 *      * The Recorder parameter is required if the original image was created on a graphite Recorder,
 *      * but must be nullptr if it was create in some other way (e.g. GrContext, raster, deferred).
 *      *
 *      * return nullptr if the requested ColorInfo is not supported, its dimesions are out of range,
 *      *  or if the recorder is null on a graphite Image.
 *      */
 *     sk_sp<SkImage> makeScaled(SkRecorder*, const SkImageInfo&, const SkSamplingOptions&) const;
 *     sk_sp<SkImage> makeScaled(SkRecorder*,
 *                               const SkImageInfo&,
 *                               const SkSamplingOptions&,
 *                               const SkSurfaceProps&) const;
 *
 *     sk_sp<SkImage> makeScaled(const SkImageInfo& info, const SkSamplingOptions& sampling) const;
 *
 *     /** Returns encoded SkImage pixels as SkData, if SkImage was created from supported
 *         encoded stream format. Platform support for formats vary and may require building
 *         with one or more of: SK_ENCODE_JPEG, SK_ENCODE_PNG, SK_ENCODE_WEBP.
 *
 *         Returns nullptr if SkImage contents are not encoded.
 *
 *         @return  encoded SkImage, or nullptr
 *
 *         example: https://fiddle.skia.org/c/@Image_refEncodedData
 *     */
 * #if defined(SK_DISABLE_LEGACY_NONCONST_ENCODED_IMAGE_DATA)
 *     sk_sp<const SkData> refEncodedData() const;
 * #else
 *     sk_sp<SkData> refEncodedData() const;
 * #endif
 *
 *     struct RequiredProperties {
 *         bool fMipmapped = false;
 *
 *         bool operator==(const RequiredProperties& other) const {
 *             return fMipmapped == other.fMipmapped;
 *         }
 *
 *         bool operator!=(const RequiredProperties& other) const { return !(*this == other); }
 *
 *         bool operator<(const RequiredProperties& other) const {
 *             return fMipmapped < other.fMipmapped;
 *         }
 *     };
 *
 *     /** Returns subset of this image.
 *
 *         Returns nullptr if any of the following are true:
 *           - Subset is empty
 *           - Subset is not contained inside the image's bounds
 *           - Pixels in the image could not be read or copied
 *           - This image is texture-backed and the provided context is null or does not match
 *             the source image's context.
 *
 *         If the source image was texture-backed, the resulting image will be texture-backed also.
 *         Otherwise, the returned image will be raster-backed.
 *
 *         @param recorder            the recorder of the source image (nullptr is ok if the
 *                                    source image was texture-backed).
 *         @param subset              bounds of returned SkImage
 *         @param RequiredProperties  properties the returned SkImage must possess (e.g. mipmaps)
 *         @return                    the subsetted image, or nullptr
 *     */
 *     virtual sk_sp<SkImage> makeSubset(SkRecorder*,
 *                                       const SkIRect& subset,
 *                                       RequiredProperties) const = 0;
 *
 *     /**
 *      *  Returns true if the image has mipmap levels.
 *      */
 *     bool hasMipmaps() const;
 *
 *     /**
 *      *  Returns true if the image holds protected content.
 *      */
 *     bool isProtected() const;
 *
 *     /**
 *      *  Returns an image with the same "base" pixels as the this image, but with mipmap levels
 *      *  automatically generated and attached.
 *      */
 *     sk_sp<SkImage> withDefaultMipmaps() const;
 *
 *     /** Returns raster image or lazy image. Copies SkImage backed by GPU texture into
 *         CPU memory if needed. Returns original SkImage if decoded in raster bitmap,
 *         or if encoded in a stream.
 *
 *         Returns nullptr if backed by GPU texture and copy fails.
 *
 *         @return  raster image, lazy image, or nullptr
 *
 *         example: https://fiddle.skia.org/c/@Image_makeNonTextureImage
 *     */
 *     sk_sp<SkImage> makeNonTextureImage(GrDirectContext* = nullptr) const;
 *
 *     /** Returns raster image. Copies SkImage backed by GPU texture into CPU memory,
 *         or decodes SkImage from lazy image. Returns original SkImage if decoded in
 *         raster bitmap.
 *
 *         Returns nullptr if copy, decode, or pixel read fails.
 *
 *         If cachingHint is kAllow_CachingHint, pixels may be retained locally.
 *         If cachingHint is kDisallow_CachingHint, pixels are not added to the local cache.
 *
 *         @return  raster image, or nullptr
 *
 *         example: https://fiddle.skia.org/c/@Image_makeRasterImage
 *     */
 *     sk_sp<SkImage> makeRasterImage(GrDirectContext*,
 *                                    CachingHint cachingHint = kDisallow_CachingHint) const;
 *
 * #if !defined(SK_IMAGE_READ_PIXELS_DISABLE_LEGACY_API)
 *     sk_sp<SkImage> makeRasterImage(CachingHint cachingHint = kDisallow_CachingHint) const {
 *         return this->makeRasterImage(nullptr, cachingHint);
 *     }
 * #endif
 *
 *     /** Deprecated.
 *      */
 *     enum LegacyBitmapMode {
 *         kRO_LegacyBitmapMode, //!< returned bitmap is read-only and immutable
 *     };
 *
 *     /** Deprecated.
 *         Creates raster SkBitmap with same pixels as SkImage. If legacyBitmapMode is
 *         kRO_LegacyBitmapMode, returned bitmap is read-only and immutable.
 *         Returns true if SkBitmap is stored in bitmap. Returns false and resets bitmap if
 *         SkBitmap write did not succeed.
 *
 *         @param bitmap            storage for legacy SkBitmap
 *         @param legacyBitmapMode  bitmap is read-only and immutable
 *         @return                  true if SkBitmap was created
 *     */
 *     bool asLegacyBitmap(SkBitmap* bitmap,
 *                         LegacyBitmapMode legacyBitmapMode = kRO_LegacyBitmapMode) const;
 *
 *     /** Returns true if SkImage is backed by an image-generator or other service that creates
 *         and caches its pixels or texture on-demand.
 *
 *         @return  true if SkImage is created as needed
 *
 *         example: https://fiddle.skia.org/c/@Image_isLazyGenerated_a
 *         example: https://fiddle.skia.org/c/@Image_isLazyGenerated_b
 *     */
 *     virtual bool isLazyGenerated() const = 0;
 *
 *     /** Creates SkImage in target SkColorSpace.
 *         Returns nullptr if SkImage could not be created.
 *
 *         Returns original SkImage if it is in target SkColorSpace.
 *         Otherwise, converts pixels from SkImage SkColorSpace to target SkColorSpace.
 *         If SkImage colorSpace() returns nullptr, SkImage SkColorSpace is assumed to be sRGB.
 *
 *         If this image is graphite-backed, the recorder parameter is required.
 *
 *         @param targetColorSpace    SkColorSpace describing color range of returned SkImage
 *         @param recorder            The Recorder in which to create the new image
 *         @param RequiredProperties  properties the returned SkImage must possess (e.g. mipmaps)
 *         @return                    created SkImage in target SkColorSpace
 *     */
 *     virtual sk_sp<SkImage> makeColorSpace(SkRecorder*,
 *                                           sk_sp<SkColorSpace> targetColorSpace,
 *                                           RequiredProperties) const = 0;
 *
 *     /** Experimental.
 *         Creates SkImage in target SkColorType and SkColorSpace.
 *         Returns nullptr if SkImage could not be created.
 *
 *         Returns original SkImage if it is in target SkColorType and SkColorSpace.
 *
 *         If this image is graphite-backed, the recorder parameter is required.
 *
 *         @param targetColorType     SkColorType of returned SkImage
 *         @param targetColorSpace    SkColorSpace of returned SkImage
 *         @param recorder            The Recorder in which to create the new image
 *         @param RequiredProperties  properties the returned SkImage must possess (e.g. mipmaps)
 *         @return                    created SkImage in target SkColorType and SkColorSpace
 *     */
 *     virtual sk_sp<SkImage> makeColorTypeAndColorSpace(SkRecorder*,
 *                                                       SkColorType targetColorType,
 *                                                       sk_sp<SkColorSpace> targetColorSpace,
 *                                                       RequiredProperties) const = 0;
 *
 *     /** Creates a new SkImage identical to this one, but with a different SkColorSpace.
 *         This does not convert the underlying pixel data, so the resulting image will draw
 *         differently.
 *     */
 *     sk_sp<SkImage> reinterpretColorSpace(sk_sp<SkColorSpace> newColorSpace) const;
 *
 * private:
 *     SkImage(const SkImageInfo& info, uint32_t uniqueID);
 *
 *     friend class SkBitmap;
 *     friend class SkImage_Base;   // for private ctor
 *     friend class SkImage_Raster; // for withMipmaps
 *     friend class SkMipmapBuilder;
 *
 *     SkImageInfo     fInfo;
 *     const uint32_t  fUniqueID;
 *
 *     sk_sp<SkImage> withMipmaps(sk_sp<SkMipmap>) const;
 *
 *     using INHERITED = SkRefCnt;
 * }
 * ```
 */
public abstract class SkImage public constructor(
  info: SkImageInfo,
  uniqueID: UInt,
) : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * SkImageInfo     fInfo
   * ```
   */
  private var fInfo: Int = TODO("Initialize fInfo")

  /**
   * C++ original:
   * ```cpp
   * const uint32_t  fUniqueID
   * ```
   */
  private val fUniqueID: UInt = TODO("Initialize fUniqueID")

  /**
   * C++ original:
   * ```cpp
   * const SkImageInfo& imageInfo() const { return fInfo; }
   * ```
   */
  public fun imageInfo(): Int {
    TODO("Implement imageInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * int width() const { return fInfo.width(); }
   * ```
   */
  public fun width(): Int {
    TODO("Implement width")
  }

  /**
   * C++ original:
   * ```cpp
   * int height() const { return fInfo.height(); }
   * ```
   */
  public fun height(): Int {
    TODO("Implement height")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize dimensions() const { return SkISize::Make(fInfo.width(), fInfo.height()); }
   * ```
   */
  public fun dimensions(): Int {
    TODO("Implement dimensions")
  }

  /**
   * C++ original:
   * ```cpp
   * SkIRect bounds() const { return SkIRect::MakeWH(fInfo.width(), fInfo.height()); }
   * ```
   */
  public fun bounds(): Int {
    TODO("Implement bounds")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t uniqueID() const { return fUniqueID; }
   * ```
   */
  public fun uniqueID(): UInt {
    TODO("Implement uniqueID")
  }

  /**
   * C++ original:
   * ```cpp
   * SkAlphaType SkImage::alphaType() const { return fInfo.alphaType(); }
   * ```
   */
  public fun alphaType(): Int {
    TODO("Implement alphaType")
  }

  /**
   * C++ original:
   * ```cpp
   * SkColorType SkImage::colorType() const { return fInfo.colorType(); }
   * ```
   */
  public fun colorType(): SkColorType {
    TODO("Implement colorType")
  }

  /**
   * C++ original:
   * ```cpp
   * SkColorSpace* SkImage::colorSpace() const { return fInfo.colorSpace(); }
   * ```
   */
  public fun colorSpace(): SkColorSpace {
    TODO("Implement colorSpace")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorSpace> SkImage::refColorSpace() const { return fInfo.refColorSpace(); }
   * ```
   */
  public fun refColorSpace(): Int {
    TODO("Implement refColorSpace")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkImage::isAlphaOnly() const { return SkColorTypeIsAlphaOnly(fInfo.colorType()); }
   * ```
   */
  public fun isAlphaOnly(): Boolean {
    TODO("Implement isAlphaOnly")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isOpaque() const { return SkAlphaTypeIsOpaque(this->alphaType()); }
   * ```
   */
  public fun isOpaque(): Boolean {
    TODO("Implement isOpaque")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> makeShader(SkTileMode tmx, SkTileMode tmy, const SkSamplingOptions&,
   *                                const SkMatrix* localMatrix = nullptr) const
   * ```
   */
  public fun makeShader(
    tmx: SkTileMode,
    tmy: SkTileMode,
    param2: SkSamplingOptions,
    localMatrix: SkMatrix? = null,
  ): Int {
    TODO("Implement makeShader")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> makeShader(SkTileMode tmx, SkTileMode tmy, const SkSamplingOptions& sampling,
   *                                const SkMatrix& lm) const
   * ```
   */
  public fun makeShader(
    tmx: SkTileMode,
    tmy: SkTileMode,
    sampling: SkSamplingOptions,
    lm: SkMatrix,
  ): Int {
    TODO("Implement makeShader")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> makeShader(const SkSamplingOptions& sampling, const SkMatrix& lm) const
   * ```
   */
  public fun makeShader(sampling: SkSamplingOptions, lm: SkMatrix): Int {
    TODO("Implement makeShader")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> makeShader(const SkSamplingOptions& sampling,
   *                                const SkMatrix* lm = nullptr) const
   * ```
   */
  public fun makeShader(sampling: SkSamplingOptions, lm: SkMatrix? = null): Int {
    TODO("Implement makeShader")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> makeRawShader(SkTileMode tmx, SkTileMode tmy, const SkSamplingOptions&,
   *                                   const SkMatrix* localMatrix = nullptr) const
   * ```
   */
  public fun makeRawShader(
    tmx: SkTileMode,
    tmy: SkTileMode,
    param2: SkSamplingOptions,
    localMatrix: SkMatrix? = null,
  ): Int {
    TODO("Implement makeRawShader")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> makeRawShader(SkTileMode tmx, SkTileMode tmy, const SkSamplingOptions& sampling,
   *                                   const SkMatrix& lm) const
   * ```
   */
  public fun makeRawShader(
    tmx: SkTileMode,
    tmy: SkTileMode,
    sampling: SkSamplingOptions,
    lm: SkMatrix,
  ): Int {
    TODO("Implement makeRawShader")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> makeRawShader(const SkSamplingOptions& sampling, const SkMatrix& lm) const
   * ```
   */
  public fun makeRawShader(sampling: SkSamplingOptions, lm: SkMatrix): Int {
    TODO("Implement makeRawShader")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> makeRawShader(const SkSamplingOptions& sampling,
   *                                   const SkMatrix* lm = nullptr) const
   * ```
   */
  public fun makeRawShader(sampling: SkSamplingOptions, lm: SkMatrix? = null): Int {
    TODO("Implement makeRawShader")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkImage::peekPixels(SkPixmap* pm) const {
   *     SkPixmap tmp;
   *     if (!pm) {
   *         pm = &tmp;
   *     }
   *     return as_IB(this)->onPeekPixels(pm);
   * }
   * ```
   */
  public fun peekPixels(pixmap: SkPixmap?): Boolean {
    TODO("Implement peekPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool isTextureBacked() const = 0
   * ```
   */
  public abstract fun isTextureBacked(): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual size_t textureSize() const = 0
   * ```
   */
  public abstract fun textureSize(): ULong

  /**
   * C++ original:
   * ```cpp
   * virtual bool isValid(SkRecorder*) const = 0
   * ```
   */
  public abstract fun isValid(param0: SkRecorder?): Boolean

  /**
   * C++ original:
   * ```cpp
   * bool SkImage::readPixels(GrDirectContext* dContext, const SkImageInfo& dstInfo, void* dstPixels,
   *                          size_t dstRowBytes, int srcX, int srcY, CachingHint chint) const {
   *     return as_IB(this)->onReadPixels(dContext, dstInfo, dstPixels, dstRowBytes, srcX, srcY, chint);
   * }
   * ```
   */
  public fun readPixels(
    context: GrDirectContext?,
    dstInfo: SkImageInfo,
    dstPixels: Unit?,
    dstRowBytes: ULong,
    srcX: Int,
    srcY: Int,
    cachingHint: CachingHint = TODO(),
  ): Boolean {
    TODO("Implement readPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkImage::readPixels(GrDirectContext* dContext, const SkPixmap& pmap, int srcX, int srcY,
   *                          CachingHint chint) const {
   *     return this->readPixels(dContext, pmap.info(), pmap.writable_addr(), pmap.rowBytes(), srcX,
   *                             srcY, chint);
   * }
   * ```
   */
  public fun readPixels(
    context: GrDirectContext?,
    dst: SkPixmap,
    srcX: Int,
    srcY: Int,
    cachingHint: CachingHint = TODO(),
  ): Boolean {
    TODO("Implement readPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkImage::readPixels(const SkImageInfo& dstInfo, void* dstPixels,
   *                          size_t dstRowBytes, int srcX, int srcY, CachingHint chint) const {
   *     auto dContext = as_IB(this)->directContext();
   *     return this->readPixels(dContext, dstInfo, dstPixels, dstRowBytes, srcX, srcY, chint);
   * }
   * ```
   */
  public fun readPixels(
    dstInfo: SkImageInfo,
    dstPixels: Unit?,
    dstRowBytes: ULong,
    srcX: Int,
    srcY: Int,
    cachingHint: CachingHint = TODO(),
  ): Boolean {
    TODO("Implement readPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkImage::readPixels(const SkPixmap& pmap, int srcX, int srcY, CachingHint chint) const {
   *     auto dContext = as_IB(this)->directContext();
   *     return this->readPixels(dContext, pmap, srcX, srcY, chint);
   * }
   * ```
   */
  public fun readPixels(
    dst: SkPixmap,
    srcX: Int,
    srcY: Int,
    cachingHint: CachingHint = TODO(),
  ): Boolean {
    TODO("Implement readPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkImage::asyncRescaleAndReadPixels(const SkImageInfo& info,
   *                                         const SkIRect& srcRect,
   *                                         RescaleGamma rescaleGamma,
   *                                         RescaleMode rescaleMode,
   *                                         ReadPixelsCallback callback,
   *                                         ReadPixelsContext context) const {
   *     if (!SkIRect::MakeWH(this->width(), this->height()).contains(srcRect) ||
   *         !SkImageInfoIsValid(info)) {
   *         callback(context, nullptr);
   *         return;
   *     }
   *     as_IB(this)->onAsyncRescaleAndReadPixels(
   *             info, srcRect, rescaleGamma, rescaleMode, callback, context);
   * }
   * ```
   */
  protected fun asyncRescaleAndReadPixels(
    info: SkImageInfo,
    srcRect: SkIRect,
    rescaleGamma: RescaleGamma,
    rescaleMode: RescaleMode,
    callback: SkImageReadPixelsCallback,
    context: SkImageReadPixelsContext,
  ) {
    TODO("Implement asyncRescaleAndReadPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkImage::asyncRescaleAndReadPixelsYUV420(SkYUVColorSpace yuvColorSpace,
   *                                               sk_sp<SkColorSpace> dstColorSpace,
   *                                               const SkIRect& srcRect,
   *                                               const SkISize& dstSize,
   *                                               RescaleGamma rescaleGamma,
   *                                               RescaleMode rescaleMode,
   *                                               ReadPixelsCallback callback,
   *                                               ReadPixelsContext context) const {
   *     if (!SkIRect::MakeWH(this->width(), this->height()).contains(srcRect) || dstSize.isZero() ||
   *         (dstSize.width() & 0b1) || (dstSize.height() & 0b1)) {
   *         callback(context, nullptr);
   *         return;
   *     }
   *     as_IB(this)->onAsyncRescaleAndReadPixelsYUV420(yuvColorSpace,
   *                                                    /*readAlpha=*/false,
   *                                                    std::move(dstColorSpace),
   *                                                    srcRect,
   *                                                    dstSize,
   *                                                    rescaleGamma,
   *                                                    rescaleMode,
   *                                                    callback,
   *                                                    context);
   * }
   * ```
   */
  protected fun asyncRescaleAndReadPixelsYUV420(
    yuvColorSpace: SkYUVColorSpace,
    dstColorSpace: SkSp<SkColorSpace>,
    srcRect: SkIRect,
    dstSize: SkISize,
    rescaleGamma: RescaleGamma,
    rescaleMode: RescaleMode,
    callback: SkImageReadPixelsCallback,
    context: SkImageReadPixelsContext,
  ) {
    TODO("Implement asyncRescaleAndReadPixelsYUV420")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkImage::asyncRescaleAndReadPixelsYUVA420(SkYUVColorSpace yuvColorSpace,
   *                                                sk_sp<SkColorSpace> dstColorSpace,
   *                                                const SkIRect& srcRect,
   *                                                const SkISize& dstSize,
   *                                                RescaleGamma rescaleGamma,
   *                                                RescaleMode rescaleMode,
   *                                                ReadPixelsCallback callback,
   *                                                ReadPixelsContext context) const {
   *     if (!SkIRect::MakeWH(this->width(), this->height()).contains(srcRect) || dstSize.isZero() ||
   *         (dstSize.width() & 0b1) || (dstSize.height() & 0b1)) {
   *         callback(context, nullptr);
   *         return;
   *     }
   *     as_IB(this)->onAsyncRescaleAndReadPixelsYUV420(yuvColorSpace,
   *                                                    /*readAlpha=*/true,
   *                                                    std::move(dstColorSpace),
   *                                                    srcRect,
   *                                                    dstSize,
   *                                                    rescaleGamma,
   *                                                    rescaleMode,
   *                                                    callback,
   *                                                    context);
   * }
   * ```
   */
  protected fun asyncRescaleAndReadPixelsYUVA420(
    yuvColorSpace: SkYUVColorSpace,
    dstColorSpace: SkSp<SkColorSpace>,
    srcRect: SkIRect,
    dstSize: SkISize,
    rescaleGamma: RescaleGamma,
    rescaleMode: RescaleMode,
    callback: SkImageReadPixelsCallback,
    context: SkImageReadPixelsContext,
  ) {
    TODO("Implement asyncRescaleAndReadPixelsYUVA420")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkImage::scalePixels(const SkPixmap& dst, const SkSamplingOptions& sampling,
   *                           CachingHint chint) const {
   *     // Context TODO: Elevate GrDirectContext requirement to public API.
   *     auto dContext = as_IB(this)->directContext();
   *     if (this->width() == dst.width() && this->height() == dst.height()) {
   *         return this->readPixels(dContext, dst, 0, 0, chint);
   *     }
   *
   *     // Idea: If/when SkImageGenerator supports a native-scaling API (where the generator itself
   *     //       can scale more efficiently) we should take advantage of it here.
   *     //
   *     SkBitmap bm;
   *     if (as_IB(this)->getROPixels(dContext, &bm, chint)) {
   *         SkPixmap pmap;
   *         // Note: By calling the pixmap scaler, we never cache the final result, so the chint
   *         //       is (currently) only being applied to the getROPixels. If we get a request to
   *         //       also attempt to cache the final (scaled) result, we would add that logic here.
   *         //
   *         return bm.peekPixels(&pmap) && pmap.scalePixels(dst, sampling);
   *     }
   *     return false;
   * }
   * ```
   */
  protected fun scalePixels(
    dst: SkPixmap,
    sampling: SkSamplingOptions,
    cachingHint: CachingHint = TODO(),
  ): Boolean {
    TODO("Implement scalePixels")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> SkImage::makeScaled(SkRecorder* recorder,
   *                                    const SkImageInfo& newInfo,
   *                                    const SkSamplingOptions& sampling) const {
   *     return makeScaled(recorder, newInfo, sampling, SkSurfaceProps{});
   * }
   * ```
   */
  protected fun makeScaled(
    recorder: SkRecorder?,
    newInfo: SkImageInfo,
    sampling: SkSamplingOptions,
  ): Int {
    TODO("Implement makeScaled")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> SkImage::makeScaled(SkRecorder* recorder,
   *                                    const SkImageInfo& newInfo,
   *                                    const SkSamplingOptions& sampling,
   *                                    const SkSurfaceProps& props) const {
   *     if (!SkImageInfoIsValid(newInfo)) {
   *         return nullptr;
   *     }
   *     if (newInfo == this->imageInfo()) {
   *         return sk_ref_sp(this);
   *     }
   *
   *     auto surf = as_IB(this)->onMakeSurface(recorder, newInfo);
   *     if (!surf) {
   *         return nullptr;
   *     }
   *
   *     SkPaint paint;
   *     paint.setBlendMode(SkBlendMode::kSrc);
   *     surf->getCanvas()->drawImageRect(this,
   *                                      SkRect::MakeIWH(newInfo.width(), newInfo.height()),
   *                                      sampling,
   *                                      &paint);
   *     return surf->makeImageSnapshot();
   * }
   * ```
   */
  protected fun makeScaled(
    recorder: SkRecorder?,
    newInfo: SkImageInfo,
    sampling: SkSamplingOptions,
    props: SkSurfaceProps,
  ): Int {
    TODO("Implement makeScaled")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> SkImage::makeScaled(const SkImageInfo& newInfo,
   *                                    const SkSamplingOptions& sampling) const {
   *     return makeScaled(nullptr, newInfo, sampling, SkSurfaceProps{});
   * }
   * ```
   */
  protected fun makeScaled(info: SkImageInfo, sampling: SkSamplingOptions): Int {
    TODO("Implement makeScaled")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkData> SkImage::refEncodedData() const {
   *     if (const SkData* data = as_IB(this)->onRefEncoded().release()) {
   *         return sk_sp<SkData>(const_cast<SkData*>(data));
   *     }
   *     return nullptr;
   * }
   * ```
   */
  protected fun refEncodedData(): Int {
    TODO("Implement refEncodedData")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<SkImage> makeSubset(SkRecorder*,
   *                                       const SkIRect& subset,
   *                                       RequiredProperties) const = 0
   * ```
   */
  protected abstract fun makeSubset(
    param0: SkRecorder?,
    subset: SkIRect,
    param2: RequiredProperties,
  ): Int

  /**
   * C++ original:
   * ```cpp
   * bool SkImage::hasMipmaps() const { return as_IB(this)->onHasMipmaps(); }
   * ```
   */
  protected fun hasMipmaps(): Boolean {
    TODO("Implement hasMipmaps")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkImage::isProtected() const { return as_IB(this)->onIsProtected(); }
   * ```
   */
  protected fun isProtected(): Boolean {
    TODO("Implement isProtected")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> SkImage::withDefaultMipmaps() const {
   *     return this->withMipmaps(nullptr);
   * }
   * ```
   */
  protected fun withDefaultMipmaps(): Int {
    TODO("Implement withDefaultMipmaps")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> SkImage::makeNonTextureImage(GrDirectContext* dContext) const {
   *     if (!this->isTextureBacked()) {
   *         return sk_ref_sp(const_cast<SkImage*>(this));
   *     }
   *     return this->makeRasterImage(dContext, kDisallow_CachingHint);
   * }
   * ```
   */
  protected fun makeNonTextureImage(dContext: GrDirectContext? = null): Int {
    TODO("Implement makeNonTextureImage")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> SkImage::makeRasterImage(GrDirectContext* dContext, CachingHint chint) const {
   *     SkPixmap pm;
   *     if (this->peekPixels(&pm)) {
   *         return sk_ref_sp(const_cast<SkImage*>(this));
   *     }
   *
   *     const size_t rowBytes = fInfo.minRowBytes();
   *     size_t size = fInfo.computeByteSize(rowBytes);
   *     if (SkImageInfo::ByteSizeOverflowed(size)) {
   *         return nullptr;
   *     }
   *
   *     if (!dContext) {
   *         // Try to get the saved context if the client didn't pass it in (but they really should).
   *         dContext = as_IB(this)->directContext();
   *     }
   *     sk_sp<SkData> data = SkData::MakeUninitialized(size);
   *     pm = {fInfo.makeColorSpace(nullptr), data->writable_data(), fInfo.minRowBytes()};
   *     if (!this->readPixels(dContext, pm, 0, 0, chint)) {
   *         return nullptr;
   *     }
   *
   *     return SkImages::RasterFromData(fInfo, std::move(data), rowBytes);
   * }
   * ```
   */
  protected fun makeRasterImage(dContext: GrDirectContext?, cachingHint: CachingHint = TODO()): Int {
    TODO("Implement makeRasterImage")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> makeRasterImage(CachingHint cachingHint = kDisallow_CachingHint) const {
   *         return this->makeRasterImage(nullptr, cachingHint);
   *     }
   * ```
   */
  protected fun makeRasterImage(cachingHint: CachingHint = TODO()): Int {
    TODO("Implement makeRasterImage")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkImage::asLegacyBitmap(SkBitmap* bitmap, LegacyBitmapMode ) const {
   *     // Context TODO: Elevate GrDirectContext requirement to public API.
   *     auto dContext = as_IB(this)->directContext();
   *     return as_IB(this)->onAsLegacyBitmap(dContext, bitmap);
   * }
   * ```
   */
  protected fun asLegacyBitmap(bitmap: SkBitmap?, legacyBitmapMode: LegacyBitmapMode = TODO()): Boolean {
    TODO("Implement asLegacyBitmap")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool isLazyGenerated() const = 0
   * ```
   */
  protected abstract fun isLazyGenerated(): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<SkImage> makeColorSpace(SkRecorder*,
   *                                           sk_sp<SkColorSpace> targetColorSpace,
   *                                           RequiredProperties) const = 0
   * ```
   */
  protected abstract fun makeColorSpace(
    param0: SkRecorder?,
    targetColorSpace: SkSp<SkColorSpace>,
    param2: RequiredProperties,
  ): Int

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<SkImage> makeColorTypeAndColorSpace(SkRecorder*,
   *                                                       SkColorType targetColorType,
   *                                                       sk_sp<SkColorSpace> targetColorSpace,
   *                                                       RequiredProperties) const = 0
   * ```
   */
  protected abstract fun makeColorTypeAndColorSpace(
    param0: SkRecorder?,
    targetColorType: SkColorType,
    targetColorSpace: SkSp<SkColorSpace>,
    param3: RequiredProperties,
  ): Int

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> SkImage::reinterpretColorSpace(sk_sp<SkColorSpace> target) const {
   *     if (!target) {
   *         return nullptr;
   *     }
   *
   *     // No need to create a new image if:
   *     // (1) The color spaces are equal.
   *     // (2) The color type is kAlpha8.
   *     SkColorSpace* colorSpace = this->colorSpace();
   *     if (!colorSpace) {
   *         colorSpace = sk_srgb_singleton();
   *     }
   *     if (SkColorSpace::Equals(colorSpace, target.get()) || this->isAlphaOnly()) {
   *         return sk_ref_sp(const_cast<SkImage*>(this));
   *     }
   *
   *     return as_IB(this)->onReinterpretColorSpace(std::move(target));
   * }
   * ```
   */
  protected fun reinterpretColorSpace(newColorSpace: SkSp<SkColorSpace>): Int {
    TODO("Implement reinterpretColorSpace")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> SkImage::withMipmaps(sk_sp<SkMipmap> mips) const {
   *     if (mips == nullptr || mips->validForRootLevel(this->imageInfo())) {
   *         if (auto result = as_IB(this)->onMakeWithMipmaps(std::move(mips))) {
   *             return result;
   *         }
   *     }
   *     return sk_ref_sp((const_cast<SkImage*>(this)));
   * }
   * ```
   */
  private fun withMipmaps(mips: SkSp<SkMipmap>): Int {
    TODO("Implement withMipmaps")
  }

  public abstract class AsyncReadResult public constructor(
    param0: undefined.AsyncReadResult,
  ) {
    public constructor() : this() {
      TODO("Implement constructor")
    }

    public fun assign(param0: undefined.AsyncReadResult) {
      TODO("Implement assign")
    }

    public abstract fun count(): Int

    public abstract fun `data`(i: Int)

    public abstract fun rowBytes(i: Int): ULong
  }

  public data class RequiredProperties public constructor(
    public var fMipmapped: Boolean,
  ) {
    public override operator fun equals(other: Any?): Boolean {
      TODO("Implement equals")
    }

    public operator fun compareTo(other: undefined.RequiredProperties): Int {
      TODO("Implement compareTo")
    }
  }

  public enum class CachingHint {
    kAllow_CachingHint,
    kDisallow_CachingHint,
  }

  public enum class RescaleGamma {
    kSrc,
    kLinear,
  }

  public enum class RescaleMode {
    kNearest,
    kLinear,
    kRepeatedLinear,
    kRepeatedCubic,
  }

  public enum class LegacyBitmapMode {
    kRO_LegacyBitmapMode,
  }
}
