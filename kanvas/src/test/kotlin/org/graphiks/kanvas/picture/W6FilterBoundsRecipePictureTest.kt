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
import org.graphiks.math.geometry.SizeF32
import org.graphiks.math.vector.Vector2F32
import org.graphiks.kanvas.pipeline.ClipOp
import org.junit.jupiter.api.Test

/** Public Picture memory/wire witnesses for W6 contextual direct-filter bounds. */
class W6FilterBoundsRecipePictureTest {
    /** The parent must reserve the tiny Picture's terminal output before its own allocation. */
    @Test
    fun tinyTopLevelPictureKeepsParentContentSizedBudget() {
        val unit = RectF32.ofLTRB(1f, 0f, 2f, 1f)
        val full = RectF32.ofLTRB(0f, 0f, 64f, 1f)
        val expected = UByteArray(256).also { it[6] = 255u; it[7] = 255u }
        // Root/readback 256 each; parent, aggregate, shaded source, Crop: four 1x1
        // RGBA8 targets; frame, solid-color material and graph-texture material rows
        // 16 each. B = 576.
        val budgetB = listOf(256L, 256L, Math.multiplyExact(4L, 4L), 16L, 32L).fold(0L, Math::addExact)
        val picture = PictureRecorder().also { recorder ->
            recorder.beginRecording(unit).drawRect(unit, Paint(ColorARGB.Blue, antiAlias = false))
        }.finishRecordingAsPicture()
        fun record(surface: Surface) = surface.canvas {
            saveLayer()
            drawPicture(picture, Paint(imageFilter = ImageFilter.Crop(unit, TileMode.DECAL), antiAlias = false))
            restore()
        }
        for (candidate in listOf(picture, assertNotNull(Picture.fromByteArray(picture.toByteArray())))) {
            val admitted = Surface(64, 1, config = RenderConfig(frameLocalBudgetBytes = budgetB))
            admitted.canvas {
                saveLayer()
                drawPicture(candidate, Paint(imageFilter = ImageFilter.Crop(unit, TileMode.DECAL), antiAlias = false))
                restore()
            }
            assertPixelsAndScopes(admitted, expected)
        }
        val refused = Surface(64, 1, config = RenderConfig(frameLocalBudgetBytes = budgetB - 1L))
        record(refused)
        assertBudgetRefusalAndRecovery(refused, full, unit, expected)
    }

    /** A large cull must not become the finite source edge of a tiny filtered draw. */
    @Test
    fun tinyFilteredPictureDrawKeepsFiniteSourceBudgetAndClampEdge() {
        val unit = RectF32.ofLTRB(1f, 0f, 2f, 1f)
        val full = RectF32.ofLTRB(0f, 0f, 64f, 1f)
        val expected = UByteArray(256).also { it[6] = 255u; it[7] = 255u }
        val crop = ImageFilter.Crop(unit, TileMode.DECAL)
        val clamp = ImageFilter.MatrixConvolution(SizeF32.of(3f, 1f), floatArrayOf(1f, 0f, 0f),
            1f, 0f, Vector2F32(1f, 0f), TileMode.CLAMP, true)
        // Root/readback 256 each, coverage/shaded/filter 1x1 targets (12), frame/filter
        // rows (32): Crop B=556. Matrix coefficients are frozen in its program, whose
        // logical lease adds 4096 bytes: Matrix B=4652.
        val cropBudgetB = listOf(256L, 256L, Math.multiplyExact(3L, 4L), 16L, 16L).fold(0L, Math::addExact)
        for ((filter, budgetB) in listOf(crop to cropBudgetB, clamp to Math.addExact(cropBudgetB, 4096L))) {
            val picture = PictureRecorder().also { recorder ->
                recorder.beginRecording(full).drawRect(unit, Paint(ColorARGB.Blue, imageFilter = filter, antiAlias = false))
            }.finishRecordingAsPicture()
            for (candidate in listOf(picture, assertNotNull(Picture.fromByteArray(picture.toByteArray())))) {
                val admitted = Surface(64, 1, config = RenderConfig(frameLocalBudgetBytes = budgetB))
                admitted.canvas { drawPicture(candidate) }
                assertPixelsAndScopes(admitted, expected)
            }
            val refused = Surface(64, 1, config = RenderConfig(frameLocalBudgetBytes = budgetB - 1L))
            refused.canvas { drawPicture(picture) }
            assertBudgetRefusalAndRecovery(refused, full, unit, expected,
                if (filter === clamp) "w6d.layer.frame_budget_exceeded:" else "w6b.filter.frame_budget_exceeded:")
        }
    }

