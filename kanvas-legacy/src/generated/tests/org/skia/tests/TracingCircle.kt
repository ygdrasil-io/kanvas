package org.skia.tests

import kotlin.Char
import kotlin.String
import org.skia.math.SkPoint
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * struct TracingCircle : public TracingShape {
 *     TracingCircle(SkPoint center, SkScalar radius) : fCenter(center), fRadius(radius) {}
 *     const char* typeName() override { return "TracingCircle"; }
 *     SkString toString() override {
 *         return SkStringPrintf("Circle(%f, %f, %f)", fCenter.fX, fCenter.fY, fRadius);
 *     }
 * #if defined(SK_ANDROID_FRAMEWORK_USE_PERFETTO)
 *     void WriteIntoTrace(::perfetto::TracedValue context) {
 *         std::move(context).WriteString(toString().c_str());
 *     }
 * #endif
 *
 *     SkPoint fCenter;
 *     SkScalar fRadius;
 * }
 * ```
 */
public open class TracingCircle public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkPoint fCenter
   * ```
   */
  public var fCenter: SkPoint,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fRadius
   * ```
   */
  public var fRadius: SkScalar,
) : TracingShape(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * TracingCircle(SkPoint center, SkScalar radius) : fCenter(center), fRadius(radius) {}
   * ```
   */
  public constructor(center: SkPoint, radius: SkScalar) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* typeName() override { return "TracingCircle"; }
   * ```
   */
  public override fun typeName(): Char {
    TODO("Implement typeName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString toString() override {
   *         return SkStringPrintf("Circle(%f, %f, %f)", fCenter.fX, fCenter.fY, fRadius);
   *     }
   * ```
   */
  public override fun toString(): String {
    TODO("Implement toString")
  }
}
