package org.graphiks.kanvas.surface.gpu

import java.util.Collections
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedVerticesUploadArtifact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedMaterialFrameIdentityAuthority
import org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedMaterialFrameSnapshot
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgram
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.recording.canonicalSnapshotHash
import org.graphiks.kanvas.gpu.renderer.state.GPUFrameProvenance
import org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes

private const val WEBGPU_BUFFER_UPLOAD_ALIGNMENT = 4L

internal data class PreparedVerticesFrameInventoryLimits(
    val maxDraws: Int,
    val maxUniqueArtifacts: Int,
    val maxVertexBytes: Long,
    val maxIndexBytes: Long,
    val maxTotalUploadBytes: Long,
    val maxRuntimeUniformBytes: Long,
    val maxRuntimeChildren: Int,
) {
    init {
        require(maxDraws >= 0) { "maxDraws must be non-negative" }
        require(maxUniqueArtifacts >= 0) { "maxUniqueArtifacts must be non-negative" }
        require(maxVertexBytes >= 0L) { "maxVertexBytes must be non-negative" }
        require(maxIndexBytes >= 0L) { "maxIndexBytes must be non-negative" }
        require(maxTotalUploadBytes >= 0L) { "maxTotalUploadBytes must be non-negative" }
        require(maxRuntimeUniformBytes >= 0L) { "maxRuntimeUniformBytes must be non-negative" }
        require(maxRuntimeChildren >= 0) { "maxRuntimeChildren must be non-negative" }
    }
}

internal data class PreparedVerticesFrameLimitEvidence(
    val configured: PreparedVerticesFrameInventoryLimits,
    val effective: PreparedVerticesFrameInventoryLimits,
    val capabilitySource: String,
) {
    init {
        require(capabilitySource.isNotBlank()) { "capabilitySource must not be blank" }
    }
}

internal enum class PreparedVerticesUploadBufferKind { Vertex, Index }

internal data class PreparedVerticesUploadRange(
    val artifactKey: String,
    val bufferKind: PreparedVerticesUploadBufferKind,
    val offset: Long,
    val byteCount: Long,
    val occupiedByteCount: Long,
    val alignment: Long = WEBGPU_BUFFER_UPLOAD_ALIGNMENT,
) {
    init {
        require(artifactKey.isNotBlank())
        require(alignment > 0L && alignment and (alignment - 1L) == 0L)
        require(offset >= 0L && offset % alignment == 0L)
        require(byteCount > 0L)
        require(occupiedByteCount >= byteCount && occupiedByteCount % alignment == 0L)
    }

    val endExclusive: Long
        get() = Math.addExact(offset, occupiedByteCount)
}

internal class PreparedVerticesFrameCommand internal constructor(
    val operationIndex: Int,
    val artifactKey: String,
    val artifact: GPUPreparedVerticesUploadArtifact,
    val materialKey: String,
    val materialFrameSnapshot: GPUPreparedMaterialFrameSnapshot,
    val draw: GPUPreparedVerticesDraw,
) {
    init {
        require(operationIndex >= 0)
        require(artifactKey.isNotBlank())
        require(materialKey.isNotBlank())
        require(GPUPreparedMaterialFrameIdentityAuthority.authenticates(materialFrameSnapshot)) {
            "Prepared vertices command material snapshot must be authenticated"
        }
        require(materialKey == materialFrameSnapshot.identity.bucketKey) {
            "Prepared vertices command material key must match its authenticated snapshot"
        }
    }

    val material: GPUPreparedMaterialProgram
        get() = materialFrameSnapshot.program
}

internal class PreparedVerticesMappedCommand internal constructor(
    val commandId: Int,
    val operationIndex: Int,
    val artifactKey: String,
    val frameProvenance: GPUFrameProvenance = GPUFrameProvenance.None,
) {
    init {
        require(commandId >= 0 && operationIndex >= 0 && artifactKey.isNotBlank())
    }
}

internal data class PreparedVerticesFrameMetrics(
    val drawCount: Int,
    val uniqueArtifactCount: Int,
    val vertexBytes: Long,
    val indexBytes: Long,
    val totalUploadBytes: Long,
    val runtimeUniformBytes: Long,
    val runtimeChildren: Int,
)

