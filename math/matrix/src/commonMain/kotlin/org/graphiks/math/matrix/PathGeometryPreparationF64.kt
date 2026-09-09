package org.graphiks.math.matrix

import org.graphiks.math.geometry.PathF32
import org.graphiks.math.geometry.InversePathDrawMode
import org.graphiks.math.geometry.InversePathPreparationResult
import org.graphiks.math.geometry.PathFillFlatteningPolicyF64
import org.graphiks.math.geometry.PathFillGeometryF32
import org.graphiks.math.geometry.PathFillInputF64
import org.graphiks.math.geometry.PathFillInvalidSceneReason
import org.graphiks.math.geometry.PathFillWithStrokeWorkPreparationResult
import org.graphiks.math.geometry.PathStrokeDrawMode
import org.graphiks.math.geometry.PathStrokeInvalidSceneReason
import org.graphiks.math.geometry.PathStrokePolicyF64
import org.graphiks.math.geometry.PathStrokePreparationResult
import org.graphiks.math.geometry.PathStrokeResourceLimitReason
import org.graphiks.math.geometry.PathStrokeStyleF64
import org.graphiks.math.geometry.PathStrokeProjectionF64
import org.graphiks.math.geometry.PathStrokeWorkUsageI64
import org.graphiks.math.geometry.PathStrokeDeviceOutlinePreparationResult
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.prepareInversePathGeometryF32
import org.graphiks.math.geometry.preparePathStrokeDeviceOutlineF64
import org.graphiks.math.geometry.preparePathFillGeometryWithStrokeWorkF32
import org.graphiks.math.geometry.prepareProjectedPathStrokeGeometryF32

/** Preserves the stage that rejected a transformed fill before geometry is published. */
public sealed interface PathTransformedFillInvalidSceneReason {
    public data class Fill(public val value: PathFillInvalidSceneReason) : PathTransformedFillInvalidSceneReason

    public data class Projective(public val value: PathProjectiveInvalidSceneReason) :
        PathTransformedFillInvalidSceneReason
}

/** Preserves the stage that exhausted bounded transformed-fill work before publication. */
public sealed interface PathTransformedFillResourceLimitReason {
    public data class Geometry(public val value: PathStrokeResourceLimitReason) :
        PathTransformedFillResourceLimitReason

    public data class Projective(public val value: PathProjectiveResourceLimitReason) :
        PathTransformedFillResourceLimitReason
}

/** Final device fill geometry and the one cumulative work ledger across transform and finalization. */
public sealed interface PathTransformedFillPreparationResult {
    public data class Ready(
        public val geometryF32: PathFillGeometryF32,
        public val pathWorkUsageI64: PathStrokeWorkUsageI64,
        public val frameWorkUsageAfterI64: PathStrokeWorkUsageI64,
    ) : PathTransformedFillPreparationResult

    public data class Empty(
        public val pathWorkUsageI64: PathStrokeWorkUsageI64,
        public val frameWorkUsageAfterI64: PathStrokeWorkUsageI64,
    ) : PathTransformedFillPreparationResult

    public data class InvalidScene(public val reason: PathTransformedFillInvalidSceneReason) :
        PathTransformedFillPreparationResult

    public data class ResourceLimitExceeded(public val reason: PathTransformedFillResourceLimitReason) :
        PathTransformedFillPreparationResult
}

/** Widens the matrix before dispatching its transformed fill preparation. */
public fun Matrix3x3F32.preparePathFillGeometryF32(
    path: PathF32,
    fillPolicyF64: PathFillFlatteningPolicyF64 = PathFillFlatteningPolicyF64(),
    strokePolicyF64: PathStrokePolicyF64 = PathStrokePolicyF64(),
    frameWorkUsageBeforeI64: PathStrokeWorkUsageI64 = PathStrokeWorkUsageI64(),
): PathTransformedFillPreparationResult = toMatrix3x3F64().preparePathFillGeometryF32(
    path = path,
    fillPolicyF64 = fillPolicyF64,
    strokePolicyF64 = strokePolicyF64,
    frameWorkUsageBeforeI64 = frameWorkUsageBeforeI64,
)

/**
 * Prepares final device fill geometry for every exact transform class.
 *
 * Affine mapping and perspective projection each publish only their snapshots; the shared fill
 * finalizer receives those snapshots and therefore never restarts either path or frame work.
 */
