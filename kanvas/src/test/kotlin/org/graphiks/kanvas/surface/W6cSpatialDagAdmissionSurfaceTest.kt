@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
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
    fun `crop is W6-owned`() {
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

    @Test
    fun `late W6d sibling refuses without readback mutation and same surface recovers`() {
        val bounds = RectF32.ofLTRB(0f, 0f, 1f, 1f)
        val sentinel = UByteArray(4) { 0x5au }
        val before = sentinel.copyOf()
        val surface = Surface(1, 1)
        surface.canvas {
            saveLayer(SaveLayerRec(paint = Paint(imageFilter = ImageFilter.Crop(bounds))))
            drawRect(bounds, Paint(ColorARGB.Red, antiAlias = false))
            restore()
            drawRect(bounds, Paint(imageFilter = ImageFilter.Magnifier(bounds, zoom = 1f, inset = 0f)))
        }

        val failure = assertFailsWith<IllegalStateException> { surface.readPixels(bounds, sentinel) }
        assertTrue(failure.message?.startsWith("w6b.filter.unsupported_family:") == true,
            failure.message ?: "missing diagnostic")
        assertContentEquals(before, sentinel)

        surface.discardRecordedOperations()
        surface.canvas { drawRect(bounds, Paint(ColorARGB.Blue, antiAlias = false)) }
        assertContentEquals(ubyteArrayOf(0u, 0u, 255u, 255u), surface.render().pixels)
    }
}
