package org.skia.gpu

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import kotlin.Unit
import org.skia.core.GlyphRunList
import org.skia.core.SkCanvas
import org.skia.core.SkStrikeDeviceInfo
import org.skia.core.StrikeForGPUCacheInterface
import org.skia.foundation.SkColor
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRefCnt
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import undefined.AtlasDrawDelegate
import undefined.SubRunContainerOwner

/**
 * C++ original:
 * ```cpp
 * class TextBlob final : public SkRefCnt {
 * public:
 *     // Key is not used as part of a hash map, so the hash is never taken. It's only used in a
 *     // list search using operator =().
 *     struct Key {
 *         static std::tuple<bool, Key> Make(const GlyphRunList& glyphRunList,
 *                                           const SkPaint& paint,
 *                                           const SkMatrix& drawMatrix,
 *                                           const SkStrikeDeviceInfo& strikeDevice);
 *         uint32_t fUniqueID;
 *         // Color may affect the gamma of the mask we generate, but in a fairly limited way.
 *         // Each color is assigned to on of a fixed number of buckets based on its
 *         // luminance. For each luminance bucket there is a "canonical color" that
 *         // represents the bucket.  This functionality is currently only supported for A8
 *         SkColor fCanonicalColor;
 *         SkScalar fFrameWidth;
 *         SkScalar fMiterLimit;
 *         SkPixelGeometry fPixelGeometry;
 *         SkMaskFilterBase::BlurRec fBlurRec;
 *         uint32_t fScalerContextFlags;
 *         SkMatrix fPositionMatrix;
 *         // Below here fields are of size 1 byte.
 *         bool fHasSomeDirectSubRuns;
 *         bool fHasBlur;
 *         SkPaint::Style fStyle;
 *         SkPaint::Join fJoin;
 *
 *         bool operator==(const Key& other) const;
 *     };
 *
 *     SK_DECLARE_INTERNAL_LLIST_INTERFACE(TextBlob);
 *
 *     // Make a TextBlob and its sub runs.
 *     static sk_sp<TextBlob> Make(const sktext::GlyphRunList& glyphRunList,
 *                                 const SkPaint& paint,
 *                                 const SkMatrix& positionMatrix,
 *                                 SkStrikeDeviceInfo strikeDeviceInfo,
 *                                 StrikeForGPUCacheInterface* strikeCache);
 *
 *     TextBlob(SubRunAllocator&& alloc,
 *              SubRunContainerOwner subRuns,
 *              int totalMemorySize,
 *              SkColor initialLuminance);
 *
 *     ~TextBlob() override;
 *
 *     // Change memory management to handle the data after TextBlob, but in the same allocation
 *     // of memory. Only allow placement new.
 *     void operator delete(void* p);
 *     void* operator new(size_t);
 *     void* operator new(size_t, void* p);
 *
 *     const Key& key() { return fKey; }
 *
 *     void addKey(const Key& key);
 *
 *     bool canReuse(const SkPaint& paint, const SkMatrix& positionMatrix) const;
 *
 *     const Key& key() const;
 *     size_t size() const { return SkTo<size_t>(fSize); }
 *
 *     void draw(SkCanvas*,
 *               SkPoint drawOrigin,
 *               const SkPaint& paint,
 *               const AtlasDrawDelegate&);
 *
 * private:
 *     friend class TextBlobTools;
 *     // The allocator must come first because it needs to be destroyed last. Other fields of this
 *     // structure may have pointers into it.
 *     SubRunAllocator fAlloc;
 *
 *     SubRunContainerOwner fSubRuns;
 *
 *     // Overall size of this struct plus vertices and glyphs at the end.
 *     const int fSize;
 *
 *     const SkColor fInitialLuminance;
 *
 *     Key fKey;
 * }
 * ```
 */
