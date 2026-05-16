package org.skia.tests

import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class PathOpsDebug {
 * public:
 *     static bool gJson;
 *     static bool gMarkJsonFlaky;
 *     static bool gOutFirst;
 *     static bool gCheckForDuplicateNames;
 *     static bool gOutputSVG;
 *     static FILE* gOut;
 * }
 * ```
 */
public open class PathOpsDebug {
  public companion object {
    public var gJson: Boolean = TODO("Initialize gJson")

    public var gMarkJsonFlaky: Boolean = TODO("Initialize gMarkJsonFlaky")

    public var gOutFirst: Boolean = TODO("Initialize gOutFirst")

    public var gCheckForDuplicateNames: Boolean = TODO("Initialize gCheckForDuplicateNames")

    public var gOutputSVG: Boolean = TODO("Initialize gOutputSVG")

    public var gOut: Int? = TODO("Initialize gOut")
  }
}
