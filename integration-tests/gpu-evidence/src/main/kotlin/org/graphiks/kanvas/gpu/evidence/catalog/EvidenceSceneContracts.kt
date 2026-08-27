package org.graphiks.kanvas.gpu.evidence.catalog

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeTelemetry

/** Stable identity for a catalogued evidence scene. */
@JvmInline
value class EvidenceSceneId(val value: String) {
    init {
        require(LOWER_KEBAB_CASE.matches(value)) {
            "EvidenceSceneId.value must use lower-kebab-case"
        }
    }

    companion object {
        private val LOWER_KEBAB_CASE = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")
    }
}

/** Closed expectation algebra for one evidence scene. */
sealed interface EvidenceExpectation {
    data object ShouldRender : EvidenceExpectation

    data class ShouldRefuse(val stableReasonCode: String) : EvidenceExpectation {
        init {
            require(stableReasonCode.isNotBlank()) {
                "EvidenceExpectation.ShouldRefuse.stableReasonCode must not be blank"
            }
        }
    }
}

/** Oracle or stable-refusal provenance associated with a scene. */
sealed interface OraclePolicy {
    data class GeneratedCpu(val oracleId: String, val version: Int) : OraclePolicy {
        init {
            require(oracleId.isNotBlank()) { "GeneratedCpu.oracleId must not be blank" }
            require(version > 0) { "GeneratedCpu.version must be positive" }
        }
    }

    data class CheckedInPng(
        val resourcePath: String,
        val sha256: String,
        val provenance: String,
    ) : OraclePolicy {
        init {
            require(resourcePath.isNotBlank()) { "CheckedInPng.resourcePath must not be blank" }
            require(sha256.isNotBlank()) { "CheckedInPng.sha256 must not be blank" }
            require(provenance.isNotBlank()) { "CheckedInPng.provenance must not be blank" }
        }

    }

    data object StableRefusal : OraclePolicy
}

/** Image comparison thresholds and their versioned rationale. */
data class ComparisonPolicy(
    val perChannelTolerance: Int,
    val minimumSimilarityPercent: Double,
    val version: Int,
    val rationale: String,
) {
    init {
        require(perChannelTolerance in 0..255) {
            "ComparisonPolicy.perChannelTolerance must be in [0, 255]"
        }
        require(minimumSimilarityPercent in 0.0..100.0) {
            "ComparisonPolicy.minimumSimilarityPercent must be in [0, 100]"
        }
        require(version > 0) { "ComparisonPolicy.version must be positive" }
        require(rationale.isNotBlank()) { "ComparisonPolicy.rationale must not be blank" }
    }
}

/** Complete immutable descriptor for one evidence scene. */
data class EvidenceSceneDescriptor(
    val id: EvidenceSceneId,
    val title: String,
    val purpose: String,
    val width: Int,
    val height: Int,
    val seed: Long,
    val tags: Set<String>,
    val expectation: EvidenceExpectation,
    val oracle: OraclePolicy,
    val comparison: ComparisonPolicy?,
    val requiredCapabilities: Set<String>,
) {
    init {
        require(title.isNotBlank()) { "EvidenceSceneDescriptor.title must not be blank" }
        require(purpose.isNotBlank()) { "EvidenceSceneDescriptor.purpose must not be blank" }
        require(width > 0) { "EvidenceSceneDescriptor.width must be positive" }
        require(height > 0) { "EvidenceSceneDescriptor.height must be positive" }
        require(tags.all(String::isNotBlank)) { "EvidenceSceneDescriptor.tags must not contain blanks" }
        require(requiredCapabilities.all(String::isNotBlank)) {
            "EvidenceSceneDescriptor.requiredCapabilities must not contain blanks"
        }
        when (expectation) {
            EvidenceExpectation.ShouldRender -> {
                require(oracle !is OraclePolicy.StableRefusal) {
                    "render expectations require an image oracle"
                }
                require(comparison != null) {
                    "render expectations require a comparison policy"
                }
            }

            is EvidenceExpectation.ShouldRefuse -> {
                require(oracle is OraclePolicy.StableRefusal) {
                    "refusal expectations require StableRefusal"
                }
                require(comparison == null) {
                    "refusal expectations cannot have an image comparison policy"
                }
            }
        }
    }
}

/** Catalog boundary owning scene identity uniqueness. */
class EvidenceSceneCatalog(descriptors: List<EvidenceSceneDescriptor>) {
    val scenes: List<EvidenceSceneDescriptor> = descriptors.toList()

    init {
        require(scenes.map { it.id }.toSet().size == scenes.size) {
            "EvidenceSceneCatalog scene ids must be unique"
        }
    }

    private val byId = scenes.associateBy { it.id }

    operator fun get(id: EvidenceSceneId): EvidenceSceneDescriptor? = byId[id]
}

/** Compatibility aliases for runner-facing scene contracts. */
typealias SceneRecordingContext = org.graphiks.kanvas.gpu.evidence.runner.SceneRecordingContext
typealias ScenePreparation = org.graphiks.kanvas.gpu.evidence.runner.ScenePreparation
typealias SceneProgram = org.graphiks.kanvas.gpu.evidence.runner.SceneProgram

