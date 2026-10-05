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
    fun lifecycleEpoch(): Long = 0L
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
    private var lease: ProductionGpuBackendRuntimeLease? = null

    override fun createOrNull(): GpuBackendSessionPort? = synchronized(this) {
        val current = lease
        if (current != null && ProductionGpuBackendRuntimeLeases.isCurrent(current)) {
            return@synchronized GpuBackendSessionAdapter(current.session)
        }
        current?.let(ProductionGpuBackendRuntimeLeases::release)
        lease = ProductionGpuBackendRuntimeLeases.acquireOrNull()
        lease?.session?.let(::GpuBackendSessionAdapter)
    }

    override fun lifecycleEpoch(): Long = GPUBackendRuntimeFactory.lifecycleEpoch()

    override fun disposeGeneration(deviceGeneration: GPUDeviceGenerationID) {
        val current = synchronized(this) { lease }
        if (current?.session?.deviceGeneration != deviceGeneration) return
        ProductionGpuBackendRuntimeLeases.disposeGeneration(deviceGeneration)
        synchronized(this) {
            if (lease === current) lease = null
        }
    }

    override fun close() {
        val current = synchronized(this) {
            lease.also { lease = null }
        }
        current?.let(ProductionGpuBackendRuntimeLeases::release)
    }
}

/** One production-context ownership claim on the process-wide backend runtime. */
internal class ProductionGpuBackendRuntimeLease internal constructor(
    internal val session: GPUBackendSession,
    internal val lifecycleEpoch: Long,
)

/**
 * Coordinates production-context ownership of the native singleton. A raw factory disposal
 * advances its epoch, making all outstanding leases stale without allowing one stale close to
 * dispose a subsequently recreated runtime.
 */
internal object ProductionGpuBackendRuntimeLeases {
    private data class SharedRuntime(
        val session: GPUBackendSession,
        val lifecycleEpoch: Long,
        var owners: Int,
    )

    private var shared: SharedRuntime? = null

    fun acquireOrNull(): ProductionGpuBackendRuntimeLease? = synchronized(this) {
        val currentEpoch = GPUBackendRuntimeFactory.lifecycleEpoch()
        val current = shared?.takeIf { it.lifecycleEpoch == currentEpoch }
        if (current == null) {
            shared = null
            val session = GPUBackendRuntimeFactory.createOrNull() ?: return@synchronized null
            val created = SharedRuntime(
                session = session,
                lifecycleEpoch = GPUBackendRuntimeFactory.lifecycleEpoch(),
                owners = 1,
            )
            shared = created
            return@synchronized ProductionGpuBackendRuntimeLease(
                created.session,
                created.lifecycleEpoch,
            )
        }
        current.owners += 1
        ProductionGpuBackendRuntimeLease(current.session, current.lifecycleEpoch)
    }

    fun isCurrent(lease: ProductionGpuBackendRuntimeLease): Boolean = synchronized(this) {
        val current = shared
        current != null &&
            current.lifecycleEpoch == GPUBackendRuntimeFactory.lifecycleEpoch() &&
            current.lifecycleEpoch == lease.lifecycleEpoch &&
            current.session === lease.session
    }

    fun release(lease: ProductionGpuBackendRuntimeLease) = synchronized(this) {
        val current = shared ?: return@synchronized
        if (current.lifecycleEpoch != GPUBackendRuntimeFactory.lifecycleEpoch() ||
            current.lifecycleEpoch != lease.lifecycleEpoch ||
            current.session !== lease.session
        ) {
            if (current.lifecycleEpoch != GPUBackendRuntimeFactory.lifecycleEpoch()) shared = null
            return@synchronized
        }
        check(current.owners > 0) { "Production runtime lease count underflow" }
        current.owners -= 1
        if (current.owners == 0) {
            shared = null
            GPUBackendRuntimeFactory.dispose()
        }
    }

    fun disposeGeneration(deviceGeneration: GPUDeviceGenerationID) = synchronized(this) {
        val current = shared ?: return@synchronized
        if (current.lifecycleEpoch != GPUBackendRuntimeFactory.lifecycleEpoch()) {
            shared = null
            return@synchronized
        }
        if (current.session.deviceGeneration == deviceGeneration) {
            shared = null
            GPUBackendRuntimeFactory.dispose()
        }
    }
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
                nativeCounters = nativeAfter.evidenceSince(
                    before = nativeBefore,
                    nativePayloadRegistrations = frame.nativePayloadRegistrations,
                ),
            ),
        )
    }
    override fun close() = delegate.close()
}

private fun org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedSceneNativeCounters.evidenceSince(
    before: org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedSceneNativeCounters,
    nativePayloadRegistrations: Long,
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
    "nativePayloadRegistrations" to nativePayloadRegistrations,
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
