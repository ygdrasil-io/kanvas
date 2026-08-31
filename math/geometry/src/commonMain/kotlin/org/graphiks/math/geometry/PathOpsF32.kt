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
        val arrangementF64F32 = buildHybridArrangementF64F32(
            inputs = listOf(
                PathOperandInputF32(PathOperand.FIRST, first),
                PathOperandInputF32(PathOperand.SECOND, second),
            ),
            normalization = normalization,
            limits = limits,
            candidateWorkBudget = candidateWorkBudget,
        )
        return writeHybridBoundaryTracesF64F32(
            tracesF64F32 = arrangementF64F32.boundary(first.fillRule, second.fillRule, op),
            fillRule = FillRule.WINDING,
            candidateWorkBudget = candidateWorkBudget,
        )
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
    // Per emitted vertex, the provenance of its outgoing legacy boundary half-edge. This is the
    // only source authority available to a projection decision.
    val legacySectionProvenancesF64: List<List<PathLegacySectionProvenanceF64>>,
    val originalSignedDoubleAreaExpansionF64: DoubleArray,
    val vertices: List<Point2F32>,
    val signedDoubleAreaExpansionF64: DoubleArray,
    val normalizedSignedDoubleAreaExpansionF64: DoubleArray,
)

// Projection makes exactly one atomic decision for every source contour.  No later phase may
// compact an emitted path, or reinterpret a missing contour as permission to keep a partial one.
private sealed interface ProjectedContourDecisionF32 {
    data class Keep(val contour: ProjectedPathContourF32) : ProjectedContourDecisionF32

    data object Drop : ProjectedContourDecisionF32

    data object Reject : ProjectedContourDecisionF32
}

private val projectionAreaToleranceF64: Double = 2.0.pow(-46)
// `signedDoubleAreaExpansionF64` represents twice the signed area. This is exactly
// `2 * projectionAreaToleranceF64`, not the area tolerance itself.
private val projectionCollapseDoubleAreaToleranceF64: Double = 2.0 * projectionAreaToleranceF64

private data class ProjectedBoundaryEdgeF64(
    val contourIndex: Int,
    val edgeIndex: Int,
    val projected: PathInputEdgeF64,
    val legacySectionProvenancesF64: List<PathLegacySectionProvenanceF64>,
)

// A claim is a temporary validation record, not a projected alias or a second topology.  It
// identifies the exact source-span interval consumed by a projected contact so different
// registry witnesses cannot silently claim overlapping interiors before the legacy writer emits.
private data class PathWitnessSpanClaimF64(
    val witnessIdI64: Long,
    val sourceSpanIdI64: Long,
    val startParameterF64: Double,
    val endParameterF64: Double,
)

private data class ProjectedContactProofF64(
    val claimsF64: List<PathWitnessSpanClaimF64>,
)

private class PathWitnessClaimLedgerF64(
    private val candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
) {
    private val claimsF64 = mutableListOf<PathWitnessSpanClaimF64>()

    // Validate a complete projected-contact proof before publishing any of its claims.  The
    // boundary builder has not run yet, so a later rejection cannot expose a partial result.
    fun registerAtomically(newClaimsF64: List<PathWitnessSpanClaimF64>): Boolean {
        newClaimsF64.forEach { newClaimF64 ->
            claimsF64.forEach { existingClaimF64 ->
                candidateWorkBudgetI32.consume()
                if (claimsConflictF64(existingClaimF64, newClaimF64)) return false
            }
        }
        newClaimsF64.indices.forEach { firstIndexI32 ->
            (firstIndexI32 + 1 until newClaimsF64.size).forEach { secondIndexI32 ->
                candidateWorkBudgetI32.consume()
                if (claimsConflictF64(newClaimsF64[firstIndexI32], newClaimsF64[secondIndexI32])) return false
            }
        }
        newClaimsF64.forEach { claimF64 ->
            candidateWorkBudgetI32.consume()
            claimsF64 += claimF64
        }
        return true
    }
}

private fun claimsConflictF64(
    firstF64: PathWitnessSpanClaimF64,
    secondF64: PathWitnessSpanClaimF64,
): Boolean {
    if (
        firstF64.witnessIdI64 == secondF64.witnessIdI64 ||
            firstF64.sourceSpanIdI64 != secondF64.sourceSpanIdI64
    ) {
        return false
    }
    return maxOf(firstF64.startParameterF64, secondF64.startParameterF64) <
        minOf(firstF64.endParameterF64, secondF64.endParameterF64)
}

