package org.graphiks.kanvas.surface.gpu

import java.util.Collections
import kotlin.math.ceil
import kotlin.math.floor
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendDestinationReadRequirement
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlanner
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendSpecializationRequest
import org.graphiks.kanvas.gpu.renderer.passes.GPUCoverageConsumption
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUTargetBlendFacts
import org.graphiks.kanvas.gpu.renderer.passes.canonicalIdentity
import org.graphiks.kanvas.gpu.renderer.passes.forCorePrimitiveAnalyticShapeCoverage
import org.graphiks.kanvas.gpu.renderer.recording.canonicalSnapshotHash
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendState
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.ClipStackOp
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.canvas.intersectWith
import org.graphiks.kanvas.gpu.renderer.commands.GPUBounds
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipKind
import org.graphiks.kanvas.gpu.renderer.commands.GPUImageFilterPlan
import org.graphiks.kanvas.gpu.renderer.filters.NormalizedBlurStyle
import org.graphiks.kanvas.gpu.renderer.filters.NormalizedMaskFilter
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.gpu.renderer.commands.GPUCommandSource
import org.graphiks.kanvas.gpu.renderer.commands.GPUFrameProvenance
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUBlendFacts
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceAlphaClassification
import org.graphiks.kanvas.gpu.renderer.commands.GPULayerFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUOrderingFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUPathFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.GPURRect
import org.graphiks.kanvas.gpu.renderer.commands.GPURRectCornerRadii
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformType
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.text.GPUTextArtifactRef
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactGeneration
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUFirstSliceCapabilityName.BOUNDED_CLIP_NATIVE
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUFirstSliceCapabilityName.PATH_FILL_STENCIL_COVER
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUFirstSliceCapabilityName.SCISSOR_NATIVE
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipAtomicGroupID
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipAnalyticElement
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageElement
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageElementKind
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageOperation
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionGeometry
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipFillRule
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskCombine
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskConsumerPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskProducerPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipOrderingToken
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilCompare
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilConsumerPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilLoadOperation
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilOperation
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilProducerPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilStoreOperation
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipAnalyticRectElement
import org.graphiks.kanvas.gpu.renderer.clips.GPU_ANALYTIC_MULTI_RECT_MAX_ELEMENTS
import org.graphiks.kanvas.gpu.renderer.clips.GPUBounds as GPUClipBounds
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.geometry.PathTessellator
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.matrix.mapAxisAligned
import org.graphiks.math.matrix.mapAxisAlignedRect
import org.graphiks.math.geometry.RectF32
import org.graphiks.kanvas.types.PointMode
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.color.ColorARGB
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.surface.RenderConfig

internal data class GPUOpMapping(
    val visualCommands: List<GPUFramePathVisualCommand>,
    val stateEvents: List<GPUFramePathStateEvent>,
    val preparedRefusal: GPUPreparedOperationRefusal? = null,
    val culledTextOperationIndices: Set<Int> = emptySet(),
    val preparedVerticesInventory: PreparedVerticesFrameInventory? = null,
    val allocatedCommandIds: Set<Int> = emptySet(),
    val commandIdsByOperationIndex: Map<Int, Set<Int>> = emptyMap(),
)

data class GPUPreparedOperationRefusal(
    val commandId: Int,
    val operationIndex: Int,
    val code: String,
    val facts: Map<String, String>,
)

private sealed interface GPUPreparedCommandSlotAuthentication {
    data class Ready(val commandIds: Set<Int>) : GPUPreparedCommandSlotAuthentication
    data class Refused(val refusal: GPUPreparedOperationRefusal) :
        GPUPreparedCommandSlotAuthentication
}

/** Sole Canvas-state translator for the Slice 12A frame route. */
internal object GPUOpMapper {
    fun mapOperations(
        operations: List<DisplayOp>,
        target: GPUTargetFacts,
        config: RenderConfig,
        capabilities: GPUCapabilities,
        preparedTextInventory: PreparedTextFrameInventory? = null,
        preparedVerticesInventory: PreparedVerticesFrameInventory? = null,
        elidedOperationIndices: Set<Int> = emptySet(),
    ): GPUOpMapping {
        val visual = mutableListOf<GPUFramePathVisualCommand>()
        val stateEvents = mutableListOf<GPUFramePathStateEvent>()
        val culledTextOperationIndices = linkedSetOf<Int>()
        val preparedVerticesProvenance = linkedMapOf<Int, GPUFrameProvenance>()
        val preparedVerticesCommandIds = linkedMapOf<Int, Int>()
        val commandIdsByOperationIndex = mutableMapOf<Int, MutableSet<Int>>()
        var provenance = GPUFrameProvenance.None

        fun nextCommandId(): Int = Math.addExact(visual.size, preparedVerticesCommandIds.size)

        fun recordCommandIds(operationIndex: Int, commandIds: Set<Int>) {
            if (commandIds.isNotEmpty()) {
                commandIdsByOperationIndex.getOrPut(operationIndex) { linkedSetOf() }
                    .addAll(commandIds)
            }
        }

        preparedVerticesInventory?.let { inventory ->
            val sourceIndices = operations.mapIndexedNotNull { index, operation ->
                index.takeIf { operation is DisplayOp.DrawVertices || operation is DisplayOp.DrawMesh }
            }
            val owned = inventory.commandsByOperationIndex.keys +
                inventory.elidedVerticesOperationIndices
            if (owned != sourceIndices.toSet() || owned.size != sourceIndices.size ||
                inventory.mappedCommands.isNotEmpty()
            ) {
                return GPUOpMapping(
                    visualCommands = emptyList(), stateEvents = emptyList(),
                    preparedRefusal = GPUPreparedOperationRefusal(
                        commandId = 0, operationIndex = sourceIndices.firstOrNull() ?: 0,
                        code = "invalid.surface.prepared.vertices-operation-ownership",
                        facts = mapOf("authority" to "GPUOpMapper"),
                    ),
                )
            }
        }

        operations.forEachIndexed { operationIndex, operation ->
            if (operationIndex in elidedOperationIndices) {
                return@forEachIndexed
            }
            when (operation) {
                is DisplayOp.Annotation -> {
                    stateEvents += GPUFramePathStateEvent(operationIndex, GPUFramePathStateKind.Annotation)
                    if (operation.key == GPU_FRAME_PROVENANCE_ANNOTATION_KEY) {
                        GPUFrameProvenance.fromAnnotationValue(operation.value)?.let { provenance = it }
                    }
                }
                is DisplayOp.SetTransform ->
                    stateEvents += GPUFramePathStateEvent(operationIndex, GPUFramePathStateKind.Transform)
                is DisplayOp.SetClip ->
                    stateEvents += GPUFramePathStateEvent(operationIndex, GPUFramePathStateKind.Clip)
                is DisplayOp.FlushAndSnapshot ->
                    stateEvents += GPUFramePathStateEvent(operationIndex, GPUFramePathStateKind.FlushSnapshot)
                is DisplayOp.DrawText -> {
                    if (preparedTextInventory == null) {
                        return@forEachIndexed
                    }
                    if (operationIndex !in preparedTextInventory.acceptedTextOperationIndices) {
                        return GPUOpMapping(
                            visualCommands = emptyList(),
                            stateEvents = stateEvents.toList(),
                            preparedRefusal = GPUPreparedOperationRefusal(
                                commandId = nextCommandId(),
                                operationIndex = operationIndex,
                                code = "invalid.surface.prepared.text-operation-ownership",
                                facts = emptyMap(),
                            ),
                        )
                    }
                    if (operation.hasConservativeTargetEmptyTextProof(
                            target.width,
                            target.height,
                        )
                    ) {
                        culledTextOperationIndices += operationIndex
                        return@forEachIndexed
                    }
                    preparedTextInventory.strokePathsByOperationIndex[operationIndex]?.let {
                        strokePaths ->
                        val strokeVisuals = ArrayList<GPUFramePathVisualCommand>(strokePaths.size)
                        strokePaths.forEach { strokePath ->
                            val commandId = nextCommandId() + strokeVisuals.size
                            val strokeOperation = DisplayOp.DrawPath(
                                path = strokePath.path,
                                paint = strokePath.draw.paint,
                                transform = strokePath.draw.transform,
                                clip = strokePath.draw.clip,
                            )
                            val lowered = lowerPreparedCoreVisual(
                                operation = strokeOperation,
                                commandId = GPUDrawCommandID(commandId),
                                paintOrder = commandId,
                                context = GPUPreparedImageLoweringContext(
                                    provenance = provenance,
                                    target = target,
                                    config = config,
                                    capabilities = capabilities,
                                ),
                            )
                            val basePath = lowered?.normalized as? NormalizedDrawCommand.FillPath
                            val geometryRefusal = lowered?.geometryRefusal
                            if (basePath == null || geometryRefusal != null) {
                                return GPUOpMapping(
                                    visualCommands = emptyList(),
                                    stateEvents = stateEvents.toList(),
                                    preparedRefusal = GPUPreparedOperationRefusal(
                                        commandId = commandId,
                                        operationIndex = operationIndex,
                                        code = geometryRefusal?.code
                                            ?: "unsupported.core_primitive.stroke.path_lowering",
                                        facts = geometryRefusal?.refusalFacts.orEmpty(),
                                    ),
                                )
                            }
                            val fillPath = basePath.toPreparedStrokeFillPath()
                                ?: return GPUOpMapping(
                                    visualCommands = emptyList(),
                                    stateEvents = stateEvents.toList(),
                                    preparedRefusal = GPUPreparedOperationRefusal(
                                        commandId = commandId,
                                        operationIndex = operationIndex,
                                        code = "unsupported.core_primitive.stroke.expansion_empty",
                                        facts = mapOf(
                                            "glyphIndex" to strokePath.glyphIndex.toString(),
                                        ),
                                    ),
                                )
                            strokeVisuals += lowered.copy(
                                normalized = fillPath,
                                targetSpaceBounds = fillPath.bounds,
                                geometryRefusal = null,
                            )
                        }
                        visual += strokeVisuals
                        recordCommandIds(
                            operationIndex,
                            strokeVisuals.mapTo(linkedSetOf()) { stroke ->
                                stroke.normalized.commandId.value
                            },
                        )
                        return@forEachIndexed
                    }
                    val subRuns = preparedTextInventory.subRunsByOperationIndex[operationIndex].orEmpty()
                    for (subRun in subRuns) {
                        val commandId = nextCommandId()
                        when (
                            val lowered = subRun.toPreparedTextVisual(
                                commandId = commandId,
                                provenance = provenance,
                                target = target,
                                config = config,
                                capabilities = capabilities,
                                inventory = preparedTextInventory,
                            )
                        ) {
                            GPUPreparedTextVisualLowering.Culled ->
                                culledTextOperationIndices += operationIndex
                            GPUPreparedTextVisualLowering.Invalid -> return GPUOpMapping(
                                visualCommands = emptyList(),
                                stateEvents = stateEvents.toList(),
                                preparedRefusal = GPUPreparedOperationRefusal(
                                    commandId = commandId,
                                    operationIndex = operationIndex,
                                    code = "invalid.surface.prepared.text-command",
                                    facts = mapOf(
                                        "subRunIndex" to subRun.subRunIndex.toString(),
                                    ),
                                ),
                            )
                            is GPUPreparedTextVisualLowering.Ready -> {
                                visual += lowered.command
                                recordCommandIds(
                                    operationIndex,
                                    setOf(lowered.command.normalized.commandId.value),
                                )
                            }
                        }
                    }
                }
                is DisplayOp.DrawImage -> {
                    val commandId = nextCommandId()
                    when (
                        val lowered = GPUPreparedDrawImageLowerer.lower(
                            operation = operation,
                            commandId = GPUDrawCommandID(commandId),
                            paintOrder = commandId,
                            provenance = provenance,
                            target = target,
                            config = config,
                            capabilities = capabilities,
                        )
                    ) {
                        is GPUPreparedDrawImageLowering.Ready -> {
                            visual += lowered.command
                            recordCommandIds(
                                operationIndex,
                                setOf(lowered.command.normalized.commandId.value),
                            )
                        }
                        is GPUPreparedDrawImageLowering.Refused -> return GPUOpMapping(
                            visualCommands = emptyList(),
                            stateEvents = stateEvents.toList(),
                            preparedRefusal = GPUPreparedOperationRefusal(
                                commandId = commandId,
                                operationIndex = operationIndex,
                                code = lowered.code,
                                facts = lowered.facts,
                            ),
                        )
                    }
                }
                is DisplayOp.DrawImageNine -> {
                    val commandId = nextCommandId()
                    val context = GPUPreparedImageLoweringContext(
                        provenance = provenance,
                        target = target,
                        config = config,
                        capabilities = capabilities,
                    )
                    when (
                        val lowered = GPUPreparedImageGridLowerer.lowerNine(
                            operation = operation,
                            firstCommandId = commandId,
                            firstPaintOrder = commandId,
                            context = context,
                        )
                    ) {
                        is GPUPreparedImageGridLowering.Ready -> {
                            visual += lowered.commands
                            recordCommandIds(
                                operationIndex,
                                lowered.commands.mapTo(linkedSetOf()) { command ->
                                    command.normalized.commandId.value
                                },
                            )
                        }
                        is GPUPreparedImageGridLowering.Refused -> return GPUOpMapping(
                            visualCommands = emptyList(),
                            stateEvents = stateEvents.toList(),
                            preparedRefusal = GPUPreparedOperationRefusal(
                                commandId = commandId,
                                operationIndex = operationIndex,
                                code = lowered.code,
                                facts = lowered.facts +
                                    ("cellOperationIndex" to lowered.operationIndex.toString()),
                            ),
                        )
                    }
                }
                is DisplayOp.DrawImageLattice -> {
                    val commandId = nextCommandId()
                    val context = GPUPreparedImageLoweringContext(
                        provenance = provenance,
                        target = target,
                        config = config,
                        capabilities = capabilities,
                    )
                    when (
                        val lowered = GPUPreparedImageGridLowerer.lowerLattice(
                            operation = operation,
                            firstCommandId = commandId,
                            firstPaintOrder = commandId,
                            context = context,
                        )
                    ) {
                        is GPUPreparedImageGridLowering.Ready -> {
                            visual += lowered.commands
                            recordCommandIds(
                                operationIndex,
                                lowered.commands.mapTo(linkedSetOf()) { command ->
                                    command.normalized.commandId.value
                                },
                            )
                        }
                        is GPUPreparedImageGridLowering.Refused -> return GPUOpMapping(
                            visualCommands = emptyList(),
                            stateEvents = stateEvents.toList(),
                            preparedRefusal = GPUPreparedOperationRefusal(
                                commandId = commandId,
                                operationIndex = operationIndex,
                                code = lowered.code,
                                facts = lowered.facts +
                                    ("cellOperationIndex" to lowered.operationIndex.toString()),
                            ),
                        )
                    }
                }
                is DisplayOp.DrawAtlas -> {
                    val commandId = nextCommandId()
                    val context = GPUPreparedImageLoweringContext(
                        provenance = provenance,
                        target = target,
                        config = config,
                        capabilities = capabilities,
                    )
                    when (
                        val lowered = GPUPreparedAtlasLowerer.lower(
                            operation = operation,
                            firstCommandId = commandId,
                            firstPaintOrder = commandId,
                            context = context,
                        )
                    ) {
                        is GPUPreparedAtlasLowering.Ready -> {
                            visual += lowered.commands
                            recordCommandIds(
                                operationIndex,
                                lowered.commands.mapTo(linkedSetOf()) { command ->
                                    command.normalized.commandId.value
                                },
                            )
                        }
                        is GPUPreparedAtlasLowering.Refused -> return GPUOpMapping(
                            visualCommands = emptyList(),
                            stateEvents = stateEvents.toList(),
                            preparedRefusal = GPUPreparedOperationRefusal(
                                commandId = commandId,
                                operationIndex = operationIndex,
                                code = lowered.code,
                                facts = lowered.facts + listOfNotNull(
                                    lowered.spriteIndex?.let { "spriteIndex" to it.toString() },
                                ),
                            ),
                        )
                    }
                }
                is DisplayOp.DrawVertices, is DisplayOp.DrawMesh -> {
                    if (preparedVerticesInventory == null) {
                        return@forEachIndexed
                    }
                    if (operationIndex in preparedVerticesInventory.elidedVerticesOperationIndices) {
                        return@forEachIndexed
                    }
                    val command = preparedVerticesInventory.commandsByOperationIndex[operationIndex]
                        ?: return GPUOpMapping(
                            visualCommands = emptyList(), stateEvents = stateEvents.toList(),
                            preparedRefusal = GPUPreparedOperationRefusal(
                                commandId = nextCommandId(), operationIndex = operationIndex,
                                code = "invalid.surface.prepared.vertices-operation-ownership",
                                facts = mapOf("authority" to "GPUOpMapper"),
                            ),
                        )
                    val commandId = nextCommandId()
                    preparedVerticesCommandIds[command.operationIndex] = commandId
                    preparedVerticesProvenance[commandId] = provenance
                }
                else -> {
                    if (operation is DisplayOp.DrawPicture) {
                        // The flat mapper has no picture replay: DrawPicture content is
                        // nested in the Picture and can only be lowered through the
                        // composite (saveLayer) route. Refuse instead of silently
                        // dropping the picture from the flat frame.
                        return GPUOpMapping(
                            visualCommands = emptyList(),
                            stateEvents = stateEvents.toList(),
                            preparedRefusal = GPUPreparedOperationRefusal(
                                commandId = nextCommandId(),
                                operationIndex = operationIndex,
                                code = "unsupported.surface.prepared.draw-picture",
                                facts = mapOf("authority" to "GPUOpMapper"),
                            ),
                        )
                    }
                    val paintOrder = nextCommandId()
                    val lowered = lowerPreparedCoreVisual(
                        operation = operation,
                        commandId = GPUDrawCommandID(paintOrder),
                        paintOrder = paintOrder,
                        context = GPUPreparedImageLoweringContext(
                            provenance = provenance,
                            target = target,
                            config = config,
                            capabilities = capabilities,
                        ),
                    )
                    if (lowered == null) {
                        return@forEachIndexed
                    }
                    visual += lowered
                    recordCommandIds(
                        operationIndex,
                        setOf(lowered.normalized.commandId.value),
                    )
                }
            }
        }
        val mappedVerticesInventory = when (
            val binding = preparedVerticesInventory?.bindCommandIds(
                preparedVerticesCommandIds,
                preparedVerticesProvenance,
            )
        ) {
            null -> null
            is PreparedVerticesCommandBindingResult.Ready -> binding.inventory
            is PreparedVerticesCommandBindingResult.Refused -> return GPUOpMapping(
                visualCommands = emptyList(),
                stateEvents = stateEvents.toList(),
                preparedRefusal = GPUPreparedOperationRefusal(
                    commandId = preparedVerticesCommandIds[binding.operationIndex]
                        ?.coerceAtLeast(0) ?: nextCommandId(),
                    operationIndex = binding.operationIndex,
                    code = binding.code,
                    facts = binding.facts,
                ),
                culledTextOperationIndices = culledTextOperationIndices.toSet(),
            )
        }
        val allocatedCommandIds = when (
            val authenticated = authenticateCommandSlots(
                visualCommands = visual,
                mappedVerticesCommands = mappedVerticesInventory?.mappedCommands.orEmpty(),
                allocatedSlotCount = nextCommandId(),
            )
        ) {
            is GPUPreparedCommandSlotAuthentication.Ready -> authenticated.commandIds
            is GPUPreparedCommandSlotAuthentication.Refused -> return GPUOpMapping(
                visualCommands = emptyList(),
                stateEvents = stateEvents.toList(),
                preparedRefusal = authenticated.refusal,
                culledTextOperationIndices = culledTextOperationIndices.toSet(),
            )
        }
        return GPUOpMapping(
            visualCommands = visual.toList(),
            stateEvents = stateEvents.toList(),
            culledTextOperationIndices = culledTextOperationIndices.toSet(),
            preparedVerticesInventory = mappedVerticesInventory,
            allocatedCommandIds = allocatedCommandIds,
            commandIdsByOperationIndex = commandIdsByOperationIndex
                .mapValues { (_, commandIds) -> commandIds.toSet() },
        )
    }

