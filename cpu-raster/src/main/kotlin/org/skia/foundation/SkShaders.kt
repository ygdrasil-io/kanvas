package org.skia.foundation


import org.graphiks.math.SkColor
import org.graphiks.math.SkColor4f
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.graphiks.math.SkColorSetARGB
import org.skia.core.SkColorSpaceXformSteps
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.graphiks.math.SkScalar

/**
 * Mirrors Skia's `SkShaders` factory namespace (declared in
 * [include/core/SkShader.h](https://github.com/google/skia/blob/main/include/core/SkShader.h)
 * and [include/effects/SkPerlinNoiseShader.h](https://github.com/google/skia/blob/main/include/effects/SkPerlinNoiseShader.h)).
 *
 * Ships :
 *  - [Color] — constant-colour shader, equivalent to `paint.color = c`
 *    but expressed as an explicit [SkShader] so it composes with
 *    `makeWithLocalMatrix` / `makeWithColorFilter`.
 *  - [CoordClamp] — wraps a child shader and clamps its evaluation to
 *    a sub-rectangle of local space; samples outside the rect return
 *    the colour at the nearest edge.
 *  - [Empty] — transparent-black shader; the upstream null-pattern for
 *    "no contribution".
 *  - [Blend] — pixel-wise blend of two child shaders by a [SkBlendMode],
 *    matching upstream `SkBlendShader`.
 *  - [MakeFractalNoise] / [MakeTurbulence] — SVG `feTurbulence` Perlin
 *    noise, delegating to the existing [SkPerlinNoiseShader] subclass.
 *
 * Not yet exposed here : `Image` / `RawImage` (callers reach for
 * [SkBitmap.makeShader] directly), and `Blend(SkBlender, …)` (the
 * runtime-blender form, dependent on D2).
 */
public object SkShaders {

    /**
     * Mirrors Skia's `SkShaders::Color(SkColor)` — returns a shader
     * that produces [c] for every covered pixel. The colour is applied
     * in the bitmap's working colour space — the shader runs the
     * standard sRGB → working-space transform once in
     * [SkShader.setupForDraw] and caches the result.
     */
    public fun Color(c: SkColor): SkShader = SkColorShader(c)

    /**
     * Mirrors Skia's `SkShaders::Color(const SkColor4f&, sk_sp<SkColorSpace>)` —
     * F32-component constant-colour shader with an explicit input
     * colour space.
     *
     * Implementation : if [cs] is `null` or already sRGB, the float
     * tuple in [c] is taken to be sRGB-encoded; we encode to bytes via
     * [SkColor4f.toSkColor] and delegate to the byte-colour [Color]
     * factory above. Otherwise we eagerly run a `cs → sRGB` xform on
     * the unpremul float tuple, encode to bytes, and forward — the
     * second hop (`sRGB → working-cs`) is handled by the byte-colour
     * shader's normal [setupForDraw] pipeline.
     *
     * Throws [IllegalArgumentException] if any component of [c] is not
     * finite (mirrors upstream's `SkScalarsAreFinite` precondition).
     */
    public fun Color(c: SkColor4f, cs: SkColorSpace?): SkShader {
        require(c.fR.isFinite() && c.fG.isFinite() && c.fB.isFinite() && c.fA.isFinite()) {
            "SkColor4f components must be finite, got $c"
        }
        if (cs == null || cs.isSRGB()) {
            return SkColorShader(c.toSkColor())
        }
        // Eagerly convert cs → sRGB unpremul, then encode bytes. The
        // byte-colour shader's setupForDraw will run the second hop
        // (sRGB → working colour space) per-draw.
        val rgba = floatArrayOf(c.fR, c.fG, c.fB, c.fA)
        val toSrgb = SkColorSpaceXformSteps(
            src = cs, srcAT = org.skia.core.SkAlphaType.kUnpremul,
            dst = SkColorSpace.makeSRGB(), dstAT = org.skia.core.SkAlphaType.kUnpremul,
        )
        toSrgb.apply(rgba)
        val srgb = SkColor4f(rgba[0], rgba[1], rgba[2], rgba[3]).toSkColor()
        return SkColorShader(srgb)
    }

