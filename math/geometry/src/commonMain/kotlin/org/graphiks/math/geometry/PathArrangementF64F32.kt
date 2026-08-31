package org.graphiks.math.geometry

import org.graphiks.math.vector.Vector2F64

internal data class PathHybridHalfEdgeF64F32(
    val idI32: Int,
    val originVertexIndexI32: Int,
    val destinationVertexIndexI32: Int,
    val twinIndexI32: Int,
    val nextIndexI32: Int,
    val leftFaceIndexI32: Int,
    val sourceSpanIdsI64: List<Long>,
    val carrierSectionsF64F32: List<PathHybridCarrierSectionF64F32>,
    val firstWindingDeltaI32: Int,
    val secondWindingDeltaI32: Int,
)

internal data class PathHybridFaceI32(
    val idI32: Int,
    val boundaryHalfEdgeIndicesI32: List<Int>,
    val firstWindingI32: Int,
    val secondWindingI32: Int,
)

internal data class PathBoundaryHalfEdgeTraceF64F32(
    val sourceSpanF64: PathSourceSpanF64,
    val sourceSectionF64: PathFlattenedSectionF64,
    val sectionIndexI32: Int,
    /** Complete canonical provenance group for this aggregated F32 rail. */
    val carrierSectionsF64F32: List<PathHybridCarrierSectionF64F32>,
    val originVertexF64F32: PathHybridVertexF64F32,
    val destinationVertexF64F32: PathHybridVertexF64F32,
    val forward: Boolean,
)

internal data class PathBoundaryTraceF64F32(
    val halfEdgesF64F32: List<PathBoundaryHalfEdgeTraceF64F32>,
)

