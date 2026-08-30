package org.graphiks.math.geometry

internal data class PathVertexF64(
    val id: Int,
    val point: Point2F64,
    val originalPointF32: Point2F32?,
)

internal data class PathEdgeF64(
    val startVertexId: Int,
    val endVertexId: Int,
    val firstWindingDelta: Int,
    val secondWindingDelta: Int,
)

internal data class PathContourVertexF64(
    val point: Point2F64,
    val originalPointF32: Point2F32?,
)

internal data class PathContourF64(val vertices: List<PathContourVertexF64>)

private data class PathHalfEdgeF64(
    val id: Int,
    val originVertexId: Int,
    val twinId: Int,
    val nextId: Int,
    val leftFaceId: Int,
    val firstWindingDelta: Int,
    val secondWindingDelta: Int,
)

private data class PathFaceI32(
    val id: Int,
    val boundaryHalfEdgeIds: List<Int>,
    val firstWinding: Int,
    val secondWinding: Int,
)

internal class PathArrangementF64 private constructor(
    private val vertices: List<PathVertexF64>,
    private val halfEdges: List<PathHalfEdgeF64>,
    private val faces: List<PathFaceI32>,
) {
    fun boundary(
        firstFillRule: FillRule,
        secondFillRule: FillRule,
        operation: PathBooleanOp,
    ): List<PathContourF64> = extractBoundaryF64 { face ->
        val firstInside = face.firstWinding.isFilledByI32(firstFillRule)
        val secondInside = face.secondWinding.isFilledByI32(secondFillRule)
        operation.selectsF64(firstInside, secondInside)
    }

    fun unaryBoundary(fillRule: FillRule): List<PathContourF64> =
        extractBoundaryF64 { face -> face.firstWinding.isFilledByI32(fillRule) }

    private fun extractBoundaryF64(selectsFace: (PathFaceI32) -> Boolean): List<PathContourF64> {
        if (halfEdges.isEmpty()) return emptyList()
        val faceSelected = BooleanArray(faces.size) { faceId -> selectsFace(faces[faceId]) }
        val selected = BooleanArray(halfEdges.size)
        halfEdges.forEach { halfEdge ->
            if (halfEdge.id > halfEdge.twinId) return@forEach
            val leftSelected = faceSelected[halfEdge.leftFaceId]
            val rightSelected = faceSelected[halfEdges[halfEdge.twinId].leftFaceId]
            if (leftSelected != rightSelected) {
                selected[if (leftSelected) halfEdge.id else halfEdge.twinId] = true
            }
        }
        if (selected.none { it }) return emptyList()

        val outgoing = List(vertices.size) { mutableListOf<Int>() }
        halfEdges.forEach { halfEdge -> outgoing[halfEdge.originVertexId] += halfEdge.id }
        outgoing.forEachIndexed { vertexId, halfEdgeIds ->
            halfEdgeIds.sortWith(
                Comparator { firstId, secondId ->
                    compareOutputOutgoingHalfEdgesF64(firstId, secondId, vertexId, halfEdges, vertices)
                },
            )
        }
        val nextSelected = IntArray(halfEdges.size) { -1 }
        halfEdges.indices.forEach { halfEdgeId ->
            if (!selected[halfEdgeId]) return@forEach
            val halfEdge = halfEdges[halfEdgeId]
            val atDestination = outgoing[halfEdges[halfEdge.twinId].originVertexId]
            val twinIndex = atDestination.indexOf(halfEdge.twinId)
            if (twinIndex < 0) pathArrangementInconsistentF64()
            // Starting at the backtracking twin, rotate clockwise through the selected sector.
            // A shared edge can have selected faces on both sides, so following the current
            // face's `nextId` would skip across that edge and create a diagonal. The first
            // selected-left / unselected-right ray is the actual output continuation.
            var scanned = 0
            while (scanned < atDestination.size) {
                val candidateIndex = if (twinIndex - scanned - 1 < 0) {
                    twinIndex - scanned - 1 + atDestination.size
                } else {
                    twinIndex - scanned - 1
                }
                val candidateId = atDestination[candidateIndex]
                val candidate = halfEdges[candidateId]
                val candidateLeftSelected = faceSelected[candidate.leftFaceId]
                val candidateRightSelected = faceSelected[halfEdges[candidate.twinId].leftFaceId]
                if (!candidateLeftSelected) pathArrangementInconsistentF64()
                if (!candidateRightSelected) {
                    if (!selected[candidateId]) pathArrangementInconsistentF64()
                    nextSelected[halfEdgeId] = candidateId
                    break
                }
                scanned += 1
            }
            if (nextSelected[halfEdgeId] == -1) pathArrangementInconsistentF64()
        }

        val visited = BooleanArray(halfEdges.size)
        val contours = mutableListOf<PathCanonicalContourF64>()
        halfEdges.indices.forEach { start ->
            if (!selected[start] || visited[start]) return@forEach
            val vertexIds = mutableListOf<Int>()
            var current = start
            var steps = 0
            while (true) {
                if (!selected[current] || (visited[current] && current != start)) pathArrangementInconsistentF64()
                if (current == start && vertexIds.isNotEmpty()) break
                visited[current] = true
                vertexIds += halfEdges[current].originVertexId
                current = nextSelected[current]
                steps += 1
                if (steps > halfEdges.size) pathArrangementInconsistentF64()
            }
            if (current != start || vertexIds.isEmpty()) pathArrangementInconsistentF64()
            canonicalContourF64(vertexIds, vertices)?.let(contours::add)
        }

        return contours.sortedWith(
            Comparator { first, second ->
                compareAbsoluteExpansionsF64(second.signedDoubleAreaExpansion, first.signedDoubleAreaExpansion)
                    .takeIf { it != 0 }
                    ?: compareContourStartF64(first.vertexIds.first(), second.vertexIds.first(), vertices)
            },
        ).map { it.contour }
    }

    companion object {
        fun build(edges: List<PathSplitEdgeF64>, limits: PathOpsLimitsI32): PathArrangementF64 {
            if (edges.isEmpty()) return PathArrangementF64(emptyList(), emptyList(), emptyList())

            // Every input split edge could survive aggregation. This division is deliberately
            // performed before allocating arrangement state: it is equivalent to checking
            // `2 * edges.size > maxHalfEdges` without an overflowing multiplication.
            val maximumUndirectedEdges = limits.maxHalfEdges / 2
            if (edges.size > maximumUndirectedEdges) throw IllegalStateException("path-half-edge-limit")

            val vertexSeedsByIdentity = linkedMapOf<PathVertexIdentityF64, PathArrangementVertexSeedF64>()
            edges.forEach { edge ->
                addArrangementVertexSeedF64(vertexSeedsByIdentity, edge.startIdentity, edge.start, limits)
                addArrangementVertexSeedF64(vertexSeedsByIdentity, edge.endIdentity, edge.end, limits)
            }
            val orderedSeeds = vertexSeedsByIdentity.values.sortedWith(
                Comparator { first, second -> comparePathVertexIdentitiesF64(first.identity, second.identity) },
            )
            val vertexIdByIdentity = mutableMapOf<PathVertexIdentityF64, Int>()
            val vertices = orderedSeeds.mapIndexed { id, seed ->
                vertexIdByIdentity[seed.identity] = id
                PathVertexF64(id, seed.point, seed.identity.originalPointF32)
            }

            val contributionsByEdge = linkedMapOf<PathArrangementEdgeKeyI32, PathArrangementContributionI64>()
            edges.forEach { edge ->
                val startVertexId = vertexIdByIdentity.getValue(edge.startIdentity)
                val endVertexId = vertexIdByIdentity.getValue(edge.endIdentity)
                val start = vertices[startVertexId].point
                val end = vertices[endVertexId].point
                if (startVertexId == endVertexId || sameArrangementPointF64(start, end)) {
                    pathArrangementInconsistentF64()
                }

                val forward = startVertexId < endVertexId
                val key = if (forward) {
                    PathArrangementEdgeKeyI32(startVertexId, endVertexId)
                } else {
                    PathArrangementEdgeKeyI32(endVertexId, startVertexId)
                }
                val contribution = contributionsByEdge.getOrPut(key) { PathArrangementContributionI64() }
                val signedDelta = if (forward) edge.windingDelta.toLong() else -edge.windingDelta.toLong()
                when (edge.operand) {
                    PathOperand.FIRST -> contribution.firstWindingDelta += signedDelta
                    PathOperand.SECOND -> contribution.secondWindingDelta += signedDelta
                }
            }
            val pathEdges = contributionsByEdge.entries.asSequence()
                .filter { (_, contribution) ->
                    contribution.firstWindingDelta != 0L || contribution.secondWindingDelta != 0L
                }
                .sortedWith(
                    compareBy<Map.Entry<PathArrangementEdgeKeyI32, PathArrangementContributionI64>> {
                        it.key.startVertexId
                    }
                        .thenBy { it.key.endVertexId },
                )
                .map { (key, contribution) ->
                    PathEdgeF64(
                        startVertexId = key.startVertexId,
                        endVertexId = key.endVertexId,
                        firstWindingDelta = contribution.firstWindingDelta.toArrangementI32(),
                        secondWindingDelta = contribution.secondWindingDelta.toArrangementI32(),
                    )
                }.toList()
            if (pathEdges.size > maximumUndirectedEdges) throw IllegalStateException("path-half-edge-limit")
            if (pathEdges.isEmpty()) return PathArrangementF64(vertices, emptyList(), emptyList())

            val mutableHalfEdges = ArrayList<PathMutableHalfEdgeF64>(pathEdges.size * 2)
            pathEdges.forEachIndexed { edgeIndex, edge ->
                val forwardId = edgeIndex * 2
                val reverseId = forwardId + 1
                mutableHalfEdges += PathMutableHalfEdgeF64(
                    id = forwardId,
                    originVertexId = edge.startVertexId,
                    destinationVertexId = edge.endVertexId,
                    twinId = reverseId,
                    firstWindingDelta = edge.firstWindingDelta,
                    secondWindingDelta = edge.secondWindingDelta,
                )
                mutableHalfEdges += PathMutableHalfEdgeF64(
                    id = reverseId,
                    originVertexId = edge.endVertexId,
                    destinationVertexId = edge.startVertexId,
                    twinId = forwardId,
                    firstWindingDelta = edge.firstWindingDelta.negatedArrangementDeltaI32(),
                    secondWindingDelta = edge.secondWindingDelta.negatedArrangementDeltaI32(),
                )
            }
            val outgoing = List(vertices.size) { mutableListOf<Int>() }
            mutableHalfEdges.forEach { halfEdge -> outgoing[halfEdge.originVertexId] += halfEdge.id }
            outgoing.forEachIndexed { vertexId, halfEdgeIds ->
                halfEdgeIds.sortWith(
                    Comparator { firstId, secondId ->
                        compareOutgoingHalfEdgesF64(firstId, secondId, vertexId, mutableHalfEdges, vertices)
                    },
                )
                halfEdgeIds.zipWithNext().forEach { (firstId, secondId) ->
                    val first = mutableHalfEdges[firstId]
                    val second = mutableHalfEdges[secondId]
                    if (sameOutgoingRayF64(first, second, vertices)) pathArrangementInconsistentF64()
                }
            }

            val outgoingPosition = IntArray(mutableHalfEdges.size) { -1 }
            outgoing.forEach { halfEdgeIds -> halfEdgeIds.forEachIndexed { index, id -> outgoingPosition[id] = index } }
            val incomingNextCount = IntArray(mutableHalfEdges.size)
            mutableHalfEdges.forEach { halfEdge ->
                val arrivalOutgoing = outgoing[halfEdge.destinationVertexId]
                val twinPosition = outgoingPosition[halfEdge.twinId]
                if (arrivalOutgoing.isEmpty() || twinPosition < 0) pathArrangementInconsistentF64()
                val nextPosition = if (twinPosition == 0) arrivalOutgoing.lastIndex else twinPosition - 1
                halfEdge.nextId = arrivalOutgoing[nextPosition]
                incomingNextCount[halfEdge.nextId] += 1
            }
            if (incomingNextCount.any { it != 1 }) pathArrangementInconsistentF64()

            val mutableFaces = enumerateArrangementFacesF64(mutableHalfEdges)
            val components = arrangementComponentsF64(pathEdges, vertices, outgoing, mutableHalfEdges, mutableFaces)
            propagateArrangementWindingsF64(components, vertices, mutableHalfEdges, mutableFaces)

            val halfEdges = mutableHalfEdges.map { halfEdge ->
                PathHalfEdgeF64(
                    id = halfEdge.id,
                    originVertexId = halfEdge.originVertexId,
                    twinId = halfEdge.twinId,
                    nextId = halfEdge.nextId,
                    leftFaceId = halfEdge.leftFaceId,
                    firstWindingDelta = halfEdge.firstWindingDelta,
                    secondWindingDelta = halfEdge.secondWindingDelta,
                )
            }
            val faces = mutableFaces.map { face ->
                PathFaceI32(
                    id = face.id,
                    boundaryHalfEdgeIds = face.boundaryHalfEdgeIds,
                    firstWinding = face.firstWinding.requireNotNullArrangementI32(),
                    secondWinding = face.secondWinding.requireNotNullArrangementI32(),
                )
            }
            return PathArrangementF64(vertices, halfEdges, faces)
        }
    }
}

