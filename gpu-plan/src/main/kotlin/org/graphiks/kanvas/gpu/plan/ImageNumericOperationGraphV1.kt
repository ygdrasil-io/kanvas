package org.graphiks.kanvas.gpu.plan

/** Executed scalar schedule and selected tap/address topology. */
public class ImageNumericOperationGraphV1 private constructor(public val colorAlpha: ImageColorAlphaPlanV1,
    public val sampling: ImageSamplingPlanV1, public val tileModes: ImageTileModePlanV1) {
    public enum class Operation { DEVICE_X_F32, DEVICE_Y_F32, UNIFORM_F32, CONSTANT_HALF_F32, CONSTANT_ONE_F32,
        ADD_F32, SUB_F32, MUL_F32, DIV_F32, FLOOR_F32 }
    public class Node internal constructor(public val operation: Operation, public val uniformIndexI32: Int = -1,
        inputs: List<Node> = emptyList()) {
        public val inputs: List<Node> = immutableList(inputs)
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
        texelOperations().joinToString(",") { it.name }
    public val denominator: Node
    public val sourceX: Node
    public val sourceY: Node
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
        fun source(rowI32: Int, axisI32: Int): Node {
            val local = binary(Operation.DIV_F32, row(rowI32), denominator)
            val relative = binary(Operation.SUB_F32, local, uniform(16 + axisI32))
            val unit = binary(Operation.DIV_F32, relative, uniform(18 + axisI32))
            return binary(Operation.ADD_F32, uniform(12 + axisI32),
                binary(Operation.MUL_F32, unit, uniform(14 + axisI32)))
        }
        sourceX = source(0, 0)
        sourceY = source(4, 1)
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
    }
    internal companion object {
        fun of(colorAlpha: ImageColorAlphaPlanV1, sampling: ImageSamplingPlanV1,
            tileModes: ImageTileModePlanV1): ImageNumericOperationGraphV1 = ImageNumericOperationGraphV1(colorAlpha, sampling, tileModes)
    }
}
