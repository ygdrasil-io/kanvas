package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorBLACK
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.math.SkISize

/**
 * Port of Skia's `gm/bug12866.cpp::bug40810065` (DEF_SIMPLE_GM,
 * 256 × 512).
 *
 * Stroker recursion-limit regression test : two near-identical cubic
 * paths (last point differs by 0.01 px) drawn under `scale(2, 2)` with
 * `kRound_Cap`. Originally one of the cubics produced a runaway
 * subdivision in the stroker, hitting the recursion limit and emitting
 * malformed outline geometry.
 */
public class Bug40810065GM : GM() {

    override fun getName(): String = "bug40810065"
    override fun getISize(): SkISize = SkISize.Make(256, 512)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.scale(2f, 2f)

        val path1 = SkPathBuilder()
            .moveTo(108.87f, 3.78f)
            .cubicTo(201.1f, -128.61f, 34.21f, 82.54f, 134.14f, 126.01f)
            .detach()
        val path2 = SkPathBuilder()
            .moveTo(108.87f, 3.78f)
            .cubicTo(201f, -128.61f, 34.21f, 82.54f, 134.14f, 126f)
            .detach()

        val stroke = SkPaint().apply {
            color = SK_ColorBLACK
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 1f
            strokeCap = SkPaint.Cap.kRound_Cap
        }

        c.save()
        c.translate(-75f, 50f)
        c.drawPath(path1, stroke)
        c.restore()

        c.save()
        c.translate(-20f, 100f)
        c.drawPath(path2, stroke)
        c.restore()
    }
}
