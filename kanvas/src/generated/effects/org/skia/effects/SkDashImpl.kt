package org.skia.effects

import kotlin.Boolean
import kotlin.Int
import org.skia.core.SkPathBuilder
import org.skia.core.SkPathEffectBase
import org.skia.core.SkStrokeRec
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkPath
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkMatrix
import org.skia.math.SkRect
import org.skia.math.SkScalar
import org.skia.memory.AutoTArray
import undefined.PointData

/**
 * C++ original:
 * ```cpp
 * class SkDashImpl : public SkPathEffectBase {
 * public:
 *     SkDashImpl(SkSpan<const SkScalar> intervals, SkScalar phase);
 *
 * protected:
 *     void flatten(SkWriteBuffer&) const override;
 *     bool onFilterPath(SkPathBuilder* dst, const SkPath& src, SkStrokeRec*, const SkRect*,
 *                       const SkMatrix&) const override;
 *
 *     bool onAsPoints(PointData* results, const SkPath& src, const SkStrokeRec&, const SkMatrix&,
 *                     const SkRect*) const override;
 *
 *     std::optional<DashInfo> asADash() const override;
 *
 * private:
 *     SK_FLATTENABLE_HOOKS(SkDashImpl)
 *
 *     bool computeFastBounds(SkRect* bounds) const override {
 *         // Dashing a path returns a subset of the input path so just return true and leave
 *         // bounds unmodified
 *         return true;
 *     }
 *
 *     skia_private::AutoTArray<SkScalar> fIntervals;
 *     SkScalar fPhase;
 *
 *     // computed from phase
 *     SkScalar    fInitialDashLength;
 *     SkScalar    fIntervalLength;
 *     size_t      fInitialDashIndex;
 *
 *     using INHERITED = SkPathEffectBase;
 * }
 * ```
 */
