package org.skia.core

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.UByte
import kotlin.ULong
import kotlin.UShort
import kotlin.Unit
import org.skia.foundation.SkSp
import org.skia.math.SkMatrix
import org.skia.math.SkRect
import org.skia.math.SkScalar
import org.skia.math.SkVector
import org.skia.memory.SkArenaAlloc

/**
 * C++ original:
 * ```cpp
 * class SkScalerContext {
 * public:
 *     enum Flags {
 *         kFrameAndFill_Flag        = 0x0001,
 *         kUnused                   = 0x0002,
 *         kEmbeddedBitmapText_Flag  = 0x0004,
 *         kEmbolden_Flag            = 0x0008,
 *         kSubpixelPositioning_Flag = 0x0010,
 *         kForceAutohinting_Flag    = 0x0020,  // Use auto instead of bytcode hinting if hinting.
 *
 *         // together, these two flags resulting in a two bit value which matches
 *         // up with the SkPaint::Hinting enum.
 *         kHinting_Shift            = 7, // to shift into the other flags above
 *         kHintingBit1_Flag         = 0x0080,
 *         kHintingBit2_Flag         = 0x0100,
 *
 *         // Pixel geometry information.
 *         // only meaningful if fMaskFormat is kLCD16
 *         kLCD_Vertical_Flag        = 0x0200,    // else Horizontal
 *         kLCD_BGROrder_Flag        = 0x0400,    // else RGB order
 *
 *         // Generate A8 from LCD source (for GDI and CoreGraphics).
 *         // only meaningful if fMaskFormat is kA8
 *         kGenA8FromLCD_Flag        = 0x0800, // could be 0x200 (bit meaning dependent on fMaskFormat)
 *         kLinearMetrics_Flag       = 0x1000,
 *         kBaselineSnap_Flag        = 0x2000,
 *
 *         kNeedsForegroundColor_Flag = 0x4000,
 *     };
 *
 *     // computed values
 *     enum {
 *         kHinting_Mask   = kHintingBit1_Flag | kHintingBit2_Flag,
 *     };
 *
 *     SkScalerContext(SkTypeface&, const SkScalerContextEffects&, const SkDescriptor*);
 *     virtual ~SkScalerContext();
 *
 *     SkTypeface* getTypeface() const { return &fTypeface; }
 *
 *     SkMask::Format getMaskFormat() const {
 *         return fRec.fMaskFormat;
 *     }
 *
 *     bool isSubpixel() const {
 *         return SkToBool(fRec.fFlags & kSubpixelPositioning_Flag);
 *     }
 *
 *     bool isLinearMetrics() const {
 *         return SkToBool(fRec.fFlags & kLinearMetrics_Flag);
 *     }
 *
 *     // DEPRECATED
 *     bool isVertical() const { return false; }
 *
 *     SkGlyph     makeGlyph(SkPackedGlyphID, SkArenaAlloc*);
 *     void        getImage(const SkGlyph&);
 *     void        getPath(SkGlyph&, SkArenaAlloc*);
 *     sk_sp<SkDrawable> getDrawable(SkGlyph&);
 *     void        getFontMetrics(SkFontMetrics*);
 *
 *     /** Return the size in bytes of the associated gamma lookup table
 *      */
 *     static size_t GetGammaLUTSize(SkScalar contrast, SkScalar deviceGamma,
 *                                   int* width, int* height);
 *
 *     /** Get the associated gamma lookup table. The 'data' pointer must point to pre-allocated
 *      *  memory, with size in bytes greater than or equal to the return value of getGammaLUTSize().
 *      *
 *      *  If the lookup table hasn't been initialized (e.g., it's linear), this will return false.
 *      */
 *     static bool GetGammaLUTData(SkScalar contrast, SkScalar deviceGamma, uint8_t* data);
 *
 *     static void MakeRecAndEffects(const SkFont& font, const SkPaint& paint,
 *                                   const SkSurfaceProps& surfaceProps,
 *                                   SkScalerContextFlags scalerContextFlags,
 *                                   const SkMatrix& deviceMatrix,
 *                                   SkScalerContextRec* rec,
 *                                   SkScalerContextEffects* effects);
 *
 *     // If we are creating rec and effects from a font only, then there is no device around either.
 *     static void MakeRecAndEffectsFromFont(const SkFont& font,
 *                                           SkScalerContextRec* rec,
 *                                           SkScalerContextEffects* effects) {
 *         SkPaint paint;
 *         return MakeRecAndEffects(
 *                 font, paint, SkSurfaceProps(),
 *                 SkScalerContextFlags::kNone, SkMatrix::I(), rec, effects);
 *     }
 *
 *     static std::unique_ptr<SkScalerContext> MakeEmpty(
 *             SkTypeface& typeface, const SkScalerContextEffects& effects,
 *             const SkDescriptor* desc);
 *
 *     static SkDescriptor* AutoDescriptorGivenRecAndEffects(
 *         const SkScalerContextRec& rec,
 *         const SkScalerContextEffects& effects,
 *         SkAutoDescriptor* ad);
 *
 *     static std::unique_ptr<SkDescriptor> DescriptorGivenRecAndEffects(
 *         const SkScalerContextRec& rec,
 *         const SkScalerContextEffects& effects);
 *
 *     static void DescriptorBufferGiveRec(const SkScalerContextRec& rec, void* buffer);
 *     static bool CheckBufferSizeForRec(const SkScalerContextRec& rec,
 *                                       const SkScalerContextEffects& effects,
 *                                       size_t size);
 *
 *     static SkMaskGamma::PreBlend GetMaskPreBlend(const SkScalerContextRec& rec);
 *
 *     const SkScalerContextRec& getRec() const { return fRec; }
 *
 *     SkScalerContextEffects getEffects() const {
 *         return { fPathEffect.get(), fMaskFilter.get() };
 *     }
 *
 *     /**
 *     *  Return the axis (if any) that the baseline for horizontal text should land on.
 *     *  As an example, the identity matrix will return SkAxisAlignment::kX.
 *     */
 *     SkAxisAlignment computeAxisAlignmentForHText() const;
 *
 *     static SkDescriptor* CreateDescriptorAndEffectsUsingPaint(
 *         const SkFont&, const SkPaint&, const SkSurfaceProps&,
 *         SkScalerContextFlags scalerContextFlags,
 *         const SkMatrix& deviceMatrix, SkAutoDescriptor* ad,
 *         SkScalerContextEffects* effects);
 *
 * protected:
 *     const SkScalerContextRec fRec;
 *
 *     struct GeneratedPath {
 *         SkPath path;
 *         bool modified;
 *     };
 *     struct GlyphMetrics {
 *         SkVector       advance;
 *         SkRect         bounds;
 *         SkMask::Format maskFormat;
 *         uint16_t       extraBits;
 *         bool           neverRequestPath;
 *         bool           computeFromPath;
 *         std::optional<GeneratedPath> generatedPath;
 *         GlyphMetrics(SkMask::Format format)
 *             : advance{0, 0}
 *             , bounds{0, 0, 0, 0}
 *             , maskFormat(format)
 *             , extraBits(0)
 *             , neverRequestPath(false)
 *             , computeFromPath(false)
 *             , generatedPath{std::nullopt}
 *         {}
 *     };
 *
 *     virtual GlyphMetrics generateMetrics(const SkGlyph&, SkArenaAlloc*) = 0;
 *
 *     static void GenerateMetricsFromPath(
 *         SkGlyph* glyph, const SkPath& path, SkMask::Format format,
 *         bool verticalLCD, bool a8FromLCD, bool hairline);
 *
 *     static void SaturateGlyphBounds(SkGlyph* glyph, SkRect&&);
 *     static void SaturateGlyphBounds(SkGlyph* glyph, SkIRect const &);
 *
 *     /** Generates the contents of glyph.fImage.
 *      *  When called, glyph.fImage will be pointing to a pre-allocated,
 *      *  uninitialized region of memory of size glyph.imageSize().
 *      *  This method may not change glyph.fMaskFormat.
 *      *
 *      *  Because glyph.imageSize() will determine the size of fImage,
 *      *  generateMetrics will be called before generateImage.
 *      */
 *     virtual void generateImage(const SkGlyph& glyph, void* imageBuffer) = 0;
 *     static void GenerateImageFromPath(
 *         SkMaskBuilder& dst, const SkPath& path, const SkMaskGamma::PreBlend& maskPreBlend,
 *         bool doBGR, bool verticalLCD, bool a8FromLCD, bool hairline);
 *     void generateImageFromPath(const SkGlyph& glyph, void* imageBuffer);
 *
 *     /** Return the glyph's outline, or if the glyph cannot be converted to one, return {}.
 *      *  Does not apply subpixel positioning to the path.
 *      */
 *     [[nodiscard]] virtual std::optional<GeneratedPath> generatePath(const SkGlyph&) = 0;
 *
 *     /** Returns the drawable for the glyph (if any).
 *      *
 *      *  The generated drawable will be lifetime scoped to the lifetime of this scaler context.
 *      *  This means the drawable may refer to the scaler context and associated font data.
 *      *
 *      *  The drawable does not need to be flattenable (e.g. implement getFactory and getTypeName).
 *      *  Any necessary serialization will be done with makePictureSnapshot.
 *      */
 *     virtual sk_sp<SkDrawable> generateDrawable(const SkGlyph&); // TODO: = 0
 *
 *     /** Retrieves font metrics. */
 *     virtual void generateFontMetrics(SkFontMetrics*) = 0;
 *
 * private:
 *     friend class PathText;  // For debug purposes
 *     friend class PathTextBench;  // For debug purposes
 *     friend class RandomScalerContext;  // For debug purposes
 *     friend class SkScalerContext_proxy;
 *
 *     static SkScalerContextRec PreprocessRec(const SkTypeface&,
 *                                             const SkScalerContextEffects&,
 *                                             const SkDescriptor&);
 *
 *     // In order for a SkScalerContext to be in use this typeface must exist.
 *     // The SkScalerContext does not keep a reference to this typeface, so this reference may be
 *     // a dangling reference when the SkScalerContext is destroyed.
 *     SkTypeface& fTypeface;
 *
 *     // optional objects, which may be null
 *     sk_sp<SkPathEffect> fPathEffect;
 *     sk_sp<SkMaskFilter> fMaskFilter;
 *
 *     // if this is set, we draw the image from a path, rather than
 *     // calling generateImage.
 *     const bool fGenerateImageFromPath;
 *
 *     void internalGetPath(SkGlyph&, SkArenaAlloc*, std::optional<GeneratedPath>&&);
 *     SkGlyph internalMakeGlyph(SkPackedGlyphID, SkMask::Format, SkArenaAlloc*);
 *
 * protected:
 *     // SkMaskGamma::PreBlend converts linear masks to gamma correcting masks.
 *     // Visible to subclasses so that generateImage can apply the pre-blend directly.
 *     const SkMaskGamma::PreBlend fPreBlend;
 * }
 * ```
 */
public abstract class SkScalerContext public constructor(
  typeface: SkTypeface,
  effects: SkScalerContextEffects,
  desc: SkDescriptor?,
) {
  /**
   * C++ original:
   * ```cpp
   * const SkScalerContextRec fRec
   * ```
   */
  protected val fRec: SkScalerContextRec = TODO("Initialize fRec")

  /**
   * C++ original:
   * ```cpp
   * SkTypeface& fTypeface
   * ```
   */
  private var fTypeface: SkTypeface = TODO("Initialize fTypeface")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkPathEffect> fPathEffect
   * ```
   */
  private var fPathEffect: SkSp<SkPathEffect> = TODO("Initialize fPathEffect")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkMaskFilter> fMaskFilter
   * ```
   */
  private var fMaskFilter: SkSp<SkMaskFilter> = TODO("Initialize fMaskFilter")

  /**
   * C++ original:
   * ```cpp
   * const bool fGenerateImageFromPath
   * ```
   */
  private val fGenerateImageFromPath: Boolean = TODO("Initialize fGenerateImageFromPath")

  /**
   * C++ original:
   * ```cpp
   * const SkMaskGamma::PreBlend fPreBlend
   * ```
   */
  protected val fPreBlend: SkMaskGamma.PreBlend = TODO("Initialize fPreBlend")

  /**
   * C++ original:
   * ```cpp
   * SkTypeface* getTypeface() const { return &fTypeface; }
   * ```
   */
  public fun getTypeface(): SkTypeface {
    TODO("Implement getTypeface")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMask::Format getMaskFormat() const {
   *         return fRec.fMaskFormat;
   *     }
   * ```
   */
  public fun getMaskFormat(): SkMask.Format {
    TODO("Implement getMaskFormat")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isSubpixel() const {
   *         return SkToBool(fRec.fFlags & kSubpixelPositioning_Flag);
   *     }
   * ```
   */
  public fun isSubpixel(): Boolean {
    TODO("Implement isSubpixel")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isLinearMetrics() const {
   *         return SkToBool(fRec.fFlags & kLinearMetrics_Flag);
   *     }
   * ```
   */
  public fun isLinearMetrics(): Boolean {
    TODO("Implement isLinearMetrics")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isVertical() const { return false; }
   * ```
   */
  public fun isVertical(): Boolean {
    TODO("Implement isVertical")
  }

  /**
   * C++ original:
   * ```cpp
   * SkGlyph SkScalerContext::makeGlyph(SkPackedGlyphID packedID, SkArenaAlloc* alloc) {
   *     return internalMakeGlyph(packedID, fRec.fMaskFormat, alloc);
   * }
   * ```
   */
  public fun makeGlyph(packedID: SkPackedGlyphID, alloc: SkArenaAlloc?): SkGlyph {
    TODO("Implement makeGlyph")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkScalerContext::getImage(const SkGlyph& origGlyph) {
   *     SkASSERT(origGlyph.fAdvancesBoundsFormatAndInitialPathDone);
   *
   *     const SkGlyph* unfilteredGlyph = &origGlyph;
   *     // in case we need to call generateImage on a mask-format that is different
   *     // (i.e. larger) than what our caller allocated by looking at origGlyph.
   *     SkAutoMalloc tmpGlyphImageStorage;
   *     SkGlyph tmpGlyph;
   *     SkSTArenaAlloc<sizeof(SkGlyph::PathData)> tmpGlyphPathDataStorage;
   *     if (fMaskFilter) {
   *         // need the original bounds, sans our maskfilter
   *         sk_sp<SkMaskFilter> mf = std::move(fMaskFilter);
   *         tmpGlyph = this->makeGlyph(origGlyph.getPackedID(), &tmpGlyphPathDataStorage);
   *         fMaskFilter = std::move(mf);
   *
   *         // Use the origGlyph storage for the temporary unfiltered mask if it will fit.
   *         if (tmpGlyph.fMaskFormat == origGlyph.fMaskFormat &&
   *             tmpGlyph.imageSize() <= origGlyph.imageSize())
   *         {
   *             tmpGlyph.fImage = origGlyph.fImage;
   *         } else {
   *             tmpGlyphImageStorage.reset(tmpGlyph.imageSize());
   *             tmpGlyph.fImage = tmpGlyphImageStorage.get();
   *         }
   *         unfilteredGlyph = &tmpGlyph;
   *     }
   *
   *     if (!fGenerateImageFromPath) {
   *         generateImage(*unfilteredGlyph, unfilteredGlyph->fImage);
   *     } else {
   *         SkASSERT(origGlyph.setPathHasBeenCalled());
   *         const SkPath* devPath = origGlyph.path();
   *
   *         if (!devPath) {
   *             generateImage(*unfilteredGlyph, unfilteredGlyph->fImage);
   *         } else {
   *             SkMaskBuilder mask(static_cast<uint8_t*>(unfilteredGlyph->fImage),
   *                                unfilteredGlyph->iRect(), unfilteredGlyph->rowBytes(),
   *                                unfilteredGlyph->maskFormat());
   *             SkASSERT(SkMask::kARGB32_Format != origGlyph.fMaskFormat);
   *             SkASSERT(SkMask::kARGB32_Format != mask.fFormat);
   *             const bool doBGR = SkToBool(fRec.fFlags & SkScalerContext::kLCD_BGROrder_Flag);
   *             const bool doVert = SkToBool(fRec.fFlags & SkScalerContext::kLCD_Vertical_Flag);
   *             const bool a8LCD = SkToBool(fRec.fFlags & SkScalerContext::kGenA8FromLCD_Flag);
   *             const bool hairline = origGlyph.pathIsHairline();
   *             GenerateImageFromPath(mask, *devPath, fPreBlend, doBGR, doVert, a8LCD, hairline);
   *         }
   *     }
   *
   *     if (fMaskFilter) {
   *         // k3D_Format should not be mask filtered.
   *         SkASSERT(SkMask::k3D_Format != unfilteredGlyph->fMaskFormat);
   *
   *         SkMaskBuilder srcMask;
   *         SkAutoMaskFreeImage srcMaskOwnedImage(nullptr);
   *
   *         if (as_MFB(fMaskFilter)->filterMask(&srcMask, unfilteredGlyph->mask(),
   *                                             fRec.getMatrixFrom2x2(), nullptr)) {
   *             // Filter succeeded; srcMask.fImage was allocated.
   *             srcMaskOwnedImage.reset(srcMask.image());
   *         } else if (unfilteredGlyph->fImage == tmpGlyphImageStorage.get()) {
   *             // Filter did nothing; unfiltered mask is independent of origGlyph.fImage.
   *             srcMask = SkMaskBuilder(static_cast<uint8_t*>(unfilteredGlyph->fImage),
   *                                     unfilteredGlyph->iRect(), unfilteredGlyph->rowBytes(),
   *                                     unfilteredGlyph->maskFormat());
   *         } else if (origGlyph.iRect() == unfilteredGlyph->iRect()) {
   *             // Filter did nothing; the unfiltered mask is in origGlyph.fImage and matches.
   *             return;
   *         } else {
   *             // Filter did nothing; the unfiltered mask is in origGlyph.fImage and conflicts.
   *             srcMask = SkMaskBuilder(static_cast<uint8_t*>(unfilteredGlyph->fImage),
   *                                     unfilteredGlyph->iRect(), unfilteredGlyph->rowBytes(),
   *                                     unfilteredGlyph->maskFormat());
   *             size_t imageSize = unfilteredGlyph->imageSize();
   *             tmpGlyphImageStorage.reset(imageSize);
   *             srcMask.image() = static_cast<uint8_t*>(tmpGlyphImageStorage.get());
   *             memcpy(srcMask.image(), unfilteredGlyph->fImage, imageSize);
   *         }
   *
   *         SkASSERT_RELEASE(srcMask.fFormat == origGlyph.fMaskFormat);
   *         SkMaskBuilder dstMask = SkMaskBuilder(static_cast<uint8_t*>(origGlyph.fImage),
   *                                               origGlyph.iRect(), origGlyph.rowBytes(),
   *                                               origGlyph.maskFormat());
   *         SkIRect origBounds = dstMask.fBounds;
   *
   *         // Find the intersection of src and dst while updating the fImages.
   *         if (srcMask.fBounds.fTop < dstMask.fBounds.fTop) {
   *             int32_t topDiff = dstMask.fBounds.fTop - srcMask.fBounds.fTop;
   *             srcMask.image() += srcMask.fRowBytes * topDiff;
   *             srcMask.bounds().fTop = dstMask.fBounds.fTop;
   *         }
   *         if (dstMask.fBounds.fTop < srcMask.fBounds.fTop) {
   *             int32_t topDiff = srcMask.fBounds.fTop - dstMask.fBounds.fTop;
   *             dstMask.image() += dstMask.fRowBytes * topDiff;
   *             dstMask.bounds().fTop = srcMask.fBounds.fTop;
   *         }
   *
   *         if (srcMask.fBounds.fLeft < dstMask.fBounds.fLeft) {
   *             int32_t leftDiff = dstMask.fBounds.fLeft - srcMask.fBounds.fLeft;
   *             srcMask.image() += leftDiff;
   *             srcMask.bounds().fLeft = dstMask.fBounds.fLeft;
   *         }
   *         if (dstMask.fBounds.fLeft < srcMask.fBounds.fLeft) {
   *             int32_t leftDiff = srcMask.fBounds.fLeft - dstMask.fBounds.fLeft;
   *             dstMask.image() += leftDiff;
   *             dstMask.bounds().fLeft = srcMask.fBounds.fLeft;
   *         }
   *
   *         if (srcMask.fBounds.fBottom < dstMask.fBounds.fBottom) {
   *             dstMask.bounds().fBottom = srcMask.fBounds.fBottom;
   *         }
   *         if (dstMask.fBounds.fBottom < srcMask.fBounds.fBottom) {
   *             srcMask.bounds().fBottom = dstMask.fBounds.fBottom;
   *         }
   *
   *         if (srcMask.fBounds.fRight < dstMask.fBounds.fRight) {
   *             dstMask.bounds().fRight = srcMask.fBounds.fRight;
   *         }
   *         if (dstMask.fBounds.fRight < srcMask.fBounds.fRight) {
   *             srcMask.bounds().fRight = dstMask.fBounds.fRight;
   *         }
   *
   *         SkASSERT(srcMask.fBounds == dstMask.fBounds);
   *         int width = srcMask.fBounds.width();
   *         int height = srcMask.fBounds.height();
   *         int dstRB = dstMask.fRowBytes;
   *         int srcRB = srcMask.fRowBytes;
   *
   *         const uint8_t* src = srcMask.fImage;
   *         uint8_t* dst = dstMask.image();
   *
   *         if (SkMask::k3D_Format == srcMask.fFormat) {
   *             // we have to copy 3 times as much
   *             height *= 3;
   *         }
   *
   *         // If not filling the full original glyph, clear it out first.
   *         if (dstMask.fBounds != origBounds) {
   *             sk_bzero(origGlyph.fImage, origGlyph.fHeight * origGlyph.rowBytes());
   *         }
   *
   *         while (--height >= 0) {
   *             memcpy(dst, src, width);
   *             src += srcRB;
   *             dst += dstRB;
   *         }
   *     }
   * }
   * ```
   */
  public fun getImage(origGlyph: SkGlyph) {
    TODO("Implement getImage")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkScalerContext::getPath(SkGlyph& glyph, SkArenaAlloc* alloc) {
   *     this->internalGetPath(glyph, alloc, std::nullopt);
   * }
   * ```
   */
  public fun getPath(glyph: SkGlyph, alloc: SkArenaAlloc?) {
    TODO("Implement getPath")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkDrawable> SkScalerContext::getDrawable(SkGlyph& glyph) {
   *     return this->generateDrawable(glyph);
   * }
   * ```
   */
  public fun getDrawable(glyph: SkGlyph): SkSp<SkDrawable> {
    TODO("Implement getDrawable")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkScalerContext::getFontMetrics(SkFontMetrics* fm) {
   *     SkASSERT(fm);
   *     this->generateFontMetrics(fm);
   * }
   * ```
   */
  public fun getFontMetrics(fm: SkFontMetrics?) {
    TODO("Implement getFontMetrics")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkScalerContextRec& getRec() const { return fRec; }
   * ```
   */
  public fun getRec(): SkScalerContextRec {
    TODO("Implement getRec")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalerContextEffects getEffects() const {
   *         return { fPathEffect.get(), fMaskFilter.get() };
   *     }
   * ```
   */
  public fun getEffects(): SkScalerContextEffects {
    TODO("Implement getEffects")
  }

  /**
   * C++ original:
   * ```cpp
   * SkAxisAlignment SkScalerContext::computeAxisAlignmentForHText() const {
   *     return fRec.computeAxisAlignmentForHText();
   * }
   * ```
   */
  public fun computeAxisAlignmentForHText(): SkAxisAlignment {
    TODO("Implement computeAxisAlignmentForHText")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual GlyphMetrics generateMetrics(const SkGlyph&, SkArenaAlloc*) = 0
   * ```
   */
  protected abstract fun generateMetrics(param0: SkGlyph, param1: SkArenaAlloc?): GlyphMetrics

  /**
   * C++ original:
   * ```cpp
   * virtual void generateImage(const SkGlyph& glyph, void* imageBuffer) = 0
   * ```
   */
  protected abstract fun generateImage(glyph: SkGlyph, imageBuffer: Unit?)

  /**
   * C++ original:
   * ```cpp
   * void SkScalerContext::generateImageFromPath(const SkGlyph& glyph, void* imageBuffer) {
   *     SkASSERT(glyph.setPathHasBeenCalled());
   *     const SkPath* devPath = glyph.path();
   *     SkASSERT_RELEASE(devPath);
   *     SkMaskBuilder mask(static_cast<uint8_t*>(imageBuffer),
   *                        glyph.iRect(), glyph.rowBytes(), glyph.maskFormat());
   *     SkASSERT(SkMask::kARGB32_Format != mask.fFormat);
   *     const bool doBGR = SkToBool(fRec.fFlags & SkScalerContext::kLCD_BGROrder_Flag);
   *     const bool doVert = SkToBool(fRec.fFlags & SkScalerContext::kLCD_Vertical_Flag);
   *     const bool a8LCD = SkToBool(fRec.fFlags & SkScalerContext::kGenA8FromLCD_Flag);
   *     const bool hairline = glyph.pathIsHairline();
   *     GenerateImageFromPath(mask, *devPath, fPreBlend, doBGR, doVert, a8LCD, hairline);
   * }
   * ```
   */
  protected fun generateImageFromPath(glyph: SkGlyph, imageBuffer: Unit?) {
    TODO("Implement generateImageFromPath")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual std::optional<GeneratedPath> generatePath(const SkGlyph&) = 0
   * ```
   */
  protected abstract fun generatePath(param0: SkGlyph): Int

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkDrawable> SkScalerContext::generateDrawable(const SkGlyph&) {
   *     return nullptr;
   * }
   * ```
   */
  protected open fun generateDrawable(param0: SkGlyph): SkSp<SkDrawable> {
    TODO("Implement generateDrawable")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void generateFontMetrics(SkFontMetrics*) = 0
   * ```
   */
  protected abstract fun generateFontMetrics(param0: SkFontMetrics?)

  /**
   * C++ original:
   * ```cpp
   * void SkScalerContext::internalGetPath(SkGlyph& glyph, SkArenaAlloc* alloc,
   *                                       std::optional<GeneratedPath>&& generatedPath) {
   *     SkASSERT(glyph.fAdvancesBoundsFormatAndInitialPathDone);
   *
   *     if (glyph.setPathHasBeenCalled()) {
   *         return;
   *     }
   *
   *     if (!generatedPath) {
   *         generatedPath = this->generatePath(glyph);
   *     }
   *     if (!generatedPath) {
   *         glyph.setPath(alloc, (SkPath*)nullptr, false, false);
   *         return;
   *     }
   *
   *     SkPath path = std::move(generatedPath->path);
   *     bool pathModified = std::move(generatedPath->modified);
   *
   *     if (fRec.fFlags & SkScalerContext::kSubpixelPositioning_Flag) {
   *         SkPackedGlyphID glyphID = glyph.getPackedID();
   *         SkFixed dx = glyphID.getSubXFixed();
   *         SkFixed dy = glyphID.getSubYFixed();
   *         if (dx | dy) {
   *             pathModified = true;
   *             path = path.makeOffset(SkFixedToScalar(dx), SkFixedToScalar(dy));
   *         }
   *     }
   *
   *     if (fRec.fFrameWidth < 0 && fPathEffect == nullptr) {
   *         glyph.setPath(alloc, &path, false, pathModified);
   *         return;
   *     }
   *
   *     pathModified = true; // It could still end up the same, but it's probably going to change.
   *
   *     // need the path in user-space, with only the point-size applied
   *     // so that our stroking and effects will operate the same way they
   *     // would if the user had extracted the path themself, and then
   *     // called drawPath
   *     SkMatrix matrix = fRec.getMatrixFrom2x2();
   *
   *     // We apply the inverse, so that localPath is only affected by the paint settings
   *     // and not the canvas matrix.
   *     auto inverse = matrix.invert();
   *     if (!inverse) {
   *         SkPath empty;
   *         glyph.setPath(alloc, &empty, false, pathModified);
   *         return;
   *     }
   *     auto localPath = path.makeTransform(*inverse);
   *
   *     SkStrokeRec rec(SkStrokeRec::kFill_InitStyle);
   *
   *     if (fRec.fFrameWidth >= 0) {
   *         rec.setStrokeStyle(fRec.fFrameWidth,
   *                            SkToBool(fRec.fFlags & kFrameAndFill_Flag));
   *         // glyphs are always closed contours, so cap type is ignored,
   *         // so we just pass something.
   *         rec.setStrokeParams((SkPaint::Cap)fRec.fStrokeCap,
   *                             (SkPaint::Join)fRec.fStrokeJoin,
   *                             fRec.fMiterLimit);
   *     }
   *
   *     if (fPathEffect) {
   *         SkPathBuilder builder;
   *         if (fPathEffect->filterPath(&builder, localPath, &rec, nullptr, matrix)) {
   *             localPath = builder.detach();
   *         }
   *     }
   *
   *     if (rec.needToApply()) {
   *         SkPathBuilder builder;
   *         if (rec.applyToPath(&builder, localPath)) {
   *             localPath = builder.detach();
   *         }
   *     }
   *
   *     auto devPath = localPath.makeTransform(matrix);
   *     glyph.setPath(alloc, &devPath, rec.isHairlineStyle(), pathModified);
   * }
   * ```
   */
  private fun internalGetPath(
    glyph: SkGlyph,
    alloc: SkArenaAlloc?,
    generatedPath: GeneratedPath?,
  ) {
    TODO("Implement internalGetPath")
  }

  /**
   * C++ original:
   * ```cpp
   * SkGlyph SkScalerContext::internalMakeGlyph(SkPackedGlyphID packedID, SkMask::Format format, SkArenaAlloc* alloc) {
   *     auto zeroBounds = [](SkGlyph& glyph) {
   *         glyph.fLeft     = 0;
   *         glyph.fTop      = 0;
   *         glyph.fWidth    = 0;
   *         glyph.fHeight   = 0;
   *     };
   *
   *     SkGlyph glyph{packedID};
   *     glyph.fMaskFormat = format; // subclass may return a different value
   *     GlyphMetrics mx = this->generateMetrics(glyph, alloc);
   *     SkASSERT(!mx.neverRequestPath || !mx.computeFromPath);
   *
   *     glyph.fAdvanceX = mx.advance.fX;
   *     glyph.fAdvanceY = mx.advance.fY;
   *     glyph.fMaskFormat = mx.maskFormat;
   *     glyph.fScalerContextBits = mx.extraBits;
   *
   *     if (mx.computeFromPath || (fGenerateImageFromPath && !mx.neverRequestPath)) {
   *         SkDEBUGCODE(glyph.fAdvancesBoundsFormatAndInitialPathDone = true;)
   *         this->internalGetPath(glyph, alloc, std::move(mx.generatedPath));
   *         const SkPath* devPath = glyph.path();
   *         if (devPath) {
   *             const bool doVert = SkToBool(fRec.fFlags & SkScalerContext::kLCD_Vertical_Flag);
   *             const bool a8LCD = SkToBool(fRec.fFlags & SkScalerContext::kGenA8FromLCD_Flag);
   *             const bool hairline = glyph.pathIsHairline();
   *             GenerateMetricsFromPath(&glyph, *devPath, format, doVert, a8LCD, hairline);
   *         }
   *     } else {
   *         SaturateGlyphBounds(&glyph, std::move(mx.bounds));
   *         if (mx.neverRequestPath) {
   *             glyph.setPath(alloc, nullptr, false, false);
   *         }
   *     }
   *     SkDEBUGCODE(glyph.fAdvancesBoundsFormatAndInitialPathDone = true;)
   *
   *     // if either dimension is empty, zap the image bounds of the glyph
   *     if (0 == glyph.fWidth || 0 == glyph.fHeight) {
   *         zeroBounds(glyph);
   *         return glyph;
   *     }
   *
   *     if (fMaskFilter) {
   *         // only want the bounds from the filter
   *         SkMask src(nullptr, glyph.iRect(), glyph.rowBytes(), glyph.maskFormat());
   *         SkMaskBuilder dst;
   *
   *         if (as_MFB(fMaskFilter)->filterMask(&dst, src, fRec.getMatrixFrom2x2(), nullptr)) {
   *             if (dst.fBounds.isEmpty()) {
   *                 zeroBounds(glyph);
   *                 return glyph;
   *             }
   *             SkASSERT(dst.fImage == nullptr);
   *             SaturateGlyphBounds(&glyph, dst.fBounds);
   *             glyph.fMaskFormat = dst.fFormat;
   *         }
   *     }
   *     return glyph;
   * }
   * ```
   */
  private fun internalMakeGlyph(
    packedID: SkPackedGlyphID,
    format: SkMask.Format,
    alloc: SkArenaAlloc?,
  ): SkGlyph {
    TODO("Implement internalMakeGlyph")
  }

  public data class GeneratedPath public constructor(
    public var path: SkPath,
    public var modified: Boolean,
  )

  public data class GlyphMetrics public constructor(
    public var advance: SkVector,
    public var bounds: SkRect,
    public var maskFormat: SkMask.Format,
    public var extraBits: UShort,
    public var neverRequestPath: Boolean,
    public var computeFromPath: Boolean,
    public var generatedPath: Int,
  )

  public enum class Flags {
    kFrameAndFill_Flag,
    kUnused,
    kEmbeddedBitmapText_Flag,
    kEmbolden_Flag,
    kSubpixelPositioning_Flag,
    kForceAutohinting_Flag,
    kHinting_Shift,
    kHintingBit1_Flag,
    kHintingBit2_Flag,
    kLCD_Vertical_Flag,
    kLCD_BGROrder_Flag,
    kGenA8FromLCD_Flag,
    kLinearMetrics_Flag,
    kBaselineSnap_Flag,
    kNeedsForegroundColor_Flag,
  }

  public companion object {
    public val kHintingMask: Int = TODO("Initialize kHintingMask")

    /**
     * C++ original:
     * ```cpp
     * size_t SkScalerContext::GetGammaLUTSize(SkScalar contrast, SkScalar deviceGamma,
     *                                         int* width, int* height) {
     *     SkAutoMutexExclusive ama(mask_gamma_cache_mutex());
     *     const SkMaskGamma& maskGamma = SkScalerContextRec::CachedMaskGamma(
     *             SkScalerContextRec::InternalContrastFromExternal(contrast),
     *             SkScalerContextRec::InternalGammaFromExternal(deviceGamma));
     *     maskGamma.getGammaTableDimensions(width, height);
     *     return maskGamma.getGammaTableSizeInBytes();
     * }
     * ```
     */
    public fun getGammaLUTSize(
      contrast: SkScalar,
      deviceGamma: SkScalar,
      width: Int?,
      height: Int?,
    ): ULong {
      TODO("Implement getGammaLUTSize")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkScalerContext::GetGammaLUTData(SkScalar contrast, SkScalar deviceGamma, uint8_t* data) {
     *     SkAutoMutexExclusive ama(mask_gamma_cache_mutex());
     *     const SkMaskGamma& maskGamma = SkScalerContextRec::CachedMaskGamma(
     *             SkScalerContextRec::InternalContrastFromExternal(contrast),
     *             SkScalerContextRec::InternalGammaFromExternal(deviceGamma));
     *     const uint8_t* gammaTables = maskGamma.getGammaTables();
     *     if (!gammaTables) {
     *         return false;
     *     }
     *
     *     memcpy(data, gammaTables, maskGamma.getGammaTableSizeInBytes());
     *     return true;
     * }
     * ```
     */
    public fun getGammaLUTData(
      contrast: SkScalar,
      deviceGamma: SkScalar,
      `data`: UByte?,
    ): Boolean {
      TODO("Implement getGammaLUTData")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkScalerContext::MakeRecAndEffects(const SkFont& font, const SkPaint& paint,
     *                                         const SkSurfaceProps& surfaceProps,
     *                                         SkScalerContextFlags scalerContextFlags,
     *                                         const SkMatrix& deviceMatrix,
     *                                         SkScalerContextRec* rec,
     *                                         SkScalerContextEffects* effects) {
     *     SkASSERT(!deviceMatrix.hasPerspective());
     *
     *     sk_bzero(rec, sizeof(SkScalerContextRec));
     *
     *     SkTypeface* typeface = font.getTypeface();
     *
     *     rec->fTypefaceID = typeface->uniqueID();
     *     rec->fTextSize = font.getSize();
     *     rec->fPreScaleX = font.getScaleX();
     *     rec->fPreSkewX  = font.getSkewX();
     *
     *     bool checkPost2x2 = false;
     *
     *     const SkMatrix::TypeMask mask = deviceMatrix.getType();
     *     if (mask & SkMatrix::kScale_Mask) {
     *         rec->fPost2x2[0][0] = sk_relax(deviceMatrix.getScaleX());
     *         rec->fPost2x2[1][1] = sk_relax(deviceMatrix.getScaleY());
     *         checkPost2x2 = true;
     *     } else {
     *         rec->fPost2x2[0][0] = rec->fPost2x2[1][1] = SK_Scalar1;
     *     }
     *     if (mask & SkMatrix::kAffine_Mask) {
     *         rec->fPost2x2[0][1] = sk_relax(deviceMatrix.getSkewX());
     *         rec->fPost2x2[1][0] = sk_relax(deviceMatrix.getSkewY());
     *         checkPost2x2 = true;
     *     } else {
     *         rec->fPost2x2[0][1] = rec->fPost2x2[1][0] = 0;
     *     }
     *
     *     SkPaint::Style  style = paint.getStyle();
     *     SkScalar        strokeWidth = paint.getStrokeWidth();
     *
     *     unsigned flags = 0;
     *
     *     if (font.isEmbolden()) {
     *         flags |= SkScalerContext::kEmbolden_Flag;
     *     }
     *
     *     if (style != SkPaint::kFill_Style && strokeWidth >= 0) {
     *         rec->fFrameWidth = strokeWidth;
     *         rec->fMiterLimit = paint.getStrokeMiter();
     *         rec->fStrokeJoin = SkToU8(paint.getStrokeJoin());
     *         rec->fStrokeCap = SkToU8(paint.getStrokeCap());
     *
     *         if (style == SkPaint::kStrokeAndFill_Style) {
     *             flags |= SkScalerContext::kFrameAndFill_Flag;
     *         }
     *     } else {
     *         rec->fFrameWidth = -1;
     *         rec->fMiterLimit = 0;
     *         rec->fStrokeJoin = 0;
     *         rec->fStrokeCap = 0;
     *     }
     *
     *     rec->fMaskFormat = compute_mask_format(font);
     *
     *     if (SkMask::kLCD16_Format == rec->fMaskFormat) {
     *         if (too_big_for_lcd(*rec, checkPost2x2)) {
     *             rec->fMaskFormat = SkMask::kA8_Format;
     *             flags |= SkScalerContext::kGenA8FromLCD_Flag;
     *         } else {
     *             SkPixelGeometry geometry = surfaceProps.pixelGeometry();
     *
     *             switch (geometry) {
     *                 case kUnknown_SkPixelGeometry:
     *                     // eeek, can't support LCD
     *                     rec->fMaskFormat = SkMask::kA8_Format;
     *                     flags |= SkScalerContext::kGenA8FromLCD_Flag;
     *                     break;
     *                 case kRGB_H_SkPixelGeometry:
     *                     // our default, do nothing.
     *                     break;
     *                 case kBGR_H_SkPixelGeometry:
     *                     flags |= SkScalerContext::kLCD_BGROrder_Flag;
     *                     break;
     *                 case kRGB_V_SkPixelGeometry:
     *                     flags |= SkScalerContext::kLCD_Vertical_Flag;
     *                     break;
     *                 case kBGR_V_SkPixelGeometry:
     *                     flags |= SkScalerContext::kLCD_Vertical_Flag;
     *                     flags |= SkScalerContext::kLCD_BGROrder_Flag;
     *                     break;
     *             }
     *         }
     *     }
     *
     *     if (font.isEmbeddedBitmaps()) {
     *         flags |= SkScalerContext::kEmbeddedBitmapText_Flag;
     *     }
     *     if (font.isSubpixel()) {
     *         flags |= SkScalerContext::kSubpixelPositioning_Flag;
     *     }
     *     if (font.isForceAutoHinting()) {
     *         flags |= SkScalerContext::kForceAutohinting_Flag;
     *     }
     *     if (font.isLinearMetrics()) {
     *         flags |= SkScalerContext::kLinearMetrics_Flag;
     *     }
     *     if (font.isBaselineSnap()) {
     *         flags |= SkScalerContext::kBaselineSnap_Flag;
     *     }
     *     if (typeface->glyphMaskNeedsCurrentColor()) {
     *         flags |= SkScalerContext::kNeedsForegroundColor_Flag;
     *         rec->fForegroundColor = paint.getColor();
     *     }
     *     rec->fFlags = SkToU16(flags);
     *
     *     // these modify fFlags, so do them after assigning fFlags
     *     rec->setHinting(font.getHinting());
     *     rec->setLuminanceColor(SkPaintPriv::ComputeLuminanceColor(paint));
     *
     *     // The paint color is always converted to the device colr space,
     *     // so the paint gamma is now always equal to the device gamma.
     *     // The math in SkMaskGamma can handle them being different,
     *     // but it requires superluminous masks when
     *     // Ex : deviceGamma(x) < paintGamma(x) and x is sufficiently large.
     *     rec->setDeviceGamma(surfaceProps.textGamma());
     *     rec->setContrast(surfaceProps.textContrast());
     *
     *     if (!SkToBool(scalerContextFlags & SkScalerContextFlags::kFakeGamma)) {
     *         rec->ignoreGamma();
     *     }
     *     if (!SkToBool(scalerContextFlags & SkScalerContextFlags::kBoostContrast)) {
     *         rec->setContrast(0);
     *     }
     *
     *     new (effects) SkScalerContextEffects{paint};
     * }
     * ```
     */
    public fun makeRecAndEffects(
      font: SkFont,
      paint: SkPaint,
      surfaceProps: SkSurfaceProps,
      scalerContextFlags: SkScalerContextFlags,
      deviceMatrix: SkMatrix,
      rec: SkScalerContextRec?,
      effects: SkScalerContextEffects?,
    ) {
      TODO("Implement makeRecAndEffects")
    }

    /**
     * C++ original:
     * ```cpp
     * static void MakeRecAndEffectsFromFont(const SkFont& font,
     *                                           SkScalerContextRec* rec,
     *                                           SkScalerContextEffects* effects) {
     *         SkPaint paint;
     *         return MakeRecAndEffects(
     *                 font, paint, SkSurfaceProps(),
     *                 SkScalerContextFlags::kNone, SkMatrix::I(), rec, effects);
     *     }
     * ```
     */
    public fun makeRecAndEffectsFromFont(
      font: SkFont,
      rec: SkScalerContextRec?,
      effects: SkScalerContextEffects?,
    ) {
      TODO("Implement makeRecAndEffectsFromFont")
    }

    /**
     * C++ original:
     * ```cpp
     * std::unique_ptr<SkScalerContext> SkScalerContext::MakeEmpty(
     *         SkTypeface& typeface, const SkScalerContextEffects& effects,
     *         const SkDescriptor* desc) {
     *     class SkScalerContext_Empty : public SkScalerContext {
     *     public:
     *         SkScalerContext_Empty(SkTypeface& typeface, const SkScalerContextEffects& effects,
     *                               const SkDescriptor* desc)
     *                 : SkScalerContext(typeface, effects, desc) {}
     *
     *     protected:
     *         GlyphMetrics generateMetrics(const SkGlyph& glyph, SkArenaAlloc*) override {
     *             return {glyph.maskFormat()};
     *         }
     *         void generateImage(const SkGlyph&, void*) override {}
     *         std::optional<GeneratedPath> generatePath(const SkGlyph& glyph) override {
     *             return {};
     *         }
     *         void generateFontMetrics(SkFontMetrics* metrics) override {
     *             if (metrics) {
     *                 sk_bzero(metrics, sizeof(*metrics));
     *             }
     *         }
     *     };
     *
     *     return std::make_unique<SkScalerContext_Empty>(typeface, effects, desc);
     * }
     * ```
     */
    public fun makeEmpty(
      typeface: SkTypeface,
      effects: SkScalerContextEffects,
      desc: SkDescriptor?,
    ): Int {
      TODO("Implement makeEmpty")
    }

    /**
     * C++ original:
     * ```cpp
     * SkDescriptor* SkScalerContext::AutoDescriptorGivenRecAndEffects(
     *     const SkScalerContextRec& rec,
     *     const SkScalerContextEffects& effects,
     *     SkAutoDescriptor* ad)
     * {
     *     SkBinaryWriteBuffer buf({});
     *
     *     ad->reset(calculate_size_and_flatten(rec, effects, &buf));
     *     generate_descriptor(rec, buf, ad->getDesc());
     *
     *     return ad->getDesc();
     * }
     * ```
     */
    public fun autoDescriptorGivenRecAndEffects(
      rec: SkScalerContextRec,
      effects: SkScalerContextEffects,
      ad: SkAutoDescriptor?,
    ): SkDescriptor {
      TODO("Implement autoDescriptorGivenRecAndEffects")
    }

    /**
     * C++ original:
     * ```cpp
     * std::unique_ptr<SkDescriptor> SkScalerContext::DescriptorGivenRecAndEffects(
     *     const SkScalerContextRec& rec,
     *     const SkScalerContextEffects& effects)
     * {
     *     SkBinaryWriteBuffer buf({});
     *
     *     auto desc = SkDescriptor::Alloc(calculate_size_and_flatten(rec, effects, &buf));
     *     generate_descriptor(rec, buf, desc.get());
     *
     *     return desc;
     * }
     * ```
     */
    public fun descriptorGivenRecAndEffects(rec: SkScalerContextRec, effects: SkScalerContextEffects): Int {
      TODO("Implement descriptorGivenRecAndEffects")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkScalerContext::DescriptorBufferGiveRec(const SkScalerContextRec& rec, void* buffer) {
     *     generate_descriptor(rec, SkBinaryWriteBuffer({}), (SkDescriptor*)buffer);
     * }
     * ```
     */
    public fun descriptorBufferGiveRec(rec: SkScalerContextRec, buffer: Unit?) {
      TODO("Implement descriptorBufferGiveRec")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkScalerContext::CheckBufferSizeForRec(const SkScalerContextRec& rec,
     *                                             const SkScalerContextEffects& effects,
     *                                             size_t size) {
     *     SkBinaryWriteBuffer buf({});
     *     return size >= calculate_size_and_flatten(rec, effects, &buf);
     * }
     * ```
     */
    public fun checkBufferSizeForRec(
      rec: SkScalerContextRec,
      effects: SkScalerContextEffects,
      size: ULong,
    ): Boolean {
      TODO("Implement checkBufferSizeForRec")
    }

    /**
     * C++ original:
     * ```cpp
     * SkMaskGamma::PreBlend SkScalerContext::GetMaskPreBlend(const SkScalerContextRec& rec) {
     *     SkAutoMutexExclusive ama(mask_gamma_cache_mutex());
     *
     *     const SkMaskGamma& maskGamma = rec.cachedMaskGamma();
     *
     *     // TODO: remove CanonicalColor when we to fix up Chrome layout tests.
     *     return maskGamma.preBlend(rec.getLuminanceColor());
     * }
     * ```
     */
    public fun getMaskPreBlend(rec: SkScalerContextRec): Int {
      TODO("Implement getMaskPreBlend")
    }

    /**
     * C++ original:
     * ```cpp
     * SkDescriptor* SkScalerContext::CreateDescriptorAndEffectsUsingPaint(
     *     const SkFont& font, const SkPaint& paint, const SkSurfaceProps& surfaceProps,
     *     SkScalerContextFlags scalerContextFlags, const SkMatrix& deviceMatrix, SkAutoDescriptor* ad,
     *     SkScalerContextEffects* effects)
     * {
     *     SkScalerContextRec rec;
     *     MakeRecAndEffects(font, paint, surfaceProps, scalerContextFlags, deviceMatrix, &rec, effects);
     *     return AutoDescriptorGivenRecAndEffects(rec, *effects, ad);
     * }
     * ```
     */
    public fun createDescriptorAndEffectsUsingPaint(
      font: SkFont,
      paint: SkPaint,
      surfaceProps: SkSurfaceProps,
      scalerContextFlags: SkScalerContextFlags,
      deviceMatrix: SkMatrix,
      ad: SkAutoDescriptor?,
      effects: SkScalerContextEffects?,
    ): SkDescriptor {
      TODO("Implement createDescriptorAndEffectsUsingPaint")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkScalerContext::GenerateMetricsFromPath(
     *     SkGlyph* glyph, const SkPath& devPath, SkMask::Format format,
     *     const bool verticalLCD, const bool a8FromLCD, const bool hairline)
     * {
     *     // Only BW, A8, and LCD16 can be produced from paths.
     *     if (glyph->fMaskFormat != SkMask::kBW_Format &&
     *         glyph->fMaskFormat != SkMask::kA8_Format &&
     *         glyph->fMaskFormat != SkMask::kLCD16_Format)
     *     {
     *         glyph->fMaskFormat = SkMask::kA8_Format;
     *     }
     *
     *     SkRect bounds = devPath.getBounds();
     *     if (!bounds.isEmpty()) {
     *         const bool fromLCD = (glyph->fMaskFormat == SkMask::kLCD16_Format) ||
     *                              (glyph->fMaskFormat == SkMask::kA8_Format && a8FromLCD);
     *
     *         const bool needExtraWidth  = (fromLCD && !verticalLCD) || hairline;
     *         const bool needExtraHeight = (fromLCD &&  verticalLCD) || hairline;
     *         if (needExtraWidth) {
     *             bounds.roundOut(&bounds);
     *             bounds.outset(1, 0);
     *         }
     *         if (needExtraHeight) {
     *             bounds.roundOut(&bounds);
     *             bounds.outset(0, 1);
     *         }
     *     }
     *     SaturateGlyphBounds(glyph, std::move(bounds));
     * }
     * ```
     */
    protected fun generateMetricsFromPath(
      glyph: SkGlyph?,
      path: SkPath,
      format: SkMask.Format,
      verticalLCD: Boolean,
      a8FromLCD: Boolean,
      hairline: Boolean,
    ) {
      TODO("Implement generateMetricsFromPath")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkScalerContext::SaturateGlyphBounds(SkGlyph* glyph, SkRect&& r) {
     *     r.roundOut(&r);
     *     glyph->fLeft    = sk_saturate_cast<int16_t>(r.fLeft);
     *     glyph->fTop     = sk_saturate_cast<int16_t>(r.fTop);
     *     glyph->fWidth   = sk_saturate_cast<uint16_t>(r.width());
     *     glyph->fHeight  = sk_saturate_cast<uint16_t>(r.height());
     * }
     * ```
     */
    protected fun saturateGlyphBounds(glyph: SkGlyph?, r: SkRect) {
      TODO("Implement saturateGlyphBounds")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkScalerContext::SaturateGlyphBounds(SkGlyph* glyph, SkIRect const & r) {
     *     glyph->fLeft    = sk_saturate_cast<int16_t>(r.fLeft);
     *     glyph->fTop     = sk_saturate_cast<int16_t>(r.fTop);
     *     glyph->fWidth   = sk_saturate_cast<uint16_t>(r.width64());
     *     glyph->fHeight  = sk_saturate_cast<uint16_t>(r.height64());
     * }
     * ```
     */
    protected fun saturateGlyphBounds(glyph: SkGlyph?, r: Any) {
      TODO("Implement saturateGlyphBounds")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkScalerContext::GenerateImageFromPath(
     *     SkMaskBuilder& dstMask, const SkPath& path, const SkMaskGamma::PreBlend& maskPreBlend,
     *     const bool doBGR, const bool verticalLCD, const bool a8FromLCD, const bool hairline)
     * {
     *     SkASSERT(dstMask.fFormat == SkMask::kBW_Format ||
     *              dstMask.fFormat == SkMask::kA8_Format ||
     *              dstMask.fFormat == SkMask::kLCD16_Format);
     *
     *     SkPaint paint;
     *     SkPath strokePath;
     *     const SkPath* pathToUse = &path;
     *
     *     int srcW = dstMask.fBounds.width();
     *     int srcH = dstMask.fBounds.height();
     *     int dstW = srcW;
     *     int dstH = srcH;
     *
     *     SkMatrix matrix;
     *     matrix.setTranslate(-SkIntToScalar(dstMask.fBounds.fLeft),
     *                         -SkIntToScalar(dstMask.fBounds.fTop));
     *
     *     paint.setStroke(hairline);
     *     paint.setAntiAlias(SkMask::kBW_Format != dstMask.fFormat);
     *
     *     const bool fromLCD = (dstMask.fFormat == SkMask::kLCD16_Format) ||
     *                          (dstMask.fFormat == SkMask::kA8_Format && a8FromLCD);
     *     const bool intermediateDst = fromLCD || dstMask.fFormat == SkMask::kBW_Format;
     *     if (fromLCD) {
     *         if (verticalLCD) {
     *             dstW = 4*dstH - 8;
     *             dstH = srcW;
     *             matrix.setAll(0, 4, -SkIntToScalar(dstMask.fBounds.fTop + 1) * 4,
     *                           1, 0, -SkIntToScalar(dstMask.fBounds.fLeft),
     *                           0, 0, 1);
     *         } else {
     *             dstW = 4*dstW - 8;
     *             matrix.setAll(4, 0, -SkIntToScalar(dstMask.fBounds.fLeft + 1) * 4,
     *                           0, 1, -SkIntToScalar(dstMask.fBounds.fTop),
     *                           0, 0, 1);
     *         }
     *
     *         // LCD hairline doesn't line up with the pixels, so do it the expensive way.
     *         SkStrokeRec rec(SkStrokeRec::kFill_InitStyle);
     *         if (hairline) {
     *             rec.setStrokeStyle(1.0f, false);
     *             rec.setStrokeParams(SkPaint::kButt_Cap, SkPaint::kRound_Join, 0.0f);
     *         }
     *
     *         SkPathBuilder builder;
     *         if (rec.needToApply() && rec.applyToPath(&builder, path)) {
     *             strokePath = builder.detach();
     *             pathToUse = &strokePath;
     *             paint.setStyle(SkPaint::kFill_Style);
     *         }
     *     }
     *
     *     SkRasterClip clip;
     *     clip.setRect(SkIRect::MakeWH(dstW, dstH));
     *
     *     const SkImageInfo info = SkImageInfo::MakeA8(dstW, dstH);
     *     SkAutoPixmapStorage dst;
     *
     *     if (intermediateDst) {
     *         if (!dst.tryAlloc(info)) {
     *             // can't allocate offscreen, so empty the mask and return
     *             sk_bzero(dstMask.image(), dstMask.computeImageSize());
     *             return;
     *         }
     *     } else {
     *         dst.reset(info, dstMask.image(), dstMask.fRowBytes);
     *     }
     *     sk_bzero(dst.writable_addr(), dst.computeByteSize());
     *
     *     skcpu::Draw draw;
     *     draw.fBlitterChooser = SkA8Blitter_Choose;
     *     draw.fDst            = dst;
     *     draw.fRC             = &clip;
     *     draw.fCTM            = &matrix;
     *     // We can save a copy if we had to use the local strokePath
     *     draw.drawPath(*pathToUse, paint, nullptr);
     *
     *     switch (dstMask.fFormat) {
     *         case SkMask::kBW_Format:
     *             packA8ToA1(dstMask, dst.addr8(0, 0), dst.rowBytes());
     *             break;
     *         case SkMask::kA8_Format:
     *             if (fromLCD) {
     *                 pack4xHToMask(dst, dstMask, maskPreBlend, doBGR, verticalLCD);
     *             } else if (maskPreBlend.isApplicable()) {
     *                 applyLUTToA8Mask(dstMask, maskPreBlend.fG);
     *             }
     *             break;
     *         case SkMask::kLCD16_Format:
     *             pack4xHToMask(dst, dstMask, maskPreBlend, doBGR, verticalLCD);
     *             break;
     *         default:
     *             break;
     *     }
     * }
     * ```
     */
    protected fun generateImageFromPath(
      dst: SkMaskBuilder,
      path: SkPath,
      maskPreBlend: SkMaskGamma.PreBlend,
      doBGR: Boolean,
      verticalLCD: Boolean,
      a8FromLCD: Boolean,
      hairline: Boolean,
    ) {
      TODO("Implement generateImageFromPath")
    }

    /**
     * C++ original:
     * ```cpp
     * SkScalerContextRec SkScalerContext::PreprocessRec(const SkTypeface& typeface,
     *                                                   const SkScalerContextEffects& effects,
     *                                                   const SkDescriptor& desc) {
     *     SkScalerContextRec rec =
     *             *static_cast<const SkScalerContextRec*>(desc.findEntry(kRec_SkDescriptorTag, nullptr));
     *
     *     // Allow the typeface to adjust the rec.
     *     typeface.onFilterRec(&rec);
     *
     *     if (effects.fMaskFilter) {
     *         // Pre-blend is not currently applied to filtered text.
     *         // The primary filter is blur, for which contrast makes no sense,
     *         // and for which the destination guess error is more visible.
     *         // Also, all existing users of blur have calibrated for linear.
     *         rec.ignorePreBlend();
     *     }
     *
     *     SkColor lumColor = rec.getLuminanceColor();
     *
     *     if (rec.fMaskFormat == SkMask::kA8_Format) {
     *         U8CPU lum = SkComputeLuminance(SkColorGetR(lumColor),
     *                                        SkColorGetG(lumColor),
     *                                        SkColorGetB(lumColor));
     *         lumColor = SkColorSetRGB(lum, lum, lum);
     *     }
     *
     *     // TODO: remove CanonicalColor when we to fix up Chrome layout tests.
     *     rec.setLuminanceColor(lumColor);
     *
     *     return rec;
     * }
     * ```
     */
    private fun preprocessRec(
      typeface: SkTypeface,
      effects: SkScalerContextEffects,
      desc: SkDescriptor,
    ): SkScalerContextRec {
      TODO("Implement preprocessRec")
    }
  }
}

public typealias SkScalerContextProxyINHERITED = SkScalerContext
