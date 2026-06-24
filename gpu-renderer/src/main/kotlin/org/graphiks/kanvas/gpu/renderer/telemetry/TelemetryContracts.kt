package org.graphiks.kanvas.gpu.renderer.telemetry

/** Counter observed by GPU renderer telemetry. */
data class GPUTelemetryCounter(
    val name: String,
    val value: Long,
    val unit: String,
    val scope: String,
)

/** Cache lookup event result observed by telemetry. */
enum class GPUCacheEventResult {
    /** Cache lookup found an existing entry. */
    Hit,
    /** Cache lookup did not find an existing entry. */
    Miss,
    /** Cache materialization created a backend-owned entry. */
    Create,
    /** Cache materialization failed before an entry could be stored. */
    Failure,
    /** Cache lookup refused because device-generation facts were stale. */
    StaleGeneration,
    /** Cache entry was evicted by policy or explicit invalidation. */
    Evict,
}

/**
 * Deterministic cache hit or miss event fact.
 *
 * Cache telemetry is observational only: it records material, module,
 * pipeline, pipeline-layout, and bind-group-layout cache behavior after
 * another package has made support decisions.
 * Domains are intentionally closed to canonical cache classes, and key facts
 * must be stable non-blank hashes rather than route decisions or backend
 * object identities.
 */
data class GPUCacheTelemetryEvent(
    val domain: String,
    val result: GPUCacheEventResult,
    val keyHash: String,
    val subjectHash: String,
) {
    init {
        require(domain in canonicalCacheDomains) { "GPU cache telemetry domain must be one of $canonicalCacheDomains" }
        require(keyHash.isNotBlank()) { "GPU cache telemetry keyHash must not be blank" }
        require(subjectHash.isNotBlank()) { "GPU cache telemetry subjectHash must not be blank" }
    }

    /** Factory helpers for canonical cache domains. */
    companion object {
        /** Creates a material cache event. */
        fun material(result: GPUCacheEventResult, keyHash: String, subjectHash: String): GPUCacheTelemetryEvent =
            GPUCacheTelemetryEvent(
                domain = "material",
                result = result,
                keyHash = keyHash,
                subjectHash = subjectHash,
            )

        /** Creates a module cache event. */
        fun module(result: GPUCacheEventResult, keyHash: String, subjectHash: String): GPUCacheTelemetryEvent =
            GPUCacheTelemetryEvent(
                domain = "module",
                result = result,
                keyHash = keyHash,
                subjectHash = subjectHash,
            )

        /** Creates a pipeline cache event. */
        fun pipeline(result: GPUCacheEventResult, keyHash: String, subjectHash: String): GPUCacheTelemetryEvent =
            GPUCacheTelemetryEvent(
                domain = "pipeline",
                result = result,
                keyHash = keyHash,
                subjectHash = subjectHash,
            )

        /** Creates a bind-group-layout cache event. */
        fun bindGroupLayout(
            result: GPUCacheEventResult,
            keyHash: String,
            subjectHash: String,
        ): GPUCacheTelemetryEvent =
            GPUCacheTelemetryEvent(
                domain = "bind-group-layout",
                result = result,
                keyHash = keyHash,
                subjectHash = subjectHash,
            )

        /** Creates a pipeline-layout cache event. */
        fun pipelineLayout(
            result: GPUCacheEventResult,
            keyHash: String,
            subjectHash: String,
        ): GPUCacheTelemetryEvent =
            GPUCacheTelemetryEvent(
                domain = "pipeline-layout",
                result = result,
                keyHash = keyHash,
                subjectHash = subjectHash,
            )
    }
}

/** Source classification for cache telemetry evidence. */
enum class GPUCacheTelemetrySourceClassification(val label: String) {
    /** Fully observed counter from a named runtime artifact with required fields present. */
    Observed("observed"),
    /** Runtime artifact exists but does not contain every required counter field. */
    ObservedPartial("observed-partial"),
    /** Counter is derived from non-runtime evidence such as reports, comments, or synthetic ledgers. */
    Derived("derived"),
    /** Required runtime evidence is missing or cannot be trusted. */
    Unavailable("unavailable"),
    /** Counter is intentionally reporting-only and cannot move readiness. */
    ReportingOnly("reporting-only"),
}

