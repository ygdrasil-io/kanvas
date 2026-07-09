package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.surface.Surface
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AllBitmapConfigsGmTest {
    @Test
    fun `draws one full-size image for each bitmap config`() {
        val gm = AllBitmapConfigsGm()
        val surface = Surface(gm.width, gm.height)
        val canvas = GmCanvas(surface.canvas(), gm.width, gm.height)

        gm.draw(canvas, gm.width, gm.height)

        val imageOps = surface.snapshotOps().filterIsInstance<DisplayOp.DrawImage>()
        assertEquals(
            listOf(
                ColorType.RGBA_8888,
                ColorType.RGB_565,
                ColorType.ARGB_4444,
                ColorType.RGBA_F16,
                ColorType.ALPHA_8,
                ColorType.GRAY_8,
            ),
            imageOps.map { it.image.colorType },
        )
        assertEquals(List(6) { 128 }, imageOps.map { it.image.width })
        assertEquals(List(6) { 128 }, imageOps.map { it.image.height })
        assertEquals(6, imageOps.map { it.image.sourceId }.distinct().size)
    }
}
