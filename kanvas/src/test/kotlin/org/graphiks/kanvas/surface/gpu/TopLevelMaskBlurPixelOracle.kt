package org.graphiks.kanvas.surface.gpu

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import org.graphiks.kanvas.gpu.renderer.clips.GPUBounds
import org.graphiks.kanvas.gpu.renderer.filters.BlurKernelUniform
import org.graphiks.kanvas.gpu.renderer.filters.MAX_MASK_BLUR_TAPS
import org.graphiks.kanvas.gpu.renderer.filters.MaskBlurPlan
import org.graphiks.kanvas.gpu.renderer.filters.MaskBlurPlanner
import org.graphiks.kanvas.gpu.renderer.filters.MaskBlurRequest
import org.graphiks.kanvas.gpu.renderer.filters.NormalizedBlurStyle
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.a
import org.graphiks.kanvas.types.b
import org.graphiks.kanvas.types.g
import org.graphiks.kanvas.types.alphaByte
import org.graphiks.kanvas.types.r
import org.graphiks.kanvas.types.redByte

/**
 * CPU pixel oracle for top-level mask blur, faithful to the legacy
 * dispatcher semantics (GPUMaskBlurDispatch.kt + MASK_BLUR_* WGSL):
 *
 *  1. plan via [MaskBlurPlanner] (same production math: halo 3σ, scale
 *     min(1, 12/σ), budget gate) with the legacy clip-bounds convention
 *  2. local shape coverage at pixel centers (non-AA → binary inside tests;
 *     rect/rrect analytic, path via winding on the localized vertices)
 *  3. horizontal then vertical Gaussian blur: kernel per [BlurKernelUniform]
 *     (taps = ceil(max(0.5, σ))·2+1 coerced to 3..25, exp(-x²/2σ²) normalized),
 *     decal sampling (out-of-bounds taps contribute nothing)
 *  4. style pass: NORMAL=blurred, SOLID=max(original,blurred),
 *     OUTER=blurred·(1-original), INNER=blurred·original (alpha only)
 *  5. composite: nearest-texel sample of the styled coverage at device pixel
 *     centers (clamp-to-edge for AA-clip lanes, mirroring the composite shader),
 *     source = linear premul color × coverage, blended into the
 *     destination per the paint blend mode (Porter-Duff for SRC_OVER/SRC,
 *     W3C formula for DARKEN — the composite route's blend oracle math),
 *     encoded linear→sRGB (the surface target is RGBA8_UNORM_SRGB).
 *
 * All pixel coordinates follow the WGSL convention: @builtin(position) is the
 * pixel center, uv = coord.xy / size, and the mask is sampled with decal.
 */
object TopLevelMaskBlurPixelOracle {

    const val MAX_TEXTURE_DIMENSION_2D = 4096

    /** Full 32x32 target bounds (the standard surface in the flip-set fixtures). */
    fun fullTargetBounds(): GPUBounds = GPUBounds(0f, 0f, 32f, 32f)

    /** Shape descriptor accepted by the oracle (all draws are non-AA in the flip set). */
    sealed interface Shape {
        data class Rect(val left: Float, val top: Float, val right: Float, val bottom: Float) : Shape
        data class RRectShape(val rect: RRect) : Shape
        data class Path(
            val vertices: List<Float>,
            val contourStarts: List<Int>,
            val inverseFill: Boolean,
        ) : Shape
    }

    /** One planned blur pass bundle, mirroring the GPU lane's plan. */
    data class Plan(
        val ready: MaskBlurPlan.Ready,
        val kernel: BlurKernelUniform,
        val localMask: FloatArray,
        val blurred: FloatArray,
        val styled: FloatArray,
    )

