package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.CornerRadiiF32
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.RectF32
import kotlin.math.min
import kotlin.random.Random

/**
 * Port of Skia's `gm/shapes.cpp::InnerShapesGM` (500 × 500).
 * 9 RRectF32 cells, each using an outer shape and an inner shape
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

    private val shapes: List<RRectF32>
    private val rotations: FloatArray
    private val simpleShapeCount: Int

    init {
        val s = mutableListOf<RRectF32>()
        val r = mutableListOf<Float>()

        s.add(RRectF32.of(RectF32(-5f, 25f, 195f, 125f),
            CornerRadiiF32.of(100f, 50f), CornerRadiiF32.of(100f, 50f), CornerRadiiF32.of(100f, 50f), CornerRadiiF32.of(100f, 50f)))
        r.add(21f)
        s.add(RRectF32.of(RectF32(95f, 75f, 220f, 175f)))
        r.add(94f)
        s.add(RRectF32.of(RectF32(0f, 75f, 150f, 175f), CornerRadiiF32.of(1e-5f, 1e-5f)))
        r.add(132f)
        s.add(RRectF32.of(RectF32(15f, -20f, 115f, 80f), CornerRadiiF32.of(20f, 15f)))
        r.add(282f)

        simpleShapeCount = s.size

        s.add(RRectF32.of(RectF32(140f, -50f, 230f, 60f),
            CornerRadiiF32.of(10f, 5f), CornerRadiiF32.of(25f, 35f), CornerRadiiF32.of(10f, 5f), CornerRadiiF32.of(25f, 35f)))
        r.add(0f)
        s.add(RRectF32.of(RectF32(160f, -60f, 220f, 30f),
            CornerRadiiF32.of(10f, 60f), CornerRadiiF32.of(50f, 30f), CornerRadiiF32.of(10f, 60f), CornerRadiiF32.of(50f, 30f)))
        r.add(-35f)
        s.add(RRectF32.of(RectF32(220f, -120f, 280f, -30f),
            CornerRadiiF32.of(1f, 89f), CornerRadiiF32.of(59f, 1f), CornerRadiiF32.of(1f, 89f), CornerRadiiF32.of(59f, 1f)))
        r.add(65f)

        s.add(RRectF32.of(RectF32(150f, -129f, 230f, 31f),
            CornerRadiiF32.of(4f, 6f), CornerRadiiF32.of(12f, 8f), CornerRadiiF32.of(24f, 16f), CornerRadiiF32.of(32f, 48f)))
        r.add(265f)

        s.add(RRectF32.of(RectF32(180f, -30f, 260f, 30f),
            CornerRadiiF32.of(0f, 0f), CornerRadiiF32.of(80f, 60f), CornerRadiiF32.of(0f, 0f), CornerRadiiF32.of(80f, 60f)))
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
                outer.rect.width() / inner.rect.width(),
                outer.rect.height() / inner.rect.height(),
            )
            var dx = (rand.nextFloat() - 0.5f) * (outer.rect.width() - scale * inner.rect.width())
            var dy = (rand.nextFloat() - 0.5f) * (outer.rect.height() - scale * inner.rect.height())

            when (i) {
                0 -> scale *= 0.85f
                8 -> { scale *= 0.4f; dx = 0f; dy = 0f }
                5 -> { scale *= 0.75f; dx = 0f; dy = 0f }
                6 -> { scale *= 0.65f; dx = -5f; dy = 10f }
            }

            val xformedInner = scaleRRect(
                inner,
                cx = outer.rect.center().x + dx,
                cy = outer.rect.center().y + dy,
                scale = scale,
            )

            val rgba = (rand.nextInt() and 0x7F7F7F) or (0x80 shl 24)
            val paint = Paint(
                antiAlias = antialias,
                color = ColorARGB.fromPackedUInt(rgba.toUInt()),
            )
            canvas.save()
            canvas.rotate(rotations[i])
            canvas.drawDRRect(outer, xformedInner, paint)
            canvas.restore()
        }
        canvas.restore()
    }

    private fun scaleRRect(inner: RRectF32, cx: Float, cy: Float, scale: Float): RRectF32 {
        val rect = inner.rect
        val newW = rect.width() * scale
        val newH = rect.height() * scale
        val newRect = RectF32(cx - newW / 2f, cy - newH / 2f, cx + newW / 2f, cy + newH / 2f)
        val isSimpleRect = inner.topLeft.x == 0f && inner.topLeft.y == 0f &&
            inner.topRight.x == 0f && inner.topRight.y == 0f &&
            inner.bottomRight.x == 0f && inner.bottomRight.y == 0f &&
            inner.bottomLeft.x == 0f && inner.bottomLeft.y == 0f
        val isOval = inner.topLeft.x >= inner.rect.width() / 2f && inner.topLeft.y >= inner.rect.height() / 2f
        if (isSimpleRect) {
            return RRectF32.of(newRect)
        }
        if (isOval) {
            return RRectF32.of(newRect,
                CornerRadiiF32.of(newW / 2f, newH / 2f),
                CornerRadiiF32.of(newW / 2f, newH / 2f),
                CornerRadiiF32.of(newW / 2f, newH / 2f),
                CornerRadiiF32.of(newW / 2f, newH / 2f))
        }
        return RRectF32.of(newRect,
            CornerRadiiF32.of(inner.topLeft.x * scale, inner.topLeft.y * scale),
            CornerRadiiF32.of(inner.topRight.x * scale, inner.topRight.y * scale),
            CornerRadiiF32.of(inner.bottomRight.x * scale, inner.bottomRight.y * scale),
            CornerRadiiF32.of(inner.bottomLeft.x * scale, inner.bottomLeft.y * scale))
    }
}

class InnerShapesAaGm : InnerShapesGm(antialias = true)
class InnerShapesBwGm : InnerShapesGm(antialias = false)
