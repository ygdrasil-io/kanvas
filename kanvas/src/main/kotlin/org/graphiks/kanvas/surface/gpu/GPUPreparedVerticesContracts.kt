package org.graphiks.kanvas.surface.gpu

import java.util.Collections
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedVerticesUploadArtifact
import org.graphiks.kanvas.gpu.renderer.clips.GPUBounds
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgram
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.commands.GPUBlendFacts
import org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesFloatBounds
import org.graphiks.kanvas.gpu.renderer.vertices.GPUPrimitiveBlendPlan
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

/** The public operation semantic retained by one handle-free prepared vertices draw. */
enum class GPUPreparedVerticesOperationKind { DrawVertices, DrawMesh }

/** Exact immutable clip decision retained by the prepared draw. */
data class GPUPreparedVerticesClipSnapshot(
    val identity: String,
    val coveragePlan: GPUClipCoveragePlan,
    val scissorBounds: GPUBounds?,
)

/** Closed accountability record for canonical refusal codes not emitted by this pure phase. */
enum class GPUPreparedVerticesRefusalDisposition { Direct, Delegated, Reserved }

data class GPUPreparedVerticesRefusalClassification(
    val disposition: GPUPreparedVerticesRefusalDisposition,
    val authority: String,
    val reason: String,
)

object GPUPreparedVerticesRefusalCoverage {
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

/**
 * Immutable result of pure vertices/mesh lowering. It deliberately contains no
 * WebGPU objects, upload offsets, cache references, or native allocation state.
 */
class GPUPreparedVerticesDraw private constructor(
    val artifact: GPUPreparedVerticesUploadArtifact,
    val operationKind: GPUPreparedVerticesOperationKind,
    val material: GPUPreparedMaterialProgram,
    val transform: Matrix33,
    clip: ClipStack,
    val clipSnapshot: GPUPreparedVerticesClipSnapshot,
    val finalBlend: GPUBlendFacts,
    val blendPlan: GPUBlendPlan,
    val sourceBounds: GPUPreparedVerticesFloatBounds,
    val deviceBounds: GPUBounds,
    /** Null means the draw is wholly clipped and can be culled without restoring device bounds. */
    val clippedBounds: GPUBounds?,
    val culledByClip: Boolean,
    meshBounds: Rect?,
    val operationIndex: Int,
    val provenance: String,
    val paintAlphaApplicationCount: Int,
    val primitiveColorPresent: Boolean,
    val primitiveBlendPlan: GPUPrimitiveBlendPlan?,
) {
    private val clipState = clip.snapshotForPreparedText()
    private val meshBoundsSnapshot = meshBounds?.copy()

    init {
        require(operationIndex >= 0) { "Prepared vertices operationIndex must be non-negative" }
        require(provenance.isNotBlank()) { "Prepared vertices provenance must not be blank" }
        require(paintAlphaApplicationCount == 1) {
            "Prepared vertices paint alpha must be applied exactly once"
        }
    }

    /** Returns a fresh deep clip copy, so no mutable clip shape escapes. */
    val clip: ClipStack
        get() = clipState.snapshotForPreparedText()

    /** Returns a fresh copy of mesh bounds when this was a MeshProgram draw. */
    val meshBounds: Rect?
        get() = meshBoundsSnapshot?.copy()

    companion object {
        @JvmSynthetic
        internal fun create(
            artifact: GPUPreparedVerticesUploadArtifact,
            operationKind: GPUPreparedVerticesOperationKind,
            material: GPUPreparedMaterialProgram,
            transform: Matrix33,
            clip: ClipStack,
            clipSnapshot: GPUPreparedVerticesClipSnapshot,
            finalBlend: GPUBlendFacts,
            blendPlan: GPUBlendPlan,
            sourceBounds: GPUPreparedVerticesFloatBounds,
            deviceBounds: GPUBounds,
            clippedBounds: GPUBounds?,
            culledByClip: Boolean,
            meshBounds: Rect?,
            operationIndex: Int,
            provenance: String,
            paintAlphaApplicationCount: Int,
            primitiveColorPresent: Boolean,
            primitiveBlendPlan: GPUPrimitiveBlendPlan?,
        ): GPUPreparedVerticesDraw = GPUPreparedVerticesDraw(
            artifact = artifact,
            operationKind = operationKind,
            material = material,
            transform = Matrix33.makeAll(
                transform.scaleX, transform.skewX, transform.transX,
                transform.skewY, transform.scaleY, transform.transY,
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
sealed interface GPUPreparedVerticesLowering {
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
