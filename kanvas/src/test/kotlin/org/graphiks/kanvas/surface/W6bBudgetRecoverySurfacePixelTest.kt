@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.paint.DropShadowMode
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.Test

/** Public B/B-1 and terminal-recovery proof for a sealed W6b Picture aggregate. */
class W6bBudgetRecoverySurfacePixelTest {
    @Test
    fun pictureAggregateBudgetAcceptsBAndRefusesBMinusOne() {
        val admitted = shadowSurface(frameLocalBudgetBytes = aggregateBudgetB)
        assertContentEquals(compositeFixturePixels(), admitted.render().pixels)

        val refused = shadowSurface(frameLocalBudgetBytes = aggregateBudgetB - 1L)
        assertBudgetRefusalKeepsSentinel(refused)
    }

    @Test
    fun nestedBudgetBoundaryAndLateSiblingRefusalAreAtomic() {
        val nested = nestedShadowSurface(frameLocalBudgetBytes = nestedAggregateBudgetB)
        assertContentEquals(compositeFixturePixels(), nested.render().pixels)

        val lateSibling = Surface(2, 1, config = RenderConfig(frameLocalBudgetBytes = aggregateBudgetB))
        lateSibling.canvas {
            drawPicture(shadowPicture(), Paint(imageFilter = fixtureShadow(), antiAlias = false))
            drawPicture(shadowPicture(), Paint(imageFilter = fixtureShadow(), antiAlias = false))
        }
        assertBudgetRefusalKeepsSentinel(lateSibling)

        lateSibling.discardRecordedOperations()
        lateSibling.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 2f, 1f), Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false))
        }
        assertContentEquals(ubyteArrayOf(17u, 61u, 211u, 255u, 17u, 61u, 211u, 255u), lateSibling.render().pixels)
    }

    private fun shadowSurface(frameLocalBudgetBytes: Long): Surface = Surface(2, 1,
        config = RenderConfig(frameLocalBudgetBytes = frameLocalBudgetBytes)).also { surface ->
        surface.canvas { drawPicture(shadowPicture(), Paint(imageFilter = fixtureShadow(), antiAlias = false)) }
    }

    private fun nestedShadowSurface(frameLocalBudgetBytes: Long): Surface = Surface(2, 1,
        config = RenderConfig(frameLocalBudgetBytes = frameLocalBudgetBytes)).also { surface ->
        surface.canvas {
            saveLayer(SaveLayerRec())
            drawPicture(shadowPicture(), Paint(imageFilter = fixtureShadow(), antiAlias = false))
            restore()
        }
    }

    private fun shadowPicture(): Picture = PictureRecorder().also { recorder ->
        recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 1f, 1f)).drawRect(
            RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.White, antiAlias = false),
        )
    }.finishRecordingAsPicture()

    private fun fixtureShadow(): ImageFilter = ImageFilter.DropShadow(
        1f, 0f, 0f, 0f, ColorARGB.of(255, 17, 61, 211), mode = DropShadowMode.COMPOSITE,
    )

    private fun compositeFixturePixels(): UByteArray = ubyteArrayOf(
        255u, 255u, 255u, 255u,
        17u, 61u, 211u, 255u,
    )

    private fun assertBudgetRefusalKeepsSentinel(surface: Surface) {
        val sentinel = ubyteArrayOf(0x5au, 0x5au, 0x5au, 0x5au, 0x5au, 0x5au, 0x5au, 0x5au)
        val before = sentinel.copyOf()
        val failure = assertFailsWith<IllegalStateException> {
            surface.readPixels(RectF32.ofLTRB(0f, 0f, 2f, 1f), sentinel)
        }
        assertTrue(failure.message?.startsWith("w6b.filter.frame_budget_exceeded:") == true, failure.message ?: "missing diagnostic")
        assertContentEquals(before, sentinel)
    }

    private companion object {
        // 2x1 root (8), one 1x1 sealed Picture aggregate and one 1x1 FilterSource
        // (2 × 4), X/Y/color filter targets (3 × 4), 2x1 terminal composite (8), five
        // sealed 16-byte W5/W6 uniform slots, and the 256-byte RGBA8 readback row.  This is
        // public fixture arithmetic; it neither reads planner peaks nor queries native state.
        const val aggregateBudgetB: Long = 8L + 2L * 4L + 3L * 4L + 8L + 5L * 16L + 256L
        // The same aggregate runs in a 2x1 explicit W6a child target (+8).
        const val nestedAggregateBudgetB: Long = aggregateBudgetB + 8L
    }
}
