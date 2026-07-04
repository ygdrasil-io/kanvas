package org.graphiks.kanvas.skia.gm.gradient

import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

class DegenerateGradientGm : SkiaGm {
    override val name = "degenerate_gradients"
    override val renderFamily = RenderFamily.GRADIENT
    override val minSimilarity = 0.0
    override val width = 800
    override val height = 800

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.translate((3 * TILE_GAP).toFloat(), (3 * TILE_GAP).toFloat())
        drawTileHeader(canvas)
        drawRow(canvas, "linear: empty, blue, blue, green") { makeLinear(it) }
        drawRow(canvas, "radial:  empty, blue, blue, green") { makeRadial(it) }
        drawRow(canvas, "sweep-0: empty, blue, blue, green") { makeSweepZeroAng(it) }
        drawRow(canvas, "sweep-45: empty, blue, blue, red 45 degree sector then green") { makeSweep(it) }
        drawRow(canvas, "2pt-conic-0: empty, blue, blue, green") { make2ptConicZeroRad(it) }
        drawRow(canvas, "2pt-conic-1: empty, blue, blue, full red circle on green") { make2ptConic(it) }
    }

    private fun drawTileHeader(canvas: GmCanvas) {
        canvas.save()
        val font = Font(typeface, 12f)
        for (name in TILE_NAMES) {
            canvas.drawString(name, 0f, 0f, font, Paint())
            canvas.translate((TILE_SIZE + TILE_GAP).toFloat(), 0f)
        }
        canvas.restore()
        canvas.translate(0f, (2 * TILE_GAP).toFloat())
    }

    private fun drawRow(canvas: GmCanvas, desc: String, factory: (TileMode) -> Shader?) {
        canvas.save()
        val text = Paint(color = Color.BLACK, antiAlias = true)
        val font = Font(typeface, 12f)
        canvas.translate(0f, TILE_GAP.toFloat())
        canvas.drawString(desc, 0f, 0f, font, text)
        canvas.translate(0f, TILE_GAP.toFloat())
        var paint = Paint(
            color = Color.BLACK,
            style = PaintStyle.STROKE_AND_FILL,
            strokeWidth = 2f,
        )
        for (mode in TILE_MODES) {
            paint = paint.copy(shader = factory(mode))
            canvas.drawRect(Rect.fromXYWH(0f, 0f, TILE_SIZE.toFloat(), TILE_SIZE.toFloat()), paint)
            canvas.translate((TILE_SIZE + TILE_GAP).toFloat(), 0f)
        }
        canvas.restore()
        canvas.translate(0f, (3 * TILE_GAP + TILE_SIZE).toFloat())
    }

    private fun makeLinear(mode: TileMode): Shader? = Shader.LinearGradient(
        start = CENTER, end = CENTER,
        stops = COLORS.mapIndexed { i, c -> GradientStop(POS[i], c) },
        tileMode = mode,
    )

    private fun makeRadial(mode: TileMode): Shader? = null

    private fun makeSweep(mode: TileMode): Shader? = null

    private fun makeSweepZeroAng(mode: TileMode): Shader? = null

    private fun make2ptConic(mode: TileMode): Shader? = Shader.ConicalGradient(
        start = CENTER, startRadius = (TILE_SIZE / 2).toFloat(),
        end = CENTER, endRadius = (TILE_SIZE / 2).toFloat(),
        stops = COLORS.mapIndexed { i, c -> GradientStop(POS[i], c) },
        tileMode = mode,
    )

    private fun make2ptConicZeroRad(mode: TileMode): Shader? = Shader.ConicalGradient(
        start = CENTER, startRadius = 0f,
        end = CENTER, endRadius = 0f,
        stops = COLORS.mapIndexed { i, c -> GradientStop(POS[i], c) },
        tileMode = mode,
    )

    private companion object {
        val COLORS = listOf(Color.RED, Color.WHITE, Color.BLUE, Color.BLACK, Color.GREEN)
        val POS = floatArrayOf(0.0f, 0.0f, 0.5f, 1.0f, 1.0f)
        val TILE_MODES = arrayOf(TileMode.DECAL, TileMode.REPEAT, TileMode.MIRROR, TileMode.CLAMP)
        val TILE_NAMES = arrayOf("decal", "repeat", "mirror", "clamp")
        const val TILE_SIZE = 100
        const val TILE_GAP = 10
        val CENTER = Point((TILE_SIZE / 2).toFloat(), (TILE_SIZE / 2).toFloat())
    }
}
