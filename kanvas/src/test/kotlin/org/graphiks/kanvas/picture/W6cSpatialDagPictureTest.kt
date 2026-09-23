@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.picture

import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.surface.Surface
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.color.ColorMatrixF32
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.Test

/** Public Picture memory and wire regression coverage for W6c contextual Compose. */
class W6cSpatialDagPictureTest {
    /**
     * Every non-spatial W6c root still has to propagate reverse source demand when it is applied
     * by Paint to an external Picture.  These expected bytes are fixed before either Surface is
     * created: the identity matrix preserves red; Compose applies its identity outer filter to
     * the blue inner result (rather than incorrectly rebinding it to the original red input);
     * the ordered Merge/Blend end in blue.
     */
    @Test
    fun externalPicturePaintSupportsColorFilterComposeMergeAndBlendRoots() {
        val picture = PictureRecorder().also { recorder ->
            recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 2f, 1f)).drawRect(
                RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.Red, antiAlias = false),
            )
        }.finishRecordingAsPicture()
        val red = ImageFilter.ColorFilter(ColorFilter.Blend(ColorARGB.Red, BlendMode.SRC))
        val blue = ImageFilter.ColorFilter(ColorFilter.Blend(ColorARGB.Blue, BlendMode.SRC))

        assertContentEquals(
            ubyteArrayOf(255u, 0u, 0u, 255u, 0u, 0u, 0u, 0u),
            renderExternal(picture, ImageFilter.ColorFilter(ColorFilter.Matrix(ColorMatrixF32.ofIdentity()))),
        )
        assertContentEquals(
            ubyteArrayOf(0u, 0u, 255u, 255u, 0u, 0u, 255u, 255u),
            renderExternal(
                picture,
                ImageFilter.Compose(
                    ImageFilter.ColorFilter(ColorFilter.Matrix(ColorMatrixF32.ofIdentity())),
                    blue,
                ),
            ),
        )
        assertContentEquals(
            ubyteArrayOf(0u, 0u, 255u, 255u, 0u, 0u, 255u, 255u),
            renderExternal(picture, ImageFilter.Merge(listOf(red, blue, blue))),
        )
        assertContentEquals(
            ubyteArrayOf(0u, 0u, 255u, 255u, 0u, 0u, 255u, 255u),
            renderExternal(picture, ImageFilter.Blend(BlendMode.SRC_IN, red, blue)),
        )
    }

    @Test
    fun postCaptureMutationDoesNotChangeComposePixels() {
        // Computed before recording: an identity outer ColorFilter retains a red source shifted
        // one texel right. The supplied matrix is then mutated to transparent after capture.
        val expectedComposeBytes = ubyteArrayOf(0u, 0u, 0u, 0u, 255u, 0u, 0u, 255u)
        val matrix = ColorMatrixF32.ofIdentity()
        val filter = ImageFilter.Compose(
            ImageFilter.ColorFilter(ColorFilter.Matrix(matrix)),
            ImageFilter.Offset(1f, 0f),
        )
        val picture = PictureRecorder().also { recorder ->
            recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 2f, 1f)).drawRect(
                RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.Red, imageFilter = filter, antiAlias = false),
            )
        }.finishRecordingAsPicture()
        matrix.setScale(0f, 0f, 0f, 0f)

        assertContentEquals(expectedComposeBytes, render(picture))
    }

    @Test
    fun memoryAndWireReplayPreserveComposePixels() {
        // Independent source-domain result: Offset(1) then Luma is transparent at x=0 and
        // transparent-black alpha 54 at x=1. It is fixed before either replay path is made.
        val expectedComposeBytes = ubyteArrayOf(0u, 0u, 0u, 0u, 0u, 0u, 0u, 54u)
        val picture = PictureRecorder().also { recorder ->
            recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 2f, 1f)).drawRect(
                RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(
                    ColorARGB.Red,
                    imageFilter = ImageFilter.Compose(
                        ImageFilter.ColorFilter(ColorFilter.Luma), ImageFilter.Offset(1f, 0f),
                    ),
                    antiAlias = false,
                ),
            )
        }.finishRecordingAsPicture()

        val replay = assertNotNull(Picture.fromByteArray(picture.toByteArray()))
        assertContentEquals(expectedComposeBytes, render(picture))
        assertContentEquals(expectedComposeBytes, render(replay))
    }

    private fun render(picture: Picture): UByteArray = Surface(2, 1).also { surface ->
        surface.canvas { picture.playback(this) }
    }.render().pixels

    private fun renderExternal(picture: Picture, filter: ImageFilter): UByteArray = Surface(2, 1).also { surface ->
        surface.canvas { drawPicture(picture, Paint(imageFilter = filter, antiAlias = false)) }
    }.render().pixels
}
