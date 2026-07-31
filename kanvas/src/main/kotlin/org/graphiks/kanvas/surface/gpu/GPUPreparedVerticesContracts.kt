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
data class GPUPreparedVerticesRefusalClassification(
    val phase: String,
    val authority: String,
    val reason: String,
)

object GPUPreparedVerticesRefusalCoverage {
    val classifications: Map<String, GPUPreparedVerticesRefusalClassification> =
        Collections.unmodifiableMap(linkedMapOf(
            org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes.Topology to c("task5", "GPUPreparedVerticesPacker", "public geometry"),
            org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes.PositionCount to c("task5", "GPUPreparedVerticesPacker", "public geometry"),
            org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes.AttributeCount to c("task5", "GPUPreparedVerticesPacker", "public attributes"),
            org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes.NonFinite to c("task5", "GPUPreparedVerticesPacker", "public positions and uvs"),
            org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes.IndexOutOfRange to c("task5", "GPUPreparedVerticesPacker", "public indices"),
            org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes.IndexFormat to c("task5", "GPUPreparedVerticesPacker", "uint32 capability"),
            org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes.AttributeLayout to c("task5", "GPUPreparedVerticesPacker", "closed packer layout"),
            org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes.Transform to c("task5", "GPUPreparedVerticesLowerer", "affine snapshot"),
            org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes.ColorConversion to c("task6", "GPUColorConversionPlan", "no vertex color-space authority"),
            org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes.PrimitiveBlender to c("task5", "GPUBlendPlanner", "primitive blend admission"),
            org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes.Material to c("task5", "GPUPreparedMaterialProgramCompiler", "paint material"),
            org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes.Budget to c("task5", "GPUPreparedVerticesPacker", "packing limits"),
            org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes.MeshBounds to c("task5", "GPUPreparedVerticesLowerer", "mesh bounds"),
            org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes.MeshProgramUnregistered to c("task5", "KanvasPreparedRuntimeEffectResolver", "descriptor registry"),
            org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes.MeshProgramCpuUnavailable to c("task5", "KanvasPreparedRuntimeEffectResolver", "cpu authority"),
            org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes.MeshProgramWgslUnavailable to c("task5", "KanvasPreparedRuntimeEffectResolver", "wgsl authority"),
            org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes.MeshProgramWgslValidation to c("task5", "GPUPreparedMaterialProgramCompiler", "wgsl validation"),
            org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes.MeshProgramAbi to c("task5", "GPUPreparedMaterialProgramCompiler", "uniform and abi"),
            org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes.MeshProgramChild to c("task5", "GPUMaterialMapper", "typed child"),
            org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes.MeshProgramResource to c("task5", "GPUPreparedMaterialProgramCompiler", "sampled resource"),
            org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes.MeshBudget to c("task6", "PreparedVerticesFrameInventory", "frame mesh budget"),
        ))

    private fun c(phase: String, authority: String, reason: String) =
        GPUPreparedVerticesRefusalClassification(phase, authority, reason)
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
    val clippedBounds: GPUBounds,
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
            clippedBounds: GPUBounds,
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
