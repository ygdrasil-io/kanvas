package org.skia.core

/**
 * C++ original:
 * ```cpp
 * class RecorderImpl final : public skcpu::Recorder {
 * public:
 *     RecorderImpl(const ContextImpl* ctx) : fCtx(ctx) {}
 *
 *     const ContextImpl* ctx() const { return fCtx; }
 *
 * private:
 *     const ContextImpl* const fCtx;
 * }
 * ```
 */
public class RecorderImpl public constructor(
  ctx: ContextImpl?,
) : Recorder() {
  /**
   * C++ original:
   * ```cpp
   * const ContextImpl* const fCtx
   * ```
   */
  private val fCtx: ContextImpl? = TODO("Initialize fCtx")

  /**
   * C++ original:
   * ```cpp
   * const ContextImpl* ctx() const { return fCtx; }
   * ```
   */
  public fun ctx(): ContextImpl {
    TODO("Implement ctx")
  }
}
