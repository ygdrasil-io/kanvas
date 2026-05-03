package org.skia.modules

import AdapterT
import Args
import kotlin.Int

public typealias PathAdapterINHERITED = DiscardableAdapterBase<PathAdapter, Path>

public typealias OpacityAdapterINHERITED = DiscardableAdapterBase<OpacityAdapter, OpacityEffect>

public typealias BlackAndWhiteAdapterINHERITED = DiscardableAdapterBase<BlackAndWhiteAdapter, ExternalColorFilter>

public typealias BrightnessContrastAdapterINHERITED = DiscardableAdapterBase<BrightnessContrastAdapter, ExternalColorFilter>

public typealias BulgeEffectAdapterINHERITED = DiscardableAdapterBase<BulgeEffectAdapter, BulgeNode>

public typealias CCTonerAdapterINHERITED = DiscardableAdapterBase<CCTonerAdapter, GradientColorFilter>

public typealias DirectionalBlurAdapterINHERITED = DiscardableAdapterBase<DirectionalBlurAdapter, ExternalImageFilter>

public typealias DisplacementMapAdapterINHERITED = DiscardableAdapterBase<DisplacementMapAdapter, DisplacementNode>

public typealias FractalNoiseAdapterINHERITED = DiscardableAdapterBase<FractalNoiseAdapter, FractalNoiseNode>

public typealias GlowAdapterINHERITED = DiscardableAdapterBase<GlowAdapter, ExternalImageFilter>

public typealias EasyLevelsEffectAdapterINHERITED = DiscardableAdapterBase<EasyLevelsEffectAdapter, ExternalColorFilter>

public typealias ProLevelsEffectAdapterINHERITED = DiscardableAdapterBase<ProLevelsEffectAdapter, ExternalColorFilter>

public typealias MotionTileAdapterINHERITED = DiscardableAdapterBase<MotionTileAdapter, TileRenderNode>

public typealias ShadowAdapterINHERITED = DiscardableAdapterBase<ShadowAdapter, ExternalImageFilter>

public typealias RadialWipeAdapterINHERITED = DiscardableAdapterBase<RadialWipeAdapter, RWipeRenderNode>

public typealias SharpenAdapterINHERITED = DiscardableAdapterBase<SharpenAdapter, ExternalImageFilter>

public typealias SphereAdapterINHERITED = DiscardableAdapterBase<SphereAdapter, SphereNode>

public typealias ThresholdAdapterINHERITED = DiscardableAdapterBase<ThresholdAdapter, ExternalColorFilter>

public typealias TransformEffectAdapterINHERITED = DiscardableAdapterBase<TransformEffectAdapter, OpacityEffect>

public typealias FillStrokeAdapterINHERITED = DiscardableAdapterBase<FillStrokeAdapter, PaintNode>

public typealias DashAdapterINHERITED = DiscardableAdapterBase<DashAdapter, DashEffect>

public typealias OffsetPathsAdapterINHERITED = DiscardableAdapterBase<OffsetPathsAdapter, OffsetEffect>

public typealias PuckerBloatAdapterINHERITED = DiscardableAdapterBase<PuckerBloatAdapter, PuckerBloatEffect>

public typealias RepeaterAdapterINHERITED = DiscardableAdapterBase<RepeaterAdapter, RepeaterRenderNode>

public typealias RoundCornersAdapterINHERITED = DiscardableAdapterBase<RoundCornersAdapter, RoundEffect>

public typealias TrimEffectAdapterINHERITED = DiscardableAdapterBase<TrimEffectAdapter, TrimEffect>

/**
 * C++ original:
 * ```cpp
 * template <typename AdapterT, typename T>
 * class DiscardableAdapterBase : public AnimatablePropertyContainer {
 * public:
 *     template <typename... Args>
 *     static sk_sp<AdapterT> Make(Args&&... args) {
 *         sk_sp<AdapterT> adapter(new AdapterT(std::forward<Args>(args)...));
 *         adapter->shrink_to_fit();
 *         return adapter;
 *     }
 *
 *     const sk_sp<T>& node() const { return fNode; }
 *
 * protected:
 *     DiscardableAdapterBase()
 *         : fNode(T::Make()) {}
 *
 *     explicit DiscardableAdapterBase(sk_sp<T> node)
 *         : fNode(std::move(node)) {}
 *
 * private:
 *     const sk_sp<T> fNode;
 * }
 * ```
 */
public open class DiscardableAdapterBase<AdapterT, T> public constructor() : AnimatablePropertyContainer() {
  /**
   * C++ original:
   * ```cpp
   * explicit DiscardableAdapterBase(sk_sp<T> node)
   * ```
   */
  protected var skSp: DiscardableAdapterBase<AdapterT, T> = TODO("Initialize skSp")

  /**
   * C++ original:
   * ```cpp
   * const sk_sp<T>& node() const { return fNode; }
   * ```
   */
  public fun node(): Int {
    TODO("Implement node")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     *     template <typename... Args>
     *     static sk_sp<AdapterT> Make(Args&&... args) {
     *         sk_sp<AdapterT> adapter(new AdapterT(std::forward<Args>(args)...));
     *         adapter->shrink_to_fit();
     *         return adapter;
     *     }
     * ```
     */
    public fun <Args> make(args: Args): Int {
      TODO("Implement make")
    }
  }
}
