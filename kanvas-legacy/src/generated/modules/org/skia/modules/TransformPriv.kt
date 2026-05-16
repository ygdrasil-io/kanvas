package org.skia.modules

import kotlin.Boolean

/**
 * C++ original:
 * ```cpp
 * class TransformPriv final {
 * public:
 *
 *     static bool Is44(const sk_sp<Transform>&t) { return t->is44(); }
 *
 *     template <typename T, typename = std::enable_if<std::is_same<T, SkMatrix>::value ||
 *                                                     std::is_same<T, SkM44   >::value >>
 *     static T As(const sk_sp<Transform>&);
 *
 * private:
 *     TransformPriv() = delete;
 * }
 * ```
 */
public class TransformPriv public constructor() {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static bool Is44(const sk_sp<Transform>&t) { return t->is44(); }
     * ```
     */
    public fun is44(): Boolean {
      TODO("Implement is44")
    }
  }
}