    private fun authenticateCommandSlots(
        visualCommands: List<GPUFramePathVisualCommand>,
        mappedVerticesCommands: List<PreparedVerticesMappedCommand>,
        allocatedSlotCount: Int,
    ): GPUPreparedCommandSlotAuthentication {
        val observed = linkedSetOf<Int>()
        visualCommands.forEach { visualCommand ->
            val commandId = visualCommand.normalized.commandId.value
            if (commandId < 0 || !observed.add(commandId)) {
                return commandSlotRefusal(
                    operationIndex = 0,
                    reason = if (commandId < 0) "negative_visual_command_id" else
                        "duplicate_visual_command_id",
                    commandId = commandId,
                    allocatedSlotCount = allocatedSlotCount,
                )
            }
        }
        mappedVerticesCommands.forEach { verticesCommand ->
            if (!observed.add(verticesCommand.commandId)) {
                return commandSlotRefusal(
                    operationIndex = verticesCommand.operationIndex,
                    reason = "overlapping_vertices_command_id",
                    commandId = verticesCommand.commandId,
                    allocatedSlotCount = allocatedSlotCount,
                )
            }
        }
        val expected = (0 until allocatedSlotCount).toSet()
        if (observed != expected) {
            val firstUnexpected = observed.firstOrNull { it !in expected }
            val firstMissing = expected.firstOrNull { it !in observed }
            val operationIndex = firstUnexpected?.let { unexpected ->
                mappedVerticesCommands.firstOrNull { it.commandId == unexpected }?.operationIndex
            } ?: 0
            return commandSlotRefusal(
                operationIndex = operationIndex,
                reason = "non_contiguous_command_slots",
                commandId = firstUnexpected ?: firstMissing ?: allocatedSlotCount,
                allocatedSlotCount = allocatedSlotCount,
                extraFacts = listOfNotNull(
                    firstUnexpected?.let { "firstUnexpectedCommandId" to it.toString() },
                    firstMissing?.let { "firstMissingCommandId" to it.toString() },
                ).toMap(),
            )
        }
        return GPUPreparedCommandSlotAuthentication.Ready(
            Collections.unmodifiableSet(LinkedHashSet(observed)),
        )
    }

    private fun commandSlotRefusal(
        operationIndex: Int,
        reason: String,
        commandId: Int,
        allocatedSlotCount: Int,
        extraFacts: Map<String, String> = emptyMap(),
    ) = GPUPreparedCommandSlotAuthentication.Refused(
        GPUPreparedOperationRefusal(
            commandId = commandId.coerceAtLeast(0),
            operationIndex = operationIndex,
            code = "invalid.surface.prepared.command-slot-authentication",
            facts = linkedMapOf(
                "authority" to "GPUOpMapper",
                "reason" to reason,
                "allocatedSlotCount" to allocatedSlotCount.toString(),
            ).apply { putAll(extraFacts) },
        ),
    )

    internal fun lowerPreparedCoreVisual(
        operation: DisplayOp,
        commandId: GPUDrawCommandID,
        paintOrder: Int,
        context: GPUPreparedImageLoweringContext,
    ): GPUFramePathVisualCommand? {
        var loweringRefusal: GPUCorePrimitiveGeometryRefusal? = null
        val rawNormalized = mapCoreOperation(
            operation = operation,
            commandId = commandId,
            paintOrder = paintOrder,
            provenance = context.provenance,
            target = context.target,
            config = context.config,
            onGeometryRefusal = { refusal -> loweringRefusal = refusal },
        ) ?: return null
        val geometryRefusal = loweringRefusal ?: operation.coreGeometryRefusalOrNull()
        val coverage = rawNormalized.geometryCoverage()
        val clipPlan = rawNormalized.clip.coverageRequest?.let { request ->
            GPUClipCoveragePlanner.planForFrameRoute(
                request,
                context.config,
                maxOf(context.target.width, context.target.height),
            )
        } ?: GPUClipCoveragePlan.NoClip
        // The rect-decomposable → AnalyticMultiRect lowering is only safe for
        // the mask-blur composite consumer (whose shader folds per-rect coverage). Non-blur
        // core draws keep their prior CoverageMask route.
        val admitAnalyticMultiRect = rawNormalized.hasBlurMaskFilter()
        val clipExecutionPlan = clipPlan.toExecutionPlan(
            context.capabilities,
            context.target,
            admitAnalyticMultiRect,
        )
        val normalized = rawNormalized.withClipPlans(clipPlan, clipExecutionPlan)
        return GPUFramePathVisualCommand(
            normalized = normalized,
            targetSpaceBounds = normalized.bounds,
            geometryCoverage = coverage,
            clipCoverage = clipPlan,
            clipExecutionPlan = clipExecutionPlan,
            blendPlan = normalized.blend.canonicalBlendPlan(coverage),
            provenance = context.provenance,
            geometryRefusal = geometryRefusal,
        )
    }

