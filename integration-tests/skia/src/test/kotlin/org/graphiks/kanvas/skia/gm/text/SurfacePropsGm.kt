package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

class SurfacePropsGm(
    private val useDistanceField: Boolean = false,
) : SkiaGm {
    override val name: String = if (useDistanceField) "surfaceprops_df" else "surfaceprops"
    override val renderFamily = RenderFamily.TEXT
    override val minSimilarity = 0.0
    override val width = W
    override val height: Int = H * recs.size

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        var y = 0f
        for (rec in recs) {
            val surface = Surface(W, H)
            surface.canvas {
                val paint = Paint(
                    shader = makeShader(),
                )
                drawRect(Rect(0f, 0f, W.toFloat(), H.toFloat()), paint)
                val textPaint = Paint(color = Color.fromRGBA(1f, 1f, 1f, 1f))
                val font = Font(typeface, size = 32f)
                drawString(rec.label, W / 2f, H * 3f / 4f, font, textPaint)
            }
            val image = surface.makeImageSnapshot()
            canvas.drawImage(image, Rect(0f, y.toFloat(), W.toFloat(), y + H.toFloat()))
            y += H.toFloat()
        }
    }

    private fun makeShader(): Shader {
        val gradA = Color.fromRGBA(0.6f, 0.6f, 0.6f, 1f)
        val gradB = Color.fromRGBA(0.4f, 0.4f, 0.4f, 1f)
        return Shader.LinearGradient(
            start = Point(0f, 0f),
            end = Point(W.toFloat(), H.toFloat()),
            stops = listOf(GradientStop(0f, gradA), GradientStop(1f, gradB)),
            tileMode = TileMode.CLAMP,
        )
    }

    private data class SurfacePropsInput(val label: String)

    private companion object {
        const val W: Int = 800
        const val H: Int = 100

        val recs: List<SurfacePropsInput> = listOf(
            SurfacePropsInput("Unknown geometry, default contrast/gamma"),
            SurfacePropsInput("RGB_H, default contrast/gamma"),
            SurfacePropsInput("BGR_H, default contrast/gamma"),
            SurfacePropsInput("RGB_V, default contrast/gamma"),
            SurfacePropsInput("BGR_V, default contrast/gamma"),
            SurfacePropsInput("RGB_H contrast : 0 gamma: 0"),
            SurfacePropsInput("RGB_H contrast : 1 gamma: 0"),
            SurfacePropsInput("RGB_H contrast : 0 gamma: 3.9"),
            SurfacePropsInput("RGB_H contrast : 1 gamma: 3.9"),
        )
    }
}
