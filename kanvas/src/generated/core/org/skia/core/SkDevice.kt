package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.CharArray
import kotlin.Float
import kotlin.Int
import org.skia.foundation.SkColor
import org.skia.foundation.SkColorType
import org.skia.foundation.SkData
import org.skia.foundation.SkDrawable
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPixmap
import org.skia.foundation.SkRRect
import org.skia.foundation.SkRefCnt
import org.skia.foundation.SkRegion
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan
import org.skia.gpu.Recorder
import org.skia.gpu.ganesh.GrRecordingContext
import org.skia.math.SkIPoint
import org.skia.math.SkIRect
import org.skia.math.SkISize
import org.skia.math.SkM44
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRSXform
import org.skia.math.SkRect
import org.skia.utils.Slug
import undefined.SkColor4f
import org.skia.gpu.Device as GpuDevice
import org.skia.gpu.ganesh.Device as GaneshDevice

/**
 * C++ original:
 * ```cpp
 * class SkDevice : public SkRefCnt {
 * public:
 *     SkDevice(const SkImageInfo&, const SkSurfaceProps&);
 *
 *     // -- Surface properties and metadata
 *
 *     /**
 *      *  Return ImageInfo for this device. If the canvas is not backed by pixels
 *      *  (cpu or gpu), then the info's ColorType will be kUnknown_SkColorType.
 *      */
 *     const SkImageInfo& imageInfo() const { return fInfo; }
 *
 *     int width() const { return this->imageInfo().width(); }
 *     int height() const { return this->imageInfo().height(); }
 *
 *     bool isOpaque() const { return this->imageInfo().isOpaque(); }
 *
 *     // NOTE: Image dimensions as a rect, *not* the current restricted clip bounds.
 *     SkIRect bounds() const { return SkIRect::MakeWH(this->width(), this->height()); }
 *     SkISize size() const { return this->imageInfo().dimensions(); }
 *
 *     /**
 *      *  Return SurfaceProps for this device.
 *      */
 *     const SkSurfaceProps& surfaceProps() const {
 *         return fSurfaceProps;
 *     }
 *
 *     SkScalerContextFlags scalerContextFlags() const;
 *
 *     virtual SkStrikeDeviceInfo strikeDeviceInfo() const {
 *         return {fSurfaceProps, this->scalerContextFlags(), nullptr};
 *     }
 *
 *     // -- Direct pixel manipulation
 *
 *     /**
 *      *  Write the pixels in 'src' into this Device at the specified x,y offset. The caller is
 *      *  responsible for "pre-clipping" the src.
 *      */
 *     bool writePixels(const SkPixmap& src, int x, int y) { return this->onWritePixels(src, x, y); }
 *
 *     /**
 *      *  Read pixels from this Device at the specified x,y offset into dst. The caller is
 *      *  responsible for "pre-clipping" the dst
 *      */
 *     bool readPixels(const SkPixmap& dst, int x, int y) { return this->onReadPixels(dst, x, y); }
 *
 *     /**
 *      *  Try to get write-access to the pixels behind the device. If successful, this returns true
 *      *  and fills-out the pixmap parameter. On success it also bumps the genID of the underlying
 *      *  bitmap.
 *      *
 *      *  On failure, returns false and ignores the pixmap parameter.
 *      */
 *     bool accessPixels(SkPixmap* pmap);
 *
 *     /**
 *      *  Try to get read-only-access to the pixels behind the device. If successful, this returns
 *      *  true and fills-out the pixmap parameter.
 *      *
 *      *  On failure, returns false and ignores the pixmap parameter.
 *      */
 *     bool peekPixels(SkPixmap*);
 *
 *
 *     // -- Device's transform (both current transform affecting draws, and its fixed global mapping)
 *
 *     /**
 *      *  Returns the transformation that maps from the local space to the device's coordinate space.
 *      */
 *     const SkM44& localToDevice44() const { return fLocalToDevice; }
 *     const SkMatrix& localToDevice() const { return fLocalToDevice33; }
 *
 *     /**
 *      *  Return the device's coordinate space transform: this maps from the device's coordinate space
 *      *  into the global canvas' space (or root device space). This includes the translation
 *      *  necessary to account for the device's origin.
 *      */
 *     const SkM44& deviceToGlobal() const { return fDeviceToGlobal; }
 *     /**
 *      *  Return the inverse of getDeviceToGlobal(), mapping from the global canvas' space (or root
 *      *  device space) into this device's coordinate space.
 *      */
 *     const SkM44& globalToDevice() const { return fGlobalToDevice; }
 *     /**
 *      *  DEPRECATED: This asserts that 'getDeviceToGlobal' is a translation matrix with integer
 *      *  components. In the future some SkDevices will have more complex device-to-global transforms,
 *      *  so getDeviceToGlobal() or getRelativeTransform() should be used instead.
 *      */
 *     SkIPoint getOrigin() const;
 *     /**
 *      * Returns true when this device's pixel grid is axis aligned with the global coordinate space,
 *      * and any relative translation between the two spaces is in integer pixel units.
 *      */
 *     bool isPixelAlignedToGlobal() const;
 *     /**
 *      * Get the transformation from this device's coordinate system to the provided device space.
 *      * This transform can be used to draw this device into the provided device, such that once
 *      * that device is drawn to the root device, the net effect will be that this device's contents
 *      * have been transformed by the global CTM.
 *      */
 *     SkM44 getRelativeTransform(const SkDevice&) const;
 *
 *     void setLocalToDevice(const SkM44& localToDevice) {
 *         fLocalToDevice = localToDevice;
 *         fLocalToDevice33 = fLocalToDevice.asM33();
 *         fLocalToDeviceDirty = true;
 *     }
 *     void setGlobalCTM(const SkM44& ctm);
 *
 *     // -- Device's clip bounds and stack manipulation
 *
 *     /**
 *      *  Return the bounds of the device in the coordinate space of the root canvas. The root device
 *      *  will have its top-left at 0,0, but other devices such as those associated with saveLayer may
 *      *  have a non-zero origin.
 *      */
 *     void getGlobalBounds(SkIRect* bounds) const {
 *         SkASSERT(bounds);
 *         *bounds = SkMatrixPriv::MapRect(fDeviceToGlobal, SkRect::Make(this->bounds())).roundOut();
 *     }
 *
 *     SkIRect getGlobalBounds() const {
 *         SkIRect bounds;
 *         this->getGlobalBounds(&bounds);
 *         return bounds;
 *     }
 *
 *     /**
 *      *  Returns the bounding box of the current clip, in this device's coordinate space. No pixels
 *      *  outside of these bounds will be touched by draws unless the clip is further modified (at
 *      *  which point this will return the updated bounds).
 *      */
 *     virtual SkIRect devClipBounds() const = 0;
 *
 *     virtual void pushClipStack() = 0;
 *     virtual void popClipStack() = 0;
 *
 *     virtual void clipRect(const SkRect& rect, SkClipOp op, bool aa) = 0;
 *     virtual void clipRRect(const SkRRect& rrect, SkClipOp op, bool aa) = 0;
 *     virtual void clipPath(const SkPath& path, SkClipOp op, bool aa) = 0;
 *     virtual void clipRegion(const SkRegion& region, SkClipOp op) = 0;
 *
 *     void clipShader(sk_sp<SkShader> sh, SkClipOp op) {
 *         sh = as_SB(sh)->makeWithCTM(this->localToDevice());
 *         if (op == SkClipOp::kDifference) {
 *             sh = as_SB(sh)->makeInvertAlpha();
 *         }
 *         this->onClipShader(std::move(sh));
 *     }
 *
 *     virtual void replaceClip(const SkIRect& rect) = 0;
 *
 *     virtual bool isClipAntiAliased() const = 0;
 *     virtual bool isClipEmpty() const = 0;
 *     virtual bool isClipRect() const = 0;
 *     virtual bool isClipWideOpen() const = 0;
 *
 *     virtual void android_utils_clipAsRgn(SkRegion*) const = 0;
 *     virtual bool android_utils_clipWithStencil() { return false; }
 *
 *     // -- Device reflection
 *
 *     // TEMPORARY: Whether or not SkCanvas should use an layer and image filters to simulate
 *     // mask filters and then draw the filtered mask using drawCoverageMask. Unlike regular
 *     // layers, the color type passed to SkDevice::createDevice() will always be an alpha-only
 *     // color type. Eventually this will be the only way that mask filters are handled (barring
 *     // dedicated fast-paths for blurs on [r]rects and text).
 *     virtual bool useDrawCoverageMaskForMaskFilters() const { return false; }
 *
 *     // SkCanvas uses NoPixelsDevice when createDevice fails; but then it needs to be able to
 *     // inspect a layer's device to know if calling drawDevice() later is allowed.
 *     virtual bool isNoPixelsDevice() const { return false; }
 *
 *     virtual void* getRasterHandle() const { return nullptr; }
 *
 *     virtual GrRecordingContext* recordingContext() const { return nullptr; }
 *     virtual skgpu::graphite::Recorder* recorder() const { return nullptr; }
 *     virtual SkRecorder* baseRecorder() const { return nullptr; }
 *
 *     virtual skgpu::ganesh::Device* asGaneshDevice() { return nullptr; }
 *     virtual skgpu::graphite::Device* asGraphiteDevice() { return nullptr; }
 *
 *     // Marking an SkDevice immutable declares the intent that rendering to the device is
 *     // complete, allowing it to be sampled as an image without requiring a copy. Drawing
 *     // operations may not function and may assert if invoked after setImmutable() is called.
 *     virtual void setImmutable() {}
 *
 *     virtual sk_sp<SkSurface> makeSurface(const SkImageInfo&, const SkSurfaceProps&);
 *
 *     struct CreateInfo {
 *         CreateInfo(const SkImageInfo& info,
 *                    SkPixelGeometry geo,
 *                    SkRasterHandleAllocator* allocator)
 *             : fInfo(info)
 *             , fPixelGeometry(geo)
 *             , fAllocator(allocator)
 *         {}
 *
 *         const SkImageInfo        fInfo;
 *         const SkPixelGeometry    fPixelGeometry;
 *         SkRasterHandleAllocator* fAllocator = nullptr;
 *     };
 *
 *     /**
 *      *  Create a new device based on CreateInfo. If the paint is not null, then it represents a
 *      *  preview of how the new device will be composed with its creator device (this).
 *      *
 *      *  The subclass may be handed this device in drawDevice(), so it must always return a device
 *      *  that it knows how to draw, and that it knows how to identify if it is not of the same
 *      *  subclass (since drawDevice is passed a SkDevice*). If the subclass cannot fulfill that
 *      *  contract (e.g. PDF cannot support some settings on the paint) it should return NULL, and the
 *      *  caller may then decide to explicitly create a bitmapdevice, knowing that later it could not
 *      *  call drawDevice with it (but it could call drawSprite or drawBitmap).
 *      */
 *     virtual sk_sp<SkDevice> createDevice(const CreateInfo&, const SkPaint*) { return nullptr; }
 *
 *     // -- Drawing routines (called after saveLayers and imagefilter operations are applied)
 *
 *     // Ensure that non-RSXForm runs are passed to onDrawGlyphRunList.
 *     void drawGlyphRunList(SkCanvas*,
 *                           const sktext::GlyphRunList& glyphRunList,
 *                           const SkPaint& paint);
 *     // Slug handling routines.
 *     virtual sk_sp<sktext::gpu::Slug> convertGlyphRunListToSlug(
 *             const sktext::GlyphRunList& glyphRunList, const SkPaint& paint);
 *     virtual void drawSlug(SkCanvas*, const sktext::gpu::Slug* slug, const SkPaint& paint);
 *
 *     virtual void drawPaint(const SkPaint& paint) = 0;
 *     virtual void drawPoints(SkCanvas::PointMode, SkSpan<const SkPoint>, const SkPaint&) = 0;
 *     virtual void drawRect(const SkRect& r,
 *                           const SkPaint& paint) = 0;
 *     virtual void drawRegion(const SkRegion& r,
 *                             const SkPaint& paint);
 *     virtual void drawOval(const SkRect& oval,
 *                           const SkPaint& paint) = 0;
 *     /** By the time this is called we know that abs(sweepAngle) is in the range [0, 360). */
 *     virtual void drawArc(const SkArc& arc, const SkPaint& paint);
 *     virtual void drawRRect(const SkRRect& rr,
 *                            const SkPaint& paint) = 0;
 *
 *     // Default impl calls drawPath()
 *     virtual void drawDRRect(const SkRRect& outer,
 *                             const SkRRect& inner, const SkPaint&);
 *
 *     /**
 *      *  If pathIsMutable, then the implementation is allowed to cast path to a
 *      *  non-const pointer and modify it in place (as an optimization). Canvas
 *      *  may do this to implement helpers such as drawOval, by placing a temp
 *      *  path on the stack to hold the representation of the oval.
 *      */
 *     virtual void drawPath(const SkPath& path,
 *                           const SkPaint& paint) = 0;
 *
 *     virtual void drawImageRect(const SkImage*, const SkRect* src, const SkRect& dst,
 *                                const SkSamplingOptions&, const SkPaint&,
 *                                SkCanvas::SrcRectConstraint) = 0;
 *     // Return true if canvas calls to drawImage or drawImageRect should try to
 *     // be drawn in a tiled way.
 *     virtual bool shouldDrawAsTiledImageRect() const { return false; }
 *     virtual bool drawAsTiledImageRect(SkCanvas*,
 *                                       const SkImage*,
 *                                       const SkRect* src,
 *                                       const SkRect& dst,
 *                                       const SkSamplingOptions&,
 *                                       const SkPaint&,
 *                                       SkCanvas::SrcRectConstraint) { return false; }
 *
 *     virtual void drawImageLattice(const SkImage*, const SkCanvas::Lattice&,
 *                                   const SkRect& dst, SkFilterMode, const SkPaint&);
 *
 *     /**
 *      * If skipColorXform is true, then the implementation should assume that the provided
 *      * vertex colors are already in the destination color space.
 *      */
 *     virtual void drawVertices(const SkVertices*,
 *                               sk_sp<SkBlender>,
 *                               const SkPaint&,
 *                               bool skipColorXform = false) = 0;
 *     virtual void drawMesh(const SkMesh& mesh, sk_sp<SkBlender>, const SkPaint&) = 0;
 *     virtual void drawShadow(SkCanvas*, const SkPath&, const SkDrawShadowRec&);
 *
 *     // default implementation calls drawVertices
 *     virtual void drawPatch(const SkPoint cubics[12], const SkColor colors[4],
 *                            const SkPoint texCoords[4], sk_sp<SkBlender>, const SkPaint& paint);
 *
 *     // default implementation calls drawVertices
 *     virtual void drawAtlas(SkSpan<const SkRSXform>, SkSpan<const SkRect>, SkSpan<const SkColor>,
 *                            sk_sp<SkBlender>, const SkPaint&);
 *
 *     virtual void drawAnnotation(const SkRect&, const char[], SkData*) {}
 *
 *     // Default impl always calls drawRect() with a solid-color paint, setting it to anti-aliased
 *     // only when all edge flags are set. If there's a clip region, it draws that using drawPath,
 *     // or uses clipPath().
 *     virtual void drawEdgeAAQuad(const SkRect& rect, const SkPoint clip[4],
 *                                 SkCanvas::QuadAAFlags aaFlags, const SkColor4f& color,
 *                                 SkBlendMode mode);
 *     // Default impl uses drawImageRect per entry, being anti-aliased only when an entry's edge flags
 *     // are all set. If there's a clip region, it will be applied using clipPath().
 *     virtual void drawEdgeAAImageSet(const SkCanvas::ImageSetEntry[], int count,
 *                                     const SkPoint dstClips[], const SkMatrix preViewMatrices[],
 *                                     const SkSamplingOptions&, const SkPaint&,
 *                                     SkCanvas::SrcRectConstraint);
 *
 *     virtual void drawDrawable(SkCanvas*, SkDrawable*, const SkMatrix*);
 *
 *     // -- "Special" drawing and image routines
 *
 *     // Snap the 'subset' contents from this device, possibly as a read-only view. If 'forceCopy'
 *     // is true then the returned image's pixels must not be affected by subsequent draws into the
 *     // device. When 'forceCopy' is false, the image can be a view into the device's pixels
 *     // (avoiding a copy for performance, at the expense of safety). Default returns null.
 *     virtual sk_sp<SkSpecialImage> snapSpecial(const SkIRect& subset, bool forceCopy = false);
 *     // Can return null if unable to perform scaling as part of the copy, even if snapSpecial() w/o
 *     // scaling would succeed.
 *     virtual sk_sp<SkSpecialImage> snapSpecialScaled(const SkIRect& subset, const SkISize& dstDims);
 *     // Get a view of the entire device's current contents as an image.
 *     sk_sp<SkSpecialImage> snapSpecial();
 *
 *     /**
 *      * The SkDevice passed will be an SkDevice which was returned by a call to
 *      * createDevice on this device with kNeverTile_TileExpectation.
 *      *
 *      * The default implementation calls snapSpecial() and drawSpecial() with the relative transform
 *      * from the input device to this device. The provided SkPaint cannot have a mask filter or
 *      * image filter, and any shader is ignored.
 *      */
 *     virtual void drawDevice(SkDevice*, const SkSamplingOptions&, const SkPaint&);
 *
 *     /**
 *      * Draw the special image's subset to this device, subject to the given matrix transform instead
 *      * of the device's current local to device matrix.
 *      *
 *      * If 'constraint' is kFast, the rendered geometry of the image still reflects the extent of
 *      * the SkSpecialImage's subset, but it's assumed that the pixel data beyond the subset is valid
 *      * (e.g. SkSpecialImage::makeSubset() was called to crop a larger image).
 *      */
 *     virtual void drawSpecial(SkSpecialImage*, const SkMatrix& localToDevice,
 *                              const SkSamplingOptions&, const SkPaint&,
 *                              SkCanvas::SrcRectConstraint constraint =
 *                                     SkCanvas::kStrict_SrcRectConstraint);
 *
 *     /**
 *      * Draw the special image's subset to this device, treating its alpha channel as coverage for
 *      * the draw and ignoring any RGB channels that might be present. This will be drawn using the
 *      * provided matrix transform instead of the device's current local to device matrix.
 *      *
 *      * Coverage values beyond the image's subset are treated as 0 (i.e. kDecal tiling). Color values
 *      * before coverage are determined as normal by the SkPaint, ignoring style, path effects,
 *      * mask filters and image filters. The local coords of any SkShader on the paint should be
 *      * relative to the SkDevice's current matrix (i.e. 'maskToDevice' determines how the coverage
 *      * mask aligns with device-space, but otherwise shading proceeds like other draws).
 *     */
 *     virtual void drawCoverageMask(const SkSpecialImage*, const SkMatrix& maskToDevice,
 *                                   const SkSamplingOptions&, const SkPaint&);
 *
 *     /**
 *      * Draw rrect with an optimized path for analytic blurs, if provided by the device.
 *      */
 *     virtual bool drawBlurredRRect(const SkRRect&, const SkPaint&, float deviceSigma) {
 *         return false;
 *     }
 *
 *     /**
 *      * Evaluate 'filter' and draw the final output into this device using 'paint'. The 'mapping'
 *      * defines the parameter-to-layer space transform used to evaluate the image filter on 'src',
 *      * and the layer-to-device space transform that is used to draw the result into this device.
 *      * Since 'mapping' fully specifies the transform, this draw function ignores the current
 *      * local-to-device matrix (i.e. just like drawSpecial and drawDevice).
 *      *
 *      * The final paint must not have an image filter or mask filter set on it; a shader is ignored.
 *      * The provided color type will be used for any intermediate surfaces that need to be created as
 *      * part of filter evaluation. It does not have to be src's color type or this Device's type.
 *      */
 *     void drawFilteredImage(const skif::Mapping& mapping, SkSpecialImage* src, SkColorType ct,
 *                            const SkImageFilter*, const SkSamplingOptions&, const SkPaint&);
 *
 * protected:
 *     // Configure the device's coordinate spaces, specifying both how its device image maps back to
 *     // the global space (via 'deviceToGlobal') and the initial CTM of the device (via
 *     // 'localToDevice', i.e. what geometry drawn into this device will be transformed with).
 *     //
 *     // (bufferOriginX, bufferOriginY) defines where the (0,0) pixel the device's backing buffer
 *     // is anchored in the device space. The final device-to-global matrix stored by the SkDevice
 *     // will include a pre-translation by T(deviceOriginX, deviceOriginY), and the final
 *     // local-to-device matrix will have a post-translation of T(-deviceOriginX, -deviceOriginY).
 *     void setDeviceCoordinateSystem(const SkM44& deviceToGlobal,
 *                                    const SkM44& globalToDevice,
 *                                    const SkM44& localToDevice,
 *                                    int bufferOriginX,
 *                                    int bufferOriginY);
 *     // Convenience to configure the device to be axis-aligned with the root canvas, but with a
 *     // unique origin.
 *     void setOrigin(const SkM44& globalCTM, int x, int y) {
 *         this->setDeviceCoordinateSystem(SkM44(), SkM44(), globalCTM, x, y);
 *     }
 *
 *     // Returns whether or not localToDevice() has changed since the last call to this function.
 *     bool checkLocalToDeviceDirty() {
 *         bool wasDirty = fLocalToDeviceDirty;
 *         fLocalToDeviceDirty = false;
 *         return wasDirty;
 *     }
 *
 * private:
 *     friend class SkCanvas; // for setOrigin/setDeviceCoordinateSystem
 *     friend class DeviceTestingAccess;
 *
 *     // Defaults to a CPU image filtering backend.
 *     virtual sk_sp<skif::Backend> createImageFilteringBackend(const SkSurfaceProps& surfaceProps,
 *                                                              SkColorType colorType) const;
 *
 *     // Implementations can assume that the device from (x,y) to (w,h) will fit within dst.
 *     virtual bool onReadPixels(const SkPixmap&, int x, int y) { return false; }
 *
 *     // Implementations can assume that the src image placed at 'x,y' will fit within the device.
 *     virtual bool onWritePixels(const SkPixmap&, int x, int y) { return false; }
 *
 *     virtual bool onAccessPixels(SkPixmap*) { return false; }
 *
 *     virtual bool onPeekPixels(SkPixmap*) { return false; }
 *
 *     virtual void onClipShader(sk_sp<SkShader>) = 0;
 *
 *     // Only called with glyphRunLists that do not contain RSXForm.
 *     virtual void onDrawGlyphRunList(SkCanvas*,
 *                                     const sktext::GlyphRunList&,
 *                                     const SkPaint& paint) = 0;
 *
 *     void simplifyGlyphRunRSXFormAndRedraw(SkCanvas*,
 *                                           const sktext::GlyphRunList&,
 *                                           const SkPaint& paint);
 *
 *     const SkImageInfo    fInfo;
 *     const SkSurfaceProps fSurfaceProps;
 *     SkM44 fLocalToDevice;
 *     // fDeviceToGlobal and fGlobalToDevice are inverses of each other; there are never that many
 *     // SkDevices, so pay the memory cost to avoid recalculating the inverse.
 *     SkM44 fDeviceToGlobal;
 *     SkM44 fGlobalToDevice;
 *
 *     // fLocalToDevice but as a 3x3.
 *     SkMatrix fLocalToDevice33;
 *
 *     // fLocalToDevice is the device CTM, not the global CTM.
 *     // It maps from local space to the device's coordinate space.
 *     // fDeviceToGlobal * fLocalToDevice will match the canvas' CTM.
 *     //
 *     // setGlobalCTM and setLocalToDevice are intentionally not virtual for performance reasons.
 *     // However, track a dirty bit for subclasses that want to defer local-to-device dependent
 *     // calculations until needed for a clip or draw.
 *     bool fLocalToDeviceDirty = true;
 * }
 * ```
 */
public abstract class SkDevice public constructor(
  info: SkImageInfo,
  surfaceProps: SkSurfaceProps,
) : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * const SkImageInfo    fInfo
   * ```
   */
  private val fInfo: SkImageInfo = TODO("Initialize fInfo")

  /**
   * C++ original:
   * ```cpp
   * const SkSurfaceProps fSurfaceProps
   * ```
   */
  private val fSurfaceProps: SkSurfaceProps = TODO("Initialize fSurfaceProps")

  /**
   * C++ original:
   * ```cpp
   * SkM44 fLocalToDevice
   * ```
   */
  private var fLocalToDevice: SkM44 = TODO("Initialize fLocalToDevice")

  /**
   * C++ original:
   * ```cpp
   * SkM44 fDeviceToGlobal
   * ```
   */
  private var fDeviceToGlobal: SkM44 = TODO("Initialize fDeviceToGlobal")

  /**
   * C++ original:
   * ```cpp
   * SkM44 fGlobalToDevice
   * ```
   */
  private var fGlobalToDevice: SkM44 = TODO("Initialize fGlobalToDevice")

  /**
   * C++ original:
   * ```cpp
   * SkMatrix fLocalToDevice33
   * ```
   */
  private var fLocalToDevice33: SkMatrix = TODO("Initialize fLocalToDevice33")

  /**
   * C++ original:
   * ```cpp
   * bool fLocalToDeviceDirty = true
   * ```
   */
  private var fLocalToDeviceDirty: Boolean = TODO("Initialize fLocalToDeviceDirty")

  /**
   * C++ original:
   * ```cpp
   * const SkImageInfo& imageInfo() const { return fInfo; }
   * ```
   */
  public fun imageInfo(): SkImageInfo {
    TODO("Implement imageInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * int width() const { return this->imageInfo().width(); }
   * ```
   */
  public fun width(): Int {
    TODO("Implement width")
  }

  /**
   * C++ original:
   * ```cpp
   * int height() const { return this->imageInfo().height(); }
   * ```
   */
  public fun height(): Int {
    TODO("Implement height")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isOpaque() const { return this->imageInfo().isOpaque(); }
   * ```
   */
  public fun isOpaque(): Boolean {
    TODO("Implement isOpaque")
  }

  /**
   * C++ original:
   * ```cpp
   * SkIRect bounds() const { return SkIRect::MakeWH(this->width(), this->height()); }
   * ```
   */
  public fun bounds(): SkIRect {
    TODO("Implement bounds")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize size() const { return this->imageInfo().dimensions(); }
   * ```
   */
  public fun size(): SkISize {
    TODO("Implement size")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkSurfaceProps& surfaceProps() const {
   *         return fSurfaceProps;
   *     }
   * ```
   */
  public fun surfaceProps(): SkSurfaceProps {
    TODO("Implement surfaceProps")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalerContextFlags SkDevice::scalerContextFlags() const {
   *     // If we're doing linear blending, then we can disable the gamma hacks.
   *     // Otherwise, leave them on. In either case, we still want the contrast boost:
   *     // TODO: Can we be even smarter about mask gamma based on the dest transfer function?
   *     const SkColorSpace* const cs = fInfo.colorSpace();
   *     if (cs && cs->gammaIsLinear()) {
   *         return SkScalerContextFlags::kBoostContrast;
   *     } else {
   *         return SkScalerContextFlags::kFakeGammaAndBoostContrast;
   *     }
   * }
   * ```
   */
  public fun scalerContextFlags(): SkScalerContextFlags {
    TODO("Implement scalerContextFlags")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual SkStrikeDeviceInfo strikeDeviceInfo() const {
   *         return {fSurfaceProps, this->scalerContextFlags(), nullptr};
   *     }
   * ```
   */
  public open fun strikeDeviceInfo(): SkStrikeDeviceInfo {
    TODO("Implement strikeDeviceInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * bool writePixels(const SkPixmap& src, int x, int y) { return this->onWritePixels(src, x, y); }
   * ```
   */
  public fun writePixels(
    src: SkPixmap,
    x: Int,
    y: Int,
  ): Boolean {
    TODO("Implement writePixels")
  }

  /**
   * C++ original:
   * ```cpp
   * bool readPixels(const SkPixmap& dst, int x, int y) { return this->onReadPixels(dst, x, y); }
   * ```
   */
  public fun readPixels(
    dst: SkPixmap,
    x: Int,
    y: Int,
  ): Boolean {
    TODO("Implement readPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkDevice::accessPixels(SkPixmap* pmap) {
   *     SkPixmap tempStorage;
   *     if (nullptr == pmap) {
   *         pmap = &tempStorage;
   *     }
   *     return this->onAccessPixels(pmap);
   * }
   * ```
   */
  public fun accessPixels(pmap: SkPixmap?): Boolean {
    TODO("Implement accessPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkDevice::peekPixels(SkPixmap* pmap) {
   *     SkPixmap tempStorage;
   *     if (nullptr == pmap) {
   *         pmap = &tempStorage;
   *     }
   *     return this->onPeekPixels(pmap);
   * }
   * ```
   */
  public fun peekPixels(pmap: SkPixmap?): Boolean {
    TODO("Implement peekPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkM44& localToDevice44() const { return fLocalToDevice; }
   * ```
   */
  public fun localToDevice44(): SkM44 {
    TODO("Implement localToDevice44")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkMatrix& localToDevice() const { return fLocalToDevice33; }
   * ```
   */
  public fun localToDevice(): SkMatrix {
    TODO("Implement localToDevice")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkM44& deviceToGlobal() const { return fDeviceToGlobal; }
   * ```
   */
  public fun deviceToGlobal(): SkM44 {
    TODO("Implement deviceToGlobal")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkM44& globalToDevice() const { return fGlobalToDevice; }
   * ```
   */
  public fun globalToDevice(): SkM44 {
    TODO("Implement globalToDevice")
  }

  /**
   * C++ original:
   * ```cpp
   * SkIPoint SkDevice::getOrigin() const {
   *     // getOrigin() is deprecated, the old origin has been moved into the fDeviceToGlobal matrix.
   *     // This extracts the origin from the matrix, but asserts that a more complicated coordinate
   *     // space hasn't been set of the device. This function can be removed once existing use cases
   *     // have been updated to use the device-to-global matrix instead or have themselves been removed
   *     // (e.g. Android's device-space clip regions are going away, and are not compatible with the
   *     // generalized device coordinate system).
   *     SkASSERT(this->isPixelAlignedToGlobal());
   *     return SkIPoint::Make(SkScalarFloorToInt(fDeviceToGlobal.rc(0, 3)),
   *                           SkScalarFloorToInt(fDeviceToGlobal.rc(1, 3)));
   * }
   * ```
   */
  public fun getOrigin(): SkIPoint {
    TODO("Implement getOrigin")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkDevice::isPixelAlignedToGlobal() const {
   *     // pixelAligned is set to the identity + integer translation of the device-to-global matrix.
   *     // If they are equal then the device is by definition pixel aligned.
   *     SkM44 pixelAligned = SkM44();
   *     pixelAligned.setRC(0, 3, SkScalarFloorToScalar(fDeviceToGlobal.rc(0, 3)));
   *     pixelAligned.setRC(1, 3, SkScalarFloorToScalar(fDeviceToGlobal.rc(1, 3)));
   *     return pixelAligned == fDeviceToGlobal;
   * }
   * ```
   */
  public fun isPixelAlignedToGlobal(): Boolean {
    TODO("Implement isPixelAlignedToGlobal")
  }

  /**
   * C++ original:
   * ```cpp
   * SkM44 SkDevice::getRelativeTransform(const SkDevice& dstDevice) const {
   *     // To get the transform from this space to the other device's, transform from our space to
   *     // global and then from global to the other device.
   *     return dstDevice.fGlobalToDevice * fDeviceToGlobal;
   * }
   * ```
   */
  public fun getRelativeTransform(dstDevice: SkDevice): SkM44 {
    TODO("Implement getRelativeTransform")
  }

  /**
   * C++ original:
   * ```cpp
   * void setLocalToDevice(const SkM44& localToDevice) {
   *         fLocalToDevice = localToDevice;
   *         fLocalToDevice33 = fLocalToDevice.asM33();
   *         fLocalToDeviceDirty = true;
   *     }
   * ```
   */
  public fun setLocalToDevice(localToDevice: SkM44) {
    TODO("Implement setLocalToDevice")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDevice::setGlobalCTM(const SkM44& ctm) {
   *     fLocalToDevice = ctm;
   *     fLocalToDevice.normalizePerspective();
   *     // Map from the global CTM state to this device's coordinate system.
   *     fLocalToDevice.postConcat(fGlobalToDevice);
   *     fLocalToDevice33 = fLocalToDevice.asM33();
   *     fLocalToDeviceDirty = true;
   * }
   * ```
   */
  public fun setGlobalCTM(ctm: SkM44) {
    TODO("Implement setGlobalCTM")
  }

  /**
   * C++ original:
   * ```cpp
   * void getGlobalBounds(SkIRect* bounds) const {
   *         SkASSERT(bounds);
   *         *bounds = SkMatrixPriv::MapRect(fDeviceToGlobal, SkRect::Make(this->bounds())).roundOut();
   *     }
   * ```
   */
  public fun getGlobalBounds(bounds: SkIRect?) {
    TODO("Implement getGlobalBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * SkIRect getGlobalBounds() const {
   *         SkIRect bounds;
   *         this->getGlobalBounds(&bounds);
   *         return bounds;
   *     }
   * ```
   */
  public fun getGlobalBounds(): SkIRect {
    TODO("Implement getGlobalBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual SkIRect devClipBounds() const = 0
   * ```
   */
  public abstract fun devClipBounds(): SkIRect

  /**
   * C++ original:
   * ```cpp
   * virtual void pushClipStack() = 0
   * ```
   */
  public abstract fun pushClipStack()

  /**
   * C++ original:
   * ```cpp
   * virtual void popClipStack() = 0
   * ```
   */
  public abstract fun popClipStack()

  /**
   * C++ original:
   * ```cpp
   * virtual void clipRect(const SkRect& rect, SkClipOp op, bool aa) = 0
   * ```
   */
  public abstract fun clipRect(
    rect: SkRect,
    op: SkClipOp,
    aa: Boolean,
  )

  /**
   * C++ original:
   * ```cpp
   * virtual void clipRRect(const SkRRect& rrect, SkClipOp op, bool aa) = 0
   * ```
   */
  public abstract fun clipRRect(
    rrect: SkRRect,
    op: SkClipOp,
    aa: Boolean,
  )

  /**
   * C++ original:
   * ```cpp
   * virtual void clipPath(const SkPath& path, SkClipOp op, bool aa) = 0
   * ```
   */
  public abstract fun clipPath(
    path: SkPath,
    op: SkClipOp,
    aa: Boolean,
  )

  /**
   * C++ original:
   * ```cpp
   * virtual void clipRegion(const SkRegion& region, SkClipOp op) = 0
   * ```
   */
  public abstract fun clipRegion(region: SkRegion, op: SkClipOp)

  /**
   * C++ original:
   * ```cpp
   * void clipShader(sk_sp<SkShader> sh, SkClipOp op) {
   *         sh = as_SB(sh)->makeWithCTM(this->localToDevice());
   *         if (op == SkClipOp::kDifference) {
   *             sh = as_SB(sh)->makeInvertAlpha();
   *         }
   *         this->onClipShader(std::move(sh));
   *     }
   * ```
   */
  public fun clipShader(sh: SkSp<SkShader>, op: SkClipOp) {
    TODO("Implement clipShader")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void replaceClip(const SkIRect& rect) = 0
   * ```
   */
  public abstract fun replaceClip(rect: SkIRect)

  /**
   * C++ original:
   * ```cpp
   * virtual bool isClipAntiAliased() const = 0
   * ```
   */
  public abstract fun isClipAntiAliased(): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual bool isClipEmpty() const = 0
   * ```
   */
  public abstract fun isClipEmpty(): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual bool isClipRect() const = 0
   * ```
   */
  public abstract fun isClipRect(): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual bool isClipWideOpen() const = 0
   * ```
   */
  public abstract fun isClipWideOpen(): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual void android_utils_clipAsRgn(SkRegion*) const = 0
   * ```
   */
  public abstract fun androidUtilsClipAsRgn(param0: SkRegion?)

  /**
   * C++ original:
   * ```cpp
   * virtual bool android_utils_clipWithStencil() { return false; }
   * ```
   */
  public open fun androidUtilsClipWithStencil(): Boolean {
    TODO("Implement androidUtilsClipWithStencil")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool useDrawCoverageMaskForMaskFilters() const { return false; }
   * ```
   */
  public open fun useDrawCoverageMaskForMaskFilters(): Boolean {
    TODO("Implement useDrawCoverageMaskForMaskFilters")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool isNoPixelsDevice() const { return false; }
   * ```
   */
  public open fun isNoPixelsDevice(): Boolean {
    TODO("Implement isNoPixelsDevice")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void* getRasterHandle() const { return nullptr; }
   * ```
   */
  public open fun getRasterHandle() {
    TODO("Implement getRasterHandle")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual GrRecordingContext* recordingContext() const { return nullptr; }
   * ```
   */
  public open fun recordingContext(): GrRecordingContext {
    TODO("Implement recordingContext")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual skgpu::graphite::Recorder* recorder() const { return nullptr; }
   * ```
   */
  public open fun recorder(): Recorder {
    TODO("Implement recorder")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual SkRecorder* baseRecorder() const { return nullptr; }
   * ```
   */
  public open fun baseRecorder(): SkRecorder {
    TODO("Implement baseRecorder")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual skgpu::ganesh::Device* asGaneshDevice() { return nullptr; }
   * ```
   */
  public open fun asGaneshDevice(): GaneshDevice {
    TODO("Implement asGaneshDevice")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual skgpu::graphite::Device* asGraphiteDevice() { return nullptr; }
   * ```
   */
  public open fun asGraphiteDevice(): GpuDevice {
    TODO("Implement asGraphiteDevice")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void setImmutable() {}
   * ```
   */
  public open fun setImmutable() {
    TODO("Implement setImmutable")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkSurface> SkDevice::makeSurface(SkImageInfo const&, SkSurfaceProps const&) {
   *     return nullptr;
   * }
   * ```
   */
  public open fun makeSurface(param0: SkImageInfo, param1: SkSurfaceProps): SkSp<SkSurface> {
    TODO("Implement makeSurface")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<SkDevice> createDevice(const CreateInfo&, const SkPaint*) { return nullptr; }
   * ```
   */
  public open fun createDevice(param0: CreateInfo, param1: SkPaint?): SkSp<SkDevice> {
    TODO("Implement createDevice")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDevice::drawGlyphRunList(SkCanvas* canvas,
   *                                 const sktext::GlyphRunList& glyphRunList,
   *                                 const SkPaint& paint) {
   *     if (!this->localToDevice().isFinite()) {
   *         return;
   *     }
   *
   *     if (!glyphRunList.hasRSXForm()) {
   *         this->onDrawGlyphRunList(canvas, glyphRunList, paint);
   *     } else {
   *         this->simplifyGlyphRunRSXFormAndRedraw(canvas, glyphRunList, paint);
   *     }
   * }
   * ```
   */
  public fun drawGlyphRunList(
    canvas: SkCanvas?,
    glyphRunList: GlyphRunList,
    paint: SkPaint,
  ) {
    TODO("Implement drawGlyphRunList")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sktext::gpu::Slug> SkDevice::convertGlyphRunListToSlug(
   *         const sktext::GlyphRunList& glyphRunList, const SkPaint& paint) {
   *     return nullptr;
   * }
   * ```
   */
  public open fun convertGlyphRunListToSlug(glyphRunList: GlyphRunList, paint: SkPaint): SkSp<Slug> {
    TODO("Implement convertGlyphRunListToSlug")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDevice::drawSlug(SkCanvas*, const sktext::gpu::Slug*, const SkPaint&) {
   *     SK_ABORT("Slug drawing not supported.");
   * }
   * ```
   */
  public open fun drawSlug(
    param0: SkCanvas?,
    slug: Slug?,
    paint: SkPaint,
  ) {
    TODO("Implement drawSlug")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void drawPaint(const SkPaint& paint) = 0
   * ```
   */
  public abstract fun drawPaint(paint: SkPaint)

  /**
   * C++ original:
   * ```cpp
   * virtual void drawPoints(SkCanvas::PointMode, SkSpan<const SkPoint>, const SkPaint&) = 0
   * ```
   */
  public abstract fun drawPoints(
    param0: SkCanvas.PointMode,
    param1: SkSpan<SkPoint>,
    param2: SkPaint,
  )

  /**
   * C++ original:
   * ```cpp
   * virtual void drawRect(const SkRect& r,
   *                           const SkPaint& paint) = 0
   * ```
   */
  public abstract fun drawRect(r: SkRect, paint: SkPaint)

  /**
   * C++ original:
   * ```cpp
   * void SkDevice::drawRegion(const SkRegion& region, const SkPaint& paint) {
   *     const SkMatrix& localToDevice = this->localToDevice();
   *     bool isNonTranslate = localToDevice.getType() & ~(SkMatrix::kTranslate_Mask);
   *     bool complexPaint = paint.getStyle() != SkPaint::kFill_Style || paint.getMaskFilter() ||
   *                         paint.getPathEffect();
   *     bool antiAlias = paint.isAntiAlias() && (!is_int(localToDevice.getTranslateX()) ||
   *                                              !is_int(localToDevice.getTranslateY()));
   *     if (isNonTranslate || complexPaint || antiAlias) {
   *         SkPathBuilder builder;
   *         region.addBoundaryPath(&builder);
   *         builder.setIsVolatile(true);
   *         return this->drawPath(builder.detach(), paint);
   *     }
   *
   *     SkRegion::Iterator it(region);
   *     while (!it.done()) {
   *         this->drawRect(SkRect::Make(it.rect()), paint);
   *         it.next();
   *     }
   * }
   * ```
   */
  public open fun drawRegion(r: SkRegion, paint: SkPaint) {
    TODO("Implement drawRegion")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void drawOval(const SkRect& oval,
   *                           const SkPaint& paint) = 0
   * ```
   */
  public abstract fun drawOval(oval: SkRect, paint: SkPaint)

  /**
   * C++ original:
   * ```cpp
   * void SkDevice::drawArc(const SkArc& arc, const SkPaint& paint) {
   *     bool isFillNoPathEffect = SkPaint::kFill_Style == paint.getStyle() && !paint.getPathEffect();
   *     SkPath path = SkPathPriv::CreateDrawArcPath(arc, isFillNoPathEffect);
   *     this->drawPath(path, paint);
   * }
   * ```
   */
  public open fun drawArc(arc: SkArc, paint: SkPaint) {
    TODO("Implement drawArc")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void drawRRect(const SkRRect& rr,
   *                            const SkPaint& paint) = 0
   * ```
   */
  public abstract fun drawRRect(rr: SkRRect, paint: SkPaint)

  /**
   * C++ original:
   * ```cpp
   * void SkDevice::drawDRRect(const SkRRect& outer,
   *                           const SkRRect& inner, const SkPaint& paint) {
   *     SkPathBuilder builder;
   *     builder.addRRect(outer);
   *     builder.addRRect(inner);
   *     builder.setFillType(SkPathFillType::kEvenOdd);
   *     builder.setIsVolatile(true);
   *
   *     this->drawPath(builder.detach(), paint);
   * }
   * ```
   */
  public open fun drawDRRect(
    outer: SkRRect,
    `inner`: SkRRect,
    paint: SkPaint,
  ) {
    TODO("Implement drawDRRect")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void drawPath(const SkPath& path,
   *                           const SkPaint& paint) = 0
   * ```
   */
  public abstract fun drawPath(path: SkPath, paint: SkPaint)

  /**
   * C++ original:
   * ```cpp
   * virtual void drawImageRect(const SkImage*, const SkRect* src, const SkRect& dst,
   *                                const SkSamplingOptions&, const SkPaint&,
   *                                SkCanvas::SrcRectConstraint) = 0
   * ```
   */
  public abstract fun drawImageRect(
    param0: SkImage?,
    src: SkRect?,
    dst: SkRect,
    param3: SkSamplingOptions,
    param4: SkPaint,
    param5: SkCanvas.SrcRectConstraint,
  )

  /**
   * C++ original:
   * ```cpp
   * virtual bool shouldDrawAsTiledImageRect() const { return false; }
   * ```
   */
  public open fun shouldDrawAsTiledImageRect(): Boolean {
    TODO("Implement shouldDrawAsTiledImageRect")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool drawAsTiledImageRect(SkCanvas*,
   *                                       const SkImage*,
   *                                       const SkRect* src,
   *                                       const SkRect& dst,
   *                                       const SkSamplingOptions&,
   *                                       const SkPaint&,
   *                                       SkCanvas::SrcRectConstraint) { return false; }
   * ```
   */
  public open fun drawAsTiledImageRect(
    param0: SkCanvas?,
    param1: SkImage?,
    src: SkRect?,
    dst: SkRect,
    param4: SkSamplingOptions,
    param5: SkPaint,
    param6: SkCanvas.SrcRectConstraint,
  ): Boolean {
    TODO("Implement drawAsTiledImageRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDevice::drawImageLattice(const SkImage* image, const SkCanvas::Lattice& lattice,
   *                                 const SkRect& dst, SkFilterMode filter, const SkPaint& paint) {
   *     SkLatticeIter iter(lattice, dst);
   *
   *     SkRect srcR, dstR;
   *     SkColor c;
   *     bool isFixedColor = false;
   *     const SkImageInfo info = SkImageInfo::Make(1, 1, kBGRA_8888_SkColorType, kUnpremul_SkAlphaType);
   *
   *     while (iter.next(&srcR, &dstR, &isFixedColor, &c)) {
   *         // TODO: support this fast-path for GPU images
   *         if (isFixedColor || (srcR.width() <= 1.0f && srcR.height() <= 1.0f &&
   *                              image->readPixels(nullptr, info, &c, 4, srcR.fLeft, srcR.fTop))) {
   *               // Fast draw with drawRect, if this is a patch containing a single color
   *               // or if this is a patch containing a single pixel.
   *               if (0 != c || !paint.isSrcOver()) {
   *                    SkPaint paintCopy(paint);
   *                    int alpha = SkAlphaMul(SkColorGetA(c), SkAlpha255To256(paint.getAlpha()));
   *                    paintCopy.setColor(SkColorSetA(c, alpha));
   *                    this->drawRect(dstR, paintCopy);
   *               }
   *         } else {
   *             this->drawImageRect(image, &srcR, dstR, SkSamplingOptions(filter), paint,
   *                                 SkCanvas::kStrict_SrcRectConstraint);
   *         }
   *     }
   * }
   * ```
   */
  public open fun drawImageLattice(
    image: SkImage?,
    lattice: SkCanvas.Lattice,
    dst: SkRect,
    filter: SkFilterMode,
    paint: SkPaint,
  ) {
    TODO("Implement drawImageLattice")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void drawVertices(const SkVertices*,
   *                               sk_sp<SkBlender>,
   *                               const SkPaint&,
   *                               bool skipColorXform = false) = 0
   * ```
   */
  public abstract fun drawVertices(
    param0: SkVertices?,
    param1: SkSp<SkBlender>,
    param2: SkPaint,
    skipColorXform: Boolean = TODO(),
  )

  /**
   * C++ original:
   * ```cpp
   * virtual void drawMesh(const SkMesh& mesh, sk_sp<SkBlender>, const SkPaint&) = 0
   * ```
   */
  public abstract fun drawMesh(
    mesh: SkMesh,
    param1: SkSp<SkBlender>,
    param2: SkPaint,
  )

  /**
   * C++ original:
   * ```cpp
   * void SkDevice::drawShadow(SkCanvas* canvas, const SkPath& path, const SkDrawShadowRec& rec) {
   *     if (!validate_rec(rec)) {
   *         return;
   *     }
   *
   *     SkMatrix viewMatrix = this->localToDevice();
   *
   * #if !defined(SK_ENABLE_OPTIMIZE_SIZE)
   *     auto drawVertsProc = [this](const SkVertices* vertices, SkBlendMode mode, const SkPaint& paint,
   *                                 SkScalar tx, SkScalar ty, bool hasPerspective) {
   *         if (vertices->priv().vertexCount()) {
   *             // For perspective shadows we've already computed the shadow in world space,
   *             // and we can't translate it without changing it. Otherwise we concat the
   *             // change in translation from the cached version.
   *             SkAutoDeviceTransformRestore adr(
   *                     this,
   *                     hasPerspective ? SkM44()
   *                                    : this->localToDevice44() * SkM44::Translate(tx, ty));
   *             // The vertex colors for a tesselated shadow polygon are always either opaque black
   *             // or transparent and their real contribution to the final blended color is via
   *             // their alpha. We can skip expensive per-vertex color conversion for this.
   *             this->drawVertices(vertices, SkBlender::Mode(mode), paint, /*skipColorXform=*/true);
   *         }
   *     };
   *
   *     ShadowedPath shadowedPath(&path, &viewMatrix);
   *
   *     bool tiltZPlane = tilted(rec.fZPlaneParams);
   *     bool transparent = SkToBool(rec.fFlags & SkShadowFlags::kTransparentOccluder_ShadowFlag);
   *     bool useBlur = SkToBool(rec.fFlags & SkShadowFlags::kConcaveBlurOnly_ShadowFlag) &&
   *                    !path.isConvex();
   *     bool uncached = tiltZPlane || path.isVolatile();
   * #endif
   *     bool directional = SkToBool(rec.fFlags & SkShadowFlags::kDirectionalLight_ShadowFlag);
   *
   *     SkPoint3 zPlaneParams = rec.fZPlaneParams;
   *     SkPoint3 devLightPos = rec.fLightPos;
   *     if (!directional) {
   *         viewMatrix.mapPoints({(SkPoint*)&devLightPos.fX, 1});
   *     }
   *     float lightRadius = rec.fLightRadius;
   *
   *     if (SkColorGetA(rec.fAmbientColor) > 0) {
   *         SkAutoDeviceTransformRestore adr(this, SkM44());
   *
   *         bool success = false;
   * #if !defined(SK_ENABLE_OPTIMIZE_SIZE)
   *         if (uncached && !useBlur) {
   *             sk_sp<SkVertices> vertices = SkShadowTessellator::MakeAmbient(path, viewMatrix,
   *                                                                           zPlaneParams,
   *                                                                           transparent);
   *             if (vertices) {
   *                 SkPaint paint;
   *                 // Run the vertex color through a GaussianColorFilter and then modulate the
   *                 // grayscale result of that against our 'color' param.
   *                 paint.setColorFilter(
   *                     SkColorFilters::Blend(rec.fAmbientColor,
   *                                                   SkBlendMode::kModulate)->makeComposed(
   *                                                                SkColorFilterPriv::MakeGaussian()));
   *                 // The vertex colors for a tesselated shadow polygon are always either opaque black
   *                 // or transparent and their real contribution to the final blended color is via
   *                 // their alpha. We can skip expensive per-vertex color conversion for this.
   *                 this->drawVertices(vertices.get(),
   *                                    SkBlender::Mode(SkBlendMode::kDst),
   *                                    paint,
   *                                    /*skipColorXform=*/true);
   *                 success = true;
   *             }
   *         }
   *
   *         if (!success && !useBlur) {
   *             AmbientVerticesFactory factory;
   *             factory.fOccluderHeight = zPlaneParams.fZ;
   *             factory.fTransparent = transparent;
   *             if (viewMatrix.hasPerspective()) {
   *                 factory.fOffset.set(0, 0);
   *             } else {
   *                 factory.fOffset.fX = viewMatrix.getTranslateX();
   *                 factory.fOffset.fY = viewMatrix.getTranslateY();
   *             }
   *
   *             success = draw_shadow(factory, drawVertsProc, shadowedPath, rec.fAmbientColor);
   *         }
   * #endif // !defined(SK_ENABLE_OPTIMIZE_SIZE)
   *
   *         // All else has failed, draw with blur
   *         if (!success) {
   *             // Pretransform the path to avoid transforming the stroke, below.
   *             SkPath devSpacePath = path.makeTransform(canvas->getLocalToDeviceAs3x3());
   *             devSpacePath.setIsVolatile(true);
   *
   *             // The tesselator outsets by AmbientBlurRadius (or 'r') to get the outer ring of
   *             // the tesselation, and sets the alpha on the path to 1/AmbientRecipAlpha (or 'a').
   *             //
   *             // We want to emulate this with a blur. The full blur width (2*blurRadius or 'f')
   *             // can be calculated by interpolating:
   *             //
   *             //            original edge        outer edge
   *             //         |       |<---------- r ------>|
   *             //         |<------|--- f -------------->|
   *             //         |       |                     |
   *             //    alpha = 1  alpha = a          alpha = 0
   *             //
   *             // Taking ratios, f/1 = r/a, so f = r/a and blurRadius = f/2.
   *             //
   *             // We now need to outset the path to place the new edge in the center of the
   *             // blur region:
   *             //
   *             //             original   new
   *             //         |       |<------|--- r ------>|
   *             //         |<------|--- f -|------------>|
   *             //         |       |<- o ->|<--- f/2 --->|
   *             //
   *             //     r = o + f/2, so o = r - f/2
   *             //
   *             // We outset by using the stroker, so the strokeWidth is o/2.
   *             //
   *             SkScalar devSpaceOutset = SkDrawShadowMetrics::AmbientBlurRadius(zPlaneParams.fZ);
   *             SkScalar oneOverA = SkDrawShadowMetrics::AmbientRecipAlpha(zPlaneParams.fZ);
   *             SkScalar blurRadius = 0.5f*devSpaceOutset*oneOverA;
   *             SkScalar strokeWidth = 0.5f*(devSpaceOutset - blurRadius);
   *
   *             // Now draw with blur
   *             SkAutoCanvasRestore autoRestore(canvas, /*doSave=*/true);
   *             canvas->setMatrix(SkM44());
   *             SkPaint paint;
   *             paint.setColor(rec.fAmbientColor);
   *             paint.setStrokeWidth(strokeWidth);
   *             paint.setStyle(SkPaint::kStrokeAndFill_Style);
   *             SkScalar sigma = SkBlurMask::ConvertRadiusToSigma(blurRadius);
   *             paint.setMaskFilter(SkMaskFilter::MakeBlur(kNormal_SkBlurStyle, sigma,
   *                                                        /*respectCTM=*/false));
   *             canvas->drawPath(devSpacePath, paint);
   *         }
   *     }
   *
   *     if (SkColorGetA(rec.fSpotColor) > 0) {
   *         SkAutoDeviceTransformRestore adr(this, SkM44());
   *
   *         bool success = false;
   * #if !defined(SK_ENABLE_OPTIMIZE_SIZE)
   *         if (uncached && !useBlur) {
   *             sk_sp<SkVertices> vertices = SkShadowTessellator::MakeSpot(path, viewMatrix,
   *                                                                        zPlaneParams,
   *                                                                        devLightPos, lightRadius,
   *                                                                        transparent,
   *                                                                        directional);
   *             if (vertices) {
   *                 SkPaint paint;
   *                 // Run the vertex color through a GaussianColorFilter and then modulate the
   *                 // grayscale result of that against our 'color' param.
   *                 paint.setColorFilter(
   *                     SkColorFilters::Blend(rec.fSpotColor,
   *                                                   SkBlendMode::kModulate)->makeComposed(
   *                                                       SkColorFilterPriv::MakeGaussian()));
   *                 // The vertex colors for a tesselated shadow polygon are always either opaque black
   *                 // or transparent and their real contribution to the final blended color is via
   *                 // their alpha. We can skip expensive per-vertex color conversion for this.
   *                 this->drawVertices(vertices.get(),
   *                                    SkBlender::Mode(SkBlendMode::kDst),
   *                                    paint,
   *                                    /*skipColorXform=*/true);
   *                 success = true;
   *             }
   *         }
   *
   *         if (!success && !useBlur) {
   *             SpotVerticesFactory factory;
   *             factory.fOccluderHeight = zPlaneParams.fZ;
   *             factory.fDevLightPos = devLightPos;
   *             factory.fLightRadius = lightRadius;
   *
   *             SkPoint center = SkPoint::Make(path.getBounds().centerX(), path.getBounds().centerY());
   *             factory.fLocalCenter = center;
   *             center = viewMatrix.mapPoint(center);
   *             SkScalar radius, scale;
   *             if (SkToBool(rec.fFlags & kDirectionalLight_ShadowFlag)) {
   *                 SkDrawShadowMetrics::GetDirectionalParams(zPlaneParams.fZ, devLightPos.fX,
   *                                                           devLightPos.fY, devLightPos.fZ,
   *                                                           lightRadius, &radius, &scale,
   *                                                           &factory.fOffset);
   *             } else {
   *                 SkDrawShadowMetrics::GetSpotParams(zPlaneParams.fZ, devLightPos.fX - center.fX,
   *                                                    devLightPos.fY - center.fY, devLightPos.fZ,
   *                                                    lightRadius, &radius, &scale, &factory.fOffset);
   *             }
   *
   *             SkRect devBounds;
   *             viewMatrix.mapRect(&devBounds, path.getBounds());
   *             if (transparent ||
   *                 SkTAbs(factory.fOffset.fX) > 0.5f*devBounds.width() ||
   *                 SkTAbs(factory.fOffset.fY) > 0.5f*devBounds.height()) {
   *                 // if the translation of the shadow is big enough we're going to end up
   *                 // filling the entire umbra, we can treat these as all the same
   *                 if (directional) {
   *                     factory.fOccluderType =
   *                             SpotVerticesFactory::OccluderType::kDirectionalTransparent;
   *                 } else {
   *                     factory.fOccluderType = SpotVerticesFactory::OccluderType::kPointTransparent;
   *                 }
   *             } else if (directional) {
   *                 factory.fOccluderType = SpotVerticesFactory::OccluderType::kDirectional;
   *             } else if (factory.fOffset.length()*scale + scale < radius) {
   *                 // if we don't translate more than the blur distance, can assume umbra is covered
   *                 factory.fOccluderType = SpotVerticesFactory::OccluderType::kPointOpaqueNoUmbra;
   *             } else if (path.isConvex()) {
   *                 factory.fOccluderType = SpotVerticesFactory::OccluderType::kPointOpaquePartialUmbra;
   *             } else {
   *                 factory.fOccluderType = SpotVerticesFactory::OccluderType::kPointTransparent;
   *             }
   *             // need to add this after we classify the shadow
   *             factory.fOffset.fX += viewMatrix.getTranslateX();
   *             factory.fOffset.fY += viewMatrix.getTranslateY();
   *
   *             SkColor color = rec.fSpotColor;
   * #ifdef DEBUG_SHADOW_CHECKS
   *             switch (factory.fOccluderType) {
   *                 case SpotVerticesFactory::OccluderType::kPointTransparent:
   *                     color = 0xFFD2B48C;  // tan for transparent
   *                     break;
   *                 case SpotVerticesFactory::OccluderType::kPointOpaquePartialUmbra:
   *                     color = 0xFFFFA500;   // orange for opaque
   *                     break;
   *                 case SpotVerticesFactory::OccluderType::kPointOpaqueNoUmbra:
   *                     color = 0xFFE5E500;  // corn yellow for covered
   *                     break;
   *                 case SpotVerticesFactory::OccluderType::kDirectional:
   *                 case SpotVerticesFactory::OccluderType::kDirectionalTransparent:
   *                     color = 0xFF550000;  // dark red for directional
   *                     break;
   *             }
   * #endif
   *             success = draw_shadow(factory, drawVertsProc, shadowedPath, color);
   *         }
   * #endif // !defined(SK_ENABLE_OPTIMIZE_SIZE)
   *
   *         // All else has failed, draw with blur
   *         if (!success) {
   *             SkMatrix shadowMatrix;
   *             SkScalar radius;
   *             if (!SkDrawShadowMetrics::GetSpotShadowTransform(devLightPos, lightRadius,
   *                                                              canvas->getLocalToDeviceAs3x3(),
   *                                                              zPlaneParams,
   *                                                              path.getBounds(), directional,
   *                                                              &shadowMatrix, &radius)) {
   *                 return;
   *             }
   *             SkAutoCanvasRestore autoRestore(canvas, /*doSave=*/true);
   *
   *             // And draw with blur
   *             canvas->setMatrix(shadowMatrix);
   *             SkPaint paint;
   *             paint.setColor(rec.fSpotColor);
   *             SkScalar sigma = SkBlurMask::ConvertRadiusToSigma(radius);
   *             paint.setMaskFilter(SkMaskFilter::MakeBlur(kNormal_SkBlurStyle, sigma,
   *                                                        /*respectCTM=*/false));
   *             canvas->drawPath(path, paint);
   *         }
   *     }
   * }
   * ```
   */
  public open fun drawShadow(
    canvas: SkCanvas?,
    path: SkPath,
    rec: SkDrawShadowRec,
  ) {
    TODO("Implement drawShadow")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDevice::drawPatch(const SkPoint cubics[12], const SkColor colors[4],
   *                          const SkPoint texCoords[4], sk_sp<SkBlender> blender,
   *                          const SkPaint& paint) {
   *     SkISize lod = SkPatchUtils::GetLevelOfDetail(cubics, &this->localToDevice());
   *     auto vertices = SkPatchUtils::MakeVertices(cubics, colors, texCoords, lod.width(), lod.height(),
   *                                                this->imageInfo().colorSpace());
   *     if (vertices) {
   *         this->drawVertices(vertices.get(), std::move(blender), paint);
   *     }
   * }
   * ```
   */
  public open fun drawPatch(
    cubics: Array<SkPoint>,
    colors: Array<SkColor>,
    texCoords: Array<SkPoint>,
    blender: SkSp<SkBlender>,
    paint: SkPaint,
  ) {
    TODO("Implement drawPatch")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDevice::drawAtlas(SkSpan<const SkRSXform> xform,
   *                          SkSpan<const SkRect> tex,
   *                          SkSpan<const SkColor> colors,
   *                          sk_sp<SkBlender> blender,
   *                          const SkPaint& paint) {
   *     const size_t quadCount = xform.size();
   *     const size_t triCount = quadCount << 1;
   *     const size_t vertexCount = triCount * 3;
   *     uint32_t flags = SkVertices::kHasTexCoords_BuilderFlag;
   *     if (!colors.empty()) {
   *         flags |= SkVertices::kHasColors_BuilderFlag;
   *     }
   *     SkVertices::Builder builder(SkVertices::kTriangles_VertexMode, vertexCount, 0, flags);
   *
   *     SkPoint* vPos = builder.positions();
   *     SkPoint* vTex = builder.texCoords();
   *     SkColor* vCol = builder.colors();
   *     for (size_t i = 0; i < quadCount; ++i) {
   *         SkPoint tmp[4];
   *         xform[i].toQuad(tex[i].width(), tex[i].height(), tmp);
   *         vPos = quad_to_tris(vPos, tmp);
   *
   *         vTex = quad_to_tris(vTex, tex[i].toQuad());
   *
   *         if (!colors.empty()) {
   *             SkOpts::memset32(vCol, colors[i], 6);
   *             vCol += 6;
   *         }
   *     }
   *     this->drawVertices(builder.detach().get(), std::move(blender), paint);
   * }
   * ```
   */
  public open fun drawAtlas(
    xform: SkSpan<SkRSXform>,
    tex: SkSpan<SkRect>,
    colors: SkSpan<SkColor>,
    blender: SkSp<SkBlender>,
    paint: SkPaint,
  ) {
    TODO("Implement drawAtlas")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void drawAnnotation(const SkRect&, const char[], SkData*) {}
   * ```
   */
  public open fun drawAnnotation(
    param0: SkRect,
    param1: CharArray,
    param2: SkData?,
  ) {
    TODO("Implement drawAnnotation")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDevice::drawEdgeAAQuad(const SkRect& r, const SkPoint clip[4], SkCanvas::QuadAAFlags aa,
   *                               const SkColor4f& color, SkBlendMode mode) {
   *     SkPaint paint;
   *     paint.setColor4f(color);
   *     paint.setBlendMode(mode);
   *     paint.setAntiAlias(aa == SkCanvas::kAll_QuadAAFlags);
   *
   *     if (clip) {
   *         // Draw the clip directly as a quad since it's a filled color with no local coords
   *         this->drawPath(SkPath::Polygon({clip, 4}, true), paint);
   *     } else {
   *         this->drawRect(r, paint);
   *     }
   * }
   * ```
   */
  public open fun drawEdgeAAQuad(
    rect: SkRect,
    clip: Array<SkPoint>,
    aaFlags: SkCanvas.QuadAAFlags,
    color: SkColor4f,
    mode: SkBlendMode,
  ) {
    TODO("Implement drawEdgeAAQuad")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDevice::drawEdgeAAImageSet(const SkCanvas::ImageSetEntry images[], int count,
   *                                   const SkPoint dstClips[], const SkMatrix preViewMatrices[],
   *                                   const SkSamplingOptions& sampling, const SkPaint& paint,
   *                                   SkCanvas::SrcRectConstraint constraint) {
   *     SkASSERT(paint.getStyle() == SkPaint::kFill_Style);
   *     SkASSERT(!paint.getPathEffect());
   *
   *     SkPaint entryPaint = paint;
   *     const SkM44 baseLocalToDevice = this->localToDevice44();
   *     int clipIndex = 0;
   *     for (int i = 0; i < count; ++i) {
   *         // TODO: Handle per-edge AA. Right now this mirrors the SkiaRenderer component of Chrome
   *         // which turns off antialiasing unless all four edges should be antialiased. This avoids
   *         // seaming in tiled composited layers.
   *         entryPaint.setAntiAlias(images[i].fAAFlags == SkCanvas::kAll_QuadAAFlags);
   *         entryPaint.setAlphaf(paint.getAlphaf() * images[i].fAlpha);
   *
   *         SkASSERT(images[i].fMatrixIndex < 0 || preViewMatrices);
   *         if (images[i].fMatrixIndex >= 0) {
   *             this->setLocalToDevice(baseLocalToDevice *
   *                                    SkM44(preViewMatrices[images[i].fMatrixIndex]));
   *         }
   *
   *         SkASSERT(!images[i].fHasClip || dstClips);
   *         if (images[i].fHasClip) {
   *             // Since drawImageRect requires a srcRect, the dst clip is implemented as a true clip
   *             this->pushClipStack();
   *             SkPath clipPath = SkPath::Polygon({dstClips + clipIndex, 4}, true);
   *             this->clipPath(clipPath, SkClipOp::kIntersect, entryPaint.isAntiAlias());
   *             clipIndex += 4;
   *         }
   *         this->drawImageRect(images[i].fImage.get(), &images[i].fSrcRect, images[i].fDstRect,
   *                             sampling, entryPaint, constraint);
   *         if (images[i].fHasClip) {
   *             this->popClipStack();
   *         }
   *         if (images[i].fMatrixIndex >= 0) {
   *             this->setLocalToDevice(baseLocalToDevice);
   *         }
   *     }
   * }
   * ```
   */
  public open fun drawEdgeAAImageSet(
    images: Array<SkCanvas.ImageSetEntry>,
    count: Int,
    dstClips: Array<SkPoint>,
    preViewMatrices: Array<SkMatrix>,
    sampling: SkSamplingOptions,
    paint: SkPaint,
    constraint: SkCanvas.SrcRectConstraint,
  ) {
    TODO("Implement drawEdgeAAImageSet")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDevice::drawDrawable(SkCanvas* canvas, SkDrawable* drawable, const SkMatrix* matrix) {
   *     drawable->draw(canvas, matrix);
   * }
   * ```
   */
  public open fun drawDrawable(
    canvas: SkCanvas?,
    drawable: SkDrawable?,
    matrix: SkMatrix?,
  ) {
    TODO("Implement drawDrawable")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkSpecialImage> SkDevice::snapSpecial(const SkIRect&, bool forceCopy) { return nullptr; }
   * ```
   */
  public open fun snapSpecial(subset: SkIRect, forceCopy: Boolean = TODO()): SkSp<SkSpecialImage> {
    TODO("Implement snapSpecial")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkSpecialImage> SkDevice::snapSpecialScaled(const SkIRect& subset,
   *                                                   const SkISize& dstDims) {
   *     return nullptr;
   * }
   * ```
   */
  public open fun snapSpecialScaled(subset: SkIRect, dstDims: SkISize): SkSp<SkSpecialImage> {
    TODO("Implement snapSpecialScaled")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkSpecialImage> SkDevice::snapSpecial() {
   *     return this->snapSpecial(SkIRect::MakeWH(this->width(), this->height()));
   * }
   * ```
   */
  public fun snapSpecial(): SkSp<SkSpecialImage> {
    TODO("Implement snapSpecial")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDevice::drawDevice(SkDevice* device,
   *                           const SkSamplingOptions& sampling,
   *                           const SkPaint& paint) {
   *     sk_sp<SkSpecialImage> deviceImage = device->snapSpecial();
   *     if (deviceImage) {
   *         // SkCanvas only calls drawDevice() when there are no filters (so the transform is pixel
   *         // aligned). As such it can be drawn without clamping.
   *         SkMatrix relativeTransform = device->getRelativeTransform(*this).asM33();
   *         const bool strict = sampling != SkFilterMode::kNearest ||
   *                             !relativeTransform.isTranslate() ||
   *                             !SkScalarIsInt(relativeTransform.getTranslateX()) ||
   *                             !SkScalarIsInt(relativeTransform.getTranslateY());
   *         this->drawSpecial(deviceImage.get(), relativeTransform, sampling, paint,
   *                           strict ? SkCanvas::kStrict_SrcRectConstraint
   *                                  : SkCanvas::kFast_SrcRectConstraint);
   *     }
   * }
   * ```
   */
  public open fun drawDevice(
    device: SkDevice?,
    sampling: SkSamplingOptions,
    paint: SkPaint,
  ) {
    TODO("Implement drawDevice")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDevice::drawSpecial(SkSpecialImage*, const SkMatrix&, const SkSamplingOptions&,
   *                            const SkPaint&, SkCanvas::SrcRectConstraint) {}
   * ```
   */
  public open fun drawSpecial(
    param0: SkSpecialImage?,
    localToDevice: SkMatrix,
    param2: SkSamplingOptions,
    param3: SkPaint,
    constraint: SkCanvas.SrcRectConstraint = TODO(),
  ) {
    TODO("Implement drawSpecial")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDevice::drawCoverageMask(const SkSpecialImage*, const SkMatrix& maskToDevice,
   *                                 const SkSamplingOptions&, const SkPaint&) {
   *     // This shouldn't be reached; SkCanvas will only call this if
   *     // useDrawCoverageMaskForMaskFilters() is overridden to return true.
   *     SK_ABORT("Must override if useDrawCoverageMaskForMaskFilters() is true");
   * }
   * ```
   */
  public open fun drawCoverageMask(
    param0: SkSpecialImage?,
    maskToDevice: SkMatrix,
    param2: SkSamplingOptions,
    param3: SkPaint,
  ) {
    TODO("Implement drawCoverageMask")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool drawBlurredRRect(const SkRRect&, const SkPaint&, float deviceSigma) {
   *         return false;
   *     }
   * ```
   */
  public open fun drawBlurredRRect(
    param0: SkRRect,
    param1: SkPaint,
    deviceSigma: Float,
  ): Boolean {
    TODO("Implement drawBlurredRRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDevice::drawFilteredImage(const skif::Mapping& mapping,
   *                                  SkSpecialImage* src,
   *                                  SkColorType colorType,
   *                                  const SkImageFilter* filter,
   *                                  const SkSamplingOptions& sampling,
   *                                  const SkPaint& paint) {
   *     SkASSERT(!paint.getImageFilter() && !paint.getMaskFilter());
   *
   *     skif::LayerSpace<SkIRect> targetOutput = mapping.deviceToLayer(
   *             skif::DeviceSpace<SkIRect>(this->devClipBounds()));
   *
   *     if (colorType == kUnknown_SkColorType) {
   *         colorType = kRGBA_8888_SkColorType;
   *     }
   *
   *     skif::Stats stats;
   *     skif::Context ctx{this->createImageFilteringBackend(src ? src->props() : this->surfaceProps(),
   *                                                         colorType),
   *                       mapping,
   *                       targetOutput,
   *                       skif::FilterResult(sk_ref_sp(src)),
   *                       this->imageInfo().colorSpace(),
   *                       &stats};
   *
   *     SkIPoint offset;
   *     sk_sp<SkSpecialImage> result = as_IFB(filter)->filterImage(ctx).imageAndOffset(ctx, &offset);
   *     stats.reportStats();
   *     if (result) {
   *         SkMatrix deviceMatrixWithOffset = mapping.layerToDevice().asM33();
   *         deviceMatrixWithOffset.preTranslate(offset.fX, offset.fY);
   *         this->drawSpecial(result.get(), deviceMatrixWithOffset, sampling, paint);
   *     }
   * }
   * ```
   */
  public fun drawFilteredImage(
    mapping: Mapping,
    src: SkSpecialImage?,
    ct: SkColorType,
    filter: SkImageFilter?,
    sampling: SkSamplingOptions,
    paint: SkPaint,
  ) {
    TODO("Implement drawFilteredImage")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDevice::setDeviceCoordinateSystem(const SkM44& deviceToGlobal,
   *                                          const SkM44& globalToDevice,
   *                                          const SkM44& localToDevice,
   *                                          int bufferOriginX,
   *                                          int bufferOriginY) {
   *     fDeviceToGlobal = deviceToGlobal;
   *     fDeviceToGlobal.normalizePerspective();
   *     fGlobalToDevice = globalToDevice;
   *     fGlobalToDevice.normalizePerspective();
   *
   *     fLocalToDevice = localToDevice;
   *     fLocalToDevice.normalizePerspective();
   *     if (bufferOriginX | bufferOriginY) {
   *         fDeviceToGlobal.preTranslate(bufferOriginX, bufferOriginY);
   *         fGlobalToDevice.postTranslate(-bufferOriginX, -bufferOriginY);
   *         fLocalToDevice.postTranslate(-bufferOriginX, -bufferOriginY);
   *     }
   *     fLocalToDevice33 = fLocalToDevice.asM33();
   *     fLocalToDeviceDirty = true;
   * }
   * ```
   */
  protected fun setDeviceCoordinateSystem(
    deviceToGlobal: SkM44,
    globalToDevice: SkM44,
    localToDevice: SkM44,
    bufferOriginX: Int,
    bufferOriginY: Int,
  ) {
    TODO("Implement setDeviceCoordinateSystem")
  }

  /**
   * C++ original:
   * ```cpp
   * void setOrigin(const SkM44& globalCTM, int x, int y) {
   *         this->setDeviceCoordinateSystem(SkM44(), SkM44(), globalCTM, x, y);
   *     }
   * ```
   */
  protected fun setOrigin(
    globalCTM: SkM44,
    x: Int,
    y: Int,
  ) {
    TODO("Implement setOrigin")
  }

  /**
   * C++ original:
   * ```cpp
   * bool checkLocalToDeviceDirty() {
   *         bool wasDirty = fLocalToDeviceDirty;
   *         fLocalToDeviceDirty = false;
   *         return wasDirty;
   *     }
   * ```
   */
  protected fun checkLocalToDeviceDirty(): Boolean {
    TODO("Implement checkLocalToDeviceDirty")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<skif::Backend> SkDevice::createImageFilteringBackend(const SkSurfaceProps& surfaceProps,
   *                                                            SkColorType colorType) const {
   *     return skif::MakeRasterBackend(surfaceProps, colorType);
   * }
   * ```
   */
  public open fun createImageFilteringBackend(surfaceProps: SkSurfaceProps, colorType: SkColorType): SkSp<Backend> {
    TODO("Implement createImageFilteringBackend")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool onReadPixels(const SkPixmap&, int x, int y) { return false; }
   * ```
   */
  public open fun onReadPixels(
    param0: SkPixmap,
    x: Int,
    y: Int,
  ): Boolean {
    TODO("Implement onReadPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool onWritePixels(const SkPixmap&, int x, int y) { return false; }
   * ```
   */
  public open fun onWritePixels(
    param0: SkPixmap,
    x: Int,
    y: Int,
  ): Boolean {
    TODO("Implement onWritePixels")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool onAccessPixels(SkPixmap*) { return false; }
   * ```
   */
  public open fun onAccessPixels(param0: SkPixmap?): Boolean {
    TODO("Implement onAccessPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool onPeekPixels(SkPixmap*) { return false; }
   * ```
   */
  public open fun onPeekPixels(param0: SkPixmap?): Boolean {
    TODO("Implement onPeekPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void onClipShader(sk_sp<SkShader>) = 0
   * ```
   */
  private abstract fun onClipShader(param0: SkSp<SkShader>)

  /**
   * C++ original:
   * ```cpp
   * virtual void onDrawGlyphRunList(SkCanvas*,
   *                                     const sktext::GlyphRunList&,
   *                                     const SkPaint& paint) = 0
   * ```
   */
  private abstract fun onDrawGlyphRunList(
    param0: SkCanvas?,
    param1: GlyphRunList,
    paint: SkPaint,
  )

  /**
   * C++ original:
   * ```cpp
   * void SkDevice::simplifyGlyphRunRSXFormAndRedraw(SkCanvas* canvas,
   *                                                 const sktext::GlyphRunList& glyphRunList,
   *                                                 const SkPaint& paint) {
   *     for (const sktext::GlyphRun& run : glyphRunList) {
   *         if (run.scaledRotations().empty()) {
   *             auto subList = glyphRunList.builder()->makeGlyphRunList(run, paint, {0, 0});
   *             this->drawGlyphRunList(canvas, subList, paint);
   *         } else {
   *             SkPoint origin = glyphRunList.origin();
   *             SkPoint sharedPos{0, 0};    // we're at the origin
   *             SkGlyphID sharedGlyphID;
   *             sktext::GlyphRun glyphRun {
   *                     run.font(),
   *                     SkSpan<const SkPoint>{&sharedPos, 1},
   *                     SkSpan<const SkGlyphID>{&sharedGlyphID, 1},
   *                     SkSpan<const char>{},
   *                     SkSpan<const uint32_t>{},
   *                     SkSpan<const SkVector>{}
   *             };
   *
   *             for (auto [i, glyphID, pos] : SkMakeEnumerate(run.source())) {
   *                 sharedGlyphID = glyphID;
   *                 auto [scos, ssin] = run.scaledRotations()[i];
   *                 SkRSXform rsxForm = SkRSXform::Make(scos, ssin, pos.x(), pos.y());
   *                 SkMatrix glyphToLocal;
   *                 glyphToLocal.setRSXform(rsxForm).postTranslate(origin.x(), origin.y());
   *
   *                 // We want to rotate each glyph by the rsxform, but we don't want to rotate "space"
   *                 // (i.e. the shader that cares about the ctm) so we have to undo our little ctm
   *                 // trick with a localmatrixshader so that the shader draws as if there was no
   *                 // change to the ctm.
   *                 SkPaint invertingPaint{paint};
   *                 invertingPaint.setShader(make_post_inverse_lm(paint.getShader(), glyphToLocal));
   *                 SkAutoCanvasRestore acr(canvas, true);
   *                 canvas->concat(SkM44(glyphToLocal));
   *                 sktext::GlyphRunList subList =
   *                         glyphRunList.builder()->makeGlyphRunList(glyphRun, paint, {0, 0});
   *                 this->drawGlyphRunList(canvas, subList, invertingPaint);
   *             }
   *         }
   *     }
   * }
   * ```
   */
  private fun simplifyGlyphRunRSXFormAndRedraw(
    canvas: SkCanvas?,
    glyphRunList: GlyphRunList,
    paint: SkPaint,
  ) {
    TODO("Implement simplifyGlyphRunRSXFormAndRedraw")
  }

  public data class CreateInfo public constructor(
    public val fInfo: SkImageInfo,
    public val fPixelGeometry: SkPixelGeometry,
    public var fAllocator: SkRasterHandleAllocator?,
  )
}