public fun Matrix3x3F64.preparePathFillGeometryF32(
    path: PathF32,
    fillPolicyF64: PathFillFlatteningPolicyF64 = PathFillFlatteningPolicyF64(),
    strokePolicyF64: PathStrokePolicyF64 = PathStrokePolicyF64(),
    frameWorkUsageBeforeI64: PathStrokeWorkUsageI64 = PathStrokeWorkUsageI64(),
): PathTransformedFillPreparationResult {
    if (!isFinite()) {
        return PathTransformedFillPreparationResult.InvalidScene(
            PathTransformedFillInvalidSceneReason.Projective(PathProjectiveInvalidSceneReason.NonFiniteMatrix),
        )
    }
    return when (classifyPathTransform()) {
        PathTransformClass.Identity -> preparePathFillGeometryWithStrokeWorkF32(
            inputF64 = PathFillInputF64.fromPathF32(path),
            fillPolicyF64 = fillPolicyF64,
            strokePolicyF64 = strokePolicyF64,
            frameWorkUsageBeforeI64 = frameWorkUsageBeforeI64,
        ).toTransformedFillPreparationResult()

        PathTransformClass.AxisAlignedAffine,
        PathTransformClass.GeneralAffine,
        -> prepareAffinePathFillGeometryF32(path, fillPolicyF64, strokePolicyF64, frameWorkUsageBeforeI64)

        PathTransformClass.Perspective -> preparePerspectivePathFillGeometryF32(
            path,
            fillPolicyF64,
            strokePolicyF64,
            frameWorkUsageBeforeI64,
        )
    }
}

/**
 * Prepares stroke or `StrokeAndFill` geometry for every exact transform class.
 *
 * Finite strokes retain source-space outlines until projection; hairlines project their
 * centerline before the geometry worker expands them by one device pixel.
 */
public fun Matrix3x3F64.preparePathStrokeGeometryF32(
    path: PathF32,
    styleF64: PathStrokeStyleF64,
    mode: PathStrokeDrawMode,
    policyF64: PathStrokePolicyF64 = PathStrokePolicyF64(),
    frameWorkUsageBeforeI64: PathStrokeWorkUsageI64 = PathStrokeWorkUsageI64(),
): PathStrokePreparationResult {
    if (!isFinite()) {
        return PathStrokePreparationResult.InvalidScene(PathStrokeInvalidSceneReason.NonFiniteInput)
    }
    val inputF64 = PathFillInputF64.fromPathF32(path)
    return when (classifyPathTransform()) {
        PathTransformClass.Identity,
        PathTransformClass.AxisAlignedAffine,
        PathTransformClass.GeneralAffine,
        -> prepareProjectedPathStrokeGeometryF32(
            inputF64 = inputF64,
            styleF64 = styleF64,
            mode = mode,
            projectionF64 = toAffinePathStrokeProjectionF64(),
            policyF64 = policyF64,
            frameWorkUsageBeforeI64 = frameWorkUsageBeforeI64,
            deviceFillSegmentMapperF64 = if (mode == PathStrokeDrawMode.StrokeAndFill) {
                pathStrokeDeviceFillSegmentMapperF64()
            } else {
                null
            },
        )

        PathTransformClass.Perspective -> preparePerspectivePathStrokeGeometryF32(
            path,
            inputF64,
            styleF64,
            mode,
            policyF64,
            frameWorkUsageBeforeI64,
        )
    }
}

/**
 * Prepares the finite interior of an inverse path in device space, retaining inversion as the
 * bounded-domain operation consumed by the renderer.
 */
public fun Matrix3x3F64.prepareTransformedInversePathGeometryF32(
    sourcePathF32: PathF32,
    styleF64: PathStrokeStyleF64?,
    mode: InversePathDrawMode,
    domainI32: RectI32,
    policyF64: PathStrokePolicyF64,
    frameWorkUsageBeforeI64: PathStrokeWorkUsageI64 = PathStrokeWorkUsageI64(),
): InversePathPreparationResult {
    if (!isFinite()) return InversePathPreparationResult.InvalidScene(PathStrokeInvalidSceneReason.NonFiniteInput)

    return when (classifyPathTransform()) {
        PathTransformClass.Identity -> prepareIdentityInversePathGeometryF32(
            sourcePathF32, styleF64, mode, domainI32, policyF64, frameWorkUsageBeforeI64,
        )

        PathTransformClass.AxisAlignedAffine,
        PathTransformClass.GeneralAffine,
        -> prepareAffineInversePathGeometryF32(
            sourcePathF32,
            styleF64,
            mode,
            domainI32,
            policyF64,
            frameWorkUsageBeforeI64,
        )

        PathTransformClass.Perspective -> preparePerspectiveInversePathGeometryF32(
            sourcePathF32,
            styleF64,
            mode,
            domainI32,
            policyF64,
            frameWorkUsageBeforeI64,
        )
    }
}

