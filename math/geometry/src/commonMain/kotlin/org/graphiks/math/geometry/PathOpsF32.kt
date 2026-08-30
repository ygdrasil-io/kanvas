package org.graphiks.math.geometry

import kotlin.math.pow

/** Boolean operations supported by [PathOpsF32]. */
public enum class PathBooleanOp { DIFFERENCE, INTERSECT, UNION, XOR, REVERSE_DIFFERENCE }

/** Renderer-neutral boolean operations over immutable [PathF32] values. */
public object PathOpsF32 {
    /** Combines two finite paths through the shared robust topology pipeline. */
    public fun op(first: PathF32, second: PathF32, op: PathBooleanOp): PathF32 =
        op(first, second, op, PathOpsLimitsI32())

    /** Resolves unary overlaps and self-intersections while retaining the source fill rule. */
    public fun simplify(path: PathF32): PathF32 = simplify(path, PathOpsLimitsI32())

    /** Reconstructs canonical non-overlapping contours with a winding fill rule. */
    public fun asWinding(path: PathF32): PathF32 = asWinding(path, PathOpsLimitsI32())

    internal fun op(
        first: PathF32,
        second: PathF32,
        op: PathBooleanOp,
        limits: PathOpsLimitsI32,
    ): PathF32 {
        require(!first.fillRule.isInverse() && !second.fillRule.isInverse()) {
            "Boolean operations require finite fill rules"
        }
        validateFinitePathF32(first)
        validateFinitePathF32(second)

        val normalization = pathNormalizationF64(listOf(first, second))
        val arrangement = buildArrangementF64(
            inputs = listOf(
                PathOperandInputF32(PathOperand.FIRST, first),
                PathOperandInputF32(PathOperand.SECOND, second),
            ),
            normalization = normalization,
            limits = limits,
        )
        val result = projectContoursF64ToPathF32(
            contours = arrangement.boundary(first.fillRule, second.fillRule, op),
            normalization = normalization,
            fillRule = FillRule.WINDING,
        )
        return result
    }

    internal fun simplify(path: PathF32, limits: PathOpsLimitsI32): PathF32 =
        unaryResultF32(path, limits, path.fillRule)

    internal fun asWinding(path: PathF32, limits: PathOpsLimitsI32): PathF32 =
        unaryResultF32(
            path = path,
            limits = limits,
            outputFillRule = if (path.fillRule.isInverse()) FillRule.INVERSE_WINDING else FillRule.WINDING,
        )
}

private data class PathOperandInputF32(
    val operand: PathOperand,
    val path: PathF32,
)

private data class PathInputVertexF64(
    val id: Int,
    val point: Point2F64,
    val originalPointF32: Point2F32?,
)

private data class PathInputEdgeSeedF64(
    val operand: PathOperand,
    val contourIndex: Int,
    val start: PathInputVertexF64,
    val end: PathInputVertexF64,
)

private data class ProjectedPathContourF32(
    val vertices: List<Point2F32>,
    val signedDoubleAreaExpansionF64: DoubleArray,
)

private val projectionCollapseDoubleAreaThresholdF64: Double = 2.0.pow(-46)

private fun unaryResultF32(path: PathF32, limits: PathOpsLimitsI32, outputFillRule: FillRule): PathF32 {
    validateFinitePathF32(path)
    val normalization = pathNormalizationF64(listOf(path))
    val arrangement = buildArrangementF64(
        inputs = listOf(PathOperandInputF32(PathOperand.FIRST, path)),
        normalization = normalization,
        limits = limits,
    )
    return projectContoursF64ToPathF32(
        contours = arrangement.unaryBoundary(path.fillRule),
        normalization = normalization,
        fillRule = outputFillRule,
    )
}

private fun buildArrangementF64(
    inputs: List<PathOperandInputF32>,
    normalization: PathNormalizationF64,
    limits: PathOpsLimitsI32,
): PathArrangementF64 {
    val edges = inputEdgesF64(inputs, normalization, limits)
    val splitEdges = splitPathEdgesF64(edges, limits)
    val arrangement = PathArrangementF64.build(splitEdges, limits)
    return arrangement
}

