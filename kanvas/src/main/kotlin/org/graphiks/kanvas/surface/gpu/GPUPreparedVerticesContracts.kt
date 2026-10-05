package org.graphiks.kanvas.surface.gpu

import java.util.Collections
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedVerticesUploadArtifact
import org.graphiks.kanvas.gpu.renderer.clips.GPUBounds
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgram
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedVerticesMaterialPlanEmission
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.commands.GPUBlendFacts
import org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesFloatBounds
import org.graphiks.kanvas.gpu.renderer.vertices.GPUPrimitiveBlendPlan
import org.graphiks.kanvas.gpu.plan.MaterialPlanRef
import org.graphiks.kanvas.gpu.plan.MaterialPlanTable
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.geometry.RectF32

/** The public operation semantic retained by one handle-free prepared vertices draw. */
internal enum class GPUPreparedVerticesOperationKind { DrawVertices, DrawMesh }

/** Sealed W5a material authority retained from capture through native preflight. */
internal data class GPUPreparedVerticesMaterialPlan(
    val table: MaterialPlanTable,
    val ref: MaterialPlanRef,
    val blend: org.graphiks.kanvas.gpu.plan.BlendPlan,
    val commonProgram: GPUPreparedMaterialProgram? = null,
) {
    init { table.entry(ref) }
}

/** Exact immutable clip decision retained by the prepared draw. */
internal data class GPUPreparedVerticesClipSnapshot(
    val identity: String,
    val coveragePlan: GPUClipCoveragePlan,
    val scissorBounds: GPUBounds?,
)

/** Closed accountability record for canonical refusal codes not emitted by this pure phase. */
internal enum class GPUPreparedVerticesRefusalDisposition { Direct, Delegated, Reserved }

internal data class GPUPreparedVerticesRefusalClassification(
    val disposition: GPUPreparedVerticesRefusalDisposition,
    val authority: String,
    val reason: String,
)

internal object GPUPreparedVerticesRefusalCoverage {
    val classifications: Map<String, GPUPreparedVerticesRefusalClassification> =
        Collections.unmodifiableMap(linkedMapOf(
            org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes.Topology to c(GPUPreparedVerticesRefusalDisposition.Reserved, "GPUPreparedVerticesPacker", "public VertexMode is closed"),
            org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes.PositionCount to c(GPUPreparedVerticesRefusalDisposition.Delegated, "GPUPreparedVerticesPacker", "empty or odd public position payload"),
            org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes.AttributeCount to c(GPUPreparedVerticesRefusalDisposition.Delegated, "GPUPreparedVerticesPacker", "public attributes"),
            org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes.NonFinite to c(GPUPreparedVerticesRefusalDisposition.Delegated, "GPUPreparedVerticesPacker", "public positions and uvs"),
            org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes.IndexOutOfRange to c(GPUPreparedVerticesRefusalDisposition.Delegated, "GPUPreparedVerticesPacker", "public indices"),
            org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes.IndexFormat to c(GPUPreparedVerticesRefusalDisposition.Delegated, "GPUPreparedVerticesPacker", "uint32 capability"),
            org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes.AttributeLayout to c(GPUPreparedVerticesRefusalDisposition.Reserved, "GPUPreparedVerticesPacker", "lowerer derives only closed layouts"),
            org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes.Transform to c(GPUPreparedVerticesRefusalDisposition.Direct, "GPUPreparedVerticesLowerer", "affine snapshot"),
            org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes.ColorConversion to c(GPUPreparedVerticesRefusalDisposition.Reserved, "GPUColorConversionPlan", "no vertex color-space authority"),
            org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes.PrimitiveBlender to c(GPUPreparedVerticesRefusalDisposition.Reserved, "GPUBlendPlanner", "fixed SrcOver primitive request is admitted"),
            org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes.Material to c(GPUPreparedVerticesRefusalDisposition.Direct, "GPUPreparedMaterialProgramCompiler", "paint material"),
            org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes.Budget to c(GPUPreparedVerticesRefusalDisposition.Delegated, "GPUPreparedVerticesPacker", "fixed public packing ceiling"),
            org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes.ClipCoverage to c(GPUPreparedVerticesRefusalDisposition.Direct, "GPUClipMapper", "mask and analytic clip plans"),
            org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes.MeshBounds to c(GPUPreparedVerticesRefusalDisposition.Direct, "GPUPreparedVerticesLowerer", "mesh bounds"),
            org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes.MeshProgramUnregistered to c(GPUPreparedVerticesRefusalDisposition.Direct, "KanvasPreparedRuntimeEffectResolver", "descriptor registry"),
            org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes.MeshProgramCpuUnavailable to c(GPUPreparedVerticesRefusalDisposition.Direct, "KanvasPreparedRuntimeEffectResolver", "cpu authority"),
            org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes.MeshProgramWgslUnavailable to c(GPUPreparedVerticesRefusalDisposition.Direct, "KanvasPreparedRuntimeEffectResolver", "wgsl authority"),
            org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes.MeshProgramWgslValidation to c(GPUPreparedVerticesRefusalDisposition.Direct, "KanvasPreparedRuntimeEffectResolver", "registered WGSL validation"),
            org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes.MeshProgramAbi to c(GPUPreparedVerticesRefusalDisposition.Direct, "GPUPreparedMaterialProgramCompiler", "uniform and abi"),
            org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes.MeshProgramChild to c(GPUPreparedVerticesRefusalDisposition.Direct, "GPUMaterialMapper", "typed child"),
            org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes.MeshProgramResource to c(GPUPreparedVerticesRefusalDisposition.Reserved, "GPUPreparedMaterialProgramCompiler", "sampled resource route"),
            org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes.MeshBudget to c(GPUPreparedVerticesRefusalDisposition.Reserved, "PreparedVerticesFrameInventory", "frame mesh budget"),
        ))

