package org.graphiks.kanvas.gpu.plan

/**
 * Backend-neutral F32 DAG for one W5a pixel. A program has one typed output:
 * RGBA8 written to the sRGB attachment. Its source, destination, and coverage
 * inputs stay explicit, so blend or coverage cannot silently be treated as a
 * source-only material calculation.
 */
public sealed interface NumericOperationGraphV1 {
    public val root: Node
    public val contractId: String get() = "WgslFloatEnvelopeV1"

    public enum class ValueType {
        SrgbaStraightF32,
        LinearStraightRgbaF32,
        LinearPremulRgbaF32,
        CoverageF32,
        AttachmentSrgbaPremulF32,
        AttachmentRgba8,
    }

    /** Every operation names both its result and its exact typed inputs. */
    public enum class Operation(
        public val output: ValueType,
        public vararg val inputs: ValueType,
    ) {
        INPUT_SOLID_SRGBA_STRAIGHT(ValueType.SrgbaStraightF32),
        INPUT_GRADIENT_SRGBA_STRAIGHT(ValueType.SrgbaStraightF32),
        INPUT_MATERIAL_LINEAR_PREMUL(ValueType.LinearPremulRgbaF32),
        INPUT_IMAGE_LINEAR_PREMUL(ValueType.LinearPremulRgbaF32),
        INPUT_IMAGE_MASK_F32(ValueType.CoverageF32),
        IMAGE_MASK_MULTIPLY(ValueType.LinearPremulRgbaF32, ValueType.LinearPremulRgbaF32, ValueType.CoverageF32),
        INPUT_DESTINATION_LINEAR_PREMUL(ValueType.LinearPremulRgbaF32),
        INPUT_COVERAGE_F32(ValueType.CoverageF32),
        CONSTANT_TRANSPARENT(ValueType.LinearPremulRgbaF32),
        SRGB_TO_LINEAR(ValueType.LinearStraightRgbaF32, ValueType.SrgbaStraightF32),
        PREMULTIPLY(ValueType.LinearPremulRgbaF32, ValueType.LinearStraightRgbaF32),
        OPACITY_F32(ValueType.LinearPremulRgbaF32, ValueType.LinearPremulRgbaF32),
        SRC_OVER(
            ValueType.LinearPremulRgbaF32,
            ValueType.LinearPremulRgbaF32,
            ValueType.LinearPremulRgbaF32,
        ),
        APPLY_COVERAGE_F32(
            ValueType.LinearPremulRgbaF32,
            ValueType.LinearPremulRgbaF32,
            ValueType.LinearPremulRgbaF32,
            ValueType.CoverageF32,
        ),
        LINEAR_TO_SRGB_ATTACHMENT(
            ValueType.AttachmentSrgbaPremulF32,
            ValueType.LinearPremulRgbaF32,
        ),
        CLAMP_01(
            ValueType.AttachmentSrgbaPremulF32,
            ValueType.AttachmentSrgbaPremulF32,
        ),
        QUANTIZE_UNORM8(ValueType.AttachmentRgba8, ValueType.AttachmentSrgbaPremulF32),
    }

    public data class Node(
        public val operation: Operation,
        public val inputs: List<Node> = emptyList(),
    ) {
        public val type: ValueType get() = operation.output

        init {
            require(inputs.size == operation.inputs.size) { "Numeric operation arity is invalid" }
            require(inputs.map(Node::type) == operation.inputs.toList()) {
                "Numeric operation input types are invalid"
            }
        }
    }

    public data class Program(override val root: Node) : NumericOperationGraphV1 {
        init {
            require(root.type == ValueType.AttachmentRgba8) {
                "A W5a numeric graph must terminate in RGBA8 attachment output"
            }
        }
    }

    public companion object {
        /** Full source-to-attachment graph for a transparent W5a material. */
        public fun transparent(): NumericOperationGraphV1 = output(Node(Operation.CONSTANT_TRANSPARENT))

        /** Full source-to-attachment graph for a Solid material. */
        public fun solid(): NumericOperationGraphV1 = output(
            Node(
                Operation.PREMULTIPLY,
                listOf(Node(Operation.SRGB_TO_LINEAR, listOf(Node(Operation.INPUT_SOLID_SRGBA_STRAIGHT)))),
            ),
        )

        public fun gradient(): NumericOperationGraphV1 = output(Node(Operation.PREMULTIPLY,
            listOf(Node(Operation.SRGB_TO_LINEAR, listOf(Node(Operation.INPUT_GRADIENT_SRGBA_STRAIGHT))))))

        public fun imageColor(): NumericOperationGraphV1 = output(Node(Operation.INPUT_IMAGE_LINEAR_PREMUL))
        public fun colorSourceV4(): NumericOperationGraphV1 = output(Node(Operation.INPUT_MATERIAL_LINEAR_PREMUL))
        public fun imageMask(): NumericOperationGraphV1 = output(Node(Operation.IMAGE_MASK_MULTIPLY,
            listOf(Node(Operation.INPUT_MATERIAL_LINEAR_PREMUL), Node(Operation.INPUT_IMAGE_MASK_F32))))

        /** Full source-to-attachment graph for an opacity wrapper. */
        public fun opacity(): NumericOperationGraphV1 = output(
            Node(Operation.OPACITY_F32, listOf(Node(Operation.INPUT_MATERIAL_LINEAR_PREMUL))),
        )

        private fun output(source: Node): NumericOperationGraphV1 {
            val destination = Node(Operation.INPUT_DESTINATION_LINEAR_PREMUL)
            val coverage = Node(Operation.INPUT_COVERAGE_F32)
            val blended = Node(Operation.SRC_OVER, listOf(source, destination))
            val covered = Node(Operation.APPLY_COVERAGE_F32, listOf(destination, blended, coverage))
            val encoded = Node(Operation.LINEAR_TO_SRGB_ATTACHMENT, listOf(covered))
            val clamped = Node(Operation.CLAMP_01, listOf(encoded))
            return Program(Node(Operation.QUANTIZE_UNORM8, listOf(clamped)))
        }
    }
}
