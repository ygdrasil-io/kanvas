package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan
import undefined.ProcessCombination

/**
 * C++ original:
 * ```cpp
 * class PaintOptionsPriv {
 * public:
 *     using ProcessCombination = PaintOptions::ProcessCombination;
 *
 *     void addColorFilter(sk_sp<PrecompileColorFilter> cf);
 *
 *     void setClipShaders(SkSpan<const sk_sp<PrecompileShader>> clipShaders) {
 *         fPaintOptions->setClipShaders(std::move(clipShaders));
 *     }
 *
 *     void setPrimitiveBlendMode(SkBlendMode primitiveBlendMode) {
 *         fPaintOptions->setPrimitiveBlendMode(primitiveBlendMode);
 *     }
 *
 *     void setSkipColorXform(bool skipColorXform) {
 *         fPaintOptions->setSkipColorXform(skipColorXform);
 *     }
 *
 *     int numCombinations() const {
 *         return fPaintOptions->numCombinations();
 *     }
 *
 *     void buildCombinations(
 *             const KeyContext& keyContext,
 *             DrawTypeFlags drawTypes,
 *             bool withPrimitiveBlender,
 *             Coverage coverage,
 *             const RenderPassDesc& renderPassDesc,
 *             const ProcessCombination& processCombination) const {
 *         fPaintOptions->buildCombinations(keyContext, drawTypes, withPrimitiveBlender, coverage,
 *                                          renderPassDesc, processCombination);
 *     }
 *
 * private:
 *     friend class PaintOptions; // to construct/copy this type.
 *
 *     explicit PaintOptionsPriv(PaintOptions* paintOptions) : fPaintOptions(paintOptions) {}
 *
 *     PaintOptionsPriv& operator=(const PaintOptionsPriv&) = delete;
 *
 *     // No taking addresses of this type.
 *     const PaintOptionsPriv* operator&() const;
 *     PaintOptionsPriv *operator&();
 *
 *     PaintOptions* fPaintOptions;
 * }
 * ```
 */
public data class PaintOptionsPriv public constructor(
  /**
   * C++ original:
   * ```cpp
   * PaintOptions* fPaintOptions
   * ```
   */
  private var fPaintOptions: PaintOptionsPriv?,
) {
  /**
   * C++ original:
   * ```cpp
   * void PaintOptionsPriv::addColorFilter(sk_sp<PrecompileColorFilter> cf) {
   *     fPaintOptions->addColorFilter(std::move(cf));
   * }
   * ```
   */
  public fun addColorFilter(cf: SkSp<PrecompileColorFilter>) {
    TODO("Implement addColorFilter")
  }

  /**
   * C++ original:
   * ```cpp
   * void setClipShaders(SkSpan<const sk_sp<PrecompileShader>> clipShaders) {
   *         fPaintOptions->setClipShaders(std::move(clipShaders));
   *     }
   * ```
   */
  public fun setClipShaders(clipShaders: SkSpan<SkSp<PrecompileShader>>) {
    TODO("Implement setClipShaders")
  }

  /**
   * C++ original:
   * ```cpp
   * void setPrimitiveBlendMode(SkBlendMode primitiveBlendMode) {
   *         fPaintOptions->setPrimitiveBlendMode(primitiveBlendMode);
   *     }
   * ```
   */
  public fun setPrimitiveBlendMode(primitiveBlendMode: SkBlendMode) {
    TODO("Implement setPrimitiveBlendMode")
  }

  /**
   * C++ original:
   * ```cpp
   * void setSkipColorXform(bool skipColorXform) {
   *         fPaintOptions->setSkipColorXform(skipColorXform);
   *     }
   * ```
   */
  public fun setSkipColorXform(skipColorXform: Boolean) {
    TODO("Implement setSkipColorXform")
  }

  /**
   * C++ original:
   * ```cpp
   * int numCombinations() const {
   *         return fPaintOptions->numCombinations();
   *     }
   * ```
   */
  public fun numCombinations(): Int {
    TODO("Implement numCombinations")
  }

  /**
   * C++ original:
   * ```cpp
   * void buildCombinations(
   *             const KeyContext& keyContext,
   *             DrawTypeFlags drawTypes,
   *             bool withPrimitiveBlender,
   *             Coverage coverage,
   *             const RenderPassDesc& renderPassDesc,
   *             const ProcessCombination& processCombination) const {
   *         fPaintOptions->buildCombinations(keyContext, drawTypes, withPrimitiveBlender, coverage,
   *                                          renderPassDesc, processCombination);
   *     }
   * ```
   */
  public fun buildCombinations(
    keyContext: KeyContext,
    drawTypes: DrawTypeFlags,
    withPrimitiveBlender: Boolean,
    coverage: Coverage,
    renderPassDesc: RenderPassDesc,
    processCombination: ProcessCombination,
  ) {
    TODO("Implement buildCombinations")
  }

  /**
   * C++ original:
   * ```cpp
   * PaintOptionsPriv& operator=(const PaintOptionsPriv&) = delete
   * ```
   */
  private fun assign(param0: PaintOptionsPriv) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * const PaintOptionsPriv* operator&() const
   * ```
   */
  private fun addressOf(): PaintOptionsPriv {
    TODO("Implement addressOf")
  }

  /**
   * C++ original:
   * ```cpp
   * PaintOptionsPriv *operator&()
   * ```
   */
  public fun priv(): PaintOptionsPriv {
    TODO("Implement priv")
  }
}
