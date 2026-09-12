package org.graphiks.kanvas.surface

import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32

/** Independent public fixture: inverse translation is x - 3, with nested shader and paint opacity. */
internal object W5dGradientAddressingCpuOracle {
    private data class Parts(val fractionF64: Double, val exponentI32: Int, val valid: Boolean)

    // Decode integer bits independently of the production matrix/coordinate implementation.
    // FTZ is a choice of classified parts; zero is never tested on the original operand again.
    private fun classifications(valueF32: Float): Set<Parts> {
        val bitsI32 = valueF32.toRawBits()
        val magnitudeI32 = bitsI32 and Int.MAX_VALUE
        val exponentI32 = magnitudeI32 ushr 23
        val mantissaI32 = magnitudeI32 and 0x7fffff
        if (exponentI32 == 255) return setOf(Parts(0.0, 0, false))
        if (magnitudeI32 == 0) return setOf(Parts(0.0, 0, true))
        val signF64 = if (bitsI32 < 0) -1.0 else 1.0
        if (exponentI32 != 0) return setOf(Parts(signF64 * (1.0 + mantissaI32 / 8388608.0) / 2.0, exponentI32 - 126, true))
        val leadingI32 = 31 - Integer.numberOfLeadingZeros(mantissaI32)
        return setOf(Parts(signF64 * mantissaI32 / Math.scalb(1.0, leadingI32 + 1), leadingI32 - 148, true),
            Parts(0.0, 0, true))
    }

    private fun divide(numeratorF32: Float, denominatorF32: Float): Set<Float?> = buildSet {
        for (numerator in classifications(numeratorF32)) for (denominator in classifications(denominatorF32)) {
            if (!numerator.valid || !denominator.valid || denominator.fractionF64 == 0.0) { add(null); continue }
            if (numerator.fractionF64 == 0.0) { add(0f); continue }
            val fractionF32 = (numerator.fractionF64 / denominator.fractionF64).toFloat()
            // Include the permitted division error, in addition to exact rounding.
            val fractions = mutableSetOf(fractionF32)
            var lowerF32 = fractionF32
            var upperF32 = fractionF32
            repeat(4) { lowerF32 = Math.nextDown(lowerF32); upperF32 = Math.nextUp(upperF32)
                fractions += lowerF32; fractions += upperF32 }
            for (quotientF32 in fractions) {
                val exponentI32 = Math.getExponent(kotlin.math.abs(quotientF32).toDouble()) + 1
                val fractionF64 = Math.scalb(quotientF32.toDouble(), -exponentI32)
                val resultExponentI32 = numerator.exponentI32 - denominator.exponentI32 + exponentI32
                if (resultExponentI32 > 128) { add(null); continue }
                val resultF32 = Math.scalb(fractionF64, resultExponentI32).toFloat()
                add(resultF32.takeIf { it.isFinite() })
                if (resultF32 != 0f && kotlin.math.abs(resultF32) < java.lang.Float.MIN_NORMAL) add(0f)
            }
        }
    }

    private fun row(aF32: Float, xF32: Float, bF32: Float, yF32: Float, cF32: Float): Set<Float> {
        // Parenthesizations and either multiply/add contraction of ax + by + c.
        fun ftz(valueF32: Float): Set<Float> = if (kotlin.math.abs(valueF32) < java.lang.Float.MIN_NORMAL)
            setOf(valueF32, 0f) else setOf(valueF32)
        fun add(left: Set<Float>, right: Set<Float>): Set<Float> = left.flatMap { a -> right.flatMap { b -> ftz(a + b) } }.toSet()
        fun mul(left: Set<Float>, right: Set<Float>): Set<Float> = left.flatMap { a -> right.flatMap { b -> ftz(a * b) } }.toSet()
        fun fma(left: Set<Float>, right: Set<Float>, tail: Set<Float>): Set<Float> =
            left.flatMap { a -> right.flatMap { b -> tail.flatMap { c -> ftz(Math.fma(a, b, c)) } } }.toSet()
        val a = ftz(aF32); val b = ftz(bF32); val c = ftz(cF32); val x = ftz(xF32); val y = ftz(yF32)
        val ax = mul(a, x); val by = mul(b, y)
        return listOf(add(add(ax, by), c), add(ax, add(by, c)), add(add(ax, c), by),
            add(fma(a, x, c), by), add(fma(b, y, c), ax),
            add(fma(a, x, by), c), add(fma(b, y, ax), c), fma(a, x, add(by, c)), fma(b, y, add(ax, c)),
            fma(a, x, fma(b, y, c)), fma(b, y, fma(a, x, c))).flatten().toSet()
    }

