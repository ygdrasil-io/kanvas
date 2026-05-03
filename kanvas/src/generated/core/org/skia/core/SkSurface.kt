package org.skia.core

import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPixmap
import org.skia.foundation.SkRefCnt
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkSp
import org.skia.foundation.SkYUVColorSpace
import org.skia.gpu.Recorder
import org.skia.gpu.RescaleGamma
import org.skia.gpu.RescaleMode
import org.skia.gpu.ganesh.GrBackendSemaphore
import org.skia.gpu.ganesh.GrBackendTexture
import org.skia.gpu.ganesh.GrRecordingContext
import org.skia.gpu.ganesh.GrSurfaceOrigin
import org.skia.math.SkIRect
import org.skia.math.SkISize
import org.skia.math.SkScalar
import org.skia.utils.GrSurfaceCharacterization

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSurface : public SkRefCnt {
 * public:
 *     /** Is this surface compatible with the provided characterization?
 *
 *         This method can be used to determine if an existing SkSurface is a viable destination
 *         for an GrDeferredDisplayList.
 *
 *         @param characterization  The characterization for which a compatibility check is desired
 *         @return                  true if this surface is compatible with the characterization;
 *                                  false otherwise
 *     */
 *     bool isCompatible(const GrSurfaceCharacterization& characterization) const;
 *
 *     /** Returns pixel count in each row; may be zero or greater.
 *
 *         @return  number of pixel columns
 *     */
 *     int width() const { return fWidth; }
 *
 *     /** Returns pixel row count; may be zero or greater.
 *
 *         @return  number of pixel rows
 *     */
 *     int height() const { return fHeight; }
 *
 *     /** Returns an ImageInfo describing the surface.
 *      */
 *     virtual SkImageInfo imageInfo() const { return SkImageInfo::MakeUnknown(fWidth, fHeight); }
 *
 *     /** Returns unique value identifying the content of SkSurface. Returned value changes
 *         each time the content changes. Content is changed by drawing, or by calling
 *         notifyContentWillChange().
 *
 *         @return  unique content identifier
 *
 *         example: https://fiddle.skia.org/c/@Surface_notifyContentWillChange
 *     */
 *     uint32_t generationID();
 *
 *     /** \enum SkSurface::ContentChangeMode
 *         ContentChangeMode members are parameters to notifyContentWillChange().
 *     */
 *     enum ContentChangeMode {
 *         kDiscard_ContentChangeMode, //!< discards surface on change
 *         kRetain_ContentChangeMode,  //!< preserves surface on change
 *     };
 *
 *     /** Notifies that SkSurface contents will be changed by code outside of Skia.
 *         Subsequent calls to generationID() return a different value.
 *
 *         TODO: Can kRetain_ContentChangeMode be deprecated?
 *
 *         example: https://fiddle.skia.org/c/@Surface_notifyContentWillChange
 *     */
 *     void notifyContentWillChange(ContentChangeMode mode);
 *
 *     /** Returns the recording context being used by the SkSurface.
 *
 *         @return the recording context, if available; nullptr otherwise
 *      */
 *     GrRecordingContext* recordingContext() const;
 *
 *     /** Returns the recorder being used by the SkSurface.
 *
 *         @return the recorder, if available; nullptr otherwise
 *      */
 *     skgpu::graphite::Recorder* recorder() const;
 *
 *     /** Returns the base SkRecorder being used by the SkSurface.
 *
 *         @return the recorder; should be non-null for drawable surfaces
 *     */
 *     SkRecorder* baseRecorder() const;
 *
 *     enum class BackendHandleAccess {
 *         kFlushRead,     //!< back-end object is readable
 *         kFlushWrite,    //!< back-end object is writable
 *         kDiscardWrite,  //!< back-end object must be overwritten
 *
 *         // Legacy names, remove when clients are migrated
 *         kFlushRead_BackendHandleAccess = kFlushRead,
 *         kFlushWrite_BackendHandleAccess = kFlushWrite,
 *         kDiscardWrite_BackendHandleAccess = kDiscardWrite,
 *     };
 *
 *     // Legacy names, remove when clients are migrated
 *     static constexpr BackendHandleAccess kFlushRead_BackendHandleAccess =
 *             BackendHandleAccess::kFlushRead;
 *     static constexpr BackendHandleAccess kFlushWrite_BackendHandleAccess =
 *             BackendHandleAccess::kFlushWrite;
 *     static constexpr BackendHandleAccess kDiscardWrite_BackendHandleAccess =
 *             BackendHandleAccess::kDiscardWrite;
 *
 *     /** Caller data passed to TextureReleaseProc; may be nullptr. */
 *     using ReleaseContext = void*;
 *     /** User function called when supplied texture may be deleted. */
 *     using TextureReleaseProc = void (*)(ReleaseContext);
 *
 *     /** If the surface was made via MakeFromBackendTexture then it's backing texture may be
 *         substituted with a different texture. The contents of the previous backing texture are
 *         copied into the new texture. SkCanvas state is preserved. The original sample count is
 *         used. The GrBackendFormat and dimensions of replacement texture must match that of
 *         the original.
 *
 *         Upon success textureReleaseProc is called when it is safe to delete the texture in the
 *         backend API (accounting only for use of the texture by this surface). If SkSurface creation
 *         fails textureReleaseProc is called before this function returns.
 *
 *         @param backendTexture      the new backing texture for the surface
 *         @param mode                Retain or discard current Content
 *         @param TextureReleaseProc  function called when texture can be released
 *         @param ReleaseContext      state passed to textureReleaseProc
 *      */
 *     virtual bool replaceBackendTexture(const GrBackendTexture& backendTexture,
 *                                        GrSurfaceOrigin origin,
 *                                        ContentChangeMode mode = kRetain_ContentChangeMode,
 *                                        TextureReleaseProc = nullptr,
 *                                        ReleaseContext = nullptr) = 0;
 *
 *     /** Returns SkCanvas that draws into SkSurface. Subsequent calls return the same SkCanvas.
 *         SkCanvas returned is managed and owned by SkSurface, and is deleted when SkSurface
 *         is deleted.
 *
 *         @return  drawing SkCanvas for SkSurface
 *
 *         example: https://fiddle.skia.org/c/@Surface_getCanvas
 *     */
 *     SkCanvas* getCanvas();
 *
 *     /** Returns SkCapabilities that describes the capabilities of the SkSurface's device.
 *
 *         @return  SkCapabilities of SkSurface's device.
 *     */
 *     sk_sp<const SkCapabilities> capabilities();
 *
 *     /** Returns a compatible SkSurface, or nullptr. Returned SkSurface contains
 *         the same raster, GPU, or null properties as the original. Returned SkSurface
 *         does not share the same pixels.
 *
 *         Returns nullptr if imageInfo width or height are zero, or if imageInfo
 *         is incompatible with SkSurface.
 *
 *         @param imageInfo  width, height, SkColorType, SkAlphaType, SkColorSpace,
 *                           of SkSurface; width and height must be greater than zero
 *         @return           compatible SkSurface or nullptr
 *
 *         example: https://fiddle.skia.org/c/@Surface_makeSurface
 *     */
 *     sk_sp<SkSurface> makeSurface(const SkImageInfo& imageInfo);
 *
 *     /** Calls makeSurface(ImageInfo) with the same ImageInfo as this surface, but with the
 *      *  specified width and height.
 *      */
 *     sk_sp<SkSurface> makeSurface(int width, int height);
 *
 *     /** Returns SkImage capturing SkSurface contents. Subsequent drawing to SkSurface contents
 *         are not captured. SkImage allocation is accounted for if SkSurface was created with
 *         skgpu::Budgeted::kYes.
 *
 *         @return  SkImage initialized with SkSurface contents
 *
 *         example: https://fiddle.skia.org/c/@Surface_makeImageSnapshot
 *     */
 *     sk_sp<SkImage> makeImageSnapshot();
 *
 *     /**
 *      *  Like the no-parameter version, this returns an image of the current surface contents.
 *      *  This variant takes a rectangle specifying the subset of the surface that is of interest.
 *      *  These bounds will be sanitized before being used.
 *      *  - If bounds extends beyond the surface, it will be trimmed to just the intersection of
 *      *    it and the surface.
 *      *  - If bounds does not intersect the surface, then this returns nullptr.
 *      *  - If bounds == the surface, then this is the same as calling the no-parameter variant.
 *
 *         example: https://fiddle.skia.org/c/@Surface_makeImageSnapshot_2
 *      */
 *     sk_sp<SkImage> makeImageSnapshot(const SkIRect& bounds);
 *
 *     /** Returns an SkImage capturing the current SkSurface contents. However, the contents of the
 *         SkImage are only valid as long as no other writes to the SkSurface occur. If writes to the
 *         original SkSurface happen then contents of the SkImage are undefined. However, continued use
 *         of the SkImage should not cause crashes or similar fatal behavior.
 *
 *         This API is useful for cases where the client either immediately destroys the SkSurface
 *         after the SkImage is created or knows they will destroy the SkImage before writing to the
 *         SkSurface again.
 *
 *         This API can be more performant than makeImageSnapshot as it never does an internal copy
 *         of the data assuming the user frees either the SkImage or SkSurface as described above.
 *      */
 *     sk_sp<SkImage> makeTemporaryImage();
 *
 *     /** Draws SkSurface contents to canvas, with its top-left corner at (x, y).
 *
 *         If SkPaint paint is not nullptr, apply SkColorFilter, alpha, SkImageFilter, and SkBlendMode.
 *
 *         @param canvas  SkCanvas drawn into
 *         @param x       horizontal offset in SkCanvas
 *         @param y       vertical offset in SkCanvas
 *         @param sampling what technique to use when sampling the surface pixels
 *         @param paint   SkPaint containing SkBlendMode, SkColorFilter, SkImageFilter,
 *                        and so on; or nullptr
 *
 *         example: https://fiddle.skia.org/c/@Surface_draw
 *     */
 *     void draw(SkCanvas* canvas, SkScalar x, SkScalar y, const SkSamplingOptions& sampling,
 *               const SkPaint* paint);
 *
 *     void draw(SkCanvas* canvas, SkScalar x, SkScalar y, const SkPaint* paint = nullptr) {
 *         this->draw(canvas, x, y, SkSamplingOptions(), paint);
 *     }
 *
 *     /** Copies SkSurface pixel address, row bytes, and SkImageInfo to SkPixmap, if address
 *         is available, and returns true. If pixel address is not available, return
 *         false and leave SkPixmap unchanged.
 *
 *         pixmap contents become invalid on any future change to SkSurface.
 *
 *         @param pixmap  storage for pixel state if pixels are readable; otherwise, ignored
 *         @return        true if SkSurface has direct access to pixels
 *
 *         example: https://fiddle.skia.org/c/@Surface_peekPixels
 *     */
 *     bool peekPixels(SkPixmap* pixmap);
 *
 *     /** Copies SkRect of pixels to dst.
 *
 *         Source SkRect corners are (srcX, srcY) and SkSurface (width(), height()).
 *         Destination SkRect corners are (0, 0) and (dst.width(), dst.height()).
 *         Copies each readable pixel intersecting both rectangles, without scaling,
 *         converting to dst.colorType() and dst.alphaType() if required.
 *
 *         Pixels are readable when SkSurface is raster, or backed by a Ganesh GPU backend. Graphite
 *         has deprecated this API in favor of the equivalent asynchronous API on
 *         skgpu::graphite::Context (with an optional explicit synchonization).
 *
 *         The destination pixel storage must be allocated by the caller.
 *
 *         Pixel values are converted only if SkColorType and SkAlphaType
 *         do not match. Only pixels within both source and destination rectangles
 *         are copied. dst contents outside SkRect intersection are unchanged.
 *
 *         Pass negative values for srcX or srcY to offset pixels across or down destination.
 *
 *         Does not copy, and returns false if:
 *         - Source and destination rectangles do not intersect.
 *         - SkPixmap pixels could not be allocated.
 *         - dst.rowBytes() is too small to contain one row of pixels.
 *
 *         @param dst   storage for pixels copied from SkSurface
 *         @param srcX  offset into readable pixels on x-axis; may be negative
 *         @param srcY  offset into readable pixels on y-axis; may be negative
 *         @return      true if pixels were copied
 *
 *         example: https://fiddle.skia.org/c/@Surface_readPixels
 *     */
 *     bool readPixels(const SkPixmap& dst, int srcX, int srcY);
 *
 *     /** Copies SkRect of pixels from SkCanvas into dstPixels.
 *
 *         Source SkRect corners are (srcX, srcY) and SkSurface (width(), height()).
 *         Destination SkRect corners are (0, 0) and (dstInfo.width(), dstInfo.height()).
 *         Copies each readable pixel intersecting both rectangles, without scaling,
 *         converting to dstInfo.colorType() and dstInfo.alphaType() if required.
 *
 *         Pixels are readable when SkSurface is raster, or backed by a Ganesh GPU backend. Graphite
 *         has deprecated this API in favor of the equivalent asynchronous API on
 *         skgpu::graphite::Context (with an optional explicit synchonization).
 *
 *         The destination pixel storage must be allocated by the caller.
 *
 *         Pixel values are converted only if SkColorType and SkAlphaType
 *         do not match. Only pixels within both source and destination rectangles
 *         are copied. dstPixels contents outside SkRect intersection are unchanged.
 *
 *         Pass negative values for srcX or srcY to offset pixels across or down destination.
 *
 *         Does not copy, and returns false if:
 *         - Source and destination rectangles do not intersect.
 *         - SkSurface pixels could not be converted to dstInfo.colorType() or dstInfo.alphaType().
 *         - dstRowBytes is too small to contain one row of pixels.
 *
 *         @param dstInfo      width, height, SkColorType, and SkAlphaType of dstPixels
 *         @param dstPixels    storage for pixels; dstInfo.height() times dstRowBytes, or larger
 *         @param dstRowBytes  size of one destination row; dstInfo.width() times pixel size, or larger
 *         @param srcX         offset into readable pixels on x-axis; may be negative
 *         @param srcY         offset into readable pixels on y-axis; may be negative
 *         @return             true if pixels were copied
 *     */
 *     bool readPixels(const SkImageInfo& dstInfo, void* dstPixels, size_t dstRowBytes,
 *                     int srcX, int srcY);
 *
 *     /** Copies SkRect of pixels from SkSurface into bitmap.
 *
 *         Source SkRect corners are (srcX, srcY) and SkSurface (width(), height()).
 *         Destination SkRect corners are (0, 0) and (bitmap.width(), bitmap.height()).
 *         Copies each readable pixel intersecting both rectangles, without scaling,
 *         converting to bitmap.colorType() and bitmap.alphaType() if required.
 *
 *         Pixels are readable when SkSurface is raster, or backed by a Ganesh GPU backend. Graphite
 *         has deprecated this API in favor of the equivalent asynchronous API on
 *         skgpu::graphite::Context (with an optional explicit synchonization).
 *
 *         The destination pixel storage must be allocated by the caller.
 *
 *         Pixel values are converted only if SkColorType and SkAlphaType
 *         do not match. Only pixels within both source and destination rectangles
 *         are copied. dst contents outside SkRect intersection are unchanged.
 *
 *         Pass negative values for srcX or srcY to offset pixels across or down destination.
 *
 *         Does not copy, and returns false if:
 *         - Source and destination rectangles do not intersect.
 *         - SkSurface pixels could not be converted to dst.colorType() or dst.alphaType().
 *         - dst pixels could not be allocated.
 *         - dst.rowBytes() is too small to contain one row of pixels.
 *
 *         @param dst   storage for pixels copied from SkSurface
 *         @param srcX  offset into readable pixels on x-axis; may be negative
 *         @param srcY  offset into readable pixels on y-axis; may be negative
 *         @return      true if pixels were copied
 *
 *         example: https://fiddle.skia.org/c/@Surface_readPixels_3
 *     */
 *     bool readPixels(const SkBitmap& dst, int srcX, int srcY);
 *
 *     using AsyncReadResult = SkImage::AsyncReadResult;
 *
 *     /** Client-provided context that is passed to client-provided ReadPixelsContext. */
 *     using ReadPixelsContext = void*;
 *
 *     /**  Client-provided callback to asyncRescaleAndReadPixels() or
 *          asyncRescaleAndReadPixelsYUV420() that is called when read result is ready or on failure.
 *      */
 *     using ReadPixelsCallback = void(ReadPixelsContext, std::unique_ptr<const AsyncReadResult>);
 *
 *     /** Controls the gamma that rescaling occurs in for asyncRescaleAndReadPixels() and
 *         asyncRescaleAndReadPixelsYUV420().
 *      */
 *     using RescaleGamma = SkImage::RescaleGamma;
 *     using RescaleMode  = SkImage::RescaleMode;
 *
 *     /** Makes surface pixel data available to caller, possibly asynchronously. It can also rescale
 *         the surface pixels.
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
 *         and alpha type of 'info'. A 'srcRect' that is not contained by the bounds of the surface
 *         causes failure.
 *
 *         When the pixel data is ready the caller's ReadPixelsCallback is called with a
 *         AsyncReadResult containing pixel data in the requested color type, alpha type, and color
 *         space. The AsyncReadResult will have count() == 1. Upon failure the callback is called
 *         with nullptr for AsyncReadResult. For a GPU surface this flushes work but a submit must
 *         occur to guarantee a finite time before the callback is called.
 *
 *         The data is valid for the lifetime of AsyncReadResult with the exception that if the
 *         SkSurface is GPU-backed the data is immediately invalidated if the context is abandoned
 *         or destroyed.
 *
 *         @param info            info of the requested pixels
 *         @param srcRect         subrectangle of surface to read
 *         @param rescaleGamma    controls whether rescaling is done in the surface's gamma or whether
 *                                the source data is transformed to a linear gamma before rescaling.
 *         @param rescaleMode     controls the technique of the rescaling
 *         @param callback        function to call with result of the read
 *         @param context         passed to callback
 *      */
 *     void asyncRescaleAndReadPixels(const SkImageInfo& info,
 *                                    const SkIRect& srcRect,
 *                                    RescaleGamma rescaleGamma,
 *                                    RescaleMode rescaleMode,
 *                                    ReadPixelsCallback callback,
 *                                    ReadPixelsContext context);
 *
 *     /**
 *         Similar to asyncRescaleAndReadPixels but performs an additional conversion to YUV. The
 *         RGB->YUV conversion is controlled by 'yuvColorSpace'. The YUV data is returned as three
 *         planes ordered y, u, v. The u and v planes are half the width and height of the resized
 *         rectangle. The y, u, and v values are single bytes. Currently this fails if 'dstSize'
 *         width and height are not even. A 'srcRect' that is not contained by the bounds of the
 *         surface causes failure.
 *
 *         When the pixel data is ready the caller's ReadPixelsCallback is called with a
 *         AsyncReadResult containing the planar data. The AsyncReadResult will have count() == 3.
 *         Upon failure the callback is called with nullptr for AsyncReadResult. For a GPU surface this
 *         flushes work but a submit must occur to guarantee a finite time before the callback is
 *         called.
 *
 *         The data is valid for the lifetime of AsyncReadResult with the exception that if the
 *         SkSurface is GPU-backed the data is immediately invalidated if the context is abandoned
 *         or destroyed.
 *
 *         @param yuvColorSpace  The transformation from RGB to YUV. Applied to the resized image
 *                               after it is converted to dstColorSpace.
 *         @param dstColorSpace  The color space to convert the resized image to, after rescaling.
 *         @param srcRect        The portion of the surface to rescale and convert to YUV planes.
 *         @param dstSize        The size to rescale srcRect to
 *         @param rescaleGamma   controls whether rescaling is done in the surface's gamma or whether
 *                               the source data is transformed to a linear gamma before rescaling.
 *         @param rescaleMode    controls the sampling technique of the rescaling
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
 *                                          ReadPixelsContext context);
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
 *                                           ReadPixelsContext context);
 *
 *     /** Copies SkRect of pixels from the src SkPixmap to the SkSurface.
 *
 *         Source SkRect corners are (0, 0) and (src.width(), src.height()).
 *         Destination SkRect corners are (dstX, dstY) and
 *         (dstX + Surface width(), dstY + Surface height()).
 *
 *         Copies each readable pixel intersecting both rectangles, without scaling,
 *         converting to SkSurface colorType() and SkSurface alphaType() if required.
 *
 *         @param src   storage for pixels to copy to SkSurface
 *         @param dstX  x-axis position relative to SkSurface to begin copy; may be negative
 *         @param dstY  y-axis position relative to SkSurface to begin copy; may be negative
 *
 *         example: https://fiddle.skia.org/c/@Surface_writePixels
 *     */
 *     void writePixels(const SkPixmap& src, int dstX, int dstY);
 *
 *     /** Copies SkRect of pixels from the src SkBitmap to the SkSurface.
 *
 *         Source SkRect corners are (0, 0) and (src.width(), src.height()).
 *         Destination SkRect corners are (dstX, dstY) and
 *         (dstX + Surface width(), dstY + Surface height()).
 *
 *         Copies each readable pixel intersecting both rectangles, without scaling,
 *         converting to SkSurface colorType() and SkSurface alphaType() if required.
 *
 *         @param src   storage for pixels to copy to SkSurface
 *         @param dstX  x-axis position relative to SkSurface to begin copy; may be negative
 *         @param dstY  y-axis position relative to SkSurface to begin copy; may be negative
 *
 *         example: https://fiddle.skia.org/c/@Surface_writePixels_2
 *     */
 *     void writePixels(const SkBitmap& src, int dstX, int dstY);
 *
 *     /** Returns SkSurfaceProps for surface.
 *
 *         @return  LCD striping orientation and setting for device independent fonts
 *     */
 *     const SkSurfaceProps& props() const { return fProps; }
 *
 *     /** Inserts a list of GPU semaphores that the current GPU-backed API must wait on before
 *         executing any more commands on the GPU for this surface. We only guarantee blocking
 *         transfer and fragment shader work, but may block earlier stages as well depending on the
 *         backend.
 *         If this call returns false, then the GPU back-end will not wait on any passed in
 *         semaphores, and the client will still own the semaphores, regardless of the value of
 *         deleteSemaphoresAfterWait.
 *
 *         If deleteSemaphoresAfterWait is false then Skia will not delete the semaphores. In this case
 *         it is the client's responsibility to not destroy or attempt to reuse the semaphores until it
 *         knows that Skia has finished waiting on them. This can be done by using finishedProcs
 *         on flush calls.
 *
 *         @param numSemaphores               size of waitSemaphores array
 *         @param waitSemaphores              array of semaphore containers
 *         @paramm deleteSemaphoresAfterWait  who owns and should delete the semaphores
 *         @return                            true if GPU is waiting on semaphores
 *     */
 *     bool wait(int numSemaphores, const GrBackendSemaphore* waitSemaphores,
 *               bool deleteSemaphoresAfterWait = true);
 *
 *     /** Initializes GrSurfaceCharacterization that can be used to perform GPU back-end
 *         processing in a separate thread. Typically this is used to divide drawing
 *         into multiple tiles. GrDeferredDisplayListRecorder records the drawing commands
 *         for each tile.
 *
 *         Return true if SkSurface supports characterization. raster surface returns false.
 *
 *         @param characterization  properties for parallel drawing
 *         @return                  true if supported
 *
 *         example: https://fiddle.skia.org/c/@Surface_characterize
 *     */
 *     bool characterize(GrSurfaceCharacterization* characterization) const;
 *
 * protected:
 *     SkSurface(int width, int height, const SkSurfaceProps* surfaceProps);
 *     SkSurface(const SkImageInfo& imageInfo, const SkSurfaceProps* surfaceProps);
 *
 *     // called by subclass if their contents have changed
 *     void dirtyGenerationID() {
 *         fGenerationID = 0;
 *     }
 *
 * private:
 *     const SkSurfaceProps fProps;
 *     const int            fWidth;
 *     const int            fHeight;
 *     uint32_t             fGenerationID;
 *
 *     using INHERITED = SkRefCnt;
 * }
 * ```
 */
public abstract class SkSurface public constructor(
  width: Int,
  height: Int,
  surfaceProps: SkSurfaceProps?,
) : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * static constexpr BackendHandleAccess kFlushRead_BackendHandleAccess =
   *             BackendHandleAccess::kFlushRead
   * ```
   */
  private val fProps: Int = TODO("Initialize fProps")

  /**
   * C++ original:
   * ```cpp
   * static constexpr BackendHandleAccess kFlushWrite_BackendHandleAccess =
   *             BackendHandleAccess::kFlushWrite
   * ```
   */
  private val fWidth: Int = TODO("Initialize fWidth")

  /**
   * C++ original:
   * ```cpp
   * static constexpr BackendHandleAccess kDiscardWrite_BackendHandleAccess =
   *             BackendHandleAccess::kDiscardWrite
   * ```
   */
  private val fHeight: Int = TODO("Initialize fHeight")

  /**
   * C++ original:
   * ```cpp
   * const SkSurfaceProps fProps
   * ```
   */
  private var fGenerationID: UInt = TODO("Initialize fGenerationID")

  /**
   * C++ original:
   * ```cpp
   * SkSurface(int width, int height, const SkSurfaceProps* surfaceProps)
   * ```
   */
  public constructor(imageInfo: SkImageInfo, surfaceProps: SkSurfaceProps?) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSurface::isCompatible(const GrSurfaceCharacterization& characterization) const {
   *     return asConstSB(this)->onIsCompatible(characterization);
   * }
   * ```
   */
  public fun isCompatible(characterization: GrSurfaceCharacterization): Boolean {
    TODO("Implement isCompatible")
  }

  /**
   * C++ original:
   * ```cpp
   * int width() const { return fWidth; }
   * ```
   */
  public fun width(): Int {
    TODO("Implement width")
  }

  /**
   * C++ original:
   * ```cpp
   * int height() const { return fHeight; }
   * ```
   */
  public fun height(): Int {
    TODO("Implement height")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual SkImageInfo imageInfo() const { return SkImageInfo::MakeUnknown(fWidth, fHeight); }
   * ```
   */
  public open fun imageInfo(): Int {
    TODO("Implement imageInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t SkSurface::generationID() {
   *     if (0 == fGenerationID) {
   *         fGenerationID = asSB(this)->newGenerationID();
   *     }
   *     return fGenerationID;
   * }
   * ```
   */
  public fun generationID(): UInt {
    TODO("Implement generationID")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSurface::notifyContentWillChange(ContentChangeMode mode) {
   *     sk_ignore_unused_variable(asSB(this)->aboutToDraw(mode));
   * }
   * ```
   */
  public fun notifyContentWillChange(mode: ContentChangeMode) {
    TODO("Implement notifyContentWillChange")
  }

  /**
   * C++ original:
   * ```cpp
   * GrRecordingContext* SkSurface::recordingContext() const {
   *     return asConstSB(this)->onGetRecordingContext();
   * }
   * ```
   */
  public fun recordingContext(): GrRecordingContext {
    TODO("Implement recordingContext")
  }

  /**
   * C++ original:
   * ```cpp
   * skgpu::graphite::Recorder* SkSurface::recorder() const { return asConstSB(this)->onGetRecorder(); }
   * ```
   */
  public fun recorder(): Recorder {
    TODO("Implement recorder")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRecorder* SkSurface::baseRecorder() const { return asConstSB(this)->onGetBaseRecorder(); }
   * ```
   */
  public fun baseRecorder(): SkRecorder {
    TODO("Implement baseRecorder")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool replaceBackendTexture(const GrBackendTexture& backendTexture,
   *                                        GrSurfaceOrigin origin,
   *                                        ContentChangeMode mode = kRetain_ContentChangeMode,
   *                                        TextureReleaseProc = nullptr,
   *                                        ReleaseContext = nullptr) = 0
   * ```
   */
  public abstract fun replaceBackendTexture(
    backendTexture: GrBackendTexture,
    origin: GrSurfaceOrigin,
    mode: ContentChangeMode = TODO(),
    param3: SkSurfaceTextureReleaseProc = null,
    param4: SkSurfaceReleaseContext = null,
  ): Boolean

  /**
   * C++ original:
   * ```cpp
   * SkCanvas* SkSurface::getCanvas() {
   *     return asSB(this)->getCachedCanvas();
   * }
   * ```
   */
  public fun getCanvas(): SkCanvas {
    TODO("Implement getCanvas")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<const SkCapabilities> SkSurface::capabilities() {
   *     return asSB(this)->onCapabilities();
   * }
   * ```
   */
  public fun capabilities(): Int {
    TODO("Implement capabilities")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkSurface> SkSurface::makeSurface(const SkImageInfo& info) {
   *     return asSB(this)->onNewSurface(info);
   * }
   * ```
   */
  public fun makeSurface(imageInfo: SkImageInfo): Int {
    TODO("Implement makeSurface")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkSurface> SkSurface::makeSurface(int width, int height) {
   *     return this->makeSurface(this->imageInfo().makeWH(width, height));
   * }
   * ```
   */
  public fun makeSurface(width: Int, height: Int): Int {
    TODO("Implement makeSurface")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> SkSurface::makeImageSnapshot() {
   *     return asSB(this)->refCachedImage();
   * }
   * ```
   */
  public fun makeImageSnapshot(): Int {
    TODO("Implement makeImageSnapshot")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> SkSurface::makeImageSnapshot(const SkIRect& srcBounds) {
   *     const SkIRect surfBounds = { 0, 0, fWidth, fHeight };
   *     SkIRect bounds = srcBounds;
   *     if (!bounds.intersect(surfBounds)) {
   *         return nullptr;
   *     }
   *     SkASSERT(!bounds.isEmpty());
   *     if (bounds == surfBounds) {
   *         return this->makeImageSnapshot();
   *     } else {
   *         return asSB(this)->onNewImageSnapshot(&bounds);
   *     }
   * }
   * ```
   */
  public fun makeImageSnapshot(bounds: SkIRect): Int {
    TODO("Implement makeImageSnapshot")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> SkSurface::makeTemporaryImage() {
   *     return asSB(this)->onMakeTemporaryImage();
   * }
   * ```
   */
  public fun makeTemporaryImage(): Int {
    TODO("Implement makeTemporaryImage")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSurface::draw(SkCanvas* canvas, SkScalar x, SkScalar y, const SkSamplingOptions& sampling,
   *                      const SkPaint* paint) {
   *     asSB(this)->onDraw(canvas, x, y, sampling, paint);
   * }
   * ```
   */
  public fun draw(
    canvas: SkCanvas?,
    x: SkScalar,
    y: SkScalar,
    sampling: SkSamplingOptions,
    paint: SkPaint?,
  ) {
    TODO("Implement draw")
  }

  /**
   * C++ original:
   * ```cpp
   * void draw(SkCanvas* canvas, SkScalar x, SkScalar y, const SkPaint* paint = nullptr) {
   *         this->draw(canvas, x, y, SkSamplingOptions(), paint);
   *     }
   * ```
   */
  public fun draw(
    canvas: SkCanvas?,
    x: SkScalar,
    y: SkScalar,
    paint: SkPaint? = null,
  ) {
    TODO("Implement draw")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSurface::peekPixels(SkPixmap* pmap) {
   *     return this->getCanvas()->peekPixels(pmap);
   * }
   * ```
   */
  public fun peekPixels(pixmap: SkPixmap?): Boolean {
    TODO("Implement peekPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * bool readPixels(const SkPixmap& dst, int srcX, int srcY)
   * ```
   */
  public fun readPixels(
    dst: SkPixmap,
    srcX: Int,
    srcY: Int,
  ): Boolean {
    TODO("Implement readPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSurface::readPixels(const SkImageInfo& dstInfo, void* dstPixels, size_t dstRowBytes,
   *                            int srcX, int srcY) {
   *     return this->readPixels({dstInfo, dstPixels, dstRowBytes}, srcX, srcY);
   * }
   * ```
   */
  public fun readPixels(
    dstInfo: SkImageInfo,
    dstPixels: Unit?,
    dstRowBytes: ULong,
    srcX: Int,
    srcY: Int,
  ): Boolean {
    TODO("Implement readPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSurface::readPixels(const SkBitmap& bitmap, int srcX, int srcY) {
   *     SkPixmap pm;
   *     return bitmap.peekPixels(&pm) && this->readPixels(pm, srcX, srcY);
   * }
   * ```
   */
  public fun readPixels(
    dst: SkBitmap,
    srcX: Int,
    srcY: Int,
  ): Boolean {
    TODO("Implement readPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSurface::asyncRescaleAndReadPixels(const SkImageInfo& info,
   *                                           const SkIRect& srcRect,
   *                                           RescaleGamma rescaleGamma,
   *                                           RescaleMode rescaleMode,
   *                                           ReadPixelsCallback callback,
   *                                           ReadPixelsContext context) {
   *     if (!SkIRect::MakeWH(this->width(), this->height()).contains(srcRect) ||
   *         !SkImageInfoIsValid(info)) {
   *         callback(context, nullptr);
   *         return;
   *     }
   *     asSB(this)->onAsyncRescaleAndReadPixels(
   *             info, srcRect, rescaleGamma, rescaleMode, callback, context);
   * }
   * ```
   */
  public fun asyncRescaleAndReadPixels(
    info: SkImageInfo,
    srcRect: SkIRect,
    rescaleGamma: RescaleGamma,
    rescaleMode: RescaleMode,
    callback: SkSurfaceReadPixelsCallback,
    context: SkSurfaceReadPixelsContext,
  ) {
    TODO("Implement asyncRescaleAndReadPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSurface::asyncRescaleAndReadPixelsYUV420(SkYUVColorSpace yuvColorSpace,
   *                                                 sk_sp<SkColorSpace> dstColorSpace,
   *                                                 const SkIRect& srcRect,
   *                                                 const SkISize& dstSize,
   *                                                 RescaleGamma rescaleGamma,
   *                                                 RescaleMode rescaleMode,
   *                                                 ReadPixelsCallback callback,
   *                                                 ReadPixelsContext context) {
   *     if (!SkIRect::MakeWH(this->width(), this->height()).contains(srcRect) || dstSize.isZero() ||
   *         (dstSize.width() & 0b1) || (dstSize.height() & 0b1)) {
   *         callback(context, nullptr);
   *         return;
   *     }
   *     asSB(this)->onAsyncRescaleAndReadPixelsYUV420(yuvColorSpace,
   *                                                   /*readAlpha=*/false,
   *                                                   std::move(dstColorSpace),
   *                                                   srcRect,
   *                                                   dstSize,
   *                                                   rescaleGamma,
   *                                                   rescaleMode,
   *                                                   callback,
   *                                                   context);
   * }
   * ```
   */
  public fun asyncRescaleAndReadPixelsYUV420(
    yuvColorSpace: SkYUVColorSpace,
    dstColorSpace: SkSp<SkColorSpace>,
    srcRect: SkIRect,
    dstSize: SkISize,
    rescaleGamma: RescaleGamma,
    rescaleMode: RescaleMode,
    callback: SkSurfaceReadPixelsCallback,
    context: SkSurfaceReadPixelsContext,
  ) {
    TODO("Implement asyncRescaleAndReadPixelsYUV420")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSurface::asyncRescaleAndReadPixelsYUVA420(SkYUVColorSpace yuvColorSpace,
   *                                                  sk_sp<SkColorSpace> dstColorSpace,
   *                                                  const SkIRect& srcRect,
   *                                                  const SkISize& dstSize,
   *                                                  RescaleGamma rescaleGamma,
   *                                                  RescaleMode rescaleMode,
   *                                                  ReadPixelsCallback callback,
   *                                                  ReadPixelsContext context) {
   *     if (!SkIRect::MakeWH(this->width(), this->height()).contains(srcRect) || dstSize.isZero() ||
   *         (dstSize.width() & 0b1) || (dstSize.height() & 0b1)) {
   *         callback(context, nullptr);
   *         return;
   *     }
   *     asSB(this)->onAsyncRescaleAndReadPixelsYUV420(yuvColorSpace,
   *                                                   /*readAlpha=*/true,
   *                                                   std::move(dstColorSpace),
   *                                                   srcRect,
   *                                                   dstSize,
   *                                                   rescaleGamma,
   *                                                   rescaleMode,
   *                                                   callback,
   *                                                   context);
   * }
   * ```
   */
  public fun asyncRescaleAndReadPixelsYUVA420(
    yuvColorSpace: SkYUVColorSpace,
    dstColorSpace: SkSp<SkColorSpace>,
    srcRect: SkIRect,
    dstSize: SkISize,
    rescaleGamma: RescaleGamma,
    rescaleMode: RescaleMode,
    callback: SkSurfaceReadPixelsCallback,
    context: SkSurfaceReadPixelsContext,
  ) {
    TODO("Implement asyncRescaleAndReadPixelsYUVA420")
  }

  /**
   * C++ original:
   * ```cpp
   * void writePixels(const SkPixmap& src, int dstX, int dstY)
   * ```
   */
  public fun writePixels(
    src: SkPixmap,
    dstX: Int,
    dstY: Int,
  ) {
    TODO("Implement writePixels")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSurface::writePixels(const SkBitmap& src, int x, int y) {
   *     SkPixmap pm;
   *     if (src.peekPixels(&pm)) {
   *         this->writePixels(pm, x, y);
   *     }
   * }
   * ```
   */
  public fun writePixels(
    src: SkBitmap,
    dstX: Int,
    dstY: Int,
  ) {
    TODO("Implement writePixels")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkSurfaceProps& props() const { return fProps; }
   * ```
   */
  public fun props(): Int {
    TODO("Implement props")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSurface::wait(int numSemaphores, const GrBackendSemaphore* waitSemaphores,
   *                      bool deleteSemaphoresAfterWait) {
   *     return asSB(this)->onWait(numSemaphores, waitSemaphores, deleteSemaphoresAfterWait);
   * }
   * ```
   */
  public fun wait(
    numSemaphores: Int,
    waitSemaphores: GrBackendSemaphore?,
    deleteSemaphoresAfterWait: Boolean = true,
  ): Boolean {
    TODO("Implement wait")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSurface::characterize(GrSurfaceCharacterization* characterization) const {
   *     return asConstSB(this)->onCharacterize(characterization);
   * }
   * ```
   */
  public fun characterize(characterization: GrSurfaceCharacterization?): Boolean {
    TODO("Implement characterize")
  }

  /**
   * C++ original:
   * ```cpp
   * void dirtyGenerationID() {
   *         fGenerationID = 0;
   *     }
   * ```
   */
  protected fun dirtyGenerationID() {
    TODO("Implement dirtyGenerationID")
  }

  public enum class ContentChangeMode {
    kDiscard_ContentChangeMode,
    kRetain_ContentChangeMode,
  }

  public enum class BackendHandleAccess {
    kFlushRead,
    kFlushWrite,
    kDiscardWrite,
    kFlushRead_BackendHandleAccess,
    kFlushWrite_BackendHandleAccess,
    kDiscardWrite_BackendHandleAccess,
  }

  public companion object {
    public val kFlushReadBackendHandleAccess: BackendHandleAccess =
        TODO("Initialize kFlushReadBackendHandleAccess")

    public val kFlushWriteBackendHandleAccess: BackendHandleAccess =
        TODO("Initialize kFlushWriteBackendHandleAccess")

    public val kDiscardWriteBackendHandleAccess: BackendHandleAccess =
        TODO("Initialize kDiscardWriteBackendHandleAccess")
  }
}
