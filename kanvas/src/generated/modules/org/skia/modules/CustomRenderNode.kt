package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.collections.List
import org.skia.foundation.SkSp

public typealias BulgeNodeINHERITED = CustomRenderNode

public typealias DisplacementNodeINHERITED = CustomRenderNode

public typealias FractalNoiseNodeINHERITED = CustomRenderNode

public typealias TileRenderNodeINHERITED = CustomRenderNode

public typealias RWipeRenderNodeINHERITED = CustomRenderNode

public typealias SkSLShaderNodeINHERITED = CustomRenderNode

public typealias SphereNodeINHERITED = CustomRenderNode

public typealias RepeaterRenderNodeINHERITED = CustomRenderNode

/**
 * C++ original:
 * ```cpp
 * class CustomRenderNode : public RenderNode {
 * protected:
 *     explicit CustomRenderNode(std::vector<sk_sp<RenderNode>>&& children);
 *     ~CustomRenderNode() override;
 *
 *     const std::vector<sk_sp<RenderNode>>& children() const { return fChildren; }
 *
 *     bool hasChildrenInval() const;
 *
 * private:
 *     std::vector<sk_sp<RenderNode>> fChildren;
 *
 *     using INHERITED = RenderNode;
 * }
 * ```
 */
public open class CustomRenderNode public constructor(
  children: List<SkSp<RenderNode>>,
) : RenderNode(TODO()) {
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
   * const std::vector<sk_sp<RenderNode>>& children() const { return fChildren; }
   * ```
   */
  protected fun children(): Int {
    TODO("Implement children")
  }

  /**
   * C++ original:
   * ```cpp
   * bool CustomRenderNode::hasChildrenInval() const {
   *     for (const auto& child : fChildren) {
   *         if (NodePriv::HasInval(child)) {
   *             return true;
   *         }
   *     }
   *
   *     return false;
   * }
   * ```
   */
  protected fun hasChildrenInval(): Boolean {
    TODO("Implement hasChildrenInval")
  }
}
