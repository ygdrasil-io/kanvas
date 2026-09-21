package org.graphiks.kanvas.gpu.renderer.vertices

import java.util.Collections
import java.util.TreeMap
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedVerticesUploadArtifact
import org.graphiks.kanvas.render.ir.PreparedVerticesUploadPayloadV1
import org.graphiks.math.geometry.TriangleMeshF32
import org.graphiks.math.geometry.TriangleTopologyI32

data class GPUPreparedVerticesFloatBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

data class GPUPreparedVerticesPackingLimits(
    val maxVertices: Int,
    val maxIndices: Int,
    val maxVertexBytes: Long,
    val maxIndexBytes: Long,
    val maxFanExpandedIndices: Int,
)

sealed interface GPUPreparedVerticesPackingResult {
    data class Ready(
        val artifact: GPUPreparedVerticesUploadArtifact,
        val sourceBounds: GPUPreparedVerticesFloatBounds,
    ) : GPUPreparedVerticesPackingResult

    data class Refused(
        val code: String,
        val facts: Map<String, String>,
    ) : GPUPreparedVerticesPackingResult
}

/**
 * Converts public DrawVertices geometry into the one closed upload form.
 *
 * Source colors are straight RGBA8. RGB is converted to canonical premultiplied
 * RGBA8 with alpha interpreted as UNORM8 and `(channel * alpha + 127) / 255`,
 * i.e. integer round-half-up. Alpha itself is preserved exactly.
 */
object GPUPreparedVerticesPacker {
    /** Conservative HotSpot-compatible upper bound for one final JVM array. */
    internal const val MAX_JVM_ARRAY_LENGTH: Int = Int.MAX_VALUE - 8

    fun pack(
        input: GPUPreparedVerticesArtifactInput,
        limits: GPUPreparedVerticesPackingLimits,
        supportsUint32Index: Boolean,
    ): GPUPreparedVerticesPackingResult {
        /*
         * Phase 1 reads immutable metadata and array lengths only, so malformed or
         * manifestly over-budget sources refuse before any potentially large copy.
         * Phase 2 snapshots every array, then inspects all mutable values exclusively
         * through those copies. Atomic multi-array snapshots are intentionally outside
         * this public input contract.
         */
        val shape = when (val preflight = preflightShape(input, limits)) {
            is PreparedVerticesShapePreflight.Ready -> preflight.shape
            is PreparedVerticesShapePreflight.Refused -> return preflight.refusal
        }
        val source = input.snapshot()

        source.positions.indexOfFirst { value -> !value.isFinite() }
            .takeIf { index -> index >= 0 }
            ?.let { index ->
                return source.refused(
                    code = GPUPreparedVerticesRefusalCodes.NonFinite,
                    reason = "non_finite_attribute",
                    "attribute" to "position",
                    "componentIndex" to index.toString(),
                    "value" to source.positions[index].toString(),
                )
            }
        source.texCoords?.indexOfFirst { value -> !value.isFinite() }
            ?.takeIf { index -> index >= 0 }
            ?.let { index ->
                return source.refused(
                    code = GPUPreparedVerticesRefusalCodes.NonFinite,
                    reason = "non_finite_attribute",
                    "attribute" to "texcoord",
                    "componentIndex" to index.toString(),
                    "value" to requireNotNull(source.texCoords)[index].toString(),
                )
            }

        source.indices?.forEachIndexed { position, index ->
            if (index < 0 || index >= shape.vertexCount) {
                return source.refused(
                    code = GPUPreparedVerticesRefusalCodes.IndexOutOfRange,
                    reason = "index_out_of_range",
                    "index" to index.toString(),
                    "indexPosition" to position.toString(),
                    "vertexCount" to shape.vertexCount.toString(),
                )
            }
        }

        val maxReferencedIndex = when {
            source.indices != null -> source.indices.maxOrNull()
            shape.topologyPlan.fanExpanded -> shape.vertexCount - 1
            else -> null
        }
        val indexFormat = maxReferencedIndex?.let { maxIndex ->
            if (maxIndex <= UINT16_MAX_INDEX) {
                UINT16_FORMAT
            } else {
                if (!supportsUint32Index) {
                    return source.refused(
                        code = GPUPreparedVerticesRefusalCodes.IndexFormat,
                        reason = "uint32_capability_unavailable",
                        "maxIndex" to maxIndex.toString(),
                        "requiredFormat" to UINT32_FORMAT,
                        "supportsUint32Index" to supportsUint32Index.toString(),
                    )
                }
                UINT32_FORMAT
            }
        }
        val indexByteCount = shape.topologyPlan.indexCount?.let { indexCount ->
            val elementBytes = if (indexFormat == UINT16_FORMAT) UINT16_BYTES else UINT32_BYTES
            checkedAllocationByteCount(indexCount, elementBytes)
                ?: return source.checkedOverflowRefusal("maxIndexBytes", indexCount.toLong())
        }
        indexByteCount?.let { byteCount ->
            budgetRefusal(source, "maxIndexBytes", byteCount, limits.maxIndexBytes)
                ?.let { return it }
        }

        val geometry = requireNotNull(TriangleMeshF32.ofOrNull(when (source.topology) {
            GPUVertexMode.Triangles -> TriangleTopologyI32.List
            GPUVertexMode.TriangleStrip -> TriangleTopologyI32.Strip
            GPUVertexMode.TriangleFan -> TriangleTopologyI32.Fan
            is GPUVertexMode.Unsupported -> error("Topology was refused before geometry packing")
        }, source.positions, source.texCoords, source.indices, limits.maxVertices,
            maxOf(limits.maxIndices, limits.maxFanExpandedIndices)))
        require(geometry.indexCountI32 == shape.topologyPlan.indexCount)
        val bounds = geometry.copyBoundsF32().let { GPUPreparedVerticesFloatBounds(it.left, it.top, it.right, it.bottom) }
        val sealed = PreparedVerticesUploadPayloadV1.seal(geometry, source.colorsRgba8)
        require(sealed.vertexCountI32 == shape.vertexCount && sealed.indexCountI32 == shape.topologyPlan.indexCount &&
            sealed.vertexStrideBytesI32 == shape.layout.strideBytes && sealed.vertexBytesI64 == shape.vertexByteCount &&
            sealed.indexBytesI64 == (indexByteCount ?: 0L) &&
            sealed.indexElementBytesI32 == indexFormat?.let { if (it == UINT16_FORMAT) UINT16_BYTES else UINT32_BYTES })
        return GPUPreparedVerticesPackingResult.Ready(
            artifact = GPUPreparedVerticesUploadArtifact.fromSealedPayload(sealed, source.provenance),
            sourceBounds = bounds,
        )
    }

