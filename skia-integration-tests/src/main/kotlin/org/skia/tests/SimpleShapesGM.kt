package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkColorSetARGB
import org.graphiks.math.SkColorGetR
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetB
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRRect
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.graphiks.math.SkVector
import org.skia.tools.SkRandom

/**
 * Port of Skia's `gm/shapes.cpp::SimpleShapesGM` (500 × 500).
 *
 * 9 RRect shapes (oval / rect / 1e-5px-radii rectXY / standard rectXY
 * + 3 nine-patch RRects + 2 rectRadii) drawn at random rotations with
 * `paint.alphaf = 0.5` so double-blends / dropped pixels stand out.
 * Each shape dispatches via `SkRRect.getType()` to `drawRect` /
 * `drawOval` / `drawRRect`.
 *
 * The `_bw` variant uses `setAntiAlias(false)` ; both share the same
 * shape layout and rotation list.
 */
public open class SimpleShapesGM(private val antialias: Boolean) : GM() {

    override fun getName(): String =
        if (antialias) "simpleshapes" else "simpleshapes_bw"
    override fun getISize(): SkISize = SkISize.Make(500, 500)

    private val shapes: List<SkRRect>
    private val rotations: FloatArray

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

        val rand = SkRandom(2)
        for (i in shapes.indices) {
            val paint = SkPaint().apply { isAntiAlias = antialias }
            val rgba = rand.nextU() and 0xFF7F7F7F.toInt() // mask 0x808080 = ~0x808080
            // SkColorSetARGB with alpha = 0x80 (~50%, matches setAlphaf(0.5)).
            paint.color = SkColorSetARGB(
                0x80,
                SkColorGetR(rgba),
                SkColorGetG(rgba),
                SkColorGetB(rgba),
            )
            val shape = shapes[i]
            c.save()
            c.rotate(rotations[i])
            when (shape.getType()) {
                SkRRect.Type.kRect_Type -> c.drawRect(shape.rect(), paint)
                SkRRect.Type.kOval_Type -> c.drawOval(shape.rect(), paint)
                else -> c.drawRRect(shape, paint)
            }
            c.restore()
        }
        c.restore()
    }
}

/** AA variant — `simpleshapes`. */
public class SimpleShapesAaGM : SimpleShapesGM(antialias = true)

/** Non-AA variant — `simpleshapes_bw`. */
public class SimpleShapesBwGM : SimpleShapesGM(antialias = false)