public open class SkDashImpl public constructor(
  intervals: SkSpan<SkScalar>,
  phase: SkScalar,
) : SkPathEffectBase() {
  /**
   * C++ original:
   * ```cpp
   * skia_private::AutoTArray<SkScalar> fIntervals
   * ```
   */
  private var fIntervals: AutoTArray<SkScalar> = TODO("Initialize fIntervals")

  /**
   * C++ original:
   * ```cpp
   * SkScalar fPhase
   * ```
   */
  private var fPhase: SkScalar = TODO("Initialize fPhase")

  /**
   * C++ original:
   * ```cpp
   * SkScalar    fInitialDashLength
   * ```
   */
  private var fInitialDashLength: SkScalar = TODO("Initialize fInitialDashLength")

  /**
   * C++ original:
   * ```cpp
   * SkScalar    fIntervalLength
   * ```
   */
  private var fIntervalLength: SkScalar = TODO("Initialize fIntervalLength")

  /**
   * C++ original:
   * ```cpp
   * size_t      fInitialDashIndex
   * ```
   */
  private var fInitialDashIndex: Int = TODO("Initialize fInitialDashIndex")

  /**
   * C++ original:
   * ```cpp
   * void SkDashImpl::flatten(SkWriteBuffer& buffer) const {
   *     buffer.writeScalar(fPhase);
   *     buffer.writeScalarArray(fIntervals);
   * }
   * ```
   */
  protected override fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkDashImpl::onFilterPath(SkPathBuilder* builder, const SkPath& src, SkStrokeRec* rec,
   *                               const SkRect* cullRect, const SkMatrix&) const {
   *     return SkDashPath::InternalFilter(builder, src, rec, cullRect, fIntervals,
   *                                       fInitialDashLength, fInitialDashIndex, fIntervalLength,
   *                                       fPhase);
   * }
   * ```
   */
  protected override fun onFilterPath(
    dst: SkPathBuilder?,
    src: SkPath,
    rec: SkStrokeRec?,
    cullRect: SkRect?,
    param4: SkMatrix,
  ): Boolean {
    TODO("Implement onFilterPath")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkDashImpl::onAsPoints(PointData* results, const SkPath& src, const SkStrokeRec& rec,
   *                             const SkMatrix& matrix, const SkRect* cullRect) const {
   *     // width < 0 -> fill && width == 0 -> hairline so requiring width > 0 rules both out
   *     if (0 >= rec.getWidth()) {
   *         return false;
   *     }
   *
   *     // TODO: this next test could be eased up. We could allow any number of
   *     // intervals as long as all the ons match and all the offs match.
   *     // Additionally, they do not necessarily need to be integers.
   *     // We cannot allow arbitrary intervals since we want the returned points
   *     // to be uniformly sized.
   *     if (fIntervals.size() != 2 ||
   *         !SkScalarNearlyEqual(fIntervals[0], fIntervals[1]) ||
   *         !SkScalarIsInt(fIntervals[0]) ||
   *         !SkScalarIsInt(fIntervals[1])) {
   *         return false;
   *     }
   *
   *     SkPoint pts[2];
   *
   *     if (!src.isLine(pts)) {
   *         return false;
   *     }
   *
   *     // TODO: this test could be eased up to allow circles
   *     if (SkPaint::kButt_Cap != rec.getCap()) {
   *         return false;
   *     }
   *
   *     // TODO: this test could be eased up for circles. Rotations could be allowed.
   *     if (!matrix.rectStaysRect()) {
   *         return false;
   *     }
   *
   *     // See if the line can be limited to something plausible.
   *     if (!cull_line(pts, rec, matrix, cullRect, fIntervalLength)) {
   *         return false;
   *     }
   *
   *     SkScalar length = SkPoint::Distance(pts[1], pts[0]);
   *
   *     SkVector tangent = pts[1] - pts[0];
   *     if (tangent.isZero()) {
   *         return false;
   *     }
   *
   *     tangent.scale(SkScalarInvert(length));
   *
   *     // TODO: make this test for horizontal & vertical lines more robust
   *     bool isXAxis = true;
   *     if (SkScalarNearlyEqual(SK_Scalar1, tangent.fX) ||
   *         SkScalarNearlyEqual(-SK_Scalar1, tangent.fX)) {
   *         results->fSize.set(SkScalarHalf(fIntervals[0]), SkScalarHalf(rec.getWidth()));
   *     } else if (SkScalarNearlyEqual(SK_Scalar1, tangent.fY) ||
   *                SkScalarNearlyEqual(-SK_Scalar1, tangent.fY)) {
   *         results->fSize.set(SkScalarHalf(rec.getWidth()), SkScalarHalf(fIntervals[0]));
   *         isXAxis = false;
   *     } else if (SkPaint::kRound_Cap != rec.getCap()) {
   *         // Angled lines don't have axis-aligned boxes.
   *         return false;
   *     }
   *
   *     if (results) {
   *         results->fFlags = 0;
   *         SkScalar clampedInitialDashLength = std::min(length, fInitialDashLength);
   *
   *         if (SkPaint::kRound_Cap == rec.getCap()) {
   *             results->fFlags |= PointData::kCircles_PointFlag;
   *         }
   *
   *         results->fNumPoints = 0;
   *         SkScalar len2 = length;
   *         if (clampedInitialDashLength > 0 || 0 == fInitialDashIndex) {
   *             SkASSERT(len2 >= clampedInitialDashLength);
   *             if (0 == fInitialDashIndex) {
   *                 if (clampedInitialDashLength > 0) {
   *                     if (clampedInitialDashLength >= fIntervals[0]) {
   *                         ++results->fNumPoints;  // partial first dash
   *                     }
   *                     len2 -= clampedInitialDashLength;
   *                 }
   *                 len2 -= fIntervals[1];  // also skip first space
   *                 if (len2 < 0) {
   *                     len2 = 0;
   *                 }
   *             } else {
   *                 len2 -= clampedInitialDashLength; // skip initial partial empty
   *             }
   *         }
   *         // Too many midpoints can cause results->fNumPoints to overflow or
   *         // otherwise cause the results->fPoints allocation below to OOM.
   *         // Cap it to a sane value.
   *         SkScalar numIntervals = len2 / fIntervalLength;
   *         if (!SkIsFinite(numIntervals) || numIntervals > SkDashPath::kMaxDashCount) {
   *             return false;
   *         }
   *         int numMidPoints = SkScalarFloorToInt(numIntervals);
   *         results->fNumPoints += numMidPoints;
   *         len2 -= numMidPoints * fIntervalLength;
   *         bool partialLast = false;
   *         if (len2 > 0) {
   *             if (len2 < fIntervals[0]) {
   *                 partialLast = true;
   *             } else {
   *                 ++numMidPoints;
   *                 ++results->fNumPoints;
   *             }
   *         }
   *
   *         results->fPoints = new SkPoint[results->fNumPoints];
   *
   *         SkScalar    distance = 0;
   *         int         curPt = 0;
   *
   *         if (clampedInitialDashLength > 0 || 0 == fInitialDashIndex) {
   *             SkASSERT(clampedInitialDashLength <= length);
   *
   *             if (0 == fInitialDashIndex) {
   *                 if (clampedInitialDashLength > 0) {
   *                     // partial first block
   *                     SkASSERT(SkPaint::kRound_Cap != rec.getCap()); // can't handle partial circles
   *                     SkScalar x = pts[0].fX + tangent.fX * SkScalarHalf(clampedInitialDashLength);
   *                     SkScalar y = pts[0].fY + tangent.fY * SkScalarHalf(clampedInitialDashLength);
   *                     SkScalar halfWidth, halfHeight;
   *                     if (isXAxis) {
   *                         halfWidth = SkScalarHalf(clampedInitialDashLength);
   *                         halfHeight = SkScalarHalf(rec.getWidth());
   *                     } else {
   *                         halfWidth = SkScalarHalf(rec.getWidth());
   *                         halfHeight = SkScalarHalf(clampedInitialDashLength);
   *                     }
   *                     if (clampedInitialDashLength < fIntervals[0]) {
   *                         // This one will not be like the others
   *                         results->fFirst = SkPath::Rect({x - halfWidth, y - halfHeight,
   *                                                         x + halfWidth, y + halfHeight});
   *                     } else {
   *                         SkASSERT(curPt < results->fNumPoints);
   *                         results->fPoints[curPt].set(x, y);
   *                         ++curPt;
   *                     }
   *
   *                     distance += clampedInitialDashLength;
   *                 }
   *
   *                 distance += fIntervals[1];  // skip over the next blank block too
   *             } else {
   *                 distance += clampedInitialDashLength;
   *             }
   *         }
   *
   *         if (0 != numMidPoints) {
   *             distance += SkScalarHalf(fIntervals[0]);
   *
   *             for (int i = 0; i < numMidPoints; ++i) {
   *                 SkScalar x = pts[0].fX + tangent.fX * distance;
   *                 SkScalar y = pts[0].fY + tangent.fY * distance;
   *
   *                 SkASSERT(curPt < results->fNumPoints);
   *                 results->fPoints[curPt].set(x, y);
   *                 ++curPt;
   *
   *                 distance += fIntervalLength;
   *             }
   *
   *             distance -= SkScalarHalf(fIntervals[0]);
   *         }
   *
   *         if (partialLast) {
   *             // partial final block
   *             SkASSERT(SkPaint::kRound_Cap != rec.getCap()); // can't handle partial circles
   *             SkScalar temp = length - distance;
   *             SkASSERT(temp < fIntervals[0]);
   *             SkScalar x = pts[0].fX + tangent.fX * (distance + SkScalarHalf(temp));
   *             SkScalar y = pts[0].fY + tangent.fY * (distance + SkScalarHalf(temp));
   *             SkScalar halfWidth, halfHeight;
   *             if (isXAxis) {
   *                 halfWidth = SkScalarHalf(temp);
   *                 halfHeight = SkScalarHalf(rec.getWidth());
   *             } else {
   *                 halfWidth = SkScalarHalf(rec.getWidth());
   *                 halfHeight = SkScalarHalf(temp);
   *             }
   *             results->fLast = SkPath::Rect({x - halfWidth, y - halfHeight,
   *                                            x + halfWidth, y + halfHeight});
   *         }
   *
   *         SkASSERT(curPt == results->fNumPoints);
   *     }
   *
   *     return true;
   * }
   * ```
   */
  protected override fun onAsPoints(
    results: PointData?,
    src: SkPath,
    rec: SkStrokeRec,
    matrix: SkMatrix,
    cullRect: SkRect?,
  ): Boolean {
    TODO("Implement onAsPoints")
  }

  /**
   * C++ original:
   * ```cpp
   * std::optional<SkPathEffectBase::DashInfo> SkDashImpl::asADash() const {
   *     return {{fIntervals, fPhase}};
   * }
   * ```
   */
  protected override fun asADash(): Int {
    TODO("Implement asADash")
  }

  /**
   * C++ original:
   * ```cpp
   * bool computeFastBounds(SkRect* bounds) const override {
   *         // Dashing a path returns a subset of the input path so just return true and leave
   *         // bounds unmodified
   *         return true;
   *     }
   * ```
   */
  public override fun computeFastBounds(bounds: SkRect?): Boolean {
    TODO("Implement computeFastBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFlattenable> SkDashImpl::CreateProc(SkReadBuffer& buffer) {
   *     const SkScalar phase = buffer.readScalar();
   *     uint32_t count = buffer.getArrayCount();
   *
   *     // Don't allocate gigantic buffers if there's not data for them.
   *     if (!buffer.validateCanReadN<SkScalar>(count)) {
   *         return nullptr;
   *     }
   *
   *     AutoSTArray<32, SkScalar> intervals(count);
   *     if (buffer.readScalarArray(intervals)) {
   *         return SkDashPathEffect::Make(intervals, phase);
   *     }
   *     return nullptr;
   * }
   * ```
   */
  public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
    TODO("Implement createProc")
  }
}
