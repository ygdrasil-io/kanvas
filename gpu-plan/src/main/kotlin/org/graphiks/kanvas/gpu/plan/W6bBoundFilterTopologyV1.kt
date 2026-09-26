package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.CapturedFilterInputV1
import org.graphiks.kanvas.render.ir.CapturedFilterNodeIdI32
import org.graphiks.kanvas.render.ir.CapturedFilterNodeV1

/** Contextual references are occurrence-local symbols, never resource IDs or value-cache keys. */
internal sealed interface W6bFilterReferenceV1 {
    data object Source : W6bFilterReferenceV1
    class Transparent(val context: W6bFilterReferenceV1) : W6bFilterReferenceV1
    class Result(val operation: W6bBoundFilterOperationV1) : W6bFilterReferenceV1
}

internal class W6bBoundFilterOperationV1(
    val id: CapturedFilterNodeIdI32,
    val node: CapturedFilterNodeV1,
    val boundSource: W6bFilterReferenceV1,
    inputs: List<W6bFilterReferenceV1>,
) {
    val inputs: List<W6bFilterReferenceV1> = immutableList(inputs)
}

/**
 * The captured table is read only here. Compose changes the source binding, while each ordered
 * input is bound separately, including repeated references to the very same captured node.
 */
internal class W6bBoundFilterTopologyV1(occurrence: W6bFilterGraphConstruction.PositiveOccurrence) {
    val operations: List<W6bBoundFilterOperationV1>
    val terminal: W6bFilterReferenceV1

    /** Wrapper classifications follow the same contextual edges as demand and evaluation. */
    fun hasTerminalFamily(predicate: (CapturedFilterNodeV1) -> Boolean): Boolean {
        val classified = java.util.IdentityHashMap<W6bBoundFilterOperationV1, Boolean>()
        fun matches(reference: W6bFilterReferenceV1): Boolean = when (reference) {
            is W6bFilterReferenceV1.Result -> classified[reference.operation] == true
            else -> false
        }
        for (operation in operations) {
            val wrapped = when (operation.node) {
                is CapturedFilterNodeV1.ColorFilter, is CapturedFilterNodeV1.Merge,
                is CapturedFilterNodeV1.Blend -> operation.inputs.any(::matches)
                else -> false
            }
            // A non-source binding records Compose's inner result even if outer is a leaf.
            classified[operation] = predicate(operation.node) || wrapped || matches(operation.boundSource)
        }
        return matches(terminal)
    }

    init {
        val ordered = mutableListOf<W6bBoundFilterOperationV1>()
        lateinit var bindNode: (CapturedFilterNodeIdI32, W6bFilterReferenceV1) -> W6bFilterReferenceV1
        fun bindInput(input: CapturedFilterInputV1, source: W6bFilterReferenceV1): W6bFilterReferenceV1 = when (input) {
            CapturedFilterInputV1.ImplicitSource -> source
            CapturedFilterInputV1.TransparentBlack -> W6bFilterReferenceV1.Transparent(source)
            is CapturedFilterInputV1.Node -> bindNode(input.id, source)
            is CapturedFilterInputV1.Picture, is CapturedFilterInputV1.Backdrop -> throw W6bFilterGraphConstruction.ConstructionFailure(
                W6bFilterDiagnostics.refusal(W6bFilterDiagnostics.UnsupportedFamily,
                    "The captured filter input has no admitted contextual binding."))
        }
        bindNode = { id, source ->
            val node = occurrence.table.nodeAt(id)
            if (node is CapturedFilterNodeV1.Compose) {
                bindInput(node.outer, bindInput(node.inner, source))
            } else {
                val capturedInputs: List<CapturedFilterInputV1> = when (node) {
                    is CapturedFilterNodeV1.Crop -> listOf(node.input)
                    is CapturedFilterNodeV1.Offset -> listOf(node.input)
                    is CapturedFilterNodeV1.Tile -> listOf(node.input)
                    is CapturedFilterNodeV1.Blur -> listOf(node.input)
                    is CapturedFilterNodeV1.DropShadow -> listOf(node.input)
                    is CapturedFilterNodeV1.ColorFilter -> listOf(node.input)
                    is CapturedFilterNodeV1.Dilate -> listOf(node.input)
                    is CapturedFilterNodeV1.Erode -> listOf(node.input)
                    is CapturedFilterNodeV1.MatrixConvolution -> listOf(node.input)
                    is CapturedFilterNodeV1.DisplacementMap -> listOf(node.displacement, node.input)
                    is CapturedFilterNodeV1.Magnifier -> listOf(node.input)
                    is CapturedFilterNodeV1.DistantLitDiffuse -> listOf(node.input)
                    is CapturedFilterNodeV1.PointLitDiffuse -> listOf(node.input)
                    is CapturedFilterNodeV1.SpotLitDiffuse -> listOf(node.input)
                    is CapturedFilterNodeV1.DistantLitSpecular -> listOf(node.input)
                    is CapturedFilterNodeV1.PointLitSpecular -> listOf(node.input)
                    is CapturedFilterNodeV1.SpotLitSpecular -> listOf(node.input)
                    is CapturedFilterNodeV1.Merge -> node.toList()
                    is CapturedFilterNodeV1.Blend -> listOf(node.background, node.foreground)
                    is CapturedFilterNodeV1.RuntimeEffect -> listOf(
                        node.firstOrNull { it.name == "input" }?.input ?: CapturedFilterInputV1.ImplicitSource)
                    is CapturedFilterNodeV1.Picture -> emptyList()
                    is CapturedFilterNodeV1.Compose -> error("Compose was bound above.")
                    else -> throw W6bFilterGraphConstruction.ConstructionFailure(W6bFilterDiagnostics.refusal(
                        W6bFilterDiagnostics.UnsupportedFamily, "The captured image-filter family is not admitted."))
                }
                val inputs = capturedInputs.map { bindInput(it, source) }
                val operation = W6bBoundFilterOperationV1(id, node, source, inputs)
                ordered += operation
                W6bFilterReferenceV1.Result(operation)
            }
        }
        terminal = occurrence.root?.let { bindNode(it.id, W6bFilterReferenceV1.Source) } ?: W6bFilterReferenceV1.Source
        operations = immutableList(ordered)
    }
}