    /** Checked JVM final-array sizing shared by vertex and index packing. */
    internal fun checkedAllocationByteCount(count: Int, elementBytes: Int): Long? {
        if (count < 0 || elementBytes <= 0) return null
        val byteCount = try {
            Math.multiplyExact(count.toLong(), elementBytes.toLong())
        } catch (_: ArithmeticException) {
            return null
        }
        return byteCount.takeIf { it <= MAX_JVM_ARRAY_LENGTH.toLong() }
    }

}

private data class PreparedVerticesSourceSnapshot(
    val topology: GPUVertexMode,
    val positions: FloatArray,
    val colorsRgba8: ByteArray?,
    val texCoords: FloatArray?,
    val indices: IntArray?,
    val provenance: String,
)

private data class CanonicalTopologyPlan(
    val topology: GPUVertexMode,
    val indexCount: Int?,
    val fanExpanded: Boolean,
)

private data class PreparedVerticesShapePlan(
    val vertexCount: Int,
    val topologyPlan: CanonicalTopologyPlan,
    val layout: GPUVertexLayoutPlan,
    val vertexByteCount: Long,
)

private sealed interface PreparedVerticesShapePreflight {
    data class Ready(val shape: PreparedVerticesShapePlan) : PreparedVerticesShapePreflight
    data class Refused(
        val refusal: GPUPreparedVerticesPackingResult.Refused,
    ) : PreparedVerticesShapePreflight
}

private data class PreparedVerticesRefusalContext(
    val topology: GPUVertexMode,
    val provenance: String,
)

