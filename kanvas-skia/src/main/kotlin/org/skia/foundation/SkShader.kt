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

    /**
     * Phase I5.3.c — sample the shader at an arbitrary point in
     * **shader-local space** (the space [localMatrix] maps from). Used
     * by [org.skia.core.SkCanvas.drawVertices] to look up texture
     * pixels at a triangle's interpolated UV ; bypasses the
     * `canvasCtm × localMatrix` chain that [shadeRow] applies.
     *
     * Default implementation : returns transparent black. Override in
     * subclasses that have a native "sample at a point" pipeline
     * (e.g. [org.skia.foundation.SkBitmapShader]). Callers must invoke
     * [setupForDraw] once before [sampleAtLocal] — same lifecycle as
     * [shadeRow].
     */
    public open fun sampleAtLocal(@Suppress("UNUSED_PARAMETER") lx: Float, @Suppress("UNUSED_PARAMETER") ly: Float): SkColor = 0

    /**
     * R-suivi.2 — float-domain counterpart of [sampleAtLocal]. Writes
     * four premultiplied floats (R, G, B, A in `[0, 1]`, in working
     * colour space) at `dst[dstOffset ..< dstOffset+4]`.
     *
     * Default implementation : forward to [sampleAtLocal] and unpack
     * the 8-bit ARGB into premul floats — same precision loss as the
     * byte path. Subclasses with a native F16 sampling pipeline
     * (e.g. gradients, [org.skia.foundation.SkBitmapShader] over a
     * kRGBA_F16Norm bitmap) override this to skip the byte round-trip.
     *
     * Used by wrapper shaders (e.g. `SkCoordClampShader`) that need
     * to sample the child at an arbitrary local point in F16 space
     * without going through a byte intermediate.
     */
    public open fun sampleAtLocalF16(lx: Float, ly: Float, dst: FloatArray, dstOffset: Int) {
        require(dst.size >= dstOffset + 4) { "dst too small at offset $dstOffset" }
        val c = sampleAtLocal(lx, ly)
        val a = SkColorGetA(c) / 255f
        dst[dstOffset]     = SkColorGetR(c) / 255f * a
        dst[dstOffset + 1] = SkColorGetG(c) / 255f * a
        dst[dstOffset + 2] = SkColorGetB(c) / 255f * a
        dst[dstOffset + 3] = a
    }

    /**
     * Phase 6b: float version of [shadeRow], called by the device when
     * compositing into a `kRGBA_F16Norm` bitmap. Writes 4 floats per pixel
     * (R, G, B, A — **premultiplied**, in the bitmap's working colour space,
     * each in `[0, 1]`) into `dst[0 ..< count * 4]` interleaved.
     *
     * The default implementation forwards to [shadeRow] and unpacks the
     * 8-bit ARGB back into premul floats — same precision loss as the 8-bit
     * compositor, only useful for shaders that don't have a native float
     * pipeline yet. Gradients override this to lerp directly in float
     * premul space, removing every byte-quantization step between the
     * source colours and the F16 buffer.
     */
    public open fun shadeRowF16(devX: Int, devY: Int, count: Int, dst: FloatArray) {
        require(dst.size >= count * 4) { "dst too small: ${dst.size} < ${count * 4}" }
        val tmp = IntArray(count)
        shadeRow(devX, devY, count, tmp)
        var di = 0
        for (i in 0 until count) {
            val c = tmp[i]
            val a = SkColorGetA(c) / 255f
            dst[di]     = SkColorGetR(c) / 255f * a
            dst[di + 1] = SkColorGetG(c) / 255f * a
            dst[di + 2] = SkColorGetB(c) / 255f * a
            dst[di + 3] = a
            di += 4
        }
    }
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

// ---------------------------------------------------------------------------
// Phase 6b: float-precision gradient helpers.
//
// Mirror [transformStopColors] / [lookupStop], but keep stop colours as
// premultiplied floats throughout. The two paths are independent (an 8-bit
// shader fills an 8-bit bitmap, an F16 shader fills an F16 bitmap), so each
// gradient subclass stores both tables and we never round-trip stops between
// representations.
// ---------------------------------------------------------------------------

