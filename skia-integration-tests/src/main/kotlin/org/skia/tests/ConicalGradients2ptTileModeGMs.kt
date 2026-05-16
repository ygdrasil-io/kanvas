package org.skia.tests

import org.skia.foundation.SkConicalGradient
import org.skia.foundation.SkShader
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkPoint

/**
 * Additional `gm/gradients_2pt_conical.cpp::ConicalGradientsGM` variants
 * picking up the dither-on / tile-mode permutations not covered by the
 * existing [ConicalGradients2ptOutsideGM] /
 * [ConicalGradients2ptOutsideNoDitherGM] /
 * [ConicalGradients2ptEdgeGM] /
 * [ConicalGradients2ptEdgeNoDitherGM] /
 * [ConicalGradients2ptInsideGM] family.
 *
 * Specifically, this file ports :
 *  - `gradients_2pt_conical_inside` (dither on, kClamp)
 *  - `gradients_2pt_conical_{inside,outside,edge}_repeat` (kRepeat)
 *  - `gradients_2pt_conical_{inside,outside,edge}_mirror` (kMirror)
 *
 * The 7 inside-case maker functions are duplicated locally — the
 * existing [ConicalGradients2ptInsideGM] keeps them as `private fun`s
 * so they're not reusable. We mirror them here verbatim.
 */

private fun midpoint(a: Float, b: Float): Float = (a + b) * 0.5f
private fun interp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

private fun make2ConicalInside(pts: Array<SkPoint>, data: ConicalGrad2ptData, tm: SkTileMode, lm: SkMatrix): SkShader? {
    val center0 = SkPoint(midpoint(pts[0].fX, pts[1].fX), midpoint(pts[0].fY, pts[1].fY))
    val center1 = SkPoint(interp(pts[0].fX, pts[1].fX, 3f / 5f), interp(pts[0].fY, pts[1].fY, 1f / 4f))
    return SkConicalGradient.Make(
        center1, (pts[1].fX - pts[0].fX) / 7f,
        center0, (pts[1].fX - pts[0].fX) / 2f,
        data.colors, data.positions, tm, lm,
    )
}

private fun make2ConicalInsideFlip(pts: Array<SkPoint>, data: ConicalGrad2ptData, tm: SkTileMode, lm: SkMatrix): SkShader? {
    val center0 = SkPoint(midpoint(pts[0].fX, pts[1].fX), midpoint(pts[0].fY, pts[1].fY))
    val center1 = SkPoint(interp(pts[0].fX, pts[1].fX, 3f / 5f), interp(pts[0].fY, pts[1].fY, 1f / 4f))
    return SkConicalGradient.Make(
        center0, (pts[1].fX - pts[0].fX) / 2f,
        center1, (pts[1].fX - pts[0].fX) / 7f,
        data.colors, data.positions, tm, lm,
    )
}

private fun make2ConicalInsideCenter(pts: Array<SkPoint>, data: ConicalGrad2ptData, tm: SkTileMode, lm: SkMatrix): SkShader? {
    val c0 = SkPoint(midpoint(pts[0].fX, pts[1].fX), midpoint(pts[0].fY, pts[1].fY))
    return SkConicalGradient.Make(
        c0, (pts[1].fX - pts[0].fX) / 7f,
        c0, (pts[1].fX - pts[0].fX) / 2f,
        data.colors, data.positions, tm, lm,
    )
}

private fun make2ConicalInsideCenterReversed(pts: Array<SkPoint>, data: ConicalGrad2ptData, tm: SkTileMode, lm: SkMatrix): SkShader? {
    val c0 = SkPoint(midpoint(pts[0].fX, pts[1].fX), midpoint(pts[0].fY, pts[1].fY))
    return SkConicalGradient.Make(
        c0, (pts[1].fX - pts[0].fX) / 2f,
        c0, (pts[1].fX - pts[0].fX) / 7f,
        data.colors, data.positions, tm, lm,
    )
}

private fun make2ConicalZeroRad(pts: Array<SkPoint>, data: ConicalGrad2ptData, tm: SkTileMode, lm: SkMatrix): SkShader? {
    val center0 = SkPoint(midpoint(pts[0].fX, pts[1].fX), midpoint(pts[0].fY, pts[1].fY))
    val center1 = SkPoint(interp(pts[0].fX, pts[1].fX, 3f / 5f), interp(pts[0].fY, pts[1].fY, 1f / 4f))
    return SkConicalGradient.Make(
        center1, 0f,
        center0, (pts[1].fX - pts[0].fX) / 2f,
        data.colors, data.positions, tm, lm,
    )
}