private fun Matrix3x3F64.prepareAffineInversePathGeometryF32(
    sourcePathF32: PathF32,
    styleF64: PathStrokeStyleF64?,
    mode: InversePathDrawMode,
    domainI32: RectI32,
    policyF64: PathStrokePolicyF64,
    frameWorkUsageBeforeI64: PathStrokeWorkUsageI64,
): InversePathPreparationResult = try {
    val ledgerI64 = PathAffineTransformWorkLedgerF64(frameWorkUsageBeforeI64, policyF64)
    val sourceInputF64 = PathFillInputF64.fromPathF32(
        sourcePathF32,
        ledgerI64::debitBeforeTransformWorkI64,
    )
    val finiteFillF64 = mapAffinePathFillInputF64(
        sourceInputF64,
        PathTransformWorkDebitI64(ledgerI64::debitBeforeTransformWorkI64),
    )
    prepareInverseWithOptionalDeviceOutlineF32(
        finiteFillF64 = finiteFillF64,
        sourcePathF32 = sourcePathF32,
        projectionF64 = toAffinePathStrokeProjectionF64(),
        styleF64 = styleF64,
        mode = mode,
        domainI32 = domainI32,
        policyF64 = policyF64,
        pathWorkUsageBeforeI64 = ledgerI64.pathSnapshotI64(),
        frameWorkUsageBeforeI64 = ledgerI64.frameSnapshotI64(),
    )
} catch (abort: PathAffineTransformWorkAbort) {
    InversePathPreparationResult.ResourceLimitExceeded(abort.reason)
} catch (_: IllegalArgumentException) {
    InversePathPreparationResult.InvalidScene(PathStrokeInvalidSceneReason.NonFiniteInput)
}

private fun Matrix3x3F64.prepareIdentityInversePathGeometryF32(
    sourcePathF32: PathF32,
    styleF64: PathStrokeStyleF64?,
    mode: InversePathDrawMode,
    domainI32: RectI32,
    policyF64: PathStrokePolicyF64,
    frameWorkUsageBeforeI64: PathStrokeWorkUsageI64,
): InversePathPreparationResult = try {
    val ledgerI64 = PathAffineTransformWorkLedgerF64(frameWorkUsageBeforeI64, policyF64)
    val sourceInputF64 = PathFillInputF64.fromPathF32(sourcePathF32, ledgerI64::debitBeforeTransformWorkI64)
    prepareInverseWithOptionalDeviceOutlineF32(
        sourceInputF64, sourcePathF32, toAffinePathStrokeProjectionF64(), styleF64, mode, domainI32, policyF64,
        ledgerI64.pathSnapshotI64(), ledgerI64.frameSnapshotI64(),
    )
} catch (abort: PathAffineTransformWorkAbort) {
    InversePathPreparationResult.ResourceLimitExceeded(abort.reason)
}

