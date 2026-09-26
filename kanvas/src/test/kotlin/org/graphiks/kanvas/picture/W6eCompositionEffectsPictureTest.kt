@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.picture

import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.surface.RenderResult
import org.graphiks.kanvas.surface.Surface
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.Test

/** Public memory and wire replay custody for the W6e composition shard. */
class W6eCompositionEffectsPictureTest {
    @Test
    fun compositionShardPictureReplayKeepsBytesAndPixels() {
        // Expected arrays are independently complete before any PictureRecorder or Surface exists.
        val colorFilterExpected = ubyteArrayOf(0u, 0u, 0u, 54u)
        val composeExpected = ubyteArrayOf(0u, 0u, 0u, 0u, 0u, 0u, 0u, 54u)
        // W5f Blend uses linear-premul before the final sRGB OETF: two alpha-128 black inputs
        // over opaque red produce byte 136, not a naïve RGBA8 attenuation of 63.
        val mergeExpected = ubyteArrayOf(136u, 0u, 0u, 255u)
        val blendExpected = ubyteArrayOf(0u, 0u, 255u, 255u)
        val dilateExpected = opaqueRed(0, 1, 2, width = 3)
        val erodeExpected = opaqueRed(2, width = 5)

        val red = ImageFilter.ColorFilter(ColorFilter.Blend(ColorARGB.Red, BlendMode.SRC))
        val blue = ImageFilter.ColorFilter(ColorFilter.Blend(ColorARGB.Blue, BlendMode.SRC))
        val sharedBlack = ImageFilter.ColorFilter(ColorFilter.Blend(
            ColorARGB.of(128, 0, 0, 0), BlendMode.SRC,
        ))
        val fixtures = listOf(
            fixture("ColorFilter", 1, colorFilterExpected, ImageFilter.ColorFilter(ColorFilter.Luma),
                RectF32.ofLTRB(0f, 0f, 1f, 1f), ColorARGB.Red),
            fixture("Compose", 2, composeExpected, ImageFilter.Compose(
                ImageFilter.ColorFilter(ColorFilter.Luma), ImageFilter.Offset(1f, 0f),
            ), RectF32.ofLTRB(0f, 0f, 1f, 1f), ColorARGB.Red),
            fixture("Merge", 1, mergeExpected, ImageFilter.Merge(listOf(red, sharedBlack, sharedBlack)),
                RectF32.ofLTRB(0f, 0f, 1f, 1f), ColorARGB.White),
            fixture("Blend", 1, blendExpected, ImageFilter.Blend(BlendMode.SRC_IN, red, blue),
                RectF32.ofLTRB(0f, 0f, 1f, 1f), ColorARGB.White),
            fixture("Dilate", 3, dilateExpected, ImageFilter.Dilate(1f, 0f),
                RectF32.ofLTRB(1f, 0f, 2f, 1f), ColorARGB.Red),
            fixture("Erode", 5, erodeExpected, ImageFilter.Erode(1f, 0f),
                RectF32.ofLTRB(1f, 0f, 4f, 1f), ColorARGB.Red),
        )
        val captured = fixtures.map { fixture -> fixture to fixture.record() }

        // The mutable caller-owned rectangles change only after capture.  Both in-memory and
        // wire replay must keep the captured source, including the contextual and shared nodes.
        fixtures.forEach { it.sourceRect.setLTRB(0f, 0f, 0f, 0f) }

        captured.forEach { (fixture, picture) ->
            val bytes = picture.toByteArray()
            val decoded = assertNotNull(Picture.fromByteArray(bytes), fixture.name)
            assertContentEquals(bytes, decoded.toByteArray(), fixture.name)
            fixture.assertPixels(render(fixture, picture))
            fixture.assertPixels(render(fixture, decoded))
        }
    }

    private fun fixture(
        name: String,
        width: Int,
        expected: UByteArray,
        filter: ImageFilter,
        sourceRect: RectF32,
        sourceColor: ColorARGB,
    ) = Fixture(name, width, expected, filter, sourceRect, sourceColor)

    private fun render(fixture: Fixture, picture: Picture): RenderResult = Surface(fixture.width, 1).also { surface ->
        surface.canvas { drawPicture(picture) }
    }.render()

    private class Fixture(
        val name: String,
        val width: Int,
        private val expected: UByteArray,
        private val filter: ImageFilter,
        val sourceRect: RectF32,
        private val sourceColor: ColorARGB,
    ) {
        fun record(): Picture = PictureRecorder().also { recorder ->
            recorder.beginRecording(RectF32.ofLTRB(0f, 0f, width.toFloat(), 1f)).apply {
                saveLayer(SaveLayerRec(paint = Paint(imageFilter = filter, antiAlias = false)))
                drawRect(sourceRect, Paint(sourceColor, antiAlias = false))
                restore()
            }
        }.finishRecordingAsPicture()

        fun assertPixels(result: RenderResult) {
            assertContentEquals(expected, result.pixels, name)
            assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
                "$name: ${result.nativeEvidenceScopeKinds}")
        }
    }

    private fun opaqueRed(vararg indices: Int, width: Int): UByteArray = UByteArray(width * 4).also { result ->
        indices.forEach { index ->
            result[index * 4] = 255u
            result[index * 4 + 3] = 255u
        }
    }
}
