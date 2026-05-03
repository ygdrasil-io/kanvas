package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import kotlin.collections.List
import org.skia.foundation.SkRefCnt
import org.skia.foundation.SkSp
import org.skia.math.SkMatrix
import undefined.Func

/**
 * C++ original:
 * ```cpp
 * class Node : public SkRefCnt {
 * public:
 *     // Traverse the DAG and revalidate any dependant/invalidated nodes.
 *     // Returns the bounding box for the DAG fragment.
 *     const SkRect& revalidate(InvalidationController*, const SkMatrix&);
 *
 *     // Tag this node for invalidation and optional damage.
 *     void invalidate(bool damage = true);
 *
 * protected:
 *     enum InvalTraits {
 *         // Nodes with this trait never generate direct damage -- instead,
 *         // the damage bubbles up to ancestors.
 *         kBubbleDamage_Trait   = 1 << 0,
 *
 *         // Nodes with this trait obscure the descendants' damage and always override it.
 *         kOverrideDamage_Trait = 1 << 1,
 *     };
 *
 *     explicit Node(uint32_t invalTraits);
 *     ~Node() override;
 *
 *     const SkRect& bounds() const {
 *         SkASSERT(!this->hasInval());
 *         return fBounds;
 *     }
 *
 *     bool hasInval() const { return fFlags & kInvalidated_Flag; }
 *
 *     // Dispatched on revalidation.  Subclasses are expected to recompute/cache their properties
 *     // and return their bounding box in local coordinates.
 *     virtual SkRect onRevalidate(InvalidationController*, const SkMatrix& ctm) = 0;
 *
 *     // Register/unregister |this| to receive invalidation events from a descendant.
 *     void observeInval(const sk_sp<Node>&);
 *     void unobserveInval(const sk_sp<Node>&);
 *
 * private:
 *     enum Flags {
 *         kInvalidated_Flag   = 1 << 0, // the node or its descendants require revalidation
 *         kDamage_Flag        = 1 << 1, // the node contributes damage during revalidation
 *         kObserverArray_Flag = 1 << 2, // the node has more than one inval observer
 *         kInTraversal_Flag   = 1 << 3, // the node is part of a traversal (cycle detection)
 *     };
 *
 *     template <typename Func>
 *     void forEachInvalObserver(Func&&) const;
 *
 *     class ScopedFlag;
 *
 *     union {
 *         Node*               fInvalObserver;
 *         std::vector<Node*>* fInvalObserverArray;
 *     };
 *     SkRect                  fBounds;
 *     const uint32_t          fInvalTraits :  2;
 *     uint32_t                fFlags       :  4; // Internal flags.
 *     uint32_t                fNodeFlags   :  8; // Accessible from select subclasses.
 *     // Free bits                         : 18;
 *
 *     friend class NodePriv;
 *     friend class RenderNode; // node flags access
 *
 *     using INHERITED = SkRefCnt;
 * }
 * ```
 */
