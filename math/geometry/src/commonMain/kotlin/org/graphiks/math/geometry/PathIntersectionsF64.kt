package org.graphiks.math.geometry

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal enum class PathOperand { FIRST, SECOND }

internal data class PathVertexIdentityF64(
    val incidentEdgeIds: List<Int>,
    val parameterByEdgeId: Map<Int, Double>,
    val originalPointF32: Point2F32?,
    // Identity consumers may need a temporary representation-local namespace.  It is part of
    // equality so legacy adapter vertices can never alias source endpoint identities merely
    // because their old integer tuples happen to agree.
    val namespaceI32: Int = 0,
    val identityScopeI64: Long = 0L,
)

/** Structural source primitive key; no projected coordinate participates in this identity. */
internal data class PathSourceSegmentKeyF64F32(
    val operand: PathOperand,
    val contourIndexI32: Int,
    val sourceSegmentIndexI32: Int,
)

/**
 * One locally observed F32-collapse interval of a self-closing source primitive.
 *
 * The point embedding that discovered the interval is intentionally not retained as an identity:
 * later selection checks use only this source key and the exact F64 parameter interval.  Keeping
 * the interval operand-local prevents a second operand or a wider common normalization from
 * making an otherwise unrepresentable primitive appear proved representable.
 */
internal data class PathOperandLocalCollapsedSectionF64F32(
    val sourceSegmentKeyF64F32: PathSourceSegmentKeyF64F32,
    val startParameterF64: Double,
    val endParameterF64: Double,
)

internal data class PathInputEdgeF64(
    val idI32: Int,
    val operand: PathOperand,
    val contourIndexI32: Int,
    val sourceSegmentIndexI32: Int,
    val sourceStartParameterF64: Double,
    val sourceEndParameterF64: Double,
    val startIdentityF64: PathVertexIdentityF64,
    val endIdentityF64: PathVertexIdentityF64,
    val startPointF64: Point2F64,
    val endPointF64: Point2F64,
    val windingDeltaI32: Int,
    /**
     * Set only by the public-path flattener after it has proved that this edge belongs to one
     * complete, source-authoritative self-closing primitive.  It is provenance, not a geometric
     * equality key: the n-way fast path still asks the exact intersection kernel to prove every
     * overlap before it suppresses a broad-phase carrier.
     */
    val isSelfClosedSourcePrimitiveF64: Boolean = false,
)

internal val PathInputEdgeF64.id: Int get() = idI32
internal val PathInputEdgeF64.contourIndex: Int get() = contourIndexI32
internal val PathInputEdgeF64.startIdentity: PathVertexIdentityF64 get() = startIdentityF64
internal val PathInputEdgeF64.endIdentity: PathVertexIdentityF64 get() = endIdentityF64
internal val PathInputEdgeF64.start: Point2F64 get() = startPointF64
internal val PathInputEdgeF64.end: Point2F64 get() = endPointF64
internal val PathInputEdgeF64.windingDelta: Int get() = windingDeltaI32

internal fun sourceParameterAtEdgeCutF64(
    edgeF64: PathInputEdgeF64,
    edgeParameterF64: Double,
): Double = edgeF64.sourceStartParameterF64 +
    (edgeF64.sourceEndParameterF64 - edgeF64.sourceStartParameterF64) * edgeParameterF64

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

private fun comparePathTopologicalCoordinateF64(firstF64: Double, secondF64: Double): Int = when {
    firstF64 == secondF64 -> 0
    firstF64 < secondF64 -> -1
    else -> 1
}

// One operation owns this work budget across its broad phase and intersection registry.  Every
// candidate-facing action consumes before it executes, so a dense AABB workload cannot evade the
// same deterministic `path-candidate-limit` that bounds the registry's profile work.
internal class PathCandidateWorkBudgetI32(maxCandidateProbes: Int) {
    // The public bound is I32, but every derived debit is I64.  Keeping the running balance in
    // I64 prevents an unchecked `Long.toInt()` from turning a very large preflight into a credit.
    private var remainingI64: Long = maxCandidateProbes.toLong()

    init {
        require(maxCandidateProbes > 0)
    }

    fun consume() {
        consumePreflightI64(1L)
    }

    // A canonical linear pass may prove its complete per-pass debit before beginning.  This is
    // a check only: every real operation still calls [consume] immediately before it executes.
    fun requireRemainingAtLeast(units: Long) {
        if (units < 0L || units > remainingI64) throw IllegalStateException("path-candidate-limit")
    }

    /**
     * Debits a statically derived unit count before a deterministic pass begins.  In particular,
     * callers must use this for a sort's worst-case comparison bound instead of charging from a
     * JVM- or JS-specific comparator callback.
     */
    fun consumePreflightI64(unitsI64: Long) {
        requireRemainingAtLeast(unitsI64)
        remainingI64 -= unitsI64
    }

}

/** Checked non-negative work arithmetic shared by the hybrid preflight passes. */
internal fun checkedPathWorkAddI64(firstI64: Long, secondI64: Long): Long {
    if (firstI64 < 0L || secondI64 < 0L || firstI64 > Long.MAX_VALUE - secondI64) {
        throw IllegalStateException("path-candidate-limit")
    }
    return firstI64 + secondI64
}

/** Checked non-negative work arithmetic shared by the hybrid preflight passes. */
internal fun checkedPathWorkMultiplyI64(firstI64: Long, secondI64: Long): Long {
    if (
        firstI64 < 0L || secondI64 < 0L ||
            (firstI64 != 0L && secondI64 > Long.MAX_VALUE / firstI64)
    ) {
        throw IllegalStateException("path-candidate-limit")
    }
    return firstI64 * secondI64
}

/** Converts a checked canonical collection count only after its public limit was applied. */
internal fun checkedPathCapacityI32(countI64: Long, errorMessage: String): Int {
    if (countI64 < 0L || countI64 > Int.MAX_VALUE.toLong()) throw IllegalStateException(errorMessage)
    return countI64.toInt()
}

private fun canonicalTopologicalPathInputEdgeF64(edge: PathInputEdgeF64): PathInputEdgeF64 = edge.copy(
    startPointF64 = canonicalTopologicalPointF64(edge.start),
    endPointF64 = canonicalTopologicalPointF64(edge.end),
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
    val contourIndexI32: Int = 0,
    val sourceSegmentIndexI32: Int = -1,
    val sourceStartParameterF64: Double = 0.0,
    val sourceEndParameterF64: Double = 1.0,
    val startIsExactEventF64: Boolean = false,
    val endIsExactEventF64: Boolean = false,
    val startIdentity: PathVertexIdentityF64,
    val endIdentity: PathVertexIdentityF64,
    val start: Point2F64,
    val end: Point2F64,
    /** Exact evaluation at this carrier incidence; distinct from the canonical source vertex. */
    val startIncidencePointF64: Point2F64 = start,
    /** Exact evaluation at this carrier incidence; distinct from the canonical source vertex. */
    val endIncidencePointF64: Point2F64 = end,
    val windingDelta: Int,
    val legacySectionProvenanceF64: PathLegacySectionProvenanceF64? = null,
)

// This is the registry export consumed by source topology.  It is deliberately derived from
// the one exact-intersection pass; callers never rerun the pairwise kernel or recover an event
// by comparing coordinates.
internal data class PathRegistryPointContactF64(
    val pointF64: Point2F64,
    val vertexIdentityF64: PathVertexIdentityF64,
)

/** One exact carrier incidence in a canonical overlap component. */
internal data class PathRegistryOverlapIncidenceF64(
    val inputEdgeIdI32: Int,
    // These parameters are paired with the component start/end, not with an arbitrary raw pair.
    val startParameterF64: Double,
    val endParameterF64: Double,
    val startVertexIdentityF64: PathVertexIdentityF64,
    val endVertexIdentityF64: PathVertexIdentityF64,
)

/**
 * A canonical exact overlap component.  The registry aggregates all source-carrier incidences
 * with the same exact interval before source topology asks for identities; it never exports a
 * pairwise contact that downstream code would have to rediscover or join by coordinates.
 */
internal data class PathRegistryOverlapContactF64(
    val startPointF64: Point2F64,
    val endPointF64: Point2F64,
    val incidencesF64: List<PathRegistryOverlapIncidenceF64>,
)

/** The direct registry identity of a cut endpoint; it is never recovered from an ULP window. */
private data class PathRegistryEndpointKeyF64(
    val inputEdgeIdI32: Int,
    val parameterBitsI64: Long,
)

/**
 * The direct cut registry entry for one raw parameter bit-pattern.  A raw pair endpoint is
 * allowed to reach its vertex only through this table; it must never be projected back onto an
 * edge from a coordinate and then matched approximately.
 */
private data class PathRegistryEndpointIdentityF64(
    val vertexIdentityF64: PathVertexIdentityF64,
    val canonicalParameterF64: Double,
    val canonicalPointF64: Point2F64,
)

/** Raw pair evidence retained only until the exact endpoint sweep materializes atomic intervals. */
private data class PathRawOverlapContactF64(
    val firstEdgeIndexI32: Int,
    val secondEdgeIndexI32: Int,
    val firstStartParameterF64: Double,
    val firstEndParameterF64: Double,
    val secondStartParameterF64: Double,
    val secondEndParameterF64: Double,
    val startPointF64: Point2F64,
    val endPointF64: Point2F64,
    // These are direct event tickets.  They retain the raw `(edge ID, parameter bits)` route
    // to the canonical component even if later component merging chooses another equivalent
    // cut parameter.  Atomic overlap materialization must never search a cut list by value.
    val startTicketF64: PathRegistryEndpointTicketF64,
    val endTicketF64: PathRegistryEndpointTicketF64,
)

private class PathRegistryEndpointTicketF64(
    private val componentF64: PathIntersectionComponentF64,
) {
    fun resolvedComponentF64(): PathIntersectionComponentF64 {
        var resolvedF64 = componentF64
        while (resolvedF64.redirectF64 != null) resolvedF64 = resolvedF64.redirectF64!!
        return resolvedF64
    }
}

/**
 * One direct `(edge, parameter bits)` incidence at an event of an exact overlap carrier.
 *
 * These records are made by the source registry's endpoint sweep, before vertex identities or
 * split edges exist.  In particular, an edge which merely spans somebody else's pairwise
 * endpoint receives a real source cut here; no consumer is allowed to reconstruct it from the
 * endpoint coordinates later.
 */
