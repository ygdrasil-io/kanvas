package org.skia.foundation

import org.skia.core.SkColorSpaceXformSteps
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkPoint
import org.graphiks.math.SkScalar
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Two-point conical gradient — colours interpolate radially between
 * two circles `{c0, r0}` and `{c1, r1}`. `t = 0` at the start circle,
 * `t = 1` at the end circle. Outside the cone of revolution, [tileMode]
 * decides what happens; pixels outside the cone are masked to
 * transparent black under all tile modes (matches upstream
 * `mask_2pt_conical_*`).
 *
 * Mirrors Skia's `SkShaders::TwoPointConicalGradient(c0, r0, c1, r1,
 * grad, lm)` — see `src/shaders/gradients/SkConicalGradient.cpp` and
 * the design doc at `https://skia.org/dev/design/conical`.
 *
 * Three sub-types based on the geometry:
 *  - **kRadial** when `c0 ≈ c1` (concentric): `t` goes from `r0` to `r1`
 *    along the radial distance from `c1`. Falls back to a
 *    [SkRadialGradient]-like shadeRow.
 *  - **kStrip** when `c0 ≠ c1` and `r0 ≈ r1`: equal-radius "tube"; `t`
 *    is the projected distance along the line `c0→c1`.
 *  - **kFocal** when `c0 ≠ c1` and `r0 ≠ r1`: general case; the focal
 *    point is computed via `f = r0 / (r0 - r1)` and the shader maps
 *    pixels into a normalized "focal" frame where the focal point is at
 *    the origin and the end circle has radius `R = r1 / |1 - f|`.
 *
 * **Implementation note**: this is a single-class port of the upstream
 * raster-pipeline approach: the per-sub-case `xy_to_2pt_conical_*` ops
 * are inlined as Kotlin `if`/`when` branches inside [computeT]. The
 * `mask_2pt_conical_*` masking and the `alter_*` post-passes are
 * folded into the same loop. This avoids a stage-machine indirection
 * but keeps the math byte-for-byte aligned with the upstream stages.
 */
