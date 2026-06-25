package org.skia.foundation

import org.skia.core.SkColorSpaceXformSteps
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkPoint
import org.graphiks.math.SkScalar

/**
 * Radial gradient — colours interpolate along the radial distance from
 * [center] in shader-local space. `t = 0` at `center`, `t = 1` at radius
 * [radius], with [tileMode] deciding what happens for `t > 1` (and also
 * for `t < 0`, which can occur under non-rigid local matrices).
 *
 * Mirrors Skia's `SkGradientShader::MakeRadial(center, radius, colors, positions, tileMode)`.
 */
public class SkRadialGradient internal constructor(
    private val center: SkPoint,
    private val radius: SkScalar,
    private val srcColors: IntArray,
    private val positions: FloatArray,
    private val tileMode: SkTileMode,
    localMatrix: SkMatrix = SkMatrix.Identity,
) : SkShader(localMatrix) {

    override val shaderKind: ShaderKind
        get() = ShaderKind.Radial(
            center = center, radius = radius,
            colors = srcColors.copyOf(),
            positions = positions.copyOf(),
            tileMode = tileMode,
            localMatrix = localMatrix,
        )

    // ─── Public accessors (B2.4 — SVG projection) ────────────────────

    /** Centre of the radial gradient in shader-local space. */
    public fun getCenter(): SkPoint = center.copy()

    /** Radius from [getCenter] at which `t = 1`. */
    public fun getRadius(): SkScalar = radius

    /** Stop colours (defensive copy). */
    public fun getColors(): IntArray = srcColors.copyOf()

    /** Stop positions in `[0, 1]` (defensive copy). */
    public fun getPositions(): FloatArray = positions.copyOf()

    /** What happens for `t < 0` / `t > 1`. */
    public fun getTileMode(): SkTileMode = tileMode

    private val xformedColors: IntArray = IntArray(srcColors.size)

    /** Phase 6b — float-premul stops for the F16 raster path. */
    private val xformedColorsF16: FloatArray = FloatArray(srcColors.size * 4)

    /** Cached `1 / radius` to avoid the divide per pixel. */
    private var invRadius: Float = 0f

    override fun setupForDraw(canvasCtm: SkMatrix, xform: SkColorSpaceXformSteps) {
        super.setupForDraw(canvasCtm, xform)
        transformStopColors(srcColors, xformedColors, xform)
        transformStopColorsF16(srcColors, xformedColorsF16, xform)
        invRadius = if (radius <= 0f) 0f else 1f / radius
    }

    override fun shadeRow(devX: Int, devY: Int, count: Int, dst: IntArray) {
        val inv = deviceToLocal
        if (inv == null || invRadius == 0f) {
            val c = if (xformedColors.isNotEmpty()) xformedColors[0] else 0
            for (i in 0 until count) dst[i] = c
            return
        }

        // Sample at pixel centres and walk along the row.
        val x0 = devX + 0.5f
        val y0 = devY + 0.5f
        var lx = inv.sx * x0 + inv.kx * y0 + inv.tx
        var ly = inv.ky * x0 + inv.sy * y0 + inv.ty
        val stepX = inv.sx
        val stepY = inv.ky

        for (i in 0 until count) {
            val rx = lx - center.fX
            val ry = ly - center.fY
            val t = length(rx, ry) * invRadius
            dst[i] = lookupStop(t, positions, xformedColors, tileMode)
            lx += stepX
            ly += stepY
        }
    }

    override fun shadeRowF16(devX: Int, devY: Int, count: Int, dst: FloatArray) {
        require(dst.size >= count * 4) { "dst too small: ${dst.size} < ${count * 4}" }
        val inv = deviceToLocal
        if (inv == null || invRadius == 0f) {
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
            val rx = lx - center.fX
            val ry = ly - center.fY
            val t = length(rx, ry) * invRadius
            lookupStopF16(t, positions, xformedColorsF16, tileMode, dst, di)
            di += 4
            lx += stepX
            ly += stepY
        }
    }

    public companion object {
        /**
         * Mirrors Skia's `SkGradientShader::MakeRadial(center, radius,
         * colors, positions, tileMode)`. `positions` may be `null` to
         * distribute stops evenly across `[0, 1]`.
         */
        public fun Make(
            center: SkPoint,
            radius: SkScalar,
            colors: IntArray,
            positions: FloatArray?,
            tileMode: SkTileMode,
            localMatrix: SkMatrix = SkMatrix.Identity,
        ): SkRadialGradient {
            require(colors.isNotEmpty()) { "SkRadialGradient.Make requires at least one colour" }
            require(radius > 0f) { "SkRadialGradient radius must be positive (got $radius)" }
            val pos = positions ?: evenlySpacedPositions(colors.size)
            require(pos.size == colors.size) {
                "positions.size (${pos.size}) must match colors.size (${colors.size})"
            }
            return SkRadialGradient(center, radius, colors.copyOf(), pos.copyOf(), tileMode, localMatrix)
        }
    }
}
