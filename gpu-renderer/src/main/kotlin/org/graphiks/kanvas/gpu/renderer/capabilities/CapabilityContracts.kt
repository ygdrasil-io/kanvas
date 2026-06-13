package org.graphiks.kanvas.gpu.renderer.capabilities

/** Implementation identity for wgpu4k, Dawn, or future pure Kotlin facade backends. */
data class GPUImplementationIdentity(
    val facadeName: String,
    val implementationName: String,
    val adapterName: String,
    val deviceName: String,
    val vendorId: String? = null,
    val deviceId: String? = null,
)

/** Single behavior-affecting capability fact. */
data class GPUCapabilityFact(
    val name: String,
    val source: String,
    val value: String,
    val affectsValidity: Boolean,
    val evidenceLabel: String,
)

/** Shader or device feature required by a route. */
data class GPUFeatureRequirement(
    val featureName: String,
    val requiredValue: String,
    val reasonCode: String,
)

/** Adapter/device limit required by a route. */
data class GPULimitRequirement(
    val limitName: String,
    val requiredMinimum: Long,
    val observedValue: Long? = null,
    val unit: String,
    val affectsValidity: Boolean,
)

/** Capability snapshot for the selected GPU facade implementation. */
data class GPUCapabilities(
    val implementation: GPUImplementationIdentity,
    val facts: List<GPUCapabilityFact>,
    val knownUnsupportedFacts: List<GPUCapabilityFact> = emptyList(),
    val snapshotId: String,
)

/** Diagnostic emitted when capability facts block a route. */
data class GPUCapabilityDiagnostic(
    val code: String,
    val severity: String,
    val requirementName: String,
    val required: String,
    val observed: String? = null,
    val isTerminal: Boolean,
)
