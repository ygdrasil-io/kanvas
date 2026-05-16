package org.skia.tests

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.ULong
import org.skia.core.SkCanvas
import org.skia.math.SkISize
import org.skia.math.SkPathDirection
import org.skia.math.SkPoint

/**
 * C++ original:
 * ```cpp
 * class PolygonOffsetGM : public GM {
 * public:
 *     PolygonOffsetGM(bool convexOnly)
 *         : fConvexOnly(convexOnly) {
 *         this->setBGColor(0xFFFFFFFF);
 *     }
 *
 * protected:
 *     SkString getName() const override {
 *         if (fConvexOnly) {
 *             return SkString("convex-polygon-inset");
 *         } else {
 *             return SkString("simple-polygon-offset");
 *         }
 *     }
 *     SkISize getISize() override { return SkISize::Make(kGMWidth, kGMHeight); }
 *     bool runAsBench() const override { return true; }
 *
 *     static void GetConvexPolygon(int index, SkPathDirection dir,
 *                                  std::unique_ptr<SkPoint[]>* data, size_t* numPts) {
 *         if (index < (int)std::size(PolygonOffsetData::gConvexPoints)) {
 *             // manually specified
 *             *numPts = PolygonOffsetData::gConvexSizes[index];
 *             *data = std::make_unique<SkPoint[]>(*numPts);
 *             if (SkPathDirection::kCW == dir) {
 *                 for (size_t i = 0; i < *numPts; ++i) {
 *                     (*data)[i] = PolygonOffsetData::gConvexPoints[index][i];
 *                 }
 *             } else {
 *                 for (size_t i = 0; i < *numPts; ++i) {
 *                     (*data)[i] = PolygonOffsetData::gConvexPoints[index][*numPts - i - 1];
 *                 }
 *             }
 *         } else {
 *             // procedurally generated
 *             SkScalar width = kMaxPathHeight / 2;
 *             SkScalar height = kMaxPathHeight / 2;
 *             int numPtsArray[] = { 3, 4, 5, 5, 6, 8, 8, 20, 100 };
 *
 *             size_t arrayIndex = index - std::size(PolygonOffsetData::gConvexPoints);
 *             SkASSERT(arrayIndex < std::size(numPtsArray));
 *             *numPts = numPtsArray[arrayIndex];
 *             if (arrayIndex == 3 || arrayIndex == 6) {
 *                 // squashed pentagon and octagon
 *                 width = kMaxPathHeight / 5;
 *             }
 *
 *             *data = std::make_unique<SkPoint[]>(*numPts);
 *
 *             create_ngon(*numPts, data->get(), width, height, dir);
 *         }
 *     }
 *
 *     static void GetSimplePolygon(int index, SkPathDirection dir,
 *                                  std::unique_ptr<SkPoint[]>* data, size_t* numPts) {
 *         if (index < (int)std::size(PolygonOffsetData::gSimplePoints)) {
 *             // manually specified
 *             *numPts = PolygonOffsetData::gSimpleSizes[index];
 *             *data = std::make_unique<SkPoint[]>(*numPts);
 *             if (SkPathDirection::kCW == dir) {
 *                 for (size_t i = 0; i < *numPts; ++i) {
 *                     (*data)[i] = PolygonOffsetData::gSimplePoints[index][i];
 *                 }
 *             } else {
 *                 for (size_t i = 0; i < *numPts; ++i) {
 *                     (*data)[i] = PolygonOffsetData::gSimplePoints[index][*numPts - i - 1];
 *                 }
 *             }
 *         } else {
 *             // procedurally generated
 *             SkScalar width = kMaxPathHeight / 2;
 *             SkScalar height = kMaxPathHeight / 2;
 *             int numPtsArray[] = { 5, 7, 8, 20, 100 };
 *
 *             size_t arrayIndex = index - std::size(PolygonOffsetData::gSimplePoints);
 *             arrayIndex = std::min(arrayIndex, std::size(numPtsArray) - 1);
 *             SkASSERT(arrayIndex < std::size(numPtsArray));
 *             *numPts = numPtsArray[arrayIndex];
 *             // squash horizontally
 *             width = kMaxPathHeight / 5;
 *
 *             *data = std::make_unique<SkPoint[]>(*numPts);
 *
 *             create_ngon(*numPts, data->get(), width, height, dir);
 *         }
 *     }
 *     // Draw a single polygon with insets and potentially outsets
 *     void drawPolygon(SkCanvas* canvas, int index, SkPoint* position) {
 *
 *         SkPoint center;
 *         {
 *             std::unique_ptr<SkPoint[]> data(nullptr);
 *             size_t numPts;
 *             if (fConvexOnly) {
 *                 GetConvexPolygon(index, SkPathDirection::kCW, &data, &numPts);
 *             } else {
 *                 GetSimplePolygon(index, SkPathDirection::kCW, &data, &numPts);
 *             }
 *             SkRect bounds = SkRect::BoundsOrEmpty({data.get(), numPts});
 *             if (!fConvexOnly) {
 *                 bounds.outset(kMaxOutset, kMaxOutset);
 *             }
 *             if (position->fX + bounds.width() > kGMWidth) {
 *                 position->fX = 0;
 *                 position->fY += kMaxPathHeight;
 *             }
 *             center = { position->fX + SkScalarHalf(bounds.width()), position->fY };
 *             position->fX += bounds.width();
 *         }
 *
 *         const SkPathDirection dirs[2] = { SkPathDirection::kCW, SkPathDirection::kCCW };
 *         const float insets[] = { 5, 10, 15, 20, 25, 30, 35, 40 };
 *         const float offsets[] = { 2, 5, 9, 14, 20, 27, 35, 44, -2, -5, -9 };
 *         const SkColor colors[] = { 0xFF901313, 0xFF8D6214, 0xFF698B14, 0xFF1C8914,
 *                                    0xFF148755, 0xFF146C84, 0xFF142482, 0xFF4A1480,
 *                                    0xFF901313, 0xFF8D6214, 0xFF698B14 };
 *
 *         SkPaint paint;
 *         paint.setAntiAlias(true);
 *         paint.setStyle(SkPaint::kStroke_Style);
 *         paint.setStrokeWidth(1);
 *
 *         std::unique_ptr<SkPoint[]> data(nullptr);
 *         size_t numPts;
 *         if (fConvexOnly) {
 *             GetConvexPolygon(index, dirs[index % 2], &data, &numPts);
 *         } else {
 *             GetSimplePolygon(index, dirs[index % 2], &data, &numPts);
 *         }
 *
 *         {
 *             SkPath path = SkPath::Polygon({data.get(), numPts}, true);
 *             canvas->save();
 *             canvas->translate(center.fX, center.fY);
 *             canvas->drawPath(path, paint);
 *             canvas->restore();
 *         }
 *
 *         SkTDArray<SkPoint> offsetPoly;
 *         size_t count = fConvexOnly ? std::size(insets) : std::size(offsets);
 *         for (size_t i = 0; i < count; ++i) {
 *             SkScalar offset = fConvexOnly ? insets[i] : offsets[i];
 *             std::function<SkScalar(const SkPoint&)> offsetFunc;
 *
 *             bool result;
 *             if (fConvexOnly) {
 *                 result = SkInsetConvexPolygon(data.get(), numPts, offset, &offsetPoly);
 *             } else {
 *                 SkRect bounds;
 *                 bounds.setBoundsCheck({data.get(), numPts});
 *                 result = SkOffsetSimplePolygon(data.get(), numPts, bounds, offset, &offsetPoly);
 *             }
 *             if (result) {
 *                 SkPath path = SkPath::Polygon(offsetPoly, true);
 *
 *                 paint.setColor(ToolUtils::color_to_565(colors[i]));
 *                 canvas->save();
 *                 canvas->translate(center.fX, center.fY);
 *                 canvas->drawPath(path, paint);
 *                 canvas->restore();
 *             }
 *         }
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         // the right edge of the last drawn path
 *         SkPoint offset = { 0, SkScalarHalf(kMaxPathHeight) };
 *         if (!fConvexOnly) {
 *             offset.fY += kMaxOutset;
 *         }
 *
 *         for (int i = 0; i < kNumPaths; ++i) {
 *             this->drawPolygon(canvas, i, &offset);
 *         }
 *     }
 *
 * private:
 *     inline static constexpr int kNumPaths = 20;
 *     inline static constexpr int kMaxPathHeight = 100;
 *     inline static constexpr int kMaxOutset = 16;
 *     inline static constexpr int kGMWidth = 512;
 *     inline static constexpr int kGMHeight = 512;
 *
 *     bool fConvexOnly;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class PolygonOffsetGM public constructor(
  convexOnly: Boolean,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kNumPaths = 20
   * ```
   */
  private var fConvexOnly: Boolean = TODO("Initialize fConvexOnly")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override {
   *         if (fConvexOnly) {
   *             return SkString("convex-polygon-inset");
   *         } else {
   *             return SkString("simple-polygon-offset");
   *         }
   *     }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(kGMWidth, kGMHeight); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * bool runAsBench() const override { return true; }
   * ```
   */
  protected override fun runAsBench(): Boolean {
    TODO("Implement runAsBench")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawPolygon(SkCanvas* canvas, int index, SkPoint* position) {
   *
   *         SkPoint center;
   *         {
   *             std::unique_ptr<SkPoint[]> data(nullptr);
   *             size_t numPts;
   *             if (fConvexOnly) {
   *                 GetConvexPolygon(index, SkPathDirection::kCW, &data, &numPts);
   *             } else {
   *                 GetSimplePolygon(index, SkPathDirection::kCW, &data, &numPts);
   *             }
   *             SkRect bounds = SkRect::BoundsOrEmpty({data.get(), numPts});
   *             if (!fConvexOnly) {
   *                 bounds.outset(kMaxOutset, kMaxOutset);
   *             }
   *             if (position->fX + bounds.width() > kGMWidth) {
   *                 position->fX = 0;
   *                 position->fY += kMaxPathHeight;
   *             }
   *             center = { position->fX + SkScalarHalf(bounds.width()), position->fY };
   *             position->fX += bounds.width();
   *         }
   *
   *         const SkPathDirection dirs[2] = { SkPathDirection::kCW, SkPathDirection::kCCW };
   *         const float insets[] = { 5, 10, 15, 20, 25, 30, 35, 40 };
   *         const float offsets[] = { 2, 5, 9, 14, 20, 27, 35, 44, -2, -5, -9 };
   *         const SkColor colors[] = { 0xFF901313, 0xFF8D6214, 0xFF698B14, 0xFF1C8914,
   *                                    0xFF148755, 0xFF146C84, 0xFF142482, 0xFF4A1480,
   *                                    0xFF901313, 0xFF8D6214, 0xFF698B14 };
   *
   *         SkPaint paint;
   *         paint.setAntiAlias(true);
   *         paint.setStyle(SkPaint::kStroke_Style);
   *         paint.setStrokeWidth(1);
   *
   *         std::unique_ptr<SkPoint[]> data(nullptr);
   *         size_t numPts;
   *         if (fConvexOnly) {
   *             GetConvexPolygon(index, dirs[index % 2], &data, &numPts);
   *         } else {
   *             GetSimplePolygon(index, dirs[index % 2], &data, &numPts);
   *         }
   *
   *         {
   *             SkPath path = SkPath::Polygon({data.get(), numPts}, true);
   *             canvas->save();
   *             canvas->translate(center.fX, center.fY);
   *             canvas->drawPath(path, paint);
   *             canvas->restore();
   *         }
   *
   *         SkTDArray<SkPoint> offsetPoly;
   *         size_t count = fConvexOnly ? std::size(insets) : std::size(offsets);
   *         for (size_t i = 0; i < count; ++i) {
   *             SkScalar offset = fConvexOnly ? insets[i] : offsets[i];
   *             std::function<SkScalar(const SkPoint&)> offsetFunc;
   *
   *             bool result;
   *             if (fConvexOnly) {
   *                 result = SkInsetConvexPolygon(data.get(), numPts, offset, &offsetPoly);
   *             } else {
   *                 SkRect bounds;
   *                 bounds.setBoundsCheck({data.get(), numPts});
   *                 result = SkOffsetSimplePolygon(data.get(), numPts, bounds, offset, &offsetPoly);
   *             }
   *             if (result) {
   *                 SkPath path = SkPath::Polygon(offsetPoly, true);
   *
   *                 paint.setColor(ToolUtils::color_to_565(colors[i]));
   *                 canvas->save();
   *                 canvas->translate(center.fX, center.fY);
   *                 canvas->drawPath(path, paint);
   *                 canvas->restore();
   *             }
   *         }
   *     }
   * ```
   */
  protected fun drawPolygon(
    canvas: SkCanvas?,
    index: Int,
    position: SkPoint?,
  ) {
    TODO("Implement drawPolygon")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         // the right edge of the last drawn path
   *         SkPoint offset = { 0, SkScalarHalf(kMaxPathHeight) };
   *         if (!fConvexOnly) {
   *             offset.fY += kMaxOutset;
   *         }
   *
   *         for (int i = 0; i < kNumPaths; ++i) {
   *             this->drawPolygon(canvas, i, &offset);
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public companion object {
    private val kNumPaths: Int = TODO("Initialize kNumPaths")

    private val kMaxPathHeight: Int = TODO("Initialize kMaxPathHeight")

    private val kMaxOutset: Int = TODO("Initialize kMaxOutset")

    private val kGMWidth: Int = TODO("Initialize kGMWidth")

    private val kGMHeight: Int = TODO("Initialize kGMHeight")

    /**
     * C++ original:
     * ```cpp
     * static void GetConvexPolygon(int index, SkPathDirection dir,
     *                                  std::unique_ptr<SkPoint[]>* data, size_t* numPts) {
     *         if (index < (int)std::size(PolygonOffsetData::gConvexPoints)) {
     *             // manually specified
     *             *numPts = PolygonOffsetData::gConvexSizes[index];
     *             *data = std::make_unique<SkPoint[]>(*numPts);
     *             if (SkPathDirection::kCW == dir) {
     *                 for (size_t i = 0; i < *numPts; ++i) {
     *                     (*data)[i] = PolygonOffsetData::gConvexPoints[index][i];
     *                 }
     *             } else {
     *                 for (size_t i = 0; i < *numPts; ++i) {
     *                     (*data)[i] = PolygonOffsetData::gConvexPoints[index][*numPts - i - 1];
     *                 }
     *             }
     *         } else {
     *             // procedurally generated
     *             SkScalar width = kMaxPathHeight / 2;
     *             SkScalar height = kMaxPathHeight / 2;
     *             int numPtsArray[] = { 3, 4, 5, 5, 6, 8, 8, 20, 100 };
     *
     *             size_t arrayIndex = index - std::size(PolygonOffsetData::gConvexPoints);
     *             SkASSERT(arrayIndex < std::size(numPtsArray));
     *             *numPts = numPtsArray[arrayIndex];
     *             if (arrayIndex == 3 || arrayIndex == 6) {
     *                 // squashed pentagon and octagon
     *                 width = kMaxPathHeight / 5;
     *             }
     *
     *             *data = std::make_unique<SkPoint[]>(*numPts);
     *
     *             create_ngon(*numPts, data->get(), width, height, dir);
     *         }
     *     }
     * ```
     */
    protected fun getConvexPolygon(
      index: Int,
      dir: SkPathDirection,
      `data`: Array<SkPoint>?,
      numPts: ULong?,
    ) {
      TODO("Implement getConvexPolygon")
    }

    /**
     * C++ original:
     * ```cpp
     * static void GetSimplePolygon(int index, SkPathDirection dir,
     *                                  std::unique_ptr<SkPoint[]>* data, size_t* numPts) {
     *         if (index < (int)std::size(PolygonOffsetData::gSimplePoints)) {
     *             // manually specified
     *             *numPts = PolygonOffsetData::gSimpleSizes[index];
     *             *data = std::make_unique<SkPoint[]>(*numPts);
     *             if (SkPathDirection::kCW == dir) {
     *                 for (size_t i = 0; i < *numPts; ++i) {
     *                     (*data)[i] = PolygonOffsetData::gSimplePoints[index][i];
     *                 }
     *             } else {
     *                 for (size_t i = 0; i < *numPts; ++i) {
     *                     (*data)[i] = PolygonOffsetData::gSimplePoints[index][*numPts - i - 1];
     *                 }
     *             }
     *         } else {
     *             // procedurally generated
     *             SkScalar width = kMaxPathHeight / 2;
     *             SkScalar height = kMaxPathHeight / 2;
     *             int numPtsArray[] = { 5, 7, 8, 20, 100 };
     *
     *             size_t arrayIndex = index - std::size(PolygonOffsetData::gSimplePoints);
     *             arrayIndex = std::min(arrayIndex, std::size(numPtsArray) - 1);
     *             SkASSERT(arrayIndex < std::size(numPtsArray));
     *             *numPts = numPtsArray[arrayIndex];
     *             // squash horizontally
     *             width = kMaxPathHeight / 5;
     *
     *             *data = std::make_unique<SkPoint[]>(*numPts);
     *
     *             create_ngon(*numPts, data->get(), width, height, dir);
     *         }
     *     }
     * ```
     */
    protected fun getSimplePolygon(
      index: Int,
      dir: SkPathDirection,
      `data`: Array<SkPoint>?,
      numPts: ULong?,
    ) {
      TODO("Implement getSimplePolygon")
    }
  }
}
