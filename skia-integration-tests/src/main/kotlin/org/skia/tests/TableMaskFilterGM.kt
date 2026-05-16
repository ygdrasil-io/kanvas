package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.effects.SkTableMaskFilter
import org.graphiks.math.SK_ColorBLACK
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathDirection
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/tablemaskfilter.cpp::tablemaskfilter` (`DEF_SIMPLE_GM`).
 *
 * Builds a 256-entry alpha LUT that maps every coverage value to `128`
 * **except** full coverage (`255 → 255`) — a step-function mask filter
 * that preserves opaque interiors while halving the alpha of anti-
 * aliased / partially-covered pixels.
 *
 * The path is the union of a rect and a CCW oval inscribed in the same
 * bounds, so the rect's corners (full coverage) draw at full alpha
 * while the oval's AA boundary and the cancelled interior land at
 * `128`. Reference image is `tablemaskfilter.png` (400 × 400).
 */
public class TableMaskFilterGM : GM() {

    override fun getName(): String = "tablemaskfilter"

    override fun getISize(): SkISize = SkISize.Make(400, 400)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val table = ByteArray(256)
        for (i in 0 until 256) {
            table[i] = 128.toByte()
        }
        table[255] = 255.toByte()

        val paint = SkPaint().apply {
            style = SkPaint.Style.kFill_Style
            color = SK_ColorBLACK
            maskFilter = SkTableMaskFilter.Create(table)
        }

        val bounds = SkRect.MakeLTRB(38f, 38f, 218f, 218f)
        val path: SkPath = SkPathBuilder()
            .addRect(bounds)
            .addOval(bounds, SkPathDirection.kCCW)
            .detach()

        c.drawPath(path, paint)
    }
}
