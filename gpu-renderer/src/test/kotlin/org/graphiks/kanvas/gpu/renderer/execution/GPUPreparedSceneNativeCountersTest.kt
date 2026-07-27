package org.graphiks.kanvas.gpu.renderer.execution

import kotlin.test.Test
import kotlin.test.assertEquals
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID

class GPUPreparedSceneNativeCountersTest {
    @Test
    fun `public counters preserve source compatibility and the order of 41 historical fields`() {
        val counters = GPUPreparedSceneNativeCounters(
            11L,
            12L,
            13L,
            preparedImagePipelineCreations = 31L,
            preparedImagePipelineReuses = 37L,
            preparedImageFrameTextureCreations = 41L,
            preparedImageFrameTextureViewCreations = 43L,
            preparedImageFrameSamplerCreations = 47L,
            preparedImageFrameUniformBufferCreations = 53L,
            preparedImageFrameBindGroupCreations = 59L,
            renderPasses = 17L,
            draws = 19L,
            drawIndexed = 23L,
            pipelineBinds = 29L,
        )

        val (encoders, commandBuffers, targetCreations) = counters

        assertEquals(11L, encoders)
        assertEquals(12L, commandBuffers)
        assertEquals(13L, targetCreations)

        val type = GPUPreparedSceneNativeCounters::class.java
        assertEquals(17L, counters.renderPasses)
        assertEquals(19L, counters.draws)
        assertEquals(23L, counters.drawIndexed)
        assertEquals(29L, counters.pipelineBinds)
        assertEquals(31L, counters.preparedImagePipelineCreations)
        assertEquals(37L, counters.preparedImagePipelineReuses)
        assertEquals(41L, counters.preparedImageFrameTextureCreations)
        assertEquals(43L, counters.preparedImageFrameTextureViewCreations)
        assertEquals(47L, counters.preparedImageFrameSamplerCreations)
        assertEquals(53L, counters.preparedImageFrameUniformBufferCreations)
        assertEquals(59L, counters.preparedImageFrameBindGroupCreations)
        assertEquals(48, type.declaredMethods.count { it.name.matches(Regex("component\\d+")) })
        assertEquals(48, type.declaredMethods.single { it.name == "copy" }.parameterCount)
        assertEquals(
            48,
            type.declaredConstructors.filterNot { it.isSynthetic }.maxOf { it.parameterCount },
        )
    }

    @Test
    fun `native counters factory exposes the handle free prepared image snapshot`() {
        val recorder = GPUPreparedImageNativeCounterRecorder()
        recorder.recordPipelineCreation()
        recorder.recordPipelineReuse()
        recorder.recordFrameTextureCreation()
        recorder.recordFrameTextureViewCreation()
        recorder.recordFrameSamplerCreation()
        recorder.recordFrameUniformBufferCreation()
        recorder.recordFrameBindGroupCreation()
        val session = GPUPreparedSceneFrameSession(
            deviceGeneration = GPUDeviceGenerationID(1L),
            coordinatorFactory = GPUFrameCoordinatorFactory { _, _ -> error("unused") },
            nativeCountersFactory = {
                GPUPreparedSceneNativeCounters()
                    .withPreparedImageNativeCounters(recorder.snapshot())
            },
        )

        val counters = session.nativeCounters()

        assertEquals(1L, counters.preparedImagePipelineCreations)
        assertEquals(1L, counters.preparedImagePipelineReuses)
        assertEquals(1L, counters.preparedImageFrameTextureCreations)
        assertEquals(1L, counters.preparedImageFrameTextureViewCreations)
        assertEquals(1L, counters.preparedImageFrameSamplerCreations)
        assertEquals(1L, counters.preparedImageFrameUniformBufferCreations)
        assertEquals(1L, counters.preparedImageFrameBindGroupCreations)
    }

    @Test
    fun `internal render counters use a dedicated defaultable factory`() {
        val coordinatorFactory = GPUFrameCoordinatorFactory { _, _ -> error("unused") }
        val defaultSession = GPUPreparedSceneFrameSession(
            deviceGeneration = GPUDeviceGenerationID(1L),
            coordinatorFactory = coordinatorFactory,
        )
        val instrumentedSession = GPUPreparedSceneFrameSession(
            deviceGeneration = GPUDeviceGenerationID(1L),
            coordinatorFactory = coordinatorFactory,
            renderCountersFactory = {
                GPUPreparedSceneRenderCounters(
                    renderPasses = 7L,
                    draws = 11L,
                    drawIndexed = 13L,
                    pipelineBinds = 17L,
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
                pipelineBinds = 17L,
                coverageMaskTextureCreations = 17L,
                coverageMaskSlotReuses = 19L,
                msaaColorTextureCreations = 23L,
                msaaColorSlotReuses = 29L,
            ),
            instrumentedSession.renderCounters(),
        )
    }
}