internal class PreparedVerticesFrameInventory internal constructor(
    commands: List<PreparedVerticesFrameCommand>,
    artifactsByKey: Map<String, GPUPreparedVerticesUploadArtifact>,
    materialsByKey: Map<String, GPUPreparedMaterialProgram>,
    artifactKeyByOperationIndex: Map<Int, String>,
    vertexUploadRanges: List<PreparedVerticesUploadRange>,
    indexUploadRanges: List<PreparedVerticesUploadRange>,
    elidedVerticesOperationIndices: List<Int>,
    mappedCommands: List<PreparedVerticesMappedCommand> = emptyList(),
    val capabilitySnapshotHash: String,
    val metrics: PreparedVerticesFrameMetrics,
    val limitEvidence: PreparedVerticesFrameLimitEvidence,
) {
    val commands: List<PreparedVerticesFrameCommand> =
        Collections.unmodifiableList(commands.toList())
    val commandsByOperationIndex: Map<Int, PreparedVerticesFrameCommand> =
        Collections.unmodifiableMap(linkedMapOf<Int, PreparedVerticesFrameCommand>().apply {
            commands.forEach { command ->
                require(put(command.operationIndex, command) == null) {
                    "Prepared vertices commands must have unique operation indices"
                }
            }
        })
    val artifactsByKey: Map<String, GPUPreparedVerticesUploadArtifact> =
        Collections.unmodifiableMap(LinkedHashMap(artifactsByKey))
    val materialsByKey: Map<String, GPUPreparedMaterialProgram> =
        Collections.unmodifiableMap(LinkedHashMap(materialsByKey))
    val artifactKeyByOperationIndex: Map<Int, String> =
        Collections.unmodifiableMap(LinkedHashMap(artifactKeyByOperationIndex))
    val elidedVerticesOperationOrder: List<Int> =
        Collections.unmodifiableList(elidedVerticesOperationIndices.toList())
    val elidedVerticesOperationIndices: Set<Int> =
        Collections.unmodifiableSet(LinkedHashSet(elidedVerticesOperationIndices))
    val mappedCommands: List<PreparedVerticesMappedCommand> =
        Collections.unmodifiableList(mappedCommands.toList())
    val artifactKeyByCommandId: Map<Int, String> = Collections.unmodifiableMap(
        linkedMapOf<Int, String>().apply {
            mappedCommands.forEach { command -> put(command.commandId, command.artifactKey) }
        },
    )
    val vertexUploadRanges: List<PreparedVerticesUploadRange> =
        Collections.unmodifiableList(vertexUploadRanges.toList())
    val indexUploadRanges: List<PreparedVerticesUploadRange> =
        Collections.unmodifiableList(indexUploadRanges.toList())

    init {
        require(capabilitySnapshotHash.isNotBlank()) {
            "Prepared vertices capability snapshot hash must not be blank"
        }
        require(commandsByOperationIndex.keys == this.artifactKeyByOperationIndex.keys) {
            "Prepared vertices command and artifact ownership must match"
        }
        require(elidedVerticesOperationIndices.size == elidedVerticesOperationOrder.size) {
            "Prepared vertices elided operation indices must be unique"
        }
        require(commandsByOperationIndex.keys.none(elidedVerticesOperationIndices::contains)) {
            "Prepared vertices accepted and elided ownership must be disjoint"
        }
        require(materialsByKey.keys == commands.mapTo(linkedSetOf()) { it.materialKey }) {
            "Prepared vertices material ownership must exactly match command material keys"
        }
        commands.forEach { command ->
            val published = materialsByKey.getValue(command.materialKey)
            require(
                GPUPreparedMaterialFrameIdentityAuthority.authenticates(
                    command.materialFrameSnapshot,
                ) && GPUPreparedMaterialFrameIdentityAuthority.exactlyMatches(
                    published,
                    command.materialFrameSnapshot.program,
                ),
            ) {
                "Prepared vertices published material must match its authenticated command snapshot"
            }
        }
    }

    internal fun bindCommandIds(
        commandIdByOperationIndex: Map<Int, Int>,
        frameProvenanceByCommandId: Map<Int, GPUFrameProvenance>,
    ): PreparedVerticesCommandBindingResult {
        commands.firstOrNull { command ->
            command.operationIndex !in commandIdByOperationIndex
        }?.let { command ->
            return bindingRefusal(command.operationIndex, "missing_operation_binding")
        }
        commandIdByOperationIndex.keys.firstOrNull { operationIndex ->
            operationIndex !in commandsByOperationIndex
        }?.let { operationIndex ->
            return bindingRefusal(operationIndex, "unexpected_operation_binding")
        }
        val operationIndexByCommandId = linkedMapOf<Int, Int>()
        commands.forEach { command ->
            val commandId = commandIdByOperationIndex.getValue(command.operationIndex)
            if (commandId < 0) {
                return bindingRefusal(command.operationIndex, "negative_command_id")
            }
            if (operationIndexByCommandId.put(commandId, command.operationIndex) != null) {
                return bindingRefusal(
                    command.operationIndex,
                    "duplicate_command_id",
                    mapOf("commandId" to commandId.toString()),
                )
            }
        }
        operationIndexByCommandId.keys.firstOrNull { commandId ->
            commandId !in frameProvenanceByCommandId
        }?.let { commandId ->
            return bindingRefusal(
                operationIndexByCommandId.getValue(commandId),
                "missing_command_provenance",
                mapOf("commandId" to commandId.toString()),
            )
        }
        frameProvenanceByCommandId.keys.firstOrNull { commandId ->
            commandId !in operationIndexByCommandId
        }?.let { commandId ->
            return bindingRefusal(
                operationIndex = commands.firstOrNull()?.operationIndex ?: -1,
                reason = "unexpected_command_provenance",
                extraFacts = mapOf("commandId" to commandId.toString()),
            )
        }
        val bindings = ArrayList<PreparedVerticesMappedCommand>(commands.size)
        commands.forEach { command ->
            val commandId = commandIdByOperationIndex.getValue(command.operationIndex)
            bindings += PreparedVerticesMappedCommand(
                commandId = commandId,
                operationIndex = command.operationIndex,
                artifactKey = command.artifactKey,
                frameProvenance = frameProvenanceByCommandId.getValue(commandId),
            )
        }
        return PreparedVerticesCommandBindingResult.Ready(
            PreparedVerticesFrameInventory(
                commands, artifactsByKey, materialsByKey, artifactKeyByOperationIndex,
                vertexUploadRanges, indexUploadRanges, elidedVerticesOperationOrder,
                bindings, capabilitySnapshotHash, metrics, limitEvidence,
            ),
        )
    }

    private fun bindingRefusal(
        operationIndex: Int,
        reason: String,
        extraFacts: Map<String, String> = emptyMap(),
    ) = PreparedVerticesCommandBindingResult.Refused(
        operationIndex = operationIndex,
        code = "invalid.surface.prepared.vertices-command-binding",
        facts = linkedMapOf(
            "authority" to "PreparedVerticesFrameInventory",
            "reason" to reason,
            "operationIndex" to operationIndex.toString(),
        ).apply { putAll(extraFacts) },
    )
}

