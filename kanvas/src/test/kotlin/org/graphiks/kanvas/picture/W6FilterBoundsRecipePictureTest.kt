@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.picture

import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.surface.W6bImageBlurCpuOracle
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.graphiks.kanvas.pipeline.ClipOp
import org.junit.jupiter.api.Test

/** Public Picture memory/wire witnesses for W6 contextual direct-filter bounds. */
class W6FilterBoundsRecipePictureTest {
    /**
     * Removing a filtered Picture-entry's early terminal production leaves only its unfiltered
     * sibling in the aggregate: the blue Offset output at x=1 then disappears at parent restore.
     */
    @Test
    fun directOffsetUnderPictureAggregateSurvivesParentRestore() {
        val expected = ubyteArrayOf(
            255u, 0u, 0u, 255u,
            0u, 0u, 255u, 255u,
            0u, 0u, 0u, 0u,
            0u, 0u, 0u, 0u,
        )
        val unit = RectF32.ofLTRB(0f, 0f, 1f, 1f)
        val child = PictureRecorder().also { recorder ->
            recorder.beginRecording(unit).drawRect(unit, Paint(ColorARGB.Blue, antiAlias = false))
        }.finishRecordingAsPicture()
        val parent = PictureRecorder().also { recorder ->
            recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 4f, 1f)).apply {
                drawRect(unit, Paint(ColorARGB.Red, antiAlias = false))
                drawPicture(child, Paint(imageFilter = ImageFilter.Offset(1f, 0f), antiAlias = false))
            }
        }.finishRecordingAsPicture()

        val result = Surface(4, 1).also { surface -> surface.canvas { drawPicture(parent) } }.render()
        assertContentEquals(expected, result.pixels)
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")), result.nativeEvidenceScopeKinds.toString())
    }

    /**
     * The parent Blur must see all three filtered Picture entries, including the two that share
     * one Offset node and the equal-but-distinct third node. The outer deferred composite clip
     * remains a terminal consumer clip and cannot clip the parent filter's halo before replay.
     */
    @Test
    fun sharedAndEqualDistinctPictureFiltersKeepDemandAndIdentityAcrossWire() {
        val expected = W6bImageBlurCpuOracle.toOpaqueWhiteRgba(W6bImageBlurCpuOracle.blurredAlpha(
            9, 1, UByteArray(9).also { alpha -> alpha[0] = 255u; alpha[2] = 255u; alpha[4] = 255u },
            1f, 0f, TileMode.DECAL, knownRight = 7,
        ))
        val unit = RectF32.ofLTRB(0f, 0f, 1f, 1f)
        val child = PictureRecorder().also { recorder ->
            recorder.beginRecording(unit).apply {
                // The recorder cull is a serializable DeviceRect hard clip: the wider primitive
                // proves it limits the child's source, while the enclosing Blur still owns halo.
                drawRect(RectF32.ofLTRB(-1f, 0f, 2f, 1f), Paint(ColorARGB.White, antiAlias = false))
            }
        }.finishRecordingAsPicture()
        val shared = ImageFilter.Picture(child)
        val equalButDistinct = ImageFilter.Picture(child)
        val recorded = PictureRecorder().also { recorder ->
            recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 7f, 1f)).apply {
                // The recorder clip is deferred by saveLayer and must remain terminal on replay.
                saveLayer()
                drawRect(unit, Paint(imageFilter = shared, antiAlias = false))
                save()
                translate(2f, 0f)
                drawRect(unit, Paint(imageFilter = shared, antiAlias = false))
                restore()
                save()
                translate(4f, 0f)
                drawRect(unit, Paint(imageFilter = equalButDistinct, antiAlias = false))
                restore()
                restore()
            }
        }.finishRecordingAsPicture()
        val replay = assertNotNull(Picture.fromByteArray(recorded.toByteArray()))

        assertFilterIdentity(recorded)
        assertFilterIdentity(replay)
        for (candidate in listOf(recorded, replay)) {
            val result = Surface(9, 1).also { surface ->
                surface.canvas {
                    clipRect(RectF32.ofLTRB(0f, 0f, 9f, 1f), ClipOp.INTERSECT, antiAlias = false)
                    drawPicture(candidate, Paint(imageFilter = ImageFilter.Blur(1f, 0f, TileMode.DECAL), antiAlias = false))
                }
            }.render()
            W6bImageBlurCpuOracle.assertNear(expected, result.pixels)
            assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")), result.nativeEvidenceScopeKinds.toString())
        }
    }

    private fun assertFilterIdentity(picture: Picture) {
        val filters = buildList {
            picture.forEachOp { operation ->
                (operation as? DisplayOp.DrawRect)?.paint?.imageFilter?.let(::add)
            }
        }
        assertSame(filters[0], filters[1])
        assertNotSame(filters[0], filters[2])
    }
}
