package org.skia.modules

/**
 * C++ original:
 * ```cpp
 * class PathAdapter final : public DiscardableAdapterBase<PathAdapter, sksg::Path> {
 * public:
 *     PathAdapter(const skjson::Value& jpath, const AnimationBuilder& abuilder)
 *         : INHERITED(sksg::Path::Make()) {
 *         this->bind(abuilder, jpath, fShape);
 *     }
 *
 * private:
 *     void onSync() override {
 *         const auto& path_node = this->node();
 *
 *         SkPath path = fShape;
 *
 *         // FillType is tracked in the SG node, not in keyframes -- make sure we preserve it.
 *         path.setFillType(path_node->getFillType());
 *         path.setIsVolatile(!this->isStatic());
 *
 *         path_node->setPath(path);
 *     }
 *
 *     ShapeValue fShape;
 *
 *     using INHERITED = DiscardableAdapterBase<PathAdapter, sksg::Path>;
 * }
 * ```
 */
public class PathAdapter public constructor(
  jpath: Value,
  abuilder: AnimationBuilder,
) : DiscardableAdapterBase(TODO()),
    PathAdapter,
    Path {
  /**
   * C++ original:
   * ```cpp
   * ShapeValue fShape
   * ```
   */
  private var fShape: ShapeValue = TODO("Initialize fShape")

  /**
   * C++ original:
   * ```cpp
   * void onSync() override {
   *         const auto& path_node = this->node();
   *
   *         SkPath path = fShape;
   *
   *         // FillType is tracked in the SG node, not in keyframes -- make sure we preserve it.
   *         path.setFillType(path_node->getFillType());
   *         path.setIsVolatile(!this->isStatic());
   *
   *         path_node->setPath(path);
   *     }
   * ```
   */
  public override fun onSync() {
    TODO("Implement onSync")
  }
}
