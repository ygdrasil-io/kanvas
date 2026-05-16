package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import kotlin.collections.List
import org.skia.core.SkCanvas
import org.skia.foundation.SkSp
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import undefined.RenderContext

public typealias GlyphDecoratorNodeINHERITED = Group

/**
 * C++ original:
 * ```cpp
 * class Group : public RenderNode {
 * public:
 *     static sk_sp<Group> Make() {
 *         return sk_sp<Group>(new Group(std::vector<sk_sp<RenderNode>>()));
 *     }
 *
 *     static sk_sp<Group> Make(std::vector<sk_sp<RenderNode>> children) {
 *         return sk_sp<Group>(new Group(std::move(children)));
 *     }
 *
 *     void addChild(sk_sp<RenderNode>);
 *     void removeChild(const sk_sp<RenderNode>&);
 *
 *     size_t size() const { return fChildren.size(); }
 *     bool  empty() const { return fChildren.empty(); }
 *     void  clear();
 *
 * protected:
 *     Group();
 *     explicit Group(std::vector<sk_sp<RenderNode>>);
 *     ~Group() override;
 *
 *     void onRender(SkCanvas*, const RenderContext*) const override;
 *     const RenderNode* onNodeAt(const SkPoint&)     const override;
 *
 *     SkRect onRevalidate(InvalidationController*, const SkMatrix&) override;
 *
 * private:
 *     std::vector<sk_sp<RenderNode>> fChildren;
 *     bool                           fRequiresIsolation = true;
 *
 *     using INHERITED = RenderNode;
 * }
 * ```
 */
public open class Group public constructor() : RenderNode() {
  /**
   * C++ original:
   * ```cpp
   * std::vector<sk_sp<RenderNode>> fChildren
   * ```
   */
  private var fChildren: Int = TODO("Initialize fChildren")

  /**
   * C++ original:
   * ```cpp
   * bool                           fRequiresIsolation = true
   * ```
   */
  private var fRequiresIsolation: Boolean = TODO("Initialize fRequiresIsolation")

  /**
   * C++ original:
   * ```cpp
   * Group::Group()
   * ```
   */
  public constructor(children: List<SkSp<RenderNode>>) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * void Group::addChild(sk_sp<RenderNode> node) {
   *     // should we allow duplicates?
   *     for (const auto& child : fChildren) {
   *         if (child == node) {
   *             return;
   *         }
   *     }
   *
   *     this->observeInval(node);
   *     fChildren.push_back(std::move(node));
   *
   *     this->invalidate();
   * }
   * ```
   */
  public fun addChild(node: SkSp<RenderNode>) {
    TODO("Implement addChild")
  }

  /**
   * C++ original:
   * ```cpp
   * void Group::removeChild(const sk_sp<RenderNode>& node) {
   *     SkDEBUGCODE(const auto origSize = fChildren.size());
   *     fChildren.erase(std::remove(fChildren.begin(), fChildren.end(), node), fChildren.end());
   *     SkASSERT(fChildren.size() == origSize - 1);
   *
   *     this->unobserveInval(node);
   *     this->invalidate();
   * }
   * ```
   */
  public fun removeChild(node: SkSp<RenderNode>) {
    TODO("Implement removeChild")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t size() const { return fChildren.size(); }
   * ```
   */
  public fun size(): ULong {
    TODO("Implement size")
  }

  /**
   * C++ original:
   * ```cpp
   * bool  empty() const { return fChildren.empty(); }
   * ```
   */
  public fun empty(): Boolean {
    TODO("Implement empty")
  }

  /**
   * C++ original:
   * ```cpp
   * void Group::clear() {
   *     for (const auto& child : fChildren) {
   *         this->unobserveInval(child);
   *     }
   *     fChildren.clear();
   * }
   * ```
   */
  public fun clear() {
    TODO("Implement clear")
  }

  /**
   * C++ original:
   * ```cpp
   * void Group::onRender(SkCanvas* canvas, const RenderContext* ctx) const {
   *     const auto local_ctx = ScopedRenderContext(canvas, ctx).setIsolation(this->bounds(),
   *                                                                          canvas->getTotalMatrix(),
   *                                                                          fRequiresIsolation);
   *
   *     for (const auto& child : fChildren) {
   *         child->render(canvas, local_ctx);
   *     }
   * }
   * ```
   */
  protected override fun onRender(canvas: SkCanvas?, ctx: RenderContext?) {
    TODO("Implement onRender")
  }

  /**
   * C++ original:
   * ```cpp
   * const RenderNode* Group::onNodeAt(const SkPoint& p) const {
   *     for (auto it = fChildren.crbegin(); it != fChildren.crend(); ++it) {
   *         if (const auto* node = (*it)->nodeAt(p)) {
   *             return node;
   *         }
   *     }
   *
   *     return nullptr;
   * }
   * ```
   */
  protected override fun onNodeAt(p: SkPoint): Int {
    TODO("Implement onNodeAt")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect Group::onRevalidate(InvalidationController* ic, const SkMatrix& ctm) {
   *     SkASSERT(this->hasInval());
   *
   *     SkRect bounds = SkRect::MakeEmpty();
   *     fRequiresIsolation = false;
   *
   *     for (size_t i = 0; i < fChildren.size(); ++i) {
   *         const auto child_bounds = fChildren[i]->revalidate(ic, ctm);
   *
   *         // If any of the child nodes overlap, group effects require layer isolation.
   *         if (!fRequiresIsolation && i > 0 && child_bounds.intersects(bounds)) {
   * #if 1
   *             // Testing conservatively against the union of prev bounds is cheap and good enough.
   *             fRequiresIsolation = true;
   * #else
   *             // Testing exhaustively doesn't seem to increase the layer elision rate in practice.
   *             for (size_t j = 0; j < i; ++ j) {
   *                 if (child_bounds.intersects(fChildren[i]->bounds())) {
   *                     fRequiresIsolation = true;
   *                     break;
   *                 }
   *             }
   * #endif
   *         }
   *
   *         bounds.join(child_bounds);
   *     }
   *
   *     return bounds;
   * }
   * ```
   */
  protected override fun onRevalidate(ic: InvalidationController?, ctm: SkMatrix): Int {
    TODO("Implement onRevalidate")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<Group> Make() {
     *         return sk_sp<Group>(new Group(std::vector<sk_sp<RenderNode>>()));
     *     }
     * ```
     */
    public fun make(): Int {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<Group> Make(std::vector<sk_sp<RenderNode>> children) {
     *         return sk_sp<Group>(new Group(std::move(children)));
     *     }
     * ```
     */
    public fun make(children: List<SkSp<RenderNode>>): Int {
      TODO("Implement make")
    }
  }
}
