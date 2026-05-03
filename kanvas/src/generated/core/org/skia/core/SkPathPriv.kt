package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import kotlin.UInt
import kotlin.ULong
import org.skia.foundation.SkPath
import org.skia.foundation.SkRRect
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan
import org.skia.math.SkMatrix
import org.skia.math.SkPathDirection
import org.skia.math.SkPathFillType
import org.skia.math.SkPathVerb
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SkPathPriv {
 * public:
 *     enum class RRectAsEnum {
 *         kRect, kOval, kRRect,
 *     };
 *     static std::pair<RRectAsEnum, unsigned> SimplifyRRect(const SkRRect& rr, unsigned startIndex) {
 *         if (rr.isRect() || rr.isEmpty()) {
 *             return { RRectAsEnum::kRect, (startIndex + 1) / 2 };
 *         }
 *         if (rr.isOval()) {
 *             return { RRectAsEnum::kOval, startIndex / 2 };
 *         }
 *         return { RRectAsEnum::kRRect, startIndex };
 *     }
 *
 *     static SkPathConvexity ComputeConvexity(SkSpan<const SkPoint> pts,
 *                                             SkSpan<const SkPathVerb> verbs,
 *                                             SkSpan<const float> conicWeights);
 *
 *     static SkPathConvexity TransformConvexity(const SkMatrix&, SkSpan<const SkPoint>,
 *                                               SkPathConvexity);
 *
 *     static uint8_t ComputeSegmentMask(SkSpan<const SkPathVerb>);
 *
 *     /* Note: does NOT use convexity in the raw, so it need not be resolved,
 *      *       if converting from builder or path.
 *      */
 *     static bool Contains(const SkPathRaw&, SkPoint);
 *
 *     // skbug.com/40041027: Not a perfect solution for W plane clipping, but 1/16384 is a
 *     // reasonable limit (roughly 5e-5)
 *     inline static constexpr SkScalar kW0PlaneDistance = 1.f / (1 << 14);
 *
 *     static SkPathFirstDirection AsFirstDirection(SkPathDirection dir) {
 *         // since we agree numerically for the values in Direction, we can just cast.
 *         return (SkPathFirstDirection)dir;
 *     }
 *
 *     /**
 *      *  Return the opposite of the specified direction. kUnknown is its own
 *      *  opposite.
 *      */
 *     static SkPathFirstDirection OppositeFirstDirection(SkPathFirstDirection dir) {
 *         static const SkPathFirstDirection gOppositeDir[] = {
 *             SkPathFirstDirection::kCCW, SkPathFirstDirection::kCW, SkPathFirstDirection::kUnknown,
 *         };
 *         return gOppositeDir[(unsigned)dir];
 *     }
 *
 *     /**
 *      *  Tries to compute the direction of the outer-most non-degenerate
 *      *  contour. If it can be computed, return that direction. If it cannot be determined,
 *      *  or the contour is known to be convex, return kUnknown. If the direction was determined,
 *      *  it is cached to make subsequent calls return quickly.
 *      */
 *     static SkPathFirstDirection ComputeFirstDirection(const SkPathRaw&);
 *     static SkPathFirstDirection ComputeFirstDirection(const SkPath&);
 *
 *     static bool IsClosedSingleContour(SkSpan<const SkPathVerb> verbs) {
 *         if (verbs.empty()) {
 *             return false;
 *         }
 *
 *         int moveCount = 0;
 *         for (const auto& verb : verbs) {
 *             switch (verb) {
 *                 case SkPathVerb::kMove:
 *                     if (++moveCount > 1) {
 *                         return false;
 *                     }
 *                     break;
 *                 case SkPathVerb::kClose:
 *                     return &verb == &verbs.back();
 *                 default:
 *                     break;
 *             }
 *         }
 *         return false;
 *     }
 *
 *     static bool IsClosedSingleContour(const SkPath& path) {
 *         return IsClosedSingleContour(path.verbs());
 *     }
 *
 *     /*
 *      *  Returns the index of the last moveTo() point, based on the verbs.
 *      *  If verbs is empty / ptCount == 0, then this returns -1.
 *      */
 *     static int FindLastMoveToIndex(SkSpan<const SkPathVerb> verbs, const size_t ptCount);
 *
 *     /*
 *      *  If we're transforming a known shape (oval or rrect), this computes what happens to its
 *      *  - winding direction
 *      *  - start index
 *      */
 *     static std::pair<SkPathDirection, unsigned>
 *     TransformDirAndStart(const SkMatrix&, bool isRRect, SkPathDirection dir, unsigned start);
 *
 *     static void AddGenIDChangeListener(const SkPath&, sk_sp<SkIDChangeListener>);
 *
 *     /**
 *      * This returns the info for a rect that has a move followed by 3 or 4 lines and a close. If
 *      * 'isSimpleFill' is true, an uncloseed rect will also be accepted as long as it starts and
 *      * ends at the same corner. This does not permit degenerate line or point rectangles.
 *      */
 *     static std::optional<SkPathRectInfo> IsSimpleRect(const SkPath& path, bool isSimpleFill);
 *
 *     // Asserts the path contour was built from RRect, so it does not return
 *     // an optional. This exists so path's can have a flag that they are really
 *     // a RRect, without having to actually store the 4 radii... since those can
 *     // be deduced from the contour itself.
 *     //
 *     static SkRRect DeduceRRectFromContour(const SkRect& bounds,
 *                                           SkSpan<const SkPoint>, SkSpan<const SkPathVerb>);
 *
 *     /**
 *      * Creates a path from arc params using the semantics of SkCanvas::drawArc. This function
 *      * assumes empty ovals and zero sweeps have already been filtered out.
 *      */
 *     static SkPath CreateDrawArcPath(const SkArc& arc, bool isFillNoPathEffect);
 *
 *     /**
 *      * Determines whether an arc produced by CreateDrawArcPath will be convex. Assumes a non-empty
 *      * oval.
 *      */
 *     static bool DrawArcIsConvex(SkScalar sweepAngle, SkArc::Type arcType, bool isFillNoPathEffect);
 *
 *     /**
 *       * Iterates through a raw range of path verbs, points, and conics. All values are returned
 *       * unaltered.
 *       *
 *       * NOTE: This class's definition will be moved into SkPathPriv once RangeIter is removed.
 *     */
 *     using RangeIter = SkPath::RangeIter;
 *
 *     /**
 *      * Iterable object for traversing verbs, points, and conic weights in a path:
 *      *
 *      *   for (auto [verb, pts, weights] : SkPathPriv::Iterate(skPath)) {
 *      *       ...
 *      *   }
 *      */
 *     struct Iterate {
 *     public:
 *         Iterate(SkPath&&) = delete;
 *         Iterate(const SkPath& path)
 *             : Iterate(path.verbs(), path.points().data(), path.conicWeights().data())
 *         {
 *             // Don't allow iteration through non-finite points.
 *             if (!path.isFinite()) {
 *                 fVerbsBegin = fVerbsEnd;
 *             }
 *         }
 *         Iterate(SkSpan<const SkPathVerb> verbs, const SkPoint* points, const SkScalar* weights)
 *             : fVerbsBegin(verbs.data())
 *             , fVerbsEnd(verbs.data() + verbs.size())
 *             , fPoints(points)
 *             , fWeights(weights)
 *         {}
 *         SkPath::RangeIter begin() { return {fVerbsBegin, fPoints, fWeights}; }
 *         SkPath::RangeIter end() { return {fVerbsEnd, nullptr, nullptr}; }
 *     private:
 *         const SkPathVerb* fVerbsBegin;
 *         const SkPathVerb* fVerbsEnd;
 *         const SkPoint* fPoints;
 *         const SkScalar* fWeights;
 *     };
 *
 *     // returns Empty() if there are no points
 *     static SkRect ComputeTightBounds(SkSpan<const SkPoint> points,
 *                                      SkSpan<const SkPathVerb> verbs,
 *                                      SkSpan<const float> conicWeights);
 *
 *     /** Returns the oval info if this path was created as an oval or circle, else returns {}.
 *      */
 *     static std::optional<SkPathOvalInfo> IsOval(const SkPath& path) {
 *         return path.getOvalInfo();
 *     }
 *
 *     /** Returns the rrect info if this path was created as one, else returns {}.
 *      */
 *     static std::optional<SkPathRRectInfo> IsRRect(const SkPath& path) {
 *         return path.getRRectInfo();
 *     }
 *
 *     /**
 *      *  Sometimes in the drawing pipeline, we have to perform math on path coordinates, even after
 *      *  the path is in device-coordinates. Tessellation and clipping are two examples. Usually this
 *      *  is pretty modest, but it can involve subtracting/adding coordinates, or multiplying by
 *      *  small constants (e.g. 2,3,4). To try to preflight issues where these optionations could turn
 *      *  finite path values into infinities (or NaNs), we allow the upper drawing code to reject
 *      *  the path if its bounds (in device coordinates) is too close to max float.
 *      */
 *     static bool TooBigForMath(const SkRect& bounds) {
 *         // This value is just a guess. smaller is safer, but we don't want to reject largish paths
 *         // that we don't have to.
 *         constexpr SkScalar scale_down_to_allow_for_small_multiplies = 0.25f;
 *         constexpr SkScalar max = SK_ScalarMax * scale_down_to_allow_for_small_multiplies;
 *
 *         // use ! expression so we return true if bounds contains NaN
 *         return !(bounds.fLeft >= -max && bounds.fTop >= -max &&
 *                  bounds.fRight <= max && bounds.fBottom <= max);
 *     }
 *
 *     // Returns number of valid points for each SkPath::Iter verb
 *     static int PtsInIter(unsigned verb) {
 *         static const uint8_t gPtsInVerb[] = {
 *             1,  // kMove    pts[0]
 *             2,  // kLine    pts[0..1]
 *             3,  // kQuad    pts[0..2]
 *             3,  // kConic   pts[0..2]
 *             4,  // kCubic   pts[0..3]
 *             0,  // kClose
 *             0   // kDone
 *         };
 *
 *         SkASSERT(verb < std::size(gPtsInVerb));
 *         return gPtsInVerb[verb];
 *     }
 *
 *     static int PtsInIter(SkPathVerb verb) { return PtsInIter((unsigned)verb); }
 *
 *     // Returns number of valid points for each verb, not including the "starter"
 *     // point that the Iterator adds for line/quad/conic/cubic
 *     static int PtsInVerb(unsigned verb) {
 *         static const uint8_t gPtsInVerb[] = {
 *             1,  // kMove    pts[0]
 *             1,  // kLine    pts[0..1]
 *             2,  // kQuad    pts[0..2]
 *             2,  // kConic   pts[0..2]
 *             3,  // kCubic   pts[0..3]
 *             0,  // kClose
 *             0   // kDone
 *         };
 *
 *         SkASSERT(verb < std::size(gPtsInVerb));
 *         return gPtsInVerb[verb];
 *     }
 *
 *     static int PtsInVerb(SkPathVerb verb) { return PtsInVerb((unsigned)verb); }
 *
 *     static bool IsAxisAligned(SkSpan<const SkPoint>);
 *
 *     static bool AllPointsEq(SkSpan<const SkPoint> pts) {
 *         for (size_t i = 1; i < pts.size(); ++i) {
 *             if (pts[0] != pts[i]) {
 *                 return false;
 *             }
 *         }
 *         return true;
 *     }
 *
 *     struct RectContour {
 *         SkRect          fRect;
 *         bool            fIsClosed;
 *         SkPathDirection fDirection;
 *         size_t          fPointsConsumed,
 *                         fVerbsConsumed;
 *     };
 *     static std::optional<RectContour> IsRectContour(SkSpan<const SkPoint> ptSpan,
 *                                                     SkSpan<const SkPathVerb> vbSpan,
 *                                                     uint32_t segmentMask,
 *                                                     bool allowPartial);
 *
 *     /** Returns true if SkPath is equivalent to nested SkRect pair when filled.
 *      If false, rect and dirs are unchanged.
 *      If true, rect and dirs are written to if not nullptr:
 *      setting rect[0] to outer SkRect, and rect[1] to inner SkRect;
 *      setting dirs[0] to SkPathDirection of outer SkRect, and dirs[1] to SkPathDirection of
 *      inner SkRect.
 *
 *      @param rect  storage for SkRect pair; may be nullptr
 *      @param dirs  storage for SkPathDirection pair; may be nullptr
 *      @return      true if SkPath contains nested SkRect pair
 *      */
 *     static bool IsNestedFillRects(const SkPathRaw&, SkRect rect[2],
 *                                   SkPathDirection dirs[2] = nullptr);
 *
 *     static bool IsNestedFillRects(const SkPath& path, SkRect rect[2],
 *                                   SkPathDirection dirs[2] = nullptr) {
 *         auto raw = Raw(path, SkResolveConvexity::kNo);
 *         return raw.has_value() && IsNestedFillRects(*raw, rect, dirs);
 *     }
 *
 *
 *     static bool IsInverseFillType(SkPathFillType fill) {
 *         return (static_cast<int>(fill) & 2) != 0;
 *     }
 *
 *     /**
 *      *  If needed (to not blow-up under a perspective matrix), clip the path, returning the
 *      *  answer in "result", and return true.
 *      *
 *      *  Note result might be empty (if the path was completely clipped out).
 *      *
 *      *  If no clipping is needed, returns false and "result" is left unchanged.
 *      */
 *     static bool PerspectiveClip(const SkPath& src, const SkMatrix&, SkPath* result);
 *
 *     /**
 *      * Gets the number of GenIDChangeListeners. If another thread has access to this path then
 *      * this may be stale before return and only indicates that the count was the return value
 *      * at some point during the execution of the function.
 *      */
 *     static int GenIDChangeListenersCount(const SkPath&);
 *
 *     static SkPathConvexity GetConvexity(const SkPath& path) {
 *         return path.getConvexity();
 *     }
 *     static SkPathConvexity GetConvexityOrUnknown(const SkPath& path) {
 *         return path.getConvexityOrUnknown();
 *     }
 *     static void SetConvexity(const SkPath& path, SkPathConvexity c) {
 *         path.setConvexity(c);
 *     }
 *     static void ForceComputeConvexity(const SkPath& path) {
 *         path.setConvexity(SkPathConvexity::kUnknown);
 *         (void)path.isConvex();
 *     }
 *
 *     static SkPathConvexity GetConvexityOrUnknown(const SkPathData& pdata) {
 *         return pdata.getConvexityOrUnknown();
 *     }
 *
 *     static void ReverseAddPath(SkPathBuilder* builder, const SkPath& reverseMe) {
 *         builder->privateReverseAddPath(reverseMe);
 *     }
 *
 *     static void ReversePathTo(SkPathBuilder* builder, const SkPath& reverseMe) {
 *         builder->privateReversePathTo(reverseMe);
 *     }
 *
 *     static SkPath ReversePath(const SkPath& reverseMe) {
 *         SkPathBuilder bu;
 *         bu.privateReverseAddPath(reverseMe);
 *         return bu.detach();
 *     }
 *
 *     static SkSpan<const SkPathVerb> GetVerbs(const SkPathBuilder& builder) {
 *         return builder.fVerbs;
 *     }
 *
 *     static int CountVerbs(const SkPathBuilder& builder) {
 *         return builder.fVerbs.size();
 *     }
 *
 *     static std::optional<SkPathRaw> Raw(const SkPath& path, SkResolveConvexity rc) {
 *         return path.raw(rc);
 *     }
 *
 *     static std::optional<SkPathRaw> Raw(const SkPathBuilder& builder, SkResolveConvexity rc) {
 *         const auto bounds = builder.computeFiniteBounds();
 *         if (!bounds) {
 *             return {};
 *         }
 *
 *         SkPathConvexity convexity = builder.fConvexity;
 *         if (convexity == SkPathConvexity::kUnknown && rc == SkResolveConvexity::kYes) {
 *             convexity = SkPathPriv::ComputeConvexity(builder.fPts,
 *                                                      builder.fVerbs,
 *                                                      builder.fConicWeights);
 *         }
 *
 *         return SkPathRaw{
 *             builder.points(),
 *             builder.verbs(),
 *             builder.conicWeights(),
 *             *bounds,
 *             builder.fillType(),
 *             convexity,
 *             SkTo<uint8_t>(builder.fSegmentMask),
 *         };
 *     }
 *
 *     // Returns Empty if there are no points
 *     // Returns {} if the bounds are not finite
 *     static std::optional<SkRect> TrimmedBounds(SkSpan<const SkPoint> pts,
 *                                                SkSpan<const SkPathVerb> vbs) {
 *         // Does a trailing kMove verb contribute to the bounds?
 *         // - only if it is the only verb in the path
 *         // - otherwise we ignore it when computing bounds
 *         if (vbs.size() > 1 && vbs.back() == SkPathVerb::kMove) {
 *             SkASSERT(pts.size() > 0);
 *             // While trailing moves do not contribute to the bounds, we still reject them.
 *             if (!pts.back().isFinite()) {
 *                 return {};
 *             }
 *             pts = pts.subspan(0, pts.size() - 1);
 *         }
 *         return SkRect::Bounds(pts);
 *     }
 * }
 * ```
 */
public open class SkPathPriv {
  public data class Iterate public constructor(
    private val fVerbsBegin: SkPathVerb?,
    private val fVerbsEnd: SkPathVerb?,
    private val fPoints: SkPoint?,
    private val fWeights: SkScalar?,
  ) {
    public fun begin(): SkPath.RangeIter {
      TODO("Implement begin")
    }

    public fun end(): SkPath.RangeIter {
      TODO("Implement end")
    }
  }

  public data class RectContour public constructor(
    public var fRect: SkRect,
    public var fIsClosed: Boolean,
    public var fDirection: SkPathDirection,
    public var fPointsConsumed: Int,
    public var fVerbsConsumed: Int,
  )

  public enum class RRectAsEnum {
    kRect,
    kOval,
    kRRect,
  }

  public companion object {
    public val kW0PlaneDistance: SkScalar = TODO("Initialize kW0PlaneDistance")

    /**
     * C++ original:
     * ```cpp
     * static std::pair<RRectAsEnum, unsigned> SimplifyRRect(const SkRRect& rr, unsigned startIndex) {
     *         if (rr.isRect() || rr.isEmpty()) {
     *             return { RRectAsEnum::kRect, (startIndex + 1) / 2 };
     *         }
     *         if (rr.isOval()) {
     *             return { RRectAsEnum::kOval, startIndex / 2 };
     *         }
     *         return { RRectAsEnum::kRRect, startIndex };
     *     }
     * ```
     */
    public fun simplifyRRect(rr: SkRRect, startIndex: UInt): Int {
      TODO("Implement simplifyRRect")
    }

    /**
     * C++ original:
     * ```cpp
     * SkPathConvexity SkPathPriv::ComputeConvexity(SkSpan<const SkPoint> points,
     *                                              SkSpan<const SkPathVerb> vbs,
     *                                              SkSpan<const float> conicWeights) {
     *     // callers need to give us finite values
     *     SkASSERT(SkRect::Bounds(points).has_value());
     *
     *     trim_trailing_moves(points, vbs);
     *
     *     if (vbs.empty()) {
     *         return SkPathConvexity::kConvex_Degenerate;
     *     }
     *
     *     // Check to see if path changes direction more than three times as quick concave test
     *     if (Convexicator::IsConcaveBySign(points.data(), points.size())) {
     *         return SkPathConvexity::kConcave;
     *     }
     *
     *     int contourCount = 0;
     *     bool needsClose = false;
     *     Convexicator state;
     *
     *     auto iter = SkPathIter(points, vbs, conicWeights);
     *     while (auto rec = iter.next()) {
     *         auto verb = rec->fVerb;
     *         auto pts = rec->fPoints;
     *
     *         // Looking for the last moveTo before non-move verbs start
     *         if (contourCount == 0) {
     *             if (verb == SkPathVerb::kMove) {
     *                 state.setMovePt(pts[0]);
     *             } else {
     *                 // Starting the actual contour, fall through to c=1 to add the points
     *                 contourCount++;
     *                 needsClose = true;
     *             }
     *         }
     *         // Accumulating points into the Convexicator until we hit a close or another move
     *         if (contourCount == 1) {
     *             if (verb == SkPathVerb::kClose || verb == SkPathVerb::kMove) {
     *                 if (!state.close()) {
     *                     return SkPathConvexity::kConcave;
     *                 }
     *                 needsClose = false;
     *                 contourCount++;
     *             } else {
     *                 // lines add 1 point, cubics add 3, conics and quads add 2
     *                 int count = SkPathPriv::PtsInVerb((unsigned) verb);
     *                 SkASSERT(count > 0);
     *                 for (int i = 1; i <= count; ++i) {
     *                     if (!state.addPt(pts[i])) {
     *                         return SkPathConvexity::kConcave;
     *                     }
     *                 }
     *             }
     *         } else {
     *             // The first contour has closed and anything other than spurious trailing moves means
     *             // there's multiple contours and the path can't be convex
     *             if (verb != SkPathVerb::kMove) {
     *                 return SkPathConvexity::kConcave;
     *             }
     *         }
     *     }
     *
     *     // If the path isn't explicitly closed do so implicitly
     *     if (needsClose && !state.close()) {
     *         return SkPathConvexity::kConcave;
     *     }
     *
     *     const auto firstDir = state.getFirstDirection();
     *     if (firstDir == SkPathFirstDirection::kUnknown && state.reversals() >= 3) {
     *         return SkPathConvexity::kConcave;
     *     }
     *     return SkPathFirstDirection_ToConvexity(firstDir);
     * }
     * ```
     */
    public fun computeConvexity(
      pts: SkSpan<SkPoint>,
      verbs: SkSpan<SkPathVerb>,
      conicWeights: SkSpan<Float>,
    ): SkPathConvexity {
      TODO("Implement computeConvexity")
    }

    /**
     * C++ original:
     * ```cpp
     * SkPathConvexity SkPathPriv::TransformConvexity(const SkMatrix& matrix, SkSpan<const SkPoint> pts,
     *                                                SkPathConvexity convexity) {
     *     if (matrix.isIdentity() || pts.empty()) {
     *         return convexity;
     *     }
     *
     *     // Due to finite/fragile float numerics, we can't assume that a convex path remains
     *     // convex after a transformation, so mark it as unknown here.
     *     // However, some transformations are thought to be safe:
     *     //    axis-aligned values under scale/translate.
     *     //
     *     if (SkPathConvexity_IsConvex(convexity)) {
     *         if (!matrix.isScaleTranslate() || !SkPathPriv::IsAxisAligned(pts)) {
     *             // Not safe to still assume we're convex...
     *             convexity = SkPathConvexity::kUnknown;
     *         } else {
     *             SkScalar det2x2 =
     *                 matrix.get(SkMatrix::kMScaleX) * matrix.get(SkMatrix::kMScaleY) -
     *                 matrix.get(SkMatrix::kMSkewX)  * matrix.get(SkMatrix::kMSkewY);
     *             if (det2x2 < 0) {
     *                 convexity = SkPathConvexity_OppositeConvexDirection(convexity);
     *             } else if (det2x2 > 0) {
     *                 // we keep our direction
     *             } else /* det2x == 0 */ {
     *                 convexity = SkPathConvexity::kConvex_Degenerate;
     *             }
     *         }
     *     }
     *     return convexity;
     * }
     * ```
     */
    public fun transformConvexity(
      matrix: SkMatrix,
      pts: SkSpan<SkPoint>,
      convexity: SkPathConvexity,
    ): SkPathConvexity {
      TODO("Implement transformConvexity")
    }

    /**
     * C++ original:
     * ```cpp
     * uint8_t SkPathPriv::ComputeSegmentMask(SkSpan<const SkPathVerb> verbs) {
     *     unsigned mask = 0;
     *     for (auto v : verbs) {
     *         unsigned i = static_cast<unsigned>(v);
     *         SkASSERT(i < std::size(gVerbToSegmentMask));
     *         mask |= gVerbToSegmentMask[i];
     *     }
     *     return SkTo<uint8_t>(mask);
     * }
     * ```
     */
    public fun computeSegmentMask(verbs: SkSpan<SkPathVerb>): Int {
      TODO("Implement computeSegmentMask")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkPathPriv::Contains(const SkPathRaw& raw, SkPoint p) {
     *     const SkPathFillType ft = raw.fillType();
     *     const bool isInverse = SkPathFillType_IsInverse(ft);
     *     if (raw.empty()) {
     *         return isInverse;
     *     }
     *
     *     if (!contains_inclusive(raw.bounds(), p)) {
     *         return isInverse;
     *     }
     *
     *     int w = 0;
     *     int onCurveCount = 0;
     *
     *     for (auto iter = SkPathEdgeIter(raw); auto rec = iter.next(); ) {
     *         switch (rec.fEdge) {
     *             case SkPathEdgeIter::Edge::kLine:
     *                 w += winding_line({rec.fPts, 2}, p.fX, p.fY, &onCurveCount);
     *                 break;
     *             case SkPathEdgeIter::Edge::kQuad:
     *                 w += winding_quad({rec.fPts, 3}, p.fX, p.fY, &onCurveCount);
     *                 break;
     *             case SkPathEdgeIter::Edge::kConic:
     *                 w += winding_conic({rec.fPts, 3}, p.fX, p.fY, iter.conicWeight(), &onCurveCount);
     *                 break;
     *             case SkPathEdgeIter::Edge::kCubic:
     *                 w += winding_cubic({rec.fPts, 4}, p.fX, p.fY, &onCurveCount);
     *                 break;
     *        }
     *     }
     *     bool evenOddFill = SkPathFillType::kEvenOdd        == ft
     *                     || SkPathFillType::kInverseEvenOdd == ft;
     *     if (evenOddFill) {
     *         w &= 1;
     *     }
     *     if (w) {
     *         return !isInverse;
     *     }
     *     if (onCurveCount <= 1) {
     *         return SkToBool(onCurveCount) ^ isInverse;
     *     }
     *     if ((onCurveCount & 1) || evenOddFill) {
     *         return SkToBool(onCurveCount & 1) ^ isInverse;
     *     }
     *     // If the point touches an even number of curves, and the fill is winding, check for
     *     // coincidence. Count coincidence as places where the on curve points have identical tangents.
     *     SkTDArray<SkVector> tangents;
     *     for (auto iter = SkPathEdgeIter(raw); auto rec = iter.next(); ) {
     *         int oldCount = tangents.size();
     *         switch (rec.fEdge) {
     *             case SkPathEdgeIter::Edge::kLine:
     *                 tangent_line({rec.fPts, 2}, p.fX, p.fY, &tangents);
     *                 break;
     *             case SkPathEdgeIter::Edge::kQuad:
     *                 tangent_quad({rec.fPts, 3}, p.fX, p.fY, &tangents);
     *                 break;
     *             case SkPathEdgeIter::Edge::kConic:
     *                 tangent_conic({rec.fPts, 3}, p.fX, p.fY, iter.conicWeight(), &tangents);
     *                 break;
     *             case SkPathEdgeIter::Edge::kCubic:
     *                 tangent_cubic({rec.fPts, 4}, p.fX, p.fY, &tangents);
     *                 break;
     *        }
     *        if (tangents.size() > oldCount) {
     *             int last = tangents.size() - 1;
     *             const SkVector& tangent = tangents[last];
     *             if (SkScalarNearlyZero(SkPointPriv::LengthSqd(tangent))) {
     *                 tangents.remove(last);
     *             } else {
     *                 for (int index = 0; index < last; ++index) {
     *                     const SkVector& test = tangents[index];
     *                     if (SkScalarNearlyZero(test.cross(tangent))
     *                             && SkScalarSignAsInt(tangent.fX * test.fX) <= 0
     *                             && SkScalarSignAsInt(tangent.fY * test.fY) <= 0) {
     *                         tangents.remove(last);
     *                         tangents.removeShuffle(index);
     *                         break;
     *                     }
     *                 }
     *             }
     *         }
     *     }
     *     return SkToBool(tangents.size()) ^ isInverse;
     * }
     * ```
     */
    public fun contains(raw: SkPathRaw, p: SkPoint): Boolean {
      TODO("Implement contains")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkPathFirstDirection AsFirstDirection(SkPathDirection dir) {
     *         // since we agree numerically for the values in Direction, we can just cast.
     *         return (SkPathFirstDirection)dir;
     *     }
     * ```
     */
    public fun asFirstDirection(dir: SkPathDirection): SkPathFirstDirection {
      TODO("Implement asFirstDirection")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkPathFirstDirection OppositeFirstDirection(SkPathFirstDirection dir) {
     *         static const SkPathFirstDirection gOppositeDir[] = {
     *             SkPathFirstDirection::kCCW, SkPathFirstDirection::kCW, SkPathFirstDirection::kUnknown,
     *         };
     *         return gOppositeDir[(unsigned)dir];
     *     }
     * ```
     */
    public fun oppositeFirstDirection(dir: SkPathFirstDirection): SkPathFirstDirection {
      TODO("Implement oppositeFirstDirection")
    }

    /**
     * C++ original:
     * ```cpp
     * SkPathFirstDirection SkPathPriv::ComputeFirstDirection(const SkPathRaw& raw) {
     *     ContourIter iter(raw.points(), raw.verbs(), raw.conics());
     *
     *     // initialize with our logical y-min
     *     SkScalar ymax = raw.bounds().fTop;
     *     SkScalar ymaxCross = 0;
     *
     *     for (; !iter.done(); iter.next()) {
     *         int n = iter.count();
     *         if (n < 3) {
     *             continue;
     *         }
     *
     *         const SkPoint* pts = iter.pts();
     *         SkScalar cross = 0;
     *         int index = find_max_y(pts, n);
     *         if (pts[index].fY < ymax) {
     *             continue;
     *         }
     *
     *         // If there is more than 1 distinct point at the y-max, we take the
     *         // x-min and x-max of them and just subtract to compute the dir.
     *         if (pts[(index + 1) % n].fY == pts[index].fY) {
     *             int maxIndex;
     *             int minIndex = find_min_max_x_at_y(pts, index, n, &maxIndex);
     *             if (minIndex == maxIndex) {
     *                 goto TRY_CROSSPROD;
     *             }
     *             SkASSERT(pts[minIndex].fY == pts[index].fY);
     *             SkASSERT(pts[maxIndex].fY == pts[index].fY);
     *             SkASSERT(pts[minIndex].fX <= pts[maxIndex].fX);
     *             // we just subtract the indices, and let that auto-convert to
     *             // SkScalar, since we just want - or + to signal the direction.
     *             cross = minIndex - maxIndex;
     *         } else {
     *             TRY_CROSSPROD:
     *             // Find a next and prev index to use for the cross-product test,
     *             // but we try to find pts that form non-zero vectors from pts[index]
     *             //
     *             // Its possible that we can't find two non-degenerate vectors, so
     *             // we have to guard our search (e.g. all the pts could be in the
     *             // same place).
     *
     *             // we pass n - 1 instead of -1 so we don't foul up % operator by
     *             // passing it a negative LH argument.
     *             int prev = find_diff_pt(pts, index, n, n - 1);
     *             if (prev == index) {
     *                 // completely degenerate, skip to next contour
     *                 continue;
     *             }
     *             int next = find_diff_pt(pts, index, n, 1);
     *             SkASSERT(next != index);
     *             cross = cross_prod(pts[prev], pts[index], pts[next]);
     *             // if we get a zero and the points are horizontal, then we look at the spread in
     *             // x-direction. We really should continue to walk away from the degeneracy until
     *             // there is a divergence.
     *             if (0 == cross && pts[prev].fY == pts[index].fY && pts[next].fY == pts[index].fY) {
     *                 // construct the subtract so we get the correct Direction below
     *                 cross = pts[index].fX - pts[next].fX;
     *             }
     *         }
     *
     *         if (cross) {
     *             // record our best guess so far
     *             ymax = pts[index].fY;
     *             ymaxCross = cross;
     *         }
     *     }
     *
     *     return ymaxCross ? crossToDir(ymaxCross) : SkPathFirstDirection::kUnknown;
     * }
     * ```
     */
    public fun computeFirstDirection(raw: SkPathRaw): SkPathFirstDirection {
      TODO("Implement computeFirstDirection")
    }

    /**
     * C++ original:
     * ```cpp
     * SkPathFirstDirection SkPathPriv::ComputeFirstDirection(const SkPath& path) {
     *     auto convexity = path.getConvexityOrUnknown();
     *     if (SkPathConvexity_IsConvex(convexity)) {
     *         // Note, this can return kUnknown. That is valid. If we've determined that the
     *         // path is convex, then we've already tried to compute its first-direction. If
     *         // that failed, then kUnknown is the right answer.
     *         return SkPathConvexity_ToFirstDirection(convexity);
     *     }
     *
     *     // Note, this can compute a 'first' direction, even for non-convex shapes.
     *     if (auto raw = SkPathPriv::Raw(path, SkResolveConvexity::kNo)) {
     *         return ComputeFirstDirection(*raw);
     *     } else {
     *         return SkPathFirstDirection::kUnknown;
     *     }
     * }
     * ```
     */
    public fun computeFirstDirection(path: SkPath): SkPathFirstDirection {
      TODO("Implement computeFirstDirection")
    }

    /**
     * C++ original:
     * ```cpp
     * static bool IsClosedSingleContour(SkSpan<const SkPathVerb> verbs) {
     *         if (verbs.empty()) {
     *             return false;
     *         }
     *
     *         int moveCount = 0;
     *         for (const auto& verb : verbs) {
     *             switch (verb) {
     *                 case SkPathVerb::kMove:
     *                     if (++moveCount > 1) {
     *                         return false;
     *                     }
     *                     break;
     *                 case SkPathVerb::kClose:
     *                     return &verb == &verbs.back();
     *                 default:
     *                     break;
     *             }
     *         }
     *         return false;
     *     }
     * ```
     */
    public fun isClosedSingleContour(verbs: SkSpan<SkPathVerb>): Boolean {
      TODO("Implement isClosedSingleContour")
    }

    /**
     * C++ original:
     * ```cpp
     * static bool IsClosedSingleContour(const SkPath& path) {
     *         return IsClosedSingleContour(path.verbs());
     *     }
     * ```
     */
    public fun isClosedSingleContour(path: SkPath): Boolean {
      TODO("Implement isClosedSingleContour")
    }

    /**
     * C++ original:
     * ```cpp
     * int SkPathPriv::FindLastMoveToIndex(SkSpan<const SkPathVerb> verbs, const size_t ptCount) {
     *     if (verbs.empty()) {
     *         SkASSERT(ptCount == 0);
     *         return -1;
     *     }
     *     SkASSERT(verbs[0] == SkPathVerb::kMove);
     *     SkASSERT(ptCount > 0);
     *
     *     int ptIndex = SkToInt(ptCount) - 1;
     *     for (auto it = verbs.rbegin(), end = verbs.rend(); it != end; ++it) {
     *         const SkPathVerb verb = *it;
     *         if (verb == SkPathVerb::kMove) {
     *             break;
     *         }
     *         ptIndex -= PtsInVerb(verb);
     *     }
     *     SkASSERT(ptIndex >= 0);
     *     return ptIndex;
     * }
     * ```
     */
    public fun findLastMoveToIndex(verbs: SkSpan<SkPathVerb>, ptCount: ULong): Int {
      TODO("Implement findLastMoveToIndex")
    }

    /**
     * C++ original:
     * ```cpp
     * std::pair<SkPathDirection, unsigned>
     * SkPathPriv::TransformDirAndStart(const SkMatrix& matrix, bool isRRect, SkPathDirection dir,
     *                                  unsigned start) {
     *     unsigned inStart = start;
     *     bool isCCW = (dir == SkPathDirection::kCCW);
     *
     *     int rm = 0;
     *     if (isRRect) {
     *         // Degenerate rrect indices to oval indices and remember the remainder.
     *         // Ovals have one index per side whereas rrects have two.
     *         rm = inStart & 0b1;
     *         inStart /= 2;
     *     }
     *     // Is the antidiagonal non-zero (otherwise the diagonal is zero)
     *     int antiDiag;
     *     // Is the non-zero value in the top row (either kMScaleX or kMSkewX) negative
     *     int topNeg;
     *     // Are the two non-zero diagonal or antidiagonal values the same sign.
     *     int sameSign;
     *     if (matrix.get(SkMatrix::kMScaleX) != 0) {
     *         antiDiag = 0b00;
     *         if (matrix.get(SkMatrix::kMScaleX) > 0) {
     *             topNeg = 0b00;
     *             sameSign = matrix.get(SkMatrix::kMScaleY) > 0 ? 0b01 : 0b00;
     *         } else {
     *             topNeg = 0b10;
     *             sameSign = matrix.get(SkMatrix::kMScaleY) > 0 ? 0b00 : 0b01;
     *         }
     *     } else {
     *         antiDiag = 0b01;
     *         if (matrix.get(SkMatrix::kMSkewX) > 0) {
     *             topNeg = 0b00;
     *             sameSign = matrix.get(SkMatrix::kMSkewY) > 0 ? 0b01 : 0b00;
     *         } else {
     *             topNeg = 0b10;
     *             sameSign = matrix.get(SkMatrix::kMSkewY) > 0 ? 0b00 : 0b01;
     *         }
     *     }
     *     if (sameSign != antiDiag) {
     *         // This is a rotation (and maybe scale). The direction is unchanged.
     *         // Trust me on the start computation (or draw yourself some pictures)
     *         start = (inStart + 4 - (topNeg | antiDiag)) % 4;
     *         SkASSERT(start < 4);
     *         if (isRRect) {
     *             start = 2 * start + rm;
     *         }
     *     } else {
     *         // This is a mirror (and maybe scale). The direction is reversed.
     *         isCCW = !isCCW;
     *         // Trust me on the start computation (or draw yourself some pictures)
     *         start = (6 + (topNeg | antiDiag) - inStart) % 4;
     *         SkASSERT(start < 4);
     *         if (isRRect) {
     *             start = 2 * start + (rm ? 0 : 1);
     *         }
     *     }
     *
     *     return {
     *         isCCW ? SkPathDirection::kCCW : SkPathDirection::kCW,
     *         start
     *     };
     * }
     * ```
     */
    public fun transformDirAndStart(
      matrix: SkMatrix,
      isRRect: Boolean,
      dir: SkPathDirection,
      start: UInt,
    ): Int {
      TODO("Implement transformDirAndStart")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkPathPriv::AddGenIDChangeListener(const SkPath& path, sk_sp<SkIDChangeListener> listener) {
     *     auto pdata = path.fPathData.get();
     *     // SkPath's error-singleton is never deleted, so we don't want to add any listeners to it.
     *     if (pdata != SkPath::PeekErrorSingleton()) {
     *         pdata->addGenIDChangeListener(std::move(listener));
     *     }
     * }
     * ```
     */
    public fun addGenIDChangeListener(path: SkPath, listener: SkSp<SkIDChangeListener>) {
      TODO("Implement addGenIDChangeListener")
    }

    /**
     * C++ original:
     * ```cpp
     * std::optional<SkPathRectInfo> SkPathPriv::IsSimpleRect(const SkPath& path, bool isSimpleFill) {
     *     if (path.getSegmentMasks() != SkPath::kLine_SegmentMask) {
     *         return {};
     *     }
     *     SkPoint rectPts[5];
     *     int rectPtCnt = 0;
     *     bool needsClose = !isSimpleFill;
     *     for (auto [v, verbPts, w] : SkPathPriv::Iterate(path)) {
     *         switch (v) {
     *             case SkPathVerb::kMove:
     *                 if (0 != rectPtCnt) {
     *                     return {};
     *                 }
     *                 rectPts[0] = verbPts[0];
     *                 ++rectPtCnt;
     *                 break;
     *             case SkPathVerb::kLine:
     *                 if (5 == rectPtCnt) {
     *                     return {};
     *                 }
     *                 rectPts[rectPtCnt] = verbPts[1];
     *                 ++rectPtCnt;
     *                 break;
     *             case SkPathVerb::kClose:
     *                 if (4 == rectPtCnt) {
     *                     rectPts[4] = rectPts[0];
     *                     rectPtCnt = 5;
     *                 }
     *                 needsClose = false;
     *                 break;
     *             case SkPathVerb::kQuad:
     *             case SkPathVerb::kConic:
     *             case SkPathVerb::kCubic:
     *                 return {};
     *         }
     *     }
     *     if (needsClose) {
     *         return {};
     *     }
     *     if (rectPtCnt < 5) {
     *         return {};
     *     }
     *     if (rectPts[0] != rectPts[4]) {
     *         return {};
     *     }
     *     // Check for two cases of rectangles: pts 0 and 3 form a vertical edge or a horizontal edge (
     *     // and pts 1 and 2 the opposite vertical or horizontal edge).
     *     bool vec03IsVertical;
     *     if (rectPts[0].fX == rectPts[3].fX && rectPts[1].fX == rectPts[2].fX &&
     *         rectPts[0].fY == rectPts[1].fY && rectPts[3].fY == rectPts[2].fY) {
     *         // Make sure it has non-zero width and height
     *         if (rectPts[0].fX == rectPts[1].fX || rectPts[0].fY == rectPts[3].fY) {
     *             return {};
     *         }
     *         vec03IsVertical = true;
     *     } else if (rectPts[0].fY == rectPts[3].fY && rectPts[1].fY == rectPts[2].fY &&
     *                rectPts[0].fX == rectPts[1].fX && rectPts[3].fX == rectPts[2].fX) {
     *         // Make sure it has non-zero width and height
     *         if (rectPts[0].fY == rectPts[1].fY || rectPts[0].fX == rectPts[3].fX) {
     *             return {};
     *         }
     *         vec03IsVertical = false;
     *     } else {
     *         return {};
     *     }
     *
     *     SkPathRectInfo info;
     *
     *     // Set sortFlags so that it has the low bit set if pt index 0 is on right edge and second bit
     *     // set if it is on the bottom edge.
     *     unsigned sortFlags =
     *             ((rectPts[0].fX < rectPts[2].fX) ? 0b00 : 0b01) |
     *             ((rectPts[0].fY < rectPts[2].fY) ? 0b00 : 0b10);
     *     switch (sortFlags) {
     *         case 0b00:
     *             info.fRect.setLTRB(rectPts[0].fX, rectPts[0].fY, rectPts[2].fX, rectPts[2].fY);
     *             info.fDirection = vec03IsVertical ? SkPathDirection::kCW : SkPathDirection::kCCW;
     *             info.fStartIndex = 0;
     *             break;
     *         case 0b01:
     *             info.fRect.setLTRB(rectPts[2].fX, rectPts[0].fY, rectPts[0].fX, rectPts[2].fY);
     *             info.fDirection = vec03IsVertical ? SkPathDirection::kCCW : SkPathDirection::kCW;
     *             info.fStartIndex = 1;
     *             break;
     *         case 0b10:
     *             info.fRect.setLTRB(rectPts[0].fX, rectPts[2].fY, rectPts[2].fX, rectPts[0].fY);
     *             info.fDirection = vec03IsVertical ? SkPathDirection::kCCW : SkPathDirection::kCW;
     *             info.fStartIndex = 3;
     *             break;
     *         case 0b11:
     *             info.fRect.setLTRB(rectPts[2].fX, rectPts[2].fY, rectPts[0].fX, rectPts[0].fY);
     *             info.fDirection = vec03IsVertical ? SkPathDirection::kCW : SkPathDirection::kCCW;
     *             info.fStartIndex = 2;
     *             break;
     *     }
     *     return info;
     * }
     * ```
     */
    public fun isSimpleRect(path: SkPath, isSimpleFill: Boolean): Int {
      TODO("Implement isSimpleRect")
    }

    /**
     * C++ original:
     * ```cpp
     * SkRRect SkPathPriv::DeduceRRectFromContour(const SkRect& bounds, SkSpan<const SkPoint> pts,
     *                                            SkSpan<const SkPathVerb> vbs) {
     *     SkASSERT(!vbs.empty());
     *     SkASSERT(vbs.front() == SkPathVerb::kMove);
     *
     *     SkVector radii[4] = {{0, 0}, {0, 0}, {0, 0}, {0, 0}};
     *
     *     size_t ptIndex = 0;
     *     for (const SkPathVerb verb : vbs) {
     *         switch (verb) {
     *             case SkPathVerb::kMove:
     *                 SkASSERT(ptIndex == 0); // we only expect 1 move
     *                 ptIndex += 1;
     *                 break;
     *             case SkPathVerb::kLine: {
     *                 // we only expect horizontal or vertical lines
     *                 SkDEBUGCODE(const SkVector delta = pts[ptIndex] - pts[ptIndex-1];)
     *                 SkASSERT(delta.fX == 0 || delta.fY == 0);
     *                 ptIndex += 1;
     *             } break;
     *             case SkPathVerb::kQuad:  SkASSERT(false); break;
     *             case SkPathVerb::kCubic: SkASSERT(false); break;
     *             case SkPathVerb::kConic: {
     *                 SkVector v1_0 = pts[ptIndex] - pts[ptIndex - 1];
     *                 SkVector v2_1 = pts[ptIndex + 1] - pts[ptIndex];
     *                 SkVector dxdy;
     *                 if (v1_0.fX) {
     *                     SkASSERT(!v2_1.fX && !v1_0.fY);
     *                     dxdy.set(SkScalarAbs(v1_0.fX), SkScalarAbs(v2_1.fY));
     *                 } else if (!v1_0.fY) {
     *                     SkASSERT(!v2_1.fX || !v2_1.fY);
     *                     dxdy.set(SkScalarAbs(v2_1.fX), SkScalarAbs(v2_1.fY));
     *                 } else {
     *                     SkASSERT(!v2_1.fY);
     *                     dxdy.set(SkScalarAbs(v2_1.fX), SkScalarAbs(v1_0.fY));
     *                 }
     *                 SkRRect::Corner corner =
     *                     pts[ptIndex].fX == bounds.fLeft ?
     *                         pts[ptIndex].fY == bounds.fTop ?
     *                             SkRRect::kUpperLeft_Corner : SkRRect::kLowerLeft_Corner :
     *                         pts[ptIndex].fY == bounds.fTop ?
     *                             SkRRect::kUpperRight_Corner : SkRRect::kLowerRight_Corner;
     *                 SkASSERT(!radii[corner].fX && !radii[corner].fY);
     *                 radii[corner] = dxdy;
     *                 ptIndex += 2;
     *             } break;
     *             case SkPathVerb::kClose:
     *                 break;
     *         }
     *     }
     *     SkRRect rrect;
     *     rrect.setRectRadii(bounds, radii);
     *     return rrect;
     * }
     * ```
     */
    public fun deduceRRectFromContour(
      bounds: SkRect,
      pts: SkSpan<SkPoint>,
      vbs: SkSpan<SkPathVerb>,
    ): SkRRect {
      TODO("Implement deduceRRectFromContour")
    }

    /**
     * C++ original:
     * ```cpp
     * SkPath SkPathPriv::CreateDrawArcPath(const SkArc& arc, bool isFillNoPathEffect) {
     *     SkRect oval = arc.fOval;
     *     SkScalar startAngle = arc.fStartAngle, sweepAngle = arc.fSweepAngle;
     *     SkASSERT(!oval.isEmpty());
     *     SkASSERT(sweepAngle);
     *     // We cap the number of total rotations. This keeps the resulting paths simpler. More important,
     *     // it prevents values so large that the loops below never terminate (once ULP > 360).
     *     if (SkScalarAbs(sweepAngle) > 3600.0f) {
     *         sweepAngle = std::copysign(3600.0f, sweepAngle) + std::fmod(sweepAngle, 360.0f);
     *     }
     *
     *     SkPathBuilder builder(SkPathFillType::kWinding);
     *     builder.setIsVolatile(true);
     *
     *     if (isFillNoPathEffect && SkScalarAbs(sweepAngle) >= 360.f) {
     *         builder.addOval(oval);
     *         SkASSERT(DrawArcIsConvex(sweepAngle, SkArc::Type::kArc, isFillNoPathEffect));
     *         return builder.detach();
     *     }
     *
     *     if (arc.isWedge()) {
     *         builder.moveTo(oval.centerX(), oval.centerY());
     *     }
     *     auto firstDir =
     *             sweepAngle > 0 ? SkPathFirstDirection::kCW : SkPathFirstDirection::kCCW;
     *     bool convex = DrawArcIsConvex(sweepAngle, arc.fType, isFillNoPathEffect);
     *     // Arc to mods at 360 and drawArc is not supposed to.
     *     bool forceMoveTo = !arc.isWedge();
     *     while (sweepAngle <= -360.f) {
     *         builder.arcTo(oval, startAngle, -180.f, forceMoveTo);
     *         startAngle -= 180.f;
     *         builder.arcTo(oval, startAngle, -180.f, false);
     *         startAngle -= 180.f;
     *         forceMoveTo = false;
     *         sweepAngle += 360.f;
     *     }
     *     while (sweepAngle >= 360.f) {
     *         builder.arcTo(oval, startAngle, 180.f, forceMoveTo);
     *         startAngle += 180.f;
     *         builder.arcTo(oval, startAngle, 180.f, false);
     *         startAngle += 180.f;
     *         forceMoveTo = false;
     *         sweepAngle -= 360.f;
     *     }
     *     builder.arcTo(oval, startAngle, sweepAngle, forceMoveTo);
     *     if (arc.isWedge()) {
     *         builder.close();
     *     }
     *
     *     auto path = builder.detach();
     *     const auto convexity = convex ? SkPathFirstDirection_ToConvexity(firstDir)
     *                                   : SkPathConvexity::kConcave;
     *     path.setConvexity(convexity);
     *     return path;
     * }
     * ```
     */
    public fun createDrawArcPath(arc: SkArc, isFillNoPathEffect: Boolean): SkPath {
      TODO("Implement createDrawArcPath")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkPathPriv::DrawArcIsConvex(SkScalar sweepAngle,
     *                                  SkArc::Type arcType,
     *                                  bool isFillNoPathEffect) {
     *     if (isFillNoPathEffect && SkScalarAbs(sweepAngle) >= 360.f) {
     *         // This gets converted to an oval.
     *         return true;
     *     }
     *     if (arcType == SkArc::Type::kWedge) {
     *         // This is a pie wedge. It's convex if the angle is <= 180.
     *         return SkScalarAbs(sweepAngle) <= 180.f;
     *     }
     *     // When the angle exceeds 360 this wraps back on top of itself. Otherwise it is a circle clipped
     *     // to a secant, i.e. convex.
     *     return SkScalarAbs(sweepAngle) <= 360.f;
     * }
     * ```
     */
    public fun drawArcIsConvex(
      sweepAngle: SkScalar,
      arcType: SkArc.Type,
      isFillNoPathEffect: Boolean,
    ): Boolean {
      TODO("Implement drawArcIsConvex")
    }

    /**
     * C++ original:
     * ```cpp
     * SkRect SkPathPriv::ComputeTightBounds(SkSpan<const SkPoint> points,
     *                                       SkSpan<const SkPathVerb> verbs,
     *                                       SkSpan<const float> conicWeights) {
     *     if (verbs.empty()) {
     *         return SkRect::MakeEmpty();
     *     }
     *
     *     // initial with the first MoveTo, so we don't have to check inside the switch
     *     float L = points[0].fX,
     *           T = points[0].fY,
     *           R = points[0].fX,
     *           B = points[0].fY;
     *
     *     for (auto [verb, pts, w] : SkPathPriv::Iterate(verbs, points.data(), conicWeights.data())) {
     *         SkPoint extremas[5]; // big enough to hold worst-case curve type (cubic) extremas + 1
     *         int count = 0;
     *         switch (verb) {
     *             case SkPathVerb::kMove:
     *                 extremas[0] = pts[0];
     *                 count = 1;
     *                 break;
     *             case SkPathVerb::kLine:
     *                 extremas[0] = pts[1];
     *                 count = 1;
     *                 break;
     *             case SkPathVerb::kQuad:
     *                 count = compute_quad_extremas(pts, extremas);
     *                 break;
     *             case SkPathVerb::kConic:
     *                 count = compute_conic_extremas(pts, *w, extremas);
     *                 break;
     *             case SkPathVerb::kCubic:
     *                 count = compute_cubic_extremas(pts, extremas);
     *                 break;
     *             case SkPathVerb::kClose:
     *                 break;
     *         }
     *         for (int i = 0; i < count; ++i) {
     *             SkPoint p = extremas[i];
     *             L = std::fminf(p.fX, L);
     *             T = std::fminf(p.fY, T);
     *             R = std::fmaxf(p.fX, R);
     *             B = std::fmaxf(p.fY, B);
     *         }
     *     }
     *     return {L, T, R, B};
     * }
     * ```
     */
    private fun computeTightBounds(
      points: SkSpan<SkPoint>,
      verbs: SkSpan<SkPathVerb>,
      conicWeights: SkSpan<Float>,
    ): SkRect {
      TODO("Implement computeTightBounds")
    }

    /**
     * C++ original:
     * ```cpp
     * static std::optional<SkPathOvalInfo> IsOval(const SkPath& path) {
     *         return path.getOvalInfo();
     *     }
     * ```
     */
    private fun isOval(path: SkPath): Int {
      TODO("Implement isOval")
    }

    /**
     * C++ original:
     * ```cpp
     * static std::optional<SkPathRRectInfo> IsRRect(const SkPath& path) {
     *         return path.getRRectInfo();
     *     }
     * ```
     */
    private fun isRRect(path: SkPath): Int {
      TODO("Implement isRRect")
    }

    /**
     * C++ original:
     * ```cpp
     * static bool TooBigForMath(const SkRect& bounds) {
     *         // This value is just a guess. smaller is safer, but we don't want to reject largish paths
     *         // that we don't have to.
     *         constexpr SkScalar scale_down_to_allow_for_small_multiplies = 0.25f;
     *         constexpr SkScalar max = SK_ScalarMax * scale_down_to_allow_for_small_multiplies;
     *
     *         // use ! expression so we return true if bounds contains NaN
     *         return !(bounds.fLeft >= -max && bounds.fTop >= -max &&
     *                  bounds.fRight <= max && bounds.fBottom <= max);
     *     }
     * ```
     */
    private fun tooBigForMath(bounds: SkRect): Boolean {
      TODO("Implement tooBigForMath")
    }

    /**
     * C++ original:
     * ```cpp
     * static int PtsInIter(unsigned verb) {
     *         static const uint8_t gPtsInVerb[] = {
     *             1,  // kMove    pts[0]
     *             2,  // kLine    pts[0..1]
     *             3,  // kQuad    pts[0..2]
     *             3,  // kConic   pts[0..2]
     *             4,  // kCubic   pts[0..3]
     *             0,  // kClose
     *             0   // kDone
     *         };
     *
     *         SkASSERT(verb < std::size(gPtsInVerb));
     *         return gPtsInVerb[verb];
     *     }
     * ```
     */
    private fun ptsInIter(verb: UInt): Int {
      TODO("Implement ptsInIter")
    }

    /**
     * C++ original:
     * ```cpp
     * static int PtsInIter(SkPathVerb verb) { return PtsInIter((unsigned)verb); }
     * ```
     */
    private fun ptsInIter(verb: SkPathVerb): Int {
      TODO("Implement ptsInIter")
    }

    /**
     * C++ original:
     * ```cpp
     * static int PtsInVerb(unsigned verb) {
     *         static const uint8_t gPtsInVerb[] = {
     *             1,  // kMove    pts[0]
     *             1,  // kLine    pts[0..1]
     *             2,  // kQuad    pts[0..2]
     *             2,  // kConic   pts[0..2]
     *             3,  // kCubic   pts[0..3]
     *             0,  // kClose
     *             0   // kDone
     *         };
     *
     *         SkASSERT(verb < std::size(gPtsInVerb));
     *         return gPtsInVerb[verb];
     *     }
     * ```
     */
    private fun ptsInVerb(verb: UInt): Int {
      TODO("Implement ptsInVerb")
    }

    /**
     * C++ original:
     * ```cpp
     * static int PtsInVerb(SkPathVerb verb) { return PtsInVerb((unsigned)verb); }
     * ```
     */
    private fun ptsInVerb(verb: SkPathVerb): Int {
      TODO("Implement ptsInVerb")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkPathPriv::IsAxisAligned(SkSpan<const SkPoint> pts) {
     *     // Conservative (quick) test to see if all segments are axis-aligned.
     *     // Multiple contours might give a false-negative, but for speed, we ignore that
     *     // and just look at the raw points.
     *
     *     for (size_t i = 1; i < pts.size(); ++i) {
     *         if (pts[i-1].fX != pts[i].fX && pts[i-1].fY != pts[i].fY) {
     *             return false;
     *         }
     *     }
     *     return true;
     * }
     * ```
     */
    private fun isAxisAligned(pts: SkSpan<SkPoint>): Boolean {
      TODO("Implement isAxisAligned")
    }

    /**
     * C++ original:
     * ```cpp
     * static bool AllPointsEq(SkSpan<const SkPoint> pts) {
     *         for (size_t i = 1; i < pts.size(); ++i) {
     *             if (pts[0] != pts[i]) {
     *                 return false;
     *             }
     *         }
     *         return true;
     *     }
     * ```
     */
    private fun allPointsEq(pts: SkSpan<SkPoint>): Boolean {
      TODO("Implement allPointsEq")
    }

    /**
     * C++ original:
     * ```cpp
     * std::optional<SkPathPriv::RectContour> SkPathPriv::IsRectContour(SkSpan<const SkPoint> ptSpan,
     *                                                                  SkSpan<const SkPathVerb> vbSpan,
     *                                                                  uint32_t segmentMask,
     *                                                                  bool allowPartial) {
     *     if (segmentMask != kLine_SkPathSegmentMask ||
     *         ptSpan.size() < 4 ||
     *         vbSpan.size() < 4) {
     *         return {};
     *     }
     *
     *     if (auto rc = trivial_rect(ptSpan, vbSpan)) {
     *         return rc;
     *     }
     *
     *     size_t currVerb = 0;
     *     const size_t verbCnt = vbSpan.size();
     *
     *     int corners = 0;
     *     SkPoint closeXY;  // used to determine if final line falls on a diagonal
     *     SkPoint lineStart;  // used to construct line from previous point
     *     const SkPoint* firstPt = nullptr; // first point in the rect (last of first moves)
     *     const SkPoint* lastPt = nullptr;  // last point in the rect (last of lines or first if closed)
     *     SkPoint firstCorner;
     *     SkPoint thirdCorner;
     *     const SkPoint* pts = ptSpan.data();
     *     const SkPoint* savePts = nullptr; // used to allow caller to iterate through a pair of rects
     *     lineStart.set(0, 0);
     *     signed char directions[] = {-1, -1, -1, -1, -1};  // -1 to 3; -1 is uninitialized
     *     bool closedOrMoved = false;
     *     bool autoClose = false;
     *     bool insertClose = false;
     *     while (currVerb < verbCnt && (!allowPartial || !autoClose)) {
     *         SkPathVerb verb = insertClose ? SkPathVerb::kClose : vbSpan[currVerb];
     *         switch (verb) {
     *             case SkPathVerb::kClose:
     *                 savePts = pts;
     *                 autoClose = true;
     *                 insertClose = false;
     *                 [[fallthrough]];
     *             case SkPathVerb::kLine: {
     *                 if (SkPathVerb::kClose != verb) {
     *                     lastPt = pts;
     *                 }
     *                 SkPoint lineEnd = SkPathVerb::kClose == verb ? *firstPt : *pts++;
     *                 SkVector lineDelta = lineEnd - lineStart;
     *                 if (lineDelta.fX && lineDelta.fY) {
     *                     return {}; // diagonal
     *                 }
     *                 if (!lineDelta.isFinite()) {
     *                     return {}; // path contains infinity or NaN
     *                 }
     *                 if (lineStart == lineEnd) {
     *                     break; // single point on side OK
     *                 }
     *                 int nextDirection = rect_make_dir(lineDelta.fX, lineDelta.fY); // 0 to 3
     *                 if (0 == corners) {
     *                     directions[0] = nextDirection;
     *                     corners = 1;
     *                     closedOrMoved = false;
     *                     lineStart = lineEnd;
     *                     break;
     *                 }
     *                 if (closedOrMoved) {
     *                     return {}; // closed followed by a line
     *                 }
     *                 if (autoClose && nextDirection == directions[0]) {
     *                     break; // colinear with first
     *                 }
     *                 closedOrMoved = autoClose;
     *                 if (directions[corners - 1] == nextDirection) {
     *                     if (3 == corners && SkPathVerb::kLine == verb) {
     *                         thirdCorner = lineEnd;
     *                     }
     *                     lineStart = lineEnd;
     *                     break; // colinear segment
     *                 }
     *                 directions[corners++] = nextDirection;
     *                 // opposite lines must point in opposite directions; xoring them should equal 2
     *                 switch (corners) {
     *                     case 2:
     *                         firstCorner = lineStart;
     *                         break;
     *                     case 3:
     *                         if ((directions[0] ^ directions[2]) != 2) {
     *                             return {};
     *                         }
     *                         thirdCorner = lineEnd;
     *                         break;
     *                     case 4:
     *                         if ((directions[1] ^ directions[3]) != 2) {
     *                             return {};
     *                         }
     *                         break;
     *                     default:
     *                         return {}; // too many direction changes
     *                 }
     *                 lineStart = lineEnd;
     *                 break;
     *             }
     *             case SkPathVerb::kQuad:
     *             case SkPathVerb::kConic:
     *             case SkPathVerb::kCubic:
     *                 return {}; // quadratic, cubic not allowed
     *             case SkPathVerb::kMove:
     *                 if (allowPartial && !autoClose && directions[0] >= 0) {
     *                     insertClose = true;
     *                     currVerb -= 1;  // try move again afterwards
     *                     goto addMissingClose;
     *                 }
     *                 if (!corners) {
     *                     firstPt = pts;
     *                 } else {
     *                     closeXY = *firstPt - *lastPt;
     *                     if (closeXY.fX && closeXY.fY) {
     *                         return {};   // we're diagonal, abort
     *                     }
     *                 }
     *                 lineStart = *pts++;
     *                 closedOrMoved = true;
     *                 break;
     *             default:
     *                 SkDEBUGFAIL("unexpected verb");
     *                 break;
     *         }
     *         currVerb += 1;
     *     addMissingClose:
     *         ;
     *     }
     *     // Success if 4 corners and first point equals last
     *     if (corners < 3 || corners > 4) {
     *         return {};
     *     }
     *     // check if close generates diagonal
     *     closeXY = *firstPt - *lastPt;
     *     if (closeXY.fX && closeXY.fY) {
     *         return {};
     *     }
     *
     *     auto bounds = [](SkPoint a, SkPoint b) {
     *         SkRect r;
     *         r.set(a, b);
     *         return r;
     *     };
     *
     *     return {{
     *         bounds(firstCorner, thirdCorner),
     *         autoClose,
     *         directions[0] == ((directions[1] + 1) & 3) ? SkPathDirection::kCW
     *                                                    : SkPathDirection::kCCW,
     *         savePts ? size_t(savePts - ptSpan.data()) : 0,
     *         currVerb,
     *     }};
     * }
     * ```
     */
    private fun isRectContour(
      ptSpan: SkSpan<SkPoint>,
      vbSpan: SkSpan<SkPathVerb>,
      segmentMask: UInt,
      allowPartial: Boolean,
    ): Int {
      TODO("Implement isRectContour")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkPathPriv::IsNestedFillRects(const SkPathRaw& raw, SkRect rects[2], SkPathDirection dirs[2]) {
     *     SkPathDirection testDirs[2];
     *     SkRect testRects[2];
     *
     *     SkSpan<const SkPoint> pts = raw.points();
     *     SkSpan<const SkPathVerb> vbs = raw.verbs();
     *
     *     auto rc = IsRectContour(pts, vbs, raw.fSegmentMask, true);
     *     if (!rc) {
     *         return false;
     *     }
     *
     *     testDirs[0] = rc->fDirection;
     *     testRects[0] = rc->fRect;
     *     pts = pts.subspan(rc->fPointsConsumed);
     *     vbs = vbs.subspan(rc->fVerbsConsumed);
     *
     *     rc = IsRectContour(pts, vbs, raw.fSegmentMask, false);
     *     if (rc) {
     *         testDirs[1] = rc->fDirection;
     *         testRects[1] = rc->fRect;
     *         if (testRects[0].contains(testRects[1])) {
     *             if (rects) {
     *                 rects[0] = testRects[0];
     *                 rects[1] = testRects[1];
     *             }
     *             if (dirs) {
     *                 dirs[0] = testDirs[0];
     *                 dirs[1] = testDirs[1];
     *             }
     *             return true;
     *         }
     *         if (testRects[1].contains(testRects[0])) {
     *             if (rects) {
     *                 rects[0] = testRects[1];
     *                 rects[1] = testRects[0];
     *             }
     *             if (dirs) {
     *                 dirs[0] = testDirs[1];
     *                 dirs[1] = testDirs[0];
     *             }
     *             return true;
     *         }
     *     }
     *     return false;
     * }
     * ```
     */
    private fun isNestedFillRects(
      raw: SkPathRaw,
      rect: Array<SkRect>,
      dirs: Array<SkPathDirection> = null,
    ): Boolean {
      TODO("Implement isNestedFillRects")
    }

    /**
     * C++ original:
     * ```cpp
     * static bool IsNestedFillRects(const SkPath& path, SkRect rect[2],
     *                                   SkPathDirection dirs[2] = nullptr) {
     *         auto raw = Raw(path, SkResolveConvexity::kNo);
     *         return raw.has_value() && IsNestedFillRects(*raw, rect, dirs);
     *     }
     * ```
     */
    private fun isNestedFillRects(
      path: SkPath,
      rect: Array<SkRect>,
      dirs: Array<SkPathDirection> = null,
    ): Boolean {
      TODO("Implement isNestedFillRects")
    }

    /**
     * C++ original:
     * ```cpp
     * static bool IsInverseFillType(SkPathFillType fill) {
     *         return (static_cast<int>(fill) & 2) != 0;
     *     }
     * ```
     */
    private fun isInverseFillType(fill: SkPathFillType): Boolean {
      TODO("Implement isInverseFillType")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkPathPriv::PerspectiveClip(const SkPath& path, const SkMatrix& matrix, SkPath* clippedPath) {
     *     if (!matrix.hasPerspective()) {
     *         return false;
     *     }
     *
     *     SkHalfPlane plane {
     *         matrix[SkMatrix::kMPersp0],
     *         matrix[SkMatrix::kMPersp1],
     *         matrix[SkMatrix::kMPersp2] - kW0PlaneDistance
     *     };
     *     if (plane.normalize()) {
     *         switch (plane.test(path.getBounds())) {
     *             case SkHalfPlane::kAllPositive:
     *                 return false;
     *             case SkHalfPlane::kMixed: {
     *                 if (auto result = clip(path, plane)) {
     *                     *clippedPath = *result;
     *                 } else {
     *                     *clippedPath = SkPath(); // clipped out (or failed)
     *                 }
     *                 return true;
     *             }
     *             default: break; // handled outside of the switch
     *         }
     *     }
     *     // clipped out (or failed)
     *     *clippedPath = SkPath();
     *     return true;
     * }
     * ```
     */
    private fun perspectiveClip(
      src: SkPath,
      matrix: SkMatrix,
      result: SkPath?,
    ): Boolean {
      TODO("Implement perspectiveClip")
    }

    /**
     * C++ original:
     * ```cpp
     * int SkPathPriv::GenIDChangeListenersCount(const SkPath& path) {
     *     return path.fPathData->genIDChangeListenerCount();
     * }
     * ```
     */
    private fun genIDChangeListenersCount(path: SkPath): Int {
      TODO("Implement genIDChangeListenersCount")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkPathConvexity GetConvexity(const SkPath& path) {
     *         return path.getConvexity();
     *     }
     * ```
     */
    private fun getConvexity(path: SkPath): SkPathConvexity {
      TODO("Implement getConvexity")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkPathConvexity GetConvexityOrUnknown(const SkPath& path) {
     *         return path.getConvexityOrUnknown();
     *     }
     * ```
     */
    private fun getConvexityOrUnknown(path: SkPath): SkPathConvexity {
      TODO("Implement getConvexityOrUnknown")
    }

    /**
     * C++ original:
     * ```cpp
     * static void SetConvexity(const SkPath& path, SkPathConvexity c) {
     *         path.setConvexity(c);
     *     }
     * ```
     */
    private fun setConvexity(path: SkPath, c: SkPathConvexity) {
      TODO("Implement setConvexity")
    }

    /**
     * C++ original:
     * ```cpp
     * static void ForceComputeConvexity(const SkPath& path) {
     *         path.setConvexity(SkPathConvexity::kUnknown);
     *         (void)path.isConvex();
     *     }
     * ```
     */
    private fun forceComputeConvexity(path: SkPath) {
      TODO("Implement forceComputeConvexity")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkPathConvexity GetConvexityOrUnknown(const SkPathData& pdata) {
     *         return pdata.getConvexityOrUnknown();
     *     }
     * ```
     */
    private fun getConvexityOrUnknown(pdata: SkPathData): SkPathConvexity {
      TODO("Implement getConvexityOrUnknown")
    }

    /**
     * C++ original:
     * ```cpp
     * static void ReverseAddPath(SkPathBuilder* builder, const SkPath& reverseMe) {
     *         builder->privateReverseAddPath(reverseMe);
     *     }
     * ```
     */
    private fun reverseAddPath(builder: SkPathBuilder?, reverseMe: SkPath) {
      TODO("Implement reverseAddPath")
    }

    /**
     * C++ original:
     * ```cpp
     * static void ReversePathTo(SkPathBuilder* builder, const SkPath& reverseMe) {
     *         builder->privateReversePathTo(reverseMe);
     *     }
     * ```
     */
    private fun reversePathTo(builder: SkPathBuilder?, reverseMe: SkPath) {
      TODO("Implement reversePathTo")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkPath ReversePath(const SkPath& reverseMe) {
     *         SkPathBuilder bu;
     *         bu.privateReverseAddPath(reverseMe);
     *         return bu.detach();
     *     }
     * ```
     */
    private fun reversePath(reverseMe: SkPath): SkPath {
      TODO("Implement reversePath")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkSpan<const SkPathVerb> GetVerbs(const SkPathBuilder& builder) {
     *         return builder.fVerbs;
     *     }
     * ```
     */
    private fun getVerbs(builder: SkPathBuilder): SkSpan<SkPathVerb> {
      TODO("Implement getVerbs")
    }

    /**
     * C++ original:
     * ```cpp
     * static int CountVerbs(const SkPathBuilder& builder) {
     *         return builder.fVerbs.size();
     *     }
     * ```
     */
    private fun countVerbs(builder: SkPathBuilder): Int {
      TODO("Implement countVerbs")
    }

    /**
     * C++ original:
     * ```cpp
     * static std::optional<SkPathRaw> Raw(const SkPath& path, SkResolveConvexity rc) {
     *         return path.raw(rc);
     *     }
     * ```
     */
    private fun raw(path: SkPath, rc: SkResolveConvexity): Int {
      TODO("Implement raw")
    }

    /**
     * C++ original:
     * ```cpp
     * static std::optional<SkPathRaw> Raw(const SkPathBuilder& builder, SkResolveConvexity rc) {
     *         const auto bounds = builder.computeFiniteBounds();
     *         if (!bounds) {
     *             return {};
     *         }
     *
     *         SkPathConvexity convexity = builder.fConvexity;
     *         if (convexity == SkPathConvexity::kUnknown && rc == SkResolveConvexity::kYes) {
     *             convexity = SkPathPriv::ComputeConvexity(builder.fPts,
     *                                                      builder.fVerbs,
     *                                                      builder.fConicWeights);
     *         }
     *
     *         return SkPathRaw{
     *             builder.points(),
     *             builder.verbs(),
     *             builder.conicWeights(),
     *             *bounds,
     *             builder.fillType(),
     *             convexity,
     *             SkTo<uint8_t>(builder.fSegmentMask),
     *         };
     *     }
     * ```
     */
    private fun raw(builder: SkPathBuilder, rc: SkResolveConvexity): Int {
      TODO("Implement raw")
    }

    /**
     * C++ original:
     * ```cpp
     * static std::optional<SkRect> TrimmedBounds(SkSpan<const SkPoint> pts,
     *                                                SkSpan<const SkPathVerb> vbs) {
     *         // Does a trailing kMove verb contribute to the bounds?
     *         // - only if it is the only verb in the path
     *         // - otherwise we ignore it when computing bounds
     *         if (vbs.size() > 1 && vbs.back() == SkPathVerb::kMove) {
     *             SkASSERT(pts.size() > 0);
     *             // While trailing moves do not contribute to the bounds, we still reject them.
     *             if (!pts.back().isFinite()) {
     *                 return {};
     *             }
     *             pts = pts.subspan(0, pts.size() - 1);
     *         }
     *         return SkRect::Bounds(pts);
     *     }
     * ```
     */
    private fun trimmedBounds(pts: SkSpan<SkPoint>, vbs: SkSpan<SkPathVerb>): Int {
      TODO("Implement trimmedBounds")
    }
  }
}