private data class PathArrangementVertexSeedF64(
    val identity: PathVertexIdentityF64,
    val point: Point2F64,
)

private data class PathArrangementEdgeKeyI32(
    val startVertexId: Int,
    val endVertexId: Int,
)

private class PathArrangementContributionI64(
    var firstWindingDelta: Long = 0L,
    var secondWindingDelta: Long = 0L,
)

private class PathMutableHalfEdgeF64(
    val id: Int,
    val originVertexId: Int,
    val destinationVertexId: Int,
    val twinId: Int,
    val firstWindingDelta: Int,
    val secondWindingDelta: Int,
    var nextId: Int = -1,
    var leftFaceId: Int = -1,
)

private class PathMutableFaceI32(
    val id: Int,
    val boundaryHalfEdgeIds: List<Int>,
    var firstWinding: Int? = null,
    var secondWinding: Int? = null,
)

private class PathArrangementComponentI32(
    val id: Int,
    val vertexIds: List<Int>,
    val faceIds: MutableList<Int> = mutableListOf(),
    var witnessVertexId: Int = -1,
    var externalSectorWitness: PathExternalSectorWitnessI32? = null,
    var externalFaceId: Int = -1,
)

private data class PathExternalSectorWitnessI32(
    val vertexId: Int,
    val outgoingHalfEdgeIdBeforeLeftRay: Int,
    val externalFaceId: Int,
)

