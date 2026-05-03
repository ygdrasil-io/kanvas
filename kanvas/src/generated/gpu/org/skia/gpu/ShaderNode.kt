package org.skia.gpu

import kotlin.Int
import kotlin.String

/**
 * C++ original:
 * ```cpp
 * class ShaderNode {
 * public:
 *     // ShaderNodes should be created in conjunction with an SkArenaAlloc that owns all nodes.
 *     ShaderNode(const ShaderSnippet* snippet,
 *                SkSpan<ShaderNode*> children,
 *                int codeID,
 *                int keyIndex,
 *                SkSpan<const uint32_t> data)
 *             : fEntry(snippet)
 *             , fChildren(children)
 *             , fCodeID(codeID)
 *             , fKeyIndex(keyIndex)
 *             , fRequiredFlags(snippet->fSnippetRequirementFlags)
 *             , fData(data) {
 *         SkASSERT(children.size() == (size_t) fEntry->fNumChildren);
 *
 *         // Propagate requirement flags from children towards root.
 *         const bool isRuntimeEffect = codeID >= kBuiltInCodeSnippetIDCount;
 *         const bool isCompose = codeID == (int) BuiltInCodeSnippetID::kCompose ||
 *                                codeID == (int) BuiltInCodeSnippetID::kBlendCompose;
 *         for (const ShaderNode* child : children) {
 *             // Mask off flags to not propagate.
 *             SkEnumBitMask<SnippetRequirementFlags> mask =
 *                     SnippetRequirementFlags::kPassthroughLocalCoords |
 *                     SnippetRequirementFlags::kLiftExpression |
 *                     SnippetRequirementFlags::kOmitExpression;
 *             // Runtime effects invoke children with explicit parameters so those requirements never
 *             // need to propagate to the root. Similarly, compose only needs to propagate the
 *             // variable parameters for the inner children.
 *             if (isRuntimeEffect || (isCompose && child == children.back())) {
 *                 mask |= SnippetRequirementFlags::kLocalCoords |
 *                         SnippetRequirementFlags::kPriorStageOutput |
 *                         SnippetRequirementFlags::kBlenderDstColor;
 *             }
 *             fRequiredFlags |= (child->requiredFlags() & ~mask);
 *         }
 *
 *         // Data should only be provided if the snippet has the kStoresSamplerDescData flag.
 *         SkASSERT(fData.empty() || snippet->storesSamplerDescData());
 *     }
 *
 *     std::string generateDefaultPreamble(const ShaderInfo& shaderInfo) const;
 *     std::string invokeAndAssign(const ShaderInfo& shaderInfo,
 *                                 const ShaderSnippet::Args& args,
 *                                 std::string* funcBody) const;
 *
 *     std::string getExpressionVaryingName() const;
 *
 *     int32_t codeSnippetId() const { return fCodeID; }
 *     int32_t keyIndex() const { return fKeyIndex; }
 *     const ShaderSnippet* entry() const { return fEntry; }
 *
 *     SkEnumBitMask<SnippetRequirementFlags> requiredFlags() const { return fRequiredFlags; }
 *     void setLiftExpressionFlag() { fRequiredFlags |= SnippetRequirementFlags::kLiftExpression; }
 *     void setOmitExpressionFlag() { fRequiredFlags |= SnippetRequirementFlags::kOmitExpression; }
 *     void unsetLocalCoordsFlag() { fRequiredFlags &= ~SnippetRequirementFlags::kLocalCoords; }
 *
 *     int numChildren() const { return fEntry->fNumChildren; }
 *     SkSpan<ShaderNode*> children() { return fChildren; }
 *     SkSpan<const ShaderNode*> children() const {
 *         return SkSpan<const ShaderNode*>(const_cast<const ShaderNode**>(fChildren.data()),
 *                                          fChildren.size());
 *     }
 *     const ShaderNode* child(int childIndex) const { return fChildren[childIndex]; }
 *
 *     SkSpan<const uint32_t> data() const { return fData; }
 *
 * private:
 *     const ShaderSnippet* fEntry; // Owned by the ShaderCodeDictionary
 *     SkSpan<ShaderNode*> fChildren; // Owned by the ShaderInfo's arena
 *
 *     int32_t fCodeID;
 *     int32_t fKeyIndex; // index back to PaintParamsKey, unique across nodes within a ShaderInfo
 *
 *     SkEnumBitMask<SnippetRequirementFlags> fRequiredFlags;
 *     SkSpan<const uint32_t> fData; // Subspan of PaintParamsKey's fData; shares same owner
 * }
 * ```
 */
