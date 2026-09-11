package org.graphiks.kanvas.gpu.plan

public sealed interface GradientNumericDomainProofV1 {
    public data object ProvenFinite : GradientNumericDomainProofV1
    public data class Unbounded(public val diagnosticCode: String) : GradientNumericDomainProofV1
}

/** Typed, executable source graph. Uniform values and concrete stop counts never shape code. */
public sealed interface GradientNumericOperationGraphV1 {
    public val root: Node
    public val contractId: String get() = "WgslFloatEnvelopeV1"
    public val domainProof: GradientNumericDomainProofV1
    /** Gamma(64) with a full-ULP unit bound: covers all eight-term sum/product schedules. */
    public val reassociationRoundoffFactorF64: Double get() = (64.0 / 8_388_608.0) / (1.0 - 64.0 / 8_388_608.0)
    public enum class ValueType { LocalPointF32, UniformScalarF32, UniformFlag, StopRangeU32,
        ScalarF32, IndexU32, ValidityFlag, SrgbaStraightF32 }
    public enum class Operation {
        INPUT_LOCAL_POINT_F32, INPUT_UNIFORM_F32, INPUT_UNIFORM_FLAG, INPUT_STOP_RANGE_U32,
        LOAD_STOP_POSITION_F32, LOAD_STOP_COLOR_SRGBA_F32, ADD_F32, SUB_F32, MUL_F32, DIV_F32,
        SQRT_F32, ATAN2_F32, FLOOR_F32, ABS_F32, MAX_F32, COMPARE_F32, SELECT,
        UPPER_BOUND_STOPS_V1, INTERPOLATE_SRGBA_STRAIGHT_F32,
    }
    public enum class Input { X, Y, START_X, START_Y, END_X, END_Y, CENTER_X, CENTER_Y, RADIUS, ZERO, ONE, DEGENERATE, STOPS, PROBE }
    public enum class Schedule { RoundedF32, FusedMultiplyAdd, ReassociatedSumOfProducts }
    public class Node internal constructor(
        public val operation: Operation,
        public val type: ValueType,
        inputs: List<Node> = emptyList(),
        public val input: Input? = null,
        public val relativeIndexI32: Int = 0,
        public val loopBody: UpperBoundBody? = null,
        public val lessOrEqual: Boolean = false,
    ) {
        public val inputs: List<Node> = immutableList(inputs)
        public val inputType: ValueType? = when (operation) {
            Operation.INPUT_LOCAL_POINT_F32 -> ValueType.LocalPointF32
            Operation.INPUT_UNIFORM_F32 -> ValueType.UniformScalarF32
            Operation.INPUT_UNIFORM_FLAG -> ValueType.UniformFlag
            Operation.INPUT_STOP_RANGE_U32 -> if (input == Input.STOPS) ValueType.StopRangeU32 else ValueType.IndexU32
            else -> null
        }
        public val componentI32: Int? = if (operation == Operation.INPUT_LOCAL_POINT_F32) {
            if (input == Input.X) 0 else 1
        } else null
        /** INTERPOLATE owns u=clamp((t-low)/(high-low),0,1); duplicate/end intervals return right. */
        public val clampInterpolationWeightToUnitInterval: Boolean = operation == Operation.INTERPOLATE_SRGBA_STRAIGHT_F32
        public val maxIntermediateMagnitudeF64: Double = if (clampInterpolationWeightToUnitInterval) 1e38 else 1e27
        /** Per-operation finite domain includes all FMA and sum-of-products schedules. */
        public val maxMagnitudeF64: Double = when (operation) {
            Operation.INPUT_LOCAL_POINT_F32, Operation.INPUT_UNIFORM_F32 -> 1e8
            Operation.SUB_F32 -> 2.000001e8
            Operation.MUL_F32 -> 8.0001e16
            Operation.ADD_F32 -> 8.0001e16
            Operation.DIV_F32 -> 1e27
            Operation.INTERPOLATE_SRGBA_STRAIGHT_F32 -> 1.00001
            else -> 1e27
        }
        public val minimumPositiveDenominatorF64: Double = if (operation == Operation.INTERPOLATE_SRGBA_STRAIGHT_F32)
            java.lang.Float.MIN_NORMAL.toDouble() else 9e-10
        public val schedules: Set<Schedule> = immutableSet(when (operation) {
            Operation.ADD_F32, Operation.INTERPOLATE_SRGBA_STRAIGHT_F32 -> Schedule.entries.toSet()
            else -> setOf(Schedule.RoundedF32)
        })
        init {
            fun requireTypes(vararg types: ValueType) { require(inputs.map { it.type } == types.toList()) }
            when (operation) {
                Operation.INPUT_LOCAL_POINT_F32, Operation.INPUT_UNIFORM_F32,
                Operation.INPUT_UNIFORM_FLAG, Operation.INPUT_STOP_RANGE_U32 -> require(inputs.isEmpty() && input != null)
                Operation.ADD_F32, Operation.SUB_F32, Operation.MUL_F32, Operation.DIV_F32,
                Operation.ATAN2_F32, Operation.MAX_F32, Operation.COMPARE_F32 -> requireTypes(ValueType.ScalarF32, ValueType.ScalarF32)
                Operation.SQRT_F32, Operation.FLOOR_F32, Operation.ABS_F32 -> requireTypes(ValueType.ScalarF32)
                Operation.SELECT -> require(inputs.size == 3 && inputs[0].type == type && inputs[1].type == type && inputs[2].type == ValueType.ValidityFlag)
                Operation.LOAD_STOP_POSITION_F32, Operation.LOAD_STOP_COLOR_SRGBA_F32 -> requireTypes(ValueType.StopRangeU32, ValueType.IndexU32)
                Operation.UPPER_BOUND_STOPS_V1 -> { requireTypes(ValueType.StopRangeU32, ValueType.ScalarF32, ValueType.ScalarF32); require(loopBody != null) }
                Operation.INTERPOLATE_SRGBA_STRAIGHT_F32 -> requireTypes(ValueType.SrgbaStraightF32, ValueType.SrgbaStraightF32,
                    ValueType.ScalarF32, ValueType.ScalarF32, ValueType.ScalarF32)
            }
        }
    }
    /** Search interval [low,high) strictly shrinks; at most count iterations, no unchecked load. */
    public class UpperBoundBody internal constructor(public val probe: Node, public val position: Node,
        public val comparison: Node) {
        public val countBoundU32: UInt = 65_538u
        public val comparisonIsLessOrEqual: Boolean = true
        public val midpointUsesDifference: Boolean = true
    }
    public class Linear internal constructor(override val root: Node,
        override val domainProof: GradientNumericDomainProofV1) : GradientNumericOperationGraphV1
    public class Radial internal constructor(override val root: Node,
        override val domainProof: GradientNumericDomainProofV1) : GradientNumericOperationGraphV1

