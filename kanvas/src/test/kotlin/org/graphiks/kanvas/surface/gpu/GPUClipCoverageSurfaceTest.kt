package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.alphaByte
import org.graphiks.kanvas.types.blueByte
import org.graphiks.kanvas.types.greenByte
import org.graphiks.kanvas.types.redByte
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalUnsignedTypes::class)
class GPUClipCoverageSurfaceTest {
    @AfterEach
    fun disposeRuntime() {
        GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun `complex difference clip masks rect rrect path and image without changing exterior`() {
        requireWebGpu()
        val surface = Surface(32, 32)
        surface.canvas {
            save()
            clipRect(Rect(2f, 2f, 30f, 30f), ClipOp.INTERSECT, antiAlias = true)
            clipRect(Rect(12f, 12f, 20f, 20f), ClipOp.DIFFERENCE, antiAlias = true)
            drawRect(Rect(0f, 0f, 32f, 32f), Paint.fill(Color.WHITE))
            drawRect(Rect(2f, 2f, 12f, 12f), Paint.fill(Color.RED))
            drawRRect(RRect(Rect(20f, 2f, 30f, 12f), radius = 2f), Paint.fill(Color.RED))
            drawPath(Path { moveTo(2f, 22f); lineTo(12f, 22f); lineTo(7f, 30f); close() }, Paint.fill(Color.RED))
            drawRect(Rect(14f, 22f, 26f, 29f), Paint.stroke(Color.RED, 1f))
            drawImage(bluePixel(), Rect(24f, 14f, 30f, 20f), Paint())
            restore()
        }

        val result = surface.render()
        assertPixel(result.pixels, 4, 4, Color.RED)
        assertTransparent(result.pixels, 16, 16)
        assertPixel(result.pixels, 28, 16, Color.BLUE)
        assertPixel(result.pixels, 14, 22, Color.RED)
        assertEquals(0, result.diagnostics.fatalCount, result.diagnostics.entries.toString())
        assertTrue(
            result.diagnostics.entries.any { entry ->
                entry.facts.any { fact -> fact.key == "clip.strategy" && fact.value == "alpha-mask" }
            },
            result.diagnostics.entries.toString(),
        )
    }

    @Test
    fun `AA device rect uses alpha mask rather than integer scissor`() {
        requireWebGpu()
        val surface = Surface(16, 16)
        surface.canvas {
            save()
            clipRect(Rect(3.5f, 2f, 12.5f, 14f), ClipOp.INTERSECT, antiAlias = true)
            drawRect(Rect(0f, 0f, 16f, 16f), Paint.fill(Color.RED))
            restore()
        }

        val result = surface.render()
        assertTrue(alphaAt(result.pixels, 3, 8) in 1..254)
        assertTrue(
            result.diagnostics.entries.any { entry ->
                entry.facts.any { fact -> fact.key == "clip.strategy" && fact.value == "alpha-mask" }
            },
        )
    }

    private fun requireWebGpu() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")
        runtime!!.close()
    }

    private fun assertPixel(pixels: UByteArray, x: Int, y: Int, expected: Color) {
        val offset = (y * 32 + x) * 4
        assertTrue((pixels[offset].toInt() and 0xff) >= expected.redByte * 200 / 255)
        assertTrue((pixels[offset + 1].toInt() and 0xff) >= expected.greenByte * 200 / 255)
        assertTrue((pixels[offset + 2].toInt() and 0xff) >= expected.blueByte * 200 / 255)
        assertTrue((pixels[offset + 3].toInt() and 0xff) >= expected.alphaByte * 200 / 255)
    }

    private fun assertTransparent(pixels: UByteArray, x: Int, y: Int) {
        val offset = (y * 32 + x) * 4
        assertEquals(0, pixels[offset + 3].toInt() and 0xff)
    }

    private fun alphaAt(pixels: UByteArray, x: Int, y: Int): Int =
        pixels[(y * 16 + x) * 4 + 3].toInt() and 0xff

    private fun bluePixel(): Image = Image.fromPixels(
        width = 1,
        height = 1,
        pixels = byteArrayOf(0, 0, 0xff.toByte(), 0xff.toByte()),
        colorType = ColorType.RGBA_8888,
        sourceId = "clip-blue-pixel",
    )
}