private fun inputEdgesF64(
    inputs: List<PathOperandInputF32>,
    normalization: PathNormalizationF64,
    limits: PathOpsLimitsI32,
): List<PathInputEdgeF64> {
    val policy = PathFlatteningPolicyF64(limits = limits)
    val vertices = mutableListOf<PathInputVertexF64>()
    val seeds = mutableListOf<PathInputEdgeSeedF64>()

    inputs.forEach { input ->
        val contours = PathFlattenerF64.flatten(
            normalizedPath = NormalizedPathF64(input.path, normalization),
            policy = policy,
            closeForFill = true,
        )
        contours.forEachIndexed { contourIndex, contour ->
            val points = canonicalFlattenedContourPointsF64(contour)
            if (points.size < 2) return@forEachIndexed
            val contourVertices = points.map { point ->
                PathInputVertexF64(
                    id = vertices.size,
                    point = point.point,
                    originalPointF32 = point.originalPointF32,
                ).also(vertices::add)
            }
            contourVertices.indices.forEach { startIndex ->
                seeds += PathInputEdgeSeedF64(
                    operand = input.operand,
                    contourIndex = contourIndex,
                    start = contourVertices[startIndex],
                    end = contourVertices[(startIndex + 1) % contourVertices.size],
                )
            }
        }
    }

    val parametersByVertexId = mutableMapOf<Int, MutableMap<Int, Double>>()
    seeds.forEachIndexed { edgeId, edge ->
        parametersByVertexId.getOrPut(edge.start.id) { mutableMapOf() }[edgeId] = 0.0
        parametersByVertexId.getOrPut(edge.end.id) { mutableMapOf() }[edgeId] = 1.0
    }
    val identitiesByVertexId = vertices.associate { vertex ->
        val parameters = parametersByVertexId.getValue(vertex.id)
        val edgeIds = parameters.keys.sorted()
        vertex.id to PathVertexIdentityF64(
            incidentEdgeIds = edgeIds,
            parameterByEdgeId = edgeIds.associateWith(parameters::getValue),
            originalPointF32 = vertex.originalPointF32,
        )
    }

    return seeds.mapIndexed { edgeId, edge ->
        PathInputEdgeF64(
            id = edgeId,
            operand = edge.operand,
            contourIndex = edge.contourIndex,
            startIdentity = identitiesByVertexId.getValue(edge.start.id),
            endIdentity = identitiesByVertexId.getValue(edge.end.id),
            start = edge.start.point,
            end = edge.end.point,
            windingDelta = 1,
        )
    }
}

private fun canonicalFlattenedContourPointsF64(contour: FlattenedContourF64): List<FlattenedPointF64> {
    val result = mutableListOf<FlattenedPointF64>()
    contour.points.forEach { point ->
        val previous = result.lastOrNull()
        when {
            previous == null -> result += point
            !samePathOperationPointF64(previous.point, point.point) -> result += point
            previous.originalPointF32 == null && point.originalPointF32 != null -> result[result.lastIndex] = point
        }
    }
    if (result.size > 1 && samePathOperationPointF64(result.first().point, result.last().point)) {
        val closingPoint = result.removeAt(result.lastIndex)
        if (result.first().originalPointF32 == null && closingPoint.originalPointF32 != null) {
            result[0] = closingPoint
        }
    }
    return result
}

private fun projectContoursF64ToPathF32(
    contours: List<PathContourF64>,
    normalization: PathNormalizationF64,
    fillRule: FillRule,
): PathF32 {
    val projected = contours.mapNotNull { contour -> projectContourF32(contour, normalization) }
        .sortedWith(
            Comparator { first, second ->
                val areaOrder = compareAbsoluteAreasF64(
                    first.signedDoubleAreaExpansionF64,
                    second.signedDoubleAreaExpansionF64,
                )
                if (areaOrder != 0) {
                    -areaOrder
                } else {
                    comparePathOperationPointsF32(first.vertices.first(), second.vertices.first())
                }
            },
        )
    val builder = PathBuilder(fillRule)
    projected.forEach { contour ->
        val first = contour.vertices.first()
        builder.moveTo(first.x, first.y)
        contour.vertices.drop(1).forEach { point -> builder.lineTo(point.x, point.y) }
        builder.close()
    }
    return builder.build()
}

