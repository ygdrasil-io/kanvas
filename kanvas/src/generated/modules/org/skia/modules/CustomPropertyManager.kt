package org.skia.modules

import PropMap
import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import kotlin.String
import kotlin.ULong
import skottie.ColorPropertyValue
import undefined.PropKey

/**
 * C++ original:
 * ```cpp
 * class CustomPropertyManager final {
 * public:
 *     enum class Mode {
 *         kCollapseProperties,   // keys ignore the ancestor chain and are
 *                                // grouped based on the local node name
 *         kNamespacedProperties, // keys include the ancestor node names (no grouping)
 *     };
 *
 *     explicit CustomPropertyManager(Mode = Mode::kNamespacedProperties,
 *                                    const char* prefix = nullptr);
 *     ~CustomPropertyManager();
 *
 *     using PropKey = std::string;
 *
 *     std::vector<PropKey> getColorProps() const;
 *     skottie::ColorPropertyValue getColor(const PropKey&) const;
 *     std::unique_ptr<skottie::ColorPropertyHandle> getColorHandle(const PropKey&, size_t) const;
 *     bool setColor(const PropKey&, const skottie::ColorPropertyValue&);
 *
 *     std::vector<PropKey> getOpacityProps() const;
 *     skottie::OpacityPropertyValue getOpacity(const PropKey&) const;
 *     std::unique_ptr<skottie::OpacityPropertyHandle> getOpacityHandle(const PropKey&, size_t) const;
 *     bool setOpacity(const PropKey&, const skottie::OpacityPropertyValue&);
 *
 *     std::vector<PropKey> getTransformProps() const;
 *     skottie::TransformPropertyValue getTransform(const PropKey&) const;
 *     std::unique_ptr<skottie::TransformPropertyHandle> getTransformHandle(const PropKey&,
 *                                                                          size_t) const;
 *     bool setTransform(const PropKey&, const skottie::TransformPropertyValue&);
 *
 *     std::vector<PropKey> getTextProps() const;
 *     skottie::TextPropertyValue getText(const PropKey&) const;
 *     std::unique_ptr<skottie::TextPropertyHandle> getTextHandle(const PropKey&, size_t index) const;
 *     bool setText(const PropKey&, const skottie::TextPropertyValue&);
 *
 *     struct MarkerInfo {
 *         std::string name;
 *         float       t0, t1;
 *     };
 *     const std::vector<MarkerInfo>& markers() const { return fMarkers; }
 *
 *     // Returns a property observer to be attached to an animation builder.
 *     sk_sp<skottie::PropertyObserver> getPropertyObserver() const;
 *
 *     // Returns a marker observer to be attached to an animation builder.
 *     sk_sp<skottie::MarkerObserver> getMarkerObserver() const;
 *
 * private:
 *     class PropertyInterceptor;
 *     class MarkerInterceptor;
 *
 *     std::string acceptKey(const char*, const char*) const;
 *
 *     template <typename T>
 *     using PropGroup = std::vector<std::unique_ptr<T>>;
 *
 *     template <typename T>
 *     using PropMap = std::unordered_map<PropKey, PropGroup<T>>;
 *
 *     template <typename T>
 *     std::vector<PropKey> getProps(const PropMap<T>& container) const;
 *
 *     template <typename V, typename T>
 *     V get(const PropKey&, const PropMap<T>& container) const;
 *
 *     template <typename T>
 *     std::unique_ptr<T> getHandle(const PropKey&, size_t, const PropMap<T>& container) const;
 *
 *     template <typename V, typename T>
 *     bool set(const PropKey&, const V&, const PropMap<T>& container);
 *
 *     const Mode                                fMode;
 *     const SkString                            fPrefix;
 *
 *     sk_sp<PropertyInterceptor>                fPropertyInterceptor;
 *     sk_sp<MarkerInterceptor>                  fMarkerInterceptor;
 *
 *     PropMap<skottie::ColorPropertyHandle>     fColorMap;
 *     PropMap<skottie::OpacityPropertyHandle>   fOpacityMap;
 *     PropMap<skottie::TransformPropertyHandle> fTransformMap;
 *     PropMap<skottie::TextPropertyHandle>      fTextMap;
 *     std::vector<MarkerInfo>                   fMarkers;
 *     std::string                               fCurrentNode;
 * }
 * ```
 */
