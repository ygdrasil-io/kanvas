package org.skia.foundation

import org.skia.core.SkColorSpaceXformSteps
import org.skia.math.SkMatrix
import org.skia.math.SkScalar
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Base class for paint shaders — Phase 5a. A [SkShader] supplies one
 * [SkColor] (in the bitmap's *working colour space*, usually Rec.2020)
 * per device-space pixel covered by a draw. The colour is then modulated
 * by AA coverage and composited (SrcOver) against the destination.
 *
 * Lifecycle per draw:
 *  1. [SkBitmapDevice] calls [setupForDraw] once with the current canvas
 *     CTM and the bitmap's `srgb → working-space` xform pipeline. The
 *     shader caches `(canvasCtm · localMatrix).invert()` and pre-transforms
 *     its sRGB stop colours into working space.
 *  2. For each scanline, the device calls [shadeRow] one or more times
 *     (once per disjoint covered x-span) and composites the returned
 *     colours through coverage + SrcOver.
 *
 * **Out of scope** for Phase 5a: bitmap shaders (`SkBitmap.makeShader`),
 * conic/sweep gradients, `SkColor4f` per-stop storage, colour-filter
 * compose, and tile modes other than [SkTileMode.kClamp] (the mode used
 * by every gradient GM in our scope).
 */
public abstract class SkShader internal constructor(
    /**
     * Optional shader-local matrix. The shader's source-space coords map
     * to canvas-source-space via this matrix; the canvas CTM then maps
     * canvas-source to device. Defaults to identity.
     */
    public val localMatrix: SkMatrix = SkMatrix.Identity,
) {
    /**
     * Cached per-draw inverse of `(canvasCtm · localMatrix)`. `null` if
     * the total matrix is singular — in which case the shader degenerates
     * to its first colour stop (matches Skia's behaviour).
     */
    protected var deviceToLocal: SkMatrix? = null
        private set

    /**
     * Called once per draw before any [shadeRow] invocation. Subclasses
     * override to also pre-transform their stops via [xform] (sRGB →
     * working-space-encoded ARGB bytes), then super-call.
     */
    public open fun setupForDraw(canvasCtm: SkMatrix, xform: SkColorSpaceXformSteps) {
        deviceToLocal = canvasCtm.preConcat(localMatrix).invert()
    }

    /**
     * Fill `dst[0 ..< count]` with the shader's colour at device-space
     * pixels `(devX + 0, devY)`, `(devX + 1, devY)`, …. Returned colours
     * are in the bitmap's working colour space — the caller passes them
     * straight to the SrcOver compositor without re-transforming.
     */
    public abstract fun shadeRow(devX: Int, devY: Int, count: Int, dst: IntArray)
}

// ---------------------------------------------------------------------------
// Internal helpers shared between linear / radial / future gradient kinds.
// ---------------------------------------------------------------------------

/**
 * Pre-transformed gradient stops: sRGB ARGB ints turned into working-
 * space ARGB ints by [transformInto], plus the sorted `t ∈ [0, 1]`
 * positions. Built once in [SkShader.setupForDraw].
 */
internal class GradientStopTable(
    val colors: IntArray,
    val positions: FloatArray,
)

/**
 * Apply the bitmap's xform to each [src] sRGB colour, writing working-
 * space ARGB into [dst]. Mirrors [org.skia.core.SkBitmapDevice]'s
 * `transformPaintColor` per-pixel logic but batches the iteration.
 */
internal fun transformStopColors(
    src: IntArray,
    dst: IntArray,
    xform: SkColorSpaceXformSteps,
) {
    val rgba = FloatArray(4)
    for (i in src.indices) {
        val c = src[i]
        if (xform.flags.isIdentity) {
            dst[i] = c
            continue
        }
        rgba[0] = SkColorGetR(c) / 255f
        rgba[1] = SkColorGetG(c) / 255f
        rgba[2] = SkColorGetB(c) / 255f
        rgba[3] = SkColorGetA(c) / 255f
        xform.apply(rgba)
        val outA = (rgba[3] * 255f + 0.5f).toInt().coerceIn(0, 255)
        val outR = (rgba[0] * 255f + 0.5f).toInt().coerceIn(0, 255)
        val outG = (rgba[1] * 255f + 0.5f).toInt().coerceIn(0, 255)
        val outB = (rgba[2] * 255f + 0.5f).toInt().coerceIn(0, 255)
        dst[i] = SkColorSetARGB(outA, outR, outG, outB)
    }
}

/**
 * Look up the gradient colour at parametric `t ∈ [0, 1]` (clamped under
 * [SkTileMode.kClamp], wrapped under [SkTileMode.kRepeat], reflected
 * under [SkTileMode.kMirror], or zero-alpha-out-of-range under
 * [SkTileMode.kDecal]). Lerps between adjacent stops in **premultiplied**
 * ARGB-byte channels.
 *
 * `colors` must already be in the bitmap's working colour space (caller
 * computes the table once via [transformStopColors]).
 */
internal fun lookupStop(
    t: SkScalar,
    positions: FloatArray,
    colors: IntArray,
    tileMode: SkTileMode,
): SkColor {
    val tt: Float = when (tileMode) {
        SkTileMode.kClamp -> t.coerceIn(0f, 1f)
        SkTileMode.kRepeat -> t - floor(t)
        SkTileMode.kMirror -> {
            val u = t * 0.5f
            val w = u - floor(u)
            if (w < 0.5f) (w * 2f) else (2f - w * 2f)
        }
        SkTileMode.kDecal -> {
            if (t < 0f || t > 1f) return 0   // transparent black
            t
        }
    }

    val n = positions.size
    if (n == 1) return colors[0]
    if (tt <= positions[0]) return colors[0]
    if (tt >= positions[n - 1]) return colors[n - 1]

    // Binary-search for the interval `[positions[lo], positions[hi]]` that
    // contains `tt`.
    var lo = 0
    var hi = n - 1
    while (lo + 1 < hi) {
        val mid = (lo + hi) ushr 1
        if (positions[mid] <= tt) lo = mid else hi = mid
    }
    val t0 = positions[lo]; val t1 = positions[hi]
    val u = if (t1 > t0) (tt - t0) / (t1 - t0) else 0f
    return lerpPremul(colors[lo], colors[hi], u)
}

/**
 * Linearly interpolate two ARGB SkColors in premultiplied byte space.
 * `t ∈ [0, 1]`. The straight-alpha lerp `(1-t)·A + t·B` over-saturates
 * RGB when the two colours have very different alphas; premultiplied
 * lerp gives the visually correct mid-point.
 */
private fun lerpPremul(a: SkColor, b: SkColor, t: Float): SkColor {
    val ta = (t * 256f).toInt().coerceIn(0, 256)   // 8.8 fixed
    val sa = 256 - ta

    val aA = SkColorGetA(a); val aR = SkColorGetR(a); val aG = SkColorGetG(a); val aB = SkColorGetB(a)
    val bA = SkColorGetA(b); val bR = SkColorGetR(b); val bG = SkColorGetG(b); val bB = SkColorGetB(b)

    // Premultiply.
    val aAR = (aR * aA + 127) / 255
    val aAG = (aG * aA + 127) / 255
    val aAB = (aB * aA + 127) / 255
    val bAR = (bR * bA + 127) / 255
    val bAG = (bG * bA + 127) / 255
    val bAB = (bB * bA + 127) / 255

    // Lerp.
    val pa = ((sa * aA + ta * bA) ushr 8)
    val pr = ((sa * aAR + ta * bAR) ushr 8)
    val pg = ((sa * aAG + ta * bAG) ushr 8)
    val pb = ((sa * aAB + ta * bAB) ushr 8)

    if (pa == 0) return 0
    val r = ((pr * 255 + (pa shr 1)) / pa).coerceIn(0, 255)
    val g = ((pg * 255 + (pa shr 1)) / pa).coerceIn(0, 255)
    val b2 = ((pb * 255 + (pa shr 1)) / pa).coerceIn(0, 255)
    return SkColorSetARGB(pa, r, g, b2)
}

/** Distance from `(0, 0)` to `(x, y)`. */
internal fun length(x: SkScalar, y: SkScalar): SkScalar = sqrt(x * x + y * y)
