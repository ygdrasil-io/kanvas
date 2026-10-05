package org.graphiks.kanvas.surface

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.surface.WgslFloatEnvelopeV1Oracle.Interval
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.SizeI32
import org.graphiks.math.matrix.Matrix3x3F32

/** Independent Park–Miller tables and published noise equations from public shader inputs. */
internal object W5gNoiseCpuOracle {
    fun expected(shader: Shader, pointF32: Point2F32, paintAlphaF32: Float = 1f,
        destination: ColorARGB = ColorARGB.Transparent, finalBlend: BlendMode = BlendMode.SRC,
        external: ColorFilter? = null, canvasMatrixF32: Matrix3x3F32 = Matrix3x3F32()
    ): WgslFloatEnvelopeV1Oracle.DrawResult = W5fColorCpuOracle.expectedShaderTree(shader,
        paintAlphaF32 = paintAlphaF32, external = external, destination = destination,
        finalBlend = finalBlend, devicePointF32 = pointF32, canvasMatrixF32 = canvasMatrixF32)

    fun source(shader: Shader, localPoint: Array<Interval>): Array<Interval> {
        require(localPoint.size == 2)
        val turbulence = shader is Shader.PerlinNoise
        val baseX: Float
        val baseY: Float
        val octaves: Int
        val seed: Int
        val tile: SizeI32?
        when (shader) {
            is Shader.PerlinNoise -> {
                baseX = shader.baseX; baseY = shader.baseY; octaves = shader.numOctaves; seed = shader.seed
                tile = shader.tileSize
            }
            is Shader.FractalNoise -> {
                baseX = shader.baseX; baseY = shader.baseY; octaves = shader.numOctaves; seed = shader.seed
                tile = shader.tileSize
            }
            else -> error("Expected a public noise leaf")
        }
        require(baseX.isFinite() && baseY.isFinite() && baseX >= 0f && baseY >= 0f && octaves in 0..255)
        // Zero-octave source is linear premultiplied, never an sRGB solid substitute.
        if (octaves == 0) return if (turbulence) Array(4) { Interval.ZERO }
            else arrayOf(input(.25f), input(.25f), input(.25f), input(.5f))
        val table = tableBytes(seed)
        fun stitch(base: Float, dimension: Int): Pair<Float,BigInteger> {
            if(base == 0f) return 0f to BigInteger.ZERO
            val size=dimension.toFloat()
            val product=size*base
            val low=kotlin.math.floor(product.toDouble()).toFloat()/size
            val high=kotlin.math.ceil(product.toDouble()).toFloat()/size
            val adjusted=if(low > 0f && base/low < high/base) low else high
            val period=BigDecimal((size*adjusted).toDouble()).setScale(0,RoundingMode.HALF_UP).toBigIntegerExact()
            return adjusted to period
        }
        val stitched=tile != null && tile.width > 0 && tile.height > 0
        val (frequencyX,initialPeriodX)=if(stitched) stitch(baseX,tile!!.width) else baseX to BigInteger.ZERO
        val (frequencyY,initialPeriodY)=if(stitched) stitch(baseY,tile!!.height) else baseY to BigInteger.ZERO
        var periodX=initialPeriodX; var periodY=initialPeriodY
        var x = mul(add(localPoint[0], input(.5f)), input(frequencyX))
        var y = mul(add(localPoint[1], input(.5f)), input(frequencyY))
        var amplitude = Interval.ONE
        val sums = Array(4) { Interval.ZERO }
        var integralTail = false
        repeat(octaves) {
            if (!integralTail) integralTail = integralPoint(x) && integralPoint(y)
            val values = if (integralTail) Array(4) { Interval.ZERO } else sample(table, x, y, periodX, periodY)
            for (channel in 0..3) {
                val value = if (turbulence) abs(values[channel]) else values[channel]
                val contribution = mul(value, amplitude)
                sums[channel] = if (integralTail) {
                    // These are still the original Multiply, Add and possible FMA operations.
                    // Finite amplitude times exact zero is exact zero under every DAZ/FTZ branch.
                    require(amplitude.lower.abs() <= input(Float.MAX_VALUE).upper &&
                        amplitude.upper.abs() <= input(Float.MAX_VALUE).upper)
                    require(contribution.lower.signum() == 0 && contribution.upper.signum() == 0)
                    addZeroToPreviouslyRoundedSum(sums[channel])
                } else hull(add(sums[channel], contribution),
                    WgslFloatEnvelopeV1Oracle.gradientFma(value, amplitude, sums[channel]))
            }
            // Keep the original tail additions and amplitude operations, including FTZ.
            amplitude = mul(amplitude, input(.5f))
            if (!integralTail && it + 1 < octaves) {
                x = mul(x, input(2f)); y = mul(y, input(2f))
                periodX=periodX.shiftLeft(1); periodY=periodY.shiftLeft(1)
            }
        }
        val clamped = Array(4) { channel -> clamp(if (turbulence) sums[channel]
            else hull(add(mul(sums[channel], input(.5f)), input(.5f)),
                WgslFloatEnvelopeV1Oracle.gradientFma(sums[channel], input(.5f), input(.5f)))) }
        return Array(4) { if (it == 3) clamped[3] else mul(clamped[it], clamped[3]) }
    }