public data class CustomPropertyManager public constructor(
  /**
   * C++ original:
   * ```cpp
   * const Mode                                fMode
   * ```
   */
  private val fMode: Mode,
  /**
   * C++ original:
   * ```cpp
   * const SkString                            fPrefix
   * ```
   */
  private val fPrefix: Int,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<PropertyInterceptor>                fPropertyInterceptor
   * ```
   */
  private var fPropertyInterceptor: Int,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<MarkerInterceptor>                  fMarkerInterceptor
   * ```
   */
  private var fMarkerInterceptor: Int,
  /**
   * C++ original:
   * ```cpp
   * PropMap<skottie::ColorPropertyHandle>     fColorMap
   * ```
   */
  private var fColorMap: Int,
  /**
   * C++ original:
   * ```cpp
   * PropMap<skottie::OpacityPropertyHandle>   fOpacityMap
   * ```
   */
  private var fOpacityMap: Int,
  /**
   * C++ original:
   * ```cpp
   * PropMap<skottie::TransformPropertyHandle> fTransformMap
   * ```
   */
  private var fTransformMap: Int,
  /**
   * C++ original:
   * ```cpp
   * PropMap<skottie::TextPropertyHandle>      fTextMap
   * ```
   */
  private var fTextMap: Int,
  /**
   * C++ original:
   * ```cpp
   * std::vector<MarkerInfo>                   fMarkers
   * ```
   */
  private var fMarkers: Int,
  /**
   * C++ original:
   * ```cpp
   * std::string                               fCurrentNode
   * ```
   */
  private var fCurrentNode: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * std::vector<CustomPropertyManager::PropKey>
   * CustomPropertyManager::getColorProps() const {
   *     return this->getProps(fColorMap);
   * }
   * ```
   */
  public fun getColorProps(): Int {
    TODO("Implement getColorProps")
  }

  /**
   * C++ original:
   * ```cpp
   * skottie::ColorPropertyValue CustomPropertyManager::getColor(const PropKey& key) const {
   *     return this->get<skottie::ColorPropertyValue>(key, fColorMap);
   * }
   * ```
   */
  public fun getColor(key: PropKey): Int {
    TODO("Implement getColor")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<skottie::ColorPropertyHandle>
   * CustomPropertyManager::getColorHandle(const PropKey& key, size_t index) const {
   *     return this->getHandle(key, index, fColorMap);
   * }
   * ```
   */
  public fun getColorHandle(key: PropKey, index: ULong): Int {
    TODO("Implement getColorHandle")
  }

  /**
   * C++ original:
   * ```cpp
   * bool CustomPropertyManager::setColor(const PropKey& key, const skottie::ColorPropertyValue& c) {
   *     return this->set(key, c, fColorMap);
   * }
   * ```
   */
  public fun setColor(key: PropKey, c: ColorPropertyValue): Boolean {
    TODO("Implement setColor")
  }

  /**
   * C++ original:
   * ```cpp
   * std::vector<CustomPropertyManager::PropKey>
   * CustomPropertyManager::getOpacityProps() const {
   *     return this->getProps(fOpacityMap);
   * }
   * ```
   */
  public fun getOpacityProps(): Int {
    TODO("Implement getOpacityProps")
  }

  /**
   * C++ original:
   * ```cpp
   * skottie::OpacityPropertyValue CustomPropertyManager::getOpacity(const PropKey& key) const {
   *     return this->get<skottie::OpacityPropertyValue>(key, fOpacityMap);
   * }
   * ```
   */
  public fun getOpacity(key: PropKey): Int {
    TODO("Implement getOpacity")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<skottie::OpacityPropertyHandle>
   * CustomPropertyManager::getOpacityHandle(const PropKey& key, size_t index) const {
   *     return this->getHandle(key, index, fOpacityMap);
   * }
   * ```
   */
  public fun getOpacityHandle(key: PropKey, index: ULong): Int {
    TODO("Implement getOpacityHandle")
  }

  /**
   * C++ original:
   * ```cpp
   * bool CustomPropertyManager::setOpacity(const PropKey& key, const skottie::OpacityPropertyValue& o) {
   *     return this->set(key, o, fOpacityMap);
   * }
   * ```
   */
  public fun setOpacity(key: PropKey, o: OpacityPropertyValue): Boolean {
    TODO("Implement setOpacity")
  }

  /**
   * C++ original:
   * ```cpp
   * std::vector<CustomPropertyManager::PropKey>
   * CustomPropertyManager::getTransformProps() const {
   *     return this->getProps(fTransformMap);
   * }
   * ```
   */
  public fun getTransformProps(): Int {
    TODO("Implement getTransformProps")
  }

  /**
   * C++ original:
   * ```cpp
   * skottie::TransformPropertyValue CustomPropertyManager::getTransform(const PropKey& key) const {
   *     return this->get<skottie::TransformPropertyValue>(key, fTransformMap);
   * }
   * ```
   */
  public fun getTransform(key: PropKey): Int {
    TODO("Implement getTransform")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<skottie::TransformPropertyHandle>
   * CustomPropertyManager::getTransformHandle(const PropKey& key, size_t index) const {
   *     return this->getHandle(key, index, fTransformMap);
   * }
   * ```
   */
  public fun getTransformHandle(key: PropKey, index: ULong): Int {
    TODO("Implement getTransformHandle")
  }

  /**
   * C++ original:
   * ```cpp
   * bool CustomPropertyManager::setTransform(const PropKey& key,
   *                                          const skottie::TransformPropertyValue& t) {
   *     return this->set(key, t, fTransformMap);
   * }
   * ```
   */
  public fun setTransform(key: PropKey, t: TransformPropertyValue): Boolean {
    TODO("Implement setTransform")
  }

  /**
   * C++ original:
   * ```cpp
   * std::vector<CustomPropertyManager::PropKey>
   * CustomPropertyManager::getTextProps() const {
   *     return this->getProps(fTextMap);
   * }
   * ```
   */
  public fun getTextProps(): Int {
    TODO("Implement getTextProps")
  }

  /**
   * C++ original:
   * ```cpp
   * skottie::TextPropertyValue CustomPropertyManager::getText(const PropKey& key) const {
   *     return this->get<skottie::TextPropertyValue>(key, fTextMap);
   * }
   * ```
   */
  public fun getText(key: PropKey): Int {
    TODO("Implement getText")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<skottie::TextPropertyHandle>
   * CustomPropertyManager::getTextHandle(const PropKey& key, size_t index) const {
   *     return this->getHandle(key, index, fTextMap);
   * }
   * ```
   */
  public fun getTextHandle(key: PropKey, index: ULong): Int {
    TODO("Implement getTextHandle")
  }

  /**
   * C++ original:
   * ```cpp
   * bool CustomPropertyManager::setText(const PropKey& key, const skottie::TextPropertyValue& o) {
   *     return this->set(key, o, fTextMap);
   * }
   * ```
   */
  public fun setText(key: PropKey, o: TextPropertyValue): Boolean {
    TODO("Implement setText")
  }

  /**
   * C++ original:
   * ```cpp
   * const std::vector<MarkerInfo>& markers() const { return fMarkers; }
   * ```
   */
  public fun markers(): Int {
    TODO("Implement markers")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<skottie::PropertyObserver> CustomPropertyManager::getPropertyObserver() const {
   *     return fPropertyInterceptor;
   * }
   * ```
   */
  public fun getPropertyObserver(): Int {
    TODO("Implement getPropertyObserver")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<skottie::MarkerObserver> CustomPropertyManager::getMarkerObserver() const {
   *     return fMarkerInterceptor;
   * }
   * ```
   */
  public fun getMarkerObserver(): Int {
    TODO("Implement getMarkerObserver")
  }

  /**
   * C++ original:
   * ```cpp
   * std::string CustomPropertyManager::acceptKey(const char* name, const char* suffix) const {
   *     if (!SkStrStartsWith(name, fPrefix.c_str())) {
   *         return std::string();
   *     }
   *
   *     return fMode == Mode::kCollapseProperties
   *             ? std::string(name)
   *             : fCurrentNode + suffix;
   * }
   * ```
   */
  private fun acceptKey(name: String?, suffix: String?): Int {
    TODO("Implement acceptKey")
  }

  /**
   * C++ original:
   * ```cpp
   * template <typename T>
   * std::vector<CustomPropertyManager::PropKey>
   * CustomPropertyManager::getProps(const PropMap<T>& container) const {
   *     std::vector<PropKey> props;
   *
   *     for (const auto& prop_list : container) {
   *         SkASSERT(!prop_list.second.empty());
   *         props.push_back(prop_list.first);
   *     }
   *
   *     return props;
   * }
   * ```
   */
  private fun <T> getProps(container: PropMap<T>): Int {
    TODO("Implement getProps")
  }

  /**
   * C++ original:
   * ```cpp
   * template <typename V, typename T>
   * V CustomPropertyManager::get(const PropKey& key, const PropMap<T>& container) const {
   *     auto prop_group = container.find(key);
   *
   *     return prop_group == container.end()
   *             ? V()
   *             : prop_group->second.front()->get();
   * }
   * ```
   */
  private fun <V, T> `get`(key: PropKey, container: PropMap<T>): V {
    TODO("Implement get")
  }

  /**
   * C++ original:
   * ```cpp
   * template <typename T>
   * std::unique_ptr<T> CustomPropertyManager::getHandle(const PropKey& key,
   *                                                     size_t index,
   *                                                     const PropMap<T>& container) const {
   *     auto prop_group = container.find(key);
   *
   *     if (prop_group == container.end() || index >= prop_group->second.size()) {
   *         return nullptr;
   *     }
   *
   *     return std::make_unique<T>(*prop_group->second[index]);
   * }
   * ```
   */
  private fun <T> getHandle(
    key: PropKey,
    index: ULong,
    container: PropMap<T>,
  ): Int {
    TODO("Implement getHandle")
  }

  /**
   * C++ original:
   * ```cpp
   * template <typename V, typename T>
   * bool CustomPropertyManager::set(const PropKey& key, const V& val, const PropMap<T>& container) {
   *     auto prop_group = container.find(key);
   *
   *     if (prop_group == container.end()) {
   *         return false;
   *     }
   *
   *     for (auto& handle : prop_group->second) {
   *         handle->set(val);
   *     }
   *
   *     return true;
   * }
   * ```
   */
  private fun <V, T> `set`(
    key: PropKey,
    `val`: V,
    container: PropMap<T>,
  ): Boolean {
    TODO("Implement set")
  }

  public data class MarkerInfo public constructor(
    public var name: Int,
    public var t0: Float,
    public var t1: Float,
  )

  public enum class Mode {
    kCollapseProperties,
    kNamespacedProperties,
  }
}