private data class PathAtomicOverlapCarrierIncidenceF64(
    val semanticOrderI32: Int,
    val edgeIndexI32: Int,
    val parameterF64: Double,
)

/** One exact endpoint event, with every carrier incident to that endpoint in semantic order. */
private data class PathAtomicOverlapCarrierEventF64(
    val pointF64: Point2F64,
    val ticketF64: PathRegistryEndpointTicketF64,
    val incidencesF64: List<PathAtomicOverlapCarrierIncidenceF64>,
)

/**
 * Cached output of the source endpoint sweep.  The component is a carrier multiset, never a
 * pairwise contact: adjacent event lists are intersected directly to form atomic intervals.
 */
private data class PathAtomicOverlapCarrierComponentF64(
    val edgeIndicesBySemanticOrderI32: List<Int>,
    val eventsF64: List<PathAtomicOverlapCarrierEventF64>,
    val endpointIncidenceCountI64: Long,
)

/** A proposal is validated globally before it becomes a source-registry incidence or cut. */
private data class PathAtomicOverlapLiftProposalF64(
    val componentF64: PathIntersectionComponentF64,
    val edgeIndexI32: Int,
    val parameterF64: Double,
    val pointF64: Point2F64,
)

private data class PathRawOverlapCarrierGroupF64(
    val contactsF64: List<PathRawOverlapContactF64>,
)

private data class PathAtomicOverlapEventDraftF64(
    val componentF64: PathIntersectionComponentF64,
    val ticketF64: PathRegistryEndpointTicketF64,
    val incidencesF64: MutableList<PathAtomicOverlapCarrierIncidenceF64>,
)

/** One carrier's inclusive endpoint range in an exact atomic-overlap sweep. */
private data class PathOverlapSweepRangeF64(
    val semanticOrderI32: Int,
    val edgeIndexI32: Int,
    val startEndpointIndexI32: Int,
    val endEndpointIndexI32: Int,
)

internal data class PathSplitTopologyF64(
    val splitEdgesF64: List<PathSplitEdgeF64>,
    val pointContactsF64: List<PathRegistryPointContactF64>,
    val overlapContactsF64: List<PathRegistryOverlapContactF64>,
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
): List<PathSplitEdgeF64> = splitPathTopologyF64(edges, limits, candidateWorkBudget).splitEdgesF64

internal fun splitPathTopologyF64(
    edges: List<PathInputEdgeF64>,
    limits: PathOpsLimitsI32,
    candidateWorkBudget: PathCandidateWorkBudgetI32,
    allowSelfClosedNWayCarrierGroupingF64: Boolean = false,
    enforceIntersectionLimit: Boolean = true,
): PathSplitTopologyF64 {
    validatePathInputEdgesF64(edges)
    val canonicalEdges = canonicalPathInputEdgesF64(edges, candidateWorkBudget).map(::canonicalTopologicalPathInputEdgeF64)
    // Source splitting is transient: exact overlaps can produce many source carriers which the
    // hybrid DCEL later aggregates into one canonical rail.  `maxHalfEdges` therefore belongs
    // exclusively to that final DCEL, after aggregation.  A newly admitted interior cut costs
    // candidate work before it is attached, so the public work ceiling is also a deterministic
    // upper bound for transient interior-cut storage.  Check the resulting capacity up-front;
    // this is deliberately not a surrogate half-edge limit.
    val maximumTransientSplitEdgesI64 = checkedPathWorkAddI64(
        canonicalEdges.size.toLong(),
        limits.maxCandidateProbes.toLong(),
    )
    checkedPathCapacityI32(maximumTransientSplitEdgesI64, "path-candidate-limit")
    val registry = PathIntersectionRegistryF64(
        edges = canonicalEdges,
        maxTransientInteriorCutsI32 = limits.maxCandidateProbes,
        candidateWorkBudget = candidateWorkBudget,
    )
    // Only a public flattener-proven self-closing primitive may use this compression.  Generic
    // coincident source edges retain their pair evidence: a semantic sort key is never geometric
    // equality authority, in particular around bounded ULP clusters.
    val candidatePlanF64 = if (allowSelfClosedNWayCarrierGroupingF64) {
        canonicalPathEdgeCandidatePlanF64(canonicalEdges, candidateWorkBudget)
    } else {
        null
    }
    if (candidatePlanF64 == null) {
        // Keep the established generic path byte-for-byte in control flow. It is deliberately
        // separate from the self-closed reduction: generic candidate accounting and ULP profile
        // behavior must not depend on an optional provenance optimization.
        forEachPathEdgeCandidatePairF64(canonicalEdges, candidateWorkBudget) { firstIndex, secondIndex ->
            val first = canonicalEdges[firstIndex]
            val second = canonicalEdges[secondIndex]
            if (pathEdgesShareOnlyKnownNonCollinearEndpointF64(first, second, candidateWorkBudget)) {
                registry.consumeKnownEndpointNoOpCandidateWorkF64()
                return@forEachPathEdgeCandidatePairF64
            }
            candidateWorkBudget.consume()
            when (val intersectionF64 = intersectPathEdgesF64(first, second)) {
                is PathIntersectionF64.PointF64 -> registry.addIntersection(
                    firstIndex,
                    intersectionF64.firstT,
                    secondIndex,
                    intersectionF64.secondT,
                    intersectionF64.point,
                    hasUniqueCarrierIntersection = true,
                )

                is PathIntersectionF64.OverlapF64 -> {
                    val startTicketF64 = registry.addIntersection(
                        firstIndex,
                        intersectionF64.firstStartParameter,
                        secondIndex,
                        intersectionF64.secondStartParameter,
                        intersectionF64.start,
                        hasUniqueCarrierIntersection = false,
                    )
                    val endTicketF64 = registry.addIntersection(
                        firstIndex,
                        intersectionF64.firstEndParameter,
                        secondIndex,
                        intersectionF64.secondEndParameter,
                        intersectionF64.end,
                        hasUniqueCarrierIntersection = false,
                    )
                    registry.addOverlapContactF64(
                        firstEdgeIndex = firstIndex,
                        secondEdgeIndex = secondIndex,
                        intersectionF64 = intersectionF64,
                        startTicketF64 = startTicketF64,
                        endTicketF64 = endTicketF64,
                    )
                }

                null -> Unit
            }
        }
    } else {
        registerCanonicalEqualEdgeOverlapRunsF64(
            candidatePlanF64 = candidatePlanF64,
            registryF64 = registry,
            candidateWorkBudgetI32 = candidateWorkBudget,
        )
        forEachPathEdgeCandidatePairF64(candidatePlanF64.representativeEdgesF64, candidateWorkBudget) {
            firstRepresentativeIndexI32,
            secondRepresentativeIndexI32,
            ->
            val firstIndex = candidatePlanF64.representativeEdgeIndicesI32[firstRepresentativeIndexI32]
            val secondIndex = candidatePlanF64.representativeEdgeIndicesI32[secondRepresentativeIndexI32]
            val first = canonicalEdges[firstIndex]
            val second = canonicalEdges[secondIndex]
            if (pathEdgesShareOnlyKnownNonCollinearEndpointF64(first, second, candidateWorkBudget)) {
                registry.consumeKnownEndpointNoOpCandidateWorkF64()
                return@forEachPathEdgeCandidatePairF64
            }
            candidateWorkBudget.consume()
            val intersectionF64 = intersectPathEdgesF64(first, second) ?: return@forEachPathEdgeCandidatePairF64
            registerCanonicalProxyIntersectionF64(
                candidatePlanF64 = candidatePlanF64,
                registryF64 = registry,
                firstRepresentativeIndexI32 = firstIndex,
                secondRepresentativeIndexI32 = secondIndex,
                intersectionF64 = intersectionF64,
                candidateWorkBudgetI32 = candidateWorkBudget,
            )
        }
    }
    // Pair contacts are only raw evidence.  Before identities/splits are allocated, sweep every
    // exact carrier component and register a direct incidence for each active carrier at each
    // atomic endpoint.  This is the sole authority for overlap cuts downstream.
    registry.materializeAtomicOverlapEndpointIncidencesF64()
    val components = registry.components
    if (enforceIntersectionLimit && components.size > limits.maxIntersections) {
        throw IllegalStateException("path-intersection-limit")
    }

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
                canonicalPointF64 = component.canonicalPoint,
            )
        }
    }

    val canonicalCutsByEdge = cutsByEdge.map(::canonicalPathCutsF64)
    val endpointIdentitiesByRegistryKeyF64 = registry.endpointIdentitiesByRegistryKeyF64(
        identityByComponentF64 = identityByComponent,
        candidateWorkBudgetI32 = candidateWorkBudget,
    )
    var splitEdgeCountI64 = 0L
    canonicalEdges.forEachIndexed { edgeIndex, edge ->
        canonicalCutsByEdge[edgeIndex].zipWithNext().forEach { (startCut, endCut) ->
            val start = pointAtPathCutF64(edge, startCut)
            val end = pointAtPathCutF64(edge, endCut)
            if (!sameTopologicalPointF64(start, end)) {
                splitEdgeCountI64 = checkedPathWorkAddI64(splitEdgeCountI64, 1L)
                if (splitEdgeCountI64 > maximumTransientSplitEdgesI64) {
                    throw IllegalStateException("path-candidate-limit")
                }
            }
        }
    }

    // Each retained split evaluates both carrier-local endpoints for the hybrid representative
    // route.  Reserve that deterministic work from the final split count before the output
    // allocation; source topology itself continues to expose only canonical event geometry.
    candidateWorkBudget.consumePreflightI64(
        checkedPathWorkMultiplyI64(splitEdgeCountI64, 2L),
    )

    val splitEdgesF64 = ArrayList<PathSplitEdgeF64>(
        checkedPathCapacityI32(splitEdgeCountI64, "path-candidate-limit"),
    ).also { splitEdges ->
        canonicalEdges.forEachIndexed { edgeIndex, edge ->
            canonicalCutsByEdge[edgeIndex].zipWithNext().forEach { (startCut, endCut) ->
                val start = pointAtPathCutF64(edge, startCut)
                val end = pointAtPathCutF64(edge, endCut)
                if (sameTopologicalPointF64(start, end)) return@forEach
                splitEdges +=
                    PathSplitEdgeF64(
                        sourceId = edge.id,
                        operand = edge.operand,
                        contourIndexI32 = edge.contourIndex,
                        sourceSegmentIndexI32 = edge.sourceSegmentIndexI32,
                        sourceStartParameterF64 = sourceParameterAtEdgeCutF64(edge, startCut.parameter),
                        sourceEndParameterF64 = sourceParameterAtEdgeCutF64(edge, endCut.parameter),
                        startIsExactEventF64 = startCut.isIntersection,
                        endIsExactEventF64 = endCut.isIntersection,
                        startIdentity = startCut.identity,
                        endIdentity = endCut.identity,
                        start = start,
                        end = end,
                        startIncidencePointF64 = incidencePointAtPathCutF64(edge, startCut),
                        endIncidencePointF64 = incidencePointAtPathCutF64(edge, endCut),
                        windingDelta = edge.windingDelta,
                    )
            }
    }
    }
    candidateWorkBudget.consumePreflightI64(components.size.toLong())
    val pointContactInputsF64 = components.map { component ->
        candidateWorkBudget.consume()
        PathRegistryPointContactF64(
            pointF64 = component.canonicalPoint,
            vertexIdentityF64 = identityByComponent.getValue(component),
        )
    }
    val pointContactsF64 = sortedRegistryF64(pointContactInputsF64, candidateWorkBudget) { first, second ->
        comparePathTopologicalCoordinateF64(first.pointF64.x, second.pointF64.x).takeIf { it != 0 }
            ?: comparePathTopologicalCoordinateF64(first.pointF64.y, second.pointF64.y)
    }
    val overlapContactsF64 = registry.overlapContactsF64(endpointIdentitiesByRegistryKeyF64)
    return PathSplitTopologyF64(
        splitEdgesF64 = splitEdgesF64,
        pointContactsF64 = pointContactsF64,
        overlapContactsF64 = overlapContactsF64,
    )
}

