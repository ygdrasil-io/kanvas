package org.graphiks.kanvas.gpu.renderer.execution

import java.util.concurrent.atomic.AtomicLong

/**
 * Handle-free evidence for prepared-image native work.
 *
 * Pipeline counters describe session-owned cache behavior. Every `frame*Creations` counter
 * describes a frame-owned native allocation and deliberately has no cache/reuse counterpart.
 */
internal data class GPUPreparedImageNativeCounterSnapshot(
    val pipelineCreations: Long = 0L,
    val pipelineReuses: Long = 0L,
    val frameTextureCreations: Long = 0L,
    val frameTextureViewCreations: Long = 0L,
    val frameSamplerCreations: Long = 0L,
    val frameUniformBufferCreations: Long = 0L,
    val frameBindGroupCreations: Long = 0L,
    val frameTextureWriteTextureCalls: Long = 0L,
)

internal class GPUPreparedImageNativeCounterRecorder {
    private val pipelineCreations = AtomicLong(0L)
    private val pipelineReuses = AtomicLong(0L)
    private val frameTextureCreations = AtomicLong(0L)
    private val frameTextureViewCreations = AtomicLong(0L)
    private val frameSamplerCreations = AtomicLong(0L)
    private val frameUniformBufferCreations = AtomicLong(0L)
    private val frameBindGroupCreations = AtomicLong(0L)
    private val frameTextureWriteTextureCalls = AtomicLong(0L)

    fun recordPipelineCreation() {
        pipelineCreations.incrementAndGet()
    }

    fun recordPipelineReuse() {
        pipelineReuses.incrementAndGet()
    }

    fun recordFrameTextureCreation() {
        frameTextureCreations.incrementAndGet()
    }

    fun recordFrameTextureViewCreation() {
        frameTextureViewCreations.incrementAndGet()
    }

    fun recordFrameSamplerCreation() {
        frameSamplerCreations.incrementAndGet()
    }

    fun recordFrameUniformBufferCreation() {
        frameUniformBufferCreations.incrementAndGet()
    }

    fun recordFrameBindGroupCreation() {
        frameBindGroupCreations.incrementAndGet()
    }

    fun recordFrameTextureWriteTexture() {
        frameTextureWriteTextureCalls.incrementAndGet()
    }

    fun snapshot(): GPUPreparedImageNativeCounterSnapshot =
        GPUPreparedImageNativeCounterSnapshot(
            pipelineCreations = pipelineCreations.get(),
            pipelineReuses = pipelineReuses.get(),
            frameTextureCreations = frameTextureCreations.get(),
            frameTextureViewCreations = frameTextureViewCreations.get(),
            frameSamplerCreations = frameSamplerCreations.get(),
            frameUniformBufferCreations = frameUniformBufferCreations.get(),
            frameBindGroupCreations = frameBindGroupCreations.get(),
            frameTextureWriteTextureCalls = frameTextureWriteTextureCalls.get(),
        )
}
