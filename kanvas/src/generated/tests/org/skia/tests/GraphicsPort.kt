package org.skia.tests

import kotlin.Float
import org.skia.core.SkCanvas
import org.skia.foundation.SkAlpha
import org.skia.foundation.SkColor
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class GraphicsPort {
 * protected:
 *     SkCanvas* fCanvas;
 *
 * public:
 *     GraphicsPort(SkCanvas* canvas) : fCanvas(canvas) {}
 *     virtual ~GraphicsPort() {}
 *
 *     void save() { fCanvas->save(); }
 *     void saveLayer(const SkRect& bounds, SkAlpha alpha) {
 *         fCanvas->saveLayerAlpha(&bounds, alpha);
 *     }
 *     void restore() { fCanvas->restore(); }
 *
 *     void translate(float x, float y) { fCanvas->translate(x, y); }
 *     void scale(float s) { fCanvas->scale(s, s); }
 *     void clip(const SkRect& r) { fCanvas->clipRect(r); }
 *
 *     void drawOval(const SkRect& r, SkColor c) {
 *         SkPaint p;
 *         p.setColor(c);
 *         fCanvas->drawOval(r, p);
 *     }
 *
 *     virtual void drawRect(const SkRect& r, SkColor c) {
 *         SkPaint p;
 *         p.setColor(c);
 *         fCanvas->drawRect(r, p);
 *     }
 *
 *     SkCanvas* peekCanvas() const { return fCanvas; }
 * }
 * ```
 */
public open class GraphicsPort public constructor(
  canvas: SkCanvas?,
) {
  /**
   * C++ original:
   * ```cpp
   * SkCanvas* fCanvas
   * ```
   */
  protected var fCanvas: SkCanvas? = TODO("Initialize fCanvas")

  /**
   * C++ original:
   * ```cpp
   * void save() { fCanvas->save(); }
   * ```
   */
  public fun save() {
    TODO("Implement save")
  }

  /**
   * C++ original:
   * ```cpp
   * void saveLayer(const SkRect& bounds, SkAlpha alpha) {
   *         fCanvas->saveLayerAlpha(&bounds, alpha);
   *     }
   * ```
   */
  public fun saveLayer(bounds: SkRect, alpha: SkAlpha) {
    TODO("Implement saveLayer")
  }

  /**
   * C++ original:
   * ```cpp
   * void restore() { fCanvas->restore(); }
   * ```
   */
  public fun restore() {
    TODO("Implement restore")
  }

  /**
   * C++ original:
   * ```cpp
   * void translate(float x, float y) { fCanvas->translate(x, y); }
   * ```
   */
  public fun translate(x: Float, y: Float) {
    TODO("Implement translate")
  }

  /**
   * C++ original:
   * ```cpp
   * void scale(float s) { fCanvas->scale(s, s); }
   * ```
   */
  public fun scale(s: Float) {
    TODO("Implement scale")
  }

  /**
   * C++ original:
   * ```cpp
   * void clip(const SkRect& r) { fCanvas->clipRect(r); }
   * ```
   */
  public fun clip(r: SkRect) {
    TODO("Implement clip")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawOval(const SkRect& r, SkColor c) {
   *         SkPaint p;
   *         p.setColor(c);
   *         fCanvas->drawOval(r, p);
   *     }
   * ```
   */
  public fun drawOval(r: SkRect, c: SkColor) {
    TODO("Implement drawOval")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void drawRect(const SkRect& r, SkColor c) {
   *         SkPaint p;
   *         p.setColor(c);
   *         fCanvas->drawRect(r, p);
   *     }
   * ```
   */
  public open fun drawRect(r: SkRect, c: SkColor) {
    TODO("Implement drawRect")
  }

  /**
   * C++ original:
   * ```cpp
   * SkCanvas* peekCanvas() const { return fCanvas; }
   * ```
   */
  public fun peekCanvas(): SkCanvas {
    TODO("Implement peekCanvas")
  }
}