internal sealed interface PreparedVerticesCommandBindingResult {
    data class Ready(val inventory: PreparedVerticesFrameInventory) :
        PreparedVerticesCommandBindingResult

    class Refused internal constructor(
        val operationIndex: Int,
        val code: String,
        facts: Map<String, String>,
    ) : PreparedVerticesCommandBindingResult {
        val facts: Map<String, String> =
            Collections.unmodifiableMap(LinkedHashMap(facts))
    }
}

internal sealed interface PreparedVerticesFrameInventoryResult {
    data class Ready(val inventory: PreparedVerticesFrameInventory) :
        PreparedVerticesFrameInventoryResult

    class Refused internal constructor(
        val code: String,
        val operationIndex: Int?,
        facts: Map<String, String>,
    ) : PreparedVerticesFrameInventoryResult {
        val facts: Map<String, String> = Collections.unmodifiableMap(LinkedHashMap(facts))
    }
}

internal object PreparedVerticesFrameInventoryBuilder {
    fun build(
        draws: List<GPUPreparedVerticesDraw>,
        limits: PreparedVerticesFrameInventoryLimits,
        capabilities: GPUCapabilities,
    ): PreparedVerticesFrameInventoryResult = build(
        draws = draws,
        limits = limits,
        capabilities = capabilities,
        artifactKeySelector = GPUPreparedVerticesUploadArtifact::key,
        materialBudgetSelector = GPUPreparedMaterialProgram::frameMaterialBudget,
    )

