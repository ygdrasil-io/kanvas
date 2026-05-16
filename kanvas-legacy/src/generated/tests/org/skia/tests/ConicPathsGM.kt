package org.skia.tests

import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkPath
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class ConicPathsGM : public skiagm::GM {
 * protected:
 *     SkString getName() const override { return SkString("conicpaths"); }
 *
 *     SkISize getISize() override { return SkISize::Make(920, 960); }
 *
 *     template <typename Proc> void append_path(Proc proc) {
 *         SkPathBuilder b;
 *         proc(&b);
 *         fPaths.push_back(b.detach());
 *     }
 *
 *     void onOnceBeforeDraw() override {
 *         this->append_path([](SkPathBuilder* conicCircle) {
 *             const SkScalar w = SkScalarSqrt(2)/2;
 *             conicCircle->moveTo(0, 0);
 *             conicCircle->conicTo(0, 50, 50, 50, w);
 *             conicCircle->rConicTo(50, 0, 50, -50, w);
 *             conicCircle->rConicTo(0, -50, -50, -50, w);
 *             conicCircle->rConicTo(-50, 0, -50, 50, w);
 *         });
 *
 *         this->append_path([](SkPathBuilder* hyperbola) {
 *             hyperbola->moveTo(0, 0);
 *             hyperbola->conicTo(0, 100, 100, 100, 2);
 *         });
 *
 *         this->append_path([](SkPathBuilder* thinHyperbola) {
 *             thinHyperbola->moveTo(0, 0);
 *             thinHyperbola->conicTo(100, 100, 5, 0, 2);
 *         });
 *
 *         this->append_path([](SkPathBuilder* veryThinHyperbola) {
 *             veryThinHyperbola->moveTo(0, 0);
 *             veryThinHyperbola->conicTo(100, 100, 1, 0, 2);
 *         });
 *
 *         this->append_path([](SkPathBuilder* closedHyperbola) {
 *             closedHyperbola->moveTo(0, 0);
 *             closedHyperbola->conicTo(100, 100, 0, 0, 2);
 *         });
 *
 *         this->append_path([](SkPathBuilder* nearParabola) {
 *             // using 1 as weight defaults to using quadTo
 *             nearParabola->moveTo(0, 0);
 *             nearParabola->conicTo(0, 100, 100, 100, 0.999f);
 *         });
 *
 *         this->append_path([](SkPathBuilder* thinEllipse) {
 *             thinEllipse->moveTo(0, 0);
 *             thinEllipse->conicTo(100, 100, 5, 0, SK_ScalarHalf);
 *         });
 *
 *         this->append_path([](SkPathBuilder* veryThinEllipse) {
 *             veryThinEllipse->moveTo(0, 0);
 *             veryThinEllipse->conicTo(100, 100, 1, 0, SK_ScalarHalf);
 *         });
 *
 *         this->append_path([](SkPathBuilder* closedEllipse) {
 *             closedEllipse->moveTo(0,  0);
 *             closedEllipse->conicTo(100, 100, 0, 0, SK_ScalarHalf);
 *         });
 *
 *         {
 *             SkPathBuilder b;
 *             const SkScalar w = SkScalarSqrt(2)/2;
 *             b.moveTo(2.1e+11f, -1.05e+11f);
 *             b.conicTo(2.1e+11f, 0, 1.05e+11f, 0, w);
 *             b.conicTo(0, 0, 0, -1.05e+11f, w);
 *             b.conicTo(0, -2.1e+11f, 1.05e+11f, -2.1e+11f, w);
 *             b.conicTo(2.1e+11f, -2.1e+11f, 2.1e+11f, -1.05e+11f, w);
 *             fGiantCircle = b.detach();
 *         }
 *     }
 *
 *     void drawGiantCircle(SkCanvas* canvas) {
 *         SkPaint paint;
 *         canvas->drawPath(fGiantCircle, paint);
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         const SkAlpha kAlphaValue[] = { 0xFF, 0x40 };
 *
 *         const SkScalar margin = 15;
 *         canvas->translate(margin, margin);
 *
 *         SkPaint paint;
 *         for (int p = 0; p < fPaths.size(); ++p) {
 *             canvas->save();
 *             for (size_t a = 0; a < std::size(kAlphaValue); ++a) {
 *                 paint.setARGB(kAlphaValue[a], 0, 0, 0);
 *                 for (int aa = 0; aa < 2; ++aa) {
 *                     paint.setAntiAlias(SkToBool(aa));
 *                     for (int fh = 0; fh < 2; ++fh) {
 *                         paint.setStroke(fh != 0);
 *
 *                         const SkRect& bounds = fPaths[p].getBounds();
 *                         canvas->save();
 *                         canvas->translate(-bounds.fLeft, -bounds.fTop);
 *                         canvas->drawPath(fPaths[p], paint);
 *                         canvas->restore();
 *
 *                         canvas->translate(110, 0);
 *                     }
 *                 }
 *             }
 *             canvas->restore();
 *             canvas->translate(0, 110);
 *         }
 *         canvas->restore();
 *
 *         this->drawGiantCircle(canvas);
 *     }
 *
 * private:
 *     TArray<SkPath> fPaths;
 *     SkPath           fGiantCircle;
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class ConicPathsGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * TArray<SkPath> fPaths
   * ```
   */
  private var fPaths: Int = TODO("Initialize fPaths")

  /**
   * C++ original:
   * ```cpp
   * SkPath           fGiantCircle
   * ```
   */
  private var fGiantCircle: SkPath = TODO("Initialize fGiantCircle")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("conicpaths"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(920, 960); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void append_path(Proc proc) {
   *         SkPathBuilder b;
   *         proc(&b);
   *         fPaths.push_back(b.detach());
   *     }
   * ```
   */
  protected fun appendPath(proc: Proc) {
    TODO("Implement appendPath")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         this->append_path([](SkPathBuilder* conicCircle) {
   *             const SkScalar w = SkScalarSqrt(2)/2;
   *             conicCircle->moveTo(0, 0);
   *             conicCircle->conicTo(0, 50, 50, 50, w);
   *             conicCircle->rConicTo(50, 0, 50, -50, w);
   *             conicCircle->rConicTo(0, -50, -50, -50, w);
   *             conicCircle->rConicTo(-50, 0, -50, 50, w);
   *         });
   *
   *         this->append_path([](SkPathBuilder* hyperbola) {
   *             hyperbola->moveTo(0, 0);
   *             hyperbola->conicTo(0, 100, 100, 100, 2);
   *         });
   *
   *         this->append_path([](SkPathBuilder* thinHyperbola) {
   *             thinHyperbola->moveTo(0, 0);
   *             thinHyperbola->conicTo(100, 100, 5, 0, 2);
   *         });
   *
   *         this->append_path([](SkPathBuilder* veryThinHyperbola) {
   *             veryThinHyperbola->moveTo(0, 0);
   *             veryThinHyperbola->conicTo(100, 100, 1, 0, 2);
   *         });
   *
   *         this->append_path([](SkPathBuilder* closedHyperbola) {
   *             closedHyperbola->moveTo(0, 0);
   *             closedHyperbola->conicTo(100, 100, 0, 0, 2);
   *         });
   *
   *         this->append_path([](SkPathBuilder* nearParabola) {
   *             // using 1 as weight defaults to using quadTo
   *             nearParabola->moveTo(0, 0);
   *             nearParabola->conicTo(0, 100, 100, 100, 0.999f);
   *         });
   *
   *         this->append_path([](SkPathBuilder* thinEllipse) {
   *             thinEllipse->moveTo(0, 0);
   *             thinEllipse->conicTo(100, 100, 5, 0, SK_ScalarHalf);
   *         });
   *
   *         this->append_path([](SkPathBuilder* veryThinEllipse) {
   *             veryThinEllipse->moveTo(0, 0);
   *             veryThinEllipse->conicTo(100, 100, 1, 0, SK_ScalarHalf);
   *         });
   *
   *         this->append_path([](SkPathBuilder* closedEllipse) {
   *             closedEllipse->moveTo(0,  0);
   *             closedEllipse->conicTo(100, 100, 0, 0, SK_ScalarHalf);
   *         });
   *
   *         {
   *             SkPathBuilder b;
   *             const SkScalar w = SkScalarSqrt(2)/2;
   *             b.moveTo(2.1e+11f, -1.05e+11f);
   *             b.conicTo(2.1e+11f, 0, 1.05e+11f, 0, w);
   *             b.conicTo(0, 0, 0, -1.05e+11f, w);
   *             b.conicTo(0, -2.1e+11f, 1.05e+11f, -2.1e+11f, w);
   *             b.conicTo(2.1e+11f, -2.1e+11f, 2.1e+11f, -1.05e+11f, w);
   *             fGiantCircle = b.detach();
   *         }
   *     }
   * ```
   */
  protected override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawGiantCircle(SkCanvas* canvas) {
   *         SkPaint paint;
   *         canvas->drawPath(fGiantCircle, paint);
   *     }
   * ```
   */
  protected fun drawGiantCircle(canvas: SkCanvas?) {
    TODO("Implement drawGiantCircle")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         const SkAlpha kAlphaValue[] = { 0xFF, 0x40 };
   *
   *         const SkScalar margin = 15;
   *         canvas->translate(margin, margin);
   *
   *         SkPaint paint;
   *         for (int p = 0; p < fPaths.size(); ++p) {
   *             canvas->save();
   *             for (size_t a = 0; a < std::size(kAlphaValue); ++a) {
   *                 paint.setARGB(kAlphaValue[a], 0, 0, 0);
   *                 for (int aa = 0; aa < 2; ++aa) {
   *                     paint.setAntiAlias(SkToBool(aa));
   *                     for (int fh = 0; fh < 2; ++fh) {
   *                         paint.setStroke(fh != 0);
   *
   *                         const SkRect& bounds = fPaths[p].getBounds();
   *                         canvas->save();
   *                         canvas->translate(-bounds.fLeft, -bounds.fTop);
   *                         canvas->drawPath(fPaths[p], paint);
   *                         canvas->restore();
   *
   *                         canvas->translate(110, 0);
   *                     }
   *                 }
   *             }
   *             canvas->restore();
   *             canvas->translate(0, 110);
   *         }
   *         canvas->restore();
   *
   *         this->drawGiantCircle(canvas);
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
