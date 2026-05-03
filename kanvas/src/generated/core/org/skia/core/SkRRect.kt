package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.IntArray
import kotlin.String
import kotlin.ULong
import kotlin.Unit
import org.skia.math.SkMatrix
import org.skia.math.SkRect
import org.skia.math.SkScalar
import org.skia.math.SkVector

/**
 * C++ original:
 * ```cpp
 * class SK_API SkRRect {
 * public:
 *
 *     /** Initializes bounds at (0, 0), the origin, with zero width and height.
 *         Initializes corner radii to (0, 0), and sets type of kEmpty_Type.
 *
 *         @return  empty SkRRect
 *     */
 *     SkRRect() = default;
 *
 *     /** Initializes to copy of rrect bounds and corner radii.
 *
 *         @param rrect  bounds and corner to copy
 *         @return       copy of rrect
 *     */
 *     SkRRect(const SkRRect& rrect) = default;
 *
 *     /** Copies rrect bounds and corner radii.
 *
 *         @param rrect  bounds and corner to copy
 *         @return       copy of rrect
 *     */
 *     SkRRect& operator=(const SkRRect& rrect) = default;
 *
 *     /** \enum SkRRect::Type
 *         Type describes possible specializations of SkRRect. Each Type is
 *         exclusive; a SkRRect may only have one type.
 *
 *         Type members become progressively less restrictive; larger values of
 *         Type have more degrees of freedom than smaller values.
 *     */
 *     enum Type {
 *         kEmpty_Type,                     //!< zero width or height
 *         kRect_Type,                      //!< non-zero width and height, and zeroed radii
 *         kOval_Type,                      //!< non-zero width and height filled with radii
 *         kSimple_Type,                    //!< non-zero width and height with equal radii
 *         kNinePatch_Type,                 //!< non-zero width and height with axis-aligned radii
 *         kComplex_Type,                   //!< non-zero width and height with arbitrary radii
 *         kLastType       = kComplex_Type, //!< largest Type value
 *     };
 *
 *     Type getType() const {
 *         SkASSERT(this->isValid());
 *         return static_cast<Type>(fType);
 *     }
 *
 *     Type type() const { return this->getType(); }
 *
 *     inline bool isEmpty() const { return kEmpty_Type == this->getType(); }
 *     inline bool isRect() const { return kRect_Type == this->getType(); }
 *     inline bool isOval() const { return kOval_Type == this->getType(); }
 *     inline bool isSimple() const { return kSimple_Type == this->getType(); }
 *     inline bool isNinePatch() const { return kNinePatch_Type == this->getType(); }
 *     inline bool isComplex() const { return kComplex_Type == this->getType(); }
 *
 *     /** Returns span on the x-axis. This does not check if result fits in 32-bit float;
 *         result may be infinity.
 *
 *         @return  rect().fRight minus rect().fLeft
 *     */
 *     SkScalar width() const { return fRect.width(); }
 *
 *     /** Returns span on the y-axis. This does not check if result fits in 32-bit float;
 *         result may be infinity.
 *
 *         @return  rect().fBottom minus rect().fTop
 *     */
 *     SkScalar height() const { return fRect.height(); }
 *
 *     /** Returns top-left corner radii. If type() returns kEmpty_Type, kRect_Type,
 *         kOval_Type, or kSimple_Type, returns a value representative of all corner radii.
 *         If type() returns kNinePatch_Type or kComplex_Type, at least one of the
 *         remaining three corners has a different value.
 *
 *         @return  corner radii for simple types
 *     */
 *     SkVector getSimpleRadii() const {
 *         return fRadii[0];
 *     }
 *
 *     /** Sets bounds to zero width and height at (0, 0), the origin. Sets
 *         corner radii to zero and sets type to kEmpty_Type.
 *     */
 *     void setEmpty() { *this = SkRRect(); }
 *
 *     /** Sets bounds to sorted rect, and sets corner radii to zero.
 *         If set bounds has width and height, and sets type to kRect_Type;
 *         otherwise, sets type to kEmpty_Type.
 *
 *         @param rect  bounds to set
 *     */
 *     void setRect(const SkRect& rect) {
 *         if (!this->initializeRect(rect)) {
 *             return;
 *         }
 *
 *         memset(fRadii, 0, sizeof(fRadii));
 *         fType = kRect_Type;
 *
 *         SkASSERT(this->isValid());
 *     }
 *
 *     /** Initializes bounds at (0, 0), the origin, with zero width and height.
 *         Initializes corner radii to (0, 0), and sets type of kEmpty_Type.
 *
 *         @return  empty SkRRect
 *     */
 *     static SkRRect MakeEmpty() { return SkRRect(); }
 *
 *     /** Initializes to copy of r bounds and zeroes corner radii.
 *
 *         @param r  bounds to copy
 *         @return   copy of r
 *     */
 *     static SkRRect MakeRect(const SkRect& r) {
 *         SkRRect rr;
 *         rr.setRect(r);
 *         return rr;
 *     }
 *
 *     /** Initializes to oval, x-axis radii to half oval.width(), and all y-axis radii
 *         to half oval.height(). If oval bounds is empty, sets to kEmpty_Type.
 *         Otherwise, sets to kOval_Type.
 *
 *         @param oval  bounds of oval
 *         @return      oval
 *     */
 *     static SkRRect MakeOval(const SkRect& oval) {
 *         SkRRect rr;
 *         rr.setOval(oval);
 *         return rr;
 *     }
 *
 *     /** Initializes to rounded rectangle with the same radii for all four corners.
 *         If rect is empty, sets to kEmpty_Type.
 *         Otherwise, if xRad and yRad are zero, sets to kRect_Type.
 *         Otherwise, if xRad is at least half rect.width() and yRad is at least half
 *         rect.height(), sets to kOval_Type.
 *         Otherwise, sets to kSimple_Type.
 *
 *         @param rect  bounds of rounded rectangle
 *         @param xRad  x-axis radius of corners
 *         @param yRad  y-axis radius of corners
 *         @return      rounded rectangle
 *     */
 *     static SkRRect MakeRectXY(const SkRect& rect, SkScalar xRad, SkScalar yRad) {
 *         SkRRect rr;
 *         rr.setRectXY(rect, xRad, yRad);
 *         return rr;
 *     }
 *
 *     /** Initializes to rounded rectangle with a radii array for individual control
 *         of all four corners.
 *
 *         If rect is empty, sets to kEmpty_Type.
 *         Otherwise, if one of each corner radii are zero, sets to kRect_Type.
 *         Otherwise, if all x-axis radii are equal and at least half rect.width(), and
 *         all y-axis radii are equal at least half rect.height(), sets to kOval_Type.
 *         Otherwise, if all x-axis radii are equal, and all y-axis radii are equal,
 *         sets to kSimple_Type. Otherwise, sets to kNinePatch_Type.
 *
 *         @param rect   bounds of rounded rectangle
 *         @param radii  corner x-axis and y-axis radii
 *
 *         example: https://fiddle.skia.org/c/@RRect_setRectRadii
 *     */
 *     static SkRRect MakeRectRadii(const SkRect& rect, const SkVector radii[4]) {
 *         SkRRect rr;
 *         rr.setRectRadii(rect, radii);
 *         return rr;
 *     }
 *
 *     /** Sets bounds to oval, x-axis radii to half oval.width(), and all y-axis radii
 *         to half oval.height(). If oval bounds is empty, sets to kEmpty_Type.
 *         Otherwise, sets to kOval_Type.
 *
 *         @param oval  bounds of oval
 *     */
 *     void setOval(const SkRect& oval);
 *
 *     /** Sets to rounded rectangle with the same radii for all four corners.
 *         If rect is empty, sets to kEmpty_Type.
 *         Otherwise, if xRad or yRad is zero, sets to kRect_Type.
 *         Otherwise, if xRad is at least half rect.width() and yRad is at least half
 *         rect.height(), sets to kOval_Type.
 *         Otherwise, sets to kSimple_Type.
 *
 *         @param rect  bounds of rounded rectangle
 *         @param xRad  x-axis radius of corners
 *         @param yRad  y-axis radius of corners
 *
 *         example: https://fiddle.skia.org/c/@RRect_setRectXY
 *     */
 *     void setRectXY(const SkRect& rect, SkScalar xRad, SkScalar yRad);
 *
 *     /** Sets bounds to rect. Sets radii to (leftRad, topRad), (rightRad, topRad),
 *         (rightRad, bottomRad), (leftRad, bottomRad).
 *
 *         If rect is empty, sets to kEmpty_Type.
 *         Otherwise, if leftRad and rightRad are zero, sets to kRect_Type.
 *         Otherwise, if topRad and bottomRad are zero, sets to kRect_Type.
 *         Otherwise, if leftRad and rightRad are equal and at least half rect.width(), and
 *         topRad and bottomRad are equal at least half rect.height(), sets to kOval_Type.
 *         Otherwise, if leftRad and rightRad are equal, and topRad and bottomRad are equal,
 *         sets to kSimple_Type. Otherwise, sets to kNinePatch_Type.
 *
 *         Nine patch refers to the nine parts defined by the radii: one center rectangle,
 *         four edge patches, and four corner patches.
 *
 *         @param rect       bounds of rounded rectangle
 *         @param leftRad    left-top and left-bottom x-axis radius
 *         @param topRad     left-top and right-top y-axis radius
 *         @param rightRad   right-top and right-bottom x-axis radius
 *         @param bottomRad  left-bottom and right-bottom y-axis radius
 *     */
 *     void setNinePatch(const SkRect& rect, SkScalar leftRad, SkScalar topRad,
 *                       SkScalar rightRad, SkScalar bottomRad);
 *
 *     /** Sets bounds to rect. Sets radii array for individual control of all four corners.
 *
 *         If rect is empty, sets to kEmpty_Type.
 *         Otherwise, if one of each corner radii are zero, sets to kRect_Type.
 *         Otherwise, if all x-axis radii are equal and at least half rect.width(), and
 *         all y-axis radii are equal at least half rect.height(), sets to kOval_Type.
 *         Otherwise, if all x-axis radii are equal, and all y-axis radii are equal,
 *         sets to kSimple_Type. Otherwise, sets to kNinePatch_Type.
 *
 *         @param rect   bounds of rounded rectangle
 *         @param radii  corner x-axis and y-axis radii
 *
 *         example: https://fiddle.skia.org/c/@RRect_setRectRadii
 *     */
 *     void setRectRadii(const SkRect& rect, const SkVector radii[4]);
 *
 *     /** \enum SkRRect::Corner
 *         The radii are stored: top-left, top-right, bottom-right, bottom-left.
 *     */
 *     enum Corner {
 *         kUpperLeft_Corner,  //!< index of top-left corner radii
 *         kUpperRight_Corner, //!< index of top-right corner radii
 *         kLowerRight_Corner, //!< index of bottom-right corner radii
 *         kLowerLeft_Corner,  //!< index of bottom-left corner radii
 *     };
 *
 *     /** Returns bounds. Bounds may have zero width or zero height. Bounds right is
 *         greater than or equal to left; bounds bottom is greater than or equal to top.
 *         Result is identical to getBounds().
 *
 *         @return  bounding box
 *     */
 *     const SkRect& rect() const { return fRect; }
 *
 *     /** Returns scalar pair for radius of curve on x-axis and y-axis for one corner.
 *         Both radii may be zero. If not zero, both are positive and finite.
 *
 *         @return        x-axis and y-axis radii for one corner
 *     */
 *     SkVector radii(Corner corner) const { return fRadii[corner]; }
 *     /**
 *      * Returns the corner radii for all four corners, in the same order as `Corner`.
 *      */
 *     SkSpan<const SkVector> radii() const { return SkSpan(fRadii, 4); }
 *
 *     /** Returns bounds. Bounds may have zero width or zero height. Bounds right is
 *         greater than or equal to left; bounds bottom is greater than or equal to top.
 *         Result is identical to rect().
 *
 *         @return  bounding box
 *     */
 *     const SkRect& getBounds() const { return fRect; }
 *
 *     /** Returns true if bounds and radii in a are equal to bounds and radii in b.
 *
 *         a and b are not equal if either contain NaN. a and b are equal if members
 *         contain zeroes with different signs.
 *
 *         @param a  SkRect bounds and radii to compare
 *         @param b  SkRect bounds and radii to compare
 *         @return   true if members are equal
 *     */
 *     friend bool operator==(const SkRRect& a, const SkRRect& b) {
 *         return a.fRect == b.fRect && SkScalarsEqual(&a.fRadii[0].fX, &b.fRadii[0].fX, 8);
 *     }
 *
 *     /** Returns true if bounds and radii in a are not equal to bounds and radii in b.
 *
 *         a and b are not equal if either contain NaN. a and b are equal if members
 *         contain zeroes with different signs.
 *
 *         @param a  SkRect bounds and radii to compare
 *         @param b  SkRect bounds and radii to compare
 *         @return   true if members are not equal
 *     */
 *     friend bool operator!=(const SkRRect& a, const SkRRect& b) {
 *         return a.fRect != b.fRect || !SkScalarsEqual(&a.fRadii[0].fX, &b.fRadii[0].fX, 8);
 *     }
 *
 *     /** Copies SkRRect to dst, then insets dst bounds by dx and dy, and adjusts dst
 *         radii by dx and dy. dx and dy may be positive, negative, or zero. dst may be
 *         SkRRect.
 *
 *         If either corner radius is zero, the corner has no curvature and is unchanged.
 *         Otherwise, if adjusted radius becomes negative, pins radius to zero.
 *         If dx exceeds half dst bounds width, dst bounds left and right are set to
 *         bounds x-axis center. If dy exceeds half dst bounds height, dst bounds top and
 *         bottom are set to bounds y-axis center.
 *
 *         If dx or dy cause the bounds to become infinite, dst bounds is zeroed.
 *
 *         @param dx   added to rect().fLeft, and subtracted from rect().fRight
 *         @param dy   added to rect().fTop, and subtracted from rect().fBottom
 *         @param dst  insets bounds and radii
 *
 *         example: https://fiddle.skia.org/c/@RRect_inset
 *     */
 *     void inset(SkScalar dx, SkScalar dy, SkRRect* dst) const;
 *
 *     /** Insets bounds by dx and dy, and adjusts radii by dx and dy. dx and dy may be
 *         positive, negative, or zero.
 *
 *         If either corner radius is zero, the corner has no curvature and is unchanged.
 *         Otherwise, if adjusted radius becomes negative, pins radius to zero.
 *         If dx exceeds half bounds width, bounds left and right are set to
 *         bounds x-axis center. If dy exceeds half bounds height, bounds top and
 *         bottom are set to bounds y-axis center.
 *
 *         If dx or dy cause the bounds to become infinite, bounds is zeroed.
 *
 *         @param dx  added to rect().fLeft, and subtracted from rect().fRight
 *         @param dy  added to rect().fTop, and subtracted from rect().fBottom
 *     */
 *     void inset(SkScalar dx, SkScalar dy) {
 *         this->inset(dx, dy, this);
 *     }
 *
 *     /** Outsets dst bounds by dx and dy, and adjusts radii by dx and dy. dx and dy may be
 *         positive, negative, or zero.
 *
 *         If either corner radius is zero, the corner has no curvature and is unchanged.
 *         Otherwise, if adjusted radius becomes negative, pins radius to zero.
 *         If dx exceeds half dst bounds width, dst bounds left and right are set to
 *         bounds x-axis center. If dy exceeds half dst bounds height, dst bounds top and
 *         bottom are set to bounds y-axis center.
 *
 *         If dx or dy cause the bounds to become infinite, dst bounds is zeroed.
 *
 *         @param dx   subtracted from rect().fLeft, and added to rect().fRight
 *         @param dy   subtracted from rect().fTop, and added to rect().fBottom
 *         @param dst  outset bounds and radii
 *     */
 *     void outset(SkScalar dx, SkScalar dy, SkRRect* dst) const {
 *         this->inset(-dx, -dy, dst);
 *     }
 *
 *     /** Outsets bounds by dx and dy, and adjusts radii by dx and dy. dx and dy may be
 *         positive, negative, or zero.
 *
 *         If either corner radius is zero, the corner has no curvature and is unchanged.
 *         Otherwise, if adjusted radius becomes negative, pins radius to zero.
 *         If dx exceeds half bounds width, bounds left and right are set to
 *         bounds x-axis center. If dy exceeds half bounds height, bounds top and
 *         bottom are set to bounds y-axis center.
 *
 *         If dx or dy cause the bounds to become infinite, bounds is zeroed.
 *
 *         @param dx  subtracted from rect().fLeft, and added to rect().fRight
 *         @param dy  subtracted from rect().fTop, and added to rect().fBottom
 *     */
 *     void outset(SkScalar dx, SkScalar dy) {
 *         this->inset(-dx, -dy, this);
 *     }
 *
 *     /** Translates SkRRect by (dx, dy).
 *
 *         @param dx  offset added to rect().fLeft and rect().fRight
 *         @param dy  offset added to rect().fTop and rect().fBottom
 *     */
 *     void offset(SkScalar dx, SkScalar dy) {
 *         fRect.offset(dx, dy);
 *     }
 *
 *     /** Returns SkRRect translated by (dx, dy).
 *
 *         @param dx  offset added to rect().fLeft and rect().fRight
 *         @param dy  offset added to rect().fTop and rect().fBottom
 *         @return    SkRRect bounds offset by (dx, dy), with unchanged corner radii
 *     */
 *     [[nodiscard]] SkRRect makeOffset(SkScalar dx, SkScalar dy) const {
 *         return SkRRect(fRect.makeOffset(dx, dy), fRadii, fType);
 *     }
 *
 *     /** Returns true if rect is inside the bounds and corner radii, and if
 *         SkRRect and rect are not empty.
 *
 *         @param rect  area tested for containment
 *         @return      true if SkRRect contains rect
 *
 *         example: https://fiddle.skia.org/c/@RRect_contains
 *     */
 *     bool contains(const SkRect& rect) const;
 *
 *     /** Returns true if bounds and radii values are finite and describe a SkRRect
 *         SkRRect::Type that matches getType(). All SkRRect methods construct valid types,
 *         even if the input values are not valid. Invalid SkRRect data can only
 *         be generated by corrupting memory.
 *
 *         @return  true if bounds and radii match type()
 *
 *         example: https://fiddle.skia.org/c/@RRect_isValid
 *     */
 *     bool isValid() const;
 *
 *     static constexpr size_t kSizeInMemory = 12 * sizeof(SkScalar);
 *
 *     /** Writes SkRRect to buffer. Writes kSizeInMemory bytes, and returns
 *         kSizeInMemory, the number of bytes written.
 *
 *         @param buffer  storage for SkRRect
 *         @return        bytes written, kSizeInMemory
 *
 *         example: https://fiddle.skia.org/c/@RRect_writeToMemory
 *     */
 *     size_t writeToMemory(void* buffer) const;
 *
 *     /** Reads SkRRect from buffer, reading kSizeInMemory bytes.
 *         Returns kSizeInMemory, bytes read if length is at least kSizeInMemory.
 *         Otherwise, returns zero.
 *
 *         @param buffer  memory to read from
 *         @param length  size of buffer
 *         @return        bytes read, or 0 if length is less than kSizeInMemory
 *
 *         example: https://fiddle.skia.org/c/@RRect_readFromMemory
 *     */
 *     size_t readFromMemory(const void* buffer, size_t length);
 *
 *     /** Transforms by SkRRect by matrix and return it if possible.
 *      *  If the matrix does not preserve axis-alignment (e.g. rotates, skews, etc.)
 *      *  then this returns {}.
 *      */
 *     std::optional<SkRRect> transform(const SkMatrix&) const;
 *
 *     // Deprecated: use optional form
 *     bool transform(const SkMatrix& matrix, SkRRect* dst) const;
 *
 *     /** Writes text representation of SkRRect to standard output.
 *         Set asHex true to generate exact binary representations
 *         of floating point numbers.
 *
 *         @param asHex  true if SkScalar values are written as hexadecimal
 *
 *         example: https://fiddle.skia.org/c/@RRect_dump
 *     */
 *     void dump(bool asHex) const;
 *     SkString dumpToString(bool asHex) const;
 *
 *     /** Writes text representation of SkRRect to standard output. The representation
 *         may be directly compiled as C++ code. Floating point values are written
 *         with limited precision; it may not be possible to reconstruct original
 *         SkRRect from output.
 *     */
 *     void dump() const { this->dump(false); }
 *
 *     /** Writes text representation of SkRRect to standard output. The representation
 *         may be directly compiled as C++ code. Floating point values are written
 *         in hexadecimal to preserve their exact bit pattern. The output reconstructs the
 *         original SkRRect.
 *     */
 *     void dumpHex() const { this->dump(true); }
 *
 * private:
 *     static bool AreRectAndRadiiValid(const SkRect&, const SkVector[4]);
 *
 *     SkRRect(const SkRect& rect, const SkVector radii[4], int32_t type)
 *         : fRect(rect)
 *         , fRadii{radii[0], radii[1], radii[2], radii[3]}
 *         , fType(type) {}
 *
 *     /**
 *      * Initializes fRect. If the passed in rect is not finite or empty the rrect will be fully
 *      * initialized and false is returned. Otherwise, just fRect is initialized and true is returned.
 *      */
 *     bool initializeRect(const SkRect&);
 *
 *     void computeType();
 *     bool checkCornerContainment(SkScalar x, SkScalar y) const;
 *     // Returns true if the radii had to be scaled to fit rect
 *     bool scaleRadii();
 *
 *     SkRect fRect = SkRect::MakeEmpty();
 *     // Radii order is UL, UR, LR, LL. Use Corner enum to index into fRadii[]
 *     SkVector fRadii[4] = {{0, 0}, {0, 0}, {0,0}, {0,0}};
 *     // use an explicitly sized type so we're sure the class is dense (no uninitialized bytes)
 *     int32_t fType = kEmpty_Type;
 *
 *     // to access fRadii directly
 *     friend class SkPath;
 *     friend class SkRRectPriv;
 * }
 * ```
 */
public data class SkRRect public constructor(
  /**
   * C++ original:
   * ```cpp
   * static constexpr size_t kSizeInMemory
   * ```
   */
  private var fRect: Int,
  /**
   * C++ original:
   * ```cpp
   * SkRect fRect
   * ```
   */
  private var fRadii: IntArray,
  /**
   * C++ original:
   * ```cpp
   * SkVector fRadii[4]
   * ```
   */
  private var fType: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * SkRRect& operator=(const SkRRect& rrect) = default
   * ```
   */
  public fun assign(rrect: SkRRect) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * Type getType() const {
   *         SkASSERT(this->isValid());
   *         return static_cast<Type>(fType);
   *     }
   * ```
   */
  public fun getType(): Type {
    TODO("Implement getType")
  }

  /**
   * C++ original:
   * ```cpp
   * Type type() const { return this->getType(); }
   * ```
   */
  public fun type(): Type {
    TODO("Implement type")
  }

  /**
   * C++ original:
   * ```cpp
   * inline bool isEmpty() const { return kEmpty_Type == this->getType(); }
   * ```
   */
  public fun isEmpty(): Boolean {
    TODO("Implement isEmpty")
  }

  /**
   * C++ original:
   * ```cpp
   * inline bool isRect() const { return kRect_Type == this->getType(); }
   * ```
   */
  public fun isRect(): Boolean {
    TODO("Implement isRect")
  }

  /**
   * C++ original:
   * ```cpp
   * inline bool isOval() const { return kOval_Type == this->getType(); }
   * ```
   */
  public fun isOval(): Boolean {
    TODO("Implement isOval")
  }

  /**
   * C++ original:
   * ```cpp
   * inline bool isSimple() const { return kSimple_Type == this->getType(); }
   * ```
   */
  public fun isSimple(): Boolean {
    TODO("Implement isSimple")
  }

  /**
   * C++ original:
   * ```cpp
   * inline bool isNinePatch() const { return kNinePatch_Type == this->getType(); }
   * ```
   */
  public fun isNinePatch(): Boolean {
    TODO("Implement isNinePatch")
  }

  /**
   * C++ original:
   * ```cpp
   * inline bool isComplex() const { return kComplex_Type == this->getType(); }
   * ```
   */
  public fun isComplex(): Boolean {
    TODO("Implement isComplex")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar width() const { return fRect.width(); }
   * ```
   */
  public fun width(): Int {
    TODO("Implement width")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar height() const { return fRect.height(); }
   * ```
   */
  public fun height(): Int {
    TODO("Implement height")
  }

  /**
   * C++ original:
   * ```cpp
   * SkVector getSimpleRadii() const {
   *         return fRadii[0];
   *     }
   * ```
   */
  public fun getSimpleRadii(): Int {
    TODO("Implement getSimpleRadii")
  }

  /**
   * C++ original:
   * ```cpp
   * void setEmpty() { *this = SkRRect(); }
   * ```
   */
  public fun setEmpty() {
    TODO("Implement setEmpty")
  }

  /**
   * C++ original:
   * ```cpp
   * void setRect(const SkRect& rect) {
   *         if (!this->initializeRect(rect)) {
   *             return;
   *         }
   *
   *         memset(fRadii, 0, sizeof(fRadii));
   *         fType = kRect_Type;
   *
   *         SkASSERT(this->isValid());
   *     }
   * ```
   */
  public fun setRect(rect: SkRect) {
    TODO("Implement setRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRRect::setOval(const SkRect& oval) {
   *     if (!this->initializeRect(oval)) {
   *         return;
   *     }
   *
   *     SkScalar xRad = SkRectPriv::HalfWidth(fRect);
   *     SkScalar yRad = SkRectPriv::HalfHeight(fRect);
   *
   *     if (xRad == 0.0f || yRad == 0.0f) {
   *         // All the corners will be square
   *         memset(fRadii, 0, sizeof(fRadii));
   *         fType = kRect_Type;
   *     } else {
   *         for (int i = 0; i < 4; ++i) {
   *             fRadii[i].set(xRad, yRad);
   *         }
   *         fType = kOval_Type;
   *     }
   *
   *     SkASSERT(this->isValid());
   * }
   * ```
   */
  public fun setOval(oval: SkRect) {
    TODO("Implement setOval")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRRect::setRectXY(const SkRect& rect, SkScalar xRad, SkScalar yRad) {
   *     if (!this->initializeRect(rect)) {
   *         return;
   *     }
   *
   *     if (!SkIsFinite(xRad, yRad)) {
   *         xRad = yRad = 0;    // devolve into a simple rect
   *     }
   *
   *     if (fRect.width() < xRad+xRad || fRect.height() < yRad+yRad) {
   *         // At most one of these two divides will be by zero, and neither numerator is zero.
   *         SkScalar scale = std::min(sk_ieee_float_divide(fRect. width(), xRad + xRad),
   *                                      sk_ieee_float_divide(fRect.height(), yRad + yRad));
   *         SkASSERT(scale < SK_Scalar1);
   *         xRad *= scale;
   *         yRad *= scale;
   *     }
   *
   *     if (xRad <= 0 || yRad <= 0) {
   *         // all corners are square in this case
   *         this->setRect(rect);
   *         return;
   *     }
   *
   *     for (int i = 0; i < 4; ++i) {
   *         fRadii[i].set(xRad, yRad);
   *     }
   *     fType = kSimple_Type;
   *     if (xRad >= SkScalarHalf(fRect.width()) && yRad >= SkScalarHalf(fRect.height())) {
   *         fType = kOval_Type;
   *         // TODO: assert that all the x&y radii are already W/2 & H/2
   *     }
   *
   *     SkASSERT(this->isValid());
   * }
   * ```
   */
  public fun setRectXY(
    rect: SkRect,
    xRad: SkScalar,
    yRad: SkScalar,
  ) {
    TODO("Implement setRectXY")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRRect::setNinePatch(const SkRect& rect, SkScalar leftRad, SkScalar topRad,
   *                            SkScalar rightRad, SkScalar bottomRad) {
   *     if (!this->initializeRect(rect)) {
   *         return;
   *     }
   *
   *     if (!SkIsFinite(leftRad, topRad, rightRad, bottomRad)) {
   *         this->setRect(rect);    // devolve into a simple rect
   *         return;
   *     }
   *
   *     leftRad = std::max(leftRad, 0.0f);
   *     topRad = std::max(topRad, 0.0f);
   *     rightRad = std::max(rightRad, 0.0f);
   *     bottomRad = std::max(bottomRad, 0.0f);
   *
   *     SkScalar scale = SK_Scalar1;
   *     if (leftRad + rightRad > fRect.width()) {
   *         scale = fRect.width() / (leftRad + rightRad);
   *     }
   *     if (topRad + bottomRad > fRect.height()) {
   *         scale = std::min(scale, fRect.height() / (topRad + bottomRad));
   *     }
   *
   *     if (scale < SK_Scalar1) {
   *         leftRad *= scale;
   *         topRad *= scale;
   *         rightRad *= scale;
   *         bottomRad *= scale;
   *     }
   *
   *     if (leftRad == rightRad && topRad == bottomRad) {
   *         if (leftRad >= SkScalarHalf(fRect.width()) && topRad >= SkScalarHalf(fRect.height())) {
   *             fType = kOval_Type;
   *         } else if (0 == leftRad || 0 == topRad) {
   *             // If the left and (by equality check above) right radii are zero then it is a rect.
   *             // Same goes for top/bottom.
   *             fType = kRect_Type;
   *             leftRad = 0;
   *             topRad = 0;
   *             rightRad = 0;
   *             bottomRad = 0;
   *         } else {
   *             fType = kSimple_Type;
   *         }
   *     } else {
   *         fType = kNinePatch_Type;
   *     }
   *
   *     fRadii[kUpperLeft_Corner].set(leftRad, topRad);
   *     fRadii[kUpperRight_Corner].set(rightRad, topRad);
   *     fRadii[kLowerRight_Corner].set(rightRad, bottomRad);
   *     fRadii[kLowerLeft_Corner].set(leftRad, bottomRad);
   *     if (clamp_to_zero(fRadii)) {
   *         this->setRect(rect);    // devolve into a simple rect
   *         return;
   *     }
   *     if (fType == kNinePatch_Type && !radii_are_nine_patch(fRadii)) {
   *         fType = kComplex_Type;
   *     }
   *
   *     SkASSERT(this->isValid());
   * }
   * ```
   */
  public fun setNinePatch(
    rect: SkRect,
    leftRad: SkScalar,
    topRad: SkScalar,
    rightRad: SkScalar,
    bottomRad: SkScalar,
  ) {
    TODO("Implement setNinePatch")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRRect::setRectRadii(const SkRect& rect, const SkVector radii[4]) {
   *     if (!this->initializeRect(rect)) {
   *         return;
   *     }
   *
   *     if (!SkIsFinite(&radii[0].fX, 8)) {
   *         this->setRect(rect);    // devolve into a simple rect
   *         return;
   *     }
   *
   *     memcpy(fRadii, radii, sizeof(fRadii));
   *
   *     if (clamp_to_zero(fRadii)) {
   *         this->setRect(rect);
   *         return;
   *     }
   *
   *     this->scaleRadii();
   *
   *     if (!this->isValid()) {
   *         this->setRect(rect);
   *         return;
   *     }
   * }
   * ```
   */
  public fun setRectRadii(rect: SkRect, radii: Array<SkVector>) {
    TODO("Implement setRectRadii")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkRect& rect() const { return fRect; }
   * ```
   */
  public fun rect(): Int {
    TODO("Implement rect")
  }

  /**
   * C++ original:
   * ```cpp
   * SkVector radii(Corner corner) const { return fRadii[corner]; }
   * ```
   */
  public fun radii(corner: Corner): Int {
    TODO("Implement radii")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const SkVector> radii() const { return SkSpan(fRadii, 4); }
   * ```
   */
  public fun radii(): Int {
    TODO("Implement radii")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkRect& getBounds() const { return fRect; }
   * ```
   */
  public fun getBounds(): Int {
    TODO("Implement getBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRRect::inset(SkScalar dx, SkScalar dy, SkRRect* dst) const {
   *     SkRect r = fRect.makeInset(dx, dy);
   *     bool degenerate = false;
   *     if (r.fRight <= r.fLeft) {
   *         degenerate = true;
   *         r.fLeft = r.fRight = sk_float_midpoint(r.fLeft, r.fRight);
   *     }
   *     if (r.fBottom <= r.fTop) {
   *         degenerate = true;
   *         r.fTop = r.fBottom = sk_float_midpoint(r.fTop, r.fBottom);
   *     }
   *     if (degenerate) {
   *         dst->fRect = r;
   *         memset(dst->fRadii, 0, sizeof(dst->fRadii));
   *         dst->fType = kEmpty_Type;
   *         return;
   *     }
   *     if (!r.isFinite()) {
   *         *dst = SkRRect();
   *         return;
   *     }
   *
   *     SkVector radii[4];
   *     memcpy(radii, fRadii, sizeof(radii));
   *     for (int i = 0; i < 4; ++i) {
   *         if (radii[i].fX) {
   *             radii[i].fX -= dx;
   *         }
   *         if (radii[i].fY) {
   *             radii[i].fY -= dy;
   *         }
   *     }
   *     dst->setRectRadii(r, radii);
   * }
   * ```
   */
  public fun inset(
    dx: SkScalar,
    dy: SkScalar,
    dst: SkRRect?,
  ) {
    TODO("Implement inset")
  }

  /**
   * C++ original:
   * ```cpp
   * void inset(SkScalar dx, SkScalar dy) {
   *         this->inset(dx, dy, this);
   *     }
   * ```
   */
  public fun inset(dx: SkScalar, dy: SkScalar) {
    TODO("Implement inset")
  }

  /**
   * C++ original:
   * ```cpp
   * void outset(SkScalar dx, SkScalar dy, SkRRect* dst) const {
   *         this->inset(-dx, -dy, dst);
   *     }
   * ```
   */
  public fun outset(
    dx: SkScalar,
    dy: SkScalar,
    dst: SkRRect?,
  ) {
    TODO("Implement outset")
  }

  /**
   * C++ original:
   * ```cpp
   * void outset(SkScalar dx, SkScalar dy) {
   *         this->inset(-dx, -dy, this);
   *     }
   * ```
   */
  public fun outset(dx: SkScalar, dy: SkScalar) {
    TODO("Implement outset")
  }

  /**
   * C++ original:
   * ```cpp
   * void offset(SkScalar dx, SkScalar dy) {
   *         fRect.offset(dx, dy);
   *     }
   * ```
   */
  public fun offset(dx: SkScalar, dy: SkScalar) {
    TODO("Implement offset")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRRect makeOffset(SkScalar dx, SkScalar dy) const {
   *         return SkRRect(fRect.makeOffset(dx, dy), fRadii, fType);
   *     }
   * ```
   */
  public fun makeOffset(dx: SkScalar, dy: SkScalar): SkRRect {
    TODO("Implement makeOffset")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkRRect::contains(const SkRect& rect) const {
   *     if (!this->getBounds().contains(rect)) {
   *         // If 'rect' isn't contained by the RR's bounds then the
   *         // RR definitely doesn't contain it
   *         return false;
   *     }
   *
   *     if (this->isRect()) {
   *         // the prior test was sufficient
   *         return true;
   *     }
   *
   *     // At this point we know all four corners of 'rect' are inside the
   *     // bounds of of this RR. Check to make sure all the corners are inside
   *     // all the curves
   *     return this->checkCornerContainment(rect.fLeft, rect.fTop) &&
   *            this->checkCornerContainment(rect.fRight, rect.fTop) &&
   *            this->checkCornerContainment(rect.fRight, rect.fBottom) &&
   *            this->checkCornerContainment(rect.fLeft, rect.fBottom);
   * }
   * ```
   */
  public fun contains(rect: SkRect): Boolean {
    TODO("Implement contains")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkRRect::isValid() const {
   *     if (!AreRectAndRadiiValid(fRect, fRadii)) {
   *         return false;
   *     }
   *
   *     bool allRadiiZero = (0 == fRadii[0].fX && 0 == fRadii[0].fY);
   *     bool allCornersSquare = (0 == fRadii[0].fX || 0 == fRadii[0].fY);
   *     bool allRadiiSame = true;
   *
   *     for (int i = 1; i < 4; ++i) {
   *         if (0 != fRadii[i].fX || 0 != fRadii[i].fY) {
   *             allRadiiZero = false;
   *         }
   *
   *         if (fRadii[i].fX != fRadii[i-1].fX || fRadii[i].fY != fRadii[i-1].fY) {
   *             allRadiiSame = false;
   *         }
   *
   *         if (0 != fRadii[i].fX && 0 != fRadii[i].fY) {
   *             allCornersSquare = false;
   *         }
   *     }
   *     bool patchesOfNine = radii_are_nine_patch(fRadii);
   *
   *     if (fType < 0 || fType > kLastType) {
   *         return false;
   *     }
   *
   *     switch (fType) {
   *         case kEmpty_Type:
   *             if (!fRect.isEmpty() || !allRadiiZero || !allRadiiSame || !allCornersSquare) {
   *                 return false;
   *             }
   *             break;
   *         case kRect_Type:
   *             if (fRect.isEmpty() || !allRadiiZero || !allRadiiSame || !allCornersSquare) {
   *                 return false;
   *             }
   *             break;
   *         case kOval_Type:
   *             if (fRect.isEmpty() || allRadiiZero || !allRadiiSame || allCornersSquare) {
   *                 return false;
   *             }
   *
   *             for (int i = 0; i < 4; ++i) {
   *                 if (!SkScalarNearlyEqual(fRadii[i].fX, SkRectPriv::HalfWidth(fRect)) ||
   *                     !SkScalarNearlyEqual(fRadii[i].fY, SkRectPriv::HalfHeight(fRect))) {
   *                     return false;
   *                 }
   *             }
   *             break;
   *         case kSimple_Type:
   *             if (fRect.isEmpty() || allRadiiZero || !allRadiiSame || allCornersSquare) {
   *                 return false;
   *             }
   *             break;
   *         case kNinePatch_Type:
   *             if (fRect.isEmpty() || allRadiiZero || allRadiiSame || allCornersSquare ||
   *                 !patchesOfNine) {
   *                 return false;
   *             }
   *             break;
   *         case kComplex_Type:
   *             if (fRect.isEmpty() || allRadiiZero || allRadiiSame || allCornersSquare ||
   *                 patchesOfNine) {
   *                 return false;
   *             }
   *             break;
   *     }
   *
   *     return true;
   * }
   * ```
   */
  public fun isValid(): Boolean {
    TODO("Implement isValid")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkRRect::writeToMemory(void* buffer) const {
   *     // Serialize only the rect and corners, but not the derived type tag.
   *     memcpy(buffer, this, kSizeInMemory);
   *     return kSizeInMemory;
   * }
   * ```
   */
  public fun writeToMemory(buffer: Unit?): ULong {
    TODO("Implement writeToMemory")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkRRect::readFromMemory(const void* buffer, size_t length) {
   *     if (length < kSizeInMemory) {
   *         return 0;
   *     }
   *
   *     // The extra (void*) tells GCC not to worry that kSizeInMemory < sizeof(SkRRect).
   *
   *     SkRRect raw;
   *     memcpy((void*)&raw, buffer, kSizeInMemory);
   *     this->setRectRadii(raw.fRect, raw.fRadii);
   *     return kSizeInMemory;
   * }
   * ```
   */
  public fun readFromMemory(buffer: Unit?, length: ULong): ULong {
    TODO("Implement readFromMemory")
  }

  /**
   * C++ original:
   * ```cpp
   * std::optional<SkRRect> SkRRect::transform(const SkMatrix& matrix) const {
   * // TODO(b/441005851): Resolve Android RenderEngine's shader prewarming regressions before removing.
   * #ifdef SK_SUPPORT_LEGACY_RRECT_TRANSFORM
   *     SkRRect newrr;
   *     if (this->transform(matrix, &newrr)) {
   *         return newrr;
   *     }
   *     return {};
   * #else
   *     if (matrix.isIdentity()) {
   *         return *this;
   *     }
   *
   *     if (!matrix.preservesAxisAlignment()) {
   *         return {};
   *     }
   *
   *     const SkRect newRect = matrix.mapRect(fRect);
   *     if (!newRect.isFinite()) {
   *         return {};
   *     }
   *
   *     switch (this->getType()) {
   *         case kEmpty_Type: return MakeEmpty();
   *         case kRect_Type:  return MakeRect(newRect);
   *         case kOval_Type:  return MakeOval(newRect);
   *         default:
   *             break;
   *     }
   *
   *     SkPathRawShapes::RRect raw(*this);
   *     matrix.mapPoints(raw.fStorage);
   *     return SkPathPriv::DeduceRRectFromContour(newRect, raw.fPoints, raw.fVerbs);
   * #endif
   * }
   * ```
   */
  public fun transform(matrix: SkMatrix): SkRRect? {
    TODO("Implement transform")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkRRect::transform(const SkMatrix& matrix, SkRRect* dst) const {
   * // TODO(b/441005851): Resolve Android RenderEngine's shader prewarming regressions before removing.
   * #ifdef SK_SUPPORT_LEGACY_RRECT_TRANSFORM
   *     if (nullptr == dst) {
   *         return false;
   *     }
   *
   *     // Assert that the caller is not trying to do this in place, which
   *     // would violate const-ness. Do not return false though, so that
   *     // if they know what they're doing and want to violate it they can.
   *     SkASSERT(dst != this);
   *
   *     if (matrix.isIdentity()) {
   *         *dst = *this;
   *         return true;
   *     }
   *
   *     if (!matrix.preservesAxisAlignment()) {
   *         return false;
   *     }
   *
   *     SkRect newRect;
   *     if (!matrix.mapRect(&newRect, fRect)) {
   *         return false;
   *     }
   *
   *     // The matrix may have scaled us to zero (or due to float madness, we now have collapsed
   *     // some dimension of the rect, so we need to check for that. Note that matrix must be
   *     // scale and translate and mapRect() produces a sorted rect. So an empty rect indicates
   *     // loss of precision.
   *     if (!newRect.isFinite() || newRect.isEmpty()) {
   *         return false;
   *     }
   *
   *     // At this point, this is guaranteed to succeed, so we can modify dst.
   *     dst->fRect = newRect;
   *
   *     // Since the only transforms that were allowed are axis aligned, the type
   *     // remains unchanged.
   *     dst->fType = fType;
   *
   *     if (kRect_Type == fType) {
   *         SkASSERT(dst->isValid());
   *         return true;
   *     }
   *     if (kOval_Type == fType) {
   *         for (int i = 0; i < 4; ++i) {
   *             dst->fRadii[i].fX = SkScalarHalf(newRect.width());
   *             dst->fRadii[i].fY = SkScalarHalf(newRect.height());
   *         }
   *         SkASSERT(dst->isValid());
   *         return true;
   *     }
   *
   *     // Now scale each corner
   *     SkScalar xScale = matrix.getScaleX();
   *     SkScalar yScale = matrix.getScaleY();
   *
   *     // There is a rotation of 90 (Clockwise 90) or 270 (Counter clockwise 90).
   *     // 180 degrees rotations are simply flipX with a flipY and would come under
   *     // a scale transform.
   *     if (!matrix.isScaleTranslate()) {
   *         // If we got here, the matrix preserves axis alignment (earlier return check) but isn't
   *         // a regular scale matrix. To confirm that it's a 90/270 rotation, the scale components are
   *         // 0s and the skew components are non-zero (+/-1 if there is no other scale factor).
   *         SkASSERT(matrix.getScaleX() == 0.f && matrix.getScaleY() == 0.f &&
   *                  matrix.getSkewX() != 0.f && matrix.getSkewY() != 0.f);
   *         const bool isClockwise = matrix.getSkewX() < 0;
   *
   *         // The matrix location for scale changes if there is a rotation.
   *         // xScale and yScale represent scales applied to the dst radii, so we store the src x scale
   *         // in yScale and vice versa.
   *         yScale = matrix.getSkewY() * (isClockwise ? 1 : -1);
   *         xScale = matrix.getSkewX() * (isClockwise ? -1 : 1);
   *
   *         const int dir = isClockwise ? 3 : 1;
   *         for (int i = 0; i < 4; ++i) {
   *             const int src = (i + dir) >= 4 ? (i + dir) % 4 : (i + dir);
   *             // Swap X and Y axis for the radii.
   *             dst->fRadii[i].fX = fRadii[src].fY;
   *             dst->fRadii[i].fY = fRadii[src].fX;
   *         }
   *     } else {
   *         for (int i = 0; i < 4; ++i) {
   *             dst->fRadii[i].fX = fRadii[i].fX;
   *             dst->fRadii[i].fY = fRadii[i].fY;
   *         }
   *     }
   *
   *     const bool flipX = xScale < 0;
   *     if (flipX) {
   *         xScale = -xScale;
   *     }
   *
   *     const bool flipY = yScale < 0;
   *     if (flipY) {
   *         yScale = -yScale;
   *     }
   *
   *     // Scale the radii without respecting the flip.
   *     for (int i = 0; i < 4; ++i) {
   *         dst->fRadii[i].fX *= xScale;
   *         dst->fRadii[i].fY *= yScale;
   *     }
   *
   *     // Now swap as necessary.
   *     using std::swap;
   *     if (flipX) {
   *         if (flipY) {
   *             // Swap with opposite corners
   *             swap(dst->fRadii[kUpperLeft_Corner], dst->fRadii[kLowerRight_Corner]);
   *             swap(dst->fRadii[kUpperRight_Corner], dst->fRadii[kLowerLeft_Corner]);
   *         } else {
   *             // Only swap in x
   *             swap(dst->fRadii[kUpperRight_Corner], dst->fRadii[kUpperLeft_Corner]);
   *             swap(dst->fRadii[kLowerRight_Corner], dst->fRadii[kLowerLeft_Corner]);
   *         }
   *     } else if (flipY) {
   *         // Only swap in y
   *         swap(dst->fRadii[kUpperLeft_Corner], dst->fRadii[kLowerLeft_Corner]);
   *         swap(dst->fRadii[kUpperRight_Corner], dst->fRadii[kLowerRight_Corner]);
   *     }
   *
   *     dst->scaleRadii();
   *
   *     if (!AreRectAndRadiiValid(dst->fRect, dst->fRadii)) {
   *         return false;
   *     }
   *
   *     SkASSERT(dst->isValid());
   *     return true;
   * #else
   *     if (auto rr = this->transform(matrix)) {
   *         if (dst) {
   *             *dst = *rr;
   *         }
   *         return true;
   *     }
   *     return false;
   * #endif
   * }
   * ```
   */
  public fun transform(matrix: SkMatrix, dst: SkRRect?): Boolean {
    TODO("Implement transform")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRRect::dump(bool asHex) const { SkDebugf("%s\n", this->dumpToString(asHex).c_str()); }
   * ```
   */
  public fun dump(asHex: Boolean) {
    TODO("Implement dump")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString SkRRect::dumpToString(bool asHex) const {
   *     SkScalarAsStringType asType = asHex ? kHex_SkScalarAsStringType : kDec_SkScalarAsStringType;
   *
   *     SkString line = fRect.dumpToString(asHex);
   *     line.appendf("\nconst SkPoint corners[] = {\n");
   *     for (int i = 0; i < 4; ++i) {
   *         SkString strX, strY;
   *         SkAppendScalar(&strX, fRadii[i].x(), asType);
   *         SkAppendScalar(&strY, fRadii[i].y(), asType);
   *         line.appendf("    { %s, %s },", strX.c_str(), strY.c_str());
   *         if (asHex) {
   *             line.appendf(" /* %f %f */", fRadii[i].x(), fRadii[i].y());
   *         }
   *         line.append("\n");
   *     }
   *     line.append("};");
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

  /**
   * C++ original:
   * ```cpp
   * bool SkRRect::initializeRect(const SkRect& rect) {
   *     // Check this before sorting because sorting can hide nans.
   *     if (!rect.isFinite()) {
   *         *this = SkRRect();
   *         return false;
   *     }
   *     fRect = rect.makeSorted();
   *     if (fRect.isEmpty()) {
   *         memset(fRadii, 0, sizeof(fRadii));
   *         fType = kEmpty_Type;
   *         return false;
   *     }
   *     return true;
   * }
   * ```
   */
  private fun initializeRect(rect: SkRect): Boolean {
    TODO("Implement initializeRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRRect::computeType() {
   *     if (fRect.isEmpty()) {
   *         SkASSERT(fRect.isSorted());
   *         for (size_t i = 0; i < std::size(fRadii); ++i) {
   *             SkASSERT((fRadii[i] == SkVector{0, 0}));
   *         }
   *         fType = kEmpty_Type;
   *         SkASSERT(this->isValid());
   *         return;
   *     }
   *
   *     bool allRadiiEqual = true; // are all x radii equal and all y radii?
   *     bool allCornersSquare = 0 == fRadii[0].fX || 0 == fRadii[0].fY;
   *
   *     for (int i = 1; i < 4; ++i) {
   *         if (0 != fRadii[i].fX && 0 != fRadii[i].fY) {
   *             // if either radius is zero the corner is square so both have to
   *             // be non-zero to have a rounded corner
   *             allCornersSquare = false;
   *         }
   *         if (fRadii[i].fX != fRadii[i-1].fX || fRadii[i].fY != fRadii[i-1].fY) {
   *             allRadiiEqual = false;
   *         }
   *     }
   *
   *     if (allCornersSquare) {
   *         fType = kRect_Type;
   *         SkASSERT(this->isValid());
   *         return;
   *     }
   *
   *     if (allRadiiEqual) {
   *         if (fRadii[0].fX >= SkScalarHalf(fRect.width()) &&
   *             fRadii[0].fY >= SkScalarHalf(fRect.height())) {
   *             fType = kOval_Type;
   *         } else {
   *             fType = kSimple_Type;
   *         }
   *         SkASSERT(this->isValid());
   *         return;
   *     }
   *
   *     if (radii_are_nine_patch(fRadii)) {
   *         fType = kNinePatch_Type;
   *     } else {
   *         fType = kComplex_Type;
   *     }
   *
   *     if (!this->isValid()) {
   *         this->setRect(this->rect());
   *         SkASSERT(this->isValid());
   *     }
   * }
   * ```
   */
  private fun computeType() {
    TODO("Implement computeType")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkRRect::checkCornerContainment(SkScalar x, SkScalar y) const {
   *     SkPoint canonicalPt; // (x,y) translated to one of the quadrants
   *     int index;
   *
   *     if (kOval_Type == this->type()) {
   *         canonicalPt.set(x - fRect.centerX(), y - fRect.centerY());
   *         index = kUpperLeft_Corner;  // any corner will do in this case
   *     } else {
   *         if (x < fRect.fLeft + fRadii[kUpperLeft_Corner].fX &&
   *             y < fRect.fTop + fRadii[kUpperLeft_Corner].fY) {
   *             // UL corner
   *             index = kUpperLeft_Corner;
   *             canonicalPt.set(x - (fRect.fLeft + fRadii[kUpperLeft_Corner].fX),
   *                             y - (fRect.fTop + fRadii[kUpperLeft_Corner].fY));
   *             SkASSERT(canonicalPt.fX < 0 && canonicalPt.fY < 0);
   *         } else if (x < fRect.fLeft + fRadii[kLowerLeft_Corner].fX &&
   *                    y > fRect.fBottom - fRadii[kLowerLeft_Corner].fY) {
   *             // LL corner
   *             index = kLowerLeft_Corner;
   *             canonicalPt.set(x - (fRect.fLeft + fRadii[kLowerLeft_Corner].fX),
   *                             y - (fRect.fBottom - fRadii[kLowerLeft_Corner].fY));
   *             SkASSERT(canonicalPt.fX < 0 && canonicalPt.fY > 0);
   *         } else if (x > fRect.fRight - fRadii[kUpperRight_Corner].fX &&
   *                    y < fRect.fTop + fRadii[kUpperRight_Corner].fY) {
   *             // UR corner
   *             index = kUpperRight_Corner;
   *             canonicalPt.set(x - (fRect.fRight - fRadii[kUpperRight_Corner].fX),
   *                             y - (fRect.fTop + fRadii[kUpperRight_Corner].fY));
   *             SkASSERT(canonicalPt.fX > 0 && canonicalPt.fY < 0);
   *         } else if (x > fRect.fRight - fRadii[kLowerRight_Corner].fX &&
   *                    y > fRect.fBottom - fRadii[kLowerRight_Corner].fY) {
   *             // LR corner
   *             index = kLowerRight_Corner;
   *             canonicalPt.set(x - (fRect.fRight - fRadii[kLowerRight_Corner].fX),
   *                             y - (fRect.fBottom - fRadii[kLowerRight_Corner].fY));
   *             SkASSERT(canonicalPt.fX > 0 && canonicalPt.fY > 0);
   *         } else {
   *             // not in any of the corners
   *             return true;
   *         }
   *     }
   *
   *     // A point is in an ellipse (in standard position) if:
   *     //      x^2     y^2
   *     //     ----- + ----- <= 1
   *     //      a^2     b^2
   *     // or :
   *     //     b^2*x^2 + a^2*y^2 <= (ab)^2
   *     float dist =  SkScalarSquare(canonicalPt.fX) * SkScalarSquare(fRadii[index].fY) +
   *                   SkScalarSquare(canonicalPt.fY) * SkScalarSquare(fRadii[index].fX);
   *     return dist <= SkScalarSquare(fRadii[index].fX * fRadii[index].fY);
   * }
   * ```
   */
  private fun checkCornerContainment(x: SkScalar, y: SkScalar): Boolean {
    TODO("Implement checkCornerContainment")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkRRect::scaleRadii() {
   *     // Proportionally scale down all radii to fit. Find the minimum ratio
   *     // of a side and the radii on that side (for all four sides) and use
   *     // that to scale down _all_ the radii. This algorithm is from the
   *     // W3 spec (http://www.w3.org/TR/css3-background/) section 5.5 - Overlapping
   *     // Curves:
   *     // "Let f = min(Li/Si), where i is one of { top, right, bottom, left },
   *     //   Si is the sum of the two corresponding radii of the corners on side i,
   *     //   and Ltop = Lbottom = the width of the box,
   *     //   and Lleft = Lright = the height of the box.
   *     // If f < 1, then all corner radii are reduced by multiplying them by f."
   *     double scale = 1.0;
   *
   *     // The sides of the rectangle may be larger than a float.
   *     double width = (double)fRect.fRight - (double)fRect.fLeft;
   *     double height = (double)fRect.fBottom - (double)fRect.fTop;
   *     scale = compute_min_scale(fRadii[0].fX, fRadii[1].fX, width,  scale);
   *     scale = compute_min_scale(fRadii[1].fY, fRadii[2].fY, height, scale);
   *     scale = compute_min_scale(fRadii[2].fX, fRadii[3].fX, width,  scale);
   *     scale = compute_min_scale(fRadii[3].fY, fRadii[0].fY, height, scale);
   *
   *     flush_to_zero(fRadii[0].fX, fRadii[1].fX);
   *     flush_to_zero(fRadii[1].fY, fRadii[2].fY);
   *     flush_to_zero(fRadii[2].fX, fRadii[3].fX);
   *     flush_to_zero(fRadii[3].fY, fRadii[0].fY);
   *
   *     if (scale < 1.0) {
   *         SkScaleToSides::AdjustRadii(width,  scale, &fRadii[0].fX, &fRadii[1].fX);
   *         SkScaleToSides::AdjustRadii(height, scale, &fRadii[1].fY, &fRadii[2].fY);
   *         SkScaleToSides::AdjustRadii(width,  scale, &fRadii[2].fX, &fRadii[3].fX);
   *         SkScaleToSides::AdjustRadii(height, scale, &fRadii[3].fY, &fRadii[0].fY);
   *     }
   *
   *     // adjust radii may set x or y to zero; set companion to zero as well
   *     clamp_to_zero(fRadii);
   *
   *     // May be simple, oval, or complex, or become a rect/empty if the radii adjustment made them 0
   *     this->computeType();
   *
   *     // TODO:  Why can't we assert this here?
   *     //SkASSERT(this->isValid());
   *
   *     return scale < 1.0;
   * }
   * ```
   */
  private fun scaleRadii(): Boolean {
    TODO("Implement scaleRadii")
  }

  public enum class Type {
    kEmpty_Type,
    kRect_Type,
    kOval_Type,
    kSimple_Type,
    kNinePatch_Type,
    kComplex_Type,
    kLastType,
  }

  public enum class Corner {
    kUpperLeft_Corner,
    kUpperRight_Corner,
    kLowerRight_Corner,
    kLowerLeft_Corner,
  }

  public companion object {
    public val kSizeInMemory: ULong = TODO("Initialize kSizeInMemory")

    /**
     * C++ original:
     * ```cpp
     * static SkRRect MakeEmpty() { return SkRRect(); }
     * ```
     */
    public fun makeEmpty(): SkRRect {
      TODO("Implement makeEmpty")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkRRect MakeRect(const SkRect& r) {
     *         SkRRect rr;
     *         rr.setRect(r);
     *         return rr;
     *     }
     * ```
     */
    public fun makeRect(r: SkRect): SkRRect {
      TODO("Implement makeRect")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkRRect MakeOval(const SkRect& oval) {
     *         SkRRect rr;
     *         rr.setOval(oval);
     *         return rr;
     *     }
     * ```
     */
    public fun makeOval(oval: SkRect): SkRRect {
      TODO("Implement makeOval")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkRRect MakeRectXY(const SkRect& rect, SkScalar xRad, SkScalar yRad) {
     *         SkRRect rr;
     *         rr.setRectXY(rect, xRad, yRad);
     *         return rr;
     *     }
     * ```
     */
    public fun makeRectXY(
      rect: SkRect,
      xRad: SkScalar,
      yRad: SkScalar,
    ): SkRRect {
      TODO("Implement makeRectXY")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkRRect MakeRectRadii(const SkRect& rect, const SkVector radii[4]) {
     *         SkRRect rr;
     *         rr.setRectRadii(rect, radii);
     *         return rr;
     *     }
     * ```
     */
    public fun makeRectRadii(rect: SkRect, radii: Array<SkVector>): SkRRect {
      TODO("Implement makeRectRadii")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkRRect::AreRectAndRadiiValid(const SkRect& rect, const SkVector radii[4]) {
     *     if (!rect.isFinite() || !rect.isSorted()) {
     *         return false;
     *     }
     *     for (int i = 0; i < 4; ++i) {
     *         if (!are_radius_check_predicates_valid(radii[i].fX, rect.fLeft, rect.fRight) ||
     *             !are_radius_check_predicates_valid(radii[i].fY, rect.fTop, rect.fBottom)) {
     *             return false;
     *         }
     *     }
     *     return true;
     * }
     * ```
     */
    private fun areRectAndRadiiValid(rect: SkRect, radii: Array<SkVector>): Boolean {
      TODO("Implement areRectAndRadiiValid")
    }
  }
}
