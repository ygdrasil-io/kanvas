package org.skia.tests

import kotlin.Array
import kotlin.BooleanArray
import kotlin.Int
import org.skia.core.SkCanvas
import org.skia.foundation.SkSp
import org.skia.math.SkPoint
import org.skia.math.SkRect
import SkColor4f as SkColor4f_
import undefined.SkColor4f as UndefinedSkColor4f

/**
 * C++ original:
 * ```cpp
 * class SolidColorRenderer : public ClipTileRenderer {
 * public:
 *
 *     static sk_sp<ClipTileRenderer> Make(const SkColor4f& color) {
 *         return sk_sp<ClipTileRenderer>(new SolidColorRenderer(color));
 *     }
 *
 *     int drawTile(SkCanvas* canvas, const SkRect& rect, const SkPoint clip[4], const bool edgeAA[4],
 *                   int tileID, int quadID) override {
 *         canvas->experimental_DrawEdgeAAQuad(rect, clip, this->maskToFlags(edgeAA),
 *                                             fColor.toSkColor(), SkBlendMode::kSrcOver);
 *         return 1;
 *     }
 *
 *     void drawBanner(SkCanvas* canvas) override {
 *         draw_text(canvas, "Solid Color");
 *     }
 *
 * private:
 *     SkColor4f fColor;
 *
 *     SolidColorRenderer(const SkColor4f& color) : fColor(color) {}
 *
 *     using INHERITED = ClipTileRenderer;
 * }
 * ```
 */
public open class SolidColorRenderer public constructor(
  color: UndefinedSkColor4f,
) : ClipTileRenderer() {
  /**
   * C++ original:
   * ```cpp
   * SkColor4f fColor
   * ```
   */
  private var fColor: UndefinedSkColor4f = TODO("Initialize fColor")

  /**
   * C++ original:
   * ```cpp
   * int drawTile(SkCanvas* canvas, const SkRect& rect, const SkPoint clip[4], const bool edgeAA[4],
   *                   int tileID, int quadID) override {
   *         canvas->experimental_DrawEdgeAAQuad(rect, clip, this->maskToFlags(edgeAA),
   *                                             fColor.toSkColor(), SkBlendMode::kSrcOver);
   *         return 1;
   *     }
   * ```
   */
  public override fun drawTile(
    canvas: SkCanvas?,
    rect: SkRect,
    clip: Array<SkPoint>,
    edgeAA: BooleanArray,
    tileID: Int,
    quadID: Int,
  ): Int {
    TODO("Implement drawTile")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawBanner(SkCanvas* canvas) override {
   *         draw_text(canvas, "Solid Color");
   *     }
   * ```
   */
  public override fun drawBanner(canvas: SkCanvas?) {
    TODO("Implement drawBanner")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<ClipTileRenderer> Make(const SkColor4f& color) {
     *         return sk_sp<ClipTileRenderer>(new SolidColorRenderer(color));
     *     }
     * ```
     */
    public fun make(color: SkColor4f_): SkSp<ClipTileRenderer> {
      TODO("Implement make")
    }
  }
}