/** Request to classify one cache telemetry counter against its named source. */
data class GPUCacheTelemetrySourceMapRequest(
    val counterName: String,
    val cacheDomain: String,
    val sourceArtifactLabel: String,
    val sourceKind: String,
    val sourceHash: String? = null,
    val requiredFields: Set<String> = emptySet(),
    val observedFields: Set<String> = emptySet(),
    val derivedFrom: List<String> = emptyList(),
) {
    init {
        require(counterName.isNotBlank()) { "GPU cache telemetry source-map counterName must not be blank" }
        require(cacheDomain.isNotBlank()) { "GPU cache telemetry source-map cacheDomain must not be blank" }
        require(sourceArtifactLabel.isNotBlank()) {
            "GPU cache telemetry source-map sourceArtifactLabel must not be blank"
        }
        require(sourceKind.isNotBlank()) { "GPU cache telemetry source-map sourceKind must not be blank" }
    }
}

/** Classified source-map entry for one cache telemetry counter. */
data class GPUCacheTelemetrySourceMapEntry(
    val counterName: String,
    val cacheDomain: String,
    val classification: GPUCacheTelemetrySourceClassification,
    val sourceArtifactLabel: String,
    val sourceKind: String,
    val sourceHash: String?,
    val requiredFields: Set<String>,
    val observedFields: Set<String>,
    val derivedFrom: List<String>,
    val countsForObservedReadiness: Boolean,
) {
    /** Returns one canonical source-map dump line. */
    fun dumpLine(): String =
        "cache-source counter=$counterName domain=$cacheDomain classification=${classification.label} " +
            "source=$sourceArtifactLabel kind=$sourceKind hash=${sourceHash ?: "none"} " +
            "fields=${observedFields.stableFieldList()} required=${requiredFields.stableFieldList()} " +
            "countsObserved=$countsForObservedReadiness"
}

/** Cache telemetry source-map report with non-promotional readiness fields. */
data class GPUCacheTelemetrySourceMapReport(
    val mapId: String,
    val entries: List<GPUCacheTelemetrySourceMapEntry>,
    val readinessDelta: Double = 0.0,
    val releaseBlocking: Boolean = false,
    val productRouteActivated: Boolean = false,
) {
    init {
        require(mapId.isNotBlank()) { "GPU cache telemetry source-map mapId must not be blank" }
    }

    /** Returns counters that are backed by complete observed runtime artifacts. */
    fun observedReadinessCounters(): List<String> =
        entries
            .filter { entry -> entry.countsForObservedReadiness }
            .map { entry -> entry.counterName }

    /** Returns canonical report lines for PM evidence and tests. */
    fun dumpLines(): List<String> {
        val counts = entries.groupingBy { entry -> entry.classification }.eachCount()

        return listOf(
            "cache-source-map id=$mapId entries=${entries.size} readinessDelta=$readinessDelta " +
                "releaseBlocking=$releaseBlocking productRouteActivated=$productRouteActivated",
        ) + entries.map { entry -> entry.dumpLine() } + listOf(
            "pm:gpu-renderer.cache-telemetry-source-map classification=PolicyGated " +
                "observed=${counts[GPUCacheTelemetrySourceClassification.Observed] ?: 0} " +
                "observedPartial=${counts[GPUCacheTelemetrySourceClassification.ObservedPartial] ?: 0} " +
                "derived=${counts[GPUCacheTelemetrySourceClassification.Derived] ?: 0} " +
                "unavailable=${counts[GPUCacheTelemetrySourceClassification.Unavailable] ?: 0} " +
                "reportingOnly=${counts[GPUCacheTelemetrySourceClassification.ReportingOnly] ?: 0} " +
                "readinessDelta=$readinessDelta releaseBlocking=$releaseBlocking",
            "nonclaim:no-release-blocking-gate no-readiness-delta no-product-activation " +
                "no-derived-as-observed no-synthetic-comment-counters",
        )
    }
}

