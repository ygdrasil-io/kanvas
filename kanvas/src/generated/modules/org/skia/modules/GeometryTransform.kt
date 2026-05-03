package org.skia.modules

import kotlin.Int
import org.skia.foundation.SkSp
import org.skia.math.SkMatrix

/**
 * C++ original:
 * ```cpp
 * class GeometryTransform final : public GeometryEffect {
 * public:
 *     static sk_sp<GeometryTransform> Make(sk_sp<GeometryNode> child, sk_sp<Transform> transform) {
 *         return child && transform
 *             ? sk_sp<GeometryTransform>(new GeometryTransform(std::move(child),
 *                                                              std::move(transform)))
 *             : nullptr;
 *     }
 *
 *     ~GeometryTransform() override;
 *
 *     const sk_sp<Transform>& getTransform() const { return fTransform; }
 *
 * private:
 *     GeometryTransform(sk_sp<GeometryNode>, sk_sp<Transform>);
 *
 *     SkPath onRevalidateEffect(const sk_sp<GeometryNode>&, const SkMatrix&) override;
 *
 *     const sk_sp<Transform> fTransform;
 *
 *     using INHERITED = GeometryEffect;
 * }
 * ```
 */
public class GeometryTransform public constructor(
  child: SkSp<GeometryNode>,
  transform: SkSp<Transform>,
) : GeometryEffect(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * const sk_sp<Transform> fTransform
   * ```
   */
  private val fTransform: Int = TODO("Initialize fTransform")

  /**
   * C++ original:
   * ```cpp
   * const sk_sp<Transform>& getTransform() const { return fTransform; }
   * ```
   */
  public fun getTransform(): Int {
    TODO("Implement getTransform")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPath GeometryTransform::onRevalidateEffect(const sk_sp<GeometryNode>& child, const SkMatrix&) {
   *     fTransform->revalidate(nullptr, SkMatrix::I());
   *     const auto m = TransformPriv::As<SkMatrix>(fTransform);
   *
   *     return child->asPath().makeTransform(m);
   * }
   * ```
   */
  public override fun onRevalidateEffect(child: SkSp<GeometryNode>, param1: SkMatrix): Int {
    TODO("Implement onRevalidateEffect")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<GeometryTransform> Make(sk_sp<GeometryNode> child, sk_sp<Transform> transform) {
     *         return child && transform
     *             ? sk_sp<GeometryTransform>(new GeometryTransform(std::move(child),
     *                                                              std::move(transform)))
     *             : nullptr;
     *     }
     * ```
     */
    public fun make(child: SkSp<GeometryNode>, transform: SkSp<Transform>): Int {
      TODO("Implement make")
    }
  }
}
