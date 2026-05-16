package org.skia.core

/**
 * C++ original:
 * ```cpp
 * class ContextImpl final : public Context {
 * public:
 *     ContextImpl() = default;
 *
 *     static const ContextImpl* TODO();
 * }
 * ```
 */
public class ContextImpl public constructor() : Context() {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * const ContextImpl* ContextImpl::TODO() {
     *     static const ContextImpl* gContext = static_cast<const ContextImpl*>(Context::Make().release());
     *     return gContext;
     * }
     * ```
     */
    public fun todo(): ContextImpl {
      TODO("Implement todo")
    }
  }
}
