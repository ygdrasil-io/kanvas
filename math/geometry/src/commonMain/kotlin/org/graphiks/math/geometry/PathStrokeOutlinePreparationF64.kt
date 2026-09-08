package org.graphiks.math.geometry

import org.graphiks.math.vector.Vector2F64
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Immutable parametric boundary evaluator retained by F64 stroke preparation. */
public sealed interface PathStrokeOutlinePrimitiveF64 {
    public fun pointAtF64(parameterF64: Double): Point2F64

    public fun derivativeAtF64(parameterF64: Double): Vector2F64
}

/** Conservative finite bounds for one retained outline interval. */
public data class PathStrokeBoundsF64(
    public val leftF64: Double,
    public val topF64: Double,
    public val rightF64: Double,
    public val bottomF64: Double,
)

/** One immutable, certified parameter interval of a stroked outline. */
public data class PathStrokeOutlineIntervalF64(
    public val primitiveF64: PathStrokeOutlinePrimitiveF64,
    public val startParameterF64: Double,
    public val endParameterF64: Double,
    public val boundsF64: PathStrokeBoundsF64,
    public val sourceSagittaUpperBoundF64: Double,
)

/** Result of projecting a source point for a device-space hairline. */
public sealed interface PathStrokeProjectionPointResultF64 {
    public data class Ready(public val pointF64: Point2F64) : PathStrokeProjectionPointResultF64

    public data object NonFinite : PathStrokeProjectionPointResultF64
}

/** Conservative result of certifying one projected outline interval. */
public sealed interface PathStrokeProjectionIntervalResultF64 {
    public data class Bounded(
        public val maximumDeviceSagittaUpperBoundF64: Double,
    ) : PathStrokeProjectionIntervalResultF64

    public data object HorizonCrossing : PathStrokeProjectionIntervalResultF64

    public data object NonFinite : PathStrokeProjectionIntervalResultF64

    public data object Unbounded : PathStrokeProjectionIntervalResultF64
}

/** Explicit projection authority required to construct a device-space hairline. */
public interface PathStrokeProjectionF64 {
    public fun projectPointF64(pointF64: Point2F64): PathStrokeProjectionPointResultF64

    public fun certifyOutlineIntervalF64(
        intervalF64: PathStrokeOutlineIntervalF64,
    ): PathStrokeProjectionIntervalResultF64
}

/** Immutable snapshot of closed outline contours. */
public class PathStrokeOutlineF64 private constructor(
    closedContoursF64: List<List<PathStrokeOutlineIntervalF64>>,
) {
    private val closedContoursSnapshotF64: List<List<PathStrokeOutlineIntervalF64>> =
        closedContoursF64.map { contourF64 -> contourF64.toList() }

    public val contourCountI32: Int
        get() = closedContoursSnapshotF64.size

    public fun copyContourIntervalsF64(indexI32: Int): List<PathStrokeOutlineIntervalF64> =
        closedContoursSnapshotF64[indexI32].toList()

    internal companion object {
        internal fun of(
            closedContoursF64: List<List<PathStrokeOutlineIntervalF64>>,
        ): PathStrokeOutlineF64 = PathStrokeOutlineF64(closedContoursF64)
    }
}

/** Outcome of finite or device-space hairline outline preparation. */
public sealed interface PathStrokeOutlinePreparationResult {
    public data class Ready(
        public val outlineF64: PathStrokeOutlineF64,
        public val pathWorkUsageI64: PathStrokeWorkUsageI64,
        public val frameWorkUsageAfterI64: PathStrokeWorkUsageI64,
    ) : PathStrokeOutlinePreparationResult

    public data class Empty(
        public val pathWorkUsageI64: PathStrokeWorkUsageI64,
        public val frameWorkUsageAfterI64: PathStrokeWorkUsageI64,
    ) : PathStrokeOutlinePreparationResult

    public data class InvalidScene(public val reason: PathStrokeInvalidSceneReason) : PathStrokeOutlinePreparationResult

    public data class ResourceLimitExceeded(public val reason: PathStrokeResourceLimitReason) :
        PathStrokeOutlinePreparationResult
}

/**
 * Expands a finite-width source centerline without flattening it before the offset.
 *
 * The emitted primitives evaluate the centerline and its normal at their retained source
 * parameter.  Consumers can therefore make their own later tessellation decision.
 */
public fun prepareFinitePathStrokeOutlineF64(
    centerlineF64: PathStrokeCenterlineF64,
    styleF64: PathStrokeStyleF64,
    policyF64: PathStrokePolicyF64 = PathStrokePolicyF64(),
    frameWorkUsageBeforeI64: PathStrokeWorkUsageI64 = PathStrokeWorkUsageI64(),
): PathStrokeOutlinePreparationResult {
    val halfWidthF64 = (styleF64.widthF64 as? PathStrokeWidthF64.Finite)?.valueF64
        ?: return PathStrokeOutlinePreparationResult.InvalidScene(PathStrokeInvalidSceneReason.InvalidStyle)
    if (halfWidthF64 == 0.0) {
        return PathStrokeOutlinePreparationResult.Empty(PathStrokeWorkUsageI64(), frameWorkUsageBeforeI64)
    }

    return prepareOutlineResultF64(policyF64, frameWorkUsageBeforeI64) { ledgerI64 ->
        prepareFinitePathStrokeOutlineF64(centerlineF64, styleF64, policyF64, ledgerI64)
    }
}

/**
 * Projects a hairline centerline first, then expands it by one device pixel in device space.
 *
 * Projection refusal preserves a horizon crossing independently from a non-finite projection.
 */
public fun prepareProjectedHairlineOutlineF64(
    centerlineF64: PathStrokeCenterlineF64,
    styleF64: PathStrokeStyleF64,
    projectionF64: PathStrokeProjectionF64,
    policyF64: PathStrokePolicyF64 = PathStrokePolicyF64(),
    frameWorkUsageBeforeI64: PathStrokeWorkUsageI64 = PathStrokeWorkUsageI64(),
): PathStrokeOutlinePreparationResult {
    if (styleF64.widthF64 !is PathStrokeWidthF64.Hairline) {
        return PathStrokeOutlinePreparationResult.InvalidScene(PathStrokeInvalidSceneReason.InvalidStyle)
    }

    return prepareOutlineResultF64(policyF64, frameWorkUsageBeforeI64) { ledgerI64 ->
        prepareProjectedHairlineOutlineF64(centerlineF64, styleF64, projectionF64, policyF64, ledgerI64)
    }
}

/**
 * Internal stage overload used by dash and finalization while preserving one transactional ledger.
 *
 * This stage materializes certified device-space line primitives. No primitive reachable from a
 * ready outline retains the projection authority, which may be mutable or fallible.
 */
public fun prepareProjectedHairlineOutlineF64(
    centerlineF64: PathStrokeCenterlineF64,
    styleF64: PathStrokeStyleF64,
    projectionF64: PathStrokeProjectionF64,
    policyF64: PathStrokePolicyF64,
    ledgerI64: PathStrokeWorkLedgerI64,
): PathStrokeOutlineF64? {
    if (styleF64.widthF64 !is PathStrokeWidthF64.Hairline) throw PathStrokeOutlineInvalidAbort()
    val deviceCenterlineF64 = materializeProjectedHairlineCenterlineF64(
        centerlineF64,
        projectionF64,
        policyF64,
        ledgerI64,
    ) ?: return null
    ledgerI64.debitTopologyBeforeEmissionI64(1L)
    val deviceStyleF64 = styleF64.copy(widthF64 = PathStrokeWidthF64.Finite(1.0))
    return prepareFinitePathStrokeOutlineF64(deviceCenterlineF64, deviceStyleF64, policyF64, ledgerI64)
}

/** Internal stage overload used by later stroke preparation stages sharing one ledger. */
public fun prepareFinitePathStrokeOutlineF64(
    centerlineF64: PathStrokeCenterlineF64,
    styleF64: PathStrokeStyleF64,
    policyF64: PathStrokePolicyF64,
    ledgerI64: PathStrokeWorkLedgerI64,
): PathStrokeOutlineF64? {
    val halfWidthF64 = (styleF64.widthF64 as? PathStrokeWidthF64.Finite)?.valueF64
        ?: throw PathStrokeOutlineInvalidAbort()
    if (halfWidthF64 == 0.0) return null
    return PathStrokeOutlinePreparerF64(
        centerlineF64 = centerlineF64,
        styleF64 = styleF64,
        halfWidthF64 = halfWidthF64 * 0.5,
        ledgerI64 = ledgerI64,
    ).prepare()
}

private inline fun prepareOutlineResultF64(
    policyF64: PathStrokePolicyF64,
    frameWorkUsageBeforeI64: PathStrokeWorkUsageI64,
    prepareF64: (PathStrokeWorkLedgerI64) -> PathStrokeOutlineF64?,
): PathStrokeOutlinePreparationResult = try {
    val ledgerI64 = PathStrokeWorkLedgerI64(
        pathWorkUsageBeforeI64 = PathStrokeWorkUsageI64(),
        frameWorkUsageBeforeI64 = frameWorkUsageBeforeI64,
        limitsI32 = policyF64.limitsI32,
        limitsI64 = policyF64.limitsI64,
    )
    val outlineF64 = prepareF64(ledgerI64)
    val pathWorkUsageI64 = ledgerI64.snapshotPathUsageI64()
    val frameWorkUsageAfterI64 = ledgerI64.snapshotFrameUsageAfterI64()
    if (outlineF64 == null) {
        PathStrokeOutlinePreparationResult.Empty(pathWorkUsageI64, frameWorkUsageAfterI64)
    } else {
        PathStrokeOutlinePreparationResult.Ready(outlineF64, pathWorkUsageI64, frameWorkUsageAfterI64)
    }
} catch (_: PathStrokeOutlineInvalidAbort) {
    PathStrokeOutlinePreparationResult.InvalidScene(PathStrokeInvalidSceneReason.NonFiniteInput)
} catch (abort: PathStrokeProjectionAbort) {
    PathStrokeOutlinePreparationResult.InvalidScene(abort.reason)
} catch (abort: PathStrokeResourceLimitAbort) {
    PathStrokeOutlinePreparationResult.ResourceLimitExceeded(abort.reason)
}

public class PathStrokeOutlineInvalidAbort : RuntimeException()

public class PathStrokeProjectionAbort(
    val reason: PathStrokeInvalidSceneReason = PathStrokeInvalidSceneReason.NonFiniteInput,
) : RuntimeException()