private fun make2ConicalZeroRadFlip(pts: Array<SkPoint>, data: ConicalGrad2ptData, tm: SkTileMode, lm: SkMatrix): SkShader? {
    val center0 = SkPoint(midpoint(pts[0].fX, pts[1].fX), midpoint(pts[0].fY, pts[1].fY))
    val center1 = SkPoint(interp(pts[0].fX, pts[1].fX, 3f / 5f), interp(pts[0].fY, pts[1].fY, 1f / 4f))
    return SkConicalGradient.Make(
        center1, (pts[1].fX - pts[0].fX) / 2f,
        center0, 0f,
        data.colors, data.positions, tm, lm,
    )
}

private fun make2ConicalZeroRadCenter(pts: Array<SkPoint>, data: ConicalGrad2ptData, tm: SkTileMode, lm: SkMatrix): SkShader? {
    val c0 = SkPoint(midpoint(pts[0].fX, pts[1].fX), midpoint(pts[0].fY, pts[1].fY))
    return SkConicalGradient.Make(
        c0, 0f,
        c0, (pts[1].fX - pts[0].fX) / 2f,
        data.colors, data.positions, tm, lm,
    )
}

internal val INSIDE_MAKERS_LOCAL: List<(Array<SkPoint>, ConicalGrad2ptData, SkTileMode, SkMatrix) -> SkShader?> = listOf(
    ::make2ConicalInside,
    ::make2ConicalInsideFlip,
    ::make2ConicalInsideCenter,
    ::make2ConicalZeroRad,
    ::make2ConicalZeroRadFlip,
    ::make2ConicalZeroRadCenter,
    ::make2ConicalInsideCenterReversed,
)

// ─── Inside-case GMs (dither-on default / repeat / mirror) ──────────

/** `gradients_2pt_conical_inside` — dither on, kClamp. */
public class ConicalGradients2ptInsideDitherGM : ConicalGradients2ptVariantGM(
    gmName = "gradients_2pt_conical_inside",
    makers = INSIDE_MAKERS_LOCAL,
    dither = true,
)

/** `gradients_2pt_conical_inside_repeat` — dither on, kRepeat. */
public class ConicalGradients2ptInsideRepeatGM : ConicalGradients2ptVariantGM(
    gmName = "gradients_2pt_conical_inside_repeat",
    makers = INSIDE_MAKERS_LOCAL,
    dither = true,
    tileMode = SkTileMode.kRepeat,
)

/** `gradients_2pt_conical_inside_mirror` — dither on, kMirror. */
public class ConicalGradients2ptInsideMirrorGM : ConicalGradients2ptVariantGM(
    gmName = "gradients_2pt_conical_inside_mirror",
    makers = INSIDE_MAKERS_LOCAL,
    dither = true,
    tileMode = SkTileMode.kMirror,
)

// ─── Outside-case GMs (repeat / mirror) ─────────────────────────────

/** `gradients_2pt_conical_outside_repeat` — dither on, kRepeat. */
public class ConicalGradients2ptOutsideRepeatGM : ConicalGradients2ptVariantGM(
    gmName = "gradients_2pt_conical_outside_repeat",
    makers = OUTSIDE_MAKERS,
    dither = true,
    tileMode = SkTileMode.kRepeat,
)

/** `gradients_2pt_conical_outside_mirror` — dither on, kMirror. */
public class ConicalGradients2ptOutsideMirrorGM : ConicalGradients2ptVariantGM(
    gmName = "gradients_2pt_conical_outside_mirror",
    makers = OUTSIDE_MAKERS,
    dither = true,
    tileMode = SkTileMode.kMirror,
)

// ─── Edge-case GMs (repeat / mirror) ────────────────────────────────

/** `gradients_2pt_conical_edge_repeat` — dither on, kRepeat. */
public class ConicalGradients2ptEdgeRepeatGM : ConicalGradients2ptVariantGM(
    gmName = "gradients_2pt_conical_edge_repeat",
    makers = EDGE_MAKERS,
    dither = true,
    tileMode = SkTileMode.kRepeat,
)

/** `gradients_2pt_conical_edge_mirror` — dither on, kMirror. */
public class ConicalGradients2ptEdgeMirrorGM : ConicalGradients2ptVariantGM(
    gmName = "gradients_2pt_conical_edge_mirror",
    makers = EDGE_MAKERS,
    dither = true,
    tileMode = SkTileMode.kMirror,
)
