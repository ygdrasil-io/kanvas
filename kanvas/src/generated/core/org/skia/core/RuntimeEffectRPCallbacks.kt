package org.skia.core

import kotlin.Boolean
import kotlin.Int
import kotlin.Unit
import org.skia.effects.SkRuntimeEffect
import org.skia.foundation.SkSpan
import org.skia.sksl.Callbacks

/**
 * C++ original:
 * ```cpp
 * class RuntimeEffectRPCallbacks : public SkSL::RP::Callbacks {
 * public:
 *     // SkStageRec::fPaintColor is used (strictly) to tint alpha-only image shaders with the paint
 *     // color. We want to suppress that behavior when they're sampled from runtime effects, so we
 *     // just override the paint color here. See also: SkImageShader::appendStages.
 *     RuntimeEffectRPCallbacks(const SkStageRec& s,
 *                              const SkShaders::MatrixRec& m,
 *                              SkSpan<const SkRuntimeEffect::ChildPtr> c,
 *                              SkSpan<const SkSL::SampleUsage> u)
 *             : fStage{s.fPipeline,
 *                      s.fAlloc,
 *                      s.fDstColorType,
 *                      s.fDstCS,
 *                      SkColors::kTransparent,
 *                      s.fSurfaceProps,
 *                      s.fDstBounds}
 *             , fMatrix(m)
 *             , fChildren(c)
 *             , fSampleUsages(u) {}
 *
 *     bool appendShader(int index) override;
 *     bool appendColorFilter(int index) override;
 *     bool appendBlender(int index) override;
 *
 *     // TODO: If an effect calls these intrinsics more than once, we could cache and re-use the steps
 *     // object(s), rather than re-creating them in the arena repeatedly.
 *     void toLinearSrgb(const void* color) override;
 *
 *     void fromLinearSrgb(const void* color) override;
 *
 * private:
 *     void applyColorSpaceXform(const SkColorSpaceXformSteps& tempXform, const void* color);
 *
 *     const SkStageRec fStage;
 *     const SkShaders::MatrixRec& fMatrix;
 *     SkSpan<const SkRuntimeEffect::ChildPtr> fChildren;
 *     SkSpan<const SkSL::SampleUsage> fSampleUsages;
 * }
 * ```
 */
