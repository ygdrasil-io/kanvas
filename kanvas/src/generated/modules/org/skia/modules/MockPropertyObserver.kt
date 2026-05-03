package org.skia.modules

import kotlin.Boolean
import kotlin.CharArray
import kotlin.Int
import kotlin.String
import undefined.ColorPropertyHandle

/**
 * C++ original:
 * ```cpp
 * class MockPropertyObserver final : public PropertyObserver {
 * public:
 *     explicit MockPropertyObserver(bool override_props = false) : fOverrideProps(override_props) {}
 *
 *     struct ColorInfo {
 *         SkString                                      node_name;
 *         std::unique_ptr<skottie::ColorPropertyHandle> handle;
 *     };
 *
 *     struct OpacityInfo {
 *         SkString                                        node_name;
 *         std::unique_ptr<skottie::OpacityPropertyHandle> handle;
 *     };
 *
 *     struct TextInfo {
 *         SkString                                     node_name;
 *         std::unique_ptr<skottie::TextPropertyHandle> handle;
 *     };
 *
 *     struct TransformInfo {
 *         SkString                                          node_name;
 *         std::unique_ptr<skottie::TransformPropertyHandle> handle;
 *     };
 *
 *     void onColorProperty(const char node_name[],
 *             const PropertyObserver::LazyHandle<ColorPropertyHandle>& lh) override {
 *         auto prop_handle = lh();
 *         if (fOverrideProps) {
 *             static constexpr ColorPropertyValue kOverrideColor = 0xffffff00;
 *             prop_handle->set(kOverrideColor);
 *         }
 *         fColors.push_back({SkString(node_name), std::move(prop_handle)});
 *         fColorsWithFullKeypath.push_back({SkString(fCurrentNode.c_str()), lh()});
 *     }
 *
 *     void onOpacityProperty(const char node_name[],
 *             const PropertyObserver::LazyHandle<OpacityPropertyHandle>& lh) override {
 *         auto prop_handle = lh();
 *         if (fOverrideProps) {
 *             static constexpr OpacityPropertyValue kOverrideOpacity = 0.75f;
 *             prop_handle->set(kOverrideOpacity);
 *         }
 *         fOpacities.push_back({SkString(node_name), std::move(prop_handle)});
 *     }
 *
 *     void onTextProperty(const char node_name[],
 *                         const PropertyObserver::LazyHandle<TextPropertyHandle>& lh) override {
 *         auto prop_handle = lh();
 *         if (fOverrideProps) {
 *             static const TextPropertyValue kOverrideText = make_text_prop("foo");
 *             prop_handle->set(kOverrideText);
 *         }
 *         fTexts.push_back({SkString(node_name), std::move(prop_handle)});
 *     }
 *
 *     void onTransformProperty(const char node_name[],
 *             const PropertyObserver::LazyHandle<TransformPropertyHandle>& lh) override {
 *         auto prop_handle = lh();
 *         if (fOverrideProps) {
 *             static constexpr TransformPropertyValue kOverrideTransform = {
 *                 { 100, 100 },
 *                 { 200, 200 },
 *                 {   2,   2 },
 *                 45,
 *                 0,
 *                 0,
 *             };
 *             prop_handle->set(kOverrideTransform);
 *         }
 *         fTransforms.push_back({SkString(node_name), std::move(prop_handle)});
 *     }
 *
 *     void onEnterNode(const char node_name[], PropertyObserver::NodeType node_type) override {
 *         if (node_name == nullptr) {
 *             return;
 *         }
 *         fCurrentNode = fCurrentNode.empty() ? node_name : fCurrentNode + "." + node_name;
 *     }
 *
 *     void onLeavingNode(const char node_name[], PropertyObserver::NodeType node_type) override {
 *         if (node_name == nullptr) {
 *             return;
 *         }
 *         auto length = strlen(node_name);
 *         fCurrentNode =
 *                 fCurrentNode.length() > length
 *                         ? fCurrentNode.substr(0, fCurrentNode.length() - strlen(node_name) - 1)
 *                         : "";
 *     }
 *
 *     const std::vector<ColorInfo>& colors() const { return fColors; }
 *     const std::vector<OpacityInfo>& opacities() const { return fOpacities; }
 *     const std::vector<TextInfo>& texts() const { return fTexts; }
 *     const std::vector<TransformInfo>& transforms() const { return fTransforms; }
 *     const std::vector<ColorInfo>& colorsWithFullKeypath() const {
 *         return fColorsWithFullKeypath;
 *     }
 *
 * private:
 *     const bool                 fOverrideProps;
 *     std::vector<ColorInfo>     fColors;
 *     std::vector<OpacityInfo>   fOpacities;
 *     std::vector<TextInfo>      fTexts;
 *     std::vector<TransformInfo> fTransforms;
 *     std::string                fCurrentNode;
 *     std::vector<ColorInfo>     fColorsWithFullKeypath;
 * }
 * ```
 */