private data class StrokeOutlinePieceF64(
    val primitiveF64: PathStrokePrimitiveF64,
    val startParameterF64: Double,
    val endParameterF64: Double,
    val startPointF64: Point2F64,
    val endPointF64: Point2F64,
    val startTangentF64: Vector2F64,
    val endTangentF64: Vector2F64,
)

private data class OutlineEmissionF64(
    val primitiveF64: PathStrokeOutlinePrimitiveF64,
    val startParameterF64: Double,
    val endParameterF64: Double,
    val boundsF64: PathStrokeBoundsF64,
    val sourceSagittaUpperBoundF64: Double,
)

private class PathStrokeOutlinePreparerF64(
    private val centerlineF64: PathStrokeCenterlineF64,
    private val styleF64: PathStrokeStyleF64,
    private val halfWidthF64: Double,
    private val ledgerI64: PathStrokeWorkLedgerI64,
) {
    private val effectiveRadiusF64 = halfWidthF64 * max(1.0, styleF64.miterLimitF64)

    fun prepare(): PathStrokeOutlineF64? {
        ledgerI64.debitBeforeEmissionI64(PathStrokeWorkUsageI64(snapshotByteCountI64 = 16L))
        val contoursF64 = mutableListOf<List<PathStrokeOutlineIntervalF64>>()
        repeat(centerlineF64.contourCountI32) { contourIndexI32 ->
            ledgerI64.debitTopologyBeforeEmissionI64(1L)
            val piecesF64 = splitContourAtCuspsF64(centerlineF64.contourSpansViewF64(contourIndexI32))
            if (piecesF64.isEmpty()) return@repeat
            if (centerlineF64.isContourClosed(contourIndexI32)) {
                ledgerI64.debitBeforeEmissionI64(PathStrokeWorkUsageI64(snapshotByteCountI64 = 32L))
                contoursF64 += buildClosedContoursF64(piecesF64)
            } else {
                ledgerI64.debitBeforeEmissionI64(PathStrokeWorkUsageI64(snapshotByteCountI64 = 16L))
                contoursF64 += buildOpenContourF64(piecesF64)
            }
        }
        if (contoursF64.isEmpty()) return null
        ledgerI64.debitBeforeEmissionI64(
            PathStrokeWorkUsageI64(snapshotByteCountI64 = contoursF64.size.toLong() * 16L),
        )
        return PathStrokeOutlineF64.of(contoursF64)
    }

    private fun splitContourAtCuspsF64(spansF64: List<PathStrokePrimitiveSpanF64>): List<StrokeOutlinePieceF64> {
        ledgerI64.debitBeforeEmissionI64(PathStrokeWorkUsageI64(snapshotByteCountI64 = 16L))
        val piecesF64 = mutableListOf<StrokeOutlinePieceF64>()
        spansF64.forEach { spanF64 ->
            ledgerI64.debitTopologyBeforeEmissionI64(1L)
            val parametersF64 = splitParametersF64(spanF64.primitiveF64, spanF64.startParameterF64, spanF64.endParameterF64)
            for (indexI32 in 0 until parametersF64.lastIndex) {
                val startParameterF64 = parametersF64[indexI32]
                val endParameterF64 = parametersF64[indexI32 + 1]
                if (startParameterF64 >= endParameterF64) continue
                ledgerI64.debitBeforeEmissionI64(PathStrokeWorkUsageI64(snapshotByteCountI64 = 64L))
                val startPointF64 = finiteSourcePointF64(spanF64.primitiveF64, startParameterF64)
                val endPointF64 = finiteSourcePointF64(spanF64.primitiveF64, endParameterF64)
                val startTangentF64 = oneSidedTangentF64(
                    spanF64.primitiveF64,
                    startParameterF64,
                    endParameterF64,
                    forward = true,
                ) ?: continue
                val endTangentF64 = oneSidedTangentF64(
                    spanF64.primitiveF64,
                    startParameterF64,
                    endParameterF64,
                    forward = false,
                ) ?: continue
                piecesF64 += StrokeOutlinePieceF64(
                    primitiveF64 = spanF64.primitiveF64,
                    startParameterF64 = startParameterF64,
                    endParameterF64 = endParameterF64,
                    startPointF64 = startPointF64,
                    endPointF64 = endPointF64,
                    startTangentF64 = startTangentF64,
                    endTangentF64 = endTangentF64,
                )
            }
        }
        return piecesF64
    }

    private fun buildOpenContourF64(piecesF64: List<StrokeOutlinePieceF64>): List<PathStrokeOutlineIntervalF64> {
        val contourF64 = mutableListOf<PathStrokeOutlineIntervalF64>()
        piecesF64.forEachIndexed { indexI32, pieceF64 ->
            appendOffsetF64(contourF64, pieceF64, sideF64 = 1.0, reversed = false)
            if (indexI32 < piecesF64.lastIndex) {
                appendJoinF64(contourF64, pieceF64, piecesF64[indexI32 + 1], sideF64 = 1.0, reversed = false)
            }
        }
        val lastPieceF64 = piecesF64.last()
        appendEndCapF64(contourF64, lastPieceF64)
        for (indexI32 in piecesF64.lastIndex downTo 0) {
            appendOffsetF64(contourF64, piecesF64[indexI32], sideF64 = -1.0, reversed = true)
            if (indexI32 > 0) {
                appendJoinF64(contourF64, piecesF64[indexI32 - 1], piecesF64[indexI32], sideF64 = -1.0, reversed = true)
            }
        }
        appendStartCapF64(contourF64, piecesF64.first())
        return contourF64
    }

    private fun buildClosedContoursF64(piecesF64: List<StrokeOutlinePieceF64>): List<List<PathStrokeOutlineIntervalF64>> = listOf(
        buildClosedBoundaryF64(piecesF64, sideF64 = 1.0, reversed = false),
        buildClosedBoundaryF64(piecesF64, sideF64 = -1.0, reversed = true),
    )

    private fun buildClosedBoundaryF64(
        piecesF64: List<StrokeOutlinePieceF64>,
        sideF64: Double,
        reversed: Boolean,
    ): List<PathStrokeOutlineIntervalF64> {
        val contourF64 = mutableListOf<PathStrokeOutlineIntervalF64>()
        if (!reversed) {
            piecesF64.forEachIndexed { indexI32, pieceF64 ->
                appendOffsetF64(contourF64, pieceF64, sideF64, reversed = false)
                appendJoinF64(
                    contourF64,
                    pieceF64,
                    piecesF64[(indexI32 + 1) % piecesF64.size],
                    sideF64,
                    reversed = false,
                )
            }
        } else {
            for (indexI32 in piecesF64.lastIndex downTo 0) {
                appendOffsetF64(contourF64, piecesF64[indexI32], sideF64, reversed = true)
                val previousIndexI32 = if (indexI32 == 0) piecesF64.lastIndex else indexI32 - 1
                appendJoinF64(
                    contourF64,
                    piecesF64[previousIndexI32],
                    piecesF64[indexI32],
                    sideF64,
                    reversed = true,
                )
            }
        }
        return contourF64
    }

    private fun appendOffsetF64(
        destinationF64: MutableList<PathStrokeOutlineIntervalF64>,
        pieceF64: StrokeOutlinePieceF64,
        sideF64: Double,
        reversed: Boolean,
    ) {
        ledgerI64.debitBeforeEmissionI64(
            PathStrokeWorkUsageI64(attemptedGeometryUnitCountI64 = 1L, snapshotByteCountI64 = 64L),
        )
        val primitiveF64 = OffsetStrokeOutlinePrimitiveF64(pieceF64, sideF64, halfWidthF64)
        val boundsF64 = expandedBoundsF64(sourceBoundsF64(pieceF64.primitiveF64), halfWidthF64)
        val emissionF64 = OutlineEmissionF64(
            primitiveF64 = primitiveF64,
            startParameterF64 = pieceF64.startParameterF64,
            endParameterF64 = pieceF64.endParameterF64,
            boundsF64 = boundsF64,
            sourceSagittaUpperBoundF64 = outlineSagittaUpperBoundF64(pieceF64.primitiveF64),
        )
        appendEmissionF64(destinationF64, emissionF64, reversed)
    }

    private fun appendJoinF64(
        destinationF64: MutableList<PathStrokeOutlineIntervalF64>,
        previousPieceF64: StrokeOutlinePieceF64,
        nextPieceF64: StrokeOutlinePieceF64,
        sideF64: Double,
        reversed: Boolean,
    ) {
        ledgerI64.debitTopologyBeforeEmissionI64(1L)
        val emissionsF64 = joinEmissionsF64(previousPieceF64, nextPieceF64, sideF64)
        if (reversed) {
            emissionsF64.asReversed().forEach { emissionF64 -> appendEmissionF64(destinationF64, emissionF64, reversed = true) }
        } else {
            emissionsF64.forEach { emissionF64 -> appendEmissionF64(destinationF64, emissionF64, reversed = false) }
        }
    }

    private fun joinEmissionsF64(
        previousPieceF64: StrokeOutlinePieceF64,
        nextPieceF64: StrokeOutlinePieceF64,
        sideF64: Double,
    ): List<OutlineEmissionF64> {
        val vertexF64 = previousPieceF64.endPointF64
        val nextVertexF64 = nextPieceF64.startPointF64
        if (!sameOutlinePointF64(vertexF64, nextVertexF64)) {
            return listOf(lineEmissionF64(offsetEndF64(previousPieceF64, sideF64), offsetStartF64(nextPieceF64, sideF64)))
        }
        val fromF64 = offsetEndF64(previousPieceF64, sideF64)
        val toF64 = offsetStartF64(nextPieceF64, sideF64)
        if (sameOutlinePointF64(fromF64, toF64)) return emptyList()

        val tangentDotF64 = previousPieceF64.endTangentF64.dot(nextPieceF64.startTangentF64)
        val tangentCrossF64 = previousPieceF64.endTangentF64.cross(nextPieceF64.startTangentF64)
        if (tangentDotF64 < -strokeOutlineEpsilonF64 && abs(tangentCrossF64) <= strokeOutlineEpsilonF64) {
            return when (styleF64.join) {
                PathStrokeJoin.Round -> listOf(roundCapEmissionF64(vertexF64, fromF64, toF64))
                PathStrokeJoin.Miter,
                PathStrokeJoin.Bevel,
                -> listOf(lineEmissionF64(fromF64, toF64))
            }
        }

        val intersectionF64 = offsetLineIntersectionF64(
            fromF64,
            previousPieceF64.endTangentF64,
            toF64,
            nextPieceF64.startTangentF64,
        )
        val outerF64 = tangentCrossF64 * sideF64 < -strokeOutlineEpsilonF64
        if (!outerF64 && intersectionF64 != null) return listOf(lineEmissionF64(fromF64, intersectionF64), lineEmissionF64(intersectionF64, toF64))
        if (!outerF64) return listOf(lineEmissionF64(fromF64, toF64))

        val resultF64: List<OutlineEmissionF64> = when (styleF64.join) {
            PathStrokeJoin.Bevel -> listOf(lineEmissionF64(fromF64, toF64))
            PathStrokeJoin.Miter -> {
                val miterDistanceF64 = intersectionF64?.distanceTo(vertexF64)?.div(halfWidthF64)
                if (intersectionF64 != null && miterDistanceF64 != null && miterDistanceF64.isFinite() &&
                    styleF64.miterLimitF64 >= 1.0 && miterDistanceF64 <= styleF64.miterLimitF64
                ) {
                    listOf(lineEmissionF64(fromF64, intersectionF64), lineEmissionF64(intersectionF64, toF64))
                } else {
                    listOf(lineEmissionF64(fromF64, toF64))
                }
            }

            PathStrokeJoin.Round -> listOf(
                roundEmissionF64(vertexF64, fromF64, toF64) ?: lineEmissionF64(fromF64, toF64),
            )
        }
        return resultF64
    }

    private fun appendEndCapF64(destinationF64: MutableList<PathStrokeOutlineIntervalF64>, pieceF64: StrokeOutlinePieceF64) {
        ledgerI64.debitTopologyBeforeEmissionI64(1L)
        val leftF64 = offsetEndF64(pieceF64, 1.0)
        val rightF64 = offsetEndF64(pieceF64, -1.0)
        val emissionsF64 = when (styleF64.cap) {
            PathStrokeCap.Butt -> listOf(lineEmissionF64(leftF64, rightF64))
            PathStrokeCap.Round -> listOf(roundCapEmissionF64(pieceF64.endPointF64, leftF64, rightF64))
            PathStrokeCap.Square -> squareCapEmissionsF64(leftF64, rightF64, pieceF64.endTangentF64, forwardF64 = true)
        }
        emissionsF64.forEach { emissionF64 -> appendEmissionF64(destinationF64, emissionF64, reversed = false) }
    }

    private fun appendStartCapF64(destinationF64: MutableList<PathStrokeOutlineIntervalF64>, pieceF64: StrokeOutlinePieceF64) {
        ledgerI64.debitTopologyBeforeEmissionI64(1L)
        val rightF64 = offsetStartF64(pieceF64, -1.0)
        val leftF64 = offsetStartF64(pieceF64, 1.0)
        val emissionsF64 = when (styleF64.cap) {
            PathStrokeCap.Butt -> listOf(lineEmissionF64(rightF64, leftF64))
            PathStrokeCap.Round -> listOf(roundCapEmissionF64(pieceF64.startPointF64, rightF64, leftF64))
            PathStrokeCap.Square -> squareCapEmissionsF64(rightF64, leftF64, pieceF64.startTangentF64, forwardF64 = false)
        }
        emissionsF64.forEach { emissionF64 -> appendEmissionF64(destinationF64, emissionF64, reversed = false) }
    }

    private fun squareCapEmissionsF64(
        firstF64: Point2F64,
        secondF64: Point2F64,
        tangentF64: Vector2F64,
        forwardF64: Boolean,
    ): List<OutlineEmissionF64> {
        val directionF64 = if (forwardF64) tangentF64 else -tangentF64
        val deltaF64 = directionF64 * halfWidthF64
        val firstExtendedF64 = firstF64 + deltaF64
        val secondExtendedF64 = secondF64 + deltaF64
        return listOf(
            lineEmissionF64(firstF64, firstExtendedF64),
            lineEmissionF64(firstExtendedF64, secondExtendedF64),
            lineEmissionF64(secondExtendedF64, secondF64),
        )
    }

    private fun appendEmissionF64(
        destinationF64: MutableList<PathStrokeOutlineIntervalF64>,
        emissionF64: OutlineEmissionF64,
        reversed: Boolean,
    ) {
        ledgerI64.debitBeforeEmissionI64(
            PathStrokeWorkUsageI64(attemptedGeometryUnitCountI64 = 1L, snapshotByteCountI64 = 64L),
        )
        destinationF64 += PathStrokeOutlineIntervalF64(
            primitiveF64 = if (reversed) {
                ReversedStrokeOutlinePrimitiveF64(
                    emissionF64.primitiveF64,
                    emissionF64.startParameterF64,
                    emissionF64.endParameterF64,
                )
            } else {
                emissionF64.primitiveF64
            },
            startParameterF64 = emissionF64.startParameterF64,
            endParameterF64 = emissionF64.endParameterF64,
            boundsF64 = emissionF64.boundsF64,
            sourceSagittaUpperBoundF64 = emissionF64.sourceSagittaUpperBoundF64,
        )
    }

    private fun offsetStartF64(pieceF64: StrokeOutlinePieceF64, sideF64: Double): Point2F64 =
        pieceF64.startPointF64 + leftNormalF64(pieceF64.startTangentF64) * (sideF64 * halfWidthF64)

    private fun offsetEndF64(pieceF64: StrokeOutlinePieceF64, sideF64: Double): Point2F64 =
        pieceF64.endPointF64 + leftNormalF64(pieceF64.endTangentF64) * (sideF64 * halfWidthF64)

    private fun outlineSagittaUpperBoundF64(primitiveF64: PathStrokePrimitiveF64): Double {
        val centerlineSagittaUpperBoundF64 = centerlineSagittaUpperBoundF64(primitiveF64)
        val normalChordDeviationUpperBoundF64 = if (primitiveF64 is PathStrokeLinePrimitiveF64) 0.0 else 2.0
        val resultF64 = centerlineSagittaUpperBoundF64 + effectiveRadiusF64 * normalChordDeviationUpperBoundF64
        if (!resultF64.isFinite() || resultF64 < 0.0) throw PathStrokeOutlineInvalidAbort()
        return resultF64
    }
}