internal class PathArrangementF64F32 private constructor(
    private val verticesF64F32: List<PathHybridVertexF64F32>,
    private val halfEdgesF64F32: List<PathHybridHalfEdgeF64F32>,
    private val facesI32: List<PathHybridFaceI32>,
    private val sourceSpansByIdI64: Map<Long, PathSourceSpanF64>,
    private val traceSpanByHalfEdgeI32: Map<Int, PathArrangementTraceSpanF64F32>,
    private val candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
) {
    fun boundary(
        firstFillRule: FillRule,
        secondFillRule: FillRule,
        operation: PathBooleanOp,
    ): List<PathBoundaryTraceF64F32> = extractBoundaryF64F32 { faceI32 ->
        operation.selectsHybridF64F32(
            faceI32.firstWindingI32.isFilledHybridF64F32(firstFillRule),
            faceI32.secondWindingI32.isFilledHybridF64F32(secondFillRule),
        )
    }

    fun unaryBoundary(fillRule: FillRule): List<PathBoundaryTraceF64F32> =
        extractBoundaryF64F32 { faceI32 -> faceI32.firstWindingI32.isFilledHybridF64F32(fillRule) }

    private fun extractBoundaryF64F32(selectsFace: (PathHybridFaceI32) -> Boolean): List<PathBoundaryTraceF64F32> {
        if (halfEdgesF64F32.isEmpty()) return emptyList()
        preflightArrangementF64F32(
            checkedPathWorkAddI64(
                checkedPathWorkAddI64(
                    facesI32.size.toLong(),
                    checkedPathWorkMultiplyI64(halfEdgesF64F32.size.toLong(), 10L),
                ),
                checkedPathWorkMultiplyI64(verticesF64F32.size.toLong(), 2L),
            ),
            candidateWorkBudgetI32,
        )
        val faceSelected = BooleanArray(facesI32.size) { faceIndexI32 -> selectsFace(facesI32[faceIndexI32]) }
        val selected = BooleanArray(halfEdgesF64F32.size)
        halfEdgesF64F32.forEach { halfEdgeF64F32 ->
            if (halfEdgeF64F32.idI32 > halfEdgeF64F32.twinIndexI32) return@forEach
            val leftSelected = faceSelected[halfEdgeF64F32.leftFaceIndexI32]
            val rightSelected = faceSelected[halfEdgesF64F32[halfEdgeF64F32.twinIndexI32].leftFaceIndexI32]
            if (leftSelected != rightSelected) {
                selected[if (leftSelected) halfEdgeF64F32.idI32 else halfEdgeF64F32.twinIndexI32] = true
            }
        }
        if (selected.none { it }) return emptyList()

        val outgoing = List(verticesF64F32.size) { mutableListOf<Int>() }
        halfEdgesF64F32.forEach { halfEdgeF64F32 -> outgoing[halfEdgeF64F32.originVertexIndexI32] += halfEdgeF64F32.idI32 }
        outgoing.forEach { halfEdgeIdsI32 ->
            sortHybridArrangementI32(halfEdgeIdsI32, candidateWorkBudgetI32) { firstI32, secondI32 ->
                compareHybridOutputRaysF64F32(firstI32, secondI32, traceSpanByHalfEdgeI32)
            }
            halfEdgeIdsI32.zipWithNext().forEach { (firstI32, secondI32) ->
                if (sameHybridOutputRayF64F32(firstI32, secondI32, traceSpanByHalfEdgeI32)) {
                    throw IllegalStateException("path-f32-projection-collapse")
                }
            }
        }

        val outgoingPositionI32 = IntArray(halfEdgesF64F32.size) { -1 }
        outgoing.forEach { halfEdgeIndicesI32 ->
            halfEdgeIndicesI32.forEachIndexed { positionI32, halfEdgeIndexI32 ->
                outgoingPositionI32[halfEdgeIndexI32] = positionI32
            }
        }

        val nextSelectedI32 = IntArray(halfEdgesF64F32.size) { -1 }
        val selectedTraversalWorkI64 = halfEdgesF64F32.indices.sumOf { halfEdgeIndexI32 ->
            if (!selected[halfEdgeIndexI32]) {
                0L
            } else {
                outgoing[halfEdgesF64F32[halfEdgesF64F32[halfEdgeIndexI32].twinIndexI32].originVertexIndexI32].size.toLong()
            }
        }
        preflightArrangementF64F32(
            checkedPathWorkAddI64(
                selectedTraversalWorkI64,
                checkedPathWorkMultiplyI64(halfEdgesF64F32.size.toLong(), 2L),
            ),
            candidateWorkBudgetI32,
        )
        halfEdgesF64F32.indices.forEach { halfEdgeIndexI32 ->
            if (!selected[halfEdgeIndexI32]) return@forEach
            val halfEdgeF64F32 = halfEdgesF64F32[halfEdgeIndexI32]
            val arrivalOutgoingI32 = outgoing[halfEdgesF64F32[halfEdgeF64F32.twinIndexI32].originVertexIndexI32]
            val twinPositionI32 = outgoingPositionI32[halfEdgeF64F32.twinIndexI32]
            if (twinPositionI32 < 0) pathHybridArrangementInconsistentF64F32()
            var scannedI32 = 0
            while (scannedI32 < arrivalOutgoingI32.size) {
                val candidateIndexI32 = (twinPositionI32 - scannedI32 - 1 + arrivalOutgoingI32.size) % arrivalOutgoingI32.size
                val candidateHalfEdgeIndexI32 = arrivalOutgoingI32[candidateIndexI32]
                val candidate = halfEdgesF64F32[candidateHalfEdgeIndexI32]
                val candidateLeft = faceSelected[candidate.leftFaceIndexI32]
                val candidateRight = faceSelected[halfEdgesF64F32[candidate.twinIndexI32].leftFaceIndexI32]
                if (!candidateLeft) pathHybridArrangementInconsistentF64F32()
                if (!candidateRight) {
                    if (!selected[candidateHalfEdgeIndexI32]) pathHybridArrangementInconsistentF64F32()
                    nextSelectedI32[halfEdgeIndexI32] = candidateHalfEdgeIndexI32
                    break
                }
                scannedI32 += 1
            }
            if (nextSelectedI32[halfEdgeIndexI32] < 0) pathHybridArrangementInconsistentF64F32()
        }

        val visited = BooleanArray(halfEdgesF64F32.size)
        val tracesF64F32 = mutableListOf<PathCanonicalTraceF64F32>()
        halfEdgesF64F32.indices.forEach { startIndexI32 ->
            if (!selected[startIndexI32] || visited[startIndexI32]) return@forEach
            val traceIndicesI32 = mutableListOf<Int>()
            var currentIndexI32 = startIndexI32
            var stepsI32 = 0
            while (true) {
                if (!selected[currentIndexI32] || (visited[currentIndexI32] && currentIndexI32 != startIndexI32)) {
                    pathHybridArrangementInconsistentF64F32()
                }
                if (currentIndexI32 == startIndexI32 && traceIndicesI32.isNotEmpty()) break
                visited[currentIndexI32] = true
                traceIndicesI32 += currentIndexI32
                currentIndexI32 = nextSelectedI32[currentIndexI32]
                stepsI32 += 1
                if (stepsI32 > halfEdgesF64F32.size) pathHybridArrangementInconsistentF64F32()
            }
            if (currentIndexI32 != startIndexI32 || traceIndicesI32.size < 3) pathHybridArrangementInconsistentF64F32()
            canonicalHybridTraceF64F32(
                rawHalfEdgeIndicesI32 = traceIndicesI32,
                halfEdgesF64F32 = halfEdgesF64F32,
                verticesF64F32 = verticesF64F32,
                traceSpanByHalfEdgeI32 = traceSpanByHalfEdgeI32,
                candidateWorkBudgetI32 = candidateWorkBudgetI32,
            )
                ?.let(tracesF64F32::add)
        }
        return sortedArrangementF64F32(tracesF64F32, candidateWorkBudgetI32) { firstF64F32, secondF64F32 ->
            compareHybridTraceStartF64F32(
                firstF64F32.halfEdgeIndicesI32.first(),
                secondF64F32.halfEdgeIndicesI32.first(),
                halfEdgesF64F32,
                verticesF64F32,
            )
        }.map(PathCanonicalTraceF64F32::traceF64F32)
    }

    companion object {
        fun build(
            topologyF64F32: PathHybridTopologyF64F32,
            limitsI32: PathOpsLimitsI32,
            candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
        ): PathArrangementF64F32 {
            preflightArrangementF64F32(
                checkedPathWorkMultiplyI64(topologyF64F32.sourceSpansF64.size.toLong(), 3L),
                candidateWorkBudgetI32,
            )
            val sourceSpansByIdI64 = mutableMapOf<Long, PathSourceSpanF64>()
            topologyF64F32.sourceSpansF64.forEach { sourceSpanF64 ->
                val previousF64 = sourceSpansByIdI64.put(sourceSpanF64.sourceSpanIdI64, sourceSpanF64)
                if (previousF64 != null && previousF64 != sourceSpanF64) {
                    throw IllegalStateException("path-arrangement-inconsistent")
                }
            }
            preflightArrangementF64F32(
                checkedPathWorkMultiplyI64(topologyF64F32.verticesF64F32.size.toLong(), 3L),
                candidateWorkBudgetI32,
            )
            val aliasesI32 = PathHybridDisjointSetI32(topologyF64F32.verticesF64F32.size)
            val sourceVertexIndexByIdentityF64 = mutableMapOf<PathVertexIdentityF64, Int>()
            topologyF64F32.verticesF64F32.forEachIndexed { indexI32, vertexF64F32 ->
                if (sourceVertexIndexByIdentityF64.put(vertexF64F32.vertexIdentityF64, indexI32) != null) {
                    pathHybridArrangementInconsistentF64F32()
                }
            }
            val aliasIdentityCountI64 = topologyF64F32.aliasGroupsF32.sumOf { aliasGroupF32 ->
                aliasGroupF32.vertexIdentitiesF64.size.toLong()
            }
            preflightArrangementF64F32(
                checkedPathWorkMultiplyI64(aliasIdentityCountI64, 3L),
                candidateWorkBudgetI32,
            )
            topologyF64F32.aliasGroupsF32.forEach { aliasGroupF32 ->
                val indicesI32 = mutableListOf<Int>()
                aliasGroupF32.vertexIdentitiesF64.forEach { identityF64 ->
                    indicesI32 += sourceVertexIndexByIdentityF64[identityF64]
                        ?: pathHybridArrangementInconsistentF64F32()
                }
                if (indicesI32.size > 1) {
                    indicesI32.drop(1).forEach { vertexIndexI32 ->
                        aliasesI32.union(indicesI32.first(), vertexIndexI32)
                    }
                }
            }
            // The hybrid topology admitted only intrinsic, witness-free flattening collapses.
            // Their endpoints are an exact adjacent source relation, not a coordinate-derived
            // alias; join precisely that pair so the following carrier sections remain one
            // continuous F32 contour.  Every collapsed carrier is consumed explicitly below.
            preflightArrangementF64F32(
                checkedPathWorkMultiplyI64(topologyF64F32.collapsedIncidencesF64F32.size.toLong(), 3L),
                candidateWorkBudgetI32,
            )
            topologyF64F32.collapsedIncidencesF64F32.forEach { collapsedF64F32 ->
                val startVertexIndexI32 = sourceVertexIndexByIdentityF64[
                    collapsedF64F32.sourceSectionF64.startIdentityF64
                ] ?: pathHybridArrangementInconsistentF64F32()
                val endVertexIndexI32 = sourceVertexIndexByIdentityF64[
                    collapsedF64F32.sourceSectionF64.endIdentityF64
                ] ?: pathHybridArrangementInconsistentF64F32()
                aliasesI32.union(startVertexIndexI32, endVertexIndexI32)
            }
            val canonicalVerticesF64F32 = canonicalHybridVerticesF64F32(
                topologyF64F32.verticesF64F32,
                aliasesI32,
                candidateWorkBudgetI32,
            )
            val finalVertexCountI64 = canonicalVerticesF64F32.verticesF64F32.size.toLong()
            if (finalVertexCountI64 > limitsI32.maxVertices.toLong()) throw IllegalStateException("path-vertex-limit")

            preflightArrangementF64F32(
                checkedPathWorkMultiplyI64(topologyF64F32.carrierSectionsF64F32.size.toLong(), 10L),
                candidateWorkBudgetI32,
            )
            val contributionsByKeyF64F32 = mutableMapOf<PathHybridEdgeKeyI32, PathHybridEdgeContributionF64F32>()
            val remainingCollapsedCarrierKeysF64F32 = topologyF64F32.collapsedIncidencesF64F32
                .mapTo(mutableSetOf()) { collapsedF64F32 ->
                    PathHybridCarrierKeyF64F32(
                        sourceSpanIdI64 = collapsedF64F32.sourceSpanF64.sourceSpanIdI64,
                        sectionIndexI32 = collapsedF64F32.sectionIndexI32,
                    )
                }
            topologyF64F32.carrierSectionsF64F32.forEach { carrierSectionF64F32 ->
                val sourceSpanF64 = carrierSectionF64F32.sourceSpanF64
                val sourceSectionF64 = carrierSectionF64F32.sourceSectionF64
                val startIdentityF64 = sourceSectionF64.startIdentityF64
                val endIdentityF64 = sourceSectionF64.endIdentityF64
                val startVertexIndexI32 = canonicalVerticesF64F32.indexByIdentityF64[startIdentityF64]
                    ?: throw IllegalStateException("path-arrangement-inconsistent")
                val endVertexIndexI32 = canonicalVerticesF64F32.indexByIdentityF64[endIdentityF64]
                    ?: throw IllegalStateException("path-arrangement-inconsistent")
                val startVertexF64F32 = canonicalVerticesF64F32.verticesF64F32[startVertexIndexI32]
                val endVertexF64F32 = canonicalVerticesF64F32.verticesF64F32[endVertexIndexI32]
                if (sameArrangementHybridPointF32(startVertexF64F32.representativePointF32, endVertexF64F32.representativePointF32)) {
                    val collapsedKeyF64F32 = PathHybridCarrierKeyF64F32(
                        sourceSpanIdI64 = sourceSpanF64.sourceSpanIdI64,
                        sectionIndexI32 = carrierSectionF64F32.sectionIndexI32,
                    )
                    if (!remainingCollapsedCarrierKeysF64F32.remove(collapsedKeyF64F32)) {
                        throw IllegalStateException("path-f32-projection-collapse")
                    }
                    return@forEach
                }
                val forward = startVertexIndexI32 < endVertexIndexI32
                val keyI32 = if (forward) {
                    PathHybridEdgeKeyI32(startVertexIndexI32, endVertexIndexI32)
                } else {
                    PathHybridEdgeKeyI32(endVertexIndexI32, startVertexIndexI32)
                }
                val contributionF64F32 = contributionsByKeyF64F32.getOrPut(keyI32) { PathHybridEdgeContributionF64F32() }
                val deltaI64 = if (forward) sourceSpanF64.windingDeltaI32.toLong() else -sourceSpanF64.windingDeltaI32.toLong()
                when (sourceSpanF64.operand) {
                    PathOperand.FIRST -> contributionF64F32.firstWindingDeltaI64 += deltaI64
                    PathOperand.SECOND -> contributionF64F32.secondWindingDeltaI64 += deltaI64
                }
                contributionF64F32.carrierSectionsF64F32 += carrierSectionF64F32
                contributionF64F32.forwardByCarrierKeyF64F32[
                    PathHybridCarrierKeyF64F32(sourceSpanF64.sourceSpanIdI64, carrierSectionF64F32.sectionIndexI32)
                ] = forward
            }
            if (remainingCollapsedCarrierKeysF64F32.isNotEmpty()) {
                throw IllegalStateException("path-f32-projection-collapse")
            }
            preflightArrangementF64F32(
                checkedPathWorkMultiplyI64(topologyF64F32.carrierSectionsF64F32.size.toLong(), 2L),
                candidateWorkBudgetI32,
            )
            val pathEdgesF64F32 = sortedArrangementF64F32(
                contributionsByKeyF64F32.entries.filter { (_, contributionF64F32) ->
                    contributionF64F32.firstWindingDeltaI64 != 0L || contributionF64F32.secondWindingDeltaI64 != 0L
                },
                candidateWorkBudgetI32,
            ) { firstEntryF64F32, secondEntryF64F32 ->
                firstEntryF64F32.key.startVertexIndexI32.compareTo(secondEntryF64F32.key.startVertexIndexI32)
                    .takeIf { it != 0 } ?: firstEntryF64F32.key.endVertexIndexI32.compareTo(secondEntryF64F32.key.endVertexIndexI32)
            }
            val finalHalfEdgeCountI64 = checkedPathWorkMultiplyI64(pathEdgesF64F32.size.toLong(), 2L)
            if (finalHalfEdgeCountI64 > limitsI32.maxHalfEdges.toLong()) {
                throw IllegalStateException("path-half-edge-limit")
            }
            if (pathEdgesF64F32.isEmpty()) {
                return PathArrangementF64F32(
                    canonicalVerticesF64F32.verticesF64F32,
                    emptyList(),
                    emptyList(),
                    sourceSpansByIdI64,
                    emptyMap(),
                    candidateWorkBudgetI32,
                )
            }

            val contributingCarrierCountI64 = pathEdgesF64F32.sumOf { (_, contributionF64F32) ->
                contributionF64F32.carrierSectionsF64F32.size.toLong()
            }
            preflightArrangementF64F32(
                checkedPathWorkAddI64(
                    checkedPathWorkMultiplyI64(pathEdgesF64F32.size.toLong(), 12L),
                    checkedPathWorkMultiplyI64(contributingCarrierCountI64, 5L),
                ),
                candidateWorkBudgetI32,
            )
            val mutableHalfEdgesF64F32 = ArrayList<PathMutableHybridHalfEdgeF64F32>(
                checkedPathCapacityI32(finalHalfEdgeCountI64, "path-half-edge-limit"),
            )
            val traceSpansByHalfEdgeI32 = mutableMapOf<Int, PathArrangementTraceSpanF64F32>()
            pathEdgesF64F32.forEachIndexed { edgeIndexI32, (keyI32, contributionF64F32) ->
                val forwardIdI32 = edgeIndexI32 * 2
                val reverseIdI32 = forwardIdI32 + 1
                val traceCarrierGroupF64F32 = canonicalTraceCarrierGroupF64F32(
                    carrierSectionsF64F32 = contributionF64F32.carrierSectionsF64F32,
                    forwardByCarrierKeyF64F32 = contributionF64F32.forwardByCarrierKeyF64F32,
                    candidateWorkBudgetI32 = candidateWorkBudgetI32,
                )
                val traceCarrierSectionF64F32 = traceCarrierGroupF64F32.canonicalCarrierSectionF64F32
                val forwardByCarrier = traceCarrierGroupF64F32.canonicalForward
                val forwardDirectionF64 = hybridSourceDirectionF64F32(traceCarrierSectionF64F32, forwardByCarrier)
                mutableHalfEdgesF64F32 += PathMutableHybridHalfEdgeF64F32(
                    idI32 = forwardIdI32,
                    originVertexIndexI32 = keyI32.startVertexIndexI32,
                    destinationVertexIndexI32 = keyI32.endVertexIndexI32,
                    twinIndexI32 = reverseIdI32,
                    sourceSpanIdsI64 = contributionF64F32.carrierSectionsF64F32.map { carrierSectionF64F32 ->
                        carrierSectionF64F32.sourceSpanF64.sourceSpanIdI64
                    },
                    carrierSectionsF64F32 = traceCarrierGroupF64F32.carrierSectionsF64F32,
                    firstWindingDeltaI32 = contributionF64F32.firstWindingDeltaI64.toHybridI32(),
                    secondWindingDeltaI32 = contributionF64F32.secondWindingDeltaI64.toHybridI32(),
                    sourceDirectionF64 = forwardDirectionF64,
                )
                mutableHalfEdgesF64F32 += PathMutableHybridHalfEdgeF64F32(
                    idI32 = reverseIdI32,
                    originVertexIndexI32 = keyI32.endVertexIndexI32,
                    destinationVertexIndexI32 = keyI32.startVertexIndexI32,
                    twinIndexI32 = forwardIdI32,
                    sourceSpanIdsI64 = contributionF64F32.carrierSectionsF64F32.map { carrierSectionF64F32 ->
                        carrierSectionF64F32.sourceSpanF64.sourceSpanIdI64
                    },
                    carrierSectionsF64F32 = traceCarrierGroupF64F32.carrierSectionsF64F32,
                    firstWindingDeltaI32 = contributionF64F32.firstWindingDeltaI64.toHybridI32().negatedHybridI32(),
                    secondWindingDeltaI32 = contributionF64F32.secondWindingDeltaI64.toHybridI32().negatedHybridI32(),
                    sourceDirectionF64 = -forwardDirectionF64,
                )
                traceSpansByHalfEdgeI32[forwardIdI32] = traceCarrierGroupF64F32
                traceSpansByHalfEdgeI32[reverseIdI32] = PathArrangementTraceSpanF64F32(
                    canonicalCarrierSectionF64F32 = traceCarrierSectionF64F32,
                    canonicalForward = !forwardByCarrier,
                    carrierSectionsF64F32 = traceCarrierGroupF64F32.carrierSectionsF64F32,
                    forwardByCarrierKeyF64F32 = traceCarrierGroupF64F32.forwardByCarrierKeyF64F32.mapValues { (_, forward) -> !forward },
                )
            }
            val outgoingI32 = List(canonicalVerticesF64F32.verticesF64F32.size) { mutableListOf<Int>() }
            mutableHalfEdgesF64F32.forEach { halfEdgeF64F32 -> outgoingI32[halfEdgeF64F32.originVertexIndexI32] += halfEdgeF64F32.idI32 }
            outgoingI32.forEachIndexed { vertexIndexI32, halfEdgeIndicesI32 ->
                sortHybridArrangementI32(halfEdgeIndicesI32, candidateWorkBudgetI32) { firstI32, secondI32 ->
                    compareHybridOutgoingRaysF64F32(
                        firstI32,
                        secondI32,
                        vertexIndexI32,
                        mutableHalfEdgesF64F32,
                    )
                }
                halfEdgeIndicesI32.zipWithNext().forEach { (firstI32, secondI32) ->
                    if (sameHybridOutgoingRayF64F32(mutableHalfEdgesF64F32[firstI32], mutableHalfEdgesF64F32[secondI32])) {
                        throw IllegalStateException("path-f32-projection-collapse")
                    }
                }
            }
            preflightArrangementF64F32(
                checkedPathWorkMultiplyI64(mutableHalfEdgesF64F32.size.toLong(), 5L),
                candidateWorkBudgetI32,
            )
            val outgoingPositionI32 = IntArray(mutableHalfEdgesF64F32.size) { -1 }
            outgoingI32.forEach { indicesI32 -> indicesI32.forEachIndexed { indexI32, halfEdgeIndexI32 -> outgoingPositionI32[halfEdgeIndexI32] = indexI32 } }
            val incomingNextCountI32 = IntArray(mutableHalfEdgesF64F32.size)
            mutableHalfEdgesF64F32.forEach { halfEdgeF64F32 ->
                val destinationOutgoingI32 = outgoingI32[halfEdgeF64F32.destinationVertexIndexI32]
                val twinPositionI32 = outgoingPositionI32[halfEdgeF64F32.twinIndexI32]
                if (destinationOutgoingI32.isEmpty() || twinPositionI32 < 0) pathHybridArrangementInconsistentF64F32()
                val nextPositionI32 = if (twinPositionI32 == 0) destinationOutgoingI32.lastIndex else twinPositionI32 - 1
                halfEdgeF64F32.nextIndexI32 = destinationOutgoingI32[nextPositionI32]
                incomingNextCountI32[halfEdgeF64F32.nextIndexI32] += 1
            }
            if (incomingNextCountI32.any { countI32 -> countI32 != 1 }) pathHybridArrangementInconsistentF64F32()

            val mutableFacesI32 = enumerateHybridFacesF64F32(mutableHalfEdgesF64F32, candidateWorkBudgetI32)
            preflightArrangementF64F32(pathEdgesF64F32.size.toLong(), candidateWorkBudgetI32)
            val pathEdgeKeysI32 = pathEdgesF64F32.map { (keyI32, _) -> keyI32 }
            val componentsI32 = hybridComponentsF64F32(
                pathEdgeKeysI32,
                canonicalVerticesF64F32.verticesF64F32,
                outgoingI32,
                mutableHalfEdgesF64F32,
                mutableFacesI32,
                candidateWorkBudgetI32,
            )
            propagateHybridWindingsF64F32(
                componentsI32,
                canonicalVerticesF64F32.verticesF64F32,
                mutableHalfEdgesF64F32,
                mutableFacesI32,
                candidateWorkBudgetI32,
            )
            preflightArrangementF64F32(
                checkedPathWorkAddI64(
                    mutableHalfEdgesF64F32.size.toLong(),
                    mutableFacesI32.size.toLong(),
                ),
                candidateWorkBudgetI32,
            )
            val halfEdgesF64F32 = mutableHalfEdgesF64F32.map { halfEdgeF64F32 ->
                PathHybridHalfEdgeF64F32(
                    idI32 = halfEdgeF64F32.idI32,
                    originVertexIndexI32 = halfEdgeF64F32.originVertexIndexI32,
                    destinationVertexIndexI32 = halfEdgeF64F32.destinationVertexIndexI32,
                    twinIndexI32 = halfEdgeF64F32.twinIndexI32,
                    nextIndexI32 = halfEdgeF64F32.nextIndexI32,
                    leftFaceIndexI32 = halfEdgeF64F32.leftFaceIndexI32,
                    sourceSpanIdsI64 = halfEdgeF64F32.sourceSpanIdsI64,
                    carrierSectionsF64F32 = halfEdgeF64F32.carrierSectionsF64F32,
                    firstWindingDeltaI32 = halfEdgeF64F32.firstWindingDeltaI32,
                    secondWindingDeltaI32 = halfEdgeF64F32.secondWindingDeltaI32,
                )
            }
            val facesI32 = mutableFacesI32.map { faceI32 ->
                PathHybridFaceI32(
                    idI32 = faceI32.idI32,
                    boundaryHalfEdgeIndicesI32 = faceI32.boundaryHalfEdgeIndicesI32,
                    firstWindingI32 = faceI32.firstWindingI32 ?: pathHybridArrangementInconsistentF64F32(),
                    secondWindingI32 = faceI32.secondWindingI32 ?: pathHybridArrangementInconsistentF64F32(),
                )
            }
            return PathArrangementF64F32(
                canonicalVerticesF64F32.verticesF64F32,
                halfEdgesF64F32,
                facesI32,
                sourceSpansByIdI64,
                traceSpansByHalfEdgeI32,
                candidateWorkBudgetI32,
            )
        }
    }
}

