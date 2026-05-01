package org.skia.math

import kotlin.Array
import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import kotlin.String
import org.skia.foundation.SkSpan

/**
 * C++ original:
 * ```cpp
 * struct SK_API SkRect {
 *     float fLeft   = 0; //!< smaller x-axis bounds
 *     float fTop    = 0; //!< smaller y-axis bounds
 *     float fRight  = 0; //!< larger x-axis bounds
 *     float fBottom = 0; //!< larger y-axis bounds
 *
 *     /** Returns constructed SkRect set to (0, 0, 0, 0).
 *         Many other rectangles are empty; if left is equal to or greater than right,
 *         or if top is equal to or greater than bottom. Setting all members to zero
 *         is a convenience, but does not designate a special empty rectangle.
 *
 *         @return  bounds (0, 0, 0, 0)
 *     */
 *     [[nodiscard]] static constexpr SkRect MakeEmpty() {
 *         return SkRect{0, 0, 0, 0};
 *     }
 *
 *     /** Returns constructed SkRect set to float values (0, 0, w, h). Does not
 *         validate input; w or h may be negative.
 *
 *         Passing integer values may generate a compiler warning since SkRect cannot
 *         represent 32-bit integers exactly. Use SkIRect for an exact integer rectangle.
 *
 *         @param w  float width of constructed SkRect
 *         @param h  float height of constructed SkRect
 *         @return   bounds (0, 0, w, h)
 *     */
 *     [[nodiscard]] static constexpr SkRect MakeWH(float w, float h) {
 *         return SkRect{0, 0, w, h};
 *     }
 *
 *     /** Returns constructed SkRect set to integer values (0, 0, w, h). Does not validate
 *         input; w or h may be negative.
 *
 *         Use to avoid a compiler warning that input may lose precision when stored.
 *         Use SkIRect for an exact integer rectangle.
 *
 *         @param w  integer width of constructed SkRect
 *         @param h  integer height of constructed SkRect
 *         @return   bounds (0, 0, w, h)
 *     */
 *     [[nodiscard]] static SkRect MakeIWH(int w, int h) {
 *         return {0, 0, static_cast<float>(w), static_cast<float>(h)};
 *     }
 *
 *     /** Returns constructed SkRect set to (0, 0, size.width(), size.height()). Does not
 *         validate input; size.width() or size.height() may be negative.
 *
 *         @param size  float values for SkRect width and height
 *         @return      bounds (0, 0, size.width(), size.height())
 *     */
 *     [[nodiscard]] static constexpr SkRect MakeSize(const SkSize& size) {
 *         return SkRect{0, 0, size.fWidth, size.fHeight};
 *     }
 *
 *     /** Returns constructed SkRect set to (l, t, r, b). Does not sort input; SkRect may
 *         result in fLeft greater than fRight, or fTop greater than fBottom.
 *
 *         @param l  float stored in fLeft
 *         @param t  float stored in fTop
 *         @param r  float stored in fRight
 *         @param b  float stored in fBottom
 *         @return   bounds (l, t, r, b)
 *     */
 *     [[nodiscard]] static constexpr SkRect MakeLTRB(float l, float t, float r, float b) {
 *         return SkRect {l, t, r, b};
 *     }
 *
 *     /** Returns constructed SkRect set to (x, y, x + w, y + h).
 *         Does not validate input; w or h may be negative.
 *
 *         @param x  stored in fLeft
 *         @param y  stored in fTop
 *         @param w  added to x and stored in fRight
 *         @param h  added to y and stored in fBottom
 *         @return   bounds at (x, y) with width w and height h
 *     */
 *     [[nodiscard]] static constexpr SkRect MakeXYWH(float x, float y, float w, float h) {
 *         return SkRect {x, y, x + w, y + h};
 *     }
 *
 *     /** Returns constructed SkIRect set to (0, 0, size.width(), size.height()).
 *         Does not validate input; size.width() or size.height() may be negative.
 *
 *         @param size  integer values for SkRect width and height
 *         @return      bounds (0, 0, size.width(), size.height())
 *     */
 *     static SkRect Make(const SkISize& size) {
 *         return MakeIWH(size.width(), size.height());
 *     }
 *
 *     /** Returns constructed SkIRect set to irect, promoting integers to float.
 *         Does not validate input; fLeft may be greater than fRight, fTop may be greater
 *         than fBottom.
 *
 *         @param irect  integer unsorted bounds
 *         @return       irect members converted to float
 *     */
 *     [[nodiscard]] static SkRect Make(const SkIRect& irect) {
 *         return {
 *             static_cast<float>(irect.fLeft), static_cast<float>(irect.fTop),
 *             static_cast<float>(irect.fRight), static_cast<float>(irect.fBottom)
 *         };
 *     }
 *
 *     /** Returns true if fLeft is equal to or greater than fRight, or if fTop is equal
 *         to or greater than fBottom. Call sort() to reverse rectangles with negative
 *         width() or height().
 *
 *         @return  true if width() or height() are zero or negative
 *     */
 *     bool isEmpty() const {
 *         // We write it as the NOT of a non-empty rect, so we will return true if any values
 *         // are NaN.
 *         return !(fLeft < fRight && fTop < fBottom);
 *     }
 *
 *     /** Returns true if fLeft is equal to or less than fRight, or if fTop is equal
 *         to or less than fBottom. Call sort() to reverse rectangles with negative
 *         width() or height().
 *
 *         @return  true if width() or height() are zero or positive
 *     */
 *     bool isSorted() const { return fLeft <= fRight && fTop <= fBottom; }
 *
 *     /** Returns true if all values in the rectangle are finite.
 *
 *         @return  true if no member is infinite or NaN
 *     */
 *     bool isFinite() const {
 *         return SkIsFinite(fLeft, fTop, fRight, fBottom);
 *     }
 *
 *     /** Returns left edge of SkRect, if sorted. Call isSorted() to see if SkRect is valid.
 *         Call sort() to reverse fLeft and fRight if needed.
 *
 *         @return  fLeft
 *     */
 *     constexpr float x() const { return fLeft; }
 *
 *     /** Returns top edge of SkRect, if sorted. Call isEmpty() to see if SkRect may be invalid,
 *         and sort() to reverse fTop and fBottom if needed.
 *
 *         @return  fTop
 *     */
 *     constexpr float y() const { return fTop; }
 *
 *     /** Returns left edge of SkRect, if sorted. Call isSorted() to see if SkRect is valid.
 *         Call sort() to reverse fLeft and fRight if needed.
 *
 *         @return  fLeft
 *     */
 *     constexpr float left() const { return fLeft; }
 *
 *     /** Returns top edge of SkRect, if sorted. Call isEmpty() to see if SkRect may be invalid,
 *         and sort() to reverse fTop and fBottom if needed.
 *
 *         @return  fTop
 *     */
 *     constexpr float top() const { return fTop; }
 *
 *     /** Returns right edge of SkRect, if sorted. Call isSorted() to see if SkRect is valid.
 *         Call sort() to reverse fLeft and fRight if needed.
 *
 *         @return  fRight
 *     */
 *     constexpr float right() const { return fRight; }
 *
 *     /** Returns bottom edge of SkRect, if sorted. Call isEmpty() to see if SkRect may be invalid,
 *         and sort() to reverse fTop and fBottom if needed.
 *
 *         @return  fBottom
 *     */
 *     constexpr float bottom() const { return fBottom; }
 *
 *     /** Returns span on the x-axis. This does not check if SkRect is sorted, or if
 *         result fits in 32-bit float; result may be negative or infinity.
 *
 *         @return  fRight minus fLeft
 *     */
 *     constexpr float width() const { return fRight - fLeft; }
 *
 *     /** Returns span on the y-axis. This does not check if SkRect is sorted, or if
 *         result fits in 32-bit float; result may be negative or infinity.
 *
 *         @return  fBottom minus fTop
 *     */
 *     constexpr float height() const { return fBottom - fTop; }
 *
 *     /** Returns average of left edge and right edge. Result does not change if SkRect
 *         is sorted. Result may overflow to infinity if SkRect is far from the origin.
 *
 *         @return  midpoint on x-axis
 *     */
 *     constexpr float centerX() const {
 *         return sk_float_midpoint(fLeft, fRight);
 *     }
 *
 *     /** Returns average of top edge and bottom edge. Result does not change if SkRect
 *         is sorted.
 *
 *         @return  midpoint on y-axis
 *     */
 *     constexpr float centerY() const {
 *         return sk_float_midpoint(fTop, fBottom);
 *     }
 *
 *     /** Returns the point this->centerX(), this->centerY().
 *         @return  rectangle center
 *      */
 *     constexpr SkPoint center() const { return {this->centerX(), this->centerY()}; }
 *
 *     /** Returns true if all members in a: fLeft, fTop, fRight, and fBottom; are
 *         equal to the corresponding members in b.
 *
 *         a and b are not equal if either contain NaN. a and b are equal if members
 *         contain zeroes with different signs.
 *
 *         @param a  SkRect to compare
 *         @param b  SkRect to compare
 *         @return   true if members are equal
 *     */
 *     friend bool operator==(const SkRect& a, const SkRect& b) {
 *         return a.fLeft == b.fLeft &&
 *                a.fTop == b.fTop &&
 *                a.fRight == b.fRight &&
 *                a.fBottom == b.fBottom;
 *     }
 *
 *     /** Returns true if any in a: fLeft, fTop, fRight, and fBottom; does not
 *         equal the corresponding members in b.
 *
 *         a and b are not equal if either contain NaN. a and b are equal if members
 *         contain zeroes with different signs.
 *
 *         @param a  SkRect to compare
 *         @param b  SkRect to compare
 *         @return   true if members are not equal
 *     */
 *     friend bool operator!=(const SkRect& a, const SkRect& b) {
 *         return !(a == b);
 *     }
 *
 *     SkPoint TL() const { return {fLeft,  fTop}; }
 *     SkPoint TR() const { return {fRight, fTop}; }
 *     SkPoint BL() const { return {fLeft,  fBottom}; }
 *     SkPoint BR() const { return {fRight, fBottom}; }
 *
 *     /** Returns four points in quad that enclose SkRect,
 *      *  respect the specified path-direction.
 *      */
 *     std::array<SkPoint, 4> toQuad(SkPathDirection dir = SkPathDirection::kCW) const {
 *         std::array<SkPoint, 4> storage;
 *         this->copyToQuad(storage, dir);
 *         return storage;
 *     }
 *
 *     // Same as toQuad(), but copies the 4 points into the specified storage
 *     // which must be at least a size of 4.
 *     void copyToQuad(SkSpan<SkPoint> pts, SkPathDirection dir = SkPathDirection::kCW) const {
 *         SkASSERT(pts.size() >= 4);
 *         pts[0] = this->TL();
 *         pts[2] = this->BR();
 *         if (dir == SkPathDirection::kCW) {
 *             pts[1] = this->TR();
 *             pts[3] = this->BL();
 *         } else {
 *             pts[1] = this->BL();
 *             pts[3] = this->TR();
 *         }
 *     }
 *
 *     // DEPRECATED: use std::array or copyToQuad versions
 *     void toQuad(SkPoint quad[4]) const {
 *         this->copyToQuad({quad, 4});
 *     }
 *
 *     /** Sets SkRect to (0, 0, 0, 0).
 *
 *         Many other rectangles are empty; if left is equal to or greater than right,
 *         or if top is equal to or greater than bottom. Setting all members to zero
 *         is a convenience, but does not designate a special empty rectangle.
 *     */
 *     void setEmpty() { *this = MakeEmpty(); }
 *
 *     /** Sets SkRect to src, promoting src members from integer to float.
 *         Very large values in src may lose precision.
 *
 *         @param src  integer SkRect
 *     */
 *     void set(const SkIRect& src) {
 *         fLeft   = src.fLeft;
 *         fTop    = src.fTop;
 *         fRight  = src.fRight;
 *         fBottom = src.fBottom;
 *     }
 *
 *     /** Sets SkRect to (left, top, right, bottom).
 *         left and right are not sorted; left is not necessarily less than right.
 *         top and bottom are not sorted; top is not necessarily less than bottom.
 *
 *         @param left    stored in fLeft
 *         @param top     stored in fTop
 *         @param right   stored in fRight
 *         @param bottom  stored in fBottom
 *     */
 *     void setLTRB(float left, float top, float right, float bottom) {
 *         fLeft   = left;
 *         fTop    = top;
 *         fRight  = right;
 *         fBottom = bottom;
 *     }
 *
 *     /**
 *      * Compute the bounds of the span of points.
 *      * If the span is empty, returns the empty-rect {0, 0, 0, 0.
 *      * If the span contains non-finite values (inf or nan), returns {}
 *      */
 *     static std::optional<SkRect> Bounds(SkSpan<const SkPoint> pts);
 *
 *     static SkRect BoundsOrEmpty(SkSpan<const SkPoint> pts) {
 *         if (auto bounds = Bounds(pts)) {
 *             return bounds.value();
 *         } else {
 *             return MakeEmpty();
 *         }
 *     }
 *
 *     /** Sets to bounds of SkPoint array with count entries. If count is zero or smaller,
 *         or if SkPoint array contains an infinity or NaN, sets to (0, 0, 0, 0).
 *
 *         Result is either empty or sorted: fLeft is less than or equal to fRight, and
 *         fTop is less than or equal to fBottom.
 *
 *         @param pts    SkPoint span
 *     */
 *     void setBounds(SkSpan<const SkPoint> pts) {
 *         (void)this->setBoundsCheck(pts);
 *     }
 *
 *     /** Sets to bounds of the span of points, and return true (if all point values were finite).
 *      *
 *      * If the span is empty, set the rect to empty() and return true.
 *      * If any point contains an infinity or NaN, set the rect to empty and return false.
 *      *
 *      * @param pts    SkPoint span
 *      * example: https://fiddle.skia.org/c/@Rect_setBoundsCheck
 *      */
 *     bool setBoundsCheck(SkSpan<const SkPoint> pts);
 *
 *     /** Sets to bounds of the span of points.
 *      *
 *      * If the span is empty, set the rect to empty().
 *      * If any point contains an infinity or NaN, set the rect to NaN.
 *      *
 *      * @param pts    SkPoint span
 *      * example: https://fiddle.skia.org/c/@Rect_setBoundsNoCheck
 *      */
 *     void setBoundsNoCheck(SkSpan<const SkPoint> pts);
 *
 * #ifdef SK_SUPPORT_UNSPANNED_APIS
 *     void setBounds(const SkPoint pts[], int count) {
 *         this->setBounds({pts, count});
 *     }
 *     void setBoundsNoCheck(const SkPoint pts[], int count) {
 *         this->setBoundsNoCheck({pts, count});
 *     }
 *     bool setBoundsCheck(const SkPoint pts[], int count) {
 *         return this->setBoundsCheck({pts, count});
 *     }
 * #endif
 *
 *     /** Sets bounds to the smallest SkRect enclosing SkPoint p0 and p1. The result is
 *         sorted and may be empty. Does not check to see if values are finite.
 *
 *         @param p0  corner to include
 *         @param p1  corner to include
 *     */
 *     void set(const SkPoint& p0, const SkPoint& p1) {
 *         fLeft =   std::min(p0.fX, p1.fX);
 *         fRight =  std::max(p0.fX, p1.fX);
 *         fTop =    std::min(p0.fY, p1.fY);
 *         fBottom = std::max(p0.fY, p1.fY);
 *     }
 *
 *     /** Sets SkRect to (x, y, x + width, y + height).
 *         Does not validate input; width or height may be negative.
 *
 *         @param x       stored in fLeft
 *         @param y       stored in fTop
 *         @param width   added to x and stored in fRight
 *         @param height  added to y and stored in fBottom
 *     */
 *     void setXYWH(float x, float y, float width, float height) {
 *         fLeft = x;
 *         fTop = y;
 *         fRight = x + width;
 *         fBottom = y + height;
 *     }
 *
 *     /** Sets SkRect to (0, 0, width, height). Does not validate input;
 *         width or height may be negative.
 *
 *         @param width   stored in fRight
 *         @param height  stored in fBottom
 *     */
 *     void setWH(float width, float height) {
 *         fLeft = 0;
 *         fTop = 0;
 *         fRight = width;
 *         fBottom = height;
 *     }
 *     void setIWH(int32_t width, int32_t height) {
 *         this->setWH(width, height);
 *     }
 *
 *     /** Returns SkRect offset by (dx, dy).
 *
 *         If dx is negative, SkRect returned is moved to the left.
 *         If dx is positive, SkRect returned is moved to the right.
 *         If dy is negative, SkRect returned is moved upward.
 *         If dy is positive, SkRect returned is moved downward.
 *
 *         @param dx  added to fLeft and fRight
 *         @param dy  added to fTop and fBottom
 *         @return    SkRect offset on axes, with original width and height
 *     */
 *     constexpr SkRect makeOffset(float dx, float dy) const {
 *         return MakeLTRB(fLeft + dx, fTop + dy, fRight + dx, fBottom + dy);
 *     }
 *
 *     /** Returns SkRect offset by v.
 *
 *         @param v  added to rect
 *         @return    SkRect offset on axes, with original width and height
 *     */
 *     constexpr SkRect makeOffset(SkVector v) const { return this->makeOffset(v.x(), v.y()); }
 *
 *     /** Returns SkRect, inset by (dx, dy).
 *
 *         If dx is negative, SkRect returned is wider.
 *         If dx is positive, SkRect returned is narrower.
 *         If dy is negative, SkRect returned is taller.
 *         If dy is positive, SkRect returned is shorter.
 *
 *         @param dx  added to fLeft and subtracted from fRight
 *         @param dy  added to fTop and subtracted from fBottom
 *         @return    SkRect inset symmetrically left and right, top and bottom
 *     */
 *     SkRect makeInset(float dx, float dy) const {
 *         return MakeLTRB(fLeft + dx, fTop + dy, fRight - dx, fBottom - dy);
 *     }
 *
 *     /** Returns SkRect, outset by (dx, dy).
 *
 *         If dx is negative, SkRect returned is narrower.
 *         If dx is positive, SkRect returned is wider.
 *         If dy is negative, SkRect returned is shorter.
 *         If dy is positive, SkRect returned is taller.
 *
 *         @param dx  subtracted to fLeft and added from fRight
 *         @param dy  subtracted to fTop and added from fBottom
 *         @return    SkRect outset symmetrically left and right, top and bottom
 *     */
 *     SkRect makeOutset(float dx, float dy) const {
 *         return MakeLTRB(fLeft - dx, fTop - dy, fRight + dx, fBottom + dy);
 *     }
 *
 *     /** Offsets SkRect by adding dx to fLeft, fRight; and by adding dy to fTop, fBottom.
 *
 *         If dx is negative, moves SkRect to the left.
 *         If dx is positive, moves SkRect to the right.
 *         If dy is negative, moves SkRect upward.
 *         If dy is positive, moves SkRect downward.
 *
 *         @param dx  offset added to fLeft and fRight
 *         @param dy  offset added to fTop and fBottom
 *     */
 *     void offset(float dx, float dy) {
 *         fLeft   += dx;
 *         fTop    += dy;
 *         fRight  += dx;
 *         fBottom += dy;
 *     }
 *
 *     /** Offsets SkRect by adding delta.fX to fLeft, fRight; and by adding delta.fY to
 *         fTop, fBottom.
 *
 *         If delta.fX is negative, moves SkRect to the left.
 *         If delta.fX is positive, moves SkRect to the right.
 *         If delta.fY is negative, moves SkRect upward.
 *         If delta.fY is positive, moves SkRect downward.
 *
 *         @param delta  added to SkRect
 *     */
 *     void offset(const SkPoint& delta) {
 *         this->offset(delta.fX, delta.fY);
 *     }
 *
 *     /** Offsets SkRect so that fLeft equals newX, and fTop equals newY. width and height
 *         are unchanged.
 *
 *         @param newX  stored in fLeft, preserving width()
 *         @param newY  stored in fTop, preserving height()
 *     */
 *     void offsetTo(float newX, float newY) {
 *         fRight += newX - fLeft;
 *         fBottom += newY - fTop;
 *         fLeft = newX;
 *         fTop = newY;
 *     }
 *
 *     /** Insets SkRect by (dx, dy).
 *
 *         If dx is positive, makes SkRect narrower.
 *         If dx is negative, makes SkRect wider.
 *         If dy is positive, makes SkRect shorter.
 *         If dy is negative, makes SkRect taller.
 *
 *         @param dx  added to fLeft and subtracted from fRight
 *         @param dy  added to fTop and subtracted from fBottom
 *     */
 *     void inset(float dx, float dy)  {
 *         fLeft   += dx;
 *         fTop    += dy;
 *         fRight  -= dx;
 *         fBottom -= dy;
 *     }
 *
 *     /** Outsets SkRect by (dx, dy).
 *
 *         If dx is positive, makes SkRect wider.
 *         If dx is negative, makes SkRect narrower.
 *         If dy is positive, makes SkRect taller.
 *         If dy is negative, makes SkRect shorter.
 *
 *         @param dx  subtracted to fLeft and added from fRight
 *         @param dy  subtracted to fTop and added from fBottom
 *     */
 *     void outset(float dx, float dy)  { this->inset(-dx, -dy); }
 *
 *     /** Returns true if SkRect intersects r, and sets SkRect to intersection.
 *         Returns false if SkRect does not intersect r, and leaves SkRect unchanged.
 *
 *         Returns false if either r or SkRect is empty, leaving SkRect unchanged.
 *
 *         @param r  limit of result
 *         @return   true if r and SkRect have area in common
 *
 *         example: https://fiddle.skia.org/c/@Rect_intersect
 *     */
 *     bool intersect(const SkRect& r);
 *
 *     /** Returns true if a intersects b, and sets SkRect to intersection.
 *         Returns false if a does not intersect b, and leaves SkRect unchanged.
 *
 *         Returns false if either a or b is empty, leaving SkRect unchanged.
 *
 *         @param a  SkRect to intersect
 *         @param b  SkRect to intersect
 *         @return   true if a and b have area in common
 *     */
 *     [[nodiscard]] bool intersect(const SkRect& a, const SkRect& b);
 *
 *
 * private:
 *     static bool Intersects(float al, float at, float ar, float ab,
 *                            float bl, float bt, float br, float bb) {
 *         float L = std::max(al, bl);
 *         float R = std::min(ar, br);
 *         float T = std::max(at, bt);
 *         float B = std::min(ab, bb);
 *         return L < R && T < B;
 *     }
 *
 * public:
 *
 *     /** Returns true if SkRect intersects r.
 *      Returns false if either r or SkRect is empty, or do not intersect.
 *
 *      @param r  SkRect to intersect
 *      @return   true if r and SkRect have area in common
 *      */
 *     bool intersects(const SkRect& r) const {
 *         return Intersects(fLeft, fTop, fRight, fBottom,
 *                           r.fLeft, r.fTop, r.fRight, r.fBottom);
 *     }
 *
 *     /** Returns true if a intersects b.
 *         Returns false if either a or b is empty, or do not intersect.
 *
 *         @param a  SkRect to intersect
 *         @param b  SkRect to intersect
 *         @return   true if a and b have area in common
 *     */
 *     static bool Intersects(const SkRect& a, const SkRect& b) {
 *         return Intersects(a.fLeft, a.fTop, a.fRight, a.fBottom,
 *                           b.fLeft, b.fTop, b.fRight, b.fBottom);
 *     }
 *
 *     /** Sets SkRect to the union of itself and r.
 *
 *         Has no effect if r is empty. Otherwise, if SkRect is empty, sets
 *         SkRect to r.
 *
 *         @param r  expansion SkRect
 *
 *         example: https://fiddle.skia.org/c/@Rect_join_2
 *     */
 *     void join(const SkRect& r);
 *
 *     /** Sets SkRect to the union of itself and r.
 *
 *         Asserts if r is empty and SK_DEBUG is defined.
 *         If SkRect is empty, sets SkRect to r.
 *
 *         May produce incorrect results if r is empty.
 *
 *         @param r  expansion SkRect
 *     */
 *     void joinNonEmptyArg(const SkRect& r) {
 *         SkASSERT(!r.isEmpty());
 *         // if we are empty, just assign
 *         if (fLeft >= fRight || fTop >= fBottom) {
 *             *this = r;
 *         } else {
 *             this->joinPossiblyEmptyRect(r);
 *         }
 *     }
 *
 *     /** Sets SkRect to the union of itself and the construction.
 *
 *         May produce incorrect results if SkRect or r is empty.
 *
 *         @param r  expansion SkRect
 *     */
 *     void joinPossiblyEmptyRect(const SkRect& r) {
 *         fLeft   = std::min(fLeft, r.left());
 *         fTop    = std::min(fTop, r.top());
 *         fRight  = std::max(fRight, r.right());
 *         fBottom = std::max(fBottom, r.bottom());
 *     }
 *
 *     /** Returns true if: fLeft <= x < fRight && fTop <= y < fBottom.
 *         Returns false if SkRect is empty.
 *
 *         @param x  test SkPoint x-coordinate
 *         @param y  test SkPoint y-coordinate
 *         @return   true if (x, y) is inside SkRect
 *     */
 *     bool contains(float x, float y) const {
 *         return x >= fLeft && x < fRight && y >= fTop && y < fBottom;
 *     }
 *
 *     /** Returns true if SkRect contains r.
 *         Returns false if SkRect is empty or r is empty.
 *
 *         SkRect contains r when SkRect area completely includes r area.
 *
 *         @param r  SkRect contained
 *         @return   true if all sides of SkRect are outside r
 *     */
 *     bool contains(const SkRect& r) const {
 *         // todo: can we eliminate the this->isEmpty check?
 *         return  !r.isEmpty() && !this->isEmpty() &&
 *                 fLeft <= r.fLeft && fTop <= r.fTop &&
 *                 fRight >= r.fRight && fBottom >= r.fBottom;
 *     }
 *
 *     /** Returns true if SkRect contains r.
 *         Returns false if SkRect is empty or r is empty.
 *
 *         SkRect contains r when SkRect area completely includes r area.
 *
 *         @param r  SkIRect contained
 *         @return   true if all sides of SkRect are outside r
 *     */
 *     bool contains(const SkIRect& r) const {
 *         // todo: can we eliminate the this->isEmpty check?
 *         return  !r.isEmpty() && !this->isEmpty() &&
 *                 fLeft <= r.fLeft && fTop <= r.fTop &&
 *                 fRight >= r.fRight && fBottom >= r.fBottom;
 *     }
 *
 *     /** Sets SkIRect by adding 0.5 and discarding the fractional portion of SkRect
 *         members, using (sk_float_round2int(fLeft), sk_float_round2int(fTop),
 *                         sk_float_round2int(fRight), sk_float_round2int(fBottom)).
 *
 *         @param dst  storage for SkIRect
 *     */
 *     void round(SkIRect* dst) const {
 *         SkASSERT(dst);
 *         dst->setLTRB(sk_float_round2int(fLeft),  sk_float_round2int(fTop),
 *                      sk_float_round2int(fRight), sk_float_round2int(fBottom));
 *     }
 *
 *     /** Sets SkIRect by discarding the fractional portion of fLeft and fTop; and rounding
 *         up fRight and fBottom, using
 *         (sk_float_floor2int(fLeft), sk_float_floor2int(fTop),
 *          sk_float_ceil2int(fRight), sk_float_ceil2int(fBottom)).
 *
 *         @param dst  storage for SkIRect
 *     */
 *     void roundOut(SkIRect* dst) const {
 *         SkASSERT(dst);
 *         dst->setLTRB(sk_float_floor2int(fLeft), sk_float_floor2int(fTop),
 *                      sk_float_ceil2int(fRight), sk_float_ceil2int(fBottom));
 *     }
 *
 *     /** Sets SkRect by discarding the fractional portion of fLeft and fTop; and rounding
 *         up fRight and fBottom, using
 *         (std::floor(fLeft), std::floor(fTop),
 *          std::ceil(fRight), std::ceil(fBottom)).
 *
 *         @param dst  storage for SkRect
 *     */
 *     void roundOut(SkRect* dst) const {
 *         dst->setLTRB(std::floor(fLeft), std::floor(fTop),
 *                      std::ceil(fRight), std::ceil(fBottom));
 *     }
 *
 *     /** Sets SkRect by rounding up fLeft and fTop; and discarding the fractional portion
 *         of fRight and fBottom, using
 *         (sk_float_ceil2int(fLeft), sk_float_ceil2int(fTop),
 *          sk_float_floor2int(fRight), sk_float_floor2int(fBottom)).
 *
 *         @param dst  storage for SkIRect
 *     */
 *     void roundIn(SkIRect* dst) const {
 *         SkASSERT(dst);
 *         dst->setLTRB(sk_float_ceil2int(fLeft),   sk_float_ceil2int(fTop),
 *                      sk_float_floor2int(fRight), sk_float_floor2int(fBottom));
 *     }
 *
 *     /** Returns SkIRect by adding 0.5 and discarding the fractional portion of SkRect
 *         members, using (sk_float_round2int(fLeft), sk_float_round2int(fTop),
 *                         sk_float_round2int(fRight), sk_float_round2int(fBottom)).
 *
 *         @return  rounded SkIRect
 *     */
 *     SkIRect round() const {
 *         SkIRect ir;
 *         this->round(&ir);
 *         return ir;
 *     }
 *
 *     /** Sets SkIRect by discarding the fractional portion of fLeft and fTop; and rounding
 *         up fRight and fBottom, using
 *         (sk_float_floor2int(fLeft), sk_float_floor2int(fTop),
 *          sk_float_ceil2int(fRight), sk_float_ceil2int(fBottom)).
 *
 *         @return  rounded SkIRect
 *     */
 *     SkIRect roundOut() const {
 *         SkIRect ir;
 *         this->roundOut(&ir);
 *         return ir;
 *     }
 *     /** Sets SkIRect by rounding up fLeft and fTop; and discarding the fractional portion
 *         of fRight and fBottom, using
 *         (sk_float_ceil2int(fLeft), sk_float_ceil2int(fTop),
 *          sk_float_floor2int(fRight), sk_float_floor2int(fBottom)).
 *
 *         @return  rounded SkIRect
 *     */
 *     SkIRect roundIn() const {
 *         SkIRect ir;
 *         this->roundIn(&ir);
 *         return ir;
 *     }
 *
 *     /** Swaps fLeft and fRight if fLeft is greater than fRight; and swaps
 *         fTop and fBottom if fTop is greater than fBottom. Result may be empty;
 *         and width() and height() will be zero or positive.
 *     */
 *     void sort() {
 *         using std::swap;
 *         if (fLeft > fRight) {
 *             swap(fLeft, fRight);
 *         }
 *
 *         if (fTop > fBottom) {
 *             swap(fTop, fBottom);
 *         }
 *     }
 *
 *     /** Returns SkRect with fLeft and fRight swapped if fLeft is greater than fRight; and
 *         with fTop and fBottom swapped if fTop is greater than fBottom. Result may be empty;
 *         and width() and height() will be zero or positive.
 *
 *         @return  sorted SkRect
 *     */
 *     SkRect makeSorted() const {
 *         return MakeLTRB(std::min(fLeft, fRight), std::min(fTop, fBottom),
 *                         std::max(fLeft, fRight), std::max(fTop, fBottom));
 *     }
 *
 *     /** Returns pointer to first float in SkRect, to treat it as an array with four
 *         entries.
 *
 *         @return  pointer to fLeft
 *     */
 *     const float* asScalars() const { return &fLeft; }
 *
 *     /** Writes text representation of SkRect to standard output. Set asHex to true to
 *         generate exact binary representations of floating point numbers.
 *
 *         @param asHex  true if SkScalar values are written as hexadecimal
 *
 *         example: https://fiddle.skia.org/c/@Rect_dump
 *     */
 *     void dump(bool asHex) const;
 *     SkString dumpToString(bool asHex) const;
 *
 *     /** Writes text representation of SkRect to standard output. The representation may be
 *         directly compiled as C++ code. Floating point values are written
 *         with limited precision; it may not be possible to reconstruct original SkRect
 *         from output.
 *     */
 *     void dump() const { this->dump(false); }
 *
 *     /** Writes text representation of SkRect to standard output. The representation may be
 *         directly compiled as C++ code. Floating point values are written
 *         in hexadecimal to preserve their exact bit pattern. The output reconstructs the
 *         original SkRect.
 *
 *         Use instead of dump() when submitting
 *     */
 *     void dumpHex() const { this->dump(true); }
 * }
 * ```
 */
public open class SkRect public constructor(
  /**
   * C++ original:
   * ```cpp
   * float fLeft   = 0
   * ```
   */
  public var fLeft: Float,
  /**
   * C++ original:
   * ```cpp
   * float fTop    = 0
   * ```
   */
  public var fTop: Float,
  /**
   * C++ original:
   * ```cpp
   * float fRight  = 0
   * ```
   */
  public var fRight: Float,
  /**
   * C++ original:
   * ```cpp
   * float fBottom = 0
   * ```
   */
  public var fBottom: Float,
) {
  /**
   * C++ original:
   * ```cpp
   * bool isEmpty() const {
   *         // We write it as the NOT of a non-empty rect, so we will return true if any values
   *         // are NaN.
   *         return !(fLeft < fRight && fTop < fBottom);
   *     }
   * ```
   */
  public fun isEmpty(): Boolean {
    return !(left() < right() && top() < bottom())
  }

  /**
   * C++ original:
   * ```cpp
   * bool isSorted() const { return fLeft <= fRight && fTop <= fBottom; }
   * ```
   */
  public fun isSorted(): Boolean {
    return fLeft <= fRight && fTop <= fBottom
  }

  /**
   * C++ original:
   * ```cpp
   * bool isFinite() const {
   *         return SkIsFinite(fLeft, fTop, fRight, fBottom);
   *     }
   * ```
   */
  public fun isFinite(): Boolean {
    TODO("Implement isFinite")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr float x() const { return fLeft; }
   * ```
   */
  public fun x(): Float {
    return fLeft
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr float y() const { return fTop; }
   * ```
   */
  public fun y(): Float {
    return y()
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr float left() const { return fLeft; }
   * ```
   */
  public fun left(): Float {
    return this.left()
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr float top() const { return fTop; }
   * ```
   */
  public fun top(): Float {
    return top()
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr float right() const { return fRight; }
   * ```
   */
  public fun right(): Float {
    return right()
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr float bottom() const { return fBottom; }
   * ```
   */
  public fun bottom(): Float {
    return fBottom
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr float width() const { return fRight - fLeft; }
   * ```
   */
  public fun width(): Float {
    return fRight - fLeft
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr float height() const { return fBottom - fTop; }
   * ```
   */
  public fun height(): Float {
    return bottom() - top()
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr float centerX() const {
   *         return sk_float_midpoint(fLeft, fRight);
   *     }
   * ```
   */
  public fun centerX(): Float {
    TODO("Implement centerX")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr float centerY() const {
   *         return sk_float_midpoint(fTop, fBottom);
   *     }
   * ```
   */
  public fun centerY(): Float {
    TODO("Implement centerY")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr SkPoint center() const { return {this->centerX(), this->centerY()}; }
   * ```
   */
  public fun center(): Int {
    TODO("Implement center")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPoint TL() const { return {fLeft,  fTop}; }
   * ```
   */
  public fun tl(): Int {
    TODO("Implement tl")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPoint TR() const { return {fRight, fTop}; }
   * ```
   */
  public fun tr(): Int {
    TODO("Implement tr")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPoint BL() const { return {fLeft,  fBottom}; }
   * ```
   */
  public fun bl(): Int {
    TODO("Implement bl")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPoint BR() const { return {fRight, fBottom}; }
   * ```
   */
  public fun br(): Int {
    TODO("Implement br")
  }

  /**
   * C++ original:
   * ```cpp
   * std::array<SkPoint, 4> toQuad(SkPathDirection dir = SkPathDirection::kCW) const {
   *         std::array<SkPoint, 4> storage;
   *         this->copyToQuad(storage, dir);
   *         return storage;
   *     }
   * ```
   */
  public fun toQuad(dir: SkPathDirection = TODO()): Int {
    TODO("Implement toQuad")
  }

  /**
   * C++ original:
   * ```cpp
   * void copyToQuad(SkSpan<SkPoint> pts, SkPathDirection dir = SkPathDirection::kCW) const {
   *         SkASSERT(pts.size() >= 4);
   *         pts[0] = this->TL();
   *         pts[2] = this->BR();
   *         if (dir == SkPathDirection::kCW) {
   *             pts[1] = this->TR();
   *             pts[3] = this->BL();
   *         } else {
   *             pts[1] = this->BL();
   *             pts[3] = this->TR();
   *         }
   *     }
   * ```
   */
  public fun copyToQuad(pts: SkSpan<SkPoint>, dir: SkPathDirection = TODO()) {
    TODO("Implement copyToQuad")
  }

  /**
   * C++ original:
   * ```cpp
   * void toQuad(SkPoint quad[4]) const {
   *         this->copyToQuad({quad, 4});
   *     }
   * ```
   */
  public fun toQuad(quad: Array<SkPoint>) {
    TODO("Implement toQuad")
  }

  /**
   * C++ original:
   * ```cpp
   * void setEmpty() { *this = MakeEmpty(); }
   * ```
   */
  public fun setEmpty() {
    TODO("Implement setEmpty")
  }

  /**
   * C++ original:
   * ```cpp
   * void set(const SkIRect& src) {
   *         fLeft   = src.fLeft;
   *         fTop    = src.fTop;
   *         fRight  = src.fRight;
   *         fBottom = src.fBottom;
   *     }
   * ```
   */
  public fun `set`(src: SkIRect) {
    TODO("Implement set")
  }

  /**
   * C++ original:
   * ```cpp
   * void setLTRB(float left, float top, float right, float bottom) {
   *         fLeft   = left;
   *         fTop    = top;
   *         fRight  = right;
   *         fBottom = bottom;
   *     }
   * ```
   */
  public fun setLTRB(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
  ) {
    TODO("Implement setLTRB")
  }

  /**
   * C++ original:
   * ```cpp
   * void setBounds(SkSpan<const SkPoint> pts) {
   *         (void)this->setBoundsCheck(pts);
   *     }
   * ```
   */
  public fun setBounds(pts: SkSpan<SkPoint>) {
    TODO("Implement setBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkRect::setBoundsCheck(SkSpan<const SkPoint> pts) {
   *     if (auto bounds = Bounds(pts)) {
   *         *this = bounds.value();
   *         return true;
   *     } else {
   *         *this = MakeEmpty();
   *         return false;
   *     }
   * }
   * ```
   */
  public fun setBoundsCheck(pts: SkSpan<SkPoint>): Boolean {
    TODO("Implement setBoundsCheck")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRect::setBoundsNoCheck(SkSpan<const SkPoint> pts) {
   *     if (auto bounds = Bounds(pts)) {
   *         *this = bounds.value();
   *     } else {
   *         this->setLTRB(SK_FloatNaN, SK_FloatNaN, SK_FloatNaN, SK_FloatNaN);
   *     }
   * }
   * ```
   */
  public fun setBoundsNoCheck(pts: SkSpan<SkPoint>) {
    TODO("Implement setBoundsNoCheck")
  }

  /**
   * C++ original:
   * ```cpp
   * void set(const SkPoint& p0, const SkPoint& p1) {
   *         fLeft =   std::min(p0.fX, p1.fX);
   *         fRight =  std::max(p0.fX, p1.fX);
   *         fTop =    std::min(p0.fY, p1.fY);
   *         fBottom = std::max(p0.fY, p1.fY);
   *     }
   * ```
   */
  public fun `set`(p0: SkPoint, p1: SkPoint) {
    TODO("Implement set")
  }

  /**
   * C++ original:
   * ```cpp
   * void setXYWH(float x, float y, float width, float height) {
   *         fLeft = x;
   *         fTop = y;
   *         fRight = x + width;
   *         fBottom = y + height;
   *     }
   * ```
   */
  public fun setXYWH(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
  ) {
    TODO("Implement setXYWH")
  }

  /**
   * C++ original:
   * ```cpp
   * void setWH(float width, float height) {
   *         fLeft = 0;
   *         fTop = 0;
   *         fRight = width;
   *         fBottom = height;
   *     }
   * ```
   */
  public fun setWH(width: Float, height: Float) {
    TODO("Implement setWH")
  }

  /**
   * C++ original:
   * ```cpp
   * void setIWH(int32_t width, int32_t height) {
   *         this->setWH(width, height);
   *     }
   * ```
   */
  public fun setIWH(width: Int, height: Int) {
    TODO("Implement setIWH")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr SkRect makeOffset(float dx, float dy) const {
   *         return MakeLTRB(fLeft + dx, fTop + dy, fRight + dx, fBottom + dy);
   *     }
   * ```
   */
  public fun makeOffset(dx: Float, dy: Float): SkRect {
    TODO("Implement makeOffset")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr SkRect makeOffset(SkVector v) const { return this->makeOffset(v.x(), v.y()); }
   * ```
   */
  public fun makeOffset(v: SkVector): SkRect {
    TODO("Implement makeOffset")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect makeInset(float dx, float dy) const {
   *         return MakeLTRB(fLeft + dx, fTop + dy, fRight - dx, fBottom - dy);
   *     }
   * ```
   */
  public fun makeInset(dx: Float, dy: Float): SkRect {
    TODO("Implement makeInset")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect makeOutset(float dx, float dy) const {
   *         return MakeLTRB(fLeft - dx, fTop - dy, fRight + dx, fBottom + dy);
   *     }
   * ```
   */
  public fun makeOutset(dx: Float, dy: Float): SkRect {
    TODO("Implement makeOutset")
  }

  /**
   * C++ original:
   * ```cpp
   * void offset(float dx, float dy) {
   *         fLeft   += dx;
   *         fTop    += dy;
   *         fRight  += dx;
   *         fBottom += dy;
   *     }
   * ```
   */
  public fun offset(dx: Float, dy: Float) {
    TODO("Implement offset")
  }

  /**
   * C++ original:
   * ```cpp
   * void offset(const SkPoint& delta) {
   *         this->offset(delta.fX, delta.fY);
   *     }
   * ```
   */
  public fun offset(delta: SkPoint) {
    TODO("Implement offset")
  }

  /**
   * C++ original:
   * ```cpp
   * void offsetTo(float newX, float newY) {
   *         fRight += newX - fLeft;
   *         fBottom += newY - fTop;
   *         fLeft = newX;
   *         fTop = newY;
   *     }
   * ```
   */
  public fun offsetTo(newX: Float, newY: Float) {
    TODO("Implement offsetTo")
  }

  /**
   * C++ original:
   * ```cpp
   * void inset(float dx, float dy)  {
   *         fLeft   += dx;
   *         fTop    += dy;
   *         fRight  -= dx;
   *         fBottom -= dy;
   *     }
   * ```
   */
  public fun inset(dx: Float, dy: Float) {
    TODO("Implement inset")
  }

  /**
   * C++ original:
   * ```cpp
   * void outset(float dx, float dy)  { this->inset(-dx, -dy); }
   * ```
   */
  public fun outset(dx: Float, dy: Float) {
    TODO("Implement outset")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkRect::intersect(const SkRect& r) {
   *     CHECK_INTERSECT(r.fLeft, r.fTop, r.fRight, r.fBottom, fLeft, fTop, fRight, fBottom);
   *     this->setLTRB(L, T, R, B);
   *     return true;
   * }
   * ```
   */
  public fun intersect(r: SkRect): Boolean {
    TODO("Implement intersect")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkRect::intersect(const SkRect& a, const SkRect& b) {
   *     CHECK_INTERSECT(a.fLeft, a.fTop, a.fRight, a.fBottom, b.fLeft, b.fTop, b.fRight, b.fBottom);
   *     this->setLTRB(L, T, R, B);
   *     return true;
   * }
   * ```
   */
  public fun intersect(a: SkRect, b: SkRect): Boolean {
    TODO("Implement intersect")
  }

  /**
   * C++ original:
   * ```cpp
   * bool intersects(const SkRect& r) const {
   *         return Intersects(fLeft, fTop, fRight, fBottom,
   *                           r.fLeft, r.fTop, r.fRight, r.fBottom);
   *     }
   * ```
   */
  public fun intersects(r: SkRect): Boolean {
    TODO("Implement intersects")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRect::join(const SkRect& r) {
   *     if (r.isEmpty()) {
   *         return;
   *     }
   *
   *     if (this->isEmpty()) {
   *         *this = r;
   *     } else {
   *         fLeft   = std::min(fLeft, r.fLeft);
   *         fTop    = std::min(fTop, r.fTop);
   *         fRight  = std::max(fRight, r.fRight);
   *         fBottom = std::max(fBottom, r.fBottom);
   *     }
   * }
   * ```
   */
  public fun join(r: SkRect) {
    TODO("Implement join")
  }

  /**
   * C++ original:
   * ```cpp
   * void joinNonEmptyArg(const SkRect& r) {
   *         SkASSERT(!r.isEmpty());
   *         // if we are empty, just assign
   *         if (fLeft >= fRight || fTop >= fBottom) {
   *             *this = r;
   *         } else {
   *             this->joinPossiblyEmptyRect(r);
   *         }
   *     }
   * ```
   */
  public fun joinNonEmptyArg(r: SkRect) {
    TODO("Implement joinNonEmptyArg")
  }

  /**
   * C++ original:
   * ```cpp
   * void joinPossiblyEmptyRect(const SkRect& r) {
   *         fLeft   = std::min(fLeft, r.left());
   *         fTop    = std::min(fTop, r.top());
   *         fRight  = std::max(fRight, r.right());
   *         fBottom = std::max(fBottom, r.bottom());
   *     }
   * ```
   */
  public fun joinPossiblyEmptyRect(r: SkRect) {
    TODO("Implement joinPossiblyEmptyRect")
  }

  /**
   * C++ original:
   * ```cpp
   * bool contains(float x, float y) const {
   *         return x >= fLeft && x < fRight && y >= fTop && y < fBottom;
   *     }
   * ```
   */
  public fun contains(x: Float, y: Float): Boolean {
    TODO("Implement contains")
  }

  /**
   * C++ original:
   * ```cpp
   * bool contains(const SkRect& r) const {
   *         // todo: can we eliminate the this->isEmpty check?
   *         return  !r.isEmpty() && !this->isEmpty() &&
   *                 fLeft <= r.fLeft && fTop <= r.fTop &&
   *                 fRight >= r.fRight && fBottom >= r.fBottom;
   *     }
   * ```
   */
  public fun contains(r: SkRect): Boolean {
    TODO("Implement contains")
  }

  /**
   * C++ original:
   * ```cpp
   * bool contains(const SkIRect& r) const {
   *         // todo: can we eliminate the this->isEmpty check?
   *         return  !r.isEmpty() && !this->isEmpty() &&
   *                 fLeft <= r.fLeft && fTop <= r.fTop &&
   *                 fRight >= r.fRight && fBottom >= r.fBottom;
   *     }
   * ```
   */
  public fun contains(r: SkIRect): Boolean {
    TODO("Implement contains")
  }

  /**
   * C++ original:
   * ```cpp
   * void round(SkIRect* dst) const {
   *         SkASSERT(dst);
   *         dst->setLTRB(sk_float_round2int(fLeft),  sk_float_round2int(fTop),
   *                      sk_float_round2int(fRight), sk_float_round2int(fBottom));
   *     }
   * ```
   */
  public fun round(dst: SkIRect?) {
    TODO("Implement round")
  }

  /**
   * C++ original:
   * ```cpp
   * void roundOut(SkIRect* dst) const {
   *         SkASSERT(dst);
   *         dst->setLTRB(sk_float_floor2int(fLeft), sk_float_floor2int(fTop),
   *                      sk_float_ceil2int(fRight), sk_float_ceil2int(fBottom));
   *     }
   * ```
   */
  public fun roundOut(dst: SkIRect?) {
    TODO("Implement roundOut")
  }

  /**
   * C++ original:
   * ```cpp
   * void roundOut(SkRect* dst) const {
   *         dst->setLTRB(std::floor(fLeft), std::floor(fTop),
   *                      std::ceil(fRight), std::ceil(fBottom));
   *     }
   * ```
   */
  public fun roundOut(dst: SkRect?) {
    TODO("Implement roundOut")
  }

  /**
   * C++ original:
   * ```cpp
   * void roundIn(SkIRect* dst) const {
   *         SkASSERT(dst);
   *         dst->setLTRB(sk_float_ceil2int(fLeft),   sk_float_ceil2int(fTop),
   *                      sk_float_floor2int(fRight), sk_float_floor2int(fBottom));
   *     }
   * ```
   */
  public fun roundIn(dst: SkIRect?) {
    TODO("Implement roundIn")
  }

  /**
   * C++ original:
   * ```cpp
   * SkIRect round() const {
   *         SkIRect ir;
   *         this->round(&ir);
   *         return ir;
   *     }
   * ```
   */
  public fun round(): SkIRect {
    TODO("Implement round")
  }

  /**
   * C++ original:
   * ```cpp
   * SkIRect roundOut() const {
   *         SkIRect ir;
   *         this->roundOut(&ir);
   *         return ir;
   *     }
   * ```
   */
  public fun roundOut(): SkIRect {
    TODO("Implement roundOut")
  }

  /**
   * C++ original:
   * ```cpp
   * SkIRect roundIn() const {
   *         SkIRect ir;
   *         this->roundIn(&ir);
   *         return ir;
   *     }
   * ```
   */
  public fun roundIn(): SkIRect {
    TODO("Implement roundIn")
  }

  /**
   * C++ original:
   * ```cpp
   * void sort() {
   *         using std::swap;
   *         if (fLeft > fRight) {
   *             swap(fLeft, fRight);
   *         }
   *
   *         if (fTop > fBottom) {
   *             swap(fTop, fBottom);
   *         }
   *     }
   * ```
   */
  public fun sort() {
    TODO("Implement sort")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect makeSorted() const {
   *         return MakeLTRB(std::min(fLeft, fRight), std::min(fTop, fBottom),
   *                         std::max(fLeft, fRight), std::max(fTop, fBottom));
   *     }
   * ```
   */
  public fun makeSorted(): SkRect {
    TODO("Implement makeSorted")
  }

  /**
   * C++ original:
   * ```cpp
   * const float* asScalars() const { return &fLeft; }
   * ```
   */
  public fun asScalars(): Float {
    TODO("Implement asScalars")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRect::dump(bool asHex) const {
   *     SkDebugf("%s\n", this->dumpToString(asHex).c_str());
   * }
   * ```
   */
  public fun dump(asHex: Boolean) {
    TODO("Implement dump")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString SkRect::dumpToString(bool asHex) const {
   *     SkScalarAsStringType asType = asHex ? kHex_SkScalarAsStringType : kDec_SkScalarAsStringType;
   *
   *     SkString line;
   *     if (asHex) {
   *         SkString tmp;
   *         line.printf( "SkRect::MakeLTRB(%s, /* %f */\n", set_scalar(&tmp, fLeft, asType), fLeft);
   *         line.appendf("                 %s, /* %f */\n", set_scalar(&tmp, fTop, asType), fTop);
   *         line.appendf("                 %s, /* %f */\n", set_scalar(&tmp, fRight, asType), fRight);
   *         line.appendf("                 %s  /* %f */);", set_scalar(&tmp, fBottom, asType), fBottom);
   *     } else {
   *         SkString strL, strT, strR, strB;
   *         SkAppendScalarDec(&strL, fLeft);
   *         SkAppendScalarDec(&strT, fTop);
   *         SkAppendScalarDec(&strR, fRight);
   *         SkAppendScalarDec(&strB, fBottom);
   *         line.printf("SkRect::MakeLTRB(%s, %s, %s, %s);",
   *                     strL.c_str(), strT.c_str(), strR.c_str(), strB.c_str());
   *     }
   *     return line;
   * }
   * ```
   */
  public fun dumpToString(asHex: Boolean): String {
    TODO("Implement dumpToString")
  }

  /**
   * C++ original:
   * ```cpp
   * void dump() const { this->dump(false); }
   * ```
   */
  public fun dump() {
    TODO("Implement dump")
  }

  /**
   * C++ original:
   * ```cpp
   * void dumpHex() const { this->dump(true); }
   * ```
   */
  public fun dumpHex() {
    TODO("Implement dumpHex")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static constexpr SkRect MakeEmpty() {
     *         return SkRect{0, 0, 0, 0};
     *     }
     * ```
     */
    public fun makeEmpty(): SkRect {
      TODO("Implement makeEmpty")
    }

    /**
     * C++ original:
     * ```cpp
     * static constexpr SkRect MakeWH(float w, float h) {
     *         return SkRect{0, 0, w, h};
     *     }
     * ```
     */
    public fun makeWH(w: Float, h: Float): SkRect {
      TODO("Implement makeWH")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkRect MakeIWH(int w, int h) {
     *         return {0, 0, static_cast<float>(w), static_cast<float>(h)};
     *     }
     * ```
     */
    public fun makeIWH(w: Int, h: Int): SkRect {
      TODO("Implement makeIWH")
    }

    /**
     * C++ original:
     * ```cpp
     * static constexpr SkRect MakeSize(const SkSize& size) {
     *         return SkRect{0, 0, size.fWidth, size.fHeight};
     *     }
     * ```
     */
    public fun makeSize(size: SkSize): SkRect {
      TODO("Implement makeSize")
    }

    /**
     * C++ original:
     * ```cpp
     * static constexpr SkRect MakeLTRB(float l, float t, float r, float b) {
     *         return SkRect {l, t, r, b};
     *     }
     * ```
     */
    public fun makeLTRB(
      l: Float,
      t: Float,
      r: Float,
      b: Float,
    ): SkRect {
      TODO("Implement makeLTRB")
    }

    /**
     * C++ original:
     * ```cpp
     * static constexpr SkRect MakeXYWH(float x, float y, float w, float h) {
     *         return SkRect {x, y, x + w, y + h};
     *     }
     * ```
     */
    public fun makeXYWH(
      x: Float,
      y: Float,
      w: Float,
      h: Float,
    ): SkRect {
      TODO("Implement makeXYWH")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkRect Make(const SkISize& size) {
     *         return MakeIWH(size.width(), size.height());
     *     }
     * ```
     */
    public fun make(size: SkISize): SkRect {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkRect Make(const SkIRect& irect) {
     *         return {
     *             static_cast<float>(irect.fLeft), static_cast<float>(irect.fTop),
     *             static_cast<float>(irect.fRight), static_cast<float>(irect.fBottom)
     *         };
     *     }
     * ```
     */
    public fun make(irect: SkIRect): SkRect {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * std::optional<SkRect> SkRect::Bounds(SkSpan<const SkPoint> points) {
     *     if (points.empty()) {
     *         return SkRect::MakeEmpty();
     *     }
     *
     *     /*
     *      *  Both of these variants compute the same numerics.
     *      *
     *      *  But, the "simple" one (no explicit skvx) runs faster (most of the time) on 64bit
     *      *  machines, and the tricky skvx version runs faster (most of the time) on 32bit machines.
     *      *
     *      *  Hence the if/else
     *      */
     *
     *     if constexpr (sizeof(void*) == 8) {
     *         float L = points[0].fX, T = points[0].fY, R = points[0].fX, B = points[0].fY;
     *         float nx = 0, ny = 0;
     *         for (auto p : points) {
     *             L = std::fminf(p.fX, L);
     *             T = std::fminf(p.fY, T);
     *             R = std::fmaxf(p.fX, R);
     *             B = std::fmaxf(p.fY, B);
     *
     *             // we do this to look for infinities or nans
     *             nx *= p.fX;
     *             ny *= p.fY;
     *         }
     *
     *         // if this is true, all our values were finite
     *         if (nx == 0 && ny == 0) {
     *             return {{L, T, R, B}};
     *         }
     *     } else {
     *         auto count = points.size();
     *         auto pts = points.data();
     *
     *         skvx::float4 min, max;
     *         if (count & 1) {
     *             min = max = skvx::float2::Load(pts).xyxy();
     *             pts   += 1;
     *             count -= 1;
     *         } else {
     *             min = max = skvx::float4::Load(pts);
     *             pts   += 2;
     *             count -= 2;
     *         }
     *
     *         skvx::float4 accum = min * 0;
     *         while (count) {
     *             skvx::float4 xy = skvx::float4::Load(pts);
     *             accum = accum * xy;
     *             min = skvx::min(min, xy);
     *             max = skvx::max(max, xy);
     *             pts   += 2;
     *             count -= 2;
     *         }
     *
     *         const bool all_finite = all(accum * 0 == 0);
     *         if (all_finite) {
     *             return MakeLTRB(std::min(min[0], min[2]), std::min(min[1], min[3]),
     *                             std::max(max[0], max[2]), std::max(max[1], max[3]));
     *         }
     *     }
     *
     *     /*
     *      *  If we got here, we were not empty, and at least one of the span values was
     *      *  either an Infinity or NaN -- so we return failure (no finite bounds)
     *      */
     *     return {};
     * }
     * ```
     */
    public fun bounds(pts: SkSpan<SkPoint>): SkRect? {
      TODO("Implement bounds")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkRect BoundsOrEmpty(SkSpan<const SkPoint> pts) {
     *         if (auto bounds = Bounds(pts)) {
     *             return bounds.value();
     *         } else {
     *             return MakeEmpty();
     *         }
     *     }
     * ```
     */
    public fun boundsOrEmpty(pts: SkSpan<SkPoint>): SkRect {
      TODO("Implement boundsOrEmpty")
    }

    /**
     * C++ original:
     * ```cpp
     * static bool Intersects(float al, float at, float ar, float ab,
     *                            float bl, float bt, float br, float bb) {
     *         float L = std::max(al, bl);
     *         float R = std::min(ar, br);
     *         float T = std::max(at, bt);
     *         float B = std::min(ab, bb);
     *         return L < R && T < B;
     *     }
     * ```
     */
    private fun intersects(
      al: Float,
      at: Float,
      ar: Float,
      ab: Float,
      bl: Float,
      bt: Float,
      br: Float,
      bb: Float,
    ): Boolean {
      TODO("Implement intersects")
    }

    /**
     * C++ original:
     * ```cpp
     * static bool Intersects(const SkRect& a, const SkRect& b) {
     *         return Intersects(a.fLeft, a.fTop, a.fRight, a.fBottom,
     *                           b.fLeft, b.fTop, b.fRight, b.fBottom);
     *     }
     * ```
     */
    public fun intersects(a: SkRect, b: SkRect): Boolean {
      TODO("Implement intersects")
    }
  }
}
