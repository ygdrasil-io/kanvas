@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.Test

/** Public byte-exact W6c evidence for ordered Merge and Blend filter inputs. */
class W6cMultiInputSurfaceTest {
    @Test
    fun mergePreservesDuplicateInputOrder() {
        // Three luma(red) layers are source-overed in this order.  Each starts at alpha 54;
        // the independent 8-bit source-over recurrence yields alpha 130 after three inputs.
        val expected = ubyteArrayOf(0u, 0u, 0u, 130u)
        val shared = ImageFilter.ColorFilter(ColorFilter.Luma)
        val filter = ImageFilter.Merge(listOf(shared, shared, shared))

        val surface = Surface(1, 1)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.Red, imageFilter = filter, antiAlias = false))
        }

        assertContentEquals(expected, surface.render().pixels)
    }

    @Test
    fun blendPreservesBackgroundForegroundOrder() {
        // SRC_IN keeps its foreground. Reversing the frozen inputs would return red, not blue.
        val expected = ubyteArrayOf(0u, 0u, 255u, 255u)
        val background = ImageFilter.ColorFilter(ColorFilter.Blend(ColorARGB.Red, BlendMode.SRC))
        val foreground = ImageFilter.ColorFilter(ColorFilter.Blend(ColorARGB.Blue, BlendMode.SRC))
        val filter = ImageFilter.Blend(BlendMode.SRC_IN, background, foreground)

        val surface = Surface(1, 1)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.White, imageFilter = filter, antiAlias = false))
        }

        assertContentEquals(expected, surface.render().pixels)
    }

    @Test
    fun equalButDistinctPublicSubtreesDoNotAlias() {
        // These two Luma instances compare equal but are distinct public subtrees.  Their bound
        // Offset sources differ, so the merged result occupies both texels.
        val expected = ubyteArrayOf(0u, 0u, 0u, 54u, 0u, 0u, 0u, 54u)
        val first = ImageFilter.ColorFilter(ColorFilter.Luma)
        val second = ImageFilter.ColorFilter(ColorFilter.Luma)
        val filter = ImageFilter.Merge(listOf(
            ImageFilter.Compose(first, ImageFilter.Offset(0f, 0f)),
            ImageFilter.Compose(second, ImageFilter.Offset(1f, 0f)),
        ))

        val surface = Surface(2, 1)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.Red, imageFilter = filter, antiAlias = false))
        }

        assertContentEquals(expected, surface.render().pixels)
    }

    @Test
    fun sharedCapturedInputRemainsReplayStable() {
        // The same public instance is deliberately repeated. Both memory and wire replay must
        // preserve the ordered three-input source-over result fixed above.
        val expected = ubyteArrayOf(0u, 0u, 0u, 130u)
        val shared = ImageFilter.ColorFilter(ColorFilter.Luma)
        val picture = PictureRecorder().also { recorder ->
            recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 1f, 1f)).drawRect(
                RectF32.ofLTRB(0f, 0f, 1f, 1f),
                Paint(ColorARGB.Red, imageFilter = ImageFilter.Merge(listOf(shared, shared, shared)), antiAlias = false),
            )
        }.finishRecordingAsPicture()

        assertContentEquals(expected, render(picture))
        assertContentEquals(expected, render(assertNotNull(Picture.fromByteArray(picture.toByteArray()))))
    }

    private fun render(picture: Picture): UByteArray = Surface(1, 1).also { surface ->
        surface.canvas { picture.playback(this) }
    }.render().pixels
}