private class OffsetStrokeOutlinePrimitiveF64(
    val pieceF64: StrokeOutlinePieceF64,
    val sideF64: Double,
    val halfWidthF64: Double,
) : PathStrokeOutlinePrimitiveF64 {
    override fun pointAtF64(parameterF64: Double): Point2F64 {
        val sourceF64 = finiteSourcePointF64(pieceF64.primitiveF64, parameterF64)
        val tangentF64 = pieceF64.primitiveF64.derivativeAtF64(parameterF64)
        val normalF64 = if (isUsableTangentF64(tangentF64)) {
            leftNormalF64(tangentF64)
        } else if (abs(parameterF64 - pieceF64.startParameterF64) <= abs(parameterF64 - pieceF64.endParameterF64)) {
            leftNormalF64(pieceF64.startTangentF64)
        } else {
            leftNormalF64(pieceF64.endTangentF64)
        }
        val pointF64 = sourceF64 + normalF64 * (sideF64 * halfWidthF64)
        if (!pointF64.isFinite()) throw PathStrokeOutlineInvalidAbort()
        return pointF64
    }

    override fun derivativeAtF64(parameterF64: Double): Vector2F64 {
        val spanF64 = pieceF64.endParameterF64 - pieceF64.startParameterF64
        if (spanF64 <= 0.0) return Vector2F64.Zero
        val stepF64 = min(spanF64 * 1e-5, 1e-5)
        val lowerF64 = max(pieceF64.startParameterF64, parameterF64 - stepF64)
        val upperF64 = min(pieceF64.endParameterF64, parameterF64 + stepF64)
        if (upperF64 <= lowerF64) return Vector2F64.Zero
        return (pointAtF64(upperF64) - pointAtF64(lowerF64)) / (upperF64 - lowerF64)
    }
}

private class LineStrokeOutlinePrimitiveF64(
    val startF64: Point2F64,
    val endF64: Point2F64,
) : PathStrokeOutlinePrimitiveF64 {
    override fun pointAtF64(parameterF64: Double): Point2F64 = Point2F64(
        interpolatedOutlineCoordinateF64(startF64.x, endF64.x, parameterF64),
        interpolatedOutlineCoordinateF64(startF64.y, endF64.y, parameterF64),
    )

    override fun derivativeAtF64(parameterF64: Double): Vector2F64 = endF64 - startF64
}

private class ReversedStrokeOutlinePrimitiveF64(
    val sourceF64: PathStrokeOutlinePrimitiveF64,
    val startParameterF64: Double,
    val endParameterF64: Double,
) : PathStrokeOutlinePrimitiveF64 {
    val parameterSumF64 = startParameterF64 + endParameterF64

    override fun pointAtF64(parameterF64: Double): Point2F64 = sourceF64.pointAtF64(parameterSumF64 - parameterF64)

    override fun derivativeAtF64(parameterF64: Double): Vector2F64 = -sourceF64.derivativeAtF64(parameterSumF64 - parameterF64)
}

private class ArcStrokeOutlinePrimitiveF64(
    val centerF64: Point2F64,
    val radiusF64: Double,
    val startAngleF64: Double,
    val sweepAngleF64: Double,
) : PathStrokeOutlinePrimitiveF64 {
    override fun pointAtF64(parameterF64: Double): Point2F64 {
        val angleF64 = startAngleF64 + sweepAngleF64 * parameterF64
        return Point2F64(
            centerF64.x + radiusF64 * cos(angleF64),
            centerF64.y + radiusF64 * sin(angleF64),
        )
    }

    override fun derivativeAtF64(parameterF64: Double): Vector2F64 {
        val angleF64 = startAngleF64 + sweepAngleF64 * parameterF64
        return Vector2F64(
            -radiusF64 * sin(angleF64) * sweepAngleF64,
            radiusF64 * cos(angleF64) * sweepAngleF64,
        )
    }
}

private class SourceStrokeOutlinePrimitiveF64(
    val sourceF64: PathStrokePrimitiveF64,
) : PathStrokeOutlinePrimitiveF64 {
    override fun pointAtF64(parameterF64: Double): Point2F64 = finiteSourcePointF64(sourceF64, parameterF64)

    override fun derivativeAtF64(parameterF64: Double): Vector2F64 = sourceF64.derivativeAtF64(parameterF64)
}

private data class SourceStrokeIntervalGeometryF64(
    val boundsF64: PathStrokeBoundsF64,
    val sagittaUpperBoundF64: Double,
)

