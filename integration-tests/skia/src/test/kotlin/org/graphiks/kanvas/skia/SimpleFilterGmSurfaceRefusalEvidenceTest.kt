package org.graphiks.kanvas.skia

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.test.GpuAvailability
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SimpleFilterGmSurfaceRefusalEvidenceTest {
    @AfterEach
    fun disposeSharedBackend() {
        GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun `targeted filter GMs expose fresh terminal route facts`() {
        GpuAvailability.requireWebGpu()
        listOf("blurrects", "offsetimagefilter", "blurquickreject").forEach { name ->
            val gm = requireNotNull(SkiaGmRegistry.all().singleOrNull { it.name == name })
            val attempt = requireNotNull(SkiaGmRenderer.renderTerminalAttempt(gm)) {
                "$name unexpectedly rendered through an unsupported route"
            }
            val actual = "${attempt.operationCount}:${attempt.diagnostic.substringBefore(":")}"
            assertEquals(
                mapOf(
                    "blurrects" to "145:unsupported.material.source_unimplemented",
                    "offsetimagefilter" to "17:unsupported.image.native_binding",
                    "blurquickreject" to "19:unsupported.stroke.width_invalid",
                )[name] ?: "unrecorded",
                actual,
                name,
            )
        }
    }
}
