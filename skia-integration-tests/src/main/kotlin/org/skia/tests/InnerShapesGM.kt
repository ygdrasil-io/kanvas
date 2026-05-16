package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorWHITE
import org.skia.math.SkColorSetARGB
import org.skia.math.SkColorGetR
import org.skia.math.SkColorGetG
import org.skia.math.SkColorGetB
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRRect
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.math.SkVector
import org.skia.tools.SkRandom
import kotlin.math.min

/**
 * Port of Skia's `gm/shapes.cpp::InnerShapesGM` (500 × 500).
 *
 * 9 RRect cells (same shape set as [SimpleShapesGM], pre-rotated by
 * the same `fRotations` list). Each cell uses the same `outer` shape
 * and an `inner` shape picked via `(i*7 + 11) % simpleShapeCount` —
 * scaled and translated to fit inside the outer. Drawn with
 * `drawDRRect(outer, transformedInner, paint)`.
 *
 * Substitute for `SkRRect.transform` (not exposed in our port) : the
 * upstream transform is always **translate + uniform scale**, so we
 * compute the transformed inner manually by scaling the rect + per-
 * corner radii by the same factor and translating to the new center.
 */
public open class InnerShapesGM(private val antialias: Boolean) : GM() {

    override fun getName(): String =
        if (antialias) "innershapes" else "innershapes_bw"
    override fun getISize(): SkISize = SkISize.Make(500, 500)

    private val shapes: List<SkRRect>
    private val rotations: FloatArray
    private val simpleShapeCount: Int

    init {
        val s = mutableListOf<SkRRect>()
        val r = mutableListOf<Float>()

        s.add(SkRRect().apply { setOval(SkRect.MakeXYWH(-5f, 25f, 200f, 100f)) })
        r.add(21f)
        s.add(SkRRect().apply { setRect(SkRect.MakeXYWH(95f, 75f, 125f, 100f)) })
        r.add(94f)
        s.add(SkRRect().apply { setRectXY(SkRect.MakeXYWH(0f, 75f, 150f, 100f), 1e-5f, 1e-5f) })
        r.add(132f)
        s.add(SkRRect().apply { setRectXY(SkRect.MakeXYWH(15f, -20f, 100f, 100f), 20f, 15f) })
        r.add(282f)

        simpleShapeCount = s.size

        s.add(SkRRect().apply { setNinePatch(SkRect.MakeXYWH(140f, -50f, 90f, 110f), 10f, 5f, 25f, 35f) })
        r.add(0f)
        s.add(SkRRect().apply { setNinePatch(SkRect.MakeXYWH(160f, -60f, 60f, 90f), 10f, 60f, 50f, 30f) })
        r.add(-35f)
        s.add(SkRRect().apply { setNinePatch(SkRect.MakeXYWH(220f, -120f, 60f, 90f), 1f, 89f, 59f, 1f) })
        r.add(65f)

        val radii = arrayOf(SkVector(4f, 6f), SkVector(12f, 8f), SkVector(24f, 16f), SkVector(32f, 48f))
        s.add(SkRRect().apply { setRectRadii(SkRect.MakeXYWH(150f, -129f, 80f, 160f), radii) })
        r.add(265f)

        val radii2 = arrayOf(SkVector(0f, 0f), SkVector(80f, 60f), SkVector(0f, 0f), SkVector(80f, 60f))
        s.add(SkRRect().apply { setRectRadii(SkRect.MakeXYWH(180f, -30f, 80f, 60f), radii2) })
        r.add(295f)

        shapes = s
        rotations = r.toFloatArray()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.clear(SK_ColorWHITE)
        c.save()
        c.translate(getISize().width / 2f, getISize().height / 2f)

        val rand = SkRandom()
        for (i in shapes.indices) {
            val outer = shapes[i]
            val inner = shapes[(i * 7 + 11) % simpleShapeCount]
            var s = 0.95f * min(
                outer.rect().width() / inner.rect().width(),
                outer.rect().height() / inner.rect().height(),
            )
            var dx = (rand.nextF() - 0.5f) * (outer.rect().width() - s * inner.rect().width())
            var dy = (rand.nextF() - 0.5f) * (outer.rect().height() - s * inner.rect().height())
            // Fixup inner rects so they don't reach outside the outer rect
            // (mirrors upstream's per-index special-case adjustments).
            when (i) {
                0 -> s *= 0.85f
                8 -> { s *= 0.4f; dx = 0f; dy = 0f }
                5 -> { s *= 0.75f; dx = 0f; dy = 0f }
                6 -> { s *= 0.65f; dx = -5f; dy = 10f }
            }

            val xformedInner = scaleAndTranslateRRect(
                inner,
                cx = outer.rect().centerX() + dx,
                cy = outer.rect().centerY() + dy,
                scale = s,
            )

            val paint = SkPaint().apply { isAntiAlias = antialias }
            val rgba = rand.nextU() and 0xFF7F7F7F.toInt()
            paint.color = SkColorSetARGB(0x80, SkColorGetR(rgba), SkColorGetG(rgba), SkColorGetB(rgba))
            c.save()
            c.rotate(rotations[i])
            c.drawDRRect(outer, xformedInner, paint)
            c.restore()
        }
        c.restore()
    }

    /**
     * Substitute for `SkRRect.transform(SkMatrix)` when the matrix is a
     * translate + uniform scale. Computes a new SkRRect whose rect is
     * `inner.rect()` scaled by `scale` and centred at `(cx, cy)`, and
     * whose 4 corner radii are scaled by `scale`.
     */
    private fun scaleAndTranslateRRect(inner: SkRRect, cx: Float, cy: Float, scale: Float): SkRRect {
        val rect = inner.rect()
        val newW = rect.width() * scale
        val newH = rect.height() * scale
        val newRect = SkRect.MakeLTRB(
            cx - newW / 2f, cy - newH / 2f,
            cx + newW / 2f, cy + newH / 2f,
        )
        val out = SkRRect()
        if (inner.isRect()) {
            out.setRect(newRect)
            return out
        }
        if (inner.isOval()) {
            out.setOval(newRect)
            return out
        }
        // Per-corner radii — scale uniformly.
        val radii = Array(4) { SkVector(0f, 0f) }
        for (corner in SkRRect.Corner.entries) {
            val r = inner.radii(corner)
            radii[corner.index] = SkVector(r.fX * scale, r.fY * scale)
        }
        out.setRectRadii(newRect, radii)
        return out
    }
}

public class InnerShapesAaGM : InnerShapesGM(antialias = true)
public class InnerShapesBwGM : InnerShapesGM(antialias = false)
