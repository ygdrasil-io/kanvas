package org.skia.tests

import org.skia.core.SkCanvas

/**
 * C++ original:
 * ```cpp
 * class DrawEmptyPictureBBHTest : public PictureBBHTestBase {
 * public:
 *     DrawEmptyPictureBBHTest()
 *         : PictureBBHTestBase(2, 2, 1, 1) {}
 *     ~DrawEmptyPictureBBHTest() override {}
 *
 *     void doTest(SkCanvas&, SkCanvas&) override {}
 * }
 * ```
 */
public open class DrawEmptyPictureBBHTest public constructor() : PictureBBHTestBase(TODO(), TODO(), TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * void doTest(SkCanvas&, SkCanvas&) override {}
   * ```
   */
  public override fun doTest(param0: SkCanvas, param1: SkCanvas) {
    TODO("Implement doTest")
  }
}
