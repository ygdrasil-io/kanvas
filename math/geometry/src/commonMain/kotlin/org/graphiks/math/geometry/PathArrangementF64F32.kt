package org.graphiks.math.geometry

import org.graphiks.math.vector.Vector2F64
import kotlin.math.pow

/** The only permitted disposition for a selected boundary that contains a collapsed incidence. */
internal enum class PathBoundaryDisposition { KEEP, DROP, REJECT }

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
    private val collapsedIncidencesF64F32: List<PathCollapsedIncidenceF64F32>,
    /**
     * Exact collapsed-contour sector data captured before provisional aliases feed the DCEL.
     * It is intentionally retained through face selection: a missing half-edge is never treated
     * as evidence that its source winding was absent.
     */
    private val collapsedSectorLedgerF64F32: PathCollapsedSectorLedgerF64F32,
    private val traceSpanByHalfEdgeI32: Map<Int, PathArrangementTraceSpanF64F32>,
    private val candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
) {
    fun boundary(
        firstFillRule: FillRule,
        secondFillRule: FillRule,
        operation: PathBooleanOp,
    ): List<PathBoundaryTraceF64F32> = extractBoundaryF64F32(
        selectsWindingF64F32 = { firstWindingI32, secondWindingI32 ->
            operation.selectsHybridF64F32(
                firstWindingI32.isFilledHybridF64F32(firstFillRule),
                secondWindingI32.isFilledHybridF64F32(secondFillRule),
            )
        },
    )

    fun unaryBoundary(fillRule: FillRule): List<PathBoundaryTraceF64F32> = extractBoundaryF64F32(
        selectsWindingF64F32 = { firstWindingI32, _ -> firstWindingI32.isFilledHybridF64F32(fillRule) },
    )

    private fun extractBoundaryF64F32(
        selectsWindingF64F32: (Int, Int) -> Boolean,
    ): List<PathBoundaryTraceF64F32> {
        if (halfEdgesF64F32.isEmpty()) {
            val collapsedOnlyDispositionF64F32 = classifyCollapsedSectorsWithoutFacesF64F32(
                collapsedSectorLedgerF64F32 = collapsedSectorLedgerF64F32,
                selectsWindingF64F32 = selectsWindingF64F32,
                candidateWorkBudgetI32 = candidateWorkBudgetI32,
            )
            when (collapsedOnlyDispositionF64F32) {
                PathBoundaryDisposition.KEEP,
                PathBoundaryDisposition.DROP -> return emptyList()
                PathBoundaryDisposition.REJECT -> throw IllegalStateException("path-f32-projection-collapse")
            }
        }
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
        val faceSelected = BooleanArray(facesI32.size) { faceIndexI32 ->
            val faceI32 = facesI32[faceIndexI32]
            selectsWindingF64F32(faceI32.firstWindingI32, faceI32.secondWindingI32)
        }
        val selected = BooleanArray(halfEdgesF64F32.size)
        halfEdgesF64F32.forEach { halfEdgeF64F32 ->
            if (halfEdgeF64F32.idI32 > halfEdgeF64F32.twinIndexI32) return@forEach
            val leftSelected = faceSelected[halfEdgeF64F32.leftFaceIndexI32]
            val rightSelected = faceSelected[halfEdgesF64F32[halfEdgeF64F32.twinIndexI32].leftFaceIndexI32]
            if (leftSelected != rightSelected) {
                selected[if (leftSelected) halfEdgeF64F32.idI32 else halfEdgeF64F32.twinIndexI32] = true
            }
        }
        // Staged connectivity aliases were needed to build these faces, but every collapsed
        // incidence remains here with its exact source adjacency.  Therefore selectedness is
        // proven from a face winding transition, never inferred from a missing half-edge.
        if (
            classifyCollapsedSectorsAfterFaceSelectionF64F32(
                collapsedSectorLedgerF64F32 = collapsedSectorLedgerF64F32,
                facesI32 = facesI32,
                verticesF64F32 = verticesF64F32,
                halfEdgesF64F32 = halfEdgesF64F32,
                selectsWindingF64F32 = selectsWindingF64F32,
                candidateWorkBudgetI32 = candidateWorkBudgetI32,
            ) == PathBoundaryDisposition.REJECT
        ) {
            throw IllegalStateException("path-f32-projection-collapse")
        }
        val collapsedDispositionF64F32 = classifySelectedCollapsedContinuationsF64F32(
            selectedI32 = selected,
            halfEdgesF64F32 = halfEdgesF64F32,
            traceSpanByHalfEdgeI32 = traceSpanByHalfEdgeI32,
            sourceSpansByIdI64 = sourceSpansByIdI64,
            collapsedIncidencesF64F32 = collapsedIncidencesF64F32,
            collapsedSectorLedgerF64F32 = collapsedSectorLedgerF64F32,
            candidateWorkBudgetI32 = candidateWorkBudgetI32,
        )
        when (collapsedDispositionF64F32) {
            PathBoundaryDisposition.KEEP,
            // A whole selected contour can be dropped only because it has no remaining F32
            // carrier to emit.  Sibling selected traces continue through the normal writer path.
            PathBoundaryDisposition.DROP -> Unit
            PathBoundaryDisposition.REJECT -> throw IllegalStateException("path-f32-projection-collapse")
        }
        if (selected.none { it }) return emptyList()

        val outgoing = List(verticesF64F32.size) { mutableListOf<Int>() }
        halfEdgesF64F32.forEach { halfEdgeF64F32 -> outgoing[halfEdgeF64F32.originVertexIndexI32] += halfEdgeF64F32.idI32 }
        outgoing.forEach { halfEdgeIdsI32 ->
            sortHybridArrangementI32(halfEdgeIdsI32, candidateWorkBudgetI32) { firstI32, secondI32 ->
                compareHybridOutputRaysF64F32(firstI32, secondI32, traceSpanByHalfEdgeI32)
            }
            rejectAdjacentHybridOutputRaysF64F32(
                halfEdgeIdsI32 = halfEdgeIdsI32,
                traceSpanByHalfEdgeI32 = traceSpanByHalfEdgeI32,
                candidateWorkBudgetI32 = candidateWorkBudgetI32,
            )
        }

        val outgoingPositionI32 = IntArray(halfEdgesF64F32.size) { -1 }
        outgoing.forEach { halfEdgeIndicesI32 ->
            halfEdgeIndicesI32.forEachIndexed { positionI32, halfEdgeIndexI32 ->
                outgoingPositionI32[halfEdgeIndexI32] = positionI32
            }
        }

        val nextSelectedI32 = IntArray(halfEdgesF64F32.size) { -1 }
        // This pass derives the exact selected-ray scan bound.  Debit it before looking at the
        // selected bitmap so a data-dependent traversal cannot escape the common ledger.
        preflightArrangementF64F32(halfEdgesF64F32.size.toLong(), candidateWorkBudgetI32)
        var selectedTraversalWorkI64 = 0L
        halfEdgesF64F32.indices.forEach { halfEdgeIndexI32 ->
            if (selected[halfEdgeIndexI32]) {
                selectedTraversalWorkI64 = checkedPathWorkAddI64(
                    selectedTraversalWorkI64,
                    outgoing[halfEdgesF64F32[halfEdgesF64F32[halfEdgeIndexI32].twinIndexI32].originVertexIndexI32]
                        .size.toLong(),
                )
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
            when (val traceDispositionF64F32 = canonicalHybridTraceF64F32(
                rawHalfEdgeIndicesI32 = traceIndicesI32,
                halfEdgesF64F32 = halfEdgesF64F32,
                verticesF64F32 = verticesF64F32,
                traceSpanByHalfEdgeI32 = traceSpanByHalfEdgeI32,
                sourceSpansByIdI64 = sourceSpansByIdI64,
                collapsedIncidencesF64F32 = collapsedIncidencesF64F32,
                candidateWorkBudgetI32 = candidateWorkBudgetI32,
            )) {
                is PathCanonicalTraceDispositionF64F32.Keep -> tracesF64F32 += traceDispositionF64F32.traceF64F32
                PathCanonicalTraceDispositionF64F32.Drop -> Unit
            }
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
            // Capture collapsed source-contour sectors before temporary endpoint aliases are
            // applied.  The ledger is immutable arrangement input: it preserves the exact
            // contour winding that the DCEL cannot carry once an F32 carrier has zero extent.
            val collapsedSectorLedgerF64F32 = buildCollapsedSectorLedgerF64F32(
                sourceSpansByIdI64 = sourceSpansByIdI64,
                collapsedIncidencesF64F32 = topologyF64F32.collapsedIncidencesF64F32,
                carrierSectionsF64F32 = topologyF64F32.carrierSectionsF64F32,
                candidateWorkBudgetI32 = candidateWorkBudgetI32,
            )
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
            preflightArrangementF64F32(topologyF64F32.aliasGroupsF32.size.toLong(), candidateWorkBudgetI32)
            var aliasIdentityCountI64 = 0L
            topologyF64F32.aliasGroupsF32.forEach { aliasGroupF32 ->
                aliasIdentityCountI64 = checkedPathWorkAddI64(
                    aliasIdentityCountI64,
                    aliasGroupF32.vertexIdentitiesF64.size.toLong(),
                )
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
            // A collapsed carrier is retained through face winding.  Its endpoints are joined
            // only as a provisional exact source-incidence relation so the surrounding carriers
            // can be classified in one arrangement; this is never a coordinate-derived alias.
            // `extractBoundaryF64F32` makes the final KEEP/DROP/REJECT decision after face
            // selection and before any empty result or trace reaches the writer.
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
                limitsI32.maxVertices,
                candidateWorkBudgetI32,
            )

            val pointWitnessByCarrierPairF64F32 = pointWitnessCarrierPairsF64F32(
                topologyF64F32.projectedCoincidencesF32,
                topologyF64F32.carrierSectionsF64F32,
                candidateWorkBudgetI32,
            )

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
                    topologyF64F32.collapsedIncidencesF64F32,
                    collapsedSectorLedgerF64F32,
                    emptyMap(),
                    candidateWorkBudgetI32,
                )
            }

            preflightArrangementF64F32(pathEdgesF64F32.size.toLong(), candidateWorkBudgetI32)
            var contributingCarrierCountI64 = 0L
            pathEdgesF64F32.forEach { (_, contributionF64F32) ->
                contributingCarrierCountI64 = checkedPathWorkAddI64(
                    contributingCarrierCountI64,
                    contributionF64F32.carrierSectionsF64F32.size.toLong(),
                )
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
                    pointWitnessByCarrierPairF64F32 = pointWitnessByCarrierPairF64F32,
                    candidateWorkBudgetI32 = candidateWorkBudgetI32,
                )
                val traceCarrierSectionF64F32 = traceCarrierGroupF64F32.canonicalCarrierSectionF64F32
                val forwardByCarrier = traceCarrierGroupF64F32.canonicalForward
                val forwardEmbeddingDirectionF64 =
                    canonicalVerticesF64F32.verticesF64F32[keyI32.endVertexIndexI32].representativePointF32.toPoint2F64() -
                        canonicalVerticesF64F32.verticesF64F32[keyI32.startVertexIndexI32].representativePointF32.toPoint2F64()
                if (forwardEmbeddingDirectionF64.x == 0.0 && forwardEmbeddingDirectionF64.y == 0.0) {
                    throw IllegalStateException("path-f32-projection-collapse")
                }
                val forwardTraceCarrierGroupF64F32 = traceCarrierGroupF64F32.copy(
                    embeddingDirectionF64 = forwardEmbeddingDirectionF64,
                )
                mutableHalfEdgesF64F32 += PathMutableHybridHalfEdgeF64F32(
                    idI32 = forwardIdI32,
                    originVertexIndexI32 = keyI32.startVertexIndexI32,
                    destinationVertexIndexI32 = keyI32.endVertexIndexI32,
                    twinIndexI32 = reverseIdI32,
                    sourceSpanIdsI64 = contributionF64F32.carrierSectionsF64F32.map { carrierSectionF64F32 ->
                        carrierSectionF64F32.sourceSpanF64.sourceSpanIdI64
                    },
                    carrierSectionsF64F32 = forwardTraceCarrierGroupF64F32.carrierSectionsF64F32,
                    firstWindingDeltaI32 = contributionF64F32.firstWindingDeltaI64.toHybridI32(),
                    secondWindingDeltaI32 = contributionF64F32.secondWindingDeltaI64.toHybridI32(),
                    sourceDirectionsF64 = forwardTraceCarrierGroupF64F32.sourceDirectionsF64,
                    pointWitnessF64 = forwardTraceCarrierGroupF64F32.pointWitnessF64,
                    embeddingDirectionF64 = forwardEmbeddingDirectionF64,
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
                    sourceDirectionsF64 = traceCarrierGroupF64F32.sourceDirectionsF64.map { directionF64 -> -directionF64 },
                    pointWitnessF64 = traceCarrierGroupF64F32.pointWitnessF64,
                    embeddingDirectionF64 = -forwardEmbeddingDirectionF64,
                )
                traceSpansByHalfEdgeI32[forwardIdI32] = forwardTraceCarrierGroupF64F32
                traceSpansByHalfEdgeI32[reverseIdI32] = PathArrangementTraceSpanF64F32(
                    canonicalCarrierSectionF64F32 = traceCarrierSectionF64F32,
                    canonicalForward = !forwardByCarrier,
                    carrierSectionsF64F32 = traceCarrierGroupF64F32.carrierSectionsF64F32,
                    forwardByCarrierKeyF64F32 = traceCarrierGroupF64F32.forwardByCarrierKeyF64F32.mapValues { (_, forward) -> !forward },
                    sourceDirectionsF64 = traceCarrierGroupF64F32.sourceDirectionsF64.map { directionF64 -> -directionF64 },
                    pointWitnessF64 = traceCarrierGroupF64F32.pointWitnessF64,
                    embeddingDirectionF64 = -forwardEmbeddingDirectionF64,
                )
            }
            val outgoingI32 = List(canonicalVerticesF64F32.verticesF64F32.size) { mutableListOf<Int>() }
            mutableHalfEdgesF64F32.forEach { halfEdgeF64F32 -> outgoingI32[halfEdgeF64F32.originVertexIndexI32] += halfEdgeF64F32.idI32 }
            outgoingI32.forEachIndexed { vertexIndexI32, halfEdgeIndicesI32 ->
                val orderedSourceEventsF64F32 = sweepHybridSourceDirectionsAtVertexF64F32(
                    halfEdgeIndicesI32 = halfEdgeIndicesI32,
                    halfEdgesF64F32 = mutableHalfEdgesF64F32,
                    candidateWorkBudgetI32 = candidateWorkBudgetI32,
                )
                sortHybridArrangementI32(halfEdgeIndicesI32, candidateWorkBudgetI32) { firstI32, secondI32 ->
                    compareHybridOutgoingRaysF64F32(
                        firstI32,
                        secondI32,
                        vertexIndexI32,
                        mutableHalfEdgesF64F32,
                    )
                }
                validateHybridSourceDirectionBundlesAtVertexF64F32(
                    halfEdgeIndicesI32 = halfEdgeIndicesI32,
                    orderedSourceEventsF64F32 = orderedSourceEventsF64F32,
                    candidateWorkBudgetI32 = candidateWorkBudgetI32,
                )
                rejectAdjacentHybridOutgoingRaysF64F32(
                    halfEdgeIndicesI32 = halfEdgeIndicesI32,
                    halfEdgesF64F32 = mutableHalfEdgesF64F32,
                    candidateWorkBudgetI32 = candidateWorkBudgetI32,
                )
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
                topologyF64F32.collapsedIncidencesF64F32,
                collapsedSectorLedgerF64F32,
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
    /** Every exact source ray represented by this one F32 rail. */
    val sourceDirectionsF64: List<Vector2F64>,
    /** Non-null only when a Point witness resolves distinct source rays on this rail. */
    val pointWitnessF64: PathContactWitnessF64.PointF64?,
    /** Lifted direction of the selected F32 embedding; assigned when the DCEL edge is built. */
    val embeddingDirectionF64: Vector2F64,
)

private data class PathHybridCarrierKeyF64F32(
    val sourceSpanIdI64: Long,
    val sectionIndexI32: Int,
)

/** Exact relation lookup only; it never participates in a geometric tie-break. */
private data class PathHybridCarrierPairKeyF64F32(
    val firstF64F32: PathHybridCarrierKeyF64F32,
    val secondF64F32: PathHybridCarrierKeyF64F32,
)

/** Materialized section provenance used to resolve a claim after its original carrier was cut. */
private data class PathMaterializedClaimCarrierKeyF64F32(
    val sourceSpanIdI64: Long,
    val inputEdgeIdI32: Int,
    val startIdentityF64: PathVertexIdentityF64,
    val endIdentityF64: PathVertexIdentityF64,
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
    val sourceDirectionsF64: List<Vector2F64>,
    val pointWitnessF64: PathContactWitnessF64.PointF64?,
    val embeddingDirectionF64: Vector2F64,
    var nextIndexI32: Int = -1,
    var leftFaceIndexI32: Int = -1,
)

/** One exact source ray, tagged only by its carrier bundle before F32 embedding order is used. */
private data class PathHybridSourceAngularEventF64F32(
    val bundleHalfEdgeIndexI32: Int,
    val directionF64: Vector2F64,
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
    maximumCanonicalVerticesI32: Int,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): PathHybridCanonicalVerticesF64F32 {
    // The public vertex limit applies to the alias-collapsed DCEL vertices, never the source
    // seeds.  Count roots with only fixed-size transient storage first; do not allocate canonical
    // groups, representatives, or their index until the final count has been accepted.
    preflightArrangementF64F32(
        checkedPathWorkMultiplyI64(sourceVerticesF64F32.size.toLong(), 2L),
        candidateWorkBudgetI32,
    )
    val rootsSeenI32 = BooleanArray(sourceVerticesF64F32.size)
    var canonicalVertexCountI64 = 0L
    sourceVerticesF64F32.indices.forEach { sourceIndexI32 ->
        val rootI32 = aliasesI32.find(sourceIndexI32)
        if (!rootsSeenI32[rootI32]) {
            rootsSeenI32[rootI32] = true
            canonicalVertexCountI64 = checkedPathWorkAddI64(canonicalVertexCountI64, 1L)
        }
    }
    if (canonicalVertexCountI64 > maximumCanonicalVerticesI32.toLong()) {
        throw IllegalStateException("path-vertex-limit")
    }
    preflightArrangementF64F32(
        checkedPathWorkMultiplyI64(sourceVerticesF64F32.size.toLong(), 3L),
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
    pointWitnessByCarrierPairF64F32: Map<PathHybridCarrierPairKeyF64F32, PathContactWitnessF64.PointF64>,
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
    preflightArrangementF64F32(
        checkedPathWorkMultiplyI64(orderedCarrierSectionsF64F32.size.toLong(), 6L),
        candidateWorkBudgetI32,
    )
    val sourceDirectionsF64 = orderedCarrierSectionsF64F32.map { carrierSectionF64F32 ->
        val carrierKeyF64F32 = PathHybridCarrierKeyF64F32(
            carrierSectionF64F32.sourceSpanF64.sourceSpanIdI64,
            carrierSectionF64F32.sectionIndexI32,
        )
        val forward = forwardByCarrierKeyF64F32[carrierKeyF64F32]
            ?: pathHybridArrangementInconsistentF64F32()
        hybridSourceDirectionF64F32(carrierSectionF64F32, forward)
    }
    val canonicalDirectionF64 = sourceDirectionsF64.first()
    var requiresPointWitnessF64 = false
    var sourceDirectionIndexI32 = 1
    while (sourceDirectionIndexI32 < sourceDirectionsF64.size) {
        if (!sameHybridDirectionsF64F32(canonicalDirectionF64, sourceDirectionsF64[sourceDirectionIndexI32])) {
            requiresPointWitnessF64 = true
            break
        }
        sourceDirectionIndexI32 += 1
    }
    val pointWitnessF64 = if (requiresPointWitnessF64) {
        pointWitnessResolvingCarrierGroupF64F32(
            orderedCarrierSectionsF64F32 = orderedCarrierSectionsF64F32,
            pointWitnessByCarrierPairF64F32 = pointWitnessByCarrierPairF64F32,
        ) ?: throw IllegalStateException("path-f32-projection-collapse")
    } else {
        null
    }
    return PathArrangementTraceSpanF64F32(
        canonicalCarrierSectionF64F32 = canonicalCarrierSectionF64F32,
        canonicalForward = canonicalForward,
        carrierSectionsF64F32 = orderedCarrierSectionsF64F32,
        forwardByCarrierKeyF64F32 = forwardByCarrierKeyF64F32,
        sourceDirectionsF64 = sourceDirectionsF64,
        pointWitnessF64 = pointWitnessF64,
        embeddingDirectionF64 = Vector2F64(0.0, 0.0),
    )
}

private fun pointWitnessCarrierPairsF64F32(
    projectedCoincidencesF32: List<PathProjectedCoincidenceF32>,
    carrierSectionsF64F32: List<PathHybridCarrierSectionF64F32>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): Map<PathHybridCarrierPairKeyF64F32, PathContactWitnessF64.PointF64> {
    preflightArrangementF64F32(
        checkedPathWorkAddI64(
            checkedPathWorkMultiplyI64(projectedCoincidencesF32.size.toLong(), 10L),
            checkedPathWorkMultiplyI64(carrierSectionsF64F32.size.toLong(), 6L),
        ),
        candidateWorkBudgetI32,
    )
    val carrierKeysBySourceF64F32 = mutableMapOf<
        PathMaterializedClaimSourceKeyF64F32,
        MutableList<PathMaterializedClaimCarrierSectionF64F32>,
        >()
    carrierSectionsF64F32.forEach { carrierSectionF64F32 ->
        val sourceSectionF64 = carrierSectionF64F32.sourceSectionF64
        val carrierKeyF64F32 = PathHybridCarrierKeyF64F32(
            carrierSectionF64F32.sourceSpanF64.sourceSpanIdI64,
            carrierSectionF64F32.sectionIndexI32,
        )
        val sourceKeyF64F32 = PathMaterializedClaimSourceKeyF64F32(
            sourceSpanIdI64 = carrierSectionF64F32.sourceSpanF64.sourceSpanIdI64,
            inputEdgeIdI32 = sourceSectionF64.inputEdgeIdI32,
        )
        carrierKeysBySourceF64F32.getOrPut(sourceKeyF64F32) { mutableListOf() } +=
            PathMaterializedClaimCarrierSectionF64F32(
                carrierKeyF64F32 = carrierKeyF64F32,
                sourceSectionF64 = sourceSectionF64,
            )
    }
    preflightArrangementF64F32(carrierKeysBySourceF64F32.size.toLong(), candidateWorkBudgetI32)
    carrierKeysBySourceF64F32.values.forEach { carrierSectionsForSourceF64F32 ->
        preflightArrangementF64F32(
            checkedPathWorkMultiplyI64(carrierSectionsForSourceF64F32.size.toLong(), 2L),
            candidateWorkBudgetI32,
        )
        carrierSectionsForSourceF64F32.zipWithNext().forEach { (firstF64F32, secondF64F32) ->
            if (
                firstF64F32.sourceSectionF64.startParameterF64 > secondF64F32.sourceSectionF64.startParameterF64 ||
                    firstF64F32.sourceSectionF64.endParameterF64 > secondF64F32.sourceSectionF64.endParameterF64
            ) {
                throw IllegalStateException("path-f32-projection-collapse")
            }
        }
    }
    val resultF64F32 = mutableMapOf<PathHybridCarrierPairKeyF64F32, PathContactWitnessF64.PointF64>()
    projectedCoincidencesF32.forEach { coincidenceF32 ->
        val firstKeysF64F32 = materializedClaimCarrierKeysF64F32(
            claimF64 = coincidenceF32.firstClaimF64,
            carrierKeysBySourceF64F32 = carrierKeysBySourceF64F32,
            candidateWorkBudgetI32 = candidateWorkBudgetI32,
        )
        val secondKeysF64F32 = materializedClaimCarrierKeysF64F32(
            claimF64 = coincidenceF32.secondClaimF64,
            carrierKeysBySourceF64F32 = carrierKeysBySourceF64F32,
            candidateWorkBudgetI32 = candidateWorkBudgetI32,
        )
        if (firstKeysF64F32.size != secondKeysF64F32.size) {
            throw IllegalStateException("path-f32-projection-collapse")
        }
        val firstForward = coincidenceF32.firstClaimF64.endEdgeParameterF64 >
            coincidenceF32.firstClaimF64.startEdgeParameterF64
        val secondForward = coincidenceF32.secondClaimF64.endEdgeParameterF64 >
            coincidenceF32.secondClaimF64.startEdgeParameterF64
        val orderedSecondKeysF64F32 = if (firstForward == secondForward) {
            secondKeysF64F32
        } else {
            secondKeysF64F32.asReversed()
        }
        preflightArrangementF64F32(
            checkedPathWorkMultiplyI64(firstKeysF64F32.size.toLong(), 4L),
            candidateWorkBudgetI32,
        )
        firstKeysF64F32.indices.forEach { indexI32 ->
            val firstKeyF64F32 = firstKeysF64F32[indexI32]
            val secondKeyF64F32 = orderedSecondKeysF64F32[indexI32]
            addPointWitnessCarrierPairF64F32(
                resultF64F32,
                PathHybridCarrierPairKeyF64F32(firstKeyF64F32, secondKeyF64F32),
                coincidenceF32.pointWitnessF64,
            )
            addPointWitnessCarrierPairF64F32(
                resultF64F32,
                PathHybridCarrierPairKeyF64F32(secondKeyF64F32, firstKeyF64F32),
                coincidenceF32.pointWitnessF64,
            )
        }
    }
    return resultF64F32
}

private data class PathMaterializedClaimSourceKeyF64F32(
    val sourceSpanIdI64: Long,
    val inputEdgeIdI32: Int,
)

/** A final carrier section, retained as a list rather than a stale pre-split section index. */
private data class PathMaterializedClaimCarrierSectionF64F32(
    val carrierKeyF64F32: PathHybridCarrierKeyF64F32,
    val sourceSectionF64: PathFlattenedSectionF64,
)

private fun materializedClaimCarrierKeysF64F32(
    claimF64: PathProjectedSpanClaimF64,
    carrierKeysBySourceF64F32: Map<
        PathMaterializedClaimSourceKeyF64F32,
        List<PathMaterializedClaimCarrierSectionF64F32>,
        >,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): List<PathHybridCarrierKeyF64F32> {
    if (claimF64.startVertexIdentityF64 == null || claimF64.endVertexIdentityF64 == null) {
        throw IllegalStateException("path-f32-projection-collapse")
    }
    val carrierSectionsF64F32 = carrierKeysBySourceF64F32[
        PathMaterializedClaimSourceKeyF64F32(claimF64.sourceSpanIdI64, claimF64.inputEdgeIdI32)
    ] ?: throw IllegalStateException("path-f32-projection-collapse")
    // Two binary searches are bounded by the Int width before either probes a section.  The
    // exact contiguous run is debited separately before it is allocated or checked below.
    preflightArrangementF64F32(128L, candidateWorkBudgetI32)
    val firstIndexI32 = lowerBoundMaterializedClaimCarrierF64F32(
        carrierSectionsF64F32,
        claimF64.startParameterF64,
        end = false,
    )
    val lastIndexI32 = lowerBoundMaterializedClaimCarrierF64F32(
        carrierSectionsF64F32,
        claimF64.endParameterF64,
        end = true,
    )
    if (firstIndexI32 >= carrierSectionsF64F32.size || lastIndexI32 >= carrierSectionsF64F32.size || firstIndexI32 > lastIndexI32) {
        throw IllegalStateException("path-f32-projection-collapse")
    }
    val sectionCountI64 = checkedPathWorkAddI64(lastIndexI32.toLong() - firstIndexI32.toLong(), 1L)
    preflightArrangementF64F32(
        checkedPathWorkMultiplyI64(sectionCountI64, 5L),
        candidateWorkBudgetI32,
    )
    val firstSectionF64 = carrierSectionsF64F32[firstIndexI32].sourceSectionF64
    val lastSectionF64 = carrierSectionsF64F32[lastIndexI32].sourceSectionF64
    if (
        firstSectionF64.startParameterF64 != claimF64.startParameterF64 ||
            lastSectionF64.endParameterF64 != claimF64.endParameterF64
    ) {
        throw IllegalStateException("path-f32-projection-collapse")
    }
    val resultF64F32 = ArrayList<PathHybridCarrierKeyF64F32>(
        checkedPathCapacityI32(sectionCountI64, "path-candidate-limit"),
    )
    var previousEndParameterF64 = claimF64.startParameterF64
    for (indexI32 in firstIndexI32..lastIndexI32) {
        val carrierSectionF64F32 = carrierSectionsF64F32[indexI32]
        val sourceSectionF64 = carrierSectionF64F32.sourceSectionF64
        if (
            sourceSectionF64.startParameterF64 != previousEndParameterF64 ||
                sourceSectionF64.startParameterF64 >= sourceSectionF64.endParameterF64
        ) {
            throw IllegalStateException("path-f32-projection-collapse")
        }
        previousEndParameterF64 = sourceSectionF64.endParameterF64
        resultF64F32 += carrierSectionF64F32.carrierKeyF64F32
    }
    if (previousEndParameterF64 != claimF64.endParameterF64) {
        throw IllegalStateException("path-f32-projection-collapse")
    }
    return resultF64F32
}

private fun lowerBoundMaterializedClaimCarrierF64F32(
    carrierSectionsF64F32: List<PathMaterializedClaimCarrierSectionF64F32>,
    parameterF64: Double,
    end: Boolean,
): Int {
    var lowerI32 = 0
    var upperI32 = carrierSectionsF64F32.size
    while (lowerI32 < upperI32) {
        val middleI32 = lowerI32 + (upperI32 - lowerI32) / 2
        val sectionF64 = carrierSectionsF64F32[middleI32].sourceSectionF64
        val comparisonF64 = if (end) sectionF64.endParameterF64 else sectionF64.startParameterF64
        if (comparisonF64 < parameterF64) {
            lowerI32 = middleI32 + 1
        } else {
            upperI32 = middleI32
        }
    }
    return lowerI32
}

private fun addPointWitnessCarrierPairF64F32(
    pointWitnessByCarrierPairF64F32: MutableMap<PathHybridCarrierPairKeyF64F32, PathContactWitnessF64.PointF64>,
    pairKeyF64F32: PathHybridCarrierPairKeyF64F32,
    pointWitnessF64: PathContactWitnessF64.PointF64,
) {
    val previousF64 = pointWitnessByCarrierPairF64F32.put(pairKeyF64F32, pointWitnessF64)
    if (previousF64 != null && previousF64 != pointWitnessF64) {
        throw IllegalStateException("path-f32-projection-collapse")
    }
}

private fun pointWitnessResolvingCarrierGroupF64F32(
    orderedCarrierSectionsF64F32: List<PathHybridCarrierSectionF64F32>,
    pointWitnessByCarrierPairF64F32: Map<PathHybridCarrierPairKeyF64F32, PathContactWitnessF64.PointF64>,
): PathContactWitnessF64.PointF64? {
    val firstCarrierF64F32 = orderedCarrierSectionsF64F32.firstOrNull() ?: return null
    val firstKeyF64F32 = PathHybridCarrierKeyF64F32(
        firstCarrierF64F32.sourceSpanF64.sourceSpanIdI64,
        firstCarrierF64F32.sectionIndexI32,
    )
    var witnessF64: PathContactWitnessF64.PointF64? = null
    orderedCarrierSectionsF64F32.drop(1).forEach { carrierSectionF64F32 ->
        val keyF64F32 = PathHybridCarrierKeyF64F32(
            carrierSectionF64F32.sourceSpanF64.sourceSpanIdI64,
            carrierSectionF64F32.sectionIndexI32,
        )
        val pairWitnessF64 = pointWitnessByCarrierPairF64F32[
            PathHybridCarrierPairKeyF64F32(firstKeyF64F32, keyF64F32)
        ] ?: return null
        val previousF64 = witnessF64
        if (previousF64 != null && previousF64 != pairWitnessF64) return null
        witnessF64 = pairWitnessF64
    }
    return witnessF64
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
    // `startPointF64`/`endPointF64` are the registry's canonical topology coordinates.  They
    // deliberately do not choose an angular sector when exact evaluations on individual
    // carriers differ.  The hybrid arrangement keeps that per-incidence geometry for this
    // source-F64 ray proof; only the eventual DCEL embedding is lifted from F32.
    val directionF64 = carrierSectionF64F32.sourceSectionF64.endIncidencePointF64 -
        carrierSectionF64F32.sourceSectionF64.startIncidencePointF64
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
            if (halfEdgesF64F32[halfEdgeIndexI32].sourceDirectionsF64.any { directionF64 ->
                    hybridQuadrantF64F32(directionF64) <= 1
                }
            ) {
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
    // First charge the count pass itself.  The precise containment envelope depends on canonical
    // face boundaries, so deriving it with `sumOf` before a debit would be hidden work.
    preflightArrangementF64F32(
        checkedPathWorkAddI64(componentCountI64, facesI32.size.toLong()),
        candidateWorkBudgetI32,
    )
    var componentFaceEdgeWorkI64 = 0L
    componentsI32.forEach { componentI32 ->
        componentI32.faceIndicesI32.forEach { faceIndexI32 ->
            componentFaceEdgeWorkI64 = checkedPathWorkAddI64(
                componentFaceEdgeWorkI64,
                checkedPathWorkAddI64(
                    facesI32[faceIndexI32].boundaryHalfEdgeIndicesI32.size.toLong(),
                    1L,
                ),
            )
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
    sourceSpansByIdI64: Map<Long, PathSourceSpanF64>,
    collapsedIncidencesF64F32: List<PathCollapsedIncidenceF64F32>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): PathCanonicalTraceDispositionF64F32 {
    if (rawHalfEdgeIndicesI32.size < 3) throw IllegalStateException("path-f32-projection-collapse")
    preflightArrangementF64F32(
        checkedPathWorkAddI64(checkedPathWorkMultiplyI64(rawHalfEdgeIndicesI32.size.toLong(), 8L), 2L),
        candidateWorkBudgetI32,
    )
    val pointsF64 = rawHalfEdgeIndicesI32.map { halfEdgeIndexI32 ->
        verticesF64F32[halfEdgesF64F32[halfEdgeIndexI32].originVertexIndexI32].representativePointF32.toPoint2F64()
    }
    val areaF64 = signedDoubleAreaExpansionF64(pointsF64 + pointsF64.first())
    if (ExpansionF64.sign(areaF64) == 0) {
        return zeroAreaTraceDispositionF64F32(
            rawHalfEdgeIndicesI32 = rawHalfEdgeIndicesI32,
            traceSpanByHalfEdgeI32 = traceSpanByHalfEdgeI32,
            sourceSpansByIdI64 = sourceSpansByIdI64,
            collapsedIncidencesF64F32 = collapsedIncidencesF64F32,
            candidateWorkBudgetI32 = candidateWorkBudgetI32,
        )
    }
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
    return PathCanonicalTraceDispositionF64F32.Keep(PathCanonicalTraceF64F32(traceF64F32, rotatedI32, areaF64))
}

/** A selected projected trace is either emitted intact or disposed as one complete source contour. */
private sealed interface PathCanonicalTraceDispositionF64F32 {
    data class Keep(val traceF64F32: PathCanonicalTraceF64F32) : PathCanonicalTraceDispositionF64F32

    data object Drop : PathCanonicalTraceDispositionF64F32
}

private data class PathSourceContourKeyF64F32(
    val operand: PathOperand,
    val contourIndexI32: Int,
)

/**
 * A zero-area F32 trace cannot disappear just because the DCEL has no visible face.  It may drop
 * only when every source carrier of exactly one source contour is represented (including an
 * explicitly recorded collapse) and that contour's exact normalized double area is at most
 * 2^-45.  All partial, mixed, or significant losses reject before the writer sees a trace.
 */
private fun zeroAreaTraceDispositionF64F32(
    rawHalfEdgeIndicesI32: List<Int>,
    traceSpanByHalfEdgeI32: Map<Int, PathArrangementTraceSpanF64F32>,
    sourceSpansByIdI64: Map<Long, PathSourceSpanF64>,
    collapsedIncidencesF64F32: List<PathCollapsedIncidenceF64F32>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): PathCanonicalTraceDispositionF64F32 {
    preflightArrangementF64F32(
        checkedPathWorkAddI64(
            checkedPathWorkMultiplyI64(rawHalfEdgeIndicesI32.size.toLong(), 8L),
            checkedPathWorkAddI64(
                checkedPathWorkMultiplyI64(sourceSpansByIdI64.size.toLong(), 4L),
                checkedPathWorkMultiplyI64(collapsedIncidencesF64F32.size.toLong(), 4L),
            ),
        ),
        candidateWorkBudgetI32,
    )
    val representedCarrierKeysF64F32 = mutableSetOf<PathHybridCarrierKeyF64F32>()
    val contourKeysF64F32 = mutableSetOf<PathSourceContourKeyF64F32>()
    rawHalfEdgeIndicesI32.forEach { halfEdgeIndexI32 ->
        val traceSpanF64F32 = traceSpanByHalfEdgeI32.getValue(halfEdgeIndexI32)
        traceSpanF64F32.carrierSectionsF64F32.forEach { carrierSectionF64F32 ->
            representedCarrierKeysF64F32 += carrierSectionF64F32.toHybridCarrierKeyF64F32()
            contourKeysF64F32 += carrierSectionF64F32.sourceSpanF64.toSourceContourKeyF64F32()
        }
    }
    val contourKeyF64F32 = contourKeysF64F32.singleOrNull()
        ?: throw IllegalStateException("path-f32-projection-collapse")
    collapsedIncidencesF64F32.forEach { collapsedF64F32 ->
        if (collapsedF64F32.sourceSpanF64.toSourceContourKeyF64F32() == contourKeyF64F32) {
            representedCarrierKeysF64F32 += collapsedF64F32.toHybridCarrierKeyF64F32()
        }
    }
    val sourceSpansF64 = sourceSpansByIdI64.values.filter { sourceSpanF64 ->
        sourceSpanF64.toSourceContourKeyF64F32() == contourKeyF64F32
    }
    if (sourceSpansF64.isEmpty()) throw IllegalStateException("path-arrangement-inconsistent")
    val expectedCarrierKeysF64F32 = mutableSetOf<PathHybridCarrierKeyF64F32>()
    sourceSpansF64.forEach { sourceSpanF64 ->
        sourceSpanF64.flattenedSectionsF64.indices.forEach { sectionIndexI32 ->
            expectedCarrierKeysF64F32 += PathHybridCarrierKeyF64F32(sourceSpanF64.sourceSpanIdI64, sectionIndexI32)
        }
    }
    if (representedCarrierKeysF64F32 != expectedCarrierKeysF64F32) {
        throw IllegalStateException("path-f32-projection-collapse")
    }
    return when (sourceContourBoundaryDispositionF64F32(sourceSpansF64, candidateWorkBudgetI32)) {
        PathBoundaryDisposition.DROP -> PathCanonicalTraceDispositionF64F32.Drop
        PathBoundaryDisposition.KEEP,
        PathBoundaryDisposition.REJECT -> throw IllegalStateException("path-f32-projection-collapse")
    }
}

private fun PathHybridCarrierSectionF64F32.toHybridCarrierKeyF64F32(): PathHybridCarrierKeyF64F32 =
    PathHybridCarrierKeyF64F32(sourceSpanF64.sourceSpanIdI64, sectionIndexI32)

private fun PathCollapsedIncidenceF64F32.toHybridCarrierKeyF64F32(): PathHybridCarrierKeyF64F32 =
    PathHybridCarrierKeyF64F32(sourceSpanF64.sourceSpanIdI64, sectionIndexI32)

private fun PathSourceSpanF64.toSourceContourKeyF64F32(): PathSourceContourKeyF64F32 =
    PathSourceContourKeyF64F32(operand, contourIndexI32)

/**
 * An F32 sector is an embedding lookup only.  Its canonical signed-zero bits must never be used
 * to construct an identity, merge a source incidence, or settle a geometric equality.
 */
private data class PathCollapsedSectorPointF64F32(
    val xBitsI32: Int,
    val yBitsI32: Int,
)

private data class PathCollapsedSectorKeyF64F32(
    /** Canonical F32 trace signature for face lookup only; never a source identity. */
    val pointsF64F32: List<PathCollapsedSectorPointF64F32>,
)

/**
 * Structural evidence that a contour can no longer be treated as an ordinary independent
 * boundary.  The F32 point below is an embedding locator only; exact source identities remain
 * the authority for both evidence and sibling interaction.
 */
private sealed interface PathCollapsedContourProvenanceF64F32 {
    /** Every carrier section has one literal F32 endpoint alias and no ray remains to emit. */
    data object AllCarrierSectionsF32 : PathCollapsedContourProvenanceF64F32

    /**
     * One original source segment closed exactly onto its own authoritative endpoint after its
     * split spans covered [0, 1] without a gap, overlap, or orientation reversal.  Its flattened
     * rails can still be nonzero in F32, so this evidence is deliberately never drop-eligible.
     */
    data class SelfClosedSourceSegmentF64F32(
        val sourceSegmentIndexI32: Int,
        val endpointIdentityF64: PathVertexIdentityF64,
        val orientationI32: Int,
        /** Original F32 endpoint, retained solely as the post-selection sector embedding. */
        val representativePointF32: Point2F32,
    ) : PathCollapsedContourProvenanceF64F32
}

private data class PathCollapsedContourLedgerF64F32(
    val contourKeyF64F32: PathSourceContourKeyF64F32,
    val sourceSpansF64: List<PathSourceSpanF64>,
    /** Exact source incidences; this is the only evidence that collapsed siblings interact. */
    val vertexIdentitiesF64: Set<PathVertexIdentityF64>,
    val sectorKeyF64F32: PathCollapsedSectorKeyF64F32,
    val representativePointF32: Point2F32,
    /** Exact signed source-contour orientation, never the raw `+1` edge marker. */
    val firstWindingDeltaI64: Long,
    val secondWindingDeltaI64: Long,
    val withinCollapseTolerance: Boolean,
    val collapseProvenanceF64F32: PathCollapsedContourProvenanceF64F32,
)

private data class PathCollapsedSectorGroupF64F32(
    val sectorKeyF64F32: PathCollapsedSectorKeyF64F32,
    val representativePointF32: Point2F32,
    val contoursF64F32: List<PathCollapsedContourLedgerF64F32>,
    /** Same F32 embedding alone is ambiguous; sibling winding needs one connected exact graph. */
    val hasExactInteractionProof: Boolean,
)

/** Immutable pre-alias provenance carried until face selection completes. */
private data class PathCollapsedSectorLedgerF64F32(
    val sourceContourCountI32: Int,
    /** Includes whole literal collapses and non-droppable self-closed source-segment candidates. */
    val sectorCandidatesByContourKeyF64F32: Map<PathSourceContourKeyF64F32, PathCollapsedContourLedgerF64F32>,
    val sectorGroupsF64F32: List<PathCollapsedSectorGroupF64F32>,
    val allSourceContoursFullyCollapsed: Boolean,
)

private data class PathSourceContourExactAreaF64F32(
    val signI32: Int,
    val withinCollapseTolerance: Boolean,
)

private data class PathSourceSegmentIntervalF64F32(
    val sourceSpanF64: PathSourceSpanF64,
    val lowerParameterF64: Double,
    val upperParameterF64: Double,
    val orientationI32: Int,
)

private fun collapsedSectorPointF64F32(pointF32: Point2F32): PathCollapsedSectorPointF64F32 =
    PathCollapsedSectorPointF64F32(
        xBitsI32 = if (pointF32.x == 0f) 0 else pointF32.x.toRawBits(),
        yBitsI32 = if (pointF32.y == 0f) 0 else pointF32.y.toRawBits(),
    )

private fun collapsedSectorKeyF64F32(pointF32: Point2F32): PathCollapsedSectorKeyF64F32 =
    PathCollapsedSectorKeyF64F32(listOf(collapsedSectorPointF64F32(pointF32)))

private fun canonicalCollapsedSectorPointF64F32(pointF32: Point2F32): Point2F32 = Point2F32(
    x = if (pointF32.x == 0f) 0f else pointF32.x,
    y = if (pointF32.y == 0f) 0f else pointF32.y,
)

private fun compareCollapsedSectorPointsF64F32(
    firstF64F32: PathCollapsedSectorPointF64F32,
    secondF64F32: PathCollapsedSectorPointF64F32,
): Int = firstF64F32.xBitsI32.compareTo(secondF64F32.xBitsI32)
    .takeIf { it != 0 }
    ?: firstF64F32.yBitsI32.compareTo(secondF64F32.yBitsI32)

private fun compareCollapsedSectorKeysF64F32(
    firstF64F32: PathCollapsedSectorKeyF64F32,
    secondF64F32: PathCollapsedSectorKeyF64F32,
): Int {
    firstF64F32.pointsF64F32.size.compareTo(secondF64F32.pointsF64F32.size).takeIf { it != 0 }?.let { return it }
    firstF64F32.pointsF64F32.indices.forEach { indexI32 ->
        compareCollapsedSectorPointsF64F32(
            firstF64F32.pointsF64F32[indexI32],
            secondF64F32.pointsF64F32[indexI32],
        ).takeIf { it != 0 }?.let { return it }
    }
    return 0
}

/**
 * Finds the one non-literal collapse provenance needed for a source primitive which starts and
 * ends at the same *authoritative* F32 input vertex.  This is intentionally not a small-bounds
 * test: every split span must name the same original source segment and form one exact directed
 * partition of `[0, 1]`.  A generated F32 point cannot establish this provenance.
 */
private fun selfClosedSourceSegmentProvenanceF64F32(
    sourceSpansF64: List<PathSourceSpanF64>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): PathCollapsedContourProvenanceF64F32.SelfClosedSourceSegmentF64F32? {
    if (sourceSpansF64.isEmpty()) return null
    preflightArrangementF64F32(
        checkedPathWorkMultiplyI64(sourceSpansF64.size.toLong(), 11L),
        candidateWorkBudgetI32,
    )
    val sourceSegmentIndexI32 = sourceSpansF64.first().startLocationF64.sourceSegmentIndexI32
    if (sourceSpansF64.any { sourceSpanF64 ->
            sourceSpanF64.startLocationF64.sourceSegmentIndexI32 != sourceSegmentIndexI32 ||
                sourceSpanF64.endLocationF64.sourceSegmentIndexI32 != sourceSegmentIndexI32
        }
    ) {
        return null
    }
    val intervalsF64F32 = ArrayList<PathSourceSegmentIntervalF64F32>(
        checkedPathCapacityI32(sourceSpansF64.size.toLong(), "path-candidate-limit"),
    )
    var orientationI32 = 0
    sourceSpansF64.forEach { sourceSpanF64 ->
        val startParameterF64 = canonicalSourceSegmentParameterF64F32(sourceSpanF64.startLocationF64.parameterF64)
        val endParameterF64 = canonicalSourceSegmentParameterF64F32(sourceSpanF64.endLocationF64.parameterF64)
        val spanOrientationI32 = startParameterF64.compareTo(endParameterF64)
        if (spanOrientationI32 == 0) return null
        if (orientationI32 == 0) {
            orientationI32 = spanOrientationI32
        } else if (orientationI32 != spanOrientationI32) {
            // A reversal across split spans is not one original directed source primitive.
            return null
        }
        intervalsF64F32 += PathSourceSegmentIntervalF64F32(
            sourceSpanF64 = sourceSpanF64,
            lowerParameterF64 = minOf(startParameterF64, endParameterF64),
            upperParameterF64 = maxOf(startParameterF64, endParameterF64),
            orientationI32 = spanOrientationI32,
        )
    }
    val orderedIntervalsF64F32 = sortedArrangementF64F32(
        intervalsF64F32,
        candidateWorkBudgetI32,
    ) { firstF64F32, secondF64F32 ->
        firstF64F32.lowerParameterF64.compareTo(secondF64F32.lowerParameterF64)
            .takeIf { it != 0 }
            ?: firstF64F32.upperParameterF64.compareTo(secondF64F32.upperParameterF64)
    }
    var expectedStartParameterF64 = 0.0
    orderedIntervalsF64F32.forEach { intervalF64F32 ->
        if (
            intervalF64F32.orientationI32 != orientationI32 ||
                intervalF64F32.lowerParameterF64 != expectedStartParameterF64 ||
                intervalF64F32.upperParameterF64 <= intervalF64F32.lowerParameterF64
        ) {
            return null
        }
        expectedStartParameterF64 = intervalF64F32.upperParameterF64
    }
    if (expectedStartParameterF64 != 1.0) return null
    val startLocationF64 = sourceSegmentLocationAtParameterF64F32(
        sourceSpanF64 = orderedIntervalsF64F32.first().sourceSpanF64,
        parameterF64 = 0.0,
    ) ?: return null
    val endLocationF64 = sourceSegmentLocationAtParameterF64F32(
        sourceSpanF64 = orderedIntervalsF64F32.last().sourceSpanF64,
        parameterF64 = 1.0,
    ) ?: return null
    val endpointIdentityF64 = startLocationF64.vertexIdentityF64 ?: return null
    val endpointPointF32 = startLocationF64.originalPointF32 ?: return null
    if (
        endLocationF64.vertexIdentityF64 != endpointIdentityF64 ||
            endLocationF64.originalPointF32 == null ||
            !sameArrangementHybridPointF32(endpointPointF32, endLocationF64.originalPointF32)
    ) {
        return null
    }
    return PathCollapsedContourProvenanceF64F32.SelfClosedSourceSegmentF64F32(
        sourceSegmentIndexI32 = sourceSegmentIndexI32,
        endpointIdentityF64 = endpointIdentityF64,
        orientationI32 = orientationI32,
        representativePointF32 = canonicalCollapsedSectorPointF64F32(endpointPointF32),
    )
}

private fun canonicalSourceSegmentParameterF64F32(parameterF64: Double): Double {
    if (!parameterF64.isFinite() || parameterF64 < 0.0 || parameterF64 > 1.0) {
        throw IllegalStateException("path-f32-projection-collapse")
    }
    return if (parameterF64 == 0.0) 0.0 else parameterF64
}

private fun sourceSegmentLocationAtParameterF64F32(
    sourceSpanF64: PathSourceSpanF64,
    parameterF64: Double,
): PathSourceLocationF64? = when {
    canonicalSourceSegmentParameterF64F32(sourceSpanF64.startLocationF64.parameterF64) == parameterF64 ->
        sourceSpanF64.startLocationF64
    canonicalSourceSegmentParameterF64F32(sourceSpanF64.endLocationF64.parameterF64) == parameterF64 ->
        sourceSpanF64.endLocationF64
    else -> null
}

/**
 * Retains exact source incidence only as interaction provenance.  It is intentionally not an
 * F32 sector key: two tiny contours with the same rounded representative remain independent
 * unless the source registry gave them at least one common exact endpoint identity.
 */
private fun sourceContourVertexIdentitiesF64F32(
    sourceSpansF64: List<PathSourceSpanF64>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): Set<PathVertexIdentityF64> {
    preflightArrangementF64F32(
        checkedPathWorkMultiplyI64(sourceSpansF64.size.toLong(), 2L),
        candidateWorkBudgetI32,
    )
    var sectionCountI64 = 0L
    sourceSpansF64.forEach { sourceSpanF64 ->
        sectionCountI64 = checkedPathWorkAddI64(sectionCountI64, sourceSpanF64.flattenedSectionsF64.size.toLong())
    }
    preflightArrangementF64F32(
        checkedPathWorkMultiplyI64(sectionCountI64, 4L),
        candidateWorkBudgetI32,
    )
    val identitiesF64 = linkedSetOf<PathVertexIdentityF64>()
    sourceSpansF64.forEach { sourceSpanF64 ->
        sourceSpanF64.flattenedSectionsF64.forEach { sourceSectionF64 ->
            identitiesF64 += sourceSectionF64.startIdentityF64
            identitiesF64 += sourceSectionF64.endIdentityF64
        }
    }
    if (identitiesF64.isEmpty()) throw IllegalStateException("path-arrangement-inconsistent")
    return identitiesF64
}

/**
 * A multi-contour F32 sector may compose winding only if its members form one connected graph of
 * exact source incidences.  A shared rounded point is deliberately insufficient: without this
 * proof, XOR/INTERSECT and inverse fills could silently cancel geometrically separate contours.
 */
private fun collapsedSectorHasExactInteractionProofF64F32(
    contoursF64F32: List<PathCollapsedContourLedgerF64F32>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): Boolean {
    if (contoursF64F32.size < 2) return true
    preflightArrangementF64F32(
        checkedPathWorkMultiplyI64(contoursF64F32.size.toLong(), 2L),
        candidateWorkBudgetI32,
    )
    var identityCountI64 = 0L
    contoursF64F32.forEach { contourF64F32 ->
        identityCountI64 = checkedPathWorkAddI64(identityCountI64, contourF64F32.vertexIdentitiesF64.size.toLong())
    }
    preflightArrangementF64F32(
        checkedPathWorkAddI64(
            checkedPathWorkMultiplyI64(identityCountI64, 3L),
            contoursF64F32.size.toLong(),
        ),
        candidateWorkBudgetI32,
    )
    val componentsI32 = PathHybridDisjointSetI32(contoursF64F32.size)
    val firstContourByIdentityF64 = mutableMapOf<PathVertexIdentityF64, Int>()
    contoursF64F32.forEachIndexed { contourIndexI32, contourF64F32 ->
        contourF64F32.vertexIdentitiesF64.forEach { identityF64 ->
            val firstContourIndexI32 = firstContourByIdentityF64[identityF64]
            if (firstContourIndexI32 == null) {
                firstContourByIdentityF64[identityF64] = contourIndexI32
            } else {
                componentsI32.union(firstContourIndexI32, contourIndexI32)
            }
        }
    }
    val firstRootI32 = componentsI32.find(0)
    for (contourIndexI32 in 1 until contoursF64F32.size) {
        if (componentsI32.find(contourIndexI32) != firstRootI32) return false
    }
    return true
}

/**
 * Builds the complete collapsed-contour ledger before provisional aliases can remove a carrier
 * from the DCEL.  All structures are local until this method returns; it mutates neither source
 * topology nor aliases.  F32 is only the sector embedding used later to locate a face.
 */
private fun buildCollapsedSectorLedgerF64F32(
    sourceSpansByIdI64: Map<Long, PathSourceSpanF64>,
    collapsedIncidencesF64F32: List<PathCollapsedIncidenceF64F32>,
    carrierSectionsF64F32: List<PathHybridCarrierSectionF64F32>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): PathCollapsedSectorLedgerF64F32 {
    preflightArrangementF64F32(
        checkedPathWorkAddI64(
            checkedPathWorkAddI64(
                checkedPathWorkMultiplyI64(sourceSpansByIdI64.size.toLong(), 5L),
                checkedPathWorkMultiplyI64(collapsedIncidencesF64F32.size.toLong(), 7L),
            ),
            checkedPathWorkMultiplyI64(carrierSectionsF64F32.size.toLong(), 2L),
        ),
        candidateWorkBudgetI32,
    )
    val carrierKeysF64F32 = carrierSectionsF64F32.mapTo(mutableSetOf()) { carrierSectionF64F32 ->
        carrierSectionF64F32.toHybridCarrierKeyF64F32()
    }
    var carrierCountI64 = 0L
    sourceSpansByIdI64.values.forEach { sourceSpanF64 ->
        carrierCountI64 = checkedPathWorkAddI64(carrierCountI64, sourceSpanF64.flattenedSectionsF64.size.toLong())
    }
    preflightArrangementF64F32(
        checkedPathWorkAddI64(
            checkedPathWorkMultiplyI64(carrierCountI64, 8L),
            checkedPathWorkMultiplyI64(collapsedIncidencesF64F32.size.toLong(), 8L),
        ),
        candidateWorkBudgetI32,
    )
    val sourceSpansByContourF64F32 = mutableMapOf<PathSourceContourKeyF64F32, MutableList<PathSourceSpanF64>>()
    val expectedCarrierKeysByContourF64F32 = mutableMapOf<PathSourceContourKeyF64F32, MutableSet<PathHybridCarrierKeyF64F32>>()
    sourceSpansByIdI64.values.forEach { sourceSpanF64 ->
        val contourKeyF64F32 = sourceSpanF64.toSourceContourKeyF64F32()
        sourceSpansByContourF64F32.getOrPut(contourKeyF64F32) { mutableListOf() } += sourceSpanF64
        val expectedCarrierKeysF64F32 = expectedCarrierKeysByContourF64F32.getOrPut(contourKeyF64F32) { mutableSetOf() }
        sourceSpanF64.flattenedSectionsF64.indices.forEach { sectionIndexI32 ->
            val carrierKeyF64F32 = PathHybridCarrierKeyF64F32(sourceSpanF64.sourceSpanIdI64, sectionIndexI32)
            if (!expectedCarrierKeysF64F32.add(carrierKeyF64F32) || carrierKeyF64F32 !in carrierKeysF64F32) {
                throw IllegalStateException("path-arrangement-inconsistent")
            }
        }
    }
    val collapsedByContourF64F32 = mutableMapOf<
        PathSourceContourKeyF64F32,
        MutableMap<PathHybridCarrierKeyF64F32, PathCollapsedIncidenceF64F32>,
        >()
    collapsedIncidencesF64F32.forEach { collapsedF64F32 ->
        val sourceSpanF64 = collapsedF64F32.sourceSpanF64
        if (sourceSpansByIdI64[sourceSpanF64.sourceSpanIdI64] != sourceSpanF64) {
            throw IllegalStateException("path-arrangement-inconsistent")
        }
        val contourKeyF64F32 = sourceSpanF64.toSourceContourKeyF64F32()
        val carrierKeyF64F32 = collapsedF64F32.toHybridCarrierKeyF64F32()
        val previousF64F32 = collapsedByContourF64F32.getOrPut(contourKeyF64F32) { mutableMapOf() }
            .put(carrierKeyF64F32, collapsedF64F32)
        if (previousF64F32 != null) throw IllegalStateException("path-f32-projection-collapse")
    }
    val sectorCandidatesByContourKeyF64F32 = mutableMapOf<PathSourceContourKeyF64F32, PathCollapsedContourLedgerF64F32>()
    sourceSpansByContourF64F32.forEach { (contourKeyF64F32, sourceSpansF64) ->
        val expectedCarrierKeysF64F32 = expectedCarrierKeysByContourF64F32.getValue(contourKeyF64F32)
        val collapsedForContourF64F32 = collapsedByContourF64F32[contourKeyF64F32]
        if (collapsedForContourF64F32 != null && !expectedCarrierKeysF64F32.containsAll(collapsedForContourF64F32.keys)) {
            throw IllegalStateException("path-arrangement-inconsistent")
        }
        val allCarrierSectionsCollapsed =
            collapsedForContourF64F32 != null && expectedCarrierKeysF64F32.size == collapsedForContourF64F32.size
        val collapseProvenanceF64F32: PathCollapsedContourProvenanceF64F32
        val representativePointF32: Point2F32
        if (allCarrierSectionsCollapsed) {
            val collapsedForContour = collapsedForContourF64F32
            if (collapsedForContour.values.any { it.adjacencyF64F32 !is PathCollapsedAdjacencyF64F32.EntireContour }) {
                throw IllegalStateException("path-f32-projection-collapse")
            }
            representativePointF32 = canonicalCollapsedSectorPointF64F32(
                collapsedForContour.values.first().hybridVertexF64F32.representativePointF32,
            )
            if (collapsedForContour.values.any { collapsedF64F32 ->
                    !sameArrangementHybridPointF32(
                        representativePointF32,
                        collapsedF64F32.hybridVertexF64F32.representativePointF32,
                    )
                }
            ) {
                throw IllegalStateException("path-f32-projection-collapse")
            }
            collapseProvenanceF64F32 = PathCollapsedContourProvenanceF64F32.AllCarrierSectionsF32
        } else {
            // A merely zero-area F32 trace made from normal rails has no collapse provenance and
            // must remain in the ordinary DCEL path.  The only non-literal sector candidate is a
            // closed original source segment whose exact [0, 1] coverage is established above.
            // If any carrier was literally collapsed, however, this is a *partial* incidence and
            // must reach the true-neighbour continuation check below rather than being hidden by
            // the whole-source structural provenance.
            if (collapsedForContourF64F32 != null) return@forEach
            val selfClosedF64F32 = selfClosedSourceSegmentProvenanceF64F32(
                sourceSpansF64 = sourceSpansF64,
                candidateWorkBudgetI32 = candidateWorkBudgetI32,
            ) ?: return@forEach
            collapseProvenanceF64F32 = selfClosedF64F32
            representativePointF32 = selfClosedF64F32.representativePointF32
        }
        // Exact area can grow quadratically as an expansion.  It is therefore reached only after
        // literal carrier-collapse or exact source-segment provenance has made this contour a
        // sector candidate; ordinary tangent curves and zero-area multi-rail traces pay no N²
        // debit here.
        val exactAreaF64F32 = exactSourceContourAreaF64F32(sourceSpansF64, candidateWorkBudgetI32)
        val firstWindingDeltaI64 = if (contourKeyF64F32.operand == PathOperand.FIRST) {
            exactAreaF64F32.signI32.toLong()
        } else {
            0L
        }
        val secondWindingDeltaI64 = if (contourKeyF64F32.operand == PathOperand.SECOND) {
            exactAreaF64F32.signI32.toLong()
        } else {
            0L
        }
        sectorCandidatesByContourKeyF64F32[contourKeyF64F32] = PathCollapsedContourLedgerF64F32(
            contourKeyF64F32 = contourKeyF64F32,
            sourceSpansF64 = sourceSpansF64.toList(),
            vertexIdentitiesF64 = sourceContourVertexIdentitiesF64F32(
                sourceSpansF64 = sourceSpansF64,
                candidateWorkBudgetI32 = candidateWorkBudgetI32,
            ),
            sectorKeyF64F32 = collapsedSectorKeyF64F32(representativePointF32),
            representativePointF32 = representativePointF32,
            firstWindingDeltaI64 = firstWindingDeltaI64,
            secondWindingDeltaI64 = secondWindingDeltaI64,
            withinCollapseTolerance = exactAreaF64F32.withinCollapseTolerance,
            collapseProvenanceF64F32 = collapseProvenanceF64F32,
        )
    }
    preflightArrangementF64F32(
        checkedPathWorkMultiplyI64(sectorCandidatesByContourKeyF64F32.size.toLong(), 8L),
        candidateWorkBudgetI32,
    )
    val candidateContoursBySectorF64F32 = mutableMapOf<PathCollapsedSectorKeyF64F32, MutableList<PathCollapsedContourLedgerF64F32>>()
    sectorCandidatesByContourKeyF64F32.values.forEach { contourF64F32 ->
        candidateContoursBySectorF64F32.getOrPut(contourF64F32.sectorKeyF64F32) { mutableListOf() } += contourF64F32
    }
    // A merely zero-area trace composed only of ordinary noncollapsed carriers never enters this
    // map.  A self-closed source segment does enter, but its provenance makes it non-droppable.
    val contoursBySectorF64F32 = candidateContoursBySectorF64F32
    val sectorGroupsF64F32 = sortedArrangementF64F32(
        contoursBySectorF64F32.values.map { contoursF64F32 ->
            val representativePointF32 = contoursF64F32.first().representativePointF32
            PathCollapsedSectorGroupF64F32(
                sectorKeyF64F32 = contoursF64F32.first().sectorKeyF64F32,
                representativePointF32 = representativePointF32,
                contoursF64F32 = sortedArrangementF64F32(
                    contoursF64F32,
                    candidateWorkBudgetI32,
                ) { firstF64F32, secondF64F32 ->
                    firstF64F32.contourKeyF64F32.operand.compareTo(secondF64F32.contourKeyF64F32.operand)
                        .takeIf { it != 0 }
                        ?: firstF64F32.contourKeyF64F32.contourIndexI32.compareTo(secondF64F32.contourKeyF64F32.contourIndexI32)
                },
                hasExactInteractionProof = collapsedSectorHasExactInteractionProofF64F32(
                    contoursF64F32 = contoursF64F32,
                    candidateWorkBudgetI32 = candidateWorkBudgetI32,
                ),
            )
        },
        candidateWorkBudgetI32,
    ) { firstF64F32, secondF64F32 ->
        compareArrangementPointsF32F64(firstF64F32.representativePointF32, secondF64F32.representativePointF32)
            .takeIf { it != 0 }
            ?: compareCollapsedSectorKeysF64F32(firstF64F32.sectorKeyF64F32, secondF64F32.sectorKeyF64F32)
    }
    return PathCollapsedSectorLedgerF64F32(
        sourceContourCountI32 = sourceSpansByContourF64F32.size,
        sectorCandidatesByContourKeyF64F32 = sectorCandidatesByContourKeyF64F32,
        sectorGroupsF64F32 = sectorGroupsF64F32,
        allSourceContoursFullyCollapsed =
            sourceSpansByContourF64F32.isNotEmpty() &&
                sourceSpansByContourF64F32.size == sectorCandidatesByContourKeyF64F32.size &&
                sectorCandidatesByContourKeyF64F32.values.all { contourF64F32 ->
                    contourF64F32.collapseProvenanceF64F32 is PathCollapsedContourProvenanceF64F32.AllCarrierSectionsF32
                },
    )
}

private fun collapsedGroupBoundaryDispositionF64F32(
    groupF64F32: PathCollapsedSectorGroupF64F32,
    baseFirstWindingI32: Int,
    baseSecondWindingI32: Int,
    selectsWindingF64F32: (Int, Int) -> Boolean,
): PathBoundaryDisposition {
    if (!groupF64F32.hasExactInteractionProof) return PathBoundaryDisposition.REJECT
    var firstDeltaI64 = 0L
    var secondDeltaI64 = 0L
    groupF64F32.contoursF64F32.forEach { contourF64F32 ->
        firstDeltaI64 += contourF64F32.firstWindingDeltaI64
        secondDeltaI64 += contourF64F32.secondWindingDeltaI64
    }
    val insideFirstWindingI64 = baseFirstWindingI32.toLong() + firstDeltaI64
    val insideSecondWindingI64 = baseSecondWindingI32.toLong() + secondDeltaI64
    if (
        insideFirstWindingI64 !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() ||
            insideSecondWindingI64 !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()
    ) {
        return PathBoundaryDisposition.REJECT
    }
    if (
        selectsWindingF64F32(baseFirstWindingI32, baseSecondWindingI32) ==
            selectsWindingF64F32(insideFirstWindingI64.toInt(), insideSecondWindingI64.toInt())
    ) {
        return PathBoundaryDisposition.KEEP
    }
    var hasFirstOperand = false
    var hasSecondOperand = false
    groupF64F32.contoursF64F32.forEach { contourF64F32 ->
        when (contourF64F32.contourKeyF64F32.operand) {
            PathOperand.FIRST -> hasFirstOperand = true
            PathOperand.SECOND -> hasSecondOperand = true
        }
    }
    // A boundary selected only by the jointly composed `(first, second)` winding has no
    // single-source whole-contour representation after both F32 rails collapsed.  It has been
    // proven selected, but cannot be emitted or discarded independently; reject atomically.
    if (hasFirstOperand && hasSecondOperand) return PathBoundaryDisposition.REJECT
    // A sector can disappear only as one complete, selected group.  Since each ledger member
    // proved all of its source carriers collapsed, this is a whole-contour decision rather than
    // an inference from a missing half-edge.
    return if (
        groupF64F32.contoursF64F32.all { contourF64F32 ->
            contourF64F32.collapseProvenanceF64F32 is PathCollapsedContourProvenanceF64F32.AllCarrierSectionsF32 &&
                contourF64F32.withinCollapseTolerance
        }
    ) {
        PathBoundaryDisposition.DROP
    } else {
        PathBoundaryDisposition.REJECT
    }
}

private fun classifyCollapsedSectorsWithoutFacesF64F32(
    collapsedSectorLedgerF64F32: PathCollapsedSectorLedgerF64F32,
    selectsWindingF64F32: (Int, Int) -> Boolean,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): PathBoundaryDisposition {
    if (collapsedSectorLedgerF64F32.sectorGroupsF64F32.isEmpty()) return PathBoundaryDisposition.KEEP
    // With no F32 face graph, exactly one fully collapsed sector is the only case whose base
    // winding is known (0, 0).  Distinct sectors or a remaining noncollapsed contour could alter
    // that base, so they reject instead of silently becoming an empty output.
    if (
        !collapsedSectorLedgerF64F32.allSourceContoursFullyCollapsed ||
            collapsedSectorLedgerF64F32.sectorGroupsF64F32.size != 1
    ) {
        return PathBoundaryDisposition.REJECT
    }
    preflightArrangementF64F32(8L, candidateWorkBudgetI32)
    return collapsedGroupBoundaryDispositionF64F32(
        groupF64F32 = collapsedSectorLedgerF64F32.sectorGroupsF64F32.single(),
        baseFirstWindingI32 = 0,
        baseSecondWindingI32 = 0,
        selectsWindingF64F32 = selectsWindingF64F32,
    )
}

private fun classifyCollapsedSectorsAfterFaceSelectionF64F32(
    collapsedSectorLedgerF64F32: PathCollapsedSectorLedgerF64F32,
    facesI32: List<PathHybridFaceI32>,
    verticesF64F32: List<PathHybridVertexF64F32>,
    halfEdgesF64F32: List<PathHybridHalfEdgeF64F32>,
    selectsWindingF64F32: (Int, Int) -> Boolean,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): PathBoundaryDisposition {
    val groupsF64F32 = collapsedSectorLedgerF64F32.sectorGroupsF64F32
    if (groupsF64F32.isEmpty()) return PathBoundaryDisposition.KEEP
    // One point-in-face pass per F32 sector, not per collapsed sibling.  The face-boundary scan
    // is bounded and debited before any predicate is evaluated.
    preflightArrangementF64F32(
        checkedPathWorkMultiplyI64(
            groupsF64F32.size.toLong(),
            checkedPathWorkAddI64(
                checkedPathWorkMultiplyI64(facesI32.size.toLong(), 2L),
                checkedPathWorkMultiplyI64(halfEdgesF64F32.size.toLong(), 3L),
            ),
        ),
        candidateWorkBudgetI32,
    )
    groupsF64F32.forEach { groupF64F32 ->
        var containingFaceCountI32 = 0
        var baseFirstWindingI32 = 0
        var baseSecondWindingI32 = 0
        val representativePointF64 = groupF64F32.representativePointF32.toPoint2F64()
        facesI32.forEach { faceI32 ->
            when (
                classifyPointInHybridFaceF64F32(
                    faceI32 = faceI32,
                    pointF64 = representativePointF64,
                    halfEdgesF64F32 = halfEdgesF64F32,
                    verticesF64F32 = verticesF64F32,
                )
            ) {
                PathHybridFacePointRelationF64F32.BOUNDARY -> return PathBoundaryDisposition.REJECT
                PathHybridFacePointRelationF64F32.INSIDE -> {
                    containingFaceCountI32 += 1
                    baseFirstWindingI32 = faceI32.firstWindingI32
                    baseSecondWindingI32 = faceI32.secondWindingI32
                }
                PathHybridFacePointRelationF64F32.OUTSIDE -> Unit
            }
        }
        if (containingFaceCountI32 > 1) return PathBoundaryDisposition.REJECT
        if (
            collapsedGroupBoundaryDispositionF64F32(
                groupF64F32 = groupF64F32,
                baseFirstWindingI32 = baseFirstWindingI32,
                baseSecondWindingI32 = baseSecondWindingI32,
                selectsWindingF64F32 = selectsWindingF64F32,
            ) == PathBoundaryDisposition.REJECT
        ) {
            return PathBoundaryDisposition.REJECT
        }
    }
    return PathBoundaryDisposition.KEEP
}

/**
 * A collapsed carrier has no half-edge, but its source-contour membership remains observable
 * until face selection.  A partial collapsed span uses only its actual adjacent noncollapsed
 * rays; a distant selected edge from the same contour is not evidence of local dependency.
 */
private fun classifySelectedCollapsedContinuationsF64F32(
    selectedI32: BooleanArray,
    halfEdgesF64F32: List<PathHybridHalfEdgeF64F32>,
    traceSpanByHalfEdgeI32: Map<Int, PathArrangementTraceSpanF64F32>,
    sourceSpansByIdI64: Map<Long, PathSourceSpanF64>,
    collapsedIncidencesF64F32: List<PathCollapsedIncidenceF64F32>,
    collapsedSectorLedgerF64F32: PathCollapsedSectorLedgerF64F32,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): PathBoundaryDisposition {
    if (collapsedIncidencesF64F32.isEmpty()) return PathBoundaryDisposition.KEEP
    preflightArrangementF64F32(
        checkedPathWorkAddI64(
            checkedPathWorkAddI64(
                checkedPathWorkMultiplyI64(halfEdgesF64F32.size.toLong(), 5L),
                checkedPathWorkMultiplyI64(collapsedIncidencesF64F32.size.toLong(), 18L),
            ),
            checkedPathWorkMultiplyI64(sourceSpansByIdI64.size.toLong(), 2L),
        ),
        candidateWorkBudgetI32,
    )
    var selectedCarrierCountI64 = 0L
    selectedI32.indices.forEach { halfEdgeIndexI32 ->
        if (!selectedI32[halfEdgeIndexI32]) return@forEach
        selectedCarrierCountI64 = checkedPathWorkAddI64(
            selectedCarrierCountI64,
            traceSpanByHalfEdgeI32.getValue(halfEdgeIndexI32).carrierSectionsF64F32.size.toLong(),
        )
    }
    preflightArrangementF64F32(selectedCarrierCountI64, candidateWorkBudgetI32)
    val selectedCarrierKeysF64F32 = mutableSetOf<PathHybridCarrierKeyF64F32>()
    selectedI32.indices.forEach { halfEdgeIndexI32 ->
        if (!selectedI32[halfEdgeIndexI32]) return@forEach
        traceSpanByHalfEdgeI32.getValue(halfEdgeIndexI32).carrierSectionsF64F32.forEach { carrierSectionF64F32 ->
            selectedCarrierKeysF64F32 += carrierSectionF64F32.toHybridCarrierKeyF64F32()
        }
    }
    collapsedIncidencesF64F32.forEach { collapsedF64F32 ->
        val sourceSpanF64 = collapsedF64F32.sourceSpanF64
        if (sourceSpansByIdI64[sourceSpanF64.sourceSpanIdI64] != sourceSpanF64) {
            throw IllegalStateException("path-arrangement-inconsistent")
        }
        if (sourceSpanF64.toSourceContourKeyF64F32() in collapsedSectorLedgerF64F32.sectorCandidatesByContourKeyF64F32) {
            return@forEach
        }
        when (val adjacencyF64F32 = collapsedF64F32.adjacencyF64F32) {
            is PathCollapsedAdjacencyF64F32.Rays -> {
                val incomingSourceSpanF64 = sourceSpansByIdI64[adjacencyF64F32.incomingSourceSpanIdI64]
                val outgoingSourceSpanF64 = sourceSpansByIdI64[adjacencyF64F32.outgoingSourceSpanIdI64]
                if (
                    incomingSourceSpanF64 == null || outgoingSourceSpanF64 == null ||
                        adjacencyF64F32.incomingSectionIndexI32 !in incomingSourceSpanF64.flattenedSectionsF64.indices ||
                        adjacencyF64F32.outgoingSectionIndexI32 !in outgoingSourceSpanF64.flattenedSectionsF64.indices
                ) {
                    throw IllegalStateException("path-arrangement-inconsistent")
                }
                val incomingSectionF64 = incomingSourceSpanF64.flattenedSectionsF64[
                    adjacencyF64F32.incomingSectionIndexI32
                ]
                val outgoingSectionF64 = outgoingSourceSpanF64.flattenedSectionsF64[
                    adjacencyF64F32.outgoingSectionIndexI32
                ]
                val expectedIncomingDirectionF64 = incomingSectionF64.startIncidencePointF64 -
                    incomingSectionF64.endIncidencePointF64
                val expectedOutgoingDirectionF64 = outgoingSectionF64.endIncidencePointF64 -
                    outgoingSectionF64.startIncidencePointF64
                // Retain and verify the actual two contour rays.  The stored directions are not
                // synthesized as `-d/+d` from the collapsed carrier: they must still match the
                // neighbouring source sections that the selected-carrier test below observes.
                if (
                    adjacencyF64F32.incomingDirectionF64 != expectedIncomingDirectionF64 ||
                        adjacencyF64F32.outgoingDirectionF64 != expectedOutgoingDirectionF64 ||
                        (expectedIncomingDirectionF64.x == 0.0 && expectedIncomingDirectionF64.y == 0.0) ||
                        (expectedOutgoingDirectionF64.x == 0.0 && expectedOutgoingDirectionF64.y == 0.0)
                ) {
                    throw IllegalStateException("path-f32-projection-collapse")
                }
                val incomingKeyF64F32 = PathHybridCarrierKeyF64F32(
                    adjacencyF64F32.incomingSourceSpanIdI64,
                    adjacencyF64F32.incomingSectionIndexI32,
                )
                val outgoingKeyF64F32 = PathHybridCarrierKeyF64F32(
                    adjacencyF64F32.outgoingSourceSpanIdI64,
                    adjacencyF64F32.outgoingSectionIndexI32,
                )
                // The adjacency builder walked the source contour and supplied these true
                // entering/outgoing rays.  Only a selected neighbor can prove that this missing
                // incidence participates in closure, orientation, or winding.
                if (
                    incomingKeyF64F32 in selectedCarrierKeysF64F32 ||
                        outgoingKeyF64F32 in selectedCarrierKeysF64F32
                ) {
                    return PathBoundaryDisposition.REJECT
                }
            }
            PathCollapsedAdjacencyF64F32.Unresolved -> return PathBoundaryDisposition.REJECT
            PathCollapsedAdjacencyF64F32.EntireContour -> return PathBoundaryDisposition.REJECT
        }
    }
    return PathBoundaryDisposition.KEEP
}

private enum class PathHybridFacePointRelationF64F32 { INSIDE, OUTSIDE, BOUNDARY }

/**
 * This is only a selection-provenance locator.  It has no authority to merge vertices or create
 * an alias: an on-edge representative is returned as `BOUNDARY` and therefore rejects.
 */
private fun classifyPointInHybridFaceF64F32(
    faceI32: PathHybridFaceI32,
    pointF64: Point2F64,
    halfEdgesF64F32: List<PathHybridHalfEdgeF64F32>,
    verticesF64F32: List<PathHybridVertexF64F32>,
): PathHybridFacePointRelationF64F32 {
    var windingI64 = 0L
    faceI32.boundaryHalfEdgeIndicesI32.forEach { halfEdgeIndexI32 ->
        val halfEdgeF64F32 = halfEdgesF64F32[halfEdgeIndexI32]
        val startF64 = verticesF64F32[halfEdgeF64F32.originVertexIndexI32].representativePointF32.toPoint2F64()
        val endF64 = verticesF64F32[halfEdgeF64F32.destinationVertexIndexI32].representativePointF32.toPoint2F64()
        if (PathPredicatesF64.onSegment(pointF64, startF64, endF64)) {
            return PathHybridFacePointRelationF64F32.BOUNDARY
        }
        val startAtOrBelow = startF64.y <= pointF64.y
        val endAbove = endF64.y > pointF64.y
        val endAtOrBelow = endF64.y <= pointF64.y
        if (startAtOrBelow && endAbove && OrientationPredicateF64.sign(startF64, endF64, pointF64) > 0) {
            windingI64 += 1L
        } else if (!startAtOrBelow && endAtOrBelow && OrientationPredicateF64.sign(startF64, endF64, pointF64) < 0) {
            windingI64 -= 1L
        }
    }
    return if (windingI64 == 0L) {
        PathHybridFacePointRelationF64F32.OUTSIDE
    } else {
        PathHybridFacePointRelationF64F32.INSIDE
    }
}

private fun sourceContourBoundaryDispositionF64F32(
    sourceSpansF64: List<PathSourceSpanF64>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): PathBoundaryDisposition = if (
    sourceContourDoubleAreaWithinCollapseToleranceF64(sourceSpansF64, candidateWorkBudgetI32)
) {
    PathBoundaryDisposition.DROP
} else {
    PathBoundaryDisposition.REJECT
}

private fun sourceContourDoubleAreaWithinCollapseToleranceF64(
    sourceSpansF64: List<PathSourceSpanF64>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): Boolean = exactSourceContourAreaF64F32(
    sourceSpansF64 = sourceSpansF64,
    candidateWorkBudgetI32 = candidateWorkBudgetI32,
).withinCollapseTolerance

/**
 * Exact area is intentionally bounded quadratically before expansion growth begins.  An
 * expansion sum can grow with every prior term, so a linear debit would undercount the work and
 * allocation performed by an adversarially long collapsed contour.
 */
private fun exactSourceContourAreaF64F32(
    sourceSpansF64: List<PathSourceSpanF64>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): PathSourceContourExactAreaF64F32 {
    preflightArrangementF64F32(
        checkedPathWorkMultiplyI64(sourceSpansF64.size.toLong(), 2L),
        candidateWorkBudgetI32,
    )
    var sectionCountI64 = 0L
    sourceSpansF64.forEach { sourceSpanF64 ->
        sectionCountI64 = checkedPathWorkAddI64(sectionCountI64, sourceSpanF64.flattenedSectionsF64.size.toLong())
    }
    preflightArrangementF64F32(
        checkedPathWorkAddI64(
            checkedPathWorkAddI64(
                checkedPathWorkMultiplyI64(sectionCountI64, 10L),
                checkedPathWorkMultiplyI64(sourceSpansF64.size.toLong(), 2L),
            ),
            checkedPathWorkMultiplyI64(sectionCountI64, sectionCountI64),
        ),
        candidateWorkBudgetI32,
    )
    var doubleAreaF64 = doubleArrayOf()
    sourceSpansF64.forEach { sourceSpanF64 ->
        sourceSpanF64.flattenedSectionsF64.forEach { sectionF64 ->
            val crossF64 = ExpansionF64.expansionDiff(
                ExpansionF64.twoProduct(sectionF64.startPointF64.x, sectionF64.endPointF64.y),
                ExpansionF64.twoProduct(sectionF64.startPointF64.y, sectionF64.endPointF64.x),
            )
            doubleAreaF64 = ExpansionF64.expansionSum(doubleAreaF64, crossF64)
        }
    }
    val signI32 = ExpansionF64.sign(doubleAreaF64)
    if (signI32 == 0) {
        return PathSourceContourExactAreaF64F32(
            signI32 = 0,
            withinCollapseTolerance = true,
        )
    }
    val absoluteDoubleAreaF64 = if (signI32 > 0) doubleAreaF64 else DoubleArray(doubleAreaF64.size) { indexI32 -> -doubleAreaF64[indexI32] }
    return PathSourceContourExactAreaF64F32(
        signI32 = signI32,
        withinCollapseTolerance = ExpansionF64.sign(
            ExpansionF64.expansionDiff(absoluteDoubleAreaF64, doubleArrayOf(2.0.pow(-45))),
        ) <= 0,
    )
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
    traceSpanByHalfEdgeI32.getValue(firstI32).embeddingDirectionF64,
    traceSpanByHalfEdgeI32.getValue(secondI32).embeddingDirectionF64,
)

private fun sameHybridOutputRayF64F32(
    firstI32: Int,
    secondI32: Int,
    traceSpanByHalfEdgeI32: Map<Int, PathArrangementTraceSpanF64F32>,
): Boolean = sameHybridDirectionsF64F32(
    traceSpanByHalfEdgeI32.getValue(firstI32).embeddingDirectionF64,
    traceSpanByHalfEdgeI32.getValue(secondI32).embeddingDirectionF64,
)

private fun compareHybridOutgoingRaysF64F32(
    firstI32: Int,
    secondI32: Int,
    originVertexIndexI32: Int,
    halfEdgesF64F32: List<PathMutableHybridHalfEdgeF64F32>,
): Int = compareHybridDirectionsF64F32(
    halfEdgesF64F32[firstI32].embeddingDirectionF64,
    halfEdgesF64F32[secondI32].embeddingDirectionF64,
)

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

/**
 * Builds the source-only angular sweep before the F32 embedding order is even computed.
 *
 * Equal source rays may repeat inside one already aggregated carrier bundle.  The same ray in
 * two distinct bundles has no exact coalescing authority, so it rejects here rather than letting
 * an F32 position, an ID, or a face traversal choose an order.
 */
private fun sweepHybridSourceDirectionsAtVertexF64F32(
    halfEdgeIndicesI32: List<Int>,
    halfEdgesF64F32: List<PathMutableHybridHalfEdgeF64F32>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): List<PathHybridSourceAngularEventF64F32> {
    if (halfEdgeIndicesI32.isEmpty()) return emptyList()
    val bundleCountI64 = halfEdgeIndicesI32.size.toLong()
    // Count every bundle visit, direction-list read, and empty-list predicate before reading a
    // carrier.  This is deliberately separate from event materialization below.
    preflightArrangementF64F32(
        checkedPathWorkMultiplyI64(bundleCountI64, 3L),
        candidateWorkBudgetI32,
    )
    var sourceEventCountI64 = 0L
    halfEdgeIndicesI32.forEach { bundleHalfEdgeIndexI32 ->
        val sourceDirectionsF64 = halfEdgesF64F32[bundleHalfEdgeIndexI32].sourceDirectionsF64
        if (sourceDirectionsF64.isEmpty()) throw IllegalStateException("path-f32-projection-collapse")
        sourceEventCountI64 = checkedPathWorkAddI64(sourceEventCountI64, sourceDirectionsF64.size.toLong())
    }
    // Reserve the event array and every source-ray visit, zero predicate, and append before
    // allocating or walking the carrier directions.  F32 compatibility stays after the exact
    // equality guard below so it cannot decide an equal-ray ambiguity.
    preflightArrangementF64F32(
        checkedPathWorkAddI64(
            checkedPathWorkMultiplyI64(sourceEventCountI64, 3L),
            checkedPathWorkMultiplyI64(bundleCountI64, 2L),
        ),
        candidateWorkBudgetI32,
    )
    val sourceEventsF64F32 = ArrayList<PathHybridSourceAngularEventF64F32>(
        checkedPathCapacityI32(sourceEventCountI64, "path-candidate-limit"),
    )
    halfEdgeIndicesI32.forEach { bundleHalfEdgeIndexI32 ->
        val bundleF64F32 = halfEdgesF64F32[bundleHalfEdgeIndexI32]
        bundleF64F32.sourceDirectionsF64.forEach { sourceDirectionF64 ->
            if (sourceDirectionF64.x == 0.0 && sourceDirectionF64.y == 0.0) {
                throw IllegalStateException("path-f32-projection-collapse")
            }
            sourceEventsF64F32 += PathHybridSourceAngularEventF64F32(
                bundleHalfEdgeIndexI32 = bundleHalfEdgeIndexI32,
                directionF64 = sourceDirectionF64,
            )
        }
    }
    val orderedSourceEventsF64F32 = sortedArrangementF64F32(
        sourceEventsF64F32,
        candidateWorkBudgetI32,
    ) { firstF64F32, secondF64F32 ->
        compareHybridDirectionsF64F32(firstF64F32.directionF64, secondF64F32.directionF64)
    }
    // The equality scan is source-only and precedes every F32 embedding decision.  Charge both
    // event visits and exact ray predicates before reading the sorted events.
    preflightArrangementF64F32(
        checkedPathWorkMultiplyI64(sourceEventCountI64, 2L),
        candidateWorkBudgetI32,
    )
    var previousEventF64F32: PathHybridSourceAngularEventF64F32? = null
    orderedSourceEventsF64F32.forEach { eventF64F32 ->
        val previousF64F32 = previousEventF64F32
        if (
            previousF64F32 != null &&
            previousF64F32.bundleHalfEdgeIndexI32 != eventF64F32.bundleHalfEdgeIndexI32 &&
            sameHybridDirectionsF64F32(previousF64F32.directionF64, eventF64F32.directionF64)
        ) {
            throw IllegalStateException("path-f32-projection-collapse")
        }
        previousEventF64F32 = eventF64F32
    }
    // Only after source equality is unambiguous may a source ray be checked against its F32
    // embedding.  Reserve every bundle lookup, dot product, and sign predicate first.
    preflightArrangementF64F32(
        checkedPathWorkMultiplyI64(sourceEventCountI64, 3L),
        candidateWorkBudgetI32,
    )
    orderedSourceEventsF64F32.forEach { eventF64F32 ->
        val embeddingDirectionF64 = halfEdgesF64F32[eventF64F32.bundleHalfEdgeIndexI32].embeddingDirectionF64
        val dotF64 = eventF64F32.directionF64.x * embeddingDirectionF64.x +
            eventF64F32.directionF64.y * embeddingDirectionF64.y
        if (dotF64 <= 0.0) throw IllegalStateException("path-f32-projection-collapse")
    }
    return orderedSourceEventsF64F32
}

/**
 * Proves that the F32 embedding has not inverted the already unambiguous exact source sectors.
 * The source sweep has rejected unresolved equal rays before this function receives the F32
 * ordering; this phase only checks cyclic run/rotation consistency.
 */
private fun validateHybridSourceDirectionBundlesAtVertexF64F32(
    halfEdgeIndicesI32: List<Int>,
    orderedSourceEventsF64F32: List<PathHybridSourceAngularEventF64F32>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
) {
    if (halfEdgeIndicesI32.isEmpty()) return
    val bundleCountI64 = halfEdgeIndicesI32.size.toLong()
    val sourceEventCountI64 = orderedSourceEventsF64F32.size.toLong()
    // Reserve the bundle-position lookup map, run list, membership bitmap, source-event
    // visits, and cyclic order predicates before allocating any of those structures.
    preflightArrangementF64F32(
        checkedPathWorkAddI64(
            checkedPathWorkMultiplyI64(sourceEventCountI64, 3L),
            checkedPathWorkMultiplyI64(bundleCountI64, 5L),
        ),
        candidateWorkBudgetI32,
    )
    val embeddingPositionByHalfEdgeIndexI32 = mutableMapOf<Int, Int>()
    halfEdgeIndicesI32.forEachIndexed { embeddingPositionI32, bundleHalfEdgeIndexI32 ->
        if (embeddingPositionByHalfEdgeIndexI32.put(bundleHalfEdgeIndexI32, embeddingPositionI32) != null) {
            throw IllegalStateException("path-f32-projection-collapse")
        }
    }
    val orderedBundleRunEmbeddingPositionsI32 = ArrayList<Int>(
        checkedPathCapacityI32(sourceEventCountI64, "path-candidate-limit"),
    )
    // Half-edge indices are list indices, so -1 is an unambiguous no-previous-event sentinel.
    var previousBundleHalfEdgeIndexI32 = -1
    orderedSourceEventsF64F32.forEach { eventF64F32 ->
        val embeddingPositionI32 = embeddingPositionByHalfEdgeIndexI32[eventF64F32.bundleHalfEdgeIndexI32]
            ?: throw IllegalStateException("path-f32-projection-collapse")
        if (previousBundleHalfEdgeIndexI32 != eventF64F32.bundleHalfEdgeIndexI32) {
            orderedBundleRunEmbeddingPositionsI32 += embeddingPositionI32
        }
        previousBundleHalfEdgeIndexI32 = eventF64F32.bundleHalfEdgeIndexI32
    }
    if (
        orderedBundleRunEmbeddingPositionsI32.size > 1 &&
        orderedSourceEventsF64F32.first().bundleHalfEdgeIndexI32 == orderedSourceEventsF64F32.last().bundleHalfEdgeIndexI32
    ) {
        orderedBundleRunEmbeddingPositionsI32.removeAt(orderedBundleRunEmbeddingPositionsI32.lastIndex)
    }
    if (orderedBundleRunEmbeddingPositionsI32.size != halfEdgeIndicesI32.size) {
        throw IllegalStateException("path-f32-projection-collapse")
    }
    val seenEmbeddingPositionsI32 = BooleanArray(halfEdgeIndicesI32.size)
    orderedBundleRunEmbeddingPositionsI32.forEach { embeddingPositionI32 ->
        if (embeddingPositionI32 !in halfEdgeIndicesI32.indices || seenEmbeddingPositionsI32[embeddingPositionI32]) {
            throw IllegalStateException("path-f32-projection-collapse")
        }
        seenEmbeddingPositionsI32[embeddingPositionI32] = true
    }
    val embeddingStartI32 = orderedBundleRunEmbeddingPositionsI32.first()
    orderedBundleRunEmbeddingPositionsI32.forEachIndexed { sourcePositionI32, embeddingPositionI32 ->
        val expectedEmbeddingIndexI32 = (embeddingStartI32 + sourcePositionI32) % halfEdgeIndicesI32.size
        if (embeddingPositionI32 != expectedEmbeddingIndexI32) {
            throw IllegalStateException("path-f32-projection-collapse")
        }
    }
}

private fun sameHybridOutgoingRayF64F32(
    firstF64F32: PathMutableHybridHalfEdgeF64F32,
    secondF64F32: PathMutableHybridHalfEdgeF64F32,
): Boolean = sameHybridDirectionsF64F32(
    firstF64F32.embeddingDirectionF64,
    secondF64F32.embeddingDirectionF64,
)

private fun rejectAdjacentHybridOutgoingRaysF64F32(
    halfEdgeIndicesI32: List<Int>,
    halfEdgesF64F32: List<PathMutableHybridHalfEdgeF64F32>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
) {
    val adjacentPairCountI64 = (halfEdgeIndicesI32.size - 1).coerceAtLeast(0).toLong()
    // `zipWithNext` would allocate pairs after the source sweep.  Reserve two list visits, the
    // exact ray predicate, and its branch for every adjacent pair before the first access.
    preflightArrangementF64F32(
        checkedPathWorkMultiplyI64(adjacentPairCountI64, 4L),
        candidateWorkBudgetI32,
    )
    var secondPositionI32 = 1
    while (secondPositionI32 < halfEdgeIndicesI32.size) {
        val firstI32 = halfEdgeIndicesI32[secondPositionI32 - 1]
        val secondI32 = halfEdgeIndicesI32[secondPositionI32]
        if (sameHybridOutgoingRayF64F32(halfEdgesF64F32[firstI32], halfEdgesF64F32[secondI32])) {
            throw IllegalStateException("path-f32-projection-collapse")
        }
        secondPositionI32 += 1
    }
}

private fun rejectAdjacentHybridOutputRaysF64F32(
    halfEdgeIdsI32: List<Int>,
    traceSpanByHalfEdgeI32: Map<Int, PathArrangementTraceSpanF64F32>,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
) {
    val adjacentPairCountI64 = (halfEdgeIdsI32.size - 1).coerceAtLeast(0).toLong()
    // This is the extraction analogue of the angular adjacency check above: no pair allocation
    // or exact predicate may begin after a prior validation without a local debit.
    preflightArrangementF64F32(
        checkedPathWorkMultiplyI64(adjacentPairCountI64, 4L),
        candidateWorkBudgetI32,
    )
    var secondPositionI32 = 1
    while (secondPositionI32 < halfEdgeIdsI32.size) {
        val firstI32 = halfEdgeIdsI32[secondPositionI32 - 1]
        val secondI32 = halfEdgeIdsI32[secondPositionI32]
        if (sameHybridOutputRayF64F32(firstI32, secondI32, traceSpanByHalfEdgeI32)) {
            throw IllegalStateException("path-f32-projection-collapse")
        }
        secondPositionI32 += 1
    }
}

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
