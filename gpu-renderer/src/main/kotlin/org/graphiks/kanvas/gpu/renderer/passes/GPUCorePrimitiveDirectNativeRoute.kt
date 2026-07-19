package org.graphiks.kanvas.gpu.renderer.passes

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload

/** Pure, handle-free geometry route shared by recording and execution validation. */
internal sealed interface GPUCorePrimitiveDirectNativeRoute {
    enum class Lane {
        DirectGeometry,
        AnalyticShape,
    }

    class Accepted(
        vertices: FloatArray,
        indices: IntArray,
        val lane: Lane = Lane.DirectGeometry,
        val renderScissor: GPUPixelBounds? = null,
    ) : GPUCorePrimitiveDirectNativeRoute {
        private val vertexSnapshot = vertices.copyOf()
        private val indexSnapshot = indices.copyOf()
        val vertexCount: Int
        val indexCount: Int = indexSnapshot.size
        val maxLocalIndex: Int

        init {
            require(vertexSnapshot.isNotEmpty() && vertexSnapshot.size % 2 == 0 &&
                vertexSnapshot.all(Float::isFinite)
            ) {
                "Direct CorePrimitive vertices must contain finite xy pairs"
            }
            vertexCount = vertexSnapshot.size / 2
            require(indexSnapshot.isNotEmpty()) {
                "Direct CorePrimitive geometry requires at least one local index"
            }
            var maximum = -1
            indexSnapshot.forEach { index ->
                require(index >= 0) { "Direct CorePrimitive local indices must be non-negative" }
                if (index > maximum) maximum = index
            }
            require(maximum < vertexCount) {
                "Direct CorePrimitive maximum local index must address its local vertices"
            }
            maxLocalIndex = maximum
        }

        fun copyVerticesInto(destination: FloatArray, destinationOffset: Int = 0) {
            require(destinationOffset >= 0 && destinationOffset <= destination.size - vertexSnapshot.size) {
                "Direct CorePrimitive vertex snapshot does not fit its destination"
            }
            vertexSnapshot.copyInto(destination, destinationOffset)
        }

        fun copyIndicesInto(destination: IntArray, destinationOffset: Int = 0) {
            require(destinationOffset >= 0 && destinationOffset <= destination.size - indexSnapshot.size) {
                "Direct CorePrimitive index snapshot does not fit its destination"
            }
            indexSnapshot.copyInto(destination, destinationOffset)
        }

        override fun equals(other: Any?): Boolean = other is Accepted &&
            lane == other.lane && renderScissor == other.renderScissor &&
            vertexSnapshot.contentEquals(other.vertexSnapshot) && indexSnapshot.contentEquals(other.indexSnapshot)

        override fun hashCode(): Int {
            var result = vertexSnapshot.contentHashCode()
            result = 31 * result + indexSnapshot.contentHashCode()
            result = 31 * result + lane.hashCode()
            result = 31 * result + (renderScissor?.hashCode() ?: 0)
            return result
        }

        override fun toString(): String =
            "Accepted(lane=$lane, renderScissor=$renderScissor, " +
                "vertexCount=$vertexCount, indexCount=$indexCount, maxLocalIndex=$maxLocalIndex)"
    }

    data class Refused(val code: String, val message: String) : GPUCorePrimitiveDirectNativeRoute
}

/**
 * Classifies one direct route from semantic data plus already-resolved neutral authorities.
 *
 * Callers own clip-plan and blend-plan interpretation. This pure classifier deliberately accepts
 * only the resulting exact scissor and canonical premultiplied-SrcOver fact, so `passes` does not
 * depend on recording or execution packages.
 */