    private fun assertBudgetRefusalAndRecovery(surface: Surface, full: RectF32, unit: RectF32, expected: UByteArray,
        diagnostic: String = "w6b.filter.frame_budget_exceeded:") {
        val sentinel = UByteArray(expected.size) { 0x5au }
        val before = sentinel.copyOf()
        val failure = assertFailsWith<IllegalStateException> { surface.readPixels(full, sentinel) }
        assertTrue(failure.message?.startsWith(diagnostic) == true,
            failure.message ?: "missing W6 budget diagnostic")
        assertContentEquals(before, sentinel)
        surface.discardRecordedOperations()
        surface.canvas { drawRect(unit, Paint(ColorARGB.Blue, antiAlias = false)) }
        assertPixelsAndScopes(surface, expected)
    }

    @Test
    fun filteredPictureDrawClampsAtItsRasterEdge() {
        val unit = RectF32.ofLTRB(1f, 0f, 2f, 1f)
        val expected = UByteArray(256).also { it[6] = 255u; it[7] = 255u }
        val clamp = ImageFilter.MatrixConvolution(SizeF32.of(3f, 1f), floatArrayOf(1f, 0f, 0f),
            1f, 0f, Vector2F32(1f, 0f), TileMode.CLAMP, true)
        val picture = PictureRecorder().also { recorder ->
            recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 64f, 1f))
                .drawRect(unit, Paint(ColorARGB.Blue, imageFilter = clamp, antiAlias = false))
        }.finishRecordingAsPicture()
        val surface = Surface(64, 1)
        surface.canvas { drawPicture(picture) }
        assertPixelsAndScopes(surface, expected)
    }

    /** Local prepared IDs must be published between filter-owned Pictures in captured order. */
    @Test
    fun interleavedPictureSourcesKeepIdentityAndInlineParentCoordinates() {
        val unit = RectF32.ofLTRB(4f, 0f, 5f, 1f)
        val expected = UByteArray(32).also {
            it[18] = 255u; it[19] = 255u
            it[20] = 255u; it[23] = 255u
        }
        val red = PictureRecorder().also { recorder ->
            recorder.beginRecording(unit).drawRect(unit, Paint(ColorARGB.Red, antiAlias = false))
        }.finishRecordingAsPicture()
        val blue = PictureRecorder().also { recorder ->
            recorder.beginRecording(unit).drawRect(unit, Paint(ColorARGB.Blue, antiAlias = false))
        }.finishRecordingAsPicture()
        val stream = PictureRecorder().also { recorder ->
            recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 8f, 1f)).apply {
                saveLayer()
                drawRect(unit, Paint(imageFilter = ImageFilter.Picture(blue), antiAlias = false))
                drawPicture(red)
                drawRect(unit, Paint(imageFilter = ImageFilter.Picture(blue), antiAlias = false))
                drawPicture(red, Paint(imageFilter = ImageFilter.Offset(1f, 0f), antiAlias = false))
                restore()
            }
        }.finishRecordingAsPicture()
        for (candidate in listOf(stream, assertNotNull(Picture.fromByteArray(stream.toByteArray())))) {
            for (nested in listOf(false, true)) {
                val surface = Surface(8, 1)
                surface.canvas {
                    saveLayer()
                    if (nested) drawPicture(candidate) else candidate.playback(this)
                    restore()
                }
                assertPixelsAndScopes(surface, expected)
            }
        }
    }

    /**
     * Cold and warm use the same immutable Picture.  B is derived before its recorder: root
     * 2x1 (8), coverage/shaded/Crop 1x1 targets (3x4), frame/material 16-byte
     * rows, and one 256-byte aligned readback row: 8+12+32+256=308. A warm cache may skip an upload, never
     * the frozen target/lease charged through completion.
     */
    @Test
    fun warmPictureReplayRetainsColdBudgetAndIdentity() {
        val unit = RectF32.ofLTRB(0f, 0f, 1f, 1f)
        val full = RectF32.ofLTRB(0f, 0f, 2f, 1f)
        val expected = ubyteArrayOf(0u, 0u, 255u, 255u, 0u, 0u, 0u, 0u)
        val budgetB = listOf(
            Math.multiplyExact(2L, Math.multiplyExact(1L, 4L)),
            Math.multiplyExact(3L, Math.multiplyExact(1L, 4L)),
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
        fun recordRecovery(surface: Surface) = surface.canvas {
            drawRect(unit, Paint(ColorARGB.Blue, antiAlias = false))
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
        coldRefusal.discardRecordedOperations()
        recordRecovery(coldRefusal)
        assertPixelsAndScopes(coldRefusal, expected)

        val admitted = Surface(2, 1, config = RenderConfig(frameLocalBudgetBytes = budgetB))
        record(admitted)
        assertPixelsAndScopes(admitted, expected)
        admitted.discardRecordedOperations()
        record(admitted)
        assertPixelsAndScopes(admitted, expected)

        val warmRefusal = Surface(2, 1, config = RenderConfig(frameLocalBudgetBytes = Math.subtractExact(budgetB, 1L)))
        record(warmRefusal)
        assertBudgetRefusal(warmRefusal)
        warmRefusal.discardRecordedOperations()
        recordRecovery(warmRefusal)
        assertPixelsAndScopes(warmRefusal, expected)
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
