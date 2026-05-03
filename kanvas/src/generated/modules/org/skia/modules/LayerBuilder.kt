package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.IntArray
import org.skia.math.SkSize

/**
 * C++ original:
 * ```cpp
 * class LayerBuilder final {
 * public:
 *     LayerBuilder(const skjson::ObjectValue& jlayer, const SkSize& comp_size);
 *     LayerBuilder(const LayerBuilder&) = default;
 *     ~LayerBuilder();
 *
 *     int index() const { return fIndex; }
 *
 *     bool isCamera() const;
 *
 *     // Attaches the local and ancestor transform chain for the layer "native" type.
 *     sk_sp<sksg::Transform> buildTransform(const AnimationBuilder&, CompositionBuilder*);
 *
 *     // Attaches the actual layer content and finalizes its render tree.  Called once per layer.
 *     sk_sp<sksg::RenderNode> buildRenderTree(const AnimationBuilder&, CompositionBuilder*,
 *                                             int prev_layer_index);
 *
 *     const sk_sp<sksg::RenderNode>& getContentTree(const AnimationBuilder&, CompositionBuilder*);
 *
 *     const SkSize& size() const { return fInfo.fSize; }
 *
 * private:
 *     enum TransformType : uint8_t {
 *         k2D = 0,
 *         k3D = 1,
 *     };
 *
 *     enum Flags {
 *         // k2DTransformValid = 0x01,  // reserved for cache tracking
 *         // k3DTransformValid = 0x02,  // reserved for cache tracking
 *         kIs3D                = 0x04,  // 3D layer ("ddd": 1) or camera layer
 *         kBuiltContent        = 0x08,  // the content tree has been built
 *     };
 *
 *     bool is3D() const { return fFlags & Flags::kIs3D; }
 *
 *     // Attaches the layer content (excluding motion blur, layer controller, and mattes).
 *     // Can be called transitively, but only once per layer (via getContentTree, which caches
 *     // the result).
 *     sk_sp<sksg::RenderNode> buildContentTree(const AnimationBuilder&, CompositionBuilder*);
 *
 *     // Attaches (if needed) and caches the transform chain for a given layer,
 *     // as either a 2D or 3D chain type.
 *     // Called transitively (and possibly repeatedly) to resolve layer parenting.
 *     sk_sp<sksg::Transform> getTransform(const AnimationBuilder&, CompositionBuilder*,
 *                                         TransformType);
 *
 *     sk_sp<sksg::Transform> getParentTransform(const AnimationBuilder&, CompositionBuilder*,
 *                                               TransformType);
 *
 *     sk_sp<sksg::Transform> doAttachTransform(const AnimationBuilder&, CompositionBuilder*,
 *                                              TransformType);
 *
 *     using LayerBuilderFunc =
 *         sk_sp<sksg::RenderNode> (AnimationBuilder::*)(const skjson::ObjectValue&,
 *                                                       LayerInfo*) const;
 *     struct BuilderInfo {
 *         LayerBuilderFunc fBuilder = nullptr;
 *         uint32_t         fFlags   = 0;
 *     };
 *
 *     const skjson::ObjectValue& fJlayer;
 *     const int                  fIndex;
 *     const int                  fParentIndex;
 *     const int                  fType;
 *     const bool                 fAutoOrient;
 *
 *     LayerInfo                  fInfo;
 *     BuilderInfo                fBuilderInfo;
 *     sk_sp<sksg::Transform>     fLayerTransform;             // this layer's transform node.
 *     sk_sp<sksg::Transform>     fTransformCache[2];          // cached 2D/3D chain for the local node
 *     sk_sp<sksg::RenderNode>    fContentTree;                // render tree for layer content,
 *                                                             // excluding mask/matte and blending
 *
 *     AnimatorScope              fLayerScope;                 // layer-scoped animators
 *     size_t                     fTransformAnimatorCount = 0; // transform-related animator count
 *     uint32_t                   fFlags                  = 0;
 * }
 * ```
 */
