package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import kotlin.String

/**
 * C++ original:
 * ```cpp
 * struct ShaderSnippet {
 *     struct Args {
 *         std::string fPriorStageOutput;
 *         std::string fBlenderDstColor;
 *         std::string fFragCoord;
 *     };
 *
 *     using GeneratePreambleForSnippetFn = std::string (*)(const ShaderInfo& shaderInfo,
 *                                                          const ShaderNode*);
 *     using GenerateLiftableExpressionFn = std::string (*)(const ShaderInfo& shaderInfo,
 *                                                          const ShaderNode*,
 *                                                          const ShaderSnippet::Args& args);
 *     static const Args kDefaultArgs;
 *
 *     // If this snippet has an expression that can be lifted from the fragment shader to the vertex
 *     // shader, the LiftableExpressionType specifies how the resolved expression is used by
 *     // subsequent shader nodes.
 *     enum class LiftableExpressionType : uint32_t {
 *         kNone,
 *         kLocalCoords,
 *         kPriorStageOutput,
 *     };
 *
 *     ShaderSnippet() = default;
 *
 *     ShaderSnippet(const char* name,
 *                   const char* staticFn,
 *                   SkEnumBitMask<SnippetRequirementFlags> snippetRequirementFlags,
 *                   SkSpan<const Uniform> uniforms,
 *                   SkSpan<const TextureAndSampler> texturesAndSamplers = {},
 *                   GeneratePreambleForSnippetFn preambleGenerator = nullptr,
 *                   int numChildren = 0,
 *                   GenerateLiftableExpressionFn liftableExpression = nullptr,
 *                   LiftableExpressionType liftableExpressionType = LiftableExpressionType::kNone,
 *                   Interpolation liftableExpressionInterpolation = Interpolation::kPerspective)
 *             : fName(name)
 *             , fStaticFunctionName(staticFn)
 *             , fSnippetRequirementFlags(snippetRequirementFlags)
 *             , fUniforms(uniforms)
 *             , fTexturesAndSamplers(texturesAndSamplers)
 *             , fNumChildren(numChildren)
 *             , fPreambleGenerator(preambleGenerator)
 *             , fLiftableExpressionInterpolation(liftableExpressionInterpolation)
 *             , fLiftableExpressionType(liftableExpressionType)
 *             , fLiftableExpressionGenerator(liftableExpression) {
 *         // Must always provide a name; static function is not optional if using the default (null)
 *         // generation logic.
 *         SkASSERT(name);
 *         SkASSERT(staticFn || preambleGenerator);
 *     }
 *
 *     bool needsLocalCoords() const {
 *         return SkToBool(fSnippetRequirementFlags & SnippetRequirementFlags::kLocalCoords);
 *     }
 *     bool needsPriorStageOutput() const {
 *         return SkToBool(fSnippetRequirementFlags & SnippetRequirementFlags::kPriorStageOutput);
 *     }
 *     bool needsBlenderDstColor() const {
 *         return SkToBool(fSnippetRequirementFlags & SnippetRequirementFlags::kBlenderDstColor);
 *     }
 *     bool storesSamplerDescData() const {
 *         return SkToBool(fSnippetRequirementFlags & SnippetRequirementFlags::kStoresSamplerDescData);
 *     }
 *
 *     const char* fName = nullptr;
 *     const char* fStaticFunctionName = nullptr;
 *
 *     // The features and args that this shader snippet requires in order to be invoked
 *     SkEnumBitMask<SnippetRequirementFlags> fSnippetRequirementFlags{SnippetRequirementFlags::kNone};
 *
 *     // If not null, the list of uniforms in `fUniforms` describes an existing struct type declared
 *     // in the Graphite modules with the given name. Instead of inlining the each uniform in the
 *     // top-level interface block or aggregate struct, there will be a single member of this struct's
 *     // type.
 *     const char* fUniformStructName = nullptr;
 *     // If the uniforms are being embedded as a sub-struct, this is the required starting alignment.
 *     int fRequiredAlignment = -1;
 *
 *     skia_private::TArray<Uniform> fUniforms;
 *     skia_private::TArray<TextureAndSampler> fTexturesAndSamplers;
 *
 *     int fNumChildren = 0;
 *     GeneratePreambleForSnippetFn fPreambleGenerator = nullptr;
 *
 *     Interpolation fLiftableExpressionInterpolation = Interpolation::kPerspective;
 *     LiftableExpressionType fLiftableExpressionType = LiftableExpressionType::kNone;
 *     GenerateLiftableExpressionFn fLiftableExpressionGenerator = nullptr;
 * }
 * ```
 */
