package org.skia.foundation

import org.skia.core.SkColorSpaceXformSteps
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkPoint
import org.graphiks.math.SkScalar
import kotlin.math.PI
import kotlin.math.atan2

/**
 * Sweep gradient — colours interpolate around the angle swept from
 * [center] in shader-local space, between [startAngle] and [endAngle]
 * (degrees, measured clockwise from the +X axis in image-space Y-down
 * coordinates). The parametric `t` is the angle's position within
 * `[startAngle, endAngle]` mapped to `[0, 1]`; [tileMode] decides what
 * happens outside that range.
 *
 * Mirrors Skia's `SkShaders::SweepGradient(center, startAngle, endAngle,
 * grad, lm)` (see `src/shaders/gradients/SkSweepGradient.cpp`). The
 * upstream raster pipeline folds the center into a `Translate(-cx, -cy)`
 * baked into the gradient matrix and uses a `xy_to_unit_angle` op; we
 * keep the subtract per-pixel and call [atan2] directly because the
 * affine fast-path of [SkMatrix.invert] already collapses to a single
 * step in [shadeRow], so the difference is one `Float` add per pixel.
 *
 * Convention: `0°` at the +X axis. Image-space Y points down, so the
 * sweep goes **clockwise** as viewed on screen — matches CSS
 * `conic-gradient` and the `sweep_tiling` GM reference.
 */
public class SkSweepGradient internal constructor(
    private val center: SkPoint,
    private val startAngle: SkScalar,
    private val endAngle: SkScalar,
    private val srcColors: IntArray,
    private val positions: FloatArray,
    private val tileMode: SkTileMode,
    localMatrix: SkMatrix = SkMatrix.Identity,
) : SkShader(localMatrix) {

    override val shaderKind: ShaderKind
        get() = ShaderKind.Sweep(
            center = center,
            startAngle = startAngle, endAngle = endAngle,
            colors = srcColors.copyOf(),
            positions = positions.copyOf(),
            tileMode = tileMode,
            localMatrix = localMatrix,
        )

    /** Centre of the sweep in shader-local space. */
    public fun getCenter(): SkPoint = center.copy()

    /** Start angle in degrees ; `0` at the +X axis, increasing clockwise. */
    public fun getStartAngle(): SkScalar = startAngle

    /** End angle in degrees ; sweep spans `[startAngle, endAngle]`. */
    public fun getEndAngle(): SkScalar = endAngle

    /** Stop colours (defensive copy). */
    public fun getColors(): IntArray = srcColors.copyOf()

    /** Stop positions in `[0, 1]` (defensive copy). */
    public fun getPositions(): FloatArray = positions.copyOf()

    /** What happens for `t` outside `[0, 1]`. */
    public fun getTileMode(): SkTileMode = tileMode

    /** Pre-transformed stops in working colour space. Built per draw. */
    private val xformedColors: IntArray = IntArray(srcColors.size)

    /** F16 raster path stops (premul floats), built per draw. */
    private val xformedColorsF16: FloatArray = FloatArray(srcColors.size * 4)

    /**
     * `tBias = -startAngle / 360` and `tScale = 360 / (endAngle - startAngle)`,
     * pre-computed so per-pixel cost is two muls + an add. With the default
     * `(0, 360)` range these collapse to `tBias = 0`, `tScale = 1`.
     */
    private val tBias: Float = -startAngle / 360f
    private val tScale: Float = 360f / (endAngle - startAngle)

    override fun setupForDraw(canvasCtm: SkMatrix, xform: SkColorSpaceXformSteps) {
        super.setupForDraw(canvasCtm, xform)
        transformStopColors(srcColors, xformedColors, xform)
        transformStopColorsF16(srcColors, xformedColorsF16, xform)
    }

    override fun shadeRow(devX: Int, devY: Int, count: Int, dst: IntArray) {
        val inv = deviceToLocal
        if (inv == null) {
            val c = if (xformedColors.isNotEmpty()) xformedColors[0] else 0
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
            val t = unitAngle(lx - center.fX, ly - center.fY) + tBias
            dst[i] = lookupStop(t * tScale, positions, xformedColors, tileMode)
            lx += stepX
            ly += stepY
        }
    }

    override fun shadeRowF16(devX: Int, devY: Int, count: Int, dst: FloatArray) {
        require(dst.size >= count * 4) { "dst too small: ${dst.size} < ${count * 4}" }
        val inv = deviceToLocal
        if (inv == null) {
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
            val t = unitAngle(lx - center.fX, ly - center.fY) + tBias
            lookupStopF16(t * tScale, positions, xformedColorsF16, tileMode, dst, di)
            di += 4
            lx += stepX
            ly += stepY
        }
    }

    public companion object {
        /**
         * Mirrors Skia's `SkShaders::SweepGradient(center, startAngle,
         * endAngle, grad, localMatrix)` overload (the only one we expose).
         * `positions` may be `null` to evenly distribute stops across
         * `[0, 1]`. Requires `startAngle < endAngle`.
         */
        public fun Make(
            center: SkPoint,
            startAngle: SkScalar,
            endAngle: SkScalar,
            colors: IntArray,
            positions: FloatArray?,
            tileMode: SkTileMode,
            localMatrix: SkMatrix = SkMatrix.Identity,
        ): SkSweepGradient {
            require(colors.isNotEmpty()) { "SkSweepGradient.Make requires at least one colour" }
            require(startAngle.isFinite() && endAngle.isFinite()) { "angles must be finite" }
            require(startAngle < endAngle) {
                "startAngle ($startAngle) must be < endAngle ($endAngle)"
            }
            val pos = positions ?: evenlySpacedPositions(colors.size)
            require(pos.size == colors.size) {
                "positions.size (${pos.size}) must match colors.size (${colors.size})"
            }
            return SkSweepGradient(
                center, startAngle, endAngle,
                colors.copyOf(), pos.copyOf(), tileMode, localMatrix,
            )
        }

        /**
         * Convenience overload mirroring Skia's no-angle variant
         * (`SkShaders::SweepGradient(center, grad, lm)` — implicit `[0°,
         * 360°)` range).
         */
        public fun Make(
            center: SkPoint,
            colors: IntArray,
            positions: FloatArray?,
            tileMode: SkTileMode,
            localMatrix: SkMatrix = SkMatrix.Identity,
        ): SkSweepGradient = Make(center, 0f, 360f, colors, positions, tileMode, localMatrix)
    }
}

/**
 * Map `(x, y)` to its unit-angle in `[0, 1)` — `0` at the +X axis,
 * increasing **clockwise** (matches image-space Y-down sweep direction
 * used by Skia's `xy_to_unit_angle` raster pipeline op).
 *
 * Special case `(0, 0)` → `0` (matches upstream which deterministically
 * collapses the singularity to the start angle).
 */
private fun unitAngle(x: Float, y: Float): Float {
    if (x == 0f && y == 0f) return 0f
    val a = atan2(y, x)  // [-π, π], image-space Y-down → CW
    val u = a / (2f * PI.toFloat())
    return if (u < 0f) u + 1f else u
}