/** Internal finalization authority for one analytically restricted stroke-outline interval. */
internal sealed interface PathStrokeOutlineRestrictionResultF64 {
    public data class Ready(
        public val intervalF64: PathStrokeOutlineIntervalF64,
    ) : PathStrokeOutlineRestrictionResultF64

    /** The source tangent becomes singular on this interval; recursively bisect it. */
    public data object NeedsSubdivision : PathStrokeOutlineRestrictionResultF64
}

/**
 * Recomputes the source certificate for the exact retained parameter range.
 *
 * Offset intervals restrict their source Bezier/arc analytically and bound unit-normal variation
 * using derivative control hulls.  The bound shrinks with each non-singular restriction; a cusp
 * is explicitly returned for subdivision rather than hidden by a constant whole-curve bound.
 */
internal fun restrictPathStrokeOutlineIntervalF64(
    intervalF64: PathStrokeOutlineIntervalF64,
    startParameterF64: Double,
    endParameterF64: Double,
): PathStrokeOutlineRestrictionResultF64 {
    if (!startParameterF64.isFinite() || !endParameterF64.isFinite() ||
        startParameterF64 < intervalF64.startParameterF64 ||
        endParameterF64 > intervalF64.endParameterF64 ||
        startParameterF64 >= endParameterF64
    ) throw PathStrokeOutlineInvalidAbort()

    val restrictedGeometryF64 = restrictedOutlineGeometryF64(
        primitiveF64 = intervalF64.primitiveF64,
        startParameterF64 = startParameterF64,
        endParameterF64 = endParameterF64,
    ) ?: return PathStrokeOutlineRestrictionResultF64.NeedsSubdivision
    return PathStrokeOutlineRestrictionResultF64.Ready(
        intervalF64.copy(
            startParameterF64 = startParameterF64,
            endParameterF64 = endParameterF64,
            boundsF64 = restrictedGeometryF64.boundsF64,
            sourceSagittaUpperBoundF64 = restrictedGeometryF64.sagittaUpperBoundF64,
        ),
    )
}

private data class RestrictedOutlineGeometryF64(
    val boundsF64: PathStrokeBoundsF64,
    val sagittaUpperBoundF64: Double,
)

private fun restrictedOutlineGeometryF64(
    primitiveF64: PathStrokeOutlinePrimitiveF64,
    startParameterF64: Double,
    endParameterF64: Double,
): RestrictedOutlineGeometryF64? = when (primitiveF64) {
    is LineStrokeOutlinePrimitiveF64 -> RestrictedOutlineGeometryF64(
        boundsF64 = boundsOfPointsF64(
            listOf(
                primitiveF64.pointAtF64(startParameterF64),
                primitiveF64.pointAtF64(endParameterF64),
            ),
        ),
        sagittaUpperBoundF64 = 0.0,
    )

    is ArcStrokeOutlinePrimitiveF64 -> restrictedArcOutlineGeometryF64(
        primitiveF64,
        startParameterF64,
        endParameterF64,
    )

    is SourceStrokeOutlinePrimitiveF64 -> sourceIntervalGeometryF64(
        primitiveF64.sourceF64,
        startParameterF64,
        endParameterF64,
    ).let { sourceGeometryF64 ->
        RestrictedOutlineGeometryF64(
            sourceGeometryF64.boundsF64,
            sourceGeometryF64.sagittaUpperBoundF64,
        )
    }

    is OffsetStrokeOutlinePrimitiveF64 -> restrictedOffsetOutlineGeometryF64(
        primitiveF64,
        startParameterF64,
        endParameterF64,
    )

    is ReversedStrokeOutlinePrimitiveF64 -> {
        val sourceStartParameterF64 = primitiveF64.parameterSumF64 - endParameterF64
        val sourceEndParameterF64 = primitiveF64.parameterSumF64 - startParameterF64
        restrictedOutlineGeometryF64(
            primitiveF64.sourceF64,
            sourceStartParameterF64,
            sourceEndParameterF64,
        )
    }
}

private fun restrictedArcOutlineGeometryF64(
    primitiveF64: ArcStrokeOutlinePrimitiveF64,
    startParameterF64: Double,
    endParameterF64: Double,
): RestrictedOutlineGeometryF64 {
    val startAngleF64 = primitiveF64.startAngleF64 + primitiveF64.sweepAngleF64 * startParameterF64
    val sweepAngleF64 = primitiveF64.sweepAngleF64 * (endParameterF64 - startParameterF64)
    if (!startAngleF64.isFinite() || !sweepAngleF64.isFinite() || primitiveF64.radiusF64 < 0.0) {
        throw PathStrokeOutlineInvalidAbort()
    }
    val pointsF64 = buildList {
        add(primitiveF64.pointAtF64(startParameterF64))
        add(primitiveF64.pointAtF64(endParameterF64))
        listOf(0.0, PI * 0.5, PI, PI * 1.5)
            .filter { angleF64 -> angleIsInOutlineSweepF64(angleF64, startAngleF64, sweepAngleF64) }
            .forEach { angleF64 ->
                add(
                    Point2F64(
                        primitiveF64.centerF64.x + primitiveF64.radiusF64 * cos(angleF64),
                        primitiveF64.centerF64.y + primitiveF64.radiusF64 * sin(angleF64),
                    ),
                )
            }
    }
    val sagittaUpperBoundF64 = primitiveF64.radiusF64 * abs(sweepAngleF64) * abs(sweepAngleF64) * 0.125
    if (!sagittaUpperBoundF64.isFinite()) throw PathStrokeOutlineInvalidAbort()
    return RestrictedOutlineGeometryF64(boundsOfPointsF64(pointsF64), sagittaUpperBoundF64)
}

private fun angleIsInOutlineSweepF64(angleF64: Double, startAngleF64: Double, sweepAngleF64: Double): Boolean {
    val distanceF64 = if (sweepAngleF64 >= 0.0) {
        positiveOutlineAngleF64(angleF64 - startAngleF64)
    } else {
        positiveOutlineAngleF64(startAngleF64 - angleF64)
    }
    return distanceF64 <= abs(sweepAngleF64) + 1e-12
}

private fun positiveOutlineAngleF64(angleF64: Double): Double {
    val resultF64 = angleF64 % (2.0 * PI)
    return if (resultF64 < 0.0) resultF64 + 2.0 * PI else resultF64
}

private fun restrictedOffsetOutlineGeometryF64(
    primitiveF64: OffsetStrokeOutlinePrimitiveF64,
    startParameterF64: Double,
    endParameterF64: Double,
): RestrictedOutlineGeometryF64? {
    val sourcePrimitiveF64 = restrictedSourcePrimitiveF64(
        primitiveF64.pieceF64.primitiveF64,
        startParameterF64,
        endParameterF64,
    )
    val normalDeviationUpperBoundF64 = normalDeviationUpperBoundF64(sourcePrimitiveF64)
        ?: return null
    val sourceSagittaUpperBoundF64 = centerlineSagittaUpperBoundF64(sourcePrimitiveF64)
    val sagittaUpperBoundF64 = sourceSagittaUpperBoundF64 + primitiveF64.halfWidthF64 * normalDeviationUpperBoundF64
    if (!sagittaUpperBoundF64.isFinite() || sagittaUpperBoundF64 < 0.0) throw PathStrokeOutlineInvalidAbort()
    return RestrictedOutlineGeometryF64(
        boundsF64 = expandedBoundsF64(sourceBoundsF64(sourcePrimitiveF64), primitiveF64.halfWidthF64),
        sagittaUpperBoundF64 = sagittaUpperBoundF64,
    )
}

/** Bounds the deviation of a unit-normal curve from its chord on an analytically restricted source. */
private fun normalDeviationUpperBoundF64(primitiveF64: PathStrokePrimitiveF64): Double? {
    return when (primitiveF64) {
        is PathStrokeLinePrimitiveF64 -> return 0.0

        is PathStrokeQuadPrimitiveF64 -> {
            val firstF64 = (primitiveF64.controlF64 - primitiveF64.startF64) * 2.0
            val secondF64 = (primitiveF64.endF64 - primitiveF64.controlF64) * 2.0
            normalDeviationForLinearDerivativeF64(firstF64, secondF64)
        }

        is PathStrokeCubicPrimitiveF64 -> {
            val firstF64 = (primitiveF64.control1F64 - primitiveF64.startF64) * 3.0
            val secondF64 = (primitiveF64.control2F64 - primitiveF64.control1F64) * 3.0
            val thirdF64 = (primitiveF64.endF64 - primitiveF64.control2F64) * 3.0
            normalDeviationForQuadraticDerivativeF64(firstF64, secondF64, thirdF64)
        }

        is PathStrokeSvgArcPrimitiveF64 -> primitiveF64.arcF64?.let { arcF64 ->
            normalDeviationForDerivativeBoundsF64(NormalDerivativeBoundsF64(
                minimumSpeedF64 = min(arcF64.radiusX, arcF64.radiusY) * abs(arcF64.sweepAngle),
                maximumDerivativeF64 = max(arcF64.radiusX, arcF64.radiusY) * arcF64.sweepAngle * arcF64.sweepAngle,
            ))
        } ?: return 0.0
    }
}

/** Factors exact endpoint zeros before bounding the direction of a linear derivative field. */
private fun normalDeviationForLinearDerivativeF64(
    firstF64: Vector2F64,
    secondF64: Vector2F64,
): Double? {
    if (isExactlyZeroVectorF64(firstF64) || isExactlyZeroVectorF64(secondF64)) {
        return if (isExactlyZeroVectorF64(firstF64) && isExactlyZeroVectorF64(secondF64)) null else 0.0
    }
    return normalDeviationForDerivativeBoundsF64(
        NormalDerivativeBoundsF64(
            minimumSpeedF64 = distanceFromOriginToSegmentF64(firstF64, secondF64),
            maximumDerivativeF64 = stableVectorLengthF64(secondF64 - firstF64),
        ),
    )
}

/** Factors t or (1 - t) from a quadratic derivative before evaluating its unit direction. */
private fun normalDeviationForQuadraticDerivativeF64(
    firstF64: Vector2F64,
    secondF64: Vector2F64,
    thirdF64: Vector2F64,
): Double? {
    if (isExactlyZeroVectorF64(firstF64)) {
        return normalDeviationForLinearDerivativeF64(secondF64 * 2.0, thirdF64)
    }
    if (isExactlyZeroVectorF64(thirdF64)) {
        return normalDeviationForLinearDerivativeF64(firstF64, secondF64 * 2.0)
    }
    return normalDeviationForDerivativeBoundsF64(
        NormalDerivativeBoundsF64(
            minimumSpeedF64 = distanceFromOriginToTriangleF64(firstF64, secondF64, thirdF64),
            maximumDerivativeF64 = 2.0 * max(
                stableVectorLengthF64(secondF64 - firstF64),
                stableVectorLengthF64(thirdF64 - secondF64),
            ),
        ),
    )
}

