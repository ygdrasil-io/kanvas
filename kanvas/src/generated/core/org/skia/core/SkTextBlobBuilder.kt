package org.skia.core

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.ULong
import org.skia.foundation.SkFont
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SK_API SkTextBlobBuilder {
 * public:
 *
 *     /** Constructs empty SkTextBlobBuilder. By default, SkTextBlobBuilder has no runs.
 *
 *         @return  empty SkTextBlobBuilder
 *
 *         example: https://fiddle.skia.org/c/@TextBlobBuilder_empty_constructor
 *     */
 *     SkTextBlobBuilder();
 *
 *     /** Deletes data allocated internally by SkTextBlobBuilder.
 *     */
 *     ~SkTextBlobBuilder();
 *
 *     /** Returns SkTextBlob built from runs of glyphs added by builder. Returned
 *         SkTextBlob is immutable; it may be copied, but its contents may not be altered.
 *         Returns nullptr if no runs of glyphs were added by builder.
 *
 *         Resets SkTextBlobBuilder to its initial empty state, allowing it to be
 *         reused to build a new set of runs.
 *
 *         @return  SkTextBlob or nullptr
 *
 *         example: https://fiddle.skia.org/c/@TextBlobBuilder_make
 *     */
 *     sk_sp<SkTextBlob> make();
 *
 *     /** \struct SkTextBlobBuilder::RunBuffer
 *         RunBuffer supplies storage for glyphs and positions within a run.
 *
 *         A run is a sequence of glyphs sharing font metrics and positioning.
 *         Each run may position its glyphs in one of three ways:
 *         by specifying where the first glyph is drawn, and allowing font metrics to
 *         determine the advance to subsequent glyphs; by specifying a baseline, and
 *         the position on that baseline for each glyph in run; or by providing SkPoint
 *         array, one per glyph.
 *     */
 *     struct RunBuffer {
 *         SkGlyphID* glyphs;   //!< storage for glyph indexes in run
 *         SkScalar*  pos;      //!< storage for glyph positions in run
 *         char*      utf8text; //!< storage for text UTF-8 code units in run
 *         uint32_t*  clusters; //!< storage for glyph clusters (index of UTF-8 code unit)
 *
 *         // Helpers, since the "pos" field can be different types (always some number of floats).
 *         SkPoint*    points() const { return reinterpret_cast<SkPoint*>(pos); }
 *         SkRSXform*  xforms() const { return reinterpret_cast<SkRSXform*>(pos); }
 *     };
 *
 *     /** Returns run with storage for glyphs. Caller must write count glyphs to
 *         RunBuffer::glyphs before next call to SkTextBlobBuilder.
 *
 *         RunBuffer::pos, RunBuffer::utf8text, and RunBuffer::clusters should be ignored.
 *
 *         Glyphs share metrics in font.
 *
 *         Glyphs are positioned on a baseline at (x, y), using font metrics to
 *         determine their relative placement.
 *
 *         bounds defines an optional bounding box, used to suppress drawing when SkTextBlob
 *         bounds does not intersect SkSurface bounds. If bounds is nullptr, SkTextBlob bounds
 *         is computed from (x, y) and RunBuffer::glyphs metrics.
 *
 *         @param font    SkFont used for this run
 *         @param count   number of glyphs
 *         @param x       horizontal offset within the blob
 *         @param y       vertical offset within the blob
 *         @param bounds  optional run bounding box
 *         @return writable glyph buffer
 *     */
 *     const RunBuffer& allocRun(const SkFont& font, int count, SkScalar x, SkScalar y,
 *                               const SkRect* bounds = nullptr);
 *
 *     /** Returns run with storage for glyphs and positions along baseline. Caller must
 *         write count glyphs to RunBuffer::glyphs and count scalars to RunBuffer::pos
 *         before next call to SkTextBlobBuilder.
 *
 *         RunBuffer::utf8text and RunBuffer::clusters should be ignored.
 *
 *         Glyphs share metrics in font.
 *
 *         Glyphs are positioned on a baseline at y, using x-axis positions written by
 *         caller to RunBuffer::pos.
 *
 *         bounds defines an optional bounding box, used to suppress drawing when SkTextBlob
 *         bounds does not intersect SkSurface bounds. If bounds is nullptr, SkTextBlob bounds
 *         is computed from y, RunBuffer::pos, and RunBuffer::glyphs metrics.
 *
 *         @param font    SkFont used for this run
 *         @param count   number of glyphs
 *         @param y       vertical offset within the blob
 *         @param bounds  optional run bounding box
 *         @return writable glyph buffer and x-axis position buffer
 *     */
 *     const RunBuffer& allocRunPosH(const SkFont& font, int count, SkScalar y,
 *                                   const SkRect* bounds = nullptr);
 *
 *     /** Returns run with storage for glyphs and SkPoint positions. Caller must
 *         write count glyphs to RunBuffer::glyphs and count SkPoint to RunBuffer::pos
 *         before next call to SkTextBlobBuilder.
 *
 *         RunBuffer::utf8text and RunBuffer::clusters should be ignored.
 *
 *         Glyphs share metrics in font.
 *
 *         Glyphs are positioned using SkPoint written by caller to RunBuffer::pos, using
 *         two scalar values for each SkPoint.
 *
 *         bounds defines an optional bounding box, used to suppress drawing when SkTextBlob
 *         bounds does not intersect SkSurface bounds. If bounds is nullptr, SkTextBlob bounds
 *         is computed from RunBuffer::pos, and RunBuffer::glyphs metrics.
 *
 *         @param font    SkFont used for this run
 *         @param count   number of glyphs
 *         @param bounds  optional run bounding box
 *         @return writable glyph buffer and SkPoint buffer
 *     */
 *     const RunBuffer& allocRunPos(const SkFont& font, int count,
 *                                  const SkRect* bounds = nullptr);
 *
 *     // RunBuffer.pos points to SkRSXform array
 *     const RunBuffer& allocRunRSXform(const SkFont& font, int count);
 *
 *     /** Returns run with storage for glyphs, text, and clusters. Caller must
 *         write count glyphs to RunBuffer::glyphs, textByteCount UTF-8 code units
 *         into RunBuffer::utf8text, and count monotonic indexes into utf8text
 *         into RunBuffer::clusters before next call to SkTextBlobBuilder.
 *
 *         RunBuffer::pos should be ignored.
 *
 *         Glyphs share metrics in font.
 *
 *         Glyphs are positioned on a baseline at (x, y), using font metrics to
 *         determine their relative placement.
 *
 *         bounds defines an optional bounding box, used to suppress drawing when SkTextBlob
 *         bounds does not intersect SkSurface bounds. If bounds is nullptr, SkTextBlob bounds
 *         is computed from (x, y) and RunBuffer::glyphs metrics.
 *
 *         @param font          SkFont used for this run
 *         @param count         number of glyphs
 *         @param x             horizontal offset within the blob
 *         @param y             vertical offset within the blob
 *         @param textByteCount number of UTF-8 code units
 *         @param bounds        optional run bounding box
 *         @return writable glyph buffer, text buffer, and cluster buffer
 *     */
 *     const RunBuffer& allocRunText(const SkFont& font, int count, SkScalar x, SkScalar y,
 *                                   int textByteCount, const SkRect* bounds = nullptr);
 *
 *     /** Returns run with storage for glyphs, positions along baseline, text,
 *         and clusters. Caller must write count glyphs to RunBuffer::glyphs,
 *         count scalars to RunBuffer::pos, textByteCount UTF-8 code units into
 *         RunBuffer::utf8text, and count monotonic indexes into utf8text into
 *         RunBuffer::clusters before next call to SkTextBlobBuilder.
 *
 *         Glyphs share metrics in font.
 *
 *         Glyphs are positioned on a baseline at y, using x-axis positions written by
 *         caller to RunBuffer::pos.
 *
 *         bounds defines an optional bounding box, used to suppress drawing when SkTextBlob
 *         bounds does not intersect SkSurface bounds. If bounds is nullptr, SkTextBlob bounds
 *         is computed from y, RunBuffer::pos, and RunBuffer::glyphs metrics.
 *
 *         @param font          SkFont used for this run
 *         @param count         number of glyphs
 *         @param y             vertical offset within the blob
 *         @param textByteCount number of UTF-8 code units
 *         @param bounds        optional run bounding box
 *         @return writable glyph buffer, x-axis position buffer, text buffer, and cluster buffer
 *     */
 *     const RunBuffer& allocRunTextPosH(const SkFont& font, int count, SkScalar y, int textByteCount,
 *                                       const SkRect* bounds = nullptr);
 *
 *     /** Returns run with storage for glyphs, SkPoint positions, text, and
 *         clusters. Caller must write count glyphs to RunBuffer::glyphs, count
 *         SkPoint to RunBuffer::pos, textByteCount UTF-8 code units into
 *         RunBuffer::utf8text, and count monotonic indexes into utf8text into
 *         RunBuffer::clusters before next call to SkTextBlobBuilder.
 *
 *         Glyphs share metrics in font.
 *
 *         Glyphs are positioned using SkPoint written by caller to RunBuffer::pos, using
 *         two scalar values for each SkPoint.
 *
 *         bounds defines an optional bounding box, used to suppress drawing when SkTextBlob
 *         bounds does not intersect SkSurface bounds. If bounds is nullptr, SkTextBlob bounds
 *         is computed from RunBuffer::pos, and RunBuffer::glyphs metrics.
 *
 *         @param font          SkFont used for this run
 *         @param count         number of glyphs
 *         @param textByteCount number of UTF-8 code units
 *         @param bounds        optional run bounding box
 *         @return writable glyph buffer, SkPoint buffer, text buffer, and cluster buffer
 *     */
 *     const RunBuffer& allocRunTextPos(const SkFont& font, int count, int textByteCount,
 *                                      const SkRect* bounds = nullptr);
 *
 *     // RunBuffer.pos points to SkRSXform array
 *     const RunBuffer& allocRunTextRSXform(const SkFont& font, int count, int textByteCount,
 *                                          const SkRect* bounds = nullptr);
 *
 * private:
 *     void reserve(size_t size);
 *     void allocInternal(const SkFont& font, SkTextBlob::GlyphPositioning positioning,
 *                        int count, int textBytes, SkPoint offset, const SkRect* bounds);
 *     bool mergeRun(const SkFont& font, SkTextBlob::GlyphPositioning positioning,
 *                   uint32_t count, SkPoint offset);
 *     void updateDeferredBounds();
 *
 *     static SkRect ConservativeRunBounds(const SkTextBlob::RunRecord&);
 *     static SkRect TightRunBounds(const SkTextBlob::RunRecord&);
 *
 *     friend class SkTextBlobPriv;
 *     friend class SkTextBlobBuilderPriv;
 *
 *     skia_private::AutoTMalloc<uint8_t> fStorage;
 *     size_t                 fStorageSize;
 *     size_t                 fStorageUsed;
 *
 *     SkRect                 fBounds;
 *     int                    fRunCount;
 *     bool                   fDeferredBounds;
 *     size_t                 fLastRun; // index into fStorage
 *
 *     RunBuffer              fCurrentRunBuffer;
 * }
 * ```
 */
public data class SkTextBlobBuilder public constructor(
  /**
   * C++ original:
   * ```cpp
   * skia_private::AutoTMalloc<uint8_t> fStorage
   * ```
   */
  private var fStorage: Int,
  /**
   * C++ original:
   * ```cpp
   * size_t                 fStorageSize
   * ```
   */
  private var fStorageSize: ULong,
  /**
   * C++ original:
   * ```cpp
   * size_t                 fStorageUsed
   * ```
   */
  private var fStorageUsed: ULong,
  /**
   * C++ original:
   * ```cpp
   * SkRect                 fBounds
   * ```
   */
  private var fBounds: Int,
  /**
   * C++ original:
   * ```cpp
   * int                    fRunCount
   * ```
   */
  private var fRunCount: Int,
  /**
   * C++ original:
   * ```cpp
   * bool                   fDeferredBounds
   * ```
   */
  private var fDeferredBounds: Boolean,
  /**
   * C++ original:
   * ```cpp
   * size_t                 fLastRun
   * ```
   */
  private var fLastRun: ULong,
  /**
   * C++ original:
   * ```cpp
   * RunBuffer              fCurrentRunBuffer
   * ```
   */
  private var fCurrentRunBuffer: RunBuffer,
) {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTextBlob> SkTextBlobBuilder::make() {
   *     if (!fRunCount) {
   *         // We don't instantiate empty blobs.
   *         SkASSERT(!fStorage.get());
   *         SkASSERT(fStorageUsed == 0);
   *         SkASSERT(fStorageSize == 0);
   *         SkASSERT(fLastRun == 0);
   *         SkASSERT(fBounds.isEmpty());
   *         return nullptr;
   *     }
   *
   *     this->updateDeferredBounds();
   *
   *     // Tag the last run as such.
   *     auto* lastRun = reinterpret_cast<SkTextBlob::RunRecord*>(fStorage.get() + fLastRun);
   *     lastRun->fFlags |= SkTextBlob::RunRecord::kLast_Flag;
   *
   *     SkTextBlob* blob = new (fStorage.release()) SkTextBlob(fBounds);
   *     SkDEBUGCODE(const_cast<SkTextBlob*>(blob)->fStorageSize = fStorageSize;)
   *
   *     SkDEBUGCODE(
   *         SkSafeMath safe;
   *         size_t validateSize = SkAlignPtr(sizeof(SkTextBlob));
   *         for (const auto* run = SkTextBlob::RunRecord::First(blob); run;
   *              run = SkTextBlob::RunRecord::Next(run)) {
   *             validateSize += SkTextBlob::RunRecord::StorageSize(
   *                     run->fCount, run->textSize(), run->positioning(), &safe);
   *             run->validate(reinterpret_cast<const uint8_t*>(blob) + fStorageUsed);
   *             fRunCount--;
   *         }
   *         SkASSERT(validateSize == fStorageUsed);
   *         SkASSERT(fRunCount == 0);
   *         SkASSERT(safe);
   *     )
   *
   *     fStorageUsed = 0;
   *     fStorageSize = 0;
   *     fRunCount = 0;
   *     fLastRun = 0;
   *     fBounds.setEmpty();
   *
   *     return sk_sp<SkTextBlob>(blob);
   * }
   * ```
   */
  public fun make(): Int {
    TODO("Implement make")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkTextBlobBuilder::RunBuffer& SkTextBlobBuilder::allocRun(const SkFont& font, int count,
   *                                                                 SkScalar x, SkScalar y,
   *                                                                 const SkRect* bounds) {
   *     this->allocInternal(font, SkTextBlob::kDefault_Positioning, count, 0, {x, y}, bounds);
   *     return fCurrentRunBuffer;
   * }
   * ```
   */
  public fun allocRun(
    font: SkFont,
    count: Int,
    x: SkScalar,
    y: SkScalar,
    bounds: SkRect? = TODO(),
  ): RunBuffer {
    TODO("Implement allocRun")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkTextBlobBuilder::RunBuffer& SkTextBlobBuilder::allocRunPosH(const SkFont& font, int count,
   *                                                                     SkScalar y,
   *                                                                     const SkRect* bounds) {
   *     this->allocInternal(font, SkTextBlob::kHorizontal_Positioning, count, 0, {0, y}, bounds);
   *     return fCurrentRunBuffer;
   * }
   * ```
   */
  public fun allocRunPosH(
    font: SkFont,
    count: Int,
    y: SkScalar,
    bounds: SkRect? = TODO(),
  ): RunBuffer {
    TODO("Implement allocRunPosH")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkTextBlobBuilder::RunBuffer& SkTextBlobBuilder::allocRunPos(const SkFont& font, int count,
   *                                                                    const SkRect* bounds) {
   *     this->allocInternal(font, SkTextBlob::kFull_Positioning, count, 0, {0, 0}, bounds);
   *     return fCurrentRunBuffer;
   * }
   * ```
   */
  public fun allocRunPos(
    font: SkFont,
    count: Int,
    bounds: SkRect? = TODO(),
  ): RunBuffer {
    TODO("Implement allocRunPos")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkTextBlobBuilder::RunBuffer&
   * SkTextBlobBuilder::allocRunRSXform(const SkFont& font, int count) {
   *     this->allocInternal(font, SkTextBlob::kRSXform_Positioning, count, 0, {0, 0}, nullptr);
   *     return fCurrentRunBuffer;
   * }
   * ```
   */
  public fun allocRunRSXform(font: SkFont, count: Int): RunBuffer {
    TODO("Implement allocRunRSXform")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkTextBlobBuilder::RunBuffer& SkTextBlobBuilder::allocRunText(const SkFont& font, int count,
   *                                                                     SkScalar x, SkScalar y,
   *                                                                     int textByteCount,
   *                                                                     const SkRect* bounds) {
   *     this->allocInternal(font,
   *                         SkTextBlob::kDefault_Positioning,
   *                         count,
   *                         textByteCount,
   *                         SkPoint::Make(x, y),
   *                         bounds);
   *     return fCurrentRunBuffer;
   * }
   * ```
   */
  public fun allocRunText(
    font: SkFont,
    count: Int,
    x: SkScalar,
    y: SkScalar,
    textByteCount: Int,
    bounds: SkRect? = TODO(),
  ): RunBuffer {
    TODO("Implement allocRunText")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkTextBlobBuilder::RunBuffer& SkTextBlobBuilder::allocRunTextPosH(const SkFont& font,
   *                                                                         int count,
   *                                                                         SkScalar y,
   *                                                                         int textByteCount,
   *                                                                         const SkRect* bounds) {
   *     this->allocInternal(font,
   *                         SkTextBlob::kHorizontal_Positioning,
   *                         count,
   *                         textByteCount,
   *                         SkPoint::Make(0, y),
   *                         bounds);
   *     return fCurrentRunBuffer;
   * }
   * ```
   */
  public fun allocRunTextPosH(
    font: SkFont,
    count: Int,
    y: SkScalar,
    textByteCount: Int,
    bounds: SkRect? = TODO(),
  ): RunBuffer {
    TODO("Implement allocRunTextPosH")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkTextBlobBuilder::RunBuffer& SkTextBlobBuilder::allocRunTextPos(const SkFont& font,
   *                                                                        int count,
   *                                                                        int textByteCount,
   *                                                                        const SkRect *bounds) {
   *     this->allocInternal(font,
   *                         SkTextBlob::kFull_Positioning,
   *                         count, textByteCount,
   *                         SkPoint::Make(0, 0),
   *                         bounds);
   *     return fCurrentRunBuffer;
   * }
   * ```
   */
  public fun allocRunTextPos(
    font: SkFont,
    count: Int,
    textByteCount: Int,
    bounds: SkRect? = TODO(),
  ): RunBuffer {
    TODO("Implement allocRunTextPos")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkTextBlobBuilder::RunBuffer& SkTextBlobBuilder::allocRunTextRSXform(const SkFont& font,
   *                                                                            int count,
   *                                                                            int textByteCount,
   *                                                                            const SkRect *bounds) {
   *     this->allocInternal(font,
   *                         SkTextBlob::kRSXform_Positioning,
   *                         count,
   *                         textByteCount,
   *                         {0, 0},
   *                         bounds);
   *     return fCurrentRunBuffer;
   * }
   * ```
   */
  public fun allocRunTextRSXform(
    font: SkFont,
    count: Int,
    textByteCount: Int,
    bounds: SkRect? = TODO(),
  ): RunBuffer {
    TODO("Implement allocRunTextRSXform")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTextBlobBuilder::reserve(size_t size) {
   *     SkSafeMath safe;
   *
   *     // We don't currently pre-allocate, but maybe someday...
   *     if (safe.add(fStorageUsed, size) <= fStorageSize && safe) {
   *         return;
   *     }
   *
   *     if (0 == fRunCount) {
   *         SkASSERT(nullptr == fStorage.get());
   *         SkASSERT(0 == fStorageSize);
   *         SkASSERT(0 == fStorageUsed);
   *
   *         // the first allocation also includes blob storage
   *         // aligned up to a pointer alignment so SkTextBlob::RunRecords after it stay aligned.
   *         fStorageUsed = SkAlignPtr(sizeof(SkTextBlob));
   *     }
   *
   *     fStorageSize = safe.add(fStorageUsed, size);
   *
   *     // FYI: This relies on everything we store being relocatable, particularly SkPaint.
   *     //      Also, this is counting on the underlying realloc to throw when passed max().
   *     fStorage.realloc(safe ? fStorageSize : std::numeric_limits<size_t>::max());
   * }
   * ```
   */
  private fun reserve(size: ULong) {
    TODO("Implement reserve")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTextBlobBuilder::allocInternal(const SkFont& font,
   *                                       SkTextBlob::GlyphPositioning positioning,
   *                                       int count, int textSize, SkPoint offset,
   *                                       const SkRect* bounds) {
   *     if (count <= 0 || textSize < 0) {
   *         fCurrentRunBuffer = { nullptr, nullptr, nullptr, nullptr };
   *         return;
   *     }
   *
   *     if (textSize != 0 || !this->mergeRun(font, positioning, count, offset)) {
   *         this->updateDeferredBounds();
   *
   *         SkSafeMath safe;
   *         size_t runSize = SkTextBlob::RunRecord::StorageSize(count, textSize, positioning, &safe);
   *         if (!safe) {
   *             fCurrentRunBuffer = { nullptr, nullptr, nullptr, nullptr };
   *             return;
   *         }
   *
   *         this->reserve(runSize);
   *
   *         SkASSERT(fStorageUsed >= SkAlignPtr(sizeof(SkTextBlob)));
   *         SkASSERT(fStorageUsed + runSize <= fStorageSize);
   *
   *         SkTextBlob::RunRecord* run = new (fStorage.get() + fStorageUsed)
   *             SkTextBlob::RunRecord(count, textSize, offset, font, positioning);
   *         fCurrentRunBuffer.glyphs = run->glyphBuffer();
   *         fCurrentRunBuffer.pos = run->posBuffer();
   *         fCurrentRunBuffer.utf8text = run->textBuffer();
   *         fCurrentRunBuffer.clusters = run->clusterBuffer();
   *
   *         fLastRun = fStorageUsed;
   *         fStorageUsed += runSize;
   *         fRunCount++;
   *
   *         SkASSERT(fStorageUsed <= fStorageSize);
   *         run->validate(fStorage.get() + fStorageUsed);
   *     }
   *     SkASSERT(textSize > 0 || nullptr == fCurrentRunBuffer.utf8text);
   *     SkASSERT(textSize > 0 || nullptr == fCurrentRunBuffer.clusters);
   *     if (!fDeferredBounds) {
   *         if (bounds) {
   *             fBounds.join(*bounds);
   *         } else {
   *             fDeferredBounds = true;
   *         }
   *     }
   * }
   * ```
   */
  private fun allocInternal(
    font: SkFont,
    positioning: SkTextBlob.GlyphPositioning,
    count: Int,
    textBytes: Int,
    offset: SkPoint,
    bounds: SkRect?,
  ) {
    TODO("Implement allocInternal")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkTextBlobBuilder::mergeRun(const SkFont& font, SkTextBlob::GlyphPositioning positioning,
   *                                  uint32_t count, SkPoint offset) {
   *     if (0 == fLastRun) {
   *         SkASSERT(0 == fRunCount);
   *         return false;
   *     }
   *
   *     SkASSERT(fLastRun >= SkAlignPtr(sizeof(SkTextBlob)));
   *     SkTextBlob::RunRecord* run = reinterpret_cast<SkTextBlob::RunRecord*>(fStorage.get() +
   *                                                                           fLastRun);
   *     SkASSERT(run->glyphCount() > 0);
   *
   *     if (run->textSize() != 0) {
   *         return false;
   *     }
   *
   *     if (run->positioning() != positioning
   *         || run->font() != font
   *         || (run->glyphCount() + count < run->glyphCount())) {
   *         return false;
   *     }
   *
   *     // we can merge same-font/same-positioning runs in the following cases:
   *     //   * fully positioned run following another fully positioned run
   *     //   * horizontally postioned run following another horizontally positioned run with the same
   *     //     y-offset
   *     if (SkTextBlob::kFull_Positioning != positioning
   *         && (SkTextBlob::kHorizontal_Positioning != positioning
   *             || run->offset().y() != offset.y())) {
   *         return false;
   *     }
   *
   *     SkSafeMath safe;
   *     size_t sizeDelta =
   *         SkTextBlob::RunRecord::StorageSize(run->glyphCount() + count, 0, positioning, &safe) -
   *         SkTextBlob::RunRecord::StorageSize(run->glyphCount()        , 0, positioning, &safe);
   *     if (!safe) {
   *         return false;
   *     }
   *
   *     this->reserve(sizeDelta);
   *
   *     // reserve may have realloced
   *     run = reinterpret_cast<SkTextBlob::RunRecord*>(fStorage.get() + fLastRun);
   *     uint32_t preMergeCount = run->glyphCount();
   *     run->grow(count);
   *
   *     // Callers expect the buffers to point at the newly added slice, ant not at the beginning.
   *     fCurrentRunBuffer.glyphs = run->glyphBuffer() + preMergeCount;
   *     fCurrentRunBuffer.pos = run->posBuffer()
   *                           + preMergeCount * SkTextBlob::ScalarsPerGlyph(positioning);
   *
   *     fStorageUsed += sizeDelta;
   *
   *     SkASSERT(fStorageUsed <= fStorageSize);
   *     run->validate(fStorage.get() + fStorageUsed);
   *
   *     return true;
   * }
   * ```
   */
  private fun mergeRun(
    font: SkFont,
    positioning: SkTextBlob.GlyphPositioning,
    count: UInt,
    offset: SkPoint,
  ): Boolean {
    TODO("Implement mergeRun")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTextBlobBuilder::updateDeferredBounds() {
   *     SkASSERT(!fDeferredBounds || fRunCount > 0);
   *
   *     if (!fDeferredBounds) {
   *         return;
   *     }
   *
   *     SkASSERT(fLastRun >= SkAlignPtr(sizeof(SkTextBlob)));
   *     SkTextBlob::RunRecord* run = reinterpret_cast<SkTextBlob::RunRecord*>(fStorage.get() +
   *                                                                           fLastRun);
   *
   *     // FIXME: we should also use conservative bounds for kDefault_Positioning.
   *     SkRect runBounds = SkTextBlob::kDefault_Positioning == run->positioning() ?
   *                        TightRunBounds(*run) : ConservativeRunBounds(*run);
   *     fBounds.join(runBounds);
   *     fDeferredBounds = false;
   * }
   * ```
   */
  private fun updateDeferredBounds() {
    TODO("Implement updateDeferredBounds")
  }

  public data class RunBuffer public constructor(
    public var glyphs: Int?,
    public var pos: Int?,
    public var utf8text: String?,
    public var clusters: UInt?,
  ) {
    public fun points(): Int {
      TODO("Implement points")
    }

    public fun xforms(): Int {
      TODO("Implement xforms")
    }
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * SkRect SkTextBlobBuilder::ConservativeRunBounds(const SkTextBlob::RunRecord& run) {
     *     SkASSERT(run.glyphCount() > 0);
     *     SkASSERT(SkTextBlob::kFull_Positioning == run.positioning() ||
     *              SkTextBlob::kHorizontal_Positioning == run.positioning() ||
     *              SkTextBlob::kRSXform_Positioning == run.positioning());
     *
     *     const SkRect fontBounds = SkFontPriv::GetFontBounds(run.font());
     *     if (fontBounds.isEmpty()) {
     *         // Empty font bounds are likely a font bug.  TightBounds has a better chance of
     *         // producing useful results in this case.
     *         return TightRunBounds(run);
     *     }
     *
     *     // Compute the glyph position bbox.
     *     SkRect bounds;
     *     switch (run.positioning()) {
     *     case SkTextBlob::kHorizontal_Positioning: {
     *         const SkScalar* glyphPos = run.posBuffer();
     *         SkASSERT((void*)(glyphPos + run.glyphCount()) <= SkTextBlob::RunRecord::Next(&run));
     *
     *         SkScalar minX = *glyphPos;
     *         SkScalar maxX = *glyphPos;
     *         for (unsigned i = 1; i < run.glyphCount(); ++i) {
     *             SkScalar x = glyphPos[i];
     *             minX = std::min(x, minX);
     *             maxX = std::max(x, maxX);
     *         }
     *
     *         bounds.setLTRB(minX, 0, maxX, 0);
     *     } break;
     *     case SkTextBlob::kFull_Positioning: {
     *         const SkPoint* glyphPosPts = run.pointBuffer();
     *         SkASSERT((void*)(glyphPosPts + run.glyphCount()) <= SkTextBlob::RunRecord::Next(&run));
     *
     *         bounds = SkRect::BoundsOrEmpty({glyphPosPts, run.glyphCount()});
     *     } break;
     *     case SkTextBlob::kRSXform_Positioning: {
     *         const SkRSXform* xform = run.xformBuffer();
     *         SkASSERT((void*)(xform + run.glyphCount()) <= SkTextBlob::RunRecord::Next(&run));
     *         bounds.setEmpty();
     *         for (unsigned i = 0; i < run.glyphCount(); ++i) {
     *             bounds.join(map_quad_to_rect(xform[i], fontBounds));
     *         }
     *     } break;
     *     default:
     *         SK_ABORT("unsupported positioning mode");
     *     }
     *
     *     if (run.positioning() != SkTextBlob::kRSXform_Positioning) {
     *         // Expand by typeface glyph bounds.
     *         bounds.fLeft   += fontBounds.left();
     *         bounds.fTop    += fontBounds.top();
     *         bounds.fRight  += fontBounds.right();
     *         bounds.fBottom += fontBounds.bottom();
     *     }
     *
     *     // Offset by run position.
     *     return bounds.makeOffset(run.offset().x(), run.offset().y());
     * }
     * ```
     */
    private fun conservativeRunBounds(run: RunRecord): Int {
      TODO("Implement conservativeRunBounds")
    }

    /**
     * C++ original:
     * ```cpp
     * SkRect SkTextBlobBuilder::TightRunBounds(const SkTextBlob::RunRecord& run) {
     *     const SkFont& font = run.font();
     *     SkRect bounds;
     *
     *     if (SkTextBlob::kDefault_Positioning == run.positioning()) {
     *         font.measureText(run.glyphBuffer(), run.glyphCount() * sizeof(SkGlyphID),
     *                          SkTextEncoding::kGlyphID, &bounds);
     *         return bounds.makeOffset(run.offset().x(), run.offset().y());
     *     }
     *
     *     AutoSTArray<16, SkRect> glyphBounds(run.glyphCount());
     *     font.getBounds({run.glyphBuffer(), run.glyphCount()}, glyphBounds, nullptr);
     *
     *     if (SkTextBlob::kRSXform_Positioning == run.positioning()) {
     *         bounds.setEmpty();
     *         const SkRSXform* xform = run.xformBuffer();
     *         SkASSERT((void*)(xform + run.glyphCount()) <= SkTextBlob::RunRecord::Next(&run));
     *         for (unsigned i = 0; i < run.glyphCount(); ++i) {
     *             bounds.join(map_quad_to_rect(xform[i], glyphBounds[i]));
     *         }
     *     } else {
     *         SkASSERT(SkTextBlob::kFull_Positioning == run.positioning() ||
     *                  SkTextBlob::kHorizontal_Positioning == run.positioning());
     *         // kFull_Positioning       => [ x, y, x, y... ]
     *         // kHorizontal_Positioning => [ x, x, x... ]
     *         //                            (const y applied by runBounds.offset(run->offset()) later)
     *         const SkScalar horizontalConstY = 0;
     *         const SkScalar* glyphPosX = run.posBuffer();
     *         const SkScalar* glyphPosY = (run.positioning() == SkTextBlob::kFull_Positioning) ?
     *                                                         glyphPosX + 1 : &horizontalConstY;
     *         const unsigned posXInc = SkTextBlob::ScalarsPerGlyph(run.positioning());
     *         const unsigned posYInc = (run.positioning() == SkTextBlob::kFull_Positioning) ?
     *                                                     posXInc : 0;
     *
     *         bounds.setEmpty();
     *         for (unsigned i = 0; i < run.glyphCount(); ++i) {
     *             bounds.join(glyphBounds[i].makeOffset(*glyphPosX, *glyphPosY));
     *             glyphPosX += posXInc;
     *             glyphPosY += posYInc;
     *         }
     *
     *         SkASSERT((void*)glyphPosX <= SkTextBlob::RunRecord::Next(&run));
     *     }
     *     return bounds.makeOffset(run.offset().x(), run.offset().y());
     * }
     * ```
     */
    private fun tightRunBounds(run: RunRecord): Int {
      TODO("Implement tightRunBounds")
    }
  }
}
