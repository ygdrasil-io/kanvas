package org.graphiks.kanvas.gpu.renderer.wgsl

/** Stable hash for a complete WGSL module. */
@JvmInline
value class WGSLModuleHash(val value: String) {
    init {
        require(value.isNotBlank()) { "WGSLModuleHash.value must not be blank" }
    }
}

/** WGSL binding layout descriptor. */
data class WGSLBindingLayout(
    val group: Int,
    val binding: Int,
    val visibility: Set<String>,
    val resourceKind: String,
    val access: String,
    val minBindingSize: Long? = null,
    val dynamicOffset: Boolean = false,
    val layoutRole: String,
    val diagnosticLabel: String,
)

/** WGSL uniform layout descriptor. */
data class WGSLUniformLayout(
    val layoutHash: String,
    val fields: List<String>,
    val sizeBytes: Long,
    val alignment: Int,
    val stride: Int? = null,
    val numericRepresentation: String,
)

/** WGSL storage layout descriptor. */
data class WGSLStorageLayout(
    val layoutHash: String,
    val fields: List<String>,
    val sizeBytes: Long,
    val alignment: Int,
    val stride: Int,
    val numericRepresentation: String,
)

/** WGSL resource binding plan. */
data class WGSLResourceBindingPlan(
    val planHash: String,
    val bindGroupRole: String,
    val bindings: List<WGSLBindingLayout>,
    val dynamicOffsetPolicy: String,
)

/** Kotlin-to-WGSL packing ABI plan. */
data class WGSLPackingPlan(
    val planHash: String,
    val layoutHash: String,
    val fieldOrder: List<String>,
    val offsets: Map<String, Long>,
    val paddingBytes: Long,
    val dynamicOffsetAlignment: Int,
)

/** WGSL reflection result accepted or rejected by validation. */
sealed interface WGSLReflectionResult {
    /** Reflection accepted for a module. */
    data class Accepted(
        val moduleHash: WGSLModuleHash,
        val bindings: List<WGSLBindingLayout>,
        val uniforms: List<WGSLUniformLayout>,
        val storage: List<WGSLStorageLayout>,
        val diagnostics: List<WGSLValidationDiagnostic> = emptyList(),
    ) : WGSLReflectionResult

    /** Reflection rejected for a module. */
    data class Rejected(
        val moduleHash: WGSLModuleHash?,
        val diagnostics: List<WGSLValidationDiagnostic>,
    ) : WGSLReflectionResult
}

/** WGSL fragment assembled by a domain planner. */
data class WGSLFragment(
    val fragmentId: String,
    val stage: String,
    val sourceHash: String,
    val entryPoints: List<String>,
    val bindingLayouts: List<WGSLBindingLayout>,
    val uniformLayouts: List<WGSLUniformLayout>,
    val storageLayouts: List<WGSLStorageLayout>,
    val requiredFeatures: List<String>,
    val diagnosticLabel: String,
)

/** Complete WGSL render module contract. */
data class WGSLModule(
    val moduleHash: WGSLModuleHash,
    val entryPoint: String,
    val fragments: List<WGSLFragment>,
    val bindings: List<WGSLBindingLayout>,
    val uniformLayouts: List<WGSLUniformLayout>,
    val storageLayouts: List<WGSLStorageLayout>,
    val reflection: WGSLReflectionResult,
    val rendererVersionSalt: String,
)

/** Complete WGSL compute module contract. */
data class WGSLComputeModule(
    val moduleHash: WGSLModuleHash,
    val entryPoint: String,
    val fragments: List<WGSLFragment>,
    val bindings: List<WGSLBindingLayout>,
    val uniformLayouts: List<WGSLUniformLayout>,
    val storageLayouts: List<WGSLStorageLayout>,
    val reflection: WGSLReflectionResult,
    val workgroupPolicy: String,
    val resourceAccessPolicy: String,
)

/** WGSL validation diagnostic. */
data class WGSLValidationDiagnostic(
    val code: String,
    val moduleHash: WGSLModuleHash? = null,
    val fieldOrBinding: String? = null,
    val message: String,
    val terminal: Boolean,
)
