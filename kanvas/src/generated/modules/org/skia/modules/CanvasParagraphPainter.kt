package org.skia.modules

import kotlin.Int
import org.skia.core.SkCanvas
import org.skia.core.SkTextBlob
import org.skia.foundation.SkColor
import org.skia.foundation.SkPath
import org.skia.foundation.SkSp
import org.skia.math.SkRect
import org.skia.math.SkScalar
import undefined.DecorationStyle
import undefined.SkPaintOrID

/**
 * C++ original:
 * ```cpp
 * class CanvasParagraphPainter : public ParagraphPainter {
 * public:
 *     explicit CanvasParagraphPainter(SkCanvas* canvas);
 *
 *     void drawTextBlob(const sk_sp<SkTextBlob>& blob, SkScalar x, SkScalar y, const SkPaintOrID& paint) override;
 *     void drawTextShadow(const sk_sp<SkTextBlob>& blob, SkScalar x, SkScalar y, SkColor color, SkScalar blurSigma) override;
 *     void drawRect(const SkRect& rect, const SkPaintOrID& paint) override;
 *     void drawFilledRect(const SkRect& rect, const DecorationStyle& decorStyle) override;
 *     void drawPath(const SkPath& path, const DecorationStyle& decorStyle) override;
 *     void drawLine(SkScalar x0, SkScalar y0, SkScalar x1, SkScalar y1, const DecorationStyle& decorStyle) override;
 *
 *     void clipRect(const SkRect& rect) override;
 *     void translate(SkScalar dx, SkScalar dy) override;
 *
 *     void save() override;
 *     void restore() override;
 *
 * private:
 *     SkCanvas* fCanvas;
 * }
 * ```
 */
public open class CanvasParagraphPainter public constructor(
  canvas: SkCanvas?,
) : ParagraphPainter() {
  /**
   * C++ original:
   * ```cpp
   * SkCanvas* fCanvas
   * ```
   */
  private var fCanvas: Int? = TODO("Initialize fCanvas")

  /**
   * C++ original:
   * ```cpp
   * void CanvasParagraphPainter::drawTextBlob(const sk_sp<SkTextBlob>& blob, SkScalar x, SkScalar y, const SkPaintOrID& paint) {
   *     SkASSERT(std::holds_alternative<SkPaint>(paint));
   *     fCanvas->drawTextBlob(blob, x, y, std::get<SkPaint>(paint));
   * }
   * ```
   */
  public override fun drawTextBlob(
    blob: SkSp<SkTextBlob>,
    x: SkScalar,
    y: SkScalar,
    paint: SkPaintOrID,
  ) {
    TODO("Implement drawTextBlob")
  }

  /**
   * C++ original:
   * ```cpp
   * void CanvasParagraphPainter::drawTextShadow(const sk_sp<SkTextBlob>& blob, SkScalar x, SkScalar y, SkColor color, SkScalar blurSigma) {
   *     SkPaint paint;
   *     paint.setColor(color);
   *     if (blurSigma != 0.0) {
   *         sk_sp<SkMaskFilter> filter = SkMaskFilter::MakeBlur(
   *             kNormal_SkBlurStyle, blurSigma, false);
   *         paint.setMaskFilter(filter);
   *     }
   *     fCanvas->drawTextBlob(blob, x, y, paint);
   * }
   * ```
   */
  public override fun drawTextShadow(
    blob: SkSp<SkTextBlob>,
    x: SkScalar,
    y: SkScalar,
    color: SkColor,
    blurSigma: SkScalar,
  ) {
    TODO("Implement drawTextShadow")
  }

  /**
   * C++ original:
   * ```cpp
   * void CanvasParagraphPainter::drawRect(const SkRect& rect, const SkPaintOrID& paint) {
   *     SkASSERT(std::holds_alternative<SkPaint>(paint));
   *     fCanvas->drawRect(rect, std::get<SkPaint>(paint));
   * }
   * ```
   */
  public override fun drawRect(rect: SkRect, paint: SkPaintOrID) {
    TODO("Implement drawRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void CanvasParagraphPainter::drawFilledRect(const SkRect& rect, const DecorationStyle& decorStyle) {
   *     SkPaint p(decorStyle.skPaint());
   *     p.setStroke(false);
   *     fCanvas->drawRect(rect, p);
   * }
   * ```
   */
  public override fun drawFilledRect(rect: SkRect, decorStyle: DecorationStyle) {
    TODO("Implement drawFilledRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void CanvasParagraphPainter::drawPath(const SkPath& path, const DecorationStyle& decorStyle) {
   *     fCanvas->drawPath(path, decorStyle.skPaint());
   * }
   * ```
   */
  public override fun drawPath(path: SkPath, decorStyle: DecorationStyle) {
    TODO("Implement drawPath")
  }

  /**
   * C++ original:
   * ```cpp
   * void CanvasParagraphPainter::drawLine(SkScalar x0, SkScalar y0, SkScalar x1, SkScalar y1, const DecorationStyle& decorStyle) {
   *     fCanvas->drawLine(x0, y0, x1, y1, decorStyle.skPaint());
   * }
   * ```
   */
  public override fun drawLine(
    x0: SkScalar,
    y0: SkScalar,
    x1: SkScalar,
    y1: SkScalar,
    decorStyle: DecorationStyle,
  ) {
    TODO("Implement drawLine")
  }

  /**
   * C++ original:
   * ```cpp
   * void CanvasParagraphPainter::clipRect(const SkRect& rect) {
   *     fCanvas->clipRect(rect);
   * }
   * ```
   */
  public override fun clipRect(rect: SkRect) {
    TODO("Implement clipRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void CanvasParagraphPainter::translate(SkScalar dx, SkScalar dy) {
   *     fCanvas->translate(dx, dy);
   * }
   * ```
   */
  public override fun translate(dx: SkScalar, dy: SkScalar) {
    TODO("Implement translate")
  }

  /**
   * C++ original:
   * ```cpp
   * void CanvasParagraphPainter::save() {
   *     fCanvas->save();
   * }
   * ```
   */
  public override fun save() {
    TODO("Implement save")
  }

  /**
   * C++ original:
   * ```cpp
   * void CanvasParagraphPainter::restore() {
   *     fCanvas->restore();
   * }
   * ```
   */
  public override fun restore() {
    TODO("Implement restore")
  }
}
