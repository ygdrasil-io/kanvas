package org.skia.tests

import kotlin.Boolean
import kotlin.Double
import kotlin.Float
import kotlin.String
import kotlin.UInt
import org.skia.effects.SkRuntimeEffect
import org.skia.foundation.SkSp
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class RuntimeShaderGM : public skiagm::GM {
 * public:
 *     RuntimeShaderGM(const char* name, SkISize size, const char* sksl, uint32_t flags = 0)
 *             : fName(name), fSize(size), fFlags(flags), fSkSL(sksl) {}
 *
 *     void onOnceBeforeDraw() override {
 *         auto [effect, error] = (fFlags & kColorFilter_RTFlag)
 *                                        ? SkRuntimeEffect::MakeForColorFilter(fSkSL)
 *                                        : SkRuntimeEffect::MakeForShader(fSkSL);
 *         if (!effect) {
 *             SkDebugf("RuntimeShader error: %s\n", error.c_str());
 *         }
 *         fEffect = std::move(effect);
 *     }
 *
 *     bool runAsBench() const override { return SkToBool(fFlags & kBench_RTFlag); }
 *     SkString getName() const override { return fName; }
 *     SkISize getISize() override { return fSize; }
 *
 *     bool onAnimate(double nanos) override {
 *         fSecs = nanos / (1000 * 1000 * 1000);
 *         return SkToBool(fFlags & kAnimate_RTFlag);
 *     }
 *
 * protected:
 *     SkString fName;
 *     SkISize  fSize;
 *     uint32_t fFlags;
 *     float    fSecs = 0.0f;
 *
 *     SkString fSkSL;
 *     sk_sp<SkRuntimeEffect> fEffect;
 * }
 * ```
 */
public open class RuntimeShaderGM public constructor(
  name: String?,
  size: SkISize,
  sksl: String?,
  flags: UInt = TODO(),
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString fName
   * ```
   */
  protected var fName: String = TODO("Initialize fName")

  /**
   * C++ original:
   * ```cpp
   * SkISize  fSize
   * ```
   */
  protected var fSize: SkISize = TODO("Initialize fSize")

  /**
   * C++ original:
   * ```cpp
   * uint32_t fFlags
   * ```
   */
  protected var fFlags: UInt = TODO("Initialize fFlags")

  /**
   * C++ original:
   * ```cpp
   * float    fSecs = 0.0f
   * ```
   */
  protected var fSecs: Float = TODO("Initialize fSecs")

  /**
   * C++ original:
   * ```cpp
   * SkString fSkSL
   * ```
   */
  protected var fSkSL: String = TODO("Initialize fSkSL")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkRuntimeEffect> fEffect
   * ```
   */
  protected var fEffect: SkSp<SkRuntimeEffect> = TODO("Initialize fEffect")

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         auto [effect, error] = (fFlags & kColorFilter_RTFlag)
   *                                        ? SkRuntimeEffect::MakeForColorFilter(fSkSL)
   *                                        : SkRuntimeEffect::MakeForShader(fSkSL);
   *         if (!effect) {
   *             SkDebugf("RuntimeShader error: %s\n", error.c_str());
   *         }
   *         fEffect = std::move(effect);
   *     }
   * ```
   */
  public override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * bool runAsBench() const override { return SkToBool(fFlags & kBench_RTFlag); }
   * ```
   */
  public override fun runAsBench(): Boolean {
    TODO("Implement runAsBench")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return fName; }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return fSize; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onAnimate(double nanos) override {
   *         fSecs = nanos / (1000 * 1000 * 1000);
   *         return SkToBool(fFlags & kAnimate_RTFlag);
   *     }
   * ```
   */
  public override fun onAnimate(nanos: Double): Boolean {
    TODO("Implement onAnimate")
  }
}