private fun Matrix3x3F64.preparePerspectiveInversePathGeometryF32(
    sourcePathF32: PathF32,
    styleF64: PathStrokeStyleF64?,
    mode: InversePathDrawMode,
    domainI32: RectI32,
    policyF64: PathStrokePolicyF64,
    frameWorkUsageBeforeI64: PathStrokeWorkUsageI64,
): InversePathPreparationResult = when (
    val projectedF64 = prepareProjectedPathFillInputF64(
        path = sourcePathF32,
        policyF64 = PathFillFlatteningPolicyF64(policyF64.maximumSagittaErrorF64),
        workPolicyF64 = policyF64,
        frameWorkUsageBeforeI64 = frameWorkUsageBeforeI64,
    )
) {
    is PathProjectivePreparationResult.Ready -> {
        prepareInverseWithOptionalDeviceOutlineF32(
            finiteFillF64 = projectedF64.inputF64,
            sourcePathF32 = sourcePathF32,
            projectionF64 = toPathStrokeProjectionF64(),
            styleF64 = styleF64,
            mode = mode,
            domainI32 = domainI32,
            policyF64 = policyF64,
            frameWorkUsageBeforeI64 = projectedF64.frameWorkUsageAfterI64,
            pathWorkUsageBeforeI64 = projectedF64.pathWorkUsageAfterI64,
        )
    }

    is PathProjectivePreparationResult.Empty -> prepareProjectedEmptyInversePathGeometryF32(
        sourceFillRule = sourcePathF32.fillRule,
        styleF64 = styleF64,
        mode = mode,
        domainI32 = domainI32,
        policyF64 = policyF64,
        pathWorkUsageBeforeI64 = projectedF64.pathWorkUsageAfterI64,
        frameWorkUsageBeforeI64 = projectedF64.frameWorkUsageAfterI64,
    )

    is PathProjectivePreparationResult.InvalidScene -> InversePathPreparationResult.InvalidScene(
        PathStrokeInvalidSceneReason.NonFiniteInput,
    )

    is PathProjectivePreparationResult.ResourceLimitExceeded -> InversePathPreparationResult.ResourceLimitExceeded(
        projectedF64.reason.toPathStrokeResourceLimitReason(),
    )
}

private fun prepareProjectedEmptyInversePathGeometryF32(
    sourceFillRule: org.graphiks.math.geometry.FillRule,
    styleF64: PathStrokeStyleF64?,
    mode: InversePathDrawMode,
    domainI32: RectI32,
    policyF64: PathStrokePolicyF64,
    pathWorkUsageBeforeI64: PathStrokeWorkUsageI64,
    frameWorkUsageBeforeI64: PathStrokeWorkUsageI64,
): InversePathPreparationResult = try {
    val ledgerI64 = PathAffineTransformWorkLedgerF64(
        frameWorkUsageBeforeI64,
        policyF64,
        pathWorkUsageBeforeI64,
    )
    ledgerI64.debitBeforeTransformWorkI64(PathStrokeWorkUsageI64(snapshotByteCountI64 = 16L))
    val emptyFillF64 = PathFillInputF64.of(sourceFillRule, emptyList())
    prepareInversePathGeometryF32(
        finiteFillF64 = emptyFillF64,
        deviceStrokeOutlineF64 = null,
        styleF64 = styleF64,
        mode = mode,
        domainI32 = domainI32,
        policyF64 = policyF64,
        frameWorkUsageBeforeI64 = ledgerI64.frameSnapshotI64(),
        pathWorkUsageBeforeI64 = ledgerI64.pathSnapshotI64(),
    )
} catch (abort: PathAffineTransformWorkAbort) {
    InversePathPreparationResult.ResourceLimitExceeded(abort.reason)
}

private fun prepareInverseWithOptionalDeviceOutlineF32(
    finiteFillF64: PathFillInputF64,
    sourcePathF32: PathF32,
    projectionF64: PathStrokeProjectionF64,
    styleF64: PathStrokeStyleF64?,
    mode: InversePathDrawMode,
    domainI32: RectI32,
    policyF64: PathStrokePolicyF64,
    pathWorkUsageBeforeI64: PathStrokeWorkUsageI64,
    frameWorkUsageBeforeI64: PathStrokeWorkUsageI64,
): InversePathPreparationResult {
    if (mode == InversePathDrawMode.Fill) {
        return prepareInversePathGeometryF32(
            finiteFillF64, null, styleF64, mode, domainI32, policyF64,
            frameWorkUsageBeforeI64, pathWorkUsageBeforeI64,
        )
    }
    val style = styleF64 ?: return InversePathPreparationResult.InvalidScene(PathStrokeInvalidSceneReason.InvalidStyle)
    return when (
        val outline = preparePathStrokeDeviceOutlineF64(
            sourcePathF32, style, projectionF64, policyF64, pathWorkUsageBeforeI64, frameWorkUsageBeforeI64,
        )
    ) {
        is PathStrokeDeviceOutlinePreparationResult.Ready -> prepareInversePathGeometryF32(
            finiteFillF64, outline.deviceOutlineF64, style, mode, domainI32, policyF64,
            outline.frameWorkUsageAfterI64, outline.pathWorkUsageI64,
        )

        is PathStrokeDeviceOutlinePreparationResult.Empty -> prepareInversePathGeometryF32(
            finiteFillF64, null, style, mode, domainI32, policyF64,
            outline.frameWorkUsageAfterI64, outline.pathWorkUsageI64,
        )

        is PathStrokeDeviceOutlinePreparationResult.InvalidScene ->
            InversePathPreparationResult.InvalidScene(outline.reason)

        is PathStrokeDeviceOutlinePreparationResult.ResourceLimitExceeded ->
            InversePathPreparationResult.ResourceLimitExceeded(outline.reason)
    }
}