    /**
     * Mirrors Skia's `SkShaders::CoordClamp(sk_sp<SkShader>, const SkRect&)`.
     *
     * Returns a shader that delegates to [shader] after clamping the
     * shader-local sample coordinate into [rect]. The clamp is **per
     * sample**, not a clipping operation : pixels outside the rect
     * still draw — they just sample the child at the nearest point on
     * the rect's boundary (the upstream semantic).
     *
     * Throws [IllegalArgumentException] if [rect] is empty.
     */
    public fun CoordClamp(shader: SkShader, rect: SkRect): SkShader {
        require(rect.width() > 0f && rect.height() > 0f) {
            "CoordClamp requires a non-empty rect, got $rect"
        }
        return SkCoordClampShader(shader, rect)
    }

    /**
     * Mirrors Skia's `SkShaders::Empty()` — a shader that produces
     * transparent black for every covered pixel. Upstream uses this as
     * the "no contribution" null pattern in compositional pipelines
     * (e.g. a `SkBlend`'s child that's been culled). It composes
     * cleanly with [makeWithColorFilter] / [Blend] / etc. so callers
     * never need to special-case "no shader".
     */
    public fun Empty(): SkShader = SkEmptyShader

    /**
     * Mirrors Skia's `SkShaders::Blend(SkBlendMode, sk_sp<SkShader> dst,
     * sk_sp<SkShader> src)` — runs [dst] and [src] independently, then
     * blends the two pixel streams under [mode] (with `src` on top of
     * `dst`, matching the upstream argument order). Equivalent to
     * "draw dst, then draw src on top with `mode`" but expressed as a
     * single shader so it composes inside paint pipelines that take
     * one shader.
     *
     * The combination is evaluated per-pixel : we ask each child for
     * its row (or sample), then run the same float-domain blend
     * recipe upstream uses (`SkBlenders::Mode` lowered to a raster-
     * pipeline blend op). All 29 [SkBlendMode] values that
     * [SkBitmapDevice.blendPixel] supports are handled; unsupported
     * modes fall back to SrcOver to match the rasterizer's behaviour.
     */
    public fun Blend(mode: SkBlendMode, dst: SkShader, src: SkShader): SkShader =
        SkBlendShader(mode, dst, src)

    /**
     * Mirrors Skia's `SkShaders::LinearGradient(pts, SkGradient)` overload
     * for RGB interpolation spaces.
     *
     * CSS perceptual spaces (`Lab`, `OKLab`, `LCH`, `OKLCH`, `HSL`, `HWB`),
     * hue methods, and premul interpolation remain explicit stubs because the
     * existing raster gradient sampler only interpolates RGB stops.
     */
    public fun LinearGradient(
        pts: Array<SkPoint>,
        gradient: SkGradient,
        localMatrix: SkMatrix = SkMatrix.Identity,
    ): SkShader {
        require(pts.size >= 2) { "SkShaders.LinearGradient requires two points" }
        if (gradient.requiresDedicatedSampler()) {
            gradient.validateDedicatedSampler()
            return SkInterpolatedLinearGradient(
                pts[0],
                pts[1],
                gradient.colors,
                gradient.positions ?: evenlySpacedPositions(gradient.colors.size),
                gradient.tileMode,
                gradient.interpolation,
                localMatrix,
            )
        }
        val shader = SkLinearGradient.Make(
            pts[0],
            pts[1],
            gradient.colors,
            gradient.positions,
            gradient.tileMode,
            localMatrix,
        )
        val workingCS = gradient.workingColorSpaceOrNull()
        return if (workingCS == null) shader else shader.makeWithWorkingColorSpace(workingCS)
    }