private class PathArrangementDisjointSetI32(size: Int) {
    private val parent = IntArray(size) { it }
    private val rank = IntArray(size)

    fun find(value: Int): Int {
        var root = value
        while (parent[root] != root) root = parent[root]
        var current = value
        while (parent[current] != current) {
            val next = parent[current]
            parent[current] = root
            current = next
        }
        return root
    }

    fun union(first: Int, second: Int) {
        var firstRoot = find(first)
        var secondRoot = find(second)
        if (firstRoot == secondRoot) return
        if (rank[firstRoot] < rank[secondRoot]) {
            val swap = firstRoot
            firstRoot = secondRoot
            secondRoot = swap
        }
        parent[secondRoot] = firstRoot
        if (rank[firstRoot] == rank[secondRoot]) rank[firstRoot] += 1
    }
}

private data class PathCanonicalContourF64(
    val contour: PathContourF64,
    val vertexIds: List<Int>,
    val signedDoubleAreaExpansion: DoubleArray,
)

private fun addArrangementVertexSeedF64(
    seedsByIdentity: MutableMap<PathVertexIdentityF64, PathArrangementVertexSeedF64>,
    identity: PathVertexIdentityF64,
    point: Point2F64,
    limits: PathOpsLimitsI32,
) {
    val canonicalPoint = canonicalArrangementPointF64(point)
    if (!canonicalPoint.isFinite()) pathArrangementInconsistentF64()
    val existing = seedsByIdentity[identity]
    if (existing != null) {
        if (!sameArrangementPointF64(existing.point, canonicalPoint)) pathArrangementInconsistentF64()
        return
    }
    if (seedsByIdentity.size >= limits.maxVertices) throw IllegalStateException("path-vertex-limit")
    seedsByIdentity[identity] = PathArrangementVertexSeedF64(identity, canonicalPoint)
}

