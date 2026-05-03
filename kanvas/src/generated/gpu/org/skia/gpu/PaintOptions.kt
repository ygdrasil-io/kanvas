package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan

/**
 * C++ original:
 * ```cpp
 * class SK_API PaintOptions {
 * public:
 *     /** Constructs a PaintOptions object with default values. It is equivalent to a default
 *      *  initialized SkPaint.
 *
 *         @return  default initialized PaintOptions
 *     */
 *     PaintOptions();
 *     PaintOptions(const PaintOptions&);
 *     ~PaintOptions();
 *     PaintOptions& operator=(const PaintOptions&);
 *
 *     /** Sets the shader options used when generating precompilation combinations.
 *
 *         This corresponds to SkPaint's setShader() method
 *
 *         @param shaders  The options used for shading when generating precompilation combinations.
 *     */
 *     void setShaders(SkSpan<const sk_sp<PrecompileShader>> shaders);
 *     SkSpan<const sk_sp<PrecompileShader>> getShaders() const {
 *         return SkSpan<const sk_sp<PrecompileShader>>(fShaderOptions);
 *     }
 *
 *     /** Sets the image filter options used when generating precompilation combinations.
 *
 *         This corresponds to SkPaint's setImageFilter() method
 *
 *         @param imageFilters  The options used for image filtering when generating precompilation
 *                              combinations.
 *     */
 *     void setImageFilters(SkSpan<const sk_sp<PrecompileImageFilter>> imageFilters);
 *     SkSpan<const sk_sp<PrecompileImageFilter>> getImageFilters() const {
 *         return SkSpan<const sk_sp<PrecompileImageFilter>>(fImageFilterOptions);
 *     }
 *
 *     /** Sets the mask filter options used when generating precompilation combinations.
 *
 *         This corresponds to SkPaint's setMaskFilter() method
 *
 *         @param maskFilters  The options used for mask filtering when generating precompilation
 *                             combinations.
 *     */
 *     void setMaskFilters(SkSpan<const sk_sp<PrecompileMaskFilter>> maskFilters);
 *     SkSpan<const sk_sp<PrecompileMaskFilter>> getMaskFilters() const {
 *         return SkSpan<const sk_sp<PrecompileMaskFilter>>(fMaskFilterOptions);
 *     }
 *
 *     /** Sets the color filter options used when generating precompilation combinations.
 *
 *         This corresponds to SkPaint's setColorFilter() method
 *
 *         @param colorFilters  The options used for color filtering when generating precompilation
 *                              combinations.
 *     */
 *     void setColorFilters(SkSpan<const sk_sp<PrecompileColorFilter>> colorFilters);
 *     SkSpan<const sk_sp<PrecompileColorFilter>> getColorFilters() const {
 *         return SkSpan<const sk_sp<PrecompileColorFilter>>(fColorFilterOptions);
 *     }
 *
 *     /** Sets the blend mode options used when generating precompilation combinations.
 *
 *         This corresponds to SkPaint's setBlendMode() method
 *
 *         @param blendModes  The options used for blending when generating precompilation
 *                            combinations.
 *     */
 *     void setBlendModes(SkSpan<const SkBlendMode> blendModes);
 *     SkSpan<const SkBlendMode> getBlendModes() const {
 *         return SkSpan<const SkBlendMode>(fBlendModeOptions.data(), fBlendModeOptions.size());
 *     }
 *     void addBlendMode(SkBlendMode bm) {
 *         fBlendModeOptions.push_back(bm);
 *     }
 *
 *     /** Sets the blender options used when generating precompilation combinations.
 *
 *         This corresponds to SkPaint's setBlender() method
 *
 *         @param blenders  The options used for blending when generating precompilation combinations.
 *     */
 *     void setBlenders(SkSpan<const sk_sp<PrecompileBlender>> blenders);
 *     SkSpan<const sk_sp<PrecompileBlender>> getBlenders() const {
 *         return SkSpan<const sk_sp<PrecompileBlender>>(fBlenderOptions);
 *     }
 *
 *     /** Sets the dither setting used when generating precompilation combinations
 *
 *         This corresponds to SkPaint's setDither() method
 *
 *         @param dither  the dither setting used when generating precompilation combinations.
 *     */
 *     void setDither(bool dither) { fDither = dither; }
 *     bool isDither() const { return fDither; }
 *
 *     void setPaintColorIsOpaque(bool paintColorIsOpaque) {
 *         fPaintColorIsOpaque = paintColorIsOpaque;
 *     }
 *     bool isPaintColorOpaque() const { return fPaintColorIsOpaque; }
 *
 *     // Provides access to functions that aren't part of the public API.
 *     PaintOptionsPriv priv();
 *     const PaintOptionsPriv priv() const;  // NOLINT(readability-const-return-type)
 *
 * private:
 *     friend class PaintOptionsPriv;
 *     friend class PrecompileImageFilter; // for ProcessCombination access
 *     friend class PrecompileMaskFilter;  // for ProcessCombination access
 *
 *     void addColorFilter(sk_sp<PrecompileColorFilter> cf);
 *
 *     void setClipShaders(SkSpan<const sk_sp<PrecompileShader>> clipShaders);
 *
 *     // In the main API this is specified via the SkBlender parameter to drawVertices
 *     void setPrimitiveBlendMode(SkBlendMode bm) { fPrimitiveBlendMode = bm; }
 *     void setSkipColorXform(bool skipColorXform) { fSkipColorXform = skipColorXform; }
 *
 *     int numShaderCombinations() const;
 *     int numColorFilterCombinations() const;
 *     int numBlendCombinations() const;
 *     int numClipShaderCombinations() const;
 *
 *     int numCombinations() const;
 *     // 'desiredCombination' must be less than the result of the numCombinations call
 *     void createKey(const KeyContext&,
 *                    TextureFormat,
 *                    int desiredCombination,
 *                    bool addPrimitiveBlender,
 *                    bool addAnalyticClip,
 *                    Coverage coverage) const;
 *
 *     typedef std::function<void(UniquePaintParamsID id,
 *                                DrawTypeFlags,
 *                                bool withPrimitiveBlender,
 *                                Coverage,
 *                                const RenderPassDesc&)> ProcessCombination;
 *
 *     void buildCombinations(const KeyContext&,
 *                            DrawTypeFlags,
 *                            bool addPrimitiveBlender,
 *                            Coverage,
 *                            const RenderPassDesc&,
 *                            const ProcessCombination&) const;
 *
 *     skia_private::TArray<sk_sp<PrecompileShader>> fShaderOptions;
 *     skia_private::TArray<sk_sp<PrecompileColorFilter>> fColorFilterOptions;
 *     skia_private::TArray<SkBlendMode> fBlendModeOptions;
 *     skia_private::TArray<sk_sp<PrecompileBlender>> fBlenderOptions;
 *     skia_private::TArray<sk_sp<PrecompileShader>> fClipShaderOptions;
 *
 *     skia_private::TArray<sk_sp<PrecompileImageFilter>> fImageFilterOptions;
 *     skia_private::TArray<sk_sp<PrecompileMaskFilter>> fMaskFilterOptions;
 *
 *     SkBlendMode fPrimitiveBlendMode = SkBlendMode::kSrcOver;
 *     bool fSkipColorXform = false;
 *     bool fDither = false;
 *     bool fPaintColorIsOpaque = true;
 * }
 * ```
 */
public data class PaintOptions public constructor(
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<sk_sp<PrecompileShader>> fShaderOptions
   * ```
   */
  private var fShaderOptions: Int,
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<sk_sp<PrecompileColorFilter>> fColorFilterOptions
   * ```
   */
  private var fColorFilterOptions: Int,
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<SkBlendMode> fBlendModeOptions
   * ```
   */
  private var fBlendModeOptions: Int,
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<sk_sp<PrecompileBlender>> fBlenderOptions
   * ```
   */
  private var fBlenderOptions: Int,
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<sk_sp<PrecompileShader>> fClipShaderOptions
   * ```
   */
  private var fClipShaderOptions: Int,
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<sk_sp<PrecompileImageFilter>> fImageFilterOptions
   * ```
   */
  private var fImageFilterOptions: Int,
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<sk_sp<PrecompileMaskFilter>> fMaskFilterOptions
   * ```
   */
  private var fMaskFilterOptions: Int,
  /**
   * C++ original:
   * ```cpp
   * SkBlendMode fPrimitiveBlendMode
   * ```
   */
  private var fPrimitiveBlendMode: Int,
  /**
   * C++ original:
   * ```cpp
   * bool fSkipColorXform = false
   * ```
   */
  private var fSkipColorXform: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool fDither = false
   * ```
   */
  private var fDither: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool fPaintColorIsOpaque = true
   * ```
   */
  private var fPaintColorIsOpaque: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * PaintOptions& PaintOptions::operator=(const PaintOptions&)
   * ```
   */
  public fun assign(param0: PaintOptions) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * void PaintOptions::setShaders(SkSpan<const sk_sp<PrecompileShader>> shaders) {
   *     fShaderOptions.clear();
   *     fShaderOptions.push_back_n(shaders.size(), shaders.data());
   * }
   * ```
   */
  public fun setShaders(shaders: SkSpan<SkSp<PrecompileShader>>) {
    TODO("Implement setShaders")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const sk_sp<PrecompileShader>> getShaders() const {
   *         return SkSpan<const sk_sp<PrecompileShader>>(fShaderOptions);
   *     }
   * ```
   */
  public fun getShaders(): Int {
    TODO("Implement getShaders")
  }

  /**
   * C++ original:
   * ```cpp
   * void PaintOptions::setImageFilters(SkSpan<const sk_sp<PrecompileImageFilter>> imageFilters) {
   *     fImageFilterOptions.clear();
   *     fImageFilterOptions.push_back_n(imageFilters.size(), imageFilters.data());
   * }
   * ```
   */
  public fun setImageFilters(imageFilters: SkSpan<SkSp<PrecompileImageFilter>>) {
    TODO("Implement setImageFilters")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const sk_sp<PrecompileImageFilter>> getImageFilters() const {
   *         return SkSpan<const sk_sp<PrecompileImageFilter>>(fImageFilterOptions);
   *     }
   * ```
   */
  public fun getImageFilters(): Int {
    TODO("Implement getImageFilters")
  }

  /**
   * C++ original:
   * ```cpp
   * void PaintOptions::setMaskFilters(SkSpan<const sk_sp<PrecompileMaskFilter>> maskFilters) {
   *     fMaskFilterOptions.clear();
   *     fMaskFilterOptions.push_back_n(maskFilters.size(), maskFilters.data());
   * }
   * ```
   */
  public fun setMaskFilters(maskFilters: SkSpan<SkSp<PrecompileMaskFilter>>) {
    TODO("Implement setMaskFilters")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const sk_sp<PrecompileMaskFilter>> getMaskFilters() const {
   *         return SkSpan<const sk_sp<PrecompileMaskFilter>>(fMaskFilterOptions);
   *     }
   * ```
   */
  public fun getMaskFilters(): Int {
    TODO("Implement getMaskFilters")
  }

  /**
   * C++ original:
   * ```cpp
   * void PaintOptions::setColorFilters(SkSpan<const sk_sp<PrecompileColorFilter>> colorFilters) {
   *     fColorFilterOptions.clear();
   *     fColorFilterOptions.push_back_n(colorFilters.size(), colorFilters.data());
   * }
   * ```
   */
  public fun setColorFilters(colorFilters: SkSpan<SkSp<PrecompileColorFilter>>) {
    TODO("Implement setColorFilters")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const sk_sp<PrecompileColorFilter>> getColorFilters() const {
   *         return SkSpan<const sk_sp<PrecompileColorFilter>>(fColorFilterOptions);
   *     }
   * ```
   */
  public fun getColorFilters(): Int {
    TODO("Implement getColorFilters")
  }

  /**
   * C++ original:
   * ```cpp
   * void PaintOptions::setBlendModes(SkSpan<const SkBlendMode> blendModes) {
   *     fBlendModeOptions.clear();
   *     fBlendModeOptions.push_back_n(blendModes.size(), blendModes.data());
   * }
   * ```
   */
  public fun setBlendModes(blendModes: SkSpan<SkBlendMode>) {
    TODO("Implement setBlendModes")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const SkBlendMode> getBlendModes() const {
   *         return SkSpan<const SkBlendMode>(fBlendModeOptions.data(), fBlendModeOptions.size());
   *     }
   * ```
   */
  public fun getBlendModes(): Int {
    TODO("Implement getBlendModes")
  }

  /**
   * C++ original:
   * ```cpp
   * void addBlendMode(SkBlendMode bm) {
   *         fBlendModeOptions.push_back(bm);
   *     }
   * ```
   */
  public fun addBlendMode(bm: SkBlendMode) {
    TODO("Implement addBlendMode")
  }

  /**
   * C++ original:
   * ```cpp
   * void PaintOptions::setBlenders(SkSpan<const sk_sp<PrecompileBlender>> blenders) {
   *     for (const sk_sp<PrecompileBlender>& b: blenders) {
   *         if (b->priv().asBlendMode().has_value()) {
   *             fBlendModeOptions.push_back(b->priv().asBlendMode().value());
   *         } else {
   *             fBlenderOptions.push_back(b);
   *         }
   *     }
   * }
   * ```
   */
  public fun setBlenders(blenders: SkSpan<SkSp<PrecompileBlender>>) {
    TODO("Implement setBlenders")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const sk_sp<PrecompileBlender>> getBlenders() const {
   *         return SkSpan<const sk_sp<PrecompileBlender>>(fBlenderOptions);
   *     }
   * ```
   */
  public fun getBlenders(): Int {
    TODO("Implement getBlenders")
  }

  /**
   * C++ original:
   * ```cpp
   * void setDither(bool dither) { fDither = dither; }
   * ```
   */
  public fun setDither(dither: Boolean) {
    TODO("Implement setDither")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isDither() const { return fDither; }
   * ```
   */
  public fun isDither(): Boolean {
    TODO("Implement isDither")
  }

  /**
   * C++ original:
   * ```cpp
   * void setPaintColorIsOpaque(bool paintColorIsOpaque) {
   *         fPaintColorIsOpaque = paintColorIsOpaque;
   *     }
   * ```
   */
  public fun setPaintColorIsOpaque(paintColorIsOpaque: Boolean) {
    TODO("Implement setPaintColorIsOpaque")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isPaintColorOpaque() const { return fPaintColorIsOpaque; }
   * ```
   */
  public fun isPaintColorOpaque(): Boolean {
    TODO("Implement isPaintColorOpaque")
  }

  /**
   * C++ original:
   * ```cpp
   * PaintOptionsPriv priv()
   * ```
   */
  public fun priv(): PaintOptionsPriv {
    TODO("Implement priv")
  }

  /**
   * C++ original:
   * ```cpp
   * const PaintOptionsPriv priv() const
   * ```
   */
  private fun addColorFilter(cf: SkSp<PrecompileColorFilter>) {
    TODO("Implement addColorFilter")
  }

  /**
   * C++ original:
   * ```cpp
   * void PaintOptions::addColorFilter(sk_sp<PrecompileColorFilter> cf) {
   *     fColorFilterOptions.push_back(std::move(cf));
   * }
   * ```
   */
  private fun setClipShaders(clipShaders: SkSpan<SkSp<PrecompileShader>>) {
    TODO("Implement setClipShaders")
  }

  /**
   * C++ original:
   * ```cpp
   * void PaintOptions::setClipShaders(SkSpan<const sk_sp<PrecompileShader>> clipShaders) {
   *     // In the normal API this modification happens in SkDevice::clipShader()
   *     fClipShaderOptions.reserve(2 * clipShaders.size());
   *     for (const sk_sp<PrecompileShader>& cs : clipShaders) {
   *         // All clipShaders get wrapped in a CTMShader ...
   *         sk_sp<PrecompileShader> withCTM = cs ? PrecompileShadersPriv::CTM({{ cs }}) : nullptr;
   *         // and, if it is a SkClipOp::kDifference clip, an additional ColorFilterShader
   *         sk_sp<PrecompileShader> inverted =
   *                 withCTM ? withCTM->makeWithColorFilter(PrecompileColorFilters::Blend())
   *                         : nullptr;
   *
   *         fClipShaderOptions.emplace_back(std::move(withCTM));
   *         fClipShaderOptions.emplace_back(std::move(inverted));
   *     }
   * }
   * ```
   */
  private fun setPrimitiveBlendMode(bm: SkBlendMode) {
    TODO("Implement setPrimitiveBlendMode")
  }

  /**
   * C++ original:
   * ```cpp
   * void setPrimitiveBlendMode(SkBlendMode bm) { fPrimitiveBlendMode = bm; }
   * ```
   */
  private fun setSkipColorXform(skipColorXform: Boolean) {
    TODO("Implement setSkipColorXform")
  }

  /**
   * C++ original:
   * ```cpp
   * void setSkipColorXform(bool skipColorXform) { fSkipColorXform = skipColorXform; }
   * ```
   */
  private fun numShaderCombinations(): Int {
    TODO("Implement numShaderCombinations")
  }

  /**
   * C++ original:
   * ```cpp
   * int PaintOptions::numShaderCombinations() const {
   *     int numShaderCombinations = 0;
   *     for (const sk_sp<PrecompileShader>& s : fShaderOptions) {
   *         numShaderCombinations += s->numCombinations();
   *     }
   *
   *     // If no shader option is specified we will add a solid color shader option
   *     return numShaderCombinations ? numShaderCombinations : 1;
   * }
   * ```
   */
  private fun numColorFilterCombinations(): Int {
    TODO("Implement numColorFilterCombinations")
  }

  /**
   * C++ original:
   * ```cpp
   * int PaintOptions::numColorFilterCombinations() const {
   *     int numColorFilterCombinations = 0;
   *     for (const sk_sp<PrecompileColorFilter>& cf : fColorFilterOptions) {
   *         if (!cf) {
   *             ++numColorFilterCombinations;
   *         } else {
   *             numColorFilterCombinations += cf->numCombinations();
   *         }
   *     }
   *
   *     // If no color filter options are specified we will use the unmodified result color
   *     return numColorFilterCombinations ? numColorFilterCombinations : 1;
   * }
   * ```
   */
  private fun numBlendCombinations(): Int {
    TODO("Implement numBlendCombinations")
  }

  /**
   * C++ original:
   * ```cpp
   * int PaintOptions::numBlendCombinations() const {
   *     int numBlendCombos = fBlendModeOptions.size();
   *     for (const sk_sp<PrecompileBlender>& b: fBlenderOptions) {
   *         SkASSERT(!b->priv().asBlendMode().has_value());
   *         numBlendCombos += b->priv().numChildCombinations();
   *     }
   *
   *     if (!numBlendCombos) {
   *         // If the user didn't specify a blender we will fall back to kSrcOver blending
   *         numBlendCombos = 1;
   *     }
   *
   *     return numBlendCombos;
   * }
   * ```
   */
  private fun numClipShaderCombinations(): Int {
    TODO("Implement numClipShaderCombinations")
  }

  /**
   * C++ original:
   * ```cpp
   * int PaintOptions::numClipShaderCombinations() const {
   *     int numClipShaderCombos = 0;
   *     for (const sk_sp<PrecompileShader>& cs: fClipShaderOptions) {
   *         if (cs) {
   *             numClipShaderCombos += cs->priv().numChildCombinations();
   *         } else {
   *             ++numClipShaderCombos;
   *         }
   *     }
   *
   *     // If no clipShader options are specified we will just have the unclipped options
   *     return numClipShaderCombos ? numClipShaderCombos : 1;
   * }
   * ```
   */
  private fun numCombinations(): Int {
    TODO("Implement numCombinations")
  }

  /**
   * C++ original:
   * ```cpp
   * int PaintOptions::numCombinations() const {
   *     return this->numShaderCombinations() *
   *            this->numColorFilterCombinations() *
   *            this->numBlendCombinations() *
   *            this->numClipShaderCombinations();
   * }
   * ```
   */
  private fun createKey(
    keyContext: KeyContext,
    targetFormat: TextureFormat,
    desiredCombination: Int,
    addPrimitiveBlender: Boolean,
    addAnalyticClip: Boolean,
    coverage: Coverage,
  ) {
    TODO("Implement createKey")
  }

  /**
   * C++ original:
   * ```cpp
   * void PaintOptions::createKey(const KeyContext& keyContext,
   *                              TextureFormat targetFormat,
   *                              int desiredCombination,
   *                              bool addPrimitiveBlender,
   *                              bool addAnalyticClip,
   *                              Coverage coverage) const {
   *     SkDEBUGCODE(keyContext.paintParamsKeyBuilder()->checkReset();)
   *     SkASSERT(desiredCombination < this->numCombinations());
   *
   *     const int numClipShaderCombos = this->numClipShaderCombinations();
   *     const int numBlendModeCombos = this->numBlendCombinations();
   *     const int numColorFilterCombinations = this->numColorFilterCombinations();
   *
   *     const int desiredClipShaderCombination = desiredCombination % numClipShaderCombos;
   *     int remainingCombinations = desiredCombination / numClipShaderCombos;
   *
   *     const int desiredBlendCombination = remainingCombinations % numBlendModeCombos;
   *     remainingCombinations /= numBlendModeCombos;
   *
   *     const int desiredColorFilterCombination = remainingCombinations % numColorFilterCombinations;
   *     remainingCombinations /= numColorFilterCombinations;
   *
   *     const int desiredShaderCombination = remainingCombinations;
   *     SkASSERT(desiredShaderCombination < this->numShaderCombinations());
   *
   *     auto clipShader = PrecompileBase::SelectOption(SkSpan(fClipShaderOptions),
   *                                                    desiredClipShaderCombination);
   *
   *     std::pair<sk_sp<PrecompileBlender>, int> finalBlender;
   *     if (desiredBlendCombination < fBlendModeOptions.size()) {
   *         finalBlender = { PrecompileBlenders::Mode(fBlendModeOptions[desiredBlendCombination]), 0 };
   *     } else {
   *         finalBlender = PrecompileBase::SelectOption(
   *                             SkSpan(fBlenderOptions),
   *                             desiredBlendCombination - fBlendModeOptions.size());
   *     }
   *     if (!finalBlender.first) {
   *         finalBlender = { PrecompileBlenders::Mode(SkBlendMode::kSrcOver), 0 };
   *     }
   *
   *     PaintOption option(fPaintColorIsOpaque,
   *                        finalBlender,
   *                        PrecompileBase::SelectOption(SkSpan(fShaderOptions),
   *                                                     desiredShaderCombination),
   *                        PrecompileBase::SelectOption(SkSpan(fColorFilterOptions),
   *                                                     desiredColorFilterCombination),
   *                        addPrimitiveBlender,
   *                        fPrimitiveBlendMode,
   *                        fSkipColorXform,
   *                        clipShader,
   *                        coverage,
   *                        targetFormat,
   *                        fDither,
   *                        addAnalyticClip);
   *
   *     option.toKey(keyContext);
   * }
   * ```
   */
  private fun buildCombinations(
    keyContext: KeyContext,
    drawTypes: DrawTypeFlags,
    addPrimitiveBlender: Boolean,
    coverage: Coverage,
    renderPassDesc: RenderPassDesc,
    processCombination: PaintOptionsProcessCombination,
  ) {
    TODO("Implement buildCombinations")
  }
}
