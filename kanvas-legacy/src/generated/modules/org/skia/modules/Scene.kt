package org.skia.modules

import kotlin.Int
import org.skia.core.SkCanvas
import org.skia.foundation.SkSp
import org.skia.math.SkPoint

/**
 * C++ original:
 * ```cpp
 * class Scene final {
 * public:
 *     static std::unique_ptr<Scene> Make(sk_sp<RenderNode> root);
 *     ~Scene();
 *     Scene(const Scene&) = delete;
 *     Scene& operator=(const Scene&) = delete;
 *
 *     void render(SkCanvas*) const;
 *     void revalidate(InvalidationController* = nullptr);
 *     const RenderNode* nodeAt(const SkPoint&) const;
 *
 * private:
 *     explicit Scene(sk_sp<RenderNode> root);
 *
 *     const sk_sp<RenderNode> fRoot;
 * }
 * ```
 */
public data class Scene public constructor(
  /**
   * C++ original:
   * ```cpp
   * explicit Scene(sk_sp<RenderNode> root)
   * ```
   */
  private var skSp: Scene,
  /**
   * C++ original:
   * ```cpp
   * const sk_sp<RenderNode> fRoot
   * ```
   */
  private val fRoot: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * Scene& operator=(const Scene&) = delete
   * ```
   */
  public fun assign(param0: Scene) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * void Scene::render(SkCanvas* canvas) const {
   *     fRoot->render(canvas);
   * }
   * ```
   */
  public fun render(canvas: SkCanvas?) {
    TODO("Implement render")
  }

  /**
   * C++ original:
   * ```cpp
   * void Scene::revalidate(InvalidationController* ic) {
   *     fRoot->revalidate(ic, SkMatrix::I());
   * }
   * ```
   */
  public fun revalidate(ic: InvalidationController? = TODO()) {
    TODO("Implement revalidate")
  }

  /**
   * C++ original:
   * ```cpp
   * const RenderNode* Scene::nodeAt(const SkPoint& p) const {
   *     return fRoot->nodeAt(p);
   * }
   * ```
   */
  public fun nodeAt(p: SkPoint): RenderNode {
    TODO("Implement nodeAt")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * std::unique_ptr<Scene> Scene::Make(sk_sp<RenderNode> root) {
     *     return root ? std::unique_ptr<Scene>(new Scene(std::move(root))) : nullptr;
     * }
     * ```
     */
    public fun make(root: SkSp<RenderNode>): Scene? {
      TODO("Implement make")
    }
  }
}
