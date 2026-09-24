@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.Test

class W6dPictureFilterSurfacePixelTest {
    @Test
    fun filterPictureIgnoresCarrierPixelsAndSamplesOnlySealedScene() {
        val expected = ubyteArrayOf(255u, 0u, 0u, 255u)
        val rect = RectF32.ofLTRB(0f, 0f, 1f, 1f)
        val recorder = PictureRecorder()
        recorder.beginRecording(rect).drawRect(rect, Paint(ColorARGB.Red, antiAlias = false))
        val picture = recorder.finishRecordingAsPicture()
        val surface = Surface(1, 1)
        surface.canvas {
            drawRect(rect, Paint(ColorARGB.Blue, imageFilter = ImageFilter.Picture(picture), antiAlias = false))
        }
        val result = surface.render()
        assertContentEquals(expected, result.pixels)
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")))
    }
}
