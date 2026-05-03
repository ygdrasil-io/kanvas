package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.ULong
import kotlin.Unit
import kotlinx.atomicfu.AtomicRef
import org.skia.foundation.SkDeserialProcs
import org.skia.foundation.SkFont
import org.skia.foundation.SkGlyphID
import org.skia.foundation.SkNVRefCnt
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSerialProcs
import org.skia.foundation.SkSpan
import org.skia.foundation.SkTypeface
import org.skia.math.SkPoint
import org.skia.math.SkRSXform
import org.skia.math.SkRect
import org.skia.math.SkScalar
import undefined.RunRecord

/**
 * C++ original:
 * ```cpp
 * class SK_API SkTextBlob final : public SkNVRefCnt<SkTextBlob> {
 * private:
 *     class RunRecord;
 *
 * public:
 *
 *     /** Returns conservative bounding box. Uses SkPaint associated with each glyph to
 *         determine glyph bounds, and unions all bounds. Returned bounds may be
 *         larger than the bounds of all glyphs in runs.
 *
 *         @return  conservative bounding box
 *     */
 *     const SkRect& bounds() const { return fBounds; }
 *
 *     /** Returns a non-zero value unique among all text blobs.
 *
 *         @return  identifier for SkTextBlob
 *     */
 *     uint32_t uniqueID() const { return fUniqueID; }
 *
 *     /** Returns the number of intervals that intersect bounds.
 *         bounds describes a pair of lines parallel to the text advance.
 *         The return count is zero or a multiple of two, and is at most twice the number of glyphs in
 *         the the blob.
 *
 *         Pass nullptr for intervals to determine the size of the interval array.
 *
 *         Runs within the blob that contain SkRSXform are ignored when computing intercepts.
 *
 *         @param bounds     lower and upper line parallel to the advance
 *         @param intervals  returned intersections; may be nullptr
 *         @param paint      specifies stroking, SkPathEffect that affects the result; may be nullptr
 *         @return           number of intersections; may be zero
 *      */
 *     int getIntercepts(const SkScalar bounds[2], SkScalar intervals[],
 *                       const SkPaint* paint = nullptr) const;
 *
 *     /** Creates SkTextBlob with a single run.
 *
 *         font contains attributes used to define the run text.
 *
 *         When encoding is SkTextEncoding::kUTF8, SkTextEncoding::kUTF16, or
 *         SkTextEncoding::kUTF32, this function uses the default
 *         character-to-glyph mapping from the SkTypeface in font.  It does not
 *         perform typeface fallback for characters not found in the SkTypeface.
 *         It does not perform kerning or other complex shaping; glyphs are
 *         positioned based on their default advances.
 *
 *         @param text        character code points or glyphs drawn
 *         @param byteLength  byte length of text array
 *         @param font        text size, typeface, text scale, and so on, used to draw
 *         @param encoding    text encoding used in the text array
 *         @return            SkTextBlob constructed from one run
 *     */
 *     static sk_sp<SkTextBlob> MakeFromText(const void* text, size_t byteLength, const SkFont& font,
 *                                           SkTextEncoding encoding = SkTextEncoding::kUTF8);
 *
 *     /** Creates SkTextBlob with a single run. string meaning depends on SkTextEncoding;
 *         by default, string is encoded as UTF-8.
 *
 *         font contains attributes used to define the run text.
 *
 *         When encoding is SkTextEncoding::kUTF8, SkTextEncoding::kUTF16, or
 *         SkTextEncoding::kUTF32, this function uses the default
 *         character-to-glyph mapping from the SkTypeface in font.  It does not
 *         perform typeface fallback for characters not found in the SkTypeface.
 *         It does not perform kerning or other complex shaping; glyphs are
 *         positioned based on their default advances.
 *
 *         @param string   character code points or glyphs drawn
 *         @param font     text size, typeface, text scale, and so on, used to draw
 *         @param encoding text encoding used in the text array
 *         @return         SkTextBlob constructed from one run
 *     */
 *     static sk_sp<SkTextBlob> MakeFromString(const char* string, const SkFont& font,
 *                                             SkTextEncoding encoding = SkTextEncoding::kUTF8) {
 *         if (!string) {
 *             return nullptr;
 *         }
 *         return MakeFromText(string, strlen(string), font, encoding);
 *     }
 *
 *     /** Returns a textblob built from a single run of text with x-positions and a single y value.
 *         This is equivalent to using SkTextBlobBuilder and calling allocRunPosH().
 *         Returns nullptr if byteLength is zero.
 *
 *         @param text        character code points or glyphs drawn (based on encoding)
 *         @param byteLength  byte length of text array
 *         @param xpos    array of x-positions, must contain values for all of the character points.
 *         @param constY  shared y-position for each character point, to be paired with each xpos.
 *         @param font    SkFont used for this run
 *         @param encoding specifies the encoding of the text array.
 *         @return        new textblob or nullptr
 *      */
 *     static sk_sp<SkTextBlob> MakeFromPosTextH(const void* text, size_t byteLength,
 *                                               SkSpan<const SkScalar> xpos, SkScalar constY,
 *                                               const SkFont& font,
 *                                               SkTextEncoding encoding = SkTextEncoding::kUTF8);
 *
 *     /** Returns a textblob built from a single run of text with positions.
 *         This is equivalent to using SkTextBlobBuilder and calling allocRunPos().
 *         Returns nullptr if byteLength is zero.
 *
 *         @param text        character code points or glyphs drawn (based on encoding)
 *         @param byteLength  byte length of text array
 *         @param pos     array of positions, must contain values for all of the character points.
 *         @param font    SkFont used for this run
 *         @param encoding specifies the encoding of the text array.
 *         @return        new textblob or nullptr
 *      */
 *     static sk_sp<SkTextBlob> MakeFromPosText(const void* text, size_t byteLength,
 *                                              SkSpan<const SkPoint> pos, const SkFont& font,
 *                                              SkTextEncoding encoding = SkTextEncoding::kUTF8);
 *
 *     static sk_sp<SkTextBlob> MakeFromRSXform(const void* text, size_t byteLength,
 *                                              SkSpan<const SkRSXform> xform, const SkFont& font,
 *                                              SkTextEncoding encoding = SkTextEncoding::kUTF8);
 *
 *     // Helpers for glyphs
 *
 *     static sk_sp<SkTextBlob> MakeFromPosHGlyphs(SkSpan<const SkGlyphID> glyphs,
 *                                                 SkSpan<const SkScalar> xpos, SkScalar constY,
 *                                                 const SkFont& font) {
 *         return MakeFromPosTextH(glyphs.data(), glyphs.size() * sizeof(SkGlyphID), xpos, constY,
 *                                 font, SkTextEncoding::kGlyphID);
 *     }
 *     static sk_sp<SkTextBlob> MakeFromPosGlyphs(SkSpan<const SkGlyphID> glyphs,
 *                                                SkSpan<const SkPoint> pos, const SkFont& font) {
 *         return MakeFromPosText(glyphs.data(), glyphs.size() * sizeof(SkGlyphID), pos, font,
 *                                SkTextEncoding::kGlyphID);
 *     }
 *     static sk_sp<SkTextBlob> MakeFromRSXformGlyphs(SkSpan<const SkGlyphID> glyphs,
 *                                                    SkSpan<const SkRSXform> xform,
 *                                                    const SkFont& font) {
 *         return MakeFromRSXform(glyphs.data(), glyphs.size() * sizeof(SkGlyphID), xform, font,
 *                                SkTextEncoding::kGlyphID);
 *     }
 *
 *     /** Writes data to allow later reconstruction of SkTextBlob. memory points to storage
 *         to receive the encoded data, and memory_size describes the size of storage.
 *         Returns bytes used if provided storage is large enough to hold all data;
 *         otherwise, returns zero.
 *
 *         procs.fTypefaceProc permits supplying a custom function to encode SkTypeface.
 *         If procs.fTypefaceProc is nullptr, default encoding is used. procs.fTypefaceCtx
 *         may be used to provide user context to procs.fTypefaceProc; procs.fTypefaceProc
 *         is called with a pointer to SkTypeface and user context.
 *
 *         @param procs       custom serial data encoders; may be nullptr
 *         @param memory      storage for data
 *         @param memory_size size of storage
 *         @return            bytes written, or zero if required storage is larger than memory_size
 *
 *         example: https://fiddle.skia.org/c/@TextBlob_serialize
 *     */
 *     size_t serialize(const SkSerialProcs& procs, void* memory, size_t memory_size) const;
 *
 *     /** Returns storage containing SkData describing SkTextBlob, using optional custom
 *         encoders.
 *
 *         procs.fTypefaceProc permits supplying a custom function to encode SkTypeface.
 *         If procs.fTypefaceProc is nullptr, default encoding is used. procs.fTypefaceCtx
 *         may be used to provide user context to procs.fTypefaceProc; procs.fTypefaceProc
 *         is called with a pointer to SkTypeface and user context.
 *
 *         @param procs  custom serial data encoders; may be nullptr
 *         @return       storage containing serialized SkTextBlob
 *
 *         example: https://fiddle.skia.org/c/@TextBlob_serialize_2
 *     */
 *     sk_sp<SkData> serialize(const SkSerialProcs& procs) const;
 *
 *     /** Recreates SkTextBlob that was serialized into data. Returns constructed SkTextBlob
 *         if successful; otherwise, returns nullptr. Fails if size is smaller than
 *         required data length, or if data does not permit constructing valid SkTextBlob.
 *
 *         procs.fTypefaceProc permits supplying a custom function to decode SkTypeface.
 *         If procs.fTypefaceProc is nullptr, default decoding is used. procs.fTypefaceCtx
 *         may be used to provide user context to procs.fTypefaceProc; procs.fTypefaceProc
 *         is called with a pointer to SkTypeface data, data byte length, and user context.
 *
 *         @param data   pointer for serial data
 *         @param size   size of data
 *         @param procs  custom serial data decoders; may be nullptr
 *         @return       SkTextBlob constructed from data in memory
 *     */
 *     static sk_sp<SkTextBlob> Deserialize(const void* data, size_t size,
 *                                          const SkDeserialProcs& procs);
 *
 *     class SK_API Iter {
 *     public:
 *         struct Run {
 *             SkTypeface* fTypeface;
 *             int fGlyphCount;
 *             const SkGlyphID* fGlyphIndices;
 * #ifdef SK_UNTIL_CRBUG_1187654_IS_FIXED
 *             const uint32_t* fClusterIndex_forTest;
 *             int fUtf8Size_forTest;
 *             const char* fUtf8_forTest;
 * #endif
 *         };
 *
 *         Iter(const SkTextBlob&);
 *
 *         /**
 *          * Returns true for each "run" inside the textblob, setting the Run fields (if not null).
 *          * If this returns false, there are no more runs, and the Run parameter will be ignored.
 *          */
 *         bool next(Run*);
 *
 *         // Experimental, DO NO USE, will change/go-away
 *         struct ExperimentalRun {
 *             SkFont font;
 *             int count;
 *             const SkGlyphID* glyphs;
 *             const SkPoint* positions;
 *         };
 *         bool experimentalNext(ExperimentalRun*);
 *
 *     private:
 *         const RunRecord* fRunRecord;
 *     };
 *
 * #ifdef SK_SUPPORT_UNSPANNED_APIS
 *     static sk_sp<SkTextBlob> MakeFromPosTextH(const void* text, size_t byteLength,
 *                                               const SkScalar xpos[], SkScalar constY,
 *                                               const SkFont& font,
 *                                               SkTextEncoding encoding = SkTextEncoding::kUTF8) {
 *         const size_t worstCaseCount = byteLength;
 *         return MakeFromPosTextH(text, byteLength, {xpos, worstCaseCount}, constY, font, encoding);
 *     }
 *     static sk_sp<SkTextBlob> MakeFromPosText(const void* text, size_t byteLength,
 *                                              const SkPoint pos[], const SkFont& font,
 *                                              SkTextEncoding encoding = SkTextEncoding::kUTF8) {
 *         const size_t worstCaseCount = byteLength;
 *         return MakeFromPosText(text, byteLength, {pos, worstCaseCount}, font, encoding);
 *     }
 *     static sk_sp<SkTextBlob> MakeFromRSXform(const void* text, size_t byteLength,
 *                                               const SkRSXform xform[], const SkFont& font,
 *                                               SkTextEncoding encoding = SkTextEncoding::kUTF8) {
 *         const size_t worstCaseCount = byteLength;
 *         return MakeFromRSXform(text, byteLength, {xform, worstCaseCount}, font, encoding);
 *     }
 * #endif
 *
 * private:
 *     friend class SkNVRefCnt<SkTextBlob>;
 *
 *     enum GlyphPositioning : uint8_t;
 *
 *     explicit SkTextBlob(const SkRect& bounds);
 *
 *     ~SkTextBlob();
 *
 *     // Memory for objects of this class is created with sk_malloc rather than operator new and must
 *     // be freed with sk_free.
 *     void operator delete(void* p);
 *     void* operator new(size_t);
 *     void* operator new(size_t, void* p);
 *
 *     static unsigned ScalarsPerGlyph(GlyphPositioning pos);
 *
 *     using PurgeDelegate = void (*)(uint32_t blobID, uint32_t cacheID);
 *
 *     // Call when this blob is part of the key to a cache entry. This allows the cache
 *     // to know automatically those entries can be purged when this SkTextBlob is deleted.
 *     void notifyAddedToCache(uint32_t cacheID, PurgeDelegate purgeDelegate) const {
 *         fCacheID.store(cacheID);
 *         fPurgeDelegate.store(purgeDelegate);
 *     }
 *
 *     friend class sktext::GlyphRunList;
 *     friend class SkTextBlobBuilder;
 *     friend class SkTextBlobPriv;
 *     friend class SkTextBlobRunIterator;
 *
 *     const SkRect                  fBounds;
 *     const uint32_t                fUniqueID;
 *     mutable std::atomic<uint32_t> fCacheID;
 *     mutable std::atomic<PurgeDelegate> fPurgeDelegate;
 *
 *     SkDEBUGCODE(size_t fStorageSize;)
 *
 *     // The actual payload resides in externally-managed storage, following the object.
 *     // (see the .cpp for more details)
 *
 *     using INHERITED = SkRefCnt;
 * }
 * ```
 */
public class SkTextBlob public constructor(
  bounds: SkRect,
) : SkNVRefCnt(),
    SkTextBlob {
  /**
   * C++ original:
   * ```cpp
   * const SkRect                  fBounds
   * ```
   */
  private val fBounds: Int = TODO("Initialize fBounds")

  /**
   * C++ original:
   * ```cpp
   * const uint32_t                fUniqueID
   * ```
   */
  private val fUniqueID: UInt = TODO("Initialize fUniqueID")

  /**
   * C++ original:
   * ```cpp
   * mutable std::atomic<uint32_t> fCacheID
   * ```
   */
  private val fCacheID: AtomicRef<UInt> = TODO("Initialize fCacheID")

  /**
   * C++ original:
   * ```cpp
   * mutable std::atomic<PurgeDelegate> fPurgeDelegate
   * ```
   */
  private val fPurgeDelegate: AtomicRef<SkTextBlobPurgeDelegate> = TODO("Initialize fPurgeDelegate")

  /**
   * C++ original:
   * ```cpp
   * const SkRect& bounds() const { return fBounds; }
   * ```
   */
  public override fun bounds(): Int {
    TODO("Implement bounds")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t uniqueID() const { return fUniqueID; }
   * ```
   */
  public override fun uniqueID(): UInt {
    TODO("Implement uniqueID")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkTextBlob::getIntercepts(const SkScalar bounds[2], SkScalar intervals[],
   *                               const SkPaint* paint) const {
   *     std::optional<SkPaint> defaultPaint;
   *     if (paint == nullptr) {
   *         defaultPaint.emplace();
   *         paint = &defaultPaint.value();
   *     }
   *
   *     sktext::GlyphRunBuilder builder;
   *     auto glyphRunList = builder.blobToGlyphRunList(*this, {0, 0});
   *
   *     int intervalCount = 0;
   *     for (const sktext::GlyphRun& glyphRun : glyphRunList) {
   *         // Ignore RSXForm runs.
   *         if (glyphRun.scaledRotations().empty()) {
   *             intervalCount = get_glyph_run_intercepts(
   *                 glyphRun, *paint, bounds, intervals, &intervalCount);
   *         }
   *     }
   *
   *     return intervalCount;
   * }
   * ```
   */
  public override fun getIntercepts(
    bounds: Array<SkScalar>,
    intervals: Array<SkScalar>,
    paint: SkPaint? = null,
  ): Int {
    TODO("Implement getIntercepts")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkTextBlob::serialize(const SkSerialProcs& procs, void* memory, size_t memory_size) const {
   *     SkBinaryWriteBuffer buffer(memory, memory_size, procs);
   *     SkTextBlobPriv::Flatten(*this, buffer);
   *     return buffer.usingInitialStorage() ? buffer.bytesWritten() : 0u;
   * }
   * ```
   */
  public override fun serialize(
    procs: SkSerialProcs,
    memory: Unit?,
    memorySize: ULong,
  ): ULong {
    TODO("Implement serialize")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkData> SkTextBlob::serialize(const SkSerialProcs& procs) const {
   *     SkBinaryWriteBuffer buffer(procs);
   *     SkTextBlobPriv::Flatten(*this, buffer);
   *
   *     size_t total = buffer.bytesWritten();
   *     sk_sp<SkData> data = SkData::MakeUninitialized(total);
   *     buffer.writeToMemory(data->writable_data());
   *     return data;
   * }
   * ```
   */
  public override fun serialize(procs: SkSerialProcs): Int {
    TODO("Implement serialize")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTextBlob::operator delete(void* p) {
   *     sk_free(p);
   * }
   * ```
   */
  public override fun toDelete(p: Unit?) {
    TODO("Implement toDelete")
  }

  /**
   * C++ original:
   * ```cpp
   * void* SkTextBlob::operator new(size_t) {
   *     SK_ABORT("All blobs are created by placement new.");
   * }
   * ```
   */
  public override fun toNew(param0: ULong) {
    TODO("Implement toNew")
  }

  /**
   * C++ original:
   * ```cpp
   * void* SkTextBlob::operator new(size_t, void* p) {
   *     return p;
   * }
   * ```
   */
  public override fun toNew(param0: ULong, p: Unit?) {
    TODO("Implement toNew")
  }

  /**
   * C++ original:
   * ```cpp
   * void notifyAddedToCache(uint32_t cacheID, PurgeDelegate purgeDelegate) const {
   *         fCacheID.store(cacheID);
   *         fPurgeDelegate.store(purgeDelegate);
   *     }
   * ```
   */
  public override fun notifyAddedToCache(cacheID: UInt, purgeDelegate: SkTextBlobPurgeDelegate) {
    TODO("Implement notifyAddedToCache")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDEBUGCODE(size_t fStorageSize;)
   * ```
   */
  public override fun skDEBUGCODE(param0: ULong): Int {
    TODO("Implement skDEBUGCODE")
  }

  public open class Iter public constructor(
    blob: SkTextBlob,
  ) {
    private val fRunRecord: RunRecord? = TODO("Initialize fRunRecord")

    public fun next(rec: org.skia.core.Iter.Run?): Boolean {
      TODO("Implement next")
    }

    public fun experimentalNext(rec: org.skia.core.Iter.ExperimentalRun?): Boolean {
      TODO("Implement experimentalNext")
    }

    public data class Run public constructor(
      public var fTypeface: SkTypeface?,
      public var fGlyphCount: Int,
      public val fGlyphIndices: Int?,
    )

    public data class ExperimentalRun public constructor(
      public var font: Int,
      public var count: Int,
      public val glyphs: Int?,
      public val positions: Int?,
    )
  }

  public enum class GlyphPositioning

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkTextBlob> SkTextBlob::MakeFromText(const void* text, size_t byteLength, const SkFont& font,
     *                                            SkTextEncoding encoding) {
     *     // Note: we deliberately promote this to fully positioned blobs, since we'd have to pay the
     *     // same cost down stream (i.e. computing bounds), so its cheaper to pay the cost once now.
     *     const size_t count = font.countText(text, byteLength, encoding);
     *     if (count == 0) {
     *         return nullptr;
     *     }
     *     SkTextBlobBuilder builder;
     *     auto buffer = builder.allocRunPos(font, count);
     *     font.textToGlyphs(text, byteLength, encoding, {buffer.glyphs, count});
     *     font.getPos({buffer.glyphs, count}, {buffer.points(), count}, {0, 0});
     *     return builder.make();
     * }
     * ```
     */
    public override fun makeFromText(
      text: Unit?,
      byteLength: ULong,
      font: SkFont,
      encoding: SkTextEncoding = TODO(),
    ): Int {
      TODO("Implement makeFromText")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkTextBlob> MakeFromString(const char* string, const SkFont& font,
     *                                             SkTextEncoding encoding = SkTextEncoding::kUTF8) {
     *         if (!string) {
     *             return nullptr;
     *         }
     *         return MakeFromText(string, strlen(string), font, encoding);
     *     }
     * ```
     */
    public override fun makeFromString(
      string: String?,
      font: SkFont,
      encoding: SkTextEncoding = TODO(),
    ): Int {
      TODO("Implement makeFromString")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkTextBlob> SkTextBlob::MakeFromPosTextH(const void* text, size_t byteLength,
     *                                                SkSpan<const SkScalar> xpos, SkScalar constY,
     *                                                const SkFont& font, SkTextEncoding encoding) {
     *     const size_t count = font.countText(text, byteLength, encoding);
     *     if (count == 0 || xpos.size() < count) {
     *         return nullptr;
     *     }
     *     SkTextBlobBuilder builder;
     *     auto buffer = builder.allocRunPosH(font, count, constY);
     *     font.textToGlyphs(text, byteLength, encoding, {buffer.glyphs, count});
     *     memcpy(buffer.pos, xpos.data(), count * sizeof(SkScalar));
     *     return builder.make();
     * }
     * ```
     */
    public override fun makeFromPosTextH(
      text: Unit?,
      byteLength: ULong,
      xpos: SkSpan<SkScalar>,
      constY: SkScalar,
      font: SkFont,
      encoding: SkTextEncoding = TODO(),
    ): Int {
      TODO("Implement makeFromPosTextH")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkTextBlob> SkTextBlob::MakeFromPosText(const void* text, size_t byteLength,
     *                                               SkSpan<const SkPoint> pos, const SkFont& font,
     *                                               SkTextEncoding encoding) {
     *     const size_t count = font.countText(text, byteLength, encoding);
     *     if (count == 0 || pos.size() < count) {
     *         return nullptr;
     *     }
     *     SkTextBlobBuilder builder;
     *     auto buffer = builder.allocRunPos(font, count);
     *     font.textToGlyphs(text, byteLength, encoding, {buffer.glyphs, count});
     *     memcpy(buffer.points(), pos.data(), count * sizeof(SkPoint));
     *     return builder.make();
     * }
     * ```
     */
    public override fun makeFromPosText(
      text: Unit?,
      byteLength: ULong,
      pos: SkSpan<SkPoint>,
      font: SkFont,
      encoding: SkTextEncoding = TODO(),
    ): Int {
      TODO("Implement makeFromPosText")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkTextBlob> SkTextBlob::MakeFromRSXform(const void* text, size_t byteLength,
     *                                               SkSpan<const SkRSXform> xform, const SkFont& font,
     *                                               SkTextEncoding encoding) {
     *     const size_t count = font.countText(text, byteLength, encoding);
     *     if (count == 0 || xform.size() < count) {
     *         return nullptr;
     *     }
     *     SkTextBlobBuilder builder;
     *     auto buffer = builder.allocRunRSXform(font, count);
     *     font.textToGlyphs(text, byteLength, encoding, {buffer.glyphs, count});
     *     memcpy(buffer.xforms(), xform.data(), count * sizeof(SkRSXform));
     *     return builder.make();
     * }
     * ```
     */
    public override fun makeFromRSXform(
      text: Unit?,
      byteLength: ULong,
      xform: SkSpan<SkRSXform>,
      font: SkFont,
      encoding: SkTextEncoding = TODO(),
    ): Int {
      TODO("Implement makeFromRSXform")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkTextBlob> MakeFromPosHGlyphs(SkSpan<const SkGlyphID> glyphs,
     *                                                 SkSpan<const SkScalar> xpos, SkScalar constY,
     *                                                 const SkFont& font) {
     *         return MakeFromPosTextH(glyphs.data(), glyphs.size() * sizeof(SkGlyphID), xpos, constY,
     *                                 font, SkTextEncoding::kGlyphID);
     *     }
     * ```
     */
    public override fun makeFromPosHGlyphs(
      glyphs: SkSpan<SkGlyphID>,
      xpos: SkSpan<SkScalar>,
      constY: SkScalar,
      font: SkFont,
    ): Int {
      TODO("Implement makeFromPosHGlyphs")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkTextBlob> MakeFromPosGlyphs(SkSpan<const SkGlyphID> glyphs,
     *                                                SkSpan<const SkPoint> pos, const SkFont& font) {
     *         return MakeFromPosText(glyphs.data(), glyphs.size() * sizeof(SkGlyphID), pos, font,
     *                                SkTextEncoding::kGlyphID);
     *     }
     * ```
     */
    public override fun makeFromPosGlyphs(
      glyphs: SkSpan<SkGlyphID>,
      pos: SkSpan<SkPoint>,
      font: SkFont,
    ): Int {
      TODO("Implement makeFromPosGlyphs")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkTextBlob> MakeFromRSXformGlyphs(SkSpan<const SkGlyphID> glyphs,
     *                                                    SkSpan<const SkRSXform> xform,
     *                                                    const SkFont& font) {
     *         return MakeFromRSXform(glyphs.data(), glyphs.size() * sizeof(SkGlyphID), xform, font,
     *                                SkTextEncoding::kGlyphID);
     *     }
     * ```
     */
    public override fun makeFromRSXformGlyphs(
      glyphs: SkSpan<SkGlyphID>,
      xform: SkSpan<SkRSXform>,
      font: SkFont,
    ): Int {
      TODO("Implement makeFromRSXformGlyphs")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkTextBlob> SkTextBlob::Deserialize(const void* data, size_t length,
     *                                           const SkDeserialProcs& procs) {
     *     SkReadBuffer buffer(data, length);
     *     buffer.setDeserialProcs(procs);
     *     return SkTextBlobPriv::MakeFromBuffer(buffer);
     * }
     * ```
     */
    public override fun deserialize(
      `data`: Unit?,
      size: ULong,
      procs: SkDeserialProcs,
    ): Int {
      TODO("Implement deserialize")
    }

    /**
     * C++ original:
     * ```cpp
     * unsigned SkTextBlob::ScalarsPerGlyph(GlyphPositioning pos) {
     *     const uint8_t gScalarsPerPositioning[] = {
     *         0,  // kDefault_Positioning
     *         1,  // kHorizontal_Positioning
     *         2,  // kFull_Positioning
     *         4,  // kRSXform_Positioning
     *     };
     *     SkASSERT((unsigned)pos <= 3);
     *     return gScalarsPerPositioning[pos];
     * }
     * ```
     */
    public override fun scalarsPerGlyph(pos: GlyphPositioning): UInt {
      TODO("Implement scalarsPerGlyph")
    }
  }
}
