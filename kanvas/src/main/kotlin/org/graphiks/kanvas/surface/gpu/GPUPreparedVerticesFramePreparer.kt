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

internal fun interface GPUPreparedFrameMappingBoundary {
    fun map(
        operations: List<DisplayOp>,
        target: GPUTargetFacts,
        config: RenderConfig,
        capabilities: GPUCapabilities,
        preparedTextInventory: PreparedTextFrameInventory?,
        preparedVerticesInventory: PreparedVerticesFrameInventory,
    ): GPUOpMapping
}

private val canonicalPreparedFrameMappingBoundary = GPUPreparedFrameMappingBoundary {
        operations, target, config, capabilities, textInventory, verticesInventory ->
    GPUOpMapper.mapOperations(
        operations = operations,
        target = target,
        config = config,
        capabilities = capabilities,
        preparedTextInventory = textInventory,
        preparedVerticesInventory = verticesInventory,
    )
}

internal object GPUPreparedVerticesFramePreparer {
    fun prepare(
        operations: List<DisplayOp>,
        target: GPUTargetFacts,
        config: RenderConfig,
        capabilities: GPUCapabilities,
        limits: PreparedVerticesFrameInventoryLimits = defaultLimits(capabilities),
        preparedTextInventory: PreparedTextFrameInventory? = null,
        mappingBoundary: GPUPreparedFrameMappingBoundary = canonicalPreparedFrameMappingBoundary,
    ): GPUPreparedVerticesFramePreparation {
        val operationSnapshot = operations.toList()
        val draws = ArrayList<GPUPreparedVerticesDraw>()
        operationSnapshot.forEachIndexed { operationIndex, operation ->
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
        val mapping = try {
            mappingBoundary.map(
                operationSnapshot, target, config, capabilities,
                preparedTextInventory, inventory,
            )
        } catch (failure: Exception) {
            return mapperFailure(operationSnapshot, "mapper_exception", failure)
        } catch (failure: LinkageError) {
            return mapperFailure(operationSnapshot, "mapper_linkage_error", failure)
        }
        mapping.preparedRefusal?.let { refusal ->
            return GPUPreparedVerticesFramePreparation.Refused(refusal)
        }
        val mappedInventory = mapping.preparedVerticesInventory
            ?: return GPUPreparedVerticesFramePreparation.Refused(
                GPUPreparedOperationRefusal(
                    commandId = 0,
                    operationIndex = firstVerticesOperationIndex(operationSnapshot),
                    code = "invalid.surface.prepared.vertices-mapping",
                    facts = mapOf(
                        "authority" to "GPUOpMapper",
                        "reason" to "missing_mapped_inventory",
                    ),
                ),
            )
        return GPUPreparedVerticesFramePreparation.Ready(mapping, mappedInventory)
    }

    private fun mapperFailure(
        operations: List<DisplayOp>,
        reason: String,
        failure: Throwable,
    ) = GPUPreparedVerticesFramePreparation.Refused(
        GPUPreparedOperationRefusal(
            commandId = 0,
            operationIndex = firstVerticesOperationIndex(operations),
            code = "invalid.surface.prepared.vertices-mapping",
            facts = mapOf(
                "authority" to "GPUOpMapper",
                "reason" to reason,
                "failure" to failure.javaClass.name,
            ),
        ),
    )

    private fun firstVerticesOperationIndex(operations: List<DisplayOp>): Int =
        operations.indexOfFirst { it is DisplayOp.DrawVertices || it is DisplayOp.DrawMesh }
            .coerceAtLeast(0)

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
