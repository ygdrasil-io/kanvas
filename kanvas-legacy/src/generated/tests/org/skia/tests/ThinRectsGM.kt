package org.skia.tests

import kotlin.Boolean
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class ThinRectsGM : public GM {
 * public:
 *     ThinRectsGM(bool round) : fRound(round) {
 *         this->setBGColor(0xFF000000);
 *     }
 *
 * protected:
 *     SkString getName() const override { return SkString(fRound ? "thinroundrects" : "thinrects"); }
 *
 *     SkISize getISize() override { return SkISize::Make(240, 320); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *
 *         SkPaint white;
 *         white.setColor(SK_ColorWHITE);
 *         white.setAntiAlias(true);
 *
 *         SkPaint green;
 *         green.setColor(SK_ColorGREEN);
 *         green.setAntiAlias(true);
 *
 *         for (int i = 0; i < 8; ++i) {
 *             canvas->save();
 *                 canvas->translate(i*0.125f, i*40.0f);
 *                 this->drawVertRects(canvas, white);
 *
 *                 canvas->translate(40.0f, 0.0f);
 *                 this->drawVertRects(canvas, green);
 *             canvas->restore();
 *
 *             canvas->save();
 *                 canvas->translate(80.0f, i*40.0f + i*0.125f);
 *                 this->drawHorizRects(canvas, white);
 *
 *                 canvas->translate(40.0f, 0.0f);
 *                 this->drawHorizRects(canvas, green);
 *             canvas->restore();
 *
 *             canvas->save();
 *                 canvas->translate(160.0f + i*0.125f,
 *                                   i*40.0f + i*0.125f);
 *                 this->drawSquares(canvas, white);
 *
 *                 canvas->translate(40.0f, 0.0f);
 *                 this->drawSquares(canvas, green);
 *             canvas->restore();
 *         }
 *     }
 *
 * private:
 *     void drawVertRects(SkCanvas* canvas, const SkPaint& p) {
 *         constexpr SkRect vertRects[] = {
 *             { 1,  1,    5.0f, 21 }, // 4 pix wide
 *             { 8,  1,   10.0f, 21 }, // 2 pix wide
 *             { 13, 1,   14.0f, 21 }, // 1 pix wide
 *             { 17, 1,   17.5f, 21 }, // 1/2 pix wide
 *             { 21, 1,  21.25f, 21 }, // 1/4 pix wide
 *             { 25, 1, 25.125f, 21 }, // 1/8 pix wide
 *             { 29, 1,   29.0f, 21 }  // 0 pix wide
 *         };
 *
 *         static constexpr SkVector radii[4] = {{1/32.f, 2/32.f}, {3/32.f, 1/32.f}, {2/32.f, 3/32.f},
 *                                               {1/32.f, 3/32.f}};
 *         SkRRect rrect;
 *         for (size_t j = 0; j < std::size(vertRects); ++j) {
 *             if (fRound) {
 *                 rrect.setRectRadii(vertRects[j], radii);
 *                 canvas->drawRRect(rrect, p);
 *             } else {
 *                 canvas->drawRect(vertRects[j], p);
 *             }
 *         }
 *     }
 *
 *     void drawHorizRects(SkCanvas* canvas, const SkPaint& p) {
 *         constexpr SkRect horizRects[] = {
 *             { 1, 1,  21,    5.0f }, // 4 pix high
 *             { 1, 8,  21,   10.0f }, // 2 pix high
 *             { 1, 13, 21,   14.0f }, // 1 pix high
 *             { 1, 17, 21,   17.5f }, // 1/2 pix high
 *             { 1, 21, 21,  21.25f }, // 1/4 pix high
 *             { 1, 25, 21, 25.125f }, // 1/8 pix high
 *             { 1, 29, 21,   29.0f }  // 0 pix high
 *         };
 *
 *         SkRRect rrect;
 *         for (size_t j = 0; j < std::size(horizRects); ++j) {
 *             if (fRound) {
 *                 rrect.setNinePatch(horizRects[j], 1/32.f, 2/32.f, 3/32.f, 4/32.f);
 *                 canvas->drawRRect(rrect, p);
 *             } else {
 *                 canvas->drawRect(horizRects[j], p);
 *             }
 *         }
 *     }
 *
 *     void drawSquares(SkCanvas* canvas, const SkPaint& p) {
 *         constexpr SkRect squares[] = {
 *             { 1,  1,     5.0f,    5.0f }, // 4 pix
 *             { 8,  8,    10.0f,   10.0f }, // 2 pix
 *             { 13, 13,   14.0f,   14.0f }, // 1 pix
 *             { 17, 17,   17.5f,   17.5f }, // 1/2 pix
 *             { 21, 21,  21.25f,  21.25f }, // 1/4 pix
 *             { 25, 25, 25.125f, 25.125f }, // 1/8 pix
 *             { 29, 29,   29.0f,   29.0f }  // 0 pix
 *         };
 *
 *         SkRRect rrect;
 *         for (size_t j = 0; j < std::size(squares); ++j) {
 *             if (fRound) {
 *                 rrect.setRectXY(squares[j], 1/32.f, 2/32.f);
 *                 canvas->drawRRect(rrect, p);
 *             } else {
 *                 canvas->drawRect(squares[j], p);
 *             }
 *         }
 *     }
 *
 *     const bool fRound;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class ThinRectsGM public constructor(
  round: Boolean,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * const bool fRound
   * ```
   */
  private val fRound: Boolean = TODO("Initialize fRound")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString(fRound ? "thinroundrects" : "thinrects"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(240, 320); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *
   *         SkPaint white;
   *         white.setColor(SK_ColorWHITE);
   *         white.setAntiAlias(true);
   *
   *         SkPaint green;
   *         green.setColor(SK_ColorGREEN);
   *         green.setAntiAlias(true);
   *
   *         for (int i = 0; i < 8; ++i) {
   *             canvas->save();
   *                 canvas->translate(i*0.125f, i*40.0f);
   *                 this->drawVertRects(canvas, white);
   *
   *                 canvas->translate(40.0f, 0.0f);
   *                 this->drawVertRects(canvas, green);
   *             canvas->restore();
   *
   *             canvas->save();
   *                 canvas->translate(80.0f, i*40.0f + i*0.125f);
   *                 this->drawHorizRects(canvas, white);
   *
   *                 canvas->translate(40.0f, 0.0f);
   *                 this->drawHorizRects(canvas, green);
   *             canvas->restore();
   *
   *             canvas->save();
   *                 canvas->translate(160.0f + i*0.125f,
   *                                   i*40.0f + i*0.125f);
   *                 this->drawSquares(canvas, white);
   *
   *                 canvas->translate(40.0f, 0.0f);
   *                 this->drawSquares(canvas, green);
   *             canvas->restore();
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawVertRects(SkCanvas* canvas, const SkPaint& p) {
   *         constexpr SkRect vertRects[] = {
   *             { 1,  1,    5.0f, 21 }, // 4 pix wide
   *             { 8,  1,   10.0f, 21 }, // 2 pix wide
   *             { 13, 1,   14.0f, 21 }, // 1 pix wide
   *             { 17, 1,   17.5f, 21 }, // 1/2 pix wide
   *             { 21, 1,  21.25f, 21 }, // 1/4 pix wide
   *             { 25, 1, 25.125f, 21 }, // 1/8 pix wide
   *             { 29, 1,   29.0f, 21 }  // 0 pix wide
   *         };
   *
   *         static constexpr SkVector radii[4] = {{1/32.f, 2/32.f}, {3/32.f, 1/32.f}, {2/32.f, 3/32.f},
   *                                               {1/32.f, 3/32.f}};
   *         SkRRect rrect;
   *         for (size_t j = 0; j < std::size(vertRects); ++j) {
   *             if (fRound) {
   *                 rrect.setRectRadii(vertRects[j], radii);
   *                 canvas->drawRRect(rrect, p);
   *             } else {
   *                 canvas->drawRect(vertRects[j], p);
   *             }
   *         }
   *     }
   * ```
   */
  private fun drawVertRects(canvas: SkCanvas?, p: SkPaint) {
    TODO("Implement drawVertRects")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawHorizRects(SkCanvas* canvas, const SkPaint& p) {
   *         constexpr SkRect horizRects[] = {
   *             { 1, 1,  21,    5.0f }, // 4 pix high
   *             { 1, 8,  21,   10.0f }, // 2 pix high
   *             { 1, 13, 21,   14.0f }, // 1 pix high
   *             { 1, 17, 21,   17.5f }, // 1/2 pix high
   *             { 1, 21, 21,  21.25f }, // 1/4 pix high
   *             { 1, 25, 21, 25.125f }, // 1/8 pix high
   *             { 1, 29, 21,   29.0f }  // 0 pix high
   *         };
   *
   *         SkRRect rrect;
   *         for (size_t j = 0; j < std::size(horizRects); ++j) {
   *             if (fRound) {
   *                 rrect.setNinePatch(horizRects[j], 1/32.f, 2/32.f, 3/32.f, 4/32.f);
   *                 canvas->drawRRect(rrect, p);
   *             } else {
   *                 canvas->drawRect(horizRects[j], p);
   *             }
   *         }
   *     }
   * ```
   */
  private fun drawHorizRects(canvas: SkCanvas?, p: SkPaint) {
    TODO("Implement drawHorizRects")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawSquares(SkCanvas* canvas, const SkPaint& p) {
   *         constexpr SkRect squares[] = {
   *             { 1,  1,     5.0f,    5.0f }, // 4 pix
   *             { 8,  8,    10.0f,   10.0f }, // 2 pix
   *             { 13, 13,   14.0f,   14.0f }, // 1 pix
   *             { 17, 17,   17.5f,   17.5f }, // 1/2 pix
   *             { 21, 21,  21.25f,  21.25f }, // 1/4 pix
   *             { 25, 25, 25.125f, 25.125f }, // 1/8 pix
   *             { 29, 29,   29.0f,   29.0f }  // 0 pix
   *         };
   *
   *         SkRRect rrect;
   *         for (size_t j = 0; j < std::size(squares); ++j) {
   *             if (fRound) {
   *                 rrect.setRectXY(squares[j], 1/32.f, 2/32.f);
   *                 canvas->drawRRect(rrect, p);
   *             } else {
   *                 canvas->drawRect(squares[j], p);
   *             }
   *         }
   *     }
   * ```
   */
  private fun drawSquares(canvas: SkCanvas?, p: SkPaint) {
    TODO("Implement drawSquares")
  }
}
