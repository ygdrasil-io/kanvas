package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.surface.Surface
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MipmapGray8SrgbGmTest {
    @Test
    fun `draws each gray8 mip from the complete source image`() {
        val gm = MipmapGray8SrgbGm()
        val surface = Surface(gm.width, gm.height)
        val canvas = GmCanvas(surface.canvas(), gm.width, gm.height)

        gm.draw(canvas, gm.width, gm.height)

        val imageOps = surface.snapshotOps().filterIsInstance<DisplayOp.DrawImage>()
        assertEquals(8, imageOps.size)
        assertEquals(
            List(8) { RectF32(0f, 0f, 100f, 100f) },
            imageOps.map { it.src },
        )
    }
}
