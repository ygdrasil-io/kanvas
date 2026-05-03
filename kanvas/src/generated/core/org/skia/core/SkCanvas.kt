package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.ByteArray
import kotlin.Char
import kotlin.CharArray
import kotlin.Float
import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkBlender
import org.skia.foundation.SkColor
import org.skia.foundation.SkColorInfo
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkData
import org.skia.foundation.SkDrawable
import org.skia.foundation.SkFont
import org.skia.foundation.SkGlyphID
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPMColor
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPixmap
import org.skia.foundation.SkRRect
import org.skia.foundation.SkRecorder
import org.skia.foundation.SkRegion
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan
import org.skia.foundation.SkSurfaceProps
import org.skia.foundation.SkTextEncoding
import org.skia.foundation.SkTileMode
import org.skia.foundation.U8CPU
import org.skia.gpu.Recorder
import org.skia.gpu.ganesh.GrRecordingContext
import org.skia.math.SkIPoint
import org.skia.math.SkIRect
import org.skia.math.SkM44
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRSXform
import org.skia.math.SkRect
import org.skia.math.SkScalar
import org.skia.utils.SkPaintFilterCanvas
import org.skia.utils.Slug
import undefined.FilterSpan
import undefined.SaveLayerFlags
import undefined.SkColor4f