/** Builds non-promotional source maps for cache telemetry counters. */
object GPUCacheTelemetrySourceMapper {
    /** Classifies every requested counter into observed, partial, derived, unavailable, or reporting-only. */
    fun map(
        mapId: String,
        requests: List<GPUCacheTelemetrySourceMapRequest>,
    ): GPUCacheTelemetrySourceMapReport =
        GPUCacheTelemetrySourceMapReport(
            mapId = mapId,
            entries = requests.map { request -> classify(request) },
        )

    private fun classify(request: GPUCacheTelemetrySourceMapRequest): GPUCacheTelemetrySourceMapEntry {
        val normalizedSourceKind = request.sourceKind.lowercase()
        val classification = classificationFor(request, normalizedSourceKind)

        return GPUCacheTelemetrySourceMapEntry(
            counterName = request.counterName,
            cacheDomain = request.cacheDomain,
            classification = classification,
            sourceArtifactLabel = request.sourceArtifactLabel,
            sourceKind = normalizedSourceKind,
            sourceHash = request.sourceHash,
            requiredFields = request.requiredFields,
            observedFields = request.observedFields,
            derivedFrom = request.derivedFrom,
            countsForObservedReadiness = classification == GPUCacheTelemetrySourceClassification.Observed,
        )
    }

    private fun classificationFor(
        request: GPUCacheTelemetrySourceMapRequest,
        normalizedSourceKind: String,
    ): GPUCacheTelemetrySourceClassification =
        when (normalizedSourceKind) {
            in observedArtifactKinds -> classifyObservedArtifact(request)
            in derivedSourceKinds -> GPUCacheTelemetrySourceClassification.Derived
            "reporting-only" -> GPUCacheTelemetrySourceClassification.ReportingOnly
            else -> GPUCacheTelemetrySourceClassification.Unavailable
        }

    private fun classifyObservedArtifact(
        request: GPUCacheTelemetrySourceMapRequest,
    ): GPUCacheTelemetrySourceClassification =
        when {
            request.sourceHash.isNullOrBlank() -> GPUCacheTelemetrySourceClassification.Unavailable
            request.requiredFields.all { field -> field in request.observedFields } ->
                GPUCacheTelemetrySourceClassification.Observed
            request.observedFields.isNotEmpty() -> GPUCacheTelemetrySourceClassification.ObservedPartial
            else -> GPUCacheTelemetrySourceClassification.Unavailable
        }

    private val observedArtifactKinds = setOf("adapter-runtime-artifact", "executed-pm-artifact")
    private val derivedSourceKinds = setOf("comment", "report-text", "synthetic-ledger")
}

/**
 * Closed R6 first-route counter domains owned by telemetry.
 *
 * Each enum entry maps to one stable `GPUTelemetryCounter.name` used by PM
 * evidence dumps. These domains are observational buckets only: they do not
 * accept, promote, or refuse renderer routes, and new domains require an
 * explicit API change instead of ad hoc string names.
 */
enum class GPUFirstRouteCounterDomain(val counterName: String) {
    /** Counts normalized draw commands by closed command family. */
    CommandFamily("first_route.command.count"),
    /** Counts route decisions by route kind after routing has decided. */
    RouteKind("first_route.route.count"),
    /** Counts visible route refusals by stable diagnostic code. */
    RouteRefusal("first_route.route.refusal.count"),
    /** Counts WGSL module validation outcomes after validation has run. */
    WGSLModuleValidation("first_route.wgsl_module_validation.count"),
    /** Counts resource materialization outcomes after resources have decided. */
    ResourceMaterialization("first_route.resource_materialization.count"),
    /** Counts command submission outcomes after execution has decided. */
    CommandSubmission("first_route.command_submission.count"),
    /** Counts explicit refusals of hidden CPU-rendered fallback. */
    NegativeCPUFallbackRefusal("first_route.negative_cpu_fallback.refusal.count"),
}

