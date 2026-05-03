package org.skia.modules

import SkShapers.Factory
import `internal`.Animator
import kotlin.CharArray
import kotlin.Double
import kotlin.Float
import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.ULong
import kotlin.collections.List
import org.skia.core.SkCanvas
import org.skia.foundation.SkFontMgr
import org.skia.foundation.SkNVRefCnt
import org.skia.foundation.SkSp
import org.skia.foundation.SkStream
import org.skia.math.SkRect
import org.skia.math.SkScalar
import org.skia.math.SkSize

/**
 * C++ original:
 * ```cpp
 * class SK_API Animation : public SkNVRefCnt<Animation> {
 * public:
 *     class SK_API Builder final {
 *     public:
 *         enum Flags : uint32_t {
 *             kDeferImageLoading   = 0x01, // Normally, all static image frames are resolved at
 *                                          // load time via ImageAsset::getFrame(0).  With this flag,
 *                                          // frames are only resolved when needed, at seek() time.
 *             kPreferEmbeddedFonts = 0x02, // Attempt to use the embedded fonts (glyph paths,
 *                                          // normally used as fallback) over native Skia typefaces.
 *         };
 *
 *         explicit Builder(uint32_t flags = 0);
 *         Builder(const Builder&);
 *         Builder(Builder&&);
 *         ~Builder();
 *
 *         struct Stats {
 *             float  fTotalLoadTimeMS  = 0, // Total animation instantiation time.
 *                    fJsonParseTimeMS  = 0, // Time spent building a JSON DOM.
 *                    fSceneParseTimeMS = 0; // Time spent constructing the animation scene graph.
 *             size_t fJsonSize         = 0, // Input JSON size.
 *                    fAnimatorCount    = 0; // Number of dynamically animated properties.
 *         };
 *
 *         /**
 *          * Returns various animation build stats.
 *          *
 *          * @return Stats (see above).
 *          */
 *         const Stats& getStats() const { return fStats; }
 *
 *         /**
 *          * Specify a loader for external resources (images, etc.).
 *          */
 *         Builder& setResourceProvider(sk_sp<ResourceProvider>);
 *
 *         /**
 *          * Specify a font manager for loading animation fonts.
 *          */
 *         Builder& setFontManager(sk_sp<SkFontMgr>);
 *
 *         /**
 *          * Specify a PropertyObserver to receive callbacks during parsing.
 *          *
 *          * See SkottieProperty.h for more details.
 *          *
 *          */
 *         Builder& setPropertyObserver(sk_sp<PropertyObserver>);
 *
 *         /**
 *          * Register a Logger with this builder.
 *          */
 *         Builder& setLogger(sk_sp<Logger>);
 *
 *         /**
 *          * Register a MarkerObserver with this builder.
 *          */
 *         Builder& setMarkerObserver(sk_sp<MarkerObserver>);
 *
 *         /**
 *          * Register a precomp layer interceptor.
 *          * This allows substituting precomp layers with custom/externally managed content.
 *          */
 *         Builder& setPrecompInterceptor(sk_sp<PrecompInterceptor>);
 *
 *         /**
 *          * Registers an ExpressionManager to evaluate AE expressions.
 *          * If unspecified, expressions in the animation JSON will be ignored.
 *          */
 *         Builder& setExpressionManager(sk_sp<ExpressionManager>);
 *
 *         /**
 *          * Registers a factory to be used when shaping text.
 *          * If unspecified, text will be shaped with primitive shaping.
 *          * See //modules/skshaper/utils/FactoryHelpers.h
 *          */
 *         Builder& setTextShapingFactory(sk_sp<SkShapers::Factory>);
 *
 *         /**
 *          * Animation factories.
 *          */
 *         sk_sp<Animation> make(SkStream*);
 *         sk_sp<Animation> make(const char* data, size_t length);
 *         sk_sp<Animation> makeFromFile(const char path[]);
 *
 *         /**
 *          * Get handle for SlotManager after animation is built.
 *          */
 *         const sk_sp<SlotManager>& getSlotManager() const {return fSlotManager;}
 *
 *         SkSpan<const LayerInfo> getLayerInfo() const {return SkSpan(fLayerInfo);}
 *
 *     private:
 *         const uint32_t          fFlags;
 *
 *         sk_sp<ResourceProvider>   fResourceProvider;
 *         sk_sp<SkFontMgr>          fFontMgr;
 *         sk_sp<PropertyObserver>   fPropertyObserver;
 *         sk_sp<Logger>             fLogger;
 *         sk_sp<MarkerObserver  >   fMarkerObserver;
 *         sk_sp<PrecompInterceptor> fPrecompInterceptor;
 *         sk_sp<ExpressionManager>  fExpressionManager;
 *         sk_sp<SkShapers::Factory> fShapingFactory;
 *         sk_sp<SlotManager>        fSlotManager;
 *         Stats                     fStats;
 *         std::vector<LayerInfo>    fLayerInfo;
 *     };
 *
 *     /**
 *      * Animation factories.
 *      *
 *      * Use the Builder helper above for more options/control.
 *      */
 *     static sk_sp<Animation> Make(const char* data, size_t length);
 *     static sk_sp<Animation> Make(SkStream*);
 *     static sk_sp<Animation> MakeFromFile(const char path[]);
 *
 *     ~Animation();
 *
 *     enum RenderFlag : uint32_t {
 *         // When rendering into a known transparent buffer, clients can pass
 *         // this flag to avoid some unnecessary compositing overhead for
 *         // animations using layer blend modes.
 *         kSkipTopLevelIsolation   = 0x01,
 *         // By default, content is clipped to the intrinsic animation
 *         // bounds (as determined by its size).  If this flag is set,
 *         // then the animation can draw outside of the bounds.
 *         kDisableTopLevelClipping = 0x02,
 *     };
 *     using RenderFlags = uint32_t;
 *
 *     /**
 *      * Draws the current animation frame.
 *      *
 *      * It is undefined behavior to call render() on a newly created Animation
 *      * before specifying an initial frame via one of the seek() variants.
 *      *
 *      * @param canvas   destination canvas
 *      * @param dst      optional destination rect
 *      * @param flags    optional RenderFlags
 *      */
 *     void render(SkCanvas* canvas, const SkRect* dst = nullptr) const;
 *     void render(SkCanvas* canvas, const SkRect* dst, RenderFlags) const;
 *
 *     /**
 *      * [Deprecated: use one of the other versions.]
 *      *
 *      * Updates the animation state for |t|.
 *      *
 *      * @param t   normalized [0..1] frame selector (0 -> first frame, 1 -> final frame)
 *      * @param ic  optional invalidation controller (dirty region tracking)
 *      *
 *      */
 *     void seek(SkScalar t, sksg::InvalidationController* ic = nullptr) {
 *         this->seekFrameTime(t * this->duration(), ic);
 *     }
 *
 *     /**
 *      * Update the animation state to match |t|, specified as a frame index
 *      * i.e. relative to duration() * fps().
 *      *
 *      * Fractional values are allowed and meaningful - e.g.
 *      *
 *      *   0.0 -> first frame
 *      *   1.0 -> second frame
 *      *   0.5 -> halfway between first and second frame
 *      */
 *     void seekFrame(double t, sksg::InvalidationController* ic = nullptr);
 *
 *     /** Update the animation state to match t, specifed in frame time
 *      *  i.e. relative to duration().
 *      */
 *     void seekFrameTime(double t, sksg::InvalidationController* = nullptr);
 *
 *     /**
 *      * Returns the animation duration in seconds.
 *      */
 *     double duration() const { return fDuration; }
 *
 *     /**
 *      * Returns the animation frame rate (frames / second).
 *      */
 *     double fps() const { return fFPS; }
 *
 *     /**
 *      * Animation in point, in frame index units.
 *      */
 *     double inPoint()  const { return fInPoint;  }
 *
 *     /**
 *      * Animation out point, in frame index units.
 *      */
 *     double outPoint() const { return fOutPoint; }
 *
 *     const SkString& version() const { return fVersion; }
 *     const SkSize&      size() const { return fSize;    }
 *
 * private:
 *     enum Flags : uint32_t {
 *         kRequiresTopLevelIsolation = 1 << 0, // Needs to draw into a layer due to layer blending.
 *     };
 *
 *     Animation(sk_sp<sksg::RenderNode>,
 *               std::vector<sk_sp<internal::Animator>>&&,
 *               SkString ver, const SkSize& size,
 *               double inPoint, double outPoint, double duration, double fps, uint32_t flags);
 *
 *     const sk_sp<sksg::RenderNode>                fSceneRoot;
 *     const std::vector<sk_sp<internal::Animator>> fAnimators;
 *     const SkString                               fVersion;
 *     const SkSize                                 fSize;
 *     const double                                 fInPoint,
 *                                                  fOutPoint,
 *                                                  fDuration,
 *                                                  fFPS;
 *     const uint32_t                               fFlags;
 *
 *     using INHERITED = SkNVRefCnt<Animation>;
 * }
 * ```
 */
public open class Animation public constructor(
  sceneRoot: SkSp<RenderNode>,
  animators: List<SkSp<Animator>>,
  version: String,
  size: SkSize,
  inPoint: Double,
  outPoint: Double,
  duration: Double,
  fps: Double,
  flags: UInt,
) : SkNVRefCnt(),
    Animation {
  /**
   * C++ original:
   * ```cpp
   * Animation(sk_sp<sksg::RenderNode>,
   *               std::vector<sk_sp<internal::Animator>>&&,
   *               SkString ver, const SkSize& size,
   *               double inPoint, double outPoint, double duration, double fps, uint32_t flags)
   * ```
   */
  private var skSp: Animation = TODO("Initialize skSp")

  /**
   * C++ original:
   * ```cpp
   * const sk_sp<sksg::RenderNode>                fSceneRoot
   * ```
   */
  private val fSceneRoot: Int = TODO("Initialize fSceneRoot")

  /**
   * C++ original:
   * ```cpp
   * const SkString                               fVersion
   * ```
   */
  private val fVersion: Int = TODO("Initialize fVersion")

  /**
   * C++ original:
   * ```cpp
   * const SkSize                                 fSize
   * ```
   */
  private val fSize: Int = TODO("Initialize fSize")

  /**
   * C++ original:
   * ```cpp
   * const double                                 fInPoint
   * ```
   */
  private val fInPoint: Double = TODO("Initialize fInPoint")

  /**
   * C++ original:
   * ```cpp
   * const double                                 fInPoint,
   *                                                  fOutPoint
   * ```
   */
  private val fOutPoint: Double = TODO("Initialize fOutPoint")

  /**
   * C++ original:
   * ```cpp
   * const double                                 fInPoint,
   *                                                  fOutPoint,
   *                                                  fDuration
   * ```
   */
  private val fDuration: Double = TODO("Initialize fDuration")

  /**
   * C++ original:
   * ```cpp
   * const double                                 fInPoint,
   *                                                  fOutPoint,
   *                                                  fDuration,
   *                                                  fFPS
   * ```
   */
  private val fFPS: Double = TODO("Initialize fFPS")

  /**
   * C++ original:
   * ```cpp
   * const uint32_t                               fFlags
   * ```
   */
  private val fFlags: UInt = TODO("Initialize fFlags")

  /**
   * C++ original:
   * ```cpp
   * void Animation::render(SkCanvas* canvas, const SkRect* dstR) const {
   *     this->render(canvas, dstR, 0);
   * }
   * ```
   */
  public override fun render(canvas: SkCanvas?, dst: SkRect? = TODO()) {
    TODO("Implement render")
  }

  /**
   * C++ original:
   * ```cpp
   * void Animation::render(SkCanvas* canvas, const SkRect* dstR, RenderFlags renderFlags) const {
   *     TRACE_EVENT0("skottie", TRACE_FUNC);
   *
   *     if (!fSceneRoot)
   *         return;
   *
   *     SkAutoCanvasRestore restore(canvas, true);
   *
   *     const SkRect srcR = SkRect::MakeSize(this->size());
   *     if (dstR) {
   *         canvas->concat(SkMatrix::RectToRectOrIdentity(srcR, *dstR, SkMatrix::kCenter_ScaleToFit));
   *     }
   *
   *     if (!(renderFlags & RenderFlag::kDisableTopLevelClipping)) {
   *         canvas->clipRect(srcR);
   *     }
   *
   *     if ((fFlags & Flags::kRequiresTopLevelIsolation) &&
   *         !(renderFlags & RenderFlag::kSkipTopLevelIsolation)) {
   *         // The animation uses non-trivial blending, and needs
   *         // to be rendered into a separate/transparent layer.
   *         canvas->saveLayer(srcR, nullptr);
   *     }
   *
   *     fSceneRoot->render(canvas);
   * }
   * ```
   */
  public override fun render(
    canvas: SkCanvas?,
    dst: SkRect?,
    renderFlags: AnimationRenderFlags,
  ) {
    TODO("Implement render")
  }

  /**
   * C++ original:
   * ```cpp
   * void seek(SkScalar t, sksg::InvalidationController* ic = nullptr) {
   *         this->seekFrameTime(t * this->duration(), ic);
   *     }
   * ```
   */
  public override fun seek(t: SkScalar, ic: InvalidationController? = TODO()) {
    TODO("Implement seek")
  }

  /**
   * C++ original:
   * ```cpp
   * void Animation::seekFrame(double t, sksg::InvalidationController* ic) {
   *     TRACE_EVENT0("skottie", TRACE_FUNC);
   *
   *     if (!fSceneRoot)
   *         return;
   *
   *     // Per AE/Lottie semantics out_point is exclusive.
   *     const auto kLastValidFrame = std::nextafterf(fOutPoint, fInPoint),
   *                      comp_time = SkTPin<float>(fInPoint + t, fInPoint, kLastValidFrame);
   *
   *     for (const auto& anim : fAnimators) {
   *         anim->seek(comp_time);
   *     }
   *
   *     fSceneRoot->revalidate(ic, SkMatrix::I());
   * }
   * ```
   */
  public override fun seekFrame(t: Double, ic: InvalidationController? = TODO()) {
    TODO("Implement seekFrame")
  }

  /**
   * C++ original:
   * ```cpp
   * void Animation::seekFrameTime(double t, sksg::InvalidationController* ic) {
   *     this->seekFrame(t * fFPS, ic);
   * }
   * ```
   */
  public override fun seekFrameTime(t: Double, ic: InvalidationController? = TODO()) {
    TODO("Implement seekFrameTime")
  }

  /**
   * C++ original:
   * ```cpp
   * double duration() const { return fDuration; }
   * ```
   */
  public override fun duration(): Double {
    TODO("Implement duration")
  }

  /**
   * C++ original:
   * ```cpp
   * double fps() const { return fFPS; }
   * ```
   */
  public override fun fps(): Double {
    TODO("Implement fps")
  }

  /**
   * C++ original:
   * ```cpp
   * double inPoint()  const { return fInPoint;  }
   * ```
   */
  public override fun inPoint(): Double {
    TODO("Implement inPoint")
  }

  /**
   * C++ original:
   * ```cpp
   * double outPoint() const { return fOutPoint; }
   * ```
   */
  public override fun outPoint(): Double {
    TODO("Implement outPoint")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkString& version() const { return fVersion; }
   * ```
   */
  public override fun version(): Int {
    TODO("Implement version")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkSize&      size() const { return fSize;    }
   * ```
   */
  public override fun size(): Int {
    TODO("Implement size")
  }

  public class Builder public constructor(
    flags: UInt = TODO(),
  ) {
    private val fFlags: UInt = TODO("Initialize fFlags")

    private var fResourceProvider: Int = TODO("Initialize fResourceProvider")

    private var fFontMgr: Int = TODO("Initialize fFontMgr")

    private var fPropertyObserver: Int = TODO("Initialize fPropertyObserver")

    private var fLogger: Int = TODO("Initialize fLogger")

    private var fMarkerObserver: Int = TODO("Initialize fMarkerObserver")

    private var fPrecompInterceptor: Int = TODO("Initialize fPrecompInterceptor")

    private var fExpressionManager: Int = TODO("Initialize fExpressionManager")

    private var fShapingFactory: Int = TODO("Initialize fShapingFactory")

    private var fSlotManager: Int = TODO("Initialize fSlotManager")

    private var fStats: org.skia.tests.Builder.Stats = TODO("Initialize fStats")

    private var fLayerInfo: Int = TODO("Initialize fLayerInfo")

    public constructor(param0: org.skia.tests.Builder) : this() {
      TODO("Implement constructor")
    }

    public fun getStats(): org.skia.tests.Builder.Stats {
      TODO("Implement getStats")
    }

    public fun setResourceProvider(rp: SkSp<ResourceProvider>): org.skia.tests.Builder {
      TODO("Implement setResourceProvider")
    }

    public fun setFontManager(fmgr: SkSp<SkFontMgr>): org.skia.tests.Builder {
      TODO("Implement setFontManager")
    }

    public fun setPropertyObserver(pobserver: SkSp<PropertyObserver>): org.skia.tests.Builder {
      TODO("Implement setPropertyObserver")
    }

    public fun setLogger(logger: SkSp<Logger>): org.skia.tests.Builder {
      TODO("Implement setLogger")
    }

    public fun setMarkerObserver(mobserver: SkSp<MarkerObserver>): org.skia.tests.Builder {
      TODO("Implement setMarkerObserver")
    }

    public fun setPrecompInterceptor(pi: SkSp<PrecompInterceptor>): org.skia.tests.Builder {
      TODO("Implement setPrecompInterceptor")
    }

    public fun setExpressionManager(em: SkSp<ExpressionManager>): org.skia.tests.Builder {
      TODO("Implement setExpressionManager")
    }

    public fun setTextShapingFactory(factory: SkSp<Factory>): org.skia.tests.Builder {
      TODO("Implement setTextShapingFactory")
    }

    public fun make(stream: SkStream?): Int {
      TODO("Implement make")
    }

    public fun make(`data`: String?, length: ULong): Int {
      TODO("Implement make")
    }

    public fun makeFromFile(path: CharArray): Int {
      TODO("Implement makeFromFile")
    }

    public fun getSlotManager(): Int {
      TODO("Implement getSlotManager")
    }

    public fun getLayerInfo(): Int {
      TODO("Implement getLayerInfo")
    }

    public data class Stats public constructor(
      public var fTotalLoadTimeMS: Float,
      public var fJsonParseTimeMS: Float,
      public var fSceneParseTimeMS: Float,
      public var fJsonSize: ULong,
      public var fAnimatorCount: ULong,
    )

    public enum class Flags {
      kDeferImageLoading,
      kPreferEmbeddedFonts,
    }
  }

  public enum class RenderFlag {
    kSkipTopLevelIsolation,
    kDisableTopLevelClipping,
  }

  public enum class Flags {
    kRequiresTopLevelIsolation,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<Animation> Animation::Make(const char* data, size_t length) {
     *     return Builder().make(data, length);
     * }
     * ```
     */
    public override fun make(`data`: String?, length: ULong): Int {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<Animation> Animation::Make(SkStream* stream) {
     *     return Builder().make(stream);
     * }
     * ```
     */
    public override fun make(stream: SkStream?): Int {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<Animation> Animation::MakeFromFile(const char path[]) {
     *     return Builder().makeFromFile(path);
     * }
     * ```
     */
    public override fun makeFromFile(path: CharArray): Int {
      TODO("Implement makeFromFile")
    }
  }
}
