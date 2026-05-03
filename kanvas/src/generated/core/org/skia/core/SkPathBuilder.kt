package org.skia.core

import kotlin.Any
import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.ULong
import org.skia.foundation.SkPath
import org.skia.foundation.SkRRect
import org.skia.foundation.SkSpan
import org.skia.math.SkMatrix
import org.skia.math.SkPathDirection
import org.skia.math.SkPathFillType
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.math.SkScalar
import org.skia.math.SkVector

/**
 * C++ original:
 * ```cpp
 * class SK_API SkPathBuilder {
 *     using PointsArray = skia_private::STArray<4, SkPoint>;
 *     using VerbsArray = skia_private::STArray<4, SkPathVerb>;
 *     using ConicWeightsArray = skia_private::STArray<2, float>;
 * public:
 *     /** Constructs an empty SkPathBuilder. By default, SkPathBuilder has no verbs, no SkPoint, and
 *         no weights. FillType is set to kWinding.
 *
 *         @return  empty SkPathBuilder
 *     */
 *     SkPathBuilder();
 *
 *     /** Constructs an empty SkPathBuilder with the given FillType. By default, SkPathBuilder has no
 *         verbs, no SkPoint, and no weights.
 *
 *         @param fillType  SkPathFillType to set on the SkPathBuilder.
 *         @return          empty SkPathBuilder
 *     */
 *     explicit SkPathBuilder(SkPathFillType fillType);
 *
 *     /** Constructs an SkPathBuilder that is a copy of an existing SkPath.
 *         Copies the FillType and replays all of the verbs from the SkPath into the SkPathBuilder.
 *
 *         @param path  SkPath to copy
 *         @return      SkPathBuilder
 *     */
 *     explicit SkPathBuilder(const SkPath& path);
 *
 *     SkPathBuilder(const SkPathBuilder&) = default;
 *     ~SkPathBuilder();
 *
 *     /** Sets an SkPathBuilder to be a copy of an existing SkPath.
 *         Copies the FillType and replays all of the verbs from the SkPath into the SkPathBuilder.
 *
 *         @param path  SkPath to copy
 *         @return      SkPathBuilder
 *     */
 *     SkPathBuilder& operator=(const SkPath&);
 *     SkPathBuilder& operator=(const SkPathBuilder&) = default;
 *
 *     bool operator==(const SkPathBuilder&) const;
 *     bool operator!=(const SkPathBuilder& o) const { return !(*this == o); }
 *
 *     /** Returns SkPathFillType, the rule used to fill SkPath.
 *
 *         @return  current SkPathFillType setting
 *     */
 *     SkPathFillType fillType() const { return fFillType; }
 *
 *     /** Returns minimum and maximum axes values of SkPoint array.
 *         Returns (0, 0, 0, 0) if SkPathBuilder contains no points.
 *
 *         SkRect returned includes all SkPoint added to SkPathBuilder, including SkPoint associated
 *         with kMove_Verb that define empty contours.
 *
 *         If any of the points are non-finite, returns {}.
 *
 *         @return  bounds of all SkPoint in SkPoint array, or {}.
 *     */
 *     std::optional<SkRect> computeFiniteBounds() const;
 *
 *     /** Like computeFiniteBounds() but returns a 'tight' bounds, meaning when there are curve
 *      *  segments, this computes the X/Y limits of the curve itself, not the curve's control
 *      *  point(s). For a polygon, this returns the same as computeFiniteBounds().
 *     */
 *     std::optional<SkRect> computeTightBounds() const;
 *
 *     // DEPRECATED -- returns "empty" if the bounds are non-finite
 *     SkRect computeBounds() const {
 *         if (auto bounds = this->computeFiniteBounds()) {
 *             return *bounds;
 *         }
 *         return SkRect::MakeEmpty();
 *     }
 *
 *     /** Returns an SkPath representing the current state of the SkPathBuilder. The builder is
 *         unchanged after returning the path.
 *
 *         @param mx if present, applied to the points after they are copied into the resulting path.
 *         @return  SkPath representing the current state of the builder.
 *      */
 *     SkPath snapshot(const SkMatrix* mx = nullptr) const;
 *
 *     /** Returns an SkPath representing the current state of the SkPathBuilder. The builder is
 *         reset to empty after returning the path.
 *
 *         @param mx if present, applied to the points after they are copied into the resulting path.
 *         @return  SkPath representing the current state of the builder.
 *      */
 *     SkPath detach(const SkMatrix* mx = nullptr);
 *
 *     sk_sp<SkPathData> snapshotData() const;
 *     sk_sp<SkPathData> detachData();
 *
 *     /** Sets SkPathFillType, the rule used to fill SkPath. While there is no
 *         check that ft is legal, values outside of SkPathFillType are not supported.
 *
 *         @param ft  SkPathFillType to be used by SKPaths generated from this builder.
 *         @return    reference to SkPathBuilder
 *     */
 *     SkPathBuilder& setFillType(SkPathFillType ft) { fFillType = ft; return *this; }
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
 *         @return            reference to SkPathBuilder
 *     */
 *     SkPathBuilder& setIsVolatile(bool isVolatile) { fIsVolatile = isVolatile; return *this; }
 *
 *     /** Sets SkPathBuilder to its initial state.
 *         Removes verb array, SkPoint array, and weights, and sets FillType to kWinding.
 *         Internal storage associated with SkPathBuilder is preserved.
 *
 *         @return  reference to SkPathBuilder
 *     */
 *     SkPathBuilder& reset();
 *
 *     /** Specifies the beginning of contour. If the previous verb was a "move" verb,
 *      *  then this just replaces the point value of that move, otherwise it appends a new
 *      *  "move" verb to the builder using the point.
 *      *
 *      *  Thus, each contour can only have 1 move verb in it (the last one specified).
 *      */
 *     SkPathBuilder& moveTo(SkPoint point);
 *
 *     SkPathBuilder& moveTo(SkScalar x, SkScalar y) {
 *         return this->moveTo(SkPoint::Make(x, y));
 *     }
 *
 *     /** Adds line from last point to SkPoint p. If SkPathBuilder is empty, or last SkPath::Verb is
 *         kClose_Verb, last point is set to (0, 0) before adding line.
 *
 *         lineTo() first appends kMove_Verb to verb array and (0, 0) to SkPoint array, if needed.
 *         lineTo() then appends kLine_Verb to verb array and SkPoint p to SkPoint array.
 *
 *         @param p  end SkPoint of added line
 *         @return   reference to SkPathBuilder
 *     */
 *     SkPathBuilder& lineTo(SkPoint pt);
 *
 *     /** Adds line from last point to (x, y). If SkPathBuilder is empty, or last SkPath::Verb is
 *         kClose_Verb, last point is set to (0, 0) before adding line.
 *
 *         lineTo() appends kMove_Verb to verb array and (0, 0) to SkPoint array, if needed.
 *         lineTo() then appends kLine_Verb to verb array and (x, y) to SkPoint array.
 *
 *         @param x  end of added line on x-axis
 *         @param y  end of added line on y-axis
 *         @return   reference to SkPathBuilder
 *     */
 *     SkPathBuilder& lineTo(SkScalar x, SkScalar y) { return this->lineTo(SkPoint::Make(x, y)); }
 *
 *     /** Adds quad from last point towards SkPoint p1, to SkPoint p2.
 *         If SkPathBuilder is empty, or last SkPath::Verb is kClose_Verb, last point is set to (0, 0)
 *         before adding quad.
 *
 *         Appends kMove_Verb to verb array and (0, 0) to SkPoint array, if needed;
 *         then appends kQuad_Verb to verb array; and SkPoint p1, p2
 *         to SkPoint array.
 *
 *         @param p1  control SkPoint of added quad
 *         @param p2  end SkPoint of added quad
 *         @return    reference to SkPathBuilder
 *     */
 *     SkPathBuilder& quadTo(SkPoint pt1, SkPoint pt2);
 *
 *     /** Adds quad from last point towards (x1, y1), to (x2, y2).
 *         If SkPath is empty, or last SkPath::Verb is kClose_Verb, last point is set to (0, 0)
 *         before adding quad.
 *
 *         Appends kMove_Verb to verb array and (0, 0) to SkPoint array, if needed;
 *         then appends kQuad_Verb to verb array; and (x1, y1), (x2, y2)
 *         to SkPoint array.
 *
 *         @param x1  control SkPoint of quad on x-axis
 *         @param y1  control SkPoint of quad on y-axis
 *         @param x2  end SkPoint of quad on x-axis
 *         @param y2  end SkPoint of quad on y-axis
 *         @return    reference to SkPath
 *
 *         example: https://fiddle.skia.org/c/@Path_quadTo
 *     */
 *     SkPathBuilder& quadTo(SkScalar x1, SkScalar y1, SkScalar x2, SkScalar y2) {
 *         return this->quadTo(SkPoint::Make(x1, y1), SkPoint::Make(x2, y2));
 *     }
 *
 *     /** Adds quad from last point towards the first SkPoint in pts, to the second.
 *         If SkPathBuilder is empty, or last SkPath::Verb is kClose_Verb, last point is set to (0, 0)
 *         before adding quad.
 *
 *         Appends kMove_Verb to verb array and (0, 0) to SkPoint array, if needed;
 *         then appends kQuad_Verb to verb array; and the SkPoints to SkPoint array.
 *
 *         @param pts  control point and endpoint of added quad.
 *         @return     reference to SkPathBuilder
 *     */
 *     SkPathBuilder& quadTo(const SkPoint pts[2]) { return this->quadTo(pts[0], pts[1]); }
 *
 *     /** Adds conic from last point towards pt1, to pt2, weighted by w.
 *         If SkPathBuilder is empty, or last SkPath::Verb is kClose_Verb, last point is set to (0, 0)
 *         before adding conic.
 *
 *         Appends kMove_Verb to verb array and (0, 0) to SkPoint array, if needed.
 *
 *         If w is finite and not one, appends kConic_Verb to verb array;
 *         and pt1, pt2 to SkPoint array; and w to conic weights.
 *
 *         If w is one, appends kQuad_Verb to verb array, and
 *         pt1, pt2 to SkPoint array.
 *
 *         If w is not finite, appends kLine_Verb twice to verb array, and
 *         pt1, pt2 to SkPoint array.
 *
 *         @param pt1  control SkPoint of conic
 *         @param pt2  end SkPoint of conic
 *         @param w   weight of added conic
 *         @return    reference to SkPathBuilder
 *     */
 *     SkPathBuilder& conicTo(SkPoint pt1, SkPoint pt2, SkScalar w);
 *
 *     /** Adds conic from last point towards (x1, y1), to (x2, y2), weighted by w.
 *         If SkPathBuilder is empty, or last SkPath::Verb is kClose_Verb, last point is set to (0, 0)
 *         before adding conic.
 *
 *         Appends kMove_Verb to verb array and (0, 0) to SkPoint array, if needed.
 *
 *         If w is finite and not one, appends kConic_Verb to verb array;
 *         and (x1, y1), (x2, y2) to SkPoint array; and w to conic weights.
 *
 *         If w is one, appends kQuad_Verb to verb array, and
 *         (x1, y1), (x2, y2) to SkPoint array.
 *
 *         If w is not finite, appends kLine_Verb twice to verb array, and
 *         (x1, y1), (x2, y2) to SkPoint array.
 *
 *         @param x1  control SkPoint of conic on x-axis
 *         @param y1  control SkPoint of conic on y-axis
 *         @param x2  end SkPoint of conic on x-axis
 *         @param y2  end SkPoint of conic on y-axis
 *         @param w   weight of added conic
 *         @return    reference to SkPathBuilder
 *     */
 *     SkPathBuilder& conicTo(SkScalar x1, SkScalar y1, SkScalar x2, SkScalar y2, SkScalar w) {
 *         return this->conicTo(SkPoint::Make(x1, y1), SkPoint::Make(x2, y2), w);
 *     }
 *
 *     /** Adds conic from last point towards SkPoint p1, to SkPoint p2, weighted by w.
 *         If SkPathBuilder is empty, or last SkPath::Verb is kClose_Verb, last point is set to (0, 0)
 *         before adding conic.
 *
 *         Appends kMove_Verb to verb array and (0, 0) to SkPoint array, if needed.
 *
 *         If w is finite and not one, appends kConic_Verb to verb array;
 *         and SkPoint p1, p2 to SkPoint array; and w to conic weights.
 *
 *         If w is one, appends kQuad_Verb to verb array, and SkPoint p1, p2
 *         to SkPoint array.
 *
 *         If w is not finite, appends kLine_Verb twice to verb array, and
 *         SkPoint p1, p2 to SkPoint array.
 *
 *         @param p1  control SkPoint of added conic
 *         @param p2  end SkPoint of added conic
 *         @param w   weight of added conic
 *         @return    reference to SkPathBuilder
 *     */
 *     SkPathBuilder& conicTo(const SkPoint pts[2], SkScalar w) {
 *         return this->conicTo(pts[0], pts[1], w);
 *     }
 *
 *     /** Adds cubic from last point towards SkPoint p1, then towards SkPoint p2, ending at
 *         SkPoint p3. If SkPathBuilder is empty, or last SkPath::Verb is kClose_Verb, last point is
 *         set to (0, 0) before adding cubic.
 *
 *         Appends kMove_Verb to verb array and (0, 0) to SkPoint array, if needed;
 *         then appends kCubic_Verb to verb array; and SkPoint p1, p2, p3
 *         to SkPoint array.
 *
 *         @param p1  first control SkPoint of cubic
 *         @param p2  second control SkPoint of cubic
 *         @param p3  end SkPoint of cubic
 *         @return    reference to SkPathBuilder
 *     */
 *     SkPathBuilder& cubicTo(SkPoint pt1, SkPoint pt2, SkPoint pt3);
 *
 *     /** Adds cubic from last point towards (x1, y1), then towards (x2, y2), ending at
 *         (x3, y3). If SkPathBuilder is empty, or last SkPath::Verb is kClose_Verb, last point is set
 *         to (0, 0) before adding cubic.
 *
 *         Appends kMove_Verb to verb array and (0, 0) to SkPoint array, if needed;
 *         then appends kCubic_Verb to verb array; and (x1, y1), (x2, y2), (x3, y3)
 *         to SkPoint array.
 *
 *         @param x1  first control SkPoint of cubic on x-axis
 *         @param y1  first control SkPoint of cubic on y-axis
 *         @param x2  second control SkPoint of cubic on x-axis
 *         @param y2  second control SkPoint of cubic on y-axis
 *         @param x3  end SkPoint of cubic on x-axis
 *         @param y3  end SkPoint of cubic on y-axis
 *         @return    reference to SkPathBuilder
 *     */
 *     SkPathBuilder& cubicTo(SkScalar x1, SkScalar y1, SkScalar x2, SkScalar y2, SkScalar x3, SkScalar y3) {
 *         return this->cubicTo(SkPoint::Make(x1, y1), SkPoint::Make(x2, y2), SkPoint::Make(x3, y3));
 *     }
 *
 *     /** Adds cubic from last point towards the first SkPoint, then towards the second, ending at
 *         the third. If SkPathBuilder is empty, or last SkPath::Verb is kClose_Verb, last point is
 *         set to (0, 0) before adding cubic.
 *
 *         Appends kMove_Verb to verb array and (0, 0) to SkPoint array, if needed;
 *         then appends kCubic_Verb to verb array; and SkPoint p1, p2, p3
 *         to SkPoint array.
 *
 *         @param pts  first and second control SkPoints of cubic, and end SkPoint.
 *         @return     reference to SkPathBuilder
 *     */
 *     SkPathBuilder& cubicTo(const SkPoint pts[3]) {
 *         return this->cubicTo(pts[0], pts[1], pts[2]);
 *     }
 *
 *     /** Appends kClose_Verb to SkPathBuilder. A closed contour connects the first and last SkPoint
 *         with line, forming a continuous loop. Open and closed contour draw the same
 *         with SkPaint::kFill_Style. With SkPaint::kStroke_Style, open contour draws
 *         SkPaint::Cap at contour start and end; closed contour draws
 *         SkPaint::Join at contour start and end.
 *
 *         close() has no effect if SkPathBuilder is empty or last SkPath SkPath::Verb is kClose_Verb.
 *
 *         @return  reference to SkPathBuilder
 *     */
 *     SkPathBuilder& close();
 *
 *     /** Append a series of lineTo(...)
 *
 *         @param pts    span of SkPoint
 *         @return reference to SkPathBuilder.
 *     */
 *     SkPathBuilder& polylineTo(SkSpan<const SkPoint> pts);
 *
 *     // Relative versions of segments, relative to the previous position.
 *
 *     /** Adds beginning of contour relative to last point.
 *         If SkPathBuilder is empty, starts contour at (dx, dy).
 *         Otherwise, start contour at last point offset by (dx, dy).
 *         Function name stands for "relative move to".
 *
 *         @param pt  vector offset from last point to contour start
 *         @return    reference to SkPathBuilder
 *
 *         example: https://fiddle.skia.org/c/@Path_rMoveTo
 *     */
 *     SkPathBuilder& rMoveTo(SkVector pt);
 *
 *     /** Adds beginning of contour relative to last point.
 *         If SkPathBuilder is empty, starts contour at (dx, dy).
 *         Otherwise, start contour at last point offset by (dx, dy).
 *         Function name stands for "relative move to".
 *
 *         @param dx  offset from last point to contour start on x-axis
 *         @param dy  offset from last point to contour start on y-axis
 *         @return    reference to SkPathBuilder
 *
 *         example: https://fiddle.skia.org/c/@Path_rMoveTo
 *     */
 *     SkPathBuilder& rMoveTo(SkScalar dx, SkScalar dy) { return this->rMoveTo({dx, dy}); }
 *
 *     /** Adds line from last point to vector given by pt. If SkPathBuilder is empty, or last
 *         SkPath::Verb is kClose_Verb, last point is set to (0, 0) before adding line.
 *
 *         Appends kMove_Verb to verb array and (0, 0) to SkPoint array, if needed;
 *         then appends kLine_Verb to verb array and line end to SkPoint array.
 *         Line end is last point plus vector given by pt.
 *         Function name stands for "relative line to".
 *
 *         @param pt  vector offset from last point to line end
 *         @return    reference to SkPathBuilder
 *     */
 *     SkPathBuilder& rLineTo(SkVector pt);
 *
 *     /** Adds line from last point to vector (dx, dy). If SkPathBuilder is empty, or last
 *         SkPath::Verb is kClose_Verb, last point is set to (0, 0) before adding line.
 *
 *         Appends kMove_Verb to verb array and (0, 0) to SkPoint array, if needed;
 *         then appends kLine_Verb to verb array and line end to SkPoint array.
 *         Line end is last point plus vector (dx, dy).
 *         Function name stands for "relative line to".
 *
 *         @param dx  offset from last point to line end on x-axis
 *         @param dy  offset from last point to line end on y-axis
 *         @return    reference to SkPathBuilder
 *     */
 *     SkPathBuilder& rLineTo(SkScalar dx, SkScalar dy) { return this->rLineTo({dx, dy}); }
 *
 *     /** Adds quad from last point towards vector pt1, to vector pt2.
 *         If SkPathBuilder is empty, or last SkPath::Verb
 *         is kClose_Verb, last point is set to (0, 0) before adding quad.
 *
 *         Appends kMove_Verb to verb array and (0, 0) to SkPoint array,
 *         if needed; then appends kQuad_Verb to verb array; and appends quad
 *         control and quad end to SkPoint array.
 *         Quad control is last point plus vector pt1.
 *         Quad end is last point plus vector pt2.
 *         Function name stands for "relative quad to".
 *
 *         @param pt1  offset vector from last point to quad control
 *         @param pt2  offset vector from last point to quad end
 *         @return     reference to SkPathBuilder
 *     */
 *     SkPathBuilder& rQuadTo(SkVector pt1, SkVector pt2);
 *
 *     /** Adds quad from last point towards vector (dx1, dy1), to vector (dx2, dy2).
 *         If SkPathBuilder is empty, or last SkPath::Verb
 *         is kClose_Verb, last point is set to (0, 0) before adding quad.
 *
 *         Appends kMove_Verb to verb array and (0, 0) to SkPoint array,
 *         if needed; then appends kQuad_Verb to verb array; and appends quad
 *         control and quad end to SkPoint array.
 *         Quad control is last point plus vector (dx1, dy1).
 *         Quad end is last point plus vector (dx2, dy2).
 *         Function name stands for "relative quad to".
 *
 *         @param dx1  offset from last point to quad control on x-axis
 *         @param dy1  offset from last point to quad control on y-axis
 *         @param dx2  offset from last point to quad end on x-axis
 *         @param dy2  offset from last point to quad end on y-axis
 *         @return     reference to SkPathBuilder
 *     */
 *     SkPathBuilder& rQuadTo(SkScalar dx1, SkScalar dy1, SkScalar dx2, SkScalar dy2) {
 *         return this->rQuadTo({dx1, dy1}, {dx2, dy2});
 *     }
 *
 *     /** Adds conic from last point towards vector p1, to vector p2,
 *         weighted by w. If SkPathBuilder is empty, or last SkPath::Verb
 *         is kClose_Verb, last point is set to (0, 0) before adding conic.
 *
 *         Appends kMove_Verb to verb array and (0, 0) to SkPoint array,
 *         if needed.
 *
 *         If w is finite and not one, next appends kConic_Verb to verb array,
 *         and w is recorded as conic weight; otherwise, if w is one, appends
 *         kQuad_Verb to verb array; or if w is not finite, appends kLine_Verb
 *         twice to verb array.
 *
 *         In all cases appends SkPoint control and end to SkPoint array.
 *         control is last point plus vector p1.
 *         end is last point plus vector p2.
 *
 *         Function name stands for "relative conic to".
 *
 *         @param p1  offset vector from last point to conic control
 *         @param p2  offset vector from last point to conic end
 *         @param w   weight of added conic
 *         @return    reference to SkPathBuilder
 *     */
 *     SkPathBuilder& rConicTo(SkVector p1, SkVector p2, SkScalar w);
 *
 *     /** Adds conic from last point towards vector (dx1, dy1), to vector (dx2, dy2),
 *         weighted by w. If SkPathBuilder is empty, or last SkPath::Verb
 *         is kClose_Verb, last point is set to (0, 0) before adding conic.
 *
 *         Appends kMove_Verb to verb array and (0, 0) to SkPoint array,
 *         if needed.
 *
 *         If w is finite and not one, next appends kConic_Verb to verb array,
 *         and w is recorded as conic weight; otherwise, if w is one, appends
 *         kQuad_Verb to verb array; or if w is not finite, appends kLine_Verb
 *         twice to verb array.
 *
 *         In all cases appends SkPoint control and end to SkPoint array.
 *         control is last point plus vector (dx1, dy1).
 *         end is last point plus vector (dx2, dy2).
 *
 *         Function name stands for "relative conic to".
 *
 *         @param dx1  offset from last point to conic control on x-axis
 *         @param dy1  offset from last point to conic control on y-axis
 *         @param dx2  offset from last point to conic end on x-axis
 *         @param dy2  offset from last point to conic end on y-axis
 *         @param w    weight of added conic
 *         @return     reference to SkPathBuilder
 *     */
 *     SkPathBuilder& rConicTo(SkScalar dx1, SkScalar dy1, SkScalar dx2, SkScalar dy2, SkScalar w) {
 *         return this->rConicTo({dx1, dy1}, {dx2, dy2}, w);
 *     }
 *
 *     /** Adds cubic from last point towards vector pt1, then towards
 *         vector pt2, to vector pt3.
 *         If SkPathBuilder is empty, or last SkPath::Verb
 *         is kClose_Verb, last point is set to (0, 0) before adding cubic.
 *
 *         Appends kMove_Verb to verb array and (0, 0) to SkPoint array,
 *         if needed; then appends kCubic_Verb to verb array; and appends cubic
 *         control and cubic end to SkPoint array.
 *         Cubic control is last point plus vector (dx1, dy1).
 *         Cubic end is last point plus vector (dx2, dy2).
 *         Function name stands for "relative cubic to".
 *
 *         @param pt1  offset vector from last point to first cubic control
 *         @param pt2  offset vector from last point to second cubic control
 *         @param pt3  offset vector from last point to cubic end
 *         @return    reference to SkPathBuilder
 *     */
 *     SkPathBuilder& rCubicTo(SkVector pt1, SkVector pt2, SkVector pt3);
 *
 *     /** Adds cubic from last point towards vector (dx1, dy1), then towards
 *         vector (dx2, dy2), to vector (dx3, dy3).
 *         If SkPathBuilder is empty, or last SkPath::Verb
 *         is kClose_Verb, last point is set to (0, 0) before adding cubic.
 *
 *         Appends kMove_Verb to verb array and (0, 0) to SkPoint array,
 *         if needed; then appends kCubic_Verb to verb array; and appends cubic
 *         control and cubic end to SkPoint array.
 *         Cubic control is last point plus vector (dx1, dy1).
 *         Cubic end is last point plus vector (dx2, dy2).
 *         Function name stands for "relative cubic to".
 *
 *         @param dx1  offset from last point to first cubic control on x-axis
 *         @param dy1  offset from last point to first cubic control on y-axis
 *         @param dx2  offset from last point to second cubic control on x-axis
 *         @param dy2  offset from last point to second cubic control on y-axis
 *         @param dx3  offset from last point to cubic end on x-axis
 *         @param dy3  offset from last point to cubic end on y-axis
 *         @return    reference to SkPathBuilder
 *     */
 *     SkPathBuilder& rCubicTo(SkScalar dx1, SkScalar dy1,
 *                             SkScalar dx2, SkScalar dy2,
 *                             SkScalar dx3, SkScalar dy3) {
 *         return this->rCubicTo({dx1, dy1}, {dx2, dy2}, {dx3, dy3});
 *     }
 *
 *     // Arcs
 *
 *     enum ArcSize {
 *         kSmall_ArcSize, //!< smaller of arc pair
 *         kLarge_ArcSize, //!< larger of arc pair
 *     };
 *
 *     /** Appends arc to SkPathBuilder, relative to last SkPath SkPoint. Arc is implemented by one or
 *         more conic, weighted to describe part of oval with radii (rx, ry) rotated by
 *         xAxisRotate degrees. Arc curves from last SkPathBuilder SkPoint to relative end SkPoint:
 *         (dx, dy), choosing one of four possible routes: clockwise or
 *         counterclockwise, and smaller or larger. If SkPathBuilder is empty, the start arc SkPoint
 *         is (0, 0).
 *
 *         Arc sweep is always less than 360 degrees. arcTo() appends line to end SkPoint
 *         if either radii are zero, or if last SkPath SkPoint equals end SkPoint.
 *         arcTo() scales radii (rx, ry) to fit last SkPath SkPoint and end SkPoint if both are
 *         greater than zero but too small to describe an arc.
 *
 *         arcTo() appends up to four conic curves.
 *         arcTo() implements the functionality of svg arc, although SVG "sweep-flag" value is
 *         opposite the integer value of sweep; SVG "sweep-flag" uses 1 for clockwise, while
 *         kCW_Direction cast to int is zero.
 *
 *         @param r            radii on axes before x-axis rotation
 *         @param xAxisRotate  x-axis rotation in degrees; positive values are clockwise
 *         @param largeArc     chooses smaller or larger arc
 *         @param sweep        chooses clockwise or counterclockwise arc
 *         @param dxdy         offset end of arc from last SkPath point
 *         @return             reference to SkPath
 *     */
 *     SkPathBuilder& rArcTo(SkPoint r, SkScalar xAxisRotate, ArcSize largeArc,
 *                           SkPathDirection sweep, SkVector dxdy);
 *
 *     /** Appends arc to the builder. Arc added is part of ellipse
 *         bounded by oval, from startAngle through sweepAngle. Both startAngle and
 *         sweepAngle are measured in degrees, where zero degrees is aligned with the
 *         positive x-axis, and positive sweeps extends arc clockwise.
 *
 *         arcTo() adds line connecting the builder's last point to initial arc point if forceMoveTo
 *         is false and the builder is not empty. Otherwise, added contour begins with first point
 *         of arc. Angles greater than -360 and less than 360 are treated modulo 360.
 *
 *         @param oval          bounds of ellipse containing arc
 *         @param startAngleDeg starting angle of arc in degrees
 *         @param sweepAngleDeg sweep, in degrees. Positive is clockwise; treated modulo 360
 *         @param forceMoveTo   true to start a new contour with arc
 *         @return              reference to the builder
 *     */
 *     SkPathBuilder& arcTo(const SkRect& oval, SkScalar startAngleDeg, SkScalar sweepAngleDeg,
 *                          bool forceMoveTo);
 *
 *     /** Appends arc to SkPath, after appending line if needed. Arc is implemented by conic
 *         weighted to describe part of circle. Arc is contained by tangent from
 *         last SkPath point to p1, and tangent from p1 to p2. Arc
 *         is part of circle sized to radius, positioned so it touches both tangent lines.
 *
 *         If last SkPath SkPoint does not start arc, arcTo() appends connecting line to SkPath.
 *         The length of vector from p1 to p2 does not affect arc.
 *
 *         Arc sweep is always less than 180 degrees. If radius is zero, or if
 *         tangents are nearly parallel, arcTo() appends line from last SkPath SkPoint to p1.
 *
 *         arcTo() appends at most one line and one conic.
 *         arcTo() implements the functionality of PostScript arct and HTML Canvas arcTo.
 *
 *         @param p1      SkPoint common to pair of tangents
 *         @param p2      end of second tangent
 *         @param radius  distance from arc to circle center
 *         @return        reference to SkPath
 *     */
 *     SkPathBuilder& arcTo(SkPoint p1, SkPoint p2, SkScalar radius);
 *
 *     /** Appends arc to SkPath. Arc is implemented by one or more conic weighted to describe
 *         part of oval with radii (r.fX, r.fY) rotated by xAxisRotate degrees. Arc curves
 *         from last SkPath SkPoint to (xy.fX, xy.fY), choosing one of four possible routes:
 *         clockwise or counterclockwise,
 *         and smaller or larger.
 *
 *         Arc sweep is always less than 360 degrees. arcTo() appends line to xy if either
 *         radii are zero, or if last SkPath SkPoint equals (xy.fX, xy.fY). arcTo() scales radii r to
 *         fit last SkPath SkPoint and xy if both are greater than zero but too small to describe
 *         an arc.
 *
 *         arcTo() appends up to four conic curves.
 *         arcTo() implements the functionality of SVG arc, although SVG sweep-flag value is
 *         opposite the integer value of sweep; SVG sweep-flag uses 1 for clockwise, while
 *         kCW_Direction cast to int is zero.
 *
 *         @param r            radii on axes before x-axis rotation
 *         @param xAxisRotate  x-axis rotation in degrees; positive values are clockwise
 *         @param largeArc     chooses smaller or larger arc
 *         @param sweep        chooses clockwise or counterclockwise arc
 *         @param xy           end of arc
 *         @return             reference to SkPath
 *     */
 *     SkPathBuilder& arcTo(SkPoint r, SkScalar xAxisRotate, ArcSize largeArc, SkPathDirection sweep,
 *                          SkPoint xy);
 *
 *     /** Appends arc to the builder, as the start of new contour. Arc added is part of ellipse
 *         bounded by oval, from startAngle through sweepAngle. Both startAngle and
 *         sweepAngle are measured in degrees, where zero degrees is aligned with the
 *         positive x-axis, and positive sweeps extends arc clockwise.
 *
 *         If sweepAngle <= -360, or sweepAngle >= 360; and startAngle modulo 90 is nearly
 *         zero, append oval instead of arc. Otherwise, sweepAngle values are treated
 *         modulo 360, and arc may or may not draw depending on numeric rounding.
 *
 *         @param oval          bounds of ellipse containing arc
 *         @param startAngleDeg starting angle of arc in degrees
 *         @param sweepAngleDeg sweep, in degrees. Positive is clockwise; treated modulo 360
 *         @return              reference to this builder
 *     */
 *     SkPathBuilder& addArc(const SkRect& oval, SkScalar startAngleDeg, SkScalar sweepAngleDeg);
 *
 *     SkPathBuilder& addLine(SkPoint a, SkPoint b) {
 *         return this->moveTo(a).lineTo(b);
 *     }
 *
 *     /** Adds a new contour to the SkPathBuilder, defined by the rect, and wound in the
 *         specified direction. The verbs added to the path will be:
 *
 *         kMove, kLine, kLine, kLine, kClose
 *
 *         start specifies which corner to begin the contour:
 *             0: upper-left  corner
 *             1: upper-right corner
 *             2: lower-right corner
 *             3: lower-left  corner
 *
 *         This start point also acts as the implied beginning of the subsequent,
 *         contour, if it does not have an explicit moveTo(). e.g.
 *
 *             path.addRect(...)
 *             // if we don't say moveTo() here, we will use the rect's start point
 *             path.lineTo(...)
 *
 *         @param rect   SkRect to add as a closed contour
 *         @param dir    SkPath::Direction to orient the new contour
 *         @param start  initial corner of SkRect to add
 *         @return       reference to SkPathBuilder
 *      */
 *     SkPathBuilder& addRect(const SkRect&, SkPathDirection, unsigned startIndex);
 *
 *     /** Adds a new contour to the SkPathBuilder, defined by the rect, and wound in the
 *         specified direction. The verbs added to the path will be:
 *
 *         kMove, kLine, kLine, kLine, kClose
 *
 *         The contour starts at the upper-left corner of the rect, which also acts as the implied
 *         beginning of the subsequent contour, if it does not have an explicit moveTo(). e.g.
 *
 *             path.addRect(...)
 *             // if we don't say moveTo() here, we will use the rect's upper-left corner
 *             path.lineTo(...)
 *
 *         @param rect   SkRect to add as a closed contour
 *         @param dir    SkPath::Direction to orient the new contour
 *         @return       reference to SkPathBuilder
 *      */
 *     SkPathBuilder& addRect(const SkRect& rect, SkPathDirection dir = SkPathDirection::kDefault) {
 *         return this->addRect(rect, dir, 0);
 *     }
 *
 *     /** Adds oval to SkPathBuilder, appending kMove_Verb, four kConic_Verb, and kClose_Verb.
 *         Oval is upright ellipse bounded by SkRect oval with radii equal to half oval width
 *         and half oval height. Oval begins at (oval.fRight, oval.centerY()) and continues
 *         clockwise if dir is kCW_Direction, counterclockwise if dir is kCCW_Direction.
 *
 *         @param oval  bounds of ellipse added
 *         @param dir   SkPath::Direction to wind ellipse
 *         @return      reference to SkPathBuilder
 *     */
 *     SkPathBuilder& addOval(const SkRect&, SkPathDirection, unsigned startIndex);
 *
 *     /** Appends SkRRect to SkPathBuilder, creating a new closed contour. If dir is kCW_Direction,
 *         SkRRect winds clockwise. If dir is kCCW_Direction, SkRRect winds counterclockwise.
 *
 *         After appending, SkPathBuilder may be empty, or may contain: SkRect, oval, or SkRRect.
 *
 *         @param rrect  SkRRect to add
 *         @param dir    SkPath::Direction to wind SkRRect
 *         @param start  index of initial point of SkRRect
 *         @return       reference to SkPathBuilder
 *     */
 *     SkPathBuilder& addRRect(const SkRRect& rrect, SkPathDirection, unsigned start);
 *
 *     /** Appends SkRRect to SkPathBuilder, creating a new closed contour. If dir is kCW_Direction,
 *         SkRRect starts at top-left of the lower-left corner and winds clockwise. If dir is
 *         kCCW_Direction, SkRRect starts at the bottom-left of the upper-left corner and winds
 *         counterclockwise.
 *
 *         After appending, SkPathBuilder may be empty, or may contain: SkRect, oval, or SkRRect.
 *
 *         @param rrect  SkRRect to add
 *         @param dir    SkPath::Direction to wind SkRRect
 *         @return       reference to SkPathBuilder
 *     */
 *     SkPathBuilder& addRRect(const SkRRect& rrect, SkPathDirection dir = SkPathDirection::kDefault) {
 *         // legacy start indices: 6 (CW) and 7 (CCW)
 *         return this->addRRect(rrect, dir, dir == SkPathDirection::kCW ? 6 : 7);
 *     }
 *
 *     /** Adds oval to SkPathBuilder, appending kMove_Verb, four kConic_Verb, and kClose_Verb.
 *         Oval is upright ellipse bounded by SkRect oval with radii equal to half oval width
 *         and half oval height. Oval begins at start and continues
 *         clockwise if dir is kCW_Direction, counterclockwise if dir is kCCW_Direction.
 *
 *         @param oval   bounds of ellipse added
 *         @param dir    SkPath::Direction to wind ellipse
 *         @return       reference to SkPath
 *
 *         example: https://fiddle.skia.org/c/@Path_addOval_2
 *     */
 *     SkPathBuilder& addOval(const SkRect& oval, SkPathDirection dir = SkPathDirection::kDefault) {
 *         // legacy start index: 1
 *         return this->addOval(oval, dir, 1);
 *     }
 *
 *     /** Adds circle centered at (x, y) of size radius to SkPathBuilder, appending kMove_Verb,
 *         four kConic_Verb, and kClose_Verb. Circle begins at: (x + radius, y), continuing
 *         clockwise if dir is kCW_Direction, and counterclockwise if dir is kCCW_Direction.
 *
 *         Has no effect if radius is zero or negative.
 *
 *         @param x       center of circle
 *         @param y       center of circle
 *         @param radius  distance from center to edge
 *         @param dir     SkPath::Direction to wind circle
 *         @return        reference to SkPathBuilder
 *     */
 *     SkPathBuilder& addCircle(SkScalar x, SkScalar y, SkScalar radius,
 *                              SkPathDirection dir = SkPathDirection::kDefault);
 *
 *     /** Adds contour created from line array, adding (pts.size() - 1) line segments.
 *         Contour added starts at pts[0], then adds a line for every additional SkPoint
 *         in pts array. If close is true, appends kClose_Verb to SkPath, connecting
 *         pts[count - 1] and pts[0].
 *
 *         @param pts    array of line sharing end and start SkPoint
 *         @param close  true to add line connecting contour end and start
 *         @return       reference to SkPath
 *     */
 *     SkPathBuilder& addPolygon(SkSpan<const SkPoint> pts, bool close);
 *
 *     /** Appends src to SkPathBuilder, offset by (dx, dy).
 *
 *         If mode is kAppend_AddPathMode, src verb array, SkPoint array, and conic weights are
 *         added unaltered. If mode is kExtend_AddPathMode, add line before appending
 *         verbs, SkPoint, and conic weights.
 *
 *         @param src   SkPath verbs, SkPoint, and conic weights to add
 *         @param dx    offset added to src SkPoint array x-axis coordinates
 *         @param dy    offset added to src SkPoint array y-axis coordinates
 *         @param mode  kAppend_AddPathMode or kExtend_AddPathMode
 *         @return      reference to SkPathBuilder
 *     */
 *     SkPathBuilder& addPath(const SkPath& src, SkScalar dx, SkScalar dy,
 *                            SkPath::AddPathMode mode = SkPath::kAppend_AddPathMode);
 *
 *     /** Appends src to SkPathBuilder.
 *
 *         If mode is kAppend_AddPathMode, src verb array, SkPoint array, and conic weights are
 *         added unaltered. If mode is kExtend_AddPathMode, add line before appending
 *         verbs, SkPoint, and conic weights.
 *
 *         @param src   SkPath verbs, SkPoint, and conic weights to add
 *         @param mode  kAppend_AddPathMode or kExtend_AddPathMode
 *         @return      reference to SkPathBuilder
 *     */
 *     SkPathBuilder& addPath(const SkPath& src,
 *                            SkPath::AddPathMode mode = SkPath::kAppend_AddPathMode) {
 *         return this->addPath(src, SkMatrix::I(), mode);
 *     }
 *
 *     /** Appends src to SkPathBuilder, transformed by matrix. Transformed curves may have different
 *         verbs, SkPoint, and conic weights.
 *
 *         If mode is kAppend_AddPathMode, src verb array, SkPoint array, and conic weights are
 *         added unaltered. If mode is kExtend_AddPathMode, add line before appending
 *         verbs, SkPoint, and conic weights.
 *
 *         @param src     SkPath verbs, SkPoint, and conic weights to add
 *         @param matrix  transform applied to src
 *         @param mode    kAppend_AddPathMode or kExtend_AddPathMode
 *         @return        reference to SkPathBuilder
 *     */
 *     SkPathBuilder& addPath(const SkPath& src, const SkMatrix& matrix,
 *                            SkPath::AddPathMode mode = SkPath::AddPathMode::kAppend_AddPathMode);
 *
 *     // Performance hint, to reserve extra storage for subsequent calls to lineTo, quadTo, etc.
 *
 *     /** Grows SkPathBuilder verb array and SkPoint array to contain additional space.
 *         May improve performance and use less memory by
 *         reducing the number and size of allocations when creating SkPathBuilder.
 *
 *         @param extraPtCount     number of additional SkPoint to allocate
 *         @param extraVerbCount   number of additional verbs
 *         @param extraConicCount  number of additional conic weights
 *     */
 *     void incReserve(int extraPtCount, int extraVerbCount, int extraConicCount);
 *
 *     /** Grows SkPathBuilder verb array and SkPoint array to contain additional space.
 *         May improve performance and use less memory by
 *         reducing the number and size of allocations when creating SkPathBuilder.
 *
 *         @param extraPtCount    number of additional SkPoints and verbs to allocate
 *     */
 *     void incReserve(int extraPtCount) {
 *         this->incReserve(extraPtCount, extraPtCount, 0);
 *     }
 *
 *     /** Offsets SkPoint array by (dx, dy).
 *
 *         @param dx   offset added to SkPoint array x-axis coordinates
 *         @param dy   offset added to SkPoint array y-axis coordinates
 *     */
 *     SkPathBuilder& offset(SkScalar dx, SkScalar dy);
 *
 *     /** Transforms verb array, SkPoint array, and weight by matrix.
 *         transform may change verbs and increase their number.
 *
 *         @param matrix  SkMatrix to apply to SkPath
 *         @param pc      whether to apply perspective clipping
 *     */
 *     SkPathBuilder& transform(const SkMatrix& matrix);
 *
 *     /*
 *      *  Returns true if the builder is empty, or all of its points are finite.
 *      */
 *     bool isFinite() const;
 *
 *     /** Replaces SkPathFillType with its inverse. The inverse of SkPathFillType describes the area
 *         unmodified by the original SkPathFillType.
 *     */
 *     SkPathBuilder& toggleInverseFillType() {
 *         fFillType = SkPathFillType_ToggleInverse(fFillType);
 *         return *this;
 *     }
 *
 *     /** Returns if SkPath is empty.
 *         Empty SkPathBuilder may have FillType but has no SkPoint, SkPath::Verb, or conic weight.
 *         SkPathBuilder() constructs empty SkPathBuilder; reset() and rewind() make SkPath empty.
 *
 *         @return  true if the path contains no SkPath::Verb array
 *     */
 *     bool isEmpty() const { return fVerbs.empty(); }
 *
 *     /** Returns last point on SkPathBuilder. Returns nullopt if SkPoint array is empty.
 *
 *         @return  last SkPoint if SkPoint array contains one or more SkPoint, otherwise nullopt
 *
 *         example: https://fiddle.skia.org/c/@Path_getLastPt
 *     */
 *     std::optional<SkPoint> getLastPt() const;
 *
 *     /** Change the point at the specified index (see countPoints()).
 *      *  If index is out of range, the call does nothing.
 *      *
 *      *  @param index which point to replace
 *      *  @param p the new point value
 *      */
 *     void setPoint(size_t index, SkPoint p);
 *
 *     /** Sets the last point on the path. If SkPoint array is empty, append kMove_Verb to
 *         verb array and append p to SkPoint array.
 *
 *         @param x  x-value of last point
 *         @param y  y-value of last point
 *     */
 *     void setLastPt(SkScalar x, SkScalar y);
 *
 *     /** Returns the number of points in SkPathBuilder.
 *         SkPoint count is initially zero.
 *
 *         @return  SkPathBuilder SkPoint array length
 *     */
 *     int countPoints() const { return fPts.size(); }
 *
 *     /** Returns if SkPathFillType describes area outside SkPath geometry. The inverse fill area
 *         extends indefinitely.
 *
 *         @return  true if FillType is kInverseWinding or kInverseEvenOdd
 *     */
 *     bool isInverseFillType() const { return SkPathFillType_IsInverse(fFillType); }
 *
 * #ifdef SK_SUPPORT_UNSPANNED_APIS
 *     SkPathBuilder& addPolygon(const SkPoint pts[], int count, bool close) {
 *         return this->addPolygon({pts, count}, close);
 *     }
 *     SkPathBuilder& polylineTo(const SkPoint pts[], int count) {
 *         return this->polylineTo({pts, count});
 *     }
 * #endif
 *
 *     SkSpan<const SkPoint> points() const {
 *         return fPts;
 *     }
 *     SkSpan<const SkPathVerb> verbs() const {
 *         return fVerbs;
 *     }
 *     SkSpan<const float> conicWeights() const {
 *         return fConicWeights;
 *     }
 *
 *     SkPathBuilder& addRaw(const SkPathRaw&);
 *
 *     SkPathIter iter() const;
 *
 *     enum class DumpFormat {
 *         kDecimal,
 *         kHex,
 *     };
 *     SkString dumpToString(DumpFormat = DumpFormat::kDecimal) const;
 *     void dump(DumpFormat) const;
 *     // can't use default argument easily in debugger, so we name this
 *     // helper explicitly.
 *     void dump() const { this->dump(DumpFormat::kDecimal); }
 *
 *     bool contains(SkPoint) const;
 *
 * private:
 *     PointsArray fPts;
 *     VerbsArray fVerbs;
 *     ConicWeightsArray fConicWeights;
 *
 *     SkPathFillType  fFillType;
 *     bool            fIsVolatile;
 *     SkPathConvexity fConvexity;
 *
 *     unsigned    fSegmentMask;
 *     int         fLastMoveIndex; // only needed until SkPath is immutable
 *
 *     SkPathIsAType fType = SkPathIsAType::kGeneral;
 *     SkPathIsAData fIsA {};
 *
 *     // called right before we add a (non-move) verb
 *     void ensureMove() {
 *         fType = SkPathIsAType::kGeneral;
 *         if (fVerbs.empty()) {
 *             this->moveTo({0, 0});
 *         } else if (fVerbs.back() == SkPathVerb::kClose) {
 *             this->moveTo(fPts[fLastMoveIndex]);
 *         }
 *     }
 *
 *     bool isZeroLengthSincePoint(int startPtIndex) const;
 *
 *     SkPathBuilder& privateReverseAddPath(const SkPath&);
 *     SkPathBuilder& privateReversePathTo(const SkPath&);
 *
 *     std::tuple<SkPoint*, SkScalar*> growForVerbsInPath(const SkPath& path);
 *
 *     friend class SkPathPriv;
 *     friend class SkStroke;
 *     friend class SkPathStroker;
 * }
 * ```
 */
public data class SkPathBuilder public constructor(
  /**
   * C++ original:
   * ```cpp
   * PointsArray fPts
   * ```
   */
  private var fPts: Int,
  /**
   * C++ original:
   * ```cpp
   * VerbsArray fVerbs
   * ```
   */
  private var fVerbs: Int,
  /**
   * C++ original:
   * ```cpp
   * ConicWeightsArray fConicWeights
   * ```
   */
  private var fConicWeights: Int,
  /**
   * C++ original:
   * ```cpp
   * SkPathFillType  fFillType
   * ```
   */
  private var fFillType: Int,
  /**
   * C++ original:
   * ```cpp
   * bool            fIsVolatile
   * ```
   */
  private var fIsVolatile: Boolean,
  /**
   * C++ original:
   * ```cpp
   * SkPathConvexity fConvexity
   * ```
   */
  private var fConvexity: Int,
  /**
   * C++ original:
   * ```cpp
   * unsigned    fSegmentMask
   * ```
   */
  private var fSegmentMask: UInt,
  /**
   * C++ original:
   * ```cpp
   * int         fLastMoveIndex
   * ```
   */
  private var fLastMoveIndex: Int,
  /**
   * C++ original:
   * ```cpp
   * SkPathIsAType fType
   * ```
   */
  private var fType: Int,
  /**
   * C++ original:
   * ```cpp
   * SkPathIsAData fIsA
   * ```
   */
  private var fIsA: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& operator=(const SkPath&)
   * ```
   */
  public fun assign(param0: SkPath) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& operator=(const SkPathBuilder&) = default
   * ```
   */
  public fun assign(param0: SkPathBuilder) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkPathBuilder::operator==(const SkPathBuilder& o) const {
   *     // quick-accept
   *     if (this == &o) {
   *         return true;
   *     }
   *     // quick-reject
   *     if (fSegmentMask != o.fSegmentMask || fFillType != o.fFillType) {
   *         return false;
   *     }
   *     // deep compare
   *     return fVerbs == o.fVerbs && fPts == o.fPts && fConicWeights == o.fConicWeights;
   * }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator!=(const SkPathBuilder& o) const { return !(*this == o); }
   * ```
   */
  public fun fillType(): Int {
    TODO("Implement fillType")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathFillType fillType() const { return fFillType; }
   * ```
   */
  public fun computeFiniteBounds(): SkRRect? {
    TODO("Implement computeFiniteBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * std::optional<SkRect> SkPathBuilder::computeFiniteBounds() const {
   *     return SkPathPriv::TrimmedBounds(this->points(), this->verbs());
   * }
   * ```
   */
  public fun computeTightBounds(): SkRRect? {
    TODO("Implement computeTightBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * std::optional<SkRect> SkPathBuilder::computeTightBounds() const {
   *     if (!this->isFinite()) {
   *         return {};
   *     }
   *     return SkPathPriv::ComputeTightBounds(this->points(), this->verbs(), this->conicWeights());
   * }
   * ```
   */
  public fun computeBounds(): SkRRect {
    TODO("Implement computeBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect computeBounds() const {
   *         if (auto bounds = this->computeFiniteBounds()) {
   *             return *bounds;
   *         }
   *         return SkRect::MakeEmpty();
   *     }
   * ```
   */
  public fun snapshot(mx: SkMatrix? = TODO()): Int {
    TODO("Implement snapshot")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPath SkPathBuilder::snapshot(const SkMatrix* mx) const {
   *     if (!mx) {
   *         mx = &SkMatrix::I();
   *     }
   *
   *     sk_sp<SkPathData> pdata;
   *     if (auto raw = SkPathPriv::Raw(*this, SkResolveConvexity::kNo)) {
   *         pdata = SkPathData::MakeTransform(*raw, *mx);
   *     }
   *     if (pdata && fType != SkPathIsAType::kGeneral) {
   *         pdata->setupIsA(fType, fIsA.fDirection, fIsA.fStartIndex);
   *     }
   *     return SkPath::MakeNullCheck(std::move(pdata), fFillType, fIsVolatile);
   * }
   * ```
   */
  public fun detach(mx: SkMatrix? = TODO()): Int {
    TODO("Implement detach")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPath SkPathBuilder::detach(const SkMatrix* mx) {
   *     auto path = this->snapshot(mx);
   *     this->reset();
   *     return path;
   * }
   * ```
   */
  public fun snapshotData(): Int {
    TODO("Implement snapshotData")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkPathData> SkPathBuilder::snapshotData() const {
   *     if (fVerbs.size() <= 1) {
   *         return SkPathData::Empty();
   *     }
   *
   *     switch (fType) {
   *         case SkPathIsAType::kGeneral:
   *             break;
   *         case SkPathIsAType::kOval:
   *             if (auto r = SkRect::Bounds(fPts)) {
   *                 return SkPathData::Oval(*r, fIsA.fDirection, fIsA.fStartIndex);
   *             }
   *             return nullptr;
   *         case SkPathIsAType::kRRect:
   *             if (auto r = SkRect::Bounds(fPts)) {
   *                 return SkPathData::RRect(SkPathPriv::DeduceRRectFromContour(*r, fPts, fVerbs),
   *                                          fIsA.fDirection, fIsA.fStartIndex);
   *             }
   *             return nullptr;
   *     }
   *     SkASSERT(fType == SkPathIsAType::kGeneral);
   *
   *     return SkPathData::Make(fPts, fVerbs, fConicWeights);
   * }
   * ```
   */
  public fun detachData(): Int {
    TODO("Implement detachData")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkPathData> SkPathBuilder::detachData() {
   *     auto data = this->snapshotData();
   *     this->reset();
   *     return data;
   * }
   * ```
   */
  public fun setFillType(ft: SkPathFillType): SkPathBuilder {
    TODO("Implement setFillType")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& setFillType(SkPathFillType ft) { fFillType = ft; return *this; }
   * ```
   */
  public fun setIsVolatile(isVolatile: Boolean): SkPathBuilder {
    TODO("Implement setIsVolatile")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& setIsVolatile(bool isVolatile) { fIsVolatile = isVolatile; return *this; }
   * ```
   */
  public fun reset(): SkPathBuilder {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& SkPathBuilder::reset() {
   *     fPts.clear();
   *     fVerbs.clear();
   *     fConicWeights.clear();
   *     fFillType = SkPathFillType::kDefault;
   *     fIsVolatile = false;
   *
   *     // these are internal state
   *
   *     fSegmentMask = 0;
   *     fLastMoveIndex = -1;        // illegal
   *
   *     fType      = SkPathIsAType::kGeneral;
   *     fConvexity = SkPathConvexity::kUnknown;
   *
   *     return *this;
   * }
   * ```
   */
  public fun moveTo(point: SkPoint): SkPathBuilder {
    TODO("Implement moveTo")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& SkPathBuilder::moveTo(SkPoint pt) {
   *     if (!fVerbs.empty() && fVerbs.back() == SkPathVerb::kMove) {
   *         fPts.back() = pt;
   *
   *         SkASSERT(fType != SkPathIsAType::kOval && fType != SkPathIsAType::kRRect);
   *         SkASSERT(fConvexity == SkPathConvexity::kUnknown);
   *         SkASSERT(fLastMoveIndex == SkToInt(fPts.size()) - 1);
   *     } else {
   *         fLastMoveIndex = SkToInt(fPts.size());
   *
   *         fPts.push_back(pt);
   *         fVerbs.push_back(SkPathVerb::kMove);
   *
   *         if (fType == SkPathIsAType::kOval || fType == SkPathIsAType::kRRect) {
   *             fType = SkPathIsAType::kGeneral;
   *         }
   *         fConvexity = SkPathConvexity::kUnknown;
   *     }
   *
   *     return *this;
   * }
   * ```
   */
  public fun moveTo(x: SkScalar, y: SkScalar): SkPathBuilder {
    TODO("Implement moveTo")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& moveTo(SkScalar x, SkScalar y) {
   *         return this->moveTo(SkPoint::Make(x, y));
   *     }
   * ```
   */
  public fun lineTo(pt: SkPoint): SkPathBuilder {
    TODO("Implement lineTo")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& SkPathBuilder::lineTo(SkPoint pt) {
   *     this->ensureMove();
   *
   *     fPts.push_back(pt);
   *     fVerbs.push_back(SkPathVerb::kLine);
   *
   *     fSegmentMask |= kLine_SkPathSegmentMask;
   *     return *this;
   * }
   * ```
   */
  public fun lineTo(x: SkScalar, y: SkScalar): SkPathBuilder {
    TODO("Implement lineTo")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& lineTo(SkScalar x, SkScalar y) { return this->lineTo(SkPoint::Make(x, y)); }
   * ```
   */
  public fun quadTo(pt1: SkPoint, pt2: SkPoint): SkPathBuilder {
    TODO("Implement quadTo")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& SkPathBuilder::quadTo(SkPoint pt1, SkPoint pt2) {
   *     this->ensureMove();
   *
   *     SkPoint* p = fPts.push_back_n(2);
   *     p[0] = pt1;
   *     p[1] = pt2;
   *     fVerbs.push_back(SkPathVerb::kQuad);
   *
   *     fSegmentMask |= kQuad_SkPathSegmentMask;
   *     return *this;
   * }
   * ```
   */
  public fun quadTo(
    x1: SkScalar,
    y1: SkScalar,
    x2: SkScalar,
    y2: SkScalar,
  ): SkPathBuilder {
    TODO("Implement quadTo")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& quadTo(SkScalar x1, SkScalar y1, SkScalar x2, SkScalar y2) {
   *         return this->quadTo(SkPoint::Make(x1, y1), SkPoint::Make(x2, y2));
   *     }
   * ```
   */
  public fun quadTo(pts: Array<SkPoint>): SkPathBuilder {
    TODO("Implement quadTo")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& quadTo(const SkPoint pts[2]) { return this->quadTo(pts[0], pts[1]); }
   * ```
   */
  public fun conicTo(
    pt1: SkPoint,
    pt2: SkPoint,
    w: SkScalar,
  ): SkPathBuilder {
    TODO("Implement conicTo")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& SkPathBuilder::conicTo(SkPoint pt1, SkPoint pt2, SkScalar w) {
   *     this->ensureMove();
   *
   *     SkPoint* p = fPts.push_back_n(2);
   *     p[0] = pt1;
   *     p[1] = pt2;
   *     if (w == 1) {
   *         fVerbs.push_back(SkPathVerb::kQuad);
   *         fSegmentMask |= kQuad_SkPathSegmentMask;
   *     } else {
   *         fVerbs.push_back(SkPathVerb::kConic);
   *         fConicWeights.push_back(w);
   *         fSegmentMask |= kConic_SkPathSegmentMask;
   *     }
   *
   *     return *this;
   * }
   * ```
   */
  public fun conicTo(
    x1: SkScalar,
    y1: SkScalar,
    x2: SkScalar,
    y2: SkScalar,
    w: SkScalar,
  ): SkPathBuilder {
    TODO("Implement conicTo")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& conicTo(SkScalar x1, SkScalar y1, SkScalar x2, SkScalar y2, SkScalar w) {
   *         return this->conicTo(SkPoint::Make(x1, y1), SkPoint::Make(x2, y2), w);
   *     }
   * ```
   */
  public fun conicTo(pts: Array<SkPoint>, w: SkScalar): SkPathBuilder {
    TODO("Implement conicTo")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& conicTo(const SkPoint pts[2], SkScalar w) {
   *         return this->conicTo(pts[0], pts[1], w);
   *     }
   * ```
   */
  public fun cubicTo(
    pt1: SkPoint,
    pt2: SkPoint,
    pt3: SkPoint,
  ): SkPathBuilder {
    TODO("Implement cubicTo")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& SkPathBuilder::cubicTo(SkPoint pt1, SkPoint pt2, SkPoint pt3) {
   *     this->ensureMove();
   *
   *     SkPoint* p = fPts.push_back_n(3);
   *     p[0] = pt1;
   *     p[1] = pt2;
   *     p[2] = pt3;
   *     fVerbs.push_back(SkPathVerb::kCubic);
   *
   *     fSegmentMask |= kCubic_SkPathSegmentMask;
   *     return *this;
   * }
   * ```
   */
  public fun cubicTo(
    x1: SkScalar,
    y1: SkScalar,
    x2: SkScalar,
    y2: SkScalar,
    x3: SkScalar,
    y3: SkScalar,
  ): SkPathBuilder {
    TODO("Implement cubicTo")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& cubicTo(SkScalar x1, SkScalar y1, SkScalar x2, SkScalar y2, SkScalar x3, SkScalar y3) {
   *         return this->cubicTo(SkPoint::Make(x1, y1), SkPoint::Make(x2, y2), SkPoint::Make(x3, y3));
   *     }
   * ```
   */
  public fun cubicTo(pts: Array<SkPoint>): SkPathBuilder {
    TODO("Implement cubicTo")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& cubicTo(const SkPoint pts[3]) {
   *         return this->cubicTo(pts[0], pts[1], pts[2]);
   *     }
   * ```
   */
  public fun close(): SkPathBuilder {
    TODO("Implement close")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& SkPathBuilder::close() {
   *     // If this is a 2nd 'close', we just ignore it
   *     if (!fVerbs.empty() && fVerbs.back() != SkPathVerb::kClose) {
   *         this->ensureMove();
   *         fVerbs.push_back(SkPathVerb::kClose);
   *     }
   *     return *this;
   * }
   * ```
   */
  public fun polylineTo(pts: SkSpan<SkPoint>): SkPathBuilder {
    TODO("Implement polylineTo")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& SkPathBuilder::polylineTo(SkSpan<const SkPoint> pts) {
   *     if (!pts.empty()) {
   *         this->ensureMove();
   *
   *         const auto count = pts.size();
   *         this->incReserve(count, count, 0);
   *         memcpy(fPts.push_back_n(count), pts.data(), count * sizeof(SkPoint));
   *         memset(fVerbs.push_back_n(count), (uint8_t)SkPathVerb::kLine, count);
   *         fSegmentMask |= kLine_SkPathSegmentMask;
   *     }
   *     return *this;
   * }
   * ```
   */
  public fun rMoveTo(pt: SkVector): SkPathBuilder {
    TODO("Implement rMoveTo")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& SkPathBuilder::rMoveTo(SkVector pt) {
   *     SkPoint lastPt = {0,0}; // in case we're empty
   *     if (!fPts.empty()) {
   *         SkASSERT(fLastMoveIndex >= 0);
   *         if (fVerbs.back() == SkPathVerb::kClose) {
   *             lastPt = fPts[fLastMoveIndex];
   *         } else {
   *             lastPt = fPts.back();
   *         }
   *     }
   *     return this->moveTo(lastPt + pt);
   * }
   * ```
   */
  public fun rMoveTo(dx: SkScalar, dy: SkScalar): SkPathBuilder {
    TODO("Implement rMoveTo")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& rMoveTo(SkScalar dx, SkScalar dy) { return this->rMoveTo({dx, dy}); }
   * ```
   */
  public fun rLineTo(pt: SkVector): SkPathBuilder {
    TODO("Implement rLineTo")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& SkPathBuilder::rLineTo(SkVector p1) {
   *     this->ensureMove();
   *     return this->lineTo(fPts.back() + p1);
   * }
   * ```
   */
  public fun rLineTo(dx: SkScalar, dy: SkScalar): SkPathBuilder {
    TODO("Implement rLineTo")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& rLineTo(SkScalar dx, SkScalar dy) { return this->rLineTo({dx, dy}); }
   * ```
   */
  public fun rQuadTo(pt1: SkVector, pt2: SkVector): SkPathBuilder {
    TODO("Implement rQuadTo")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& SkPathBuilder::rQuadTo(SkVector p1, SkVector p2) {
   *     this->ensureMove();
   *     SkPoint base = fPts.back();
   *     return this->quadTo(base + p1, base + p2);
   * }
   * ```
   */
  public fun rQuadTo(
    dx1: SkScalar,
    dy1: SkScalar,
    dx2: SkScalar,
    dy2: SkScalar,
  ): SkPathBuilder {
    TODO("Implement rQuadTo")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& rQuadTo(SkScalar dx1, SkScalar dy1, SkScalar dx2, SkScalar dy2) {
   *         return this->rQuadTo({dx1, dy1}, {dx2, dy2});
   *     }
   * ```
   */
  public fun rConicTo(
    p1: SkVector,
    p2: SkVector,
    w: SkScalar,
  ): SkPathBuilder {
    TODO("Implement rConicTo")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& SkPathBuilder::rConicTo(SkVector p1, SkVector p2, SkScalar w) {
   *     this->ensureMove();
   *     SkPoint base = fPts.back();
   *     return this->conicTo(base + p1, base + p2, w);
   * }
   * ```
   */
  public fun rConicTo(
    dx1: SkScalar,
    dy1: SkScalar,
    dx2: SkScalar,
    dy2: SkScalar,
    w: SkScalar,
  ): SkPathBuilder {
    TODO("Implement rConicTo")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& rConicTo(SkScalar dx1, SkScalar dy1, SkScalar dx2, SkScalar dy2, SkScalar w) {
   *         return this->rConicTo({dx1, dy1}, {dx2, dy2}, w);
   *     }
   * ```
   */
  public fun rCubicTo(
    pt1: SkVector,
    pt2: SkVector,
    pt3: SkVector,
  ): SkPathBuilder {
    TODO("Implement rCubicTo")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& SkPathBuilder::rCubicTo(SkVector p1, SkVector p2, SkVector p3) {
   *     this->ensureMove();
   *     SkPoint base = fPts.back();
   *     return this->cubicTo(base + p1, base + p2, base + p3);
   * }
   * ```
   */
  public fun rCubicTo(
    dx1: SkScalar,
    dy1: SkScalar,
    dx2: SkScalar,
    dy2: SkScalar,
    dx3: SkScalar,
    dy3: SkScalar,
  ): SkPathBuilder {
    TODO("Implement rCubicTo")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& rCubicTo(SkScalar dx1, SkScalar dy1,
   *                             SkScalar dx2, SkScalar dy2,
   *                             SkScalar dx3, SkScalar dy3) {
   *         return this->rCubicTo({dx1, dy1}, {dx2, dy2}, {dx3, dy3});
   *     }
   * ```
   */
  public fun rArcTo(
    r: SkPoint,
    xAxisRotate: SkScalar,
    largeArc: ArcSize,
    sweep: SkPathDirection,
    dxdy: SkVector,
  ): SkPathBuilder {
    TODO("Implement rArcTo")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& SkPathBuilder::rArcTo(SkPoint r, SkScalar xAxisRotate, ArcSize largeArc,
   *                                      SkPathDirection sweep, SkVector dxdy) {
   *     const SkPoint currentPoint = this->getLastPt().value_or(SkPoint{0, 0});
   *     return this->arcTo(r, xAxisRotate, largeArc, sweep, currentPoint + dxdy);
   * }
   * ```
   */
  public fun arcTo(
    oval: SkRect,
    startAngleDeg: SkScalar,
    sweepAngleDeg: SkScalar,
    forceMoveTo: Boolean,
  ): SkPathBuilder {
    TODO("Implement arcTo")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& SkPathBuilder::arcTo(const SkRect& oval, SkScalar startAngle, SkScalar sweepAngle,
   *                                     bool forceMoveTo) {
   *     if (oval.width() < 0 || oval.height() < 0) {
   *         return *this;
   *     }
   *
   *     startAngle = SkScalarMod(startAngle, 360.0f);
   *
   *     if (fVerbs.empty()) {
   *         forceMoveTo = true;
   *     }
   *
   *     SkPoint lonePt;
   *     if (arc_is_lone_point(oval, startAngle, sweepAngle, &lonePt)) {
   *         return forceMoveTo ? this->moveTo(lonePt) : this->lineTo(lonePt);
   *     }
   *
   *     SkVector startV, stopV;
   *     SkPathDirection dir;
   *     angles_to_unit_vectors(startAngle, sweepAngle, &startV, &stopV, &dir);
   *
   *     SkPoint singlePt;
   *
   *     // Adds a move-to to 'pt' if forceMoveTo is true. Otherwise a lineTo unless we're sufficiently
   *     // close to 'pt' currently. This prevents spurious lineTos when adding a series of contiguous
   *     // arcs from the same oval.
   *     auto addPt = [forceMoveTo, this](const SkPoint& pt) {
   *         if (forceMoveTo) {
   *             this->moveTo(pt);
   *         } else if (!nearly_equal(fPts.back(), pt)) {
   *             this->lineTo(pt);
   *         }
   *     };
   *
   *     // At this point, we know that the arc is not a lone point, but startV == stopV
   *     // indicates that the sweepAngle is too small such that angles_to_unit_vectors
   *     // cannot handle it.
   *     if (startV == stopV) {
   *         SkScalar endAngle = SkDegreesToRadians(startAngle + sweepAngle);
   *         SkScalar radiusX = oval.width() / 2;
   *         SkScalar radiusY = oval.height() / 2;
   *         // We do not use SkScalar[Sin|Cos]SnapToZero here. When sin(startAngle) is 0 and sweepAngle
   *         // is very small and radius is huge, the expected behavior here is to draw a line. But
   *         // calling SkScalarSinSnapToZero will make sin(endAngle) be 0 which will then draw a dot.
   *         singlePt.set(oval.centerX() + radiusX * SkScalarCos(endAngle),
   *                      oval.centerY() + radiusY * SkScalarSin(endAngle));
   *         addPt(singlePt);
   *         return *this;
   *     }
   *
   *     SkConic conics[SkConic::kMaxConicsForArc];
   *     int count = build_arc_conics(oval, startV, stopV, dir, conics, &singlePt);
   *     if (count) {
   *         this->incReserve(count * 2 + 1);
   *         const SkPoint& pt = conics[0].fPts[0];
   *         addPt(pt);
   *         for (int i = 0; i < count; ++i) {
   *             this->conicTo(conics[i].fPts[1], conics[i].fPts[2], conics[i].fW);
   *         }
   *     } else {
   *         addPt(singlePt);
   *     }
   *     return *this;
   * }
   * ```
   */
  public fun arcTo(
    p1: SkPoint,
    p2: SkPoint,
    radius: SkScalar,
  ): SkPathBuilder {
    TODO("Implement arcTo")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& SkPathBuilder::arcTo(SkPoint p1, SkPoint p2, SkScalar radius) {
   *     this->ensureMove();
   *
   *     if (radius == 0) {
   *         return this->lineTo(p1);
   *     }
   *
   *     // need to know our prev pt so we can construct tangent vectors
   *     SkPoint start = fPts.back();
   *
   *     // need double precision for these calcs.
   *     skvx::double2 befored = normalize(skvx::double2{p1.fX - start.fX, p1.fY - start.fY});
   *     skvx::double2 afterd = normalize(skvx::double2{p2.fX - p1.fX, p2.fY - p1.fY});
   *     double cosh = dot(befored, afterd);
   *     double sinh = cross(befored, afterd);
   *
   *     // If the previous point equals the first point, befored will be denormalized.
   *     // If the two points equal, afterd will be denormalized.
   *     // If the second point equals the first point, sinh will be zero.
   *     // In all these cases, we cannot construct an arc, so we construct a line to the first point.
   *     if (!isfinite(befored) || !isfinite(afterd) || SkScalarNearlyZero(SkDoubleToScalar(sinh))) {
   *         return this->lineTo(p1);
   *     }
   *
   *     // safe to convert back to floats now
   *     SkScalar dist = SkScalarAbs(SkDoubleToScalar(radius * (1 - cosh) / sinh));
   *     SkScalar xx = p1.fX - dist * befored[0];
   *     SkScalar yy = p1.fY - dist * befored[1];
   *
   *     SkVector after = SkVector::Make(afterd[0], afterd[1]);
   *     after.setLength(dist);
   *     this->lineTo(xx, yy);
   *     SkScalar weight = SkScalarSqrt(SkDoubleToScalar(SK_ScalarHalf + cosh * 0.5));
   *     return this->conicTo(p1, p1 + after, weight);
   * }
   * ```
   */
  public fun arcTo(
    r: SkPoint,
    xAxisRotate: SkScalar,
    largeArc: ArcSize,
    sweep: SkPathDirection,
    xy: SkPoint,
  ): SkPathBuilder {
    TODO("Implement arcTo")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& SkPathBuilder::arcTo(SkPoint rad, SkScalar angle, SkPathBuilder::ArcSize arcLarge,
   *                                     SkPathDirection arcSweep, SkPoint endPt) {
   *     this->ensureMove();
   *
   *     const SkPoint srcPts[2] = { fPts.back(), endPt };
   *
   *     // If rx = 0 or ry = 0 then this arc is treated as a straight line segment (a "lineto")
   *     // joining the endpoints.
   *     // http://www.w3.org/TR/SVG/implnote.html#ArcOutOfRangeParameters
   *     if (!rad.fX || !rad.fY) {
   *         return this->lineTo(endPt);
   *     }
   *     // If the current point and target point for the arc are identical, it should be treated as a
   *     // zero length path. This ensures continuity in animations.
   *     if (srcPts[0] == srcPts[1]) {
   *         return this->lineTo(endPt);
   *     }
   *     SkScalar rx = SkScalarAbs(rad.fX);
   *     SkScalar ry = SkScalarAbs(rad.fY);
   *     SkVector midPointDistance = srcPts[0] - srcPts[1];
   *     midPointDistance *= 0.5f;
   *
   *     SkMatrix pointTransform;
   *     pointTransform.setRotate(-angle);
   *
   *     SkPoint transformedMidPoint = pointTransform.mapPoint(midPointDistance);
   *     SkScalar squareRx = rx * rx;
   *     SkScalar squareRy = ry * ry;
   *     SkScalar squareX = transformedMidPoint.fX * transformedMidPoint.fX;
   *     SkScalar squareY = transformedMidPoint.fY * transformedMidPoint.fY;
   *
   *     // Check if the radii are big enough to draw the arc, scale radii if not.
   *     // http://www.w3.org/TR/SVG/implnote.html#ArcCorrectionOutOfRangeRadii
   *     SkScalar radiiScale = squareX / squareRx + squareY / squareRy;
   *     if (radiiScale > 1) {
   *         radiiScale = SkScalarSqrt(radiiScale);
   *         rx *= radiiScale;
   *         ry *= radiiScale;
   *     }
   *
   *     pointTransform.setScale(1 / rx, 1 / ry);
   *     pointTransform.preRotate(-angle);
   *
   *     SkPoint unitPts[2];
   *     pointTransform.mapPoints(unitPts, srcPts);
   *     SkVector delta = unitPts[1] - unitPts[0];
   *
   *     SkScalar d = delta.fX * delta.fX + delta.fY * delta.fY;
   *     SkScalar scaleFactorSquared = std::max(1 / d - 0.25f, 0.f);
   *
   *     SkScalar scaleFactor = SkScalarSqrt(scaleFactorSquared);
   *     if ((arcSweep == SkPathDirection::kCCW) != SkToBool(arcLarge)) {  // flipped from the original implementation
   *         scaleFactor = -scaleFactor;
   *     }
   *     delta.scale(scaleFactor);
   *     SkPoint centerPoint = unitPts[0] + unitPts[1];
   *     centerPoint *= 0.5f;
   *     centerPoint.offset(-delta.fY, delta.fX);
   *     unitPts[0] -= centerPoint;
   *     unitPts[1] -= centerPoint;
   *     SkScalar theta1 = SkScalarATan2(unitPts[0].fY, unitPts[0].fX);
   *     SkScalar theta2 = SkScalarATan2(unitPts[1].fY, unitPts[1].fX);
   *     SkScalar thetaArc = theta2 - theta1;
   *     if (thetaArc < 0 && (arcSweep == SkPathDirection::kCW)) {  // arcSweep flipped from the original implementation
   *         thetaArc += SK_ScalarPI * 2;
   *     } else if (thetaArc > 0 && (arcSweep != SkPathDirection::kCW)) {  // arcSweep flipped from the original implementation
   *         thetaArc -= SK_ScalarPI * 2;
   *     }
   *
   *     // Very tiny angles cause our subsequent math to go wonky (skbug.com/40040578)
   *     // so we do a quick check here. The precise tolerance amount is just made up.
   *     // PI/million happens to fix the bug in 9272, but a larger value is probably
   *     // ok too.
   *     if (SkScalarAbs(thetaArc) < (SK_ScalarPI / (1000 * 1000))) {
   *         return this->lineTo(endPt);
   *     }
   *
   *     pointTransform.setRotate(angle);
   *     pointTransform.preScale(rx, ry);
   *
   *     // the arc may be slightly bigger than 1/4 circle, so allow up to 1/3rd
   *     int segments = SkScalarCeilToInt(SkScalarAbs(thetaArc / (2 * SK_ScalarPI / 3)));
   *     SkScalar thetaWidth = thetaArc / segments;
   *     SkScalar t = SkScalarTan(0.5f * thetaWidth);
   *     if (!SkIsFinite(t)) {
   *         return *this;
   *     }
   *     SkScalar startTheta = theta1;
   *     SkScalar w = SkScalarSqrt(SK_ScalarHalf + SkScalarCos(thetaWidth) * SK_ScalarHalf);
   *     auto scalar_is_integer = [](SkScalar scalar) -> bool {
   *         return scalar == SkScalarFloorToScalar(scalar);
   *     };
   *     bool expectIntegers = SkScalarNearlyZero(SK_ScalarPI/2 - SkScalarAbs(thetaWidth)) &&
   *         scalar_is_integer(rx) && scalar_is_integer(ry) &&
   *         scalar_is_integer(endPt.fX) && scalar_is_integer(endPt.fY);
   *
   *     for (int i = 0; i < segments; ++i) {
   *         SkScalar endTheta    = startTheta + thetaWidth,
   *                  sinEndTheta = SkScalarSinSnapToZero(endTheta),
   *                  cosEndTheta = SkScalarCosSnapToZero(endTheta);
   *
   *         unitPts[1].set(cosEndTheta, sinEndTheta);
   *         unitPts[1] += centerPoint;
   *         unitPts[0] = unitPts[1];
   *         unitPts[0].offset(t * sinEndTheta, -t * cosEndTheta);
   *         SkPoint mapped[2];
   *         pointTransform.mapPoints(mapped, unitPts);
   *         /*
   *         Computing the arc width introduces rounding errors that cause arcs to start
   *         outside their marks. A round rect may lose convexity as a result. If the input
   *         values are on integers, place the conic on integers as well.
   *          */
   *         if (expectIntegers) {
   *             for (SkPoint& point : mapped) {
   *                 point.fX = SkScalarRoundToScalar(point.fX);
   *                 point.fY = SkScalarRoundToScalar(point.fY);
   *             }
   *         }
   *         this->conicTo(mapped[0], mapped[1], w);
   *         startTheta = endTheta;
   *     }
   *
   *     // The final point should match the input point (by definition); replace it to
   *     // ensure that rounding errors in the above math don't cause any problems.
   *     fPts.back() = endPt;
   *     return *this;
   * }
   * ```
   */
  public fun addArc(
    oval: SkRect,
    startAngleDeg: SkScalar,
    sweepAngleDeg: SkScalar,
  ): SkPathBuilder {
    TODO("Implement addArc")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& SkPathBuilder::addArc(const SkRect& oval, SkScalar startAngle, SkScalar sweepAngle) {
   *     if (oval.isEmpty() || 0 == sweepAngle) {
   *         return *this;
   *     }
   *
   *     const SkScalar kFullCircleAngle = SkIntToScalar(360);
   *
   *     if (sweepAngle >= kFullCircleAngle || sweepAngle <= -kFullCircleAngle) {
   *         // We can treat the arc as an oval if it begins at one of our legal starting positions.
   *         // See SkPath::addOval() docs.
   *         SkScalar startOver90 = startAngle / 90.f;
   *         SkScalar startOver90I = SkScalarRoundToScalar(startOver90);
   *         SkScalar error = startOver90 - startOver90I;
   *         if (SkScalarNearlyEqual(error, 0)) {
   *             // Index 1 is at startAngle == 0.
   *             SkScalar startIndex = std::fmod(startOver90I + 1.f, 4.f);
   *             startIndex = startIndex < 0 ? startIndex + 4.f : startIndex;
   *             return this->addOval(oval, sweepAngle > 0 ? SkPathDirection::kCW : SkPathDirection::kCCW,
   *                                  (unsigned) startIndex);
   *         }
   *     }
   *     return this->arcTo(oval, startAngle, sweepAngle, true);
   * }
   * ```
   */
  public fun addLine(a: SkPoint, b: SkPoint): SkPathBuilder {
    TODO("Implement addLine")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& addLine(SkPoint a, SkPoint b) {
   *         return this->moveTo(a).lineTo(b);
   *     }
   * ```
   */
  public fun addRect(
    rect: SkRect,
    dir: SkPathDirection,
    startIndex: UInt,
  ): SkPathBuilder {
    TODO("Implement addRect")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& SkPathBuilder::addRect(const SkRect& rect, SkPathDirection dir, unsigned index) {
   *     const bool wasEmpty = (fSegmentMask == 0);
   *
   *     this->addRaw(SkPathRawShapes::Rect(rect, dir, index));
   *
   *     if (wasEmpty) {
   *         // now we're a rect
   *         fConvexity = SkPathDirection_ToConvexity(dir);
   *     }
   *     return *this;
   * }
   * ```
   */
  public fun addRect(rect: SkRect, dir: SkPathDirection = TODO()): SkPathBuilder {
    TODO("Implement addRect")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& addRect(const SkRect& rect, SkPathDirection dir = SkPathDirection::kDefault) {
   *         return this->addRect(rect, dir, 0);
   *     }
   * ```
   */
  public fun addOval(
    oval: SkRect,
    dir: SkPathDirection,
    startIndex: UInt,
  ): SkPathBuilder {
    TODO("Implement addOval")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& SkPathBuilder::addOval(const SkRect& oval, SkPathDirection dir, unsigned index) {
   *     const bool wasEmpty = (fSegmentMask == 0);
   *
   *     this->addRaw(SkPathRawShapes::Oval(oval, dir, index));
   *
   *     if (wasEmpty) {
   *         fType            = SkPathIsAType::kOval;
   *         fIsA.fDirection  = dir;
   *         fIsA.fStartIndex = index % 4;
   *         fConvexity = SkPathDirection_ToConvexity(dir);
   *     }
   *
   *     return *this;
   * }
   * ```
   */
  public fun addRRect(
    rrect: SkRRect,
    dir: SkPathDirection,
    start: UInt,
  ): SkPathBuilder {
    TODO("Implement addRRect")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& SkPathBuilder::addRRect(const SkRRect& rrect, SkPathDirection dir, unsigned index) {
   *     const SkRect& bounds = rrect.getBounds();
   *
   *     auto [asType, newIndex] = SkPathPriv::SimplifyRRect(rrect, index);
   *     switch (asType) {
   *         case SkPathPriv::RRectAsEnum::kRect:
   *             return this->addRect(bounds, dir, newIndex);
   *         case SkPathPriv::RRectAsEnum::kOval:
   *             return this->addOval(bounds, dir, newIndex);
   *         case SkPathPriv::RRectAsEnum::kRRect:
   *             // fall through ...
   *             break;
   *     }
   *
   *     const bool wasEmpty = (fSegmentMask == 0);
   *
   *     this->addRaw(SkPathRawShapes::RRect(rrect, dir, index));
   *
   *     if (wasEmpty) {
   *         fType            = SkPathIsAType::kRRect;
   *         fIsA.fDirection  = dir;
   *         fIsA.fStartIndex = index % 8;
   *         fConvexity = SkPathDirection_ToConvexity(dir);
   *     }
   *     return *this;
   * }
   * ```
   */
  public fun addRRect(rrect: SkRRect, dir: SkPathDirection = TODO()): SkPathBuilder {
    TODO("Implement addRRect")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& addRRect(const SkRRect& rrect, SkPathDirection dir = SkPathDirection::kDefault) {
   *         // legacy start indices: 6 (CW) and 7 (CCW)
   *         return this->addRRect(rrect, dir, dir == SkPathDirection::kCW ? 6 : 7);
   *     }
   * ```
   */
  public fun addOval(oval: SkRect, dir: SkPathDirection = TODO()): SkPathBuilder {
    TODO("Implement addOval")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& addOval(const SkRect& oval, SkPathDirection dir = SkPathDirection::kDefault) {
   *         // legacy start index: 1
   *         return this->addOval(oval, dir, 1);
   *     }
   * ```
   */
  public fun addCircle(
    x: SkScalar,
    y: SkScalar,
    radius: SkScalar,
    dir: SkPathDirection = TODO(),
  ): SkPathBuilder {
    TODO("Implement addCircle")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& SkPathBuilder::addCircle(SkScalar x, SkScalar y, SkScalar r, SkPathDirection dir) {
   *     if (r >= 0) {
   *         this->addOval(SkRect::MakeLTRB(x - r, y - r, x + r, y + r), dir);
   *     }
   *     return *this;
   * }
   * ```
   */
  public fun addPolygon(pts: SkSpan<SkPoint>, close: Boolean): SkPathBuilder {
    TODO("Implement addPolygon")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& SkPathBuilder::addPolygon(SkSpan<const SkPoint> pts, bool isClosed) {
   *     if (pts.empty()) {
   *         return *this;
   *     }
   *
   *     this->moveTo(pts[0]);
   *     this->polylineTo(pts.last(pts.size() - 1));
   *     if (isClosed) {
   *         this->close();
   *     }
   *     return *this;
   * }
   * ```
   */
  public fun addPath(
    src: SkPath,
    dx: SkScalar,
    dy: SkScalar,
    mode: SkPath.AddPathMode = TODO(),
  ): SkPathBuilder {
    TODO("Implement addPath")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& SkPathBuilder::addPath(const SkPath& path, SkScalar dx, SkScalar dy,
   *                                       SkPath::AddPathMode mode) {
   *     SkMatrix matrix = SkMatrix::Translate(dx, dy);
   *     return this->addPath(path, matrix, mode);
   * }
   * ```
   */
  public fun addPath(src: SkPath, mode: SkPath.AddPathMode = TODO()): SkPathBuilder {
    TODO("Implement addPath")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& addPath(const SkPath& src,
   *                            SkPath::AddPathMode mode = SkPath::kAppend_AddPathMode) {
   *         return this->addPath(src, SkMatrix::I(), mode);
   *     }
   * ```
   */
  public fun addPath(
    src: SkPath,
    matrix: SkMatrix,
    mode: SkPath.AddPathMode = TODO(),
  ): SkPathBuilder {
    TODO("Implement addPath")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& SkPathBuilder::addPath(const SkPath& src, const SkMatrix& matrix,
   *                                       SkPath::AddPathMode mode) {
   *     if (src.isEmpty()) {
   *         return *this;
   *     }
   *
   *     const bool canReplaceThis = (mode == SkPath::AddPathMode::kAppend_AddPathMode &&
   *                                  this->verbs().size() <= 1)
   *                               || this->verbs().empty();
   *     if (canReplaceThis && matrix.isIdentity()) {
   *         const SkPathFillType fillType = fFillType;
   *         *this = src;
   *         fFillType = fillType;
   *         return *this;
   *     }
   *
   *     // We're about to append - clear convexity.
   *     fConvexity = SkPathConvexity::kUnknown;
   *
   *     if (SkPath::AddPathMode::kAppend_AddPathMode == mode && !matrix.hasPerspective()) {
   *         const int lastMoveToIndex = SkPathPriv::FindLastMoveToIndex(src.verbs(), src.points().size());
   *         SkASSERT(lastMoveToIndex >= 0);
   *         fLastMoveIndex = lastMoveToIndex + this->countPoints();
   *
   *         auto [newPts, newWeights] = this->growForVerbsInPath(src);
   *         const size_t count = src.points().size();
   *         matrix.mapPoints({newPts, count}, src.points());
   *         if (auto conics = src.conicWeights(); !conics.empty()) {
   *             memcpy(newWeights, conics.data(), conics.size_bytes());
   *         }
   *         return *this;
   *     }
   *
   *     SkMatrixPriv::MapPtsProc mapPtsProc = SkMatrixPriv::GetMapPtsProc(matrix);
   *     bool firstVerb = true;
   *     for (auto [verb, pts, w] : SkPathPriv::Iterate(src)) {
   *         SkPoint mappedPts[3];
   *         switch (verb) {
   *             case SkPathVerb::kMove:
   *                 mapPtsProc(matrix, mappedPts, &pts[0], 1);
   *                 if (firstVerb && mode == SkPath::kExtend_AddPathMode && !isEmpty()) {
   *                     this->ensureMove(); // In case last contour is closed
   *                     std::optional<SkPoint> lastPt = this->getLastPt();
   *                     // don't add lineTo if it is degenerate
   *                     if (!lastPt.has_value() || lastPt.value() != mappedPts[0]) {
   *                         this->lineTo(mappedPts[0]);
   *                     }
   *                 } else {
   *                     this->moveTo(mappedPts[0]);
   *                 }
   *                 break;
   *             case SkPathVerb::kLine:
   *                 mapPtsProc(matrix, mappedPts, &pts[1], 1);
   *                 this->lineTo(mappedPts[0]);
   *                 break;
   *             case SkPathVerb::kQuad:
   *                 mapPtsProc(matrix, mappedPts, &pts[1], 2);
   *                 this->quadTo(mappedPts[0], mappedPts[1]);
   *                 break;
   *             case SkPathVerb::kConic:
   *                 mapPtsProc(matrix, mappedPts, &pts[1], 2);
   *                 this->conicTo(mappedPts[0], mappedPts[1], *w);
   *                 break;
   *             case SkPathVerb::kCubic:
   *                 mapPtsProc(matrix, mappedPts, &pts[1], 3);
   *                 this->cubicTo(mappedPts[0], mappedPts[1], mappedPts[2]);
   *                 break;
   *             case SkPathVerb::kClose:
   *                 this->close();
   *                 break;
   *         }
   *         firstVerb = false;
   *     }
   *     return *this;
   * }
   * ```
   */
  public fun incReserve(
    extraPtCount: Int,
    extraVerbCount: Int,
    extraConicCount: Int,
  ) {
    TODO("Implement incReserve")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPathBuilder::incReserve(int extraPtCount, int extraVbCount, int extraCnCount) {
   *     fPts.reserve_exact(Sk32_sat_add(fPts.size(), extraPtCount));
   *     fVerbs.reserve_exact(Sk32_sat_add(fVerbs.size(), extraVbCount));
   *     fConicWeights.reserve_exact(Sk32_sat_add(fConicWeights.size(), extraCnCount));
   * }
   * ```
   */
  public fun incReserve(extraPtCount: Int) {
    TODO("Implement incReserve")
  }

  /**
   * C++ original:
   * ```cpp
   * void incReserve(int extraPtCount) {
   *         this->incReserve(extraPtCount, extraPtCount, 0);
   *     }
   * ```
   */
  public fun offset(dx: SkScalar, dy: SkScalar): SkPathBuilder {
    TODO("Implement offset")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& SkPathBuilder::offset(SkScalar dx, SkScalar dy) {
   *     for (auto& p : fPts) {
   *         p += {dx, dy};
   *     }
   *     return *this;
   * }
   * ```
   */
  public fun transform(matrix: SkMatrix): SkPathBuilder {
    TODO("Implement transform")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& SkPathBuilder::transform(const SkMatrix& matrix) {
   *     if (matrix.isIdentity() || this->isEmpty()) {
   *         return *this;
   *     }
   *
   *     if (matrix.hasPerspective()) {
   *         SkPath src = this->detach();
   *
   *         // remember this from before the detach()
   *         this->setFillType(src.getFillType());
   *
   *         SkPath clipped;
   *         if (SkPathPriv::PerspectiveClip(src, matrix, &clipped)) {
   *             src = std::move(clipped);
   *         }
   *
   *         for (auto [verb, pts, wt] : SkPathPriv::Iterate(src)) {
   *             switch (verb) {
   *                 case SkPathVerb::kMove:
   *                     this->moveTo(pts[0]);
   *                     break;
   *                 case SkPathVerb::kLine:
   *                     this->lineTo(pts[1]);
   *                     break;
   *                 case SkPathVerb::kQuad:
   *                     // promote the quad to a conic
   *                     this->conicTo(pts[1], pts[2], SkConic::TransformW(pts, SK_Scalar1, matrix));
   *                     break;
   *                 case SkPathVerb::kConic:
   *                     this->conicTo(pts[1], pts[2], SkConic::TransformW(pts, wt[0], matrix));
   *                     break;
   *                 case SkPathVerb::kCubic:
   *                     subdivide_cubic_to(this, pts);
   *                     break;
   *                 case SkPathVerb::kClose:
   *                     this->close();
   *                     break;
   *             }
   *         }
   *     } else {
   *
   *         // Can we maintain our special case shape?
   *         if (!matrix.rectStaysRect() || !SkPathPriv::IsAxisAligned(fPts)) {
   *             fType = SkPathIsAType::kGeneral;
   *             // lose convexity (just to be numerically safe)
   *             if (SkPathConvexity_IsConvex(fConvexity)) {
   *                 fConvexity = SkPathConvexity::kUnknown;
   *             }
   *         }
   *
   *         // If we're still a special case, check if we need to reverse our winding
   *         if (fType == SkPathIsAType::kOval || fType == SkPathIsAType::kRRect) {
   *             auto [dir, start] =
   *             SkPathPriv::TransformDirAndStart(matrix, fType == SkPathIsAType::kRRect,
   *                                              fIsA.fDirection, fIsA.fStartIndex);
   *             fIsA.fDirection  = dir;
   *             fIsA.fStartIndex = start;
   *         }
   *
   *     }
   *     matrix.mapPoints(fPts);
   *
   *     return *this;
   * }
   * ```
   */
  public fun isFinite(): Boolean {
    TODO("Implement isFinite")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkPathBuilder::isFinite() const {
   *     for (auto p : fPts) {
   *         if (!p.isFinite()) {
   *             return false;
   *         }
   *     }
   *     return true;
   * }
   * ```
   */
  public fun toggleInverseFillType(): SkPathBuilder {
    TODO("Implement toggleInverseFillType")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& toggleInverseFillType() {
   *         fFillType = SkPathFillType_ToggleInverse(fFillType);
   *         return *this;
   *     }
   * ```
   */
  public fun isEmpty(): Boolean {
    TODO("Implement isEmpty")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isEmpty() const { return fVerbs.empty(); }
   * ```
   */
  public fun getLastPt(): Int {
    TODO("Implement getLastPt")
  }

  /**
   * C++ original:
   * ```cpp
   * std::optional<SkPoint> SkPathBuilder::getLastPt() const {
   *     int count = this->fPts.size();
   *     if (count > 0) {
   *         return this->fPts.at(count - 1);
   *     }
   *     return std::nullopt;
   * }
   * ```
   */
  public fun setPoint(index: ULong, p: SkPoint) {
    TODO("Implement setPoint")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPathBuilder::setPoint(size_t index, SkPoint p) {
   *     if (index < (size_t)fPts.size()) {
   *         fPts[index] = p;
   *         fType = SkPathIsAType::kGeneral;
   *     }
   * }
   * ```
   */
  public fun setLastPt(x: SkScalar, y: SkScalar) {
    TODO("Implement setLastPt")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPathBuilder::setLastPt(SkScalar x, SkScalar y) {
   *     int count = fPts.size();
   *     if (count == 0) {
   *         this->moveTo(x, y);
   *     } else {
   *         fPts.at(count-1).set(x, y);
   *         fType = SkPathIsAType::kGeneral;
   *     }
   * }
   * ```
   */
  public fun countPoints(): Int {
    TODO("Implement countPoints")
  }

  /**
   * C++ original:
   * ```cpp
   * int countPoints() const { return fPts.size(); }
   * ```
   */
  public fun isInverseFillType(): Boolean {
    TODO("Implement isInverseFillType")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isInverseFillType() const { return SkPathFillType_IsInverse(fFillType); }
   * ```
   */
  public fun points(): Int {
    TODO("Implement points")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const SkPoint> points() const {
   *         return fPts;
   *     }
   * ```
   */
  public fun verbs(): Int {
    TODO("Implement verbs")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const SkPathVerb> verbs() const {
   *         return fVerbs;
   *     }
   * ```
   */
  public fun conicWeights(): Int {
    TODO("Implement conicWeights")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const float> conicWeights() const {
   *         return fConicWeights;
   *     }
   * ```
   */
  public fun addRaw(raw: SkPathRaw): SkPathBuilder {
    TODO("Implement addRaw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& SkPathBuilder::addRaw(const SkPathRaw& raw) {
   *     this->incReserve(raw.points().size(), raw.verbs().size(), raw.conics().size());
   *
   *     for (auto iter = raw.iter(); auto rec = iter.next();) {
   *         const auto pts = rec->fPoints;
   *         switch (rec->fVerb) {
   *             case SkPathVerb::kMove:  this->moveTo( pts[0]); break;
   *             case SkPathVerb::kLine:  this->lineTo( pts[1]); break;
   *             case SkPathVerb::kQuad:  this->quadTo( pts[1], pts[2]); break;
   *             case SkPathVerb::kConic: this->conicTo(pts[1], pts[2], rec->fConicWeight); break;
   *             case SkPathVerb::kCubic: this->cubicTo(pts[1], pts[2], pts[3]); break;
   *             case SkPathVerb::kClose: this->close(); break;
   *         }
   *     }
   *
   *     auto has_trailing_move = [](SkSpan<const SkPathVerb> vbs) {
   *         return vbs.size() > 0 && vbs.back() == SkPathVerb::kMove;
   *     };
   *
   *     // if the iterator 'trimmed' off a trialing move, we restore it here
   *     if (has_trailing_move(raw.verbs()) && !has_trailing_move(this->verbs())) {
   *         this->moveTo(raw.points().back());
   *     }
   *
   *     return *this;
   * }
   * ```
   */
  public fun iter(): Int {
    TODO("Implement iter")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathIter SkPathBuilder::iter() const {
   *     return SkPathIter(fPts, fVerbs, fConicWeights);
   * }
   * ```
   */
  public fun dumpToString(format: DumpFormat = TODO()): String {
    TODO("Implement dumpToString")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString SkPathBuilder::dumpToString(DumpFormat format) const {
   *     SkScalarAsStringType asType = format == DumpFormat::kHex ? kHex_SkScalarAsStringType
   *                                                              : kDec_SkScalarAsStringType;
   *
   *     SkString builder;
   *     builder.printf("SkPathBuilder(SkPathFillType::k%s)\n",
   *                    gFillTypeStrs[(int) this->fillType()]);
   *
   *     dump_iter(this->iter(), &builder, "", asType, false, [](){});
   *
   *     return builder;
   * }
   * ```
   */
  public fun dump(format: DumpFormat) {
    TODO("Implement dump")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPathBuilder::dump(DumpFormat format) const {
   *     SkDebugf("%s", dumpToString().c_str());
   * }
   * ```
   */
  public fun dump() {
    TODO("Implement dump")
  }

  /**
   * C++ original:
   * ```cpp
   * void dump() const { this->dump(DumpFormat::kDecimal); }
   * ```
   */
  public fun contains(p: SkPoint): Boolean {
    TODO("Implement contains")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkPathBuilder::contains(SkPoint p) const {
   *     const auto raw = SkPathPriv::Raw(*this, SkResolveConvexity::kNo);
   *     return raw.has_value() && SkPathPriv::Contains(*raw, p);
   * }
   * ```
   */
  private fun ensureMove() {
    TODO("Implement ensureMove")
  }

  /**
   * C++ original:
   * ```cpp
   * void ensureMove() {
   *         fType = SkPathIsAType::kGeneral;
   *         if (fVerbs.empty()) {
   *             this->moveTo({0, 0});
   *         } else if (fVerbs.back() == SkPathVerb::kClose) {
   *             this->moveTo(fPts[fLastMoveIndex]);
   *         }
   *     }
   * ```
   */
  private fun isZeroLengthSincePoint(startPtIndex: Int): Boolean {
    TODO("Implement isZeroLengthSincePoint")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkPathBuilder::isZeroLengthSincePoint(int startPtIndex) const {
   *     int count = fPts.size() - startPtIndex;
   *     if (count < 2) {
   *         return true;
   *     }
   *     const SkPoint* pts = fPts.begin() + startPtIndex;
   *     const SkPoint& first = *pts;
   *     for (int index = 1; index < count; ++index) {
   *         if (first != pts[index]) {
   *             return false;
   *         }
   *     }
   *     return true;
   * }
   * ```
   */
  private fun privateReverseAddPath(src: SkPath): SkPathBuilder {
    TODO("Implement privateReverseAddPath")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& SkPathBuilder::privateReverseAddPath(const SkPath& src) {
   *     auto verbSpan = src.verbs();
   *     if (verbSpan.empty()) {
   *         return *this;
   *     }
   *
   *     auto verbs = verbSpan.end();
   *     auto verbsBegin = verbSpan.begin();
   *     auto pts = src.points().end();
   *     auto conicWeights = src.conicWeights().end();
   *
   *     bool needMove = true;
   *     bool needClose = false;
   *     while (verbs > verbsBegin) {
   *         SkPathVerb v = *--verbs;
   *         int n = SkPathPriv::PtsInVerb(v);
   *
   *         if (needMove) {
   *             --pts;
   *             this->moveTo(pts->fX, pts->fY);
   *             needMove = false;
   *         }
   *         pts -= n;
   *         switch ((SkPathVerb)v) {
   *             case SkPathVerb::kMove:
   *                 if (needClose) {
   *                     this->close();
   *                     needClose = false;
   *                 }
   *                 needMove = true;
   *                 pts += 1;   // so we see the point in "if (needMove)" above
   *                 break;
   *             case SkPathVerb::kLine:
   *                 this->lineTo(pts[0]);
   *                 break;
   *             case SkPathVerb::kQuad:
   *                 this->quadTo(pts[1], pts[0]);
   *                 break;
   *             case SkPathVerb::kConic:
   *                 this->conicTo(pts[1], pts[0], *--conicWeights);
   *                 break;
   *             case SkPathVerb::kCubic:
   *                 this->cubicTo(pts[2], pts[1], pts[0]);
   *                 break;
   *             case SkPathVerb::kClose:
   *                 needClose = true;
   *                 break;
   *         }
   *     }
   *     return *this;
   * }
   * ```
   */
  private fun privateReversePathTo(path: SkPath): SkPathBuilder {
    TODO("Implement privateReversePathTo")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathBuilder& SkPathBuilder::privateReversePathTo(const SkPath& path) {
   *     auto verbSpan = path.verbs();
   *     if (verbSpan.empty()) {
   *         return *this;
   *     }
   *
   *     auto verbs = verbSpan.end();
   *     auto verbsBegin = verbSpan.begin();
   *     auto pts = path.points().end() - 1;
   *     auto conicWeights = path.conicWeights().end();
   *
   *     while (verbs > verbsBegin) {
   *         SkPathVerb v = *--verbs;
   *         pts -= SkPathPriv::PtsInVerb(v);
   *         switch (v) {
   *             case SkPathVerb::kMove:
   *                 // if the path has multiple contours, stop after reversing the last
   *                 return *this;
   *             case SkPathVerb::kLine:
   *                 this->lineTo(pts[0]);
   *                 break;
   *             case SkPathVerb::kQuad:
   *                 this->quadTo(pts[1], pts[0]);
   *                 break;
   *             case SkPathVerb::kConic:
   *                 this->conicTo(pts[1], pts[0], *--conicWeights);
   *                 break;
   *             case SkPathVerb::kCubic:
   *                 this->cubicTo(pts[2], pts[1], pts[0]);
   *                 break;
   *             case SkPathVerb::kClose:
   *                 break;
   *         }
   *     }
   *     return *this;
   * }
   * ```
   */
  private fun growForVerbsInPath(path: SkPath): Int {
    TODO("Implement growForVerbsInPath")
  }

  public enum class ArcSize {
    kSmall_ArcSize,
    kLarge_ArcSize,
  }

  public enum class DumpFormat {
    kDecimal,
    kHex,
  }
}
