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
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33

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
            Matrix33.skew(1f, 0f)
        } else {
            val scale = 11f / 12f
            Matrix33.scale(scale, scale)
        }

        val paint = Paint(
            shader = Shader.WithLocalMatrix(Shader.Image(bm, mode, mode), m),
        )

        canvas.translate(50f, 50f)
        canvas.drawRect(
            org.graphiks.kanvas.types.Rect(0f, 0f, 640f, 480f), paint,
        )
    }

    private fun makeBm(): Image {
        val W = 257
        val H = 161
        val surf = Surface(W, H)
        surf.canvas {
            drawColor(Color.WHITE)
            val colors = arrayOf(Color.BLUE, Color.RED, Color.BLACK, Color.GREEN)
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

class GiantBitmapClampPointScale : GiantBitmapGm(TileMode.CLAMP, false, false)
class GiantBitmapRepeatPointScale : GiantBitmapGm(TileMode.REPEAT, false, false)
class GiantBitmapMirrorPointScale : GiantBitmapGm(TileMode.MIRROR, false, false)
class GiantBitmapClampBilerpScale : GiantBitmapGm(TileMode.CLAMP, true, false)
class GiantBitmapRepeatBilerpScale : GiantBitmapGm(TileMode.REPEAT, true, false)
class GiantBitmapMirrorBilerpScale : GiantBitmapGm(TileMode.MIRROR, true, false)
class GiantBitmapClampPointRotate : GiantBitmapGm(TileMode.CLAMP, false, true)
class GiantBitmapRepeatPointRotate : GiantBitmapGm(TileMode.REPEAT, false, true)
class GiantBitmapMirrorPointRotate : GiantBitmapGm(TileMode.MIRROR, false, true)
class GiantBitmapClampBilerpRotate : GiantBitmapGm(TileMode.CLAMP, true, true)
class GiantBitmapRepeatBilerpRotate : GiantBitmapGm(TileMode.REPEAT, true, true)
class GiantBitmapMirrorBilerpRotate : GiantBitmapGm(TileMode.MIRROR, true, true)
