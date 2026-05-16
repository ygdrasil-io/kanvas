package org.skia.gpu

import kotlin.Int
import org.skia.core.SkEnumBitMask
import org.skia.effects.SkRuntimeEffect
import org.skia.foundation.SkColorInfo
import org.skia.foundation.SkSp
import org.skia.math.SkM44
import undefined.SkColor4f

/**
 * C++ original:
 * ```cpp
 * class KeyContext {
 * public:
 *     // Constructor for the pre-compile code path (i.e., no Recorder)
 *     KeyContext(const Caps*,
 *                FloatStorageManager*,
 *                PaintParamsKeyBuilder*,
 *                PipelineDataGatherer*,
 *                ShaderCodeDictionary*,
 *                sk_sp<RuntimeEffectDictionary>,
 *                const SkColorInfo& dstColorInfo);
 *
 *     // Constructor for the ExtractPaintData code path (i.e., with a Recorder)
 *     KeyContext(Recorder*,
 *                DrawContext*,
 *                FloatStorageManager*,
 *                PaintParamsKeyBuilder*,
 *                PipelineDataGatherer*,
 *                const SkM44& local2Dev,
 *                const SkColorInfo& dstColorInfo,
 *                SkEnumBitMask<KeyGenFlags> initialFlags,
 *                const SkColor4f& paintColor);
 *
 *     KeyContext(const KeyContext&, SkEnumBitMask<KeyGenFlags> xtraFlags=KeyGenFlags::kDefault);
 *     ~KeyContext();
 *
 *     // Create scoped KeyContexts that allow child effects to be processed differently.
 *     KeyContext withColorInfo(const SkColorInfo& info) const {
 *         KeyContext o = *this;
 *         o.fDstColorInfo = info;
 *
 *         // We want to keep fPaintColor's alpha value but replace the RGB with values in the new
 *         // color space. By overriding the alpha type of the old and new dst color infos to be
 *         // kOpaque, SkColorSpaceXformSteps will leave the alpha channel alone.
 *         SkColorSpaceXformSteps(fDstColorInfo.colorSpace(), kOpaque_SkAlphaType,
 *                                info.colorSpace(),          kOpaque_SkAlphaType)
 *                 .apply(o.fPaintColor.vec());
 *         SkASSERT(o.fPaintColor.fA == fPaintColor.fA);
 *         return o;
 *     }
 *
 *     // The key generation flags vary in the scope of a SkRuntimeEffect per child based on how the
 *     // RuntimeEffect's SkSL invokes each child.
 *     KeyContext forRuntimeEffect(const SkRuntimeEffect* effect, int child) const;
 *
 *     KeyContext withExtraFlags(SkEnumBitMask<KeyGenFlags> flags) const {
 *         return KeyContext(*this, flags);
 *     }
 *
 *     Recorder* recorder() const { return fRecorder; }
 *     DrawContext* drawContext() const { return fDC; }
 *
 *     const Caps* caps() const { return fCaps; }
 *
 *     const SkM44& local2Dev() const { return fLocal2Dev; }
 *     const SkMatrix* localMatrix() const { return fLocalMatrix; }
 *
 *     FloatStorageManager* floatStorageManager() const { return fFloatStorageManager; }
 *     PaintParamsKeyBuilder* paintParamsKeyBuilder() const { return fPaintParamsKeyBuilder; }
 *     PipelineDataGatherer* pipelineDataGatherer() const { return fPipelineDataGatherer; }
 *     ShaderCodeDictionary* dict() const { return fDictionary; }
 *
 *     sk_sp<RuntimeEffectDictionary> rtEffectDict() const;
 *
 *     const SkColorInfo& dstColorInfo() const { return fDstColorInfo; }
 *
 *     const SkPMColor4f& paintColor() const { return fPaintColor; }
 *
 *     SkEnumBitMask<KeyGenFlags> flags() const { return fKeyGenFlags; }
 *
 * private:
 *     // Fields which will not change over the course of building a paint key
 *     const Caps* fCaps;
 *     Recorder* fRecorder;
 *     DrawContext* fDC;
 *     FloatStorageManager* fFloatStorageManager;
 *     PaintParamsKeyBuilder* fPaintParamsKeyBuilder;
 *     PipelineDataGatherer* fPipelineDataGatherer;
 *     ShaderCodeDictionary* fDictionary;
 *     sk_sp<RuntimeEffectDictionary> fRTEffectDict;
 *     SkM44 fLocal2Dev;
 *
 * protected:
 *     // Fields that can be modified while walking a paint's effects for a key
 *     SkMatrix* fLocalMatrix = nullptr;
 *     SkColorInfo fDstColorInfo;
 *     // Although stored as premul the paint color is actually comprised of an opaque RGB portion
 *     // and a separate alpha portion. The two portions will never be used together but are stored
 *     // together to reduce the number of uniforms.
 *     SkPMColor4f fPaintColor = SK_PMColor4fBLACK;
 *     SkEnumBitMask<KeyGenFlags> fKeyGenFlags = KeyGenFlags::kDefault;
 * }
 * ```
 */
