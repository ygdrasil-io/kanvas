@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Shader
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.matrix.Matrix3x3F32
import org.junit.jupiter.api.Test

/** Public pixel proof for W6a layer bounds, origins, mapping and clip semantics. */
class W6aLayerBoundsSurfacePixelTest {
    @Test
    fun `translatedFractionalBoundsRoundOutward`() {
        // A 3x3 root/readback/uniform frame costs 836 bytes.  The translated F64 hint
        // [1.25, 1.25, 2.75, 2.75] rounds outward to a 2x2 target (+16), not a 1x1
        // truncated/ignored target (+4).  B-1 consequently proves that the target extent
        // is sealed from the outward projection, independently of the pixels below.
        val expected = pixels(3, 3, 1, 1, ColorARGB.of(255, 17, 61, 211))
        val surface = Surface(3, 3, config = RenderConfig(frameLocalBudgetBytes = 852L))

        surface.canvas {
            translate(1f, 1f)
            saveLayer(RectF32.ofLTRB(.25f, .25f, 1.75f, 1.75f))
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false))
            restore()
        }

        assertContentEquals(expected, surface.render().pixels)

        val oneByteTooSmall = Surface(3, 3, config = RenderConfig(frameLocalBudgetBytes = 851L))
        oneByteTooSmall.canvas {
            translate(1f, 1f)
            saveLayer(RectF32.ofLTRB(.25f, .25f, 1.75f, 1.75f))
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false))
            restore()
        }
        assertTerminalWithoutReadbackMutation(oneByteTooSmall, "w6a.layer.resource_limit")
    }

    @Test
    fun `explicitClipLimitsRestore`() {
        // The explicit parent clip limits the restore domain to one texel, not the child API.
        val expected = pixels(2, 2, 1, 1, ColorARGB.of(255, 239, 51, 73))
        val surface = Surface(2, 2, config = RenderConfig(frameLocalBudgetBytes = 564L))

        surface.canvas {
            clipRect(RectF32.ofLTRB(1f, 1f, 2f, 2f), antiAlias = false)
            saveLayer()
            drawRect(RectF32.ofLTRB(0f, 0f, 2f, 2f), Paint(ColorARGB.of(255, 239, 51, 73), antiAlias = false))
            restore()
        }

        assertContentEquals(expected, surface.render().pixels)
    }

    @Test
    fun `hintDoesNotClipChildren`() {
        // The child is deliberately outside the hint. A hint is not a hard child clip.
        val expected = pixels(2, 2, 1, 1, ColorARGB.of(255, 17, 61, 211))
        val surface = Surface(2, 2, config = RenderConfig(frameLocalBudgetBytes = 576L))

        surface.canvas {
            saveLayer(RectF32.ofLTRB(0f, 0f, 1f, 1f))
            drawRect(RectF32.ofLTRB(1f, 1f, 2f, 2f), Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false))
            restore()
        }

        assertContentEquals(expected, surface.render().pixels)
    }

    @Test
    fun `childClipAndCompositeClipRemainSeparate`() {
        // The parent clip owns restore; the child clip owns source scissoring. Their exact
        // intersection is the centre texel, but neither clip is copied into the other phase.
        val expected = pixels(3, 3, 1, 1, ColorARGB.of(255, 239, 51, 73))
        val surface = Surface(3, 3, config = RenderConfig(frameLocalBudgetBytes = 852L))

        surface.canvas {
            clipRect(RectF32.ofLTRB(1f, 0f, 3f, 2f), antiAlias = false)
            saveLayer()
            clipRect(RectF32.ofLTRB(0f, 1f, 2f, 3f), antiAlias = false)
            drawRect(RectF32.ofLTRB(0f, 0f, 3f, 3f), Paint(ColorARGB.of(255, 239, 51, 73), antiAlias = false))
            restore()
        }

        assertContentEquals(expected, surface.render().pixels)
    }

    @Test
    fun `emptyCompositeClipElidesBeforeHorizonAndSameSurfaceRecovers`() {
        val expectedNoOp = pixels(2, 2, 0, 0, ColorARGB.of(255, 17, 61, 211))
        val expectedRecovery = pixels(2, 2, 1, 1, ColorARGB.of(255, 17, 61, 211))
        // Root, readback, geometry and the root source uniform cost 560 bytes; a layer target
        // would exceed it. The root draw proves the empty layer is a no-op, not a second route.
        val surface = Surface(2, 2, config = RenderConfig(frameLocalBudgetBytes = 560L))

        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false))
            save()
            clipRect(RectF32.ofLTRB(0f, 0f, 0f, 0f), antiAlias = false)
            setMatrix(Matrix3x3F32(persp0 = 1f, persp2 = -1f))
            saveLayer()
            drawRect(RectF32.ofLTRB(0f, 0f, 2f, 2f), Paint(ColorARGB.of(255, 239, 51, 73), antiAlias = false))
            restore()
            restore()
        }
        assertContentEquals(expectedNoOp, surface.render().pixels)

        surface.discardRecordedOperations()
        surface.canvas {
            resetMatrix()
            drawRect(RectF32.ofLTRB(1f, 1f, 2f, 2f), Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false))
        }
        assertContentEquals(expectedRecovery, surface.render().pixels)
    }

    @Test
    fun `emptyCompositeClipDoesNotMaskUnsupportedBackdropAndSameSurfaceRecovers`() {
        val expected = pixels(2, 2, 1, 1, ColorARGB.of(255, 17, 61, 211))
        val surface = Surface(2, 2)

        surface.canvas {
            save()
            clipRect(RectF32.ofLTRB(0f, 0f, 0f, 0f), antiAlias = false)
            saveLayer(SaveLayerRec(backdrop = ImageFilter.Blur(1f, 1f)))
            restore()
            restore()
        }
        assertTerminalWithoutReadbackMutation(surface, "w6a.layer.unsupported_backdrop")

        surface.discardRecordedOperations()
        surface.canvas {
            drawRect(RectF32.ofLTRB(1f, 1f, 2f, 2f), Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false))
        }
        assertContentEquals(expected, surface.render().pixels)
    }

    @Test
    fun `emptyHintDoesNotConstrainChildren`() {
        val expected = pixels(2, 2, 1, 1, ColorARGB.of(255, 17, 61, 211))
        val surface = Surface(2, 2, config = RenderConfig(frameLocalBudgetBytes = 564L))

        surface.canvas {
            saveLayer(RectF32.ofLTRB(0f, 0f, 0f, 0f))
            drawRect(RectF32.ofLTRB(1f, 1f, 2f, 2f), Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false))
            restore()
        }

        assertContentEquals(expected, surface.render().pixels)
    }

    @Test
    fun `nonzeroLayerOriginPreservesSampling`() {
        // 2x2 root/readback/uniforms plus 1x1 layer (564), a 112-byte gradient uniform and 64-byte stop slab.
        // At device x=1.5 on a [0,2] gradient, encoded-sRGB interpolation is (64, 0, 191, 255).
        val expected = pixels(2, 2, 1, 0, ColorARGB.of(255, 64, 0, 191))
        val gradient = Shader.LinearGradient(
            Point2F32(0f, 0f),
            Point2F32(2f, 0f),
            listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
        )
        val surface = Surface(2, 2, config = RenderConfig(frameLocalBudgetBytes = 740L))

        surface.canvas {
            saveLayer(RectF32.ofLTRB(1f, 0f, 2f, 1f))
            drawRect(RectF32.ofLTRB(1f, 0f, 2f, 1f), Paint(shader = gradient, antiAlias = false))
            restore()
        }

        assertContentEquals(expected, surface.render().pixels)
    }

    @Test
    fun `reflectedLayerPreservesPixels`() {
        // Reflection remains invertible. Its mapped one-pixel target starts at device x=1.
        val expected = pixels(2, 2, 1, 0, ColorARGB.of(255, 17, 61, 211))
        val surface = Surface(2, 2, config = RenderConfig(frameLocalBudgetBytes = 564L))

        surface.canvas {
            translate(2f, 0f)
            scale(-1f, 1f)
            saveLayer(RectF32.ofLTRB(0f, 0f, 1f, 1f))
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false))
            restore()
        }

        assertContentEquals(expected, surface.render().pixels)
    }

    @Test
    fun `horizonCrossingTransformRefusesAndSameSurfaceRecovers`() {
        val expected = pixels(2, 2, 1, 1, ColorARGB.of(255, 17, 61, 211))
        val surface = Surface(2, 2)

        surface.canvas {
            setMatrix(Matrix3x3F32(persp0 = 1f, persp2 = -1f))
            saveLayer(RectF32.ofLTRB(0f, 0f, 2f, 1f))
            restore()
        }
        assertTerminalWithoutReadbackMutation(surface, "w6a.layer.mapping_horizon")

        surface.discardRecordedOperations()
        surface.canvas {
            resetMatrix()
            drawRect(RectF32.ofLTRB(1f, 1f, 2f, 2f), Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false))
        }
        assertContentEquals(expected, surface.render().pixels)
    }

    private fun pixels(widthI32: Int, heightI32: Int, xI32: Int, yI32: Int, color: ColorARGB): UByteArray {
        val result = UByteArray(widthI32 * heightI32 * 4)
        val offsetI32 = (yI32 * widthI32 + xI32) * 4
        result[offsetI32] = color.red.toUByte()
        result[offsetI32 + 1] = color.green.toUByte()
        result[offsetI32 + 2] = color.blue.toUByte()
        result[offsetI32 + 3] = color.alpha.toUByte()
        return result
    }

    private fun assertTerminalWithoutReadbackMutation(surface: Surface, code: String) {
        val sentinel = UByteArray(16) { 0x5au }
        val before = sentinel.copyOf()
        val error = assertFailsWith<IllegalStateException> {
            surface.readPixels(RectF32.ofLTRB(0f, 0f, 2f, 2f), sentinel)
        }
        assertTrue(error.message?.startsWith("$code:") == true, error.message ?: "missing diagnostic")
        assertContentEquals(before, sentinel)
    }
}
