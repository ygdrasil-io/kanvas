package org.skia.utils

import kotlin.CharArray
import kotlin.ULong
import kotlin.Unit
import org.skia.core.SkCanvas
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkTextEncoding
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SK_API SkTextUtils {
 * public:
 *     enum Align {
 *         kLeft_Align,
 *         kCenter_Align,
 *         kRight_Align,
 *     };
 *
 *     static void Draw(SkCanvas*, const void* text, size_t size, SkTextEncoding,
 *                      SkScalar x, SkScalar y, const SkFont&, const SkPaint&, Align = kLeft_Align);
 *
 *     static void DrawString(SkCanvas* canvas, const char text[], SkScalar x, SkScalar y,
 *                            const SkFont& font, const SkPaint& paint, Align align = kLeft_Align) {
 *         Draw(canvas, text, strlen(text), SkTextEncoding::kUTF8, x, y, font, paint, align);
 *     }
 *
 *     static void GetPath(const void* text, size_t length, SkTextEncoding, SkScalar x, SkScalar y,
 *                         const SkFont&, SkPath*);
 * }
 * ```
 */
public open class SkTextUtils {
  public enum class Align {
    kLeft_Align,
    kCenter_Align,
    kRight_Align,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * void SkTextUtils::Draw(SkCanvas* canvas, const void* text, size_t size, SkTextEncoding encoding,
     *                        SkScalar x, SkScalar y, const SkFont& font, const SkPaint& paint,
     *                        Align align) {
     *     if (align != kLeft_Align) {
     *         SkScalar width = font.measureText(text, size, encoding);
     *         if (align == kCenter_Align) {
     *             width *= 0.5f;
     *         }
     *         x -= width;
     *     }
     *
     *     canvas->drawTextBlob(SkTextBlob::MakeFromText(text, size, font, encoding), x, y, paint);
     * }
     * ```
     */
    public fun draw(
      canvas: SkCanvas?,
      text: Unit?,
      size: ULong,
      encoding: SkTextEncoding,
      x: SkScalar,
      y: SkScalar,
      font: SkFont,
      paint: SkPaint,
      align: Align = TODO(),
    ) {
      TODO("Implement draw")
    }

    /**
     * C++ original:
     * ```cpp
     * static void DrawString(SkCanvas* canvas, const char text[], SkScalar x, SkScalar y,
     *                            const SkFont& font, const SkPaint& paint, Align align = kLeft_Align) {
     *         Draw(canvas, text, strlen(text), SkTextEncoding::kUTF8, x, y, font, paint, align);
     *     }
     * ```
     */
    public fun drawString(
      canvas: SkCanvas?,
      text: CharArray,
      x: SkScalar,
      y: SkScalar,
      font: SkFont,
      paint: SkPaint,
      align: Align = TODO(),
    ) {
      TODO("Implement drawString")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkTextUtils::GetPath(const void* text, size_t length, SkTextEncoding encoding,
     *                           SkScalar x, SkScalar y, const SkFont& font, SkPath* path) {
     *     SkAutoToGlyphs ag(font, text, length, encoding);
     *     AutoTArray<SkPoint> pos(ag.count());
     *     font.getPos(ag.glyphs(), pos, {x, y});
     *
     *     struct Rec {
     *         SkPathBuilder fDst;
     *         const SkPoint* fPos;
     *     } rec = { {}, pos.get() };
     *
     *     font.getPaths(ag.glyphs(), [](const SkPath* src, const SkMatrix& mx, void* ctx) {
     *         Rec* rec = (Rec*)ctx;
     *         if (src) {
     *             SkMatrix m(mx);
     *             m.postTranslate(rec->fPos->fX, rec->fPos->fY);
     *             rec->fDst.addPath(*src, m);
     *         }
     *         rec->fPos += 1;
     *     }, &rec);
     *     *path = rec.fDst.detach();
     * }
     * ```
     */
    public fun getPath(
      text: Unit?,
      length: ULong,
      encoding: SkTextEncoding,
      x: SkScalar,
      y: SkScalar,
      font: SkFont,
      path: SkPath?,
    ) {
      TODO("Implement getPath")
    }
  }
}