private fun enumerateArrangementFacesF64(
    halfEdges: List<PathMutableHalfEdgeF64>,
): List<PathMutableFaceI32> {
    val faces = mutableListOf<PathMutableFaceI32>()
    halfEdges.forEach { start ->
        if (start.leftFaceId != -1) return@forEach
        val boundary = mutableListOf<Int>()
        var current = start.id
        var steps = 0
        while (true) {
            if (current == start.id && boundary.isNotEmpty()) break
            if (current !in halfEdges.indices || halfEdges[current].leftFaceId != -1) pathArrangementInconsistentF64()
            halfEdges[current].leftFaceId = faces.size
            boundary += current
            current = halfEdges[current].nextId
            steps += 1
            if (steps > halfEdges.size) pathArrangementInconsistentF64()
        }
        if (current != start.id || boundary.isEmpty()) pathArrangementInconsistentF64()
        faces += PathMutableFaceI32(faces.size, boundary)
    }
    return faces
}

private fun arrangementComponentsF64(
    pathEdges: List<PathEdgeF64>,
    vertices: List<PathVertexF64>,
    outgoing: List<List<Int>>,
    halfEdges: List<PathMutableHalfEdgeF64>,
    faces: List<PathMutableFaceI32>,
): List<PathArrangementComponentI32> {
    val disjointSet = PathArrangementDisjointSetI32(vertices.size)
    pathEdges.forEach { edge -> disjointSet.union(edge.startVertexId, edge.endVertexId) }
    val verticesByRoot = linkedMapOf<Int, MutableList<Int>>()
    vertices.indices.forEach { vertexId ->
        if (outgoing[vertexId].isNotEmpty()) {
            verticesByRoot.getOrPut(disjointSet.find(vertexId)) { mutableListOf() } += vertexId
        }
    }
    val components = verticesByRoot.entries.map { (_, vertexIds) ->
        PathArrangementComponentI32(
            id = -1,
            vertexIds = vertexIds.sorted(),
        )
    }.sortedWith(
        Comparator { first, second ->
            val firstVertex = first.vertexIds.minWithOrNull(Comparator { a, b -> compareVerticesF64(a, b, vertices) })
                ?: pathArrangementInconsistentF64()
            val secondVertex = second.vertexIds.minWithOrNull(Comparator { a, b -> compareVerticesF64(a, b, vertices) })
                ?: pathArrangementInconsistentF64()
            compareVerticesF64(firstVertex, secondVertex, vertices)
        },
    ).mapIndexed { index, component ->
        PathArrangementComponentI32(index, component.vertexIds)
    }
    val componentByRoot = mutableMapOf<Int, PathArrangementComponentI32>()
    components.forEach { component -> componentByRoot[disjointSet.find(component.vertexIds.first())] = component }

    faces.forEach { face ->
        val roots = face.boundaryHalfEdgeIds.map { halfEdgeId ->
            disjointSet.find(halfEdges[halfEdgeId].originVertexId)
        }.distinct()
        if (roots.size != 1) pathArrangementInconsistentF64()
        componentByRoot.getValue(roots.single()).faceIds += face.id
    }
    components.forEach { component ->
        if (component.faceIds.isEmpty()) pathArrangementInconsistentF64()
        component.witnessVertexId = component.vertexIds.minWithOrNull(
            Comparator { first, second -> compareVerticesF64(first, second, vertices) },
        ) ?: pathArrangementInconsistentF64()
        val sectorWitness = certifiedExternalSectorWitnessI32(component, outgoing, halfEdges, vertices)
        component.externalSectorWitness = sectorWitness
        component.externalFaceId = sectorWitness.externalFaceId
        if (component.externalFaceId !in component.faceIds) pathArrangementInconsistentF64()
    }
    return components
}