public abstract class Node public constructor(
  invalTraits: UInt,
) : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * SkRect                  fBounds
   * ```
   */
  private var fBounds: Int = TODO("Initialize fBounds")

  /**
   * C++ original:
   * ```cpp
   * const uint32_t          fInvalTraits :  2
   * ```
   */
  private val fInvalTraits: UInt = TODO("Initialize fInvalTraits")

  /**
   * C++ original:
   * ```cpp
   * uint32_t                fFlags       :  4
   * ```
   */
  private var fFlags: UInt = TODO("Initialize fFlags")

  /**
   * C++ original:
   * ```cpp
   * uint32_t                fNodeFlags   :  8
   * ```
   */
  private var fNodeFlags: UInt = TODO("Initialize fNodeFlags")

  private var fInvalObserver: Node? = TODO("Initialize fInvalObserver")

  private var fInvalObserverArray: List<Node?>? = TODO("Initialize fInvalObserverArray")

  /**
   * C++ original:
   * ```cpp
   * const SkRect& Node::revalidate(InvalidationController* ic, const SkMatrix& ctm) {
   *     TRAVERSAL_GUARD fBounds;
   *
   *     if (!this->hasInval()) {
   *         return fBounds;
   *     }
   *
   *     const auto generate_damage =
   *             ic && ((fFlags & kDamage_Flag) || (fInvalTraits & kOverrideDamage_Trait));
   *     if (!generate_damage) {
   *         // Trivial transitive revalidation.
   *         fBounds = this->onRevalidate(ic, ctm);
   *     } else {
   *         // Revalidate and emit damage for old-bounds, new-bounds.
   *         const auto prev_bounds = fBounds;
   *
   *         auto* ic_override = (fInvalTraits & kOverrideDamage_Trait) ? nullptr : ic;
   *         fBounds = this->onRevalidate(ic_override, ctm);
   *
   *         ic->inval(prev_bounds, ctm);
   *         if (fBounds != prev_bounds) {
   *             ic->inval(fBounds, ctm);
   *         }
   *     }
   *
   *     fFlags &= ~(kInvalidated_Flag | kDamage_Flag);
   *
   *     return fBounds;
   * }
   * ```
   */
  public fun revalidate(ic: InvalidationController?, ctm: SkMatrix): Int {
    TODO("Implement revalidate")
  }

  /**
   * C++ original:
   * ```cpp
   * void Node::invalidate(bool damageBubbling) {
   *     TRAVERSAL_GUARD;
   *
   *     if (this->hasInval() && (!damageBubbling || (fFlags & kDamage_Flag))) {
   *         // All done.
   *         return;
   *     }
   *
   *     if (damageBubbling && !(fInvalTraits & kBubbleDamage_Trait)) {
   *         // Found a damage observer.
   *         fFlags |= kDamage_Flag;
   *         damageBubbling = false;
   *     }
   *
   *     fFlags |= kInvalidated_Flag;
   *
   *     forEachInvalObserver([&](Node* observer) {
   *         observer->invalidate(damageBubbling);
   *     });
   * }
   * ```
   */
  public fun invalidate(damage: Boolean = TODO()) {
    TODO("Implement invalidate")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkRect& bounds() const {
   *         SkASSERT(!this->hasInval());
   *         return fBounds;
   *     }
   * ```
   */
  protected fun bounds(): Int {
    TODO("Implement bounds")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hasInval() const { return fFlags & kInvalidated_Flag; }
   * ```
   */
  protected fun hasInval(): Boolean {
    TODO("Implement hasInval")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual SkRect onRevalidate(InvalidationController*, const SkMatrix& ctm) = 0
   * ```
   */
  protected abstract fun onRevalidate(param0: InvalidationController?, ctm: SkMatrix): Int

  /**
   * C++ original:
   * ```cpp
   * void Node::observeInval(const sk_sp<Node>& node) {
   *     SkASSERT(node);
   *     if (!(node->fFlags & kObserverArray_Flag)) {
   *         if (!node->fInvalObserver) {
   *             node->fInvalObserver = this;
   *             return;
   *         }
   *
   *         auto observers = new std::vector<Node*>();
   *         observers->reserve(2);
   *         observers->push_back(node->fInvalObserver);
   *
   *         node->fInvalObserverArray = observers;
   *         node->fFlags |= kObserverArray_Flag;
   *     }
   *
   *     // No duplicate observers.
   *     SkASSERT(std::find(node->fInvalObserverArray->begin(),
   *                        node->fInvalObserverArray->end(), this) == node->fInvalObserverArray->end());
   *
   *     node->fInvalObserverArray->push_back(this);
   * }
   * ```
   */
  protected fun observeInval(node: SkSp<Node>) {
    TODO("Implement observeInval")
  }

  /**
   * C++ original:
   * ```cpp
   * void Node::unobserveInval(const sk_sp<Node>& node) {
   *     SkASSERT(node);
   *     if (!(node->fFlags & kObserverArray_Flag)) {
   *         SkASSERT(node->fInvalObserver == this);
   *         node->fInvalObserver = nullptr;
   *         return;
   *     }
   *
   *     SkDEBUGCODE(const auto origSize = node->fInvalObserverArray->size());
   *     node->fInvalObserverArray->erase(std::remove(node->fInvalObserverArray->begin(),
   *                                                  node->fInvalObserverArray->end(), this),
   *                                      node->fInvalObserverArray->end());
   *     SkASSERT(node->fInvalObserverArray->size() == origSize - 1);
   * }
   * ```
   */
  protected fun unobserveInval(node: SkSp<Node>) {
    TODO("Implement unobserveInval")
  }

  /**
   * C++ original:
   * ```cpp
   * template <typename Func>
   * void Node::forEachInvalObserver(Func&& func) const {
   *     if (fFlags & kObserverArray_Flag) {
   *         for (const auto& parent : *fInvalObserverArray) {
   *             func(parent);
   *         }
   *         return;
   *     }
   *
   *     if (fInvalObserver) {
   *         func(fInvalObserver);
   *     }
   * }
   * ```
   */
  private fun <Func> forEachInvalObserver(func: Func) {
    TODO("Implement forEachInvalObserver")
  }

  public enum class InvalTraits {
    kBubbleDamage_Trait,
    kOverrideDamage_Trait,
  }

  public enum class Flags {
    kInvalidated_Flag,
    kDamage_Flag,
    kObserverArray_Flag,
    kInTraversal_Flag,
  }
}
