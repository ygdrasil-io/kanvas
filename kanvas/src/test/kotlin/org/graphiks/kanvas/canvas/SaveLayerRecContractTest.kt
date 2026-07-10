package org.graphiks.kanvas.canvas

import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Rect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SaveLayerRecContractTest {
    @Test
    fun `save layer record preserves the animated backdrop filter graph`() {
        val crop = Rect.fromLTRB(0f, 100f, 512f, 400f)
        val backdrop = ImageFilter.Crop(
            crop = crop,
            tileMode = TileMode.DECAL,
            input = ImageFilter.Blur(
                sigmaX = 30f,
                sigmaY = 30f,
                input = ImageFilter.Crop(crop, TileMode.MIRROR),
            ),
        )
        val rec = SaveLayerRec(backdrop = backdrop)
        val surface = Surface(width = 512, height = 1024)

        surface.canvas().saveLayer(rec)
        surface.canvas().restore()

        assertEquals(
            listOf(DisplayOp.BeginLayer(rec), DisplayOp.EndLayer),
            surface.snapshotOps(),
        )
    }
}
