package org.skia.gpu

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class SK_API PrecompileBlender : public PrecompileBase {
 * public:
 *     // Provides access to functions that aren't part of the public API.
 *     PrecompileBlenderPriv priv();
 *     const PrecompileBlenderPriv priv() const;  // NOLINT(readability-const-return-type)
 *
 * protected:
 *     friend class PrecompileBlenderPriv;
 *
 *     virtual std::optional<SkBlendMode> asBlendMode() const { return {}; }
 *
 *     PrecompileBlender() : PrecompileBase(Type::kBlender) {}
 *     ~PrecompileBlender() override;
 * }
 * ```
 */
public open class PrecompileBlender public constructor() : PrecompileBase(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * PrecompileBlenderPriv priv()
   * ```
   */
  public override fun priv(): PrecompileBlenderPriv {
    TODO("Implement priv")
  }

  /**
   * C++ original:
   * ```cpp
   * const PrecompileBlenderPriv priv() const
   * ```
   */
  protected open fun asBlendMode(): Int {
    TODO("Implement asBlendMode")
  }
}
