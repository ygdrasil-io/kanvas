package org.graphiks.kanvas.gpu.renderer.planning

import java.util.concurrent.atomic.AtomicBoolean
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
import org.graphiks.kanvas.gpu.plan.CapabilityCompilerChain
import org.graphiks.kanvas.gpu.plan.PlanLogicalColorFormat
import org.graphiks.kanvas.gpu.plan.RenderGraph
import org.graphiks.kanvas.gpu.plan.W3SolidRectPlanCompiler
import org.graphiks.kanvas.gpu.plan.W4aAnalyticRectPlanCompiler
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.execution.GPUOffscreenTargetRequest
import org.graphiks.kanvas.render.ir.RenderDiagnostic
import org.graphiks.kanvas.render.ir.RenderDiagnosticCode
import org.graphiks.kanvas.render.ir.RenderDiagnosticDomain
import org.graphiks.kanvas.render.ir.RenderDiagnosticSeverity
import org.graphiks.kanvas.render.ir.RenderExecutionResult
import org.graphiks.kanvas.render.ir.RenderPlanResult
import org.graphiks.kanvas.render.ir.RenderTargetDescriptor
import org.graphiks.kanvas.render.ir.SceneSnapshot

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
    data class Ready(val reservation: GpuPreparedSessionReservation) : GpuPreparedSessionAcquisition

    data class GenerationMismatch(
        val expectedGeneration: GPUDeviceGenerationID,
        val actualGeneration: GPUDeviceGenerationID,
    ) : GpuPreparedSessionAcquisition

    data object Unavailable : GpuPreparedSessionAcquisition
}

/** Opaque, one-shot ownership of a prepared session acquired for one frame. */
internal interface GpuPreparedSessionReservation

/** Physical-capability acquisition kept distinct from semantic GPU plan selection. */
internal sealed interface GpuPlanningCapabilityAcquisition {
    data class Ready(val snapshot: PlanCapabilitySnapshot) : GpuPlanningCapabilityAcquisition
    data class Unsupported(val diagnostic: RenderDiagnostic) : GpuPlanningCapabilityAcquisition
    data object Unavailable : GpuPlanningCapabilityAcquisition
}

