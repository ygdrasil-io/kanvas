package org.graphiks.math.geometry

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal enum class PathOperand { FIRST, SECOND }

internal data class PathVertexIdentityF64(
    val incidentEdgeIds: List<Int>,
    val parameterByEdgeId: Map<Int, Double>,
    val originalPointF32: Point2F32?,
)

internal data class PathInputEdgeF64(
    val id: Int,
    val operand: PathOperand,
    val contourIndex: Int,
    val startIdentity: PathVertexIdentityF64,
    val endIdentity: PathVertexIdentityF64,
    val start: Point2F64,
    val end: Point2F64,
    val windingDelta: Int,
)

// `Point2F64` intentionally keeps its generated bitwise equality: public value/provenance users
// can still distinguish signed-zero payloads where that matters. Path topology instead has one
// geometric zero. Canonicalize only the F64 geometry carried through predicates, cuts and emitted
// split points; retain the original vertex identities so untouched F32 provenance is not rewritten.
private fun canonicalTopologicalCoordinateF64(value: Double): Double = if (value == 0.0) 0.0 else value

private fun canonicalTopologicalPointF64(point: Point2F64): Point2F64 = Point2F64(
    x = canonicalTopologicalCoordinateF64(point.x),
    y = canonicalTopologicalCoordinateF64(point.y),
)

private fun sameTopologicalPointF64(first: Point2F64, second: Point2F64): Boolean =
    first.x == second.x && first.y == second.y

// One operation owns this work budget across its broad phase and intersection registry.  Every
// candidate-facing action consumes before it executes, so a dense AABB workload cannot evade the
// same deterministic `path-candidate-limit` that bounds the registry's profile work.
internal class PathCandidateWorkBudgetI32(maxCandidateProbes: Int) {
    private var remaining: Int = maxCandidateProbes

    init {
        require(maxCandidateProbes > 0)
    }

    fun consume() {
        if (remaining <= 0) throw IllegalStateException("path-candidate-limit")
        remaining -= 1
    }
}

private fun canonicalTopologicalPathInputEdgeF64(edge: PathInputEdgeF64): PathInputEdgeF64 = edge.copy(
    start = canonicalTopologicalPointF64(edge.start),
    end = canonicalTopologicalPointF64(edge.end),
)

internal sealed interface PathIntersectionF64 {
    data class PointF64(
        val point: Point2F64,
        val firstT: Double,
        val secondT: Double,
    ) : PathIntersectionF64

    data class OverlapF64(
        val start: Point2F64,
        val end: Point2F64,
        val firstRange: ClosedFloatingPointRange<Double>,
        val secondRange: ClosedFloatingPointRange<Double>,
        val firstStartParameter: Double,
        val firstEndParameter: Double,
        val secondStartParameter: Double,
        val secondEndParameter: Double,
    ) : PathIntersectionF64
}

internal data class PathSplitEdgeF64(
    val sourceId: Int,
    val operand: PathOperand,
    val startIdentity: PathVertexIdentityF64,
    val endIdentity: PathVertexIdentityF64,
    val start: Point2F64,
    val end: Point2F64,
    val windingDelta: Int,
)

internal fun intersectPathEdgesF64(firstInput: PathInputEdgeF64, secondInput: PathInputEdgeF64): PathIntersectionF64? {
    // The topology treats both IEEE signed zero encodings as the same coordinate. Keep the
    // endpoint identities themselves untouched for F32 provenance, but perform every geometric
    // decision and emit every F64 point from this canonical geometry.
    val first = canonicalTopologicalPathInputEdgeF64(firstInput)
    val second = canonicalTopologicalPathInputEdgeF64(secondInput)
    if (!first.start.isFinite() || !first.end.isFinite() || !second.start.isFinite() || !second.end.isFinite()) return null

    val firstDegenerate = sameTopologicalPointF64(first.start, first.end)
    val secondDegenerate = sameTopologicalPointF64(second.start, second.end)
    if (firstDegenerate && secondDegenerate) {
        return if (sameTopologicalPointF64(first.start, second.start)) {
            PathIntersectionF64.PointF64(canonicalTopologicalPointF64(first.start), 0.0, 0.0)
        } else {
            null
        }
    }
    if (firstDegenerate) {
        return if (PathPredicatesF64.onSegment(first.start, second.start, second.end)) {
            PathIntersectionF64.PointF64(canonicalTopologicalPointF64(first.start), 0.0, parameterAtPointF64(first.start, second))
        } else {
            null
        }
    }
    if (secondDegenerate) {
        return if (PathPredicatesF64.onSegment(second.start, first.start, first.end)) {
            PathIntersectionF64.PointF64(canonicalTopologicalPointF64(second.start), parameterAtPointF64(second.start, first), 0.0)
        } else {
            null
        }
    }

    val firstStartSide = OrientationPredicateF64.sign(first.start, first.end, second.start)
    val firstEndSide = OrientationPredicateF64.sign(first.start, first.end, second.end)
    val secondStartSide = OrientationPredicateF64.sign(second.start, second.end, first.start)
    val secondEndSide = OrientationPredicateF64.sign(second.start, second.end, first.end)

    if (firstStartSide == 0 && firstEndSide == 0 && secondStartSide == 0 && secondEndSide == 0) {
        return intersectCollinearPathEdgesF64(first, second)
    }

    fun endpointIntersection(point: Point2F64, firstT: Double, secondT: Double): PathIntersectionF64.PointF64 =
        PathIntersectionF64.PointF64(canonicalTopologicalPointF64(point), snapParameterF64(firstT), snapParameterF64(secondT))

    if (firstStartSide == 0 && PathPredicatesF64.onSegment(second.start, first.start, first.end)) {
        return endpointIntersection(second.start, parameterAtPointF64(second.start, first), 0.0)
    }
    if (firstEndSide == 0 && PathPredicatesF64.onSegment(second.end, first.start, first.end)) {
        return endpointIntersection(second.end, parameterAtPointF64(second.end, first), 1.0)
    }
    if (secondStartSide == 0 && PathPredicatesF64.onSegment(first.start, second.start, second.end)) {
        return endpointIntersection(first.start, 0.0, parameterAtPointF64(first.start, second))
    }
    if (secondEndSide == 0 && PathPredicatesF64.onSegment(first.end, second.start, second.end)) {
        return endpointIntersection(first.end, 1.0, parameterAtPointF64(first.end, second))
    }

    if (firstStartSide * firstEndSide >= 0 || secondStartSide * secondEndSide >= 0) return null

    val firstDeltaX = first.end.x - first.start.x
    val firstDeltaY = first.end.y - first.start.y
    val secondDeltaX = second.end.x - second.start.x
    val secondDeltaY = second.end.y - second.start.y
    val betweenX = second.start.x - first.start.x
    val betweenY = second.start.y - first.start.y
    val denominator = crossF64(firstDeltaX, firstDeltaY, secondDeltaX, secondDeltaY)
    if (denominator == 0.0 || !denominator.isFinite()) return null

    val firstT = snapParameterF64(crossF64(betweenX, betweenY, secondDeltaX, secondDeltaY) / denominator)
    val secondT = snapParameterF64(crossF64(betweenX, betweenY, firstDeltaX, firstDeltaY) / denominator)
    return PathIntersectionF64.PointF64(pointAtPathParameterF64(first, firstT), firstT, secondT)
}

internal fun splitPathEdgesF64(edges: List<PathInputEdgeF64>, limits: PathOpsLimitsI32): List<PathSplitEdgeF64> =
    splitPathEdgesF64(edges, limits, PathCandidateWorkBudgetI32(limits.maxCandidateProbes))

internal fun splitPathEdgesF64(
    edges: List<PathInputEdgeF64>,
    limits: PathOpsLimitsI32,
    candidateWorkBudget: PathCandidateWorkBudgetI32,
): List<PathSplitEdgeF64> {
    validatePathInputEdgesF64(edges)
    val canonicalEdges = canonicalPathInputEdgesF64(edges).map(::canonicalTopologicalPathInputEdgeF64)
    val maximumSplitEdges = limits.maxHalfEdges / 2
    val baseSplitEdges = canonicalEdges.count { edge -> !sameTopologicalPointF64(edge.start, edge.end) }
    if (baseSplitEdges > maximumSplitEdges) throw IllegalStateException("path-half-edge-limit")
    // Storage invariant: an interior incidence is a canonical cut, never a pair event, and can
    // add at most one split edge. Reserving only the final split-edge slack therefore bounds all
    // persistent registry state without charging repeated pair incidences to maxHalfEdges.
    val registry = PathIntersectionRegistryF64(
        edges = canonicalEdges,
        maxInteriorCuts = maximumSplitEdges - baseSplitEdges,
        candidateWorkBudget = candidateWorkBudget,
    )
    forEachPathEdgeCandidatePairF64(canonicalEdges, candidateWorkBudget) { firstIndex, secondIndex ->
        val first = canonicalEdges[firstIndex]
        val second = canonicalEdges[secondIndex]
        // The broad phase emits the retained second indices in this exact canonical order.
        // It rejects only AABB gaps that cannot reach the kernel's endpoint-snap policy;
        // endpoint/tangent/overlap contacts continue through the robust kernel unchanged.
        if (pathEdgesShareOnlyKnownNonCollinearEndpointF64(first, second, candidateWorkBudget)) {
            registry.consumeKnownEndpointNoOpCandidateWorkF64()
            return@forEachPathEdgeCandidatePairF64
        }
        candidateWorkBudget.consume()
        when (val intersection = intersectPathEdgesF64(first, second)) {
            is PathIntersectionF64.PointF64 -> registry.addIntersection(
                firstIndex,
                intersection.firstT,
                secondIndex,
                intersection.secondT,
                intersection.point,
                hasUniqueCarrierIntersection = true,
            )
            is PathIntersectionF64.OverlapF64 -> {
                registry.addIntersection(
                    firstIndex,
                    intersection.firstStartParameter,
                    secondIndex,
                    intersection.secondStartParameter,
                    intersection.start,
                    hasUniqueCarrierIntersection = false,
                )
                registry.addIntersection(
                    firstIndex,
                    intersection.firstEndParameter,
                    secondIndex,
                    intersection.secondEndParameter,
                    intersection.end,
                    hasUniqueCarrierIntersection = false,
                )
            }
            null -> Unit
        }
    }
    val components = registry.components
    if (components.size > limits.maxIntersections) throw IllegalStateException("path-intersection-limit")

    val identityByComponent = components.associateWith { component ->
        pathIntersectionIdentityF64(component, canonicalEdges)
    }
    val cutsByEdge = canonicalEdges.indices.map { edgeIndex ->
        mutableListOf(
            PathEdgeCutF64(0.0, canonicalEdges[edgeIndex].startIdentity, false),
            PathEdgeCutF64(1.0, canonicalEdges[edgeIndex].endIdentity, false),
        )
    }
    components.forEach { component ->
        val identity = identityByComponent.getValue(component)
        component.incidencesByEdge.forEach { (edgeIndex, incidence) ->
            cutsByEdge[edgeIndex] += PathEdgeCutF64(
                parameter = incidence.parameter,
                identity = identity,
                isIntersection = true,
                canonicalPoint = component.canonicalPoint,
            )
        }
    }

    val canonicalCutsByEdge = cutsByEdge.map(::canonicalPathCutsF64)
    var splitEdgeCount = 0
    canonicalEdges.forEachIndexed { edgeIndex, edge ->
        canonicalCutsByEdge[edgeIndex].zipWithNext().forEach { (startCut, endCut) ->
            val start = pointAtPathCutF64(edge, startCut)
            val end = pointAtPathCutF64(edge, endCut)
            if (!sameTopologicalPointF64(start, end)) {
                // Compare in split-edge units before incrementing: this is exactly the final
                // 2 * splitEdges.size half-edge budget without an overflowing multiplication.
                if (splitEdgeCount >= maximumSplitEdges) throw IllegalStateException("path-half-edge-limit")
                splitEdgeCount += 1
            }
        }
    }

    return ArrayList<PathSplitEdgeF64>(splitEdgeCount).also { splitEdges ->
        canonicalEdges.forEachIndexed { edgeIndex, edge ->
            canonicalCutsByEdge[edgeIndex].zipWithNext().forEach { (startCut, endCut) ->
                val start = pointAtPathCutF64(edge, startCut)
                val end = pointAtPathCutF64(edge, endCut)
                if (sameTopologicalPointF64(start, end)) return@forEach
                splitEdges +=
                    PathSplitEdgeF64(
                        sourceId = edge.id,
                        operand = edge.operand,
                        startIdentity = startCut.identity,
                        endIdentity = endCut.identity,
                        start = start,
                        end = end,
                        windingDelta = edge.windingDelta,
                    )
            }
        }
    }
}

