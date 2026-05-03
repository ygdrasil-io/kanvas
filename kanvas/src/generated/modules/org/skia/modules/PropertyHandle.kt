package org.skia.modules

import NodeT
import ValueT
import `internal`.TextAdapter
import `internal`.TransformAdapter2D
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * template <typename ValueT, typename NodeT>
 * class SK_API PropertyHandle final {
 * public:
 *     explicit PropertyHandle(sk_sp<NodeT>);
 *     PropertyHandle(sk_sp<NodeT> node, sk_sp<internal::SceneGraphRevalidator> revalidator)
 *         : fNode(std::move(node))
 *         , fRevalidator(std::move(revalidator)) {}
 *     ~PropertyHandle();
 *
 *     PropertyHandle(const PropertyHandle&);
 *
 *     ValueT get() const;
 *     void set(const ValueT&);
 *
 * private:
 *     const sk_sp<NodeT>                           fNode;
 *     const sk_sp<internal::SceneGraphRevalidator> fRevalidator;
 * }
 * ```
 */
public data class PropertyHandle<ValueT, NodeT> public constructor(
  /**
   * C++ original:
   * ```cpp
   * explicit PropertyHandle(sk_sp<NodeT>)
   * ```
   */
  public var skSp: PropertyHandle<ValueT, NodeT>,
  /**
   * C++ original:
   * ```cpp
   * const sk_sp<NodeT>                           fNode
   * ```
   */
  private val fNode: Int,
  /**
   * C++ original:
   * ```cpp
   * const sk_sp<internal::SceneGraphRevalidator> fRevalidator
   * ```
   */
  private val fRevalidator: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * ValueT get() const
   * ```
   */
  public fun `get`(): ValueT {
    TODO("Implement get")
  }

  /**
   * C++ original:
   * ```cpp
   * void set(const ValueT&)
   * ```
   */
  public fun `set`(param0: ValueT) {
    TODO("Implement set")
  }
}

public typealias OpacityPropertyHandle = PropertyHandle<OpacityPropertyValue, OpacityEffect>

public typealias TextPropertyHandle = PropertyHandle<TextPropertyValue, TextAdapter>

public typealias TransformPropertyHandle = PropertyHandle<TransformPropertyValue, TransformAdapter2D>