/**
 * Closed first-route command families reported by telemetry.
 *
 * The set mirrors the command-family buckets needed for R6 ledgers without
 * importing command-package types into telemetry. Values are counter labels,
 * not route support claims.
 */
enum class GPUFirstRouteCommandFamily {
    /** Rectangle draw family. */
    Rect,
    /** Rounded-rectangle draw family. */
    RRect,
    /** Path draw family. */
    Path,
    /** Text draw family. */
    Text,
    /** Image draw family. */
    Image,
    /** Vertices draw family. */
    Vertices,
}

/**
 * Closed route-kind buckets reported by telemetry after routing decides.
 *
 * These labels are counters for PM evidence. Recording `GPUNative` here means
 * only that routing reported that kind; it does not promote support or invent
 * backend success.
 */
enum class GPUFirstRouteRouteKind {
    /** Fully GPU-native route kind reported by routing. */
    GPUNative,
    /** CPU-prepared typed artifact consumed by GPU work. */
    CPUPreparedGPU,
    /** CPU reference-only evidence route, not product fallback. */
    CPUReferenceOnly,
    /** Terminal visible refusal diagnostic. */
    RefuseDiagnostic,
}

/**
 * Closed WGSL module validation outcomes observed by telemetry.
 *
 * `Success` and `Failure` count validation results only. They do not imply
 * route support, parser availability beyond the validation result, or backend
 * shader creation.
 */
enum class GPUFirstRouteWGSLModuleValidationOutcome {
    /** WGSL validation accepted the module facts. */
    Success,
    /** WGSL validation rejected the module facts. */
    Failure,
}

/**
 * Closed resource materialization outcomes observed by telemetry.
 *
 * The resource package owns materialization decisions. Telemetry records the
 * resulting bucket and never creates, substitutes, or retries resources.
 */
enum class GPUFirstRouteResourceMaterializationOutcome {
    /** Resource materialization produced typed resource references. */
    Materialized,
    /** Resource materialization was explicitly deferred. */
    Deferred,
    /** Resource materialization was refused with diagnostics. */
    Refused,
}

/**
 * Closed command submission outcomes observed by telemetry.
 *
 * The execution package owns submission behavior. Telemetry counts the outcome
 * after execution decides and must not turn refused or failed submissions into
 * fake GPU success.
 */
enum class GPUFirstRouteCommandSubmissionOutcome {
    /** Commands were submitted by the execution layer. */
    Submitted,
    /** Submission failed after execution attempted it. */
    Failed,
    /** Submission was refused before backend work. */
    Refused,
}

/**
 * First-route telemetry event that can be folded into deterministic counters.
 *
 * Events are immutable observations from other packages. Constructors validate
 * positive counts and stable refusal-code tokens; invalid facts throw
 * `IllegalArgumentException` before a misleading counter can be recorded.
 */
sealed interface GPUFirstRouteTelemetryEvent {
    /** Number of observed events represented by this fact. */
    val count: Long

    /**
     * Command-family counter event.
     *
     * `count` must be positive. The family label is closed by
     * `GPUFirstRouteCommandFamily`, so this event cannot introduce new command
     * support categories at runtime.
     */
    data class CommandFamily(
        val family: GPUFirstRouteCommandFamily,
        override val count: Long = 1L,
    ) : GPUFirstRouteTelemetryEvent {
        init {
            requirePositiveTelemetryCount(count)
        }
    }

    /**
     * Route-kind counter event with optional refusal-code detail.
     *
     * `count` must be positive. `refusalCode` is required only for
     * `RefuseDiagnostic`, forbidden for all other route kinds, and must be a
     * stable non-blank token without whitespace or `:` separators.
     */
    data class Route(
        val kind: GPUFirstRouteRouteKind,
        val refusalCode: String? = null,
        override val count: Long = 1L,
    ) : GPUFirstRouteTelemetryEvent {
        init {
            requirePositiveTelemetryCount(count)
            if (kind == GPUFirstRouteRouteKind.RefuseDiagnostic) {
                requireStableTelemetryToken("refusalCode", refusalCode)
            } else {
                require(refusalCode == null) {
                    "GPU first-route telemetry refusalCode is only valid for RefuseDiagnostic routes"
                }
            }
        }
    }

