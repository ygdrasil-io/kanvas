package org.skia.core

import kotlin.Boolean
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer
import org.skia.tests.ShaderType
import undefined.SkColor4f

/**
 * C++ original:
 * ```cpp
 * class SkColorShader : public SkShaderBase {
 * public:
 *     /** Create a ColorShader wrapping the given sRGB color.
 *     */
 *     explicit SkColorShader(const SkColor4f& c) : fColor(c) {}
 *
 *     bool isOpaque() const override { return fColor.isOpaque(); }
 *     bool isConstant(SkColor4f* color = nullptr) const override {
 *         if (color) {
 *             *color = fColor;
 *         }
 *         return true;
 *     }
 *
 *     ShaderType type() const override { return ShaderType::kColor; }
 *
 *     const SkColor4f& color() const { return fColor; }
 *
 * private:
 *     friend void ::SkRegisterColorShaderFlattenable();
 *     SK_FLATTENABLE_HOOKS(SkColorShader)
 *
 *     void flatten(SkWriteBuffer&) const override;
 *
 *     bool onAsLuminanceColor(SkColor4f* lum) const override {
 *         *lum = fColor;
 *         return true;
 *     }
 *
 *     bool appendStages(const SkStageRec&, const SkShaders::MatrixRec&) const override;
 *
 *     // The color is stored in extended sRGB, regardless of the original color space that was
 *     // passed into SkShaders::Color().
 *     const SkColor4f fColor;
 * }
 * ```
 */
public open class SkColorShader public constructor(
  c: SkColor4f,
) : SkShaderBase() {
  /**
   * C++ original:
   * ```cpp
   * const SkColor4f fColor
   * ```
   */
  private val fColor: SkColor4f = TODO("Initialize fColor")

  /**
   * C++ original:
   * ```cpp
   * bool isOpaque() const override { return fColor.isOpaque(); }
   * ```
   */
  public override fun isOpaque(): Boolean {
    TODO("Implement isOpaque")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isConstant(SkColor4f* color = nullptr) const override {
   *         if (color) {
   *             *color = fColor;
   *         }
   *         return true;
   *     }
   * ```
   */
  public override fun isConstant(color: SkColor4f? = TODO()): Boolean {
    TODO("Implement isConstant")
  }

  /**
   * C++ original:
   * ```cpp
   * ShaderType type() const override { return ShaderType::kColor; }
   * ```
   */
  public override fun type(): ShaderType {
    TODO("Implement type")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkColor4f& color() const { return fColor; }
   * ```
   */
  public fun color(): SkColor4f {
    TODO("Implement color")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkColorShader::flatten(SkWriteBuffer& buffer) const {
   *     buffer.writeColor4f(fColor);
   * }
   * ```
   */
  public override fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onAsLuminanceColor(SkColor4f* lum) const override {
   *         *lum = fColor;
   *         return true;
   *     }
   * ```
   */
  public override fun onAsLuminanceColor(lum: SkColor4f?): Boolean {
    TODO("Implement onAsLuminanceColor")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkColorShader::appendStages(const SkStageRec& rec, const SkShaders::MatrixRec&) const {
   *     SkColor4f color = fColor;
   *     SkColorSpaceXformSteps(sk_srgb_singleton(), kUnpremul_SkAlphaType,
   *                            rec.fDstCS,          kPremul_SkAlphaType).apply(color.vec());
   *     rec.fPipeline->appendConstantColor(rec.fAlloc, color.vec());
   *     return true;
   * }
   * ```
   */
  public override fun appendStages(rec: SkStageRec, param1: MatrixRec): Boolean {
    TODO("Implement appendStages")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFlattenable> SkColorShader::CreateProc(SkReadBuffer& buffer) {
   *     if (buffer.isVersionLT(SkPicturePriv::Version::kCombineColorShaders)) {
   *         // Stored an 8-bit color only.
   *         return SkShaders::Color(buffer.readColor());
   *     } else {
   *         // Stores a floating-point color in sRGB.
   *         SkColor4f color;
   *         buffer.readColor4f(&color);
   *         return SkShaders::Color(color, SkColorSpace::MakeSRGB());
   *     }
   * }
   * ```
   */
  public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
    TODO("Implement createProc")
  }
}