private fun certifiedExternalSectorWitnessI32(
    component: PathArrangementComponentI32,
    outgoing: List<List<Int>>,
    halfEdges: List<PathMutableHalfEdgeF64>,
    vertices: List<PathVertexF64>,
): PathExternalSectorWitnessI32 {
    val vertexId = component.witnessVertexId
    val point = vertices[vertexId].point
    val candidates = outgoing[vertexId]
    if (candidates.isEmpty()) pathArrangementInconsistentF64()
    // This is the containment witness required for disconnected components. At the lexicographic
    // minimum x vertex no incident edge can point strictly left. The sector containing the left
    // ray is therefore the left sector of the last outgoing ray before that direction. We choose
    // it from the robust quadrant order, never by offsetting a sampled point.
    candidates.forEach { halfEdgeId ->
        if (vertices[halfEdges[halfEdgeId].destinationVertexId].point.x < point.x) pathArrangementInconsistentF64()
    }
    val indexBeforeLeftRay = candidates.indexOfLast { halfEdgeId ->
        outgoingQuadrantF64(halfEdges[halfEdgeId], vertices) <= 1
    }
    val witnessHalfEdge = candidates[if (indexBeforeLeftRay >= 0) indexBeforeLeftRay else candidates.lastIndex]
    return PathExternalSectorWitnessI32(
        vertexId = vertexId,
        outgoingHalfEdgeIdBeforeLeftRay = witnessHalfEdge,
        externalFaceId = halfEdges[witnessHalfEdge].leftFaceId,
    )
}

private fun PathArrangementComponentI32.externalSectorWitnessPointF64(
    vertices: List<PathVertexF64>,
): Point2F64 {
    val witness = externalSectorWitness ?: pathArrangementInconsistentF64()
    if (witness.vertexId != witnessVertexId || witness.externalFaceId != externalFaceId) {
        pathArrangementInconsistentF64()
    }
    return vertices.getOrNull(witness.vertexId)?.point ?: pathArrangementInconsistentF64()
}

private fun propagateArrangementWindingsF64(
    components: List<PathArrangementComponentI32>,
    vertices: List<PathVertexF64>,
    halfEdges: List<PathMutableHalfEdgeF64>,
    faces: List<PathMutableFaceI32>,
) {
    val parentByComponent = IntArray(components.size) { -1 }
    val parentFaceByComponent = IntArray(components.size) { -1 }
    components.forEach { component ->
        var immediateParent = -1
        var immediateParentFaceId = -1
        components.forEach { candidate ->
            if (candidate.id == component.id) return@forEach
            val candidateContainingFaceId = containingFaceForWitnessF64(
                candidate,
                component,
                vertices,
                halfEdges,
                faces,
            ) ?: return@forEach
            if (immediateParent == -1) {
                immediateParent = candidate.id
                immediateParentFaceId = candidateContainingFaceId
                return@forEach
            }
            val currentParent = components[immediateParent]
            when {
                containingFaceForWitnessF64(currentParent, candidate, vertices, halfEdges, faces) != null -> {
                    immediateParent = candidate.id
                    immediateParentFaceId = candidateContainingFaceId
                }
                containingFaceForWitnessF64(candidate, currentParent, vertices, halfEdges, faces) != null -> Unit
                else -> pathArrangementInconsistentF64()
            }
        }
        parentByComponent[component.id] = immediateParent
        parentFaceByComponent[component.id] = immediateParentFaceId
    }

    val children = List(components.size) { mutableListOf<Int>() }
    val pending = mutableListOf<Int>()
    parentByComponent.forEachIndexed { componentId, parent ->
        if (parent < 0) pending += componentId else children[parent] += componentId
    }
    children.forEach { it.sort() }
    var nextPending = 0
    var propagatedCount = 0
    while (nextPending < pending.size) {
        val componentId = pending[nextPending++]
        val component = components[componentId]
        val parent = parentByComponent[componentId]
        val initialWinding = if (parent < 0) {
            if (parentFaceByComponent[componentId] != -1) pathArrangementInconsistentF64()
            PathArrangementWindingI32(0, 0)
        } else {
            val parentComponent = components[parent]
            val containingFaceId = parentFaceByComponent[componentId]
            if (containingFaceId !in parentComponent.faceIds) pathArrangementInconsistentF64()
            val containingFace = faces[containingFaceId]
            PathArrangementWindingI32(
                first = containingFace.firstWinding.requireNotNullArrangementI32(),
                second = containingFace.secondWinding.requireNotNullArrangementI32(),
            )
        }
        propagateComponentWindingF64(component, initialWinding, halfEdges, faces)
        propagatedCount += 1
        children[componentId].forEach(pending::add)
    }
    if (propagatedCount != components.size) pathArrangementInconsistentF64()
}

private data class PathArrangementWindingI32(val first: Int, val second: Int)

