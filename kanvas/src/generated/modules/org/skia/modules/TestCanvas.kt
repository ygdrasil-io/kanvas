package org.skia.modules

import kotlin.Boolean
import kotlin.String
import kotlin.collections.List
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColor
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class TestCanvas {
 * public:
 *     TestCanvas(const char* testName) : name(testName) {
 *         bits.allocN32Pixels(TestCanvasWidth, TestCanvasHeight);
 *         canvas = new SkCanvas(bits);
 *         canvas->clear(SK_ColorWHITE);
 *     }
 *
 *     ~TestCanvas() {
 *         SkString tmpDir = skiatest::GetTmpDir();
 *         if (!tmpDir.isEmpty()) {
 *             SkString path = SkOSPath::Join(tmpDir.c_str(), name);
 *             SkFILEWStream file(path.c_str());
 *             if (!SkPngEncoder::Encode(&file, bits.pixmap(), {})) {
 *                 SkDebugf("Cannot write a picture %s\n", name);
 *             }
 *         }
 *         delete canvas;
 *     }
 *
 *     void drawRects(SkColor color, std::vector<TextBox>& result, bool fill = false) {
 *
 *         SkPaint paint;
 *         if (!fill) {
 *             paint.setStyle(SkPaint::kStroke_Style);
 *             paint.setAntiAlias(true);
 *             paint.setStrokeWidth(1);
 *         }
 *         paint.setColor(color);
 *         for (auto& r : result) {
 *             canvas->drawRect(r.rect, paint);
 *         }
 *     }
 *
 *     void drawLine(SkColor color, SkRect rect, bool vertical = true) {
 *
 *         SkPaint paint;
 *         paint.setStyle(SkPaint::kStroke_Style);
 *         paint.setAntiAlias(true);
 *         paint.setStrokeWidth(1);
 *         paint.setColor(color);
 *         if (vertical) {
 *             canvas->drawLine(rect.fLeft, rect.fTop, rect.fLeft, rect.fBottom, paint);
 *         } else {
 *             canvas->drawLine(rect.fLeft, rect.fTop, rect.fRight, rect.fTop, paint);
 *         }
 *     }
 *
 *     void drawLines(SkColor color, std::vector<TextBox>& result) {
 *
 *         for (auto& r : result) {
 *             drawLine(color, r.rect);
 *         }
 *     }
 *
 *     SkCanvas* get() { return canvas; }
 * private:
 *     SkBitmap bits;
 *     SkCanvas* canvas;
 *     const char* name;
 * }
 * ```
 */
public data class TestCanvas public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkBitmap bits
   * ```
   */
  private var bits: SkBitmap,
  /**
   * C++ original:
   * ```cpp
   * SkCanvas* canvas
   * ```
   */
  private var canvas: SkCanvas?,
  /**
   * C++ original:
   * ```cpp
   * const char* name
   * ```
   */
  private val name: String?,
) {
  /**
   * C++ original:
   * ```cpp
   * void drawRects(SkColor color, std::vector<TextBox>& result, bool fill = false) {
   *
   *         SkPaint paint;
   *         if (!fill) {
   *             paint.setStyle(SkPaint::kStroke_Style);
   *             paint.setAntiAlias(true);
   *             paint.setStrokeWidth(1);
   *         }
   *         paint.setColor(color);
   *         for (auto& r : result) {
   *             canvas->drawRect(r.rect, paint);
   *         }
   *     }
   * ```
   */
  public fun drawRects(
    color: SkColor,
    result: List<TextBox>,
    fill: Boolean = TODO(),
  ) {
    TODO("Implement drawRects")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawLine(SkColor color, SkRect rect, bool vertical = true) {
   *
   *         SkPaint paint;
   *         paint.setStyle(SkPaint::kStroke_Style);
   *         paint.setAntiAlias(true);
   *         paint.setStrokeWidth(1);
   *         paint.setColor(color);
   *         if (vertical) {
   *             canvas->drawLine(rect.fLeft, rect.fTop, rect.fLeft, rect.fBottom, paint);
   *         } else {
   *             canvas->drawLine(rect.fLeft, rect.fTop, rect.fRight, rect.fTop, paint);
   *         }
   *     }
   * ```
   */
  public fun drawLine(
    color: SkColor,
    rect: SkRect,
    vertical: Boolean = TODO(),
  ) {
    TODO("Implement drawLine")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawLines(SkColor color, std::vector<TextBox>& result) {
   *
   *         for (auto& r : result) {
   *             drawLine(color, r.rect);
   *         }
   *     }
   * ```
   */
  public fun drawLines(color: SkColor, result: List<TextBox>) {
    TODO("Implement drawLines")
  }

  /**
   * C++ original:
   * ```cpp
   * SkCanvas* get() { return canvas; }
   * ```
   */
  public fun `get`(): SkCanvas {
    TODO("Implement get")
  }
}
