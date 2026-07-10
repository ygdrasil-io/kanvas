package org.graphiks.kanvas.skia.gm.clip

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/complexclip4.cpp::ComplexClip4GM` (970 × 780).
 *
 * Exercises clip-rect / clip-path / clip-rrect replacement after a
 * "device clip restriction" pass. This port simulates the restriction
 * by wrapping the green fill in `save()` + `clipRect(restrictRect)` +
 * `drawColor(green)` + `restore()`, which leaves the parent clip state
 * unchanged for the subsequent clip operation.
 * @see https://github.com/google/skia/blob/main/gm/complexclip4.cpp
 */
open class ComplexClip4Gm(
    override val name: String,
    private val doAAClip: Boolean,
) : SkiaGm {
    override val renderFamily = RenderFamily.CLIP
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 970
    override val height = 780

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0xDE / 255f, 0xDF / 255f, 0xDE / 255f)

        val yellow = Paint(color = Color(0xFFFFFF00u), antiAlias = doAAClip)

        canvas.save()

        canvas.save()
        greenIn(canvas, Rect.fromLTRB(100f, 100f, 300f, 300f))
        canvas.clipPath(
            Path { }.apply { addRect(Rect.fromLTRB(100f, 200f, 400f, 500f)) },
            antiAlias = doAAClip,
        )
        canvas.drawRect(Rect.fromLTRB(100f, 200f, 400f, 500f), yellow)
        canvas.restore()

        canvas.save()
        greenIn(canvas, Rect.fromLTRB(500f, 100f, 800f, 300f))
        val pathClip = Path {
            moveTo(650f, 200f)
            lineTo(900f, 300f)
            lineTo(650f, 400f)
            lineTo(650f, 300f)
            close()
        }
        canvas.clipPath(pathClip, antiAlias = doAAClip)
        canvas.drawRect(Rect.fromLTRB(500f, 200f, 900f, 500f), yellow)
        canvas.restore()

        canvas.save()
        greenIn(canvas, Rect.fromLTRB(500f, 500f, 800f, 700f))
        val rrect = RRect(Rect.fromLTRB(500f, 600f, 900f, 750f), radius = 0f).copy(
            topLeft = org.graphiks.kanvas.types.CornerRadii(200f, 75f),
            topRight = org.graphiks.kanvas.types.CornerRadii(200f, 75f),
            bottomLeft = org.graphiks.kanvas.types.CornerRadii(200f, 75f),
            bottomRight = org.graphiks.kanvas.types.CornerRadii(200f, 75f),
        )
        canvas.clipRRect(rrect, antiAlias = doAAClip)
        canvas.drawRect(Rect.fromLTRB(500f, 600f, 900f, 750f), yellow)
        canvas.restore()

        canvas.save()
        canvas.clipPath(
            Path { }.apply { addRect(Rect.fromLTRB(100f, 400f, 300f, 750f)) },
            antiAlias = doAAClip,
        )
        canvas.drawColor(0f, 1f, 0f)
        canvas.rotate(20f)
        canvas.translate(50f, 50f)
        canvas.save()
        canvas.clipPath(
            Path { }.apply { addRect(Rect.fromLTRB(150f, 450f, 250f, 700f)) },
            antiAlias = doAAClip,
        )
        canvas.drawColor(1f, 1f, 0f)
        canvas.restore()
        canvas.restore()

        canvas.restore()
    }

    private fun greenIn(canvas: GmCanvas, restrict: Rect) {
        canvas.save()
        canvas.clipPath(Path { }.apply { addRect(restrict) }, antiAlias = doAAClip)
        canvas.drawColor(0f, 1f, 0f)
        canvas.restore()
    }
}

class ComplexClip4BwGm : ComplexClip4Gm("complexclip4_bw", false) {
    override val renderCost = RenderCost.FAST
}
class ComplexClip4AaGm : ComplexClip4Gm("complexclip4_aa", true) {
    override val renderCost = RenderCost.FAST
}