private fun Matrix3x3F64.prepareAffinePathFillGeometryF32(
    path: PathF32,
    fillPolicyF64: PathFillFlatteningPolicyF64,
    strokePolicyF64: PathStrokePolicyF64,
    frameWorkUsageBeforeI64: PathStrokeWorkUsageI64,
): PathTransformedFillPreparationResult = try {
    val ledgerI64 = PathAffineTransformWorkLedgerF64(frameWorkUsageBeforeI64, strokePolicyF64)
    val mappedInputF64 = mapAffinePathFillInputF64(
        inputF64 = PathFillInputF64.fromPathF32(path),
        debitI64 = PathTransformWorkDebitI64(ledgerI64::debitBeforeTransformWorkI64),
    )
    preparePathFillGeometryWithStrokeWorkF32(
        inputF64 = mappedInputF64,
        fillPolicyF64 = fillPolicyF64,
        strokePolicyF64 = strokePolicyF64,
        pathWorkUsageBeforeI64 = ledgerI64.pathSnapshotI64(),
        frameWorkUsageBeforeI64 = ledgerI64.frameSnapshotI64(),
    ).toTransformedFillPreparationResult()
} catch (abort: PathAffineTransformWorkAbort) {
    PathTransformedFillPreparationResult.ResourceLimitExceeded(
        PathTransformedFillResourceLimitReason.Geometry(abort.reason),
    )
} catch (_: IllegalArgumentException) {
    PathTransformedFillPreparationResult.InvalidScene(
        PathTransformedFillInvalidSceneReason.Fill(PathFillInvalidSceneReason.NonFiniteInput),
    )
}

private fun Matrix3x3F64.preparePerspectivePathFillGeometryF32(
    path: PathF32,
    fillPolicyF64: PathFillFlatteningPolicyF64,
    strokePolicyF64: PathStrokePolicyF64,
    frameWorkUsageBeforeI64: PathStrokeWorkUsageI64,
): PathTransformedFillPreparationResult = when (
    val projectedF64 = prepareProjectedPathFillInputF64(
        path = path,
        policyF64 = fillPolicyF64,
        workPolicyF64 = strokePolicyF64,
        frameWorkUsageBeforeI64 = frameWorkUsageBeforeI64,
    )
) {
    is PathProjectivePreparationResult.Ready -> preparePathFillGeometryWithStrokeWorkF32(
        inputF64 = projectedF64.inputF64,
        fillPolicyF64 = fillPolicyF64,
        strokePolicyF64 = strokePolicyF64,
        pathWorkUsageBeforeI64 = projectedF64.pathWorkUsageAfterI64,
        frameWorkUsageBeforeI64 = projectedF64.frameWorkUsageAfterI64,
    ).toTransformedFillPreparationResult()

    is PathProjectivePreparationResult.Empty -> PathTransformedFillPreparationResult.Empty(
        pathWorkUsageI64 = projectedF64.pathWorkUsageAfterI64,
        frameWorkUsageAfterI64 = projectedF64.frameWorkUsageAfterI64,
    )

    is PathProjectivePreparationResult.InvalidScene -> PathTransformedFillPreparationResult.InvalidScene(
        PathTransformedFillInvalidSceneReason.Projective(projectedF64.reason),
    )

    is PathProjectivePreparationResult.ResourceLimitExceeded ->
        PathTransformedFillPreparationResult.ResourceLimitExceeded(
            PathTransformedFillResourceLimitReason.Projective(projectedF64.reason),
        )
}