public open class RuntimeEffectRPCallbacks public constructor(
  s: SkStageRec,
  m: MatrixRec,
  c: SkSpan<SkRuntimeEffect.ChildPtr>,
  u: SkSpan<SampleUsage>,
) : Callbacks() {
  /**
   * C++ original:
   * ```cpp
   * const SkStageRec fStage
   * ```
   */
  private val fStage: SkStageRec = TODO("Initialize fStage")

  /**
   * C++ original:
   * ```cpp
   * const SkShaders::MatrixRec& fMatrix
   * ```
   */
  private val fMatrix: MatrixRec = TODO("Initialize fMatrix")

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const SkRuntimeEffect::ChildPtr> fChildren
   * ```
   */
  private val fChildren: SkSpan<SkRuntimeEffect.ChildPtr> = TODO("Initialize fChildren")

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const SkSL::SampleUsage> fSampleUsages
   * ```
   */
  private val fSampleUsages: SkSpan<SampleUsage> = TODO("Initialize fSampleUsages")

  /**
   * C++ original:
   * ```cpp
   * bool RuntimeEffectRPCallbacks::appendShader(int index) {
   *     if (SkShader* shader = fChildren[index].shader()) {
   *         if (fSampleUsages[index].isPassThrough()) {
   *             // Given a passthrough sample, the total-matrix is still as valid as before.
   *             return as_SB(shader)->appendStages(fStage, fMatrix);
   *         }
   *         // For a non-passthrough sample, we need to explicitly mark the total-matrix as invalid.
   *         SkShaders::MatrixRec nonPassthroughMatrix = fMatrix;
   *         nonPassthroughMatrix.markTotalMatrixInvalid();
   *         return as_SB(shader)->appendStages(fStage, nonPassthroughMatrix);
   *     }
   *     // Return transparent black when a null shader is evaluated.
   *     fStage.fPipeline->appendConstantColor(fStage.fAlloc, SkColors::kTransparent);
   *     return true;
   * }
   * ```
   */
  public override fun appendShader(index: Int): Boolean {
    TODO("Implement appendShader")
  }

  /**
   * C++ original:
   * ```cpp
   * bool RuntimeEffectRPCallbacks::appendColorFilter(int index) {
   *     if (SkColorFilter* colorFilter = fChildren[index].colorFilter()) {
   *         return as_CFB(colorFilter)->appendStages(fStage, /*shaderIsOpaque=*/false);
   *     }
   *     // Return the original color as-is when a null child color filter is evaluated.
   *     return true;
   * }
   * ```
   */
  public override fun appendColorFilter(index: Int): Boolean {
    TODO("Implement appendColorFilter")
  }

  /**
   * C++ original:
   * ```cpp
   * bool RuntimeEffectRPCallbacks::appendBlender(int index) {
   *     if (SkBlender* blender = fChildren[index].blender()) {
   *         return as_BB(blender)->appendStages(fStage);
   *     }
   *     // Return a source-over blend when a null blender is evaluated.
   *     fStage.fPipeline->append(SkRasterPipelineOp::srcover);
   *     return true;
   * }
   * ```
   */
  public override fun appendBlender(index: Int): Boolean {
    TODO("Implement appendBlender")
  }

  /**
   * C++ original:
   * ```cpp
   * void RuntimeEffectRPCallbacks::toLinearSrgb(const void* color) {
   *     if (fStage.fDstCS) {
   *         SkColorSpaceXformSteps xform{fStage.fDstCS,              kUnpremul_SkAlphaType,
   *                                      sk_srgb_linear_singleton(), kUnpremul_SkAlphaType};
   *         if (xform.fFlags.mask()) {
   *             // We have a non-identity colorspace transform; apply it.
   *             this->applyColorSpaceXform(xform, color);
   *         }
   *     }
   * }
   * ```
   */
  public override fun toLinearSrgb(color: Unit?) {
    TODO("Implement toLinearSrgb")
  }

  /**
   * C++ original:
   * ```cpp
   * void RuntimeEffectRPCallbacks::fromLinearSrgb(const void* color) {
   *     if (fStage.fDstCS) {
   *         SkColorSpaceXformSteps xform{sk_srgb_linear_singleton(), kUnpremul_SkAlphaType,
   *                                      fStage.fDstCS,              kUnpremul_SkAlphaType};
   *         if (xform.fFlags.mask()) {
   *             // We have a non-identity colorspace transform; apply it.
   *             this->applyColorSpaceXform(xform, color);
   *         }
   *     }
   * }
   * ```
   */
  public override fun fromLinearSrgb(color: Unit?) {
    TODO("Implement fromLinearSrgb")
  }

  /**
   * C++ original:
   * ```cpp
   * void RuntimeEffectRPCallbacks::applyColorSpaceXform(const SkColorSpaceXformSteps& tempXform,
   *                                                     const void* color) {
   *     // Copy the transform steps into our alloc.
   *     SkColorSpaceXformSteps* xform = fStage.fAlloc->make<SkColorSpaceXformSteps>(tempXform);
   *
   *     // Put the color into src.rgba (and temporarily stash the execution mask there instead).
   *     fStage.fPipeline->append(SkRasterPipelineOp::exchange_src, color);
   *     // Add the color space transform to our raster pipeline.
   *     xform->apply(fStage.fPipeline);
   *     // Restore the execution mask, and move the color back into program data.
   *     fStage.fPipeline->append(SkRasterPipelineOp::exchange_src, color);
   * }
   * ```
   */
  private fun applyColorSpaceXform(tempXform: SkColorSpaceXformSteps, color: Unit?) {
    TODO("Implement applyColorSpaceXform")
  }
}