public data class ShaderNode public constructor(
  /**
   * C++ original:
   * ```cpp
   * const ShaderSnippet* fEntry
   * ```
   */
  private val fEntry: ShaderSnippet?,
  /**
   * C++ original:
   * ```cpp
   * SkSpan<ShaderNode*> fChildren
   * ```
   */
  private var fChildren: Int,
  /**
   * C++ original:
   * ```cpp
   * int32_t fCodeID
   * ```
   */
  private var fCodeID: Int,
  /**
   * C++ original:
   * ```cpp
   * int32_t fKeyIndex
   * ```
   */
  private var fKeyIndex: Int,
  /**
   * C++ original:
   * ```cpp
   * SkEnumBitMask<SnippetRequirementFlags> fRequiredFlags
   * ```
   */
  private var fRequiredFlags: Int,
  /**
   * C++ original:
   * ```cpp
   * SkSpan<const uint32_t> fData
   * ```
   */
  private var fData: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * std::string ShaderNode::generateDefaultPreamble(const ShaderInfo& shaderInfo) const {
   *     if (this->numChildren() == 0) {
   *         // We don't need a helper function to wrap the snippet's static function
   *         return "";
   *     }
   *
   *     std::string code = emit_helper_declaration(this) + " {";
   *
   *     // Invoke each child with unmodified input values and collect in a list of local variables
   *     STArray<2, std::string> childOutputVarNames;
   *     for (const ShaderNode* child : this->children()) {
   *         // Emit glue code into our helper function body (i.e. lifting the child execution up front
   *         // so their outputs can be passed to the static module function for the node's snippet).
   *         childOutputVarNames.push_back(
   *                 child->invokeAndAssign(shaderInfo, ShaderSnippet::kDefaultArgs, &code));
   *     }
   *
   *     // Finally, invoke the snippet from the helper function, passing uniforms and child outputs.
   *     STArray<3, std::string> params;
   *     append_defaults(&params, this, &ShaderSnippet::kDefaultArgs);
   *     append_uniforms(&params, shaderInfo, this, childOutputVarNames);
   *
   *     SkSL::String::appendf(&code,
   *                               "return %s(%s);"
   *                           "}",
   *                           this->entry()->fStaticFunctionName,
   *                           stitch_csv(params).c_str());
   *     return code;
   * }
   * ```
   */
  public fun generateDefaultPreamble(shaderInfo: ShaderInfo): Int {
    TODO("Implement generateDefaultPreamble")
  }

  /**
   * C++ original:
   * ```cpp
   * std::string ShaderNode::invokeAndAssign(const ShaderInfo& shaderInfo,
   *                                         const ShaderSnippet::Args& args,
   *                                         std::string* funcBody) const {
   *     std::string expr = invoke_node(shaderInfo, this, args);
   *     std::string outputVar = get_mangled_name("outColor", this->keyIndex());
   * #if defined(SK_DEBUG)
   *     SkSL::String::appendf(funcBody,
   *                           "// [%d] %s\n"
   *                           "half4 %s = %s;",
   *                           this->keyIndex(),
   *                           this->entry()->fName,
   *                           outputVar.c_str(),
   *                           expr.c_str());
   * #else
   *     SkSL::String::appendf(funcBody,
   *                           "half4 %s = %s;",
   *                           outputVar.c_str(),
   *                           expr.c_str());
   * #endif
   *     return outputVar;
   * }
   * ```
   */
  public fun invokeAndAssign(
    shaderInfo: ShaderInfo,
    args: ShaderSnippet.Args,
    funcBody: String?,
  ): Int {
    TODO("Implement invokeAndAssign")
  }

  /**
   * C++ original:
   * ```cpp
   * std::string ShaderNode::getExpressionVaryingName() const {
   *     return get_mangled_name(this->entry()->fName, this->keyIndex()) + "_Var";
   * }
   * ```
   */
  public fun getExpressionVaryingName(): Int {
    TODO("Implement getExpressionVaryingName")
  }

  /**
   * C++ original:
   * ```cpp
   * int32_t codeSnippetId() const { return fCodeID; }
   * ```
   */
  public fun codeSnippetId(): Int {
    TODO("Implement codeSnippetId")
  }

  /**
   * C++ original:
   * ```cpp
   * int32_t keyIndex() const { return fKeyIndex; }
   * ```
   */
  public fun keyIndex(): Int {
    TODO("Implement keyIndex")
  }

  /**
   * C++ original:
   * ```cpp
   * const ShaderSnippet* entry() const { return fEntry; }
   * ```
   */
  public fun entry(): ShaderSnippet {
    TODO("Implement entry")
  }

  /**
   * C++ original:
   * ```cpp
   * SkEnumBitMask<SnippetRequirementFlags> requiredFlags() const { return fRequiredFlags; }
   * ```
   */
  public fun requiredFlags(): Int {
    TODO("Implement requiredFlags")
  }

  /**
   * C++ original:
   * ```cpp
   * void setLiftExpressionFlag() { fRequiredFlags |= SnippetRequirementFlags::kLiftExpression; }
   * ```
   */
  public fun setLiftExpressionFlag() {
    TODO("Implement setLiftExpressionFlag")
  }

  /**
   * C++ original:
   * ```cpp
   * void setOmitExpressionFlag() { fRequiredFlags |= SnippetRequirementFlags::kOmitExpression; }
   * ```
   */
  public fun setOmitExpressionFlag() {
    TODO("Implement setOmitExpressionFlag")
  }

  /**
   * C++ original:
   * ```cpp
   * void unsetLocalCoordsFlag() { fRequiredFlags &= ~SnippetRequirementFlags::kLocalCoords; }
   * ```
   */
  public fun unsetLocalCoordsFlag() {
    TODO("Implement unsetLocalCoordsFlag")
  }

  /**
   * C++ original:
   * ```cpp
   * int numChildren() const { return fEntry->fNumChildren; }
   * ```
   */
  public fun numChildren(): Int {
    TODO("Implement numChildren")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<ShaderNode*> children() { return fChildren; }
   * ```
   */
  public fun children(): Int {
    TODO("Implement children")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const ShaderNode*> children() const {
   *         return SkSpan<const ShaderNode*>(const_cast<const ShaderNode**>(fChildren.data()),
   *                                          fChildren.size());
   *     }
   * ```
   */
  public fun child(childIndex: Int): ShaderNode {
    TODO("Implement child")
  }

  /**
   * C++ original:
   * ```cpp
   * const ShaderNode* child(int childIndex) const { return fChildren[childIndex]; }
   * ```
   */
  public fun `data`(): Int {
    TODO("Implement data")
  }
}
