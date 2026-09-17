package org.graphiks.kanvas.gpu.plan

/** Stable W6a refusal vocabulary: owned layers never resume a legacy route. */
public object W6aPlanDiagnostics {
    public const val UnsupportedBackdrop: String = "w6a.layer.unsupported_backdrop"
    public const val UnsupportedSpatialFilter: String = "w6a.layer.unsupported_spatial_filter"
    public const val UnsupportedTargetFormat: String = "w6a.layer.unsupported_target_format"
    public const val MalformedStack: String = "w6a.layer.malformed_stack"
    public const val NonFiniteTransform: String = "w6a.layer.non_finite_transform"
    public const val MappingHorizon: String = "w6a.layer.mapping_horizon"
    public const val MappingOverflow: String = "w6a.layer.mapping_overflow"
    public const val UnsupportedRestore: String = "w6a.layer.unsupported_restore"
    public const val UnsupportedNestedScope: String = "w6a.layer.unsupported_nested_scope"
    public const val UnsupportedChild: String = "w6a.layer.unsupported_child"
}
