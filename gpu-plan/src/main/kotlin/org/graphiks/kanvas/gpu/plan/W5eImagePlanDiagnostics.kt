package org.graphiks.kanvas.gpu.plan

/** Stable image-specific refusal families; ownership is independent of native realization. */
public object W5eImagePlanDiagnostics {
    public const val ExternalResource: String = "unsupported.material.image.external_resource"
    public const val Format: String = "unsupported.material.image.format"
    public const val ColorSpace: String = "unsupported.material.image.color-space"
    public const val Alpha: String = "unsupported.material.image.alpha"
    public const val Dimensions: String = "unsupported.material.image.dimensions"
    public const val Stride: String = "unsupported.material.image.stride"
    public const val Payload: String = "unsupported.material.image.payload"
    public const val Overflow: String = "unsupported.material.image.overflow"
    public const val TextureLimit: String = "unsupported.material.image.texture-limit"
    public const val BindingLimit: String = "unsupported.material.image.binding-limit"
    public const val UnsupportedSlice: String = "unsupported.material.image.slice"
    public const val InvalidLayout: String = "unsupported.material.image.layout"
    public const val NumericDomainUnbounded: String = "unsupported.material.image.numeric-domain-unbounded"
    public const val FrameBudget: String = "resource.material.image.frame-budget"
    public const val Capability: String = "unsupported.material.image.capability"
    public const val InvalidContract: String = "invalid.material.image.contract"
}
