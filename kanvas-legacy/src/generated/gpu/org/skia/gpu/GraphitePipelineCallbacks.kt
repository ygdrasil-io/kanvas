package org.skia.gpu

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.sksl.Callbacks
import org.skia.sksl.VarDeclaration

/**
 * C++ original:
 * ```cpp
 * class GraphitePipelineCallbacks : public SkSL::PipelineStage::Callbacks {
 * public:
 *     GraphitePipelineCallbacks(const ShaderInfo& shaderInfo,
 *                               const ShaderNode* node,
 *                               std::string* preamble,
 *                               [[maybe_unused]] const SkRuntimeEffect* effect)
 *             : fShaderInfo(shaderInfo)
 *             , fNode(node)
 *             , fPreamble(preamble) {
 *         SkDEBUGCODE(fEffect = effect;)
 *     }
 *
 *     std::string declareUniform(const SkSL::VarDeclaration* decl) override {
 *         std::string result = get_mangled_name(std::string(decl->var()->name()), fNode->keyIndex());
 *         if (fShaderInfo.uniformSsboIndex()) {
 *             result =
 *                     get_storage_buffer_access(fShaderInfo.uniformSsboIndex(), result.c_str());
 *         }
 *         return result;
 *     }
 *
 *     void defineFunction(const char* decl, const char* body, bool isMain) override {
 *         if (isMain) {
 *             SkSL::String::appendf(
 *                     fPreamble,
 *                     "%s { %s }",
 *                     emit_helper_declaration(fNode).c_str(),
 *                     body);
 *         } else {
 *             SkSL::String::appendf(fPreamble, "%s {%s}\n", decl, body);
 *         }
 *     }
 *
 *     void declareFunction(const char* decl) override {
 *         *fPreamble += std::string(decl);
 *     }
 *
 *     void defineStruct(const char* definition) override {
 *         *fPreamble += std::string(definition);
 *     }
 *
 *     void declareGlobal(const char* declaration) override {
 *         *fPreamble += std::string(declaration);
 *     }
 *
 *     std::string sampleShader(int index, std::string coords) override {
 *         ShaderSnippet::Args args = ShaderSnippet::kDefaultArgs;
 *         args.fFragCoord = coords;
 *         return invoke_node(fShaderInfo, fNode->child(index), args);
 *     }
 *
 *     std::string sampleColorFilter(int index, std::string color) override {
 *         ShaderSnippet::Args args = ShaderSnippet::kDefaultArgs;
 *         args.fPriorStageOutput = color;
 *         return invoke_node(fShaderInfo, fNode->child(index), args);
 *     }
 *
 *     std::string sampleBlender(int index, std::string src, std::string dst) override {
 *         ShaderSnippet::Args args = ShaderSnippet::kDefaultArgs;
 *         args.fPriorStageOutput = src;
 *         args.fBlenderDstColor = dst;
 *         return invoke_node(fShaderInfo, fNode->child(index), args);
 *     }
 *
 *     std::string toLinearSrgb(std::string color) override {
 *         SkASSERT(SkRuntimeEffectPriv::UsesColorTransform(fEffect));
 *         // If we use color transforms (e.g. reference [to|from]LinearSrgb(), we dynamically add two
 *         // children to the runtime effect's node after all explicitly declared children. The
 *         // conversion *to* linear srgb is the second-to-last child node, and the conversion *from*
 *         // linear srgb is the last child node.)
 *         const ShaderNode* toLinearSrgbNode = fNode->child(fNode->numChildren() - 2);
 *         SkASSERT(toLinearSrgbNode->codeSnippetId() ==
 *                          (int)BuiltInCodeSnippetID::kColorSpaceXformColorFilter ||
 *                  toLinearSrgbNode->codeSnippetId() ==
 *                          (int)BuiltInCodeSnippetID::kColorSpaceXformPremul ||
 *                  toLinearSrgbNode->codeSnippetId() ==
 *                          (int)BuiltInCodeSnippetID::kColorSpaceXformSRGB);
 *
 *         ShaderSnippet::Args args = ShaderSnippet::kDefaultArgs;
 *         args.fPriorStageOutput = SkSL::String::printf("(%s).rgb1", color.c_str());
 *         std::string xformedColor = invoke_node(fShaderInfo, toLinearSrgbNode, args);
 *         return SkSL::String::printf("(%s).rgb", xformedColor.c_str());
 *     }
 *
 *
 *     std::string fromLinearSrgb(std::string color) override {
 *         SkASSERT(SkRuntimeEffectPriv::UsesColorTransform(fEffect));
 *         // If we use color transforms (e.g. reference [to|from]LinearSrgb()), we dynamically add two
 *         // children to the runtime effect's node after all explicitly declared children. The
 *         // conversion *to* linear srgb is the second-to-last child node, and the conversion *from*
 *         // linear srgb is the last child node.
 *         const ShaderNode* fromLinearSrgbNode = fNode->child(fNode->numChildren() - 1);
 *         SkASSERT(fromLinearSrgbNode->codeSnippetId() ==
 *                          (int)BuiltInCodeSnippetID::kColorSpaceXformColorFilter ||
 *                  fromLinearSrgbNode->codeSnippetId() ==
 *                          (int)BuiltInCodeSnippetID::kColorSpaceXformPremul ||
 *                  fromLinearSrgbNode->codeSnippetId() ==
 *                          (int)BuiltInCodeSnippetID::kColorSpaceXformSRGB);
 *
 *         ShaderSnippet::Args args = ShaderSnippet::kDefaultArgs;
 *         args.fPriorStageOutput = SkSL::String::printf("(%s).rgb1", color.c_str());
 *         std::string xformedColor = invoke_node(fShaderInfo, fromLinearSrgbNode, args);
 *         return SkSL::String::printf("(%s).rgb", xformedColor.c_str());
 *     }
 *
 *     std::string getMangledName(const char* name) override {
 *         return get_mangled_name(name, fNode->keyIndex());
 *     }
 *
 * private:
 *     const ShaderInfo& fShaderInfo;
 *     const ShaderNode* fNode;
 *     std::string* fPreamble;
 *     SkDEBUGCODE(const SkRuntimeEffect* fEffect;)
 * }
 * ```
 */
