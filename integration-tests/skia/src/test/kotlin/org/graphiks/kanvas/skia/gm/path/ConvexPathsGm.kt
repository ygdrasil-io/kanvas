package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.skia.SkiaRandom
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.CornerRadii
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/convexpaths.cpp`.
 * 35+ convex paths laid out in a 5-column grid, each filled with a random opaque color.
 * @see https://github.com/google/skia/blob/main/gm/convexpaths.cpp
 */
class ConvexPathsGm : SkiaGm {
    override val name = "convexpaths"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 1200
    override val height = 1100

    private val paths by lazy { makePaths() }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val rand = SkiaRandom(0u)
        canvas.drawColor(0f, 0f, 0f, 1f)
        canvas.translate(20f, 20f)
        canvas.scale(2f / 3, 2f / 3)
        for (i in paths.indices) {
            canvas.save()
            canvas.translate(200f * (i % 5) + 1f / 10, 200f * (i / 5) + 9f / 10)
            val raw = rand.nextS()
            val ci = raw or (0xFF000000.toInt())
            val a = ((ci ushr 24) and 0xFF) / 255f
            val r = ((ci ushr 16) and 0xFF) / 255f
            val g = ((ci ushr 8) and 0xFF) / 255f
            val b = (ci and 0xFF) / 255f
            canvas.drawPath(paths[i], Paint(color = Color.fromRGBA(r, g, b, a), antiAlias = true))
            canvas.restore()
        }
    }

    private fun makePaths(): List<Path> {
        val out = mutableListOf<Path>()

        out.add(Path { moveTo(0f, 0f); quadTo(50f, 100f, 0f, 100f); lineTo(0f, 0f) })

        out.add(Path { moveTo(0f, 50f); quadTo(50f, 0f, 100f, 50f); quadTo(50f, 100f, 0f, 50f) })

        out.add(Path { }.apply { addRect(Rect(0f, 0f, 100f, 100f)) })
        out.add(Path { }.apply { addRect(Rect(0f, 0f, 100f, 100f)) })

        out.add(Path { }.apply { addCircle(50f, 50f, 50f) })

        out.add(Path { }.apply { addOval(Rect.fromXYWH(0f, 0f, 50f, 100f)) })
        out.add(Path { }.apply { addOval(Rect.fromXYWH(0f, 0f, 100f, 5f)) })
        out.add(Path { }.apply { addOval(Rect.fromXYWH(0f, 0f, 1f, 100f)) })

        val radii = CornerRadii(40f, 20f)
        out.add(Path { }.apply { addRRect(RRect(Rect(0f, 0f, 100f, 100f), radii, radii, radii, radii)) })

        run {
            val length = 100f
            val ptsPerSide = 1 shl 12
            val p = Path { }
            p.moveTo(0f, 0f)
            for (i in 1 until ptsPerSide) p.lineTo(length * i / ptsPerSide, 0f)
            for (i in 0 until ptsPerSide) p.lineTo(length, length * i / ptsPerSide)
            for (i in ptsPerSide downTo 1) p.lineTo(length * i / ptsPerSide, length)
            for (i in ptsPerSide downTo 1) p.lineTo(0f, length * i / ptsPerSide)
            out.add(p)
        }

        out.add(Path { moveTo(0f, 0f); lineTo(100f, 1f); lineTo(98f, 100f); lineTo(3f, 96f) })

        out.add(Path { arcTo(50f, 100f, 0f, false, true, 50f, 100f) })

        run {
            val p = Path { }
            p.moveTo(0f, 0f); p.cubicTo(1f, 1f, 10f, 90f, 0f, 100f)
            out.add(p)
        }
        run {
            val p = Path { }
            p.moveTo(0f, 0f); p.cubicTo(100f, 50f, 20f, 100f, 0f, 0f)
            out.add(p)
        }

        run {
            val p = Path { }
            p.moveTo(10f, 10f)
            p.cubicTo(10f, 10f, 10f, 0f, 20f, 0f)
            p.lineTo(40f, 0f)
            p.cubicTo(40f, 0f, 50f, 0f, 50f, 10f)
            out.add(p)
        }

        run {
            val p = Path { }
            p.moveTo(10f, 10f)
            p.cubicTo(10f, 0f, 10f, 0f, 20f, 0f)
            p.lineTo(40f, 0f)
            p.cubicTo(50f, 0f, 50f, 0f, 50f, 10f)
            out.add(p)
        }

        run {
            val p = Path { }
            p.moveTo(0f, 228f / 8)
            p.cubicTo(628f / 8, 82f / 8, 1255f / 8, 141f / 8, 1883f / 8, 202f / 8)
            out.add(p)
        }

        run {
            val p = Path { }; p.moveTo(10f, 0f); p.cubicTo(0f, 1f, 30f, 1f, 20f, 0f); out.add(p)
        }
        run {
            val p = Path { }; p.moveTo(0f, 0f); p.cubicTo(10f, 1f, 30f, 1f, 20f, 0f); out.add(p)
        }
        run {
            val p = Path { }; p.moveTo(10f, 0f); p.cubicTo(0f, 1f, 20f, 1f, 30f, 0f); out.add(p)
        }

        run {
            val p = Path { }
            p.moveTo(8.59375f, 45f)
            p.quadTo(16.9921875f, 45f, 31.25f, 45f)
            p.lineTo(100f, 100f)
            p.lineTo(8.59375f, 45f)
            out.add(p)
        }

        out.add(Path { moveTo(0f, 25f); lineTo(50f, 0f); quadTo(50f, 50f, 50f, 50f) })

        out.add(Path { moveTo(0f, 25f); lineTo(50f, 0f); cubicTo(50f, 0f, 50f, 50f, 50f, 50f) })

        out.add(Path { moveTo(0f, 25f); lineTo(50f, 0f); quadTo(50f, 49.95f, 50f, 50f) })

        out.add(Path { moveTo(0f, 25f); lineTo(50f, 0f); cubicTo(50f, 49.95f, 50f, 49.97f, 50f, 50f) })

        run {
            val p = Path { }
            p.moveTo(0f, 25f); p.lineTo(50f, 0f); p.lineTo(50f, 50f); p.cubicTo(50f, 50f, 50f, 50f, 50f, 50f)
            out.add(p)
        }

        out.add(Path { moveTo(50f, 50f); lineTo(50f, 50f) })

        out.add(Path { moveTo(50f, 50f); quadTo(50f, 50f, 50f, 50f) })

        out.add(Path { moveTo(50f, 50f); cubicTo(50f, 50f, 50f, 50f, 50f, 50f) })

        run {
            val p = Path { }
            p.moveTo(0f, 0f); p.moveTo(0f, 0f); p.moveTo(1f, 1f); p.moveTo(1f, 1f); p.moveTo(10f, 10f)
            out.add(p)
        }

        out.add(Path { moveTo(0f, 0f); moveTo(0f, 0f) })

        out.add(Path { moveTo(0f, 0f); lineTo(100f, 100f) })
        out.add(Path { moveTo(0f, 0f); quadTo(100f, 100f, 0f, 0f) })
        out.add(Path { moveTo(0f, 0f); quadTo(100f, 100f, 50f, 50f) })
        out.add(Path { moveTo(0f, 0f); quadTo(50f, 50f, 100f, 100f) })
        out.add(Path { moveTo(0f, 0f); cubicTo(0f, 0f, 0f, 0f, 100f, 100f) })

        out.add(skbugTransformedTriangle())

        out.add(Path { }.apply { addCircle(0f, 0f, 1.2f) })

        return out
    }

    private fun skbugTransformedTriangle(): Path {
        fun mx(x: Float) = 0.1f * x - 1f
        fun my(y: Float) = 0.115207f * y - 2.64977f
        val p = Path { }
        p.moveTo(mx(16.875f), my(192.594f))
        p.cubicTo(mx(45.625f), my(192.594f), mx(74.375f), my(192.594f), mx(103.125f), my(192.594f))
        p.cubicTo(mx(88.75f), my(167.708f), mx(74.375f), my(142.823f), mx(60f), my(117.938f))
        p.cubicTo(mx(45.625f), my(142.823f), mx(31.25f), my(167.708f), mx(16.875f), my(192.594f))
        p.close()
        return p
    }
}