    private fun mapCoreOperation(
        operation: DisplayOp,
        commandId: GPUDrawCommandID,
        paintOrder: Int,
        provenance: GPUFrameProvenance,
        target: GPUTargetFacts,
        config: RenderConfig,
        onGeometryRefusal: (GPUCorePrimitiveGeometryRefusal) -> Unit,
    ): NormalizedDrawCommand? {
        var loweringRefusal: GPUCorePrimitiveGeometryRefusal? = null
        val command = try {
            when (operation) {
            is DisplayOp.DrawColor -> operation.toNormalizedCommand(commandId, target)
            is DisplayOp.Clear -> operation.toNormalizedCommand(commandId, target)
            is DisplayOp.DrawPoint -> DisplayOp.DrawPoints(
                PointMode.POINTS,
                listOf(Point2F32(operation.x, operation.y)),
                operation.paint,
                operation.transform,
                operation.clip,
            ).let { points ->
                DisplayOp.DrawPath(
                    points.toPath(),
                    points.paint,
                    points.transform,
                    points.clip,
                ).toPathCommand(commandId, target, config).copy(stroke = false)
            }
            is DisplayOp.DrawRect -> if (operation.paint.isStroke()) {
                operation.toStrokePathCommand(commandId, target)
            } else {
                operation.toNormalizedCommand(commandId, target)
            }
            is DisplayOp.DrawRRect -> if (operation.paint.isStroke()) {
                DisplayOp.DrawPath(
                    Path().addRRect(operation.rrect),
                    operation.paint,
                    operation.transform,
                    operation.clip,
                ).toPathCommand(commandId, target, config)
            } else {
                operation.toNormalizedCommand(commandId, target)
            }
            is DisplayOp.DrawPath -> operation.toPathCommand(commandId, target, config)
            is DisplayOp.DrawPoints -> DisplayOp.DrawPath(
                operation.toPath(),
                operation.paint,
                operation.transform,
                operation.clip,
            ).toPathCommand(commandId, target, config).copy(
                stroke = operation.mode != PointMode.POINTS,
            )
            is DisplayOp.DrawDRRect -> {
                DisplayOp.DrawPath(
                    operation.toPath(),
                    operation.paint,
                    operation.transform,
                    operation.clip,
                ).toPathCommand(commandId, target, config)
            }
                else -> null
            }
        } catch (failure: IllegalStateException) {
            if (!failure.isPathVertexBudgetFailure() || !operation.isCorePathOperation()) throw failure
            loweringRefusal = GPUCorePrimitiveGeometryRefusal(
                code = "unsupported.core_primitive.path_vertex_budget",
                refusalFacts = mapOf(
                    "maxPathVertices" to config.maxPathVertices.toString(),
                    "reason" to (failure.message ?: "path_vertex_budget"),
                ),
            ).also(onGeometryRefusal)
            operation.toPathBudgetPlaceholder(commandId, target)
        } ?: return null

        val targetBounds = when {
            loweringRefusal != null -> command.clip.bounds.clampedTo(target)
            operation is DisplayOp.DrawPath && operation.path.fillType.isInverse ->
                command.clip.bounds.clampedTo(target)
            operation.coreGeometryRefusalOrNull() != null -> command.clip.bounds.clampedTo(target)
            else -> operation.localGeometryBounds(command)
                .outset(operation.conservativeStrokeOutset())
                .mappedBy(operation.transformOrIdentity())
                .outset(operation.deviceAntiAliasOutset())
                .clampedTo(target)
        }
        val coverage = command.geometryCoverage()
        val scalarCoverage = coverage == GPUCoverageConsumption.ScalarCoverage
        val blendPlan = command.blend.canonicalBlendPlan(
            if (scalarCoverage) GPUCoverageConsumption.FullOrScissor else coverage,
        ).let { plan -> if (scalarCoverage) plan.forCorePrimitiveAnalyticShapeCoverage() else plan }
        val ordering = GPUOrderingFacts(
            paintOrder = paintOrder,
            dependsOnDestination = blendPlan.destinationReadRequirement ==
                GPUBlendDestinationReadRequirement.DestinationTextureRequired,
            requiresBarrier = false,
        )
        val source = GPUCommandSource(
            adapter = "kanvas-surface",
            operation = operation.coreSourceOperation(),
            frameProvenance = provenance,
        )
        return when (command) {
            is NormalizedDrawCommand.FillRect -> command.copy(bounds = targetBounds, ordering = ordering, source = source)
            is NormalizedDrawCommand.FillRRect -> command.copy(bounds = targetBounds, ordering = ordering, source = source)
            is NormalizedDrawCommand.FillPath -> command.copy(
                bounds = targetBounds,
                ordering = ordering,
                source = source,
                pathDescriptor = command.pathDescriptor.copy(
                    verbCount = operation.pathVerbCount(),
                    transformClass = command.transform.type.name.lowercase(),
                ),
            )
            else -> error("Slice 12A mapper produced a non-core command")
        }
    }
}

private fun NormalizedDrawCommand.FillPath.toPreparedStrokeFillPath():
    NormalizedDrawCommand.FillPath? {
    if (!stroke) return null
    val cap = when (strokeCap) {
        "round" -> org.graphiks.kanvas.paint.StrokeCap.ROUND
        "square" -> org.graphiks.kanvas.paint.StrokeCap.SQUARE
        else -> org.graphiks.kanvas.paint.StrokeCap.BUTT
    }
    val join = when (strokeJoin) {
        "round" -> org.graphiks.kanvas.paint.StrokeJoin.ROUND
        "bevel" -> org.graphiks.kanvas.paint.StrokeJoin.BEVEL
        else -> org.graphiks.kanvas.paint.StrokeJoin.MITER
    }
    val fill = strokeToFillGeometry(
        contourVertices = tessellatedVertices,
        contourStarts = contourStarts,
        strokeWidth = strokeWidth,
        dashArray = dashIntervals,
        dashPhase = dashPhase,
        capStyle = cap,
        joinStyle = join,
        miterLimit = strokeMiterLimit,
        transform = transform,
    )
    check(fill.coordinateSpace == StrokeGeometryCoordinateSpace.DEVICE)
    if (fill.vertices.isEmpty() || fill.vertices.any { vertex -> !vertex.isFinite() }) {
        return null
    }
    val vertexCount = fill.vertices.size / 2
    val exactContourStarts = fill.contourStarts
        .filter { start -> start in 0 until vertexCount }
        .distinct()
        .ifEmpty { listOf(0) }
    val preparedPathKey = preparedStrokeGeometryPathKey(
        vertices = fill.vertices,
        contourStarts = exactContourStarts,
    )
    return copy(
        pathKey = preparedPathKey,
        pathDescriptor = pathDescriptor.copy(
            pathKey = preparedPathKey,
            verbCount = vertexCount + exactContourStarts.size,
            pointCount = vertexCount,
            fillRule = "winding",
            inverseFill = false,
            finiteProof = "all_finite",
            transformClass = "identity",
            edgeCount = vertexCount,
        ),
        tessellatedVertices = fill.vertices,
        contourStarts = exactContourStarts,
        totalVertexCount = vertexCount,
        edgeCount = vertexCount,
        transform = GPUTransformFacts.identity(),
        bounds = computeBounds(fill.vertices),
        source = source.copy(operation = "drawText.stroke-path"),
        stroke = false,
    )
}

private sealed interface GPUPreparedTextVisualLowering {
    data class Ready(val command: GPUFramePathVisualCommand) : GPUPreparedTextVisualLowering
    data object Culled : GPUPreparedTextVisualLowering
    data object Invalid : GPUPreparedTextVisualLowering
}

private fun GPUPreparedTextSubRun.toPreparedTextVisual(
    commandId: Int,
    provenance: GPUFrameProvenance,
    target: GPUTargetFacts,
    config: RenderConfig,
    capabilities: GPUCapabilities,
    inventory: PreparedTextFrameInventory,
): GPUPreparedTextVisualLowering {
    if (draw.operationIndex != operationIndex ||
        draw.material.materialKey != materialKey ||
        draw.blendPlan.canonicalIdentity() != blendPlanIdentity ||
        draw.clipContentKey != clipIdentity ||
        draw.capabilitySnapshotHash != capabilities.canonicalSnapshotHash() ||
        instances.isEmpty() ||
        representation == GPUPreparedTextRepresentation.A8_MASK && colorGlyphLayerPlan != null ||
        representation == GPUPreparedTextRepresentation.COLRV0 && colorGlyphLayerPlan == null
    ) {
        return GPUPreparedTextVisualLowering.Invalid
    }
    val page = pageIndex?.let { index ->
        inventory.pages.singleOrNull { candidate -> candidate.pageIndex == index }
    } ?: return GPUPreparedTextVisualLowering.Invalid
    if (page.artifactKey.generation != inventory.generation ||
        instances.any { instance -> instance.pageIndex != page.pageIndex }
    ) {
        return GPUPreparedTextVisualLowering.Invalid
    }
    val bounds = instances.preparedTextBounds(target) ?: return GPUPreparedTextVisualLowering.Invalid
    val clipFacts = draw.clip.toGPUClipFacts(target)
    val maxTextureDimension = capabilities.limits?.maxTextureDimension2D
        ?.coerceAtMost(Int.MAX_VALUE.toLong())
        ?.toInt()
        ?: maxOf(target.width, target.height)
    val clipCoverage = clipFacts.coverageRequest?.let { request ->
        if (request.contentKey != draw.clipContentKey) return GPUPreparedTextVisualLowering.Invalid
        GPUClipCoveragePlanner.planForFrameRoute(request, config, maxTextureDimension)
    } ?: if (draw.clipContentKey == "prepared-text-clip:wide-open") {
        GPUClipCoveragePlan.NoClip
    } else {
        return GPUPreparedTextVisualLowering.Invalid
    }
    if (clipCoverage is GPUClipCoveragePlan.Refused) return GPUPreparedTextVisualLowering.Invalid
    if (clipCoverage is GPUClipCoveragePlan.Scissor && clipCoverage.isTargetEmpty(target)) {
        return GPUPreparedTextVisualLowering.Culled
    }
    val clipExecution = clipCoverage.toExecutionPlan(capabilities, target)
    val artifactRef = GPUTextArtifactRef(
        artifactType = "PreparedTextA8AtlasPage",
        artifactId = page.artifactKey.artifactID.value.toString(),
        artifactKeyHash = page.artifactKey.contentFingerprint,
        generation = page.artifactKey.generation,
        routeHint = "AtlasMaskSample",
    )
    val stableRunIdentity =
        "prepared-text:${inventory.contentSha256}:operation=$operationIndex:subrun=$subRunIndex"
    val normalized = NormalizedDrawCommand.DrawTextRun(
        commandId = GPUDrawCommandID(commandId),
        textLayoutResultId = "prepared-text:${inventory.contentSha256}",
        glyphRunId = stableRunIdentity,
        glyphRunDescriptorRefs = listOf(stableRunIdentity),
        glyphRunDescriptor = null,
        colorGlyphPlans = listOfNotNull(colorGlyphLayerPlan),
        artifactRefs = listOf(artifactRef),
        artifactKeyHashes = listOf(artifactRef.artifactKeyHash),
        atlasGenerations = listOf(GPUTextArtifactGeneration(inventory.generation.value)),
        uploadDependencyFacts = listOf("upload-before-sample:${page.artifactKey.contentFingerprint}"),
        routeDiagnostics = emptyList(),
        transform = draw.transform.toGPUTransformFacts(),
        clip = clipFacts.copy(
            coveragePlan = clipCoverage,
            executionPlan = clipExecution,
        ),
        layer = GPULayerFacts.root(target),
        preparedMaterial = draw.material,
        blend = draw.blendPlan.mode.toPaintBlendMode().toGpuBlendFacts(),
        preparedBlendPlan = draw.blendPlan,
        bounds = bounds,
        ordering = GPUOrderingFacts(
            paintOrder = commandId,
            dependsOnDestination = draw.blendPlan.destinationReadRequirement ==
                GPUBlendDestinationReadRequirement.DestinationTextureRequired,
            requiresBarrier = false,
        ),
        source = GPUCommandSource(
            adapter = "kanvas-surface",
            operation = "drawText.prepared:$operationIndex:$subRunIndex",
            frameProvenance = provenance,
        ),
    )
    return GPUPreparedTextVisualLowering.Ready(GPUFramePathVisualCommand(
        normalized = normalized,
        targetSpaceBounds = bounds,
        geometryCoverage = GPUCoverageConsumption.ScalarCoverage,
        clipCoverage = clipCoverage,
        clipExecutionPlan = clipExecution,
        blendPlan = draw.blendPlan,
        provenance = provenance,
        preparedText = this,
    ))
}

private fun GPUClipCoveragePlan.Scissor.isTargetEmpty(target: GPUTargetFacts): Boolean {
    val scalars = listOf(bounds.left, bounds.top, bounds.right, bounds.bottom)
    if (scalars.any { value -> !value.isFinite() || value != value.toInt().toFloat() }) {
        return false
    }
    val left = bounds.left.toInt().coerceIn(0, target.width)
    val top = bounds.top.toInt().coerceIn(0, target.height)
    val right = bounds.right.toInt().coerceIn(0, target.width)
    val bottom = bounds.bottom.toInt().coerceIn(0, target.height)
    return right <= left || bottom <= top
}

private fun GPUBlendMode.toPaintBlendMode(): BlendMode = when (this) {
    GPUBlendMode.CLEAR -> BlendMode.CLEAR
    GPUBlendMode.SRC_OVER -> BlendMode.SRC_OVER
    GPUBlendMode.SRC -> BlendMode.SRC
    GPUBlendMode.DST -> BlendMode.DST
    GPUBlendMode.DST_OVER -> BlendMode.DST_OVER
    GPUBlendMode.SRC_IN -> BlendMode.SRC_IN
    GPUBlendMode.DST_IN -> BlendMode.DST_IN
    GPUBlendMode.SRC_OUT -> BlendMode.SRC_OUT
    GPUBlendMode.DST_OUT -> BlendMode.DST_OUT
    GPUBlendMode.SRC_ATOP -> BlendMode.SRC_ATOP
    GPUBlendMode.DST_ATOP -> BlendMode.DST_ATOP
    GPUBlendMode.XOR -> BlendMode.XOR
    GPUBlendMode.PLUS -> BlendMode.PLUS
    GPUBlendMode.MODULATE -> BlendMode.MODULATE
    GPUBlendMode.MULTIPLY -> BlendMode.MULTIPLY
    GPUBlendMode.SCREEN -> BlendMode.SCREEN
    GPUBlendMode.OVERLAY -> BlendMode.OVERLAY
    GPUBlendMode.DARKEN -> BlendMode.DARKEN
    GPUBlendMode.LIGHTEN -> BlendMode.LIGHTEN
    GPUBlendMode.COLOR_DODGE -> BlendMode.COLOR_DODGE
    GPUBlendMode.COLOR_BURN -> BlendMode.COLOR_BURN
    GPUBlendMode.HARD_LIGHT -> BlendMode.HARD_LIGHT
    GPUBlendMode.SOFT_LIGHT -> BlendMode.SOFT_LIGHT
    GPUBlendMode.DIFFERENCE -> BlendMode.DIFFERENCE
    GPUBlendMode.EXCLUSION -> BlendMode.EXCLUSION
    GPUBlendMode.HUE -> BlendMode.HUE
    GPUBlendMode.SATURATION -> BlendMode.SATURATION
    GPUBlendMode.COLOR -> BlendMode.COLOR
    GPUBlendMode.LUMINOSITY -> BlendMode.LUMINOSITY
}

private fun Throwable.isPathVertexBudgetFailure(): Boolean =
    message?.let { it.startsWith("Path flattened to ") || it.startsWith("Path has ") } == true

private fun DisplayOp.isCorePathOperation(): Boolean = when (this) {
    is DisplayOp.DrawPoint,
    is DisplayOp.DrawPoints,
    is DisplayOp.DrawPath,
    is DisplayOp.DrawDRRect,
    is DisplayOp.DrawRRect,
    is DisplayOp.DrawRect,
    -> true
    else -> false
}

