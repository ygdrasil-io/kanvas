package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class PictureShaderTileGM : public skiagm::GM {
 * protected:
 *     SkString getName() const override { return SkString("pictureshadertile"); }
 *
 *     SkISize getISize() override { return SkISize::Make(800, 600); }
 *
 *     void onOnceBeforeDraw() override {
 *         SkPictureRecorder recorder;
 *         SkCanvas* pictureCanvas = recorder.beginRecording(kPictureSize, kPictureSize);
 *         draw_scene(pictureCanvas, kPictureSize);
 *         sk_sp<SkPicture> picture(recorder.finishRecordingAsPicture());
 *
 *         SkPoint offset = SkPoint::Make(100, 100);
 *         pictureCanvas = recorder.beginRecording(SkRect::MakeXYWH(offset.x(), offset.y(),
 *                                                                  kPictureSize, kPictureSize));
 *         pictureCanvas->translate(offset.x(), offset.y());
 *         draw_scene(pictureCanvas, kPictureSize);
 *         sk_sp<SkPicture> offsetPicture(recorder.finishRecordingAsPicture());
 *
 *         for (unsigned i = 0; i < std::size(tiles); ++i) {
 *             SkRect tile = SkRect::MakeXYWH(tiles[i].x * kPictureSize,
 *                                            tiles[i].y * kPictureSize,
 *                                            tiles[i].w * kPictureSize,
 *                                            tiles[i].h * kPictureSize);
 *             SkMatrix localMatrix;
 *             localMatrix.setTranslate(tiles[i].offsetX * kPictureSize,
 *                                      tiles[i].offsetY * kPictureSize);
 *             localMatrix.postScale(kFillSize / (2 * kPictureSize),
 *                                   kFillSize / (2 * kPictureSize));
 *
 *             sk_sp<SkPicture> pictureRef = picture;
 *             SkRect* tilePtr = &tile;
 *
 *             if (tile == SkRect::MakeWH(kPictureSize, kPictureSize)) {
 *                 // When the tile == picture bounds, exercise the picture + offset path.
 *                 pictureRef = offsetPicture;
 *                 tilePtr = nullptr;
 *             }
 *
 *             fShaders[i] = pictureRef->makeShader(SkTileMode::kRepeat, SkTileMode::kRepeat,
 *                                                  SkFilterMode::kNearest, &localMatrix, tilePtr);
 *         }
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         canvas->clear(SK_ColorBLACK);
 *
 *         SkPaint paint;
 *         paint.setStyle(SkPaint::kFill_Style);
 *
 *         for (unsigned i = 0; i < std::size(fShaders); ++i) {
 *             paint.setShader(fShaders[i]);
 *
 *             canvas->save();
 *             canvas->translate((i % kRowSize) * kFillSize * 1.1f,
 *                               (i / kRowSize) * kFillSize * 1.1f);
 *             canvas->drawRect(SkRect::MakeWH(kFillSize, kFillSize), paint);
 *             canvas->restore();
 *         }
 *     }
 *
 * private:
 *     sk_sp<SkShader> fShaders[std::size(tiles)];
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class PictureShaderTileGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> fShaders
   * ```
   */
  private var fShaders: SkSp<SkShader> = TODO("Initialize fShaders")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("pictureshadertile"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(800, 600); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         SkPictureRecorder recorder;
   *         SkCanvas* pictureCanvas = recorder.beginRecording(kPictureSize, kPictureSize);
   *         draw_scene(pictureCanvas, kPictureSize);
   *         sk_sp<SkPicture> picture(recorder.finishRecordingAsPicture());
   *
   *         SkPoint offset = SkPoint::Make(100, 100);
   *         pictureCanvas = recorder.beginRecording(SkRect::MakeXYWH(offset.x(), offset.y(),
   *                                                                  kPictureSize, kPictureSize));
   *         pictureCanvas->translate(offset.x(), offset.y());
   *         draw_scene(pictureCanvas, kPictureSize);
   *         sk_sp<SkPicture> offsetPicture(recorder.finishRecordingAsPicture());
   *
   *         for (unsigned i = 0; i < std::size(tiles); ++i) {
   *             SkRect tile = SkRect::MakeXYWH(tiles[i].x * kPictureSize,
   *                                            tiles[i].y * kPictureSize,
   *                                            tiles[i].w * kPictureSize,
   *                                            tiles[i].h * kPictureSize);
   *             SkMatrix localMatrix;
   *             localMatrix.setTranslate(tiles[i].offsetX * kPictureSize,
   *                                      tiles[i].offsetY * kPictureSize);
   *             localMatrix.postScale(kFillSize / (2 * kPictureSize),
   *                                   kFillSize / (2 * kPictureSize));
   *
   *             sk_sp<SkPicture> pictureRef = picture;
   *             SkRect* tilePtr = &tile;
   *
   *             if (tile == SkRect::MakeWH(kPictureSize, kPictureSize)) {
   *                 // When the tile == picture bounds, exercise the picture + offset path.
   *                 pictureRef = offsetPicture;
   *                 tilePtr = nullptr;
   *             }
   *
   *             fShaders[i] = pictureRef->makeShader(SkTileMode::kRepeat, SkTileMode::kRepeat,
   *                                                  SkFilterMode::kNearest, &localMatrix, tilePtr);
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
   * void onDraw(SkCanvas* canvas) override {
   *         canvas->clear(SK_ColorBLACK);
   *
   *         SkPaint paint;
   *         paint.setStyle(SkPaint::kFill_Style);
   *
   *         for (unsigned i = 0; i < std::size(fShaders); ++i) {
   *             paint.setShader(fShaders[i]);
   *
   *             canvas->save();
   *             canvas->translate((i % kRowSize) * kFillSize * 1.1f,
   *                               (i / kRowSize) * kFillSize * 1.1f);
   *             canvas->drawRect(SkRect::MakeWH(kFillSize, kFillSize), paint);
   *             canvas->restore();
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
