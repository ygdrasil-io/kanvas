package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import kotlin.Short
import kotlin.ULong
import kotlin.UShort
import kotlin.Unit
import org.skia.foundation.SkGlyphID
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkFixed
import org.skia.math.SkIRect
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.math.SkScalar
import org.skia.math.SkVector
import org.skia.memory.SkArenaAlloc

/**
 * C++ original:
 * ```cpp
 * class SkGlyph {
 * public:
 *     static std::optional<SkGlyph> MakeFromBuffer(SkReadBuffer&);
 *     // SkGlyph() is used for testing.
 *     constexpr SkGlyph() : SkGlyph{SkPackedGlyphID()} { }
 *     SkGlyph(const SkGlyph&) = default;
 *     SkGlyph& operator=(const SkGlyph&) = default;
 *     SkGlyph(SkGlyph&&) = default;
 *     SkGlyph& operator=(SkGlyph&&) = default;
 *     ~SkGlyph() = default;
 *     constexpr explicit SkGlyph(SkPackedGlyphID id) : fID{id} { }
 *
 *     SkVector advanceVector() const { return SkVector{fAdvanceX, fAdvanceY}; }
 *     SkScalar advanceX() const { return fAdvanceX; }
 *     SkScalar advanceY() const { return fAdvanceY; }
 *
 *     SkGlyphID getGlyphID() const { return fID.glyphID(); }
 *     SkPackedGlyphID getPackedID() const { return fID; }
 *     SkFixed getSubXFixed() const { return fID.getSubXFixed(); }
 *     SkFixed getSubYFixed() const { return fID.getSubYFixed(); }
 *
 *     size_t rowBytes() const;
 *     size_t rowBytesUsingFormat(SkMask::Format format) const;
 *
 *     // Call this to set all the metrics fields to 0 (e.g. if the scaler
 *     // encounters an error measuring a glyph). Note: this does not alter the
 *     // fImage, fPath, fID, fMaskFormat fields.
 *     void zeroMetrics();
 *
 *     SkMask mask() const;
 *
 *     SkMask mask(SkPoint position) const;
 *
 *     // Image
 *     // If we haven't already tried to associate an image with this glyph
 *     // (i.e. setImageHasBeenCalled() returns false), then use the
 *     // SkScalerContext or const void* argument to set the image.
 *     bool setImage(SkArenaAlloc* alloc, SkScalerContext* scalerContext);
 *     bool setImage(SkArenaAlloc* alloc, const void* image);
 *
 *     // Merge the 'from' glyph into this glyph using alloc to allocate image data. Return the number
 *     // of bytes allocated. Copy the width, height, top, left, format, and image into this glyph
 *     // making a copy of the image using the alloc.
 *     size_t setMetricsAndImage(SkArenaAlloc* alloc, const SkGlyph& from);
 *
 *     // Returns true if the image has been set.
 *     bool setImageHasBeenCalled() const {
 *         // Check for empty bounds first to guard against fImage somehow being set.
 *         return this->isEmpty() || fImage != nullptr || this->imageTooLarge();
 *     }
 *
 *     // Return a pointer to the path if the image exists, otherwise return nullptr.
 *     const void* image() const { SkASSERT(this->setImageHasBeenCalled()); return fImage; }
 *
 *     // Return the size of the image.
 *     size_t imageSize() const;
 *
 *     // Path
 *     // If we haven't already tried to associate a path to this glyph
 *     // (i.e. setPathHasBeenCalled() returns false), then use the
 *     // SkScalerContext or SkPath argument to try to do so.  N.B. this
 *     // may still result in no path being associated with this glyph,
 *     // e.g. if you pass a null SkPath or the typeface is bitmap-only.
 *     //
 *     // This setPath() call is sticky... once you call it, the glyph
 *     // stays in its state permanently, ignoring any future calls.
 *     //
 *     // Returns true if this is the first time you called setPath()
 *     // and there actually is a path; call path() to get it.
 *     bool setPath(SkArenaAlloc* alloc, SkScalerContext* scalerContext);
 *     bool setPath(SkArenaAlloc* alloc, const SkPath* path, bool hairline, bool modified);
 *
 *     // Returns true if that path has been set.
 *     bool setPathHasBeenCalled() const { return fPathData != nullptr; }
 *
 *     // Return a pointer to the path if it exists, otherwise return nullptr. Only works if the
 *     // path was previously set.
 *     const SkPath* path() const;
 *     bool pathIsHairline() const;
 *     bool pathIsModified() const;
 *
 *     bool setDrawable(SkArenaAlloc* alloc, SkScalerContext* scalerContext);
 *     bool setDrawable(SkArenaAlloc* alloc, sk_sp<SkDrawable> drawable);
 *     bool setDrawableHasBeenCalled() const { return fDrawableData != nullptr; }
 *     SkDrawable* drawable() const;
 *
 *     // Format
 *     bool isColor() const { return fMaskFormat == SkMask::kARGB32_Format; }
 *     SkMask::Format maskFormat() const { return fMaskFormat; }
 *     size_t formatAlignment() const;
 *
 *     // Bounds
 *     int maxDimension() const { return std::max(fWidth, fHeight); }
 *     SkIRect iRect() const { return SkIRect::MakeXYWH(fLeft, fTop, fWidth, fHeight); }
 *     SkRect rect()   const { return SkRect::MakeXYWH(fLeft, fTop, fWidth, fHeight);  }
 *     SkGlyphRect glyphRect() const {
 *         return SkGlyphRect(fLeft, fTop, fLeft + fWidth, fTop + fHeight);
 *     }
 *     int left()   const { return fLeft;   }
 *     int top()    const { return fTop;    }
 *     int width()  const { return fWidth;  }
 *     int height() const { return fHeight; }
 *     bool isEmpty() const {
 *         return fWidth == 0 || fHeight == 0;
 *     }
 *     bool imageTooLarge() const { return fWidth >= kMaxGlyphWidth; }
 *
 *     uint16_t extraBits() const { return fScalerContextBits; }
 *
 *     // Make sure that the intercept information is on the glyph and return it, or return it if it
 *     // already exists.
 *     // * bounds - [0] - top of underline; [1] - bottom of underline.
 *     // * scale, xPos - information about how wide the gap is.
 *     // * array - accumulated gaps for many characters if not null.
 *     // * count - the number of gaps.
 *     void ensureIntercepts(const SkScalar bounds[2], SkScalar scale, SkScalar xPos,
 *                           SkScalar* array, int* count, SkArenaAlloc* alloc);
 *
 *     // Deprecated. Do not use. The last use is in SkChromeRemoteCache, and will be deleted soon.
 *     void setImage(void* image) { fImage = image; }
 *
 *     // Serialize/deserialize functions.
 *     // Flatten the metrics portions, but no drawing data.
 *     void flattenMetrics(SkWriteBuffer&) const;
 *
 *     // Flatten just the the mask data.
 *     void flattenImage(SkWriteBuffer&) const;
 *
 *     // Read the image data, store it in the alloc, and add it to the glyph.
 *     size_t addImageFromBuffer(SkReadBuffer&, SkArenaAlloc*);
 *
 *     // Flatten just the path data.
 *     void flattenPath(SkWriteBuffer&) const;
 *
 *     // Read the path data, create the glyph's path data in the alloc, and add it to the glyph.
 *     size_t addPathFromBuffer(SkReadBuffer&, SkArenaAlloc*);
 *
 *     // Flatten just the drawable data.
 *     void flattenDrawable(SkWriteBuffer&) const;
 *
 *     // Read the drawable data, create the glyph's drawable data in the alloc, and add it to the
 *     // glyph.
 *     size_t addDrawableFromBuffer(SkReadBuffer&, SkArenaAlloc*);
 *
 * private:
 *     // There are two sides to an SkGlyph, the scaler side (things that create glyph data) have
 *     // access to all the fields. Scalers are assumed to maintain all the SkGlyph invariants. The
 *     // consumer side has a tighter interface.
 *     friend class SkScalerContext;
 *     friend class SkGlyphTestPeer;
 *
 *     inline static constexpr uint16_t kMaxGlyphWidth = 1u << 13u;
 *
 *     // Support horizontal and vertical skipping strike-through / underlines.
 *     // The caller walks the linked list looking for a match. For a horizontal underline,
 *     // the fBounds contains the top and bottom of the underline. The fInterval pair contains the
 *     // beginning and end of the intersection of the bounds and the glyph's path.
 *     // If interval[0] >= interval[1], no intersection was found.
 *     struct Intercept {
 *         Intercept* fNext;
 *         SkScalar   fBounds[2];    // for horz underlines, the boundaries in Y
 *         SkScalar   fInterval[2];  // the outside intersections of the axis and the glyph
 *     };
 *
 *     struct PathData {
 *         Intercept* fIntercept{nullptr};
 *         SkPath     fPath;
 *         bool       fHasPath{false};
 *         // A normal user-path will have patheffects applied to it and eventually become a dev-path.
 *         // A dev-path is always a fill-path, except when it is hairline.
 *         // The fPath is a dev-path, so sidecar the paths hairline status.
 *         // This allows the user to avoid filling paths which should not be filled.
 *         bool       fHairline{false};
 *         // This is set if the path is significantly different from what a reasonable interpreter of
 *         // the underlying font data would produce. This is set if any non-identity matrix, stroke,
 *         // path effect, emboldening, etc is applied.
 *         // This allows Document implementations to know if a glyph should be drawn out of the font
 *         // data or needs to be embedded differently.
 *         bool       fModified{false};
 *     };
 *
 *     struct DrawableData {
 *         Intercept* fIntercept{nullptr};
 *         sk_sp<SkDrawable> fDrawable;
 *         bool fHasDrawable{false};
 *     };
 *
 *     size_t allocImage(SkArenaAlloc* alloc);
 *
 *     void installImage(void* imageData) {
 *         SkASSERT(!this->setImageHasBeenCalled());
 *         fImage = imageData;
 *     }
 *
 *     // path == nullptr indicates that there is no path.
 *     void installPath(SkArenaAlloc* alloc, const SkPath* path, bool hairline, bool modified);
 *
 *     // drawable == nullptr indicates that there is no path.
 *     void installDrawable(SkArenaAlloc* alloc, sk_sp<SkDrawable> drawable);
 *
 *     // The width and height of the glyph mask.
 *     uint16_t  fWidth  = 0,
 *               fHeight = 0;
 *
 *     // The offset from the glyphs origin on the baseline to the top left of the glyph mask.
 *     int16_t   fTop  = 0,
 *               fLeft = 0;
 *
 *     // fImage must remain null if the glyph is empty or if width > kMaxGlyphWidth.
 *     void*     fImage    = nullptr;
 *
 *     // Path data has tricky state. If the glyph isEmpty, then fPathData should always be nullptr,
 *     // else if fPathData is not null, then a path has been requested. The fPath field of fPathData
 *     // may still be null after the request meaning that there is no path for this glyph.
 *     PathData* fPathData = nullptr;
 *     DrawableData* fDrawableData = nullptr;
 *
 *     // The advance for this glyph.
 *     float     fAdvanceX = 0,
 *               fAdvanceY = 0;
 *
 *     SkMask::Format fMaskFormat{SkMask::kBW_Format};
 *
 *     // Used by the SkScalerContext to pass state from generateMetrics to generateImage.
 *     // Usually specifies which glyph representation was used to generate the metrics.
 *     uint16_t  fScalerContextBits = 0;
 *
 *     // An SkGlyph can be created with just a packedID, but generally speaking some glyph factory
 *     // needs to actually fill out the glyph before it can be used as part of that system.
 *     SkDEBUGCODE(bool fAdvancesBoundsFormatAndInitialPathDone{false};)
 *
 *     SkPackedGlyphID fID;
 * }
 * ```
 */
