package org.skia.tests

import kotlin.Unit

/**
 * C++ original:
 * ```cpp
 * class PathOpsThreadedRunnable {
 * public:
 *     PathOpsThreadedRunnable(void (*testFun)(PathOpsThreadState*), int a, int b, int c, int d,
 *             PathOpsThreadedTestRunner* runner) {
 *         fState.fA = (a & 0xFF);
 *         fState.fB = (b & 0xFF);
 *         fState.fC = (c & 0xFF);
 *         fState.fD = (d & 0xFF);
 *         fState.fReporter = runner->fReporter;
 *         fTestFun = testFun;
 *     }
 *
 *     PathOpsThreadedRunnable(void (*testFun)(PathOpsThreadState*), const char* str,
 *             PathOpsThreadedTestRunner* runner) {
 *         SkASSERT(strlen(str) < sizeof(fState.fSerialNo) - 1);
 *         strcpy(fState.fSerialNo, str);
 *         fState.fReporter = runner->fReporter;
 *         fTestFun = testFun;
 *     }
 *
 *     PathOpsThreadedRunnable(void (*testFun)(PathOpsThreadState*), int dirNo, const char* str,
 *             PathOpsThreadedTestRunner* runner) {
 *         SkASSERT(strlen(str) < sizeof(fState.fSerialNo) - 1);
 *         fState.fA = dirNo;
 *         strcpy(fState.fSerialNo, str);
 *         fState.fReporter = runner->fReporter;
 *         fTestFun = testFun;
 *     }
 *
 *     void operator()() {
 *         SkBitmap bitmap;
 *         fState.fBitmap = &bitmap;
 *         (*fTestFun)(&fState);
 *     }
 *
 * private:
 *     PathOpsThreadState fState;
 *     void (*fTestFun)(PathOpsThreadState*);
 * }
 * ```
 */
public data class PathOpsThreadedRunnable public constructor(
  /**
   * C++ original:
   * ```cpp
   * PathOpsThreadState fState
   * ```
   */
  private var fState: PathOpsThreadState,
  /**
   * C++ original:
   * ```cpp
   * void (*fTestFun)(PathOpsThreadState*)
   * ```
   */
  private var fTestFun: (PathOpsThreadState?) -> Unit,
) {
  /**
   * C++ original:
   * ```cpp
   * void operator()() {
   *         SkBitmap bitmap;
   *         fState.fBitmap = &bitmap;
   *         (*fTestFun)(&fState);
   *     }
   * ```
   */
  public operator fun invoke() {
    TODO("Implement invoke")
  }
}