public class TextBlob public constructor(
  alloc: SubRunAllocator,
  subRuns: SubRunContainerOwner,
  totalMemorySize: Int,
  initialLuminance: SkColor,
) : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * SubRunAllocator fAlloc
   * ```
   */
  private var fAlloc: Int = TODO("Initialize fAlloc")

  /**
   * C++ original:
   * ```cpp
   * SubRunContainerOwner fSubRuns
   * ```
   */
  private var fSubRuns: Int = TODO("Initialize fSubRuns")

  /**
   * C++ original:
   * ```cpp
   * const int fSize
   * ```
   */
  private val fSize: Int = TODO("Initialize fSize")

  /**
   * C++ original:
   * ```cpp
   * const SkColor fInitialLuminance
   * ```
   */
  private val fInitialLuminance: Int = TODO("Initialize fInitialLuminance")

  /**
   * C++ original:
   * ```cpp
   * Key fKey
   * ```
   */
  private var fKey: Key = TODO("Initialize fKey")

  /**
   * C++ original:
   * ```cpp
   * void TextBlob::operator delete(void* p) { ::operator delete(p); }
   * ```
   */
  public fun toDelete(p: Unit?) {
    TODO("Implement toDelete")
  }

  /**
   * C++ original:
   * ```cpp
   * void* TextBlob::operator new(size_t) { SK_ABORT("All blobs are created by placement new."); }
   * ```
   */
  public fun toNew(param0: ULong) {
    TODO("Implement toNew")
  }

  /**
   * C++ original:
   * ```cpp
   * void* TextBlob::operator new(size_t, void* p) { return p; }
   * ```
   */
  public fun toNew(param0: ULong, p: Unit?) {
    TODO("Implement toNew")
  }

  /**
   * C++ original:
   * ```cpp
   * const Key& key() { return fKey; }
   * ```
   */
  public fun key(): Key {
    TODO("Implement key")
  }

  /**
   * C++ original:
   * ```cpp
   * void TextBlob::addKey(const Key& key) {
   *     fKey = key;
   * }
   * ```
   */
  public fun addKey(key: Key) {
    TODO("Implement addKey")
  }

  /**
   * C++ original:
   * ```cpp
   * bool TextBlob::canReuse(const SkPaint& paint, const SkMatrix& positionMatrix) const {
   *     // A singular matrix will create a TextBlob with no SubRuns, but unknown glyphs can also
   *     // cause empty runs. If there are no subRuns, then regenerate when the matrices don't match.
   *     if (fSubRuns->isEmpty() && fSubRuns->initialPosition() != positionMatrix) {
   *         return false;
   *     }
   *
   *     // If we have LCD text then our canonical color will be set to transparent, in this case we have
   *     // to regenerate the blob on any color change
   *     // We use the grPaint to get any color filter effects
   *     if (fKey.fCanonicalColor == SK_ColorTRANSPARENT &&
   *         fInitialLuminance != SkPaintPriv::ComputeLuminanceColor(paint)) {
   *         return false;
   *     }
   *
   *     return fSubRuns->canReuse(paint, positionMatrix);
   * }
   * ```
   */
  public fun canReuse(paint: SkPaint, positionMatrix: SkMatrix): Boolean {
    TODO("Implement canReuse")
  }

  /**
   * C++ original:
   * ```cpp
   * const Key& key() const
   * ```
   */
  public fun size(): Int {
    TODO("Implement size")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t size() const { return SkTo<size_t>(fSize); }
   * ```
   */
  public fun draw(
    canvas: SkCanvas?,
    drawOrigin: SkPoint,
    paint: SkPaint,
    atlasDelegate: AtlasDrawDelegate,
  ) {
    TODO("Implement draw")
  }

  public data class Key public constructor(
    public var fUniqueID: Int,
    public var fCanonicalColor: Int,
    public var fFrameWidth: Int,
    public var fMiterLimit: Int,
    public var fPixelGeometry: Int,
    public var fBlurRec: Int,
    public var fScalerContextFlags: Int,
    public var fPositionMatrix: Int,
    public var fHasSomeDirectSubRuns: Boolean,
    public var fHasBlur: Boolean,
    public var fStyle: Int,
    public var fJoin: Int,
  ) {
    public override operator fun equals(other: Any?): Boolean {
      TODO("Implement equals")
    }

    public companion object {
      public fun make(
        glyphRunList: GlyphRunList,
        paint: SkPaint,
        drawMatrix: SkMatrix,
        strikeDevice: SkStrikeDeviceInfo,
      ): Int {
        TODO("Implement make")
      }
    }
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<TextBlob> TextBlob::Make(const GlyphRunList& glyphRunList,
     *                                const SkPaint& paint,
     *                                const SkMatrix& positionMatrix,
     *                                SkStrikeDeviceInfo strikeDeviceInfo,
     *                                StrikeForGPUCacheInterface* strikeCache) {
     *     size_t subRunSizeHint = SubRunContainer::EstimateAllocSize(glyphRunList);
     *     auto [initializer, totalMemoryAllocated, alloc] =
     *             SubRunAllocator::AllocateClassMemoryAndArena<TextBlob>(subRunSizeHint);
     *
     *     auto container = SubRunContainer::MakeInAlloc(
     *             glyphRunList, positionMatrix, paint,
     *             strikeDeviceInfo, strikeCache, &alloc, SubRunContainer::kAddSubRuns, "TextBlob");
     *
     *     SkColor initialLuminance = SkPaintPriv::ComputeLuminanceColor(paint);
     *     sk_sp<TextBlob> blob = sk_sp<TextBlob>(initializer.initialize(std::move(alloc),
     *                                                                   std::move(container),
     *                                                                   totalMemoryAllocated,
     *                                                                   initialLuminance));
     *     return blob;
     * }
     * ```
     */
    public fun make(
      glyphRunList: GlyphRunList,
      paint: SkPaint,
      positionMatrix: SkMatrix,
      strikeDeviceInfo: SkStrikeDeviceInfo,
      strikeCache: StrikeForGPUCacheInterface?,
    ): Int {
      TODO("Implement make")
    }
  }
}
