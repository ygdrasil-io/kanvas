package org.graphiks.kanvas.gpu.plan

/**
 * Immutable publication witness for W6b.  It starts from terminal composites and validates
 * immediate producer/consumer edges; grouping convenient pass contexts is not an occurrence
 * proof.
 */
internal class W6bFilterGraphWitnessV1 private constructor(occurrences: List<Occurrence>) {
    internal class Occurrence(
        val boundSourceId: PlanResourceId,
        val firstPassIndexI32: Int,
        val terminalPassIndexI32: Int,
        val compositePassIndexI32: Int,
    )

    private val values = immutableList(occurrences)
    internal fun occurrences(): List<Occurrence> = values

    internal companion object {
        fun seal(resources: List<PlanResource>, passes: List<PlanPass>): W6bFilterGraphWitnessV1 {
            val rows = resources.associateBy { it.id }
            val producers = mutableMapOf<PlanResourceId, Int>()
            val owners = mutableMapOf<PlanResourceId, PlanResourceId>()
            val inputs = mutableSetOf<PlanResourceId>()
            val materialCoverageInputs = mutableSetOf<PlanResourceId>()
            val outputs = mutableSetOf<PlanResourceId>()
            fun row(id: PlanResourceId): PlanResource = requireNotNull(rows[id]) { "W6b resource is absent." }
            fun produced(id: PlanResourceId, before: Int): Int = requireNotNull(producers[id]) {
                "W6b input has no immutable producer."
            }.also { require(it < before) { "W6b input must precede its consumer." } }

            passes.forEachIndexed { index, pass -> when (pass) {
                is PlanPass.RenderPass -> {
                    pass.coverageSource?.let { produced(it, index); materialCoverageInputs += it }
                    producers[pass.target] = index
                }
                is PlanPass.TextureCopy -> producers[pass.destination] = index
                is PlanPass.PictureSourcePass -> producers[pass.output] = index
                is PlanPass.FilterCoverageSourcePass -> {
                    require(row(pass.output).role == PlanResourceRole.CoverageSource)
                    producers[pass.output] = index
                    owners[pass.output] = pass.output
                }
                is PlanPass.FilterCoverageRetainPass -> {
                    require(row(pass.source).role in setOf(PlanResourceRole.CoverageSource, PlanResourceRole.FilterTarget))
                    require(row(pass.output).role == PlanResourceRole.CoverageOriginal)
                    produced(pass.source, index)
                    producers[pass.output] = index
                    owners[pass.output] = requireNotNull(owners[pass.source])
                }
                is PlanPass.FilterSourceClear -> {
                    require(row(pass.boundSourceId).role == PlanResourceRole.FilterSource)
                    require(row(pass.output).role == PlanResourceRole.FilterTransparentBlack)
                    produced(pass.boundSourceId, index)
                    producers[pass.output] = index
                    owners[pass.output] = pass.boundSourceId
                }
                is PlanPass.FilterPass -> {
                    val bound = row(pass.evaluationKey.boundSourceId)
                    require(bound.role in setOf(PlanResourceRole.FilterSource, PlanResourceRole.CoverageSource)) {
                        "W6b occurrence source must be immutable FilterSource or CoverageSource."
                    }
                    produced(bound.id, index)
                    require(row(pass.output).role == PlanResourceRole.FilterTarget)
                    require(pass.inputs().size == arity(pass.operation)) { "W6b operation input arity is invalid." }
                    pass.inputs().forEach { input ->
                        produced(input, index)
                        require(owners[input] == null || owners[input] == bound.id) {
                            "W6b input belongs to another occurrence."
                        }
                    }
                    validatePass(pass, passes, producers, rows, owners)
                    inputs += pass.inputs()
                    outputs += pass.output
                    producers[pass.output] = index
                    owners[pass.output] = bound.id
                }
                else -> Unit
            } }

            val terminals = outputs - inputs - materialCoverageInputs
            val composites = passes.mapIndexedNotNull { index, pass ->
                (pass as? PlanPass.FilterComposite)?.let { index to it }
            }
            val bySource = composites.associateBy({ it.second.source }, { it })
            require(bySource.size == composites.size && bySource.keys == terminals) {
                "Every and only W6b terminal filter output must have one parent composite."
            }
            return W6bFilterGraphWitnessV1(composites.map { (compositeIndex, composite) ->
                val terminalIndex = produced(composite.source, compositeIndex)
                val terminal = passes[terminalIndex] as? PlanPass.FilterPass
                    ?: throw IllegalArgumentException("W6b composite source is not a filter output.")
                require(terminal.evaluationKey === composite.evaluationKey && terminalIndex + 1 == compositeIndex) {
                    "W6b terminal output must immediately composite with the exact evaluation key."
                }
                Occurrence(terminal.evaluationKey.boundSourceId, firstInSameKey(terminalIndex, passes, producers),
                    terminalIndex, compositeIndex)
            })
        }

        private fun arity(operation: FilterPassOperationV1): Int = when (operation) {
            is FilterPassOperationV1.DropShadowComposite ->
                if (operation.mode == org.graphiks.kanvas.render.ir.CapturedDropShadowModeV1.SHADOW_ONLY) 1 else 2
            is FilterPassOperationV1.MaskBlurStyle -> if (operation.originalCoverageSource == null) 1 else 2
            is FilterPassOperationV1.SeparableBlur,
            is FilterPassOperationV1.MaskShader,
            is FilterPassOperationV1.MaskTable,
            is FilterPassOperationV1.MaterializedSource,
            is FilterPassOperationV1.DropShadowColorize,
            -> 1
        }

        private fun validatePass(
            pass: PlanPass.FilterPass,
            passes: List<PlanPass>,
            producers: Map<PlanResourceId, Int>,
            rows: Map<PlanResourceId, PlanResource>,
            owners: Map<PlanResourceId, PlanResourceId>,
        ) {
            val key = pass.evaluationKey
            val inputs = pass.inputs()
            fun producer(id: PlanResourceId): PlanPass? = producers[id]?.let(passes::get)
            fun owner(id: PlanResourceId): PlanResourceId? = owners[id]
            fun sameKey(id: PlanResourceId, check: (FilterPassOperationV1) -> Boolean) {
                val previous = producer(id) as? PlanPass.FilterPass
                require(previous != null && previous.evaluationKey === key && check(previous.operation)) {
                    "W6b immediate chain producer has the wrong operation or evaluation key."
                }
            }
            fun occurrenceOwned(id: PlanResourceId) {
                require(id == key.boundSourceId || owner(id) == key.boundSourceId) {
                    "W6b input is not owned by the immutable occurrence source."
                }
            }
            when (val operation = pass.operation) {
                is FilterPassOperationV1.SeparableBlur -> {
                    val input = inputs.single()
                    occurrenceOwned(input)
                    val mask = operation.kind in setOf(FilterImplementationKindV1.MASK_COVERAGE_BLUR_X,
                        FilterImplementationKindV1.MASK_COVERAGE_BLUR_Y)
                    require((rows.getValue(key.boundSourceId).role == PlanResourceRole.CoverageSource) == mask) {
                        "W6b blur family disagrees with its immutable source role."
                    }
                    if (operation.axis == FilterAxisV1.Y) sameKey(input) { previous ->
                        previous is FilterPassOperationV1.SeparableBlur && previous.axis == FilterAxisV1.X &&
                            previous.kind.name.removeSuffix("_X") == operation.kind.name.removeSuffix("_Y")
                    } else require((producer(input) as? PlanPass.FilterPass)?.evaluationKey !== key) {
                        "W6b X blur must be first in its exact-key chain."
                    }
                }
                is FilterPassOperationV1.MaskBlurStyle -> {
                    require(inputs[0] == operation.blurredCoverageSource)
                    sameKey(inputs[0]) { it is FilterPassOperationV1.SeparableBlur && it.axis == FilterAxisV1.Y }
                    operation.originalCoverageSource?.let { original ->
                        require(inputs[1] == original && rows.getValue(original).role == PlanResourceRole.CoverageOriginal &&
                            owner(original) == key.boundSourceId) {
                            "W6b mask blur style lost its original occurrence coverage."
                        }
                    }
                }
                is FilterPassOperationV1.MaskShader -> {
                    require(rows.getValue(key.boundSourceId).role == PlanResourceRole.CoverageSource && inputs.single() == key.boundSourceId)
                    when (operation.materialBinding) {
                        is FilterPassOperationV1.MaskShaderMaterialBindingV1.CapturedOccurrence,
                        is FilterPassOperationV1.MaskShaderMaterialBindingV1.Planned,
                        -> Unit
                    }
                }
                is FilterPassOperationV1.MaskTable ->
                    require(rows.getValue(key.boundSourceId).role == PlanResourceRole.CoverageSource && inputs.single() == key.boundSourceId)
                is FilterPassOperationV1.MaterializedSource ->
                    require(rows.getValue(key.boundSourceId).role == PlanResourceRole.FilterSource && inputs.single() == key.boundSourceId)
                is FilterPassOperationV1.DropShadowColorize -> occurrenceOwned(inputs.single())
                is FilterPassOperationV1.DropShadowComposite -> {
                    sameKey(inputs[0]) { it is FilterPassOperationV1.DropShadowColorize }
                    if (operation.mode == org.graphiks.kanvas.render.ir.CapturedDropShadowModeV1.SHADOW_ONLY) {
                        require(operation.originalInput == null)
                    } else {
                        val original = requireNotNull(operation.originalInput)
                        require(inputs[1] == original && (original == key.boundSourceId || owner(original) == key.boundSourceId)) {
                            "W6b drop shadow composite has the wrong immutable original input."
                        }
                    }
                }
            }
        }

        private fun firstInSameKey(index: Int, passes: List<PlanPass>, producers: Map<PlanResourceId, Int>): Int {
            var current = index
            val key = (passes[current] as PlanPass.FilterPass).evaluationKey
            while (true) {
                val previous = producers[(passes[current] as PlanPass.FilterPass).inputs().first()] ?: return current
                val pass = passes[previous] as? PlanPass.FilterPass ?: return current
                if (pass.evaluationKey !== key) return current
                current = previous
            }
        }
    }
}