public open class KeyContext public constructor(
  param0: Caps,
  param1: FloatStorageManager,
  param2: PaintParamsKeyBuilder,
  param3: PipelineDataGatherer,
  param4: ShaderCodeDictionary,
  param5: SkSp<RuntimeEffectDictionary>,
  dstColorInfo: SkColorInfo,
) {
  /**
   * C++ original:
   * ```cpp
   * const Caps* fCaps
   * ```
   */
  private val fCaps: Caps? = TODO("Initialize fCaps")

  /**
   * C++ original:
   * ```cpp
   * Recorder* fRecorder
   * ```
   */
  private var fRecorder: Recorder? = TODO("Initialize fRecorder")

  /**
   * C++ original:
   * ```cpp
   * DrawContext* fDC
   * ```
   */
  private var fDC: DrawContext? = TODO("Initialize fDC")

  /**
   * C++ original:
   * ```cpp
   * FloatStorageManager* fFloatStorageManager
   * ```
   */
  private var fFloatStorageManager: FloatStorageManager? = TODO("Initialize fFloatStorageManager")

  /**
   * C++ original:
   * ```cpp
   * PaintParamsKeyBuilder* fPaintParamsKeyBuilder
   * ```
   */
  private var fPaintParamsKeyBuilder: PaintParamsKeyBuilder? =
      TODO("Initialize fPaintParamsKeyBuilder")

  /**
   * C++ original:
   * ```cpp
   * PipelineDataGatherer* fPipelineDataGatherer
   * ```
   */
  private var fPipelineDataGatherer: PipelineDataGatherer? =
      TODO("Initialize fPipelineDataGatherer")

  /**
   * C++ original:
   * ```cpp
   * ShaderCodeDictionary* fDictionary
   * ```
   */
  private var fDictionary: ShaderCodeDictionary? = TODO("Initialize fDictionary")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<RuntimeEffectDictionary> fRTEffectDict
   * ```
   */
  private var fRTEffectDict: Int = TODO("Initialize fRTEffectDict")

  /**
   * C++ original:
   * ```cpp
   * SkM44 fLocal2Dev
   * ```
   */
  private var fLocal2Dev: Int = TODO("Initialize fLocal2Dev")

  /**
   * C++ original:
   * ```cpp
   * SkMatrix* fLocalMatrix
   * ```
   */
  protected var fLocalMatrix: Int? = TODO("Initialize fLocalMatrix")

  /**
   * C++ original:
   * ```cpp
   * SkColorInfo fDstColorInfo
   * ```
   */
  protected var fDstColorInfo: Int = TODO("Initialize fDstColorInfo")

  /**
   * C++ original:
   * ```cpp
   * SkPMColor4f fPaintColor
   * ```
   */
  protected var fPaintColor: Int = TODO("Initialize fPaintColor")

  /**
   * C++ original:
   * ```cpp
   * SkEnumBitMask<KeyGenFlags> fKeyGenFlags
   * ```
   */
  protected var fKeyGenFlags: Int = TODO("Initialize fKeyGenFlags")

  /**
   * C++ original:
   * ```cpp
   * KeyContext(const Caps*,
   *                FloatStorageManager*,
   *                PaintParamsKeyBuilder*,
   *                PipelineDataGatherer*,
   *                ShaderCodeDictionary*,
   *                sk_sp<RuntimeEffectDictionary>,
   *                const SkColorInfo& dstColorInfo)
   * ```
   */
  public constructor(
    param0: Recorder,
    param1: DrawContext,
    param2: FloatStorageManager,
    param3: PaintParamsKeyBuilder,
    param4: PipelineDataGatherer,
    local2Dev: SkM44,
    dstColorInfo: SkColorInfo,
    initialFlags: SkEnumBitMask<KeyGenFlags>,
    paintColor: SkColor4f,
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * KeyContext(Recorder*,
   *                DrawContext*,
   *                FloatStorageManager*,
   *                PaintParamsKeyBuilder*,
   *                PipelineDataGatherer*,
   *                const SkM44& local2Dev,
   *                const SkColorInfo& dstColorInfo,
   *                SkEnumBitMask<KeyGenFlags> initialFlags,
   *                const SkColor4f& paintColor)
   * ```
   */
  public constructor(other: KeyContext, xtraFlags: SkEnumBitMask<KeyGenFlags>) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * KeyContext(const KeyContext&, SkEnumBitMask<KeyGenFlags> xtraFlags=KeyGenFlags::kDefault)
   * ```
   */
  public constructor(
    caps: Caps?,
    floatStorageManager: FloatStorageManager?,
    paintParamsKeyBuilder: PaintParamsKeyBuilder?,
    pipelineDataGatherer: PipelineDataGatherer?,
    dict: ShaderCodeDictionary?,
    rtEffectDict: SkSp<RuntimeEffectDictionary>,
    dstColorInfo: SkColorInfo,
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * KeyContext::KeyContext(const Caps* caps,
   *                        FloatStorageManager* floatStorageManager,
   *                        PaintParamsKeyBuilder* paintParamsKeyBuilder,
   *                        PipelineDataGatherer* pipelineDataGatherer,
   *                        ShaderCodeDictionary* dict,
   *                        sk_sp<RuntimeEffectDictionary> rtEffectDict,
   *                        const SkColorInfo& dstColorInfo)
   *         : fCaps(caps)
   *         , fRecorder(nullptr)
   *         , fFloatStorageManager(floatStorageManager)
   *         , fPaintParamsKeyBuilder(paintParamsKeyBuilder)
   *         , fPipelineDataGatherer(pipelineDataGatherer)
   *         , fDictionary(dict)
   *         , fRTEffectDict(std::move(rtEffectDict))
   *         , fDstColorInfo(dstColorInfo) {}
   * ```
   */
  public constructor(
    recorder: Recorder?,
    drawContext: DrawContext?,
    floatStorageManager: FloatStorageManager?,
    paintParamsKeyBuilder: PaintParamsKeyBuilder?,
    pipelineDataGatherer: PipelineDataGatherer?,
    local2Dev: SkM44,
    dstColorInfo: SkColorInfo,
    initialFlags: SkEnumBitMask<KeyGenFlags>,
    paintColor: SkColor4f,
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * KeyContext withColorInfo(const SkColorInfo& info) const {
   *         KeyContext o = *this;
   *         o.fDstColorInfo = info;
   *
   *         // We want to keep fPaintColor's alpha value but replace the RGB with values in the new
   *         // color space. By overriding the alpha type of the old and new dst color infos to be
   *         // kOpaque, SkColorSpaceXformSteps will leave the alpha channel alone.
   *         SkColorSpaceXformSteps(fDstColorInfo.colorSpace(), kOpaque_SkAlphaType,
   *                                info.colorSpace(),          kOpaque_SkAlphaType)
   *                 .apply(o.fPaintColor.vec());
   *         SkASSERT(o.fPaintColor.fA == fPaintColor.fA);
   *         return o;
   *     }
   * ```
   */
  public fun withColorInfo(info: SkColorInfo): KeyContext {
    TODO("Implement withColorInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * KeyContext KeyContext::forRuntimeEffect(const SkRuntimeEffect* effect, int child) const {
   *     // Runtime effects always disable paint-color colorization of alpha-only image shaders
   *     SkEnumBitMask<KeyGenFlags> xtraFlags = KeyGenFlags::kDisableAlphaOnlyImageColorization;
   *
   *     if (SkRuntimeEffectPriv::ChildSampleUsage(effect, child).isExplicit()) {
   *         // Assume explicit sampling as a proxy for either a likely data lookup (e.g. raw shader)
   *         // or an effect that might sample the child many times. This means it's worth using
   *         // eliding colorspace conversions, and we have to disable sampling optimization.
   *         xtraFlags |= KeyGenFlags::kEnableIdentityColorSpaceXform |
   *                      KeyGenFlags::kDisableSamplingOptimization;
   *     }
   *
   *     return this->withExtraFlags(xtraFlags);
   * }
   * ```
   */
  public fun forRuntimeEffect(effect: SkRuntimeEffect?, child: Int): KeyContext {
    TODO("Implement forRuntimeEffect")
  }

  /**
   * C++ original:
   * ```cpp
   * KeyContext withExtraFlags(SkEnumBitMask<KeyGenFlags> flags) const {
   *         return KeyContext(*this, flags);
   *     }
   * ```
   */
  public fun withExtraFlags(flags: SkEnumBitMask<KeyGenFlags>): KeyContext {
    TODO("Implement withExtraFlags")
  }

  /**
   * C++ original:
   * ```cpp
   * Recorder* recorder() const { return fRecorder; }
   * ```
   */
  public fun recorder(): Recorder {
    TODO("Implement recorder")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawContext* drawContext() const { return fDC; }
   * ```
   */
  public fun drawContext(): DrawContext {
    TODO("Implement drawContext")
  }

  /**
   * C++ original:
   * ```cpp
   * const Caps* caps() const { return fCaps; }
   * ```
   */
  public fun caps(): Caps {
    TODO("Implement caps")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkM44& local2Dev() const { return fLocal2Dev; }
   * ```
   */
  public fun local2Dev(): Int {
    TODO("Implement local2Dev")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkMatrix* localMatrix() const { return fLocalMatrix; }
   * ```
   */
  public fun localMatrix(): Int {
    TODO("Implement localMatrix")
  }

  /**
   * C++ original:
   * ```cpp
   * FloatStorageManager* floatStorageManager() const { return fFloatStorageManager; }
   * ```
   */
  public fun floatStorageManager(): FloatStorageManager {
    TODO("Implement floatStorageManager")
  }

  /**
   * C++ original:
   * ```cpp
   * PaintParamsKeyBuilder* paintParamsKeyBuilder() const { return fPaintParamsKeyBuilder; }
   * ```
   */
  public fun paintParamsKeyBuilder(): PaintParamsKeyBuilder {
    TODO("Implement paintParamsKeyBuilder")
  }

  /**
   * C++ original:
   * ```cpp
   * PipelineDataGatherer* pipelineDataGatherer() const { return fPipelineDataGatherer; }
   * ```
   */
  public fun pipelineDataGatherer(): PipelineDataGatherer {
    TODO("Implement pipelineDataGatherer")
  }

  /**
   * C++ original:
   * ```cpp
   * ShaderCodeDictionary* dict() const { return fDictionary; }
   * ```
   */
  public fun dict(): ShaderCodeDictionary {
    TODO("Implement dict")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<RuntimeEffectDictionary> KeyContext::rtEffectDict() const { return fRTEffectDict; }
   * ```
   */
  public fun rtEffectDict(): Int {
    TODO("Implement rtEffectDict")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkColorInfo& dstColorInfo() const { return fDstColorInfo; }
   * ```
   */
  public fun dstColorInfo(): Int {
    TODO("Implement dstColorInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkPMColor4f& paintColor() const { return fPaintColor; }
   * ```
   */
  public fun paintColor(): Int {
    TODO("Implement paintColor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkEnumBitMask<KeyGenFlags> flags() const { return fKeyGenFlags; }
   * ```
   */
  public fun flags(): Int {
    TODO("Implement flags")
  }
}
