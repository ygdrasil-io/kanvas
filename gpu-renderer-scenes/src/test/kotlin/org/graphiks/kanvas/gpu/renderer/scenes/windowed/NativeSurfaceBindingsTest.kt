package org.graphiks.kanvas.gpu.renderer.scenes.windowed

import org.graphiks.kanvas.gpu.renderer.execution.GPUNativePlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NativeSurfaceBindingsTest {
    @Test
    fun `appkit metal layer binding exposes native surface metadata`() {
        val binding = appKitMetalLayerBinding(width = 640, height = 480, nsLayer = 99L)

        assertEquals(GPUNativePlatform.AppKitMetalLayer, binding.platform)
        assertEquals(640, binding.width)
        assertEquals(480, binding.height)
        assertEquals(99L, binding.pointerLabels.getValue("nsLayer"))
    }

    @Test
    fun `appkit metal layer binding requires non zero nsLayer`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            appKitMetalLayerBinding(width = 640, height = 480, nsLayer = 0L)
        }

        assertEquals("AppKit Metal layer pointer must be non-zero", failure.message)
    }
}
