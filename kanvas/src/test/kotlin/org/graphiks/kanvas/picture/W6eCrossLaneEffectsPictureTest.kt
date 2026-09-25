@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.picture

import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.surface.RenderResult
import org.graphiks.kanvas.surface.Surface
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.Test

/** Public memory/wire replay of a nested W6 graph with shared and equal-distinct filter nodes. */
class W6eCrossLaneEffectsPictureTest {
    @Test
    fun crossLanePictureKeepsSharingWithoutValueAliasing() {
        // The expected attachment is fixed before either a PictureRecorder or Surface exists.
        val expected = rgba(ColorARGB.Red) + rgba(ColorARGB.Green) + rgba(ColorARGB.Blue)
        val shared = ImageFilter.Offset(0f, 0f)
        val equalButDistinct = ImageFilter.Offset(0f, 0f)

        val picture = PictureRecorder().also { recorder ->
            recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 3f, 1f)).apply {
                saveLayer()
                drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.Red, imageFilter = shared, antiAlias = false))
                saveLayer(SaveLayerRec(paint = Paint(imageFilter = shared, antiAlias = false)))
                drawRect(RectF32.ofLTRB(1f, 0f, 2f, 1f), Paint(ColorARGB.Green, imageFilter = shared, antiAlias = false))
                drawRect(RectF32.ofLTRB(2f, 0f, 3f, 1f), Paint(ColorARGB.Blue, imageFilter = equalButDistinct, antiAlias = false))
                restore()
                restore()
            }
        }.finishRecordingAsPicture()
        val wire = assertNotNull(Picture.fromByteArray(picture.toByteArray()))

        listOf(picture, wire).forEach { candidate ->
            val filters = filterOccurrences(candidate)
            assertSame(filters[0], filters[1])
            assertSame(filters[1], filters[2])
            assertNotSame(filters[0], filters[3])
            assertEquals(filters[0], filters[3])
            assertPicturePixels(expected, candidate)
        }
    }

    private fun filterOccurrences(picture: Picture): List<ImageFilter> = buildList {
        picture.forEachOp { operation ->
            when (operation) {
                is DisplayOp.DrawRect -> operation.paint.imageFilter?.let(::add)
                is DisplayOp.BeginLayer -> operation.paint?.imageFilter?.let(::add)
                else -> Unit
            }
        }
    }

    private fun assertPicturePixels(expected: UByteArray, picture: Picture) {
        val result = Surface(3, 1).also { surface -> surface.canvas { drawPicture(picture) } }.render()
        assertContentEquals(expected, result.pixels)
        assertRenderAndReadback(result)
    }

    private fun assertRenderAndReadback(result: RenderResult) {
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            result.nativeEvidenceScopeKinds.toString())
    }

    private fun rgba(color: ColorARGB): UByteArray =
        ubyteArrayOf(color.red.toUByte(), color.green.toUByte(), color.blue.toUByte(), color.alpha.toUByte())
}
