package org.graphiks.kanvas.gpu.plan

/** Stable, backend-neutral refusal codes for W5a material planning. */
public object W5aPlanDiagnostics {
    public const val UnsupportedMaterial: String = "unsupported.material.w5a.kind"
    public const val UnsupportedDrawState: String = "unsupported.material.w5a.draw_state"
    public const val InvalidOpacity: String = "invalid.material.w5a.opacity"
    /** A public plan table carried a non-finite Solid or Opacity binding. */
    public const val NonFiniteBinding: String = "invalid.material.w5a.non_finite_binding"
}