internal fun validateCorePrimitiveDirectNativeRoute(
    semantic: GPUDrawSemanticPayload.CorePrimitive,
    exactClipScissor: GPUPixelBounds?,
    canonicalPremultipliedSrcOver: Boolean,
    samplePlan: GPUSamplePlan,
    targetFormat: String,
): GPUCorePrimitiveDirectNativeRoute {
    fun refused(code: String, message: String) = GPUCorePrimitiveDirectNativeRoute.Refused(code, message)
    if (targetFormat != "rgba8unorm") {
        return refused(
            "unsupported.native-core-primitive.target-format",
            "Direct CorePrimitive native geometry requires rgba8unorm.",
        )
    }
    if (samplePlan != GPUSamplePlan.SingleSampleFrame) {
        return refused(
            "unsupported.native-core-primitive.sample-plan",
            "Direct CorePrimitive native geometry requires one sample.",
        )
    }
    if (!canonicalPremultipliedSrcOver) {
        return refused(
            "unsupported.native-core-primitive.blend",
            "Direct CorePrimitive native geometry requires canonical premultiplied SrcOver.",
        )
    }
    val analyticShape = when (semantic.geometry) {
        is GPUCorePrimitiveGeometry.Rect ->
            semantic.coverageMode == GPUCorePrimitiveCoverageMode.ScalarAA
        is GPUCorePrimitiveGeometry.RRect ->
            semantic.coverageMode == GPUCorePrimitiveCoverageMode.FullOrScissor ||
                semantic.coverageMode == GPUCorePrimitiveCoverageMode.ScalarAA
        is GPUCorePrimitiveGeometry.TriangulatedPath -> false
    }
    val coverageCompatible = when (semantic.geometry) {
        is GPUCorePrimitiveGeometry.Rect,
        is GPUCorePrimitiveGeometry.RRect,
        -> semantic.coverageMode == GPUCorePrimitiveCoverageMode.FullOrScissor ||
            semantic.coverageMode == GPUCorePrimitiveCoverageMode.ScalarAA
        is GPUCorePrimitiveGeometry.TriangulatedPath ->
            semantic.coverageMode == GPUCorePrimitiveCoverageMode.FullOrScissor
    }
    if (!coverageCompatible) {
        return refused(
            "unsupported.native-core-primitive.coverage",
            "CorePrimitive geometry does not match the promoted hard or ScalarAA native lane.",
        )
    }
    val exactScissor = exactClipScissor ?: return refused(
        "unsupported.native-core-primitive.clip",
        "Direct CorePrimitive native geometry accepts only no clip or scissor clip.",
    )
    if (semantic.scissorBounds != exactScissor) {
        return refused(
            "invalid.native-core-primitive.scissor-authority",
            "The semantic scissor must exactly match the classified direct clip plan.",
        )
    }
    fun accepted(
        vertices: FloatArray,
        indices: IntArray,
        lane: GPUCorePrimitiveDirectNativeRoute.Lane = GPUCorePrimitiveDirectNativeRoute.Lane.DirectGeometry,
        renderScissor: GPUPixelBounds = exactScissor,
    ): GPUCorePrimitiveDirectNativeRoute = try {
        GPUCorePrimitiveDirectNativeRoute.Accepted(vertices, indices, lane, renderScissor)
    } catch (_: IllegalArgumentException) {
        refused(
            "invalid.native-core-primitive.geometry-indices",
            "Direct CorePrimitive geometry requires finite xy vertices and in-range local indices.",
        )
    }
    return when (val geometry = semantic.geometry) {
        is GPUCorePrimitiveGeometry.Rect -> if (analyticShape) {
            analyticShapeRoute(
                geometry.left,
                geometry.top,
                geometry.right,
                geometry.bottom,
                antiAlias = true,
                targetBounds = semantic.targetBounds,
                clipScissor = exactScissor,
                accepted = ::accepted,
                refused = ::refused,
            )
        } else {
            accepted(
                vertices = quadVertices(geometry.left, geometry.top, geometry.right, geometry.bottom),
                indices = QUAD_INDICES,
            )
        }
        is GPUCorePrimitiveGeometry.RRect -> analyticShapeRoute(
            geometry.left,
            geometry.top,
            geometry.right,
            geometry.bottom,
            antiAlias = semantic.coverageMode == GPUCorePrimitiveCoverageMode.ScalarAA,
            targetBounds = semantic.targetBounds,
            clipScissor = exactScissor,
            accepted = ::accepted,
            refused = ::refused,
        )
        is GPUCorePrimitiveGeometry.TriangulatedPath -> when {
            geometry.inverseFill -> refused(
                "unsupported.native-core-primitive.inverse-fill",
                "Inverse fill requires a later cover or mask route.",
            )
            geometry.geometryMode != GPUCorePrimitiveGeometryMode.DirectTriangles -> refused(
                "unsupported.native-core-primitive.geometry",
                "Stencil edge fans require the later stencil-cover route.",
            )
            geometry.strokeStyle != null -> refused(
                "unsupported.native-core-primitive.geometry",
                "Direct triangles may not retain stroke lowering state.",
            )
            else -> accepted(
                FloatArray(geometry.vertices.size) { index -> geometry.vertices[index] },
                IntArray(geometry.indices.size) { index -> geometry.indices[index] },
            )
        }
    }
}

