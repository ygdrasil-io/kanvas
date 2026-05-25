package org.skia.foundation

import org.graphiks.math.SkColor
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.graphiks.math.SkColorSetARGB
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkPoint
import org.skia.core.SkColorSpaceXformSteps
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

internal class SkInterpolatedLinearGradient(
    private val p0: SkPoint,
    private val p1: SkPoint,
    private val srcColors: IntArray,
    private val positions: FloatArray,
    private val tileMode: SkTileMode,
    private val interpolation: SkGradient.Interpolation,
    localMatrix: SkMatrix,
) : SkShader(localMatrix) {
    private val stops: Array<HueStop> = Array(srcColors.size) { HueStop(0f, 0f, 0f, 0f, true) }
    private var invLenSqDirX: Float = 0f
    private var invLenSqDirY: Float = 0f
    private var xformSteps: SkColorSpaceXformSteps? = null

    init {
        require(srcColors.isNotEmpty()) { "SkInterpolatedLinearGradient requires at least one colour" }
        require(positions.size == srcColors.size) {
            "positions.size (${positions.size}) must match colors.size (${srcColors.size})"
        }
        require(
            interpolation.colorSpace == SkGradient.Interpolation.ColorSpace.kHSL ||
                interpolation.colorSpace == SkGradient.Interpolation.ColorSpace.kLCH ||
                interpolation.colorSpace == SkGradient.Interpolation.ColorSpace.kOKLCH ||
                interpolation.colorSpace == SkGradient.Interpolation.ColorSpace.kHWB,
        ) {
            "SkInterpolatedLinearGradient only supports HSL/LCH/OKLCH/HWB interpolation"
        }
    }

    override fun setupForDraw(canvasCtm: SkMatrix, xform: SkColorSpaceXformSteps) {
        super.setupForDraw(canvasCtm, xform)
        xformSteps = xform
        for (i in srcColors.indices) {
            stops[i] = when (interpolation.colorSpace) {
                SkGradient.Interpolation.ColorSpace.kHSL -> colorToHslStop(srcColors[i], interpolation.inPremul)
                SkGradient.Interpolation.ColorSpace.kLCH -> colorToLchStop(srcColors[i], interpolation.inPremul)
                SkGradient.Interpolation.ColorSpace.kOKLCH -> colorToOklchStop(srcColors[i], interpolation.inPremul)
                SkGradient.Interpolation.ColorSpace.kHWB -> colorToHwbStop(srcColors[i], interpolation.inPremul)
                else -> error("unsupported interpolation space: ${interpolation.colorSpace}")
            }
        }
        val dx = p1.fX - p0.fX
        val dy = p1.fY - p0.fY
        val lenSq = dx * dx + dy * dy
        if (lenSq == 0f) {
            invLenSqDirX = 0f
            invLenSqDirY = 0f
        } else {
            val inv = 1f / lenSq
            invLenSqDirX = dx * inv
            invLenSqDirY = dy * inv
        }
    }

    override fun shadeRow(devX: Int, devY: Int, count: Int, dst: IntArray) {
        val inv = deviceToLocal
        if (inv == null) {
            val c = if (stops.isNotEmpty()) hueStopToWorkingColor(stops[0]) else 0
            for (i in 0 until count) dst[i] = c
            return
        }

        val x0 = devX + 0.5f
        val y0 = devY + 0.5f
        var lx = inv.sx * x0 + inv.kx * y0 + inv.tx
        var ly = inv.ky * x0 + inv.sy * y0 + inv.ty
        val stepX = inv.sx
        val stepY = inv.ky

        for (i in 0 until count) {
            val t = (lx - p0.fX) * invLenSqDirX + (ly - p0.fY) * invLenSqDirY
            dst[i] = sampleAtT(t)
            lx += stepX
            ly += stepY
        }
    }

    override fun sampleAtLocal(lx: Float, ly: Float): SkColor {
        val t = (lx - p0.fX) * invLenSqDirX + (ly - p0.fY) * invLenSqDirY
        return sampleAtT(t)
    }

    private fun sampleAtT(t: Float): SkColor {
        val tt = tileT(t) ?: return 0
        val n = positions.size
        if (n == 1) return hueStopToWorkingColor(stops[0])
        if (tt <= positions[0]) return hueStopToWorkingColor(stops[0])
        if (tt >= positions[n - 1]) return hueStopToWorkingColor(stops[n - 1])

        var lo = 0
        var hi = n - 1
        while (lo + 1 < hi) {
            val mid = (lo + hi) ushr 1
            if (positions[mid] <= tt) lo = mid else hi = mid
        }
        val t0 = positions[lo]
        val t1 = positions[hi]
        val u = if (t1 > t0) (tt - t0) / (t1 - t0) else 0f
        return hueStopToWorkingColor(lerpHueStop(stops[lo], stops[hi], u))
    }

    private fun tileT(t: Float): Float? = when (tileMode) {
        SkTileMode.kClamp -> t.coerceIn(0f, 1f)
        SkTileMode.kRepeat -> t - floor(t)
        SkTileMode.kMirror -> {
            val u = t * 0.5f
            val w = u - floor(u)
            if (w < 0.5f) w * 2f else 2f - w * 2f
        }
        SkTileMode.kDecal -> if (t < 0f || t > 1f) null else t
    }

    private fun lerpHueStop(a: HueStop, b: HueStop, t: Float): HueStop {
        val startHue = if (a.powerless && !b.powerless) b.h else a.h
        val endHue = if (b.powerless && !a.powerless) a.h else b.h
        val dh = hueDelta(startHue, endHue, interpolation.hueMethod)
        return HueStop(
            normalizeHue(startHue + dh * t),
            lerp(a.c1, b.c1, t),
            lerp(a.c2, b.c2, t),
            lerp(a.a, b.a, t),
            a.powerless && b.powerless,
        )
    }

    private fun hueStopToWorkingColor(stop: HueStop): SkColor {
        val a = stop.a
        val c1 = if (interpolation.inPremul == SkGradient.Interpolation.InPremul.kYes && a > 0f) {
            stop.c1 / a
        } else {
            stop.c1
        }
        val c2 = if (interpolation.inPremul == SkGradient.Interpolation.InPremul.kYes && a > 0f) {
            stop.c2 / a
        } else {
            stop.c2
        }
        val rgb = when (interpolation.colorSpace) {
            SkGradient.Interpolation.ColorSpace.kHSL -> hslToRgb(
                stop.h,
                c1.coerceIn(0f, 1f),
                c2.coerceIn(0f, 1f),
            )
            SkGradient.Interpolation.ColorSpace.kLCH -> lchToRgb(
                stop.h,
                c1.coerceIn(0f, 150f),
                c2.coerceIn(0f, 100f),
            )
            SkGradient.Interpolation.ColorSpace.kOKLCH -> oklchToRgb(
                stop.h,
                c1.coerceIn(0f, 0.6f),
                c2.coerceIn(0f, 1f),
            )
            SkGradient.Interpolation.ColorSpace.kHWB -> hwbToRgb(
                stop.h,
                c1.coerceIn(0f, 1f),
                c2.coerceIn(0f, 1f),
            )
            else -> error("unsupported interpolation space: ${interpolation.colorSpace}")
        }
        val rgba = floatArrayOf(rgb[0], rgb[1], rgb[2], a)
        xformSteps?.apply(rgba)
        val outA = (rgba[3] * 255f + 0.5f).toInt().coerceIn(0, 255)
        val r = (rgba[0] * 255f + 0.5f).toInt().coerceIn(0, 255)
        val g = (rgba[1] * 255f + 0.5f).toInt().coerceIn(0, 255)
        val b = (rgba[2] * 255f + 0.5f).toInt().coerceIn(0, 255)
        return SkColorSetARGB(outA, r, g, b)
    }
}