    private fun c(
        disposition: GPUPreparedVerticesRefusalDisposition,
        authority: String,
        reason: String,
    ) = GPUPreparedVerticesRefusalClassification(disposition, authority, reason)
}

internal interface GPUPreparedVerticesGeometryInput {
    val artifact: GPUPreparedVerticesUploadArtifact
    val operationKind: GPUPreparedVerticesOperationKind
    val operationIndex: Int
    val culledByClip: Boolean
}

/** Actual packed geometry and clip only. Material is bound after the common source publication. */
internal class GPUPreparedVerticesGeometry(
    override val artifact: GPUPreparedVerticesUploadArtifact,
    override val operationKind: GPUPreparedVerticesOperationKind,
    override val operationIndex: Int,
    val transform: Matrix3x3F32,
    val clip: ClipStack,
    val clipSnapshot: GPUPreparedVerticesClipSnapshot,
    val sourceBounds: GPUPreparedVerticesFloatBounds,
    val deviceBounds: GPUBounds,
    val clippedBounds: GPUBounds?,
    val meshBounds: RectF32?,
    val provenance: String,
    val primitiveColorPresent: Boolean,
) : GPUPreparedVerticesGeometryInput {
    override val culledByClip get() = clipSnapshot.scissorBounds != null && clippedBounds == null

    fun bind(paint: org.graphiks.kanvas.paint.Paint, operationBlendMode: org.graphiks.kanvas.paint.BlendMode?,
        targetColorFormat: String, plan: GPUPreparedVerticesMaterialPlan): GPUPreparedVerticesDraw {
        val program = requireNotNull(plan.commonProgram)
        val alpha = org.graphiks.kanvas.gpu.renderer.passes.GPUSourceAlphaClassification.Translucent
        val finalBlend = paint.blendMode.toGpuBlendFacts().copy(sourceAlpha = alpha)
        val primitive = if (!primitiveColorPresent) null else GPUPrimitiveBlendPlan(
            (operationBlendMode ?: org.graphiks.kanvas.paint.BlendMode.MODULATE).toGpuBlendFacts()
                .copy(sourceAlpha = alpha).canonicalBlendPlan(
                    org.graphiks.kanvas.gpu.renderer.passes.GPUCoverageConsumption.FullOrScissor, targetColorFormat))
        return GPUPreparedVerticesDraw.create(artifact, operationKind, program, plan,
            GPUPreparedVerticesMaterialPlanEmission.common(program), transform, clip, clipSnapshot,
            finalBlend, org.graphiks.kanvas.gpu.renderer.planning.W5bBlendPlanLowerer.lowerForRecording(plan.blend),
            sourceBounds, deviceBounds, clippedBounds, culledByClip, meshBounds, operationIndex,
            provenance, 1, primitiveColorPresent, primitive)
    }
}

/**
 * Immutable result of pure vertices/mesh lowering. It deliberately contains no
 * WebGPU objects, upload offsets, cache references, or native allocation state.
 */