    /**
     * Mirrors Skia's `SkShaders::MakeFractalNoise(...)` — SVG
     * `feTurbulence` Perlin fractal-noise shader. Delegates to the
     * existing [SkPerlinNoiseShader.MakeFractalNoise] companion
     * factory, which carries the byte-for-byte port of upstream's
     * lattice / noise table generation and per-pixel sampling.
     *
     * `baseFreqX` / `baseFreqY` are typically in `(0, 1)` and must be
     * non-negative; [numOctaves] is in `[0, 255]`; [seed] is truncated
     * to an int per the SVG spec. If [tileSize] is non-null and
     * non-empty the noise tiles stitch seamlessly at that size.
     */
    public fun MakeFractalNoise(
        baseFreqX: SkScalar,
        baseFreqY: SkScalar,
        numOctaves: Int,
        seed: SkScalar,
        tileSize: SkISize? = null,
    ): SkShader = SkPerlinNoiseShader.MakeFractalNoise(
        baseFreqX, baseFreqY, numOctaves, seed, tileSize,
    )

    /**
     * Mirrors Skia's `SkShaders::MakeTurbulence(...)` — SVG
     * `feTurbulence` Perlin turbulence shader (i.e. `|fractal noise|`).
     * Same parameter contract as [MakeFractalNoise], delegating to
     * [SkPerlinNoiseShader.MakeTurbulence].
     */
    public fun MakeTurbulence(
        baseFreqX: SkScalar,
        baseFreqY: SkScalar,
        numOctaves: Int,
        seed: SkScalar,
        tileSize: SkISize? = null,
    ): SkShader = SkPerlinNoiseShader.MakeTurbulence(
        baseFreqX, baseFreqY, numOctaves, seed, tileSize,
    )
}

// -- Internal concrete implementations --------------------------------------

/**
 * Constant-colour shader. Produces the same [SkColor] for every
 * covered pixel. The sRGB → working-space transform is applied once
 * in [setupForDraw] (the shader exposes its colour in **working
 * colour space**, matching the [SkShader] contract).
 *
 * Mirrors Skia's `SkColorShader` (private subclass of `SkShader`).
 */
internal class SkColorShader(private val srcColor: SkColor) : SkShader() {

    /** Working-space colour, computed once per draw. */
    private var workingColor: SkColor = srcColor

    /** Premul float-quartet for the F16 fast path. */
    private val workingColorF16: FloatArray = FloatArray(4)

    override fun setupForDraw(canvasCtm: SkMatrix, xform: SkColorSpaceXformSteps) {
        super.setupForDraw(canvasCtm, xform)
        // Byte path : run the xform once and cache the ARGB int.
        if (xform.flags.isIdentity) {
            workingColor = srcColor
        } else {
            val rgba = floatArrayOf(
                SkColorGetR(srcColor) / 255f,
                SkColorGetG(srcColor) / 255f,
                SkColorGetB(srcColor) / 255f,
                SkColorGetA(srcColor) / 255f,
            )
            xform.apply(rgba)
            val a = (rgba[3] * 255f + 0.5f).toInt().coerceIn(0, 255)
            val r = (rgba[0] * 255f + 0.5f).toInt().coerceIn(0, 255)
            val g = (rgba[1] * 255f + 0.5f).toInt().coerceIn(0, 255)
            val b = (rgba[2] * 255f + 0.5f).toInt().coerceIn(0, 255)
            workingColor = SkColorSetARGB(a, r, g, b)
        }
        // F16 path : premul floats, no byte quantization.
        val rgba = floatArrayOf(
            SkColorGetR(srcColor) / 255f,
            SkColorGetG(srcColor) / 255f,
            SkColorGetB(srcColor) / 255f,
            SkColorGetA(srcColor) / 255f,
        )
        if (!xform.flags.isIdentity) xform.apply(rgba)
        val a = rgba[3]
        workingColorF16[0] = rgba[0] * a
        workingColorF16[1] = rgba[1] * a
        workingColorF16[2] = rgba[2] * a
        workingColorF16[3] = a
    }

    override fun shadeRow(devX: Int, devY: Int, count: Int, dst: IntArray) {
        val c = workingColor
        for (i in 0 until count) dst[i] = c
    }

    override fun shadeRowF16(devX: Int, devY: Int, count: Int, dst: FloatArray) {
        require(dst.size >= count * 4) { "dst too small: ${dst.size} < ${count * 4}" }
        var di = 0
        val r = workingColorF16[0]
        val g = workingColorF16[1]
        val b = workingColorF16[2]
        val a = workingColorF16[3]
        for (i in 0 until count) {
            dst[di]     = r
            dst[di + 1] = g
            dst[di + 2] = b
            dst[di + 3] = a
            di += 4
        }
    }

