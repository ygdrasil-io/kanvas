package org.graphiks.kanvas.skia

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.test.GpuAvailability
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CurvedClipGmSurfaceRefusalEvidenceTest {
    @AfterEach
    fun disposeSharedBackend() {
        GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun `curved clip GMs expose the cubic route boundary and promotion`() {
        GpuAvailability.requireWebGpu()
        val clipCubic = requireNotNull(SkiaGmRegistry.all().singleOrNull { it.name == "clipcubic" })
        val refusal = requireNotNull(SkiaGmRenderer.renderTerminalAttempt(clipCubic)) {
            "clipcubic unexpectedly rendered"
        }
        assertEquals("unsupported.stroke.width_invalid", refusal.diagnostic.substringBefore(":"))
        assertEquals(17, refusal.operationCount)
        println(
            "task116.gm-refusal gm=clipcubic operations=${refusal.operationCount} " +
                "diagnostic=${refusal.diagnostic.substringBefore(":")}",
        )

        val clippedCubic = requireNotNull(SkiaGmRegistry.all().singleOrNull { it.name == "clippedcubic" })
        assertNull(
            SkiaGmRenderer.renderTerminalAttempt(clippedCubic),
            "clippedcubic should complete through the bounded cubic path route",
        )
        println("task116.gm-render gm=clippedcubic route=bounded-cubic-path")
    }
}