    @JvmSynthetic
    internal fun build(
        draws: List<GPUPreparedVerticesDraw>,
        limits: PreparedVerticesFrameInventoryLimits,
        capabilities: GPUCapabilities,
        artifactKeySelector: (GPUPreparedVerticesUploadArtifact) -> String,
        materialBucketKeySelector: (GPUPreparedMaterialFrameSnapshot) -> String =
            { snapshot -> snapshot.identity.bucketKey },
        materialBudgetSelector: (GPUPreparedMaterialProgram) -> Pair<Long, Int> =
            GPUPreparedMaterialProgram::frameMaterialBudget,
    ): PreparedVerticesFrameInventoryResult {
        val limitEvidence = effectiveLimits(limits, capabilities)
        val effective = limitEvidence.effective
        val seenOperationIndices = linkedSetOf<Int>()
        draws.forEach { draw ->
            if (!seenOperationIndices.add(draw.operationIndex)) {
                return refused(
                    draw.operationIndex,
                    "duplicate_operation_index",
                    mapOf("operationIndex" to draw.operationIndex.toString()),
                    budgetCodeFor(draw.operationKind),
                )
            }
        }
        val elided = draws.filter { draw ->
            draw.culledByClip || draw.blendPlan is GPUBlendPlan.NoOp
        }
        val visibleDraws = draws.filterNot { draw ->
            draw.culledByClip || draw.blendPlan is GPUBlendPlan.NoOp
        }
        if (visibleDraws.size > effective.maxDraws) {
            val offending = visibleDraws[effective.maxDraws]
            return budgetRefusal(
                offending.operationIndex, "maxDraws",
                Math.addExact(effective.maxDraws.toLong(), 1L),
                effective.maxDraws.toLong(), budgetCodeFor(offending.operationKind),
            )
        }

        val artifacts = linkedMapOf<String, GPUPreparedVerticesUploadArtifact>()
        val materials = linkedMapOf<String, GPUPreparedMaterialProgram>()
        val materialSnapshotsByBucketKey = linkedMapOf<String, GPUPreparedMaterialFrameSnapshot>()
        val commands = ArrayList<PreparedVerticesFrameCommand>(visibleDraws.size)
        val vertexRanges = ArrayList<PreparedVerticesUploadRange>()
        val indexRanges = ArrayList<PreparedVerticesUploadRange>()
        val artifactKeyByOperation = linkedMapOf<Int, String>()
        var vertexBytes = 0L
        var indexBytes = 0L
        var runtimeUniformBytes = 0L
        var runtimeChildren = 0L
        var vertexOffset = 0L
        var indexOffset = 0L

        for (draw in visibleDraws) {
            val operationIndex = draw.operationIndex
            val budgetCode = budgetCodeFor(draw.operationKind)
            val artifact = draw.artifact
            val artifactKey = artifactKeySelector(artifact)
            if (artifactKey.isBlank()) {
                return refused(operationIndex, "blank_artifact_key", code = budgetCode)
            }
            val existingArtifact = artifacts[artifactKey]
            if (existingArtifact != null && !existingArtifact.exactIdentityEquals(artifact)) {
                return refused(
                    operationIndex,
                    "artifact_identity_collision",
                    mapOf(
                        "artifactKey" to artifactKey,
                        "authority" to "PreparedVerticesFrameInventory",
                    ),
                    budgetCode,
                )
            }
            if (existingArtifact == null) {
                if (artifacts.size >= effective.maxUniqueArtifacts) {
                    return budgetRefusal(
                        operationIndex, "maxUniqueArtifacts",
                        Math.addExact(artifacts.size.toLong(), 1L),
                        effective.maxUniqueArtifacts.toLong(),
                        budgetCode,
                    )
                }
                vertexBytes = checkedAddOrRefuse(vertexBytes, artifact.vertexByteCount(), operationIndex)
                    ?: return overflowRefusal(operationIndex, "vertexBytes", budgetCode)
                indexBytes = checkedAddOrRefuse(indexBytes, artifact.indexByteCount(), operationIndex)
                    ?: return overflowRefusal(operationIndex, "indexBytes", budgetCode)
                if (vertexBytes > effective.maxVertexBytes) {
                    return budgetRefusal(operationIndex, "maxVertexBytes", vertexBytes, effective.maxVertexBytes, budgetCode)
                }
                if (indexBytes > effective.maxIndexBytes) {
                    return budgetRefusal(operationIndex, "maxIndexBytes", indexBytes, effective.maxIndexBytes, budgetCode)
                }
                val vertexRange = rangeOrNull(
                    artifactKey, PreparedVerticesUploadBufferKind.Vertex,
                    vertexOffset, artifact.vertexByteCount(),
                ) ?: return overflowRefusal(operationIndex, "vertexUploadRange", budgetCode)
                val indexRange = artifact.indexByteCount().takeIf { it > 0L }?.let { byteCount ->
                    rangeOrNull(
                        artifactKey, PreparedVerticesUploadBufferKind.Index,
                        indexOffset, byteCount,
                    ) ?: return overflowRefusal(operationIndex, "indexUploadRange", budgetCode)
                }
                val nextVertexOffset = vertexRange.endExclusive
                val nextIndexOffset = indexRange?.endExclusive ?: indexOffset
                val totalUploadBytes = checkedAddOrRefuse(
                    nextVertexOffset, nextIndexOffset, operationIndex,
                ) ?: return overflowRefusal(operationIndex, "totalUploadBytes", budgetCode)
                if (totalUploadBytes > effective.maxTotalUploadBytes) {
                    return budgetRefusal(
                        operationIndex, "maxTotalUploadBytes", totalUploadBytes,
                        effective.maxTotalUploadBytes, budgetCode,
                    )
                }
                artifacts[artifactKey] = artifact
                vertexRanges += vertexRange
                indexRange?.let(indexRanges::add)
                vertexOffset = nextVertexOffset
                indexOffset = nextIndexOffset
            }

            val materialFrameSnapshot = GPUPreparedMaterialFrameIdentityAuthority.authenticate(draw.material)
            val material = materialFrameSnapshot.program
            val materialBucketKey = materialBucketKeySelector(materialFrameSnapshot)
            if (materialBucketKey.isBlank()) {
                return refused(
                    operationIndex,
                    "blank_material_bucket_key",
                    code = GPUPreparedVerticesRefusalCodes.Material,
                )
            }
            val existingMaterialSnapshot = materialSnapshotsByBucketKey[materialBucketKey]
            if (existingMaterialSnapshot != null &&
                !GPUPreparedMaterialFrameIdentityAuthority.exactlyMatches(
                    existingMaterialSnapshot.program,
                    material,
                )
            ) {
                return refused(
                    operationIndex,
                    "material_identity_collision",
                    mapOf(
                        "materialKey" to materialBucketKey,
                        "authority" to "GPUPreparedMaterialProgram",
                    ),
                    GPUPreparedVerticesRefusalCodes.Material,
                )
            }
            if (existingMaterialSnapshot == null) {
                materialSnapshotsByBucketKey[materialBucketKey] = materialFrameSnapshot
            }
            val authenticatedMaterialKey = materialFrameSnapshot.identity.bucketKey
            val existingPublishedMaterial = materials[authenticatedMaterialKey]
            if (existingPublishedMaterial != null &&
                !GPUPreparedMaterialFrameIdentityAuthority.exactlyMatches(
                    existingPublishedMaterial,
                    material,
                )
            ) {
                return refused(
                    operationIndex,
                    "material_identity_collision",
                    mapOf(
                        "materialKey" to authenticatedMaterialKey,
                        "authority" to "GPUPreparedMaterialProgram",
                    ),
                    GPUPreparedVerticesRefusalCodes.Material,
                )
            }
            if (existingPublishedMaterial == null) {
                materials[authenticatedMaterialKey] = material
            }

            val (drawUniformBytes, drawRuntimeChildren) = materialBudgetSelector(material)
            if (drawUniformBytes < 0L || drawRuntimeChildren < 0) {
                return refused(operationIndex, "invalid_material_budget", code = budgetCode)
            }
            runtimeUniformBytes = checkedAddOrRefuse(runtimeUniformBytes, drawUniformBytes, operationIndex)
                ?: return overflowRefusal(operationIndex, "runtimeUniformBytes", budgetCode)
            runtimeChildren = checkedAddOrRefuse(
                runtimeChildren, drawRuntimeChildren.toLong(), operationIndex,
            ) ?: return overflowRefusal(operationIndex, "runtimeChildren", budgetCode)
            if (runtimeUniformBytes > effective.maxRuntimeUniformBytes) {
                return budgetRefusal(
                    operationIndex, "maxRuntimeUniformBytes",
                    runtimeUniformBytes, effective.maxRuntimeUniformBytes,
                    budgetCode,
                )
            }
            if (runtimeChildren > effective.maxRuntimeChildren.toLong()) {
                return budgetRefusal(
                    operationIndex, "maxRuntimeChildren",
                    runtimeChildren, effective.maxRuntimeChildren.toLong(),
                    budgetCode,
                )
            }
            artifactKeyByOperation[operationIndex] = artifactKey
            val canonicalArtifact = artifacts.getValue(artifactKey)
            commands += PreparedVerticesFrameCommand(
                operationIndex = operationIndex,
                artifactKey = artifactKey,
                artifact = canonicalArtifact,
                materialKey = authenticatedMaterialKey,
                materialFrameSnapshot = materialFrameSnapshot,
                draw = draw,
            )
        }

        val totalUploadBytes = checkedAddOrRefuse(vertexOffset, indexOffset, null)
            ?: error("Per-draw total upload accounting must already have refused overflow")

        return PreparedVerticesFrameInventoryResult.Ready(
            PreparedVerticesFrameInventory(
                commands = commands,
                artifactsByKey = artifacts,
                materialsByKey = materials,
                artifactKeyByOperationIndex = artifactKeyByOperation,
                vertexUploadRanges = vertexRanges,
                indexUploadRanges = indexRanges,
                elidedVerticesOperationIndices = elided.map { it.operationIndex },
                capabilitySnapshotHash = capabilities.canonicalSnapshotHash(),
                metrics = PreparedVerticesFrameMetrics(
                    drawCount = commands.size,
                    uniqueArtifactCount = artifacts.size,
                    vertexBytes = vertexBytes,
                    indexBytes = indexBytes,
                    totalUploadBytes = totalUploadBytes,
                    runtimeUniformBytes = runtimeUniformBytes,
                    runtimeChildren = runtimeChildren.toInt(),
                ),
                limitEvidence = limitEvidence,
            ),
        )
    }

