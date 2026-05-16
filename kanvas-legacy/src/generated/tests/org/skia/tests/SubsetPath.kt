package org.skia.tests

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkPath

/**
 * C++ original:
 * ```cpp
 * class SubsetPath {
 * public:
 *     explicit SubsetPath(const SkPath& path);
 *     virtual ~SubsetPath() {}
 *     bool subset(bool testFailed, SkPath* sub);
 * protected:
 *     int range(int* end) const;
 *     virtual SkPath getSubsetPath() const = 0;
 *
 *     const SkPath& fPath;
 *     SkTDArray<bool> fSelected;
 *     int fSubset;
 *     int fTries;
 *
 * }
 * ```
 */
public abstract class SubsetPath public constructor(
  path: SkPath,
) {
  /**
   * C++ original:
   * ```cpp
   * const SkPath& fPath
   * ```
   */
  protected val fPath: Int = TODO("Initialize fPath")

  /**
   * C++ original:
   * ```cpp
   * SkTDArray<bool> fSelected
   * ```
   */
  protected var fSelected: Int = TODO("Initialize fSelected")

  /**
   * C++ original:
   * ```cpp
   * int fSubset
   * ```
   */
  protected var fSubset: Int = TODO("Initialize fSubset")

  /**
   * C++ original:
   * ```cpp
   * int fTries
   * ```
   */
  protected var fTries: Int = TODO("Initialize fTries")

  /**
   * C++ original:
   * ```cpp
   * bool SubsetPath::subset(bool testFailed, SkPath* sub) {
   *     int start, end;
   *     if (!testFailed) {
   *         start = range(&end);
   *         for (; start < end; ++start) {
   *             fSelected[start] = true;
   *         }
   *     }
   *     do {
   *         do {
   *             ++fSubset;
   *             start = range(&end);
   *  //           SkDebugf("%d s=%d e=%d t=%d\n", fSubset, start, end, fTries);
   *             if (end - start > 1) {
   *                 fTries = fSelected.size();
   *             } else if (end - start == 1) {
   *                 if (--fTries <= 0) {
   *                     return false;
   *                 }
   *             }
   *         } while (start == end);
   *     } while (!fSelected[start]);
   *     for (; start < end; ++start) {
   *         fSelected[start] = false;
   *     }
   * #if 1
   *     SkDebugf("selected: ");
   *     for (int index = 0; index < fSelected.size(); ++index) {
   *         SkDebugf("%c", fSelected[index] ? 'x' : '-');
   *     }
   * #endif
   *     *sub = getSubsetPath();
   *     return true;
   * }
   * ```
   */
  public fun subset(testFailed: Boolean, sub: SkPath?): Boolean {
    TODO("Implement subset")
  }

  /**
   * C++ original:
   * ```cpp
   * int SubsetPath::range(int* end) const {
   *     int leadingZero = SkCLZ(fSubset);
   *     int parts = 1 << (31 - leadingZero);
   *     int partIndex = fSubset - parts;
   *     SkASSERT(partIndex >= 0);
   *     int count = fSelected.size();
   *     int start = count * partIndex / parts;
   *     *end = count * (partIndex + 1) / parts;
   *     return start;
   * }
   * ```
   */
  protected fun range(end: Int?): Int {
    TODO("Implement range")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual SkPath getSubsetPath() const = 0
   * ```
   */
  protected abstract fun getSubsetPath(): Int
}
