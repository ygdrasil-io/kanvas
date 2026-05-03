package org.skia.modules

import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkVector

/**
 * C++ original:
 * ```cpp
 * class TransformAdapter2D final : public DiscardableAdapterBase<TransformAdapter2D,
 *                                                                sksg::Matrix<SkMatrix>> {
 * public:
 *     TransformAdapter2D(const AnimationBuilder&,
 *                        const skjson::ObjectValue* janchor_point,
 *                        const skjson::ObjectValue* jposition,
 *                        const skjson::ObjectValue* jscale,
 *                        const skjson::ObjectValue* jrotation,
 *                        const skjson::ObjectValue* jskew,
 *                        const skjson::ObjectValue* jskew_axis,
 *                        bool auto_orient = false);
 *     ~TransformAdapter2D() override;
 *
 *     // Accessors needed for public property APIs.
 *     // TODO: introduce a separate public type.
 *     SkPoint getAnchorPoint() const;
 *     void    setAnchorPoint(const SkPoint&);
 *
 *     SkPoint getPosition() const;
 *     void    setPosition(const SkPoint&);
 *
 *     SkVector getScale() const;
 *     void     setScale(const SkVector&);
 *
 *     float getRotation() const  { return fRotation; }
 *     void  setRotation(float r);
 *
 *     float getSkew() const   { return fSkew; }
 *     void  setSkew(float sk);
 *
 *     float getSkewAxis() const    { return fSkewAxis; }
 *     void  setSkewAxis(float sa );
 *
 *     SkMatrix totalMatrix() const;
 *
 * private:
 *     void onSync() override;
 *
 *     Vec2Value   fAnchorPoint = {   0,   0 },
 *                 fPosition    = {   0,   0 },
 *                 fScale       = { 100, 100 };
 *     ScalarValue fRotation    = 0,
 *                 fSkew        = 0,
 *                 fSkewAxis    = 0,
 *                 fOrientation = 0; // additional rotation component controlled by auto-orient
 *
 *     using INHERITED = DiscardableAdapterBase<TransformAdapter2D, sksg::Matrix<SkMatrix>>;
 * }
 * ```
 */