private fun DisplayOp.toPathBudgetPlaceholder(
    commandId: GPUDrawCommandID,
    target: GPUTargetFacts,
): NormalizedDrawCommand.FillPath {
    val (paint, clip) = when (this) {
        is DisplayOp.DrawPoint -> paint to clip
        is DisplayOp.DrawPoints -> paint to clip
        is DisplayOp.DrawRect -> paint to clip
        is DisplayOp.DrawRRect -> paint to clip
        is DisplayOp.DrawDRRect -> paint to clip
        is DisplayOp.DrawPath -> paint to clip
        else -> error("Path budget placeholder requires a core path operation")
    }
    return DisplayOp.DrawPath(Path(), paint, transformOrIdentity(), clip).toNormalizedCommand(
        commandId,
        target,
        tessellatedVertices = emptyList(),
        contourStarts = listOf(0),
        edgeCount = 0,
    )
}

private fun DisplayOp.coreGeometryRefusalOrNull(): GPUCorePrimitiveGeometryRefusal? {
    val transform = transformOrIdentity()
    val transformValues = listOf(
        transform.sx, transform.kx, transform.tx,
        transform.ky, transform.sy, transform.ty,
        transform.persp0, transform.persp1, transform.persp2,
    )
    if (!transformValues.all(Float::isFinite)) {
        return GPUCorePrimitiveGeometryRefusal(
            "unsupported.core_primitive.geometry.non_finite_transform",
            mapOf("operation" to coreSourceOperation()),
        )
    }
    if (transform.hasPerspective()) {
        return GPUCorePrimitiveGeometryRefusal(
            "unsupported.core_primitive.geometry.non_affine_transform",
            mapOf("operation" to coreSourceOperation()),
        )
    }
    if (this is DisplayOp.DrawRRect && (transform.kx != 0f || transform.ky != 0f)) {
        return GPUCorePrimitiveGeometryRefusal(
            "unsupported.core_primitive.rrect.non_axis_aligned_transform",
            mapOf("operation" to coreSourceOperation()),
        )
    }
    return (this as? DisplayOp.DrawDRRect)?.exactLoweringRefusalOrNull()
}

private fun DisplayOp.DrawDRRect.exactLoweringRefusalOrNull(): GPUCorePrimitiveGeometryRefusal? {
    val outerRect = outer.rect
    val innerRect = inner.rect
    if (!listOf(
        outerRect.left, outerRect.top, outerRect.right, outerRect.bottom,
        innerRect.left, innerRect.top, innerRect.right, innerRect.bottom,
        outer.topLeft.x, outer.topLeft.y, outer.topRight.x, outer.topRight.y,
        outer.bottomRight.x, outer.bottomRight.y, outer.bottomLeft.x, outer.bottomLeft.y,
        inner.topLeft.x, inner.topLeft.y, inner.topRight.x, inner.topRight.y,
        inner.bottomRight.x, inner.bottomRight.y, inner.bottomLeft.x, inner.bottomLeft.y,
    ).all(Float::isFinite)) {
        return GPUCorePrimitiveGeometryRefusal("unsupported.core_primitive.drrect.non_finite", emptyMap())
    }
    if (!listOf(
        outer.topLeft.x, outer.topLeft.y, outer.topRight.x, outer.topRight.y,
        outer.bottomRight.x, outer.bottomRight.y, outer.bottomLeft.x, outer.bottomLeft.y,
        inner.topLeft.x, inner.topLeft.y, inner.topRight.x, inner.topRight.y,
        inner.bottomRight.x, inner.bottomRight.y, inner.bottomLeft.x, inner.bottomLeft.y,
    ).all { it >= 0f }) {
        return GPUCorePrimitiveGeometryRefusal("unsupported.core_primitive.drrect.negative_radius", emptyMap())
    }
    if (!(outerRect.left < outerRect.right && outerRect.top < outerRect.bottom &&
        innerRect.left < innerRect.right && innerRect.top < innerRect.bottom
    )) {
        return GPUCorePrimitiveGeometryRefusal("unsupported.core_primitive.drrect.empty", emptyMap())
    }
    if (!(innerRect.left >= outerRect.left && innerRect.top >= outerRect.top &&
        innerRect.right <= outerRect.right && innerRect.bottom <= outerRect.bottom
    )) {
        return GPUCorePrimitiveGeometryRefusal("unsupported.core_primitive.drrect.inner_outside_outer", emptyMap())
    }
    return null
}

private fun DisplayOp.coreSourceOperation(): String = when (this) {
    is DisplayOp.DrawColor -> "drawColor"
    is DisplayOp.Clear -> "clear"
    is DisplayOp.DrawPoint -> "drawPoint"
    is DisplayOp.DrawPoints -> "drawPoints.${mode.name.lowercase()}"
    is DisplayOp.DrawRect -> if (paint.isStroke()) "drawRect.stroke" else "drawRect"
    is DisplayOp.DrawRRect -> if (paint.isStroke()) "drawRRect.stroke" else "drawRRect"
    is DisplayOp.DrawDRRect -> "drawDRRect"
    is DisplayOp.DrawPath -> sourceOperation
    else -> error("Non-core operation has no Slice 12A source identity")
}

private fun DisplayOp.DrawPath.toPathCommand(
    commandId: GPUDrawCommandID,
    target: GPUTargetFacts,
    config: RenderConfig,
): NormalizedDrawCommand.FillPath {
    val flattened = PathTessellator(
        tolerance = config.curveTolerance,
        maxVertices = config.maxPathVertices.toInt(),
    ).flattenWithContours(path.toPathTessellatorData())
    return toNormalizedCommand(
        commandId,
        target,
        flattened.points.flatMap { point -> listOf(point.x, point.y) },
        flattened.contourStarts.ifEmpty { listOf(0) },
        flattened.points.size,
    )
}

private fun NormalizedDrawCommand.geometryCoverage(): GPUCoverageConsumption = when (this) {
    is NormalizedDrawCommand.FillPath -> GPUCoverageConsumption.StencilCoverage1x
    is NormalizedDrawCommand.FillRRect -> if (antiAlias) {
        GPUCoverageConsumption.ScalarCoverage
    } else {
        GPUCoverageConsumption.FullOrScissor
    }
    is NormalizedDrawCommand.FillRect -> if (antiAlias) {
        GPUCoverageConsumption.ScalarCoverage
    } else {
        GPUCoverageConsumption.FullOrScissor
    }
    else -> error("Geometry coverage requested for a non-Slice-12A command")
}

private fun NormalizedDrawCommand.withClipPlans(
    coveragePlan: GPUClipCoveragePlan,
    executionPlan: GPUClipExecutionPlan,
): NormalizedDrawCommand = when (this) {
    is NormalizedDrawCommand.FillRect -> copy(
        clip = clip.copy(coveragePlan = coveragePlan, executionPlan = executionPlan),
    )
    is NormalizedDrawCommand.FillRRect -> copy(
        clip = clip.copy(coveragePlan = coveragePlan, executionPlan = executionPlan),
    )
    is NormalizedDrawCommand.FillPath -> copy(
        clip = clip.copy(coveragePlan = coveragePlan, executionPlan = executionPlan),
    )
    else -> error("Clip coverage attached to a non-Slice-12A command")
}

private fun GPUClipCoveragePlan.toExecutionPlan(
    capabilities: GPUCapabilities,
    target: GPUTargetFacts,
    admitAnalyticMultiRect: Boolean = false,
): GPUClipExecutionPlan = when (this) {
    GPUClipCoveragePlan.NoClip -> GPUClipExecutionPlan.NoClip
    is GPUClipCoveragePlan.Scissor -> toScissorExecutionPlan(capabilities, target)
    is GPUClipCoveragePlan.AnalyticIntersection -> toAnalyticIntersectionExecutionPlan(capabilities)
    is GPUClipCoveragePlan.Refused -> GPUClipExecutionPlan.Refused(
        code = code,
        message = "Clip coverage planning refused before execution classification.",
    )
    is GPUClipCoveragePlan.Mask -> toMaskExecutionPlan(capabilities, target, admitAnalyticMultiRect)
}

/** True when the normalized command carries a mask blur filter (the mask-blur composite lane). */
private fun NormalizedDrawCommand.hasBlurMaskFilter(): Boolean = when (this) {
    is NormalizedDrawCommand.FillRect -> maskFilter != null
    is NormalizedDrawCommand.FillRRect -> maskFilter != null
    is NormalizedDrawCommand.FillPath -> maskFilter != null
    else -> false
}

private fun GPUClipCoveragePlan.AnalyticIntersection.toAnalyticIntersectionExecutionPlan(
    capabilities: GPUCapabilities,
): GPUClipExecutionPlan {
    if (!capabilities.supportsClipCapability(BOUNDED_CLIP_NATIVE)) {
        return clipExecutionRefusal(
            code = "unsupported.clip.analytic_unavailable",
            message = "Analytic rect/rrect clip execution requires bounded clip support.",
        )
    }
    val analyticElements = elements.map { element ->
        GPUClipAnalyticElement(
            geometry = element.executionGeometryOrRefusal()
                ?: return invalidClipGeometryRefusal(element),
            antiAlias = element.antiAlias,
        )
    }
    return GPUClipExecutionPlan.AnalyticIntersection(analyticElements)
}

private fun GPUClipCoveragePlan.Scissor.toScissorExecutionPlan(
    capabilities: GPUCapabilities,
    target: GPUTargetFacts,
): GPUClipExecutionPlan {
    if (!capabilities.supportsClipCapability(SCISSOR_NATIVE)) {
        return clipExecutionRefusal(
            code = "unsupported.clip.scissor_unavailable",
            message = "Integral device clip execution requires native scissor support.",
        )
    }
    val scalars = listOf(bounds.left, bounds.top, bounds.right, bounds.bottom)
    if (scalars.any { value -> !value.isFinite() || value != value.toInt().toFloat() }) {
        return clipExecutionRefusal(
            code = "unsupported.clip.scissor_invalid",
            message = "Native scissor bounds must be finite integral device pixels.",
        )
    }
    val left = bounds.left.toInt().coerceIn(0, target.width)
    val top = bounds.top.toInt().coerceIn(0, target.height)
    val right = bounds.right.toInt().coerceIn(0, target.width)
    val bottom = bounds.bottom.toInt().coerceIn(0, target.height)
    return if (right <= left || bottom <= top) {
        clipExecutionRefusal(
            code = "unsupported.clip.scissor_empty",
            message = "Native scissor classification produced empty target bounds.",
        )
    } else {
        GPUClipExecutionPlan.ScissorOnly(GPUPixelBounds(left, top, right, bottom))
    }
}

private fun GPUClipCoveragePlan.Mask.toMaskExecutionPlan(
    capabilities: GPUCapabilities,
    target: GPUTargetFacts,
    admitAnalyticMultiRect: Boolean,
): GPUClipExecutionPlan {
    val single = elements.singleOrNull()
    if (
        single != null &&
        single.operation == GPUClipCoverageOperation.Intersect &&
        !single.inverseFill &&
        single.kind != GPUClipCoverageElementKind.Path
    ) {
        if (!capabilities.supportsClipCapability(BOUNDED_CLIP_NATIVE)) {
            return clipExecutionRefusal(
                code = "unsupported.clip.analytic_unavailable",
                message = "Analytic rect/rrect clip execution requires bounded clip support.",
            )
        }
        return single.executionGeometryOrRefusal()?.let { geometry ->
            GPUClipExecutionPlan.AnalyticCoverage(
                geometry = geometry,
                scissor = null,
                antiAlias = single.antiAlias,
            )
        } ?: invalidClipGeometryRefusal(single)
    }

    if (
        single != null &&
        single.operation == GPUClipCoverageOperation.Intersect &&
        single.kind == GPUClipCoverageElementKind.Path &&
        !single.antiAlias
    ) {
        if (!capabilities.supportsClipCapability(PATH_FILL_STENCIL_COVER)) {
            return clipExecutionRefusal(
                code = "unsupported.clip.stencil_unavailable",
                message = "Path clip execution requires stencil-cover support.",
            )
        }
        if (!capabilities.supportsClipCapability(BOUNDED_CLIP_NATIVE)) {
            return clipExecutionRefusal(
                code = "unsupported.clip.mask_unavailable",
                message = "Path clip execution requires bounded clip support.",
            )
        }
        val geometry = single.executionGeometryOrRefusal() as? GPUClipExecutionGeometry.Path
            ?: return invalidClipGeometryRefusal(single)
        val targetBounds = GPUPixelBounds(0, 0, target.width, target.height)
        val (frontPassOperation, backPassOperation) = when (geometry.fillRule) {
            org.graphiks.kanvas.gpu.renderer.clips.GPUClipFillRule.Winding ->
                GPUClipStencilOperation.IncrementWrap to GPUClipStencilOperation.DecrementWrap
            org.graphiks.kanvas.gpu.renderer.clips.GPUClipFillRule.EvenOdd ->
                GPUClipStencilOperation.Invert to GPUClipStencilOperation.Invert
        }
        return GPUClipExecutionPlan.StencilCoverage(
            contentKey = contentKey,
            bounds = targetBounds,
            sampleCount = sampleCount,
            atomicGroup = GPUClipAtomicGroupID("clip-atomic:$contentKey"),
            orderingToken = GPUClipOrderingToken("clip-order:$contentKey"),
            producer = GPUClipStencilProducerPlan(
                geometry = geometry,
                scissor = null,
                fillRule = geometry.fillRule,
                reference = 0u,
                compare = GPUClipStencilCompare.Always,
                frontPassOperation = frontPassOperation,
                backPassOperation = backPassOperation,
                loadOperation = GPUClipStencilLoadOperation.Clear,
                storeOperation = GPUClipStencilStoreOperation.Store,
                clearValue = 0u,
            ),
            consumer = GPUClipStencilConsumerPlan(
                scissor = null,
                reference = 0u,
                compare = if (geometry.inverseFill) {
                    GPUClipStencilCompare.Equal
                } else {
                    GPUClipStencilCompare.NotEqual
                },
            ),
        )
    }

    if (!capabilities.supportsClipCapability(BOUNDED_CLIP_NATIVE)) {
        return clipExecutionRefusal(
            code = "unsupported.clip.mask_unavailable",
            message = "Ordered clip-mask execution requires bounded clip support.",
        )
    }
    // A rect-decomposable complex clip lowers to bounded analytic
    // multi-rect coverage instead of a coverage mask, scoped to the mask-blur
    // composite lane (the only consumer whose composite shader folds the per-rect
    // coverage). Non-blur consumers keep their prior CoverageMask route, so a
    // rect INTERSECT + orthogonal-polygon DIFFERENCE clip keeps rendering through
    // the coverage-mask producer/consumer topology. Admission is also scoped to the
    // rect-decomposable case only: every element must be a rect/rrect or a
    // non-inverse axis-aligned orthogonal polygon DIFFERENCE path (the blur
    // fixture's notch), at least one element must be such a path, and the decomposed
    // rect count must fit the fixed analytic block. Coverage-mask and stacked clips
    // (including a plain rect-vs-rect difference and inverse fills) stay terminal.
    if (admitAnalyticMultiRect) {
        val analyticMultiRect = toAnalyticMultiRectOrNull()
        if (analyticMultiRect != null) {
            return GPUClipExecutionPlan.AnalyticMultiRect(analyticMultiRect)
        }
    }
    val producers = elements.mapIndexed { index, element ->
        val geometry = element.executionGeometryOrRefusal()
            ?: return invalidClipGeometryRefusal(element)
        GPUClipMaskProducerPlan(
            sourceOrder = index,
            geometry = geometry,
            combine = when (element.operation) {
                GPUClipCoverageOperation.Intersect -> GPUClipMaskCombine.Intersect
                GPUClipCoverageOperation.Difference -> GPUClipMaskCombine.Difference
            },
            antiAlias = element.antiAlias,
        )
    }
    return GPUClipExecutionPlan.CoverageMask(
        contentKey = contentKey,
        bounds = GPUPixelBounds(0, 0, target.width, target.height),
        sampleCount = sampleCount,
        depthStencilRequired = elements.any { it.kind == GPUClipCoverageElementKind.Path },
        orderingToken = GPUClipOrderingToken("clip-order:$contentKey"),
        producers = producers,
        consumer = GPUClipMaskConsumerPlan(),
    )
}

