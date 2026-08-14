package org.graphiks.kanvas.gpu.renderer.vertices

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Collections
import java.util.TreeMap
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedVerticesCanonicalizationIdentity
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedVerticesUploadArtifact

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

        val bounds = source.positions.bounds()
        val vertexBytes = packVertices(
            source = source,
            vertexCount = shape.vertexCount,
            layout = shape.layout,
            byteCount = shape.vertexByteCount.toInt(),
        )
        val indexBytes = indexByteCount?.let { byteCount ->
            packIndices(source, shape.topologyPlan, requireNotNull(indexFormat), byteCount.toInt())
        }
        return GPUPreparedVerticesPackingResult.Ready(
            artifact = GPUPreparedVerticesUploadArtifact(
                topology = shape.topologyPlan.topology,
                layout = shape.layout,
                vertexBytes = vertexBytes,
                indexBytes = indexBytes,
                vertexCount = shape.vertexCount,
                indexCount = shape.topologyPlan.indexCount,
                indexFormat = indexFormat,
                provenance = source.provenance,
                canonicalizationIdentity = if (shape.topologyPlan.fanExpanded) {
                    GPUPreparedVerticesCanonicalizationIdentity.TriangleFanToTriangleListV1
                } else {
                    GPUPreparedVerticesCanonicalizationIdentity.IdentityV1
                },
            ),
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

    private fun packVertices(
        source: PreparedVerticesSourceSnapshot,
        vertexCount: Int,
        layout: GPUVertexLayoutPlan,
        byteCount: Int,
    ): ByteArray {
        val output = ByteBuffer.allocate(byteCount).order(ByteOrder.LITTLE_ENDIAN)
        repeat(vertexCount) { vertexIndex ->
            val positionOffset = vertexIndex * POSITION_COMPONENTS
            output.putFloat(source.positions[positionOffset])
            output.putFloat(source.positions[positionOffset + 1])

            source.colorsRgba8?.let { colors ->
                val colorOffset = vertexIndex * COLOR_COMPONENTS
                val alpha = colors[colorOffset + 3].toInt() and 0xff
                output.put(premultiplyUnorm8(colors[colorOffset], alpha))
                output.put(premultiplyUnorm8(colors[colorOffset + 1], alpha))
                output.put(premultiplyUnorm8(colors[colorOffset + 2], alpha))
                output.put(alpha.toByte())
            }
            source.texCoords?.let { texCoords ->
                val texCoordOffset = vertexIndex * TEX_COORD_COMPONENTS
                output.putFloat(texCoords[texCoordOffset])
                output.putFloat(texCoords[texCoordOffset + 1])
            }
        }
        check(output.position() == layout.strideBytes * vertexCount)
        return output.array()
    }

    private fun packIndices(
        source: PreparedVerticesSourceSnapshot,
        topologyPlan: CanonicalTopologyPlan,
        indexFormat: String,
        byteCount: Int,
    ): ByteArray {
        val output = ByteBuffer.allocate(byteCount).order(ByteOrder.LITTLE_ENDIAN)
        fun putIndex(index: Int) {
            when (indexFormat) {
                UINT16_FORMAT -> output.putShort(index.toShort())
                UINT32_FORMAT -> output.putInt(index)
                else -> error("Validated prepared vertices index format required")
            }
        }

        if (topologyPlan.fanExpanded) {
            val sourceCount = source.indices?.size ?: (source.positions.size / POSITION_COMPONENTS)
            fun sourceIndex(position: Int): Int = source.indices?.get(position) ?: position
            val anchor = sourceIndex(0)
            for (position in 1 until sourceCount - 1) {
                putIndex(anchor)
                putIndex(sourceIndex(position))
                putIndex(sourceIndex(position + 1))
            }
        } else {
            requireNotNull(source.indices).forEach(::putIndex)
        }
        check(output.position() == byteCount)
        return output.array()
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

private fun FloatArray.bounds(): GPUPreparedVerticesFloatBounds {
    var left = this[0]
    var top = this[1]
    var right = left
    var bottom = top
    var offset = POSITION_COMPONENTS
    while (offset < size) {
        val x = this[offset]
        val y = this[offset + 1]
        if (x < left) left = x
        if (x > right) right = x
        if (y < top) top = y
        if (y > bottom) bottom = y
        offset += POSITION_COMPONENTS
    }
    return GPUPreparedVerticesFloatBounds(left, top, right, bottom)
}

private fun checkedFanIndexCount(sourceElementCount: Int): Int? {
    val count = try {
        Math.multiplyExact(sourceElementCount.toLong() - 2L, TRIANGLE_INDEX_COUNT.toLong())
    } catch (_: ArithmeticException) {
        return null
    }
    return count.takeIf { it <= Int.MAX_VALUE.toLong() }?.toInt()
}

private fun premultiplyUnorm8(component: Byte, alpha: Int): Byte {
    val unsignedComponent = component.toInt() and 0xff
    return ((unsignedComponent * alpha + UNORM8_ROUND_HALF_UP_BIAS) / UNORM8_MAX).toByte()
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
private const val UNORM8_MAX = 255
private const val UNORM8_ROUND_HALF_UP_BIAS = 127
