package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUCacheTelemetry

/** Compact phase 0 snapshot that ties runtime counters to cache and capability evidence. */
data class GPURuntimeBaselineSnapshot(
    val label: String,
    val telemetry: GPUBackendRuntimeTelemetry,
    val cacheTelemetry: List<GPUCacheTelemetry> = emptyList(),
    val capabilityFacts: List<GPUCapabilityFact> = emptyList(),
) {
    init {
        require(label.isNotBlank()) { "GPURuntimeBaselineSnapshot.label must not be blank" }
        require(label.isDumpSafePhase0Token()) {
            "GPURuntimeBaselineSnapshot.label must be dump-safe"
        }
    }

    /** Deterministic phase 0 dump lines without runtime handles. */
    fun dumpLines(): List<String> =
        buildList {
            requireDumpSafePhase0Token("GPURuntimeBaselineSnapshot.label", label)
            add(
                "gpu-phase0.baseline label=$label renderPasses=${telemetry.renderPasses} " +
                    "offscreenPasses=${telemetry.offscreenPasses} windowPasses=${telemetry.windowPasses} " +
                    "submissions=${telemetry.submissions} commandBuffers=${telemetry.commandBuffers} " +
                    "buffersCreated=${telemetry.buffersCreated} texturesCreated=${telemetry.texturesCreated} " +
                    "bindGroupsCreated=${telemetry.bindGroupsCreated} samplersCreated=${telemetry.samplersCreated} " +
                    "queueWrites=${telemetry.queueWrites} uniformSlabsCreated=${telemetry.uniformSlabsCreated} " +
                    "uniformSlabBytesAllocated=${telemetry.uniformSlabBytesAllocated} " +
                    "uniformSlabFallbacks=${telemetry.uniformSlabFallbacks}",
            )
            cacheTelemetry.forEach { cache ->
                requireDumpSafePhase0Token("GPURuntimeBaselineSnapshot.cacheTelemetry.domain", cache.cacheName)
                add(
                    "gpu-phase0.cache label=$label domain=${cache.cacheName} hits=${cache.hits} " +
                        "misses=${cache.misses} creates=${cache.creations} failures=${cache.failures}",
                )
            }
            capabilityFacts.forEach { fact ->
                requireDumpSafePhase0Token("GPURuntimeBaselineSnapshot.capabilityFacts.name", fact.name)
                requireDumpSafePhase0Token("GPURuntimeBaselineSnapshot.capabilityFacts.source", fact.source)
                requireDumpSafePhase0Token("GPURuntimeBaselineSnapshot.capabilityFacts.value", fact.value)
                requireDumpSafePhase0Token("GPURuntimeBaselineSnapshot.capabilityFacts.evidenceLabel", fact.evidenceLabel)
                add(
                    "gpu-phase0.capability label=$label name=${fact.name} source=${fact.source} " +
                        "value=${fact.value} affectsValidity=${fact.affectsValidity} " +
                        "evidence=${fact.evidenceLabel}",
                )
            }
        }
}

fun GPUBackendSession.phase0BaselineSnapshot(label: String): GPURuntimeBaselineSnapshot =
    GPURuntimeBaselineSnapshot(
        label = label,
        telemetry = runtimeTelemetry,
        cacheTelemetry = executionCacheTelemetry,
        capabilityFacts = buildList {
            capabilities?.limits?.capabilityFacts(evidenceLabel = "runtime")?.let(::addAll)
            capabilities?.facts?.let(::addAll)
        }.distinctBy { fact ->
            listOf(
                fact.name,
                fact.source,
                fact.value,
                fact.affectsValidity.toString(),
            )
        },
    )

private fun String.isDumpSafePhase0Token(): Boolean =
    matches(PHASE0_DUMP_SAFE_LABEL_PATTERN) && !PHASE0_RAW_HANDLE_DUMP_PATTERN.containsMatchIn(this)

private fun requireDumpSafePhase0Token(fieldName: String, value: String) {
    require(value.isDumpSafePhase0Token()) { "$fieldName must be dump-safe" }
}

private val PHASE0_DUMP_SAFE_LABEL_PATTERN = Regex("^[A-Za-z0-9._:-]+$")
private val PHASE0_RAW_BACKEND_TOKEN = "w" + "gpu"
private val PHASE0_RAW_HANDLE_DUMP_PATTERN =
    Regex("(?i)($PHASE0_RAW_BACKEND_TOKEN|externaltexturehandle|gpu[a-z0-9]*handle|0x[0-9a-f]{6,})")