private data class PathEdgeAabbF64(
    val minimumX: Double,
    val maximumX: Double,
    val minimumY: Double,
    val maximumY: Double,
    val maximumSpan: Double,
)

private data class PathAabbBoundsF64(
    val minimumX: Double,
    val maximumX: Double,
    val minimumY: Double,
    val maximumY: Double,
    val maximumSpan: Double,
)

private data class PathEndpointRelationF64(
    val firstPoint: Point2F64,
    val firstIdentity: PathVertexIdentityF64,
    val secondPoint: Point2F64,
    val secondIdentity: PathVertexIdentityF64,
)

private fun pathEdgesShareOnlyKnownNonCollinearEndpointF64(
    first: PathInputEdgeF64,
    second: PathInputEdgeF64,
    candidateWorkBudget: PathCandidateWorkBudgetI32,
): Boolean {
    // Candidate emission merely hands this pair to the topology stage. Debit its
    // preclassification separately before allocating endpoint relations, filtering them, or
    // invoking an exact orientation predicate; a normal kernel classification has its own debit
    // below, while the proven no-op subsequently pays its two incoming incidences.
    candidateWorkBudget.consume()
    if (!first.start.isFinite() || !first.end.isFinite() || !second.start.isFinite() || !second.end.isFinite()) {
        return false
    }
    if (sameTopologicalPointF64(first.start, first.end) || sameTopologicalPointF64(second.start, second.end)) {
        return false
    }
    val concreteEndpointRelations = listOf(
        PathEndpointRelationF64(first.start, first.startIdentity, second.start, second.startIdentity),
        PathEndpointRelationF64(first.start, first.startIdentity, second.end, second.endIdentity),
        PathEndpointRelationF64(first.end, first.endIdentity, second.start, second.startIdentity),
        PathEndpointRelationF64(first.end, first.endIdentity, second.end, second.endIdentity),
    ).filter { relation -> sameTopologicalPointF64(relation.firstPoint, relation.secondPoint) }
    if (concreteEndpointRelations.size != 1) return false
    val relation = concreteEndpointRelations.single()
    if (relation.firstIdentity != relation.secondIdentity) return false
    return OrientationPredicateF64.sign(first.start, first.end, second.start) != 0 ||
        OrientationPredicateF64.sign(first.start, first.end, second.end) != 0
}

private class PathEdgeAabbIndexF64(edges: List<PathInputEdgeF64>) {
    private class Node(
        val fromIndex: Int,
        val untilIndex: Int,
        val bounds: PathAabbBoundsF64?,
        val hasNonFiniteEdge: Boolean,
        val left: Node? = null,
        val right: Node? = null,
    )

    private val boxesByEdgeIndex: List<PathEdgeAabbF64?> = edges.map(::pathEdgeAabbF64)
    private val root: Node? = buildNodeF64(0, boxesByEdgeIndex.size)

    fun forEachCandidateIndexAfter(
        firstIndex: Int,
        candidateWorkBudget: PathCandidateWorkBudgetI32,
        action: (Int) -> Unit,
    ) {
        if (firstIndex + 1 >= boxesByEdgeIndex.size) return
        val first = boxesByEdgeIndex[firstIndex]
        if (first == null) {
            for (secondIndex in firstIndex + 1 until boxesByEdgeIndex.size) {
                candidateWorkBudget.consume()
                action(secondIndex)
            }
            return
        }
        visitCandidateNodeF64(root, firstIndex, first, candidateWorkBudget, action)
    }

    private fun buildNodeF64(fromIndex: Int, untilIndex: Int): Node? {
        if (fromIndex >= untilIndex) return null
        if (untilIndex - fromIndex == 1) {
            val box = boxesByEdgeIndex[fromIndex]
            return Node(
                fromIndex = fromIndex,
                untilIndex = untilIndex,
                bounds = box?.toPathAabbBoundsF64(),
                hasNonFiniteEdge = box == null,
            )
        }
        val middleIndex = (fromIndex + untilIndex) ushr 1
        val left = checkNotNull(buildNodeF64(fromIndex, middleIndex))
        val right = checkNotNull(buildNodeF64(middleIndex, untilIndex))
        return Node(
            fromIndex = fromIndex,
            untilIndex = untilIndex,
            bounds = unionPathAabbBoundsF64(left.bounds, right.bounds),
            hasNonFiniteEdge = left.hasNonFiniteEdge || right.hasNonFiniteEdge,
            left = left,
            right = right,
        )
    }

    private fun visitCandidateNodeF64(
        node: Node?,
        firstIndex: Int,
        query: PathEdgeAabbF64,
        candidateWorkBudget: PathCandidateWorkBudgetI32,
        action: (Int) -> Unit,
    ) {
        node ?: return
        candidateWorkBudget.consume()
        if (node.untilIndex <= firstIndex + 1) return
        node.bounds?.let { bounds ->
            candidateWorkBudget.consume()
            if (!node.hasNonFiniteEdge && pathAabbGapIsProvablyDisjointF64(query, bounds)) return
        }
        if (node.left == null && node.right == null) {
            val secondIndex = node.fromIndex
            if (secondIndex <= firstIndex) return
            val second = boxesByEdgeIndex[secondIndex]
            if (second != null) {
                candidateWorkBudget.consume()
                if (pathAabbGapIsProvablyDisjointF64(query, second.toPathAabbBoundsF64())) return
            }
            candidateWorkBudget.consume()
            action(secondIndex)
            return
        }
        visitCandidateNodeF64(node.left, firstIndex, query, candidateWorkBudget, action)
        visitCandidateNodeF64(node.right, firstIndex, query, candidateWorkBudget, action)
    }
}

// This is the only broad-phase pair enumerator used by topology consumers.  Its index is built
// over canonical semantic positions and its left-to-right traversal emits the same `i, j` order
// as the former nested loop after conservative culling, without materializing or sorting pairs.
internal fun forEachPathEdgeCandidatePairF64(
    edges: List<PathInputEdgeF64>,
    candidateWorkBudget: PathCandidateWorkBudgetI32,
    action: (firstIndex: Int, secondIndex: Int) -> Unit,
) {
    val broadPhase = PathEdgeAabbIndexF64(edges)
    edges.indices.forEach { firstIndex ->
        broadPhase.forEachCandidateIndexAfter(firstIndex, candidateWorkBudget) { secondIndex ->
            action(firstIndex, secondIndex)
        }
    }
}

private fun pathEdgeAabbF64(edge: PathInputEdgeF64): PathEdgeAabbF64? {
    if (!edge.start.isFinite() || !edge.end.isFinite()) return null
    val startX = canonicalTopologicalCoordinateF64(edge.start.x)
    val startY = canonicalTopologicalCoordinateF64(edge.start.y)
    val endX = canonicalTopologicalCoordinateF64(edge.end.x)
    val endY = canonicalTopologicalCoordinateF64(edge.end.y)
    return PathEdgeAabbF64(
        minimumX = min(startX, endX),
        maximumX = max(startX, endX),
        minimumY = min(startY, endY),
        maximumY = max(startY, endY),
        maximumSpan = max(abs(endX - startX), abs(endY - startY)),
    )
}

private fun PathEdgeAabbF64.toPathAabbBoundsF64(): PathAabbBoundsF64 = PathAabbBoundsF64(
    minimumX = minimumX,
    maximumX = maximumX,
    minimumY = minimumY,
    maximumY = maximumY,
    maximumSpan = maximumSpan,
)

private fun unionPathAabbBoundsF64(first: PathAabbBoundsF64?, second: PathAabbBoundsF64?): PathAabbBoundsF64? = when {
    first == null -> second
    second == null -> first
    else -> PathAabbBoundsF64(
        minimumX = min(first.minimumX, second.minimumX),
        maximumX = max(first.maximumX, second.maximumX),
        minimumY = min(first.minimumY, second.minimumY),
        maximumY = max(first.maximumY, second.maximumY),
        maximumSpan = max(first.maximumSpan, second.maximumSpan),
    )
}

// `intersectPathEdgesF64` snaps a parameter to one only through
// `samePathParameterF64`.  A strict box gap is therefore cullable only when that gap cannot
// become such an endpoint parameter on either carrier.  The subtree's maximum L-infinity span
// is intentionally conservative: it may retain false positives, but never filters a contact the
// robust kernel can canonicalize.
private fun pathAabbGapIsProvablyDisjointF64(first: PathEdgeAabbF64, second: PathAabbBoundsF64): Boolean =
    pathAabbAxisGapIsProvablyDisjointF64(
        first.minimumX,
        first.maximumX,
        second.minimumX,
        second.maximumX,
        max(first.maximumSpan, second.maximumSpan),
    ) || pathAabbAxisGapIsProvablyDisjointF64(
        first.minimumY,
        first.maximumY,
        second.minimumY,
        second.maximumY,
        max(first.maximumSpan, second.maximumSpan),
    )