private fun normalDeviationForDerivativeBoundsF64(derivativeBoundsF64: NormalDerivativeBoundsF64): Double? {
    if (!derivativeBoundsF64.minimumSpeedF64.isFinite() || !derivativeBoundsF64.maximumDerivativeF64.isFinite()) {
        throw PathStrokeOutlineInvalidAbort()
    }
    if (derivativeBoundsF64.minimumSpeedF64 == 0.0) return null
    return (derivativeBoundsF64.maximumDerivativeF64 / derivativeBoundsF64.minimumSpeedF64)
        .takeIf(Double::isFinite)
        ?.coerceAtMost(2.0)
}

private fun isExactlyZeroVectorF64(vectorF64: Vector2F64): Boolean =
    vectorF64.x == 0.0 && vectorF64.y == 0.0

private data class NormalDerivativeBoundsF64(
    val minimumSpeedF64: Double,
    val maximumDerivativeF64: Double,
)

private fun distanceFromOriginToSegmentF64(startF64: Vector2F64, endF64: Vector2F64): Double {
    val scaleF64 = max(
        max(abs(startF64.x), abs(startF64.y)),
        max(abs(endF64.x), abs(endF64.y)),
    )
    if (!scaleF64.isFinite()) throw PathStrokeOutlineInvalidAbort()
    if (scaleF64 == 0.0) return 0.0
    val scaledStartF64 = Vector2F64(startF64.x / scaleF64, startF64.y / scaleF64)
    val scaledEndF64 = Vector2F64(endF64.x / scaleF64, endF64.y / scaleF64)
    val scaledDeltaF64 = scaledEndF64 - scaledStartF64
    val scaledLengthSquaredF64 = scaledDeltaF64.lengthSquared()
    if (!scaledLengthSquaredF64.isFinite()) throw PathStrokeOutlineInvalidAbort()
    if (scaledLengthSquaredF64 == 0.0) {
        return if (startF64 == endF64) stableVectorLengthF64(startF64) else 0.0
    }
    val parameterF64 = (-scaledStartF64.dot(scaledDeltaF64) / scaledLengthSquaredF64).coerceIn(0.0, 1.0)
    val resultF64 = stableVectorLengthF64(scaledStartF64 + scaledDeltaF64 * parameterF64) * scaleF64
    if (!resultF64.isFinite()) throw PathStrokeOutlineInvalidAbort()
    return resultF64
}

private fun distanceFromOriginToTriangleF64(
    firstF64: Vector2F64,
    secondF64: Vector2F64,
    thirdF64: Vector2F64,
): Double {
    if (originIsInsideNonDegenerateTriangleF64(firstF64, secondF64, thirdF64)) return 0.0
    return min(
        distanceFromOriginToSegmentF64(firstF64, secondF64),
        min(
            distanceFromOriginToSegmentF64(secondF64, thirdF64),
            distanceFromOriginToSegmentF64(thirdF64, firstF64),
        ),
    )
}

private fun originIsInsideNonDegenerateTriangleF64(
    firstF64: Vector2F64,
    secondF64: Vector2F64,
    thirdF64: Vector2F64,
): Boolean {
    val scaleF64 = max(
        max(abs(firstF64.x), abs(firstF64.y)),
        max(max(abs(secondF64.x), abs(secondF64.y)), max(abs(thirdF64.x), abs(thirdF64.y))),
    )
    if (!scaleF64.isFinite()) throw PathStrokeOutlineInvalidAbort()
    if (scaleF64 == 0.0) return true
    val scaledFirstF64 = Vector2F64(firstF64.x / scaleF64, firstF64.y / scaleF64)
    val scaledSecondF64 = Vector2F64(secondF64.x / scaleF64, secondF64.y / scaleF64)
    val scaledThirdF64 = Vector2F64(thirdF64.x / scaleF64, thirdF64.y / scaleF64)
    val orientationF64 = (scaledSecondF64 - scaledFirstF64).cross(scaledThirdF64 - scaledFirstF64)
    if (!orientationF64.isFinite()) throw PathStrokeOutlineInvalidAbort()
    if (orientationF64 == 0.0) return false
    val firstCrossF64 = scaledFirstF64.cross(scaledSecondF64)
    val secondCrossF64 = scaledSecondF64.cross(scaledThirdF64)
    val thirdCrossF64 = scaledThirdF64.cross(scaledFirstF64)
    if (!firstCrossF64.isFinite() || !secondCrossF64.isFinite() || !thirdCrossF64.isFinite()) {
        throw PathStrokeOutlineInvalidAbort()
    }
    return if (orientationF64 > 0.0) {
        firstCrossF64 >= 0.0 && secondCrossF64 >= 0.0 && thirdCrossF64 >= 0.0
    } else {
        firstCrossF64 <= 0.0 && secondCrossF64 <= 0.0 && thirdCrossF64 <= 0.0
    }
}

private fun stableVectorLengthF64(vectorF64: Vector2F64): Double {
    val scaleF64 = max(abs(vectorF64.x), abs(vectorF64.y))
    if (!scaleF64.isFinite()) throw PathStrokeOutlineInvalidAbort()
    if (scaleF64 == 0.0) return 0.0
    val resultF64 = scaleF64 * sqrt(
        (vectorF64.x / scaleF64) * (vectorF64.x / scaleF64) +
            (vectorF64.y / scaleF64) * (vectorF64.y / scaleF64),
    )
    if (!resultF64.isFinite()) throw PathStrokeOutlineInvalidAbort()
    return resultF64
}

/** Analytically reparameterizes one source interval before deriving its conservative certificate. */
private fun sourceIntervalGeometryF64(
    primitiveF64: PathStrokePrimitiveF64,
    startParameterF64: Double,
    endParameterF64: Double,
): SourceStrokeIntervalGeometryF64 {
    val intervalPrimitiveF64 = restrictedSourcePrimitiveF64(
        primitiveF64,
        startParameterF64,
        endParameterF64,
    )
    return SourceStrokeIntervalGeometryF64(
        boundsF64 = sourceBoundsF64(intervalPrimitiveF64),
        sagittaUpperBoundF64 = centerlineSagittaUpperBoundF64(intervalPrimitiveF64),
    )
}

private fun restrictedSourcePrimitiveF64(
    primitiveF64: PathStrokePrimitiveF64,
    startParameterF64: Double,
    endParameterF64: Double,
): PathStrokePrimitiveF64 = when (primitiveF64) {
        is PathStrokeLinePrimitiveF64 -> PathStrokeLinePrimitiveF64(
            finiteSourcePointF64(primitiveF64, startParameterF64),
            finiteSourcePointF64(primitiveF64, endParameterF64),
        )

        is PathStrokeQuadPrimitiveF64 -> restrictedQuadPrimitiveF64(
            primitiveF64,
            startParameterF64,
            endParameterF64,
        )

        is PathStrokeCubicPrimitiveF64 -> restrictedCubicPrimitiveF64(
            primitiveF64,
            startParameterF64,
            endParameterF64,
        )

        is PathStrokeSvgArcPrimitiveF64 -> restrictedArcPrimitiveF64(
            primitiveF64,
            startParameterF64,
            endParameterF64,
        )
    }

private fun restrictedQuadPrimitiveF64(
    primitiveF64: PathStrokeQuadPrimitiveF64,
    startParameterF64: Double,
    endParameterF64: Double,
): PathStrokeQuadPrimitiveF64 {
    val (_, remainingF64) = splitQuadPrimitiveF64(primitiveF64, startParameterF64)
    val relativeEndParameterF64 = relativeIntervalEndParameterF64(startParameterF64, endParameterF64)
    return splitQuadPrimitiveF64(remainingF64, relativeEndParameterF64).first
}

private fun splitQuadPrimitiveF64(
    primitiveF64: PathStrokeQuadPrimitiveF64,
    parameterF64: Double,
): Pair<PathStrokeQuadPrimitiveF64, PathStrokeQuadPrimitiveF64> {
    val startControlF64 = interpolatedOutlinePointF64(primitiveF64.startF64, primitiveF64.controlF64, parameterF64)
    val controlEndF64 = interpolatedOutlinePointF64(primitiveF64.controlF64, primitiveF64.endF64, parameterF64)
    val splitPointF64 = interpolatedOutlinePointF64(startControlF64, controlEndF64, parameterF64)
    return PathStrokeQuadPrimitiveF64(primitiveF64.startF64, startControlF64, splitPointF64) to
        PathStrokeQuadPrimitiveF64(splitPointF64, controlEndF64, primitiveF64.endF64)
}

private fun restrictedCubicPrimitiveF64(
    primitiveF64: PathStrokeCubicPrimitiveF64,
    startParameterF64: Double,
    endParameterF64: Double,
): PathStrokeCubicPrimitiveF64 {
    val (_, remainingF64) = splitCubicPrimitiveF64(primitiveF64, startParameterF64)
    val relativeEndParameterF64 = relativeIntervalEndParameterF64(startParameterF64, endParameterF64)
    return splitCubicPrimitiveF64(remainingF64, relativeEndParameterF64).first
}

private fun splitCubicPrimitiveF64(
    primitiveF64: PathStrokeCubicPrimitiveF64,
    parameterF64: Double,
): Pair<PathStrokeCubicPrimitiveF64, PathStrokeCubicPrimitiveF64> {
    val startControl1F64 = interpolatedOutlinePointF64(primitiveF64.startF64, primitiveF64.control1F64, parameterF64)
    val controlsF64 = interpolatedOutlinePointF64(primitiveF64.control1F64, primitiveF64.control2F64, parameterF64)
    val control2EndF64 = interpolatedOutlinePointF64(primitiveF64.control2F64, primitiveF64.endF64, parameterF64)
    val startControl2F64 = interpolatedOutlinePointF64(startControl1F64, controlsF64, parameterF64)
    val endControl1F64 = interpolatedOutlinePointF64(controlsF64, control2EndF64, parameterF64)
    val splitPointF64 = interpolatedOutlinePointF64(startControl2F64, endControl1F64, parameterF64)
    return PathStrokeCubicPrimitiveF64(
        primitiveF64.startF64,
        startControl1F64,
        startControl2F64,
        splitPointF64,
    ) to PathStrokeCubicPrimitiveF64(
        splitPointF64,
        endControl1F64,
        control2EndF64,
        primitiveF64.endF64,
    )
}

