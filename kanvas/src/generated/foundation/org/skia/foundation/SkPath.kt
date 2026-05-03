package org.skia.foundation

import kotlin.Any
import kotlin.Array
import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import kotlin.UByte
import kotlin.UInt
import kotlin.ULong
import kotlin.Unit
import org.skia.core.SkPathData
import org.skia.core.SkPathOvalInfo
import org.skia.core.SkPathRRectInfo
import org.skia.core.SkPathRaw
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
 * class SK_API SkPath {
 * public:
 *     /**
 *      *  Create a new path with the specified spans.
 *      *
 *      *  The points and weights arrays are read in order, based on the sequence of verbs.
 *      *
 *      *  Move    1 point
 *      *  Line    1 point
 *      *  Quad    2 points
 *      *  Conic   2 points and 1 weight
 *      *  Cubic   3 points
 *      *  Close   0 points
 *      *
 *      *  If an illegal sequence of verbs is encountered, or the specified number of points
 *      *  or weights is not sufficient given the verbs, an empty Path is returned.
 *      *
 *      *  A legal sequence of verbs consists of any number of Contours. A contour always begins
 *      *  with a Move verb, followed by 0 or more segments: Line, Quad, Conic, Cubic, followed
 *      *  by an optional Close.
 *      */
 *     static SkPath Raw(SkSpan<const SkPoint> pts,
 *                       SkSpan<const SkPathVerb> verbs,
 *                       SkSpan<const SkScalar> conics,
 *                       SkPathFillType, bool isVolatile = false);
 *
 *     static SkPath Rect(const SkRect&, SkPathFillType, SkPathDirection = SkPathDirection::kDefault,
 *                        unsigned startIndex = 0);
 *     static SkPath Rect(const SkRect& r, SkPathDirection direction = SkPathDirection::kDefault,
 *                        unsigned startIndex = 0) {
 *         return Rect(r, SkPathFillType::kDefault, direction, startIndex);
 *     }
 *     static SkPath Oval(const SkRect&, SkPathDirection = SkPathDirection::kDefault);
 *     static SkPath Oval(const SkRect&, SkPathDirection, unsigned startIndex);
 *     static SkPath Circle(SkScalar center_x, SkScalar center_y, SkScalar radius,
 *                          SkPathDirection dir = SkPathDirection::kCW);
 *     static SkPath RRect(const SkRRect&, SkPathDirection dir = SkPathDirection::kDefault);
 *     static SkPath RRect(const SkRRect&, SkPathDirection, unsigned startIndex);
 *     static SkPath RRect(const SkRect& bounds, SkScalar rx, SkScalar ry,
 *                         SkPathDirection dir = SkPathDirection::kDefault);
 *
 *     static SkPath Polygon(SkSpan<const SkPoint> pts, bool isClosed,
 *                           SkPathFillType fillType = SkPathFillType::kDefault,
 *                           bool isVolatile = false);
 *
 *     static SkPath Line(SkPoint a, SkPoint b) {
 *         return Polygon({{a, b}}, false);
 *     }
 *
 *     // Deprecated: use Raw()
 *     static SkPath Make(SkSpan<const SkPoint> pts,
 *                        SkSpan<const uint8_t> verbs,
 *                        SkSpan<const SkScalar> conics,
 *                        SkPathFillType fillType,
 *                        bool isVolatile = false) {
 *         return Raw(pts, {reinterpret_cast<const SkPathVerb*>(verbs.data()), verbs.size()},
 *                    conics, fillType, isVolatile);
 *     }
 *
 *     /** Constructs an empty SkPath: no verbs, no points, no conic weights.
 *
 *         @return  empty SkPath
 *
 *         example: https://fiddle.skia.org/c/@Path_empty_constructor
 *     */
 *     explicit SkPath(SkPathFillType);
 *
 *     SkPath() : SkPath(SkPathFillType::kDefault) {}
 *
 *     /** Constructs a copy of an existing path.
 *         Copy constructor makes two paths identical by value. Internally, path and
 *         the returned result share pointer values. The underlying verb array, SkPoint array
 *         and weights are copied when modified.
 *
 *         Creating a SkPath copy is very efficient and never allocates memory.
 *         SkPath are always copied by value from the interface; the underlying shared
 *         pointers are not exposed.
 *
 *         @param path  SkPath to copy by value
 *         @return      copy of SkPath
 *
 *         example: https://fiddle.skia.org/c/@Path_copy_const_SkPath
 *     */
 *     SkPath(const SkPath& path);
 *
 *     /** Releases ownership of any shared data and deletes data if SkPath is sole owner.
 *
 *         example: https://fiddle.skia.org/c/@Path_destructor
 *     */
 *     ~SkPath();
 *
 *     /** Returns a copy of this path in the current state. */
 *     SkPath snapshot() const {
 *         return *this;
 *     }
 *
 *     /** Constructs a copy of an existing path.
 *         SkPath assignment makes two paths identical by value. Internally, assignment
 *         shares pointer values. The underlying verb array, SkPoint array and weights
 *         are copied when modified.
 *
 *         Copying SkPath by assignment is very efficient and never allocates memory.
 *         SkPath are always copied by value from the interface; the underlying shared
 *         pointers are not exposed.
 *
 *         @param path  verb array, SkPoint array, weights, and SkPath::FillType to copy
 *         @return      SkPath copied by value
 *
 *         example: https://fiddle.skia.org/c/@Path_copy_operator
 *     */
 *     SkPath& operator=(const SkPath& path);
 *
 *     /** Compares a and b; returns true if SkPath::FillType, verb array, SkPoint array, and weights
 *         are equivalent.
 *
 *         @param a  SkPath to compare
 *         @param b  SkPath to compare
 *         @return   true if SkPath pair are equivalent
 *     */
 *     friend SK_API bool operator==(const SkPath& a, const SkPath& b);
 *
 *     /** Compares a and b; returns true if SkPath::FillType, verb array, SkPoint array, and weights
 *         are not equivalent.
 *
 *         @param a  SkPath to compare
 *         @param b  SkPath to compare
 *         @return   true if SkPath pair are not equivalent
 *     */
 *     friend bool operator!=(const SkPath& a, const SkPath& b) {
 *         return !(a == b);
 *     }
 *
 * // Note: These 3 interpolate() methods no long use any private access/info,
 * //       and could trivially be implemented directly by the client.
 *
 *     /** Returns true if SkPath contain equal verbs and equal weights.
 *         If SkPath contain one or more conics, the weights must match.
 *
 *         conicTo() may add different verbs depending on conic weight, so it is not
 *         trivial to interpolate a pair of SkPath containing conics with different
 *         conic weight values.
 *
 *         @param compare  SkPath to compare
 *         @return         true if SkPath verb array and weights are equivalent
 *
 *         example: https://fiddle.skia.org/c/@Path_isInterpolatable
 *     */
 *     bool isInterpolatable(const SkPath& compare) const;
 *
 *     /** Interpolates between SkPath with SkPoint array of equal size.
 *         Copy verb array and weights to out, and set out SkPoint array to a weighted
 *         average of this SkPoint array and ending SkPoint array, using the formula:
 *         (Path Point * weight) + ending Point * (1 - weight).
 *
 *         weight is most useful when between zero (ending SkPoint array) and
 *         one (this Point_Array); will work with values outside of this
 *         range.
 *
 *         interpolate() returns an empty SkPath if SkPoint array is not the same size
 *         as ending SkPoint array. Call isInterpolatable() to check SkPath compatibility
 *         prior to calling makeInterpolate().
 *
 *         @param ending  SkPoint array averaged with this SkPoint array
 *         @param weight  contribution of this SkPoint array, and
 *                        one minus contribution of ending SkPoint array
 *         @return        SkPath replaced by interpolated averages
 *
 *         example: https://fiddle.skia.org/c/@Path_interpolate
 *     */
 *     SkPath makeInterpolate(const SkPath& ending, SkScalar weight) const;
 *
 *     /** Interpolates between SkPath with SkPoint array of equal size.
 *         Copy verb array and weights to out, and set out SkPoint array to a weighted
 *         average of this SkPoint array and ending SkPoint array, using the formula:
 *         (Path Point * weight) + ending Point * (1 - weight).
 *
 *         weight is most useful when between zero (ending SkPoint array) and
 *         one (this Point_Array); will work with values outside of this
 *         range.
 *
 *         interpolate() returns false and leaves out unchanged if SkPoint array is not
 *         the same size as ending SkPoint array. Call isInterpolatable() to check SkPath
 *         compatibility prior to calling interpolate().
 *
 *         @param ending  SkPoint array averaged with this SkPoint array
 *         @param weight  contribution of this SkPoint array, and
 *                        one minus contribution of ending SkPoint array
 *         @param out     SkPath replaced by interpolated averages
 *         @return        true if SkPath contain same number of SkPoint
 *
 *         example: https://fiddle.skia.org/c/@Path_interpolate
 *     */
 *     bool interpolate(const SkPath& ending, SkScalar weight, SkPath* out) const;
 *
 *     /** Returns SkPathFillType, the rule used to fill SkPath.
 *
 *         @return  current SkPathFillType setting
 *     */
 *     SkPathFillType getFillType() const { return (SkPathFillType)fFillType; }
 *
 *     /** Creates an SkPath with the same properties and data, and with SkPathFillType
 *         set to newFillType.
 *     */
 *     SkPath makeFillType(SkPathFillType newFillType) const;
 *
 *     /** Returns if SkPathFillType describes area outside SkPath geometry. The inverse fill area
 *         extends indefinitely.
 *
 *         @return  true if FillType is kInverseWinding or kInverseEvenOdd
 *     */
 *     bool isInverseFillType() const { return SkPathFillType_IsInverse(this->getFillType()); }
 *
 *     /** Creates an SkPath with the same properties and data, and with SkPathFillType
 *         replaced with its inverse.  The inverse of SkPathFillType describes the area unmodified
 *         by the original FillType.
 *     */
 *     SkPath makeToggleInverseFillType() const;
 *
 *     /** Returns true if the path is convex. If necessary, it will first compute the convexity.
 *      */
 *     bool isConvex() const;
 *
 *     /** Returns true if this path is recognized as an oval or circle.
 *
 *         bounds receives bounds of oval.
 *
 *         bounds is unmodified if oval is not found.
 *
 *         @param bounds  storage for bounding SkRect of oval; may be nullptr
 *         @return        true if SkPath is recognized as an oval or circle
 *
 *         example: https://fiddle.skia.org/c/@Path_isOval
 *     */
 *     bool isOval(SkRect* bounds) const;
 *
 *     /** Returns true if path is representable as SkRRect.
 *         Returns false if path is representable as oval, circle, or SkRect.
 *
 *         rrect receives bounds of SkRRect.
 *
 *         rrect is unmodified if SkRRect is not found.
 *
 *         @param rrect  storage for bounding SkRect of SkRRect; may be nullptr
 *         @return       true if SkPath contains only SkRRect
 *
 *         example: https://fiddle.skia.org/c/@Path_isRRect
 *     */
 *     bool isRRect(SkRRect* rrect) const;
 *
 *     /** Returns if SkPath is empty.
 *         Empty SkPath may have FillType but has no SkPoint, SkPath::Verb, or conic weight.
 *         SkPath() constructs empty SkPath; reset() and rewind() make SkPath empty.
 *
 *         @return  true if the path contains no SkPath::Verb array
 *     */
 *     bool isEmpty() const;
 *
 *     /** Returns if contour is closed.
 *         Contour is closed if SkPath SkPath::Verb array was last modified by close(). When stroked,
 *         closed contour draws SkPaint::Join instead of SkPaint::Cap at first and last SkPoint.
 *
 *         @return  true if the last contour ends with a kClose_Verb
 *
 *         example: https://fiddle.skia.org/c/@Path_isLastContourClosed
 *     */
 *     bool isLastContourClosed() const;
 *
 *     /** Returns true for finite SkPoint array values between negative SK_ScalarMax and
 *         positive SK_ScalarMax. Returns false for any SkPoint array value of
 *         SK_ScalarInfinity, SK_ScalarNegativeInfinity, or SK_ScalarNaN.
 *
 *         @return  true if all SkPoint values are finite
 *     */
 *     bool isFinite() const;
 *
 *     /** Returns true if the path is volatile; it will not be altered or discarded
 *         by the caller after it is drawn. SkPath by default have volatile set false, allowing
 *         SkSurface to attach a cache of data which speeds repeated drawing. If true, SkSurface
 *         may not speed repeated drawing.
 *
 *         @return  true if caller will alter SkPath after drawing
 *     */
 *     bool isVolatile() const {
 *         return SkToBool(fIsVolatile);
 *     }
 *
 *     /** Return a copy of SkPath with isVolatile indicating whether it will be altered
 *         or discarded by the caller after it is drawn. SkPath by default have volatile
 *         set false, allowing Skia to attach a cache of data which speeds repeated drawing.
 *
 *         Mark temporary paths, discarded or modified after use, as volatile
 *         to inform Skia that the path need not be cached.
 *
 *         Mark animating SkPath volatile to improve performance.
 *         Mark unchanging SkPath non-volatile to improve repeated rendering.
 *
 *         raster surface SkPath draws are affected by volatile for some shadows.
 *         GPU surface SkPath draws are affected by volatile for some shadows and concave geometries.
 *
 *         @param isVolatile  true if caller will alter SkPath after drawing
 *         @return            SkPath
 *     */
 *     SkPath makeIsVolatile(bool isVolatile) const;
 *
 *     /** Tests if line between SkPoint pair is degenerate.
 *         Line with no length or that moves a very short distance is degenerate; it is
 *         treated as a point.
 *
 *         exact changes the equality test. If true, returns true only if p1 equals p2.
 *         If false, returns true if p1 equals or nearly equals p2.
 *
 *         @param p1     line start point
 *         @param p2     line end point
 *         @param exact  if false, allow nearly equals
 *         @return       true if line is degenerate; its length is effectively zero
 *
 *         example: https://fiddle.skia.org/c/@Path_IsLineDegenerate
 *     */
 *     static bool IsLineDegenerate(const SkPoint& p1, const SkPoint& p2, bool exact);
 *
 *     /** Tests if quad is degenerate.
 *         Quad with no length or that moves a very short distance is degenerate; it is
 *         treated as a point.
 *
 *         @param p1     quad start point
 *         @param p2     quad control point
 *         @param p3     quad end point
 *         @param exact  if true, returns true only if p1, p2, and p3 are equal;
 *                       if false, returns true if p1, p2, and p3 are equal or nearly equal
 *         @return       true if quad is degenerate; its length is effectively zero
 *     */
 *     static bool IsQuadDegenerate(const SkPoint& p1, const SkPoint& p2,
 *                                  const SkPoint& p3, bool exact);
 *
 *     /** Tests if cubic is degenerate.
 *         Cubic with no length or that moves a very short distance is degenerate; it is
 *         treated as a point.
 *
 *         @param p1     cubic start point
 *         @param p2     cubic control point 1
 *         @param p3     cubic control point 2
 *         @param p4     cubic end point
 *         @param exact  if true, returns true only if p1, p2, p3, and p4 are equal;
 *                       if false, returns true if p1, p2, p3, and p4 are equal or nearly equal
 *         @return       true if cubic is degenerate; its length is effectively zero
 *     */
 *     static bool IsCubicDegenerate(const SkPoint& p1, const SkPoint& p2,
 *                                   const SkPoint& p3, const SkPoint& p4, bool exact);
 *
 *     /** Returns true if SkPath contains only one line;
 *         SkPath::Verb array has two entries: kMove_Verb, kLine_Verb.
 *         If SkPath contains one line and line is not nullptr, line is set to
 *         line start point and line end point.
 *         Returns false if SkPath is not one line; line is unaltered.
 *
 *         @param line  storage for line. May be nullptr
 *         @return      true if SkPath contains exactly one line
 *
 *         example: https://fiddle.skia.org/c/@Path_isLine
 *     */
 *     bool isLine(SkPoint line[2]) const;
 *
 *     /*
 *      *  Return a read-only view into the path's points.
 *      */
 *     SkSpan<const SkPoint> points() const;
 *
 *     /*
 *      *  Return a read-only view into the path's verbs.
 *      */
 *     SkSpan<const SkPathVerb> verbs() const;
 *
 *     /*
 *      *  Return a read-only view into the path's conic-weights.
 *      */
 *     SkSpan<const float> conicWeights() const;
 *
 *     int countPoints() const { return SkToInt(this->points().size()); }
 *     int countVerbs() const { return SkToInt(this->verbs().size()); }
 *
 *     /** Return the last point, or {}
 *
 *         @return The last if the path contains one or more SkPoint, else returns {}
 *
 *         example: https://fiddle.skia.org/c/@Path_getLastPt
 *     */
 *     std::optional<SkPoint> getLastPt() const;
 *
 * #ifdef SK_LEGACY_PATH_ACCESSORS
 *     /** Returns SkPoint at index in SkPoint array. Valid range for index is
 *         0 to countPoints() - 1.
 *         Returns (0, 0) if index is out of range.
 *         DEPRECATED
 *         @param index  SkPoint array element selector
 *         @return       SkPoint array value or (0, 0)
 *     */
 *     SkPoint getPoint(int index) const;
 *
 *     /** Returns number of points in SkPath.
 *         Copies N points from the path into the span, where N = min(#points, span capacity)
 *         DEPRECATED
 *         @param points  span to receive the points. may be empty
 *         @return the number of points in the path
 *     */
 *     size_t getPoints(SkSpan<SkPoint> points) const;
 *
 *     /** Returns number of points in SkPath.
 *         Copies N points from the path into the span, where N = min(#points, span capacity)
 *         DEPRECATED
 *         @param verbs span to store the verbs. may be empty.
 *         @return the number of verbs in the path
 *
 *         example: https://fiddle.skia.org/c/@Path_getVerbs
 *     */
 *     size_t getVerbs(SkSpan<uint8_t> verbs) const;
 *
 *     // DEPRECATED
 *     bool getLastPt(SkPoint* lastPt) const {
 *         if (auto lp = this->getLastPt()) {
 *             if (lastPt) {
 *                 *lastPt = *lp;
 *             }
 *             return true;
 *         }
 *         if (lastPt) {
 *             *lastPt = {0, 0};
 *         }
 *         return false;
 *     }
 * #endif
 *
 *     /** Returns the approximate byte size of the SkPath in memory.
 *
 *         @return  approximate size
 *     */
 *     size_t approximateBytesUsed() const;
 *
 *     /** Returns the min/max of the path's 'trimmed' points. The trimmed points are all of the
 *         points in the path, with the exception of the path having more than one contour, and the
 *         final contour containing only a kMove verb. In that case the trailing kMove point
 *         is ignored when computing the bounds.
 *
 *         If the path has no verbs, or the path contains non-finite values,
 *         then {0, 0, 0, 0} is returned. (see isFinite())
 *
 *         @return  bounds of the path's points
 *     */
 *     const SkRect& getBounds() const;
 *
 *     /** Updates internal bounds so that subsequent calls to getBounds() are instantaneous.
 *         Unaltered copies of SkPath may also access cached bounds through getBounds().
 *
 *         For now, identical to calling getBounds() and ignoring the returned value.
 *
 *         Call to prepare SkPath subsequently drawn from multiple threads,
 *         to avoid a race condition where each draw separately computes the bounds.
 *     */
 *     void updateBoundsCache() const {
 *         // for now, just calling getBounds() is sufficient
 *         this->getBounds();
 *     }
 *
 *     /** Returns minimum and maximum axes values of the lines and curves in SkPath.
 *         Returns (0, 0, 0, 0) if SkPath contains no points.
 *         Returned bounds width and height may be larger or smaller than area affected
 *         when SkPath is drawn.
 *
 *         Includes SkPoint associated with kMove_Verb that define empty
 *         contours.
 *
 *         Behaves identically to getBounds() when SkPath contains
 *         only lines. If SkPath contains curves, computed bounds includes
 *         the maximum extent of the quad, conic, or cubic; is slower than getBounds();
 *         and unlike getBounds(), does not cache the result.
 *
 *         @return  tight bounds of curves in SkPath
 *
 *         example: https://fiddle.skia.org/c/@Path_computeTightBounds
 *     */
 *     SkRect computeTightBounds() const;
 *
 *     /** Returns true if rect is contained by SkPath.
 *         May return false when rect is contained by SkPath.
 *
 *         For now, only returns true if SkPath has one contour and is convex.
 *         rect may share points and edges with SkPath and be contained.
 *         Returns true if rect is empty, that is, it has zero width or height; and
 *         the SkPoint or line described by rect is contained by SkPath.
 *
 *         @param rect  SkRect, line, or SkPoint checked for containment
 *         @return      true if rect is contained
 *
 *         example: https://fiddle.skia.org/c/@Path_conservativelyContainsRect
 *     */
 *     bool conservativelyContainsRect(const SkRect& rect) const;
 *
 *     /** \enum SkPath::ArcSize
 *         Four oval parts with radii (rx, ry) start at last SkPath SkPoint and ends at (x, y).
 *         ArcSize and Direction select one of the four oval parts.
 *     */
 *     enum ArcSize {
 *         kSmall_ArcSize, //!< smaller of arc pair
 *         kLarge_ArcSize, //!< larger of arc pair
 *     };
 *
 *     /** Approximates conic with quad array. Conic is constructed from start SkPoint p0,
 *         control SkPoint p1, end SkPoint p2, and weight w.
 *         Quad array is stored in pts; this storage is supplied by caller.
 *         Maximum quad count is 2 to the pow2.
 *         Every third point in array shares last SkPoint of previous quad and first SkPoint of
 *         next quad. Maximum pts storage size is given by:
 *         (1 + 2 * (1 << pow2)) * sizeof(SkPoint).
 *
 *         Returns quad count used the approximation, which may be smaller
 *         than the number requested.
 *
 *         conic weight determines the amount of influence conic control point has on the curve.
 *         w less than one represents an elliptical section. w greater than one represents
 *         a hyperbolic section. w equal to one represents a parabolic section.
 *
 *         Two quad curves are sufficient to approximate an elliptical conic with a sweep
 *         of up to 90 degrees; in this case, set pow2 to one.
 *
 *         @param p0    conic start SkPoint
 *         @param p1    conic control SkPoint
 *         @param p2    conic end SkPoint
 *         @param w     conic weight
 *         @param pts   storage for quad array
 *         @param pow2  quad count, as power of two, normally 0 to 5 (1 to 32 quad curves)
 *         @return      number of quad curves written to pts
 *     */
 *     static int ConvertConicToQuads(const SkPoint& p0, const SkPoint& p1, const SkPoint& p2,
 *                                    SkScalar w, SkPoint pts[], int pow2);
 *
 *     /** Returns true if SkPath is equivalent to SkRect when filled.
 *         If false: rect, isClosed, and direction are unchanged.
 *         If true: rect, isClosed, and direction are written to if not nullptr.
 *
 *         rect may be smaller than the SkPath bounds. SkPath bounds may include kMove_Verb points
 *         that do not alter the area drawn by the returned rect.
 *
 *         @param rect       storage for bounds of SkRect; may be nullptr
 *         @param isClosed   storage set to true if SkPath is closed; may be nullptr
 *         @param direction  storage set to SkRect direction; may be nullptr
 *         @return           true if SkPath contains SkRect
 *
 *         example: https://fiddle.skia.org/c/@Path_isRect
 *     */
 *     bool isRect(SkRect* rect, bool* isClosed = nullptr, SkPathDirection* direction = nullptr) const;
 *
 *     /** \enum SkPath::AddPathMode
 *         AddPathMode chooses how addPath() appends. Adding one SkPath to another can extend
 *         the last contour or start a new contour.
 *     */
 *     enum AddPathMode {
 *         /** Contours are appended to the destination path as new contours.
 *         */
 *         kAppend_AddPathMode,
 *         /** Extends the last contour of the destination path with the first countour
 *             of the source path, connecting them with a line.  If the last contour is
 *             closed, a new empty contour starting at its start point is extended instead.
 *             If the destination path is empty, the result is the source path.
 *             The last path of the result is closed only if the last path of the source is.
 *         */
 *         kExtend_AddPathMode,
 *     };
 *
 *     /** Return a copy of SkPath with verb array, SkPoint array, and weight transformed
 *         by matrix. makeTransform may change verbs and increase their number.
 *
 *         If the resulting path has any non-finite values, returns {}.
 *
 *         @param matrix  SkMatrix to apply to SkPath
 *         @return        SkPath if finite, or {}
 *     */
 *     std::optional<SkPath> tryMakeTransform(const SkMatrix& matrix) const;
 *
 *     std::optional<SkPath> tryMakeOffset(float dx, float dy) const {
 *         return this->tryMakeTransform(SkMatrix::Translate(dx, dy));
 *     }
 *
 *     std::optional<SkPath> tryMakeScale(float sx, float sy) const {
 *         return this->tryMakeTransform(SkMatrix::Scale(sx, sy));
 *     }
 *
 *     /** Return a copy of SkPath with verb array, SkPoint array, and weight transformed
 *         by matrix. makeTransform may change verbs and increase their number.
 *
 *         If the resulting path has any non-finite values, this will still return a path
 *         but that path will return true for isFinite().
 *
 *         The newer pattern is to call tryMakeTransform(matrix) which will only return a
 *         path if the result is finite.
 *
 *         @param matrix  SkMatrix to apply to SkPath
 *         @return        SkPath
 *     */
 *     SkPath makeTransform(const SkMatrix& matrix) const;
 *
 *     /** Returns SkPath with SkPoint array offset by (dx, dy).
 *
 *         @param dx  offset added to SkPoint array x-axis coordinates
 *         @param dy  offset added to SkPoint array y-axis coordinates
 *     */
 *     SkPath makeOffset(SkScalar dx, SkScalar dy) const {
 *         return this->makeTransform(SkMatrix::Translate(dx, dy));
 *     }
 *
 *     SkPath makeScale(SkScalar sx, SkScalar sy) const {
 *         return this->makeTransform(SkMatrix::Scale(sx, sy));
 *     }
 *
 *     /** \enum SkPath::SegmentMask
 *         SegmentMask constants correspond to each drawing Verb type in SkPath; for
 *         instance, if SkPath only contains lines, only the kLine_SegmentMask bit is set.
 *     */
 *     enum SegmentMask {
 *         kLine_SegmentMask  = kLine_SkPathSegmentMask,
 *         kQuad_SegmentMask  = kQuad_SkPathSegmentMask,
 *         kConic_SegmentMask = kConic_SkPathSegmentMask,
 *         kCubic_SegmentMask = kCubic_SkPathSegmentMask,
 *     };
 *
 *     /** Returns a mask, where each set bit corresponds to a SegmentMask constant
 *         if SkPath contains one or more verbs of that type.
 *         Returns zero if SkPath contains no lines, or curves: quads, conics, or cubics.
 *
 *         getSegmentMasks() returns a cached result; it is very fast.
 *
 *         @return  SegmentMask bits or zero
 *     */
 *     uint32_t getSegmentMasks() const;
 *
 *     /** \enum SkPath::Verb
 *         Verb instructs SkPath how to interpret one or more SkPoint and optional conic weight;
 *         manage contour, and terminate SkPath.
 *     */
 *     enum Verb {
 *         kMove_Verb  = static_cast<int>(SkPathVerb::kMove),
 *         kLine_Verb  = static_cast<int>(SkPathVerb::kLine),
 *         kQuad_Verb  = static_cast<int>(SkPathVerb::kQuad),
 *         kConic_Verb = static_cast<int>(SkPathVerb::kConic),
 *         kCubic_Verb = static_cast<int>(SkPathVerb::kCubic),
 *         kClose_Verb = static_cast<int>(SkPathVerb::kClose),
 *         kDone_Verb  = kClose_Verb + 1
 *     };
 *
 *     /** Specifies whether SkPath is volatile; whether it will be altered or discarded
 *         by the caller after it is drawn. SkPath by default have volatile set false, allowing
 *         Skia to attach a cache of data which speeds repeated drawing.
 *
 *         Mark temporary paths, discarded or modified after use, as volatile
 *         to inform Skia that the path need not be cached.
 *
 *         Mark animating SkPath volatile to improve performance.
 *         Mark unchanging SkPath non-volatile to improve repeated rendering.
 *
 *         raster surface SkPath draws are affected by volatile for some shadows.
 *         GPU surface SkPath draws are affected by volatile for some shadows and concave geometries.
 *
 *         @param isVolatile  true if caller will alter SkPath after drawing
 *         @return            reference to SkPath
 *     */
 *     SkPath& setIsVolatile(bool isVolatile) {
 *         fIsVolatile = isVolatile;
 *         return *this;
 *     }
 *
 *     /** Exchanges the verb array, SkPoint array, weights, and SkPath::FillType with other.
 *         Cached state is also exchanged. swap() internally exchanges pointers, so
 *         it is lightweight and does not allocate memory.
 *
 *         swap() usage has largely been replaced by operator=(const SkPath& path).
 *         SkPath do not copy their content on assignment until they are written to,
 *         making assignment as efficient as swap().
 *
 *         @param other  SkPath exchanged by value
 *
 *         example: https://fiddle.skia.org/c/@Path_swap
 *     */
 *     void swap(SkPath& other);
 *
 *     /** Sets SkPathFillType, the rule used to fill SkPath. While there is no
 *         check that ft is legal, values outside of SkPathFillType are not supported.
 *     */
 *     void setFillType(SkPathFillType ft) {
 *         fFillType = ft;
 *     }
 *
 *     /** Replaces SkPathFillType with its inverse. The inverse of SkPathFillType describes the area
 *         unmodified by the original SkPathFillType.
 *     */
 *     void toggleInverseFillType() {
 *         fFillType = SkPathFillType_ToggleInverse(fFillType);
 *     }
 *
 *     /** Sets SkPath to its initial state.
 *         Removes verb array, SkPoint array, and weights, and sets FillType to kWinding.
 *         Internal storage associated with SkPath is released.
 *
 *         @return  reference to SkPath
 *
 *         example: https://fiddle.skia.org/c/@Path_reset
 *     */
 *     SkPath& reset();
 *
 * #ifdef SK_SUPPORT_UNSPANNED_APIS
 *     static SkPath Make(const SkPoint points[], int pointCount,
 *                        const uint8_t verbs[], int verbCount,
 *                        const SkScalar conics[], int conicWeightCount,
 *                        SkPathFillType fillType, bool isVolatile = false) {
 *         return Make({points, pointCount},
 *                     {verbs, verbCount},
 *                     {conics, conicWeightCount},
 *                     fillType, isVolatile);
 *     }
 *     static SkPath Polygon(const SkPoint pts[], int count, bool isClosed,
 *                           SkPathFillType fillType = SkPathFillType::kWinding,
 *                           bool isVolatile = false) {
 *         return Polygon({pts, count}, isClosed, fillType, isVolatile);
 *     }
 *     int getPoints(SkPoint points[], int max) const {
 *         return (int)this->getPoints({points, max});
 *     }
 *     int getVerbs(uint8_t verbs[], int max) const {
 *         return (int)this->getVerbs({verbs, max});
 *     }
 * #endif  // SK_SUPPORT_UNSPANNED_APIS
 *
 *     SkPathIter iter() const;
 *
 *     struct IterRec {
 *         SkPathVerb            fVerb;
 *         SkSpan<const SkPoint> fPoints;
 *         float                 fConicWeight;
 *
 *         float conicWeight() const {
 *             SkASSERT(fVerb == SkPathVerb::kConic);
 *             return fConicWeight;
 *         }
 *     };
 *
 *     /** \class SkPath::Iter
 *         Iterates through verb array, and associated SkPoint array and conic weight.
 *         Provides options to treat open contours as closed, and to ignore
 *         degenerate data.
 *     */
 *     class SK_API Iter {
 *     public:
 *
 *         /** Initializes SkPath::Iter with an empty SkPath. next() on SkPath::Iter returns
 *             kDone_Verb.
 *             Call setPath to initialize SkPath::Iter at a later time.
 *
 *             @return  SkPath::Iter of empty SkPath
 *
 *         example: https://fiddle.skia.org/c/@Path_Iter_Iter
 *         */
 *         Iter();
 *
 *         /** Sets SkPath::Iter to return elements of verb array, SkPoint array, and conic weight in
 *             path. If forceClose is true, SkPath::Iter will add kLine_Verb and kClose_Verb after each
 *             open contour. path is not altered.
 *
 *             @param path        SkPath to iterate
 *             @param forceClose  true if open contours generate kClose_Verb
 *             @return            SkPath::Iter of path
 *
 *         example: https://fiddle.skia.org/c/@Path_Iter_const_SkPath
 *         */
 *         Iter(const SkPath& path, bool forceClose);
 *
 *         /** Sets SkPath::Iter to return elements of verb array, SkPoint array, and conic weight in
 *             path. If forceClose is true, SkPath::Iter will add kLine_Verb and kClose_Verb after each
 *             open contour. path is not altered.
 *
 *             @param path        SkPath to iterate
 *             @param forceClose  true if open contours generate kClose_Verb
 *
 *         example: https://fiddle.skia.org/c/@Path_Iter_setPath
 *         */
 *         void setPath(const SkPath& path, bool forceClose);
 *
 *         /** Returns next SkPath::Verb in verb array, and advances SkPath::Iter.
 *             When verb array is exhausted, returns kDone_Verb.
 *
 *             Zero to four SkPoint are stored in pts, depending on the returned SkPath::Verb.
 *
 *             @param pts  storage for SkPoint data describing returned SkPath::Verb
 *             @return     next SkPath::Verb from verb array
 *
 *         example: https://fiddle.skia.org/c/@Path_RawIter_next
 *         */
 *         Verb next(SkPoint pts[4]);
 *
 *         std::optional<IterRec> next();
 *
 *         /** Returns conic weight if next() returned kConic_Verb.
 *
 *             If next() has not been called, or next() did not return kConic_Verb,
 *             result is undefined.
 *
 *             @return  conic weight for conic SkPoint returned by next()
 *         */
 *         SkScalar conicWeight() const { return *fConicWeights; }
 *
 *         /** Returns true if last kLine_Verb returned by next() was generated
 *             by kClose_Verb. When true, the end point returned by next() is
 *             also the start point of contour.
 *
 *             If next() has not been called, or next() did not return kLine_Verb,
 *             result is undefined.
 *
 *             @return  true if last kLine_Verb was generated by kClose_Verb
 *         */
 *         bool isCloseLine() const { return SkToBool(fCloseLine); }
 *
 *         /** Returns true if subsequent calls to next() return kClose_Verb before returning
 *             kMove_Verb. if true, contour SkPath::Iter is processing may end with kClose_Verb, or
 *             SkPath::Iter may have been initialized with force close set to true.
 *
 *             @return  true if contour is closed
 *
 *         example: https://fiddle.skia.org/c/@Path_Iter_isClosedContour
 *         */
 *         bool isClosedContour() const;
 *
 *     private:
 *         const SkPoint*          fPts;
 *         const SkPathVerb*       fVerbs;
 *         const SkPathVerb*       fVerbStop;
 *         const SkScalar*         fConicWeights;
 *         SkPoint                 fMoveTo;
 *         SkPoint                 fLastPt;
 *         std::array<SkPoint, 4>  fStorage;
 *         bool                    fForceClose;
 *         bool                    fNeedClose;
 *         bool                    fCloseLine;
 *
 *         SkPathVerb autoClose(SkPoint pts[2]);
 *     };
 *
 * private:
 *     std::optional<SkPathOvalInfo> getOvalInfo() const;
 *     std::optional<SkPathRRectInfo> getRRectInfo() const;
 *     std::optional<SkPathRaw> raw(SkResolveConvexity) const;
 *
 *     /** \class SkPath::RangeIter
 *         Iterates through a raw range of path verbs, points, and conics. All values are returned
 *         unaltered.
 *
 *         NOTE: This class will be moved into SkPathPriv once RangeIter is removed.
 *     */
 *     class RangeIter {
 *     public:
 *         RangeIter() = default;
 *         RangeIter(const SkPathVerb* verbs, const SkPoint* points, const SkScalar* weights)
 *                 : fVerb(verbs), fPoints(points), fWeights(weights) {
 *             SkDEBUGCODE(fInitialPoints = fPoints;)
 *         }
 *         bool operator!=(const RangeIter& that) const {
 *             return fVerb != that.fVerb;
 *         }
 *         bool operator==(const RangeIter& that) const {
 *             return fVerb == that.fVerb;
 *         }
 *         RangeIter& operator++() {
 *             auto verb = *fVerb++;
 *             fPoints += pts_advance_after_verb(verb);
 *             if (verb == SkPathVerb::kConic) {
 *                 ++fWeights;
 *             }
 *             return *this;
 *         }
 *         RangeIter operator++(int) {
 *             RangeIter copy = *this;
 *             this->operator++();
 *             return copy;
 *         }
 *         SkPathVerb peekVerb() const {
 *             return *fVerb;
 *         }
 *         std::tuple<SkPathVerb, const SkPoint*, const SkScalar*> operator*() const {
 *             SkPathVerb verb = this->peekVerb();
 *             // We provide the starting point for beziers by peeking backwards from the current
 *             // point, which works fine as long as there is always a kMove before any geometry.
 *             // (SkPath::injectMoveToIfNeeded should have guaranteed this to be the case.)
 *             int backset = pts_backset_for_verb(verb);
 *             SkASSERT(fPoints + backset >= fInitialPoints);
 *             return {verb, fPoints + backset, fWeights};
 *         }
 *     private:
 *         constexpr static int pts_advance_after_verb(SkPathVerb verb) {
 *             switch (verb) {
 *                 case SkPathVerb::kMove: return 1;
 *                 case SkPathVerb::kLine: return 1;
 *                 case SkPathVerb::kQuad: return 2;
 *                 case SkPathVerb::kConic: return 2;
 *                 case SkPathVerb::kCubic: return 3;
 *                 case SkPathVerb::kClose: return 0;
 *             }
 *             SkUNREACHABLE;
 *         }
 *         constexpr static int pts_backset_for_verb(SkPathVerb verb) {
 *             switch (verb) {
 *                 case SkPathVerb::kMove: return 0;
 *                 case SkPathVerb::kLine: return -1;
 *                 case SkPathVerb::kQuad: return -1;
 *                 case SkPathVerb::kConic: return -1;
 *                 case SkPathVerb::kCubic: return -1;
 *                 case SkPathVerb::kClose: return -1;
 *             }
 *             SkUNREACHABLE;
 *         }
 *         const SkPathVerb* fVerb = nullptr;
 *         const SkPoint* fPoints = nullptr;
 *         const SkScalar* fWeights = nullptr;
 *         SkDEBUGCODE(const SkPoint* fInitialPoints = nullptr;)
 *     };
 * public:
 *
 *     /** \class SkPath::RawIter
 *         Use Iter instead. This class will soon be removed and RangeIter will be made private.
 *     */
 *     class SK_API RawIter {
 *     public:
 *
 *         /** Initializes RawIter with an empty SkPath. next() on RawIter returns kDone_Verb.
 *             Call setPath to initialize SkPath::Iter at a later time.
 *
 *             @return  RawIter of empty SkPath
 *         */
 *         RawIter() {}
 *
 *         /** Sets RawIter to return elements of verb array, SkPoint array, and conic weight in path.
 *
 *             @param path  SkPath to iterate
 *             @return      RawIter of path
 *         */
 *         RawIter(const SkPath& path) {
 *             setPath(path);
 *         }
 *
 *         /** Sets SkPath::Iter to return elements of verb array, SkPoint array, and conic weight in
 *             path.
 *
 *             @param path  SkPath to iterate
 *         */
 *         void setPath(const SkPath&);
 *
 *         /** Returns next SkPath::Verb in verb array, and advances RawIter.
 *             When verb array is exhausted, returns kDone_Verb.
 *             Zero to four SkPoint are stored in pts, depending on the returned SkPath::Verb.
 *
 *             @param pts  storage for SkPoint data describing returned SkPath::Verb
 *             @return     next SkPath::Verb from verb array
 *         */
 *         Verb next(SkPoint[4]);
 *
 *         std::optional<IterRec> next();
 *
 *         /** Returns next SkPath::Verb, but does not advance RawIter.
 *
 *             @return  next SkPath::Verb from verb array
 *         */
 *         Verb peek() const {
 *             return (fIter != fEnd) ? static_cast<Verb>(std::get<0>(*fIter)) : kDone_Verb;
 *         }
 *
 *         /** Returns conic weight if next() returned kConic_Verb.
 *
 *             If next() has not been called, or next() did not return kConic_Verb,
 *             result is undefined.
 *
 *             @return  conic weight for conic SkPoint returned by next()
 *         */
 *         SkScalar conicWeight() const {
 *             return fConicWeight;
 *         }
 *
 *     private:
 *         RangeIter fIter;
 *         RangeIter fEnd;
 *         SkScalar fConicWeight = 0;
 *         friend class SkPath;
 *
 *     };
 *
 *     /** Returns true if the point is contained by SkPath, taking into
 *         account FillType.
 *
 *         @param point the point to test
 *         @return true if SkPoint is in SkPath
 *     */
 *     bool contains(SkPoint point) const;
 *
 *     // deprecated
 *     bool contains(SkScalar x, SkScalar y) const {
 *         return this->contains({x, y});
 *     }
 *
 *     /** Writes text representation of SkPath to stream. If stream is nullptr, writes to
 *         standard output. Set dumpAsHex true to generate exact binary representations
 *         of floating point numbers used in SkPoint array and conic weights.
 *
 *         @param stream      writable SkWStream receiving SkPath text representation; may be nullptr
 *         @param dumpAsHex   true if SkScalar values are written as hexadecimal
 *
 *         example: https://fiddle.skia.org/c/@Path_dump
 *     */
 *     void dump(SkWStream* stream, bool dumpAsHex) const;
 *
 *     void dump() const { this->dump(nullptr, false); }
 *     void dumpHex() const { this->dump(nullptr, true); }
 *
 *     /** Writes SkPath to buffer, returning the number of bytes written.
 *         Pass nullptr to obtain the storage size.
 *
 *         Writes SkPath::FillType, verb array, SkPoint array, conic weight, and
 *         additionally writes computed information like SkPath::Convexity and bounds.
 *
 *         Use only be used in concert with readFromMemory();
 *         the format used for SkPath in memory is not guaranteed.
 *
 *         @param buffer  storage for SkPath; may be nullptr
 *         @return        size of storage required for SkPath; always a multiple of 4
 *
 *         example: https://fiddle.skia.org/c/@Path_writeToMemory
 *     */
 *     size_t writeToMemory(void* buffer) const;
 *
 *     /** Writes SkPath to buffer, returning the buffer written to, wrapped in SkData.
 *
 *         serialize() writes SkPath::FillType, verb array, SkPoint array, conic weight, and
 *         additionally writes computed information like SkPath::Convexity and bounds.
 *
 *         serialize() should only be used in concert with readFromMemory().
 *         The format used for SkPath in memory is not guaranteed.
 *
 *         @return  SkPath data wrapped in SkData buffer
 *
 *         example: https://fiddle.skia.org/c/@Path_serialize
 *     */
 *     sk_sp<SkData> serialize() const;
 *
 *     /** Returns a SkPath from buffer of size length. If the buffer data is inconsistent, or the
 *         length is too small, returns a nullopt.
 *
 *         Reads SkPath::FillType, verb array, SkPoint array, conic weight, and
 *         additionally reads computed information like SkPath::Convexity and bounds.
 *
 *         Used only in concert with writeToMemory();
 *         the format used for SkPath in memory is not guaranteed.
 *
 *         @param buffer    storage for SkPath
 *         @param length    buffer size in bytes; must be multiple of 4
 *         @param bytesRead if not null, the number of bytes read from buffer will be written here
 *         @return          the path read, or nullopt on failure
 *
 *         example: https://fiddle.skia.org/c/@Path_readFromMemory
 *     */
 *     static std::optional<SkPath> ReadFromMemory(const void* buffer, size_t length,
 *                                                 size_t* bytesRead = nullptr);
 *
 *     /** (See skbug.com/40032862)
 *         Returns a non-zero, globally unique value. A different value is returned
 *         if verb array, SkPoint array, or conic weight changes.
 *
 *         Setting SkPath::FillType does not change generation identifier.
 *
 *         Each time the path is modified, a different generation identifier will be returned.
 *         SkPath::FillType does affect generation identifier on Android framework.
 *
 *         @return  non-zero, globally unique value
 *
 *         example: https://fiddle.skia.org/c/@Path_getGenerationID
 *     */
 *     uint32_t getGenerationID() const;
 *
 *     /** Returns if SkPath data is consistent. Corrupt SkPath data is detected if
 *         internal values are out of range or internal storage does not match
 *         array dimensions.
 *
 *         @return  true if SkPath data is consistent
 *     */
 *     bool isValid() const;
 *
 *     using sk_is_trivially_relocatable = std::true_type;
 *
 * private:
 *     static SkPath MakeNullCheck(sk_sp<SkPathData>, SkPathFillType, bool isVolatile);
 *     static SkPathData* PeekErrorSingleton();
 *
 *     SkPath(sk_sp<SkPathData>, SkPathFillType, bool isVolatile);
 *
 *     sk_sp<SkPathData> fPathData;
 *     SkPathFillType    fFillType;
 *     bool              fIsVolatile;
 *
 *     size_t writeToMemoryAsRRect(void* buffer) const;
 *
 *     friend class Iter;
 *     friend class SkPathPriv;
 *     friend class SkPathStroker;
 *
 *     SkPathConvexity computeConvexity() const;
 *
 *     bool isValidImpl() const;
 *     /** Asserts if SkPath data is inconsistent.
 *         Debugging check intended for internal use only.
 *      */
 * #ifdef SK_DEBUG
 *     void validate() const;
 * #endif
 *
 *     /** Returns the comvexity type, computing if needed. Never returns kUnknown.
 *         @return  path's convexity type (convex or concave)
 *     */
 *     SkPathConvexity getConvexity() const;
 *
 *     SkPathConvexity getConvexityOrUnknown() const;
 *
 *     /** Stores a convexity type for this path. This is what will be returned if
 *      *  getConvexityOrUnknown() is called. If you pass kUnknown, then if getContexityType()
 *      *  is called, the real convexity will be computed.
 *      */
 *     void setConvexity(SkPathConvexity) const;
 *
 *     friend class SkPathBuilder;
 * }
 * ```
 */
public abstract class SkPath public constructor(
  ft: SkPathFillType,
) {
  /**
   * C++ original:
   * ```cpp
   * SkPath(sk_sp<SkPathData>, SkPathFillType, bool isVolatile)
   * ```
   */
  private var skSp: SkPath = TODO("Initialize skSp")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkPathData> fPathData
   * ```
   */
  private var fPathData: Int = TODO("Initialize fPathData")

  /**
   * C++ original:
   * ```cpp
   * SkPathFillType    fFillType
   * ```
   */
  private var fFillType: Int = TODO("Initialize fFillType")

  /**
   * C++ original:
   * ```cpp
   * bool              fIsVolatile
   * ```
   */
  private var fIsVolatile: Boolean = TODO("Initialize fIsVolatile")

  /**
   * C++ original:
   * ```cpp
   * explicit SkPath(SkPathFillType)
   * ```
   */
  public constructor() : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPath() : SkPath(SkPathFillType::kDefault) {}
   * ```
   */
  public constructor(path: SkPath) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPath::SkPath(const SkPath& that)
   *     : fPathData(that.fPathData)
   *     , fFillType(that.fFillType)
   *     , fIsVolatile(that.fIsVolatile)
   * {}
   * ```
   */
  public constructor(
    pd: SkSp<SkPathData>,
    ft: SkPathFillType,
    isVolatile: Boolean,
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPath snapshot() const {
   *         return *this;
   *     }
   * ```
   */
  public fun snapshot(): SkPath {
    TODO("Implement snapshot")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPath& SkPath::operator=(const SkPath& o) {
   *     if (this != &o) {
   *         fPathData   = o.fPathData;
   *         fFillType   = o.fFillType;
   *         fIsVolatile = o.fIsVolatile;
   *     }
   *     return *this;
   * }
   * ```
   */
  public fun assign(path: SkPath) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkPath::isInterpolatable(const SkPath& compare) const {
   *     return CanInterpolate(*this, compare);
   * }
   * ```
   */
  public fun isInterpolatable(compare: SkPath): Boolean {
    TODO("Implement isInterpolatable")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPath SkPath::makeInterpolate(const SkPath& ending, SkScalar weight) const {
   *     return Interpolate(*this, ending, 1 - weight).value_or(SkPath());
   * }
   * ```
   */
  public fun makeInterpolate(ending: SkPath, weight: SkScalar): SkPath {
    TODO("Implement makeInterpolate")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkPath::interpolate(const SkPath& ending, SkScalar weight, SkPath* out) const {
   *     if (auto result = Interpolate(*this, ending, 1 - weight)) {
   *         *out = *result;
   *         return true;
   *     }
   *     return false;
   * }
   * ```
   */
  public fun interpolate(
    ending: SkPath,
    weight: SkScalar,
    `out`: SkPath?,
  ): Boolean {
    TODO("Implement interpolate")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathFillType getFillType() const { return (SkPathFillType)fFillType; }
   * ```
   */
  public fun getFillType(): Int {
    TODO("Implement getFillType")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPath SkPath::makeFillType(SkPathFillType ft) const {
   *     SkPath copy = *this;
   *     copy.setFillType(ft);
   *     return copy;
   * }
   * ```
   */
  public fun makeFillType(newFillType: SkPathFillType): SkPath {
    TODO("Implement makeFillType")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isInverseFillType() const { return SkPathFillType_IsInverse(this->getFillType()); }
   * ```
   */
  public fun isInverseFillType(): Boolean {
    TODO("Implement isInverseFillType")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPath SkPath::makeToggleInverseFillType() const {
   *     return this->makeFillType(SkPathFillType_ToggleInverse(fFillType));
   * }
   * ```
   */
  public fun makeToggleInverseFillType(): SkPath {
    TODO("Implement makeToggleInverseFillType")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkPath::isConvex() const {
   *     return SkPathConvexity_IsConvex(this->getConvexity());
   * }
   * ```
   */
  public fun isConvex(): Boolean {
    TODO("Implement isConvex")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkPath::isOval(SkRect* bounds) const {
   *     if (auto info = this->getOvalInfo()) {
   *         if (bounds) {
   *             *bounds = info->fBounds;
   *         }
   *         return true;
   *     }
   *     return false;
   * }
   * ```
   */
  public fun isOval(bounds: SkRect?): Boolean {
    TODO("Implement isOval")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkPath::isRRect(SkRRect* rrect) const {
   *     if (auto info = this->getRRectInfo()) {
   *         if (rrect) {
   *             *rrect = info->fRRect;
   *         }
   *         return true;
   *     }
   *     return false;
   * }
   * ```
   */
  public fun isRRect(rrect: SkRRect?): Boolean {
    TODO("Implement isRRect")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkPath::isEmpty() const {
   *     SkDEBUGCODE(this->validate();)
   *     return this->verbs().empty();
   * }
   * ```
   */
  public fun isEmpty(): Boolean {
    TODO("Implement isEmpty")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkPath::isLastContourClosed() const {
   *     SkSpan<const SkPathVerb> verbs = this->verbs();
   *     return !verbs.empty() && verbs.back() == SkPathVerb::kClose;
   * }
   * ```
   */
  public fun isLastContourClosed(): Boolean {
    TODO("Implement isLastContourClosed")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkPath::isFinite() const {
   *     return fPathData.get() != PeekErrorSingleton();
   * }
   * ```
   */
  public fun isFinite(): Boolean {
    TODO("Implement isFinite")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isVolatile() const {
   *         return SkToBool(fIsVolatile);
   *     }
   * ```
   */
  public fun isVolatile(): Boolean {
    TODO("Implement isVolatile")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPath SkPath::makeIsVolatile(bool v) const {
   *     SkPath copy = *this;
   *     copy.fIsVolatile = v;
   *     return copy;
   * }
   * ```
   */
  public fun makeIsVolatile(isVolatile: Boolean): SkPath {
    TODO("Implement makeIsVolatile")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkPath::isLine(SkPoint line[2]) const {
   *     SkSpan<const SkPathVerb> verbs = this->verbs();
   *     if (verbs.size() == 2 && verbs[1] == SkPathVerb::kLine) {
   *         SkASSERT(verbs[0] == SkPathVerb::kMove);
   *         SkSpan<const SkPoint> pts = this->points();
   *         SkASSERT(pts.size() == 2);
   *         if (line) {
   *             line[0] = pts[0];
   *             line[1] = pts[1];
   *         }
   *         return true;
   *     }
   *     return false;
   * }
   * ```
   */
  public fun isLine(line: Array<SkPoint>): Boolean {
    TODO("Implement isLine")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const SkPoint> SkPath::points() const { return fPathData->points(); }
   * ```
   */
  public fun points(): Int {
    TODO("Implement points")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const SkPathVerb> SkPath::verbs() const { return fPathData->verbs(); }
   * ```
   */
  public fun verbs(): Int {
    TODO("Implement verbs")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const float> SkPath::conicWeights() const { return fPathData->conics(); }
   * ```
   */
  public fun conicWeights(): Int {
    TODO("Implement conicWeights")
  }

  /**
   * C++ original:
   * ```cpp
   * int countPoints() const { return SkToInt(this->points().size()); }
   * ```
   */
  public fun countPoints(): Int {
    TODO("Implement countPoints")
  }

  /**
   * C++ original:
   * ```cpp
   * int countVerbs() const { return SkToInt(this->verbs().size()); }
   * ```
   */
  public fun countVerbs(): Int {
    TODO("Implement countVerbs")
  }

  /**
   * C++ original:
   * ```cpp
   * std::optional<SkPoint> SkPath::getLastPt() const {
   *     SkDEBUGCODE(this->validate();)
   *     SkSpan<const SkPoint> pts = this->points();
   *     if (!pts.empty()) {
   *         return pts.back();
   *     }
   *     return {};
   * }
   * ```
   */
  public fun getLastPt(): Int {
    TODO("Implement getLastPt")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPoint SkPath::getPoint(int index) const {
   *     SkSpan<const SkPoint> pts = this->points();
   *     if ((unsigned)index < (unsigned)pts.size()) {
   *         return pts[index];
   *     }
   *     return SkPoint::Make(0, 0);
   * }
   * ```
   */
  public fun getPoint(index: Int): Int {
    TODO("Implement getPoint")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkPath::getPoints(SkSpan<SkPoint> dst) const {
   *     SkDEBUGCODE(this->validate();)
   *     SkSpan<const SkPoint> src = this->points();
   *
   *     const size_t n = std::min(dst.size(), src.size());
   *     sk_careful_memcpy(dst.data(), src.data(), n * sizeof(SkPoint));
   *     return src.size();
   * }
   * ```
   */
  public fun getPoints(points: SkSpan<SkPoint>): ULong {
    TODO("Implement getPoints")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkPath::getVerbs(SkSpan<uint8_t> dst) const {
   *     SkDEBUGCODE(this->validate();)
   *     SkSpan<const SkPathVerb> src = this->verbs();
   *
   *     const size_t n = std::min(dst.size(), src.size());
   *     sk_careful_memcpy(dst.data(), src.data(), n);
   *     return src.size();
   * }
   * ```
   */
  public fun getVerbs(verbs: SkSpan<UByte>): ULong {
    TODO("Implement getVerbs")
  }

  /**
   * C++ original:
   * ```cpp
   * bool getLastPt(SkPoint* lastPt) const {
   *         if (auto lp = this->getLastPt()) {
   *             if (lastPt) {
   *                 *lastPt = *lp;
   *             }
   *             return true;
   *         }
   *         if (lastPt) {
   *             *lastPt = {0, 0};
   *         }
   *         return false;
   *     }
   * ```
   */
  public fun getLastPt(lastPt: SkPoint?): Boolean {
    TODO("Implement getLastPt")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkPath::approximateBytesUsed() const {
   *     return sizeof(SkPath)
   *          + this->points().size_bytes()
   *          + this->verbs().size_bytes()
   *          + this->conicWeights().size_bytes();
   * }
   * ```
   */
  public fun approximateBytesUsed(): ULong {
    TODO("Implement approximateBytesUsed")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkRect& SkPath::getBounds() const {
   *     return fPathData->bounds();
   * }
   * ```
   */
  public fun getBounds(): Int {
    TODO("Implement getBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void updateBoundsCache() const {
   *         // for now, just calling getBounds() is sufficient
   *         this->getBounds();
   *     }
   * ```
   */
  public fun updateBoundsCache() {
    TODO("Implement updateBoundsCache")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect SkPath::computeTightBounds() const {
   *     // If we're only lines, then our (quick) bounds is also tight.
   *     if (this->getSegmentMasks() == SkPath::kLine_SegmentMask) {
   *         return this->getBounds();
   *     }
   *
   *     return SkPathPriv::ComputeTightBounds(this->points(),
   *                                           this->verbs(),
   *                                           this->conicWeights());
   * }
   * ```
   */
  public fun computeTightBounds(): Int {
    TODO("Implement computeTightBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkPath::conservativelyContainsRect(const SkRect& rect) const {
   *     const SkPathConvexity convexity = this->getConvexity();
   *     if (!SkPathConvexity_IsConvex(convexity)) {
   *         return false;
   *     }
   *
   *     const auto direction = SkPathConvexity_ToDirection(convexity);
   *     if (!direction) {
   *         return false;
   *     }
   *
   *     SkPoint firstPt;
   *     SkPoint prevPt;
   *     int segmentCount = 0;
   *     SkDEBUGCODE(int moveCnt = 0;)
   *
   *     for (auto [verb, pts, weight] : SkPathPriv::Iterate(*this)) {
   *         if (verb == SkPathVerb::kClose || (segmentCount > 0 && verb == SkPathVerb::kMove)) {
   *             // Closing the current contour; but since convexity is a precondition, it's the only
   *             // contour that matters.
   *             SkASSERT(moveCnt);
   *             segmentCount++;
   *             break;
   *         } else if (verb == SkPathVerb::kMove) {
   *             // A move at the start of the contour (or multiple leading moves, in which case we
   *             // keep the last one before a non-move verb).
   *             SkASSERT(!segmentCount);
   *             SkDEBUGCODE(++moveCnt);
   *             firstPt = prevPt = pts[0];
   *         } else {
   *             int pointCount = SkPathPriv::PtsInVerb((unsigned) verb);
   *             SkASSERT(pointCount > 0);
   *
   *             if (!SkPathPriv::AllPointsEq({pts, (size_t)pointCount + 1})) {
   *                 SkASSERT(moveCnt);
   *                 int nextPt = pointCount;
   *                 segmentCount++;
   *
   *                 if (prevPt == pts[nextPt]) {
   *                     // A pre-condition to getting here is that the path is convex, so if a
   *                     // verb's start and end points are the same, it means it's the only
   *                     // verb in the contour (and the only contour). While it's possible for
   *                     // such a single verb to be a convex curve, we do not have any non-zero
   *                     // length edges to conservatively test against without splitting or
   *                     // evaluating the curve. For simplicity, just reject the rectangle.
   *                     return false;
   *                 } else if (SkPathVerb::kConic == verb) {
   *                     SkConic orig;
   *                     orig.set(pts, *weight);
   *                     SkPoint quadPts[5];
   *                     int count = orig.chopIntoQuadsPOW2(quadPts, 1);
   *                     SkASSERT_RELEASE(2 == count);
   *
   *                     if (!check_edge_against_rect(quadPts[0], quadPts[2], rect, *direction)) {
   *                         return false;
   *                     }
   *                     if (!check_edge_against_rect(quadPts[2], quadPts[4], rect, *direction)) {
   *                         return false;
   *                     }
   *                 } else {
   *                     if (!check_edge_against_rect(prevPt, pts[nextPt], rect, *direction)) {
   *                         return false;
   *                     }
   *                 }
   *                 prevPt = pts[nextPt];
   *             }
   *         }
   *     }
   *
   *     if (segmentCount) {
   *         return check_edge_against_rect(prevPt, firstPt, rect, *direction);
   *     }
   *     return false;
   * }
   * ```
   */
  public fun conservativelyContainsRect(rect: SkRect): Boolean {
    TODO("Implement conservativelyContainsRect")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkPath::isRect(SkRect* rect, bool* isClosed, SkPathDirection* direction) const {
   *     SkDEBUGCODE(this->validate();)
   *     SkSpan<const SkPoint> pts = this->points();
   *     SkSpan<const SkPathVerb> vbs = this->verbs();
   *     if (auto rc = SkPathPriv::IsRectContour(pts, vbs, this->getSegmentMasks(), false)) {
   *         if (rect) {
   *             *rect = rc->fRect;
   *         }
   *         if (isClosed) {
   *             *isClosed = rc->fIsClosed;
   *         }
   *         if (direction) {
   *             *direction = rc->fDirection;
   *         }
   *         return true;
   *     }
   *     return false;
   * }
   * ```
   */
  public fun isRect(
    rect: SkRect?,
    isClosed: Boolean? = null,
    direction: SkPathDirection? = null,
  ): Boolean {
    TODO("Implement isRect")
  }

  /**
   * C++ original:
   * ```cpp
   * std::optional<SkPath> SkPath::tryMakeTransform(const SkMatrix& matrix) const {
   *     if (auto pdata = fPathData->makeTransform(matrix)) {
   *         return SkPath(std::move(pdata), fFillType, fIsVolatile);
   *     }
   *     return {};
   * }
   * ```
   */
  public fun tryMakeTransform(matrix: SkMatrix): SkPath? {
    TODO("Implement tryMakeTransform")
  }

  /**
   * C++ original:
   * ```cpp
   * std::optional<SkPath> tryMakeOffset(float dx, float dy) const {
   *         return this->tryMakeTransform(SkMatrix::Translate(dx, dy));
   *     }
   * ```
   */
  public fun tryMakeOffset(dx: Float, dy: Float): SkPath? {
    TODO("Implement tryMakeOffset")
  }

  /**
   * C++ original:
   * ```cpp
   * std::optional<SkPath> tryMakeScale(float sx, float sy) const {
   *         return this->tryMakeTransform(SkMatrix::Scale(sx, sy));
   *     }
   * ```
   */
  public fun tryMakeScale(sx: Float, sy: Float): SkPath? {
    TODO("Implement tryMakeScale")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPath SkPath::makeTransform(const SkMatrix& matrix) const {
   *     if (!this->isFinite()) {
   *         return *this;
   *     }
   *     if (auto newpath = this->tryMakeTransform(matrix)) {
   *         return *newpath;
   *     }
   *     return SkPath(sk_ref_sp(PeekErrorSingleton()), fFillType, false);
   * }
   * ```
   */
  public fun makeTransform(matrix: SkMatrix): SkPath {
    TODO("Implement makeTransform")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPath makeOffset(SkScalar dx, SkScalar dy) const {
   *         return this->makeTransform(SkMatrix::Translate(dx, dy));
   *     }
   * ```
   */
  public fun makeOffset(dx: SkScalar, dy: SkScalar): SkPath {
    TODO("Implement makeOffset")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPath makeScale(SkScalar sx, SkScalar sy) const {
   *         return this->makeTransform(SkMatrix::Scale(sx, sy));
   *     }
   * ```
   */
  public fun makeScale(sx: SkScalar, sy: SkScalar): SkPath {
    TODO("Implement makeScale")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t SkPath::getSegmentMasks() const {
   *     return fPathData->segmentMask();
   * }
   * ```
   */
  public fun getSegmentMasks(): UInt {
    TODO("Implement getSegmentMasks")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPath& setIsVolatile(bool isVolatile) {
   *         fIsVolatile = isVolatile;
   *         return *this;
   *     }
   * ```
   */
  public fun setIsVolatile(isVolatile: Boolean): SkPath {
    TODO("Implement setIsVolatile")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPath::swap(SkPath& that) {
   *     if (this != &that) {
   *         fPathData.swap(that.fPathData);
   *         std::swap(fFillType, that.fFillType);
   *         std::swap(fIsVolatile, that.fIsVolatile);
   *     }
   * }
   * ```
   */
  public fun swap(other: SkPath) {
    TODO("Implement swap")
  }

  /**
   * C++ original:
   * ```cpp
   * void setFillType(SkPathFillType ft) {
   *         fFillType = ft;
   *     }
   * ```
   */
  public fun setFillType(ft: SkPathFillType) {
    TODO("Implement setFillType")
  }

  /**
   * C++ original:
   * ```cpp
   * void toggleInverseFillType() {
   *         fFillType = SkPathFillType_ToggleInverse(fFillType);
   *     }
   * ```
   */
  public fun toggleInverseFillType() {
    TODO("Implement toggleInverseFillType")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPath& SkPath::reset() {
   *     *this = SkPath();
   *     return *this;
   * }
   * ```
   */
  public fun reset(): SkPath {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathIter SkPath::iter() const {
   *     return { this->points(), this->verbs(), this->conicWeights() };
   * }
   * ```
   */
  public fun iter(): Int {
    TODO("Implement iter")
  }

  /**
   * C++ original:
   * ```cpp
   * std::optional<SkPathOvalInfo> SkPath::getOvalInfo() const { return fPathData->asOval(); }
   * ```
   */
  private fun getOvalInfo(): SkPathOvalInfo? {
    TODO("Implement getOvalInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * std::optional<SkPathRRectInfo> SkPath::getRRectInfo() const { return fPathData->asRRect(); }
   * ```
   */
  private fun getRRectInfo(): SkPathRRectInfo? {
    TODO("Implement getRRectInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * std::optional<SkPathRaw> SkPath::raw(SkResolveConvexity rc) const {
   *     return fPathData->raw(fFillType, rc);
   * }
   * ```
   */
  private fun raw(rc: SkResolveConvexity): SkPathRaw? {
    TODO("Implement raw")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkPath::contains(SkPoint p) const {
   *     const auto raw = SkPathPriv::Raw(*this, SkResolveConvexity::kNo);
   *     return raw.has_value() && SkPathPriv::Contains(*raw, p);
   * }
   * ```
   */
  private fun contains(point: SkPoint): Boolean {
    TODO("Implement contains")
  }

  /**
   * C++ original:
   * ```cpp
   * bool contains(SkScalar x, SkScalar y) const {
   *         return this->contains({x, y});
   *     }
   * ```
   */
  private fun contains(x: SkScalar, y: SkScalar): Boolean {
    TODO("Implement contains")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPath::dump(SkWStream* wStream, bool dumpAsHex) const {
   *     SkScalarAsStringType asType = dumpAsHex ? kHex_SkScalarAsStringType : kDec_SkScalarAsStringType;
   *
   *     SkString builder;
   *     builder.printf("path.setFillType(SkPathFillType::k%s);\n",
   *             gFillTypeStrs[(int) this->getFillType()]);
   *
   *     dump_iter(this->iter(), &builder, "path", asType, true, [&]() {
   *         if (!wStream && builder.size()) {
   *             SkDebugf("%s", builder.c_str());
   *             builder.reset();
   *         }
   *     });
   *     if (wStream) {
   *         wStream->writeText(builder.c_str());
   *     }
   * }
   * ```
   */
  private fun dump(stream: SkWStream?, dumpAsHex: Boolean) {
    TODO("Implement dump")
  }

  /**
   * C++ original:
   * ```cpp
   * void dump() const { this->dump(nullptr, false); }
   * ```
   */
  private fun dump() {
    TODO("Implement dump")
  }

  /**
   * C++ original:
   * ```cpp
   * void dumpHex() const { this->dump(nullptr, true); }
   * ```
   */
  private fun dumpHex() {
    TODO("Implement dumpHex")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkPath::writeToMemory(void* storage) const {
   *     SkDEBUGCODE(this->validate();)
   *
   *     if (size_t bytes = this->writeToMemoryAsRRect(storage)) {
   *         return bytes;
   *     }
   *
   *     int32_t packed = (static_cast<int>(fFillType) << kFillType_SerializationShift) |
   *                      (SerializationType::kGeneral << kType_SerializationShift) |
   *                      kCurrent_Version;
   *
   *     SkSpan<const SkPoint> points = this->points();
   *     SkSpan<const SkPathVerb> verbs = this->verbs();
   *     SkSpan<const float> conics = this->conicWeights();
   *
   *     int32_t pts = SkToS32(points.size());
   *     int32_t cnx = SkToS32(conics.size());
   *     int32_t vbs = SkToS32(verbs.size());
   *
   *     SkSafeMath safe;
   *     size_t size = 4 * sizeof(int32_t);
   *     size = safe.add(size, safe.mul(pts, sizeof(SkPoint)));
   *     size = safe.add(size, safe.mul(cnx, sizeof(float)));
   *     size = safe.add(size, safe.mul(vbs, sizeof(uint8_t)));
   *     size = safe.alignUp(size, 4);
   *     if (!safe) {
   *         return 0;
   *     }
   *     if (!storage) {
   *         return size;
   *     }
   *
   *     SkWBuffer buffer(storage);
   *     buffer.write32(packed);
   *     buffer.write32(pts);
   *     buffer.write32(cnx);
   *     buffer.write32(vbs);
   *     buffer.write(points.data(), points.size_bytes());
   *     buffer.write(conics.data(), conics.size_bytes());
   *     buffer.write(verbs.data(), verbs.size_bytes());
   *     buffer.padToAlign4();
   *
   *     SkASSERT(buffer.pos() == size);
   *     return size;
   * }
   * ```
   */
  private fun writeToMemory(buffer: Unit?): ULong {
    TODO("Implement writeToMemory")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkData> SkPath::serialize() const {
   *     size_t size = this->writeToMemory(nullptr);
   *     sk_sp<SkData> data = SkData::MakeUninitialized(size);
   *     this->writeToMemory(data->writable_data());
   *     return data;
   * }
   * ```
   */
  private fun serialize(): Int {
    TODO("Implement serialize")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t SkPath::getGenerationID() const { return fPathData->uniqueID(); }
   * ```
   */
  private fun getGenerationID(): UInt {
    TODO("Implement getGenerationID")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkPath::isValid() const { return this->isFinite(); }
   * ```
   */
  private fun isValid(): Boolean {
    TODO("Implement isValid")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkPath::writeToMemoryAsRRect(void* storage) const {
   *     SkRRect rrect;
   *     SkPathDirection firstDir;
   *     unsigned start;
   *
   *     if (auto oinfo = this->getOvalInfo()) {
   *         rrect.setOval(oinfo->fBounds);
   *         firstDir = oinfo->fDirection;
   *         // Convert to rrect start indices.
   *         start = oinfo->fStartIndex * 2;
   *     } else if (auto rinfo = this->getRRectInfo()) {
   *         rrect = rinfo->fRRect;
   *         firstDir = rinfo->fDirection;
   *         start = rinfo->fStartIndex;
   *     } else {
   *         return 0;
   *     }
   *
   *     // packed header, rrect, start index.
   *     const size_t sizeNeeded = sizeof(int32_t) + SkRRect::kSizeInMemory + sizeof(int32_t);
   *     if (!storage) {
   *         return sizeNeeded;
   *     }
   *
   *     int32_t packed = (static_cast<int>(fFillType) << kFillType_SerializationShift) |
   *                      ((int)firstDir << kDirection_SerializationShift) |
   *                      (SerializationType::kRRect << kType_SerializationShift) |
   *                      kCurrent_Version;
   *
   *     SkWBuffer buffer(storage);
   *     buffer.write32(packed);
   *     SkRRectPriv::WriteToBuffer(rrect, &buffer);
   *     buffer.write32(SkToS32(start));
   *     buffer.padToAlign4();
   *     SkASSERT(sizeNeeded == buffer.pos());
   *     return buffer.pos();
   * }
   * ```
   */
  private fun writeToMemoryAsRRect(buffer: Unit?): ULong {
    TODO("Implement writeToMemoryAsRRect")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathConvexity SkPath::computeConvexity() const {
   *     if (auto c = this->getConvexityOrUnknown(); c != SkPathConvexity::kUnknown) {
   *         return c;
   *     }
   *
   *     SkPathConvexity convexity = SkPathConvexity::kConcave;
   *
   *     if (this->isFinite()) {
   *         convexity = SkPathPriv::ComputeConvexity(this->points(),
   *                                                  this->verbs(),
   *                                                  this->conicWeights());
   *     }
   *
   *     SkASSERT(convexity != SkPathConvexity::kUnknown);
   *     this->setConvexity(convexity);
   *     return convexity;
   * }
   * ```
   */
  private fun computeConvexity(): SkPathConvexity {
    TODO("Implement computeConvexity")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isValidImpl() const
   * ```
   */
  private fun isValidImpl(): Boolean {
    TODO("Implement isValidImpl")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathConvexity SkPath::getConvexity() const {
   * // Enable once we fix all the bugs
   * //    SkDEBUGCODE(this->isConvexityAccurate());
   *     SkPathConvexity convexity = this->getConvexityOrUnknown();
   *     if (convexity == SkPathConvexity::kUnknown) {
   *         convexity = this->computeConvexity();
   *     }
   *     SkASSERT(convexity != SkPathConvexity::kUnknown);
   *     return convexity;
   * }
   * ```
   */
  private fun getConvexity(): SkPathConvexity {
    TODO("Implement getConvexity")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathConvexity SkPath::getConvexityOrUnknown() const {
   *     return fPathData->getConvexityOrUnknown();
   * }
   * ```
   */
  private fun getConvexityOrUnknown(): SkPathConvexity {
    TODO("Implement getConvexityOrUnknown")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPath::setConvexity(SkPathConvexity c) const {
   *     fPathData->setConvexity(c);
   * }
   * ```
   */
  private fun setConvexity(c: SkPathConvexity) {
    TODO("Implement setConvexity")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPath::validate() const {}
   * ```
   */
  public fun validate() {
    TODO("Implement validate")
  }

  public data class IterRec public constructor(
    public var fVerb: Int,
    public var fPoints: Int,
    public var fConicWeight: Float,
  ) {
    public fun conicWeight(): Float {
      TODO("Implement conicWeight")
    }
  }

  public open class Iter public constructor() {
    private val fPts: Int? = TODO("Initialize fPts")

    private val fVerbs: Int? = TODO("Initialize fVerbs")

    private val fVerbStop: Int? = TODO("Initialize fVerbStop")

    private val fConicWeights: Int? = TODO("Initialize fConicWeights")

    private var fMoveTo: Int = TODO("Initialize fMoveTo")

    private var fLastPt: Int = TODO("Initialize fLastPt")

    private var fStorage: Int = TODO("Initialize fStorage")

    private var fForceClose: Boolean = TODO("Initialize fForceClose")

    private var fNeedClose: Boolean = TODO("Initialize fNeedClose")

    private var fCloseLine: Boolean = TODO("Initialize fCloseLine")

    public constructor(path: SkPath, forceClose: Boolean) : this() {
      TODO("Implement constructor")
    }

    public fun setPath(path: SkPath, forceClose: Boolean) {
      TODO("Implement setPath")
    }

    public fun next(pts: Array<SkPoint>): undefined.Verb {
      TODO("Implement next")
    }

    public fun next(): IterRec? {
      TODO("Implement next")
    }

    public fun conicWeight(): Int {
      TODO("Implement conicWeight")
    }

    public fun isCloseLine(): Boolean {
      TODO("Implement isCloseLine")
    }

    public fun isClosedContour(): Boolean {
      TODO("Implement isClosedContour")
    }

    private fun autoClose(pts: Array<SkPoint>): Int {
      TODO("Implement autoClose")
    }
  }

  public data class RangeIter public constructor(
    private val fVerb: Int?,
    private val fPoints: Int?,
    private val fWeights: Int?,
  ) {
    public override operator fun equals(other: Any?): Boolean {
      TODO("Implement equals")
    }

    public operator fun inc(): undefined.RangeIter {
      TODO("Implement inc")
    }

    public fun peekVerb(): Int {
      TODO("Implement peekVerb")
    }

    private fun skDEBUGCODE(param0: Int?): Int {
      TODO("Implement skDEBUGCODE")
    }

    public companion object {
      private fun ptsAdvanceAfterVerb(verb: SkPathVerb): Int {
        TODO("Implement ptsAdvanceAfterVerb")
      }

      private fun ptsBacksetForVerb(verb: SkPathVerb): Int {
        TODO("Implement ptsBacksetForVerb")
      }
    }
  }

  public data class RawIter public constructor(
    private var fIter: undefined.RangeIter,
    private var fEnd: undefined.RangeIter,
    private var fConicWeight: Int,
  ) {
    public fun setPath(path: SkPath) {
      TODO("Implement setPath")
    }

    public fun next(pts: Array<SkPoint>): undefined.Verb {
      TODO("Implement next")
    }

    public fun next(): IterRec? {
      TODO("Implement next")
    }

    public fun peek(): undefined.Verb {
      TODO("Implement peek")
    }

    public fun conicWeight(): Int {
      TODO("Implement conicWeight")
    }
  }

  public enum class ArcSize {
    kSmall_ArcSize,
    kLarge_ArcSize,
  }

  public enum class AddPathMode {
    kAppend_AddPathMode,
    kExtend_AddPathMode,
  }

  public enum class SegmentMask {
    kLine_SegmentMask,
    kQuad_SegmentMask,
    kConic_SegmentMask,
    kCubic_SegmentMask,
  }

  public enum class Verb {
    kMove_Verb,
    kLine_Verb,
    kQuad_Verb,
    kConic_Verb,
    kCubic_Verb,
    kClose_Verb,
    kDone_Verb,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * SkPath SkPath::Raw(SkSpan<const SkPoint> pts, SkSpan<const SkPathVerb> vbs,
     *                    SkSpan<const float> ws, SkPathFillType ft, bool isVolatile) {
     *     return MakeNullCheck(SkPathData::Make(pts, vbs, ws), ft, isVolatile);
     * }
     * ```
     */
    public fun raw(
      pts: SkSpan<SkPoint>,
      verbs: SkSpan<SkPathVerb>,
      conics: SkSpan<SkScalar>,
      ft: SkPathFillType,
      isVolatile: Boolean = false,
    ): SkPath {
      TODO("Implement raw")
    }

    /**
     * C++ original:
     * ```cpp
     * SkPath SkPath::Rect(const SkRect& r, SkPathFillType ft, SkPathDirection dir, unsigned startIndex) {
     *     startIndex &= 3;    // keep it legal
     *     return MakeNullCheck(SkPathData::Rect(r, dir, startIndex), ft, false);
     * }
     * ```
     */
    public fun rect(
      r: SkRect,
      ft: SkPathFillType,
      dir: SkPathDirection = TODO(),
      startIndex: UInt = 0u,
    ): SkPath {
      TODO("Implement rect")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkPath Rect(const SkRect& r, SkPathDirection direction = SkPathDirection::kDefault,
     *                        unsigned startIndex = 0) {
     *         return Rect(r, SkPathFillType::kDefault, direction, startIndex);
     *     }
     * ```
     */
    public fun rect(
      r: SkRect,
      direction: SkPathDirection = TODO(),
      startIndex: UInt = 0u,
    ): SkPath {
      TODO("Implement rect")
    }

    /**
     * C++ original:
     * ```cpp
     * SkPath SkPath::Oval(const SkRect& r, SkPathDirection dir) {
     *     // legacy start index: 1
     *     return Oval(r, dir, 1);
     * }
     * ```
     */
    public fun oval(r: SkRect, dir: SkPathDirection = TODO()): SkPath {
      TODO("Implement oval")
    }

    /**
     * C++ original:
     * ```cpp
     * SkPath SkPath::Oval(const SkRect& r, SkPathDirection dir, unsigned startIndex) {
     *     startIndex &= 3;    // keep it legal
     *     return MakeNullCheck(SkPathData::Oval(r, dir, startIndex), SkPathFillType::kDefault, false);
     * }
     * ```
     */
    public fun oval(
      r: SkRect,
      dir: SkPathDirection,
      startIndex: UInt,
    ): SkPath {
      TODO("Implement oval")
    }

    /**
     * C++ original:
     * ```cpp
     * SkPath SkPath::Circle(SkScalar x, SkScalar y, SkScalar r, SkPathDirection dir) {
     *     if (r >= 0) {
     *         return Oval(SkRect::MakeLTRB(x - r, y - r, x + r, y + r), dir);
     *     } else {
     *         return SkPath();
     *     }
     * }
     * ```
     */
    public fun circle(
      centerX: SkScalar,
      centerY: SkScalar,
      radius: SkScalar,
      dir: SkPathDirection = TODO(),
    ): SkPath {
      TODO("Implement circle")
    }

    /**
     * C++ original:
     * ```cpp
     * SkPath SkPath::RRect(const SkRRect& rr, SkPathDirection dir) {
     *     // legacy start indices: 6 (CW) and 7 (CCW)
     *     return RRect(rr, dir, dir == SkPathDirection::kCW ? 6 : 7);
     * }
     * ```
     */
    public fun rRect(rr: SkRRect, dir: SkPathDirection = TODO()): SkPath {
      TODO("Implement rRect")
    }

    /**
     * C++ original:
     * ```cpp
     * SkPath SkPath::RRect(const SkRRect& rr, SkPathDirection dir, unsigned startIndex) {
     *     startIndex &= 7;    // keep it legal
     *     // To be backwards compatible with the old impl for building a rrect path, we
     *     // first check to see if the rrect itself can be simplified...
     *     const SkRect& bounds = rr.getBounds();
     *     auto [asType, newIndex] = SkPathPriv::SimplifyRRect(rr, startIndex);
     *     switch (asType) {
     *         case SkPathPriv::RRectAsEnum::kRect:
     *             return SkPath::Rect(bounds, SkPathFillType::kDefault, dir, newIndex);
     *
     *         case SkPathPriv::RRectAsEnum::kOval:
     *             return SkPath::Oval(bounds, dir, newIndex);
     *
     *         case SkPathPriv::RRectAsEnum::kRRect:
     *             // fall through
     *             break;
     *     }
     *     return MakeNullCheck(SkPathData::RRect(rr, dir, newIndex), SkPathFillType::kDefault, false);
     * }
     * ```
     */
    public fun rRect(
      rr: SkRRect,
      dir: SkPathDirection,
      startIndex: UInt,
    ): SkPath {
      TODO("Implement rRect")
    }

    /**
     * C++ original:
     * ```cpp
     * SkPath SkPath::RRect(const SkRect& r, SkScalar rx, SkScalar ry, SkPathDirection dir) {
     *     return RRect(SkRRect::MakeRectXY(r, rx, ry), dir);
     * }
     * ```
     */
    public fun rRect(
      bounds: SkRect,
      rx: SkScalar,
      ry: SkScalar,
      dir: SkPathDirection = TODO(),
    ): SkPath {
      TODO("Implement rRect")
    }

    /**
     * C++ original:
     * ```cpp
     * SkPath SkPath::Polygon(SkSpan<const SkPoint> pts, bool isClosed,
     *                        SkPathFillType ft, bool isVolatile) {
     *     return MakeNullCheck(SkPathData::Polygon(pts, isClosed), ft, isVolatile);
     * }
     * ```
     */
    public fun polygon(
      pts: SkSpan<SkPoint>,
      isClosed: Boolean,
      fillType: SkPathFillType = TODO(),
      isVolatile: Boolean = false,
    ): SkPath {
      TODO("Implement polygon")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkPath Line(SkPoint a, SkPoint b) {
     *         return Polygon({{a, b}}, false);
     *     }
     * ```
     */
    public fun line(a: SkPoint, b: SkPoint): SkPath {
      TODO("Implement line")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkPath Make(SkSpan<const SkPoint> pts,
     *                        SkSpan<const uint8_t> verbs,
     *                        SkSpan<const SkScalar> conics,
     *                        SkPathFillType fillType,
     *                        bool isVolatile = false) {
     *         return Raw(pts, {reinterpret_cast<const SkPathVerb*>(verbs.data()), verbs.size()},
     *                    conics, fillType, isVolatile);
     *     }
     * ```
     */
    public fun make(
      pts: SkSpan<SkPoint>,
      verbs: SkSpan<UByte>,
      conics: SkSpan<SkScalar>,
      fillType: SkPathFillType,
      isVolatile: Boolean = false,
    ): SkPath {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkPath::IsLineDegenerate(const SkPoint& p1, const SkPoint& p2, bool exact) {
     *     return exact ? p1 == p2 : SkPointPriv::EqualsWithinTolerance(p1, p2);
     * }
     * ```
     */
    public fun isLineDegenerate(
      p1: SkPoint,
      p2: SkPoint,
      exact: Boolean,
    ): Boolean {
      TODO("Implement isLineDegenerate")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkPath::IsQuadDegenerate(const SkPoint& p1, const SkPoint& p2,
     *                                 const SkPoint& p3, bool exact) {
     *     return exact ? p1 == p2 && p2 == p3 : SkPointPriv::EqualsWithinTolerance(p1, p2) &&
     *             SkPointPriv::EqualsWithinTolerance(p2, p3);
     * }
     * ```
     */
    public fun isQuadDegenerate(
      p1: SkPoint,
      p2: SkPoint,
      p3: SkPoint,
      exact: Boolean,
    ): Boolean {
      TODO("Implement isQuadDegenerate")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkPath::IsCubicDegenerate(const SkPoint& p1, const SkPoint& p2,
     *                                 const SkPoint& p3, const SkPoint& p4, bool exact) {
     *     return exact ? p1 == p2 && p2 == p3 && p3 == p4 :
     *             SkPointPriv::EqualsWithinTolerance(p1, p2) &&
     *             SkPointPriv::EqualsWithinTolerance(p2, p3) &&
     *             SkPointPriv::EqualsWithinTolerance(p3, p4);
     * }
     * ```
     */
    public fun isCubicDegenerate(
      p1: SkPoint,
      p2: SkPoint,
      p3: SkPoint,
      p4: SkPoint,
      exact: Boolean,
    ): Boolean {
      TODO("Implement isCubicDegenerate")
    }

    /**
     * C++ original:
     * ```cpp
     * int SkPath::ConvertConicToQuads(const SkPoint& p0, const SkPoint& p1, const SkPoint& p2,
     *                                 SkScalar w, SkPoint pts[], int pow2) {
     *     const SkConic conic(p0, p1, p2, w);
     *     return conic.chopIntoQuadsPOW2(pts, pow2);
     * }
     * ```
     */
    public fun convertConicToQuads(
      p0: SkPoint,
      p1: SkPoint,
      p2: SkPoint,
      w: SkScalar,
      pts: Array<SkPoint>,
      pow2: Int,
    ): Int {
      TODO("Implement convertConicToQuads")
    }

    /**
     * C++ original:
     * ```cpp
     * std::optional<SkPath> SkPath::ReadFromMemory(const void* storage, size_t length, size_t* bytesRead) {
     *     size_t bytesStorage = 0;
     *     if (!bytesRead) {
     *         bytesRead = &bytesStorage;
     *     }
     *     SkRBuffer buffer(storage, length);
     *     uint32_t packed;
     *     if (!buffer.readU32(&packed)) {
     *         *bytesRead = 0;
     *         return {};
     *     }
     *     unsigned version = extract_version(packed);
     *
     *     const bool verbsAreForward = (version == kVerbsAreStoredForward_Version);
     *     if (!verbsAreForward && version != kJustPublicData_Version) SK_UNLIKELY {
     *         // Old/unsupported version.
     *         *bytesRead = 0;
     *         return {};
     *     }
     *
     *     switch (extract_serializationtype(packed)) {
     *         case SerializationType::kRRect:
     *             return read_rrect_path(storage, length, bytesRead);
     *         case SerializationType::kGeneral:
     *             break;  // fall out
     *         default:
     *             *bytesRead = 0;
     *             return {};
     *     }
     *
     *     // To minimize the number of reads done a structure with the counts is used.
     *     struct {
     *       uint32_t pts, cnx, vbs;
     *     } counts;
     *     if (!buffer.read(&counts, sizeof(counts))) {
     *         *bytesRead = 0;
     *         return {};
     *     }
     *
     *     const SkPoint* points = buffer.skipCount<SkPoint>(counts.pts);
     *     const SkScalar* conics = buffer.skipCount<SkScalar>(counts.cnx);
     *     const SkPathVerb* verbs = buffer.skipCount<SkPathVerb>(counts.vbs);
     *     buffer.skipToAlign4();
     *     if (!buffer.isValid()) {
     *         *bytesRead = 0;
     *         return {};
     *     }
     *     SkASSERT(buffer.pos() <= length);
     *
     *     if (counts.vbs == 0) {
     *         if (counts.pts == 0 && counts.cnx == 0) {
     *             SkPath path(extract_filltype(packed));
     *             *bytesRead = buffer.pos();
     *             return path;
     *         }
     *         // No verbs but points and/or conic weights is a not a valid path.
     *         *bytesRead = 0;
     *         return {};
     *     }
     *
     *     SkAutoMalloc reversedStorage;
     *     if (!verbsAreForward) SK_UNLIKELY {
     *         SkPathVerb* tmpVerbs = (SkPathVerb*)reversedStorage.reset(counts.vbs);
     *         for (unsigned i = 0; i < counts.vbs; ++i) {
     *             tmpVerbs[i] = verbs[counts.vbs - i - 1];
     *         }
     *         verbs = tmpVerbs;
     *     }
     *
     *     *bytesRead = buffer.pos();
     *     return SkPath::Raw({points, counts.pts},
     *                        {verbs, counts.vbs},
     *                        {conics, counts.cnx},
     *                        extract_filltype(packed),
     *                        false);
     * }
     * ```
     */
    private fun readFromMemory(
      buffer: Unit?,
      length: ULong,
      bytesRead: ULong? = null,
    ): SkPath? {
      TODO("Implement readFromMemory")
    }

    /**
     * C++ original:
     * ```cpp
     * SkPath SkPath::MakeNullCheck(sk_sp<SkPathData> pdata, SkPathFillType ft, bool isVolatile) {
     *     if (!pdata) {
     *         pdata = sk_ref_sp(PeekErrorSingleton());
     *     }
     *     return SkPath(std::move(pdata), ft, isVolatile);
     * }
     * ```
     */
    private fun makeNullCheck(
      pdata: SkSp<SkPathData>,
      ft: SkPathFillType,
      isVolatile: Boolean,
    ): SkPath {
      TODO("Implement makeNullCheck")
    }

    /**
     * C++ original:
     * ```cpp
     * SkPathData* SkPath::PeekErrorSingleton() {
     *     static SkPathData* gErrorSingleton = SkPathData::MakeNoCheck({}, {}, {}, {}, {}).release();
     *
     *     // Make sure MakeNoCheck() didn't alias us to the standard Empty instance. We want our
     *     // pointer to be distinct from that one.
     *     SkASSERT(gErrorSingleton != SkPathData::Empty().get());
     *
     *     return gErrorSingleton;
     * }
     * ```
     */
    private fun peekErrorSingleton(): SkPathData {
      TODO("Implement peekErrorSingleton")
    }
  }
}
