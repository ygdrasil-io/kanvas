package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.RectF32
import kotlin.math.abs
import kotlin.math.floor

private class ImageAddressReductionFactV1(
    private val mode: ImageTileAxisModePlanV1,
    private val indexI32: IntRange,
    private val dimensionI32: Int,
    private val signedRemainderI32: IntRange?,
    private val normalizedI32: IntRange?,
    private val secondModuloI32: IntRange?,
    private val mirrorFoldI32: IntRange?,
    private val addressedI32: IntRange,
) {
    private fun IntRange.identity(): String = "${first}:${last}"
    val canonicalIdentity: String = "$mode:${indexI32.identity()}:n=$dimensionI32:r=${signedRemainderI32?.identity()}:" +
        "add=${normalizedI32?.identity()}:mod=${secondModuloI32?.identity()}:fold=${mirrorFoldI32?.identity()}:" +
        "address=${addressedI32.identity()}"
}

/** Immutable arithmetic certificate for indices and the selected component sum. */
private class ImageSamplingArithmeticProofV1(
    private val xPreReductionI32: IntRange,
    private val yPreReductionI32: IntRange,
    private val xAddress: ImageAddressReductionFactV1,
    private val yAddress: ImageAddressReductionFactV1,
    private val accumulatedComponentF32: ClosedFloatingPointRange<Double>,
) {
    val canonicalIdentity: String = "x=${xPreReductionI32.first}:${xPreReductionI32.last}:y=${yPreReductionI32.first}:${yPreReductionI32.last}:" +
        "x-address=${xAddress.canonicalIdentity}:y-address=${yAddress.canonicalIdentity}:" +
        "acc=${accumulatedComponentF32.start.toRawBits()}:${accumulatedComponentF32.endInclusive.toRawBits()}"
}

