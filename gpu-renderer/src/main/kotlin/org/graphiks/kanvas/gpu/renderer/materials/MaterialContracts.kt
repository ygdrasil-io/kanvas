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
        val entryPoint: String,
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

/** Reflected scalar/vector/matrix type admitted by a registered runtime-effect program. */
enum class GPUPreparedRuntimeEffectUniformType {
    Float1,
    Float2,
    Float3,
    Float4,
    Int1,
    Matrix3x3,
    Matrix4x4,
}

/** Exact reflected field layout owned by a registered runtime-effect program. */
@ConsistentCopyVisibility
data class GPUPreparedRuntimeEffectUniformField internal constructor(
    val name: String,
    val type: GPUPreparedRuntimeEffectUniformType,
    val offsetBytes: Int,
    val sizeBytes: Int,
    val alignmentBytes: Int,
    val strideBytes: Int? = null,
)

/** Exact reflected resource-binding topology owned by a registered runtime-effect program. */
@ConsistentCopyVisibility
data class GPUPreparedRuntimeEffectBinding internal constructor(
    val group: Int,
    val binding: Int,
    val resourceKind: String,
    val minBindingSizeBytes: Int?,
)

enum class GPUPreparedRuntimeEffectSourceColorContract {
    LinearStraightRgba,
    LinearPremultipliedRgba,
}

/** Reflected, ordered child-slot ABI owned by a registered runtime-effect program. */
@ConsistentCopyVisibility
data class GPUPreparedRuntimeEffectChildSlot internal constructor(
    val name: String,
    val role: GPUPreparedRuntimeEffectChildRole,
    val bindingIndex: Int?,
    val abiHash: String,
) {
    init {
        require(name.isNotBlank()) { "Prepared runtime-effect child slot name must not be blank" }
        require(bindingIndex == null || bindingIndex >= 0) {
            "Prepared runtime-effect child binding index must be non-negative"
        }
        require(abiHash.matches(Regex("sha256:[0-9a-f]{64}"))) {
            "Prepared runtime-effect child slot ABI hash must be canonical"
        }
    }
}

/** Canonical invocation ABI shared by every child program admitted for [role]. */
internal fun preparedRuntimeEffectChildAbiHash(
    role: GPUPreparedRuntimeEffectChildRole,
): String = CanonicalIdentityEncoder("prepared-runtime-effect-child-abi-v1")
    .text("role", role.name)
    .text(
        "inputContract",
        when (role) {
            GPUPreparedRuntimeEffectChildRole.Shader -> "local-position-vec2-f32"
            GPUPreparedRuntimeEffectChildRole.ColorFilter -> "linear-premul-rgba-f32"
            GPUPreparedRuntimeEffectChildRole.Blender -> "two-linear-premul-rgba-f32"
        },
    )
    .text("outputContract", "linear-premul-rgba-f32")
    .digestIdentity()

/**
 * Canonical executable facts for one registered Kanvas runtime effect.
 *
 * The constructor is module-internal so consumers cannot inject arbitrary
 * WGSL, entry points, ABI layouts, or hashes through the public compiler API.
 */
@ConsistentCopyVisibility
data class GPUPreparedRuntimeEffectProgram internal constructor(
    val effectId: String,
    val descriptorVersion: Int,
    val wgslSource: String,
    val sourceFunction: String,
    val sourceColorContract: GPUPreparedRuntimeEffectSourceColorContract,
    val sourceHash: String,
    val moduleHash: String,
    val reflectionHash: String,
    val uniformSchemaHash: String,
    val uniformBlockSizeBytes: Int,
    val uniformFields: List<GPUPreparedRuntimeEffectUniformField>,
    val bindings: List<GPUPreparedRuntimeEffectBinding>,
    val bindingPlanHash: String,
    val routeContractHash: String,
    val childSlots: List<GPUPreparedRuntimeEffectChildSlot> = emptyList(),
) {
    init {
        require(childSlots.map { slot -> slot.name }.distinct().size == childSlots.size) {
            "Prepared runtime-effect child slot names must be unique"
        }
        val bindingIndices = childSlots.mapNotNull { slot -> slot.bindingIndex }
        require(bindingIndices.distinct().size == bindingIndices.size) {
            "Prepared runtime-effect child binding indices must be unique"
        }
    }
}

/** Result of resolving a descriptor against registered Kanvas program authority. */
sealed interface GPUPreparedRuntimeEffectResolution {
    /** The canonical descriptor is absent or its version differs. */
    data class DescriptorUnavailable(val message: String) :
        GPUPreparedRuntimeEffectResolution

    /** The descriptor exists but no fully proven Kotlin/CPU plus WGSL program is available. */
    data class ProgramUnavailable(val message: String) :
        GPUPreparedRuntimeEffectResolution

    /** A descriptor-, CPU-, parser-, reflection-, and ABI-validated program. */
    @ConsistentCopyVisibility
    data class Ready internal constructor(val program: GPUPreparedRuntimeEffectProgram) :
        GPUPreparedRuntimeEffectResolution
}

/** Neutral lookup seam consumed by material lowering without importing runtime-effect ownership. */
fun interface GPUPreparedRuntimeEffectResolver {
    fun resolve(effectId: String, descriptorVersion: Int): GPUPreparedRuntimeEffectResolution
}

/** Fail-closed resolver used by contexts that do not opt into registered runtime effects. */
object GPUPreparedRuntimeEffectResolverUnavailable : GPUPreparedRuntimeEffectResolver {
    override fun resolve(
        effectId: String,
        descriptorVersion: Int,
    ): GPUPreparedRuntimeEffectResolution =
        GPUPreparedRuntimeEffectResolution.ProgramUnavailable(
            "Registered runtime-effect program resolver is unavailable",
        )
}

/** Material lowering context facts. */
data class GPUMaterialLoweringContext(
    val capabilityClass: String,
    val targetFormatClass: String,
    val dictionaryVersion: String,
    val runtimeEffectResolver: GPUPreparedRuntimeEffectResolver =
        GPUPreparedRuntimeEffectResolverUnavailable,
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
