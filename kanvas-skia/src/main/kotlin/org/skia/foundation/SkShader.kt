package org.skia.foundation


import org.graphiks.math.SkColor
import org.graphiks.math.SkColor4f
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.graphiks.math.SkColorSetARGB
import org.skia.core.SkColorSpaceXformSteps
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkScalar
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Identifies the concrete [SkShader] subclass and exposes its parameters
 * for bridge conversion without requiring cross-module type checks.
 * The bridge in [org.skia.kanvas.KanvasSkiaBridge] switches on this type.
 */
public sealed class ShaderKind {
    public data class Linear(
        val p0: org.graphiks.math.SkPoint,
        val p1: org.graphiks.math.SkPoint,
        val colors: IntArray,
        val positions: FloatArray,
        val tileMode: SkTileMode,
        val localMatrix: org.graphiks.math.SkMatrix = org.graphiks.math.SkMatrix.Identity,
    ) : ShaderKind() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Linear) return false
            return p0 == other.p0 && p1 == other.p1 &&
                colors.contentEquals(other.colors) &&
                positions.contentEquals(other.positions) &&
                tileMode == other.tileMode && localMatrix == other.localMatrix
        }
        override fun hashCode(): Int {
            var result = p0.hashCode()
            result = 31 * result + p1.hashCode()
            result = 31 * result + colors.contentHashCode()
            result = 31 * result + positions.contentHashCode()
            result = 31 * result + tileMode.hashCode()
            result = 31 * result + localMatrix.hashCode()
            return result
        }
    }

    public data class Radial(
        val center: org.graphiks.math.SkPoint,
        val radius: Float,
        val colors: IntArray,
        val positions: FloatArray,
        val tileMode: SkTileMode,
        val localMatrix: org.graphiks.math.SkMatrix = org.graphiks.math.SkMatrix.Identity,
    ) : ShaderKind() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Radial) return false
            return center == other.center && radius == other.radius &&
                colors.contentEquals(other.colors) &&
                positions.contentEquals(other.positions) &&
                tileMode == other.tileMode && localMatrix == other.localMatrix
        }
        override fun hashCode(): Int {
            var result = center.hashCode()
            result = 31 * result + radius.hashCode()
            result = 31 * result + colors.contentHashCode()
            result = 31 * result + positions.contentHashCode()
            result = 31 * result + tileMode.hashCode()
            result = 31 * result + localMatrix.hashCode()
            return result
        }
    }

    public data class Sweep(
        val center: org.graphiks.math.SkPoint,
        val startAngle: Float,
        val endAngle: Float,
        val colors: IntArray,
        val positions: FloatArray,
        val tileMode: SkTileMode,
        val localMatrix: org.graphiks.math.SkMatrix = org.graphiks.math.SkMatrix.Identity,
    ) : ShaderKind() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Sweep) return false
            return center == other.center && startAngle == other.startAngle &&
                endAngle == other.endAngle && colors.contentEquals(other.colors) &&
                positions.contentEquals(other.positions) &&
                tileMode == other.tileMode && localMatrix == other.localMatrix
        }
        override fun hashCode(): Int {
            var result = center.hashCode()
            result = 31 * result + startAngle.hashCode()
            result = 31 * result + endAngle.hashCode()
            result = 31 * result + colors.contentHashCode()
            result = 31 * result + positions.contentHashCode()
            result = 31 * result + tileMode.hashCode()
            result = 31 * result + localMatrix.hashCode()
            return result
        }
    }

    public data class Bitmap(
        val image: SkImage,
        val tileX: SkTileMode,
        val tileY: SkTileMode,
        val localMatrix: org.graphiks.math.SkMatrix = org.graphiks.math.SkMatrix.Identity,
    ) : ShaderKind()

    public data object Unknown : ShaderKind()
}

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
public abstract class SkShader protected constructor(
    /**
     * Optional shader-local matrix. The shader's source-space coords map
     * to canvas-source-space via this matrix; the canvas CTM then maps
     * canvas-source to device. Defaults to identity.
     */
    public val localMatrix: SkMatrix = SkMatrix.Identity,
) {
    /**
     * Identifies the concrete shader subclass and exposes its parameters.
     * Returns [ShaderKind.Unknown] by default; subclasses override to
     * return the appropriate kind. Used by the Kanvas bridge for type
     * conversion without cross-module type checks.
     */
    public open val shaderKind: ShaderKind get() = ShaderKind.Unknown

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

    /**
     * Mirrors Skia's `SkShader::makeWithColorFilter(sk_sp<SkColorFilter>)`.
     *
     * Returns a new shader that applies [filter] to every colour produced
     * by `this` shader before it reaches the paint pipeline.
     */
    public open fun makeWithColorFilter(filter: SkColorFilter): SkShader =
        SkColorFilterShader(this, filter)

    /**
     * R-final.2 — mirrors Skia's
     * [`SkShader::makeWithLocalMatrix`](https://github.com/google/skia/blob/main/src/shaders/SkShader.cpp#L26).
     *
     * Returns a new shader that, when sampled, behaves as if the input
     * `(canvasCtm)` had been pre-multiplied with [matrix] before being
     * mapped into this shader's local space. Equivalent to
     * `paint.shader = baseShader` and drawing under
     * `canvas.concat(matrix)`, but bound to the shader instance — the
     * canvas CTM stays untouched.
     *
     * **Folding** (matches upstream) : if `this` is already a
     * [SkLocalMatrixShader] (or any shader that exposes itself as one
     * via [makeAsALocalMatrixShader]), the two matrices fold into a
     * single `outer × inner` localMatrix wrapped around the original
     * proxy — no double wrapping. This keeps the per-draw inverse
     * computation a single 3×3 multiply instead of an N-deep chain.
     *
     * Identity is treated as a no-op : returning `this` directly avoids
     * an unnecessary wrapper allocation when callers route a default
     * `SkMatrix.Identity` through this method.
     */
    public fun makeWithLocalMatrix(matrix: SkMatrix): SkShader {
        if (matrix.isIdentity) return this
        // Upstream `makeWithLocalMatrix` first asks the shader if it can
        // be re-expressed as a (proxy, localMatrix) pair via
        // `makeAsALocalMatrixShader`. If so, it folds the new matrix
        // into the existing localMatrix and re-wraps the proxy ; this
        // avoids stacking N wrappers when callers chain the call.
        val (baseShader, foldedMatrix) = makeAsALocalMatrixShader()?.let { (proxy, childLm) ->
            // ConcatLocalMatrices(parent, child) = parent · child.
            proxy to matrix.preConcat(childLm)
        } ?: (this to matrix)
        return SkLocalMatrixShader(baseShader, foldedMatrix)
    }

    /**
     * R-final.2 — mirrors Skia's
     * [`SkShaderBase::makeAsALocalMatrixShader`](https://github.com/google/skia/blob/main/src/shaders/SkShaderBase.h#L383).
     *
     * If this shader can be represented as `(proxy, localMatrix)` —
     * i.e. it's structurally a [SkLocalMatrixShader] wrapper — return
     * the unwrapped pair so callers (notably [makeWithLocalMatrix])
     * can fold rather than nest. The default returns `null` (most
     * shaders are not local-matrix wrappers).
     */
    public open fun makeAsALocalMatrixShader(): Pair<SkShader, SkMatrix>? = null

    /**
     * R-final.3 — mirrors Skia's
     * [`SkShader::makeWithWorkingColorSpace`](https://github.com/google/skia/blob/main/src/shaders/SkWorkingColorSpaceShader.cpp).
     *
     * Returns a wrapper shader whose child renders as if the canvas's
     * destination colour space were [workingCS] instead of the bitmap's
     * actual working CS. Then the wrapper's output is re-xformed back
     * into the bitmap's working CS so the device can composite as
     * usual.
     *
     * Practical use case (per upstream `gm/workingspace.cpp`): drive a
     * gradient through `makeWithWorkingColorSpace(MakeSRGBLinear())`
     * to force interpolation in linear sRGB. Without the wrapper, a
     * red-to-green gradient interpolates through dark brown in
     * sRGB-encoded bytes ; with the wrapper it interpolates through
     * bright yellow in linear light, matching the visually-correct
     * outcome.
     *
     * Identity short-circuit : if [workingCS] is the same as the dst
     * CS at draw time, the wrapper falls back to plain forwarding
     * (no extra xform step).
     */
    public open fun makeWithWorkingColorSpace(workingCS: SkColorSpace): SkShader =
        SkWorkingColorSpaceShader(this, workingCS)
}

