package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.math.SkISize

/**
 * Port of Skia's `gm/pathfill.cpp::bug7792` (DEF_SIMPLE_GM, 800 × 800).
 *
 * 14 line-only paths exercising `moveTo` / `close` edge cases for the
 * fill rasterizer. Reproduces every corner case listed in
 * skbug.com/40039046 (#1, #3, #9, #11, #14, #15, #17, #19, #23, #29,
 * #31, #36, #39, plus zero-length and #41, #53). Each path is a small
 * variation of "rect with extra moveTo or duplicate close".
 */
public class Bug7792GM : GM() {

    override fun getName(): String = "bug7792"
    override fun getISize(): SkISize = SkISize.Make(800, 800)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val p = SkPaint()

        // skbug.com/40039046 description.
        c.drawPath(
            SkPathBuilder()
                .moveTo(10f, 10f)
                .moveTo(75f, 75f)
                .lineTo(150f, 75f)
                .lineTo(150f, 150f)
                .lineTo(75f, 150f)
                .detach(),
            p,
        )

        // skbug.com/40039046#c3
        c.translate(200f, 0f)
        c.drawPath(
            SkPathBuilder()
                .moveTo(75f, 50f)
                .moveTo(100f, 75f)
                .lineTo(150f, 75f)
                .lineTo(150f, 150f)
                .lineTo(75f, 150f)
                .lineTo(75f, 50f)
                .close()
                .detach(),
            p,
        )

        // skbug.com/40039046#c9
        c.translate(200f, 0f)
        c.drawPath(
            SkPathBuilder()
                .moveTo(10f, 10f)
                .moveTo(75f, 75f)
                .lineTo(150f, 75f)
                .lineTo(150f, 150f)
                .lineTo(75f, 150f)
                .close()
                .detach(),
            p,
        )

        // skbug.com/40039046#c11
        c.translate(-400f, 200f)
        c.drawPath(
            SkPathBuilder()
                .moveTo(75f, 150f)
                .lineTo(75f, 75f)
                .lineTo(150f, 75f)
                .lineTo(150f, 150f)
                .lineTo(75f, 150f)
                .moveTo(75f, 150f)
                .detach(),
            p,
        )

        // skbug.com/40039046#c14
        c.translate(200f, 0f)
        c.drawPath(
            SkPathBuilder()
                .moveTo(250f, 75f)
                .moveTo(250f, 75f)
                .moveTo(250f, 75f)
                .moveTo(100f, 75f)
                .lineTo(150f, 75f)
                .lineTo(150f, 150f)
                .lineTo(75f, 150f)
                .lineTo(75f, 75f)
                .close()
                .lineTo(0f, 0f)
                .close()
                .detach(),
            p,
        )

        // skbug.com/40039046#c15
        c.translate(200f, 0f)
        c.drawPath(
            SkPathBuilder()
                .moveTo(75f, 75f)
                .lineTo(150f, 75f)
                .lineTo(150f, 150f)
                .lineTo(75f, 150f)
                .moveTo(250f, 75f)
                .detach(),
            p,
        )

        // skbug.com/40039046#c17
        c.translate(-400f, 200f)
        c.drawPath(
            SkPathBuilder()
                .moveTo(75f, 10f)
                .moveTo(75f, 75f)
                .lineTo(150f, 75f)
                .lineTo(150f, 150f)
                .lineTo(75f, 150f)
                .lineTo(75f, 10f)
                .close()
                .detach(),
            p,
        )

        // skbug.com/40039046#c19
        c.translate(200f, 0f)
        c.drawPath(
            SkPathBuilder()
                .moveTo(75f, 75f)
                .lineTo(75f, 75f)
                .lineTo(75f, 75f)
                .lineTo(75f, 75f)
                .lineTo(150f, 75f)
                .lineTo(150f, 150f)
                .lineTo(75f, 150f)
                .close()
                .moveTo(10f, 10f)
                .lineTo(30f, 10f)
                .lineTo(10f, 30f)
                .detach(),
            p,
        )

        // skbug.com/40039046#c23
        c.translate(200f, 0f)
        c.drawPath(
            SkPathBuilder()
                .moveTo(75f, 75f)
                .lineTo(75f, 75f)
                .moveTo(75f, 75f)
                .lineTo(75f, 75f)
                .lineTo(150f, 75f)
                .lineTo(150f, 150f)
                .lineTo(75f, 150f)
                .close()
                .detach(),
            p,
        )

        // skbug.com/40039046#c29
        c.translate(-400f, 200f)
        c.drawPath(
            SkPathBuilder()
                .moveTo(75f, 75f)
                .lineTo(150f, 75f)
                .lineTo(150f, 150f)
                .lineTo(75f, 150f)
                .lineTo(75f, 250f)
                .moveTo(75f, 75f)
                .close()
                .detach(),
            p,
        )

        // skbug.com/40039046#c31
        c.translate(200f, 0f)
        c.drawPath(
            SkPathBuilder()
                .moveTo(75f, 75f)
                .lineTo(150f, 75f)
                .lineTo(150f, 150f)
                .lineTo(75f, 150f)
                .lineTo(75f, 10f)
                .moveTo(75f, 75f)
                .close()
                .detach(),
            p,
        )

        // skbug.com/40039046#c36
        c.translate(200f, 0f)
        c.drawPath(
            SkPathBuilder()
                .moveTo(75f, 75f)
                .lineTo(150f, 75f)
                .lineTo(150f, 150f)
                .lineTo(10f, 150f)
                .moveTo(75f, 75f)
                .lineTo(75f, 75f)
                .detach(),
            p,
        )

        // skbug.com/40039046#c39
        c.translate(200f, -600f)
        c.drawPath(
            SkPathBuilder()
                .moveTo(150f, 75f)
                .lineTo(150f, 150f)
                .lineTo(75f, 150f)
                .lineTo(75f, 100f)
                .detach(),
            p,
        )

        // zero_length_paths_aa
        c.translate(0f, 200f)
        c.drawPath(
            SkPathBuilder()
                .moveTo(150f, 100f)
                .lineTo(150f, 100f)
                .lineTo(150f, 150f)
                .lineTo(75f, 150f)
                .lineTo(75f, 100f)
                .lineTo(75f, 75f)
                .lineTo(150f, 75f)
                .close()
                .detach(),
            p,
        )

        // skbug.com/40039046#c41
        c.translate(0f, 200f)
        c.drawPath(
            SkPathBuilder()
                .moveTo(75f, 75f)
                .lineTo(150f, 75f)
                .lineTo(150f, 150f)
                .lineTo(140f, 150f)
                .lineTo(140f, 75f)
                .moveTo(75f, 75f)
                .close()
                .detach(),
            p,
        )

        // skbug.com/40039046#c53 (same as #c41).
        c.translate(0f, 200f)
        c.drawPath(
            SkPathBuilder()
                .moveTo(75f, 75f)
                .lineTo(150f, 75f)
                .lineTo(150f, 150f)
                .lineTo(140f, 150f)
                .lineTo(140f, 75f)
                .moveTo(75f, 75f)
                .close()
                .detach(),
            p,
        )
    }
}
