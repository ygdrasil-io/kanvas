package org.skia.tests

import kotlin.Array
import kotlin.Boolean
import kotlin.BooleanArray
import kotlin.Int
import org.skia.core.SkCanvas
import org.skia.foundation.SkSp
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class DebugTileRenderer : public ClipTileRenderer {
 * public:
 *
 *     static sk_sp<ClipTileRenderer> Make() {
 *         // Since aa override is disabled, the quad flags arg doesn't matter.
 *         return sk_sp<ClipTileRenderer>(new DebugTileRenderer(SkCanvas::kAll_QuadAAFlags, false));
 *     }
 *
 *     static sk_sp<ClipTileRenderer> MakeAA() {
 *         return sk_sp<ClipTileRenderer>(new DebugTileRenderer(SkCanvas::kAll_QuadAAFlags, true));
 *     }
 *
 *     static sk_sp<ClipTileRenderer> MakeNonAA() {
 *         return sk_sp<ClipTileRenderer>(new DebugTileRenderer(SkCanvas::kNone_QuadAAFlags, true));
 *     }
 *
 *     int drawTile(SkCanvas* canvas, const SkRect& rect, const SkPoint clip[4], const bool edgeAA[4],
 *                   int tileID, int quadID) override {
 *         // Colorize the tile based on its grid position and quad ID
 *         int i = tileID / kColCount;
 *         int j = tileID % kColCount;
 *
 *         SkColor4f c = {(i + 1.f) / kRowCount, (j + 1.f) / kColCount, .4f, 1.f};
 *         float alpha = quadID / 10.f;
 *         c.fR = c.fR * (1 - alpha) + alpha;
 *         c.fG = c.fG * (1 - alpha) + alpha;
 *         c.fB = c.fB * (1 - alpha) + alpha;
 *         c.fA = c.fA * (1 - alpha) + alpha;
 *
 *         SkCanvas::QuadAAFlags aaFlags = fEnableAAOverride ? fAAOverride : this->maskToFlags(edgeAA);
 *         canvas->experimental_DrawEdgeAAQuad(
 *                 rect, clip, aaFlags, c.toSkColor(), SkBlendMode::kSrcOver);
 *         return 1;
 *     }
 *
 *     void drawBanner(SkCanvas* canvas) override {
 *         draw_text(canvas, "Edge AA");
 *         canvas->translate(0.f, 15.f);
 *
 *         SkString config;
 *         constexpr char kFormat[] = "Ext(%s) - Int(%s)";
 *         if (fEnableAAOverride) {
 *             SkASSERT(fAAOverride == SkCanvas::kAll_QuadAAFlags ||
 *                      fAAOverride == SkCanvas::kNone_QuadAAFlags);
 *             if (fAAOverride == SkCanvas::kAll_QuadAAFlags) {
 *                 config.appendf(kFormat, "yes", "yes");
 *             } else {
 *                 config.appendf(kFormat, "no", "no");
 *             }
 *         } else {
 *             config.appendf(kFormat, "yes", "no");
 *         }
 *         draw_text(canvas, config.c_str());
 *     }
 *
 * private:
 *     SkCanvas::QuadAAFlags fAAOverride;
 *     bool fEnableAAOverride;
 *
 *     DebugTileRenderer(SkCanvas::QuadAAFlags aa, bool enableAAOverrde)
 *             : fAAOverride(aa)
 *             , fEnableAAOverride(enableAAOverrde) {}
 *
 *     using INHERITED = ClipTileRenderer;
 * }
 * ```
 */
public open class DebugTileRenderer public constructor(
  aa: SkCanvas.QuadAAFlags,
  enableAAOverrde: Boolean,
) : ClipTileRenderer() {
  /**
   * C++ original:
   * ```cpp
   * SkCanvas::QuadAAFlags fAAOverride
   * ```
   */
  private var fAAOverride: SkCanvas.QuadAAFlags = TODO("Initialize fAAOverride")

  /**
   * C++ original:
   * ```cpp
   * bool fEnableAAOverride
   * ```
   */
  private var fEnableAAOverride: Boolean = TODO("Initialize fEnableAAOverride")

  /**
   * C++ original:
   * ```cpp
   * int drawTile(SkCanvas* canvas, const SkRect& rect, const SkPoint clip[4], const bool edgeAA[4],
   *                   int tileID, int quadID) override {
   *         // Colorize the tile based on its grid position and quad ID
   *         int i = tileID / kColCount;
   *         int j = tileID % kColCount;
   *
   *         SkColor4f c = {(i + 1.f) / kRowCount, (j + 1.f) / kColCount, .4f, 1.f};
   *         float alpha = quadID / 10.f;
   *         c.fR = c.fR * (1 - alpha) + alpha;
   *         c.fG = c.fG * (1 - alpha) + alpha;
   *         c.fB = c.fB * (1 - alpha) + alpha;
   *         c.fA = c.fA * (1 - alpha) + alpha;
   *
   *         SkCanvas::QuadAAFlags aaFlags = fEnableAAOverride ? fAAOverride : this->maskToFlags(edgeAA);
   *         canvas->experimental_DrawEdgeAAQuad(
   *                 rect, clip, aaFlags, c.toSkColor(), SkBlendMode::kSrcOver);
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
   *         draw_text(canvas, "Edge AA");
   *         canvas->translate(0.f, 15.f);
   *
   *         SkString config;
   *         constexpr char kFormat[] = "Ext(%s) - Int(%s)";
   *         if (fEnableAAOverride) {
   *             SkASSERT(fAAOverride == SkCanvas::kAll_QuadAAFlags ||
   *                      fAAOverride == SkCanvas::kNone_QuadAAFlags);
   *             if (fAAOverride == SkCanvas::kAll_QuadAAFlags) {
   *                 config.appendf(kFormat, "yes", "yes");
   *             } else {
   *                 config.appendf(kFormat, "no", "no");
   *             }
   *         } else {
   *             config.appendf(kFormat, "yes", "no");
   *         }
   *         draw_text(canvas, config.c_str());
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
     * static sk_sp<ClipTileRenderer> Make() {
     *         // Since aa override is disabled, the quad flags arg doesn't matter.
     *         return sk_sp<ClipTileRenderer>(new DebugTileRenderer(SkCanvas::kAll_QuadAAFlags, false));
     *     }
     * ```
     */
    public fun make(): SkSp<ClipTileRenderer> {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<ClipTileRenderer> MakeAA() {
     *         return sk_sp<ClipTileRenderer>(new DebugTileRenderer(SkCanvas::kAll_QuadAAFlags, true));
     *     }
     * ```
     */
    public fun makeAA(): SkSp<ClipTileRenderer> {
      TODO("Implement makeAA")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<ClipTileRenderer> MakeNonAA() {
     *         return sk_sp<ClipTileRenderer>(new DebugTileRenderer(SkCanvas::kNone_QuadAAFlags, true));
     *     }
     * ```
     */
    public fun makeNonAA(): SkSp<ClipTileRenderer> {
      TODO("Implement makeNonAA")
    }
  }
}