    fun planShape(
        targetWidth: Int,
        targetHeight: Int,
        shape: Shape,
        clipBounds: GPUBounds,
        style: BlurStyle,
        sigma: Float,
        maxIntermediateBytes: Long = Long.MAX_VALUE,
    ): MaskBlurPlan {
        val shapeBounds = shapeBounds(shape)
        val normalizedStyle = when (style) {
            BlurStyle.NORMAL -> NormalizedBlurStyle.NORMAL
            BlurStyle.SOLID -> NormalizedBlurStyle.SOLID
            BlurStyle.OUTER -> NormalizedBlurStyle.OUTER
            BlurStyle.INNER -> NormalizedBlurStyle.INNER
        }
        return MaskBlurPlanner.plan(
            MaskBlurRequest(
                bounds = shapeBounds,
                clipBounds = clipBounds,
                targetWidth = targetWidth,
                targetHeight = targetHeight,
                style = normalizedStyle,
                sigma = sigma,
                maxTextureDimension2D = MAX_TEXTURE_DIMENSION_2D,
                maxIntermediateBytes = maxIntermediateBytes,
            ),
        )
    }

    fun shapeBounds(shape: Shape): GPUBounds = when (shape) {
        is Shape.Rect -> GPUBounds(shape.left, shape.top, shape.right, shape.bottom)
        is Shape.RRectShape -> {
            val r = shape.rect.rect
            GPUBounds(r.left, r.top, r.right, r.bottom)
        }
        is Shape.Path -> {
            var left = Float.MAX_VALUE
            var top = Float.MAX_VALUE
            var right = -Float.MAX_VALUE
            var bottom = -Float.MAX_VALUE
            shape.vertices.chunked(2).forEach { (x, y) ->
                left = min(left, x); top = min(top, y)
                right = max(right, x); bottom = max(bottom, y)
            }
            GPUBounds(left, top, right, bottom)
        }
    }

    /** Clip applied to the blur composite (device-rect scissor semantics). */
    sealed interface Clip

    /** One analytic device-rect clip. */
    data class RectClip(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val antiAlias: Boolean,
    ) : Clip

    /**
     * One ordered multi-rect analytic clip: an INTERSECT rect folds
     * the per-rect coverage in, a DIFFERENCE rect folds one-minus-coverage in, both
     * evaluated with the same two-sided SDF ramp as [RectClip] at pixel centers.
     */
    data class ComplexClip(val elements: List<ComplexClipElement>) : Clip {
        init {
            require(elements.isNotEmpty()) { "ComplexClip requires at least one element" }
        }
    }

    /** One ordered element of a [ComplexClip]. */
    data class ComplexClipElement(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val operation: ComplexClipOperation,
        val antiAlias: Boolean,
    )

    enum class ComplexClipOperation { Intersect, Difference }

    /** Renders the full blur chain and returns the expected encoded RGBA target pixels. */
    fun render(
        targetWidth: Int,
        targetHeight: Int,
        shape: Shape,
        clipBounds: GPUBounds,
        style: BlurStyle,
        sigma: Float,
        source: Color,
        blendMode: BlendMode,
        destinationEncoded: UByteArray,
        maxIntermediateBytes: Long = Long.MAX_VALUE,
        clip: Clip? = null,
    ): UByteArray {
        val planClipBounds = (clip as? RectClip)?.let { rect ->
            GPUBounds(rect.left, rect.top, rect.right, rect.bottom)
        } ?: clipBounds
        val plan = planShape(
            targetWidth, targetHeight, shape, planClipBounds, style, sigma, maxIntermediateBytes,
        ) as MaskBlurPlan.Ready
        val kernel = kernelFor(plan)
        val localMask = localCoverage(plan, shape)
        val blurredH = blurPass(localMask, plan.localWidth, plan.localHeight, kernel, horizontal = true)
        val blurred = blurPass(blurredH, plan.localWidth, plan.localHeight, kernel, horizontal = false)
        val styled = stylePass(plan, localMask, blurred)

        val composite = compositePass(
            plan, styled, source, blendMode, destinationEncoded, targetWidth, targetHeight, clip,
        )

        return composite
    }