private fun pathAabbAxisGapIsProvablyDisjointF64(
    firstMinimum: Double,
    firstMaximum: Double,
    secondMinimum: Double,
    secondMaximum: Double,
    maximumCarrierSpan: Double,
): Boolean {
    val gap = when {
        firstMaximum < secondMinimum -> secondMinimum - firstMaximum
        secondMaximum < firstMinimum -> firstMinimum - secondMaximum
        else -> return false
    }
    return !pathAabbGapMaySnapToEndpointF64(gap, maximumCarrierSpan)
}

private fun pathAabbGapMaySnapToEndpointF64(gap: Double, maximumCarrierSpan: Double): Boolean {
    // Overflow while forming a finite-coordinate carrier span is not a proof of separation.
    // Keep that pair for the robust kernel rather than inventing a finite absolute tolerance.
    if (gap <= 0.0 || !gap.isFinite() || !maximumCarrierSpan.isFinite() || maximumCarrierSpan <= 0.0) return true
    val parameter = 1.0 + gap / maximumCarrierSpan
    return parameter.isFinite() && samePathParameterF64(1.0, parameter)
}

private fun validatePathInputEdgesF64(edges: List<PathInputEdgeF64>) {
    val edgeIds = mutableSetOf<Int>()
    edges.forEach { edge ->
        if (!edgeIds.add(edge.id)) throw IllegalArgumentException("path-edge-id-duplicate")
    }
    edges.forEach { edge ->
        if (!hasEndpointIdentityF64(edge.startIdentity, edge.id, 0.0)) {
            throw IllegalArgumentException("path-edge-start-identity")
        }
        if (!hasEndpointIdentityF64(edge.endIdentity, edge.id, 1.0)) {
            throw IllegalArgumentException("path-edge-end-identity")
        }
    }
}

private fun canonicalPathInputEdgesF64(edges: List<PathInputEdgeF64>): List<PathInputEdgeF64> =
    edges.sortedWith(Comparator(::comparePathInputEdgesSemanticallyF64))

private fun hasEndpointIdentityF64(identity: PathVertexIdentityF64, edgeId: Int, parameter: Double): Boolean =
    edgeId in identity.incidentEdgeIds && identity.parameterByEdgeId[edgeId]?.let { samePathParameterF64(it, parameter) } == true

private data class PathEdgeCutF64(
    val parameter: Double,
    val identity: PathVertexIdentityF64,
    val isIntersection: Boolean,
    val canonicalPoint: Point2F64? = null,
)

private data class PathComponentIncidenceF64(
    val parameter: Double,
    val minimumParameter: Double = parameter,
    val maximumParameter: Double = parameter,
)

private data class PathComponentProfileF64(
    var exactWitness: PathExactIntersectionWitnessF64?,
    var canonicalPoint: Point2F64,
    val incidencesByEdge: MutableMap<Int, PathComponentIncidenceF64>,
)

private fun mergeDirectPathIncidencesF64(
    first: PathComponentIncidenceF64,
    second: PathComponentIncidenceF64,
): PathComponentIncidenceF64? {
    val minimumParameter = min(first.minimumParameter, second.minimumParameter)
    val maximumParameter = max(first.maximumParameter, second.maximumParameter)
    if (!samePathParameterF64(minimumParameter, maximumParameter)) return null
    return PathComponentIncidenceF64(
        parameter = canonicalParameterF64(listOf(minimumParameter, maximumParameter)),
        minimumParameter = minimumParameter,
        maximumParameter = maximumParameter,
    )
}

private fun mergeDirectPathIncidenceF64(
    incidence: PathComponentIncidenceF64,
    parameter: Double,
): PathComponentIncidenceF64? = mergeDirectPathIncidencesF64(incidence, PathComponentIncidenceF64(parameter))

private class PathIntersectionComponentF64(
    val id: Int,
    var orderKey: PathComponentOrderKeyF64,
    var exactWitness: PathExactIntersectionWitnessF64?,
    var canonicalPoint: Point2F64,
) {
    val incidencesByEdge = mutableMapOf<Int, PathComponentIncidenceF64>()
    // Persistent per-component marking deduplicates the fixed candidate streams without an
    // O(b_j) rejected-candidate set. It is reset only after the (physically unreachable) Long
    // epoch wrap, so no source ID participates in this decision.
    var lastCandidateEpoch: Long = Long.MIN_VALUE
}

private data class PathParameterIntervalKeyF64(
    val minimumBits: Long,
    val width: Int,
)

private class PathEdgeComponentIndexF64 {
    private val componentsByInterval = PathParameterIntervalIndexF64()

    // A direct incidence stores an exact ordered-ULP interval with width strictly below sixteen.
    // A query opens the fixed 31 * 16 signature neighbourhood around its parameter. Each outer
    // AVL lookup is logarithmic and each occupied signature yields an ordered cursor over every
    // live member; no representative is allowed to hide a compatible component. The registry
    // merges these fixed streams and defers mutation until all cursors are exhausted.
    fun cursors(parameter: Double): List<PathComponentCursorF64> {
        val key = orderedPathParameterBitsF64(parameter)
        return buildList {
            for (minimumOffset in -15L..15L) {
                for (width in 0..15) {
                    componentsByInterval.cursor(PathParameterIntervalKeyF64(key + minimumOffset, width))?.let(::add)
                }
            }
        }
    }

    fun add(component: PathIntersectionComponentF64, incidence: PathComponentIncidenceF64) {
        componentsByInterval.add(pathParameterIntervalKeyF64(incidence), component)
    }

    fun remove(component: PathIntersectionComponentF64, incidence: PathComponentIncidenceF64) {
        componentsByInterval.remove(pathParameterIntervalKeyF64(incidence), component)
    }
}

private fun orderedPathParameterBitsF64(parameter: Double): Long {
    return orderedPathFloatingBitsF64(parameter)
}

private fun pathParameterIntervalKeyF64(incidence: PathComponentIncidenceF64): PathParameterIntervalKeyF64 {
    val minimumBits = orderedPathParameterBitsF64(incidence.minimumParameter)
    val maximumBits = orderedPathParameterBitsF64(incidence.maximumParameter)
    check(maximumBits >= minimumBits && maximumBits - minimumBits < 16L)
    return PathParameterIntervalKeyF64(
        minimumBits = minimumBits,
        width = (maximumBits - minimumBits).toInt(),
    )
}

private data class PathEndpointProvenanceKeyF64(
    val originalPointPresent: Boolean,
    val originalPointX: Int,
    val originalPointY: Int,
    val parameters: List<Long>,
)

private data class PathEdgeSemanticKeyF64(
    val operandOrdinal: Int,
    val contourIndex: Int,
    val startX: Long,
    val startY: Long,
    val endX: Long,
    val endY: Long,
    val windingDelta: Int,
    val startProvenance: PathEndpointProvenanceKeyF64,
    val endProvenance: PathEndpointProvenanceKeyF64,
)

private data class PathEventIncidenceOrderKeyF64(
    val edgeRank: Int,
    val parameter: Long,
)

private data class PathEventOrderKeyF64(
    val first: PathEventIncidenceOrderKeyF64,
    val second: PathEventIncidenceOrderKeyF64,
    val pointX: Long,
    val pointY: Long,
    val exactWitness: PathExactIntersectionWitnessF64?,
)

private data class PathComponentOrderKeyF64(
    // Immutable birth-event rank. It is constant-size and never incorporates the mutable
    // incidence map, so AVL/heap comparisons stay O(1) with respect to component degree.
    val firstEvent: PathEventOrderKeyF64,
)

private fun pathEdgeSemanticKeyF64(edge: PathInputEdgeF64): PathEdgeSemanticKeyF64 = PathEdgeSemanticKeyF64(
    operandOrdinal = edge.operand.ordinal,
    contourIndex = edge.contourIndex,
    startX = orderedPathFloatingBitsF64(edge.start.x),
    startY = orderedPathFloatingBitsF64(edge.start.y),
    endX = orderedPathFloatingBitsF64(edge.end.x),
    endY = orderedPathFloatingBitsF64(edge.end.y),
    windingDelta = edge.windingDelta,
    startProvenance = pathEndpointProvenanceKeyF64(edge.startIdentity),
    endProvenance = pathEndpointProvenanceKeyF64(edge.endIdentity),
)

private fun pathEndpointProvenanceKeyF64(identity: PathVertexIdentityF64): PathEndpointProvenanceKeyF64 {
    val original = identity.originalPointF32
    return PathEndpointProvenanceKeyF64(
        originalPointPresent = original != null,
        originalPointX = original?.x?.let(::orderedPathFloatingBitsF32) ?: 0,
        originalPointY = original?.y?.let(::orderedPathFloatingBitsF32) ?: 0,
        parameters = identity.parameterByEdgeId.values.map(::orderedPathFloatingBitsF64).sorted(),
    )
}

private fun pathEventOrderKeyF64(
    firstEdgeRank: Int,
    firstParameter: Double,
    secondEdgeRank: Int,
    secondParameter: Double,
    point: Point2F64,
    exactWitness: PathExactIntersectionWitnessF64?,
): PathEventOrderKeyF64 {
    val first = PathEventIncidenceOrderKeyF64(firstEdgeRank, orderedPathFloatingBitsF64(firstParameter))
    val second = PathEventIncidenceOrderKeyF64(secondEdgeRank, orderedPathFloatingBitsF64(secondParameter))
    return if (comparePathEventIncidencesF64(first, second) <= 0) {
        PathEventOrderKeyF64(
            first = first,
            second = second,
            pointX = orderedPathFloatingBitsF64(point.x),
            pointY = orderedPathFloatingBitsF64(point.y),
            exactWitness = exactWitness,
        )
    } else {
        PathEventOrderKeyF64(
            first = second,
            second = first,
            pointX = orderedPathFloatingBitsF64(point.x),
            pointY = orderedPathFloatingBitsF64(point.y),
            exactWitness = exactWitness,
        )
    }
}

private fun pathComponentOrderKeyF64(firstEvent: PathEventOrderKeyF64): PathComponentOrderKeyF64 =
    PathComponentOrderKeyF64(firstEvent)

private fun pathEdgeSemanticRanksF64(edgeKeys: List<PathEdgeSemanticKeyF64>): List<Int> {
    if (edgeKeys.isEmpty()) return emptyList()
    val ranks = MutableList(edgeKeys.size) { 0 }
    var currentRank = 0
    ranks[0] = currentRank
    for (index in 1 until edgeKeys.size) {
        if (comparePathEdgeSemanticKeysF64(edgeKeys[index - 1], edgeKeys[index]) != 0) currentRank += 1
        ranks[index] = currentRank
    }
    return ranks
}

