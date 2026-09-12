package org.graphiks.kanvas.gpu.plan

public object W5dPlanDiagnostics {
    public const val LocalMatrixNonFinite: String = "unsupported.material.gradient.local-matrix-non-finite"
    public const val LocalMatrixSingular: String = "unsupported.material.gradient.local-matrix-singular"
    public const val LocalMatrixUnrepresentable: String = "unsupported.material.gradient.local-matrix-unrepresentable"
    public const val CoordClampNonFinite: String = "unsupported.material.gradient.coord-clamp-non-finite"
    public const val CoordClampUnsorted: String = "unsupported.material.gradient.coord-clamp-unsorted"
    public const val CoordinateUniformBudget: String = "resource.material.gradient.coordinate-uniform-budget"
    public const val CoordinatePlanSchema: String = "schema.material.gradient.coordinate-plan"
}