/**
 * R-final.2 — mirrors Skia's
 * [`SkLocalMatrixShader`](https://github.com/google/skia/blob/main/src/shaders/SkLocalMatrixShader.cpp).
 *
 * Wraps a child [SkShader] and pre-concatenates [localMatrix] into the
 * canvas CTM at draw setup time. The wrapped shader's per-pixel sampler
 * (`shadeRow` / `sampleAtLocal` / their F16 variants) runs unchanged —
 * its `deviceToLocal` inverse is just computed from the augmented CTM
 * passed via [setupForDraw], so this wrapper costs one extra 3×3
 * multiply per draw and zero per-pixel overhead.
 *
 * Public via the [org.skia.foundation.SkShader.makeWithLocalMatrix]
 * factory ; not constructible directly so call sites cannot bypass the
 * folding step that prevents N-deep wrapper stacks.
 */
/**
 * R-final.3 — wrapper that runs the [child] shader as if the canvas's
 * dst colour space were [workingCS] instead of the bitmap's actual
 * working CS, then xforms the per-pixel output back into the actual
 * working CS so the device's compositor sees the expected encoding.
 *
 * Per-draw lifecycle :
 *  1. [setupForDraw] receives the device's `xform: sRGB → bitmap.cs`.
 *     We synthesise a fake `xform: sRGB → workingCS` and forward that
 *     to the child — the child's pre-xformed stops, image samplers,
 *     etc. then encode their outputs as if they were targeting
 *     [workingCS].
 *  2. We additionally cache a `workingCS → bitmap.cs` post-xform that
 *     re-encodes the child's output before returning it to the
 *     device's compositor.
 *  3. [shadeRow] / [shadeRowF16] forward to the child, then run the
 *     post-xform on each returned colour.
 *
 * Identity short-circuits :
 *  - [workingCS] equals the bitmap's dst CS ⇒ pre-xform == device's
 *    own xform and post-xform == identity ⇒ behaves like a no-op
 *    forwarder. We don't bother detecting this — the child gets the
 *    same xform and the identity post-xform is a per-pixel no-op.
 *
 * Mirrors `SkWorkingColorSpaceShader`
 * (`src/shaders/SkWorkingColorSpaceShader.cpp`).
 */