private fun comparePathInputEdgesSemanticallyF64(first: PathInputEdgeF64, second: PathInputEdgeF64): Int =
    comparePathEdgeSemanticKeysF64(pathEdgeSemanticKeyF64(first), pathEdgeSemanticKeyF64(second))

private fun comparePathEdgeSemanticKeysF64(first: PathEdgeSemanticKeyF64, second: PathEdgeSemanticKeyF64): Int {
    compareValues(first.operandOrdinal, second.operandOrdinal).takeIf { it != 0 }?.let { return it }
    compareValues(first.contourIndex, second.contourIndex).takeIf { it != 0 }?.let { return it }
    compareValues(first.startX, second.startX).takeIf { it != 0 }?.let { return it }
    compareValues(first.startY, second.startY).takeIf { it != 0 }?.let { return it }
    compareValues(first.endX, second.endX).takeIf { it != 0 }?.let { return it }
    compareValues(first.endY, second.endY).takeIf { it != 0 }?.let { return it }
    compareValues(first.windingDelta, second.windingDelta).takeIf { it != 0 }?.let { return it }
    comparePathEndpointProvenanceKeysF64(first.startProvenance, second.startProvenance).takeIf { it != 0 }?.let { return it }
    return comparePathEndpointProvenanceKeysF64(first.endProvenance, second.endProvenance)
}

private fun comparePathEndpointProvenanceKeysF64(
    first: PathEndpointProvenanceKeyF64,
    second: PathEndpointProvenanceKeyF64,
): Int {
    compareValues(first.originalPointPresent, second.originalPointPresent).takeIf { it != 0 }?.let { return it }
    compareValues(first.originalPointX, second.originalPointX).takeIf { it != 0 }?.let { return it }
    compareValues(first.originalPointY, second.originalPointY).takeIf { it != 0 }?.let { return it }
    compareValues(first.parameters.size, second.parameters.size).takeIf { it != 0 }?.let { return it }
    first.parameters.indices.forEach { index ->
        compareValues(first.parameters[index], second.parameters[index]).takeIf { it != 0 }?.let { return it }
    }
    return 0
}

private fun comparePathEventIncidencesF64(
    first: PathEventIncidenceOrderKeyF64,
    second: PathEventIncidenceOrderKeyF64,
): Int = compareValues(first.edgeRank, second.edgeRank)
    .takeIf { it != 0 } ?: compareValues(first.parameter, second.parameter)

private fun comparePathEventOrderKeysF64(first: PathEventOrderKeyF64, second: PathEventOrderKeyF64): Int {
    comparePathEventIncidencesF64(first.first, second.first).takeIf { it != 0 }?.let { return it }
    comparePathEventIncidencesF64(first.second, second.second).takeIf { it != 0 }?.let { return it }
    compareValues(first.pointX, second.pointX).takeIf { it != 0 }?.let { return it }
    compareValues(first.pointY, second.pointY).takeIf { it != 0 }?.let { return it }
    return when {
        first.exactWitness == null && second.exactWitness == null -> 0
        first.exactWitness == null -> -1
        second.exactWitness == null -> 1
        else -> comparePathExactWitnessesF64(first.exactWitness, second.exactWitness)
    }
}

private fun comparePathComponentOrderKeysF64(first: PathComponentOrderKeyF64, second: PathComponentOrderKeyF64): Int =
    comparePathEventOrderKeysF64(first.firstEvent, second.firstEvent)

private fun comparePathIntersectionComponentSemanticallyF64(
    first: PathIntersectionComponentF64,
    second: PathIntersectionComponentF64,
): Int = comparePathComponentOrderKeysF64(first.orderKey, second.orderKey)

private fun comparePathIntersectionComponentsF64(
    first: PathIntersectionComponentF64,
    second: PathIntersectionComponentF64,
): Int = comparePathIntersectionComponentSemanticallyF64(first, second)
    .takeIf { it != 0 } ?: compareValues(first.id, second.id)
// `id` is a storage/cursor tie-break only. `orderKey` is the immutable canonical birth-event
// rank, formed from precomputed edge ranks, parameters, canonical point and exact witness. Equal
// ranks mean equal semantic event keys; their edges are an automorphic key batch and every member
// is still evaluated before the atomic commit. Mutable component incidence maps are deliberately
// excluded, so neither an AVL nor the fixed candidate heap performs degree-dependent comparison.

private fun orderedPathFloatingBitsF64(value: Double): Long {
    val bits = if (value == 0.0) 0L else value.toRawBits()
    return if (bits < 0L) Long.MIN_VALUE - bits else bits
}

private fun orderedPathFloatingBitsF32(value: Float): Int {
    val bits = if (value == 0f) 0 else value.toRawBits()
    return if (bits < 0) Int.MIN_VALUE - bits else bits
}

private class PathParameterIntervalIndexF64 {
    private class Node(
        val key: PathParameterIntervalKeyF64,
        val components: PathComponentIdIndexF64 = PathComponentIdIndexF64(),
        var left: Node? = null,
        var right: Node? = null,
        var height: Int = 1,
    )

    private var root: Node? = null

    fun cursor(key: PathParameterIntervalKeyF64): PathComponentCursorF64? {
        var node = root
        while (node != null) {
            val comparison = comparePathParameterIntervalKeysF64(key, node.key)
            if (comparison == 0) return node.components.cursor()
            node = if (comparison < 0) node.left else node.right
        }
        return null
    }

    fun add(key: PathParameterIntervalKeyF64, component: PathIntersectionComponentF64) {
        root = add(root, key, component)
    }

    fun remove(key: PathParameterIntervalKeyF64, component: PathIntersectionComponentF64) {
        root = remove(root, key, component)
    }

    private fun add(
        node: Node?,
        key: PathParameterIntervalKeyF64,
        component: PathIntersectionComponentF64,
    ): Node = when {
        node == null -> Node(key).also { it.components.add(component) }
        comparePathParameterIntervalKeysF64(key, node.key) < 0 -> {
            node.left = add(node.left, key, component)
            rebalance(node)
        }
        comparePathParameterIntervalKeysF64(key, node.key) > 0 -> {
            node.right = add(node.right, key, component)
            rebalance(node)
        }
        else -> node.also { it.components.add(component) }
    }

    private fun remove(
        node: Node?,
        key: PathParameterIntervalKeyF64,
        component: PathIntersectionComponentF64,
    ): Node? {
        node ?: return null
        return when {
            comparePathParameterIntervalKeysF64(key, node.key) < 0 -> {
                node.left = remove(node.left, key, component)
                rebalance(node)
            }
            comparePathParameterIntervalKeysF64(key, node.key) > 0 -> {
                node.right = remove(node.right, key, component)
                rebalance(node)
            }
            else -> {
                node.components.remove(component)
                if (node.components.isEmpty()) join(node.left, node.right) else node
            }
        }
    }

    private fun join(left: Node?, right: Node?): Node? = when {
        left == null -> right
        right == null -> left
        nodeHeight(left) >= nodeHeight(right) -> {
            left.right = join(left.right, right)
            rebalance(left)
        }
        else -> {
            right.left = join(left, right.left)
            rebalance(right)
        }
    }

    private fun rebalance(node: Node): Node {
        node.height = max(nodeHeight(node.left), nodeHeight(node.right)) + 1
        val balance = nodeHeight(node.left) - nodeHeight(node.right)
        return when {
            balance > 1 && nodeHeight(node.left?.left) >= nodeHeight(node.left?.right) -> rotateRight(node)
            balance > 1 -> {
                node.left = rotateLeft(checkNotNull(node.left))
                rotateRight(node)
            }
            balance < -1 && nodeHeight(node.right?.right) >= nodeHeight(node.right?.left) -> rotateLeft(node)
            balance < -1 -> {
                node.right = rotateRight(checkNotNull(node.right))
                rotateLeft(node)
            }
            else -> node
        }
    }

    private fun rotateLeft(node: Node): Node {
        val pivot = checkNotNull(node.right)
        node.right = pivot.left
        pivot.left = node
        node.height = max(nodeHeight(node.left), nodeHeight(node.right)) + 1
        pivot.height = max(nodeHeight(pivot.left), nodeHeight(pivot.right)) + 1
        return pivot
    }

    private fun rotateRight(node: Node): Node {
        val pivot = checkNotNull(node.left)
        node.left = pivot.right
        pivot.right = node
        node.height = max(nodeHeight(node.left), nodeHeight(node.right)) + 1
        pivot.height = max(nodeHeight(pivot.left), nodeHeight(pivot.right)) + 1
        return pivot
    }

    private fun nodeHeight(node: Node?): Int = node?.height ?: 0
}

private fun comparePathParameterIntervalKeysF64(
    first: PathParameterIntervalKeyF64,
    second: PathParameterIntervalKeyF64,
): Int {
    val minimum = first.minimumBits.compareTo(second.minimumBits)
    return if (minimum != 0) minimum else first.width.compareTo(second.width)
}

private interface PathComponentCursorF64 {
    fun hasNext(): Boolean

    fun next(): PathIntersectionComponentF64
}

private class PathComponentIdIndexF64 {
    private class Node(
        val component: PathIntersectionComponentF64,
        var left: Node? = null,
        var right: Node? = null,
        var height: Int = 1,
    )

    private var root: Node? = null

    fun cursor(): PathComponentCursorF64 = Cursor(root)

    fun add(component: PathIntersectionComponentF64) {
        root = add(root, component)
    }

    fun remove(component: PathIntersectionComponentF64) {
        root = remove(root, component)
    }

    fun isEmpty(): Boolean = root == null

    private fun add(node: Node?, component: PathIntersectionComponentF64): Node = when {
        node == null -> Node(component)
        comparePathIntersectionComponentsF64(component, node.component) < 0 -> {
            node.left = add(node.left, component)
            rebalance(node)
        }
        comparePathIntersectionComponentsF64(component, node.component) > 0 -> {
            node.right = add(node.right, component)
            rebalance(node)
        }
        else -> node
    }

    private fun remove(node: Node?, component: PathIntersectionComponentF64): Node? {
        node ?: return null
        val comparison = comparePathIntersectionComponentsF64(component, node.component)
        return when {
            comparison < 0 -> {
                node.left = remove(node.left, component)
                rebalance(node)
            }
            comparison > 0 -> {
                node.right = remove(node.right, component)
                rebalance(node)
            }
            else -> join(node.left, node.right)
        }
    }

    private fun join(left: Node?, right: Node?): Node? = when {
        left == null -> right
        right == null -> left
        nodeHeight(left) >= nodeHeight(right) -> {
            left.right = join(left.right, right)
            rebalance(left)
        }
        else -> {
            right.left = join(left, right.left)
            rebalance(right)
        }
    }

