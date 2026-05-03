package org.skia.core

import kotlin.Array
import kotlin.Int
import kotlin.ShortArray
import kotlin.Unit
import org.skia.foundation.SkAlpha
import org.skia.math.SkIRect

/**
 * C++ original:
 * ```cpp
 * class SkAAClipBlitter : public SkBlitter {
 * public:
 *     SkAAClipBlitter() : fScanlineScratch(nullptr) {}
 *     ~SkAAClipBlitter() override;
 *
 *     void init(SkBlitter* blitter, const SkAAClip* aaclip) {
 *         SkASSERT(aaclip && !aaclip->isEmpty());
 *         fBlitter = blitter;
 *         fAAClip = aaclip;
 *         fAAClipBounds = aaclip->getBounds();
 *     }
 *
 *     void blitH(int x, int y, int width) override;
 *     void blitAntiH(int x, int y, const SkAlpha[], const int16_t runs[]) override;
 *     void blitV(int x, int y, int height, SkAlpha alpha) override;
 *     void blitRect(int x, int y, int width, int height) override;
 *     void blitMask(const SkMask&, const SkIRect& clip) override;
 *
 * private:
 *     SkBlitter*      fBlitter;
 *     const SkAAClip* fAAClip;
 *     SkIRect         fAAClipBounds;
 *
 *     // point into fScanlineScratch
 *     int16_t*        fRuns;
 *     SkAlpha*        fAA;
 *
 *     enum {
 *         kSize = 32 * 32
 *     };
 *     SkAutoSMalloc<kSize> fGrayMaskScratch;  // used for blitMask
 *     void* fScanlineScratch;  // enough for a mask at 32bit, or runs+aa
 *
 *     void ensureRunsAndAA();
 * }
 * ```
 */
public open class SkAAClipBlitter public constructor() : SkBlitter() {
  /**
   * C++ original:
   * ```cpp
   * SkBlitter*      fBlitter
   * ```
   */
  private var fBlitter: SkBlitter? = TODO("Initialize fBlitter")

  /**
   * C++ original:
   * ```cpp
   * const SkAAClip* fAAClip
   * ```
   */
  private val fAAClip: SkAAClip? = TODO("Initialize fAAClip")

  /**
   * C++ original:
   * ```cpp
   * SkIRect         fAAClipBounds
   * ```
   */
  private var fAAClipBounds: SkIRect = TODO("Initialize fAAClipBounds")

  /**
   * C++ original:
   * ```cpp
   * int16_t*        fRuns
   * ```
   */
  private var fRuns: Int? = TODO("Initialize fRuns")

  /**
   * C++ original:
   * ```cpp
   * SkAlpha*        fAA
   * ```
   */
  private var fAA: SkAlpha? = TODO("Initialize fAA")

  /**
   * C++ original:
   * ```cpp
   * SkAutoSMalloc<kSize> fGrayMaskScratch
   * ```
   */
  private var fGrayMaskScratch: SkAutoSMallockSize = TODO("Initialize fGrayMaskScratch")

  /**
   * C++ original:
   * ```cpp
   * void* fScanlineScratch
   * ```
   */
  private var fScanlineScratch: Unit? = TODO("Initialize fScanlineScratch")

