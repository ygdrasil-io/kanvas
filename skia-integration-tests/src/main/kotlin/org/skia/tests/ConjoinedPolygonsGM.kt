package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/polygons.cpp::conjoined_polygons` (DEF_SIMPLE_GM,
 * 400 × 400). Single self-touching 7-vertex bow-tie path drawn AA-
 * filled. Originally a regression test for crbug.com/1197461 — the
 * triangulator's handling of a polygon that touches itself at a single
 * vertex.
 */
public class ConjoinedPolygonsGM : GM() {

    override fun getName(): String = "conjoined_polygons"
    override fun getISize(): SkISize = SkISize.Make(400, 400)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val path = SkPathBuilder()
            .moveTo(0f, 120f)
            .lineTo(0f, 0f)
            .lineTo(50f, 330f)
            .lineTo(90f, 0f)
            .lineTo(340f, 0f)
            .lineTo(90f, 330f)
            .lineTo(50f, 330f)
            .close()
            .detach()
        val paint = SkPaint().apply { isAntiAlias = true }
        c.drawPath(path, paint)
    }
}
