package org.skia.tests

/**
 * Alias for [ConicalGradients2ptInsideDitherGM].
 *
 * The upstream class `gm/gradients_2pt_conical.cpp::ConicalGradientsGM`
 * is a parameterised template over (GradCaseType × dither × tileMode)
 * that ships 12 `DEF_GM` registrations. The first registration —
 * `DEF_GM(return new ConicalGradientsGM(kInside_GradCaseType, true))`
 * — produces the GM named `gradients_2pt_conical_inside`, which is
 * already ported as [ConicalGradients2ptInsideDitherGM] (kInside +
 * dither + kClamp tileMode).
 *
 * The remaining 11 instantiations are also already ported as
 * siblings — see the [ConicalGradients2ptVariantGM] family in
 * `ConicalGradients2ptInsideGM.kt`, `ConicalGradients2ptOutsideGM.kt`,
 * and `ConicalGradients2ptTileModeGMs.kt`.
 *
 * Keeping this `typealias` so tooling that looks up the GM class
 * under its longer upstream C++ identifier resolves cleanly.
 */
public typealias ConicalGradientsGM = ConicalGradients2ptInsideDitherGM
