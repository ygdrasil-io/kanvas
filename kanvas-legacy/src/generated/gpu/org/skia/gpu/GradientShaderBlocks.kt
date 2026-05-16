package org.skia.gpu

import kotlin.Any
import kotlin.Boolean
import kotlin.Float
import kotlin.FloatArray
import kotlin.Int
import kotlin.IntArray

/**
 * C++ original:
 * ```cpp
 * struct GradientShaderBlocks {
 *     struct GradientData {
 *         // The number of stops stored internal to this data structure before falling back to
 *         // bitmap storage.
 *         static constexpr int kNumInternalStorageStops = 8;
 *
 *         // This ctor is used during pre-compilation when we don't have enough information to
 *         // extract uniform data. However, we must be able to provide enough data to make all the
 *         // relevant decisions about which code snippets to use.
 *         GradientData(SkShaderBase::GradientType, int numStops, bool useStorageBuffer);
 *
 *         // This ctor is used when extracting information from PaintParams. It must provide
 *         // enough data to generate the uniform data the selected code snippet will require.
 *         GradientData(SkShaderBase::GradientType,
 *                      SkPoint point0, SkPoint point1,
 *                      float radius0, float radius1,
 *                      float bias, float scale,
 *                      SkTileMode,
 *                      int numStops,
 *                      const SkPMColor4f* colors,
 *                      const float* offsets,
 *                      const SkGradientBaseShader* shader,
 *                      sk_sp<TextureProxy> colorsAndOffsetsProxy,
 *                      bool useStorageBuffer,
 *                      const SkGradient::Interpolation&);
 *
 *         bool operator==(const GradientData& rhs) const = delete;
 *         bool operator!=(const GradientData& rhs) const = delete;
 *
 *         // Layout options.
 *         SkShaderBase::GradientType fType;
 *         SkPoint                    fPoints[2];
 *         float                      fRadii[2];
 *
 *         // Layout options for sweep gradient.
 *         float                  fBias;
 *         float                  fScale;
 *
 *         SkTileMode             fTM;
 *         int                    fNumStops;
 *         bool                   fUseStorageBuffer;
 *
 *         // For gradients w/ <= kNumInternalStorageStops stops we use fColors and fOffsets.
 *         // The offsets are packed into a single float4 to save space when the layout is std140.
 *         //
 *         // Otherwise when storage buffers are preferred, we save the colors and offsets pointers
 *         // to fSrcColors and fSrcOffsets so we can directly copy to the gatherer gradient buffer,
 *         // else we pack the data into the fColorsAndOffsetsProxy texture.
 *         SkPMColor4f                   fColors[kNumInternalStorageStops];
 *         SkV4                          fOffsets[kNumInternalStorageStops / 4];
 *         sk_sp<TextureProxy>           fColorsAndOffsetsProxy;
 *         const SkPMColor4f*            fSrcColors;
 *         const float*                  fSrcOffsets;
 *         const SkGradientBaseShader*   fSrcShader;
 *
 *         SkGradient::Interpolation     fInterpolation;
 *     };
 *
 *     static void AddBlock(const KeyContext&, const GradientData&);
 * }
 * ```
 */