  /**
   * C++ original:
   * ```cpp
   * void init(SkBlitter* blitter, const SkAAClip* aaclip) {
   *         SkASSERT(aaclip && !aaclip->isEmpty());
   *         fBlitter = blitter;
   *         fAAClip = aaclip;
   *         fAAClipBounds = aaclip->getBounds();
   *     }
   * ```
   */
  public fun `init`(blitter: SkBlitter?, aaclip: SkAAClip?) {
    TODO("Implement init")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkAAClipBlitter::blitH(int x, int y, int width) {
   *     SkASSERT(width > 0);
   *     SkASSERT(fAAClipBounds.contains(x, y));
   *     SkASSERT(fAAClipBounds.contains(x + width  - 1, y));
   *
   *     const uint8_t* row = fAAClip->findRow(y);
   *     int initialCount;
   *     row = fAAClip->findX(row, x, &initialCount);
   *
   *     if (initialCount >= width) {
   *         SkAlpha alpha = row[1];
   *         if (0 == alpha) {
   *             return;
   *         }
   *         if (0xFF == alpha) {
   *             fBlitter->blitH(x, y, width);
   *             return;
   *         }
   *     }
   *
   *     this->ensureRunsAndAA();
   *     expandToRuns(row, initialCount, width, fRuns, fAA);
   *
   *     fBlitter->blitAntiH(x, y, fAA, fRuns);
   * }
   * ```
   */
  public override fun blitH(
    x: Int,
    y: Int,
    width: Int,
  ) {
    TODO("Implement blitH")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkAAClipBlitter::blitAntiH(int x, int y, const SkAlpha aa[],
   *                                 const int16_t runs[]) {
   *
   *     const uint8_t* row = fAAClip->findRow(y);
   *     int initialCount;
   *     row = fAAClip->findX(row, x, &initialCount);
   *
   *     this->ensureRunsAndAA();
   *
   *     merge(row, initialCount, aa, runs, fAA, fRuns, fAAClipBounds.width());
   *     fBlitter->blitAntiH(x, y, fAA, fRuns);
   * }
   * ```
   */
  public override fun blitAntiH(
    x: Int,
    y: Int,
    aa: Array<SkAlpha>,
    runs: ShortArray,
  ) {
    TODO("Implement blitAntiH")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkAAClipBlitter::blitV(int x, int y, int height, SkAlpha alpha) {
   *     if (fAAClip->quickContains(x, y, x + 1, y + height)) {
   *         fBlitter->blitV(x, y, height, alpha);
   *         return;
   *     }
   *
   *     for (;;) {
   *         int lastY SK_INIT_TO_AVOID_WARNING;
   *         const uint8_t* row = fAAClip->findRow(y, &lastY);
   *         int dy = lastY - y + 1;
   *         if (dy > height) {
   *             dy = height;
   *         }
   *         height -= dy;
   *
   *         row = fAAClip->findX(row, x);
   *         SkAlpha newAlpha = SkMulDiv255Round(alpha, row[1]);
   *         if (newAlpha) {
   *             fBlitter->blitV(x, y, dy, newAlpha);
   *         }
   *         SkASSERT(height >= 0);
   *         if (height <= 0) {
   *             break;
   *         }
   *         y = lastY + 1;
   *     }
   * }
   * ```
   */
  public override fun blitV(
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
   * void SkAAClipBlitter::blitRect(int x, int y, int width, int height) {
   *     if (fAAClip->quickContains(x, y, x + width, y + height)) {
   *         fBlitter->blitRect(x, y, width, height);
   *         return;
   *     }
   *
   *     while (--height >= 0) {
   *         this->blitH(x, y, width);
   *         y += 1;
   *     }
   * }
   * ```
   */
  public override fun blitRect(
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
   * void SkAAClipBlitter::blitMask(const SkMask& origMask, const SkIRect& clip) {
   *     SkASSERT(fAAClip->getBounds().contains(clip));
   *
   *     if (fAAClip->quickContains(clip)) {
   *         fBlitter->blitMask(origMask, clip);
   *         return;
   *     }
   *
   *     const SkMask* mask = &origMask;
   *
   *     // if we're BW, we need to upscale to A8 (ugh)
   *     SkMaskBuilder  grayMask;
   *     if (SkMask::kBW_Format == origMask.fFormat) {
   *         grayMask.format() = SkMask::kA8_Format;
   *         grayMask.bounds() = origMask.fBounds;
   *         grayMask.rowBytes() = origMask.fBounds.width();
   *         size_t size = grayMask.computeImageSize();
   *         grayMask.image() = reinterpret_cast<uint8_t*>(
   *             fGrayMaskScratch.reset(size, SkAutoMalloc::kReuse_OnShrink));
   *
   *         upscaleBW2A8(&grayMask, origMask);
   *         mask = &grayMask;
   *     }
   *
   *     this->ensureRunsAndAA();
   *
   *     // HACK -- we are devolving 3D into A8, need to copy the rest of the 3D
   *     // data into a temp block to support it better (ugh)
   *
   *     const void* src = mask->getAddr(clip.fLeft, clip.fTop);
   *     const size_t srcRB = mask->fRowBytes;
   *     const int width = clip.width();
   *     MergeAAProc mergeProc = find_merge_aa_proc(mask->fFormat);
   *
   *     SkMaskBuilder rowMask;
   *     rowMask.format() = SkMask::k3D_Format == mask->fFormat ? SkMask::kA8_Format : mask->fFormat;
   *     rowMask.bounds().fLeft = clip.fLeft;
   *     rowMask.bounds().fRight = clip.fRight;
   *     rowMask.rowBytes() = mask->fRowBytes; // doesn't matter, since our height==1
   *     rowMask.image() = (uint8_t*)fScanlineScratch;
   *
   *     int y = clip.fTop;
   *     const int stopY = y + clip.height();
   *
   *     do {
   *         int localStopY SK_INIT_TO_AVOID_WARNING;
   *         const uint8_t* row = fAAClip->findRow(y, &localStopY);
   *         // findRow returns last Y, not stop, so we add 1
   *         localStopY = std::min(localStopY + 1, stopY);
   *
   *         int initialCount;
   *         row = fAAClip->findX(row, clip.fLeft, &initialCount);
   *         do {
   *             mergeProc(src, width, row, initialCount, rowMask.image());
   *             rowMask.bounds().fTop = y;
   *             rowMask.bounds().fBottom = y + 1;
   *             fBlitter->blitMask(rowMask, rowMask.fBounds);
   *             src = (const void*)((const char*)src + srcRB);
   *         } while (++y < localStopY);
   *     } while (y < stopY);
   * }
   * ```
   */
  public override fun blitMask(origMask: SkMask, clip: SkIRect) {
    TODO("Implement blitMask")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkAAClipBlitter::ensureRunsAndAA() {
   *     if (nullptr == fScanlineScratch) {
   *         // add 1 so we can store the terminating run count of 0
   *         int count = fAAClipBounds.width() + 1;
   *         // we use this either for fRuns + fAA, or a scaline of a mask
   *         // which may be as deep as 32bits
   *         fScanlineScratch = sk_malloc_throw(count * sizeof(SkPMColor));
   *         fRuns = (int16_t*)fScanlineScratch;
   *         fAA = (SkAlpha*)(fRuns + count);
   *     }
   * }
   * ```
   */
  private fun ensureRunsAndAA() {
    TODO("Implement ensureRunsAndAA")
  }

  public companion object {
    public val kSize: Int = TODO("Initialize kSize")
  }
}
