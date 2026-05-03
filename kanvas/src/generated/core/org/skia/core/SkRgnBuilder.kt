package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.ShortArray
import org.skia.foundation.SkAlpha
import org.skia.math.SkIRect

/**
 * C++ original:
 * ```cpp
 * class SkRgnBuilder : public SkBlitter {
 * public:
 *     SkRgnBuilder();
 *     ~SkRgnBuilder() override;
 *
 *     // returns true if it could allocate the working storage needed
 *     bool init(int maxHeight, int maxTransitions, bool pathIsInverse);
 *
 *     void done() {
 *         if (fCurrScanline != nullptr) {
 *             fCurrScanline->fXCount = (SkRegion::RunType)((int)(fCurrXPtr - fCurrScanline->firstX()));
 *             if (!this->collapsWithPrev()) { // flush the last line
 *                 fCurrScanline = fCurrScanline->nextScanline();
 *             }
 *         }
 *     }
 *
 *     int     computeRunCount() const;
 *     void    copyToRect(SkIRect*) const;
 *     void    copyToRgn(SkRegion::RunType runs[]) const;
 *
 *     void blitH(int x, int y, int width) override;
 *     void blitAntiH(int x, int y, const SkAlpha antialias[], const int16_t runs[]) override {
 *         SkDEBUGFAIL("blitAntiH not implemented");
 *     }
 *
 * #ifdef SK_DEBUG
 *     void dump() const {
 *         SkDebugf("SkRgnBuilder: Top = %d\n", fTop);
 *         Scanline* line = (Scanline*)fStorage;
 *         while (line < fCurrScanline) {
 *             SkDebugf("SkRgnBuilder::Scanline: LastY=%d, fXCount=%d", line->fLastY, line->fXCount);
 *             for (int i = 0; i < line->fXCount; i++) {
 *                 SkDebugf(" %d", line->firstX()[i]);
 *             }
 *             SkDebugf("\n");
 *
 *             line = line->nextScanline();
 *         }
 *     }
 * #endif
 * private:
 *     /*
 *      *  Scanline mimics a row in the region, nearly. A row in a region is:
 *      *      [Bottom IntervalCount [L R]... Sentinel]
 *      *  while a Scanline is
 *      *      [LastY XCount [L R]... uninitialized]
 *      *  The two are the same length (which is good), but we have to transmute
 *      *  the scanline a little when we convert it to a region-row.
 *      *
 *      *  Potentially we could recode this to exactly match the row format, in
 *      *  which case copyToRgn() could be a single memcpy. Not sure that is worth
 *      *  the effort.
 *      */
 *     struct Scanline {
 *         SkRegion::RunType fLastY;
 *         SkRegion::RunType fXCount;
 *
 *         SkRegion::RunType* firstX() { return (SkRegion::RunType*)(this + 1); }
 *         Scanline* nextScanline() {
 *             // add final +1 for the x-sentinel
 *             return (Scanline*)((SkRegion::RunType*)(this + 1) + fXCount + 1);
 *         }
 *     };
 *     SkRegion::RunType*  fStorage;
 *     Scanline*           fCurrScanline;
 *     Scanline*           fPrevScanline;
 *     //  points at next avialable x[] in fCurrScanline
 *     SkRegion::RunType*  fCurrXPtr;
 *     SkRegion::RunType   fTop;           // first Y value
 *
 *     int fStorageCount;
 *
 *     bool collapsWithPrev() {
 *         if (fPrevScanline != nullptr &&
 *             fPrevScanline->fLastY + 1 == fCurrScanline->fLastY &&
 *             fPrevScanline->fXCount == fCurrScanline->fXCount &&
 *             sk_memeq32(fPrevScanline->firstX(), fCurrScanline->firstX(), fCurrScanline->fXCount))
 *         {
 *             // update the height of fPrevScanline
 *             fPrevScanline->fLastY = fCurrScanline->fLastY;
 *             return true;
 *         }
 *         return false;
 *     }
 * }
 * ```
 */
public open class SkRgnBuilder public constructor() : SkBlitter() {
  /**
   * C++ original:
   * ```cpp
   * SkRegion::RunType*  fStorage
   * ```
   */
  private var fStorage: SkRegionRunType? = TODO("Initialize fStorage")

  /**
   * C++ original:
   * ```cpp
   * Scanline*           fCurrScanline
   * ```
   */
  private var fCurrScanline: Scanline? = TODO("Initialize fCurrScanline")

  /**
   * C++ original:
   * ```cpp
   * Scanline*           fPrevScanline
   * ```
   */
  private var fPrevScanline: Scanline? = TODO("Initialize fPrevScanline")

  /**
   * C++ original:
   * ```cpp
   * SkRegion::RunType*  fCurrXPtr
   * ```
   */
  private var fCurrXPtr: SkRegionRunType? = TODO("Initialize fCurrXPtr")

  /**
   * C++ original:
   * ```cpp
   * SkRegion::RunType   fTop
   * ```
   */
  private var fTop: SkRegionRunType = TODO("Initialize fTop")

  /**
   * C++ original:
   * ```cpp
   * int fStorageCount
   * ```
   */
  private var fStorageCount: Int = TODO("Initialize fStorageCount")

  /**
   * C++ original:
   * ```cpp
   * bool SkRgnBuilder::init(int maxHeight, int maxTransitions, bool pathIsInverse) {
   *     if ((maxHeight | maxTransitions) < 0) {
   *         return false;
   *     }
   *
   *     SkSafeMath  safe;
   *
   *     if (pathIsInverse) {
   *         // allow for additional X transitions to "invert" each scanline
   *         // [ L' ... normal transitions ... R' ]
   *         //
   *         maxTransitions = safe.addInt(maxTransitions, 2);
   *     }
   *
   *     // compute the count with +1 and +3 slop for the working buffer
   *     size_t count = safe.mul(safe.addInt(maxHeight, 1), safe.addInt(3, maxTransitions));
   *
   *     if (pathIsInverse) {
   *         // allow for two "empty" rows for the top and bottom
   *         //      [ Y, 1, L, R, S] == 5 (*2 for top and bottom)
   *         count = safe.add(count, 10);
   *     }
   *
   *     if (!safe || !SkTFitsIn<int32_t>(count)) {
   *         return false;
   *     }
   *     fStorageCount = SkToS32(count);
   *
   *     fStorage = (SkRegion::RunType*)sk_malloc_canfail(fStorageCount, sizeof(SkRegion::RunType));
   *     if (nullptr == fStorage) {
   *         return false;
   *     }
   *
   *     fCurrScanline = nullptr;    // signal empty collection
   *     fPrevScanline = nullptr;    // signal first scanline
   *     return true;
   * }
   * ```
   */
  public fun `init`(
    maxHeight: Int,
    maxTransitions: Int,
    pathIsInverse: Boolean,
  ): Boolean {
    TODO("Implement init")
  }

  /**
   * C++ original:
   * ```cpp
   * void done() {
   *         if (fCurrScanline != nullptr) {
   *             fCurrScanline->fXCount = (SkRegion::RunType)((int)(fCurrXPtr - fCurrScanline->firstX()));
   *             if (!this->collapsWithPrev()) { // flush the last line
   *                 fCurrScanline = fCurrScanline->nextScanline();
   *             }
   *         }
   *     }
   * ```
   */
  public fun done() {
    TODO("Implement done")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkRgnBuilder::computeRunCount() const {
   *     if (fCurrScanline == nullptr) {
   *         return 0;
   *     }
   *
   *     const SkRegion::RunType*  line = fStorage;
   *     const SkRegion::RunType*  stop = (const SkRegion::RunType*)fCurrScanline;
   *
   *     return 2 + (int)(stop - line);
   * }
   * ```
   */
  public fun computeRunCount(): Int {
    TODO("Implement computeRunCount")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRgnBuilder::copyToRect(SkIRect* r) const {
   *     SkASSERT(fCurrScanline != nullptr);
   *     // A rect's scanline is [bottom intervals left right sentinel] == 5
   *     SkASSERT((const SkRegion::RunType*)fCurrScanline - fStorage == 5);
   *
   *     Scanline* line = (Scanline*)fStorage;
   *     SkASSERT(line->fXCount == 2);
   *
   *     r->setLTRB(line->firstX()[0], fTop, line->firstX()[1], line->fLastY + 1);
   * }
   * ```
   */
  public fun copyToRect(r: SkIRect?) {
    TODO("Implement copyToRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRgnBuilder::copyToRgn(SkRegion::RunType runs[]) const {
   *     SkASSERT(fCurrScanline != nullptr);
   *     SkASSERT((const SkRegion::RunType*)fCurrScanline - fStorage > 4);
   *
   *     Scanline* line = (Scanline*)fStorage;
   *     const Scanline* stop = fCurrScanline;
   *
   *     *runs++ = fTop;
   *     do {
   *         *runs++ = (SkRegion::RunType)(line->fLastY + 1);
   *         int count = line->fXCount;
   *         *runs++ = count >> 1;   // intervalCount
   *         if (count) {
   *             memcpy(runs, line->firstX(), count * sizeof(SkRegion::RunType));
   *             runs += count;
   *         }
   *         *runs++ = SkRegion_kRunTypeSentinel;
   *         line = line->nextScanline();
   *     } while (line < stop);
   *     SkASSERT(line == stop);
   *     *runs = SkRegion_kRunTypeSentinel;
   * }
   * ```
   */
  public fun copyToRgn(runs: Array<SkRegionRunType>) {
    TODO("Implement copyToRgn")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRgnBuilder::blitH(int x, int y, int width) {
   *     if (fCurrScanline == nullptr) {  // first time
   *         fTop = (SkRegion::RunType)(y);
   *         fCurrScanline = (Scanline*)fStorage;
   *         fCurrScanline->fLastY = (SkRegion::RunType)(y);
   *         fCurrXPtr = fCurrScanline->firstX();
   *     } else {
   *         SkASSERT(y >= fCurrScanline->fLastY);
   *
   *         if (y > fCurrScanline->fLastY) {
   *             // if we get here, we're done with fCurrScanline
   *             fCurrScanline->fXCount = (SkRegion::RunType)((int)(fCurrXPtr - fCurrScanline->firstX()));
   *
   *             int prevLastY = fCurrScanline->fLastY;
   *             if (!this->collapsWithPrev()) {
   *                 fPrevScanline = fCurrScanline;
   *                 fCurrScanline = fCurrScanline->nextScanline();
   *
   *             }
   *             if (y - 1 > prevLastY) {  // insert empty run
   *                 fCurrScanline->fLastY = (SkRegion::RunType)(y - 1);
   *                 fCurrScanline->fXCount = 0;
   *                 fCurrScanline = fCurrScanline->nextScanline();
   *             }
   *             // setup for the new curr line
   *             fCurrScanline->fLastY = (SkRegion::RunType)(y);
   *             fCurrXPtr = fCurrScanline->firstX();
   *         }
   *     }
   *     //  check if we should extend the current run, or add a new one
   *     if (fCurrXPtr > fCurrScanline->firstX() && fCurrXPtr[-1] == x) {
   *         fCurrXPtr[-1] = (SkRegion::RunType)(x + width);
   *     } else {
   *         fCurrXPtr[0] = (SkRegion::RunType)(x);
   *         fCurrXPtr[1] = (SkRegion::RunType)(x + width);
   *         fCurrXPtr += 2;
   *     }
   *     SkASSERT(fCurrXPtr - fStorage < fStorageCount);
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
   * void blitAntiH(int x, int y, const SkAlpha antialias[], const int16_t runs[]) override {
   *         SkDEBUGFAIL("blitAntiH not implemented");
   *     }
   * ```
   */
  public override fun blitAntiH(
    x: Int,
    y: Int,
    antialias: Array<SkAlpha>,
    runs: ShortArray,
  ) {
    TODO("Implement blitAntiH")
  }

  /**
   * C++ original:
   * ```cpp
   * void dump() const {
   *         SkDebugf("SkRgnBuilder: Top = %d\n", fTop);
   *         Scanline* line = (Scanline*)fStorage;
   *         while (line < fCurrScanline) {
   *             SkDebugf("SkRgnBuilder::Scanline: LastY=%d, fXCount=%d", line->fLastY, line->fXCount);
   *             for (int i = 0; i < line->fXCount; i++) {
   *                 SkDebugf(" %d", line->firstX()[i]);
   *             }
   *             SkDebugf("\n");
   *
   *             line = line->nextScanline();
   *         }
   *     }
   * ```
   */
  public fun dump() {
    TODO("Implement dump")
  }

  /**
   * C++ original:
   * ```cpp
   * bool collapsWithPrev() {
   *         if (fPrevScanline != nullptr &&
   *             fPrevScanline->fLastY + 1 == fCurrScanline->fLastY &&
   *             fPrevScanline->fXCount == fCurrScanline->fXCount &&
   *             sk_memeq32(fPrevScanline->firstX(), fCurrScanline->firstX(), fCurrScanline->fXCount))
   *         {
   *             // update the height of fPrevScanline
   *             fPrevScanline->fLastY = fCurrScanline->fLastY;
   *             return true;
   *         }
   *         return false;
   *     }
   * ```
   */
  private fun collapsWithPrev(): Boolean {
    TODO("Implement collapsWithPrev")
  }

  public data class Scanline public constructor(
    public var fLastY: SkRegionRunType,
    public var fXCount: SkRegionRunType,
  ) {
    public fun firstX(): SkRegionRunType {
      TODO("Implement firstX")
    }

    public fun nextScanline(): undefined.Scanline {
      TODO("Implement nextScanline")
    }
  }
}
