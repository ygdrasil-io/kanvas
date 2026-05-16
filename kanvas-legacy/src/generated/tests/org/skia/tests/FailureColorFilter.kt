package org.skia.tests

import kotlin.Boolean
import kotlin.Char
import org.skia.core.SkStageRec
import org.skia.effects.SkColorFilterBase
import org.skia.modules.Factory

/**
 * C++ original:
 * ```cpp
 * struct FailureColorFilter final : public SkColorFilterBase {
 *     SkColorFilterBase::Type type() const override { return SkColorFilterBase::Type::kNoop; }
 *
 *     bool appendStages(const SkStageRec&, bool) const override { return false; }
 *
 *     // Only created here, should never be flattened / unflattened.
 *     Factory getFactory() const override { return nullptr; }
 *     const char* getTypeName() const override { return "FailureColorFilter"; }
 * }
 * ```
 */
public class FailureColorFilter : SkColorFilterBase() {
  /**
   * C++ original:
   * ```cpp
   * SkColorFilterBase::Type type() const override { return SkColorFilterBase::Type::kNoop; }
   * ```
   */
  public override fun type(): SkColorFilterBase.Type {
    TODO("Implement type")
  }

  /**
   * C++ original:
   * ```cpp
   * bool appendStages(const SkStageRec&, bool) const override { return false; }
   * ```
   */
  public override fun appendStages(param0: SkStageRec, param1: Boolean): Boolean {
    TODO("Implement appendStages")
  }

  /**
   * C++ original:
   * ```cpp
   * Factory getFactory() const override { return nullptr; }
   * ```
   */
  public override fun getFactory(): Factory {
    TODO("Implement getFactory")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* getTypeName() const override { return "FailureColorFilter"; }
   * ```
   */
  public override fun getTypeName(): Char {
    TODO("Implement getTypeName")
  }
}
