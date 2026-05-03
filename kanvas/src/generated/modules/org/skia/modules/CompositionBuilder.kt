package org.skia.modules

import kotlin.Int
import org.skia.foundation.SkNoncopyable
import org.skia.math.SkSize

/**
 * C++ original:
 * ```cpp
 * class CompositionBuilder final : SkNoncopyable {
 * public:
 *     CompositionBuilder(const AnimationBuilder&, const SkSize&, const skjson::ObjectValue&);
 *     ~CompositionBuilder();
 *
 *     sk_sp<sksg::RenderNode> build(const AnimationBuilder&);
 *
 *     LayerBuilder* layerBuilder(int layer_index);
 *
 *     sk_sp<sksg::RenderNode> layerContent(const AnimationBuilder&, int layer_index);
 *
 * private:
 *     const sk_sp<sksg::Transform>& getCameraTransform() const { return fCameraTransform; }
 *
 *     friend class LayerBuilder;
 *
 *     const SkSize              fSize;
 *
 *     std::vector<LayerBuilder> fLayerBuilders;
 *     skia_private::THashMap<int, size_t>   fLayerIndexMap; // Maps layer "ind" to layer builder index.
 *
 *     sk_sp<sksg::Transform>    fCameraTransform;
 * }
 * ```
 */
public class CompositionBuilder public constructor(
  abuilder: AnimationBuilder,
  size: SkSize,
  jcomp: ObjectValue,
) : SkNoncopyable() {
  /**
   * C++ original:
   * ```cpp
   * const SkSize              fSize
   * ```
   */
  private val fSize: Int = TODO("Initialize fSize")

  /**
   * C++ original:
   * ```cpp
   * std::vector<LayerBuilder> fLayerBuilders
   * ```
   */
  private var fLayerBuilders: Int = TODO("Initialize fLayerBuilders")

  /**
   * C++ original:
   * ```cpp
   * skia_private::THashMap<int, size_t>   fLayerIndexMap
   * ```
   */
  private var fLayerIndexMap: Int = TODO("Initialize fLayerIndexMap")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::Transform>    fCameraTransform
   * ```
   */
  private var fCameraTransform: Int = TODO("Initialize fCameraTransform")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> CompositionBuilder::build(const AnimationBuilder& abuilder) {
   *     // First pass - transitively attach layer transform chains.
   *     for (auto& lbuilder : fLayerBuilders) {
   *         lbuilder.buildTransform(abuilder, this);
   *     }
   *
   *     // Second pass - attach actual layer contents and finalize the layer render tree.
   *     std::vector<sk_sp<sksg::RenderNode>> layers;
   *     layers.reserve(fLayerBuilders.size());
   *
   *     int prev_layer_index = -1;
   *     for (auto& lbuilder : fLayerBuilders) {
   *         if (auto layer = lbuilder.buildRenderTree(abuilder, this, prev_layer_index)) {
   *             layers.push_back(std::move(layer));
   *         }
   *         prev_layer_index = lbuilder.index();
   *     }
   *
   *     if (layers.empty()) {
   *         return nullptr;
   *     }
   *
   *     if (layers.size() == 1) {
   *         return std::move(layers[0]);
   *     }
   *
   *     // Layers are painted in bottom->top order.
   *     std::reverse(layers.begin(), layers.end());
   *     layers.shrink_to_fit();
   *
   *     return sksg::Group::Make(std::move(layers));
   * }
   * ```
   */
  public fun build(abuilder: AnimationBuilder): Int {
    TODO("Implement build")
  }

  /**
   * C++ original:
   * ```cpp
   * LayerBuilder* CompositionBuilder::layerBuilder(int layer_index) {
   *     if (layer_index < 0) {
   *         return nullptr;
   *     }
   *
   *     if (const auto* idx = fLayerIndexMap.find(layer_index)) {
   *         return &fLayerBuilders[SkToInt(*idx)];
   *     }
   *
   *     return nullptr;
   * }
   * ```
   */
  public fun layerBuilder(layerIndex: Int): Int {
    TODO("Implement layerBuilder")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> CompositionBuilder::layerContent(const AnimationBuilder& abuilder,
   *                                                          int layer_index) {
   *     if (auto* lbuilder = this->layerBuilder(layer_index)) {
   *         return lbuilder->getContentTree(abuilder, this);
   *     }
   *
   *     return nullptr;
   * }
   * ```
   */
  public fun layerContent(abuilder: AnimationBuilder, layerIndex: Int): Int {
    TODO("Implement layerContent")
  }

  /**
   * C++ original:
   * ```cpp
   * const sk_sp<sksg::Transform>& getCameraTransform() const { return fCameraTransform; }
   * ```
   */
  private fun getCameraTransform(): Int {
    TODO("Implement getCameraTransform")
  }
}
