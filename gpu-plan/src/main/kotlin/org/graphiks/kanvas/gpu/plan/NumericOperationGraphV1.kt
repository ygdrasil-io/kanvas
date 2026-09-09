package org.graphiks.kanvas.gpu.plan

/**
 * Backend-neutral finite operation graph carried by every W5a material program.
 * The renderer emits WGSL from this graph; public tests evaluate an independent
 * envelope rather than relying on host-Float equality.
 */
public sealed interface NumericOperationGraphV1 {
    public val contractId: String get() = "WgslFloatEnvelopeV1"

    public data object Transparent : NumericOperationGraphV1
    public data object SolidSrgbToLinearPremul : NumericOperationGraphV1
    public data class Opacity(public val child: NumericOperationGraphV1) : NumericOperationGraphV1
}
