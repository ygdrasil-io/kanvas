package org.skia.effects

import kotlin.Boolean
import kotlin.Char
import org.skia.core.SkPathBuilder
import org.skia.core.SkPathEffectBase
import org.skia.core.SkStrokeRec
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkPath
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkMatrix
import org.skia.math.SkRect
import org.skia.math.SkScalar
import org.skia.modules.Factory

/**
 * C++ original:
 * ```cpp
 * class SkCornerPathEffectImpl : public SkPathEffectBase {
 * public:
 *     explicit SkCornerPathEffectImpl(SkScalar radius) : fRadius(radius) {
 *         SkASSERT(radius > 0);
 *     }
 *
 *     bool onFilterPath(SkPathBuilder* dst, const SkPath& src, SkStrokeRec*, const SkRect*,
 *                       const SkMatrix&) const override {
 *         if (fRadius <= 0) {
 *             return false;
 *         }
 *
 *         // just need a value that won't match when we compare it initially
 *         SkPathVerb   prevVerb = static_cast<SkPathVerb>(0xFF);
 *
 *         bool        closed;
 *         SkPoint     moveTo, lastCorner;
 *         SkVector    firstStep, step;
 *         bool        prevIsValid = true;
 *
 *         // to avoid warnings
 *         step.set(0, 0);
 *         moveTo.set(0, 0);
 *         firstStep.set(0, 0);
 *         lastCorner.set(0, 0);
 *
 *         SkPath::Iter iter(src, false);
 *         while (auto rec = iter.next()) {
 *             SkSpan<const SkPoint> pts = rec->fPoints;
 *             switch (rec->fVerb) {
 *                 case SkPathVerb::kMove:
 *                     // close out the previous (open) contour
 *                     if (SkPathVerb::kLine == prevVerb) {
 *                         dst->lineTo(lastCorner);
 *                     }
 *                     closed = iter.isClosedContour();
 *                     if (closed) {
 *                         moveTo = pts[0];
 *                         prevIsValid = false;
 *                     } else {
 *                         dst->moveTo(pts[0]);
 *                         prevIsValid = true;
 *                     }
 *                     break;
 *                 case SkPathVerb::kLine: {
 *                     bool drawSegment = ComputeStep(pts[0], pts[1], fRadius, &step);
 *                     // prev corner
 *                     if (!prevIsValid) {
 *                         dst->moveTo(moveTo + step);
 *                         prevIsValid = true;
 *                     } else {
 *                         dst->quadTo(pts[0].fX, pts[0].fY, pts[0].fX + step.fX,
 *                                     pts[0].fY + step.fY);
 *                     }
 *                     if (drawSegment) {
 *                         dst->lineTo(pts[1].fX - step.fX, pts[1].fY - step.fY);
 *                     }
 *                     lastCorner = pts[1];
 *                     prevIsValid = true;
 *                     break;
 *                 }
 *                 case SkPathVerb::kQuad:
 *                     // TBD - just replicate the curve for now
 *                     if (!prevIsValid) {
 *                         dst->moveTo(pts[0]);
 *                         prevIsValid = true;
 *                     }
 *                     dst->quadTo(pts[1], pts[2]);
 *                     lastCorner = pts[2];
 *                     firstStep.set(0, 0);
 *                     break;
 *                 case SkPathVerb::kConic:
 *                     // TBD - just replicate the curve for now
 *                     if (!prevIsValid) {
 *                         dst->moveTo(pts[0]);
 *                         prevIsValid = true;
 *                     }
 *                     dst->conicTo(pts[1], pts[2], rec->conicWeight());
 *                     lastCorner = pts[2];
 *                     firstStep.set(0, 0);
 *                     break;
 *                 case SkPathVerb::kCubic:
 *                     if (!prevIsValid) {
 *                         dst->moveTo(pts[0]);
 *                         prevIsValid = true;
 *                     }
 *                     // TBD - just replicate the curve for now
 *                     dst->cubicTo(pts[1], pts[2], pts[3]);
 *                     lastCorner = pts[3];
 *                     firstStep.set(0, 0);
 *                     break;
 *                 case SkPathVerb::kClose:
 *                     if (firstStep.fX || firstStep.fY) {
 *                         dst->quadTo(lastCorner.fX, lastCorner.fY,
 *                                     lastCorner.fX + firstStep.fX,
 *                                     lastCorner.fY + firstStep.fY);
 *                     }
 *                     dst->close();
 *                     prevIsValid = false;
 *                     break;
 *             }
 *             if (SkPathVerb::kMove == prevVerb) {
 *                 firstStep = step;
 *             }
 *             prevVerb = rec->fVerb;
 *         }
 *         if (prevIsValid) {
 *             dst->lineTo(lastCorner);
 *         }
 *         return true;
 *     }
 *
 *     bool computeFastBounds(SkRect*) const override {
 *         // Rounding sharp corners within a path produces a new path that is still contained within
 *         // the original's bounds, so leave 'bounds' unmodified.
 *         return true;
 *     }
 *
 *     static sk_sp<SkFlattenable> CreateProc(SkReadBuffer& buffer) {
 *         return SkCornerPathEffect::Make(buffer.readScalar());
 *     }
 *
 *     void flatten(SkWriteBuffer& buffer) const override {
 *         buffer.writeScalar(fRadius);
 *     }
 *
 *     Factory getFactory() const override { return CreateProc; }
 *     const char* getTypeName() const override { return "SkCornerPathEffect"; }
 *
 * private:
 *     const SkScalar fRadius;
 *
 *     using INHERITED = SkPathEffectBase;
 * }
 * ```
 */
