package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BitmapShaderGmTest {
    @Test
    fun `draws bitmap shader and alpha mask cases`() {
        val gm = BitmapShaderGm()
        val surface = Surface(gm.width, gm.height)
        val canvas = GmCanvas(surface.canvas(), gm.width, gm.height)

        gm.draw(canvas, gm.width, gm.height)

        val ops = surface.snapshotOps()
        val imageOps = ops.filterIsInstance<DisplayOp.DrawImage>()
        val rectOps = ops.filterIsInstance<DisplayOp.DrawRect>()
        val pathOps = ops.filterIsInstance<DisplayOp.DrawPath>()

        assertEquals(8, imageOps.size)
        assertEquals(List(8) { ColorType.ALPHA_8 }, imageOps.map { it.image.colorType })
        assertEquals(4, imageOps.count { it.paint?.shader != null })
        assertEquals(4, imageOps.count { it.paint?.shader == null && it.paint?.color == Color.GREEN })

        val maskShaderPaints =
            rectOps.map { it.paint }.plus(pathOps.map { it.paint })
                .filter { it.shader != null && it.color == Color.RED }
        assertEquals(4, maskShaderPaints.size)
        assertTrue(
            maskShaderPaints.all { it.shader?.unwrapImageShader()?.tileModeX == TileMode.REPEAT },
        )
        assertTrue(
            maskShaderPaints.all { it.shader?.unwrapImageShader()?.image?.colorType == ColorType.ALPHA_8 },
        )
    }
}

private fun Shader.unwrapImageShader(): Shader.Image? = when (this) {
    is Shader.Image -> this
    is Shader.WithLocalMatrix -> shader.unwrapImageShader()
    else -> null
}
