package org.skia.tests

import kotlin.Array
import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkMaskFilter
import org.skia.foundation.SkSp
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class BlurIgnoreXformGM : public skiagm::GM {
 * public:
 *     enum class DrawType {
 *         kCircle,
 *         kRect,
 *         kRRect,
 *     };
 *
 *     BlurIgnoreXformGM(DrawType drawType) : fDrawType(drawType) { }
 *
 * protected:
 *     bool runAsBench() const override { return true; }
 *
 *     SkString getName() const override {
 *         SkString name;
 *         name.printf("blur_ignore_xform_%s",
 *                     DrawType::kCircle == fDrawType ? "circle"
 *                         : DrawType::kRect == fDrawType ? "rect" : "rrect");
 *         return name;
 *     }
 *
 *     SkISize getISize() override { return SkISize::Make(375, 475); }
 *
 *     void onOnceBeforeDraw() override {
 *         for (int i = 0; i < kNumBlurs; ++i) {
 *             fBlurFilters[i] = SkMaskFilter::MakeBlur(
 *                                     kNormal_SkBlurStyle,
 *                                     SkBlurMask::ConvertRadiusToSigma(SkIntToScalar(20)),
 *                                     kBlurFlags[i].fRespectCTM);
 *         }
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPaint paint;
 *         paint.setColor(SK_ColorBLACK);
 *         paint.setAntiAlias(true);
 *
 *         canvas->translate(10, 25);
 *         canvas->save();
 *         canvas->translate(80, 0);
 *         for (size_t i = 0; i < kNumBlurs; ++i) {
 *             SkAutoCanvasRestore autoRestore(canvas, true);
 *             canvas->translate(SkIntToScalar(i * 150), 0);
 *             for (auto scale : kMatrixScales) {
 *                 canvas->save();
 *                 canvas->scale(scale.fScale, scale.fScale);
 *                 static const SkScalar kRadius = 20.0f;
 *                 SkScalar coord = 50.0f * 1.0f / scale.fScale;
 *                 SkRect rect = SkRect::MakeXYWH(coord - kRadius , coord - kRadius,
 *                                                2 * kRadius, 2 * kRadius);
 *                 SkRRect rrect = SkRRect::MakeRectXY(rect, kRadius/2.0f, kRadius/2.0f);
 *
 *                 paint.setMaskFilter(fBlurFilters[i]);
 *                 for (int j = 0; j < 2; ++j) {
 *                     canvas->save();
 *                     canvas->translate(10 * (1 - j), 10 * (1 - j));
 *                     if (DrawType::kCircle == fDrawType) {
 *                         canvas->drawCircle(coord, coord, kRadius, paint);
 *                     } else if (DrawType::kRect == fDrawType) {
 *                         canvas->drawRect(rect, paint);
 *                     } else {
 *                         canvas->drawRRect(rrect, paint);
 *                     }
 *                     paint.setMaskFilter(nullptr);
 *                     canvas->restore();
 *                 }
 *                 canvas->restore();
 *                 canvas->translate(0, SkIntToScalar(150));
 *             }
 *         }
 *         canvas->restore();
 *         if (kBench_Mode != this->getMode()) {
 *             this->drawOverlay(canvas);
 *         }
 *     }
 *
 *     void drawOverlay(SkCanvas* canvas) {
 *         canvas->translate(10, 0);
 *         SkFont font = ToolUtils::DefaultPortableFont();
 *         canvas->save();
 *         for (int i = 0; i < kNumBlurs; ++i) {
 *             canvas->drawString(kBlurFlags[i].fName, 100, 0, font, SkPaint());
 *             canvas->translate(SkIntToScalar(130), 0);
 *         }
 *         canvas->restore();
 *         for (auto scale : kMatrixScales) {
 *             canvas->drawString(scale.fName, 0, 50, font, SkPaint());
 *             canvas->translate(0, SkIntToScalar(150));
 *         }
 *     }
 *
 * private:
 *     inline static constexpr int kNumBlurs = 2;
 *
 *     static const struct BlurFlags {
 *         bool fRespectCTM;
 *         const char* fName;
 *     } kBlurFlags[kNumBlurs];
 *
 *     static const struct MatrixScale {
 *         float fScale;
 *         const char* fName;
 *     } kMatrixScales[3];
 *
 *     DrawType fDrawType;
 *     sk_sp<SkMaskFilter> fBlurFilters[kNumBlurs];
 *
 *     using INHERITED =         skiagm::GM;
 * }
 * ```
 */
public open class BlurIgnoreXformGM public constructor(
  drawType: DrawType,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kNumBlurs = 2
   * ```
   */
  private var fDrawType: DrawType = TODO("Initialize fDrawType")

  /**
   * C++ original:
   * ```cpp
   * static const struct BlurFlags {
   *         bool fRespectCTM;
   *         const char* fName;
   *     } kBlurFlags[kNumBlurs]
   * ```
   */
  private var fBlurFilters: Array<SkSp<SkMaskFilter>> = TODO("Initialize fBlurFilters")

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
   * SkString getName() const override {
   *         SkString name;
   *         name.printf("blur_ignore_xform_%s",
   *                     DrawType::kCircle == fDrawType ? "circle"
   *                         : DrawType::kRect == fDrawType ? "rect" : "rrect");
   *         return name;
   *     }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(375, 475); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         for (int i = 0; i < kNumBlurs; ++i) {
   *             fBlurFilters[i] = SkMaskFilter::MakeBlur(
   *                                     kNormal_SkBlurStyle,
   *                                     SkBlurMask::ConvertRadiusToSigma(SkIntToScalar(20)),
   *                                     kBlurFlags[i].fRespectCTM);
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
   *         SkPaint paint;
   *         paint.setColor(SK_ColorBLACK);
   *         paint.setAntiAlias(true);
   *
   *         canvas->translate(10, 25);
   *         canvas->save();
   *         canvas->translate(80, 0);
   *         for (size_t i = 0; i < kNumBlurs; ++i) {
   *             SkAutoCanvasRestore autoRestore(canvas, true);
   *             canvas->translate(SkIntToScalar(i * 150), 0);
   *             for (auto scale : kMatrixScales) {
   *                 canvas->save();
   *                 canvas->scale(scale.fScale, scale.fScale);
   *                 static const SkScalar kRadius = 20.0f;
   *                 SkScalar coord = 50.0f * 1.0f / scale.fScale;
   *                 SkRect rect = SkRect::MakeXYWH(coord - kRadius , coord - kRadius,
   *                                                2 * kRadius, 2 * kRadius);
   *                 SkRRect rrect = SkRRect::MakeRectXY(rect, kRadius/2.0f, kRadius/2.0f);
   *
   *                 paint.setMaskFilter(fBlurFilters[i]);
   *                 for (int j = 0; j < 2; ++j) {
   *                     canvas->save();
   *                     canvas->translate(10 * (1 - j), 10 * (1 - j));
   *                     if (DrawType::kCircle == fDrawType) {
   *                         canvas->drawCircle(coord, coord, kRadius, paint);
   *                     } else if (DrawType::kRect == fDrawType) {
   *                         canvas->drawRect(rect, paint);
   *                     } else {
   *                         canvas->drawRRect(rrect, paint);
   *                     }
   *                     paint.setMaskFilter(nullptr);
   *                     canvas->restore();
   *                 }
   *                 canvas->restore();
   *                 canvas->translate(0, SkIntToScalar(150));
   *             }
   *         }
   *         canvas->restore();
   *         if (kBench_Mode != this->getMode()) {
   *             this->drawOverlay(canvas);
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
   * void drawOverlay(SkCanvas* canvas) {
   *         canvas->translate(10, 0);
   *         SkFont font = ToolUtils::DefaultPortableFont();
   *         canvas->save();
   *         for (int i = 0; i < kNumBlurs; ++i) {
   *             canvas->drawString(kBlurFlags[i].fName, 100, 0, font, SkPaint());
   *             canvas->translate(SkIntToScalar(130), 0);
   *         }
   *         canvas->restore();
   *         for (auto scale : kMatrixScales) {
   *             canvas->drawString(scale.fName, 0, 50, font, SkPaint());
   *             canvas->translate(0, SkIntToScalar(150));
   *         }
   *     }
   * ```
   */
  protected fun drawOverlay(canvas: SkCanvas?) {
    TODO("Implement drawOverlay")
  }

  public data class BlurFlags public constructor(
    public var fRespectCTM: Boolean,
    public val fName: String?,
  )

  public data class MatrixScale public constructor(
    public var fScale: Float,
    public val fName: String?,
  )

  public enum class DrawType {
    kCircle,
    kRect,
    kRRect,
  }

  public companion object {
    private val kNumBlurs: Int = TODO("Initialize kNumBlurs")

    private val kBlurFlags: Array<BlurFlags> = TODO("Initialize kBlurFlags")

    private val kMatrixScales: Array<MatrixScale> = TODO("Initialize kMatrixScales")
  }
}
