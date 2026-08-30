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
    val nodes = mutableListOf<PathIntersectionNodeF64>()
    val unionFind = PathUnionFindI32()

    fun addNode(edgeIndex: Int, parameter: Double): Int {
        val index = nodes.size
        nodes += PathIntersectionNodeF64(edgeIndex, snapParameterF64(parameter))
        unionFind.add()
        return index
    }
    fun addIntersection(firstIndex: Int, firstT: Double, secondIndex: Int, secondT: Double) {
        val firstNode = addNode(firstIndex, firstT)
        val secondNode = addNode(secondIndex, secondT)
        unionFind.union(firstNode, secondNode)
    }

    edges.indices.forEach { firstIndex ->
        for (secondIndex in firstIndex + 1 until edges.size) {
            when (val intersection = intersectPathEdgesF64(edges[firstIndex], edges[secondIndex])) {
                is PathIntersectionF64.PointF64 -> addIntersection(firstIndex, intersection.firstT, secondIndex, intersection.secondT)
                is PathIntersectionF64.OverlapF64 -> {
                    addIntersection(firstIndex, intersection.firstRange.start, secondIndex, parameterAtPointF64(intersection.start, edges[secondIndex]))
                    addIntersection(firstIndex, intersection.firstRange.endInclusive, secondIndex, parameterAtPointF64(intersection.end, edges[secondIndex]))
                }
                null -> Unit
            }
        }
    }

    nodes.indices.groupBy { nodes[it].edgeIndex }.values.forEach { edgeNodes ->
        edgeNodes.sortedBy { nodes[it].parameter }.zipWithNext().forEach { (firstNode, secondNode) ->
            if (PathPredicatesF64.almostEqualUlps(nodes[firstNode].parameter, nodes[secondNode].parameter)) {
                unionFind.union(firstNode, secondNode)
            }
        }
    }

    val rootByNode = nodes.indices.associateWith { unionFind.find(it) }
    val nodesByRoot = nodes.indices.groupBy { rootByNode.getValue(it) }
    if (nodesByRoot.size > limits.maxIntersections) throw IllegalStateException("path-intersection-limit")

    val identityByRoot = nodesByRoot.mapValues { (_, rootNodes) ->
        pathIntersectionIdentityF64(rootNodes, nodes, edges)
    }
    val cutsByEdge = edges.indices.map { edgeIndex ->
        mutableListOf(
            PathEdgeCutF64(0.0, edges[edgeIndex].startIdentity, false),
            PathEdgeCutF64(1.0, edges[edgeIndex].endIdentity, false),
        )
    }
    nodes.indices.forEach { nodeIndex ->
        val node = nodes[nodeIndex]
        val root = rootByNode.getValue(nodeIndex)
        cutsByEdge[node.edgeIndex] += PathEdgeCutF64(node.parameter, identityByRoot.getValue(root), true)
    }

    return buildList {
        edges.forEachIndexed { edgeIndex, edge ->
            canonicalPathCutsF64(cutsByEdge[edgeIndex]).zipWithNext().forEach { (startCut, endCut) ->
                val start = pointAtPathParameterF64(edge, startCut.parameter)
                val end = pointAtPathParameterF64(edge, endCut.parameter)
                if (start == end) return@forEach
                add(
                    PathSplitEdgeF64(
                        sourceId = edge.id,
                        operand = edge.operand,
                        startIdentity = startCut.identity,
                        endIdentity = endCut.identity,
                        start = start,
                        end = end,
                        windingDelta = edge.windingDelta,
                    ),
                )
            }
        }
    }
}

private data class PathIntersectionNodeF64(
    val edgeIndex: Int,
    val parameter: Double,
)

private data class PathEdgeCutF64(
    val parameter: Double,
    val identity: PathVertexIdentityF64,
    val isIntersection: Boolean,
)

private class PathUnionFindI32 {
    private val parents = mutableListOf<Int>()
    private val ranks = mutableListOf<Int>()

    fun add() {
        parents += parents.size
        ranks += 0
    }

    fun find(value: Int): Int {
        var root = value
        while (parents[root] != root) root = parents[root]
        var current = value
        while (parents[current] != current) {
            val next = parents[current]
            parents[current] = root
            current = next
        }
        return root
    }

    fun union(first: Int, second: Int) {
        var firstRoot = find(first)
        var secondRoot = find(second)
        if (firstRoot == secondRoot) return
        if (ranks[firstRoot] < ranks[secondRoot]) {
            val temporary = firstRoot
            firstRoot = secondRoot
            secondRoot = temporary
        }
        parents[secondRoot] = firstRoot
        if (ranks[firstRoot] == ranks[secondRoot]) ranks[firstRoot] += 1
    }
}

private fun intersectCollinearPathEdgesF64(first: PathInputEdgeF64, second: PathInputEdgeF64): PathIntersectionF64? {
    val secondStartOnFirst = unboundedParameterAtPointF64(second.start, first)
    val secondEndOnFirst = unboundedParameterAtPointF64(second.end, first)
    val firstStart = max(0.0, min(secondStartOnFirst, secondEndOnFirst))
    val firstEnd = min(1.0, max(secondStartOnFirst, secondEndOnFirst))
    if (firstStart > firstEnd && !PathPredicatesF64.almostEqualUlps(firstStart, firstEnd)) return null

    val startT = snapParameterF64(firstStart)
    val endT = snapParameterF64(firstEnd)
    val start = pointAtPathParameterF64(first, startT)
    val end = pointAtPathParameterF64(first, endT)
    val secondStart = parameterAtPointF64(start, second)
    val secondEnd = parameterAtPointF64(end, second)
    if (PathPredicatesF64.almostEqualUlps(startT, endT)) {
        return PathIntersectionF64.PointF64(start, startT, secondStart)
    }
    return PathIntersectionF64.OverlapF64(
        start = start,
        end = end,
        firstRange = startT..endT,
        secondRange = min(secondStart, secondEnd)..max(secondStart, secondEnd),
    )
}

private fun pathIntersectionIdentityF64(
    rootNodes: List<Int>,
    nodes: List<PathIntersectionNodeF64>,
    edges: List<PathInputEdgeF64>,
): PathVertexIdentityF64 {
    val parameters = mutableMapOf<Int, MutableList<Double>>()
    val incidentEdgeIds = mutableSetOf<Int>()
    val originalPoints = mutableListOf<Pair<Int, Point2F32>>()

    rootNodes.forEach { nodeIndex ->
        val node = nodes[nodeIndex]
        val edge = edges[node.edgeIndex]
        incidentEdgeIds += edge.id
        parameters.getOrPut(edge.id) { mutableListOf() } += node.parameter
        val endpointIdentity = when (node.parameter) {
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
            ),
        )
    }
}

private fun List<PathEdgeCutF64>.groupByEquivalentParametersF64(): List<List<PathEdgeCutF64>> {
    if (isEmpty()) return emptyList()
    val groups = mutableListOf<MutableList<PathEdgeCutF64>>()
    forEach { cut ->
        val previous = groups.lastOrNull()
        if (previous != null && PathPredicatesF64.almostEqualUlps(previous.last().parameter, cut.parameter)) {
            previous += cut
        } else {
            groups += mutableListOf(cut)
        }
    }
    return groups
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
    PathPredicatesF64.almostEqualUlps(parameter, 0.0) -> 0.0
    PathPredicatesF64.almostEqualUlps(parameter, 1.0) -> 1.0
    else -> parameter
}

private fun crossF64(firstX: Double, firstY: Double, secondX: Double, secondY: Double): Double =
    firstX * secondY - firstY * secondX