private fun preflightShape(
    input: GPUPreparedVerticesArtifactInput,
    limits: GPUPreparedVerticesPackingLimits,
): PreparedVerticesShapePreflight {
    val context = PreparedVerticesRefusalContext(input.topology, input.provenance)
    fun refused(refusal: GPUPreparedVerticesPackingResult.Refused): PreparedVerticesShapePreflight =
        PreparedVerticesShapePreflight.Refused(refusal)

    invalidLimit(context, limits)?.let { return refused(it) }
    if (input.provenance.isBlank()) {
        return refused(context.refused(
            code = GPUPreparedVerticesRefusalCodes.AttributeLayout,
            reason = "provenance_blank",
        ))
    }
    if (input.positions.isEmpty()) {
        return refused(context.refused(
            code = GPUPreparedVerticesRefusalCodes.PositionCount,
            reason = "positions_empty",
            "positionFloatCount" to "0",
        ))
    }
    if (input.positions.size % POSITION_COMPONENTS != 0) {
        return refused(context.refused(
            code = GPUPreparedVerticesRefusalCodes.PositionCount,
            reason = "positions_not_float32x2",
            "positionFloatCount" to input.positions.size.toString(),
        ))
    }

    val vertexCount = input.positions.size / POSITION_COMPONENTS
    budgetRefusal(context, "maxVertices", vertexCount.toLong(), limits.maxVertices.toLong())
        ?.let { return refused(it) }

    input.colorsRgba8?.let { colors ->
        val expected = vertexCount.toLong() * COLOR_COMPONENTS
        if (colors.size.toLong() != expected) {
            return refused(context.refused(
                code = GPUPreparedVerticesRefusalCodes.AttributeCount,
                reason = "attribute_count_mismatch",
                "actual" to colors.size.toString(),
                "attribute" to "color",
                "expected" to expected.toString(),
            ))
        }
    }
    input.texCoords?.let { texCoords ->
        val expected = vertexCount.toLong() * TEX_COORD_COMPONENTS
        if (texCoords.size.toLong() != expected) {
            return refused(context.refused(
                code = GPUPreparedVerticesRefusalCodes.AttributeCount,
                reason = "attribute_count_mismatch",
                "actual" to texCoords.size.toString(),
                "attribute" to "texcoord",
                "expected" to expected.toString(),
            ))
        }
    }

    val sourceElementCount = input.indices?.size ?: vertexCount
    val topologyPlan = when (input.topology) {
        GPUVertexMode.Triangles -> {
            if (sourceElementCount <= 0 || sourceElementCount % TRIANGLE_INDEX_COUNT != 0) {
                return refused(context.topologyRefused(
                    reason = "triangle_count_not_multiple_of_three",
                    sourceElementCount = sourceElementCount,
                ))
            }
            CanonicalTopologyPlan(
                topology = GPUVertexMode.Triangles,
                indexCount = input.indices?.size,
                fanExpanded = false,
            )
        }
        GPUVertexMode.TriangleStrip -> {
            if (sourceElementCount < MIN_PRIMITIVE_ELEMENTS) {
                return refused(context.topologyRefused(
                    reason = "strip_requires_three_elements",
                    sourceElementCount = sourceElementCount,
                ))
            }
            CanonicalTopologyPlan(
                topology = GPUVertexMode.TriangleStrip,
                indexCount = input.indices?.size,
                fanExpanded = false,
            )
        }
        GPUVertexMode.TriangleFan -> {
            if (sourceElementCount < MIN_PRIMITIVE_ELEMENTS) {
                return refused(context.topologyRefused(
                    reason = "fan_requires_three_elements",
                    sourceElementCount = sourceElementCount,
                ))
            }
            val expandedCount = checkedFanIndexCount(sourceElementCount)
                ?: return refused(context.checkedOverflowRefusal(
                    "maxFanExpandedIndices",
                    sourceElementCount.toLong(),
                ))
            budgetRefusal(
                context,
                "maxFanExpandedIndices",
                expandedCount.toLong(),
                limits.maxFanExpandedIndices.toLong(),
            )?.let { return refused(it) }
            CanonicalTopologyPlan(
                topology = GPUVertexMode.Triangles,
                indexCount = expandedCount,
                fanExpanded = true,
            )
        }
        is GPUVertexMode.Unsupported -> {
            return refused(context.topologyRefused(
                reason = "unsupported_topology",
                sourceElementCount = sourceElementCount,
            ))
        }
    }
    topologyPlan.indexCount?.let { indexCount ->
        budgetRefusal(context, "maxIndices", indexCount.toLong(), limits.maxIndices.toLong())
            ?.let { return refused(it) }
        val minimumIndexByteCount = GPUPreparedVerticesPacker.checkedAllocationByteCount(
            count = indexCount,
            elementBytes = UINT16_BYTES,
        ) ?: return refused(context.checkedOverflowRefusal("maxIndexBytes", indexCount.toLong()))
        budgetRefusal(context, "maxIndexBytes", minimumIndexByteCount, limits.maxIndexBytes)
            ?.let { return refused(it) }
    }

    val layout = GPUPreparedVerticesLayoutAuthority.layout(
        hasColors = input.colorsRgba8 != null,
        hasTexCoords = input.texCoords != null,
    )
    val vertexByteCount = GPUPreparedVerticesPacker.checkedAllocationByteCount(
        count = vertexCount,
        elementBytes = layout.strideBytes,
    ) ?: return refused(context.checkedOverflowRefusal("maxVertexBytes", vertexCount.toLong()))
    budgetRefusal(context, "maxVertexBytes", vertexByteCount, limits.maxVertexBytes)
        ?.let { return refused(it) }

    return PreparedVerticesShapePreflight.Ready(
        PreparedVerticesShapePlan(
            vertexCount = vertexCount,
            topologyPlan = topologyPlan,
            layout = layout,
            vertexByteCount = vertexByteCount,
        ),
    )
}