    /**
     * WGSL module validation counter event.
     *
     * `count` must be positive. The outcome is a validation observation only
     * and does not claim that a pipeline or backend shader was created.
     */
    data class WGSLModuleValidation(
        val outcome: GPUFirstRouteWGSLModuleValidationOutcome,
        override val count: Long = 1L,
    ) : GPUFirstRouteTelemetryEvent {
        init {
            requirePositiveTelemetryCount(count)
        }
    }

    /**
     * Resource materialization counter event.
     *
     * `count` must be positive. The outcome records a resource-layer decision
     * and does not materialize resources from telemetry.
     */
    data class ResourceMaterialization(
        val outcome: GPUFirstRouteResourceMaterializationOutcome,
        override val count: Long = 1L,
    ) : GPUFirstRouteTelemetryEvent {
        init {
            requirePositiveTelemetryCount(count)
        }
    }

    /**
     * Command submission counter event.
     *
     * `count` must be positive. The outcome records an execution-layer result
     * and must not be used to fake backend submission success.
     */
    data class CommandSubmission(
        val outcome: GPUFirstRouteCommandSubmissionOutcome,
        override val count: Long = 1L,
    ) : GPUFirstRouteTelemetryEvent {
        init {
            requirePositiveTelemetryCount(count)
        }
    }

    /**
     * Negative evidence that hidden CPU-rendered fallback stayed refused.
     *
     * Recording this event increments exactly one refusal counter. It does not
     * create a CPU route, CPU artifact, or product fallback success path.
     */
    object NegativeCPUFallbackRefusal : GPUFirstRouteTelemetryEvent {
        override val count: Long = 1L
    }
}

/** Pipeline cache telemetry snapshot for PM evidence. */
data class GPUPipelineCacheTelemetry(
    val sceneId: String,
    val hitCount: Long,
    val missCount: Long,
    val evictionCount: Long,
    val moduleCount: Long,
) {
    init {
        require(sceneId.isNotBlank()) { "GPU pipeline cache telemetry sceneId must not be blank" }
        require(hitCount >= 0L) { "GPU pipeline cache telemetry hitCount must not be negative" }
        require(missCount >= 0L) { "GPU pipeline cache telemetry missCount must not be negative" }
        require(evictionCount >= 0L) { "GPU pipeline cache telemetry evictionCount must not be negative" }
        require(moduleCount >= 0L) { "GPU pipeline cache telemetry moduleCount must not be negative" }
    }

    val hitRate: Double
        get() = if (hitCount + missCount > 0L) hitCount.toDouble() / (hitCount + missCount) else 0.0

    /** Dumps a single-line telemetry snapshot. */
    fun dumpLine(): String =
        "pipeline-cache scene=$sceneId hitCount=$hitCount missCount=$missCount " +
            "hitRate=${"%.4f".format(hitRate)} evictionCount=$evictionCount moduleCount=$moduleCount"
}

/** Cache telemetry facts. */
data class GPUCacheTelemetry(
    val cacheName: String,
    val hits: Long,
    val misses: Long,
    val evictions: Long,
    val residentBytes: Long,
    val pressureBytes: Long,
    val creations: Long = 0L,
    val failures: Long = 0L,
    val staleGenerations: Long = 0L,
)

/** Budget telemetry facts. */
data class GPUBudgetTelemetry(
    val budgetName: String,
    val requested: Long,
    val limit: Long,
    val unit: String,
    val exceeded: Boolean,
)

/** Evidence gathered for feature promotion. */
data class GPUPromotionEvidence(
    val evidenceId: String,
    val routeKindLabel: String,
    val artifactPaths: List<String>,
    val diagnosticCodes: List<String>,
)

