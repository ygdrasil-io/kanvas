package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import kotlin.Unit
import org.skia.core.SkIPoint16
import org.skia.foundation.SkColorType
import org.skia.foundation.SkPixmap
import org.skia.foundation.SkRefCnt
import org.skia.foundation.SkSp
import org.skia.math.SkIPoint
import org.skia.math.SkIRect

/**
 * C++ original:
 * ```cpp
 * class Plot : public SkRefCnt {
 *     SK_DECLARE_INTERNAL_LLIST_INTERFACE(Plot);
 *
 * public:
 *     Plot(int pageIndex, int plotIndex, AtlasGenerationCounter* generationCounter,
 *          int offX, int offY, int width, int height, SkColorType colorType, size_t bpp);
 *
 *     uint32_t pageIndex() const { return fPageIndex; }
 *
 *     /** plotIndex() is a unique id for the plot relative to the owning GrAtlas and page. */
 *     uint32_t plotIndex() const { return fPlotIndex; }
 *     /**
 *      * genID() is incremented when the plot is evicted due to a atlas spill. It is used to
 *      * know if a particular subimage is still present in the atlas.
 *      */
 *     uint64_t genID() const { return fGenID; }
 *     PlotLocator plotLocator() const {
 *         SkASSERT(fPlotLocator.isValid());
 *         return fPlotLocator;
 *     }
 *     SkDEBUGCODE(size_t bpp() const { return fBytesPerPixel; })
 *
 *     /**
 *      * To add data to the Plot, first call addRect to see if it's possible. If successful,
 *      * use the atlasLocator to get a pointer to the location in the atlas via dataAt() and render to
 *      * that location, or if you already have data use copySubImage().
 *      */
 *     bool addRect(int width, int height, AtlasLocator* atlasLocator);
 *     void* dataAt(const AtlasLocator& atlasLocator);
 *     void copySubImage(const AtlasLocator& atlasLocator, const void* image);
 *     // Returns a Pixmap pointing to the backing data for the locator. Optionally, the caller can
 *     // provide an inset that is applied to all four sides. This is useful for use cases that need
 *     // to leave space between items in the atlas. The pixmap will exclude the padding. The entire
 *     // Plot is cleared to zero when allocated. By passing an initialColor here, the caller can
 *     // re-clear the entire locator's rect (including any padding) to any color.
 *     SkPixmap prepForRender(const AtlasLocator&,
 *                            int padding = 0,
 *                            std::optional<SkColor> initialColor = {});
 *
 *     // TODO: Utility method for Ganesh, consider removing
 *     bool addSubImage(int width, int height, const void* image, AtlasLocator* atlasLocator);
 *
 *     /**
 *      * To manage the lifetime of a plot, we use two tokens. We use the last upload token to
 *      * know when we can 'piggy back' uploads, i.e. if the last upload hasn't been flushed to
 *      * the gpu, we don't need to issue a new upload even if we update the cpu backing store. We
 *      * use lastUse to determine when we can evict a plot from the cache, i.e. if the last use
 *      * has already flushed through the gpu then we can reuse the plot.
 *      */
 *     skgpu::Token lastUploadToken() const { return fLastUpload; }
 *     skgpu::Token lastUseToken() const { return fLastUse; }
 *     void setLastUploadToken(skgpu::Token token) { fLastUpload = token; }
 *     void setLastUseToken(skgpu::Token token) { fLastUse = token; }
 *
 *     int flushesSinceLastUsed() { return fFlushesSinceLastUse; }
 *     void resetFlushesSinceLastUsed() { fFlushesSinceLastUse = 0; }
 *     void incFlushesSinceLastUsed() { fFlushesSinceLastUse++; }
 *
 *     bool needsUpload() { return !fDirtyRect.isEmpty(); }
 *     std::pair<const void*, SkIRect> prepareForUpload();
 *     // Re-initialize Plot. The client should ensure that they process any eviction callbacks
 *     // before calling this, otherwise any cached references will point to invalid data.
 *     // If freeData is true, this will free the backing data as well. This should only be used
 *     // when we know we won't be adding to the Plot immediately afterwards.
 *     void resetRects(bool freeData);
 *
 *     void markFullIfUsed() { fIsFull = !fDirtyRect.isEmpty(); }
 *     bool isEmpty() const { return fRectanizer.percentFull() == 0; }
 *     bool hasAllocation() const { return fData != nullptr; }
 *
 *     /**
 *      * Create a clone of this plot. The cloned plot will take the place of the current plot in
 *      * the atlas
 *      */
 *     sk_sp<Plot> clone() const {
 *         return sk_sp<Plot>(new Plot(
 *             fPageIndex, fPlotIndex, fGenerationCounter, fX, fY, fWidth, fHeight, fColorType,
 *             fBytesPerPixel));
 *     }
 *
 * #ifdef SK_DEBUG
 *     void resetListPtrs() {
 *         fPrev = fNext = nullptr;
 *         fList = nullptr;
 *     }
 * #endif
 *
 * private:
 *     ~Plot() override;
 *     size_t rowBytes() const { return fWidth * fBytesPerPixel; }
 *     void* dataAt(SkIPoint atlasPoint);
 *
 *     skgpu::Token fLastUpload;
 *     skgpu::Token fLastUse;
 *     int          fFlushesSinceLastUse;
 *
 *     struct {
 *         const uint32_t fPageIndex : 16;
 *         const uint32_t fPlotIndex : 16;
 *     };
 *     AtlasGenerationCounter* const fGenerationCounter;
 *     uint64_t fGenID;
 *     PlotLocator fPlotLocator;
 *     std::byte* fData;
 *     const int fWidth;
 *     const int fHeight;
 *     const int fX;
 *     const int fY;
 *     skgpu::RectanizerSkyline fRectanizer;
 *     const SkIPoint16 fOffset;  // the offset of the plot in the backing texture
 *     const SkColorType fColorType;
 *     const size_t fBytesPerPixel;
 *     SkIRect fDirtyRect;  // area in the Plot that needs to be uploaded
 *     bool fIsFull;
 *     SkDEBUGCODE(bool fDirty;)
 * }
 * ```
 */
public abstract class Plot public constructor(
  pageIndex: Int,
  plotIndex: Int,
  generationCounter: AtlasGenerationCounter?,
  offX: Int,
  offY: Int,
  width: Int,
  height: Int,
  colorType: SkColorType,
  bpp: ULong,
) : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * skgpu::Token fLastUpload
   * ```
   */
  private var fLastUpload: Token = TODO("Initialize fLastUpload")

  /**
   * C++ original:
   * ```cpp
   * skgpu::Token fLastUse
   * ```
   */
  private var fLastUse: Token = TODO("Initialize fLastUse")

  /**
   * C++ original:
   * ```cpp
   * int          fFlushesSinceLastUse
   * ```
   */
  private var fFlushesSinceLastUse: Int = TODO("Initialize fFlushesSinceLastUse")

  /**
   * C++ original:
   * ```cpp
   * AtlasGenerationCounter* const fGenerationCounter
   * ```
   */
  private val fGenerationCounter: AtlasGenerationCounter? = TODO("Initialize fGenerationCounter")

  /**
   * C++ original:
   * ```cpp
   * uint64_t fGenID
   * ```
   */
  private var fGenID: Int = TODO("Initialize fGenID")

  /**
   * C++ original:
   * ```cpp
   * PlotLocator fPlotLocator
   * ```
   */
  private var fPlotLocator: PlotLocator = TODO("Initialize fPlotLocator")

  /**
   * C++ original:
   * ```cpp
   * std::byte* fData
   * ```
   */
  private var fData: Int? = TODO("Initialize fData")

  /**
   * C++ original:
   * ```cpp
   * const int fWidth
   * ```
   */
  private val fWidth: Int = TODO("Initialize fWidth")

  /**
   * C++ original:
   * ```cpp
   * const int fHeight
   * ```
   */
  private val fHeight: Int = TODO("Initialize fHeight")

  /**
   * C++ original:
   * ```cpp
   * const int fX
   * ```
   */
  private val fX: Int = TODO("Initialize fX")

  /**
   * C++ original:
   * ```cpp
   * const int fY
   * ```
   */
  private val fY: Int = TODO("Initialize fY")

  /**
   * C++ original:
   * ```cpp
   * skgpu::RectanizerSkyline fRectanizer
   * ```
   */
  private var fRectanizer: RectanizerSkyline = TODO("Initialize fRectanizer")

  /**
   * C++ original:
   * ```cpp
   * const SkIPoint16 fOffset
   * ```
   */
  private val fOffset: SkIPoint16 = TODO("Initialize fOffset")

  /**
   * C++ original:
   * ```cpp
   * const SkColorType fColorType
   * ```
   */
  private val fColorType: SkColorType = TODO("Initialize fColorType")

  /**
   * C++ original:
   * ```cpp
   * const size_t fBytesPerPixel
   * ```
   */
  private val fBytesPerPixel: Int = TODO("Initialize fBytesPerPixel")

  /**
   * C++ original:
   * ```cpp
   * SkIRect fDirtyRect
   * ```
   */
  private var fDirtyRect: SkIRect = TODO("Initialize fDirtyRect")

  /**
   * C++ original:
   * ```cpp
   * bool fIsFull
   * ```
   */
  private var fIsFull: Boolean = TODO("Initialize fIsFull")

  public val fPageIndex: Int = TODO("Initialize fPageIndex")

  public val fPlotIndex: Int = TODO("Initialize fPlotIndex")

  /**
   * C++ original:
   * ```cpp
   * uint32_t pageIndex() const { return fPageIndex; }
   * ```
   */
  public fun pageIndex(): Int {
    TODO("Implement pageIndex")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t plotIndex() const { return fPlotIndex; }
   * ```
   */
  public fun plotIndex(): Int {
    TODO("Implement plotIndex")
  }

  /**
   * C++ original:
   * ```cpp
   * uint64_t genID() const { return fGenID; }
   * ```
   */
  public fun genID(): Int {
    TODO("Implement genID")
  }

  /**
   * C++ original:
   * ```cpp
   * PlotLocator plotLocator() const {
   *         SkASSERT(fPlotLocator.isValid());
   *         return fPlotLocator;
   *     }
   * ```
   */
  public fun plotLocator(): PlotLocator {
    TODO("Implement plotLocator")
  }

  /**
   * C++ original:
   * ```cpp
   * bool Plot::addRect(int width, int height, AtlasLocator* atlasLocator) {
   *     SkASSERT(width <= fWidth && height <= fHeight);
   *
   *     SkIPoint16 loc;
   *     if (!fRectanizer.addRect(width, height, &loc)) {
   *         return false;
   *     }
   *
   *     auto rect = skgpu::IRect16::MakeXYWH(loc.fX, loc.fY, width, height);
   *     fDirtyRect.join({rect.fLeft, rect.fTop, rect.fRight, rect.fBottom});
   *
   *     rect.offset(fOffset.fX, fOffset.fY);
   *     atlasLocator->updateRect(rect);
   *     SkDEBUGCODE(fDirty = true;)
   *
   *     return true;
   * }
   * ```
   */
  public fun addRect(
    width: Int,
    height: Int,
    atlasLocator: AtlasLocator?,
  ): Boolean {
    TODO("Implement addRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void* Plot::dataAt(const AtlasLocator& atlasLocator) { return dataAt(atlasLocator.topLeft()); }
   * ```
   */
  public fun dataAt(atlasLocator: AtlasLocator) {
    TODO("Implement dataAt")
  }

  /**
   * C++ original:
   * ```cpp
   * void Plot::copySubImage(const AtlasLocator& al, const void* image) {
   *     const unsigned char* imagePtr = (const unsigned char*)image;
   *     unsigned char* dataPtr = (unsigned char*)this->dataAt(al);
   *     int width = al.width();
   *     int height = al.height();
   *     size_t imageRB = width * fBytesPerPixel;
   *     size_t plotRB = this->rowBytes();
   *
   *     // copy into the data buffer, swizzling as we go if this is ARGB data
   *     constexpr bool kBGRAIsNative = kN32_SkColorType == kBGRA_8888_SkColorType;
   *     if (4 == fBytesPerPixel && kBGRAIsNative) {
   *         for (int i = 0; i < height; ++i) {
   *             SkOpts::RGBA_to_BGRA((uint32_t*)dataPtr, (const uint32_t*)imagePtr, width);
   *             dataPtr += plotRB;
   *             imagePtr += imageRB;
   *         }
   *     } else {
   *         for (int i = 0; i < height; ++i) {
   *             memcpy(dataPtr, imagePtr, imageRB);
   *             dataPtr += plotRB;
   *             imagePtr += imageRB;
   *         }
   *     }
   * }
   * ```
   */
  public fun copySubImage(atlasLocator: AtlasLocator, image: Unit?) {
    TODO("Implement copySubImage")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPixmap Plot::prepForRender(const AtlasLocator& al,
   *                              int padding,
   *                              std::optional<SkColor> initialColor) {
   *     SkASSERT(padding >= 0);
   *     auto info = SkImageInfo::Make(al.width(), al.height(), fColorType, kOpaque_SkAlphaType);
   *     SkPixmap outerPM{info, this->dataAt(al.topLeft()), this->rowBytes()};
   *     if (initialColor) {
   * #if defined(SK_DEBUG)
   *         if (*initialColor == 0) {
   *             SkDebugf("Plot Data: potential redudant clear of Plot to zero.");
   *         }
   * #endif
   *         outerPM.erase(*initialColor);
   *     }
   *     SkPixmap innerPM;
   *     SkIRect rect = SkIRect::MakeSize(outerPM.dimensions()).makeInset(padding, padding);
   *     SkAssertResult(outerPM.extractSubset(&innerPM, rect));
   *     return innerPM;
   * }
   * ```
   */
  public abstract fun prepForRender(
    al: AtlasLocator,
    padding: Int,
    initialColor: Int,
  ): SkPixmap

  /**
   * C++ original:
   * ```cpp
   * bool Plot::addSubImage(int width, int height, const void* image, AtlasLocator* atlasLocator) {
   *     if (fIsFull || !this->addRect(width, height, atlasLocator)) {
   *         return false;
   *     }
   *     this->copySubImage(*atlasLocator, image);
   *
   *     return true;
   * }
   * ```
   */
  public fun addSubImage(
    width: Int,
    height: Int,
    image: Unit?,
    atlasLocator: AtlasLocator?,
  ): Boolean {
    TODO("Implement addSubImage")
  }

  /**
   * C++ original:
   * ```cpp
   * skgpu::Token lastUploadToken() const { return fLastUpload; }
   * ```
   */
  public fun lastUploadToken(): Token {
    TODO("Implement lastUploadToken")
  }

  /**
   * C++ original:
   * ```cpp
   * skgpu::Token lastUseToken() const { return fLastUse; }
   * ```
   */
  public fun lastUseToken(): Token {
    TODO("Implement lastUseToken")
  }

  /**
   * C++ original:
   * ```cpp
   * void setLastUploadToken(skgpu::Token token) { fLastUpload = token; }
   * ```
   */
  public fun setLastUploadToken(token: Token) {
    TODO("Implement setLastUploadToken")
  }

  /**
   * C++ original:
   * ```cpp
   * void setLastUseToken(skgpu::Token token) { fLastUse = token; }
   * ```
   */
  public fun setLastUseToken(token: Token) {
    TODO("Implement setLastUseToken")
  }

  /**
   * C++ original:
   * ```cpp
   * int flushesSinceLastUsed() { return fFlushesSinceLastUse; }
   * ```
   */
  public fun flushesSinceLastUsed(): Int {
    TODO("Implement flushesSinceLastUsed")
  }

  /**
   * C++ original:
   * ```cpp
   * void resetFlushesSinceLastUsed() { fFlushesSinceLastUse = 0; }
   * ```
   */
  public fun resetFlushesSinceLastUsed() {
    TODO("Implement resetFlushesSinceLastUsed")
  }

  /**
   * C++ original:
   * ```cpp
   * void incFlushesSinceLastUsed() { fFlushesSinceLastUse++; }
   * ```
   */
  public fun incFlushesSinceLastUsed() {
    TODO("Implement incFlushesSinceLastUsed")
  }

  /**
   * C++ original:
   * ```cpp
   * bool needsUpload() { return !fDirtyRect.isEmpty(); }
   * ```
   */
  public fun needsUpload(): Boolean {
    TODO("Implement needsUpload")
  }

  /**
   * C++ original:
   * ```cpp
   * std::pair<const void*, SkIRect> Plot::prepareForUpload() {
   *     // We should only be issuing uploads if we are dirty
   *     SkASSERT(fDirty);
   *     if (!fData) {
   *         return {nullptr, {}};
   *     }
   *     const std::byte* dataPtr;
   *     SkIRect offsetRect;
   *     // Clamp to 4-byte aligned boundaries
   *     unsigned int clearBits = 0x3 / fBytesPerPixel;
   *     fDirtyRect.fLeft &= ~clearBits;
   *     fDirtyRect.fRight += clearBits;
   *     fDirtyRect.fRight &= ~clearBits;
   *     SkASSERT(fDirtyRect.fRight <= fWidth);
   *     // Set up dataPtr
   *     dataPtr = fData;
   *     dataPtr += this->rowBytes() * fDirtyRect.fTop;
   *     dataPtr += fBytesPerPixel * fDirtyRect.fLeft;
   *     offsetRect = fDirtyRect.makeOffset(fOffset.fX, fOffset.fY);
   *
   *     fDirtyRect.setEmpty();
   *     fIsFull = false;
   *     SkDEBUGCODE(fDirty = false);
   *
   *     return { dataPtr, offsetRect };
   * }
   * ```
   */
  public fun prepareForUpload(): Int {
    TODO("Implement prepareForUpload")
  }

  /**
   * C++ original:
   * ```cpp
   * void Plot::resetRects(bool freeData) {
   *     fRectanizer.reset();
   *     fGenID = fGenerationCounter->next();
   *     fPlotLocator = PlotLocator(fPageIndex, fPlotIndex, fGenID);
   *     fLastUpload = Token::InvalidToken();
   *     fLastUse = Token::InvalidToken();
   *
   *     if (freeData) {
   *         sk_free(fData);
   *         fData = nullptr;
   *     } else if (fData) {
   *         // zero out the plot
   *         sk_bzero(fData, this->rowBytes() * fHeight);
   *     }
   *
   *     fDirtyRect.setEmpty();
   *     fIsFull = false;
   *     SkDEBUGCODE(fDirty = false;)
   * }
   * ```
   */
  public fun resetRects(freeData: Boolean) {
    TODO("Implement resetRects")
  }

  /**
   * C++ original:
   * ```cpp
   * void markFullIfUsed() { fIsFull = !fDirtyRect.isEmpty(); }
   * ```
   */
  public fun markFullIfUsed() {
    TODO("Implement markFullIfUsed")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isEmpty() const { return fRectanizer.percentFull() == 0; }
   * ```
   */
  public fun isEmpty(): Boolean {
    TODO("Implement isEmpty")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hasAllocation() const { return fData != nullptr; }
   * ```
   */
  public fun hasAllocation(): Boolean {
    TODO("Implement hasAllocation")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<Plot> clone() const {
   *         return sk_sp<Plot>(new Plot(
   *             fPageIndex, fPlotIndex, fGenerationCounter, fX, fY, fWidth, fHeight, fColorType,
   *             fBytesPerPixel));
   *     }
   * ```
   */
  public fun clone(): SkSp<Plot> {
    TODO("Implement clone")
  }

  /**
   * C++ original:
   * ```cpp
   * void resetListPtrs() {
   *         fPrev = fNext = nullptr;
   *         fList = nullptr;
   *     }
   * ```
   */
  public fun resetListPtrs() {
    TODO("Implement resetListPtrs")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t rowBytes() const { return fWidth * fBytesPerPixel; }
   * ```
   */
  private fun rowBytes(): Int {
    TODO("Implement rowBytes")
  }

  /**
   * C++ original:
   * ```cpp
   * void* Plot::dataAt(SkIPoint atlasPoint) {
   *     if (!fData) {
   *         // We use calloc here because our contract is that all pixel data is initially zero.
   *         // This is of particular importance when a caller uses padding with prepForRender().
   *         fData = reinterpret_cast<std::byte*>(sk_calloc_throw(this->rowBytes() * fHeight));
   *     }
   *
   *     auto localPoint = atlasPoint - SkIPoint{fOffset.fX, fOffset.fY};
   *     SkASSERT(localPoint.fX >= 0 && localPoint.fX < fWidth);
   *     SkASSERT(localPoint.fY >= 0 && localPoint.fY < fHeight);
   *
   *     size_t offset = fBytesPerPixel * (localPoint.fY * fWidth + localPoint.fX);
   *
   *     return fData + offset;
   * }
   * ```
   */
  private fun dataAt(atlasPoint: SkIPoint) {
    TODO("Implement dataAt")
  }
}
