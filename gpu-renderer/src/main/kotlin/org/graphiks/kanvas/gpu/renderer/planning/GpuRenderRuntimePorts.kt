package org.graphiks.kanvas.gpu.renderer.planning

import java.util.Collections
import java.util.concurrent.CompletionStage
import kotlinx.coroutines.suspendCancellableCoroutine
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendSession
import org.graphiks.kanvas.gpu.renderer.execution.GPUFrameImmediateState
import org.graphiks.kanvas.gpu.renderer.execution.GPUOffscreenTargetRequest
import org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedSceneCompletedFrameResult
import org.graphiks.kanvas.gpu.renderer.execution.GPUSceneFrameOutputRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal interface GpuBackendRuntimeOwnerPort : AutoCloseable {
    fun createOrNull(): GpuBackendSessionPort?
    fun disposeGeneration(deviceGeneration: GPUDeviceGenerationID)
}
internal interface GpuBackendSessionPort : AutoCloseable {
    val deviceGeneration: GPUDeviceGenerationID
    val capabilities: GPUCapabilities?
    fun prepareSceneFrameSession(request: GPUOffscreenTargetRequest): GpuPreparedSceneSessionPort
}
internal interface GpuPreparedSceneSessionPort : AutoCloseable {
    val deviceGeneration: GPUDeviceGenerationID
    fun renderFrame(
        taskList: GPUTaskList,
        outputRequest: GPUSceneFrameOutputRequest,
        visualCommandCount: Int,
    ): GpuPreparedFrameHandle
}
/** Immutable native observations captured for exactly one prepared frame. */
internal class GpuPreparedFrameMetricsSnapshot(
    val visualCommandCount: Int,
    val pipelineBinds: Long,
    val draws: Long,
    val drawIndexed: Long,
    nativeCounters: Map<String, Long>,
) {
    val nativeCounters: Map<String, Long> =
        Collections.unmodifiableMap(LinkedHashMap(nativeCounters))

    init {
        require(visualCommandCount >= 0)
        require(pipelineBinds >= 0L && draws >= 0L && drawIndexed >= 0L)
        require(nativeCounters.keys.all(String::isNotBlank) && nativeCounters.values.all { it >= 0L })
    }
}
/** A submitted frame owns one release action, irrespective of its terminal path. */
internal data class GpuPreparedFrameHandle(
    val immediateState: GPUFrameImmediateState,
    val completion: CompletionStage<GPUPreparedSceneCompletedFrameResult>,
    val metricsSnapshot: GpuPreparedFrameMetricsSnapshot,
    val release: () -> Unit = {},
)
internal fun interface GpuCompletionAwaiter { suspend fun await(completion: CompletionStage<GPUPreparedSceneCompletedFrameResult>): GPUPreparedSceneCompletedFrameResult }

internal class DefaultGpuBackendRuntimeOwner : GpuBackendRuntimeOwnerPort {
    private var session: GPUBackendSession? = null
    override fun createOrNull(): GpuBackendSessionPort? = synchronized(this) {
        session?.let(::GpuBackendSessionAdapter) ?: GPUBackendRuntimeFactory.createOrNull()?.also { session = it }?.let(::GpuBackendSessionAdapter)
    }
    override fun disposeGeneration(deviceGeneration: GPUDeviceGenerationID) = synchronized(this) {
        if (session?.deviceGeneration == deviceGeneration) { session?.close(); session = null; GPUBackendRuntimeFactory.dispose() }
    }
    override fun close() = synchronized(this) { session?.close(); session = null; GPUBackendRuntimeFactory.dispose() }
}
internal class GpuBackendSessionAdapter(private val delegate: GPUBackendSession) : GpuBackendSessionPort {
    override val deviceGeneration get() = delegate.deviceGeneration
    override val capabilities get() = delegate.capabilities
    override fun prepareSceneFrameSession(request: GPUOffscreenTargetRequest) = GpuPreparedSceneSessionAdapter(delegate.prepareSceneFrameSession(request))
    override fun close() = delegate.close()
}
internal class GpuPreparedSceneSessionAdapter(private val delegate: org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedSceneFrameSession) : GpuPreparedSceneSessionPort {
    override val deviceGeneration get() = delegate.deviceGeneration
    override fun renderFrame(
        taskList: GPUTaskList,
        outputRequest: GPUSceneFrameOutputRequest,
        visualCommandCount: Int,
    ): GpuPreparedFrameHandle {
        val nativeBefore = delegate.nativeCounters()
        val renderBefore = delegate.renderCounters()
        val frame = delegate.renderFrame(taskList, outputRequest)
        val nativeAfter = delegate.nativeCounters()
        val renderAfter = delegate.renderCounters()
        return GpuPreparedFrameHandle(
            immediateState = frame.immediateState,
            completion = frame.completion,
            metricsSnapshot = GpuPreparedFrameMetricsSnapshot(
                visualCommandCount = visualCommandCount,
                pipelineBinds = nativeDelta(renderAfter.pipelineBinds, renderBefore.pipelineBinds),
                draws = nativeDelta(renderAfter.draws, renderBefore.draws),
                drawIndexed = nativeDelta(renderAfter.drawIndexed, renderBefore.drawIndexed),
                nativeCounters = nativeAfter.evidenceSince(nativeBefore),
            ),
        )
    }
    override fun close() = delegate.close()
}

private fun org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedSceneNativeCounters.evidenceSince(
    before: org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedSceneNativeCounters,
): Map<String, Long> = linkedMapOf(
    "encoders" to nativeDelta(encoders, before.encoders),
    "commandBuffers" to nativeDelta(commandBuffers, before.commandBuffers),
    "targetCreations" to nativeDelta(targetCreations, before.targetCreations),
    "targetCloses" to nativeDelta(targetCloses, before.targetCloses),
    "targetNativeUses" to nativeDelta(targetNativeUses, before.targetNativeUses),
    "submits" to nativeDelta(submits, before.submits),
    "readbackCopies" to nativeDelta(readbackCopies, before.readbackCopies),
    "retentionRegistrations" to nativeDelta(retentionRegistrations, before.retentionRegistrations),
    "retentionCompletions" to nativeDelta(retentionCompletions, before.retentionCompletions),
    "retentionQuarantines" to nativeDelta(retentionQuarantines, before.retentionQuarantines),
    "frameCoordinatorCreations" to nativeDelta(frameCoordinatorCreations, before.frameCoordinatorCreations),
    "nativePayloadRegistrations" to nativeDelta(nativePayloadRegistrations, before.nativePayloadRegistrations),
    "renderPasses" to nativeDelta(renderPasses, before.renderPasses),
    "draws" to nativeDelta(draws, before.draws),
    "drawIndexed" to nativeDelta(drawIndexed, before.drawIndexed),
    "pipelineBinds" to nativeDelta(pipelineBinds, before.pipelineBinds),
    "destinationCopies" to nativeDelta(destinationCopies, before.destinationCopies),
)

private fun nativeDelta(after: Long, before: Long): Long =
    Math.subtractExact(after, before).also { delta -> require(delta >= 0L) }
internal object DefaultGpuCompletionAwaiter : GpuCompletionAwaiter {
    override suspend fun await(completion: CompletionStage<GPUPreparedSceneCompletedFrameResult>): GPUPreparedSceneCompletedFrameResult = suspendCancellableCoroutine { continuation ->
        completion.whenComplete { value, failure -> if (failure == null) continuation.resume(value) else continuation.resumeWithException(failure) }
    }
}
