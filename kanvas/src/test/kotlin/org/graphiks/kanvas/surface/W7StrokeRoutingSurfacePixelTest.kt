@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.Shader
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test

/** Public Render+Readback witnesses for standalone W4d rect/path stroke routing. */
class W7StrokeRoutingSurfacePixelTest {
    companion object {
        @AfterAll
        @JvmStatic
        fun cleanupGpu() {
            // GLFW/AppKit teardown must run on the test's first thread, not a JVM shutdown hook.
            GPUBackendRuntimeFactory.dispose()
        }
    }

    @Test
    fun `rect and path stroke matrix preserves the hand-derived one-device-pixel ring`() {
        // The complete 8x8 oracle precedes Surface. A centreline (2.5,2.5)..(5.5,5.5)
        // covers [2,6) minus [3,5), denoted R over the opaque B background.
        val blue = ColorARGB.of(255, 17, 61, 211)
        val red = ColorARGB.of(255, 239, 51, 73)
        val expected = listOf(
            "BBBBBBBB", "BBBBBBBB", "BBRRRRBB", "BBRBBRBB",
            "BBRBBRBB", "BBRRRRBB", "BBBBBBBB", "BBBBBBBB",
        )
        listOf(false, true).forEach { antiAlias -> listOf(0f, 1f).forEach { width -> listOf(false, true).forEach { path ->
            val surface = Surface(8, 8)
            surface.canvas {
                drawRect(RectF32.ofLTRB(0f, 0f, 8f, 8f), Paint(blue, antiAlias = false))
                val paint = Paint(red, antiAlias = antiAlias, style = PaintStyle.STROKE, strokeWidth = width)
                if (path) drawPath(Path().apply { addRect(RectF32.ofLTRB(2.5f, 2.5f, 5.5f, 5.5f)) }, paint)
                else drawRect(RectF32.ofLTRB(2.5f, 2.5f, 5.5f, 5.5f), paint)
            }
            val actual = try { surface.render() } catch (failure: Exception) {
                throw AssertionError("AA=$antiAlias width=$width path=$path: ${failure.message}", failure)
            }
            assertPixelTable(expected, blue, red, actual)
        } } }
    }

