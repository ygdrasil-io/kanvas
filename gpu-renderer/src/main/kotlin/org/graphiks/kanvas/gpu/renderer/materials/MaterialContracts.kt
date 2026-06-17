package org.graphiks.kanvas.gpu.renderer.materials

/** Stable material key. */
@JvmInline
value class MaterialKey(val value: String) {
    init {
        require(value.isNotBlank()) { "MaterialKey.value must not be blank" }
    }
}

/** Material program identifier. */
@JvmInline
value class GPUMaterialProgramID(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUMaterialProgramID.value must not be blank" }
    }
}

/** WGSL snippet identifier owned by the material dictionary. */
@JvmInline
value class WGSLSnippetID(val value: String) {
    init {
        require(value.isNotBlank()) { "WGSLSnippetID.value must not be blank" }
    }
}

/** Paint evaluation order. */
enum class GPUPaintEvaluationOrder {
    /** Evaluate source before coverage. */
    SourceThenCoverage,
    /** Evaluate coverage before source. */
    CoverageThenSource,
}

/** Material source kind. */
enum class GPUMaterialSourceKind {
    /** Solid color source. */
    SolidColor,
    /** Gradient source. */
    Gradient,
    /** Image shader source. */
    ImageShader,
    /** Registered runtime effect source. */
    RuntimeEffect,
    /** Unsupported source that must route to refusal. */
    Unsupported,
}

/** Gradient kind. */
enum class GPUGradientKind {
    /** Linear gradient. */
    Linear,
    /** Radial gradient. */
    Radial,
    /** Sweep gradient. */
    Sweep,
    /** Two-point conical gradient. */
    TwoPointConical,
}

/** Material tile mode. */
enum class GPUMaterialTileMode {
    /** Clamp to edge. */
    Clamp,
    /** Repeat periodically. */
    Repeat,
    /** Mirror periodically. */
    Mirror,
    /** Decal outside source bounds. */
    Decal,
}

/** Paint descriptor captured before material lowering. */
data class GPUPaintDescriptor(
    val paintId: String,
    val source: GPUMaterialSourceDescriptor,
    val blendModeLabel: String,
    val alpha: Float,
    val colorSpaceLabel: String,
)

/** Solid color source plan. */
data class GPUSolidColorPlan(
    val r: Float,
    val g: Float,
    val b: Float,
    val a: Float,
    val colorSpecLabel: String,
)

/** Gradient geometry plan. */
data class GPUGradientGeometryPlan(
    val kind: GPUGradientKind,
    val controlPoints: List<Float>,
    val localMatrixHash: String? = null,
)

/** Gradient stop plan. */
data class GPUGradientStopPlan(
    val offset: Float,
    val colorLabel: String,
)

/** Gradient stop storage plan. */
data class GPUGradientStopStorePlan(
    val stopCount: Int,
    val storageKind: String,
    val payloadHash: String,
)

/** Gradient source plan. */
data class GPUGradientPlan(
    val geometry: GPUGradientGeometryPlan,
    val stops: List<GPUGradientStopPlan>,
    val stopStore: GPUGradientStopStorePlan,
    val tileMode: GPUMaterialTileMode,
)

/** Material sampling plan. */
data class GPUMaterialSamplingPlan(
    val tileModeX: GPUMaterialTileMode,
    val tileModeY: GPUMaterialTileMode,
    val filterMode: String,
    val mipmapMode: String,
)

/** Image shader source plan. */
data class GPUImageShaderPlan(
    val imageSourceKey: String,
    val sampling: GPUMaterialSamplingPlan,
    val colorTreatment: String,
)

/** Local matrix shader wrapper plan. */
data class GPULocalMatrixShaderPlan(
    val childSourceKey: String,
    val localMatrixHash: String,
    val inverseAvailable: Boolean,
)

/** Shader blend source plan. */
data class GPUShaderBlendSourcePlan(
    val srcSourceKey: String,
    val dstSourceKey: String,
    val blendModeLabel: String,
)

/** Paint color plan after color management. */
data class GPUPaintColorPlan(
    val sourceColorLabel: String,
    val colorUniformSlot: String,
    val premulPolicy: String,
)

/** Material source descriptor union. */
sealed interface GPUMaterialSourceDescriptor {
    /** Source kind. */
    val kind: GPUMaterialSourceKind

    /** Solid color descriptor. */
    data class Solid(val plan: GPUSolidColorPlan) : GPUMaterialSourceDescriptor {
        override val kind: GPUMaterialSourceKind = GPUMaterialSourceKind.SolidColor
    }

    /** Gradient descriptor. */
    data class Gradient(val plan: GPUGradientPlan) : GPUMaterialSourceDescriptor {
        override val kind: GPUMaterialSourceKind = GPUMaterialSourceKind.Gradient
    }

    /** Image shader descriptor. */
    data class Image(val plan: GPUImageShaderPlan) : GPUMaterialSourceDescriptor {
        override val kind: GPUMaterialSourceKind = GPUMaterialSourceKind.ImageShader
    }