public class TransformAdapter2D public constructor(
  abuilder: AnimationBuilder,
  janchorPoint: ObjectValue?,
  jposition: ObjectValue?,
  jscale: ObjectValue?,
  jrotation: ObjectValue?,
  jskew: ObjectValue?,
  jskewAxis: ObjectValue?,
  autoOrient: Boolean = TODO(),
) : DiscardableAdapterBase(),
    TransformAdapter2D,
    Matrix,
    SkMatrix {
  /**
   * C++ original:
   * ```cpp
   * Vec2Value   fAnchorPoint
   * ```
   */
  private var fAnchorPoint: Int = TODO("Initialize fAnchorPoint")

  /**
   * C++ original:
   * ```cpp
   * Vec2Value   fAnchorPoint = {   0,   0 },
   *                 fPosition
   * ```
   */
  private var fPosition: Int = TODO("Initialize fPosition")

  /**
   * C++ original:
   * ```cpp
   * Vec2Value   fAnchorPoint = {   0,   0 },
   *                 fPosition    = {   0,   0 },
   *                 fScale
   * ```
   */
  private var fScale: Int = TODO("Initialize fScale")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fRotation
   * ```
   */
  private var fRotation: Int = TODO("Initialize fRotation")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fRotation    = 0,
   *                 fSkew
   * ```
   */
  private var fSkew: Int = TODO("Initialize fSkew")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fRotation    = 0,
   *                 fSkew        = 0,
   *                 fSkewAxis
   * ```
   */
  private var fSkewAxis: Int = TODO("Initialize fSkewAxis")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fRotation    = 0,
   *                 fSkew        = 0,
   *                 fSkewAxis    = 0,
   *                 fOrientation
   * ```
   */
  private var fOrientation: Int = TODO("Initialize fOrientation")

  /**
   * C++ original:
   * ```cpp
   * SkPoint TransformAdapter2D::getAnchorPoint() const {
   *     return { fAnchorPoint.x, fAnchorPoint.y };
   * }
   * ```
   */
  public override fun getAnchorPoint(): Int {
    TODO("Implement getAnchorPoint")
  }

  /**
   * C++ original:
   * ```cpp
   * void TransformAdapter2D::setAnchorPoint(const SkPoint& ap) {
   *     fAnchorPoint = { ap.x(), ap.y() };
   *     this->onSync();
   * }
   * ```
   */
  public override fun setAnchorPoint(ap: SkPoint) {
    TODO("Implement setAnchorPoint")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPoint TransformAdapter2D::getPosition() const {
   *     return { fPosition.x, fPosition.y };
   * }
   * ```
   */
  public override fun getPosition(): Int {
    TODO("Implement getPosition")
  }

  /**
   * C++ original:
   * ```cpp
   * void TransformAdapter2D::setPosition(const SkPoint& p) {
   *     fPosition = { p.x(), p.y() };
   *     this->onSync();
   * }
   * ```
   */
  public override fun setPosition(p: SkPoint) {
    TODO("Implement setPosition")
  }

  /**
   * C++ original:
   * ```cpp
   * SkVector TransformAdapter2D::getScale() const {
   *     return { fScale.x, fScale.y };
   * }
   * ```
   */
  public override fun getScale(): Int {
    TODO("Implement getScale")
  }

  /**
   * C++ original:
   * ```cpp
   * void TransformAdapter2D::setScale(const SkVector& s) {
   *     fScale = { s.x(), s.y() };
   *     this->onSync();
   * }
   * ```
   */
  public override fun setScale(s: SkVector) {
    TODO("Implement setScale")
  }

  /**
   * C++ original:
   * ```cpp
   * float getRotation() const  { return fRotation; }
   * ```
   */
  public override fun getRotation(): Float {
    TODO("Implement getRotation")
  }

  /**
   * C++ original:
   * ```cpp
   * void TransformAdapter2D::setRotation(float r) {
   *     fRotation = r;
   *     this->onSync();
   * }
   * ```
   */
  public override fun setRotation(r: Float) {
    TODO("Implement setRotation")
  }

  /**
   * C++ original:
   * ```cpp
   * float getSkew() const   { return fSkew; }
   * ```
   */
  public override fun getSkew(): Float {
    TODO("Implement getSkew")
  }

  /**
   * C++ original:
   * ```cpp
   * void TransformAdapter2D::setSkew(float sk) {
   *     fSkew = sk;
   *     this->onSync();
   * }
   * ```
   */
  public override fun setSkew(sk: Float) {
    TODO("Implement setSkew")
  }

  /**
   * C++ original:
   * ```cpp
   * float getSkewAxis() const    { return fSkewAxis; }
   * ```
   */
  public override fun getSkewAxis(): Float {
    TODO("Implement getSkewAxis")
  }

  /**
   * C++ original:
   * ```cpp
   * void TransformAdapter2D::setSkewAxis(float sa) {
   *     fSkewAxis = sa;
   *     this->onSync();
   * }
   * ```
   */
  public override fun setSkewAxis(sa: Float) {
    TODO("Implement setSkewAxis")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix TransformAdapter2D::totalMatrix() const {
   *     auto skew_matrix = [](float sk, float sa) {
   *         if (!sk) return SkMatrix::I();
   *
   *         // AE control limit.
   *         static constexpr float kMaxSkewAngle = 85;
   *         sk = -SkDegreesToRadians(SkTPin(sk, -kMaxSkewAngle, kMaxSkewAngle));
   *         sa =  SkDegreesToRadians(sa);
   *
   *         // Similar to CSS/SVG SkewX [1] with an explicit rotation.
   *         // [1] https://www.w3.org/TR/css-transforms-1/#SkewXDefined
   *         return SkMatrix::RotateRad(sa)
   *              * SkMatrix::Skew(std::tan(sk), 0)
   *              * SkMatrix::RotateRad(-sa);
   *     };
   *
   *     return SkMatrix::Translate(fPosition.x, fPosition.y)
   *          * SkMatrix::RotateDeg(fRotation + fOrientation)
   *          * skew_matrix        (fSkew, fSkewAxis)
   *          * SkMatrix::Scale    (fScale.x / 100, fScale.y / 100) // 100% based
   *          * SkMatrix::Translate(-fAnchorPoint.x, -fAnchorPoint.y);
   * }
   * ```
   */
  public override fun totalMatrix(): Int {
    TODO("Implement totalMatrix")
  }

  /**
   * C++ original:
   * ```cpp
   * void TransformAdapter2D::onSync() {
   *     this->node()->setMatrix(this->totalMatrix());
   * }
   * ```
   */
  public override fun onSync() {
    TODO("Implement onSync")
  }
}