    private fun effectiveLimits(
        configured: PreparedVerticesFrameInventoryLimits,
        capabilities: GPUCapabilities,
    ): PreparedVerticesFrameLimitEvidence {
        val observedBufferLimit = capabilities.limits?.maxBufferSize
        val capabilitySource = capabilities.limits?.source ?: "policy.no-device-buffer-limit"
        if (observedBufferLimit == null) {
            return PreparedVerticesFrameLimitEvidence(configured, configured, capabilitySource)
        }
        val combinedBufferLimit = checkedMultiplyOrMax(observedBufferLimit, 2L)
        val effective = configured.copy(
            maxVertexBytes = minOf(configured.maxVertexBytes, observedBufferLimit),
            maxIndexBytes = minOf(configured.maxIndexBytes, observedBufferLimit),
            maxTotalUploadBytes = minOf(configured.maxTotalUploadBytes, combinedBufferLimit),
        )
        return PreparedVerticesFrameLimitEvidence(configured, effective, capabilitySource)
    }

    private fun rangeOrNull(
        key: String,
        kind: PreparedVerticesUploadBufferKind,
        cursor: Long,
        byteCount: Long,
    ): PreparedVerticesUploadRange? = try {
        val offset = checkedAlign(cursor, WEBGPU_BUFFER_UPLOAD_ALIGNMENT)
        val occupied = checkedAlign(byteCount, WEBGPU_BUFFER_UPLOAD_ALIGNMENT)
        Math.addExact(offset, occupied)
        PreparedVerticesUploadRange(key, kind, offset, byteCount, occupied)
    } catch (_: ArithmeticException) {
        null
    }

