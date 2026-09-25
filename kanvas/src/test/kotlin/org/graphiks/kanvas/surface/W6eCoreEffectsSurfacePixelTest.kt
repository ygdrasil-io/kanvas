@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.paint.DropShadowMode
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.Test

/** Public W6e covering shard: exact spatial filters remain distinct from sampled families. */
class W6eCoreEffectsSurfacePixelTest {
    @Test
    fun coreShardKeepsExactAndBlurOracleAssertionsSeparate() {
        // Every expected is owned before any Surface exists. Crop/Offset/Tile are byte-exact;
        // blur and shadow use the independent CPU family oracle below rather than that policy.
        val cropExpected = rgbaRow(listOf(transparent, blue, transparent, transparent))
        val offsetExpected = rgbaRow(listOf(transparent, blue, transparent, transparent))
        val tileExpected = rgbaRow(listOf(blue, transparent, blue, transparent))
        val blurExpected = W6eBlurShadowCpuOracle.blurOpaqueWhiteImpulse(7, 7, 3, 3)
        val shadowExpected = W6eBlurShadowCpuOracle.dropShadow(
            width = 7, height = 7, sourceAlpha = impulseAlpha(7, 7, 2, 3),
            sourceColor = ColorARGB.White, dx = 2f, dy = 0f, sigma = 1f,
            shadowColor = ColorARGB.Blue, mode = DropShadowMode.COMPOSITE,
        )

        assertRenderedExactly(cropExpected) {
            renderLayer(4, 1, ImageFilter.Crop(RectF32.ofLTRB(1f, 0f, 2f, 1f), TileMode.DECAL)) {
                drawRect(RectF32.ofLTRB(1f, 0f, 2f, 1f), Paint(ColorARGB.Blue, antiAlias = false))
            }
        }
        assertRenderedExactly(offsetExpected) {
            renderLayer(4, 1, ImageFilter.Offset(1f, 0f)) {
                drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.Blue, antiAlias = false))
            }
        }
        assertRenderedExactly(tileExpected) {
            renderLayer(4, 1, ImageFilter.Tile(
                RectF32.ofLTRB(0f, 0f, 2f, 1f), RectF32.ofLTRB(0f, 0f, 4f, 1f),
            )) {
                drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.Blue, antiAlias = false))
            }
        }
        assertFamilyNear(blurExpected, maxDelta = 12) {
            renderLayer(7, 7, ImageFilter.Blur(1f, 1f, TileMode.DECAL)) {
                drawRect(RectF32.ofLTRB(3f, 3f, 4f, 4f), Paint(ColorARGB.White, antiAlias = false))
            }
        }
        assertFamilyNear(shadowExpected, maxDelta = 12) {
            renderLayer(7, 7, ImageFilter.DropShadow(
                2f, 0f, 1f, 1f, ColorARGB.Blue, mode = DropShadowMode.COMPOSITE,
            )) {
                drawRect(RectF32.ofLTRB(2f, 3f, 3f, 4f), Paint(ColorARGB.White, antiAlias = false))
            }
        }
    }

    private fun assertRenderedExactly(expected: UByteArray, render: () -> RenderResult) {
        val actual = render()
        assertContentEquals(expected, actual.pixels)
        assertNativeRenderAndReadback(actual)
    }

    private fun assertFamilyNear(expected: UByteArray, maxDelta: Int, render: () -> RenderResult) {
        val actual = render()
        W6eBlurShadowCpuOracle.assertNear(expected, actual.pixels, maxDelta)
        assertNativeRenderAndReadback(actual)
    }

    private fun renderLayer(
        width: Int,
        height: Int,
        filter: ImageFilter,
        draw: org.graphiks.kanvas.canvas.Canvas.() -> Unit,
    ): RenderResult = Surface(width, height).also { surface ->
        surface.canvas {
            saveLayer(SaveLayerRec(paint = Paint(imageFilter = filter, antiAlias = false)))
            draw()
            restore()
        }
    }.render()

    private fun assertNativeRenderAndReadback(result: RenderResult) {
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            result.nativeEvidenceScopeKinds.toString())
    }

    private fun impulseAlpha(width: Int, height: Int, x: Int, y: Int): UByteArray =
        UByteArray(width * height).also { it[x + y * width] = 255u }

    private fun rgbaRow(pixels: List<UByteArray>): UByteArray = pixels.flatMap { it.asList() }.toUByteArray()

    private companion object {
        val transparent = ubyteArrayOf(0u, 0u, 0u, 0u)
        val blue = ubyteArrayOf(0u, 0u, 255u, 255u)
    }
}