    private fun rebalance(node: Node): Node {
        node.height = max(nodeHeight(node.left), nodeHeight(node.right)) + 1
        val balance = nodeHeight(node.left) - nodeHeight(node.right)
        return when {
            balance > 1 && nodeHeight(node.left?.left) >= nodeHeight(node.left?.right) -> rotateRight(node)
            balance > 1 -> {
                node.left = rotateLeft(checkNotNull(node.left))
                rotateRight(node)
            }
            balance < -1 && nodeHeight(node.right?.right) >= nodeHeight(node.right?.left) -> rotateLeft(node)
            balance < -1 -> {
                node.right = rotateRight(checkNotNull(node.right))
                rotateLeft(node)
            }
            else -> node
        }
    }

    private fun rotateLeft(node: Node): Node {
        val pivot = checkNotNull(node.right)
        node.right = pivot.left
        pivot.left = node
        node.height = max(nodeHeight(node.left), nodeHeight(node.right)) + 1
        pivot.height = max(nodeHeight(pivot.left), nodeHeight(pivot.right)) + 1
        return pivot
    }

    private fun rotateRight(node: Node): Node {
        val pivot = checkNotNull(node.left)
        node.left = pivot.right
        pivot.right = node
        node.height = max(nodeHeight(node.left), nodeHeight(node.right)) + 1
        pivot.height = max(nodeHeight(pivot.left), nodeHeight(pivot.right)) + 1
        return pivot
    }

    private fun nodeHeight(node: Node?): Int = node?.height ?: 0

    private class Cursor(root: Node?) : PathComponentCursorF64 {
        private val stack = mutableListOf<Node>()

        init {
            pushLeft(root)
        }

        override fun hasNext(): Boolean = stack.isNotEmpty()

        override fun next(): PathIntersectionComponentF64 {
            val node = stack.removeAt(stack.lastIndex)
            pushLeft(node.right)
            return node.component
        }

        private fun pushLeft(start: Node?) {
            var node = start
            while (node != null) {
                stack += node
                node = node.left
            }
        }
    }
}