private fun Matrix3x3F64.preparePerspectivePathStrokeGeometryF32(
    path: PathF32,
    inputF64: PathFillInputF64,
    styleF64: PathStrokeStyleF64,
    mode: PathStrokeDrawMode,
    policyF64: PathStrokePolicyF64,
    frameWorkUsageBeforeI64: PathStrokeWorkUsageI64,
): PathStrokePreparationResult {
    if (mode == PathStrokeDrawMode.Stroke) {
        return prepareProjectedPathStrokeGeometryF32(
            inputF64 = inputF64,
            styleF64 = styleF64,
            mode = mode,
            projectionF64 = toPathStrokeProjectionF64(),
            policyF64 = policyF64,
            frameWorkUsageBeforeI64 = frameWorkUsageBeforeI64,
        )
    }
    return when (
        val projectedF64 = prepareProjectedPathFillInputF64(
            path = path,
            policyF64 = PathFillFlatteningPolicyF64(maximumSagittaErrorF64 = policyF64.maximumSagittaErrorF64),
            workPolicyF64 = policyF64,
            frameWorkUsageBeforeI64 = frameWorkUsageBeforeI64,
        )
    ) {
        is PathProjectivePreparationResult.Ready -> prepareProjectedPathStrokeGeometryF32(
            inputF64 = inputF64,
            styleF64 = styleF64,
            mode = mode,
            projectionF64 = toPathStrokeProjectionF64(),
            policyF64 = policyF64,
            pathWorkUsageBeforeI64 = projectedF64.pathWorkUsageAfterI64,
            frameWorkUsageBeforeI64 = projectedF64.frameWorkUsageAfterI64,
            deviceFillInputF64 = projectedF64.inputF64,
        )

        is PathProjectivePreparationResult.Empty -> PathStrokePreparationResult.Empty(
            pathWorkUsageI64 = projectedF64.pathWorkUsageAfterI64,
            frameWorkUsageAfterI64 = projectedF64.frameWorkUsageAfterI64,
        )

        is PathProjectivePreparationResult.InvalidScene -> PathStrokePreparationResult.InvalidScene(
            when (projectedF64.reason) {
                PathProjectiveInvalidSceneReason.PerspectiveHorizonCrossing ->
                    PathStrokeInvalidSceneReason.ProjectionHorizonCrossing

                PathProjectiveInvalidSceneReason.NonFiniteMatrix,
                PathProjectiveInvalidSceneReason.NonFiniteProjection,
                -> PathStrokeInvalidSceneReason.NonFiniteInput
            },
        )

        is PathProjectivePreparationResult.ResourceLimitExceeded ->
            PathStrokePreparationResult.ResourceLimitExceeded(projectedF64.reason.toPathStrokeResourceLimitReason())
    }
}

private fun PathFillWithStrokeWorkPreparationResult.toTransformedFillPreparationResult():
    PathTransformedFillPreparationResult = when (this) {
    is PathFillWithStrokeWorkPreparationResult.Ready -> PathTransformedFillPreparationResult.Ready(
        geometryF32 = geometryF32,
        pathWorkUsageI64 = pathWorkUsageI64,
        frameWorkUsageAfterI64 = frameWorkUsageAfterI64,
    )

    is PathFillWithStrokeWorkPreparationResult.Empty -> PathTransformedFillPreparationResult.Empty(
        pathWorkUsageI64 = pathWorkUsageI64,
        frameWorkUsageAfterI64 = frameWorkUsageAfterI64,
    )

    is PathFillWithStrokeWorkPreparationResult.InvalidScene -> PathTransformedFillPreparationResult.InvalidScene(
        PathTransformedFillInvalidSceneReason.Fill(reason),
    )

    is PathFillWithStrokeWorkPreparationResult.ResourceLimitExceeded ->
        PathTransformedFillPreparationResult.ResourceLimitExceeded(
            PathTransformedFillResourceLimitReason.Geometry(reason),
        )
}

private fun PathProjectiveResourceLimitReason.toPathStrokeResourceLimitReason(): PathStrokeResourceLimitReason = when (this) {
    PathProjectiveResourceLimitReason.FlatteningDidNotConverge -> PathStrokeResourceLimitReason.FlatteningDidNotConverge
    PathProjectiveResourceLimitReason.PathWorkLimit -> PathStrokeResourceLimitReason.PathWorkLimit
    PathProjectiveResourceLimitReason.FrameWorkLimit -> PathStrokeResourceLimitReason.FrameWorkLimit
    PathProjectiveResourceLimitReason.SnapshotByteLimit -> PathStrokeResourceLimitReason.SnapshotByteLimit
    PathProjectiveResourceLimitReason.RasterBoundsOverflow -> PathStrokeResourceLimitReason.RasterBoundsOverflow
}

private class PathAffineTransformWorkAbort(
    val reason: PathStrokeResourceLimitReason,
) : RuntimeException()

