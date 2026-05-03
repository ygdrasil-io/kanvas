package org.skia.effects

import kotlin.Boolean
import kotlin.Int
import org.skia.core.SkStageRec
import org.skia.foundation.SkColorFilter
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer

/**
 * C++ original:
 * ```cpp
 * class SkComposeColorFilter final : public SkColorFilterBase {
 * public:
 *     bool onIsAlphaUnchanged() const override;
 *
 *     bool appendStages(const SkStageRec& rec, bool shaderIsOpaque) const override;
 *
 *     SkColorFilterBase::Type type() const override { return SkColorFilterBase::Type::kCompose; }
 *
 *     sk_sp<SkColorFilterBase> outer() const { return fOuter; }
 *     sk_sp<SkColorFilterBase> inner() const { return fInner; }
 *
 * protected:
 *     void flatten(SkWriteBuffer& buffer) const override;
 *
 * private:
 *     friend void ::SkRegisterComposeColorFilterFlattenable();
 *     SK_FLATTENABLE_HOOKS(SkComposeColorFilter)
 *
 *     SkComposeColorFilter(sk_sp<SkColorFilter> outer, sk_sp<SkColorFilter> inner);
 *
 *     sk_sp<SkColorFilterBase> fOuter;
 *     sk_sp<SkColorFilterBase> fInner;
 *
 *     friend class SkColorFilter;
 *
 *     using INHERITED = SkColorFilter;
 * }
 * ```
 */
public class SkComposeColorFilter public constructor(
  outer: SkSp<SkColorFilter>,
  `inner`: SkSp<SkColorFilter>,
) : SkColorFilterBase() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorFilterBase> fOuter
   * ```
   */
  private var fOuter: Int = TODO("Initialize fOuter")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorFilterBase> fInner
   * ```
   */
  private var fInner: Int = TODO("Initialize fInner")

  /**
   * C++ original:
   * ```cpp
   * bool SkComposeColorFilter::onIsAlphaUnchanged() const {
   *     // Can only claim alphaunchanged support if both our proxys do.
   *     return fOuter->isAlphaUnchanged() && fInner->isAlphaUnchanged();
   * }
   * ```
   */
  public override fun onIsAlphaUnchanged(): Boolean {
    TODO("Implement onIsAlphaUnchanged")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkComposeColorFilter::appendStages(const SkStageRec& rec, bool shaderIsOpaque) const {
   *     bool innerIsOpaque = shaderIsOpaque;
   *     if (!fInner->isAlphaUnchanged()) {
   *         innerIsOpaque = false;
   *     }
   *     return fInner->appendStages(rec, shaderIsOpaque) && fOuter->appendStages(rec, innerIsOpaque);
   * }
   * ```
   */
  public override fun appendStages(rec: SkStageRec, shaderIsOpaque: Boolean): Boolean {
    TODO("Implement appendStages")
  }

  /**
   * C++ original:
   * ```cpp
   * SkColorFilterBase::Type type() const override { return SkColorFilterBase::Type::kCompose; }
   * ```
   */
  public override fun type(): Int {
    TODO("Implement type")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorFilterBase> outer() const { return fOuter; }
   * ```
   */
  public fun outer(): Int {
    TODO("Implement outer")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorFilterBase> inner() const { return fInner; }
   * ```
   */
  public fun `inner`(): Int {
    TODO("Implement inner")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkComposeColorFilter::flatten(SkWriteBuffer& buffer) const {
   *     buffer.writeFlattenable(fOuter.get());
   *     buffer.writeFlattenable(fInner.get());
   * }
   * ```
   */
  protected override fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFlattenable> SkComposeColorFilter::CreateProc(SkReadBuffer& buffer) {
   *     sk_sp<SkColorFilter> outer(buffer.readColorFilter());
   *     sk_sp<SkColorFilter> inner(buffer.readColorFilter());
   *     return outer ? outer->makeComposed(std::move(inner)) : inner;
   * }
   * ```
   */
  public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
    TODO("Implement createProc")
  }
}
