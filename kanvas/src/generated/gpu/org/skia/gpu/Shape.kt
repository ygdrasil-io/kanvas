package org.skia.gpu

import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import kotlin.UInt
import org.skia.core.Float2
import org.skia.core.SkArc
import org.skia.foundation.SkPath
import org.skia.foundation.SkRRect
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.math.SkV2

/**
 * C++ original:
 * ```cpp
 * class Shape {
 * public:
 *     enum class Type : uint8_t {
 *         kEmpty, kLine, kRect, kRRect, kArc, kPath
 *     };
 *     inline static constexpr int kTypeCount = static_cast<int>(Type::kPath) + 1;
 *
 *     // The default tolerance to use for fuzzy geometric comparisons that are already transformed
 *     // into device-space. Distances, containment checks, or equality tests closer than
 *     // kDefaultPixelTolerance (< ~0.004) can be considered perceptibly equivalent. This can be
 *     // tested in a different coordinate space by scaling this constant with Transform::localAARadius
 *     static constexpr float kDefaultPixelTolerance = 0.0039f; // (1.f - 0.001f) / 255.f;
 *
 *     Shape() {}
 *     Shape(const Shape& shape)               { *this = shape; }
 *     Shape(Shape&&) = delete;
 *
 *     Shape(SkPoint p0, SkPoint p1)           { this->setLine(p0, p1); }
 *     Shape(SkV2 p0, SkV2 p1)                 { this->setLine(p0, p1); }
 *     Shape(skvx::float2 p0, skvx::float2 p1) { this->setLine(p0, p1); }
 *     explicit Shape(const Rect& rect)        { this->setRect(rect);   }
 *     explicit Shape(const SkRect& rect)      { this->setRect(rect);   }
 *     explicit Shape(const SkRRect& rrect)    { this->setRRect(rrect); }
 *     explicit Shape(const SkArc& arc)        { this->setArc(arc);     }
 *     explicit Shape(const SkPath& path)      { this->setPath(path);   }
 *
 *     ~Shape() { this->reset(); }
 *
 *     // NOTE: None of the geometry types benefit from move semantics, so we don't bother
 *     // defining a move assignment operator for Shape.
 *     Shape& operator=(Shape&&) = delete;
 *     Shape& operator=(const Shape&);
 *
 *     // Return the type of the data last stored in the Shape, which does not incorporate any possible
 *     // simplifications that could be applied to it (e.g. a degenerate round rect with 0 radius
 *     // corners is kRRect and not kRect).
 *     Type type() const { return fType; }
 *
 *     bool isEmpty() const { return fType == Type::kEmpty; }
 *     bool isLine()  const { return fType == Type::kLine;  }
 *     bool isRect()  const { return fType == Type::kRect;  }
 *     bool isRRect() const { return fType == Type::kRRect; }
 *     bool isArc()   const { return fType == Type::kArc;   }
 *     bool isPath()  const { return fType == Type::kPath;  }
 *
 *     bool isFloodFill() const { return this->isEmpty() && this->inverted(); }
 *
 *     bool isVolatilePath() const {
 *         return fType == Type::kPath && this->path().isVolatile();
 *     }
 *
 *     bool inverted() const {
 *         SkASSERT(fType != Type::kPath || fInverted == fPath.isInverseFillType());
 *         return fInverted;
 *     }
 *
 *     void setInverted(bool inverted) {
 *         if (fType == Type::kPath && inverted != fPath.isInverseFillType()) {
 *             fPath.toggleInverseFillType();
 *         }
 *         fInverted = inverted;
 *     }
 *
 *     SkPathFillType fillType() const {
 *         if (fType == Type::kPath) {
 *             return fPath.getFillType(); // already incorporates invertedness
 *         } else {
 *             return fInverted ? SkPathFillType::kInverseEvenOdd : SkPathFillType::kEvenOdd;
 *         }
 *     }
 *
 *     // True if the given bounding box is completely inside the shape, if it's conservatively treated
 *     // as a filled, closed shape.
 *     bool conservativeContains(const Rect& rect) const;
 *     bool conservativeContains(skvx::float2 point) const;
 *
 *     // True if the underlying shape is known to be convex, assuming no other styles. If 'simpleFill'
 *     // is true, it is assumed the contours will be implicitly closed when drawn or used.
 *     bool convex(bool simpleFill = true) const;
 *
 *     // The bounding box of the shape.
 *     Rect bounds() const;
 *
 *     // Convert the shape into a path that describes the same geometry.
 *     SkPath asPath() const;
 *
 *     // Access the actual geometric description of the shape. May only access the appropriate type
 *     // based on what was last set.
 *     skvx::float2   p0()    const { SkASSERT(this->isLine());  return fRect.topLeft();  }
 *     skvx::float2   p1()    const { SkASSERT(this->isLine());  return fRect.botRight(); }
 *     skvx::float4   line()  const { SkASSERT(this->isLine());  return fRect.ltrb();     }
 *     const Rect&    rect()  const { SkASSERT(this->isRect());  return fRect;            }
 *     const SkRRect& rrect() const { SkASSERT(this->isRRect()); return fRRect;           }
 *     const SkArc&   arc()   const { SkASSERT(this->isArc());   return fArc;             }
 *     const SkPath&  path()  const { SkASSERT(this->isPath());  return fPath;            }
 *
 *     // Non-const access to the more complex types
 *     Rect&    rect()  { SkASSERT(this->isRect());  return fRect;  }
 *     SkRRect& rrect() { SkASSERT(this->isRRect()); return fRRect; }
 *     SkArc&   arc()   { SkASSERT(this->isArc());   return fArc;   }
 *     SkPath&  path()  { SkASSERT(this->isPath());  return fPath;  }
 *
 *     // Update the geometry stored in the Shape and update its associated type to match. This
 *     // performs no simplification, so calling setRRect() with a round rect that has isRect() return
 *     // true will still be considered an rrect by Shape.
 *     //
 *     // These reset inversion to the default for the geometric type.
 *     void setLine(SkPoint p0, SkPoint p1) {
 *         this->setLine(skvx::float2{p0.fX, p0.fY}, skvx::float2{p1.fX, p1.fY});
 *     }
 *     void setLine(SkV2 p0, SkV2 p1) {
 *         this->setLine(skvx::float2{p0.x, p0.y}, skvx::float2{p1.x, p1.y});
 *     }
 *     void setLine(skvx::float2 p0, skvx::float2 p1) {
 *         this->setType(Type::kLine);
 *         fRect = Rect(p0, p1);
 *         fInverted = false;
 *     }
 *     void setRect(const SkRect& rect) { this->setRect(Rect(rect)); }
 *     void setRect(const Rect& rect) {
 *         this->setType(Type::kRect);
 *         fRect = rect;
 *         fInverted = false;
 *     }
 *     void setRRect(const SkRRect& rrect) {
 *         this->setType(Type::kRRect);
 *         fRRect = rrect;
 *         fInverted = false;
 *     }
 *     void setArc(const SkArc& arc) {
 *         this->setType(Type::kArc);
 *         fArc = arc;
 *         fInverted = false;
 *     }
 *     void setPath(const SkPath& path) {
 *         if (fType == Type::kPath) {
 *             // Assign directly
 *             fPath = path;
 *         } else {
 *             // In-place initialize
 *             this->setType(Type::kPath);
 *             new (&fPath) SkPath(path);
 *         }
 *         fInverted = path.isInverseFillType();
 *     }
 *
 *     void reset() {
 *         this->setType(Type::kEmpty);
 *         fInverted = false;
 *     }
 *
 *     /**
 *      * Gets the size of the key for the shape represented by this Shape.
 *      */
 *     int keySize() const;
 *
 *     /**
 *      * Writes keySize() bytes into the provided pointer. Assumes that there is enough
 *      * space allocated for the key. If includeInverted is false, non-inverted state will
 *      * be written into the key regardless of the Shape's state.
 *      */
 *     void writeKey(uint32_t* key, bool includeInverted) const;
 *
 * private:
 *     void setType(Type type) {
 *         if (this->isPath() && type != Type::kPath) {
 *             fPath.~SkPath();
 *         }
 *         fType = type;
 *     }
 *
 *     /**
 *      * Key for the state data in the shape. This includes path fill type,
 *      * and any tracked inversion, as well as the class of geometry.
 *      * If includeInverted is false, non-inverted state will be written into
 *      * the key regardless of the Shape's state.
 *      */
 *     uint32_t stateKey(bool includeInverted) const;
 *
 *     union {
 *         Rect    fRect; // p0 = top-left, p1 = bot-right if type is kLine (may be unsorted)
 *         SkRRect fRRect;
 *         SkArc   fArc;
 *         SkPath  fPath;
 *     };
 *
 *     Type    fType     = Type::kEmpty;
 *     bool    fInverted = false;
 * }
 * ```
 */
public data class Shape public constructor(
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kTypeCount = static_cast<int>(Type::kPath) + 1
   * ```
   */
  private var fType: Type,
  /**
   * C++ original:
   * ```cpp
   * static constexpr float kDefaultPixelTolerance = 0.0039f
   * ```
   */
  private var fInverted: Boolean,
  /**
   * C++ original:
   * ```cpp
   * Type    fType     = Type::kEmpty
   * ```
   */
  private var fRect: Int,
  /**
   * C++ original:
   * ```cpp
   * bool    fInverted = false
   * ```
   */
  private var fRRect: Int,
  private var fArc: Int,
  private var fPath: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * Shape& operator=(Shape&&) = delete
   * ```
   */
  public fun assign(param0: Shape) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * Shape& Shape::operator=(const Shape& shape) {
   *     switch (shape.type()) {
   *         case Type::kEmpty: this->reset();                         break;
   *         case Type::kLine:  this->setLine(shape.p0(), shape.p1()); break;
   *         case Type::kRect:  this->setRect(shape.rect());           break;
   *         case Type::kRRect: this->setRRect(shape.rrect());         break;
   *         case Type::kArc:   this->setArc(shape.arc());             break;
   *         case Type::kPath:  this->setPath(shape.path());           break;
   *     }
   *
   *     fInverted = shape.fInverted;
   *     return *this;
   * }
   * ```
   */
  public fun type(): Type {
    TODO("Implement type")
  }

  /**
   * C++ original:
   * ```cpp
   * Type type() const { return fType; }
   * ```
   */
  public fun isEmpty(): Boolean {
    TODO("Implement isEmpty")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isEmpty() const { return fType == Type::kEmpty; }
   * ```
   */
  public fun isLine(): Boolean {
    TODO("Implement isLine")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isLine()  const { return fType == Type::kLine;  }
   * ```
   */
  public fun isRect(): Boolean {
    TODO("Implement isRect")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isRect()  const { return fType == Type::kRect;  }
   * ```
   */
  public fun isRRect(): Boolean {
    TODO("Implement isRRect")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isRRect() const { return fType == Type::kRRect; }
   * ```
   */
  public fun isArc(): Boolean {
    TODO("Implement isArc")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isArc()   const { return fType == Type::kArc;   }
   * ```
   */
  public fun isPath(): Boolean {
    TODO("Implement isPath")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isPath()  const { return fType == Type::kPath;  }
   * ```
   */
  public fun isFloodFill(): Boolean {
    TODO("Implement isFloodFill")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isFloodFill() const { return this->isEmpty() && this->inverted(); }
   * ```
   */
  public fun isVolatilePath(): Boolean {
    TODO("Implement isVolatilePath")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isVolatilePath() const {
   *         return fType == Type::kPath && this->path().isVolatile();
   *     }
   * ```
   */
  public fun inverted(): Boolean {
    TODO("Implement inverted")
  }

  /**
   * C++ original:
   * ```cpp
   * bool inverted() const {
   *         SkASSERT(fType != Type::kPath || fInverted == fPath.isInverseFillType());
   *         return fInverted;
   *     }
   * ```
   */
  public fun setInverted(inverted: Boolean) {
    TODO("Implement setInverted")
  }

  /**
   * C++ original:
   * ```cpp
   * void setInverted(bool inverted) {
   *         if (fType == Type::kPath && inverted != fPath.isInverseFillType()) {
   *             fPath.toggleInverseFillType();
   *         }
   *         fInverted = inverted;
   *     }
   * ```
   */
  public fun fillType(): Int {
    TODO("Implement fillType")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathFillType fillType() const {
   *         if (fType == Type::kPath) {
   *             return fPath.getFillType(); // already incorporates invertedness
   *         } else {
   *             return fInverted ? SkPathFillType::kInverseEvenOdd : SkPathFillType::kEvenOdd;
   *         }
   *     }
   * ```
   */
  public fun conservativeContains(rect: Rect): Boolean {
    TODO("Implement conservativeContains")
  }

  /**
   * C++ original:
   * ```cpp
   * bool conservativeContains(const Rect& rect) const
   * ```
   */
  public fun conservativeContains(point: Float2): Boolean {
    TODO("Implement conservativeContains")
  }

  /**
   * C++ original:
   * ```cpp
   * bool conservativeContains(skvx::float2 point) const
   * ```
   */
  public fun convex(simpleFill: Boolean = TODO()): Boolean {
    TODO("Implement convex")
  }

  /**
   * C++ original:
   * ```cpp
   * bool Shape::convex(bool simpleFill) const {
   *     if (this->isPath()) {
   *         // SkPath.isConvex() really means "is this path convex were it to be closed".
   *         return (simpleFill || fPath.isLastContourClosed()) && fPath.isConvex();
   *     } else if (this->isArc()) {
   *         return SkPathPriv::DrawArcIsConvex(fArc.sweepAngle(), fArc.fType, simpleFill);
   *     } else {
   *         // Every other shape type is convex by construction.
   *         return true;
   *     }
   * }
   * ```
   */
  public fun bounds(): Int {
    TODO("Implement bounds")
  }

  /**
   * C++ original:
   * ```cpp
   * Rect Shape::bounds() const {
   *     switch (fType) {
   *         case Type::kEmpty: return Rect(0, 0, 0, 0);
   *         case Type::kLine:  return fRect.makeSorted(); // sorting corners computes bbox of segment
   *         case Type::kRect:  return fRect; // assuming it's sorted
   *         case Type::kRRect: return fRRect.getBounds();
   *         case Type::kArc:   return fArc.oval();
   *         case Type::kPath:  return fPath.getBounds();
   *     }
   *     SkUNREACHABLE;
   * }
   * ```
   */
  public fun asPath(): Int {
    TODO("Implement asPath")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPath Shape::asPath() const {
   *     if (fType == Type::kPath) {
   *         return fPath;
   *     }
   *
   *     if (fType == Type::kArc) {
   *         // Filled ovals are already culled out so we assume no simple fills
   *         SkPath out = SkPathPriv::CreateDrawArcPath(fArc, /*isFillNoPathEffect=*/false);
   *         // CreateDrawArcPath resets the output path and configures its fill
   *         // type, so we just have to ensure invertedness is correct.
   *         if (fInverted) {
   *             out.toggleInverseFillType();
   *         }
   *         return out;
   *     }
   *
   *     SkPathBuilder builder(this->fillType());
   *     switch (fType) {
   *         case Type::kEmpty: /* do nothing */                            break;
   *         case Type::kLine:  builder.moveTo(fRect.left(), fRect.top())
   *                                   .lineTo(fRect.right(), fRect.bot()); break;
   *         case Type::kRect:  builder.addRect(fRect.asSkRect());          break;
   *         case Type::kRRect: builder.addRRect(fRRect);                   break;
   *         case Type::kPath:
   *         case Type::kArc:   SkUNREACHABLE;
   *     }
   *     return builder.detach();
   * }
   * ```
   */
  public fun p0(): Int {
    TODO("Implement p0")
  }

  /**
   * C++ original:
   * ```cpp
   * skvx::float2   p0()    const { SkASSERT(this->isLine());  return fRect.topLeft();  }
   * ```
   */
  public fun p1(): Int {
    TODO("Implement p1")
  }

  /**
   * C++ original:
   * ```cpp
   * skvx::float2   p1()    const { SkASSERT(this->isLine());  return fRect.botRight(); }
   * ```
   */
  public fun line(): Int {
    TODO("Implement line")
  }

  /**
   * C++ original:
   * ```cpp
   * skvx::float4   line()  const { SkASSERT(this->isLine());  return fRect.ltrb();     }
   * ```
   */
  public fun rect(): Int {
    TODO("Implement rect")
  }

  /**
   * C++ original:
   * ```cpp
   * const Rect&    rect()  const { SkASSERT(this->isRect());  return fRect;            }
   * ```
   */
  public fun rrect(): Int {
    TODO("Implement rrect")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkRRect& rrect() const { SkASSERT(this->isRRect()); return fRRect;           }
   * ```
   */
  public fun arc(): Int {
    TODO("Implement arc")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkArc&   arc()   const { SkASSERT(this->isArc());   return fArc;             }
   * ```
   */
  public fun path(): Int {
    TODO("Implement path")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkPath&  path()  const { SkASSERT(this->isPath());  return fPath;            }
   * ```
   */
  public fun setLine(p0: SkPoint, p1: SkPoint) {
    TODO("Implement setLine")
  }

  /**
   * C++ original:
   * ```cpp
   * Rect&    rect()  { SkASSERT(this->isRect());  return fRect;  }
   * ```
   */
  public fun setLine(p0: SkV2, p1: SkV2) {
    TODO("Implement setLine")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRRect& rrect() { SkASSERT(this->isRRect()); return fRRect; }
   * ```
   */
  public fun setLine(p0: Float2, p1: Float2) {
    TODO("Implement setLine")
  }

  /**
   * C++ original:
   * ```cpp
   * SkArc&   arc()   { SkASSERT(this->isArc());   return fArc;   }
   * ```
   */
  public fun setRect(rect: SkRect) {
    TODO("Implement setRect")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPath&  path()  { SkASSERT(this->isPath());  return fPath;  }
   * ```
   */
  public fun setRect(rect: Rect) {
    TODO("Implement setRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void setLine(SkPoint p0, SkPoint p1) {
   *         this->setLine(skvx::float2{p0.fX, p0.fY}, skvx::float2{p1.fX, p1.fY});
   *     }
   * ```
   */
  public fun setRRect(rrect: SkRRect) {
    TODO("Implement setRRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void setLine(SkV2 p0, SkV2 p1) {
   *         this->setLine(skvx::float2{p0.x, p0.y}, skvx::float2{p1.x, p1.y});
   *     }
   * ```
   */
  public fun setArc(arc: SkArc) {
    TODO("Implement setArc")
  }

  /**
   * C++ original:
   * ```cpp
   * void setLine(skvx::float2 p0, skvx::float2 p1) {
   *         this->setType(Type::kLine);
   *         fRect = Rect(p0, p1);
   *         fInverted = false;
   *     }
   * ```
   */
  public fun setPath(path: SkPath) {
    TODO("Implement setPath")
  }

  /**
   * C++ original:
   * ```cpp
   * void setRect(const SkRect& rect) { this->setRect(Rect(rect)); }
   * ```
   */
  public fun reset() {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * void setRect(const Rect& rect) {
   *         this->setType(Type::kRect);
   *         fRect = rect;
   *         fInverted = false;
   *     }
   * ```
   */
  public fun keySize(): Int {
    TODO("Implement keySize")
  }

  /**
   * C++ original:
   * ```cpp
   * void setRRect(const SkRRect& rrect) {
   *         this->setType(Type::kRRect);
   *         fRRect = rrect;
   *         fInverted = false;
   *     }
   * ```
   */
  public fun writeKey(key: UInt?, includeInverted: Boolean) {
    TODO("Implement writeKey")
  }

  /**
   * C++ original:
   * ```cpp
   * void setArc(const SkArc& arc) {
   *         this->setType(Type::kArc);
   *         fArc = arc;
   *         fInverted = false;
   *     }
   * ```
   */
  private fun setType(type: Type) {
    TODO("Implement setType")
  }

  /**
   * C++ original:
   * ```cpp
   * void setPath(const SkPath& path) {
   *         if (fType == Type::kPath) {
   *             // Assign directly
   *             fPath = path;
   *         } else {
   *             // In-place initialize
   *             this->setType(Type::kPath);
   *             new (&fPath) SkPath(path);
   *         }
   *         fInverted = path.isInverseFillType();
   *     }
   * ```
   */
  private fun stateKey(includeInverted: Boolean): Int {
    TODO("Implement stateKey")
  }

  public enum class Type {
    kEmpty,
    kLine,
    kRect,
    kRRect,
    kArc,
    kPath,
  }

  public companion object {
    public val kTypeCount: Int = TODO("Initialize kTypeCount")

    public val kDefaultPixelTolerance: Float = TODO("Initialize kDefaultPixelTolerance")
  }
}
