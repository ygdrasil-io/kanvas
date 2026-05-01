package org.skia.math

import kotlin.Boolean
import kotlin.Int
import kotlin.Long

/**
 * C++ original:
 * ```cpp
 * struct SK_API SkIRect {
 *     int32_t fLeft   = 0; //!< smaller x-axis bounds
 *     int32_t fTop    = 0; //!< smaller y-axis bounds
 *     int32_t fRight  = 0; //!< larger x-axis bounds
 *     int32_t fBottom = 0; //!< larger y-axis bounds
 *
 *     /** Returns constructed SkIRect set to (0, 0, 0, 0).
 *         Many other rectangles are empty; if left is equal to or greater than right,
 *         or if top is equal to or greater than bottom. Setting all members to zero
 *         is a convenience, but does not designate a special empty rectangle.
 *
 *         @return  bounds (0, 0, 0, 0)
 *     */
 *     [[nodiscard]] static constexpr SkIRect MakeEmpty() {
 *         return SkIRect{0, 0, 0, 0};
 *     }
 *
 *     /** Returns constructed SkIRect set to (0, 0, w, h). Does not validate input; w or h
 *         may be negative.
 *
 *         @param w  width of constructed SkIRect
 *         @param h  height of constructed SkIRect
 *         @return   bounds (0, 0, w, h)
 *     */
 *     [[nodiscard]] static constexpr SkIRect MakeWH(int32_t w, int32_t h) {
 *         return SkIRect{0, 0, w, h};
 *     }
 *
 *     /** Returns constructed SkIRect set to (0, 0, size.width(), size.height()).
 *         Does not validate input; size.width() or size.height() may be negative.
 *
 *         @param size  values for SkIRect width and height
 *         @return      bounds (0, 0, size.width(), size.height())
 *     */
 *     [[nodiscard]] static constexpr SkIRect MakeSize(const SkISize& size) {
 *         return SkIRect{0, 0, size.fWidth, size.fHeight};
 *     }
 *
 *     /** Returns constructed SkIRect set to (pt.x(), pt.y(), pt.x() + size.width(),
 *         pt.y() + size.height()). Does not validate input; size.width() or size.height() may be
 *         negative.
 *
 *         @param pt    values for SkIRect fLeft and fTop
 *         @param size  values for SkIRect width and height
 *         @return      bounds at pt with width and height of size
 *     */
 *     [[nodiscard]] static constexpr SkIRect MakePtSize(SkIPoint pt, SkISize size) {
 *         return MakeXYWH(pt.x(), pt.y(), size.width(), size.height());
 *     }
 *
 *     /** Returns constructed SkIRect set to (l, t, r, b). Does not sort input; SkIRect may
 *         result in fLeft greater than fRight, or fTop greater than fBottom.
 *
 *         @param l  integer stored in fLeft
 *         @param t  integer stored in fTop
 *         @param r  integer stored in fRight
 *         @param b  integer stored in fBottom
 *         @return   bounds (l, t, r, b)
 *     */
 *     [[nodiscard]] static constexpr SkIRect MakeLTRB(int32_t l, int32_t t, int32_t r, int32_t b) {
 *         return SkIRect{l, t, r, b};
 *     }
 *
 *     /** Returns constructed SkIRect set to: (x, y, x + w, y + h).
 *         Does not validate input; w or h may be negative.
 *
 *         @param x  stored in fLeft
 *         @param y  stored in fTop
 *         @param w  added to x and stored in fRight
 *         @param h  added to y and stored in fBottom
 *         @return   bounds at (x, y) with width w and height h
 *     */
 *     [[nodiscard]] static constexpr SkIRect MakeXYWH(int32_t x, int32_t y, int32_t w, int32_t h) {
 *         return { x, y, Sk32_sat_add(x, w), Sk32_sat_add(y, h) };
 *     }
 *
 *     /** Returns left edge of SkIRect, if sorted.
 *         Call sort() to reverse fLeft and fRight if needed.
 *
 *         @return  fLeft
 *     */
 *     constexpr int32_t left() const { return fLeft; }
 *
 *     /** Returns top edge of SkIRect, if sorted. Call isEmpty() to see if SkIRect may be invalid,
 *         and sort() to reverse fTop and fBottom if needed.
 *
 *         @return  fTop
 *     */
 *     constexpr int32_t top() const { return fTop; }
 *
 *     /** Returns right edge of SkIRect, if sorted.
 *         Call sort() to reverse fLeft and fRight if needed.
 *
 *         @return  fRight
 *     */
 *     constexpr int32_t right() const { return fRight; }
 *
 *     /** Returns bottom edge of SkIRect, if sorted. Call isEmpty() to see if SkIRect may be invalid,
 *         and sort() to reverse fTop and fBottom if needed.
 *
 *         @return  fBottom
 *     */
 *     constexpr int32_t bottom() const { return fBottom; }
 *
 *     /** Returns left edge of SkIRect, if sorted. Call isEmpty() to see if SkIRect may be invalid,
 *         and sort() to reverse fLeft and fRight if needed.
 *
 *         @return  fLeft
 *     */
 *     constexpr int32_t x() const { return fLeft; }
 *
 *     /** Returns top edge of SkIRect, if sorted. Call isEmpty() to see if SkIRect may be invalid,
 *         and sort() to reverse fTop and fBottom if needed.
 *
 *         @return  fTop
 *     */
 *     constexpr int32_t y() const { return fTop; }
 *
 *     // Experimental
 *     constexpr SkIPoint topLeft() const { return {fLeft, fTop}; }
 *
 *     /** Returns span on the x-axis. This does not check if SkIRect is sorted, or if
 *         result fits in 32-bit signed integer; result may be negative.
 *
 *         @return  fRight minus fLeft
 *     */
 *     constexpr int32_t width() const { return Sk32_can_overflow_sub(fRight, fLeft); }
 *
 *     /** Returns span on the y-axis. This does not check if SkIRect is sorted, or if
 *         result fits in 32-bit signed integer; result may be negative.
 *
 *         @return  fBottom minus fTop
 *     */
 *     constexpr int32_t height() const { return Sk32_can_overflow_sub(fBottom, fTop); }
 *
 *     /** Returns spans on the x-axis and y-axis. This does not check if SkIRect is sorted,
 *         or if result fits in 32-bit signed integer; result may be negative.
 *
 *         @return  SkISize (width, height)
 *     */
 *     constexpr SkISize size() const { return SkISize::Make(this->width(), this->height()); }
 *
 *     /** Returns span on the x-axis. This does not check if SkIRect is sorted, so the
 *         result may be negative. This is safer than calling width() since width() might
 *         overflow in its calculation.
 *
 *         @return  fRight minus fLeft cast to int64_t
 *     */
 *     constexpr int64_t width64() const { return (int64_t)fRight - (int64_t)fLeft; }
 *
 *     /** Returns span on the y-axis. This does not check if SkIRect is sorted, so the
 *         result may be negative. This is safer than calling height() since height() might
 *         overflow in its calculation.
 *
 *         @return  fBottom minus fTop cast to int64_t
 *     */
 *     constexpr int64_t height64() const { return (int64_t)fBottom - (int64_t)fTop; }
 *
 *     /** Returns true if fLeft is equal to or greater than fRight, or if fTop is equal
 *         to or greater than fBottom. Call sort() to reverse rectangles with negative
 *         width64() or height64().
 *
 *         @return  true if width64() or height64() are zero or negative
 *     */
 *     bool isEmpty64() const { return fRight <= fLeft || fBottom <= fTop; }
 *
 *     /** Returns true if width() or height() are zero or negative.
 *
 *         @return  true if width() or height() are zero or negative
 *     */
 *     bool isEmpty() const {
 *         int64_t w = this->width64();
 *         int64_t h = this->height64();
 *         if (w <= 0 || h <= 0) {
 *             return true;
 *         }
 *         // Return true if either exceeds int32_t
 *         return !SkTFitsIn<int32_t>(w | h);
 *     }
 *
 *     /** Returns true if all members in a: fLeft, fTop, fRight, and fBottom; are
 *         identical to corresponding members in b.
 *
 *         @param a  SkIRect to compare
 *         @param b  SkIRect to compare
 *         @return   true if members are equal
 *     */
 *     friend bool operator==(const SkIRect& a, const SkIRect& b) {
 *         return a.fLeft == b.fLeft && a.fTop == b.fTop &&
 *                a.fRight == b.fRight && a.fBottom == b.fBottom;
 *     }
 *
 *     /** Returns true if any member in a: fLeft, fTop, fRight, and fBottom; is not
 *         identical to the corresponding member in b.
 *
 *         @param a  SkIRect to compare
 *         @param b  SkIRect to compare
 *         @return   true if members are not equal
 *     */
 *     friend bool operator!=(const SkIRect& a, const SkIRect& b) {
 *         return a.fLeft != b.fLeft || a.fTop != b.fTop ||
 *                a.fRight != b.fRight || a.fBottom != b.fBottom;
 *     }
 *
 *     /** Sets SkIRect to (0, 0, 0, 0).
 *
 *         Many other rectangles are empty; if left is equal to or greater than right,
 *         or if top is equal to or greater than bottom. Setting all members to zero
 *         is a convenience, but does not designate a special empty rectangle.
 *     */
 *     void setEmpty() { memset(this, 0, sizeof(*this)); }
 *
 *     /** Sets SkIRect to (left, top, right, bottom).
 *         left and right are not sorted; left is not necessarily less than right.
 *         top and bottom are not sorted; top is not necessarily less than bottom.
 *
 *         @param left    stored in fLeft
 *         @param top     stored in fTop
 *         @param right   stored in fRight
 *         @param bottom  stored in fBottom
 *     */
 *     void setLTRB(int32_t left, int32_t top, int32_t right, int32_t bottom) {
 *         fLeft   = left;
 *         fTop    = top;
 *         fRight  = right;
 *         fBottom = bottom;
 *     }
 *
 *     /** Sets SkIRect to: (x, y, x + width, y + height).
 *         Does not validate input; width or height may be negative.
 *
 *         @param x       stored in fLeft
 *         @param y       stored in fTop
 *         @param width   added to x and stored in fRight
 *         @param height  added to y and stored in fBottom
 *     */
 *     void setXYWH(int32_t x, int32_t y, int32_t width, int32_t height) {
 *         fLeft   = x;
 *         fTop    = y;
 *         fRight  = Sk32_sat_add(x, width);
 *         fBottom = Sk32_sat_add(y, height);
 *     }
 *
 *     void setWH(int32_t width, int32_t height) {
 *         fLeft   = 0;
 *         fTop    = 0;
 *         fRight  = width;
 *         fBottom = height;
 *     }
 *
 *     void setSize(SkISize size) {
 *         fLeft = 0;
 *         fTop = 0;
 *         fRight = size.width();
 *         fBottom = size.height();
 *     }
 *
 *     /** Returns SkIRect offset by (dx, dy).
 *
 *         If dx is negative, SkIRect returned is moved to the left.
 *         If dx is positive, SkIRect returned is moved to the right.
 *         If dy is negative, SkIRect returned is moved upward.
 *         If dy is positive, SkIRect returned is moved downward.
 *
 *         @param dx  offset added to fLeft and fRight
 *         @param dy  offset added to fTop and fBottom
 *         @return    SkIRect offset by dx and dy, with original width and height
 *     */
 *     constexpr SkIRect makeOffset(int32_t dx, int32_t dy) const {
 *         return {
 *             Sk32_sat_add(fLeft,  dx), Sk32_sat_add(fTop,    dy),
 *             Sk32_sat_add(fRight, dx), Sk32_sat_add(fBottom, dy),
 *         };
 *     }
 *
 *     /** Returns SkIRect offset by (offset.x(), offset.y()).
 *
 *         If offset.x() is negative, SkIRect returned is moved to the left.
 *         If offset.x() is positive, SkIRect returned is moved to the right.
 *         If offset.y() is negative, SkIRect returned is moved upward.
 *         If offset.y() is positive, SkIRect returned is moved downward.
 *
 *         @param offset  translation vector
 *         @return    SkIRect translated by offset, with original width and height
 *     */
 *     constexpr SkIRect makeOffset(SkIVector offset) const {
 *         return this->makeOffset(offset.x(), offset.y());
 *     }
 *
 *     /** Returns SkIRect, inset by (dx, dy).
 *
 *         If dx is negative, SkIRect returned is wider.
 *         If dx is positive, SkIRect returned is narrower.
 *         If dy is negative, SkIRect returned is taller.
 *         If dy is positive, SkIRect returned is shorter.
 *
 *         @param dx  offset added to fLeft and subtracted from fRight
 *         @param dy  offset added to fTop and subtracted from fBottom
 *         @return    SkIRect inset symmetrically left and right, top and bottom
 *     */
 *     SkIRect makeInset(int32_t dx, int32_t dy) const {
 *         return {
 *             Sk32_sat_add(fLeft,  dx), Sk32_sat_add(fTop,    dy),
 *             Sk32_sat_sub(fRight, dx), Sk32_sat_sub(fBottom, dy),
 *         };
 *     }
 *
 *     /** Returns SkIRect, outset by (dx, dy).
 *
 *         If dx is negative, SkIRect returned is narrower.
 *         If dx is positive, SkIRect returned is wider.
 *         If dy is negative, SkIRect returned is shorter.
 *         If dy is positive, SkIRect returned is taller.
 *
 *         @param dx  offset subtracted to fLeft and added from fRight
 *         @param dy  offset subtracted to fTop and added from fBottom
 *         @return    SkIRect outset symmetrically left and right, top and bottom
 *     */
 *     SkIRect makeOutset(int32_t dx, int32_t dy) const {
 *         return {
 *             Sk32_sat_sub(fLeft,  dx), Sk32_sat_sub(fTop,    dy),
 *             Sk32_sat_add(fRight, dx), Sk32_sat_add(fBottom, dy),
 *         };
 *     }
 *
 *     /** Offsets SkIRect by adding dx to fLeft, fRight; and by adding dy to fTop, fBottom.
 *
 *         If dx is negative, moves SkIRect returned to the left.
 *         If dx is positive, moves SkIRect returned to the right.
 *         If dy is negative, moves SkIRect returned upward.
 *         If dy is positive, moves SkIRect returned downward.
 *
 *         @param dx  offset added to fLeft and fRight
 *         @param dy  offset added to fTop and fBottom
 *     */
 *     void offset(int32_t dx, int32_t dy) {
 *         fLeft   = Sk32_sat_add(fLeft,   dx);
 *         fTop    = Sk32_sat_add(fTop,    dy);
 *         fRight  = Sk32_sat_add(fRight,  dx);
 *         fBottom = Sk32_sat_add(fBottom, dy);
 *     }
 *
 *     /** Offsets SkIRect by adding delta.fX to fLeft, fRight; and by adding delta.fY to
 *         fTop, fBottom.
 *
 *         If delta.fX is negative, moves SkIRect returned to the left.
 *         If delta.fX is positive, moves SkIRect returned to the right.
 *         If delta.fY is negative, moves SkIRect returned upward.
 *         If delta.fY is positive, moves SkIRect returned downward.
 *
 *         @param delta  offset added to SkIRect
 *     */
 *     void offset(const SkIPoint& delta) {
 *         this->offset(delta.fX, delta.fY);
 *     }
 *
 *     /** Offsets SkIRect so that fLeft equals newX, and fTop equals newY. width and height
 *         are unchanged.
 *
 *         @param newX  stored in fLeft, preserving width()
 *         @param newY  stored in fTop, preserving height()
 *     */
 *     void offsetTo(int32_t newX, int32_t newY) {
 *         fRight  = Sk64_pin_to_s32((int64_t)fRight + newX - fLeft);
 *         fBottom = Sk64_pin_to_s32((int64_t)fBottom + newY - fTop);
 *         fLeft   = newX;
 *         fTop    = newY;
 *     }
 *
 *     /** Insets SkIRect by (dx,dy).
 *
 *         If dx is positive, makes SkIRect narrower.
 *         If dx is negative, makes SkIRect wider.
 *         If dy is positive, makes SkIRect shorter.
 *         If dy is negative, makes SkIRect taller.
 *
 *         @param dx  offset added to fLeft and subtracted from fRight
 *         @param dy  offset added to fTop and subtracted from fBottom
 *     */
 *     void inset(int32_t dx, int32_t dy) {
 *         fLeft   = Sk32_sat_add(fLeft,   dx);
 *         fTop    = Sk32_sat_add(fTop,    dy);
 *         fRight  = Sk32_sat_sub(fRight,  dx);
 *         fBottom = Sk32_sat_sub(fBottom, dy);
 *     }
 *
 *     /** Outsets SkIRect by (dx, dy).
 *
 *         If dx is positive, makes SkIRect wider.
 *         If dx is negative, makes SkIRect narrower.
 *         If dy is positive, makes SkIRect taller.
 *         If dy is negative, makes SkIRect shorter.
 *
 *         @param dx  subtracted to fLeft and added from fRight
 *         @param dy  subtracted to fTop and added from fBottom
 *     */
 *     void outset(int32_t dx, int32_t dy)  { this->inset(-dx, -dy); }
 *
 *     /** Adjusts SkIRect by adding dL to fLeft, dT to fTop, dR to fRight, and dB to fBottom.
 *
 *         If dL is positive, narrows SkIRect on the left. If negative, widens it on the left.
 *         If dT is positive, shrinks SkIRect on the top. If negative, lengthens it on the top.
 *         If dR is positive, narrows SkIRect on the right. If negative, widens it on the right.
 *         If dB is positive, shrinks SkIRect on the bottom. If negative, lengthens it on the bottom.
 *
 *         The resulting SkIRect is not checked for validity. Thus, if the resulting SkIRect left is
 *         greater than right, the SkIRect will be considered empty. Call sort() after this call
 *         if that is not the desired behavior.
 *
 *         @param dL  offset added to fLeft
 *         @param dT  offset added to fTop
 *         @param dR  offset added to fRight
 *         @param dB  offset added to fBottom
 *     */
 *     void adjust(int32_t dL, int32_t dT, int32_t dR, int32_t dB) {
 *         fLeft   = Sk32_sat_add(fLeft,   dL);
 *         fTop    = Sk32_sat_add(fTop,    dT);
 *         fRight  = Sk32_sat_add(fRight,  dR);
 *         fBottom = Sk32_sat_add(fBottom, dB);
 *     }
 *
 *     /** Returns true if: fLeft <= x < fRight && fTop <= y < fBottom.
 *         Returns false if SkIRect is empty.
 *
 *         Considers input to describe constructed SkIRect: (x, y, x + 1, y + 1) and
 *         returns true if constructed area is completely enclosed by SkIRect area.
 *
 *         @param x  test SkIPoint x-coordinate
 *         @param y  test SkIPoint y-coordinate
 *         @return   true if (x, y) is inside SkIRect
 *     */
 *     bool contains(int32_t x, int32_t y) const {
 *         return x >= fLeft && x < fRight && y >= fTop && y < fBottom;
 *     }
 *
 *     /** Returns true if SkIRect contains r.
 *      Returns false if SkIRect is empty or r is empty.
 *
 *      SkIRect contains r when SkIRect area completely includes r area.
 *
 *      @param r  SkIRect contained
 *      @return   true if all sides of SkIRect are outside r
 *      */
 *     bool contains(const SkIRect& r) const {
 *         return  !r.isEmpty() && !this->isEmpty() &&     // check for empties
 *                 fLeft <= r.fLeft && fTop <= r.fTop &&
 *                 fRight >= r.fRight && fBottom >= r.fBottom;
 *     }
 *
 *     /** Returns true if SkIRect contains r.
 *         Returns false if SkIRect is empty or r is empty.
 *
 *         SkIRect contains r when SkIRect area completely includes r area.
 *
 *         @param r  SkRect contained
 *         @return   true if all sides of SkIRect are outside r
 *     */
 *     inline bool contains(const SkRect& r) const;
 *
 *     /** Returns true if SkIRect contains construction.
 *         Asserts if SkIRect is empty or construction is empty, and if SK_DEBUG is defined.
 *
 *         Return is undefined if SkIRect is empty or construction is empty.
 *
 *         @param r  SkIRect contained
 *         @return   true if all sides of SkIRect are outside r
 *     */
 *     bool containsNoEmptyCheck(const SkIRect& r) const {
 *         SkASSERT(fLeft < fRight && fTop < fBottom);
 *         SkASSERT(r.fLeft < r.fRight && r.fTop < r.fBottom);
 *         return fLeft <= r.fLeft && fTop <= r.fTop && fRight >= r.fRight && fBottom >= r.fBottom;
 *     }
 *
 *     /** Returns true if SkIRect intersects r, and sets SkIRect to intersection.
 *         Returns false if SkIRect does not intersect r, and leaves SkIRect unchanged.
 *
 *         Returns false if either r or SkIRect is empty, leaving SkIRect unchanged.
 *
 *         @param r  limit of result
 *         @return   true if r and SkIRect have area in common
 *     */
 *     bool intersect(const SkIRect& r) {
 *         return this->intersect(*this, r);
 *     }
 *
 *     /** Returns true if a intersects b, and sets SkIRect to intersection.
 *         Returns false if a does not intersect b, and leaves SkIRect unchanged.
 *
 *         Returns false if either a or b is empty, leaving SkIRect unchanged.
 *
 *         @param a  SkIRect to intersect
 *         @param b  SkIRect to intersect
 *         @return   true if a and b have area in common
 *     */
 *     [[nodiscard]] bool intersect(const SkIRect& a, const SkIRect& b);
 *
 *     /** Returns true if a intersects b.
 *         Returns false if either a or b is empty, or do not intersect.
 *
 *         @param a  SkIRect to intersect
 *         @param b  SkIRect to intersect
 *         @return   true if a and b have area in common
 *     */
 *     static bool Intersects(const SkIRect& a, const SkIRect& b) {
 *         return SkIRect{}.intersect(a, b);
 *     }
 *
 *     /** Sets SkIRect to the union of itself and r.
 *
 *      Has no effect if r is empty. Otherwise, if SkIRect is empty, sets SkIRect to r.
 *
 *      @param r  expansion SkIRect
 *
 *         example: https://fiddle.skia.org/c/@IRect_join_2
 *      */
 *     void join(const SkIRect& r);
 *
 *     /** Swaps fLeft and fRight if fLeft is greater than fRight; and swaps
 *         fTop and fBottom if fTop is greater than fBottom. Result may be empty,
 *         and width() and height() will be zero or positive.
 *     */
 *     void sort() {
 *         using std::swap;
 *         if (fLeft > fRight) {
 *             swap(fLeft, fRight);
 *         }
 *         if (fTop > fBottom) {
 *             swap(fTop, fBottom);
 *         }
 *     }
 *
 *     /** Returns SkIRect with fLeft and fRight swapped if fLeft is greater than fRight; and
 *         with fTop and fBottom swapped if fTop is greater than fBottom. Result may be empty;
 *         and width() and height() will be zero or positive.
 *
 *         @return  sorted SkIRect
 *     */
 *     SkIRect makeSorted() const {
 *         return MakeLTRB(std::min(fLeft, fRight), std::min(fTop, fBottom),
 *                         std::max(fLeft, fRight), std::max(fTop, fBottom));
 *     }
 *
 *     /** Returns pointer to first int32 in SkIRect, to treat it as an array with four
 *         entries.
 *
 *         @return  pointer to fLeft
 *     */
 *     const int32_t* asInt32s() const { return &fLeft; }
 * }
 * ```
 */
public open class SkIRect public constructor(
  /**
   * C++ original:
   * ```cpp
   * int32_t fLeft   = 0
   * ```
   */
  public var fLeft: Int,
  /**
   * C++ original:
   * ```cpp
   * int32_t fTop    = 0
   * ```
   */
  public var fTop: Int,
  /**
   * C++ original:
   * ```cpp
   * int32_t fRight  = 0
   * ```
   */
  public var fRight: Int,
  /**
   * C++ original:
   * ```cpp
   * int32_t fBottom = 0
   * ```
   */
  public var fBottom: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * constexpr int32_t left() const { return fLeft; }
   * ```
   */
  public fun left(): Int {
    TODO("Implement left")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr int32_t top() const { return fTop; }
   * ```
   */
  public fun top(): Int {
    return fTop
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr int32_t right() const { return fRight; }
   * ```
   */
  public fun right(): Int {
    return fRight
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr int32_t bottom() const { return fBottom; }
   * ```
   */
  public fun bottom(): Int {
    return bottom()
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr int32_t x() const { return fLeft; }
   * ```
   */
  public fun x(): Int {
    return fLeft
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr int32_t y() const { return fTop; }
   * ```
   */
  public fun y(): Int {
    return this.fTop
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr SkIPoint topLeft() const { return {fLeft, fTop}; }
   * ```
   */
  public fun topLeft(): Int {
    TODO("Implement topLeft")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr int32_t width() const { return Sk32_can_overflow_sub(fRight, fLeft); }
   * ```
   */
  public fun width(): Int {
    return fRight - fLeft
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr int32_t height() const { return Sk32_can_overflow_sub(fBottom, fTop); }
   * ```
   */
  public fun height(): Int {
    TODO("Implement height")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr SkISize size() const { return SkISize::Make(this->width(), this->height()); }
   * ```
   */
  public fun size(): Int {
    TODO("Implement size")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr int64_t width64() const { return (int64_t)fRight - (int64_t)fLeft; }
   * ```
   */
  public fun width64(): Long {
    TODO("Implement width64")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr int64_t height64() const { return (int64_t)fBottom - (int64_t)fTop; }
   * ```
   */
  public fun height64(): Long {
    TODO("Implement height64")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isEmpty64() const { return fRight <= fLeft || fBottom <= fTop; }
   * ```
   */
  public fun isEmpty64(): Boolean {
    TODO("Implement isEmpty64")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isEmpty() const {
   *         int64_t w = this->width64();
   *         int64_t h = this->height64();
   *         if (w <= 0 || h <= 0) {
   *             return true;
   *         }
   *         // Return true if either exceeds int32_t
   *         return !SkTFitsIn<int32_t>(w | h);
   *     }
   * ```
   */
  public fun isEmpty(): Boolean {
    TODO("Implement isEmpty")
  }

  /**
   * C++ original:
   * ```cpp
   * void setEmpty() { memset(this, 0, sizeof(*this)); }
   * ```
   */
  public fun setEmpty() {
    TODO("Implement setEmpty")
  }

  /**
   * C++ original:
   * ```cpp
   * void setLTRB(int32_t left, int32_t top, int32_t right, int32_t bottom) {
   *         fLeft   = left;
   *         fTop    = top;
   *         fRight  = right;
   *         fBottom = bottom;
   *     }
   * ```
   */
  public fun setLTRB(
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
  ) {
    TODO("Implement setLTRB")
  }

  /**
   * C++ original:
   * ```cpp
   * void setXYWH(int32_t x, int32_t y, int32_t width, int32_t height) {
   *         fLeft   = x;
   *         fTop    = y;
   *         fRight  = Sk32_sat_add(x, width);
   *         fBottom = Sk32_sat_add(y, height);
   *     }
   * ```
   */
  public fun setXYWH(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
  ) {
    TODO("Implement setXYWH")
  }

  /**
   * C++ original:
   * ```cpp
   * void setWH(int32_t width, int32_t height) {
   *         fLeft   = 0;
   *         fTop    = 0;
   *         fRight  = width;
   *         fBottom = height;
   *     }
   * ```
   */
  public fun setWH(width: Int, height: Int) {
    TODO("Implement setWH")
  }

  /**
   * C++ original:
   * ```cpp
   * void setSize(SkISize size) {
   *         fLeft = 0;
   *         fTop = 0;
   *         fRight = size.width();
   *         fBottom = size.height();
   *     }
   * ```
   */
  public fun setSize(size: SkISize) {
    TODO("Implement setSize")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr SkIRect makeOffset(int32_t dx, int32_t dy) const {
   *         return {
   *             Sk32_sat_add(fLeft,  dx), Sk32_sat_add(fTop,    dy),
   *             Sk32_sat_add(fRight, dx), Sk32_sat_add(fBottom, dy),
   *         };
   *     }
   * ```
   */
  public fun makeOffset(dx: Int, dy: Int): SkIRect {
    TODO("Implement makeOffset")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr SkIRect makeOffset(SkIVector offset) const {
   *         return this->makeOffset(offset.x(), offset.y());
   *     }
   * ```
   */
  public fun makeOffset(offset: SkIVector): SkIRect {
    TODO("Implement makeOffset")
  }

  /**
   * C++ original:
   * ```cpp
   * SkIRect makeInset(int32_t dx, int32_t dy) const {
   *         return {
   *             Sk32_sat_add(fLeft,  dx), Sk32_sat_add(fTop,    dy),
   *             Sk32_sat_sub(fRight, dx), Sk32_sat_sub(fBottom, dy),
   *         };
   *     }
   * ```
   */
  public fun makeInset(dx: Int, dy: Int): SkIRect {
    TODO("Implement makeInset")
  }

  /**
   * C++ original:
   * ```cpp
   * SkIRect makeOutset(int32_t dx, int32_t dy) const {
   *         return {
   *             Sk32_sat_sub(fLeft,  dx), Sk32_sat_sub(fTop,    dy),
   *             Sk32_sat_add(fRight, dx), Sk32_sat_add(fBottom, dy),
   *         };
   *     }
   * ```
   */
  public fun makeOutset(dx: Int, dy: Int): SkIRect {
    TODO("Implement makeOutset")
  }

  /**
   * C++ original:
   * ```cpp
   * void offset(int32_t dx, int32_t dy) {
   *         fLeft   = Sk32_sat_add(fLeft,   dx);
   *         fTop    = Sk32_sat_add(fTop,    dy);
   *         fRight  = Sk32_sat_add(fRight,  dx);
   *         fBottom = Sk32_sat_add(fBottom, dy);
   *     }
   * ```
   */
  public fun offset(dx: Int, dy: Int) {
    TODO("Implement offset")
  }

  /**
   * C++ original:
   * ```cpp
   * void offset(const SkIPoint& delta) {
   *         this->offset(delta.fX, delta.fY);
   *     }
   * ```
   */
  public fun offset(delta: SkIPoint) {
    TODO("Implement offset")
  }

  /**
   * C++ original:
   * ```cpp
   * void offsetTo(int32_t newX, int32_t newY) {
   *         fRight  = Sk64_pin_to_s32((int64_t)fRight + newX - fLeft);
   *         fBottom = Sk64_pin_to_s32((int64_t)fBottom + newY - fTop);
   *         fLeft   = newX;
   *         fTop    = newY;
   *     }
   * ```
   */
  public fun offsetTo(newX: Int, newY: Int) {
    TODO("Implement offsetTo")
  }

  /**
   * C++ original:
   * ```cpp
   * void inset(int32_t dx, int32_t dy) {
   *         fLeft   = Sk32_sat_add(fLeft,   dx);
   *         fTop    = Sk32_sat_add(fTop,    dy);
   *         fRight  = Sk32_sat_sub(fRight,  dx);
   *         fBottom = Sk32_sat_sub(fBottom, dy);
   *     }
   * ```
   */
  public fun inset(dx: Int, dy: Int) {
    TODO("Implement inset")
  }

  /**
   * C++ original:
   * ```cpp
   * void outset(int32_t dx, int32_t dy)  { this->inset(-dx, -dy); }
   * ```
   */
  public fun outset(dx: Int, dy: Int) {
    TODO("Implement outset")
  }

  /**
   * C++ original:
   * ```cpp
   * void adjust(int32_t dL, int32_t dT, int32_t dR, int32_t dB) {
   *         fLeft   = Sk32_sat_add(fLeft,   dL);
   *         fTop    = Sk32_sat_add(fTop,    dT);
   *         fRight  = Sk32_sat_add(fRight,  dR);
   *         fBottom = Sk32_sat_add(fBottom, dB);
   *     }
   * ```
   */
  public fun adjust(
    dL: Int,
    dT: Int,
    dR: Int,
    dB: Int,
  ) {
    TODO("Implement adjust")
  }

  /**
   * C++ original:
   * ```cpp
   * bool contains(int32_t x, int32_t y) const {
   *         return x >= fLeft && x < fRight && y >= fTop && y < fBottom;
   *     }
   * ```
   */
  public fun contains(x: Int, y: Int): Boolean {
    TODO("Implement contains")
  }

  /**
   * C++ original:
   * ```cpp
   * bool contains(const SkIRect& r) const {
   *         return  !r.isEmpty() && !this->isEmpty() &&     // check for empties
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
   * inline bool SkIRect::contains(const SkRect& r) const {
   *     return  !r.isEmpty() && !this->isEmpty() &&     // check for empties
   *             fLeft <= r.fLeft && fTop <= r.fTop &&
   *             fRight >= r.fRight && fBottom >= r.fBottom;
   * }
   * ```
   */
  public fun contains(r: SkRect): Boolean {
    TODO("Implement contains")
  }

  /**
   * C++ original:
   * ```cpp
   * bool containsNoEmptyCheck(const SkIRect& r) const {
   *         SkASSERT(fLeft < fRight && fTop < fBottom);
   *         SkASSERT(r.fLeft < r.fRight && r.fTop < r.fBottom);
   *         return fLeft <= r.fLeft && fTop <= r.fTop && fRight >= r.fRight && fBottom >= r.fBottom;
   *     }
   * ```
   */
  public fun containsNoEmptyCheck(r: SkIRect): Boolean {
    TODO("Implement containsNoEmptyCheck")
  }

  /**
   * C++ original:
   * ```cpp
   * bool intersect(const SkIRect& r) {
   *         return this->intersect(*this, r);
   *     }
   * ```
   */
  public fun intersect(r: SkIRect): Boolean {
    TODO("Implement intersect")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkIRect::intersect(const SkIRect& a, const SkIRect& b) {
   *     SkIRect tmp = {
   *         std::max(a.fLeft,   b.fLeft),
   *         std::max(a.fTop,    b.fTop),
   *         std::min(a.fRight,  b.fRight),
   *         std::min(a.fBottom, b.fBottom)
   *     };
   *     if (tmp.isEmpty()) {
   *         return false;
   *     }
   *     *this = tmp;
   *     return true;
   * }
   * ```
   */
  public fun intersect(a: SkIRect, b: SkIRect): Boolean {
    TODO("Implement intersect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkIRect::join(const SkIRect& r) {
   *     // do nothing if the params are empty
   *     if (r.fLeft >= r.fRight || r.fTop >= r.fBottom) {
   *         return;
   *     }
   *
   *     // if we are empty, just assign
   *     if (fLeft >= fRight || fTop >= fBottom) {
   *         *this = r;
   *     } else {
   *         if (r.fLeft < fLeft)     fLeft = r.fLeft;
   *         if (r.fTop < fTop)       fTop = r.fTop;
   *         if (r.fRight > fRight)   fRight = r.fRight;
   *         if (r.fBottom > fBottom) fBottom = r.fBottom;
   *     }
   * }
   * ```
   */
  public fun join(r: SkIRect) {
    TODO("Implement join")
  }

  /**
   * C++ original:
   * ```cpp
   * void sort() {
   *         using std::swap;
   *         if (fLeft > fRight) {
   *             swap(fLeft, fRight);
   *         }
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
   * SkIRect makeSorted() const {
   *         return MakeLTRB(std::min(fLeft, fRight), std::min(fTop, fBottom),
   *                         std::max(fLeft, fRight), std::max(fTop, fBottom));
   *     }
   * ```
   */
  public fun makeSorted(): SkIRect {
    TODO("Implement makeSorted")
  }

  /**
   * C++ original:
   * ```cpp
   * const int32_t* asInt32s() const { return &fLeft; }
   * ```
   */
  public fun asInt32s(): Int {
    TODO("Implement asInt32s")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static constexpr SkIRect MakeEmpty() {
     *         return SkIRect{0, 0, 0, 0};
     *     }
     * ```
     */
    public fun makeEmpty(): SkIRect {
      TODO("Implement makeEmpty")
    }

    /**
     * C++ original:
     * ```cpp
     * static constexpr SkIRect MakeWH(int32_t w, int32_t h) {
     *         return SkIRect{0, 0, w, h};
     *     }
     * ```
     */
    public fun makeWH(w: Int, h: Int): SkIRect {
      TODO("Implement makeWH")
    }

    /**
     * C++ original:
     * ```cpp
     * static constexpr SkIRect MakeSize(const SkISize& size) {
     *         return SkIRect{0, 0, size.fWidth, size.fHeight};
     *     }
     * ```
     */
    public fun makeSize(size: SkISize): SkIRect {
      TODO("Implement makeSize")
    }

    /**
     * C++ original:
     * ```cpp
     * static constexpr SkIRect MakePtSize(SkIPoint pt, SkISize size) {
     *         return MakeXYWH(pt.x(), pt.y(), size.width(), size.height());
     *     }
     * ```
     */
    public fun makePtSize(pt: SkIPoint, size: SkISize): SkIRect {
      TODO("Implement makePtSize")
    }

    /**
     * C++ original:
     * ```cpp
     * static constexpr SkIRect MakeLTRB(int32_t l, int32_t t, int32_t r, int32_t b) {
     *         return SkIRect{l, t, r, b};
     *     }
     * ```
     */
    public fun makeLTRB(
      l: Int,
      t: Int,
      r: Int,
      b: Int,
    ): SkIRect {
      TODO("Implement makeLTRB")
    }

    /**
     * C++ original:
     * ```cpp
     * static constexpr SkIRect MakeXYWH(int32_t x, int32_t y, int32_t w, int32_t h) {
     *         return { x, y, Sk32_sat_add(x, w), Sk32_sat_add(y, h) };
     *     }
     * ```
     */
    public fun makeXYWH(
      x: Int,
      y: Int,
      w: Int,
      h: Int,
    ): SkIRect {
      TODO("Implement makeXYWH")
    }

    /**
     * C++ original:
     * ```cpp
     * static bool Intersects(const SkIRect& a, const SkIRect& b) {
     *         return SkIRect{}.intersect(a, b);
     *     }
     * ```
     */
    public fun intersects(a: SkIRect, b: SkIRect): Boolean {
      TODO("Implement intersects")
    }
  }
}
