package org.skia.tests

import org.skia.math.SkMatrix

/**
 * C++ original:
 * ```cpp
 * struct Xforms {
 *     SkMatrix    fIM, fTM, fSM, fRM;
 *
 *     Xforms() {
 *         fIM.reset();
 *         fTM.setTranslate(10, 20);
 *         fSM.setScale(2, 3);
 *         fRM.setRotate(30);
 *     }
 * }
 * ```
 */
public data class Xforms public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkMatrix    fIM
   * ```
   */
  public var fIM: SkMatrix,
  /**
   * C++ original:
   * ```cpp
   * SkMatrix    fIM, fTM
   * ```
   */
  public var fTM: SkMatrix,
  /**
   * C++ original:
   * ```cpp
   * SkMatrix    fIM, fTM, fSM
   * ```
   */
  public var fSM: SkMatrix,
  /**
   * C++ original:
   * ```cpp
   * SkMatrix    fIM, fTM, fSM, fRM
   * ```
   */
  public var fRM: SkMatrix,
)
