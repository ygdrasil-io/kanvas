package org.graphiks.kanvas.skia

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.test.GpuAvailability
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** Freezes the real terminal boundaries for the two selected compositing GMs. */
class CompositingGmSurfaceRefusalEvidenceTest {
    @AfterEach
    fun disposeSharedBackend() {
        GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun `srcmode and rasterallocator preserve their preceding unsupported boundaries`() {
        GpuAvailability.requireWebGpu()
        mapOf(
            "srcmode" to "invalid.preflight.text.blend",
            "rasterallocator" to "unsupported.core_primitive.coverage_sample.scalar_aa_not_promoted",
        ).forEach { (name, expectedCode) ->
            val gm = requireNotNull(SkiaGmRegistry.all().singleOrNull { it.name == name })
            val failure = assertThrows(IllegalStateException::class.java, {
                SkiaGmRenderer.render(gm)
            }, name)
            assertEquals(expectedCode, failure.message.orEmpty().substringBefore(":"), name)
        }
    }
}
