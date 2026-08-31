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
    val sourceSegmentIndexI32: Int,
    val parameterF64: Double,
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

private sealed interface ProjectedContourResultF32 {
    data class Retained(val contour: ProjectedPathContourF32) : ProjectedContourResultF32

    data object Drop : ProjectedContourResultF32
}

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

private enum class ProjectedSourceContactKindF32 { POINT, OVERLAP }

// This is a direct source witness, never a contour-pair permission. The exact source locus is
// retained beside its F32 image so an F32 contact can only be weakened from that witness, never
// strengthened by a rounded chain of neighbouring contacts.
private data class ProjectedSourceContactAnchorF32(
    val firstContourIndex: Int,
    val firstEdgeIndex: Int,
    val secondContourIndex: Int,
    val secondEdgeIndex: Int,
    val kind: ProjectedSourceContactKindF32,
    val sourcePoints: List<Point2F64>,
    val anchors: List<Point2F32>,
)

private data class ProjectedBoundaryContactF64(
    val first: ProjectedBoundaryEdgeF64,
    val second: ProjectedBoundaryEdgeF64,
    val intersection: PathIntersectionF64,
)

private data class ProjectedContactPreimageF64(
    val vertexIndex: Int,
    val point: Point2F64,
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
                    sourceSegmentIndexI32 = point.sourceSegmentIndexI32,
                    parameterF64 = point.parameterF64,
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
            sourceSegmentIndexI32 = edge.end.sourceSegmentIndexI32,
            sourceStartParameterF64 = edge.start.parameterF64,
            sourceEndParameterF64 = edge.end.parameterF64,
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
    val uncanonicalProjected = contours.mapNotNull { contour ->
        when (val result = projectUncanonicalContourF32(contour, normalization)) {
            is ProjectedContourResultF32.Retained -> result.contour
            ProjectedContourResultF32.Drop -> null
        }
    }
    // TODO(Task 3): delete the late-projection adapter once the hybrid DCEL writes its boundary
    // trace directly.  The legacy compactor remains only for original PathF32 provenance; raw
    // F64 contours have no authority to remove a witness after projection.
    val hasUnsafeSyntheticWitnesses = contours.size > 1 && contours.all { contour ->
        contour.vertices.all { vertex -> vertex.originalPointF32 == null }
    }
    val legacyProjected = if (hasUnsafeSyntheticWitnesses) {
        uncanonicalProjected
    } else {
        val compactionEdges = projectedBoundaryEdgesF64(uncanonicalProjected)
        val pointWitnesses = if (compactionEdges.size < 2) {
            emptyList()
        } else {
            projectedSourceContactAnchorsF64(
                edges = compactionEdges,
                contours = uncanonicalProjected,
                normalization = normalization,
                candidateWorkBudget = candidateWorkBudget,
            )
        }
        compactProjectedPointWitnessRunsF64(
            contours = uncanonicalProjected,
            pointWitnesses = pointWitnesses,
            normalization = normalization,
            candidateWorkBudget = candidateWorkBudget,
        )
    }
    val projected = legacyProjected.mapNotNull { contour ->
        projectContourF32(contour, normalization, candidateWorkBudget)
    }
    if (hasUnsafeSyntheticWitnesses) {
        rejectProjectedRunsThatConsumeDistinctWitnessesF64(projected)
    }
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

private fun projectUncanonicalContourF32(
    contour: PathContourF64,
    normalization: PathNormalizationF64,
): ProjectedContourResultF32 {
    if (contour.vertices.isEmpty()) return ProjectedContourResultF32.Drop
    val originalPoints = contour.vertices.map { it.point }
    val originalArea = signedDoubleAreaExpansionF64(originalPoints + originalPoints.first())
    val originalSign = ExpansionF64.sign(originalArea)
    if (originalSign == 0) return ProjectedContourResultF32.Drop

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
    if (vertices.size < 3) return projectionCollapseOrDropResultF32(originalArea)

    var projectedArea = signedDoubleAreaExpansionF64(vertices.map(Point2F32::toPoint2F64) + vertices.first().toPoint2F64())
    var projectedSign = ExpansionF64.sign(projectedArea)
    if (projectedSign == 0) return projectionCollapseOrDropResultF32(originalArea)
    if (projectedSign != originalSign) {
        vertices = vertices.asReversed().toMutableList()
        val reversedSourceFirstVertices = sourceLastVertices.asReversed().toMutableList()
        sourceLastVertices = sourceFirstVertices.asReversed().toMutableList()
        sourceFirstVertices = reversedSourceFirstVertices
        projectedArea = signedDoubleAreaExpansionF64(vertices.map(Point2F32::toPoint2F64) + vertices.first().toPoint2F64())
        projectedSign = ExpansionF64.sign(projectedArea)
        if (projectedSign != originalSign) throw IllegalStateException("path-f32-projection-collapse")
    }

    val normalizedProjectedArea = signedDoubleAreaExpansionF64(
        vertices.map(normalization::normalize) + normalization.normalize(vertices.first()),
    )
    if (ExpansionF64.sign(normalizedProjectedArea) != originalSign) {
        throw IllegalStateException("path-f32-projection-collapse")
    }
    val result = projectedPathContourF32(
        originalSignedDoubleAreaExpansionF64 = originalArea,
        vertices = vertices,
        sourceFirstVertices = sourceFirstVertices,
        sourceLastVertices = sourceLastVertices,
        normalization = normalization,
    ) ?: return projectionCollapseOrDropResultF32(originalArea)
    return ProjectedContourResultF32.Retained(result)
}

private fun projectContourF32(
    contour: ProjectedPathContourF32,
    normalization: PathNormalizationF64,
    candidateWorkBudget: PathCandidateWorkBudgetI32,
): ProjectedPathContourF32? {
    val firstIndex = pathOperationRotationIndexF32(contour.vertices, candidateWorkBudget)
    val vertices = rotatePathOperationVerticesF32(contour.vertices, firstIndex)
    val sourceFirstVertices =
        (contour.sourceFirstVertices.drop(firstIndex) + contour.sourceFirstVertices.take(firstIndex)).toMutableList()
    val sourceLastVertices =
        (contour.sourceLastVertices.drop(firstIndex) + contour.sourceLastVertices.take(firstIndex)).toMutableList()
    return projectedPathContourF32(
        originalSignedDoubleAreaExpansionF64 = contour.originalSignedDoubleAreaExpansionF64,
        vertices = vertices,
        sourceFirstVertices = sourceFirstVertices,
        sourceLastVertices = sourceLastVertices,
        normalization = normalization,
    )
}

// A source Point may round a locally curved neighbourhood onto a lattice carrier shared with a
// tangent contour. Compact only one precomputed non-wrapping collinear run into its own known
// source witness before contact validation. Every source bridge in that run must turn relative
// to the carrier. The replacement bridge has that witness as an endpoint, so any retained
// projected Point has a direct source preimage; no pair-wide permission, contact-graph walk,
// seam wrap, witness aggregation, or Point-to-Overlap reclassification is involved.
private fun compactProjectedPointWitnessRunsF64(
    contours: List<ProjectedPathContourF32>,
    pointWitnesses: List<ProjectedSourceContactAnchorF32>,
    normalization: PathNormalizationF64,
    candidateWorkBudget: PathCandidateWorkBudgetI32,
): List<ProjectedPathContourF32> {
    val witnessesByContour = linkedMapOf<Int, MutableList<Point2F64>>()
    pointWitnesses.forEach { witness ->
        if (witness.kind != ProjectedSourceContactKindF32.POINT || witness.sourcePoints.size != 1) return@forEach
        listOf(witness.firstContourIndex, witness.secondContourIndex).forEach { contourIndex ->
            val witnesses = witnessesByContour.getOrPut(contourIndex) { mutableListOf() }
            val sourcePoint = witness.sourcePoints.single()
            var alreadyPresent = false
            witnesses.forEach { existing ->
                candidateWorkBudget.consume()
                if (samePathOperationPointF64(existing, sourcePoint)) alreadyPresent = true
            }
            if (!alreadyPresent) {
                candidateWorkBudget.consume()
                witnesses += sourcePoint
            }
        }
    }
    return contours.mapIndexed { contourIndex, contour ->
        val witnesses = witnessesByContour[contourIndex] ?: return@mapIndexed contour
        compactProjectedPointWitnessRunsF64(contour, witnesses, normalization, candidateWorkBudget)
    }
}

private fun compactProjectedPointWitnessRunsF64(
    contour: ProjectedPathContourF32,
    witnesses: List<Point2F64>,
    normalization: PathNormalizationF64,
    candidateWorkBudget: PathCandidateWorkBudgetI32,
): ProjectedPathContourF32 {
    val vertices = contour.vertices.toMutableList()
    val sourceFirstVertices = contour.sourceFirstVertices.toMutableList()
    val sourceLastVertices = contour.sourceLastVertices.toMutableList()
    val removals = linkedSetOf<Int>()

    witnesses.forEach { sourcePoint ->
        val witnessIndex = sourceFirstVertices.indices.firstOrNull { index ->
            candidateWorkBudget.consume()
            samePathOperationPointF64(sourceFirstVertices[index], sourcePoint) &&
                samePathOperationPointF64(sourceLastVertices[index], sourcePoint)
        } ?: return@forEach

        listOf(-1, 1).forEach { direction ->
            val runEnd = projectionOnlyWitnessRunEndF64(
                vertices = vertices,
                sourceFirstVertices = sourceFirstVertices,
                sourceLastVertices = sourceLastVertices,
                witnessIndex = witnessIndex,
                direction = direction,
                normalization = normalization,
                candidateWorkBudget = candidateWorkBudget,
            ) ?: return@forEach
            var index = witnessIndex + direction
            while (index != runEnd + direction) {
                candidateWorkBudget.consume()
                removals += index
                index += direction
            }
        }
    }

    if (removals.isEmpty()) return contour
    removals.sortedDescending().forEach { index ->
        vertices.removeAt(index)
        sourceFirstVertices.removeAt(index)
        sourceLastVertices.removeAt(index)
    }
    return checkNotNull(
        projectedPathContourF32(
            originalSignedDoubleAreaExpansionF64 = contour.originalSignedDoubleAreaExpansionF64,
            vertices = vertices,
            sourceFirstVertices = sourceFirstVertices,
            sourceLastVertices = sourceLastVertices,
            normalization = normalization,
        ),
    )
}

private fun projectionOnlyWitnessRunEndF64(
    vertices: List<Point2F32>,
    sourceFirstVertices: List<Point2F64>,
    sourceLastVertices: List<Point2F64>,
    witnessIndex: Int,
    direction: Int,
    normalization: PathNormalizationF64,
    candidateWorkBudget: PathCandidateWorkBudgetI32,
): Int? {
    val firstIndex = witnessIndex + direction
    val secondIndex = firstIndex + direction
    if (firstIndex !in vertices.indices || secondIndex !in vertices.indices) return null
    val witness = vertices[witnessIndex].toPoint2F64()
    val first = vertices[firstIndex].toPoint2F64()
    candidateWorkBudget.consume()
    if (samePathOperationPointF64(witness, first)) return null
    candidateWorkBudget.consume()
    if (OrientationPredicateF64.sign(witness, first, vertices[secondIndex].toPoint2F64()) != 0) return null

    val sourceWitness = sourceFirstVertices[witnessIndex]
    val sourceFirst = sourcePointOnWitnessBranchF64(sourceFirstVertices, sourceLastVertices, firstIndex, direction)
    val sourceSecond = sourcePointOnWitnessBranchF64(sourceFirstVertices, sourceLastVertices, secondIndex, direction)
    candidateWorkBudget.consume()
    if (samePathOperationPointF64(sourceWitness, sourceFirst)) return null
    candidateWorkBudget.consume()
    if (OrientationPredicateF64.sign(sourceWitness, sourceFirst, sourceSecond) == 0) return null

    var runEnd = firstIndex
    var index = secondIndex
    while (index in vertices.indices) {
        candidateWorkBudget.consume()
        if (OrientationPredicateF64.sign(witness, first, vertices[index].toPoint2F64()) != 0) break
        runEnd = index
        index += direction
    }
    if (
        !sourceWitnessRunBridgesAreNonParallelToProjectedCarrierF64(
            vertices = vertices,
            sourceFirstVertices = sourceFirstVertices,
            sourceLastVertices = sourceLastVertices,
            witnessIndex = witnessIndex,
            runEnd = runEnd,
            direction = direction,
            normalization = normalization,
            candidateWorkBudget = candidateWorkBudget,
        )
    ) {
        return null
    }
    return runEnd
}

// A source rail that merely sits one F32 ULP-cell away from the witness is not a curved tangent
// artefact.  Every source bridge in a compacted run must therefore turn relative to the exact
// F32 carrier through the witness.  The carrier is mapped back through the shared normalization,
// so this uses an exact robust direction predicate rather than an absolute tolerance.
private fun sourceWitnessRunBridgesAreNonParallelToProjectedCarrierF64(
    vertices: List<Point2F32>,
    sourceFirstVertices: List<Point2F64>,
    sourceLastVertices: List<Point2F64>,
    witnessIndex: Int,
    runEnd: Int,
    direction: Int,
    normalization: PathNormalizationF64,
    candidateWorkBudget: PathCandidateWorkBudgetI32,
): Boolean {
    val carrierStart = normalization.normalize(vertices[witnessIndex])
    val carrierEnd = normalization.normalize(vertices[witnessIndex + direction])
    var previousIndex = witnessIndex
    while (previousIndex != runEnd) {
        val nextIndex = previousIndex + direction
        val sourceStart = if (direction > 0) sourceLastVertices[previousIndex] else sourceLastVertices[nextIndex]
        val sourceEnd = if (direction > 0) sourceFirstVertices[nextIndex] else sourceFirstVertices[previousIndex]
        val sourceDirectionEnd = Point2F64(
            x = carrierStart.x + (sourceEnd.x - sourceStart.x),
            y = carrierStart.y + (sourceEnd.y - sourceStart.y),
        )
        candidateWorkBudget.consume()
        if (OrientationPredicateF64.sign(carrierStart, carrierEnd, sourceDirectionEnd) == 0) return false
        previousIndex = nextIndex
    }
    return true
}

private fun sourcePointOnWitnessBranchF64(
    sourceFirstVertices: List<Point2F64>,
    sourceLastVertices: List<Point2F64>,
    index: Int,
    direction: Int,
): Point2F64 = if (direction < 0) sourceLastVertices[index] else sourceFirstVertices[index]

private fun projectedPathContourF32(
    originalSignedDoubleAreaExpansionF64: DoubleArray,
    vertices: List<Point2F32>,
    sourceFirstVertices: List<Point2F64>,
    sourceLastVertices: List<Point2F64>,
    normalization: PathNormalizationF64,
): ProjectedPathContourF32? {
    check(vertices.size == sourceFirstVertices.size && vertices.size == sourceLastVertices.size)
    if (vertices.size < 3) {
        if (isTopologicallySignificantAreaF64(originalSignedDoubleAreaExpansionF64)) {
            throw IllegalStateException("path-f32-projection-collapse")
        }
        return null
    }
    val originalSign = ExpansionF64.sign(originalSignedDoubleAreaExpansionF64)
    if (originalSign == 0) return null
    val projectedArea = signedDoubleAreaExpansionF64(vertices.map(Point2F32::toPoint2F64) + vertices.first().toPoint2F64())
    if (ExpansionF64.sign(projectedArea) != originalSign) throw IllegalStateException("path-f32-projection-collapse")
    val normalizedProjectedArea = signedDoubleAreaExpansionF64(
        vertices.map(normalization::normalize) + normalization.normalize(vertices.first()),
    )
    if (ExpansionF64.sign(normalizedProjectedArea) != originalSign) {
        throw IllegalStateException("path-f32-projection-collapse")
    }
    return ProjectedPathContourF32(
        sourceFirstVertices = sourceFirstVertices,
        sourceLastVertices = sourceLastVertices,
        originalSignedDoubleAreaExpansionF64 = originalSignedDoubleAreaExpansionF64,
        vertices = vertices,
        signedDoubleAreaExpansionF64 = projectedArea,
        normalizedSignedDoubleAreaExpansionF64 = normalizedProjectedArea,
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
        val sourceContactAnchors = projectedSourceContactAnchorsF64(
            edges,
            contours,
            normalization,
            candidateWorkBudget,
        )
        validateProjectedBoundaryContactsF64(
            edges,
            contours,
            cycleKeys,
            sourceContactAnchors,
            normalization,
            candidateWorkBudget,
        )
    }
    // This pass is deliberately independent of the edge-pair walk: a group can be significant
    // only after all of its coincident cycles are aggregated, even when each pair is below the
    // promised area tolerance.
    validateProjectedCycleGroupsF64(contours, cycleKeys, normalization, candidateWorkBudget)
}

// A projected vertex can cover a run of several source vertices.  A later compaction used to
// replace such a run with a chord, which could silently consume two exact Point witnesses.  The
// transitional writer cannot represent that claim safely, so reject it before validation.  A
// genuinely dropped contour never reaches this check.
private fun rejectProjectedRunsThatConsumeDistinctWitnessesF64(
    contours: List<ProjectedPathContourF32>,
) {
    val edges = projectedBoundaryEdgesF64(contours)
    if (edges.size < 2) return
    val witnessesByContour = mutableMapOf<Int, MutableSet<Point2F64>>()
    edges.indices.forEach { firstIndex ->
        for (secondIndex in firstIndex + 1 until edges.size) {
            val first = edges[firstIndex]
            val second = edges[secondIndex]
            if (first.contourIndex == second.contourIndex) continue
            val sourceWitness = intersectPathEdgesF64(first.source, second.source) as? PathIntersectionF64.PointF64 ?: continue
            if (intersectPathEdgesF64(first.projected, second.projected) == null) continue
            witnessesByContour.getOrPut(first.contourIndex) { linkedSetOf() } += sourceWitness.point
            witnessesByContour.getOrPut(second.contourIndex) { linkedSetOf() } += sourceWitness.point
        }
    }
    contours.forEachIndexed { contourIndex, contour ->
        val contourWitnesses = witnessesByContour[contourIndex].orEmpty()
        contour.vertices.indices.forEach { vertexIndex ->
            val previous = contour.vertices[(vertexIndex + contour.vertices.size - 1) % contour.vertices.size].toPoint2F64()
            val current = contour.vertices[vertexIndex].toPoint2F64()
            val next = contour.vertices[(vertexIndex + 1) % contour.vertices.size].toPoint2F64()
            if (
                contourWitnesses.size > 1 &&
                    OrientationPredicateF64.sign(previous, current, next) == 0 &&
                    PathPredicatesF64.onSegment(current, previous, next)
            ) {
                throw IllegalStateException("path-f32-projection-collapse")
            }
        }
    }
}

private fun validateProjectedBoundaryContactsF64(
    edges: List<ProjectedBoundaryEdgeF64>,
    contours: List<ProjectedPathContourF32>,
    cycleKeys: List<ProjectedCycleKeyF32>,
    sourceContactAnchors: List<ProjectedSourceContactAnchorF32>,
    normalization: PathNormalizationF64,
    candidateWorkBudget: PathCandidateWorkBudgetI32,
): Unit {
    var accumulatedLocalLoss = doubleArrayOf()
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
        val sourceBacksProjectedContact = sourceIntersectionBacksProjectedContactF64(
            sourceIntersection,
            projectedIntersection,
            normalization,
            candidateWorkBudget,
        )
        val normalizedProjectedRelation = if (sourceBacksProjectedContact) {
            projectedIntersection
        } else {
            normalizeProjectedContactWithLocalSourceWitnessF64(
                first,
                second,
                projectedIntersection,
                edges,
                sourceContactAnchors,
                candidateWorkBudget,
            )
        }
        if (normalizedProjectedRelation != null) {
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

        // A new same-contour contact can erase a narrow face while an unrelated distant rounding
        // change cancels the contour's signed total area.  Measure only the local loop/strip
        // identified by this contact and add absolute losses, never a globally cancellable sum.
        val localLoss = projectedContactLocalLossF64(
            first,
            second,
            projectedIntersection,
            contours[first.contourIndex],
            candidateWorkBudget,
        ) ?: throw IllegalStateException("path-f32-projection-collapse")
        candidateWorkBudget.consume()
        accumulatedLocalLoss = ExpansionF64.expansionSum(accumulatedLocalLoss, localLoss)
        if (isTopologicallySignificantAreaF64(accumulatedLocalLoss)) {
            throw IllegalStateException("path-f32-projection-collapse")
        }
    }
    if (isTopologicallySignificantAreaF64(accumulatedLocalLoss)) {
        throw IllegalStateException("path-f32-projection-collapse")
    }
}

private fun projectedSourceContactAnchorsF64(
    edges: List<ProjectedBoundaryEdgeF64>,
    contours: List<ProjectedPathContourF32>,
    normalization: PathNormalizationF64,
    candidateWorkBudget: PathCandidateWorkBudgetI32,
): List<ProjectedSourceContactAnchorF32> {
    val anchors = linkedSetOf<ProjectedSourceContactAnchorF32>()
    forEachPathEdgeCandidatePairF64(edges.map(ProjectedBoundaryEdgeF64::projected), candidateWorkBudget) { firstIndex, secondIndex ->
        val first = edges[firstIndex]
        val second = edges[secondIndex]
        if (projectedBoundaryEdgesAreAdjacentF64(first, second, contours)) return@forEachPathEdgeCandidatePairF64
        if (first.contourIndex == second.contourIndex) return@forEachPathEdgeCandidatePairF64
        candidateWorkBudget.consume()
        val sourceIntersection = intersectPathEdgesF64(first.source, second.source)
            ?: return@forEachPathEdgeCandidatePairF64
        candidateWorkBudget.consume()
        intersectPathEdgesF64(first.projected, second.projected)
            ?: return@forEachPathEdgeCandidatePairF64
        val anchor = projectedSourceContactAnchorF32(first, second, sourceIntersection, normalization, candidateWorkBudget)
            ?: return@forEachPathEdgeCandidatePairF64
        candidateWorkBudget.consume()
        anchors += anchor
    }
    return anchors.toList()
}

private fun projectedSourceContactAnchorF32(
    first: ProjectedBoundaryEdgeF64,
    second: ProjectedBoundaryEdgeF64,
    sourceIntersection: PathIntersectionF64,
    normalization: PathNormalizationF64,
    candidateWorkBudget: PathCandidateWorkBudgetI32,
): ProjectedSourceContactAnchorF32? {
    if (first.contourIndex == second.contourIndex) return null
    val sourcePoints = when (sourceIntersection) {
        is PathIntersectionF64.PointF64 -> listOf(sourceIntersection.point)
        is PathIntersectionF64.OverlapF64 -> listOf(sourceIntersection.start, sourceIntersection.end)
    }
    val anchors = sourcePoints.map { point ->
        candidateWorkBudget.consume()
        val projected = canonicalProjectedPointF32(normalization.denormalize(point))
        if (!projected.isFinite()) throw IllegalStateException("path-f32-projection-collapse")
        projected
    }.distinct()
    if (anchors.isEmpty()) return null
    val kind = when (sourceIntersection) {
        is PathIntersectionF64.PointF64 -> ProjectedSourceContactKindF32.POINT
        is PathIntersectionF64.OverlapF64 -> ProjectedSourceContactKindF32.OVERLAP
    }
    return if (first.contourIndex < second.contourIndex) {
        ProjectedSourceContactAnchorF32(
            first.contourIndex,
            first.edgeIndex,
            second.contourIndex,
            second.edgeIndex,
            kind,
            sourcePoints,
            anchors,
        )
    } else {
        ProjectedSourceContactAnchorF32(
            second.contourIndex,
            second.edgeIndex,
            first.contourIndex,
            first.edgeIndex,
            kind,
            sourcePoints,
            anchors,
        )
    }
}

private fun normalizeProjectedContactWithLocalSourceWitnessF64(
    first: ProjectedBoundaryEdgeF64,
    second: ProjectedBoundaryEdgeF64,
    projectedIntersection: PathIntersectionF64,
    edges: List<ProjectedBoundaryEdgeF64>,
    sourceContactAnchors: List<ProjectedSourceContactAnchorF32>,
    candidateWorkBudget: PathCandidateWorkBudgetI32,
): PathIntersectionF64? {
    if (first.contourIndex == second.contourIndex) return null
    val candidate = canonicalProjectedBoundaryContactF64(first, second, projectedIntersection)
    for (anchor in sourceContactAnchors) {
        candidateWorkBudget.consume()
        if (
            anchor.firstContourIndex != candidate.first.contourIndex ||
                anchor.secondContourIndex != candidate.second.contourIndex
        ) {
            continue
        }
        when (projectedIntersection) {
            is PathIntersectionF64.PointF64 -> {
                if (
                    anchor.kind == ProjectedSourceContactKindF32.OVERLAP &&
                        sourceOverlapWitnessBacksProjectedContactF64(candidate, anchor, candidateWorkBudget)
                ) {
                    return projectedIntersection
                }
                if (
                    anchor.kind == ProjectedSourceContactKindF32.POINT &&
                        sourcePointWitnessBacksDirectProjectedPointF64(candidate, anchor, edges, candidateWorkBudget)
                ) {
                    return projectedIntersection
                }
            }
            is PathIntersectionF64.OverlapF64 -> {
                // The relation partial order is geometric as well as classificatory: a source
                // Point cannot retain an F32 interval under a different relation name.  The
                // raw interval remains in the emitted boundary, so only an exact source
                // Overlap can certify it.
                if (
                    anchor.kind == ProjectedSourceContactKindF32.OVERLAP &&
                        sourceOverlapWitnessBacksProjectedContactF64(candidate, anchor, candidateWorkBudget)
                ) {
                    return projectedIntersection
                }
            }
        }
    }
    return null
}

private fun canonicalProjectedBoundaryContactF64(
    first: ProjectedBoundaryEdgeF64,
    second: ProjectedBoundaryEdgeF64,
    intersection: PathIntersectionF64,
): ProjectedBoundaryContactF64 = if (first.contourIndex < second.contourIndex) {
    ProjectedBoundaryContactF64(first, second, intersection)
} else {
    ProjectedBoundaryContactF64(second, first, intersection)
}

private fun sourceOverlapWitnessBacksProjectedContactF64(
    candidate: ProjectedBoundaryContactF64,
    anchor: ProjectedSourceContactAnchorF32,
    candidateWorkBudget: PathCandidateWorkBudgetI32,
): Boolean {
    if (anchor.kind != ProjectedSourceContactKindF32.OVERLAP || anchor.sourcePoints.size != 2 || anchor.anchors.size != 2) {
        return false
    }
    val sourceStart = anchor.sourcePoints[0]
    val sourceEnd = anchor.sourcePoints[1]
    val projectedStart = canonicalProjectedPointF32(anchor.anchors[0]).toPoint2F64()
    val projectedEnd = canonicalProjectedPointF32(anchor.anchors[1]).toPoint2F64()
    return projectedContactLatticeBoundaryPointsF64(candidate.intersection).all { point ->
        candidateWorkBudget.consume()
        PathPredicatesF64.onSegment(point, projectedStart, projectedEnd)
    } &&
        sourceBridgeFollowsSourceOverlapCarrierF64(candidate.first.source, sourceStart, sourceEnd, candidateWorkBudget) &&
        sourceBridgeFollowsSourceOverlapCarrierF64(candidate.second.source, sourceStart, sourceEnd, candidateWorkBudget)
}

private fun sourceBridgeFollowsSourceOverlapCarrierF64(
    bridge: PathInputEdgeF64,
    sourceStart: Point2F64,
    sourceEnd: Point2F64,
    candidateWorkBudget: PathCandidateWorkBudgetI32,
): Boolean {
    for (point in listOf(bridge.start, bridge.end)) {
        candidateWorkBudget.consume()
        if (OrientationPredicateF64.sign(sourceStart, sourceEnd, point) != 0) return false
        candidateWorkBudget.consume()
        if (!PathPredicatesF64.onSegment(point, sourceStart, sourceEnd)) return false
    }
    return true
}

private fun sourcePointWitnessBacksDirectProjectedPointF64(
    candidate: ProjectedBoundaryContactF64,
    anchor: ProjectedSourceContactAnchorF32,
    edges: List<ProjectedBoundaryEdgeF64>,
    candidateWorkBudget: PathCandidateWorkBudgetI32,
): Boolean {
    if (anchor.kind != ProjectedSourceContactKindF32.POINT || anchor.sourcePoints.size != 1 || anchor.anchors.size != 1) {
        return false
    }
    val anchorFirst = projectedBoundaryEdgeAtSourceAnchorF64(candidate.first, anchor.firstEdgeIndex, edges)
    val anchorSecond = projectedBoundaryEdgeAtSourceAnchorF64(candidate.second, anchor.secondEdgeIndex, edges)
    val sourcePoint = anchor.sourcePoints.single()
    return sourcePointIsOnBridgeF64(candidate.first.source, sourcePoint, candidateWorkBudget) &&
        sourcePointIsOnBridgeF64(candidate.second.source, sourcePoint, candidateWorkBudget) &&
        sourcePointIsOnBridgeF64(anchorFirst.source, sourcePoint, candidateWorkBudget) &&
        sourcePointIsOnBridgeF64(anchorSecond.source, sourcePoint, candidateWorkBudget) &&
        candidateBridgeIsWitnessOrImmediateMergedContinuationF64(candidate.first, anchorFirst, candidateWorkBudget) &&
        candidateBridgeIsWitnessOrImmediateMergedContinuationF64(candidate.second, anchorSecond, candidateWorkBudget) &&
        projectedIntersectionContainsSourceAnchorF64(candidate.intersection, anchor.anchors, candidateWorkBudget)
}

private fun projectedBoundaryEdgeAtSourceAnchorF64(
    candidate: ProjectedBoundaryEdgeF64,
    anchorEdgeIndex: Int,
    edges: List<ProjectedBoundaryEdgeF64>,
): ProjectedBoundaryEdgeF64 {
    val contourFirstEdgeId = candidate.projected.id - candidate.edgeIndex
    val anchor = edges[contourFirstEdgeId + anchorEdgeIndex]
    check(anchor.contourIndex == candidate.contourIndex && anchor.edgeIndex == anchorEdgeIndex)
    return anchor
}

private fun sourcePointIsOnBridgeF64(
    bridge: PathInputEdgeF64,
    sourcePoint: Point2F64,
    candidateWorkBudget: PathCandidateWorkBudgetI32,
): Boolean {
    candidateWorkBudget.consume()
    if (OrientationPredicateF64.sign(bridge.start, bridge.end, sourcePoint) != 0) return false
    candidateWorkBudget.consume()
    return PathPredicatesF64.onSegment(sourcePoint, bridge.start, bridge.end)
}

// No path walk is permitted here.  A non-anchor bridge can be a continuation only when it is
// immediately adjacent in the already projected contour order, never through the cyclic seam.
// Its source bridge has already been proved to contain the exact source witness above.
private fun candidateBridgeIsWitnessOrImmediateMergedContinuationF64(
    candidate: ProjectedBoundaryEdgeF64,
    witness: ProjectedBoundaryEdgeF64,
    candidateWorkBudget: PathCandidateWorkBudgetI32,
): Boolean {
    candidateWorkBudget.consume()
    if (candidate.edgeIndex == witness.edgeIndex) return true
    candidateWorkBudget.consume()
    return candidate.edgeIndex + 1 == witness.edgeIndex || witness.edgeIndex + 1 == candidate.edgeIndex
}

private fun projectedContactBoundaryPointsF64(intersection: PathIntersectionF64): List<Point2F64> = when (intersection) {
    is PathIntersectionF64.PointF64 -> listOf(intersection.point)
    is PathIntersectionF64.OverlapF64 -> listOf(intersection.start, intersection.end)
}

private fun projectedContactLatticeBoundaryPointsF64(intersection: PathIntersectionF64): List<Point2F64> =
    projectedContactBoundaryPointsF64(intersection).map { point -> projectedLatticePointF32(point).toPoint2F64() }

private fun projectedIntersectionContainsSourceAnchorF64(
    projectedIntersection: PathIntersectionF64,
    anchors: List<Point2F32>,
    candidateWorkBudget: PathCandidateWorkBudgetI32,
): Boolean = anchors.any { anchor ->
    candidateWorkBudget.consume()
    val point = canonicalProjectedPointF32(anchor).toPoint2F64()
    when (projectedIntersection) {
        is PathIntersectionF64.PointF64 -> samePathOperationPointF64(projectedLatticePointF32(projectedIntersection.point).toPoint2F64(), point)
        is PathIntersectionF64.OverlapF64 -> {
            val endpoints = projectedContactLatticeBoundaryPointsF64(projectedIntersection)
            PathPredicatesF64.onSegment(point, endpoints.first(), endpoints.last())
        }
    }
}

private fun sourceIntersectionBacksProjectedContactF64(
    source: PathIntersectionF64?,
    projected: PathIntersectionF64,
    normalization: PathNormalizationF64,
    candidateWorkBudget: PathCandidateWorkBudgetI32,
): Boolean = when (source) {
    null -> false
    is PathIntersectionF64.PointF64 -> {
        if (projected !is PathIntersectionF64.PointF64) return false
        candidateWorkBudget.consume()
        val anchor = canonicalProjectedPointF32(normalization.denormalize(source.point))
        if (!anchor.isFinite()) throw IllegalStateException("path-f32-projection-collapse")
        projectedIntersectionContainsSourceAnchorF64(projected, listOf(anchor), candidateWorkBudget)
    }
    is PathIntersectionF64.OverlapF64 -> {
        candidateWorkBudget.consume()
        val start = canonicalProjectedPointF32(normalization.denormalize(source.start))
        candidateWorkBudget.consume()
        val end = canonicalProjectedPointF32(normalization.denormalize(source.end))
        if (!start.isFinite() || !end.isFinite()) throw IllegalStateException("path-f32-projection-collapse")
        val sourceLocusStart = start.toPoint2F64()
        val sourceLocusEnd = end.toPoint2F64()
        projectedContactLatticeBoundaryPointsF64(projected).all { point ->
            candidateWorkBudget.consume()
            PathPredicatesF64.onSegment(point, sourceLocusStart, sourceLocusEnd)
        }
    }
}

private fun projectedContactLocalLossF64(
    first: ProjectedBoundaryEdgeF64,
    second: ProjectedBoundaryEdgeF64,
    projectedIntersection: PathIntersectionF64,
    contour: ProjectedPathContourF32,
    candidateWorkBudget: PathCandidateWorkBudgetI32,
): DoubleArray? = when (projectedIntersection) {
    is PathIntersectionF64.PointF64 -> projectedPointContactLocalLossF64(
        first,
        second,
        projectedIntersection.point,
        contour,
        candidateWorkBudget,
    )
    is PathIntersectionF64.OverlapF64 -> projectedOverlapContactLocalLossF64(
        first,
        second,
        projectedIntersection,
        contour,
        candidateWorkBudget,
    )
}

private fun projectedPointContactLocalLossF64(
    first: ProjectedBoundaryEdgeF64,
    second: ProjectedBoundaryEdgeF64,
    projectedPoint: Point2F64,
    contour: ProjectedPathContourF32,
    candidateWorkBudget: PathCandidateWorkBudgetI32,
): DoubleArray? {
    val firstPreimage = projectedContactPreimageF64(first, projectedPoint, contour) ?: return null
    val secondPreimage = projectedContactPreimageF64(second, projectedPoint, contour) ?: return null
    if (firstPreimage.vertexIndex == secondPreimage.vertexIndex) return doubleArrayOf()
    val vertices = exactSourceContourVerticesF64(contour, candidateWorkBudget) ?: return null
    val firstArc = sourceContourArcF64(vertices, firstPreimage.vertexIndex, secondPreimage.vertexIndex, candidateWorkBudget)
    val secondArc = sourceContourArcF64(vertices, secondPreimage.vertexIndex, firstPreimage.vertexIndex, candidateWorkBudget)
    val firstArea = absolutePathOperationExpansionF64(signedDoubleAreaExpansionF64(firstArc + firstArc.first()))
    val secondArea = absolutePathOperationExpansionF64(signedDoubleAreaExpansionF64(secondArc + secondArc.first()))
    // The projected identification closes both source arcs at the new point.  Keep both
    // non-negative loop losses: selecting the smaller one would let a distant opposite-area
    // change conceal the significant complementary loop that the F32 contact also identifies.
    candidateWorkBudget.consume()
    return ExpansionF64.expansionSum(firstArea, secondArea)
}

private fun projectedOverlapContactLocalLossF64(
    first: ProjectedBoundaryEdgeF64,
    second: ProjectedBoundaryEdgeF64,
    projectedOverlap: PathIntersectionF64.OverlapF64,
    contour: ProjectedPathContourF32,
    candidateWorkBudget: PathCandidateWorkBudgetI32,
): DoubleArray? {
    val firstStart = projectedContactPreimageF64(first, projectedOverlap.start, contour) ?: return null
    val firstEnd = projectedContactPreimageF64(first, projectedOverlap.end, contour) ?: return null
    val secondStart = projectedContactPreimageF64(second, projectedOverlap.start, contour) ?: return null
    val secondEnd = projectedContactPreimageF64(second, projectedOverlap.end, contour) ?: return null
    val strip = listOf(firstStart.point, firstEnd.point, secondEnd.point, secondStart.point)
    candidateWorkBudget.consume()
    return absolutePathOperationExpansionF64(signedDoubleAreaExpansionF64(strip + strip.first()))
}

private fun projectedContactPreimageF64(
    edge: ProjectedBoundaryEdgeF64,
    projectedPoint: Point2F64,
    contour: ProjectedPathContourF32,
): ProjectedContactPreimageF64? {
    val edgeCount = contour.vertices.size
    val startIndex = edge.edgeIndex
    val endIndex = (edge.edgeIndex + 1) % edgeCount
    if (
        samePathOperationPointF64(edge.projected.start, projectedPoint) &&
            samePathOperationPointF64(contour.sourceFirstVertices[startIndex], contour.sourceLastVertices[startIndex])
    ) {
        return ProjectedContactPreimageF64(startIndex, edge.source.start)
    }
    if (
        samePathOperationPointF64(edge.projected.end, projectedPoint) &&
            samePathOperationPointF64(contour.sourceFirstVertices[endIndex], contour.sourceLastVertices[endIndex])
    ) {
        return ProjectedContactPreimageF64(endIndex, edge.source.end)
    }
    return null
}

private fun exactSourceContourVerticesF64(
    contour: ProjectedPathContourF32,
    candidateWorkBudget: PathCandidateWorkBudgetI32,
): List<Point2F64>? {
    contour.sourceFirstVertices.indices.forEach { index ->
        candidateWorkBudget.consume()
        if (!samePathOperationPointF64(contour.sourceFirstVertices[index], contour.sourceLastVertices[index])) {
            return null
        }
    }
    return contour.sourceFirstVertices
}

private fun sourceContourArcF64(
    vertices: List<Point2F64>,
    startIndex: Int,
    endIndex: Int,
    candidateWorkBudget: PathCandidateWorkBudgetI32,
): List<Point2F64> {
    val result = mutableListOf<Point2F64>()
    var index = startIndex
    do {
        candidateWorkBudget.consume()
        result += vertices[index]
        index = (index + 1) % vertices.size
    } while (index != (endIndex + 1) % vertices.size)
    return result
}

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

private fun projectedLatticePointF32(point: Point2F32): Point2F32 = Point2F32(
    x = Float.fromBits(point.x.toRawBits()),
    y = Float.fromBits(point.y.toRawBits()),
)

private fun projectedLatticePointF32(point: Point2F64): Point2F32 =
    projectedLatticePointF32(Point2F32(point.x.toFloat(), point.y.toFloat()))

private fun canonicalProjectedPointF32(point: Point2F32): Point2F32 {
    val latticePoint = projectedLatticePointF32(point)
    return Point2F32(
        x = if (latticePoint.x == 0f) 0f else latticePoint.x,
        y = if (latticePoint.y == 0f) 0f else latticePoint.y,
    )
}

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

private fun projectionCollapseOrDropResultF32(originalArea: DoubleArray): ProjectedContourResultF32 {
    if (isTopologicallySignificantAreaF64(originalArea)) {
        throw IllegalStateException("path-f32-projection-collapse")
    }
    return ProjectedContourResultF32.Drop
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

internal fun pathOperationRotationIndexF32(
    vertices: List<Point2F32>,
    candidateWorkBudget: PathCandidateWorkBudgetI32,
): Int {
    val canonicalCost = canonicalBoothCandidateCostI64(vertices.size)
    // Proof of the `3n` bound used for canonical debit:
    // - `first` and `second` never move backwards. A mismatch ends one phase and advances one
    //   of them by `offset + 1` (or farther when it must step past the other candidate).
    // - A phase therefore performs exactly `offset + 1` comparisons and is covered by that
    //   monotone candidate advance. Across all mismatch-ended phases, the two candidates can
    //   advance fewer than `2n` positions before one leaves the range.
    // - The only remaining phase is an equality suffix. Its `offset` rises from zero to at most
    //   `n`, then terminates the loop. It performs at most `n` comparisons.
    // Thus no execution compares more than `3n` points. Compute the bound with `Long` so an
    // `Int`-sized contour cannot overflow. Check the complete canonical debit before the first
    // comparison so cyclic rotations/reversals cannot partially scan then diverge at a budget
    // boundary. This is not a reservation: every real comparison below still debits immediately
    // before it runs, then only the proven unused remainder is consumed as canonical padding.
    candidateWorkBudget.requireRemainingAtLeast(canonicalCost)
    var chargedComparisons = 0L
    val result = minimalPathOperationRotationIndexF32(vertices) { first, second ->
        candidateWorkBudget.consume()
        chargedComparisons += 1L
        comparePathOperationPointsF32(first, second)
    }
    while (chargedComparisons < canonicalCost) {
        candidateWorkBudget.consume()
        chargedComparisons += 1L
    }
    return result
}

private fun canonicalBoothCandidateCostI64(size: Int): Long = if (size < 2) 0L else 3L * size.toLong()

private fun minimalPathOperationRotationIndexF32(
    vertices: List<Point2F32>,
    compare: (Point2F32, Point2F32) -> Int,
): Int {
    if (vertices.size < 2) return 0
    var first = 0
    var second = 1
    var offset = 0
    val size = vertices.size
    while (first < size && second < size && offset < size) {
        val comparison = compare(
            vertices[pathOperationCyclicIndexF32(first, offset, size)],
            vertices[pathOperationCyclicIndexF32(second, offset, size)],
        )
        when {
            comparison == 0 -> offset += 1
            comparison > 0 -> {
                first = pathOperationAdvanceIndexF32(first, offset, size)
                if (first == second) first = pathOperationAdvanceIndexF32(first, 0, size)
                offset = 0
            }
            else -> {
                second = pathOperationAdvanceIndexF32(second, offset, size)
                if (first == second) second = pathOperationAdvanceIndexF32(second, 0, size)
                offset = 0
            }
        }
    }
    return minOf(first, second).coerceAtMost(size - 1)
}

private fun pathOperationCyclicIndexF32(index: Int, offset: Int, size: Int): Int =
    ((index.toLong() + offset.toLong()) % size.toLong()).toInt()

private fun pathOperationAdvanceIndexF32(index: Int, offset: Int, size: Int): Int =
    (index.toLong() + offset.toLong() + 1L).coerceAtMost(size.toLong()).toInt()

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
