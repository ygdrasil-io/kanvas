package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class DistantClipGM : public GM {
 *     SkString getName() const override { return SkString("distantclip"); }
 *
 *     SkISize getISize() override { return {100, 100}; }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         constexpr SkScalar kOffset = 35000.0f;
 *         constexpr SkScalar kExtents = 1000.0f;
 *
 *         SkPictureRecorder recorder;
 *         // We record a picture of huge vertical extents in which we clear the canvas to red, create
 *         // a 'extents' by 'extents' round rect clip at a vertical offset of 'offset', then draw
 *         // green into that.
 *         SkCanvas* rec = recorder.beginRecording(kExtents, kOffset + kExtents);
 *         rec->drawColor(SK_ColorRED);
 *         rec->save();
 *         SkRect r = SkRect::MakeXYWH(-kExtents, kOffset - kExtents, 2 * kExtents, 2 * kExtents);
 *         rec->clipPath(SkPath::RRect(r, 5, 5), true);
 *         rec->drawColor(SK_ColorGREEN);
 *         rec->restore();
 *         sk_sp<SkPicture> pict(recorder.finishRecordingAsPicture());
 *
 *         // Next we play that picture into another picture of the same size.
 *         pict->playback(recorder.beginRecording(pict->cullRect().width(),
 *                                                pict->cullRect().height()));
 *         sk_sp<SkPicture> pict2(recorder.finishRecordingAsPicture());
 *
 *         // Finally we play the part of that second picture that should be green into the canvas.
 *         canvas->save();
 *         canvas->translate(kExtents / 2, -(kOffset - kExtents / 2));
 *         pict2->playback(canvas);
 *         canvas->restore();
 *
 *         // If the image is red, we erroneously decided the clipPath was empty and didn't record
 *         // the green drawColor, if it's green we're all good.
 *     }
 * }
 * ```
 */
public open class DistantClipGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("distantclip"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {100, 100}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         constexpr SkScalar kOffset = 35000.0f;
   *         constexpr SkScalar kExtents = 1000.0f;
   *
   *         SkPictureRecorder recorder;
   *         // We record a picture of huge vertical extents in which we clear the canvas to red, create
   *         // a 'extents' by 'extents' round rect clip at a vertical offset of 'offset', then draw
   *         // green into that.
   *         SkCanvas* rec = recorder.beginRecording(kExtents, kOffset + kExtents);
   *         rec->drawColor(SK_ColorRED);
   *         rec->save();
   *         SkRect r = SkRect::MakeXYWH(-kExtents, kOffset - kExtents, 2 * kExtents, 2 * kExtents);
   *         rec->clipPath(SkPath::RRect(r, 5, 5), true);
   *         rec->drawColor(SK_ColorGREEN);
   *         rec->restore();
   *         sk_sp<SkPicture> pict(recorder.finishRecordingAsPicture());
   *
   *         // Next we play that picture into another picture of the same size.
   *         pict->playback(recorder.beginRecording(pict->cullRect().width(),
   *                                                pict->cullRect().height()));
   *         sk_sp<SkPicture> pict2(recorder.finishRecordingAsPicture());
   *
   *         // Finally we play the part of that second picture that should be green into the canvas.
   *         canvas->save();
   *         canvas->translate(kExtents / 2, -(kOffset - kExtents / 2));
   *         pict2->playback(canvas);
   *         canvas->restore();
   *
   *         // If the image is red, we erroneously decided the clipPath was empty and didn't record
   *         // the green drawColor, if it's green we're all good.
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