/** Conservative WgslFloatEnvelopeV1 proof; the full device rectangle encloses fragment centres. */
public class ImageNumericAuthorityV1 private constructor(
    public val graph: ImageNumericOperationGraphV1,
    private val programIdentity: MaterialProgramPlanId,
    private val uploadIdentity: String,
    private val coordinateIdentity: String,
    private val paintAlphaBitsI32: Int,
    private val samplingProof: ImageSamplingArithmeticProofV1,
    deviceBoundsF32: RectF32,
    public val cellSelection: ImageCellSelectionPlanV1?,
) {
    private val bounds = deviceBoundsF32.copy()
    public fun copyDeviceBoundsF32(): RectF32 = bounds.copy()
    public val canonicalIdentity: String = "${graph.topologyIdentity}:sampling=${graph.sampling.bindingIdentity}:${programIdentity.value}:$uploadIdentity:$coordinateIdentity:" +
        listOf(bounds.left, bounds.top, bounds.right, bounds.bottom).joinToString(",") { it.toRawBits().toString() } +
        ":paint=$paintAlphaBitsI32:texel-domain-v1:unorm8:positive-alpha-ge-2^-9:signed-components-abs-lt-2^36:" +
        "sampling-arithmetic-v1:${samplingProof.canonicalIdentity}" +
        graph.cubicScheduleIdentity?.let { ":$it" }.orEmpty() + cellSelection?.let { ":${it.canonicalIdentity}" }.orEmpty()
    public fun authenticates(program: ImageMaterialProgramV3, execution: ImageSampleExecutionPlanV1): Boolean =
        program.structuralId == programIdentity && execution.upload.contentIdentity == uploadIdentity &&
            execution.coordinates.canonicalIdentity == coordinateIdentity && execution.numericAuthority === this &&
            execution.paintAlphaF32.toRawBits() == paintAlphaBitsI32 && execution.colorAlpha == graph.colorAlpha &&
            execution.sampling == graph.sampling && execution.tileModes == graph.tileModes &&
            program.selectsCells == (cellSelection != null) &&
            cellSelection?.samples.orEmpty().all { sample ->
                val proof = sample.numericAuthority
                proof.programIdentity == programIdentity && proof.uploadIdentity == uploadIdentity &&
                    proof.coordinateIdentity == sample.coordinates.canonicalIdentity && proof.paintAlphaBitsI32 == paintAlphaBitsI32 &&
                    proof.graph.colorAlpha == graph.colorAlpha && proof.graph.sampling == graph.sampling &&
                    proof.graph.tileModes == graph.tileModes && proof.cellSelection == null && proof.bounds == bounds
            } &&
            execution.upload.widthI32 > 0 && execution.upload.heightI32 > 0 &&
            execution.colorAlpha == when (program) {
                is ImageMaterialProgramV3.ColorV3 -> ImageColorAlphaPlanV1(program.channelOrder, program.alphaType, program.transfer, program.gamut).takeIf {
                    program.sampling == execution.sampling && program.tileModes == execution.tileModes }
                is ImageMaterialProgramV3.MaskV3 -> ImageColorAlphaPlanV1(ImageChannelOrderV1.ALPHA, program.alphaType,
                    ImageTransferPlanV1.NONE, ImageGamutPlanV1.NONE).takeIf {
                    program.sampling == execution.sampling && program.tileModes == execution.tileModes }
            }

    internal companion object {
        fun seal(program: ImageMaterialProgramV3, upload: ImageUploadPlanV1,
            coordinates: ImageCoordinatePlanV1, deviceBoundsF32: RectF32, paintAlphaF32: Float,
            sampling: ImageSamplingPlanV1, tileModes: ImageTileModePlanV1,
            cells: List<ImageCellPlanV1.Sampled>? = null): ImageNumericAuthorityV1? {
            if (listOf(deviceBoundsF32.left, deviceBoundsF32.top, deviceBoundsF32.right, deviceBoundsF32.bottom)
                    .any { !it.isFinite() } || !deviceBoundsF32.isSorted()) return null
            if (!paintAlphaF32.isFinite() || paintAlphaF32 !in 0f..1f) return null
            val color = when (program) {
                is ImageMaterialProgramV3.ColorV3 -> ImageColorAlphaPlanV1(program.channelOrder, program.alphaType, program.transfer, program.gamut)
                is ImageMaterialProgramV3.MaskV3 -> ImageColorAlphaPlanV1(ImageChannelOrderV1.ALPHA, program.alphaType,
                    ImageTransferPlanV1.NONE, ImageGamutPlanV1.NONE)
            }
            if (!provesFiniteTexelDomain(color, upload)) return null
            if (upload.widthI32 <= 0 || upload.heightI32 <= 0) return null
            val graph = ImageNumericOperationGraphV1.of(color, sampling, tileModes)
            val cubic = sampling as? ImageSamplingPlanV1.Cubic
            val bF32 = cubic?.let { Float.fromBits(it.bBitsI32) } ?: 0f
            val cF32 = cubic?.let { Float.fromBits(it.cBitsI32) } ?: 0f
            if (!bF32.isFinite() || !cF32.isFinite() || bF32 !in 0f..1f || cF32 !in 0f..1f) return null
            val values = coordinates.uniformValuesF32() +
                listOf(upload.widthI32.toFloat(), upload.heightI32.toFloat(), paintAlphaF32, 0f, bF32, cF32, 0f, 0f)
            var finite = true
            fun rounded(lowF64: Double, highF64: Double, division: Boolean = false): ClosedFloatingPointRange<Double> {
                // One full F32 ULP for basic ops, eight for division; include FTZ alternatives.
                val errorF64 = maxOf(abs(lowF64), abs(highF64)) * Math.scalb(1.0, if (division) -20 else -23) + java.lang.Float.MIN_NORMAL
                val low = Math.nextDown(lowF64 - errorF64)
                val high = Math.nextUp(highF64 + errorF64)
                finite = finite && low.isFinite() && high.isFinite() && maxOf(abs(low), abs(high)) <= Float.MAX_VALUE.toDouble()
                return low..high
            }
            val memo = mutableMapOf<ImageNumericOperationGraphV1.Node, ClosedFloatingPointRange<Double>>()
            fun evaluateWithMemo(node: ImageNumericOperationGraphV1.Node,
                cache: MutableMap<ImageNumericOperationGraphV1.Node, ClosedFloatingPointRange<Double>>): ClosedFloatingPointRange<Double> = cache.getOrPut(node) {
                val args = node.inputs.map { evaluateWithMemo(it, cache) }
                when (node.operation) {
                    ImageNumericOperationGraphV1.Operation.DEVICE_X_F32 -> deviceBoundsF32.left.toDouble()..deviceBoundsF32.right.toDouble()
                    ImageNumericOperationGraphV1.Operation.DEVICE_Y_F32 -> deviceBoundsF32.top.toDouble()..deviceBoundsF32.bottom.toDouble()
                    ImageNumericOperationGraphV1.Operation.UNIFORM_F32 -> values[node.uniformIndexI32].toDouble().let {
                        if (abs(it) < java.lang.Float.MIN_NORMAL) minOf(0.0, it)..maxOf(0.0, it) else it..it
                    }
                    ImageNumericOperationGraphV1.Operation.CONSTANT_HALF_F32 -> 0.5..0.5
                    ImageNumericOperationGraphV1.Operation.CONSTANT_ONE_F32 -> 1.0..1.0
                    ImageNumericOperationGraphV1.Operation.CONSTANT_F32 -> Float.fromBits(node.constantBitsI32).toDouble().let { it..it }
                    ImageNumericOperationGraphV1.Operation.KERNEL_DISTANCE_F32 -> {
                        // A kernel invocation must supply this input; an unbound graph fails closed.
                        finite = false
                        0.0..0.0
                    }
                    ImageNumericOperationGraphV1.Operation.TEXEL_COMPONENT_F32 -> -Math.scalb(1.0, 36)..Math.scalb(1.0, 36)
                    ImageNumericOperationGraphV1.Operation.ABS_F32 -> {
                        val range = args.single()
                        val low = when {
                            range.start >= 0.0 -> range.start
                            range.endInclusive <= 0.0 -> -range.endInclusive
                            else -> 0.0
                        }
                        low..maxOf(abs(range.start), abs(range.endInclusive))
                    }
                    ImageNumericOperationGraphV1.Operation.TAP_INDEX_F32 -> {
                        // The signed addition is checked before its conversion to F32. One
                        // full ULP conservatively includes the integer-to-F32 rounding.
                        val low = args.single().start + node.uniformIndexI32.toDouble()
                        val high = args.single().endInclusive + node.uniformIndexI32.toDouble()
                        if (low < Int.MIN_VALUE.toDouble() || high > Int.MAX_VALUE.toDouble()) {
                            finite = false
                            0.0..0.0
                        } else rounded(low, high)
                    }
                    ImageNumericOperationGraphV1.Operation.CUBIC_KERNEL_F32 -> {
                        val kernel = requireNotNull(graph.cubicKernel)
                        val distance = args.single()
                        val absolute = evaluateWithMemo(kernel.absoluteDistance, mutableMapOf(kernel.distance to distance))
                        val alternatives = mutableListOf<ClosedFloatingPointRange<Double>>()
                        val innerLimitF64 = kernel.innerLimitF32.toDouble()
                        val outerLimitF64 = kernel.outerLimitF32.toDouble()
                        fun branch(root: ImageNumericOperationGraphV1.Node, domain: ClosedFloatingPointRange<Double>) =
                            evaluateWithMemo(root, mutableMapOf(kernel.distance to distance, kernel.absoluteDistance to domain))
                        // These restrictions follow the emitted comparisons. Every polynomial
                        // intermediate is evaluated in its actual branch, never weight-clamped.
                        if (absolute.start < innerLimitF64)
                            alternatives += branch(kernel.innerResult, absolute.start..minOf(absolute.endInclusive, innerLimitF64))
                        if (absolute.endInclusive >= innerLimitF64 && absolute.start < outerLimitF64)
                            alternatives += branch(kernel.outerResult, maxOf(absolute.start, innerLimitF64)..minOf(absolute.endInclusive, outerLimitF64))
                        if (absolute.endInclusive >= outerLimitF64) alternatives += evaluateWithMemo(kernel.outsideResult, mutableMapOf())
                        alternatives.minOf { it.start }..alternatives.maxOf { it.endInclusive }
                    }
                    ImageNumericOperationGraphV1.Operation.ADD_F32 -> rounded(args[0].start + args[1].start, args[0].endInclusive + args[1].endInclusive)
                    ImageNumericOperationGraphV1.Operation.SUB_F32 -> rounded(args[0].start - args[1].endInclusive, args[0].endInclusive - args[1].start)
                    ImageNumericOperationGraphV1.Operation.MUL_F32, ImageNumericOperationGraphV1.Operation.DIV_F32 -> {
                        val division = node.operation == ImageNumericOperationGraphV1.Operation.DIV_F32
                        if (division && args[1].start <= 0.0 && args[1].endInclusive >= 0.0) {
                            finite = false
                            0.0..0.0
                        } else {
                            val candidates = listOf(args[0].start, args[0].endInclusive).flatMap { a ->
                                listOf(args[1].start, args[1].endInclusive).map { b -> if (division) a / b else a * b }
                            }
                            rounded(candidates.min(), candidates.max(), division)
                        }
                    }
                    ImageNumericOperationGraphV1.Operation.FLOOR_F32 -> {
                        val low = floor(args[0].start)
                        val high = floor(args[0].endInclusive)
                        finite = finite && low.isFinite() && high.isFinite() &&
                            low >= -Float.MAX_VALUE.toDouble() && high <= Float.MAX_VALUE.toDouble()
                        low..high
                    }
                }
            }
            fun evaluate(node: ImageNumericOperationGraphV1.Node): ClosedFloatingPointRange<Double> = evaluateWithMemo(node, memo)
            val denominator = evaluate(graph.denominator)
            if (!finite || denominator.start <= 0.0 && denominator.endInclusive >= 0.0) return null
            val samplingProof = proveSamplingArithmetic(graph, upload, ::evaluate, ::rounded) ?: return null
            if (!finite) return null
            val cellSelection = cells?.let { sourceCells ->
                if (sourceCells.size > 9) return null
                // Each graph covers the FULL enclosing device domain, including the
                // exterior AA fragments that the outer selector bands extrapolate.
                // Thus branch uncertainty cannot expose an unproved scalar/index path.
                val cellSamples = sourceCells.map { cell ->
                    val mapping = ImageCoordinatePlanV1.sealInverse(coordinates.copyInverseF32(), cell.copySourceF32(), cell.copyDestinationF32())
                    val proof = seal(program, upload, mapping, deviceBoundsF32, paintAlphaF32, sampling, tileModes) ?: return null
                    ImageCellSelectionPlanV1.Sample(cell, mapping, proof)
                }
                ImageCellSelectionPlanV1(cellSamples)
            }
            return ImageNumericAuthorityV1(graph, program.structuralId, upload.contentIdentity, coordinates.canonicalIdentity,
                paintAlphaF32.toRawBits(), samplingProof, deviceBoundsF32, cellSelection)
        }

        /**
         * Mirrors the selected WGSL schedule: the half-pixel shift, Linear fractions/weights or
         * Cubic distance/kernel/product nodes, then the left-associated component accumulation. The I32
         * intervals are established before Clamp/Repeat/Mirror/Decal; modulo never receives an
         * unchecked conversion and MIRROR's 2n arithmetic is proven representable.
         */
        private fun proveSamplingArithmetic(
            graph: ImageNumericOperationGraphV1,
            upload: ImageUploadPlanV1,
            evaluate: (ImageNumericOperationGraphV1.Node) -> ClosedFloatingPointRange<Double>,
            rounded: (Double, Double, Boolean) -> ClosedFloatingPointRange<Double>,
        ): ImageSamplingArithmeticProofV1? {
            fun i32(range: ClosedFloatingPointRange<Double>, extraLowI32: Int, extraHighI32: Int): IntRange? {
                val low = floor(range.start)
                val high = floor(range.endInclusive)
                if (!low.isFinite() || !high.isFinite() || low < Int.MIN_VALUE.toDouble() + extraLowI32 ||
                    high > Int.MAX_VALUE.toDouble() - extraHighI32) return null
                return low.toInt()..high.toInt()
            }
            fun offset(range: IntRange, lowI32: Int, highI32: Int): IntRange =
                Math.addExact(range.first, lowI32)..Math.addExact(range.last, highI32)
            fun checkedI64(lowI64: Long, highI64: Long): IntRange? {
                if (lowI64 < Int.MIN_VALUE.toLong() || highI64 > Int.MAX_VALUE.toLong()) return null
                return lowI64.toInt()..highI64.toInt()
            }
            fun signedRemainder(indexI32: IntRange, divisorI32: Int): IntRange {
                // WGSL's signed % keeps the dividend sign. These bounds cover every exact
                // remainder over the authenticated pre-reduction interval, not a hardware cap.
                val magnitudeI64 = divisorI32.toLong() - 1L
                val lowI64 = when {
                    indexI32.first >= 0 -> 0L
                    else -> -minOf(magnitudeI64, -indexI32.first.toLong())
                }
                val highI64 = when {
                    indexI32.last <= 0 -> 0L
                    else -> minOf(magnitudeI64, indexI32.last.toLong())
                }
                return lowI64.toInt()..highI64.toInt()
            }
            fun reduction(indexI32: IntRange, dimensionI32: Int, mode: ImageTileAxisModePlanV1): ImageAddressReductionFactV1? {
                if (dimensionI32 <= 0) return null
                if (mode == ImageTileAxisModePlanV1.CLAMP || mode == ImageTileAxisModePlanV1.DECAL)
                    return ImageAddressReductionFactV1(mode, indexI32, dimensionI32, null, null, null, null, 0..(dimensionI32 - 1))
                val periodI32 = when (mode) {
                    ImageTileAxisModePlanV1.REPEAT -> dimensionI32
                    ImageTileAxisModePlanV1.MIRROR -> checkedI64(dimensionI32.toLong() * 2L, dimensionI32.toLong() * 2L)?.first
                    else -> error("unreachable")
                } ?: return null
                val remainder = signedRemainder(indexI32, periodI32)
                // This is the emitted (index % period) + period, checked in I64 before Ready.
                val normalized = checkedI64(remainder.first.toLong() + periodI32.toLong(),
                    remainder.last.toLong() + periodI32.toLong()) ?: return null
                // The normalized dividend is positive, so its second signed % is [0, period - 1].
                val secondModulo = 0..(periodI32 - 1)
                val fold = if (mode == ImageTileAxisModePlanV1.MIRROR) {
                    // This is the emitted min(phase, period - 1 - phase), with both subtraction
                    // endpoints checked in I64 before its I32 WGSL evaluation.
                    checkedI64(periodI32.toLong() - 1L - secondModulo.last.toLong(),
                        periodI32.toLong() - 1L - secondModulo.first.toLong()) ?: return null
                } else null
                // With period = 2n, min(phase, period - 1 - phase) is exactly in [0, n - 1].
                val addressed = if (mode == ImageTileAxisModePlanV1.MIRROR) 0..(dimensionI32 - 1) else secondModulo
                return ImageAddressReductionFactV1(mode, indexI32, dimensionI32, remainder, normalized, secondModulo, fold, addressed)
            }
            val (extraLow, extraHigh) = when (graph.sampling) {
                ImageSamplingPlanV1.Nearest -> 0 to 0
                ImageSamplingPlanV1.Linear -> 0 to 1
                is ImageSamplingPlanV1.Cubic -> 1 to 2
            }
            val baseX = i32(evaluate(graph.baseXF32), extraLow, extraHigh) ?: return null
            val baseY = i32(evaluate(graph.baseYF32), extraLow, extraHigh) ?: return null
            val xPre = offset(baseX, -extraLow, extraHigh)
            val yPre = offset(baseY, -extraLow, extraHigh)
            val xAddress = reduction(xPre, upload.widthI32, graph.tileModes.x) ?: return null
            val yAddress = reduction(yPre, upload.heightI32, graph.tileModes.y) ?: return null
            val signedTexelComponent = -Math.scalb(1.0, 36)..Math.scalb(1.0, 36)
            if (graph.sampling == ImageSamplingPlanV1.Nearest)
                return ImageSamplingArithmeticProofV1(xPre, yPre, xAddress, yAddress, signedTexelComponent)

            if (graph.sampling is ImageSamplingPlanV1.Cubic) {
                // Evaluation walks the same distance/branch/coefficient/polynomial,
                // separable-product and row-major accumulation nodes the emitter consumes.
                val accumulated = evaluate(requireNotNull(graph.cubicAccumulationF32))
                if (!accumulated.start.isFinite() || !accumulated.endInclusive.isFinite() ||
                    maxOf(abs(accumulated.start), abs(accumulated.endInclusive)) > Float.MAX_VALUE.toDouble()) return null
                return ImageSamplingArithmeticProofV1(xPre, yPre, xAddress, yAddress, accumulated)
            }

            val weights = listOf(requireNotNull(graph.weight00F32), requireNotNull(graph.weight10F32),
                requireNotNull(graph.weight01F32), requireNotNull(graph.weight11F32)).map(evaluate)
            if (weights.any { !it.start.isFinite() || !it.endInclusive.isFinite() }) return null
            fun products(a: ClosedFloatingPointRange<Double>, b: ClosedFloatingPointRange<Double>): ClosedFloatingPointRange<Double> {
                val candidates = listOf(a.start, a.endInclusive).flatMap { x -> listOf(b.start, b.endInclusive).map { y -> x * y } }
                return rounded(candidates.min(), candidates.max(), false)
            }
            val weighted = weights.map { products(signedTexelComponent, it) }
            var accumulated = weighted.first()
            for (term in weighted.drop(1)) accumulated = rounded(accumulated.start + term.start,
                accumulated.endInclusive + term.endInclusive, false)
            if (!accumulated.start.isFinite() || !accumulated.endInclusive.isFinite() ||
                maxOf(abs(accumulated.start), abs(accumulated.endInclusive)) > Float.MAX_VALUE.toDouble()) return null
            return ImageSamplingArithmeticProofV1(xPre, yPre, xAddress, yAddress, accumulated)
        }

        /**
         * A universal finite-domain proof for the authenticated byte upload, not a pixel oracle.
         * UNORM8 endpoints are exact; every positive channel is at least 1/255 including its
         * F32 conversion envelope, hence greater than 2^-9. The zero-alpha branch dominates
         * division. With its full 2.5-ULP envelope, straight RGB is nonnegative and <2^10.
         * The transfer's pow base (c + .055)/1.055 is strictly between 2^-5 and 2^11,
         * including eagerly evaluated select alternatives. WGSL pow inherits log2, multiply,
         * exp2: log2 stays in (-6,12), the exponent in (-16,30), exp2 below 2^31 with
         * their full accuracy envelopes. Three gamut products (coefficients magnitude <2),
         * every association/contraction, and premultiplication/paint opacity have absolute value
         * below 2^36. Display-P3 conversion may make a linear component negative (for example
         * the green/blue output of saturated red), so sampling uses the signed [-2^36, 2^36]
         * component domain. FTZ adds zero alternatives only. A8 returns [0,1]; its authenticated
         * W5a/W5d child has its own finite source proof. These coarse bounds prove finiteness,
         * not final bytes.
         */
        private fun provesFiniteTexelDomain(color: ImageColorAlphaPlanV1, upload: ImageUploadPlanV1): Boolean {
            if (color.alphaType !in setOf(org.graphiks.kanvas.render.ir.ImageAlphaType.OPAQUE,
                    org.graphiks.kanvas.render.ir.ImageAlphaType.PREMUL, org.graphiks.kanvas.render.ir.ImageAlphaType.UNPREMUL)) return false
            return when (color.channelOrder) {
                ImageChannelOrderV1.ALPHA -> upload.logicalFormat == org.graphiks.kanvas.render.ir.ImagePixelFormat.ALPHA_8 &&
                    upload.physicalFormat == ImagePhysicalFormatV1.R8_UNORM && color.transfer == ImageTransferPlanV1.NONE && color.gamut == ImageGamutPlanV1.NONE
                ImageChannelOrderV1.BGRA, ImageChannelOrderV1.RGBA -> {
                    val formatMatches = if (color.channelOrder == ImageChannelOrderV1.BGRA)
                        upload.logicalFormat == org.graphiks.kanvas.render.ir.ImagePixelFormat.BGRA_8888 else
                        upload.logicalFormat in setOf(org.graphiks.kanvas.render.ir.ImagePixelFormat.RGBA_8888,
                            org.graphiks.kanvas.render.ir.ImagePixelFormat.SRGBA_8888)
                    formatMatches && upload.physicalFormat == ImagePhysicalFormatV1.RGBA8_UNORM &&
                        (color.transfer == ImageTransferPlanV1.SRGB && color.gamut in setOf(ImageGamutPlanV1.SRGB, ImageGamutPlanV1.DISPLAY_P3) ||
                            color.transfer == ImageTransferPlanV1.LINEAR && color.gamut == ImageGamutPlanV1.SRGB) &&
                        (upload.logicalFormat != org.graphiks.kanvas.render.ir.ImagePixelFormat.SRGBA_8888 ||
                            color.transfer == ImageTransferPlanV1.SRGB && color.gamut == ImageGamutPlanV1.SRGB)
                }
            }
        }
    }
}
