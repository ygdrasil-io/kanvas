package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.canvas.drawLine
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.matrix.Matrix3x3F32

open class GiantBitmapGm(
    private val mode: TileMode,
    private val doFilter: Boolean,
    private val doRotate: Boolean,
) : SkiaGm {
    override val name: String
        get() {
            val sb = StringBuilder("giantbitmap_")
            sb.append(when (mode) {
                TileMode.CLAMP -> "clamp"
                TileMode.REPEAT -> "repeat"
                TileMode.MIRROR -> "mirror"
                TileMode.DECAL -> "decal"
            })
            sb.append(if (doFilter) "_bilerp" else "_point")
            sb.append(if (doRotate) "_rotate" else "_scale")
            return sb.toString()
        }

    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 640
    override val height = 480

    private val bm: Image by lazy { makeBm() }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val m = if (doRotate) {
            Matrix3x3F32.skewing(1f, 0f)
        } else {
            val scale = 11f / 12f
            Matrix3x3F32.scaling(scale, scale)
        }

        val paint = Paint(
            shader = Shader.WithLocalMatrix(Shader.Image(bm, mode, mode), m),
        )

        canvas.translate(50f, 50f)
        canvas.drawRect(
            org.graphiks.math.geometry.RectF32(0f, 0f, 640f, 480f), paint,
        )
    }

    private fun makeBm(): Image {
        val W = 257
        val H = 161
        val surf = Surface(W, H)
        surf.canvas {
            drawColor(ColorARGB.White)
            val colors = arrayOf(ColorARGB.Blue, ColorARGB.Red, ColorARGB.Black, ColorARGB.Green)
            var x = -W
            while (x < W) {
                val paint = Paint(
                    color = colors[(x / 60) and 0x3],
                    antiAlias = true,
                    style = org.graphiks.kanvas.paint.PaintStyle.STROKE,
                    strokeWidth = 20f,
                )
                val xx = x.toFloat()
                drawLine(xx, 0f, xx, H.toFloat(), paint)
                x += 60
            }
        }
        return surf.makeImageSnapshot()
    }
}

class GiantBitmapClampPointScale : GiantBitmapGm(TileMode.CLAMP, false, false) {
    override val renderCost = RenderCost.FAST
}
class GiantBitmapRepeatPointScale : GiantBitmapGm(TileMode.REPEAT, false, false) {
    override val renderCost = RenderCost.FAST
}
class GiantBitmapMirrorPointScale : GiantBitmapGm(TileMode.MIRROR, false, false) {
    override val renderCost = RenderCost.FAST
}
class GiantBitmapClampBilerpScale : GiantBitmapGm(TileMode.CLAMP, true, false) {
    override val renderCost = RenderCost.FAST
}
class GiantBitmapRepeatBilerpScale : GiantBitmapGm(TileMode.REPEAT, true, false) {
    override val renderCost = RenderCost.FAST
}
class GiantBitmapMirrorBilerpScale : GiantBitmapGm(TileMode.MIRROR, true, false) {
    override val renderCost = RenderCost.FAST
}
class GiantBitmapClampPointRotate : GiantBitmapGm(TileMode.CLAMP, false, true) {
    override val renderCost = RenderCost.FAST
}
class GiantBitmapRepeatPointRotate : GiantBitmapGm(TileMode.REPEAT, false, true) {
    override val renderCost = RenderCost.FAST
}
class GiantBitmapMirrorPointRotate : GiantBitmapGm(TileMode.MIRROR, false, true) {
    override val renderCost = RenderCost.FAST
}
class GiantBitmapClampBilerpRotate : GiantBitmapGm(TileMode.CLAMP, true, true) {
    override val renderCost = RenderCost.FAST
}
class GiantBitmapRepeatBilerpRotate : GiantBitmapGm(TileMode.REPEAT, true, true) {
    override val renderCost = RenderCost.FAST
}
class GiantBitmapMirrorBilerpRotate : GiantBitmapGm(TileMode.MIRROR, true, true) {
    override val renderCost = RenderCost.FAST
}