private fun GPUClipCoverageElement.executionGeometryOrRefusal(): GPUClipExecutionGeometry? = try {
    when (kind) {
        GPUClipCoverageElementKind.Rect -> GPUClipExecutionGeometry.Rect(
            GPUClipBounds(values[0], values[1], values[2], values[3]),
        )
        GPUClipCoverageElementKind.RRect -> GPUClipExecutionGeometry.RRect(
            bounds = GPUClipBounds(values[0], values[1], values[2], values[3]),
            radii = values.subList(4, 12),
        )
        GPUClipCoverageElementKind.Path -> {
            val contourCount = values.first().toInt()
            GPUClipExecutionGeometry.Path(
                vertices = values.subList(1 + contourCount, values.size),
                contourStarts = values.subList(1, 1 + contourCount).map(Float::toInt),
                fillRule = fillRule,
                inverseFill = inverseFill,
            )
        }
    }
} catch (_: IllegalArgumentException) {
    null
} catch (_: IndexOutOfBoundsException) {
    null
}

private fun invalidClipGeometryRefusal(
    element: GPUClipCoverageElement,
): GPUClipExecutionPlan.Refused = clipExecutionRefusal(
    code = "unsupported.clip.execution_geometry_invalid",
    message = "${element.kind.name} clip geometry cannot be represented by the execution contract.",
)

/**
 * Attempts the analytic multi-rect lowering: a complex clip whose elements are all
 * rect/rrect or a non-inverse axis-aligned orthogonal polygon **DIFFERENCE** path
 * (INTERSECT paths are rejected — the composite folds one-minus-coverage per rect,
 * so only DIFFERENCE unions decompose exactly), with at least one such path,
 * decomposed into a bounded ordered rect list for analytic multi-rect execution.
 * Returns null when the clip must stay on the coverage-mask route (rect-vs-rect
 * differences, inverse fills, curved or multi-contour paths, INTERSECT paths,
 * self-intersecting Winding polygons, or decomposed counts beyond the fixed
 * analytic block).
 */
private fun GPUClipCoveragePlan.Mask.toAnalyticMultiRectOrNull(): List<GPUClipAnalyticRectElement>? {
    var sawRectDecomposedPath = false
    val primitives = mutableListOf<AnalyticRectPrimitive>()
    for (element in elements) {
        val elementPrimitives = when (element.kind) {
            GPUClipCoverageElementKind.Rect -> {
                if (element.values.size != 4) return null
                listOf(
                    AnalyticRectPrimitive(
                        element.values[0],
                        element.values[1],
                        element.values[2],
                        element.values[3],
                        element.operation,
                        element.antiAlias,
                    ),
                )
            }
            GPUClipCoverageElementKind.RRect -> return null
            GPUClipCoverageElementKind.Path -> {
                val decomposed = decomposeOrthogonalPolygon(element) ?: return null
                sawRectDecomposedPath = true
                decomposed
            }
        }
        primitives += elementPrimitives
    }
    if (!sawRectDecomposedPath) return null
    if (primitives.size !in 1..GPU_ANALYTIC_MULTI_RECT_MAX_ELEMENTS) return null
    if (primitives.map { it.antiAlias }.distinct().size != 1) return null
    return primitives.map { primitive ->
        GPUClipAnalyticRectElement(
            geometry = GPUClipExecutionGeometry.Rect(
                GPUClipBounds(primitive.left, primitive.top, primitive.right, primitive.bottom),
            ),
            antiAlias = primitive.antiAlias,
            operation = when (primitive.operation) {
                GPUClipCoverageOperation.Intersect -> GPUClipMaskCombine.Intersect
                GPUClipCoverageOperation.Difference -> GPUClipMaskCombine.Difference
            },
        )
    }
}

private data class AnalyticRectPrimitive(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val operation: GPUClipCoverageOperation,
    val antiAlias: Boolean,
)

/**
 * Decomposes a single-contour, non-inverse, axis-aligned orthogonal polygon DIFFERENCE
 * path into its axis-aligned band rects (scanline even-odd). Returns null for anything
 * outside that closed shape family so the clip stays on the coverage-mask route. Only
 * DIFFERENCE paths decompose safely: the composite folds one-minus-coverage per rect, so
 * a disjoint union of difference rects is exact, while an INTERSECT path would multiply
 * disjoint rect coverages to zero (an empty clip). A Winding-filled polygon must also be
 * simple — the even-odd scanline is exact for EvenOdd always and for Winding only when
 * the non-zero winding number stays within {-1, 0, 1} along the sweep.
 */
private fun decomposeOrthogonalPolygon(
    element: GPUClipCoverageElement,
): List<AnalyticRectPrimitive>? {
    if (element.inverseFill) return null
    if (element.operation != GPUClipCoverageOperation.Difference) return null
    val values = element.values
    val vertexCount = element.vertexCount
    val contourCount = values.first().toInt()
    if (contourCount != 1) return null
    val coordinateStart = 1 + contourCount
    if (values.size != coordinateStart + vertexCount * 2) return null
    if (vertexCount == 0) return null
    val points = (0 until vertexCount).map { index ->
        values[coordinateStart + index * 2] to values[coordinateStart + index * 2 + 1]
    }
    for (index in 0 until vertexCount) {
        val (x0, y0) = points[index]
        val (x1, y1) = points[(index + 1) % vertexCount]
        if (x0 != x1 && y0 != y1) return null
    }
    val ys = points.map { it.second }.distinct().sorted()
    if (ys.size < 2) return null
    val requiresSimpleWinding = element.fillRule == GPUClipFillRule.Winding
    val rects = mutableListOf<AnalyticRectPrimitive>()
    for (bandIndex in 0 until ys.size - 1) {
        val top = ys[bandIndex]
        val bottom = ys[bandIndex + 1]
        val midY = (top + bottom) * 0.5f
        val crossings = mutableListOf<Pair<Float, Boolean>>()
        for (index in 0 until vertexCount) {
            val (x0, y0) = points[index]
            val (x1, y1) = points[(index + 1) % vertexCount]
            if (y0 == y1) continue
            val lo = minOf(y0, y1)
            val hi = maxOf(y0, y1)
            if (midY >= lo && midY < hi) crossings.add(x0 to (y1 < y0))
        }
        crossings.sortBy { it.first }
        if (crossings.size % 2 != 0) return null
        if (requiresSimpleWinding) {
            var winding = 0
            for ((_, upward) in crossings) {
                winding += if (upward) 1 else -1
                if (winding < -1 || winding > 1) return null
            }
        }
        for (pairIndex in 0 until crossings.size / 2) {
            val left = crossings[pairIndex * 2].first
            val right = crossings[pairIndex * 2 + 1].first
            if (right <= left) return null
            rects.add(
                AnalyticRectPrimitive(left, top, right, bottom, element.operation, element.antiAlias),
            )
        }
    }
    if (rects.isEmpty()) return null
    return rects
}

private fun clipExecutionRefusal(code: String, message: String): GPUClipExecutionPlan.Refused =
    GPUClipExecutionPlan.Refused(code = code, message = message)

private fun GPUCapabilities.supportsClipCapability(name: String): Boolean =
    knownUnsupportedFacts.none { fact -> fact.name == name } &&
        facts.any { fact ->
            fact.name == name && fact.value == "supported" && fact.affectsValidity
        }

private fun NormalizedDrawCommand.localBounds(): GPUBounds = when (this) {
    is NormalizedDrawCommand.FillRect -> GPUBounds(rect.left, rect.top, rect.right, rect.bottom)
    is NormalizedDrawCommand.FillRRect -> GPUBounds(
        rrect.rect.left,
        rrect.rect.top,
        rrect.rect.right,
        rrect.rect.bottom,
    )
    is NormalizedDrawCommand.FillPath -> computeBounds(tessellatedVertices)
    else -> bounds
}

private fun DisplayOp.localGeometryBounds(command: NormalizedDrawCommand): GPUBounds = when (this) {
    else -> command.localBounds()
}

private fun DisplayOp.conservativeStrokeOutset(): Float {
    val paint = when (this) {
        is DisplayOp.DrawPoints -> paint.takeIf { mode != PointMode.POINTS }
        is DisplayOp.DrawRect -> paint.takeIf { it.isStroke() }
        is DisplayOp.DrawRRect -> paint.takeIf { it.isStroke() }
        is DisplayOp.DrawPath -> paint.takeIf { it.isStroke() }
        else -> null
    } ?: return 0f
    val halfWidth = if (paint.strokeWidth == 0f) 0f else paint.strokeWidth * 0.5f
    val hasJoins = when (this) {
        is DisplayOp.DrawRect,
        is DisplayOp.DrawRRect,
        is DisplayOp.DrawPath,
        -> true
        else -> false
    }
    val joinMultiplier = if (hasJoins && paint.strokeJoin.name == "MITER") {
        paint.strokeMiter.coerceAtLeast(1f)
    } else {
        1f
    }
    return halfWidth * joinMultiplier
}

private fun DisplayOp.deviceAntiAliasOutset(): Float {
    val antiAlias = when (this) {
        is DisplayOp.DrawPoint -> paint.antiAlias
        is DisplayOp.DrawPoints -> paint.antiAlias
        is DisplayOp.DrawRect -> paint.antiAlias
        is DisplayOp.DrawRRect -> paint.antiAlias
        is DisplayOp.DrawPath -> paint.antiAlias
        else -> false
    }
    return if (antiAlias) 0.5f else 0f
}

private fun GPUBounds.outset(amount: Float): GPUBounds = if (amount == 0f) {
    this
} else {
    GPUBounds(left - amount, top - amount, right + amount, bottom + amount)
}

private fun GPUBounds.mappedBy(matrix: Matrix3x3F32): GPUBounds {
    val corners = listOf(
        matrix.transform(Point2F32(left, top)),
        matrix.transform(Point2F32(right, top)),
        matrix.transform(Point2F32(right, bottom)),
        matrix.transform(Point2F32(left, bottom)),
    )
    return GPUBounds(
        left = corners.minOf(Point2F32::x),
        top = corners.minOf(Point2F32::y),
        right = corners.maxOf(Point2F32::x),
        bottom = corners.maxOf(Point2F32::y),
    )
}

private fun GPUBounds.clampedTo(target: GPUTargetFacts): GPUBounds = GPUBounds(
    left = floor(left).coerceIn(0f, target.width.toFloat()),
    top = floor(top).coerceIn(0f, target.height.toFloat()),
    right = ceil(right).coerceIn(0f, target.width.toFloat()),
    bottom = ceil(bottom).coerceIn(0f, target.height.toFloat()),
)

private fun DisplayOp.transformOrIdentity(): Matrix3x3F32 = when (this) {
    is DisplayOp.DrawColor -> Matrix3x3F32.Identity
    is DisplayOp.DrawPoint -> transform
    is DisplayOp.DrawPoints -> transform
    is DisplayOp.DrawRect -> transform
    is DisplayOp.DrawRRect -> transform
    is DisplayOp.DrawDRRect -> transform
    is DisplayOp.DrawPath -> transform
    is DisplayOp.Clear -> Matrix3x3F32.Identity
    else -> Matrix3x3F32.Identity
}

private fun DisplayOp.pathVerbCount(): Int = when (this) {
    is DisplayOp.DrawPath -> path.verbs().size
    is DisplayOp.DrawPoints -> toPath().verbs().size
    is DisplayOp.DrawDRRect -> toPath().verbs().size
    is DisplayOp.DrawRect -> 5
    is DisplayOp.DrawRRect -> Path().addRRect(rrect).verbs().size
    else -> 0
}