public open class GraphitePipelineCallbacks public constructor(
  shaderInfo: ShaderInfo,
  node: ShaderNode?,
  preamble: String?,
  effect: Any?,
) : Callbacks() {
  /**
   * C++ original:
   * ```cpp
   * const ShaderInfo& fShaderInfo
   * ```
   */
  private val fShaderInfo: ShaderInfo = TODO("Initialize fShaderInfo")

  /**
   * C++ original:
   * ```cpp
   * const ShaderNode* fNode
   * ```
   */
  private val fNode: ShaderNode? = TODO("Initialize fNode")

  /**
   * C++ original:
   * ```cpp
   * std::string* fPreamble
   * ```
   */
  private var fPreamble: Int? = TODO("Initialize fPreamble")

  /**
   * C++ original:
   * ```cpp
   * std::string declareUniform(const SkSL::VarDeclaration* decl) override {
   *         std::string result = get_mangled_name(std::string(decl->var()->name()), fNode->keyIndex());
   *         if (fShaderInfo.uniformSsboIndex()) {
   *             result =
   *                     get_storage_buffer_access(fShaderInfo.uniformSsboIndex(), result.c_str());
   *         }
   *         return result;
   *     }
   * ```
   */
  public override fun declareUniform(decl: VarDeclaration?): Int {
    TODO("Implement declareUniform")
  }

  /**
   * C++ original:
   * ```cpp
   * void defineFunction(const char* decl, const char* body, bool isMain) override {
   *         if (isMain) {
   *             SkSL::String::appendf(
   *                     fPreamble,
   *                     "%s { %s }",
   *                     emit_helper_declaration(fNode).c_str(),
   *                     body);
   *         } else {
   *             SkSL::String::appendf(fPreamble, "%s {%s}\n", decl, body);
   *         }
   *     }
   * ```
   */
  public override fun defineFunction(
    decl: String?,
    body: String?,
    isMain: Boolean,
  ) {
    TODO("Implement defineFunction")
  }

  /**
   * C++ original:
   * ```cpp
   * void declareFunction(const char* decl) override {
   *         *fPreamble += std::string(decl);
   *     }
   * ```
   */
  public override fun declareFunction(decl: String?) {
    TODO("Implement declareFunction")
  }

  /**
   * C++ original:
   * ```cpp
   * void defineStruct(const char* definition) override {
   *         *fPreamble += std::string(definition);
   *     }
   * ```
   */
  public override fun defineStruct(definition: String?) {
    TODO("Implement defineStruct")
  }

  /**
   * C++ original:
   * ```cpp
   * void declareGlobal(const char* declaration) override {
   *         *fPreamble += std::string(declaration);
   *     }
   * ```
   */
  public override fun declareGlobal(declaration: String?) {
    TODO("Implement declareGlobal")
  }

  /**
   * C++ original:
   * ```cpp
   * std::string sampleShader(int index, std::string coords) override {
   *         ShaderSnippet::Args args = ShaderSnippet::kDefaultArgs;
   *         args.fFragCoord = coords;
   *         return invoke_node(fShaderInfo, fNode->child(index), args);
   *     }
   * ```
   */
  public override fun sampleShader(index: Int, coords: String): Int {
    TODO("Implement sampleShader")
  }

  /**
   * C++ original:
   * ```cpp
   * std::string sampleColorFilter(int index, std::string color) override {
   *         ShaderSnippet::Args args = ShaderSnippet::kDefaultArgs;
   *         args.fPriorStageOutput = color;
   *         return invoke_node(fShaderInfo, fNode->child(index), args);
   *     }
   * ```
   */
  public override fun sampleColorFilter(index: Int, color: String): Int {
    TODO("Implement sampleColorFilter")
  }

  /**
   * C++ original:
   * ```cpp
   * std::string sampleBlender(int index, std::string src, std::string dst) override {
   *         ShaderSnippet::Args args = ShaderSnippet::kDefaultArgs;
   *         args.fPriorStageOutput = src;
   *         args.fBlenderDstColor = dst;
   *         return invoke_node(fShaderInfo, fNode->child(index), args);
   *     }
   * ```
   */
  public override fun sampleBlender(
    index: Int,
    src: String,
    dst: String,
  ): Int {
    TODO("Implement sampleBlender")
  }

  /**
   * C++ original:
   * ```cpp
   * std::string toLinearSrgb(std::string color) override {
   *         SkASSERT(SkRuntimeEffectPriv::UsesColorTransform(fEffect));
   *         // If we use color transforms (e.g. reference [to|from]LinearSrgb(), we dynamically add two
   *         // children to the runtime effect's node after all explicitly declared children. The
   *         // conversion *to* linear srgb is the second-to-last child node, and the conversion *from*
   *         // linear srgb is the last child node.)
   *         const ShaderNode* toLinearSrgbNode = fNode->child(fNode->numChildren() - 2);
   *         SkASSERT(toLinearSrgbNode->codeSnippetId() ==
   *                          (int)BuiltInCodeSnippetID::kColorSpaceXformColorFilter ||
   *                  toLinearSrgbNode->codeSnippetId() ==
   *                          (int)BuiltInCodeSnippetID::kColorSpaceXformPremul ||
   *                  toLinearSrgbNode->codeSnippetId() ==
   *                          (int)BuiltInCodeSnippetID::kColorSpaceXformSRGB);
   *
   *         ShaderSnippet::Args args = ShaderSnippet::kDefaultArgs;
   *         args.fPriorStageOutput = SkSL::String::printf("(%s).rgb1", color.c_str());
   *         std::string xformedColor = invoke_node(fShaderInfo, toLinearSrgbNode, args);
   *         return SkSL::String::printf("(%s).rgb", xformedColor.c_str());
   *     }
   * ```
   */
  public override fun toLinearSrgb(color: String): Int {
    TODO("Implement toLinearSrgb")
  }

  /**
   * C++ original:
   * ```cpp
   * std::string fromLinearSrgb(std::string color) override {
   *         SkASSERT(SkRuntimeEffectPriv::UsesColorTransform(fEffect));
   *         // If we use color transforms (e.g. reference [to|from]LinearSrgb()), we dynamically add two
   *         // children to the runtime effect's node after all explicitly declared children. The
   *         // conversion *to* linear srgb is the second-to-last child node, and the conversion *from*
   *         // linear srgb is the last child node.
   *         const ShaderNode* fromLinearSrgbNode = fNode->child(fNode->numChildren() - 1);
   *         SkASSERT(fromLinearSrgbNode->codeSnippetId() ==
   *                          (int)BuiltInCodeSnippetID::kColorSpaceXformColorFilter ||
   *                  fromLinearSrgbNode->codeSnippetId() ==
   *                          (int)BuiltInCodeSnippetID::kColorSpaceXformPremul ||
   *                  fromLinearSrgbNode->codeSnippetId() ==
   *                          (int)BuiltInCodeSnippetID::kColorSpaceXformSRGB);
   *
   *         ShaderSnippet::Args args = ShaderSnippet::kDefaultArgs;
   *         args.fPriorStageOutput = SkSL::String::printf("(%s).rgb1", color.c_str());
   *         std::string xformedColor = invoke_node(fShaderInfo, fromLinearSrgbNode, args);
   *         return SkSL::String::printf("(%s).rgb", xformedColor.c_str());
   *     }
   * ```
   */
  public override fun fromLinearSrgb(color: String): Int {
    TODO("Implement fromLinearSrgb")
  }

  /**
   * C++ original:
   * ```cpp
   * std::string getMangledName(const char* name) override {
   *         return get_mangled_name(name, fNode->keyIndex());
   *     }
   * ```
   */
  public override fun getMangledName(name: String?): Int {
    TODO("Implement getMangledName")
  }
}
