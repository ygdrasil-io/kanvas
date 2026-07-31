package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.surface.RenderConfig

internal sealed interface GPUPreparedVerticesFramePreparation {
    data class Ready(
        val mapping: GPUOpMapping,
        val inventory: PreparedVerticesFrameInventory,
    ) : GPUPreparedVerticesFramePreparation

    data class Refused(
        val refusal: GPUPreparedOperationRefusal,
    ) : GPUPreparedVerticesFramePreparation
}

internal fun interface GPUPreparedVerticesOperationMapper {
    fun map(
        operations: List<DisplayOp>,
        target: GPUTargetFacts,
        config: RenderConfig,
        capabilities: GPUCapabilities,
        inventory: PreparedVerticesFrameInventory,
    ): GPUOpMapping
}

private val canonicalPreparedVerticesOperationMapper = GPUPreparedVerticesOperationMapper {
        operations, target, config, capabilities, inventory ->
    GPUOpMapper.mapOperationsWithPreparedVerticesInventory(
        operations = operations,
        target = target,
        config = config,
        capabilities = capabilities,
        inventory = inventory,
    )
}

internal object GPUPreparedVerticesFramePreparer {
    fun prepare(
        operations: List<DisplayOp>,
        target: GPUTargetFacts,
        config: RenderConfig,
        capabilities: GPUCapabilities,
        limits: PreparedVerticesFrameInventoryLimits = defaultLimits(capabilities),
        mapper: GPUPreparedVerticesOperationMapper = canonicalPreparedVerticesOperationMapper,
        inventoryObserver: (PreparedVerticesFrameInventory) -> Unit = {},
    ): GPUPreparedVerticesFramePreparation {
        val draws = ArrayList<GPUPreparedVerticesDraw>()
        operations.forEachIndexed { operationIndex, operation ->
            if (operation !is DisplayOp.DrawVertices && operation !is DisplayOp.DrawMesh) {
                return@forEachIndexed
            }
            when (
                val lowered = GPUPreparedVerticesLowerer.lower(
                    operation = operation,
                    operationIndex = operationIndex,
                    target = target,
                    capabilities = capabilities,
                )
            ) {
                is GPUPreparedVerticesLowering.Ready -> draws += lowered.draw
                is GPUPreparedVerticesLowering.Refused ->
                    return GPUPreparedVerticesFramePreparation.Refused(
                        GPUPreparedOperationRefusal(
                            commandId = operationIndex,
                            operationIndex = operationIndex,
                            code = lowered.code,
                            facts = lowered.facts,
                        ),
                    )
            }
        }

        val inventory = when (
            val built = PreparedVerticesFrameInventoryBuilder.build(draws, limits, capabilities)
        ) {
            is PreparedVerticesFrameInventoryResult.Ready -> built.inventory
            is PreparedVerticesFrameInventoryResult.Refused -> {
                val operationIndex = built.operationIndex ?: 0
                return GPUPreparedVerticesFramePreparation.Refused(
                    GPUPreparedOperationRefusal(
                        commandId = operationIndex,
                        operationIndex = operationIndex,
                        code = built.code,
                        facts = built.facts,
                    ),
                )
            }
        }
        inventoryObserver(inventory)
        val mapping = mapper.map(operations, target, config, capabilities, inventory)
        mapping.preparedRefusal?.let { refusal ->
            return GPUPreparedVerticesFramePreparation.Refused(refusal)
        }
        return GPUPreparedVerticesFramePreparation.Ready(mapping, inventory)
    }

    private fun defaultLimits(capabilities: GPUCapabilities): PreparedVerticesFrameInventoryLimits {
        val policyBufferBytes = 64L * 1024L * 1024L
        val deviceBufferBytes = capabilities.limits?.maxBufferSize ?: policyBufferBytes
        val effectiveBufferBytes = minOf(policyBufferBytes, deviceBufferBytes)
        return PreparedVerticesFrameInventoryLimits(
            maxDraws = 65_536,
            maxUniqueArtifacts = 16_384,
            maxVertexBytes = effectiveBufferBytes,
            maxIndexBytes = effectiveBufferBytes,
            maxTotalUploadBytes = Math.multiplyExact(effectiveBufferBytes, 2L),
            maxRuntimeUniformBytes = 16L * 1024L * 1024L,
            maxRuntimeChildren = 65_536,
        )
    }
}

private fun GPUOpMapper.mapOperationsWithPreparedVerticesInventory(
    operations: List<DisplayOp>,
    target: GPUTargetFacts,
    config: RenderConfig,
    capabilities: GPUCapabilities,
    inventory: PreparedVerticesFrameInventory,
): GPUOpMapping {
    val sourceVerticesOperationIndices = operations.mapIndexedNotNull { operationIndex, operation ->
        operationIndex.takeIf {
            operation is DisplayOp.DrawVertices || operation is DisplayOp.DrawMesh
        }
    }
    require(inventory.commands.map(PreparedVerticesFrameCommand::operationIndex) ==
        sourceVerticesOperationIndices
    ) {
        "Prepared vertices inventory must be complete and preserve source operation order"
    }
    return mapOperations(operations, target, config, capabilities)
}
