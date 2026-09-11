package org.graphiks.kanvas.surface

import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.math.geometry.Point2F32
import org.graphiks.kanvas.gpu.plan.GradientNumericOperationGraphV1
import org.graphiks.kanvas.gpu.plan.GradientNumericOperationGraphV1.Operation
import org.graphiks.kanvas.gpu.plan.GradientNumericOperationGraphV1.Input
import org.graphiks.kanvas.gpu.plan.GradientNumericDomainProofV1
import org.graphiks.kanvas.surface.WgslFloatEnvelopeV1Oracle.Interval

/** Independent public-input interpreter: no production normalizer, bindings, shaders or formula helpers. */
internal object W5cGradientCpuOracle {
    class Sample internal constructor(private val pointF32: Point2F32, private val startF32: Point2F32,
        private val endF32: Point2F32, private val stops: List<GradientStop>) {
        fun thenBlend(destination: W5bBlendCpuOracle.Draw, blend: BlendMode, opacityF32: Float): WgslFloatEnvelopeV1Oracle.DrawResult {
            val graph = GradientNumericOperationGraphV1.linear()
            if (graph.domainProof != GradientNumericDomainProofV1.ProvenFinite ||
                stops.isEmpty() || stops.size > 65_536 || stops.any { !it.position.isFinite() } ||
                listOf(pointF32.x, pointF32.y, startF32.x, startF32.y, endF32.x, endF32.y).any {
                    !it.isFinite() || kotlin.math.abs(it.toDouble()) > 1e8 })
                return WgslFloatEnvelopeV1Oracle.DrawResult.DomainUnbounded("No finite Linear input-domain proof")
            val interpreter = Interpreter(pointF32, startF32, endF32, stops)
            if (!interpreter.hasFiniteStopDomain())
                return WgslFloatEnvelopeV1Oracle.DrawResult.DomainUnbounded("Stop interval has no normal finite denominator")
            val background = W5aSolidOpacityCpuOracle.draw(destination.color, destination.opacityF32)
            return WgslFloatEnvelopeV1Oracle.gradientThenBlend(
                { interpreter.evaluate(graph.root).color() }, opacityF32,
                requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(background)), blend,
            )
        }
    }
    fun linearClampSrgb(localPointF32: Point2F32, startF32: Point2F32, endF32: Point2F32,
        stops: List<GradientStop>): Sample = Sample(localPointF32, startF32, endF32, stops.toList())

    private sealed interface Value {
        fun scalar(): Interval = (this as Scalar).value
        fun color(): Array<Interval> = (this as Color).value
    }
    private data class Scalar(val value: Interval) : Value
    private data class Color(val value: Array<Interval>) : Value
    private data class Flag(val values: Set<Boolean>) : Value
    private data class Index(val values: Set<Int>) : Value
    private data object Range : Value

    private class Interpreter(val pointF32: Point2F32, val startF32: Point2F32, val endF32: Point2F32,
        inputStops: List<GradientStop>) {
        private val stops: List<GradientStop>
        private val values = mutableMapOf<GradientNumericOperationGraphV1.Node, Value>()
        init {
            require(inputStops.isNotEmpty() && inputStops.all { it.position.isFinite() })
            val monotone = mutableListOf<GradientStop>()
            inputStops.forEach { stop ->
                monotone += stop.copy(position = stop.position.coerceIn(monotone.lastOrNull()?.position ?: 0f, 1f))
            }
            if (monotone.first().position > 0f) monotone.add(0, monotone.first().copy(position = 0f))
            if (monotone.last().position < 1f) monotone += monotone.last().copy(position = 1f)
            stops = monotone
        }
        fun hasFiniteStopDomain(): Boolean = stops.zipWithNext().all { (low, high) ->
            high.position == low.position || high.position - low.position >= java.lang.Float.MIN_NORMAL
        }
        fun evaluate(node: GradientNumericOperationGraphV1.Node): Value = values.getOrPut(node) {
            val args = node.inputs.map(::evaluate)
            val oracle = WgslFloatEnvelopeV1Oracle
            fun scalarF32(valueF32: Float) = Scalar(Interval.input(valueF32))
            when (node.operation) {
                Operation.INPUT_LOCAL_POINT_F32 -> scalarF32(if (node.input == Input.X) pointF32.x else pointF32.y)
                Operation.INPUT_UNIFORM_F32 -> scalarF32(when (node.input) {
                    Input.START_X -> startF32.x; Input.START_Y -> startF32.y
                    Input.END_X -> endF32.x; Input.END_Y -> endF32.y
                    Input.ZERO -> 0f; Input.ONE -> 1f; else -> error("Unexpected scalar")
                })
                Operation.INPUT_UNIFORM_FLAG -> {
                    val dxF32 = endF32.x - startF32.x; val dyF32 = endF32.y - startF32.y
                    Flag(setOf(kotlin.math.sqrt(dxF32 * dxF32 + dyF32 * dyF32) <= 0.000030517578125f))
                }
                Operation.INPUT_STOP_RANGE_U32 -> if (node.input == Input.STOPS) Range else Index(setOf(0))
                Operation.ADD_F32 -> {
                    var sum = oracle.gradientAdd(args[0].scalar(), args[1].scalar())
                    node.inputs.forEachIndexed { indexI32, child -> if (child.operation == Operation.MUL_F32 &&
                        GradientNumericOperationGraphV1.Schedule.FusedMultiplyAdd in node.schedules) {
                        sum = oracle.gradientHull(sum, oracle.gradientFma(evaluate(child.inputs[0]).scalar(),
                            evaluate(child.inputs[1]).scalar(), args[1 - indexI32].scalar()))
                    } }
                    Scalar(sum)
                }
                Operation.SUB_F32 -> Scalar(oracle.gradientSubtract(args[0].scalar(), args[1].scalar()))
                Operation.MUL_F32 -> Scalar(oracle.gradientMultiply(args[0].scalar(), args[1].scalar()))
                Operation.DIV_F32 -> Scalar(oracle.gradientDivide(args[0].scalar(), args[1].scalar()))
                Operation.MAX_F32 -> Scalar(Interval(maxOf(args[0].scalar().lower, args[1].scalar().lower),
                    maxOf(args[0].scalar().upper, args[1].scalar().upper)))
                Operation.COMPARE_F32 -> {
                    val a = args[0].scalar(); val b = args[1].scalar()
                    Flag(buildSet {
                        if (if (node.lessOrEqual) a.lower <= b.upper else a.lower < b.upper) add(true)
                        if (if (node.lessOrEqual) a.upper > b.lower else a.upper >= b.lower) add(false)
                    })
                }
                Operation.SELECT -> {
                    val flags = (args[2] as Flag).values
                    if (flags.size == 1) args[if (flags.single()) 1 else 0]
                    else when (args[0]) {
                        is Scalar -> Scalar(oracle.gradientHull(args[0].scalar(), args[1].scalar()))
                        is Color -> Color(Array(4) { oracle.gradientHull(args[0].color()[it], args[1].color()[it]) })
                        else -> error("Unexpected select type")
                    }
                }
                Operation.UPPER_BOUND_STOPS_V1 -> {
                    val body = requireNotNull(node.loopBody)
                    require(stops.size.toUInt() <= body.countBoundU32 && body.comparison.lessOrEqual)
                    // Interpret the explicit loop body with each probed stop index substituted.
                    fun compare(probeI32: Int): Set<Boolean> {
                        fun bodyValue(n: GradientNumericOperationGraphV1.Node): Value {
                            if (n === body.probe) return Index(setOf(probeI32))
                            if (n === body.position) return Scalar(Interval.input(stops[probeI32].position))
                            if (n.operation == Operation.MUL_F32) return Scalar(oracle.gradientMultiply(
                                bodyValue(n.inputs[0]).scalar(), bodyValue(n.inputs[1]).scalar()))
                            return evaluate(n)
                        }
                        val a = bodyValue(body.comparison.inputs[0]).scalar()
                        val b = bodyValue(body.comparison.inputs[1]).scalar()
                        return buildSet { if (a.lower <= b.upper) add(true); if (a.upper > b.lower) add(false) }
                    }
                    fun search(lowI32: Int, highI32: Int): Set<Int> {
                        if (lowI32 >= highI32) return setOf(lowI32)
                        val probeI32 = lowI32 + (highI32 - lowI32) / 2
                        val flags = compare(probeI32)
                        return buildSet {
                            if (true in flags) addAll(search(probeI32 + 1, highI32))
                            if (false in flags) addAll(search(lowI32, probeI32))
                        }
                    }
                    Index(search(0, stops.size))
                }
                Operation.LOAD_STOP_POSITION_F32, Operation.LOAD_STOP_COLOR_SRGBA_F32 -> {
                    val selected = (args[1] as Index).values.map { stops[(it + node.relativeIndexI32).coerceIn(0, stops.lastIndex)] }
                    if (node.operation == Operation.LOAD_STOP_POSITION_F32)
                        Scalar(oracle.gradientHull(*selected.map { Interval.input(it.position) }.toTypedArray()))
                    else Color(Array(4) { channelI32 -> oracle.gradientHull(*selected.map { stop ->
                        val color = stop.color
                        Interval.input(listOf(color.redNormalized, color.greenNormalized, color.blueNormalized, color.alphaNormalized)[channelI32])
                    }.toTypedArray()) })
                }
                Operation.INTERPOLATE_SRGBA_STRAIGHT_F32 -> {
                    require(node.clampInterpolationWeightToUnitInterval)
                    require(GradientNumericOperationGraphV1.Schedule.FusedMultiplyAdd in node.schedules &&
                        GradientNumericOperationGraphV1.Schedule.RoundedF32 in node.schedules)
                    val left = args[0].color(); val right = args[1].color()
                    val low = args[2].scalar(); val high = args[3].scalar(); val t = args[4].scalar()
                    if (high.upper <= low.lower) Color(right)
                    else if (left.contentEquals(right)) Color(left)
                    else if (high.lower <= low.upper) Color(Array(4) { oracle.gradientHull(left[it], right[it]) })
                    else {
                        val rawWeight = oracle.gradientDivide(oracle.gradientSubtract(t, low), oracle.gradientSubtract(high, low))
                        val weight = Interval(rawWeight.lower.coerceIn(java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE),
                            rawWeight.upper.coerceIn(java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE))
                        Color(Array(4) { channelI32 ->
                            val delta = oracle.gradientSubtract(right[channelI32], left[channelI32])
                            oracle.gradientHull(oracle.gradientAdd(left[channelI32], oracle.gradientMultiply(delta, weight)),
                                oracle.gradientFma(delta, weight, left[channelI32]))
                        })
                    }
                }
                Operation.SQRT_F32, Operation.ATAN2_F32, Operation.FLOOR_F32, Operation.ABS_F32 ->
                    error("Operation belongs to a deferred family outside the sealed Linear graph")
            }
        }
    }
}