private class PathAffineTransformWorkLedgerF64(
    frameWorkUsageBeforeI64: PathStrokeWorkUsageI64,
    private val policyF64: PathStrokePolicyF64,
    pathWorkUsageBeforeI64: PathStrokeWorkUsageI64 = PathStrokeWorkUsageI64(),
) {
    private var pathWorkUsageI64: PathStrokeWorkUsageI64 = pathWorkUsageBeforeI64
    private var frameWorkUsageI64: PathStrokeWorkUsageI64 = frameWorkUsageBeforeI64

    init {
        requireWithinLimits(pathWorkUsageI64, isPath = true)
        requireWithinLimits(frameWorkUsageI64, isPath = false)
    }

    fun debitBeforeTransformWorkI64(deltaI64: PathStrokeWorkUsageI64) {
        val nextPathWorkUsageI64 = addUsageI64(pathWorkUsageI64, deltaI64)
        val nextFrameWorkUsageI64 = addUsageI64(frameWorkUsageI64, deltaI64)
        requireWithinLimits(nextPathWorkUsageI64, isPath = true)
        requireWithinLimits(nextFrameWorkUsageI64, isPath = false)
        pathWorkUsageI64 = nextPathWorkUsageI64
        frameWorkUsageI64 = nextFrameWorkUsageI64
    }

    fun pathSnapshotI64(): PathStrokeWorkUsageI64 = pathWorkUsageI64

    fun frameSnapshotI64(): PathStrokeWorkUsageI64 = frameWorkUsageI64

    private fun addUsageI64(
        firstI64: PathStrokeWorkUsageI64,
        secondI64: PathStrokeWorkUsageI64,
    ): PathStrokeWorkUsageI64 = PathStrokeWorkUsageI64(
        attemptedGeometryUnitCountI64 = checkedAddI64(
            firstI64.attemptedGeometryUnitCountI64,
            secondI64.attemptedGeometryUnitCountI64,
        ),
        emittedVertexCountI64 = checkedAddI64(firstI64.emittedVertexCountI64, secondI64.emittedVertexCountI64),
        emittedIndexCountI64 = checkedAddI64(firstI64.emittedIndexCountI64, secondI64.emittedIndexCountI64),
        snapshotByteCountI64 = checkedAddI64(firstI64.snapshotByteCountI64, secondI64.snapshotByteCountI64),
    )

    private fun checkedAddI64(firstI64: Long, secondI64: Long): Long {
        if (firstI64 > Long.MAX_VALUE - secondI64) {
            throw PathAffineTransformWorkAbort(PathStrokeResourceLimitReason.HostSizeOverflow)
        }
        return firstI64 + secondI64
    }

    private fun requireWithinLimits(usageI64: PathStrokeWorkUsageI64, isPath: Boolean) {
        val limitsI32 = policyF64.limitsI32
        val limitsI64 = policyF64.limitsI64
        if (usageI64.attemptedGeometryUnitCountI64 > if (isPath) {
                limitsI32.maxAttemptedGeometryUnitsPerPathI32.toLong()
            } else {
                limitsI32.maxAttemptedGeometryUnitsPerFrameI32.toLong()
            }
        ) {
            throw PathAffineTransformWorkAbort(
                if (isPath) PathStrokeResourceLimitReason.PathWorkLimit else PathStrokeResourceLimitReason.FrameWorkLimit,
            )
        }
        if (usageI64.emittedVertexCountI64 > if (isPath) {
                limitsI32.maxEmittedVertexCountPerPathI32.toLong()
            } else {
                limitsI32.maxEmittedVertexCountPerFrameI32.toLong()
            }
        ) throw PathAffineTransformWorkAbort(PathStrokeResourceLimitReason.VertexLimit)
        if (usageI64.emittedIndexCountI64 > if (isPath) {
                limitsI32.maxEmittedIndexCountPerPathI32.toLong()
            } else {
                limitsI32.maxEmittedIndexCountPerFrameI32.toLong()
            }
        ) throw PathAffineTransformWorkAbort(PathStrokeResourceLimitReason.IndexLimit)
        if (usageI64.snapshotByteCountI64 > if (isPath) {
                limitsI64.maxSnapshotByteCountPerPathI64
            } else {
                limitsI64.maxSnapshotByteCountPerFrameI64
            }
        ) throw PathAffineTransformWorkAbort(PathStrokeResourceLimitReason.SnapshotByteLimit)
    }
}
