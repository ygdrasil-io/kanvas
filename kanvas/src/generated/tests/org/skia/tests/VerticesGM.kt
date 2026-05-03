package org.skia.tests

import kotlin.Array
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkColor
import org.skia.foundation.SkColorFilter
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.math.SkISize
import org.skia.math.SkPoint
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class VerticesGM : public skiagm::GM {
 *     SkPoint                 fPts[kMeshVertexCnt];
 *     SkPoint                 fTexs[kMeshVertexCnt];
 *     SkColor                 fColors[kMeshVertexCnt];
 *     sk_sp<SkShader>         fShader1;
 *     sk_sp<SkShader>         fShader2;
 *     sk_sp<SkColorFilter>    fColorFilter;
 *     SkScalar                fShaderScale;
 *
 * public:
 *     VerticesGM(SkScalar shaderScale) : fShaderScale(shaderScale) {}
 *
 * protected:
 *
 *     void onOnceBeforeDraw() override {
 *         fill_mesh(fPts, fTexs, fColors, fShaderScale);
 *         fShader1 = make_shader1(fShaderScale);
 *         fShader2 = make_shader2();
 *         fColorFilter = make_color_filter();
 *     }
 *
 *     SkString getName() const override {
 *         SkString name("vertices");
 *         if (fShaderScale != 1) {
 *             name.append("_scaled_shader");
 *         }
 *         return name;
 *     }
 *
 *     SkISize getISize() override { return SkISize::Make(975, 1175); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         const SkBlendMode modes[] = {
 *             SkBlendMode::kClear,
 *             SkBlendMode::kSrc,
 *             SkBlendMode::kDst,
 *             SkBlendMode::kSrcOver,
 *             SkBlendMode::kDstOver,
 *             SkBlendMode::kSrcIn,
 *             SkBlendMode::kDstIn,
 *             SkBlendMode::kSrcOut,
 *             SkBlendMode::kDstOut,
 *             SkBlendMode::kSrcATop,
 *             SkBlendMode::kDstATop,
 *             SkBlendMode::kXor,
 *             SkBlendMode::kPlus,
 *             SkBlendMode::kModulate,
 *             SkBlendMode::kScreen,
 *             SkBlendMode::kOverlay,
 *             SkBlendMode::kDarken,
 *             SkBlendMode::kLighten,
 *             SkBlendMode::kColorDodge,
 *             SkBlendMode::kColorBurn,
 *             SkBlendMode::kHardLight,
 *             SkBlendMode::kSoftLight,
 *             SkBlendMode::kDifference,
 *             SkBlendMode::kExclusion,
 *             SkBlendMode::kMultiply,
 *             SkBlendMode::kHue,
 *             SkBlendMode::kSaturation,
 *             SkBlendMode::kColor,
 *             SkBlendMode::kLuminosity,
 *         };
 *
 *         SkPaint paint;
 *
 *         canvas->translate(4, 4);
 *         for (auto mode : modes) {
 *             canvas->save();
 *             for (float alpha : {1.0f, 0.5f}) {
 *                 for (const auto& cf : {sk_sp<SkColorFilter>(nullptr), fColorFilter}) {
 *                     for (const auto& shader : {fShader1, fShader2}) {
 *                         static constexpr struct {
 *                             bool fHasColors;
 *                             bool fHasTexs;
 *                         } kAttrs[] = {{true, false}, {false, true}, {true, true}};
 *                         for (auto attrs : kAttrs) {
 *                             paint.setShader(shader);
 *                             paint.setColorFilter(cf);
 *                             paint.setAlphaf(alpha);
 *
 *                             const SkColor* colors = attrs.fHasColors ? fColors : nullptr;
 *                             const SkPoint* texs = attrs.fHasTexs ? fTexs : nullptr;
 *                             auto v = SkVertices::MakeCopy(SkVertices::kTriangleFan_VertexMode,
 *                                                           kMeshVertexCnt, fPts, texs, colors,
 *                                                           kMeshIndexCnt, kMeshFan);
 *                             canvas->drawVertices(v, mode, paint);
 *                             canvas->translate(40, 0);
 *                         }
 *                     }
 *                 }
 *             }
 *             canvas->restore();
 *             canvas->translate(0, 40);
 *         }
 *     }
 *
 * private:
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class VerticesGM public constructor(
  shaderScale: SkScalar,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkPoint                 fPts[kMeshVertexCnt]
   * ```
   */
  private var fPts: Array<SkPoint> = TODO("Initialize fPts")

  /**
   * C++ original:
   * ```cpp
   * SkPoint                 fTexs[kMeshVertexCnt]
   * ```
   */
  private var fTexs: Array<SkPoint> = TODO("Initialize fTexs")

  /**
   * C++ original:
   * ```cpp
   * SkColor                 fColors[kMeshVertexCnt]
   * ```
   */
  private var fColors: Array<SkColor> = TODO("Initialize fColors")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader>         fShader1
   * ```
   */
  private var fShader1: SkSp<SkShader> = TODO("Initialize fShader1")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader>         fShader2
   * ```
   */
  private var fShader2: SkSp<SkShader> = TODO("Initialize fShader2")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorFilter>    fColorFilter
   * ```
   */
  private var fColorFilter: SkSp<SkColorFilter> = TODO("Initialize fColorFilter")

  /**
   * C++ original:
   * ```cpp
   * SkScalar                fShaderScale
   * ```
   */
  private var fShaderScale: SkScalar = TODO("Initialize fShaderScale")

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         fill_mesh(fPts, fTexs, fColors, fShaderScale);
   *         fShader1 = make_shader1(fShaderScale);
   *         fShader2 = make_shader2();
   *         fColorFilter = make_color_filter();
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
   *         SkString name("vertices");
   *         if (fShaderScale != 1) {
   *             name.append("_scaled_shader");
   *         }
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
   * SkISize getISize() override { return SkISize::Make(975, 1175); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         const SkBlendMode modes[] = {
   *             SkBlendMode::kClear,
   *             SkBlendMode::kSrc,
   *             SkBlendMode::kDst,
   *             SkBlendMode::kSrcOver,
   *             SkBlendMode::kDstOver,
   *             SkBlendMode::kSrcIn,
   *             SkBlendMode::kDstIn,
   *             SkBlendMode::kSrcOut,
   *             SkBlendMode::kDstOut,
   *             SkBlendMode::kSrcATop,
   *             SkBlendMode::kDstATop,
   *             SkBlendMode::kXor,
   *             SkBlendMode::kPlus,
   *             SkBlendMode::kModulate,
   *             SkBlendMode::kScreen,
   *             SkBlendMode::kOverlay,
   *             SkBlendMode::kDarken,
   *             SkBlendMode::kLighten,
   *             SkBlendMode::kColorDodge,
   *             SkBlendMode::kColorBurn,
   *             SkBlendMode::kHardLight,
   *             SkBlendMode::kSoftLight,
   *             SkBlendMode::kDifference,
   *             SkBlendMode::kExclusion,
   *             SkBlendMode::kMultiply,
   *             SkBlendMode::kHue,
   *             SkBlendMode::kSaturation,
   *             SkBlendMode::kColor,
   *             SkBlendMode::kLuminosity,
   *         };
   *
   *         SkPaint paint;
   *
   *         canvas->translate(4, 4);
   *         for (auto mode : modes) {
   *             canvas->save();
   *             for (float alpha : {1.0f, 0.5f}) {
   *                 for (const auto& cf : {sk_sp<SkColorFilter>(nullptr), fColorFilter}) {
   *                     for (const auto& shader : {fShader1, fShader2}) {
   *                         static constexpr struct {
   *                             bool fHasColors;
   *                             bool fHasTexs;
   *                         } kAttrs[] = {{true, false}, {false, true}, {true, true}};
   *                         for (auto attrs : kAttrs) {
   *                             paint.setShader(shader);
   *                             paint.setColorFilter(cf);
   *                             paint.setAlphaf(alpha);
   *
   *                             const SkColor* colors = attrs.fHasColors ? fColors : nullptr;
   *                             const SkPoint* texs = attrs.fHasTexs ? fTexs : nullptr;
   *                             auto v = SkVertices::MakeCopy(SkVertices::kTriangleFan_VertexMode,
   *                                                           kMeshVertexCnt, fPts, texs, colors,
   *                                                           kMeshIndexCnt, kMeshFan);
   *                             canvas->drawVertices(v, mode, paint);
   *                             canvas->translate(40, 0);
   *                         }
   *                     }
   *                 }
   *             }
   *             canvas->restore();
   *             canvas->translate(0, 40);
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