public open class SkCornerPathEffectImpl public constructor(
  radius: SkScalar,
) : SkPathEffectBase() {
  /**
   * C++ original:
   * ```cpp
   * const SkScalar fRadius
   * ```
   */
  private val fRadius: SkScalar = TODO("Initialize fRadius")

  /**
   * C++ original:
   * ```cpp
   * bool onFilterPath(SkPathBuilder* dst, const SkPath& src, SkStrokeRec*, const SkRect*,
   *                       const SkMatrix&) const override {
   *         if (fRadius <= 0) {
   *             return false;
   *         }
   *
   *         // just need a value that won't match when we compare it initially
   *         SkPathVerb   prevVerb = static_cast<SkPathVerb>(0xFF);
   *
   *         bool        closed;
   *         SkPoint     moveTo, lastCorner;
   *         SkVector    firstStep, step;
   *         bool        prevIsValid = true;
   *
   *         // to avoid warnings
   *         step.set(0, 0);
   *         moveTo.set(0, 0);
   *         firstStep.set(0, 0);
   *         lastCorner.set(0, 0);
   *
   *         SkPath::Iter iter(src, false);
   *         while (auto rec = iter.next()) {
   *             SkSpan<const SkPoint> pts = rec->fPoints;
   *             switch (rec->fVerb) {
   *                 case SkPathVerb::kMove:
   *                     // close out the previous (open) contour
   *                     if (SkPathVerb::kLine == prevVerb) {
   *                         dst->lineTo(lastCorner);
   *                     }
   *                     closed = iter.isClosedContour();
   *                     if (closed) {
   *                         moveTo = pts[0];
   *                         prevIsValid = false;
   *                     } else {
   *                         dst->moveTo(pts[0]);
   *                         prevIsValid = true;
   *                     }
   *                     break;
   *                 case SkPathVerb::kLine: {
   *                     bool drawSegment = ComputeStep(pts[0], pts[1], fRadius, &step);
   *                     // prev corner
   *                     if (!prevIsValid) {
   *                         dst->moveTo(moveTo + step);
   *                         prevIsValid = true;
   *                     } else {
   *                         dst->quadTo(pts[0].fX, pts[0].fY, pts[0].fX + step.fX,
   *                                     pts[0].fY + step.fY);
   *                     }
   *                     if (drawSegment) {
   *                         dst->lineTo(pts[1].fX - step.fX, pts[1].fY - step.fY);
   *                     }
   *                     lastCorner = pts[1];
   *                     prevIsValid = true;
   *                     break;
   *                 }
   *                 case SkPathVerb::kQuad:
   *                     // TBD - just replicate the curve for now
   *                     if (!prevIsValid) {
   *                         dst->moveTo(pts[0]);
   *                         prevIsValid = true;
   *                     }
   *                     dst->quadTo(pts[1], pts[2]);
   *                     lastCorner = pts[2];
   *                     firstStep.set(0, 0);
   *                     break;
   *                 case SkPathVerb::kConic:
   *                     // TBD - just replicate the curve for now
   *                     if (!prevIsValid) {
   *                         dst->moveTo(pts[0]);
   *                         prevIsValid = true;
   *                     }
   *                     dst->conicTo(pts[1], pts[2], rec->conicWeight());
   *                     lastCorner = pts[2];
   *                     firstStep.set(0, 0);
   *                     break;
   *                 case SkPathVerb::kCubic:
   *                     if (!prevIsValid) {
   *                         dst->moveTo(pts[0]);
   *                         prevIsValid = true;
   *                     }
   *                     // TBD - just replicate the curve for now
   *                     dst->cubicTo(pts[1], pts[2], pts[3]);
   *                     lastCorner = pts[3];
   *                     firstStep.set(0, 0);
   *                     break;
   *                 case SkPathVerb::kClose:
   *                     if (firstStep.fX || firstStep.fY) {
   *                         dst->quadTo(lastCorner.fX, lastCorner.fY,
   *                                     lastCorner.fX + firstStep.fX,
   *                                     lastCorner.fY + firstStep.fY);
   *                     }
   *                     dst->close();
   *                     prevIsValid = false;
   *                     break;
   *             }
   *             if (SkPathVerb::kMove == prevVerb) {
   *                 firstStep = step;
   *             }
   *             prevVerb = rec->fVerb;
   *         }
   *         if (prevIsValid) {
   *             dst->lineTo(lastCorner);
   *         }
   *         return true;
   *     }
   * ```
   */
  public override fun onFilterPath(
    dst: SkPathBuilder?,
    src: SkPath,
    param2: SkStrokeRec?,
    param3: SkRect?,
    param4: SkMatrix,
  ): Boolean {
    TODO("Implement onFilterPath")
  }

  /**
   * C++ original:
   * ```cpp
   * bool computeFastBounds(SkRect*) const override {
   *         // Rounding sharp corners within a path produces a new path that is still contained within
   *         // the original's bounds, so leave 'bounds' unmodified.
   *         return true;
   *     }
   * ```
   */
  public override fun computeFastBounds(param0: SkRect?): Boolean {
    TODO("Implement computeFastBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void flatten(SkWriteBuffer& buffer) const override {
   *         buffer.writeScalar(fRadius);
   *     }
   * ```
   */
  public override fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * Factory getFactory() const override { return CreateProc; }
   * ```
   */
  public override fun getFactory(): Factory {
    TODO("Implement getFactory")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* getTypeName() const override { return "SkCornerPathEffect"; }
   * ```
   */
  public override fun getTypeName(): Char {
    TODO("Implement getTypeName")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkFlattenable> CreateProc(SkReadBuffer& buffer) {
     *         return SkCornerPathEffect::Make(buffer.readScalar());
     *     }
     * ```
     */
    public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
      TODO("Implement createProc")
    }
  }
}
