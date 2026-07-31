package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactGeneration
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedTextNativeBlendDomain
import org.graphiks.kanvas.surface.RenderConfig

/** Transactional result of the shared lower-all, inventory-once, map-once text preparation. */
internal sealed interface GPUPreparedTextFramePreparation {
    data class Ready(
        val mapping: GPUOpMapping,
        val inventory: PreparedTextFrameInventory,
        val metrics: GPUPreparedTextFrameMetrics,
    ) : GPUPreparedTextFramePreparation

    data class Refused(
        val refusal: GPUPreparedOperationRefusal,
    ) : GPUPreparedTextFramePreparation
}

/** Single production authority shared by direct Surface and diagnostic inventory paths. */
internal object GPUPreparedTextFramePreparer {
    fun prepare(
        operations: List<DisplayOp>,
        target: GPUTargetFacts,
        config: RenderConfig,
        capabilities: GPUCapabilities,
        generation: GPUTextArtifactGeneration,
        limits: PreparedTextFrameInventoryLimits = defaultLimits(target, capabilities),
    ): GPUPreparedTextFramePreparation {
        val preparedDraws = ArrayList<GPUPreparedTextDraw>()
        val elidedTextOperationIndices = linkedSetOf<Int>()
        val loweringStartedAt = System.nanoTime()
        operations.forEachIndexed { operationIndex, operation ->
            if (operation !is DisplayOp.DrawText) return@forEachIndexed
            if (operation.blob.glyphRuns.all { run -> run.glyphs.isEmpty() }) {
                elidedTextOperationIndices += operationIndex
                return@forEachIndexed
            }
            when (
                val lowered = GPUPreparedTextLowerer.lower(
                    operation = operation,
                    operationIndex = operationIndex,
                    target = target,
                    capabilities = capabilities,
                )
            ) {
                is GPUPreparedTextLowering.Ready -> {
                    if (lowered.draw.blendPlan is GPUBlendPlan.NoOp) {
                        elidedTextOperationIndices += operationIndex
                    } else {
                        preparedDraws += lowered.draw
                    }
                }
                is GPUPreparedTextLowering.Refused ->
                    return GPUPreparedTextFramePreparation.Refused(
                        GPUPreparedOperationRefusal(
                            commandId = operationIndex,
                            operationIndex = operationIndex,
                            code = lowered.code,
                            facts = lowered.facts,
                        ),
                    )
            }
        }
        val loweringNanoseconds = Math.subtractExact(System.nanoTime(), loweringStartedAt)
        val inventoryResult = when (
            val built = PreparedTextFrameInventoryBuilder.build(
                draws = preparedDraws,
                generation = generation,
                limits = limits,
                elidedTextOperationIndices = elidedTextOperationIndices,
            )
        ) {
            is PreparedTextFrameInventoryResult.Ready -> built
            is PreparedTextFrameInventoryResult.Refused ->
                return GPUPreparedTextFramePreparation.Refused(
                    GPUPreparedOperationRefusal(
                        commandId = built.operationIndex ?: 0,
                        operationIndex = built.operationIndex ?: 0,
                        code = built.code,
                        facts = built.facts,
                    ),
                )
        }
        val inventory = inventoryResult.inventory
        val refusedTextA8SubRun = inventory.subRunsByOperationIndex.values
            .asSequence()
            .flatten()
            .firstOrNull { subRun ->
                subRun.representation == GPUPreparedTextRepresentation.A8_MASK &&
                    GPUPreparedTextNativeBlendDomain.refusalCodeOrNull(
                        listOf(subRun.draw.blendPlan),
                    ) != null
            }
        if (refusedTextA8SubRun != null) {
            return GPUPreparedTextFramePreparation.Refused(
                GPUPreparedOperationRefusal(
                    commandId = refusedTextA8SubRun.operationIndex,
                    operationIndex = refusedTextA8SubRun.operationIndex,
                    code = GPUPreparedTextNativeBlendDomain.REFUSAL_CODE,
                    facts = emptyMap(),
                ),
            )
        }
        val mapping = GPUOpMapper.mapOperations(
            operations = operations,
            target = target,
            config = config,
            capabilities = capabilities,
            preparedTextInventory = inventory,
        )
        mapping.preparedRefusal?.let { refusal ->
            return GPUPreparedTextFramePreparation.Refused(refusal)
        }
        return GPUPreparedTextFramePreparation.Ready(
            mapping = mapping,
            inventory = inventory,
            metrics = inventory.metrics.copy(
                loweringNanoseconds = loweringNanoseconds,
                rasterNanoseconds = inventoryResult.rasterNanoseconds,
                packingNanoseconds = inventoryResult.packingNanoseconds,
            ),
        )
    }

    private fun defaultLimits(
        target: GPUTargetFacts,
        capabilities: GPUCapabilities,
    ): PreparedTextFrameInventoryLimits {
        val deviceMaxTexture = capabilities.limits?.maxTextureDimension2D
            ?.coerceAtMost(8_192L)
            ?.toInt()
            ?: 4_096
        val pageExtent = minOf(512, deviceMaxTexture)
        val pageBytes = Math.multiplyExact(pageExtent, pageExtent)
        val maxPages = 8
        val maxTotalPageBytes = Math.multiplyExact(pageBytes, maxPages)
        val targetPixels = target.width.toLong() * target.height.toLong()
        val boundedInstances = targetPixels.coerceIn(1L, 65_536L).toInt()
        return PreparedTextFrameInventoryLimits(
            pageWidth = pageExtent,
            pageHeight = pageExtent,
            maxPages = maxPages,
            maxPageBytes = pageBytes,
            maxTotalPageBytes = maxTotalPageBytes,
            maxGlyphs = boundedInstances,
            maxInstances = boundedInstances,
            maxSubRuns = minOf(16_384, boundedInstances),
            maxInstanceBytes = minOf(16 * 1_024 * 1_024, boundedInstances * 128),
            maxTextureDimension2D = deviceMaxTexture,
        )
    }
}
