@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.picture

import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.surface.W6bImageBlurCpuOracle
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.graphiks.kanvas.pipeline.ClipOp
import org.junit.jupiter.api.Test

/** Public Picture memory/wire witnesses for W6 contextual direct-filter bounds. */
class W6FilterBoundsRecipePictureTest {
    /**
     * Cold and warm use the same immutable Picture.  B is derived before its recorder: root
     * 2x1 (8), aggregate/direct-source/source/Crop/terminal 1x1 targets (5x4), two 16-byte W6
     * rows, and one 256-byte aligned readback row: 8+20+32+256=316.  A warm cache may skip an upload, never
     * the frozen target/lease charged through completion.
     */
    @Test
    fun warmPictureReplayRetainsColdBudgetAndIdentity() {
        val unit = RectF32.ofLTRB(0f, 0f, 1f, 1f)
        val full = RectF32.ofLTRB(0f, 0f, 2f, 1f)
        val expected = ubyteArrayOf(0u, 0u, 255u, 255u, 0u, 0u, 0u, 0u)
        val budgetB = listOf(
            Math.multiplyExact(2L, Math.multiplyExact(1L, 4L)),
            Math.multiplyExact(5L, Math.multiplyExact(1L, 4L)),
            Math.multiplyExact(2L, 16L),
            256L,
        ).fold(0L, Math::addExact)
        val picture = PictureRecorder().also { recorder ->
            recorder.beginRecording(unit).drawRect(unit, Paint(
                ColorARGB.Blue,
                imageFilter = ImageFilter.Crop(unit, TileMode.DECAL),
                antiAlias = false,
            ))
        }.finishRecordingAsPicture()
        fun record(surface: Surface) = surface.canvas {
            drawPicture(picture)
        }
        fun assertBudgetRefusal(surface: Surface) {
            val sentinel = UByteArray(8) { 0x5au }
            val before = sentinel.copyOf()
            val failure = assertFailsWith<IllegalStateException> { surface.readPixels(full, sentinel) }
            assertTrue(failure.message?.startsWith("w6b.filter.frame_budget_exceeded:") == true,
                failure.message ?: "missing W6 budget diagnostic")
            assertContentEquals(before, sentinel)
        }

        val coldRefusal = Surface(2, 1, config = RenderConfig(frameLocalBudgetBytes = Math.subtractExact(budgetB, 1L)))
        record(coldRefusal)
        assertBudgetRefusal(coldRefusal)

        val admitted = Surface(2, 1, config = RenderConfig(frameLocalBudgetBytes = budgetB))
        record(admitted)
        assertPixelsAndScopes(admitted, expected)
        admitted.discardRecordedOperations()
        record(admitted)
        assertPixelsAndScopes(admitted, expected)

        val warmRefusal = Surface(2, 1, config = RenderConfig(frameLocalBudgetBytes = Math.subtractExact(budgetB, 1L)))
        record(warmRefusal)
        assertBudgetRefusal(warmRefusal)
    }

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
                // This second hard clip is distinct from the recorder cull. It limits the child
                // source, while the enclosing Blur still owns its halo after Picture replay.
                clipRect(unit, ClipOp.INTERSECT, antiAlias = false)
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

    private fun assertPixelsAndScopes(surface: Surface, expected: UByteArray) {
        val result = surface.render()
        assertContentEquals(expected, result.pixels)
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")), result.nativeEvidenceScopeKinds.toString())
    }
}
