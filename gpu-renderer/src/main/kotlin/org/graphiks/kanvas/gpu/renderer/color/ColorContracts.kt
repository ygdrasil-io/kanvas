package org.graphiks.kanvas.gpu.renderer.color

/** Color-space descriptor used by color planning. */
data class GPUColorSpaceDescriptor(
    val name: String,
    val primaries: String,
    val transferFunction: String,
    val whitePoint: String,
)

/** External or embedded color profile descriptor. */
data class GPUColorProfileDescriptor(
    val sourceKind: String,
    val profileId: String,
    val profileHash: String,
)

/** Color value representation. */
data class GPUColorValueSpec(
    val componentCount: Int,
    val colorSpace: GPUColorSpaceDescriptor,
    val alphaType: String,
    val numericEncoding: String,
)

/** Color transform plan. */
data class GPUColorTransformPlan(
    val transformKey: String,
    val matrixValues: List<Float>,
    val precisionPolicy: String,
)

/** Color conversion plan between source and destination spaces. */
data class GPUColorConversionPlan(
    val source: GPUColorSpaceDescriptor,
    val destination: GPUColorSpaceDescriptor,
    val transform: GPUColorTransformPlan,
    val policy: String,
    val refusalCode: String? = null,
)

/** Working color-space plan. */
data class GPUWorkingColorSpacePlan(
    val space: GPUColorSpaceDescriptor,
    val reason: String,
    val highPrecision: Boolean,
)

/** Gradient interpolation color plan. */
data class GPUGradientColorPlan(
    val interpolationSpace: GPUColorSpaceDescriptor,
    val hueMethod: String,
    val premulInterpolation: Boolean,
    val stopStorePolicy: String,
)

/** Color uniform packing plan. */
data class GPUColorUniformPlan(
    val slotName: String,
    val valueSpecs: List<GPUColorValueSpec>,
    val packingPolicy: String,
    val dynamicValueCount: Int,
)

/** HDR color plan. */
data class GPUHDRColorPlan(
    val enabled: Boolean,
    val transferFunction: String,
    val maxNits: Float? = null,
    val refusalCode: String? = null,
)

/** Gainmap handling plan. */
data class GPUGainmapPlan(
    val kind: String,
    val baseSpace: GPUColorSpaceDescriptor,
    val alternateSpace: GPUColorSpaceDescriptor? = null,
    val metadataHash: String? = null,
    val supported: Boolean,
)

/** Target store conversion plan. */
data class GPUColorStorePlan(
    val targetSpace: GPUColorSpaceDescriptor,
    val conversion: GPUColorConversionPlan? = null,
    val quantization: String,
    val dither: Boolean,
)

/** Color cache invalidation plan. */
data class GPUColorCachePlan(
    val cacheKey: String,
    val dependentProfileIds: List<String>,
    val invalidationFacts: List<String>,
)

/** Color-management plan for one material or target. */
data class GPUColorManagementPlan(
    val inputSpec: GPUColorValueSpec,
    val workingSpace: GPUWorkingColorSpacePlan,
    val conversion: GPUColorConversionPlan? = null,
    val store: GPUColorStorePlan,
    val diagnostics: List<GPUColorDiagnostic> = emptyList(),
)

/** Color planning diagnostic. */
data class GPUColorDiagnostic(
    val code: String,
    val severity: String,
    val message: String,
    val facts: Map<String, String> = emptyMap(),
    val isTerminal: Boolean,
)