/** Adapter identity captured as product evidence without native handles. */
data class EvidenceAdapter(
    val summary: String?,
    val vendor: String?,
    val device: String?,
    val architecture: String?,
    val description: String?,
    val isFallbackAdapter: Boolean?,
)

/** Environment facts attached to every observed result. */
data class EvidenceEnvironment(
    val sourceCommit: String,
    val osName: String,
    val osVersion: String,
    val osArchitecture: String,
    val javaVersion: String,
    val adapter: EvidenceAdapter?,
    val deviceGeneration: Long?,
    val capabilityImplementation: String?,
    val available: Boolean,
)

data class EvidenceEnvironmentRootIdentity(
    val osName: String,
    val osVersion: String,
    val osArchitecture: String,
    val javaVersion: String,
    val adapter: EvidenceAdapter?,
    val deviceGeneration: Long?,
    val capabilityImplementation: String?,
    val available: Boolean,
)

fun EvidenceEnvironment.rootIdentity(): EvidenceEnvironmentRootIdentity = EvidenceEnvironmentRootIdentity(
    osName = osName,
    osVersion = osVersion,
    osArchitecture = osArchitecture,
    javaVersion = javaVersion,
    adapter = adapter,
    deviceGeneration = deviceGeneration,
    capabilityImplementation = capabilityImplementation,
    available = available,
)

data class StructuralEventEvidence(
    val kind: String,
    val phase: String,
    val label: String?,
) {
    init {
        require(kind.isNotBlank()) { "StructuralEventEvidence.kind must not be blank" }
        require(phase.isNotBlank()) { "StructuralEventEvidence.phase must not be blank" }
    }
}

data class RouteEvidence(
    val routeId: String,
    val attemptId: String?,
    val furthestPhase: String?,
    val outcome: String,
    val encodedScopeKinds: List<String>,
    val structuralEvents: List<StructuralEventEvidence>,
    val structuralCounters: Map<String, Long>,
    val runtimeTelemetryDelta: GPUBackendRuntimeTelemetry,
) {
    init {
        require(routeId.isNotBlank()) { "RouteEvidence.routeId must not be blank" }
        require(outcome.isNotBlank()) { "RouteEvidence.outcome must not be blank" }
        require(encodedScopeKinds.all(String::isNotBlank)) {
            "RouteEvidence.encodedScopeKinds must not contain blanks"
        }
        require(structuralCounters.keys.all(String::isNotBlank)) {
            "RouteEvidence.structuralCounters keys must not be blank"
        }
        require(structuralCounters.values.all { it >= 0L }) {
            "RouteEvidence.structuralCounters values must be non-negative"
        }
    }
}

/** Immutable comparison result whose diff bytes cannot be mutated by callers. */
class ImageComparison(
    val passed: Boolean,
    val similarityPercent: Double,
    val differingPixels: Int,
    val maxChannelDifference: Int,
    val meanChannelDifference: Double,
    diffRgba: ByteArray,
    val policyVersion: Int,
) {
    private val ownedDiff = diffRgba.copyOf()
    val diffRgba: ByteArray get() = ownedDiff.copyOf()

    init {
        require(similarityPercent in 0.0..100.0) { "ImageComparison.similarityPercent must be in [0, 100]" }
        require(differingPixels >= 0) { "ImageComparison.differingPixels must be non-negative" }
        require(maxChannelDifference in 0..255) { "ImageComparison.maxChannelDifference must be in [0, 255]" }
        require(meanChannelDifference >= 0.0) { "ImageComparison.meanChannelDifference must be non-negative" }
        require(policyVersion > 0) { "ImageComparison.policyVersion must be positive" }
    }
}

/** Observed terminal result of one scene execution attempt. */
sealed interface SceneObservation {
    val environment: EvidenceEnvironment

    class Rendered(
        rgba: ByteArray,
        val route: RouteEvidence,
        val diagnostics: List<String>,
        override val environment: EvidenceEnvironment,
        val comparison: ImageComparison,
    ) : SceneObservation {
        private val ownedRgba = rgba.copyOf()
        val rgba: ByteArray get() = ownedRgba.copyOf()
    }

    data class Refused(
        val stableReasonCode: String,
        val message: String,
        val submissionDelta: Long,
        val route: RouteEvidence,
        val diagnostics: List<String>,
        override val environment: EvidenceEnvironment,
    ) : SceneObservation {
        init {
            require(stableReasonCode.isNotBlank()) { "SceneObservation.Refused.stableReasonCode must not be blank" }
            require(message.isNotBlank()) { "SceneObservation.Refused.message must not be blank" }
            require(submissionDelta >= 0L) { "SceneObservation.Refused.submissionDelta must be non-negative" }
        }
    }

    data class Unavailable(
        val stableReasonCode: String,
        val message: String,
        override val environment: EvidenceEnvironment,
    ) : SceneObservation {
        init {
            require(stableReasonCode.isNotBlank()) { "SceneObservation.Unavailable.stableReasonCode must not be blank" }
            require(message.isNotBlank()) { "SceneObservation.Unavailable.message must not be blank" }
        }
    }
}