public data class SkGlyph public constructor(
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr uint16_t kMaxGlyphWidth = 1u << 13u
   * ```
   */
  private var fWidth: UShort,
  /**
   * C++ original:
   * ```cpp
   * uint16_t  fWidth  = 0
   * ```
   */
  private var fHeight: UShort,
  /**
   * C++ original:
   * ```cpp
   * uint16_t  fWidth  = 0,
   *               fHeight = 0
   * ```
   */
  private var fTop: Short,
  /**
   * C++ original:
   * ```cpp
   * int16_t   fTop  = 0
   * ```
   */
  private var fLeft: Short,
  /**
   * C++ original:
   * ```cpp
   * int16_t   fTop  = 0,
   *               fLeft = 0
   * ```
   */
  private var fImage: Unit?,
  /**
   * C++ original:
   * ```cpp
   * void*     fImage    = nullptr
   * ```
   */
  private var fPathData: PathData?,
  /**
   * C++ original:
   * ```cpp
   * PathData* fPathData = nullptr
   * ```
   */
  private var fDrawableData: DrawableData?,
  /**
   * C++ original:
   * ```cpp
   * DrawableData* fDrawableData = nullptr
   * ```
   */
  private var fAdvanceX: Float,
  /**
   * C++ original:
   * ```cpp
   * float     fAdvanceX = 0
   * ```
   */
  private var fAdvanceY: Float,
  /**
   * C++ original:
   * ```cpp
   * float     fAdvanceX = 0,
   *               fAdvanceY = 0
   * ```
   */
  private var fMaskFormat: SkMask.Format,
  /**
   * C++ original:
   * ```cpp
   * SkMask::Format fMaskFormat{SkMask::kBW_Format}
   * ```
   */
  private var fScalerContextBits: UShort,
  /**
   * C++ original:
   * ```cpp
   * uint16_t  fScalerContextBits = 0
   * ```
   */
  private var fID: SkPackedGlyphID,
) {
  /**
   * C++ original:
   * ```cpp
   * SkGlyph& operator=(const SkGlyph&) = default
   * ```
   */
  public fun assign(param0: SkGlyph) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * SkGlyph& operator=(SkGlyph&&) = default
   * ```
   */
  public fun advanceVector(): SkVector {
    TODO("Implement advanceVector")
  }

  /**
   * C++ original:
   * ```cpp
   * SkVector advanceVector() const { return SkVector{fAdvanceX, fAdvanceY}; }
   * ```
   */
  public fun advanceX(): SkScalar {
    TODO("Implement advanceX")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar advanceX() const { return fAdvanceX; }
   * ```
   */
  public fun advanceY(): SkScalar {
    TODO("Implement advanceY")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar advanceY() const { return fAdvanceY; }
   * ```
   */
  public fun getGlyphID(): SkGlyphID {
    TODO("Implement getGlyphID")
  }

  /**
   * C++ original:
   * ```cpp
   * SkGlyphID getGlyphID() const { return fID.glyphID(); }
   * ```
   */
  public fun getPackedID(): SkPackedGlyphID {
    TODO("Implement getPackedID")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPackedGlyphID getPackedID() const { return fID; }
   * ```
   */
  public fun getSubXFixed(): SkFixed {
    TODO("Implement getSubXFixed")
  }

  /**
   * C++ original:
   * ```cpp
   * SkFixed getSubXFixed() const { return fID.getSubXFixed(); }
   * ```
   */
  public fun getSubYFixed(): SkFixed {
    TODO("Implement getSubYFixed")
  }

  /**
   * C++ original:
   * ```cpp
   * SkFixed getSubYFixed() const { return fID.getSubYFixed(); }
   * ```
   */
  public fun rowBytes(): ULong {
    TODO("Implement rowBytes")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkGlyph::rowBytes() const {
   *     return format_rowbytes(fWidth, fMaskFormat);
   * }
   * ```
   */
  public fun rowBytesUsingFormat(format: SkMask.Format): ULong {
    TODO("Implement rowBytesUsingFormat")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkGlyph::rowBytesUsingFormat(SkMask::Format format) const {
   *     return format_rowbytes(fWidth, format);
   * }
   * ```
   */
  public fun zeroMetrics() {
    TODO("Implement zeroMetrics")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkGlyph::zeroMetrics() {
   *     fAdvanceX = 0;
   *     fAdvanceY = 0;
   *     fWidth    = 0;
   *     fHeight   = 0;
   *     fTop      = 0;
   *     fLeft     = 0;
   * }
   * ```
   */
  public fun mask(): SkMask {
    TODO("Implement mask")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMask SkGlyph::mask() const {
   *     SkIRect bounds = SkIRect::MakeXYWH(fLeft, fTop, fWidth, fHeight);
   *     return SkMask(static_cast<const uint8_t*>(fImage), bounds, this->rowBytes(), fMaskFormat);
   * }
   * ```
   */
  public fun mask(position: SkPoint): SkMask {
    TODO("Implement mask")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMask SkGlyph::mask(SkPoint position) const {
   *     SkASSERT(SkScalarIsInt(position.x()) && SkScalarIsInt(position.y()));
   *     SkIRect bounds = SkIRect::MakeXYWH(fLeft, fTop, fWidth, fHeight);
   *     bounds.offset(SkScalarFloorToInt(position.x()), SkScalarFloorToInt(position.y()));
   *     return SkMask(static_cast<const uint8_t*>(fImage), bounds, this->rowBytes(), fMaskFormat);
   * }
   * ```
   */
  public fun setImage(alloc: SkArenaAlloc?, scalerContext: SkScalerContext?): Boolean {
    TODO("Implement setImage")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkGlyph::setImage(SkArenaAlloc* alloc, SkScalerContext* scalerContext) {
   *     if (!this->setImageHasBeenCalled()) {
   *         // It used to be that getImage() could change the fMaskFormat. Extra checking to make
   *         // sure there are no regressions.
   *         SkDEBUGCODE(SkMask::Format oldFormat = this->maskFormat());
   *         this->allocImage(alloc);
   *         scalerContext->getImage(*this);
   *         SkASSERT(oldFormat == this->maskFormat());
   *         return true;
   *     }
   *     return false;
   * }
   * ```
   */
  public fun setImage(alloc: SkArenaAlloc?, image: Unit?): Boolean {
    TODO("Implement setImage")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkGlyph::setImage(SkArenaAlloc* alloc, const void* image) {
   *     if (!this->setImageHasBeenCalled()) {
   *         this->allocImage(alloc);
   *         memcpy(fImage, image, this->imageSize());
   *         return true;
   *     }
   *     return false;
   * }
   * ```
   */
  public fun setMetricsAndImage(alloc: SkArenaAlloc?, from: SkGlyph): ULong {
    TODO("Implement setMetricsAndImage")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkGlyph::setMetricsAndImage(SkArenaAlloc* alloc, const SkGlyph& from) {
   *     // Since the code no longer tries to find replacement glyphs, the image should always be
   *     // nullptr.
   *     SkASSERT(fImage == nullptr || from.fImage == nullptr);
   *
   *     // TODO(herb): remove "if" when we are sure there are no colliding glyphs.
   *     if (fImage == nullptr) {
   *         fAdvanceX = from.fAdvanceX;
   *         fAdvanceY = from.fAdvanceY;
   *         fWidth = from.fWidth;
   *         fHeight = from.fHeight;
   *         fTop = from.fTop;
   *         fLeft = from.fLeft;
   *         fScalerContextBits = from.fScalerContextBits;
   *         fMaskFormat = from.fMaskFormat;
   *
   *         // From glyph may not have an image because the glyph is too large.
   *         if (from.fImage != nullptr && this->setImage(alloc, from.image())) {
   *             return this->imageSize();
   *         }
   *
   *         SkDEBUGCODE(fAdvancesBoundsFormatAndInitialPathDone = from.fAdvancesBoundsFormatAndInitialPathDone;)
   *     }
   *     return 0;
   * }
   * ```
   */
  public fun setImageHasBeenCalled(): Boolean {
    TODO("Implement setImageHasBeenCalled")
  }

  /**
   * C++ original:
   * ```cpp
   * bool setImageHasBeenCalled() const {
   *         // Check for empty bounds first to guard against fImage somehow being set.
   *         return this->isEmpty() || fImage != nullptr || this->imageTooLarge();
   *     }
   * ```
   */
  public fun image() {
    TODO("Implement image")
  }

  /**
   * C++ original:
   * ```cpp
   * const void* image() const { SkASSERT(this->setImageHasBeenCalled()); return fImage; }
   * ```
   */
  public fun imageSize(): ULong {
    TODO("Implement imageSize")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkGlyph::imageSize() const {
   *     if (this->isEmpty() || this->imageTooLarge()) { return 0; }
   *
   *     size_t size = this->rowBytes() * fHeight;
   *
   *     if (fMaskFormat == SkMask::k3D_Format) {
   *         size *= 3;
   *     }
   *
   *     return size;
   * }
   * ```
   */
  public fun setPath(alloc: SkArenaAlloc?, scalerContext: SkScalerContext?): Boolean {
    TODO("Implement setPath")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkGlyph::setPath(SkArenaAlloc* alloc, SkScalerContext* scalerContext) {
   *     if (!this->setPathHasBeenCalled()) {
   *         scalerContext->getPath(*this, alloc);
   *         SkASSERT(this->setPathHasBeenCalled());
   *         return this->path() != nullptr;
   *     }
   *
   *     return false;
   * }
   * ```
   */
  public fun setPath(
    alloc: SkArenaAlloc?,
    path: SkPath?,
    hairline: Boolean,
    modified: Boolean,
  ): Boolean {
    TODO("Implement setPath")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkGlyph::setPath(SkArenaAlloc* alloc, const SkPath* path, bool hairline, bool modified) {
   *     if (!this->setPathHasBeenCalled()) {
   *         this->installPath(alloc, path, hairline, modified);
   *         return this->path() != nullptr;
   *     }
   *     return false;
   * }
   * ```
   */
  public fun setPathHasBeenCalled(): Boolean {
    TODO("Implement setPathHasBeenCalled")
  }

  /**
   * C++ original:
   * ```cpp
   * bool setPathHasBeenCalled() const { return fPathData != nullptr; }
   * ```
   */
  public fun path(): SkPath {
    TODO("Implement path")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkPath* SkGlyph::path() const {
   *     // setPath must have been called previously.
   *     SkASSERT(this->setPathHasBeenCalled());
   *     if (fPathData->fHasPath) {
   *         return &fPathData->fPath;
   *     }
   *     return nullptr;
   * }
   * ```
   */
  public fun pathIsHairline(): Boolean {
    TODO("Implement pathIsHairline")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkGlyph::pathIsHairline() const {
   *     // setPath must have been called previously.
   *     SkASSERT(this->setPathHasBeenCalled());
   *     return fPathData->fHairline;
   * }
   * ```
   */
  public fun pathIsModified(): Boolean {
    TODO("Implement pathIsModified")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkGlyph::pathIsModified() const {
   *     // setPath must have been called previously.
   *     SkASSERT(this->setPathHasBeenCalled());
   *     return fPathData->fModified;
   * }
   * ```
   */
  public fun setDrawable(alloc: SkArenaAlloc?, scalerContext: SkScalerContext?): Boolean {
    TODO("Implement setDrawable")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkGlyph::setDrawable(SkArenaAlloc* alloc, SkScalerContext* scalerContext) {
   *     if (!this->setDrawableHasBeenCalled()) {
   *         sk_sp<SkDrawable> drawable = scalerContext->getDrawable(*this);
   *         this->installDrawable(alloc, std::move(drawable));
   *         return this->drawable() != nullptr;
   *     }
   *     return false;
   * }
   * ```
   */
  public fun setDrawable(alloc: SkArenaAlloc?, drawable: SkSp<SkDrawable>): Boolean {
    TODO("Implement setDrawable")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkGlyph::setDrawable(SkArenaAlloc* alloc, sk_sp<SkDrawable> drawable) {
   *     if (!this->setDrawableHasBeenCalled()) {
   *         this->installDrawable(alloc, std::move(drawable));
   *         return this->drawable() != nullptr;
   *     }
   *     return false;
   * }
   * ```
   */
  public fun setDrawableHasBeenCalled(): Boolean {
    TODO("Implement setDrawableHasBeenCalled")
  }

  /**
   * C++ original:
   * ```cpp
   * bool setDrawableHasBeenCalled() const { return fDrawableData != nullptr; }
   * ```
   */
  public fun drawable(): SkDrawable {
    TODO("Implement drawable")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDrawable* SkGlyph::drawable() const {
   *     // setDrawable must have been called previously.
   *     SkASSERT(this->setDrawableHasBeenCalled());
   *     if (fDrawableData->fHasDrawable) {
   *         return fDrawableData->fDrawable.get();
   *     }
   *     return nullptr;
   * }
   * ```
   */
  public fun isColor(): Boolean {
    TODO("Implement isColor")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isColor() const { return fMaskFormat == SkMask::kARGB32_Format; }
   * ```
   */
  public fun maskFormat(): SkMask.Format {
    TODO("Implement maskFormat")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMask::Format maskFormat() const { return fMaskFormat; }
   * ```
   */
  public fun formatAlignment(): ULong {
    TODO("Implement formatAlignment")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkGlyph::formatAlignment() const {
   *     return format_alignment(this->maskFormat());
   * }
   * ```
   */
  public fun maxDimension(): Int {
    TODO("Implement maxDimension")
  }

  /**
   * C++ original:
   * ```cpp
   * int maxDimension() const { return std::max(fWidth, fHeight); }
   * ```
   */
  public fun iRect(): SkIRect {
    TODO("Implement iRect")
  }

  /**
   * C++ original:
   * ```cpp
   * SkIRect iRect() const { return SkIRect::MakeXYWH(fLeft, fTop, fWidth, fHeight); }
   * ```
   */
  public fun rect(): SkRect {
    TODO("Implement rect")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect rect()   const { return SkRect::MakeXYWH(fLeft, fTop, fWidth, fHeight);  }
   * ```
   */
  public fun glyphRect(): SkGlyphRect {
    TODO("Implement glyphRect")
  }

  /**
   * C++ original:
   * ```cpp
   * SkGlyphRect glyphRect() const {
   *         return SkGlyphRect(fLeft, fTop, fLeft + fWidth, fTop + fHeight);
   *     }
   * ```
   */
  public fun left(): Int {
    TODO("Implement left")
  }

  /**
   * C++ original:
   * ```cpp
   * int left()   const { return fLeft;   }
   * ```
   */
  public fun top(): Int {
    TODO("Implement top")
  }

  /**
   * C++ original:
   * ```cpp
   * int top()    const { return fTop;    }
   * ```
   */
  public fun width(): Int {
    TODO("Implement width")
  }

  /**
   * C++ original:
   * ```cpp
   * int width()  const { return fWidth;  }
   * ```
   */
  public fun height(): Int {
    TODO("Implement height")
  }

  /**
   * C++ original:
   * ```cpp
   * int height() const { return fHeight; }
   * ```
   */
  public fun isEmpty(): Boolean {
    TODO("Implement isEmpty")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isEmpty() const {
   *         return fWidth == 0 || fHeight == 0;
   *     }
   * ```
   */
  public fun imageTooLarge(): Boolean {
    TODO("Implement imageTooLarge")
  }

  /**
   * C++ original:
   * ```cpp
   * bool imageTooLarge() const { return fWidth >= kMaxGlyphWidth; }
   * ```
   */
  public fun extraBits(): UShort {
    TODO("Implement extraBits")
  }

  /**
   * C++ original:
   * ```cpp
   * uint16_t extraBits() const { return fScalerContextBits; }
   * ```
   */
  public fun ensureIntercepts(
    bounds: Array<SkScalar>,
    scale: SkScalar,
    xPos: SkScalar,
    array: SkScalar?,
    count: Int?,
    alloc: SkArenaAlloc?,
  ) {
    TODO("Implement ensureIntercepts")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkGlyph::ensureIntercepts(const SkScalar* bounds, SkScalar scale, SkScalar xPos,
   *                                SkScalar* array, int* count, SkArenaAlloc* alloc) {
   *
   *     auto offsetResults = [scale, xPos](
   *             const SkGlyph::Intercept* intercept,SkScalar* array, int* count) {
   *         if (array) {
   *             array += *count;
   *             for (int index = 0; index < 2; index++) {
   *                 *array++ = intercept->fInterval[index] * scale + xPos;
   *             }
   *         }
   *         *count += 2;
   *     };
   *
   *     const SkGlyph::Intercept* match =
   *             [this](const SkScalar bounds[2]) -> const SkGlyph::Intercept* {
   *                 if (fPathData == nullptr) {
   *                     return nullptr;
   *                 }
   *                 const SkGlyph::Intercept* intercept = fPathData->fIntercept;
   *                 while (intercept != nullptr) {
   *                     if (bounds[0] == intercept->fBounds[0] && bounds[1] == intercept->fBounds[1]) {
   *                         return intercept;
   *                     }
   *                     intercept = intercept->fNext;
   *                 }
   *                 return nullptr;
   *             }(bounds);
   *
   *     if (match != nullptr) {
   *         if (match->fInterval[0] < match->fInterval[1]) {
   *             offsetResults(match, array, count);
   *         }
   *         return;
   *     }
   *
   *     SkGlyph::Intercept* intercept = alloc->make<SkGlyph::Intercept>();
   *     intercept->fNext = fPathData->fIntercept;
   *     intercept->fBounds[0] = bounds[0];
   *     intercept->fBounds[1] = bounds[1];
   *     intercept->fInterval[0] = SK_ScalarMax;
   *     intercept->fInterval[1] = SK_ScalarMin;
   *     fPathData->fIntercept = intercept;
   *     const SkPath* path = &(fPathData->fPath);
   *     const SkRect& pathBounds = path->getBounds();
   *     if (pathBounds.fBottom < bounds[0] || bounds[1] < pathBounds.fTop) {
   *         return;
   *     }
   *
   *     std::tie(intercept->fInterval[0], intercept->fInterval[1])
   *             = calculate_path_gap(bounds[0], bounds[1], *path);
   *
   *     if (intercept->fInterval[0] >= intercept->fInterval[1]) {
   *         intercept->fInterval[0] = SK_ScalarMax;
   *         intercept->fInterval[1] = SK_ScalarMin;
   *         return;
   *     }
   *     offsetResults(intercept, array, count);
   * }
   * ```
   */
  public fun setImage(image: Unit?) {
    TODO("Implement setImage")
  }

  /**
   * C++ original:
   * ```cpp
   * void setImage(void* image) { fImage = image; }
   * ```
   */
  public fun flattenMetrics(buffer: SkWriteBuffer) {
    TODO("Implement flattenMetrics")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkGlyph::flattenMetrics(SkWriteBuffer& buffer) const {
   *     buffer.writeUInt(fID.value());
   *     buffer.writePoint({fAdvanceX, fAdvanceY});
   *     buffer.writeUInt(fWidth << 16 | fHeight);
   *     // Note: << has undefined behavior for negative values, so convert everything to the bit
   *     // values of uint16_t. Using the cast keeps the signed values fLeft and fTop from sign
   *     // extending.
   *     const uint32_t left = static_cast<uint16_t>(fLeft);
   *     const uint32_t top = static_cast<uint16_t>(fTop);
   *     buffer.writeUInt(left << 16 | top);
   *     buffer.writeUInt(SkTo<uint32_t>(fMaskFormat));
   * }
   * ```
   */
  public fun flattenImage(buffer: SkWriteBuffer) {
    TODO("Implement flattenImage")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkGlyph::flattenImage(SkWriteBuffer& buffer) const {
   *     SkASSERT(this->setImageHasBeenCalled());
   *
   *     // If the glyph is empty or too big, then no image data is sent.
   *     if (!this->isEmpty() && SkGlyphDigest::FitsInAtlas(*this)) {
   *         buffer.writeByteArray(this->image(), this->imageSize());
   *     }
   * }
   * ```
   */
  public fun addImageFromBuffer(buffer: SkReadBuffer, alloc: SkArenaAlloc?): ULong {
    TODO("Implement addImageFromBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkGlyph::addImageFromBuffer(SkReadBuffer& buffer, SkArenaAlloc* alloc) {
   *     SkASSERT(buffer.isValid());
   *
   *     // If the glyph is empty or too big, then no image data is received.
   *     if (this->isEmpty() || !SkGlyphDigest::FitsInAtlas(*this)) {
   *         return 0;
   *     }
   *
   *     size_t memoryIncrease = 0;
   *
   *     void* imageData = alloc->makeBytesAlignedTo(this->imageSize(), this->formatAlignment());
   *     buffer.readByteArray(imageData, this->imageSize());
   *     if (buffer.isValid()) {
   *         this->installImage(imageData);
   *         memoryIncrease += this->imageSize();
   *     }
   *
   *     return memoryIncrease;
   * }
   * ```
   */
  public fun flattenPath(buffer: SkWriteBuffer) {
    TODO("Implement flattenPath")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkGlyph::flattenPath(SkWriteBuffer& buffer) const {
   *     SkASSERT(this->setPathHasBeenCalled());
   *
   *     const bool hasPath = this->path() != nullptr;
   *     buffer.writeBool(hasPath);
   *     if (hasPath) {
   *         buffer.writeBool(this->pathIsHairline());
   *         buffer.writeBool(this->pathIsModified());
   *         buffer.writePath(*this->path());
   *     }
   * }
   * ```
   */
  public fun addPathFromBuffer(buffer: SkReadBuffer, alloc: SkArenaAlloc?): ULong {
    TODO("Implement addPathFromBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkGlyph::addPathFromBuffer(SkReadBuffer& buffer, SkArenaAlloc* alloc) {
   *     SkASSERT(buffer.isValid());
   *
   *     size_t memoryIncrease = 0;
   *     const bool hasPath = buffer.readBool();
   *     // Check if the buffer is invalid, so as to not make a logical decision on invalid data.
   *     if (!buffer.isValid()) {
   *         return 0;
   *     }
   *     if (hasPath) {
   *         const bool pathIsHairline = buffer.readBool();
   *         const bool pathIsModified = buffer.readBool();
   *         if (auto path = buffer.readPath()) {
   *             if (this->setPath(alloc, &path.value(), pathIsHairline, pathIsModified)) {
   *                 memoryIncrease += path->approximateBytesUsed();
   *             }
   *         }
   *     } else {
   *         this->setPath(alloc, nullptr, false, false);
   *     }
   *
   *     return memoryIncrease;
   * }
   * ```
   */
  public fun flattenDrawable(buffer: SkWriteBuffer) {
    TODO("Implement flattenDrawable")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkGlyph::flattenDrawable(SkWriteBuffer& buffer) const {
   *     SkASSERT(this->setDrawableHasBeenCalled());
   *
   *     if (this->isEmpty() || this->drawable() == nullptr) {
   *         SkPictureBackedGlyphDrawable::FlattenDrawable(buffer, nullptr);
   *         return;
   *     }
   *
   *     SkPictureBackedGlyphDrawable::FlattenDrawable(buffer, this->drawable());
   * }
   * ```
   */
  public fun addDrawableFromBuffer(buffer: SkReadBuffer, alloc: SkArenaAlloc?): ULong {
    TODO("Implement addDrawableFromBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkGlyph::addDrawableFromBuffer(SkReadBuffer& buffer, SkArenaAlloc* alloc) {
   *     SkASSERT(buffer.isValid());
   *
   *     sk_sp<SkDrawable> drawable = SkPictureBackedGlyphDrawable::MakeFromBuffer(buffer);
   *     if (!buffer.isValid()) {
   *         return 0;
   *     }
   *
   *     if (this->setDrawable(alloc, std::move(drawable))) {
   *         return this->drawable()->approximateBytesUsed();
   *     }
   *
   *     return 0;
   * }
   * ```
   */
  private fun allocImage(alloc: SkArenaAlloc?): ULong {
    TODO("Implement allocImage")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkGlyph::allocImage(SkArenaAlloc* alloc) {
   *     SkASSERT(!this->isEmpty());
   *     auto size = this->imageSize();
   *     fImage = alloc->makeBytesAlignedTo(size, this->formatAlignment());
   *
   *     return size;
   * }
   * ```
   */
  private fun installImage(imageData: Unit?) {
    TODO("Implement installImage")
  }

  /**
   * C++ original:
   * ```cpp
   * void installImage(void* imageData) {
   *         SkASSERT(!this->setImageHasBeenCalled());
   *         fImage = imageData;
   *     }
   * ```
   */
  private fun installPath(
    alloc: SkArenaAlloc?,
    path: SkPath?,
    hairline: Boolean,
    modified: Boolean,
  ) {
    TODO("Implement installPath")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkGlyph::installPath(SkArenaAlloc* alloc, const SkPath* path, bool hairline, bool modified) {
   *     SkASSERT(fPathData == nullptr);
   *     SkASSERT(!this->setPathHasBeenCalled());
   *     fPathData = alloc->make<SkGlyph::PathData>();
   *     if (path != nullptr) {
   *         fPathData->fPath = *path;
   *         fPathData->fPath.updateBoundsCache();
   *         fPathData->fPath.getGenerationID();
   *         fPathData->fHasPath = true;
   *         fPathData->fHairline = hairline;
   *         fPathData->fModified = modified;
   *     }
   * }
   * ```
   */
  private fun installDrawable(alloc: SkArenaAlloc?, drawable: SkSp<SkDrawable>) {
    TODO("Implement installDrawable")
  }

  public data class Intercept public constructor(
    public var fNext: Intercept?,
    public var fBounds: Array<SkScalar>,
    public var fInterval: Array<SkScalar>,
  )

  public data class PathData public constructor(
    public var fIntercept: Intercept?,
    public var fPath: SkPath,
    public var fHasPath: Boolean,
    public var fHairline: Boolean,
    public var fModified: Boolean,
  )

  public data class DrawableData public constructor(
    public var fIntercept: Intercept?,
    public var fDrawable: SkSp<SkDrawable>,
    public var fHasDrawable: Boolean,
  )

  public companion object {
    private val kMaxGlyphWidth: UShort = TODO("Initialize kMaxGlyphWidth")

    /**
     * C++ original:
     * ```cpp
     * std::optional<SkGlyph> SkGlyph::MakeFromBuffer(SkReadBuffer& buffer) {
     *     SkASSERT(buffer.isValid());
     *     const SkPackedGlyphID packedID{buffer.readUInt()};
     *     const SkVector advance = buffer.readPoint();
     *     const uint32_t dimensions = buffer.readUInt();
     *     const uint32_t leftTop = buffer.readUInt();
     *     const SkMask::Format format = SkTo<SkMask::Format>(buffer.readUInt());
     *
     *     if (!buffer.validate(SkMask::IsValidFormat(format))) {
     *         return std::nullopt;
     *     }
     *
     *     SkGlyph glyph{packedID};
     *     glyph.fAdvanceX = advance.x();
     *     glyph.fAdvanceY = advance.y();
     *     glyph.fWidth = dimensions >> 16;
     *     glyph.fHeight = dimensions & 0xffffu;
     *     glyph.fLeft = leftTop >> 16;
     *     glyph.fTop = leftTop & 0xffffu;
     *     glyph.fMaskFormat = format;
     *     SkDEBUGCODE(glyph.fAdvancesBoundsFormatAndInitialPathDone = true;)
     *     return glyph;
     * }
     * ```
     */
    public fun makeFromBuffer(buffer: SkReadBuffer): Int {
      TODO("Implement makeFromBuffer")
    }
  }
}