private data class HueStop(
    val h: Float,
    val c1: Float,
    val c2: Float,
    val a: Float,
    val powerless: Boolean,
)

private fun colorToHslStop(c: SkColor, inPremul: SkGradient.Interpolation.InPremul): HueStop {
    val a = SkColorGetA(c) / 255f
    val r = SkColorGetR(c) / 255f
    val g = SkColorGetG(c) / 255f
    val b = SkColorGetB(c) / 255f
    val maxC = max(r, max(g, b))
    val minC = min(r, min(g, b))
    val l = (maxC + minC) * 0.5f
    val d = maxC - minC
    val powerless = d == 0f
    val s = if (d == 0f) 0f else d / (1f - abs(2f * l - 1f))
    val h = when {
        d == 0f -> 0f
        maxC == r -> 60f * (((g - b) / d) % 6f)
        maxC == g -> 60f * (((b - r) / d) + 2f)
        else -> 60f * (((r - g) / d) + 4f)
    }
    val premulScale = if (inPremul == SkGradient.Interpolation.InPremul.kYes) a else 1f
    return HueStop(normalizeHue(h), s * premulScale, l * premulScale, a, powerless)
}

private fun colorToLchStop(c: SkColor, inPremul: SkGradient.Interpolation.InPremul): HueStop {
    val a = SkColorGetA(c) / 255f
    val r = SkColorGetR(c) / 255f
    val g = SkColorGetG(c) / 255f
    val b = SkColorGetB(c) / 255f
    val lch = rgbToLch(r, g, b)
    val powerless = lch[1] <= 1e-4f
    val premulScale = if (inPremul == SkGradient.Interpolation.InPremul.kYes) a else 1f
    return HueStop(
        h = lch[2],
        c1 = lch[1] * premulScale,
        c2 = lch[0] * premulScale,
        a = a,
        powerless = powerless,
    )
}

