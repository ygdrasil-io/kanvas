package org.graphiks.kanvas.skia.gm.blur

import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Rect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AnimatedBackdropBlurGmTest {
    @Test
    fun `records the upstream crop blur crop backdrop layer`() {
        val gm = AnimatedBackdropBlurGm()
        val surface = Surface(gm.width, gm.height)
        val canvas = GmCanvas(surface.canvas(), gm.width, gm.height)

        gm.onOnceBeforeDraw(canvas)
        gm.draw(canvas, gm.width, gm.height)

        val rec = surface.snapshotOps().filterIsInstance<DisplayOp.BeginLayer>().single().rec
        assertEquals(
            SaveLayerRec(
                backdrop = ImageFilter.Crop(
                    crop = Rect.fromLTRB(0f, 100f, 512f, 400f),
                    tileMode = TileMode.DECAL,
                    input = ImageFilter.Blur(
                        sigmaX = 30f,
                        sigmaY = 30f,
                        input = ImageFilter.Crop(
                            crop = Rect.fromLTRB(0f, 100f, 512f, 400f),
                            tileMode = TileMode.MIRROR,
                        ),
                    ),
                ),
            ),
            rec,
        )
        assertTrue(surface.snapshotOps().any { it is DisplayOp.DrawText })
        assertTrue(surface.snapshotOps().any { it is DisplayOp.DrawImage })
    }
}
