package org.skia.tests

import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize
import undefined.ClipTileRendererArray

/**
 * C++ original:
 * ```cpp
 * class CompositorGM : public skiagm::GM {
 * public:
 *     CompositorGM(const char* name, std::function<ClipTileRendererArray()> makeRendererFn)
 *             : fMakeRendererFn(std::move(makeRendererFn))
 *             , fName(name) {}
 *
 * protected:
 *     SkISize getISize() override {
 *         // Initialize the array of renderers.
 *         this->onceBeforeDraw();
 *
 *         // The GM draws a grid of renderers (rows) x transforms (col). Within each cell, the
 *         // renderer draws the transformed tile grid, which is approximately
 *         // (kColCount*kTileWidth, kRowCount*kTileHeight), although it has additional line
 *         // visualizations and can be transformed outside of those rectangular bounds (i.e. persp),
 *         // so pad the cell dimensions to be conservative. Must also account for the banner text.
 *         static constexpr SkScalar kCellWidth = 1.3f * kColCount * kTileWidth;
 *         static constexpr SkScalar kCellHeight = 1.3f * kRowCount * kTileHeight;
 *         return SkISize::Make(SkScalarRoundToInt(kCellWidth * kMatrixCount + 175.f),
 *                              SkScalarRoundToInt(kCellHeight * fRenderers.size() + 75.f));
 *     }
 *
 *     SkString getName() const override {
 *         SkString fullName;
 *         fullName.appendf("compositor_quads_%s", fName.c_str());
 *         return fullName;
 *     }
 *
 *     void onOnceBeforeDraw() override {
 *         fRenderers = fMakeRendererFn();
 *         this->configureMatrices();
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         static constexpr SkScalar kGap = 40.f;
 *         static constexpr SkScalar kBannerWidth = 120.f;
 *         static constexpr SkScalar kOffset = 15.f;
 *
 *         TArray<int> drawCounts(fRenderers.size());
 *         drawCounts.push_back_n(fRenderers.size(), 0);
 *
 *         canvas->save();
 *         canvas->translate(kOffset + kBannerWidth, kOffset);
 *         for (int i = 0; i < fMatrices.size(); ++i) {
 *             canvas->save();
 *             draw_text(canvas, fMatrixNames[i].c_str());
 *
 *             canvas->translate(0.f, kGap);
 *             for (int j = 0; j < fRenderers.size(); ++j) {
 *                 canvas->save();
 *                 draw_tile_boundaries(canvas, fMatrices[i]);
 *                 draw_clipping_boundaries(canvas, fMatrices[i]);
 *
 *                 canvas->concat(fMatrices[i]);
 *                 drawCounts[j] += fRenderers[j]->drawTiles(canvas);
 *
 *                 canvas->restore();
 *                 // And advance to the next row
 *                 canvas->translate(0.f, kGap + kRowCount * kTileHeight);
 *             }
 *             // Reset back to the left edge
 *             canvas->restore();
 *             // And advance to the next column
 *             canvas->translate(kGap + kColCount * kTileWidth, 0.f);
 *         }
 *         canvas->restore();
 *
 *         // Print a row header, with total draw counts
 *         canvas->save();
 *         canvas->translate(kOffset, kGap + 0.5f * kRowCount * kTileHeight);
 *         for (int j = 0; j < fRenderers.size(); ++j) {
 *             fRenderers[j]->drawBanner(canvas);
 *             canvas->translate(0.f, 15.f);
 *             draw_text(canvas, SkStringPrintf("Draws = %d", drawCounts[j]).c_str());
 *             canvas->translate(0.f, kGap + kRowCount * kTileHeight);
 *         }
 *         canvas->restore();
 *     }
 *
 * private:
 *     std::function<ClipTileRendererArray()> fMakeRendererFn;
 *     ClipTileRendererArray fRenderers;
 *     TArray<SkMatrix> fMatrices;
 *     TArray<SkString> fMatrixNames;
 *
 *     SkString fName;
 *
 *     void configureMatrices() {
 *         fMatrices.clear();
 *         fMatrixNames.clear();
 *         fMatrices.push_back_n(kMatrixCount);
 *
 *         // Identity
 *         fMatrices[0].setIdentity();
 *         fMatrixNames.push_back(SkString("Identity"));
 *
 *         // Translate/scale
 *         fMatrices[1].setTranslate(5.5f, 20.25f);
 *         fMatrices[1].postScale(.9f, .7f);
 *         fMatrixNames.push_back(SkString("T+S"));
 *
 *         // Rotation
 *         fMatrices[2].setRotate(20.0f);
 *         fMatrices[2].preTranslate(15.f, -20.f);
 *         fMatrixNames.push_back(SkString("Rotate"));
 *
 *         // Skew
 *         fMatrices[3].setSkew(.5f, .25f);
 *         fMatrices[3].preTranslate(-30.f, 0.f);
 *         fMatrixNames.push_back(SkString("Skew"));
 *
 *         // Perspective
 *         const std::array<SkPoint, 4> src = SkRect::MakeWH(kColCount * kTileWidth,
 *                                                           kRowCount * kTileHeight).toQuad();
 *         SkPoint dst[4] = {{0, 0},
 *                           {kColCount * kTileWidth + 10.f, 15.f},
 *                           {kColCount * kTileWidth - 28.f, kRowCount * kTileHeight + 40.f},
 *                           {25.f, kRowCount * kTileHeight - 15.f}};
 *         SkAssertResult(fMatrices[4].setPolyToPoly(src, dst));
 *         fMatrices[4].preTranslate(0.f, 10.f);
 *         fMatrixNames.push_back(SkString("Perspective"));
 *
 *         SkASSERT(fMatrices.size() == fMatrixNames.size());
 *     }
 *
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class CompositorGM public constructor(
  name: String?,
  makeRendererFn: () -> ClipTileRendererArray,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * std::function<ClipTileRendererArray()> fMakeRendererFn
   * ```
   */
  private var fMakeRendererFn: Int = TODO("Initialize fMakeRendererFn")

  /**
   * C++ original:
   * ```cpp
   * ClipTileRendererArray fRenderers
   * ```
   */
  private var fRenderers: Int = TODO("Initialize fRenderers")

  /**
   * C++ original:
   * ```cpp
   * TArray<SkMatrix> fMatrices
   * ```
   */
  private var fMatrices: Int = TODO("Initialize fMatrices")

  /**
   * C++ original:
   * ```cpp
   * TArray<SkString> fMatrixNames
   * ```
   */
  private var fMatrixNames: Int = TODO("Initialize fMatrixNames")

  /**
   * C++ original:
   * ```cpp
   * SkString fName
   * ```
   */
  private var fName: String = TODO("Initialize fName")

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override {
   *         // Initialize the array of renderers.
   *         this->onceBeforeDraw();
   *
   *         // The GM draws a grid of renderers (rows) x transforms (col). Within each cell, the
   *         // renderer draws the transformed tile grid, which is approximately
   *         // (kColCount*kTileWidth, kRowCount*kTileHeight), although it has additional line
   *         // visualizations and can be transformed outside of those rectangular bounds (i.e. persp),
   *         // so pad the cell dimensions to be conservative. Must also account for the banner text.
   *         static constexpr SkScalar kCellWidth = 1.3f * kColCount * kTileWidth;
   *         static constexpr SkScalar kCellHeight = 1.3f * kRowCount * kTileHeight;
   *         return SkISize::Make(SkScalarRoundToInt(kCellWidth * kMatrixCount + 175.f),
   *                              SkScalarRoundToInt(kCellHeight * fRenderers.size() + 75.f));
   *     }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override {
   *         SkString fullName;
   *         fullName.appendf("compositor_quads_%s", fName.c_str());
   *         return fullName;
   *     }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         fRenderers = fMakeRendererFn();
   *         this->configureMatrices();
   *     }
   * ```
   */
  protected override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         static constexpr SkScalar kGap = 40.f;
   *         static constexpr SkScalar kBannerWidth = 120.f;
   *         static constexpr SkScalar kOffset = 15.f;
   *
   *         TArray<int> drawCounts(fRenderers.size());
   *         drawCounts.push_back_n(fRenderers.size(), 0);
   *
   *         canvas->save();
   *         canvas->translate(kOffset + kBannerWidth, kOffset);
   *         for (int i = 0; i < fMatrices.size(); ++i) {
   *             canvas->save();
   *             draw_text(canvas, fMatrixNames[i].c_str());
   *
   *             canvas->translate(0.f, kGap);
   *             for (int j = 0; j < fRenderers.size(); ++j) {
   *                 canvas->save();
   *                 draw_tile_boundaries(canvas, fMatrices[i]);
   *                 draw_clipping_boundaries(canvas, fMatrices[i]);
   *
   *                 canvas->concat(fMatrices[i]);
   *                 drawCounts[j] += fRenderers[j]->drawTiles(canvas);
   *
   *                 canvas->restore();
   *                 // And advance to the next row
   *                 canvas->translate(0.f, kGap + kRowCount * kTileHeight);
   *             }
   *             // Reset back to the left edge
   *             canvas->restore();
   *             // And advance to the next column
   *             canvas->translate(kGap + kColCount * kTileWidth, 0.f);
   *         }
   *         canvas->restore();
   *
   *         // Print a row header, with total draw counts
   *         canvas->save();
   *         canvas->translate(kOffset, kGap + 0.5f * kRowCount * kTileHeight);
   *         for (int j = 0; j < fRenderers.size(); ++j) {
   *             fRenderers[j]->drawBanner(canvas);
   *             canvas->translate(0.f, 15.f);
   *             draw_text(canvas, SkStringPrintf("Draws = %d", drawCounts[j]).c_str());
   *             canvas->translate(0.f, kGap + kRowCount * kTileHeight);
   *         }
   *         canvas->restore();
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void configureMatrices() {
   *         fMatrices.clear();
   *         fMatrixNames.clear();
   *         fMatrices.push_back_n(kMatrixCount);
   *
   *         // Identity
   *         fMatrices[0].setIdentity();
   *         fMatrixNames.push_back(SkString("Identity"));
   *
   *         // Translate/scale
   *         fMatrices[1].setTranslate(5.5f, 20.25f);
   *         fMatrices[1].postScale(.9f, .7f);
   *         fMatrixNames.push_back(SkString("T+S"));
   *
   *         // Rotation
   *         fMatrices[2].setRotate(20.0f);
   *         fMatrices[2].preTranslate(15.f, -20.f);
   *         fMatrixNames.push_back(SkString("Rotate"));
   *
   *         // Skew
   *         fMatrices[3].setSkew(.5f, .25f);
   *         fMatrices[3].preTranslate(-30.f, 0.f);
   *         fMatrixNames.push_back(SkString("Skew"));
   *
   *         // Perspective
   *         const std::array<SkPoint, 4> src = SkRect::MakeWH(kColCount * kTileWidth,
   *                                                           kRowCount * kTileHeight).toQuad();
   *         SkPoint dst[4] = {{0, 0},
   *                           {kColCount * kTileWidth + 10.f, 15.f},
   *                           {kColCount * kTileWidth - 28.f, kRowCount * kTileHeight + 40.f},
   *                           {25.f, kRowCount * kTileHeight - 15.f}};
   *         SkAssertResult(fMatrices[4].setPolyToPoly(src, dst));
   *         fMatrices[4].preTranslate(0.f, 10.f);
   *         fMatrixNames.push_back(SkString("Perspective"));
   *
   *         SkASSERT(fMatrices.size() == fMatrixNames.size());
   *     }
   * ```
   */
  private fun configureMatrices() {
    TODO("Implement configureMatrices")
  }
}