    override fun sampleAtLocal(lx: Float, ly: Float): SkColor = workingColor

    override fun sampleAtLocalF16(lx: Float, ly: Float, dst: FloatArray, dstOffset: Int) {
        require(dst.size >= dstOffset + 4)
        dst[dstOffset]     = workingColorF16[0]
        dst[dstOffset + 1] = workingColorF16[1]
        dst[dstOffset + 2] = workingColorF16[2]
        dst[dstOffset + 3] = workingColorF16[3]
    }
}

/**
 * Coordinate-clamping wrapper. Forwards each row request to the child
 * shader after pinning every per-pixel local-space coordinate into
 * [rect]. Implementation strategy :
 *
 * 1. Forward [setupForDraw] to the child so it can pre-compute its
 *    own per-draw state (colour-stop tables, xformed pixels, …).
 * 2. Override [sampleAtLocal] so the wrapper composes naturally with
 *    [org.skia.core.SkCanvas.drawVertices] and any other caller that
 *    samples in shader-local space.
 * 3. For [shadeRow] / [shadeRowF16], we walk device-space coordinates,
 *    map them into local space ourselves (the child's `deviceToLocal`
 *    matrix is the same as ours since we share the parent localMatrix),
 *    clamp into [rect], and call back into the child's
 *    [sampleAtLocal] one pixel at a time.
 *
 * Per-pixel `sampleAtLocal` is slower than a child's bulk `shadeRow`,
 * but it's the only way to honour the clamp boundary correctly when
 * the child doesn't expose a "sample at this local point" entry
 * point in its row API. Acceptable for R1 — the bulk path can be
 * hoisted in a later micro-opt slice if a GM hits it on a hot path.
 *
 * Mirrors Skia's `SkCoordClampShader` (private subclass).
 */
internal class SkCoordClampShader(
    private val child: SkShader,
    private val rect: SkRect,
) : SkShader() {

    override fun setupForDraw(canvasCtm: SkMatrix, xform: SkColorSpaceXformSteps) {
        super.setupForDraw(canvasCtm, xform)
        child.setupForDraw(canvasCtm, xform)
    }

    override fun shadeRow(devX: Int, devY: Int, count: Int, dst: IntArray) {
        val inv = deviceToLocal
        if (inv == null) {
            for (i in 0 until count) dst[i] = 0
            return
        }
        val y0 = devY + 0.5f
        var x0 = devX + 0.5f
        val stepX = inv.sx
        val stepY = inv.ky
        var lx = inv.sx * x0 + inv.kx * y0 + inv.tx
        var ly = inv.ky * x0 + inv.sy * y0 + inv.ty
        for (i in 0 until count) {
            val cx = lx.coerceIn(rect.left, rect.right)
            val cy = ly.coerceIn(rect.top, rect.bottom)
            dst[i] = child.sampleAtLocal(cx, cy)
            lx += stepX
            ly += stepY
        }
    }

    override fun shadeRowF16(devX: Int, devY: Int, count: Int, dst: FloatArray) {
        require(dst.size >= count * 4) { "dst too small: ${dst.size} < ${count * 4}" }
        // R-suivi.2 — Direct F16 sampling : walk device-space coords,
        // map them into local space, clamp into [rect] in float space,
        // then ask the child for its premul-float sample at that
        // local point via [sampleAtLocalF16]. No byte intermediate, so
        // HDR sources (with components > 1.0) preserve precision —
        // the previous implementation routed through `shadeRow` which
        // quantised every channel to 8-bit before promoting back to
        // float, losing every above-1.0 component on the way.
        val inv = deviceToLocal
        if (inv == null) {
            for (i in 0 until count * 4) dst[i] = 0f
            return
        }
        val y0 = devY + 0.5f
        val x0 = devX + 0.5f
        val stepX = inv.sx
        val stepY = inv.ky
        var lx = inv.sx * x0 + inv.kx * y0 + inv.tx
        var ly = inv.ky * x0 + inv.sy * y0 + inv.ty
        var di = 0
        for (i in 0 until count) {
            val cx = lx.coerceIn(rect.left, rect.right)
            val cy = ly.coerceIn(rect.top, rect.bottom)
            child.sampleAtLocalF16(cx, cy, dst, di)
            lx += stepX
            ly += stepY
            di += 4
        }
    }

    override fun sampleAtLocal(lx: Float, ly: Float): SkColor =
        child.sampleAtLocal(
            lx.coerceIn(rect.left, rect.right),
            ly.coerceIn(rect.top, rect.bottom),
        )

    override fun sampleAtLocalF16(lx: Float, ly: Float, dst: FloatArray, dstOffset: Int) {
        child.sampleAtLocalF16(
            lx.coerceIn(rect.left, rect.right),
            ly.coerceIn(rect.top, rect.bottom),
            dst,
            dstOffset,
        )
    }
}

