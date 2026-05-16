package org.skia.tests

import kotlin.Boolean
import kotlin.Float
import kotlin.String
import kotlin.UInt
import org.skia.core.SkCanvas
import org.skia.core.SkPicture
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkSp
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class PictureShaderGM : public skiagm::GM {
 * public:
 *     PictureShaderGM(SkScalar tileSize, SkScalar sceneSize, bool useLocalMatrixWrapper = false,
 *                     float alpha = 1)
 *         : fTileSize(tileSize)
 *         , fSceneSize(sceneSize)
 *         , fAlpha(alpha)
 *         , fUseLocalMatrixWrapper(useLocalMatrixWrapper)
 *     {}
 *
 *  protected:
 *     void onOnceBeforeDraw() override {
 *        // Build the picture.
 *         SkPictureRecorder recorder;
 *         SkCanvas* pictureCanvas = recorder.beginRecording(fTileSize, fTileSize);
 *         this->drawTile(pictureCanvas);
 *         fPicture = recorder.finishRecordingAsPicture();
 *
 *         // Build a reference bitmap.
 *         fBitmap.allocN32Pixels(SkScalarCeilToInt(fTileSize), SkScalarCeilToInt(fTileSize));
 *         fBitmap.eraseColor(SK_ColorTRANSPARENT);
 *         SkCanvas bitmapCanvas(fBitmap);
 *         this->drawTile(&bitmapCanvas);
 *     }
 *
 *     SkString getName() const override {
 *         return SkStringPrintf("pictureshader%s%s",
 *                               fUseLocalMatrixWrapper ? "_localwrapper" : "",
 *                               fAlpha < 1 ? "_alpha" : "");
 *     }
 *
 *     SkISize getISize() override { return SkISize::Make(1400, 1450); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         this->drawSceneColumn(canvas, SkPoint::Make(0, 0), 1, 1, 0);
 *         this->drawSceneColumn(canvas, SkPoint::Make(0, fSceneSize * 6.4f), 1, 2, 0);
 *         this->drawSceneColumn(canvas, SkPoint::Make(fSceneSize * 2.4f, 0), 1, 1, 1);
 *         this->drawSceneColumn(canvas, SkPoint::Make(fSceneSize * 2.4f, fSceneSize * 6.4f), 1, 1, 2);
 *         this->drawSceneColumn(canvas, SkPoint::Make(fSceneSize * 4.8f, 0), 2, 1, 0);
 *         this->drawSceneColumn(canvas, SkPoint::Make(fSceneSize * 9.6f, 0), 2, 2, 0);
 *
 *         // One last custom row to exercise negative scaling
 *         SkMatrix ctm, localMatrix;
 *         ctm.setTranslate(fSceneSize * 2.1f, fSceneSize * 13.8f);
 *         ctm.preScale(-1, -1);
 *         localMatrix.setScale(2, 2);
 *         this->drawScene(canvas, ctm, localMatrix, 0);
 *
 *         ctm.setTranslate(fSceneSize * 2.4f, fSceneSize * 12.8f);
 *         localMatrix.setScale(-1, -1);
 *         this->drawScene(canvas, ctm, localMatrix, 0);
 *
 *         ctm.setTranslate(fSceneSize * 4.8f, fSceneSize * 12.3f);
 *         ctm.preScale(2, 2);
 *         this->drawScene(canvas, ctm, localMatrix, 0);
 *
 *         ctm.setTranslate(fSceneSize * 13.8f, fSceneSize * 14.3f);
 *         ctm.preScale(-2, -2);
 *         localMatrix.setTranslate(fTileSize / 4, fTileSize / 4);
 *         localMatrix.preRotate(45);
 *         localMatrix.preScale(-2, -2);
 *         this->drawScene(canvas, ctm, localMatrix, 0);
 *     }
 *
 * private:
 *     void drawSceneColumn(SkCanvas* canvas, const SkPoint& pos, SkScalar scale, SkScalar localScale,
 *                          unsigned tileMode) {
 *         SkMatrix ctm, localMatrix;
 *
 *         ctm.setTranslate(pos.x(), pos.y());
 *         ctm.preScale(scale, scale);
 *         localMatrix.setScale(localScale, localScale);
 *         this->drawScene(canvas, ctm, localMatrix, tileMode);
 *
 *         ctm.setTranslate(pos.x(), pos.y() + fSceneSize * 1.2f * scale);
 *         ctm.preScale(scale, scale);
 *         localMatrix.setTranslate(fTileSize / 4, fTileSize / 4);
 *         localMatrix.preScale(localScale, localScale);
 *         this->drawScene(canvas, ctm, localMatrix, tileMode);
 *
 *         ctm.setTranslate(pos.x(), pos.y() + fSceneSize * 2.4f * scale);
 *         ctm.preScale(scale, scale);
 *         localMatrix.setRotate(45);
 *         localMatrix.preScale(localScale, localScale);
 *         this->drawScene(canvas, ctm, localMatrix, tileMode);
 *
 *         ctm.setTranslate(pos.x(), pos.y() + fSceneSize * 3.6f * scale);
 *         ctm.preScale(scale, scale);
 *         localMatrix.setSkew(1, 0);
 *         localMatrix.preScale(localScale, localScale);
 *         this->drawScene(canvas, ctm, localMatrix, tileMode);
 *
 *         ctm.setTranslate(pos.x(), pos.y() + fSceneSize * 4.8f * scale);
 *         ctm.preScale(scale, scale);
 *         localMatrix.setTranslate(fTileSize / 4, fTileSize / 4);
 *         localMatrix.preRotate(45);
 *         localMatrix.preScale(localScale, localScale);
 *         this->drawScene(canvas, ctm, localMatrix, tileMode);
 *     }
 *
 *     void drawTile(SkCanvas* canvas) {
 *         SkPaint paint;
 *         paint.setColor(SK_ColorGREEN);
 *         paint.setStyle(SkPaint::kFill_Style);
 *         paint.setAntiAlias(true);
 *
 *         canvas->drawCircle(fTileSize / 4, fTileSize / 4, fTileSize / 4, paint);
 *         canvas->drawRect(SkRect::MakeXYWH(fTileSize / 2, fTileSize / 2,
 *                                           fTileSize / 2, fTileSize / 2), paint);
 *
 *         paint.setColor(SK_ColorRED);
 *         canvas->drawLine(fTileSize / 2, fTileSize * 1 / 3,
 *                          fTileSize / 2, fTileSize * 2 / 3, paint);
 *         canvas->drawLine(fTileSize * 1 / 3, fTileSize / 2,
 *                          fTileSize * 2 / 3, fTileSize / 2, paint);
 *     }
 *
 *     void drawScene(SkCanvas* canvas, const SkMatrix& matrix, const SkMatrix& localMatrix,
 *                    unsigned tileMode) {
 *         SkASSERT(tileMode < std::size(kTileConfigs));
 *
 *         SkPaint paint;
 *         paint.setStyle(SkPaint::kFill_Style);
 *         paint.setColor(SK_ColorLTGRAY);
 *
 *         canvas->save();
 *         canvas->concat(matrix);
 *         canvas->drawRect(SkRect::MakeWH(fSceneSize, fSceneSize), paint);
 *         canvas->drawRect(SkRect::MakeXYWH(fSceneSize * 1.1f, 0, fSceneSize, fSceneSize), paint);
 *
 *         paint.setAlphaf(fAlpha);
 *
 *         auto pictureShader = fPicture->makeShader(kTileConfigs[tileMode].tmx,
 *                                                   kTileConfigs[tileMode].tmy,
 *                                                   SkFilterMode::kNearest,
 *                                                   fUseLocalMatrixWrapper ? nullptr : &localMatrix,
 *                                                   nullptr);
 *         paint.setShader(fUseLocalMatrixWrapper
 *                             ? pictureShader->makeWithLocalMatrix(localMatrix)
 *                             : pictureShader);
 *         canvas->drawRect(SkRect::MakeWH(fSceneSize, fSceneSize), paint);
 *
 *         canvas->translate(fSceneSize * 1.1f, 0);
 *
 *         auto bitmapShader = fBitmap.makeShader(kTileConfigs[tileMode].tmx,
 *                                                kTileConfigs[tileMode].tmy,
 *                                                SkSamplingOptions(),
 *                                                fUseLocalMatrixWrapper ? nullptr : &localMatrix);
 *         paint.setShader(fUseLocalMatrixWrapper
 *                             ? bitmapShader->makeWithLocalMatrix(localMatrix)
 *                             : bitmapShader);
 *         canvas->drawRect(SkRect::MakeWH(fSceneSize, fSceneSize), paint);
 *
 *         canvas->restore();
 *     }
 *
 *     const SkScalar   fTileSize;
 *     const SkScalar   fSceneSize;
 *     const float      fAlpha;
 *     const bool       fUseLocalMatrixWrapper;
 *
 *     sk_sp<SkPicture> fPicture;
 *     SkBitmap         fBitmap;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class PictureShaderGM public constructor(
  tileSize: SkScalar,
  sceneSize: SkScalar,
  useLocalMatrixWrapper: Boolean = TODO(),
  alpha: Float = TODO(),
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * const SkScalar   fTileSize
   * ```
   */
  private val fTileSize: SkScalar = TODO("Initialize fTileSize")

  /**
   * C++ original:
   * ```cpp
   * const SkScalar   fSceneSize
   * ```
   */
  private val fSceneSize: SkScalar = TODO("Initialize fSceneSize")

  /**
   * C++ original:
   * ```cpp
   * const float      fAlpha
   * ```
   */
  private val fAlpha: Float = TODO("Initialize fAlpha")

  /**
   * C++ original:
   * ```cpp
   * const bool       fUseLocalMatrixWrapper
   * ```
   */
  private val fUseLocalMatrixWrapper: Boolean = TODO("Initialize fUseLocalMatrixWrapper")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkPicture> fPicture
   * ```
   */
  private var fPicture: SkSp<SkPicture> = TODO("Initialize fPicture")

  /**
   * C++ original:
   * ```cpp
   * SkBitmap         fBitmap
   * ```
   */
  private var fBitmap: SkBitmap = TODO("Initialize fBitmap")

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *        // Build the picture.
   *         SkPictureRecorder recorder;
   *         SkCanvas* pictureCanvas = recorder.beginRecording(fTileSize, fTileSize);
   *         this->drawTile(pictureCanvas);
   *         fPicture = recorder.finishRecordingAsPicture();
   *
   *         // Build a reference bitmap.
   *         fBitmap.allocN32Pixels(SkScalarCeilToInt(fTileSize), SkScalarCeilToInt(fTileSize));
   *         fBitmap.eraseColor(SK_ColorTRANSPARENT);
   *         SkCanvas bitmapCanvas(fBitmap);
   *         this->drawTile(&bitmapCanvas);
   *     }
   * ```
   */
  protected override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override {
   *         return SkStringPrintf("pictureshader%s%s",
   *                               fUseLocalMatrixWrapper ? "_localwrapper" : "",
   *                               fAlpha < 1 ? "_alpha" : "");
   *     }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(1400, 1450); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         this->drawSceneColumn(canvas, SkPoint::Make(0, 0), 1, 1, 0);
   *         this->drawSceneColumn(canvas, SkPoint::Make(0, fSceneSize * 6.4f), 1, 2, 0);
   *         this->drawSceneColumn(canvas, SkPoint::Make(fSceneSize * 2.4f, 0), 1, 1, 1);
   *         this->drawSceneColumn(canvas, SkPoint::Make(fSceneSize * 2.4f, fSceneSize * 6.4f), 1, 1, 2);
   *         this->drawSceneColumn(canvas, SkPoint::Make(fSceneSize * 4.8f, 0), 2, 1, 0);
   *         this->drawSceneColumn(canvas, SkPoint::Make(fSceneSize * 9.6f, 0), 2, 2, 0);
   *
   *         // One last custom row to exercise negative scaling
   *         SkMatrix ctm, localMatrix;
   *         ctm.setTranslate(fSceneSize * 2.1f, fSceneSize * 13.8f);
   *         ctm.preScale(-1, -1);
   *         localMatrix.setScale(2, 2);
   *         this->drawScene(canvas, ctm, localMatrix, 0);
   *
   *         ctm.setTranslate(fSceneSize * 2.4f, fSceneSize * 12.8f);
   *         localMatrix.setScale(-1, -1);
   *         this->drawScene(canvas, ctm, localMatrix, 0);
   *
   *         ctm.setTranslate(fSceneSize * 4.8f, fSceneSize * 12.3f);
   *         ctm.preScale(2, 2);
   *         this->drawScene(canvas, ctm, localMatrix, 0);
   *
   *         ctm.setTranslate(fSceneSize * 13.8f, fSceneSize * 14.3f);
   *         ctm.preScale(-2, -2);
   *         localMatrix.setTranslate(fTileSize / 4, fTileSize / 4);
   *         localMatrix.preRotate(45);
   *         localMatrix.preScale(-2, -2);
   *         this->drawScene(canvas, ctm, localMatrix, 0);
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawSceneColumn(SkCanvas* canvas, const SkPoint& pos, SkScalar scale, SkScalar localScale,
   *                          unsigned tileMode) {
   *         SkMatrix ctm, localMatrix;
   *
   *         ctm.setTranslate(pos.x(), pos.y());
   *         ctm.preScale(scale, scale);
   *         localMatrix.setScale(localScale, localScale);
   *         this->drawScene(canvas, ctm, localMatrix, tileMode);
   *
   *         ctm.setTranslate(pos.x(), pos.y() + fSceneSize * 1.2f * scale);
   *         ctm.preScale(scale, scale);
   *         localMatrix.setTranslate(fTileSize / 4, fTileSize / 4);
   *         localMatrix.preScale(localScale, localScale);
   *         this->drawScene(canvas, ctm, localMatrix, tileMode);
   *
   *         ctm.setTranslate(pos.x(), pos.y() + fSceneSize * 2.4f * scale);
   *         ctm.preScale(scale, scale);
   *         localMatrix.setRotate(45);
   *         localMatrix.preScale(localScale, localScale);
   *         this->drawScene(canvas, ctm, localMatrix, tileMode);
   *
   *         ctm.setTranslate(pos.x(), pos.y() + fSceneSize * 3.6f * scale);
   *         ctm.preScale(scale, scale);
   *         localMatrix.setSkew(1, 0);
   *         localMatrix.preScale(localScale, localScale);
   *         this->drawScene(canvas, ctm, localMatrix, tileMode);
   *
   *         ctm.setTranslate(pos.x(), pos.y() + fSceneSize * 4.8f * scale);
   *         ctm.preScale(scale, scale);
   *         localMatrix.setTranslate(fTileSize / 4, fTileSize / 4);
   *         localMatrix.preRotate(45);
   *         localMatrix.preScale(localScale, localScale);
   *         this->drawScene(canvas, ctm, localMatrix, tileMode);
   *     }
   * ```
   */
  private fun drawSceneColumn(
    canvas: SkCanvas?,
    pos: SkPoint,
    scale: SkScalar,
    localScale: SkScalar,
    tileMode: UInt,
  ) {
    TODO("Implement drawSceneColumn")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawTile(SkCanvas* canvas) {
   *         SkPaint paint;
   *         paint.setColor(SK_ColorGREEN);
   *         paint.setStyle(SkPaint::kFill_Style);
   *         paint.setAntiAlias(true);
   *
   *         canvas->drawCircle(fTileSize / 4, fTileSize / 4, fTileSize / 4, paint);
   *         canvas->drawRect(SkRect::MakeXYWH(fTileSize / 2, fTileSize / 2,
   *                                           fTileSize / 2, fTileSize / 2), paint);
   *
   *         paint.setColor(SK_ColorRED);
   *         canvas->drawLine(fTileSize / 2, fTileSize * 1 / 3,
   *                          fTileSize / 2, fTileSize * 2 / 3, paint);
   *         canvas->drawLine(fTileSize * 1 / 3, fTileSize / 2,
   *                          fTileSize * 2 / 3, fTileSize / 2, paint);
   *     }
   * ```
   */
  private fun drawTile(canvas: SkCanvas?) {
    TODO("Implement drawTile")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawScene(SkCanvas* canvas, const SkMatrix& matrix, const SkMatrix& localMatrix,
   *                    unsigned tileMode) {
   *         SkASSERT(tileMode < std::size(kTileConfigs));
   *
   *         SkPaint paint;
   *         paint.setStyle(SkPaint::kFill_Style);
   *         paint.setColor(SK_ColorLTGRAY);
   *
   *         canvas->save();
   *         canvas->concat(matrix);
   *         canvas->drawRect(SkRect::MakeWH(fSceneSize, fSceneSize), paint);
   *         canvas->drawRect(SkRect::MakeXYWH(fSceneSize * 1.1f, 0, fSceneSize, fSceneSize), paint);
   *
   *         paint.setAlphaf(fAlpha);
   *
   *         auto pictureShader = fPicture->makeShader(kTileConfigs[tileMode].tmx,
   *                                                   kTileConfigs[tileMode].tmy,
   *                                                   SkFilterMode::kNearest,
   *                                                   fUseLocalMatrixWrapper ? nullptr : &localMatrix,
   *                                                   nullptr);
   *         paint.setShader(fUseLocalMatrixWrapper
   *                             ? pictureShader->makeWithLocalMatrix(localMatrix)
   *                             : pictureShader);
   *         canvas->drawRect(SkRect::MakeWH(fSceneSize, fSceneSize), paint);
   *
   *         canvas->translate(fSceneSize * 1.1f, 0);
   *
   *         auto bitmapShader = fBitmap.makeShader(kTileConfigs[tileMode].tmx,
   *                                                kTileConfigs[tileMode].tmy,
   *                                                SkSamplingOptions(),
   *                                                fUseLocalMatrixWrapper ? nullptr : &localMatrix);
   *         paint.setShader(fUseLocalMatrixWrapper
   *                             ? bitmapShader->makeWithLocalMatrix(localMatrix)
   *                             : bitmapShader);
   *         canvas->drawRect(SkRect::MakeWH(fSceneSize, fSceneSize), paint);
   *
   *         canvas->restore();
   *     }
   * ```
   */
  private fun drawScene(
    canvas: SkCanvas?,
    matrix: SkMatrix,
    localMatrix: SkMatrix,
    tileMode: UInt,
  ) {
    TODO("Implement drawScene")
  }
}