    public companion object {
        private fun input(slot: Input): Node = Node(if (slot in listOf(Input.X, Input.Y)) Operation.INPUT_LOCAL_POINT_F32
                else Operation.INPUT_UNIFORM_F32, ValueType.ScalarF32, input = slot)
        private fun scalar(op: Operation, vararg args: Node): Node = Node(op, ValueType.ScalarF32, args.toList())

        public fun linear(): GradientNumericOperationGraphV1 {
            val one = input(Input.ONE)
            val dx = scalar(Operation.SUB_F32, input(Input.END_X), input(Input.START_X))
            val dy = scalar(Operation.SUB_F32, input(Input.END_Y), input(Input.START_Y))
            val px = scalar(Operation.SUB_F32, input(Input.X), input(Input.START_X))
            val py = scalar(Operation.SUB_F32, input(Input.Y), input(Input.START_Y))
            val dot = scalar(Operation.ADD_F32, scalar(Operation.MUL_F32, px, dx), scalar(Operation.MUL_F32, py, dy))
            val length = scalar(Operation.ADD_F32, scalar(Operation.MUL_F32, dx, dx), scalar(Operation.MUL_F32, dy, dy))
            val degenerate = Node(Operation.INPUT_UNIFORM_FLAG, ValueType.ValidityFlag, input = Input.DEGENERATE)
            val safeLength = scalar(Operation.SELECT, length, one, degenerate)
            val numerator = scalar(Operation.SELECT, dot, one, degenerate)
            return Linear(clampStops(numerator, safeLength),
                GradientNumericDomainProofV1.Unbounded(W5cPlanDiagnostics.NumericDomainUnbounded))
        }

        public fun radial(): GradientNumericOperationGraphV1 {
            val zero = input(Input.ZERO)
            val one = input(Input.ONE)
            val dx = scalar(Operation.SUB_F32, input(Input.X), input(Input.CENTER_X))
            val dy = scalar(Operation.SUB_F32, input(Input.Y), input(Input.CENTER_Y))
            val absX = scalar(Operation.ABS_F32, dx)
            val absY = scalar(Operation.ABS_F32, dy)
            fun isZero(value: Node): Node = Node(Operation.COMPARE_F32, ValueType.ValidityFlag,
                listOf(value, zero), lessOrEqual = true)
            // Exact axis identities preserve equality without assuming sqrt or division exact.
            // Clamp also covers cancellation in the declared reassociated square expansion.
            val sum = scalar(Operation.ADD_F32, scalar(Operation.MUL_F32, dx, dx), scalar(Operation.MUL_F32, dy, dy))
            val length = scalar(Operation.SQRT_F32, scalar(Operation.MAX_F32, sum, zero))
            val distance = scalar(Operation.SELECT,
                scalar(Operation.SELECT, length, absY, isZero(absX)), absX, isZero(absY))
            val degenerate = Node(Operation.INPUT_UNIFORM_FLAG, ValueType.ValidityFlag, input = Input.DEGENERATE)
            val radius = scalar(Operation.SELECT, input(Input.RADIUS), one, degenerate)
            val numerator = scalar(Operation.SELECT, distance, one, degenerate)
            return Radial(clampStops(numerator, radius),
                GradientNumericDomainProofV1.Unbounded(W5cPlanDiagnostics.NumericDomainUnbounded))
        }

        private fun clampStops(numerator: Node, safeLength: Node): Node {
            val zero = input(Input.ZERO)
            val one = input(Input.ONE)
            val projection = scalar(Operation.DIV_F32, numerator, safeLength)
            val selected = projection
            val positive = scalar(Operation.MAX_F32, selected, zero)
            val exceeds = Node(Operation.COMPARE_F32, ValueType.ValidityFlag, listOf(one, positive))
            val clamped = scalar(Operation.SELECT, positive, one, exceeds)
            val range = Node(Operation.INPUT_STOP_RANGE_U32, ValueType.StopRangeU32, input = Input.STOPS)
            val probe = Node(Operation.INPUT_STOP_RANGE_U32, ValueType.IndexU32, input = Input.PROBE)
            val position = Node(Operation.LOAD_STOP_POSITION_F32, ValueType.ScalarF32, listOf(range, probe))
            val scaledPosition = scalar(Operation.MUL_F32, position, safeLength)
            val comparison = Node(Operation.COMPARE_F32, ValueType.ValidityFlag, listOf(scaledPosition, numerator), lessOrEqual = true)
            val upper = Node(Operation.UPPER_BOUND_STOPS_V1, ValueType.IndexU32, listOf(range, numerator, safeLength),
                loopBody = UpperBoundBody(probe, position, comparison))
            fun load(op: Operation, offsetI32: Int) = Node(op,
                if (op == Operation.LOAD_STOP_POSITION_F32) ValueType.ScalarF32 else ValueType.SrgbaStraightF32,
                listOf(range, upper), relativeIndexI32 = offsetI32)
            val interpolated = Node(Operation.INTERPOLATE_SRGBA_STRAIGHT_F32, ValueType.SrgbaStraightF32, listOf(
                load(Operation.LOAD_STOP_COLOR_SRGBA_F32, -1), load(Operation.LOAD_STOP_COLOR_SRGBA_F32, 0),
                load(Operation.LOAD_STOP_POSITION_F32, -1), load(Operation.LOAD_STOP_POSITION_F32, 0), clamped,
            ))
            val first = Node(Operation.LOAD_STOP_COLOR_SRGBA_F32, ValueType.SrgbaStraightF32, listOf(range,
                Node(Operation.INPUT_STOP_RANGE_U32, ValueType.IndexU32, input = Input.ZERO)))
            val below = Node(Operation.COMPARE_F32, ValueType.ValidityFlag, listOf(numerator, zero))
            return Node(Operation.SELECT, ValueType.SrgbaStraightF32, listOf(interpolated, first, below))
        }
    }
}