/** Performance gate observation, not a route decision. */
data class GPUPerformanceGate(
    val gateName: String,
    val metricName: String,
    val threshold: Double,
    val comparator: String,
    val status: String,
)

/**
 * Telemetry ledger for one renderer scope.
 *
 * The ledger accumulates counters, first-route events, cache events, budgets,
 * and evidence facts without selecting routes or changing support status.
 * Callers must interpret these observations through validation or promotion
 * gates.
 */
data class GPUTelemetryLedger(
    val counters: List<GPUTelemetryCounter>,
    val cacheTelemetry: List<GPUCacheTelemetry>,
    val budgetTelemetry: List<GPUBudgetTelemetry>,
    val promotionEvidence: List<GPUPromotionEvidence>,
    val cacheEvents: List<GPUCacheTelemetryEvent> = emptyList(),
    val pipelineCacheTelemetry: List<GPUPipelineCacheTelemetry> = emptyList(),
) {
    /** Records a cache hit or miss event without changing route support state. */
    fun recordCacheEvent(event: GPUCacheTelemetryEvent): GPUTelemetryLedger {
        val existing = cacheTelemetry.firstOrNull { it.cacheName == event.domain }
            ?: GPUCacheTelemetry(
                cacheName = event.domain,
                hits = 0L,
                misses = 0L,
                evictions = 0L,
                residentBytes = 0L,
                pressureBytes = 0L,
            )
        val updated = when (event.result) {
            GPUCacheEventResult.Hit -> existing.copy(hits = existing.hits + 1)
            GPUCacheEventResult.Miss -> existing.copy(misses = existing.misses + 1)
            GPUCacheEventResult.Create -> existing.copy(creations = existing.creations + 1)
            GPUCacheEventResult.Failure -> existing.copy(failures = existing.failures + 1)
            GPUCacheEventResult.StaleGeneration ->
                existing.copy(staleGenerations = existing.staleGenerations + 1)
            GPUCacheEventResult.Evict -> existing.copy(evictions = existing.evictions + 1)
        }

        return copy(
            cacheEvents = cacheEvents + event,
            cacheTelemetry = cacheTelemetry.filterNot { it.cacheName == event.domain } + updated,
        )
    }

    /**
     * Records a pipeline cache telemetry snapshot for PM evidence.
     *
     * The returned ledger is a new value; existing ledgers are not mutated.
     * Snapshots are appended per scene so historical evidence is preserved.
     */
    fun recordPipelineCacheTelemetry(telemetry: GPUPipelineCacheTelemetry): GPUTelemetryLedger =
        copy(pipelineCacheTelemetry = pipelineCacheTelemetry + telemetry)

    /**
     * Returns pipeline cache dump lines for PM reports and tests.
     */
    fun pipelineCacheTelemetryDumpLines(): List<String> =
        pipelineCacheTelemetry.map { it.dumpLine() }

    /**
     * Records one R6 first-route telemetry observation as deterministic counters.
     *
     * The returned ledger is a new value; existing ledgers are not mutated.
     * Invalid event facts are rejected by the event constructors before this
     * method is called. Counter aggregation preserves first-observed order and
     * increments only counters with the same name, scope, and unit.
     */
    fun recordFirstRouteEvent(event: GPUFirstRouteTelemetryEvent): GPUTelemetryLedger =
        copy(
            counters = event.firstRouteCounters().fold(counters) { accumulated, counter ->
                accumulated.incrementCounter(counter)
            },
        )

    /**
     * Emits deterministic first-route counter lines for PM reports and tests.
     *
     * Only R6 first-route counters are included. Lines preserve ledger counter
     * order and use stable `counter:name:scope:value:unit` fields so failures
     * are reproducible for fixed scenes.
     */
    fun firstRouteCounterDumpLines(): List<String> =
        counters
            .filter { counter -> counter.name in firstRouteCounterNames }
            .map { counter -> "counter:${counter.name}:${counter.scope}:${counter.value}:${counter.unit}" }

    /** Factory helpers for telemetry ledgers. */
    companion object {
        /** Creates an empty telemetry ledger. */
        fun empty(): GPUTelemetryLedger =
            GPUTelemetryLedger(
                counters = emptyList(),
                cacheTelemetry = emptyList(),
                budgetTelemetry = emptyList(),
                promotionEvidence = emptyList(),
                cacheEvents = emptyList(),
                pipelineCacheTelemetry = emptyList(),
            )
    }
}

