package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.CornerRadii
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect
import kotlin.math.min
import kotlin.random.Random

/**
 * Port of Skia's `gm/shapes.cpp::InnerShapesGM` (500 × 500).
 * 9 RRect cells, each using an outer shape and an inner shape
 * picked via `(i*7 + 11) % simpleShapeCount` — scaled and translated to fit inside.
 * @see https://github.com/google/skia/blob/main/gm/shapes.cpp
 */
open class InnerShapesGm(private val antialias: Boolean) : SkiaGm {
    override val name: String get() = if (antialias) "innershapes" else "innershapes_bw"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 500
    override val height = 500

    private val shapes: List<RRect>
    private val rotations: FloatArray
    private val simpleShapeCount: Int

    init {
        val s = mutableListOf<RRect>()
        val r = mutableListOf<Float>()

        s.add(RRect(Rect(-5f, 25f, 195f, 125f),
            CornerRadii(100f, 50f), CornerRadii(100f, 50f), CornerRadii(100f, 50f), CornerRadii(100f, 50f)))
        r.add(21f)
        s.add(RRect(Rect(95f, 75f, 220f, 175f)))
        r.add(94f)
        s.add(RRect(Rect(0f, 75f, 150f, 175f), CornerRadii(1e-5f, 1e-5f)))
        r.add(132f)
        s.add(RRect(Rect(15f, -20f, 115f, 80f), CornerRadii(20f, 15f)))
        r.add(282f)

        simpleShapeCount = s.size

        s.add(RRect(Rect(140f, -50f, 230f, 60f),
            CornerRadii(10f, 5f), CornerRadii(25f, 35f), CornerRadii(10f, 5f), CornerRadii(25f, 35f)))
        r.add(0f)
        s.add(RRect(Rect(160f, -60f, 220f, 30f),
            CornerRadii(10f, 60f), CornerRadii(50f, 30f), CornerRadii(10f, 60f), CornerRadii(50f, 30f)))
        r.add(-35f)
        s.add(RRect(Rect(220f, -120f, 280f, -30f),
            CornerRadii(1f, 89f), CornerRadii(59f, 1f), CornerRadii(1f, 89f), CornerRadii(59f, 1f)))
        r.add(65f)

        s.add(RRect(Rect(150f, -129f, 230f, 31f),
            CornerRadii(4f, 6f), CornerRadii(12f, 8f), CornerRadii(24f, 16f), CornerRadii(32f, 48f)))
        r.add(265f)

        s.add(RRect(Rect(180f, -30f, 260f, 30f),
            CornerRadii(0f, 0f), CornerRadii(80f, 60f), CornerRadii(0f, 0f), CornerRadii(80f, 60f)))
        r.add(295f)

        shapes = s
        rotations = r.toFloatArray()
    }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(1f, 1f, 1f, 1f)
        canvas.save()
        canvas.translate(width / 2f, height / 2f)

        val rand = Random(42)
        for (i in shapes.indices) {
            val outer = shapes[i]
            val inner = shapes[(i * 7 + 11) % simpleShapeCount]
            var scale = 0.95f * min(
                outer.rect.width / inner.rect.width,
                outer.rect.height / inner.rect.height,
            )
            var dx = (rand.nextFloat() - 0.5f) * (outer.rect.width - scale * inner.rect.width)
            var dy = (rand.nextFloat() - 0.5f) * (outer.rect.height - scale * inner.rect.height)

            when (i) {
                0 -> scale *= 0.85f
                8 -> { scale *= 0.4f; dx = 0f; dy = 0f }
                5 -> { scale *= 0.75f; dx = 0f; dy = 0f }
                6 -> { scale *= 0.65f; dx = -5f; dy = 10f }
            }

            val xformedInner = scaleRRect(
                inner,
                cx = outer.rect.center.x + dx,
                cy = outer.rect.center.y + dy,
                scale = scale,
            )

            val rgba = (rand.nextInt() and 0x7F7F7F) or (0x80 shl 24)
            val paint = Paint(
                antiAlias = antialias,
                color = Color(rgba.toUInt()),
            )
            canvas.save()
            canvas.rotate(rotations[i])
            canvas.drawDRRect(outer, xformedInner, paint)
            canvas.restore()
        }
        canvas.restore()
    }

    private fun scaleRRect(inner: RRect, cx: Float, cy: Float, scale: Float): RRect {
        val rect = inner.rect
        val newW = rect.width * scale
        val newH = rect.height * scale
        val newRect = Rect(cx - newW / 2f, cy - newH / 2f, cx + newW / 2f, cy + newH / 2f)
        val isSimpleRect = inner.topLeft.x == 0f && inner.topLeft.y == 0f &&
            inner.topRight.x == 0f && inner.topRight.y == 0f &&
            inner.bottomRight.x == 0f && inner.bottomRight.y == 0f &&
            inner.bottomLeft.x == 0f && inner.bottomLeft.y == 0f
        val isOval = inner.topLeft.x >= inner.rect.width / 2f && inner.topLeft.y >= inner.rect.height / 2f
        if (isSimpleRect) {
            return RRect(newRect)
        }
        if (isOval) {
            return RRect(newRect,
                CornerRadii(newW / 2f, newH / 2f),
                CornerRadii(newW / 2f, newH / 2f),
                CornerRadii(newW / 2f, newH / 2f),
                CornerRadii(newW / 2f, newH / 2f))
        }
        return RRect(newRect,
            CornerRadii(inner.topLeft.x * scale, inner.topLeft.y * scale),
            CornerRadii(inner.topRight.x * scale, inner.topRight.y * scale),
            CornerRadii(inner.bottomRight.x * scale, inner.bottomRight.y * scale),
            CornerRadii(inner.bottomLeft.x * scale, inner.bottomLeft.y * scale))
    }
}

class InnerShapesAaGm : InnerShapesGm(antialias = true)
class InnerShapesBwGm : InnerShapesGm(antialias = false)
