package org.skia.foundation

import org.skia.core.SkColorSpaceXformSteps
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkPoint
import org.graphiks.math.SkScalar

/**
 * Linear gradient — colours interpolate along the line segment from
 * [p0] to [p1] in shader-local space. `t = 0` at `p0`, `t = 1` at `p1`,
 * with [tileMode] deciding what happens for `t < 0` or `t > 1`.
 *
 * Mirrors Skia's `SkGradientShader::MakeLinear(p0, p1, colors, positions, tileMode)`.
 */
public class SkLinearGradient internal constructor(
    private val p0: SkPoint,
    private val p1: SkPoint,
    private val srcColors: IntArray,
    private val positions: FloatArray,
    private val tileMode: SkTileMode,
    localMatrix: SkMatrix = SkMatrix.Identity,
) : SkShader(localMatrix) {

    override val shaderKind: ShaderKind
        get() = ShaderKind.Linear(
            p0 = p0, p1 = p1,
            colors = srcColors.copyOf(),
            positions = positions.copyOf(),
            tileMode = tileMode,
            localMatrix = localMatrix,
        )

    // ─── Public accessors (mirror Skia's `SkShader::asAGradient(GradientInfo*)`) ─
    //
    // Used by [org.skia.svg.SkSVGCanvas] (B2.4) to project a linear
    // gradient into a `<linearGradient>` SVG def. Defensive copies are
    // returned so external mutation can't corrupt the shader's
    // pre-computed colour table.

    /** Start point of the gradient line, in shader-local space. */
    public fun getStartPoint(): SkPoint = p0.copy()

    /** End point of the gradient line, in shader-local space. */
    public fun getEndPoint(): SkPoint = p1.copy()

    /** Stop colours (defensive copy) — same order / count as [getPositions]. */
    public fun getColors(): IntArray = srcColors.copyOf()

    /** Stop positions in `[0, 1]` (defensive copy). */
    public fun getPositions(): FloatArray = positions.copyOf()

    /** What happens for `t < 0` / `t > 1`. */
    public fun getTileMode(): SkTileMode = tileMode

    /**
     * Pre-transformed `colors` (working colour space). Built once per draw
     * in [setupForDraw]. Same length as [srcColors].
     */
    private val xformedColors: IntArray = IntArray(srcColors.size)

    /**
     * Phase 6b — float-premul stops in the bitmap's working colour space.
     * 4 floats per source stop, built once per draw in [setupForDraw]. Used
     * by [shadeRowF16] for the F16 raster path; bypasses the 8-bit byte
     * quantization that [xformedColors] necessarily incurs.
     */
    private val xformedColorsF16: FloatArray = FloatArray(srcColors.size * 4)

    /**
     * Inverse-length-squared direction vector. For a line from `p0` to
     * `p1`, the parametric `t` at point `p` is `((p − p0) · (p1 − p0)) /
     * |p1 − p0|²`. We pre-compute `(p1 − p0) / |p1 − p0|²` so each pixel
     * needs just `t = (px − p0x)·dx + (py − p0y)·dy`.
     *
     * Recomputed each [setupForDraw] (constant — depends only on `p0`/`p1`,
     * but we recompute defensively in case future subclasses mutate).
     */
    private var invLenSqDirX: Float = 0f
    private var invLenSqDirY: Float = 0f

    override fun setupForDraw(canvasCtm: SkMatrix, xform: SkColorSpaceXformSteps) {
        super.setupForDraw(canvasCtm, xform)
        transformStopColors(srcColors, xformedColors, xform)
        transformStopColorsF16(srcColors, xformedColorsF16, xform)
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
            // Singular total matrix — degenerate to first stop.
            val c = if (xformedColors.isNotEmpty()) xformedColors[0] else 0
            for (i in 0 until count) dst[i] = c
            return
        }

        // Sample at pixel centres `(devX + 0.5, devY + 0.5)` and walk
        // along the row by `(inv.sx, inv.ky)` per device-x step.
        val x0 = devX + 0.5f
        val y0 = devY + 0.5f
        var lx = inv.sx * x0 + inv.kx * y0 + inv.tx
        var ly = inv.ky * x0 + inv.sy * y0 + inv.ty
        val stepX = inv.sx
        val stepY = inv.ky

        for (i in 0 until count) {
            val t = (lx - p0.fX) * invLenSqDirX + (ly - p0.fY) * invLenSqDirY
            dst[i] = lookupStop(t, positions, xformedColors, tileMode)
            lx += stepX
            ly += stepY
        }
    }

    override fun shadeRowF16(devX: Int, devY: Int, count: Int, dst: FloatArray) {
        require(dst.size >= count * 4) { "dst too small: ${dst.size} < ${count * 4}" }
        val inv = deviceToLocal
        if (inv == null) {
            // Singular total matrix — degenerate to first stop.
            var di = 0
            for (i in 0 until count) {
                dst[di]     = xformedColorsF16[0]
                dst[di + 1] = xformedColorsF16[1]
                dst[di + 2] = xformedColorsF16[2]
                dst[di + 3] = xformedColorsF16[3]
                di += 4
            }
            return
        }

        val x0 = devX + 0.5f
        val y0 = devY + 0.5f
        var lx = inv.sx * x0 + inv.kx * y0 + inv.tx
        var ly = inv.ky * x0 + inv.sy * y0 + inv.ty
        val stepX = inv.sx
        val stepY = inv.ky

        var di = 0
        for (i in 0 until count) {
            val t = (lx - p0.fX) * invLenSqDirX + (ly - p0.fY) * invLenSqDirY
            lookupStopF16(t, positions, xformedColorsF16, tileMode, dst, di)
            di += 4
            lx += stepX
            ly += stepY
        }
    }

    override fun sampleAtLocal(lx: Float, ly: Float): Int {
        if (xformedColors.isEmpty()) return 0
        val t = (lx - p0.fX) * invLenSqDirX + (ly - p0.fY) * invLenSqDirY
        return lookupStop(t, positions, xformedColors, tileMode)
    }

    override fun sampleAtLocalF16(lx: Float, ly: Float, dst: FloatArray, dstOffset: Int) {
        require(dst.size >= dstOffset + 4) { "dst too small at offset $dstOffset" }
        if (xformedColorsF16.isEmpty()) {
            dst[dstOffset] = 0f
            dst[dstOffset + 1] = 0f
            dst[dstOffset + 2] = 0f
            dst[dstOffset + 3] = 0f
            return
        }
        val t = (lx - p0.fX) * invLenSqDirX + (ly - p0.fY) * invLenSqDirY
        lookupStopF16(t, positions, xformedColorsF16, tileMode, dst, dstOffset)
    }

    public companion object {
        /**
         * Mirrors Skia's `SkGradientShader::MakeLinear(p0, p1, colors,
         * positions, tileMode)` (the simplest overload — no localMatrix,
         * no SkColor4f, no flags).
         *
         * `positions` may be `null` to evenly distribute stops across
         * `[0, 1]`, matching Skia.
         */
        public fun Make(
            p0: SkPoint,
            p1: SkPoint,
            colors: IntArray,
            positions: FloatArray?,
            tileMode: SkTileMode,
            localMatrix: SkMatrix = SkMatrix.Identity,
        ): SkLinearGradient {
            require(colors.isNotEmpty()) { "SkLinearGradient.Make requires at least one colour" }
            val pos = positions ?: evenlySpacedPositions(colors.size)
            require(pos.size == colors.size) {
                "positions.size (${pos.size}) must match colors.size (${colors.size})"
            }
            return SkLinearGradient(p0, p1, colors.copyOf(), pos.copyOf(), tileMode, localMatrix)
        }
    }
}

/** Returns `[0/0, 1/(n-1), 2/(n-1), …, 1]` for `n ≥ 2`, or `[0]` for `n == 1`. */
internal fun evenlySpacedPositions(n: Int): FloatArray {
    require(n >= 1)
    if (n == 1) return floatArrayOf(0f)
    val out = FloatArray(n)
    val step = 1f / (n - 1).toFloat()
    for (i in 0 until n) out[i] = i * step
    out[n - 1] = 1f       // pin the final entry to defeat float drift
    return out
}
