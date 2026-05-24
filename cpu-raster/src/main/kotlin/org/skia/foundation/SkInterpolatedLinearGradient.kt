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
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

internal class SkInterpolatedLinearGradient(
    private val p0: SkPoint,
    private val p1: SkPoint,
    private val srcColors: IntArray,
    private val positions: FloatArray,
    private val tileMode: SkTileMode,
    private val interpolation: SkGradient.Interpolation,
    localMatrix: SkMatrix,
) : SkShader(localMatrix) {
    private val stops: Array<Hsla> = Array(srcColors.size) { Hsla(0f, 0f, 0f, 0f, true) }
    private var invLenSqDirX: Float = 0f
    private var invLenSqDirY: Float = 0f
    private var xformSteps: SkColorSpaceXformSteps? = null

    init {
        require(srcColors.isNotEmpty()) { "SkInterpolatedLinearGradient requires at least one colour" }
        require(positions.size == srcColors.size) {
            "positions.size (${positions.size}) must match colors.size (${srcColors.size})"
        }
        require(interpolation.colorSpace == SkGradient.Interpolation.ColorSpace.kHSL) {
            "SkInterpolatedLinearGradient only supports HSL interpolation"
        }
    }

    override fun setupForDraw(canvasCtm: SkMatrix, xform: SkColorSpaceXformSteps) {
        super.setupForDraw(canvasCtm, xform)
        xformSteps = xform
        for (i in srcColors.indices) stops[i] = colorToHsla(srcColors[i], interpolation.inPremul)
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
            val c = if (stops.isNotEmpty()) hslaToWorkingColor(stops[0]) else 0
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
        if (n == 1) return hslaToWorkingColor(stops[0])
        if (tt <= positions[0]) return hslaToWorkingColor(stops[0])
        if (tt >= positions[n - 1]) return hslaToWorkingColor(stops[n - 1])

        var lo = 0
        var hi = n - 1
        while (lo + 1 < hi) {
            val mid = (lo + hi) ushr 1
            if (positions[mid] <= tt) lo = mid else hi = mid
        }
        val t0 = positions[lo]
        val t1 = positions[hi]
        val u = if (t1 > t0) (tt - t0) / (t1 - t0) else 0f
        return hslaToWorkingColor(lerpHsla(stops[lo], stops[hi], u))
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

    private fun lerpHsla(a: Hsla, b: Hsla, t: Float): Hsla {
        val startHue = if (a.powerless && !b.powerless) b.h else a.h
        val endHue = if (b.powerless && !a.powerless) a.h else b.h
        val dh = hueDelta(startHue, endHue, interpolation.hueMethod)
        return Hsla(
            normalizeHue(startHue + dh * t),
            lerp(a.s, b.s, t),
            lerp(a.l, b.l, t),
            lerp(a.a, b.a, t),
            a.powerless && b.powerless,
        )
    }

    private fun hslaToWorkingColor(hsla: Hsla): SkColor {
        val a = hsla.a
        val s = if (interpolation.inPremul == SkGradient.Interpolation.InPremul.kYes && a > 0f) {
            (hsla.s / a).coerceIn(0f, 1f)
        } else {
            hsla.s
        }
        val l = if (interpolation.inPremul == SkGradient.Interpolation.InPremul.kYes && a > 0f) {
            (hsla.l / a).coerceIn(0f, 1f)
        } else {
            hsla.l
        }
        val rgb = hslToRgb(hsla.h, s, l)
        val rgba = floatArrayOf(rgb[0], rgb[1], rgb[2], a)
        xformSteps?.apply(rgba)
        val outA = (rgba[3] * 255f + 0.5f).toInt().coerceIn(0, 255)
        val r = (rgba[0] * 255f + 0.5f).toInt().coerceIn(0, 255)
        val g = (rgba[1] * 255f + 0.5f).toInt().coerceIn(0, 255)
        val b = (rgba[2] * 255f + 0.5f).toInt().coerceIn(0, 255)
        return SkColorSetARGB(outA, r, g, b)
    }
}

private data class Hsla(
    val h: Float,
    val s: Float,
    val l: Float,
    val a: Float,
    val powerless: Boolean,
)

private fun colorToHsla(c: SkColor, inPremul: SkGradient.Interpolation.InPremul): Hsla {
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
    return Hsla(normalizeHue(h), s * premulScale, l * premulScale, a, powerless)
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
