package org.graphiks.kanvas.gpu.renderer.execution

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GPUBackendRuntimeContractsTest {
    @Test
    fun `offscreen request requires positive dimensions and rgba8 format`() {
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
    fun `native surface binding requires stable platform handle data`() {
        val binding = GPUNativeSurfaceBinding(
            platform = GPUNativePlatform.AppKitMetalLayer,
            width = 1280,
            height = 720,
            pointerLabels = mapOf("nsLayer" to 42L),
        )

        assertEquals(GPUNativePlatform.AppKitMetalLayer, binding.platform)
        assertEquals(42L, binding.pointerLabels.getValue("nsLayer"))
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
}
