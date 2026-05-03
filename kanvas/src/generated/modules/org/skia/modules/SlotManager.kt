package org.skia.modules

import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import org.skia.foundation.SkColor
import org.skia.foundation.SkRefCnt
import org.skia.foundation.SkSp
import org.skia.math.SkV2
import undefined.ScalarValue
import undefined.SlotID
import undefined.Vec2Value

/**
 * C++ original:
 * ```cpp
 * class SK_API SlotManager final : public SkRefCnt {
 *
 * public:
 *     using SlotID = SkString;
 *
 *     explicit SlotManager(sk_sp<skottie::internal::SceneGraphRevalidator>);
 *     ~SlotManager() override;
 *
 *     bool setColorSlot(const SlotID&, SkColor);
 *     bool setImageSlot(const SlotID&, const sk_sp<skresources::ImageAsset>&);
 *     bool setScalarSlot(const SlotID&, float);
 *     bool setVec2Slot(const SlotID&, SkV2);
 *     bool setTextSlot(const SlotID&, const TextPropertyValue&);
 *
 *     std::optional<SkColor>               getColorSlot(const SlotID&) const;
 *     sk_sp<const skresources::ImageAsset> getImageSlot(const SlotID&) const;
 *     std::optional<float>                 getScalarSlot(const SlotID&) const;
 *     std::optional<SkV2>                  getVec2Slot(const SlotID&) const;
 *     std::optional<TextPropertyValue>     getTextSlot(const SlotID&) const;
 *
 *     struct SlotInfo {
 *         TArray<SlotID> fColorSlotIDs;
 *         TArray<SlotID> fScalarSlotIDs;
 *         TArray<SlotID> fVec2SlotIDs;
 *         TArray<SlotID> fImageSlotIDs;
 *         TArray<SlotID> fTextSlotIDs;
 *     };
 *
 *     // Helper function to get all slot IDs and their value types
 *     SlotInfo getSlotInfo() const;
 *
 * private:
 *
 *     // pass value to the SlotManager for manipulation and node for invalidation
 *     void trackColorValue(const SlotID&, ColorValue*,
 *                          sk_sp<skottie::internal::AnimatablePropertyContainer>);
 *     sk_sp<skresources::ImageAsset> trackImageValue(const SlotID&, sk_sp<skresources::ImageAsset>);
 *     void trackScalarValue(const SlotID&, ScalarValue*,
 *                           sk_sp<skottie::internal::AnimatablePropertyContainer>);
 *     void trackVec2Value(const SlotID&, Vec2Value*,
 *                         sk_sp<skottie::internal::AnimatablePropertyContainer>);
 *     void trackTextValue(const SlotID&, sk_sp<skottie::internal::TextAdapter>);
 *
 *     // ValuePair tracks a pointer to a value to change, and a means to invalidate the render tree.
 *     // For the latter, we can take either a node in the scene graph that directly the scene graph,
 *     // or an adapter which takes the value passed and interprets it before pushing to the scene
 *     // (clamping, normalizing, etc.)
 *     // Only one should be set, it is UB to create a ValuePair with both a node and an adapter.
 *     template <typename T>
 *     struct ValuePair
 *     {
 *         T value;
 *         sk_sp<skottie::internal::AnimatablePropertyContainer> adapter;
 *     };
 *
 *     class ImageAssetProxy;
 *     template <typename T>
 *     using SlotMap = THashMap<SlotID, TArray<T>>;
 *
 *     SlotMap<ValuePair<ColorValue*>>                fColorMap;
 *     SlotMap<ValuePair<ScalarValue*>>               fScalarMap;
 *     SlotMap<ValuePair<Vec2Value*>>                 fVec2Map;
 *     SlotMap<sk_sp<ImageAssetProxy>>                fImageMap;
 *     SlotMap<sk_sp<skottie::internal::TextAdapter>> fTextMap;
 *
 *     const sk_sp<skottie::internal::SceneGraphRevalidator> fRevalidator;
 *
 *     friend class skottie::internal::AnimationBuilder;
 *     friend class skottie::internal::AnimatablePropertyContainer;
 * }
 * ```
 */
