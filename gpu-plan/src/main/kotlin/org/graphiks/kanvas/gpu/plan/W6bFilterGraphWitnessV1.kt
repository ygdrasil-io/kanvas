package org.graphiks.kanvas.gpu.plan

import java.util.IdentityHashMap

/**
 * Immutable publication witness for W6b occurrence ownership.  It is intentionally graph-owned:
 * individual [PlanPass.FilterPass] values cannot establish that their contextual source, immediate
 * chain, and terminal parent composite all refer to the same captured occurrence.
 */
internal class W6bFilterGraphWitnessV1 private constructor(
    occurrences: List<Occurrence>,
) {
    internal class Occurrence(
        val boundSourceId: PlanResourceId,
        val firstPassIndexI32: Int,
        val terminalPassIndexI32: Int,
        val compositePassIndexI32: Int,
    )

    private val occurrencesSnapshot = immutableList(occurrences)
    internal fun occurrences(): List<Occurrence> = occurrencesSnapshot

    internal companion object {
        fun seal(resources: List<PlanResource>, passes: List<PlanPass>): W6bFilterGraphWitnessV1 {
            val byId = resources.associateBy { it.id }
            val producers = mutableMapOf<PlanResourceId, Int>()
            val outputOwner = mutableMapOf<PlanResourceId, PlanResourceId>()
            val contexts = IdentityHashMap<FilterEvaluationKeyV1, Context>()
            val filterOutputs = linkedSetOf<PlanResourceId>()
            val filterInputs = linkedSetOf<PlanResourceId>()
            val occurrences = mutableListOf<Occurrence>()

            fun producerFor(id: PlanResourceId, before: Int): Int {
                val producer = requireNotNull(producers[id]) { "W6b input has no immutable producer." }
                require(producer < before) { "W6b input must be produced before its consumer." }
                return producer
            }
            fun filterArity(operation: FilterPassOperationV1): Int = when (operation) {
                is FilterPassOperationV1.DropShadowComposite -> 2
                is FilterPassOperationV1.SeparableBlur,
                is FilterPassOperationV1.MaskBlurStyle,
                is FilterPassOperationV1.MaskShader,
                is FilterPassOperationV1.MaskTable,
                is FilterPassOperationV1.DropShadowColorize,
                -> 1
            }

            passes.forEachIndexed { indexI32, pass ->
                when (pass) {
                    is PlanPass.RenderPass -> producers[pass.target] = indexI32
                    is PlanPass.TextureCopy -> producers[pass.destination] = indexI32
                    is PlanPass.FilterSourceClear -> {
                        val source = requireNotNull(byId[pass.boundSourceId]) {
                            "W6b transparent-black input has no immutable occurrence source."
                        }
                        require(source.role == PlanResourceRole.FilterSource)
                        producerFor(source.id, indexI32)
                        producers[pass.output] = indexI32
                        outputOwner[pass.output] = source.id
                    }
                    is PlanPass.PictureSourcePass -> producers[pass.output] = indexI32
                    is PlanPass.FilterPass -> {
                        val source = requireNotNull(byId[pass.evaluationKey.boundSourceId]) {
                            "W6b occurrence source is absent from the published resource table."
                        }
                        require(source.role in setOf(PlanResourceRole.FilterSource, PlanResourceRole.LayerTarget)) {
                            "W6b occurrence source must be one immutable source generation or layer target."
                        }
                        producerFor(source.id, indexI32)
                        val inputs = pass.inputs()
                        require(inputs.size == filterArity(pass.operation)) { "W6b filter operation input arity is invalid." }
                        inputs.forEach { input -> producerFor(input, indexI32) }
                        val context = contexts[pass.evaluationKey]
                        if (context == null) {
                            val firstInput = inputs.first()
                            require(firstInput == source.id || outputOwner[firstInput] == source.id) {
                                "W6b first filter input is not continuous with its occurrence source."
                            }
                            contexts[pass.evaluationKey] = Context(source.id, pass.output, indexI32, pass.operation)
                        } else {
                            require(context.boundSourceId == source.id && inputs.first() == context.lastOutput) {
                                "W6b downstream filter input must consume the preceding same-key output."
                            }
                            if (pass.operation is FilterPassOperationV1.SeparableBlur &&
                                pass.operation.axis == FilterAxisV1.Y) {
                                val previous = context.lastOperation as? FilterPassOperationV1.SeparableBlur
                                require(previous?.axis == FilterAxisV1.X) {
                                    "W6b vertical blur must consume the matching horizontal blur output."
                                }
                            }
                            context.lastOutput = pass.output
                            context.lastPassIndexI32 = indexI32
                            context.lastOperation = pass.operation
                        }
                        filterInputs += inputs
                        filterOutputs += pass.output
                        outputOwner[pass.output] = source.id
                        producers[pass.output] = indexI32
                    }
                    else -> Unit
                }
            }

            val compositeBySource = mutableMapOf<PlanResourceId, Pair<Int, PlanPass.FilterComposite>>()
            passes.forEachIndexed { indexI32, pass -> if (pass is PlanPass.FilterComposite) {
                require(byId.getValue(pass.source).role == PlanResourceRole.FilterTarget &&
                    byId.getValue(pass.destination).role in setOf(
                        PlanResourceRole.LogicalTarget,
                        PlanResourceRole.LayerTarget,
                        PlanResourceRole.FilterSource,
                    )) { "W6b composite has an invalid source or parent target." }
                require(compositeBySource.put(pass.source, indexI32 to pass) == null) {
                    "W6b terminal filter output has more than one parent composite."
                }
            } }
            val terminals = filterOutputs - filterInputs
            require(terminals.isNotEmpty() || filterOutputs.isEmpty()) { "W6b graph has no terminal filter output." }
            terminals.forEach { terminal ->
                val (compositeIndexI32, composite) = requireNotNull(compositeBySource[terminal]) {
                    "W6b terminal filter output must be consumed by its typed parent composite."
                }
                val producerIndexI32 = requireNotNull(producers[terminal])
                require(compositeIndexI32 == producerIndexI32 + 1 && composite.evaluationKey ===
                    passes[producerIndexI32].let { it as PlanPass.FilterPass }.evaluationKey) {
                    "W6b terminal filter output must composite immediately with the same occurrence key."
                }
            }
            contexts.forEach { (key, context) ->
                // An image-filter root may feed a separately keyed mask occurrence. Only the
                // terminal value of the complete occurrence graph composites into its parent;
                // an intermediate evaluation must instead have a real downstream use.
                if (context.lastOutput in terminals) {
                    val composite = requireNotNull(compositeBySource[context.lastOutput]) {
                        "W6b occurrence terminal output is unconsumed."
                    }
                    occurrences += Occurrence(key.boundSourceId, context.firstPassIndexI32, context.lastPassIndexI32, composite.first)
                } else {
                    require(context.lastOutput in filterInputs) {
                        "W6b non-terminal filter output is not consumed by a downstream evaluation."
                    }
                }
            }
            return W6bFilterGraphWitnessV1(occurrences)
        }
    }

    private class Context(
        val boundSourceId: PlanResourceId,
        var lastOutput: PlanResourceId,
        val firstPassIndexI32: Int,
        var lastOperation: FilterPassOperationV1,
    ) {
        var lastPassIndexI32: Int = firstPassIndexI32
    }
}
