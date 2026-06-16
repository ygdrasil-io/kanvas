package org.graphiks.kanvas.gpu.renderer.execution

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class GPUBackendRuntimeWgpuSmokeTest {
    @Test
    fun `align copy bytes per row rounds up to 256-byte blocks`() {
        assertEquals(256, alignCopyBytesPerRow(4))
        assertEquals(256, alignCopyBytesPerRow(128))
        assertEquals(512, alignCopyBytesPerRow(300))
    }

    @Test
    fun `strip row padding compacts padded rgba rows`() {
        val padded = byteArrayOf(
            1, 2, 3, 4, 5, 6, 7, 8,
            9, 9, 9, 9, 9, 9, 9, 9,
            10, 11, 12, 13, 14, 15, 16, 17,
            8, 8, 8, 8, 8, 8, 8, 8,
        )

        val stripped = stripRowPadding(
            bytes = padded,
            width = 2,
            height = 2,
            bytesPerPixel = 4,
            paddedBytesPerRow = 16,
        )

        assertContentEquals(
            byteArrayOf(
                1, 2, 3, 4, 5, 6, 7, 8,
                10, 11, 12, 13, 14, 15, 16, 17,
            ),
            stripped,
        )
    }

    @Test
    fun `swizzle bgra to rgba rewrites channel order per pixel`() {
        val bgra = byteArrayOf(
            30, 20, 10, 40,
            70, 60, 50, 80,
        )

        val rgba = swizzleBgraToRgba(bgra)

        assertContentEquals(
            byteArrayOf(
                10, 20, 30, 40,
                50, 60, 70, 80,
            ),
            rgba,
        )
    }

    @Test
    fun `window surface helpers derive deterministic device generation and target id`() {
        val binding = GPUNativeSurfaceBinding(
            platform = GPUNativePlatform.AppKitMetalLayer,
            width = 640,
            height = 480,
            pointerLabels = mapOf("layerHandle" to 42L),
        )

        assertEquals(GPUDeviceGeneration(7L), windowSurfaceDeviceGeneration(windowRuntimeOrdinal = 7L))
        assertEquals(
            "wgpu-window-surface-7-appkitmetallayer-640x480",
            windowSurfaceTargetId(windowRuntimeOrdinal = 7L, binding = binding),
        )
    }

    @Test
    fun `offscreen target helper derives deterministic unique target id per session and target`() {
        val request = GPUOffscreenTargetRequest(
            width = 320,
            height = 180,
            colorFormat = "rgba8unorm",
        )

        assertEquals(GPUDeviceGeneration(3L), sessionDeviceGeneration(sessionOrdinal = 3L))
        assertEquals(
            "wgpu-offscreen-3-5-320x180-rgba8unorm",
            offscreenTargetId(
                sessionOrdinal = 3L,
                offscreenTargetOrdinal = 5L,
                request = request,
            ),
        )
    }
}
