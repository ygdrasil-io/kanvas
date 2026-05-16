package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import kotlin.Unit
import org.skia.core.SkPMColor4f
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan

/**
 * C++ original:
 * ```cpp
 * class PipelineDataGatherer {
 * public:
 *     PipelineDataGatherer(Layout layout) : fUniformManager(layout) {}
 *
 *     // Fully resets both uniforms (paint and renderstep)and textures
 *     void resetForDraw() {
 *         fUniformManager.reset();
 *         fTextures.clear();
 *         fPaintTextureCount = 0;
 *     }
 *
 * #if defined(SK_DEBUG)
 *     // Check that the gatherer has been reset to its initial state prior to collecting new data.
 *     void checkReset() const {
 *         SkASSERT(fTextures.empty());
 *         SkASSERT(fUniformManager.isReset());
 *         SkASSERT(fPaintTextureCount == 0);
 *     }
 *
 *     void checkRewind() const {
 *         SkASSERT(fTextures.size() == fPaintTextureCount);
 *     }
 * #endif // SK_DEBUG
 *
 *     // If a renderstep performs shading, then alignment should occur on the combined
 *     // paint+renderstep, so no alignment is required and we simply mark the end of the paints. Else
 *     // we need to align whatever is cuurrently stored to the renderstep's uniform alignment.
 *     void markOffsetAndAlign(bool performsShading, int requiredAlignment) {
 *         fPaintTextureCount = fTextures.size();
 *         fUniformManager.markOffset();
 *         if (!performsShading) {
 *             fUniformManager.alignForNonShading(requiredAlignment);
 *         }
 *     }
 *
 *     // Rewind to collect data for another RenderStep using the same paint data.
 *     void rewindForRenderStep() {
 *         fTextures.resize_back(fPaintTextureCount);
 *         fUniformManager.rewindToMark();
 *     }
 *
 *     // Mark the end of extracting uniforms and textures from a RenderStep.
 *     std::pair<UniformDataBlock, TextureDataBlock> endCombinedData(bool performsShading) {
 *         SkSpan<const TextureDataBlock::SampledTexture> textures{fTextures};
 *         if (performsShading) {
 *             // Return paint AND renderstep uniforms written since the last resetForDraw.
 *             return {UniformDataBlock::Wrap(&fUniformManager), TextureDataBlock(textures)};
 *         } else {
 *             textures = textures.subspan(fPaintTextureCount);
 *             return {UniformDataBlock::WrapNonShading(&fUniformManager), TextureDataBlock(textures)};
 *         }
 *     }
 *
 *     // Append a sampled texture.
 *     void add(sk_sp<TextureProxy> proxy, const SamplerDesc& samplerDesc) {
 *         fTextures.push_back({std::move(proxy), samplerDesc});
 *     }
 *
 *     void tryShrinkCapacity() {
 *         SkDEBUGCODE(this->checkReset());
 *         fUniformManager.tryShrinkCapacity();
 *     }
 *
 *     // Mimic the type-safe API available in UniformManager
 *     template <typename T> void write(const T& t) { fUniformManager.write(t); }
 *     template <typename T> void writeHalf(const T& t) { fUniformManager.writeHalf(t); }
 *     template <typename T> void writeArray(SkSpan<const T> t) { fUniformManager.writeArray(t); }
 *     template <typename T> void writeHalfArray(SkSpan<const T> t) {
 *         fUniformManager.writeHalfArray(t);
 *     }
 *     void write(const Uniform& u, const void* data) { fUniformManager.write(u, data); }
 *     void writePaintColor(const SkPMColor4f& color) { fUniformManager.writePaintColor(color); }
 *     void beginStruct(int baseAligment) { fUniformManager.beginStruct(baseAligment); }
 *     void endStruct() { fUniformManager.endStruct(); }
 *
 *     SkDEBUGCODE(UniformManager* uniformManager() { return &fUniformManager; })
 * private:
 *     SkDEBUGCODE(friend class UniformExpectationsValidator;)
 *
 *     UniformManager fUniformManager;
 *     skia_private::TArray<TextureDataBlock::SampledTexture> fTextures;
 *
 *     int fPaintTextureCount = 0;
 * }
 * ```
 */