    // --- stages ---

    private fun localCoverage(plan: MaskBlurPlan.Ready, shape: Shape): FloatArray {
        val coverage = FloatArray(plan.localWidth * plan.localHeight)
        for (y in 0 until plan.localHeight) {
            for (x in 0 until plan.localWidth) {
                val px = x + 0.5f
                val py = y + 0.5f
                coverage[y * plan.localWidth + x] = when (shape) {
                    is Shape.Rect -> {
                        val localLeft = (shape.left - plan.deviceBounds.left) * plan.scale
                        val localTop = (shape.top - plan.deviceBounds.top) * plan.scale
                        val localRight = (shape.right - plan.deviceBounds.left) * plan.scale
                        val localBottom = (shape.bottom - plan.deviceBounds.top) * plan.scale
                        if (px >= localLeft && px < localRight && py >= localTop && py < localBottom) 1f else 0f
                    }
                    is Shape.RRectShape -> rrectInside(plan, shape.rect, px, py)
                    is Shape.Path -> pathInside(plan, shape, px, py)
                }
            }
        }
        return coverage
    }

    private fun rrectInside(plan: MaskBlurPlan.Ready, rrect: RRect, px: Float, py: Float): Float {
        val rect = rrect.rect
        val left = (rect.left - plan.deviceBounds.left) * plan.scale
        val top = (rect.top - plan.deviceBounds.top) * plan.scale
        val right = (rect.right - plan.deviceBounds.left) * plan.scale
        val bottom = (rect.bottom - plan.deviceBounds.top) * plan.scale
        if (px < left || px >= right || py < top || py >= bottom) return 0f
        val radii = listOf(
            rrect.topLeft, rrect.topRight, rrect.bottomRight, rrect.bottomLeft,
        )
        val (cornerX, cornerY) = when {
            px < left + radii[0].x && py < top + radii[0].y -> radii[0].x * plan.scale to radii[0].y * plan.scale
            px >= right - radii[1].x && py < top + radii[1].y -> radii[1].x * plan.scale to radii[1].y * plan.scale
            px >= right - radii[2].x && py >= bottom - radii[2].y -> radii[2].x * plan.scale to radii[2].y * plan.scale
            px < left + radii[3].x && py >= bottom - radii[3].y -> radii[3].x * plan.scale to radii[3].y * plan.scale
            else -> return 1f
        }
        // Ellipse test against the corner center in the local pixel frame.
        val ex = if (px < left + cornerX) left + cornerX else right - cornerX
        val ey = if (py < top + cornerY) top + cornerY else bottom - cornerY
        val dx = (px - ex) / cornerX
        val dy = (py - ey) / cornerY
        return if (dx * dx + dy * dy <= 1f) 1f else 0f
    }

    private fun pathInside(plan: MaskBlurPlan.Ready, shape: Shape.Path, px: Float, py: Float): Float {
        val local = shape.vertices.chunked(2).map { (x, y) ->
            ((x - plan.deviceBounds.left) * plan.scale) to ((y - plan.deviceBounds.top) * plan.scale)
        }
        var inside = false
        var j = local.size - 1
        for (i in local.indices) {
            val (xi, yi) = local[i]
            val (xj, yj) = local[j]
            if ((yi > py) != (yj > py) &&
                px < (xj - xi) * (py - yi) / (yj - yi) + xi
            ) {
                inside = !inside
            }
            j = i
        }
        if (shape.inverseFill) inside = !inside
        return if (inside) 1f else 0f
    }

