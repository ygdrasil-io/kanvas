package org.graphiks.kanvas.gpu.renderer.execution

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID

class GPUPreparedSceneNativeCountersTest {
    @Test
    fun `public command evidence snapshots constructor and copy maps and rejects mutation`() {
        val first = GPUPreparedNativeCommandEncodingCounters(draws = 1L, bindGroups = 2L)
        val second = GPUPreparedNativeCommandEncodingCounters(drawIndexed = 3L, bindGroups = 4L)
        val source = linkedMapOf(7 to first, 9 to second)
        val counters = GPUPreparedSceneNativeCounters(commandsByCommandId = source)

        source.clear()
        assertEquals(linkedMapOf(7 to first, 9 to second), counters.commandsByCommandId)
        assertEquals(listOf(7, 9), counters.commandsByCommandId.keys.toList())
        assertFailsWith<UnsupportedOperationException> {
            (counters.commandsByCommandId as MutableMap<Int, GPUPreparedNativeCommandEncodingCounters>)
                .clear()
        }
        assertEquals(linkedMapOf(7 to first, 9 to second), counters.commandsByCommandId)

        val copySource = linkedMapOf(11 to second, 13 to first)
        val copied = counters.copy(commandsByCommandId = copySource)
        copySource.clear()
        assertEquals(linkedMapOf(11 to second, 13 to first), copied.commandsByCommandId)
        assertEquals(listOf(11, 13), copied.commandsByCommandId.keys.toList())
        assertFailsWith<UnsupportedOperationException> {
            (copied.commandsByCommandId as MutableMap<Int, GPUPreparedNativeCommandEncodingCounters>)
                .put(17, first)
        }
        assertEquals(linkedMapOf(11 to second, 13 to first), copied.commandsByCommandId)
    }

    @Test
    fun `public counters preserve historical field order and append exact command evidence`() {
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
            destinationCopies = 61L,
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
        assertEquals(61L, counters.destinationCopies)
        assertEquals(50, type.declaredMethods.count { it.name.matches(Regex("component\\d+")) })
        assertEquals(50, type.declaredMethods.single { it.name == "copy" }.parameterCount)
        assertEquals(
            50,
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
