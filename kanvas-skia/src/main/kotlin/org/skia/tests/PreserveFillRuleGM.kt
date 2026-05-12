package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorGREEN
import org.skia.foundation.SK_ColorWHITE
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathFillType
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkRect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port of Skia's `gm/preservefillrule.cpp::PreserveFillRuleGM`.
 *
 * Originally a regression test for the now-removed CCPR (coverage
 * counting path renderer) path-cache : it ensured the cache didn't
 * mix wound-fill and even-odd entries for the same geometry. CCPR
 * is gone but upstream keeps the test as a general assertion that
 * `SkPath::setFillType` propagates through the rendering pipeline.
 *
 * Lays out four star paths in a 2×2 grid :
 *  - top-left : 7-pointed star, winding fill
 *  - top-right : 5-pointed star, winding fill
 *  - bottom-left : 7-pointed star, even-odd fill
 *  - bottom-right : 5-pointed star, even-odd fill
 *
 * Two GM variants — `_big` (200pt stars, 400 × 400 canvas) and
 * `_little` (20pt stars, 40 × 40 canvas).
 *
 * C++ original:
 * ```cpp
 * class PreserveFillRuleGM : public GM {
 * public:
 *     PreserveFillRuleGM(bool big) : fBig(big) , fStarSize((big) ? 200 : 20) {}
 *
 * private:
 *     SkString getName() const override {
 *         SkString name("preservefillrule");
 *         name += (fBig) ? "_big" : "_little";
 *         return name;
 *     }
 *     SkISize getISize() override { return SkISize::Make(fStarSize * 2, fStarSize * 2); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         auto starRect = SkRect::MakeWH(fStarSize, fStarSize);
 *         SkPath star7_winding = ToolUtils::make_star(starRect, 7);
 *         star7_winding.setFillType(SkPathFillType::kWinding);
 *
 *         SkPath star7_evenOdd = star7_winding.makeTransform(SkMatrix::Translate(0, fStarSize))
 *                                             .makeFillType(SkPathFillType::kEvenOdd);
 *
 *         SkPath star5_winding = ToolUtils::make_star(starRect, 5)
 *                                .makeTransform(SkMatrix::Translate(fStarSize, 0))
 *                                .makeFillType(SkPathFillType::kWinding);
 *
 *         SkPath star5_evenOdd = star5_winding.makeTransform(SkMatrix::Translate(0, fStarSize))
 *                                             .makeFillType(SkPathFillType::kEvenOdd);
 *
 *         SkPaint paint;
 *         paint.setColor(SK_ColorGREEN);
 *         paint.setAntiAlias(true);
 *
 *         canvas->clear(SK_ColorWHITE);
 *         canvas->drawPath(star7_winding, paint);
 *         canvas->drawPath(star7_evenOdd, paint);
 *         canvas->drawPath(star5_winding, paint);
 *         canvas->drawPath(star5_evenOdd, paint);
 *     }
 * };
 *
 * DEF_GM( return new PreserveFillRuleGM(true); )
 * DEF_GM( return new PreserveFillRuleGM(false); )
 * ```
 *
 * `:kanvas-skia` carries no `ToolUtils::make_star(rect, numPts)` —
 * we inline the [makeStar] helper, which mirrors `tools/ToolUtils.cpp`
 * line ~270 (build a unit-circle star with `n` points stepping by
 * 2 per vertex, then `RectToRect` the path's bounds onto `bounds`).
 */
public abstract class PreserveFillRuleGM(
    private val fBig: Boolean,
) : GM() {

    private val fStarSize: Int = if (fBig) 200 else 20

    override fun getName(): String = "preservefillrule" + (if (fBig) "_big" else "_little")

    override fun getISize(): SkISize = SkISize.Make(fStarSize * 2, fStarSize * 2)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val starRect = SkRect.MakeWH(fStarSize.toFloat(), fStarSize.toFloat())

        val star7Winding: SkPath = makeStar(starRect, 7)
            .makeFillType(SkPathFillType.kWinding)

        val star7EvenOdd: SkPath = star7Winding
            .makeTransform(SkMatrix.MakeTrans(0f, fStarSize.toFloat()))
            .makeFillType(SkPathFillType.kEvenOdd)

        val star5Winding: SkPath = makeStar(starRect, 5)
            .makeTransform(SkMatrix.MakeTrans(fStarSize.toFloat(), 0f))
            .makeFillType(SkPathFillType.kWinding)

        val star5EvenOdd: SkPath = star5Winding
            .makeTransform(SkMatrix.MakeTrans(0f, fStarSize.toFloat()))
            .makeFillType(SkPathFillType.kEvenOdd)

        val paint = SkPaint().apply {
            color = SK_ColorGREEN
            isAntiAlias = true
        }

        c.clear(SK_ColorWHITE)
        c.drawPath(star7Winding, paint)
        c.drawPath(star7EvenOdd, paint)
        c.drawPath(star5Winding, paint)
        c.drawPath(star5EvenOdd, paint)
    }

    private companion object {
        /**
         * Mirrors `ToolUtils::make_star(bounds, numPts, step = 2)`
         * (`tools/ToolUtils.cpp` ~line 270). Builds a star with `numPts`
         * vertices stepping by `step` around a unit circle (the "step"
         * controls the pointiness — a 5-point star with step = 2 is the
         * classic five-pointed shape, a 7-point star with step = 2 is the
         * heptagram). Then `RectToRect`-maps the path's bounds onto
         * `bounds`. Fill rule defaults to even-odd, as upstream.
         */
        private fun makeStar(bounds: SkRect, numPts: Int): SkPath {
            val step = 2
            val b = SkPathBuilder()
            b.setFillType(SkPathFillType.kEvenOdd)
            b.moveTo(0f, -1f)
            for (i in 1 until numPts) {
                val idx = i * step % numPts
                val theta = idx * 2f * PI.toFloat() / numPts + PI.toFloat() / 2f
                val x = cos(theta)
                val y = -sin(theta)
                b.lineTo(x, y)
            }
            val path = b.detach()
            val xf = SkMatrix.MakeRectToRect(path.computeBounds(), bounds, SkMatrix.ScaleToFit.kFill_ScaleToFit)
                ?: return path
            return path.makeTransform(xf)
        }
    }
}

/** Concrete GM variant — `preservefillrule_big` (200pt stars, 400 × 400). */
public class PreserveFillRuleBigGM : PreserveFillRuleGM(fBig = true)

/** Concrete GM variant — `preservefillrule_little` (20pt stars, 40 × 40). */
public class PreserveFillRuleLittleGM : PreserveFillRuleGM(fBig = false)