/** Process-scoped owner for the runtime, its submission workers, and reusable prepared targets. */
public class GpuRenderContext internal constructor(
    private val runtimeOwner: GpuBackendRuntimeOwnerPort = DefaultGpuBackendRuntimeOwner(),
    internal val completionAwaiter: GpuCompletionAwaiter = DefaultGpuCompletionAwaiter,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : AutoCloseable {
    public companion object {
        /** Creates the production context without exposing its injectable runtime ports. */
        public fun createProduction(): GpuRenderContext = GpuRenderContext()

        private const val MAX_PREPARED_SESSIONS: Int = 8
    }

    /** Creates a plan facade whose API does not leak the internal gpu-plan module. */
    public fun planSurfaceExecutor(): GpuPlanSurfaceExecutor = GpuPlanSurfaceExecutor(this)

    private data class Entry(
        val session: GpuPreparedSceneSessionPort,
        val lock: Mutex = Mutex(),
        var activeLeases: Int = 0,
        var lastAccess: Long = 0L,
    )

    private class Reservation(
        val owner: GpuRenderContext,
        val key: GpuRenderSessionKey,
        val entry: Entry,
        var consumed: Boolean = false,
    ) : GpuPreparedSessionReservation

    private val workerScope = CoroutineScope(SupervisorJob() + workerDispatcher)
    private val workers = linkedSetOf<Job>()
    private var backend: GpuBackendSessionPort? = null
    private var snapshot: PlanCapabilitySnapshot? = null
    private var capabilityFailure: RenderDiagnostic? = null
    private var runtimeEpoch: Long? = null
    private var closed = false
    private val entries = linkedMapOf<GpuRenderSessionKey, Entry>()
    private var lastEntryAccess: Long = 0L
    private val invalidatedGenerations = mutableSetOf<Long>()
    private val invalidatingGenerations = mutableSetOf<Long>()

    internal fun acquirePlanningCapabilities(): GpuPlanningCapabilityAcquisition {
        discardStaleRuntimeEpoch()
        return synchronized(this) {
            if (closed || invalidatingGenerations.isNotEmpty()) {
                return@synchronized GpuPlanningCapabilityAcquisition.Unavailable
            }
            snapshot?.let { return@synchronized GpuPlanningCapabilityAcquisition.Ready(it) }
            capabilityFailure?.let { return@synchronized GpuPlanningCapabilityAcquisition.Unsupported(it) }
            val created = runtimeOwner.createOrNull()
                ?: return@synchronized GpuPlanningCapabilityAcquisition.Unavailable
            if (created.deviceGeneration.value in invalidatedGenerations) {
                runCatching { created.close() }
                return@synchronized GpuPlanningCapabilityAcquisition.Unavailable
            }
            when (val adapted = created.capabilities?.toPlanCapabilitySnapshot(created.deviceGeneration)) {
                is GpuPlanCapabilityAdapterResult.Supported -> {
                    backend = created
                    snapshot = adapted.snapshot
                    GpuPlanningCapabilityAcquisition.Ready(adapted.snapshot)
                }
                is GpuPlanCapabilityAdapterResult.Unsupported -> {
                    runCatching { created.close() }
                    capabilityFailure = adapted.diagnostic
                    GpuPlanningCapabilityAcquisition.Unsupported(adapted.diagnostic)
                }
                null -> {
                    runCatching { created.close() }
                    GpuPlanningCapabilityAcquisition.Unavailable
                }
            }
        }
    }

    internal fun capabilities(): PlanCapabilitySnapshot? =
        (acquirePlanningCapabilities() as? GpuPlanningCapabilityAcquisition.Ready)?.snapshot

    internal fun prepared(key: GpuRenderSessionKey): GpuPreparedSceneSessionPort? =
        (acquirePrepared(key) as? GpuPreparedSessionAcquisition.Ready)?.let { acquired ->
            runBlocking { withLease(acquired.reservation) { session -> session } }
        }

    internal fun acquirePrepared(key: GpuRenderSessionKey): GpuPreparedSessionAcquisition {
        discardStaleRuntimeEpoch()
        var mismatchedSession: GpuPreparedSceneSessionPort? = null
        var evictedEntry: Entry? = null
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
                return@synchronized reserve(key, entry)
            }
            val owner = backend ?: return@synchronized GpuPreparedSessionAcquisition.Unavailable
            val evicted = if (entries.size >= MAX_PREPARED_SESSIONS) {
                entries.entries
                    .asSequence()
                    .filter { (_, entry) -> entry.activeLeases == 0 }
                    .minByOrNull { (_, entry) -> entry.lastAccess }
                    ?: return@synchronized GpuPreparedSessionAcquisition.Unavailable
            } else {
                null
            }
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
                evicted?.let { (evictedKey, entry) ->
                    entries.remove(evictedKey)
                    evictedEntry = entry
                }
                val entry = Entry(session)
                entries[key] = entry
                reserve(key, entry)
            }
        }
        mismatchedSession?.let(::closeSessionBestEffort)
        evictedEntry?.let { entry -> closeEntriesBestEffort(listOf(entry)) }
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
        reservation: GpuPreparedSessionReservation,
        block: suspend (GpuPreparedSceneSessionPort) -> T,
    ): T? {
        val lease = reservation as? Reservation ?: return null
        val entry = synchronized(this) {
            if (lease.owner !== this || lease.consumed) null else lease.entry.also { lease.consumed = true }
        } ?: return null
        return try {
            entry.lock.withLock {
                val valid = synchronized(this) {
                    !closed &&
                        invalidatingGenerations.isEmpty() &&
                        lease.key.deviceGeneration !in invalidatedGenerations &&
                        entries[lease.key] === entry &&
                        snapshot?.deviceGeneration == lease.key.deviceGeneration
                }
                if (valid) block(entry.session) else null
            }
        } finally {
            synchronized(this) {
                check(entry.activeLeases > 0) { "Prepared-session lease count underflow" }
                entry.activeLeases -= 1
                if (entries[lease.key] === entry) touch(entry)
            }
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
                    capabilityFailure = null
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
            capabilityFailure = null
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

    /** Reserves the exact entry while it is still protected by the context monitor. */
    private fun reserve(
        key: GpuRenderSessionKey,
        entry: Entry,
    ): GpuPreparedSessionAcquisition.Ready {
        entry.activeLeases += 1
        touch(entry)
        return GpuPreparedSessionAcquisition.Ready(Reservation(this, key, entry))
    }

    /** Records access under this context monitor, preserving an idle-only LRU eviction order. */
    private fun touch(entry: Entry) {
        if (lastEntryAccess == Long.MAX_VALUE) {
            entries.values
                .sortedBy(Entry::lastAccess)
                .forEachIndexed { index, cached -> cached.lastAccess = index.toLong() + 1L }
            lastEntryAccess = entries.size.toLong()
        }
        lastEntryAccess += 1L
        entry.lastAccess = lastEntryAccess
    }

    /** Raw factory disposal has no callback into this context, so observe its epoch at boundaries. */
    private fun discardStaleRuntimeEpoch() {
        val current = try {
            runtimeOwner.lifecycleEpoch()
        } catch (_: Throwable) {
            return
        }
        val stale = synchronized(this) {
            when (val known = runtimeEpoch) {
                null -> {
                    runtimeEpoch = current
                    emptyList()
                }
                current -> emptyList()
                else -> {
                    runtimeEpoch = current
                    val entriesToClose = entries.values.toList()
                    entries.clear()
                    backend = null
                    snapshot = null
                    capabilityFailure = null
                    entriesToClose
                }
            }
        }
        closeEntriesBestEffort(stale)
    }
}

/** Opaque, handle-free proof that a GPU plan frame was planned and may be submitted once. */
public interface GpuPlanSurfaceReadyToken

/** Typed first phase of a GPU plan Surface attempt. */
public sealed interface GpuPlanSurfacePlanResult {
    public data class Ready(public val token: GpuPlanSurfaceReadyToken) : GpuPlanSurfacePlanResult
    public data class GapNotMigrated(public val diagnostics: List<RenderDiagnostic>) : GpuPlanSurfacePlanResult
    public data class Terminal(public val diagnostics: List<RenderDiagnostic>) : GpuPlanSurfacePlanResult
}

/** Typed completion of submitting an already-ready GPU plan Surface token. */
public sealed interface GpuPlanSurfaceSubmitResult {
    public data class Completed(public val output: GpuFrameOutput) : GpuPlanSurfaceSubmitResult
    public data class Terminal(public val diagnostics: List<RenderDiagnostic>) : GpuPlanSurfaceSubmitResult
}

/** Public production facade over GPU plan planning/submission that keeps RenderGraph module-private. */
public class GpuPlanSurfaceExecutor internal constructor(
    private val context: GpuRenderContext,
) {
    private class ReadyToken(
        val context: GpuRenderContext,
        val backend: GpuRenderBackend,
        val graph: RenderGraph,
        private val submitted: AtomicBoolean = AtomicBoolean(false),
    ) : GpuPlanSurfaceReadyToken {
        fun claim(): Boolean = submitted.compareAndSet(false, true)
    }

    public fun plan(
        scene: SceneSnapshot,
        target: RenderTargetDescriptor,
        frameLocalBudgetBytes: Long,
    ): GpuPlanSurfacePlanResult {
        val backend = GpuRenderBackend(
            compiler = CapabilityCompilerChain.of(
                listOf(W3SolidRectPlanCompiler(), W4aAnalyticRectPlanCompiler()),
            ),
            context = context,
            targetConfig = GpuRenderTargetConfig(target.extent, target.colorSpace, frameLocalBudgetBytes),
        )
        return when (val result = backend.plan(scene, target)) {
            is RenderPlanResult.Ready -> GpuPlanSurfacePlanResult.Ready(ReadyToken(context, backend, result.plan))
            is RenderPlanResult.GapNotMigrated -> GpuPlanSurfacePlanResult.GapNotMigrated(result.diagnostics)
            is RenderPlanResult.GapOnPromotedScope -> GpuPlanSurfacePlanResult.Terminal(result.diagnostics)
            is RenderPlanResult.InvalidScene -> GpuPlanSurfacePlanResult.Terminal(result.diagnostics)
            is RenderPlanResult.ResourceLimitExceeded -> GpuPlanSurfacePlanResult.Terminal(result.diagnostics)
        }
    }

    public fun submit(token: GpuPlanSurfaceReadyToken): GpuPlanSurfaceSubmitResult {
        val ready = token as? ReadyToken
            ?: return invalidReadyToken("GPU plan submission requires a ready token issued by this facade.")
        if (ready.context !== context || !ready.claim()) {
            return invalidReadyToken("GPU plan ready token is stale, foreign, or already submitted.")
        }
        return when (val execution = runBlocking { ready.backend.submit(ready.graph).await() }) {
            is RenderExecutionResult.Completed -> GpuPlanSurfaceSubmitResult.Completed(execution.output)
            is RenderExecutionResult.UnsupportedCapability -> GpuPlanSurfaceSubmitResult.Terminal(execution.diagnostics)
            is RenderExecutionResult.InvalidPlan -> GpuPlanSurfaceSubmitResult.Terminal(execution.diagnostics)
            is RenderExecutionResult.ResourceLimitExceeded -> GpuPlanSurfaceSubmitResult.Terminal(execution.diagnostics)
            is RenderExecutionResult.DeviceFailure -> GpuPlanSurfaceSubmitResult.Terminal(execution.diagnostics)
        }
    }

    private fun invalidReadyToken(message: String): GpuPlanSurfaceSubmitResult.Terminal =
        GpuPlanSurfaceSubmitResult.Terminal(
            listOf(
                RenderDiagnostic(
                    RenderDiagnosticCode("w3.lowering.incompatible_plan"),
                    RenderDiagnosticDomain.RESOURCE,
                    RenderDiagnosticSeverity.ERROR,
                    message,
                ),
            ),
        )
}