public class SkConicalGradient internal constructor(
    private val c0: SkPoint,
    private val r0: SkScalar,
    private val c1: SkPoint,
    private val r1: SkScalar,
    private val srcColors: IntArray,
    private val positions: FloatArray,
    private val tileMode: SkTileMode,
    private val type: Type,
    private val gradientMatrix: SkMatrix,
    private val focalData: FocalData?,
    localMatrix: SkMatrix = SkMatrix.Identity,
) : SkShader(localMatrix) {

    /** Sub-type tag; mirrors upstream's `SkConicalGradient::Type`. */
    public enum class Type { kRadial, kStrip, kFocal }

    // ─── Public accessors (G4.4 — GPU dispatch) ────────────────────────

    /** Start circle centre in shader-local space. */
    public fun getStart(): SkPoint = c0.copy()

    /** Start circle radius. */
    public fun getStartRadius(): SkScalar = r0

    /** End circle centre in shader-local space. */
    public fun getEnd(): SkPoint = c1.copy()

    /** End circle radius. */
    public fun getEndRadius(): SkScalar = r1

    /** Stop colours (defensive copy). */
    public fun getColors(): IntArray = srcColors.copyOf()

    /** Stop positions in `[0, 1]` (defensive copy). */
    public fun getPositions(): FloatArray = positions.copyOf()

    /** What happens for `t < 0` / `t > 1`. */
    public fun getTileMode(): SkTileMode = tileMode

    /** Geometric sub-type ; the GPU pipeline switches on this. */
    public fun getType(): Type = type

    /** Pre-transformed stop colours in working space (8-bit path). */
    private val xformedColors: IntArray = IntArray(srcColors.size)

    /** F16 raster path stops (premul floats), built per draw. */
    private val xformedColorsF16: FloatArray = FloatArray(srcColors.size * 4)

    // ─── kRadial pre-computes ──────────────────────────────────────────
    /** `(maxRadius / dRadius, -r0 / dRadius)` for the kRadial path:
     *  `t = maxRadius/dRadius · |p - c1|/maxRadius - r0/dRadius`. Folded
     *  into the [gradientMatrix] post-scale already does the
     *  `1/maxRadius` part; here we just need the `[scale, bias]` shift
     *  on `t`. */
    private val radialScale: Float
    private val radialBias: Float

    // ─── kStrip pre-computes ──────────────────────────────────────────
    /** `(r0 / |c1.x|)²` — the `fP0` of upstream's strip context. */
    private val stripP0: Float

    init {
        if (type == Type.kRadial) {
            val dRadius = r1 - r0
            val maxR = max(r0, r1)
            radialScale = if (dRadius != 0f) maxR / dRadius else 0f
            radialBias = if (dRadius != 0f) -r0 / dRadius else 0f
        } else {
            radialScale = 0f
            radialBias = 0f
        }

        if (type == Type.kStrip) {
            // Upstream's `getCenterX1()` returns the x-coordinate of the second
            // center *after* `MapToUnitX` mapping (which puts c0 at origin and
            // c1 at (1, 0)). So the divisor is always 1 in the mapped frame.
            // Refer to `SkConicalGradient::getCenterX1()`.
            val scaledR0 = r0  // since centerX1 = 1 in mapped frame
            stripP0 = scaledR0 * scaledR0
        } else {
            stripP0 = 0f
        }
    }

    /**
     * Cached forward `device → conical-frame` matrix. Composed once per
     * draw as `gradientMatrix · (canvasCtm · localMatrix)⁻¹`.
     *
     * Derivation: the conical algorithm wants `(x, y)` in the
     * conical-frame (focal-mapped for kFocal, unit-circle for kRadial,
     * etc.). The chain is
     *   `device → (CTM⁻¹) → canvas-source → (userLocal⁻¹) → user-shader →
     *   (gradientMatrix) → conical-frame`,
     * so the single forward matrix is
     *   `gradientMatrix · userLocal⁻¹ · CTM⁻¹ = gradientMatrix · (CTM·userLocal)⁻¹`.
     * Stored as a single matrix so the per-pixel cost is the same as a
     * radial / linear gradient.
     */
    private var deviceToConical: SkMatrix? = null

    override fun setupForDraw(canvasCtm: SkMatrix, xform: SkColorSpaceXformSteps) {
        super.setupForDraw(canvasCtm, xform)
        transformStopColors(srcColors, xformedColors, xform)
        transformStopColorsF16(srcColors, xformedColorsF16, xform)

        val ctmLocal = canvasCtm.preConcat(localMatrix)         // CTM · userLocal
        val invCtmLocal = ctmLocal.invert() ?: return
        deviceToConical = gradientMatrix.preConcat(invCtmLocal) // gradientMatrix · (CTM·userLocal)⁻¹
    }

    override fun shadeRow(devX: Int, devY: Int, count: Int, dst: IntArray) {
        val inv = deviceToConical
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
            val t = computeT(lx, ly)
            dst[i] = if (t.isNaN()) 0 else lookupStop(t, positions, xformedColors, tileMode)
            lx += stepX
            ly += stepY
        }
    }

    override fun shadeRowF16(devX: Int, devY: Int, count: Int, dst: FloatArray) {
        require(dst.size >= count * 4) { "dst too small: ${dst.size} < ${count * 4}" }
        val inv = deviceToConical
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
            val t = computeT(lx, ly)
            if (t.isNaN()) {
                dst[di] = 0f; dst[di + 1] = 0f; dst[di + 2] = 0f; dst[di + 3] = 0f
            } else {
                lookupStopF16(t, positions, xformedColorsF16, tileMode, dst, di)
            }
            di += 4
            lx += stepX
            ly += stepY
        }
    }

    /**
     * Compute the gradient-parametric `t` at mapped point `(x, y)`. The
     * input is already in the canonical conical frame (via
     * [gradientMatrix]); this dispatches to the per-sub-type formula.
     * Returns `Float.NaN` for pixels outside the cone — caller maps that
     * to transparent black.
     *
     * Mirror of upstream's `xy_to_2pt_conical_*` raster-pipeline ops +
     * the `mask_2pt_conical_*` post-pass + the `alter_2pt_*`
     * compensations. Ordering inside the kFocal branch mirrors
     * `SkConicalGradient::appendGradientStages`.
     */
    private fun computeT(x: Float, y: Float): Float {
        return when (type) {
            Type.kRadial -> {
                // `xy_to_radius` then `Translate(bias, 0) · Scale(scale, 1)`.
                val tRaw = sqrt(x * x + y * y)
                tRaw * radialScale + radialBias
            }
            Type.kStrip -> {
                // `xy_to_2pt_conical_strip` then `mask_2pt_conical_nan`.
                val disc = stripP0 - y * y
                if (disc < 0f) Float.NaN else x + sqrt(disc)
            }
            Type.kFocal -> computeTFocal(x, y)
        }
    }

    /**
     * kFocal sub-case dispatcher. Picks one of `well_behaved`, `greater`,
     * `smaller`, `focal_on_circle` based on [FocalData] flags, then
     * applies `negate_x`, `compensate_focal`, `unswap` post-passes as
     * needed.
     */
    private fun computeTFocal(xIn: Float, yIn: Float): Float {
        val fd = focalData!!
        val fP0 = 1f / fd.fR1   // 1/R
        val fP1 = fd.fFocalX    // f

        val xx = xIn
        val yy = yIn
        var t: Float

        if (fd.isFocalOnCircle()) {
            // (x² + y²) / x — singularity at x = 0.
            t = if (xx == 0f) Float.NaN else xx + yy * yy / xx
        } else if (fd.isWellBehaved()) {
            t = sqrt(xx * xx + yy * yy) - xx * fP0
        } else if (fd.isSwapped() || (1f - fP1) < 0f) {
            val disc = xx * xx - yy * yy
            t = if (disc < 0f) Float.NaN else -sqrt(disc) - xx * fP0
        } else {
            val disc = xx * xx - yy * yy
            t = if (disc < 0f) Float.NaN else sqrt(disc) - xx * fP0
        }

        // mask_2pt_conical_degenerates: t ≤ 0 OR NaN → degenerate
        // (only applied for non-well-behaved sub-cases).
        if (!fd.isWellBehaved()) {
            if (t.isNaN() || t <= 0f) return Float.NaN
        }

        // negate_x post-pass (only when 1 - f < 0).
        // Upstream applies this *after* the t computation but before
        // compensate_focal, working on the input x. Effectively flips
        // the sign of x going into compensate_focal — but compensate_focal
        // only adds fP1 to t, so negate_x has no effect on t directly.
        // (See raster pipeline: negate_x acts on `r` which is `x`, not `t`.
        // Since `t` aliases `r` after the conical op, the negation IS on `t`.)
        if ((1f - fP1) < 0f) {
            t = -t
        }

        // alter_2pt_conical_compensate_focal: t += f.
        if (!fd.isNativelyFocal()) {
            t += fP1
        }

        // alter_2pt_conical_unswap: t = 1 - t.
        if (fd.isSwapped()) {
            t = 1f - t
        }

        return t
    }

    /**
     * Focal-point-frame parameters mirroring upstream's
     * `SkConicalGradient::FocalData`. Captured at construction time;
     * re-used by [computeTFocal] every pixel.
     */
    public class FocalData internal constructor(
        public val fFocalX: Float,
        public val fR1: Float,
        public val fIsSwapped: Boolean,
    ) {
        public fun isFocalOnCircle(): Boolean = nearlyZero(1f - fR1)
        public fun isSwapped(): Boolean = fIsSwapped
        public fun isWellBehaved(): Boolean = !isFocalOnCircle() && fR1 > 1f
        public fun isNativelyFocal(): Boolean = nearlyZero(fFocalX)
    }

    public companion object {
        private const val kDegenerateThreshold: Float = 1f / (1 shl 12).toFloat()

        /**
         * Mirrors Skia's `SkShaders::TwoPointConicalGradient(start,
         * startRadius, end, endRadius, grad, lm)` — picks the right
         * sub-type, builds the `gradientMatrix` (and `focalData` for
         * kFocal), and constructs the shader.
         *
         * Returns `null` for fully degenerate inputs (matching upstream's
         * "skip the draw" behaviour). `positions` may be `null` to evenly
         * distribute stops across `[0, 1]`.
         */
        public fun Make(
            start: SkPoint,
            startRadius: SkScalar,
            end: SkPoint,
            endRadius: SkScalar,
            colors: IntArray,
            positions: FloatArray?,
            tileMode: SkTileMode,
            localMatrix: SkMatrix = SkMatrix.Identity,
        ): SkConicalGradient? {
            require(colors.isNotEmpty()) { "SkConicalGradient.Make requires at least one colour" }
            require(startRadius >= 0f && endRadius >= 0f) { "radii must be ≥ 0" }
            val pos = positions ?: evenlySpacedPositions(colors.size)
            require(pos.size == colors.size)

            val dx = end.fX - start.fX
            val dy = end.fY - start.fY
            val dCenter = sqrt(dx * dx + dy * dy)

            var gradientMatrix: SkMatrix
            val type: Type
            var focalData: FocalData? = null

            if (nearlyZero(dCenter)) {
                if (nearlyZero(max(startRadius, endRadius)) ||
                    nearlyEqual(startRadius, endRadius)) return null
                if (nearlyZero(startRadius)) {
                    // Degenerates to a plain radial — still expose as kRadial
                    // for API parity, but the math collapses identically.
                }
                val scale = 1f / max(startRadius, endRadius)
                gradientMatrix = SkMatrix.MakeTrans(-end.fX, -end.fY)
                gradientMatrix = gradientMatrix.postScale(scale, scale)
                type = Type.kRadial
            } else {
                val mx = SkMatrix.MakePolyToPoly(
                    arrayOf(start, end),
                    arrayOf(SkPoint(0f, 0f), SkPoint(1f, 0f)),
                ) ?: return null
                gradientMatrix = mx
                type = if (nearlyEqual(startRadius, endRadius)) Type.kStrip else Type.kFocal
            }

            if (type == Type.kFocal) {
                val r0n = startRadius / dCenter
                val r1n = endRadius / dCenter
                val (built, matrixOut) = buildFocalData(r0n, r1n, gradientMatrix) ?: return null
                focalData = built
                gradientMatrix = matrixOut
            }

            return SkConicalGradient(
                start, startRadius, end, endRadius,
                colors.copyOf(), pos.copyOf(), tileMode, type,
                gradientMatrix, focalData, localMatrix,
            )
        }

        /**
         * Build the [FocalData] and return the post-concat'd
         * `gradientMatrix`. Mirrors `FocalData::set` byte-for-byte.
         */
        private fun buildFocalData(
            r0In: Float, r1In: Float, mxIn: SkMatrix,
        ): Pair<FocalData, SkMatrix>? {
            var r0 = r0In
            var r1 = r1In
            var matrix = mxIn
            var fIsSwapped = false
            var fFocalX = if (r0 == r1) 0f else r0 / (r0 - r1)
            if (nearlyZero(fFocalX - 1f)) {
                // Swap r0, r1 + apply post-translate(-1, 0) · post-scale(-1, 1).
                matrix = matrix.postTranslate(-1f, 0f).postScale(-1f, 1f)
                val tmp = r0; r0 = r1; r1 = tmp
                fFocalX = 0f
                fIsSwapped = true
            }

            // Map {focal point, (1, 0)} to {(0, 0), (1, 0)}.
            val from = arrayOf(SkPoint(fFocalX, 0f), SkPoint(1f, 0f))
            val to = arrayOf(SkPoint(0f, 0f), SkPoint(1f, 0f))
            val focalMatrix = SkMatrix.MakePolyToPoly(from, to) ?: return null
            matrix = matrix.postConcat(focalMatrix)

            val fR1 = r1 / abs(1f - fFocalX)

            // Acceleration scales (see upstream comments).
            matrix = if (nearlyZero(1f - fR1)) {
                matrix.postScale(0.5f, 0.5f)
            } else {
                val s2 = fR1 * fR1 - 1f
                matrix.postScale(fR1 / s2, 1f / sqrt(abs(s2)))
            }
            matrix = matrix.postScale(abs(1f - fFocalX), abs(1f - fFocalX))

            return FocalData(fFocalX, fR1, fIsSwapped) to matrix
        }

        private fun nearlyZero(v: Float, tol: Float = kDegenerateThreshold): Boolean = abs(v) < tol
        private fun nearlyEqual(a: Float, b: Float, tol: Float = kDegenerateThreshold): Boolean =
            abs(a - b) < tol
    }
}