private class PathIntersectionRegistryF64(
    private val edges: List<PathInputEdgeF64>,
    private val maxInteriorCuts: Int,
    private val candidateWorkBudget: PathCandidateWorkBudgetI32,
) {
    private val activeComponentsById = linkedMapOf<Int, PathIntersectionComponentF64>()
    val components: List<PathIntersectionComponentF64>
        get() = activeComponentsById.values.sortedWith(Comparator(::comparePathIntersectionComponentsF64))
    // Pair enumeration opens two fixed 31 * 16 direct-signature cursor sets and one exact-witness
    // cursor. The exact stream is consumed first as the stronger proof, then the direct streams
    // are merged before any index mutation; persistent state is canonical components/incidences,
    // never raw pair events or profile subsets.
    private val componentsByEdge = List(edges.size) { PathEdgeComponentIndexF64() }
    private val edgeKeys = edges.map(::pathEdgeSemanticKeyF64)
    // Equal semantic keys intentionally receive the same rank. Such edges are the automorphic
    // batch described by the event-key invariant; source IDs never determine their rank.
    private val edgeSemanticRanks = pathEdgeSemanticRanksF64(edgeKeys)
    private val exactLines = edges.map(::exactPathLineF64)
    private val exactComponentsByWitness = PathExactWitnessIndexF64()
    private var nextComponentId = 0
    private var nextCandidateEpoch = 0L
    private var interiorCutCount = 0

    // A geometrically proven endpoint identity needs no new component and therefore must not
    // consume `maxIntersections`. It still represents the same two incoming incidences that a
    // normal pair would establish, so it spends that minimal candidate work before the no-op.
    fun consumeKnownEndpointNoOpCandidateWorkF64() {
        candidateWorkBudget.consume()
        candidateWorkBudget.consume()
    }

    private fun incomingProfileF64(
        firstEdgeIndex: Int,
        firstParameter: Double,
        secondEdgeIndex: Int,
        secondParameter: Double,
        exactWitness: PathExactIntersectionWitnessF64?,
        canonicalPoint: Point2F64,
    ): PathComponentProfileF64 {
        val incidences = mutableMapOf<Int, PathComponentIncidenceF64>()
        // The two incoming incidences are temporary state too: debit before each insertion so a
        // tiny global work budget fails before allocating an arbitrarily large later profile.
        consumeCandidateWorkF64()
        incidences[firstEdgeIndex] = PathComponentIncidenceF64(firstParameter)
        consumeCandidateWorkF64()
        incidences[secondEdgeIndex] = PathComponentIncidenceF64(secondParameter)
        consumeCandidateWorkF64()
        consumeCandidateWorkF64()
        return PathComponentProfileF64(exactWitness, canonicalTopologicalPointF64(canonicalPoint), incidences)
    }

    private fun copyProfileWithBudgetF64(source: PathComponentProfileF64): PathComponentProfileF64 {
        val copied = mutableMapOf<Int, PathComponentIncidenceF64>()
        val iterator = source.incidencesByEdge.entries.iterator()
        while (iterator.hasNext()) {
            consumeCandidateWorkF64()
            val entry = iterator.next()
            copied[entry.key] = entry.value
        }
        consumeCandidateWorkF64()
        return PathComponentProfileF64(source.exactWitness, source.canonicalPoint, copied)
    }

    fun addIntersection(
        firstEdgeIndex: Int,
        firstParameter: Double,
        secondEdgeIndex: Int,
        secondParameter: Double,
        point: Point2F64,
        hasUniqueCarrierIntersection: Boolean,
    ) {
        val snappedFirstParameter = snapParameterF64(firstParameter)
        val snappedSecondParameter = snapParameterF64(secondParameter)
        val incomingWitness = if (hasUniqueCarrierIntersection) {
            exactPathIntersectionWitnessF64(exactLines[firstEdgeIndex], exactLines[secondEdgeIndex])
        } else {
            null
        }
        val incomingPoint = canonicalTopologicalPointF64(incomingWitness?.canonicalPoint() ?: point)
        val incoming = incomingProfileF64(
            firstEdgeIndex = firstEdgeIndex,
            firstParameter = snappedFirstParameter,
            secondEdgeIndex = secondEdgeIndex,
            secondParameter = snappedSecondParameter,
            exactWitness = incomingWitness,
            canonicalPoint = incomingPoint,
        )
        val eventKey = pathEventOrderKeyF64(
            firstEdgeRank = edgeSemanticRanks[firstEdgeIndex],
            firstParameter = snappedFirstParameter,
            secondEdgeRank = edgeSemanticRanks[secondEdgeIndex],
            secondParameter = snappedSecondParameter,
            point = incomingPoint,
            exactWitness = incomingWitness,
        )
        if (isRepeatedExactConcurrencyNoOpF64(
                firstEdgeIndex = firstEdgeIndex,
                firstParameter = snappedFirstParameter,
                secondEdgeIndex = secondEdgeIndex,
                secondParameter = snappedSecondParameter,
                incoming = incoming,
            )
        ) return

        val accumulator = copyProfileWithBudgetF64(incoming)
        val accepted = mutableListOf<PathIntersectionComponentF64>()

        forEachMatchingComponentF64(
            firstEdgeIndex = firstEdgeIndex,
            firstParameter = snappedFirstParameter,
            secondEdgeIndex = secondEdgeIndex,
            secondParameter = snappedSecondParameter,
            incomingWitness = incomingWitness,
        ) { candidate ->
            val exactMatch = exactMatchF64(candidate, incomingWitness)
            if (!exactMatch && !isCompatibleWithProfileF64(candidate, incoming, requireSharedIncidence = true)) {
                return@forEachMatchingComponentF64
            }
            if (!exactMatch && !isCompatibleWithProfileF64(candidate, accumulator, requireSharedIncidence = true)) {
                return@forEachMatchingComponentF64
            }
            check(mergeComponentIntoProfileF64(accumulator, candidate, exactMatch))
            consumeCandidateWorkF64()
            accepted += candidate
        }
        commitAtomicProfileF64(eventKey, incoming, accumulator, accepted)
    }

    private fun forEachMatchingComponentF64(
        firstEdgeIndex: Int,
        firstParameter: Double,
        secondEdgeIndex: Int,
        secondParameter: Double,
        incomingWitness: PathExactIntersectionWitnessF64?,
        action: (PathIntersectionComponentF64) -> Unit,
    ) {
        val candidateEpoch = nextCandidateEpochF64()
        // The exact AVL is a complete multi-value stream of a stronger proof. Process it before
        // direct candidates, then mark every delivered component persistently for this event.
        // This both gives exact equality priority and avoids assuming that a direct AVL, ordered
        // by the semantic component key, is also ordered by the dynamic exact/non-exact flag.
        exactComponentCursorF64(incomingWitness)?.let { cursor ->
            while (cursor.hasNext()) {
                consumeCandidateWorkF64()
                visitCandidateOnceF64(cursor.next(), candidateEpoch, action)
            }
        }
        val streams = buildList {
            directComponentCursorsF64(firstEdgeIndex, firstParameter).forEach(::add)
            directComponentCursorsF64(secondEdgeIndex, secondParameter).forEach(::add)
        }
        val heap = mutableListOf<PathCandidateStreamF64>()
        streams.forEachIndexed { streamIndex, cursor ->
            if (cursor.hasNext()) {
                consumeCandidateWorkF64()
                heapAddCandidateStreamF64(
                    heap,
                    PathCandidateStreamF64(streamIndex, cursor, cursor.next()),
                )
            }
        }
        while (heap.isNotEmpty()) {
            val stream = heapRemoveCandidateStreamF64(heap)
            val candidate = stream.component
            if (stream.cursor.hasNext()) {
                consumeCandidateWorkF64()
                stream.component = stream.cursor.next()
                heapAddCandidateStreamF64(heap, stream)
            }
            visitCandidateOnceF64(candidate, candidateEpoch, action)
        }
    }

    // A repeated exact concurrence can be a real no-op only after the complete candidate domain
    // has been exhausted: a direct-only candidate compatible with N could otherwise still belong
    // to the atomic closure. This probe retains at most one eligible component and never builds
    // an accumulator. Its final predicate reads only the canonical point/witness and N's two
    // incidences; all raw pops and all compatibility inspections are already budgeted by the
    // normal candidate iterator.
    private fun isRepeatedExactConcurrencyNoOpF64(
        firstEdgeIndex: Int,
        firstParameter: Double,
        secondEdgeIndex: Int,
        secondParameter: Double,
        incoming: PathComponentProfileF64,
    ): Boolean {
        val incomingWitness = incoming.exactWitness ?: return false
        var onlyEligible: PathIntersectionComponentF64? = null
        var hasAnotherEligible = false
        forEachMatchingComponentF64(
            firstEdgeIndex = firstEdgeIndex,
            firstParameter = firstParameter,
            secondEdgeIndex = secondEdgeIndex,
            secondParameter = secondParameter,
            incomingWitness = incomingWitness,
        ) { candidate ->
            val exactMatch = exactMatchF64(candidate, incomingWitness)
            if (!exactMatch && !isCompatibleWithProfileF64(candidate, incoming, requireSharedIncidence = true)) {
                return@forEachMatchingComponentF64
            }
            if (onlyEligible == null) {
                onlyEligible = candidate
            } else {
                hasAnotherEligible = true
            }
        }
        val component = onlyEligible ?: return false
        return !hasAnotherEligible && componentAlreadyRepresentsIncomingF64(component, incoming)
    }

    private fun nextCandidateEpochF64(): Long {
        if (nextCandidateEpoch == Long.MAX_VALUE) {
            activeComponentsById.values.forEach { component -> component.lastCandidateEpoch = Long.MIN_VALUE }
            nextCandidateEpoch = 0L
        }
        return nextCandidateEpoch++
    }

    private fun visitCandidateOnceF64(
        component: PathIntersectionComponentF64,
        candidateEpoch: Long,
        action: (PathIntersectionComponentF64) -> Unit,
    ) {
        if (component.lastCandidateEpoch == candidateEpoch) return
        component.lastCandidateEpoch = candidateEpoch
        action(component)
    }

    private fun profileContainsAllWithBudgetF64(
        profile: PathComponentProfileF64,
        incoming: PathComponentProfileF64,
    ): Boolean {
        val iterator = incoming.incidencesByEdge.entries.iterator()
        while (iterator.hasNext()) {
            consumeCandidateWorkF64()
            val incomingEntry = iterator.next()
            if (incomingEntry.key !in profile.incidencesByEdge) return false
        }
        return true
    }

    private fun countInteriorCutsWithBudgetF64(incidences: Map<Int, PathComponentIncidenceF64>): Int {
        var count = 0
        val iterator = incidences.entries.iterator()
        while (iterator.hasNext()) {
            consumeCandidateWorkF64()
            val entry = iterator.next()
            if (isInteriorCutF64(entry.key, entry.value.parameter)) count += 1
        }
        return count
    }

    private fun copyProfileIncidencesIntoComponentF64(
        profile: PathComponentProfileF64,
        component: PathIntersectionComponentF64,
    ) {
        val iterator = profile.incidencesByEdge.entries.iterator()
        while (iterator.hasNext()) {
            consumeCandidateWorkF64()
            val entry = iterator.next()
            component.incidencesByEdge[entry.key] = entry.value
        }
    }

    private fun clearComponentIncidencesWithBudgetF64(component: PathIntersectionComponentF64) {
        while (component.incidencesByEdge.isNotEmpty()) {
            consumeCandidateWorkF64()
            val iterator = component.incidencesByEdge.entries.iterator()
            iterator.next()
            iterator.remove()
        }
    }

    private fun removeComponentIncidencesFromIndexF64(component: PathIntersectionComponentF64) {
        val iterator = component.incidencesByEdge.entries.iterator()
        while (iterator.hasNext()) {
            consumeCandidateWorkF64()
            val entry = iterator.next()
            removeComponentForEdgeF64(entry.key, component, entry.value)
        }
    }

    private fun insertComponentIncidencesIntoIndexF64(component: PathIntersectionComponentF64) {
        val iterator = component.incidencesByEdge.entries.iterator()
        while (iterator.hasNext()) {
            consumeCandidateWorkF64()
            val entry = iterator.next()
            insertComponentForEdgeF64(entry.key, component, entry.value)
        }
    }

    // The candidate cursors are still live while this method runs, so all index mutation is
    // deferred here. Accepted storage is O(k): every entry other than the surviving winner is a
    // destructive component removal, and the no-merge case contains one component only.
    private fun commitAtomicProfileF64(
        eventKey: PathEventOrderKeyF64,
        incoming: PathComponentProfileF64,
        profile: PathComponentProfileF64,
        accepted: List<PathIntersectionComponentF64>,
    ) {
        check(profileContainsAllWithBudgetF64(profile, incoming))
        if (accepted.isEmpty()) {
            val interiorCuts = countInteriorCutsWithBudgetF64(profile.incidencesByEdge)
            ensureInteriorCutCapacityF64(interiorCuts)
            consumeCandidateWorkF64()
            val component = PathIntersectionComponentF64(
                id = nextComponentId++,
                orderKey = pathComponentOrderKeyF64(eventKey),
                exactWitness = profile.exactWitness,
                canonicalPoint = profile.canonicalPoint,
            )
            copyProfileIncidencesIntoComponentF64(profile, component)
            consumeCandidateWorkF64()
            activeComponentsById[component.id] = component
            insertComponentIncidencesIntoIndexF64(component)
            interiorCutCount += interiorCuts
            registerExactComponentF64(component)
            return
        }

        val winner = accepted.minWith(Comparator(::comparePathIntersectionComponentsF64))
        // Exact n-way no-ops have already returned through the preflight above, before an
        // accumulator copy. This fallback retains the same semantic no-op for direct events;
        // its profile work is charged and therefore cannot hide a degree-dependent allocation.
        if (accepted.size == 1 && componentAlreadyRepresentsIncomingF64(winner, incoming)) return
        var removedInteriorCuts = 0
        accepted.forEach { component ->
            removedInteriorCuts += countInteriorCutsWithBudgetF64(component.incidencesByEdge)
        }
        val profileInteriorCuts = countInteriorCutsWithBudgetF64(profile.incidencesByEdge)
        ensureInteriorCutCapacityF64(max(0, profileInteriorCuts - removedInteriorCuts))

        accepted.forEach { component ->
            unregisterExactComponentF64(component)
            removeComponentIncidencesFromIndexF64(component)
            consumeCandidateWorkF64()
            activeComponentsById.remove(component.id)
        }
        interiorCutCount += profileInteriorCuts - removedInteriorCuts
        clearComponentIncidencesWithBudgetF64(winner)
        copyProfileIncidencesIntoComponentF64(profile, winner)
        consumeCandidateWorkF64()
        winner.exactWitness = profile.exactWitness
        consumeCandidateWorkF64()
        winner.canonicalPoint = profile.canonicalPoint
        var firstEvent = eventKey
        accepted.forEach { component ->
            consumeCandidateWorkF64()
            if (comparePathEventOrderKeysF64(component.orderKey.firstEvent, firstEvent) < 0) {
                firstEvent = component.orderKey.firstEvent
            }
        }
        consumeCandidateWorkF64()
        winner.orderKey = pathComponentOrderKeyF64(firstEvent)
        consumeCandidateWorkF64()
        activeComponentsById[winner.id] = winner
        insertComponentIncidencesIntoIndexF64(winner)
        registerExactComponentF64(winner)
    }

    private fun componentAlreadyRepresentsIncomingF64(
        component: PathIntersectionComponentF64,
        incoming: PathComponentProfileF64,
    ): Boolean {
        consumeCandidateWorkF64()
        if (!sameTopologicalPointF64(canonicalPointF64(incoming.canonicalPoint, component.canonicalPoint), component.canonicalPoint)) {
            return false
        }
        consumeCandidateWorkF64()
        val mergedWitness = canonicalExactWitnessF64(incoming.exactWitness, component.exactWitness)
        val componentWitness = component.exactWitness
        if (mergedWitness != null && (componentWitness == null ||
                comparePathExactWitnessesF64(mergedWitness, componentWitness) != 0)
        ) return false
        if (mergedWitness == null && componentWitness != null) return false

        val exactPriority = exactMatchF64(component, incoming.exactWitness)
        val iterator = incoming.incidencesByEdge.entries.iterator()
        while (iterator.hasNext()) {
            consumeCandidateWorkF64()
            val incomingEntry = iterator.next()
            val edgeIndex = incomingEntry.key
            val incomingIncidence = incomingEntry.value
            val componentIncidence = component.incidencesByEdge[edgeIndex] ?: return false
            val mergedIncidence = mergeDirectPathIncidencesF64(incomingIncidence, componentIncidence)
                ?: if (exactPriority) mergeExactPathIncidencesF64(incomingIncidence, componentIncidence) else return false
            if (mergedIncidence != componentIncidence) return false
        }
        return true
    }

    private fun ensureInteriorCutCapacityF64(additionalInteriorCuts: Int) {
        if (additionalInteriorCuts > maxInteriorCuts - interiorCutCount) {
            throw IllegalStateException("path-half-edge-limit")
        }
    }

    private fun isInteriorCutF64(edgeIndex: Int, parameter: Double): Boolean =
        !sameTopologicalPointF64(edges[edgeIndex].start, edges[edgeIndex].end) && parameter != 0.0 && parameter != 1.0

    private class PathCandidateStreamF64(
        val ordinal: Int,
        val cursor: PathComponentCursorF64,
        var component: PathIntersectionComponentF64,
    )

    private fun consumeCandidateWorkF64() {
        candidateWorkBudget.consume()
    }

    private fun directComponentCursorsF64(edgeIndex: Int, parameter: Double): List<PathComponentCursorF64> =
        componentsByEdge[edgeIndex].cursors(parameter)

    private fun exactComponentCursorF64(witness: PathExactIntersectionWitnessF64?): PathComponentCursorF64? {
        if (witness == null) return null
        // Equality is checked again at selection time; this AVL lookup is only a complete,
        // multi-value candidate acceleration for the homogeneous witness.
        return exactComponentsByWitness.cursor(witness)
    }

    private fun isCompatibleWithProfileF64(
        component: PathIntersectionComponentF64,
        profile: PathComponentProfileF64,
        requireSharedIncidence: Boolean,
    ): Boolean {
        var hasSharedIncidence = false
        val iterator = component.incidencesByEdge.entries.iterator()
        while (iterator.hasNext()) {
            // Debit before reading either side. Non-common incidences are still a real scan and
            // are the high-degree blocker case that the global work budget must bound.
            consumeCandidateWorkF64()
            val componentEntry = iterator.next()
            val edgeIndex = componentEntry.key
            val componentIncidence = componentEntry.value
            val profileIncidence = profile.incidencesByEdge[edgeIndex] ?: continue
            if (mergeDirectPathIncidencesF64(componentIncidence, profileIncidence) == null) return false
            hasSharedIncidence = true
        }
        return hasSharedIncidence || !requireSharedIncidence
    }

    private fun mergeComponentIntoProfileF64(
        profile: PathComponentProfileF64,
        component: PathIntersectionComponentF64,
        exactPriority: Boolean,
    ): Boolean {
        val iterator = component.incidencesByEdge.entries.iterator()
        while (iterator.hasNext()) {
            // As above, charge absent incidences too: copying one into the accumulator is work
            // and makes temporary state O(min(I, remaining candidate work)).
            consumeCandidateWorkF64()
            val componentEntry = iterator.next()
            val edgeIndex = componentEntry.key
            val componentIncidence = componentEntry.value
            val profileIncidence = profile.incidencesByEdge[edgeIndex]
            if (profileIncidence == null) {
                profile.incidencesByEdge[edgeIndex] = componentIncidence
                continue
            }
            val merged = mergeDirectPathIncidencesF64(profileIncidence, componentIncidence)
            when {
                merged != null -> profile.incidencesByEdge[edgeIndex] = merged
                exactPriority -> profile.incidencesByEdge[edgeIndex] =
                    mergeExactPathIncidencesF64(profileIncidence, componentIncidence)
                else -> return false
            }
        }
        consumeCandidateWorkF64()
        profile.exactWitness = canonicalExactWitnessF64(profile.exactWitness, component.exactWitness)
        consumeCandidateWorkF64()
        profile.canonicalPoint = canonicalPointF64(profile.canonicalPoint, component.canonicalPoint)
        return true
    }

    private fun mergeExactPathIncidencesF64(
        first: PathComponentIncidenceF64,
        second: PathComponentIncidenceF64,
    ): PathComponentIncidenceF64 {
        // An exact homogeneous witness is stronger than an unstable division parameter. Retain a
        // single cut without widening its ULP interval: an exact endpoint wins, otherwise the
        // least canonical parameter wins. This is deterministic on JVM and JS and cannot turn a
        // direct 0/15/30 chain into a 30-ULP interval.
        consumeCandidateWorkF64()
        val parameter = when {
            first.parameter == 0.0 || second.parameter == 0.0 -> 0.0
            first.parameter == 1.0 || second.parameter == 1.0 -> 1.0
            else -> min(first.parameter, second.parameter)
        }
        return PathComponentIncidenceF64(parameter)
    }

    private fun canonicalExactWitnessF64(
        first: PathExactIntersectionWitnessF64?,
        second: PathExactIntersectionWitnessF64?,
    ): PathExactIntersectionWitnessF64? = when {
        first == null -> second
        second == null -> first
        comparePathExactWitnessesF64(first, second) <= 0 -> first
        else -> second
    }

    private fun heapAddCandidateStreamF64(
        heap: MutableList<PathCandidateStreamF64>,
        stream: PathCandidateStreamF64,
    ) {
        heap += stream
        var index = heap.lastIndex
        while (index > 0) {
            val parent = (index - 1) / 2
            if (compareCandidateStreamsF64(heap[parent], heap[index]) <= 0) break
            heap[parent] = heap[index].also { heap[index] = heap[parent] }
            index = parent
        }
    }

    private fun heapRemoveCandidateStreamF64(
        heap: MutableList<PathCandidateStreamF64>,
    ): PathCandidateStreamF64 {
        val result = heap[0]
        val last = heap.removeAt(heap.lastIndex)
        if (heap.isNotEmpty()) {
            heap[0] = last
            var index = 0
            while (true) {
                val left = index * 2 + 1
                if (left >= heap.size) break
                val right = left + 1
                val child = if (right < heap.size &&
                    compareCandidateStreamsF64(heap[right], heap[left]) < 0
                ) {
                    right
                } else {
                    left
                }
                if (compareCandidateStreamsF64(heap[index], heap[child]) <= 0) break
                heap[index] = heap[child].also { heap[child] = heap[index] }
                index = child
            }
        }
        return result
    }

    private fun compareCandidateStreamsF64(
        first: PathCandidateStreamF64,
        second: PathCandidateStreamF64,
    ): Int {
        comparePathIntersectionComponentsF64(first.component, second.component).takeIf { it != 0 }?.let { return it }
        return compareValues(first.ordinal, second.ordinal)
    }

    private fun exactMatchF64(
        component: PathIntersectionComponentF64,
        incomingWitness: PathExactIntersectionWitnessF64?,
    ): Boolean = incomingWitness != null && component.exactWitness?.samePoint(incomingWitness) == true

    private fun insertComponentForEdgeF64(
        edgeIndex: Int,
        component: PathIntersectionComponentF64,
        incidence: PathComponentIncidenceF64,
    ) {
        consumeCandidateWorkF64()
        componentsByEdge[edgeIndex].add(component, incidence)
    }

    private fun removeComponentForEdgeF64(
        edgeIndex: Int,
        component: PathIntersectionComponentF64,
        incidence: PathComponentIncidenceF64,
    ) {
        consumeCandidateWorkF64()
        componentsByEdge[edgeIndex].remove(component, incidence)
    }

    private fun registerExactComponentF64(component: PathIntersectionComponentF64) {
        component.exactWitness?.let { witness ->
            consumeCandidateWorkF64()
            exactComponentsByWitness.insert(witness, component)
        }
    }

    private fun unregisterExactComponentF64(component: PathIntersectionComponentF64) {
        component.exactWitness?.let { witness ->
            consumeCandidateWorkF64()
            exactComponentsByWitness.remove(witness, component)
        }
    }
}

