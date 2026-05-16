package org.skia.tests

import kotlin.Array
import kotlin.CharArray
import kotlin.Int
import org.skia.core.SkCanvas
import org.skia.foundation.SkData
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class TestAnnotationCanvas : public SkCanvas {
 *     skiatest::Reporter*     fReporter;
 *     const AnnotationRec*    fRec;
 *     int                     fCount;
 *     int                     fCurrIndex;
 *
 * public:
 *     TestAnnotationCanvas(skiatest::Reporter* reporter, const AnnotationRec rec[], int count)
 *         : SkCanvas(100, 100)
 *         , fReporter(reporter)
 *         , fRec(rec)
 *         , fCount(count)
 *         , fCurrIndex(0)
 *     {}
 *
 *     ~TestAnnotationCanvas() override {
 *         REPORTER_ASSERT(fReporter, fCount == fCurrIndex);
 *     }
 *
 * protected:
 *     void onDrawAnnotation(const SkRect& rect, const char key[], SkData* value) override {
 *         REPORTER_ASSERT(fReporter, fCurrIndex < fCount);
 *         REPORTER_ASSERT(fReporter, rect == fRec[fCurrIndex].fRect);
 *         REPORTER_ASSERT(fReporter, !strcmp(key, fRec[fCurrIndex].fKey));
 *         REPORTER_ASSERT(fReporter, value->equals(fRec[fCurrIndex].fValue.get()));
 *         fCurrIndex += 1;
 *     }
 * }
 * ```
 */
public open class TestAnnotationCanvas public constructor(
  reporter: Reporter?,
  rec: Array<AnnotationRec>,
  count: Int,
) : SkCanvas(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * skiatest::Reporter*     fReporter
   * ```
   */
  private var fReporter: Reporter? = TODO("Initialize fReporter")

  /**
   * C++ original:
   * ```cpp
   * const AnnotationRec*    fRec
   * ```
   */
  private val fRec: AnnotationRec? = TODO("Initialize fRec")

  /**
   * C++ original:
   * ```cpp
   * int                     fCount
   * ```
   */
  private var fCount: Int = TODO("Initialize fCount")

  /**
   * C++ original:
   * ```cpp
   * int                     fCurrIndex
   * ```
   */
  private var fCurrIndex: Int = TODO("Initialize fCurrIndex")

  /**
   * C++ original:
   * ```cpp
   * void onDrawAnnotation(const SkRect& rect, const char key[], SkData* value) override {
   *         REPORTER_ASSERT(fReporter, fCurrIndex < fCount);
   *         REPORTER_ASSERT(fReporter, rect == fRec[fCurrIndex].fRect);
   *         REPORTER_ASSERT(fReporter, !strcmp(key, fRec[fCurrIndex].fKey));
   *         REPORTER_ASSERT(fReporter, value->equals(fRec[fCurrIndex].fValue.get()));
   *         fCurrIndex += 1;
   *     }
   * ```
   */
  protected override fun onDrawAnnotation(
    rect: SkRect,
    key: CharArray,
    `value`: SkData?,
  ) {
    TODO("Implement onDrawAnnotation")
  }
}