/**
 * A semantic comparator may return zero only as a candidate grouping hint. The plan below is
 * available exclusively to flattened public input that already proved source-primitive closure.
 * It then validates every member against the robust intersection kernel before reducing its
 * broad-phase representation. If any member fails structural or complete-overlap validation the
 * entire run remains independent; semantic ordering never becomes a geometric equality authority.
 */
private data class PathCanonicalEdgeCandidatePlanF64(
    val representativeEdgeIndicesI32: List<Int>,
    val representativeEdgesF64: List<PathInputEdgeF64>,
    /** The first canonical index of a validated complete-overlap run, or this index itself. */
    val overlapRunStartByEdgeI32: IntArray,
    /** Non-null only for a validated non-first member of an exact complete-overlap run. */
    val overlapByMemberEdgeI32: Array<PathIntersectionF64.OverlapF64?>,
    /** Contiguous exact members retained by each broad-phase proxy representative. */
    val memberRangeStartByRepresentativeI32: IntArray,
    /** Exclusive end of [memberRangeStartByRepresentativeI32]'s exact-member range. */
    val memberRangeEndExclusiveByRepresentativeI32: IntArray,
)

private fun canonicalPathEdgeCandidatePlanF64(
    canonicalEdgesF64: List<PathInputEdgeF64>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): PathCanonicalEdgeCandidatePlanF64 {
    val edgeCountI32 = canonicalEdgesF64.size
    if (edgeCountI32 == 0) {
        return PathCanonicalEdgeCandidatePlanF64(
            representativeEdgeIndicesI32 = emptyList(),
            representativeEdgesF64 = emptyList(),
            overlapRunStartByEdgeI32 = IntArray(0),
            overlapByMemberEdgeI32 = emptyArray(),
            memberRangeStartByRepresentativeI32 = IntArray(0),
            memberRangeEndExclusiveByRepresentativeI32 = IntArray(0),
        )
    }
    // The complete scan, candidate validation and fixed-size indexing are all debited before
    // their allocations/predicates. This is linear in input carriers, not in duplicate pairs.
    candidateWorkBudgetI32.consumePreflightI64(
        checkedPathWorkMultiplyI64(edgeCountI32.toLong(), 7L),
    )
    val overlapRunStartByEdgeI32 = IntArray(edgeCountI32) { it }
    val overlapByMemberEdgeI32 = arrayOfNulls<PathIntersectionF64.OverlapF64>(edgeCountI32)
    val memberRangeStartByRepresentativeI32 = IntArray(edgeCountI32) { it }
    val memberRangeEndExclusiveByRepresentativeI32 = IntArray(edgeCountI32) { indexI32 -> indexI32 + 1 }
    var duplicateMemberCountI64 = 0L
    var groupStartI32 = 0
    while (groupStartI32 < edgeCountI32) {
        var groupEndI32 = groupStartI32 + 1
        while (
            groupEndI32 < edgeCountI32 &&
                comparePathInputEdgesSemanticallyF64(
                    canonicalEdgesF64[groupStartI32],
                    canonicalEdgesF64[groupEndI32],
                ) == 0
        ) {
            groupEndI32 += 1
        }
        if (
            isSelfClosedDuplicateCarrierRunF64(
                canonicalEdgesF64,
                groupStartI32,
                groupEndI32,
                candidateWorkBudgetI32,
            )
        ) {
            duplicateMemberCountI64 = checkedPathWorkAddI64(
                duplicateMemberCountI64,
                (groupEndI32 - groupStartI32 - 1).toLong(),
            )
            // The leader is selected only after structural membership and exact geometry have
            // been established.  Its source ordinal navigates a proven automorphism class; it
            // never decides whether two carriers are geometrically equal.
            val leaderIndexI32 = canonicalSelfClosedDuplicateCarrierLeaderF64(
                canonicalEdgesF64 = canonicalEdgesF64,
                groupStartI32 = groupStartI32,
                groupEndI32 = groupEndI32,
            )
            var validCompleteOverlap = true
            for (memberIndexI32 in groupStartI32 until groupEndI32) {
                if (memberIndexI32 == leaderIndexI32) continue
                val overlapF64 = intersectPathEdgesF64(
                    canonicalEdgesF64[leaderIndexI32],
                    canonicalEdgesF64[memberIndexI32],
                )
                if (overlapF64 !is PathIntersectionF64.OverlapF64 ||
                    !isCompleteCanonicalEdgeOverlapF64(
                        canonicalEdgesF64[leaderIndexI32],
                        canonicalEdgesF64[memberIndexI32],
                        overlapF64,
                    )
                ) {
                    validCompleteOverlap = false
                    break
                }
                overlapByMemberEdgeI32[memberIndexI32] = overlapF64
            }
            if (validCompleteOverlap) {
                for (memberIndexI32 in groupStartI32 until groupEndI32) {
                    overlapRunStartByEdgeI32[memberIndexI32] = leaderIndexI32
                }
                memberRangeStartByRepresentativeI32[leaderIndexI32] = groupStartI32
                memberRangeEndExclusiveByRepresentativeI32[leaderIndexI32] = groupEndI32
            } else {
                for (memberIndexI32 in groupStartI32 until groupEndI32) {
                    overlapByMemberEdgeI32[memberIndexI32] = null
                }
            }
        }
        groupStartI32 = groupEndI32
    }
    candidateWorkBudgetI32.consumePreflightI64(
        checkedPathWorkMultiplyI64(duplicateMemberCountI64, 4L),
    )
    var representativeCountI64 = 0L
    overlapRunStartByEdgeI32.indices.forEach { edgeIndexI32 ->
        if (overlapRunStartByEdgeI32[edgeIndexI32] == edgeIndexI32) {
            representativeCountI64 = checkedPathWorkAddI64(representativeCountI64, 1L)
        }
    }
    candidateWorkBudgetI32.consumePreflightI64(
        checkedPathWorkMultiplyI64(representativeCountI64, 2L),
    )
    val representativeEdgeIndicesI32 = ArrayList<Int>(
        checkedPathCapacityI32(representativeCountI64, "path-candidate-limit"),
    )
    overlapRunStartByEdgeI32.indices.forEach { edgeIndexI32 ->
        if (overlapRunStartByEdgeI32[edgeIndexI32] == edgeIndexI32) {
            representativeEdgeIndicesI32 += edgeIndexI32
        }
    }
    val representativeEdgesF64 = ArrayList<PathInputEdgeF64>(representativeEdgeIndicesI32.size)
    representativeEdgeIndicesI32.forEach { edgeIndexI32 ->
        representativeEdgesF64 += canonicalEdgesF64[edgeIndexI32]
    }
    return PathCanonicalEdgeCandidatePlanF64(
        representativeEdgeIndicesI32 = representativeEdgeIndicesI32,
        representativeEdgesF64 = representativeEdgesF64,
        overlapRunStartByEdgeI32 = overlapRunStartByEdgeI32,
        overlapByMemberEdgeI32 = overlapByMemberEdgeI32,
        memberRangeStartByRepresentativeI32 = memberRangeStartByRepresentativeI32,
        memberRangeEndExclusiveByRepresentativeI32 = memberRangeEndExclusiveByRepresentativeI32,
    )
}

/**
 * The flattener marks a carrier only when its primitive began and ended at the same authoritative
 * public endpoint.  A run can be compressed only when every member came from a different such
 * primitive in the same operand and covers exactly the same primitive parameter interval.
 * Contour partitioning is not geometry: equivalent separate contours must retain the same proxy
 * semantics as consecutive primitives. The source fields here restrict provenance traversal;
 * complete geometric equality is still proved below by [intersectPathEdgesF64].
 */