private data class PathExactLineF64(
    val a: DoubleArray,
    val b: DoubleArray,
    val c: DoubleArray,
)

private data class PathExactIntersectionWitnessF64(
    val x: DoubleArray,
    val y: DoubleArray,
    val w: DoubleArray,
) {
    fun samePoint(other: PathExactIntersectionWitnessF64?): Boolean = other != null &&
        ExpansionF64.sign(
            ExpansionF64.expansionDiff(
                ExpansionF64.product(x, other.w),
                ExpansionF64.product(other.x, w),
            ),
        ) == 0 &&
        ExpansionF64.sign(
            ExpansionF64.expansionDiff(
                ExpansionF64.product(y, other.w),
                ExpansionF64.product(other.y, w),
            ),
        ) == 0

    fun canonicalPoint(): Point2F64? {
        val pointX = exactQuotientF64(x, w) ?: return null
        val pointY = exactQuotientF64(y, w) ?: return null
        return Point2F64(
            x = if (pointX == 0.0) 0.0 else pointX,
            y = if (pointY == 0.0) 0.0 else pointY,
        )
    }
}

private class PathExactWitnessIndexF64 {
    private class Node(
        val witness: PathExactIntersectionWitnessF64,
        val components: PathComponentIdIndexF64 = PathComponentIdIndexF64(),
        var left: Node? = null,
        var right: Node? = null,
        var height: Int = 1,
    )

    private var root: Node? = null

    fun cursor(witness: PathExactIntersectionWitnessF64): PathComponentCursorF64? {
        var node = root
        while (node != null) {
            val comparison = comparePathExactWitnessesF64(witness, node.witness)
            when {
                comparison == 0 -> return node.components.cursor()
                comparison < 0 -> node = node.left
                else -> node = node.right
            }
        }
        return null
    }

    fun insert(witness: PathExactIntersectionWitnessF64, component: PathIntersectionComponentF64) {
        root = insert(root, witness, component)
    }

    fun remove(witness: PathExactIntersectionWitnessF64, component: PathIntersectionComponentF64) {
        root = remove(root, witness, component)
    }

    private fun insert(
        node: Node?,
        witness: PathExactIntersectionWitnessF64,
        component: PathIntersectionComponentF64,
    ): Node = when {
        node == null -> Node(witness).also { it.components.add(component) }
        comparePathExactWitnessesF64(witness, node.witness) < 0 -> {
            node.left = insert(node.left, witness, component)
            rebalance(node)
        }
        comparePathExactWitnessesF64(witness, node.witness) > 0 -> {
            node.right = insert(node.right, witness, component)
            rebalance(node)
        }
        else -> node.also { it.components.add(component) }
    }

    private fun remove(
        node: Node?,
        witness: PathExactIntersectionWitnessF64,
        component: PathIntersectionComponentF64,
    ): Node? {
        node ?: return null
        val comparison = comparePathExactWitnessesF64(witness, node.witness)
        return when {
            comparison < 0 -> {
                node.left = remove(node.left, witness, component)
                rebalance(node)
            }
            comparison > 0 -> {
                node.right = remove(node.right, witness, component)
                rebalance(node)
            }
            else -> {
                node.components.remove(component)
                if (node.components.isEmpty()) join(node.left, node.right) else node
            }
        }
    }

    private fun join(left: Node?, right: Node?): Node? = when {
        left == null -> right
        right == null -> left
        nodeHeight(left) >= nodeHeight(right) -> {
            left.right = join(left.right, right)
            rebalance(left)
        }
        else -> {
            right.left = join(left, right.left)
            rebalance(right)
        }
    }

    private fun rebalance(node: Node): Node {
        node.height = max(nodeHeight(node.left), nodeHeight(node.right)) + 1
        val balance = nodeHeight(node.left) - nodeHeight(node.right)
        return when {
            balance > 1 && nodeHeight(node.left?.left) >= nodeHeight(node.left?.right) -> rotateRight(node)
            balance > 1 -> {
                node.left = rotateLeft(checkNotNull(node.left))
                rotateRight(node)
            }
            balance < -1 && nodeHeight(node.right?.right) >= nodeHeight(node.right?.left) -> rotateLeft(node)
            balance < -1 -> {
                node.right = rotateRight(checkNotNull(node.right))
                rotateLeft(node)
            }
            else -> node
        }
    }

    private fun rotateLeft(node: Node): Node {
        val pivot = checkNotNull(node.right)
        node.right = pivot.left
        pivot.left = node
        node.height = max(nodeHeight(node.left), nodeHeight(node.right)) + 1
        pivot.height = max(nodeHeight(pivot.left), nodeHeight(pivot.right)) + 1
        return pivot
    }

    private fun rotateRight(node: Node): Node {
        val pivot = checkNotNull(node.left)
        node.left = pivot.right
        pivot.right = node
        node.height = max(nodeHeight(node.left), nodeHeight(node.right)) + 1
        pivot.height = max(nodeHeight(pivot.left), nodeHeight(pivot.right)) + 1
        return pivot
    }

    private fun nodeHeight(node: Node?): Int = node?.height ?: 0
}

private fun comparePathExactWitnessesF64(
    first: PathExactIntersectionWitnessF64,
    second: PathExactIntersectionWitnessF64,
): Int {
    val x = compareExactPathQuotientsF64(first.x, first.w, second.x, second.w)
    return if (x != 0) x else compareExactPathQuotientsF64(first.y, first.w, second.y, second.w)
}

private fun compareExactPathQuotientsF64(
    firstNumerator: DoubleArray,
    firstDenominator: DoubleArray,
    secondNumerator: DoubleArray,
    secondDenominator: DoubleArray,
): Int {
    val crossDifference = ExpansionF64.expansionDiff(
        ExpansionF64.product(firstNumerator, secondDenominator),
        ExpansionF64.product(secondNumerator, firstDenominator),
    )
    val rawSign = ExpansionF64.sign(crossDifference)
    if (rawSign == 0) return 0
    val denominatorSign = ExpansionF64.sign(firstDenominator) * ExpansionF64.sign(secondDenominator)
    return if (denominatorSign > 0) rawSign else -rawSign
}