public data class LayerBuilder public constructor(
  /**
   * C++ original:
   * ```cpp
   * const skjson::ObjectValue& fJlayer
   * ```
   */
  private val fJlayer: ObjectValue,
  /**
   * C++ original:
   * ```cpp
   * const int                  fIndex
   * ```
   */
  private val fIndex: Int,
  /**
   * C++ original:
   * ```cpp
   * const int                  fParentIndex
   * ```
   */
  private val fParentIndex: Int,
  /**
   * C++ original:
   * ```cpp
   * const int                  fType
   * ```
   */
  private val fType: Int,
  /**
   * C++ original:
   * ```cpp
   * const bool                 fAutoOrient
   * ```
   */
  private val fAutoOrient: Boolean,
  /**
   * C++ original:
   * ```cpp
   * LayerInfo                  fInfo
   * ```
   */
  private var fInfo: Int,
  /**
   * C++ original:
   * ```cpp
   * BuilderInfo                fBuilderInfo
   * ```
   */
  private var fBuilderInfo: BuilderInfo,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::Transform>     fLayerTransform
   * ```
   */
  private var fLayerTransform: Int,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::Transform>     fTransformCache[2]
   * ```
   */
  private var fTransformCache: IntArray,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode>    fContentTree
   * ```
   */
  private var fContentTree: Int,
  /**
   * C++ original:
   * ```cpp
   * AnimatorScope              fLayerScope
   * ```
   */
  private var fLayerScope: Int,
  /**
   * C++ original:
   * ```cpp
   * size_t                     fTransformAnimatorCount
   * ```
   */
  private var fTransformAnimatorCount: Int,
  /**
   * C++ original:
   * ```cpp
   * uint32_t                   fFlags
   * ```
   */
  private var fFlags: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * int index() const { return fIndex; }
   * ```
   */
  public fun index(): Int {
    TODO("Implement index")
  }

  /**
   * C++ original:
   * ```cpp
   * bool LayerBuilder::isCamera() const {
   *     static constexpr int kCameraLayerType = 13;
   *
   *     return fType == kCameraLayerType;
   * }
   * ```
   */
  public fun isCamera(): Boolean {
    TODO("Implement isCamera")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::Transform> LayerBuilder::buildTransform(const AnimationBuilder& abuilder,
   *                                                     CompositionBuilder* cbuilder) {
   *     // Depending on the leaf node type, we treat the whole transform chain as either 2D or 3D.
   *     const auto transform_chain_type = this->is3D() ? TransformType::k3D
   *                                                    : TransformType::k2D;
   *     fLayerTransform = this->getTransform(abuilder, cbuilder, transform_chain_type);
   *
   *     return fLayerTransform;
   * }
   * ```
   */
  public fun buildTransform(abuilder: AnimationBuilder, cbuilder: CompositionBuilder?): Int {
    TODO("Implement buildTransform")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> LayerBuilder::buildRenderTree(const AnimationBuilder& abuilder,
   *                                                       CompositionBuilder* cbuilder,
   *                                                       int prev_layer_index) {
   *     sk_sp<sksg::RenderNode> layer = this->getContentTree(abuilder, cbuilder);
   *
   *     const auto force_seek_count = fBuilderInfo.fFlags & kForceSeek
   *             ? fLayerScope.size()
   *             : fTransformAnimatorCount;
   *
   *     abuilder.fCurrentAnimatorScope->push_back(sk_make_sp<LayerController>(std::move(fLayerScope),
   *                                                                           layer,
   *                                                                           force_seek_count,
   *                                                                           fInfo.fInPoint,
   *                                                                           fInfo.fOutPoint));
   *     const auto& is_hidden = [this]() {
   *         // If present, the 'hd' property controls visibility.
   *         if (const skjson::BoolValue* jhidden = fJlayer["hd"]) {
   *             return **jhidden;
   *         }
   *
   *         // Legacy track matte flag, not supported in Lottie >= 1.0.
   *         // We only observe this in the absence of an explicit `hd` property.
   *         return ParseDefault<bool>(fJlayer["td"], false);
   *     };
   *
   *     if (is_hidden()) {
   *         return nullptr;
   *     }
   *
   *     // Optional matte.
   *     if (const auto matte_mode = ParseDefault<size_t>(fJlayer["tt"], 0)) {
   *         static constexpr sksg::MaskEffect::Mode gMatteModes[] = {
   *             sksg::MaskEffect::Mode::kAlphaNormal, // tt: 1
   *             sksg::MaskEffect::Mode::kAlphaInvert, // tt: 2
   *             sksg::MaskEffect::Mode::kLumaNormal,  // tt: 3
   *             sksg::MaskEffect::Mode::kLumaInvert,  // tt: 4
   *         };
   *
   *         if (matte_mode <= std::size(gMatteModes)) {
   *             int matte_index = ParseDefault<int>(fJlayer["tp"], -1);
   *             if (matte_index < 0) {
   *                 // When 'tp' is not present, assume the matte source is the previous layer
   *                 // (legacy assets).
   *                 matte_index = prev_layer_index;
   *             }
   *
   *             if (matte_index >= 0) {
   *                 layer = sksg::MaskEffect::Make(std::move(layer),
   *                                                cbuilder->layerContent(abuilder, matte_index),
   *                                                gMatteModes[matte_mode - 1]);
   *             }
   *         } else {
   *             abuilder.log(Logger::Level::kError, nullptr,
   *                          "Unknown track matte mode: %zu\n", matte_mode);
   *         }
   *     }
   *
   *     // Finally, attach an optional blend mode.
   *     // NB: blend modes are never applied to matte sources (layer content only).
   *     return abuilder.attachBlendMode(fJlayer, std::move(layer));
   * }
   * ```
   */
  public fun buildRenderTree(
    abuilder: AnimationBuilder,
    cbuilder: CompositionBuilder?,
    prevLayerIndex: Int,
  ): Int {
    TODO("Implement buildRenderTree")
  }

  /**
   * C++ original:
   * ```cpp
   * const sk_sp<sksg::RenderNode>& LayerBuilder::getContentTree(const AnimationBuilder& abuilder,
   *                                                             CompositionBuilder* cbuilder) {
   *     if (!(fFlags & kBuiltContent)) {
   *         // Set the flag first to prevent reference cycles.
   *         fFlags |= Flags::kBuiltContent;
   *
   *         fContentTree = this->buildContentTree(abuilder, cbuilder);
   *     }
   *
   *     return fContentTree;
   * }
   * ```
   */
  public fun getContentTree(abuilder: AnimationBuilder, cbuilder: CompositionBuilder?): Int {
    TODO("Implement getContentTree")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkSize& size() const { return fInfo.fSize; }
   * ```
   */
  public fun size(): SkSize {
    TODO("Implement size")
  }

  /**
   * C++ original:
   * ```cpp
   * bool is3D() const { return fFlags & Flags::kIs3D; }
   * ```
   */
  private fun is3D(): Boolean {
    TODO("Implement is3D")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> LayerBuilder::buildContentTree(const AnimationBuilder& abuilder,
   *                                                        CompositionBuilder* cbuilder) {
   *     const AnimationBuilder::AutoPropertyTracker apt(&abuilder, fJlayer,
   *                                                     PropertyObserver::NodeType::LAYER);
   *
   *     // Switch to the layer animator scope (which at this point holds transform-only animators).
   *     AnimationBuilder::AutoScope ascope(&abuilder, std::move(fLayerScope));
   *
   *     // Potentially null.
   *     sk_sp<sksg::RenderNode> layer;
   *
   *     // Build the layer content fragment.
   *     if (fBuilderInfo.fBuilder) {
   *         layer = (abuilder.*(fBuilderInfo.fBuilder))(fJlayer, &fInfo);
   *     }
   *
   *     // Clip layers with explicit dimensions.
   *     float w = 0, h = 0;
   *     if (::skottie::Parse<float>(fJlayer["w"], &w) && ::skottie::Parse<float>(fJlayer["h"], &h)) {
   *         layer = sksg::ClipEffect::Make(std::move(layer),
   *                                        sksg::Rect::Make(SkRect::MakeWH(w, h)),
   * #ifdef SK_LEGACY_SKOTTIE_CLIPPING
   *                                        /*aa=*/true, /*force_clip=*/false);
   * #else
   *                                        /*aa=*/true, /*force_clip=*/true);
   * #endif
   *     }
   *
   *     // Optional layer mask.
   *     layer = AttachMask(fJlayer["masksProperties"], &abuilder, std::move(layer));
   *
   *     // Does the transform apply to effects also?
   *     // (AE quirk: it doesn't - except for solid layers)
   *     const auto transform_effects = (fBuilderInfo.fFlags & kTransformEffects);
   *
   *     // Attach the transform before effects, when needed.
   *     if (fLayerTransform && !transform_effects) {
   *         layer = sksg::TransformEffect::Make(std::move(layer), fLayerTransform);
   *     }
   *
   *     // Optional layer effects.
   *     if (const skjson::ArrayValue* jeffects = fJlayer["ef"]) {
   *         layer = EffectBuilder(&abuilder, fInfo.fSize, cbuilder)
   *                 .attachEffects(*jeffects, std::move(layer));
   *     }
   *
   *     // Attach the transform after effects, when needed.
   *     if (fLayerTransform && transform_effects) {
   *         layer = sksg::TransformEffect::Make(std::move(layer), std::move(fLayerTransform));
   *     }
   *
   *     // Optional layer styles.
   *     if (const skjson::ArrayValue* jstyles = fJlayer["sy"]) {
   *         layer = EffectBuilder(&abuilder, fInfo.fSize, cbuilder)
   *                 .attachStyles(*jstyles, std::move(layer));
   *     }
   *
   *     // Optional layer opacity.
   *     // TODO: de-dupe this "ks" lookup with matrix above.
   *     if (const skjson::ObjectValue* jtransform = fJlayer["ks"]) {
   *         layer = abuilder.attachOpacity(*jtransform, std::move(layer));
   *     }
   *
   *     // Stash the layer animator scope, to be picked up later in buildRenderTree().
   *     fLayerScope = ascope.release();
   *
   *     abuilder.trackLayerInfo(fInfo);
   *     return layer;
   * }
   * ```
   */
  private fun buildContentTree(abuilder: AnimationBuilder, cbuilder: CompositionBuilder?): Int {
    TODO("Implement buildContentTree")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::Transform> LayerBuilder::getTransform(const AnimationBuilder& abuilder,
   *                                                   CompositionBuilder* cbuilder,
   *                                                   TransformType ttype) {
   *     const auto cache_valid_mask = (1ul << ttype);
   *     if (!(fFlags & cache_valid_mask)) {
   *         // Set valid flag upfront to break cycles.
   *         fFlags |= cache_valid_mask;
   *
   *         const AnimationBuilder::AutoPropertyTracker apt(&abuilder, fJlayer, PropertyObserver::NodeType::LAYER);
   *         AnimationBuilder::AutoScope ascope(&abuilder, std::move(fLayerScope));
   *         fTransformCache[ttype] = this->doAttachTransform(abuilder, cbuilder, ttype);
   *         fLayerScope = ascope.release();
   *         fTransformAnimatorCount = fLayerScope.size();
   *     }
   *
   *     return fTransformCache[ttype];
   * }
   * ```
   */
  private fun getTransform(
    abuilder: AnimationBuilder,
    cbuilder: CompositionBuilder?,
    ttype: TransformType,
  ): Int {
    TODO("Implement getTransform")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::Transform> LayerBuilder::getParentTransform(const AnimationBuilder& abuilder,
   *                                                         CompositionBuilder* cbuilder,
   *                                                         TransformType ttype) {
   *     if (auto* parent_builder = cbuilder->layerBuilder(fParentIndex)) {
   *         // Explicit parent layer.
   *         return parent_builder->getTransform(abuilder, cbuilder, ttype);
   *     }
   *
   *     // Camera layers have no implicit parent transform,
   *     // while regular 3D transform chains are implicitly rooted onto the camera.
   *     if (ttype == TransformType::k3D && !this->isCamera()) {
   *         return cbuilder->getCameraTransform();
   *     }
   *
   *     return nullptr;
   * }
   * ```
   */
  private fun getParentTransform(
    abuilder: AnimationBuilder,
    cbuilder: CompositionBuilder?,
    ttype: TransformType,
  ): Int {
    TODO("Implement getParentTransform")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::Transform> LayerBuilder::doAttachTransform(const AnimationBuilder& abuilder,
   *                                                        CompositionBuilder* cbuilder,
   *                                                        TransformType ttype) {
   *     const skjson::ObjectValue* jtransform = fJlayer["ks"];
   *     if (!jtransform) {
   *         return nullptr;
   *     }
   *
   *     auto parent_transform = this->getParentTransform(abuilder, cbuilder, ttype);
   *
   *     if (this->isCamera()) {
   *         // parent_transform applies to the camera itself => it pre-composes inverted to the
   *         // camera/view/adapter transform.
   *         //
   *         //   T_camera' = T_camera x Inv(parent_transform)
   *         //
   *         return abuilder.attachCamera(fJlayer,
   *                                      *jtransform,
   *                                      sksg::Transform::MakeInverse(std::move(parent_transform)),
   *                                      cbuilder->fSize);
   *     }
   *
   *     return this->is3D()
   *             ? abuilder.attachMatrix3D(*jtransform, std::move(parent_transform), fAutoOrient)
   *             : abuilder.attachMatrix2D(*jtransform, std::move(parent_transform), fAutoOrient);
   * }
   * ```
   */
  private fun doAttachTransform(
    abuilder: AnimationBuilder,
    cbuilder: CompositionBuilder?,
    ttype: TransformType,
  ): Int {
    TODO("Implement doAttachTransform")
  }

  public data class BuilderInfo public constructor(
    public var fBuilder: LayerBuilder,
    public var fFlags: Int,
  )

  public enum class TransformType {
    k2D,
    k3D,
  }

  public enum class Flags {
    kIs3D,
    kBuiltContent,
  }
}
