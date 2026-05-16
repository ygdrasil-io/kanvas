package org.skia.modules

import SkShapers.Factory
import kotlin.Any
import kotlin.Boolean
import kotlin.CharArray
import kotlin.Float
import kotlin.Int
import kotlin.String
import kotlin.UInt
import org.skia.foundation.SkFontMgr
import org.skia.foundation.SkNoncopyable
import org.skia.foundation.SkSp
import org.skia.math.SkSize
import org.skia.tests.Builder
import undefined.Args
import undefined.AttachShapeContext

/**
 * C++ original:
 * ```cpp
 * class AnimationBuilder final : public SkNoncopyable {
 * public:
 *     AnimationBuilder(sk_sp<ResourceProvider>, sk_sp<SkFontMgr>, sk_sp<PropertyObserver>,
 *                      sk_sp<Logger>, sk_sp<MarkerObserver>, sk_sp<PrecompInterceptor>,
 *                      sk_sp<ExpressionManager>, sk_sp<SkShapers::Factory>,
 *                      Animation::Builder::Stats*, const SkSize& comp_size,
 *                      float duration, float framerate, uint32_t flags);
 *
 *     struct AnimationInfo {
 *         sk_sp<sksg::RenderNode> fSceneRoot;
 *         AnimatorScope           fAnimators;
 *         sk_sp<SlotManager>      fSlotManager;
 *         std::vector<LayerInfo>  fLayerInfo;
 *     };
 *
 *     AnimationInfo parse(const skjson::ObjectValue&);
 *
 *     struct FontInfo {
 *         SkString            fFamily,
 *                             fStyle,
 *                             fPath;
 *         SkScalar            fAscentPct;
 *         sk_sp<SkTypeface>   fTypeface;
 *         CustomFont::Builder fCustomFontBuilder;
 *
 *         bool matches(const char family[], const char style[]) const;
 *     };
 *     const FontInfo* findFont(const SkString& name) const;
 *
 *     void log(Logger::Level, const skjson::Value*, const char fmt[], ...) const SK_PRINTF_LIKE(4, 5);
 *
 *     sk_sp<sksg::Transform> attachMatrix2D(const skjson::ObjectValue&, sk_sp<sksg::Transform>,
 *                                           bool auto_orient = false) const;
 *     sk_sp<sksg::Transform> attachMatrix3D(const skjson::ObjectValue&, sk_sp<sksg::Transform>,
 *                                           bool auto_orient = false) const;
 *
 *     sk_sp<sksg::Transform> attachCamera(const skjson::ObjectValue& jlayer,
 *                                         const skjson::ObjectValue& jtransform,
 *                                         sk_sp<sksg::Transform>,
 *                                         const SkSize&) const;
 *
 *     sk_sp<sksg::RenderNode> attachOpacity(const skjson::ObjectValue&,
 *                                           sk_sp<sksg::RenderNode>) const;
 *     sk_sp<sksg::Path> attachPath(const skjson::Value&) const;
 *
 *     bool hasNontrivialBlending() const { return fHasNontrivialBlending; }
 *
 *     class AutoScope final {
 *     public:
 *         explicit AutoScope(const AnimationBuilder* builder) : AutoScope(builder, AnimatorScope()) {}
 *
 *         AutoScope(const AnimationBuilder* builder, AnimatorScope&& scope)
 *             : fBuilder(builder)
 *             , fCurrentScope(std::move(scope))
 *             , fPrevScope(fBuilder->fCurrentAnimatorScope) {
 *             fBuilder->fCurrentAnimatorScope = &fCurrentScope;
 *         }
 *
 *         AnimatorScope release() {
 *             fBuilder->fCurrentAnimatorScope = fPrevScope;
 *             SkDEBUGCODE(fBuilder = nullptr);
 *
 *             return std::move(fCurrentScope);
 *         }
 *
 *         ~AutoScope() { SkASSERT(!fBuilder); }
 *
 *     private:
 *         const AnimationBuilder* fBuilder;
 *         AnimatorScope           fCurrentScope;
 *         AnimatorScope*          fPrevScope;
 *     };
 *
 *     template <typename T>
 *     void attachDiscardableAdapter(sk_sp<T> adapter) const {
 *         if (adapter->isStatic()) {
 *             // Fire off a synthetic tick to force a single SG sync before discarding.
 *             adapter->seek(0);
 *         } else {
 *             fCurrentAnimatorScope->push_back(std::move(adapter));
 *         }
 *     }
 *
 *     template <typename T, typename... Args>
 *     auto attachDiscardableAdapter(Args&&... args) const ->
 *         typename std::decay<decltype(T::Make(std::forward<Args>(args)...)->node())>::type
 *     {
 *         using NodeType =
 *         typename std::decay<decltype(T::Make(std::forward<Args>(args)...)->node())>::type;
 *
 *         NodeType node;
 *         if (auto adapter = T::Make(std::forward<Args>(args)...)) {
 *             node = adapter->node();
 *             this->attachDiscardableAdapter(std::move(adapter));
 *         }
 *         return node;
 *     }
 *
 *     class AutoPropertyTracker {
 *     public:
 *         AutoPropertyTracker(const AnimationBuilder* builder, const skjson::ObjectValue& obj, const PropertyObserver::NodeType node_type)
 *             : fBuilder(builder)
 *             , fPrevContext(builder->fPropertyObserverContext), fNodeType(node_type) {
 *             if (fBuilder->fPropertyObserver) {
 *                 auto observer = builder->fPropertyObserver.get();
 *                 this->updateContext(observer, obj);
 *                 observer->onEnterNode(fBuilder->fPropertyObserverContext, fNodeType);
 *             }
 *         }
 *
 *         ~AutoPropertyTracker() {
 *             if (fBuilder->fPropertyObserver) {
 *                 fBuilder->fPropertyObserver->onLeavingNode(fBuilder->fPropertyObserverContext, fNodeType);
 *                 fBuilder->fPropertyObserverContext = fPrevContext;
 *             }
 *         }
 *     private:
 *         void updateContext(PropertyObserver*, const skjson::ObjectValue&);
 *
 *         const AnimationBuilder* fBuilder;
 *         const char*             fPrevContext;
 *         const PropertyObserver::NodeType fNodeType;
 *     };
 *
 *     bool dispatchColorProperty(const sk_sp<sksg::Color>&) const;
 *     bool dispatchOpacityProperty(const sk_sp<sksg::OpacityEffect>&) const;
 *     bool dispatchTextProperty(const sk_sp<TextAdapter>&,
 *                               const skjson::ObjectValue* jtext) const;
 *     bool dispatchTransformProperty(const sk_sp<TransformAdapter2D>&) const;
 *
 *     sk_sp<ExpressionManager> expression_manager() const;
 *
 *     const skjson::ObjectValue* getSlotsRoot() const {
 *         return fSlotsRoot;
 *     }
 *
 *     void parseFonts (const skjson::ObjectValue* jfonts, const skjson::ArrayValue* jchars);
 *
 * private:
 *     friend class CompositionBuilder;
 *     friend class CustomFont;
 *     friend class LayerBuilder;
 *     friend class AnimatablePropertyContainer;
 *     friend class SkSLEffectBase;
 *
 *     struct AttachLayerContext;
 *     struct AttachShapeContext;
 *     struct FootageAssetInfo;
 *
 *     void parseAssets(const skjson::ArrayValue*);
 *
 *     // Return true iff all fonts were resolved.
 *     bool resolveNativeTypefaces();
 *     bool resolveEmbeddedTypefaces(const skjson::ArrayValue& jchars);
 *
 *     void dispatchMarkers(const skjson::ArrayValue*) const;
 *
 *     sk_sp<sksg::RenderNode> attachBlendMode(const skjson::ObjectValue&,
 *                                             sk_sp<sksg::RenderNode>) const;
 *
 *     sk_sp<sksg::RenderNode> attachShape(const skjson::ArrayValue*, AttachShapeContext*,
 *                                         bool suppress_draws = false) const;
 *     const FootageAssetInfo* loadFootageAsset(const skjson::ObjectValue&) const;
 *     sk_sp<sksg::RenderNode> attachFootageAsset(const skjson::ObjectValue&, LayerInfo*) const;
 *
 *     sk_sp<sksg::RenderNode> attachExternalPrecompLayer(const skjson::ObjectValue&,
 *                                                        const LayerInfo&) const;
 *
 *     sk_sp<sksg::RenderNode> attachFootageLayer(const skjson::ObjectValue&, LayerInfo*) const;
 *     sk_sp<sksg::RenderNode> attachNullLayer   (const skjson::ObjectValue&, LayerInfo*) const;
 *     sk_sp<sksg::RenderNode> attachPrecompLayer(const skjson::ObjectValue&, LayerInfo*) const;
 *     sk_sp<sksg::RenderNode> attachShapeLayer  (const skjson::ObjectValue&, LayerInfo*) const;
 *     sk_sp<sksg::RenderNode> attachSolidLayer  (const skjson::ObjectValue&, LayerInfo*) const;
 *     sk_sp<sksg::RenderNode> attachTextLayer   (const skjson::ObjectValue&, LayerInfo*) const;
 *     sk_sp<sksg::RenderNode> attachAudioLayer  (const skjson::ObjectValue&, LayerInfo*) const;
 *
 *     void trackLayerInfo(const LayerInfo& info) const {fLayerInfo.emplace_back(info);}
 *
 *     sk_sp<ResourceProvider>        fResourceProvider;
 *     sk_sp<SkFontMgr>               fFontMgr;
 *     sk_sp<PropertyObserver>        fPropertyObserver;
 *     sk_sp<Logger>                  fLogger;
 *     sk_sp<MarkerObserver>          fMarkerObserver;
 *     sk_sp<PrecompInterceptor>      fPrecompInterceptor;
 *     sk_sp<ExpressionManager>       fExpressionManager;
 *     sk_sp<SkShapers::Factory>      fShapingFactory;
 *     sk_sp<SceneGraphRevalidator>   fRevalidator;
 *     sk_sp<SlotManager>             fSlotManager;
 *     Animation::Builder::Stats*     fStats;
 *     const SkSize                   fCompSize;
 *     const float                    fDuration,
 *                                    fFrameRate;
 *     const uint32_t                 fFlags;
 *     mutable std::vector<LayerInfo> fLayerInfo;
 *     mutable AnimatorScope*         fCurrentAnimatorScope;
 *     mutable const char*            fPropertyObserverContext = nullptr;
 *     mutable bool                   fHasNontrivialBlending : 1;
 *     struct AssetInfo {
 *         const skjson::ObjectValue* fAsset;
 *         mutable bool               fIsAttaching; // Used for cycle detection
 *     };
 *
 *     struct FootageAssetInfo {
 *         sk_sp<ImageAsset> fAsset;
 *         SkISize           fSize;
 *     };
 *
 *     class ScopedAssetRef {
 *     public:
 *         ScopedAssetRef(const AnimationBuilder* abuilder, const skjson::ObjectValue& jlayer);
 *
 *         ~ScopedAssetRef() {
 *             if (fInfo) {
 *                 fInfo->fIsAttaching = false;
 *             }
 *         }
 *
 *         explicit operator bool() const { return !!fInfo; }
 *
 *         const skjson::ObjectValue& operator*() const { return *fInfo->fAsset; }
 *
 *     private:
 *         const AssetInfo* fInfo = nullptr;
 *     };
 *
 *     skia_private::THashMap<SkString, AssetInfo>                fAssets;
 *     skia_private::THashMap<SkString, FontInfo>                 fFonts;
 *     sk_sp<CustomFont::GlyphCompMapper>                         fCustomGlyphMapper;
 *     mutable skia_private::THashMap<SkString, FootageAssetInfo> fImageAssetCache;
 *
 *     // Handle to "slots" JSON Object, used to grab slot values while building
 *     const skjson::ObjectValue* fSlotsRoot;
 *
 *     using INHERITED = SkNoncopyable;
 * }
 * ```
 */