/**
 * Transparent-black shader. Mirrors Skia's `SkEmptyShader` — every
 * sample is `(0, 0, 0, 0)` regardless of CTM or local matrix, so we
 * declare it as a Kotlin `object` (no per-instance state). The
 * `setupForDraw` no-op stays inherited (the default implementation
 * still caches the device-to-local matrix, but we never read it).
 */
internal object SkEmptyShader : SkShader() {
    override fun shadeRow(devX: Int, devY: Int, count: Int, dst: IntArray) {
        for (i in 0 until count) dst[i] = 0
    }

    override fun shadeRowF16(devX: Int, devY: Int, count: Int, dst: FloatArray) {
        require(dst.size >= count * 4) { "dst too small: ${dst.size} < ${count * 4}" }
        for (i in 0 until count * 4) dst[i] = 0f
    }

    override fun sampleAtLocal(lx: Float, ly: Float): SkColor = 0
}

/**
 * Two-child pixel-wise blend shader. Mirrors upstream `SkBlendShader`
 * (private subclass; see `src/shaders/SkBlendShader.cpp`).
 *
 * Per-draw lifecycle :
 *  1. [setupForDraw] forwards to both children so they can build their
 *     own per-draw caches (xformed stop tables, lattices, …) — the
 *     base-class deviceToLocal inverse is also captured, but we don't
 *     consume it directly (the children own the geometry).
 *  2. [shadeRow] asks each child for its row into a scratch buffer,
 *     then runs [blendPremulF32Into] per pixel to produce the output.
 *  3. [shadeRowF16] mirrors (2) on the float-premul fast path : we
 *     fetch each child's F16 row and blend in premul-float space
 *     directly, no byte round-trip.
 *
 * Reference for the blend formulas : the float-premul recipe in
 * `org.skia.foundation.blendPixel` inside `SkImageFilters.kt`, which
 * itself mirrors `include/core/SkBlendMode.h`. We keep our copy local
 * here rather than depending on that private helper — small footprint
 * (29 modes × four-tuple arithmetic), and the duplication keeps the
 * shader self-contained.
 *
 * **Argument order matches Skia** : upstream is `Blend(mode, dst, src)`
 * — `src` is the *top* layer, `dst` the *bottom*. The shader output
 * for each pixel is `blend(mode, src, dst)`.
 */
