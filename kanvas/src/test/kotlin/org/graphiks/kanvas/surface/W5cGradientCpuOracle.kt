package org.graphiks.kanvas.surface

import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.math.geometry.Point2F32
import org.graphiks.kanvas.gpu.plan.GradientNumericOperationGraphV1
import org.graphiks.kanvas.gpu.plan.GradientNumericOperationGraphV1.Operation
import org.graphiks.kanvas.gpu.plan.GradientNumericOperationGraphV1.Input
import org.graphiks.kanvas.gpu.plan.GradientNumericOperationGraphV1.Schedule
import org.graphiks.kanvas.surface.WgslFloatEnvelopeV1Oracle.Interval

/** Independent public-input interpreter: no production normalizer, bindings, shaders or formula helpers. */
internal object W5cGradientCpuOracle {
    class Sample internal constructor(private val pointF32: Point2F32, private val startF32: Point2F32,
        private val endF32: Point2F32, private val stops: List<GradientStop>, private val radiusF32: Float? = null,
        private val sweepAnglesF32: Pair<Float, Float>? = null, private val conicalRadiiF32: Pair<Float, Float>? = null) {
        fun thenBlend(destination: W5bBlendCpuOracle.Draw, blend: BlendMode, opacityF32: Float): WgslFloatEnvelopeV1Oracle.DrawResult {
            val graph = if (conicalRadiiF32 != null) GradientNumericOperationGraphV1.conical()
                else if (sweepAnglesF32 != null) GradientNumericOperationGraphV1.sweep()
                else if (radiusF32 == null) GradientNumericOperationGraphV1.linear() else GradientNumericOperationGraphV1.radial()
            // A value-free schema carries no production proof; derive this sample's domain independently.
            if (stops.isEmpty() || stops.size > 65_536 || stops.any { !it.position.isFinite() } ||
                listOf(pointF32.x, pointF32.y, startF32.x, startF32.y, endF32.x, endF32.y).any {
                    !it.isFinite() || kotlin.math.abs(it.toDouble()) > 1e8 } ||
                radiusF32 != null && (!radiusF32.isFinite() || radiusF32 < 0f || radiusF32 > 1e8f) ||
                conicalRadiiF32 != null && listOf(conicalRadiiF32.first, conicalRadiiF32.second).any {
                    !it.isFinite() || it < 0f || it > 1e8f } ||
                sweepAnglesF32 != null && (sweepAnglesF32.first > sweepAnglesF32.second ||
                    listOf(sweepAnglesF32.first, sweepAnglesF32.second, sweepAnglesF32.second - sweepAnglesF32.first)
                        .any { !it.isFinite() || kotlin.math.abs(it) > 1e8f }))
                return WgslFloatEnvelopeV1Oracle.DrawResult.DomainUnbounded("No finite gradient input-domain proof")
            val interpreter = Interpreter(pointF32, startF32, endF32, stops, radiusF32, sweepAnglesF32, conicalRadiiF32)
            if (!interpreter.hasFiniteStopDomain())
                return WgslFloatEnvelopeV1Oracle.DrawResult.DomainUnbounded("Stop interval has no normal finite denominator")
            val background = W5aSolidOpacityCpuOracle.draw(destination.color, destination.opacityF32)
            return try {
                WgslFloatEnvelopeV1Oracle.gradientThenBlend(
                    { interpreter.evaluate(graph.root).color() }, opacityF32,
                    requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(background)), blend,
                )
            } catch (failure: IllegalArgumentException) {
                WgslFloatEnvelopeV1Oracle.DrawResult.DomainUnbounded(failure.message ?: "No finite operation-domain proof")
            }
        }
    }
    fun linearClampSrgb(localPointF32: Point2F32, startF32: Point2F32, endF32: Point2F32,
        stops: List<GradientStop>): Sample = Sample(localPointF32, startF32, endF32, stops.toList())
    fun radialClampSrgb(localPointF32: Point2F32, centerF32: Point2F32, radiusF32: Float,
        stops: List<GradientStop>): Sample = Sample(localPointF32, centerF32, centerF32, stops.toList(), radiusF32)
    fun sweepClampSrgb(localPointF32: Point2F32, centerF32: Point2F32, startAngleDegreesF32: Float,
        endAngleDegreesF32: Float, stops: List<GradientStop>): Sample = Sample(localPointF32, centerF32, centerF32,
        stops.toList(), sweepAnglesF32 = startAngleDegreesF32 to endAngleDegreesF32)
    fun conicalClampSrgb(localPointF32: Point2F32, startF32: Point2F32, startRadiusF32: Float,
        endF32: Point2F32, endRadiusF32: Float, stops: List<GradientStop>): Sample = Sample(localPointF32, startF32,
        endF32, stops.toList(), conicalRadiiF32 = startRadiusF32 to endRadiusF32)

    private sealed interface Value {
        fun scalar(): Interval = (this as Scalar).value
        fun color(): Array<Interval> = (this as Color).value
    }
    private data class Scalar(val value: Interval) : Value
    private data class Color(val value: Array<Interval>) : Value
    private data class Flag(val values: Set<Boolean>) : Value
    private data class Index(val values: Set<Int>) : Value
    /** Finite outcomes and signed infinity alternatives, never NaN. */
    private data class Candidate(val finite: Interval, val negativeInfinity: Boolean = false,
        val positiveInfinity: Boolean = false) : Value
    private data object Range : Value

    private class Interpreter(val pointF32: Point2F32, val startF32: Point2F32, val endF32: Point2F32,
        inputStops: List<GradientStop>, val radiusF32: Float?, val sweepAnglesF32: Pair<Float, Float>?,
        val conicalRadiiF32: Pair<Float, Float>?) {
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
            if (node.operation == Operation.VALIDITY_MASK) {
                val valid = (evaluate(node.inputs[1]) as Flag).values
                if (valid == setOf(false)) return@getOrPut Color(Array(4) { Interval.ZERO })
                val color = evaluate(node.inputs[0]).color()
                return@getOrPut Color(if (false in valid) Array(4) {
                    WgslFloatEnvelopeV1Oracle.gradientHull(color[it], Interval.ZERO) } else color)
            }
            val args = node.inputs.map(::evaluate)
            val oracle = WgslFloatEnvelopeV1Oracle
            fun scalarF32(valueF32: Float) = Scalar(Interval.input(valueF32))
            when (node.operation) {
                Operation.INPUT_LOCAL_POINT_F32 -> scalarF32(if (node.input == Input.X) pointF32.x else pointF32.y)
                Operation.INPUT_UNIFORM_F32 -> scalarF32(when (node.input) {
                    Input.START_X -> startF32.x; Input.START_Y -> startF32.y
                    Input.END_X -> endF32.x; Input.END_Y -> endF32.y
                    Input.LINEAR_DX -> endF32.x - startF32.x
                    Input.LINEAR_DY -> endF32.y - startF32.y
                    Input.LINEAR_LEN2 -> {
                        val dxF32 = endF32.x - startF32.x
                        val dyF32 = endF32.y - startF32.y
                        val x2F32 = dxF32 * dxF32
                        val y2F32 = dyF32 * dyF32
                        x2F32 + y2F32
                    }
                    Input.CENTER_X -> startF32.x; Input.CENTER_Y -> startF32.y
                    Input.RADIUS -> requireNotNull(radiusF32)
                    Input.START_DEGREES -> requireNotNull(sweepAnglesF32).first
                    Input.END_DEGREES -> requireNotNull(sweepAnglesF32).second
                    Input.SPAN_DEGREES -> requireNotNull(sweepAnglesF32).let { it.second - it.first }
                    Input.MIN_NORMAL -> java.lang.Float.MIN_NORMAL
                    Input.TWO_PI -> (2.0 * kotlin.math.PI).toFloat()
                    Input.QUARTER -> .25f; Input.HALF -> .5f; Input.THREE_QUARTERS -> .75f
                    Input.FULL_TURN_DEGREES -> 360f
                    Input.CONICAL_DX -> endF32.x - startF32.x
                    Input.CONICAL_DY -> endF32.y - startF32.y
                    Input.CONICAL_START_RADIUS -> requireNotNull(conicalRadiiF32).first
                    Input.CONICAL_END_RADIUS -> requireNotNull(conicalRadiiF32).second
                    Input.CONICAL_DR -> requireNotNull(conicalRadiiF32).let { it.second - it.first }
                    Input.CONICAL_A -> {
                        val dxF32 = endF32.x - startF32.x; val dyF32 = endF32.y - startF32.y
                        val x2F32 = dxF32 * dxF32; val y2F32 = dyF32 * dyF32
                        val ddF32 = x2F32 + y2F32
                        val drF32 = requireNotNull(conicalRadiiF32).let { it.second - it.first }
                        val dr2F32 = drF32 * drF32
                        ddF32 - dr2F32
                    }
                    Input.ZERO -> 0f; Input.ONE -> 1f; Input.TWO -> 2f; Input.FOUR -> 4f
                    else -> error("Unexpected scalar")
                })
                Operation.INPUT_UNIFORM_FLAG -> {
                    val dxF32 = endF32.x - startF32.x; val dyF32 = endF32.y - startF32.y
                    Flag(setOf(if (conicalRadiiF32 != null) {
                        val drF32 = conicalRadiiF32.second - conicalRadiiF32.first
                        val x2F32 = dxF32 * dxF32
                        val y2F32 = dyF32 * dyF32
                        val ddF32 = x2F32 + y2F32
                        val dr2F32 = drF32 * drF32
                        val centers = kotlin.math.sqrt(ddF32) <= 0.000030517578125f
                        val equal = kotlin.math.abs(drF32) <= 0.000030517578125f
                        val linear = kotlin.math.abs(ddF32 - dr2F32) <= 0.000030517578125f * maxOf(maxOf(1f, ddF32), dr2F32)
                        val branchI32 = when { centers && equal -> 0; centers -> 1; linear -> 2; else -> 3 }
                        when (node.input) {
                            Input.CONICAL_FULLY_DEGENERATE -> branchI32 == 0
                            Input.CONICAL_CONCENTRIC -> branchI32 == 1
                            Input.CONICAL_LINEAR_EQUATION -> branchI32 == 2
                            Input.CONICAL_QUADRATIC -> branchI32 == 3
                            Input.CONICAL_SHARED_RADIUS_ABOVE_EPSILON -> branchI32 == 0 && conicalRadiiF32.second > 0.000030517578125f
                            else -> error("Unexpected Conical flag")
                        }
                    } else if (sweepAnglesF32 != null) {
                        val degenerate = sweepAnglesF32.second - sweepAnglesF32.first <= 0.000030517578125f
                        when (node.input) {
                            Input.DEGENERATE -> degenerate
                            Input.LEADING_SEGMENT -> degenerate && sweepAnglesF32.second > 0.000030517578125f
                            else -> error("Unexpected Sweep flag")
                        }
                    } else if (radiusF32 == null) kotlin.math.sqrt(dxF32 * dxF32 + dyF32 * dyF32) <= 0.000030517578125f
                        else radiusF32 <= 0.000030517578125f))
                }
                Operation.INPUT_STOP_RANGE_U32 -> if (node.input == Input.STOPS) Range else Index(setOf(0))
                Operation.ADD_F32 -> {
                    Scalar(oracle.gradientHull(*node.schedules.map { schedule -> when (schedule) {
                        Schedule.RoundedF32 -> oracle.gradientAdd(args[0].scalar(), args[1].scalar())
                        Schedule.FusedMultiplyAdd -> oracle.gradientHull(
                            oracle.gradientAdd(args[0].scalar(), args[1].scalar()),
                            *node.inputs.mapIndexedNotNull { indexI32, child ->
                                child.takeIf { it.operation == Operation.MUL_F32 }?.let {
                                    oracle.gradientFma(evaluate(it.inputs[0]).scalar(), evaluate(it.inputs[1]).scalar(), args[1 - indexI32].scalar())
                                }
                            }.toTypedArray())
                        Schedule.ReassociatedSumOfProducts -> reassociatedSum(expandProducts(node))
                    } }.toTypedArray()))
                }
                Operation.SUB_F32 -> Scalar(oracle.gradientHull(*node.schedules.map { schedule -> when (schedule) {
                    Schedule.RoundedF32 -> oracle.gradientSubtract(args[0].scalar(), args[1].scalar())
                    Schedule.FusedMultiplyAdd -> oracle.gradientHull(
                        oracle.gradientSubtract(args[0].scalar(), args[1].scalar()),
                        *node.inputs.mapIndexedNotNull { indexI32, child ->
                            child.takeIf { it.operation == Operation.MUL_F32 }?.let {
                                val a = evaluate(it.inputs[0]).scalar(); val b = evaluate(it.inputs[1]).scalar()
                                if (indexI32 == 0) oracle.gradientFma(a, b, negate(args[1].scalar()))
                                else oracle.gradientFma(negate(a), b, args[0].scalar())
                            }
                        }.toTypedArray())
                    Schedule.ReassociatedSumOfProducts -> reassociatedSum(expandProducts(node))
                } }.toTypedArray()))
                Operation.MUL_F32 -> Scalar(oracle.gradientHull(oracle.gradientMultiply(args[0].scalar(), args[1].scalar()),
                    reassociatedSum(expandProducts(node))))
                Operation.DIV_F32 -> Scalar(oracle.gradientDivide(args[0].scalar(), args[1].scalar()))
                Operation.ROOT_DIV_F32 -> {
                    val a = args[0].scalar(); val b = args[1].scalar()
                    val normal = java.math.BigDecimal(java.lang.Float.MIN_NORMAL.toDouble())
                    require(b.lower >= normal || b.upper <= normal.negate()) { "Root denominator lacks a normal-domain proof: $b" }
                    try { Candidate(oracle.gradientDivide(a, b)) } catch (_: IllegalArgumentException) {
                        candidateEnvelope(realDivide(a, b), 3.5)
                    }
                }
                Operation.FINITE_F32 -> {
                    val candidate = args.single() as Candidate
                    Flag(if (candidate.negativeInfinity || candidate.positiveInfinity) setOf(true, false) else setOf(true))
                }
                Operation.FINITE_ROOT_OR_ZERO_F32 -> {
                    val candidate = args[0] as Candidate
                    Scalar(if (candidate.negativeInfinity || candidate.positiveInfinity)
                        oracle.gradientHull(candidate.finite, Interval.ZERO) else candidate.finite)
                }
                Operation.ROOT_RADIUS_MUL_F32 -> {
                    try { Candidate(oracle.gradientMultiply(args[0].scalar(), args[1].scalar())) }
                    catch (_: IllegalArgumentException) { candidateEnvelope(realMultiply(args[0].scalar(), args[1].scalar()), 1.0) }
                }
                Operation.ROOT_RADIUS_ADD_F32 -> {
                    val product = args[0] as Candidate
                    val sum = try { Candidate(oracle.gradientAdd(product.finite, args[1].scalar())) }
                        catch (_: IllegalArgumentException) { candidateEnvelope(realAdd(product.finite, args[1].scalar()), 1.0) }
                    val multiply = node.inputs[0]
                    val a = evaluate(multiply.inputs[0]).scalar()
                    val b = evaluate(multiply.inputs[1]).scalar()
                    val c = args[1].scalar()
                    val fused = try { Candidate(oracle.gradientFma(a, b, c)) }
                        catch (_: IllegalArgumentException) { candidateEnvelope(realAdd(realMultiply(a, b), c), 1.0) }
                    require(node.schedules == Schedule.entries.toSet())
                    Candidate(oracle.gradientHull(sum.finite, fused.finite),
                        product.negativeInfinity || sum.negativeInfinity || fused.negativeInfinity,
                        product.positiveInfinity || sum.positiveInfinity || fused.positiveInfinity)
                }
                Operation.ROOT_RADIUS_POSITIVE -> {
                    val candidate = args.single() as Candidate
                    Flag(buildSet {
                        if (candidate.finite.upper.signum() > 0 || candidate.positiveInfinity) add(true)
                        if (candidate.finite.lower.signum() <= 0 || candidate.negativeInfinity) add(false)
                    })
                }
                Operation.AND_FLAG -> Flag((args[0] as Flag).values.flatMap { a -> (args[1] as Flag).values.map { b -> a && b } }.toSet())
                Operation.OR_FLAG -> Flag((args[0] as Flag).values.flatMap { a -> (args[1] as Flag).values.map { b -> a || b } }.toSet())
                Operation.VALIDITY_MASK -> error("Mask is evaluated before the color subtree")
                Operation.SQRT_F32 -> Scalar(oracle.gradientSqrt(args[0].scalar()))
                Operation.ABS_F32 -> {
                    val value = args[0].scalar()
                    Scalar(Interval(if (value.lower.signum() <= 0 && value.upper.signum() >= 0) java.math.BigDecimal.ZERO
                        else minOf(value.lower.abs(), value.upper.abs()), maxOf(value.lower.abs(), value.upper.abs())))
                }
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
                        is Flag -> Flag((args[0] as Flag).values + (args[1] as Flag).values)
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
                            oracle.gradientHull(*node.schedules.map { schedule -> when (schedule) {
                                Schedule.RoundedF32 -> oracle.gradientAdd(left[channelI32], oracle.gradientMultiply(delta, weight))
                                Schedule.FusedMultiplyAdd -> oracle.gradientFma(delta, weight, left[channelI32])
                                Schedule.ReassociatedSumOfProducts -> {
                                    val inverse = oracle.gradientSubtract(Interval.ONE, weight)
                                    oracle.gradientHull(
                                        reassociatedSum(listOf(listOf(left[channelI32]), listOf(right[channelI32], weight),
                                            listOf(negate(left[channelI32]), weight))),
                                        reassociatedSum(listOf(listOf(left[channelI32], inverse), listOf(right[channelI32], weight))),
                                        oracle.gradientAdd(right[channelI32], oracle.gradientMultiply(negate(delta), inverse)),
                                        oracle.gradientFma(negate(delta), inverse, right[channelI32]),
                                    )
                                }
                            } }.toTypedArray())
                        })
                    }
                }
                Operation.ATAN2_F32 -> Scalar(oracle.gradientAtan2(args[0].scalar(), args[1].scalar(), node.accuracyUlpsF64))
                Operation.FLOOR_F32 -> Scalar(oracle.gradientFloor(args.single().scalar()))
            }
        }

        private fun negate(value: Interval): Interval = WgslFloatEnvelopeV1Oracle.gradientSubtract(Interval.ZERO, value)

        private val down = java.math.MathContext(160, java.math.RoundingMode.FLOOR)
        private val up = java.math.MathContext(160, java.math.RoundingMode.CEILING)
        private fun realAdd(a: Interval, b: Interval): Interval = Interval(a.lower.add(b.lower, down), a.upper.add(b.upper, up))
        private fun realMultiply(a: Interval, b: Interval): Interval {
            val pairs = listOf(a.lower, a.upper).flatMap { x -> listOf(b.lower, b.upper).map { y -> x to y } }
            return Interval(pairs.minOf { (x, y) -> x.multiply(y, down) }, pairs.maxOf { (x, y) -> x.multiply(y, up) })
        }
        private fun realDivide(a: Interval, b: Interval): Interval {
            require(b.lower.signum() > 0 || b.upper.signum() < 0)
            val pairs = listOf(a.lower, a.upper).flatMap { x -> listOf(b.lower, b.upper).map { y -> x to y } }
            return Interval(pairs.minOf { (x, y) -> x.divide(y, down) }, pairs.maxOf { (x, y) -> x.divide(y, up) })
        }

        /** Includes both overflow-to-infinity and overflow-to-max-finite allowed outcomes. */
        private fun candidateEnvelope(real: Interval, ulpsF64: Double): Candidate {
            val maximum = java.math.BigDecimal(Float.MAX_VALUE.toDouble())
            val minimumNormal = java.math.BigDecimal(java.lang.Float.MIN_NORMAL.toDouble())
            val magnitudeF32 = maxOf(real.lower.abs(), real.upper.abs()).min(maximum).toFloat()
            val error = java.math.BigDecimal(Math.ulp(magnitudeF32).toDouble()).multiply(java.math.BigDecimal(ulpsF64), up)
            val low = real.lower.subtract(error, down)
            val high = real.upper.add(error, up)
            var finiteLow = low.coerceIn(maximum.negate(), maximum)
            var finiteHigh = high.coerceIn(maximum.negate(), maximum)
            if (finiteLow.abs() < minimumNormal) finiteLow = minOf(finiteLow, java.math.BigDecimal.ZERO)
            if (finiteHigh.abs() < minimumNormal) finiteHigh = maxOf(finiteHigh, java.math.BigDecimal.ZERO)
            return Candidate(Interval(finiteLow, finiteHigh), low < maximum.negate(), high > maximum)
        }

        /** Independent polynomial expansion of the declared dot/length sum-of-products schedule. */
        private fun expandProducts(node: GradientNumericOperationGraphV1.Node): List<List<Interval>> = when (node.operation) {
            Operation.ADD_F32 -> expandProducts(node.inputs[0]) + expandProducts(node.inputs[1])
            Operation.SUB_F32 -> expandProducts(node.inputs[0]) + expandProducts(node.inputs[1]).map { factors ->
                listOf(negate(factors.first())) + factors.drop(1)
            }
            Operation.MUL_F32 -> expandProducts(node.inputs[0]).flatMap { left -> expandProducts(node.inputs[1]).map { right -> left + right } }
            else -> listOf(listOf(evaluate(node).scalar()))
        }

        /** Enumerates all subset partitions: every ordering/association and every available FMA. */
        private fun reassociatedSum(inputTerms: List<List<Interval>>): Interval {
            val oracle = WgslFloatEnvelopeV1Oracle
            val terms = inputTerms.filterNot { factors -> factors.any { it.lower.signum() == 0 && it.upper.signum() == 0 } }
            if (terms.isEmpty()) return Interval.ZERO
            if (terms.size > 8 || terms.any { it.size > 8 }) {
                // Polynomial closure for Conical's discriminant: the exact real
                // expansion plus gamma(n)*sum(abs(products)) covers every product
                // ordering, association and partial FMA contraction. FTZ error is
                // propagated through every remaining factor with outward rounding.
                val countI32 = terms.sumOf { it.size } + terms.size
                require(countI32 < 8_388_608)
                var exact = Interval.ZERO
                var absolute = java.math.BigDecimal.ZERO
                var underflow = java.math.BigDecimal.ZERO
                for (factors in terms) {
                    val product = factors.fold(Interval.ONE, ::realMultiply)
                    exact = realAdd(exact, product)
                    absolute = absolute.add(maxOf(product.lower.abs(), product.upper.abs()), up)
                    val sensitivity = factors.fold(java.math.BigDecimal.ONE) { acc, factor ->
                        acc.multiply(maxOf(java.math.BigDecimal.ONE, factor.lower.abs(), factor.upper.abs()), up)
                    }
                    underflow = underflow.add(sensitivity.multiply(java.math.BigDecimal(factors.size.toLong()), up), up)
                }
                val gamma = java.math.BigDecimal(countI32).divide(java.math.BigDecimal(8_388_608 - countI32), up)
                val error = absolute.multiply(gamma, up).add(underflow.multiply(
                    java.math.BigDecimal(java.lang.Float.MIN_NORMAL.toDouble()), up).multiply(java.math.BigDecimal.ONE.add(gamma, up), up), up)
                return Interval(exact.lower.subtract(error, down), exact.upper.add(error, up))
            }
            val products = mutableMapOf<Pair<List<Interval>, Int>, Interval>()
            fun product(factors: List<Interval>, maskI32: Int): Interval = products.getOrPut(factors to maskI32) {
                if (maskI32.countOneBits() == 1) factors[maskI32.countTrailingZeroBits()]
                else {
                    val alternatives = mutableListOf<Interval>()
                    var leftI32 = (maskI32 - 1) and maskI32
                    while (leftI32 != 0) {
                        alternatives += oracle.gradientMultiply(product(factors, leftI32), product(factors, maskI32 xor leftI32))
                        leftI32 = (leftI32 - 1) and maskI32
                    }
                    oracle.gradientHull(*alternatives.toTypedArray())
                }
            }
            val cache = mutableMapOf<Int, Interval>()
            fun sum(maskI32: Int): Interval = cache.getOrPut(maskI32) {
                if (maskI32.countOneBits() == 1) {
                    val factors = terms[maskI32.countTrailingZeroBits()]
                    product(factors, (1 shl factors.size) - 1)
                } else {
                    val alternatives = mutableListOf<Interval>()
                    var leftI32 = (maskI32 - 1) and maskI32
                    while (leftI32 != 0) {
                        val rightI32 = maskI32 xor leftI32
                        alternatives += oracle.gradientAdd(sum(leftI32), sum(rightI32))
                        if (leftI32.countOneBits() == 1) {
                            val factors = terms[leftI32.countTrailingZeroBits()]
                            val factorMaskI32 = (1 shl factors.size) - 1
                            var firstI32 = (factorMaskI32 - 1) and factorMaskI32
                            while (firstI32 != 0) {
                                alternatives += oracle.gradientFma(product(factors, firstI32), product(factors, factorMaskI32 xor firstI32), sum(rightI32))
                                firstI32 = (firstI32 - 1) and factorMaskI32
                            }
                        }
                        leftI32 = (leftI32 - 1) and maskI32
                    }
                    oracle.gradientHull(*alternatives.toTypedArray())
                }
            }
            return sum((1 shl terms.size) - 1)
        }
    }
}