public data class PipelineDataGatherer public constructor(
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<TextureDataBlock::SampledTexture> fTextures
   * ```
   */
  private var fTextures: Int,
  /**
   * C++ original:
   * ```cpp
   * int fPaintTextureCount = 0
   * ```
   */
  private var fPaintTextureCount: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * void resetForDraw() {
   *         fUniformManager.reset();
   *         fTextures.clear();
   *         fPaintTextureCount = 0;
   *     }
   * ```
   */
  public fun resetForDraw() {
    TODO("Implement resetForDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void markOffsetAndAlign(bool performsShading, int requiredAlignment) {
   *         fPaintTextureCount = fTextures.size();
   *         fUniformManager.markOffset();
   *         if (!performsShading) {
   *             fUniformManager.alignForNonShading(requiredAlignment);
   *         }
   *     }
   * ```
   */
  public fun markOffsetAndAlign(performsShading: Boolean, requiredAlignment: Int) {
    TODO("Implement markOffsetAndAlign")
  }

  /**
   * C++ original:
   * ```cpp
   * void rewindForRenderStep() {
   *         fTextures.resize_back(fPaintTextureCount);
   *         fUniformManager.rewindToMark();
   *     }
   * ```
   */
  public fun rewindForRenderStep() {
    TODO("Implement rewindForRenderStep")
  }

  /**
   * C++ original:
   * ```cpp
   * std::pair<UniformDataBlock, TextureDataBlock> endCombinedData(bool performsShading) {
   *         SkSpan<const TextureDataBlock::SampledTexture> textures{fTextures};
   *         if (performsShading) {
   *             // Return paint AND renderstep uniforms written since the last resetForDraw.
   *             return {UniformDataBlock::Wrap(&fUniformManager), TextureDataBlock(textures)};
   *         } else {
   *             textures = textures.subspan(fPaintTextureCount);
   *             return {UniformDataBlock::WrapNonShading(&fUniformManager), TextureDataBlock(textures)};
   *         }
   *     }
   * ```
   */
  public fun endCombinedData(performsShading: Boolean): Int {
    TODO("Implement endCombinedData")
  }

  /**
   * C++ original:
   * ```cpp
   * void add(sk_sp<TextureProxy> proxy, const SamplerDesc& samplerDesc) {
   *         fTextures.push_back({std::move(proxy), samplerDesc});
   *     }
   * ```
   */
  public fun add(proxy: SkSp<TextureProxy>, samplerDesc: SamplerDesc) {
    TODO("Implement add")
  }

  /**
   * C++ original:
   * ```cpp
   * void tryShrinkCapacity() {
   *         SkDEBUGCODE(this->checkReset());
   *         fUniformManager.tryShrinkCapacity();
   *     }
   * ```
   */
  public fun tryShrinkCapacity() {
    TODO("Implement tryShrinkCapacity")
  }

  /**
   * C++ original:
   * ```cpp
   * void write(const T& t) { fUniformManager.write(t); }
   * ```
   */
  public fun write(t: T) {
    TODO("Implement write")
  }

  /**
   * C++ original:
   * ```cpp
   * void writeHalf(const T& t) { fUniformManager.writeHalf(t); }
   * ```
   */
  public fun writeHalf(t: T) {
    TODO("Implement writeHalf")
  }

  /**
   * C++ original:
   * ```cpp
   * void writeArray(SkSpan<const T> t) { fUniformManager.writeArray(t); }
   * ```
   */
  public fun writeArray(t: SkSpan<T>) {
    TODO("Implement writeArray")
  }

  /**
   * C++ original:
   * ```cpp
   * void writeHalfArray(SkSpan<const T> t) {
   *         fUniformManager.writeHalfArray(t);
   *     }
   * ```
   */
  public fun writeHalfArray(t: SkSpan<T>) {
    TODO("Implement writeHalfArray")
  }

  /**
   * C++ original:
   * ```cpp
   * void write(const Uniform& u, const void* data) { fUniformManager.write(u, data); }
   * ```
   */
  public fun write(u: Uniform, `data`: Unit?) {
    TODO("Implement write")
  }

  /**
   * C++ original:
   * ```cpp
   * void writePaintColor(const SkPMColor4f& color) { fUniformManager.writePaintColor(color); }
   * ```
   */
  public fun writePaintColor(color: SkPMColor4f) {
    TODO("Implement writePaintColor")
  }

  /**
   * C++ original:
   * ```cpp
   * void beginStruct(int baseAligment) { fUniformManager.beginStruct(baseAligment); }
   * ```
   */
  public fun beginStruct(baseAligment: Int) {
    TODO("Implement beginStruct")
  }

  /**
   * C++ original:
   * ```cpp
   * void endStruct() { fUniformManager.endStruct(); }
   * ```
   */
  public fun endStruct() {
    TODO("Implement endStruct")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDEBUGCODE(UniformManager* uniformManager() { return &fUniformManager; })
   * ```
   */
  public fun skDEBUGCODE(param0: () -> Int?): Int {
    TODO("Implement skDEBUGCODE")
  }
}
