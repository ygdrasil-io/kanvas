package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.RectF32
import kotlin.math.abs
import kotlin.math.floor

/** Conservative WgslFloatEnvelopeV1 proof; the full device rectangle encloses fragment centres. */
public class ImageNumericAuthorityV1 private constructor(
    public val graph: ImageNumericOperationGraphV1,
    private val programIdentity: MaterialProgramPlanId,
    private val uploadIdentity: String,
    private val coordinateIdentity: String,
    private val paintAlphaBitsI32: Int,
    deviceBoundsF32: RectF32,
) {
    private val bounds = deviceBoundsF32.copy()
    public fun copyDeviceBoundsF32(): RectF32 = bounds.copy()
    public val canonicalIdentity: String = "${graph.topologyIdentity}:${programIdentity.value}:$uploadIdentity:$coordinateIdentity:" +
        listOf(bounds.left, bounds.top, bounds.right, bounds.bottom).joinToString(",") { it.toRawBits().toString() } +
        ":paint=$paintAlphaBitsI32:texel-domain-v1:unorm8:positive-alpha-ge-2^-9:all-intermediates-lt-2^36"
    public fun authenticates(program: ImageMaterialProgramV3, execution: ImageSampleExecutionPlanV1): Boolean =
        program.structuralId == programIdentity && execution.upload.contentIdentity == uploadIdentity &&
            execution.coordinates.canonicalIdentity == coordinateIdentity && execution.numericAuthority === this &&
            execution.paintAlphaF32.toRawBits() == paintAlphaBitsI32 && execution.colorAlpha == graph.colorAlpha &&
            execution.sampling == graph.sampling && execution.tileModes == graph.tileModes &&
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
            sampling: ImageSamplingPlanV1, tileModes: ImageTileModePlanV1): ImageNumericAuthorityV1? {
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
            val values = coordinates.uniformValuesF32()
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
            fun evaluate(node: ImageNumericOperationGraphV1.Node): ClosedFloatingPointRange<Double> = memo.getOrPut(node) {
                val args = node.inputs.map(::evaluate)
                when (node.operation) {
                    ImageNumericOperationGraphV1.Operation.DEVICE_X_F32 -> deviceBoundsF32.left.toDouble()..deviceBoundsF32.right.toDouble()
                    ImageNumericOperationGraphV1.Operation.DEVICE_Y_F32 -> deviceBoundsF32.top.toDouble()..deviceBoundsF32.bottom.toDouble()
                    ImageNumericOperationGraphV1.Operation.UNIFORM_F32 -> values[node.uniformIndexI32].toDouble().let {
                        if (abs(it) < java.lang.Float.MIN_NORMAL) minOf(0.0, it)..maxOf(0.0, it) else it..it
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
                }
            }
            val denominator = evaluate(graph.denominator)
            if (!finite || denominator.start <= 0.0 && denominator.endInclusive >= 0.0) return null
            val haloF64 = if (sampling == ImageSamplingPlanV1.Linear) .5 else 0.0
            val samples = listOf(evaluate(graph.sourceX), evaluate(graph.sourceY))
            // Addressing converts the selected pre-reduction tap base. REPEAT/MIRROR therefore
            // receive the same finite I32 envelope before floor-mod; never rely on WGSL casts.
            if (!finite || samples.any {
                    floor(it.start - haloF64) < Int.MIN_VALUE.toDouble() ||
                        floor(it.endInclusive - haloF64) > Int.MAX_VALUE.toDouble() - if (sampling == ImageSamplingPlanV1.Linear) 1.0 else 0.0
                }) return null
            return ImageNumericAuthorityV1(graph, program.structuralId, upload.contentIdentity, coordinates.canonicalIdentity,
                paintAlphaF32.toRawBits(), deviceBoundsF32)
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
         * every association/contraction, and premultiplication/paint opacity stay below 2^36.
         * FTZ adds zero alternatives only. A8 returns [0,1]; its authenticated W5a/W5d child
         * has its own finite source proof. These coarse bounds prove finiteness, not final bytes.
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