private data class PathHybridCanonicalVerticesF64F32(
    val verticesF64F32: List<PathHybridVertexF64F32>,
    val indexByIdentityF64: Map<PathVertexIdentityF64, Int>,
)

private data class PathHybridEdgeKeyI32(
    val startVertexIndexI32: Int,
    val endVertexIndexI32: Int,
)

private class PathHybridEdgeContributionF64F32(
    var firstWindingDeltaI64: Long = 0L,
    var secondWindingDeltaI64: Long = 0L,
    val carrierSectionsF64F32: MutableList<PathHybridCarrierSectionF64F32> = mutableListOf(),
    val forwardByCarrierKeyF64F32: MutableMap<PathHybridCarrierKeyF64F32, Boolean> = mutableMapOf(),
)

private data class PathArrangementTraceSpanF64F32(
    val canonicalCarrierSectionF64F32: PathHybridCarrierSectionF64F32,
    val canonicalForward: Boolean,
    val carrierSectionsF64F32: List<PathHybridCarrierSectionF64F32>,
    val forwardByCarrierKeyF64F32: Map<PathHybridCarrierKeyF64F32, Boolean>,
)

private data class PathHybridCarrierKeyF64F32(
    val sourceSpanIdI64: Long,
    val sectionIndexI32: Int,
)

