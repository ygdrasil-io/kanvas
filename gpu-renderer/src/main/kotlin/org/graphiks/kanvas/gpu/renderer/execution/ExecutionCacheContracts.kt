package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.renderer.telemetry.GPUCacheEventResult
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUCacheTelemetryEvent

/** Execution cache domain whose entries are owned by the resource/execution boundary. */
enum class GPUExecutionCacheDomain(val telemetryDomain: String) {
    /** Parser-validated WGSL module cache. */
    Module("module"),
    /** WGPU render-pipeline cache. */
    RenderPipeline("pipeline"),
    /** WGPU pipeline-layout cache. */
    PipelineLayout("pipeline-layout"),
    /** WGPU bind-group-layout cache. */
    BindGroupLayout("bind-group-layout"),
}

/**
 * Request to materialize or reuse one execution cache entry.
 *
 * The request contains stable key and subject hashes plus device-generation
 * facts. It does not expose or accept backend handles. The producer callback
 * in [GPUExecutionObjectCache.getOrCreate] owns the actual facade creation
 * event.
 */
data class GPUExecutionCacheRequest(
    val domain: GPUExecutionCacheDomain,
    val keyHash: String,
    val subjectHash: String,
    val deviceGeneration: GPUDeviceGeneration,
    val expectedDeviceGeneration: GPUDeviceGeneration,
    val ownerScope: String,
    val releaseBlocking: Boolean = false,
    val productRouteActivated: Boolean = false,
) {
    init {
        require(keyHash.isNotBlank()) { "GPUExecutionCacheRequest.keyHash must not be blank" }
        require(subjectHash.isNotBlank()) { "GPUExecutionCacheRequest.subjectHash must not be blank" }
        require(ownerScope.isNotBlank()) { "GPUExecutionCacheRequest.ownerScope must not be blank" }
        require(!releaseBlocking) { "GPUExecutionCacheRequest.releaseBlocking must stay false" }
        require(!productRouteActivated) {
            "GPUExecutionCacheRequest.productRouteActivated must stay false"
        }
    }
}

/** Result of one execution-cache materialization attempt. */
sealed interface GPUExecutionCacheDecision<out T : Any> {
    /** Telemetry events emitted by this cache decision. */
    val cacheEvents: List<GPUCacheTelemetryEvent>

    /** Stable dump lines that intentionally omit backend object handles. */
    fun dumpLines(): List<String>

    /** Cache lookup returned or created a provider-owned backend object. */
    data class Ready<T : Any>(
        val request: GPUExecutionCacheRequest,
        val handle: T,
        override val cacheEvents: List<GPUCacheTelemetryEvent>,
    ) : GPUExecutionCacheDecision<T> {
        override fun dumpLines(): List<String> =
            cacheEvents.map { event -> request.dumpLine(event.result) }
    }

    /** Cache lookup refused before returning a backend object. */
    data class Refused(
        val request: GPUExecutionCacheRequest,
        val diagnosticCode: String,
        override val cacheEvents: List<GPUCacheTelemetryEvent>,
    ) : GPUExecutionCacheDecision<Nothing> {
        init {
            require(diagnosticCode.isNotBlank()) {
                "GPUExecutionCacheDecision.Refused.diagnosticCode must not be blank"
            }
        }

        override fun dumpLines(): List<String> =
            cacheEvents.map { event -> request.dumpLine(event.result) } + listOf(
                "execution.cache:refused domain=${request.domain.telemetryDomain} " +
                    "key=${request.keyHash} subject=${request.subjectHash} " +
                    "deviceGeneration=${request.deviceGeneration.value} " +
                    "owner=${request.ownerScope} code=$diagnosticCode " +
                    "releaseBlocking=${request.releaseBlocking} " +
                    "productRouteActivated=${request.productRouteActivated}",
            )
    }

    /** Cache entry was evicted. */
    data class Evicted(
        val request: GPUExecutionCacheRequest,
        override val cacheEvents: List<GPUCacheTelemetryEvent>,
    ) : GPUExecutionCacheDecision<Nothing> {
        override fun dumpLines(): List<String> =
            cacheEvents.map { event -> request.dumpLine(event.result) }
    }
}

/**
 * Small provider-owned object cache for execution materialization.
 *
 * The cache stores live objects but never includes their identity in telemetry
 * or dumps. It is intentionally keyed by stable string facts and device
 * generation, leaving route support and submission decisions to callers.
 */
