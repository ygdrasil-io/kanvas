package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkColor
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class CGGraphicsPort : public GraphicsPort {
 * public:
 *     CGGraphicsPort(SkCanvas* canvas) : GraphicsPort(canvas) {}
 *
 *     void drawRect(const SkRect& r, SkColor c) override {
 *         CGContextRef cg = (CGContextRef)fCanvas->accessTopRasterHandle();
 *
 *         CGColorRef color = CGColorCreateGenericRGB(SkColorGetR(c)/255.f,
 *                                                    SkColorGetG(c)/255.f,
 *                                                    SkColorGetB(c)/255.f,
 *                                                    SkColorGetA(c)/255.f);
 *
 *         CGContextSetFillColorWithColor(cg, color);
 *         CGContextFillRect(cg, CGRectMake(r.x(), r.y(), r.width(), r.height()));
 *     }
 * }
 * ```
 */
public open class CGGraphicsPort public constructor(
  canvas: SkCanvas?,
) : GraphicsPort(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * void drawRect(const SkRect& r, SkColor c) override {
   *         CGContextRef cg = (CGContextRef)fCanvas->accessTopRasterHandle();
   *
   *         CGColorRef color = CGColorCreateGenericRGB(SkColorGetR(c)/255.f,
   *                                                    SkColorGetG(c)/255.f,
   *                                                    SkColorGetB(c)/255.f,
   *                                                    SkColorGetA(c)/255.f);
   *
   *         CGContextSetFillColorWithColor(cg, color);
   *         CGContextFillRect(cg, CGRectMake(r.x(), r.y(), r.width(), r.height()));
   *     }
   * ```
   */
  public override fun drawRect(r: SkRect, c: SkColor) {
    TODO("Implement drawRect")
  }
}

public typealias MyPort = CGGraphicsPort