private fun propagateComponentWindingF64(
    component: PathArrangementComponentI32,
    externalWinding: PathArrangementWindingI32,
    halfEdges: List<PathMutableHalfEdgeF64>,
    faces: List<PathMutableFaceI32>,
) {
    assignFaceWindingF64(faces[component.externalFaceId], externalWinding)
    val pending = mutableListOf(component.externalFaceId)
    var nextPending = 0
    while (nextPending < pending.size) {
        val face = faces[pending[nextPending++]]
        val firstWinding = face.firstWinding.requireNotNullArrangementI32()
        val secondWinding = face.secondWinding.requireNotNullArrangementI32()
        face.boundaryHalfEdgeIds.forEach { halfEdgeId ->
            val halfEdge = halfEdges[halfEdgeId]
            val oppositeFaceId = halfEdges[halfEdge.twinId].leftFaceId
            if (oppositeFaceId !in component.faceIds) pathArrangementInconsistentF64()
            val expected = PathArrangementWindingI32(
                first = (firstWinding.toLong() - halfEdge.firstWindingDelta.toLong()).toArrangementI32(),
                second = (secondWinding.toLong() - halfEdge.secondWindingDelta.toLong()).toArrangementI32(),
            )
            val opposite = faces[oppositeFaceId]
            if (opposite.firstWinding == null && opposite.secondWinding == null) {
                assignFaceWindingF64(opposite, expected)
                pending += oppositeFaceId
            } else if (opposite.firstWinding != expected.first || opposite.secondWinding != expected.second) {
                pathArrangementInconsistentF64()
            }
        }
    }
    if (component.faceIds.any { faceId -> faces[faceId].firstWinding == null || faces[faceId].secondWinding == null }) {
        pathArrangementInconsistentF64()
    }
}

private fun assignFaceWindingF64(face: PathMutableFaceI32, winding: PathArrangementWindingI32) {
    if (face.firstWinding == null && face.secondWinding == null) {
        face.firstWinding = winding.first
        face.secondWinding = winding.second
    } else if (face.firstWinding != winding.first || face.secondWinding != winding.second) {
        pathArrangementInconsistentF64()
    }
}

private fun containingFaceForWitnessF64(
    component: PathArrangementComponentI32,
    witnessComponent: PathArrangementComponentI32,
    vertices: List<PathVertexF64>,
    halfEdges: List<PathMutableHalfEdgeF64>,
    faces: List<PathMutableFaceI32>,
): Int? {
    val point = witnessComponent.externalSectorWitnessPointF64(vertices)
    val matchingFaceIds = component.faceIds.filter { faceId ->
        faceId != component.externalFaceId &&
            faceBoundaryContainsPointF64(faces[faceId], point, halfEdges, vertices)
    }
    if (matchingFaceIds.size > 1) pathArrangementInconsistentF64()
    return matchingFaceIds.singleOrNull()
}

private fun faceBoundaryContainsPointF64(
    face: PathMutableFaceI32,
    point: Point2F64,
    halfEdges: List<PathMutableHalfEdgeF64>,
    vertices: List<PathVertexF64>,
): Boolean {
    var winding = 0L
    face.boundaryHalfEdgeIds.forEach { halfEdgeId ->
        val halfEdge = halfEdges[halfEdgeId]
        val start = vertices[halfEdge.originVertexId].point
        val end = vertices[halfEdge.destinationVertexId].point
        // The child witness is an exact vertex of a separate component. Its certified external
        // sector is therefore wholly within one parent face unless it lies on a parent boundary;
        // that contact must already have been split into the same component by Task 3.
        if (PathPredicatesF64.onSegment(point, start, end)) pathArrangementInconsistentF64()
        // The half-open vertical intervals count an incident vertex exactly once. Together with
        // the exact orientation sign, this is a certified boundary winding rather than a sample.
        val startAtOrBelow = start.y <= point.y
        val endAbove = end.y > point.y
        val endAtOrBelow = end.y <= point.y
        if (startAtOrBelow && endAbove && OrientationPredicateF64.sign(start, end, point) > 0) {
            winding += 1L
        } else if (!startAtOrBelow && endAtOrBelow && OrientationPredicateF64.sign(start, end, point) < 0) {
            winding -= 1L
        }
    }
    return winding != 0L
}