    private fun checkedAlign(value: Long, alignment: Long): Long {
        require(value >= 0L)
        val mask = alignment - 1L
        return Math.addExact(value, mask) and mask.inv()
    }

    private fun checkedAddOrRefuse(left: Long, right: Long, operationIndex: Int?): Long? =
        try {
            Math.addExact(left, right)
        } catch (_: ArithmeticException) {
            null
        }

    private fun checkedMultiplyOrMax(left: Long, right: Long): Long = try {
        Math.multiplyExact(left, right)
    } catch (_: ArithmeticException) {
        Long.MAX_VALUE
    }

    private fun budgetRefusal(
        operationIndex: Int?,
        budget: String,
        observed: Long,
        limit: Long,
        code: String = GPUPreparedVerticesRefusalCodes.Budget,
    ) = PreparedVerticesFrameInventoryResult.Refused(
        code = code,
        operationIndex = operationIndex,
        facts = linkedMapOf(
            "authority" to "PreparedVerticesFrameInventory",
            "reason" to "budget_exceeded",
            "budget" to budget,
            "observed" to observed.toString(),
            "limit" to limit.toString(),
            "operationIndex" to operationIndex.toString(),
        ),
    )

    private fun overflowRefusal(operationIndex: Int?, field: String, code: String) =
        PreparedVerticesFrameInventoryResult.Refused(
            code = code,
            operationIndex = operationIndex,
            facts = linkedMapOf(
                "authority" to "PreparedVerticesFrameInventory",
                "reason" to "checked_arithmetic_overflow",
                "field" to field,
                "operationIndex" to operationIndex.toString(),
            ),
        )