class GPUExecutionObjectCache<T : Any>(
    private val domain: GPUExecutionCacheDomain,
    private val dispose: (T) -> Unit = {},
) : AutoCloseable {
    private val entries = linkedMapOf<CacheEntryKey, T>()

    /** Gets or creates one cache entry with deterministic telemetry. */
    fun getOrCreate(
        request: GPUExecutionCacheRequest,
        create: () -> T,
    ): GPUExecutionCacheDecision<T> {
        require(request.domain == domain) {
            "GPUExecutionObjectCache domain ${domain.telemetryDomain} cannot serve ${request.domain.telemetryDomain}"
        }

        if (request.deviceGeneration != request.expectedDeviceGeneration) {
            return GPUExecutionCacheDecision.Refused(
                request = request,
                diagnosticCode = "unsupported.execution.cache_device_generation_stale",
                cacheEvents = listOf(request.cacheEvent(GPUCacheEventResult.StaleGeneration)),
            )
        }

        val entryKey = request.entryKey()
        val cached = entries[entryKey]
        if (cached != null) {
            return GPUExecutionCacheDecision.Ready(
                request = request,
                handle = cached,
                cacheEvents = listOf(request.cacheEvent(GPUCacheEventResult.Hit)),
            )
        }

        return try {
            val handle = create()
            entries[entryKey] = handle
            GPUExecutionCacheDecision.Ready(
                request = request,
                handle = handle,
                cacheEvents = listOf(
                    request.cacheEvent(GPUCacheEventResult.Miss),
                    request.cacheEvent(GPUCacheEventResult.Create),
                ),
            )
        } catch (_: Throwable) {
            GPUExecutionCacheDecision.Refused(
                request = request,
                diagnosticCode = "unsupported.execution.cache_create_failed",
                cacheEvents = listOf(
                    request.cacheEvent(GPUCacheEventResult.Miss),
                    request.cacheEvent(GPUCacheEventResult.Failure),
                ),
            )
        }
    }

    /** Evicts one cache entry and reports the invalidation attempt. */
    fun evict(request: GPUExecutionCacheRequest): GPUExecutionCacheDecision.Evicted {
        require(request.domain == domain) {
            "GPUExecutionObjectCache domain ${domain.telemetryDomain} cannot evict ${request.domain.telemetryDomain}"
        }
        entries.remove(request.entryKey())?.let(::disposeEntry)
        return GPUExecutionCacheDecision.Evicted(
            request = request,
            cacheEvents = listOf(request.cacheEvent(GPUCacheEventResult.Evict)),
        )
    }

    override fun close() {
        var firstFailure: Throwable? = null
        entries.values.toList().asReversed().forEach { handle ->
            try {
                disposeEntry(handle)
            } catch (failure: Throwable) {
                if (firstFailure == null) {
                    firstFailure = failure
                } else {
                    firstFailure.addSuppressed(failure)
                }
            }
        }
        entries.clear()
        firstFailure?.let { throw it }
    }

    private fun disposeEntry(handle: T) {
        dispose(handle)
    }

    private data class CacheEntryKey(
        val keyHash: String,
        val subjectHash: String,
        val deviceGeneration: GPUDeviceGeneration,
    )

    private fun GPUExecutionCacheRequest.entryKey(): CacheEntryKey =
        CacheEntryKey(
            keyHash = keyHash,
            subjectHash = subjectHash,
            deviceGeneration = deviceGeneration,
        )
}

private fun GPUExecutionCacheRequest.cacheEvent(result: GPUCacheEventResult): GPUCacheTelemetryEvent =
    GPUCacheTelemetryEvent(
        domain = domain.telemetryDomain,
        result = result,
        keyHash = keyHash,
        subjectHash = subjectHash,
    )

private fun GPUExecutionCacheRequest.dumpLine(result: GPUCacheEventResult): String =
    "execution.cache domain=${domain.telemetryDomain} " +
        "result=${result.dumpToken()} " +
        "key=$keyHash subject=$subjectHash " +
        "deviceGeneration=${deviceGeneration.value} " +
        "owner=$ownerScope " +
        "releaseBlocking=$releaseBlocking " +
        "productRouteActivated=$productRouteActivated"

private fun GPUCacheEventResult.dumpToken(): String =
    when (this) {
        GPUCacheEventResult.Hit -> "hit"
        GPUCacheEventResult.Miss -> "miss"
        GPUCacheEventResult.Create -> "create"
        GPUCacheEventResult.Failure -> "failure"
        GPUCacheEventResult.StaleGeneration -> "stale-generation"
        GPUCacheEventResult.Evict -> "evict"
    }
