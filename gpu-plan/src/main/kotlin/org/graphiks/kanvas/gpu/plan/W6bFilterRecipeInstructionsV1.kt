package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.CapturedDropShadowModeV1
import org.graphiks.kanvas.render.ir.CapturedFilterNodeIdI32
import org.graphiks.math.geometry.Point2I32
import org.graphiks.math.geometry.RectF64
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.SizeI32

internal class W6bRecipeKeyV1(
    val nodeId: CapturedFilterNodeIdI32?,
    val maskOccurrenceI32: Int?,
    val boundSource: W6bRecipeSourceV1,
    desired: RectI32,
    val pictureProvenance: PictureFilterEvaluationProvenanceV1?,
) {
    private val desired = desired.copy()
    fun lower(source: W6bFilterGraphConstruction.SourceBinding): FilterEvaluationKeyV1 =
        nodeId?.let { FilterEvaluationKeyV1.of(it, source.resourceId, boundSource.mapping, desired,
            sourceRevisionIdentity = source.sourceRevisionIdentity, pictureProvenance = pictureProvenance) }
            ?: FilterEvaluationKeyV1.forMaskOccurrence(requireNotNull(maskOccurrenceI32), source.resourceId,
                boundSource.mapping, desired, sourceRevisionIdentity = source.sourceRevisionIdentity)
}

internal class W6bPreparedPictureSourceV1(
    val draft: PictureStreamAggregateDraftV1,
    val source: W6bSourceGeometryV1,
    content: RectF64,
) {
    private val content = content.copy()
    fun copyContentDeviceF64(): RectF64 = content.copy()
}

/** Only resource substitution remains deferred; family, geometry and sampling are already sealed. */
internal sealed interface W6bRecipeOperationV1 {
    class Fixed(val operation: FilterPassOperationV1) : W6bRecipeOperationV1
    class ShadowComposite(
        val mode: CapturedDropShadowModeV1,
        val original: W6bRecipeSymbolV1,
        val bounds: FilterBoundsPlanV1,
        val shadowOffset: Point2I32,
        val originalOffset: Point2I32,
    ) : W6bRecipeOperationV1
    class Picture(
        val source: W6bRecipeSymbolV1,
        val bounds: FilterBoundsPlanV1,
        val sampling: W6dPictureSamplingV1,
        val inputSampling: FilterInputSamplingV1,
        val prepared: W6bPreparedPictureSourceV1,
    ) : W6bRecipeOperationV1
}

internal sealed interface W6bRecipeInstructionV1 {
    class Resource(val symbol: W6bRecipeSymbolV1, val role: PlanResourceRole, extent: SizeI32) : W6bRecipeInstructionV1 {
        private val extent = extent.copy()
        fun copyExtentI32(): SizeI32 = extent.copy()
    }
    class Clear(val output: W6bRecipeSymbolV1, val reference: W6bRecipeSymbolV1) : W6bRecipeInstructionV1
    class PictureSource(val output: W6bRecipeSourceV1, val context: W6bRecipeSymbolV1,
        val prepared: W6bPreparedPictureSourceV1) : W6bRecipeInstructionV1
    class Pass(inputs: List<W6bRecipeSymbolV1>, val output: W6bRecipeSymbolV1,
        val key: W6bRecipeKeyV1, val operation: W6bRecipeOperationV1) : W6bRecipeInstructionV1 {
        val inputs = immutableList(inputs)
    }
}
