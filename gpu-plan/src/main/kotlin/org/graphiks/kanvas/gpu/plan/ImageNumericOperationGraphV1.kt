package org.graphiks.kanvas.gpu.plan

/** Executed scalar schedule for Nearest; texel addressing follows the bounded I32 conversion. */
public class ImageNumericOperationGraphV1 private constructor(public val colorAlpha: ImageColorAlphaPlanV1) {
    public enum class Operation { DEVICE_X_F32, DEVICE_Y_F32, UNIFORM_F32, ADD_F32, SUB_F32, MUL_F32, DIV_F32 }
    public class Node internal constructor(public val operation: Operation, public val uniformIndexI32: Int = -1,
        inputs: List<Node> = emptyList()) {
        public val inputs: List<Node> = immutableList(inputs)
    }
    public enum class TexelOperation { PROJECTIVE_VALIDITY_MASK, PIXEL_CENTER_NEAREST, FLOOR_F32,
        CONVERT_I32, ZERO_TAP_OFFSET_I32, CLAMP_X_I32, CLAMP_Y_I32, LOAD_UNORM8,
        SWIZZLE_BGRA, ALPHA_OPAQUE, ALPHA_STORED, ZERO_ALPHA_GUARD,
        UNPREMULTIPLY_SOURCE, SRGB_TO_LINEAR, DISPLAY_P3_TO_LINEAR_SRGB,
        PREMULTIPLY_LINEAR, RETURN_SCALAR_MASK, ACCUMULATE_NEAREST, PAINT_OPACITY }
    public val contractId: String = "WgslFloatEnvelopeV1"
    public val topologyIdentity: String = "w5e-image-numeric-v1:inverse-project-divide-map-nearest-clamp:$colorAlpha:" +
        texelOperations().joinToString(",") { it.name }
    public val denominator: Node
    public val sourceX: Node
    public val sourceY: Node
    public fun texelOperations(): List<TexelOperation> = buildList {
        addAll(listOf(TexelOperation.PROJECTIVE_VALIDITY_MASK, TexelOperation.PIXEL_CENTER_NEAREST,
            TexelOperation.FLOOR_F32, TexelOperation.CONVERT_I32, TexelOperation.ZERO_TAP_OFFSET_I32,
            TexelOperation.CLAMP_X_I32, TexelOperation.CLAMP_Y_I32, TexelOperation.LOAD_UNORM8))
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
            add(TexelOperation.ACCUMULATE_NEAREST)
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
        fun source(rowI32: Int, axisI32: Int): Node {
            val local = binary(Operation.DIV_F32, row(rowI32), denominator)
            val relative = binary(Operation.SUB_F32, local, uniform(16 + axisI32))
            val unit = binary(Operation.DIV_F32, relative, uniform(18 + axisI32))
            return binary(Operation.ADD_F32, uniform(12 + axisI32),
                binary(Operation.MUL_F32, unit, uniform(14 + axisI32)))
        }
        sourceX = source(0, 0)
        sourceY = source(4, 1)
    }
    internal companion object { fun nearest(colorAlpha: ImageColorAlphaPlanV1): ImageNumericOperationGraphV1 = ImageNumericOperationGraphV1(colorAlpha) }
}
