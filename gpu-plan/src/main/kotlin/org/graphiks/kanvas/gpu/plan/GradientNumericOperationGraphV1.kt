package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.gpu.plan.GradientNumericOperationGraphV1.Operation
import org.graphiks.kanvas.gpu.plan.GradientNumericOperationGraphV1.Input

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
    /** Root candidates may overflow; radius candidates are predicate-only and never NaN. */
    public enum class ValueType { LocalPointF32, UniformScalarF32, UniformFlag, StopRangeU32,
        ScalarF32, RootCandidateF32, RootRadiusCandidateF32, IndexU32, ValidityFlag, SrgbaStraightF32 }
    public enum class Operation {
        INPUT_LOCAL_POINT_F32, INPUT_UNIFORM_F32, INPUT_UNIFORM_FLAG, INPUT_STOP_RANGE_U32,
        LOAD_STOP_POSITION_F32, LOAD_STOP_COLOR_SRGBA_F32, ADD_F32, SUB_F32, MUL_F32, DIV_F32,
        SQRT_F32, ATAN2_F32, FLOOR_F32, ABS_F32, MAX_F32, COMPARE_F32, SELECT,
        FINITE_F32, AND_FLAG, OR_FLAG, VALIDITY_MASK,
        ROOT_DIV_F32, FINITE_ROOT_OR_ZERO_F32, ROOT_RADIUS_MUL_F32, ROOT_RADIUS_ADD_F32, ROOT_RADIUS_POSITIVE,
        UPPER_BOUND_STOPS_V1, INTERPOLATE_SRGBA_STRAIGHT_F32,
    }
    public enum class Input { X, Y, START_X, START_Y, END_X, END_Y, CENTER_X, CENTER_Y, RADIUS,
        START_DEGREES, END_DEGREES, SPAN_DEGREES, LEADING_SEGMENT, MIN_NORMAL, TWO_PI,
        QUARTER, HALF, THREE_QUARTERS, FULL_TURN_DEGREES, ZERO, ONE, TWO, FOUR, DEGENERATE, STOPS, PROBE,
        LINEAR_DX, LINEAR_DY, LINEAR_LEN2,
        CONICAL_DX, CONICAL_DY, CONICAL_START_RADIUS, CONICAL_END_RADIUS, CONICAL_DR, CONICAL_A,
        CONICAL_FULLY_DEGENERATE, CONICAL_CONCENTRIC, CONICAL_LINEAR_EQUATION, CONICAL_QUADRATIC,
        CONICAL_SHARED_RADIUS_ABOVE_EPSILON }
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
            Operation.INPUT_LOCAL_POINT_F32 -> 1e8
            Operation.INPUT_UNIFORM_F32 -> when (input) {
                Input.LINEAR_DX, Input.LINEAR_DY -> 2.000001e8
                Input.LINEAR_LEN2 -> 8.0001e16
                else -> 1e8
            }
            Operation.SUB_F32 -> 2.000001e8
            Operation.MUL_F32 -> 8.0001e16
            Operation.ADD_F32 -> 8.0001e16
            Operation.DIV_F32 -> 1e27
            Operation.INTERPOLATE_SRGBA_STRAIGHT_F32 -> 1.00001
            else -> 1e27
        }
        public val minimumPositiveDenominatorF64: Double = if (operation in setOf(Operation.INTERPOLATE_SRGBA_STRAIGHT_F32, Operation.ROOT_DIV_F32))
            java.lang.Float.MIN_NORMAL.toDouble() else 9e-10
        /** WGSL §15.7.4.1: requires normal finite operands; eager inputs are explicitly guarded. */
        public val accuracyUlpsF64: Double = when (operation) {
            Operation.ATAN2_F32 -> 4096.0
            Operation.DIV_F32, Operation.ROOT_DIV_F32 -> 2.5
            else -> 0.0
        }
        public val schedules: Set<Schedule> = immutableSet(when (operation) {
            Operation.ADD_F32, Operation.SUB_F32, Operation.MUL_F32, Operation.ROOT_RADIUS_ADD_F32,
            Operation.INTERPOLATE_SRGBA_STRAIGHT_F32 -> Schedule.entries.toSet()
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
                Operation.ROOT_DIV_F32 -> { requireTypes(ValueType.ScalarF32, ValueType.ScalarF32); require(type == ValueType.RootCandidateF32) }
                Operation.FINITE_F32 -> { requireTypes(ValueType.RootCandidateF32); require(type == ValueType.ValidityFlag) }
                Operation.FINITE_ROOT_OR_ZERO_F32 -> {
                    requireTypes(ValueType.RootCandidateF32, ValueType.ValidityFlag)
                    require(type == ValueType.ScalarF32 && inputs[1].operation == Operation.FINITE_F32 && inputs[1].inputs.single() === inputs[0])
                }
                Operation.ROOT_RADIUS_MUL_F32 -> { requireTypes(ValueType.ScalarF32, ValueType.ScalarF32); require(type == ValueType.RootRadiusCandidateF32) }
                Operation.ROOT_RADIUS_ADD_F32 -> { requireTypes(ValueType.RootRadiusCandidateF32, ValueType.ScalarF32); require(type == ValueType.RootRadiusCandidateF32) }
                Operation.ROOT_RADIUS_POSITIVE -> { requireTypes(ValueType.RootRadiusCandidateF32); require(type == ValueType.ValidityFlag) }
                Operation.AND_FLAG, Operation.OR_FLAG -> requireTypes(ValueType.ValidityFlag, ValueType.ValidityFlag)
                Operation.VALIDITY_MASK -> requireTypes(ValueType.SrgbaStraightF32, ValueType.ValidityFlag)
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
    public class Sweep internal constructor(override val root: Node,
        override val domainProof: GradientNumericDomainProofV1) : GradientNumericOperationGraphV1
    public class Conical internal constructor(override val root: Node,
        override val domainProof: GradientNumericDomainProofV1) : GradientNumericOperationGraphV1

    public companion object {
        private fun input(slot: Input): Node = Node(if (slot in listOf(Input.X, Input.Y)) Operation.INPUT_LOCAL_POINT_F32
                else Operation.INPUT_UNIFORM_F32, ValueType.ScalarF32, input = slot)
        private fun scalar(op: Operation, vararg args: Node): Node = Node(op, ValueType.ScalarF32, args.toList())

        public fun linear(tileGraph: GradientTileOperationGraphV2? = null): GradientNumericOperationGraphV1 {
            val one = input(Input.ONE)
            val dx = input(Input.LINEAR_DX)
            val dy = input(Input.LINEAR_DY)
            val px = scalar(Operation.SUB_F32, input(Input.X), input(Input.START_X))
            val py = scalar(Operation.SUB_F32, input(Input.Y), input(Input.START_Y))
            val dot = scalar(Operation.ADD_F32, scalar(Operation.MUL_F32, px, dx), scalar(Operation.MUL_F32, py, dy))
            val length = input(Input.LINEAR_LEN2)
            val degenerate = Node(Operation.INPUT_UNIFORM_FLAG, ValueType.ValidityFlag, input = Input.DEGENERATE)
            val safeLength = scalar(Operation.SELECT, length, one, degenerate)
            val numerator = scalar(Operation.SELECT, dot, one, degenerate)
            val valid = Node(Operation.COMPARE_F32, ValueType.ValidityFlag, listOf(one, one), lessOrEqual = true)
            val root = if (tileGraph == null) clampStops(numerator, safeLength)
                else tiledStops(numerator, safeLength, valid, tileGraph)
            return Linear(root,
                GradientNumericDomainProofV1.Unbounded(W5cPlanDiagnostics.NumericDomainUnbounded))
        }

        /** The single family-independent translation of the sealed tile grammar. */
        private fun tiledStops(numerator: Node, safeLength: Node, familyValidity: Node,
            tileGraph: GradientTileOperationGraphV2): Node {
            val one = input(Input.ONE)
            val raw = if (safeLength.input == Input.ONE) numerator else scalar(Operation.DIV_F32, numerator, safeLength)
            val lowered = mutableMapOf<GradientTileOperationNodeV2, Node>()
            fun lower(node: GradientTileOperationNodeV2): Node = lowered.getOrPut(node) {
                when (node) {
                    GradientTileOperationNodeV2.InputTF32 -> raw
                    GradientTileOperationNodeV2.InputValidity -> familyValidity
                    is GradientTileOperationNodeV2.ConstantF32 -> input(when (node.valueF32) {
                        0f -> Input.ZERO; .5f -> Input.HALF; 1f -> Input.ONE; 2f -> Input.TWO
                        else -> error("Unsupported sealed tile constant")
                    })
                    is GradientTileOperationNodeV2.MulF32 -> scalar(Operation.MUL_F32, lower(node.left), lower(node.right))
                    is GradientTileOperationNodeV2.SubF32 -> scalar(Operation.SUB_F32, lower(node.left), lower(node.right))
                    is GradientTileOperationNodeV2.FloorF32 -> scalar(Operation.FLOOR_F32, lower(node.input))
                    is GradientTileOperationNodeV2.AbsF32 -> scalar(Operation.ABS_F32, lower(node.input))
                    is GradientTileOperationNodeV2.CompareF32 -> Node(Operation.COMPARE_F32, ValueType.ValidityFlag,
                        listOf(lower(node.left), lower(node.right)), lessOrEqual = node.lessOrEqual)
                    is GradientTileOperationNodeV2.SelectF32 -> scalar(Operation.SELECT,
                        lower(node.otherwise), lower(node.selected), lower(node.condition))
                    is GradientTileOperationNodeV2.AndValidity -> Node(Operation.AND_FLAG, ValueType.ValidityFlag,
                        listOf(lower(node.left), lower(node.right)))
                }
            }
            val tiled = lower(tileGraph.outputTF32)
            // CLAMP keeps W5c's scaled comparison, avoiding division error at hard stops.
            val color = if (tileGraph.effectiveMode == GradientTileModeV2.CLAMP)
                clampStops(numerator, safeLength, tiled) else clampStops(tiled, one)
            return Node(Operation.VALIDITY_MASK, ValueType.SrgbaStraightF32, listOf(color, lower(tileGraph.validity)))
        }

        public fun radial(tileGraph: GradientTileOperationGraphV2? = null): GradientNumericOperationGraphV1 {
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
            val valid = Node(Operation.COMPARE_F32, ValueType.ValidityFlag, listOf(one, one), lessOrEqual = true)
            return Radial(if (tileGraph == null) clampStops(numerator, radius)
                else tiledStops(numerator, radius, valid, tileGraph),
                GradientNumericDomainProofV1.Unbounded(W5cPlanDiagnostics.NumericDomainUnbounded))
        }

        public fun sweep(tileGraph: GradientTileOperationGraphV2? = null): GradientNumericOperationGraphV1 {
            val zero = input(Input.ZERO)
            val one = input(Input.ONE)
            fun less(a: Node, b: Node): Node = Node(Operation.COMPARE_F32, ValueType.ValidityFlag, listOf(a, b))
            fun isZero(a: Node): Node = Node(Operation.COMPARE_F32, ValueType.ValidityFlag,
                listOf(scalar(Operation.ABS_F32, a), zero), lessOrEqual = true)
            // Deterministic permitted FTZ: both retained and hardware-flushed subnormals
            // choose +0 before any cardinal decision or eager atan2 evaluation.
            fun delta(component: Input, center: Input): Node {
                val raw = scalar(Operation.SUB_F32, input(component), input(center))
                return scalar(Operation.SELECT, raw, zero,
                    less(scalar(Operation.ABS_F32, raw), input(Input.MIN_NORMAL)))
            }
            val dx = delta(Input.X, Input.CENTER_X)
            val dy = delta(Input.Y, Input.CENTER_Y)
            val safeX = scalar(Operation.SELECT, dx, one, isZero(dx))
            val safeY = scalar(Operation.SELECT, dy, one, isZero(dy))
            val angle = scalar(Operation.ATAN2_F32, safeY, safeX)
            val turns = scalar(Operation.DIV_F32, angle, input(Input.TWO_PI))
            val wrapped = scalar(Operation.SUB_F32, turns, scalar(Operation.FLOOR_F32, turns))
            val vertical = scalar(Operation.SELECT, input(Input.QUARTER), input(Input.THREE_QUARTERS), less(dy, zero))
            val horizontal = scalar(Operation.SELECT, zero, input(Input.HALF), less(dx, zero))
            val canonicalTurns = scalar(Operation.SELECT,
                scalar(Operation.SELECT, wrapped, vertical, isZero(dx)), horizontal, isZero(dy))
            val degrees = scalar(Operation.MUL_F32, input(Input.FULL_TURN_DEGREES), canonicalTurns)
            val mapped = scalar(Operation.SUB_F32, degrees, input(Input.START_DEGREES))
            val degenerate = Node(Operation.INPUT_UNIFORM_FLAG, ValueType.ValidityFlag, input = Input.DEGENERATE)
            val leading = Node(Operation.INPUT_UNIFORM_FLAG, ValueType.ValidityFlag, input = Input.LEADING_SEGMENT)
            // Negative numerator selects the actual first stop (even a hard stop at 0).
            val leadingValue = scalar(Operation.SELECT, one, scalar(Operation.SUB_F32, zero, one),
                less(degrees, input(Input.END_DEGREES)))
            val degenerateValue = scalar(Operation.SELECT, one, leadingValue, leading)
            val numerator = scalar(Operation.SELECT, mapped, degenerateValue, degenerate)
            val denominator = scalar(Operation.SELECT, input(Input.SPAN_DEGREES), one, degenerate)
            // Full coverage has already selected effective CLAMP in the planner.
            val valid = Node(Operation.COMPARE_F32, ValueType.ValidityFlag, listOf(one, one), lessOrEqual = true)
            return Sweep(if (tileGraph == null) clampStops(numerator, denominator)
                else tiledStops(numerator, denominator, valid, tileGraph),
                GradientNumericDomainProofV1.Unbounded(W5cPlanDiagnostics.NumericDomainUnbounded))
        }

        public fun conical(tileGraph: GradientTileOperationGraphV2? = null): GradientNumericOperationGraphV1 {
            val zero = input(Input.ZERO)
            val one = input(Input.ONE)
            val two = input(Input.TWO)
            fun flag(slot: Input) = Node(Operation.INPUT_UNIFORM_FLAG, ValueType.ValidityFlag, input = slot)
            fun compare(a: Node, b: Node, inclusive: Boolean = false) = Node(Operation.COMPARE_F32,
                ValueType.ValidityFlag, listOf(a, b), lessOrEqual = inclusive)
            fun both(a: Node, b: Node) = Node(Operation.AND_FLAG, ValueType.ValidityFlag, listOf(a, b))
            fun either(a: Node, b: Node) = Node(Operation.OR_FLAG, ValueType.ValidityFlag, listOf(a, b))
            fun finite(a: Node) = Node(Operation.FINITE_F32, ValueType.ValidityFlag, listOf(a))
            fun rootDivide(a: Node, b: Node) = Node(Operation.ROOT_DIV_F32, ValueType.RootCandidateF32, listOf(a, b))
            fun safeRoot(root: Node): Node {
                val finiteRoot = Node(Operation.FINITE_ROOT_OR_ZERO_F32, ValueType.ScalarF32, listOf(root, finite(root)))
                // Canonical permitted FTZ applies to every family root before its
                // radius predicate; no exactness claim is made for zero DIV results.
                return scalar(Operation.SELECT, zero, finiteRoot,
                    compare(input(Input.MIN_NORMAL), scalar(Operation.ABS_F32, finiteRoot), true))
            }
            fun choose(a: Node, b: Node, condition: Node) = scalar(Operation.SELECT, a, b, condition)
            fun negate(a: Node) = scalar(Operation.SUB_F32, zero, a)
            fun isZero(a: Node) = compare(scalar(Operation.ABS_F32, a), zero, true)
            val fully = flag(Input.CONICAL_FULLY_DEGENERATE)
            val concentric = flag(Input.CONICAL_CONCENTRIC)
            val linear = flag(Input.CONICAL_LINEAR_EQUATION)
            val quadratic = flag(Input.CONICAL_QUADRATIC)
            val r0 = input(Input.CONICAL_START_RADIUS)
            val dr = input(Input.CONICAL_DR)
            val qx = scalar(Operation.SUB_F32, input(Input.X), input(Input.START_X))
            val qy = scalar(Operation.SUB_F32, input(Input.Y), input(Input.START_Y))
            val dot = scalar(Operation.ADD_F32, scalar(Operation.MUL_F32, qx, input(Input.CONICAL_DX)),
                scalar(Operation.MUL_F32, qy, input(Input.CONICAL_DY)))
            val b = scalar(Operation.MUL_F32, negate(two),
                scalar(Operation.ADD_F32, dot, scalar(Operation.MUL_F32, r0, dr)))
            val q2 = scalar(Operation.ADD_F32, scalar(Operation.MUL_F32, qx, qx), scalar(Operation.MUL_F32, qy, qy))
            val c = scalar(Operation.SUB_F32, q2, scalar(Operation.MUL_F32, r0, r0))
            // Explicit normal-domain guard: subnormal B is deterministically flushed
            // to zero, as permitted by WgslFloatEnvelopeV1. Never divide eagerly by it.
            val bNormal = compare(input(Input.MIN_NORMAL), scalar(Operation.ABS_F32, b), true)
            val canonicalB = choose(zero, b, bNormal)
            val linearValid = both(linear, bNormal)
            val linearCandidate = rootDivide(choose(zero, negate(c), linearValid),
                choose(one, canonicalB, linearValid))
            val linearRoot = safeRoot(linearCandidate)
            val a = input(Input.CONICAL_A)
            val discriminant = scalar(Operation.SUB_F32, scalar(Operation.MUL_F32, b, b),
                scalar(Operation.MUL_F32, scalar(Operation.MUL_F32, input(Input.FOUR), a), c))
            val discriminantValid = compare(zero, discriminant, true)
            val squareRoot = scalar(Operation.SQRT_F32, scalar(Operation.MAX_F32, discriminant, zero))
            val quadraticValid = both(quadratic, discriminantValid)
            val denominator = choose(one, scalar(Operation.MUL_F32, two, a), quadraticValid)
            val minusCandidate = rootDivide(
                choose(zero, scalar(Operation.SUB_F32, negate(b), squareRoot), quadraticValid), denominator)
            val plusCandidate = rootDivide(
                choose(zero, scalar(Operation.ADD_F32, negate(b), squareRoot), quadraticValid), denominator)
            val rootMinus = safeRoot(minusCandidate)
            val rootPlus = safeRoot(plusCandidate)
            fun validRoot(candidate: Node, root: Node, available: Node): Node {
                // Signed overflow is confined to a positivity predicate. The finite
                // root substitute and finite dr/r0 make NaN impossible even with FMA.
                val radiusProduct = Node(Operation.ROOT_RADIUS_MUL_F32, ValueType.RootRadiusCandidateF32, listOf(root, dr))
                val radius = Node(Operation.ROOT_RADIUS_ADD_F32, ValueType.RootRadiusCandidateF32, listOf(radiusProduct, r0))
                val positive = Node(Operation.ROOT_RADIUS_POSITIVE, ValueType.ValidityFlag, listOf(radius))
                return both(both(available, finite(candidate)), positive)
            }
            val minusValid = validRoot(minusCandidate, rootMinus, quadraticValid)
            val plusValid = validRoot(plusCandidate, rootPlus, quadraticValid)
            val choosePlus = both(plusValid, either(compare(rootMinus, rootPlus),
                Node(Operation.SELECT, ValueType.ValidityFlag, listOf(
                    compare(zero, one), compare(one, zero), minusValid))))
            val quadraticRoot = choose(rootMinus, rootPlus, choosePlus)
            val rootsValid = either(minusValid, plusValid)
            // Same explicit axis identities as Radial, including exact circle equality.
            val length = scalar(Operation.SQRT_F32, scalar(Operation.MAX_F32, q2, zero))
            val distance = choose(choose(length, scalar(Operation.ABS_F32, qy), isZero(qx)),
                scalar(Operation.ABS_F32, qx), isZero(qy))
            val concentricCandidate = rootDivide(
                choose(zero, scalar(Operation.SUB_F32, distance, r0), concentric), choose(one, dr, concentric))
            val concentricRoot = safeRoot(concentricCandidate)
            val selectedRoot = choose(choose(quadraticRoot, linearRoot, linear), concentricRoot, concentric)
            val selectedValid = either(either(rootsValid, validRoot(linearCandidate, linearRoot, linearValid)),
                validRoot(concentricCandidate, concentricRoot, concentric))
            // Negative parameter chooses the actual first stop even at a hard stop at 0.
            val diskValue = choose(one, negate(one), both(flag(Input.CONICAL_SHARED_RADIUS_ABOVE_EPSILON),
                compare(distance, input(Input.CONICAL_END_RADIUS))))
            val parameter = choose(selectedRoot, diskValue, fully)
            val valid = either(fully, selectedValid)
            return Conical(if (tileGraph == null) Node(Operation.VALIDITY_MASK, ValueType.SrgbaStraightF32,
                listOf(clampStops(parameter, one), valid)) else tiledStops(parameter, one, valid, tileGraph),
                GradientNumericDomainProofV1.Unbounded(W5cPlanDiagnostics.NumericDomainUnbounded))
        }

        private fun clampStops(numerator: Node, safeLength: Node, lookupTF32: Node? = null): Node {
            val zero = input(Input.ZERO)
            val one = input(Input.ONE)
            val projection = if (safeLength.input == Input.ONE) numerator else scalar(Operation.DIV_F32, numerator, safeLength)
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
                load(Operation.LOAD_STOP_POSITION_F32, -1), load(Operation.LOAD_STOP_POSITION_F32, 0), lookupTF32 ?: clamped,
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
    // Retain the existing conservative endpoint-domain admission bound. The
    // denominator itself is now a sealed F32 input, never a fragment expression.
    val xSumF64 = kotlin.math.abs(startF32.x.toDouble()) + kotlin.math.abs(endF32.x.toDouble())
    val ySumF64 = kotlin.math.abs(startF32.y.toDouble()) + kotlin.math.abs(endF32.y.toDouble())
    val dxF64 = endF32.x.toDouble() - startF32.x.toDouble()
    val dyF64 = endF32.y.toDouble() - startF32.y.toDouble()
    val lengthErrorF64 = (xSumF64 * xSumF64 + ySumF64 * ySumF64) * reassociationRoundoffFactorF64 +
        64.0 * java.lang.Float.MIN_NORMAL
    val minimumLengthF64 = if (degeneracy.linearDegenerate) 1.0 else
        minOf(degeneracy.linearLen2F32.toDouble(), dxF64 * dxF64 + dyF64 * dyF64 - lengthErrorF64)
    return proveGradientDomainV1(localMagnitudeF64, uniformMagnitudeF64, minimumLengthF64, stops,
        mapOf(Input.LINEAR_DX to degeneracy.linearDxF32, Input.LINEAR_DY to degeneracy.linearDyF32,
            Input.LINEAR_LEN2 to degeneracy.linearLen2F32))
}

internal fun GradientNumericOperationGraphV1.proveRadialDomainV1(
    localMagnitudeF64: Double, uniformMagnitudeF64: Double,
    degeneracy: RadialGradientDegeneracyV1, stops: List<GradientStopPlanV1>,
): GradientNumericDomainProofV1 = proveGradientDomainV1(localMagnitudeF64, uniformMagnitudeF64,
    if (degeneracy.radialDegenerate) 1.0 else degeneracy.radialRadiusF32.toDouble(), stops)

internal fun GradientNumericOperationGraphV1.proveSweepDomainV1(
    localMagnitudeF64: Double, uniformMagnitudeF64: Double,
    degeneracy: SweepGradientDegeneracyV1, stops: List<GradientStopPlanV1>,
): GradientNumericDomainProofV1 = proveGradientDomainV1(localMagnitudeF64, uniformMagnitudeF64,
    if (degeneracy.sweepDegenerate) 1.0 else degeneracy.sweepSpanDegreesF32.toDouble(), stops)

/** Conical has two predicate-only exceptional domains; no exceptional value can escape them. */
internal fun GradientNumericOperationGraphV1.proveConicalDomainV1(
    localMagnitudeF64: Double, startF32: org.graphiks.math.geometry.Point2F32,
    endF32: org.graphiks.math.geometry.Point2F32, degeneracy: ConicalGradientDegeneracyV1,
    stops: List<GradientStopPlanV1>,
): GradientNumericDomainProofV1 {
    val boundsF64 = mutableMapOf<GradientNumericOperationGraphV1.Node, Double>()
    val flags = mutableMapOf<GradientNumericOperationGraphV1.Node, Boolean?>()
    var proven = localMagnitudeF64.isFinite() && localMagnitudeF64 <= 1e8 &&
        degeneracy.copyScalarsF32().all { it.isFinite() && kotlin.math.abs(it) <= 1e8f }
    val minimumGapF64 = stops.zipWithNext().mapNotNull { (a, b) ->
        (b.positionF32 - a.positionF32).takeIf { it > 0f }?.toDouble()
    }.minOrNull() ?: 1.0
    fun flag(node: GradientNumericOperationGraphV1.Node): Boolean? = flags.getOrPut(node) {
        when (node.operation) {
            Operation.INPUT_UNIFORM_FLAG -> when (node.input) {
                Input.CONICAL_FULLY_DEGENERATE -> degeneracy.conicalBranchTagU32 == 0u
                Input.CONICAL_CONCENTRIC -> degeneracy.conicalBranchTagU32 == 1u
                Input.CONICAL_LINEAR_EQUATION -> degeneracy.conicalBranchTagU32 == 2u
                Input.CONICAL_QUADRATIC -> degeneracy.conicalBranchTagU32 == 3u
                Input.CONICAL_SHARED_RADIUS_ABOVE_EPSILON -> degeneracy.conicalSharedRadiusAboveEpsilon
                else -> null
            }
            Operation.AND_FLAG -> when { node.inputs.any { flag(it) == false } -> false
                node.inputs.all { flag(it) == true } -> true; else -> null }
            Operation.OR_FLAG -> when { node.inputs.any { flag(it) == true } -> true
                node.inputs.all { flag(it) == false } -> false; else -> null }
            else -> null
        }
    }
    fun denominatorMinimumF64(node: GradientNumericOperationGraphV1.Node): Double {
        if (node.operation != Operation.SELECT || node.inputs[0].input != Input.ONE) return 0.0
        if (flag(node.inputs[2]) == false) return 1.0
        val value = node.inputs[1]
        if (value.input == Input.CONICAL_DR) return minOf(1.0, kotlin.math.abs(degeneracy.conicalDrF32.toDouble()))
        if (value.operation == Operation.MUL_F32 && value.inputs[0].input == Input.TWO && value.inputs[1].input == Input.CONICAL_A)
            return minOf(1.0, 2.0 * kotlin.math.abs(degeneracy.conicalAF32.toDouble()))
        // canonicalB=select(0,B,MIN_NORMAL<=abs(B)), and the outer guard
        // contains that exact comparison. Eager DIV therefore always sees 1 or a normal B.
        if (value.operation != Operation.SELECT || value.inputs[0].input != Input.ZERO) return 0.0
        val normal = value.inputs[2]
        fun contains(condition: GradientNumericOperationGraphV1.Node): Boolean = condition === normal ||
            condition.operation == Operation.AND_FLAG && condition.inputs.any(::contains)
        return if (normal.operation == Operation.COMPARE_F32 && normal.lessOrEqual &&
            normal.inputs[0].input == Input.MIN_NORMAL && normal.inputs[1].operation == Operation.ABS_F32 &&
            normal.inputs[1].inputs.single() === value.inputs[1] && contains(node.inputs[2]))
            java.lang.Float.MIN_NORMAL.toDouble() else 0.0
    }
    fun bound(node: GradientNumericOperationGraphV1.Node): Double = boundsF64.getOrPut(node) {
        val valuesF64 = node.inputs.map(::bound)
        fun rounded(valueF64: Double): Double = valueF64 * (1.0 + reassociationRoundoffFactorF64) + java.lang.Float.MIN_NORMAL
        // Correlated tile identities are valid across the entire finite F32 root domain.
        // Above 2^24 every F32 is an even integer, so the period subtraction is zero.
        // Below that threshold subtraction is bounded by one/two (including FTZ).
        val repeatDifference = node.operation == Operation.SUB_F32 &&
            node.inputs[1].operation == Operation.FLOOR_F32 && node.inputs[1].inputs.single() === node.inputs[0]
        val halfScale = node.operation == Operation.MUL_F32 && node.inputs[1].input == Input.HALF
        val periodScale = node.operation == Operation.MUL_F32 && node.inputs[0].input == Input.TWO &&
            node.inputs[1].operation == Operation.FLOOR_F32 &&
            node.inputs[1].inputs.single().let { it.operation == Operation.MUL_F32 && it.inputs[1].input == Input.HALF }
        val mirrorDifference = node.operation == Operation.SUB_F32 && node.inputs[1].let { period ->
            period.operation == Operation.MUL_F32 && period.inputs[0].input == Input.TWO &&
                period.inputs[1].operation == Operation.FLOOR_F32 && period.inputs[1].inputs.single().let {
                    it.operation == Operation.MUL_F32 && it.inputs[1].input == Input.HALF && it.inputs[0] === node.inputs[0]
                }
        }
        val magnitudeF64 = when (node.operation) {
            Operation.INPUT_LOCAL_POINT_F32 -> localMagnitudeF64
            Operation.INPUT_UNIFORM_F32 -> kotlin.math.abs(when (node.input) {
                Input.START_X -> startF32.x.toDouble(); Input.START_Y -> startF32.y.toDouble()
                Input.END_X -> endF32.x.toDouble(); Input.END_Y -> endF32.y.toDouble()
                Input.CONICAL_DX -> degeneracy.conicalDxF32.toDouble(); Input.CONICAL_DY -> degeneracy.conicalDyF32.toDouble()
                Input.CONICAL_START_RADIUS -> degeneracy.conicalStartRadiusF32.toDouble()
                Input.CONICAL_END_RADIUS -> degeneracy.conicalEndRadiusF32.toDouble()
                Input.CONICAL_DR -> degeneracy.conicalDrF32.toDouble(); Input.CONICAL_A -> degeneracy.conicalAF32.toDouble()
                Input.ZERO -> 0.0; Input.ONE -> 1.0; Input.TWO -> 2.0; Input.FOUR -> 4.0; Input.HALF -> .5
                Input.MIN_NORMAL -> java.lang.Float.MIN_NORMAL.toDouble()
                else -> { proven = false; Double.POSITIVE_INFINITY }
            })
            Operation.ADD_F32, Operation.SUB_F32 -> when {
                repeatDifference -> 1.0
                mirrorDifference -> 2.0
                else -> rounded(valuesF64.sum())
            }
            Operation.MUL_F32 -> if (halfScale || periodScale) valuesF64[0] * valuesF64[1]
                else rounded(valuesF64[0] * valuesF64[1])
            Operation.FLOOR_F32 -> kotlin.math.ceil(valuesF64.single())
            Operation.ABS_F32 -> valuesF64.single()
            Operation.MAX_F32 -> valuesF64.max()
            Operation.SQRT_F32 -> {
                proven = proven && node.inputs.single().operation == Operation.MAX_F32 &&
                    node.inputs.single().inputs[1].input == Input.ZERO
                // 2 ULP inverseSqrt + 2.5 ULP division + both F32 roundings.
                rounded(kotlin.math.sqrt(valuesF64.single()))
            }
            Operation.ROOT_DIV_F32 -> {
                proven = proven && denominatorMinimumF64(node.inputs[1]) >= java.lang.Float.MIN_NORMAL
                // RootCandidate is allowed ±infinity; operands and normal denominator
                // exclude NaN. Its only consumers are FINITE and the checked substitute.
                Float.MAX_VALUE.toDouble()
            }
            Operation.FINITE_ROOT_OR_ZERO_F32 -> Float.MAX_VALUE.toDouble()
            Operation.ROOT_RADIUS_MUL_F32, Operation.ROOT_RADIUS_ADD_F32 -> {
                // Finite substitute * finite dr, then + finite nonnegative r0:
                // signed overflow is allowed, inf-inf and 0*inf are impossible.
                Float.MAX_VALUE.toDouble()
            }
            Operation.SELECT -> when (flag(node.inputs[2])) {
                true -> valuesF64[1]; false -> valuesF64[0]
                null -> {
                    val condition = node.inputs[2]
                    if (condition.operation == Operation.COMPARE_F32 && condition.inputs[0] === node.inputs[1] &&
                        condition.inputs[1] === node.inputs[0]) valuesF64[1] else maxOf(valuesF64[0], valuesF64[1])
                }
            }
            Operation.VALIDITY_MASK -> valuesF64[0]
            Operation.UPPER_BOUND_STOPS_V1 -> {
                val body = requireNotNull(node.loopBody)
                proven = proven && stops.size.toUInt() <= body.countBoundU32
                bound(body.comparison)
                stops.size.toDouble()
            }
            Operation.INTERPOLATE_SRGBA_STRAIGHT_F32 -> {
                proven = proven && node.clampInterpolationWeightToUnitInterval &&
                    minimumGapF64 >= node.minimumPositiveDenominatorF64 &&
                    rounded((valuesF64[4] + valuesF64[2]) / minimumGapF64) <= node.maxIntermediateMagnitudeF64
                1.000001
            }
            Operation.INPUT_UNIFORM_FLAG, Operation.INPUT_STOP_RANGE_U32,
            Operation.LOAD_STOP_POSITION_F32, Operation.LOAD_STOP_COLOR_SRGBA_F32,
            Operation.COMPARE_F32, Operation.FINITE_F32, Operation.AND_FLAG, Operation.OR_FLAG,
            Operation.ROOT_RADIUS_POSITIVE -> 1.0
            else -> { proven = false; Double.POSITIVE_INFINITY }
        }
        // Safe roots can span the entire finite F32 range, but only selection,
        // comparison, MAX and predicate-only radius nodes may consume that range.
        val extended = node.operation in setOf(Operation.ROOT_DIV_F32, Operation.FINITE_ROOT_OR_ZERO_F32,
            Operation.ROOT_RADIUS_MUL_F32, Operation.ROOT_RADIUS_ADD_F32, Operation.SELECT, Operation.MAX_F32,
            Operation.ABS_F32, Operation.FLOOR_F32) || halfScale || periodScale
        proven = proven && magnitudeF64.isFinite() && magnitudeF64 <= if (extended) Float.MAX_VALUE.toDouble() else node.maxMagnitudeF64
        magnitudeF64
    }
    bound(root)
    return if (proven) GradientNumericDomainProofV1.ProvenFinite
        else GradientNumericDomainProofV1.Unbounded(W5cPlanDiagnostics.NumericDomainUnbounded)
}

private fun GradientNumericOperationGraphV1.proveGradientDomainV1(
    localMagnitudeF64: Double, uniformMagnitudeF64: Double, minimumLengthF64: Double,
    stops: List<GradientStopPlanV1>,
    sealedInputsF32: Map<Input, Float> = emptyMap(),
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
                GradientNumericOperationGraphV1.Input.TWO -> 2.0
                GradientNumericOperationGraphV1.Input.MIN_NORMAL -> java.lang.Float.MIN_NORMAL.toDouble()
                GradientNumericOperationGraphV1.Input.TWO_PI -> 6.2831855f.toDouble()
                GradientNumericOperationGraphV1.Input.QUARTER -> .25
                GradientNumericOperationGraphV1.Input.HALF -> .5
                GradientNumericOperationGraphV1.Input.THREE_QUARTERS -> .75
                GradientNumericOperationGraphV1.Input.FULL_TURN_DEGREES -> 360.0
                else -> sealedInputsF32[node.input]?.let { kotlin.math.abs(it.toDouble()) } ?: uniformMagnitudeF64
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
                val divisorF64 = if (node.inputs[1].input == GradientNumericOperationGraphV1.Input.TWO_PI) 6.2831855f.toDouble()
                    else minimumLengthF64
                finite = finite && divisorF64 >= node.minimumPositiveDenominatorF64
                rounded(inputs[0] / divisorF64)
            }
            GradientNumericOperationGraphV1.Operation.ATAN2_F32 -> {
                // Each operand is select(canonicalDelta,1,abs(canonicalDelta)<=0).
                // canonicalDelta itself selects +0 for abs(raw)<MIN_NORMAL. Thus
                // all eager atan2 inputs are normal, nonzero, finite and <=2e8.
                fun guarded(operand: GradientNumericOperationGraphV1.Node): Boolean {
                    if (operand.operation != Operation.SELECT || operand.inputs[1].input != Input.ONE) return false
                    val delta = operand.inputs[0]
                    val condition = operand.inputs[2]
                    if (condition.operation != Operation.COMPARE_F32 || !condition.lessOrEqual ||
                        condition.inputs[1].input != Input.ZERO || condition.inputs[0].operation != Operation.ABS_F32 ||
                        condition.inputs[0].inputs.single() !== delta || delta.operation != Operation.SELECT || delta.inputs[1].input != Input.ZERO) return false
                    val flush = delta.inputs[2]
                    return flush.operation == Operation.COMPARE_F32 && !flush.lessOrEqual &&
                        flush.inputs[1].input == Input.MIN_NORMAL && flush.inputs[0].operation == Operation.ABS_F32 &&
                        flush.inputs[0].inputs.single() === delta.inputs[0]
                }
                finite = finite && node.inputs.all(::guarded) && inputs.all { it <= Math.scalb(1.0, 126) }
                // pi + 4096 ULP, with final rounding/FTZ enclosed by rounded().
                rounded(kotlin.math.PI + node.accuracyUlpsF64 * Math.ulp(kotlin.math.PI.toFloat()).toDouble())
            }
            GradientNumericOperationGraphV1.Operation.FLOOR_F32 -> kotlin.math.ceil(inputs.single())
            GradientNumericOperationGraphV1.Operation.SELECT -> {
                val condition = node.inputs[2]
                // select(x,1,1<x) is an explicit graph clamp, not an emitter-only optimization.
                if (condition.operation == GradientNumericOperationGraphV1.Operation.COMPARE_F32 &&
                    condition.inputs[0] === node.inputs[1] && condition.inputs[1] === node.inputs[0]) inputs[1]
                else maxOf(inputs[0], inputs[1])
            }
            GradientNumericOperationGraphV1.Operation.MAX_F32 -> inputs.max()
            GradientNumericOperationGraphV1.Operation.VALIDITY_MASK -> inputs[0]
            GradientNumericOperationGraphV1.Operation.AND_FLAG -> 1.0
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