internal class SkBlendShader(
    private val mode: SkBlendMode,
    private val dst: SkShader,
    private val src: SkShader,
) : SkShader() {

    override fun setupForDraw(canvasCtm: SkMatrix, xform: SkColorSpaceXformSteps) {
        super.setupForDraw(canvasCtm, xform)
        dst.setupForDraw(canvasCtm, xform)
        src.setupForDraw(canvasCtm, xform)
    }

    override fun shadeRow(devX: Int, devY: Int, count: Int, dst: IntArray) {
        val dstRow = IntArray(count)
        val srcRow = IntArray(count)
        this.dst.shadeRow(devX, devY, count, dstRow)
        this.src.shadeRow(devX, devY, count, srcRow)
        for (i in 0 until count) {
            dst[i] = blendBytes(mode, srcRow[i], dstRow[i])
        }
    }

    override fun shadeRowF16(devX: Int, devY: Int, count: Int, dst: FloatArray) {
        require(dst.size >= count * 4) { "dst too small: ${dst.size} < ${count * 4}" }
        val dstRow = FloatArray(count * 4)
        val srcRow = FloatArray(count * 4)
        this.dst.shadeRowF16(devX, devY, count, dstRow)
        this.src.shadeRowF16(devX, devY, count, srcRow)
        var i = 0
        while (i < count * 4) {
            blendPremulF32Into(
                mode,
                srcRow[i], srcRow[i + 1], srcRow[i + 2], srcRow[i + 3],
                dstRow[i], dstRow[i + 1], dstRow[i + 2], dstRow[i + 3],
                dst, i,
            )
            i += 4
        }
    }

    override fun sampleAtLocal(lx: Float, ly: Float): SkColor =
        blendBytes(mode, src.sampleAtLocal(lx, ly), dst.sampleAtLocal(lx, ly))
}

// -- Blend math (premul float pipeline) -------------------------------------
//
// Both `shadeRow` and `shadeRowF16` route through the same float-premul
// blend kernel. The byte path quantises at the end; the F16 path keeps
// floats throughout. Coverage and precision identical to the float
// blend in `SkImageFilters.kt::blendPixel`, with one shared kernel
// here so both `SkBlendShader` row paths stay in sync.

/**
 * Byte-domain blend : decode `srcARGB` / `dstARGB` (non-premul bytes) to
 * premul floats, run [blendPremulF32Into], encode back to non-premul
 * ARGB bytes. Result alpha-0 short-circuits to `0` (transparent black,
 * matches upstream's "alpha-zero ⇒ identity zero").
 */
private fun blendBytes(mode: SkBlendMode, srcARGB: SkColor, dstARGB: SkColor): SkColor {
    val sa = SkColorGetA(srcARGB) / 255f
    val sr = SkColorGetR(srcARGB) / 255f * sa
    val sg = SkColorGetG(srcARGB) / 255f * sa
    val sb = SkColorGetB(srcARGB) / 255f * sa
    val da = SkColorGetA(dstARGB) / 255f
    val dr = SkColorGetR(dstARGB) / 255f * da
    val dg = SkColorGetG(dstARGB) / 255f * da
    val db = SkColorGetB(dstARGB) / 255f * da
    val out = FloatArray(4)
    blendPremulF32Into(mode, sr, sg, sb, sa, dr, dg, db, da, out, 0)
    val oR = out[0]; val oG = out[1]; val oB = out[2]; val oA = out[3]
    val ai = (oA * 255f + 0.5f).toInt().coerceIn(0, 255)
    if (ai == 0) return 0
    val inv = 1f / oA
    val ri = (oR * inv * 255f + 0.5f).toInt().coerceIn(0, 255)
    val gi = (oG * inv * 255f + 0.5f).toInt().coerceIn(0, 255)
    val bi = (oB * inv * 255f + 0.5f).toInt().coerceIn(0, 255)
    return SkColorSetARGB(ai, ri, gi, bi)
}

/**
 * Premul-float blend kernel. Inputs `(sr, sg, sb, sa)` and `(dr, dg, db,
 * da)` are premultiplied source / destination colours; output 4 floats
 * (R, G, B, A) are written to `out[outOffset ..< outOffset+4]` still in
 * premul space.
 *
 * Coverage : the 13 Porter-Duff modes (Clear, Src, Dst, SrcOver,
 * DstOver, SrcIn, DstIn, SrcOut, DstOut, SrcATop, DstATop, Xor, Plus),
 * Modulate, Screen, Multiply, Darken, Lighten. The remaining advanced /
 * HSL modes fall back to SrcOver — same accuracy budget as the
 * existing `SkImageFilters.blendPixel` helper.
 */
