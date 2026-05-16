package org.skia.core

import kotlin.Boolean
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer

/**
 * C++ original:
 * ```cpp
 * class SkTextBlobPriv {
 * public:
 *     /**
 *      *  Serialize to a buffer.
 *      */
 *     static void Flatten(const SkTextBlob& , SkWriteBuffer&);
 *
 *     /**
 *      *  Recreate an SkTextBlob that was serialized into a buffer.
 *      *
 *      *  @param  SkReadBuffer Serialized blob data.
 *      *  @return A new SkTextBlob representing the serialized data, or NULL if the buffer is
 *      *          invalid.
 *      */
 *     static sk_sp<SkTextBlob> MakeFromBuffer(SkReadBuffer&);
 *
 *     static bool HasRSXForm(const SkTextBlob& blob);
 * }
 * ```
 */
public open class SkTextBlobPriv {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * void SkTextBlobPriv::Flatten(const SkTextBlob& blob, SkWriteBuffer& buffer) {
     *     // seems like we could skip this, and just recompute bounds in unflatten, but
     *     // some cc_unittests fail if we remove this...
     *     buffer.writeRect(blob.bounds());
     *
     *     SkTextBlobRunIterator it(&blob);
     *     while (!it.done()) {
     *         SkASSERT(it.glyphCount() > 0);
     *
     *         buffer.write32(it.glyphCount());
     *         PositioningAndExtended pe;
     *         pe.intValue = 0;
     *         pe.positioning = it.positioning();
     *         SkASSERT((int32_t)it.positioning() == pe.intValue);  // backwards compat.
     *
     *         uint32_t textSize = it.textSize();
     *         pe.extended = textSize > 0;
     *         buffer.write32(pe.intValue);
     *         if (pe.extended) {
     *             buffer.write32(textSize);
     *         }
     *         buffer.writePoint(it.offset());
     *
     *         SkFontPriv::Flatten(it.font(), buffer);
     *
     *         buffer.writeByteArray(it.glyphs(), it.glyphCount() * sizeof(SkGlyphID));
     *         buffer.writeByteArray(it.pos(),
     *                               it.glyphCount() * sizeof(SkScalar) *
     *                               SkTextBlob::ScalarsPerGlyph(
     *                                   SkTo<SkTextBlob::GlyphPositioning>(it.positioning())));
     *         if (pe.extended) {
     *             buffer.writeByteArray(it.clusters(), sizeof(uint32_t) * it.glyphCount());
     *             buffer.writeByteArray(it.text(), it.textSize());
     *         }
     *
     *         it.next();
     *     }
     *
     *     // Marker for the last run (0 is not a valid glyph count).
     *     buffer.write32(0);
     * }
     * ```
     */
    public fun flatten(blob: SkTextBlob, buffer: SkWriteBuffer) {
      TODO("Implement flatten")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkTextBlob> SkTextBlobPriv::MakeFromBuffer(SkReadBuffer& reader) {
     *     SkRect bounds;
     *     reader.readRect(&bounds);
     *
     *     SkTextBlobBuilder blobBuilder;
     *     SkSafeMath safe;
     *     for (;;) {
     *         int glyphCount = reader.read32();
     *         if (glyphCount == 0) {
     *             // End-of-runs marker.
     *             break;
     *         }
     *
     *         PositioningAndExtended pe;
     *         pe.intValue = reader.read32();
     *         const auto pos = SkTo<SkTextBlob::GlyphPositioning>(pe.positioning);
     *         if (glyphCount <= 0 || pos > SkTextBlob::kRSXform_Positioning) {
     *             return nullptr;
     *         }
     *         int textSize = pe.extended ? reader.read32() : 0;
     *         if (textSize < 0) {
     *             return nullptr;
     *         }
     *
     *         SkPoint offset;
     *         reader.readPoint(&offset);
     *         SkFont font;
     *         SkFontPriv::Unflatten(&font, reader);
     *
     *         // Compute the expected size of the buffer and ensure we have enough to deserialize
     *         // a run before allocating it.
     *         const size_t glyphSize = safe.mul(glyphCount, sizeof(SkGlyphID)),
     *                      posSize =
     *                              safe.mul(glyphCount, safe.mul(sizeof(SkScalar),
     *                              SkTextBlob::ScalarsPerGlyph(pos))),
     *                      clusterSize = pe.extended ? safe.mul(glyphCount, sizeof(uint32_t)) : 0;
     *         const size_t totalSize =
     *                 safe.add(safe.add(glyphSize, posSize), safe.add(clusterSize, textSize));
     *
     *         if (!reader.isValid() || !safe || totalSize > reader.available()) {
     *             return nullptr;
     *         }
     *
     *         const SkTextBlobBuilder::RunBuffer* buf = nullptr;
     *         switch (pos) {
     *             case SkTextBlob::kDefault_Positioning:
     *                 buf = &blobBuilder.allocRunText(font, glyphCount, offset.x(), offset.y(),
     *                                                 textSize, &bounds);
     *                 break;
     *             case SkTextBlob::kHorizontal_Positioning:
     *                 buf = &blobBuilder.allocRunTextPosH(font, glyphCount, offset.y(),
     *                                                     textSize, &bounds);
     *                 break;
     *             case SkTextBlob::kFull_Positioning:
     *                 buf = &blobBuilder.allocRunTextPos(font, glyphCount, textSize, &bounds);
     *                 break;
     *             case SkTextBlob::kRSXform_Positioning:
     *                 buf = &blobBuilder.allocRunTextRSXform(font, glyphCount, textSize, &bounds);
     *                 break;
     *         }
     *
     *         if (!buf->glyphs ||
     *             !buf->pos ||
     *             (pe.extended && (!buf->clusters || !buf->utf8text))) {
     *             return nullptr;
     *         }
     *
     *         if (!reader.readByteArray(buf->glyphs, glyphSize) ||
     *             !reader.readByteArray(buf->pos, posSize)) {
     *             return nullptr;
     *             }
     *
     *         if (pe.extended) {
     *             if (!reader.readByteArray(buf->clusters, clusterSize) ||
     *                 !reader.readByteArray(buf->utf8text, textSize)) {
     *                 return nullptr;
     *             }
     *         }
     *     }
     *
     *     return blobBuilder.make();
     * }
     * ```
     */
    public fun makeFromBuffer(reader: SkReadBuffer): SkSp<SkTextBlob> {
      TODO("Implement makeFromBuffer")
    }

    /**
     * C++ original:
     * ```cpp
     * static bool HasRSXForm(const SkTextBlob& blob)
     * ```
     */
    public fun hasRSXForm(blob: SkTextBlob): Boolean {
      TODO("Implement hasRSXForm")
    }
  }
}
