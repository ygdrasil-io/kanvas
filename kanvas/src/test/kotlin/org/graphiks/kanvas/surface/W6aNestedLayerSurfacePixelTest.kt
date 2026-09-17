@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.render.ir.GraphLimits
import org.graphiks.kanvas.render.ir.SceneCaptureLimits
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.Test

/**
 * Public nesting contract for W6a.  Every expected byte sequence is constructed before its
 * Surface, independently of the planner, graph, renderer, or another rendering route.
 */
class W6aNestedLayerSurfacePixelTest {
    @Test
    fun `nestedRestoreKeepsParentPaintOrder`() {
        val expected = rgba(239, 51, 73) + rgba(17, 61, 211) + rgba(43, 181, 93)
        val surface = Surface(3, 1)
        surface.canvas {
            saveLayer()
            drawOpaque(0f, 1f, ColorARGB.of(255, 239, 51, 73))
            saveLayer()
            drawOpaque(1f, 2f, ColorARGB.of(255, 17, 61, 211))
            restore()
            drawOpaque(2f, 3f, ColorARGB.of(255, 43, 181, 93))
            restore()
        }
        assertContentEquals(expected, surface.render().pixels)
    }

    @Test
    fun `siblingsWithEqualDescriptorsRemainIndependent`() {
        val expected = rgba(239, 51, 73) + rgba(17, 61, 211)
        val surface = Surface(2, 1)
        surface.canvas {
            saveLayer()
            saveLayer()
            drawOpaque(0f, 1f, ColorARGB.of(255, 239, 51, 73))
            restore()
            saveLayer()
            drawOpaque(1f, 2f, ColorARGB.of(255, 17, 61, 211))
            restore()
            restore()
        }
        assertContentEquals(expected, surface.render().pixels)
    }

    @Test
    fun `nestedDestinationReadBlendReadsItsImmediateParent`() {
        // Difference is evaluated from opaque red and the blue written inside the parent layer,
        // rather than from the older green scene target.
        val expected = opaqueDifference(239, 51, 73, 17, 61, 211)
        val surface = Surface(1, 1)
        surface.canvas {
            drawOpaque(0f, 1f, ColorARGB.of(255, 43, 181, 93))
            saveLayer()
            drawOpaque(0f, 1f, ColorARGB.of(255, 17, 61, 211))
            saveLayer(paint = Paint(blendMode = BlendMode.DIFFERENCE, antiAlias = false))
            drawOpaque(0f, 1f, ColorARGB.of(255, 239, 51, 73))
            restore()
            restore()
        }
        assertContentEquals(expected, surface.render().pixels)
    }

    @Test
    fun `parentDrawAfterChildIsNotMovedBeforeRestore`() {
        val expected = rgba(43, 181, 93) + rgba(17, 61, 211)
        val surface = Surface(2, 1)
        surface.canvas {
            saveLayer()
            drawOpaque(0f, 2f, ColorARGB.of(255, 239, 51, 73))
            saveLayer()
            drawOpaque(0f, 2f, ColorARGB.of(255, 17, 61, 211))
            restore()
            drawOpaque(0f, 1f, ColorARGB.of(255, 43, 181, 93))
            restore()
        }
        assertContentEquals(expected, surface.render().pixels)
    }

    @Test
    fun `nestedClipsOriginsAndTransformsStayInTheirImmediateParent`() {
        // The outer restore clip is [1,4), while the translated child and its parent-after draw
        // occupy device pixels 1 and 2. Their targets have different nonzero origins.
        val transparent = rgba(0, 0, 0, 0)
        val expected = transparent + rgba(17, 61, 211) + rgba(43, 181, 93) + transparent + transparent
        val surface = Surface(5, 1)
        surface.canvas {
            clipRect(RectF32.ofLTRB(1f, 0f, 4f, 1f), antiAlias = false)
            saveLayer(RectF32.ofLTRB(1f, 0f, 4f, 1f))
            translate(1f, 0f)
            saveLayer()
            drawOpaque(0f, 1f, ColorARGB.of(255, 17, 61, 211))
            restore()
            drawOpaque(1f, 2f, ColorARGB.of(255, 43, 181, 93))
            restore()
        }
        assertContentEquals(expected, surface.render().pixels)
    }

    @Test
    fun `restoreToCountClosesLayersInOrder`() {
        val expected = rgba(239, 51, 73) + rgba(17, 61, 211) + rgba(43, 181, 93)
        val surface = Surface(3, 1)
        surface.canvas {
            val rootCount = saveCount
            val parentCount = saveLayer()
            drawOpaque(0f, 1f, ColorARGB.of(255, 239, 51, 73))
            saveLayer()
            drawOpaque(1f, 2f, ColorARGB.of(255, 17, 61, 211))
            restoreToCount(parentCount)
            drawOpaque(2f, 3f, ColorARGB.of(255, 43, 181, 93))
            restoreToCount(rootCount)
        }
        assertContentEquals(expected, surface.render().pixels)
    }

    @Test
    fun `depthLimitRefusesBeforeReadbackAndSameSurfaceRecovers`() {
        val expectedRecovery = rgba(17, 61, 211) + rgba(17, 61, 211)
        val surface = Surface(2, 1, captureLimits = SceneCaptureLimits(graphLimits = GraphLimits(maxDepth = 1)))
        surface.canvas {
            repeat(2) { saveLayer() }
            repeat(2) { restore() }
        }

        val sentinel = UByteArray(8) { 0x5au }
        val before = sentinel.copyOf()
        val error = assertFailsWith<IllegalStateException> {
            surface.readPixels(RectF32.ofLTRB(0f, 0f, 2f, 1f), sentinel)
        }
        assertTrue(error.message?.startsWith("w6a.layer.depth_limit:") == true, error.message ?: "missing diagnostic")
        assertContentEquals(before, sentinel)

        surface.discardRecordedOperations()
        surface.canvas { drawOpaque(0f, 2f, ColorARGB.of(255, 17, 61, 211)) }
        assertContentEquals(expectedRecovery, surface.render().pixels)
    }

    private fun org.graphiks.kanvas.canvas.Canvas.drawOpaque(left: Float, right: Float, color: ColorARGB) {
        drawRect(RectF32.ofLTRB(left, 0f, right, 1f), Paint(color, antiAlias = false))
    }

    private fun rgba(red: Int, green: Int, blue: Int, alpha: Int = 255): UByteArray =
        ubyteArrayOf(red.toUByte(), green.toUByte(), blue.toUByte(), alpha.toUByte())

    /** W3C Difference for two opaque encoded-sRGB inputs, calculated in linear light. */
    private fun opaqueDifference(
        sourceRed: Int,
        sourceGreen: Int,
        sourceBlue: Int,
        destinationRed: Int,
        destinationGreen: Int,
        destinationBlue: Int,
    ): UByteArray = rgba(
        encodeDifference(sourceRed, destinationRed),
        encodeDifference(sourceGreen, destinationGreen),
        encodeDifference(sourceBlue, destinationBlue),
    )

    private fun encodeDifference(source: Int, destination: Int): Int {
        fun decode(value: Int): Double {
            val encoded = value / 255.0
            return if (encoded <= 0.04045) encoded / 12.92 else ((encoded + 0.055) / 1.055).pow(2.4)
        }
        val linear = abs(decode(source) - decode(destination))
        val encoded = if (linear <= 0.0031308) linear * 12.92 else 1.055 * linear.pow(1.0 / 2.4) - 0.055
        return (encoded * 255.0).roundToInt()
    }
}
