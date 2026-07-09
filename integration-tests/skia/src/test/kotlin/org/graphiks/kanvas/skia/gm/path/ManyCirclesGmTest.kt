package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.surface.Surface
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ManyCirclesGmTest {
    @Test
    fun `draws ten thousand oval path operations`() {
        val gm = ManyCirclesGm()
        val surface = Surface(gm.width, gm.height)
        val canvas = GmCanvas(surface.canvas(), gm.width, gm.height)

        gm.draw(canvas, gm.width, gm.height)

        assertEquals(10_000, surface.snapshotOps().filterIsInstance<DisplayOp.DrawPath>().size)
    }
}
