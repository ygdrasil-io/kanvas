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
        val candidateWorkBudget = PathCandidateWorkBudgetI32(limits.maxCandidateProbes)
        val arrangement = buildArrangementF64(
            inputs = listOf(
                PathOperandInputF32(PathOperand.FIRST, first),
                PathOperandInputF32(PathOperand.SECOND, second),
            ),
            normalization = normalization,
            limits = limits,
            candidateWorkBudget = candidateWorkBudget,
        )
        val result = projectContoursF64ToPathF32(
            contours = arrangement.boundary(first.fillRule, second.fillRule, op),
            normalization = normalization,
            fillRule = FillRule.WINDING,
            candidateWorkBudget = candidateWorkBudget,
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
    val sourceFirstVertices: List<Point2F64>,
    val sourceLastVertices: List<Point2F64>,
    val originalSignedDoubleAreaExpansionF64: DoubleArray,
    val vertices: List<Point2F32>,
    val signedDoubleAreaExpansionF64: DoubleArray,
    val normalizedSignedDoubleAreaExpansionF64: DoubleArray,
)

private val projectionAreaToleranceF64: Double = 2.0.pow(-46)
// `signedDoubleAreaExpansionF64` represents twice the signed area. This is exactly
// `2 * projectionAreaToleranceF64`, not the area tolerance itself.
private val projectionCollapseDoubleAreaToleranceF64: Double = 2.0 * projectionAreaToleranceF64

private data class ProjectedBoundaryEdgeF64(
    val contourIndex: Int,
    val edgeIndex: Int,
    val projected: PathInputEdgeF64,
    val source: PathInputEdgeF64,
)

// The key is structural F32 geometry only: no quantization, strings, or source IDs participate.
// Its vertices are exact F32 values with signed zero canonicalized, collinear subdivision removed,
// and the lexicographically smaller of its two orientations selected.
private data class ProjectedCycleKeyF32(
    val vertices: List<Point2F32>,
)

private data class ProjectedContourPairI32(
    val firstContourIndex: Int,
    val secondContourIndex: Int,
)

private fun unaryResultF32(path: PathF32, limits: PathOpsLimitsI32, outputFillRule: FillRule): PathF32 {
    validateFinitePathF32(path)
    val normalization = pathNormalizationF64(listOf(path))
    val candidateWorkBudget = PathCandidateWorkBudgetI32(limits.maxCandidateProbes)
    val arrangement = buildArrangementF64(
        inputs = listOf(PathOperandInputF32(PathOperand.FIRST, path)),
        normalization = normalization,
        limits = limits,
        candidateWorkBudget = candidateWorkBudget,
    )
    return projectContoursF64ToPathF32(
        contours = arrangement.unaryBoundary(path.fillRule),
        normalization = normalization,
        fillRule = outputFillRule,
        candidateWorkBudget = candidateWorkBudget,
    )
}

