package org.skia.modules

import kotlin.Boolean
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class NodePriv final {
 * public:
 *
 *     static bool HasInval(const sk_sp<Node>& node) { return node->hasInval(); }
 *
 * private:
 *     NodePriv() = delete;
 * }
 * ```
 */
public class NodePriv public constructor() {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static bool HasInval(const sk_sp<Node>& node) { return node->hasInval(); }
     * ```
     */
    public fun hasInval(node: SkSp<Node>): Boolean {
      TODO("Implement hasInval")
    }
  }
}
