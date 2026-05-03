package org.skia.modules

import kotlin.Int
import org.skia.math.SkSize
import org.skia.math.SkV3

/**
 * C++ original:
 * ```cpp
 * class CameraAdaper final : public TransformAdapter3D {
 * public:
 *     CameraAdaper(const skjson::ObjectValue& jlayer,
 *                  const skjson::ObjectValue& jtransform,
 *                  const AnimationBuilder& abuilder,
 *                  const SkSize& viewport_size);
 *     ~CameraAdaper() override;
 *
 *     // Used in the absence of an explicit camera layer.
 *     static sk_sp<sksg::Transform> DefaultCameraTransform(const SkSize& viewport_size);
 *
 *     SkM44 totalMatrix() const override;
 *
 * private:
 *     enum class CameraType {
 *         kOneNode, // implicitly facing forward (decreasing z), does not auto-orient
 *         kTwoNode, // explicitly facing a POI (the anchor point), auto-orients
 *     };
 *
 *     SkV3 poi(const SkV3& pos) const;
 *
 *     const SkSize     fViewportSize;
 *     const CameraType fType;
 *
 *     ScalarValue fZoom = 0;
 *
 *     using INHERITED = TransformAdapter3D;
 * }
 * ```
 */
public class CameraAdaper public constructor(
  jlayer: ObjectValue,
  jtransform: ObjectValue,
  abuilder: AnimationBuilder,
  viewportSize: SkSize,
) : TransformAdapter3D() {
  /**
   * C++ original:
   * ```cpp
   * const SkSize     fViewportSize
   * ```
   */
  private val fViewportSize: Int = TODO("Initialize fViewportSize")

  /**
   * C++ original:
   * ```cpp
   * const CameraType fType
   * ```
   */
  private val fType: CameraType = TODO("Initialize fType")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fZoom
   * ```
   */
  private var fZoom: Int = TODO("Initialize fZoom")

  /**
   * C++ original:
   * ```cpp
   * SkM44 CameraAdaper::totalMatrix() const {
   *     // Camera parameters:
   *     //
   *     //   * location          -> position attribute
   *     //   * point of interest -> anchor point attribute (two-node camera only)
   *     //   * orientation       -> rotation attribute
   *     //
   *     const auto position = this->position();
   *
   *     return ComputeCameraMatrix(position,
   *                                this->poi(position),
   *                                this->rotation(),
   *                                fViewportSize,
   *                                fZoom);
   * }
   * ```
   */
  public override fun totalMatrix(): Int {
    TODO("Implement totalMatrix")
  }

  /**
   * C++ original:
   * ```cpp
   * SkV3 CameraAdaper::poi(const SkV3& pos) const {
   *     // AE supports two camera types:
   *     //
   *     //   - one-node camera: does not auto-orient, and starts off perpendicular
   *     //     to the z = 0 plane, facing "forward" (decreasing z).
   *     //
   *     //   - two-node camera: has a point of interest (encoded as the anchor point),
   *     //     and auto-orients to point in its direction.
   *
   *     if (fType == CameraType::kOneNode) {
   *         return { pos.x, pos.y, -pos.z - 1};
   *     }
   *
   *     const auto ap = this->anchor_point();
   *
   *     return { ap.x, ap.y, -ap.z };
   * }
   * ```
   */
  private fun poi(pos: SkV3): Int {
    TODO("Implement poi")
  }

  public enum class CameraType {
    kOneNode,
    kTwoNode,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<sksg::Transform> CameraAdaper::DefaultCameraTransform(const SkSize& viewport_size) {
     *     const auto center = SkVector::Make(viewport_size.width()  * 0.5f,
     *                                        viewport_size.height() * 0.5f);
     *
     *     static constexpr float kDefaultAEZoom = 879.13f;
     *
     *     const SkV3 pos = { center.fX, center.fY, -kDefaultAEZoom },
     *                poi = {     pos.x,     pos.y,      -pos.z - 1 },
     *                rot = {         0,         0,               0 };
     *
     *     return sksg::Matrix<SkM44>::Make(
     *                 ComputeCameraMatrix(pos, poi, rot, viewport_size, kDefaultAEZoom));
     * }
     * ```
     */
    public fun defaultCameraTransform(viewportSize: SkSize): Int {
      TODO("Implement defaultCameraTransform")
    }
  }
}
