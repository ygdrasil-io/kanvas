package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkSp
import undefined.Func

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGContainer : public SkSVGTransformableNode {
 * public:
 *     void appendChild(sk_sp<SkSVGNode>) override;
 *
 * protected:
 *     explicit SkSVGContainer(SkSVGTag);
 *
 *     void onRender(const SkSVGRenderContext&) const override;
 *
 *     SkPath onAsPath(const SkSVGRenderContext&) const override;
 *
 *     SkRect onTransformableObjectBoundingBox(const SkSVGRenderContext&) const final;
 *
 *     bool hasChildren() const final;
 *
 *     template <typename NodeType, typename Func>
 *     void forEachChild(Func func) const {
 *         for (const auto& child : fChildren) {
 *             if (child->tag() == NodeType::tag) {
 *                 func(static_cast<const NodeType*>(child.get()));
 *             }
 *         }
 *     }
 *
 *     // TODO: convert remaining direct users to iterators, and make the container private.
 *     skia_private::STArray<1, sk_sp<SkSVGNode>, true> fChildren;
 *
 * private:
 *     using INHERITED = SkSVGTransformableNode;
 * }
 * ```
 */
public open class SkSVGContainer public constructor(
  t: SkSVGTag,
) : SkSVGTransformableNode(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * skia_private::STArray<1, sk_sp<SkSVGNode>, true> fChildren
   * ```
   */
  protected var fChildren: Int = TODO("Initialize fChildren")

  /**
   * C++ original:
   * ```cpp
   * void SkSVGContainer::appendChild(sk_sp<SkSVGNode> node) {
   *     SkASSERT(node);
   *     fChildren.push_back(std::move(node));
   * }
   * ```
   */
  public override fun appendChild(node: SkSp<SkSVGNode>) {
    TODO("Implement appendChild")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSVGContainer::onRender(const SkSVGRenderContext& ctx) const {
   *     for (int i = 0; i < fChildren.size(); ++i) {
   *         fChildren[i]->render(ctx);
   *     }
   * }
   * ```
   */
  protected override fun onRender(ctx: SkSVGRenderContext) {
    TODO("Implement onRender")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPath SkSVGContainer::onAsPath(const SkSVGRenderContext& ctx) const {
   *     SkPath path;
   *
   *     for (int i = 0; i < fChildren.size(); ++i) {
   *         const SkPath childPath = fChildren[i]->asPath(ctx);
   *
   *         if (auto result = Op(path, childPath, kUnion_SkPathOp)) {
   *             path = *result;
   *         }
   *     }
   *
   *     return this->mapToParent(path);
   * }
   * ```
   */
  protected override fun onAsPath(ctx: SkSVGRenderContext): Int {
    TODO("Implement onAsPath")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect SkSVGContainer::onTransformableObjectBoundingBox(const SkSVGRenderContext& ctx) const {
   *     SkRect bounds = SkRect::MakeEmpty();
   *
   *     for (int i = 0; i < fChildren.size(); ++i) {
   *         const SkRect childBounds = fChildren[i]->objectBoundingBox(ctx);
   *         bounds.join(childBounds);
   *     }
   *
   *     return bounds;
   * }
   * ```
   */
  protected override fun onTransformableObjectBoundingBox(ctx: SkSVGRenderContext): Int {
    TODO("Implement onTransformableObjectBoundingBox")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGContainer::hasChildren() const {
   *     return !fChildren.empty();
   * }
   * ```
   */
  protected fun hasChildren(): Boolean {
    TODO("Implement hasChildren")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <typename NodeType, typename Func>
   *     void forEachChild(Func func) const {
   *         for (const auto& child : fChildren) {
   *             if (child->tag() == NodeType::tag) {
   *                 func(static_cast<const NodeType*>(child.get()));
   *             }
   *         }
   *     }
   * ```
   */
  protected fun <NodeType, Func> forEachChild(func: Func) {
    TODO("Implement forEachChild")
  }
}

public typealias SkSVGHiddenContainerINHERITED = SkSVGContainer