    private fun kernelFor(plan: MaskBlurPlan.Ready): BlurKernelUniform {
        val activeSigma = max(0.5f, plan.effectiveSigma)
        val taps = (ceil(activeSigma).toInt() * 2 + 1).coerceIn(3, MAX_MASK_BLUR_TAPS)
        val half = taps / 2
        val activeWeights = FloatArray(taps) { index ->
            val x = (index - half).toFloat()
            exp(-(x * x) / (2f * activeSigma * activeSigma))
        }
        val sum = activeWeights.sum()
        val paddedWeights = FloatArray(MAX_MASK_BLUR_TAPS)
        activeWeights.forEachIndexed { index, value -> paddedWeights[index] = value / sum }
        return BlurKernelUniform(tapCount = taps, weights = paddedWeights)
    }

    private fun blurPass(
        source: FloatArray,
        width: Int,
        height: Int,
        kernel: BlurKernelUniform,
        horizontal: Boolean,
    ): FloatArray {
        val half = kernel.tapCount / 2
        val out = FloatArray(source.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var acc = 0f
                for (i in 0 until kernel.tapCount) {
                    val offset = i - half
                    val sx = if (horizontal) x + offset else x
                    val sy = if (horizontal) y else y + offset
                    if (sx < 0 || sx >= width || sy < 0 || sy >= height) continue
                    acc += kernel.weights[i] * source[sy * width + sx]
                }
                out[y * width + x] = acc
            }
        }
        return out
    }

    private fun stylePass(
        plan: MaskBlurPlan.Ready,
        original: FloatArray,
        blurred: FloatArray,
    ): FloatArray {
        val out = FloatArray(original.size)
        for (i in original.indices) {
            out[i] = when (plan.style) {
                NormalizedBlurStyle.NORMAL -> blurred[i]
                NormalizedBlurStyle.SOLID -> max(original[i], blurred[i])
                NormalizedBlurStyle.OUTER -> blurred[i] * (1f - original[i])
                NormalizedBlurStyle.INNER -> blurred[i] * original[i]
            }
        }
        return out
    }

    private fun compositePass(
        plan: MaskBlurPlan.Ready,
        styled: FloatArray,
        source: Color,
        blendMode: BlendMode,
        destinationEncoded: UByteArray,
        targetWidth: Int,
        targetHeight: Int,
        clip: Clip?,
    ): UByteArray {
        val bounds = plan.deviceBounds
        val localW = plan.localWidth
        val localH = plan.localHeight
        val srcLinear = source.toLinearPremul()
        val out = UByteArray(targetWidth * targetHeight * 4)
        // An AA analytic clip folds into the composite SHADER: the styled mask is sampled
        // from a fullscreen triangle with a clamp-to-edge sampler (the composite's
        // `maskSampler`), so a pixel center exactly ON the device-bounds edge still reads
        // the edge texel instead of hard zero. The oracle mirrors that for AA-clip lanes
        // (the non-AA clip and no-clip lanes keep the strict in-bounds gate). The clip
        // coverage is the shader's two-sided SDF ramp `0.5 - distance`, symmetric about
        // each edge: half-integer bounds place pixel centers on the ramp at 0.5 coverage,
        // and fractional bounds in (k+0.5, k+1) leave exterior half-pixels inside the ramp
        // (e.g. `0.5 - (x - left)` at the left edge) — both oracle-exact.
        val aaClip = clipAntiAliased(clip)
        for (y in 0 until targetHeight) {
            for (x in 0 until targetWidth) {
                val px = x + 0.5f
                val py = y + 0.5f
                val inBounds = px >= bounds.left && px < bounds.right && py >= bounds.top && py < bounds.bottom
                var coverage = if (inBounds || aaClip) {
                    val u = ((px - bounds.left) / (bounds.right - bounds.left) * localW).toInt()
                    val v = ((py - bounds.top) / (bounds.bottom - bounds.top) * localH).toInt()
                    val tu = u.coerceIn(0, localW - 1)
                    val tv = v.coerceIn(0, localH - 1)
                    styled[tv * localW + tu]
                } else {
                    0f
                }
                when (clip) {
                    is RectClip -> coverage *= rectClipCoverage(
                        px, py, clip.left, clip.top, clip.right, clip.bottom, clip.antiAlias,
                    )
                    is ComplexClip -> clip.elements.forEach { element ->
                        val elementCoverage = rectClipCoverage(
                            px, py, element.left, element.top, element.right, element.bottom,
                            element.antiAlias,
                        )
                        coverage *= when (element.operation) {
                            ComplexClipOperation.Intersect -> elementCoverage
                            ComplexClipOperation.Difference -> 1f - elementCoverage
                        }
                    }
                    null -> Unit
                }
                val dstIndex = (y * targetWidth + x) * 4
                val dstEncoded = FloatArray(4) { channel ->
                    destinationEncoded[dstIndex + channel].toInt() / 255f
                }
                val src = Premul4(
                    srcLinear[0] * coverage,
                    srcLinear[1] * coverage,
                    srcLinear[2] * coverage,
                    srcLinear[3] * coverage,
                )
                val blended = when (blendMode) {
                    BlendMode.SRC_OVER -> {
                        // Fixed-function composite: the source is blended against the
                        // decoded attachment in linear space, then the result is
                        // sRGB-encoded on write (observed GPU behavior).
                        val dst = Premul4(
                            srgbToLinear(dstEncoded[0]),
                            srgbToLinear(dstEncoded[1]),
                            srgbToLinear(dstEncoded[2]),
                            dstEncoded[3],
                        )
                        Premul4(
                            encodeLinear(src.r + dst.r * (1f - src.a)),
                            encodeLinear(src.g + dst.g * (1f - src.a)),
                            encodeLinear(src.b + dst.b * (1f - src.a)),
                            encodeAlpha(src.a + dst.a * (1f - src.a)),
                        )
                    }
                    BlendMode.SRC -> {
                        Premul4(
                            encodeLinear(src.r),
                            encodeLinear(src.g),
                            encodeLinear(src.b),
                            encodeAlpha(src.a),
                        )
                    }
                    else -> {
                        // Destination-read formula composite: linear blend, final encode.
                        val dst = Premul4(
                            srgbToLinear(dstEncoded[0]),
                            srgbToLinear(dstEncoded[1]),
                            srgbToLinear(dstEncoded[2]),
                            dstEncoded[3],
                        )
                        val formula = blendPremul(src, dst, blendMode)
                        Premul4(
                            encodeLinear(formula.r),
                            encodeLinear(formula.g),
                            encodeLinear(formula.b),
                            encodeAlpha(formula.a),
                        )
                    }
                }
                out[dstIndex] = encodeByte(blended.r)
                out[dstIndex + 1] = encodeByte(blended.g)
                out[dstIndex + 2] = encodeByte(blended.b)
                out[dstIndex + 3] = encodeByte(blended.a)
            }
        }
        return out
    }

    private fun clipAntiAliased(clip: Clip?): Boolean = when (clip) {
        null -> false
        is RectClip -> clip.antiAlias
        is ComplexClip -> clip.elements.all { it.antiAlias }
    }

    /**
     * One rect's two-sided SDF coverage at a pixel center, mirroring the composite
     * shader's `rect_coverage`: non-AA is a hard inside test, AA is the symmetric
     * `clamp(0.5 - distance, 0, 1)` ramp (a pixel center exactly on an edge gets 0.5).
     */
    private fun rectClipCoverage(
        px: Float,
        py: Float,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        antiAlias: Boolean,
    ): Float {
        if (!antiAlias) {
            return if (px < left || px >= right || py < top || py >= bottom) 0f else 1f
        }
        val xEdge = min(px - left + 0.5f, right - px + 0.5f)
        val yEdge = min(py - top + 0.5f, bottom - py + 0.5f)
        return min(1f, min(xEdge, yEdge).coerceIn(0f, 1f))
    }

    // --- blend math (composite route oracle: Porter-Duff + W3C formulas, linear) ---

    private data class Premul4(val r: Float, val g: Float, val b: Float, val a: Float)

    private fun Color.toLinearPremul(): FloatArray {
        val alpha = a
        return floatArrayOf(srgbToLinear(r) * alpha, srgbToLinear(g) * alpha, srgbToLinear(b) * alpha, alpha)
    }



    private fun FloatArray.toLinearPremul(): Premul4 = Premul4(this[0], this[1], this[2], this[3])

    private fun blendPremul(src: Premul4, dst: Premul4, mode: BlendMode): Premul4 = when (mode) {
        BlendMode.SRC_OVER -> Premul4(
            src.r + dst.r * (1f - src.a),
            src.g + dst.g * (1f - src.a),
            src.b + dst.b * (1f - src.a),
            src.a + dst.a * (1f - src.a),
        )
        BlendMode.SRC -> src
        BlendMode.DARKEN -> {
            // W3C compositing formula, mirroring kanvasBlendAdvancedPremul exactly:
            // the blend term B(Cb, Cs) = min(Cb, Cs) applies to the UNPREMULTIPLIED
            // colors, while the Porter-Duff terms use the premultiplied rgb.
            if (src.a == 0f) return dst
            val srcColor = floatArrayOf(src.r / src.a, src.g / src.a, src.b / src.a)
            val dstColor = floatArrayOf(dst.r / dst.a, dst.g / dst.a, dst.b / dst.a)
            val blended = floatArrayOf(
                min(srcColor[0], dstColor[0]),
                min(srcColor[1], dstColor[1]),
                min(srcColor[2], dstColor[2]),
            )
            val alpha = src.a + dst.a * (1f - src.a)
            Premul4(
                src.r * (1f - dst.a) + dst.r * (1f - src.a) + src.a * dst.a * blended[0],
                src.g * (1f - dst.a) + dst.g * (1f - src.a) + src.a * dst.a * blended[1],
                src.b * (1f - dst.a) + dst.b * (1f - src.a) + src.a * dst.a * blended[2],
                alpha,
            )
        }
        else -> error("TopLevelMaskBlurPixelOracle supports SRC_OVER, SRC, and DARKEN; got $mode")
    }

    private fun srgbToLinear(value: Float): Float =
        if (value <= 0.04045f) value / 12.92f else ((value + 0.055f) / 1.055f).pow(2.4f)

    private fun linearToSrgb(value: Float): Float {
        val clamped = value.coerceIn(0f, 1f)
        return if (clamped <= 0.0031308f) clamped * 12.92f else 1.055f * clamped.pow(1f / 2.4f) - 0.055f
    }

    private fun encode(value: Float): UByte = encodeByte(value)

    private fun encodeByte(value: Float): UByte {
        val clamped = value.coerceIn(0f, 1f)
        val scaled = clamped * 255f
        return (if (scaled - floor(scaled) >= 0.5f) ceil(scaled) else floor(scaled)).toInt()
            .coerceIn(0, 255).toUByte()
    }

    private fun encodeLinear(value: Float): Float {
        val clamped = value.coerceIn(0f, 1f)
        return linearToSrgb(clamped)
    }

    private fun encodeAlpha(value: Float): Float = value.coerceIn(0f, 1f)

    /** Overlays one non-AA solid rect onto an existing encoded destination (used for under-draws). */
    fun overlayRect(
        destination: UByteArray,
        targetWidth: Int,
        targetHeight: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        color: Color,
    ): UByteArray {
        val premul = color.toLinearPremul()
        val out = destination.copyOf()
        for (y in 0 until targetHeight) {
            for (x in 0 until targetWidth) {
                val px = x + 0.5f
                val py = y + 0.5f
                if (px >= left && px < right && py >= top && py < bottom) {
                    val index = (y * targetWidth + x) * 4
                    out[index] = encodeByte(encodeLinear(premul[0]))
                    out[index + 1] = encodeByte(encodeLinear(premul[1]))
                    out[index + 2] = encodeByte(encodeLinear(premul[2]))
                    out[index + 3] = encodeByte(encodeAlpha(premul[3]))
                }
            }
        }
        return out
    }

    /** Non-AA solid rect fill in the same encode convention (used for under-draws). */
    fun fillRect(
        targetWidth: Int,
        targetHeight: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        color: Color,
    ): UByteArray {
        val premul = color.toLinearPremul()
        val out = UByteArray(targetWidth * targetHeight * 4)
        for (y in 0 until targetHeight) {
            for (x in 0 until targetWidth) {
                val px = x + 0.5f
                val py = y + 0.5f
                if (px >= left && px < right && py >= top && py < bottom) {
                    val index = (y * targetWidth + x) * 4
                    out[index] = encodeByte(encodeLinear(premul[0]))
                    out[index + 1] = encodeByte(encodeLinear(premul[1]))
                    out[index + 2] = encodeByte(encodeLinear(premul[2]))
                    out[index + 3] = encodeByte(encodeAlpha(premul[3]))
                }
            }
        }
        return out
    }

    /**
     * Asserts the GPU pixels within a channel tolerance of the oracle.
     *
     * Tolerance rationale (24/255): the lane is four consecutive 8-bit stages
     * (mask -> blur-h -> blur-v -> style -> composite), each quantizing ~1-2/255
     * on top of the scene target's own 8-bit encode, and the styled mask is
     * sampled with nearest filtering into the scaled local grid, so a
     * boundary texel can shift by a full local texel when scale < 1. The
     * secondary exactness asserts in the flip-set tests pin the extremes
     * (exact 255/255 center coverage and exact 0/255 decal corners), so a
     * mismatched shape cannot hide inside the tolerance.
     */
    fun assertPixelsNear(expected: UByteArray, actual: UByteArray, tolerance: Int = 24) {
        require(expected.size == actual.size)
        require(actual.size % 4 == 0) {
            "Mask blur pixel buffers must be RGBA byte arrays"
        }
        var worst = 0
        var worstIdx = 0
        for (i in expected.indices) {
            val diff = abs(expected[i].toInt() - actual[i].toInt())
            if (diff > worst) { worst = diff; worstIdx = i }
        }
        // Derive the target width from the buffer length (closest factor pair of
        // the channel count) so non-32 targets dump correctly.
        val channels = actual.size / 4
        var w = sqrt(channels.toDouble()).toInt()
        while (w > 1 && channels % w != 0) w -= 1
        check(w >= 1) { "Mask blur pixel buffers must have a factorable channel count" }
        val h = channels / w
        val rows = listOf(0.25, 0.375, 0.5, 0.625, 0.75, 0.875)
            .map { fraction -> ((h - 1) * fraction).toInt() }
            .distinct()
        val cols = listOf(0.0, 0.125, 0.1875, 0.25, 0.46875, 0.53125, 0.5625, 0.625, 0.6875, 0.875)
            .map { fraction -> ((w - 1) * fraction).toInt() }
            .distinct()
        val sb = StringBuilder()
        for (y in rows) {
            sb.append("row ").append(y).append(": ")
            for (x in cols) {
                val idx = (y * w + x) * 4
                sb.append("(").append(x).append(",").append(y).append(")rgba=")
                    .append(actual[idx].toInt()).append(",").append(actual[idx+1].toInt()).append(",").append(actual[idx+2].toInt()).append(",").append(actual[idx+3].toInt())
                    .append(" / ")
                    .append(expected[idx].toInt()).append(",").append(expected[idx+1].toInt()).append(",").append(expected[idx+2].toInt()).append(",").append(expected[idx+3].toInt())
                    .append("  ")
            }
            sb.append("\n")
        }
        check(worst <= tolerance) {
            "top-level mask blur pixel mismatch: worst channel diff $worst at index $worstIdx > $tolerance\n" +
                sb.toString()
        }
    }
}
