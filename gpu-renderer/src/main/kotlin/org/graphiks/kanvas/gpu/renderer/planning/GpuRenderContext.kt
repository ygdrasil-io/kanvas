package org.graphiks.kanvas.gpu.renderer.planning

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.graphiks.kanvas.gpu.plan.PlanCapabilitySnapshot
import org.graphiks.kanvas.gpu.plan.PlanLogicalColorFormat
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.execution.GPUOffscreenTargetRequest

public data class GpuRenderSessionKey(
    public val deviceGeneration: Long,
    public val width: Int,
    public val height: Int,
    public val internalFormat: PlanLogicalColorFormat,
) {
    init {
        require(deviceGeneration >= 0 && width > 0 && height > 0)
    }
}

internal sealed interface GpuPreparedSessionAcquisition {
    data class Ready(val session: GpuPreparedSceneSessionPort) : GpuPreparedSessionAcquisition

    data class GenerationMismatch(
        val expectedGeneration: GPUDeviceGenerationID,
        val actualGeneration: GPUDeviceGenerationID,
    ) : GpuPreparedSessionAcquisition

    data object Unavailable : GpuPreparedSessionAcquisition
}

/** Process-scoped owner for the runtime, its submission workers, and reusable prepared targets. */
public class GpuRenderContext internal constructor(
    private val runtimeOwner: GpuBackendRuntimeOwnerPort = DefaultGpuBackendRuntimeOwner(),
    internal val completionAwaiter: GpuCompletionAwaiter = DefaultGpuCompletionAwaiter,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : AutoCloseable {
    private data class Entry(
        val session: GpuPreparedSceneSessionPort,
        val lock: Mutex = Mutex(),
    )

    private val workerScope = CoroutineScope(SupervisorJob() + workerDispatcher)
    private val workers = linkedSetOf<Job>()
    private var backend: GpuBackendSessionPort? = null
    private var snapshot: PlanCapabilitySnapshot? = null
    private var closed = false
    private val entries = linkedMapOf<GpuRenderSessionKey, Entry>()
    private val invalidatedGenerations = mutableSetOf<Long>()
    private val invalidatingGenerations = mutableSetOf<Long>()

    internal fun capabilities(): PlanCapabilitySnapshot? = synchronized(this) {
        if (closed || invalidatingGenerations.isNotEmpty()) return null
        snapshot ?: runtimeOwner.createOrNull()?.let { created ->
            val supported = created.capabilities?.toPlanCapabilitySnapshot(created.deviceGeneration)
            if (
                created.deviceGeneration.value !in invalidatedGenerations &&
                supported is GpuPlanCapabilityAdapterResult.Supported
            ) {
                backend = created
                supported.snapshot.also { snapshot = it }
            } else {
                runCatching { created.close() }
                null
            }
        }
    }

    internal fun prepared(key: GpuRenderSessionKey): GpuPreparedSceneSessionPort? =
        (acquirePrepared(key) as? GpuPreparedSessionAcquisition.Ready)?.session

    internal fun acquirePrepared(key: GpuRenderSessionKey): GpuPreparedSessionAcquisition {
        var mismatchedSession: GpuPreparedSceneSessionPort? = null
        val acquisition = synchronized(this) {
            if (
                closed ||
                invalidatingGenerations.isNotEmpty() ||
                key.deviceGeneration in invalidatedGenerations ||
                snapshot?.deviceGeneration != key.deviceGeneration
            ) {
                return@synchronized GpuPreparedSessionAcquisition.Unavailable
            }
            entries[key]?.let { entry ->
                return@synchronized GpuPreparedSessionAcquisition.Ready(entry.session)
            }
            val owner = backend ?: return@synchronized GpuPreparedSessionAcquisition.Unavailable
            val session = owner.prepareSceneFrameSession(
                GPUOffscreenTargetRequest(
                    key.width,
                    key.height,
                    GPUColorFormat.RGBA8UnormSrgb,
                    GPUColorInterpretation.LinearPremul,
                ),
            )
            if (session.deviceGeneration != owner.deviceGeneration || session.deviceGeneration.value != key.deviceGeneration) {
                mismatchedSession = session
                GpuPreparedSessionAcquisition.GenerationMismatch(
                    expectedGeneration = GPUDeviceGenerationID(key.deviceGeneration),
                    actualGeneration = session.deviceGeneration,
                )
            } else {
                entries[key] = Entry(session)
                GpuPreparedSessionAcquisition.Ready(session)
            }
        }
        mismatchedSession?.let(::closeSessionBestEffort)
        return acquisition
    }

    internal fun backendCapabilities() = synchronized(this) {
        if (closed || invalidatingGenerations.isNotEmpty()) null else backend?.capabilities
    }

    internal fun launchWorker(block: suspend () -> Unit): Boolean {
        lateinit var worker: Job
        synchronized(this) {
            if (closed) return false
            worker = workerScope.launch(start = CoroutineStart.LAZY) {
                try {
                    block()
                } finally {
                    synchronized(this@GpuRenderContext) { workers.remove(worker) }
                }
            }
            workers += worker
        }
        worker.start()
        return true
    }

    internal suspend fun <T> withLease(
        key: GpuRenderSessionKey,
        block: suspend (GpuPreparedSceneSessionPort) -> T,
    ): T? {
        val entry = synchronized(this) { entries[key] } ?: return null
        return entry.lock.withLock {
            val valid = synchronized(this) {
                !closed &&
                    invalidatingGenerations.isEmpty() &&
                    key.deviceGeneration !in invalidatedGenerations &&
                    entries[key] === entry &&
                    snapshot?.deviceGeneration == key.deviceGeneration
            }
            if (valid) block(entry.session) else null
        }
    }

    public fun invalidateDeviceGeneration(deviceGeneration: GPUDeviceGenerationID) {
        val stale = synchronized(this) {
            if (closed || !invalidatedGenerations.add(deviceGeneration.value)) return
            invalidatingGenerations += deviceGeneration.value
            entries.filterKeys { it.deviceGeneration == deviceGeneration.value }.values.toList().also {
                entries.keys.removeIf { key -> key.deviceGeneration == deviceGeneration.value }
            }.also {
                if (snapshot?.deviceGeneration == deviceGeneration.value) {
                    backend = null
                    snapshot = null
                }
            }
        }
        try {
            closeEntriesBestEffort(stale)
        } finally {
            try {
                runCatching { runtimeOwner.disposeGeneration(deviceGeneration) }
            } finally {
                synchronized(this) { invalidatingGenerations -= deviceGeneration.value }
            }
        }
    }

    override fun close() {
        val workersToDrain: List<Job>
        val sessionsToClose: List<Entry>
        synchronized(this) {
            if (closed) return
            closed = true
            workersToDrain = workers.toList()
            sessionsToClose = entries.values.toList()
            entries.clear()
            backend = null
            snapshot = null
        }
        try {
            runBlocking { workersToDrain.joinAll() }
        } finally {
            try {
                closeEntriesBestEffort(sessionsToClose)
            } finally {
                try {
                    runCatching { runtimeOwner.close() }
                } finally {
                    workerScope.cancel()
                }
            }
        }
    }

    private fun closeEntriesBestEffort(entries: List<Entry>) {
        entries.forEach { entry ->
            runCatching {
                runBlocking { entry.lock.withLock { entry.session.close() } }
            }
        }
    }

    private fun closeSessionBestEffort(session: GpuPreparedSceneSessionPort) {
        runCatching { session.close() }
    }
}