private fun projectContourF32(
    contour: PathContourF64,
    normalization: PathNormalizationF64,
): ProjectedPathContourF32? {
    if (contour.vertices.isEmpty()) return null
    val originalPoints = contour.vertices.map { it.point }
    val originalArea = signedDoubleAreaExpansionF64(originalPoints + originalPoints.first())
    val originalSign = ExpansionF64.sign(originalArea)
    if (originalSign == 0) return null

    var vertices = mutableListOf<Point2F32>()
    contour.vertices.forEach { vertex ->
        val projected = vertex.originalPointF32 ?: normalization.denormalize(vertex.point)
        if (!projected.isFinite()) throw IllegalStateException("path-f32-projection-collapse")
        if (vertices.lastOrNull()?.let { samePathOperationPointF32(it, projected) } != true) {
            vertices += projected
        }
    }
    if (vertices.size > 1 && samePathOperationPointF32(vertices.first(), vertices.last())) {
        vertices.removeAt(vertices.lastIndex)
    }
    if (vertices.size < 3) return projectionCollapseOrDropF32(originalArea)

    var projectedArea = signedDoubleAreaExpansionF64(vertices.map(Point2F32::toPoint2F64) + vertices.first().toPoint2F64())
    var projectedSign = ExpansionF64.sign(projectedArea)
    if (projectedSign == 0) return projectionCollapseOrDropF32(originalArea)
    if (projectedSign != originalSign) {
        vertices = vertices.asReversed().toMutableList()
        projectedArea = signedDoubleAreaExpansionF64(vertices.map(Point2F32::toPoint2F64) + vertices.first().toPoint2F64())
        projectedSign = ExpansionF64.sign(projectedArea)
        if (projectedSign != originalSign) throw IllegalStateException("path-f32-projection-collapse")
    }

    vertices = rotatePathOperationVerticesF32(vertices)
    projectedArea = signedDoubleAreaExpansionF64(vertices.map(Point2F32::toPoint2F64) + vertices.first().toPoint2F64())
    if (ExpansionF64.sign(projectedArea) != originalSign) throw IllegalStateException("path-f32-projection-collapse")
    return ProjectedPathContourF32(vertices, projectedArea)
}

private fun projectionCollapseOrDropF32(originalArea: DoubleArray): Nothing? {
    if (isTopologicallySignificantAreaF64(originalArea)) {
        throw IllegalStateException("path-f32-projection-collapse")
    }
    return null
}

private fun isTopologicallySignificantAreaF64(area: DoubleArray): Boolean {
    val sign = ExpansionF64.sign(area)
    if (sign == 0) return false
    val absoluteArea = if (sign > 0) area else area.negatedPathOperationExpansionF64()
    return ExpansionF64.sign(
        ExpansionF64.expansionDiff(absoluteArea, doubleArrayOf(projectionCollapseDoubleAreaThresholdF64)),
    ) > 0
}

private fun compareAbsoluteAreasF64(first: DoubleArray, second: DoubleArray): Int {
    val firstSign = ExpansionF64.sign(first)
    val secondSign = ExpansionF64.sign(second)
    if (firstSign == 0 || secondSign == 0) throw IllegalStateException("path-f32-projection-collapse")
    val firstAbsolute = if (firstSign > 0) first else first.negatedPathOperationExpansionF64()
    val secondAbsolute = if (secondSign > 0) second else second.negatedPathOperationExpansionF64()
    return ExpansionF64.sign(ExpansionF64.expansionDiff(firstAbsolute, secondAbsolute))
}

private fun DoubleArray.negatedPathOperationExpansionF64(): DoubleArray = DoubleArray(size) { index -> -this[index] }

private fun rotatePathOperationVerticesF32(vertices: List<Point2F32>): MutableList<Point2F32> {
    val firstIndex = vertices.indices.minWithOrNull(
        Comparator { firstIndex, secondIndex ->
            comparePathOperationPointsF32(vertices[firstIndex], vertices[secondIndex])
        },
    ) ?: return vertices.toMutableList()
    return (vertices.drop(firstIndex) + vertices.take(firstIndex)).toMutableList()
}

private fun comparePathOperationPointsF32(first: Point2F32, second: Point2F32): Int {
    comparePathOperationCoordinatesF32(first.x, second.x).takeIf { it != 0 }?.let { return it }
    return comparePathOperationCoordinatesF32(first.y, second.y)
}

private fun comparePathOperationCoordinatesF32(first: Float, second: Float): Int = when {
    first == second -> 0
    first < second -> -1
    else -> 1
}

private fun samePathOperationPointF64(first: Point2F64, second: Point2F64): Boolean =
    first.x == second.x && first.y == second.y

private fun samePathOperationPointF32(first: Point2F32, second: Point2F32): Boolean =
    first.x == second.x && first.y == second.y

private fun validateFinitePathF32(path: PathF32) {
    path.forEach { segment ->
        when (segment) {
            is PathSegmentF32.MoveTo -> requireFinitePointF32(segment.point)
            is PathSegmentF32.LineTo -> requireFinitePointF32(segment.point)
            is PathSegmentF32.QuadTo -> {
                requireFinitePointF32(segment.control)
                requireFinitePointF32(segment.point)
            }
            is PathSegmentF32.CubicTo -> {
                requireFinitePointF32(segment.control1)
                requireFinitePointF32(segment.control2)
                requireFinitePointF32(segment.point)
            }
            is PathSegmentF32.ArcTo -> {
                require(segment.radius.isFinite() && segment.xAxisRotation.isFinite()) { "Path operations require finite coordinates" }
                requireFinitePointF32(segment.point)
            }
            PathSegmentF32.Close -> Unit
        }
    }
}

private fun requireFinitePointF32(point: Point2F32) {
    require(point.isFinite()) { "Path operations require finite coordinates" }
}