private fun GPUPreparedVerticesArtifactInput.snapshot(): PreparedVerticesSourceSnapshot =
    PreparedVerticesSourceSnapshot(
        topology = topology,
        positions = positions.copyOf(),
        colorsRgba8 = colorsRgba8?.copyOf(),
        texCoords = texCoords?.copyOf(),
        indices = indices?.copyOf(),
        provenance = provenance,
    )

private fun invalidLimit(
    context: PreparedVerticesRefusalContext,
    limits: GPUPreparedVerticesPackingLimits,
): GPUPreparedVerticesPackingResult.Refused? {
    val values = listOf(
        "maxVertices" to limits.maxVertices.toLong(),
        "maxIndices" to limits.maxIndices.toLong(),
        "maxVertexBytes" to limits.maxVertexBytes,
        "maxIndexBytes" to limits.maxIndexBytes,
        "maxFanExpandedIndices" to limits.maxFanExpandedIndices.toLong(),
    )
    val invalid = values.firstOrNull { (_, value) -> value < 0L } ?: return null
    return context.refused(
        code = GPUPreparedVerticesRefusalCodes.Budget,
        reason = "invalid_limit",
        "budget" to invalid.first,
        "limit" to invalid.second.toString(),
    )
}

private fun budgetRefusal(
    context: PreparedVerticesRefusalContext,
    budget: String,
    actual: Long,
    limit: Long,
): GPUPreparedVerticesPackingResult.Refused? =
    if (actual > limit) {
        context.refused(
            code = GPUPreparedVerticesRefusalCodes.Budget,
            reason = "budget_exceeded",
            "actual" to actual.toString(),
            "budget" to budget,
            "limit" to limit.toString(),
        )
    } else {
        null
    }

private fun budgetRefusal(
    source: PreparedVerticesSourceSnapshot,
    budget: String,
    actual: Long,
    limit: Long,
): GPUPreparedVerticesPackingResult.Refused? =
    budgetRefusal(source.refusalContext(), budget, actual, limit)

private fun PreparedVerticesRefusalContext.checkedOverflowRefusal(
    budget: String,
    actualElements: Long,
): GPUPreparedVerticesPackingResult.Refused =
    refused(
        code = GPUPreparedVerticesRefusalCodes.Budget,
        reason = "checked_overflow",
        "actualElements" to actualElements.toString(),
        "budget" to budget,
    )

private fun PreparedVerticesRefusalContext.topologyRefused(
    reason: String,
    sourceElementCount: Int,
): GPUPreparedVerticesPackingResult.Refused =
    refused(
        code = GPUPreparedVerticesRefusalCodes.Topology,
        reason = reason,
        "sourceElementCount" to sourceElementCount.toString(),
    )

private fun PreparedVerticesRefusalContext.refused(
    code: String,
    reason: String,
    vararg details: Pair<String, String>,
): GPUPreparedVerticesPackingResult.Refused {
    require(code in GPUPreparedVerticesRefusalCodes.ALL) {
        "Prepared vertices packer refusal must use the canonical authority"
    }
    val facts = TreeMap<String, String>()
    facts["provenance"] = provenance
    facts["reason"] = reason
    facts["topology"] = topology.sourceLabel
    details.forEach { (name, value) -> facts[name] = value }
    return GPUPreparedVerticesPackingResult.Refused(
        code = code,
        facts = Collections.unmodifiableMap(facts),
    )
}

private fun PreparedVerticesSourceSnapshot.refusalContext(): PreparedVerticesRefusalContext =
    PreparedVerticesRefusalContext(topology, provenance)

private fun PreparedVerticesSourceSnapshot.checkedOverflowRefusal(
    budget: String,
    actualElements: Long,
): GPUPreparedVerticesPackingResult.Refused =
    refusalContext().checkedOverflowRefusal(budget, actualElements)

private fun PreparedVerticesSourceSnapshot.refused(
    code: String,
    reason: String,
    vararg details: Pair<String, String>,
): GPUPreparedVerticesPackingResult.Refused =
    refusalContext().refused(code, reason, *details)

private fun checkedFanIndexCount(sourceElementCount: Int): Int? {
    val count = try {
        Math.multiplyExact(sourceElementCount.toLong() - 2L, TRIANGLE_INDEX_COUNT.toLong())
    } catch (_: ArithmeticException) {
        return null
    }
    return count.takeIf { it <= Int.MAX_VALUE.toLong() }?.toInt()
}

private const val POSITION_COMPONENTS = 2
private const val COLOR_COMPONENTS = 4
private const val TEX_COORD_COMPONENTS = 2
private const val TRIANGLE_INDEX_COUNT = 3
private const val MIN_PRIMITIVE_ELEMENTS = 3
private const val UINT16_MAX_INDEX = 65_535
private const val UINT16_BYTES = 2
private const val UINT32_BYTES = 4
private const val UINT16_FORMAT = "uint16"
private const val UINT32_FORMAT = "uint32"