    private fun matrix(points: Set<Point2F32?>, inverseF32: Matrix3x3F32): Set<Point2F32?> = buildSet {
        for (point in points) {
            if (point == null) { add(null); continue }
            val xs = row(inverseF32.sx, point.x, inverseF32.kx, point.y, inverseF32.tx)
            val ys = row(inverseF32.ky, point.x, inverseF32.sy, point.y, inverseF32.ty)
            val ws = row(inverseF32.persp0, point.x, inverseF32.persp1, point.y, inverseF32.persp2)
            for (wF32 in ws) for (xF32 in xs) for (yF32 in ys) {
                val affine = inverseF32.persp0 == 0f && inverseF32.persp1 == 0f && inverseF32.persp2 == 1f
                val projectedX = if (affine) setOf(xF32.takeIf { it.isFinite() }) else divide(xF32, wF32)
                val projectedY = if (affine) setOf(yF32.takeIf { it.isFinite() }) else divide(yF32, wF32)
                for (pxF32 in projectedX) for (pyF32 in projectedY) {
                    add(if (pxF32 == null || pyF32 == null) null else Point2F32(pxF32, pyF32))
                }
            }
        }
    }

    private fun clamp(points: Set<Point2F32?>, subsetF32: RectF32): Set<Point2F32?> = points.map { point ->
        point?.let { Point2F32(it.x.coerceIn(subsetF32.left, subsetF32.right), it.y.coerceIn(subsetF32.top, subsetF32.bottom)) }
    }.toSet()

    @OptIn(ExperimentalUnsignedTypes::class)
    fun projectivePixelCodes(pixelXI32: Int, scaleF64: Double, numeratorF64: Double = 2 * scaleF64): Set<List<UByte>> {
        // Hand-derived inverse coefficients; no production composition, inversion or plan helper.
        var points: Set<Point2F32?> = setOf(Point2F32(pixelXI32 + .5f, .5f))
        points = matrix(points, Matrix3x3F32()) // affine CTM is exact, without an artificial /1 error
        points = matrix(points, Matrix3x3F32(sx = 0f, kx = 0f, tx = numeratorF64.toFloat(),
            ky = 0f, sy = scaleF64.toFloat(), ty = 0f, persp0 = scaleF64.toFloat(), persp1 = 0f,
            persp2 = (-1.5 * scaleF64).toFloat()))
        points = clamp(points, RectF32.ofLTRB(0f, 0f, 2f, 1f))
        val result = points.map { point ->
            if (point == null) listOf<UByte>(0u, 0u, 0u, 0u)
            else {
                // Every candidate stays in the same constant stop span.
                require(point.x <= .875f || point.x >= 1f)
                if (point.x >= 1f) bluePixel().toList() else redPixel().toList()
            }
        }.toSet()
        require(result.size == 1) { "Chosen public pixel must close to a singleton: $result" }
        return result
    }

    // Hand-derived endpoint colors: inverse x = 8.5 is blue; inverse x = .5 is red.
    @OptIn(ExperimentalUnsignedTypes::class)
    fun bluePixel(): UByteArray = ubyteArrayOf(0u, 0u, 255u, 255u)
    @OptIn(ExperimentalUnsignedTypes::class)
    fun redPixel(): UByteArray = ubyteArrayOf(255u, 0u, 0u, 255u)

    fun evaluate(pixelXI32: Int, stops: List<GradientStop>, ctmScaleXF32: Float = 1f): WgslFloatEnvelopeV1Oracle.DrawResult.Bounded {
        // The fixture has an exact dy=0 axis and a power-of-two denominator. Samples
        // are inside constant spans, so every permitted coordinate schedule selects
        // the same color; the attachment/opacity envelope remains independently modeled.
        val tF32 = (((pixelXI32 + .5f) / ctmScaleXF32 - 3f) / 8f).coerceIn(0f, 1f)
        val left = stops.lastOrNull { it.position <= tF32 } ?: stops.first()
        val right = stops.firstOrNull { it.position > tF32 } ?: stops.last()
        require(left.color == right.color) { "This fixture must sample a constant-color span" }
        val color = left.color
        val result = WgslFloatEnvelopeV1Oracle.gradientThenBlend({
            listOf(color.redNormalized, color.greenNormalized, color.blueNormalized, color.alphaNormalized)
                .map(WgslFloatEnvelopeV1Oracle.Interval::input).toTypedArray()
        }, (.75f * .5f) * (191f / 255f), WgslFloatEnvelopeV1Oracle.clearAttachment(), BlendMode.SRC_OVER)
        require(result is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded) { result.toString() }
        return result
    }
}
