package org.skia.utils

import org.skia.core.SkCanvas
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTextEncoding
import org.graphiks.math.SkScalar

/**
 * Mirrors Skia's `SkTextUtils` (`include/utils/SkTextUtils.h` /
 * `src/utils/SkTextUtils.cpp`).
 *
 * The only entry point used by the GMs we mirror today is
 * [DrawString] — a horizontal-alignment-aware wrapper over
 * [SkCanvas.drawSimpleText]. Upstream measures the glyph run via
 * `font.measureText` then shifts the origin so the run is left/center/
 * right-aligned around the supplied `(x, y)`.
 */
public object SkTextUtils {

    /** Mirrors `SkTextUtils::Align` in `include/utils/SkTextUtils.h`. */
    public enum class Align { kLeft_Align, kCenter_Align, kRight_Align }

    /**
     * Mirrors `SkTextUtils::Draw(canvas, text, size, encoding, x, y, font,
     * paint, align)`. `(x, y)` denote the text origin AFTER the alignment
     * shift — `kLeft_Align` keeps the origin as the left edge of the run,
     * `kCenter_Align` shifts left by half the run width, `kRight_Align`
     * shifts left by the full run width.
     */
    public fun Draw(
        canvas: SkCanvas,
        text: String,
        encoding: SkTextEncoding,
        x: SkScalar,
        y: SkScalar,
        font: SkFont,
        paint: SkPaint,
        align: Align = Align.kLeft_Align,
    ) {
        var ox = x
        if (align != Align.kLeft_Align) {
            var width = font.measureText(text, text.length, encoding)
            if (align == Align.kCenter_Align) width *= 0.5f
            ox -= width
        }
        canvas.drawSimpleText(text, text.length, encoding, ox, y, font, paint)
    }

    /**
     * Convenience overload defaulting to `kUTF8` encoding — the encoding
     * upstream GMs use exclusively when calling
     * `SkTextUtils::DrawString(canvas, text, x, y, font, paint, align)`.
     */
    public fun DrawString(
        canvas: SkCanvas,
        text: String,
        x: SkScalar,
        y: SkScalar,
        font: SkFont,
        paint: SkPaint,
        align: Align = Align.kLeft_Align,
    ): Unit = Draw(canvas, text, SkTextEncoding.kUTF8, x, y, font, paint, align)
}