private fun unaryResultF32(path: PathF32, limits: PathOpsLimitsI32, outputFillRule: FillRule): PathF32 {
    validateFinitePathF32(path)
    val normalization = pathNormalizationF64(listOf(path))
    val candidateWorkBudget = PathCandidateWorkBudgetI32(limits.maxCandidateProbes)
    val arrangementF64F32 = buildHybridArrangementF64F32(
        inputs = listOf(PathOperandInputF32(PathOperand.FIRST, path)),
        normalization = normalization,
        limits = limits,
        candidateWorkBudget = candidateWorkBudget,
    )
    return writeHybridBoundaryTracesF64F32(
        tracesF64F32 = arrangementF64F32.unaryBoundary(path.fillRule),
        fillRule = outputFillRule,
        candidateWorkBudget = candidateWorkBudget,
    )
}

private fun buildHybridArrangementF64F32(
    inputs: List<PathOperandInputF32>,
    normalization: PathNormalizationF64,
    limits: PathOpsLimitsI32,
    candidateWorkBudget: PathCandidateWorkBudgetI32,
): PathArrangementF64F32 {
    try {
        val edgesF64 = inputEdgesF64(inputs, normalization, limits)
        val sourceTopologyF64 = splitPathSourceTopologyF64(edgesF64, limits, candidateWorkBudget)
        val topologyF64F32 = buildPathHybridTopologyF64F32(
            sourceTopologyF64 = sourceTopologyF64,
            normalizationF64 = normalization,
            limitsI32 = limits,
            candidateWorkBudgetI32 = candidateWorkBudget,
        )
        return PathArrangementF64F32.build(topologyF64F32, limits, candidateWorkBudget)
    } catch (error: IllegalStateException) {
        // A hybrid invariant can only fail after an exact source topology was accepted.  The
        // public contract therefore reports the conservative projection outcome rather than an
        // implementation-detail DCEL error.
        if (error.message == "path-arrangement-inconsistent") {
            throw IllegalStateException("path-f32-projection-collapse")
        }
        throw error
    }
}

// A single production fixture entry point for precise F64 source inputs.  It follows exactly
// the public route after flattening: source topology -> hybrid topology -> hybrid DCEL -> writer.
// Tests may construct numerical source edges, but cannot rebuild or inspect any arrangement.
internal fun projectSourceEdgesThroughHybridF64F32(
    edgesF64: List<PathInputEdgeF64>,
    normalizationF64: PathNormalizationF64,
    fillRule: FillRule,
    limitsI32: PathOpsLimitsI32 = PathOpsLimitsI32(),
): PathF32 {
    try {
        val candidateWorkBudgetI32 = PathCandidateWorkBudgetI32(limitsI32.maxCandidateProbes)
        val sourceTopologyF64 = splitPathSourceTopologyF64(edgesF64, limitsI32, candidateWorkBudgetI32)
        val topologyF64F32 = buildPathHybridTopologyF64F32(
            sourceTopologyF64 = sourceTopologyF64,
            normalizationF64 = normalizationF64,
            limitsI32 = limitsI32,
            candidateWorkBudgetI32 = candidateWorkBudgetI32,
        )
        val arrangementF64F32 = PathArrangementF64F32.build(topologyF64F32, limitsI32, candidateWorkBudgetI32)
        return writeHybridBoundaryTracesF64F32(
            tracesF64F32 = arrangementF64F32.unaryBoundary(fillRule),
            fillRule = fillRule,
            candidateWorkBudget = candidateWorkBudgetI32,
        )
    } catch (error: IllegalStateException) {
        if (error.message == "path-arrangement-inconsistent") {
            throw IllegalStateException("path-f32-projection-collapse")
        }
        throw error
    }
}

