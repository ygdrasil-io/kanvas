package org.skia.modules

import kotlin.Int
import org.skia.foundation.SkNVRefCnt
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class SceneGraphRevalidator final : public SkNVRefCnt<SceneGraphRevalidator> {
 * public:
 *     void revalidate();
 *     void setRoot(sk_sp<sksg::RenderNode>);
 *
 * private:
 *     sk_sp<sksg::RenderNode> fRoot;
 * }
 * ```
 */
public class SceneGraphRevalidator : SkNVRefCnt(), SceneGraphRevalidator {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> fRoot
   * ```
   */
  private var fRoot: Int = TODO("Initialize fRoot")

  /**
   * C++ original:
   * ```cpp
   * void SceneGraphRevalidator::revalidate() {
   *     if (fRoot) {
   *         fRoot->revalidate(nullptr, SkMatrix::I());
   *     }
   * }
   * ```
   */
  public override fun revalidate() {
    TODO("Implement revalidate")
  }

  /**
   * C++ original:
   * ```cpp
   * void SceneGraphRevalidator::setRoot(sk_sp<sksg::RenderNode> root) {
   *     fRoot = std::move(root);
   * }
   * ```
   */
  public override fun setRoot(root: SkSp<RenderNode>) {
    TODO("Implement setRoot")
  }
}
