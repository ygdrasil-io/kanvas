package org.graphiks.kanvas.gpu.plan

/** Executed scalar schedule for Nearest; texel addressing follows the bounded I32 conversion. */
public class ImageNumericOperationGraphV1 private constructor() {
    public enum class Operation { DEVICE_X_F32, DEVICE_Y_F32, UNIFORM_F32, ADD_F32, SUB_F32, MUL_F32, DIV_F32 }
    public class Node internal constructor(public val operation: Operation, public val uniformIndexI32: Int = -1,
        inputs: List<Node> = emptyList()) {
        public val inputs: List<Node> = immutableList(inputs)
    }
    public enum class TexelOperation { PROJECTIVE_VALIDITY_MASK, PIXEL_CENTER_NEAREST, FLOOR_F32,
        CONVERT_I32, ZERO_TAP_OFFSET_I32, CLAMP_X_I32, CLAMP_Y_I32, LOAD_RGBA8,
        UNPREMULTIPLY_SOURCE, SRGB_TO_LINEAR, PREMULTIPLY_LINEAR, ACCUMULATE_NEAREST }
    public val contractId: String = "WgslFloatEnvelopeV1"
    public val topologyIdentity: String = "w5e-image-numeric-v1:inverse-project-divide-map-nearest-clamp-rgba-premul-srgb"
    public val denominator: Node
    public val sourceX: Node
    public val sourceY: Node
    public fun texelOperations(): List<TexelOperation> = TexelOperation.entries.toList()
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
    public companion object { public fun nearest(): ImageNumericOperationGraphV1 = ImageNumericOperationGraphV1() }
}
