package org.graphiks.kanvas.skia.gm.clip

import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private fun drawClippedFlower(canvas: GmCanvas, fillType: FillType) {
    val kSize = 1000
    canvas.drawColor(0f, 1f, 1f)

    val clip = Path { }
    clip.fillType = FillType.WINDING
    val kGridCount = 50
    val kCellSize: Float = kSize.toFloat() / kGridCount
    for (y in 0 until kGridCount) {
        clip.addRect(Rect.fromLTRB(0f, y * kCellSize, kSize.toFloat(), (y + 1) * kCellSize))
    }
    for (x in 0 until kGridCount) {
        clip.addRect(Rect.fromLTRB(x * kCellSize, 0f, (x + 1) * kCellSize, kSize.toFloat()))
    }
    canvas.clipPath(clip)

    val flower = Path { }
    flower.fillType = fillType
    flower.moveTo(1f, 0f)
    val kNumPetals = 9
    for (i in 1..kNumPetals) {
        val c = 2f * PI.toFloat() * (i - 0.5f) / kNumPetals
        val theta = 2f * PI.toFloat() * i / kNumPetals
        flower.quadTo(cos(c) * 2f, sin(c) * 2f, cos(theta), sin(theta))
    }
    flower.close()
    flower.addOval(Rect.fromLTRB(-0.75f, -0.75f, 0.75f, 0.75f))

    canvas.translate(kSize / 2f, kSize / 2f)
    canvas.scale(kSize / 3f, kSize / 3f)

    val paint = Paint(antiAlias = true, color = Color(0xFFFF00FFu))
    canvas.drawPath(flower, paint)
}

/**
 * Port of Skia's `gm/largeclippedpath.cpp::largeclippedpath_winding`.
 *
 * 1000 × 1000. A 50×50 alternating-direction rect grid clip, then a
 * 9-petal flower with winding fill rule.
 * @see https://github.com/google/skia/blob/main/gm/largeclippedpath.cpp
 */
class LargeClippedPathWindingGm : SkiaGm {
    override val name = "largeclippedpath_winding"
    override val renderFamily = RenderFamily.CLIP
    override val minSimilarity = 0.0
    override val width = 1000
    override val height = 1000

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        drawClippedFlower(canvas, FillType.WINDING)
    }
}

/**
 * Port of Skia's `gm/largeclippedpath.cpp::largeclippedpath_evenodd`.
 *
 * 1000 × 1000. Same clip as winding variant, but flower uses even-odd
 * fill rule so the inner disc subtracts via even-odd.
 * @see https://github.com/google/skia/blob/main/gm/largeclippedpath.cpp
 */
class LargeClippedPathEvenoddGm : SkiaGm {
    override val name = "largeclippedpath_evenodd"
    override val renderFamily = RenderFamily.CLIP
    override val minSimilarity = 0.0
    override val width = 1000
    override val height = 1000

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        drawClippedFlower(canvas, FillType.EVEN_ODD)
    }
}