private fun restrictedArcPrimitiveF64(
    primitiveF64: PathStrokeSvgArcPrimitiveF64,
    startParameterF64: Double,
    endParameterF64: Double,
): PathStrokeSvgArcPrimitiveF64 {
    val startF64 = finiteSourcePointF64(primitiveF64, startParameterF64)
    val endF64 = finiteSourcePointF64(primitiveF64, endParameterF64)
    val sourceArcF64 = primitiveF64.arcF64 ?: return PathStrokeSvgArcPrimitiveF64(startF64, endF64, null)
    return PathStrokeSvgArcPrimitiveF64(
        startF64,
        endF64,
        ArcCenterF64(
            center = sourceArcF64.center,
            radiusX = sourceArcF64.radiusX,
            radiusY = sourceArcF64.radiusY,
            rotationRadians = sourceArcF64.rotationRadians,
            startAngle = sourceArcF64.startAngle + sourceArcF64.sweepAngle * startParameterF64,
            sweepAngle = sourceArcF64.sweepAngle * (endParameterF64 - startParameterF64),
        ),
    )
}

private fun relativeIntervalEndParameterF64(startParameterF64: Double, endParameterF64: Double): Double {
    val remainingParameterF64 = 1.0 - startParameterF64
    if (remainingParameterF64 <= 0.0) throw PathStrokeOutlineInvalidAbort()
    return ((endParameterF64 - startParameterF64) / remainingParameterF64).also { relativeParameterF64 ->
        if (!relativeParameterF64.isFinite() || relativeParameterF64 < 0.0 || relativeParameterF64 > 1.0) {
            throw PathStrokeOutlineInvalidAbort()
        }
    }
}

private fun interpolatedOutlinePointF64(
    firstF64: Point2F64,
    secondF64: Point2F64,
    parameterF64: Double,
): Point2F64 = Point2F64(
    interpolatedOutlineCoordinateF64(firstF64.x, secondF64.x, parameterF64),
    interpolatedOutlineCoordinateF64(firstF64.y, secondF64.y, parameterF64),
)

/**
 * Splits source spans at derivative cusps, certifies each source interval, then stores only
 * device-space chords. Projection is therefore fully consumed before [PathStrokeOutlineF64] is
 * observable. Post-projection flattening is intentionally limited to the hairline lane.
 */
private fun materializeProjectedHairlineCenterlineF64(
    sourceCenterlineF64: PathStrokeCenterlineF64,
    projectionF64: PathStrokeProjectionF64,
    policyF64: PathStrokePolicyF64,
    ledgerI64: PathStrokeWorkLedgerI64,
): PathStrokeCenterlineF64? {
    ledgerI64.debitBeforeEmissionI64(PathStrokeWorkUsageI64(snapshotByteCountI64 = 16L))
    val contoursF64 = mutableListOf<List<PathStrokePrimitiveSpanF64>>()
    val closedContoursF64 = mutableListOf<Boolean>()
    repeat(sourceCenterlineF64.contourCountI32) { contourIndexI32 ->
        ledgerI64.debitBeforeEmissionI64(PathStrokeWorkUsageI64(snapshotByteCountI64 = 16L))
        val deviceSpansF64 = mutableListOf<PathStrokePrimitiveSpanF64>()
        ledgerI64.debitTopologyBeforeEmissionI64(1L)
        sourceCenterlineF64.contourSpansViewF64(contourIndexI32).forEach { sourceSpanF64 ->
            ledgerI64.debitTopologyBeforeEmissionI64(1L)
            val splitParametersF64 = splitParametersF64(
                sourceSpanF64.primitiveF64,
                sourceSpanF64.startParameterF64,
                sourceSpanF64.endParameterF64,
            )
            for (indexI32 in 0 until splitParametersF64.lastIndex) {
                materializeProjectedHairlineIntervalF64(
                    destinationF64 = deviceSpansF64,
                    sourcePrimitiveF64 = sourceSpanF64.primitiveF64,
                    startParameterF64 = splitParametersF64[indexI32],
                    endParameterF64 = splitParametersF64[indexI32 + 1],
                    projectionF64 = projectionF64,
                    policyF64 = policyF64,
                    ledgerI64 = ledgerI64,
                    depthI32 = 0,
                )
            }
        }
        if (deviceSpansF64.isNotEmpty()) {
            ledgerI64.debitBeforeEmissionI64(PathStrokeWorkUsageI64(snapshotByteCountI64 = 16L))
            contoursF64 += deviceSpansF64
            closedContoursF64 += sourceCenterlineF64.isContourClosed(contourIndexI32)
        }
    }
    if (contoursF64.isEmpty()) return null
    ledgerI64.debitTopologyBeforeEmissionI64(1L)
    val spanSnapshotBytesI64 = contoursF64.sumOf { contourF64 -> contourF64.size.toLong() * 32L }
    ledgerI64.debitBeforeEmissionI64(
        PathStrokeWorkUsageI64(snapshotByteCountI64 = spanSnapshotBytesI64 + closedContoursF64.size.toLong()),
    )
    return PathStrokeCenterlineF64.of(contoursF64, closedContoursF64.toBooleanArray())
}

private fun materializeProjectedHairlineIntervalF64(
    destinationF64: MutableList<PathStrokePrimitiveSpanF64>,
    sourcePrimitiveF64: PathStrokePrimitiveF64,
    startParameterF64: Double,
    endParameterF64: Double,
    projectionF64: PathStrokeProjectionF64,
    policyF64: PathStrokePolicyF64,
    ledgerI64: PathStrokeWorkLedgerI64,
    depthI32: Int,
) {
    if (startParameterF64 >= endParameterF64) return
    ledgerI64.debitTopologyBeforeEmissionI64(1L)
    val sourceStartF64 = finiteSourcePointF64(sourcePrimitiveF64, startParameterF64)
    val sourceEndF64 = finiteSourcePointF64(sourcePrimitiveF64, endParameterF64)
    val sourceGeometryF64 = sourceIntervalGeometryF64(
        sourcePrimitiveF64,
        startParameterF64,
        endParameterF64,
    )
    val sourceIntervalF64 = PathStrokeOutlineIntervalF64(
        primitiveF64 = SourceStrokeOutlinePrimitiveF64(sourcePrimitiveF64),
        startParameterF64 = startParameterF64,
        endParameterF64 = endParameterF64,
        boundsF64 = sourceGeometryF64.boundsF64,
        sourceSagittaUpperBoundF64 = sourceGeometryF64.sagittaUpperBoundF64,
    )
    when (val certificationF64 = projectionF64.certifyOutlineIntervalF64(sourceIntervalF64)) {
        is PathStrokeProjectionIntervalResultF64.Bounded -> {
            val maximumSagittaF64 = certificationF64.maximumDeviceSagittaUpperBoundF64
            if (!maximumSagittaF64.isFinite() || maximumSagittaF64 < 0.0) throw PathStrokeProjectionAbort()
            if (maximumSagittaF64 > policyF64.maximumSagittaErrorF64) {
                if (depthI32 >= policyF64.limitsI32.maxSubdivisionDepthI32) {
                    throw PathStrokeResourceLimitAbort(PathStrokeResourceLimitReason.FlatteningDidNotConverge)
                }
                ledgerI64.debitTopologyBeforeEmissionI64(1L)
                val middleParameterF64 = startParameterF64 + (endParameterF64 - startParameterF64) * 0.5
                if (!middleParameterF64.isFinite() || middleParameterF64 <= startParameterF64 ||
                    middleParameterF64 >= endParameterF64
                ) throw PathStrokeProjectionAbort()
                materializeProjectedHairlineIntervalF64(
                    destinationF64,
                    sourcePrimitiveF64,
                    startParameterF64,
                    middleParameterF64,
                    projectionF64,
                    policyF64,
                    ledgerI64,
                    depthI32 + 1,
                )
                materializeProjectedHairlineIntervalF64(
                    destinationF64,
                    sourcePrimitiveF64,
                    middleParameterF64,
                    endParameterF64,
                    projectionF64,
                    policyF64,
                    ledgerI64,
                    depthI32 + 1,
                )
                return
            }
            ledgerI64.debitTopologyBeforeEmissionI64(1L)
            val deviceStartF64 = projectPointF64(projectionF64, sourceStartF64)
            val deviceEndF64 = projectPointF64(projectionF64, sourceEndF64)
            if (sameOutlinePointF64(deviceStartF64, deviceEndF64)) return
            ledgerI64.debitBeforeEmissionI64(
                PathStrokeWorkUsageI64(attemptedGeometryUnitCountI64 = 1L, snapshotByteCountI64 = 32L),
            )
            destinationF64 += PathStrokePrimitiveSpanF64(
                PathStrokeLinePrimitiveF64(deviceStartF64, deviceEndF64),
                0.0,
                1.0,
            )
        }

        PathStrokeProjectionIntervalResultF64.HorizonCrossing ->
            throw PathStrokeProjectionAbort(PathStrokeInvalidSceneReason.ProjectionHorizonCrossing)

        PathStrokeProjectionIntervalResultF64.NonFinite,
        PathStrokeProjectionIntervalResultF64.Unbounded,
        -> throw PathStrokeProjectionAbort()
    }
}

private fun lineEmissionF64(startF64: Point2F64, endF64: Point2F64): OutlineEmissionF64 = OutlineEmissionF64(
    primitiveF64 = LineStrokeOutlinePrimitiveF64(startF64, endF64),
    startParameterF64 = 0.0,
    endParameterF64 = 1.0,
    boundsF64 = boundsOfPointsF64(listOf(startF64, endF64)),
    sourceSagittaUpperBoundF64 = 0.0,
)

private fun roundCapEmissionF64(centerF64: Point2F64, startF64: Point2F64, endF64: Point2F64): OutlineEmissionF64 =
    roundEmissionF64(centerF64, startF64, endF64) ?: lineEmissionF64(startF64, endF64)

