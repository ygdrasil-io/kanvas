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
    fun `late W6d Magnifier sibling is admitted with pixels and same surface recovers`() {
        val bounds = RectF32.ofLTRB(0f, 0f, 1f, 1f)
        // The first Crop writes red, then the explicit blue Magnifier samples its only texel
        // at zoom=1/inset=0 and SRC_OVER replaces that red.  This oracle is fixed before
        // either public recording object is created.
        val expected = ubyteArrayOf(0u, 0u, 255u, 255u)
        val surface = Surface(1, 1)
        surface.canvas {
            saveLayer(SaveLayerRec(paint = Paint(imageFilter = ImageFilter.Crop(bounds))))
            drawRect(bounds, Paint(ColorARGB.Red, antiAlias = false))
            restore()
            drawRect(bounds, Paint(
                ColorARGB.Blue,
                imageFilter = ImageFilter.Magnifier(bounds, zoom = 1f, inset = 0f),
                antiAlias = false,
            ))
        }

        val rendered = surface.render()
        assertContentEquals(expected, rendered.pixels)
        assertTrue(rendered.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            rendered.nativeEvidenceScopeKinds.toString())
        val readback = UByteArray(4)
        assertTrue(surface.readPixels(bounds, readback))
        assertContentEquals(expected, readback)

        surface.discardRecordedOperations()
        surface.canvas { drawRect(bounds, Paint(ColorARGB.Blue, antiAlias = false)) }
        assertContentEquals(ubyteArrayOf(0u, 0u, 255u, 255u), surface.render().pixels)
    }
}