public class MockPropertyObserver public constructor(
  overrideProps: Boolean = TODO(),
) : PropertyObserver() {
  /**
   * C++ original:
   * ```cpp
   * const bool                 fOverrideProps
   * ```
   */
  private val fOverrideProps: Boolean = TODO("Initialize fOverrideProps")

  /**
   * C++ original:
   * ```cpp
   * std::vector<ColorInfo>     fColors
   * ```
   */
  private var fColors: Int = TODO("Initialize fColors")

  /**
   * C++ original:
   * ```cpp
   * std::vector<OpacityInfo>   fOpacities
   * ```
   */
  private var fOpacities: Int = TODO("Initialize fOpacities")

  /**
   * C++ original:
   * ```cpp
   * std::vector<TextInfo>      fTexts
   * ```
   */
  private var fTexts: Int = TODO("Initialize fTexts")

  /**
   * C++ original:
   * ```cpp
   * std::vector<TransformInfo> fTransforms
   * ```
   */
  private var fTransforms: Int = TODO("Initialize fTransforms")

  /**
   * C++ original:
   * ```cpp
   * std::string                fCurrentNode
   * ```
   */
  private var fCurrentNode: Int = TODO("Initialize fCurrentNode")

  /**
   * C++ original:
   * ```cpp
   * std::vector<ColorInfo>     fColorsWithFullKeypath
   * ```
   */
  private var fColorsWithFullKeypath: Int = TODO("Initialize fColorsWithFullKeypath")

  /**
   * C++ original:
   * ```cpp
   * void onColorProperty(const char node_name[],
   *             const PropertyObserver::LazyHandle<ColorPropertyHandle>& lh) override {
   *         auto prop_handle = lh();
   *         if (fOverrideProps) {
   *             static constexpr ColorPropertyValue kOverrideColor = 0xffffff00;
   *             prop_handle->set(kOverrideColor);
   *         }
   *         fColors.push_back({SkString(node_name), std::move(prop_handle)});
   *         fColorsWithFullKeypath.push_back({SkString(fCurrentNode.c_str()), lh()});
   *     }
   * ```
   */
  public override fun onColorProperty(nodeName: CharArray, lh: PropertyObserver.LazyHandle<ColorPropertyHandle>) {
    TODO("Implement onColorProperty")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOpacityProperty(const char node_name[],
   *             const PropertyObserver::LazyHandle<OpacityPropertyHandle>& lh) override {
   *         auto prop_handle = lh();
   *         if (fOverrideProps) {
   *             static constexpr OpacityPropertyValue kOverrideOpacity = 0.75f;
   *             prop_handle->set(kOverrideOpacity);
   *         }
   *         fOpacities.push_back({SkString(node_name), std::move(prop_handle)});
   *     }
   * ```
   */
  public override fun onOpacityProperty(nodeName: CharArray, lh: PropertyObserver.LazyHandle<OpacityPropertyHandle>) {
    TODO("Implement onOpacityProperty")
  }

  /**
   * C++ original:
   * ```cpp
   * void onTextProperty(const char node_name[],
   *                         const PropertyObserver::LazyHandle<TextPropertyHandle>& lh) override {
   *         auto prop_handle = lh();
   *         if (fOverrideProps) {
   *             static const TextPropertyValue kOverrideText = make_text_prop("foo");
   *             prop_handle->set(kOverrideText);
   *         }
   *         fTexts.push_back({SkString(node_name), std::move(prop_handle)});
   *     }
   * ```
   */
  public override fun onTextProperty(nodeName: CharArray, lh: PropertyObserver.LazyHandle<TextPropertyHandle>) {
    TODO("Implement onTextProperty")
  }

  /**
   * C++ original:
   * ```cpp
   * void onTransformProperty(const char node_name[],
   *             const PropertyObserver::LazyHandle<TransformPropertyHandle>& lh) override {
   *         auto prop_handle = lh();
   *         if (fOverrideProps) {
   *             static constexpr TransformPropertyValue kOverrideTransform = {
   *                 { 100, 100 },
   *                 { 200, 200 },
   *                 {   2,   2 },
   *                 45,
   *                 0,
   *                 0,
   *             };
   *             prop_handle->set(kOverrideTransform);
   *         }
   *         fTransforms.push_back({SkString(node_name), std::move(prop_handle)});
   *     }
   * ```
   */
  public override fun onTransformProperty(nodeName: CharArray, lh: PropertyObserver.LazyHandle<TransformPropertyHandle>) {
    TODO("Implement onTransformProperty")
  }

  /**
   * C++ original:
   * ```cpp
   * void onEnterNode(const char node_name[], PropertyObserver::NodeType node_type) override {
   *         if (node_name == nullptr) {
   *             return;
   *         }
   *         fCurrentNode = fCurrentNode.empty() ? node_name : fCurrentNode + "." + node_name;
   *     }
   * ```
   */
  public override fun onEnterNode(nodeName: CharArray, nodeType: PropertyObserver.NodeType) {
    TODO("Implement onEnterNode")
  }

  /**
   * C++ original:
   * ```cpp
   * void onLeavingNode(const char node_name[], PropertyObserver::NodeType node_type) override {
   *         if (node_name == nullptr) {
   *             return;
   *         }
   *         auto length = strlen(node_name);
   *         fCurrentNode =
   *                 fCurrentNode.length() > length
   *                         ? fCurrentNode.substr(0, fCurrentNode.length() - strlen(node_name) - 1)
   *                         : "";
   *     }
   * ```
   */
  public override fun onLeavingNode(nodeName: CharArray, nodeType: PropertyObserver.NodeType) {
    TODO("Implement onLeavingNode")
  }

  /**
   * C++ original:
   * ```cpp
   * const std::vector<ColorInfo>& colors() const { return fColors; }
   * ```
   */
  public fun colors(): Int {
    TODO("Implement colors")
  }

  /**
   * C++ original:
   * ```cpp
   * const std::vector<OpacityInfo>& opacities() const { return fOpacities; }
   * ```
   */
  public fun opacities(): Int {
    TODO("Implement opacities")
  }

  /**
   * C++ original:
   * ```cpp
   * const std::vector<TextInfo>& texts() const { return fTexts; }
   * ```
   */
  public fun texts(): Int {
    TODO("Implement texts")
  }

  /**
   * C++ original:
   * ```cpp
   * const std::vector<TransformInfo>& transforms() const { return fTransforms; }
   * ```
   */
  public fun transforms(): Int {
    TODO("Implement transforms")
  }

  /**
   * C++ original:
   * ```cpp
   * const std::vector<ColorInfo>& colorsWithFullKeypath() const {
   *         return fColorsWithFullKeypath;
   *     }
   * ```
   */
  public fun colorsWithFullKeypath(): Int {
    TODO("Implement colorsWithFullKeypath")
  }

  public data class ColorInfo public constructor(
    public var nodeName: String,
    public var handle: Int,
  )

  public data class OpacityInfo public constructor(
    public var nodeName: String,
    public var handle: Int,
  )

  public data class TextInfo public constructor(
    public var nodeName: String,
    public var handle: Int,
  )

  public data class TransformInfo public constructor(
    public var nodeName: String,
    public var handle: Int,
  )
}