private fun canonicalContourF64(
    rawVertexIds: List<Int>,
    vertices: List<PathVertexF64>,
): PathCanonicalContourF64? {
    val vertexIds = rawVertexIds.fold(mutableListOf<Int>()) { result, vertexId ->
        if (result.lastOrNull() != vertexId) result += vertexId
        result
    }
    if (vertexIds.size > 1 && vertexIds.first() == vertexIds.last()) vertexIds.removeAt(vertexIds.lastIndex)
    var removed = true
    while (removed && vertexIds.size >= 3) {
        removed = false
        vertexIds.indices.forEach { index ->
            if (removed) return@forEach
            val previous = vertices[vertexIds[(index - 1 + vertexIds.size) % vertexIds.size]].point
            val current = vertices[vertexIds[index]].point
            val next = vertices[vertexIds[(index + 1) % vertexIds.size]].point
            if (
                OrientationPredicateF64.sign(previous, current, next) == 0 &&
                PathPredicatesF64.onSegment(current, previous, next)
            ) {
                vertexIds.removeAt(index)
                removed = true
            }
        }
    }
    if (vertexIds.size < 3) return null
    val points = vertexIds.map { vertices[it].point }
    val closedPoints = points + points.first()
    val signedDoubleAreaExpansion = signedDoubleAreaExpansionF64(closedPoints)
    if (ExpansionF64.sign(signedDoubleAreaExpansion) == 0) return null
    val firstIndex = vertexIds.indices.minWithOrNull(
        Comparator { first, second -> compareContourStartF64(vertexIds[first], vertexIds[second], vertices) },
    ) ?: return null
    val rotatedIds = vertexIds.drop(firstIndex) + vertexIds.take(firstIndex)
    return PathCanonicalContourF64(
        contour = PathContourF64(
            rotatedIds.map { vertexId ->
                val vertex = vertices[vertexId]
                PathContourVertexF64(vertex.point, vertex.originalPointF32)
            },
        ),
        vertexIds = rotatedIds,
        signedDoubleAreaExpansion = signedDoubleAreaExpansion,
    )
}

private fun compareAbsoluteExpansionsF64(first: DoubleArray, second: DoubleArray): Int {
    val firstSign = ExpansionF64.sign(first)
    val secondSign = ExpansionF64.sign(second)
    if (firstSign == 0 || secondSign == 0) pathArrangementInconsistentF64()
    val firstAbsolute = if (firstSign > 0) first else first.negatedExpansionF64()
    val secondAbsolute = if (secondSign > 0) second else second.negatedExpansionF64()
    return ExpansionF64.sign(ExpansionF64.expansionDiff(firstAbsolute, secondAbsolute))
}

private fun DoubleArray.negatedExpansionF64(): DoubleArray = DoubleArray(size) { index -> -this[index] }

private fun compareOutgoingHalfEdgesF64(
    firstId: Int,
    secondId: Int,
    originVertexId: Int,
    halfEdges: List<PathMutableHalfEdgeF64>,
    vertices: List<PathVertexF64>,
): Int {
    if (firstId == secondId) return 0
    val first = halfEdges[firstId]
    val second = halfEdges[secondId]
    val firstQuadrant = outgoingQuadrantF64(first, vertices)
    val secondQuadrant = outgoingQuadrantF64(second, vertices)
    if (firstQuadrant != secondQuadrant) return firstQuadrant.compareTo(secondQuadrant)
    val origin = vertices[originVertexId].point
    val firstDestination = vertices[first.destinationVertexId].point
    val secondDestination = vertices[second.destinationVertexId].point
    val orientation = OrientationPredicateF64.sign(origin, firstDestination, secondDestination)
    if (orientation != 0) return -orientation
    return first.destinationVertexId.compareTo(second.destinationVertexId)
}

private fun compareOutputOutgoingHalfEdgesF64(
    firstId: Int,
    secondId: Int,
    originVertexId: Int,
    halfEdges: List<PathHalfEdgeF64>,
    vertices: List<PathVertexF64>,
): Int {
    if (firstId == secondId) return 0
    val first = halfEdges[firstId]
    val second = halfEdges[secondId]
    val firstQuadrant = outputOutgoingQuadrantF64(first, halfEdges, vertices)
    val secondQuadrant = outputOutgoingQuadrantF64(second, halfEdges, vertices)
    if (firstQuadrant != secondQuadrant) return firstQuadrant.compareTo(secondQuadrant)
    val origin = vertices[originVertexId].point
    val firstDestination = vertices[halfEdges[first.twinId].originVertexId].point
    val secondDestination = vertices[halfEdges[second.twinId].originVertexId].point
    val orientation = OrientationPredicateF64.sign(origin, firstDestination, secondDestination)
    if (orientation != 0) return -orientation
    return first.twinId.compareTo(second.twinId)
}

private fun sameOutgoingRayF64(
    first: PathMutableHalfEdgeF64,
    second: PathMutableHalfEdgeF64,
    vertices: List<PathVertexF64>,
): Boolean {
    if (outgoingQuadrantF64(first, vertices) != outgoingQuadrantF64(second, vertices)) return false
    val origin = vertices[first.originVertexId].point
    return OrientationPredicateF64.sign(
        origin,
        vertices[first.destinationVertexId].point,
        vertices[second.destinationVertexId].point,
    ) == 0
}

private fun outgoingQuadrantF64(halfEdge: PathMutableHalfEdgeF64, vertices: List<PathVertexF64>): Int {
    val origin = vertices[halfEdge.originVertexId].point
    val destination = vertices[halfEdge.destinationVertexId].point
    val deltaX = destination.x - origin.x
    val deltaY = destination.y - origin.y
    if (deltaX == 0.0 && deltaY == 0.0) pathArrangementInconsistentF64()
    return when {
        deltaX >= 0.0 && deltaY >= 0.0 -> 0
        deltaX < 0.0 && deltaY >= 0.0 -> 1
        deltaX < 0.0 && deltaY < 0.0 -> 2
        else -> 3
    }
}

