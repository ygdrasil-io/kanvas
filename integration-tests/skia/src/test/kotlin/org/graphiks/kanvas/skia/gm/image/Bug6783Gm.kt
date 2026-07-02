package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

class Bug6783Gm : SkiaGm {
    override val name = "bug6783"
    override val renderFamily = RenderFamily.IMAGE
    override val minSimilarity = 0.0
    override val width = 500
    override val height = 500

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val surf = Surface(100, 100)
        surf.canvas {
            clear(Color.fromRGBA(1f, 1f, 0f))
            drawRect(Rect.fromLTRB(0f, 0f, 50f, 100f), Paint(Color.BLUE))
        }
        val img = surf.makeImageSnapshot()
        val shader = Shader.WithLocalMatrix(
            img.makeShader(TileMode.REPEAT, TileMode.CLAMP),
            Matrix33.translate(25f, 214f) * Matrix33.scale(2f, 2f) * Matrix33.skew(0.5f, 0.5f),
        )
        canvas.drawRect(Rect(0f, 0f, 500f, 500f), Paint(shader = shader))
    }
}