private fun isSelfClosedDuplicateCarrierRunF64(
    canonicalEdgesF64: List<PathInputEdgeF64>,
    groupStartI32: Int,
    groupEndI32: Int,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): Boolean {
    if (groupEndI32 - groupStartI32 < 2) return false
    val firstEdgeF64 = canonicalEdgesF64[groupStartI32]
    if (!firstEdgeF64.isSelfClosedSourcePrimitiveF64 || firstEdgeF64.sourceSegmentIndexI32 < 0) return false
    // Source-segment uniqueness is a structural prerequisite for a proxy group. Its explicit
    // sort is deterministic O(N log N) work, so reserve both the transient array and all worst-
    // case comparisons before inspecting or mutating it.
    val sourceSegmentCountI32 = groupEndI32 - groupStartI32
    candidateWorkBudgetI32.consumePreflightI64(
        checkedPathWorkAddI64(
            sourceSegmentCountI32.toLong(),
            registrySortCostI64F32(sourceSegmentCountI32),
        ),
    )
    val sourceSegmentsI32 = IntArray(sourceSegmentCountI32)
    for (offsetI32 in sourceSegmentsI32.indices) {
        val edgeF64 = canonicalEdgesF64[groupStartI32 + offsetI32]
        if (
            !edgeF64.isSelfClosedSourcePrimitiveF64 ||
                edgeF64.operand != firstEdgeF64.operand ||
                edgeF64.sourceSegmentIndexI32 < 0 ||
                !sameStructuralPathParameterF64(edgeF64.sourceStartParameterF64, firstEdgeF64.sourceStartParameterF64) ||
                !sameStructuralPathParameterF64(edgeF64.sourceEndParameterF64, firstEdgeF64.sourceEndParameterF64)
        ) {
            return false
        }
        sourceSegmentsI32[offsetI32] = edgeF64.sourceSegmentIndexI32
    }
    sourceSegmentsI32.sort()
    for (indexI32 in 1 until sourceSegmentsI32.size) {
        if (sourceSegmentsI32[indexI32 - 1] == sourceSegmentsI32[indexI32]) return false
    }
    return true
}

private fun canonicalSelfClosedDuplicateCarrierLeaderF64(
    canonicalEdgesF64: List<PathInputEdgeF64>,
    groupStartI32: Int,
    groupEndI32: Int,
): Int {
    var leaderIndexI32 = groupStartI32
    for (memberIndexI32 in groupStartI32 + 1 until groupEndI32) {
        val comparisonI32 = compareSelfClosedDuplicateCarrierProvenanceF64(
            canonicalEdgesF64[memberIndexI32],
            canonicalEdgesF64[leaderIndexI32],
        )
        if (comparisonI32 < 0) leaderIndexI32 = memberIndexI32
    }
    return leaderIndexI32
}

private fun compareSelfClosedDuplicateCarrierProvenanceF64(
    firstF64: PathInputEdgeF64,
    secondF64: PathInputEdgeF64,
): Int = firstF64.sourceSegmentIndexI32.compareTo(secondF64.sourceSegmentIndexI32)
    .takeIf { it != 0 }
    ?: structuralPathParameterBitsF64(firstF64.sourceStartParameterF64)
        .compareTo(structuralPathParameterBitsF64(secondF64.sourceStartParameterF64))
        .takeIf { it != 0 }
    ?: structuralPathParameterBitsF64(firstF64.sourceEndParameterF64)
        .compareTo(structuralPathParameterBitsF64(secondF64.sourceEndParameterF64))

private fun sameStructuralPathParameterF64(firstF64: Double, secondF64: Double): Boolean =
    structuralPathParameterBitsF64(firstF64) == structuralPathParameterBitsF64(secondF64)

private fun structuralPathParameterBitsF64(parameterF64: Double): Long =
    if (parameterF64 == 0.0) 0L else parameterF64.toRawBits()

private fun isCompleteCanonicalEdgeOverlapF64(
    firstEdgeF64: PathInputEdgeF64,
    secondEdgeF64: PathInputEdgeF64,
    overlapF64: PathIntersectionF64.OverlapF64,
): Boolean =
    overlapF64.firstStartParameter == 0.0 &&
        overlapF64.firstEndParameter == 1.0 &&
        overlapF64.secondStartParameter == 0.0 &&
        overlapF64.secondEndParameter == 1.0 &&
        sameTopologicalPointF64(overlapF64.start, firstEdgeF64.start) &&
        sameTopologicalPointF64(overlapF64.end, firstEdgeF64.end) &&
        sameTopologicalPointF64(overlapF64.start, secondEdgeF64.start) &&
        sameTopologicalPointF64(overlapF64.end, secondEdgeF64.end)

private fun registerCanonicalEqualEdgeOverlapRunsF64(
    candidatePlanF64: PathCanonicalEdgeCandidatePlanF64,
    registryF64: PathIntersectionRegistryF64,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
) {
    // Registering one star relation per duplicate is the complete n-way connectivity proof and
    // replaces the discarded pairwise relations. Each relation and every registry sub-step debit
    // immediately before its own mutation; no unchecked group-level allocation is introduced.
    candidatePlanF64.overlapRunStartByEdgeI32.indices.forEach { memberIndexI32 ->
        val firstIndexI32 = candidatePlanF64.overlapRunStartByEdgeI32[memberIndexI32]
        if (firstIndexI32 == memberIndexI32) return@forEach
        val overlapF64 = candidatePlanF64.overlapByMemberEdgeI32[memberIndexI32]
            ?: throw IllegalStateException("path-arrangement-inconsistent")
        candidateWorkBudgetI32.consume()
        registerPathIntersectionF64(
            registryF64 = registryF64,
            firstIndexI32 = firstIndexI32,
            secondIndexI32 = memberIndexI32,
            intersectionF64 = overlapF64,
        )
    }
}

/**
 * A broad-phase proxy represents a fully proved exact-overlap run, not a replacement source
 * carrier. Once its leader meets another proxy, attach that already-proved point/overlap event
 * to every concrete member pair before the registry can make components, identities, or splits.
 * Member ranges are contiguous semantic runs and every member was independently checked against
 * its leader during plan construction; no coordinate grouping is introduced here.
 */
private fun registerCanonicalProxyIntersectionF64(
    candidatePlanF64: PathCanonicalEdgeCandidatePlanF64,
    registryF64: PathIntersectionRegistryF64,
    firstRepresentativeIndexI32: Int,
    secondRepresentativeIndexI32: Int,
    intersectionF64: PathIntersectionF64,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
) {
    val firstMemberStartI32 = candidatePlanF64.memberRangeStartByRepresentativeI32[firstRepresentativeIndexI32]
    val firstMemberEndExclusiveI32 = candidatePlanF64.memberRangeEndExclusiveByRepresentativeI32[firstRepresentativeIndexI32]
    val secondMemberStartI32 = candidatePlanF64.memberRangeStartByRepresentativeI32[secondRepresentativeIndexI32]
    val secondMemberEndExclusiveI32 = candidatePlanF64.memberRangeEndExclusiveByRepresentativeI32[secondRepresentativeIndexI32]
    val edgeCountI32 = candidatePlanF64.overlapRunStartByEdgeI32.size
    if (
        firstMemberStartI32 < 0 || firstMemberStartI32 >= firstMemberEndExclusiveI32 ||
            firstMemberEndExclusiveI32 > edgeCountI32 ||
            secondMemberStartI32 < 0 || secondMemberStartI32 >= secondMemberEndExclusiveI32 ||
            secondMemberEndExclusiveI32 > edgeCountI32
    ) {
        throw IllegalStateException("path-arrangement-inconsistent")
    }
    val memberPairCountI64 = checkedPathWorkMultiplyI64(
        (firstMemberEndExclusiveI32 - firstMemberStartI32).toLong(),
        (secondMemberEndExclusiveI32 - secondMemberStartI32).toLong(),
    )
    // A leader kernel classification was charged by the caller. This preflight is the distinct
    // propagation dispatch: it occurs before any registry allocation/mutation and includes each
    // actual source-pair attachment exactly once.
    candidateWorkBudgetI32.consumePreflightI64(memberPairCountI64)
    for (firstMemberIndexI32 in firstMemberStartI32 until firstMemberEndExclusiveI32) {
        if (candidatePlanF64.overlapRunStartByEdgeI32[firstMemberIndexI32] != firstRepresentativeIndexI32) {
            throw IllegalStateException("path-arrangement-inconsistent")
        }
        for (secondMemberIndexI32 in secondMemberStartI32 until secondMemberEndExclusiveI32) {
            if (candidatePlanF64.overlapRunStartByEdgeI32[secondMemberIndexI32] != secondRepresentativeIndexI32) {
                throw IllegalStateException("path-arrangement-inconsistent")
            }
            registerPathIntersectionF64(
                registryF64 = registryF64,
                firstIndexI32 = firstMemberIndexI32,
                secondIndexI32 = secondMemberIndexI32,
                intersectionF64 = intersectionF64,
            )
        }
    }
}

private fun registerPathIntersectionF64(
    registryF64: PathIntersectionRegistryF64,
    firstIndexI32: Int,
    secondIndexI32: Int,
    intersectionF64: PathIntersectionF64?,
) {
    when (intersectionF64) {
        is PathIntersectionF64.PointF64 -> registryF64.addIntersection(
            firstIndexI32,
            intersectionF64.firstT,
            secondIndexI32,
            intersectionF64.secondT,
            intersectionF64.point,
            hasUniqueCarrierIntersection = true,
        )
        is PathIntersectionF64.OverlapF64 -> {
            val startTicketF64 = registryF64.addIntersection(
                firstIndexI32,
                intersectionF64.firstStartParameter,
                secondIndexI32,
                intersectionF64.secondStartParameter,
                intersectionF64.start,
                hasUniqueCarrierIntersection = false,
            )
            val endTicketF64 = registryF64.addIntersection(
                firstIndexI32,
                intersectionF64.firstEndParameter,
                secondIndexI32,
                intersectionF64.secondEndParameter,
                intersectionF64.end,
                hasUniqueCarrierIntersection = false,
            )
            registryF64.addOverlapContactF64(
                firstEdgeIndex = firstIndexI32,
                secondEdgeIndex = secondIndexI32,
                intersectionF64 = intersectionF64,
                startTicketF64 = startTicketF64,
                endTicketF64 = endTicketF64,
            )
        }
        null -> Unit
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
    val firstEdgeParameterF64: Double,
    val secondPoint: Point2F64,
    val secondIdentity: PathVertexIdentityF64,
    val secondEdgeParameterF64: Double,
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
        PathEndpointRelationF64(first.start, first.startIdentity, 0.0, second.start, second.startIdentity, 0.0),
        PathEndpointRelationF64(first.start, first.startIdentity, 0.0, second.end, second.endIdentity, 1.0),
        PathEndpointRelationF64(first.end, first.endIdentity, 1.0, second.start, second.startIdentity, 0.0),
        PathEndpointRelationF64(first.end, first.endIdentity, 1.0, second.end, second.endIdentity, 1.0),
    ).filter { relation -> sameTopologicalPointF64(relation.firstPoint, relation.secondPoint) }
    if (concreteEndpointRelations.size != 1) return false
    val relation = concreteEndpointRelations.single()
    if (relation.firstIdentity != relation.secondIdentity) return false
    if (
        OrientationPredicateF64.sign(first.start, first.end, second.start) != 0 ||
            OrientationPredicateF64.sign(first.start, first.end, second.end) != 0
    ) {
        return true
    }
    return pathEdgesMeetAtAFlatteningSubdivisionOnlyF64(first, second, relation, candidateWorkBudget)
}

