package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorRED
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/plus.cpp::PlusMergesAA` (256 × 256).
 *
 * Demonstrates that AA seams between two adjacent triangles **merge
 * losslessly** when drawn under `kPlus` inside a `saveLayer`, vs the
 * naive `kSrcOver` case which leaks the underlying red square through
 * the AA-subpixel coverage gap.
 *
 *  - Top-left red 100×100 square + green over-draw via two triangles
 *    under `kSrcOver` → faint red diagonal seam visible.
 *  - Top-right red 100×100 square + green under `kPlus` inside a
 *    `saveLayer` → seam fully covered, output is uniform green.
 *
 * The `saveLayer + kPlus` path validates our composite-from-layer
 * implementation under a non-`kSrcOver` paint blend mode.
 */
public class PlusMergesAaGM : GM() {

    override fun getName(): String = "PlusMergesAA"
    override fun getISize(): SkISize = SkISize.Make(256, 256)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val p = SkPaint().apply {
            color = SK_ColorRED
            isAntiAlias = true
        }

        c.drawRect(SkRect.MakeWH(100f, 100f), p)
        c.drawRect(SkRect.MakeXYWH(150f, 0f, 100f, 100f), p)

        p.color = 0xF000FF00.toInt()

        // Upper-left triangle : (0,0)→(100,0)→(0,100)→(0,0).
        val upperLeft: SkPath = SkPathBuilder()
            .moveTo(0f, 0f)
            .lineTo(100f, 0f)
            .lineTo(0f, 100f)
            .lineTo(0f, 0f)
            .detach()

        // Bottom-right triangle : (100,0)→(100,100)→(0,100)→(100,0).
        val bottomRight: SkPath = SkPathBuilder()
            .moveTo(100f, 0f)
            .lineTo(100f, 100f)
            .lineTo(0f, 100f)
            .lineTo(100f, 0f)
            .detach()

        // Left square : naive kSrcOver — red AA seam visible.
        c.drawPath(upperLeft, p)
        c.drawPath(bottomRight, p)

        // Right square : kPlus inside a saveLayer — seam vanishes.
        c.saveLayer(null, null)
        p.blendMode = SkBlendMode.kPlus
        c.translate(150f, 0f)
        c.drawPath(upperLeft, p)
        c.drawPath(bottomRight, p)
        c.restore()
    }
}