internal fun DisplayOp.DrawRect.toNormalizedCommand(
    cmdId: GPUDrawCommandID,
    target: GPUTargetFacts,
): NormalizedDrawCommand.FillRect {
    val paint = this.paint
    val material = paint.toMaterial()
    val gpRect = GPURect(this.rect.left, this.rect.top, this.rect.right, this.rect.bottom)
    val bounds = GPUBounds(gpRect.left, gpRect.top, gpRect.right, gpRect.bottom)
    val clip = this.clip.toGPUClipFacts(target)
    val transform = this.transform.toGPUTransformFacts()
    return NormalizedDrawCommand.FillRect(
        commandId = cmdId,
        rect = gpRect,
        transform = transform,
        clip = clip,
        layer = GPULayerFacts.root(target),
        material = material,
        bounds = bounds,
        ordering = GPUOrderingFacts(
            paintOrder = 0,
            dependsOnDestination = false,
            requiresBarrier = false,
        ),
        source = GPUCommandSource(adapter = "kanvas-surface", operation = "drawRect"),
        stroke = paint.isStroke(),
        antiAlias = paint.antiAlias,
        blend = paint.blendMode.toGpuBlendFacts(),
        maskFilter = paint.maskFilter.toNormalizedMaskFilter(),
    )
}

internal fun DisplayOp.DrawPath.toNormalizedCommand(
    cmdId: GPUDrawCommandID,
    target: GPUTargetFacts,
    tessellatedVertices: List<Float>,
    contourStarts: List<Int>,
    edgeCount: Int,
): NormalizedDrawCommand.FillPath {
    val paint = this.paint
    val material = paint.toMaterial()
    val bounds = computeBounds(tessellatedVertices)
    val clip = this.clip.toGPUClipFacts(target)
    val transform = this.transform.toGPUTransformFacts()
    val maskFilter = paint.maskFilter.toNormalizedMaskFilter()
    val pathStencilConfig = stencilConfig(path.fillType)
    return NormalizedDrawCommand.FillPath(
        commandId = cmdId,
        pathKey = "path-${cmdId.value}",
        pathDescriptor = GPUPathFacts(
            pathKey = "path-${cmdId.value}",
            verbCount = 0,
            pointCount = tessellatedVertices.size / 2,
            fillRule = pathStencilConfig.fillRule.name,
            inverseFill = pathStencilConfig.inverse,
            finiteProof = if (tessellatedVertices.all(Float::isFinite)) "all_finite" else "non_finite",
            volatility = "static",
            transformClass = "identity",
            edgeCount = edgeCount,
        ),
        tessellatedVertices = tessellatedVertices,
        contourStarts = contourStarts,
        totalVertexCount = tessellatedVertices.size / 2,
        edgeCount = edgeCount,
        transform = transform,
        clip = clip,
        layer = GPULayerFacts.root(target),
        material = material,
        bounds = bounds,
        ordering = GPUOrderingFacts(
            paintOrder = 0,
            dependsOnDestination = false,
            requiresBarrier = false,
        ),
        source = GPUCommandSource(adapter = "kanvas-surface", operation = sourceOperation),
        stroke = paint.isStroke(),
        strokeWidth = paint.strokeWidth,
        dashIntervals = (paint.pathEffect as? PathEffect.Dash)?.intervals,
        dashPhase = (paint.pathEffect as? PathEffect.Dash)?.phase ?: 0f,
        strokeCap = paint.strokeCap.name.lowercase(),
        strokeJoin = paint.strokeJoin.name.lowercase(),
        strokeMiterLimit = paint.strokeMiter,
        antiAlias = paint.antiAlias,
        blend = paint.blendMode.toGpuBlendFacts(),
        maskFilter = maskFilter,
    )
}

/**
 * Converts a stroke-style [DisplayOp.DrawRect] into a [NormalizedDrawCommand.FillPath]
 * so the stroke can be dispatched through the tessellated-path pipeline.
 *
 * Generates a closed contour from the 4 rect corners and copies the paint's
 * stroke parameters (width, cap, join, dash) directly onto the path command.
 * Returns a fill-path command with [FillPath.stroke] set to `true`.
 */
internal fun DisplayOp.DrawRect.toStrokePathCommand(
    cmdId: GPUDrawCommandID,
    target: GPUTargetFacts,
): NormalizedDrawCommand.FillPath {
    val r = this.rect
    val vertices = listOf(r.left, r.top, r.right, r.top, r.right, r.bottom, r.left, r.bottom)
    val edges = 4
    val bounds = computeBounds(vertices)
    val clip = this.clip.toGPUClipFacts(target)
    val transform = this.transform.toGPUTransformFacts()
    val paint = this.paint
    return NormalizedDrawCommand.FillPath(
        commandId = cmdId,
        pathKey = "rect-stroke-${cmdId.value}",
        pathDescriptor = GPUPathFacts(
            pathKey = "rect-stroke-${cmdId.value}",
            verbCount = edges,
            pointCount = edges,
            fillRule = "winding",
            inverseFill = false,
            finiteProof = "all_finite",
            volatility = "static",
            transformClass = transform.type.name.lowercase(),
            edgeCount = edges,
        ),
        tessellatedVertices = vertices,
        contourStarts = listOf(0),
        totalVertexCount = edges,
        edgeCount = edges,
        transform = transform,
        clip = clip,
        layer = GPULayerFacts.root(target),
        material = paint.toMaterial(),
        bounds = bounds,
        ordering = GPUOrderingFacts(
            paintOrder = 0,
            dependsOnDestination = false,
            requiresBarrier = false,
        ),
        source = GPUCommandSource(adapter = "kanvas-surface", operation = "drawRect.stroke"),
        stroke = true,
        strokeWidth = paint.strokeWidth,
        dashIntervals = (paint.pathEffect as? PathEffect.Dash)?.intervals,
        dashPhase = (paint.pathEffect as? PathEffect.Dash)?.phase ?: 0f,
        strokeCap = paint.strokeCap.name.lowercase(),
        strokeJoin = paint.strokeJoin.name.lowercase(),
        strokeMiterLimit = paint.strokeMiter,
        antiAlias = paint.antiAlias,
        maskFilter = paint.maskFilter.toNormalizedMaskFilter(),
    )
}

internal fun DisplayOp.DrawRRect.toNormalizedCommand(
    cmdId: GPUDrawCommandID,
    target: GPUTargetFacts,
): NormalizedDrawCommand.FillRRect {
    val paint = this.paint
    val material = paint.toMaterial()
    val sourceRRect = this.rrect
    val gpRect = GPURect(
        sourceRRect.rect.left, sourceRRect.rect.top,
        sourceRRect.rect.right, sourceRRect.rect.bottom,
    )
    val gpRRect = GPURRect(
        gpRect,
        topLeft = GPURRectCornerRadii(sourceRRect.topLeft.x, sourceRRect.topLeft.y),
        topRight = GPURRectCornerRadii(sourceRRect.topRight.x, sourceRRect.topRight.y),
        bottomRight = GPURRectCornerRadii(sourceRRect.bottomRight.x, sourceRRect.bottomRight.y),
        bottomLeft = GPURRectCornerRadii(sourceRRect.bottomLeft.x, sourceRRect.bottomLeft.y),
    )
    val bounds = GPUBounds(gpRect.left, gpRect.top, gpRect.right, gpRect.bottom)
    val clip = this.clip.toGPUClipFacts(target)
    val transform = this.transform.toGPUTransformFacts()
    return NormalizedDrawCommand.FillRRect(
        commandId = cmdId,
        rrect = gpRRect,
        transform = transform,
        clip = clip,
        layer = GPULayerFacts.root(target),
        material = material,
        bounds = bounds,
        ordering = GPUOrderingFacts(
            paintOrder = 0,
            dependsOnDestination = false,
            requiresBarrier = false,
        ),
        source = GPUCommandSource(adapter = "kanvas-surface", operation = "drawRRect"),
        stroke = paint.isStroke(),
        antiAlias = paint.antiAlias,
        blend = paint.blendMode.toGpuBlendFacts(),
        maskFilter = paint.maskFilter.toNormalizedMaskFilter(),
    )
}

internal fun BlendMode.toGpuBlendFacts(): GPUBlendFacts {
    val mode = when (this) {
        BlendMode.CLEAR -> GPUBlendMode.CLEAR
        BlendMode.SRC_OVER -> GPUBlendMode.SRC_OVER
        BlendMode.SRC -> GPUBlendMode.SRC
        BlendMode.DST -> GPUBlendMode.DST
        BlendMode.DST_OVER -> GPUBlendMode.DST_OVER
        BlendMode.SRC_IN -> GPUBlendMode.SRC_IN
        BlendMode.DST_IN -> GPUBlendMode.DST_IN
        BlendMode.SRC_OUT -> GPUBlendMode.SRC_OUT
        BlendMode.DST_OUT -> GPUBlendMode.DST_OUT
        BlendMode.SRC_ATOP -> GPUBlendMode.SRC_ATOP
        BlendMode.DST_ATOP -> GPUBlendMode.DST_ATOP
        BlendMode.XOR -> GPUBlendMode.XOR
        BlendMode.PLUS -> GPUBlendMode.PLUS
        BlendMode.MODULATE -> GPUBlendMode.MODULATE
        BlendMode.MULTIPLY -> GPUBlendMode.MULTIPLY
        BlendMode.SCREEN -> GPUBlendMode.SCREEN
        BlendMode.OVERLAY -> GPUBlendMode.OVERLAY
        BlendMode.DARKEN -> GPUBlendMode.DARKEN
        BlendMode.LIGHTEN -> GPUBlendMode.LIGHTEN
        BlendMode.COLOR_DODGE -> GPUBlendMode.COLOR_DODGE
        BlendMode.COLOR_BURN -> GPUBlendMode.COLOR_BURN
        BlendMode.HARD_LIGHT -> GPUBlendMode.HARD_LIGHT
        BlendMode.SOFT_LIGHT -> GPUBlendMode.SOFT_LIGHT
        BlendMode.DIFFERENCE -> GPUBlendMode.DIFFERENCE
        BlendMode.EXCLUSION -> GPUBlendMode.EXCLUSION
        BlendMode.HUE -> GPUBlendMode.HUE
        BlendMode.SATURATION -> GPUBlendMode.SATURATION
        BlendMode.COLOR -> GPUBlendMode.COLOR
        BlendMode.LUMINOSITY -> GPUBlendMode.LUMINOSITY
    }
    return GPUBlendFacts(
        mode = mode,
        sourceAlpha = GPUSourceAlphaClassification.Translucent,
    )
}

internal fun GPUBlendFacts.canonicalBlendPlan(
    coverage: GPUCoverageConsumption = GPUCoverageConsumption.FullOrScissor,
    targetFormatClass: String = "rgba8unorm",
    samplePlan: GPUSamplePlan = GPUSamplePlan.SingleSampleFrame,
): GPUBlendPlan = mode.canonicalBlendPlan(coverage, sourceAlpha, targetFormatClass, samplePlan)

internal fun GPUBlendMode.canonicalBlendPlan(
    coverage: GPUCoverageConsumption = GPUCoverageConsumption.FullOrScissor,
    sourceAlpha: GPUSourceAlphaClassification = GPUSourceAlphaClassification.Translucent,
    targetFormatClass: String = "rgba8unorm",
    samplePlan: GPUSamplePlan = GPUSamplePlan.SingleSampleFrame,
): GPUBlendPlan = GPUBlendPlanner().plan(
    GPUBlendSpecializationRequest(
        mode = this,
        coverage = coverage,
        sourceAlpha = sourceAlpha,
        target = GPUTargetBlendFacts(
            formatClass = targetFormatClass,
            clampsNormalizedColorWrites = "unorm" in targetFormatClass,
            premultipliedAlpha = true,
        ),
        samplePlan = samplePlan,
    ),
)

internal fun GPUBlendFacts.needsDestinationTexture(): Boolean =
    canonicalBlendPlan().destinationReadRequirement ==
        GPUBlendDestinationReadRequirement.DestinationTextureRequired

internal fun GPUBlendFacts.canonicalFixedFunctionState(
    coverage: GPUCoverageConsumption = GPUCoverageConsumption.FullOrScissor,
): GPUFixedFunctionBlendState? =
    (canonicalBlendPlan(coverage = coverage) as? GPUBlendPlan.FixedFunctionBlend)?.state

internal fun GPUBlendMode.canonicalFixedFunctionState(
    coverage: GPUCoverageConsumption = GPUCoverageConsumption.FullOrScissor,
): GPUFixedFunctionBlendState? =
    (canonicalBlendPlan(coverage = coverage) as? GPUBlendPlan.FixedFunctionBlend)?.state

internal fun Matrix3x3F32.toGPUTransformFacts(): GPUTransformFacts {
    if (hasPerspective()) return GPUTransformFacts.perspective()
    if (this == Matrix3x3F32.Identity) return GPUTransformFacts.identity()
    return GPUTransformFacts.affine(
        scaleX = this.sx,
        skewX = this.kx,
        skewY = this.ky,
        scaleY = this.sy,
        translateX = this.tx,
        translateY = this.ty,
    )
}

internal fun MaskFilter?.toNormalizedMaskFilter(): NormalizedMaskFilter? = when (this) {
    is MaskFilter.Blur -> NormalizedMaskFilter.Blur(
        style = style.toNormalizedBlurStyle(),
        sigma = sigma,
    )
    is MaskFilter.Shader -> null
    is MaskFilter.Table -> null
    null -> null
}

