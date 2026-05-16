package org.skia.tests

import kotlin.Int
import kotlin.Unit

/**
 * C++ original:
 * ```cpp
 * struct PromiseImageChecker {
 *     PromiseImageChecker() = default;
 *
 *     void checkImageReleased(skiatest::Reporter* reporter, int expectedReleaseCnt) {
 *         REPORTER_ASSERT(reporter, expectedReleaseCnt == fImageReleaseCount);
 *     }
 *
 *     int fImageReleaseCount = 0;
 *
 *     static void ImageRelease(void* self) {
 *         auto checker = reinterpret_cast<PromiseImageChecker*>(self);
 *
 *         checker->fImageReleaseCount++;
 *     }
 * }
 * ```
 */
public data class PromiseImageChecker public constructor(
  /**
   * C++ original:
   * ```cpp
   * int fImageReleaseCount = 0
   * ```
   */
  public var fImageReleaseCount: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * void checkImageReleased(skiatest::Reporter* reporter, int expectedReleaseCnt) {
   *         REPORTER_ASSERT(reporter, expectedReleaseCnt == fImageReleaseCount);
   *     }
   * ```
   */
  public fun checkImageReleased(reporter: Reporter?, expectedReleaseCnt: Int) {
    TODO("Implement checkImageReleased")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static void ImageRelease(void* self) {
     *         auto checker = reinterpret_cast<PromiseImageChecker*>(self);
     *
     *         checker->fImageReleaseCount++;
     *     }
     * ```
     */
    public fun imageRelease(self: Unit?) {
      TODO("Implement imageRelease")
    }
  }
}