private val QUAD_INDICES = intArrayOf(0, 2, 1, 0, 3, 2)

private fun quadVertices(left: Float, top: Float, right: Float, bottom: Float): FloatArray =
    floatArrayOf(left, top, right, top, right, bottom, left, bottom)

private fun analyticShapeRoute(
    geometryLeft: Float,
    geometryTop: Float,
    geometryRight: Float,
    geometryBottom: Float,
    antiAlias: Boolean,
    targetBounds: GPUPixelBounds,
    clipScissor: GPUPixelBounds,
    accepted: (
        FloatArray,
        IntArray,
        GPUCorePrimitiveDirectNativeRoute.Lane,
        GPUPixelBounds,
    ) -> GPUCorePrimitiveDirectNativeRoute,
    refused: (String, String) -> GPUCorePrimitiveDirectNativeRoute.Refused,
): GPUCorePrimitiveDirectNativeRoute {
    val outset = if (antiAlias) 1f else 0f
    val quadLeft = geometryLeft - outset
    val quadTop = geometryTop - outset
    val quadRight = geometryRight + outset
    val quadBottom = geometryBottom + outset
    val renderScissor = conservativeAnalyticShapeScissor(
        quadLeft,
        quadTop,
        quadRight,
        quadBottom,
        targetBounds,
        clipScissor,
    ) ?: return refused(
        "unsupported.native-core-primitive.analytic-shape.empty-scissor",
        "Analytic CorePrimitive geometry has no pixel intersection with its target and sealed clip.",
    )
    return accepted(
        quadVertices(quadLeft, quadTop, quadRight, quadBottom),
        QUAD_INDICES,
        GPUCorePrimitiveDirectNativeRoute.Lane.AnalyticShape,
        renderScissor,
    )
}

private fun conservativeAnalyticShapeScissor(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    targetBounds: GPUPixelBounds,
    clipScissor: GPUPixelBounds,
): GPUPixelBounds? {
    if (!left.isFinite() || !top.isFinite() || !right.isFinite() || !bottom.isFinite() ||
        left >= right || top >= bottom
    ) return null
    val roundedLeft = floor(left.toDouble())
        .coerceIn(Int.MIN_VALUE.toDouble(), Int.MAX_VALUE.toDouble()).toInt()
    val roundedTop = floor(top.toDouble())
        .coerceIn(Int.MIN_VALUE.toDouble(), Int.MAX_VALUE.toDouble()).toInt()
    val roundedRight = ceil(right.toDouble())
        .coerceIn(Int.MIN_VALUE.toDouble(), Int.MAX_VALUE.toDouble()).toInt()
    val roundedBottom = ceil(bottom.toDouble())
        .coerceIn(Int.MIN_VALUE.toDouble(), Int.MAX_VALUE.toDouble()).toInt()
    val intersectedLeft = max(max(targetBounds.left, clipScissor.left), roundedLeft)
    val intersectedTop = max(max(targetBounds.top, clipScissor.top), roundedTop)
    val intersectedRight = min(min(targetBounds.right, clipScissor.right), roundedRight)
    val intersectedBottom = min(min(targetBounds.bottom, clipScissor.bottom), roundedBottom)
    if (intersectedLeft >= intersectedRight || intersectedTop >= intersectedBottom) return null
    return GPUPixelBounds(intersectedLeft, intersectedTop, intersectedRight, intersectedBottom)
}