internal fun BlurStyle.toNormalizedBlurStyle(): NormalizedBlurStyle = when (this) {
    BlurStyle.NORMAL -> NormalizedBlurStyle.NORMAL
    BlurStyle.SOLID -> NormalizedBlurStyle.SOLID
    BlurStyle.OUTER -> NormalizedBlurStyle.OUTER
    BlurStyle.INNER -> NormalizedBlurStyle.INNER
}

internal fun computeBounds(flatVertices: List<Float>): GPUBounds {
    if (flatVertices.isEmpty()) return GPUBounds(0f, 0f, 0f, 0f)
    var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
    var maxX = Float.MIN_VALUE; var maxY = Float.MIN_VALUE
    for (i in flatVertices.indices step 2) {
        val x = flatVertices[i]; val y = flatVertices[i + 1]
        if (x < minX) minX = x; if (y < minY) minY = y
        if (x > maxX) maxX = x; if (y > maxY) maxY = y
    }
    return GPUBounds(minX, minY, maxX, maxY)
}

// ────────────────────────────────────────────────────────────────────────────
// DrawColor / Clear — full‑surface rect fills
// ────────────────────────────────────────────────────────────────────────────

internal fun DisplayOp.DrawColor.toNormalizedCommand(
    cmdId: GPUDrawCommandID,
    target: GPUTargetFacts,
): NormalizedDrawCommand.FillRect {
    val w = target.width.toFloat()
    val h = target.height.toFloat()
    val gpRect = GPURect(0f, 0f, w, h)
    val bounds = GPUBounds(0f, 0f, w, h)
    val clip = this.clip.toGPUClipFacts(target)
    val transform = GPUTransformFacts.identity()
    return NormalizedDrawCommand.FillRect(
        commandId = cmdId,
        rect = gpRect,
        transform = transform,
        clip = clip,
        layer = GPULayerFacts.root(target),
        material = GPUMaterialDescriptor.SolidColor(
            r = this.color.r, g = this.color.g, b = this.color.b, a = this.color.a,
        ),
        bounds = bounds,
        ordering = GPUOrderingFacts(paintOrder = 0, dependsOnDestination = false, requiresBarrier = false),
        source = GPUCommandSource(adapter = "kanvas-surface", operation = "drawColor"),
        stroke = false,
        antiAlias = false,
        blend = this.mode.toGpuBlendFacts(),
    )
}

internal fun DisplayOp.Clear.toNormalizedCommand(
    cmdId: GPUDrawCommandID,
    target: GPUTargetFacts,
): NormalizedDrawCommand.FillRect {
    val w = target.width.toFloat()
    val h = target.height.toFloat()
    return NormalizedDrawCommand.FillRect(
        commandId = cmdId,
        rect = GPURect(0f, 0f, w, h),
        transform = GPUTransformFacts.identity(),
        clip = GPUClipFacts.wideOpen(bounds = GPUBounds(0f, 0f, w, h)),
        layer = GPULayerFacts.root(target),
        material = GPUMaterialDescriptor.SolidColor(
            r = this.color.r, g = this.color.g, b = this.color.b, a = this.color.a,
        ),
        bounds = GPUBounds(0f, 0f, w, h),
        ordering = GPUOrderingFacts(paintOrder = 0, dependsOnDestination = false, requiresBarrier = false),
        source = GPUCommandSource(adapter = "kanvas-surface", operation = "clear"),
        stroke = false,
        antiAlias = false,
        blend = BlendMode.SRC.toGpuBlendFacts(),
    )
}

// ────────────────────────────────────────────────────────────────────────────
// DrawPoint — single pixel as 1×1 rect fill
// ────────────────────────────────────────────────────────────────────────────

internal fun DisplayOp.DrawPoint.toNormalizedCommand(
    cmdId: GPUDrawCommandID,
    target: GPUTargetFacts,
): NormalizedDrawCommand.FillRect {
    val paint = this.paint
    val gpRect = GPURect(this.x, this.y, this.x + 1f, this.y + 1f)
    val bounds = GPUBounds(this.x, this.y, this.x + 1f, this.y + 1f)
    val clip = this.clip.toGPUClipFacts(target)
    val transform = this.transform.toGPUTransformFacts()
    return NormalizedDrawCommand.FillRect(
        commandId = cmdId,
        rect = gpRect,
        transform = transform,
        clip = clip,
        layer = GPULayerFacts.root(target),
        material = paint.toMaterial(),
        bounds = bounds,
        ordering = GPUOrderingFacts(paintOrder = 0, dependsOnDestination = false, requiresBarrier = false),
        source = GPUCommandSource(adapter = "kanvas-surface", operation = "drawPoint"),
        stroke = false,
        antiAlias = paint.antiAlias,
        blend = paint.blendMode.toGpuBlendFacts(),
    )
}

// ────────────────────────────────────────────────────────────────────────────
// DrawPoints — build a Path from the point list and the point mode.
// POINTS  → tiny rects for each point
// LINES   → moveTo/lineTo pairs
// POLYGON → closed polygon
// ────────────────────────────────────────────────────────────────────────────

internal fun DisplayOp.DrawPoints.toPath(): Path = when (this.mode) {
    PointMode.POINTS -> Path().also { path ->
        val halfWidth = paint.strokeWidth * 0.5f
        for (pt in this.points) {
            path.addRect(RectF32.ofLTRB(
                pt.x - halfWidth,
                pt.y - halfWidth,
                pt.x + halfWidth,
                pt.y + halfWidth,
            ))
        }
    }
    PointMode.LINES -> Path().also { path ->
        var i = 0
        while (i + 1 < this.points.size) {
            path.moveTo(this.points[i].x, this.points[i].y)
            path.lineTo(this.points[i + 1].x, this.points[i + 1].y)
            i += 2
        }
    }
    PointMode.POLYGON -> Path().also { path ->
        if (this.points.isEmpty()) return@also
        path.moveTo(this.points[0].x, this.points[0].y)
        for (i in 1 until this.points.size) {
            path.lineTo(this.points[i].x, this.points[i].y)
        }
        path.close()
    }
}

private val org.graphiks.kanvas.geometry.FillType.isInverse: Boolean
    get() = this == org.graphiks.kanvas.geometry.FillType.INVERSE_WINDING ||
        this == org.graphiks.kanvas.geometry.FillType.INVERSE_EVEN_ODD

// ────────────────────────────────────────────────────────────────────────────
// DrawDRRect — outer RRect contour (CW) + inner RRect contour (CCW) for hole
// ────────────────────────────────────────────────────────────────────────────

internal fun DisplayOp.DrawDRRect.toPath(): Path {
    val path = Path()
    path.addRRect(this.outer)
    // Inner contour: reverse the inner RRect path to produce CCW winding,
    // which punches a hole under non-zero winding fill.
    val innerPath = Path().addRRect(this.inner)
    path.reverseAddPath(innerPath)
    return path
}

// ────────────────────────────────────────────────────────────────────────────
// DrawImage → NormalizedDrawCommand.DrawImageRect
// ────────────────────────────────────────────────────────────────────────────

internal fun DisplayOp.DrawImage.toImageRectCommand(
    cmdId: GPUDrawCommandID,
    target: GPUTargetFacts,
    sampling: org.graphiks.kanvas.paint.SamplingOptions? = null,
): NormalizedDrawCommand.DrawImageRect {
    val image = this.image
    val requestedSampling = sampling ?: this.paint?.let { p ->
        val sh = p.shader
        (sh as? org.graphiks.kanvas.paint.Shader.Image)?.sampling
    }
    val samplingFilterMode = when (requestedSampling) {
        org.graphiks.kanvas.paint.SamplingOptions.NEAREST -> "nearest"
        org.graphiks.kanvas.paint.SamplingOptions.LINEAR,
        is org.graphiks.kanvas.paint.SamplingOptions.Cubic,
        null,
        -> "linear"
    }
    val material = GPUMaterialDescriptor.ImageDraw(
        imageSourceId = image.sourceId,
        imageWidth = image.width,
        imageHeight = image.height,
        samplingFilterMode = samplingFilterMode,
        alphaOnly = image.colorType == org.graphiks.kanvas.image.ColorType.ALPHA_8,
        tintR = if (image.colorType == org.graphiks.kanvas.image.ColorType.ALPHA_8) this.paint?.color?.r ?: 0f else 1f,
        tintG = if (image.colorType == org.graphiks.kanvas.image.ColorType.ALPHA_8) this.paint?.color?.g ?: 0f else 1f,
        tintB = if (image.colorType == org.graphiks.kanvas.image.ColorType.ALPHA_8) this.paint?.color?.b ?: 0f else 1f,
        tintA = this.paint?.color?.a ?: 1f,
    )
    val src = GPURect(this.src.left, this.src.top, this.src.right, this.src.bottom)
    val dst = GPURect(this.dst.left, this.dst.top, this.dst.right, this.dst.bottom)
    val bounds = GPUBounds(dst.left, dst.top, dst.right, dst.bottom)
    val clip = this.clip.toGPUClipFacts(target)
    val transform = this.transform.toGPUTransformFacts()
    return NormalizedDrawCommand.DrawImageRect(
        commandId = cmdId,
        imageSourceId = image.sourceId,
        src = src,
        dst = dst,
        imageFilterPlan = toImageFilterPlan(transform, clip, target, dst),
        transform = transform,
        clip = clip,
        layer = GPULayerFacts.root(target),
        material = material,
        bounds = bounds,
        ordering = GPUOrderingFacts(paintOrder = 0, dependsOnDestination = false, requiresBarrier = false),
        source = GPUCommandSource(adapter = "kanvas-surface", operation = "drawImage"),
        blend = (this.paint?.blendMode ?: BlendMode.SRC_OVER).toGpuBlendFacts(),
        samplingFilterMode = material.samplingFilterMode,
        pixelsWidth = image.width,
        pixelsHeight = image.height,
        pixelsFormat = "RGBA8Unorm",
        pixelsAlphaType = "Premul",
    )
}

private fun DisplayOp.DrawImage.toImageFilterPlan(
    transform: GPUTransformFacts,
    clip: GPUClipFacts,
    target: GPUTargetFacts,
    dst: GPURect,
): GPUImageFilterPlan {
    val paint = paint ?: return GPUImageFilterPlan.None
    if (paint.maskFilter != null) return GPUImageFilterPlan.Refused("unsupported.mask-filter.image")
    val imageFilter = paint.imageFilter ?: return GPUImageFilterPlan.None

    val blur = imageFilter as? ImageFilter.Blur
        ?: return GPUImageFilterPlan.Refused("unsupported.image-filter.image.kind")
    if (blur.input != null) return GPUImageFilterPlan.Refused("unsupported.image-filter.blur.input")
    if (blur.tileMode != TileMode.CLAMP) return GPUImageFilterPlan.Refused("unsupported.image-filter.blur.tile-mode")
    if (
        !blur.sigmaX.isFinite() ||
        !blur.sigmaY.isFinite() ||
        blur.sigmaX < 0f ||
        blur.sigmaY < 0f ||
        blur.sigmaX > 12f ||
        blur.sigmaY > 12f
    ) {
        return GPUImageFilterPlan.Refused("unsupported.image-filter.blur.sigma")
    }
    if (blur.sigmaX == 0f && blur.sigmaY == 0f) return GPUImageFilterPlan.Identity

    val haloX = kotlin.math.ceil(3f * blur.sigmaX).toInt()
    val haloY = kotlin.math.ceil(3f * blur.sigmaY).toInt()
    val targetBounds = GPURect(0f, 0f, target.width.toFloat(), target.height.toFloat())
    val clipBounds = when (clip.kind) {
        GPUClipKind.WideOpen -> targetBounds
        GPUClipKind.DeviceRect -> intersect(clip.bounds.toRect(), targetBounds)
        // A complex clip is applied once at the shared source-to-scene composite.
        // The filter source must therefore retain its full target-space halo.
        GPUClipKind.ComplexStack -> targetBounds
    }
    val outputBounds = intersect(
        GPURect(
            left = dst.left - haloX,
            top = dst.top - haloY,
            right = dst.right + haloX,
            bottom = dst.bottom + haloY,
        ),
        clipBounds,
    )
    val outputWidth = outputBounds.right - outputBounds.left
    val outputHeight = outputBounds.bottom - outputBounds.top
    if (
        outputWidth <= 0f || outputHeight <= 0f ||
        outputWidth > 2048f || outputHeight > 2048f
    ) {
        return GPUImageFilterPlan.Refused("unsupported.image-filter.blur.intermediate-size")
    }
    if (transform.type != GPUTransformType.Identity) {
        return GPUImageFilterPlan.Refused("unsupported.image-filter.blur.transform")
    }
    return GPUImageFilterPlan.Blur(
        sigmaX = blur.sigmaX,
        sigmaY = blur.sigmaY,
        haloX = haloX,
        haloY = haloY,
        outputBounds = outputBounds,
    )
}

private fun GPUBounds.toRect(): GPURect = GPURect(left, top, right, bottom)

private fun intersect(first: GPURect, second: GPURect): GPURect = GPURect(
    left = maxOf(first.left, second.left),
    top = maxOf(first.top, second.top),
    right = minOf(first.right, second.right),
    bottom = minOf(first.bottom, second.bottom),
)

// ────────────────────────────────────────────────────────────────────────────
// DrawImageNine — decompose into 9 cells (src / dst pairs)
// ────────────────────────────────────────────────────────────────────────────

internal data class ImageCell(
    val src: RectF32,
    val dst: RectF32,
    val color: ColorARGB? = null,
    val sourceIndex: Int = 0,
)