private fun roundEmissionF64(centerF64: Point2F64, startF64: Point2F64, endF64: Point2F64): OutlineEmissionF64? {
    val startDeltaF64 = startF64 - centerF64
    val endDeltaF64 = endF64 - centerF64
    val radiusF64 = startDeltaF64.length()
    if (!radiusF64.isFinite() || radiusF64 <= strokeOutlineEpsilonF64 ||
        abs(endDeltaF64.length() - radiusF64) > max(1.0, radiusF64) * 1e-7
    ) return null
    val startAngleF64 = atan2(startDeltaF64.y, startDeltaF64.x)
    var sweepAngleF64 = atan2(startDeltaF64.cross(endDeltaF64), startDeltaF64.dot(endDeltaF64))
    if (abs(sweepAngleF64) <= strokeOutlineEpsilonF64) return null
    if (abs(abs(sweepAngleF64) - PI) <= 1e-9) sweepAngleF64 = -PI
    val primitiveF64 = ArcStrokeOutlinePrimitiveF64(centerF64, radiusF64, startAngleF64, sweepAngleF64)
    return OutlineEmissionF64(
        primitiveF64 = primitiveF64,
        startParameterF64 = 0.0,
        endParameterF64 = 1.0,
        boundsF64 = expandedBoundsF64(boundsOfPointsF64(listOf(centerF64)), radiusF64),
        sourceSagittaUpperBoundF64 = radiusF64 * 2.0,
    )
}

private fun offsetLineIntersectionF64(
    firstPointF64: Point2F64,
    firstDirectionF64: Vector2F64,
    secondPointF64: Point2F64,
    secondDirectionF64: Vector2F64,
): Point2F64? {
    val denominatorF64 = firstDirectionF64.cross(secondDirectionF64)
    if (!denominatorF64.isFinite() || abs(denominatorF64) <= strokeOutlineEpsilonF64) return null
    val deltaF64 = secondPointF64 - firstPointF64
    val parameterF64 = deltaF64.cross(secondDirectionF64) / denominatorF64
    if (!parameterF64.isFinite()) return null
    val resultF64 = firstPointF64 + firstDirectionF64 * parameterF64
    return resultF64.takeIf(Point2F64::isFinite)
}

private fun splitParametersF64(
    primitiveF64: PathStrokePrimitiveF64,
    startParameterF64: Double,
    endParameterF64: Double,
): List<Double> {
    val cuspParametersF64 = when (primitiveF64) {
        is PathStrokeQuadPrimitiveF64 -> quadCuspParametersF64(primitiveF64)
        is PathStrokeCubicPrimitiveF64 -> cubicCuspParametersF64(primitiveF64)
        else -> emptyList()
    }
    return (listOf(startParameterF64) + cuspParametersF64 + listOf(endParameterF64))
        .filter { parameterF64 -> parameterF64.isFinite() && parameterF64 >= startParameterF64 && parameterF64 <= endParameterF64 }
        .sorted()
        .fold(mutableListOf<Double>()) { valuesF64, parameterF64 ->
            if (valuesF64.isEmpty() || valuesF64.last() != parameterF64) valuesF64 += parameterF64
            valuesF64
        }
}

private fun quadCuspParametersF64(primitiveF64: PathStrokeQuadPrimitiveF64): List<Double> {
    val xRootF64 = linearRootF64(
        2.0 * (primitiveF64.startF64.x - 2.0 * primitiveF64.controlF64.x + primitiveF64.endF64.x),
        2.0 * (primitiveF64.controlF64.x - primitiveF64.startF64.x),
    )
    val yRootF64 = linearRootF64(
        2.0 * (primitiveF64.startF64.y - 2.0 * primitiveF64.controlF64.y + primitiveF64.endF64.y),
        2.0 * (primitiveF64.controlF64.y - primitiveF64.startF64.y),
    )
    return commonCuspCandidatesF64(listOfNotNull(xRootF64, yRootF64), primitiveF64)
}

private fun cubicCuspParametersF64(primitiveF64: PathStrokeCubicPrimitiveF64): List<Double> {
    val xRootsF64 = quadraticRootsF64(
        -primitiveF64.startF64.x + 3.0 * primitiveF64.control1F64.x - 3.0 * primitiveF64.control2F64.x + primitiveF64.endF64.x,
        2.0 * primitiveF64.startF64.x - 4.0 * primitiveF64.control1F64.x + 2.0 * primitiveF64.control2F64.x,
        primitiveF64.control1F64.x - primitiveF64.startF64.x,
    )
    val yRootsF64 = quadraticRootsF64(
        -primitiveF64.startF64.y + 3.0 * primitiveF64.control1F64.y - 3.0 * primitiveF64.control2F64.y + primitiveF64.endF64.y,
        2.0 * primitiveF64.startF64.y - 4.0 * primitiveF64.control1F64.y + 2.0 * primitiveF64.control2F64.y,
        primitiveF64.control1F64.y - primitiveF64.startF64.y,
    )
    return commonCuspCandidatesF64(xRootsF64 + yRootsF64, primitiveF64)
}

private fun commonCuspCandidatesF64(
    candidatesF64: List<Double>,
    primitiveF64: PathStrokePrimitiveF64,
): List<Double> = candidatesF64.filter { parameterF64 ->
    parameterF64 > 0.0 && parameterF64 < 1.0 &&
        cuspCandidateHasZeroDerivativeF64(primitiveF64, parameterF64)
}

/**
 * Root formulae normally leave a small relative residual in the other coordinate, even for a
 * mathematical cusp.  Compare that residual to this primitive's derivative scale so tiny paths
 * retain the same decision as large ones without treating a finite tangent as zero.
 */
private fun cuspCandidateHasZeroDerivativeF64(
    primitiveF64: PathStrokePrimitiveF64,
    parameterF64: Double,
): Boolean {
    val derivativeF64 = primitiveF64.derivativeAtF64(parameterF64)
    if (!derivativeF64.isFinite()) return false
    val scaleF64 = derivativeControlScaleF64(primitiveF64)
    if (!scaleF64.isFinite()) return false
    if (scaleF64 == 0.0) return isExactlyZeroVectorF64(derivativeF64)
    val scaledX = derivativeF64.x / scaleF64
    val scaledY = derivativeF64.y / scaleF64
    return sqrt(scaledX * scaledX + scaledY * scaledY) <= cuspRootRelativeResidualF64
}

private fun derivativeControlScaleF64(primitiveF64: PathStrokePrimitiveF64): Double = when (primitiveF64) {
    is PathStrokeQuadPrimitiveF64 -> max(
        vectorComponentScaleF64((primitiveF64.controlF64 - primitiveF64.startF64) * 2.0),
        vectorComponentScaleF64((primitiveF64.endF64 - primitiveF64.controlF64) * 2.0),
    )

    is PathStrokeCubicPrimitiveF64 -> max(
        vectorComponentScaleF64((primitiveF64.control1F64 - primitiveF64.startF64) * 3.0),
        max(
            vectorComponentScaleF64((primitiveF64.control2F64 - primitiveF64.control1F64) * 3.0),
            vectorComponentScaleF64((primitiveF64.endF64 - primitiveF64.control2F64) * 3.0),
        ),
    )

    else -> vectorComponentScaleF64(primitiveF64.derivativeAtF64(0.0))
}

private fun vectorComponentScaleF64(vectorF64: Vector2F64): Double = max(abs(vectorF64.x), abs(vectorF64.y))

private fun linearRootF64(aF64: Double, bF64: Double): Double? =
    if (!aF64.isFinite() || !bF64.isFinite() || aF64 == 0.0) null else -bF64 / aF64

private fun quadraticRootsF64(aF64: Double, bF64: Double, cF64: Double): List<Double> {
    val coefficientScaleF64 = max(abs(aF64), max(abs(bF64), abs(cF64)))
    if (!coefficientScaleF64.isFinite() || coefficientScaleF64 == 0.0) return emptyList()
    val scaledAF64 = aF64 / coefficientScaleF64
    val scaledBF64 = bF64 / coefficientScaleF64
    val scaledCF64 = cF64 / coefficientScaleF64
    if (scaledAF64 == 0.0) return listOfNotNull(linearRootF64(scaledBF64, scaledCF64))
    val squaredBF64 = scaledBF64 * scaledBF64
    val fourTimesAF64 = 4.0 * scaledAF64
    val fourTimesACF64 = fourTimesAF64 * scaledCF64
    val rawDiscriminantF64 = squaredBF64 - fourTimesACF64
    if (!rawDiscriminantF64.isFinite()) return emptyList()
    val discriminantF64 = if (rawDiscriminantF64 < 0.0 &&
        -rawDiscriminantF64 <= discriminantRoundingErrorBoundF64(
            scaledAF64,
            scaledBF64,
            scaledCF64,
            squaredBF64,
            fourTimesACF64,
            rawDiscriminantF64,
        )
    ) {
        0.0
    } else {
        rawDiscriminantF64
    }
    if (discriminantF64 < 0.0) return emptyList()
    val rootF64 = sqrt(discriminantF64)
    if (rootF64 == 0.0) return listOf(-scaledBF64 / (2.0 * scaledAF64))
    val signedRootF64 = if (scaledBF64 >= 0.0) rootF64 else -rootF64
    val stableTermF64 = -0.5 * (scaledBF64 + signedRootF64)
    return if (stableTermF64 == 0.0) {
        listOf(
            (-scaledBF64 - rootF64) / (2.0 * scaledAF64),
            (-scaledBF64 + rootF64) / (2.0 * scaledAF64),
        )
    } else {
        listOf(stableTermF64 / scaledAF64, scaledCF64 / stableTermF64)
    }
}

/** Bounds coefficient normalization and evaluation rounding in `b² - 4ac` by local ULPs. */
private fun discriminantRoundingErrorBoundF64(
    scaledAF64: Double,
    scaledBF64: Double,
    scaledCF64: Double,
    squaredBF64: Double,
    fourTimesACF64: Double,
    discriminantF64: Double,
): Double {
    val aUlpF64 = nonNegativeUlpF64(scaledAF64)
    val bUlpF64 = nonNegativeUlpF64(scaledBF64)
    val cUlpF64 = nonNegativeUlpF64(scaledCF64)
    val coefficientPropagationF64 =
        2.0 * abs(scaledBF64) * bUlpF64 + bUlpF64 * bUlpF64 +
            4.0 * (
                abs(scaledCF64) * aUlpF64 +
                    abs(scaledAF64) * cUlpF64 +
                    aUlpF64 * cUlpF64
                )
    val evaluationRoundingF64 =
        nonNegativeUlpF64(squaredBF64) + nonNegativeUlpF64(fourTimesACF64) + nonNegativeUlpF64(discriminantF64)
    return nextUpNonNegativeF64(coefficientPropagationF64 + evaluationRoundingF64)
}

