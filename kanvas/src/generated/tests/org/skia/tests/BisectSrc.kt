package org.skia.tests

import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.tools.GraphiteTestContext

/**
 * C++ original:
 * ```cpp
 * class BisectSrc : public SKPSrc {
 * public:
 *     explicit BisectSrc(Path path, const char* trail);
 *
 *     Result draw(SkCanvas*, GraphiteTestContext*) const override;
 *
 * private:
 *     SkString fTrail;
 *
 *     using INHERITED = SKPSrc;
 * }
 * ```
 */
public open class BisectSrc public constructor(
  path: Path,
  trail: String?,
) : SKPSrc(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SkString fTrail
   * ```
   */
  private var fTrail: Int = TODO("Initialize fTrail")

  /**
   * C++ original:
   * ```cpp
   * Result BisectSrc::draw(SkCanvas* canvas, GraphiteTestContext* testContext) const {
   *     struct FoundPath {
   *         SkPath fPath;
   *         SkPaint fPaint;
   *         SkMatrix fViewMatrix;
   *     };
   *
   *     // This subclass of SkCanvas just extracts all the SkPaths (drawn via drawPath) from an SKP.
   *     class PathFindingCanvas : public SkCanvas {
   *     public:
   *         PathFindingCanvas(int width, int height) : SkCanvas(width, height, nullptr) {}
   *         const TArray<FoundPath>& foundPaths() const { return fFoundPaths; }
   *
   *     private:
   *         void onDrawPath(const SkPath& path, const SkPaint& paint) override {
   *             fFoundPaths.push_back() = {path, paint, this->getTotalMatrix()};
   *         }
   *
   *         TArray<FoundPath> fFoundPaths;
   *     };
   *
   *     PathFindingCanvas pathFinder(canvas->getBaseLayerSize().width(),
   *                                  canvas->getBaseLayerSize().height());
   *     Result result = this->INHERITED::draw(&pathFinder, testContext);
   *     if (!result.isOk()) {
   *         return result;
   *     }
   *
   *     int start = 0, end = pathFinder.foundPaths().size();
   *     for (const char* ch = fTrail.c_str(); *ch; ++ch) {
   *         int midpt = (start + end) / 2;
   *         if ('l' == *ch) {
   *             start = midpt;
   *         } else if ('r' == *ch) {
   *             end = midpt;
   *         }
   *     }
   *
   *     for (int i = start; i < end; ++i) {
   *         const FoundPath& path = pathFinder.foundPaths()[i];
   *         SkAutoCanvasRestore acr(canvas, true);
   *         canvas->concat(path.fViewMatrix);
   *         canvas->drawPath(path.fPath, path.fPaint);
   *     }
   *
   *     return Result::Ok();
   * }
   * ```
   */
  public override fun draw(canvas: SkCanvas?, testContext: GraphiteTestContext?): Result {
    TODO("Implement draw")
  }
}
