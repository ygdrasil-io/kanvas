package org.graphiks.kanvas.surface.gpu

import java.util.Collections
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedVerticesUploadArtifact
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgram
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.commands.GPUBlendFacts
import org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesFloatBounds
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

/** The public operation semantic retained by one handle-free prepared vertices draw. */
enum class GPUPreparedVerticesOperationKind { DrawVertices, DrawMesh }

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
    val finalBlend: GPUBlendFacts,
    val blendPlan: GPUBlendPlan,
    val sourceBounds: GPUPreparedVerticesFloatBounds,
    meshBounds: Rect?,
    val operationIndex: Int,
    val provenance: String,
    val paintAlphaApplicationCount: Int,
    val primitiveColorPresent: Boolean,
) {
    private val clipSnapshot = clip.snapshotForPreparedText()
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
        get() = clipSnapshot.snapshotForPreparedText()

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
            finalBlend: GPUBlendFacts,
            blendPlan: GPUBlendPlan,
            sourceBounds: GPUPreparedVerticesFloatBounds,
            meshBounds: Rect?,
            operationIndex: Int,
            provenance: String,
            paintAlphaApplicationCount: Int,
            primitiveColorPresent: Boolean,
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
            finalBlend = finalBlend,
            blendPlan = blendPlan,
            sourceBounds = sourceBounds.copy(),
            meshBounds = meshBounds,
            operationIndex = operationIndex,
            provenance = provenance,
            paintAlphaApplicationCount = paintAlphaApplicationCount,
            primitiveColorPresent = primitiveColorPresent,
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