/**
 * Apply `xform` to each [src] sRGB colour, then premultiply, writing
 * `[r, g, b, a]` quartets into [dst] (length `4 × src.size`). Output stays
 * in `[0, 1]` premultiplied float — no byte quantization at any step.
 */
internal fun transformStopColorsF16(
    src: IntArray,
    dst: FloatArray,
    xform: SkColorSpaceXformSteps,
) {
    require(dst.size >= src.size * 4)
    val rgba = FloatArray(4)
    for (i in src.indices) {
        val c = src[i]
        rgba[0] = SkColorGetR(c) / 255f
        rgba[1] = SkColorGetG(c) / 255f
        rgba[2] = SkColorGetB(c) / 255f
        rgba[3] = SkColorGetA(c) / 255f
        if (!xform.flags.isIdentity) xform.apply(rgba)
        val a = rgba[3]
        val o = i * 4
        dst[o]     = rgba[0] * a
        dst[o + 1] = rgba[1] * a
        dst[o + 2] = rgba[2] * a
        dst[o + 3] = a
    }
}

/**
 * Look up the gradient colour at parametric `t ∈ [0, 1]` in float-premul
 * space, writing 4 floats (R, G, B, A) at `dst[dstOffset ..< dstOffset+4]`.
 *
 * `colorsF16` holds 4 premultiplied floats per stop (built once via
 * [transformStopColorsF16]). Lerp in premul space is a straight 4-channel
 * `(1−u)·A + u·B` — no per-channel un-premul/re-premul (compare with the
 * 8-bit [lookupStop] which has to round-trip because byte premul lerp would
 * over-saturate when alphas differ).
 */
internal fun lookupStopF16(
    t: SkScalar,
    positions: FloatArray,
    colorsF16: FloatArray,
    tileMode: SkTileMode,
    dst: FloatArray,
    dstOffset: Int,
) {
    val tt: Float = when (tileMode) {
        SkTileMode.kClamp -> t.coerceIn(0f, 1f)
        SkTileMode.kRepeat -> t - floor(t)
        SkTileMode.kMirror -> {
            val u = t * 0.5f
            val w = u - floor(u)
            if (w < 0.5f) (w * 2f) else (2f - w * 2f)
        }
        SkTileMode.kDecal -> {
            if (t < 0f || t > 1f) {
                dst[dstOffset]     = 0f
                dst[dstOffset + 1] = 0f
                dst[dstOffset + 2] = 0f
                dst[dstOffset + 3] = 0f
                return
            }
            t
        }
    }

    val n = positions.size
    if (n == 1) {
        dst[dstOffset]     = colorsF16[0]
        dst[dstOffset + 1] = colorsF16[1]
        dst[dstOffset + 2] = colorsF16[2]
        dst[dstOffset + 3] = colorsF16[3]
        return
    }
    if (tt <= positions[0]) {
        dst[dstOffset]     = colorsF16[0]
        dst[dstOffset + 1] = colorsF16[1]
        dst[dstOffset + 2] = colorsF16[2]
        dst[dstOffset + 3] = colorsF16[3]
        return
    }
    if (tt >= positions[n - 1]) {
        val o = (n - 1) * 4
        dst[dstOffset]     = colorsF16[o]
        dst[dstOffset + 1] = colorsF16[o + 1]
        dst[dstOffset + 2] = colorsF16[o + 2]
        dst[dstOffset + 3] = colorsF16[o + 3]
        return
    }

    var lo = 0
    var hi = n - 1
    while (lo + 1 < hi) {
        val mid = (lo + hi) ushr 1
        if (positions[mid] <= tt) lo = mid else hi = mid
    }
    val t0 = positions[lo]; val t1 = positions[hi]
    val u = if (t1 > t0) (tt - t0) / (t1 - t0) else 0f
    val iu = 1f - u
    val oa = lo * 4
    val ob = hi * 4
    dst[dstOffset]     = iu * colorsF16[oa]     + u * colorsF16[ob]
    dst[dstOffset + 1] = iu * colorsF16[oa + 1] + u * colorsF16[ob + 1]
    dst[dstOffset + 2] = iu * colorsF16[oa + 2] + u * colorsF16[ob + 2]
    dst[dstOffset + 3] = iu * colorsF16[oa + 3] + u * colorsF16[ob + 3]
}