public class AnimationBuilder public constructor(
  rp: SkSp<ResourceProvider>,
  fontmgr: SkSp<SkFontMgr>,
  pobserver: SkSp<PropertyObserver>,
  logger: SkSp<Logger>,
  mobserver: SkSp<MarkerObserver>,
  pi: SkSp<PrecompInterceptor>,
  expressionmgr: SkSp<ExpressionManager>,
  shapingFactory: SkSp<Factory>,
  stats: Builder.Stats?,
  compSize: SkSize,
  duration: Float,
  framerate: Float,
  flags: UInt,
) : SkNoncopyable() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<ResourceProvider>        fResourceProvider
   * ```
   */
  private var fResourceProvider: Int = TODO("Initialize fResourceProvider")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFontMgr>               fFontMgr
   * ```
   */
  private var fFontMgr: Int = TODO("Initialize fFontMgr")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<PropertyObserver>        fPropertyObserver
   * ```
   */
  private var fPropertyObserver: Int = TODO("Initialize fPropertyObserver")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<Logger>                  fLogger
   * ```
   */
  private var fLogger: Int = TODO("Initialize fLogger")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<MarkerObserver>          fMarkerObserver
   * ```
   */
  private var fMarkerObserver: Int = TODO("Initialize fMarkerObserver")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<PrecompInterceptor>      fPrecompInterceptor
   * ```
   */
  private var fPrecompInterceptor: Int = TODO("Initialize fPrecompInterceptor")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<ExpressionManager>       fExpressionManager
   * ```
   */
  private var fExpressionManager: Int = TODO("Initialize fExpressionManager")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShapers::Factory>      fShapingFactory
   * ```
   */
  private var fShapingFactory: Int = TODO("Initialize fShapingFactory")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SceneGraphRevalidator>   fRevalidator
   * ```
   */
  private var fRevalidator: Int = TODO("Initialize fRevalidator")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SlotManager>             fSlotManager
   * ```
   */
  private var fSlotManager: Int = TODO("Initialize fSlotManager")

  /**
   * C++ original:
   * ```cpp
   * Animation::Builder::Stats*     fStats
   * ```
   */
  private var fStats: Int? = TODO("Initialize fStats")

  /**
   * C++ original:
   * ```cpp
   * const SkSize                   fCompSize
   * ```
   */
  private val fCompSize: Int = TODO("Initialize fCompSize")

  /**
   * C++ original:
   * ```cpp
   * const float                    fDuration
   * ```
   */
  private val fDuration: Float = TODO("Initialize fDuration")

  /**
   * C++ original:
   * ```cpp
   * const float                    fDuration,
   *                                    fFrameRate
   * ```
   */
  private val fFrameRate: Float = TODO("Initialize fFrameRate")

  /**
   * C++ original:
   * ```cpp
   * const uint32_t                 fFlags
   * ```
   */
  private val fFlags: Int = TODO("Initialize fFlags")

  /**
   * C++ original:
   * ```cpp
   * mutable std::vector<LayerInfo> fLayerInfo
   * ```
   */
  private var fLayerInfo: Int = TODO("Initialize fLayerInfo")

  /**
   * C++ original:
   * ```cpp
   * mutable AnimatorScope*         fCurrentAnimatorScope
   * ```
   */
  private var fCurrentAnimatorScope: Int? = TODO("Initialize fCurrentAnimatorScope")

  /**
   * C++ original:
   * ```cpp
   * mutable const char*            fPropertyObserverContext = nullptr
   * ```
   */
  private val fPropertyObserverContext: String? = TODO("Initialize fPropertyObserverContext")

  /**
   * C++ original:
   * ```cpp
   * mutable bool                   fHasNontrivialBlending : 1
   * ```
   */
  private var fHasNontrivialBlending: Boolean = TODO("Initialize fHasNontrivialBlending")

  /**
   * C++ original:
   * ```cpp
   * skia_private::THashMap<SkString, AssetInfo>                fAssets
   * ```
   */
  private var fAssets: Int = TODO("Initialize fAssets")

  /**
   * C++ original:
   * ```cpp
   * skia_private::THashMap<SkString, FontInfo>                 fFonts
   * ```
   */
  private var fFonts: Int = TODO("Initialize fFonts")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<CustomFont::GlyphCompMapper>                         fCustomGlyphMapper
   * ```
   */
  private var fCustomGlyphMapper: Int = TODO("Initialize fCustomGlyphMapper")

  /**
   * C++ original:
   * ```cpp
   * mutable skia_private::THashMap<SkString, FootageAssetInfo> fImageAssetCache
   * ```
   */
  private var fImageAssetCache: Int = TODO("Initialize fImageAssetCache")

  /**
   * C++ original:
   * ```cpp
   * const skjson::ObjectValue* fSlotsRoot
   * ```
   */
  private val fSlotsRoot: ObjectValue? = TODO("Initialize fSlotsRoot")

  /**
   * C++ original:
   * ```cpp
   * AnimationBuilder::AnimationInfo AnimationBuilder::parse(const skjson::ObjectValue& jroot) {
   *     this->dispatchMarkers(jroot["markers"]);
   *
   *     AutoScope ascope(this);
   *     AutoPropertyTracker apt(this, jroot, PropertyObserver::NodeType::COMPOSITION);
   *
   *     this->parseAssets(jroot["assets"]);
   *     this->parseFonts(jroot["fonts"], jroot["chars"]);
   *     fSlotsRoot = jroot["slots"];
   *
   *     auto root = CompositionBuilder(*this, fCompSize, jroot).build(*this);
   *
   *     auto animators = ascope.release();
   *     fStats->fAnimatorCount = animators.size();
   *
   *     // Point the revalidator to our final root, and perform initial revalidation.
   *     fRevalidator->setRoot(root);
   *     fRevalidator->revalidate();
   *
   *     return { std::move(root), std::move(animators), std::move(fSlotManager), std::move(fLayerInfo)};
   * }
   * ```
   */
  public fun parse(jroot: ObjectValue): AnimationInfo {
    TODO("Implement parse")
  }

  /**
   * C++ original:
   * ```cpp
   * const AnimationBuilder::FontInfo* AnimationBuilder::findFont(const SkString& font_name) const {
   *     return fFonts.find(font_name);
   * }
   * ```
   */
  public fun findFont(name: String): FontInfo {
    TODO("Implement findFont")
  }

  /**
   * C++ original:
   * ```cpp
   * void AnimationBuilder::log(Logger::Level lvl, const skjson::Value* json,
   *                            const char fmt[], ...) const {
   *     if (!fLogger) {
   *         return;
   *     }
   *
   *     char buff[1024];
   *     va_list va;
   *     va_start(va, fmt);
   *     const auto len = vsnprintf(buff, sizeof(buff), fmt, va);
   *     va_end(va);
   *
   *     if (len < 0) {
   *         SkDebugf("!! Could not format log message !!\n");
   *         return;
   *     }
   *
   *     if (len >= SkToInt(sizeof(buff))) {
   *         static constexpr char kEllipsesStr[] = "...";
   *         strcpy(buff + sizeof(buff) - sizeof(kEllipsesStr), kEllipsesStr);
   *     }
   *
   *     SkString jsonstr = json ? json->toString() : SkString();
   *
   *     fLogger->log(lvl, buff, jsonstr.c_str());
   * }
   * ```
   */
  public fun log(
    lvl: Logger.Level,
    json: Value?,
    fmt: CharArray,
    param3: Any,
  ) {
    TODO("Implement log")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::Transform> AnimationBuilder::attachMatrix2D(const skjson::ObjectValue& jtransform,
   *                                                         sk_sp<sksg::Transform> parent,
   *                                                         bool auto_orient) const {
   *     const auto* jrotation = &jtransform["r"];
   *     if (jrotation->is<skjson::NullValue>()) {
   *         // Some 2D rotations are disguised as 3D...
   *         jrotation = &jtransform["rz"];
   *     }
   *
   *     auto adapter = TransformAdapter2D::Make(*this,
   *                                             jtransform["a"],
   *                                             jtransform["p"],
   *                                             jtransform["s"],
   *                                             *jrotation,
   *                                             jtransform["sk"],
   *                                             jtransform["sa"],
   *                                             auto_orient);
   *     SkASSERT(adapter);
   *
   *     const auto dispatched = this->dispatchTransformProperty(adapter);
   *
   *     if (adapter->isStatic()) {
   *         if (!dispatched && adapter->totalMatrix().isIdentity()) {
   *             // The transform has no observable effects - we can discard.
   *             return parent;
   *         }
   *         adapter->seek(0);
   *     } else {
   *         fCurrentAnimatorScope->push_back(adapter);
   *     }
   *
   *     return sksg::Transform::MakeConcat(std::move(parent), adapter->node());
   * }
   * ```
   */
  public fun attachMatrix2D(
    jtransform: ObjectValue,
    parent: SkSp<Transform>,
    autoOrient: Boolean = TODO(),
  ): Int {
    TODO("Implement attachMatrix2D")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::Transform> AnimationBuilder::attachMatrix3D(const skjson::ObjectValue& jtransform,
   *                                                         sk_sp<sksg::Transform> parent,
   *                                                         bool /*TODO: auto_orient*/) const {
   *     auto adapter = TransformAdapter3D::Make(jtransform, *this);
   *     SkASSERT(adapter);
   *
   *     if (adapter->isStatic()) {
   *         // TODO: SkM44::isIdentity?
   *         if (adapter->totalMatrix() == SkM44()) {
   *             // The transform has no observable effects - we can discard.
   *             return parent;
   *         }
   *         adapter->seek(0);
   *     } else {
   *         fCurrentAnimatorScope->push_back(adapter);
   *     }
   *
   *     return sksg::Transform::MakeConcat(std::move(parent), adapter->node());
   * }
   * ```
   */
  public fun attachMatrix3D(
    jtransform: ObjectValue,
    parent: SkSp<Transform>,
    autoOrient: Boolean = TODO(),
  ): Int {
    TODO("Implement attachMatrix3D")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::Transform> AnimationBuilder::attachCamera(const skjson::ObjectValue& jlayer,
   *                                                       const skjson::ObjectValue& jtransform,
   *                                                       sk_sp<sksg::Transform> parent,
   *                                                       const SkSize& viewport_size) const {
   *     auto adapter = sk_make_sp<CameraAdaper>(jlayer, jtransform, *this, viewport_size);
   *
   *     if (adapter->isStatic()) {
   *         adapter->seek(0);
   *     } else {
   *         fCurrentAnimatorScope->push_back(adapter);
   *     }
   *
   *     return sksg::Transform::MakeConcat(adapter->node(), std::move(parent));
   * }
   * ```
   */
  public fun attachCamera(
    jlayer: ObjectValue,
    jtransform: ObjectValue,
    parent: SkSp<Transform>,
    viewportSize: SkSize,
  ): Int {
    TODO("Implement attachCamera")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> AnimationBuilder::attachOpacity(const skjson::ObjectValue& jobject,
   *                                                         sk_sp<sksg::RenderNode> child_node) const {
   *     if (!child_node)
   *         return nullptr;
   *
   *     auto adapter = OpacityAdapter::Make(jobject, child_node, *this);
   *     if (adapter->isStatic()) {
   *         adapter->seek(0);
   *     }
   *     auto dispatched = this->dispatchOpacityProperty(adapter->node());
   *     if (adapter->isStatic()) {
   *         if (!dispatched && adapter->node()->getOpacity() >= 1) {
   *             // No obeservable effects - we can discard.
   *             return child_node;
   *         }
   *     } else {
   *         fCurrentAnimatorScope->push_back(adapter);
   *     }
   *
   *     return adapter->node();
   * }
   * ```
   */
  public fun attachOpacity(jobject: ObjectValue, childNode: SkSp<RenderNode>): Int {
    TODO("Implement attachOpacity")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::Path> AnimationBuilder::attachPath(const skjson::Value& jpath) const {
   *     return this->attachDiscardableAdapter<PathAdapter>(jpath, *this);
   * }
   * ```
   */
  public fun attachPath(jpath: Value): Int {
    TODO("Implement attachPath")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hasNontrivialBlending() const { return fHasNontrivialBlending; }
   * ```
   */
  public fun hasNontrivialBlending(): Boolean {
    TODO("Implement hasNontrivialBlending")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <typename T>
   *     void attachDiscardableAdapter(sk_sp<T> adapter) const {
   *         if (adapter->isStatic()) {
   *             // Fire off a synthetic tick to force a single SG sync before discarding.
   *             adapter->seek(0);
   *         } else {
   *             fCurrentAnimatorScope->push_back(std::move(adapter));
   *         }
   *     }
   * ```
   */
  private fun <T> attachDiscardableAdapter(adapter: SkSp<T>) {
    TODO("Implement attachDiscardableAdapter")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <typename T, typename... Args>
   *     auto attachDiscardableAdapter(Args&&... args) const ->
   *         typename std::decay
   * ```
   */
  private fun <T, Args> attachDiscardableAdapter(args: Args): Any {
    TODO("Implement attachDiscardableAdapter")
  }

  /**
   * C++ original:
   * ```cpp
   * bool AnimationBuilder::dispatchColorProperty(const sk_sp<sksg::Color>& c) const {
   *     bool dispatched = false;
   *     if (fPropertyObserver) {
   *         const char * node_name = fPropertyObserverContext;
   *         fPropertyObserver->onColorProperty(node_name,
   *             [&]() {
   *                 dispatched = true;
   *                 return std::make_unique<ColorPropertyHandle>(c, fRevalidator);
   *             });
   *     }
   *
   *     return dispatched;
   * }
   * ```
   */
  private fun dispatchColorProperty(c: SkSp<Color>): Boolean {
    TODO("Implement dispatchColorProperty")
  }

  /**
   * C++ original:
   * ```cpp
   * bool AnimationBuilder::dispatchOpacityProperty(const sk_sp<sksg::OpacityEffect>& o) const {
   *     bool dispatched = false;
   *
   *     if (fPropertyObserver) {
   *         fPropertyObserver->onOpacityProperty(fPropertyObserverContext,
   *             [&]() {
   *                 dispatched = true;
   *                 return std::make_unique<OpacityPropertyHandle>(o, fRevalidator);
   *             });
   *     }
   *
   *     return dispatched;
   * }
   * ```
   */
  private fun dispatchOpacityProperty(o: SkSp<OpacityEffect>): Boolean {
    TODO("Implement dispatchOpacityProperty")
  }

  /**
   * C++ original:
   * ```cpp
   * bool AnimationBuilder::dispatchTextProperty(const sk_sp<TextAdapter>& t,
   *                                             const skjson::ObjectValue* jtext) const {
   *     bool dispatched = false;
   *
   *     if (jtext) {
   *         if (const skjson::StringValue* slotID = (*jtext)["sid"]) {
   *             fSlotManager->trackTextValue(SkString(slotID->begin()), t);
   *             dispatched = true;
   *         }
   *     }
   *
   *     if (fPropertyObserver) {
   *         fPropertyObserver->onTextProperty(fPropertyObserverContext,
   *             [&]() {
   *                 dispatched = true;
   *                 return std::make_unique<TextPropertyHandle>(t, fRevalidator);
   *             });
   *     }
   *
   *     return dispatched;
   * }
   * ```
   */
  private fun dispatchTextProperty(t: SkSp<TextAdapter>, jtext: ObjectValue?): Boolean {
    TODO("Implement dispatchTextProperty")
  }

  /**
   * C++ original:
   * ```cpp
   * bool AnimationBuilder::dispatchTransformProperty(const sk_sp<TransformAdapter2D>& t) const {
   *     bool dispatched = false;
   *
   *     if (fPropertyObserver) {
   *         fPropertyObserver->onTransformProperty(fPropertyObserverContext,
   *             [&]() {
   *                 dispatched = true;
   *                 return std::make_unique<TransformPropertyHandle>(t, fRevalidator);
   *             });
   *     }
   *
   *     return dispatched;
   * }
   * ```
   */
  private fun dispatchTransformProperty(t: SkSp<TransformAdapter2D>): Boolean {
    TODO("Implement dispatchTransformProperty")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<ExpressionManager> AnimationBuilder::expression_manager() const {
   *     return fExpressionManager;
   * }
   * ```
   */
  private fun expressionManager(): Int {
    TODO("Implement expressionManager")
  }

  /**
   * C++ original:
   * ```cpp
   * const skjson::ObjectValue* getSlotsRoot() const {
   *         return fSlotsRoot;
   *     }
   * ```
   */
  private fun getSlotsRoot(): ObjectValue {
    TODO("Implement getSlotsRoot")
  }

  /**
   * C++ original:
   * ```cpp
   * void AnimationBuilder::parseFonts(const skjson::ObjectValue* jfonts,
   *                                   const skjson::ArrayValue* jchars) {
   *     // Optional array of font entries, referenced (by name) from text layer document nodes. E.g.
   *     // "fonts": {
   *     //        "list": [
   *     //            {
   *     //                "ascent": 75,
   *     //                "fClass": "",
   *     //                "fFamily": "Roboto",
   *     //                "fName": "Roboto-Regular",
   *     //                "fPath": "https://fonts.googleapis.com/css?family=Roboto",
   *     //                "fPath": "",
   *     //                "fStyle": "Regular",
   *     //                "fWeight": "",
   *     //                "origin": 1
   *     //            }
   *     //        ]
   *     //    },
   *     const skjson::ArrayValue* jlist = jfonts
   *             ? static_cast<const skjson::ArrayValue*>((*jfonts)["list"])
   *             : nullptr;
   *     if (!jlist) {
   *         return;
   *     }
   *
   *     // First pass: collect font info.
   *     for (const skjson::ObjectValue* jfont : *jlist) {
   *         if (!jfont) {
   *             continue;
   *         }
   *
   *         const skjson::StringValue* jname   = (*jfont)["fName"];
   *         const skjson::StringValue* jfamily = (*jfont)["fFamily"];
   *         const skjson::StringValue* jstyle  = (*jfont)["fStyle"];
   *         const skjson::StringValue* jpath   = (*jfont)["fPath"];
   *
   *         if (!jname   || !jname->size() ||
   *             !jfamily || !jfamily->size() ||
   *             !jstyle) {
   *             this->log(Logger::Level::kError, jfont, "Invalid font.");
   *             continue;
   *         }
   *
   *         fFonts.set(SkString(jname->begin(), jname->size()),
   *                   {
   *                       SkString(jfamily->begin(), jfamily->size()),
   *                       SkString( jstyle->begin(),  jstyle->size()),
   *                       jpath ? SkString(  jpath->begin(),   jpath->size()) : SkString(),
   *                       ParseDefault((*jfont)["ascent"] , 0.0f),
   *                       nullptr, // placeholder
   *                       CustomFont::Builder()
   *                   });
   *     }
   *
   *     const auto has_comp_glyphs = [](const skjson::ArrayValue* jchars) {
   *         if (!jchars) {
   *             return false;
   *         }
   *
   *         for (const skjson::ObjectValue* jchar : *jchars) {
   *             if (!jchar) {
   *                 continue;
   *             }
   *             if (ParseDefault<int>((*jchar)["t"], 0) == 1) {
   *                 return true;
   *             }
   *         }
   *
   *         return false;
   *     };
   *
   *     // Historically, Skottie has been loading native fonts before embedded glyphs, unless
   *     // the opposite is explicitly requested via kPreferEmbeddedFonts.  That's mostly because
   *     // embedded glyphs used to be just a path representation of system fonts at export time,
   *     // (and thus lower quality).
   *     //
   *     // OTOH embedded glyph *compositions* must be prioritized, as they are presumably more
   *     // expressive than the system font equivalent.
   *     const auto prioritize_embedded_fonts =
   *             (fFlags & Animation::Builder::kPreferEmbeddedFonts) || has_comp_glyphs(jchars);
   *
   *     // Optional pass.
   *     if (jchars && prioritize_embedded_fonts && this->resolveEmbeddedTypefaces(*jchars)) {
   *         return;
   *     }
   *
   *     // Native typeface resolution.
   *     if (this->resolveNativeTypefaces()) {
   *         return;
   *     }
   *
   *     // Embedded typeface fallback.
   *     if (jchars && !prioritize_embedded_fonts) {
   *         this->resolveEmbeddedTypefaces(*jchars);
   *     }
   * }
   * ```
   */
  private fun parseFonts(jfonts: ObjectValue?, jchars: ArrayValue?) {
    TODO("Implement parseFonts")
  }

  /**
   * C++ original:
   * ```cpp
   * void AnimationBuilder::parseAssets(const skjson::ArrayValue* jassets) {
   *     if (!jassets) {
   *         return;
   *     }
   *
   *     for (const skjson::ObjectValue* asset : *jassets) {
   *         if (asset) {
   *             fAssets.set(ParseDefault<SkString>((*asset)["id"], SkString()), { asset, false });
   *         }
   *     }
   * }
   * ```
   */
  private fun parseAssets(jassets: ArrayValue?) {
    TODO("Implement parseAssets")
  }

  /**
   * C++ original:
   * ```cpp
   * bool AnimationBuilder::resolveNativeTypefaces() {
   *     bool has_unresolved = false;
   *
   *     fFonts.foreach([&](const SkString& name, FontInfo* finfo) {
   *         SkASSERT(finfo);
   *
   *         if (finfo->fTypeface) {
   *             // Already resolved from glyph paths.
   *             return;
   *         }
   *
   *         // Typeface fallback order:
   *         //   1) externally-loaded font (provided by the embedder)
   *         //   2) system font (family/style)
   *         //   3) system default
   *
   *         finfo->fTypeface = fResourceProvider->loadTypeface(name.c_str(), finfo->fPath.c_str());
   *
   *         // legacy API fallback
   *         // TODO: remove after client migration
   *         if (!finfo->fTypeface && fFontMgr) {
   *             finfo->fTypeface = fFontMgr->makeFromData(
   *                     fResourceProvider->loadFont(name.c_str(), finfo->fPath.c_str()));
   *         }
   *
   *         if (!finfo->fTypeface && fFontMgr) {
   *             finfo->fTypeface = fFontMgr->matchFamilyStyle(finfo->fFamily.c_str(),
   *                                                       FontStyle(this, finfo->fStyle.c_str()));
   *
   *             if (!finfo->fTypeface) {
   *                 this->log(Logger::Level::kError, nullptr, "Could not create typeface for %s|%s.",
   *                           finfo->fFamily.c_str(), finfo->fStyle.c_str());
   *                 // Last resort.
   *                 finfo->fTypeface = fFontMgr->legacyMakeTypeface(nullptr,
   *                                                             FontStyle(this, finfo->fStyle.c_str()));
   *
   *                 has_unresolved |= !finfo->fTypeface;
   *             }
   *         }
   *         if (!finfo->fTypeface && !fFontMgr) {
   *             this->log(Logger::Level::kError, nullptr,
   *                       "Could not load typeface for %s|%s because no SkFontMgr provided.",
   *                       finfo->fFamily.c_str(), finfo->fStyle.c_str());
   *         }
   *     });
   *
   *     return !has_unresolved;
   * }
   * ```
   */
  private fun resolveNativeTypefaces(): Boolean {
    TODO("Implement resolveNativeTypefaces")
  }

  /**
   * C++ original:
   * ```cpp
   * bool AnimationBuilder::resolveEmbeddedTypefaces(const skjson::ArrayValue& jchars) {
   *     // Optional array of glyphs, to be associated with one of the declared fonts. E.g.
   *     // "chars": [
   *     //     {
   *     //         "fFamily": "Roboto",       // part of the font key
   *     //         "style": "Regular",        // part of the font key
   *     //         ...                        // glyph data
   *     //    }
   *     // ]
   *     FontInfo* current_font = nullptr;
   *
   *     for (const skjson::ObjectValue* jchar : jchars) {
   *         if (!jchar) {
   *             continue;
   *         }
   *
   *         const skjson::StringValue* jfamily = (*jchar)["fFamily"];
   *         const skjson::StringValue* jstyle  = (*jchar)["style"]; // "style", not "fStyle"...
   *
   *         if (!jfamily || !jstyle) {
   *             this->log(Logger::Level::kError, jchar, "Invalid glyph.");
   *             continue;
   *         }
   *         const auto* family = jfamily->begin();
   *         const auto* style  = jstyle->begin();
   *
   *         // Locate (and cache) the font info. Unlike text nodes, glyphs reference the font by
   *         // (family, style) -- not by name :(  For now this performs a linear search over *all*
   *         // fonts: generally there are few of them, and glyph definitions are font-clustered.
   *         // If problematic, we can refactor as a two-level hashmap.
   *         if (!current_font || !current_font->matches(family, style)) {
   *             current_font = nullptr;
   *             fFonts.foreach([&](const SkString& name, FontInfo* finfo) {
   *                 if (finfo->matches(family, style)) {
   *                     current_font = finfo;
   *                     // TODO: would be nice to break early here...
   *                 }
   *             });
   *             if (!current_font) {
   *                 this->log(Logger::Level::kError, nullptr,
   *                           "Font not found (%s, %s).", family, style);
   *                 continue;
   *             }
   *         }
   *
   *         if (!current_font->fCustomFontBuilder.parseGlyph(this, *jchar)) {
   *             this->log(Logger::Level::kError, jchar, "Invalid glyph.");
   *         }
   *     }
   *
   *     // Final pass to commit custom typefaces.
   *     bool has_unresolved = false;
   *     std::vector<std::unique_ptr<CustomFont>> custom_fonts;
   *     fFonts.foreach([&has_unresolved, &custom_fonts](const SkString&, FontInfo* finfo) {
   *         if (finfo->fTypeface) {
   *             return; // already resolved
   *         }
   *
   *         auto font = finfo->fCustomFontBuilder.detach();
   *
   *         finfo->fTypeface = font->typeface();
   *
   *         if (font->glyphCompCount() > 0) {
   *             custom_fonts.push_back(std::move(font));
   *         }
   *
   *         has_unresolved |= !finfo->fTypeface;
   *     });
   *
   *     // Stash custom font data for later use.
   *     if (!custom_fonts.empty()) {
   *         custom_fonts.shrink_to_fit();
   *         fCustomGlyphMapper = sk_make_sp<CustomFont::GlyphCompMapper>(std::move(custom_fonts));
   *     }
   *
   *     return !has_unresolved;
   * }
   * ```
   */
  private fun resolveEmbeddedTypefaces(jchars: ArrayValue): Boolean {
    TODO("Implement resolveEmbeddedTypefaces")
  }

  /**
   * C++ original:
   * ```cpp
   * void AnimationBuilder::dispatchMarkers(const skjson::ArrayValue* jmarkers) const {
   *     if (!fMarkerObserver || !jmarkers) {
   *         return;
   *     }
   *
   *     // For frame-number -> t conversions.
   *     const auto frameRatio = 1 / (fFrameRate * fDuration);
   *
   *     for (const skjson::ObjectValue* m : *jmarkers) {
   *         if (!m) continue;
   *
   *         const skjson::StringValue* name = (*m)["cm"];
   *         const auto time = ParseDefault((*m)["tm"], -1.0f),
   *                duration = ParseDefault((*m)["dr"], -1.0f);
   *
   *         if (name && time >= 0 && duration >= 0) {
   *             fMarkerObserver->onMarker(
   *                         name->begin(),
   *                         // "tm" is in frames
   *                         time * frameRatio,
   *                         // ... as is "dr"
   *                         (time + duration) * frameRatio
   *             );
   *         } else {
   *             this->log(Logger::Level::kWarning, m, "Ignoring unexpected marker.");
   *         }
   *     }
   * }
   * ```
   */
  private fun dispatchMarkers(jmarkers: ArrayValue?) {
    TODO("Implement dispatchMarkers")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> AnimationBuilder::attachBlendMode(const skjson::ObjectValue& jobject,
   *                                                           sk_sp<sksg::RenderNode> child) const {
   *     if (auto blender = get_blender(jobject, this)) {
   *         fHasNontrivialBlending = true;
   *         child = sksg::BlenderEffect::Make(std::move(child), std::move(blender));
   *     }
   *
   *     return child;
   * }
   * ```
   */
  private fun attachBlendMode(jobject: ObjectValue, child: SkSp<RenderNode>): Int {
    TODO("Implement attachBlendMode")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> AnimationBuilder::attachShape(const skjson::ArrayValue* jshape,
   *                                                       AttachShapeContext* ctx,
   *                                                       bool suppress_draws) const {
   *     if (!jshape)
   *         return nullptr;
   *
   *     SkDEBUGCODE(const auto initialGeometryEffects = ctx->fGeometryEffectStack->size();)
   *
   *     const skjson::ObjectValue* jtransform = nullptr;
   *
   *     struct ShapeRec {
   *         const skjson::ObjectValue& fJson;
   *         const ShapeInfo&           fInfo;
   *         bool                       fSuppressed;
   *     };
   *
   *     // First pass (bottom->top):
   *     //
   *     //   * pick up the group transform and opacity
   *     //   * push local geometry effects onto the stack
   *     //   * store recs for next pass
   *     //
   *     std::vector<ShapeRec> recs;
   *     for (size_t i = 0; i < jshape->size(); ++i) {
   *         const skjson::ObjectValue* shape = (*jshape)[jshape->size() - 1 - i];
   *         if (!shape) continue;
   *
   *         const auto* info = FindShapeInfo(*shape);
   *         if (!info) {
   *             this->log(Logger::Level::kError, &(*shape)["ty"], "Unknown shape.");
   *             continue;
   *         }
   *
   *         if (ParseDefault<bool>((*shape)["hd"], false)) {
   *             // Ignore hidden shapes.
   *             continue;
   *         }
   *
   *         recs.push_back({ *shape, *info, suppress_draws });
   *
   *         // Some effects (merge) suppress any paints above them.
   *         suppress_draws |= (info->fFlags & kSuppressDraws) != 0;
   *
   *         switch (info->fShapeType) {
   *         case ShapeType::kTransform:
   *             // Just track the transform property for now -- we'll deal with it later.
   *             jtransform = shape;
   *             break;
   *         case ShapeType::kGeometryEffect:
   *             SkASSERT(info->fAttacherIndex < std::size(gGeometryEffectAttachers));
   *             ctx->fGeometryEffectStack->push_back(
   *                 { *shape, gGeometryEffectAttachers[info->fAttacherIndex] });
   *             break;
   *         default:
   *             break;
   *         }
   *     }
   *
   *     // Second pass (top -> bottom, after 2x reverse):
   *     //
   *     //   * track local geometry
   *     //   * emit local paints
   *     //
   *     std::vector<sk_sp<sksg::GeometryNode>> geos;
   *     std::vector<sk_sp<sksg::RenderNode  >> draws;
   *
   *     const auto add_draw = [this, &draws](sk_sp<sksg::RenderNode> draw, const ShapeRec& rec) {
   *         // All draws can have an optional blend mode.
   *         draws.push_back(this->attachBlendMode(rec.fJson, std::move(draw)));
   *     };
   *
   *     for (auto rec = recs.rbegin(); rec != recs.rend(); ++rec) {
   *         const AutoPropertyTracker apt(this, rec->fJson, PropertyObserver::NodeType::OTHER);
   *
   *         switch (rec->fInfo.fShapeType) {
   *         case ShapeType::kGeometry: {
   *             SkASSERT(rec->fInfo.fAttacherIndex < std::size(gGeometryAttachers));
   *             if (auto geo = gGeometryAttachers[rec->fInfo.fAttacherIndex](rec->fJson, this)) {
   *                 geos.push_back(std::move(geo));
   *             }
   *         } break;
   *         case ShapeType::kGeometryEffect: {
   *             // Apply the current effect and pop from the stack.
   *             SkASSERT(rec->fInfo.fAttacherIndex < std::size(gGeometryEffectAttachers));
   *             if (!geos.empty()) {
   *                 geos = gGeometryEffectAttachers[rec->fInfo.fAttacherIndex](rec->fJson,
   *                                                                            this,
   *                                                                            std::move(geos));
   *             }
   *
   *             SkASSERT(&ctx->fGeometryEffectStack->back().fJson == &rec->fJson);
   *             SkASSERT(ctx->fGeometryEffectStack->back().fAttach ==
   *                      gGeometryEffectAttachers[rec->fInfo.fAttacherIndex]);
   *             ctx->fGeometryEffectStack->pop_back();
   *         } break;
   *         case ShapeType::kGroup: {
   *             AttachShapeContext groupShapeCtx(&geos,
   *                                              ctx->fGeometryEffectStack,
   *                                              ctx->fCommittedAnimators);
   *             if (auto subgroup =
   *                 this->attachShape(rec->fJson["it"], &groupShapeCtx, rec->fSuppressed)) {
   *                 add_draw(std::move(subgroup), *rec);
   *                 SkASSERT(groupShapeCtx.fCommittedAnimators >= ctx->fCommittedAnimators);
   *                 ctx->fCommittedAnimators = groupShapeCtx.fCommittedAnimators;
   *             }
   *         } break;
   *         case ShapeType::kPaint: {
   *             SkASSERT(rec->fInfo.fAttacherIndex < std::size(gPaintAttachers));
   *             auto paint = gPaintAttachers[rec->fInfo.fAttacherIndex](rec->fJson, this);
   *             if (!paint || geos.empty() || rec->fSuppressed)
   *                 break;
   *
   *             auto drawGeos = geos;
   *
   *             // Apply all pending effects from the stack.
   *             for (auto it = ctx->fGeometryEffectStack->rbegin();
   *                  it != ctx->fGeometryEffectStack->rend(); ++it) {
   *                 drawGeos = it->fAttach(it->fJson, this, std::move(drawGeos));
   *             }
   *
   *             // Apply local paint geometry adjustments (e.g. dashing).
   *             SkASSERT(rec->fInfo.fAttacherIndex < std::size(gPaintGeometryAdjusters));
   *             if (const auto adjuster = gPaintGeometryAdjusters[rec->fInfo.fAttacherIndex]) {
   *                 drawGeos = adjuster(rec->fJson, this, std::move(drawGeos));
   *             }
   *
   *             // If we still have multiple geos, reduce using 'merge'.
   *             auto geo = drawGeos.size() > 1
   *                 ? ShapeBuilder::MergeGeometry(std::move(drawGeos), sksg::Merge::Mode::kMerge)
   *                 : drawGeos[0];
   *             SkASSERT(geo);
   *
   *             // Apply paint-specific fill rule if needed.
   *             geo = AdjustGeometryFillRule(std::move(geo), rec->fJson);
   *
   *             add_draw(sksg::Draw::Make(std::move(geo), std::move(paint)), *rec);
   *             ctx->fCommittedAnimators = fCurrentAnimatorScope->size();
   *         } break;
   *         case ShapeType::kDrawEffect: {
   *             SkASSERT(rec->fInfo.fAttacherIndex < std::size(gDrawEffectAttachers));
   *             if (!draws.empty()) {
   *                 draws = gDrawEffectAttachers[rec->fInfo.fAttacherIndex](rec->fJson,
   *                                                                         this,
   *                                                                         std::move(draws));
   *                 ctx->fCommittedAnimators = fCurrentAnimatorScope->size();
   *             }
   *         } break;
   *         default:
   *             break;
   *         }
   *     }
   *
   *     // By now we should have popped all local geometry effects.
   *     SkASSERT(ctx->fGeometryEffectStack->size() == initialGeometryEffects);
   *
   *     sk_sp<sksg::RenderNode> shape_wrapper;
   *     if (draws.size() == 1) {
   *         // For a single draw, we don't need a group.
   *         shape_wrapper = std::move(draws.front());
   *     } else if (!draws.empty()) {
   *         // Emit local draws reversed (bottom->top, per spec).
   *         std::reverse(draws.begin(), draws.end());
   *         draws.shrink_to_fit();
   *
   *         // We need a group to dispatch multiple draws.
   *         shape_wrapper = sksg::Group::Make(std::move(draws));
   *     }
   *
   *     sk_sp<sksg::Transform> shape_transform;
   *     if (jtransform) {
   *         const AutoPropertyTracker apt(this, *jtransform, PropertyObserver::NodeType::OTHER);
   *
   *         // This is tricky due to the interaction with ctx->fCommittedAnimators: we want any
   *         // animators related to tranform/opacity to be committed => they must be inserted in front
   *         // of the dangling/uncommitted ones.
   *         AutoScope ascope(this);
   *
   *         if ((shape_transform = this->attachMatrix2D(*jtransform, nullptr))) {
   *             shape_wrapper = sksg::TransformEffect::Make(std::move(shape_wrapper), shape_transform);
   *         }
   *         shape_wrapper = this->attachOpacity(*jtransform, std::move(shape_wrapper));
   *
   *         auto local_scope = ascope.release();
   *         fCurrentAnimatorScope->insert(fCurrentAnimatorScope->begin() + ctx->fCommittedAnimators,
   *                                       std::make_move_iterator(local_scope.begin()),
   *                                       std::make_move_iterator(local_scope.end()));
   *         ctx->fCommittedAnimators += local_scope.size();
   *     }
   *
   *     // Push transformed local geometries to parent list, for subsequent paints.
   *     for (auto& geo : geos) {
   *         ctx->fGeometryStack->push_back(shape_transform
   *             ? sksg::GeometryTransform::Make(std::move(geo), shape_transform)
   *             : std::move(geo));
   *     }
   *
   *     return shape_wrapper;
   * }
   * ```
   */
  private fun attachShape(
    jshape: ArrayValue?,
    ctx: AttachShapeContext?,
    suppressDraws: Boolean = TODO(),
  ): Int {
    TODO("Implement attachShape")
  }

  /**
   * C++ original:
   * ```cpp
   * const AnimationBuilder::FootageAssetInfo*
   * AnimationBuilder::loadFootageAsset(const skjson::ObjectValue& defaultJImage) const {
   *     const skjson::ObjectValue* jimage = &defaultJImage;
   *     const skjson::StringValue* slotID = defaultJImage["sid"];
   *     if (slotID) {
   *         if (!(fSlotsRoot)) {
   *             this->log(Logger::Level::kWarning, nullptr,
   *                          "Slotid found but no slots were found in the json. Using default asset.");
   *         } else {
   *             const skjson::ObjectValue* slot = (*(fSlotsRoot))[slotID->begin()];
   *             if (!slot) {
   *                 this->log(Logger::Level::kWarning, nullptr,
   *                              "Specified slotID not found in 'slots'. Using default asset.");
   *             } else {
   *                 jimage = (*slot)["p"];
   *             }
   *         }
   *     }
   *
   *     const skjson::StringValue* name = (*jimage)["p"];
   *     const skjson::StringValue* path = (*jimage)["u"];
   *     const skjson::StringValue* id   = (*jimage)["id"];
   *     if (!name || !path || !id) {
   *         return nullptr;
   *     }
   *
   *     const SkString res_id(id->begin());
   *     if (auto* cached_info = fImageAssetCache.find(res_id)) {
   *         return cached_info;
   *     }
   *
   *     auto asset = fResourceProvider->loadImageAsset(path->begin(), name->begin(), id->begin());
   *     if (!asset && !slotID) {
   *         this->log(Logger::Level::kError, nullptr, "Could not load image asset: %s/%s (id: '%s').",
   *                   path->begin(), name->begin(), id->begin());
   *         return nullptr;
   *     }
   *
   *     if (slotID) {
   *         asset = fSlotManager->trackImageValue(SkString(slotID->begin()), std::move(asset));
   *     }
   *     const auto size = SkISize::Make(ParseDefault<int>((*jimage)["w"], 0),
   *                                     ParseDefault<int>((*jimage)["h"], 0));
   *     return fImageAssetCache.set(res_id, { std::move(asset), size });
   * }
   * ```
   */
  private fun loadFootageAsset(defaultJImage: ObjectValue): FootageAssetInfo {
    TODO("Implement loadFootageAsset")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> AnimationBuilder::attachFootageAsset(const skjson::ObjectValue& jimage,
   *                                                              LayerInfo* layer_info) const {
   *     const auto* asset_info = this->loadFootageAsset(jimage);
   *     if (!asset_info) {
   *         return nullptr;
   *     }
   *     SkASSERT(asset_info->fAsset);
   *
   *     auto image_node = sksg::Image::Make(nullptr);
   *
   *     // Optional image transform (mapping the intrinsic image size to declared asset size).
   *     sk_sp<sksg::Matrix<SkMatrix>> image_transform;
   *
   *     const auto requires_animator = (fFlags & Animation::Builder::kDeferImageLoading)
   *                                     || asset_info->fAsset->isMultiFrame();
   *     if (requires_animator) {
   *         // We don't know the intrinsic image size yet (plus, in the general case,
   *         // the size may change from frame to frame) -> we always prepare a scaling transform.
   *         image_transform = sksg::Matrix<SkMatrix>::Make(SkMatrix::I());
   *         fCurrentAnimatorScope->push_back(sk_make_sp<FootageAnimator>(asset_info->fAsset,
   *                                                                      image_node,
   *                                                                      image_transform,
   *                                                                      asset_info->fSize,
   *                                                                      -layer_info->fInPoint,
   *                                                                      1 / fFrameRate));
   *     } else {
   *         // No animator needed, resolve the (only) frame upfront.
   *         auto frame_data = asset_info->fAsset->getFrameData(0);
   *         if (!frame_data.image) {
   *             this->log(Logger::Level::kError, nullptr, "Could not load single-frame image asset.");
   *             return nullptr;
   *         }
   *
   *         const auto m = image_matrix(frame_data, asset_info->fSize);
   *         if (!m.isIdentity()) {
   *             image_transform = sksg::Matrix<SkMatrix>::Make(m);
   *         }
   *
   *         image_node->setImage(std::move(frame_data.image));
   *         image_node->setSamplingOptions(frame_data.sampling);
   *     }
   *
   *     // Image layers are sized explicitly.
   *     layer_info->fSize = SkSize::Make(asset_info->fSize);
   *
   *     if (!image_transform) {
   *         // No resize needed.
   *         return image_node;
   *     }
   *
   *     return sksg::TransformEffect::Make(std::move(image_node), std::move(image_transform));
   * }
   * ```
   */
  private fun attachFootageAsset(jimage: ObjectValue, layerInfo: LayerInfo?): Int {
    TODO("Implement attachFootageAsset")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> AnimationBuilder::attachExternalPrecompLayer(
   *         const skjson::ObjectValue& jlayer,
   *         const LayerInfo& layer_info) const {
   *
   *     if (!fPrecompInterceptor) {
   *         return nullptr;
   *     }
   *
   *     const skjson::StringValue* id = jlayer["refId"];
   *     const skjson::StringValue* nm = jlayer["nm"];
   *
   *     if (!id || !nm) {
   *         return nullptr;
   *     }
   *
   *     auto external_layer = fPrecompInterceptor->onLoadPrecomp(id->begin(),
   *                                                              nm->begin(),
   *                                                              layer_info.fSize);
   *     if (!external_layer) {
   *         return nullptr;
   *     }
   *
   *     // Attaches an ExternalLayer implementation to the animation scene graph.
   *     class SGAdapter final : public sksg::RenderNode {
   *     public:
   *         SG_ATTRIBUTE(T, float, fCurrentT)
   *
   *         SGAdapter(sk_sp<ExternalLayer> external, const SkSize& layer_size)
   *             : fExternal(std::move(external))
   *             , fSize(layer_size) {}
   *
   *     private:
   *         SkRect onRevalidate(sksg::InvalidationController*, const SkMatrix&) override {
   *             return SkRect::MakeSize(fSize);
   *         }
   *
   *         void onRender(SkCanvas* canvas, const RenderContext* ctx) const override {
   *             // Commit all pending effects via a layer if needed,
   *             // since we don't have knowledge of the external content.
   *             const auto local_scope =
   *                 ScopedRenderContext(canvas, ctx).setIsolation(this->bounds(),
   *                                                               canvas->getTotalMatrix(),
   *                                                               true);
   *             fExternal->render(canvas, static_cast<double>(fCurrentT));
   *         }
   *
   *         const RenderNode* onNodeAt(const SkPoint& pt) const override {
   *             SkASSERT(this->bounds().contains(pt.fX, pt.fY));
   *             return this;
   *         }
   *
   *         const sk_sp<ExternalLayer> fExternal;
   *         const SkSize               fSize;
   *         float                      fCurrentT = 0;
   *     };
   *
   *     // Connects an SGAdapter to the animator tree and dispatches seek events.
   *     class AnimatorAdapter final : public Animator {
   *     public:
   *         AnimatorAdapter(sk_sp<SGAdapter> sg_adapter, float fps)
   *             : fSGAdapter(std::move(sg_adapter))
   *             , fFps(fps) {}
   *
   *     private:
   *         StateChanged onSeek(float t) override {
   *             fSGAdapter->setT(t / fFps);
   *
   *             return true;
   *         }
   *
   *         const sk_sp<SGAdapter> fSGAdapter;
   *         const float            fFps;
   *     };
   *
   *     auto sg_adapter = sk_make_sp<SGAdapter>(std::move(external_layer), layer_info.fSize);
   *
   *     fCurrentAnimatorScope->push_back(sk_make_sp<AnimatorAdapter>(sg_adapter, fFrameRate));
   *
   *     return sg_adapter;
   * }
   * ```
   */
  private fun attachExternalPrecompLayer(jlayer: ObjectValue, layerInfo: LayerInfo): Int {
    TODO("Implement attachExternalPrecompLayer")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> AnimationBuilder::attachFootageLayer(const skjson::ObjectValue& jlayer,
   *                                                              LayerInfo* layer_info) const {
   *     const ScopedAssetRef footage_asset(this, jlayer);
   *
   *     return footage_asset
   *         ? this->attachFootageAsset(*footage_asset, layer_info)
   *         : nullptr;
   * }
   * ```
   */
  private fun attachFootageLayer(jlayer: ObjectValue, layerInfo: LayerInfo?): Int {
    TODO("Implement attachFootageLayer")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> AnimationBuilder::attachNullLayer(const skjson::ObjectValue& layer,
   *                                                           LayerInfo*) const {
   *     // Null layers are used solely to drive dependent transforms,
   *     // but we use free-floating sksg::Matrices for that purpose.
   *     return nullptr;
   * }
   * ```
   */
  private fun attachNullLayer(layer: ObjectValue, param1: LayerInfo?): Int {
    TODO("Implement attachNullLayer")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> AnimationBuilder::attachPrecompLayer(const skjson::ObjectValue& jlayer,
   *                                                              LayerInfo* layer_info) const {
   *     sk_sp<TimeRemapper> time_remapper;
   *     if (const skjson::ObjectValue* jtm = jlayer["tm"]) {
   *         time_remapper = sk_make_sp<TimeRemapper>(*jtm, this, fFrameRate);
   *     }
   *
   *     const auto start_time = ParseDefault<float>(jlayer["st"], 0.0f),
   *              stretch_time = ParseDefault<float>(jlayer["sr"], 1.0f);
   *     const auto requires_time_mapping = !SkScalarNearlyEqual(start_time  , 0) ||
   *                                        !SkScalarNearlyEqual(stretch_time, 1) ||
   *                                        time_remapper;
   *
   *     // Precomp layers are sized explicitly.
   *     auto parse_size = [](const skjson::ObjectValue& jlayer) {
   *         return SkSize::Make(ParseDefault<float>(jlayer["w"], 0.0f),
   *                             ParseDefault<float>(jlayer["h"], 0.0f));
   *     };
   *     layer_info->fSize = parse_size(jlayer);
   *
   *     std::optional<AutoScope> local_scope;
   *     if (requires_time_mapping) {
   *         local_scope.emplace(this);
   *     }
   *
   *     auto precomp_layer = this->attachExternalPrecompLayer(jlayer, *layer_info);
   *
   *     if (!precomp_layer) {
   *         const ScopedAssetRef precomp_asset(this, jlayer);
   *         if (precomp_asset) {
   *             // Unlike regular precomp layers, glyph precomps don't have an explicit size - they
   *             // use the actual asset comp size.
   *             if (layer_info->fSize.isEmpty()) {
   *                 layer_info->fSize = parse_size(*precomp_asset);
   *             }
   *
   *             AutoPropertyTracker apt(this, *precomp_asset, PropertyObserver::NodeType::COMPOSITION);
   *             precomp_layer =
   *                 CompositionBuilder(*this, layer_info->fSize, *precomp_asset).build(*this);
   *         }
   *     }
   *
   *     if (requires_time_mapping) {
   *         const auto t_bias  = -start_time,
   *                    t_scale = sk_ieee_float_divide(1, stretch_time);
   *         auto time_mapper = sk_make_sp<CompTimeMapper>(local_scope->release(),
   *                                                       std::move(time_remapper),
   *                                                       t_bias,
   *                                                       std::isfinite(t_scale) ? t_scale : 0);
   *
   *         fCurrentAnimatorScope->push_back(std::move(time_mapper));
   *     }
   *
   *     return precomp_layer;
   * }
   * ```
   */
  private fun attachPrecompLayer(jlayer: ObjectValue, layerInfo: LayerInfo?): Int {
    TODO("Implement attachPrecompLayer")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> AnimationBuilder::attachShapeLayer(const skjson::ObjectValue& layer,
   *                                                            LayerInfo*) const {
   *     std::vector<sk_sp<sksg::GeometryNode>> geometryStack;
   *     std::vector<GeometryEffectRec> geometryEffectStack;
   *     AttachShapeContext shapeCtx(&geometryStack, &geometryEffectStack,
   *                                 fCurrentAnimatorScope->size());
   *     auto shapeNode = this->attachShape(layer["shapes"], &shapeCtx);
   *
   *     // Trim uncommitted animators: AttachShape consumes effects on the fly, and greedily attaches
   *     // geometries => at the end, we can end up with unused geometries, which are nevertheless alive
   *     // due to attached animators.  To avoid this, we track committed animators and discard the
   *     // orphans here.
   *     SkASSERT(shapeCtx.fCommittedAnimators <= fCurrentAnimatorScope->size());
   *     fCurrentAnimatorScope->resize(shapeCtx.fCommittedAnimators);
   *
   *     return shapeNode;
   * }
   * ```
   */
  private fun attachShapeLayer(layer: ObjectValue, param1: LayerInfo?): Int {
    TODO("Implement attachShapeLayer")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> AnimationBuilder::attachSolidLayer(const skjson::ObjectValue& jlayer,
   *                                                            LayerInfo* layer_info) const {
   *     layer_info->fSize = SkSize::Make(ParseDefault<float>(jlayer["sw"], 0.0f),
   *                                      ParseDefault<float>(jlayer["sh"], 0.0f));
   *     const skjson::StringValue* hex_str = jlayer["sc"];
   *     uint32_t c;
   *     if (layer_info->fSize.isEmpty() ||
   *         !hex_str ||
   *         *hex_str->begin() != '#' ||
   *         !SkParse::FindHex(hex_str->begin() + 1, &c)) {
   *         this->log(Logger::Level::kError, &jlayer, "Could not parse solid layer.");
   *         return nullptr;
   *     }
   *
   *     const SkColor color = 0xff000000 | c;
   *
   *     auto solid_paint = sksg::Color::Make(color);
   *     solid_paint->setAntiAlias(true);
   *     this->dispatchColorProperty(solid_paint);
   *
   *     return sksg::Draw::Make(sksg::Rect::Make(SkRect::MakeSize(layer_info->fSize)),
   *                             std::move(solid_paint));
   * }
   * ```
   */
  private fun attachSolidLayer(jlayer: ObjectValue, layerInfo: LayerInfo?): Int {
    TODO("Implement attachSolidLayer")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> AnimationBuilder::attachTextLayer(const skjson::ObjectValue& jlayer,
   *                                                           LayerInfo*) const {
   *     return this->attachDiscardableAdapter<TextAdapter>(jlayer,
   *                                                        this,
   *                                                        fFontMgr,
   *                                                        fCustomGlyphMapper,
   *                                                        fLogger,
   *                                                        fShapingFactory);
   * }
   * ```
   */
  private fun attachTextLayer(jlayer: ObjectValue, param1: LayerInfo?): Int {
    TODO("Implement attachTextLayer")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> AnimationBuilder::attachAudioLayer(const skjson::ObjectValue& jlayer,
   *                                                            LayerInfo* layer_info) const {
   *     const ScopedAssetRef audio_asset(this, jlayer);
   *
   *     if (audio_asset) {
   *         const auto& jaudio = *audio_asset;
   *         const skjson::StringValue* name = jaudio["p"];
   *         const skjson::StringValue* path = jaudio["u"];
   *         const skjson::StringValue* id   = jaudio["id"];
   *
   *         if (name && path && id) {
   *             auto track = fResourceProvider->loadAudioAsset(path->begin(),
   *                                                            name->begin(),
   *                                                            id  ->begin());
   *             if (track) {
   *                 fCurrentAnimatorScope->push_back(
   *                     sk_make_sp<ForwardingPlaybackController>(std::move(track),
   *                                                              layer_info->fInPoint,
   *                                                              layer_info->fOutPoint,
   *                                                              fFrameRate));
   *             } else {
   *                 this->log(Logger::Level::kWarning, nullptr,
   *                           "Could not load audio asset '%s'.", name->begin());
   *             }
   *         }
   *     }
   *
   *     // no render node, playback is controlled from the Animator tree.
   *     return nullptr;
   * }
   * ```
   */
  private fun attachAudioLayer(jlayer: ObjectValue, layerInfo: LayerInfo?): Int {
    TODO("Implement attachAudioLayer")
  }

  /**
   * C++ original:
   * ```cpp
   * void trackLayerInfo(const LayerInfo& info) const {fLayerInfo.emplace_back(info);}
   * ```
   */
  private fun trackLayerInfo(info: LayerInfo) {
    TODO("Implement trackLayerInfo")
  }

  public data class AnimationInfo public constructor(
    public var fSceneRoot: Int,
    public var fAnimators: Int,
    public var fSlotManager: Int,
    public var fLayerInfo: Int,
  )

  public data class FontInfo public constructor(
    public var fFamily: Int,
    public var fStyle: Int,
    public var fPath: Int,
    public var fAscentPct: Int,
    public var fTypeface: Int,
    public var fCustomFontBuilder: Int,
  ) {
    public fun matches(family: CharArray, style: CharArray): Boolean {
      TODO("Implement matches")
    }
  }

  public data class AutoScope public constructor(
    private val fBuilder: AnimationBuilder?,
    private var fCurrentScope: Int,
    private var fPrevScope: Int?,
  ) {
    public fun release(): Int {
      TODO("Implement release")
    }
  }

  public data class AssetInfo public constructor(
    public val fAsset: ObjectValue?,
    public var fIsAttaching: Boolean,
  )

  public data class FootageAssetInfo public constructor(
    public var fAsset: Int,
    public var fSize: Int,
  )

  public data class ScopedAssetRef public constructor(
    private val fInfo: AssetInfo?,
  )
}
