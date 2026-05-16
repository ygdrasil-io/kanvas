package org.skia.modules

import kotlin.Int
import org.skia.math.SkM44

/**
 * C++ original:
 * ```cpp
 * class TransformAdapter3D : public DiscardableAdapterBase<TransformAdapter3D, sksg::Matrix<SkM44>> {
 * public:
 *     TransformAdapter3D(const skjson::ObjectValue&, const AnimationBuilder&);
 *     ~TransformAdapter3D() override;
 *
 *     virtual SkM44 totalMatrix() const;
 *
 * protected:
 *     SkV3 anchor_point() const;
 *     SkV3 position() const;
 *     SkV3 rotation() const;
 *
 * private:
 *     void onSync() final;
 *
 *     VectorValue fAnchorPoint,
 *                 fPosition,
 *                 fOrientation,
 *                 fScale = { 100, 100, 100 };
 *     ScalarValue fRx = 0,
 *                 fRy = 0,
 *                 fRz = 0;
 *
 *     using INHERITED = DiscardableAdapterBase<TransformAdapter3D, sksg::Matrix<SkM44>>;
 * }
 * ```
 */
public open class TransformAdapter3D public constructor(
  jtransform: ObjectValue,
  abuilder: AnimationBuilder,
) : DiscardableAdapterBase(),
    TransformAdapter3D,
    Matrix,
    SkM44 {
  /**
   * C++ original:
   * ```cpp
   * VectorValue fAnchorPoint
   * ```
   */
  private var fAnchorPoint: Int = TODO("Initialize fAnchorPoint")

  /**
   * C++ original:
   * ```cpp
   * VectorValue fAnchorPoint,
   *                 fPosition
   * ```
   */
  private var fPosition: Int = TODO("Initialize fPosition")

  /**
   * C++ original:
   * ```cpp
   * VectorValue fAnchorPoint,
   *                 fPosition,
   *                 fOrientation
   * ```
   */
  private var fOrientation: Int = TODO("Initialize fOrientation")

  /**
   * C++ original:
   * ```cpp
   * VectorValue fAnchorPoint,
   *                 fPosition,
   *                 fOrientation,
   *                 fScale
   * ```
   */
  private var fScale: Int = TODO("Initialize fScale")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fRx
   * ```
   */
  private var fRx: Int = TODO("Initialize fRx")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fRx = 0,
   *                 fRy
   * ```
   */
  private var fRy: Int = TODO("Initialize fRy")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fRx = 0,
   *                 fRy = 0,
   *                 fRz
   * ```
   */
  private var fRz: Int = TODO("Initialize fRz")

  /**
   * C++ original:
   * ```cpp
   * SkM44 TransformAdapter3D::totalMatrix() const {
   *     const auto anchor_point = this->anchor_point(),
   *                position     = this->position(),
   *                scale        = static_cast<SkV3>(fScale),
   *                rotation     = this->rotation();
   *
   *     return SkM44::Translate(position.x, position.y, position.z)
   *          * SkM44::Rotate({ 1, 0, 0 }, SkDegreesToRadians(rotation.x))
   *          * SkM44::Rotate({ 0, 1, 0 }, SkDegreesToRadians(rotation.y))
   *          * SkM44::Rotate({ 0, 0, 1 }, SkDegreesToRadians(rotation.z))
   *          * SkM44::Scale(scale.x / 100, scale.y / 100, scale.z / 100)
   *          * SkM44::Translate(-anchor_point.x, -anchor_point.y, -anchor_point.z);
   * }
   * ```
   */
  public override fun totalMatrix(): Int {
    TODO("Implement totalMatrix")
  }

  /**
   * C++ original:
   * ```cpp
   * SkV3 TransformAdapter3D::anchor_point() const {
   *     return fAnchorPoint;
   * }
   * ```
   */
  protected override fun anchorPoint(): Int {
    TODO("Implement anchorPoint")
  }

  /**
   * C++ original:
   * ```cpp
   * SkV3 TransformAdapter3D::position() const {
   *     return fPosition;
   * }
   * ```
   */
  protected override fun position(): Int {
    TODO("Implement position")
  }

  /**
   * C++ original:
   * ```cpp
   * SkV3 TransformAdapter3D::rotation() const {
   *     // orientation and axis-wise rotation map onto the same property.
   *     return static_cast<SkV3>(fOrientation) + SkV3{ fRx, fRy, fRz };
   * }
   * ```
   */
  protected override fun rotation(): Int {
    TODO("Implement rotation")
  }

  /**
   * C++ original:
   * ```cpp
   * void TransformAdapter3D::onSync() {
   *     this->node()->setMatrix(this->totalMatrix());
   * }
   * ```
   */
  public override fun onSync() {
    TODO("Implement onSync")
  }
}
