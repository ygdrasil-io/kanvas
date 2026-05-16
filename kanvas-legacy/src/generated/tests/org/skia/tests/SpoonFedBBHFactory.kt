package org.skia.tests

import org.skia.core.SkBBHFactory
import org.skia.core.SkBBoxHierarchy
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class SpoonFedBBHFactory : public SkBBHFactory {
 * public:
 *     explicit SpoonFedBBHFactory(sk_sp<SkBBoxHierarchy> bbh) : fBBH(std::move(bbh)) {}
 *     sk_sp<SkBBoxHierarchy> operator()() const override {
 *         return fBBH;
 *     }
 * private:
 *     sk_sp<SkBBoxHierarchy> fBBH;
 * }
 * ```
 */
public open class SpoonFedBBHFactory public constructor(
  bbh: SkSp<SkBBoxHierarchy>,
) : SkBBHFactory() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkBBoxHierarchy> fBBH
   * ```
   */
  private var fBBH: SkSp<SkBBoxHierarchy> = TODO("Initialize fBBH")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkBBoxHierarchy> operator()() const override {
   *         return fBBH;
   *     }
   * ```
   */
  public override operator fun invoke(): SkSp<SkBBoxHierarchy> {
    TODO("Implement invoke")
  }
}
