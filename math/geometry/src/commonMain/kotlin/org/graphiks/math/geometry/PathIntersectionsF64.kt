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

internal fun intersectPathEdgesF64(first: PathInputEdgeF64, second: PathInputEdgeF64): PathIntersectionF64? {
    if (!first.start.isFinite() || !first.end.isFinite() || !second.start.isFinite() || !second.end.isFinite()) return null

    val firstDegenerate = first.start == first.end
    val secondDegenerate = second.start == second.end
    if (firstDegenerate && secondDegenerate) {
        return if (first.start == second.start) PathIntersectionF64.PointF64(first.start, 0.0, 0.0) else null
    }
    if (firstDegenerate) {
        return if (PathPredicatesF64.onSegment(first.start, second.start, second.end)) {
            PathIntersectionF64.PointF64(first.start, 0.0, parameterAtPointF64(first.start, second))
        } else {
            null
        }
    }
    if (secondDegenerate) {
        return if (PathPredicatesF64.onSegment(second.start, first.start, first.end)) {
            PathIntersectionF64.PointF64(second.start, parameterAtPointF64(second.start, first), 0.0)
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
        PathIntersectionF64.PointF64(point, snapParameterF64(firstT), snapParameterF64(secondT))

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

internal fun splitPathEdgesF64(edges: List<PathInputEdgeF64>, limits: PathOpsLimitsI32): List<PathSplitEdgeF64> {
    validatePathInputEdgesF64(edges)
    val canonicalEdges = edges.sortedBy { it.id }
    val maximumSplitEdges = limits.maxHalfEdges / 2
    val baseSplitEdges = canonicalEdges.count { edge -> edge.start != edge.end }
    if (baseSplitEdges > maximumSplitEdges) throw IllegalStateException("path-half-edge-limit")
    // Storage invariant: an interior incidence is a canonical cut, never a pair event, and can
    // add at most one split edge. Reserving only the final split-edge slack therefore bounds all
    // persistent registry state without charging repeated pair incidences to maxHalfEdges.
    val registry = PathIntersectionRegistryF64(
        edges = canonicalEdges,
        maxInteriorCuts = maximumSplitEdges - baseSplitEdges,
    )

    canonicalEdges.indices.forEach { firstIndex ->
        for (secondIndex in firstIndex + 1 until canonicalEdges.size) {
            when (val intersection = intersectPathEdgesF64(canonicalEdges[firstIndex], canonicalEdges[secondIndex])) {
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
            if (start != end) {
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
                if (start == end) return@forEach
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
    var exactWitness: PathExactIntersectionWitnessF64?,
    var canonicalPoint: Point2F64,
) {
    val incidencesByEdge = mutableMapOf<Int, PathComponentIncidenceF64>()
}

private data class PathParameterBucketKeyF64(
    val negative: Boolean,
    val bucket: Long,
)

private class PathEdgeComponentIndexF64 {
    private val componentsByBucket = mutableMapOf<PathParameterBucketKeyF64, MutableList<PathIntersectionComponentF64>>()

    // Each bucket covers sixteen ordered ULPs. A query checks only the neighbouring buckets and
    // then proves direct equivalence. This index stores live edge incidences, never pair events.
    fun matching(edgeIndex: Int, parameter: Double): List<PathIntersectionComponentF64> {
        val key = pathParameterBucketKeyF64(parameter)
        return buildList {
            for (offset in -1L..1L) {
                val bucket = key.bucket + offset
                componentsByBucket[PathParameterBucketKeyF64(key.negative, bucket)]?.forEach { component ->
                    val incidence = component.incidencesByEdge[edgeIndex]
                    if (incidence != null && mergeDirectPathIncidenceF64(incidence, parameter) != null) add(component)
                }
            }
        }
    }

    fun add(component: PathIntersectionComponentF64, parameter: Double) {
        val bucket = componentsByBucket.getOrPut(pathParameterBucketKeyF64(parameter)) { mutableListOf() }
        if (component !in bucket) bucket += component
    }

    fun remove(component: PathIntersectionComponentF64, parameter: Double) {
        val key = pathParameterBucketKeyF64(parameter)
        componentsByBucket[key]?.let { bucket ->
            bucket.remove(component)
            if (bucket.isEmpty()) componentsByBucket.remove(key)
        }
    }
}

private fun pathParameterBucketKeyF64(parameter: Double): PathParameterBucketKeyF64 {
    val bits = parameter.toRawBits()
    return PathParameterBucketKeyF64(
        negative = bits < 0L,
        bucket = (bits and Long.MAX_VALUE) / 16L,
    )
}

private class PathIntersectionRegistryF64(
    private val edges: List<PathInputEdgeF64>,
    private val maxInteriorCuts: Int,
) {
    private val activeComponentsById = linkedMapOf<Int, PathIntersectionComponentF64>()
    val components: List<PathIntersectionComponentF64> get() = activeComponentsById.values.toList()
    // Pair enumeration performs two local parameter lookups and one logarithmic exact-witness
    // lookup. No path scans all components or all incidences of an edge, and persistent state is
    // proportional to canonical cuts rather than pair events.
    private val componentsByEdge = List(edges.size) { PathEdgeComponentIndexF64() }
    private val exactLines = edges.map(::exactPathLineF64)
    private val exactComponentsByWitness = PathExactWitnessIndexF64()
    private var nextComponentId = 0
    private var interiorCutCount = 0

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
        val incomingPoint = incomingWitness?.canonicalPoint() ?: point
        val candidates = matchingComponentsF64(
            firstEdgeIndex,
            snappedFirstParameter,
            secondEdgeIndex,
            snappedSecondParameter,
            incomingWitness,
        )
        var component: PathIntersectionComponentF64? = null
        candidates.sortedBy { it.id }.forEach { candidate ->
            component = when (val selected = component) {
                null -> candidate
                else -> if (canMergeComponentsF64(selected, candidate)) {
                    mergeComponentsF64(selected, candidate)
                } else {
                    selected
                }
            }
        }
        if (component == null) {
            addNewComponentF64(
                firstEdgeIndex,
                snappedFirstParameter,
                secondEdgeIndex,
                snappedSecondParameter,
                incomingWitness,
                incomingPoint,
            )
            return
        }

        if (component.exactWitness == null && incomingWitness != null) {
            component.exactWitness = incomingWitness
            component.canonicalPoint = incomingPoint
            registerExactComponentF64(component)
        } else {
            component.canonicalPoint = canonicalPointF64(component.canonicalPoint, incomingPoint)
        }
        addIncidenceF64(component, firstEdgeIndex, snappedFirstParameter)
        addIncidenceF64(component, secondEdgeIndex, snappedSecondParameter)
    }

    private fun matchingComponentsF64(
        firstEdgeIndex: Int,
        firstParameter: Double,
        secondEdgeIndex: Int,
        secondParameter: Double,
        incomingWitness: PathExactIntersectionWitnessF64?,
    ): List<PathIntersectionComponentF64> = (
        directComponentsF64(firstEdgeIndex, firstParameter) +
            directComponentsF64(secondEdgeIndex, secondParameter) +
            exactComponentsF64(incomingWitness)
        ).distinct().filter { component ->
            val exactMatch = exactMatchF64(component, incomingWitness)
            if (exactMatch) return@filter true

            val firstMatches = component.incidencesByEdge[firstEdgeIndex]?.let { incidence ->
                mergeDirectPathIncidenceF64(incidence, firstParameter) != null
            } ?: false
            val secondMatches = component.incidencesByEdge[secondEdgeIndex]?.let { incidence ->
                mergeDirectPathIncidenceF64(incidence, secondParameter) != null
            } ?: false
            firstMatches || secondMatches
        }

    private fun addNewComponentF64(
        firstEdgeIndex: Int,
        firstParameter: Double,
        secondEdgeIndex: Int,
        secondParameter: Double,
        exactWitness: PathExactIntersectionWitnessF64?,
        canonicalPoint: Point2F64,
    ) {
        val additionalInteriorCuts = listOf(firstEdgeIndex to firstParameter, secondEdgeIndex to secondParameter)
            .count { (edgeIndex, parameter) -> isInteriorCutF64(edgeIndex, parameter) }
        ensureInteriorCutCapacityF64(additionalInteriorCuts)

        val component = PathIntersectionComponentF64(nextComponentId++, exactWitness, canonicalPoint)
        activeComponentsById[component.id] = component
        addIncidenceF64(component, firstEdgeIndex, firstParameter)
        addIncidenceF64(component, secondEdgeIndex, secondParameter)
        registerExactComponentF64(component)
    }

    private fun addIncidenceF64(
        component: PathIntersectionComponentF64,
        edgeIndex: Int,
        parameter: Double,
    ) {
        val existing = component.incidencesByEdge[edgeIndex]
        if (existing != null) {
            // A homogeneous witness can select this component even when division produces a less
            // stable parameter, but it never widens the stored direct ULP cluster. Its retained
            // minimum and maximum always remain directly equivalent, so 0/15/30 cannot collapse.
            val merged = mergeDirectPathIncidenceF64(existing, parameter)
            if (merged != null) {
                if (merged != existing) {
                    componentsByEdge[edgeIndex].remove(component, existing.parameter)
                    component.incidencesByEdge[edgeIndex] = merged
                    insertComponentForEdgeF64(edgeIndex, component)
                }
                return
            }
            return
        }

        ensureInteriorCutCapacityF64(if (isInteriorCutF64(edgeIndex, parameter)) 1 else 0)
        component.incidencesByEdge[edgeIndex] = PathComponentIncidenceF64(parameter)
        insertComponentForEdgeF64(edgeIndex, component)
        if (isInteriorCutF64(edgeIndex, parameter)) interiorCutCount += 1
    }

    private fun ensureInteriorCutCapacityF64(additionalInteriorCuts: Int) {
        if (additionalInteriorCuts > maxInteriorCuts - interiorCutCount) {
            throw IllegalStateException("path-half-edge-limit")
        }
    }

    private fun isInteriorCutF64(edgeIndex: Int, parameter: Double): Boolean =
        edges[edgeIndex].start != edges[edgeIndex].end && parameter != 0.0 && parameter != 1.0

    private fun canMergeComponentsF64(
        first: PathIntersectionComponentF64,
        second: PathIntersectionComponentF64,
    ): Boolean {
        if (first.exactWitness?.samePoint(second.exactWitness) == true) return true
        var hasSharedDirectIncidence = false
        return first.incidencesByEdge.all { (edgeIndex, firstIncidence) ->
            second.incidencesByEdge[edgeIndex]?.let { secondIncidence ->
                val compatible = mergeDirectPathIncidencesF64(firstIncidence, secondIncidence) != null
                hasSharedDirectIncidence = hasSharedDirectIncidence || compatible
                compatible
            } ?: true
        } && hasSharedDirectIncidence
    }

    private fun mergeComponentsF64(
        first: PathIntersectionComponentF64,
        second: PathIntersectionComponentF64,
    ): PathIntersectionComponentF64 {
        if (first === second) return first
        val winner = if (first.id < second.id) first else second
        val loser = if (winner === first) second else first
        unregisterExactComponentF64(winner)
        unregisterExactComponentF64(loser)
        loser.incidencesByEdge.forEach { (edgeIndex, loserIncidence) ->
            val winnerIncidence = winner.incidencesByEdge[edgeIndex]
            if (winnerIncidence == null) {
                winner.incidencesByEdge[edgeIndex] = loserIncidence
                componentsByEdge[edgeIndex].remove(loser, loserIncidence.parameter)
                insertComponentForEdgeF64(edgeIndex, winner)
            } else {
                if (isInteriorCutF64(edgeIndex, loserIncidence.parameter)) interiorCutCount -= 1
                componentsByEdge[edgeIndex].remove(winner, winnerIncidence.parameter)
                winner.incidencesByEdge[edgeIndex] = checkNotNull(
                    mergeDirectPathIncidencesF64(winnerIncidence, loserIncidence),
                )
                componentsByEdge[edgeIndex].remove(loser, loserIncidence.parameter)
                insertComponentForEdgeF64(edgeIndex, winner)
            }
        }
        if (winner.exactWitness == null) winner.exactWitness = loser.exactWitness
        winner.canonicalPoint = canonicalPointF64(winner.canonicalPoint, loser.canonicalPoint)
        activeComponentsById.remove(loser.id)
        registerExactComponentF64(winner)
        return winner
    }

    private fun directComponentsF64(edgeIndex: Int, parameter: Double): List<PathIntersectionComponentF64> =
        componentsByEdge[edgeIndex].matching(edgeIndex, parameter)

    private fun exactComponentsF64(witness: PathExactIntersectionWitnessF64?): List<PathIntersectionComponentF64> {
        if (witness == null) return emptyList()
        // The tree's ordering compares homogeneous coordinates exactly; the equality check is
        // retained as the semantic gate, so neither rounded points nor index coincidences unite.
        return exactComponentsByWitness.find(witness)
            ?.takeIf { component -> exactMatchF64(component, witness) }
            ?.let(::listOf)
            .orEmpty()
    }

    private fun exactMatchF64(
        component: PathIntersectionComponentF64,
        incomingWitness: PathExactIntersectionWitnessF64?,
    ): Boolean = incomingWitness != null && component.exactWitness?.samePoint(incomingWitness) == true

    private fun insertComponentForEdgeF64(edgeIndex: Int, component: PathIntersectionComponentF64) {
        val parameter = component.incidencesByEdge.getValue(edgeIndex).parameter
        componentsByEdge[edgeIndex].add(component, parameter)
    }

    private fun registerExactComponentF64(component: PathIntersectionComponentF64) {
        component.exactWitness?.let { witness -> exactComponentsByWitness.insert(witness, component) }
    }

    private fun unregisterExactComponentF64(component: PathIntersectionComponentF64) {
        component.exactWitness?.let { witness -> exactComponentsByWitness.remove(witness, component) }
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
        val component: PathIntersectionComponentF64,
        var left: Node? = null,
        var right: Node? = null,
        var height: Int = 1,
    )

    private var root: Node? = null

    fun find(witness: PathExactIntersectionWitnessF64): PathIntersectionComponentF64? {
        var node = root
        while (node != null) {
            val comparison = comparePathExactWitnessesF64(witness, node.witness)
            when {
                comparison == 0 -> return node.component
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
        node == null -> Node(witness, component)
        comparePathExactWitnessesF64(witness, node.witness) < 0 -> {
            node.left = insert(node.left, witness, component)
            rebalance(node)
        }
        comparePathExactWitnessesF64(witness, node.witness) > 0 -> {
            node.right = insert(node.right, witness, component)
            rebalance(node)
        }
        else -> node
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
            else -> if (node.component === component) join(node.left, node.right) else node
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
    if (!edge.start.isFinite() || !edge.end.isFinite() || edge.start == edge.end) return null
    return PathExactLineF64(
        a = ExpansionF64.twoDiff(edge.start.y, edge.end.y),
        b = ExpansionF64.twoDiff(edge.end.x, edge.start.x),
        c = ExpansionF64.expansionDiff(
            ExpansionF64.product(doubleArrayOf(edge.start.x), doubleArrayOf(edge.end.y)),
            ExpansionF64.product(doubleArrayOf(edge.start.y), doubleArrayOf(edge.end.x)),
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
    val originalPoints = mutableListOf<Pair<Int, Point2F32>>()

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
            endpointIdentity.originalPointF32?.let { originalPoints += edge.id to it }
        }
    }

    val canonicalParameters = linkedMapOf<Int, Double>()
    parameters.keys.sorted().forEach { edgeId ->
        canonicalParameters[edgeId] = canonicalParameterF64(parameters.getValue(edgeId))
    }
    return PathVertexIdentityF64(
        incidentEdgeIds = incidentEdgeIds.sorted(),
        parameterByEdgeId = canonicalParameters,
        originalPointF32 = originalPoints.minByOrNull { it.first }?.second,
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
    cut.canonicalPoint ?: pointAtPathParameterF64(edge, cut.parameter)

private fun canonicalPointF64(first: Point2F64, second: Point2F64): Point2F64 = when {
    first.x < second.x -> first
    first.x > second.x -> second
    first.y <= second.y -> first
    else -> second
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

private fun pointAtPathParameterF64(edge: PathInputEdgeF64, parameter: Double): Point2F64 = when (parameter) {
    0.0 -> edge.start
    1.0 -> edge.end
    else -> Point2F64(
        edge.start.x + (edge.end.x - edge.start.x) * parameter,
        edge.start.y + (edge.end.y - edge.start.y) * parameter,
    )
}

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
