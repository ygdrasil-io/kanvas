package org.graphiks.kanvas.gpu.plan

/**
 * Backend-neutral finite operation graph carried by every W5a material program.
 * The renderer emits WGSL from this graph; public tests evaluate an independent
 * envelope rather than relying on host-Float equality.
 */
public sealed interface NumericOperationGraphV1 {
    public val contractId: String get() = "WgslFloatEnvelopeV1"

    /** Typed F32 DAG nodes; each operation names the exact WGSL primitive it requires. */
    public enum class Operation {
        INPUT_SRGB_RGBA,
        CONSTANT_TRANSPARENT,
        SRGB_TO_LINEAR,
        PREMULTIPLY,
        OPACITY_F32,
        COVERAGE_F32,
        SRC_OVER,
        CLAMP_01,
        QUANTIZE_UNORM8,
    }

    public data class Node(
        public val operation: Operation,
        public val inputs: List<Node> = emptyList(),
    ) : NumericOperationGraphV1 {
        init {
            require(inputs.size == arity(operation)) { "Numeric operation arity is invalid" }
        }
        public companion object {
            private fun arity(operation: Operation): Int = when (operation) {
                Operation.INPUT_SRGB_RGBA, Operation.CONSTANT_TRANSPARENT -> 0
                Operation.SRGB_TO_LINEAR, Operation.PREMULTIPLY, Operation.OPACITY_F32,
                Operation.COVERAGE_F32, Operation.CLAMP_01, Operation.QUANTIZE_UNORM8 -> 1
                Operation.SRC_OVER -> 2
            }
        }
    }

    public data object Transparent : NumericOperationGraphV1
    public data object SolidSrgbToLinearPremul : NumericOperationGraphV1
    public data class Opacity(public val child: NumericOperationGraphV1) : NumericOperationGraphV1
}