    private fun refused(
        operationIndex: Int?,
        reason: String,
        extraFacts: Map<String, String> = emptyMap(),
        code: String = GPUPreparedVerticesRefusalCodes.Budget,
    ) = PreparedVerticesFrameInventoryResult.Refused(
        code = code,
        operationIndex = operationIndex,
        facts = linkedMapOf(
            "authority" to "PreparedVerticesFrameInventory",
            "reason" to reason,
        ).apply { putAll(extraFacts) },
    )
}

private fun budgetCodeFor(operationKind: GPUPreparedVerticesOperationKind): String =
    if (operationKind == GPUPreparedVerticesOperationKind.DrawMesh) {
        GPUPreparedVerticesRefusalCodes.MeshBudget
    } else {
        GPUPreparedVerticesRefusalCodes.Budget
    }

private fun GPUPreparedVerticesUploadArtifact.vertexByteCount(): Long =
    Math.multiplyExact(vertexCount.toLong(), layout.strideBytes.toLong())

private fun GPUPreparedVerticesUploadArtifact.indexByteCount(): Long =
    indexCount?.let { count ->
        Math.multiplyExact(count.toLong(), if (indexFormat == "uint16") 2L else 4L)
    } ?: 0L

private fun GPUPreparedVerticesUploadArtifact.exactIdentityEquals(
    other: GPUPreparedVerticesUploadArtifact,
): Boolean =
    topology == other.topology &&
        layout == other.layout &&
        vertexCount == other.vertexCount &&
        indexCount == other.indexCount &&
        indexFormat == other.indexFormat &&
        canonicalizationIdentity == other.canonicalizationIdentity &&
        vertexContentHash == other.vertexContentHash &&
        indexContentHash == other.indexContentHash &&
        vertexBytesForUpload().contentEquals(other.vertexBytesForUpload()) &&
        nullableBytesEqual(indexBytesForUpload(), other.indexBytesForUpload())

private fun nullableBytesEqual(left: ByteArray?, right: ByteArray?): Boolean = when {
    left == null -> right == null
    right == null -> false
    else -> left.contentEquals(right)
}

private fun GPUPreparedMaterialProgram.frameMaterialBudget(): Pair<Long, Int> {
    var uniformBytes = uniformBytes.size.toLong()
    for (child in childPrograms) {
        uniformBytes = Math.addExact(uniformBytes, child.uniformBytes.size.toLong())
    }
    return uniformBytes to childPrograms.size
}