public data class ShaderSnippet public constructor(
  /**
   * C++ original:
   * ```cpp
   * static const Args kDefaultArgs
   * ```
   */
  public val fName: String?,
  /**
   * C++ original:
   * ```cpp
   * const char* fName = nullptr
   * ```
   */
  public val fStaticFunctionName: String?,
  /**
   * C++ original:
   * ```cpp
   * const char* fStaticFunctionName = nullptr
   * ```
   */
  public var fSnippetRequirementFlags: Int,
  /**
   * C++ original:
   * ```cpp
   * SkEnumBitMask<SnippetRequirementFlags> fSnippetRequirementFlags
   * ```
   */
  public val fUniformStructName: String?,
  /**
   * C++ original:
   * ```cpp
   * const char* fUniformStructName = nullptr
   * ```
   */
  public var fRequiredAlignment: Int,
  /**
   * C++ original:
   * ```cpp
   * int fRequiredAlignment = -1
   * ```
   */
  public var fUniforms: Int,
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<Uniform> fUniforms
   * ```
   */
  public var fTexturesAndSamplers: Int,
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<TextureAndSampler> fTexturesAndSamplers
   * ```
   */
  public var fNumChildren: Int,
  /**
   * C++ original:
   * ```cpp
   * int fNumChildren = 0
   * ```
   */
  public var fPreambleGenerator: Int,
  /**
   * C++ original:
   * ```cpp
   * GeneratePreambleForSnippetFn fPreambleGenerator
   * ```
   */
  public var fLiftableExpressionInterpolation: Int,
  /**
   * C++ original:
   * ```cpp
   * Interpolation fLiftableExpressionInterpolation
   * ```
   */
  public var fLiftableExpressionType: LiftableExpressionType,
  /**
   * C++ original:
   * ```cpp
   * LiftableExpressionType fLiftableExpressionType = LiftableExpressionType::kNone
   * ```
   */
  public var fLiftableExpressionGenerator: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * bool needsLocalCoords() const {
   *         return SkToBool(fSnippetRequirementFlags & SnippetRequirementFlags::kLocalCoords);
   *     }
   * ```
   */
  public fun needsLocalCoords(): Boolean {
    TODO("Implement needsLocalCoords")
  }

  /**
   * C++ original:
   * ```cpp
   * bool needsPriorStageOutput() const {
   *         return SkToBool(fSnippetRequirementFlags & SnippetRequirementFlags::kPriorStageOutput);
   *     }
   * ```
   */
  public fun needsPriorStageOutput(): Boolean {
    TODO("Implement needsPriorStageOutput")
  }

  /**
   * C++ original:
   * ```cpp
   * bool needsBlenderDstColor() const {
   *         return SkToBool(fSnippetRequirementFlags & SnippetRequirementFlags::kBlenderDstColor);
   *     }
   * ```
   */
  public fun needsBlenderDstColor(): Boolean {
    TODO("Implement needsBlenderDstColor")
  }

  /**
   * C++ original:
   * ```cpp
   * bool storesSamplerDescData() const {
   *         return SkToBool(fSnippetRequirementFlags & SnippetRequirementFlags::kStoresSamplerDescData);
   *     }
   * ```
   */
  public fun storesSamplerDescData(): Boolean {
    TODO("Implement storesSamplerDescData")
  }

  public data class Args public constructor(
    public var fPriorStageOutput: Int,
    public var fBlenderDstColor: Int,
    public var fFragCoord: Int,
  )

  public enum class LiftableExpressionType {
    kNone,
    kLocalCoords,
    kPriorStageOutput,
  }

  public companion object {
    public val kDefaultArgs: Args = TODO("Initialize kDefaultArgs")
  }
}
