package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.CapturedFilterInputV1
import org.graphiks.kanvas.render.ir.CapturedFilterNodeIdI32
import org.graphiks.kanvas.render.ir.CapturedFilterNodeV1
import org.graphiks.math.geometry.Point2I32
import org.graphiks.math.geometry.RectF64
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.expandForBlurF64OrNull
import org.graphiks.math.geometry.intersectF64OrNull
import org.graphiks.math.geometry.roundOutToRectI32OrNull
import org.graphiks.math.matrix.LayerMappingF64

/** Immutable pre-allocation evaluation facts for exactly one captured W6 occurrence. */
internal class W6bFilterSourceFactsV1(
    sourceDomainDeviceI32: RectI32,
    knownContentDeviceI32: RectI32?,
    desiredOutputDeviceI32: RectI32,
    val mapping: LayerMappingF64,
) {
    private val sourceDomain = sourceDomainDeviceI32.copy()
    private val knownContent = knownContentDeviceI32?.copy()
    private val desiredOutput = desiredOutputDeviceI32.copy()
    fun copySourceDomainDeviceI32(): RectI32 = sourceDomain.copy()
    fun copyKnownContentDeviceI32(): RectI32? = knownContent?.copy()
    fun copyDesiredOutputDeviceI32(): RectI32 = desiredOutput.copy()
}

internal class W6bEvaluatedFilterRecipeV1(requiredInputDeviceI32: RectI32?, producedOutputDeviceI32: RectI32?) {
    private val requiredInput = requiredInputDeviceI32?.copy()
    private val producedOutput = producedOutputDeviceI32?.copy()
    fun copyRequiredInputDeviceI32(): RectI32? = requiredInput?.copy()
    fun copyProducedOutputDeviceI32(): RectI32? = producedOutput?.copy()
}

/** Semantic recipe: it owns neither resources nor pass ids, only frozen captured topology. */
internal class W6bContextualFilterRecipeV1 internal constructor(
    private val occurrence: W6bFilterGraphConstruction.PositiveOccurrence,
    private val desiredOutputDeviceI32: RectI32,
    private val mapping: LayerMappingF64,
) {
    fun copyRequiredInputDeviceI32(): RectI32? = W6bFilterGraphConstruction.reverseInputDemand(
        occurrence, desiredOutputDeviceI32, mapping,
    )

    fun evaluate(source: W6bFilterSourceFactsV1, runtimeCatalog: RuntimeEffectSemanticCatalogSnapshot): W6bEvaluatedFilterRecipeV1 {
        // Runtime validation remains the existing W6b lowering authority.  Recipe geometry is
        // intentionally resource-free, and all of its geometric operations reuse W6c/W6b helpers.
        @Suppress("UNUSED_VARIABLE") val catalog = runtimeCatalog
        lateinit var input: (CapturedFilterInputV1, RectI32?) -> RectI32?
        lateinit var node: (CapturedFilterNodeIdI32, RectI32?) -> RectI32?
        input = { value, current -> if (value is CapturedFilterInputV1.Node) node(value.id, current) else current }
        node = { id, current -> when (val value = occurrence.table.nodeAt(id)) {
            is CapturedFilterNodeV1.Offset -> W6cSpatialBoundsPlanner.producedOutputBounds(value, input(value.input, current), source.mapping)
            is CapturedFilterNodeV1.Crop -> W6cSpatialBoundsPlanner.producedOutputBounds(value, input(value.input, current), source.mapping)
            is CapturedFilterNodeV1.Tile -> W6cSpatialBoundsPlanner.producedOutputBounds(value, input(value.input, current), source.mapping)
            is CapturedFilterNodeV1.Blur -> input(value.input, current)?.let { region -> RectF64(region.left.toDouble(), region.top.toDouble(),
                region.right.toDouble(), region.bottom.toDouble()).expandForBlurF64OrNull(value.sigmaX, value.sigmaY)?.roundOutToRectI32OrNull() }
            is CapturedFilterNodeV1.Compose -> input(value.outer, input(value.inner, current))
            is CapturedFilterNodeV1.ColorFilter -> input(value.input, current)
            is CapturedFilterNodeV1.DistantLitDiffuse, is CapturedFilterNodeV1.PointLitDiffuse, is CapturedFilterNodeV1.SpotLitDiffuse,
            is CapturedFilterNodeV1.DistantLitSpecular, is CapturedFilterNodeV1.PointLitSpecular, is CapturedFilterNodeV1.SpotLitSpecular -> source.copyDesiredOutputDeviceI32()
            else -> current
        } }
        val produced = occurrence.root?.let { node(it.id, source.copyKnownContentDeviceI32()) }
            ?.let { output -> intersect(output, source.copyDesiredOutputDeviceI32()) }
        return W6bEvaluatedFilterRecipeV1(copyRequiredInputDeviceI32(), produced)
    }

    private fun intersect(first: RectI32, second: RectI32): RectI32? = RectF64(first.left.toDouble(), first.top.toDouble(),
        first.right.toDouble(), first.bottom.toDouble()).intersectF64OrNull(RectF64(second.left.toDouble(), second.top.toDouble(),
        second.right.toDouble(), second.bottom.toDouble()))?.roundOutToRectI32OrNull()
}

internal fun W6bFilterGraphConstruction.bindOccurrenceRecipe(
    occurrence: W6bFilterGraphConstruction.PositiveOccurrence,
    desiredOutputDeviceI32: RectI32,
    mapping: LayerMappingF64,
): W6bContextualFilterRecipeV1 = W6bContextualFilterRecipeV1(occurrence, desiredOutputDeviceI32, mapping)
