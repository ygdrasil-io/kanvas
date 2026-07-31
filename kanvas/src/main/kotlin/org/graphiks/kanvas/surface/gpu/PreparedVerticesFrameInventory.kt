package org.graphiks.kanvas.surface.gpu

import java.util.Collections
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedVerticesUploadArtifact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.materials.CanonicalIdentityEncoder
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgram
import org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes

private const val WEBGPU_BUFFER_UPLOAD_ALIGNMENT = 4L

data class PreparedVerticesFrameInventoryLimits(
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

data class PreparedVerticesFrameLimitEvidence(
    val configured: PreparedVerticesFrameInventoryLimits,
    val effective: PreparedVerticesFrameInventoryLimits,
    val capabilitySource: String,
) {
    init {
        require(capabilitySource.isNotBlank()) { "capabilitySource must not be blank" }
    }
}

enum class PreparedVerticesUploadBufferKind { Vertex, Index }

data class PreparedVerticesUploadRange(
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

class PreparedVerticesFrameCommand internal constructor(
    val commandId: Int,
    val operationIndex: Int,
    val artifactKey: String,
    val artifact: GPUPreparedVerticesUploadArtifact,
    val materialKey: String,
    val material: GPUPreparedMaterialProgram,
    val draw: GPUPreparedVerticesDraw,
) {
    init {
        require(commandId >= 0)
        require(operationIndex >= 0)
        require(artifactKey.isNotBlank())
        require(materialKey.isNotBlank())
    }
}

data class PreparedVerticesFrameMetrics(
    val drawCount: Int,
    val uniqueArtifactCount: Int,
    val vertexBytes: Long,
    val indexBytes: Long,
    val totalUploadBytes: Long,
    val runtimeUniformBytes: Long,
    val runtimeChildren: Int,
)

class PreparedVerticesFrameInventory internal constructor(
    commands: List<PreparedVerticesFrameCommand>,
    artifactsByKey: Map<String, GPUPreparedVerticesUploadArtifact>,
    materialsByKey: Map<String, GPUPreparedMaterialProgram>,
    artifactKeyByOperationIndex: Map<Int, String>,
    vertexUploadRanges: List<PreparedVerticesUploadRange>,
    indexUploadRanges: List<PreparedVerticesUploadRange>,
    val metrics: PreparedVerticesFrameMetrics,
    val limitEvidence: PreparedVerticesFrameLimitEvidence,
) {
    val commands: List<PreparedVerticesFrameCommand> =
        Collections.unmodifiableList(commands.toList())
    val artifactsByKey: Map<String, GPUPreparedVerticesUploadArtifact> =
        Collections.unmodifiableMap(LinkedHashMap(artifactsByKey))
    val materialsByKey: Map<String, GPUPreparedMaterialProgram> =
        Collections.unmodifiableMap(LinkedHashMap(materialsByKey))
    val artifactKeyByOperationIndex: Map<Int, String> =
        Collections.unmodifiableMap(LinkedHashMap(artifactKeyByOperationIndex))
    val vertexUploadRanges: List<PreparedVerticesUploadRange> =
        Collections.unmodifiableList(vertexUploadRanges.toList())
    val indexUploadRanges: List<PreparedVerticesUploadRange> =
        Collections.unmodifiableList(indexUploadRanges.toList())
}

sealed interface PreparedVerticesFrameInventoryResult {
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

object PreparedVerticesFrameInventoryBuilder {
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
        materialBudgetSelector: (GPUPreparedMaterialProgram) -> Pair<Long, Int> =
            GPUPreparedMaterialProgram::frameMaterialBudget,
    ): PreparedVerticesFrameInventoryResult {
        val limitEvidence = effectiveLimits(limits, capabilities)
        val effective = limitEvidence.effective
        if (draws.size > effective.maxDraws) {
            return budgetRefusal(
                draws.getOrNull(effective.maxDraws)?.operationIndex,
                "maxDraws", draws.size.toLong(), effective.maxDraws.toLong(),
            )
        }

        val artifacts = linkedMapOf<String, GPUPreparedVerticesUploadArtifact>()
        val materials = linkedMapOf<String, GPUPreparedMaterialProgram>()
        val commands = ArrayList<PreparedVerticesFrameCommand>(draws.size)
        val artifactKeyByOperation = linkedMapOf<Int, String>()
        var vertexBytes = 0L
        var indexBytes = 0L
        var runtimeUniformBytes = 0L
        var runtimeChildren = 0L

        for (draw in draws) {
            val operationIndex = draw.operationIndex
            if (artifactKeyByOperation.containsKey(operationIndex)) {
                return refused(
                    operationIndex,
                    "duplicate_operation_index",
                    mapOf("operationIndex" to operationIndex.toString()),
                )
            }
            val artifact = draw.artifact
            val artifactKey = artifactKeySelector(artifact)
            if (artifactKey.isBlank()) {
                return refused(operationIndex, "blank_artifact_key")
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
                )
            }
            if (existingArtifact == null) {
                if (artifacts.size >= effective.maxUniqueArtifacts) {
                    return budgetRefusal(
                        operationIndex, "maxUniqueArtifacts",
                        Math.addExact(artifacts.size.toLong(), 1L),
                        effective.maxUniqueArtifacts.toLong(),
                    )
                }
                vertexBytes = checkedAddOrRefuse(vertexBytes, artifact.vertexByteCount(), operationIndex)
                    ?: return overflowRefusal(operationIndex, "vertexBytes")
                indexBytes = checkedAddOrRefuse(indexBytes, artifact.indexByteCount(), operationIndex)
                    ?: return overflowRefusal(operationIndex, "indexBytes")
                if (vertexBytes > effective.maxVertexBytes) {
                    return budgetRefusal(operationIndex, "maxVertexBytes", vertexBytes, effective.maxVertexBytes)
                }
                if (indexBytes > effective.maxIndexBytes) {
                    return budgetRefusal(operationIndex, "maxIndexBytes", indexBytes, effective.maxIndexBytes)
                }
                artifacts[artifactKey] = artifact
            }

            val material = draw.material
            val materialKey = material.exactFrameIdentity()
            val existingMaterial = materials[materialKey]
            if (existingMaterial != null && !existingMaterial.exactFrameEquals(material)) {
                return refused(
                    operationIndex,
                    "material_identity_collision",
                    mapOf("materialKey" to materialKey, "authority" to "GPUPreparedMaterialProgram"),
                    GPUPreparedVerticesRefusalCodes.Material,
                )
            }
            if (existingMaterial == null) materials[materialKey] = material

            val (drawUniformBytes, drawRuntimeChildren) = materialBudgetSelector(material)
            if (drawUniformBytes < 0L || drawRuntimeChildren < 0) {
                return refused(operationIndex, "invalid_material_budget")
            }
            runtimeUniformBytes = checkedAddOrRefuse(runtimeUniformBytes, drawUniformBytes, operationIndex)
                ?: return overflowRefusal(operationIndex, "runtimeUniformBytes")
            runtimeChildren = checkedAddOrRefuse(
                runtimeChildren, drawRuntimeChildren.toLong(), operationIndex,
            ) ?: return overflowRefusal(operationIndex, "runtimeChildren")
            if (runtimeUniformBytes > effective.maxRuntimeUniformBytes) {
                return budgetRefusal(
                    operationIndex, "maxRuntimeUniformBytes",
                    runtimeUniformBytes, effective.maxRuntimeUniformBytes,
                )
            }
            if (runtimeChildren > effective.maxRuntimeChildren.toLong()) {
                return budgetRefusal(
                    operationIndex, "maxRuntimeChildren",
                    runtimeChildren, effective.maxRuntimeChildren.toLong(),
                    if (draw.operationKind == GPUPreparedVerticesOperationKind.DrawMesh) {
                        GPUPreparedVerticesRefusalCodes.MeshBudget
                    } else {
                        GPUPreparedVerticesRefusalCodes.Budget
                    },
                )
            }
            artifactKeyByOperation[operationIndex] = artifactKey
            val canonicalArtifact = artifacts.getValue(artifactKey)
            commands += PreparedVerticesFrameCommand(
                commandId = operationIndex,
                operationIndex = operationIndex,
                artifactKey = artifactKey,
                artifact = canonicalArtifact,
                materialKey = materialKey,
                material = materials.getValue(materialKey),
                draw = draw,
            )
        }

        val vertexRanges = ArrayList<PreparedVerticesUploadRange>(artifacts.size)
        val indexRanges = ArrayList<PreparedVerticesUploadRange>(artifacts.size)
        var vertexOffset = 0L
        var indexOffset = 0L
        for ((key, artifact) in artifacts) {
            val vertexRange = rangeOrNull(
                key, PreparedVerticesUploadBufferKind.Vertex,
                vertexOffset, artifact.vertexByteCount(),
            ) ?: return overflowRefusal(artifact.firstOperationIndex(draws), "vertexUploadRange")
            vertexRanges += vertexRange
            vertexOffset = vertexRange.endExclusive
            val indexByteCount = artifact.indexByteCount()
            if (indexByteCount > 0L) {
                val indexRange = rangeOrNull(
                    key, PreparedVerticesUploadBufferKind.Index,
                    indexOffset, indexByteCount,
                ) ?: return overflowRefusal(artifact.firstOperationIndex(draws), "indexUploadRange")
                indexRanges += indexRange
                indexOffset = indexRange.endExclusive
            }
        }
        val totalUploadBytes = checkedAddOrRefuse(vertexOffset, indexOffset, null)
            ?: return overflowRefusal(null, "totalUploadBytes")
        if (totalUploadBytes > effective.maxTotalUploadBytes) {
            return budgetRefusal(
                draws.lastOrNull()?.operationIndex,
                "maxTotalUploadBytes", totalUploadBytes, effective.maxTotalUploadBytes,
            )
        }

        return PreparedVerticesFrameInventoryResult.Ready(
            PreparedVerticesFrameInventory(
                commands = commands,
                artifactsByKey = artifacts,
                materialsByKey = materials,
                artifactKeyByOperationIndex = artifactKeyByOperation,
                vertexUploadRanges = vertexRanges,
                indexUploadRanges = indexRanges,
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
        ),
    )

    private fun overflowRefusal(operationIndex: Int?, field: String) =
        PreparedVerticesFrameInventoryResult.Refused(
            code = GPUPreparedVerticesRefusalCodes.Budget,
            operationIndex = operationIndex,
            facts = linkedMapOf(
                "authority" to "PreparedVerticesFrameInventory",
                "reason" to "checked_arithmetic_overflow",
                "field" to field,
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

private fun GPUPreparedVerticesUploadArtifact.firstOperationIndex(
    draws: List<GPUPreparedVerticesDraw>,
): Int? = draws.firstOrNull { draw -> draw.artifact.key == key }?.operationIndex

private fun GPUPreparedMaterialProgram.exactFrameIdentity(): String =
    CanonicalIdentityEncoder("prepared-vertices-frame-material-v1")
        .text("materialKey", materialKey)
        .text("wgslSource", wgslSource)
        .text("entryPoint", entryPoint)
        .text("fragmentHash", composableFragment.fragmentHash)
        .text("fragmentAbiHash", composableFragment.abiHash)
        .bytes("uniformBytes", uniformBytes.map(Int::toByte).toByteArray())
        .texts("sampledResources", sampledResources.flatMapIndexed { index, resource ->
            listOf(
                "resource[$index].key=${resource.resourceKey}",
                "resource[$index].contentHash=${resource.contentHash}",
                "resource[$index].width=${resource.width}",
                "resource[$index].height=${resource.height}",
                "resource[$index].sampling=${resource.samplingFilterMode}",
                "resource[$index].alphaOnly=${resource.alphaOnly}",
            )
        })
        .texts("childPrograms", childPrograms.flatMapIndexed { index, child ->
            buildList {
                add("child[$index].name=${child.name}")
                add("child[$index].role=${child.role.name}")
                add("child[$index].programKey=${child.programKey}")
                add("child[$index].abiHash=${child.abiHash}")
                child.uniformBytes.forEachIndexed { byteIndex, byte ->
                    add("child[$index].uniform[$byteIndex]=$byte")
                }
                child.resourceFacts.forEachIndexed { factIndex, fact ->
                    add("child[$index].resourceFact[$factIndex]=$fact")
                }
                add("child[$index].wgslSource=${child.wgslSource}")
                add("child[$index].evaluationFunction=${child.evaluationFunction}")
            }
        })
        .floatBits("paintAlpha", paintAlpha)
        .text("sourceKind", sourceKind.name)
        .text("preCoverageSourceAlpha", preCoverageSourceAlpha.name)
        .text("abiHash", abiHash)
        .digestIdentity()

private fun GPUPreparedMaterialProgram.frameMaterialBudget(): Pair<Long, Int> {
    var uniformBytes = uniformBytes.size.toLong()
    for (child in childPrograms) {
        uniformBytes = Math.addExact(uniformBytes, child.uniformBytes.size.toLong())
    }
    return uniformBytes to childPrograms.size
}

private fun GPUPreparedMaterialProgram.exactFrameEquals(other: GPUPreparedMaterialProgram): Boolean =
    materialKey == other.materialKey &&
        wgslSource == other.wgslSource &&
        entryPoint == other.entryPoint &&
        composableFragment.fragmentHash == other.composableFragment.fragmentHash &&
        composableFragment.abiHash == other.composableFragment.abiHash &&
        uniformBytes == other.uniformBytes &&
        paintAlpha.toRawBits() == other.paintAlpha.toRawBits() &&
        sourceKind == other.sourceKind &&
        preCoverageSourceAlpha == other.preCoverageSourceAlpha &&
        abiHash == other.abiHash &&
        sampledResources.size == other.sampledResources.size &&
        sampledResources.zip(other.sampledResources).all { (left, right) ->
            left.resourceKey == right.resourceKey &&
                left.contentHash == right.contentHash &&
                left.width == right.width &&
                left.height == right.height &&
                left.samplingFilterMode == right.samplingFilterMode &&
                left.alphaOnly == right.alphaOnly &&
                left.rgba8Bytes().contentEquals(right.rgba8Bytes())
        } &&
        childPrograms.size == other.childPrograms.size &&
        childPrograms.zip(other.childPrograms).all { (left, right) ->
            left.name == right.name &&
                left.role == right.role &&
                left.programKey == right.programKey &&
                left.abiHash == right.abiHash &&
                left.uniformBytes == right.uniformBytes &&
                left.resourceFacts == right.resourceFacts &&
                left.wgslSource == right.wgslSource &&
                left.evaluationFunction == right.evaluationFunction
        }