internal fun DisplayOp.DrawImageNine.decompose(): List<ImageCell> {
    val iw = this.image.width.toFloat()
    val ih = this.image.height.toFloat()
    val c = this.center
    val d = this.dst
    val cells = mutableListOf<ImageCell>()

    // Column boundaries (source)
    val srcL = listOf(0f, c.left, c.right, iw)
    // Row boundaries (source)
    val srcT = listOf(0f, c.top, c.bottom, ih)
    // Column boundaries (destination)
    val dstL = listOf(
        d.left,
        d.left + c.left,
        d.right - (iw - c.right),
        d.right,
    )
    // Row boundaries (destination)
    val dstT = listOf(
        d.top,
        d.top + c.top,
        d.bottom - (ih - c.bottom),
        d.bottom,
    )

    for (row in 0 until 3) {
        for (col in 0 until 3) {
            val src = RectF32.ofLTRB(srcL[col], srcT[row], srcL[col + 1], srcT[row + 1])
            val dst = RectF32.ofLTRB(dstL[col], dstT[row], dstL[col + 1], dstT[row + 1])
            if (!src.isEmpty && !dst.isEmpty) {
                cells.add(ImageCell(src = src, dst = dst, sourceIndex = row * 3 + col))
            }
        }
    }
    return cells
}

// ────────────────────────────────────────────────────────────────────────────
// DrawImageLattice — decompose into (xDivs+1)×(yDivs+1) cells
// ────────────────────────────────────────────────────────────────────────────

internal fun DisplayOp.DrawImageLattice.decompose(): List<ImageCell> {
    val iw = this.image.width.toFloat()
    val ih = this.image.height.toFloat()
    val lat = this.lattice
    val d = this.dst

    // Column boundaries from xDivs
    val cols = mutableListOf(0f)
    for (xv in lat.xDivs) cols.add(xv.toFloat())
    cols.add(iw)
    // Row boundaries from yDivs
    val rows = mutableListOf(0f)
    for (yv in lat.yDivs) rows.add(yv.toFloat())
    rows.add(ih)

    val numCols = cols.size - 1
    val numRows = rows.size - 1
    val dstColumns = latticeDestinationBoundaries(cols, d.left, d.right)
    val dstRows = latticeDestinationBoundaries(rows, d.top, d.bottom)
    val cells = mutableListOf<ImageCell>()
    var cellIndex = 0

    for (r in 0 until numRows) {
        for (c in 0 until numCols) {
            val srcLeft = cols[c]
            val srcTop = rows[r]
            val srcRight = cols[c + 1]
            val srcBottom = rows[r + 1]

            val dstRect = if (lat.rects != null && cellIndex < lat.rects.size) {
                lat.rects[cellIndex]
            } else {
                RectF32.ofLTRB(
                    dstColumns[c],
                    dstRows[r],
                    dstColumns[c + 1],
                    dstRows[r + 1],
                )
            }

            val flag = lat.flags?.getOrNull(cellIndex) ?: org.graphiks.kanvas.types.LatticeFlags.DEFAULT
            if (flag == org.graphiks.kanvas.types.LatticeFlags.TRANSPARENT) {
                cellIndex++
                continue
            }
            val color = if (flag == org.graphiks.kanvas.types.LatticeFlags.FIXED_COLOR) {
                lat.colors?.getOrNull(cellIndex)
            } else {
                null
            }
            cells.add(ImageCell(
                src = RectF32.ofLTRB(srcLeft, srcTop, srcRight, srcBottom),
                dst = dstRect,
                color = color,
                sourceIndex = cellIndex,
            ))
            cellIndex++
        }
    }
    return cells
}

/**
 * Skia lattices alternate fixed and scalable bands: the outer band is fixed,
 * each odd band stretches, and fixed bands retain their source extent while
 * there is room.  This is the geometry used by a nine-patch lattice, not a
 * proportional resampling of every source cell.
 */
private fun latticeDestinationBoundaries(
    sourceBoundaries: List<Float>,
    destinationStart: Float,
    destinationEnd: Float,
): List<Float> {
    val segmentCount = sourceBoundaries.size - 1
    if (segmentCount <= 0) return listOf(destinationStart, destinationEnd)
    val segmentLengths = List(segmentCount) { sourceBoundaries[it + 1] - sourceBoundaries[it] }
    val fixedTotal = segmentLengths.filterIndexed { index, _ -> index % 2 == 0 }.sum()
    val scalableTotal = segmentLengths.filterIndexed { index, _ -> index % 2 != 0 }.sum()
    val destinationLength = destinationEnd - destinationStart
    val fixedScale = if (fixedTotal <= 0f) 0f else minOf(1f, destinationLength / fixedTotal)
    val scalableLength = (destinationLength - fixedTotal * fixedScale).coerceAtLeast(0f)
    val result = ArrayList<Float>(sourceBoundaries.size)
    result += destinationStart
    var current = destinationStart
    for (index in 0 until segmentCount) {
        val length = when {
            index % 2 == 0 -> segmentLengths[index] * fixedScale
            scalableTotal > 0f -> scalableLength * segmentLengths[index] / scalableTotal
            else -> 0f
        }
        current += length
        result += current
    }
    result[result.lastIndex] = destinationEnd
    return result
}

/** Applies the caller's alpha and blend mode without tinting a lattice fixed color. */
internal fun fixedLatticeColorPaint(color: ColorARGB, paint: Paint?): Paint {
    val base = paint ?: Paint()
    return base.copy(color = ColorARGB.fromRGBA(color.r, color.g, color.b, color.a * base.color.a))
}

// ────────────────────────────────────────────────────────────────────────────
// DisplayOp.withCombinedTransform — concatenate an outer transform into every
// drawing op that carries a transform field. Used for picture replay.
// ────────────────────────────────────────────────────────────────────────────

internal fun DisplayOp.withCombinedTransform(outer: Matrix3x3F32): DisplayOp = when (this) {
    is DisplayOp.DrawRect -> copy(transform = outer * transform)
    is DisplayOp.DrawRRect -> copy(transform = outer * transform)
    is DisplayOp.DrawPath -> copyPreservingSourceOperation(transform = outer * transform)
    is DisplayOp.DrawImage -> copy(transform = outer * transform)
    is DisplayOp.DrawText -> copy(transform = outer * transform)
    is DisplayOp.DrawColor -> copy(transform = outer * transform)
    is DisplayOp.DrawPoint -> copy(transform = outer * transform)
    is DisplayOp.DrawPoints -> copy(transform = outer * transform)
    is DisplayOp.DrawDRRect -> copy(transform = outer * transform)
    is DisplayOp.DrawImageNine -> copy(transform = outer * transform)
    is DisplayOp.DrawImageLattice -> copy(transform = outer * transform)
    is DisplayOp.DrawPicture -> copy(transform = outer * transform)
    is DisplayOp.DrawVertices -> copy(transform = outer * transform)
    is DisplayOp.DrawMesh -> copy(transform = outer * transform)
    is DisplayOp.DrawAtlas -> copy(transform = outer * transform)
    is DisplayOp.BeginLayer -> copy(transform = outer * transform)
    is DisplayOp.Clear,
    is DisplayOp.SetTransform,
    is DisplayOp.SetClip,
    DisplayOp.EndLayer,
    is DisplayOp.Annotation,
    is DisplayOp.FlushAndSnapshot -> this
}

/**
 * Replays an operation captured in a [Picture] under an outer picture transform.
 *
 * Display-list clips are already in the picture's device space at capture time, so they
 * must be transformed independently from the operation transform and then intersected
 * with the clip captured by each enclosing DrawPicture. This keeps a Picture child on the
 * same clip/S/G route it would have used if it had been recorded directly on the canvas.
 */
internal fun DisplayOp.withPictureReplayState(
    outerTransform: Matrix3x3F32,
    enclosingClip: ClipStack,
): DisplayOp {
    val replayClip = enclosingClip.intersectWith(clipForPictureReplay(this)?.transformForPictureReplay(outerTransform))
    return when (val transformed = withCombinedTransform(outerTransform)) {
        is DisplayOp.DrawRect -> transformed.copy(clip = replayClip)
        is DisplayOp.DrawRRect -> transformed.copy(clip = replayClip)
        is DisplayOp.DrawPath -> transformed.copyPreservingSourceOperation(clip = replayClip)
        is DisplayOp.DrawImage -> transformed.copy(clip = replayClip)
        is DisplayOp.DrawText -> transformed.copy(clip = replayClip)
        is DisplayOp.DrawColor -> transformed.copy(clip = replayClip)
        is DisplayOp.DrawPoint -> transformed.copy(clip = replayClip)
        is DisplayOp.DrawPoints -> transformed.copy(clip = replayClip)
        is DisplayOp.DrawDRRect -> transformed.copy(clip = replayClip)
        is DisplayOp.DrawImageNine -> transformed.copy(clip = replayClip)
        is DisplayOp.DrawImageLattice -> transformed.copy(clip = replayClip)
        is DisplayOp.DrawPicture -> transformed.copy(clip = replayClip)
        is DisplayOp.DrawVertices -> transformed.copy(clip = replayClip)
        is DisplayOp.DrawMesh -> transformed.copy(clip = replayClip)
        is DisplayOp.DrawAtlas -> transformed.copy(clip = replayClip)
        is DisplayOp.BeginLayer -> {
            val compositeClip = enclosingClip
                .intersectWith(transformed.rec.compositeClip?.transformForPictureReplay(outerTransform))
                .takeUnless { it == ClipStack.WideOpen }
            transformed.copy(rec = transformed.rec.copy(compositeClip = compositeClip))
        }
        else -> transformed
    }
}

private fun clipForPictureReplay(operation: DisplayOp): ClipStack? = when (operation) {
    is DisplayOp.DrawRect -> operation.clip
    is DisplayOp.DrawRRect -> operation.clip
    is DisplayOp.DrawPath -> operation.clip
    is DisplayOp.DrawImage -> operation.clip
    is DisplayOp.DrawText -> operation.clip
    is DisplayOp.DrawColor -> operation.clip
    is DisplayOp.DrawPoint -> operation.clip
    is DisplayOp.DrawPoints -> operation.clip
    is DisplayOp.DrawDRRect -> operation.clip
    is DisplayOp.DrawImageNine -> operation.clip
    is DisplayOp.DrawImageLattice -> operation.clip
    is DisplayOp.DrawPicture -> operation.clip
    is DisplayOp.DrawVertices -> operation.clip
    is DisplayOp.DrawMesh -> operation.clip
    is DisplayOp.DrawAtlas -> operation.clip
    else -> null
}

private fun ClipStack?.transformForPictureReplay(matrix: Matrix3x3F32): ClipStack? = this?.let { clip ->
    when (clip) {
        ClipStack.WideOpen -> ClipStack.WideOpen
        is ClipStack.DeviceRect -> clip.rectForPictureReplay(matrix, clip.antiAlias)
        is ClipStack.Complex -> clip.collapsedIntersectingRectOrNull()?.let {
            it.rectForPictureReplay(matrix, it.antiAlias)
        } ?: ClipStack.Complex(clip.ops.map { it.transformForPictureReplay(matrix) })
    }
}

/** The recorder's rectangular intersects remain a device rect only when their AA rules match. */
private fun ClipStack.Complex.collapsedIntersectingRectOrNull(): ClipStack.DeviceRect? {
    val rectOps = ops.map { it as? ClipStackOp.RectOp ?: return null }
    if (rectOps.any { it.op != org.graphiks.kanvas.pipeline.ClipOp.INTERSECT }) return null
    val antiAlias = rectOps.firstOrNull()?.antiAlias ?: return null
    if (rectOps.any { it.antiAlias != antiAlias }) return null
    val intersection = rectOps.fold<ClipStackOp.RectOp, RectF32?>(null) { current, op ->
        val rect = op.rect
        current?.let {
            RectF32.ofLTRB(
                maxOf(it.left, rect.left),
                maxOf(it.top, rect.top),
                minOf(it.right, rect.right),
                minOf(it.bottom, rect.bottom),
            )
        } ?: rect
    } ?: return null
    return ClipStack.DeviceRect(intersection, antiAlias)
}

private fun ClipStack.DeviceRect.rectForPictureReplay(matrix: Matrix3x3F32, antiAlias: Boolean): ClipStack = when {
    matrix.isScaleTranslate() -> ClipStack.DeviceRect(matrix.mapAxisAlignedRect(rect), antiAlias)
    !matrix.hasPerspective() -> ClipStack.Complex(
        listOf(ClipStackOp.PathOp(Path().addRect(rect).transform(matrix), org.graphiks.kanvas.pipeline.ClipOp.INTERSECT, antiAlias)),
    )
    else -> ClipStack.Complex(
        listOf(ClipStackOp.PathOp(Path().addRect(rect), org.graphiks.kanvas.pipeline.ClipOp.INTERSECT, antiAlias, perspectiveCaptureRefusal = true)),
    )
}

private fun ClipStackOp.transformForPictureReplay(matrix: Matrix3x3F32): ClipStackOp = when (this) {
    is ClipStackOp.RectOp -> when {
        matrix.isScaleTranslate() -> copy(rect = matrix.mapAxisAlignedRect(rect))
        !matrix.hasPerspective() -> ClipStackOp.PathOp(Path().addRect(rect).transform(matrix), op, antiAlias, perspectiveCaptureRefusal)
        else -> ClipStackOp.PathOp(Path().addRect(rect), op, antiAlias, perspectiveCaptureRefusal = true)
    }
    is ClipStackOp.RRectOp -> when {
        matrix.isScaleTranslate() -> copy(rrect = rrect.mapAxisAligned(matrix))
        !matrix.hasPerspective() -> ClipStackOp.PathOp(Path().addRRect(rrect).transform(matrix), op, antiAlias, perspectiveCaptureRefusal)
        else -> ClipStackOp.PathOp(Path().addRRect(rrect), op, antiAlias, perspectiveCaptureRefusal = true)
    }
    is ClipStackOp.PathOp -> copy(
        path = if (!matrix.hasPerspective()) path.transform(matrix) else path,
        perspectiveCaptureRefusal = perspectiveCaptureRefusal || matrix.hasPerspective(),
    )
}