/**
 * C++ original:
 * ```cpp
 * class SK_API SkCanvas {
 * public:
 *
 *     /** Allocates raster SkCanvas that will draw directly into pixels.
 *
 *         SkCanvas is returned if all parameters are valid.
 *         Valid parameters include:
 *         info dimensions are zero or positive;
 *         info contains SkColorType and SkAlphaType supported by raster surface;
 *         pixels is not nullptr;
 *         rowBytes is zero or large enough to contain info width pixels of SkColorType.
 *
 *         Pass zero for rowBytes to compute rowBytes from info width and size of pixel.
 *         If rowBytes is greater than zero, it must be equal to or greater than
 *         info width times bytes required for SkColorType.
 *
 *         Pixel buffer size should be info height times computed rowBytes.
 *         Pixels are not initialized.
 *         To access pixels after drawing, call flush() or peekPixels().
 *
 *         @param info      width, height, SkColorType, SkAlphaType, SkColorSpace, of raster surface;
 *                          width, or height, or both, may be zero
 *         @param pixels    pointer to destination pixels buffer
 *         @param rowBytes  interval from one SkSurface row to the next, or zero
 *         @param props     LCD striping orientation and setting for device independent fonts;
 *                          may be nullptr
 *         @return          SkCanvas if all parameters are valid; otherwise, nullptr
 *     */
 *     static std::unique_ptr<SkCanvas> MakeRasterDirect(const SkImageInfo& info, void* pixels,
 *                                                       size_t rowBytes,
 *                                                       const SkSurfaceProps* props = nullptr);
 *
 *     /** Allocates raster SkCanvas specified by inline image specification. Subsequent SkCanvas
 *         calls draw into pixels.
 *         SkColorType is set to kN32_SkColorType.
 *         SkAlphaType is set to kPremul_SkAlphaType.
 *         To access pixels after drawing, call flush() or peekPixels().
 *
 *         SkCanvas is returned if all parameters are valid.
 *         Valid parameters include:
 *         width and height are zero or positive;
 *         pixels is not nullptr;
 *         rowBytes is zero or large enough to contain width pixels of kN32_SkColorType.
 *
 *         Pass zero for rowBytes to compute rowBytes from width and size of pixel.
 *         If rowBytes is greater than zero, it must be equal to or greater than
 *         width times bytes required for SkColorType.
 *
 *         Pixel buffer size should be height times rowBytes.
 *
 *         @param width     pixel column count on raster surface created; must be zero or greater
 *         @param height    pixel row count on raster surface created; must be zero or greater
 *         @param pixels    pointer to destination pixels buffer; buffer size should be height
 *                          times rowBytes
 *         @param rowBytes  interval from one SkSurface row to the next, or zero
 *         @return          SkCanvas if all parameters are valid; otherwise, nullptr
 *     */
 *     static std::unique_ptr<SkCanvas> MakeRasterDirectN32(int width, int height, SkPMColor* pixels,
 *                                                          size_t rowBytes) {
 *         return MakeRasterDirect(SkImageInfo::MakeN32Premul(width, height), pixels, rowBytes);
 *     }
 *
 *     /** Creates an empty SkCanvas with no backing device or pixels, with
 *         a width and height of zero.
 *
 *         @return  empty SkCanvas
 *
 *         example: https://fiddle.skia.org/c/@Canvas_empty_constructor
 *     */
 *     SkCanvas();
 *
 *     /** Creates SkCanvas of the specified dimensions without a SkSurface.
 *         Used by subclasses with custom implementations for draw member functions.
 *
 *         If props equals nullptr, SkSurfaceProps are created with
 *         SkSurfaceProps::InitType settings, which choose the pixel striping
 *         direction and order. Since a platform may dynamically change its direction when
 *         the device is rotated, and since a platform may have multiple monitors with
 *         different characteristics, it is best not to rely on this legacy behavior.
 *
 *         @param width   zero or greater
 *         @param height  zero or greater
 *         @param props   LCD striping orientation and setting for device independent fonts;
 *                        may be nullptr
 *         @return        SkCanvas placeholder with dimensions
 *
 *         example: https://fiddle.skia.org/c/@Canvas_int_int_const_SkSurfaceProps_star
 *     */
 *     SkCanvas(int width, int height, const SkSurfaceProps* props = nullptr);
 *
 *     /** Private. For internal use only.
 *     */
 *     explicit SkCanvas(sk_sp<SkDevice> device);
 *
 *     /** Constructs a canvas that draws into bitmap.
 *         Sets kUnknown_SkPixelGeometry in constructed SkSurface.
 *
 *         SkBitmap is copied so that subsequently editing bitmap will not affect
 *         constructed SkCanvas.
 *
 *         May be deprecated in the future.
 *
 *         @param bitmap  width, height, SkColorType, SkAlphaType, and pixel
 *                        storage of raster surface
 *         @return        SkCanvas that can be used to draw into bitmap
 *
 *         example: https://fiddle.skia.org/c/@Canvas_copy_const_SkBitmap
 *     */
 *     explicit SkCanvas(const SkBitmap& bitmap);
 *
 * #ifdef SK_BUILD_FOR_ANDROID_FRAMEWORK
 *     /** Private.
 *      */
 *     enum class ColorBehavior {
 *         kLegacy, //!< placeholder
 *     };
 *
 *     /** Private. For use by Android framework only.
 *
 *         @param bitmap    specifies a bitmap for the canvas to draw into
 *         @param behavior  specializes this constructor; value is unused
 *         @return          SkCanvas that can be used to draw into bitmap
 *     */
 *     SkCanvas(const SkBitmap& bitmap, ColorBehavior behavior);
 * #endif
 *
 *     /** Constructs a canvas that draws into bitmap.
 *         Use props to match the device characteristics, like LCD striping.
 *
 *         bitmap is copied so that subsequently editing bitmap will not affect
 *         constructed SkCanvas.
 *
 *         @param bitmap  width, height, SkColorType, SkAlphaType,
 *                        and pixel storage of raster surface
 *         @param props   order and orientation of RGB striping; and whether to use
 *                        device independent fonts
 *         @return        SkCanvas that can be used to draw into bitmap
 *
 *         example: https://fiddle.skia.org/c/@Canvas_const_SkBitmap_const_SkSurfaceProps
 *     */
 *     SkCanvas(const SkBitmap& bitmap, const SkSurfaceProps& props);
 *
 *     /** Draws saved layers, if any.
 *         Frees up resources used by SkCanvas.
 *
 *         example: https://fiddle.skia.org/c/@Canvas_destructor
 *     */
 *     virtual ~SkCanvas();
 *
 *     /** Returns SkImageInfo for SkCanvas. If SkCanvas is not associated with raster surface or
 *         GPU surface, returned SkColorType is set to kUnknown_SkColorType.
 *
 *         @return  dimensions and SkColorType of SkCanvas
 *
 *         example: https://fiddle.skia.org/c/@Canvas_imageInfo
 *     */
 *     SkImageInfo imageInfo() const;
 *
 *     /** Copies SkSurfaceProps, if SkCanvas is associated with raster surface or
 *         GPU surface, and returns true. Otherwise, returns false and leave props unchanged.
 *
 *         @param props  storage for writable SkSurfaceProps
 *         @return       true if SkSurfaceProps was copied
 *
 *         DEPRECATED: Replace usage with getBaseProps() or getTopProps()
 *
 *         example: https://fiddle.skia.org/c/@Canvas_getProps
 *     */
 *     bool getProps(SkSurfaceProps* props) const;
 *
 *     /** Returns the SkSurfaceProps associated with the canvas (i.e., at the base of the layer
 *         stack).
 *
 *         @return  base SkSurfaceProps
 *     */
 *     SkSurfaceProps getBaseProps() const;
 *
 *     /** Returns the SkSurfaceProps associated with the canvas that are currently active (i.e., at
 *         the top of the layer stack). This can differ from getBaseProps depending on the flags
 *         passed to saveLayer (see SaveLayerFlagsSet).
 *
 *         @return  SkSurfaceProps active in the current/top layer
 *     */
 *     SkSurfaceProps getTopProps() const;
 *
 *     /** Gets the size of the base or root layer in global canvas coordinates. The
 *         origin of the base layer is always (0,0). The area available for drawing may be
 *         smaller (due to clipping or saveLayer).
 *
 *         @return  integral width and height of base layer
 *
 *         example: https://fiddle.skia.org/c/@Canvas_getBaseLayerSize
 *     */
 *     virtual SkISize getBaseLayerSize() const;
 *
 *     /** Creates SkSurface matching info and props, and associates it with SkCanvas.
 *         Returns nullptr if no match found.
 *
 *         If props is nullptr, matches SkSurfaceProps in SkCanvas. If props is nullptr and SkCanvas
 *         does not have SkSurfaceProps, creates SkSurface with default SkSurfaceProps.
 *
 *         @param info   width, height, SkColorType, SkAlphaType, and SkColorSpace
 *         @param props  SkSurfaceProps to match; may be nullptr to match SkCanvas
 *         @return       SkSurface matching info and props, or nullptr if no match is available
 *
 *         example: https://fiddle.skia.org/c/@Canvas_makeSurface
 *     */
 *     sk_sp<SkSurface> makeSurface(const SkImageInfo& info, const SkSurfaceProps* props = nullptr);
 *
 *     /** Returns Ganesh context of the GPU surface associated with SkCanvas.
 *
 *         @return  GPU context, if available; nullptr otherwise
 *
 *         example: https://fiddle.skia.org/c/@Canvas_recordingContext
 *      */
 *     virtual GrRecordingContext* recordingContext() const;
 *
 *     /** Returns Recorder for the GPU surface associated with SkCanvas.
 *
 *         @return  Recorder, if available; nullptr otherwise
 *      */
 *     virtual skgpu::graphite::Recorder* recorder() const;
 *
 *     /** Returns Recorder for the surface associated with SkCanvas.
 *
 *         @return  Recorder, should be non-null
 *      */
 *     virtual SkRecorder* baseRecorder() const;
 *
 *     /** Sometimes a canvas is owned by a surface. If it is, getSurface() will return a bare
 *      *  pointer to that surface, else this will return nullptr.
 *      */
 *     SkSurface* getSurface() const;
 *
 *     /** Returns the pixel base address, SkImageInfo, rowBytes, and origin if the pixels
 *         can be read directly. The returned address is only valid
 *         while SkCanvas is in scope and unchanged. Any SkCanvas call or SkSurface call
 *         may invalidate the returned address and other returned values.
 *
 *         If pixels are inaccessible, info, rowBytes, and origin are unchanged.
 *
 *         @param info      storage for writable pixels' SkImageInfo; may be nullptr
 *         @param rowBytes  storage for writable pixels' row bytes; may be nullptr
 *         @param origin    storage for SkCanvas top layer origin, its top-left corner;
 *                          may be nullptr
 *         @return          address of pixels, or nullptr if inaccessible
 *
 *         example: https://fiddle.skia.org/c/@Canvas_accessTopLayerPixels_a
 *         example: https://fiddle.skia.org/c/@Canvas_accessTopLayerPixels_b
 *     */
 *     void* accessTopLayerPixels(SkImageInfo* info, size_t* rowBytes, SkIPoint* origin = nullptr);
 *
 *     /** Returns custom context that tracks the SkMatrix and clip.
 *
 *         Use SkRasterHandleAllocator to blend Skia drawing with custom drawing, typically performed
 *         by the host platform user interface. The custom context returned is generated by
 *         SkRasterHandleAllocator::MakeCanvas, which creates a custom canvas with raster storage for
 *         the drawing destination.
 *
 *         @return  context of custom allocation
 *
 *         example: https://fiddle.skia.org/c/@Canvas_accessTopRasterHandle
 *     */
 *     SkRasterHandleAllocator::Handle accessTopRasterHandle() const;
 *
 *     /** Returns true if SkCanvas has direct access to its pixels.
 *
 *         Pixels are readable when SkDevice is raster. Pixels are not readable when SkCanvas
 *         is returned from GPU surface, returned by SkDocument::beginPage, returned by
 *         SkPictureRecorder::beginRecording, or SkCanvas is the base of a utility class
 *         like DebugCanvas.
 *
 *         pixmap is valid only while SkCanvas is in scope and unchanged. Any
 *         SkCanvas or SkSurface call may invalidate the pixmap values.
 *
 *         @param pixmap  storage for pixel state if pixels are readable; otherwise, ignored
 *         @return        true if SkCanvas has direct access to pixels
 *
 *         example: https://fiddle.skia.org/c/@Canvas_peekPixels
 *     */
 *     bool peekPixels(SkPixmap* pixmap);
 *
 *     /** Copies SkRect of pixels from SkCanvas into dstPixels. SkMatrix and clip are
 *         ignored.
 *
 *         Source SkRect corners are (srcX, srcY) and (imageInfo().width(), imageInfo().height()).
 *         Destination SkRect corners are (0, 0) and (dstInfo.width(), dstInfo.height()).
 *         Copies each readable pixel intersecting both rectangles, without scaling,
 *         converting to dstInfo.colorType() and dstInfo.alphaType() if required.
 *
 *         Pixels are readable when SkDevice is raster, or backed by a GPU.
 *         Pixels are not readable when SkCanvas is returned by SkDocument::beginPage,
 *         returned by SkPictureRecorder::beginRecording, or SkCanvas is the base of a utility
 *         class like DebugCanvas.
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
 *         - SkCanvas pixels could not be converted to dstInfo.colorType() or dstInfo.alphaType().
 *         - SkCanvas pixels are not readable; for instance, SkCanvas is document-based.
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
 *     /** Copies SkRect of pixels from SkCanvas into pixmap. SkMatrix and clip are
 *         ignored.
 *
 *         Source SkRect corners are (srcX, srcY) and (imageInfo().width(), imageInfo().height()).
 *         Destination SkRect corners are (0, 0) and (pixmap.width(), pixmap.height()).
 *         Copies each readable pixel intersecting both rectangles, without scaling,
 *         converting to pixmap.colorType() and pixmap.alphaType() if required.
 *
 *         Pixels are readable when SkDevice is raster, or backed by a GPU.
 *         Pixels are not readable when SkCanvas is returned by SkDocument::beginPage,
 *         returned by SkPictureRecorder::beginRecording, or SkCanvas is the base of a utility
 *         class like DebugCanvas.
 *
 *         Caller must allocate pixel storage in pixmap if needed.
 *
 *         Pixel values are converted only if SkColorType and SkAlphaType
 *         do not match. Only pixels within both source and destination SkRect
 *         are copied. pixmap pixels contents outside SkRect intersection are unchanged.
 *
 *         Pass negative values for srcX or srcY to offset pixels across or down pixmap.
 *
 *         Does not copy, and returns false if:
 *         - Source and destination rectangles do not intersect.
 *         - SkCanvas pixels could not be converted to pixmap.colorType() or pixmap.alphaType().
 *         - SkCanvas pixels are not readable; for instance, SkCanvas is document-based.
 *         - SkPixmap pixels could not be allocated.
 *         - pixmap.rowBytes() is too small to contain one row of pixels.
 *
 *         @param pixmap  storage for pixels copied from SkCanvas
 *         @param srcX    offset into readable pixels on x-axis; may be negative
 *         @param srcY    offset into readable pixels on y-axis; may be negative
 *         @return        true if pixels were copied
 *
 *         example: https://fiddle.skia.org/c/@Canvas_readPixels_2
 *     */
 *     bool readPixels(const SkPixmap& pixmap, int srcX, int srcY);
 *
 *     /** Copies SkRect of pixels from SkCanvas into bitmap. SkMatrix and clip are
 *         ignored.
 *
 *         Source SkRect corners are (srcX, srcY) and (imageInfo().width(), imageInfo().height()).
 *         Destination SkRect corners are (0, 0) and (bitmap.width(), bitmap.height()).
 *         Copies each readable pixel intersecting both rectangles, without scaling,
 *         converting to bitmap.colorType() and bitmap.alphaType() if required.
 *
 *         Pixels are readable when SkDevice is raster, or backed by a GPU.
 *         Pixels are not readable when SkCanvas is returned by SkDocument::beginPage,
 *         returned by SkPictureRecorder::beginRecording, or SkCanvas is the base of a utility
 *         class like DebugCanvas.
 *
 *         Caller must allocate pixel storage in bitmap if needed.
 *
 *         SkBitmap values are converted only if SkColorType and SkAlphaType
 *         do not match. Only pixels within both source and destination rectangles
 *         are copied. SkBitmap pixels outside SkRect intersection are unchanged.
 *
 *         Pass negative values for srcX or srcY to offset pixels across or down bitmap.
 *
 *         Does not copy, and returns false if:
 *         - Source and destination rectangles do not intersect.
 *         - SkCanvas pixels could not be converted to bitmap.colorType() or bitmap.alphaType().
 *         - SkCanvas pixels are not readable; for instance, SkCanvas is document-based.
 *         - bitmap pixels could not be allocated.
 *         - bitmap.rowBytes() is too small to contain one row of pixels.
 *
 *         @param bitmap  storage for pixels copied from SkCanvas
 *         @param srcX    offset into readable pixels on x-axis; may be negative
 *         @param srcY    offset into readable pixels on y-axis; may be negative
 *         @return        true if pixels were copied
 *
 *         example: https://fiddle.skia.org/c/@Canvas_readPixels_3
 *     */
 *     bool readPixels(const SkBitmap& bitmap, int srcX, int srcY);
 *
 *     /** Copies SkRect from pixels to SkCanvas. SkMatrix and clip are ignored.
 *         Source SkRect corners are (0, 0) and (info.width(), info.height()).
 *         Destination SkRect corners are (x, y) and
 *         (imageInfo().width(), imageInfo().height()).
 *
 *         Copies each readable pixel intersecting both rectangles, without scaling,
 *         converting to imageInfo().colorType() and imageInfo().alphaType() if required.
 *
 *         Pixels are writable when SkDevice is raster, or backed by a GPU.
 *         Pixels are not writable when SkCanvas is returned by SkDocument::beginPage,
 *         returned by SkPictureRecorder::beginRecording, or SkCanvas is the base of a utility
 *         class like DebugCanvas.
 *
 *         Pixel values are converted only if SkColorType and SkAlphaType
 *         do not match. Only pixels within both source and destination rectangles
 *         are copied. SkCanvas pixels outside SkRect intersection are unchanged.
 *
 *         Pass negative values for x or y to offset pixels to the left or
 *         above SkCanvas pixels.
 *
 *         Does not copy, and returns false if:
 *         - Source and destination rectangles do not intersect.
 *         - pixels could not be converted to SkCanvas imageInfo().colorType() or
 *         imageInfo().alphaType().
 *         - SkCanvas pixels are not writable; for instance, SkCanvas is document-based.
 *         - rowBytes is too small to contain one row of pixels.
 *
 *         @param info      width, height, SkColorType, and SkAlphaType of pixels
 *         @param pixels    pixels to copy, of size info.height() times rowBytes, or larger
 *         @param rowBytes  size of one row of pixels; info.width() times pixel size, or larger
 *         @param x         offset into SkCanvas writable pixels on x-axis; may be negative
 *         @param y         offset into SkCanvas writable pixels on y-axis; may be negative
 *         @return          true if pixels were written to SkCanvas
 *
 *         example: https://fiddle.skia.org/c/@Canvas_writePixels
 *     */
 *     bool writePixels(const SkImageInfo& info, const void* pixels, size_t rowBytes, int x, int y);
 *
 *     /** Copies SkRect from pixels to SkCanvas. SkMatrix and clip are ignored.
 *         Source SkRect corners are (0, 0) and (bitmap.width(), bitmap.height()).
 *
 *         Destination SkRect corners are (x, y) and
 *         (imageInfo().width(), imageInfo().height()).
 *
 *         Copies each readable pixel intersecting both rectangles, without scaling,
 *         converting to imageInfo().colorType() and imageInfo().alphaType() if required.
 *
 *         Pixels are writable when SkDevice is raster, or backed by a GPU.
 *         Pixels are not writable when SkCanvas is returned by SkDocument::beginPage,
 *         returned by SkPictureRecorder::beginRecording, or SkCanvas is the base of a utility
 *         class like DebugCanvas.
 *
 *         Pixel values are converted only if SkColorType and SkAlphaType
 *         do not match. Only pixels within both source and destination rectangles
 *         are copied. SkCanvas pixels outside SkRect intersection are unchanged.
 *
 *         Pass negative values for x or y to offset pixels to the left or
 *         above SkCanvas pixels.
 *
 *         Does not copy, and returns false if:
 *         - Source and destination rectangles do not intersect.
 *         - bitmap does not have allocated pixels.
 *         - bitmap pixels could not be converted to SkCanvas imageInfo().colorType() or
 *         imageInfo().alphaType().
 *         - SkCanvas pixels are not writable; for instance, SkCanvas is document based.
 *         - bitmap pixels are inaccessible; for instance, bitmap wraps a texture.
 *
 *         @param bitmap  contains pixels copied to SkCanvas
 *         @param x       offset into SkCanvas writable pixels on x-axis; may be negative
 *         @param y       offset into SkCanvas writable pixels on y-axis; may be negative
 *         @return        true if pixels were written to SkCanvas
 *
 *         example: https://fiddle.skia.org/c/@Canvas_writePixels_2
 *         example: https://fiddle.skia.org/c/@State_Stack_a
 *         example: https://fiddle.skia.org/c/@State_Stack_b
 *     */
 *     bool writePixels(const SkBitmap& bitmap, int x, int y);
 *
 *     /** Saves SkMatrix and clip.
 *         Calling restore() discards changes to SkMatrix and clip,
 *         restoring the SkMatrix and clip to their state when save() was called.
 *
 *         SkMatrix may be changed by translate(), scale(), rotate(), skew(), concat(), setMatrix(),
 *         and resetMatrix(). Clip may be changed by clipRect(), clipRRect(), clipPath(), clipRegion().
 *
 *         Saved SkCanvas state is put on a stack; multiple calls to save() should be balance
 *         by an equal number of calls to restore().
 *
 *         Call restoreToCount() with result to restore this and subsequent saves.
 *
 *         @return  depth of saved stack
 *
 *         example: https://fiddle.skia.org/c/@Canvas_save
 *     */
 *     int save();
 *
 *     /** Saves SkMatrix and clip, and allocates a SkSurface for subsequent drawing.
 *         Calling restore() discards changes to SkMatrix and clip, and draws the SkSurface.
 *
 *         SkMatrix may be changed by translate(), scale(), rotate(), skew(), concat(),
 *         setMatrix(), and resetMatrix(). Clip may be changed by clipRect(), clipRRect(),
 *         clipPath(), clipRegion().
 *
 *         SkRect bounds suggests but does not define the SkSurface size. To clip drawing to
 *         a specific rectangle, use clipRect().
 *
 *         Optional SkPaint paint applies alpha, SkColorFilter, SkImageFilter, and
 *         SkBlendMode when restore() is called.
 *
 *         Call restoreToCount() with returned value to restore this and subsequent saves.
 *
 *         @param bounds  hint to limit the size of the layer; may be nullptr
 *         @param paint   graphics state for layer; may be nullptr
 *         @return        depth of saved stack
 *
 *         example: https://fiddle.skia.org/c/@Canvas_saveLayer
 *         example: https://fiddle.skia.org/c/@Canvas_saveLayer_4
 *     */
 *     int saveLayer(const SkRect* bounds, const SkPaint* paint);
 *
 *     /** Saves SkMatrix and clip, and allocates a SkSurface for subsequent drawing.
 *         Calling restore() discards changes to SkMatrix and clip, and draws the SkSurface.
 *
 *         SkMatrix may be changed by translate(), scale(), rotate(), skew(), concat(),
 *         setMatrix(), and resetMatrix(). Clip may be changed by clipRect(), clipRRect(),
 *         clipPath(), clipRegion().
 *
 *         SkRect bounds suggests but does not define the layer size. To clip drawing to
 *         a specific rectangle, use clipRect().
 *
 *         Optional SkPaint paint applies alpha, SkColorFilter, SkImageFilter, and
 *         SkBlendMode when restore() is called.
 *
 *         Call restoreToCount() with returned value to restore this and subsequent saves.
 *
 *         @param bounds  hint to limit the size of layer; may be nullptr
 *         @param paint   graphics state for layer; may be nullptr
 *         @return        depth of saved stack
 *     */
 *     int saveLayer(const SkRect& bounds, const SkPaint* paint) {
 *         return this->saveLayer(&bounds, paint);
 *     }
 *
 *     /** Saves SkMatrix and clip, and allocates SkSurface for subsequent drawing.
 *
 *         Calling restore() discards changes to SkMatrix and clip,
 *         and blends layer with alpha opacity onto prior layer.
 *
 *         SkMatrix may be changed by translate(), scale(), rotate(), skew(), concat(),
 *         setMatrix(), and resetMatrix(). Clip may be changed by clipRect(), clipRRect(),
 *         clipPath(), clipRegion().
 *
 *         SkRect bounds suggests but does not define layer size. To clip drawing to
 *         a specific rectangle, use clipRect().
 *
 *         alpha of zero is fully transparent, 1.0f is fully opaque.
 *
 *         Call restoreToCount() with returned value to restore this and subsequent saves.
 *
 *         @param bounds  hint to limit the size of layer; may be nullptr
 *         @param alpha   opacity of layer
 *         @return        depth of saved stack
 *
 *         example: https://fiddle.skia.org/c/@Canvas_saveLayerAlpha
 *     */
 *     int saveLayerAlphaf(const SkRect* bounds, float alpha);
 *     // Helper that accepts an int between 0 and 255, and divides it by 255.0
 *     int saveLayerAlpha(const SkRect* bounds, U8CPU alpha) {
 *         return this->saveLayerAlphaf(bounds, alpha * (1.0f / 255));
 *     }
 *
 *     /** \enum SkCanvas::SaveLayerFlagsSet
 *         SaveLayerFlags provides options that may be used in any combination in SaveLayerRec,
 *         defining how layer allocated by saveLayer() operates. It may be set to zero,
 *         kPreserveLCDText_SaveLayerFlag, kInitWithPrevious_SaveLayerFlag, or both flags.
 *     */
 *     enum SaveLayerFlagsSet {
 *         kPreserveLCDText_SaveLayerFlag  = 1 << 1,
 *         kInitWithPrevious_SaveLayerFlag = 1 << 2, //!< initializes with previous contents
 *         // instead of matching previous layer's colortype, use F16
 *         kF16ColorType                   = 1 << 4,
 *     };
 *
 *     using SaveLayerFlags = uint32_t;
 *     using FilterSpan = SkSpan<sk_sp<SkImageFilter>>;
 *     static constexpr int kMaxFiltersPerLayer = 16;
 *
 *     /** \struct SkCanvas::SaveLayerRec
 *         SaveLayerRec contains the state used to create the layer.
 *     */
 *     struct SaveLayerRec {
 *         /** Sets fBounds, fPaint, and fBackdrop to nullptr. Clears fSaveLayerFlags.
 *
 *             @return  empty SaveLayerRec
 *         */
 *         SaveLayerRec() {}
 *
 *         /** Sets fBounds, fPaint, and fSaveLayerFlags; sets fBackdrop to nullptr.
 *
 *             @param bounds          layer dimensions; may be nullptr
 *             @param paint           applied to layer when overlaying prior layer; may be nullptr
 *             @param saveLayerFlags  SaveLayerRec options to modify layer
 *             @return                SaveLayerRec with empty fBackdrop
 *         */
 *         SaveLayerRec(const SkRect* bounds, const SkPaint* paint, SaveLayerFlags saveLayerFlags = 0)
 *             : SaveLayerRec(bounds, paint, nullptr, nullptr, 1.f, SkTileMode::kClamp,
 *                            saveLayerFlags, /*filters=*/{}) {}
 *
 *         /** Sets fBounds, fPaint, fBackdrop, and fSaveLayerFlags.
 *
 *             @param bounds          layer dimensions; may be nullptr
 *             @param paint           applied to layer when overlaying prior layer;
 *                                    may be nullptr
 *             @param backdrop        If not null, this causes the current layer to be filtered by
 *                                    backdrop, and then drawn into the new layer
 *                                    (respecting the current clip).
 *                                    If null, the new layer is initialized with transparent-black.
 *             @param saveLayerFlags  SaveLayerRec options to modify layer
 *             @return                SaveLayerRec fully specified
 *         */
 *         SaveLayerRec(const SkRect* bounds, const SkPaint* paint, const SkImageFilter* backdrop,
 *                      SaveLayerFlags saveLayerFlags)
 *             : SaveLayerRec(bounds, paint, backdrop, nullptr, 1.f, SkTileMode::kClamp,
 *                            saveLayerFlags, /*filters=*/{}) {}
 *
 *         /** Sets fBounds, fBackdrop, fColorSpace, and fSaveLayerFlags.
 *
 *             @param bounds          layer dimensions; may be nullptr
 *             @param paint           applied to layer when overlaying prior layer;
 *                                    may be nullptr
 *             @param backdrop        If not null, this causes the current layer to be filtered by
 *                                    backdrop, and then drawn into the new layer
 *                                    (respecting the current clip).
 *                                    If null, the new layer is initialized with transparent-black.
 *             @param colorSpace      If not null, when the layer is restored, a color space
 *                                    conversion will be applied from this color space to the
 *                                    parent's color space. The restore paint and backdrop filters will
 *                                    be applied in this color space.
 *                                    If null, the new layer will inherit the color space from its
 *                                    parent.
 *             @param saveLayerFlags  SaveLayerRec options to modify layer
 *             @return                SaveLayerRec fully specified
 *         */
 *         SaveLayerRec(const SkRect* bounds, const SkPaint* paint, const SkImageFilter* backdrop,
 *                      const SkColorSpace* colorSpace, SaveLayerFlags saveLayerFlags)
 *             : SaveLayerRec(bounds, paint, backdrop, colorSpace, 1.f, SkTileMode::kClamp,
 *                            saveLayerFlags, /*filters=*/{}) {}
 *
 *
 *         /** Sets fBounds, fBackdrop, fBackdropTileMode, fColorSpace, and fSaveLayerFlags.
 *
 *             @param bounds           layer dimensions; may be nullptr
 *             @param paint            applied to layer when overlaying prior layer;
 *                                     may be nullptr
 *             @param backdrop         If not null, this causes the current layer to be filtered by
 *                                     backdrop, and then drawn into the new layer
 *                                     (respecting the current clip).
 *                                     If null, the new layer is initialized with transparent-black.
 *             @param backdropTileMode If the 'backdrop' is not null, or 'saveLayerFlags' has
 *                                     kInitWithPrevious set, this tile mode is used when the new layer
 *                                     would read outside the backdrop image's available content.
 *             @param colorSpace       If not null, when the layer is restored, a color space
 *                                     conversion will be applied from this color space to the parent's
 *                                     color space. The restore paint and backdrop filters will be
 *                                     applied in this color space.
 *                                     If null, the new layer will inherit the color space from its
 *                                     parent.
 *             @param saveLayerFlags   SaveLayerRec options to modify layer
 *             @return                 SaveLayerRec fully specified
 *         */
 *         SaveLayerRec(const SkRect* bounds, const SkPaint* paint, const SkImageFilter* backdrop,
 *                      SkTileMode backdropTileMode, const SkColorSpace* colorSpace,
 *                      SaveLayerFlags saveLayerFlags)
 *             : SaveLayerRec(bounds, paint, backdrop, colorSpace, 1.f, backdropTileMode,
 *                            saveLayerFlags, /*filters=*/{}) {}
 *
 *         /** hints at layer size limit */
 *         const SkRect* fBounds = nullptr;
 *
 *         /** modifies overlay */
 *         const SkPaint* fPaint = nullptr;
 *
 *         FilterSpan fFilters = {};
 *
 *         /**
 *          *  If not null, this triggers the same initialization behavior as setting
 *          *  kInitWithPrevious_SaveLayerFlag on fSaveLayerFlags: the current layer is copied into
 *          *  the new layer, rather than initializing the new layer with transparent-black.
 *          *  This is then filtered by fBackdrop (respecting the current clip).
 *          */
 *         const SkImageFilter* fBackdrop = nullptr;
 *
 *         /**
 *          * If the layer is initialized with prior content (and/or with a backdrop filter) and this
 *          * would require sampling outside of the available backdrop, this is the tilemode applied
 *          * to the boundary of the prior layer's image.
 *          */
 *         SkTileMode fBackdropTileMode = SkTileMode::kClamp;
 *
 *         /**
 *          * If not null, this triggers a color space conversion when the layer is restored. It
 *          * will be as if the layer's contents are drawn in this color space. Filters from
 *          * fBackdrop and fPaint will be applied in this color space.
 *          */
 *         const SkColorSpace* fColorSpace = nullptr;
 *
 *         /** preserves LCD text, creates with prior layer contents */
 *         SaveLayerFlags fSaveLayerFlags = 0;
 *
 *     private:
 *         friend class SkCanvas;
 *         friend class SkCanvasPriv;
 *
 *         SaveLayerRec(const SkRect* bounds,
 *                      const SkPaint* paint,
 *                      const SkImageFilter* backdrop,
 *                      const SkColorSpace* colorSpace,
 *                      SkScalar backdropScale,
 *                      SkTileMode backdropTileMode,
 *                      SaveLayerFlags saveLayerFlags,
 *                      FilterSpan filters)
 *                 : fBounds(bounds)
 *                 , fPaint(paint)
 *                 , fFilters(filters)
 *                 , fBackdrop(backdrop)
 *                 , fBackdropTileMode(backdropTileMode)
 *                 , fColorSpace(colorSpace)
 *                 , fSaveLayerFlags(saveLayerFlags)
 *                 , fExperimentalBackdropScale(backdropScale) {
 *             // We only allow the paint's image filter or the side-car list of filters -- not both.
 *             SkASSERT(fFilters.empty() || !paint || !paint->getImageFilter());
 *             // To keep things reasonable (during deserialization), we limit filter list size.
 *             SkASSERT(fFilters.size() <= kMaxFiltersPerLayer);
 *         }
 *
 *         // Relative scale factor that the image content used to initialize the layer when the
 *         // kInitFromPrevious flag or a backdrop filter is used.
 *         SkScalar             fExperimentalBackdropScale = 1.f;
 *     };
 *
 *     /** Saves SkMatrix and clip, and allocates SkSurface for subsequent drawing.
 *
 *         Calling restore() discards changes to SkMatrix and clip,
 *         and blends SkSurface with alpha opacity onto the prior layer.
 *
 *         SkMatrix may be changed by translate(), scale(), rotate(), skew(), concat(),
 *         setMatrix(), and resetMatrix(). Clip may be changed by clipRect(), clipRRect(),
 *         clipPath(), clipRegion().
 *
 *         SaveLayerRec contains the state used to create the layer.
 *
 *         Call restoreToCount() with returned value to restore this and subsequent saves.
 *
 *         @param layerRec  layer state
 *         @return          depth of save state stack before this call was made.
 *
 *         example: https://fiddle.skia.org/c/@Canvas_saveLayer_3
 *     */
 *     int saveLayer(const SaveLayerRec& layerRec);
 *
 *     /** Removes changes to SkMatrix and clip since SkCanvas state was
 *         last saved. The state is removed from the stack.
 *
 *         Does nothing if the stack is empty.
 *
 *         example: https://fiddle.skia.org/c/@AutoCanvasRestore_restore
 *
 *         example: https://fiddle.skia.org/c/@Canvas_restore
 *     */
 *     void restore();
 *
 *     /** Returns the number of saved states, each containing: SkMatrix and clip.
 *         Equals the number of save() calls less the number of restore() calls plus one.
 *         The save count of a new canvas is one.
 *
 *         @return  depth of save state stack
 *
 *         example: https://fiddle.skia.org/c/@Canvas_getSaveCount
 *     */
 *     int getSaveCount() const;
 *
 *     /** Restores state to SkMatrix and clip values when save(), saveLayer(),
 *         saveLayerPreserveLCDTextRequests(), or saveLayerAlpha() returned saveCount.
 *
 *         Does nothing if saveCount is greater than state stack count.
 *         Restores state to initial values if saveCount is less than or equal to one.
 *
 *         @param saveCount  depth of state stack to restore
 *
 *         example: https://fiddle.skia.org/c/@Canvas_restoreToCount
 *     */
 *     void restoreToCount(int saveCount);
 *
 *     /** Translates SkMatrix by dx along the x-axis and dy along the y-axis.
 *
 *         Mathematically, replaces SkMatrix with a translation matrix
 *         premultiplied with SkMatrix.
 *
 *         This has the effect of moving the drawing by (dx, dy) before transforming
 *         the result with SkMatrix.
 *
 *         @param dx  distance to translate on x-axis
 *         @param dy  distance to translate on y-axis
 *
 *         example: https://fiddle.skia.org/c/@Canvas_translate
 *     */
 *     void translate(SkScalar dx, SkScalar dy);
 *
 *     /** Scales SkMatrix by sx on the x-axis and sy on the y-axis.
 *
 *         Mathematically, replaces SkMatrix with a scale matrix
 *         premultiplied with SkMatrix.
 *
 *         This has the effect of scaling the drawing by (sx, sy) before transforming
 *         the result with SkMatrix.
 *
 *         @param sx  amount to scale on x-axis
 *         @param sy  amount to scale on y-axis
 *
 *         example: https://fiddle.skia.org/c/@Canvas_scale
 *     */
 *     void scale(SkScalar sx, SkScalar sy);
 *
 *     /** Rotates SkMatrix by degrees. Positive degrees rotates clockwise.
 *
 *         Mathematically, replaces SkMatrix with a rotation matrix
 *         premultiplied with SkMatrix.
 *
 *         This has the effect of rotating the drawing by degrees before transforming
 *         the result with SkMatrix.
 *
 *         @param degrees  amount to rotate, in degrees
 *
 *         example: https://fiddle.skia.org/c/@Canvas_rotate
 *     */
 *     void rotate(SkScalar degrees);
 *
 *     /** Rotates SkMatrix by degrees about a point at (px, py). Positive degrees rotates
 *         clockwise.
 *
 *         Mathematically, constructs a rotation matrix; premultiplies the rotation matrix by
 *         a translation matrix; then replaces SkMatrix with the resulting matrix
 *         premultiplied with SkMatrix.
 *
 *         This has the effect of rotating the drawing about a given point before
 *         transforming the result with SkMatrix.
 *
 *         @param degrees  amount to rotate, in degrees
 *         @param px       x-axis value of the point to rotate about
 *         @param py       y-axis value of the point to rotate about
 *
 *         example: https://fiddle.skia.org/c/@Canvas_rotate_2
 *     */
 *     void rotate(SkScalar degrees, SkScalar px, SkScalar py);
 *
 *     /** Skews SkMatrix by sx on the x-axis and sy on the y-axis. A positive value of sx
 *         skews the drawing right as y-axis values increase; a positive value of sy skews
 *         the drawing down as x-axis values increase.
 *
 *         Mathematically, replaces SkMatrix with a skew matrix premultiplied with SkMatrix.
 *
 *         This has the effect of skewing the drawing by (sx, sy) before transforming
 *         the result with SkMatrix.
 *
 *         @param sx  amount to skew on x-axis
 *         @param sy  amount to skew on y-axis
 *
 *         example: https://fiddle.skia.org/c/@Canvas_skew
 *     */
 *     void skew(SkScalar sx, SkScalar sy);
 *
 *     /** Replaces SkMatrix with matrix premultiplied with existing SkMatrix.
 *
 *         This has the effect of transforming the drawn geometry by matrix, before
 *         transforming the result with existing SkMatrix.
 *
 *         @param matrix  matrix to premultiply with existing SkMatrix
 *
 *         example: https://fiddle.skia.org/c/@Canvas_concat
 *     */
 *     void concat(const SkMatrix& matrix);
 *     void concat(const SkM44&);
 *
 *     /** Replaces SkMatrix with matrix.
 *         Unlike concat(), any prior matrix state is overwritten.
 *
 *         @param matrix  matrix to copy, replacing existing SkMatrix
 *
 *         example: https://fiddle.skia.org/c/@Canvas_setMatrix
 *     */
 *     void setMatrix(const SkM44& matrix);
 *
 *     // DEPRECATED -- use SkM44 version
 *     void setMatrix(const SkMatrix& matrix);
 *
 *     /** Sets SkMatrix to the identity matrix.
 *         Any prior matrix state is overwritten.
 *
 *         example: https://fiddle.skia.org/c/@Canvas_resetMatrix
 *     */
 *     void resetMatrix();
 *
 *     /** Replaces clip with the intersection or difference of clip and rect,
 *         with an aliased or anti-aliased clip edge. rect is transformed by SkMatrix
 *         before it is combined with clip.
 *
 *         @param rect         SkRect to combine with clip
 *         @param op           SkClipOp to apply to clip
 *         @param doAntiAlias  true if clip is to be anti-aliased
 *
 *         example: https://fiddle.skia.org/c/@Canvas_clipRect
 *     */
 *     void clipRect(const SkRect& rect, SkClipOp op, bool doAntiAlias);
 *
 *     /** Replaces clip with the intersection or difference of clip and rect.
 *         Resulting clip is aliased; pixels are fully contained by the clip.
 *         rect is transformed by SkMatrix before it is combined with clip.
 *
 *         @param rect  SkRect to combine with clip
 *         @param op    SkClipOp to apply to clip
 *     */
 *     void clipRect(const SkRect& rect, SkClipOp op) {
 *         this->clipRect(rect, op, false);
 *     }
 *
 *     /** Replaces clip with the intersection of clip and rect.
 *         Resulting clip is aliased; pixels are fully contained by the clip.
 *         rect is transformed by SkMatrix
 *         before it is combined with clip.
 *
 *         @param rect         SkRect to combine with clip
 *         @param doAntiAlias  true if clip is to be anti-aliased
 *     */
 *     void clipRect(const SkRect& rect, bool doAntiAlias = false) {
 *         this->clipRect(rect, SkClipOp::kIntersect, doAntiAlias);
 *     }
 *
 *     void clipIRect(const SkIRect& irect, SkClipOp op = SkClipOp::kIntersect) {
 *         this->clipRect(SkRect::Make(irect), op, false);
 *     }
 *
 *     /** Sets the maximum clip rectangle, which can be set by clipRect(), clipRRect() and
 *         clipPath() and intersect the current clip with the specified rect.
 *         The maximum clip affects only future clipping operations; it is not retroactive.
 *         The clip restriction is not recorded in pictures.
 *
 *         Pass an empty rect to disable maximum clip.
 *         This private API is for use by Android framework only.
 *
 *         DEPRECATED: Replace usage with SkAndroidFrameworkUtils::replaceClip()
 *
 *         @param rect  maximum allowed clip in device coordinates
 *     */
 *     void androidFramework_setDeviceClipRestriction(const SkIRect& rect);
 *
 *     /** Replaces clip with the intersection or difference of clip and rrect,
 *         with an aliased or anti-aliased clip edge.
 *         rrect is transformed by SkMatrix
 *         before it is combined with clip.
 *
 *         @param rrect        SkRRect to combine with clip
 *         @param op           SkClipOp to apply to clip
 *         @param doAntiAlias  true if clip is to be anti-aliased
 *
 *         example: https://fiddle.skia.org/c/@Canvas_clipRRect
 *     */
 *     void clipRRect(const SkRRect& rrect, SkClipOp op, bool doAntiAlias);
 *
 *     /** Replaces clip with the intersection or difference of clip and rrect.
 *         Resulting clip is aliased; pixels are fully contained by the clip.
 *         rrect is transformed by SkMatrix before it is combined with clip.
 *
 *         @param rrect  SkRRect to combine with clip
 *         @param op     SkClipOp to apply to clip
 *     */
 *     void clipRRect(const SkRRect& rrect, SkClipOp op) {
 *         this->clipRRect(rrect, op, false);
 *     }
 *
 *     /** Replaces clip with the intersection of clip and rrect,
 *         with an aliased or anti-aliased clip edge.
 *         rrect is transformed by SkMatrix before it is combined with clip.
 *
 *         @param rrect        SkRRect to combine with clip
 *         @param doAntiAlias  true if clip is to be anti-aliased
 *     */
 *     void clipRRect(const SkRRect& rrect, bool doAntiAlias = false) {
 *         this->clipRRect(rrect, SkClipOp::kIntersect, doAntiAlias);
 *     }
 *
 *     /** Replaces clip with the intersection or difference of clip and path,
 *         with an aliased or anti-aliased clip edge. SkPath::FillType determines if path
 *         describes the area inside or outside its contours; and if path contour overlaps
 *         itself or another path contour, whether the overlaps form part of the area.
 *         path is transformed by SkMatrix before it is combined with clip.
 *
 *         @param path         SkPath to combine with clip
 *         @param op           SkClipOp to apply to clip
 *         @param doAntiAlias  true if clip is to be anti-aliased
 *
 *         example: https://fiddle.skia.org/c/@Canvas_clipPath
 *     */
 *     void clipPath(const SkPath& path, SkClipOp op, bool doAntiAlias);
 *
 *     /** Replaces clip with the intersection or difference of clip and path.
 *         Resulting clip is aliased; pixels are fully contained by the clip.
 *         SkPath::FillType determines if path
 *         describes the area inside or outside its contours; and if path contour overlaps
 *         itself or another path contour, whether the overlaps form part of the area.
 *         path is transformed by SkMatrix
 *         before it is combined with clip.
 *
 *         @param path  SkPath to combine with clip
 *         @param op    SkClipOp to apply to clip
 *     */
 *     void clipPath(const SkPath& path, SkClipOp op) {
 *         this->clipPath(path, op, false);
 *     }
 *
 *     /** Replaces clip with the intersection of clip and path.
 *         Resulting clip is aliased; pixels are fully contained by the clip.
 *         SkPath::FillType determines if path
 *         describes the area inside or outside its contours; and if path contour overlaps
 *         itself or another path contour, whether the overlaps form part of the area.
 *         path is transformed by SkMatrix before it is combined with clip.
 *
 *         @param path         SkPath to combine with clip
 *         @param doAntiAlias  true if clip is to be anti-aliased
 *     */
 *     void clipPath(const SkPath& path, bool doAntiAlias = false) {
 *         this->clipPath(path, SkClipOp::kIntersect, doAntiAlias);
 *     }
 *
 *     void clipShader(sk_sp<SkShader>, SkClipOp = SkClipOp::kIntersect);
 *
 *     /** Replaces clip with the intersection or difference of clip and SkRegion deviceRgn.
 *         Resulting clip is aliased; pixels are fully contained by the clip.
 *         deviceRgn is unaffected by SkMatrix.
 *
 *         @param deviceRgn  SkRegion to combine with clip
 *         @param op         SkClipOp to apply to clip
 *
 *         example: https://fiddle.skia.org/c/@Canvas_clipRegion
 *     */
 *     void clipRegion(const SkRegion& deviceRgn, SkClipOp op = SkClipOp::kIntersect);
 *
 *     /** Returns true if SkRect rect, transformed by SkMatrix, can be quickly determined to be
 *         outside of clip. May return false even though rect is outside of clip.
 *
 *         Use to check if an area to be drawn is clipped out, to skip subsequent draw calls.
 *
 *         @param rect  SkRect to compare with clip
 *         @return      true if rect, transformed by SkMatrix, does not intersect clip
 *
 *         example: https://fiddle.skia.org/c/@Canvas_quickReject
 *     */
 *     bool quickReject(const SkRect& rect) const;
 *
 *     /** Returns true if path, transformed by SkMatrix, can be quickly determined to be
 *         outside of clip. May return false even though path is outside of clip.
 *
 *         Use to check if an area to be drawn is clipped out, to skip subsequent draw calls.
 *
 *         @param path  SkPath to compare with clip
 *         @return      true if path, transformed by SkMatrix, does not intersect clip
 *
 *         example: https://fiddle.skia.org/c/@Canvas_quickReject_2
 *     */
 *     bool quickReject(const SkPath& path) const;
 *
 *     /** Returns bounds of clip, transformed by inverse of SkMatrix. If clip is empty,
 *         return SkRect::MakeEmpty, where all SkRect sides equal zero.
 *
 *         SkRect returned is outset by one to account for partial pixel coverage if clip
 *         is anti-aliased.
 *
 *         @return  bounds of clip in local coordinates
 *
 *         example: https://fiddle.skia.org/c/@Canvas_getLocalClipBounds
 *     */
 *     SkRect getLocalClipBounds() const;
 *
 *     /** Returns bounds of clip, transformed by inverse of SkMatrix. If clip is empty,
 *         return false, and set bounds to SkRect::MakeEmpty, where all SkRect sides equal zero.
 *
 *         bounds is outset by one to account for partial pixel coverage if clip
 *         is anti-aliased.
 *
 *         @param bounds  SkRect of clip in local coordinates
 *         @return        true if clip bounds is not empty
 *     */
 *     bool getLocalClipBounds(SkRect* bounds) const {
 *         *bounds = this->getLocalClipBounds();
 *         return !bounds->isEmpty();
 *     }
 *
 *     /** Returns SkIRect bounds of clip, unaffected by SkMatrix. If clip is empty,
 *         return SkRect::MakeEmpty, where all SkRect sides equal zero.
 *
 *         Unlike getLocalClipBounds(), returned SkIRect is not outset.
 *
 *         @return  bounds of clip in base device coordinates
 *
 *         example: https://fiddle.skia.org/c/@Canvas_getDeviceClipBounds
 *     */
 *     SkIRect getDeviceClipBounds() const;
 *
 *     /** Returns SkIRect bounds of clip, unaffected by SkMatrix. If clip is empty,
 *         return false, and set bounds to SkRect::MakeEmpty, where all SkRect sides equal zero.
 *
 *         Unlike getLocalClipBounds(), bounds is not outset.
 *
 *         @param bounds  SkRect of clip in device coordinates
 *         @return        true if clip bounds is not empty
 *     */
 *     bool getDeviceClipBounds(SkIRect* bounds) const {
 *         *bounds = this->getDeviceClipBounds();
 *         return !bounds->isEmpty();
 *     }
 *
 *     /** Fills clip with color color.
 *         mode determines how ARGB is combined with destination.
 *
 *         @param color  unpremultiplied ARGB
 *         @param mode   SkBlendMode used to combine source color and destination
 *
 *         example: https://fiddle.skia.org/c/@Canvas_drawColor
 *     */
 *     void drawColor(SkColor color, SkBlendMode mode = SkBlendMode::kSrcOver) {
 *         this->drawColor(SkColor4f::FromColor(color), mode);
 *     }
 *
 *     /** Fills clip with color color.
 *         mode determines how ARGB is combined with destination.
 *
 *         @param color  SkColor4f representing unpremultiplied color.
 *         @param mode   SkBlendMode used to combine source color and destination
 *     */
 *     void drawColor(const SkColor4f& color, SkBlendMode mode = SkBlendMode::kSrcOver);
 *
 *     /** Fills clip with color color using SkBlendMode::kSrc.
 *         This has the effect of replacing all pixels contained by clip with color.
 *
 *         @param color  unpremultiplied ARGB
 *     */
 *     void clear(SkColor color) {
 *         this->clear(SkColor4f::FromColor(color));
 *     }
 *
 *     /** Fills clip with color color using SkBlendMode::kSrc.
 *         This has the effect of replacing all pixels contained by clip with color.
 *
 *         @param color  SkColor4f representing unpremultiplied color.
 *     */
 *     void clear(const SkColor4f& color) {
 *         this->drawColor(color, SkBlendMode::kSrc);
 *     }
 *
 *     /** Makes SkCanvas contents undefined. Subsequent calls that read SkCanvas pixels,
 *         such as drawing with SkBlendMode, return undefined results. discard() does
 *         not change clip or SkMatrix.
 *
 *         discard() may do nothing, depending on the implementation of SkSurface or SkDevice
 *         that created SkCanvas.
 *
 *         discard() allows optimized performance on subsequent draws by removing
 *         cached data associated with SkSurface or SkDevice.
 *         It is not necessary to call discard() once done with SkCanvas;
 *         any cached data is deleted when owning SkSurface or SkDevice is deleted.
 *     */
 *     void discard() { this->onDiscard(); }
 *
 *     /** Fills clip with SkPaint paint. SkPaint components, SkShader,
 *         SkColorFilter, SkImageFilter, and SkBlendMode affect drawing;
 *         SkMaskFilter and SkPathEffect in paint are ignored.
 *
 *         @param paint  graphics state used to fill SkCanvas
 *
 *         example: https://fiddle.skia.org/c/@Canvas_drawPaint
 *     */
 *     void drawPaint(const SkPaint& paint);
 *
 *     /** \enum SkCanvas::PointMode
 *         Selects if an array of points are drawn as discrete points, as lines, or as
 *         an open polygon.
 *     */
 *     enum PointMode {
 *         kPoints_PointMode,  //!< draw each point separately
 *         kLines_PointMode,   //!< draw each pair of points as a line segment
 *         kPolygon_PointMode, //!< draw the array of points as a open polygon
 *     };
 *
 *     /** Draws pts using clip, SkMatrix and SkPaint paint.
 *         count is the number of points; if count is less than one, has no effect.
 *         mode may be one of: kPoints_PointMode, kLines_PointMode, or kPolygon_PointMode.
 *
 *         If mode is kPoints_PointMode, the shape of point drawn depends on paint
 *         SkPaint::Cap. If paint is set to SkPaint::kRound_Cap, each point draws a
 *         circle of diameter SkPaint stroke width. If paint is set to SkPaint::kSquare_Cap
 *         or SkPaint::kButt_Cap, each point draws a square of width and height
 *         SkPaint stroke width.
 *
 *         If mode is kLines_PointMode, each pair of points draws a line segment.
 *         One line is drawn for every two points; each point is used once. If count is odd,
 *         the final point is ignored.
 *
 *         If mode is kPolygon_PointMode, each adjacent pair of points draws a line segment.
 *         count minus one lines are drawn; the first and last point are used once.
 *
 *         Each line segment respects paint SkPaint::Cap and SkPaint stroke width.
 *         SkPaint::Style is ignored, as if were set to SkPaint::kStroke_Style.
 *
 *         Always draws each element one at a time; is not affected by
 *         SkPaint::Join, and unlike drawPath(), does not create a mask from all points
 *         and lines before drawing.
 *
 *         @param mode   whether pts draws points or lines
 *         @param count  number of points in the array
 *         @param pts    array of points to draw
 *         @param paint  stroke, blend, color, and so on, used to draw
 *
 *         example: https://fiddle.skia.org/c/@Canvas_drawPoints
 *     */
 *     void drawPoints(PointMode mode, SkSpan<const SkPoint>, const SkPaint& paint);
 * #ifdef SK_SUPPORT_UNSPANNED_APIS
 *     void drawPoints(PointMode mode, size_t count, const SkPoint pts[], const SkPaint& paint) {
 *         this->drawPoints(mode, {pts, count}, paint);
 *     }
 * #endif
 *
 *     /** Draws point at (x, y) using clip, SkMatrix and SkPaint paint.
 *
 *         The shape of point drawn depends on paint SkPaint::Cap.
 *         If paint is set to SkPaint::kRound_Cap, draw a circle of diameter
 *         SkPaint stroke width. If paint is set to SkPaint::kSquare_Cap or SkPaint::kButt_Cap,
 *         draw a square of width and height SkPaint stroke width.
 *         SkPaint::Style is ignored, as if were set to SkPaint::kStroke_Style.
 *
 *         @param x      left edge of circle or square
 *         @param y      top edge of circle or square
 *         @param paint  stroke, blend, color, and so on, used to draw
 *
 *         example: https://fiddle.skia.org/c/@Canvas_drawPoint
 *     */
 *     void drawPoint(SkScalar x, SkScalar y, const SkPaint& paint);
 *
 *     /** Draws point p using clip, SkMatrix and SkPaint paint.
 *
 *         The shape of point drawn depends on paint SkPaint::Cap.
 *         If paint is set to SkPaint::kRound_Cap, draw a circle of diameter
 *         SkPaint stroke width. If paint is set to SkPaint::kSquare_Cap or SkPaint::kButt_Cap,
 *         draw a square of width and height SkPaint stroke width.
 *         SkPaint::Style is ignored, as if were set to SkPaint::kStroke_Style.
 *
 *         @param p      top-left edge of circle or square
 *         @param paint  stroke, blend, color, and so on, used to draw
 *     */
 *     void drawPoint(SkPoint p, const SkPaint& paint) {
 *         this->drawPoint(p.x(), p.y(), paint);
 *     }
 *
 *     /** Draws line segment from (x0, y0) to (x1, y1) using clip, SkMatrix, and SkPaint paint.
 *         In paint: SkPaint stroke width describes the line thickness;
 *         SkPaint::Cap draws the end rounded or square;
 *         SkPaint::Style is ignored, as if were set to SkPaint::kStroke_Style.
 *
 *         @param x0     start of line segment on x-axis
 *         @param y0     start of line segment on y-axis
 *         @param x1     end of line segment on x-axis
 *         @param y1     end of line segment on y-axis
 *         @param paint  stroke, blend, color, and so on, used to draw
 *
 *         example: https://fiddle.skia.org/c/@Canvas_drawLine
 *     */
 *     void drawLine(SkScalar x0, SkScalar y0, SkScalar x1, SkScalar y1, const SkPaint& paint);
 *
 *     /** Draws line segment from p0 to p1 using clip, SkMatrix, and SkPaint paint.
 *         In paint: SkPaint stroke width describes the line thickness;
 *         SkPaint::Cap draws the end rounded or square;
 *         SkPaint::Style is ignored, as if were set to SkPaint::kStroke_Style.
 *
 *         @param p0     start of line segment
 *         @param p1     end of line segment
 *         @param paint  stroke, blend, color, and so on, used to draw
 *     */
 *     void drawLine(SkPoint p0, SkPoint p1, const SkPaint& paint) {
 *         this->drawLine(p0.x(), p0.y(), p1.x(), p1.y(), paint);
 *     }
 *
 *     /** Draws SkRect rect using clip, SkMatrix, and SkPaint paint.
 *         In paint: SkPaint::Style determines if rectangle is stroked or filled;
 *         if stroked, SkPaint stroke width describes the line thickness, and
 *         SkPaint::Join draws the corners rounded or square.
 *
 *         @param rect   rectangle to draw
 *         @param paint  stroke or fill, blend, color, and so on, used to draw
 *
 *         example: https://fiddle.skia.org/c/@Canvas_drawRect
 *     */
 *     void drawRect(const SkRect& rect, const SkPaint& paint);
 *
 *     /** Draws SkIRect rect using clip, SkMatrix, and SkPaint paint.
 *         In paint: SkPaint::Style determines if rectangle is stroked or filled;
 *         if stroked, SkPaint stroke width describes the line thickness, and
 *         SkPaint::Join draws the corners rounded or square.
 *
 *         @param rect   rectangle to draw
 *         @param paint  stroke or fill, blend, color, and so on, used to draw
 *     */
 *     void drawIRect(const SkIRect& rect, const SkPaint& paint) {
 *         SkRect r;
 *         r.set(rect);    // promotes the ints to scalars
 *         this->drawRect(r, paint);
 *     }
 *
 *     /** Draws SkRegion region using clip, SkMatrix, and SkPaint paint.
 *         In paint: SkPaint::Style determines if rectangle is stroked or filled;
 *         if stroked, SkPaint stroke width describes the line thickness, and
 *         SkPaint::Join draws the corners rounded or square.
 *
 *         @param region  region to draw
 *         @param paint   SkPaint stroke or fill, blend, color, and so on, used to draw
 *
 *         example: https://fiddle.skia.org/c/@Canvas_drawRegion
 *     */
 *     void drawRegion(const SkRegion& region, const SkPaint& paint);
 *
 *     /** Draws oval oval using clip, SkMatrix, and SkPaint.
 *         In paint: SkPaint::Style determines if oval is stroked or filled;
 *         if stroked, SkPaint stroke width describes the line thickness.
 *
 *         @param oval   SkRect bounds of oval
 *         @param paint  SkPaint stroke or fill, blend, color, and so on, used to draw
 *
 *         example: https://fiddle.skia.org/c/@Canvas_drawOval
 *     */
 *     void drawOval(const SkRect& oval, const SkPaint& paint);
 *
 *     /** Draws SkRRect rrect using clip, SkMatrix, and SkPaint paint.
 *         In paint: SkPaint::Style determines if rrect is stroked or filled;
 *         if stroked, SkPaint stroke width describes the line thickness.
 *
 *         rrect may represent a rectangle, circle, oval, uniformly rounded rectangle, or
 *         may have any combination of positive non-square radii for the four corners.
 *
 *         @param rrect  SkRRect with up to eight corner radii to draw
 *         @param paint  SkPaint stroke or fill, blend, color, and so on, used to draw
 *
 *         example: https://fiddle.skia.org/c/@Canvas_drawRRect
 *     */
 *     void drawRRect(const SkRRect& rrect, const SkPaint& paint);
 *
 *     /** Draws SkRRect outer and inner
 *         using clip, SkMatrix, and SkPaint paint.
 *         outer must contain inner or the drawing is undefined.
 *         In paint: SkPaint::Style determines if SkRRect is stroked or filled;
 *         if stroked, SkPaint stroke width describes the line thickness.
 *         If stroked and SkRRect corner has zero length radii, SkPaint::Join can
 *         draw corners rounded or square.
 *
 *         GPU-backed platforms optimize drawing when both outer and inner are
 *         concave and outer contains inner. These platforms may not be able to draw
 *         SkPath built with identical data as fast.
 *
 *         @param outer  SkRRect outer bounds to draw
 *         @param inner  SkRRect inner bounds to draw
 *         @param paint  SkPaint stroke or fill, blend, color, and so on, used to draw
 *
 *         example: https://fiddle.skia.org/c/@Canvas_drawDRRect_a
 *         example: https://fiddle.skia.org/c/@Canvas_drawDRRect_b
 *     */
 *     void drawDRRect(const SkRRect& outer, const SkRRect& inner, const SkPaint& paint);
 *
 *     /** Draws circle at (cx, cy) with radius using clip, SkMatrix, and SkPaint paint.
 *         If radius is zero or less, nothing is drawn.
 *         In paint: SkPaint::Style determines if circle is stroked or filled;
 *         if stroked, SkPaint stroke width describes the line thickness.
 *
 *         @param cx      circle center on the x-axis
 *         @param cy      circle center on the y-axis
 *         @param radius  half the diameter of circle
 *         @param paint   SkPaint stroke or fill, blend, color, and so on, used to draw
 *
 *         example: https://fiddle.skia.org/c/@Canvas_drawCircle
 *     */
 *     void drawCircle(SkScalar cx, SkScalar cy, SkScalar radius, const SkPaint& paint);
 *
 *     /** Draws circle at center with radius using clip, SkMatrix, and SkPaint paint.
 *         If radius is zero or less, nothing is drawn.
 *         In paint: SkPaint::Style determines if circle is stroked or filled;
 *         if stroked, SkPaint stroke width describes the line thickness.
 *
 *         @param center  circle center
 *         @param radius  half the diameter of circle
 *         @param paint   SkPaint stroke or fill, blend, color, and so on, used to draw
 *     */
 *     void drawCircle(SkPoint center, SkScalar radius, const SkPaint& paint) {
 *         this->drawCircle(center.x(), center.y(), radius, paint);
 *     }
 *
 *     /** Draws arc using clip, SkMatrix, and SkPaint paint.
 *
 *         Arc is part of oval bounded by oval, sweeping from startAngle to startAngle plus
 *         sweepAngle. startAngle and sweepAngle are in degrees.
 *
 *         startAngle of zero places start point at the right middle edge of oval.
 *         A positive sweepAngle places arc end point clockwise from start point;
 *         a negative sweepAngle places arc end point counterclockwise from start point.
 *         sweepAngle may exceed 360 degrees, a full circle.
 *         If useCenter is true, draw a wedge that includes lines from oval
 *         center to arc end points. If useCenter is false, draw arc between end points.
 *
 *         If SkRect oval is empty or sweepAngle is zero, nothing is drawn.
 *
 *         @param oval        SkRect bounds of oval containing arc to draw
 *         @param startAngle  angle in degrees where arc begins
 *         @param sweepAngle  sweep angle in degrees; positive is clockwise
 *         @param useCenter   if true, include the center of the oval
 *         @param paint       SkPaint stroke or fill, blend, color, and so on, used to draw
 *     */
 *     void drawArc(const SkRect& oval, SkScalar startAngle, SkScalar sweepAngle,
 *                  bool useCenter, const SkPaint& paint);
 *
 *     /** Draws arc using clip, SkMatrix, and SkPaint paint.
 *
 *         Arc is part of oval bounded by oval, sweeping from startAngle to startAngle plus
 *         sweepAngle. startAngle and sweepAngle are in degrees.
 *
 *         startAngle of zero places start point at the right middle edge of oval.
 *         A positive sweepAngle places arc end point clockwise from start point;
 *         a negative sweepAngle places arc end point counterclockwise from start point.
 *         sweepAngle may exceed 360 degrees, a full circle.
 *         If useCenter is true, draw a wedge that includes lines from oval
 *         center to arc end points. If useCenter is false, draw arc between end points.
 *
 *         If SkRect oval is empty or sweepAngle is zero, nothing is drawn.
 *
 *         @param arc    SkArc specifying oval, startAngle, sweepAngle, and arc-vs-wedge
 *         @param paint  SkPaint stroke or fill, blend, color, and so on, used to draw
 *     */
 *     void drawArc(const SkArc& arc, const SkPaint& paint) {
 *         this->drawArc(arc.fOval, arc.fStartAngle, arc.fSweepAngle, arc.isWedge(), paint);
 *     }
 *
 *     /** Draws SkRRect bounded by SkRect rect, with corner radii (rx, ry) using clip,
 *         SkMatrix, and SkPaint paint.
 *
 *         In paint: SkPaint::Style determines if SkRRect is stroked or filled;
 *         if stroked, SkPaint stroke width describes the line thickness.
 *         If rx or ry are less than zero, they are treated as if they are zero.
 *         If rx plus ry exceeds rect width or rect height, radii are scaled down to fit.
 *         If rx and ry are zero, SkRRect is drawn as SkRect and if stroked is affected by
 *         SkPaint::Join.
 *
 *         @param rect   SkRect bounds of SkRRect to draw
 *         @param rx     axis length on x-axis of oval describing rounded corners
 *         @param ry     axis length on y-axis of oval describing rounded corners
 *         @param paint  stroke, blend, color, and so on, used to draw
 *
 *         example: https://fiddle.skia.org/c/@Canvas_drawRoundRect
 *     */
 *     void drawRoundRect(const SkRect& rect, SkScalar rx, SkScalar ry, const SkPaint& paint);
 *
 *     /** Draws SkPath path using clip, SkMatrix, and SkPaint paint.
 *         SkPath contains an array of path contour, each of which may be open or closed.
 *
 *         In paint: SkPaint::Style determines if SkRRect is stroked or filled:
 *         if filled, SkPath::FillType determines whether path contour describes inside or
 *         outside of fill; if stroked, SkPaint stroke width describes the line thickness,
 *         SkPaint::Cap describes line ends, and SkPaint::Join describes how
 *         corners are drawn.
 *
 *         @param path   SkPath to draw
 *         @param paint  stroke, blend, color, and so on, used to draw
 *
 *         example: https://fiddle.skia.org/c/@Canvas_drawPath
 *     */
 *     void drawPath(const SkPath& path, const SkPaint& paint);
 *
 *     void drawImage(const SkImage* image, SkScalar left, SkScalar top) {
 *         this->drawImage(image, left, top, SkSamplingOptions(), nullptr);
 *     }
 *     void drawImage(const sk_sp<SkImage>& image, SkScalar left, SkScalar top) {
 *         this->drawImage(image.get(), left, top, SkSamplingOptions(), nullptr);
 *     }
 *
 *     /** \enum SkCanvas::SrcRectConstraint
 *         SrcRectConstraint controls the behavior at the edge of source SkRect,
 *         provided to drawImageRect() when there is any filtering. If kStrict is set,
 *         then extra code is used to ensure it never samples outside of the src-rect.
 *         kStrict_SrcRectConstraint disables the use of mipmaps and anisotropic filtering.
 *     */
 *     enum SrcRectConstraint {
 *         kStrict_SrcRectConstraint, //!< sample only inside bounds; slower
 *         kFast_SrcRectConstraint,   //!< sample outside bounds; faster
 *     };
 *
 *     void drawImage(const SkImage*, SkScalar x, SkScalar y, const SkSamplingOptions&,
 *                    const SkPaint* = nullptr);
 *     void drawImage(const sk_sp<SkImage>& image, SkScalar x, SkScalar y,
 *                    const SkSamplingOptions& sampling, const SkPaint* paint = nullptr) {
 *         this->drawImage(image.get(), x, y, sampling, paint);
 *     }
 *     void drawImageRect(const SkImage*, const SkRect& src, const SkRect& dst,
 *                        const SkSamplingOptions&, const SkPaint*, SrcRectConstraint);
 *     void drawImageRect(const SkImage*, const SkRect& dst, const SkSamplingOptions&,
 *                        const SkPaint* = nullptr);
 *     void drawImageRect(const sk_sp<SkImage>& image, const SkRect& src, const SkRect& dst,
 *                        const SkSamplingOptions& sampling, const SkPaint* paint,
 *                        SrcRectConstraint constraint) {
 *         this->drawImageRect(image.get(), src, dst, sampling, paint, constraint);
 *     }
 *     void drawImageRect(const sk_sp<SkImage>& image, const SkRect& dst,
 *                        const SkSamplingOptions& sampling, const SkPaint* paint = nullptr) {
 *         this->drawImageRect(image.get(), dst, sampling, paint);
 *     }
 *
 *     /** Draws SkImage image stretched proportionally to fit into SkRect dst.
 *         SkIRect center divides the image into nine sections: four sides, four corners, and
 *         the center. Corners are unmodified or scaled down proportionately if their sides
 *         are larger than dst; center and four sides are scaled to fit remaining space, if any.
 *
 *         Additionally transform draw using clip, SkMatrix, and optional SkPaint paint.
 *
 *         If SkPaint paint is supplied, apply SkColorFilter, alpha, SkImageFilter, and
 *         SkBlendMode. If image is kAlpha_8_SkColorType, apply SkShader.
 *         If paint contains SkMaskFilter, generate mask from image bounds.
 *         Any SkMaskFilter on paint is ignored as is paint anti-aliasing state.
 *
 *         If generated mask extends beyond image bounds, replicate image edge colors, just
 *         as SkShader made from SkImage::makeShader with SkShader::kClamp_TileMode set
 *         replicates the image edge color when it samples outside of its bounds.
 *
 *         @param image   SkImage containing pixels, dimensions, and format
 *         @param center  SkIRect edge of image corners and sides
 *         @param dst     destination SkRect of image to draw to
 *         @param filter  what technique to use when sampling the image
 *         @param paint   SkPaint containing SkBlendMode, SkColorFilter, SkImageFilter,
 *                        and so on; or nullptr
 *     */
 *     void drawImageNine(const SkImage* image, const SkIRect& center, const SkRect& dst,
 *                        SkFilterMode filter, const SkPaint* paint = nullptr);
 *
 *     /** \struct SkCanvas::Lattice
 *         SkCanvas::Lattice divides SkBitmap or SkImage into a rectangular grid.
 *         Grid entries on even columns and even rows are fixed; these entries are
 *         always drawn at their original size if the destination is large enough.
 *         If the destination side is too small to hold the fixed entries, all fixed
 *         entries are proportionately scaled down to fit.
 *         The grid entries not on even columns and rows are scaled to fit the
 *         remaining space, if any.
 *     */
 *     struct Lattice {
 *
 *         /** \enum SkCanvas::Lattice::RectType
 *             Optional setting per rectangular grid entry to make it transparent,
 *             or to fill the grid entry with a color.
 *         */
 *         enum RectType : uint8_t {
 *             kDefault     = 0, //!< draws SkBitmap into lattice rectangle
 *             kTransparent,     //!< skips lattice rectangle by making it transparent
 *             kFixedColor,      //!< draws one of fColors into lattice rectangle
 *         };
 *
 *         const int*      fXDivs;     //!< x-axis values dividing bitmap
 *         const int*      fYDivs;     //!< y-axis values dividing bitmap
 *         const RectType* fRectTypes; //!< array of fill types
 *         int             fXCount;    //!< number of x-coordinates
 *         int             fYCount;    //!< number of y-coordinates
 *         const SkIRect*  fBounds;    //!< source bounds to draw from
 *         const SkColor*  fColors;    //!< array of colors
 *     };
 *
 *     /** Draws SkImage image stretched proportionally to fit into SkRect dst.
 *
 *         SkCanvas::Lattice lattice divides image into a rectangular grid.
 *         Each intersection of an even-numbered row and column is fixed;
 *         fixed lattice elements never scale larger than their initial
 *         size and shrink proportionately when all fixed elements exceed the bitmap
 *         dimension. All other grid elements scale to fill the available space, if any.
 *
 *         Additionally transform draw using clip, SkMatrix, and optional SkPaint paint.
 *
 *         If SkPaint paint is supplied, apply SkColorFilter, alpha, SkImageFilter, and
 *         SkBlendMode. If image is kAlpha_8_SkColorType, apply SkShader.
 *         If paint contains SkMaskFilter, generate mask from image bounds.
 *         Any SkMaskFilter on paint is ignored as is paint anti-aliasing state.
 *
 *         If generated mask extends beyond bitmap bounds, replicate bitmap edge colors,
 *         just as SkShader made from SkShader::MakeBitmapShader with
 *         SkShader::kClamp_TileMode set replicates the bitmap edge color when it samples
 *         outside of its bounds.
 *
 *         @param image    SkImage containing pixels, dimensions, and format
 *         @param lattice  division of bitmap into fixed and variable rectangles
 *         @param dst      destination SkRect of image to draw to
 *         @param filter   what technique to use when sampling the image
 *         @param paint    SkPaint containing SkBlendMode, SkColorFilter, SkImageFilter,
 *                         and so on; or nullptr
 *     */
 *     void drawImageLattice(const SkImage* image, const Lattice& lattice, const SkRect& dst,
 *                           SkFilterMode filter, const SkPaint* paint = nullptr);
 *     void drawImageLattice(const SkImage* image, const Lattice& lattice, const SkRect& dst) {
 *         this->drawImageLattice(image, lattice, dst, SkFilterMode::kNearest, nullptr);
 *     }
 *
 *     /**
 *      * Experimental. Controls anti-aliasing of each edge of images in an image-set.
 *      */
 *     enum QuadAAFlags : unsigned {
 *         kLeft_QuadAAFlag    = 0b0001,
 *         kTop_QuadAAFlag     = 0b0010,
 *         kRight_QuadAAFlag   = 0b0100,
 *         kBottom_QuadAAFlag  = 0b1000,
 *
 *         kNone_QuadAAFlags   = 0b0000,
 *         kAll_QuadAAFlags    = 0b1111,
 *     };
 *
 *     /** This is used by the experimental API below. */
 *     struct SK_API ImageSetEntry {
 *         ImageSetEntry(sk_sp<const SkImage> image, const SkRect& srcRect, const SkRect& dstRect,
 *                       int matrixIndex, float alpha, unsigned aaFlags, bool hasClip);
 *
 *         ImageSetEntry(sk_sp<const SkImage> image, const SkRect& srcRect, const SkRect& dstRect,
 *                       float alpha, unsigned aaFlags);
 *
 *         ImageSetEntry();
 *         ~ImageSetEntry();
 *         ImageSetEntry(const ImageSetEntry&);
 *         ImageSetEntry& operator=(const ImageSetEntry&);
 *
 *         sk_sp<const SkImage> fImage;
 *         SkRect fSrcRect;
 *         SkRect fDstRect;
 *         int fMatrixIndex = -1; // Index into the preViewMatrices arg, or < 0
 *         float fAlpha = 1.f;
 *         unsigned fAAFlags = kNone_QuadAAFlags; // QuadAAFlags
 *         bool fHasClip = false; // True to use next 4 points in dstClip arg as quad
 *     };
 *
 *     /**
 *      * This is an experimental API for the SkiaRenderer Chromium project, and its API will surely
 *      * evolve if it is not removed outright.
 *      *
 *      * This behaves very similarly to drawRect() combined with a clipPath() formed by clip
 *      * quadrilateral. 'rect' and 'clip' are in the same coordinate space. If 'clip' is null, then it
 *      * is as if the rectangle was not clipped (or, alternatively, clipped to itself). If not null,
 *      * then it must provide 4 points.
 *      *
 *      * In addition to combining the draw and clipping into one operation, this function adds the
 *      * additional capability of controlling each of the rectangle's edges anti-aliasing
 *      * independently.  The edges of the clip will respect the per-edge AA flags. It is required that
 *      * 'clip' be contained inside 'rect'. In terms of mapping to edge labels, the 'clip' points
 *      * should be ordered top-left, top-right, bottom-right, bottom-left so that the edge between [0]
 *      * and [1] is "top", [1] and [2] is "right", [2] and [3] is "bottom", and [3] and [0] is "left".
 *      * This ordering matches SkRect::toQuad().
 *      *
 *      * This API only draws solid color, filled rectangles so it does not accept a full SkPaint.
 *      */
 *     void experimental_DrawEdgeAAQuad(const SkRect& rect, const SkPoint clip[4], QuadAAFlags aaFlags,
 *                                      const SkColor4f& color, SkBlendMode mode);
 *     void experimental_DrawEdgeAAQuad(const SkRect& rect, const SkPoint clip[4], QuadAAFlags aaFlags,
 *                                      SkColor color, SkBlendMode mode) {
 *         this->experimental_DrawEdgeAAQuad(rect, clip, aaFlags, SkColor4f::FromColor(color), mode);
 *     }
 *
 *     /**
 *      * This is an bulk variant of experimental_DrawEdgeAAQuad() that renders 'cnt' textured quads.
 *      * For each entry, 'fDstRect' is rendered with its clip (determined by entry's 'fHasClip' and
 *      * the current index in 'dstClip'). The entry's fImage is applied to the destination rectangle
 *      * by sampling from 'fSrcRect' sub-image.  The corners of 'fSrcRect' map to the corners of
 *      * 'fDstRect', just like in drawImageRect(), and they will be properly interpolated when
 *      * applying a clip.
 *      *
 *      * Like experimental_DrawEdgeAAQuad(), each entry can specify edge AA flags that apply to both
 *      * the destination rect and its clip.
 *      *
 *      * If provided, the 'dstClips' array must have length equal 4 * the number of entries with
 *      * fHasClip true. If 'dstClips' is null, every entry must have 'fHasClip' set to false. The
 *      * destination clip coordinates will be read consecutively with the image set entries, advancing
 *      * by 4 points every time an entry with fHasClip is passed.
 *      *
 *      * This entry point supports per-entry manipulations to the canvas's current matrix. If an
 *      * entry provides 'fMatrixIndex' >= 0, it will be drawn as if the canvas's CTM was
 *      * canvas->getTotalMatrix() * preViewMatrices[fMatrixIndex]. If 'fMatrixIndex' is less than 0,
 *      * the pre-view matrix transform is implicitly the identity, so it will be drawn using just the
 *      * current canvas matrix. The pre-view matrix modifies the canvas's view matrix, it does not
 *      * affect the local coordinates of each entry.
 *      *
 *      * An optional paint may be provided, which supports the same subset of features usable with
 *      * drawImageRect (i.e. assumed to be filled and no path effects). When a paint is provided, the
 *      * image set is drawn as if each image used the applied paint independently, so each is affected
 *      * by the image, color, and/or mask filter.
 *      */
 *     void experimental_DrawEdgeAAImageSet(const ImageSetEntry imageSet[], int cnt,
 *                                          const SkPoint dstClips[], const SkMatrix preViewMatrices[],
 *                                          const SkSamplingOptions&, const SkPaint* paint = nullptr,
 *                                          SrcRectConstraint constraint = kStrict_SrcRectConstraint);
 *
 *     /** Draws text, with origin at (x, y), using clip, SkMatrix, SkFont font,
 *         and SkPaint paint.
 *
 *         When encoding is SkTextEncoding::kUTF8, SkTextEncoding::kUTF16, or
 *         SkTextEncoding::kUTF32, this function uses the default
 *         character-to-glyph mapping from the SkTypeface in font.  It does not
 *         perform typeface fallback for characters not found in the SkTypeface.
 *         It does not perform kerning or other complex shaping; glyphs are
 *         positioned based on their default advances.
 *
 *         Text meaning depends on SkTextEncoding.
 *
 *         Text size is affected by SkMatrix and SkFont text size. Default text
 *         size is 12 point.
 *
 *         All elements of paint: SkPathEffect, SkMaskFilter, SkShader,
 *         SkColorFilter, and SkImageFilter; apply to text. By
 *         default, draws filled black glyphs.
 *
 *         @param text        character code points or glyphs drawn
 *         @param byteLength  byte length of text array
 *         @param encoding    text encoding used in the text array
 *         @param x           start of text on x-axis
 *         @param y           start of text on y-axis
 *         @param font        typeface, text size and so, used to describe the text
 *         @param paint       blend, color, and so on, used to draw
 *     */
 *     void drawSimpleText(const void* text, size_t byteLength, SkTextEncoding encoding,
 *                         SkScalar x, SkScalar y, const SkFont& font, const SkPaint& paint);
 *
 *     /** Draws null terminated string, with origin at (x, y), using clip, SkMatrix,
 *         SkFont font, and SkPaint paint.
 *
 *         This function uses the default character-to-glyph mapping from the
 *         SkTypeface in font.  It does not perform typeface fallback for
 *         characters not found in the SkTypeface.  It does not perform kerning;
 *         glyphs are positioned based on their default advances.
 *
 *         String str is encoded as UTF-8.
 *
 *         Text size is affected by SkMatrix and font text size. Default text
 *         size is 12 point.
 *
 *         All elements of paint: SkPathEffect, SkMaskFilter, SkShader,
 *         SkColorFilter, and SkImageFilter; apply to text. By
 *         default, draws filled black glyphs.
 *
 *         @param str     character code points drawn,
 *                        ending with a char value of zero
 *         @param x       start of string on x-axis
 *         @param y       start of string on y-axis
 *         @param font    typeface, text size and so, used to describe the text
 *         @param paint   blend, color, and so on, used to draw
 *     */
 *     void drawString(const char str[], SkScalar x, SkScalar y, const SkFont& font,
 *                     const SkPaint& paint) {
 *         this->drawSimpleText(str, strlen(str), SkTextEncoding::kUTF8, x, y, font, paint);
 *     }
 *
 *     /** Draws SkString, with origin at (x, y), using clip, SkMatrix, SkFont font,
 *         and SkPaint paint.
 *
 *         This function uses the default character-to-glyph mapping from the
 *         SkTypeface in font.  It does not perform typeface fallback for
 *         characters not found in the SkTypeface.  It does not perform kerning;
 *         glyphs are positioned based on their default advances.
 *
 *         SkString str is encoded as UTF-8.
 *
 *         Text size is affected by SkMatrix and SkFont text size. Default text
 *         size is 12 point.
 *
 *         All elements of paint: SkPathEffect, SkMaskFilter, SkShader,
 *         SkColorFilter, and SkImageFilter; apply to text. By
 *         default, draws filled black glyphs.
 *
 *         @param str     character code points drawn,
 *                        ending with a char value of zero
 *         @param x       start of string on x-axis
 *         @param y       start of string on y-axis
 *         @param font    typeface, text size and so, used to describe the text
 *         @param paint   blend, color, and so on, used to draw
 *     */
 *     void drawString(const SkString& str, SkScalar x, SkScalar y, const SkFont& font,
 *                     const SkPaint& paint) {
 *         this->drawSimpleText(str.c_str(), str.size(), SkTextEncoding::kUTF8, x, y, font, paint);
 *     }
 *
 *     /** Draws count glyphs, at positions relative to origin styled with font and paint with
 *         supporting utf8 and cluster information.
 *
 *        This function draw glyphs at the given positions relative to the given origin.
 *        It does not perform typeface fallback for glyphs not found in the SkTypeface in font.
 *
 *        The drawing obeys the current transform matrix and clipping.
 *
 *        All elements of paint: SkPathEffect, SkMaskFilter, SkShader,
 *        SkColorFilter, and SkImageFilter; apply to text. By
 *        default, draws filled black glyphs.
 *
 *        @param glyphs          the span of glyphIDs to draw
 *        @param positions       where to draw each glyph relative to origin
 *        @param clusters        cluster information
 *        @param utf8text        utf8text supporting information for the glyphs
 *        @param origin          the origin of all the positions
 *        @param font            typeface, text size and so, used to describe the text
 *        @param paint           blend, color, and so on, used to draw
 *     */
 *     void drawGlyphs(SkSpan<const SkGlyphID> glyphs, SkSpan<const SkPoint> positions,
 *                     SkSpan<const uint32_t> clusters, SkSpan<const char> utf8text,
 *                     SkPoint origin, const SkFont& font, const SkPaint& paint);
 * #ifdef SK_SUPPORT_UNSPANNED_APIS
 *     void drawGlyphs(int count, const SkGlyphID glyphs[], const SkPoint positions[],
 *                     const uint32_t clusters[], int textByteCount, const char utf8text[],
 *                     SkPoint origin, const SkFont& font, const SkPaint& paint) {
 *         this->drawGlyphs({glyphs,    count},
 *                          {positions, count},
 *                          {clusters,  count},
 *                          {utf8text,  textByteCount},
 *                          origin, font, paint);
 *     }
 * #endif
 *
 *     /** Draws count glyphs, at positions relative to origin styled with font and paint.
 *
 *         This function draw glyphs at the given positions relative to the given origin.
 *         It does not perform typeface fallback for glyphs not found in the SkTypeface in font.
 *
 *         The drawing obeys the current transform matrix and clipping.
 *
 *         All elements of paint: SkPathEffect, SkMaskFilter, SkShader,
 *         SkColorFilter, and SkImageFilter; apply to text. By
 *         default, draws filled black glyphs.
 *
 *         @param count       number of glyphs to draw
 *         @param glyphs      the array of glyphIDs to draw
 *         @param positions   where to draw each glyph relative to origin
 *         @param origin      the origin of all the positions
 *         @param font        typeface, text size and so, used to describe the text
 *         @param paint       blend, color, and so on, used to draw
 *     */
 *     void drawGlyphs(SkSpan<const SkGlyphID> glyphs, SkSpan<const SkPoint> positions,
 *                     SkPoint origin, const SkFont& font, const SkPaint& paint);
 * #ifdef SK_SUPPORT_UNSPANNED_APIS
 *     void drawGlyphs(int count, const SkGlyphID glyphs[], const SkPoint positions[],
 *                     SkPoint origin, const SkFont& font, const SkPaint& paint) {
 *         this->drawGlyphs({glyphs, count}, {positions, count}, origin, font, paint);
 *     }
 * #endif
 *
 *     /** Draws count glyphs, at positions relative to origin styled with font and paint.
 *
 *         This function draw glyphs using the given scaling and rotations. They are positioned
 *         relative to the given origin. It does not perform typeface fallback for glyphs not found
 *         in the SkTypeface in font.
 *
 *         The drawing obeys the current transform matrix and clipping.
 *
 *         All elements of paint: SkPathEffect, SkMaskFilter, SkShader,
 *         SkColorFilter, and SkImageFilter; apply to text. By
 *         default, draws filled black glyphs.
 *
 *         @param count    number of glyphs to draw
 *         @param glyphs   the array of glyphIDs to draw
 *         @param xforms   where to draw and orient each glyph
 *         @param origin   the origin of all the positions
 *         @param font     typeface, text size and so, used to describe the text
 *         @param paint    blend, color, and so on, used to draw
 *     */
 *     void drawGlyphsRSXform(SkSpan<const SkGlyphID> glyphs, SkSpan<const SkRSXform> xforms,
 *                            SkPoint origin, const SkFont& font, const SkPaint& paint);
 * #ifdef SK_SUPPORT_UNSPANNED_APIS
 *     void drawGlyphs(int count, const SkGlyphID glyphs[], const SkRSXform xforms[],
 *                     SkPoint origin, const SkFont& font, const SkPaint& paint) {
 *         this->drawGlyphsRSXform({glyphs, count}, {xforms, count}, origin, font, paint);
 *     }
 * #endif
 *
 *     /** Draws SkTextBlob blob at (x, y), using clip, SkMatrix, and SkPaint paint.
 *
 *         blob contains glyphs, their positions, and paint attributes specific to text:
 *         SkTypeface, SkPaint text size, SkPaint text scale x,
 *         SkPaint text skew x, SkPaint::Align, SkPaint::Hinting, anti-alias, SkPaint fake bold,
 *         SkPaint font embedded bitmaps, SkPaint full hinting spacing, LCD text, SkPaint linear text,
 *         and SkPaint subpixel text.
 *
 *         SkTextEncoding must be set to SkTextEncoding::kGlyphID.
 *
 *         Elements of paint: anti-alias, SkBlendMode, color including alpha,
 *         SkColorFilter, SkPaint dither, SkMaskFilter, SkPathEffect, SkShader, and
 *         SkPaint::Style; apply to blob. If SkPaint contains SkPaint::kStroke_Style:
 *         SkPaint miter limit, SkPaint::Cap, SkPaint::Join, and SkPaint stroke width;
 *         apply to SkPath created from blob.
 *
 *         @param blob   glyphs, positions, and their paints' text size, typeface, and so on
 *         @param x      horizontal offset applied to blob
 *         @param y      vertical offset applied to blob
 *         @param paint  blend, color, stroking, and so on, used to draw
 *
 *         example: https://fiddle.skia.org/c/@Canvas_drawTextBlob
 *     */
 *     void drawTextBlob(const SkTextBlob* blob, SkScalar x, SkScalar y, const SkPaint& paint);
 *
 *     /** Draws SkTextBlob blob at (x, y), using clip, SkMatrix, and SkPaint paint.
 *
 *         blob contains glyphs, their positions, and paint attributes specific to text:
 *         SkTypeface, SkPaint text size, SkPaint text scale x,
 *         SkPaint text skew x, SkPaint::Align, SkPaint::Hinting, anti-alias, SkPaint fake bold,
 *         SkPaint font embedded bitmaps, SkPaint full hinting spacing, LCD text, SkPaint linear text,
 *         and SkPaint subpixel text.
 *
 *         SkTextEncoding must be set to SkTextEncoding::kGlyphID.
 *
 *         Elements of paint: SkPathEffect, SkMaskFilter, SkShader, SkColorFilter,
 *         and SkImageFilter; apply to blob.
 *
 *         @param blob   glyphs, positions, and their paints' text size, typeface, and so on
 *         @param x      horizontal offset applied to blob
 *         @param y      vertical offset applied to blob
 *         @param paint  blend, color, stroking, and so on, used to draw
 *     */
 *     void drawTextBlob(const sk_sp<SkTextBlob>& blob, SkScalar x, SkScalar y, const SkPaint& paint) {
 *         this->drawTextBlob(blob.get(), x, y, paint);
 *     }
 *
 *     /** Draws SkPicture picture, using clip and SkMatrix.
 *         Clip and SkMatrix are unchanged by picture contents, as if
 *         save() was called before and restore() was called after drawPicture().
 *
 *         SkPicture records a series of draw commands for later playback.
 *
 *         @param picture  recorded drawing commands to play
 *     */
 *     void drawPicture(const SkPicture* picture) {
 *         this->drawPicture(picture, nullptr, nullptr);
 *     }
 *
 *     /** Draws SkPicture picture, using clip and SkMatrix.
 *         Clip and SkMatrix are unchanged by picture contents, as if
 *         save() was called before and restore() was called after drawPicture().
 *
 *         SkPicture records a series of draw commands for later playback.
 *
 *         @param picture  recorded drawing commands to play
 *     */
 *     void drawPicture(const sk_sp<SkPicture>& picture) {
 *         this->drawPicture(picture.get());
 *     }
 *
 *     /** Draws SkPicture picture, using clip and SkMatrix; transforming picture with
 *         SkMatrix matrix, if provided; and use SkPaint paint alpha, SkColorFilter,
 *         SkImageFilter, and SkBlendMode, if provided.
 *
 *         If paint is non-null, then the picture is always drawn into a temporary layer before
 *         actually landing on the canvas. Note that drawing into a layer can also change its
 *         appearance if there are any non-associative blendModes inside any of the pictures elements.
 *
 *         @param picture  recorded drawing commands to play
 *         @param matrix   SkMatrix to rotate, scale, translate, and so on; may be nullptr
 *         @param paint    SkPaint to apply transparency, filtering, and so on; may be nullptr
 *
 *         example: https://fiddle.skia.org/c/@Canvas_drawPicture_3
 *     */
 *     void drawPicture(const SkPicture* picture, const SkMatrix* matrix, const SkPaint* paint);
 *
 *     /** Draws SkPicture picture, using clip and SkMatrix; transforming picture with
 *         SkMatrix matrix, if provided; and use SkPaint paint alpha, SkColorFilter,
 *         SkImageFilter, and SkBlendMode, if provided.
 *
 *         If paint is non-null, then the picture is always drawn into a temporary layer before
 *         actually landing on the canvas. Note that drawing into a layer can also change its
 *         appearance if there are any non-associative blendModes inside any of the pictures elements.
 *
 *         @param picture  recorded drawing commands to play
 *         @param matrix   SkMatrix to rotate, scale, translate, and so on; may be nullptr
 *         @param paint    SkPaint to apply transparency, filtering, and so on; may be nullptr
 *     */
 *     void drawPicture(const sk_sp<SkPicture>& picture, const SkMatrix* matrix,
 *                      const SkPaint* paint) {
 *         this->drawPicture(picture.get(), matrix, paint);
 *     }
 *
 *     /** Draws SkVertices vertices, a triangle mesh, using clip and SkMatrix.
 *         If paint contains an SkShader and vertices does not contain texCoords, the shader
 *         is mapped using the vertices' positions.
 *
 *         SkBlendMode is ignored if SkVertices does not have colors. Otherwise, it combines
 *            - the SkShader if SkPaint contains SkShader
 *            - or the opaque SkPaint color if SkPaint does not contain SkShader
 *         as the src of the blend and the interpolated vertex colors as the dst.
 *
 *         SkMaskFilter, SkPathEffect, and antialiasing on SkPaint are ignored.
 *
 *         @param vertices  triangle mesh to draw
 *         @param mode      combines vertices' colors with SkShader if present or SkPaint opaque color
 *                          if not. Ignored if the vertices do not contain color.
 *         @param paint     specifies the SkShader, used as SkVertices texture, and SkColorFilter.
 *
 *         example: https://fiddle.skia.org/c/@Canvas_drawVertices
 *     */
 *     void drawVertices(const SkVertices* vertices, SkBlendMode mode, const SkPaint& paint);
 *
 *     /** Draws SkVertices vertices, a triangle mesh, using clip and SkMatrix.
 *         If paint contains an SkShader and vertices does not contain texCoords, the shader
 *         is mapped using the vertices' positions.
 *
 *         SkBlendMode is ignored if SkVertices does not have colors. Otherwise, it combines
 *            - the SkShader if SkPaint contains SkShader
 *            - or the opaque SkPaint color if SkPaint does not contain SkShader
 *         as the src of the blend and the interpolated vertex colors as the dst.
 *
 *         SkMaskFilter, SkPathEffect, and antialiasing on SkPaint are ignored.
 *
 *         @param vertices  triangle mesh to draw
 *         @param mode      combines vertices' colors with SkShader if present or SkPaint opaque color
 *                          if not. Ignored if the vertices do not contain color.
 *         @param paint     specifies the SkShader, used as SkVertices texture, may be nullptr
 *
 *         example: https://fiddle.skia.org/c/@Canvas_drawVertices_2
 *     */
 *     void drawVertices(const sk_sp<SkVertices>& vertices, SkBlendMode mode, const SkPaint& paint);
 *
 *     /**
 *         Experimental, under active development, and subject to change without notice.
 *
 *         Draws a mesh using a user-defined specification (see SkMeshSpecification). Requires
 *         a GPU backend or SkSL to be compiled in.
 *
 *         SkBlender is ignored if SkMesh's specification does not output fragment shader color.
 *         Otherwise, it combines
 *             - the SkShader if SkPaint contains SkShader
 *             - or the opaque SkPaint color if SkPaint does not contain SkShader
 *         as the src of the blend and the mesh's fragment color as the dst.
 *
 *         SkMaskFilter, SkPathEffect, and antialiasing on SkPaint are ignored.
 *
 *         @param mesh      the mesh vertices and compatible specification.
 *         @param blender   combines vertices colors with SkShader if present or SkPaint opaque color
 *                          if not. Ignored if the custom mesh does not output color. Defaults to
 *                          SkBlendMode::kModulate if nullptr.
 *         @param paint     specifies the SkShader, used as SkVertices texture, may be nullptr
 *     */
 *     void drawMesh(const SkMesh& mesh, sk_sp<SkBlender> blender, const SkPaint& paint);
 *
 *     /** Draws a Coons patch: the interpolation of four cubics with shared corners,
 *         associating a color, and optionally a texture SkPoint, with each corner.
 *
 *         SkPoint array cubics specifies four SkPath cubic starting at the top-left corner,
 *         in clockwise order, sharing every fourth point. The last SkPath cubic ends at the
 *         first point.
 *
 *         Color array color associates colors with corners in top-left, top-right,
 *         bottom-right, bottom-left order.
 *
 *         If paint contains SkShader, SkPoint array texCoords maps SkShader as texture to
 *         corners in top-left, top-right, bottom-right, bottom-left order. If texCoords is
 *         nullptr, SkShader is mapped using positions (derived from cubics).
 *
 *         SkBlendMode is ignored if colors is null. Otherwise, it combines
 *             - the SkShader if SkPaint contains SkShader
 *             - or the opaque SkPaint color if SkPaint does not contain SkShader
 *         as the src of the blend and the interpolated patch colors as the dst.
 *
 *         SkMaskFilter, SkPathEffect, and antialiasing on SkPaint are ignored.
 *
 *         @param cubics     SkPath cubic array, sharing common points
 *         @param colors     color array, one for each corner
 *         @param texCoords  SkPoint array of texture coordinates, mapping SkShader to corners;
 *                           may be nullptr
 *         @param mode       combines patch's colors with SkShader if present or SkPaint opaque color
 *                           if not. Ignored if colors is null.
 *         @param paint      SkShader, SkColorFilter, SkBlendMode, used to draw
 *     */
 *     void drawPatch(const SkPoint cubics[12], const SkColor colors[4],
 *                    const SkPoint texCoords[4], SkBlendMode mode, const SkPaint& paint);
 *
 *     /** Draws a set of sprites from atlas, using clip, SkMatrix, and optional SkPaint paint.
 *         paint uses anti-alias, alpha, SkColorFilter, SkImageFilter, and SkBlendMode
 *         to draw, if present. For each entry in the array, SkRect tex locates sprite in
 *         atlas, and SkRSXform xform transforms it into destination space.
 *
 *         SkMaskFilter and SkPathEffect on paint are ignored.
 *
 *         For non-empty spans, the number of draws will be the min of
 *         xform.size(), tex.size(), and (if not empty) colors.size().
 *
 *         Optional colors are applied for each sprite using SkBlendMode mode, treating
 *         sprite as source and colors as destination.
 *         Optional cullRect is a conservative bounds of all transformed sprites.
 *         If cullRect is outside of clip, canvas can skip drawing.
 *
 *         If atlas is nullptr, this draws nothing.
 *
 *         @param atlas     SkImage containing sprites
 *         @param xform     SkRSXform mappings for sprites in atlas
 *         @param tex       SkRect locations of sprites in atlas
 *         @param colors    one per sprite, blended with sprite using SkBlendMode; may be nullptr
 *         @param mode      SkBlendMode combining colors and sprites
 *         @param sampling  SkSamplingOptions used when sampling from the atlas image
 *         @param cullRect  bounds of transformed sprites for efficient clipping; may be nullptr
 *         @param paint     SkColorFilter, SkImageFilter, SkBlendMode, and so on; may be nullptr
 *     */
 *     void drawAtlas(const SkImage* atlas, SkSpan<const SkRSXform> xform,
 *                    SkSpan<const SkRect> tex, SkSpan<const SkColor> colors, SkBlendMode mode,
 *                    const SkSamplingOptions& sampling, const SkRect* cullRect, const SkPaint* paint);
 * #ifdef SK_SUPPORT_UNSPANNED_APIS
 *     void drawAtlas(const SkImage* atlas, const SkRSXform xform[], const SkRect tex[],
 *                    const SkColor colors[], int count, SkBlendMode mode,
 *                    const SkSamplingOptions& samp, const SkRect* cullRect, const SkPaint* paint) {
 *         this->drawAtlas(atlas,
 *                         {xform, count},
 *                         {tex, tex ? count : 0},
 *                         {colors, colors ? count : 0},
 *                         mode, samp, cullRect, paint);
 *     }
 * #endif
 *
 *     /** Draws SkDrawable drawable using clip and SkMatrix, concatenated with
 *         optional matrix.
 *
 *         If SkCanvas has an asynchronous implementation, as is the case
 *         when it is recording into SkPicture, then drawable will be referenced,
 *         so that SkDrawable::draw() can be called when the operation is finalized. To force
 *         immediate drawing, call SkDrawable::draw() instead.
 *
 *         @param drawable  custom struct encapsulating drawing commands
 *         @param matrix    transformation applied to drawing; may be nullptr
 *
 *         example: https://fiddle.skia.org/c/@Canvas_drawDrawable
 *     */
 *     void drawDrawable(SkDrawable* drawable, const SkMatrix* matrix = nullptr);
 *
 *     /** Draws SkDrawable drawable using clip and SkMatrix, offset by (x, y).
 *
 *         If SkCanvas has an asynchronous implementation, as is the case
 *         when it is recording into SkPicture, then drawable will be referenced,
 *         so that SkDrawable::draw() can be called when the operation is finalized. To force
 *         immediate drawing, call SkDrawable::draw() instead.
 *
 *         @param drawable  custom struct encapsulating drawing commands
 *         @param x         offset into SkCanvas writable pixels on x-axis
 *         @param y         offset into SkCanvas writable pixels on y-axis
 *
 *         example: https://fiddle.skia.org/c/@Canvas_drawDrawable_2
 *     */
 *     void drawDrawable(SkDrawable* drawable, SkScalar x, SkScalar y);
 *
 *     /** Associates SkRect on SkCanvas with an annotation; a key-value pair, where the key is
 *         a null-terminated UTF-8 string, and optional value is stored as SkData.
 *
 *         Only some canvas implementations, such as recording to SkPicture, or drawing to
 *         document PDF, use annotations.
 *
 *         @param rect   SkRect extent of canvas to annotate
 *         @param key    string used for lookup
 *         @param value  data holding value stored in annotation
 *
 *         example: https://fiddle.skia.org/c/@Canvas_drawAnnotation_2
 *     */
 *     void drawAnnotation(const SkRect& rect, const char key[], SkData* value);
 *
 *     /** Associates SkRect on SkCanvas when an annotation; a key-value pair, where the key is
 *         a null-terminated UTF-8 string, and optional value is stored as SkData.
 *
 *         Only some canvas implementations, such as recording to SkPicture, or drawing to
 *         document PDF, use annotations.
 *
 *         @param rect   SkRect extent of canvas to annotate
 *         @param key    string used for lookup
 *         @param value  data holding value stored in annotation
 *     */
 *     void drawAnnotation(const SkRect& rect, const char key[], const sk_sp<SkData>& value) {
 *         this->drawAnnotation(rect, key, value.get());
 *     }
 *
 *     /** Returns true if clip is empty; that is, nothing will draw.
 *
 *         May do work when called; it should not be called
 *         more often than needed. However, once called, subsequent calls perform no
 *         work until clip changes.
 *
 *         @return  true if clip is empty
 *
 *         example: https://fiddle.skia.org/c/@Canvas_isClipEmpty
 *     */
 *     virtual bool isClipEmpty() const;
 *
 *     /** Returns true if clip is SkRect and not empty.
 *         Returns false if the clip is empty, or if it is not SkRect.
 *
 *         @return  true if clip is SkRect and not empty
 *
 *         example: https://fiddle.skia.org/c/@Canvas_isClipRect
 *     */
 *     virtual bool isClipRect() const;
 *
 *     /** Returns the current transform from local coordinates to the 'device', which for most
 *      *  purposes means pixels.
 *      *
 *      *  @return transformation from local coordinates to device / pixels.
 *      */
 *     SkM44 getLocalToDevice() const;
 *
 *     /**
 *      *  Throws away the 3rd row and column in the matrix, so be warned.
 *      */
 *     SkMatrix getLocalToDeviceAs3x3() const {
 *         return this->getLocalToDevice().asM33();
 *     }
 *
 * #ifdef SK_SUPPORT_LEGACY_GETTOTALMATRIX
 *     /** DEPRECATED
 *      *  Legacy version of getLocalToDevice(), which strips away any Z information, and
 *      *  just returns a 3x3 version.
 *      *
 *      *  @return 3x3 version of getLocalToDevice()
 *      *
 *      *  example: https://fiddle.skia.org/c/@Canvas_getTotalMatrix
 *      *  example: https://fiddle.skia.org/c/@Clip
 *      */
 *     SkMatrix getTotalMatrix() const;
 * #endif
 *
 *     ///////////////////////////////////////////////////////////////////////////
 *
 *     /**
 *      *  Returns the global clip as a region. If the clip contains AA, then only the bounds
 *      *  of the clip may be returned.
 *      */
 *     void temporary_internal_getRgnClip(SkRegion* region);
 *
 *     void private_draw_shadow_rec(const SkPath&, const SkDrawShadowRec&);
 *
 *
 * protected:
 *     // default impl defers to getDevice()->newSurface(info)
 *     virtual sk_sp<SkSurface> onNewSurface(const SkImageInfo& info, const SkSurfaceProps& props);
 *
 *     // default impl defers to its device
 *     virtual bool onPeekPixels(SkPixmap* pixmap);
 *     virtual bool onAccessTopLayerPixels(SkPixmap* pixmap);
 *     virtual SkImageInfo onImageInfo() const;
 *     virtual bool onGetProps(SkSurfaceProps* props, bool top) const;
 *
 *     // Subclass save/restore notifiers.
 *     // Overriders should call the corresponding INHERITED method up the inheritance chain.
 *     // getSaveLayerStrategy()'s return value may suppress full layer allocation.
 *     enum SaveLayerStrategy {
 *         kFullLayer_SaveLayerStrategy,
 *         kNoLayer_SaveLayerStrategy,
 *     };
 *
 *     virtual void willSave() {}
 *     // Overriders should call the corresponding INHERITED method up the inheritance chain.
 *     virtual SaveLayerStrategy getSaveLayerStrategy(const SaveLayerRec& ) {
 *         return kFullLayer_SaveLayerStrategy;
 *     }
 *
 *     // returns true if we should actually perform the saveBehind, or false if we should just save.
 *     virtual bool onDoSaveBehind(const SkRect*) { return true; }
 *     virtual void willRestore() {}
 *     virtual void didRestore() {}
 *
 *     virtual void didConcat44(const SkM44&) {}
 *     virtual void didSetM44(const SkM44&) {}
 *     virtual void didTranslate(SkScalar, SkScalar) {}
 *     virtual void didScale(SkScalar, SkScalar) {}
 *
 *     // NOTE: If you are adding a new onDraw virtual to SkCanvas, PLEASE add an override to
 *     // SkCanvasVirtualEnforcer (in SkCanvasVirtualEnforcer.h). This ensures that subclasses using
 *     // that mechanism  will be required to implement the new function.
 *     virtual void onDrawPaint(const SkPaint& paint);
 *     virtual void onDrawBehind(const SkPaint& paint);
 *     virtual void onDrawRect(const SkRect& rect, const SkPaint& paint);
 *     virtual void onDrawRRect(const SkRRect& rrect, const SkPaint& paint);
 *     virtual void onDrawDRRect(const SkRRect& outer, const SkRRect& inner, const SkPaint& paint);
 *     virtual void onDrawOval(const SkRect& rect, const SkPaint& paint);
 *     virtual void onDrawArc(const SkRect& rect, SkScalar startAngle, SkScalar sweepAngle,
 *                            bool useCenter, const SkPaint& paint);
 *     virtual void onDrawPath(const SkPath& path, const SkPaint& paint);
 *     virtual void onDrawRegion(const SkRegion& region, const SkPaint& paint);
 *
 *     virtual void onDrawTextBlob(const SkTextBlob* blob, SkScalar x, SkScalar y,
 *                                 const SkPaint& paint);
 *
 *     virtual void onDrawGlyphRunList(const sktext::GlyphRunList& glyphRunList, const SkPaint& paint);
 *
 *     virtual void onDrawPatch(const SkPoint cubics[12], const SkColor colors[4],
 *                            const SkPoint texCoords[4], SkBlendMode mode, const SkPaint& paint);
 *     virtual void onDrawPoints(PointMode mode, size_t count, const SkPoint pts[],
 *                               const SkPaint& paint);
 *
 *     virtual void onDrawImage2(const SkImage*, SkScalar dx, SkScalar dy, const SkSamplingOptions&,
 *                               const SkPaint*);
 *     virtual void onDrawImageRect2(const SkImage*, const SkRect& src, const SkRect& dst,
 *                                   const SkSamplingOptions&, const SkPaint*, SrcRectConstraint);
 *     virtual void onDrawImageLattice2(const SkImage*, const Lattice&, const SkRect& dst,
 *                                      SkFilterMode, const SkPaint*);
 *     virtual void onDrawAtlas2(const SkImage*, const SkRSXform[], const SkRect src[],
 *                               const SkColor[], int count, SkBlendMode, const SkSamplingOptions&,
 *                               const SkRect* cull, const SkPaint*);
 *     virtual void onDrawEdgeAAImageSet2(const ImageSetEntry imageSet[], int count,
 *                                        const SkPoint dstClips[], const SkMatrix preViewMatrices[],
 *                                        const SkSamplingOptions&, const SkPaint*,
 *                                        SrcRectConstraint);
 *
 *     virtual void onDrawVerticesObject(const SkVertices* vertices, SkBlendMode mode,
 *                                       const SkPaint& paint);
 *     virtual void onDrawMesh(const SkMesh&, sk_sp<SkBlender>, const SkPaint&);
 *     virtual void onDrawAnnotation(const SkRect& rect, const char key[], SkData* value);
 *     virtual void onDrawShadowRec(const SkPath&, const SkDrawShadowRec&);
 *
 *     virtual void onDrawDrawable(SkDrawable* drawable, const SkMatrix* matrix);
 *     virtual void onDrawPicture(const SkPicture* picture, const SkMatrix* matrix,
 *                                const SkPaint* paint);
 *
 *     virtual void onDrawEdgeAAQuad(const SkRect& rect, const SkPoint clip[4], QuadAAFlags aaFlags,
 *                                   const SkColor4f& color, SkBlendMode mode);
 *
 *     enum ClipEdgeStyle {
 *         kHard_ClipEdgeStyle,
 *         kSoft_ClipEdgeStyle
 *     };
 *
 *     virtual void onClipRect(const SkRect& rect, SkClipOp op, ClipEdgeStyle edgeStyle);
 *     virtual void onClipRRect(const SkRRect& rrect, SkClipOp op, ClipEdgeStyle edgeStyle);
 *     virtual void onClipPath(const SkPath& path, SkClipOp op, ClipEdgeStyle edgeStyle);
 *     virtual void onClipShader(sk_sp<SkShader>, SkClipOp);
 *     virtual void onClipRegion(const SkRegion& deviceRgn, SkClipOp op);
 *     virtual void onResetClip();
 *
 *     virtual void onDiscard();
 *
 *     /**
 *      */
 *     virtual sk_sp<sktext::gpu::Slug> onConvertGlyphRunListToSlug(
 *             const sktext::GlyphRunList& glyphRunList, const SkPaint& paint);
 *
 *     /**
 *      */
 *     virtual void onDrawSlug(const sktext::gpu::Slug* slug, const SkPaint& paint);
 *
 * private:
 *     enum class PredrawFlags : unsigned {
 *         kNone                    = 0,
 *         kOpaqueShaderOverride    = 1, // The paint's shader is overridden with an opaque image
 *         kNonOpaqueShaderOverride = 2, // The paint's shader is overridden but is not opaque
 *         kCheckForOverwrite       = 4, // Check if the draw would overwrite the entire surface
 *         kSkipMaskFilterAutoLayer = 8, // Do not apply mask filters in the AutoLayer
 *     };
 *     // Inlined SK_DECL_BITMASK_OPS_FRIENDS to avoid including SkEnumBitMask.h
 *     friend constexpr SkEnumBitMask<PredrawFlags> operator|(PredrawFlags, PredrawFlags);
 *     friend constexpr SkEnumBitMask<PredrawFlags> operator&(PredrawFlags, PredrawFlags);
 *     friend constexpr SkEnumBitMask<PredrawFlags> operator^(PredrawFlags, PredrawFlags);
 *     friend constexpr SkEnumBitMask<PredrawFlags> operator~(PredrawFlags);
 *
 *     // notify our surface (if we have one) that we are about to draw, so it
 *     // can perform copy-on-write or invalidate any cached images
 *     // returns false if the copy failed
 *     [[nodiscard]] bool predrawNotify(bool willOverwritesEntireSurface = false);
 *     [[nodiscard]] bool predrawNotify(const SkRect*, const SkPaint*, SkEnumBitMask<PredrawFlags>);
 *
 *     // call the appropriate predrawNotify and create a layer if needed.
 *     std::optional<AutoLayerForImageFilter> aboutToDraw(
 *             const SkPaint& paint,
 *             const SkRect* rawBounds,
 *             SkEnumBitMask<PredrawFlags> flags);
 *     std::optional<AutoLayerForImageFilter> aboutToDraw(
 *             const SkPaint& paint,
 *             const SkRect* rawBounds = nullptr);
 *
 *     // The bottom-most device in the stack, only changed by init(). Image properties and the final
 *     // canvas pixels are determined by this device.
 *     SkDevice* rootDevice() const {
 *         SkASSERT(fRootDevice);
 *         return fRootDevice.get();
 *     }
 *
 *     // The top-most device in the stack, will change within saveLayer()'s. All drawing and clipping
 *     // operations should route to this device.
 *     SkDevice* topDevice() const;
 *
 *     // Canvases maintain a sparse stack of layers, where the top-most layer receives the drawing,
 *     // clip, and matrix commands. There is a layer per call to saveLayer() using the
 *     // kFullLayer_SaveLayerStrategy.
 *     struct Layer {
 *         sk_sp<SkDevice>                                fDevice;
 *         skia_private::STArray<1, sk_sp<SkImageFilter>> fImageFilters;
 *         SkPaint                                        fPaint;
 *         bool                                           fIsCoverage;
 *         bool                                           fDiscard;
 *
 *         // If true, the layer image is sized to include a 1px buffer that remains transparent
 *         // to allow for faster linear filtering under complex transforms.
 *         bool                                           fIncludesPadding;
 *
 *         Layer(sk_sp<SkDevice> device,
 *               FilterSpan imageFilters,
 *               const SkPaint& paint,
 *               bool isCoverage,
 *               bool includesPadding);
 *     };
 *
 *     // Encapsulate state needed to restore from saveBehind()
 *     struct BackImage {
 *         // Out of line to avoid including SkSpecialImage.h
 *         BackImage(sk_sp<SkSpecialImage>, SkIPoint);
 *         BackImage(const BackImage&);
 *         BackImage(BackImage&&);
 *         BackImage& operator=(const BackImage&);
 *         ~BackImage();
 *
 *         sk_sp<SkSpecialImage> fImage;
 *         SkIPoint              fLoc;
 *     };
 *
 *     class MCRec {
 *     public:
 *         // If not null, this MCRec corresponds with the saveLayer() record that made the layer.
 *         // The base "layer" is not stored here, since it is stored inline in SkCanvas and has no
 *         // restoration behavior.
 *         std::unique_ptr<Layer> fLayer;
 *
 *         // This points to the device of the top-most layer (which may be lower in the stack), or
 *         // to the canvas's fRootDevice. The MCRec does not own the device.
 *         SkDevice* fDevice;
 *
 *         std::unique_ptr<BackImage> fBackImage;
 *         SkM44 fMatrix;
 *         int fDeferredSaveCount = 0;
 *
 *         MCRec(SkDevice* device);
 *         MCRec(const MCRec* prev);
 *         ~MCRec();
 *
 *         void newLayer(sk_sp<SkDevice> layerDevice,
 *                       FilterSpan filters,
 *                       const SkPaint& restorePaint,
 *                       bool layerIsCoverage,
 *                       bool includesPadding);
 *
 *         void reset(SkDevice* device);
 *     };
 *
 * #if defined(SK_CANVAS_SAVE_RESTORE_PREALLOC_COUNT)
 *     static constexpr int kMCRecCount = SK_CANVAS_SAVE_RESTORE_PREALLOC_COUNT;
 * #else
 *     static constexpr int kMCRecCount = 32; // common depth for save/restores
 * #endif
 *
 *     // This stack allocation of memory will be used to house the first kMCRecCount
 *     // layers without need to call malloc.
 *     alignas(MCRec) std::byte fMCRecStorage[sizeof(MCRec) * kMCRecCount];
 *
 *     SkDeque     fMCStack; // uses the stack memory
 *     MCRec*      fMCRec;   // points to top of stack for convenience
 *
 *     // Installed via init()
 *     sk_sp<SkDevice> fRootDevice;
 *     const SkSurfaceProps fProps;
 *
 *     int         fSaveCount;         // value returned by getSaveCount()
 *
 *     std::unique_ptr<SkRasterHandleAllocator> fAllocator;
 *
 *     SkSurface_Base*  fSurfaceBase;
 *     SkSurface_Base* getSurfaceBase() const { return fSurfaceBase; }
 *     void setSurfaceBase(SkSurface_Base* sb) {
 *         fSurfaceBase = sb;
 *     }
 *     friend class SkSurface_Base;
 *     friend class SkSurface_Ganesh;
 *
 *     SkIRect fClipRestrictionRect = SkIRect::MakeEmpty();
 *     int fClipRestrictionSaveCount = -1;
 *
 *     void doSave();
 *     void checkForDeferredSave();
 *     void internalSetMatrix(const SkM44&);
 *
 *     virtual void onSurfaceDelete() {}
 *
 *     friend class SkAndroidFrameworkUtils;
 *     friend class SkCanvasPriv;      // needs to expose android functions for testing outside android
 *     friend class AutoLayerForImageFilter;
 *     friend class SkSurface_Raster;  // needs getDevice()
 *     friend class SkNoDrawCanvas;    // needs resetForNextPicture()
 *     friend class SkNWayCanvas;
 *     friend class SkPictureRecord;   // predrawNotify (why does it need it? <reed>)
 *     friend class SkOverdrawCanvas;
 *     friend class SkRasterHandleAllocator;
 *     friend class SkRecords::Draw;
 *     template <typename Key>
 *     friend class skiatest::TestCanvas;
 *
 * protected:
 *     // For use by SkNoDrawCanvas (via SkCanvasVirtualEnforcer, which can't be a friend)
 *     explicit SkCanvas(const SkIRect& bounds);
 *
 * private:
 *     SkCanvas(const SkBitmap&, std::unique_ptr<SkRasterHandleAllocator>,
 *              SkRasterHandleAllocator::Handle, const SkSurfaceProps* props);
 *
 *     SkCanvas(SkCanvas&&) = delete;
 *     SkCanvas(const SkCanvas&) = delete;
 *     SkCanvas& operator=(SkCanvas&&) = delete;
 *     SkCanvas& operator=(const SkCanvas&) = delete;
 *
 *     friend class sktext::gpu::Slug;
 *     friend class SkPicturePlayback;
 *     /**
 *      * Convert a SkTextBlob to a sktext::gpu::Slug using the current canvas state.
 *      */
 *     sk_sp<sktext::gpu::Slug> convertBlobToSlug(const SkTextBlob& blob, SkPoint origin,
 *                                                const SkPaint& paint);
 *
 *     /**
 *      * Draw an sktext::gpu::Slug given the current canvas state.
 *      */
 *     void drawSlug(const sktext::gpu::Slug* slug, const SkPaint& paint);
 *
 *     /** Experimental
 *      *  Saves the specified subset of the current pixels in the current layer,
 *      *  and then clears those pixels to transparent black.
 *      *  Restores the pixels on restore() by drawing them in SkBlendMode::kDstOver.
 *      *
 *      *  @param subset   conservative bounds of the area to be saved / restored.
 *      *  @return depth of save state stack before this call was made.
 *      */
 *     int only_axis_aligned_saveBehind(const SkRect* subset);
 *
 *     /**
 *      *  Like drawPaint, but magically clipped to the most recent saveBehind buffer rectangle.
 *      *  If there is no active saveBehind, then this draws nothing.
 *      */
 *     void drawClippedToSaveBehind(const SkPaint&);
 *
 *     void resetForNextPicture(const SkIRect& bounds);
 *
 *     // needs gettotalclip()
 *     friend class SkCanvasStateUtils;
 *
 *     void init(sk_sp<SkDevice>);
 *
 *     bool nothingToDraw(const SkPaint& paint) const;
 *
 *     // All base onDrawX() functions should call this and skip drawing if it returns true.
 *     // If 'matrix' is non-null, it maps the paint's fast bounds before checking for quick rejection
 *     bool internalQuickReject(const SkRect& bounds, const SkPaint& paint,
 *                              const SkMatrix* matrix = nullptr);
 *
 *     void internalDrawPaint(const SkPaint& paint);
 *     void internalSaveLayer(const SaveLayerRec&, SaveLayerStrategy, bool coverageOnly=false);
 *     void internalSaveBehind(const SkRect*);
 *
 *     void internalConcat44(const SkM44&);
 *
 *     // shared by save() and saveLayer()
 *     void internalSave();
 *     void internalRestore();
 *
 *     enum class DeviceCompatibleWithFilter : int {
 *         // Check the src device's local-to-device matrix for compatibility with the filter, and if
 *         // it is not compatible, introduce an intermediate image and transformation that allows the
 *         // filter to be evaluated on the modified src content.
 *         kUnknown,
 *         // Assume that the src device's local-to-device matrix is compatible with the filter.
 *         kYes,
 *         // Assume that the src device's local-to-device matrix is compatible with the filter,
 *         // *and* the source image has a 1px buffer of padding.
 *         kYesWithPadding
 *     };
 *     /**
 *      * Filters the contents of 'src' and draws the result into 'dst'. The filter is evaluated
 *      * relative to the current canvas matrix, and src is drawn to dst using their relative transform
 *      * 'paint' is applied after the filter and must not have a mask or image filter of its own.
 *      * A null 'filter' behaves as if the identity filter were used.
 *      *
 *      * 'scaleFactor' is an extra uniform scale transform applied to downscale the 'src' image
 *      * before any filtering, or as part of the copy, and is then drawn with 1/scaleFactor to 'dst'.
 *      * Must be 1.0 if 'compat' is kYes (i.e. any scale factor has already been baked into the
 *      * relative transforms between the devices).
 *      *
 *      * 'srcTileMode' is the tile mode to apply to the boundary of the 'src' image when insufficient
 *      * content is available. It defaults to kDecal for the regular saveLayer() case.
 *      */
 *     void internalDrawDeviceWithFilter(SkDevice* src, SkDevice* dst,
 *                                       FilterSpan filters, const SkPaint& paint,
 *                                       DeviceCompatibleWithFilter compat,
 *                                       const SkColorInfo& filterColorInfo,
 *                                       SkScalar scaleFactor = 1.f,
 *                                       SkTileMode srcTileMode = SkTileMode::kDecal,
 *                                       bool srcIsCoverageLayer = false);
 *
 *     /*
 *      *  Returns true if drawing the specified rect (or all if it is null) with the specified
 *      *  paint (or default if null) would overwrite the entire root device of the canvas
 *      *  (i.e. the canvas' surface if it had one).
 *      */
 *     bool wouldOverwriteEntireSurface(const SkRect*, const SkPaint*,
 *                                      SkEnumBitMask<PredrawFlags>) const;
 *
 *     /**
 *      *  Returns true if the clip (for any active layer) contains antialiasing.
 *      *  If the clip is empty, this will return false.
 *      */
 *     bool androidFramework_isClipAA() const;
 *
 *     /**
 *      * Reset the clip to be wide-open (modulo any separately specified device clip restriction).
 *      * This operate within the save/restore clip stack so it can be undone by restoring to an
 *      * earlier save point.
 *      */
 *     void internal_private_resetClip();
 *
 *     virtual SkPaintFilterCanvas* internal_private_asPaintFilterCanvas() const { return nullptr; }
 *
 *     // Keep track of the device clip bounds in the canvas' global space to reject draws before
 *     // invoking the top-level device.
 *     SkRect fQuickRejectBounds;
 *
 *     // Compute the clip's bounds based on all clipped SkDevice's reported device bounds transformed
 *     // into the canvas' global space.
 *     SkRect computeDeviceClipBounds(bool outsetForAA=true) const;
 *
 *     // Returns the paint's mask filter if it can be used to draw an rrect with an analytic blur, and
 *     // returns null otherwise.
 *     const SkBlurMaskFilterImpl* canAttemptBlurredRRectDraw(const SkPaint&) const;
 *
 *     // Attempt to draw a rrect with an analytic blur. If the draw succeeds or predrawNotify fails,
 *     // nullopt is returned indicating that nothing further should be drawn.
 *     std::optional<AutoLayerForImageFilter> attemptBlurredRRectDraw(const SkRRect&,
 *                                                                    const SkBlurMaskFilterImpl*,
 *                                                                    const SkPaint&,
 *                                                                    SkEnumBitMask<PredrawFlags>);
 *
 *     class AutoUpdateQRBounds;
 *     void validateClip() const;
 *
 *     std::unique_ptr<sktext::GlyphRunBuilder> fScratchGlyphRunBuilder;
 * }
 * ```
 */
public open class SkCanvas public constructor() {
  /**
   * C++ original:
   * ```cpp
   * explicit SkCanvas(sk_sp<SkDevice> device)
   * ```
   */
  public var skSp: SkCanvas = TODO("Initialize skSp")

  /**
   * C++ original:
   * ```cpp
   * static constexpr int kMaxFiltersPerLayer = 16
   * ```
   */
  public var fMCRecStorage: ByteArray = TODO("Initialize fMCRecStorage")

  /**
   * C++ original:
   * ```cpp
   * static constexpr int kMCRecCount = 32
   * ```
   */
  public var fMCStack: Int = TODO("Initialize fMCStack")

  /**
   * C++ original:
   * ```cpp
   * std::byte fMCRecStorage[sizeof(MCRec) * kMCRecCount]
   * ```
   */
  public var fMCRec: MCRec? = TODO("Initialize fMCRec")

  /**
   * C++ original:
   * ```cpp
   * SkDeque     fMCStack
   * ```
   */
  public var fRootDevice: Int = TODO("Initialize fRootDevice")

  /**
   * C++ original:
   * ```cpp
   * MCRec*      fMCRec
   * ```
   */
  public val fProps: Int = TODO("Initialize fProps")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkDevice> fRootDevice
   * ```
   */
  public var fSaveCount: Int = TODO("Initialize fSaveCount")

  /**
   * C++ original:
   * ```cpp
   * const SkSurfaceProps fProps
   * ```
   */
  public var fAllocator: Int = TODO("Initialize fAllocator")

  /**
   * C++ original:
   * ```cpp
   * int         fSaveCount
   * ```
   */
  public var fSurfaceBase: SkSurfaceBase? = TODO("Initialize fSurfaceBase")

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkRasterHandleAllocator> fAllocator
   * ```
   */
  public var fClipRestrictionRect: Int = TODO("Initialize fClipRestrictionRect")

  /**
   * C++ original:
   * ```cpp
   * SkSurface_Base*  fSurfaceBase
   * ```
   */
  public var fClipRestrictionSaveCount: Int = TODO("Initialize fClipRestrictionSaveCount")

  /**
   * C++ original:
   * ```cpp
   * SkIRect fClipRestrictionRect
   * ```
   */
  private var fQuickRejectBounds: Int = TODO("Initialize fQuickRejectBounds")

  /**
   * C++ original:
   * ```cpp
   * int fClipRestrictionSaveCount = -1
   * ```
   */
  private var fScratchGlyphRunBuilder: Int = TODO("Initialize fScratchGlyphRunBuilder")

  /**
   * C++ original:
   * ```cpp
   * SkCanvas::SkCanvas() : fMCStack(sizeof(MCRec), fMCRecStorage, sizeof(fMCRecStorage)) {
   *     this->init(nullptr);
   * }
   * ```
   */
  public constructor(
    width: Int,
    height: Int,
    props: SkSurfaceProps? = TODO(),
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkCanvas(int width, int height, const SkSurfaceProps* props = nullptr)
   * ```
   */
  public constructor(bitmap: SkBitmap) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkCanvas::SkCanvas(const SkBitmap& bitmap) : SkCanvas(bitmap, nullptr, nullptr, nullptr) {}
   * ```
   */
  public constructor(bitmap: SkBitmap, props: SkSurfaceProps) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkCanvas(const SkBitmap& bitmap, const SkSurfaceProps& props)
   * ```
   */
  public constructor(bounds: SkIRect) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * explicit SkCanvas(const SkIRect& bounds)
   * ```
   */
  public constructor(
    bitmap: SkBitmap,
    alloc: SkRasterHandleAllocator?,
    hndl: SkRasterHandleAllocatorHandle,
    props: SkSurfaceProps?,
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkCanvas(const SkBitmap&, std::unique_ptr<SkRasterHandleAllocator>,
   *              SkRasterHandleAllocator::Handle, const SkSurfaceProps* props)
   * ```
   */
  public constructor(param0: SkCanvas) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkCanvas(SkCanvas&&) = delete
   * ```
   */
  public constructor(device: SkSp<SkDevice>) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkImageInfo SkCanvas::imageInfo() const {
   *     return this->onImageInfo();
   * }
   * ```
   */
  public fun imageInfo(): Int {
    TODO("Implement imageInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkCanvas::getProps(SkSurfaceProps* props) const {
   *     return this->onGetProps(props, /*top=*/false);
   * }
   * ```
   */
  public fun getProps(props: SkSurfaceProps?): Boolean {
    TODO("Implement getProps")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSurfaceProps SkCanvas::getBaseProps() const {
   *     SkSurfaceProps props;
   *     this->onGetProps(&props, /*top=*/false);
   *     return props;
   * }
   * ```
   */
  public fun getBaseProps(): Int {
    TODO("Implement getBaseProps")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSurfaceProps SkCanvas::getTopProps() const {
   *     SkSurfaceProps props;
   *     this->onGetProps(&props, /*top=*/true);
   *     return props;
   * }
   * ```
   */
  public fun getTopProps(): Int {
    TODO("Implement getTopProps")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize SkCanvas::getBaseLayerSize() const {
   *     return this->rootDevice()->imageInfo().dimensions();
   * }
   * ```
   */
  public open fun getBaseLayerSize(): Int {
    TODO("Implement getBaseLayerSize")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkSurface> SkCanvas::makeSurface(const SkImageInfo& info, const SkSurfaceProps* props) {
   *     if (nullptr == props) {
   *         props = &fProps;
   *     }
   *     return this->onNewSurface(info, *props);
   * }
   * ```
   */
  public fun makeSurface(info: SkImageInfo, props: SkSurfaceProps? = TODO()): Int {
    TODO("Implement makeSurface")
  }

  /**
   * C++ original:
   * ```cpp
   * GrRecordingContext* SkCanvas::recordingContext() const {
   *     return this->topDevice()->recordingContext();
   * }
   * ```
   */
  public open fun recordingContext(): GrRecordingContext {
    TODO("Implement recordingContext")
  }

  /**
   * C++ original:
   * ```cpp
   * skgpu::graphite::Recorder* SkCanvas::recorder() const {
   *     return this->topDevice()->recorder();
   * }
   * ```
   */
  public open fun recorder(): Recorder {
    TODO("Implement recorder")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRecorder* SkCanvas::baseRecorder() const {
   *     return this->topDevice()->baseRecorder();
   * }
   * ```
   */
  public open fun baseRecorder(): SkRecorder {
    TODO("Implement baseRecorder")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSurface* SkCanvas::getSurface() const {
   *     return fSurfaceBase;
   * }
   * ```
   */
  public fun getSurface(): SkSurface {
    TODO("Implement getSurface")
  }

  /**
   * C++ original:
   * ```cpp
   * void* SkCanvas::accessTopLayerPixels(SkImageInfo* info, size_t* rowBytes, SkIPoint* origin) {
   *     SkPixmap pmap;
   *     if (!this->onAccessTopLayerPixels(&pmap)) {
   *         return nullptr;
   *     }
   *     if (info) {
   *         *info = pmap.info();
   *     }
   *     if (rowBytes) {
   *         *rowBytes = pmap.rowBytes();
   *     }
   *     if (origin) {
   *         // If the caller requested the origin, they presumably are expecting the returned pixels to
   *         // be axis-aligned with the root canvas. If the top level device isn't axis aligned, that's
   *         // not the case. Until we update accessTopLayerPixels() to accept a coord space matrix
   *         // instead of an origin, just don't expose the pixels in that case. Note that this means
   *         // that layers with complex coordinate spaces can still report their pixels if the caller
   *         // does not ask for the origin (e.g. just to dump its output to a file, etc).
   *         if (this->topDevice()->isPixelAlignedToGlobal()) {
   *             *origin = this->topDevice()->getOrigin();
   *         } else {
   *             return nullptr;
   *         }
   *     }
   *     return pmap.writable_addr();
   * }
   * ```
   */
  public fun accessTopLayerPixels(
    info: SkImageInfo?,
    rowBytes: ULong?,
    origin: SkIPoint? = TODO(),
  ) {
    TODO("Implement accessTopLayerPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRasterHandleAllocator::Handle SkCanvas::accessTopRasterHandle() const {
   *     const SkDevice* dev = this->topDevice();
   *     if (fAllocator) {
   *         SkRasterHandleAllocator::Handle handle = dev->getRasterHandle();
   *         SkIRect clip = dev->devClipBounds();
   *         if (!clip.intersect({0, 0, dev->width(), dev->height()})) {
   *             clip.setEmpty();
   *         }
   *
   *         fAllocator->updateHandle(handle, dev->localToDevice(), clip);
   *         return handle;
   *     }
   *     return nullptr;
   * }
   * ```
   */
  public fun accessTopRasterHandle(): Int {
    TODO("Implement accessTopRasterHandle")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkCanvas::peekPixels(SkPixmap* pmap) {
   *     return this->onPeekPixels(pmap);
   * }
   * ```
   */
  public fun peekPixels(pixmap: SkPixmap?): Boolean {
    TODO("Implement peekPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkCanvas::readPixels(const SkImageInfo& dstInfo, void* dstP, size_t rowBytes, int x, int y) {
   *     return this->readPixels({ dstInfo, dstP, rowBytes}, x, y);
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
   * bool SkCanvas::readPixels(const SkPixmap& pm, int x, int y) {
   *     return pm.addr() && this->rootDevice()->readPixels(pm, x, y);
   * }
   * ```
   */
  public fun readPixels(
    pixmap: SkPixmap,
    srcX: Int,
    srcY: Int,
  ): Boolean {
    TODO("Implement readPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkCanvas::readPixels(const SkBitmap& bm, int x, int y) {
   *     SkPixmap pm;
   *     return bm.peekPixels(&pm) && this->readPixels(pm, x, y);
   * }
   * ```
   */
  public fun readPixels(
    bitmap: SkBitmap,
    srcX: Int,
    srcY: Int,
  ): Boolean {
    TODO("Implement readPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkCanvas::writePixels(const SkImageInfo& srcInfo, const void* pixels, size_t rowBytes,
   *                            int x, int y) {
   *     SkDevice* device = this->rootDevice();
   *
   *     // This check gives us an early out and prevents generation ID churn on the surface.
   *     // This is purely optional: it is a subset of the checks performed by SkWritePixelsRec.
   *     SkIRect srcRect = SkIRect::MakeXYWH(x, y, srcInfo.width(), srcInfo.height());
   *     if (!srcRect.intersect({0, 0, device->width(), device->height()})) {
   *         return false;
   *     }
   *
   *     // Tell our owning surface to bump its generation ID.
   *     const bool completeOverwrite = srcRect.size() == device->imageInfo().dimensions();
   *     if (!this->predrawNotify(completeOverwrite)) {
   *         return false;
   *     }
   *
   *     // This can still fail, most notably in the case of a invalid color type or alpha type
   *     // conversion.  We could pull those checks into this function and avoid the unnecessary
   *     // generation ID bump.  But then we would be performing those checks twice, since they
   *     // are also necessary at the bitmap/pixmap entry points.
   *     return device->writePixels({srcInfo, pixels, rowBytes}, x, y);
   * }
   * ```
   */
  public fun writePixels(
    info: SkImageInfo,
    pixels: Unit?,
    rowBytes: ULong,
    x: Int,
    y: Int,
  ): Boolean {
    TODO("Implement writePixels")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkCanvas::writePixels(const SkBitmap& bitmap, int x, int y) {
   *     SkPixmap pm;
   *     if (bitmap.peekPixels(&pm)) {
   *         return this->writePixels(pm.info(), pm.addr(), pm.rowBytes(), x, y);
   *     }
   *     return false;
   * }
   * ```
   */
  public fun writePixels(
    bitmap: SkBitmap,
    x: Int,
    y: Int,
  ): Boolean {
    TODO("Implement writePixels")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkCanvas::save() {
   *     fSaveCount += 1;
   *     fMCRec->fDeferredSaveCount += 1;
   *     return this->getSaveCount() - 1;  // return our prev value
   * }
   * ```
   */
  public fun save(): Int {
    TODO("Implement save")
  }

  /**
   * C++ original:
   * ```cpp
   * int saveLayer(const SkRect* bounds, const SkPaint* paint)
   * ```
   */
  public fun saveLayer(bounds: SkRect?, paint: SkPaint?): Int {
    TODO("Implement saveLayer")
  }

  /**
   * C++ original:
   * ```cpp
   * int saveLayer(const SkRect& bounds, const SkPaint* paint) {
   *         return this->saveLayer(&bounds, paint);
   *     }
   * ```
   */
  public fun saveLayer(bounds: SkRect, paint: SkPaint?): Int {
    TODO("Implement saveLayer")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkCanvas::saveLayerAlphaf(const SkRect* bounds, float alpha) {
   *     if (alpha >= 1.0f) {
   *         return this->saveLayer(bounds, nullptr);
   *     } else {
   *         SkPaint tmpPaint;
   *         tmpPaint.setAlphaf(alpha);
   *         return this->saveLayer(bounds, &tmpPaint);
   *     }
   * }
   * ```
   */
  public fun saveLayerAlphaf(bounds: SkRect?, alpha: Float): Int {
    TODO("Implement saveLayerAlphaf")
  }

  /**
   * C++ original:
   * ```cpp
   * int saveLayerAlpha(const SkRect* bounds, U8CPU alpha) {
   *         return this->saveLayerAlphaf(bounds, alpha * (1.0f / 255));
   *     }
   * ```
   */
  public fun saveLayerAlpha(bounds: SkRect?, alpha: U8CPU): Int {
    TODO("Implement saveLayerAlpha")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkCanvas::saveLayer(const SaveLayerRec& rec) {
   *     TRACE_EVENT0("skia", TRACE_FUNC);
   *     if (rec.fPaint && this->nothingToDraw(*rec.fPaint)) {
   *         // no need for the layer (or any of the draws until the matching restore()
   *         this->save();
   *         this->clipRect({0,0,0,0});
   *     } else {
   *         SaveLayerStrategy strategy = this->getSaveLayerStrategy(rec);
   *         fSaveCount += 1;
   *         this->internalSaveLayer(rec, strategy);
   *     }
   *     return this->getSaveCount() - 1;
   * }
   * ```
   */
  private fun saveLayer(layerRec: SaveLayerRec): Int {
    TODO("Implement saveLayer")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::restore() {
   *     if (fMCRec->fDeferredSaveCount > 0) {
   *         SkASSERT(fSaveCount > 1);
   *         fSaveCount -= 1;
   *         fMCRec->fDeferredSaveCount -= 1;
   *     } else {
   *         // check for underflow
   *         if (fMCStack.count() > 1) {
   *             this->willRestore();
   *             SkASSERT(fSaveCount > 1);
   *             fSaveCount -= 1;
   *             this->internalRestore();
   *             this->didRestore();
   *         }
   *     }
   * }
   * ```
   */
  private fun restore() {
    TODO("Implement restore")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkCanvas::getSaveCount() const {
   * #ifdef SK_DEBUG
   *     int count = 0;
   *     SkDeque::Iter iter(fMCStack, SkDeque::Iter::kFront_IterStart);
   *     for (;;) {
   *         const MCRec* rec = (const MCRec*)iter.next();
   *         if (!rec) {
   *             break;
   *         }
   *         count += 1 + rec->fDeferredSaveCount;
   *     }
   *     SkASSERT(count == fSaveCount);
   * #endif
   *     return fSaveCount;
   * }
   * ```
   */
  private fun getSaveCount(): Int {
    TODO("Implement getSaveCount")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::restoreToCount(int count) {
   *     // safety check
   *     if (count < 1) {
   *         count = 1;
   *     }
   *
   *     int n = this->getSaveCount() - count;
   *     for (int i = 0; i < n; ++i) {
   *         this->restore();
   *     }
   * }
   * ```
   */
  private fun restoreToCount(saveCount: Int) {
    TODO("Implement restoreToCount")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::translate(SkScalar dx, SkScalar dy) {
   *     if (dx || dy) {
   *         this->checkForDeferredSave();
   *         fMCRec->fMatrix.preTranslate(dx, dy);
   *
   *         this->topDevice()->setGlobalCTM(fMCRec->fMatrix);
   *
   *         this->didTranslate(dx,dy);
   *     }
   * }
   * ```
   */
  private fun translate(dx: SkScalar, dy: SkScalar) {
    TODO("Implement translate")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::scale(SkScalar sx, SkScalar sy) {
   *     if (sx != 1 || sy != 1) {
   *         this->checkForDeferredSave();
   *         fMCRec->fMatrix.preScale(sx, sy);
   *
   *         this->topDevice()->setGlobalCTM(fMCRec->fMatrix);
   *
   *         this->didScale(sx, sy);
   *     }
   * }
   * ```
   */
  private fun scale(sx: SkScalar, sy: SkScalar) {
    TODO("Implement scale")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::rotate(SkScalar degrees) {
   *     SkMatrix m;
   *     m.setRotate(degrees);
   *     this->concat(m);
   * }
   * ```
   */
  private fun rotate(degrees: SkScalar) {
    TODO("Implement rotate")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::rotate(SkScalar degrees, SkScalar px, SkScalar py) {
   *     SkMatrix m;
   *     m.setRotate(degrees, px, py);
   *     this->concat(m);
   * }
   * ```
   */
  private fun rotate(
    degrees: SkScalar,
    px: SkScalar,
    py: SkScalar,
  ) {
    TODO("Implement rotate")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::skew(SkScalar sx, SkScalar sy) {
   *     SkMatrix m;
   *     m.setSkew(sx, sy);
   *     this->concat(m);
   * }
   * ```
   */
  private fun skew(sx: SkScalar, sy: SkScalar) {
    TODO("Implement skew")
  }

  /**
   * C++ original:
   * ```cpp
   * void concat(const SkMatrix& matrix)
   * ```
   */
  private fun concat(matrix: SkMatrix) {
    TODO("Implement concat")
  }

  /**
   * C++ original:
   * ```cpp
   * void concat(const SkM44&)
   * ```
   */
  private fun concat(param0: SkM44) {
    TODO("Implement concat")
  }

  /**
   * C++ original:
   * ```cpp
   * void setMatrix(const SkM44& matrix)
   * ```
   */
  private fun setMatrix(matrix: SkM44) {
    TODO("Implement setMatrix")
  }

  /**
   * C++ original:
   * ```cpp
   * void setMatrix(const SkMatrix& matrix)
   * ```
   */
  private fun setMatrix(matrix: SkMatrix) {
    TODO("Implement setMatrix")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::resetMatrix() {
   *     this->setMatrix(SkM44());
   * }
   * ```
   */
  private fun resetMatrix() {
    TODO("Implement resetMatrix")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::clipRect(const SkRect& rect, SkClipOp op, bool doAA) {
   *     if (!rect.isFinite()) {
   *         return;
   *     }
   *     this->checkForDeferredSave();
   *     ClipEdgeStyle edgeStyle = doAA ? kSoft_ClipEdgeStyle : kHard_ClipEdgeStyle;
   *     this->onClipRect(rect.makeSorted(), op, edgeStyle);
   * }
   * ```
   */
  private fun clipRect(
    rect: SkRect,
    op: SkClipOp,
    doAntiAlias: Boolean,
  ) {
    TODO("Implement clipRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void clipRect(const SkRect& rect, SkClipOp op) {
   *         this->clipRect(rect, op, false);
   *     }
   * ```
   */
  private fun clipRect(rect: SkRect, op: SkClipOp) {
    TODO("Implement clipRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void clipRect(const SkRect& rect, bool doAntiAlias = false) {
   *         this->clipRect(rect, SkClipOp::kIntersect, doAntiAlias);
   *     }
   * ```
   */
  private fun clipRect(rect: SkRect, doAntiAlias: Boolean = TODO()) {
    TODO("Implement clipRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void clipIRect(const SkIRect& irect, SkClipOp op = SkClipOp::kIntersect) {
   *         this->clipRect(SkRect::Make(irect), op, false);
   *     }
   * ```
   */
  private fun clipIRect(irect: SkIRect, op: SkClipOp = TODO()) {
    TODO("Implement clipIRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::androidFramework_setDeviceClipRestriction(const SkIRect& rect) {
   *     // The device clip restriction is a surface-space rectangular intersection that cannot be
   *     // drawn outside of. The rectangle is remembered so that subsequent resetClip calls still
   *     // respect the restriction. Other than clip resetting, all clip operations restrict the set
   *     // of renderable pixels, so once set, the restriction will be respected until the canvas
   *     // save stack is restored past the point this function was invoked. Unfortunately, the current
   *     // implementation relies on the clip stack of the underyling SkDevices, which leads to some
   *     // awkward behavioral interactions (see skbug.com/40043342).
   *     //
   *     // Namely, a canvas restore() could undo the clip restriction's rect, and if
   *     // setDeviceClipRestriction were called at a nested save level, there's no way to undo just the
   *     // prior restriction and re-apply the new one. It also only makes sense to apply to the base
   *     // device; any other device for a saved layer will be clipped back to the base device during its
   *     // matched restore. As such, we:
   *     // - Remember the save count that added the clip restriction and reset the rect to empty when
   *     //   we've restored past that point to keep our state in sync with the device's clip stack.
   *     // - We assert that we're on the base device when this is invoked.
   *     // - We assert that setDeviceClipRestriction() is only called when there was no prior
   *     //   restriction (cannot re-restrict, and prior state must have been reset by restoring the
   *     //   canvas state).
   *     // - Historically, the empty rect would reset the clip restriction but it only could do so
   *     //   partially since the device's clips wasn't adjusted. Resetting is now handled
   *     //   automatically via SkCanvas::restore(), so empty input rects are skipped.
   *     SkASSERT(this->topDevice() == this->rootDevice()); // shouldn't be in a nested layer
   *     // and shouldn't already have a restriction
   *     SkASSERT(fClipRestrictionSaveCount < 0 && fClipRestrictionRect.isEmpty());
   *
   *     if (fClipRestrictionSaveCount < 0 && !rect.isEmpty()) {
   *         fClipRestrictionRect = rect;
   *         fClipRestrictionSaveCount = this->getSaveCount();
   *
   *         // A non-empty clip restriction immediately applies an intersection op (ignoring the ctm).
   *         // so we have to resolve the save.
   *         this->checkForDeferredSave();
   *         AutoUpdateQRBounds aqr(this);
   *         // Use clipRegion() since that operates in canvas-space, whereas clipRect() would apply the
   *         // device's current transform first.
   *         this->topDevice()->clipRegion(SkRegion(rect), SkClipOp::kIntersect);
   *     }
   * }
   * ```
   */
  private fun androidFrameworkSetDeviceClipRestriction(rect: SkIRect) {
    TODO("Implement androidFrameworkSetDeviceClipRestriction")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::clipRRect(const SkRRect& rrect, SkClipOp op, bool doAA) {
   *     this->checkForDeferredSave();
   *     ClipEdgeStyle edgeStyle = doAA ? kSoft_ClipEdgeStyle : kHard_ClipEdgeStyle;
   *     if (rrect.isRect()) {
   *         this->onClipRect(rrect.getBounds(), op, edgeStyle);
   *     } else {
   *         this->onClipRRect(rrect, op, edgeStyle);
   *     }
   * }
   * ```
   */
  private fun clipRRect(
    rrect: SkRRect,
    op: SkClipOp,
    doAntiAlias: Boolean,
  ) {
    TODO("Implement clipRRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void clipRRect(const SkRRect& rrect, SkClipOp op) {
   *         this->clipRRect(rrect, op, false);
   *     }
   * ```
   */
  private fun clipRRect(rrect: SkRRect, op: SkClipOp) {
    TODO("Implement clipRRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void clipRRect(const SkRRect& rrect, bool doAntiAlias = false) {
   *         this->clipRRect(rrect, SkClipOp::kIntersect, doAntiAlias);
   *     }
   * ```
   */
  private fun clipRRect(rrect: SkRRect, doAntiAlias: Boolean = TODO()) {
    TODO("Implement clipRRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::clipPath(const SkPath& path, SkClipOp op, bool doAA) {
   *     this->checkForDeferredSave();
   *     ClipEdgeStyle edgeStyle = doAA ? kSoft_ClipEdgeStyle : kHard_ClipEdgeStyle;
   *
   *     if (!path.isInverseFillType() && fMCRec->fMatrix.asM33().rectStaysRect()) {
   *         SkRect r;
   *         if (path.isRect(&r)) {
   *             this->onClipRect(r, op, edgeStyle);
   *             return;
   *         }
   *         SkRRect rrect;
   *         if (path.isOval(&r)) {
   *             rrect.setOval(r);
   *             this->onClipRRect(rrect, op, edgeStyle);
   *             return;
   *         }
   *         if (path.isRRect(&rrect)) {
   *             this->onClipRRect(rrect, op, edgeStyle);
   *             return;
   *         }
   *     }
   *
   *     this->onClipPath(path, op, edgeStyle);
   * }
   * ```
   */
  private fun clipPath(
    path: SkPath,
    op: SkClipOp,
    doAntiAlias: Boolean,
  ) {
    TODO("Implement clipPath")
  }

  /**
   * C++ original:
   * ```cpp
   * void clipPath(const SkPath& path, SkClipOp op) {
   *         this->clipPath(path, op, false);
   *     }
   * ```
   */
  private fun clipPath(path: SkPath, op: SkClipOp) {
    TODO("Implement clipPath")
  }

  /**
   * C++ original:
   * ```cpp
   * void clipPath(const SkPath& path, bool doAntiAlias = false) {
   *         this->clipPath(path, SkClipOp::kIntersect, doAntiAlias);
   *     }
   * ```
   */
  private fun clipPath(path: SkPath, doAntiAlias: Boolean = TODO()) {
    TODO("Implement clipPath")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::clipShader(sk_sp<SkShader> sh, SkClipOp op) {
   *     if (sh) {
   *         if (sh->isOpaque()) {
   *             if (op == SkClipOp::kIntersect) {
   *                 // we don't occlude anything, so skip this call
   *             } else {
   *                 SkASSERT(op == SkClipOp::kDifference);
   *                 // we occlude everything, so set the clip to empty
   *                 this->clipRect({0,0,0,0});
   *             }
   *         } else {
   *             this->checkForDeferredSave();
   *             this->onClipShader(std::move(sh), op);
   *         }
   *     }
   * }
   * ```
   */
  private fun clipShader(sh: SkSp<SkShader>, op: SkClipOp = TODO()) {
    TODO("Implement clipShader")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::clipRegion(const SkRegion& rgn, SkClipOp op) {
   *     this->checkForDeferredSave();
   *     this->onClipRegion(rgn, op);
   * }
   * ```
   */
  private fun clipRegion(deviceRgn: SkRegion, op: SkClipOp = TODO()) {
    TODO("Implement clipRegion")
  }

  /**
   * C++ original:
   * ```cpp
   * bool quickReject(const SkRect& rect) const
   * ```
   */
  private fun quickReject(rect: SkRect): Boolean {
    TODO("Implement quickReject")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkCanvas::quickReject(const SkPath& path) const {
   *     return path.isEmpty() || this->quickReject(path.getBounds());
   * }
   * ```
   */
  private fun quickReject(path: SkPath): Boolean {
    TODO("Implement quickReject")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect SkCanvas::getLocalClipBounds() const {
   *     SkIRect ibounds = this->getDeviceClipBounds();
   *     if (ibounds.isEmpty()) {
   *         return SkRect::MakeEmpty();
   *     }
   *
   *     auto inverse = fMCRec->fMatrix.asM33().invert();
   *     // if we can't invert the CTM, we can't return local clip bounds
   *     if (!inverse) {
   *         return SkRect::MakeEmpty();
   *     }
   *
   *     // adjust it outwards in case we are antialiasing
   *     const int margin = 1;
   *
   *     return inverse->mapRect(SkRect::Make(ibounds.makeOutset(margin, margin)));
   * }
   * ```
   */
  private fun getLocalClipBounds(): Int {
    TODO("Implement getLocalClipBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * bool getLocalClipBounds(SkRect* bounds) const {
   *         *bounds = this->getLocalClipBounds();
   *         return !bounds->isEmpty();
   *     }
   * ```
   */
  private fun getLocalClipBounds(bounds: SkRect?): Boolean {
    TODO("Implement getLocalClipBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * SkIRect SkCanvas::getDeviceClipBounds() const {
   *     return this->computeDeviceClipBounds(/*outsetForAA=*/false).roundOut();
   * }
   * ```
   */
  private fun getDeviceClipBounds(): Int {
    TODO("Implement getDeviceClipBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * bool getDeviceClipBounds(SkIRect* bounds) const {
   *         *bounds = this->getDeviceClipBounds();
   *         return !bounds->isEmpty();
   *     }
   * ```
   */
  private fun getDeviceClipBounds(bounds: SkIRect?): Boolean {
    TODO("Implement getDeviceClipBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawColor(SkColor color, SkBlendMode mode = SkBlendMode::kSrcOver) {
   *         this->drawColor(SkColor4f::FromColor(color), mode);
   *     }
   * ```
   */
  private fun drawColor(color: SkColor, mode: SkBlendMode = TODO()) {
    TODO("Implement drawColor")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawColor(const SkColor4f& color, SkBlendMode mode = SkBlendMode::kSrcOver)
   * ```
   */
  private fun drawColor(color: SkColor4f, mode: SkBlendMode = TODO()) {
    TODO("Implement drawColor")
  }

  /**
   * C++ original:
   * ```cpp
   * void clear(SkColor color) {
   *         this->clear(SkColor4f::FromColor(color));
   *     }
   * ```
   */
  private fun clear(color: SkColor) {
    TODO("Implement clear")
  }

  /**
   * C++ original:
   * ```cpp
   * void clear(const SkColor4f& color) {
   *         this->drawColor(color, SkBlendMode::kSrc);
   *     }
   * ```
   */
  private fun clear(color: SkColor4f) {
    TODO("Implement clear")
  }

  /**
   * C++ original:
   * ```cpp
   * void discard() { this->onDiscard(); }
   * ```
   */
  private fun discard() {
    TODO("Implement discard")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::drawPaint(const SkPaint& paint) {
   *     TRACE_EVENT0("skia", TRACE_FUNC);
   *     this->onDrawPaint(paint);
   * }
   * ```
   */
  private fun drawPaint(paint: SkPaint) {
    TODO("Implement drawPaint")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::drawPoints(PointMode mode, SkSpan<const SkPoint> pts, const SkPaint& paint) {
   *     TRACE_EVENT0("skia", TRACE_FUNC);
   *     if (!pts.empty()) {
   *         this->onDrawPoints(mode, pts.size(), pts.data(), paint);
   *     }
   * }
   * ```
   */
  private fun drawPoints(
    mode: PointMode,
    pts: SkSpan<SkPoint>,
    paint: SkPaint,
  ) {
    TODO("Implement drawPoints")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::drawPoint(SkScalar x, SkScalar y, const SkPaint& paint) {
   *     const SkPoint pt[1] = {{ x, y }};
   *     this->drawPoints(kPoints_PointMode, pt, paint);
   * }
   * ```
   */
  private fun drawPoint(
    x: SkScalar,
    y: SkScalar,
    paint: SkPaint,
  ) {
    TODO("Implement drawPoint")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawPoint(SkPoint p, const SkPaint& paint) {
   *         this->drawPoint(p.x(), p.y(), paint);
   *     }
   * ```
   */
  private fun drawPoint(p: SkPoint, paint: SkPaint) {
    TODO("Implement drawPoint")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::drawLine(SkScalar x0, SkScalar y0, SkScalar x1, SkScalar y1, const SkPaint& paint) {
   *     SkPoint pts[2];
   *     pts[0].set(x0, y0);
   *     pts[1].set(x1, y1);
   *     this->drawPoints(kLines_PointMode, pts, paint);
   * }
   * ```
   */
  private fun drawLine(
    x0: SkScalar,
    y0: SkScalar,
    x1: SkScalar,
    y1: SkScalar,
    paint: SkPaint,
  ) {
    TODO("Implement drawLine")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawLine(SkPoint p0, SkPoint p1, const SkPaint& paint) {
   *         this->drawLine(p0.x(), p0.y(), p1.x(), p1.y(), paint);
   *     }
   * ```
   */
  private fun drawLine(
    p0: SkPoint,
    p1: SkPoint,
    paint: SkPaint,
  ) {
    TODO("Implement drawLine")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::drawRect(const SkRect& r, const SkPaint& paint) {
   *     TRACE_EVENT0("skia", TRACE_FUNC);
   *     // To avoid redundant logic in our culling code and various backends, we always sort rects
   *     // before passing them along.
   *     this->onDrawRect(r.makeSorted(), paint);
   * }
   * ```
   */
  private fun drawRect(rect: SkRect, paint: SkPaint) {
    TODO("Implement drawRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawIRect(const SkIRect& rect, const SkPaint& paint) {
   *         SkRect r;
   *         r.set(rect);    // promotes the ints to scalars
   *         this->drawRect(r, paint);
   *     }
   * ```
   */
  private fun drawIRect(rect: SkIRect, paint: SkPaint) {
    TODO("Implement drawIRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::drawRegion(const SkRegion& region, const SkPaint& paint) {
   *     TRACE_EVENT0("skia", TRACE_FUNC);
   *     if (region.isEmpty()) {
   *         return;
   *     }
   *
   *     if (region.isRect()) {
   *         return this->drawIRect(region.getBounds(), paint);
   *     }
   *
   *     this->onDrawRegion(region, paint);
   * }
   * ```
   */
  private fun drawRegion(region: SkRegion, paint: SkPaint) {
    TODO("Implement drawRegion")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::drawOval(const SkRect& r, const SkPaint& paint) {
   *     TRACE_EVENT0("skia", TRACE_FUNC);
   *     // To avoid redundant logic in our culling code and various backends, we always sort rects
   *     // before passing them along.
   *     this->onDrawOval(r.makeSorted(), paint);
   * }
   * ```
   */
  private fun drawOval(oval: SkRect, paint: SkPaint) {
    TODO("Implement drawOval")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::drawRRect(const SkRRect& rrect, const SkPaint& paint) {
   *     TRACE_EVENT0("skia", TRACE_FUNC);
   *     this->onDrawRRect(rrect, paint);
   * }
   * ```
   */
  private fun drawRRect(rrect: SkRRect, paint: SkPaint) {
    TODO("Implement drawRRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::drawDRRect(const SkRRect& outer, const SkRRect& inner,
   *                           const SkPaint& paint) {
   *     TRACE_EVENT0("skia", TRACE_FUNC);
   *     if (outer.isEmpty()) {
   *         return;
   *     }
   *     if (inner.isEmpty()) {
   *         this->drawRRect(outer, paint);
   *         return;
   *     }
   *
   *     // We don't have this method (yet), but technically this is what we should
   *     // be able to return ...
   *     // if (!outer.contains(inner))) {
   *     //
   *     // For now at least check for containment of bounds
   *     if (!outer.getBounds().contains(inner.getBounds())) {
   *         return;
   *     }
   *
   *     this->onDrawDRRect(outer, inner, paint);
   * }
   * ```
   */
  private fun drawDRRect(
    outer: SkRRect,
    `inner`: SkRRect,
    paint: SkPaint,
  ) {
    TODO("Implement drawDRRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::drawCircle(SkScalar cx, SkScalar cy, SkScalar radius, const SkPaint& paint) {
   *     if (radius < 0) {
   *         radius = 0;
   *     }
   *
   *     SkRect  r;
   *     r.setLTRB(cx - radius, cy - radius, cx + radius, cy + radius);
   *     this->drawOval(r, paint);
   * }
   * ```
   */
  private fun drawCircle(
    cx: SkScalar,
    cy: SkScalar,
    radius: SkScalar,
    paint: SkPaint,
  ) {
    TODO("Implement drawCircle")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawCircle(SkPoint center, SkScalar radius, const SkPaint& paint) {
   *         this->drawCircle(center.x(), center.y(), radius, paint);
   *     }
   * ```
   */
  private fun drawCircle(
    center: SkPoint,
    radius: SkScalar,
    paint: SkPaint,
  ) {
    TODO("Implement drawCircle")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::drawArc(const SkRect& oval, SkScalar startAngle,
   *                        SkScalar sweepAngle, bool useCenter,
   *                        const SkPaint& paint) {
   *     TRACE_EVENT0("skia", TRACE_FUNC);
   *     if (oval.isEmpty() || !sweepAngle) {
   *         return;
   *     }
   *     this->onDrawArc(oval, startAngle, sweepAngle, useCenter, paint);
   * }
   * ```
   */
  private fun drawArc(
    oval: SkRect,
    startAngle: SkScalar,
    sweepAngle: SkScalar,
    useCenter: Boolean,
    paint: SkPaint,
  ) {
    TODO("Implement drawArc")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawArc(const SkArc& arc, const SkPaint& paint) {
   *         this->drawArc(arc.fOval, arc.fStartAngle, arc.fSweepAngle, arc.isWedge(), paint);
   *     }
   * ```
   */
  private fun drawArc(arc: SkArc, paint: SkPaint) {
    TODO("Implement drawArc")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::drawRoundRect(const SkRect& r, SkScalar rx, SkScalar ry,
   *                              const SkPaint& paint) {
   *     if (rx > 0 && ry > 0) {
   *         SkRRect rrect;
   *         rrect.setRectXY(r, rx, ry);
   *         this->drawRRect(rrect, paint);
   *     } else {
   *         this->drawRect(r, paint);
   *     }
   * }
   * ```
   */
  private fun drawRoundRect(
    rect: SkRect,
    rx: SkScalar,
    ry: SkScalar,
    paint: SkPaint,
  ) {
    TODO("Implement drawRoundRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::drawPath(const SkPath& path, const SkPaint& paint) {
   *     TRACE_EVENT0("skia", TRACE_FUNC);
   *     this->onDrawPath(path, paint);
   * }
   * ```
   */
  private fun drawPath(path: SkPath, paint: SkPaint) {
    TODO("Implement drawPath")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawImage(const SkImage* image, SkScalar left, SkScalar top) {
   *         this->drawImage(image, left, top, SkSamplingOptions(), nullptr);
   *     }
   * ```
   */
  private fun drawImage(
    image: SkImage?,
    left: SkScalar,
    top: SkScalar,
  ) {
    TODO("Implement drawImage")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawImage(const sk_sp<SkImage>& image, SkScalar left, SkScalar top) {
   *         this->drawImage(image.get(), left, top, SkSamplingOptions(), nullptr);
   *     }
   * ```
   */
  private fun drawImage(
    image: SkSp<SkImage>,
    left: SkScalar,
    top: SkScalar,
  ) {
    TODO("Implement drawImage")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawImage(const SkImage*, SkScalar x, SkScalar y, const SkSamplingOptions&,
   *                    const SkPaint* = nullptr)
   * ```
   */
  private fun drawImage(
    param0: SkImage?,
    x: SkScalar,
    y: SkScalar,
    param3: SkSamplingOptions,
    param4: SkPaint? = TODO(),
  ) {
    TODO("Implement drawImage")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawImage(const sk_sp<SkImage>& image, SkScalar x, SkScalar y,
   *                    const SkSamplingOptions& sampling, const SkPaint* paint = nullptr) {
   *         this->drawImage(image.get(), x, y, sampling, paint);
   *     }
   * ```
   */
  private fun drawImage(
    image: SkSp<SkImage>,
    x: SkScalar,
    y: SkScalar,
    sampling: SkSamplingOptions,
    paint: SkPaint? = TODO(),
  ) {
    TODO("Implement drawImage")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawImageRect(const SkImage*, const SkRect& src, const SkRect& dst,
   *                        const SkSamplingOptions&, const SkPaint*, SrcRectConstraint)
   * ```
   */
  private fun drawImageRect(
    param0: SkImage?,
    src: SkRect,
    dst: SkRect,
    param3: SkSamplingOptions,
    param4: SkPaint?,
    param5: SrcRectConstraint,
  ) {
    TODO("Implement drawImageRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawImageRect(const SkImage*, const SkRect& dst, const SkSamplingOptions&,
   *                        const SkPaint* = nullptr)
   * ```
   */
  private fun drawImageRect(
    param0: SkImage?,
    dst: SkRect,
    param2: SkSamplingOptions,
    param3: SkPaint? = TODO(),
  ) {
    TODO("Implement drawImageRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawImageRect(const sk_sp<SkImage>& image, const SkRect& src, const SkRect& dst,
   *                        const SkSamplingOptions& sampling, const SkPaint* paint,
   *                        SrcRectConstraint constraint) {
   *         this->drawImageRect(image.get(), src, dst, sampling, paint, constraint);
   *     }
   * ```
   */
  private fun drawImageRect(
    image: SkSp<SkImage>,
    src: SkRect,
    dst: SkRect,
    sampling: SkSamplingOptions,
    paint: SkPaint?,
    constraint: SrcRectConstraint,
  ) {
    TODO("Implement drawImageRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawImageRect(const sk_sp<SkImage>& image, const SkRect& dst,
   *                        const SkSamplingOptions& sampling, const SkPaint* paint = nullptr) {
   *         this->drawImageRect(image.get(), dst, sampling, paint);
   *     }
   * ```
   */
  private fun drawImageRect(
    image: SkSp<SkImage>,
    dst: SkRect,
    sampling: SkSamplingOptions,
    paint: SkPaint? = TODO(),
  ) {
    TODO("Implement drawImageRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::drawImageNine(const SkImage* image, const SkIRect& center, const SkRect& dst,
   *                              SkFilterMode filter, const SkPaint* paint) {
   *     RETURN_ON_NULL(image);
   *
   *     const int xdivs[] = {center.fLeft, center.fRight};
   *     const int ydivs[] = {center.fTop, center.fBottom};
   *
   *     Lattice lat;
   *     lat.fXDivs = xdivs;
   *     lat.fYDivs = ydivs;
   *     lat.fRectTypes = nullptr;
   *     lat.fXCount = lat.fYCount = 2;
   *     lat.fBounds = nullptr;
   *     lat.fColors = nullptr;
   *     this->drawImageLattice(image, lat, dst, filter, paint);
   * }
   * ```
   */
  private fun drawImageNine(
    image: SkImage?,
    center: SkIRect,
    dst: SkRect,
    filter: SkFilterMode,
    paint: SkPaint? = TODO(),
  ) {
    TODO("Implement drawImageNine")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::drawImageLattice(const SkImage* image, const Lattice& lattice, const SkRect& dst,
   *                                 SkFilterMode filter, const SkPaint* paint) {
   *     TRACE_EVENT0("skia", TRACE_FUNC);
   *     RETURN_ON_NULL(image);
   *     if (dst.isEmpty()) {
   *         return;
   *     }
   *
   *     SkIRect bounds;
   *     Lattice latticePlusBounds = lattice;
   *     if (!latticePlusBounds.fBounds) {
   *         bounds = SkIRect::MakeWH(image->width(), image->height());
   *         latticePlusBounds.fBounds = &bounds;
   *     }
   *
   *     SkPaint latticePaint = clean_paint_for_lattice(paint);
   *     if (SkLatticeIter::Valid(image->width(), image->height(), latticePlusBounds)) {
   *         this->onDrawImageLattice2(image, latticePlusBounds, dst, filter, &latticePaint);
   *     } else {
   *         this->drawImageRect(image, SkRect::MakeIWH(image->width(), image->height()), dst,
   *                             SkSamplingOptions(filter), &latticePaint, kStrict_SrcRectConstraint);
   *     }
   * }
   * ```
   */
  private fun drawImageLattice(
    image: SkImage?,
    lattice: Lattice,
    dst: SkRect,
    filter: SkFilterMode,
    paint: SkPaint? = TODO(),
  ) {
    TODO("Implement drawImageLattice")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawImageLattice(const SkImage* image, const Lattice& lattice, const SkRect& dst) {
   *         this->drawImageLattice(image, lattice, dst, SkFilterMode::kNearest, nullptr);
   *     }
   * ```
   */
  private fun drawImageLattice(
    image: SkImage?,
    lattice: Lattice,
    dst: SkRect,
  ) {
    TODO("Implement drawImageLattice")
  }

  /**
   * C++ original:
   * ```cpp
   * void experimental_DrawEdgeAAQuad(const SkRect& rect, const SkPoint clip[4], QuadAAFlags aaFlags,
   *                                      const SkColor4f& color, SkBlendMode mode)
   * ```
   */
  private fun experimentalDrawEdgeAAQuad(
    rect: SkRect,
    clip: Array<SkPoint>,
    aaFlags: QuadAAFlags,
    color: SkColor4f,
    mode: SkBlendMode,
  ) {
    TODO("Implement experimentalDrawEdgeAAQuad")
  }

  /**
   * C++ original:
   * ```cpp
   * void experimental_DrawEdgeAAQuad(const SkRect& rect, const SkPoint clip[4], QuadAAFlags aaFlags,
   *                                      SkColor color, SkBlendMode mode) {
   *         this->experimental_DrawEdgeAAQuad(rect, clip, aaFlags, SkColor4f::FromColor(color), mode);
   *     }
   * ```
   */
  private fun experimentalDrawEdgeAAQuad(
    rect: SkRect,
    clip: Array<SkPoint>,
    aaFlags: QuadAAFlags,
    color: SkColor,
    mode: SkBlendMode,
  ) {
    TODO("Implement experimentalDrawEdgeAAQuad")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::experimental_DrawEdgeAAImageSet(const ImageSetEntry imageSet[], int cnt,
   *                                                const SkPoint dstClips[],
   *                                                const SkMatrix preViewMatrices[],
   *                                                const SkSamplingOptions& sampling,
   *                                                const SkPaint* paint,
   *                                                SrcRectConstraint constraint) {
   *     TRACE_EVENT0("skia", TRACE_FUNC);
   *     // Route single, rectangular quads to drawImageRect() to take advantage of image filter
   *     // optimizations that avoid a layer.
   *     if (paint && (paint->getImageFilter() || paint->getMaskFilter()) && cnt == 1) {
   *         const auto& entry = imageSet[0];
   *         // If the preViewMatrix is skipped or a positive-scale + translate matrix, we can apply it
   *         // to the entry's dstRect w/o changing output behavior.
   *         const bool canMapDstRect = entry.fMatrixIndex < 0 ||
   *             (preViewMatrices[entry.fMatrixIndex].isScaleTranslate() &&
   *              preViewMatrices[entry.fMatrixIndex].getScaleX() > 0.f &&
   *              preViewMatrices[entry.fMatrixIndex].getScaleY() > 0.f);
   *         if (!entry.fHasClip && canMapDstRect) {
   *             SkRect dst = entry.fDstRect;
   *             if (entry.fMatrixIndex >= 0) {
   *                 preViewMatrices[entry.fMatrixIndex].mapRect(&dst);
   *             }
   *             this->drawImageRect(entry.fImage.get(), entry.fSrcRect, dst,
   *                                 sampling, paint, constraint);
   *             return;
   *         } // Else the entry is doing more than can be represented by drawImageRect
   *     } // Else no filter, or many entries that should be filtered together
   *     this->onDrawEdgeAAImageSet2(imageSet, cnt, dstClips, preViewMatrices, sampling, paint,
   *                                 constraint);
   * }
   * ```
   */
  private fun experimentalDrawEdgeAAImageSet(
    imageSet: Array<undefined.ImageSetEntry>,
    cnt: Int,
    dstClips: Array<SkPoint>,
    preViewMatrices: Array<SkMatrix>,
    sampling: SkSamplingOptions,
    paint: SkPaint? = TODO(),
    constraint: SrcRectConstraint = TODO(),
  ) {
    TODO("Implement experimentalDrawEdgeAAImageSet")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::drawSimpleText(const void* text, size_t byteLength, SkTextEncoding encoding,
   *                               SkScalar x, SkScalar y, const SkFont& font, const SkPaint& paint) {
   *     TRACE_EVENT0("skia", TRACE_FUNC);
   *     if (byteLength) {
   *         sk_msan_assert_initialized(text, SkTAddOffset<const void>(text, byteLength));
   *         const sktext::GlyphRunList& glyphRunList =
   *             fScratchGlyphRunBuilder->textToGlyphRunList(
   *                     font, paint, text, byteLength, {x, y}, encoding);
   *         if (!glyphRunList.empty()) {
   *             this->onDrawGlyphRunList(glyphRunList, paint);
   *         }
   *     }
   * }
   * ```
   */
  private fun drawSimpleText(
    text: Unit?,
    byteLength: ULong,
    encoding: SkTextEncoding,
    x: SkScalar,
    y: SkScalar,
    font: SkFont,
    paint: SkPaint,
  ) {
    TODO("Implement drawSimpleText")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawString(const char str[], SkScalar x, SkScalar y, const SkFont& font,
   *                     const SkPaint& paint) {
   *         this->drawSimpleText(str, strlen(str), SkTextEncoding::kUTF8, x, y, font, paint);
   *     }
   * ```
   */
  private fun drawString(
    str: CharArray,
    x: SkScalar,
    y: SkScalar,
    font: SkFont,
    paint: SkPaint,
  ) {
    TODO("Implement drawString")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawString(const SkString& str, SkScalar x, SkScalar y, const SkFont& font,
   *                     const SkPaint& paint) {
   *         this->drawSimpleText(str.c_str(), str.size(), SkTextEncoding::kUTF8, x, y, font, paint);
   *     }
   * ```
   */
  private fun drawString(
    str: String,
    x: SkScalar,
    y: SkScalar,
    font: SkFont,
    paint: SkPaint,
  ) {
    TODO("Implement drawString")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::drawGlyphs(SkSpan<const SkGlyphID> glyphs, SkSpan<const SkPoint> positions,
   *                           SkSpan<const uint32_t> clusters, SkSpan<const char> utf8text,
   *                           SkPoint origin, const SkFont& font, const SkPaint& paint) {
   *     if (glyphs.empty()) { return; }
   *
   *     sktext::GlyphRun glyphRun {
   *             font,
   *             positions,
   *             glyphs,
   *             utf8text,
   *             clusters,
   *             SkSpan<SkVector>()
   *     };
   *
   *     sktext::GlyphRunList glyphRunList = fScratchGlyphRunBuilder->makeGlyphRunList(
   *             glyphRun, paint, origin);
   *     this->onDrawGlyphRunList(glyphRunList, paint);
   * }
   * ```
   */
  private fun drawGlyphs(
    glyphs: SkSpan<SkGlyphID>,
    positions: SkSpan<SkPoint>,
    clusters: SkSpan<UInt>,
    utf8text: SkSpan<Char>,
    origin: SkPoint,
    font: SkFont,
    paint: SkPaint,
  ) {
    TODO("Implement drawGlyphs")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::drawGlyphs(SkSpan<const SkGlyphID> glyphs, SkSpan<const SkPoint> positions,
   *                           SkPoint origin, const SkFont& font, const SkPaint& paint) {
   *     if (glyphs.empty()) { return; }
   *
   *     sktext::GlyphRun glyphRun {
   *         font,
   *         positions,
   *         glyphs,
   *         SkSpan<const char>(),
   *         SkSpan<const uint32_t>(),
   *         SkSpan<SkVector>()
   *     };
   *
   *     sktext::GlyphRunList glyphRunList = fScratchGlyphRunBuilder->makeGlyphRunList(
   *             glyphRun, paint, origin);
   *     this->onDrawGlyphRunList(glyphRunList, paint);
   * }
   * ```
   */
  private fun drawGlyphs(
    glyphs: SkSpan<SkGlyphID>,
    positions: SkSpan<SkPoint>,
    origin: SkPoint,
    font: SkFont,
    paint: SkPaint,
  ) {
    TODO("Implement drawGlyphs")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::drawGlyphsRSXform(SkSpan<const SkGlyphID> glyphs, SkSpan<const SkRSXform> xforms,
   *                                  SkPoint origin, const SkFont& font, const SkPaint& paint) {
   *     if (glyphs.empty()) { return; }
   *
   *     auto [positions, rotateScales] =
   *             fScratchGlyphRunBuilder->convertRSXForm(xforms);
   *
   *     sktext::GlyphRun glyphRun {
   *             font,
   *             positions,
   *             glyphs,
   *             SkSpan<const char>(),
   *             SkSpan<const uint32_t>(),
   *             rotateScales
   *     };
   *     sktext::GlyphRunList glyphRunList = fScratchGlyphRunBuilder->makeGlyphRunList(
   *             glyphRun, paint, origin);
   *     this->onDrawGlyphRunList(glyphRunList, paint);
   * }
   * ```
   */
  private fun drawGlyphsRSXform(
    glyphs: SkSpan<SkGlyphID>,
    xforms: SkSpan<SkRSXform>,
    origin: SkPoint,
    font: SkFont,
    paint: SkPaint,
  ) {
    TODO("Implement drawGlyphsRSXform")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawTextBlob(const SkTextBlob* blob, SkScalar x, SkScalar y, const SkPaint& paint)
   * ```
   */
  private fun drawTextBlob(
    blob: SkTextBlob?,
    x: SkScalar,
    y: SkScalar,
    paint: SkPaint,
  ) {
    TODO("Implement drawTextBlob")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawTextBlob(const sk_sp<SkTextBlob>& blob, SkScalar x, SkScalar y, const SkPaint& paint) {
   *         this->drawTextBlob(blob.get(), x, y, paint);
   *     }
   * ```
   */
  private fun drawTextBlob(
    blob: SkSp<SkTextBlob>,
    x: SkScalar,
    y: SkScalar,
    paint: SkPaint,
  ) {
    TODO("Implement drawTextBlob")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawPicture(const SkPicture* picture) {
   *         this->drawPicture(picture, nullptr, nullptr);
   *     }
   * ```
   */
  private fun drawPicture(picture: SkPicture?) {
    TODO("Implement drawPicture")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawPicture(const sk_sp<SkPicture>& picture) {
   *         this->drawPicture(picture.get());
   *     }
   * ```
   */
  private fun drawPicture(picture: SkSp<SkPicture>) {
    TODO("Implement drawPicture")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawPicture(const SkPicture* picture, const SkMatrix* matrix, const SkPaint* paint)
   * ```
   */
  private fun drawPicture(
    picture: SkPicture?,
    matrix: SkMatrix?,
    paint: SkPaint?,
  ) {
    TODO("Implement drawPicture")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawPicture(const sk_sp<SkPicture>& picture, const SkMatrix* matrix,
   *                      const SkPaint* paint) {
   *         this->drawPicture(picture.get(), matrix, paint);
   *     }
   * ```
   */
  private fun drawPicture(
    picture: SkSp<SkPicture>,
    matrix: SkMatrix?,
    paint: SkPaint?,
  ) {
    TODO("Implement drawPicture")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawVertices(const SkVertices* vertices, SkBlendMode mode, const SkPaint& paint)
   * ```
   */
  private fun drawVertices(
    vertices: SkVertices?,
    mode: SkBlendMode,
    paint: SkPaint,
  ) {
    TODO("Implement drawVertices")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawVertices(const sk_sp<SkVertices>& vertices, SkBlendMode mode, const SkPaint& paint)
   * ```
   */
  private fun drawVertices(
    vertices: SkSp<SkVertices>,
    mode: SkBlendMode,
    paint: SkPaint,
  ) {
    TODO("Implement drawVertices")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::drawMesh(const SkMesh& mesh, sk_sp<SkBlender> blender, const SkPaint& paint) {
   *     TRACE_EVENT0("skia", TRACE_FUNC);
   *     if (!blender) {
   *         blender = SkBlender::Mode(SkBlendMode::kModulate);
   *     }
   *     this->onDrawMesh(mesh, std::move(blender), paint);
   * }
   * ```
   */
  private fun drawMesh(
    mesh: SkMesh,
    blender: SkSp<SkBlender>,
    paint: SkPaint,
  ) {
    TODO("Implement drawMesh")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::drawPatch(const SkPoint cubics[12], const SkColor colors[4],
   *                          const SkPoint texCoords[4], SkBlendMode bmode,
   *                          const SkPaint& paint) {
   *     TRACE_EVENT0("skia", TRACE_FUNC);
   *     if (nullptr == cubics) {
   *         return;
   *     }
   *
   *     this->onDrawPatch(cubics, colors, texCoords, bmode, paint);
   * }
   * ```
   */
  private fun drawPatch(
    cubics: Array<SkPoint>,
    colors: Array<SkColor>,
    texCoords: Array<SkPoint>,
    mode: SkBlendMode,
    paint: SkPaint,
  ) {
    TODO("Implement drawPatch")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::drawAtlas(const SkImage* atlas, SkSpan<const SkRSXform> xform,
   *                          SkSpan<const SkRect> tex, SkSpan<const SkColor> colors, SkBlendMode mode,
   *                          const SkSamplingOptions& sampling, const SkRect* cull,
   *                          const SkPaint* paint) {
   *     TRACE_EVENT0("skia", TRACE_FUNC);
   *     RETURN_ON_NULL(atlas);
   *     size_t count = std::min(xform.size(), tex.size());
   *     if (!colors.empty()) {
   *         count = std::min(count, colors.size());
   *     }
   *     if (count == 0) {
   *         return;
   *     }
   *     this->onDrawAtlas2(atlas, xform.data(), tex.data(), colors.data(), count, mode, sampling,
   *                        cull, paint);
   * }
   * ```
   */
  private fun drawAtlas(
    atlas: SkImage?,
    xform: SkSpan<SkRSXform>,
    tex: SkSpan<SkRect>,
    colors: SkSpan<SkColor>,
    mode: SkBlendMode,
    sampling: SkSamplingOptions,
    cullRect: SkRect?,
    paint: SkPaint?,
  ) {
    TODO("Implement drawAtlas")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::drawDrawable(SkDrawable* dr, const SkMatrix* matrix) {
   * #ifndef SK_BUILD_FOR_ANDROID_FRAMEWORK
   *     TRACE_EVENT0("skia", TRACE_FUNC);
   * #endif
   *     RETURN_ON_NULL(dr);
   *     if (matrix && matrix->isIdentity()) {
   *         matrix = nullptr;
   *     }
   *     this->onDrawDrawable(dr, matrix);
   * }
   * ```
   */
  private fun drawDrawable(drawable: SkDrawable?, matrix: SkMatrix? = TODO()) {
    TODO("Implement drawDrawable")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::drawDrawable(SkDrawable* dr, SkScalar x, SkScalar y) {
   * #ifndef SK_BUILD_FOR_ANDROID_FRAMEWORK
   *     TRACE_EVENT0("skia", TRACE_FUNC);
   * #endif
   *     RETURN_ON_NULL(dr);
   *     if (x || y) {
   *         SkMatrix matrix = SkMatrix::Translate(x, y);
   *         this->onDrawDrawable(dr, &matrix);
   *     } else {
   *         this->onDrawDrawable(dr, nullptr);
   *     }
   * }
   * ```
   */
  private fun drawDrawable(
    drawable: SkDrawable?,
    x: SkScalar,
    y: SkScalar,
  ) {
    TODO("Implement drawDrawable")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawAnnotation(const SkRect& rect, const char key[], SkData* value)
   * ```
   */
  private fun drawAnnotation(
    rect: SkRect,
    key: CharArray,
    `value`: SkData?,
  ) {
    TODO("Implement drawAnnotation")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawAnnotation(const SkRect& rect, const char key[], const sk_sp<SkData>& value) {
   *         this->drawAnnotation(rect, key, value.get());
   *     }
   * ```
   */
  private fun drawAnnotation(
    rect: SkRect,
    key: CharArray,
    `value`: SkSp<SkData>,
  ) {
    TODO("Implement drawAnnotation")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkCanvas::isClipEmpty() const {
   *     return this->topDevice()->isClipEmpty();
   * }
   * ```
   */
  public open fun isClipEmpty(): Boolean {
    TODO("Implement isClipEmpty")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkCanvas::isClipRect() const {
   *     return this->topDevice()->isClipRect();
   * }
   * ```
   */
  public open fun isClipRect(): Boolean {
    TODO("Implement isClipRect")
  }

  /**
   * C++ original:
   * ```cpp
   * SkM44 SkCanvas::getLocalToDevice() const {
   *     return fMCRec->fMatrix;
   * }
   * ```
   */
  private fun getLocalToDevice(): Int {
    TODO("Implement getLocalToDevice")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix getLocalToDeviceAs3x3() const {
   *         return this->getLocalToDevice().asM33();
   *     }
   * ```
   */
  private fun getLocalToDeviceAs3x3(): Int {
    TODO("Implement getLocalToDeviceAs3x3")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix SkCanvas::getTotalMatrix() const {
   *     return fMCRec->fMatrix.asM33();
   * }
   * ```
   */
  private fun getTotalMatrix(): Int {
    TODO("Implement getTotalMatrix")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::temporary_internal_getRgnClip(SkRegion* rgn) {
   *     rgn->setEmpty();
   *     SkDevice* device = this->topDevice();
   *     if (device && device->isPixelAlignedToGlobal()) {
   *         device->android_utils_clipAsRgn(rgn);
   *         SkIPoint origin = device->getOrigin();
   *         if (origin.x() | origin.y()) {
   *             rgn->translate(origin.x(), origin.y());
   *         }
   *     }
   * }
   * ```
   */
  private fun temporaryInternalGetRgnClip(region: SkRegion?) {
    TODO("Implement temporaryInternalGetRgnClip")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::private_draw_shadow_rec(const SkPath& path, const SkDrawShadowRec& rec) {
   *     TRACE_EVENT0("skia", TRACE_FUNC);
   *     this->onDrawShadowRec(path, rec);
   * }
   * ```
   */
  private fun privateDrawShadowRec(path: SkPath, rec: SkDrawShadowRec) {
    TODO("Implement privateDrawShadowRec")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkSurface> SkCanvas::onNewSurface(const SkImageInfo& info, const SkSurfaceProps& props) {
   *     return this->rootDevice()->makeSurface(info, props);
   * }
   * ```
   */
  protected open fun onNewSurface(info: SkImageInfo, props: SkSurfaceProps): Int {
    TODO("Implement onNewSurface")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkCanvas::onPeekPixels(SkPixmap* pmap) {
   *     return this->rootDevice()->peekPixels(pmap);
   * }
   * ```
   */
  protected open fun onPeekPixels(pixmap: SkPixmap?): Boolean {
    TODO("Implement onPeekPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkCanvas::onAccessTopLayerPixels(SkPixmap* pmap) {
   *     return this->topDevice()->accessPixels(pmap);
   * }
   * ```
   */
  protected open fun onAccessTopLayerPixels(pixmap: SkPixmap?): Boolean {
    TODO("Implement onAccessTopLayerPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * SkImageInfo SkCanvas::onImageInfo() const {
   *     return this->rootDevice()->imageInfo();
   * }
   * ```
   */
  protected open fun onImageInfo(): Int {
    TODO("Implement onImageInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkCanvas::onGetProps(SkSurfaceProps* props, bool top) const {
   *     if (props) {
   *         *props = top ? topDevice()->surfaceProps() : fProps;
   *     }
   *     return true;
   * }
   * ```
   */
  protected open fun onGetProps(props: SkSurfaceProps?, top: Boolean): Boolean {
    TODO("Implement onGetProps")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void willSave() {}
   * ```
   */
  protected open fun willSave() {
    TODO("Implement willSave")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual SaveLayerStrategy getSaveLayerStrategy(const SaveLayerRec& ) {
   *         return kFullLayer_SaveLayerStrategy;
   *     }
   * ```
   */
  protected open fun getSaveLayerStrategy(param0: SaveLayerRec): SaveLayerStrategy {
    TODO("Implement getSaveLayerStrategy")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool onDoSaveBehind(const SkRect*) { return true; }
   * ```
   */
  protected open fun onDoSaveBehind(param0: SkRect?): Boolean {
    TODO("Implement onDoSaveBehind")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void willRestore() {}
   * ```
   */
  protected open fun willRestore() {
    TODO("Implement willRestore")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void didRestore() {}
   * ```
   */
  protected open fun didRestore() {
    TODO("Implement didRestore")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void didConcat44(const SkM44&) {}
   * ```
   */
  protected open fun didConcat44(param0: SkM44) {
    TODO("Implement didConcat44")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void didSetM44(const SkM44&) {}
   * ```
   */
  protected open fun didSetM44(param0: SkM44) {
    TODO("Implement didSetM44")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void didTranslate(SkScalar, SkScalar) {}
   * ```
   */
  protected open fun didTranslate(param0: SkScalar, param1: SkScalar) {
    TODO("Implement didTranslate")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void didScale(SkScalar, SkScalar) {}
   * ```
   */
  protected open fun didScale(param0: SkScalar, param1: SkScalar) {
    TODO("Implement didScale")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::onDrawPaint(const SkPaint& paint) {
   *     this->internalDrawPaint(paint);
   * }
   * ```
   */
  protected open fun onDrawPaint(paint: SkPaint) {
    TODO("Implement onDrawPaint")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::onDrawBehind(const SkPaint& paint) {
   *     SkDevice* dev = this->topDevice();
   *     if (!dev) {
   *         return;
   *     }
   *
   *     SkIRect bounds;
   *     SkDeque::Iter iter(fMCStack, SkDeque::Iter::kBack_IterStart);
   *     for (;;) {
   *         const MCRec* rec = (const MCRec*)iter.prev();
   *         if (!rec) {
   *             return; // no backimages, so nothing to draw
   *         }
   *         if (rec->fBackImage) {
   *             // drawBehind should only have been called when the saveBehind record is active;
   *             // if this fails, it means a real saveLayer was made w/o being restored first.
   *             SkASSERT(dev == rec->fDevice);
   *             bounds = SkIRect::MakeXYWH(rec->fBackImage->fLoc.fX, rec->fBackImage->fLoc.fY,
   *                                        rec->fBackImage->fImage->width(),
   *                                        rec->fBackImage->fImage->height());
   *             break;
   *         }
   *     }
   *
   *     // The backimage location (and thus bounds) were defined in the device's space, so mark it
   *     // as a clip. We use a clip instead of just drawing a rect in case the paint has an image
   *     // filter on it (which is applied before any auto-layer so the filter is clipped).
   *     dev->pushClipStack();
   *     {
   *         // We also have to temporarily whack the device matrix since clipRegion is affected by the
   *         // global-to-device matrix and clipRect is affected by the local-to-device.
   *         SkAutoDeviceTransformRestore adtr(dev, SkM44());
   *         dev->clipRect(SkRect::Make(bounds), SkClipOp::kIntersect, /* aa */ false);
   *         // ~adtr will reset the local-to-device matrix so that drawPaint() shades correctly.
   *     }
   *
   *     auto layer = this->aboutToDraw(paint);
   *     if (layer) {
   *         this->topDevice()->drawPaint(layer->paint());
   *     }
   *
   *     dev->popClipStack();
   * }
   * ```
   */
  protected open fun onDrawBehind(paint: SkPaint) {
    TODO("Implement onDrawBehind")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::onDrawRect(const SkRect& r, const SkPaint& paint) {
   *     SkASSERT(r.isSorted());
   *     if (this->internalQuickReject(r, paint)) {
   *         return;
   *     }
   *
   *     std::optional<AutoLayerForImageFilter> layer;
   *     constexpr PredrawFlags kPredrawFlags = PredrawFlags::kCheckForOverwrite;
   *
   *     if (const SkBlurMaskFilterImpl* blurMaskFilter = this->canAttemptBlurredRRectDraw(paint)) {
   *         // Returns a layer if a blurred draw was unsuccessful.
   *         layer = this->attemptBlurredRRectDraw(
   *                 SkRRect::MakeRect(r), blurMaskFilter, paint, kPredrawFlags);
   *     } else {
   *         layer = this->aboutToDraw(paint, &r, kPredrawFlags);
   *     }
   *
   *     if (layer) {
   *         this->topDevice()->drawRect(r, layer->paint());
   *     }
   * }
   * ```
   */
  protected open fun onDrawRect(rect: SkRect, paint: SkPaint) {
    TODO("Implement onDrawRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::onDrawRRect(const SkRRect& rrect, const SkPaint& paint) {
   *     const SkRect& bounds = rrect.getBounds();
   *
   *     // Delegating to simpler draw operations
   *     if (rrect.isRect()) {
   *         // call the non-virtual version
   *         this->SkCanvas::drawRect(bounds, paint);
   *         return;
   *     } else if (rrect.isOval()) {
   *         // call the non-virtual version
   *         this->SkCanvas::drawOval(bounds, paint);
   *         return;
   *     }
   *
   *     if (this->internalQuickReject(bounds, paint)) {
   *         return;
   *     }
   *
   *     std::optional<AutoLayerForImageFilter> layer;
   *
   *     if (const SkBlurMaskFilterImpl* blurMaskFilter = this->canAttemptBlurredRRectDraw(paint)) {
   *         // Returns a layer if a blurred draw was unsuccessful.
   *         layer = this->attemptBlurredRRectDraw(rrect, blurMaskFilter, paint, PredrawFlags::kNone);
   *     } else {
   *         layer = this->aboutToDraw(paint, &bounds);
   *     }
   *
   *     if (layer) {
   *         this->topDevice()->drawRRect(rrect, layer->paint());
   *     }
   * }
   * ```
   */
  protected open fun onDrawRRect(rrect: SkRRect, paint: SkPaint) {
    TODO("Implement onDrawRRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::onDrawDRRect(const SkRRect& outer, const SkRRect& inner, const SkPaint& paint) {
   *     const SkRect& bounds = outer.getBounds();
   *     if (this->internalQuickReject(bounds, paint)) {
   *         return;
   *     }
   *
   *     auto layer = this->aboutToDraw(paint, &bounds);
   *     if (layer) {
   *         this->topDevice()->drawDRRect(outer, inner, layer->paint());
   *     }
   * }
   * ```
   */
  protected open fun onDrawDRRect(
    outer: SkRRect,
    `inner`: SkRRect,
    paint: SkPaint,
  ) {
    TODO("Implement onDrawDRRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::onDrawOval(const SkRect& oval, const SkPaint& paint) {
   *     SkASSERT(oval.isSorted());
   *     if (this->internalQuickReject(oval, paint)) {
   *         return;
   *     }
   *
   *     std::optional<AutoLayerForImageFilter> layer;
   *
   *     if (const SkBlurMaskFilterImpl* blurMaskFilter = this->canAttemptBlurredRRectDraw(paint)) {
   *         // Returns a layer if a blurred draw was unsuccessful.
   *         layer = this->attemptBlurredRRectDraw(
   *                 SkRRect::MakeOval(oval), blurMaskFilter, paint, PredrawFlags::kNone);
   *     } else {
   *         layer = this->aboutToDraw(paint, &oval);
   *     }
   *
   *     if (layer) {
   *         this->topDevice()->drawOval(oval, layer->paint());
   *     }
   * }
   * ```
   */
  protected open fun onDrawOval(rect: SkRect, paint: SkPaint) {
    TODO("Implement onDrawOval")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::onDrawArc(const SkRect& oval, SkScalar startAngle,
   *                          SkScalar sweepAngle, bool useCenter,
   *                          const SkPaint& paint) {
   *     SkASSERT(oval.isSorted());
   *     if (this->internalQuickReject(oval, paint)) {
   *         return;
   *     }
   *
   *     std::optional<AutoLayerForImageFilter> layer;
   *
   *     // Arcs with sweeps >= 360° are ovals. In this case, attempt a specialized blurred draw.
   *     if (const SkBlurMaskFilterImpl* blurMaskFilter = this->canAttemptBlurredRRectDraw(paint);
   *         blurMaskFilter && SkScalarAbs(sweepAngle) >= 360.f) {
   *         // Returns a layer if a blurred draw was unsuccessful.
   *         layer = this->attemptBlurredRRectDraw(
   *                 SkRRect::MakeOval(oval), blurMaskFilter, paint, PredrawFlags::kNone);
   *     } else {
   *         layer = this->aboutToDraw(paint, &oval);
   *     }
   *
   *     if (layer) {
   *         this->topDevice()->drawArc(SkArc::Make(oval, startAngle, sweepAngle, useCenter),
   *                                    layer->paint());
   *     }
   * }
   * ```
   */
  protected open fun onDrawArc(
    rect: SkRect,
    startAngle: SkScalar,
    sweepAngle: SkScalar,
    useCenter: Boolean,
    paint: SkPaint,
  ) {
    TODO("Implement onDrawArc")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::onDrawPath(const SkPath& path, const SkPaint& paint) {
   *     if (!path.isFinite()) {
   *         return;
   *     }
   *
   *     const SkRect& pathBounds = path.getBounds();
   *     if (!path.isInverseFillType() && this->internalQuickReject(pathBounds, paint)) {
   *         return;
   *     }
   *     if (path.isInverseFillType() && pathBounds.width() <= 0 && pathBounds.height() <= 0) {
   *         this->internalDrawPaint(paint);
   *         return;
   *     }
   *
   *     auto layer = this->aboutToDraw(paint, path.isInverseFillType() ? nullptr : &pathBounds);
   *     if (layer) {
   *         this->topDevice()->drawPath(path, layer->paint());
   *     }
   * }
   * ```
   */
  protected open fun onDrawPath(path: SkPath, paint: SkPaint) {
    TODO("Implement onDrawPath")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::onDrawRegion(const SkRegion& region, const SkPaint& paint) {
   *     const SkRect bounds = SkRect::Make(region.getBounds());
   *     if (this->internalQuickReject(bounds, paint)) {
   *         return;
   *     }
   *
   *     auto layer = this->aboutToDraw(paint, &bounds);
   *     if (layer) {
   *         this->topDevice()->drawRegion(region, layer->paint());
   *     }
   * }
   * ```
   */
  protected open fun onDrawRegion(region: SkRegion, paint: SkPaint) {
    TODO("Implement onDrawRegion")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::onDrawTextBlob(const SkTextBlob* blob, SkScalar x, SkScalar y,
   *                               const SkPaint& paint) {
   *     auto glyphRunList = fScratchGlyphRunBuilder->blobToGlyphRunList(*blob, {x, y});
   *     this->onDrawGlyphRunList(glyphRunList, paint);
   * }
   * ```
   */
  protected open fun onDrawTextBlob(
    blob: SkTextBlob?,
    x: SkScalar,
    y: SkScalar,
    paint: SkPaint,
  ) {
    TODO("Implement onDrawTextBlob")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::onDrawGlyphRunList(const sktext::GlyphRunList& glyphRunList, const SkPaint& paint) {
   *     SkRect bounds = glyphRunList.sourceBoundsWithOrigin();
   *     if (this->internalQuickReject(bounds, paint)) {
   *         return;
   *     }
   *
   *     // Text attempts to apply any SkMaskFilter internally and save the blurred masks in the
   *     // strike cache; if a glyph must be drawn as a path or drawable, SkDevice routes back to
   *     // this SkCanvas to retry, which will go through a function that does *not* skip the mask
   *     // filter layer.
   *     auto layer = this->aboutToDraw(paint, &bounds, PredrawFlags::kSkipMaskFilterAutoLayer);
   *     if (layer) {
   *         this->topDevice()->drawGlyphRunList(this, glyphRunList, layer->paint());
   *     }
   * }
   * ```
   */
  protected open fun onDrawGlyphRunList(glyphRunList: GlyphRunList, paint: SkPaint) {
    TODO("Implement onDrawGlyphRunList")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::onDrawPatch(const SkPoint cubics[12], const SkColor colors[4],
   *                            const SkPoint texCoords[4], SkBlendMode bmode,
   *                            const SkPaint& paint) {
   *     auto bounds = SkRect::Bounds({cubics, (size_t)SkPatchUtils::kNumCtrlPts});
   *     if (!bounds) {
   *         return; // we don't draw if the bounds are not finite
   *     }
   *
   *     // drawPatch has the same behavior restrictions as drawVertices
   *     SkPaint simplePaint = clean_paint_for_drawVertices(paint);
   *
   *     // Since a patch is always within the convex hull of the control points, we discard it when its
   *     // bounding rectangle is completely outside the current clip.
   *     if (this->internalQuickReject(bounds.value(), simplePaint)) {
   *         return;
   *     }
   *
   *     auto r = bounds.value();
   *     auto layer = this->aboutToDraw(simplePaint, &r);
   *     if (layer) {
   *         this->topDevice()->drawPatch(cubics, colors, texCoords, SkBlender::Mode(bmode),
   *                                      layer->paint());
   *     }
   * }
   * ```
   */
  protected open fun onDrawPatch(
    cubics: Array<SkPoint>,
    colors: Array<SkColor>,
    texCoords: Array<SkPoint>,
    mode: SkBlendMode,
    paint: SkPaint,
  ) {
    TODO("Implement onDrawPatch")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::onDrawPoints(PointMode mode, size_t count, const SkPoint pts[],
   *                             const SkPaint& paint) {
   *     if ((long)count <= 0 || this->nothingToDraw(paint)) {
   *         return;
   *     }
   *     SkASSERT(pts != nullptr);
   *
   *     // Enforce paint style matches implicit behavior of drawPoints
   *     SkPaint strokePaint = paint;
   *     strokePaint.setStyle(SkPaint::kStroke_Style);
   *
   *     SkRect boundsStorage;
   *     const SkRect* boundsPtr = nullptr;
   *
   *     /*
   *      *  Computing the bounds can actually slow us down (since we check inside).
   *      *  But if there is a filter, then it is useful to limit the size of
   *      *  its offscreen, hence we only compute it in those cases.
   *      *
   *      *  Note: it would be "correct" to never compute this, it is just considered
   *      *        an optimization opportunity.
   *      */
   *     if (paint.getImageFilter() || paint.getMaskFilter()) {
   *         auto bounds = SkRect::Bounds({pts, count});
   *         if (!bounds) {
   *             return;
   *         }
   *         if (this->internalQuickReject(bounds.value(), strokePaint)) {
   *             return;
   *         }
   *         boundsStorage = bounds.value();
   *         boundsPtr = &boundsStorage;
   *     }
   *
   *     auto layer = this->aboutToDraw(strokePaint, boundsPtr);
   *     if (layer) {
   *         this->topDevice()->drawPoints(mode, {pts, count}, layer->paint());
   *     }
   * }
   * ```
   */
  protected open fun onDrawPoints(
    mode: PointMode,
    count: ULong,
    pts: Array<SkPoint>,
    paint: SkPaint,
  ) {
    TODO("Implement onDrawPoints")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::onDrawImage2(const SkImage* image, SkScalar x, SkScalar y,
   *                             const SkSamplingOptions& sampling, const SkPaint* paint) {
   *     SkUNREACHABLE;
   * }
   * ```
   */
  protected open fun onDrawImage2(
    image: SkImage?,
    dx: SkScalar,
    dy: SkScalar,
    sampling: SkSamplingOptions,
    paint: SkPaint?,
  ) {
    TODO("Implement onDrawImage2")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::onDrawImageRect2(const SkImage* image, const SkRect& src, const SkRect& dst,
   *                                 const SkSamplingOptions& sampling, const SkPaint* paint,
   *                                 SrcRectConstraint constraint) {
   *     SkPaint realPaint = clean_paint_for_drawImage(paint);
   *     SkSamplingOptions realSampling = clean_sampling_for_constraint(sampling, constraint);
   *
   *     if (this->internalQuickReject(dst, realPaint)) {
   *         return;
   *     }
   *
   *     if (this->topDevice()->shouldDrawAsTiledImageRect()) {
   *         if (this->topDevice()->drawAsTiledImageRect(
   *                     this, image, &src, dst, realSampling, realPaint, constraint)) {
   *             return;
   *         }
   *     }
   *
   *     // drawImageRect()'s behavior is modified by the presence of an image filter, a mask filter, a
   *     // color filter, the paint's alpha, the paint's blender, and--when it's an alpha-only image--
   *     // the paint's color or shader. When there's an image filter, the paint's blender is applied to
   *     // the result of the image filter function, but every other aspect would influence the source
   *     // image that's then rendered with src-over blending into a transparent temporary layer.
   *     //
   *     // However, skif::FilterResult can apply the paint alpha and any color filter often without
   *     // requiring a layer, and src-over blending onto a transparent dst is a no-op, so we can use the
   *     // input image directly as the source for filtering. When the image is alpha-only and must be
   *     // colorized, or when a mask filter would change the coverage we skip this optimization for
   *     // simplicity since *somehow* embedding colorization or mask blurring into the filter graph
   *     // would likely be equivalent to using the existing AutoLayerForImageFilter functionality.
   *     if (realPaint.getImageFilter() && !image->isAlphaOnly() && !realPaint.getMaskFilter()) {
   *         SkDevice* device = this->topDevice();
   *
   *         skif::ParameterSpace<SkRect> imageBounds{dst};
   *         skif::DeviceSpace<SkIRect> outputBounds{device->devClipBounds()};
   *         FilterToSpan filterAsSpan(realPaint.getImageFilter());
   *         auto mappingAndBounds = get_layer_mapping_and_bounds(filterAsSpan,
   *                                                              device->localToDevice44(),
   *                                                              outputBounds,
   *                                                              imageBounds);
   *         if (!mappingAndBounds) {
   *             return;
   *         }
   *         if (!this->predrawNotify()) {
   *             return;
   *         }
   *
   *         // Start out with an empty source image, to be replaced with the converted 'image', and a
   *         // desired output equal to the calculated initial source layer bounds, which accounts for
   *         // how the image filters will access 'image' (possibly different than just 'outputBounds').
   *         auto backend = device->createImageFilteringBackend(
   *                 device->surfaceProps(),
   *                 image_filter_color_type(device->imageInfo().colorInfo()));
   *         auto [mapping, srcBounds] = *mappingAndBounds;
   *         skif::Stats stats;
   *         skif::Context ctx{std::move(backend),
   *                           mapping,
   *                           srcBounds,
   *                           skif::FilterResult{},
   *                           device->imageInfo().colorSpace(),
   *                           &stats};
   *
   *         auto source = skif::FilterResult::MakeFromImage(
   *                 ctx, sk_ref_sp(image), src, imageBounds, sampling);
   *         // Apply effects that are normally processed on the draw *before* any layer/image filter.
   *         source = apply_alpha_and_colorfilter(ctx, source, realPaint);
   *
   *         // Evaluate the image filter, with a context pointing to the source created directly from
   *         // 'image' (which will not require intermediate renderpasses when 'src' is integer aligned).
   *         // and a desired output matching the device clip bounds.
   *         ctx = ctx.withNewDesiredOutput(mapping.deviceToLayer(outputBounds))
   *                  .withNewSource(source);
   *         auto result = as_IFB(realPaint.getImageFilter())->filterImage(ctx);
   *         result.draw(ctx, device, realPaint.getBlender());
   *         stats.reportStats();
   *         return;
   *     }
   *
   *     if (realPaint.getMaskFilter() && this->topDevice()->useDrawCoverageMaskForMaskFilters()) {
   *         // Route mask-filtered drawImages to drawRect() to use the auto-layer for mask filters,
   *         // which require all shading to be encoded in the paint.
   *         SkRect drawDst = SkModifyPaintAndDstForDrawImageRect(
   *                 image, sampling, src, dst, constraint == kStrict_SrcRectConstraint, &realPaint);
   *         if (drawDst.isEmpty()) {
   *             return;
   *         } else {
   *             this->drawRect(drawDst, realPaint);
   *             return;
   *         }
   *     }
   *
   *     auto layer = this->aboutToDraw(realPaint, &dst,
   *                                    PredrawFlags::kCheckForOverwrite |
   *                                    (image->isOpaque() ? PredrawFlags::kOpaqueShaderOverride
   *                                                       : PredrawFlags::kNonOpaqueShaderOverride));
   *     if (layer) {
   *         this->topDevice()->drawImageRect(image, &src, dst, realSampling, layer->paint(),
   *                                          constraint);
   *     }
   * }
   * ```
   */
  protected open fun onDrawImageRect2(
    image: SkImage?,
    src: SkRect,
    dst: SkRect,
    sampling: SkSamplingOptions,
    paint: SkPaint?,
    constraint: SrcRectConstraint,
  ) {
    TODO("Implement onDrawImageRect2")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::onDrawImageLattice2(const SkImage* image, const Lattice& lattice, const SkRect& dst,
   *                                    SkFilterMode filter, const SkPaint* paint) {
   *     SkPaint realPaint = clean_paint_for_drawImage(paint);
   *
   *     if (this->internalQuickReject(dst, realPaint)) {
   *         return;
   *     }
   *
   *     auto layer = this->aboutToDraw(realPaint, &dst);
   *     if (layer) {
   *         this->topDevice()->drawImageLattice(image, lattice, dst, filter, layer->paint());
   *     }
   * }
   * ```
   */
  protected open fun onDrawImageLattice2(
    image: SkImage?,
    lattice: Lattice,
    dst: SkRect,
    filter: SkFilterMode,
    paint: SkPaint?,
  ) {
    TODO("Implement onDrawImageLattice2")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::onDrawAtlas2(const SkImage* atlas, const SkRSXform xform[], const SkRect tex[],
   *                             const SkColor colors[], int count, SkBlendMode bmode,
   *                             const SkSamplingOptions& sampling, const SkRect* cull,
   *                             const SkPaint* paint) {
   *     // drawAtlas is a combination of drawVertices and drawImage...
   *     SkPaint realPaint = clean_paint_for_drawVertices(clean_paint_for_drawImage(paint));
   *     realPaint.setShader(atlas->makeShader(sampling));
   *
   *     if (cull && this->internalQuickReject(*cull, realPaint)) {
   *         return;
   *     }
   *
   *     // drawAtlas should not have mask filters on its paint, so we don't need to worry about
   *     // converting its "drawImage" behavior into the paint to work with the auto-mask-filter system.
   *     SkASSERT(!realPaint.getMaskFilter());
   *     auto layer = this->aboutToDraw(realPaint);
   *     if (layer) {
   *         size_t N = SkToSizeT(count);
   *         this->topDevice()->drawAtlas({xform, N}, {tex, N}, {colors, colors ? N : 0},
   *                                      SkBlender::Mode(bmode), layer->paint());
   *     }
   * }
   * ```
   */
  protected open fun onDrawAtlas2(
    atlas: SkImage?,
    xform: Array<SkRSXform>,
    src: Array<SkRect>,
    colors: Array<SkColor>,
    count: Int,
    bmode: SkBlendMode,
    sampling: SkSamplingOptions,
    cull: SkRect?,
    paint: SkPaint?,
  ) {
    TODO("Implement onDrawAtlas2")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::onDrawEdgeAAImageSet2(const ImageSetEntry imageSet[], int count,
   *                                      const SkPoint dstClips[], const SkMatrix preViewMatrices[],
   *                                      const SkSamplingOptions& sampling, const SkPaint* paint,
   *                                      SrcRectConstraint constraint) {
   *     if (count <= 0) {
   *         // Nothing to draw
   *         return;
   *     }
   *
   *     SkPaint realPaint = clean_paint_for_drawImage(paint);
   *     SkSamplingOptions realSampling = clean_sampling_for_constraint(sampling, constraint);
   *
   *     // We could calculate the set's dstRect union to always check quickReject(), but we can't reject
   *     // individual entries and Chromium's occlusion culling already makes it likely that at least one
   *     // entry will be visible. So, we only calculate the draw bounds when it's trivial (count == 1),
   *     // or we need it for the autolooper (since it greatly improves image filter perf).
   *     bool needsAutoLayer = SkToBool(realPaint.getImageFilter() || realPaint.getMaskFilter());
   *     bool setBoundsValid = count == 1 || needsAutoLayer;
   *     SkRect setBounds = imageSet[0].fDstRect;
   *     if (imageSet[0].fMatrixIndex >= 0) {
   *         // Account for the per-entry transform that is applied prior to the CTM when drawing
   *         preViewMatrices[imageSet[0].fMatrixIndex].mapRect(&setBounds);
   *     }
   *     if (needsAutoLayer) {
   *         for (int i = 1; i < count; ++i) {
   *             SkRect entryBounds = imageSet[i].fDstRect;
   *             if (imageSet[i].fMatrixIndex >= 0) {
   *                 preViewMatrices[imageSet[i].fMatrixIndex].mapRect(&entryBounds);
   *             }
   *             setBounds.joinPossiblyEmptyRect(entryBounds);
   *         }
   *     }
   *
   *     // If we happen to have the draw bounds, though, might as well check quickReject().
   *     if (setBoundsValid && this->internalQuickReject(setBounds, realPaint)) {
   *         return;
   *     }
   *
   *     if (realPaint.getMaskFilter() && this->topDevice()->useDrawCoverageMaskForMaskFilters()) {
   *         // Route mask-filtered drawEdgeAAImageSets to drawEdgeAAQuad() or drawImageRect()
   *         // to use the auto-layer for mask filters, which require all shading to be encoded in
   *         // the paint.
   *         int dstClipIndex = 0;
   *         for (int i = 0; i < count; ++i) {
   *             SkPaint imagePaint = realPaint;
   *             SkRect drawDst = SkModifyPaintAndDstForDrawImageRect(
   *                                 imageSet[i].fImage.get(), sampling,
   *                                 imageSet[i].fSrcRect, imageSet[i].fDstRect,
   *                                 constraint == kStrict_SrcRectConstraint, &imagePaint);
   *             if (drawDst.isEmpty()) {
   *                 return;
   *             }
   *
   *             auto layer = this->aboutToDraw(imagePaint, &drawDst);
   *             if (layer) {
   *                 // Since we can't call mapRect to apply any preview matrix and drawEdgeAAQuad
   *                 // doesn't take an optional matrix, we can modify the local-to-device matrix
   *                 // of the layers top device.
   *                 if (imageSet[i].fMatrixIndex >= 0) {
   *                     this->topDevice()->setLocalToDevice(
   *                         this->topDevice()->localToDevice44() *
   *                         SkM44(preViewMatrices[imageSet[i].fMatrixIndex]));
   *                 }
   *
   *                 // Call drawEdgeAAImageSet on each image one at a time, to correctly
   *                 // paint the image.
   *                 this->topDevice()->drawEdgeAAQuad(drawDst,
   *                                                   imageSet[i].fHasClip ? dstClips + dstClipIndex
   *                                                                         : nullptr,
   *                                                   (QuadAAFlags)imageSet[i].fAAFlags,
   *                                                   layer->paint().getColor4f(),
   *                                                   SkBlendMode::kSrcOver);
   *             }
   *             dstClipIndex += 4 * imageSet[i].fHasClip;
   *         }
   *         return;
   *     }
   *
   *     auto layer = this->aboutToDraw(realPaint, setBoundsValid ? &setBounds : nullptr);
   *     if (layer) {
   *         this->topDevice()->drawEdgeAAImageSet(imageSet, count, dstClips, preViewMatrices,
   *                                               realSampling, layer->paint(), constraint);
   *     }
   * }
   * ```
   */
  protected open fun onDrawEdgeAAImageSet2(
    imageSet: Array<undefined.ImageSetEntry>,
    count: Int,
    dstClips: Array<SkPoint>,
    preViewMatrices: Array<SkMatrix>,
    sampling: SkSamplingOptions,
    paint: SkPaint?,
    constraint: SrcRectConstraint,
  ) {
    TODO("Implement onDrawEdgeAAImageSet2")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::onDrawVerticesObject(const SkVertices* vertices, SkBlendMode bmode,
   *                                     const SkPaint& paint) {
   *     SkPaint simplePaint = clean_paint_for_drawVertices(paint);
   *
   *     const SkRect& bounds = vertices->bounds();
   *     if (this->internalQuickReject(bounds, simplePaint)) {
   *         return;
   *     }
   *
   *     auto layer = this->aboutToDraw(simplePaint, &bounds);
   *     if (layer) {
   *         this->topDevice()->drawVertices(vertices, SkBlender::Mode(bmode), layer->paint());
   *     }
   * }
   * ```
   */
  protected open fun onDrawVerticesObject(
    vertices: SkVertices?,
    mode: SkBlendMode,
    paint: SkPaint,
  ) {
    TODO("Implement onDrawVerticesObject")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::onDrawMesh(const SkMesh& mesh, sk_sp<SkBlender> blender, const SkPaint& paint) {
   *     SkPaint simplePaint = clean_paint_for_drawVertices(paint);
   *     auto layer = this->aboutToDraw(simplePaint, nullptr);
   *     if (layer) {
   *         this->topDevice()->drawMesh(mesh, std::move(blender), paint);
   *     }
   * }
   * ```
   */
  protected open fun onDrawMesh(
    mesh: SkMesh,
    blender: SkSp<SkBlender>,
    paint: SkPaint,
  ) {
    TODO("Implement onDrawMesh")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::onDrawAnnotation(const SkRect& rect, const char key[], SkData* value) {
   *     SkASSERT(key);
   *
   *     if (this->predrawNotify()) {
   *         this->topDevice()->drawAnnotation(rect, key, value);
   *     }
   * }
   * ```
   */
  protected open fun onDrawAnnotation(
    rect: SkRect,
    key: CharArray,
    `value`: SkData?,
  ) {
    TODO("Implement onDrawAnnotation")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::onDrawShadowRec(const SkPath& path, const SkDrawShadowRec& rec) {
   *     // We don't test quickReject because the shadow outsets the path's bounds.
   *     // TODO(michaelludwig): Is it worth calling SkDrawShadowMetrics::GetLocalBounds here?
   *     if (!this->predrawNotify()) {
   *         return;
   *     }
   *     this->topDevice()->drawShadow(this, path, rec);
   * }
   * ```
   */
  protected open fun onDrawShadowRec(path: SkPath, rec: SkDrawShadowRec) {
    TODO("Implement onDrawShadowRec")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::onDrawDrawable(SkDrawable* dr, const SkMatrix* matrix) {
   *     // drawable bounds are no longer reliable (e.g. android displaylist)
   *     // so don't use them for quick-reject
   *     if (this->predrawNotify()) {
   *         this->topDevice()->drawDrawable(this, dr, matrix);
   *     }
   * }
   * ```
   */
  protected open fun onDrawDrawable(drawable: SkDrawable?, matrix: SkMatrix?) {
    TODO("Implement onDrawDrawable")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::onDrawPicture(const SkPicture* picture, const SkMatrix* matrix,
   *                              const SkPaint* paint) {
   *     if (this->internalQuickReject(picture->cullRect(), paint ? *paint : SkPaint{}, matrix)) {
   *         return;
   *     }
   *
   *     SkAutoCanvasMatrixPaint acmp(this, matrix, paint, picture->cullRect());
   *     picture->playback(this);
   * }
   * ```
   */
  protected open fun onDrawPicture(
    picture: SkPicture?,
    matrix: SkMatrix?,
    paint: SkPaint?,
  ) {
    TODO("Implement onDrawPicture")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::onDrawEdgeAAQuad(const SkRect& r, const SkPoint clip[4], QuadAAFlags edgeAA,
   *                                 const SkColor4f& color, SkBlendMode mode) {
   *     SkASSERT(r.isSorted());
   *
   *     SkPaint paint{color};
   *     paint.setBlendMode(mode);
   *     if (this->internalQuickReject(r, paint)) {
   *         return;
   *     }
   *
   *     if (this->predrawNotify()) {
   *         this->topDevice()->drawEdgeAAQuad(r, clip, edgeAA, color, mode);
   *     }
   * }
   * ```
   */
  protected open fun onDrawEdgeAAQuad(
    rect: SkRect,
    clip: Array<SkPoint>,
    aaFlags: QuadAAFlags,
    color: SkColor4f,
    mode: SkBlendMode,
  ) {
    TODO("Implement onDrawEdgeAAQuad")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::onClipRect(const SkRect& rect, SkClipOp op, ClipEdgeStyle edgeStyle) {
   *     SkASSERT(rect.isSorted());
   *     const bool isAA = kSoft_ClipEdgeStyle == edgeStyle;
   *
   *     AutoUpdateQRBounds aqr(this);
   *     this->topDevice()->clipRect(rect, op, isAA);
   * }
   * ```
   */
  protected open fun onClipRect(
    rect: SkRect,
    op: SkClipOp,
    edgeStyle: ClipEdgeStyle,
  ) {
    TODO("Implement onClipRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::onClipRRect(const SkRRect& rrect, SkClipOp op, ClipEdgeStyle edgeStyle) {
   *     bool isAA = kSoft_ClipEdgeStyle == edgeStyle;
   *
   *     AutoUpdateQRBounds aqr(this);
   *     this->topDevice()->clipRRect(rrect, op, isAA);
   * }
   * ```
   */
  protected open fun onClipRRect(
    rrect: SkRRect,
    op: SkClipOp,
    edgeStyle: ClipEdgeStyle,
  ) {
    TODO("Implement onClipRRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::onClipPath(const SkPath& path, SkClipOp op, ClipEdgeStyle edgeStyle) {
   *     bool isAA = kSoft_ClipEdgeStyle == edgeStyle;
   *
   *     AutoUpdateQRBounds aqr(this);
   *     this->topDevice()->clipPath(path, op, isAA);
   * }
   * ```
   */
  protected open fun onClipPath(
    path: SkPath,
    op: SkClipOp,
    edgeStyle: ClipEdgeStyle,
  ) {
    TODO("Implement onClipPath")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::onClipShader(sk_sp<SkShader> sh, SkClipOp op) {
   *     AutoUpdateQRBounds aqr(this);
   *     this->topDevice()->clipShader(sh, op);
   * }
   * ```
   */
  protected open fun onClipShader(sh: SkSp<SkShader>, op: SkClipOp) {
    TODO("Implement onClipShader")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::onClipRegion(const SkRegion& rgn, SkClipOp op) {
   *     AutoUpdateQRBounds aqr(this);
   *     this->topDevice()->clipRegion(rgn, op);
   * }
   * ```
   */
  protected open fun onClipRegion(deviceRgn: SkRegion, op: SkClipOp) {
    TODO("Implement onClipRegion")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::onResetClip() {
   *     SkIRect deviceRestriction = this->topDevice()->imageInfo().bounds();
   *     if (fClipRestrictionSaveCount >= 0 && this->topDevice() == this->rootDevice()) {
   *         // Respect the device clip restriction when resetting the clip if we're on the base device.
   *         // If we're not on the base device, then the "reset" applies to the top device's clip stack,
   *         // and the clip restriction will be respected automatically during a restore of the layer.
   *         if (!deviceRestriction.intersect(fClipRestrictionRect)) {
   *             deviceRestriction = SkIRect::MakeEmpty();
   *         }
   *     }
   *
   *     AutoUpdateQRBounds aqr(this);
   *     this->topDevice()->replaceClip(deviceRestriction);
   * }
   * ```
   */
  protected open fun onResetClip() {
    TODO("Implement onResetClip")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::onDiscard() {
   *     if (fSurfaceBase) {
   *         sk_ignore_unused_variable(fSurfaceBase->aboutToDraw(SkSurface::kDiscard_ContentChangeMode));
   *     }
   * }
   * ```
   */
  protected open fun onDiscard() {
    TODO("Implement onDiscard")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<Slug> SkCanvas::onConvertGlyphRunListToSlug(const sktext::GlyphRunList& glyphRunList,
   *                                                   const SkPaint& paint) {
   *     SkRect bounds = glyphRunList.sourceBoundsWithOrigin();
   *     if (bounds.isEmpty() || !bounds.isFinite() || this->nothingToDraw(paint)) {
   *         return nullptr;
   *     }
   *     // See comment in onDrawGlyphRunList()
   *     auto layer = this->aboutToDraw(paint, &bounds, PredrawFlags::kSkipMaskFilterAutoLayer);
   *     if (layer) {
   *         return this->topDevice()->convertGlyphRunListToSlug(glyphRunList, layer->paint());
   *     }
   *     return nullptr;
   * }
   * ```
   */
  protected open fun onConvertGlyphRunListToSlug(glyphRunList: GlyphRunList, paint: SkPaint): Int {
    TODO("Implement onConvertGlyphRunListToSlug")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::onDrawSlug(const Slug* slug, const SkPaint& paint) {
   *     SkRect bounds = slug->sourceBoundsWithOrigin();
   *     if (this->internalQuickReject(bounds, paint)) {
   *         return;
   *     }
   *     // See comment in onDrawGlyphRunList()
   *     auto layer = this->aboutToDraw(paint, &bounds, PredrawFlags::kSkipMaskFilterAutoLayer);
   *     if (layer) {
   *         this->topDevice()->drawSlug(this, slug, layer->paint());
   *     }
   * }
   * ```
   */
  protected open fun onDrawSlug(slug: Slug?, paint: SkPaint) {
    TODO("Implement onDrawSlug")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkCanvas::predrawNotify(bool willOverwritesEntireSurface) {
   *     if (fSurfaceBase) {
   *         if (!fSurfaceBase->aboutToDraw(willOverwritesEntireSurface
   *                                        ? SkSurface::kDiscard_ContentChangeMode
   *                                        : SkSurface::kRetain_ContentChangeMode)) {
   *             return false;
   *         }
   *     }
   *     return true;
   * }
   * ```
   */
  private fun predrawNotify(willOverwritesEntireSurface: Boolean = TODO()): Boolean {
    TODO("Implement predrawNotify")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkCanvas::predrawNotify(const SkRect* rect, const SkPaint* paint,
   *                              SkEnumBitMask<PredrawFlags> flags) {
   *     if (fSurfaceBase) {
   *         SkSurface::ContentChangeMode mode = SkSurface::kRetain_ContentChangeMode;
   *         // Since willOverwriteAllPixels() may not be complete free to call, we only do so if
   *         // there is an outstanding snapshot, since w/o that, there will be no copy-on-write
   *         // and therefore we don't care which mode we're in.
   *         //
   *         if (fSurfaceBase->outstandingImageSnapshot()) {
   *             if (this->wouldOverwriteEntireSurface(rect, paint, flags)) {
   *                 mode = SkSurface::kDiscard_ContentChangeMode;
   *             }
   *         }
   *         if (!fSurfaceBase->aboutToDraw(mode)) {
   *             return false;
   *         }
   *     }
   *     return true;
   * }
   * ```
   */
  private fun predrawNotify(
    rect: SkRect?,
    paint: SkPaint?,
    flags: SkEnumBitMask<PredrawFlags>,
  ): Boolean {
    TODO("Implement predrawNotify")
  }

  /**
   * C++ original:
   * ```cpp
   * std::optional<AutoLayerForImageFilter> SkCanvas::aboutToDraw(
   *         const SkPaint& paint,
   *         const SkRect* rawBounds,
   *         SkEnumBitMask<PredrawFlags> flags) {
   *     if (flags & PredrawFlags::kCheckForOverwrite) {
   *         if (!this->predrawNotify(rawBounds, &paint, flags)) {
   *             return std::nullopt;
   *         }
   *     } else {
   *         if (!this->predrawNotify()) {
   *             return std::nullopt;
   *         }
   *     }
   *
   *     // TODO: Eventually all devices will use this code path and this will just test 'flags'.
   *     const bool skipMaskFilterLayer = (flags & PredrawFlags::kSkipMaskFilterAutoLayer) ||
   *                                      !this->topDevice()->useDrawCoverageMaskForMaskFilters();
   *     return std::optional<AutoLayerForImageFilter>(
   *             std::in_place, this, paint, rawBounds, skipMaskFilterLayer);
   * }
   * ```
   */
  private fun aboutToDraw(
    paint: SkPaint,
    rawBounds: SkRect?,
    flags: SkEnumBitMask<PredrawFlags>,
  ): AutoLayerForImageFilter? {
    TODO("Implement aboutToDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * std::optional<AutoLayerForImageFilter> SkCanvas::aboutToDraw(
   *         const SkPaint& paint,
   *         const SkRect* rawBounds) {
   *     return this->aboutToDraw(paint, rawBounds, PredrawFlags::kNone);
   * }
   * ```
   */
  private fun aboutToDraw(paint: SkPaint, rawBounds: SkRect? = TODO()): AutoLayerForImageFilter? {
    TODO("Implement aboutToDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDevice* rootDevice() const {
   *         SkASSERT(fRootDevice);
   *         return fRootDevice.get();
   *     }
   * ```
   */
  private fun rootDevice(): SkDevice {
    TODO("Implement rootDevice")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDevice* SkCanvas::topDevice() const {
   *     SkASSERT(fMCRec->fDevice);
   *     return fMCRec->fDevice;
   * }
   * ```
   */
  private fun topDevice(): SkDevice {
    TODO("Implement topDevice")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSurface_Base* getSurfaceBase() const { return fSurfaceBase; }
   * ```
   */
  public fun getSurfaceBase(): SkSurfaceBase {
    TODO("Implement getSurfaceBase")
  }

  /**
   * C++ original:
   * ```cpp
   * void setSurfaceBase(SkSurface_Base* sb) {
   *         fSurfaceBase = sb;
   *     }
   * ```
   */
  public fun setSurfaceBase(sb: SkSurfaceBase?) {
    TODO("Implement setSurfaceBase")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::doSave() {
   *     this->willSave();
   *
   *     SkASSERT(fMCRec->fDeferredSaveCount > 0);
   *     fMCRec->fDeferredSaveCount -= 1;
   *     this->internalSave();
   * }
   * ```
   */
  public fun doSave() {
    TODO("Implement doSave")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::checkForDeferredSave() {
   *     if (fMCRec->fDeferredSaveCount > 0) {
   *         this->doSave();
   *     }
   * }
   * ```
   */
  public fun checkForDeferredSave() {
    TODO("Implement checkForDeferredSave")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::internalSetMatrix(const SkM44& m) {
   *     fMCRec->fMatrix = m;
   *
   *     this->topDevice()->setGlobalCTM(fMCRec->fMatrix);
   * }
   * ```
   */
  public fun internalSetMatrix(m: SkM44) {
    TODO("Implement internalSetMatrix")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void onSurfaceDelete() {}
   * ```
   */
  public open fun onSurfaceDelete() {
    TODO("Implement onSurfaceDelete")
  }

  /**
   * C++ original:
   * ```cpp
   * SkCanvas& operator=(SkCanvas&&) = delete
   * ```
   */
  private fun assign(param0: SkCanvas) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * SkCanvas& operator=(const SkCanvas&) = delete
   * ```
   */
  private fun convertBlobToSlug(
    blob: SkTextBlob,
    origin: SkPoint,
    paint: SkPaint,
  ): Int {
    TODO("Implement convertBlobToSlug")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<Slug> SkCanvas::convertBlobToSlug(
   *         const SkTextBlob& blob, SkPoint origin, const SkPaint& paint) {
   *     TRACE_EVENT0("skia", TRACE_FUNC);
   *     auto glyphRunList = fScratchGlyphRunBuilder->blobToGlyphRunList(blob, origin);
   *     return this->onConvertGlyphRunListToSlug(glyphRunList, paint);
   * }
   * ```
   */
  private fun drawSlug(slug: Slug?, paint: SkPaint) {
    TODO("Implement drawSlug")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::drawSlug(const Slug* slug, const SkPaint& paint) {
   *     TRACE_EVENT0("skia", TRACE_FUNC);
   *     if (slug) {
   *         this->onDrawSlug(slug, paint);
   *     }
   * }
   * ```
   */
  private fun onlyAxisAlignedSaveBehind(subset: SkRect?): Int {
    TODO("Implement onlyAxisAlignedSaveBehind")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkCanvas::only_axis_aligned_saveBehind(const SkRect* bounds) {
   *     if (bounds && !this->getLocalClipBounds().intersects(*bounds)) {
   *         // Assuming clips never expand, if the request bounds is outside of the current clip
   *         // there is no need to copy/restore the area, so just devolve back to a regular save.
   *         this->save();
   *     } else {
   *         bool doTheWork = this->onDoSaveBehind(bounds);
   *         fSaveCount += 1;
   *         this->internalSave();
   *         if (doTheWork) {
   *             this->internalSaveBehind(bounds);
   *         }
   *     }
   *     return this->getSaveCount() - 1;
   * }
   * ```
   */
  private fun drawClippedToSaveBehind(paint: SkPaint) {
    TODO("Implement drawClippedToSaveBehind")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::drawClippedToSaveBehind(const SkPaint& paint) {
   *     TRACE_EVENT0("skia", TRACE_FUNC);
   *     this->onDrawBehind(paint);
   * }
   * ```
   */
  private fun resetForNextPicture(bounds: SkIRect) {
    TODO("Implement resetForNextPicture")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::resetForNextPicture(const SkIRect& bounds) {
   *     this->restoreToCount(1);
   *
   *     // We're peering through a lot of structs here.  Only at this scope do we know that the device
   *     // is a SkNoPixelsDevice.
   *     SkASSERT(fRootDevice->isNoPixelsDevice());
   *     SkNoPixelsDevice* asNoPixelsDevice = static_cast<SkNoPixelsDevice*>(fRootDevice.get());
   *     if (!asNoPixelsDevice->resetForNextPicture(bounds)) {
   *         fRootDevice = sk_make_sp<SkNoPixelsDevice>(bounds,
   *                                                    fRootDevice->surfaceProps(),
   *                                                    fRootDevice->imageInfo().refColorSpace());
   *     }
   *
   *     fMCRec->reset(fRootDevice.get());
   *     fQuickRejectBounds = this->computeDeviceClipBounds();
   * }
   * ```
   */
  private fun `init`(device: SkSp<SkDevice>) {
    TODO("Implement init")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::init(sk_sp<SkDevice> device) {
   *     if (!device) {
   *         device = sk_make_sp<SkNoPixelsDevice>(SkIRect::MakeEmpty(), fProps);
   *     }
   *
   *     // From this point on, SkCanvas will always have a device
   *     SkASSERT(device);
   *
   *     fSaveCount = 1;
   *     fMCRec = new (fMCStack.push_back()) MCRec(device.get());
   *
   *     // The root device and the canvas should always have the same pixel geometry
   *     SkASSERT(fProps.pixelGeometry() == device->surfaceProps().pixelGeometry());
   *
   *     fSurfaceBase = nullptr;
   *     fRootDevice = std::move(device);
   *     fScratchGlyphRunBuilder = std::make_unique<sktext::GlyphRunBuilder>();
   *     fQuickRejectBounds = this->computeDeviceClipBounds();
   * }
   * ```
   */
  private fun nothingToDraw(paint: SkPaint): Boolean {
    TODO("Implement nothingToDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkCanvas::nothingToDraw(const SkPaint& paint) const {
   *     return !this->topDevice()->surfaceProps().preservesTransparentDraws() && paint.nothingToDraw();
   * }
   * ```
   */
  private fun internalQuickReject(
    bounds: SkRect,
    paint: SkPaint,
    matrix: SkMatrix? = TODO(),
  ): Boolean {
    TODO("Implement internalQuickReject")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkCanvas::internalQuickReject(const SkRect& bounds, const SkPaint& paint,
   *                                    const SkMatrix* matrix) {
   *     if (!bounds.isFinite() || this->nothingToDraw(paint)) {
   *         return true;
   *     }
   *
   *     if (paint.canComputeFastBounds()) {
   *         SkRect tmp = matrix ? matrix->mapRect(bounds) : bounds;
   *         return this->quickReject(paint.computeFastBounds(tmp, &tmp));
   *     }
   *
   *     return false;
   * }
   * ```
   */
  private fun internalDrawPaint(paint: SkPaint) {
    TODO("Implement internalDrawPaint")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::internalDrawPaint(const SkPaint& paint) {
   *     // drawPaint does not call internalQuickReject() because computing its geometry is not free
   *     // (see getLocalClipBounds(), and the two conditions below are sufficient.
   *     if (this->nothingToDraw(paint) || this->isClipEmpty()) {
   *         return;
   *     }
   *
   *     auto layer = this->aboutToDraw(paint, nullptr, PredrawFlags::kCheckForOverwrite);
   *     if (layer) {
   *         this->topDevice()->drawPaint(layer->paint());
   *     }
   * }
   * ```
   */
  private fun internalSaveLayer(
    rec: SaveLayerRec,
    strategy: SaveLayerStrategy,
    coverageOnly: Boolean = TODO(),
  ) {
    TODO("Implement internalSaveLayer")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::internalSaveLayer(const SaveLayerRec& rec,
   *                                  SaveLayerStrategy strategy,
   *                                  bool coverageOnly) {
   *     TRACE_EVENT0("skia", TRACE_FUNC);
   *     // Do this before we create the layer. We don't call the public save() since that would invoke a
   *     // possibly overridden virtual.
   *     this->internalSave();
   *
   *     if (this->isClipEmpty()) {
   *         // Early out if the layer wouldn't draw anything
   *         return;
   *     }
   *
   *     // Build up the paint for restoring the layer, taking only the pieces of rec.fPaint that are
   *     // relevant. Filtering is automatically chosen in internalDrawDeviceWithFilter based on the
   *     // device's coordinate space.
   *     SkPaint restorePaint(rec.fPaint ? *rec.fPaint : SkPaint());
   *     restorePaint.setStyle(SkPaint::kFill_Style); // a layer is filled out "infinitely"
   *     restorePaint.setPathEffect(nullptr);         // path effects are ignored for saved layers
   *     restorePaint.setMaskFilter(nullptr);         // mask filters are ignored for saved layers
   *     restorePaint.setImageFilter(nullptr);        // the image filter is held separately
   *     // Smooth non-axis-aligned layer edges; this automatically downgrades to non-AA for aligned
   *     // layer restores. This is done to match legacy behavior where the post-applied MatrixTransform
   *     // bilerp also smoothed cropped edges. See skbug.com/40042614
   *     restorePaint.setAntiAlias(true);
   *
   *     sk_sp<SkImageFilter> paintFilter = rec.fPaint ? rec.fPaint->refImageFilter() : nullptr;
   *     FilterSpan filters = paintFilter ? FilterSpan{&paintFilter, 1} : rec.fFilters;
   *     if (filters.size() > kMaxFiltersPerLayer) {
   *         filters = filters.first(kMaxFiltersPerLayer);
   *     }
   *     const SkColorFilter* cf = restorePaint.getColorFilter();
   *     const SkBlender* blender = restorePaint.getBlender();
   *
   *     // When this is false, restoring the layer filled with unmodified prior contents should be
   *     // identical to the prior contents, so we can restrict the layer even more than just the
   *     // clip bounds.
   *     bool filtersPriorDevice = rec.fBackdrop;
   * #if !defined(SK_LEGACY_INITWITHPREV_LAYER_SIZING)
   *     // A regular filter applied to a layer initialized with prior contents is somewhat
   *     // analogous to a backdrop filter so they are treated the same.
   *     // TODO(b/314968012): Chrome needs to be updated to clip saveAlphaLayer bounds explicitly when
   *     // it uses kInitWithPrevious and LCD text.
   *     filtersPriorDevice |= ((rec.fSaveLayerFlags & kInitWithPrevious_SaveLayerFlag) &&
   *              (!filters.empty() || cf || blender || restorePaint.getAlphaf() < 1.f));
   * #endif
   *     // If the restorePaint has a transparency-affecting colorfilter or blender, the output is
   *     // unbounded during restore(). `internalDrawDeviceWithFilter` automatically applies these
   *     // effects. When there's no image filter, SkDevice::drawDevice is used, which does
   *     // not apply effects beyond the layer's image so we mark `trivialRestore` as false too.
   *     // TODO: drawDevice() could be updated to apply transparency-affecting effects to a content-
   *     // clipped image, but this is the simplest solution when considering document-based SkDevices.
   *     const bool drawDeviceMustFillClip = filters.empty() &&
   *             ((cf && as_CFB(cf)->affectsTransparentBlack()) ||
   *                 (blender && as_BB(blender)->affectsTransparentBlack()));
   *     const bool trivialRestore = !filtersPriorDevice && !drawDeviceMustFillClip;
   *
   *     // Size the new layer relative to the prior device, which may already be aligned for filters.
   *     SkDevice* priorDevice = this->topDevice();
   *     skif::Mapping newLayerMapping;
   *     skif::LayerSpace<SkIRect> layerBounds;
   *     skif::DeviceSpace<SkIRect> outputBounds{priorDevice->devClipBounds()};
   *
   *     std::optional<skif::ParameterSpace<SkRect>> contentBounds;
   *     // Set the bounds hint if provided and there's no further effects on prior device content
   *     if (rec.fBounds && trivialRestore) {
   *         contentBounds = skif::ParameterSpace<SkRect>(*rec.fBounds);
   *     }
   *
   *     auto mappingAndBounds = get_layer_mapping_and_bounds(
   *             filters, priorDevice->localToDevice44(), outputBounds, contentBounds);
   *
   *     auto abortLayer = [this]() {
   *         // The filtered content would not draw anything, or the new device space has an invalid
   *         // coordinate system, in which case we mark the current top device as empty so that nothing
   *         // draws until the canvas is restored past this saveLayer.
   *         AutoUpdateQRBounds aqr(this);
   *         this->topDevice()->clipRect(SkRect::MakeEmpty(), SkClipOp::kIntersect, /* aa */ false);
   *     };
   *
   *     if (!mappingAndBounds) {
   *         abortLayer();
   *         return;
   *     }
   *
   *     std::tie(newLayerMapping, layerBounds) = *mappingAndBounds;
   *
   *     bool paddedLayer = false;
   *     if (layerBounds.isEmpty()) {
   *         // The image filter graph does not require any input, so we don't need to actually render
   *         // a new layer for the source image. This could be because the image filter itself will not
   *         // produce output, or that the filter DAG has no references to the dynamic source image.
   *         // In this case it still has an output that we need to render, but do so now since there is
   *         // no new layer pushed on the stack and the paired restore() will be a no-op.
   *         if (!filters.empty() && !priorDevice->isNoPixelsDevice()) {
   *             SkColorInfo filterColorInfo = priorDevice->imageInfo().colorInfo();
   *             if (rec.fColorSpace) {
   *                 filterColorInfo = filterColorInfo.makeColorSpace(sk_ref_sp(rec.fColorSpace));
   *             }
   *             this->internalDrawDeviceWithFilter(/*src=*/nullptr, priorDevice, filters, restorePaint,
   *                                                DeviceCompatibleWithFilter::kUnknown,
   *                                                filterColorInfo);
   *         }
   *
   *         // Regardless of if we drew the "restored" image filter or not, mark the layer as empty
   *         // until the restore() since we don't care about any of its content.
   *         abortLayer();
   *         return;
   *     } else {
   *         // TODO(b/329700315): Once dithers can be anchored more flexibly, we can return to
   *         // universally adding padding even for layers w/o filters. This change would simplify layer
   *         // prep and restore logic and allow us to flexibly switch the sampling to linear if NN has
   *         // issues on certain hardware.
   *         if (!filters.empty()) {
   *             // Add a buffer of padding so that image filtering can avoid accessing unitialized data
   *             // and switch from shader-decal'ing to clamping.
   *             auto paddedLayerBounds = layerBounds;
   *             paddedLayerBounds.outset(skif::LayerSpace<SkISize>({1, 1}));
   *             if (paddedLayerBounds.left() < layerBounds.left() &&
   *                 paddedLayerBounds.top() < layerBounds.top() &&
   *                 paddedLayerBounds.right() > layerBounds.right() &&
   *                 paddedLayerBounds.bottom() > layerBounds.bottom()) {
   *                 // The outset was not saturated to INT_MAX, so the transparent pixels can be
   *                 // preserved.
   *                 layerBounds = paddedLayerBounds;
   *                 paddedLayer = true;
   *             }
   *         }
   *     }
   *
   *     sk_sp<SkDevice> newDevice;
   *     if (strategy == kFullLayer_SaveLayerStrategy) {
   *         SkASSERT(!layerBounds.isEmpty());
   *
   *         SkColorType layerColorType;
   *         if (coverageOnly) {
   *             layerColorType = kAlpha_8_SkColorType;
   *         } else {
   *             layerColorType = SkToBool(rec.fSaveLayerFlags & kF16ColorType)
   *                                     ? kRGBA_F16_SkColorType
   *                                     : image_filter_color_type(priorDevice->imageInfo().colorInfo());
   *         }
   *         SkImageInfo info =
   *                 SkImageInfo::Make(layerBounds.width(),
   *                                   layerBounds.height(),
   *                                   layerColorType,
   *                                   kPremul_SkAlphaType,
   *                                   rec.fColorSpace ? sk_ref_sp(rec.fColorSpace)
   *                                                   : priorDevice->imageInfo().refColorSpace());
   *
   *         SkPixelGeometry geo = rec.fSaveLayerFlags & kPreserveLCDText_SaveLayerFlag
   *                                       ? fProps.pixelGeometry()
   *                                       : kUnknown_SkPixelGeometry;
   *         const auto createInfo = SkDevice::CreateInfo(info, geo, fAllocator.get());
   *         // Use the original paint as a hint so that it includes the image filter
   *         newDevice = priorDevice->createDevice(createInfo, rec.fPaint);
   *     }
   *
   *     bool initBackdrop = (rec.fSaveLayerFlags & kInitWithPrevious_SaveLayerFlag) || rec.fBackdrop;
   *     if (!newDevice) {
   *         // Either we weren't meant to allocate a full layer, or the full layer creation failed.
   *         // Using an explicit NoPixelsDevice lets us reflect what the layer state would have been
   *         // on success (or kFull_LayerStrategy) while squashing draw calls that target something that
   *         // doesn't exist.
   *         newDevice = sk_make_sp<SkNoPixelsDevice>(SkIRect::MakeWH(layerBounds.width(),
   *                                                                  layerBounds.height()),
   *                                                  fProps, this->imageInfo().refColorSpace());
   *         initBackdrop = false;
   *     }
   *
   *     // Clip while the device coordinate space is the identity so it's easy to define the rect that
   *     // excludes the added padding pixels. This ensures they remain cleared to transparent black.
   *     if (paddedLayer) {
   *         newDevice->clipRect(SkRect::Make(newDevice->devClipBounds().makeInset(1, 1)),
   *                             SkClipOp::kIntersect, /*aa=*/false);
   *     }
   *
   *     // Configure device to match determined mapping for any image filters.
   *     // The setDeviceCoordinateSystem applies the prior device's global transform since
   *     // 'newLayerMapping' only defines the transforms between the two devices and it must be updated
   *     // to the global coordinate system.
   *     newDevice->setDeviceCoordinateSystem(
   *             priorDevice->deviceToGlobal() * newLayerMapping.layerToDevice(),
   *             newLayerMapping.deviceToLayer() * priorDevice->globalToDevice(),
   *             newLayerMapping.layerMatrix(),
   *             layerBounds.left(),
   *             layerBounds.top());
   *
   *     if (initBackdrop) {
   *         SkASSERT(!coverageOnly);
   *         SkPaint backdropPaint;
   *         FilterToSpan backdropAsSpan(rec.fBackdrop);
   *         // The new device was constructed to be compatible with 'filter', not necessarily
   *         // 'rec.fBackdrop', so allow DrawDeviceWithFilter to transform the prior device contents
   *         // if necessary to evaluate the backdrop filter. If no filters are involved, then the
   *         // devices differ by integer translations and are always compatible.
   *         bool scaleBackdrop = rec.fExperimentalBackdropScale != 1.0f;
   *         auto compat = (!filters.empty() || rec.fBackdrop || scaleBackdrop)
   *                 ? DeviceCompatibleWithFilter::kUnknown : DeviceCompatibleWithFilter::kYes;
   *         // Using the color info of 'newDevice' is equivalent to using 'rec.fColorSpace'.
   *         this->internalDrawDeviceWithFilter(priorDevice,     // src
   *                                            newDevice.get(), // dst
   *                                            backdropAsSpan,
   *                                            backdropPaint,
   *                                            compat,
   *                                            newDevice->imageInfo().colorInfo(),
   *                                            rec.fExperimentalBackdropScale,
   *                                            rec.fBackdropTileMode);
   *     }
   *
   *     fMCRec->newLayer(std::move(newDevice), filters, restorePaint, coverageOnly, paddedLayer);
   *     fQuickRejectBounds = this->computeDeviceClipBounds();
   * }
   * ```
   */
  private fun internalSaveBehind(localBounds: SkRect?) {
    TODO("Implement internalSaveBehind")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::internalSaveBehind(const SkRect* localBounds) {
   *     SkDevice* device = this->topDevice();
   *
   *     // Map the local bounds into the top device's coordinate space (this is not
   *     // necessarily the full global CTM transform).
   *     SkIRect devBounds;
   *     if (localBounds) {
   *         SkRect tmp;
   *         device->localToDevice().mapRect(&tmp, *localBounds);
   *         if (!devBounds.intersect(tmp.round(), device->devClipBounds())) {
   *             devBounds.setEmpty();
   *         }
   *     } else {
   *         devBounds = device->devClipBounds();
   *     }
   *     if (devBounds.isEmpty()) {
   *         return;
   *     }
   *
   *     // This is getting the special image from the current device, which is then drawn into (both by
   *     // a client, and the drawClippedToSaveBehind below). Since this is not saving a layer, with its
   *     // own device, we need to explicitly copy the back image contents so that its original content
   *     // is available when we splat it back later during restore.
   *     auto backImage = device->snapSpecial(devBounds, /* forceCopy= */ true);
   *     if (!backImage) {
   *         return;
   *     }
   *
   *     // we really need the save, so we can wack the fMCRec
   *     this->checkForDeferredSave();
   *
   *     fMCRec->fBackImage =
   *             std::make_unique<BackImage>(BackImage{std::move(backImage), devBounds.topLeft()});
   *
   *     SkPaint paint;
   *     paint.setBlendMode(SkBlendMode::kClear);
   *     this->drawClippedToSaveBehind(paint);
   * }
   * ```
   */
  private fun internalConcat44(m: SkM44) {
    TODO("Implement internalConcat44")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::internalConcat44(const SkM44& m) {
   *     this->checkForDeferredSave();
   *
   *     fMCRec->fMatrix.preConcat(m);
   *
   *     this->topDevice()->setGlobalCTM(fMCRec->fMatrix);
   * }
   * ```
   */
  private fun internalSave() {
    TODO("Implement internalSave")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::internalSave() {
   *     fMCRec = new (fMCStack.push_back()) MCRec(fMCRec);
   *
   *     this->topDevice()->pushClipStack();
   * }
   * ```
   */
  private fun internalRestore() {
    TODO("Implement internalRestore")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::internalRestore() {
   *     SkASSERT(!fMCStack.empty());
   *
   *     // now detach these from fMCRec so we can pop(). Gets freed after its drawn
   *     std::unique_ptr<Layer> layer = std::move(fMCRec->fLayer);
   *     std::unique_ptr<BackImage> backImage = std::move(fMCRec->fBackImage);
   *
   *     // now do the normal restore()
   *     fMCRec->~MCRec();       // balanced in save()
   *     fMCStack.pop_back();
   *     fMCRec = (MCRec*) fMCStack.back();
   *
   *     if (!fMCRec) {
   *         // This was the last record, restored during the destruction of the SkCanvas
   *         return;
   *     }
   *
   *     this->topDevice()->popClipStack();
   *     this->topDevice()->setGlobalCTM(fMCRec->fMatrix);
   *
   *     if (backImage) {
   *         SkPaint paint;
   *         paint.setBlendMode(SkBlendMode::kDstOver);
   *         this->topDevice()->drawSpecial(backImage->fImage.get(),
   *                                        SkMatrix::Translate(backImage->fLoc),
   *                                        SkSamplingOptions(),
   *                                        paint);
   *     }
   *
   *     // Draw the layer's device contents into the now-current older device. We can't call public
   *     // draw functions since we don't want to record them.
   *     if (layer && !layer->fDevice->isNoPixelsDevice() && !layer->fDiscard) {
   *         layer->fDevice->setImmutable();
   *
   *         // Don't go through AutoLayerForImageFilter since device draws are so closely tied to
   *         // internalSaveLayer and internalRestore.
   *         if (this->predrawNotify()) {
   *             SkDevice* dstDev = this->topDevice();
   *             if (!layer->fImageFilters.empty()) {
   *                 auto compat = layer->fIncludesPadding ? DeviceCompatibleWithFilter::kYesWithPadding
   *                                                       : DeviceCompatibleWithFilter::kYes;
   *                 this->internalDrawDeviceWithFilter(layer->fDevice.get(), // src
   *                                                    dstDev,               // dst
   *                                                    layer->fImageFilters,
   *                                                    layer->fPaint,
   *                                                    compat,
   *                                                    layer->fDevice->imageInfo().colorInfo(),
   *                                                    /*scaleFactor=*/1.0f,
   *                                                    /*srcTileMode=*/SkTileMode::kDecal,
   *                                                    layer->fIsCoverage);
   *             } else {
   *                 // NOTE: We don't just call internalDrawDeviceWithFilter with a null filter
   *                 // because we want to take advantage of overridden drawDevice functions for
   *                 // document-based devices.
   *                 SkASSERT(!layer->fIsCoverage && !layer->fIncludesPadding);
   *                 SkSamplingOptions sampling;
   *                 dstDev->drawDevice(layer->fDevice.get(), sampling, layer->fPaint);
   *             }
   *         }
   *     }
   *
   *     // Reset the clip restriction if the restore went past the save point that had added it.
   *     if (this->getSaveCount() < fClipRestrictionSaveCount) {
   *         fClipRestrictionRect.setEmpty();
   *         fClipRestrictionSaveCount = -1;
   *     }
   *     // Update the quick-reject bounds in case the restore changed the top device or the
   *     // removed save record had included modifications to the clip stack.
   *     fQuickRejectBounds = this->computeDeviceClipBounds();
   *     this->validateClip();
   * }
   * ```
   */
  private fun internalDrawDeviceWithFilter(
    src: SkDevice?,
    dst: SkDevice?,
    filters: FilterSpan,
    paint: SkPaint,
    compat: DeviceCompatibleWithFilter,
    filterColorInfo: SkColorInfo,
    scaleFactor: SkScalar = TODO(),
    srcTileMode: SkTileMode = TODO(),
    srcIsCoverageLayer: Boolean = TODO(),
  ) {
    TODO("Implement internalDrawDeviceWithFilter")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::internalDrawDeviceWithFilter(SkDevice* src,
   *                                             SkDevice* dst,
   *                                             FilterSpan filters,
   *                                             const SkPaint& paint,
   *                                             DeviceCompatibleWithFilter compat,
   *                                             const SkColorInfo& filterColorInfo,
   *                                             SkScalar scaleFactor,
   *                                             SkTileMode srcTileMode,
   *                                             bool srcIsCoverageLayer) {
   *     // The dst is always required, the src can be null if 'filter' is non-null and does not require
   *     // a source image. For regular filters, 'src' is the layer and 'dst' is the parent device. For
   *     // backdrop filters, 'src' is the parent device and 'dst' is the layer.
   *     SkASSERT(dst);
   *
   *     sk_sp<SkColorSpace> filterColorSpace = filterColorInfo.refColorSpace();
   *
   *     const SkColorType filterColorType =
   *             srcIsCoverageLayer ? kAlpha_8_SkColorType : image_filter_color_type(filterColorInfo);
   *
   *     // 'filter' sees the src device's buffer as the implicit input image, and processes the image
   *     // in this device space (referred to as the "layer" space). However, the filter
   *     // parameters need to respect the current matrix, which is not necessarily the local matrix that
   *     // was set on 'src' (e.g. because we've popped src off the stack already).
   *     SkM44 localToSrc = src ? (src->globalToDevice() * fMCRec->fMatrix) : SkM44();
   *     SkISize srcDims = src ? src->imageInfo().dimensions() : SkISize::Make(0, 0);
   *
   *     // Whether or not we need to make a transformed tmp image from 'src', and what that transform is
   *     skif::LayerSpace<SkMatrix> srcToLayer;
   *
   *     skif::Mapping mapping;
   *     skif::LayerSpace<SkIRect> requiredInput;
   *     skif::DeviceSpace<SkIRect> outputBounds{dst->devClipBounds()};
   *     if (compat != DeviceCompatibleWithFilter::kUnknown) {
   *         // Just use the relative transform from src to dst and the src's whole image, since
   *         // internalSaveLayer should have already determined what was necessary. We explicitly
   *         // construct the inverse (dst->src) to avoid the case where src's and dst's coord transforms
   *         // were individually invertible by SkM44::invert() but their product is considered not
   *         // invertible by SkMatrix::invert(). When this happens the matrices are already poorly
   *         // conditioned so getRelativeTransform() gives us something reasonable.
   *         SkASSERT(src);
   *         SkASSERT(scaleFactor == 1.0f);
   *         SkASSERT(!srcDims.isEmpty());
   *
   *         mapping = skif::Mapping(src->getRelativeTransform(*dst),
   *                                 dst->getRelativeTransform(*src),
   *                                 localToSrc);
   *         requiredInput = skif::LayerSpace<SkIRect>(SkIRect::MakeSize(srcDims));
   *         srcToLayer = skif::LayerSpace<SkMatrix>(SkMatrix::I());
   *     } else {
   *         // Compute the image filter mapping by decomposing the local->device matrix of dst and
   *         // re-determining the required input.
   *         auto mappingAndBounds = get_layer_mapping_and_bounds(
   *                 filters, dst->localToDevice44(), outputBounds, {}, SkTPin(scaleFactor, 0.f, 1.f));
   *         if (!mappingAndBounds) {
   *             return;
   *         }
   *
   *         std::tie(mapping, requiredInput) = *mappingAndBounds;
   *         if (src) {
   *             if (!requiredInput.isEmpty()) {
   *                 // The above mapping transforms from local to dst's device space, where the layer
   *                 // space represents the intermediate buffer. Now we need to determine the transform
   *                 // from src to intermediate to prepare the input to the filter.
   *                 SkM44 srcToLocal;
   *                 if (!localToSrc.invert(&srcToLocal)) {
   *                     return;
   *                 }
   *                 srcToLayer = skif::LayerSpace<SkMatrix>((mapping.layerMatrix()*srcToLocal).asM33());
   *             } // Else no input is needed which can happen if a backdrop filter that doesn't use src
   *         } else {
   *             // Trust the caller that no input was required, but keep the calculated mapping
   *             requiredInput = skif::LayerSpace<SkIRect>::Empty();
   *         }
   *     }
   *
   *     // Start out with an empty source image, to be replaced with the snapped 'src' device.
   *     auto backend = dst->createImageFilteringBackend(src ? src->surfaceProps() : dst->surfaceProps(),
   *                                                     filterColorType);
   *     skif::Stats stats;
   *     skif::Context ctx{std::move(backend),
   *                       mapping,
   *                       requiredInput,
   *                       skif::FilterResult{},
   *                       filterColorSpace.get(),
   *                       &stats};
   *
   *     skif::FilterResult source;
   *     if (src && !requiredInput.isEmpty()) {
   *         skif::LayerSpace<SkIRect> srcSubset;
   *         if (!srcToLayer.inverseMapRect(requiredInput, &srcSubset)) {
   *             return;
   *         }
   *
   *         // Include the layer in the offscreen count
   *         ctx.markNewSurface();
   *
   *         auto availSrc = skif::LayerSpace<SkIRect>(src->size()).relevantSubset(
   *                 srcSubset, srcTileMode);
   *
   *         if (SkMatrix(srcToLayer).isScaleTranslate()) {
   *             // Apply the srcToLayer transformation directly while snapping an image from the src
   *             // device. Calculate the subset of requiredInput that corresponds to srcSubset that was
   *             // restricted to the actual src dimensions.
   *             auto requiredSubset = srcToLayer.mapRect(availSrc);
   *             if (requiredSubset.width() == availSrc.width() &&
   *                 requiredSubset.height() == availSrc.height()) {
   *                 // Unlike snapSpecialScaled(), snapSpecial() can avoid a copy when the underlying
   *                 // representation permits it.
   *                 source = {src->snapSpecial(SkIRect(availSrc)), requiredSubset.topLeft()};
   *             } else {
   *                 SkASSERT(compat == DeviceCompatibleWithFilter::kUnknown);
   *                 source = {src->snapSpecialScaled(SkIRect(availSrc),
   *                                                  SkISize(requiredSubset.size())),
   *                           requiredSubset.topLeft()};
   *                 ctx.markNewSurface();
   *             }
   *         }
   *
   *         if (compat == DeviceCompatibleWithFilter::kYesWithPadding) {
   *             // Padding was added to the source image when the 'src' SkDevice was created, so inset
   *             // to allow bounds tracking to skip shader-based tiling when possible.
   *             SkASSERT(!filters.empty());
   *             source = source.insetForSaveLayer();
   *         } else if (compat == DeviceCompatibleWithFilter::kYes) {
   *             // Do nothing, leave `source` as-is; FilterResult will automatically augment the image
   *             // sampling as needed to be visually equivalent to the more optimal kYesWithPadding case
   *         } else if (source) {
   *             // A backdrop filter that succeeded in snapSpecial() or snapSpecialScaled(), but since
   *             // the 'src' device wasn't prepared with 'requiredInput' in mind, add clamping.
   *             source = source.applyCrop(ctx, source.layerBounds(), srcTileMode);
   *         } else if (!requiredInput.isEmpty()) {
   *             // Otherwise snapSpecialScaled() failed or the transform was complex, so snap the source
   *             // image at its original resolution and then apply srcToLayer to map to the effective
   *             // layer coordinate space.
   *             source = {src->snapSpecial(SkIRect(availSrc)), availSrc.topLeft()};
   *             // We adjust the desired output of the applyCrop() because ctx was original set to
   *             // fulfill 'requiredInput', which is valid *after* we apply srcToLayer. Use the original
   *             // 'srcSubset' for the desired output so that the tilemode applied to the available
   *             // subset is not discarded as a no-op.
   *             source = source.applyCrop(ctx.withNewDesiredOutput(srcSubset),
   *                                       source.layerBounds(),
   *                                       srcTileMode)
   *                            .applyTransform(ctx, srcToLayer, SkFilterMode::kLinear);
   *         }
   *     } // else leave 'source' as the empty image
   *
   *     // Evaluate the image filter, with a context pointing to the source snapped from 'src' and
   *     // possibly transformed into the intermediate layer coordinate space.
   *     ctx = ctx.withNewDesiredOutput(mapping.deviceToLayer(outputBounds))
   *              .withNewSource(source);
   *
   *     // Here, we allow a single-element FilterSpan with a null entry, to simplify the loop:
   *     sk_sp<SkImageFilter> nullFilter;
   *     FilterSpan filtersOrNull = filters.empty() ? FilterSpan{&nullFilter, 1} : filters;
   *
   *     for (const sk_sp<SkImageFilter>& filter : filtersOrNull) {
   *         auto result = filter ? as_IFB(filter)->filterImage(ctx) : source;
   *
   *         if (srcIsCoverageLayer) {
   *             SkASSERT(dst->useDrawCoverageMaskForMaskFilters());
   *             // TODO: Can FilterResult optimize this in any meaningful way if it still has to go
   *             // through drawCoverageMask that requires an image (vs a coverage shader)?
   *             auto [coverageMask, origin] = result.imageAndOffset(ctx);
   *             if (coverageMask) {
   *                 SkM44 deviceMatrixWithOffset = mapping.layerToDevice();
   *                 deviceMatrixWithOffset.preTranslate(origin.x(), origin.y());
   *                 dst->drawCoverageMask(
   *                         coverageMask.get(), deviceMatrixWithOffset.asM33(),
   *                         result.sampling(), paint);
   *             }
   *         } else {
   *             result = apply_alpha_and_colorfilter(ctx, result, paint);
   *             result.draw(ctx, dst, paint.getBlender());
   *         }
   *     }
   *
   *     stats.reportStats();
   * }
   * ```
   */
  private fun wouldOverwriteEntireSurface(
    rect: SkRect?,
    paint: SkPaint?,
    flags: SkEnumBitMask<PredrawFlags>,
  ): Boolean {
    TODO("Implement wouldOverwriteEntireSurface")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkCanvas::wouldOverwriteEntireSurface(const SkRect* rect, const SkPaint* paint,
   *                                            SkEnumBitMask<PredrawFlags> flags) const {
   *     // Convert flags to a ShaderOverrideOpacity enum
   *     auto overrideOpacity = (flags & PredrawFlags::kOpaqueShaderOverride) ?
   *                                     SkPaintPriv::kOpaque_ShaderOverrideOpacity :
   *                            (flags & PredrawFlags::kNonOpaqueShaderOverride) ?
   *                                     SkPaintPriv::kNotOpaque_ShaderOverrideOpacity :
   *                                     SkPaintPriv::kNone_ShaderOverrideOpacity;
   *
   *     const SkISize size = this->getBaseLayerSize();
   *     const SkRect bounds = SkRect::MakeIWH(size.width(), size.height());
   *
   *     // if we're clipped at all, we can't overwrite the entire surface
   *     {
   *         const SkDevice* root = this->rootDevice();
   *         const SkDevice* top = this->topDevice();
   *         if (root != top) {
   *             return false;   // we're in a saveLayer, so conservatively don't assume we'll overwrite
   *         }
   *         if (!root->isClipWideOpen()) {
   *             return false;
   *         }
   *     }
   *
   *     if (rect) {
   *         if (!this->getTotalMatrix().isScaleTranslate()) {
   *             return false; // conservative
   *         }
   *
   *         SkRect devRect;
   *         this->getTotalMatrix().mapRectScaleTranslate(&devRect, *rect);
   *         if (!devRect.contains(bounds)) {
   *             return false;
   *         }
   *     }
   *
   *     if (paint) {
   *         SkPaint::Style paintStyle = paint->getStyle();
   *         if (!(paintStyle == SkPaint::kFill_Style ||
   *               paintStyle == SkPaint::kStrokeAndFill_Style)) {
   *             return false;
   *         }
   *         if (paint->getMaskFilter() || paint->getPathEffect() || paint->getImageFilter()) {
   *             return false; // conservative
   *         }
   *     }
   *     return SkPaintPriv::Overwrites(paint, overrideOpacity);
   * }
   * ```
   */
  private fun androidFrameworkIsClipAA(): Boolean {
    TODO("Implement androidFrameworkIsClipAA")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkCanvas::androidFramework_isClipAA() const {
   *     return this->topDevice()->isClipAntiAliased();
   * }
   * ```
   */
  private fun internalPrivateResetClip() {
    TODO("Implement internalPrivateResetClip")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCanvas::internal_private_resetClip() {
   *     this->checkForDeferredSave();
   *     this->onResetClip();
   * }
   * ```
   */
  public open fun internalPrivateAsPaintFilterCanvas(): SkPaintFilterCanvas {
    TODO("Implement internalPrivateAsPaintFilterCanvas")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual SkPaintFilterCanvas* internal_private_asPaintFilterCanvas() const { return nullptr; }
   * ```
   */
  private fun computeDeviceClipBounds(outsetForAA: Boolean = TODO()): Int {
    TODO("Implement computeDeviceClipBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect SkCanvas::computeDeviceClipBounds(bool outsetForAA) const {
   *     const SkDevice* dev = this->topDevice();
   *     if (dev->isClipEmpty()) {
   *         return SkRect::MakeEmpty();
   *     } else {
   *         SkRect devClipBounds =
   *                 SkMatrixPriv::MapRect(dev->deviceToGlobal(), SkRect::Make(dev->devClipBounds()));
   *         if (outsetForAA) {
   *             // Expand bounds out by 1 in case we are anti-aliasing.  We store the
   *             // bounds as floats to enable a faster quick reject implementation.
   *             devClipBounds.outset(1.f, 1.f);
   *         }
   *         return devClipBounds;
   *     }
   * }
   * ```
   */
  private fun canAttemptBlurredRRectDraw(paint: SkPaint): SkBlurMaskFilterImpl {
    TODO("Implement canAttemptBlurredRRectDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkBlurMaskFilterImpl* SkCanvas::canAttemptBlurredRRectDraw(const SkPaint& paint) const {
   *     if (!this->topDevice()->useDrawCoverageMaskForMaskFilters()) {
   *         // Perform a regular draw in the legacy mask filter case.
   *         return nullptr;
   *     }
   *
   *     if (paint.getPathEffect()) {
   *         return nullptr;
   *     }
   *
   *     // TODO: Once stroke-and-fill goes away, we can check the paint's style directly.
   *     if (SkStrokeRec(paint).getStyle() != SkStrokeRec::kFill_Style) {
   *         return nullptr;
   *     }
   *
   *     const SkMaskFilterBase* maskFilter = as_MFB(paint.getMaskFilter());
   *     if (!maskFilter || maskFilter->type() != SkMaskFilterBase::Type::kBlur) {
   *         return nullptr;
   *     }
   *
   *     const SkBlurMaskFilterImpl* blurMaskFilter =
   *             static_cast<const SkBlurMaskFilterImpl*>(maskFilter);
   *     if (blurMaskFilter->blurStyle() != kNormal_SkBlurStyle) {
   *         return nullptr;
   *     }
   *
   *     if (!this->getTotalMatrix().isSimilarity()) {
   *         // TODO: If the CTM does more than just translation, rotation, and uniform scale, then the
   *         // results of analytic blurring will be different than mask filter blurring. Skip the
   *         // specialized path in this case.
   *         return nullptr;
   *     }
   *
   *     return blurMaskFilter;
   * }
   * ```
   */
  private fun attemptBlurredRRectDraw(
    rrect: SkRRect,
    blurMaskFilter: SkBlurMaskFilterImpl?,
    paint: SkPaint,
    flags: SkEnumBitMask<PredrawFlags>,
  ): AutoLayerForImageFilter? {
    TODO("Implement attemptBlurredRRectDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * std::optional<AutoLayerForImageFilter> SkCanvas::attemptBlurredRRectDraw(
   *         const SkRRect& rrect,
   *         const SkBlurMaskFilterImpl* blurMaskFilter,
   *         const SkPaint& paint,
   *         SkEnumBitMask<PredrawFlags> flags) {
   *     SkASSERT(blurMaskFilter && blurMaskFilter == this->canAttemptBlurredRRectDraw(paint) &&
   *              !(flags & PredrawFlags::kSkipMaskFilterAutoLayer));
   *     const SkRect& bounds = rrect.getBounds();
   *
   *     auto layer = this->aboutToDraw(paint, &bounds, flags | PredrawFlags::kSkipMaskFilterAutoLayer);
   *     if (!layer) {
   *         // predrawNotify failed.
   *         return std::nullopt;
   *     }
   *
   *     const float deviceSigma = blurMaskFilter->computeXformedSigma(this->getTotalMatrix());
   *     if (this->topDevice()->drawBlurredRRect(rrect, layer->paint(), deviceSigma)) {
   *         // Analytic draw was successful.
   *         return std::nullopt;
   *     }
   *
   *     // Fall back on a regular draw, adding any mask filter layer we skipped earlier. We know the
   *     // paint has a mask filter here, otherwise we would have failed the can_attempt check above.
   *     layer->addMaskFilterLayer(&bounds);
   *     return layer;
   * }
   * ```
   */
  private fun validateClip() {
    TODO("Implement validateClip")
  }

  public data class SaveLayerRec public constructor(
    public val fBounds: Int?,
    public val fPaint: Int?,
    public var fFilters: Int,
    public val fBackdrop: Int?,
    public var fBackdropTileMode: Int,
    public val fColorSpace: SkColorSpace?,
    public var fSaveLayerFlags: SaveLayerFlags,
    private var fExperimentalBackdropScale: Int,
  )

  public data class Lattice public constructor(
    public val fXDivs: Int?,
    public val fYDivs: Int?,
    public val fRectTypes: Lattice.RectType?,
    public var fXCount: Int,
    public var fYCount: Int,
    public val fBounds: Int?,
    public val fColors: Int?,
  ) {
    public enum class RectType {
      kDefault,
      kTransparent,
      kFixedColor,
    }
  }

  public data class ImageSetEntry public constructor(
    public var fImage: Int,
    public var fSrcRect: Int,
    public var fDstRect: Int,
    public var fMatrixIndex: Int,
    public var fAlpha: Float,
    public var fAAFlags: UInt,
    public var fHasClip: Boolean,
  ) {
    public fun assign(param0: undefined.ImageSetEntry) {
      TODO("Implement assign")
    }
  }

  public data class Layer public constructor(
    public var fDevice: Int,
    public var fPaint: Int,
    public var fIsCoverage: Boolean,
    public var fDiscard: Boolean,
    public var fIncludesPadding: Boolean,
  )

  public data class BackImage public constructor(
    public var fImage: Int,
    public var fLoc: Int,
  ) {
    public fun assign(param0: undefined.BackImage) {
      TODO("Implement assign")
    }
  }

  public data class MCRec public constructor(
    public var fLayer: Int,
    public var fDevice: SkDevice?,
    public var fBackImage: Int,
    public var fMatrix: Int,
    public var fDeferredSaveCount: Int,
  ) {
    public fun newLayer(
      layerDevice: SkSp<SkDevice>,
      filters: FilterSpan,
      restorePaint: SkPaint,
      layerIsCoverage: Boolean,
      includesPadding: Boolean,
    ) {
      TODO("Implement newLayer")
    }

    public fun reset(device: SkDevice?) {
      TODO("Implement reset")
    }
  }

  public enum class SaveLayerFlagsSet {
    kPreserveLCDText_SaveLayerFlag,
    kInitWithPrevious_SaveLayerFlag,
    kF16ColorType,
  }

  public enum class PointMode {
    kPoints_PointMode,
    kLines_PointMode,
    kPolygon_PointMode,
  }

  public enum class SrcRectConstraint {
    kStrict_SrcRectConstraint,
    kFast_SrcRectConstraint,
  }

  public enum class QuadAAFlags {
    kLeft_QuadAAFlag,
    kTop_QuadAAFlag,
    kRight_QuadAAFlag,
    kBottom_QuadAAFlag,
    kNone_QuadAAFlags,
    kAll_QuadAAFlags,
  }

  public enum class SaveLayerStrategy {
    kFullLayer_SaveLayerStrategy,
    kNoLayer_SaveLayerStrategy,
  }

  public enum class ClipEdgeStyle {
    kHard_ClipEdgeStyle,
    kSoft_ClipEdgeStyle,
  }

  public enum class PredrawFlags {
    kNone,
    kOpaqueShaderOverride,
    kNonOpaqueShaderOverride,
    kCheckForOverwrite,
    kSkipMaskFilterAutoLayer,
  }

  public enum class DeviceCompatibleWithFilter {
    kUnknown,
    kYes,
    kYesWithPadding,
  }

  public companion object {
    public val kMaxFiltersPerLayer: Int = TODO("Initialize kMaxFiltersPerLayer")

    public val kMCRecCount: Int = TODO("Initialize kMCRecCount")

    /**
     * C++ original:
     * ```cpp
     * std::unique_ptr<SkCanvas> SkCanvas::MakeRasterDirect(const SkImageInfo& info, void* pixels,
     *                                                      size_t rowBytes, const SkSurfaceProps* props) {
     *     if (!SkSurfaceValidateRasterInfo(info, rowBytes)) {
     *         return nullptr;
     *     }
     *
     *     SkBitmap bitmap;
     *     if (!bitmap.installPixels(info, pixels, rowBytes)) {
     *         return nullptr;
     *     }
     *
     *     return props ?
     *         std::make_unique<SkCanvas>(bitmap, *props) :
     *         std::make_unique<SkCanvas>(bitmap);
     * }
     * ```
     */
    public fun makeRasterDirect(
      info: SkImageInfo,
      pixels: Unit?,
      rowBytes: ULong,
      props: SkSurfaceProps? = TODO(),
    ): SkCanvas? {
      TODO("Implement makeRasterDirect")
    }

    /**
     * C++ original:
     * ```cpp
     * static std::unique_ptr<SkCanvas> MakeRasterDirectN32(int width, int height, SkPMColor* pixels,
     *                                                          size_t rowBytes) {
     *         return MakeRasterDirect(SkImageInfo::MakeN32Premul(width, height), pixels, rowBytes);
     *     }
     * ```
     */
    public fun makeRasterDirectN32(
      width: Int,
      height: Int,
      pixels: SkPMColor?,
      rowBytes: ULong,
    ): SkCanvas? {
      TODO("Implement makeRasterDirectN32")
    }
  }
}
