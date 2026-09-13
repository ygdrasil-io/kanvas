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
    deviceBoundsF32: RectF32,
) {
    private val bounds = deviceBoundsF32.copy()
    public fun copyDeviceBoundsF32(): RectF32 = bounds.copy()
    public val canonicalIdentity: String = "${graph.topologyIdentity}:${programIdentity.value}:$uploadIdentity:$coordinateIdentity:" +
        listOf(bounds.left, bounds.top, bounds.right, bounds.bottom).joinToString(",") { it.toRawBits().toString() }
    public fun authenticates(program: ImageMaterialProgramV3, execution: ImageSampleExecutionPlanV1): Boolean =
        program.structuralId == programIdentity && execution.upload.contentIdentity == uploadIdentity &&
            execution.coordinates.canonicalIdentity == coordinateIdentity && execution.numericAuthority === this &&
            execution.sampling == ImageSamplingPlanV1.Nearest && execution.tileX == ImageTilePlanV1.CLAMP &&
            execution.tileY == ImageTilePlanV1.CLAMP && program is ImageMaterialProgramV3.ColorV3 &&
            execution.colorAlpha == ImageColorAlphaPlanV1(program.channelOrder, program.alphaType, program.transfer, program.gamut)

    internal companion object {
        fun seal(program: ImageMaterialProgramV3.ColorV3, upload: ImageUploadPlanV1,
            coordinates: ImageCoordinatePlanV1, deviceBoundsF32: RectF32): ImageNumericAuthorityV1? {
            if (listOf(deviceBoundsF32.left, deviceBoundsF32.top, deviceBoundsF32.right, deviceBoundsF32.bottom)
                    .any { !it.isFinite() } || !deviceBoundsF32.isSorted()) return null
            val graph = ImageNumericOperationGraphV1.nearest()
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
            val samples = listOf(evaluate(graph.sourceX), evaluate(graph.sourceY))
            if (!finite || samples.any { floor(it.start) < Int.MIN_VALUE.toDouble() || floor(it.endInclusive) > Int.MAX_VALUE.toDouble() }) return null
            return ImageNumericAuthorityV1(graph, program.structuralId, upload.contentIdentity, coordinates.canonicalIdentity, deviceBoundsF32)
        }
    }
}
