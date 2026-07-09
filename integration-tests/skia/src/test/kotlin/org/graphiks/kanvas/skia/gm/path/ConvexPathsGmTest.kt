package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConvexPathsGmTest {
    @Test
    fun `draws black background before convex paths`() {
        val gm = ConvexPathsGm()
        val surface = Surface(gm.width, gm.height)
        val canvas = GmCanvas(surface.canvas(), gm.width, gm.height)

        gm.draw(canvas, gm.width, gm.height)

        val ops = surface.snapshotOps()
        val firstRect = ops.firstOrNull() as? DisplayOp.DrawRect
        requireNotNull(firstRect) { "expected first op to be a DrawRect background" }
        assertEquals(Color.BLACK, firstRect.paint.color)
        assertEquals(0f, firstRect.rect.left)
        assertEquals(0f, firstRect.rect.top)
        assertEquals(gm.width.toFloat(), firstRect.rect.right)
        assertEquals(gm.height.toFloat(), firstRect.rect.bottom)
        assertTrue(ops.drop(1).filterIsInstance<DisplayOp.DrawPath>().isNotEmpty())
    }
}