public open class GradientShaderBlocks {
  public data class GradientData public constructor(
    public var fType: Int,
    public var fPoints: IntArray,
    public var fRadii: FloatArray,
    public var fBias: Float,
    public var fScale: Float,
    public var fTM: Int,
    public var fNumStops: Int,
    public var fUseStorageBuffer: Boolean,
    public var fColors: IntArray,
    public var fOffsets: IntArray,
    public var fColorsAndOffsetsProxy: Int,
    public val fSrcColors: Int?,
    public val fSrcOffsets: Float?,
    public val fSrcShader: Int?,
    public var fInterpolation: Int,
  ) {
    public override operator fun equals(other: Any?): Boolean {
      TODO("Implement equals")
    }

    public companion object {
      public val kNumInternalStorageStops: Int = TODO("Initialize kNumInternalStorageStops")
    }
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * void GradientShaderBlocks::AddBlock(const KeyContext& keyContext, const GradientData& gradData) {
     *     int bufferOffset = 0;
     *     if (gradData.fNumStops > GradientData::kNumInternalStorageStops && keyContext.recorder()) {
     *         if (gradData.fUseStorageBuffer) {
     *             bufferOffset = write_color_and_offset_bufdata(gradData.fNumStops,
     *                                                           gradData.fSrcColors,
     *                                                           gradData.fSrcOffsets,
     *                                                           gradData.fSrcShader,
     *                                                           keyContext.floatStorageManager());
     *         } else {
     *             SkASSERT(gradData.fColorsAndOffsetsProxy);
     *             keyContext.pipelineDataGatherer()->add(gradData.fColorsAndOffsetsProxy,
     *                           {SkFilterMode::kNearest, SkTileMode::kClamp});
     *         }
     *     }
     *
     *     BuiltInCodeSnippetID codeSnippetID = BuiltInCodeSnippetID::kSolidColorShader;
     *     switch (gradData.fType) {
     *         case SkShaderBase::GradientType::kLinear:
     *             codeSnippetID =
     *                     gradData.fNumStops <= 4 ? BuiltInCodeSnippetID::kLinearGradientShader4
     *                     : gradData.fNumStops <= 8 ? BuiltInCodeSnippetID::kLinearGradientShader8
     *                         : gradData.fUseStorageBuffer
     *                             ? BuiltInCodeSnippetID::kLinearGradientShaderBuffer
     *                             : BuiltInCodeSnippetID::kLinearGradientShaderTexture;
     *             add_linear_gradient_uniform_data(keyContext, codeSnippetID, gradData, bufferOffset);
     *             break;
     *         case SkShaderBase::GradientType::kRadial:
     *             codeSnippetID =
     *                     gradData.fNumStops <= 4 ? BuiltInCodeSnippetID::kRadialGradientShader4
     *                     : gradData.fNumStops <= 8 ? BuiltInCodeSnippetID::kRadialGradientShader8
     *                         : gradData.fUseStorageBuffer
     *                             ? BuiltInCodeSnippetID::kRadialGradientShaderBuffer
     *                             : BuiltInCodeSnippetID::kRadialGradientShaderTexture;
     *             add_radial_gradient_uniform_data(keyContext, codeSnippetID, gradData, bufferOffset);
     *             break;
     *         case SkShaderBase::GradientType::kSweep:
     *             codeSnippetID =
     *                     gradData.fNumStops <= 4 ? BuiltInCodeSnippetID::kSweepGradientShader4
     *                     : gradData.fNumStops <= 8 ? BuiltInCodeSnippetID::kSweepGradientShader8
     *                         : gradData.fUseStorageBuffer
     *                             ? BuiltInCodeSnippetID::kSweepGradientShaderBuffer
     *                             : BuiltInCodeSnippetID::kSweepGradientShaderTexture;
     *             add_sweep_gradient_uniform_data(keyContext, codeSnippetID, gradData, bufferOffset);
     *             break;
     *         case SkShaderBase::GradientType::kConical:
     *             codeSnippetID =
     *                     gradData.fNumStops <= 4 ? BuiltInCodeSnippetID::kConicalGradientShader4
     *                     : gradData.fNumStops <= 8 ? BuiltInCodeSnippetID::kConicalGradientShader8
     *                         : gradData.fUseStorageBuffer
     *                             ? BuiltInCodeSnippetID::kConicalGradientShaderBuffer
     *                             : BuiltInCodeSnippetID::kConicalGradientShaderTexture;
     *             add_conical_gradient_uniform_data(keyContext, codeSnippetID, gradData, bufferOffset);
     *             break;
     *         case SkShaderBase::GradientType::kNone:
     *         default:
     *             SkDEBUGFAIL("Expected a gradient shader, but it wasn't one.");
     *             break;
     *     }
     *
     *     keyContext.paintParamsKeyBuilder()->addBlock(codeSnippetID);
     * }
     * ```
     */
    public fun addBlock(keyContext: KeyContext, gradData: GradientData) {
      TODO("Implement addBlock")
    }
  }
}
