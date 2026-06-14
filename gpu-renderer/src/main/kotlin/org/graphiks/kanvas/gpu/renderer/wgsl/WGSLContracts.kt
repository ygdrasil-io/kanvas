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

/** Typed WGSL uniform field layout descriptor. */
data class WGSLUniformFieldLayout(
    val name: String,
    val type: String,
    val offset: Long,
    val sizeBytes: Long,
    val alignment: Int,
)

/** WGSL uniform layout descriptor. */
data class WGSLUniformLayout(
    val layoutHash: String,
    val fields: List<String>,
    val fieldLayouts: List<WGSLUniformFieldLayout> = emptyList(),
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

/**
 * Explicit parser state for WGSL validation and reflection fixtures.
 *
 * `parserBacked` is true only when a real `wgsl4k` handoff produced the
 * reflection data. When the dependency is unavailable, modules may still carry
 * fixture-declared ABI facts for tests, but they must not claim parser-backed
 * product support.
 */
data class WGSLParserState(
    val status: String,
    val toolName: String,
    val message: String,
) {
    /** True only when reflection came from a real parser handoff. */
    val parserBacked: Boolean
        get() = status == "parser-backed"

    /** Parser state factory constants. */
    companion object {
        /** Records that the parser dependency is unavailable for this module. */
        fun unavailable(toolName: String, message: String): WGSLParserState =
            WGSLParserState(status = "unavailable", toolName = toolName, message = message)
    }
}

/** WGSL reflection result accepted or rejected by validation. */
sealed interface WGSLReflectionResult {
    /** Reflection accepted for a module. */
    data class Accepted(
        val moduleHash: WGSLModuleHash,
        val bindings: List<WGSLBindingLayout>,
        val uniforms: List<WGSLUniformLayout>,
        val storage: List<WGSLStorageLayout>,
        val diagnostics: List<WGSLValidationDiagnostic> = emptyList(),
        val parserState: WGSLParserState = WGSLParserState.unavailable(
            toolName = "wgsl4k",
            message = "parser state not provided",
        ),
        val reflectionSource: String = "fixture-declared",
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
    val moduleLabel: String = "wgsl-module",
    val moduleSalt: String = rendererVersionSalt,
    val vertexEntryPoint: String = entryPoint,
    val fragmentEntryPoint: String = entryPoint,
    val packingPlans: List<WGSLPackingPlan> = emptyList(),
    val parserState: WGSLParserState = WGSLParserState.unavailable(
        toolName = "wgsl4k",
        message = "parser state not provided",
    ),
    val source: String = "",
    val diagnostics: List<WGSLValidationDiagnostic> = emptyList(),
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

/** Facade capabilities used by module assembly fixtures. */
data class WGSLFacadeCapabilities(
    val supportedFeatures: Set<String>,
    val maxBindGroup: Int = 3,
    val maxBinding: Int = 15,
)

/**
 * Complete render module assembly input for deterministic WGSL fixtures.
 *
 * The input is structured descriptor data, not arbitrary user WGSL. Assembly
 * validates entry points, binding uniqueness, facade limits, and Kotlin/WGSL
 * packing offsets before emitting a module or a stable rejected result.
 */
data class WGSLModuleAssemblyInput(
    val moduleLabel: String,
    val moduleSalt: String,
    val vertexEntryPoint: String,
    val fragmentEntryPoint: String,
    val fragments: List<WGSLFragment>,
    val bindings: List<WGSLBindingLayout>,
    val uniformLayouts: List<WGSLUniformLayout>,
    val packingPlans: List<WGSLPackingPlan>,
    val storageLayouts: List<WGSLStorageLayout> = emptyList(),
    val parserState: WGSLParserState,
    val capabilities: WGSLFacadeCapabilities,
)

/** Result of deterministic WGSL render module assembly. */
sealed interface WGSLModuleAssemblyResult {
    /** Complete module fixture assembled and ABI-reflected from declared descriptors. */
    data class Accepted(val module: WGSLModule) : WGSLModuleAssemblyResult

    /** Module assembly refused with stable diagnostics before backend shader creation. */
    data class Rejected(
        val moduleHash: WGSLModuleHash?,
        val diagnostics: List<WGSLValidationDiagnostic>,
    ) : WGSLModuleAssemblyResult
}
