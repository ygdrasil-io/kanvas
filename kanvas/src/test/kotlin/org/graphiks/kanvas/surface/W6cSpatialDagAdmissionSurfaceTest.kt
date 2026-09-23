@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.Test

/** Public W6c root admission evidence, kept at the 1x1 RGBA8 frozen vertical slice. */
class W6cSpatialDagAdmissionSurfaceTest {
    @Test
    fun `crop is W6-owned and unsupported W6d sibling stays terminal`() {
        val before = UByteArray(4) { 0x5au }
        val surface = Surface(1, 1)
        surface.canvas {
            saveLayer(SaveLayerRec(paint = Paint(imageFilter = ImageFilter.Crop(
                RectF32.ofLTRB(0f, 0f, 1f, 1f),
            ))))
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.Red, antiAlias = false))
            restore()
        }
        assertContentEquals(ubyteArrayOf(255u, 0u, 0u, 255u), surface.render().pixels)
        assertTrue(surface.readPixels(RectF32.ofLTRB(0f, 0f, 1f, 1f), before))
        assertContentEquals(ubyteArrayOf(255u, 0u, 0u, 255u), before)
    }
}
