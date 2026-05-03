package org.skia.gpu

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class SK_API PrecompileMaskFilter : public PrecompileBase {
 * protected:
 *     friend class PaintOptions;  // for createPipelines() access
 *
 *     PrecompileMaskFilter() : PrecompileBase(Type::kMaskFilter) {}
 *     ~PrecompileMaskFilter() override;
 *
 *     void addToKey(const KeyContext&, int desiredCombination) const final;
 *
 *     virtual void createPipelines(const KeyContext&,
 *                                  const PaintOptions&,
 *                                  const RenderPassDesc&,
 *                                  const PaintOptions::ProcessCombination&) const = 0;
 * }
 * ```
 */
public abstract class PrecompileMaskFilter public constructor() : PrecompileBase(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * void PrecompileMaskFilter::addToKey(const KeyContext& keyContext, int desiredCombination) const {
   *     SkASSERT(false);
   * }
   * ```
   */
  protected fun addToKey(keyContext: KeyContext, desiredCombination: Int) {
    TODO("Implement addToKey")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void createPipelines(const KeyContext&,
   *                                  const PaintOptions&,
   *                                  const RenderPassDesc&,
   *                                  const PaintOptions::ProcessCombination&) const = 0
   * ```
   */
  protected abstract fun createPipelines(
    param0: KeyContext,
    param1: PaintOptions,
    param2: RenderPassDesc,
    param3: PaintOptionsProcessCombination,
  )
}
