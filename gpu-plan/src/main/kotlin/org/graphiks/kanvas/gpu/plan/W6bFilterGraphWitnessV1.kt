package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.RectI32

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
                is PlanPass.PictureAggregateSealPass -> producers[pass.sealedSource] = index
                is PlanPass.RenderPass -> {
                    pass.coverageSource?.let { produced(it, index); materialCoverageInputs += it }
                    producers[pass.target] = index
                }
                is PlanPass.StencilCover -> {
                    pass.coverageSource?.let { produced(it, index); materialCoverageInputs += it }
                    producers[pass.target] = index
                }
                is PlanPass.TextureCopy -> producers[pass.destination] = index
                is PlanPass.PictureSourcePass -> {
                    pass.coverageSource?.let { produced(it, index); materialCoverageInputs += it }
                    if (pass.layerInput != null || pass.graphTextureRequest != null || pass.graphTextureOperand != null) {
                        require(pass.sourceSampling != null) {
                            "W6b Picture source must publish target-local sampling before native lowering."
                        }
                    }
                    producers[pass.output] = index
                }
                is PlanPass.FilterCoverageSourcePass -> {
                    require(row(pass.output).role == PlanResourceRole.CoverageSource)
                    pass.sealedAlphaSource?.let { alpha ->
                        require(pass.sealedAlphaSampling != null) {
                            "W6b alpha coverage must publish target-local sampling before native lowering."
                        }
                        if (alpha.aggregateId != null) {
                            val seal = passes[produced(alpha.sealedSourceId, index)] as? PlanPass.PictureAggregateSealPass
                            require(seal != null && seal.aggregateId == alpha.aggregateId &&
                                seal.sourceGenerationI64 == alpha.sealedSourceGenerationI64)
                        } else {
                            require(row(alpha.sealedSourceId).role == PlanResourceRole.LayerTarget)
                            produced(alpha.sealedSourceId, index)
                        }
                    }
                    producers[pass.output] = index
                    owners[pass.output] = pass.output
                }
                is PlanPass.FilterCoverageRetainPass -> {
                    require(row(pass.source).role in setOf(PlanResourceRole.CoverageSource, PlanResourceRole.FilterTarget))
                    require(row(pass.output).role == PlanResourceRole.CoverageOriginal)
                    produced(pass.source, index)
                    require(pass.sampling != null) {
                        "W6b retained coverage must publish target-local sampling before native lowering."
                    }
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
                    val materializedImageInput = isMaterializedImageInput(pass, passes, producers, rows)
                    require(bound.role in setOf(PlanResourceRole.FilterSource, PlanResourceRole.CoverageSource) ||
                        materializedImageInput) {
                        "W6b occurrence source must be immutable FilterSource or CoverageSource."
                    }
                    produced(bound.id, index)
                    require(row(pass.output).role == PlanResourceRole.FilterTarget)
                    require(pass.inputs().size == arity(pass.operation)) { "W6b operation input arity is invalid." }
                    requirePublishedTargetLocalSampling(pass.operation)
                    pass.inputs().forEachIndexed { inputIndex, input ->
                        produced(input, index)
                        val materialCoverage = pass.operation is FilterPassOperationV1.MaterializedSource && inputIndex == 1
                        val materializedImageSource = materializedImageInput && inputIndex == 0 && input == bound.id
                        require(materialCoverage || materializedImageSource || owners[input] == null || owners[input] == bound.id) {
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
                val snapshot = passes.getOrNull(terminalIndex + 1) as? PlanPass.TextureCopy
                val picture = (composite.operation as? FilterCompositeOperationV1.Picture)?.terminal
                val adjacent = terminalIndex + 1 == compositeIndex ||
                    terminalIndex + 2 == compositeIndex && snapshot != null && picture != null &&
                        snapshot.source == composite.destination &&
                        snapshot.destination == picture.blend.destinationReadSnapshotResourceV1() &&
                        snapshot.destinationVersion == picture.destinationVersionBefore
                require(terminal.evaluationKey === composite.evaluationKey && adjacent) {
                    "W6b terminal output must immediately composite with the exact evaluation key."
                }
                Occurrence(terminal.evaluationKey.boundSourceId, firstInSameKey(terminalIndex, passes, producers),
                    terminalIndex, compositeIndex)
            })
        }

        private fun arity(operation: FilterPassOperationV1): Int = when (operation) {
            is FilterPassOperationV1.DropShadowComposite -> 2
            is FilterPassOperationV1.MaskBlurStyle -> if (operation.originalCoverageSource == null) 1 else 2
            is FilterPassOperationV1.SeparableBlur,
            is FilterPassOperationV1.MaskShader,
            is FilterPassOperationV1.MaskTable,
            is FilterPassOperationV1.DropShadowColorize,
            -> 1
            is FilterPassOperationV1.MaterializedSource -> 2
        }

        /** Every W6b texture read has a plan-sealed local offset; the renderer owns no origin map. */
        private fun requirePublishedTargetLocalSampling(operation: FilterPassOperationV1) {
            when (operation) {
                is FilterPassOperationV1.SeparableBlur -> require(operation.sampling != null)
                is FilterPassOperationV1.MaskBlurStyle -> {
                    require(operation.blurredSampling != null)
                    require((operation.originalCoverageSource == null) == (operation.originalSampling == null))
                }
                is FilterPassOperationV1.MaskShader -> require(operation.sampling != null)
                is FilterPassOperationV1.MaskTable -> require(operation.sampling != null)
                is FilterPassOperationV1.MaterializedSource ->
                    require(operation.sourceSampling != null && operation.coverageSampling != null)
                is FilterPassOperationV1.DropShadowColorize,
                is FilterPassOperationV1.DropShadowComposite,
                -> Unit
            }
        }

        /**
         * A combined W6b occurrence first freezes mask coverage into a materialized source,
         * then uses that typed filter target as the immutable entry point for the captured
         * image-filter chain.  This is still a producer edge in the sealed graph, not a
         * renderer-side source rediscovery.
         */
        private fun isMaterializedImageInput(
            pass: PlanPass.FilterPass,
            passes: List<PlanPass>,
            producers: Map<PlanResourceId, Int>,
            rows: Map<PlanResourceId, PlanResource>,
        ): Boolean {
            val blur = pass.operation as? FilterPassOperationV1.SeparableBlur ?: return false
            val boundSource = pass.evaluationKey.boundSourceId
            if (rows.getValue(boundSource).role != PlanResourceRole.FilterTarget) return false
            return when (blur.kind) {
                FilterImplementationKindV1.IMAGE_BLUR_X ->
                    blur.axis == FilterAxisV1.X && pass.inputs().singleOrNull() == boundSource
                FilterImplementationKindV1.IMAGE_BLUR_Y -> {
                    val previous = pass.inputs().singleOrNull()?.let { producers[it] }?.let(passes::get) as? PlanPass.FilterPass
                    previous?.evaluationKey === pass.evaluationKey &&
                        (previous.operation as? FilterPassOperationV1.SeparableBlur)?.kind == FilterImplementationKindV1.IMAGE_BLUR_X
                }
                else -> false
            }
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
                    val mask = operation.kind in setOf(FilterImplementationKindV1.MASK_COVERAGE_BLUR_X,
                        FilterImplementationKindV1.MASK_COVERAGE_BLUR_Y)
                    val materializedImageInput = isMaterializedImageInput(pass, passes, producers, rows)
                    if (!materializedImageInput) occurrenceOwned(input)
                    val sourceRole = rows.getValue(key.boundSourceId).role
                    require((sourceRole == PlanResourceRole.CoverageSource) == mask &&
                        (sourceRole != PlanResourceRole.FilterTarget || materializedImageInput)) {
                        "W6b blur family disagrees with its immutable source role."
                    }
                    if (materializedImageInput && operation.axis == FilterAxisV1.X) require((producer(input) as? PlanPass.FilterPass)?.operation
                        is FilterPassOperationV1.MaterializedSource) {
                        "W6b image blur must begin from the preceding materialized source."
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
                        is FilterPassOperationV1.MaskShaderMaterialBindingV1.CapturedOccurrence -> Unit
                        is FilterPassOperationV1.MaskShaderMaterialBindingV1.Planned -> {
                            val uniform = rows.getValue(operation.materialBinding.uniformResource)
                            require(uniform.role == PlanResourceRole.SourceUniformData &&
                                uniform.kind == PlanResourceKind.Buffer &&
                                operation.materialBinding.uniformOffsetBytesI64 == 0L &&
                                operation.materialBinding.uniformCapacityBytesI64 == uniform.byteSize &&
                                operation.materialBinding.materialAuthority.materialPlanRef() ==
                                operation.materialBinding.material)
                        }
                    }
                }
                is FilterPassOperationV1.MaskTable -> {
                    require(rows.getValue(key.boundSourceId).role == PlanResourceRole.CoverageSource && inputs.single() == key.boundSourceId)
                    val table = rows.getValue(operation.tableResourceId)
                    require(operation.entryCountI32 == 256 && operation.copyTable().sizeI32 == 256 &&
                        operation.generationI64 == 0L && operation.ownerMaskOccurrenceI32 == key.maskOccurrenceI32 &&
                        table.role == PlanResourceRole.MaskTableData && table.kind == PlanResourceKind.Buffer &&
                        table.byteSize == 256L && table.usages() ==
                        setOf(PlanResourceUsage.StorageRead, PlanResourceUsage.CopyDestination) &&
                        table.lifetime == PlanResourceLifetime.FrameLocal)
                }
                is FilterPassOperationV1.MaterializedSource -> {
                    require(rows.getValue(key.boundSourceId).role == PlanResourceRole.FilterSource &&
                        inputs.first() == key.boundSourceId)
                    val coverage = inputs.last()
                    val coverageOperation = (producer(coverage) as? PlanPass.FilterPass)?.operation
                    require(rows.getValue(coverage).role == PlanResourceRole.FilterTarget && (
                        coverageOperation is FilterPassOperationV1.MaskBlurStyle ||
                            coverageOperation is FilterPassOperationV1.MaskShader ||
                            coverageOperation is FilterPassOperationV1.MaskTable
                        )) {
                        "W6b materialized source must consume the frozen mask coverage output."
                    }
                }
                is FilterPassOperationV1.DropShadowColorize -> {
                    val input = inputs.single()
                    occurrenceOwned(input)
                    sameKey(input) { previous ->
                        previous is FilterPassOperationV1.SeparableBlur &&
                            previous.axis == FilterAxisV1.Y &&
                            previous.kind == FilterImplementationKindV1.IMAGE_BLUR_Y
                    }
                    val sampling = requireNotNull(operation.linearSampling) {
                        "W6b shadow linear sampling must be sealed before publication."
                    }
                    val inputExtent = requireNotNull(rows.getValue(input).copyExtent())
                    val outputExtent = requireNotNull(rows.getValue(pass.output).copyExtent())
                    require(sampling.copySourceFootprintTargetLocalI32() == RectI32(0, 0, inputExtent.width, inputExtent.height) &&
                        sampling.copyOutputFootprintTargetLocalI32() == RectI32(0, 0, outputExtent.width, outputExtent.height)) {
                        "W6b shadow linear sampling footprint disagrees with its sealed targets."
                    }
                }
                is FilterPassOperationV1.DropShadowComposite -> {
                    require(operation.mode == org.graphiks.kanvas.render.ir.CapturedDropShadowModeV1.COMPOSITE) {
                        "SHADOW_ONLY must terminate at DropShadowColorize."
                    }
                    sameKey(inputs[0]) { it is FilterPassOperationV1.DropShadowColorize }
                    val original = requireNotNull(operation.originalInput)
                    require(inputs[1] == original && (original == key.boundSourceId || owner(original) == key.boundSourceId)) {
                        "W6b drop shadow composite has the wrong immutable original input."
                    }
                    require(operation.copyShadowSampleOffsetTargetLocalI32() != null &&
                        operation.copyOriginalSampleOffsetTargetLocalI32() != null) {
                        "W6b drop shadow composite must consume plan-sealed target-local coordinates."
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
