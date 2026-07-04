package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect
import kotlin.math.abs
import kotlin.math.max

class HSLColorFilterGm : SkiaGm {
    override val name = "hslcolorfilter"
    override val renderFamily = RenderFamily.COMPOSITE
    override val minSimilarity = 0.0
    override val width = 840
    override val height = 1100

    private val fShaders: MutableList<Shader?> = mutableListOf()

    override fun onOnceBeforeDraw(canvas: GmCanvas) {
        val mandrill = decodeResource("images/mandrill_256.png")
        if (mandrill != null) {
            fShaders.add(mandrill.makeShader(TileMode.CLAMP, TileMode.CLAMP))
        } else {
            fShaders.add(null)
        }

        val gGrads = arrayOf(
            listOf(0xFFFF0000u.toInt(), 0xFF00FF00u.toInt(), 0xFF0000FFu.toInt(), 0xFFFF0000u.toInt()),
            listOf(0xDFC08040u.toInt(), 0xDF8040C0u.toInt(), 0xDF40C080u.toInt(), 0xDFC08040u.toInt()),
        )
        for (cols in gGrads) {
            val n = cols.size
            val stops = cols.mapIndexed { i, c ->
                GradientStop(i.toFloat() / (n - 1).toFloat(), argbToColor(c))
            }
            fShaders.add(Shader.SweepGradient(
                center = Point(K_WHEEL_SIZE / 2f, K_WHEEL_SIZE / 2f),
                startAngle = -90f, endAngle = 270f,
                stops = stops,
                tileMode = TileMode.REPEAT,
            ))
        }
    }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawRect(Rect(0f, 0f, width.toFloat(), height.toFloat()), Paint(color = argbToColor(0xFFCCCCCCu.toInt())))

        val gTests = arrayOf(
            arrayOf(floatArrayOf(-0.5f, 0.5f), floatArrayOf(0f, 0f), floatArrayOf(0f, 0f)),
            arrayOf(floatArrayOf(0f, 0f), floatArrayOf(-1f, 1f), floatArrayOf(0f, 0f)),
            arrayOf(floatArrayOf(0f, 0f), floatArrayOf(0f, 0f), floatArrayOf(-1f, 1f)),
        )

        val rect = Rect(0f, 0f, K_WHEEL_SIZE, K_WHEEL_SIZE)
        var paint = Paint()

        for (shader in fShaders) {
            paint = paint.copy(shader = shader)
            for (tst in gTests) {
                canvas.translate(0f, K_WHEEL_SIZE * 0.1f)
                val dh = (tst[0][1] - tst[0][0]) / (K_STEPS - 1).toFloat()
                val ds = (tst[1][1] - tst[1][0]) / (K_STEPS - 1).toFloat()
                val dl = (tst[2][1] - tst[2][0]) / (K_STEPS - 1).toFloat()
                var h = tst[0][0]
                var s = tst[1][0]
                var l = tst[2][0]
                canvas.save()
                for (i in 0 until K_STEPS) {
                    val hBias = h
                    val sBias = max(s, 0f)
                    val sScale = 1f - abs(s)
                    val lBias = max(l, 0f)
                    val lScale = 1f - abs(l)
                    val cm = floatArrayOf(
                        1f, 0f, 0f, 0f, hBias,
                        0f, sScale, 0f, 0f, sBias,
                        0f, 0f, lScale, 0f, lBias,
                        0f, 0f, 0f, 1f, 0f,
                    )
                    paint = paint.copy(colorFilter = ColorFilter.Matrix(cm))
                    canvas.translate(K_WHEEL_SIZE * 0.1f, 0f)
                    canvas.drawRect(rect, paint)
                    canvas.translate(K_WHEEL_SIZE * 1.1f, 0f)
                    h += dh
                    s += ds
                    l += dl
                }
                canvas.restore()
                canvas.translate(0f, K_WHEEL_SIZE * 1.1f)
            }
            canvas.translate(0f, K_WHEEL_SIZE * 0.1f)
        }
    }

    private fun decodeResource(path: String): Image? {
        val bytes = this::class.java.classLoader?.getResourceAsStream(path)?.readBytes() ?: return null
        val img = Image.decode(bytes)
        return if (img.width > 0) img else null
    }

    private companion object {
        const val K_WHEEL_SIZE: Float = 100f
        const val K_STEPS: Int = 7
    }
}

private fun argbToColor(argb: Int): Color {
    val a = (argb ushr 24) and 0xFF
    val r = (argb ushr 16) and 0xFF
    val g = (argb ushr 8) and 0xFF
    val b = argb and 0xFF
    return Color.fromRGBA(r / 255f, g / 255f, b / 255f, a / 255f)
}
