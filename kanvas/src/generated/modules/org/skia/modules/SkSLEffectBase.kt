package org.skia.modules

import kotlin.Int
import kotlin.String
import org.skia.effects.SkRuntimeEffect
import org.skia.foundation.SkData
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class SkSLEffectBase {
 * public:
 *     SkSLEffectBase(const skjson::ArrayValue& jprops,
 *                    const AnimationBuilder& abuilder)
 *     {
 *         if (jprops.size() < 1) {
 *             return;
 *         }
 *         const skjson::ObjectValue* jSkSL = jprops[kSkSL_index];
 *         if (!jSkSL) {
 *             return;
 *         }
 *         const skjson::StringValue* jShader = (*jSkSL)["sh"];
 *         if (!jShader) {
 *             return;
 *         }
 *         SkString shader = SkString(jShader->begin(), jShader->size());
 *         auto result = SkRuntimeEffect::MakeForShader(shader, {});
 *         if (!result.effect) {
 *             abuilder.log(Logger::Level::kError, nullptr, "Failed to parse SkSL shader: %s",
 *                result.errorText.c_str());
 *             return;
 *         }
 *         fEffect = std::move(result.effect);
 *     }
 * protected:
 *     enum : size_t {
 *         kSkSL_index = 0,
 *         kFirstUniform_index = 1,
 *     };
 *
 *     enum : size_t {
 *         kSkSLProp_uniform = 0,   // Maps to the integer value 1
 *         kSkSLProp_image = 98, // Maps to the integer value 98
 *         kSkSLProp_layer = 99  // Maps to the integer value 99
 *     };
 *
 *     struct ChildData {
 *         int type;
 *         SkString name;
 *         SkRuntimeEffect::ChildPtr child;
 *     };
 *
 *     void bindUniforms(const skjson::ArrayValue& jprops,
 *                       const AnimationBuilder& abuilder,
 *                       AnimatablePropertyContainer * const &container) {
 *         // construct dynamic uniform list from jprops, skip SkSL property
 *         for (size_t i = kFirstUniform_index; i < jprops.size(); i++) {
 *             const skjson::ObjectValue* jprop = jprops[i];
 *             if (!jprop) { continue; }
 *             const skjson::StringValue* uniformName = (*jprop)["nm"];
 *             if (!uniformName) { continue; }
 *             int type = ParseDefault<int>((*jprop)["ty"], kSkSLProp_uniform);
 *             if (type == kSkSLProp_uniform) {
 *                 auto uniformTuple = std::make_tuple(SkString(uniformName->begin(),
 *                                                             uniformName->size()),
 *                                                     std::make_unique<VectorValue>());
 *                 fUniforms.push_back(std::move(uniformTuple));
 *                 container->bind(abuilder, (*jprop)["v"], std::get<1>(fUniforms.back()).get());
 *             } else if (type == kSkSLProp_image) {
 *                 const skjson::ObjectValue* jimageRef = (*jprop)["v"];
 *                 if (!jimageRef) {
 *                     continue;
 *                 }
 *
 *                 const AnimationBuilder::ScopedAssetRef footageAsset(&abuilder, *jimageRef);
 *                 const auto* asset_info = abuilder.loadFootageAsset(*footageAsset);
 *                 if (asset_info && asset_info->fAsset) {
 *                     // TODO: instead of resolving shaders here, save a collection of footage assets
 *                     // onSync, grab the correct frameData and create a shader then
 *                     auto frameData = asset_info->fAsset->getFrameData(0);
 *                     SkSamplingOptions sampling(SkFilterMode::kLinear);
 *                     fChildren.push_back({type, SkString(uniformName->begin(), uniformName->size()),
 *                                             frameData.image->makeShader(sampling)});
 *                 } else {
 *                     SkDebugf("cannot find asset for custom shader effect");
 *                 }
 *             } else if (type == kSkSLProp_layer) { /* layer content */
 *                 fChildren.push_back({type, SkString(uniformName->begin(), uniformName->size()),
 *                     SkRuntimeEffect::ChildPtr()});
 *             }
 *         }
 *     }
 *
 *     sk_sp<SkData> buildUniformData() const {
 *         auto uniformData = SkData::MakeZeroInitialized(fEffect->uniformSize());
 *         SkASSERT(uniformData);
 *         for (const auto& uniform : fUniforms) {
 *             const auto& name = std::get<0>(uniform);
 *             const auto& data = std::get<1>(uniform);
 *             auto metadata = fEffect->findUniform(name.c_str());
 *             if (metadata && metadata->count == static_cast<int>(data->size())) {
 *                 auto dst = reinterpret_cast<uint8_t*>(uniformData->writable_data())
 *                     + metadata->offset;
 *                 memcpy(reinterpret_cast<void*>(dst), data->data(), data->size() * sizeof(float));
 *             } else {
 *                 SkDebugf("cannot set malformed uniform: %s\n", name.c_str());
 *             }
 *         }
 *         return uniformData;
 *     }
 *
 *     std::vector<SkRuntimeEffect::ChildPtr> buildChildrenData(sk_sp<SkSLShaderNode> node) const {
 *         std::vector<SkRuntimeEffect::ChildPtr> childrenData(fEffect->children().size());
 *         for (const auto& childData : fChildren) {
 *             auto metadata = fEffect->findChild(childData.name.c_str());
 *             if (childData.type == kSkSLProp_layer) {
 *                 childrenData[metadata->index] = (node->contentShader());
 *             } else if (childData.type == kSkSLProp_image) {
 *                 childrenData[metadata->index] = childData.child;
 *             }
 *         }
 *         return childrenData;
 *     }
 *     sk_sp<SkRuntimeEffect> fEffect;
 *     std::vector<std::tuple<SkString, std::unique_ptr<VectorValue>>> fUniforms;
 *     std::vector<ChildData> fChildren;
 * }
 * ```
 */
public open class SkSLEffectBase public constructor(
  jprops: ArrayValue,
  abuilder: AnimationBuilder,
) {
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
   * std::vector<ChildData> fChildren
   * ```
   */
  protected var fChildren: Int = TODO("Initialize fChildren")

  /**
   * C++ original:
   * ```cpp
   * void bindUniforms(const skjson::ArrayValue& jprops,
   *                       const AnimationBuilder& abuilder,
   *                       AnimatablePropertyContainer * const &container) {
   *         // construct dynamic uniform list from jprops, skip SkSL property
   *         for (size_t i = kFirstUniform_index; i < jprops.size(); i++) {
   *             const skjson::ObjectValue* jprop = jprops[i];
   *             if (!jprop) { continue; }
   *             const skjson::StringValue* uniformName = (*jprop)["nm"];
   *             if (!uniformName) { continue; }
   *             int type = ParseDefault<int>((*jprop)["ty"], kSkSLProp_uniform);
   *             if (type == kSkSLProp_uniform) {
   *                 auto uniformTuple = std::make_tuple(SkString(uniformName->begin(),
   *                                                             uniformName->size()),
   *                                                     std::make_unique<VectorValue>());
   *                 fUniforms.push_back(std::move(uniformTuple));
   *                 container->bind(abuilder, (*jprop)["v"], std::get<1>(fUniforms.back()).get());
   *             } else if (type == kSkSLProp_image) {
   *                 const skjson::ObjectValue* jimageRef = (*jprop)["v"];
   *                 if (!jimageRef) {
   *                     continue;
   *                 }
   *
   *                 const AnimationBuilder::ScopedAssetRef footageAsset(&abuilder, *jimageRef);
   *                 const auto* asset_info = abuilder.loadFootageAsset(*footageAsset);
   *                 if (asset_info && asset_info->fAsset) {
   *                     // TODO: instead of resolving shaders here, save a collection of footage assets
   *                     // onSync, grab the correct frameData and create a shader then
   *                     auto frameData = asset_info->fAsset->getFrameData(0);
   *                     SkSamplingOptions sampling(SkFilterMode::kLinear);
   *                     fChildren.push_back({type, SkString(uniformName->begin(), uniformName->size()),
   *                                             frameData.image->makeShader(sampling)});
   *                 } else {
   *                     SkDebugf("cannot find asset for custom shader effect");
   *                 }
   *             } else if (type == kSkSLProp_layer) { /* layer content */
   *                 fChildren.push_back({type, SkString(uniformName->begin(), uniformName->size()),
   *                     SkRuntimeEffect::ChildPtr()});
   *             }
   *         }
   *     }
   * ```
   */
  protected fun bindUniforms(
    jprops: ArrayValue,
    abuilder: AnimationBuilder,
    container: Int,
  ) {
    TODO("Implement bindUniforms")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkData> buildUniformData() const {
   *         auto uniformData = SkData::MakeZeroInitialized(fEffect->uniformSize());
   *         SkASSERT(uniformData);
   *         for (const auto& uniform : fUniforms) {
   *             const auto& name = std::get<0>(uniform);
   *             const auto& data = std::get<1>(uniform);
   *             auto metadata = fEffect->findUniform(name.c_str());
   *             if (metadata && metadata->count == static_cast<int>(data->size())) {
   *                 auto dst = reinterpret_cast<uint8_t*>(uniformData->writable_data())
   *                     + metadata->offset;
   *                 memcpy(reinterpret_cast<void*>(dst), data->data(), data->size() * sizeof(float));
   *             } else {
   *                 SkDebugf("cannot set malformed uniform: %s\n", name.c_str());
   *             }
   *         }
   *         return uniformData;
   *     }
   * ```
   */
  protected fun buildUniformData(): SkSp<SkData> {
    TODO("Implement buildUniformData")
  }

  /**
   * C++ original:
   * ```cpp
   * std::vector<SkRuntimeEffect::ChildPtr> buildChildrenData(sk_sp<SkSLShaderNode> node) const {
   *         std::vector<SkRuntimeEffect::ChildPtr> childrenData(fEffect->children().size());
   *         for (const auto& childData : fChildren) {
   *             auto metadata = fEffect->findChild(childData.name.c_str());
   *             if (childData.type == kSkSLProp_layer) {
   *                 childrenData[metadata->index] = (node->contentShader());
   *             } else if (childData.type == kSkSLProp_image) {
   *                 childrenData[metadata->index] = childData.child;
   *             }
   *         }
   *         return childrenData;
   *     }
   * ```
   */
  protected fun buildChildrenData(node: SkSp<SkSLShaderNode>): Int {
    TODO("Implement buildChildrenData")
  }

  public data class ChildData public constructor(
    public var type: Int,
    public var name: String,
    public var child: SkRuntimeEffect.ChildPtr,
  )

  public companion object {
    public val kSkSLIndex: Int = TODO("Initialize kSkSLIndex")

    public val kFirstUniformIndex: Int = TODO("Initialize kFirstUniformIndex")

    public val kSkSLPropUniform: Int = TODO("Initialize kSkSLPropUniform")

    public val kSkSLPropImage: Int = TODO("Initialize kSkSLPropImage")

    public val kSkSLPropLayer: Int = TODO("Initialize kSkSLPropLayer")
  }
}