private fun nonNegativeUlpF64(valueF64: Double): Double {
    val magnitudeF64 = abs(valueF64)
    if (!magnitudeF64.isFinite()) return Double.POSITIVE_INFINITY
    if (magnitudeF64 == 0.0) return Double.fromBits(1L)
    return Double.fromBits(magnitudeF64.toBits() + 1L) - magnitudeF64
}

private fun nextUpNonNegativeF64(valueF64: Double): Double = when {
    !valueF64.isFinite() -> valueF64
    valueF64 == 0.0 -> Double.fromBits(1L)
    else -> Double.fromBits(valueF64.toBits() + 1L)
}

private fun oneSidedTangentF64(
    primitiveF64: PathStrokePrimitiveF64,
    startParameterF64: Double,
    endParameterF64: Double,
    forward: Boolean,
): Vector2F64? {
    val parameterF64 = if (forward) startParameterF64 else endParameterF64
    val directF64 = primitiveF64.derivativeAtF64(parameterF64)
    normalizedStrokeTangentF64(directF64)?.let { tangentF64 -> return tangentF64 }
    val widthF64 = endParameterF64 - startParameterF64
    val sampleF64 = if (forward) {
        min(endParameterF64, startParameterF64 + max(widthF64 * 1e-5, 1e-8))
    } else {
        max(startParameterF64, endParameterF64 - max(widthF64 * 1e-5, 1e-8))
    }
    val sampledF64 = primitiveF64.derivativeAtF64(sampleF64)
    normalizedStrokeTangentF64(sampledF64)?.let { tangentF64 -> return tangentF64 }
    val firstPointF64 = finiteSourcePointF64(primitiveF64, startParameterF64)
    val secondPointF64 = finiteSourcePointF64(primitiveF64, endParameterF64)
    return normalizedStrokeTangentF64(secondPointF64 - firstPointF64)
}

private fun isUsableTangentF64(tangentF64: Vector2F64): Boolean =
    normalizedStrokeTangentF64(tangentF64) != null

private fun leftNormalF64(tangentF64: Vector2F64): Vector2F64 {
    val unitF64 = normalizedStrokeTangentF64(tangentF64) ?: throw PathStrokeOutlineInvalidAbort()
    return Vector2F64(-unitF64.y, unitF64.x)
}

/** Normalizes every finite non-zero tangent in its own scale, including subnormal derivatives. */
private fun normalizedStrokeTangentF64(tangentF64: Vector2F64): Vector2F64? {
    if (!tangentF64.isFinite()) return null
    val scaleF64 = max(abs(tangentF64.x), abs(tangentF64.y))
    if (scaleF64 == 0.0) return null
    val scaledX = tangentF64.x / scaleF64
    val scaledY = tangentF64.y / scaleF64
    val scaledLengthF64 = sqrt(scaledX * scaledX + scaledY * scaledY)
    if (!scaledLengthF64.isFinite() || scaledLengthF64 == 0.0) return null
    return Vector2F64(scaledX / scaledLengthF64, scaledY / scaledLengthF64)
}

private fun finiteSourcePointF64(primitiveF64: PathStrokePrimitiveF64, parameterF64: Double): Point2F64 {
    val pointF64 = primitiveF64.pointAtF64(parameterF64)
    if (!pointF64.isFinite()) throw PathStrokeOutlineInvalidAbort()
    return pointF64
}

private fun projectPointF64(projectionF64: PathStrokeProjectionF64, pointF64: Point2F64): Point2F64 = when (
    val resultF64 = projectionF64.projectPointF64(pointF64)
) {
    is PathStrokeProjectionPointResultF64.Ready -> resultF64.pointF64.takeIf(Point2F64::isFinite)
        ?: throw PathStrokeProjectionAbort()
    PathStrokeProjectionPointResultF64.NonFinite -> throw PathStrokeProjectionAbort()
}

private fun sourceBoundsF64(primitiveF64: PathStrokePrimitiveF64): PathStrokeBoundsF64 {
    val parametersF64 = when (primitiveF64) {
        is PathStrokeLinePrimitiveF64 -> listOf(0.0, 1.0)
        is PathStrokeQuadPrimitiveF64 -> listOf(0.0, 1.0) + quadExtremaParametersF64(primitiveF64)
        is PathStrokeCubicPrimitiveF64 -> listOf(0.0, 1.0) + cubicExtremaParametersF64(primitiveF64)
        is PathStrokeSvgArcPrimitiveF64 -> return primitiveF64.arcF64?.let { arcF64 ->
            boundsOfPointsF64(arcF64.extrema())
        } ?: boundsOfPointsF64(listOf(primitiveF64.startF64, primitiveF64.endF64))
    }
    return boundsOfPointsF64(parametersF64.map { parameterF64 -> finiteSourcePointF64(primitiveF64, parameterF64) })
}

private fun quadExtremaParametersF64(primitiveF64: PathStrokeQuadPrimitiveF64): List<Double> = listOfNotNull(
    linearRootF64(
        primitiveF64.startF64.x - 2.0 * primitiveF64.controlF64.x + primitiveF64.endF64.x,
        primitiveF64.controlF64.x - primitiveF64.startF64.x,
    ),
    linearRootF64(
        primitiveF64.startF64.y - 2.0 * primitiveF64.controlF64.y + primitiveF64.endF64.y,
        primitiveF64.controlF64.y - primitiveF64.startF64.y,
    ),
).filter { parameterF64 -> parameterF64 in 0.0..1.0 }

private fun cubicExtremaParametersF64(primitiveF64: PathStrokeCubicPrimitiveF64): List<Double> =
    quadraticRootsF64(
        -primitiveF64.startF64.x + 3.0 * primitiveF64.control1F64.x - 3.0 * primitiveF64.control2F64.x + primitiveF64.endF64.x,
        2.0 * primitiveF64.startF64.x - 4.0 * primitiveF64.control1F64.x + 2.0 * primitiveF64.control2F64.x,
        primitiveF64.control1F64.x - primitiveF64.startF64.x,
    ).plus(
        quadraticRootsF64(
            -primitiveF64.startF64.y + 3.0 * primitiveF64.control1F64.y - 3.0 * primitiveF64.control2F64.y + primitiveF64.endF64.y,
            2.0 * primitiveF64.startF64.y - 4.0 * primitiveF64.control1F64.y + 2.0 * primitiveF64.control2F64.y,
            primitiveF64.control1F64.y - primitiveF64.startF64.y,
        ),
    ).filter { parameterF64 -> parameterF64 in 0.0..1.0 }

private fun centerlineSagittaUpperBoundF64(primitiveF64: PathStrokePrimitiveF64): Double = when (primitiveF64) {
    is PathStrokeLinePrimitiveF64 -> 0.0
    is PathStrokeQuadPrimitiveF64 -> pointToSegmentDistanceF64(
        primitiveF64.controlF64,
        primitiveF64.startF64,
        primitiveF64.endF64,
    )

    is PathStrokeCubicPrimitiveF64 -> max(
        pointToSegmentDistanceF64(primitiveF64.control1F64, primitiveF64.startF64, primitiveF64.endF64),
        pointToSegmentDistanceF64(primitiveF64.control2F64, primitiveF64.startF64, primitiveF64.endF64),
    )

    is PathStrokeSvgArcPrimitiveF64 -> primitiveF64.arcF64?.let { arcF64 ->
        max(arcF64.radiusX, arcF64.radiusY) * abs(arcF64.sweepAngle) * abs(arcF64.sweepAngle) * 0.125
    } ?: 0.0
}.also { resultF64 -> if (!resultF64.isFinite() || resultF64 < 0.0) throw PathStrokeOutlineInvalidAbort() }

private fun pointToSegmentDistanceF64(pointF64: Point2F64, startF64: Point2F64, endF64: Point2F64): Double {
    val deltaF64 = endF64 - startF64
    val lengthSquaredF64 = deltaF64.lengthSquared()
    if (!lengthSquaredF64.isFinite()) throw PathStrokeOutlineInvalidAbort()
    if (lengthSquaredF64 <= strokeOutlineEpsilonF64) return pointF64.distanceTo(startF64)
    val projectionF64 = ((pointF64 - startF64).dot(deltaF64) / lengthSquaredF64).coerceIn(0.0, 1.0)
    val resultF64 = pointF64.distanceTo(startF64 + deltaF64 * projectionF64)
    if (!resultF64.isFinite()) throw PathStrokeOutlineInvalidAbort()
    return resultF64
}

private fun boundsOfPointsF64(pointsF64: List<Point2F64>): PathStrokeBoundsF64 {
    if (pointsF64.isEmpty() || pointsF64.any { pointF64 -> !pointF64.isFinite() }) throw PathStrokeOutlineInvalidAbort()
    return PathStrokeBoundsF64(
        leftF64 = pointsF64.minOf(Point2F64::x),
        topF64 = pointsF64.minOf(Point2F64::y),
        rightF64 = pointsF64.maxOf(Point2F64::x),
        bottomF64 = pointsF64.maxOf(Point2F64::y),
    )
}

private fun expandedBoundsF64(boundsF64: PathStrokeBoundsF64, radiusF64: Double): PathStrokeBoundsF64 {
    if (!radiusF64.isFinite() || radiusF64 < 0.0) throw PathStrokeOutlineInvalidAbort()
    val resultF64 = PathStrokeBoundsF64(
        leftF64 = boundsF64.leftF64 - radiusF64,
        topF64 = boundsF64.topF64 - radiusF64,
        rightF64 = boundsF64.rightF64 + radiusF64,
        bottomF64 = boundsF64.bottomF64 + radiusF64,
    )
    if (!resultF64.leftF64.isFinite() || !resultF64.topF64.isFinite() ||
        !resultF64.rightF64.isFinite() || !resultF64.bottomF64.isFinite()
    ) throw PathStrokeOutlineInvalidAbort()
    return resultF64
}

private fun sameOutlinePointF64(firstF64: Point2F64, secondF64: Point2F64): Boolean =
    firstF64.x == secondF64.x && firstF64.y == secondF64.y

private fun interpolatedOutlineCoordinateF64(firstF64: Double, secondF64: Double, parameterF64: Double): Double =
    if ((firstF64 < 0.0) == (secondF64 < 0.0)) {
        firstF64 + (secondF64 - firstF64) * parameterF64
    } else {
        firstF64 * (1.0 - parameterF64) + secondF64 * parameterF64
    }

private const val strokeOutlineEpsilonF64: Double = 1e-10
private const val cuspRootRelativeResidualF64: Double = 1e-12