private class PathMutableHybridHalfEdgeF64F32(
    val idI32: Int,
    val originVertexIndexI32: Int,
    val destinationVertexIndexI32: Int,
    val twinIndexI32: Int,
    val sourceSpanIdsI64: List<Long>,
    val carrierSectionsF64F32: List<PathHybridCarrierSectionF64F32>,
    val firstWindingDeltaI32: Int,
    val secondWindingDeltaI32: Int,
    val sourceDirectionF64: Vector2F64,
    var nextIndexI32: Int = -1,
    var leftFaceIndexI32: Int = -1,
)

private class PathMutableHybridFaceI32(
    val idI32: Int,
    val boundaryHalfEdgeIndicesI32: List<Int>,
    var firstWindingI32: Int? = null,
    var secondWindingI32: Int? = null,
)

private class PathHybridComponentI32(
    val idI32: Int,
    val vertexIndicesI32: List<Int>,
    val faceIndicesI32: MutableList<Int> = mutableListOf(),
    var witnessVertexIndexI32: Int = -1,
    var externalFaceIndexI32: Int = -1,
)

private data class PathCanonicalTraceF64F32(
    val traceF64F32: PathBoundaryTraceF64F32,
    val halfEdgeIndicesI32: List<Int>,
    val signedDoubleAreaF64: DoubleArray,
)

