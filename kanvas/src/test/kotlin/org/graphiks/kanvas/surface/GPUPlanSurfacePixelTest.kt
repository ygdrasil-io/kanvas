package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalUnsignedTypes::class)
class GPUPlanSurfacePixelTest {
    @AfterEach
    fun disposeGpuRuntime() {
        GPUBackendRuntimeFactory.dispose()
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
    fun `non W3 SRC frame preserves the known legacy result`() {
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

    private fun assertW3Evidence(result: RenderResult) {
        val expectedScopeKinds = setOf("Render", "Readback")
        assertTrue(
            result.nativeEvidenceScopeKinds.containsAll(expectedScopeKinds),
            result.nativeEvidenceScopeKinds.toString(),
        )
        assertEquals(expectedScopeKinds, result.nativeEvidenceScopeKinds.toSet())
    }

    private fun assertPixelsEqual(expected: UByteArray, actual: UByteArray) {
        assertContentEquals(expected, actual, "expected=${expected.toList()} actual=${actual.toList()}")
    }
}
