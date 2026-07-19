package org.graphiks.kanvas.gpu.renderer.execution

import kotlin.test.Test
import kotlin.test.assertEquals

class GPUPreparedSceneNativeCountersTest {
    @Test
    fun `public counters preserve the exact historical data class ABI`() {
        val counters = GPUPreparedSceneNativeCounters(
            11L,
            12L,
            13L,
        )

        val (encoders, commandBuffers, targetCreations) = counters

        assertEquals(11L, encoders)
        assertEquals(12L, commandBuffers)
        assertEquals(13L, targetCreations)

        val type = GPUPreparedSceneNativeCounters::class.java
        assertEquals(37, type.declaredMethods.count { it.name.matches(Regex("component\\d+")) })
        assertEquals(37, type.declaredMethods.single { it.name == "copy" }.parameterCount)
        assertEquals(
            37,
            type.declaredConstructors.filterNot { it.isSynthetic }.maxOf { it.parameterCount },
        )
    }

    @Test
    fun `internal render counters use a dedicated defaultable factory`() {
        val coordinatorFactory = GPUFrameCoordinatorFactory { _, _ -> error("unused") }
        val defaultSession = GPUPreparedSceneFrameSession(coordinatorFactory = coordinatorFactory)
        val instrumentedSession = GPUPreparedSceneFrameSession(
            coordinatorFactory = coordinatorFactory,
            renderCountersFactory = {
                GPUPreparedSceneRenderCounters(
                    renderPasses = 7L,
                    draws = 11L,
                    drawIndexed = 13L,
                    coverageMaskTextureCreations = 17L,
                    coverageMaskSlotReuses = 19L,
                    msaaColorTextureCreations = 23L,
                    msaaColorSlotReuses = 29L,
                )
            },
        )

        assertEquals(GPUPreparedSceneRenderCounters(), defaultSession.renderCounters())
        assertEquals(
            GPUPreparedSceneRenderCounters(
                renderPasses = 7L,
                draws = 11L,
                drawIndexed = 13L,
                coverageMaskTextureCreations = 17L,
                coverageMaskSlotReuses = 19L,
                msaaColorTextureCreations = 23L,
                msaaColorSlotReuses = 29L,
            ),
            instrumentedSession.renderCounters(),
        )
    }
}
