package org.graphiks.kanvas.gpu.plan

/** Stable image-specific refusal families; ownership is independent of native realization. */
public object W5eImagePlanDiagnostics {
    public const val ExternalResource: String = "unsupported.material.image.external_resource"
    public const val UnsupportedSlice: String = "unsupported.material.image.slice"
    public const val InvalidLayout: String = "unsupported.material.image.layout"
    public const val NumericDomainUnbounded: String = "unsupported.material.image.numeric-domain-unbounded"
    public const val FrameBudget: String = "resource.material.image.frame-budget"
    public const val Capability: String = "unsupported.material.image.capability"
    public const val InvalidContract: String = "invalid.material.image.contract"
}