private fun colorToOklchStop(c: SkColor, inPremul: SkGradient.Interpolation.InPremul): HueStop {
    val a = SkColorGetA(c) / 255f
    val r = SkColorGetR(c) / 255f
    val g = SkColorGetG(c) / 255f
    val b = SkColorGetB(c) / 255f
    val oklch = rgbToOklch(r, g, b)
    val powerless = oklch[1] <= 1e-5f
    val premulScale = if (inPremul == SkGradient.Interpolation.InPremul.kYes) a else 1f
    return HueStop(
        h = oklch[2],
        c1 = oklch[1] * premulScale,
        c2 = oklch[0] * premulScale,
        a = a,
        powerless = powerless,
    )
}

private fun colorToHwbStop(c: SkColor, inPremul: SkGradient.Interpolation.InPremul): HueStop {
    val a = SkColorGetA(c) / 255f
    val r = SkColorGetR(c) / 255f
    val g = SkColorGetG(c) / 255f
    val b = SkColorGetB(c) / 255f
    val hwb = rgbToHwb(r, g, b)
    val powerless = hwb[1] + hwb[2] >= 1f - 1e-6f
    val premulScale = if (inPremul == SkGradient.Interpolation.InPremul.kYes) a else 1f
    return HueStop(
        h = hwb[0],
        c1 = hwb[1] * premulScale,
        c2 = hwb[2] * premulScale,
        a = a,
        powerless = powerless,
    )
}

