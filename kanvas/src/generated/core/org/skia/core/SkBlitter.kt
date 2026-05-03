package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.ShortArray
import kotlin.ULong
import org.skia.foundation.SkAlpha
import org.skia.foundation.SkMask
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPixmap
import org.skia.foundation.SkRegion
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.foundation.SkSurfaceProps
import org.skia.foundation.U8CPU
import org.skia.math.SkIRect
import org.skia.math.SkMatrix
import org.skia.math.SkRect
import org.skia.memory.SkArenaAlloc

public typealias SkA8BlitterINHERITED = SkBlitter

public typealias SkRasterPipelineBlitterINHERITED = SkBlitter

/**
 * C++ original:
 * ```cpp
 * class SkBlitter {
 * public:
 *     virtual ~SkBlitter();
 *     SkBlitter() = default;
 *     SkBlitter(const SkBlitter&) = delete;
 *     SkBlitter(SkBlitter&&) = delete;
 *     SkBlitter& operator=(const SkBlitter&) = delete;
 *     SkBlitter& operator=(SkBlitter&&) = delete;
 *
 *     /// Blit a horizontal run of one or more pixels.
 *     virtual void blitH(int x, int y, int width) = 0;
 *
 *     /// Blit a horizontal run of antialiased pixels; runs[] is a *sparse*
 *     /// zero-terminated run-length encoding of spans of constant alpha values.
 *     /// The runs[] and antialias[] work together to represent long runs of pixels with the same
 *     /// alphas. The runs[] contains the number of pixels with the same alpha, and antialias[]
 *     /// contain the coverage value for that number of pixels. The runs[] (and antialias[]) are
 *     /// encoded in a clever way. The runs array is zero terminated, and has enough entries for
 *     /// each pixel plus one, in most cases some of the entries will not contain valid data. An entry
 *     /// in the runs array contains the number of pixels (np) that have the same alpha value. The
 *     /// next np value is found np entries away. For example, if runs[0] = 7, then the next valid
 *     /// entry will by at runs[7]. The runs array and antialias[] are coupled by index. So, if the
 *     /// np entry is at runs[45] = 12 then the alpha value can be found at antialias[45] = 0x88.
 *     /// This would mean to use an alpha value of 0x88 for the next 12 pixels starting at pixel 45.
 *     virtual void blitAntiH(int x, int y, const SkAlpha antialias[], const int16_t runs[]) = 0;
 *
 *     /// Blit a vertical run of pixels with a constant alpha value.
 *     virtual void blitV(int x, int y, int height, SkAlpha alpha);
 *
 *     /// Blit a solid rectangle one or more pixels wide.
 *     virtual void blitRect(int x, int y, int width, int height);
 *
 *     /** Blit a rectangle with one alpha-blended column on the left,
 *         width (zero or more) opaque pixels, and one alpha-blended column
 *         on the right.
 *         The result will always be at least two pixels wide.
 *     */
 *     virtual void blitAntiRect(int x, int y, int width, int height,
 *                               SkAlpha leftAlpha, SkAlpha rightAlpha);
 *
 *     // Blit a rect in AA with size at least 3 x 3 (small rect has too many edge cases...)
 *     void blitFatAntiRect(const SkRect& rect);
 *
 *     /// Blit a pattern of pixels defined by a rectangle-clipped mask;
 *     /// typically used for text.
 *     virtual void blitMask(const SkMask&, const SkIRect& clip);
 *
 *     // (x, y), (x + 1, y)
 *     virtual void blitAntiH2(int x, int y, U8CPU a0, U8CPU a1) {
 *         int16_t runs[3];
 *         uint8_t aa[2];
 *
 *         runs[0] = 1;
 *         runs[1] = 1;
 *         runs[2] = 0;
 *         aa[0] = SkToU8(a0);
 *         aa[1] = SkToU8(a1);
 *         this->blitAntiH(x, y, aa, runs);
 *     }
 *
 *     // (x, y), (x, y + 1)
 *     virtual void blitAntiV2(int x, int y, U8CPU a0, U8CPU a1) {
 *         int16_t runs[2];
 *         uint8_t aa[1];
 *
 *         runs[0] = 1;
 *         runs[1] = 0;
 *         aa[0] = SkToU8(a0);
 *         this->blitAntiH(x, y, aa, runs);
 *         // reset in case the clipping blitter modified runs
 *         runs[0] = 1;
 *         runs[1] = 0;
 *         aa[0] = SkToU8(a1);
 *         this->blitAntiH(x, y + 1, aa, runs);
 *     }
 *
 *     /**
 *      * Special methods for blitters that can blit more than one row at a time.
 *      * This function returns the number of rows that this blitter could optimally
 *      * process at a time. It is still required to support blitting one scanline
 *      * at a time.
 *      */
 *     virtual int requestRowsPreserved() const { return 1; }
 *
 *
 *     struct DirectBlit {
 *         SkPixmap pm;
 *         uint64_t value; // low bits match pixmap's bitdepth
 *     };
 *     virtual std::optional<DirectBlit> canDirectBlit() { return {}; }
 *
 *     /**
 *      * This function allocates memory for the blitter that the blitter then owns.
 *      * The memory can be used by the calling function at will, but it will be
 *      * released when the blitter's destructor is called. This function returns
 *      * nullptr if no persistent memory is needed by the blitter.
 *      */
 *     virtual void* allocBlitMemory(size_t sz) {
 *         return fBlitMemory.reset(sz, SkAutoMalloc::kReuse_OnShrink);
 *     }
 *
 *     ///@name non-virtual helpers
 *     void blitMaskRegion(const SkMask& mask, const SkRegion& clip);
 *     void blitRectRegion(const SkIRect& rect, const SkRegion& clip);
 *     void blitRegion(const SkRegion& clip);
 *     ///@}
 *
 *     /** @name Factories
 *         Return the correct blitter to use given the specified context.
 *      */
 *     static SkBlitter* Choose(const SkPixmap& dst,
 *                              const SkMatrix& ctm,
 *                              const SkPaint& paint,
 *                              SkArenaAlloc*,
 *                              SkDrawCoverage,
 *                              sk_sp<SkShader> clipShader,
 *                              const SkSurfaceProps& props,
 *                              const SkRect& devBounds);
 *
 *     static SkBlitter* ChooseSprite(const SkPixmap& dst,
 *                                    const SkPaint&,
 *                                    const SkPixmap& src,
 *                                    int left, int top,
 *                                    SkArenaAlloc*, sk_sp<SkShader> clipShader);
 *     ///@}
 *
 *     static bool UseLegacyBlitter(const SkPixmap&, const SkPaint&, const SkMatrix&);
 *
 * protected:
 *     SkAutoMalloc fBlitMemory;
 * }
 * ```
 */
public abstract class SkBlitter public constructor() {
  /**
   * C++ original:
   * ```cpp
   * SkAutoMalloc fBlitMemory
   * ```
   */
  protected var fBlitMemory: SkAutoMalloc = TODO("Initialize fBlitMemory")

  /**
   * C++ original:
   * ```cpp
   * SkBlitter() = default
   * ```
   */
  public constructor(param0: SkBlitter) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkBlitter& operator=(const SkBlitter&) = delete
   * ```
   */
  public fun assign(param0: SkBlitter) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * SkBlitter& operator=(SkBlitter&&) = delete
   * ```
   */
  public abstract fun blitH(
    x: Int,
    y: Int,
    width: Int,
  )

  /**
   * C++ original:
   * ```cpp
   * virtual void blitH(int x, int y, int width) = 0
   * ```
   */
  public abstract fun blitAntiH(
    x: Int,
    y: Int,
    antialias: Array<SkAlpha>,
    runs: ShortArray,
  )

  /**
   * C++ original:
   * ```cpp
   * virtual void blitAntiH(int x, int y, const SkAlpha antialias[], const int16_t runs[]) = 0
   * ```
   */
  public open fun blitV(
    x: Int,
    y: Int,
    height: Int,
    alpha: SkAlpha,
  ) {
    TODO("Implement blitV")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBlitter::blitV(int x, int y, int height, SkAlpha alpha) {
   *     if (alpha == 255) {
   *         this->blitRect(x, y, 1, height);
   *     } else {
   *         int16_t runs[2];
   *         runs[0] = 1;
   *         runs[1] = 0;
   *
   *         while (--height >= 0) {
   *             this->blitAntiH(x, y++, &alpha, runs);
   *         }
   *     }
   * }
   * ```
   */
  public open fun blitRect(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
  ) {
    TODO("Implement blitRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBlitter::blitRect(int x, int y, int width, int height) {
   *     SkASSERT(width > 0);
   *     while (--height >= 0) {
   *         this->blitH(x, y++, width);
   *     }
   * }
   * ```
   */
  public open fun blitAntiRect(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    leftAlpha: SkAlpha,
    rightAlpha: SkAlpha,
  ) {
    TODO("Implement blitAntiRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBlitter::blitAntiRect(int x, int y, int width, int height,
   *                              SkAlpha leftAlpha, SkAlpha rightAlpha) {
   *     if (leftAlpha > 0) { // we may send in x = -1 with leftAlpha = 0
   *         this->blitV(x, y, height, leftAlpha);
   *     }
   *     x++;
   *     if (width > 0) {
   *         this->blitRect(x, y, width, height);
   *         x += width;
   *     }
   *     if (rightAlpha > 0) {
   *         this->blitV(x, y, height, rightAlpha);
   *     }
   * }
   * ```
   */
  public fun blitFatAntiRect(rect: SkRect) {
    TODO("Implement blitFatAntiRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBlitter::blitFatAntiRect(const SkRect& rect) {
   *     SkIRect bounds = rect.roundOut();
   *     SkASSERT(bounds.width() >= 3);
   *
   *     // skbug.com/40039068
   *     // To ensure consistency of the threaded backend (a rect that's considered fat in the init-once
   *     // phase must also be considered fat in the draw phase), we have to deal with rects with small
   *     // heights because the horizontal tiling in the threaded backend may change the height.
   *     //
   *     // This also implies that we cannot do vertical tiling unless we can blit any rect (not just the
   *     // fat one.)
   *     if (bounds.height() == 0) {
   *         return;
   *     }
   *
   *     int         runSize = bounds.width() + 1; // +1 so we can set runs[bounds.width()] = 0
   *     void*       storage = this->allocBlitMemory(runSize * (sizeof(int16_t) + sizeof(SkAlpha)));
   *     int16_t*    runs    = reinterpret_cast<int16_t*>(storage);
   *     SkAlpha*    alphas  = reinterpret_cast<SkAlpha*>(runs + runSize);
   *
   *     runs[0] = 1;
   *     runs[1] = bounds.width() - 2;
   *     runs[bounds.width() - 1] = 1;
   *     runs[bounds.width()]  = 0;
   *
   *     SkScalar partialL = bounds.fLeft + 1 - rect.fLeft;
   *     SkScalar partialR = rect.fRight - (bounds.fRight - 1);
   *     SkScalar partialT = bounds.fTop + 1 - rect.fTop;
   *     SkScalar partialB = rect.fBottom - (bounds.fBottom - 1);
   *
   *     if (bounds.height() == 1) {
   *         partialT = rect.fBottom - rect.fTop;
   *     }
   *
   *     alphas[0] = scalar_to_alpha(partialL * partialT);
   *     alphas[1] = scalar_to_alpha(partialT);
   *     alphas[bounds.width() - 1] = scalar_to_alpha(partialR * partialT);
   *     this->blitAntiH(bounds.fLeft, bounds.fTop, alphas, runs);
   *
   *     if (bounds.height() > 2) {
   *         this->blitAntiRect(bounds.fLeft, bounds.fTop + 1, bounds.width() - 2, bounds.height() - 2,
   *                            scalar_to_alpha(partialL), scalar_to_alpha(partialR));
   *     }
   *
   *     if (bounds.height() > 1) {
   *         alphas[0] = scalar_to_alpha(partialL * partialB);
   *         alphas[1] = scalar_to_alpha(partialB);
   *         alphas[bounds.width() - 1] = scalar_to_alpha(partialR * partialB);
   *         this->blitAntiH(bounds.fLeft, bounds.fBottom - 1, alphas, runs);
   *     }
   * }
   * ```
   */
  public open fun blitMask(mask: SkMask, clip: SkIRect) {
    TODO("Implement blitMask")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBlitter::blitMask(const SkMask& mask, const SkIRect& clip) {
   *     SkASSERT(mask.fBounds.contains(clip));
   *
   *     if (mask.fFormat == SkMask::kLCD16_Format) {
   *         return; // needs to be handled by subclass
   *     }
   *
   *     if (mask.fFormat == SkMask::kBW_Format) {
   *         int cx = clip.fLeft;
   *         int cy = clip.fTop;
   *         int maskLeft = mask.fBounds.fLeft;
   *         int maskRowBytes = mask.fRowBytes;
   *         int height = clip.height();
   *
   *         const uint8_t* bits = mask.getAddr1(cx, cy);
   *
   *         SkDEBUGCODE(const uint8_t* endOfImage =
   *             mask.fImage + (mask.fBounds.height() - 1) * maskRowBytes
   *             + ((mask.fBounds.width() + 7) >> 3));
   *
   *         if (cx == maskLeft && clip.fRight == mask.fBounds.fRight) {
   *             while (--height >= 0) {
   *                 int affectedRightBit = mask.fBounds.width() - 1;
   *                 ptrdiff_t rowBytes = (affectedRightBit >> 3) + 1;
   *                 SkASSERT(bits + rowBytes <= endOfImage);
   *                 U8CPU rightMask = generate_right_mask((affectedRightBit & 7) + 1);
   *                 bits_to_runs(this, cx, cy, bits, 0xFF, rowBytes, rightMask);
   *                 bits += maskRowBytes;
   *                 cy += 1;
   *             }
   *         } else {
   *             // Bits is calculated as the offset into the mask at the point {cx, cy} therefore, all
   *             // addressing into the bit mask is relative to that point. Since this is an address
   *             // calculated from a arbitrary bit in that byte, calculate the left most bit.
   *             int bitsLeft = cx - ((cx - maskLeft) & 7);
   *
   *             // Everything is relative to the bitsLeft.
   *             int leftEdge = cx - bitsLeft;
   *             SkASSERT(leftEdge >= 0);
   *             int rightEdge = clip.fRight - bitsLeft;
   *             SkASSERT(rightEdge > leftEdge);
   *
   *             // Calculate left byte and mask
   *             const uint8_t* leftByte = bits;
   *             U8CPU leftMask = 0xFFU >> (leftEdge & 7);
   *
   *             // Calculate right byte and mask
   *             int affectedRightBit = rightEdge - 1;
   *             const uint8_t* rightByte = bits + (affectedRightBit >> 3);
   *             U8CPU rightMask = generate_right_mask((affectedRightBit & 7) + 1);
   *
   *             // leftByte and rightByte are byte locations therefore, to get a count of bytes the
   *             // code must add one.
   *             ptrdiff_t rowBytes = rightByte - leftByte + 1;
   *
   *             while (--height >= 0) {
   *                 SkASSERT(bits + rowBytes <= endOfImage);
   *                 bits_to_runs(this, bitsLeft, cy, bits, leftMask, rowBytes, rightMask);
   *                 bits += maskRowBytes;
   *                 cy += 1;
   *             }
   *         }
   *     } else {
   *         int                         width = clip.width();
   *         AutoSTMalloc<64, int16_t> runStorage(width + 1);
   *         int16_t*                    runs = runStorage.get();
   *         const uint8_t*              aa = mask.getAddr8(clip.fLeft, clip.fTop);
   *
   *         SkOpts::memset16((uint16_t*)runs, 1, width);
   *         runs[width] = 0;
   *
   *         int height = clip.height();
   *         int y = clip.fTop;
   *         while (--height >= 0) {
   *             this->blitAntiH(clip.fLeft, y, aa, runs);
   *             aa += mask.fRowBytes;
   *             y += 1;
   *         }
   *     }
   * }
   * ```
   */
  public open fun blitAntiH2(
    x: Int,
    y: Int,
    a0: U8CPU,
    a1: U8CPU,
  ) {
    TODO("Implement blitAntiH2")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void blitAntiH2(int x, int y, U8CPU a0, U8CPU a1) {
   *         int16_t runs[3];
   *         uint8_t aa[2];
   *
   *         runs[0] = 1;
   *         runs[1] = 1;
   *         runs[2] = 0;
   *         aa[0] = SkToU8(a0);
   *         aa[1] = SkToU8(a1);
   *         this->blitAntiH(x, y, aa, runs);
   *     }
   * ```
   */
  public open fun blitAntiV2(
    x: Int,
    y: Int,
    a0: U8CPU,
    a1: U8CPU,
  ) {
    TODO("Implement blitAntiV2")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void blitAntiV2(int x, int y, U8CPU a0, U8CPU a1) {
   *         int16_t runs[2];
   *         uint8_t aa[1];
   *
   *         runs[0] = 1;
   *         runs[1] = 0;
   *         aa[0] = SkToU8(a0);
   *         this->blitAntiH(x, y, aa, runs);
   *         // reset in case the clipping blitter modified runs
   *         runs[0] = 1;
   *         runs[1] = 0;
   *         aa[0] = SkToU8(a1);
   *         this->blitAntiH(x, y + 1, aa, runs);
   *     }
   * ```
   */
  public open fun requestRowsPreserved(): Int {
    TODO("Implement requestRowsPreserved")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual int requestRowsPreserved() const { return 1; }
   * ```
   */
  public open fun canDirectBlit(): Int {
    TODO("Implement canDirectBlit")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual std::optional<DirectBlit> canDirectBlit() { return {}; }
   * ```
   */
  public open fun allocBlitMemory(sz: ULong) {
    TODO("Implement allocBlitMemory")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void* allocBlitMemory(size_t sz) {
   *         return fBlitMemory.reset(sz, SkAutoMalloc::kReuse_OnShrink);
   *     }
   * ```
   */
  public fun blitMaskRegion(mask: SkMask, clip: SkRegion) {
    TODO("Implement blitMaskRegion")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBlitter::blitMaskRegion(const SkMask& mask, const SkRegion& clip) {
   *     if (clip.quickReject(mask.fBounds)) {
   *         return;
   *     }
   *
   *     SkRegion::Cliperator clipper(clip, mask.fBounds);
   *
   *     while (!clipper.done()) {
   *         const SkIRect& cr = clipper.rect();
   *         this->blitMask(mask, cr);
   *         clipper.next();
   *     }
   * }
   * ```
   */
  public fun blitRectRegion(rect: SkIRect, clip: SkRegion) {
    TODO("Implement blitRectRegion")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBlitter::blitRectRegion(const SkIRect& rect, const SkRegion& clip) {
   *     SkRegion::Cliperator clipper(clip, rect);
   *
   *     while (!clipper.done()) {
   *         const SkIRect& cr = clipper.rect();
   *         this->blitRect(cr.fLeft, cr.fTop, cr.width(), cr.height());
   *         clipper.next();
   *     }
   * }
   * ```
   */
  public fun blitRegion(clip: SkRegion) {
    TODO("Implement blitRegion")
  }

  public data class DirectBlit public constructor(
    public var pm: SkPixmap,
    public var `value`: Int,
  )

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * SkBlitter* SkBlitter::Choose(const SkPixmap& device,
     *                              const SkMatrix& ctm,
     *                              const SkPaint& origPaint,
     *                              SkArenaAlloc* alloc,
     *                              SkDrawCoverage drawCoverage,
     *                              sk_sp<SkShader> clipShader,
     *                              const SkSurfaceProps& props,
     *                              const SkRect& devBounds) {
     *     SkASSERT(alloc);
     *
     *     if (kUnknown_SkColorType == device.colorType()) {
     *         return alloc->make<SkNullBlitter>();
     *     }
     *
     *     // We may tweak the original paint as we go.
     *     SkTCopyOnFirstWrite<SkPaint> paint(origPaint);
     *
     *     if (auto mode = paint->asBlendMode()) {
     *         // We have the most fast-paths for SrcOver, so see if we can act like SrcOver.
     *         if (mode.value() != SkBlendMode::kSrcOver) {
     *             switch (CheckFastPath(*paint, SkColorTypeIsAlwaysOpaque(device.colorType()))) {
     *                 case SkBlendFastPath::kSrcOver:
     *                     paint.writable()->setBlendMode(SkBlendMode::kSrcOver);
     *                     break;
     *                 case SkBlendFastPath::kSkipDrawing:
     *                     return alloc->make<SkNullBlitter>();
     *                 default:
     *                     break;
     *             }
     *         }
     *
     *         // A Clear blend mode will ignore the entire color pipeline, as if Src mode with 0x00000000.
     *         if (mode.value() == SkBlendMode::kClear) {
     *             SkPaint* p = paint.writable();
     *             p->setShader(nullptr);
     *             p->setColorFilter(nullptr);
     *             p->setBlendMode(SkBlendMode::kSrc);
     *             p->setColor(0x00000000);
     *         }
     *     }
     *
     *     if (paint->getColorFilter()) {
     *         SkPaintPriv::RemoveColorFilter(paint.writable(), device.colorSpace());
     *     }
     *     SkASSERT(!paint->getColorFilter());
     *
     *     if (drawCoverage == SkDrawCoverage::kYes) {
     *         if (device.colorType() == kAlpha_8_SkColorType) {
     *             SkASSERT(!paint->getShader());
     *             SkASSERT(paint->isSrcOver());
     *             return alloc->make<SkA8_Coverage_Blitter>(device, *paint);
     *         }
     *         return alloc->make<SkNullBlitter>();
     *     }
     *
     *     if (paint->isDither() && !SkPaintPriv::ShouldDither(*paint, device.colorType())) {
     *         paint.writable()->setDither(false);
     *     }
     *
     *     auto CreateSkRPBlitter = [&]() -> SkBlitter* {
     *         auto blitter = SkCreateRasterPipelineBlitter(
     *                 device, *paint, ctm, alloc, clipShader, props, devBounds);
     *         return blitter ? blitter
     *                        : alloc->make<SkNullBlitter>();
     *     };
     *
     *     // We'll end here for many interesting cases: color spaces, color filters, most color types.
     *     if (clipShader || !UseLegacyBlitter(device, *paint, ctm)) {
     *         return CreateSkRPBlitter();
     *     }
     *
     *     // Everything but legacy kN32_SkColorType should already be handled.
     *     SkASSERT(device.colorType() == kN32_SkColorType);
     *
     *     // And we should be blending with SrcOver
     *     SkASSERT(paint->asBlendMode() == SkBlendMode::kSrcOver);
     *
     *     // Legacy blitters keep their shader state on a shader context.
     *     SkShaderBase::Context* shaderContext = nullptr;
     *     if (paint->getShader()) {
     *         shaderContext = as_SB(paint->getShader())
     *                                 ->makeContext({paint->getAlpha(),
     *                                                SkShaders::MatrixRec(ctm),
     *                                                device.colorType(),
     *                                                device.colorSpace(),
     *                                                props},
     *                                               alloc);
     *
     *         // Creating the context isn't always possible... try fallbacks before giving up.
     *         if (!shaderContext) {
     *             return CreateSkRPBlitter();
     *         }
     *     }
     *
     *     if (shaderContext) {
     *         return alloc->make<SkARGB32_Shader_Blitter>(device, *paint, shaderContext);
     *     } else if (paint->getColor() == SK_ColorBLACK) {
     *         return alloc->make<SkARGB32_Black_Blitter>(device, *paint);
     *     } else if (paint->getAlpha() == 0xFF) {
     *         return alloc->make<SkARGB32_Opaque_Blitter>(device, *paint);
     *     } else {
     *         return alloc->make<SkARGB32_Blitter>(device, *paint);
     *     }
     * }
     * ```
     */
    public fun choose(
      dst: SkPixmap,
      ctm: SkMatrix,
      paint: SkPaint,
      alloc: SkArenaAlloc?,
      drawCoverage: SkDrawCoverage,
      clipShader: SkSp<SkShader>,
      props: SkSurfaceProps,
      devBounds: SkRect,
    ): SkBlitter {
      TODO("Implement choose")
    }

    /**
     * C++ original:
     * ```cpp
     * SkBlitter* SkBlitter::ChooseSprite(const SkPixmap& dst, const SkPaint& paint,
     *                                    const SkPixmap& source, int left, int top,
     *                                    SkArenaAlloc* alloc, sk_sp<SkShader> clipShader) {
     *     /*  We currently ignore antialiasing and filtertype, meaning we will take our
     *         special blitters regardless of these settings. Ignoring filtertype seems fine
     *         since by definition there is no scale in the matrix. Ignoring antialiasing is
     *         a bit of a hack, since we "could" pass in the fractional left/top for the bitmap,
     *         and respect that by blending the edges of the bitmap against the device. To support
     *         this we could either add more special blitters here, or detect antialiasing in the
     *         paint and return null if it is set, forcing the client to take the slow shader case
     *         (which does respect soft edges).
     *     */
     *     SkASSERT(alloc != nullptr);
     *
     *     // TODO: in principle SkRasterPipelineSpriteBlitter could be made to handle this.
     *     if (source.alphaType() == kUnpremul_SkAlphaType) {
     *         return nullptr;
     *     }
     *
     *     SkSpriteBlitter* blitter = nullptr;
     *
     *     if (gSkForceRasterPipelineBlitter) {
     *         // Do not use any of these optimized memory blitters
     *     } else if (0 == SkColorSpaceXformSteps(source,dst).fFlags.mask() && !clipShader) {
     *         if (!blitter && SkSpriteBlitter_Memcpy::Supports(dst, source, paint)) {
     *             blitter = alloc->make<SkSpriteBlitter_Memcpy>(source);
     *         }
     *         if (!blitter) {
     *             switch (dst.colorType()) {
     *                 case kN32_SkColorType:
     *                     blitter = SkSpriteBlitter::ChooseL32(source, paint, alloc);
     *                     break;
     *                 default:
     *                     break;
     *             }
     *         }
     *     }
     *     if (!blitter && !paint.getMaskFilter()) {
     *         blitter = alloc->make<SkRasterPipelineSpriteBlitter>(source, alloc, clipShader);
     *     }
     *
     *     if (blitter && blitter->setup(dst, left,top, paint)) {
     *         return blitter;
     *     }
     *
     *     return nullptr;
     * }
     * ```
     */
    public fun chooseSprite(
      dst: SkPixmap,
      paint: SkPaint,
      src: SkPixmap,
      left: Int,
      top: Int,
      alloc: SkArenaAlloc?,
      clipShader: SkSp<SkShader>,
    ): SkBlitter {
      TODO("Implement chooseSprite")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkBlitter::UseLegacyBlitter(const SkPixmap& device,
     *                                  const SkPaint& paint,
     *                                  const SkMatrix& matrix) {
     *     if (gSkForceRasterPipelineBlitter) {
     *         return false;
     *     }
     * #if defined(SK_FORCE_RASTER_PIPELINE_BLITTER)
     *     return false;
     * #else
     *
     *     if (paint.isDither()) {
     *         return false;
     *     }
     *
     *     const SkMaskFilterBase* mf = as_MFB(paint.getMaskFilter());
     *
     *     // The legacy blitters cannot handle any of these "complex" features (anymore).
     *     if (device.alphaType() == kUnpremul_SkAlphaType   ||
     *         !paint.isSrcOver()                            ||
     *         (mf && mf->getFormat() == SkMask::k3D_Format)) {
     *         return false;
     *     }
     *
     *     auto cs = device.colorSpace();
     *     // We check (indirectly via makeContext()) later on if the shader can handle the colorspace
     *     // in legacy mode, so here we just focus on if a single color needs raster-pipeline.
     *     if (cs && !paint.getShader()) {
     *         if (!paint.getColor4f().fitsInBytes() || !cs->isSRGB()) {
     *             return false;
     *         }
     *     }
     *
     *     // Only kN32 is handled by legacy blitters now
     *     return device.colorType() == kN32_SkColorType;
     * #endif
     * }
     * ```
     */
    public fun useLegacyBlitter(
      device: SkPixmap,
      paint: SkPaint,
      matrix: SkMatrix,
    ): Boolean {
      TODO("Implement useLegacyBlitter")
    }
  }
}

public typealias SkSpriteBlitterINHERITED = SkBlitter
