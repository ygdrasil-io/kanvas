@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.Point2F32
import org.junit.jupiter.api.Test

/** Public W6a contract: all assertions cross Surface rather than plan or renderer internals. */
class W6aLayerSurfacePixelTest {
    @Test
    fun `emptyLayerWithoutAnySourceRemainsTransparent`() {
        val surface = Surface(4, 4)
        surface.canvas { saveLayer(); restore() }
        assertContentEquals(UByteArray(64), surface.render().pixels)
    }

    @Test
    fun `rootAndSiblingLayersRetainOneFrameWideGradientSource`() {
        // Encoded-sRGB interpolation at x = .5, 1.5, 2.5, 3.5: t = 1/8, 3/8, 5/8, 7/8.
        // Rounded 8-bit red/blue values, independently of either rendering route.
        val row = ubyteArrayOf(223u, 0u, 32u, 255u, 159u, 0u, 96u, 255u,
            96u, 0u, 159u, 255u, 32u, 0u, 223u, 255u)
        val expected = UByteArray(64) { row[it % 16] }
        val shader = Shader.Blend(BlendMode.SRC_OVER,
            Shader.LinearGradient(Point2F32(0f, 0f), Point2F32(4f, 0f), listOf(
                GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue))),
            Shader.SolidColor(ColorARGB.Transparent))
        val paint = Paint(shader = shader, antiAlias = false)
        val control = Surface(4, 4)
        control.canvas { drawRect(RectF32.ofLTRB(0f, 0f, 4f, 4f), paint) }
        assertContentEquals(expected, control.render().pixels)
        val layered = Surface(4, 4)
        layered.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 4f, 4f), paint)
            saveLayer()
            drawRect(RectF32.ofLTRB(0f, 0f, 2f, 4f), paint)
            restore()
            saveLayer()
            drawRect(RectF32.ofLTRB(2f, 0f, 4f, 4f), paint)
            restore()
        }
        assertContentEquals(expected, layered.render().pixels)
    }

    @Test
    fun `siblingTargetsExceedBudgetAtomicallyAndRecoverWithOneLayer`() {
        val expected = UByteArray(16)
        // 2x2 root + one layer (32), two aligned readback rows (512), geometry uniform (16).
        // A second live layer requires another 16 bytes; equal descriptors cannot imply aliasing.
        val surface = Surface(2, 2, config = RenderConfig(frameLocalBudgetBytes = 560L))
        surface.canvas { saveLayer(); restore(); saveLayer(); restore() }
        assertTerminalWithoutReadbackMutation(surface, "w6a.layer.resource_limit")
        surface.discardRecordedOperations()
        surface.canvas { saveLayer(); restore() }
        assertContentEquals(expected, surface.render().pixels)
    }

    @Test
    fun `sourceUniformBudgetRefusesPreciselyAndRecovers`() {
        val expected = UByteArray(16)
        val surface = Surface(2, 2, config = RenderConfig(frameLocalBudgetBytes = 560L))
        surface.canvas { saveLayer(); drawW5Rect(ColorARGB.Blue); restore() }
        assertTerminalWithoutReadbackMutation(surface, "w6a.layer.resource_limit")
        surface.discardRecordedOperations()
        surface.canvas { saveLayer(); restore() }
        assertContentEquals(expected, surface.render().pixels)
    }

    @Test
    fun `transparentSourceChildClearsOnlyItsLayer`() {
        val surface = Surface(4, 4)
        surface.canvas {
            drawW5Rect(ColorARGB.of(255, 239, 51, 73))
            saveLayer()
            drawW5Rect(ColorARGB.of(255, 17, 61, 211))
            drawRect(RectF32.ofLTRB(0f, 0f, 2f, 4f), Paint(
                shader = Shader.SolidColor(ColorARGB.Transparent),
                blendMode = BlendMode.SRC, antiAlias = false,
            ))
            restore()
        }
        val pixels = surface.render().pixels
        assertPixel(pixels, 4, 0, 1, 239, 51, 73, 255)
        assertPixel(pixels, 4, 3, 1, 17, 61, 211, 255)
    }

    @Test
    fun `direct no-layer W5 control remains available`() {
        val surface = Surface(4, 4)
        surface.canvas {
            drawW5Rect(ColorARGB.of(255, 17, 61, 211))
        }

        assertPixel(surface.render().pixels, 4, 1, 1, 17, 61, 211, 255)
    }

    @Test
    fun `boundedLayerIsolatesOverlappingChildren`() {
        val surface = Surface(4, 4)
        surface.canvas {
            saveLayer(RectF32.ofLTRB(0f, 0f, 4f, 4f))
            drawW5Rect(ColorARGB.of(255, 239, 51, 73))
            drawW5Rect(ColorARGB.of(255, 17, 61, 211))
            restore()
        }

        assertPixel(surface.render().pixels, 4, 1, 1, 17, 61, 211, 255)
    }

    @Test
    fun `unboundedLayerUsesTheParentClip`() {
        val surface = Surface(4, 4)
        surface.canvas {
            clipRect(RectF32.ofLTRB(1f, 1f, 3f, 3f), antiAlias = false)
            saveLayer()
            drawW5Rect(ColorARGB.of(255, 17, 61, 211))
            restore()
        }

        val pixels = surface.render().pixels
        assertPixel(pixels, 4, 0, 0, 0, 0, 0, 0)
        assertPixel(pixels, 4, 1, 1, 17, 61, 211, 255)
        assertPixel(pixels, 4, 3, 3, 0, 0, 0, 0)
    }

    @Test
    fun `emptyTransparentLayerIsAVisualNoOp`() {
        val surface = Surface(4, 4)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 4f, 4f), Paint(ColorARGB.of(255, 239, 51, 73), antiAlias = false))
            saveLayer()
            restore()
        }

        assertPixel(surface.render().pixels, 4, 2, 2, 239, 51, 73, 255)
    }

    @Test
    fun `twoEqualLayerDescriptorsRemainDistinctOccurrences`() {
        val surface = Surface(4, 4)
        surface.canvas {
            saveLayer()
            drawRect(RectF32.ofLTRB(0f, 0f, 2f, 4f), Paint(ColorARGB.of(255, 239, 51, 73), antiAlias = false))
            restore()
            saveLayer()
            drawRect(RectF32.ofLTRB(2f, 0f, 4f, 4f), Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false))
            restore()
        }

        val pixels = surface.render().pixels
        assertPixel(pixels, 4, 0, 1, 239, 51, 73, 255)
        assertPixel(pixels, 4, 3, 1, 17, 61, 211, 255)
    }

    @Test
    fun `ordinaryLayerDoesNotUseLegacyFallback`() {
        val surface = Surface(4, 4)
        surface.canvas {
            saveLayer(RectF32.ofLTRB(0f, 0f, 4f, 4f))
            drawW5Rect(ColorARGB.of(255, 17, 61, 211))
            restore()
        }

        assertPixel(surface.render().pixels, 4, 1, 1, 17, 61, 211, 255)
    }

    @Test
    fun `unsupportedBackdropRefusesTerminallyAndRecovers`() {
        val surface = Surface(2, 2)
        surface.canvas {
            saveLayer(SaveLayerRec(
                bounds = RectF32.ofLTRB(0f, 0f, 2f, 2f),
                backdrop = ImageFilter.Blur(1f, 1f),
            ))
            drawRect(RectF32.ofLTRB(0f, 0f, 2f, 2f), Paint(ColorARGB.White, antiAlias = false))
            restore()
        }

        assertTerminalWithoutReadbackMutation(surface, "w6a.layer.unsupported_backdrop")
        surface.discardRecordedOperations()
        surface.canvas { drawRect(RectF32.ofLTRB(0f, 0f, 2f, 2f), Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false)) }
        assertPixel(surface.render().pixels, 2, 1, 1, 17, 61, 211, 255)
    }

    @Test
    fun `unsupportedSpatialFilterRefusesTerminallyAndRecovers`() {
        val surface = Surface(2, 2)
        surface.canvas {
            saveLayer(RectF32.ofLTRB(0f, 0f, 2f, 2f))
            drawRect(
                RectF32.ofLTRB(0f, 0f, 2f, 2f),
                Paint(ColorARGB.White, imageFilter = ImageFilter.Blur(1f, 1f), antiAlias = false),
            )
            restore()
        }

        assertTerminalWithoutReadbackMutation(surface, "w6a.layer.unsupported_spatial_filter")
        surface.discardRecordedOperations()
        surface.canvas { drawRect(RectF32.ofLTRB(0f, 0f, 2f, 2f), Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false)) }
        assertPixel(surface.render().pixels, 2, 1, 1, 17, 61, 211, 255)
    }

    @Test
    fun `restoreMaskFilterRefusesTerminallyAndRecovers`() {
        val surface = Surface(2, 2)
        surface.canvas {
            saveLayer(paint = Paint(maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 1f), antiAlias = false))
            drawRect(RectF32.ofLTRB(0f, 0f, 2f, 2f), Paint(ColorARGB.White, antiAlias = false))
            restore()
        }

        assertTerminalWithoutReadbackMutation(surface, "w6a.layer.unsupported_spatial_filter")
        surface.discardRecordedOperations()
        surface.canvas { drawRect(RectF32.ofLTRB(0f, 0f, 2f, 2f), Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false)) }
        assertPixel(surface.render().pixels, 2, 1, 1, 17, 61, 211, 255)
    }

    @Test
    fun `unsupportedNonRgba8TargetRefusesTerminallyAndRecovers`() {
        val surface = Surface(2, 2, config = RenderConfig(gpuColorFormat = GPUColorFormat.BGRA8_UNORM))
        surface.canvas {
            saveLayer(RectF32.ofLTRB(0f, 0f, 2f, 2f))
            drawW5Rect(ColorARGB.of(255, 17, 61, 211))
            restore()
        }

        assertTerminalWithoutReadbackMutation(surface, "w6a.layer.unsupported_target_format")
        surface.discardRecordedOperations()
        surface.canvas { drawRect(RectF32.ofLTRB(0f, 0f, 2f, 2f), Paint(ColorARGB.Black, antiAlias = false)) }
        assertPixel(surface.render().pixels, 2, 1, 1, 0, 0, 0, 255)
    }

    private fun org.graphiks.kanvas.canvas.Canvas.drawW5Rect(color: ColorARGB) {
        drawRect(
            RectF32.ofLTRB(0f, 0f, 4f, 4f),
            Paint(shader = Shader.Opacity(Shader.SolidColor(color), 1f), antiAlias = false),
        )
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

    private fun assertPixel(
        pixels: UByteArray,
        widthI32: Int,
        xI32: Int,
        yI32: Int,
        redI32: Int,
        greenI32: Int,
        blueI32: Int,
        alphaI32: Int,
    ) {
        val offsetI32 = (yI32 * widthI32 + xI32) * 4
        assertContentEquals(
            ubyteArrayOf(redI32.toUByte(), greenI32.toUByte(), blueI32.toUByte(), alphaI32.toUByte()),
            pixels.copyOfRange(offsetI32, offsetI32 + 4),
        )
    }
}