/** Checks the actual draw domain against every executable node, including search-loop arithmetic. */
internal fun GradientNumericOperationGraphV1.proveLinearDomainV1(
    localMagnitudeF64: Double,
    uniformMagnitudeF64: Double,
    degeneracy: LinearGradientDegeneracyV1,
    stops: List<GradientStopPlanV1>,
    startF32: org.graphiks.math.geometry.Point2F32,
    endF32: org.graphiks.math.geometry.Point2F32,
): GradientNumericDomainProofV1 {
    // The expanded form may cancel large products even when (end-start)^2 looks harmless.
    val xSumF64 = kotlin.math.abs(startF32.x.toDouble()) + kotlin.math.abs(endF32.x.toDouble())
    val ySumF64 = kotlin.math.abs(startF32.y.toDouble()) + kotlin.math.abs(endF32.y.toDouble())
    val dxF64 = endF32.x.toDouble() - startF32.x.toDouble()
    val dyF64 = endF32.y.toDouble() - startF32.y.toDouble()
    val lengthErrorF64 = (xSumF64 * xSumF64 + ySumF64 * ySumF64) * reassociationRoundoffFactorF64 +
        64.0 * java.lang.Float.MIN_NORMAL
    val minimumLengthF64 = if (degeneracy.degenerate) 1.0 else dxF64 * dxF64 + dyF64 * dyF64 - lengthErrorF64
    return proveGradientDomainV1(localMagnitudeF64, uniformMagnitudeF64, minimumLengthF64, stops)
}