private fun blendPremulF32Into(
    mode: SkBlendMode,
    sr: Float, sg: Float, sb: Float, sa: Float,
    dr: Float, dg: Float, db: Float, da: Float,
    out: FloatArray, outOffset: Int,
) {
    val oR: Float; val oG: Float; val oB: Float; val oA: Float
    when (mode) {
        SkBlendMode.kClear -> { oR = 0f; oG = 0f; oB = 0f; oA = 0f }
        SkBlendMode.kSrc -> { oR = sr; oG = sg; oB = sb; oA = sa }
        SkBlendMode.kDst -> { oR = dr; oG = dg; oB = db; oA = da }
        SkBlendMode.kSrcOver -> {
            val ia = 1f - sa
            oR = sr + dr * ia; oG = sg + dg * ia; oB = sb + db * ia; oA = sa + da * ia
        }
        SkBlendMode.kDstOver -> {
            val ia = 1f - da
            oR = dr + sr * ia; oG = dg + sg * ia; oB = db + sb * ia; oA = da + sa * ia
        }
        SkBlendMode.kSrcIn  -> { oR = sr * da; oG = sg * da; oB = sb * da; oA = sa * da }
        SkBlendMode.kDstIn  -> { oR = dr * sa; oG = dg * sa; oB = db * sa; oA = da * sa }
        SkBlendMode.kSrcOut -> {
            val ia = 1f - da
            oR = sr * ia; oG = sg * ia; oB = sb * ia; oA = sa * ia
        }
        SkBlendMode.kDstOut -> {
            val ia = 1f - sa
            oR = dr * ia; oG = dg * ia; oB = db * ia; oA = da * ia
        }
        SkBlendMode.kSrcATop -> {
            val ia = 1f - sa
            oR = sr * da + dr * ia; oG = sg * da + dg * ia
            oB = sb * da + db * ia; oA = da
        }
        SkBlendMode.kDstATop -> {
            val ia = 1f - da
            oR = dr * sa + sr * ia; oG = dg * sa + sg * ia
            oB = db * sa + sb * ia; oA = sa
        }
        SkBlendMode.kXor -> {
            val isa = 1f - sa; val ida = 1f - da
            oR = sr * ida + dr * isa; oG = sg * ida + dg * isa
            oB = sb * ida + db * isa; oA = sa * ida + da * isa
        }
        SkBlendMode.kPlus -> {
            oR = (sr + dr).coerceAtMost(1f); oG = (sg + dg).coerceAtMost(1f)
            oB = (sb + db).coerceAtMost(1f); oA = (sa + da).coerceAtMost(1f)
        }
        SkBlendMode.kModulate -> { oR = sr * dr; oG = sg * dg; oB = sb * db; oA = sa * da }
        SkBlendMode.kScreen -> {
            oR = sr + dr - sr * dr; oG = sg + dg - sg * dg
            oB = sb + db - sb * db; oA = sa + da - sa * da
        }
        SkBlendMode.kMultiply -> {
            val isa = 1f - sa; val ida = 1f - da
            oR = sr * ida + dr * isa + sr * dr
            oG = sg * ida + dg * isa + sg * dg
            oB = sb * ida + db * isa + sb * db
            oA = sa + da - sa * da
        }
        SkBlendMode.kDarken -> {
            val isa = 1f - sa; val ida = 1f - da
            oR = minOf(sr + dr * isa, dr + sr * ida)
            oG = minOf(sg + dg * isa, dg + sg * ida)
            oB = minOf(sb + db * isa, db + sb * ida)
            oA = sa + da - sa * da
        }
        SkBlendMode.kLighten -> {
            val isa = 1f - sa; val ida = 1f - da
            oR = maxOf(sr + dr * isa, dr + sr * ida)
            oG = maxOf(sg + dg * isa, dg + sg * ida)
            oB = maxOf(sb + db * isa, db + sb * ida)
            oA = sa + da - sa * da
        }
        else -> {
            // Advanced / HSL modes — fall back to SrcOver. Matches the
            // `SkImageFilters.blendPixel` budget; a future slice can
            // promote these once the rasterizer's HSL/separable paths
            // are extracted into a shared helper.
            val ia = 1f - sa
            oR = sr + dr * ia; oG = sg + dg * ia; oB = sb + db * ia; oA = sa + da * ia
        }
    }
    out[outOffset]     = oR
    out[outOffset + 1] = oG
    out[outOffset + 2] = oB
    out[outOffset + 3] = oA
}