private fun hslToRgb(h: Float, s: Float, l: Float): FloatArray {
    if (s == 0f) return floatArrayOf(l, l, l)
    val c = (1f - abs(2f * l - 1f)) * s
    val hp = normalizeHue(h) / 60f
    val x = c * (1f - abs(hp % 2f - 1f))
    val (r1, g1, b1) = when {
        hp < 1f -> Triple(c, x, 0f)
        hp < 2f -> Triple(x, c, 0f)
        hp < 3f -> Triple(0f, c, x)
        hp < 4f -> Triple(0f, x, c)
        hp < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = l - c * 0.5f
    return floatArrayOf((r1 + m).coerceIn(0f, 1f), (g1 + m).coerceIn(0f, 1f), (b1 + m).coerceIn(0f, 1f))
}

private fun rgbToLch(r: Float, g: Float, b: Float): FloatArray {
    val rl = srgbToLinear(r)
    val gl = srgbToLinear(g)
    val bl = srgbToLinear(b)

    val x = 0.4124564f * rl + 0.3575761f * gl + 0.1804375f * bl
    val y = 0.2126729f * rl + 0.7151522f * gl + 0.0721750f * bl
    val z = 0.0193339f * rl + 0.1191920f * gl + 0.9503041f * bl
    val lab = xyzToLab(x, y, z)
    val aa = lab[1]
    val bb = lab[2]
    val c = kotlin.math.sqrt(aa * aa + bb * bb)
    val h = normalizeHue((atan2(bb, aa) * 180.0 / PI).toFloat())
    return floatArrayOf(lab[0], c, h)
}

private fun lchToRgb(h: Float, c: Float, l: Float): FloatArray {
    val rad = h * (PI / 180.0f).toFloat()
    val a = c * cos(rad)
    val b = c * sin(rad)
    val xyz = labToXyz(l, a, b)
    val rl = 3.2404542f * xyz[0] - 1.5371385f * xyz[1] - 0.4985314f * xyz[2]
    val gl = -0.9692660f * xyz[0] + 1.8760108f * xyz[1] + 0.0415560f * xyz[2]
    val bl = 0.0556434f * xyz[0] - 0.2040259f * xyz[1] + 1.0572252f * xyz[2]
    return floatArrayOf(
        linearToSrgb(rl).coerceIn(0f, 1f),
        linearToSrgb(gl).coerceIn(0f, 1f),
        linearToSrgb(bl).coerceIn(0f, 1f),
    )
}

private fun rgbToOklch(r: Float, g: Float, b: Float): FloatArray {
    val rl = srgbToLinear(r)
    val gl = srgbToLinear(g)
    val bl = srgbToLinear(b)
    val l = 0.41222146f * rl + 0.53633255f * gl + 0.051445995f * bl
    val m = 0.2119035f * rl + 0.6806995f * gl + 0.10739696f * bl
    val s = 0.08830246f * rl + 0.28171885f * gl + 0.6299787f * bl
    val l_ = l.pow(1f / 3f)
    val m_ = m.pow(1f / 3f)
    val s_ = s.pow(1f / 3f)
    val okL = 0.21045426f * l_ + 0.7936178f * m_ - 0.004072047f * s_
    val okA = 1.9779985f * l_ - 2.4285922f * m_ + 0.4505937f * s_
    val okB = 0.025904037f * l_ + 0.78277177f * m_ - 0.80867577f * s_
    val c = kotlin.math.sqrt(okA * okA + okB * okB)
    val h = normalizeHue((atan2(okB, okA) * 180.0 / PI).toFloat())
    return floatArrayOf(okL, c, h)
}

private fun oklchToRgb(h: Float, c: Float, l: Float): FloatArray {
    val rad = h * (PI / 180.0f).toFloat()
    val okA = c * cos(rad)
    val okB = c * sin(rad)
    val l_ = l + 0.39633778f * okA + 0.21580376f * okB
    val m_ = l - 0.105561346f * okA - 0.06385417f * okB
    val s_ = l - 0.08948418f * okA - 1.2914855f * okB
    val l3 = l_ * l_ * l_
    val m3 = m_ * m_ * m_
    val s3 = s_ * s_ * s_
    val rl = 4.0767417f * l3 - 3.3077116f * m3 + 0.23096994f * s3
    val gl = -1.268438f * l3 + 2.6097574f * m3 - 0.34131938f * s3
    val bl = -0.0041960863f * l3 - 0.7034186f * m3 + 1.7076147f * s3
    return floatArrayOf(
        linearToSrgb(rl).coerceIn(0f, 1f),
        linearToSrgb(gl).coerceIn(0f, 1f),
        linearToSrgb(bl).coerceIn(0f, 1f),
    )
}

private fun rgbToHwb(r: Float, g: Float, b: Float): FloatArray {
    val hsl = colorToHslStop(
        ((255 shl 24) or ((r * 255f + 0.5f).toInt() shl 16) or ((g * 255f + 0.5f).toInt() shl 8) or (b * 255f + 0.5f).toInt()),
        SkGradient.Interpolation.InPremul.kNo,
    )
    val w = min(r, min(g, b))
    val bl = 1f - max(r, max(g, b))
    return floatArrayOf(hsl.h, w, bl)
}

private fun hwbToRgb(h: Float, w: Float, bl: Float): FloatArray {
    val sum = w + bl
    if (sum >= 1f) {
        val g = if (sum == 0f) 0f else w / sum
        return floatArrayOf(g, g, g)
    }
    val rgb = hslToRgb(h, 1f, 0.5f)
    val factor = 1f - w - bl
    return floatArrayOf(
        (rgb[0] * factor + w).coerceIn(0f, 1f),
        (rgb[1] * factor + w).coerceIn(0f, 1f),
        (rgb[2] * factor + w).coerceIn(0f, 1f),
    )
}

private fun xyzToLab(x: Float, y: Float, z: Float): FloatArray {
    val xn = 0.95047f
    val yn = 1.00000f
    val zn = 1.08883f
    val fx = labF(x / xn)
    val fy = labF(y / yn)
    val fz = labF(z / zn)
    val l = 116f * fy - 16f
    val a = 500f * (fx - fy)
    val b = 200f * (fy - fz)
    return floatArrayOf(l, a, b)
}

private fun labToXyz(l: Float, a: Float, b: Float): FloatArray {
    val xn = 0.95047f
    val yn = 1.00000f
    val zn = 1.08883f
    val fy = (l + 16f) / 116f
    val fx = fy + (a / 500f)
    val fz = fy - (b / 200f)
    val x = xn * labFInv(fx)
    val y = yn * labFInv(fy)
    val z = zn * labFInv(fz)
    return floatArrayOf(x, y, z)
}

private fun labF(t: Float): Float {
    val delta = 6f / 29f
    val delta3 = delta * delta * delta
    return if (t > delta3) t.pow(1f / 3f) else (t / (3f * delta * delta)) + (4f / 29f)
}

private fun labFInv(t: Float): Float {
    val delta = 6f / 29f
    return if (t > delta) t * t * t else 3f * delta * delta * (t - 4f / 29f)
}

private fun srgbToLinear(c: Float): Float =
    if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)

private fun linearToSrgb(c: Float): Float {
    val clamped = c.coerceIn(0f, 1f)
    return if (clamped <= 0.0031308f) 12.92f * clamped else 1.055f * clamped.pow(1f / 2.4f) - 0.055f
}

private fun hueDelta(
    from: Float,
    to: Float,
    method: SkGradient.Interpolation.HueMethod,
): Float {
    val raw = normalizeHue(to) - normalizeHue(from)
    return when (method) {
        SkGradient.Interpolation.HueMethod.kShorter -> {
            when {
                raw > 180f -> raw - 360f
                raw < -180f -> raw + 360f
                else -> raw
            }
        }
        SkGradient.Interpolation.HueMethod.kLonger -> {
            when {
                raw > 0f && raw < 180f -> raw - 360f
                raw < 0f && raw > -180f -> raw + 360f
                raw == 0f -> 0f
                else -> raw
            }
        }
        SkGradient.Interpolation.HueMethod.kIncreasing -> if (raw < 0f) raw + 360f else raw
        SkGradient.Interpolation.HueMethod.kDecreasing -> if (raw > 0f) raw - 360f else raw
    }
}

private fun normalizeHue(h: Float): Float {
    val n = h % 360f
    return if (n < 0f) n + 360f else n
}

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