// Temporary Task-2 writer bridge.  It consumes the already selected hybrid half-edge traces;
// it neither finds contacts nor rewrites a run, so the hybrid DCEL remains the sole authority.
private fun writeHybridBoundaryTracesF64F32(
    tracesF64F32: List<PathBoundaryTraceF64F32>,
    fillRule: FillRule,
    candidateWorkBudget: PathCandidateWorkBudgetI32,
): PathF32 {
    val vertexCountI64 = tracesF64F32.sumOf { traceF64F32 -> traceF64F32.halfEdgesF64F32.size.toLong() }
    // The temporary writer only walks the immutable traces selected by the hybrid DCEL.  Reserve
    // every point/area/builder pass up front from those canonical trace lengths; it must not add
    // traversal-order-dependent debits while it serializes the public PathF32.
    candidateWorkBudget.consumePreflightI64(vertexCountI64 * 5L + tracesF64F32.size.toLong() * 3L)
    val builder = PathBuilder(fillRule)
    tracesF64F32.forEach { traceF64F32 ->
        val halfEdgesF64F32 = traceF64F32.halfEdgesF64F32
        if (halfEdgesF64F32.size < 3) throw IllegalStateException("path-f32-projection-collapse")
        val pointsF32 = halfEdgesF64F32.map { halfEdgeF64F32 ->
            halfEdgeF64F32.originVertexF64F32.representativePointF32
        }
        if (pointsF32.zipWithNext().any { (firstF32, secondF32) -> firstF32.x == secondF32.x && firstF32.y == secondF32.y }) {
            throw IllegalStateException("path-f32-projection-collapse")
        }
        val areaF64 = signedDoubleAreaExpansionF64(pointsF32.map(Point2F32::toPoint2F64) + pointsF32.first().toPoint2F64())
        if (ExpansionF64.sign(areaF64) == 0) throw IllegalStateException("path-f32-projection-collapse")
        builder.moveTo(pointsF32.first().x, pointsF32.first().y)
        pointsF32.drop(1).forEach { pointF32 ->
            builder.lineTo(pointF32.x, pointF32.y)
        }
        builder.close()
    }
    return builder.build()
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
            val points = contour.points
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
            contourVertices.zipWithNext().forEach { (start, end) ->
                if (samePathOperationPointF64(start.point, end.point)) return@forEach
                seeds += PathInputEdgeSeedF64(
                    operand = input.operand,
                    contourIndex = contourIndex,
                    start = start,
                    end = end,
                )
            }
        }
    }

    val parametersByVertexId = mutableMapOf<Int, MutableMap<Int, Double>>()
    vertices.forEach { vertex -> parametersByVertexId.getOrPut(vertex.id) { mutableMapOf() } }
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
            idI32 = edgeId,
            operand = edge.operand,
            contourIndexI32 = edge.contourIndex,
            sourceSegmentIndexI32 = edge.end.sourceSegmentIndexI32,
            // A destination location owns the segment.  Coincident MoveTo/segment starts are
            // intentionally distinct locations: the first flattened section starts at 0.0;
            // so do explicit and implicit closure seams.
            sourceStartParameterF64 = if (
                edge.start.sourceSegmentIndexI32 == edge.end.sourceSegmentIndexI32
            ) edge.start.parameterF64 else 0.0,
            sourceEndParameterF64 = if (edge.end.sourceSegmentIndexI32 == -1) 1.0 else edge.end.parameterF64,
            startIdentityF64 = identitiesByVertexId.getValue(edge.start.id),
            endIdentityF64 = identitiesByVertexId.getValue(edge.end.id),
            startPointF64 = edge.start.point,
            endPointF64 = edge.end.point,
            windingDeltaI32 = 1,
        )
    }
}

