package org.skia.tests

import kotlin.Boolean
import org.skia.core.SkCanvas

/**
 * C++ original:
 * ```cpp
 * class SimpleShapesGM : public ShapesGM {
 * public:
 *     SimpleShapesGM(bool antialias) : INHERITED("simpleshapes", antialias) {}
 *
 * private:
 *     void drawShapes(SkCanvas* canvas) const override {
 *         SkRandom rand(2);
 *         for (int i = 0; i < fShapes.size(); i++) {
 *             SkPaint paint(fPaint);
 *             paint.setColor(rand.nextU() & ~0x808080);
 *             paint.setAlphaf(0.5f);  // Use alpha to detect double blends.
 *             const SkRRect& shape = fShapes[i];
 *             canvas->save();
 *             canvas->rotate(fRotations[i]);
 *             switch (shape.getType()) {
 *                 case SkRRect::kRect_Type:
 *                     canvas->drawRect(shape.rect(), paint);
 *                     break;
 *                 case SkRRect::kOval_Type:
 *                     canvas->drawOval(shape.rect(), paint);
 *                     break;
 *                 default:
 *                     canvas->drawRRect(shape, paint);
 *                     break;
 *             }
 *             canvas->restore();
 *         }
 *     }
 *
 *     using INHERITED = ShapesGM;
 * }
 * ```
 */
public open class SimpleShapesGM public constructor(
  antialias: Boolean,
) : ShapesGM(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * void drawShapes(SkCanvas* canvas) const override {
   *         SkRandom rand(2);
   *         for (int i = 0; i < fShapes.size(); i++) {
   *             SkPaint paint(fPaint);
   *             paint.setColor(rand.nextU() & ~0x808080);
   *             paint.setAlphaf(0.5f);  // Use alpha to detect double blends.
   *             const SkRRect& shape = fShapes[i];
   *             canvas->save();
   *             canvas->rotate(fRotations[i]);
   *             switch (shape.getType()) {
   *                 case SkRRect::kRect_Type:
   *                     canvas->drawRect(shape.rect(), paint);
   *                     break;
   *                 case SkRRect::kOval_Type:
   *                     canvas->drawOval(shape.rect(), paint);
   *                     break;
   *                 default:
   *                     canvas->drawRRect(shape, paint);
   *                     break;
   *             }
   *             canvas->restore();
   *         }
   *     }
   * ```
   */
  public override fun drawShapes(canvas: SkCanvas?) {
    TODO("Implement drawShapes")
  }
}
