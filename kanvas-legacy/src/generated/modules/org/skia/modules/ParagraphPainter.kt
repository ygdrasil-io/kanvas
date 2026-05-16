package org.skia.modules

import kotlin.Int
import org.skia.core.SkTextBlob
import org.skia.foundation.SkColor
import org.skia.foundation.SkPath
import org.skia.foundation.SkSp
import org.skia.math.SkRect
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class ParagraphPainter {
 * public:
 *     typedef int PaintID;
 *     typedef std::variant<SkPaint, PaintID> SkPaintOrID;
 *
 *     struct DashPathEffect {
 *         DashPathEffect(SkScalar onLength, SkScalar offLength);
 *
 *         SkScalar fOnLength;
 *         SkScalar fOffLength;
 *     };
 *
 *     class DecorationStyle {
 *     public:
 *         DecorationStyle();
 *         DecorationStyle(SkColor color, SkScalar strokeWidth,
 *                         std::optional<DashPathEffect> dashPathEffect);
 *
 *         SkColor getColor() const { return fColor; }
 *         SkScalar getStrokeWidth() const { return fStrokeWidth; }
 *         std::optional<DashPathEffect> getDashPathEffect() const { return fDashPathEffect; }
 *         const SkPaint& skPaint() const { return fPaint; }
 *
 *     private:
 *         SkColor fColor;
 *         SkScalar fStrokeWidth;
 *         std::optional<DashPathEffect> fDashPathEffect;
 *         SkPaint fPaint;
 *     };
 *
 *     virtual ~ParagraphPainter() = default;
 *
 *     virtual void drawTextBlob(const sk_sp<SkTextBlob>& blob, SkScalar x, SkScalar y, const SkPaintOrID& paint) = 0;
 *     virtual void drawTextShadow(const sk_sp<SkTextBlob>& blob, SkScalar x, SkScalar y, SkColor color, SkScalar blurSigma) = 0;
 *     virtual void drawRect(const SkRect& rect, const SkPaintOrID& paint) = 0;
 *     virtual void drawFilledRect(const SkRect& rect, const DecorationStyle& decorStyle) = 0;
 *     virtual void drawPath(const SkPath& path, const DecorationStyle& decorStyle) = 0;
 *     virtual void drawLine(SkScalar x0, SkScalar y0, SkScalar x1, SkScalar y1, const DecorationStyle& decorStyle) = 0;
 *
 *     virtual void clipRect(const SkRect& rect) = 0;
 *     virtual void translate(SkScalar dx, SkScalar dy) = 0;
 *
 *     virtual void save() = 0;
 *     virtual void restore() = 0;
 * }
 * ```
 */
public abstract class ParagraphPainter {
  /**
   * C++ original:
   * ```cpp
   * virtual void drawTextBlob(const sk_sp<SkTextBlob>& blob, SkScalar x, SkScalar y, const SkPaintOrID& paint) = 0
   * ```
   */
  private abstract fun drawTextBlob(
    blob: SkSp<SkTextBlob>,
    x: SkScalar,
    y: SkScalar,
    paint: ParagraphPainterSkPaintOrID,
  )

  /**
   * C++ original:
   * ```cpp
   * virtual void drawTextShadow(const sk_sp<SkTextBlob>& blob, SkScalar x, SkScalar y, SkColor color, SkScalar blurSigma) = 0
   * ```
   */
  private abstract fun drawTextShadow(
    blob: SkSp<SkTextBlob>,
    x: SkScalar,
    y: SkScalar,
    color: SkColor,
    blurSigma: SkScalar,
  )

  /**
   * C++ original:
   * ```cpp
   * virtual void drawRect(const SkRect& rect, const SkPaintOrID& paint) = 0
   * ```
   */
  private abstract fun drawRect(rect: SkRect, paint: ParagraphPainterSkPaintOrID)

  /**
   * C++ original:
   * ```cpp
   * virtual void drawFilledRect(const SkRect& rect, const DecorationStyle& decorStyle) = 0
   * ```
   */
  private abstract fun drawFilledRect(rect: SkRect, decorStyle: DecorationStyle)

  /**
   * C++ original:
   * ```cpp
   * virtual void drawPath(const SkPath& path, const DecorationStyle& decorStyle) = 0
   * ```
   */
  private abstract fun drawPath(path: SkPath, decorStyle: DecorationStyle)

  /**
   * C++ original:
   * ```cpp
   * virtual void drawLine(SkScalar x0, SkScalar y0, SkScalar x1, SkScalar y1, const DecorationStyle& decorStyle) = 0
   * ```
   */
  private abstract fun drawLine(
    x0: SkScalar,
    y0: SkScalar,
    x1: SkScalar,
    y1: SkScalar,
    decorStyle: DecorationStyle,
  )

  /**
   * C++ original:
   * ```cpp
   * virtual void clipRect(const SkRect& rect) = 0
   * ```
   */
  private abstract fun clipRect(rect: SkRect)

  /**
   * C++ original:
   * ```cpp
   * virtual void translate(SkScalar dx, SkScalar dy) = 0
   * ```
   */
  private abstract fun translate(dx: SkScalar, dy: SkScalar)

  /**
   * C++ original:
   * ```cpp
   * virtual void save() = 0
   * ```
   */
  private abstract fun save()

  /**
   * C++ original:
   * ```cpp
   * virtual void restore() = 0
   * ```
   */
  private abstract fun restore()

  public data class DashPathEffect public constructor(
    public var fOnLength: Int,
    public var fOffLength: Int,
  )

  public data class DecorationStyle public constructor(
    private var fColor: Int,
    private var fStrokeWidth: Int,
    private var fDashPathEffect: DashPathEffect?,
    private var fPaint: Int,
  ) {
    public fun getColor(): Int {
      TODO("Implement getColor")
    }

    public fun getStrokeWidth(): Int {
      TODO("Implement getStrokeWidth")
    }

    public fun getDashPathEffect(): DashPathEffect? {
      TODO("Implement getDashPathEffect")
    }

    public fun skPaint(): Int {
      TODO("Implement skPaint")
    }
  }
}