    private fun sample(bytes: ByteArray, x: Interval, y: Interval, periodX: BigInteger, periodY: BigInteger): Array<Interval> {
        fun phase(q: Interval): Pair<Int, Interval> {
            val low = q.lower.setScale(0, RoundingMode.FLOOR).intValueExact()
            val high = q.upper.setScale(0, RoundingMode.FLOOR).intValueExact()
            require(low == high) { "Noise fixture crosses a lattice branch" }
            val fraction = sub(q, input(low.toFloat()))
            return low to fraction
        }
        val (ix, fx) = phase(x)
        val (iy, fy) = phase(y)
        fun address(value: Int,period: BigInteger): Int =
            (if(period.signum() == 0) BigInteger.valueOf(value.toLong()) else BigInteger.valueOf(value.toLong()).mod(period))
                .and(BigInteger.valueOf(255L)).toInt()
        fun permutation(index: Int) = bytes[index and 255].toInt() and 255
        val corners = intArrayOf((permutation(address(ix,periodX)) + address(iy,periodY)) and 255,
            (permutation(address(ix + 1,periodX)) + address(iy,periodY)) and 255,
            (permutation(address(ix,periodX)) + address(iy + 1,periodY)) and 255,
            (permutation(address(ix + 1,periodX)) + address(iy + 1,periodY)) and 255)
        fun component(channel: Int, corner: Int, axis: Int): Interval {
            val offset = 256 + channel * 1024 + corners[corner] * 4 + axis * 2
            val code = (bytes[offset].toInt() and 255) or ((bytes[offset + 1].toInt() and 255) shl 8)
            return sub(WgslFloatEnvelopeV1Oracle.gradientDivide(input(code.toFloat()), input(32767.5f)), Interval.ONE)
        }
        val sx = smooth(fx); val sy = smooth(fy)
        return Array(4) { channel ->
            fun corner(index: Int): Interval = dot(component(channel, index, 0),
                if (index and 1 == 0) fx else sub(fx, Interval.ONE),
                component(channel, index, 1), if (index < 2) fy else sub(fy, Interval.ONE))
            lerp(lerp(corner(0), corner(1), sx), lerp(corner(2), corner(3), sx), sy)
        }
    }

