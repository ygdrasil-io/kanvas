package org.skia.effects

import kotlin.Boolean
import kotlin.Int
import org.skia.core.SkStageRec
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColor
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer
import undefined.SkColor4f

/**
 * C++ original:
 * ```cpp
 * class SkBlendModeColorFilter final : public SkColorFilterBase {
 * public:
 *     SkBlendModeColorFilter(const SkColor4f& color, SkBlendMode mode);
 *
 *     bool appendStages(const SkStageRec& rec, bool shaderIsOpaque) const override;
 *
 *     bool onIsAlphaUnchanged() const override;
 *
 *     SkColorFilterBase::Type type() const override { return SkColorFilterBase::Type::kBlendMode; }
 *
 *     SkColor4f color() const { return fColor; }
 *     SkBlendMode mode() const { return fMode; }
 *
 * private:
 *     friend void ::SkRegisterModeColorFilterFlattenable();
 *     SK_FLATTENABLE_HOOKS(SkBlendModeColorFilter)
 *
 *     void flatten(SkWriteBuffer&) const override;
 *     bool onAsAColorMode(SkColor*, SkBlendMode*) const override;
 *
 *     SkColor4f fColor;  // always stored in sRGB
 *     SkBlendMode fMode;
 * }
 * ```
 */
public class SkBlendModeColorFilter public constructor(
  color: SkColor4f,
  mode: SkBlendMode,
) : SkColorFilterBase() {
  /**
   * C++ original:
   * ```cpp
   * SkColor4f fColor
   * ```
   */
  private var fColor: Int = TODO("Initialize fColor")

  /**
   * C++ original:
   * ```cpp
   * SkBlendMode fMode
   * ```
   */
  private var fMode: SkBlendMode = TODO("Initialize fMode")

  /**
   * C++ original:
   * ```cpp
   * bool SkBlendModeColorFilter::appendStages(const SkStageRec& rec, bool shaderIsOpaque) const {
   *     rec.fPipeline->append(SkRasterPipelineOp::move_src_dst);
   *     SkColor4f color = fColor;
   *     SkColorSpaceXformSteps(sk_srgb_singleton(), kUnpremul_SkAlphaType,
   *                            rec.fDstCS,          kPremul_SkAlphaType).apply(color.vec());
   *     rec.fPipeline->appendConstantColor(rec.fAlloc, color.vec());
   *     SkBlendMode_AppendStages(fMode, rec.fPipeline);
   *     return true;
   * }
   * ```
   */
  public override fun appendStages(rec: SkStageRec, shaderIsOpaque: Boolean): Boolean {
    TODO("Implement appendStages")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkBlendModeColorFilter::onIsAlphaUnchanged() const {
   *     switch (fMode) {
   *         case SkBlendMode::kDst:      //!< [Da, Dc]
   *         case SkBlendMode::kSrcATop:  //!< [Da, Sc * Da + (1 - Sa) * Dc]
   *             return true;
   *         default:
   *             break;
   *     }
   *     return false;
   * }
   * ```
   */
  public override fun onIsAlphaUnchanged(): Boolean {
    TODO("Implement onIsAlphaUnchanged")
  }

  /**
   * C++ original:
   * ```cpp
   * SkColorFilterBase::Type type() const override { return SkColorFilterBase::Type::kBlendMode; }
   * ```
   */
  public override fun type(): Int {
    TODO("Implement type")
  }

  /**
   * C++ original:
   * ```cpp
   * SkColor4f color() const { return fColor; }
   * ```
   */
  public fun color(): Int {
    TODO("Implement color")
  }

  /**
   * C++ original:
   * ```cpp
   * SkBlendMode mode() const { return fMode; }
   * ```
   */
  public fun mode(): SkBlendMode {
    TODO("Implement mode")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkBlendModeColorFilter::onAsAColorMode(SkColor* color, SkBlendMode* mode) const {
   *     if (color) {
   *         *color = fColor.toSkColor();
   *     }
   *     if (mode) {
   *         *mode = fMode;
   *     }
   *     return true;
   * }
   * ```
   */
  public override fun onAsAColorMode(color: SkColor?, mode: SkBlendMode?): Boolean {
    TODO("Implement onAsAColorMode")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBlendModeColorFilter::flatten(SkWriteBuffer& buffer) const {
   *     buffer.writeColor4f(fColor);
   *     buffer.writeUInt((int)fMode);
   * }
   * ```
   */
  public fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFlattenable> SkBlendModeColorFilter::CreateProc(SkReadBuffer& buffer) {
   *     if (buffer.isVersionLT(SkPicturePriv::kBlend4fColorFilter)) {
   *         // Color is 8-bit, sRGB
   *         SkColor color = buffer.readColor();
   *         SkBlendMode mode = (SkBlendMode)buffer.readUInt();
   *         return SkColorFilters::Blend(SkColor4f::FromColor(color), /*sRGB*/ nullptr, mode);
   *     } else {
   *         // Color is 32-bit, sRGB
   *         SkColor4f color;
   *         buffer.readColor4f(&color);
   *         SkBlendMode mode = (SkBlendMode)buffer.readUInt();
   *         return SkColorFilters::Blend(color, /*sRGB*/ nullptr, mode);
   *     }
   * }
   * ```
   */
  public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
    TODO("Implement createProc")
  }
}
