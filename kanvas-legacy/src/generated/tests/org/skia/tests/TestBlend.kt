package org.skia.tests

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt
import org.skia.core.SkSurface
import org.skia.effects.SkRuntimeBlendBuilder
import org.skia.foundation.SkSp
import org.skia.gpu.ganesh.GrRecordingContext
import undefined.PreTestFn

/**
 * C++ original:
 * ```cpp
 * class TestBlend {
 * public:
 *     TestBlend(skiatest::Reporter* r, GrRecordingContext* grContext, const GraphiteInfo* graphite)
 *             : fReporter(r), fGrContext(grContext), fGraphite(graphite) {
 *         fSurface = make_surface(fGrContext, fGraphite, /*size=*/{2, 2});
 *     }
 *
 *     void build(const char* src, bool allowPrivateAccess = false) {
 *         SkRuntimeEffect::Options options;
 *         if (allowPrivateAccess) {
 *             SkRuntimeEffectPriv::AllowPrivateAccess(&options);
 *         }
 *         auto [effect, errorText] = SkRuntimeEffect::MakeForBlender(SkString(src), options);
 *         if (!effect) {
 *             ERRORF(fReporter, "Effect didn't compile: %s", errorText.c_str());
 *             return;
 *         }
 *         fBuilder.emplace(std::move(effect));
 *     }
 *
 *     SkSurface* surface() {
 *         return fSurface.get();
 *     }
 *
 *     SkRuntimeBlendBuilder::BuilderUniform uniform(const char* name) {
 *         return fBuilder->uniform(name);
 *     }
 *
 *     SkRuntimeBlendBuilder::BuilderChild child(const char* name) {
 *         return fBuilder->child(name);
 *     }
 *
 *     void test(std::array<uint32_t, 4> expected, PreTestFn preTestCallback = nullptr) {
 *         auto blender = fBuilder->makeBlender();
 *         if (!blender) {
 *             ERRORF(fReporter, "Effect didn't produce a blender");
 *             return;
 *         }
 *
 *         SkCanvas* canvas = fSurface->getCanvas();
 *         SkPaint paint;
 *         paint.setBlender(std::move(blender));
 *         paint.setColor(SK_ColorGRAY);
 *
 *         paint_canvas(canvas, &paint, preTestCallback);
 *
 *         verify_2x2_surface_results(fReporter, fBuilder->effect(), fSurface.get(), expected);
 *     }
 *
 *     void test(uint32_t expected, PreTestFn preTestCallback = nullptr) {
 *         this->test({expected, expected, expected, expected}, preTestCallback);
 *     }
 *
 * private:
 *     skiatest::Reporter*            fReporter;
 *     sk_sp<SkSurface>               fSurface;
 *     GrRecordingContext*            fGrContext;
 *     const GraphiteInfo*            fGraphite;
 *     std::optional<SkRuntimeBlendBuilder> fBuilder;
 * }
 * ```
 */
public data class TestBlend public constructor(
  /**
   * C++ original:
   * ```cpp
   * skiatest::Reporter*            fReporter
   * ```
   */
  private var fReporter: Reporter?,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkSurface>               fSurface
   * ```
   */
  private var fSurface: SkSp<SkSurface>,
  /**
   * C++ original:
   * ```cpp
   * GrRecordingContext*            fGrContext
   * ```
   */
  private var fGrContext: GrRecordingContext?,
  /**
   * C++ original:
   * ```cpp
   * const GraphiteInfo*            fGraphite
   * ```
   */
  private val fGraphite: GraphiteInfo?,
  /**
   * C++ original:
   * ```cpp
   * std::optional<SkRuntimeBlendBuilder> fBuilder
   * ```
   */
  private var fBuilder: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * void build(const char* src, bool allowPrivateAccess = false) {
   *         SkRuntimeEffect::Options options;
   *         if (allowPrivateAccess) {
   *             SkRuntimeEffectPriv::AllowPrivateAccess(&options);
   *         }
   *         auto [effect, errorText] = SkRuntimeEffect::MakeForBlender(SkString(src), options);
   *         if (!effect) {
   *             ERRORF(fReporter, "Effect didn't compile: %s", errorText.c_str());
   *             return;
   *         }
   *         fBuilder.emplace(std::move(effect));
   *     }
   * ```
   */
  public fun build(src: String?, allowPrivateAccess: Boolean = TODO()) {
    TODO("Implement build")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSurface* surface() {
   *         return fSurface.get();
   *     }
   * ```
   */
  public fun surface(): SkSurface {
    TODO("Implement surface")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRuntimeBlendBuilder::BuilderUniform uniform(const char* name) {
   *         return fBuilder->uniform(name);
   *     }
   * ```
   */
  public fun uniform(name: String?): SkRuntimeBlendBuilder.BuilderUniform {
    TODO("Implement uniform")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRuntimeBlendBuilder::BuilderChild child(const char* name) {
   *         return fBuilder->child(name);
   *     }
   * ```
   */
  public fun child(name: String?): SkRuntimeBlendBuilder.BuilderChild {
    TODO("Implement child")
  }

  /**
   * C++ original:
   * ```cpp
   * void test(std::array<uint32_t, 4> expected, PreTestFn preTestCallback = nullptr) {
   *         auto blender = fBuilder->makeBlender();
   *         if (!blender) {
   *             ERRORF(fReporter, "Effect didn't produce a blender");
   *             return;
   *         }
   *
   *         SkCanvas* canvas = fSurface->getCanvas();
   *         SkPaint paint;
   *         paint.setBlender(std::move(blender));
   *         paint.setColor(SK_ColorGRAY);
   *
   *         paint_canvas(canvas, &paint, preTestCallback);
   *
   *         verify_2x2_surface_results(fReporter, fBuilder->effect(), fSurface.get(), expected);
   *     }
   * ```
   */
  public fun test(expected: Array<UInt>, preTestCallback: PreTestFn = TODO()) {
    TODO("Implement test")
  }

  /**
   * C++ original:
   * ```cpp
   * void test(uint32_t expected, PreTestFn preTestCallback = nullptr) {
   *         this->test({expected, expected, expected, expected}, preTestCallback);
   *     }
   * ```
   */
  public fun test(expected: UInt, preTestCallback: PreTestFn = TODO()) {
    TODO("Implement test")
  }
}