internal fun GradientNumericOperationGraphV1.proveRadialDomainV1(
    localMagnitudeF64: Double, uniformMagnitudeF64: Double,
    degeneracy: RadialGradientDegeneracyV1, stops: List<GradientStopPlanV1>,
): GradientNumericDomainProofV1 = proveGradientDomainV1(localMagnitudeF64, uniformMagnitudeF64,
    if (degeneracy.radialDegenerate) 1.0 else degeneracy.radialRadiusF32.toDouble(), stops)

private fun GradientNumericOperationGraphV1.proveGradientDomainV1(
    localMagnitudeF64: Double, uniformMagnitudeF64: Double, minimumLengthF64: Double,
    stops: List<GradientStopPlanV1>,
): GradientNumericDomainProofV1 {
    val bounds = mutableMapOf<GradientNumericOperationGraphV1.Node, Double>()
    val minimumGapF64 = stops.zipWithNext().mapNotNull { (a, b) ->
        (b.positionF32 - a.positionF32).takeIf { it > 0f }?.toDouble()
    }.minOrNull() ?: 1.0
    var finite = true
    fun bound(node: GradientNumericOperationGraphV1.Node): Double = bounds.getOrPut(node) {
        val inputs = node.inputs.map(::bound)
        fun rounded(valueF64: Double): Double = valueF64 * (1.0 + reassociationRoundoffFactorF64) + java.lang.Float.MIN_NORMAL
        val valueF64 = when (node.operation) {
            GradientNumericOperationGraphV1.Operation.INPUT_LOCAL_POINT_F32 -> localMagnitudeF64
            GradientNumericOperationGraphV1.Operation.INPUT_UNIFORM_F32 -> when (node.input) {
                GradientNumericOperationGraphV1.Input.ZERO -> 0.0
                GradientNumericOperationGraphV1.Input.ONE -> 1.0
                else -> uniformMagnitudeF64
            }
            GradientNumericOperationGraphV1.Operation.ADD_F32,
            GradientNumericOperationGraphV1.Operation.SUB_F32 -> rounded(inputs.sum())
            GradientNumericOperationGraphV1.Operation.MUL_F32 -> rounded(inputs[0] * inputs[1])
            GradientNumericOperationGraphV1.Operation.ABS_F32 -> inputs[0]
            GradientNumericOperationGraphV1.Operation.SQRT_F32 -> {
                // Radial square root always receives max(sum,0); all axis and generic
                // paths are visited even though select may discard one at runtime.
                finite = finite && node.inputs.single().operation == GradientNumericOperationGraphV1.Operation.MAX_F32
                rounded(kotlin.math.sqrt(inputs[0]))
            }
            GradientNumericOperationGraphV1.Operation.DIV_F32 -> {
                finite = finite && minimumLengthF64 >= node.minimumPositiveDenominatorF64
                rounded(inputs[0] / minimumLengthF64)
            }
            GradientNumericOperationGraphV1.Operation.SELECT -> {
                val condition = node.inputs[2]
                // select(x,1,1<x) is an explicit graph clamp, not an emitter-only optimization.
                if (condition.operation == GradientNumericOperationGraphV1.Operation.COMPARE_F32 &&
                    condition.inputs[0] === node.inputs[1] && condition.inputs[1] === node.inputs[0]) inputs[1]
                else maxOf(inputs[0], inputs[1])
            }
            GradientNumericOperationGraphV1.Operation.MAX_F32 -> inputs.max()
            GradientNumericOperationGraphV1.Operation.INTERPOLATE_SRGBA_STRAIGHT_F32 -> {
                finite = finite && node.clampInterpolationWeightToUnitInterval &&
                    minimumGapF64 >= node.minimumPositiveDenominatorF64 &&
                    rounded((inputs[4] + inputs[2]) / minimumGapF64) <= node.maxIntermediateMagnitudeF64
                1.000001
            }
            GradientNumericOperationGraphV1.Operation.UPPER_BOUND_STOPS_V1 -> {
                val body = requireNotNull(node.loopBody)
                finite = finite && stops.size.toUInt() <= body.countBoundU32 && minimumLengthF64 > 0.0
                bound(body.comparison)
                stops.size.toDouble()
            }
            GradientNumericOperationGraphV1.Operation.LOAD_STOP_POSITION_F32,
            GradientNumericOperationGraphV1.Operation.LOAD_STOP_COLOR_SRGBA_F32,
            GradientNumericOperationGraphV1.Operation.INPUT_UNIFORM_FLAG,
            GradientNumericOperationGraphV1.Operation.INPUT_STOP_RANGE_U32,
            GradientNumericOperationGraphV1.Operation.COMPARE_F32 -> 1.0
            else -> { finite = false; Double.POSITIVE_INFINITY }
        }
        finite = finite && valueF64.isFinite() && valueF64 <= node.maxMagnitudeF64
        valueF64
    }
    bound(root)
    return if (finite) GradientNumericDomainProofV1.ProvenFinite
        else GradientNumericDomainProofV1.Unbounded(W5cPlanDiagnostics.NumericDomainUnbounded)
}
