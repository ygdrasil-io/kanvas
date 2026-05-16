package org.skia.core

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer
import org.skia.tests.BlenderType

/**
 * C++ original:
 * ```cpp
 * class SkBlendModeBlender : public SkBlenderBase {
 * public:
 *     explicit SkBlendModeBlender(SkBlendMode mode) : fMode(mode) {}
 *
 *     BlenderType type() const override { return BlenderType::kBlendMode; }
 *     SkBlendMode mode() const { return fMode; }
 *
 *     SK_FLATTENABLE_HOOKS(SkBlendModeBlender)
 *
 * private:
 *     using INHERITED = SkBlenderBase;
 *
 *     std::optional<SkBlendMode> asBlendMode() const final { return fMode; }
 *
 *     void flatten(SkWriteBuffer& buffer) const override;
 *
 *     bool onAppendStages(const SkStageRec& rec) const override;
 *
 *     SkBlendMode fMode;
 * }
 * ```
 */
public open class SkBlendModeBlender public constructor(
  mode: SkBlendMode,
) : SkBlenderBase() {
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
   * BlenderType type() const override { return BlenderType::kBlendMode; }
   * ```
   */
  public override fun type(): BlenderType {
    TODO("Implement type")
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
   * std::optional<SkBlendMode> asBlendMode() const final { return fMode; }
   * ```
   */
  public override fun asBlendMode(): Int {
    TODO("Implement asBlendMode")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBlendModeBlender::flatten(SkWriteBuffer& buffer) const {
   *     buffer.writeInt((int)fMode);
   * }
   * ```
   */
  public override fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkBlendModeBlender::onAppendStages(const SkStageRec& rec) const {
   *     SkBlendMode_AppendStages(fMode, rec.fPipeline);
   *     return true;
   * }
   * ```
   */
  public override fun onAppendStages(rec: SkStageRec): Boolean {
    TODO("Implement onAppendStages")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFlattenable> SkBlendModeBlender::CreateProc(SkReadBuffer& buffer) {
   *     SkBlendMode mode = buffer.read32LE(SkBlendMode::kLastMode);
   *     return SkBlender::Mode(mode);
   * }
   * ```
   */
  public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
    TODO("Implement createProc")
  }
}
