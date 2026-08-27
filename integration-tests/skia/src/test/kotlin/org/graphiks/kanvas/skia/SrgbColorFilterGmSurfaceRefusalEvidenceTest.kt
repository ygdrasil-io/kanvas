package org.graphiks.kanvas.skia

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.test.GpuAvailability
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SrgbColorFilterGmSurfaceRefusalEvidenceTest {
    @AfterEach
    fun disposeSharedBackend() {
        GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun `color filter GM variants preserve their first terminal route refusal`() {
        GpuAvailability.requireWebGpu()
        val expected = mapOf(
            "colorfilterimagefilter" to "53:unsupported.core_primitive.coverage_sample.scalar_aa_not_promoted",
            "srgb_colorfilter" to "7:unsupported.image.native_binding",
        )

        expected.forEach { (name, reason) ->
            val gm = requireNotNull(SkiaGmRegistry.all().singleOrNull { it.name == name })
            val attempt = requireNotNull(SkiaGmRenderer.renderTerminalAttempt(gm)) {
                "$name unexpectedly rendered through an unsupported route"
            }
            println("task9.gm-refusal name=$name operations=${attempt.operationCount} diagnostic=${attempt.diagnostic}")
            assertEquals(reason, "${attempt.operationCount}:${attempt.diagnostic.substringBefore(":")}", name)
        }
    }
}