private fun exactQuotientF64(numerator: DoubleArray, denominator: DoubleArray): Double? {
    val denominatorEstimate = estimateExpansionF64(denominator)
    if (denominatorEstimate == 0.0 || !denominatorEstimate.isFinite()) return null
    var quotient = estimateExpansionF64(numerator) / denominatorEstimate
    if (!quotient.isFinite()) return null
    repeat(8) {
        val residual = ExpansionF64.expansionDiff(
            numerator,
            ExpansionF64.product(doubleArrayOf(quotient), denominator),
        )
        val correction = estimateExpansionF64(residual) / denominatorEstimate
        if (!correction.isFinite()) return null
        val corrected = quotient + correction
        if (corrected == quotient) return if (quotient.isFinite()) quotient else null
        quotient = corrected
    }
    return if (quotient.isFinite()) quotient else null
}

private fun exactPathLineF64(edge: PathInputEdgeF64): PathExactLineF64? {
    val canonicalEdge = canonicalTopologicalPathInputEdgeF64(edge)
    if (!canonicalEdge.start.isFinite() || !canonicalEdge.end.isFinite() ||
        sameTopologicalPointF64(canonicalEdge.start, canonicalEdge.end)
    ) return null
    return PathExactLineF64(
        a = ExpansionF64.twoDiff(canonicalEdge.start.y, canonicalEdge.end.y),
        b = ExpansionF64.twoDiff(canonicalEdge.end.x, canonicalEdge.start.x),
        c = ExpansionF64.expansionDiff(
            ExpansionF64.product(doubleArrayOf(canonicalEdge.start.x), doubleArrayOf(canonicalEdge.end.y)),
            ExpansionF64.product(doubleArrayOf(canonicalEdge.start.y), doubleArrayOf(canonicalEdge.end.x)),
        ),
    )
}

private fun exactPathIntersectionWitnessF64(
    first: PathExactLineF64?,
    second: PathExactLineF64?,
): PathExactIntersectionWitnessF64? {
    if (first == null || second == null) return null
    val x = ExpansionF64.expansionDiff(
        ExpansionF64.product(first.b, second.c),
        ExpansionF64.product(first.c, second.b),
    )
    val y = ExpansionF64.expansionDiff(
        ExpansionF64.product(first.c, second.a),
        ExpansionF64.product(first.a, second.c),
    )
    val w = ExpansionF64.expansionDiff(
        ExpansionF64.product(first.a, second.b),
        ExpansionF64.product(first.b, second.a),
    )
    return if (ExpansionF64.sign(w) == 0) null else PathExactIntersectionWitnessF64(x, y, w)
}

private fun expansionSumF64(vararg expansions: DoubleArray): DoubleArray = expansions.fold(doubleArrayOf()) { sum, expansion ->
    ExpansionF64.expansionSum(sum, expansion)
}

private fun estimateExpansionF64(expansion: DoubleArray): Double = expansion.fold(0.0) { sum, component -> sum + component }

private fun intersectCollinearPathEdgesF64(first: PathInputEdgeF64, second: PathInputEdgeF64): PathIntersectionF64? {
    val secondStartOnFirst = unboundedParameterAtPointF64(second.start, first)
    val secondEndOnFirst = unboundedParameterAtPointF64(second.end, first)
    val firstStart = max(0.0, min(secondStartOnFirst, secondEndOnFirst))
    val firstEnd = min(1.0, max(secondStartOnFirst, secondEndOnFirst))
    if (firstStart > firstEnd && !samePathParameterF64(firstStart, firstEnd)) return null

    val startT = snapParameterF64(firstStart)
    val endT = snapParameterF64(firstEnd)
    val start = pointAtPathParameterF64(first, startT)
    val end = pointAtPathParameterF64(first, endT)
    val firstPerSecondParameter = secondEndOnFirst - secondStartOnFirst
    if (firstPerSecondParameter == 0.0 || !firstPerSecondParameter.isFinite()) return null
    val secondStart = snapParameterF64(
        ((startT - secondStartOnFirst) / firstPerSecondParameter).coerceIn(0.0, 1.0),
    )
    val secondEnd = snapParameterF64(
        ((endT - secondStartOnFirst) / firstPerSecondParameter).coerceIn(0.0, 1.0),
    )
    if (samePathParameterF64(startT, endT)) {
        return PathIntersectionF64.PointF64(start, startT, secondStart)
    }
    return PathIntersectionF64.OverlapF64(
        start = start,
        end = end,
        firstRange = startT..endT,
        secondRange = min(secondStart, secondEnd)..max(secondStart, secondEnd),
        firstStartParameter = startT,
        firstEndParameter = endT,
        secondStartParameter = secondStart,
        secondEndParameter = secondEnd,
    )
}

private fun pathIntersectionIdentityF64(
    component: PathIntersectionComponentF64,
    edges: List<PathInputEdgeF64>,
): PathVertexIdentityF64 {
    val parameters = mutableMapOf<Int, MutableList<Double>>()
    val incidentEdgeIds = mutableSetOf<Int>()
    val originalPoints = mutableListOf<Point2F32>()

    component.incidencesByEdge.entries.sortedBy { entry -> entry.key }.forEach { entry ->
        val edgeIndex = entry.key
        val incidence = entry.value
        val edge = edges[edgeIndex]
        incidentEdgeIds += edge.id
        parameters.getOrPut(edge.id) { mutableListOf() } += incidence.parameter
        val endpointIdentity = when (incidence.parameter) {
            0.0 -> edge.startIdentity
            1.0 -> edge.endIdentity
            else -> null
        }
        if (endpointIdentity != null) {
            incidentEdgeIds += endpointIdentity.incidentEdgeIds
            endpointIdentity.parameterByEdgeId.forEach { (edgeId, parameter) ->
                parameters.getOrPut(edgeId) { mutableListOf() } += parameter
            }
            endpointIdentity.originalPointF32?.let(originalPoints::add)
        }
    }

    val canonicalParameters = linkedMapOf<Int, Double>()
    parameters.keys.sorted().forEach { edgeId ->
        canonicalParameters[edgeId] = canonicalParameterF64(parameters.getValue(edgeId))
    }
    return PathVertexIdentityF64(
        incidentEdgeIds = incidentEdgeIds.sorted(),
        parameterByEdgeId = canonicalParameters,
        originalPointF32 = originalPoints.minWithOrNull(
            compareBy<Point2F32> { point -> orderedPathFloatingBitsF32(point.x) }
                .thenBy { point -> orderedPathFloatingBitsF32(point.y) },
        ),
    )
}

private fun canonicalPathCutsF64(cuts: List<PathEdgeCutF64>): List<PathEdgeCutF64> = buildList {
    cuts.sortedBy { it.parameter }.groupByEquivalentParametersF64().forEach { equivalentCuts ->
        val intersectionCut = equivalentCuts.firstOrNull { it.isIntersection }
        add(
            PathEdgeCutF64(
                parameter = canonicalParameterF64(equivalentCuts.map { it.parameter }),
                identity = intersectionCut?.identity ?: equivalentCuts.first().identity,
                isIntersection = intersectionCut != null,
                canonicalPoint = intersectionCut?.canonicalPoint,
            ),
        )
    }
}

private fun List<PathEdgeCutF64>.groupByEquivalentParametersF64(): List<List<PathEdgeCutF64>> {
    if (isEmpty()) return emptyList()
    val groups = mutableListOf<MutableList<PathEdgeCutF64>>()
    forEach { cut ->
        val previous = groups.lastOrNull()
        val first = previous?.first()
        val isSameCanonicalCut = first != null && first.isIntersection && cut.isIntersection && first.identity == cut.identity
        if (first != null && samePathParameterF64(first.parameter, cut.parameter) &&
            (isSameCanonicalCut || first.parameter == cut.parameter)
        ) {
            previous += cut
        } else {
            groups += mutableListOf(cut)
        }
    }
    return groups
}

private fun pointAtPathCutF64(edge: PathInputEdgeF64, cut: PathEdgeCutF64): Point2F64 =
    canonicalTopologicalPointF64(cut.canonicalPoint ?: pointAtPathParameterF64(edge, cut.parameter))

private fun canonicalPointF64(first: Point2F64, second: Point2F64): Point2F64 {
    val canonicalFirst = canonicalTopologicalPointF64(first)
    val canonicalSecond = canonicalTopologicalPointF64(second)
    return when {
        canonicalFirst.x < canonicalSecond.x -> canonicalFirst
        canonicalFirst.x > canonicalSecond.x -> canonicalSecond
        canonicalFirst.y <= canonicalSecond.y -> canonicalFirst
        else -> canonicalSecond
    }
}

private fun parameterAtPointF64(point: Point2F64, edge: PathInputEdgeF64): Double {
    return snapParameterF64(unboundedParameterAtPointF64(point, edge).coerceIn(0.0, 1.0))
}

private fun unboundedParameterAtPointF64(point: Point2F64, edge: PathInputEdgeF64): Double {
    val deltaX = edge.end.x - edge.start.x
    val deltaY = edge.end.y - edge.start.y
    return if (abs(deltaX) >= abs(deltaY)) {
        (point.x - edge.start.x) / deltaX
    } else {
        (point.y - edge.start.y) / deltaY
    }
}

private fun pointAtPathParameterF64(edge: PathInputEdgeF64, parameter: Double): Point2F64 =
    canonicalTopologicalPointF64(
        when (parameter) {
            0.0 -> edge.start
            1.0 -> edge.end
            else -> Point2F64(
                edge.start.x + (edge.end.x - edge.start.x) * parameter,
                edge.start.y + (edge.end.y - edge.start.y) * parameter,
            )
        },
    )

private fun canonicalParameterF64(parameters: List<Double>): Double = when {
    parameters.any { it == 0.0 } -> 0.0
    parameters.any { it == 1.0 } -> 1.0
    else -> parameters.minOrNull()!!
}

private fun snapParameterF64(parameter: Double): Double = when {
    samePathParameterF64(parameter, 0.0) -> 0.0
    samePathParameterF64(parameter, 1.0) -> 1.0
    else -> parameter
}

private fun samePathParameterF64(first: Double, second: Double): Boolean =
    PathPredicatesF64.almostEqualUlps(first, second, maxUlps = 16, nearZeroMaxUlps = 0)

private fun crossF64(firstX: Double, firstY: Double, secondX: Double, secondY: Double): Double =
    firstX * secondY - firstY * secondX
