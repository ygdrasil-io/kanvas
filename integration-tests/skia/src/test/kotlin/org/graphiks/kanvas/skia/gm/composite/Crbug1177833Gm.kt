package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

class Crbug1177833Gm : SkiaGm {
    override val name = "crbug_1177833"
    override val renderFamily = RenderFamily.COMPOSITE
    override val minSimilarity = 0.0
    override val width = 400
    override val height = 400

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawRect(Rect(0f, 0f, this.width.toFloat(), this.height.toFloat()), Paint(color = Color.BLACK))
        canvas.translate(-700f, -700f)

        run {
            val ctm = Matrix33.makeAll(
                Float.fromBits(0xbf79250e.toInt()),
                Float.fromBits(0x3e9da860),
                Float.fromBits(0x44914c8a),
                Float.fromBits(0xbf982962.toInt()),
                Float.fromBits(0xbf280002.toInt()),
                Float.fromBits(0x44c3116e),
                Float.fromBits(0xba9bfe62.toInt()),
                Float.fromBits(0x39d10455),
                Float.fromBits(0x3fc9b377),
            )
            val clip = arrayOf(
                Pair(Float.fromBits(0x409fff57), Float.fromBits(0x40c86a18)),
                Pair(Float.fromBits(0x409fff57), Float.fromBits(0x4314dc8c)),
                Pair(Float.fromBits(0x407f6b0d), Float.fromBits(0x43157fff)),
                Pair(Float.fromBits(0x4040859c), Float.fromBits(0x43140374)),
            )
            val color4f = Pair(
                0x3f6eeef0.toInt(), 0x3f800000.toInt(),
            )
            canvas.save()
            canvas.concat(ctm)
            canvas.drawPath(
                Path {
                    moveTo(clip[0].first, clip[0].second)
                    lineTo(clip[1].first, clip[1].second)
                    lineTo(clip[2].first, clip[2].second)
                    lineTo(clip[3].first, clip[3].second)
                    close()
                },
                Paint(color = floatBitsToColor(0x3f6eeef0.toInt(), 0x3f6eeef0.toInt(), 0x3f6eeef0.toInt(), 0x3f800000.toInt())),
            )
            canvas.restore()
        }

        canvas.save()
        canvas.translate(-300f, 0f)
        run {
            val ctm = Matrix33.makeAll(
                Float.fromBits(0x3f54dd8a),
                Float.fromBits(0xbf9096a4.toInt()),
                Float.fromBits(0x447eae34),
                Float.fromBits(0x3f3f6905),
                Float.fromBits(0xbe5208ba.toInt()),
                Float.fromBits(0x4418118b),
                Float.fromBits(0x3aa134a1),
                Float.fromBits(0xb93ef249.toInt()),
                Float.fromBits(0x3f580bd4),
            )
            val clip = arrayOf(
                Pair(Float.fromBits(0x40a0000e), Float.fromBits(0x40c86b5a)),
                Pair(Float.fromBits(0x40a0001e), Float.fromBits(0x4314dd5f)),
                Pair(Float.fromBits(0x407f76eb), Float.fromBits(0x431580c2)),
                Pair(Float.fromBits(0x404092e7), Float.fromBits(0x43140445)),
            )
            canvas.save()
            canvas.concat(ctm)
            canvas.drawPath(
                Path {
                    moveTo(clip[0].first, clip[0].second)
                    lineTo(clip[1].first, clip[1].second)
                    lineTo(clip[2].first, clip[2].second)
                    lineTo(clip[3].first, clip[3].second)
                    close()
                },
                Paint(color = floatBitsToColor(0x3f6eeef0.toInt(), 0x3f6eeef0.toInt(), 0x3f6eeef0.toInt(), 0x3f800000.toInt())),
            )
            canvas.restore()
        }
        canvas.restore()

        run {
            val ctm = Matrix33.makeAll(
                Float.fromBits(0x3f54b255),
                Float.fromBits(0x3eb5a94d),
                Float.fromBits(0x443d7419),
                Float.fromBits(0x3f885d66),
                Float.fromBits(0x3f5a6b9c),
                Float.fromBits(0x443c7334),
                Float.fromBits(0x3aa95ea5),
                Float.fromBits(0xb8a1391e.toInt()),
                Float.fromBits(0x3f84dde5),
            )
            val clip = arrayOf(
                Pair(Float.fromBits(0x405a654c), Float.fromBits(0x42e8c790)),
                Pair(Float.fromBits(0x3728c61b), Float.fromBits(0x42e7df31)),
                Pair(Float.fromBits(0xb678ecc5.toInt()), Float.fromBits(0x412db4e0)),
                Pair(Float.fromBits(0x4024b2ad), Float.fromBits(0x413ab3ed)),
            )
            canvas.save()
            canvas.concat(ctm)
            canvas.drawPath(
                Path {
                    moveTo(clip[0].first, clip[0].second)
                    lineTo(clip[1].first, clip[1].second)
                    lineTo(clip[2].first, clip[2].second)
                    lineTo(clip[3].first, clip[3].second)
                    close()
                },
                Paint(color = floatBitsToColor(0x3f800000.toInt(), 0x3f800000.toInt(), 0x3f800000.toInt(), 0x3f800000.toInt())),
            )
            canvas.restore()
        }
    }

    private fun floatBitsToColor(rBits: Int, gBits: Int, bBits: Int, aBits: Int): Color {
        return Color.fromRGBA(
            Float.fromBits(rBits),
            Float.fromBits(gBits),
            Float.fromBits(bBits),
            Float.fromBits(aBits),
        )
    }
}