private fun buildArrangementF64(
    inputs: List<PathOperandInputF32>,
    normalization: PathNormalizationF64,
    limits: PathOpsLimitsI32,
    candidateWorkBudget: PathCandidateWorkBudgetI32,
): PathArrangementF64 {
    val edges = inputEdgesF64(inputs, normalization, limits)
    val splitEdges = splitPathEdgesF64(edges, limits, candidateWorkBudget)
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

internal fun projectContoursF64ToPathF32(
    contours: List<PathContourF64>,
    normalization: PathNormalizationF64,
    fillRule: FillRule,
    candidateWorkBudget: PathCandidateWorkBudgetI32 =
        PathCandidateWorkBudgetI32(PathOpsLimitsI32().maxCandidateProbes),
): PathF32 {
    val projected = contours.mapNotNull { contour -> projectContourF32(contour, normalization) }
    validateProjectedContourSetF64(projected, normalization, candidateWorkBudget)
    val ordered = projected
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
    ordered.forEach { contour ->
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
    var sourceFirstVertices = mutableListOf<Point2F64>()
    var sourceLastVertices = mutableListOf<Point2F64>()
    contour.vertices.forEach { vertex ->
        val projected = vertex.originalPointF32 ?: normalization.denormalize(vertex.point)
        if (!projected.isFinite()) throw IllegalStateException("path-f32-projection-collapse")
        if (vertices.lastOrNull()?.let { samePathOperationPointF32(it, projected) } != true) {
            vertices += projected
            sourceFirstVertices += vertex.point
            sourceLastVertices += vertex.point
        } else {
            sourceLastVertices[sourceLastVertices.lastIndex] = vertex.point
        }
    }
    if (vertices.size > 1 && samePathOperationPointF32(vertices.first(), vertices.last())) {
        // The final and initial projected runs are cyclically adjacent. Preserve the real source
        // bridge entering the merged run from the former final run and leaving it through the
        // former initial run; treating either as a chord would hide a new projected collision.
        sourceFirstVertices[0] = sourceFirstVertices.last()
        vertices.removeAt(vertices.lastIndex)
        sourceFirstVertices.removeAt(sourceFirstVertices.lastIndex)
        sourceLastVertices.removeAt(sourceLastVertices.lastIndex)
    }
    if (vertices.size < 3) return projectionCollapseOrDropF32(originalArea)

    var projectedArea = signedDoubleAreaExpansionF64(vertices.map(Point2F32::toPoint2F64) + vertices.first().toPoint2F64())
    var projectedSign = ExpansionF64.sign(projectedArea)
    if (projectedSign == 0) return projectionCollapseOrDropF32(originalArea)
    if (projectedSign != originalSign) {
        vertices = vertices.asReversed().toMutableList()
        val reversedSourceFirstVertices = sourceLastVertices.asReversed().toMutableList()
        sourceLastVertices = sourceFirstVertices.asReversed().toMutableList()
        sourceFirstVertices = reversedSourceFirstVertices
        projectedArea = signedDoubleAreaExpansionF64(vertices.map(Point2F32::toPoint2F64) + vertices.first().toPoint2F64())
        projectedSign = ExpansionF64.sign(projectedArea)
        if (projectedSign != originalSign) throw IllegalStateException("path-f32-projection-collapse")
    }

    val firstIndex = pathOperationRotationIndexF32(vertices)
    vertices = rotatePathOperationVerticesF32(vertices, firstIndex)
    sourceFirstVertices =
        (sourceFirstVertices.drop(firstIndex) + sourceFirstVertices.take(firstIndex)).toMutableList()
    sourceLastVertices = (sourceLastVertices.drop(firstIndex) + sourceLastVertices.take(firstIndex)).toMutableList()
    projectedArea = signedDoubleAreaExpansionF64(vertices.map(Point2F32::toPoint2F64) + vertices.first().toPoint2F64())
    if (ExpansionF64.sign(projectedArea) != originalSign) throw IllegalStateException("path-f32-projection-collapse")
    val normalizedProjectedArea = signedDoubleAreaExpansionF64(
        vertices.map(normalization::normalize) + normalization.normalize(vertices.first()),
    )
    if (ExpansionF64.sign(normalizedProjectedArea) != originalSign) {
        throw IllegalStateException("path-f32-projection-collapse")
    }
    return ProjectedPathContourF32(
        sourceFirstVertices,
        sourceLastVertices,
        originalArea,
        vertices,
        projectedArea,
        normalizedProjectedArea,
    )
}

private fun validateProjectedContourSetF64(
    contours: List<ProjectedPathContourF32>,
    normalization: PathNormalizationF64,
    candidateWorkBudget: PathCandidateWorkBudgetI32,
) {
    val cycleKeys = contours.map { contour ->
        canonicalProjectedCycleKeyF32(contour.vertices, candidateWorkBudget)
    }
    val edges = projectedBoundaryEdgesF64(contours)
    if (edges.size >= 2) {
        val sourceContactContourPairs = projectedContourPairsWithSourceContactF64(
            edges,
            contours,
            candidateWorkBudget,
        )
        validateProjectedBoundaryContactsF64(
            edges,
            contours,
            cycleKeys,
            sourceContactContourPairs,
            candidateWorkBudget,
        )
    }
    // This pass is deliberately independent of the edge-pair walk: a group can be significant
    // only after all of its coincident cycles are aggregated, even when each pair is below the
    // promised area tolerance.
    validateProjectedCycleGroupsF64(contours, cycleKeys, normalization, candidateWorkBudget)
}

private fun validateProjectedBoundaryContactsF64(
    edges: List<ProjectedBoundaryEdgeF64>,
    contours: List<ProjectedPathContourF32>,
    cycleKeys: List<ProjectedCycleKeyF32>,
    sourceContactContourPairs: Set<ProjectedContourPairI32>,
    candidateWorkBudget: PathCandidateWorkBudgetI32,
): Unit {
    forEachPathEdgeCandidatePairF64(edges.map(ProjectedBoundaryEdgeF64::projected), candidateWorkBudget) { firstIndex, secondIndex ->
        val first = edges[firstIndex]
        val second = edges[secondIndex]
        if (projectedBoundaryEdgesAreAdjacentF64(first, second, contours)) return@forEachPathEdgeCandidatePairF64

        // The source bridge is authoritative. Debit and classify it before asking whether its
        // F32 image touches: otherwise a newly rounded endpoint or collinear overlap could be
        // mistaken for the pre-existing tangent/overlap it only resembles after projection.
        candidateWorkBudget.consume()
        val sourceIntersection = intersectPathEdgesF64(first.source, second.source)
        candidateWorkBudget.consume()
        val projectedIntersection = intersectPathEdgesF64(first.projected, second.projected)
            ?: return@forEachPathEdgeCandidatePairF64
        val contourPair = projectedContourPairI32(first.contourIndex, second.contourIndex)
        if (
            sourceIntersectionBacksProjectedContactF64(sourceIntersection, projectedIntersection) ||
                (contourPair != null && contourPair in sourceContactContourPairs)
        ) {
            return@forEachPathEdgeCandidatePairF64
        }

        if (first.contourIndex != second.contourIndex) {
            // Two exactly coincident full cycles are resolved by the aggregate pass below. This
            // permits a complete group whose exact cumulative modification is at or below the
            // tolerance, while corner/partial contacts between different cycles remain errors.
            if (cycleKeys[first.contourIndex] != cycleKeys[second.contourIndex]) {
                throw IllegalStateException("path-f32-projection-collapse")
            }
            return@forEachPathEdgeCandidatePairF64
        }

        // A non-adjacent edge contact within one contour can erase a narrow face even though no
        // cross-contour winding sum changes. Compare the source normalized contour to its actual
        // F32 image; a material loss is not an allowed projection simplification.
        if (projectedContourModificationIsSignificantF64(contours[first.contourIndex])) {
            throw IllegalStateException("path-f32-projection-collapse")
        }
    }
}

private fun projectedContourPairsWithSourceContactF64(
    edges: List<ProjectedBoundaryEdgeF64>,
    contours: List<ProjectedPathContourF32>,
    candidateWorkBudget: PathCandidateWorkBudgetI32,
): Set<ProjectedContourPairI32> {
    val result = linkedSetOf<ProjectedContourPairI32>()
    forEachPathEdgeCandidatePairF64(edges.map(ProjectedBoundaryEdgeF64::projected), candidateWorkBudget) { firstIndex, secondIndex ->
        val first = edges[firstIndex]
        val second = edges[secondIndex]
        if (projectedBoundaryEdgesAreAdjacentF64(first, second, contours)) return@forEachPathEdgeCandidatePairF64
        // Evaluate the source relation before the F32 image. A source contact elsewhere between
        // the same two selected contours proves their intended tangent/overlap topology, so a
        // neighboring F32 lattice contact produced by flattening is not a new component merge.
        candidateWorkBudget.consume()
        val sourceIntersection = intersectPathEdgesF64(first.source, second.source)
        candidateWorkBudget.consume()
        intersectPathEdgesF64(first.projected, second.projected)
            ?: return@forEachPathEdgeCandidatePairF64
        if (sourceIntersection != null) {
            projectedContourPairI32(first.contourIndex, second.contourIndex)?.let(result::add)
        }
    }
    return result
}

private fun projectedContourPairI32(firstContourIndex: Int, secondContourIndex: Int): ProjectedContourPairI32? = when {
    firstContourIndex == secondContourIndex -> null
    firstContourIndex < secondContourIndex -> ProjectedContourPairI32(firstContourIndex, secondContourIndex)
    else -> ProjectedContourPairI32(secondContourIndex, firstContourIndex)
}

private fun sourceIntersectionBacksProjectedContactF64(
    source: PathIntersectionF64?,
    projected: PathIntersectionF64,
): Boolean = when (projected) {
    is PathIntersectionF64.PointF64 -> source is PathIntersectionF64.PointF64 || source is PathIntersectionF64.OverlapF64
    is PathIntersectionF64.OverlapF64 -> source is PathIntersectionF64.OverlapF64
}

private fun projectedContourModificationIsSignificantF64(contour: ProjectedPathContourF32): Boolean =
    isTopologicallySignificantAreaF64(
        ExpansionF64.expansionDiff(
            contour.originalSignedDoubleAreaExpansionF64,
            contour.normalizedSignedDoubleAreaExpansionF64,
        ),
    )

private fun validateProjectedCycleGroupsF64(
    contours: List<ProjectedPathContourF32>,
    cycleKeys: List<ProjectedCycleKeyF32>,
    normalization: PathNormalizationF64,
    candidateWorkBudget: PathCandidateWorkBudgetI32,
) {
    // Linked insertion order follows the canonical contour order. Equality remains the complete
    // structural point sequence, not a lossy numeric key, and each member is charged before it
    // is inserted so this aggregation cannot add unbounded free work after the broad phase.
    val membersByKey = linkedMapOf<ProjectedCycleKeyF32, MutableList<ProjectedPathContourF32>>()
    contours.indices.forEach { contourIndex ->
        candidateWorkBudget.consume()
        membersByKey.getOrPut(cycleKeys[contourIndex]) { mutableListOf() } += contours[contourIndex]
    }
    membersByKey.forEach { (key, members) ->
        if (members.size > 1) {
            validateProjectedCycleGroupF64(key, members, normalization, candidateWorkBudget)
        }
    }
}

private fun validateProjectedCycleGroupF64(
    key: ProjectedCycleKeyF32,
    members: List<ProjectedPathContourF32>,
    normalization: PathNormalizationF64,
    candidateWorkBudget: PathCandidateWorkBudgetI32,
) {
    val canonicalProjectedArea = signedDoubleAreaExpansionF64(
        key.vertices.map(normalization::normalize) + normalization.normalize(key.vertices.first()),
    )
    val canonicalSign = ExpansionF64.sign(canonicalProjectedArea)
    if (canonicalSign == 0) throw IllegalStateException("path-f32-projection-collapse")

    var sourceAggregate = doubleArrayOf()
    var projectedAggregate = doubleArrayOf()
    var minimumSourceAbsoluteArea: DoubleArray? = null
    var maximumSourceAbsoluteArea: DoubleArray? = null
    members.forEach { member ->
        candidateWorkBudget.consume()
        sourceAggregate = ExpansionF64.expansionSum(sourceAggregate, member.originalSignedDoubleAreaExpansionF64)
        val memberSign = ExpansionF64.sign(member.normalizedSignedDoubleAreaExpansionF64)
        if (memberSign == 0) throw IllegalStateException("path-f32-projection-collapse")
        val projectedContribution = if (memberSign == canonicalSign) {
            canonicalProjectedArea
        } else {
            canonicalProjectedArea.negatedPathOperationExpansionF64()
        }
        projectedAggregate = ExpansionF64.expansionSum(projectedAggregate, projectedContribution)

        val sourceAbsoluteArea = absolutePathOperationExpansionF64(member.originalSignedDoubleAreaExpansionF64)
        val minimum = minimumSourceAbsoluteArea
        if (minimum == null) {
            minimumSourceAbsoluteArea = sourceAbsoluteArea
            maximumSourceAbsoluteArea = sourceAbsoluteArea
        } else {
            candidateWorkBudget.consume()
            if (compareNonNegativeExpansionsF64(sourceAbsoluteArea, minimum) < 0) {
                minimumSourceAbsoluteArea = sourceAbsoluteArea
            }
            candidateWorkBudget.consume()
            if (compareNonNegativeExpansionsF64(sourceAbsoluteArea, checkNotNull(maximumSourceAbsoluteArea)) > 0) {
                maximumSourceAbsoluteArea = sourceAbsoluteArea
            }
        }
    }

    candidateWorkBudget.consume()
    val signedAggregateModification = ExpansionF64.expansionDiff(sourceAggregate, projectedAggregate)
    candidateWorkBudget.consume()
    val cumulativeBoundaryModification = ExpansionF64.expansionDiff(
        checkNotNull(maximumSourceAbsoluteArea),
        checkNotNull(minimumSourceAbsoluteArea),
    )
    if (
        isTopologicallySignificantAreaF64(signedAggregateModification) ||
            isTopologicallySignificantAreaF64(cumulativeBoundaryModification)
    ) {
        throw IllegalStateException("path-f32-projection-collapse")
    }
}

private fun absolutePathOperationExpansionF64(area: DoubleArray): DoubleArray =
    if (ExpansionF64.sign(area) >= 0) area else area.negatedPathOperationExpansionF64()

private fun compareNonNegativeExpansionsF64(first: DoubleArray, second: DoubleArray): Int =
    ExpansionF64.sign(ExpansionF64.expansionDiff(first, second))

private fun canonicalProjectedCycleKeyF32(
    vertices: List<Point2F32>,
    candidateWorkBudget: PathCandidateWorkBudgetI32,
): ProjectedCycleKeyF32 {
    val forward = canonicalProjectedCycleForEqualityF32(vertices, candidateWorkBudget)
    val reverse = canonicalProjectedCycleForEqualityF32(vertices.asReversed(), candidateWorkBudget)
    if (forward.size < 3 || reverse.size < 3) throw IllegalStateException("path-f32-projection-collapse")
    return ProjectedCycleKeyF32(
        if (compareProjectedCycleVerticesF32(forward, reverse, candidateWorkBudget) <= 0) forward else reverse,
    )
}

private fun compareProjectedCycleVerticesF32(
    first: List<Point2F32>,
    second: List<Point2F32>,
    candidateWorkBudget: PathCandidateWorkBudgetI32,
): Int {
    first.indices.forEach { index ->
        if (index >= second.size) return 1
        candidateWorkBudget.consume()
        val pointOrder = comparePathOperationPointsF32(first[index], second[index])
        if (pointOrder != 0) return pointOrder
    }
    return first.size.compareTo(second.size)
}

private fun canonicalProjectedCycleForEqualityF32(
    vertices: List<Point2F32>,
    candidateWorkBudget: PathCandidateWorkBudgetI32,
): List<Point2F32> {
    val reduced = mutableListOf<Point2F32>()
    vertices.forEach { point ->
        candidateWorkBudget.consume()
        reduced += canonicalProjectedPointF32(point)
        while (
            reduced.size >= 3 &&
                projectedMiddlePointIsCollinearF32(
                    reduced[reduced.lastIndex - 2],
                    reduced[reduced.lastIndex - 1],
                    reduced.last(),
                    candidateWorkBudget,
                )
        ) {
            reduced.removeAt(reduced.lastIndex - 1)
        }
    }
    var removedAtSeam = true
    while (removedAtSeam && reduced.size >= 3) {
        removedAtSeam = false
        if (projectedMiddlePointIsCollinearF32(reduced.last(), reduced.first(), reduced[1], candidateWorkBudget)) {
            reduced.removeAt(0)
            removedAtSeam = true
            continue
        }
        if (
            projectedMiddlePointIsCollinearF32(
                reduced[reduced.lastIndex - 1],
                reduced.last(),
                reduced.first(),
                candidateWorkBudget,
            )
        ) {
            reduced.removeAt(reduced.lastIndex)
            removedAtSeam = true
        }
    }
    if (reduced.size < 3) return emptyList()
    val firstIndex = pathOperationRotationIndexF32(reduced, candidateWorkBudget)
    return rotatePathOperationVerticesF32(reduced, firstIndex)
}

private fun canonicalProjectedPointF32(point: Point2F32): Point2F32 = Point2F32(
    x = if (point.x == 0f) 0f else point.x,
    y = if (point.y == 0f) 0f else point.y,
)

private fun projectedMiddlePointIsCollinearF32(
    previous: Point2F32,
    current: Point2F32,
    next: Point2F32,
    candidateWorkBudget: PathCandidateWorkBudgetI32,
): Boolean {
    candidateWorkBudget.consume()
    val previousF64 = previous.toPoint2F64()
    val currentF64 = current.toPoint2F64()
    val nextF64 = next.toPoint2F64()
    return OrientationPredicateF64.sign(previousF64, currentF64, nextF64) == 0 &&
        PathPredicatesF64.onSegment(currentF64, previousF64, nextF64)
}

private fun projectedBoundaryEdgesF64(contours: List<ProjectedPathContourF32>): List<ProjectedBoundaryEdgeF64> {
    val result = mutableListOf<ProjectedBoundaryEdgeF64>()
    var nextId = 0
    contours.forEachIndexed { contourIndex, contour ->
        check(
            contour.vertices.size == contour.sourceFirstVertices.size &&
                contour.vertices.size == contour.sourceLastVertices.size,
        )
        contour.vertices.indices.forEach { edgeIndex ->
            val nextIndex = (edgeIndex + 1) % contour.vertices.size
            val edgeId = nextId++
            fun identity(parameter: Double): PathVertexIdentityF64 = PathVertexIdentityF64(
                incidentEdgeIds = listOf(edgeId),
                parameterByEdgeId = mapOf(edgeId to parameter),
                originalPointF32 = null,
            )
            val projected = PathInputEdgeF64(
                id = edgeId,
                operand = PathOperand.FIRST,
                contourIndex = contourIndex,
                startIdentity = identity(0.0),
                endIdentity = identity(1.0),
                start = contour.vertices[edgeIndex].toPoint2F64(),
                end = contour.vertices[nextIndex].toPoint2F64(),
                windingDelta = 1,
            )
            val source = projected.copy(
                start = contour.sourceLastVertices[edgeIndex],
                end = contour.sourceFirstVertices[nextIndex],
            )
            result += ProjectedBoundaryEdgeF64(contourIndex, edgeIndex, projected, source)
        }
    }
    return result
}

private fun projectedBoundaryEdgesAreAdjacentF64(
    first: ProjectedBoundaryEdgeF64,
    second: ProjectedBoundaryEdgeF64,
    contours: List<ProjectedPathContourF32>,
): Boolean {
    if (first.contourIndex != second.contourIndex) return false
    val edgeCount = contours[first.contourIndex].vertices.size
    val difference = kotlin.math.abs(first.edgeIndex - second.edgeIndex)
    return difference == 1 || difference == edgeCount - 1
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
        ExpansionF64.expansionDiff(absoluteArea, doubleArrayOf(projectionCollapseDoubleAreaToleranceF64)),
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

private fun pathOperationRotationIndexF32(vertices: List<Point2F32>): Int = vertices.indices
    .minWithOrNull(
        Comparator { firstIndex, secondIndex ->
            comparePathOperationPointsF32(vertices[firstIndex], vertices[secondIndex])
        },
    ) ?: 0

private fun pathOperationRotationIndexF32(
    vertices: List<Point2F32>,
    candidateWorkBudget: PathCandidateWorkBudgetI32,
): Int {
    var result = 0
    for (index in 1 until vertices.size) {
        candidateWorkBudget.consume()
        if (comparePathOperationPointsF32(vertices[index], vertices[result]) < 0) result = index
    }
    return result
}

private fun rotatePathOperationVerticesF32(vertices: List<Point2F32>, firstIndex: Int): MutableList<Point2F32> {
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