private val canonicalCacheDomains = setOf("material", "module", "pipeline", "bind-group-layout", "pipeline-layout")

private val firstRouteCounterNames = GPUFirstRouteCounterDomain.values()
    .map { domain -> domain.counterName }
    .toSet()

private fun GPUFirstRouteTelemetryEvent.firstRouteCounters(): List<GPUTelemetryCounter> =
    when (this) {
        is GPUFirstRouteTelemetryEvent.CommandFamily -> listOf(
            firstRouteCounter(
                domain = GPUFirstRouteCounterDomain.CommandFamily,
                scope = "family=${family.name}",
                count = count,
            ),
        )
        is GPUFirstRouteTelemetryEvent.Route -> listOfNotNull(
            firstRouteCounter(
                domain = GPUFirstRouteCounterDomain.RouteKind,
                scope = "kind=${kind.name}",
                count = count,
            ),
            refusalCode?.let { code ->
                firstRouteCounter(
                    domain = GPUFirstRouteCounterDomain.RouteRefusal,
                    scope = "code=$code",
                    count = count,
                )
            },
        )
        is GPUFirstRouteTelemetryEvent.WGSLModuleValidation -> listOf(
            firstRouteCounter(
                domain = GPUFirstRouteCounterDomain.WGSLModuleValidation,
                scope = "outcome=${outcome.name}",
                count = count,
            ),
        )
        is GPUFirstRouteTelemetryEvent.ResourceMaterialization -> listOf(
            firstRouteCounter(
                domain = GPUFirstRouteCounterDomain.ResourceMaterialization,
                scope = "outcome=${outcome.name}",
                count = count,
            ),
        )
        is GPUFirstRouteTelemetryEvent.CommandSubmission -> listOf(
            firstRouteCounter(
                domain = GPUFirstRouteCounterDomain.CommandSubmission,
                scope = "outcome=${outcome.name}",
                count = count,
            ),
        )
        GPUFirstRouteTelemetryEvent.NegativeCPUFallbackRefusal -> listOf(
            firstRouteCounter(
                domain = GPUFirstRouteCounterDomain.NegativeCPUFallbackRefusal,
                scope = "policy=forbidden",
                count = count,
            ),
        )
    }

private fun firstRouteCounter(
    domain: GPUFirstRouteCounterDomain,
    scope: String,
    count: Long,
): GPUTelemetryCounter =
    GPUTelemetryCounter(
        name = domain.counterName,
        value = count,
        unit = "count",
        scope = scope,
    )

private fun List<GPUTelemetryCounter>.incrementCounter(counter: GPUTelemetryCounter): List<GPUTelemetryCounter> {
    var updated = false
    val incremented = map { existing ->
        if (existing.name == counter.name && existing.scope == counter.scope && existing.unit == counter.unit) {
            updated = true
            existing.copy(value = existing.value + counter.value)
        } else {
            existing
        }
    }

    return if (updated) {
        incremented
    } else {
        incremented + counter
    }
}

private fun requirePositiveTelemetryCount(count: Long) {
    require(count > 0L) { "GPU first-route telemetry count must be positive" }
}

private fun requireStableTelemetryToken(label: String, value: String?) {
    require(!value.isNullOrBlank()) { "GPU first-route telemetry $label must not be blank" }
    require(value.none { char -> char == ':' || char.isWhitespace() }) {
        "GPU first-route telemetry $label must not contain whitespace or ':'"
    }
}

private fun Set<String>.stableFieldList(): String =
    if (isEmpty()) {
        "-"
    } else {
        sorted().joinToString(",")
    }