    /** Registered runtime-effect material source descriptor. */
    data class RuntimeEffect(
        val effectId: String,
        val descriptorVersion: Int,
        val routeContractHash: String,
    ) : GPUMaterialSourceDescriptor {
        override val kind: GPUMaterialSourceKind = GPUMaterialSourceKind.RuntimeEffect
    }

    /** Unsupported descriptor. */
    data class Unsupported(val reasonCode: String) : GPUMaterialSourceDescriptor {
        override val kind: GPUMaterialSourceKind = GPUMaterialSourceKind.Unsupported
    }
}

/** Material source lowering plan. */
sealed interface GPUMaterialSourcePlan {
    /** Accepted source lowering. */
    data class Accepted(
        val source: GPUMaterialSourceDescriptor,
        val snippetId: WGSLSnippetID,
        val payloadPlanHash: String,
        val diagnostics: List<GPUMaterialSourceDiagnostic> = emptyList(),
    ) : GPUMaterialSourcePlan

    /** Refused source lowering. */
    data class Refused(val diagnostic: GPUMaterialSourceDiagnostic) : GPUMaterialSourcePlan
}

/** Paint pipeline stage plan. */
sealed interface GPUPaintStagePlan {
    /** Material stage. */
    data class Material(val sourcePlan: GPUMaterialSourcePlan) : GPUPaintStagePlan

    /** Color stage. */
    data class Color(val colorPlan: GPUPaintColorPlan) : GPUPaintStagePlan

    /** Refused stage. */
    data class Refused(val diagnostic: GPUPaintPipelineDiagnostic) : GPUPaintStagePlan
}

/** Paint pipeline plan. */
data class GPUPaintPipelinePlan(
    val paint: GPUPaintDescriptor,
    val evaluationOrder: GPUPaintEvaluationOrder,
    val stages: List<GPUPaintStagePlan>,
    val materialKey: MaterialKey,
    val diagnostics: List<GPUPaintPipelineDiagnostic> = emptyList(),
)

/** Snapshot of material dictionary entries. */
data class GPUMaterialDictionary(
    val dictionaryVersion: String,
    val snippets: List<WGSLSnippet>,
    val rootSets: List<GPUMaterialRootSet>,
)

/** Material lowering context facts. */
data class GPUMaterialLoweringContext(
    val capabilityClass: String,
    val targetFormatClass: String,
    val dictionaryVersion: String,
)

/** Material root set used for assembly. */
data class GPUMaterialRootSet(
    val rootSetId: String,
    val snippetIds: List<WGSLSnippetID>,
    val payloadShapeHash: String,
)

/**
 * WGSL snippet dictionary entry owned by material lowering.
 *
 * Snippets describe material-source code fragments and their ABI requirements;
 * they are not arbitrary product WGSL input. Missing snippets, incompatible
 * versions, graph cycles, or unmet feature requirements must surface as stable
 * material diagnostics before generic WGSL module assembly.
 */
data class WGSLSnippet(
    val snippetId: WGSLSnippetID,
    val sourceHash: String,
    val entryPoint: String,
    val requiredBindings: List<String>,
    val category: String = "material-source",
    val version: String = "v1",
    val uniformLayoutHashes: List<String> = emptyList(),
    val requiredFeatures: List<String> = emptyList(),
)

/** WGSL snippet dependency node. */
data class WGSLSnippetNode(
    val snippetId: WGSLSnippetID,
    val children: List<WGSLSnippetID>,
    val evaluationOrder: Int,
)

/** Material assembly plan. */
data class GPUMaterialAssemblyPlan(
    val programId: GPUMaterialProgramID,
    val rootSet: GPUMaterialRootSet,
    val snippetGraph: List<WGSLSnippetNode>,
    val moduleSalt: String,
)

/** Result of expanding a material dictionary entry into a module assembly plan. */
sealed interface GPUMaterialAssemblyResult {
    /** Material dictionary expansion accepted with a deterministic assembly plan. */
    data class Accepted(val plan: GPUMaterialAssemblyPlan) : GPUMaterialAssemblyResult

    /** Material dictionary expansion refused before WGSL assembly. */
    data class Refused(val diagnostic: GPUMaterialSourceDiagnostic) : GPUMaterialAssemblyResult
}

/** Material source payload plan. */
data class GPUMaterialSourcePayloadPlan(
    val materialKey: MaterialKey,
    val payloadFields: List<String>,
    val resourceBindings: List<String>,
)

/** Material-source diagnostic. */
data class GPUMaterialSourceDiagnostic(
    val code: String,
    val sourceKind: GPUMaterialSourceKind,
    val message: String,
    val terminal: Boolean,
)

/** Paint-pipeline diagnostic. */
data class GPUPaintPipelineDiagnostic(
    val code: String,
    val paintId: String,
    val message: String,
    val terminal: Boolean,
)
