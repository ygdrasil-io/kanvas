package org.skia.effects

import kotlin.Boolean
import kotlin.Int
import org.skia.core.SkStageRec
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer

/**
 * C++ original:
 * ```cpp
 * class SkColorSpaceXformColorFilter final : public SkColorFilterBase {
 * public:
 *     SkColorSpaceXformColorFilter(sk_sp<SkColorSpace> src, sk_sp<SkColorSpace> dst);
 *
 *     bool appendStages(const SkStageRec& rec, bool shaderIsOpaque) const override;
 *
 *     SkColorFilterBase::Type type() const override {
 *         return SkColorFilterBase::Type::kColorSpaceXform;
 *     }
 *
 *     sk_sp<SkColorSpace> src() const { return fSrc; }
 *     sk_sp<SkColorSpace> dst() const { return fDst; }
 *
 * protected:
 *     void flatten(SkWriteBuffer& buffer) const override;
 *
 * private:
 *     friend void ::SkRegisterSkColorSpaceXformColorFilterFlattenable();
 *     SK_FLATTENABLE_HOOKS(SkColorSpaceXformColorFilter)
 *     static sk_sp<SkFlattenable> LegacyGammaOnlyCreateProc(SkReadBuffer& buffer);
 *
 *     const sk_sp<SkColorSpace> fSrc;
 *     const sk_sp<SkColorSpace> fDst;
 *     SkColorSpaceXformSteps fSteps;
 *
 *     friend class SkColorFilter;
 *     using INHERITED = SkColorFilterBase;
 * }
 * ```
 */
public class SkColorSpaceXformColorFilter public constructor(
  src: SkSp<SkColorSpace>,
  dst: SkSp<SkColorSpace>,
) : SkColorFilterBase() {
  /**
   * C++ original:
   * ```cpp
   * const sk_sp<SkColorSpace> fSrc
   * ```
   */
  private val fSrc: Int = TODO("Initialize fSrc")

  /**
   * C++ original:
   * ```cpp
   * const sk_sp<SkColorSpace> fDst
   * ```
   */
  private val fDst: Int = TODO("Initialize fDst")

  /**
   * C++ original:
   * ```cpp
   * SkColorSpaceXformSteps fSteps
   * ```
   */
  private var fSteps: Int = TODO("Initialize fSteps")

  /**
   * C++ original:
   * ```cpp
   * bool SkColorSpaceXformColorFilter::appendStages(const SkStageRec& rec, bool shaderIsOpaque) const {
   *     if (!shaderIsOpaque) {
   *         rec.fPipeline->append(SkRasterPipelineOp::unpremul);
   *     }
   *
   *     fSteps.apply(rec.fPipeline);
   *
   *     if (!shaderIsOpaque) {
   *         rec.fPipeline->append(SkRasterPipelineOp::premul);
   *     }
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
   * SkColorFilterBase::Type type() const override {
   *         return SkColorFilterBase::Type::kColorSpaceXform;
   *     }
   * ```
   */
  public override fun type(): Int {
    TODO("Implement type")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorSpace> src() const { return fSrc; }
   * ```
   */
  public fun src(): Int {
    TODO("Implement src")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorSpace> dst() const { return fDst; }
   * ```
   */
  public fun dst(): Int {
    TODO("Implement dst")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkColorSpaceXformColorFilter::flatten(SkWriteBuffer& buffer) const {
   *     buffer.writeDataAsByteArray(fSrc->serialize().get());
   *     buffer.writeDataAsByteArray(fDst->serialize().get());
   * }
   * ```
   */
  protected override fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFlattenable> SkColorSpaceXformColorFilter::LegacyGammaOnlyCreateProc(SkReadBuffer& buffer) {
   *     uint32_t dir = buffer.read32();
   *     if (!buffer.validate(dir <= 1)) {
   *         return nullptr;
   *     }
   *     if (dir == 0) {
   *         return SkColorFilters::LinearToSRGBGamma();
   *     }
   *     return SkColorFilters::SRGBToLinearGamma();
   * }
   * ```
   */
  public fun legacyGammaOnlyCreateProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
    TODO("Implement legacyGammaOnlyCreateProc")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFlattenable> SkColorSpaceXformColorFilter::CreateProc(SkReadBuffer& buffer) {
   *     sk_sp<SkColorSpace> colorSpaces[2];
   *     for (int i = 0; i < 2; ++i) {
   *         auto data = buffer.readByteArrayAsData();
   *         if (!buffer.validate(data != nullptr)) {
   *             return nullptr;
   *         }
   *         colorSpaces[i] = SkColorSpace::Deserialize(data->data(), data->size());
   *         if (!buffer.validate(colorSpaces[i] != nullptr)) {
   *             return nullptr;
   *         }
   *     }
   *     return sk_sp<SkFlattenable>(
   *             new SkColorSpaceXformColorFilter(std::move(colorSpaces[0]), std::move(colorSpaces[1])));
   * }
   * ```
   */
  public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
    TODO("Implement createProc")
  }
}
