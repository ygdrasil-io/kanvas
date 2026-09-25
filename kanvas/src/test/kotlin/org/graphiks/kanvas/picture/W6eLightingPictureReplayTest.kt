@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.picture

import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.surface.Surface
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.Test

/** Public memory/wire replay witness for immutable Picture-filter source and cull capture. */
class W6eLightingPictureReplayTest {
    @Test
    fun pictureFilterPreservesImmutableReplay() {
        val expected = ubyteArrayOf(
            255u, 0u, 0u, 255u,
            0u, 0u, 0u, 0u,
        )
        val sourceCull = RectF32.ofLTRB(0f, 0f, 2f, 1f)
        val source = PictureRecorder().also { recorder ->
            recorder.beginRecording(sourceCull).apply {
                drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.Red, antiAlias = false))
                drawRect(RectF32.ofLTRB(1f, 0f, 2f, 1f), Paint(ColorARGB.Green, antiAlias = false))
            }
        }.finishRecordingAsPicture()
        val filterSource = RectF32.ofLTRB(0f, 0f, 1f, 1f)
        val carrier = RectF32.ofLTRB(0f, 0f, 2f, 1f)
        val recorded = PictureRecorder().also { recorder ->
            recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 2f, 1f)).drawRect(
                carrier,
                Paint(ColorARGB.Blue, imageFilter = ImageFilter.Picture(source, filterSource), antiAlias = false),
            )
        }.finishRecordingAsPicture()

        sourceCull.setLTRB(1f, 0f, 2f, 1f)
        filterSource.setLTRB(1f, 0f, 2f, 1f)
        carrier.setLTRB(0f, 0f, 0f, 0f)

        val wire = assertNotNull(Picture.fromByteArray(recorded.toByteArray()))
        listOf(recorded, wire).forEach { candidate ->
            val result = Surface(2, 1).also { surface ->
                surface.canvas { drawPicture(candidate) }
            }.render()
            assertContentEquals(expected, result.pixels)
            assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")), result.nativeEvidenceScopeKinds.toString())
        }
    }
}
