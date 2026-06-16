package org.graphiks.kanvas.gpu.renderer.execution

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GPUBackendRuntimeContractsTest {
    @Test
    fun `offscreen request requires positive dimensions and nonblank format`() {
        val request = GPUOffscreenTargetRequest(width = 320, height = 180, colorFormat = "rgba8unorm")

        assertEquals(320, request.width)
        assertEquals(180, request.height)
        assertEquals("rgba8unorm", request.colorFormat)
        assertFailsWith<IllegalArgumentException> {
            GPUOffscreenTargetRequest(width = 0, height = 180, colorFormat = "rgba8unorm")
        }
        assertFailsWith<IllegalArgumentException> {
            GPUOffscreenTargetRequest(width = 320, height = -1, colorFormat = "rgba8unorm")
        }
        assertFailsWith<IllegalArgumentException> {
            GPUOffscreenTargetRequest(width = 320, height = 180, colorFormat = "")
        }
    }

    @Test
    fun `native surface binding requires positive size and nonblank pointer label keys`() {
        val binding = GPUNativeSurfaceBinding(
            platform = GPUNativePlatform.AppKitMetalLayer,
            width = 1280,
            height = 720,
            pointerLabels = mapOf("layerHandle" to 0L),
        )

        assertEquals(GPUNativePlatform.AppKitMetalLayer, binding.platform)
        assertEquals(0L, binding.pointerLabels.getValue("layerHandle"))
        assertFailsWith<IllegalArgumentException> {
            GPUNativeSurfaceBinding(
                platform = GPUNativePlatform.AppKitMetalLayer,
                width = 1280,
                height = 720,
                pointerLabels = emptyMap(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GPUNativeSurfaceBinding(
                platform = GPUNativePlatform.AppKitMetalLayer,
                width = 0,
                height = 720,
                pointerLabels = mapOf("nsLayer" to 42L),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GPUNativeSurfaceBinding(
                platform = GPUNativePlatform.AppKitMetalLayer,
                width = 1280,
                height = 720,
                pointerLabels = mapOf("" to 42L),
            )
        }
    }

    @Test
    fun `clear color stores normalized channel values`() {
        val color = GPUClearColor(red = 0.1, green = 0.2, blue = 0.3, alpha = 1.0)

        assertEquals(0.1, color.red)
        assertEquals(1.0, color.alpha)
        assertFailsWith<IllegalArgumentException> {
            GPUClearColor(red = -0.01, green = 0.2, blue = 0.3, alpha = 1.0)
        }
        assertFailsWith<IllegalArgumentException> {
            GPUClearColor(red = 0.1, green = 1.01, blue = 0.3, alpha = 1.0)
        }
        assertFailsWith<IllegalArgumentException> {
            GPUClearColor(red = 0.1, green = 0.2, blue = -0.01, alpha = 1.0)
        }
        assertFailsWith<IllegalArgumentException> {
            GPUClearColor(red = 0.1, green = 0.2, blue = 0.3, alpha = 1.01)
        }
    }

    @Test
    fun `backend rect draw requires rgba size four and positive scissor dimensions`() {
        val draw = GPUBackendRectDraw(
            rgbaPremul = floatArrayOf(0.1f, 0.2f, 0.3f, 1.0f),
            scissorX = 4,
            scissorY = 8,
            scissorWidth = 16,
            scissorHeight = 32,
        )

        assertEquals(16, draw.scissorWidth)
        assertFailsWith<IllegalArgumentException> {
            GPUBackendRectDraw(
                rgbaPremul = floatArrayOf(0.1f, 0.2f, 0.3f),
                scissorX = 0,
                scissorY = 0,
                scissorWidth = 16,
                scissorHeight = 32,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GPUBackendRectDraw(
                rgbaPremul = floatArrayOf(0.1f, 0.2f, 0.3f, 1.0f),
                scissorX = 0,
                scissorY = 0,
                scissorWidth = 0,
                scissorHeight = 32,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GPUBackendRectDraw(
                rgbaPremul = floatArrayOf(0.1f, 0.2f, 0.3f, 1.0f),
                scissorX = 0,
                scissorY = 0,
                scissorWidth = 16,
                scissorHeight = 0,
            )
        }
    }
}