private class PathHybridDisjointSetI32(sizeI32: Int) {
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

private fun canonicalHybridVerticesF64F32(
    sourceVerticesF64F32: List<PathHybridVertexF64F32>,
    aliasesI32: PathHybridDisjointSetI32,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): PathHybridCanonicalVerticesF64F32 {
    preflightArrangementF64F32(
        checkedPathWorkMultiplyI64(sourceVerticesF64F32.size.toLong(), 5L),
        candidateWorkBudgetI32,
    )
    val groupsByRootI32 = mutableMapOf<Int, MutableList<PathHybridVertexF64F32>>()
    sourceVerticesF64F32.forEachIndexed { sourceIndexI32, vertexF64F32 ->
        groupsByRootI32.getOrPut(aliasesI32.find(sourceIndexI32)) { mutableListOf() } += vertexF64F32
    }
    val groupsF64F32 = groupsByRootI32.map { (rootI32, verticesF64F32) ->
        PathHybridVertexGroupF64F32(
            rootI32 = rootI32,
            verticesF64F32 = verticesF64F32,
            representativeF64F32 = selectCanonicalHybridVertexF64F32(verticesF64F32, candidateWorkBudgetI32),
        )
    }
    val orderedGroupsF64F32 = sortedArrangementF64F32(groupsF64F32, candidateWorkBudgetI32) { firstF64F32, secondF64F32 ->
        compareArrangementVerticesF64F32(firstF64F32.representativeF64F32, secondF64F32.representativeF64F32)
    }
    val representativesF64F32 = orderedGroupsF64F32.map(PathHybridVertexGroupF64F32::representativeF64F32)
    val representativeIndexByRootI32 = mutableMapOf<Int, Int>()
    preflightArrangementF64F32(
        checkedPathWorkMultiplyI64(orderedGroupsF64F32.size.toLong(), 2L),
        candidateWorkBudgetI32,
    )
    orderedGroupsF64F32.forEachIndexed { indexI32, groupF64F32 ->
        representativeIndexByRootI32[groupF64F32.rootI32] = indexI32
    }
    val indexByIdentityF64 = mutableMapOf<PathVertexIdentityF64, Int>()
    preflightArrangementF64F32(
        checkedPathWorkMultiplyI64(sourceVerticesF64F32.size.toLong(), 2L),
        candidateWorkBudgetI32,
    )
    sourceVerticesF64F32.forEachIndexed { sourceIndexI32, vertexF64F32 ->
        val rootI32 = aliasesI32.find(sourceIndexI32)
        indexByIdentityF64[vertexF64F32.vertexIdentityF64] = representativeIndexByRootI32[rootI32]
            ?: pathHybridArrangementInconsistentF64F32()
    }
    return PathHybridCanonicalVerticesF64F32(representativesF64F32, indexByIdentityF64)
}

private data class PathHybridVertexGroupF64F32(
    val rootI32: Int,
    val verticesF64F32: List<PathHybridVertexF64F32>,
    val representativeF64F32: PathHybridVertexF64F32,
)

private fun selectCanonicalHybridVertexF64F32(
    verticesF64F32: List<PathHybridVertexF64F32>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): PathHybridVertexF64F32 {
    val firstF64F32 = verticesF64F32.firstOrNull() ?: pathHybridArrangementInconsistentF64F32()
    preflightArrangementF64F32(
        checkedPathWorkMultiplyI64(verticesF64F32.size.toLong(), 2L),
        candidateWorkBudgetI32,
    )
    var representativeF64F32 = firstF64F32
    verticesF64F32.drop(1).forEach { vertexF64F32 ->
        if (!sameArrangementHybridPointF32(vertexF64F32.representativePointF32, firstF64F32.representativePointF32)) {
            throw IllegalStateException("path-f32-projection-collapse")
        }
        if (compareArrangementVerticesF64F32(vertexF64F32, representativeF64F32) < 0) {
            representativeF64F32 = vertexF64F32
        }
    }
    return representativeF64F32
}

/**
 * Retains every source carrier which contributed to one aggregated F32 rail.  A single carrier
 * only supplies the writer's concrete trace, but all carriers remain attached to the half-edge
 * for winding/provenance and must agree on their local F64 ray.  There is deliberately no source
 * label or storage ID in this canonical selection.
 */
private fun canonicalTraceCarrierGroupF64F32(
    carrierSectionsF64F32: List<PathHybridCarrierSectionF64F32>,
    forwardByCarrierKeyF64F32: Map<PathHybridCarrierKeyF64F32, Boolean>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): PathArrangementTraceSpanF64F32 {
    val orderedCarrierSectionsF64F32 = sortedArrangementF64F32(
        carrierSectionsF64F32,
        candidateWorkBudgetI32,
        ::compareTraceCarrierSectionsF64F32,
    )
    val canonicalCarrierSectionF64F32 = orderedCarrierSectionsF64F32.firstOrNull()
        ?: pathHybridArrangementInconsistentF64F32()
    val canonicalCarrierKeyF64F32 = PathHybridCarrierKeyF64F32(
        canonicalCarrierSectionF64F32.sourceSpanF64.sourceSpanIdI64,
        canonicalCarrierSectionF64F32.sectionIndexI32,
    )
    val canonicalForward = forwardByCarrierKeyF64F32[canonicalCarrierKeyF64F32]
        ?: pathHybridArrangementInconsistentF64F32()
    val canonicalDirectionF64 = hybridSourceDirectionF64F32(canonicalCarrierSectionF64F32, canonicalForward)
    preflightArrangementF64F32(
        checkedPathWorkMultiplyI64(orderedCarrierSectionsF64F32.size.toLong(), 3L),
        candidateWorkBudgetI32,
    )
    orderedCarrierSectionsF64F32.drop(1).forEach { carrierSectionF64F32 ->
        val carrierKeyF64F32 = PathHybridCarrierKeyF64F32(
            carrierSectionF64F32.sourceSpanF64.sourceSpanIdI64,
            carrierSectionF64F32.sectionIndexI32,
        )
        val forward = forwardByCarrierKeyF64F32[carrierKeyF64F32]
            ?: pathHybridArrangementInconsistentF64F32()
        val directionF64 = hybridSourceDirectionF64F32(carrierSectionF64F32, forward)
        if (!sameHybridDirectionsF64F32(canonicalDirectionF64, directionF64)) {
            throw IllegalStateException("path-f32-projection-collapse")
        }
    }
    return PathArrangementTraceSpanF64F32(
        canonicalCarrierSectionF64F32 = canonicalCarrierSectionF64F32,
        canonicalForward = canonicalForward,
        carrierSectionsF64F32 = orderedCarrierSectionsF64F32,
        forwardByCarrierKeyF64F32 = forwardByCarrierKeyF64F32,
    )
}

private fun compareTraceCarrierSectionsF64F32(
    firstF64F32: PathHybridCarrierSectionF64F32,
    secondF64F32: PathHybridCarrierSectionF64F32,
): Int {
    compareArrangementPointsF64F32(
        firstF64F32.sourceSectionF64.startPointF64,
        secondF64F32.sourceSectionF64.startPointF64,
    ).takeIf { it != 0 }?.let { return it }
    compareArrangementPointsF64F32(
        firstF64F32.sourceSectionF64.endPointF64,
        secondF64F32.sourceSectionF64.endPointF64,
    ).takeIf { it != 0 }?.let { return it }
    return firstF64F32.sourceSpanF64.windingDeltaI32.compareTo(secondF64F32.sourceSpanF64.windingDeltaI32)
}

private fun hybridSourceDirectionF64F32(
    carrierSectionF64F32: PathHybridCarrierSectionF64F32,
    forward: Boolean,
): Vector2F64 {
    val directionF64 = carrierSectionF64F32.sourceSectionF64.endPointF64 -
        carrierSectionF64F32.sourceSectionF64.startPointF64
    if (directionF64.x == 0.0 && directionF64.y == 0.0) throw IllegalStateException("path-f32-projection-collapse")
    return if (forward) directionF64 else -directionF64
}

private fun enumerateHybridFacesF64F32(
    halfEdgesF64F32: List<PathMutableHybridHalfEdgeF64F32>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): List<PathMutableHybridFaceI32> {
    preflightArrangementF64F32(
        checkedPathWorkMultiplyI64(halfEdgesF64F32.size.toLong(), 3L),
        candidateWorkBudgetI32,
    )
    val facesI32 = mutableListOf<PathMutableHybridFaceI32>()
    halfEdgesF64F32.forEach { startF64F32 ->
        if (startF64F32.leftFaceIndexI32 != -1) return@forEach
        val boundaryI32 = mutableListOf<Int>()
        var currentI32 = startF64F32.idI32
        var stepsI32 = 0
        while (true) {
            if (currentI32 == startF64F32.idI32 && boundaryI32.isNotEmpty()) break
            if (currentI32 !in halfEdgesF64F32.indices || halfEdgesF64F32[currentI32].leftFaceIndexI32 != -1) {
                pathHybridArrangementInconsistentF64F32()
            }
            halfEdgesF64F32[currentI32].leftFaceIndexI32 = facesI32.size
            boundaryI32 += currentI32
            currentI32 = halfEdgesF64F32[currentI32].nextIndexI32
            stepsI32 += 1
            if (stepsI32 > halfEdgesF64F32.size) pathHybridArrangementInconsistentF64F32()
        }
        facesI32 += PathMutableHybridFaceI32(facesI32.size, boundaryI32)
    }
    return facesI32
}

private fun hybridComponentsF64F32(
    pathEdgeKeysI32: List<PathHybridEdgeKeyI32>,
    verticesF64F32: List<PathHybridVertexF64F32>,
    outgoingI32: List<List<Int>>,
    halfEdgesF64F32: List<PathMutableHybridHalfEdgeF64F32>,
    facesI32: List<PathMutableHybridFaceI32>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): List<PathHybridComponentI32> {
    preflightArrangementF64F32(
        checkedPathWorkAddI64(
            checkedPathWorkAddI64(
                checkedPathWorkMultiplyI64(pathEdgeKeysI32.size.toLong(), 3L),
                checkedPathWorkMultiplyI64(verticesF64F32.size.toLong(), 8L),
            ),
            checkedPathWorkAddI64(
                checkedPathWorkMultiplyI64(halfEdgesF64F32.size.toLong(), 4L),
                checkedPathWorkMultiplyI64(facesI32.size.toLong(), 3L),
            ),
        ),
        candidateWorkBudgetI32,
    )
    val disjointSetI32 = PathHybridDisjointSetI32(verticesF64F32.size)
    pathEdgeKeysI32.forEach { keyI32 -> disjointSetI32.union(keyI32.startVertexIndexI32, keyI32.endVertexIndexI32) }
    val verticesByRootI32 = mutableMapOf<Int, MutableList<Int>>()
    verticesF64F32.indices.forEach { vertexIndexI32 ->
        if (outgoingI32[vertexIndexI32].isNotEmpty()) {
            verticesByRootI32.getOrPut(disjointSetI32.find(vertexIndexI32)) { mutableListOf() } += vertexIndexI32
        }
    }
    val unorderedComponentsI32 = verticesByRootI32.values.map { indicesI32 ->
        PathHybridComponentI32(
            idI32 = -1,
            vertexIndicesI32 = sortedArrangementF64F32(indicesI32, candidateWorkBudgetI32) { firstI32, secondI32 ->
                compareArrangementVerticesF64F32(verticesF64F32[firstI32], verticesF64F32[secondI32])
            },
        )
    }
    val componentsI32 = sortedArrangementF64F32(unorderedComponentsI32, candidateWorkBudgetI32) { firstI32, secondI32 ->
        compareArrangementVerticesF64F32(
            verticesF64F32[firstI32.vertexIndicesI32.first()],
            verticesF64F32[secondI32.vertexIndicesI32.first()],
        )
    }.mapIndexed { indexI32, componentI32 -> PathHybridComponentI32(indexI32, componentI32.vertexIndicesI32) }
    val componentByRootI32 = mutableMapOf<Int, PathHybridComponentI32>()
    componentsI32.forEach { componentI32 -> componentI32.vertexIndicesI32.forEach { vertexIndexI32 -> componentByRootI32[disjointSetI32.find(vertexIndexI32)] = componentI32 } }
    facesI32.forEach { faceI32 ->
        val rootsI32 = linkedSetOf<Int>()
        faceI32.boundaryHalfEdgeIndicesI32.forEach { halfEdgeIndexI32 ->
            rootsI32 += disjointSetI32.find(halfEdgesF64F32[halfEdgeIndexI32].originVertexIndexI32)
        }
        if (rootsI32.size != 1) pathHybridArrangementInconsistentF64F32()
        componentByRootI32.getValue(rootsI32.single()).faceIndicesI32 += faceI32.idI32
    }
    componentsI32.forEach { componentI32 ->
        if (componentI32.faceIndicesI32.isEmpty()) pathHybridArrangementInconsistentF64F32()
        componentI32.witnessVertexIndexI32 = componentI32.vertexIndicesI32.first()
        val pointF32 = verticesF64F32[componentI32.witnessVertexIndexI32].representativePointF32
        val candidatesI32 = outgoingI32[componentI32.witnessVertexIndexI32]
        if (candidatesI32.isEmpty()) pathHybridArrangementInconsistentF64F32()
        candidatesI32.forEach { halfEdgeIndexI32 ->
            if (verticesF64F32[halfEdgesF64F32[halfEdgeIndexI32].destinationVertexIndexI32].representativePointF32.x < pointF32.x) {
                pathHybridArrangementInconsistentF64F32()
            }
        }
        var beforeLeftI32 = -1
        candidatesI32.forEachIndexed { indexI32, halfEdgeIndexI32 ->
            if (hybridQuadrantF64F32(halfEdgesF64F32[halfEdgeIndexI32].sourceDirectionF64) <= 1) {
                beforeLeftI32 = indexI32
            }
        }
        val witnessHalfEdgeI32 = candidatesI32[if (beforeLeftI32 >= 0) beforeLeftI32 else candidatesI32.lastIndex]
        componentI32.externalFaceIndexI32 = halfEdgesF64F32[witnessHalfEdgeI32].leftFaceIndexI32
    }
    return componentsI32
}

private fun propagateHybridWindingsF64F32(
    componentsI32: List<PathHybridComponentI32>,
    verticesF64F32: List<PathHybridVertexF64F32>,
    halfEdgesF64F32: List<PathMutableHybridHalfEdgeF64F32>,
    facesI32: List<PathMutableHybridFaceI32>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
) {
    preflightHybridWindingF64F32(componentsI32, facesI32, candidateWorkBudgetI32)
    val parentByComponentI32 = IntArray(componentsI32.size) { -1 }
    val parentFaceByComponentI32 = IntArray(componentsI32.size) { -1 }
    componentsI32.forEach { componentI32 ->
        var parentI32 = -1
        var parentFaceI32 = -1
        componentsI32.forEach { candidateI32 ->
            if (candidateI32.idI32 == componentI32.idI32) return@forEach
            val candidateFaceI32 = hybridContainingFaceF64F32(
                candidateI32,
                componentI32,
                verticesF64F32,
                halfEdgesF64F32,
                facesI32,
            )
                ?: return@forEach
            if (parentI32 < 0) {
                parentI32 = candidateI32.idI32
                parentFaceI32 = candidateFaceI32
            } else {
                val currentParentI32 = componentsI32[parentI32]
                when {
                    hybridContainingFaceF64F32(
                        currentParentI32,
                        candidateI32,
                        verticesF64F32,
                        halfEdgesF64F32,
                        facesI32,
                    ) != null -> {
                        parentI32 = candidateI32.idI32
                        parentFaceI32 = candidateFaceI32
                    }
                    hybridContainingFaceF64F32(
                        candidateI32,
                        currentParentI32,
                        verticesF64F32,
                        halfEdgesF64F32,
                        facesI32,
                    ) != null -> Unit
                    else -> pathHybridArrangementInconsistentF64F32()
                }
            }
        }
        parentByComponentI32[componentI32.idI32] = parentI32
        parentFaceByComponentI32[componentI32.idI32] = parentFaceI32
    }
    val childrenI32 = List(componentsI32.size) { mutableListOf<Int>() }
    val pendingI32 = mutableListOf<Int>()
    parentByComponentI32.forEachIndexed { componentI32, parentI32 -> if (parentI32 < 0) pendingI32 += componentI32 else childrenI32[parentI32] += componentI32 }
    childrenI32.forEach { childrenForParentI32 ->
        sortHybridArrangementI32(childrenForParentI32, candidateWorkBudgetI32) { firstI32, secondI32 -> firstI32.compareTo(secondI32) }
    }
    var cursorI32 = 0
    while (cursorI32 < pendingI32.size) {
        val componentI32 = componentsI32[pendingI32[cursorI32++]]
        val parentI32 = parentByComponentI32[componentI32.idI32]
        val initialI32 = if (parentI32 < 0) {
            PathHybridWindingI32(0, 0)
        } else {
            val parentFaceI32 = parentFaceByComponentI32[componentI32.idI32]
            val parentFace = facesI32.getOrNull(parentFaceI32) ?: pathHybridArrangementInconsistentF64F32()
            PathHybridWindingI32(
                parentFace.firstWindingI32 ?: pathHybridArrangementInconsistentF64F32(),
                parentFace.secondWindingI32 ?: pathHybridArrangementInconsistentF64F32(),
            )
        }
        propagateHybridComponentWindingF64F32(
            componentI32,
            initialI32,
            halfEdgesF64F32,
            facesI32,
        )
        childrenI32[componentI32.idI32].forEach(pendingI32::add)
    }
}

private data class PathHybridWindingI32(val firstI32: Int, val secondI32: Int)

/**
 * Containment is nested, but the complete envelope is available from the already canonical
 * component/face lists.  Reserve it before constructing the parent forest so neither a JVM/JS
 * collection traversal nor a comparator invocation can influence the operation budget.
 */
private fun preflightHybridWindingF64F32(
    componentsI32: List<PathHybridComponentI32>,
    facesI32: List<PathMutableHybridFaceI32>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
) {
    val componentCountI64 = componentsI32.size.toLong()
    val componentFaceEdgeWorkI64 = componentsI32.sumOf { componentI32 ->
        componentI32.faceIndicesI32.sumOf { faceIndexI32 ->
            facesI32[faceIndexI32].boundaryHalfEdgeIndicesI32.size.toLong() + 1L
        }
    }
    val containmentI64 = checkedPathWorkMultiplyI64(
        checkedPathWorkMultiplyI64(componentCountI64, componentFaceEdgeWorkI64),
        3L,
    )
    val forestI64 = checkedPathWorkAddI64(
        checkedPathWorkMultiplyI64(componentCountI64, componentCountI64),
        checkedPathWorkMultiplyI64(componentCountI64, 6L),
    )
    preflightArrangementF64F32(
        checkedPathWorkAddI64(checkedPathWorkAddI64(containmentI64, componentFaceEdgeWorkI64), forestI64),
        candidateWorkBudgetI32,
    )
}

private fun propagateHybridComponentWindingF64F32(
    componentI32: PathHybridComponentI32,
    externalWindingI32: PathHybridWindingI32,
    halfEdgesF64F32: List<PathMutableHybridHalfEdgeF64F32>,
    facesI32: List<PathMutableHybridFaceI32>,
) {
    assignHybridFaceWindingF64F32(facesI32[componentI32.externalFaceIndexI32], externalWindingI32)
    val pendingI32 = mutableListOf(componentI32.externalFaceIndexI32)
    var cursorI32 = 0
    while (cursorI32 < pendingI32.size) {
        val faceI32 = facesI32[pendingI32[cursorI32++]]
        val firstWindingI32 = faceI32.firstWindingI32 ?: pathHybridArrangementInconsistentF64F32()
        val secondWindingI32 = faceI32.secondWindingI32 ?: pathHybridArrangementInconsistentF64F32()
        faceI32.boundaryHalfEdgeIndicesI32.forEach { halfEdgeIndexI32 ->
            val halfEdgeF64F32 = halfEdgesF64F32[halfEdgeIndexI32]
            val oppositeFaceI32 = halfEdgesF64F32[halfEdgeF64F32.twinIndexI32].leftFaceIndexI32
            if (oppositeFaceI32 !in componentI32.faceIndicesI32) pathHybridArrangementInconsistentF64F32()
            val expectedI32 = PathHybridWindingI32(
                (firstWindingI32.toLong() - halfEdgeF64F32.firstWindingDeltaI32.toLong()).toHybridI32(),
                (secondWindingI32.toLong() - halfEdgeF64F32.secondWindingDeltaI32.toLong()).toHybridI32(),
            )
            val oppositeI32 = facesI32[oppositeFaceI32]
            if (oppositeI32.firstWindingI32 == null && oppositeI32.secondWindingI32 == null) {
                assignHybridFaceWindingF64F32(oppositeI32, expectedI32)
                pendingI32 += oppositeFaceI32
            } else if (oppositeI32.firstWindingI32 != expectedI32.firstI32 || oppositeI32.secondWindingI32 != expectedI32.secondI32) {
                pathHybridArrangementInconsistentF64F32()
            }
        }
    }
}

private fun assignHybridFaceWindingF64F32(faceI32: PathMutableHybridFaceI32, windingI32: PathHybridWindingI32) {
    if (faceI32.firstWindingI32 == null && faceI32.secondWindingI32 == null) {
        faceI32.firstWindingI32 = windingI32.firstI32
        faceI32.secondWindingI32 = windingI32.secondI32
    } else if (faceI32.firstWindingI32 != windingI32.firstI32 || faceI32.secondWindingI32 != windingI32.secondI32) {
        pathHybridArrangementInconsistentF64F32()
    }
}

private fun hybridContainingFaceF64F32(
    componentI32: PathHybridComponentI32,
    witnessComponentI32: PathHybridComponentI32,
    verticesF64F32: List<PathHybridVertexF64F32>,
    halfEdgesF64F32: List<PathMutableHybridHalfEdgeF64F32>,
    facesI32: List<PathMutableHybridFaceI32>,
): Int? {
    val pointF64 = verticesF64F32[witnessComponentI32.witnessVertexIndexI32].representativePointF32.toPoint2F64()
    val matchesI32 = componentI32.faceIndicesI32.filter { faceIndexI32 ->
        faceIndexI32 != componentI32.externalFaceIndexI32 &&
            hybridFaceContainsPointF64F32(
                facesI32[faceIndexI32],
                pointF64,
                halfEdgesF64F32,
                verticesF64F32,
            )
    }
    if (matchesI32.size > 1) pathHybridArrangementInconsistentF64F32()
    return matchesI32.singleOrNull()
}

private fun hybridFaceContainsPointF64F32(
    faceI32: PathMutableHybridFaceI32,
    pointF64: Point2F64,
    halfEdgesF64F32: List<PathMutableHybridHalfEdgeF64F32>,
    verticesF64F32: List<PathHybridVertexF64F32>,
): Boolean {
    var windingI64 = 0L
    faceI32.boundaryHalfEdgeIndicesI32.forEach { halfEdgeIndexI32 ->
        val halfEdgeF64F32 = halfEdgesF64F32[halfEdgeIndexI32]
        val startF64 = verticesF64F32[halfEdgeF64F32.originVertexIndexI32].representativePointF32.toPoint2F64()
        val endF64 = verticesF64F32[halfEdgeF64F32.destinationVertexIndexI32].representativePointF32.toPoint2F64()
        if (PathPredicatesF64.onSegment(pointF64, startF64, endF64)) pathHybridArrangementInconsistentF64F32()
        val startAtOrBelow = startF64.y <= pointF64.y
        val endAbove = endF64.y > pointF64.y
        val endAtOrBelow = endF64.y <= pointF64.y
        if (startAtOrBelow && endAbove && OrientationPredicateF64.sign(startF64, endF64, pointF64) > 0) {
            windingI64 += 1L
        } else if (!startAtOrBelow && endAtOrBelow && OrientationPredicateF64.sign(startF64, endF64, pointF64) < 0) {
            windingI64 -= 1L
        }
    }
    return windingI64 != 0L
}

private fun canonicalHybridTraceF64F32(
    rawHalfEdgeIndicesI32: List<Int>,
    halfEdgesF64F32: List<PathHybridHalfEdgeF64F32>,
    verticesF64F32: List<PathHybridVertexF64F32>,
    traceSpanByHalfEdgeI32: Map<Int, PathArrangementTraceSpanF64F32>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): PathCanonicalTraceF64F32? {
    if (rawHalfEdgeIndicesI32.size < 3) return null
    preflightArrangementF64F32(
        checkedPathWorkAddI64(checkedPathWorkMultiplyI64(rawHalfEdgeIndicesI32.size.toLong(), 8L), 2L),
        candidateWorkBudgetI32,
    )
    val pointsF64 = rawHalfEdgeIndicesI32.map { halfEdgeIndexI32 ->
        verticesF64F32[halfEdgesF64F32[halfEdgeIndexI32].originVertexIndexI32].representativePointF32.toPoint2F64()
    }
    val areaF64 = signedDoubleAreaExpansionF64(pointsF64 + pointsF64.first())
    if (ExpansionF64.sign(areaF64) == 0) return null
    val firstIndexI32 = canonicalHybridTraceRotationIndexF64F32(
        rawHalfEdgeIndicesI32 = rawHalfEdgeIndicesI32,
        halfEdgesF64F32 = halfEdgesF64F32,
        verticesF64F32 = verticesF64F32,
        traceSpanByHalfEdgeI32 = traceSpanByHalfEdgeI32,
    )
    val rotatedI32 = rawHalfEdgeIndicesI32.drop(firstIndexI32) + rawHalfEdgeIndicesI32.take(firstIndexI32)
    val traceF64F32 = PathBoundaryTraceF64F32(rotatedI32.map { halfEdgeIndexI32 ->
        val halfEdgeF64F32 = halfEdgesF64F32[halfEdgeIndexI32]
        val traceSpanF64F32 = traceSpanByHalfEdgeI32.getValue(halfEdgeIndexI32)
        PathBoundaryHalfEdgeTraceF64F32(
            sourceSpanF64 = traceSpanF64F32.canonicalCarrierSectionF64F32.sourceSpanF64,
            sourceSectionF64 = traceSpanF64F32.canonicalCarrierSectionF64F32.sourceSectionF64,
            sectionIndexI32 = traceSpanF64F32.canonicalCarrierSectionF64F32.sectionIndexI32,
            carrierSectionsF64F32 = traceSpanF64F32.carrierSectionsF64F32,
            originVertexF64F32 = verticesF64F32[halfEdgeF64F32.originVertexIndexI32],
            destinationVertexF64F32 = verticesF64F32[halfEdgeF64F32.destinationVertexIndexI32],
            forward = traceSpanF64F32.canonicalForward,
        )
    })
    return PathCanonicalTraceF64F32(traceF64F32, rotatedI32, areaF64)
}

/**
 * Booth's linear minimal-rotation algorithm over the complete selected half-edge sequence.
 * The element comparison deliberately uses only stable F32/F64 geometry and the already
 * canonical carrier representative; source labels and mutable DCEL indices never break a tie.
 */
private fun canonicalHybridTraceRotationIndexF64F32(
    rawHalfEdgeIndicesI32: List<Int>,
    halfEdgesF64F32: List<PathHybridHalfEdgeF64F32>,
    verticesF64F32: List<PathHybridVertexF64F32>,
    traceSpanByHalfEdgeI32: Map<Int, PathArrangementTraceSpanF64F32>,
): Int {
    val sizeI32 = rawHalfEdgeIndicesI32.size
    if (sizeI32 < 2) return 0
    var firstI32 = 0
    var secondI32 = 1
    var offsetI32 = 0
    while (firstI32 < sizeI32 && secondI32 < sizeI32 && offsetI32 < sizeI32) {
        val comparisonI32 = compareHybridTraceElementsF64F32(
            firstHalfEdgeIndexI32 = rawHalfEdgeIndicesI32[(firstI32 + offsetI32) % sizeI32],
            secondHalfEdgeIndexI32 = rawHalfEdgeIndicesI32[(secondI32 + offsetI32) % sizeI32],
            halfEdgesF64F32 = halfEdgesF64F32,
            verticesF64F32 = verticesF64F32,
            traceSpanByHalfEdgeI32 = traceSpanByHalfEdgeI32,
        )
        when {
            comparisonI32 == 0 -> offsetI32 += 1
            comparisonI32 > 0 -> {
                firstI32 += offsetI32 + 1
                if (firstI32 == secondI32) firstI32 += 1
                offsetI32 = 0
            }
            else -> {
                secondI32 += offsetI32 + 1
                if (firstI32 == secondI32) secondI32 += 1
                offsetI32 = 0
            }
        }
    }
    return minOf(firstI32, secondI32).coerceAtMost(sizeI32 - 1)
}

private fun compareHybridTraceElementsF64F32(
    firstHalfEdgeIndexI32: Int,
    secondHalfEdgeIndexI32: Int,
    halfEdgesF64F32: List<PathHybridHalfEdgeF64F32>,
    verticesF64F32: List<PathHybridVertexF64F32>,
    traceSpanByHalfEdgeI32: Map<Int, PathArrangementTraceSpanF64F32>,
): Int {
    compareHybridTraceStartF64F32(
        firstHalfEdgeIndexI32,
        secondHalfEdgeIndexI32,
        halfEdgesF64F32,
        verticesF64F32,
    ).takeIf { it != 0 }?.let { return it }
    val firstHalfEdgeF64F32 = halfEdgesF64F32[firstHalfEdgeIndexI32]
    val secondHalfEdgeF64F32 = halfEdgesF64F32[secondHalfEdgeIndexI32]
    compareArrangementVerticesF64F32(
        verticesF64F32[firstHalfEdgeF64F32.destinationVertexIndexI32],
        verticesF64F32[secondHalfEdgeF64F32.destinationVertexIndexI32],
    ).takeIf { it != 0 }?.let { return it }
    val firstTraceSpanF64F32 = traceSpanByHalfEdgeI32.getValue(firstHalfEdgeIndexI32)
    val secondTraceSpanF64F32 = traceSpanByHalfEdgeI32.getValue(secondHalfEdgeIndexI32)
    compareTraceCarrierSectionsF64F32(
        firstTraceSpanF64F32.canonicalCarrierSectionF64F32,
        secondTraceSpanF64F32.canonicalCarrierSectionF64F32,
    ).takeIf { it != 0 }?.let { return it }
    return firstTraceSpanF64F32.carrierSectionsF64F32.size.compareTo(
        secondTraceSpanF64F32.carrierSectionsF64F32.size,
    )
}

private fun compareHybridTraceStartF64F32(
    firstHalfEdgeIndexI32: Int,
    secondHalfEdgeIndexI32: Int,
    halfEdgesF64F32: List<PathHybridHalfEdgeF64F32>,
    verticesF64F32: List<PathHybridVertexF64F32>,
): Int = compareArrangementVerticesF64F32(
    verticesF64F32[halfEdgesF64F32[firstHalfEdgeIndexI32].originVertexIndexI32],
    verticesF64F32[halfEdgesF64F32[secondHalfEdgeIndexI32].originVertexIndexI32],
)

private fun compareHybridAbsoluteAreasF64(firstF64: DoubleArray, secondF64: DoubleArray): Int {
    val firstSignI32 = ExpansionF64.sign(firstF64)
    val secondSignI32 = ExpansionF64.sign(secondF64)
    if (firstSignI32 == 0 || secondSignI32 == 0) pathHybridArrangementInconsistentF64F32()
    val firstAbsoluteF64 = if (firstSignI32 > 0) firstF64 else DoubleArray(firstF64.size) { indexI32 -> -firstF64[indexI32] }
    val secondAbsoluteF64 = if (secondSignI32 > 0) secondF64 else DoubleArray(secondF64.size) { indexI32 -> -secondF64[indexI32] }
    return ExpansionF64.sign(ExpansionF64.expansionDiff(secondAbsoluteF64, firstAbsoluteF64))
}

private fun compareHybridOutgoingRaysF64F32(
    firstI32: Int,
    secondI32: Int,
    originVertexIndexI32: Int,
    halfEdgesF64F32: List<PathHybridHalfEdgeF64F32>,
    verticesF64F32: List<PathHybridVertexF64F32>,
): Int {
    if (firstI32 == secondI32) return 0
    val firstF64F32 = halfEdgesF64F32[firstI32]
    val secondF64F32 = halfEdgesF64F32[secondI32]
    val originF64 = verticesF64F32[originVertexIndexI32].representativePointF32.toPoint2F64()
    val firstDestinationF64 = verticesF64F32[halfEdgesF64F32[firstF64F32.twinIndexI32].originVertexIndexI32]
        .representativePointF32.toPoint2F64()
    val secondDestinationF64 = verticesF64F32[halfEdgesF64F32[secondF64F32.twinIndexI32].originVertexIndexI32]
        .representativePointF32.toPoint2F64()
    return compareHybridDirectionsF64F32(firstDestinationF64 - originF64, secondDestinationF64 - originF64)
}

private fun compareHybridOutputRaysF64F32(
    firstI32: Int,
    secondI32: Int,
    traceSpanByHalfEdgeI32: Map<Int, PathArrangementTraceSpanF64F32>,
): Int = compareHybridDirectionsF64F32(
    hybridSourceDirectionF64F32(
        traceSpanByHalfEdgeI32.getValue(firstI32).canonicalCarrierSectionF64F32,
        traceSpanByHalfEdgeI32.getValue(firstI32).canonicalForward,
    ),
    hybridSourceDirectionF64F32(
        traceSpanByHalfEdgeI32.getValue(secondI32).canonicalCarrierSectionF64F32,
        traceSpanByHalfEdgeI32.getValue(secondI32).canonicalForward,
    ),
)

private fun sameHybridOutputRayF64F32(
    firstI32: Int,
    secondI32: Int,
    traceSpanByHalfEdgeI32: Map<Int, PathArrangementTraceSpanF64F32>,
): Boolean = sameHybridDirectionsF64F32(
    hybridSourceDirectionF64F32(
        traceSpanByHalfEdgeI32.getValue(firstI32).canonicalCarrierSectionF64F32,
        traceSpanByHalfEdgeI32.getValue(firstI32).canonicalForward,
    ),
    hybridSourceDirectionF64F32(
        traceSpanByHalfEdgeI32.getValue(secondI32).canonicalCarrierSectionF64F32,
        traceSpanByHalfEdgeI32.getValue(secondI32).canonicalForward,
    ),
)

private fun compareHybridOutgoingRaysF64F32(
    firstI32: Int,
    secondI32: Int,
    originVertexIndexI32: Int,
    halfEdgesF64F32: List<PathMutableHybridHalfEdgeF64F32>,
): Int = compareHybridDirectionsF64F32(halfEdgesF64F32[firstI32].sourceDirectionF64, halfEdgesF64F32[secondI32].sourceDirectionF64)

private fun compareHybridDirectionsF64F32(firstF64: Vector2F64, secondF64: Vector2F64): Int {
    val firstQuadrantI32 = hybridQuadrantF64F32(firstF64)
    val secondQuadrantI32 = hybridQuadrantF64F32(secondF64)
    if (firstQuadrantI32 != secondQuadrantI32) return firstQuadrantI32.compareTo(secondQuadrantI32)
    val orientationI32 = OrientationPredicateF64.sign(
        Point2F64.Origin,
        Point2F64(firstF64.x, firstF64.y),
        Point2F64(secondF64.x, secondF64.y),
    )
    return -orientationI32
}

private fun sameHybridOutgoingRayF64F32(
    firstF64F32: PathMutableHybridHalfEdgeF64F32,
    secondF64F32: PathMutableHybridHalfEdgeF64F32,
): Boolean = sameHybridDirectionsF64F32(firstF64F32.sourceDirectionF64, secondF64F32.sourceDirectionF64)

private fun sameHybridDirectionsF64F32(firstF64: Vector2F64, secondF64: Vector2F64): Boolean =
    hybridQuadrantF64F32(firstF64) == hybridQuadrantF64F32(secondF64) &&
    OrientationPredicateF64.sign(
        Point2F64.Origin,
        Point2F64(firstF64.x, firstF64.y),
        Point2F64(secondF64.x, secondF64.y),
    ) == 0

private fun hybridQuadrantF64F32(directionF64: Vector2F64): Int {
    if (directionF64.x == 0.0 && directionF64.y == 0.0) pathHybridArrangementInconsistentF64F32()
    return when {
        directionF64.x >= 0.0 && directionF64.y >= 0.0 -> 0
        directionF64.x < 0.0 && directionF64.y >= 0.0 -> 1
        directionF64.x < 0.0 && directionF64.y < 0.0 -> 2
        else -> 3
    }
}

private fun compareArrangementVerticesF64F32(firstF64F32: PathHybridVertexF64F32, secondF64F32: PathHybridVertexF64F32): Int {
    compareArrangementPointsF32F64(firstF64F32.representativePointF32, secondF64F32.representativePointF32).takeIf { it != 0 }?.let { return it }
    return compareArrangementPointsF64F32(firstF64F32.sourcePointF64, secondF64F32.sourcePointF64)
}

private fun compareArrangementPointsF64F32(firstF64: Point2F64, secondF64: Point2F64): Int = when {
    firstF64.x < secondF64.x -> -1
    firstF64.x > secondF64.x -> 1
    firstF64.y < secondF64.y -> -1
    firstF64.y > secondF64.y -> 1
    else -> 0
}

private fun compareArrangementPointsF32F64(firstF32: Point2F32, secondF32: Point2F32): Int = when {
    firstF32.x < secondF32.x -> -1
    firstF32.x > secondF32.x -> 1
    firstF32.y < secondF32.y -> -1
    firstF32.y > secondF32.y -> 1
    else -> 0
}

private fun sameArrangementHybridPointF32(firstF32: Point2F32, secondF32: Point2F32): Boolean =
    firstF32.x == secondF32.x && firstF32.y == secondF32.y

private fun Int.isFilledHybridF64F32(fillRule: FillRule): Boolean = when (fillRule) {
    FillRule.WINDING, FillRule.INVERSE_WINDING -> this != 0
    FillRule.EVEN_ODD, FillRule.INVERSE_EVEN_ODD -> this % 2 != 0
}

private fun PathBooleanOp.selectsHybridF64F32(first: Boolean, second: Boolean): Boolean = when (this) {
    PathBooleanOp.DIFFERENCE -> first && !second
    PathBooleanOp.INTERSECT -> first && second
    PathBooleanOp.UNION -> first || second
    PathBooleanOp.XOR -> first != second
    PathBooleanOp.REVERSE_DIFFERENCE -> second && !first
}

private fun Long.toHybridI32(): Int {
    if (this !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) pathHybridArrangementInconsistentF64F32()
    return toInt()
}

private fun Int.negatedHybridI32(): Int {
    if (this == Int.MIN_VALUE) pathHybridArrangementInconsistentF64F32()
    return -this
}

private fun <T> sortedArrangementF64F32(
    values: List<T>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
    compare: (T, T) -> Int,
): List<T> {
    val sizeI32 = values.size
    preflightArrangementF64F32(
        checkedPathWorkAddI64(arrangementSortCostI64F32(sizeI32), sizeI32.toLong()),
        candidateWorkBudgetI32,
    )
    return values.sortedWith(Comparator(compare))
}

private fun sortHybridArrangementI32(
    valuesI32: MutableList<Int>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
    compare: (Int, Int) -> Int,
) {
    preflightArrangementF64F32(
        checkedPathWorkAddI64(arrangementSortCostI64F32(valuesI32.size), valuesI32.size.toLong()),
        candidateWorkBudgetI32,
    )
    valuesI32.sortWith(Comparator(compare))
}

private fun arrangementSortCostI64F32(sizeI32: Int): Long {
    if (sizeI32 < 2) return 0L
    var widthI64 = 1L
    var levelsI64 = 0L
    while (widthI64 < sizeI32.toLong()) {
        widthI64 = widthI64 shl 1
        levelsI64 += 1L
    }
    return checkedPathWorkMultiplyI64(sizeI32.toLong(), levelsI64)
}

private fun preflightArrangementF64F32(unitsI64: Long, candidateWorkBudgetI32: PathCandidateWorkBudgetI32) {
    candidateWorkBudgetI32.consumePreflightI64(unitsI64)
}

private fun pathHybridArrangementInconsistentF64F32(): Nothing =
    throw IllegalStateException("path-arrangement-inconsistent")