public class SlotManager public constructor(
  revalidator: SkSp<SceneGraphRevalidator>,
) : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * explicit SlotManager(sk_sp<skottie::internal::SceneGraphRevalidator>)
   * ```
   */
  public var skSp: SlotManager = TODO("Initialize skSp")

  /**
   * C++ original:
   * ```cpp
   * SlotMap<ValuePair<ColorValue*>>                fColorMap
   * ```
   */
  private var fColorMap: Int = TODO("Initialize fColorMap")

  /**
   * C++ original:
   * ```cpp
   * SlotMap<ValuePair<ScalarValue*>>               fScalarMap
   * ```
   */
  private var fScalarMap: Int = TODO("Initialize fScalarMap")

  /**
   * C++ original:
   * ```cpp
   * SlotMap<ValuePair<Vec2Value*>>                 fVec2Map
   * ```
   */
  private var fVec2Map: Int = TODO("Initialize fVec2Map")

  /**
   * C++ original:
   * ```cpp
   * const sk_sp<skottie::internal::SceneGraphRevalidator> fRevalidator
   * ```
   */
  private val fRevalidator: Int = TODO("Initialize fRevalidator")

  /**
   * C++ original:
   * ```cpp
   * bool skottie::SlotManager::setColorSlot(const SlotID& slotID, SkColor c) {
   *     auto c4f = SkColor4f::FromColor(c);
   *     ColorValue v{c4f.fR, c4f.fG, c4f.fB, c4f.fA};
   *     const auto valueGroup = fColorMap.find(slotID);
   *     if (valueGroup) {
   *         for (auto& cPair : *valueGroup) {
   *             *(cPair.value) = v;
   *             cPair.adapter->onSync();
   *         }
   *         fRevalidator->revalidate();
   *         return true;
   *     }
   *     return false;
   * }
   * ```
   */
  public fun setColorSlot(slotID: SlotID, c: SkColor): Boolean {
    TODO("Implement setColorSlot")
  }

  /**
   * C++ original:
   * ```cpp
   * bool skottie::SlotManager::setImageSlot(const SlotID& slotID,
   *                                         const sk_sp<skresources::ImageAsset>& i) {
   *     const auto imageGroup = fImageMap.find(slotID);
   *     if (imageGroup) {
   *         for (auto& imageAsset : *imageGroup) {
   *             imageAsset->setImageAsset(i);
   *         }
   *         fRevalidator->revalidate();
   *         return true;
   *     }
   *     return false;
   * }
   * ```
   */
  public fun setImageSlot(slotID: SlotID, i: SkSp<ImageAsset>): Boolean {
    TODO("Implement setImageSlot")
  }

  /**
   * C++ original:
   * ```cpp
   * bool skottie::SlotManager::setScalarSlot(const SlotID& slotID, float s) {
   *     const auto valueGroup = fScalarMap.find(slotID);
   *     if (valueGroup) {
   *         for (auto& sPair : *valueGroup) {
   *             *(sPair.value) = s;
   *             sPair.adapter->onSync();
   *         }
   *         fRevalidator->revalidate();
   *         return true;
   *     }
   *     return false;
   * }
   * ```
   */
  public fun setScalarSlot(slotID: SlotID, s: Float): Boolean {
    TODO("Implement setScalarSlot")
  }

  /**
   * C++ original:
   * ```cpp
   * bool skottie::SlotManager::setVec2Slot(const SlotID& slotID, SkV2 v) {
   *     const auto valueGroup = fVec2Map.find(slotID);
   *     if (valueGroup) {
   *         for (auto& vPair : *valueGroup) {
   *             *(vPair.value) = v;
   *             vPair.adapter->onSync();
   *         }
   *         fRevalidator->revalidate();
   *         return true;
   *     }
   *     return false;
   * }
   * ```
   */
  public fun setVec2Slot(slotID: SlotID, v: SkV2): Boolean {
    TODO("Implement setVec2Slot")
  }

  /**
   * C++ original:
   * ```cpp
   * bool skottie::SlotManager::setTextSlot(const SlotID& slotID, const TextPropertyValue& t) {
   *     const auto adapterGroup = fTextMap.find(slotID);
   *     if (adapterGroup) {
   *         for (auto& textAdapter : *adapterGroup) {
   *             textAdapter->setText(t);
   *         }
   *         fRevalidator->revalidate();
   *         return true;
   *     }
   *     return false;
   * }
   * ```
   */
  public fun setTextSlot(slotID: SlotID, t: TextPropertyValue): Boolean {
    TODO("Implement setTextSlot")
  }

  /**
   * C++ original:
   * ```cpp
   * std::optional<SkColor> skottie::SlotManager::getColorSlot(const SlotID& slotID) const {
   *     const auto valueGroup = fColorMap.find(slotID);
   *     return valueGroup && !valueGroup->empty() ? std::optional<SkColor>(*(valueGroup->at(0).value))
   *                                               : std::nullopt;
   * }
   * ```
   */
  public fun getColorSlot(slotID: SlotID): Int {
    TODO("Implement getColorSlot")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<const skresources::ImageAsset> skottie::SlotManager::getImageSlot(
   *         const SlotID& slotID) const {
   *     const auto imageGroup = fImageMap.find(slotID);
   *     return imageGroup && !imageGroup->empty() ? imageGroup->at(0)->getImageAsset() : nullptr;
   * }
   * ```
   */
  public fun getImageSlot(slotID: SlotID): Int {
    TODO("Implement getImageSlot")
  }

  /**
   * C++ original:
   * ```cpp
   * std::optional<float> skottie::SlotManager::getScalarSlot(const SlotID& slotID) const {
   *     const auto valueGroup = fScalarMap.find(slotID);
   *     return valueGroup && !valueGroup->empty() ? std::optional<float>(*(valueGroup->at(0).value))
   *                                               : std::nullopt;
   * }
   * ```
   */
  public fun getScalarSlot(slotID: SlotID): Float? {
    TODO("Implement getScalarSlot")
  }

  /**
   * C++ original:
   * ```cpp
   * std::optional<SkV2> skottie::SlotManager::getVec2Slot(const SlotID& slotID) const {
   *     const auto valueGroup = fVec2Map.find(slotID);
   *     return valueGroup && !valueGroup->empty() ? std::optional<SkV2>(*(valueGroup->at(0).value))
   *                                               : std::nullopt;
   * }
   * ```
   */
  public fun getVec2Slot(slotID: SlotID): SkV2? {
    TODO("Implement getVec2Slot")
  }

  /**
   * C++ original:
   * ```cpp
   * std::optional<skottie::TextPropertyValue> skottie::SlotManager::getTextSlot(
   *         const SlotID& slotID) const {
   *     const auto adapterGroup = fTextMap.find(slotID);
   *     return adapterGroup && !adapterGroup->empty() ?
   *            std::optional<TextPropertyValue>(adapterGroup->at(0)->getText()) :
   *            std::nullopt;
   * }
   * ```
   */
  public fun getTextSlot(slotID: SlotID): TextPropertyValue? {
    TODO("Implement getTextSlot")
  }

  /**
   * C++ original:
   * ```cpp
   * skottie::SlotManager::SlotInfo skottie::SlotManager::getSlotInfo() const {
   *     SlotInfo sInfo;
   *     for (const auto& c : fColorMap) {
   *         sInfo.fColorSlotIDs.push_back(c.first);
   *     }
   *     for (const auto& s : fScalarMap) {
   *         sInfo.fScalarSlotIDs.push_back(s.first);
   *     }
   *     for (const auto& v : fVec2Map) {
   *         sInfo.fVec2SlotIDs.push_back(v.first);
   *     }
   *     for (const auto& i : fImageMap) {
   *         sInfo.fImageSlotIDs.push_back(i.first);
   *     }
   *     for (const auto& t : fTextMap) {
   *         sInfo.fTextSlotIDs.push_back(t.first);
   *     }
   *     return sInfo;
   * }
   * ```
   */
  public fun getSlotInfo(): SlotInfo {
    TODO("Implement getSlotInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * void skottie::SlotManager::trackColorValue(const SlotID& slotID, ColorValue* colorValue,
   *                                            sk_sp<internal::AnimatablePropertyContainer> adapter) {
   *     fColorMap[slotID].push_back({colorValue, std::move(adapter)});
   * }
   * ```
   */
  private fun trackColorValue(
    slotID: SlotID,
    colorValue: ColorValue?,
    adapter: SkSp<AnimatablePropertyContainer>,
  ) {
    TODO("Implement trackColorValue")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<skresources::ImageAsset> skottie::SlotManager::trackImageValue(const SlotID& slotID,
   *                                                                      sk_sp<skresources::ImageAsset>
   *                                                                         imageAsset) {
   *     auto proxy = sk_make_sp<ImageAssetProxy>(std::move(imageAsset));
   *     fImageMap[slotID].push_back(proxy);
   *     return proxy;
   * }
   * ```
   */
  private fun trackImageValue(slotID: SlotID, imageAsset: SkSp<ImageAsset>): Int {
    TODO("Implement trackImageValue")
  }

  /**
   * C++ original:
   * ```cpp
   * void skottie::SlotManager::trackScalarValue(const SlotID& slotID, ScalarValue* scalarValue,
   *                                             sk_sp<internal::AnimatablePropertyContainer> adapter) {
   *     fScalarMap[slotID].push_back({scalarValue, std::move(adapter)});
   * }
   * ```
   */
  private fun trackScalarValue(
    slotID: SlotID,
    scalarValue: ScalarValue?,
    adapter: SkSp<AnimatablePropertyContainer>,
  ) {
    TODO("Implement trackScalarValue")
  }

  /**
   * C++ original:
   * ```cpp
   * void skottie::SlotManager::trackVec2Value(const SlotID& slotID, Vec2Value* vec2Value,
   *                                           sk_sp<internal::AnimatablePropertyContainer> adapter) {
   *     fVec2Map[slotID].push_back({vec2Value, std::move(adapter)});
   * }
   * ```
   */
  private fun trackVec2Value(
    slotID: SlotID,
    vec2Value: Vec2Value?,
    adapter: SkSp<AnimatablePropertyContainer>,
  ) {
    TODO("Implement trackVec2Value")
  }

  /**
   * C++ original:
   * ```cpp
   * void skottie::SlotManager::trackTextValue(const SlotID& slotID,
   *                                           sk_sp<internal::TextAdapter> adapter) {
   *     fTextMap[slotID].push_back(std::move(adapter));
   * }
   * ```
   */
  private fun trackTextValue(slotID: SlotID, adapter: SkSp<TextAdapter>) {
    TODO("Implement trackTextValue")
  }

  public data class SlotInfo public constructor(
    public var fColorSlotIDs: Int,
    public var fScalarSlotIDs: Int,
    public var fVec2SlotIDs: Int,
    public var fImageSlotIDs: Int,
    public var fTextSlotIDs: Int,
  )

  public data class ValuePair<T> public constructor(
    private var `value`: T,
    private var adapter: Int,
  )
}