internal fun projectContoursF64ToPathF32(
    contours: List<PathContourF64>,
    normalization: PathNormalizationF64,
    fillRule: FillRule,
    candidateWorkBudget: PathCandidateWorkBudgetI32 =
        PathCandidateWorkBudgetI32(PathOpsLimitsI32().maxCandidateProbes),
): PathF32 {
    val uncanonicalProjected = buildList {
        contours.forEach { contour ->
            when (val decisionF32 = projectUncanonicalContourF32(contour, normalization)) {
                is ProjectedContourDecisionF32.Keep -> add(decisionF32.contour)
                ProjectedContourDecisionF32.Drop -> Unit
                ProjectedContourDecisionF32.Reject -> throw IllegalStateException("path-f32-projection-collapse")
            }
        }
    }
    // The old compactor could replace an arbitrary source run with a chord. It is no longer an
    // authority: a trace is kept intact, dropped wholly, or rejected by explicit provenance.
    val projected = buildList {
        uncanonicalProjected.forEach { contour ->
            when (val decisionF32 = projectContourF32(contour, normalization, candidateWorkBudget)) {
                is ProjectedContourDecisionF32.Keep -> add(decisionF32.contour)
                ProjectedContourDecisionF32.Drop -> Unit
                ProjectedContourDecisionF32.Reject -> throw IllegalStateException("path-f32-projection-collapse")
            }
        }
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
): ProjectedContourDecisionF32 {
    if (contour.vertices.isEmpty()) return ProjectedContourDecisionF32.Drop
    val originalPoints = contour.vertices.map { it.point }
    val originalArea = signedDoubleAreaExpansionF64(originalPoints + originalPoints.first())
    val originalSign = ExpansionF64.sign(originalArea)
    if (originalSign == 0) return ProjectedContourDecisionF32.Drop

    var vertices = mutableListOf<Point2F32>()
    var legacySectionProvenancesF64 = mutableListOf<List<PathLegacySectionProvenanceF64>>()
    var firstSourcePointF64: Point2F64? = null
    var lastSourcePointF64: Point2F64? = null
    var hasUnprovenProjectedCollapse = false
    contour.vertices.forEach { vertex ->
        val projected = vertex.originalPointF32 ?: normalization.denormalize(vertex.point)
        if (!projected.isFinite()) return ProjectedContourDecisionF32.Reject
        if (vertices.lastOrNull()?.let { samePathOperationPointF32(it, projected) } != true) {
            vertices += projected
            if (firstSourcePointF64 == null) firstSourcePointF64 = vertex.point
            lastSourcePointF64 = vertex.point
            legacySectionProvenancesF64 += vertex.legacySectionProvenancesF64
        } else {
            if (!samePathOperationPointF64(lastSourcePointF64 ?: vertex.point, vertex.point)) {
                hasUnprovenProjectedCollapse = true
            } else {
                legacySectionProvenancesF64[legacySectionProvenancesF64.lastIndex] =
                    legacySectionProvenancesF64.last() + vertex.legacySectionProvenancesF64
            }
        }
    }
    if (vertices.size > 1 && samePathOperationPointF32(vertices.first(), vertices.last())) {
        if (!samePathOperationPointF64(lastSourcePointF64 ?: contour.vertices.last().point, firstSourcePointF64 ?: contour.vertices.first().point)) {
            hasUnprovenProjectedCollapse = true
        } else {
            legacySectionProvenancesF64[0] =
                legacySectionProvenancesF64.last() + legacySectionProvenancesF64.first()
            vertices.removeAt(vertices.lastIndex)
            legacySectionProvenancesF64.removeAt(legacySectionProvenancesF64.lastIndex)
        }
    }
    if (hasUnprovenProjectedCollapse) return projectionCollapseDecisionF32(originalArea)
    if (vertices.size < 3) return projectionCollapseDecisionF32(originalArea)

    var projectedArea = signedDoubleAreaExpansionF64(vertices.map(Point2F32::toPoint2F64) + vertices.first().toPoint2F64())
    var projectedSign = ExpansionF64.sign(projectedArea)
    if (projectedSign == 0) return projectionCollapseDecisionF32(originalArea)
    if (projectedSign != originalSign) {
        vertices = vertices.asReversed().toMutableList()
        legacySectionProvenancesF64 = MutableList(legacySectionProvenancesF64.size) { indexI32 ->
            val sourceIndexI32 = (legacySectionProvenancesF64.size - 2 - indexI32 + legacySectionProvenancesF64.size) %
                legacySectionProvenancesF64.size
            legacySectionProvenancesF64[sourceIndexI32]
        }
        projectedArea = signedDoubleAreaExpansionF64(vertices.map(Point2F32::toPoint2F64) + vertices.first().toPoint2F64())
        projectedSign = ExpansionF64.sign(projectedArea)
        if (projectedSign != originalSign) return ProjectedContourDecisionF32.Reject
    }

    val normalizedProjectedArea = signedDoubleAreaExpansionF64(
        vertices.map(normalization::normalize) + normalization.normalize(vertices.first()),
    )
    if (ExpansionF64.sign(normalizedProjectedArea) != originalSign) {
        return ProjectedContourDecisionF32.Reject
    }
    return projectedPathContourF32(
        originalSignedDoubleAreaExpansionF64 = originalArea,
        vertices = vertices,
        legacySectionProvenancesF64 = legacySectionProvenancesF64,
        normalization = normalization,
    )
}

private fun projectContourF32(
    contour: ProjectedPathContourF32,
    normalization: PathNormalizationF64,
    candidateWorkBudget: PathCandidateWorkBudgetI32,
): ProjectedContourDecisionF32 {
    val firstIndex = pathOperationRotationIndexF32(contour.vertices, candidateWorkBudget)
    val vertices = rotatePathOperationVerticesF32(contour.vertices, firstIndex)
    val legacySectionProvenancesF64 =
        (contour.legacySectionProvenancesF64.drop(firstIndex) + contour.legacySectionProvenancesF64.take(firstIndex)).toMutableList()
    return projectedPathContourF32(
        originalSignedDoubleAreaExpansionF64 = contour.originalSignedDoubleAreaExpansionF64,
        vertices = vertices,
        legacySectionProvenancesF64 = legacySectionProvenancesF64,
        normalization = normalization,
    )
}

private fun projectedPathContourF32(
    originalSignedDoubleAreaExpansionF64: DoubleArray,
    vertices: List<Point2F32>,
    legacySectionProvenancesF64: List<List<PathLegacySectionProvenanceF64>>,
    normalization: PathNormalizationF64,
): ProjectedContourDecisionF32 {
    if (vertices.size != legacySectionProvenancesF64.size) return ProjectedContourDecisionF32.Reject
    if (vertices.size < 3) {
        return projectionCollapseDecisionF32(originalSignedDoubleAreaExpansionF64)
    }
    val originalSign = ExpansionF64.sign(originalSignedDoubleAreaExpansionF64)
    if (originalSign == 0) return ProjectedContourDecisionF32.Drop
    val projectedArea = signedDoubleAreaExpansionF64(vertices.map(Point2F32::toPoint2F64) + vertices.first().toPoint2F64())
    if (ExpansionF64.sign(projectedArea) != originalSign) return ProjectedContourDecisionF32.Reject
    val normalizedProjectedArea = signedDoubleAreaExpansionF64(
        vertices.map(normalization::normalize) + normalization.normalize(vertices.first()),
    )
    if (ExpansionF64.sign(normalizedProjectedArea) != originalSign) return ProjectedContourDecisionF32.Reject
    return ProjectedContourDecisionF32.Keep(
        ProjectedPathContourF32(
            legacySectionProvenancesF64 = legacySectionProvenancesF64,
            originalSignedDoubleAreaExpansionF64 = originalSignedDoubleAreaExpansionF64,
            vertices = vertices,
            signedDoubleAreaExpansionF64 = projectedArea,
            normalizedSignedDoubleAreaExpansionF64 = normalizedProjectedArea,
        ),
    )
}

private fun validateProjectedContourSetF64(
    contours: List<ProjectedPathContourF32>,
    normalization: PathNormalizationF64,
    candidateWorkBudget: PathCandidateWorkBudgetI32,
) {
    val edges = projectedBoundaryEdgesF64(contours)
    if (edges.size >= 2) {
        // Every selected legacy boundary edge must carry its source-section trace.  An empty
        // trace cannot fall back to a reconstructed source bridge or a coordinate match.
        validateProjectedBoundaryContactsFromProvenanceF64(
            edges,
            contours,
            normalization,
            candidateWorkBudget,
        )
    }
}

private fun validateProjectedBoundaryContactsFromProvenanceF64(
    edges: List<ProjectedBoundaryEdgeF64>,
    contours: List<ProjectedPathContourF32>,
    normalization: PathNormalizationF64,
    candidateWorkBudget: PathCandidateWorkBudgetI32,
) {
    // One projected index is built for this validation pass.  Every candidate debit happens
    // before the predicate or witness scan, and the exact registry witness is the sole proof.
    val claimLedgerF64 = PathWitnessClaimLedgerF64(candidateWorkBudget)
    forEachPathEdgeCandidatePairF64(edges.map(ProjectedBoundaryEdgeF64::projected), candidateWorkBudget) { firstIndex, secondIndex ->
        val firstF64 = edges[firstIndex]
        val secondF64 = edges[secondIndex]
        if (projectedBoundaryEdgesAreAdjacentF64(firstF64, secondF64, contours)) return@forEachPathEdgeCandidatePairF64
        candidateWorkBudget.consume()
        val projectedContactF64 = intersectPathEdgesF64(firstF64.projected, secondF64.projected)
            ?: return@forEachPathEdgeCandidatePairF64
        candidateWorkBudget.consume()
        val proofF64 = projectedContactProofF64(
            firstF64,
            secondF64,
            projectedContactF64,
            normalization,
            candidateWorkBudget,
        )
        if (proofF64 == null || !claimLedgerF64.registerAtomically(proofF64.claimsF64)) {
            throw IllegalStateException("path-f32-projection-collapse")
        }
    }
}

private fun projectedContactProofF64(
    firstF64: ProjectedBoundaryEdgeF64,
    secondF64: ProjectedBoundaryEdgeF64,
    projectedContactF64: PathIntersectionF64,
    normalization: PathNormalizationF64,
    candidateWorkBudget: PathCandidateWorkBudgetI32,
): ProjectedContactProofF64? {
    firstF64.legacySectionProvenancesF64.forEach { firstSectionF64 ->
        firstSectionF64.contactWitnessesF64.forEach { firstWitnessF64 ->
            secondF64.legacySectionProvenancesF64.forEach { secondSectionF64 ->
                secondSectionF64.contactWitnessesF64.forEach { secondWitnessF64 ->
                    candidateWorkBudget.consume()
                    if (firstWitnessF64 != secondWitnessF64) return@forEach
                    when (firstWitnessF64) {
                        is PathContactWitnessF64.PointF64 -> {
                            // A Point witness can support a quantized contact only between two
                            // distinct source spans directly incident at that exact component.
                            // The section trace proves the bounded span interval; no coordinate
                            // lookup, transitive witness, or projected identity participates.
                            candidateWorkBudget.consume()
                            if (
                                projectedContactF64 is PathIntersectionF64.PointF64 &&
                                    isPointWitnessLocalToSectionsF64(firstWitnessF64, firstSectionF64, secondSectionF64)
                            ) {
                                val claimsF64 = pointWitnessClaimsF64(
                                    firstWitnessF64,
                                    firstSectionF64,
                                    secondSectionF64,
                                    candidateWorkBudget,
                                ) ?: return@forEach
                                return ProjectedContactProofF64(claimsF64)
                            }
                        }
                        is PathContactWitnessF64.OverlapF64 -> {
                            val anchorsF32 = overlapWitnessAnchorsF32(
                                firstWitnessF64,
                                firstSectionF64,
                                secondSectionF64,
                                normalization,
                                candidateWorkBudget,
                            )
                            if (anchorsF32.size != 2) return@forEach
                            val startF64 = anchorsF32[0].toPoint2F64()
                            val endF64 = anchorsF32[1].toPoint2F64()
                            val certified = projectedContactLatticeBoundaryPointsF64(projectedContactF64).all { pointF64 ->
                                candidateWorkBudget.consume()
                                PathPredicatesF64.onSegment(pointF64, startF64, endF64)
                            }
                            if (certified) {
                                val claimsF64 = overlapWitnessClaimsF64(
                                    firstWitnessF64,
                                    firstSectionF64,
                                    secondSectionF64,
                                    candidateWorkBudget,
                                ) ?: return@forEach
                                return ProjectedContactProofF64(claimsF64)
                            }
                        }
                    }
                }
            }
        }
    }
    return null
}

private fun isPointWitnessLocalToSectionsF64(
    witnessF64: PathContactWitnessF64.PointF64,
    firstSectionF64: PathLegacySectionProvenanceF64,
    secondSectionF64: PathLegacySectionProvenanceF64,
): Boolean {
    if (firstSectionF64.sourceSpanIdI64 == secondSectionF64.sourceSpanIdI64) return false
    if (
        firstSectionF64.sourceSpanIdI64 !in witnessF64.incidentSourceSpanIdsI64 ||
            secondSectionF64.sourceSpanIdI64 !in witnessF64.incidentSourceSpanIdsI64
    ) return false
    return sectionTouchesWitnessF64(firstSectionF64, witnessF64) &&
        sectionTouchesWitnessF64(secondSectionF64, witnessF64)
}

private fun sectionTouchesWitnessF64(
    sectionF64: PathLegacySectionProvenanceF64,
    witnessF64: PathContactWitnessF64.PointF64,
): Boolean =
    // A source span contains only flattening subdivisions between exact events.  A PointF64
    // witness at either span endpoint can therefore authorize a local F32 point contact across
    // any of its carried sections, but never across a different span or a later exact event.
    sectionF64.sourceSpanStartLocationF64.vertexIdentityF64 == witnessF64.vertexIdentityF64 ||
        sectionF64.sourceSpanEndLocationF64.vertexIdentityF64 == witnessF64.vertexIdentityF64

private fun pointWitnessClaimsF64(
    witnessF64: PathContactWitnessF64.PointF64,
    firstSectionF64: PathLegacySectionProvenanceF64,
    secondSectionF64: PathLegacySectionProvenanceF64,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): List<PathWitnessSpanClaimF64>? {
    val firstClaimF64 = pointWitnessClaimF64(witnessF64, firstSectionF64, candidateWorkBudgetI32) ?: return null
    val secondClaimF64 = pointWitnessClaimF64(witnessF64, secondSectionF64, candidateWorkBudgetI32) ?: return null
    return listOf(firstClaimF64, secondClaimF64)
}

private fun pointWitnessClaimF64(
    witnessF64: PathContactWitnessF64.PointF64,
    sectionF64: PathLegacySectionProvenanceF64,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): PathWitnessSpanClaimF64? {
    candidateWorkBudgetI32.consume()
    val parameterF64 = when (witnessF64.vertexIdentityF64) {
        sectionF64.sourceSpanStartLocationF64.vertexIdentityF64 ->
            sectionF64.sourceSpanStartLocationF64.parameterF64
        sectionF64.sourceSpanEndLocationF64.vertexIdentityF64 ->
            sectionF64.sourceSpanEndLocationF64.parameterF64
        else -> return null
    }
    return PathWitnessSpanClaimF64(
        witnessIdI64 = witnessF64.witnessIdI64,
        sourceSpanIdI64 = sectionF64.sourceSpanIdI64,
        startParameterF64 = parameterF64,
        endParameterF64 = parameterF64,
    )
}

private fun overlapWitnessClaimsF64(
    witnessF64: PathContactWitnessF64.OverlapF64,
    firstSectionF64: PathLegacySectionProvenanceF64,
    secondSectionF64: PathLegacySectionProvenanceF64,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): List<PathWitnessSpanClaimF64>? {
    val firstClaimF64 = overlapWitnessClaimF64(witnessF64, firstSectionF64, candidateWorkBudgetI32) ?: return null
    val secondClaimF64 = overlapWitnessClaimF64(witnessF64, secondSectionF64, candidateWorkBudgetI32) ?: return null
    return listOf(firstClaimF64, secondClaimF64)
}

private fun overlapWitnessClaimF64(
    witnessF64: PathContactWitnessF64.OverlapF64,
    sectionF64: PathLegacySectionProvenanceF64,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): PathWitnessSpanClaimF64? {
    candidateWorkBudgetI32.consume()
    val inFirstI32 = if (sectionF64.sourceSpanIdI64 in witnessF64.firstSourceSpanIdsI64) 1 else 0
    candidateWorkBudgetI32.consume()
    val inSecondI32 = if (sectionF64.sourceSpanIdI64 in witnessF64.secondSourceSpanIdsI64) 1 else 0
    if (inFirstI32 + inSecondI32 != 1) return null
    val witnessStartParameterF64 = if (inFirstI32 == 1) {
        witnessF64.firstStartParameterF64
    } else {
        witnessF64.secondStartParameterF64
    }
    val witnessEndParameterF64 = if (inFirstI32 == 1) {
        witnessF64.firstEndParameterF64
    } else {
        witnessF64.secondEndParameterF64
    }
    candidateWorkBudgetI32.consume()
    val startParameterF64 = maxOf(
        minOf(witnessStartParameterF64, witnessEndParameterF64),
        minOf(
            sectionF64.sourceSpanStartLocationF64.parameterF64,
            sectionF64.sourceSpanEndLocationF64.parameterF64,
        ),
    )
    candidateWorkBudgetI32.consume()
    val endParameterF64 = minOf(
        maxOf(witnessStartParameterF64, witnessEndParameterF64),
        maxOf(
            sectionF64.sourceSpanStartLocationF64.parameterF64,
            sectionF64.sourceSpanEndLocationF64.parameterF64,
        ),
    )
    if (startParameterF64 > endParameterF64) return null
    return PathWitnessSpanClaimF64(
        witnessIdI64 = witnessF64.witnessIdI64,
        sourceSpanIdI64 = sectionF64.sourceSpanIdI64,
        startParameterF64 = startParameterF64,
        endParameterF64 = endParameterF64,
    )
}

private fun overlapWitnessAnchorsF32(
    witnessF64: PathContactWitnessF64.OverlapF64,
    firstSectionF64: PathLegacySectionProvenanceF64,
    secondSectionF64: PathLegacySectionProvenanceF64,
    normalization: PathNormalizationF64,
    candidateWorkBudget: PathCandidateWorkBudgetI32,
): List<Point2F32> {
    val anchorsF32 = mutableListOf<Point2F32>()
    listOf(firstSectionF64, secondSectionF64).forEach { sectionF64 ->
        listOf(
            sectionF64.sourceSectionF64.startIdentityF64 to sectionF64.sourceSectionF64.startPointF64,
            sectionF64.sourceSectionF64.endIdentityF64 to sectionF64.sourceSectionF64.endPointF64,
        ).forEach { (identityF64, pointF64) ->
            candidateWorkBudget.consume()
            if (
                identityF64 != witnessF64.startVertexIdentityF64 &&
                    identityF64 != witnessF64.endVertexIdentityF64
            ) return@forEach
            candidateWorkBudget.consume()
            val anchorF32 = canonicalProjectedPointF32(normalization.denormalize(pointF64))
            if (!anchorF32.isFinite()) throw IllegalStateException("path-f32-projection-collapse")
            if (anchorsF32.none { existingF32 -> samePathOperationPointF32(existingF32, anchorF32) }) anchorsF32 += anchorF32
        }
    }
    return anchorsF32
}

private fun projectedContactBoundaryPointsF64(intersection: PathIntersectionF64): List<Point2F64> = when (intersection) {
    is PathIntersectionF64.PointF64 -> listOf(intersection.point)
    is PathIntersectionF64.OverlapF64 -> listOf(intersection.start, intersection.end)
}

private fun projectedContactLatticeBoundaryPointsF64(intersection: PathIntersectionF64): List<Point2F64> =
    projectedContactBoundaryPointsF64(intersection).map { point -> projectedLatticePointF32(point).toPoint2F64() }

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

private fun projectedBoundaryEdgesF64(contours: List<ProjectedPathContourF32>): List<ProjectedBoundaryEdgeF64> {
    val result = mutableListOf<ProjectedBoundaryEdgeF64>()
    var nextId = 0
    contours.forEachIndexed { contourIndex, contour ->
        if (contour.vertices.size != contour.legacySectionProvenancesF64.size) {
            throw IllegalStateException("path-f32-projection-collapse")
        }
        contour.vertices.indices.forEach { edgeIndex ->
            val nextIndex = (edgeIndex + 1) % contour.vertices.size
            val edgeId = nextId++
            fun identity(parameter: Double): PathVertexIdentityF64 = PathVertexIdentityF64(
                incidentEdgeIds = listOf(edgeId),
                parameterByEdgeId = mapOf(edgeId to parameter),
                originalPointF32 = null,
            )
            val projected = PathInputEdgeF64(
                idI32 = edgeId,
                operand = PathOperand.FIRST,
                contourIndexI32 = contourIndex,
                sourceSegmentIndexI32 = -1,
                sourceStartParameterF64 = 0.0,
                sourceEndParameterF64 = 1.0,
                startIdentityF64 = identity(0.0),
                endIdentityF64 = identity(1.0),
                startPointF64 = contour.vertices[edgeIndex].toPoint2F64(),
                endPointF64 = contour.vertices[nextIndex].toPoint2F64(),
                windingDeltaI32 = 1,
            )
            result += ProjectedBoundaryEdgeF64(
                contourIndex = contourIndex,
                edgeIndex = edgeIndex,
                projected = projected,
                legacySectionProvenancesF64 = contour.legacySectionProvenancesF64[edgeIndex],
            )
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

private fun projectionCollapseDecisionF32(originalArea: DoubleArray): ProjectedContourDecisionF32 =
    if (isTopologicallySignificantAreaF64(originalArea)) ProjectedContourDecisionF32.Reject
    else ProjectedContourDecisionF32.Drop

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