private fun pathEdgesMeetAtAFlatteningSubdivisionOnlyF64(
    firstF64: PathInputEdgeF64,
    secondF64: PathInputEdgeF64,
    relationF64: PathEndpointRelationF64,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): Boolean {
    candidateWorkBudgetI32.consume()
    if (
        firstF64.operand != secondF64.operand ||
            firstF64.contourIndexI32 != secondF64.contourIndexI32 ||
            firstF64.sourceSegmentIndexI32 < 0 ||
            firstF64.sourceSegmentIndexI32 != secondF64.sourceSegmentIndexI32
    ) return false
    candidateWorkBudgetI32.consume()
    val contiguousF64 = when {
        relationF64.firstEdgeParameterF64 == 1.0 && relationF64.secondEdgeParameterF64 == 0.0 ->
            samePathParameterF64(firstF64.sourceEndParameterF64, secondF64.sourceStartParameterF64)
        relationF64.firstEdgeParameterF64 == 0.0 && relationF64.secondEdgeParameterF64 == 1.0 ->
            samePathParameterF64(firstF64.sourceStartParameterF64, secondF64.sourceEndParameterF64)
        else -> false
    }
    if (!contiguousF64) return false
    val firstOtherF64 = if (relationF64.firstEdgeParameterF64 == 0.0) firstF64.end else firstF64.start
    val secondOtherF64 = if (relationF64.secondEdgeParameterF64 == 0.0) secondF64.end else secondF64.start
    candidateWorkBudgetI32.consume()
    val firstDeltaX = firstOtherF64.x - relationF64.firstPoint.x
    val firstDeltaY = firstOtherF64.y - relationF64.firstPoint.y
    val secondDeltaX = secondOtherF64.x - relationF64.secondPoint.x
    val secondDeltaY = secondOtherF64.y - relationF64.secondPoint.y
    // A shared endpoint with rays pointing away from one another is a pure subdivision joint.
    // Same-direction collinear rays retain their true overlap in the exact registry.
    return firstDeltaX * secondDeltaX + firstDeltaY * secondDeltaY < 0.0
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

private fun canonicalPathInputEdgesF64(
    edges: List<PathInputEdgeF64>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): List<PathInputEdgeF64> = sortedRegistryF64(edges, candidateWorkBudgetI32, ::comparePathInputEdgesSemanticallyF64)

private fun hasEndpointIdentityF64(identity: PathVertexIdentityF64, edgeId: Int, parameter: Double): Boolean =
    edgeId in identity.incidentEdgeIds && identity.parameterByEdgeId[edgeId]?.let { samePathParameterF64(it, parameter) } == true

private data class PathEdgeCutF64(
    val parameter: Double,
    val identity: PathVertexIdentityF64,
    val isIntersection: Boolean,
    /** Canonical exact event geometry for source-topology continuity only. */
    val canonicalPointF64: Point2F64? = null,
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
    // Raw overlap endpoint tickets may outlive a component merge.  The successor link preserves
    // their direct event route without re-identifying an endpoint from coordinates or a search.
    var redirectF64: PathIntersectionComponentF64? = null
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

private fun canonicalRegistryOverlapContactF64(
    startPointF64: Point2F64,
    endPointF64: Point2F64,
    incidencesF64: List<PathRegistryOverlapIncidenceF64>,
): PathRegistryOverlapContactF64 {
    val start = canonicalTopologicalPointF64(startPointF64)
    val end = canonicalTopologicalPointF64(endPointF64)
    return if (compareRegistryPointsF64(start, end) <= 0) {
        PathRegistryOverlapContactF64(start, end, incidencesF64)
    } else {
        PathRegistryOverlapContactF64(
            startPointF64 = end,
            endPointF64 = start,
            incidencesF64 = incidencesF64.map { incidenceF64 ->
                incidenceF64.copy(
                    startParameterF64 = incidenceF64.endParameterF64,
                    endParameterF64 = incidenceF64.startParameterF64,
                    startVertexIdentityF64 = incidenceF64.endVertexIdentityF64,
                    endVertexIdentityF64 = incidenceF64.startVertexIdentityF64,
                )
            },
        )
    }
}

private fun compareRegistryOverlapComponentsF64(
    firstF64: PathRegistryOverlapContactF64,
    secondF64: PathRegistryOverlapContactF64,
): Int = compareRegistryOverlapGeometryF64(firstF64, secondF64)

private fun compareRegistryOverlapGeometryF64(
    firstF64: PathRegistryOverlapContactF64,
    secondF64: PathRegistryOverlapContactF64,
): Int = compareRegistryPointsF64(firstF64.startPointF64, secondF64.startPointF64)
    .takeIf { it != 0 } ?: compareRegistryPointsF64(firstF64.endPointF64, secondF64.endPointF64)

private fun compareRegistryOverlapIncidencesF64(
    firstF64: PathRegistryOverlapIncidenceF64,
    secondF64: PathRegistryOverlapIncidenceF64,
): Int = comparePathTopologicalCoordinateF64(firstF64.startParameterF64, secondF64.startParameterF64)
    .takeIf { it != 0 } ?: comparePathTopologicalCoordinateF64(firstF64.endParameterF64, secondF64.endParameterF64)

private fun compareRegistryPointsF64(firstF64: Point2F64, secondF64: Point2F64): Int =
    comparePathTopologicalCoordinateF64(firstF64.x, secondF64.x).takeIf { it != 0 }
        ?: comparePathTopologicalCoordinateF64(firstF64.y, secondF64.y)

private fun <T> sortedRegistryF64(
    valuesF64: List<T>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
    compare: (T, T) -> Int,
): List<T> {
    val sizeI32 = valuesF64.size
    candidateWorkBudgetI32.consumePreflightI64(registrySortCostI64F32(sizeI32) + sizeI32.toLong())
    return valuesF64.sortedWith(Comparator(compare))
}

private fun registrySortCostI64F32(sizeI32: Int): Long {
    if (sizeI32 < 2) return 0L
    var widthI64 = 1L
    var levelsI64 = 0L
    val sizeI64 = sizeI32.toLong()
    while (widthI64 < sizeI64) {
        widthI64 = widthI64 shl 1
        levelsI64 += 1L
    }
    return checkedPathWorkMultiplyI64(sizeI64, levelsI64)
}

private class PathIntersectionRegistryF64(
    private val edges: List<PathInputEdgeF64>,
    private val maxTransientInteriorCutsI32: Int,
    private val candidateWorkBudget: PathCandidateWorkBudgetI32,
) {
    private val activeComponentsById = linkedMapOf<Int, PathIntersectionComponentF64>()
    private val rawOverlapContactsF64 = mutableListOf<PathRawOverlapContactF64>()
    // This cache is populated once, after all pair evidence is known and before any source
    // identity/split allocation.  Downstream overlap export reads only these direct tickets.
    private var atomicOverlapCarrierComponentsF64: List<PathAtomicOverlapCarrierComponentF64> = emptyList()
    private var atomicOverlapEndpointIncidenceCountI64: Long = 0L
    private var atomicOverlapEndpointsMaterializedF64 = false
    val components: List<PathIntersectionComponentF64>
        get() {
            candidateWorkBudget.consumePreflightI64(activeComponentsById.size.toLong())
            val valuesF64 = activeComponentsById.values.toList()
            return sortedRegistryF64(valuesF64, candidateWorkBudget, ::comparePathIntersectionComponentsF64)
        }
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

    fun addOverlapContactF64(
        firstEdgeIndex: Int,
        secondEdgeIndex: Int,
        intersectionF64: PathIntersectionF64.OverlapF64,
        startTicketF64: PathRegistryEndpointTicketF64,
        endTicketF64: PathRegistryEndpointTicketF64,
    ) {
        // Pair discovery is evidence only.  The registry materializes a witness after it has
        // swept all exact endpoints of the connected carrier component, so staggered n-way
        // overlap interiors cannot become competing pairwise claims downstream.
        candidateWorkBudget.consumePreflightI64(2L)
        rawOverlapContactsF64 += PathRawOverlapContactF64(
            firstEdgeIndexI32 = firstEdgeIndex,
            secondEdgeIndexI32 = secondEdgeIndex,
            firstStartParameterF64 = intersectionF64.firstStartParameter,
            firstEndParameterF64 = intersectionF64.firstEndParameter,
            secondStartParameterF64 = intersectionF64.secondStartParameter,
            secondEndParameterF64 = intersectionF64.secondEndParameter,
            startPointF64 = canonicalTopologicalPointF64(intersectionF64.start),
            endPointF64 = canonicalTopologicalPointF64(intersectionF64.end),
            startTicketF64 = startTicketF64,
            endTicketF64 = endTicketF64,
        )
    }

    /**
     * Turns pairwise overlap evidence into one exact endpoint sweep per collinear carrier
     * component.  The sweep is deliberately performed while the registry is still mutable:
     * every active carrier is attached to every event before vertex identities, aliases or
     * source splits are created.  Consequently later stages need only direct
     * `(input edge ID, parameter bits)` lookups and can never rediscover an event from a point.
     */
    fun materializeAtomicOverlapEndpointIncidencesF64() {
        if (atomicOverlapEndpointsMaterializedF64) return
        atomicOverlapEndpointsMaterializedF64 = true
        if (rawOverlapContactsF64.isEmpty()) return

        val rawContactCountI64 = rawOverlapContactsF64.size.toLong()
        candidateWorkBudget.consumePreflightI64(
            checkedPathWorkAddI64(
                checkedPathWorkMultiplyI64(rawContactCountI64, 12L),
                checkedPathWorkMultiplyI64(edges.size.toLong(), 2L),
            ),
        )
        val connectedEdgesI32 = PathOverlapDisjointSetI32(edges.size)
        rawOverlapContactsF64.forEach { contactF64 ->
            connectedEdgesI32.union(contactF64.firstEdgeIndexI32, contactF64.secondEdgeIndexI32)
        }
        val contactsByRootI32 = mutableMapOf<Int, MutableList<PathRawOverlapContactF64>>()
        rawOverlapContactsF64.forEach { contactF64 ->
            val rootI32 = connectedEdgesI32.find(contactF64.firstEdgeIndexI32)
            contactsByRootI32.getOrPut(rootI32) { mutableListOf() } += contactF64
        }

        // All candidates are built and validated first.  In particular, an interior-cut limit
        // failure leaves the registry exactly as it was after pair discovery.
        val proposalsF64 = ArrayList<PathAtomicOverlapLiftProposalF64>(
            checkedPathCapacityI32(
                checkedPathWorkMultiplyI64(rawContactCountI64, 4L),
                "path-candidate-limit",
            ),
        )
        val draftsF64 = ArrayList<PathAtomicOverlapCarrierComponentF64>()
        var plannedInteriorCutCountI32 = 0
        var endpointIncidenceCountI64 = 0L

        contactsByRootI32.values.forEach { contactsF64 ->
            val componentContactCountI64 = contactsF64.size.toLong()
            candidateWorkBudget.consumePreflightI64(
                checkedPathWorkAddI64(
                    checkedPathWorkMultiplyI64(componentContactCountI64, 10L),
                    8L,
                ),
            )
            val edgeIndicesI32 = linkedSetOf<Int>()
            val eventsByComponentF64 = linkedMapOf<PathIntersectionComponentF64, PathAtomicOverlapEventDraftF64>()
            fun addEventF64(ticketF64: PathRegistryEndpointTicketF64) {
                val componentF64 = ticketF64.resolvedComponentF64()
                if (componentF64 !in eventsByComponentF64) {
                    eventsByComponentF64[componentF64] = PathAtomicOverlapEventDraftF64(
                        componentF64 = componentF64,
                        ticketF64 = ticketF64,
                        incidencesF64 = mutableListOf(),
                    )
                }
            }
            contactsF64.forEach { contactF64 ->
                edgeIndicesI32 += contactF64.firstEdgeIndexI32
                edgeIndicesI32 += contactF64.secondEdgeIndexI32
                addEventF64(contactF64.startTicketF64)
                addEventF64(contactF64.endTicketF64)
            }
            val edgesBySemanticOrderF64 = sortedRegistryF64(
                edgeIndicesI32.map { edgeIndexI32 -> edgeIndexI32 to edges[edgeIndexI32] },
                candidateWorkBudget,
            ) { firstF64, secondF64 ->
                comparePathInputEdgesSemanticallyF64(firstF64.second, secondF64.second)
            }
            val edgeMembershipI32 = edgeIndicesI32.toSet()
            val eventsF64 = sortedRegistryF64(
                eventsByComponentF64.values.toList(),
                candidateWorkBudget,
            ) { firstF64, secondF64 ->
                compareRegistryPointsF64(firstF64.componentF64.canonicalPoint, secondF64.componentF64.canonicalPoint)
            }
            if (eventsF64.size < 2 || edgesBySemanticOrderF64.size < 2) return@forEach

            // Build inclusive ranges by scanning only incidences present at each raw event.  The
            // old implementation tested every interval against every edge here; this is O(S+K)
            // before the AVL event sweep below, never an interval×carrier filter.
            candidateWorkBudget.consumePreflightI64(
                checkedPathWorkAddI64(
                    checkedPathWorkMultiplyI64(componentContactCountI64, 12L),
                    checkedPathWorkMultiplyI64(eventsF64.size.toLong(), 4L),
                ),
            )
            val firstEndpointIndexByEdgeI32 = mutableMapOf<Int, Int>()
            val lastEndpointIndexByEdgeI32 = mutableMapOf<Int, Int>()
            eventsF64.forEachIndexed { eventIndexI32, eventF64 ->
                eventF64.componentF64.incidencesByEdge.forEach { (edgeIndexI32, _) ->
                    if (edgeIndexI32 !in edgeMembershipI32) return@forEach
                    if (edgeIndexI32 !in firstEndpointIndexByEdgeI32) {
                        firstEndpointIndexByEdgeI32[edgeIndexI32] = eventIndexI32
                    }
                    lastEndpointIndexByEdgeI32[edgeIndexI32] = eventIndexI32
                }
            }
            val sweepRangesF64 = ArrayList<PathOverlapSweepRangeF64>(edgesBySemanticOrderF64.size)
            edgesBySemanticOrderF64.forEachIndexed { semanticOrderI32, (edgeIndexI32, _) ->
                val startEndpointIndexI32 = firstEndpointIndexByEdgeI32[edgeIndexI32]
                    ?: throw IllegalStateException("path-arrangement-inconsistent")
                val endEndpointIndexI32 = lastEndpointIndexByEdgeI32[edgeIndexI32]
                    ?: throw IllegalStateException("path-arrangement-inconsistent")
                if (startEndpointIndexI32 >= endEndpointIndexI32) {
                    throw IllegalStateException("path-arrangement-inconsistent")
                }
                sweepRangesF64 += PathOverlapSweepRangeF64(
                    semanticOrderI32 = semanticOrderI32,
                    edgeIndexI32 = edgeIndexI32,
                    startEndpointIndexI32 = startEndpointIndexI32,
                    endEndpointIndexI32 = endEndpointIndexI32,
                )
            }
            candidateWorkBudget.consumePreflightI64(
                checkedPathWorkAddI64(
                    checkedPathWorkMultiplyI64(eventsF64.size.toLong(), 5L),
                    checkedPathWorkMultiplyI64(sweepRangesF64.size.toLong(), 8L),
                ),
            )
            val startsByEndpointI32 = Array(eventsF64.size) { mutableListOf<PathOverlapSweepRangeF64>() }
            val endsByEndpointI32 = Array(eventsF64.size) { mutableListOf<PathOverlapSweepRangeF64>() }
            sweepRangesF64.forEach { rangeF64 ->
                startsByEndpointI32[rangeF64.startEndpointIndexI32] += rangeF64
                endsByEndpointI32[rangeF64.endEndpointIndexI32] += rangeF64
            }

            // Count every emitted event incidence before tree allocation/mutation.  This is K
            // for the active incidence multiset and is independent of JVM/JS sort callbacks.
            var activeCountI32 = 0
            var componentEndpointIncidenceCountI64 = 0L
            eventsF64.indices.forEach { eventIndexI32 ->
                activeCountI32 += startsByEndpointI32[eventIndexI32].size
                componentEndpointIncidenceCountI64 = checkedPathWorkAddI64(
                    componentEndpointIncidenceCountI64,
                    activeCountI32.toLong(),
                )
                activeCountI32 -= endsByEndpointI32[eventIndexI32].size
                if (activeCountI32 < 0) throw IllegalStateException("path-arrangement-inconsistent")
            }
            if (activeCountI32 != 0) throw IllegalStateException("path-arrangement-inconsistent")
            endpointIncidenceCountI64 = checkedPathWorkAddI64(
                endpointIncidenceCountI64,
                componentEndpointIncidenceCountI64,
            )
            val treeOperationCostI64 = overlapSweepTreeOperationCostI64F32(edgesBySemanticOrderF64.size)
            candidateWorkBudget.consumePreflightI64(
                checkedPathWorkAddI64(
                    checkedPathWorkMultiplyI64(
                        checkedPathWorkMultiplyI64(sweepRangesF64.size.toLong(), 2L),
                        treeOperationCostI64,
                    ),
                    checkedPathWorkMultiplyI64(componentEndpointIncidenceCountI64, 10L),
                ),
            )

            val activeBySemanticOrderI32 = PathOverlapActiveSetI32()
            eventsF64.forEachIndexed { eventIndexI32, eventF64 ->
                startsByEndpointI32[eventIndexI32].forEach { rangeF64 ->
                    activeBySemanticOrderI32.add(rangeF64.semanticOrderI32)
                }
                activeBySemanticOrderI32.forEachInOrder { semanticOrderI32 ->
                    val (edgeIndexI32, edgeF64) = edgesBySemanticOrderF64[semanticOrderI32]
                    val existingIncidenceF64 = eventF64.componentF64.incidencesByEdge[edgeIndexI32]
                    val parameterF64 = existingIncidenceF64?.parameter ?: exactOverlapLiftParameterF64(
                        pointF64 = eventF64.componentF64.canonicalPoint,
                        edgeF64 = edgeF64,
                    )
                    if (existingIncidenceF64 == null) {
                        proposalsF64 += PathAtomicOverlapLiftProposalF64(
                            componentF64 = eventF64.componentF64,
                            edgeIndexI32 = edgeIndexI32,
                            parameterF64 = parameterF64,
                            pointF64 = eventF64.componentF64.canonicalPoint,
                        )
                        if (isInteriorCutF64(edgeIndexI32, parameterF64)) plannedInteriorCutCountI32 += 1
                    }
                    eventF64.incidencesF64 += PathAtomicOverlapCarrierIncidenceF64(
                        semanticOrderI32 = semanticOrderI32,
                        edgeIndexI32 = edgeIndexI32,
                        parameterF64 = parameterF64,
                    )
                }
                endsByEndpointI32[eventIndexI32].forEach { rangeF64 ->
                    activeBySemanticOrderI32.remove(rangeF64.semanticOrderI32)
                }
            }
            if (activeBySemanticOrderI32.sizeI32 != 0) throw IllegalStateException("path-arrangement-inconsistent")
            draftsF64 += PathAtomicOverlapCarrierComponentF64(
                edgeIndicesBySemanticOrderI32 = edgesBySemanticOrderF64.map { it.first },
                eventsF64 = eventsF64.map { eventF64 ->
                    PathAtomicOverlapCarrierEventF64(
                        pointF64 = eventF64.componentF64.canonicalPoint,
                        ticketF64 = eventF64.ticketF64,
                        incidencesF64 = eventF64.incidencesF64,
                    )
                },
                endpointIncidenceCountI64 = componentEndpointIncidenceCountI64,
            )
        }

        ensureInteriorCutCapacityF64(plannedInteriorCutCountI32)
        // All geometric predicates and limit checks above have completed.  These direct index
        // insertions are the single publish phase and are covered by the sweep preflight.
        proposalsF64.forEach { proposalF64 -> attachAtomicOverlapLiftProposalF64(proposalF64) }
        atomicOverlapCarrierComponentsF64 = draftsF64
        atomicOverlapEndpointIncidenceCountI64 = endpointIncidenceCountI64
    }

    /**
     * Lifts an already-proven exact event onto an active collinear carrier.  This is not an
     * identity lookup: the sweep owns both the carrier and the exact event, then records the
     * resulting source parameter bits directly in the registry.  Endpoint values are accepted
     * only by exact topological equality; an interior value is never snapped through an ULP
     * window.
     */
    private fun exactOverlapLiftParameterF64(
        pointF64: Point2F64,
        edgeF64: PathInputEdgeF64,
    ): Double {
        if (!PathPredicatesF64.onSegment(pointF64, edgeF64.start, edgeF64.end)) {
            throw IllegalStateException("path-arrangement-inconsistent")
        }
        return when {
            sameTopologicalPointF64(pointF64, edgeF64.start) -> 0.0
            sameTopologicalPointF64(pointF64, edgeF64.end) -> 1.0
            else -> unboundedParameterAtPointF64(pointF64, edgeF64).also { parameterF64 ->
                if (!parameterF64.isFinite() || parameterF64 <= 0.0 || parameterF64 >= 1.0) {
                    throw IllegalStateException("path-arrangement-inconsistent")
                }
            }
        }
    }

    /** Publish one prevalidated direct active-event incidence without reopening candidate search. */
    private fun attachAtomicOverlapLiftProposalF64(proposalF64: PathAtomicOverlapLiftProposalF64) {
        if (!sameTopologicalPointF64(proposalF64.componentF64.canonicalPoint, proposalF64.pointF64)) {
            throw IllegalStateException("path-arrangement-inconsistent")
        }
        val existingF64 = proposalF64.componentF64.incidencesByEdge[proposalF64.edgeIndexI32]
        if (existingF64 != null) {
            if (mergeDirectPathIncidenceF64(existingF64, proposalF64.parameterF64) == null) {
                throw IllegalStateException("path-arrangement-inconsistent")
            }
            return
        }
        val incidenceF64 = PathComponentIncidenceF64(proposalF64.parameterF64)
        // The sweep preflight has charged this direct AVL/index insertion.  Calling the regular
        // candidate helper here would re-run its dynamic per-candidate debit after validation.
        proposalF64.componentF64.incidencesByEdge[proposalF64.edgeIndexI32] = incidenceF64
        componentsByEdge[proposalF64.edgeIndexI32].add(proposalF64.componentF64, incidenceF64)
        if (isInteriorCutF64(proposalF64.edgeIndexI32, proposalF64.parameterF64)) interiorCutCount += 1
    }

    fun endpointIdentitiesByRegistryKeyF64(
        identityByComponentF64: Map<PathIntersectionComponentF64, PathVertexIdentityF64>,
        candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
    ): Map<PathRegistryEndpointKeyF64, PathRegistryEndpointIdentityF64> {
        if (!atomicOverlapEndpointsMaterializedF64) throw IllegalStateException("path-arrangement-inconsistent")
        candidateWorkBudgetI32.consumePreflightI64(
            checkedPathWorkAddI64(
                checkedPathWorkMultiplyI64(atomicOverlapEndpointIncidenceCountI64, 3L),
                1L,
            ),
        )
        val endpointIdentitiesByRegistryKeyF64 = mutableMapOf<PathRegistryEndpointKeyF64, PathRegistryEndpointIdentityF64>()
        atomicOverlapCarrierComponentsF64.forEach { carrierComponentF64 ->
            carrierComponentF64.eventsF64.forEach { eventF64 ->
                eventF64.incidencesF64.forEach { incidenceF64 ->
                    registerEndpointIdentityF64(
                        endpointIdentitiesByRegistryKeyF64,
                        incidenceF64.edgeIndexI32,
                        incidenceF64.parameterF64,
                        eventF64.ticketF64,
                        identityByComponentF64,
                    )
                }
            }
        }
        return endpointIdentitiesByRegistryKeyF64
    }

    fun overlapContactsF64(
        endpointIdentitiesByRegistryKeyF64: Map<PathRegistryEndpointKeyF64, PathRegistryEndpointIdentityF64>,
    ): List<PathRegistryOverlapContactF64> = atomicRegistryOverlapContactsF64(
        carrierComponentsF64 = atomicOverlapCarrierComponentsF64,
        edgesF64 = edges,
        endpointIdentitiesByRegistryKeyF64 = endpointIdentitiesByRegistryKeyF64,
        candidateWorkBudgetI32 = candidateWorkBudget,
    )

    private fun registerEndpointIdentityF64(
        endpointIdentitiesByRegistryKeyF64: MutableMap<PathRegistryEndpointKeyF64, PathRegistryEndpointIdentityF64>,
        edgeIndexI32: Int,
        rawParameterF64: Double,
        ticketF64: PathRegistryEndpointTicketF64,
        identityByComponentF64: Map<PathIntersectionComponentF64, PathVertexIdentityF64>,
    ) {
        val componentF64 = ticketF64.resolvedComponentF64()
        val identityF64 = identityByComponentF64[componentF64] ?: throw IllegalStateException("path-arrangement-inconsistent")
        val canonicalParameterF64 = componentF64.incidencesByEdge[edgeIndexI32]?.parameter
            ?: throw IllegalStateException("path-arrangement-inconsistent")
        val endpointF64 = PathRegistryEndpointIdentityF64(
            vertexIdentityF64 = identityF64,
            canonicalParameterF64 = canonicalParameterF64,
            canonicalPointF64 = componentF64.canonicalPoint,
        )
        val keyF64 = PathRegistryEndpointKeyF64(
            inputEdgeIdI32 = edges[edgeIndexI32].idI32,
            parameterBitsI64 = registryParameterBitsI64(rawParameterF64),
        )
        val previousF64 = endpointIdentitiesByRegistryKeyF64.put(keyF64, endpointF64)
        if (previousF64 != null && previousF64 != endpointF64) throw IllegalStateException("path-arrangement-inconsistent")
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
    ): PathRegistryEndpointTicketF64 {
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
        val repeatedComponentF64 = repeatedExactConcurrencyComponentOrNullF64(
                firstEdgeIndex = firstEdgeIndex,
                firstParameter = snappedFirstParameter,
                secondEdgeIndex = secondEdgeIndex,
                secondParameter = snappedSecondParameter,
                incoming = incoming,
            )
        if (repeatedComponentF64 != null) return PathRegistryEndpointTicketF64(repeatedComponentF64)

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
        return PathRegistryEndpointTicketF64(commitAtomicProfileF64(eventKey, incoming, accumulator, accepted))
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
    private fun repeatedExactConcurrencyComponentOrNullF64(
        firstEdgeIndex: Int,
        firstParameter: Double,
        secondEdgeIndex: Int,
        secondParameter: Double,
        incoming: PathComponentProfileF64,
    ): PathIntersectionComponentF64? {
        val incomingWitness = incoming.exactWitness ?: return null
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
        val componentF64 = onlyEligible ?: return null
        return componentF64.takeIf {
            !hasAnotherEligible && componentAlreadyRepresentsIncomingF64(componentF64, incoming)
        }
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
    ): PathIntersectionComponentF64 {
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
            return component
        }

        val winner = accepted.minWith(Comparator(::comparePathIntersectionComponentsF64))
        // Exact n-way no-ops have already returned through the preflight above, before an
        // accumulator copy. This fallback retains the same semantic no-op for direct events;
        // its profile work is charged and therefore cannot hide a degree-dependent allocation.
        if (accepted.size == 1 && componentAlreadyRepresentsIncomingF64(winner, incoming)) return winner
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
            if (component !== winner) component.redirectF64 = winner
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
        return winner
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
        if (additionalInteriorCuts > maxTransientInteriorCutsI32 - interiorCutCount) {
            throw IllegalStateException("path-candidate-limit")
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

/**
 * Publishes atomic overlap intervals from the already enriched source sweep.  Two adjacent
 * event incidence multisets are intersected in their canonical semantic order; this is linear
 * in the recorded active incidences and never re-tests every carrier against every interval.
 */
private fun atomicRegistryOverlapContactsF64(
    carrierComponentsF64: List<PathAtomicOverlapCarrierComponentF64>,
    edgesF64: List<PathInputEdgeF64>,
    endpointIdentitiesByRegistryKeyF64: Map<PathRegistryEndpointKeyF64, PathRegistryEndpointIdentityF64>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): List<PathRegistryOverlapContactF64> {
    if (carrierComponentsF64.isEmpty()) return emptyList()
    val atomicContactsF64 = ArrayList<PathRegistryOverlapContactF64>()
    carrierComponentsF64.forEach { carrierComponentF64 ->
        val eventsF64 = carrierComponentF64.eventsF64
        if (eventsF64.size < 2) return@forEach
        // The sweep recorded both endpoint incidence multisets.  Debit their full bounded
        // two-pointer/output pass before allocating any interval result.
        candidateWorkBudgetI32.consumePreflightI64(
            checkedPathWorkAddI64(
                checkedPathWorkMultiplyI64(eventsF64.size.toLong(), 4L),
                checkedPathWorkMultiplyI64(carrierComponentF64.endpointIncidenceCountI64, 8L),
            ),
        )
        var eventIndexI32 = 0
        while (eventIndexI32 < eventsF64.lastIndex) {
            val startEventF64 = eventsF64[eventIndexI32]
            val endEventF64 = eventsF64[eventIndexI32 + 1]
            var startIndexI32 = 0
            var endIndexI32 = 0
            val incidencesF64 = ArrayList<PathRegistryOverlapIncidenceF64>(
                min(startEventF64.incidencesF64.size, endEventF64.incidencesF64.size),
            )
            while (startIndexI32 < startEventF64.incidencesF64.size && endIndexI32 < endEventF64.incidencesF64.size) {
                val startIncidenceF64 = startEventF64.incidencesF64[startIndexI32]
                val endIncidenceF64 = endEventF64.incidencesF64[endIndexI32]
                when {
                    startIncidenceF64.semanticOrderI32 < endIncidenceF64.semanticOrderI32 -> startIndexI32 += 1
                    startIncidenceF64.semanticOrderI32 > endIncidenceF64.semanticOrderI32 -> endIndexI32 += 1
                    else -> {
                        if (startIncidenceF64.edgeIndexI32 != endIncidenceF64.edgeIndexI32) {
                            throw IllegalStateException("path-arrangement-inconsistent")
                        }
                        val edgeF64 = edgesF64[startIncidenceF64.edgeIndexI32]
                        val startIdentityF64 = endpointIdentityForAtomicOverlapF64(
                            edgeF64,
                            startIncidenceF64.parameterF64,
                            endpointIdentitiesByRegistryKeyF64,
                        )
                        val endIdentityF64 = endpointIdentityForAtomicOverlapF64(
                            edgeF64,
                            endIncidenceF64.parameterF64,
                            endpointIdentitiesByRegistryKeyF64,
                        )
                        incidencesF64 += PathRegistryOverlapIncidenceF64(
                            inputEdgeIdI32 = edgeF64.idI32,
                            startParameterF64 = sourceParameterAtEdgeCutF64(
                                edgeF64,
                                startIdentityF64.canonicalParameterF64,
                            ),
                            endParameterF64 = sourceParameterAtEdgeCutF64(
                                edgeF64,
                                endIdentityF64.canonicalParameterF64,
                            ),
                            startVertexIdentityF64 = startIdentityF64.vertexIdentityF64,
                            endVertexIdentityF64 = endIdentityF64.vertexIdentityF64,
                        )
                        startIndexI32 += 1
                        endIndexI32 += 1
                    }
                }
            }
            if (incidencesF64.size >= 2) {
                atomicContactsF64 += canonicalRegistryOverlapContactF64(
                    startPointF64 = startEventF64.pointF64,
                    endPointF64 = endEventF64.pointF64,
                    incidencesF64 = incidencesF64,
                )
            }
            eventIndexI32 += 1
        }
    }
    return sortedRegistryF64(atomicContactsF64, candidateWorkBudgetI32, ::compareRegistryOverlapComponentsF64)
}
private fun endpointIdentityForAtomicOverlapF64(
    edgeF64: PathInputEdgeF64,
    rawParameterF64: Double,
    endpointIdentitiesByRegistryKeyF64: Map<PathRegistryEndpointKeyF64, PathRegistryEndpointIdentityF64>,
): PathRegistryEndpointIdentityF64 = endpointIdentitiesByRegistryKeyF64[
    PathRegistryEndpointKeyF64(edgeF64.idI32, registryParameterBitsI64(rawParameterF64))
] ?: throw IllegalStateException("path-arrangement-inconsistent")

private fun overlapSweepTreeOperationCostI64F32(sizeI32: Int): Long {
    var capacityI64 = 1L
    var levelsI64 = 1L
    val targetI64 = sizeI32.coerceAtLeast(1).toLong()
    while (capacityI64 < targetI64) {
        capacityI64 = checkedPathWorkMultiplyI64(capacityI64, 2L)
        levelsI64 = checkedPathWorkAddI64(levelsI64, 1L)
    }
    // Search, rebalance and the bounded predecessor/successor join.
    return checkedPathWorkAddI64(levelsI64, 3L)
}

private fun registryParameterBitsI64(parameterF64: Double): Long =
    parameterF64.toRawBits()

private class PathOverlapDisjointSetI32(sizeI32: Int) {
    private val parentI32 = IntArray(sizeI32) { indexI32 -> indexI32 }
    private val rankI32 = IntArray(sizeI32)

    fun find(valueI32: Int): Int {
        var rootI32 = valueI32
        while (parentI32[rootI32] != rootI32) rootI32 = parentI32[rootI32]
        var currentI32 = valueI32
        while (parentI32[currentI32] != currentI32) {
            val nextI32 = parentI32[currentI32]
            parentI32[currentI32] = rootI32
            currentI32 = nextI32
        }
        return rootI32
    }

    fun union(firstI32: Int, secondI32: Int) {
        var firstRootI32 = find(firstI32)
        var secondRootI32 = find(secondI32)
        if (firstRootI32 == secondRootI32) return
        if (rankI32[firstRootI32] < rankI32[secondRootI32]) {
            val swapI32 = firstRootI32
            firstRootI32 = secondRootI32
            secondRootI32 = swapI32
        }
        parentI32[secondRootI32] = firstRootI32
        if (rankI32[firstRootI32] == rankI32[secondRootI32]) rankI32[firstRootI32] += 1
    }
}

/**
 * A small deterministic AVL set for the semantic carrier order of one overlap component.
 * There is exactly one insertion and removal per carrier range, so allocation and every tree
 * operation can be preflighted from the component's endpoint counts before the sweep starts.
 */
private class PathOverlapActiveSetI32 {
    private class Node(
        val keyI32: Int,
        var left: Node? = null,
        var right: Node? = null,
        var heightI32: Int = 1,
    )

    private var root: Node? = null
    var sizeI32: Int = 0
        private set

    fun add(keyI32: Int) {
        root = insert(root, keyI32)
    }

    fun remove(keyI32: Int) {
        root = remove(root, keyI32)
    }

    fun forEachInOrder(action: (Int) -> Unit) {
        visitInOrder(root, action)
    }

    private fun insert(node: Node?, keyI32: Int): Node = when {
        node == null -> {
            sizeI32 += 1
            Node(keyI32)
        }
        keyI32 < node.keyI32 -> {
            node.left = insert(node.left, keyI32)
            rebalance(node)
        }
        keyI32 > node.keyI32 -> {
            node.right = insert(node.right, keyI32)
            rebalance(node)
        }
        else -> throw IllegalStateException("path-arrangement-inconsistent")
    }

    private fun remove(node: Node?, keyI32: Int): Node? {
        node ?: throw IllegalStateException("path-arrangement-inconsistent")
        return when {
            keyI32 < node.keyI32 -> {
                node.left = remove(node.left, keyI32)
                rebalance(node)
            }
            keyI32 > node.keyI32 -> {
                node.right = remove(node.right, keyI32)
                rebalance(node)
            }
            else -> {
                sizeI32 -= 1
                join(node.left, node.right)
            }
        }
    }

    private fun join(left: Node?, right: Node?): Node? = when {
        left == null -> right
        right == null -> left
        nodeHeightI32(left) >= nodeHeightI32(right) -> {
            left.right = join(left.right, right)
            rebalance(left)
        }
        else -> {
            right.left = join(left, right.left)
            rebalance(right)
        }
    }

    private fun visitInOrder(node: Node?, action: (Int) -> Unit) {
        node ?: return
        visitInOrder(node.left, action)
        action(node.keyI32)
        visitInOrder(node.right, action)
    }

    private fun rebalance(node: Node): Node {
        node.heightI32 = maxOf(nodeHeightI32(node.left), nodeHeightI32(node.right)) + 1
        val balanceI32 = nodeHeightI32(node.left) - nodeHeightI32(node.right)
        return when {
            balanceI32 > 1 && nodeHeightI32(node.left?.left) >= nodeHeightI32(node.left?.right) -> rotateRight(node)
            balanceI32 > 1 -> {
                node.left = rotateLeft(checkNotNull(node.left))
                rotateRight(node)
            }
            balanceI32 < -1 && nodeHeightI32(node.right?.right) >= nodeHeightI32(node.right?.left) -> rotateLeft(node)
            balanceI32 < -1 -> {
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
        node.heightI32 = maxOf(nodeHeightI32(node.left), nodeHeightI32(node.right)) + 1
        pivot.heightI32 = maxOf(nodeHeightI32(pivot.left), nodeHeightI32(pivot.right)) + 1
        return pivot
    }

    private fun rotateRight(node: Node): Node {
        val pivot = checkNotNull(node.left)
        node.left = pivot.right
        pivot.right = node
        node.heightI32 = maxOf(nodeHeightI32(node.left), nodeHeightI32(node.right)) + 1
        pivot.heightI32 = maxOf(nodeHeightI32(pivot.left), nodeHeightI32(pivot.right)) + 1
        return pivot
    }

    private fun nodeHeightI32(node: Node?): Int = node?.heightI32 ?: 0
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
        // The component is an exact local incidence set.  It is therefore the narrowest place
        // where equivalent F32 payloads may choose an observable signed-zero spelling.  Unlike
        // topology ordering, this selection deliberately retains raw zero bits as the final,
        // deterministic tie-break: neither operand insertion order nor an unrelated source
        // point can choose the emitted payload.
        originalPointF32 = originalPoints.minWithOrNull(::comparePathOriginalPointPayloadF32),
    )
}

private fun comparePathOriginalPointPayloadF32(firstF32: Point2F32, secondF32: Point2F32): Int = when {
    firstF32.x < secondF32.x -> -1
    firstF32.x > secondF32.x -> 1
    firstF32.y < secondF32.y -> -1
    firstF32.y > secondF32.y -> 1
    firstF32.x.toRawBits() != secondF32.x.toRawBits() -> firstF32.x.toRawBits().compareTo(secondF32.x.toRawBits())
    firstF32.y.toRawBits() != secondF32.y.toRawBits() -> firstF32.y.toRawBits().compareTo(secondF32.y.toRawBits())
    else -> 0
}

private fun canonicalPathCutsF64(cuts: List<PathEdgeCutF64>): List<PathEdgeCutF64> = buildList {
    cuts.sortedBy { it.parameter }.groupByEquivalentParametersF64().forEach { equivalentCuts ->
        val intersectionCut = equivalentCuts.firstOrNull { it.isIntersection }
        add(
            PathEdgeCutF64(
                parameter = canonicalParameterF64(equivalentCuts.map { it.parameter }),
                identity = intersectionCut?.identity ?: equivalentCuts.first().identity,
                isIntersection = intersectionCut != null,
                canonicalPointF64 = intersectionCut?.canonicalPointF64,
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
    canonicalTopologicalPointF64(cut.canonicalPointF64 ?: pointAtPathParameterF64(edge, cut.parameter))

private fun incidencePointAtPathCutF64(edge: PathInputEdgeF64, cut: PathEdgeCutF64): Point2F64 =
    canonicalTopologicalPointF64(pointAtPathParameterF64(edge, cut.parameter))

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