    private fun tableBytes(seed: Int): ByteArray {
        var state = if (seed <= 0) -(seed.toLong() % 2147483646L) + 1L else minOf(seed.toLong(), 2147483646L)
        fun next(): Int {
            val candidate = 16807L * (state % 127773L) - 2836L * (state / 127773L)
            state = if (candidate <= 0L) candidate + 2147483647L else candidate
            return state.toInt()
        }
        val permutation = IntArray(256) { it }
        val raw = Array(4) { Array(256) { intArrayOf(next() % 512, next() % 512) } }
        for (index in 255 downTo 1) {
            val other = next() % 256
            val previous = permutation[index]
            permutation[index] = permutation[other]; permutation[other] = previous
        }
        val bytes = ByteArray(4352)
        permutation.forEachIndexed { index, value -> bytes[index] = value.toByte() }
        for (channel in 0..3) for (index in 0..255) {
            val pair = raw[channel][permutation[index]]
            val x = (pair[0] - 256) / 256.0; val y = (pair[1] - 256) / 256.0
            val length = StrictMath.sqrt(x * x + y * y)
            val reciprocal = if (length == 0.0) 0.0 else 1.0 / length
            for (axis in 0..1) {
                val normalized = ((if (axis == 0) x else y) * reciprocal).toFloat()
                val mapped = (normalized + 1f) * 32767.5f
                val code = kotlin.math.floor(mapped.toDouble() + .5).toInt()
                val offset = 256 + channel * 1024 + index * 4 + axis * 2
                bytes[offset] = code.toByte(); bytes[offset + 1] = (code ushr 8).toByte()
            }
        }
        return bytes
    }

    private fun integralPoint(value: Interval) = value.lower.compareTo(value.upper) == 0 &&
        value.lower.stripTrailingZeros().scale() <= 0
    private fun addZeroToPreviouslyRoundedSum(sum: Interval): Interval {
        // Every represented actual sum is already F32, so Add(sum, 0) and FMA(0, a, sum)
        // have no further rounding error. Retain the alternative zero result whenever an
        // actual subnormal sum could be flushed on input (DAZ) or on output (FTZ).
        val minimumNormal = BigDecimal(java.lang.Float.MIN_NORMAL.toDouble())
        return if (sum.lower < minimumNormal && sum.upper > minimumNormal.negate())
            hull(sum, Interval.ZERO) else sum
    }
    private fun smooth(f: Interval): Interval {
        val inner = hull(sub(input(3f), mul(input(2f), f)),
            WgslFloatEnvelopeV1Oracle.gradientFma(input(-2f), f, input(3f)))
        return hull(mul(mul(f, f), inner), mul(f, mul(f, inner)))
    }
    private fun lerp(a: Interval, b: Interval, t: Interval): Interval {
        val difference = sub(b, a)
        return hull(add(a, mul(difference, t)), WgslFloatEnvelopeV1Oracle.gradientFma(difference, t, a))
    }
    private fun dot(a: Interval, x: Interval, b: Interval, y: Interval) = hull(
        add(mul(a, x), mul(b, y)), WgslFloatEnvelopeV1Oracle.gradientFma(a, x, mul(b, y)),
        WgslFloatEnvelopeV1Oracle.gradientFma(b, y, mul(a, x)))
    private fun abs(value: Interval) = Interval(
        if (value.lower.signum() <= 0 && value.upper.signum() >= 0) BigDecimal.ZERO
        else minOf(value.lower.abs(), value.upper.abs()), maxOf(value.lower.abs(), value.upper.abs()))
    private fun clamp(value: Interval) = Interval(value.lower.coerceIn(BigDecimal.ZERO, BigDecimal.ONE),
        value.upper.coerceIn(BigDecimal.ZERO, BigDecimal.ONE))
    private fun input(value: Float) = Interval.input(value)
    private fun add(a: Interval, b: Interval) = WgslFloatEnvelopeV1Oracle.gradientAdd(a, b)
    private fun sub(a: Interval, b: Interval) = WgslFloatEnvelopeV1Oracle.gradientSubtract(a, b)
    private fun mul(a: Interval, b: Interval) = WgslFloatEnvelopeV1Oracle.gradientMultiply(a, b)
    private fun hull(vararg values: Interval) = WgslFloatEnvelopeV1Oracle.gradientHull(*values)
}
