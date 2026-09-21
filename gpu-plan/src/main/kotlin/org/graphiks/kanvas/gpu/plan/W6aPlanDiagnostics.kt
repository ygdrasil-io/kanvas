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
    public const val RestoreCapability: String = "w6a.layer.restore_capability"
    /** The frozen complete-frame physical allocation peak exceeds the admitted W6a budget. */
    public const val FrameBudgetExceeded: String = "w6a.layer.frame_budget_exceeded"
    /** Checked construction arithmetic overflowed before a physical peak could be formed. */
    public const val FrameConstructionOverflow: String = "resource.w6a.layer.frame-construction-overflow"
    /** Captured W6a scope stack exceeded its immutable GraphLimits depth. */
    public const val DepthLimit: String = "w6a.layer.depth_limit"
    /** Captured W6a command sequence exceeded its immutable GraphLimits node bound. */
    public const val CommandLimit: String = "w6a.layer.command_limit"
    public const val UnsupportedChild: String = "w6a.layer.unsupported_child"
}
