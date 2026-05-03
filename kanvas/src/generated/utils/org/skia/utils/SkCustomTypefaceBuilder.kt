package org.skia.utils

import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import kotlin.collections.List
import org.skia.core.SkFontMetrics
import org.skia.foundation.SkDrawable
import org.skia.foundation.SkFontArguments
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkGlyphID
import org.skia.foundation.SkPath
import org.skia.foundation.SkSp
import org.skia.foundation.SkStream
import org.skia.foundation.SkStreamAsset
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class SK_API SkCustomTypefaceBuilder {
 * public:
 *     SkCustomTypefaceBuilder();
 *
 *     void setGlyph(SkGlyphID, float advance, const SkPath&);
 *     void setGlyph(SkGlyphID, float advance, sk_sp<SkDrawable>, const SkRect& bounds);
 *
 *     void setMetrics(const SkFontMetrics& fm, float scale = 1);
 *     void setFontStyle(SkFontStyle);
 *
 *     sk_sp<SkTypeface> detach();
 *
 *     static constexpr SkTypeface::FactoryId FactoryId = SkSetFourByteTag('u','s','e','r');
 *     static sk_sp<SkTypeface> MakeFromStream(std::unique_ptr<SkStreamAsset>, const SkFontArguments&);
 *
 * private:
 *     struct GlyphRec {
 *         // logical union
 *         SkPath            fPath;
 *         sk_sp<SkDrawable> fDrawable;
 *
 *         SkRect            fBounds  = {0,0,0,0}; // only used for drawable glyphs atm
 *         float             fAdvance = 0;
 *
 *         bool isDrawable() const {
 *             SkASSERT(!fDrawable || fPath.isEmpty());
 *             return fDrawable != nullptr;
 *         }
 *     };
 *
 *     std::vector<GlyphRec> fGlyphRecs;
 *     SkFontMetrics         fMetrics;
 *     SkFontStyle           fStyle;
 *
 *     GlyphRec& ensureStorage(SkGlyphID);
 *
 *     static sk_sp<SkTypeface> Deserialize(SkStream*);
 *
 *     friend class SkTypeface;
 *     friend class SkUserTypeface;
 * }
 * ```
 */
public data class SkCustomTypefaceBuilder public constructor(
  /**
   * C++ original:
   * ```cpp
   * static constexpr SkTypeface::FactoryId FactoryId = SkSetFourByteTag('u','s','e','r')
   * ```
   */
  private var fGlyphRecs: List<GlyphRec>,
  /**
   * C++ original:
   * ```cpp
   * std::vector<GlyphRec> fGlyphRecs
   * ```
   */
  private var fMetrics: Int,
  /**
   * C++ original:
   * ```cpp
   * SkFontMetrics         fMetrics
   * ```
   */
  private var fStyle: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * void SkCustomTypefaceBuilder::setGlyph(SkGlyphID index, float advance, const SkPath& path) {
   *     auto& rec = this->ensureStorage(index);
   *     rec.fAdvance  = advance;
   *     rec.fPath     = path;
   *     rec.fDrawable = nullptr;
   * }
   * ```
   */
  public fun setGlyph(
    index: SkGlyphID,
    advance: Float,
    path: SkPath,
  ) {
    TODO("Implement setGlyph")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCustomTypefaceBuilder::setGlyph(SkGlyphID index, float advance,
   *                                        sk_sp<SkDrawable> drawable, const SkRect& bounds) {
   *     auto& rec = this->ensureStorage(index);
   *     rec.fAdvance  = advance;
   *     rec.fDrawable = std::move(drawable);
   *     rec.fBounds   = bounds;
   *     rec.fPath     = SkPath();
   * }
   * ```
   */
  public fun setGlyph(
    index: SkGlyphID,
    advance: Float,
    drawable: SkSp<SkDrawable>,
    bounds: SkRect,
  ) {
    TODO("Implement setGlyph")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCustomTypefaceBuilder::setMetrics(const SkFontMetrics& fm, float scale) {
   *     fMetrics = scale_fontmetrics(fm, scale, scale);
   * }
   * ```
   */
  public fun setMetrics(fm: SkFontMetrics, scale: Float = TODO()) {
    TODO("Implement setMetrics")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCustomTypefaceBuilder::setFontStyle(SkFontStyle style) {
   *     fStyle = style;
   * }
   * ```
   */
  public fun setFontStyle(style: SkFontStyle) {
    TODO("Implement setFontStyle")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> SkCustomTypefaceBuilder::detach() {
   *     if (fGlyphRecs.empty()) return nullptr;
   *
   *     // initially inverted, so that any "union" will overwrite the first time
   *     SkRect bounds = {SK_ScalarMax, SK_ScalarMax, -SK_ScalarMax, -SK_ScalarMax};
   *
   *     for (const auto& rec : fGlyphRecs) {
   *         bounds.join(rec.isDrawable()
   *                         ? rec.fBounds
   *                         : rec.fPath.getBounds());
   *     }
   *
   *     fMetrics.fTop    = bounds.top();
   *     fMetrics.fBottom = bounds.bottom();
   *     fMetrics.fXMin   = bounds.left();
   *     fMetrics.fXMax   = bounds.right();
   *
   *     return sk_sp<SkUserTypeface>(new SkUserTypeface(fStyle, fMetrics, std::move(fGlyphRecs)));
   * }
   * ```
   */
  public fun detach(): Int {
    TODO("Implement detach")
  }

  /**
   * C++ original:
   * ```cpp
   * SkCustomTypefaceBuilder::GlyphRec& SkCustomTypefaceBuilder::ensureStorage(SkGlyphID index) {
   *     if (index >= fGlyphRecs.size()) {
   *            fGlyphRecs.resize(SkToSizeT(index) + 1);
   *     }
   *
   *     return fGlyphRecs[index];
   * }
   * ```
   */
  private fun ensureStorage(index: SkGlyphID): GlyphRec {
    TODO("Implement ensureStorage")
  }

  public data class GlyphRec public constructor(
    public var fPath: Int,
    public var fDrawable: Int,
    public var fBounds: Int,
    public var fAdvance: Float,
  ) {
    public fun isDrawable(): Boolean {
      TODO("Implement isDrawable")
    }
  }

  public companion object {
    public val factoryId: Int = TODO("Initialize factoryId")

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkTypeface> MakeFromStream(std::unique_ptr<SkStreamAsset>, const SkFontArguments&)
     * ```
     */
    public fun makeFromStream(param0: SkStreamAsset?, param1: SkFontArguments): Int {
      TODO("Implement makeFromStream")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkTypeface> SkCustomTypefaceBuilder::Deserialize(SkStream* stream) {
     *     AutoRestorePosition arp(stream);
     *
     *     char header[kHeaderSize];
     *     if (stream->read(header, kHeaderSize) != kHeaderSize ||
     *         0 != memcmp(header, gHeaderString, kHeaderSize))
     *     {
     *         return nullptr;
     *     }
     *
     *     SkFontMetrics metrics;
     *     if (stream->read(&metrics, sizeof(metrics)) != sizeof(metrics)) {
     *         return nullptr;
     *     }
     *
     *     SkFontStyle style;
     *     if (stream->read(&style, sizeof(style)) != sizeof(style)) {
     *         return nullptr;
     *     }
     *
     *     int glyphCount;
     *     if (!stream->readS32(&glyphCount) || glyphCount < 0 || glyphCount > kMaxGlyphCount) {
     *         return nullptr;
     *     }
     *
     *     SkCustomTypefaceBuilder builder;
     *
     *     builder.setMetrics(metrics);
     *     builder.setFontStyle(style);
     *
     *     for (int i = 0; i < glyphCount; ++i) {
     *         uint32_t gtype;
     *         if (!stream->readU32(&gtype) ||
     *             (gtype != GlyphType::kDrawable && gtype != GlyphType::kPath)) {
     *             return nullptr;
     *         }
     *
     *         float advance;
     *         if (!stream->readScalar(&advance)) {
     *             return nullptr;
     *         }
     *
     *         SkRect bounds;
     *         if (stream->read(&bounds, sizeof(bounds)) != sizeof(bounds) || !bounds.isFinite()) {
     *             return nullptr;
     *         }
     *
     *         // SkPath and SkDrawable cannot read from a stream, so we have to page them into ram
     *         size_t sz;
     *         if (stream->read(&sz, sizeof(sz)) != sizeof(sz)) {
     *             return nullptr;
     *         }
     *
     *         // The amount of bytes in the stream must be at least as big as sz, otherwise
     *         // sz is invalid.
     *         if (SkStreamPriv::RemainingLengthIsBelow(stream, sz)) {
     *             return nullptr;
     *         }
     *
     *         auto data = SkData::MakeUninitialized(sz);
     *         if (stream->read(data->writable_data(), sz) != sz) {
     *             return nullptr;
     *         }
     *
     *         switch (gtype) {
     *             case GlyphType::kDrawable: {
     *                 SkDeserialProcs procs;
     *                 procs.fAllowSkSL = false;
     *                 auto drawable = SkDrawable::Deserialize(data->data(), data->size(), &procs);
     *                 if (!drawable) {
     *                     return nullptr;
     *                 }
     *                 builder.setGlyph(i, advance, std::move(drawable), bounds);
     *             } break;
     *             case GlyphType::kPath: {
     *                 size_t bytesRead = 0;
     *                 auto path = SkPath::ReadFromMemory(data->data(), data->size(), &bytesRead);
     *                 if (path.has_value() && (bytesRead == data->size())) {
     *                     builder.setGlyph(i, advance, *path);
     *                 } else {
     *                     return nullptr;
     *                 }
     *             } break;
     *             default:
     *                 return nullptr;
     *         }
     *     }
     *
     *     arp.markDone();
     *     return builder.detach();
     * }
     * ```
     */
    private fun deserialize(stream: SkStream?): Int {
      TODO("Implement deserialize")
    }
  }
}
