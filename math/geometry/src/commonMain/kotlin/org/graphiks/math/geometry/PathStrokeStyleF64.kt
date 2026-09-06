package org.graphiks.math.geometry

/** Selects the termination shape of an open stroked contour. */
public enum class PathStrokeCap {
    Butt,
    Round,
    Square,
}

/** Selects the shape used where two stroked contour segments meet. */
public enum class PathStrokeJoin {
    Miter,
    Round,
    Bevel,
}

/** Represents either a device-independent hairline or a finite stroke width. */
public sealed interface PathStrokeWidthF64 {
    public data object Hairline : PathStrokeWidthF64

    public data class Finite(public val valueF64: Double) : PathStrokeWidthF64 {
        init {
            require(valueF64.isFinite())
            require(valueF64 >= 0.0)
        }
    }
}

/** Immutable dash intervals and phase used by a stroked path. */
public class PathStrokeDashF64 private constructor(
    intervalsF64: DoubleArray,
    public val phaseF64: Double,
) {
    private val intervalsSnapshotF64: DoubleArray = intervalsF64.copyOf()

    public val intervalCountI32: Int
        get() = intervalsSnapshotF64.size

    public fun copyIntervalsF64(): DoubleArray = intervalsSnapshotF64.copyOf()

    public companion object {
        public fun of(intervalsF64: DoubleArray, phaseF64: Double): PathStrokeDashF64 {
            require(phaseF64.isFinite())
            require(intervalsF64.size % 2 == 0)
            require(intervalsF64.all { it.isFinite() && it >= 0.0 })

            var totalF64 = 0.0
            intervalsF64.forEach { intervalF64 ->
                totalF64 += intervalF64
            }
            require(totalF64.isFinite() && totalF64 > 0.0)

            return PathStrokeDashF64(intervalsF64, phaseF64)
        }
    }
}

/** Complete F64 stroke style used to expand a path in device space. */
public data class PathStrokeStyleF64(
    public val widthF64: PathStrokeWidthF64,
    public val cap: PathStrokeCap,
    public val join: PathStrokeJoin,
    public val miterLimitF64: Double,
    public val dashF64: PathStrokeDashF64? = null,
) {
    init {
        if (widthF64 is PathStrokeWidthF64.Finite) {
            require(widthF64.valueF64.isFinite())
            require(widthF64.valueF64 >= 0.0)
        }
        require(miterLimitF64.isFinite())
    }
}

/** Stable reason for rejecting an input scene before geometry is emitted. */
public enum class PathStrokeInvalidSceneReason {
    NonFiniteInput,
    InvalidStyle,
}

/** Stable reason for bounded stroke preparation to stop before an allocation. */
public enum class PathStrokeResourceLimitReason {
    FlatteningDidNotConverge,
    PathWorkLimit,
    FrameWorkLimit,
    VertexLimit,
    IndexLimit,
    SnapshotByteLimit,
    TopologyLimit,
    RasterBoundsOverflow,
    HostSizeOverflow,
}