    @Test
    fun `scaled rect hairline remains one device pixel wide`() {
        // The local centreline (1.25,1.25)..(2.75,2.75) under a 2x CTM reaches exactly the
        // same device ring as the unscaled witness; width zero is not rewritten to local 1.
        val blue = ColorARGB.of(255, 17, 61, 211)
        val red = ColorARGB.of(255, 239, 51, 73)
        val expected = listOf(
            Sample(2, 2, red), Sample(4, 2, red), Sample(2, 4, red), Sample(4, 4, blue),
            Sample(3, 3, blue), Sample(0, 0, blue),
        )

        val surface = Surface(8, 8)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 8f, 8f), Paint(blue, antiAlias = false))
            scale(2f, 2f)
            drawRect(RectF32.ofLTRB(1.25f, 1.25f, 2.75f, 2.75f),
                Paint(red, antiAlias = false, style = PaintStyle.STROKE, strokeWidth = 0f))
        }
        assertSamples(expected, surface.render(), 8)
    }

    @Test
    fun `fractional aa rect retains a partial edge rather than hardening it`() {
        // At (2,3), a fractional AA edge is neither the opaque blue destination nor opaque red.
        val blue = ColorARGB.of(255, 17, 61, 211)
        val red = ColorARGB.of(255, 239, 51, 73)
        val expectedInterior = Sample(3, 3, blue)
        val expectedOutside = Sample(0, 0, blue)

        val surface = Surface(8, 8)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 8f, 8f), Paint(blue, antiAlias = false))
            drawRect(RectF32.ofLTRB(2.25f, 2.25f, 5.75f, 5.75f),
                Paint(red, antiAlias = true, style = PaintStyle.STROKE, strokeWidth = 1f))
        }
        val actual = surface.render()
        assertSamples(listOf(expectedInterior, expectedOutside), actual, 8)
        val offset = (3 * 8 + 2) * 4
        assertTrue(actual.pixels[offset].toInt() in 18..238, actual.pixels.copyOfRange(offset, offset + 4).toString())
        assertTrue(actual.pixels[offset + 2].toInt() in 74..210, actual.pixels.copyOfRange(offset, offset + 4).toString())
        assertNativeEvidence(actual)
    }

    @Test
    fun `aa rect stroke shader keeps its refusal and leaves the next surface usable`() {
        val expected = listOf(Sample(0, 0, ColorARGB.Blue))
        val rejected = Surface(8, 8)
        rejected.canvas {
            drawRect(RectF32.ofLTRB(2f, 2f, 6f, 6f),
                Paint(shader = Shader.SolidColor(ColorARGB.Red), antiAlias = true,
                    style = PaintStyle.STROKE, strokeWidth = 1f))
        }
        val failure = assertFailsWith<IllegalStateException> { rejected.render() }
        assertEquals("unsupported.stroke.rect_anti_alias", failure.message.orEmpty().substringBefore(':'))
        val accepted = Surface(1, 1)
        accepted.canvas { drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.Blue, antiAlias = false)) }
        assertSamples(expected, accepted.render(), 1)
    }

    @Test
    fun `aa triangle resolves with and without a hard background`() {
        // Interior samples have full red coverage; samples outside the triangle retain
        // either the explicitly painted blue background or the transparent initial clear.
        listOf(false, true).forEach { background ->
            val outside = if (background) ColorARGB.Blue else ColorARGB.of(0, 0, 0, 0)
            val expected = listOf(Sample(2, 2, ColorARGB.Red), Sample(3, 2, ColorARGB.Red),
                Sample(0, 0, outside), Sample(6, 6, outside))
            val surface = Surface(8, 8)
            surface.canvas {
                if (background) drawRect(RectF32.ofLTRB(0f, 0f, 8f, 8f), Paint(ColorARGB.Blue, antiAlias = false))
                drawPath(Path().apply {
                    moveTo(1f, 1f); lineTo(7f, 1f); lineTo(1f, 7f); close()
                }, Paint(ColorARGB.Red, antiAlias = true))
            }
            assertSamples(expected, surface.render(), 8)
        }
    }

    @Test
    fun `later hard rect stroke preserves ordered alpha coverage`() {
        // The later opaque green partial ring replaces red at one shared edge, while an
        // unshared edge retains the hand-derived SrcOver red-half-on-blue byte (188,0,187).
        val blue = ColorARGB.Blue
        val redHalf = ColorARGB.of(128, 255, 0, 0)
        val green = ColorARGB.Green
        val expected = listOf(
            Sample(2, 3, green), Sample(5, 3, ColorARGB.of(255, 188, 0, 187)),
            Sample(3, 3, blue), Sample(0, 0, blue),
        )

        val surface = Surface(8, 8)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 8f, 8f), Paint(blue, antiAlias = false))
            drawRect(RectF32.ofLTRB(2.5f, 2.5f, 5.5f, 5.5f),
                Paint(redHalf, antiAlias = true, style = PaintStyle.STROKE, strokeWidth = 0f))
            drawRect(RectF32.ofLTRB(2.5f, 2.5f, 4.5f, 5.5f),
                Paint(green, antiAlias = false, style = PaintStyle.STROKE, strokeWidth = 0f))
        }
        assertSamples(expected, surface.render(), 8)
    }
}

private data class Sample(val x: Int, val y: Int, val color: ColorARGB)

private fun assertSamples(expected: List<Sample>, actual: RenderResult, width: Int) {
    expected.forEach { sample ->
        val offset = (sample.y * width + sample.x) * 4
        assertEquals(sample.color.red.toInt(), actual.pixels[offset].toInt(), "red at $sample")
        assertEquals(sample.color.green.toInt(), actual.pixels[offset + 1].toInt(), "green at $sample")
        assertEquals(sample.color.blue.toInt(), actual.pixels[offset + 2].toInt(), "blue at $sample")
        assertEquals(sample.color.alpha.toInt(), actual.pixels[offset + 3].toInt(), "alpha at $sample")
    }
    assertNativeEvidence(actual)
}

private fun assertPixelTable(expected: List<String>, blue: ColorARGB, red: ColorARGB, actual: RenderResult) {
    val samples = expected.flatMapIndexed { y, row -> row.mapIndexed { x, marker ->
        Sample(x, y, if (marker == 'R') red else blue)
    } }
    assertSamples(samples, actual, 8)
}

private fun assertNativeEvidence(actual: RenderResult) {
    assertTrue(actual.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
        actual.nativeEvidenceScopeKinds.toString())
}
