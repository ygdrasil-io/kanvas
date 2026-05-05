package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.math.SkISize

/**
 * Port of Skia's `gm/strokes.cpp::b_340982297` (DEF_SIMPLE_GM, 80 × 50).
 *
 * Two AA-filled self-intersecting line polygons (close-after-cross fill
 * regression). Originally exposed a triangulator bug where the close-
 * after-crossing produced an inverted winding contribution.
 */
public class B340982297GM : GM() {

    override fun getName(): String = "b_340982297"
    override fun getISize(): SkISize = SkISize.Make(80, 50)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply { isAntiAlias = true }

        val p1 = SkPathBuilder()
            .moveTo(30.23983f, 48.5674667f)
            .lineTo(1.30884242f, 45.5222702f)
            .lineTo(2.97688866f, 29.6749554f)
            .lineTo(17.4423828f, 31.1975555f)
            .lineTo(2.94269657f, 30.0452003f)
            .lineTo(4.38597536f, 11.8849154f)
            .lineTo(33.3853493f, 14.1896257f)
            .close()
            .detach()
        c.drawPath(p1, paint)

        val p2 = SkPathBuilder()
            .moveTo(73.3853455f, 4.18963623f)
            .lineTo(69.995636f, 39.1360626f)
            .lineTo(42.83145142f, 21.056778f)
            .lineTo(42.97689819f, 19.6749573f)
            .lineTo(57.4423828f, 21.1975555f)
            .lineTo(42.94268799f, 20.0451965f)
            .lineTo(44.38595581f, 1.88491821f)
            .close()
            .detach()
        c.drawPath(p2, paint)
    }
}
