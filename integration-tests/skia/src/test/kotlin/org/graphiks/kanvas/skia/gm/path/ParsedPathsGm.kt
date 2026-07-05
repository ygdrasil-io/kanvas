package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import kotlin.random.Random

/**
 * Port of Skia's gm/arcto.cpp (DEF_SIMPLE_GM(parsedpaths, ..., 500, 500)).
 *
 * Generates random paths and draws each into a 100x100 clipped tile on a
 * 5x5 grid. Three random paths per tile with random colors.
 * @see https://github.com/google/skia/blob/main/gm/arcto.cpp
 */
class ParsedPathsGm : SkiaGm {
    override val name = "parsedpaths"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 500
    override val height = 500

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val rand = Random(0)
        val paint = Paint(antiAlias = true)

        for (xStart in 0 until 500 step 100) {
            canvas.save()
            for (yStart in 0 until 500 step 100) {
                var count = 3
                do {
                    val cp = paint.copy(color = Color.fromRGBA(rand.nextFloat(), rand.nextFloat(), rand.nextFloat()))
                    val path = makeRandomPath(rand)
                    canvas.save()
                    canvas.clipRect(Rect.fromXYWH(0f, 0f, 100f, 100f))
                    canvas.drawPath(path, cp)
                    canvas.restore()
                } while (--count > 0)
                canvas.translate(0f, 100f)
            }
            canvas.restore()
            canvas.translate(100f, 0f)
        }
    }

    private fun makeRandomPath(rand: Random): Path {
        val path = Path { }
        val x = rand.nextFloat() * 40f + 30f
        val y = rand.nextFloat() * 40f + 30f
        path.moveTo(x, y)
        val count = rand.nextInt(11)
        repeat(count) {
            when (rand.nextInt(10)) {
                0 -> path.lineTo(rand.nextFloat() * 100f, rand.nextFloat() * 100f)
                1 -> path.lineTo(rand.nextFloat() * 100f, rand.nextFloat() * 100f)
                2 -> path.quadTo(rand.nextFloat() * 100f, rand.nextFloat() * 100f, rand.nextFloat() * 100f, rand.nextFloat() * 100f)
                3 -> path.quadTo(rand.nextFloat() * 100f, rand.nextFloat() * 100f, rand.nextFloat() * 100f, rand.nextFloat() * 100f)
                4 -> path.cubicTo(rand.nextFloat() * 100f, rand.nextFloat() * 100f, rand.nextFloat() * 100f, rand.nextFloat() * 100f, rand.nextFloat() * 100f, rand.nextFloat() * 100f)
                5 -> path.cubicTo(rand.nextFloat() * 100f, rand.nextFloat() * 100f, rand.nextFloat() * 100f, rand.nextFloat() * 100f, rand.nextFloat() * 100f, rand.nextFloat() * 100f)
                6 -> path.lineTo(rand.nextFloat() * 100f, rand.nextFloat() * 100f)
                7 -> path.quadTo(rand.nextFloat() * 100f, rand.nextFloat() * 100f, rand.nextFloat() * 100f, rand.nextFloat() * 100f)
                8 -> path.cubicTo(rand.nextFloat() * 100f, rand.nextFloat() * 100f, rand.nextFloat() * 100f, rand.nextFloat() * 100f, rand.nextFloat() * 100f, rand.nextFloat() * 100f)
                9 -> path.close()
            }
        }
        return path
    }
}