internal class SkWorkingColorSpaceShader(
    private val child: SkShader,
    private val workingCS: SkColorSpace,
) : SkShader() {

    /** Post-xform applied per pixel : `workingCS → bitmap.dstCS`. */
    private var postXform: SkColorSpaceXformSteps? = null

    override fun setupForDraw(canvasCtm: SkMatrix, xform: SkColorSpaceXformSteps) {
        super.setupForDraw(canvasCtm, xform)
        // Build a child-facing xform that targets [workingCS] instead of
        // the bitmap's actual dst CS. The child believes its outputs
        // are bound for [workingCS] and pre-encodes its stops / pixels
        // accordingly.
        val srgbToWorking = SkColorSpaceXformSteps(
            src = SkColorSpace.makeSRGB(),
            srcAT = org.skia.core.SkAlphaType.kUnpremul,
            dst = workingCS,
            dstAT = org.skia.core.SkAlphaType.kUnpremul,
        )
        child.setupForDraw(canvasCtm, srgbToWorking)
        // Build the post-xform that brings child output (in workingCS)
        // back into the bitmap's actual dst CS. We can't see the dst CS
        // directly here — we know it only through `xform.dst` — but we
        // can synthesise the round-trip via sRGB : a single
        // `workingCS → dstCS` xform encodes the same end-to-end pipeline
        // as `workingCS → sRGB → dstCS` modulo numerics. For exact
        // bit-fidelity we reach into the device-supplied xform and
        // reconstruct its dst CS by inverting it through sRGB.
        //
        // The cheapest reliable route is to ask the device-supplied
        // xform "what's your dst CS?" via the [SkColorSpaceXformSteps]
        // public surface. That surface is not exposed yet — we fall
        // back to the round-trip via sRGB, which only adds two extra
        // TF evals per pixel and is bit-identical when sRGB is the
        // hub.
        postXform = if (xform.flags.isIdentity) {
            // Bitmap dst CS is sRGB — `workingCS → sRGB` covers the
            // whole post-xform.
            SkColorSpaceXformSteps(
                src = workingCS,
                srcAT = org.skia.core.SkAlphaType.kUnpremul,
                dst = SkColorSpace.makeSRGB(),
                dstAT = org.skia.core.SkAlphaType.kUnpremul,
            )
        } else {
            // Bitmap dst CS is some non-sRGB working space ; route via
            // sRGB through the supplied [xform]. We can't compose
            // arbitrary xforms in place, so we evaluate the round-trip
            // pixel-by-pixel via [applyPostXform].
            null
        }
        deviceXform = xform
    }

    private var deviceXform: SkColorSpaceXformSteps? = null

    /** Run `workingCS → dstCS` on a single 4-float quartet in place. */
    private fun applyPostXform(rgba: FloatArray) {
        // First : workingCS → sRGB (the cached path).
        val toSrgb = workingToSrgb
        toSrgb.apply(rgba)
        // Then : sRGB → dstCS (the device's existing xform).
        deviceXform?.let { it.apply(rgba) }
    }

    private val workingToSrgb: SkColorSpaceXformSteps =
        SkColorSpaceXformSteps(
            src = workingCS,
            srcAT = org.skia.core.SkAlphaType.kUnpremul,
            dst = SkColorSpace.makeSRGB(),
            dstAT = org.skia.core.SkAlphaType.kUnpremul,
        )

    override fun shadeRow(devX: Int, devY: Int, count: Int, dst: IntArray) {
        child.shadeRow(devX, devY, count, dst)
        val pf = postXform
        if (pf != null && pf.flags.isIdentity) return
        val rgba = FloatArray(4)
        for (i in 0 until count) {
            val c = dst[i]
            val a = SkColorGetA(c) / 255f
            rgba[0] = SkColorGetR(c) / 255f
            rgba[1] = SkColorGetG(c) / 255f
            rgba[2] = SkColorGetB(c) / 255f
            rgba[3] = a
            if (pf != null) pf.apply(rgba) else applyPostXform(rgba)
            val ai = (rgba[3].coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            val ri = (rgba[0].coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            val gi = (rgba[1].coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            val bi = (rgba[2].coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            dst[i] = SkColorSetARGB(ai, ri, gi, bi)
        }
    }

    override fun shadeRowF16(devX: Int, devY: Int, count: Int, dst: FloatArray) {
        require(dst.size >= count * 4) { "dst too small: ${dst.size} < ${count * 4}" }
        child.shadeRowF16(devX, devY, count, dst)
        val pf = postXform
        if (pf != null && pf.flags.isIdentity) return
        // child output is premul float in workingCS — un-premul, xform,
        // re-premul. We must do this per pixel since the xform pipeline
        // operates on un-premul colours.
        val rgba = FloatArray(4)
        var i = 0
        while (i < count * 4) {
            val a = dst[i + 3]
            if (a <= 0f) { i += 4; continue }
            val invA = 1f / a
            rgba[0] = dst[i] * invA
            rgba[1] = dst[i + 1] * invA
            rgba[2] = dst[i + 2] * invA
            rgba[3] = a
            if (pf != null) pf.apply(rgba) else applyPostXform(rgba)
            val outA = rgba[3]
            dst[i] = rgba[0] * outA
            dst[i + 1] = rgba[1] * outA
            dst[i + 2] = rgba[2] * outA
            dst[i + 3] = outA
            i += 4
        }
    }

    override fun sampleAtLocal(lx: Float, ly: Float): SkColor {
        val c = child.sampleAtLocal(lx, ly)
        val pf = postXform
        if (pf != null && pf.flags.isIdentity) return c
        val a = SkColorGetA(c) / 255f
        val rgba = floatArrayOf(
            SkColorGetR(c) / 255f,
            SkColorGetG(c) / 255f,
            SkColorGetB(c) / 255f,
            a,
        )
        if (pf != null) pf.apply(rgba) else applyPostXform(rgba)
        val ai = (rgba[3].coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        val ri = (rgba[0].coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        val gi = (rgba[1].coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        val bi = (rgba[2].coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        return SkColorSetARGB(ai, ri, gi, bi)
    }
}

/**
 * Wrapper shader matching Skia's private `SkColorFilterShader`.
 *
 * The child shader owns geometry and colour-space setup. This wrapper
 * only inserts [filter] after child evaluation, keeping the filter in
 * the same working colour domain as `SkPaint.colorFilter`.
 */
internal class SkColorFilterShader(
    private val child: SkShader,
    private val filter: SkColorFilter,
) : SkShader() {

    override fun setupForDraw(canvasCtm: SkMatrix, xform: SkColorSpaceXformSteps) {
        super.setupForDraw(canvasCtm, xform)
        child.setupForDraw(canvasCtm, xform)
    }

    override fun shadeRow(devX: Int, devY: Int, count: Int, dst: IntArray) {
        child.shadeRow(devX, devY, count, dst)
        for (i in 0 until count) {
            dst[i] = filter.filterColor(dst[i])
        }
    }

    override fun shadeRowF16(devX: Int, devY: Int, count: Int, dst: FloatArray) {
        require(dst.size >= count * 4) { "dst too small: ${dst.size} < ${count * 4}" }
        child.shadeRowF16(devX, devY, count, dst)
        var i = 0
        while (i < count * 4) {
            filterPremulF16(dst, i)
            i += 4
        }
    }

    override fun sampleAtLocal(lx: Float, ly: Float): SkColor =
        filter.filterColor(child.sampleAtLocal(lx, ly))

    override fun sampleAtLocalF16(lx: Float, ly: Float, dst: FloatArray, dstOffset: Int) {
        child.sampleAtLocalF16(lx, ly, dst, dstOffset)
        filterPremulF16(dst, dstOffset)
    }

    private fun filterPremulF16(dst: FloatArray, offset: Int) {
        val srcA = dst[offset + 3]
        val src = if (srcA > 0f) {
            val invA = 1f / srcA
            SkColor4f(
                dst[offset] * invA,
                dst[offset + 1] * invA,
                dst[offset + 2] * invA,
                srcA,
            )
        } else {
            SkColor4f(0f, 0f, 0f, srcA)
        }
        val out = filter.filterColor4f(src)
        dst[offset] = out.fR * out.fA
        dst[offset + 1] = out.fG * out.fA
        dst[offset + 2] = out.fB * out.fA
        dst[offset + 3] = out.fA
    }
}

internal class SkLocalMatrixShader(
    private val wrappedShader: SkShader,
    private val wrapperLocalMatrix: SkMatrix,
) : SkShader() {

    override fun setupForDraw(canvasCtm: SkMatrix, xform: SkColorSpaceXformSteps) {
        // Default `super.setupForDraw` would compute `(ctm · this.localMatrix)^-1`,
        // but this wrapper has no localMatrix of its own (the parent
        // ctor defaults to Identity) — instead we forward the augmented
        // ctm `(ctm · wrapperLocalMatrix)` to the child, which then
        // composes it with its own localMatrix as usual.
        super.setupForDraw(canvasCtm, xform)
        wrappedShader.setupForDraw(canvasCtm.preConcat(wrapperLocalMatrix), xform)
    }

    override fun shadeRow(devX: Int, devY: Int, count: Int, dst: IntArray) {
        wrappedShader.shadeRow(devX, devY, count, dst)
    }

    override fun shadeRowF16(devX: Int, devY: Int, count: Int, dst: FloatArray) {
        wrappedShader.shadeRowF16(devX, devY, count, dst)
    }

    override fun sampleAtLocal(lx: Float, ly: Float): SkColor {
        // Apply the wrapper localMatrix ourselves : `sampleAtLocal`
        // bypasses the per-draw `deviceToLocal` chain (it's the
        // "sample at this local point" entry point), so the wrapper
        // matrix needs to be applied here directly rather than via
        // `setupForDraw`.
        val (mlx, mly) = wrapperLocalMatrix.mapXY(lx, ly)
        return wrappedShader.sampleAtLocal(mlx, mly)
    }

    override fun sampleAtLocalF16(lx: Float, ly: Float, dst: FloatArray, dstOffset: Int) {
        val (mlx, mly) = wrapperLocalMatrix.mapXY(lx, ly)
        wrappedShader.sampleAtLocalF16(mlx, mly, dst, dstOffset)
    }

    /**
     * Expose the wrapped (proxy, localMatrix) so
     * [SkShader.makeWithLocalMatrix] can fold a new matrix into the
     * existing chain rather than stack a second wrapper.
     */
    override fun makeAsALocalMatrixShader(): Pair<SkShader, SkMatrix> =
        wrappedShader to wrapperLocalMatrix
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
public fun transformStopColors(
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
public fun lookupStop(
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
public fun length(x: SkScalar, y: SkScalar): SkScalar = sqrt(x * x + y * y)

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
public fun transformStopColorsF16(
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
public fun lookupStopF16(
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
