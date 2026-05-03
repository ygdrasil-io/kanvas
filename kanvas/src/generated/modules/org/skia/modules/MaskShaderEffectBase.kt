package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkSp
import org.skia.math.SkSize

public typealias LinearWipeAdapterINHERITED = MaskShaderEffectBase

public typealias VenetianBlindsAdapterINHERITED = MaskShaderEffectBase

/**
 * C++ original:
 * ```cpp
 * class MaskShaderEffectBase : public AnimatablePropertyContainer {
 * public:
 *     const sk_sp<sksg::MaskShaderEffect>& node() const { return fMaskEffectNode; }
 *
 * protected:
 *     MaskShaderEffectBase(sk_sp<sksg::RenderNode>, const SkSize&);
 *
 *     const SkSize& layerSize() const { return  fLayerSize; }
 *
 * struct MaskInfo {
 *         sk_sp<SkShader> fMaskShader;
 *         bool            fVisible;
 *     };
 *     virtual MaskInfo onMakeMask() const = 0;
 *
 * private:
 *     void onSync() final;
 *
 *     const sk_sp<sksg::MaskShaderEffect> fMaskEffectNode;
 *     const SkSize                        fLayerSize;
 * }
 * ```
 */
public abstract class MaskShaderEffectBase public constructor(
  child: SkSp<RenderNode>,
  ls: SkSize,
) : AnimatablePropertyContainer() {
  /**
   * C++ original:
   * ```cpp
   * const sk_sp<sksg::MaskShaderEffect> fMaskEffectNode
   * ```
   */
  private val fMaskEffectNode: Int = TODO("Initialize fMaskEffectNode")

  /**
   * C++ original:
   * ```cpp
   * const SkSize                        fLayerSize
   * ```
   */
  private val fLayerSize: Int = TODO("Initialize fLayerSize")

  /**
   * C++ original:
   * ```cpp
   * const sk_sp<sksg::MaskShaderEffect>& node() const { return fMaskEffectNode; }
   * ```
   */
  public fun node(): Int {
    TODO("Implement node")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkSize& layerSize() const { return  fLayerSize; }
   * ```
   */
  protected fun layerSize(): Int {
    TODO("Implement layerSize")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual MaskInfo onMakeMask() const = 0
   * ```
   */
  protected abstract fun onMakeMask(): MaskInfo

  /**
   * C++ original:
   * ```cpp
   * void MaskShaderEffectBase::onSync() {
   *     const auto minfo = this->onMakeMask();
   *
   *     fMaskEffectNode->setVisible(minfo.fVisible);
   *     fMaskEffectNode->setShader(std::move(minfo.fMaskShader));
   * }
   * ```
   */
  public override fun onSync() {
    TODO("Implement onSync")
  }

  public data class MaskInfo public constructor(
    public var fMaskShader: Int,
    public var fVisible: Boolean,
  )
}