private fun outputOutgoingQuadrantF64(
    halfEdge: PathHalfEdgeF64,
    halfEdges: List<PathHalfEdgeF64>,
    vertices: List<PathVertexF64>,
): Int {
    val origin = vertices[halfEdge.originVertexId].point
    val destination = vertices[halfEdges[halfEdge.twinId].originVertexId].point
    val deltaX = destination.x - origin.x
    val deltaY = destination.y - origin.y
    if (deltaX == 0.0 && deltaY == 0.0) pathArrangementInconsistentF64()
    return when {
        deltaX >= 0.0 && deltaY >= 0.0 -> 0
        deltaX < 0.0 && deltaY >= 0.0 -> 1
        deltaX < 0.0 && deltaY < 0.0 -> 2
        else -> 3
    }
}

private fun compareVerticesF64(firstId: Int, secondId: Int, vertices: List<PathVertexF64>): Int {
    if (firstId == secondId) return 0
    val first = vertices[firstId]
    val second = vertices[secondId]
    compareTopologicalCoordinatesF64(first.point.x, second.point.x).takeIf { it != 0 }?.let { return it }
    compareTopologicalCoordinatesF64(first.point.y, second.point.y).takeIf { it != 0 }?.let { return it }
    return first.id.compareTo(second.id)
}

private fun compareContourStartF64(firstId: Int, secondId: Int, vertices: List<PathVertexF64>): Int =
    compareVerticesF64(firstId, secondId, vertices)

private fun comparePathVertexIdentitiesF64(first: PathVertexIdentityF64, second: PathVertexIdentityF64): Int {
    first.incidentEdgeIds.size.compareTo(second.incidentEdgeIds.size).takeIf { it != 0 }?.let { return it }
    first.incidentEdgeIds.indices.forEach { index ->
        first.incidentEdgeIds[index].compareTo(second.incidentEdgeIds[index]).takeIf { it != 0 }?.let { return it }
    }
    val firstParameters = first.parameterByEdgeId.entries.sortedBy { it.key }
    val secondParameters = second.parameterByEdgeId.entries.sortedBy { it.key }
    firstParameters.size.compareTo(secondParameters.size).takeIf { it != 0 }?.let { return it }
    firstParameters.indices.forEach { index ->
        firstParameters[index].key.compareTo(secondParameters[index].key).takeIf { it != 0 }?.let { return it }
        firstParameters[index].value.toRawBits().compareTo(secondParameters[index].value.toRawBits())
            .takeIf { it != 0 }
            ?.let { return it }
    }
    val firstOriginal = first.originalPointF32
    val secondOriginal = second.originalPointF32
    when {
        firstOriginal == null && secondOriginal != null -> return -1
        firstOriginal != null && secondOriginal == null -> return 1
        firstOriginal != null && secondOriginal != null -> {
            firstOriginal.x.toRawBits().compareTo(secondOriginal.x.toRawBits()).takeIf { it != 0 }?.let { return it }
            firstOriginal.y.toRawBits().compareTo(secondOriginal.y.toRawBits()).takeIf { it != 0 }?.let { return it }
        }
    }
    return 0
}

private fun canonicalArrangementPointF64(point: Point2F64): Point2F64 = Point2F64(
    x = if (point.x == 0.0) 0.0 else point.x,
    y = if (point.y == 0.0) 0.0 else point.y,
)

private fun sameArrangementPointF64(first: Point2F64, second: Point2F64): Boolean =
    first.x == second.x && first.y == second.y

private fun compareTopologicalCoordinatesF64(first: Double, second: Double): Int = when {
    first == second -> 0
    first < second -> -1
    else -> 1
}

private fun Int.isFilledByI32(fillRule: FillRule): Boolean = when (fillRule) {
    FillRule.WINDING, FillRule.INVERSE_WINDING -> this != 0
    FillRule.EVEN_ODD, FillRule.INVERSE_EVEN_ODD -> this % 2 != 0
}

private fun PathBooleanOp.selectsF64(first: Boolean, second: Boolean): Boolean = when (this) {
    PathBooleanOp.DIFFERENCE -> first && !second
    PathBooleanOp.INTERSECT -> first && second
    PathBooleanOp.UNION -> first || second
    PathBooleanOp.XOR -> first != second
    PathBooleanOp.REVERSE_DIFFERENCE -> second && !first
}

private fun Int?.requireNotNullArrangementI32(): Int = this ?: pathArrangementInconsistentF64()

private fun Long.toArrangementI32(): Int {
    if (this !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) pathArrangementInconsistentF64()
    return toInt()
}

private fun Int.checkedAddI32(other: Int): Int = (toLong() + other.toLong()).toArrangementI32()

private fun Int.negatedArrangementDeltaI32(): Int {
    if (this == Int.MIN_VALUE) pathArrangementInconsistentF64()
    return -this
}

private fun pathArrangementInconsistentF64(): Nothing = throw IllegalStateException("path-arrangement-inconsistent")
