package org.skia.foundation

import org.skia.core.SkColorSpaceXformSteps
import org.skia.math.SkMatrix
import org.skia.math.SkRect

/**
 * Mirrors Skia's `SkShaders` factory namespace (declared in
 * [include/core/SkShader.h](https://github.com/google/skia/blob/main/include/core/SkShader.h)).
 *
 * Ships (R1-B foundation slice) :
 *  - [Color] — constant-colour shader, equivalent to `paint.color = c`
 *    but expressed as an explicit [SkShader] so it composes with
 *    `makeWithLocalMatrix` / `makeWithColorFilter` and can be passed
 *    wherever a shader is expected (e.g. `SkShaders.Blend` once that
 *    factory ships in D2).
 *  - [CoordClamp] — wraps a child shader and clamps its evaluation to
 *    a sub-rectangle of local space; samples outside the rect return
 *    the colour at the nearest edge. Mirrors upstream `CoordClamp`,
 *    which Skia implements as a coordinate-clamp wrapper rather than
 *    a tile mode (so it composes orthogonally with the child's own
 *    tiling).
 *
 * Other upstream factories (`Empty`, `Blend(SkBlendMode, dst, src)`,
 * `Image`, `RawImage`) are deferred to later slices — they need
 * either D2 (`SkBlender`) or already exist as direct constructors
 * (`SkBitmapShader` for `Image`).
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
        // Fall back to the byte path then promote — the wrapper's
        // job is to clamp coords, not to preserve F16 precision; if a
        // GM needs F16-precise clamped shading we can add a
        // child.sampleAtLocalF16 hook.
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

    override fun sampleAtLocal(lx: Float, ly: Float): SkColor =
        child.sampleAtLocal(
            lx.coerceIn(rect.left, rect.right),
            ly.coerceIn(rect.top, rect.bottom),
        )
}
