package org.skia.tests

import kotlin.Boolean
import org.skia.core.SkCanvas

/**
 * C++ original:
 * ```cpp
 * class InnerShapesGM : public ShapesGM {
 * public:
 *     InnerShapesGM(bool antialias) : INHERITED("innershapes", antialias) {}
 *
 * private:
 *     void drawShapes(SkCanvas* canvas) const override {
 *         SkRandom rand;
 *         for (int i = 0; i < fShapes.size(); i++) {
 *             const SkRRect& outer = fShapes[i];
 *             const SkRRect& inner = fShapes[(i * 7 + 11) % fSimpleShapeCount];
 *             float s = 0.95f * std::min(outer.rect().width() / inner.rect().width(),
 *                                        outer.rect().height() / inner.rect().height());
 *             SkMatrix innerXform;
 *             float dx = (rand.nextF() - 0.5f) * (outer.rect().width() - s * inner.rect().width());
 *             float dy = (rand.nextF() - 0.5f) * (outer.rect().height() - s * inner.rect().height());
 *             // Fixup inner rects so they don't reach outside the outer rect.
 *             switch (i) {
 *                 case 0:
 *                     s *= .85f;
 *                     break;
 *                 case 8:
 *                     s *= .4f;
 *                     dx = dy = 0;
 *                     break;
 *                 case 5:
 *                     s *= .75f;
 *                     dx = dy = 0;
 *                     break;
 *                 case 6:
 *                     s *= .65f;
 *                     dx = -5;
 *                     dy = 10;
 *                     break;
 *             }
 *             innerXform.setTranslate(outer.rect().centerX() + dx, outer.rect().centerY() + dy);
 *             if (s < 1) {
 *                 innerXform.preScale(s, s);
 *             }
 *             innerXform.preTranslate(-inner.rect().centerX(), -inner.rect().centerY());
 *             auto xformedInner = inner.transform(innerXform).value_or(SkRRect());
 *             SkPaint paint(fPaint);
 *             paint.setColor(rand.nextU() & ~0x808080);
 *             paint.setAlphaf(0.5f);  // Use alpha to detect double blends.
 *             canvas->save();
 *             canvas->rotate(fRotations[i]);
 *             canvas->drawDRRect(outer, xformedInner, paint);
 *             canvas->restore();
 *         }
 *     }
 *
 *     using INHERITED = ShapesGM;
 * }
 * ```
 */
public open class InnerShapesGM public constructor(
  antialias: Boolean,
) : ShapesGM(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * void drawShapes(SkCanvas* canvas) const override {
   *         SkRandom rand;
   *         for (int i = 0; i < fShapes.size(); i++) {
   *             const SkRRect& outer = fShapes[i];
   *             const SkRRect& inner = fShapes[(i * 7 + 11) % fSimpleShapeCount];
   *             float s = 0.95f * std::min(outer.rect().width() / inner.rect().width(),
   *                                        outer.rect().height() / inner.rect().height());
   *             SkMatrix innerXform;
   *             float dx = (rand.nextF() - 0.5f) * (outer.rect().width() - s * inner.rect().width());
   *             float dy = (rand.nextF() - 0.5f) * (outer.rect().height() - s * inner.rect().height());
   *             // Fixup inner rects so they don't reach outside the outer rect.
   *             switch (i) {
   *                 case 0:
   *                     s *= .85f;
   *                     break;
   *                 case 8:
   *                     s *= .4f;
   *                     dx = dy = 0;
   *                     break;
   *                 case 5:
   *                     s *= .75f;
   *                     dx = dy = 0;
   *                     break;
   *                 case 6:
   *                     s *= .65f;
   *                     dx = -5;
   *                     dy = 10;
   *                     break;
   *             }
   *             innerXform.setTranslate(outer.rect().centerX() + dx, outer.rect().centerY() + dy);
   *             if (s < 1) {
   *                 innerXform.preScale(s, s);
   *             }
   *             innerXform.preTranslate(-inner.rect().centerX(), -inner.rect().centerY());
   *             auto xformedInner = inner.transform(innerXform).value_or(SkRRect());
   *             SkPaint paint(fPaint);
   *             paint.setColor(rand.nextU() & ~0x808080);
   *             paint.setAlphaf(0.5f);  // Use alpha to detect double blends.
   *             canvas->save();
   *             canvas->rotate(fRotations[i]);
   *             canvas->drawDRRect(outer, xformedInner, paint);
   *             canvas->restore();
   *         }
   *     }
   * ```
   */
  public override fun drawShapes(canvas: SkCanvas?) {
    TODO("Implement drawShapes")
  }
}
