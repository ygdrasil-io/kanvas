package org.skia.modules

import kotlin.Any
import org.skia.foundation.SkSp
import org.skia.math.SkMatrix
import org.skia.math.SkSize
import undefined.Vec2Value

/**
 * C++ original:
 * ```cpp
 * class CornerPinAdapter final : public AnimatablePropertyContainer {
 * public:
 *     static sk_sp<CornerPinAdapter> Make(const skjson::ArrayValue& jprops,
 *                                         const AnimationBuilder& abuilder,
 *                                         const SkSize& layer_size) {
 *         return sk_sp<CornerPinAdapter>(new CornerPinAdapter(jprops, abuilder, layer_size));
 *     }
 *
 *     auto& node() const { return fMatrixNode; }
 *
 * private:
 *     CornerPinAdapter(const skjson::ArrayValue& jprops,
 *                      const AnimationBuilder& abuilder,
 *                      const SkSize& layer_size)
 *         : fMatrixNode(sksg::Matrix<SkMatrix>::Make(SkMatrix::I()))
 *         , fLayerSize(layer_size) {
 *         enum : size_t {
 *              kUpperLeft_Index = 0,
 *             kUpperRight_Index = 1,
 *              kLowerLeft_Index = 2,
 *             kLowerRight_Index = 3,
 *         };
 *
 *         EffectBinder(jprops, abuilder, this)
 *             .bind( kUpperLeft_Index, fUL)
 *             .bind(kUpperRight_Index, fUR)
 *             .bind( kLowerLeft_Index, fLL)
 *             .bind(kLowerRight_Index, fLR);
 *     }
 *
 *     void onSync() override {
 *         const SkPoint src[] = {{                 0,                   0},
 *                                {fLayerSize.width(),                   0},
 *                                {fLayerSize.width(), fLayerSize.height()},
 *                                {                 0, fLayerSize.height()}},
 *
 *                       dst[] = {{ fUL.x, fUL.y},
 *                                { fUR.x, fUR.y},
 *                                { fLR.x, fLR.y},
 *                                { fLL.x, fLL.y}};
 *         static_assert(std::size(src) == std::size(dst));
 *
 *         if (auto m = SkMatrix::PolyToPoly(src, dst)) {
 *             fMatrixNode->setMatrix(*m);
 *         }
 *     }
 *
 *     const sk_sp<sksg::Matrix<SkMatrix>> fMatrixNode;
 *     const SkSize                        fLayerSize;
 *
 *     Vec2Value fUL,
 *               fLL,
 *               fUR,
 *               fLR;
 * }
 * ```
 */
public class CornerPinAdapter public constructor(
  jprops: ArrayValue,
  abuilder: AnimationBuilder,
  layerSize: SkSize,
) : AnimatablePropertyContainer() {
  /**
   * C++ original:
   * ```cpp
   * const sk_sp<sksg::Matrix<SkMatrix>> fMatrixNode
   * ```
   */
  private val fMatrixNode: SkSp<Matrix<SkMatrix>> = TODO("Initialize fMatrixNode")

  /**
   * C++ original:
   * ```cpp
   * const SkSize                        fLayerSize
   * ```
   */
  private val fLayerSize: SkSize = TODO("Initialize fLayerSize")

  /**
   * C++ original:
   * ```cpp
   * Vec2Value fUL
   * ```
   */
  private var fUL: Vec2Value = TODO("Initialize fUL")

  /**
   * C++ original:
   * ```cpp
   * Vec2Value fUL,
   *               fLL
   * ```
   */
  private var fLL: Vec2Value = TODO("Initialize fLL")

  /**
   * C++ original:
   * ```cpp
   * Vec2Value fUL,
   *               fLL,
   *               fUR
   * ```
   */
  private var fUR: Vec2Value = TODO("Initialize fUR")

  /**
   * C++ original:
   * ```cpp
   * Vec2Value fUL,
   *               fLL,
   *               fUR,
   *               fLR
   * ```
   */
  private var fLR: Vec2Value = TODO("Initialize fLR")

  /**
   * C++ original:
   * ```cpp
   * auto& node() const { return fMatrixNode; }
   * ```
   */
  public fun node(): Any {
    TODO("Implement node")
  }

  /**
   * C++ original:
   * ```cpp
   * void onSync() override {
   *         const SkPoint src[] = {{                 0,                   0},
   *                                {fLayerSize.width(),                   0},
   *                                {fLayerSize.width(), fLayerSize.height()},
   *                                {                 0, fLayerSize.height()}},
   *
   *                       dst[] = {{ fUL.x, fUL.y},
   *                                { fUR.x, fUR.y},
   *                                { fLR.x, fLR.y},
   *                                { fLL.x, fLL.y}};
   *         static_assert(std::size(src) == std::size(dst));
   *
   *         if (auto m = SkMatrix::PolyToPoly(src, dst)) {
   *             fMatrixNode->setMatrix(*m);
   *         }
   *     }
   * ```
   */
  public override fun onSync() {
    TODO("Implement onSync")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<CornerPinAdapter> Make(const skjson::ArrayValue& jprops,
     *                                         const AnimationBuilder& abuilder,
     *                                         const SkSize& layer_size) {
     *         return sk_sp<CornerPinAdapter>(new CornerPinAdapter(jprops, abuilder, layer_size));
     *     }
     * ```
     */
    public fun make(
      jprops: ArrayValue,
      abuilder: AnimationBuilder,
      layerSize: SkSize,
    ): SkSp<CornerPinAdapter> {
      TODO("Implement make")
    }
  }
}
