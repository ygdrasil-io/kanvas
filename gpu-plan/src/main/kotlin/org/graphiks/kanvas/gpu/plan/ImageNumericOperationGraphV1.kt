package org.graphiks.kanvas.gpu.plan

private fun imageScalarScheduleIdentity(roots: List<ImageNumericOperationGraphV1.Node>): String {
    val indices = linkedMapOf<ImageNumericOperationGraphV1.Node, Int>()
    fun visit(node: ImageNumericOperationGraphV1.Node) {
        if (node in indices) return
        node.inputs.forEach(::visit)
        indices[node] = indices.size
    }
    roots.forEach(::visit)
    return indices.entries.joinToString(";") { (node, indexI32) ->
        "$indexI32:${node.operation}:${node.uniformIndexI32}:${node.constantBitsI32}:" +
            node.inputs.joinToString(",") { indices.getValue(it).toString() }
    } + ":roots=" + roots.joinToString(",") { indices.getValue(it).toString() }
}

/** Executed scalar schedule and selected tap/address topology. */
public class ImageNumericOperationGraphV1 private constructor(public val colorAlpha: ImageColorAlphaPlanV1,
    public val sampling: ImageSamplingPlanV1, public val tileModes: ImageTileModePlanV1) {
    public enum class Operation { DEVICE_X_F32, DEVICE_Y_F32, UNIFORM_F32, CONSTANT_HALF_F32, CONSTANT_ONE_F32,
        ADD_F32, SUB_F32, MUL_F32, DIV_F32, FLOOR_F32, CONSTANT_F32, ABS_F32,
        KERNEL_DISTANCE_F32, TAP_INDEX_F32, CUBIC_KERNEL_F32, TEXEL_COMPONENT_F32 }
    public class Node internal constructor(public val operation: Operation, public val uniformIndexI32: Int = -1,
        inputs: List<Node> = emptyList(), public val constantBitsI32: Int = 0) {
        public val inputs: List<Node> = immutableList(inputs)
    }
    /** Branch domains are exact comparisons of abs(distance), not weight clamping. */
    public class CubicKernelGraph internal constructor() {
        public val distance: Node = Node(Operation.KERNEL_DISTANCE_F32)
        public val absoluteDistance: Node = Node(Operation.ABS_F32, inputs = listOf(distance))
        public val innerLimitF32: Float = 1f
        public val outerLimitF32: Float = 2f
        public val innerResult: Node
        public val outerResult: Node
        public val outsideResult: Node = Node(Operation.CONSTANT_F32, constantBitsI32 = 0f.toRawBits())
        public val scheduleIdentity: String
        init {
            fun constant(value: Float) = Node(Operation.CONSTANT_F32, constantBitsI32 = value.toRawBits())
            fun binary(operation: Operation, a: Node, b: Node) = Node(operation, inputs = listOf(a, b))
            fun add(a: Node, b: Node) = binary(Operation.ADD_F32, a, b)
            fun subtract(a: Node, b: Node) = binary(Operation.SUB_F32, a, b)
            fun multiply(a: Node, b: Node) = binary(Operation.MUL_F32, a, b)
            val b = Node(Operation.UNIFORM_F32, 24)
            val c = Node(Operation.UNIFORM_F32, 25)
            fun scaled(value: Float, parameter: Node) = multiply(constant(value), parameter)
            fun polynomial(outer: Boolean): Node {
                val coefficient3 = if (outer) subtract(subtract(constant(0f), b), scaled(6f, c))
                    else subtract(subtract(constant(12f), scaled(9f, b)), scaled(6f, c))
                val coefficient2 = if (outer) add(scaled(6f, b), scaled(30f, c))
                    else add(add(constant(-18f), scaled(12f, b)), scaled(6f, c))
                val x = absoluteDistance
                val cubicTerm = multiply(multiply(multiply(coefficient3, x), x), x)
                val squareTerm = multiply(multiply(coefficient2, x), x)
                var numerator = add(cubicTerm, squareTerm)
                if (outer) numerator = add(numerator, multiply(subtract(scaled(-12f, b), scaled(48f, c)), x))
                val coefficient0 = if (outer) add(scaled(8f, b), scaled(24f, c))
                    else subtract(constant(6f), scaled(2f, b))
                return binary(Operation.DIV_F32, add(numerator, coefficient0), constant(6f))
            }
            innerResult = polynomial(false)
            outerResult = polynomial(true)
            scheduleIdentity = "ordered-abs-lt:${innerLimitF32.toRawBits()}:${outerLimitF32.toRawBits()}:otherwise:" +
                imageScalarScheduleIdentity(listOf(innerResult, outerResult, outsideResult))
        }
    }
    public enum class TexelOperation { PROJECTIVE_VALIDITY_MASK, PIXEL_CENTER_NEAREST, PIXEL_CENTER_LINEAR, PIXEL_CENTER_CUBIC,
        FLOOR_F32, CONVERT_I32, MINUS_ONE_TAP_OFFSET_I32, ZERO_TAP_OFFSET_I32, PLUS_ONE_TAP_OFFSET_I32, PLUS_TWO_TAP_OFFSET_I32,
        MITCHELL_NETRAVALI_KERNEL_F32, CLAMP_X_I32, CLAMP_Y_I32,
        REPEAT_X_I32, REPEAT_Y_I32, MIRROR_X_I32, MIRROR_Y_I32, DECAL_X_I32, DECAL_Y_I32, LOAD_UNORM8,
        SWIZZLE_BGRA, ALPHA_OPAQUE, ALPHA_STORED, ZERO_ALPHA_GUARD,
        UNPREMULTIPLY_SOURCE, SRGB_TO_LINEAR, DISPLAY_P3_TO_LINEAR_SRGB,
        PREMULTIPLY_LINEAR, RETURN_SCALAR_MASK, ACCUMULATE_NEAREST, ACCUMULATE_LINEAR, ACCUMULATE_CUBIC_ROW_MAJOR, PAINT_OPACITY }
    public val contractId: String = "WgslFloatEnvelopeV1"
    public val topologyIdentity: String = "w5e-image-numeric-v1:inverse-project-divide-map:${sampling.topologyId}:${tileModes.topologyId}:$colorAlpha:" +
        texelOperations().joinToString(",") { it.name } +
        (if (sampling is ImageSamplingPlanV1.Cubic) ":cubic-scalar-schedule-v1" else "")
    public val denominator: Node
    public val localXF32: Node
    public val localYF32: Node
    public val sourceX: Node
    public val sourceY: Node
    public val unitXF32: Node
    public val unitYF32: Node
    /** These nodes are emitted verbatim by the sampler; sealing evaluates this same graph. */
    public val tapXF32: Node
    public val tapYF32: Node
    public val baseXF32: Node
    public val baseYF32: Node
    public val fractionXF32: Node?
    public val fractionYF32: Node?
    public val weight00F32: Node?
    public val weight10F32: Node?
    public val weight01F32: Node?
    public val weight11F32: Node?
    public val cubicKernel: CubicKernelGraph?
    public val cubicWeightsF32: List<Node>
    public val cubicAccumulationF32: Node?
    public val cubicScheduleIdentity: String?
    public fun texelOperations(): List<TexelOperation> = buildList {
        add(TexelOperation.PROJECTIVE_VALIDITY_MASK)
        add(when (sampling) {
            ImageSamplingPlanV1.Nearest -> TexelOperation.PIXEL_CENTER_NEAREST
            ImageSamplingPlanV1.Linear -> TexelOperation.PIXEL_CENTER_LINEAR
            is ImageSamplingPlanV1.Cubic -> TexelOperation.PIXEL_CENTER_CUBIC
        })
        addAll(listOf(TexelOperation.FLOOR_F32, TexelOperation.CONVERT_I32, TexelOperation.ZERO_TAP_OFFSET_I32))
        when (sampling) {
            ImageSamplingPlanV1.Linear -> add(TexelOperation.PLUS_ONE_TAP_OFFSET_I32)
            is ImageSamplingPlanV1.Cubic -> addAll(listOf(TexelOperation.MINUS_ONE_TAP_OFFSET_I32,
                TexelOperation.PLUS_ONE_TAP_OFFSET_I32, TexelOperation.PLUS_TWO_TAP_OFFSET_I32, TexelOperation.MITCHELL_NETRAVALI_KERNEL_F32))
            ImageSamplingPlanV1.Nearest -> Unit
        }
        fun axis(mode: ImageTileAxisModePlanV1, x: Boolean) = when (mode) {
            ImageTileAxisModePlanV1.CLAMP -> if (x) TexelOperation.CLAMP_X_I32 else TexelOperation.CLAMP_Y_I32
            ImageTileAxisModePlanV1.REPEAT -> if (x) TexelOperation.REPEAT_X_I32 else TexelOperation.REPEAT_Y_I32
            ImageTileAxisModePlanV1.MIRROR -> if (x) TexelOperation.MIRROR_X_I32 else TexelOperation.MIRROR_Y_I32
            ImageTileAxisModePlanV1.DECAL -> if (x) TexelOperation.DECAL_X_I32 else TexelOperation.DECAL_Y_I32
        }
        add(axis(tileModes.x, true)); add(axis(tileModes.y, false)); add(TexelOperation.LOAD_UNORM8)
        if (colorAlpha.channelOrder == ImageChannelOrderV1.BGRA) add(TexelOperation.SWIZZLE_BGRA)
        add(if (colorAlpha.alphaType == org.graphiks.kanvas.render.ir.ImageAlphaType.OPAQUE)
            TexelOperation.ALPHA_OPAQUE else TexelOperation.ALPHA_STORED)
        if (colorAlpha.channelOrder == ImageChannelOrderV1.ALPHA) add(TexelOperation.RETURN_SCALAR_MASK)
        else {
            add(TexelOperation.ZERO_ALPHA_GUARD)
            if (colorAlpha.alphaType == org.graphiks.kanvas.render.ir.ImageAlphaType.PREMUL) add(TexelOperation.UNPREMULTIPLY_SOURCE)
            if (colorAlpha.transfer == ImageTransferPlanV1.SRGB) add(TexelOperation.SRGB_TO_LINEAR)
            if (colorAlpha.gamut == ImageGamutPlanV1.DISPLAY_P3) add(TexelOperation.DISPLAY_P3_TO_LINEAR_SRGB)
            add(TexelOperation.PREMULTIPLY_LINEAR)
            add(when (sampling) {
                ImageSamplingPlanV1.Nearest -> TexelOperation.ACCUMULATE_NEAREST
                ImageSamplingPlanV1.Linear -> TexelOperation.ACCUMULATE_LINEAR
                is ImageSamplingPlanV1.Cubic -> TexelOperation.ACCUMULATE_CUBIC_ROW_MAJOR
            })
            add(TexelOperation.PAINT_OPACITY)
        }
    }
    init {
        fun uniform(indexI32: Int) = Node(Operation.UNIFORM_F32, indexI32)
        fun binary(op: Operation, a: Node, b: Node) = Node(op, inputs = listOf(a, b))
        val x = Node(Operation.DEVICE_X_F32)
        val y = Node(Operation.DEVICE_Y_F32)
        fun row(indexI32: Int) = binary(Operation.ADD_F32,
            binary(Operation.ADD_F32, binary(Operation.MUL_F32, uniform(indexI32), x),
                binary(Operation.MUL_F32, uniform(indexI32 + 1), y)), uniform(indexI32 + 2))
        denominator = row(8)
        localXF32 = binary(Operation.DIV_F32, row(0), denominator)
        localYF32 = binary(Operation.DIV_F32, row(4), denominator)
        fun unit(local: Node, axisI32: Int): Node {
            val relative = binary(Operation.SUB_F32, local, uniform(16 + axisI32))
            return binary(Operation.DIV_F32, relative, uniform(18 + axisI32))
        }
        unitXF32 = unit(localXF32, 0)
        unitYF32 = unit(localYF32, 1)
        fun source(unit: Node, axisI32: Int): Node {
            return binary(Operation.ADD_F32, uniform(12 + axisI32),
                binary(Operation.MUL_F32, unit, uniform(14 + axisI32)))
        }
        sourceX = source(unitXF32, 0)
        sourceY = source(unitYF32, 1)
        val half = Node(Operation.CONSTANT_HALF_F32)
        val one = Node(Operation.CONSTANT_ONE_F32)
        tapXF32 = if (sampling == ImageSamplingPlanV1.Nearest) sourceX else binary(Operation.SUB_F32, sourceX, half)
        tapYF32 = if (sampling == ImageSamplingPlanV1.Nearest) sourceY else binary(Operation.SUB_F32, sourceY, half)
        baseXF32 = Node(Operation.FLOOR_F32, inputs = listOf(tapXF32))
        baseYF32 = Node(Operation.FLOOR_F32, inputs = listOf(tapYF32))
        if (sampling == ImageSamplingPlanV1.Linear) {
            fractionXF32 = binary(Operation.SUB_F32, tapXF32, baseXF32)
            fractionYF32 = binary(Operation.SUB_F32, tapYF32, baseYF32)
            val inverseX = binary(Operation.SUB_F32, one, fractionXF32)
            val inverseY = binary(Operation.SUB_F32, one, fractionYF32)
            weight00F32 = binary(Operation.MUL_F32, inverseX, inverseY)
            weight10F32 = binary(Operation.MUL_F32, fractionXF32, inverseY)
            weight01F32 = binary(Operation.MUL_F32, inverseX, fractionYF32)
            weight11F32 = binary(Operation.MUL_F32, fractionXF32, fractionYF32)
        } else {
            fractionXF32 = null
            fractionYF32 = null
            weight00F32 = null
            weight10F32 = null
            weight01F32 = null
            weight11F32 = null
        }
        if (sampling is ImageSamplingPlanV1.Cubic) {
            cubicKernel = CubicKernelGraph()
            fun weights(tap: Node, base: Node): List<Node> = (-1..2).map { offsetI32 ->
                val index = Node(Operation.TAP_INDEX_F32, offsetI32, listOf(base))
                Node(Operation.CUBIC_KERNEL_F32, inputs = listOf(binary(Operation.SUB_F32, tap, index)))
            }
            val weightsX = weights(tapXF32, baseXF32)
            val weightsY = weights(tapYF32, baseYF32)
            cubicWeightsF32 = immutableList((0 until 4).flatMap { rowI32 -> (0 until 4).map { columnI32 ->
                binary(Operation.MUL_F32, weightsX[columnI32], weightsY[rowI32])
            } })
            val terms = cubicWeightsF32.mapIndexed { tapI32, weight ->
                binary(Operation.MUL_F32, Node(Operation.TEXEL_COMPONENT_F32, tapI32), weight)
            }
            cubicAccumulationF32 = terms.drop(1).fold(terms.first()) { sum, term -> binary(Operation.ADD_F32, sum, term) }
            cubicScheduleIdentity = cubicKernel.scheduleIdentity + ":row-major:" + imageScalarScheduleIdentity(listOf(cubicAccumulationF32))
        } else {
            cubicKernel = null
            cubicWeightsF32 = emptyList()
            cubicAccumulationF32 = null
            cubicScheduleIdentity = null
        }
    }
    internal companion object {
        fun of(colorAlpha: ImageColorAlphaPlanV1, sampling: ImageSamplingPlanV1,
            tileModes: ImageTileModePlanV1): ImageNumericOperationGraphV1 = ImageNumericOperationGraphV1(colorAlpha, sampling, tileModes)
    }
}
