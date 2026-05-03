package org.skia.core

import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer

/**
 * C++ original:
 * ```cpp
 * class SkPairPathEffect : public SkPathEffectBase {
 * protected:
 *     SkPairPathEffect(sk_sp<SkPathEffect> pe0, sk_sp<SkPathEffect> pe1)
 *         : fPE0(std::move(pe0)), fPE1(std::move(pe1))
 *     {
 *         SkASSERT(fPE0.get());
 *         SkASSERT(fPE1.get());
 *     }
 *
 *     void flatten(SkWriteBuffer& buffer) const override {
 *         buffer.writeFlattenable(fPE0.get());
 *         buffer.writeFlattenable(fPE1.get());
 *     }
 *
 *     // these are visible to our subclasses
 *     sk_sp<SkPathEffect> fPE0;
 *     sk_sp<SkPathEffect> fPE1;
 *
 * private:
 *     using INHERITED = SkPathEffectBase;
 * }
 * ```
 */
public open class SkPairPathEffect public constructor(
  pe0: SkSp<SkPathEffect>,
  pe1: SkSp<SkPathEffect>,
) : SkPathEffectBase() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkPathEffect> fPE0
   * ```
   */
  protected var fPE0: SkSp<SkPathEffect> = TODO("Initialize fPE0")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkPathEffect> fPE1
   * ```
   */
  protected var fPE1: SkSp<SkPathEffect> = TODO("Initialize fPE1")

  /**
   * C++ original:
   * ```cpp
   * void flatten(SkWriteBuffer& buffer) const override {
   *         buffer.writeFlattenable(fPE0.get());
   *         buffer.writeFlattenable(fPE1.get());
   *     }
   * ```
   */
  protected override fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }
}

public typealias SkComposePathEffectINHERITED = SkPairPathEffect

public typealias SkSumPathEffectINHERITED = SkPairPathEffect
