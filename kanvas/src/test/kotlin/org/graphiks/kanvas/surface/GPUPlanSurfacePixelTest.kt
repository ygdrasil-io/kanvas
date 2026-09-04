package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.CornerRadiiF32
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.RectI32
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalUnsignedTypes::class)
class GPUPlanSurfacePixelTest {
    @AfterEach
    fun disposeGpuRuntime() {
        GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun `W4b zero-radius white rrect uses exact rectangular overlap`() {
        val shape = RRectF32.of(RectF32(0f, 0f, 2f, 2f))
        val draws = listOf(
            W4bAnalyticRRectCpuOracle.Draw(ColorARGB.White, shape, RectI32(0, 0, 2, 2)),
        )
        val surface = Surface(2, 2)
        surface.canvas {
            drawRRect(shape, Paint.fill(ColorARGB.White).copy(antiAlias = true))
        }

        val result = surface.render()

        assertW4bEvidence(result)
        assertPixelsEqual(
            ubyteArrayOf(
                255u, 255u, 255u, 255u,
                255u, 255u, 255u, 255u,
                255u, 255u, 255u, 255u,
                255u, 255u, 255u, 255u,
            ),
            result.pixels,
        )
        assertPixelsEqual(W4bAnalyticRRectCpuOracle.render(2, 2, draws), result.pixels)
    }

    @Test
    fun `W4b red radius-one rrect matches all nine SDF pixels in RGBA and BGRA`() {
        val red = ColorARGB.Red
        val shape = RRectF32.of(
            RectF32(0f, 0f, 3f, 3f),
            CornerRadiiF32.of(1f, 1f),
            CornerRadiiF32.of(1f, 1f),
            CornerRadiiF32.of(1f, 1f),
            CornerRadiiF32.of(1f, 1f),
        )
        val draws = listOf(W4bAnalyticRRectCpuOracle.Draw(red, shape, RectI32(0, 0, 3, 3)))
        val rgba = Surface(3, 3, PixelFormat.RGBA8).also { surface ->
            surface.canvas { drawRRect(shape, Paint.fill(red).copy(antiAlias = true)) }
        }.render()
        val bgra = Surface(3, 3, PixelFormat.BGRA8).also { surface ->
            surface.canvas { drawRRect(shape, Paint.fill(red).copy(antiAlias = true)) }
        }.render()

        assertW4bEvidence(rgba)
        assertW4bEvidence(bgra)
        assertPixelsEqual(W4bAnalyticRRectCpuOracle.render(3, 3, draws), rgba.pixels)
        assertPixelsEqual(W4bAnalyticRRectCpuOracle.render(3, 3, draws, PixelFormat.BGRA8), bgra.pixels)
    }

    @Test
    fun `W4b mixed rect and translucent rrect preserve quantized SrcOver paint order`() {
        val blue = ColorARGB.Blue
        val translucentRed = ColorARGB.of(128, 255, 0, 0)
        val rect = RectF32(0f, 0f, 3f, 3f)
        val rrect = RRectF32.of(
            RectF32(1f, 0f, 4f, 3f),
            CornerRadiiF32.of(1f, 1f),
            CornerRadiiF32.of(1f, 1f),
            CornerRadiiF32.of(1f, 1f),
            CornerRadiiF32.of(1f, 1f),
        )
        val scissor = RectI32(0, 0, 4, 3)
        val forwardDraws = listOf(
            W4bAnalyticRRectCpuOracle.Draw(blue, RRectF32.of(rect), scissor),
            W4bAnalyticRRectCpuOracle.Draw(translucentRed, rrect, scissor),
        )
        val reverseDraws = listOf(
            W4bAnalyticRRectCpuOracle.Draw(translucentRed, rrect, scissor),
            W4bAnalyticRRectCpuOracle.Draw(blue, RRectF32.of(rect), scissor),
        )
        val forward = Surface(4, 3).also { surface ->
            surface.canvas {
                drawRect(rect, Paint.fill(blue).copy(antiAlias = true))
                drawRRect(rrect, Paint.fill(translucentRed).copy(antiAlias = true))
            }
        }.render()
        val reverse = Surface(4, 3).also { surface ->
            surface.canvas {
                drawRRect(rrect, Paint.fill(translucentRed).copy(antiAlias = true))
                drawRect(rect, Paint.fill(blue).copy(antiAlias = true))
            }
        }.render()

        assertW4bEvidence(forward)
        assertW4bEvidence(reverse)
        assertPixelsEqual(W4bAnalyticRRectCpuOracle.render(4, 3, forwardDraws), forward.pixels)
        assertPixelsEqual(W4bAnalyticRRectCpuOracle.render(4, 3, reverseDraws), reverse.pixels)
        assertFalse(forward.pixels.contentEquals(reverse.pixels))
    }

    @Test
    fun `W4b asymmetric reflected rrect clips exactly at the integral scissor`() {
        val color = ColorARGB.of(191, 37, 155, 229)
        val localShape = RRectF32.of(
            RectF32(0f, 0f, 4f, 4f),
            CornerRadiiF32.of(1f, 1f),
            CornerRadiiF32.of(2f, 1f),
            CornerRadiiF32.of(1f, 2f),
            CornerRadiiF32.of(0.5f, 1f),
        )
        val deviceShape = RRectF32.of(
            RectF32(0f, 0f, 4f, 4f),
            CornerRadiiF32.of(2f, 1f),
            CornerRadiiF32.of(1f, 1f),
            CornerRadiiF32.of(0.5f, 1f),
            CornerRadiiF32.of(1f, 2f),
        )
        val scissor = RectI32(1, 0, 4, 4)
        val surface = Surface(4, 4)
        surface.canvas {
            clipRect(RectF32(1f, 0f, 4f, 4f), antiAlias = false)
            translate(4f, 0f)
            scale(-1f, 1f)
            drawRRect(localShape, Paint.fill(color).copy(antiAlias = true))
        }

        val result = surface.render()

        assertW4bEvidence(result)
        assertPixelsEqual(
            W4bAnalyticRRectCpuOracle.render(
                4,
                4,
                listOf(W4bAnalyticRRectCpuOracle.Draw(color, deviceShape, scissor)),
            ),
            result.pixels,
        )
        assertTransparentOutside(result.pixels, 4, 4, scissor)
    }

    @Test
    fun `W4b renders 512 mixed draws through the public Surface boundary`() {
        val blue = ColorARGB.Blue
        val translucentRed = ColorARGB.of(128, 255, 0, 0)
        val rect = RectF32(0f, 0f, 1f, 1f)
        val rrect = RRectF32.of(
            rect,
            CornerRadiiF32.of(0.25f, 0.25f),
            CornerRadiiF32.of(0.25f, 0.25f),
            CornerRadiiF32.of(0.25f, 0.25f),
            CornerRadiiF32.of(0.25f, 0.25f),
        )
        val scissor = RectI32(0, 0, 1, 1)
        val draws = List(511) {
            W4bAnalyticRRectCpuOracle.Draw(blue, RRectF32.of(rect), scissor)
        } + W4bAnalyticRRectCpuOracle.Draw(translucentRed, rrect, scissor)
        val surface = Surface(1, 1, config = RenderConfig(frameLocalBudgetBytes = W4B_512_FRAME_BUDGET_BYTES))
        surface.canvas {
            repeat(511) { drawRect(rect, Paint.fill(blue).copy(antiAlias = true)) }
            drawRRect(rrect, Paint.fill(translucentRed).copy(antiAlias = true))
        }

        val result = surface.render()

        assertW4bEvidence(result)
        assertPixelsEqual(W4bAnalyticRRectCpuOracle.render(1, 1, draws), result.pixels)
    }

    @Test
    fun `W4a oracle calculates the hand checked fractional coverage and BGRA swizzle`() {
        val fractional = W4aAnalyticRectCpuOracle.render(
            width = 2,
            height = 2,
            draws = listOf(
                W4aAnalyticRectCpuOracle.Draw(
                    ColorARGB.White,
                    RectF32(0.25f, 0.5f, 1.75f, 1.5f),
                    RectI32(0, 0, 2, 2),
                ),
            ),
        )
        val bgra = W4aAnalyticRectCpuOracle.render(
            width = 1,
            height = 1,
            draws = listOf(
                W4aAnalyticRectCpuOracle.Draw(
                    ColorARGB.Red,
                    RectF32(0f, 0f, 1f, 1f),
                    RectI32(0, 0, 1, 1),
                ),
            ),
            format = PixelFormat.BGRA8,
        )

        // Each touched pixel has coverage (0.75 * 0.5) = 0.375 before blending.
        assertPixelsEqual(
            ubyteArrayOf(
                165u, 165u, 165u, 96u,
                165u, 165u, 165u, 96u,
                165u, 165u, 165u, 96u,
                165u, 165u, 165u, 96u,
            ),
            fractional,
        )
        assertPixelsEqual(ubyteArrayOf(0u, 0u, 255u, 255u), bgra)
    }

    @Test
    fun `opaque fractional AA rectangle matches the independent W4a oracle`() {
        val color = ColorARGB.of(255, 40, 120, 210)
        val draws = listOf(
            W4aAnalyticRectCpuOracle.Draw(
                color,
                RectF32(0.25f, 0.5f, 3.75f, 2.25f),
                RectI32(0, 0, 4, 3),
            ),
        )
        val surface = Surface(4, 3)
        surface.canvas {
            drawRect(draws.single().bounds, Paint.fill(color).copy(antiAlias = true))
        }

        val result = surface.render()

        assertW4aEvidence(result)
        assertPixelsEqual(W4aAnalyticRectCpuOracle.render(4, 3, draws), result.pixels)
    }

    @Test
    fun `overlapping translucent fractional AA rectangles quantize between draws`() {
        val first = ColorARGB.of(137, 14, 157, 83)
        val second = ColorARGB.of(191, 227, 62, 174)
        val draws = listOf(
            W4aAnalyticRectCpuOracle.Draw(first, RectF32(0.25f, 0.25f, 2.75f, 2.5f), RectI32(0, 0, 4, 3)),
            W4aAnalyticRectCpuOracle.Draw(second, RectF32(1.5f, 0.5f, 3.75f, 2.75f), RectI32(0, 0, 4, 3)),
        )
        val surface = Surface(4, 3)
        surface.canvas {
            drawRect(draws[0].bounds, Paint.fill(first).copy(antiAlias = true))
            drawRect(draws[1].bounds, Paint.fill(second).copy(antiAlias = true))
        }

        val result = surface.render()

        assertW4aEvidence(result)
        assertPixelsEqual(W4aAnalyticRectCpuOracle.render(4, 3, draws), result.pixels)
    }

    @Test
    fun `integral scissor clips an AA fringe and leaves outside pixels transparent`() {
        val color = ColorARGB.of(255, 40, 120, 210)
        val scissor = RectI32(1, 0, 4, 2)
        val draws = listOf(
            W4aAnalyticRectCpuOracle.Draw(color, RectF32(0.25f, 0.5f, 3.75f, 2.25f), scissor),
        )
        val surface = Surface(4, 3)
        surface.canvas {
            clipRect(RectF32(1f, 0f, 4f, 2f), antiAlias = false)
            drawRect(draws.single().bounds, Paint.fill(color).copy(antiAlias = true))
        }

        val result = surface.render()

        assertW4aEvidence(result)
        assertPixelsEqual(W4aAnalyticRectCpuOracle.render(4, 3, draws), result.pixels)
        assertTransparentOutside(result.pixels, 4, 3, scissor)
    }

    @Test
    fun `fractional W4a rectangle supports RGBA and BGRA attachment ordering`() {
        val color = ColorARGB.of(255, 40, 120, 210)
        val draws = listOf(
            W4aAnalyticRectCpuOracle.Draw(color, RectF32(0.25f, 0.5f, 3.75f, 2.25f), RectI32(0, 0, 4, 3)),
        )

        val rgba = renderFractionalScene(PixelFormat.RGBA8, draws)
        val bgra = renderFractionalScene(PixelFormat.BGRA8, draws)

        assertW4aEvidence(rgba)
        assertW4aEvidence(bgra)
        assertPixelsEqual(W4aAnalyticRectCpuOracle.render(4, 3, draws), rgba.pixels)
        assertPixelsEqual(W4aAnalyticRectCpuOracle.render(4, 3, draws, PixelFormat.BGRA8), bgra.pixels)
    }

    @Test
    fun `integral and fractional AA rectangles share the W4a frame in paint order`() {
        val integral = ColorARGB.Blue
        val fractional = ColorARGB.Red
        val draws = listOf(
            W4aAnalyticRectCpuOracle.Draw(
                integral, RectF32(0f, 0f, 2f, 2f), RectI32(0, 0, 4, 3),
            ),
            W4aAnalyticRectCpuOracle.Draw(
                fractional, RectF32(1.25f, 0.5f, 3.75f, 2.25f), RectI32(0, 0, 4, 3),
            ),
        )
        val surface = Surface(4, 3)
        surface.canvas {
            drawRect(draws[0].bounds, Paint.fill(integral).copy(antiAlias = true))
            drawRect(draws[1].bounds, Paint.fill(fractional).copy(antiAlias = true))
        }

        val result = surface.render()

        assertW4aEvidence(result)
        assertPixelsEqual(W4aAnalyticRectCpuOracle.render(4, 3, draws), result.pixels)
    }

    @Test
    fun `512 fractional AA rectangles plus annotation render through W4a`() {
        val color = ColorARGB.Red
        val draw = W4aAnalyticRectCpuOracle.Draw(
            color,
            RectF32(0f, 0f, 0.5f, 1f),
            RectI32(0, 0, 1, 1),
        )
        val draws = List(512) { draw }
        val surface = Surface(1, 1, config = RenderConfig(frameLocalBudgetBytes = W4A_512_FRAME_BUDGET_BYTES))
        surface.canvas {
            repeat(512) { drawRect(draw.bounds, Paint.fill(color).copy(antiAlias = true)) }
            drawAnnotation(RectF32.Empty, "evidence", "w4a-512")
        }

        val result = surface.render()

        assertW4aEvidence(result)
        assertPixelsEqual(W4aAnalyticRectCpuOracle.render(1, 1, draws), result.pixels)
    }

    @Test
    fun `513 fractional AA rectangles retain known legacy pixel under the W4a terminal budget`() {
        val color = ColorARGB.of(255, 40, 120, 210)
        val bounds = RectF32(0.25f, 0.25f, 0.75f, 0.75f)
        val surface = Surface(1, 1, config = RenderConfig(frameLocalBudgetBytes = W4A_512_FRAME_BUDGET_BYTES))
        surface.canvas {
            repeat(513) { drawRect(bounds, Paint.fill(color).copy(antiAlias = true)) }
        }

        val result = surface.render()

        assertPixelsEqual(ubyteArrayOf(38u, 119u, 209u, 253u), result.pixels)
    }

    @Test
    fun `opaque overlapping rectangles match the independent CPU oracle`() {
        val red = ColorARGB.of(255, 231, 37, 19)
        val blue = ColorARGB.of(255, 17, 83, 219)
        val surface = Surface(4, 3)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 3f, 2f), Paint.fill(red).copy(antiAlias = false))
            drawRect(RectF32.ofLTRB(2f, 1f, 4f, 3f), Paint.fill(blue).copy(antiAlias = false))
        }

        val result = surface.render()
        assertW3Evidence(result)
        assertPixelsEqual(
            W3SolidRectCpuOracle.render(
                4,
                3,
                listOf(
                    W3SolidRectCpuOracle.Draw(red, 0, 0, 3, 2),
                    W3SolidRectCpuOracle.Draw(blue, 2, 1, 4, 3),
                ),
            ),
            result.pixels,
        )
    }

    @Test
    fun `translucent rectangles use linear premultiplied SrcOver exactly`() {
        val first = ColorARGB.of(101, 23, 181, 71)
        val second = ColorARGB.of(173, 211, 47, 139)
        val surface = Surface(3, 2)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 3f, 2f), Paint.fill(first).copy(antiAlias = false))
            drawRect(RectF32.ofLTRB(1f, 0f, 3f, 2f), Paint.fill(second).copy(antiAlias = false))
        }

        val result = surface.render()
        assertW3Evidence(result)
        assertPixelsEqual(
            W3SolidRectCpuOracle.render(
                3,
                2,
                listOf(
                    W3SolidRectCpuOracle.Draw(first, 0, 0, 3, 2),
                    W3SolidRectCpuOracle.Draw(second, 1, 0, 3, 2),
                ),
            ),
            result.pixels,
        )
    }

    @Test
    fun `DeviceRect clip limits an otherwise larger solid rectangle`() {
        val green = ColorARGB.of(255, 29, 197, 113)
        val surface = Surface(5, 4)
        surface.canvas {
            clipRect(RectF32.ofLTRB(1f, 1f, 4f, 3f), antiAlias = false)
            drawRect(RectF32.ofLTRB(0f, 0f, 5f, 4f), Paint.fill(green).copy(antiAlias = false))
        }

        val result = surface.render()
        assertW3Evidence(result)
        assertPixelsEqual(
            W3SolidRectCpuOracle.render(
                5,
                4,
                listOf(W3SolidRectCpuOracle.Draw(green, 0, 0, 5, 4, 1, 1, 4, 3)),
            ),
            result.pixels,
        )
    }

    @Test
    fun `DrawColor SrcOver matches the independent CPU oracle`() {
        val backdrop = ColorARGB.of(151, 44, 96, 203)
        val foreground = ColorARGB.of(119, 216, 71, 31)
        val surface = Surface(2, 2)
        surface.canvas {
            drawColor(backdrop)
            drawColor(foreground, BlendMode.SRC_OVER)
        }

        val result = surface.render()
        assertW3Evidence(result)
        assertPixelsEqual(
            W3SolidRectCpuOracle.render(
                2,
                2,
                listOf(
                    W3SolidRectCpuOracle.Draw(backdrop, 0, 0, 2, 2),
                    W3SolidRectCpuOracle.Draw(foreground, 0, 0, 2, 2),
                ),
            ),
            result.pixels,
        )
    }

    @Test
    fun `RGBA and BGRA Surface results match the same scene after channel ordering`() {
        val first = ColorARGB.of(137, 14, 157, 83)
        val second = ColorARGB.of(191, 227, 62, 174)
        val draws = listOf(
            W3SolidRectCpuOracle.Draw(first, 0, 0, 3, 2),
            W3SolidRectCpuOracle.Draw(second, 1, 0, 4, 2),
        )

        val rgba = renderScene(PixelFormat.RGBA8, first, second)
        val bgra = renderScene(PixelFormat.BGRA8, first, second)

        assertW3Evidence(rgba)
        assertW3Evidence(bgra)
        assertPixelsEqual(W3SolidRectCpuOracle.render(4, 2, draws, PixelFormat.RGBA8), rgba.pixels)
        assertPixelsEqual(W3SolidRectCpuOracle.render(4, 2, draws, PixelFormat.BGRA8), bgra.pixels)
    }

    @Test
    fun `attachment quantization is visible to the next translucent SrcOver draw`() {
        val first = ColorARGB.of(182, 145, 133, 48)
        val second = ColorARGB.of(23, 221, 204, 240)
        val surface = Surface(1, 1)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint.fill(first).copy(antiAlias = false))
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint.fill(second).copy(antiAlias = false))
        }

        val result = surface.render()
        assertW3Evidence(result)
        assertPixelsEqual(ubyteArrayOf(137u, 126u, 88u, 189u), result.pixels)
        assertPixelsEqual(
            W3SolidRectCpuOracle.render(
                1,
                1,
                listOf(
                    W3SolidRectCpuOracle.Draw(first, 0, 0, 1, 1),
                    W3SolidRectCpuOracle.Draw(second, 0, 0, 1, 1),
                ),
            ),
            result.pixels,
        )
    }

    @Test
    fun `unsupported SRC scene retains its known legacy pixels`() {
        val surface = Surface(1, 1)
        surface.canvas {
            drawColor(ColorARGB.Blue)
            drawColor(ColorARGB.of(128, 255, 0, 0), BlendMode.SRC)
        }

        assertPixelsEqual(ubyteArrayOf(188u, 0u, 0u, 128u), surface.render().pixels)
    }

    private fun renderScene(format: PixelFormat, first: ColorARGB, second: ColorARGB): RenderResult {
        val surface = Surface(4, 2, format)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 3f, 2f), Paint.fill(first).copy(antiAlias = false))
            drawRect(RectF32.ofLTRB(1f, 0f, 4f, 2f), Paint.fill(second).copy(antiAlias = false))
        }
        return surface.render()
    }

    private fun renderFractionalScene(
        format: PixelFormat,
        draws: List<W4aAnalyticRectCpuOracle.Draw>,
    ): RenderResult {
        val surface = Surface(4, 3, format)
        surface.canvas {
            draws.forEach { drawRect(it.bounds, Paint.fill(it.color).copy(antiAlias = true)) }
        }
        return surface.render()
    }

    private fun assertW3Evidence(result: RenderResult) {
        val expectedScopeKinds = setOf("Render", "Readback")
        assertTrue(
            result.nativeEvidenceScopeKinds.containsAll(expectedScopeKinds),
            result.nativeEvidenceScopeKinds.toString(),
        )
        assertEquals(expectedScopeKinds, result.nativeEvidenceScopeKinds.toSet())
    }

    private fun assertW4aEvidence(result: RenderResult) {
        assertEquals(setOf("Render", "Readback"), result.nativeEvidenceScopeKinds.toSet())
    }

    private fun assertW4bEvidence(result: RenderResult) {
        assertEquals(setOf("Render", "Readback"), result.nativeEvidenceScopeKinds.toSet())
    }

    private fun assertTransparentOutside(pixels: UByteArray, width: Int, height: Int, scissor: RectI32) {
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (x !in scissor.left until scissor.right || y !in scissor.top until scissor.bottom) {
                    val offset = (y * width + x) * 4
                    assertPixelsEqual(ubyteArrayOf(0u, 0u, 0u, 0u), pixels.copyOfRange(offset, offset + 4))
                }
            }
        }
    }

    private fun assertPixelsEqual(expected: UByteArray, actual: UByteArray) {
        assertContentEquals(expected, actual, "expected=${expected.toList()} actual=${actual.toList()}")
    }

    private companion object {
        const val W4A_512_FRAME_BUDGET_BYTES = 164_100L
        const val W4B_512_FRAME_BUDGET_BYTES = 164_100L
    }
}
