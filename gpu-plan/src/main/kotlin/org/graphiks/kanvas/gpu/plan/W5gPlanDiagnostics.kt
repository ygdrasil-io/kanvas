package org.graphiks.kanvas.gpu.plan

public object W5gPlanDiagnostics {
    public const val Schema: String = "invalid.material.composed.schema"
    public const val NumericDomainUnbounded: String = "unsupported.material.composed.numeric-domain-unbounded"
    public const val Binding: String = "resource-limit.w5g.composed-binding"
    public const val Uniform: String = "budget.w5g.composed-uniform"
    public const val Storage: String = "budget.material.composed.storage"
    public const val Unpromoted: String = "unsupported.material.composed.slice"
    public const val NoiseParameters: String = "invalid.material.noise.parameters"
    public const val NoiseTile: String = "invalid.material.noise.tile"
    public const val NoiseUnpromoted: String = "unsupported.material.noise.slice"
    public const val NoiseNumericDomainUnbounded: String = "unsupported.material.noise.numeric-domain-unbounded"
    public const val NoiseWork: String = "budget.material.noise.octave-evaluations"
    public const val NoiseStorage: String = "budget.material.noise.storage"
}
