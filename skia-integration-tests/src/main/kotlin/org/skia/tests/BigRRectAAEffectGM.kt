package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * R-final.S — **STUB.GPU_BIG_RRECT_AA_EFFECT** consumer GMs. Port of Skia's
 * `gm/bigrrectaaeffect.cpp` — `BigRRectAAEffectGM`.
 *
 * These GMs (`big_rrect_rect_aa_effect`, `big_rrect_circle_aa_effect`,
 * `big_rrect_ellipse_aa_effect`, `big_rrect_circular_corner_aa_effect`,
 * `big_rrect_elliptical_corner_aa_effect`) are GpuGM variants that drive
 * the Ganesh-internal `GrRRectEffect` fragment processor directly via
 * `SurfaceDrawContext`, `GrClipEdgeType`, `GrPaint`, `GrPorterDuffXPFactory`,
 * and `skgpu::ganesh::FillRectOp::MakeNonAARect` — Ganesh-private APIs that
 * do not exist in our WebGPU / CPU-raster pipeline.
 *
 * Motivated by chromium:477684: the large rrect size (700 px) was chosen to
 * trigger integer overflow in the GPU shader without the fix.
 *
 * The body throws [TODO] tagged **STUB.GPU_BIG_RRECT_AA_EFFECT** so the
 * compile contract holds; [BigRRectAAEffectTest] is `@Disabled`.
 *
 * See upstream `gm/bigrrectaaeffect.cpp`.
 */

private const val kSize = 700
private const val kPad = 7
private const val kGap = 3

private fun computeWidth(rrectWidth: Int): Int {
    val testWidth = rrectWidth + 2 * kGap
    val testOffsetX = testWidth + kPad
    return 2 * testOffsetX + kPad
}

private fun computeHeight(rrectHeight: Int): Int {
    val testHeight = rrectHeight + 2 * kGap
    val testOffsetY = testHeight + kPad
    return testOffsetY + kPad
}

/**
 * `big_rrect_rect_aa_effect` — SkRRect::MakeRect(700×700).
 */
public class BigRRectRectAAEffectGM : GM() {

    override fun getName(): String = "big_rrect_rect_aa_effect"
    override fun getISize(): SkISize = SkISize.Make(
        computeWidth(kSize),
        computeHeight(kSize),
    )

    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.GPU_BIG_RRECT_AA_EFFECT")
    }
}

/**
 * `big_rrect_circle_aa_effect` — SkRRect::MakeOval(700×700).
 */
public class BigRRectCircleAAEffectGM : GM() {

    override fun getName(): String = "big_rrect_circle_aa_effect"
    override fun getISize(): SkISize = SkISize.Make(
        computeWidth(kSize),
        computeHeight(kSize),
    )

    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.GPU_BIG_RRECT_AA_EFFECT")
    }
}

/**
 * `big_rrect_ellipse_aa_effect` — SkRRect::MakeOval(699×690).
 */
public class BigRRectEllipseAAEffectGM : GM() {

    override fun getName(): String = "big_rrect_ellipse_aa_effect"
    override fun getISize(): SkISize = SkISize.Make(
        computeWidth(kSize - 1),
        computeHeight(kSize - 10),
    )

    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.GPU_BIG_RRECT_AA_EFFECT")
    }
}

/**
 * `big_rrect_circular_corner_aa_effect` — SkRRect::MakeRectXY(699×690, rx=340, ry=340).
 * Small linear segments between the corners.
 */
public class BigRRectCircularCornerAAEffectGM : GM() {

    override fun getName(): String = "big_rrect_circular_corner_aa_effect"
    override fun getISize(): SkISize = SkISize.Make(
        computeWidth(kSize - 1),
        computeHeight(kSize - 10),
    )

    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.GPU_BIG_RRECT_AA_EFFECT")
    }
}

/**
 * `big_rrect_elliptical_corner_aa_effect` — SkRRect::MakeRectXY(699×690, rx=340, ry=335).
 * Small linear segments between the corners.
 */
public class BigRRectEllipticalCornerAAEffectGM : GM() {

    override fun getName(): String = "big_rrect_elliptical_corner_aa_effect"
    override fun getISize(): SkISize = SkISize.Make(
        computeWidth(kSize - 1),
        computeHeight(kSize - 10),
    )

    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.GPU_BIG_RRECT_AA_EFFECT")
    }
}
