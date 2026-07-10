package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.CornerRadii
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect
import kotlin.random.Random

/**
 * Port of Skia's `gm/shapes.cpp::SimpleShapesGM` (500 × 500).
 * 9 RRect cells drawn at various rotations with semi-transparent
 * random colors in DARKEN blend mode to detect double-blending
 * artefacts.
 * @see https://github.com/google/skia/blob/main/gm/shapes.cpp
 */
open class SimpleShapesGm(private val antialias: Boolean = true) : SkiaGm {
    override val name: String get() = if (antialias) "simpleshapes" else "simpleshapes_bw"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 500
    override val height = 500

    private val shapes: List<RRect>
    private val rotations: FloatArray

    init {
        val s = mutableListOf<RRect>()
        val r = mutableListOf<Float>()

        // setOval(Rect(-5, 25, 200, 100))
        val ovalW = 200f
        val ovalH = 100f
        s.add(RRect(
            Rect(-5f, 25f, 195f, 125f),
            CornerRadii(ovalW / 2f, ovalH / 2f),
            CornerRadii(ovalW / 2f, ovalH / 2f),
            CornerRadii(ovalW / 2f, ovalH / 2f),
            CornerRadii(ovalW / 2f, ovalH / 2f),
        ))
        r.add(21f)

        // setRect(Rect(95, 75, 125, 100))
        s.add(RRect(Rect(95f, 75f, 220f, 175f)))
        r.add(94f)

        // setRectXY(Rect(0, 75, 150, 100), 1e-5, 1e-5)
        s.add(RRect(Rect(0f, 75f, 150f, 175f), 1e-5f))
        r.add(132f)

        // setRectXY(Rect(15, -20, 100, 100), 20, 15)
        s.add(RRect(
            Rect(15f, -20f, 115f, 80f),
            CornerRadii(20f, 15f), CornerRadii(20f, 15f),
            CornerRadii(20f, 15f), CornerRadii(20f, 15f),
        ))
        r.add(282f)

        // setNinePatch(Rect(140, -50, 90, 110), 10, 5, 25, 35)
        s.add(RRect(
            Rect(140f, -50f, 230f, 60f),
            CornerRadii(10f, 5f), CornerRadii(25f, 5f),
            CornerRadii(25f, 35f), CornerRadii(10f, 35f),
        ))
        r.add(0f)

        // setNinePatch(Rect(160, -60, 60, 90), 10, 60, 50, 30)
        s.add(RRect(
            Rect(160f, -60f, 220f, 30f),
            CornerRadii(10f, 60f), CornerRadii(50f, 60f),
            CornerRadii(50f, 30f), CornerRadii(10f, 30f),
        ))
        r.add(-35f)

        // setNinePatch(Rect(220, -120, 60, 90), 1, 89, 59, 1)
        s.add(RRect(
            Rect(220f, -120f, 280f, -30f),
            CornerRadii(1f, 89f), CornerRadii(59f, 89f),
            CornerRadii(59f, 1f), CornerRadii(1f, 1f),
        ))
        r.add(65f)

        // setRectRadii(Rect(150, -129, 80, 160), [{4,6},{12,8},{24,16},{32,48}])
        s.add(RRect(
            Rect(150f, -129f, 230f, 31f),
            CornerRadii(4f, 6f), CornerRadii(12f, 8f),
            CornerRadii(24f, 16f), CornerRadii(32f, 48f),
        ))
        r.add(265f)

        // setRectRadii(Rect(180, -30, 80, 60), [{0,0},{80,60},{0,0},{80,60}])
        s.add(RRect(
            Rect(180f, -30f, 260f, 30f),
            CornerRadii(0f, 0f), CornerRadii(80f, 60f),
            CornerRadii(0f, 0f), CornerRadii(80f, 60f),
        ))
        r.add(295f)

        shapes = s
        rotations = r.toFloatArray()
    }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(1f, 1f, 1f, 1f)
        canvas.save()
        canvas.translate(width / 2f, height / 2f)

        val rand = Random(2)
        for (i in shapes.indices) {
            val rgba = (rand.nextInt() and 0x7F7F7F) or (0x80 shl 24)
            val paint = Paint(
                antiAlias = antialias,
                color = Color(rgba.toUInt()),
                blendMode = BlendMode.DARKEN,
            )
            canvas.save()
            canvas.rotate(rotations[i])
            canvas.drawRRect(shapes[i], paint)
            canvas.restore()
        }

        canvas.restore()
    }
}
