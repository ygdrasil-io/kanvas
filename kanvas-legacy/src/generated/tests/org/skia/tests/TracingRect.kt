package org.skia.tests

import kotlin.Char
import kotlin.String
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * struct TracingRect : public TracingShape {
 *     TracingRect(SkRect rect) : fRect(rect) {}
 *     const char* typeName() override { return "TracingRect"; }
 *     SkString toString() override {
 *         return SkStringPrintf("Rect(%f, %f, %f, %f)",
 *                               fRect.fLeft, fRect.fTop, fRect.fRight, fRect.fBottom);
 *     }
 *
 *     SkRect fRect;
 * }
 * ```
 */
public open class TracingRect public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkRect fRect
   * ```
   */
  public var fRect: SkRect,
) : TracingShape(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * TracingRect(SkRect rect) : fRect(rect) {}
   * ```
   */
  public constructor(rect: SkRect) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* typeName() override { return "TracingRect"; }
   * ```
   */
  public override fun typeName(): Char {
    TODO("Implement typeName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString toString() override {
   *         return SkStringPrintf("Rect(%f, %f, %f, %f)",
   *                               fRect.fLeft, fRect.fTop, fRect.fRight, fRect.fBottom);
   *     }
   * ```
   */
  public override fun toString(): String {
    TODO("Implement toString")
  }
}