internal class GPUPreparedVerticesDraw private constructor(
    override val artifact: GPUPreparedVerticesUploadArtifact,
    override val operationKind: GPUPreparedVerticesOperationKind,
    val material: GPUPreparedMaterialProgram,
    val materialPlan: GPUPreparedVerticesMaterialPlan?,
    val materialPlanEmission: GPUPreparedVerticesMaterialPlanEmission?,
    val transform: Matrix3x3F32,
    clip: ClipStack,
    val clipSnapshot: GPUPreparedVerticesClipSnapshot,
    val finalBlend: GPUBlendFacts,
    val blendPlan: GPUBlendPlan,
    val sourceBounds: GPUPreparedVerticesFloatBounds,
    val deviceBounds: GPUBounds,
    /** Null means the draw is wholly clipped and can be culled without restoring device bounds. */
    val clippedBounds: GPUBounds?,
    override val culledByClip: Boolean,
    meshBounds: RectF32?,
    override val operationIndex: Int,
    val provenance: String,
    val paintAlphaApplicationCount: Int,
    val primitiveColorPresent: Boolean,
    val primitiveBlendPlan: GPUPrimitiveBlendPlan?,
) : GPUPreparedVerticesGeometryInput {
    private val clipState = clip.snapshotForPreparedText()
    private val meshBoundsSnapshot = meshBounds?.copy()

    init {
        require(operationIndex >= 0) { "Prepared vertices operationIndex must be non-negative" }
        require(provenance.isNotBlank()) { "Prepared vertices provenance must not be blank" }
        require(paintAlphaApplicationCount == 1) {
            "Prepared vertices paint alpha must be applied exactly once"
        }
        require((materialPlan == null) == (materialPlanEmission == null)) {
            "Prepared vertices W5a plan and compiler emission must be paired"
        }
    }

    /** Returns a fresh deep clip copy, so no mutable clip shape escapes. */
    val clip: ClipStack
        get() = clipState.snapshotForPreparedText()

    /** Returns a fresh copy of mesh bounds when this was a MeshProgram draw. */
    val meshBounds: RectF32?
        get() = meshBoundsSnapshot?.copy()

    companion object {
        @JvmSynthetic
        internal fun create(
            artifact: GPUPreparedVerticesUploadArtifact,
            operationKind: GPUPreparedVerticesOperationKind,
            material: GPUPreparedMaterialProgram,
            materialPlan: GPUPreparedVerticesMaterialPlan? = null,
            materialPlanEmission: GPUPreparedVerticesMaterialPlanEmission? = null,
            transform: Matrix3x3F32,
            clip: ClipStack,
            clipSnapshot: GPUPreparedVerticesClipSnapshot,
            finalBlend: GPUBlendFacts,
            blendPlan: GPUBlendPlan,
            sourceBounds: GPUPreparedVerticesFloatBounds,
            deviceBounds: GPUBounds,
            clippedBounds: GPUBounds?,
            culledByClip: Boolean,
            meshBounds: RectF32?,
            operationIndex: Int,
            provenance: String,
            paintAlphaApplicationCount: Int,
            primitiveColorPresent: Boolean,
            primitiveBlendPlan: GPUPrimitiveBlendPlan?,
        ): GPUPreparedVerticesDraw = GPUPreparedVerticesDraw(
            artifact = artifact,
            operationKind = operationKind,
            material = material,
            materialPlan = materialPlan,
            materialPlanEmission = materialPlanEmission,
            transform = Matrix3x3F32.of(
                transform.sx, transform.kx, transform.tx,
                transform.ky, transform.sy, transform.ty,
                transform.persp0, transform.persp1, transform.persp2,
            ),
            clip = clip,
            clipSnapshot = clipSnapshot,
            finalBlend = finalBlend,
            blendPlan = blendPlan,
            sourceBounds = sourceBounds.copy(),
            deviceBounds = deviceBounds,
            clippedBounds = clippedBounds,
            culledByClip = culledByClip,
            meshBounds = meshBounds,
            operationIndex = operationIndex,
            provenance = provenance,
            paintAlphaApplicationCount = paintAlphaApplicationCount,
            primitiveColorPresent = primitiveColorPresent,
            primitiveBlendPlan = primitiveBlendPlan,
        )
    }
}

/** One terminal result, published only after every lowering authority succeeds. */
internal sealed interface GPUPreparedVerticesLowering {
    /** Actual packer/transform/clip admission with no material program or authority. */
    data class GeometryReady(val geometry: GPUPreparedVerticesGeometry) : GPUPreparedVerticesLowering
    @ConsistentCopyVisibility
    data class Ready internal constructor(val draw: GPUPreparedVerticesDraw) : GPUPreparedVerticesLowering

    class Refused internal constructor(
        val code: String,
        val operationIndex: Int,
        facts: Map<String, String>,
    ) : GPUPreparedVerticesLowering {
        val facts: Map<String, String> = Collections.unmodifiableMap(LinkedHashMap(facts))
    }
}
